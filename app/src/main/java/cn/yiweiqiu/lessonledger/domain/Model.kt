package cn.yiweiqiu.lessonledger.domain

import cn.yiweiqiu.lessonledger.data.local.entity.ChildEntity
import cn.yiweiqiu.lessonledger.data.local.entity.CourseEntity
import cn.yiweiqiu.lessonledger.data.local.entity.LessonEntity
import cn.yiweiqiu.lessonledger.data.local.entity.PaymentEntity
import cn.yiweiqiu.lessonledger.core.Dates
import kotlinx.serialization.json.Json
import java.time.LocalDate

// ---------------------------------------------------------------- 枚举

enum class Category(val label: String) {
    MUSIC("音乐"),
    SPORTS("体育"),
    ACADEMIC("学科"),
    ART("美术"),
    DANCE("舞蹈"),
    CODING("编程"),
    OTHER("其他");

    companion object {
        fun of(value: String?): Category = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

enum class CourseStatus(val label: String) {
    ACTIVE("在读"),
    PAUSED("暂停"),
    FINISHED("已结束"),
    REFUNDED("已退费");

    companion object {
        fun of(value: String?): CourseStatus = entries.firstOrNull { it.name == value } ?: ACTIVE
    }
}

enum class PayMethod(val label: String) {
    WECHAT("微信"),
    ALIPAY("支付宝"),
    BANK("银行卡"),
    CASH("现金"),
    OTHER("其他");

    companion object {
        fun of(value: String?): PayMethod = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/**
 * 一笔缴费在「一次购买」里的角色。
 *
 * 机构搞活动时常见先交定金、开课后再补尾款，定金还能翻倍抵扣（交 500 抵 800）。
 * 这时一次购买由两条记录组成：[DEPOSIT] 锁定课时与总价，[BALANCE] 只补钱。
 * 两条记录用 [Payment.groupKey] 串在一起，算单价、算批次时按一个组处理，
 * 否则一笔 42 节课的课会被拆成「500 元 / 0 节」和「2400 元 / 42 节」两块，单价立刻失真。
 */
enum class PaymentKind(val label: String) {
    /** 一次付清（老数据默认） */
    FULL(""),
    DEPOSIT("定金"),
    BALANCE("尾款");

    companion object {
        fun of(value: String?): PaymentKind = entries.firstOrNull { it.name == value } ?: FULL
    }
}

enum class LessonStatus(val label: String) {
    PENDING("待上"),
    DONE("已上"),
    LEAVE("请假"),
    ABSENT("缺勤"),
    RESCHEDULED("调课"),
    CANCELLED("已取消");

    companion object {
        fun of(value: String?): LessonStatus = entries.firstOrNull { it.name == value } ?: PENDING
    }
}

// ---------------------------------------------------------------- 领域模型

data class Child(
    val id: String,
    val name: String,
    val nickname: String,
    val birthday: LocalDate?,
    val avatarColor: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
) {
    val displayName: String get() = nickname.ifBlank { name }
    val initial: String get() = name.trim().take(1).ifBlank { "?" }
    val age: Int?
        get() = birthday?.let { java.time.Period.between(it, Dates.today()).years }
}

data class Course(
    val id: String,
    val childId: String,
    val orgName: String,
    val courseName: String,
    val category: Category,
    val teacher: String?,
    val location: String?,
    val weekdayMask: Int,
    val startTime: String?,
    /** 每个星期几各自的上课时间（key：0=周一 ... 6=周日），没单独设就是空表 */
    val startTimesJson: String? = null,
    val durationMinutes: Int,
    /** 每个星期几各自的单次课时长（分钟），没单独设就是空表 */
    val durationMinutesJson: String? = null,
    val warningThreshold: Int,
    /** 上课前多少分钟发通知提醒（0 = 不提醒） */
    val remindBeforeMinutes: Int,
    val status: CourseStatus,
    /** 上一期课程 id，续费开新一期时串出的期次链；接续续费为 null */
    val previousCourseId: String? = null,
    val totalLessonsBoughtHalf: Long,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
) {
    val title: String get() = courseName.ifBlank { orgName }
    val subtitle: String get() = if (orgName.isBlank()) category.label else orgName

    /** 每个星期几各自的上课时间；解析不出来就当没设过，回落到统一时间 */
    val startTimes: Map<Int, String> get() = decodeStartTimes(startTimesJson)

    /**
     * 某个星期几几点上课（0=周一 ... 6=周日）。
     * 那天单独设过时间就用单独的，没设就回落到 [startTime] —— 老数据没有按天的时间，不该受影响。
     */
    fun startTimeAt(weekday: Int): String? = startTimes[weekday] ?: startTime

    /** 某一天几点上课 */
    fun startTimeOn(date: LocalDate): String? = startTimeAt(Dates.weekdayOf(date))

    /** 每个星期几各自的单次课时长（分钟） */
    val durations: Map<Int, Int> get() = decodeDurations(durationMinutesJson)

    /**
     * 某个星期几一节课多少分钟。
     * 平时 60 分钟、周末 90 分钟这种很常见，那天单独设过就用单独的，否则回落 [durationMinutes]。
     */
    fun durationAt(weekday: Int): Int = durations[weekday] ?: durationMinutes

    /** 某一天一节课多少分钟 */
    fun durationOn(date: LocalDate): Int = durationAt(Dates.weekdayOf(date))

    /**
     * 「周一 18:00 · 周三 16:30」这样的课表描述，列表页和详情页直接拿去显示。
     * 某天没填时间就只显示星期几，不至于让整段文案空掉。
     */
    val scheduleText: String
        get() {
            val days = Dates.weekdayNames.filterIndexed { i, _ -> weekdayMask and (1 shl i) != 0 }
            if (days.isEmpty()) return "未设置上课日"
            return days.joinToString(" · ") { name ->
                val idx = Dates.weekdayNames.indexOf(name)
                val t = startTimeAt(idx)
                if (t.isNullOrBlank()) name else "$name $t"
            }
        }
}

data class Payment(
    val id: String,
    val courseId: String,
    val payDate: LocalDate,
    val amountCents: Long,
    val originalAmountCents: Long?,
    val lessonsBoughtHalf: Long,
    val lessonsBonusHalf: Long,
    val payMethod: PayMethod,
    val validUntil: LocalDate?,
    val receipts: List<String>,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val kind: PaymentKind = PaymentKind.FULL,
    /** 同一次购买的组 key（= 定金那笔的 id）；一次付清为 null */
    val groupKey: String? = null,
    /** 定金抵扣金额（分），非定金为 0 */
    val creditCents: Long = 0,
) {
    /** 该批次总课时（半节） */
    val totalHalf: Long get() = lessonsBoughtHalf + lessonsBonusHalf
    val isRefund: Boolean get() = amountCents < 0
    val isDeposit: Boolean get() = kind == PaymentKind.DEPOSIT
    val isBalance: Boolean get() = kind == PaymentKind.BALANCE

    /**
     * 定金未付尾款时，尾款该付多少（分）。
     *
     * 定金记的是「整期总价 = 原价」，实付只记定金本身，所以尾款 = 原价 − 抵扣。
     * 没填原价（老数据或用户懒得填）时给 null，页面就不显示「待付尾款」，
     * 免得凭空算出一个用户没同意过的数字。
     */
    val balanceDueCents: Long?
        get() {
            if (!isDeposit) return null
            val total = originalAmountCents ?: return null
            return (total - creditCents).coerceAtLeast(0L)
        }
}

data class Lesson(
    val id: String,
    val courseId: String,
    val scheduledAt: Long,
    val actualAt: Long?,
    val durationMinutes: Int,
    val status: LessonStatus,
    val deductedHalf: Long,
    val paymentId: String?,
    val teacher: String?,
    val note: String,
    val autoGenerated: Boolean,
    /** 用户手动删过这节自动课次，自动排课不再补回来 */
    val suppressed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
)

data class LedgerData(
    val children: List<Child> = emptyList(),
    val courses: List<Course> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
) {
    fun childById(id: String?): Child? = children.firstOrNull { it.id == id }
    fun courseById(id: String?): Course? = courses.firstOrNull { it.id == id }
    fun paymentsOf(courseId: String): List<Payment> =
        payments.filter { it.courseId == courseId }.sortedWith(compareBy({ it.payDate }, { it.createdAt }))

    fun lessonsOf(courseId: String): List<Lesson> =
        lessons.filter { it.courseId == courseId }.sortedWith(compareBy({ it.scheduledAt }, { it.createdAt }))

    /**
     * 这门课里还没补尾款的定金。
     *
     * 判定很朴素：定金那一组的 key 底下还没有任何一条「尾款」记录。
     * 尾款被删掉时它会重新冒出来 —— 这是对的，钱确实还没付。
     */
    fun pendingDeposits(courseId: String): List<Payment> {
        val paidGroups = payments
            .filter { !it.deleted && it.courseId == courseId && it.isBalance }
            .mapNotNull { it.groupKey }
            .toSet()
        return paymentsOf(courseId).filter { p ->
            p.isDeposit && p.id !in paidGroups && (p.balanceDueCents ?: 0L) > 0L
        }
    }

    fun coursesOf(childId: String): List<Course> = courses.filter { it.childId == childId }

    fun childOf(course: Course): Child? = childById(course.childId)

    // ------------------------------------------------------------ 期次链

    /**
     * 沿 [Course.previousCourseId] 往上回溯，返回同一门课的全部期次，第 1 期在最前。
     *
     * 同步是各自设备写全量快照的，理论上不该出现环；但历史数据被手改过也不是没可能，
     * 所以这里带一个 seen 集合兜底，宁可少走两级也不要在这里死循环把 App 卡住。
     */
    fun courseChain(course: Course): List<Course> {
        val chain = ArrayDeque<Course>()
        val seen = mutableSetOf<String>()
        var cursor: Course? = course
        while (cursor != null && seen.add(cursor.id)) {
            chain.addFirst(cursor)
            cursor = cursor.previousCourseId?.let { id -> courseById(id)?.takeIf { !it.deleted } }
        }
        return chain.toList()
    }

    /** 这门课是同一门课的第几期，从 1 开始 */
    fun coursePeriod(course: Course): Int = courseChain(course).size

    /** 这门课后面还有没有更新的期次（有 = 它已经是历史期，列表里该折叠） */
    fun hasNewerPeriod(course: Course): Boolean =
        courses.any { !it.deleted && it.previousCourseId == course.id }
}

// ---------------------------------------------------------------- 表单草稿

/**
 * 一次课程录入要填的全部字段。
 *
 * 放在 domain 而不是 ui，是因为仓库层也要用它：缴费即录课那条路径里，
 * 课程信息是跟着缴费表单一起提交的，仓库不该反过来依赖 UI 的 data class。
 * （ui/forms 里保留了 CourseFormData 这个名字的 typealias，调用点不用改。）
 */
data class CourseDraft(
    val orgName: String,
    val courseName: String,
    val category: Category,
    val teacher: String?,
    val location: String?,
    val weekdayMask: Int,
    val startTime: String?,
    /** 每个星期几各自的上课时间（key：0=周一 ... 6=周日）。给个默认值，老调用点不用改 */
    val startTimesJson: String? = null,
    val durationMinutes: Int,
    /** 每个星期几各自的单次课时长（分钟）。同样给默认值 */
    val durationMinutesJson: String? = null,
    val warningThreshold: Int,
    val remindBeforeMinutes: Int,
    val status: CourseStatus,
    val note: String,
)

/** 一笔缴费要填的全部字段 */
data class PaymentDraft(
    val payDate: LocalDate,
    val amountCents: Long,
    val originalAmountCents: Long?,
    val lessonsBoughtHalf: Long,
    val lessonsBonusHalf: Long,
    val payMethod: PayMethod,
    val validUntil: LocalDate?,
    val receipts: List<String>,
    val note: String,
    val kind: PaymentKind = PaymentKind.FULL,
    val groupKey: String? = null,
    val creditCents: Long = 0,
)

// ---------------------------------------------------------------- 实体映射

private val receiptsJson = Json { ignoreUnknownKeys = true }

private fun decodeReceipts(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        receiptsJson.decodeFromString<List<String>>(raw)
    } catch (e: Exception) {
        emptyList()
    }
}

private val startTimesJsonParser = Json { ignoreUnknownKeys = true }

/** 按天的上课时间存的是 JSON，手改坏了也不该让 App 崩，解析失败就当没设过 */
private fun decodeStartTimes(raw: String?): Map<Int, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return try {
        startTimesJsonParser.decodeFromString<Map<String, String>>(raw)
            .mapNotNull { (key, value) -> key.toIntOrNull()?.let { it to value } }
            .toMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun decodeDurations(raw: String?): Map<Int, Int> {
    if (raw.isNullOrBlank()) return emptyMap()
    return try {
        // 复用上面那个 parser，统一按 Map<String, String> 解，再把时长转回 Int
        startTimesJsonParser.decodeFromString<Map<String, String>>(raw)
            .mapNotNull { (key, value) ->
                val k = key.toIntOrNull() ?: return@mapNotNull null
                val v = value.toIntOrNull() ?: return@mapNotNull null
                k to v
            }
            .toMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

/** 反方向：编辑表单里的时长 Map 存回 JSON；空表存 null */
fun encodeDurations(times: Map<Int, Int>): String? {
    val cleaned = times.filterValues { it > 0 }
    if (cleaned.isEmpty()) return null
    // 值也存成字符串（跟 startTimesJson 一个格式），解码那边统一按 Map<String, String> 读再转 Int，
    // 存数字的话 kotlinx 反序列化会直接抛错、整张表读成空，时长就悄悄回落了
    return cleaned.entries.sortedBy { it.key }
        .joinToString(",", prefix = "{", postfix = "}") { "\"${it.key}\":\"${it.value}\"" }
}

/** 反方向：编辑表单里的 Map 存回 JSON；空表存 null，免得库里堆一堆 "{}" */
fun encodeStartTimes(times: Map<Int, String>): String? {
    val cleaned = times.mapNotNull { (k, v) -> v.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    if (cleaned.isEmpty()) return null
    // 值是 "HH:mm"，不含引号之类的特殊字符，直接拼比走序列化库省心（也省掉一个 reified import）
    return cleaned.entries.sortedBy { it.key }
        .joinToString(",", prefix = "{", postfix = "}") { "\"${it.key}\":\"${it.value}\"" }
}

fun ChildEntity.toModel() = Child(
    id = id,
    name = name,
    nickname = nickname,
    birthday = Dates.parseDate(birthday),
    avatarColor = avatarColor,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun CourseEntity.toModel() = Course(
    id = id,
    childId = childId,
    orgName = orgName,
    courseName = courseName,
    category = Category.of(category),
    teacher = teacher,
    location = location,
    weekdayMask = weekdayMask,
    startTime = startTime,
    startTimesJson = startTimesJson,
    durationMinutes = durationMinutes,
    durationMinutesJson = durationMinutesJson,
    warningThreshold = warningThreshold,
    remindBeforeMinutes = remindBeforeMinutes,
    status = CourseStatus.of(status),
    previousCourseId = previousCourseId,
    totalLessonsBoughtHalf = totalLessonsBoughtHalf,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun PaymentEntity.toModel() = Payment(
    id = id,
    courseId = courseId,
    payDate = Dates.parseDate(payDate) ?: Dates.today(),
    amountCents = amount,
    originalAmountCents = originalAmount,
    lessonsBoughtHalf = lessonsBoughtHalf,
    lessonsBonusHalf = lessonsBonusHalf,
    payMethod = PayMethod.of(payMethod),
    validUntil = Dates.parseDate(validUntil),
    receipts = decodeReceipts(receiptPaths),
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
    kind = PaymentKind.of(kind),
    groupKey = groupKey,
    creditCents = creditCents,
)

fun LessonEntity.toModel() = Lesson(
    id = id,
    courseId = courseId,
    scheduledAt = scheduledAt,
    actualAt = actualAt,
    durationMinutes = durationMinutes,
    status = LessonStatus.of(status),
    deductedHalf = deductedHalf,
    paymentId = paymentId,
    teacher = teacher,
    note = note,
    autoGenerated = autoGenerated,
    suppressed = suppressed,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)
