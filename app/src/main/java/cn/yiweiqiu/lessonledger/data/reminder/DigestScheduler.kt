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
import cn.yiweiqiu.lessonledger.data.settings.AppSettings
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.DemoIds
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每天早上 10:00 的「今天有课」汇总提醒。
 *
 * 为什么单独一个闹钟：以前每节课各排一个"提前 N 分钟"的闹钟，一天三节课就响三次，
 * 而且提前半小时那声往往人已经在路上，既解决不了忘打卡，还容易被静音。
 * 现在收敛成两条通知：
 * 1. 早上 10:00 —— 今天几节课、几点、在哪，一条看完（本类）
 * 2. 每节课开始那一刻 —— "该打卡了"（[ReminderScheduler]）
 *
 * 排程时机：App 启动 / 回到前台 / 打卡后 / 改课表后 / 开机，都走 [ReminderScheduler.reschedule]
 * 顺带把下一次 10:00 排上；闹钟响过之后再自己排明天。
 */
@Singleton
class DigestScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val lessonDao: LessonDao,
    private val courseDao: CourseDao,
    private val childDao: ChildDao,
    private val notifier: ReminderNotifier,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * 排下一次 10:00。关掉开关就只取消、不排。
     *
     * 每次都先 cancel 再 set：AlarmManager 里同一个 PendingIntent 是覆盖写，
     * 显式 cancel 只是为了让"关掉开关"这条路径也能立刻生效。
     */
    fun scheduleNext() {
        val manager = alarmManager ?: return
        val operation = pendingIntent()
        manager.cancel(operation)
        if (!settings.current.dailyDigestEnabled) return

        val at = nextFireAt()
        try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, operation)
            }
        } catch (e: SecurityException) {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, operation)
        }
    }

    /**
     * 下一次 10:00 的时间戳。
     *
     * 离现在不足 1 分钟时直接排到明天：否则用户 09:59:30 打开 App 会立刻收到一条，
     * 紧接着 10:00 又来一条，重复打扰。
     */
    fun nextFireAt(now: Long = Dates.now()): Long = nextDigestAt(now)

    /**
     * 把今天的课程汇总发出去，返回今天要上几节（0 表示没课、不发通知）。
     *
     * 闹钟触发时调一次。查询放在触发时刻而不是排程时刻：这样中途改了课表、或者
     * 早上在另一台手机上把课打卡了，10 点看到的一定是最新的。
     */
    suspend fun postTodayDigest(now: Long = Dates.now()): Int {
        if (!settings.current.dailyDigestEnabled) return 0

        val isDemo = settings.current.effectiveSource.isDemo
        val dayStart = Dates.startOfDay(Dates.toDate(now))
        val dayEnd = Dates.endOfDay(Dates.toDate(now))

        val courses = courseDao.getAll()
            .filter { !it.deleted && DemoIds.isDemo(it.id) == isDemo }
            .associateBy { it.id }

        val todays = lessonDao.getAll()
            .filter {
                !it.deleted &&
                    it.status == LessonStatus.PENDING.name &&
                    it.scheduledAt in dayStart..dayEnd &&
                    courses[it.courseId]?.status == CourseStatus.ACTIVE.name
            }
            .sortedBy { it.scheduledAt }

        if (todays.isEmpty()) return 0

        val entries = todays.mapNotNull { lesson ->
            val course = courses[lesson.courseId] ?: return@mapNotNull null
            DigestEntry(
                lessonId = lesson.id,
                time = Dates.formatTime(lesson.scheduledAt),
                courseName = course.courseName.ifBlank { course.orgName.ifBlank { "课程" } },
                childName = childDao.byId(course.childId)?.name,
                place = course.orgName.takeIf { it.isNotBlank() && it != course.courseName },
            )
        }
        if (entries.isEmpty()) return 0

        notifier.notifyDigest(entries)
        return entries.size
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, DigestReceiver::class.java).apply {
            action = DigestReceiver.ACTION_DIGEST
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private companion object {
        const val REQUEST_CODE = 0x0D16E5
        const val WINDOW_MS = 10L * 60 * 1000
    }
}

/** 离 10:00 不足这么点时间就不发今天的了，直接排明天 */
internal const val DIGEST_MIN_LEAD_MS = 60_000L

/**
 * 下一次 10:00 的时间戳。抽成纯函数是为了能单测跨天（23:00、09:59、正好 10:00）。
 */
internal fun nextDigestAt(now: Long): Long {
    val tenAm = LocalTime.of(AppSettings.DIGEST_HOUR, 0)
    val todayAt = Dates.toMillis(Dates.toDate(now), tenAm)
    if (todayAt - now > DIGEST_MIN_LEAD_MS) return todayAt
    return Dates.toMillis(Dates.toDate(now).plusDays(1), tenAm)
}

/** 汇总里的一节课，够渲染一行"时间 + 课名 + 孩子" */
data class DigestEntry(
    val lessonId: String,
    val time: String,
    val courseName: String,
    val childName: String?,
    val place: String?,
)
