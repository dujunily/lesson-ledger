package cn.yiweiqiu.lessonledger.data.reminder

import android.content.Context

/**
 * 提醒排程的轻量本地账本（普通 SharedPreferences，不含敏感信息）。
 *
 * - scheduledIds：当前排了闹钟的课次，重新排程时用来取消多余的闹钟
 * - notifiedIds：已经提醒过的课次，避免"课还没上但 App 又被打开"时反复补提醒
 *
 * key 用 "lessonId@scheduledAt"：课次改了上课时间就当成新的一节，提醒状态自动重置。
 */
internal class ReminderTrack(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lessonledger_reminder_track", Context.MODE_PRIVATE)

    fun scheduledIds(): Set<String> = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()

    fun setScheduledIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_SCHEDULED, ids.toSet()).apply()
    }

    fun isNotified(lessonId: String, scheduledAt: Long): Boolean =
        prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().contains(key(lessonId, scheduledAt))

    fun markNotified(lessonId: String, scheduledAt: Long) {
        val current = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().toMutableSet()
        current.add(key(lessonId, scheduledAt))
        // 只保留最近 200 条，别让这个 set 无限膨胀
        val trimmed = if (current.size > 200) current.toList().takeLast(200).toSet() else current
        prefs.edit().putStringSet(KEY_NOTIFIED, trimmed).apply()
    }

    private fun key(lessonId: String, scheduledAt: Long) = "$lessonId@$scheduledAt"

    companion object {
        const val KEY_SCHEDULED = "scheduled_lesson_ids"
        const val KEY_NOTIFIED = "notified_lesson_ids"

        /** 从 "lessonId@scheduledAt" 里取出 lessonId */
        fun lessonIdOf(entry: String): String = entry.substringBefore('@')
    }
}
