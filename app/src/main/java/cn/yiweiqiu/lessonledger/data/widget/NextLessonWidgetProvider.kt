package cn.yiweiqiu.lessonledger.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import cn.yiweiqiu.lessonledger.R

/**
 * 桌面小组件「下一节课」：
 *
 * - 显示最近一节待上课次：课程名大字 + 孩子 · 机构 + 人话时间（今天 16:00 / 明天 09:00 / …）；
 * - 没有待上课次时显示「近期没有待上课次」；
 * - 点组件 → App 打卡页。
 */
class NextLessonWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetCommon.ACTION_REFRESH -> renderAsync(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAsync(context)
    }

    private fun renderAsync(context: Context) {
        val app = context.applicationContext
        WidgetCommon.launchSafely(goAsync()) { pushAll(app) }
    }

    private suspend fun pushAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, NextLessonWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val snap = WidgetCommon.snapshot(context)
        val next = snap.next

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_next).apply {
                if (next == null) {
                    setTextViewText(R.id.next_course, "近期没有待上课次")
                    setTextViewText(R.id.next_detail, "去课程页排课或添加课次")
                    setTextViewText(R.id.next_time, "")
                } else {
                    setTextViewText(R.id.next_course, next.courseName)
                    setTextViewText(
                        R.id.next_detail,
                        buildString {
                            if (next.childName.isNotBlank()) append(next.childName)
                        }.ifBlank { " " },
                    )
                    setTextViewText(R.id.next_time, WidgetCommon.nextTimeText(next.lesson.scheduledAt))
                }
                setOnClickPendingIntent(R.id.next_root, WidgetCommon.openApp(context))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
