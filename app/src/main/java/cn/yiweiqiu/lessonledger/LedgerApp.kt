package cn.yiweiqiu.lessonledger

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import cn.yiweiqiu.lessonledger.data.reminder.ReminderNotifier
import cn.yiweiqiu.lessonledger.data.reminder.ReminderScheduler
import cn.yiweiqiu.lessonledger.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LedgerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var notifier: ReminderNotifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.ensurePeriodicWork()
        // 通知渠道要先建好，否则第一条提醒可能发不出去
        notifier.ensureChannel()
        // 冷启动重排一次闹钟（设备重启 / 应用更新后闹钟会被清空）
        reminderScheduler.rescheduleAsync()
    }
}
