package cn.yiweiqiu.lessonledger.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import cn.yiweiqiu.lessonledger.R
import cn.yiweiqiu.lessonledger.core.Dates
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface

/**
 * 桌面小组件「今日打卡」：
 *
 * - 显示今天有没有课、几节待打卡；
 * - 有待打卡时中间是一个「✓ 今天上课了」大按钮，点一下把今天的待打卡课次
 *   全部标为「已上」（按默认扣课时），不用打开 App；
 * - 点组件其他位置 → 直接落到 App 的打卡页。
 *
 * 数据与一键打卡逻辑在 [WidgetCommon]（四个小组件共享）。
 */
class CheckinWidgetProvider : AppWidgetProvider() {

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
            else -> super.onReceive(context, intent) // APPWIDGET_UPDATE → onUpdate
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
        val ids = manager.getAppWidgetIds(
            ComponentName(context, CheckinWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return

        val snap = WidgetCommon.snapshot(context)
        val today = snap.today

        val datePrefix = "${today.monthValue}月${today.dayOfMonth}日 " +
            Dates.weekdayNames[Dates.weekdayOf(today)] + "  "
        val statusText = when {
            snap.waiting > 0 -> "${snap.waiting} 节课待打卡"
            snap.total > 0 -> "${snap.total}节课都上完了"
            else -> "没排课"
        }
        val canMark = snap.waiting > 0

        // 日期小字 + 状态大字混排成一行，整个组件保证塞进 3x1 格子的高度
        val statusSpan = SpannableString(datePrefix + statusText).apply {
            setSpan(
                AbsoluteSizeSpan(11, true),
                0,
                datePrefix.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(context.getColor(R.color.widget_text_secondary)),
                0,
                datePrefix.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                AbsoluteSizeSpan(15, true),
                datePrefix.length,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                datePrefix.length,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

        // drawable/颜色资源由系统按日夜模式自动解析，无需手动判断深色
        val btnBg = if (canMark) R.drawable.widget_btn_primary else R.drawable.widget_btn_muted
        val btnText = when {
            canMark -> "✓ 今天上课了"
            snap.total > 0 -> "已全部打卡"
            else -> "打开课时簿"
        }

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_checkin).apply {
                setTextViewText(R.id.widget_status, statusSpan)
                setTextViewText(R.id.widget_action, btnText)
                setInt(R.id.widget_action, "setBackgroundResource", btnBg)
                setTextColor(
                    R.id.widget_action,
                    context.getColor(
                        if (canMark) R.color.widget_btn_text else R.color.widget_btn_muted_text,
                    ),
                )
                // 按钮以外任何位置点 → 打卡页
                setOnClickPendingIntent(R.id.widget_root, WidgetCommon.openApp(context))
                setOnClickPendingIntent(
                    R.id.widget_action,
                    if (canMark) {
                        WidgetCommon.markDone(context, CheckinWidgetProvider::class.java, REQUEST_CODE)
                    } else {
                        WidgetCommon.openApp(context)
                    },
                )
            }
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        private const val REQUEST_CODE = 11

        /** App 侧数据变了（回前台/打卡页操作后）叫醒全部小组件刷新 */
        fun refresh(context: Context) {
            WidgetCommon.refreshAll(context)
        }
    }
}
