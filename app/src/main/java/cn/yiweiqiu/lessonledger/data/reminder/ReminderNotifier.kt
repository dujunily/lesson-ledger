package cn.yiweiqiu.lessonledger.data.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.yiweiqiu.lessonledger.MainActivity
import cn.yiweiqiu.lessonledger.R
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.data.local.entity.CourseEntity
import cn.yiweiqiu.lessonledger.data.local.entity.LessonEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知的构造与发送。
 *
 * 通知内容刻意做成"时间 + 人 + 地点"三件套：家长在地铁上看到就能判断"是不是该走了"。
 *
 * 只有两类通知：
 * 1. 每天 10:00 的当日汇总 [notifyDigest]
 * 2. 上课那一刻的"该打卡了" [notify]
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "上课提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "每天上午汇总今天要上的课，上课时提醒你去打卡"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(channel)
    }

    /** 系统是否允许发通知（Android 13+ 需要用户授权） */
    fun canNotify(): Boolean = manager.areNotificationsEnabled()

    fun notify(lesson: LessonEntity, course: CourseEntity, childName: String?) {
        ensureChannel()
        if (!canNotify()) return

        val now = Dates.now()
        val remainMin = ((lesson.scheduledAt - now) / 60_000L).coerceAtLeast(0L).toInt()
        val courseName = course.courseName.ifBlank { course.orgName.ifBlank { "课程" } }
        // 这条通知的唯一目的就是让人别忘打卡，所以到点后别再写"正在上课"这种陈述句
        val title = if (remainMin > 5) {
            "「$courseName」还有 $remainMin 分钟上课"
        } else {
            "「$courseName」到点啦，记得打卡"
        }

        val time = Dates.formatTime(lesson.scheduledAt)
        val who = childName?.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
        val where = listOfNotNull(
            course.orgName.takeIf { it.isNotBlank() },
            course.location?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val teacher = course.teacher?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val text = (who + time + (if (where.isNotBlank()) " · $where" else "") + teacher).trim()

        val contentIntent = PendingIntent.getActivity(
            context,
            lesson.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_CHECKIN, true)
                putExtra(MainActivity.EXTRA_LESSON_ID, lesson.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val actionIntent = PendingIntent.getActivity(
            context,
            lesson.id.hashCode() + 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_CHECKIN, true)
                putExtra(MainActivity.EXTRA_LESSON_ID, lesson.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_class)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .addAction(0, "去打卡", actionIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_ID)
            .build()

        try {
            manager.notify(lesson.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // 用户在系统设置里关掉了通知权限，忽略即可
        }
    }

    /**
     * 每天早上 10:00 的当日汇总。
     *
     * 一节课就把课名写进标题、并给「去打卡」按钮（点两下完事）；
     * 多节课标题只写条数，正文一行一节，点进来是首页今天的课表。
     */
    fun notifyDigest(entries: List<DigestEntry>) {
        ensureChannel()
        if (!canNotify() || entries.isEmpty()) return

        val single = entries.singleOrNull()
        val title = if (single != null) {
            "今天 ${single.time}「${single.courseName}」"
        } else {
            "今天 ${entries.size} 节课"
        }
        val oneLine = entries.joinToString(" · ") { "${it.time} ${it.courseName}" }
        val multiLine = entries.joinToString("\n") { e ->
            buildString {
                append(e.time).append(' ').append(e.courseName)
                val extra = listOfNotNull(
                    e.childName?.takeIf { it.isNotBlank() },
                    e.place?.takeIf { it.isNotBlank() },
                ).distinct().joinToString(" · ")
                if (extra.isNotBlank()) append("（").append(extra).append('）')
            }
        }

        // 只有一节课才直接跳打卡；多节课跳首页，让用户自己挑是哪一节课
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (single != null) {
                putExtra(MainActivity.EXTRA_OPEN_CHECKIN, true)
                putExtra(MainActivity.EXTRA_LESSON_ID, single.lessonId)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            DIGEST_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_class)
            .setContentTitle(title)
            .setContentText(oneLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(multiLine))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_ID)

        if (single != null) builder.addAction(0, "去打卡", contentIntent)

        try {
            manager.notify(DIGEST_NOTIFY_ID, builder.build())
        } catch (e: SecurityException) {
            // 通知权限被关掉了，忽略
        }
    }

    companion object {
        const val CHANNEL_ID = "lesson_reminder"
        const val GROUP_ID = "lesson_reminder_group"

        /** 汇总通知固定一个 ID：每天覆盖同一条，通知栏不会堆一串 */
        private const val DIGEST_NOTIFY_ID = 0x0D16E5
        private const val DIGEST_REQUEST_CODE = 0x0D16E6
    }
}
