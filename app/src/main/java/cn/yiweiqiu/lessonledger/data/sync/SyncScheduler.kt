package cn.yiweiqiu.lessonledger.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步触发调度：
 * - 写操作后 debounce 8 秒（避免连续打卡触发十几次请求）
 * - App 启动 / 回前台立即尝试（回前台带 60 秒去重：刚同步过且本地无改动就不再发请求）
 * - 前台每 10 分钟静默轮询一次（不闪「同步中…」、不写空转日志）
 * - 失败按 30s → 2min → 10min → 1h 指数退避，最多 5 次
 * - WorkManager 每 6 小时兜底
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: SyncEngine,
    private val settings: SettingsStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var debounceJob: Job? = null
    private var retryJob: Job? = null
    /** 正在跑的那一轮；手动连点「同步」不再叠请求，否则坚果云会按频率直接回 503 */
    private var activeJob: Job? = null
    private var retryAttempt = 0

    private val retryDelays = listOf(30_000L, 120_000L, 600_000L, 3_600_000L, 3_600_000L)

    fun requestSync(delayMs: Long = 8_000L, trigger: String = "auto") {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            runSync(trigger, quiet = false)
        }
    }

    /**
     * @param quiet 后台轮询用：界面不显示「同步中…」，也不记空转日志
     * @param minIntervalMs 大于 0 时，若「上一次成功同步距今不到这个时间」且本地没有待上传改动，直接跳过、不发任何请求。
     *                     用来吃掉「启动 + 回前台」这类一进 App 就触发的重复同步。
     */
    fun syncNow(trigger: String = "manual", quiet: Boolean = false, minIntervalMs: Long = 0L) {
        debounceJob?.cancel()
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            if (minIntervalMs > 0L && recentlySynced(minIntervalMs)) return@launch
            runSync(trigger, quiet)
        }
    }

    private suspend fun recentlySynced(minIntervalMs: Long): Boolean {
        val last = settings.current.lastSyncAt
        if (last <= 0L) return false
        if (Dates.now() - last >= minIntervalMs) return false
        // 本地刚改过东西就一定得同步，不能因为"时间还早"把改动压在本机
        return runCatching { engine.dirtyCount(last) }.getOrDefault(1) == 0
    }

    private suspend fun runSync(trigger: String, quiet: Boolean) {
        when (val outcome = engine.sync(trigger, quiet)) {
            is SyncOutcome.Success, SyncOutcome.Fresh -> {
                retryAttempt = 0
                settings.updateRetry(0)
            }
            is SyncOutcome.Failure -> if (outcome.retryable) scheduleRetry()
            SyncOutcome.NotConfigured -> Unit
        }
    }

    private fun scheduleRetry() {
        if (retryAttempt >= retryDelays.size) return
        val wait = retryDelays[retryAttempt]
        retryAttempt++
        settings.updateRetry(retryAttempt)
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(wait)
            runSync("retry-$retryAttempt", quiet = false)
        }
    }

    fun ensurePeriodicWork() {
        val networkType = if (settings.current.syncWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelAll() {
        debounceJob?.cancel()
        retryJob?.cancel()
        retryAttempt = 0
    }

    companion object {
        const val WORK_NAME = "lesson-ledger-periodic-sync"
    }
}
