package cn.yiweiqiu.lessonledger.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.yiweiqiu.lessonledger.data.widget.WidgetCommon
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (val outcome = engine.sync("workmanager")) {
        // 只有真的拉到变化才刷组件：Fresh 表示远端没动，没必要唤醒桌面。
        // 组件已不再让系统每 30 分钟定时自刷，另一台手机的改动就靠这里落到桌面。
        is SyncOutcome.Success -> {
            WidgetCommon.refreshAll(applicationContext)
            Result.success()
        }
        SyncOutcome.Fresh, SyncOutcome.NotConfigured -> Result.success()
        is SyncOutcome.Failure -> if (outcome.retryable) Result.retry() else Result.failure()
    }
}
