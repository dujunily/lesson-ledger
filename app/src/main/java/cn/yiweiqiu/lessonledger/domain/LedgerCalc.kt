package cn.yiweiqiu.lessonledger.domain

import cn.yiweiqiu.lessonledger.core.ConflictMark
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.LedgerMark
import cn.yiweiqiu.lessonledger.core.Lessons
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 核心业务计算：剩余课时、FIFO 批次分配、有效单价、待消耗金额。
 *
 * 单位约定：
 * - 金额：分（Long）
 * - 课时：半节（Long，1 节课 = 2）
 */
object LedgerCalc {

    /** 把金额除法做四舍五入，全程不出现浮点误差 */
    private fun divHalfUp(numerator: Long, denominator: Long, scale: Int = 0): Long {
        if (denominator == 0L) return 0L
        return BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP)
            .toLong()
    }

    /**
     * 把课时数按「实付总额 ÷ 购买总课时」的比例折算成金额（分）。
     *
     * 为什么不用「四舍五入后的单价 × 课时数」：
     * 有效单价只能精确到分，42 节课的单价是 ¥71.43，拿它乘 42 就是 ¥3000.06 ——
     * 比实付的 ¥3000 还多，用户一眼就觉得账算错了。
     *
     * 这里始终用原始的分子（实付总额）/ 分母（购买总课时）做**一次**除法，
     * 结果天然不会超过实付总额，也不会随课时数放大误差。
     */
    fun valueOfHalf(halfLessons: Long, numerCents: Long, denomHalf: Long): Long {
        if (denomHalf <= 0L || halfLessons == 0L) return 0L
        return divHalfUp(halfLessons * numerCents, denomHalf)
    }

    // ------------------------------------------------------------ 批次分配

    data class Segment(val lessonId: String, val paymentId: String, val half: Long)

    data class Allocation(
        val segmentsByLesson: Map<String, List<Segment>>,
        /** 每个批次已被扣掉的课时（半节） */
        val usedByPayment: Map<String, Long>,
        /** 课时总量不够扣、无法归属到任何批次的部分（半节） */
        val unattributedHalf: Long,
    ) {
        fun primaryPaymentId(lessonId: String): String? =
            segmentsByLesson[lessonId]?.firstOrNull()?.paymentId
    }

    /**
     * FIFO：按 payDate 升序（同日按 createdAt 升序）遍历缴费批次，先扣最早的批次。
     * 退费批次（负数课时）不参与分配，它只从总课时池中扣减。
     */
    fun allocateFifo(payments: List<Payment>, lessons: List<Lesson>): Allocation {
        val batches = payments
            .filter { !it.deleted && it.totalHalf > 0 }
            .sortedWith(compareBy({ it.payDate }, { it.createdAt }))
        val remainingCapacity = batches.associate { it.id to it.totalHalf }.toMutableMap()
        val segments = LinkedHashMap<String, MutableList<Segment>>()
        val used = HashMap<String, Long>()
        var unattributed = 0L

        val consuming = lessons
            .filter { !it.deleted && it.deductedHalf > 0 }
            .sortedWith(compareBy({ it.scheduledAt }, { it.createdAt }))

        for (lesson in consuming) {
            var need = lesson.deductedHalf
            for (batch in batches) {
                if (need <= 0L) break
                val available = remainingCapacity[batch.id] ?: 0L
                if (available <= 0L) continue
                val take = min(available, need)
                remainingCapacity[batch.id] = available - take
                used[batch.id] = (used[batch.id] ?: 0L) + take
                segments.getOrPut(lesson.id) { mutableListOf() }
                    .add(Segment(lesson.id, batch.id, take))
                need -= take
            }
            if (need > 0L) unattributed += need
        }
        return Allocation(segments, used, unattributed)
    }

    // ------------------------------------------------------------ 课程统计

    data class BatchBar(
        val paymentId: String,
        val payDate: LocalDate,
        val totalHalf: Long,
        val usedHalf: Long,
        val amountCents: Long,
    ) {
        val remainingHalf: Long get() = max(0L, totalHalf - usedHalf)
    }

    data class CourseStats(
        val courseId: String,
        /** Σ(购买 + 赠送)，含退费负数（半节） */
        val purchasedHalf: Long,
        val usedHalf: Long,
        val remainingHalf: Long,
        /** Σ实付金额（分），含退费负数 */
        val paidCents: Long,
        /**
         * 真实交出去的钱（分）：只加正数，不减退费。
         *
         * 用户关心的是「我一共交了多少」，退回来的是另一件事——
         * 拿净额当累计缴费，退了 600 就会显得当初只交了 2160，账对不上记忆。
         */
        val grossPaidCents: Long,
        /** 累计退费（分）：正数 */
        val refundedCents: Long,
        /** 有效单价（分/节） */
        val unitPriceCents: Long,
        /** 待消耗金额（分） */
        val pendingValueCents: Long,
        /** 折算用的分子：实付总额（分），退费后为净额 */
        val valueNumerCents: Long,
        /** 折算用的分母：购买总课时（半节） */
        val valueDenomHalf: Long,
        /** 已上课次数（一次课 = 一条记录，90 分钟那节也只算一次） */
        val doneCount: Int,
        /** 已上课节数（半节）：只累加「已上」课次扣掉的课时，不含缺勤/请假扣的 */
        val doneHalf: Long,
        /** 其中「一次性结清」扣掉的课时（半节）：计入节数，但不计入次数 */
        val settledHalf: Long = 0L,
        /** 超扣课时（半节） */
        val overdrawnHalf: Long,
        /** 最近一个未来到期日 */
        val expiringAt: LocalDate?,
        val nextLessonAt: Long?,
        val batches: List<BatchBar>,
        val allocation: Allocation,
    ) {
        val ratio: Float
            get() = if (purchasedHalf <= 0L) 0f
            else (usedHalf.toFloat() / purchasedHalf.toFloat()).coerceIn(0f, 1f)

        val isOverdrawn: Boolean get() = overdrawnHalf > 0L || remainingHalf < 0L
    }

    fun computeStats(
        course: Course,
        payments: List<Payment>,
        lessons: List<Lesson>,
        today: LocalDate = Dates.today(),
        now: Long = Dates.now(),
    ): CourseStats {
        // 冲突副本是同步时输掉的那条记录的另一份拷贝（待用户处理），它和正主记录
        // 指向同一门课：不排除的话，累计缴费 / 购买课时会被凭空翻倍
        val livePayments = payments.filter { !it.deleted && it.courseId == course.id && !ConflictMark.isCopy(it.note) }
        val liveLessons = lessons.filter { !it.deleted && it.courseId == course.id }

        val purchasedHalf = livePayments.sumOf { it.totalHalf }
        val usedHalf = liveLessons.filter { it.deductedHalf > 0 }.sumOf { it.deductedHalf }
        val remainingHalf = purchasedHalf - usedHalf
        val paidCents = livePayments.sumOf { it.amountCents }
        val grossPaidCents = livePayments.filter { it.amountCents > 0 }.sumOf { it.amountCents }
        val refundedCents = livePayments.filter { it.amountCents < 0 }.sumOf { -it.amountCents }

        // 有效单价：按「一次购买」分组算。
        // 定金 + 尾款是同一笔钱的两个部分（见 PaymentKind），拆开算会把 42 节课的单价
        // 算成「尾款 ÷ 42」，凭空便宜一大截；0 元纯赠送批次仍然排除，避免把单价拉低或除零。
        val groups = livePayments.groupBy { it.groupKey ?: it.id }
        val priceable = groups.values.filter { group ->
            group.sumOf { it.totalHalf } != 0L && group.sumOf { it.amountCents } != 0L
        }
        val denom = priceable.sumOf { group -> group.sumOf { it.totalHalf } }
        val numer = priceable.sumOf { group -> group.sumOf { it.amountCents } }
        val unitPriceCents = if (denom > 0L) divHalfUp(numer * 2, denom) else 0L

        // 待消耗金额：按原始比例一次算出，不用「四舍五入单价 × 课时」，
        // 否则 42 节课会算出 ¥3000.06，比实付的 ¥3000 还多。
        // 分母用全部购买课时（含赠送），分子用实付净额，剩余多少就折算多少。
        val pendingValueCents = valueOfHalf(remainingHalf, paidCents, purchasedHalf)

        val allocation = allocateFifo(livePayments, liveLessons)
        // 次数和节数要分开算：90 分钟那节课算「一次」但扣 1.5 节，
        // 之前界面拿次数当节数显示，一门课全是 90 分钟就会显得少扣了。
        // 「一次性结清」那条只扣课时、不是一次真实上课，所以次数里排除掉，
        // 单独记在 settledHalf 里，界面才能说清「36 节里有一半是补录结清的」。
        val doneLessons = liveLessons.filter { it.status == LessonStatus.DONE }
        val settledHalf = doneLessons.filter { LedgerMark.isSettle(it.note) }.sumOf { it.deductedHalf }
        val doneCount = doneLessons.count { !LedgerMark.isSettle(it.note) }
        val doneHalf = doneLessons.sumOf { it.deductedHalf }

        val expiring = livePayments.mapNotNull { it.validUntil }
            .filter { !it.isBefore(today) }
            .minOrNull()

        val nextLessonAt = liveLessons
            .filter { it.status == LessonStatus.PENDING && it.scheduledAt >= now }
            .minOfOrNull { it.scheduledAt }

        // 批次列表同样按组呈现：定金那笔没有课时、尾款那笔没有课时额度，
        // 只有合在一起才是「这一期买了多少节、花了多少钱」。
        val bars = groups.values
            .filter { group -> group.sumOf { it.totalHalf } > 0L }
            .map { group ->
                // 代表 id 取真正带课时的那条（尾款记录课时为 0，不能拿它当批次）
                val head = group
                    .sortedWith(compareBy({ it.createdAt }, { it.id }))
                    .firstOrNull { it.totalHalf > 0L }
                    ?: group.first()
                BatchBar(
                    paymentId = head.id,
                    payDate = group.minOf { it.payDate },
                    totalHalf = group.sumOf { it.totalHalf },
                    usedHalf = group.sumOf { allocation.usedByPayment[it.id] ?: 0L },
                    amountCents = group.sumOf { it.amountCents },
                )
            }
            .sortedWith(compareBy({ it.payDate }, { it.paymentId }))

        return CourseStats(
            courseId = course.id,
            purchasedHalf = purchasedHalf,
            usedHalf = usedHalf,
            remainingHalf = remainingHalf,
            paidCents = paidCents,
            grossPaidCents = grossPaidCents,
            refundedCents = refundedCents,
            unitPriceCents = unitPriceCents,
            pendingValueCents = pendingValueCents,
            valueNumerCents = paidCents,
            valueDenomHalf = purchasedHalf,
            doneCount = doneCount,
            doneHalf = doneHalf,
            settledHalf = settledHalf,
            overdrawnHalf = max(0L, allocation.unattributedHalf),
            expiringAt = expiring,
            nextLessonAt = nextLessonAt,
            batches = bars,
            allocation = allocation,
        )
    }

    fun statsForAll(data: LedgerData, today: LocalDate = Dates.today(), now: Long = Dates.now()): Map<String, CourseStats> =
        data.courses.associate { course ->
            course.id to computeStats(course, data.payments, data.lessons, today, now)
        }

    // ------------------------------------------------------------ 退费

    data class RefundResult(
        val refundCents: Long,
        val unitPriceCents: Long,
        val lessonsHalf: Long,
        val feeCents: Long,
    )

    fun computeRefund(stats: CourseStats, lessonsHalf: Long, feeCents: Long): RefundResult {
        // 同样按原始比例折算，避免退费金额比实付比例多出几分钱
        val gross = valueOfHalf(lessonsHalf, stats.valueNumerCents, stats.valueDenomHalf)
        return RefundResult(
            refundCents = gross - feeCents,
            unitPriceCents = stats.unitPriceCents,
            lessonsHalf = lessonsHalf,
            feeCents = feeCents,
        )
    }

    // ------------------------------------------------------------ 打卡扣课

    /** 根据状态与设置决定这一节应扣多少课时（半节），1 节课 = 2 */
    fun deductedFor(
        status: LessonStatus,
        absentDeducts: Boolean,
        leaveDeducts: Boolean,
    ): Long = when (status) {
        LessonStatus.DONE -> 2L
        LessonStatus.ABSENT -> if (absentDeducts) 2L else 0L
        LessonStatus.LEAVE -> if (leaveDeducts) 2L else 0L
        else -> 0L
    }

    /**
     * 按实际上课时长折算扣课时：1 节 = 课程的单节时长（分钟）。
     *
     * 英语课常出现「平时 1 小时、周末 90 分钟」这类排课，一律扣 1 节账就对不上了，
     * 所以打卡时按当天实际时长除以课程的单节时长折算：90 分钟 / 基准 60 → 1.5 节，
     * 120 分钟 → 2 节。向上取到 0.5 节，45 分钟这种短课也至少算 1 节（不会扣成 0.75）。
     *
     * 返回「半天数」（1 节 = 2），和 [Lesson.deductedHalf] 口径一致。
     */
    fun deductedFromDuration(durationMinutes: Int, standardMinutes: Int): Long {
        val base = standardMinutes.takeIf { it > 0 } ?: 60
        val halves = durationMinutes.toDouble() / base.toDouble() * 2.0
        return ceil(halves).toLong().coerceAtLeast(1L)
    }

    // ------------------------------------------------------------ 提醒

    enum class ReminderKind(val priority: Int) {
        OVERDRAWN(1),
        LOW_LESSONS(2),
        EXPIRING(3),
        TODAY_LESSON(4),
        WEEK_UNCLOCKED(5),
    }

    data class Reminder(
        val key: String,
        val kind: ReminderKind,
        val title: String,
        val detail: String,
        val courseId: String?,
        val childId: String?,
        val severity: Int,
    )

    fun buildReminders(
        data: LedgerData,
        stats: Map<String, CourseStats>,
        today: LocalDate = Dates.today(),
        now: Long = Dates.now(),
        maxCount: Int = 5,
    ): List<Reminder> {
        val out = mutableListOf<Reminder>()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)

        for (course in data.courses.filter { it.status == CourseStatus.ACTIVE }) {
            val s = stats[course.id] ?: continue
            val child = data.childOf(course)
            val name = "${child?.displayName ?: ""}·${course.courseName}".trim('·')

            if (s.isOverdrawn) {
                out += Reminder(
                    key = "over-${course.id}",
                    kind = ReminderKind.OVERDRAWN,
                    title = "${course.courseName} 已超出 ${Lessons.format(max(s.overdrawnHalf, -s.remainingHalf))} 节",
                    detail = "请补缴费用或核对课时记录",
                    courseId = course.id,
                    childId = course.childId,
                    severity = 3,
                )
            }
            // 只有买过课时的课程才提示“该续费了”：从未缴费的课程（含冲突副本）不参与。
            // 剩 0 节的也不提醒——课上完了可能就是不续了，再挂「只剩 0 节」纯属催命
            if (s.purchasedHalf > 0L && !s.isOverdrawn &&
                s.remainingHalf > 0L &&
                s.remainingHalf <= Lessons.lessonsToHalf(course.warningThreshold)
            ) {
                out += Reminder(
                    key = "low-${course.id}",
                    kind = ReminderKind.LOW_LESSONS,
                    title = "${course.courseName} 只剩 ${Lessons.format(max(0L, s.remainingHalf))} 节",
                    detail = "该续费了 · $name",
                    courseId = course.id,
                    childId = course.childId,
                    severity = if (s.remainingHalf <= 0L) 3 else 2,
                )
            }
            val expiring = s.expiringAt
            if (expiring != null && Dates.daysBetween(today, expiring) in 0..30 && s.remainingHalf > 0) {
                out += Reminder(
                    key = "exp-${course.id}",
                    kind = ReminderKind.EXPIRING,
                    title = "${course.courseName} ${Dates.formatDate(expiring)} 到期",
                    detail = "还剩 ${Lessons.format(s.remainingHalf)} 节未消耗",
                    courseId = course.id,
                    childId = course.childId,
                    severity = 2,
                )
            }
        }

        // 今日课程
        data.lessons
            .filter { !it.deleted && it.status == LessonStatus.PENDING }
            .filter { Dates.toDate(it.scheduledAt) == today }
            .sortedBy { it.scheduledAt }
            .forEach { lesson ->
                val course = data.courseById(lesson.courseId) ?: return@forEach
                val child = data.childOf(course)
                out += Reminder(
                    key = "today-${lesson.id}",
                    kind = ReminderKind.TODAY_LESSON,
                    title = "今天 ${Dates.formatTime(lesson.scheduledAt)} ${course.courseName}",
                    detail = "${child?.displayName ?: ""} · 别忘了打卡",
                    courseId = course.id,
                    childId = course.childId,
                    severity = 1,
                )
            }

        // 本周已过但未打卡
        data.lessons
            .filter { !it.deleted && it.status == LessonStatus.PENDING }
            .filter {
                val d = Dates.toDate(it.scheduledAt)
                d.isBefore(today) && !d.isBefore(weekStart) && !d.isAfter(weekEnd)
            }
            .sortedBy { it.scheduledAt }
            .forEach { lesson ->
                val course = data.courseById(lesson.courseId) ?: return@forEach
                val child = data.childOf(course)
                val d = Dates.toDate(lesson.scheduledAt)
                out += Reminder(
                    key = "week-${lesson.id}",
                    kind = ReminderKind.WEEK_UNCLOCKED,
                    title = "${Dates.weekdayNames[Dates.weekdayOf(d)]}的${course.courseName}还没打卡",
                    detail = "${child?.displayName ?: ""} · ${Dates.formatDate(d)}",
                    courseId = course.id,
                    childId = course.childId,
                    severity = 1,
                )
            }

        return out
            .sortedWith(compareBy({ it.kind.priority }, { -it.severity }, { it.title }))
            .take(maxCount)
    }
}
