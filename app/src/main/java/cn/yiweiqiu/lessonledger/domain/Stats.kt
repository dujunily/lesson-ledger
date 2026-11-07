package cn.yiweiqiu.lessonledger.domain

import cn.yiweiqiu.lessonledger.core.ConflictMark
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.core.Money
import java.time.LocalDate
import java.time.YearMonth

enum class StatsPeriod(val label: String) {
    ALL("全部"),
    MONTH("本月"),
    HALF_YEAR("近 6 月"),
    YEAR("本年"),
}

data class MonthPoint(val year: Int, val month: Int, val spentCents: Long, val refundCents: Long) {
    val label: String get() = "${month}月"
    val netCents: Long get() = spentCents + refundCents
}

data class CategorySlice(val category: Category, val cents: Long)

data class OrgSlice(val orgName: String, val cents: Long)

data class ChildSlice(val child: Child, val cents: Long)

data class StatsResult(
    val period: StatsPeriod,
    val from: LocalDate,
    val to: LocalDate,
    /** 支出（正数金额合计） */
    val spendCents: Long,
    /** 退费（负数金额合计，负数） */
    val refundCents: Long,
    val netCents: Long,
    val paymentCount: Int,
    val monthly: List<MonthPoint>,
    val byCategory: List<CategorySlice>,
    val byOrg: List<OrgSlice>,
    val byChild: List<ChildSlice>,
) {
    val hasData: Boolean get() = paymentCount > 0
}

object StatsCalc {

    fun rangeOf(period: StatsPeriod, data: LedgerData, today: LocalDate = Dates.today()): Pair<LocalDate, LocalDate> {
        val monthStart = today.withDayOfMonth(1)
        return when (period) {
            StatsPeriod.MONTH -> monthStart to monthStart.plusMonths(1).minusDays(1)
            StatsPeriod.HALF_YEAR -> monthStart.minusMonths(5) to today
            StatsPeriod.YEAR -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
            StatsPeriod.ALL -> {
                val dates = data.payments.filter { !it.deleted }.map { it.payDate }
                if (dates.isEmpty()) monthStart.minusMonths(11) to today
                else dates.min() to maxOf(dates.max(), today)
            }
        }
    }

    fun compute(
        data: LedgerData,
        period: StatsPeriod,
        childId: String? = null,
        today: LocalDate = Dates.today(),
    ): StatsResult {
        val (from, to) = rangeOf(period, data, today)
        val courseById = data.courses.associateBy { it.id }
        val scoped = data.payments
            .filter { !it.deleted }
            .filter { childId == null || courseById[it.courseId]?.childId == childId }

        val inRange = scoped.filter { !it.payDate.isBefore(from) && !it.payDate.isAfter(to) }
        val spend = inRange.filter { it.amountCents > 0 }.sumOf { it.amountCents }
        val refund = inRange.filter { it.amountCents < 0 }.sumOf { it.amountCents }

        val byCategory = inRange.groupBy { courseById[it.courseId]?.category ?: Category.OTHER }
            .map { (cat, list) -> CategorySlice(cat, list.sumOf { it.amountCents }) }
            .filter { it.cents != 0L }
            .sortedByDescending { kotlin.math.abs(it.cents) }

        val byOrg = inRange.groupBy { courseById[it.courseId]?.orgName?.ifBlank { "未填写机构" } ?: "未填写机构" }
            .map { (org, list) -> OrgSlice(org, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.cents }
            .take(5)

        val byChild = inRange.groupBy { courseById[it.courseId]?.childId }
            .mapNotNull { (cid, list) ->
                val child = data.childById(cid) ?: return@mapNotNull null
                ChildSlice(child, list.sumOf { it.amountCents })
            }
            .sortedByDescending { it.cents }

        val months = buildList {
            val start = today.withDayOfMonth(1).minusMonths(5)
            for (i in 0 until 6) {
                val ym = YearMonth.from(start.plusMonths(i.toLong()))
                val list = scoped.filter {
                    YearMonth.from(it.payDate) == ym
                }
                add(
                    MonthPoint(
                        year = ym.year,
                        month = ym.monthValue,
                        spentCents = list.filter { it.amountCents > 0 }.sumOf { it.amountCents },
                        refundCents = list.filter { it.amountCents < 0 }.sumOf { it.amountCents },
                    )
                )
            }
        }

        return StatsResult(
            period = period,
            from = from,
            to = to,
            spendCents = spend,
            refundCents = refund,
            netCents = spend + refund,
            paymentCount = inRange.size,
            monthly = months,
            byCategory = byCategory,
            byOrg = byOrg,
            byChild = byChild,
        )
    }

    /** 首页汇总：本月支出 / 在读课程 / 待消耗余额 */
    data class HomeSummary(
        val monthSpendCents: Long,
        val activeCourseCount: Int,
        val pendingValueCents: Long,
        val totalPaidCents: Long,
    )

    fun homeSummary(
        data: LedgerData,
        stats: Map<String, LedgerCalc.CourseStats>,
        today: LocalDate = Dates.today(),
    ): HomeSummary {
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        val monthSpend = data.payments
            .filter { !it.deleted && it.amountCents > 0 }
            .filter { !it.payDate.isBefore(monthStart) && !it.payDate.isAfter(monthEnd) }
            .sumOf { it.amountCents }
        // 冲突副本在用户处理前不算在读课程，否则「在读 N 门」会虚高
        val active = data.courses.count { it.status == CourseStatus.ACTIVE && !ConflictMark.isCopy(it.note) }
        val pending = data.courses.sumOf { stats[it.id]?.pendingValueCents ?: 0L }
        val totalPaid = data.payments.filter { !it.deleted && it.amountCents > 0 }.sumOf { it.amountCents }
        return HomeSummary(monthSpend, active, pending, totalPaid)
    }

    // ------------------------------------------------------------ CSV

    fun buildCsv(data: LedgerData, period: StatsPeriod, childId: String? = null, today: LocalDate = Dates.today()): String {
        val (from, to) = rangeOf(period, data, today)
        val courseById = data.courses.associateBy { it.id }
        val rows = data.payments
            .filter { !it.deleted }
            .filter { !it.payDate.isBefore(from) && !it.payDate.isAfter(to) }
            .filter { pid -> childId == null || courseById[pid.courseId]?.childId == childId }
            .sortedBy { it.payDate }

        val sb = StringBuilder()
        sb.append("日期,孩子,机构,课程,金额,课时,备注\n")
        for (p in rows) {
            val course = courseById[p.courseId]
            val childName = data.childById(course?.childId)?.displayName ?: ""
            val lessonsText = Lessons.format(p.lessonsBoughtHalf + p.lessonsBonusHalf)
            val note = buildString {
                if (p.lessonsBonusHalf > 0) append("赠送${Lessons.format(p.lessonsBonusHalf)}节 ")
                if (p.note.isNotBlank()) append(p.note)
            }.trim()
            sb.append(
                listOf(
                    Dates.formatDate(p.payDate),
                    childName,
                    course?.orgName ?: "",
                    course?.courseName ?: "",
                    Money.plain(p.amountCents),
                    lessonsText,
                    note,
                ).joinToString(",") { csvCell(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    private fun csvCell(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }
}
