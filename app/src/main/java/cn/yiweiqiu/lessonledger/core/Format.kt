package cn.yiweiqiu.lessonledger.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 金额工具：一律以 Long 存"分"，禁止 Double/Float 参与运算。
 */
object Money {

    private val withGroup = java.text.DecimalFormat("#,##0.00")
    private val plainGroup = java.text.DecimalFormat("#,##0")

    /** ¥1,234.50 / -¥12.00 */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        return "$sign¥" + withGroup.format(BigDecimal.valueOf(abs(cents)).movePointLeft(2))
    }

    /** 卡片上用：¥1,234（整数时省略小数） */
    fun compact(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = abs(cents)
        val yuan = BigDecimal.valueOf(abs).movePointLeft(2)
        return if (abs % 100 == 0L) {
            "$sign¥" + plainGroup.format(yuan)
        } else {
            "$sign¥" + withGroup.format(yuan)
        }
    }

    /** 不带符号不带千分位：1234.50 */
    fun plain(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = abs(cents)
        return sign + (abs / 100).toString() + "." + (abs % 100).toString().padStart(2, '0')
    }

    /** "1234.5" -> 123450（分）。非法输入返回 null。 */
    fun parseYuan(text: String): Long? {
        val t = text.trim().replace(",", "").replace("¥", "").replace("￥", "")
        if (t.isEmpty() || t == ".") return null
        return try {
            BigDecimal(t).setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)).toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }
}

/**
 * 课时工具：一律以 Long 存"半节"（1 节课 = 2），避免 0.5 节的浮点误差。
 */
object Lessons {

    const val HALF_PER_LESSON = 2L

    fun format(half: Long): String =
        if (half % 2 == 0L) (half / 2).toString() else (half / 2).toString() + ".5"

    /** "1.5" -> 3（半节）。非法输入返回 null。 */
    fun parse(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty() || t == ".") return null
        return try {
            BigDecimal(t).multiply(BigDecimal(2)).setScale(0, RoundingMode.HALF_UP).toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun lessonsToHalf(lessons: Int): Long = lessons * HALF_PER_LESSON
}

/**
 * 日期工具：
 * - 纯日历日期（缴费日、有效期、生日）用 "yyyy-MM-dd" 字符串存储，无时区歧义
 * - 时间点（上课时刻、打卡时刻）用 UTC epoch millis 存储，展示时转本地时区
 */
object Dates {

    val zone: ZoneId get() = ZoneId.systemDefault()

    private val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val md = DateTimeFormatter.ofPattern("MM-dd")

    fun today(): LocalDate = LocalDate.now(zone)
    fun now(): Long = System.currentTimeMillis()

    fun toDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun toDateTime(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun toMillis(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun formatDate(ms: Long): String = toDate(ms).format(iso)
    fun formatDate(date: LocalDate): String = date.format(iso)
    fun formatTime(ms: Long): String = toDateTime(ms).format(hm)
    fun formatDateTime(ms: Long): String = "${formatDate(ms)} ${formatTime(ms)}"
    fun formatMonthDay(ms: Long): String = toDate(ms).format(md)

    fun parseDate(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        return try {
            LocalDate.parse(text.trim(), iso)
        } catch (e: Exception) {
            null
        }
    }

    fun parseTimeOrNull(text: String?): LocalTime? {
        if (text.isNullOrBlank()) return null
        return try {
            LocalTime.parse(text.trim(), hm)
        } catch (e: Exception) {
            null
        }
    }

    /** 相对时间：刚刚 / 2 分钟前 / 3 小时前 / 2 天前 */
    fun relative(ms: Long, now: Long = now()): String {
        if (ms <= 0) return "从未"
        val diff = now - ms
        if (diff < 0) return "刚刚"
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 60 * 24 -> "${minutes / 60} 小时前"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} 天前"
            else -> formatDate(ms)
        }
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** 位掩码星期：bit0=周一 ... bit6=周日 */
    fun weekdayOf(date: LocalDate): Int = date.dayOfWeek.value - 1
    fun bit(date: LocalDate): Int = 1 shl weekdayOf(date)

    val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekdayLabel(mask: Int): String {
        if (mask == 0) return "未设置"
        val names = weekdayNames.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
        return if (names.isEmpty()) "未设置" else names.joinToString("、") { it.removePrefix("周") }
    }

    fun monthLabel(year: Int, month: Int): String = "${year}年${month}月"
}
