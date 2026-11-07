package cn.yiweiqiu.lessonledger.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.data.local.dao.ChildDao
import cn.yiweiqiu.lessonledger.data.local.dao.CourseDao
import cn.yiweiqiu.lessonledger.data.local.dao.LessonDao
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.DemoIds
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上课提醒排程器。
 *
 * 思路：不用 WorkManager（它的最小延迟粒度是 15 分钟，做不到"上课前 30 分钟准时提醒"），
 * 直接用 AlarmManager 精确闹钟。
 *
 * 排程依据是 lessons 表里的"待上"课次 —— 那些课次由 ensureUpcomingLessons()
 * 按课程 weekdayMask + startTime 自动生成未来两周，每次排课/打卡/启动 App 都会重排一遍。
 *
 * 三种触发时机：
 * 1. 正常：上课的这一刻 —— setExactAndAllowWhileIdle，设备休眠也能准时响
 * 2. 补提醒：闹钟因为刚装 App / 刚授权 / 刚改课表而错过，但课还没开始
 *    —— 立刻补一条 "该打卡了"
 * 3. 兜底：系统不允许精确闹钟时退化成 setWindow，最多晚 10 分钟
 *
 * 注意：提前 15/30/60/120 分钟的做法已经取消了。家长要的是"到点提醒我打卡"，
 * 提前半小时那条往往人已经在路上，既不解决忘打卡，还一天响好几遍。
 * 现在只有两条通知：每天早上 10 点的当日汇总（[DigestScheduler]）+ 每节课开始时刻这一条。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseDao: CourseDao,
    private val lessonDao: LessonDao,
    private val childDao: ChildDao,
    private val settings: SettingsStore,
    private val digestScheduler: DigestScheduler,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** 记录已经排过闹钟的课次，重新排程时据此取消多余的闹钟 */
    private val track = ReminderTrack(context)

    /** 异步重排；UI 侧调用不会卡住主线程 */
    fun rescheduleAsync() {
        scope.launch { runCatching { reschedule() } }
    }

    suspend fun reschedule() {
        val enabled = settings.current.classReminderEnabled
        val now = Dates.now()
        val horizon = now + HORIZON_MS

        val courses = courseDao.getAll().filter {
            !it.deleted && DemoIds.isDemo(it.id) == settings.current.effectiveSource.isDemo
        }
        val courseById = courses.associateBy { it.id }
        val lessons = lessonDao.getAll().filter {
            !it.deleted && it.status == LessonStatus.PENDING.name && it.scheduledAt in now..horizon
        }

        val plan = LinkedHashMap<String, Long>()   // "lessonId@scheduledAt" -> 触发时刻
        if (enabled) {
            for (lesson in lessons) {
                val course = courseById[lesson.courseId] ?: continue
                if (course.status != CourseStatus.ACTIVE.name) continue
                // remindBeforeMinutes 现在只有两种语义：0 = 这门课不提醒，非 0 = 上课时刻提醒。
                // 老数据里的 15/30/60/120 一律按"上课时刻"处理，不需要迁移。
                if (course.remindBeforeMinutes <= 0) continue
                val fireAt = lesson.scheduledAt
                val entry = "${lesson.id}@${lesson.scheduledAt}"
                plan[entry] = when {
                    fireAt > now -> fireAt
                    // 闹钟错过了但课还没开始（刚装 App / 刚开通知权限）：立刻补一条
                    lesson.scheduledAt > now && !track.isNotified(lesson.id, lesson.scheduledAt) ->
                        now + CATCH_UP_DELAY_MS
                    else -> continue
                }
            }
        }

        // 先取消这次不该再响的闹钟（课已打卡 / 课程被删 / 提醒被关掉 / 课次改了时间）
        val previous = track.scheduledIds()
        for (entry in previous) {
            if (!plan.containsKey(entry)) {
                cancelAlarm(ReminderTrack.lessonIdOf(entry))
            }
        }

        for ((entry, fireAt) in plan) {
            scheduleAlarm(ReminderTrack.lessonIdOf(entry), fireAt)
        }
        track.setScheduledIds(plan.keys)

        // 每日 10:00 的汇总跟课表强相关（删课、改时间、换数据源都要跟着变），
        // 放在这里一起重排，省得每个入口都记得单独调一次。
        digestScheduler.scheduleNext()
    }

    /** 取消某一节课的闹钟（打卡后立即调用，避免还响） */
    fun cancel(lessonId: String) {
        cancelAlarm(lessonId)
        val remaining = track.scheduledIds() - lessonId
        track.setScheduledIds(remaining)
    }

    private fun scheduleAlarm(lessonId: String, fireAt: Long) {
        val manager = alarmManager ?: return
        val operation = alarmPendingIntent(lessonId)
        try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation)
            } else {
                // 没有精确闹钟权限：退化成窗口闹钟（最早 fireAt 起 10 分钟内触发）
                manager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    fireAt,
                    WINDOW_MS,
                    operation,
                )
            }
        } catch (e: SecurityException) {
            manager.setWindow(AlarmManager.RTC_WAKEUP, fireAt, WINDOW_MS, operation)
        }
    }

    private fun cancelAlarm(lessonId: String) {
        val manager = alarmManager ?: return
        manager.cancel(alarmPendingIntent(lessonId))
    }

    private fun alarmPendingIntent(lessonId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            data = android.net.Uri.parse("lessonledger://remind/$lessonId")
            putExtra(ReminderReceiver.EXTRA_LESSON_ID, lessonId)
        }
        return PendingIntent.getBroadcast(
            context,
            lessonId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Android 12+ 需要用户授权才能设精确闹钟 */
    private fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /** 供 UI 显示：当前能否使用精确闹钟 */
    fun exactAlarmAllowed(): Boolean = canScheduleExact()

    companion object {
        /** 排程窗口：两周，和自动排课的生成范围保持一致 */
        const val HORIZON_MS = 14L * 24 * 60 * 60 * 1000
        private const val WINDOW_MS = 10L * 60 * 1000
        private const val CATCH_UP_DELAY_MS = 5_000L
    }
}
