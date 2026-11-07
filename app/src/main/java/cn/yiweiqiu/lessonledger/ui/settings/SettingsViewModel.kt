package cn.yiweiqiu.lessonledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.data.reminder.ReminderNotifier
import cn.yiweiqiu.lessonledger.data.reminder.ReminderScheduler
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.data.sync.DavClient
import cn.yiweiqiu.lessonledger.data.sync.SyncScheduler
import cn.yiweiqiu.lessonledger.data.sync.SyncStatusHolder
import cn.yiweiqiu.lessonledger.domain.DataSource
import cn.yiweiqiu.lessonledger.domain.DemoStats
import cn.yiweiqiu.lessonledger.domain.forSource
import cn.yiweiqiu.lessonledger.domain.statsOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val dav: DavClient,
    private val repo: LedgerRepository,
    private val scheduler: SyncScheduler,
    private val syncStatus: SyncStatusHolder,
    private val reminderScheduler: ReminderScheduler,
    private val digestScheduler: cn.yiweiqiu.lessonledger.data.reminder.DigestScheduler,
    private val notifier: ReminderNotifier,
) : ViewModel() {

    data class TestResult(
        val ok: Boolean,
        val message: String,
        val quotaAvailable: Long? = null,
        val quotaUsed: Long? = null,
        val loading: Boolean = false,
    )

    val settingsState = settings.state
    val syncState = syncStatus.state

    /**
     * 两套数据的现状：各自多大、现在用的是哪一套。
     *
     * 用的是 [LedgerRepository.allData]（全库），不是 repo.data —— 后者已经按当前数据源筛过，
     * 拿它算「另一套有多大」永远是 0。
     */
    data class SourceState(
        val current: DataSource = DataSource.MINE,
        val mine: DemoStats = DemoStats(),
        val demo: DemoStats = DemoStats(),
        val working: Boolean = false,
        /** 演示数据总开关：关着时下面关于演示的入口与条数一律不呈现 */
        val demoEnabled: Boolean = false,
    ) {
        val demoPresent: Boolean get() = demo.present
    }

    val sourceState: StateFlow<SourceState> = combine(repo.allData, settings.state) { data, setting ->
        SourceState(
            current = setting.effectiveSource,
            mine = data.forSource(DataSource.MINE).statsOf(),
            demo = data.forSource(DataSource.DEMO).statsOf(),
            demoEnabled = setting.demoEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourceState())

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult = _testResult.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun saveAccount(email: String, password: String) {
        settings.updateAccount(email, password)
        _testResult.value = null
    }

    fun testConnection() {
        val s = settings.current
        if (!s.isConfigured) {
            _testResult.value = TestResult(false, "请先填写坚果云账号邮箱和应用密码")
            return
        }
        _testResult.value = TestResult(false, "正在连接坚果云…", loading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val session = dav.openSession(s.email, s.appPassword, s.rootDirNormalized)
                    session.testConnection()
                }
            }
            _testResult.value = result.fold(
                onSuccess = { (code, available, used) ->
                    when (code) {
                        207 -> TestResult(
                            ok = true,
                            message = "连接成功（PROPFIND 207）",
                            quotaAvailable = available,
                            quotaUsed = used,
                        )
                        401 -> TestResult(
                            false,
                            "认证失败（401）：请使用坚果云「应用密码」，不是登录密码",
                        )
                        403 -> TestResult(false, "无权限（403）：请检查账号状态")
                        else -> TestResult(false, "连接失败（HTTP $code）")
                    }
                },
                onFailure = { TestResult(false, "网络错误：${it.message ?: it.javaClass.simpleName}") },
            )
        }
    }

    fun syncNow() = scheduler.syncNow("manual")

    /** 改设备名后立刻同步一次，让云端 manifest 里记上新的名字（对方能看到是谁改的） */
    fun saveDeviceName(name: String) {
        settings.updateDeviceName(name)
        scheduler.syncNow("device-name")
    }

    fun setAccent(key: String) = settings.setAccent(key)

    fun setThemeMode(mode: String) = settings.setThemeMode(mode)

    fun setWiFiOnly(enabled: Boolean) {
        settings.setSyncWifiOnly(enabled)
        scheduler.ensurePeriodicWork()
    }

    fun setBackupEncryption(enabled: Boolean, passphrase: String) {
        settings.setBackupEncryption(enabled, passphrase)
    }

    fun setDefaultThreshold(value: Int) = settings.setDefaultThreshold(value)

    fun setClassReminderEnabled(enabled: Boolean) {
        settings.setClassReminderEnabled(enabled)
        reminderScheduler.rescheduleAsync()
    }

    fun setDefaultRemindBeforeMinutes(value: Int) {
        settings.setDefaultRemindBeforeMinutes(value)
        reminderScheduler.rescheduleAsync()
    }

    /** 每天早上 10:00 的当日汇总开关 */
    fun setDailyDigestEnabled(enabled: Boolean) {
        settings.setDailyDigestEnabled(enabled)
        reminderScheduler.rescheduleAsync()
    }

    /**
     * 立刻发一条今天的汇总，不用等到明天 10:00。
     * 回调参数是今天要上几节：0 表示今天没课（没课就不发，免得通知栏空响）。
     */
    fun previewDigest(onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = runCatching { digestScheduler.postTodayDigest() }.getOrDefault(0)
            withContext(Dispatchers.Main) { onDone(count) }
        }
    }

    /** 系统层面是否允许发通知（Android 13+ 需要用户授权） */
    fun canNotify(): Boolean = notifier.canNotify()

    /** 是否还能设精确闹钟（Android 12+ 需要用户授权） */
    fun exactAlarmAllowed(): Boolean = reminderScheduler.exactAlarmAllowed()

    fun setMonthlyBudget(text: String) {
        val cents = if (text.isBlank()) null else cn.yiweiqiu.lessonledger.core.Money.parseYuan(text)
        settings.setMonthlyBudget(cents)
    }

    fun setDeductionRules(absent: Boolean, leave: Boolean) = settings.setDeductionRules(absent, leave)

    fun buildExportJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val text = repo.exportJson()
            onReady(text)
        }
    }

    fun importJson(text: String) {
        viewModelScope.launch {
            val source = repo.currentSource()
            runCatching { repo.importJson(text) }
                .onSuccess { result ->
                    val taken = result.children + result.courses + result.payments + result.lessons
                    _message.value = when {
                        // 整份备份都属于另一套：直接说清楚该切到哪边，别让用户以为文件坏了
                        taken == 0 && result.skipped > 0 ->
                            "这份备份属于「另一套」数据，当前在「${source.label}」，没有导入任何记录。" +
                                "先切到那一套再导入，两套不会互相覆盖。"
                        result.skipped > 0 ->
                            "导入成功：${result.children} 个孩子 / ${result.courses} 门课 / " +
                                "${result.payments} 笔缴费 / ${result.lessons} 条上课记录；" +
                                "另有 ${result.skipped} 条属于另一套，已跳过。"
                        else ->
                            "导入成功：${result.children} 个孩子 / ${result.courses} 门课 / " +
                                "${result.payments} 笔缴费 / ${result.lessons} 条上课记录"
                    }
                }
                .onFailure { _message.value = "导入失败：${it.message ?: "文件格式不正确"}" }
        }
    }

    /** 清空「正在使用的这一套」：另一套一条都不动 */
    fun clearCurrentSource(onDone: (DemoStats) -> Unit = {}) {
        viewModelScope.launch {
            val source = repo.currentSource()
            val removed = repo.clearSource(source)
            syncStatus.reset()
            _message.value = "已清空「${source.label}」：${removed.summary}"
            onDone(removed)
        }
    }

    /** 切换数据源：只换看哪一套，不删任何记录 */
    fun switchSource(target: DataSource) {
        viewModelScope.launch { repo.switchSource(target) }
    }

    /**
     * 演示数据总开关。关掉时仓库会把「看哪一套」收回「我的数据」，
     * 所以调用方不用自己再补一次切换。
     */
    fun setDemoEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setDemoEnabled(enabled) }
    }

    /** 清掉某一套数据（演示那套清完会自动切回「我的数据」） */
    fun clearSource(source: DataSource, onDone: (DemoStats) -> Unit = {}) {
        viewModelScope.launch {
            val removed = repo.clearSource(source)
            _message.value = "已清空「${source.label}」：${removed.summary}"
            onDone(removed)
        }
    }
}
