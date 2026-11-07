package cn.yiweiqiu.lessonledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.data.local.entity.SyncLogEntity
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.data.sync.DavClient
import cn.yiweiqiu.lessonledger.data.sync.SyncPhase
import cn.yiweiqiu.lessonledger.data.sync.SyncScheduler
import cn.yiweiqiu.lessonledger.data.sync.SyncStatus
import cn.yiweiqiu.lessonledger.data.sync.SyncStatusHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SyncDetailViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val settings: SettingsStore,
    private val dav: DavClient,
    private val syncStatus: SyncStatusHolder,
    private val scheduler: SyncScheduler,
) : ViewModel() {

    data class ConflictItem(
        val id: String,
        val type: String,
        val title: String,
        val note: String,
    )

    data class TestResult(
        val ok: Boolean,
        val message: String,
        val quotaAvailable: Long? = null,
        val quotaUsed: Long? = null,
        val loading: Boolean = false,
    )

    data class UiState(
        val status: SyncStatus = SyncStatus(),
        val lastSyncAt: Long = 0L,
        val configured: Boolean = false,
        val rootDir: String = "",
        val deviceName: String = "",
        val email: String = "",
        val appPassword: String = "",
        val logs: List<SyncLogEntity> = emptyList(),
        val conflicts: List<ConflictItem> = emptyList(),
    )

    val uiState = combine(
        repo.data,
        repo.observeSyncLogs(20),
        settings.state,
        syncStatus.state,
    ) { data, logs, setting, status ->
        val conflicts = buildList {
            data.courses.filter { it.note.startsWith("[冲突副本]") }.forEach {
                add(ConflictItem(it.id, "课程", it.courseName, it.note))
            }
            data.payments.filter { it.note.startsWith("[冲突副本]") }.forEach {
                add(ConflictItem(it.id, "缴费", "${it.payDate} ${it.amountCents}", it.note))
            }
            data.lessons.filter { it.note.startsWith("[冲突副本]") }.forEach {
                add(ConflictItem(it.id, "课次", "${it.scheduledAt}", it.note))
            }
            data.children.filter { it.note.startsWith("[冲突副本]") }.forEach {
                add(ConflictItem(it.id, "孩子", it.name, it.note))
            }
        }
        UiState(
            status = status,
            lastSyncAt = setting.lastSyncAt,
            configured = setting.isConfigured,
            rootDir = setting.rootDirNormalized,
            deviceName = setting.deviceName,
            email = setting.email,
            appPassword = setting.appPassword,
            logs = logs,
            conflicts = conflicts,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun syncNow() = scheduler.syncNow("manual-detail")

    private val _cleanupMsg = MutableStateFlow<String?>(null)
    val cleanupMsg = _cleanupMsg.asStateFlow()

    /**
     * 一键清理所有冲突副本（软删除，会同步成墓碑到其他设备）。
     * 之前只能一条条手动删，副本一多就没人愿意处理了。
     */
    fun cleanupConflicts() {
        viewModelScope.launch {
            val n = repo.deleteConflictCopies()
            _cleanupMsg.value = if (n > 0) "已清理 $n 条重复/无效记录，正在同步…" else "没有需要清理的记录"
            if (n > 0) scheduler.syncNow("cleanup-conflicts")
        }
    }

    fun saveAccount(email: String, password: String) {
        settings.updateAccount(email, password)
        _testResult.value = null
    }

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult = _testResult.asStateFlow()

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
}
