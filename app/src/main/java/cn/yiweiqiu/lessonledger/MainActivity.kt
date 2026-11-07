package cn.yiweiqiu.lessonledger

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cn.yiweiqiu.lessonledger.data.reminder.ReminderNav
import cn.yiweiqiu.lessonledger.data.reminder.ReminderNotifier
import cn.yiweiqiu.lessonledger.data.reminder.ReminderScheduler
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.data.sync.SyncScheduler
import cn.yiweiqiu.lessonledger.data.widget.CheckinWidgetProvider
import cn.yiweiqiu.lessonledger.ui.nav.AppRoot
import cn.yiweiqiu.lessonledger.ui.theme.AccentTheme
import cn.yiweiqiu.lessonledger.ui.theme.LessonLedgerTheme
import cn.yiweiqiu.lessonledger.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var notifier: ReminderNotifier

    private var pollJob: Job? = null

    /** Android 13+ 发通知要用户授权，只在第一次启动时申请一次 */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        settingsStore.markNotificationAsked()
        if (granted) reminderScheduler.rescheduleAsync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏、导航栏全部透明，内容画到系统栏下面（沉浸式）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        // 小白条（手势条）适配：
        // ① 关掉系统给导航栏铺的「对比度蒙层」——国产 ROM（HyperOS / MIUI）上它会按
        //    系统深浅画一条纯白的底，App 自己的极光背景是暖白，于是最底下永远垫着
        //    一条突兀的白杠，怎么调 App 的颜色都盖不掉；
        // ② 手势条图标的深浅跟随 **App 内**的深浅色，而不是系统主题，否则 App 里
        //    手动选深色时，系统仍按浅色画深灰手势条，压在深底上几乎看不见。
        disableSystemBarContrast()
        // 通知点击进来时先记下"要跳打卡页"，setContent 之后由 AppRoot 消费
        handleReminderIntent(intent)
        setContent {
            val prefs by settingsStore.state.collectAsStateWithLifecycle()
            val dark = when (ThemeMode.of(prefs.themeMode)) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LessonLedgerTheme(accent = AccentTheme.of(prefs.accentKey), darkTheme = dark) {
                // 主题一变就把系统栏图标深浅重设一遍：手势条（小白条）在深色底上必须是
                // 浅色、浅色底上必须是深色，跟随系统主题做不到这件事。
                LaunchedEffect(dark) { applySystemBarAppearance(dark) }
                // 透明 Surface：真正的底色是 AppRoot 里的极光层，玻璃条才能透出颜色。
                // 注意必须显式给 contentColor —— 透明容器会让 LocalContentColor 变成
                // Unspecified，所有没指定颜色的 Text 都会回退成黑色（深色模式下等于看不见）。
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    AppRoot()
                }
            }
        }
        askNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
    }

    /**
     * 关掉系统栏的对比度蒙层。
     *
     * Android 10 起，导航栏透明时系统会按栏内图标颜色铺一层蒙层保证可见性。
     * 桌面/浅色 App 里看不出问题，但我们这层极光是暖白的，蒙层是纯白，
     * 于是屏幕最底下那条（手势条所在的一整条）永远比上面白一档，
     * 像没铺满的背景 —— 这就是「没适配小白条」的观感来源。
     */
    private fun disableSystemBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    /** 系统栏图标深浅跟随 App 内主题（不是系统主题），小白条才不会和底色糊在一起 */
    private fun applySystemBarAppearance(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台触发一次同步（未配置时会立即返回）。
        // 60 秒去重：刚启动时首页也会同步一次，切回来的第一下没必要再发一轮请求。
        syncScheduler.syncNow("resume", minIntervalMs = 60_000L)
        // 回到前台也重排一次闹钟：课表可能刚在另一台设备上被改过
        reminderScheduler.rescheduleAsync()
        // 桌面小组件跟着刷新一次（今天有没有课、几节待打卡）
        CheckinWidgetProvider.refresh(this)
        startForegroundPolling()
    }

    override fun onPause() {
        super.onPause()
        pollJob?.cancel()
        pollJob = null
    }

    /** 通知点击 → 直接落到打卡页 */
    private fun handleReminderIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CHECKIN, false) == true) {
            ReminderNav.requestOpenCheckIn()
        }
    }

    /**
     * 首次启动申请通知权限。
     * 不申请的话提醒排程照跑，但 Android 13+ 一条也发不出去，用户会以为功能坏了。
     */
    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (settingsStore.current.notificationAsked) return
        if (notifier.canNotify()) {
            settingsStore.markNotificationAsked()
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * 前台轮询：两台手机（比如夫妻各一台）同时开着 App 时，
     * 让对方的改动在 10 分钟内自动落地，不用手动下拉。
     * 只在前台跑，且远端没变化时就是一次 PROPFIND + manifest GET，开销很小。
     *
     * 间隔不能太短：坚果云对 WebDAV 有频率限制（我们撞过 503），
     * 而真实的跨设备场景是「一边改完，另一边过一会儿看」，10 分钟足够；
     * 等不及的时候下拉刷新 / 从别的 App 切回来都会立刻同步。
     */
    private fun startForegroundPolling() {
        pollJob?.cancel()
        if (!settingsStore.current.isConfigured) return
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (settingsStore.current.isConfigured) {
                    // quiet：轮询是后台行为，别在界面上闪「同步中…」，也不记空转日志
                    syncScheduler.syncNow("foreground-poll", quiet = true)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_CHECKIN = "open_checkin"
        const val EXTRA_LESSON_ID = "lesson_id"
        private const val POLL_INTERVAL_MS = 600_000L
    }
}
