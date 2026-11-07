package cn.yiweiqiu.lessonledger.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import cn.yiweiqiu.lessonledger.R

/**
 * 桌面小组件「快捷打卡」（整块就是一个大按钮）：
 *
 * - 今天有待打卡：整块是「✓ 今天上课了」实心按钮，点一下全部标已上；
 * - 都打完了：「已全部打卡」弱化样式；没排课：「今天没课」。
 * - 适合放在常驻首屏，回家路上顺手一点。
 */
class QuickMarkWidgetProvider : AppWidgetProvider() {

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
        val ids = manager.getAppWidgetIds(ComponentName(context, QuickMarkWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val snap = WidgetCommon.snapshot(context)
        val canMark = snap.waiting > 0
        val text = when {
            canMark -> "✓ ${snap.waiting} 节待打卡"
            snap.total > 0 -> "已全部打卡"
            else -> "今天没课"
        }

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick).apply {
                setTextViewText(R.id.quick_action, text)
                setInt(
                    R.id.quick_action,
                    "setBackgroundResource",
                    if (canMark) R.drawable.widget_btn_primary else R.drawable.widget_btn_muted,
                )
                setTextColor(
                    R.id.quick_action,
                    context.getColor(
                        if (canMark) R.color.widget_btn_text else R.color.widget_btn_muted_text,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.quick_root,
                    if (canMark) {
                        WidgetCommon.markDone(context, QuickMarkWidgetProvider::class.java, REQUEST_CODE)
                    } else {
                        WidgetCommon.openApp(context)
                    },
                )
            }
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        private const val REQUEST_CODE = 31
    }
}
