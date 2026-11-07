package cn.yiweiqiu.lessonledger.data.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import cn.yiweiqiu.lessonledger.MainActivity
import cn.yiweiqiu.lessonledger.R
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.Lesson
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 四个桌面小组件共享的数据与交互：
 *
 * - 数据快照：今天的课次（带课程/孩子信息）+ 下一节待上课次；
 * - 「一键打卡」把今天所有待打卡课次标为已上（与打卡页同口径）；
 * - 点组件本体落打卡页；PendingIntent 构造器共用。
 *
 * 注意：不使用 @AndroidEntryPoint —— Hilt 对 AppWidgetProvider 的字段注入在
 * 广播冷启动路径上不可靠（实测 lateinit 未初始化崩溃），改为 EntryPoint 手动取。
 */
object WidgetCommon {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepoEntryPoint {
        fun repo(): LedgerRepository
    }

    fun repo(context: Context): LedgerRepository =
        EntryPointAccessors.fromApplication(context.applicationContext, RepoEntryPoint::class.java).repo()

    /**
     * 小组件后台任务统一入口：任何异常只记日志、绝不外抛——
     * 未捕获异常会让系统直接杀进程（就是上午两次闪退的路径），
     * 小组件渲染失败顶多显示旧数据，不该拖垮整个 App。
     * 同时保证 goAsync() 的 PendingResult 一定 finish。
     */
    fun launchSafely(pending: BroadcastReceiver.PendingResult, block: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } catch (t: Throwable) {
                Log.w(TAG, "widget task failed", t)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    /** 小组件渲染用的一行课次 */
    data class LessonRow(
        val lesson: Lesson,
        val time: String,
        val courseName: String,
        val childName: String,
        val status: LessonStatus,
    )

    data class Snapshot(
        val today: LocalDate,
        val todayRows: List<LessonRow>,
        val waiting: Int,
        val next: LessonRow?,
    ) {
        val total: Int get() = todayRows.size
    }

    suspend fun snapshot(context: Context): Snapshot {
        val repo = repo(context)
        val today = Dates.today()
        val data = repo.data.first()
        val courseById = data.courses.associateBy { it.id }
        val childById = data.children.associateBy { it.id }

        fun rowOf(lesson: Lesson): LessonRow? {
            val course = courseById[lesson.courseId] ?: return null
            val child = childById[course.childId]
            return LessonRow(
                lesson = lesson,
                time = Dates.formatTime(lesson.scheduledAt),
                courseName = course.courseName,
                childName = child?.displayName ?: "",
                status = lesson.status,
            )
        }

        val todayRows = data.lessons
            .filter { Dates.toDate(it.scheduledAt) == today }
            .sortedBy { it.scheduledAt }
            .mapNotNull(::rowOf)

        val now = Dates.now()
        val next = data.lessons
            .filter {
                it.status == LessonStatus.PENDING &&
                    it.scheduledAt >= Dates.startOfDay(today) &&
                    it.scheduledAt >= now - 90 * 60_000L // 刚开课不到 90 分钟的也算"下一节"
            }
            .minByOrNull { it.scheduledAt }
            ?.let(::rowOf)

        return Snapshot(
            today = today,
            todayRows = todayRows,
            waiting = todayRows.count { it.status == LessonStatus.PENDING },
            next = next,
        )
    }

    /** 把今天所有待打卡的课次标为「已上」（和打卡页按下「已上」完全同一口径） */
    suspend fun markTodayDone(context: Context) {
        val today = Dates.today()
        val repo = repo(context)
        val data = repo.data.first()
        val courseById = data.courses.associateBy { it.id }
        data.lessons
            .filter {
                Dates.toDate(it.scheduledAt) == today &&
                    it.status == LessonStatus.PENDING &&
                    it.courseId in courseById
            }
            // 打卡扣的课时按这节课的实际时长折算：课程可能平时 60 分钟、周末 90 分钟，
            // 一律扣 1 节账就对不上了。找不到课程（数据还没刷出来）时回落到 1 节。
            .forEach { lesson ->
                val course = courseById[lesson.courseId]
                val half = if (course == null) {
                    2L
                } else {
                    LedgerCalc.deductedFromDuration(lesson.durationMinutes, course.durationMinutes)
                }
                repo.setLessonStatus(lesson.id, LessonStatus.DONE, deductedHalf = half)
            }
    }

    /** 点组件其他位置 → App 打卡页 */
    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_CHECKIN, true)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 「一键打卡」按钮的广播 PendingIntent（各 Provider 用不同 requestCode 互不冲突） */
    fun markDone(context: Context, provider: Class<*>, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, provider).setAction(WidgetCommon.ACTION_MARK_DONE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 下一节课的人话时间：今天 16:00 / 明天 09:00 / 10月31日(周六) 09:00 */
    fun nextTimeText(at: Long): String {
        val dt: LocalDateTime = Dates.toDateTime(at)
        val today = Dates.today()
        val days = ChronoUnit.DAYS.between(today, dt.toLocalDate())
        val hm = "%02d:%02d".format(dt.hour, dt.minute)
        return when (days) {
            0L -> "今天 $hm"
            1L -> "明天 $hm"
            2L -> "后天 $hm"
            else -> "${dt.monthValue}月${dt.dayOfMonth}日${Dates.weekdayNames[Dates.weekdayOf(dt.toLocalDate())]} $hm"
        }
    }

    /** 状态标签的文字颜色 */
    fun statusColorRes(status: LessonStatus): Int = when (status) {
        LessonStatus.PENDING -> R.color.widget_status_pending
        LessonStatus.DONE -> R.color.widget_status_done
        LessonStatus.LEAVE, LessonStatus.ABSENT -> R.color.widget_status_warning
        LessonStatus.RESCHEDULED, LessonStatus.CANCELLED -> R.color.widget_status_muted
    }

    /**
     * 数据变了叫醒所有小组件（App 回前台/打卡操作后调用）。
     * 显式广播只发给自己的 Provider，不需要任何导出权限。
     */
    fun refreshAll(context: Context) {
        listOf(
            CheckinWidgetProvider::class.java,
            TodayListWidgetProvider::class.java,
            NextLessonWidgetProvider::class.java,
            QuickMarkWidgetProvider::class.java,
        ).forEach { cls ->
            runCatching {
                context.sendBroadcast(
                    Intent(context, cls).setAction(ACTION_REFRESH),
                )
            }
        }
    }

    const val ACTION_MARK_DONE = "cn.yiweiqiu.lessonledger.action.WIDGET_MARK_DONE"
    const val ACTION_REFRESH = "cn.yiweiqiu.lessonledger.action.WIDGET_REFRESH"

    private const val TAG = "LessonWidget"
}
