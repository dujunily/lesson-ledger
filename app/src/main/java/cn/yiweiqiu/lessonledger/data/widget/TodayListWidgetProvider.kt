package cn.yiweiqiu.lessonledger.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import cn.yiweiqiu.lessonledger.R

/**
 * 桌面小组件「今日课程」：
 *
 * - 头部：日期 + 「共 N 节 · M 节待打卡」；
 * - 中间：今天每节课一行（时间 / 课程 · 孩子 / 状态）；
 * - 底部：有待打卡时显示「✓ 今天上课了」一键打卡按钮；
 * - 点组件其他位置 → App 打卡页。
 *
 * 行用 RemoteViews.addView 逐行拼（今天的课通常 ≤ 4 节，不需要 RemoteViewsService）。
 */
class TodayListWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetCommon.ACTION_MARK_DONE -> {
                val app = context.applicationContext
                WidgetCommon.launchSafely(goAsync()) {
                    WidgetCommon.markTodayDone(app)
                    WidgetCommon.refreshAll(app)
                }
            }
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
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayListWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val snap = WidgetCommon.snapshot(context)
        val dateHead = "${snap.today.monthValue}月${snap.today.dayOfMonth}日 " +
            cn.yiweiqiu.lessonledger.core.Dates.weekdayNames[
                cn.yiweiqiu.lessonledger.core.Dates.weekdayOf(snap.today),
            ]
        val summary = when {
            snap.waiting > 0 -> "${snap.total} 节 · ${snap.waiting} 待打卡"
            snap.total > 0 -> "${snap.total} 节 · 都上完了"
            else -> "今天没排课"
        }
        val canMark = snap.waiting > 0

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_today_list).apply {
                setTextViewText(R.id.list_date, dateHead)
                setTextViewText(R.id.list_summary, summary)
                // 行区
                removeAllViews(R.id.list_rows)
                if (snap.todayRows.isEmpty()) {
                    addView(
                        R.id.list_rows,
                        RemoteViews(context.packageName, R.layout.widget_lesson_row).apply {
                            setTextViewText(R.id.row_time, "")
                            setTextViewText(R.id.row_title, "今天没有排课，去课程页看看吧")
                            setTextViewText(R.id.row_status, "")
                        },
                    )
                } else {
                    snap.todayRows.take(5).forEach { row ->
                        addView(
                            R.id.list_rows,
                            RemoteViews(context.packageName, R.layout.widget_lesson_row).apply {
                                setTextViewText(R.id.row_time, row.time)
                                setTextViewText(
                                    R.id.row_title,
                                    buildString {
                                        append(row.courseName)
                                        if (row.childName.isNotBlank()) append(" · ").append(row.childName)
                                    },
                                )
                                setTextViewText(R.id.row_status, row.status.label)
                                setTextColor(R.id.row_status, context.getColor(WidgetCommon.statusColorRes(row.status)))
                            },
                        )
                    }
                    if (snap.todayRows.size > 5) {
                        addView(
                            R.id.list_rows,
                            RemoteViews(context.packageName, R.layout.widget_lesson_row).apply {
                                setTextViewText(R.id.row_time, "")
                                setTextViewText(R.id.row_title, "还有 ${snap.todayRows.size - 5} 节…")
                                setTextViewText(R.id.row_status, "")
                            },
                        )
                    }
                }
                // 一键打卡按钮
                setViewVisibility(
                    R.id.list_action,
                    if (canMark) android.view.View.VISIBLE else android.view.View.GONE,
                )
                setOnClickPendingIntent(R.id.list_root, WidgetCommon.openApp(context))
                if (canMark) {
                    setOnClickPendingIntent(
                        R.id.list_action,
                        WidgetCommon.markDone(context, TodayListWidgetProvider::class.java, REQUEST_CODE),
                    )
                }
            }
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        private const val REQUEST_CODE = 21
    }
}
