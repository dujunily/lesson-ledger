package cn.yiweiqiu.lessonledger.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.yiweiqiu.lessonledger.data.widget.WidgetCommon
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 早上 10:00 的闹钟响了 → 发当天课程汇总 → 顺手排好明天的那一个。
 *
 * 排下一次必须放在这里：AlarmManager 的闹钟是一次性的，不在响应里续排，
 * 第二天就再也不会响了（这也是很多人写每日提醒踩过的坑）。
 */
@AndroidEntryPoint
class DigestReceiver : BroadcastReceiver() {

    @Inject
    lateinit var digestScheduler: DigestScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DIGEST) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                digestScheduler.postTodayDigest()
                // 桌面小组件已经不再让系统每 30 分钟定时唤醒（updatePeriodMillis=0），
                // 这里反正已经被闹钟叫醒了，就当当天第一个刷新点：
                // 不看 App 的人早上看桌面也能看到今天的课。
                WidgetCommon.refreshAll(appContext)
            } catch (e: Exception) {
                // 发通知失败不能拖垮系统广播
            } finally {
                try {
                    digestScheduler.scheduleNext()
                } catch (e: Exception) {
                    // 排程失败下次启动/回前台还会重排
                }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DIGEST = "cn.yiweiqiu.lessonledger.action.DAILY_DIGEST"
    }
}
