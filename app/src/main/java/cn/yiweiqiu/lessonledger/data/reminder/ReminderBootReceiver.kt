package cn.yiweiqiu.lessonledger.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 开机 / 应用更新后重排闹钟。
 * 系统重启会清空所有 AlarmManager 闹钟，不重排的话提醒就失效了。
 */
@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.reschedule()
            } catch (e: Exception) {
                // 忽略
            } finally {
                pending.finish()
            }
        }
    }
}
