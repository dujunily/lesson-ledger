package cn.yiweiqiu.lessonledger.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.yiweiqiu.lessonledger.data.local.dao.ChildDao
import cn.yiweiqiu.lessonledger.data.local.dao.CourseDao
import cn.yiweiqiu.lessonledger.data.local.dao.LessonDao
import cn.yiweiqiu.lessonledger.data.widget.WidgetCommon
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 闹钟响了 → 发通知。
 *
 * 发之前重新查一次数据库：这节课可能已经在另一台设备上打过卡了（夫妻两台手机），
 * 或者课程已被删除，这时就不该再打扰用户。
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var lessonDao: LessonDao

    @Inject
    lateinit var courseDao: CourseDao

    @Inject
    lateinit var childDao: ChildDao

    @Inject
    lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val lessonId = intent.getStringExtra(EXTRA_LESSON_ID) ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lesson = lessonDao.byId(lessonId)
                if (lesson != null && !lesson.deleted && lesson.status == LessonStatus.PENDING.name) {
                    val course = courseDao.byId(lesson.courseId)
                    if (course != null && !course.deleted && course.status == CourseStatus.ACTIVE.name) {
                        val child = childDao.byId(course.childId)
                        notifier.notify(lesson, course, child?.name)
                        ReminderTrack(appContext).markNotified(lesson.id, lesson.scheduledAt)
                        // 到点提醒时顺手刷一次桌面小组件：这节课一上完，
                        // 「今日待打卡 / 下一节」的内容就变了，而组件已经不再定时自刷。
                        WidgetCommon.refreshAll(appContext)
                    }
                }
            } catch (e: Exception) {
                // 提醒失败不能拖垮系统广播
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "cn.yiweiqiu.lessonledger.action.REMIND"
        const val EXTRA_LESSON_ID = "lesson_id"
    }
}
