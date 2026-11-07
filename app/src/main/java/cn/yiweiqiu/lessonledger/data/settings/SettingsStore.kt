package cn.yiweiqiu.lessonledger.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import cn.yiweiqiu.lessonledger.domain.DataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val email: String = "",
    val appPassword: String = "",
    val rootDir: String = DEFAULT_ROOT,
    val deviceId: String = "",
    val deviceName: String = "",
    val lastSyncAt: Long = 0L,
    val lastRemoteEtag: String? = null,
    val backupEncrypted: Boolean = false,
    val backupPassphrase: String = "",
    val defaultThreshold: Int = 5,
    /** 上课提醒总开关 */
    val classReminderEnabled: Boolean = true,
    /** 新建课程时默认提前多少分钟提醒 */
    val defaultRemindBeforeMinutes: Int = 30,
    /**
     * 每天早上 [DIGEST_HOUR] 点发一条「今天有课」的汇总提醒。
     *
     * 以前是每节课各按「提前 15/30/60/120 分钟」各响一次，一学期下来一天能响三四条，
     * 而且提前半小时才提醒往往已经在路上，来不及安排接送。改成每天早起一次汇总：
     * 一眼看完今天几节课、几点、在哪，剩下的交给「到点该打卡」那一条。
     */
    val dailyDigestEnabled: Boolean = true,
    /** 是否已经向用户申请过通知权限（避免每次启动都弹） */
    val notificationAsked: Boolean = false,
    val monthlyBudgetCents: Long? = null,
    val syncWifiOnly: Boolean = false,
    val absentDeducts: Boolean = true,
    val leaveDeducts: Boolean = false,
    val seeded: Boolean = false,
    val lastConflictCount: Int = 0,
    val retryAttempt: Int = 0,
    /** 云端最近一次写入来自哪台设备、什么时候（家庭共享时用来显示“老婆的手机 · 5 分钟前”） */
    val remoteDeviceName: String = "",
    val remoteWriteAt: Long = 0L,
    /** 主题色（AccentTheme.key）与深浅色策略（system/light/dark），只影响本机显示，不参与同步 */
    val accentKey: String = "system",
    val themeMode: String = "system",
    /**
     * 当前在看哪一套数据（DataSource.key）。
     * 两套数据都留在库里，这里只决定「今天 / 课程 / 统计 / 我的 / 小组件」呈现哪一套。
     */
    val dataSource: String = "mine",
    /**
     * 演示数据总开关（默认关）。
     *
     * 关着的时候全 App 只有「我的数据」：示例账本不进统计、不进课程列表、
     * 不进提醒排程，设置里也看不到它的条数与入口。打开之后才允许 [dataSource]
     * 指到演示那一套 —— 换句话说，[dataSource] 是「看哪一套」，这里是「允不允许看」。
     */
    val demoEnabled: Boolean = false,
    /**
     * 远端 /keshibu/、backups/、attachments/ 三个目录是否已经建好。
     * 建目录是幂等操作但每次都要发 3 个 MKCOL（服务器回 405），
     * 而目录一旦建好就不会自己消失——记下来，正常同步只发 PROPFIND + GET manifest。
     * 遇到 404 会被当成"目录被删了"，自动重建并重新置位。
     */
    val dirsReady: Boolean = false,
) {
    /**
     * 实际生效的数据源：总开关关着时永远是「我的数据」。
     *
     * 全 App 只认这一个口径 —— 统计、课程、首页、我的、提醒排程、小组件都不再各判各的。
     * 这样即使 [dataSource] 里残留着 "demo"（比如上次停在演示页就退出），
     * 只要总开关是关的，示例数据也不会从任何一个缝里露出来。
     */
    val effectiveSource: DataSource
        get() = if (demoEnabled) DataSource.of(dataSource) else DataSource.MINE

    val isConfigured: Boolean get() = email.isNotBlank() && appPassword.isNotBlank()
    val rootDirNormalized: String
        get() {
            val trimmed = rootDir.trim().ifBlank { DEFAULT_ROOT }
            val withSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
            return if (withSlash.endsWith("/")) withSlash else "$withSlash/"
        }

    companion object {
        /** 远端目录固定这一个：两台手机目录不一致就等于两份账本，所以不给用户填 */
        const val DEFAULT_ROOT = "/keshibu/"

        /** 每日汇总提醒的整点（本地时区）。用户要求固定早上 10 点，不做提前量 */
        const val DIGEST_HOUR = 10
    }
}

/**
 * 设置存储。
 * 坚果云应用密码属于敏感资产，必须走 EncryptedSharedPreferences（Android Keystore 加密），
 * 严禁明文写普通 SharedPreferences，严禁打印到日志。
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    private fun createPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // 极端情况下（设备 keystore 异常）降级，保证 App 仍可用
            context.getSharedPreferences("${FILE_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun load(): AppSettings {
        val p = prefs
        val deviceId = p.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { p.edit().putString(KEY_DEVICE_ID, it).apply() }
        val deviceName = p.getString(KEY_DEVICE_NAME, null)
            ?: defaultDeviceName().also { p.edit().putString(KEY_DEVICE_NAME, it).apply() }
        return AppSettings(
            email = p.getString(KEY_EMAIL, "") ?: "",
            appPassword = p.getString(KEY_PASSWORD, "") ?: "",
            // 老版本的默认目录叫「课外班管家」，现在统一成 keshibu；老配置顺手迁过来
            rootDir = (p.getString(KEY_ROOT, AppSettings.DEFAULT_ROOT) ?: AppSettings.DEFAULT_ROOT)
                .let { if (it.trim().trim('/') == "课外班管家") AppSettings.DEFAULT_ROOT else it },
            deviceId = deviceId,
            deviceName = deviceName,
            lastSyncAt = p.getLong(KEY_LAST_SYNC, 0L),
            lastRemoteEtag = p.getString(KEY_ETAG, null),
            backupEncrypted = p.getBoolean(KEY_ENCRYPT, false),
            backupPassphrase = p.getString(KEY_PASSPHRASE, "") ?: "",
            defaultThreshold = p.getInt(KEY_THRESHOLD, 5),
            classReminderEnabled = p.getBoolean(KEY_REMINDER_ON, true),
            defaultRemindBeforeMinutes = p.getInt(KEY_REMINDER_LEAD, 30),
            dailyDigestEnabled = p.getBoolean(KEY_DAILY_DIGEST, true),
            notificationAsked = p.getBoolean(KEY_NOTIF_ASKED, false),
            monthlyBudgetCents = p.getLong(KEY_BUDGET, -1L).takeIf { it >= 0 },
            syncWifiOnly = p.getBoolean(KEY_WIFI_ONLY, false),
            absentDeducts = p.getBoolean(KEY_ABSENT, true),
            leaveDeducts = p.getBoolean(KEY_LEAVE, false),
            seeded = p.getBoolean(KEY_SEEDED, false),
            lastConflictCount = p.getInt(KEY_CONFLICTS, 0),
            retryAttempt = p.getInt(KEY_RETRY, 0),
            remoteDeviceName = p.getString(KEY_PEER_NAME, "") ?: "",
            remoteWriteAt = p.getLong(KEY_PEER_AT, 0L),
            accentKey = p.getString(KEY_ACCENT, "system") ?: "system",
            themeMode = p.getString(KEY_THEME_MODE, "system") ?: "system",
            dataSource = p.getString(KEY_DATA_SOURCE, "mine") ?: "mine",
            demoEnabled = p.getBoolean(KEY_DEMO_ENABLED, false),
            dirsReady = p.getBoolean(KEY_DIRS_READY, false),
        )
    }

    private fun defaultDeviceName(): String {
        val model = Build.MODEL ?: "Android"
        // 模拟器/工程机的机型名对用户没意义，换成人看得懂的名字
        if (model.startsWith("Android SDK built for") || model.equals("sdk", true)) return "Android 模拟器"
        return try {
            val name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            name?.takeIf { it.isNotBlank() } ?: model
        } catch (e: Exception) {
            model
        }
    }

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = load()
    }

    /** 远端目录不再由用户填写，一律走 DEFAULT_ROOT */
    fun updateAccount(email: String, appPassword: String) = write {
        putString(KEY_EMAIL, email.trim())
        putString(KEY_PASSWORD, appPassword)
        putString(KEY_ROOT, AppSettings.DEFAULT_ROOT)
        // 换了账号就是另一朵云，目录标记作废，下一轮重新建
        putBoolean(KEY_DIRS_READY, false)
    }

    fun updateDeviceName(name: String) = write { putString(KEY_DEVICE_NAME, name.ifBlank { defaultDeviceName() }) }

    fun updateSyncResult(lastSyncAt: Long, etag: String?, conflictCount: Int) = write {
        putLong(KEY_LAST_SYNC, lastSyncAt)
        if (etag != null) putString(KEY_ETAG, etag) else remove(KEY_ETAG)
        putInt(KEY_CONFLICTS, conflictCount)
        putInt(KEY_RETRY, 0)
    }

    fun updateRetry(attempt: Int) = write { putInt(KEY_RETRY, attempt) }

    /** 记录云端最后一次写入来自哪台设备；本机写入时由调用方跳过 */
    fun updateRemotePeer(name: String, writeAt: Long) = write {
        putString(KEY_PEER_NAME, name)
        putLong(KEY_PEER_AT, writeAt)
    }

    fun setAccent(key: String) = write { putString(KEY_ACCENT, key) }

    fun setThemeMode(mode: String) = write { putString(KEY_THEME_MODE, mode) }

    /** 切换数据源：只改「看哪一套」，不动任何一条记录 */
    fun setDataSource(key: String) = write { putString(KEY_DATA_SOURCE, key) }

    /** 演示数据总开关：关掉时全 App 只剩「我的数据」，示例账本在任何页面都不出现 */
    fun setDemoEnabled(enabled: Boolean) = write { putBoolean(KEY_DEMO_ENABLED, enabled) }

    fun setBackupEncryption(enabled: Boolean, passphrase: String) = write {
        putBoolean(KEY_ENCRYPT, enabled)
        putString(KEY_PASSPHRASE, passphrase)
    }

    fun setDefaultThreshold(value: Int) = write { putInt(KEY_THRESHOLD, value.coerceIn(1, 99)) }

    fun setClassReminderEnabled(enabled: Boolean) = write { putBoolean(KEY_REMINDER_ON, enabled) }

    fun setDefaultRemindBeforeMinutes(value: Int) =
        write { putInt(KEY_REMINDER_LEAD, value.coerceIn(0, 240)) }

    fun setDailyDigestEnabled(enabled: Boolean) = write { putBoolean(KEY_DAILY_DIGEST, enabled) }

    fun markNotificationAsked() = write { putBoolean(KEY_NOTIF_ASKED, true) }

    fun setMonthlyBudget(cents: Long?) = write {
        if (cents == null) remove(KEY_BUDGET) else putLong(KEY_BUDGET, cents)
    }

    fun setSyncWifiOnly(enabled: Boolean) = write { putBoolean(KEY_WIFI_ONLY, enabled) }

    fun setDeductionRules(absentDeducts: Boolean, leaveDeducts: Boolean) = write {
        putBoolean(KEY_ABSENT, absentDeducts)
        putBoolean(KEY_LEAVE, leaveDeducts)
    }

    fun markSeeded() = write { putBoolean(KEY_SEEDED, true) }

    /** 远端目录已确认建好，之后不必每轮都发 MKCOL */
    fun markDirsReady(ready: Boolean = true) = write { putBoolean(KEY_DIRS_READY, ready) }

    fun clearSyncCursor() = write {
        putLong(KEY_LAST_SYNC, 0L)
        remove(KEY_ETAG)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _state.value = load()
    }

    private companion object {
        const val FILE_NAME = "ledger_secure_settings"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "app_password"
        const val KEY_ROOT = "root_dir"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_ETAG = "last_remote_etag"
        const val KEY_ENCRYPT = "backup_encrypt"
        const val KEY_PASSPHRASE = "backup_passphrase"
        const val KEY_THRESHOLD = "default_threshold"
        const val KEY_REMINDER_ON = "class_reminder_enabled"
        const val KEY_REMINDER_LEAD = "default_remind_before_minutes"
        const val KEY_DAILY_DIGEST = "daily_digest_enabled"
        const val KEY_NOTIF_ASKED = "notification_asked"
        const val KEY_BUDGET = "monthly_budget"
        const val KEY_WIFI_ONLY = "sync_wifi_only"
        const val KEY_ABSENT = "absent_deducts"
        const val KEY_LEAVE = "leave_deducts"
        const val KEY_SEEDED = "seeded"
        const val KEY_CONFLICTS = "conflict_count"
        const val KEY_RETRY = "retry_attempt"
        const val KEY_PEER_NAME = "remote_peer_name"
        const val KEY_PEER_AT = "remote_peer_at"
        const val KEY_ACCENT = "accent_key"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DATA_SOURCE = "data_source"
        const val KEY_DEMO_ENABLED = "demo_enabled"
        const val KEY_DIRS_READY = "dirs_ready"
    }
}
