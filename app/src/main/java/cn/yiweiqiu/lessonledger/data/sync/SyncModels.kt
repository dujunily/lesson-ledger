package cn.yiweiqiu.lessonledger.data.sync

import cn.yiweiqiu.lessonledger.data.local.entity.ChildEntity
import cn.yiweiqiu.lessonledger.data.local.entity.CourseEntity
import cn.yiweiqiu.lessonledger.data.local.entity.LessonEntity
import cn.yiweiqiu.lessonledger.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

const val SCHEMA_VERSION = 1

@Serializable
data class LedgerSnapshotDto(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long = 0L,
    val deviceId: String = "",
    val children: List<ChildEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val lessons: List<LessonEntity> = emptyList(),
) {
    val recordCount: Int
        get() = children.size + courses.size + payments.size + lessons.size
}

@Serializable
data class ManifestDto(
    val schemaVersion: Int = SCHEMA_VERSION,
    val lastDeviceId: String = "",
    /** 写入方的设备名（夫妻两台手机时用来区分“谁最后改的”） */
    val lastDeviceName: String = "",
    val lastWriteAt: Long = 0L,
    val dataEtag: String? = null,
    val recordCount: Int = 0,
)

enum class SyncPhase {
    NOT_CONFIGURED,
    SYNCED,
    SYNCING,
    OFFLINE,
    CONFLICT,
    AUTH_FAILED,
    SERVER_ERROR,
    UPGRADE_REQUIRED,
    SPACE_FULL,
}

data class SyncStatus(
    val phase: SyncPhase = SyncPhase.NOT_CONFIGURED,
    val pendingCount: Int = 0,
    val lastSuccessAt: Long = 0L,
    val message: String = "",
    val conflictCount: Int = 0,
)

sealed interface SyncOutcome {
    data object Success : SyncOutcome
    data object Fresh : SyncOutcome
    data object NotConfigured : SyncOutcome
    data class Failure(
        val phase: SyncPhase,
        val message: String,
        val retryable: Boolean,
    ) : SyncOutcome
}

@Singleton
class SyncStatusHolder @Inject constructor() {
    private val _state = MutableStateFlow(SyncStatus())
    val state: StateFlow<SyncStatus> = _state.asStateFlow()

    fun update(block: (SyncStatus) -> SyncStatus) = _state.update(block)

    fun reset() = _state.update { SyncStatus() }
}
