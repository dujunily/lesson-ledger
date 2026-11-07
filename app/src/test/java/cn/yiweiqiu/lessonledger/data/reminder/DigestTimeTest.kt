package cn.yiweiqiu.lessonledger.data.reminder

import cn.yiweiqiu.lessonledger.core.Dates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 每天早上 10:00 汇总提醒的排程时间计算。
 *
 * 这类「每天固定时刻」的闹钟最容易错在跨天边界：09:59:30 打开 App 时，
 * 如果直接排今天 10:00，会在半分钟内连响两条（一条现在补发、一条十点整）。
 */
class DigestTimeTest {

    private val day = LocalDate.of(2026, 10, 31)

    private fun at(hour: Int, minute: Int, second: Int = 0): Long =
        Dates.toMillis(day, LocalTime.of(hour, minute, second))

    @Test
    fun `早上九点排今天十点`() {
        val now = at(9, 0)
        assertEquals(at(10, 0), nextDigestAt(now))
    }

    @Test
    fun `九点五十九分半改排明天 避免连响两条`() {
        val now = at(9, 59, 30)
        assertEquals(Dates.toMillis(day.plusDays(1), LocalTime.of(10, 0)), nextDigestAt(now))
    }

    @Test
    fun `正好十点排明天`() {
        val now = at(10, 0)
        assertEquals(Dates.toMillis(day.plusDays(1), LocalTime.of(10, 0)), nextDigestAt(now))
    }

    @Test
    fun `晚上十一点排明天上午十点`() {
        val now = at(23, 0)
        assertEquals(Dates.toMillis(day.plusDays(1), LocalTime.of(10, 0)), nextDigestAt(now))
    }

    @Test
    fun `凌晨排当天十点`() {
        val now = at(0, 30)
        assertEquals(at(10, 0), nextDigestAt(now))
    }
}
