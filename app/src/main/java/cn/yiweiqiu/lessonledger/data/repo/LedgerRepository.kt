package cn.yiweiqiu.lessonledger.data.repo

import cn.yiweiqiu.lessonledger.core.ConflictMark
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.LedgerMark
import cn.yiweiqiu.lessonledger.core.Receipts
import cn.yiweiqiu.lessonledger.data.local.AppDatabase
import cn.yiweiqiu.lessonledger.data.local.entity.ChildEntity
import cn.yiweiqiu.lessonledger.data.local.entity.CourseEntity
import cn.yiweiqiu.lessonledger.data.local.entity.LessonEntity
import cn.yiweiqiu.lessonledger.data.local.entity.PaymentEntity
import cn.yiweiqiu.lessonledger.data.local.entity.SyncLogEntity
import cn.yiweiqiu.lessonledger.data.reminder.ReminderScheduler
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.data.sync.LedgerSnapshotDto
import cn.yiweiqiu.lessonledger.data.sync.SCHEMA_VERSION
import cn.yiweiqiu.lessonledger.data.sync.SyncScheduler
import cn.yiweiqiu.lessonledger.domain.Category
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseDraft
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.DataSource
import cn.yiweiqiu.lessonledger.domain.DemoIds
import cn.yiweiqiu.lessonledger.domain.DemoStats
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.LedgerData
import cn.yiweiqiu.lessonledger.domain.Lesson
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.domain.PayMethod
import cn.yiweiqiu.lessonledger.domain.Payment
import cn.yiweiqiu.lessonledger.domain.PaymentDraft
import cn.yiweiqiu.lessonledger.domain.PaymentKind
import cn.yiweiqiu.lessonledger.domain.forSource
import cn.yiweiqiu.lessonledger.domain.statsOf
import cn.yiweiqiu.lessonledger.domain.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导入结果。
 *
 * [skipped] 是「属于另一套、被挡在门外」的条数。备份文件可能是旧版全量导出的，
 * 里面混着两套数据；不清不楚地导一半比直接报错更吓人，所以如实数出来告给用户。
 */
data class ImportResult(
    val children: Int,
    val courses: Int,
    val payments: Int,
    val lessons: Int,
    val skipped: Int = 0,
)

@Singleton
class LedgerRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val scheduler: SyncScheduler,
    private val reminderScheduler: ReminderScheduler,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    // ------------------------------------------------------------ 读

    /**
     * 全库：两套数据都在里面（我的 + 演示）。
     *
     * 只有设置页需要它 —— 它要同时显示两套的规模、算清「清空这套会删掉多少」。
     * 其余所有页面都用下面的 [data]，看到的是当前数据源那一套。
     */
    val allData: Flow<LedgerData> = combine(
        db.childDao().observeAll(),
        db.courseDao().observeAll(),
        db.paymentDao().observeAll(),
        db.lessonDao().observeAll(),
    ) { children, courses, payments, lessons ->
        LedgerData(
            children = children.filterNot { it.deleted }.map { it.toModel() },
            courses = courses.filterNot { it.deleted }.map { it.toModel() },
            payments = payments.filterNot { it.deleted }.map { it.toModel() },
            lessons = lessons.filterNot { it.deleted }.map { it.toModel() },
        )
    }.flowOn(Dispatchers.IO)

    /**
     * 当前数据源看到的那一套 —— 今天 / 课程 / 统计 / 我的 / 小组件全都走这里。
     *
     * 切成演示数据时，这里就只剩演示那套记录，真实账本一条都不参与统计；
     * 切回来立刻恢复，中间没有任何删除动作。
     */
    val data: Flow<LedgerData> = combine(
        allData,
        // 只盯「看哪一套」+ 演示总开关；改主题色之类的设置不该让整页数据重算一遍
        settings.state.map { it.effectiveSource }.distinctUntilChanged(),
    ) { ledger, source ->
        ledger.forSource(source)
    }.flowOn(Dispatchers.IO)

    /** 现在在看哪一套（演示总开关关着时恒为「我的数据」） */
    fun currentSource(): DataSource = settings.current.effectiveSource

    /**
     * 被用户手动删掉、但留了墓碑的课次。
     *
     * 这些记录 deleted = true，所以不在 [data] 里（那是给统计用的干净数据）。
     * 单独开一条流，课程详情页拿它显示"已移除的课次 · 点按恢复"。
     */
    val removedSlots: Flow<List<Lesson>> = db.lessonDao().observeAll()
        .map { lessons ->
            lessons.filter { it.suppressed && it.deleted }.map { it.toModel() }
        }
        .flowOn(Dispatchers.IO)

    fun observeSyncLogs(limit: Int = 20): Flow<List<SyncLogEntity>> =
        db.syncLogDao().observeRecent(limit)

    suspend fun child(id: String?): Child? = id?.let { db.childDao().byId(it)?.toModel() }
    suspend fun course(id: String?): Course? = id?.let { db.courseDao().byId(it)?.toModel() }
    suspend fun lesson(id: String?): Lesson? = id?.let { db.lessonDao().byId(it)?.toModel() }

    suspend fun paymentsOf(courseId: String): List<Payment> =
        db.paymentDao().byCourse(courseId).filterNot { it.deleted }.map { it.toModel() }

    suspend fun lessonsOf(courseId: String): List<Lesson> =
        db.lessonDao().byCourse(courseId).filterNot { it.deleted }.map { it.toModel() }

    suspend fun statsOf(courseId: String): LedgerCalc.CourseStats? {
        val course = course(courseId) ?: return null
        return LedgerCalc.computeStats(course, paymentsOf(courseId), lessonsOf(courseId))
    }

    suspend fun pendingCount(): Int {
        val since = settings.current.lastSyncAt
        return db.childDao().countDirty(since) + db.courseDao().countDirty(since) +
            db.paymentDao().countDirty(since) + db.lessonDao().countDirty(since)
    }

    // ------------------------------------------------------------ 孩子

    suspend fun saveChild(
        existing: Child?,
        name: String,
        nickname: String,
        birthday: LocalDate?,
        avatarColor: String,
        note: String,
    ): String {
        val now = Dates.now()
        val id = existing?.id ?: currentSource().newId()
        val entity = ChildEntity(
            id = id,
            name = name.trim(),
            nickname = nickname.trim(),
            birthday = birthday?.let { Dates.formatDate(it) },
            avatarColor = avatarColor,
            note = note.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deleted = false,
        )
        db.childDao().upsert(entity)
        afterWrite()
        return id
    }

    suspend fun deleteChild(childId: String) {
        val now = Dates.now()
        db.childDao().byId(childId)?.let { db.childDao().upsert(it.copy(deleted = true, updatedAt = now)) }
        db.courseDao().byChild(childId).forEach { course ->
            db.courseDao().upsert(course.copy(deleted = true, updatedAt = now))
            db.paymentDao().byCourse(course.id)
                .forEach { db.paymentDao().upsert(it.copy(deleted = true, updatedAt = now)) }
            db.lessonDao().byCourse(course.id)
                .forEach { db.lessonDao().upsert(it.copy(deleted = true, updatedAt = now)) }
        }
        afterWrite()
    }

    // ------------------------------------------------------------ 课程

    suspend fun saveCourse(
        existing: Course?,
        childId: String,
        orgName: String,
        courseName: String,
        category: Category,
        teacher: String?,
        location: String?,
        weekdayMask: Int,
        startTime: String?,
        /** 每个星期几各自的上课时间（key：0=周一 ... 6=周日）。给默认值，老调用点不用跟着改 */
        startTimesJson: String? = null,
        durationMinutes: Int,
        /** 每个星期几各自的单次课时长（key：0=周一 ... 6=周日）。给默认值，老调用点不用跟着改 */
        durationMinutesJson: String? = null,
        warningThreshold: Int,
        remindBeforeMinutes: Int,
        status: CourseStatus,
        note: String,
        /** 续费开新一期时传入被续的课程 id，串成期次链 */
        previousCourseId: String? = null,
    ): String {
        val now = Dates.now()
        val id = existing?.id ?: currentSource().newId()
        val entity = CourseEntity(
            id = id,
            childId = childId,
            orgName = orgName.trim(),
            courseName = courseName.trim(),
            category = category.name,
            teacher = teacher?.trim()?.takeIf { it.isNotBlank() },
            location = location?.trim()?.takeIf { it.isNotBlank() },
            weekdayMask = weekdayMask,
            startTime = startTime,
            // 一周上好几节课时，每天的时间可能不一样（周三 16:30、周六 10:00），单独记一份
            startTimesJson = startTimesJson,
            durationMinutes = durationMinutes.coerceIn(10, 600),
            // 和上课时间一样，一天上多长时间也可能各天不同（平时 60、周末 90），单独记一份
            durationMinutesJson = durationMinutesJson,
            warningThreshold = warningThreshold.coerceIn(0, 99),
            remindBeforeMinutes = remindBeforeMinutes.coerceIn(0, 240),
            status = status.name,
            // 编辑课程不能把续费链丢掉，否则期次会断在这里
            previousCourseId = existing?.previousCourseId ?: previousCourseId,
            totalLessonsBoughtHalf = existing?.totalLessonsBoughtHalf ?: 0L,
            note = note.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deleted = false,
        )
        db.courseDao().upsert(entity)
        recalcCourse(id)
        // 新课立刻排未来两周的课次，提醒闹钟才有东西可挂
        ensureUpcomingLessons()
        afterWrite()
        return id
    }

    suspend fun deleteCourse(courseId: String) {
        val now = Dates.now()
        db.courseDao().byId(courseId)?.let { db.courseDao().upsert(it.copy(deleted = true, updatedAt = now)) }
        db.paymentDao().byCourse(courseId)
            .forEach { db.paymentDao().upsert(it.copy(deleted = true, updatedAt = now)) }
        db.lessonDao().byCourse(courseId)
            .forEach { db.lessonDao().upsert(it.copy(deleted = true, updatedAt = now)) }
        afterWrite()
    }

    suspend fun updateCourseStatus(courseId: String, status: CourseStatus) {
        val now = Dates.now()
        db.courseDao().byId(courseId)?.let {
            db.courseDao().upsert(it.copy(status = status.name, updatedAt = now))
        }
        afterWrite()
    }

    // ------------------------------------------------------------ 缴费

    suspend fun savePayment(
        existing: Payment?,
        courseId: String,
        payDate: LocalDate,
        amountCents: Long,
        originalAmountCents: Long?,
        lessonsBoughtHalf: Long,
        lessonsBonusHalf: Long,
        payMethod: PayMethod,
        validUntil: LocalDate?,
        receipts: List<String>,
        note: String,
        kind: PaymentKind = PaymentKind.FULL,
        groupKey: String? = null,
        creditCents: Long = 0L,
    ): String {
        val now = Dates.now()
        val id = existing?.id ?: currentSource().newId()
        val entity = PaymentEntity(
            id = id,
            courseId = courseId,
            payDate = Dates.formatDate(payDate),
            amount = amountCents,
            originalAmount = originalAmountCents,
            lessonsBoughtHalf = lessonsBoughtHalf,
            lessonsBonusHalf = lessonsBonusHalf,
            kind = kind.name,
            groupKey = groupKey,
            creditCents = creditCents,
            payMethod = payMethod.name,
            validUntil = validUntil?.let { Dates.formatDate(it) },
            receiptPaths = Receipts.encodePaths(receipts),
            note = note.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deleted = false,
        )
        db.paymentDao().upsert(entity)
        recalcCourse(courseId)
        afterWrite()
        return id
    }

    /**
     * 补一笔定金对应的尾款。
     *
     * 尾款只补钱、不带课时：课时在交定金那一刻就已经锁定在定金那条记录上，
     * 否则「定金交了但还没上课」的这段时间里课时是 0，一打卡就会显示超扣。
     */
    suspend fun payDepositBalance(
        depositId: String,
        courseId: String,
        payDate: LocalDate,
        amountCents: Long,
        payMethod: PayMethod,
        note: String,
    ): String = savePayment(
        existing = null,
        courseId = courseId,
        payDate = payDate,
        amountCents = amountCents,
        originalAmountCents = null,
        lessonsBoughtHalf = 0L,
        lessonsBonusHalf = 0L,
        payMethod = payMethod,
        validUntil = null,
        receipts = emptyList(),
        note = note.ifBlank { "尾款" },
        kind = PaymentKind.BALANCE,
        groupKey = depositId,
    )

    suspend fun deletePayment(paymentId: String) {
        val now = Dates.now()
        val existing = db.paymentDao().byId(paymentId) ?: return
        db.paymentDao().upsert(existing.copy(deleted = true, updatedAt = now))
        recalcCourse(existing.courseId)
        afterWrite()
    }

    /** 退费：生成一条负数金额 + 负数课时的缴费记录 */
    suspend fun addRefund(        courseId: String,
        payDate: LocalDate,
        refundCents: Long,
        lessonsHalf: Long,
        note: String,
    ): String = savePayment(
        existing = null,
        courseId = courseId,
        payDate = payDate,
        amountCents = -kotlin.math.abs(refundCents),
        originalAmountCents = null,
        lessonsBoughtHalf = -kotlin.math.abs(lessonsHalf),
        lessonsBonusHalf = 0L,
        payMethod = PayMethod.OTHER,
        validUntil = null,
        receipts = emptyList(),
        note = note.ifBlank { "退费" },
    )

    /**
     * 一次性结清剩余课时（补录历史账用）。
     *
     * 两三年前上完的课，具体哪天上了哪节已经查不到了。逐节铺日期是编造，
     * 所以这里只生成一条「核销」记录：课时照扣，但不进打卡日历、不算一次上课。
     * 用固定 id，重复结清是在同一条上改数，不会越点越多。
     */
    suspend fun settleLessons(
        courseId: String,
        lessonsHalf: Long,
        note: String,
        finish: Boolean = true,
    ) {
        if (lessonsHalf <= 0L) return
        val now = Dates.now()
        val id = "settle-$courseId"
        val existing = db.lessonDao().byId(id)
        db.lessonDao().upsert(
            (existing ?: LessonEntity(id = id, courseId = courseId, createdAt = now)).copy(
                // 归零的时间戳：它不对应任何一天，日历和"今天"都不该看到它
                scheduledAt = LedgerMark.SETTLE_AT,
                actualAt = now,
                durationMinutes = 0,
                status = LessonStatus.DONE.name,
                deductedHalf = lessonsHalf,
                note = LedgerMark.SETTLE + note.ifBlank { "历史课时一次性结清" },
                autoGenerated = false,
                deleted = false,
                updatedAt = now,
            ),
        )
        recalcCourse(courseId)
        // 课时清零了就不再是"在读"：否则它会以「剩 0 节」的样子一直挂在首页
        if (finish) updateCourseStatus(courseId, CourseStatus.FINISHED)
        afterWrite()
    }

    // ------------------------------------------------------------ 缴费即录课

    /**
     * 第一次缴费：课程和首笔缴费一起落地。
     *
     * 现实里家长的动作是「今天给娃交了一学期学费」，机构、课程名、课时、金额都在收据上，
     * 课表反而要等开学排课才知道。所以入口就该是缴费，课程是它的产物 ——
     * 不再要求先建一门零缴费的空课程，再从课程里补一笔钱。
     *
     * 先写课程再写缴费：中途失败最多留一条没有缴费的课程（课程页能直接删掉），
     * 不会出现「有缴费却查不到课程」这种账对不上的脏数据。
     */
    suspend fun enroll(
        childId: String,
        course: CourseDraft,
        pay: PaymentDraft,
    ): String {
        val courseId = saveCourse(
            existing = null,
            childId = childId,
            orgName = course.orgName,
            courseName = course.courseName,
            category = course.category,
            teacher = course.teacher,
            location = course.location,
            weekdayMask = course.weekdayMask,
            startTime = course.startTime,
            startTimesJson = course.startTimesJson,
            durationMinutes = course.durationMinutes,
            // 课时长也照着草稿抄过去：平时 60、周末 90 这种按天的时长别在这步丢了
            durationMinutesJson = course.durationMinutesJson,
            warningThreshold = course.warningThreshold,
            remindBeforeMinutes = course.remindBeforeMinutes,
            status = course.status,
            note = course.note,
        )
        savePayment(
            existing = null,
            courseId = courseId,
            payDate = pay.payDate,
            amountCents = pay.amountCents,
            originalAmountCents = pay.originalAmountCents,
            lessonsBoughtHalf = pay.lessonsBoughtHalf,
            lessonsBonusHalf = pay.lessonsBonusHalf,
            payMethod = pay.payMethod,
            validUntil = pay.validUntil,
            receipts = pay.receipts,
            note = pay.note,
            kind = pay.kind,
            groupKey = pay.groupKey,
            creditCents = pay.creditCents,
        )
        return courseId
    }

    /**
     * 续费。
     *
     * [startNewPeriod] = false：只加一笔缴费，课时累加到原课程。
     * 适合「同一个班、同一个老师、接着上」——账是一条连续的线，剩余课时始终只有一个答案。
     *
     * [startNewPeriod] = true：按 [course] 建一条新课程，用 previousCourseId 串回原课程。
     * 旧期的课表、老师、单价就此冻结，后面改新课不会把历史改写掉；
     * 每期的消耗率和单价也能单独比较。旧期只有确实上完了才会被置为已结束。
     *
     * 这里刻意把「分期」和「结课」当成两件事：
     * 开新一期只是给同一门课再建一份档案，它**不负责结束旧的一期**。
     * 旧期结没结，只看两个事实——课时有没有上完，或者用户有没有自己把它标成已结束。
     * 上一版是无条件置 FINISHED，于是剩 29 节的课被冻住：不再排课、不进今天页、
     * 不在在读列表，可余额还挂在「待消耗学费」里，成了用不掉也抹不掉的幽灵余额。
     */
    suspend fun renew(
        existing: Course,
        startNewPeriod: Boolean,
        course: CourseDraft,
        pay: PaymentDraft,
    ): String {
        val canClose = existing.status != CourseStatus.ACTIVE || remainingHalfOf(existing.id) <= 0L
        val startNew = startNewPeriod && canClose
        if (!startNew) {
            savePayment(
                existing = null,
                courseId = existing.id,
                payDate = pay.payDate,
                amountCents = pay.amountCents,
                originalAmountCents = pay.originalAmountCents,
                lessonsBoughtHalf = pay.lessonsBoughtHalf,
                lessonsBonusHalf = pay.lessonsBonusHalf,
                payMethod = pay.payMethod,
                validUntil = pay.validUntil,
                receipts = pay.receipts,
                note = pay.note,
                kind = pay.kind,
                groupKey = pay.groupKey,
                creditCents = pay.creditCents,
            )
            // 上一期可能被手动标成「已结束」，续了费就该重新回到在读，
            // 否则课程页会把它归到已结课，排课和提醒也不会再跑
            if (existing.status != CourseStatus.ACTIVE) {
                updateCourseStatus(existing.id, CourseStatus.ACTIVE)
            }
            return existing.id
        }

        val now = Dates.now()
        db.courseDao().byId(existing.id)?.let {
            db.courseDao().upsert(it.copy(status = CourseStatus.FINISHED.name, updatedAt = now))
        }
        val newCourseId = saveCourse(
            existing = null,
            childId = existing.childId,
            orgName = course.orgName,
            courseName = course.courseName,
            category = course.category,
            teacher = course.teacher,
            location = course.location,
            weekdayMask = course.weekdayMask,
            startTime = course.startTime,
            // 新一期是照着旧期的课表抄的，每天各自的时间也得跟着抄过去
            startTimesJson = course.startTimesJson,
            durationMinutes = course.durationMinutes,
            // 课时长也照着草稿抄过去：平时 60、周末 90 这种按天的时长别在这步丢了
            durationMinutesJson = course.durationMinutesJson,
            warningThreshold = course.warningThreshold,
            remindBeforeMinutes = course.remindBeforeMinutes,
            status = CourseStatus.ACTIVE,
            note = course.note,
            previousCourseId = existing.id,
        )
        savePayment(
            existing = null,
            courseId = newCourseId,
            payDate = pay.payDate,
            amountCents = pay.amountCents,
            originalAmountCents = pay.originalAmountCents,
            lessonsBoughtHalf = pay.lessonsBoughtHalf,
            lessonsBonusHalf = pay.lessonsBonusHalf,
            payMethod = pay.payMethod,
            validUntil = pay.validUntil,
            receipts = pay.receipts,
            note = pay.note,
            kind = pay.kind,
            groupKey = pay.groupKey,
            creditCents = pay.creditCents,
        )
        afterWrite()
        return newCourseId
    }

    /**
     * 这门课还剩多少课时（半节）。
     *
     * 续费时用它判断「这一期到底结没结」——结课是事实，不该由"点了开新一期"这个动作决定。
     * 口径与 LedgerCalc.computeStats 一致：缴费合计（购买 + 赠送）减去已扣减。
     */
    private suspend fun remainingHalfOf(courseId: String): Long {
        val payments = db.paymentDao().byCourse(courseId).filterNot { it.deleted }
        val lessons = db.lessonDao().byCourse(courseId).filterNot { it.deleted }
        val bought = payments.sumOf { it.lessonsBoughtHalf + it.lessonsBonusHalf }
        val used = lessons.filter { it.deductedHalf > 0 }.sumOf { it.deductedHalf }
        return bought - used
    }

    // ------------------------------------------------------------ 课次

    suspend fun addLesson(
        courseId: String,
        date: LocalDate,
        time: LocalTime?,
        status: LessonStatus,
        deductedHalf: Long?,
        note: String,
        durationMinutes: Int,
    ): String {
        val now = Dates.now()
        val course = course(courseId)
        // 没指定时间时先用「这一天」的上课时间：给周三补打卡，默认就该是周三的 16:30，
        // 而不是整门课的统一时间；都取不到才落到 18:00
        val scheduled = Dates.toMillis(
            date,
            time ?: Dates.parseTimeOrNull(course?.startTimeOn(date))
                ?: Dates.parseTimeOrNull(course?.startTime)
                ?: LocalTime.of(18, 0)
        )
        // 这一天这门课已经排着一节「待上」、别的什么都没有时，用户这次记的就是同一节课：
        // 直接把那条改掉，不再另插一条。否则这一天会同时挂着「请假」和「待打卡」——
        // 请了假这节课就完结了，不该还占着一个没打卡的名额。
        // 判定：只有一条「待上」才合并，一天两节课的情况不会被误并。
        // 「已上」还要求时刻一致（同一天另一节真上了的话，那是另一节课）；
        // 请假 / 缺勤 / 调课本身不发生在某个时刻上，就不挑时刻了。
        if (status != LessonStatus.PENDING) {
            val sameDay = db.lessonDao().byCourse(courseId)
                .filterNot { it.deleted }
                .filter { Dates.toDate(it.scheduledAt) == date }
            val only = sameDay.singleOrNull()
            val sameMoment = only != null && only.scheduledAt == scheduled
            if (only != null && only.status == LessonStatus.PENDING.name &&
                (status != LessonStatus.DONE || sameMoment)
            ) {
                setLessonStatus(
                    lessonId = only.id,
                    status = status,
                    deductedHalf = deductedHalf,
                    note = note.trim().takeIf { it.isNotBlank() },
                    durationMinutes = durationMinutes,
                )
                return only.id
            }
        }
        val rules = settings.current
        // 「待上」的课还没上，先不扣：表单里会按当天时长算出 1.5 节，
        // 直接存进去会导致还没上就把课时扣了
        val deducted = when {
            status == LessonStatus.PENDING -> 0L
            deductedHalf != null -> deductedHalf
            // 打卡页直接点「已上」时不带扣数：按这节课的实际时长 ÷ 课程单节时长折算，
            // 45 分钟的课扣 45 分钟的钱，别再写死 1 节
            status == LessonStatus.DONE ->
                LedgerCalc.deductedFromDuration(durationMinutes, course?.durationMinutes ?: 0)
            else -> LedgerCalc.deductedFor(status, rules.absentDeducts, rules.leaveDeducts)
        }
        val entity = LessonEntity(
            id = currentSource().newId(),
            courseId = courseId,
            scheduledAt = scheduled,
            actualAt = if (status == LessonStatus.DONE) now else null,
            durationMinutes = durationMinutes,
            status = status.name,
            deductedHalf = deducted,
            paymentId = null,
            teacher = null,
            note = note.trim(),
            autoGenerated = false,
            createdAt = now,
            updatedAt = now,
            deleted = false,
        )
        db.lessonDao().upsert(entity)
        recalcCourse(courseId)
        afterWrite()
        return entity.id
    }

    suspend fun setLessonStatus(
        lessonId: String,
        status: LessonStatus,
        deductedHalf: Long? = null,
        note: String? = null,
        actualAt: Long? = null,
        /** 补打卡时用户可能把时长改成 90 分钟 / 2 小时，传进来才存得住，不然又被课表盖回去 */
        durationMinutes: Int? = null,
    ) {
        val now = Dates.now()
        val existing = db.lessonDao().byId(lessonId) ?: return
        val rules = settings.current
        val course = db.courseDao().byId(existing.courseId)
        val newDuration = durationMinutes ?: existing.durationMinutes
        val deducted = when {
            // 改回「待上」一律清零，之前扣掉的课时退回来
            status == LessonStatus.PENDING -> 0L
            deductedHalf != null -> deductedHalf
            // 没给扣数就按时长折算：1 节 = 课程定义的单节时长（45 / 60 / 90 分钟各不相同）
            status == LessonStatus.DONE ->
                LedgerCalc.deductedFromDuration(newDuration, course?.durationMinutes ?: 0)
            else -> LedgerCalc.deductedFor(status, rules.absentDeducts, rules.leaveDeducts)
        }
        db.lessonDao().upsert(
            existing.copy(
                status = status.name,
                deductedHalf = deducted,
                durationMinutes = newDuration,
                actualAt = actualAt ?: if (status == LessonStatus.DONE) (existing.actualAt ?: now) else null,
                note = note ?: existing.note,
                updatedAt = now,
            )
        )
        recalcCourse(existing.courseId)
        // 这节课已经上了 / 请假了，别再提醒
        if (status != LessonStatus.PENDING) reminderScheduler.cancel(lessonId)
        afterWrite()
    }

    suspend fun deleteLesson(lessonId: String) {
        val now = Dates.now()
        val existing = db.lessonDao().byId(lessonId) ?: return
        // 自动生成的课次：留个"别再补"的墓碑，否则下一次重排又会把这天加回来
        val keepTombstone = existing.autoGenerated &&
            Dates.toDate(existing.scheduledAt) >= Dates.today()
        db.lessonDao().upsert(
            existing.copy(
                deleted = true,
                suppressed = keepTombstone,
                updatedAt = now,
            )
        )
        recalcCourse(existing.courseId)
        reminderScheduler.cancel(lessonId)
        afterWrite()
    }

    /**
     * 恢复一节被手动删掉的课次。
     * 恢复后墓碑清除，这一天重新回到课表里。
     */
    suspend fun restoreLesson(lessonId: String) {
        val now = Dates.now()
        val existing = db.lessonDao().byId(lessonId) ?: return
        db.lessonDao().upsert(
            existing.copy(deleted = false, suppressed = false, updatedAt = now)
        )
        recalcCourse(existing.courseId)
        afterWrite()
    }

    /**
     * 一键清理同步冲突副本：所有带「[冲突副本]」标记的记录整批软删除。
     * 副本只是同步引擎保底留下的另一份拷贝，正主记录确认无误后就可以清掉；
     * 走软删除，会以墓碑形式同步到其他设备，不会又被云端拉回来。
     *
     * @return 清掉的条数
     */
    suspend fun deleteConflictCopies(): Int {
        val now = Dates.now()
        var removed = 0
        db.childDao().getAll().filter { !it.deleted && ConflictMark.isCopy(it.note) }.forEach {
            db.childDao().upsert(it.copy(deleted = true, updatedAt = now)); removed++
        }
        db.courseDao().getAll().filter { !it.deleted && ConflictMark.isCopy(it.note) }.forEach {
            db.courseDao().upsert(it.copy(deleted = true, updatedAt = now)); removed++
        }
        db.paymentDao().getAll().filter { !it.deleted && ConflictMark.isCopy(it.note) }.forEach {
            db.paymentDao().upsert(it.copy(deleted = true, updatedAt = now)); removed++
        }
        db.lessonDao().getAll().filter { !it.deleted && ConflictMark.isCopy(it.note) }.forEach {
            db.lessonDao().upsert(it.copy(deleted = true, updatedAt = now)); removed++
        }

        // 孤儿数据清理。两道保险：只有「孩子 / 课程表非空」时才判孤儿，
        // 否则首次同步、切数据源这种中间态可能把一个孩子都没有的账本当全孤儿整锅删掉。
        val knownChildIds = db.childDao().getAll().filter { !it.deleted }.map { it.id }.toSet()
        if (knownChildIds.isNotEmpty()) {
            db.courseDao().getAll().filter { !it.deleted && it.childId !in knownChildIds }.forEach {
                db.courseDao().upsert(it.copy(deleted = true, updatedAt = now)); removed++
            }
        }

        // 孤儿课次：所属课程在这套账本里已经不存在了（早期演示数据串号留下的），
        // 界面上永远看不到、统计也取不到，但会一直被同步搬来搬去，一并清掉。
        val knownCourseIds = db.courseDao().getAll().map { it.id }.toSet()
        if (knownCourseIds.isNotEmpty() && knownChildIds.isNotEmpty()) {
            db.lessonDao().getAll().filter { !it.deleted && it.courseId !in knownCourseIds }.forEach {
                db.lessonDao().upsert(it.copy(deleted = true, updatedAt = now)); removed++
            }
            // 孤儿缴费同上
            db.paymentDao().getAll().filter { !it.deleted && it.courseId !in knownCourseIds }.forEach {
                db.paymentDao().upsert(it.copy(deleted = true, updatedAt = now)); removed++
            }
        }

        if (removed > 0) afterWrite()
        return removed
    }

    /**
     * 手工加一节课（接龙、临时约课）。
     *
     * 与自动课次不同：手工课次用的是随机 UUID，不会被自动排课覆盖；
     * 如果这一天已经有自动课次，则复用那条记录（避免同一天出现两节课）。
     */
    suspend fun addManualLesson(
        courseId: String,
        date: LocalDate,
        time: LocalTime?,
        durationMinutes: Int,
        note: String,
    ): String {
        val now = Dates.now()
        val course = course(courseId)
        // 同手工补打卡：默认时间跟着这一天所在的星期几走，各不相同
        val fallback = Dates.parseTimeOrNull(course?.startTimeOn(date))
            ?: Dates.parseTimeOrNull(course?.startTime)
            ?: LocalTime.of(18, 0)
        val scheduled = Dates.toMillis(date, time ?: fallback)
        val id = autoLessonId(courseId, date.toString())
        val old = db.lessonDao().byId(id)
        db.lessonDao().upsert(
            LessonEntity(
                id = id,
                courseId = courseId,
                scheduledAt = scheduled,
                actualAt = null,
                durationMinutes = durationMinutes,
                status = LessonStatus.PENDING.name,
                deductedHalf = 0L,
                paymentId = old?.paymentId,
                teacher = null,
                note = note.trim(),
                autoGenerated = false,
                // 手工加的这天，之前删过的墓碑要清掉，不然它永远排不进来
                suppressed = false,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
                deleted = false,
            )
        )
        recalcCourse(courseId)
        afterWrite()
        return id
    }

    /**
     * 自动排课使用的确定性 UUID（name-based，等价 UUIDv3）：
     * 同一门课的同一天在任何设备上都会算出同一个 id，同步合并时天然去重。
     *
     * 演示课程派生出来的课次也带 `seed-` 前缀 —— 否则这门课的课次会跑到「我的数据」里，
     * 切换数据源时看见别的账本的课。
     */
    private fun autoLessonId(courseId: String, isoDate: String): String {
        val base = UUID.nameUUIDFromBytes("lesson:$courseId:$isoDate".toByteArray(Charsets.UTF_8)).toString()
        return if (DemoIds.isDemo(courseId)) DemoIds.PREFIX + base else base
    }

    private val startTimesJsonParser = Json { ignoreUnknownKeys = true }

    /**
     * 每星期几各自的上课时间（key：0=周一 ... 6=周日）。
     *
     * domain 里那份 decodeStartTimes 是私有的，这里照它的口径重新解一遍；
     * JSON 手改坏了也当没设过（返回空表），由调用方回落到统一时间，别让 App 崩。
     */
    private fun startTimesOf(course: CourseEntity): Map<Int, String> {
        val raw = course.startTimesJson
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            startTimesJsonParser.decodeFromString<Map<String, String>>(raw)
                .mapNotNull { (key, value) -> key.toIntOrNull()?.let { it to value } }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 这门课在某一天几点上课：那天单独设过时间就用单独的，否则回落到统一时间。
     *
     * 这里拿到的是 CourseEntity，直接用不了 domain 的 Course.startTimeOn（那是 Course 的成员），
     * 所以自己按星期几查一遍；查不到（这门课这天不排课、也没统一时间）返回 null，
     * 由调用方决定跳过这一天。
     */
    private fun startTimeFor(course: CourseEntity, date: LocalDate): LocalTime? =
        Dates.parseTimeOrNull(startTimesOf(course)[Dates.weekdayOf(date)])
            ?: Dates.parseTimeOrNull(course.startTime)

    /**
     * 每个星期几各自的单次课时长（key：0=周一 ... 6=周日）。
     *
     * 和 [startTimesOf] 一个套路：JSON 手改坏了就当没设过（返回空表），
     * 由 [durationFor] 回落到统一时长。value 转不成整数的那一条单独丢掉，
     * 别因为一天写坏了就让整门课的排课崩掉。
     */
    private fun durationsOf(course: CourseEntity): Map<Int, Int> {
        val raw = course.durationMinutesJson
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            startTimesJsonParser.decodeFromString<Map<String, String>>(raw)
                .mapNotNull { (key, value) ->
                    val weekday = key.toIntOrNull() ?: return@mapNotNull null
                    val minutes = value.toIntOrNull() ?: return@mapNotNull null
                    weekday to minutes
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 这门课在某一天一节课多少分钟：那天单独设过就用单独的，否则用统一的。
     *
     * 平时 1 小时、周末 1.5 小时这种排法很常见，排课时得按天取 ——
     * 拿统一时长去填，周末那节课的时长就对不上课表了。
     */
    private fun durationFor(course: CourseEntity, date: LocalDate): Int =
        durationsOf(course)[Dates.weekdayOf(date)] ?: course.durationMinutes

    /**
     * 自愈：清掉「一节课两个状态」的历史副本。
     *
     * 老版本在「今天页补记 / 课程详情添加课次」里把状态选成请假、缺勤、调课时，
     * 是**另插一条**新记录，而不是把当天那条「待上」改掉 —— 于是同一天躺着一节课的
     * 两个身份：已经请假了，还挂着「待打卡」。
     *
     * 判定分两档，都要求「同一门课 + 同一天」已经有落定的记录（已上 / 请假 / 缺勤 / 调课），
     * 且「已取消」不算落定 —— 取消后那天补排一节课是正常的：
     * 1. 时刻完全一致 → 肯定是同一节课，删掉那条「待上」
     * 2. 时刻不一致，但那天只有这一条「待上」、且它是自动排出来的，同时那天有请假/缺勤/调课
     *    → 也是同一节课（请假本来就不挑几点），删掉
     * 一天两节课的情况：那两条「待上」不会被一起清掉，只清时刻对得上的那条。
     *
     * 删「待上」那条时留墓碑（自动排的），免得下一轮排课又把它补回来。
     */
    suspend fun pruneSettledDuplicates(): Int {
        val now = Dates.now()
        val today = Dates.today()
        // 只动当前数据源：在「我的数据」下改到演示账本，两套数据就不独立了
        val demo = currentSource().isDemo
        val live = db.lessonDao().getAll()
            .filterNot { it.deleted }
            .filter { DemoIds.isDemo(it.courseId) == demo }
        val ghosts = mutableListOf<LessonEntity>()
        live.groupBy { it.courseId to Dates.toDate(it.scheduledAt) }.forEach { (_, rows) ->
            val pendings = rows.filter { it.status == LessonStatus.PENDING.name }
            val settled = rows.filter {
                it.status != LessonStatus.PENDING.name && it.status != LessonStatus.CANCELLED.name
            }
            if (pendings.isEmpty() || settled.isEmpty()) return@forEach
            val settledTimes = settled.map { it.scheduledAt }.toSet()
            val hasCancelledClass = settled.any { it.status != LessonStatus.DONE.name }
            pendings.forEach { pending ->
                val sameMoment = pending.scheduledAt in settledTimes
                val soloAutoSlot = hasCancelledClass && pendings.size == 1 && pending.autoGenerated
                if (sameMoment || soloAutoSlot) ghosts += pending
            }
        }
        var pruned = 0
        ghosts.forEach { ghost ->
            db.lessonDao().upsert(
                ghost.copy(
                    deleted = true,
                    // 自动排出来的那条要留个「别再补」的墓碑，手工加的不用
                    suppressed = ghost.autoGenerated && Dates.toDate(ghost.scheduledAt) >= today,
                    updatedAt = now,
                )
            )
            pruned++
        }
        if (pruned > 0) afterWrite()
        return pruned
    }

    /**
     * 按各课程 weekdayMask + 各天的上课时间生成未来两周的"待上"课次。
     * 已存在同一天记录的课程会跳过，反复调用不会重复生成。
     *
     * 同时会把已经排好但还没上的自动课次跟新课表对齐：
     * - 改了上课时间 → 课次时间跟着改（否则课表和上课提醒对不上）
     * - 取消了某个上课日 → 那天还没上的课次作废
     *
     * 两条"不自动排课"的规则：
     * 1. 某个上课日没填时间（接龙课、临时约课）→ 那天不生成课次，只用手工加的；
     *    同一门课的其他日子照常排 —— 一周好几天时间参差不齐是本分，不是异常
     * 2. 用户手动删过某天的课次 → 那一天留着墓碑，不再补回来
     */
    suspend fun ensureUpcomingLessons(): Int {
        val now = Dates.now()
        val today = Dates.today()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val demo = currentSource().isDemo
        // 排课之前先把历史遗留的「请假了还挂着待打卡」副本清掉：
        // 留着它这一天会既算请假又算待打卡，而且会挡住同一天正常的排课判断
        pruneSettledDuplicates()
        // 只给当前数据源里的课排课：真实模式下不该去动演示课的课次，反之亦然
        val courses = db.courseDao().getAll().filter {
            !it.deleted && it.status == CourseStatus.ACTIVE.name && it.weekdayMask != 0 &&
                DemoIds.isDemo(it.id) == demo
        }
        var created = 0
        var synced = 0
        for (course in courses) {
            // 上课时间按天给，不在这里一次性取一个统一时间 ——
            // 一周上好几节课时每天可能不同（周三 16:30、周六 10:00），得排到哪天用哪天的时间
            val all = db.lessonDao().byCourse(course.id)
            val existing = all.filterNot { it.deleted }
            // 用户手动删掉过的日子：留个墓碑，自动排课时跳过
            val suppressedDays = all.filter { it.suppressed }
                .map { Dates.toDate(it.scheduledAt) }
                .toSet()

            // 先把已有课次对齐到新课表
            for (lesson in existing) {
                if (!lesson.autoGenerated) continue
                if (lesson.status != LessonStatus.PENDING.name) continue
                val date = Dates.toDate(lesson.scheduledAt)
                // 只动今天及以后的课次：更早的课没打卡也不改，免得篡改历史
                if (date < today) continue
                if (course.weekdayMask and Dates.bit(date) == 0) {
                    // 这个星期几不再上课了，这条课次作废
                    db.lessonDao().upsert(lesson.copy(deleted = true, updatedAt = now))
                    synced++
                    continue
                }
                // 这天几点上课：取不到就原样留着（这天没排时间不等于课取消了），
                // 不动它也不作废，更不要拿统一时间或 18:00 去覆盖人家手工调过的时间
                val time = startTimeFor(course, date) ?: continue
                val newAt = Dates.toMillis(date, time)
                // 时长也要跟着课表走：改了某天的时长后，已经排好的那节课得一起改，
                // 否则「周三 90 分钟」改完，日历里那节课还挂着旧的 60
                val newDuration = durationFor(course, date)
                if (newAt != lesson.scheduledAt || newDuration != lesson.durationMinutes) {
                    db.lessonDao().upsert(
                        lesson.copy(
                            scheduledAt = newAt,
                            durationMinutes = newDuration,
                            updatedAt = now,
                        )
                    )
                    synced++
                }
            }

            // 作废过的课次要重新算进来，否则同一天会被漏掉
            val existingDays = all
                .filterNot { it.deleted }
                .map { Dates.toDate(it.scheduledAt) }
                .toSet()

            for (offset in 0 until 14) {
                val date = weekStart.plusDays(offset.toLong())
                if (course.weekdayMask and (1 shl Dates.weekdayOf(date)) == 0) continue
                // 这天几点上课：单独设过用单独的，没设回落统一时间；都没有就跳过这一天，
                // 不影响这门课的其他日子
                val time = startTimeFor(course, date) ?: continue
                if (existingDays.contains(date)) continue
                // 用户手动删过这一天，尊重他的意思，别再自动补回来
                if (suppressedDays.contains(date)) continue
                db.lessonDao().upsert(
                    LessonEntity(
                        // 用“课程 + 日期”派生固定 UUID：夫妻两台手机各自排课也会得到同一条记录，
                        // 否则同步合并后同一天会出现两节课
                        id = autoLessonId(course.id, date.toString()),
                        courseId = course.id,
                        scheduledAt = Dates.toMillis(date, time),
                        // 时长也按天取：周末那节课可能是 1.5 小时，用统一时长就对不上课表了
                        durationMinutes = durationFor(course, date),
                        status = LessonStatus.PENDING.name,
                        deductedHalf = 0L,
                        autoGenerated = true,
                        suppressed = false,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                created++
            }
        }
        if (created > 0 || synced > 0) afterWrite()
        return created
    }

    // ------------------------------------------------------------ 维护

    /** FIFO 批次重算 + 冗余字段维护（不刷新 updatedAt，避免制造同步脏数据） */
    private suspend fun recalcCourse(courseId: String) {
        val payments = db.paymentDao().byCourse(courseId).filterNot { it.deleted }.map { it.toModel() }
        val lessons = db.lessonDao().byCourse(courseId).filterNot { it.deleted }.map { it.toModel() }
        val allocation = LedgerCalc.allocateFifo(payments, lessons)
        for (lesson in lessons) {
            val expected = allocation.primaryPaymentId(lesson.id)
            if (lesson.paymentId != expected) {
                db.lessonDao().setPaymentId(lesson.id, expected)
            }
        }
        db.courseDao().setTotalBought(courseId, payments.sumOf { it.totalHalf })
    }

    private suspend fun recalcAllCourses() {
        db.courseDao().getAll().filterNot { it.deleted }.forEach { recalcCourse(it.id) }
    }

    private fun afterWrite() {
        scheduler.requestSync()
        // 课表/打卡变了，提醒也要跟着重排
        reminderScheduler.rescheduleAsync()
    }

    // ------------------------------------------------------------ 数据源切换 / 清空 / 导入导出

    /**
     * 切换数据源：只换「看哪一套」，一条记录都不动。
     *
     * 切到演示数据时，如果库里还没有示例记录就现造一套 —— 第一次点过去不用再点一次「载入」；
     * 切完补排一次当前这套的课次并重挂提醒，否则今天页会空着、闹钟还挂着另一套的课。
     */
    suspend fun switchSource(target: DataSource) {
        // 总开关关着时不允许切到演示：入口本来也藏着，这里再兜一层，防止残留的调用方把示例数据翻出来
        if (target.isDemo && !settings.current.demoEnabled) return
        if (currentSource() == target) return
        settings.setDataSource(target.key)
        if (target.isDemo) ensureDemoSeeded()
        ensureUpcomingLessons()
        reminderScheduler.rescheduleAsync()
    }

    /**
     * 演示数据总开关。
     *
     * 打开 = 立刻切到示例账本（没有就现造一套），关掉 = 立刻收回「我的数据」。
     * 一个开关管到底，用户不需要再理解「两套数据」这层概念：
     * 关着的时候，今天 / 课程 / 统计 / 我的 / 小组件 / 提醒里都不会出现任何示例记录。
     */
    suspend fun setDemoEnabled(enabled: Boolean) {
        settings.setDemoEnabled(enabled)
        val target = if (enabled) DataSource.DEMO else DataSource.MINE
        if (currentSource() != target) settings.setDataSource(target.key)
        if (enabled) ensureDemoSeeded()
        // reschedule() 会把「这次不该再响」的闹钟取消掉（切换后另一套的课天然落在计划之外），
        // 所以开关一变就重排一次，不必单独去扫演示课的闹钟。
        ensureUpcomingLessons()
        reminderScheduler.rescheduleAsync()
    }

    /**
     * 库里还没有示例记录时补一套；已经有就什么都不做。
     *
     * 「有没有」只看 id 前缀，所以用户在示例账本里改过的课名、加过的记录都不会被冲掉。
     */
    suspend fun ensureDemoSeeded(): Boolean {
        val hasDemo = db.childDao().getAll().any { !it.deleted && DemoIds.isDemo(it.id) } ||
            db.courseDao().getAll().any { !it.deleted && DemoIds.isDemo(it.id) }
        if (hasDemo) return false
        seedDemo()
        settings.markSeeded()
        afterWrite()
        return true
    }

    /**
     * 清掉某一套数据：连同挂在它下面的缴费、课次一起，[source] 之外的一条都不动。
     *
     * 用软删除而不是物理 DELETE，是因为同步走的是全量快照合并：
     * 本地硬删之后云端那份还在，下一次同步会把它整批再拉回来，
     * 用户就会看到「明明清过了又回来了」。留墓碑才真的清得掉，也能同步到另一台设备。
     *
     * 判定只看 id 前缀和它挂在哪个孩子下，不看名字：示例课被改名、孩子被改昵称照样认得出来。
     * 清演示那一套时顺带把数据源切回「我的数据」，否则用户删完还站在一张空页面上。
     */
    suspend fun clearSource(source: DataSource): DemoStats {
        val now = Dates.now()
        val demo = source.isDemo
        val childRows = db.childDao().getAll()
        val childIds = childRows.filter { DemoIds.isDemo(it.id) == demo }.map { it.id }.toSet()
        val courses = db.courseDao().getAll().filter { it.childId in childIds }
        val courseIds = courses.map { it.id }.toSet()
        val payments = db.paymentDao().getAll().filter { !it.deleted && it.courseId in courseIds }
        val lessons = db.lessonDao().getAll().filter { !it.deleted && it.courseId in courseIds }

        lessons.forEach { db.lessonDao().upsert(it.copy(deleted = true, updatedAt = now)) }
        payments.forEach { db.paymentDao().upsert(it.copy(deleted = true, updatedAt = now)) }
        courses.filterNot { it.deleted }
            .forEach { db.courseDao().upsert(it.copy(deleted = true, updatedAt = now)) }
        childRows.filter { it.id in childIds && !it.deleted }
            .forEach { db.childDao().upsert(it.copy(deleted = true, updatedAt = now)) }

        if (demo && currentSource().isDemo) settings.setDataSource(DataSource.MINE.key)
        afterWrite()
        return DemoStats(
            children = childRows.count { it.id in childIds && !it.deleted },
            courses = courses.count { !it.deleted },
            payments = payments.size,
            lessons = lessons.size,
        )
    }

    private suspend fun seedDemo() {
        val now = Dates.now()
        val today = Dates.today()

        val childId = DemoIds.CHILD
        db.childDao().upsert(
            ChildEntity(
                id = childId,
                name = "小明",
                nickname = "明明",
                birthday = today.minusYears(8).withMonth(5).withDayOfMonth(12).toString(),
                avatarColor = "#FF8A4C",
                note = "示例数据，可自由修改或删除",
                createdAt = now,
                updatedAt = now,
            )
        )

        val pianoId = DemoIds.COURSE_PIANO
        db.courseDao().upsert(
            CourseEntity(
                id = pianoId,
                childId = childId,
                orgName = "星海钢琴艺术中心",
                courseName = "钢琴一对一",
                category = Category.MUSIC.name,
                teacher = "李老师",
                location = "文化路 88 号 3 楼",
                weekdayMask = 1 shl 3,
                startTime = "16:00",
                durationMinutes = 60,
                warningThreshold = 5,
                status = CourseStatus.ACTIVE.name,
                createdAt = now,
                updatedAt = now,
            )
        )

        val swimId = DemoIds.COURSE_SWIM
        db.courseDao().upsert(
            CourseEntity(
                id = swimId,
                childId = childId,
                orgName = "蓝豚游泳俱乐部",
                courseName = "游泳私教",
                category = Category.SPORTS.name,
                teacher = "王教练",
                location = "体育中心游泳馆",
                weekdayMask = 1 shl 5,
                startTime = "10:00",
                durationMinutes = 60,
                warningThreshold = 5,
                status = CourseStatus.ACTIVE.name,
                createdAt = now + 1,
                updatedAt = now + 1,
            )
        )

        db.paymentDao().upsert(
            PaymentEntity(
                id = "${DemoIds.PREFIX}pay-1",
                courseId = pianoId,
                payDate = Dates.formatDate(today.minusMonths(4).withDayOfMonth(1)),
                amount = 480_000,
                originalAmount = 520_000,
                lessonsBoughtHalf = 40,
                lessonsBonusHalf = 4,
                payMethod = PayMethod.WECHAT.name,
                validUntil = Dates.formatDate(today.plusMonths(8)),
                note = "春季续费，送 2 节",
                createdAt = now + 2,
                updatedAt = now + 2,
            )
        )
        db.paymentDao().upsert(
            PaymentEntity(
                id = "${DemoIds.PREFIX}pay-2",
                courseId = pianoId,
                payDate = Dates.formatDate(today.minusMonths(1).withDayOfMonth(5)),
                amount = 240_000,
                lessonsBoughtHalf = 20,
                lessonsBonusHalf = 0,
                payMethod = PayMethod.ALIPAY.name,
                note = "加购 10 节",
                createdAt = now + 3,
                updatedAt = now + 3,
            )
        )
        db.paymentDao().upsert(
            PaymentEntity(
                id = "${DemoIds.PREFIX}pay-3",
                courseId = swimId,
                payDate = Dates.formatDate(today.minusDays(9)),
                amount = 180_000,
                lessonsBoughtHalf = 12,
                lessonsBonusHalf = 0,
                payMethod = PayMethod.WECHAT.name,
                validUntil = Dates.formatDate(today.plusMonths(3)),
                note = "",
                createdAt = now + 4,
                updatedAt = now + 4,
            )
        )

        val doneLessons = listOf(
            Triple(pianoId, today.minusDays(21), 16),
            Triple(pianoId, today.minusDays(14), 16),
            Triple(swimId, today.minusDays(17), 10),
            Triple(swimId, today.minusDays(10), 10),
        )
        doneLessons.forEachIndexed { index, (courseId, date, hour) ->
            db.lessonDao().upsert(
                LessonEntity(
                    id = "${DemoIds.PREFIX}lesson-done-$index",
                    courseId = courseId,
                    scheduledAt = Dates.toMillis(date, LocalTime.of(hour, 0)),
                    actualAt = Dates.toMillis(date, LocalTime.of(hour, 5)),
                    durationMinutes = 60,
                    status = LessonStatus.DONE.name,
                    deductedHalf = 2,
                    note = if (index == 0) "第一次课，识谱" else "",
                    createdAt = now + 10 + index,
                    updatedAt = now + 10 + index,
                )
            )
        }

        val pendingLessons = listOf(
            Triple(pianoId, nextWeekday(today, 3), 16),
            Triple(swimId, nextWeekday(today, 5), 10),
        )
        pendingLessons.forEachIndexed { index, (courseId, date, hour) ->
            db.lessonDao().upsert(
                LessonEntity(
                    id = "${DemoIds.PREFIX}lesson-pending-$index",
                    courseId = courseId,
                    scheduledAt = Dates.toMillis(date, LocalTime.of(hour, 0)),
                    durationMinutes = 60,
                    status = LessonStatus.PENDING.name,
                    deductedHalf = 0,
                    autoGenerated = true,
                    createdAt = now + 20 + index,
                    updatedAt = now + 20 + index,
                )
            )
        }

        recalcCourse(pianoId)
        recalcCourse(swimId)
        scheduler.requestSync(3_000L, "seed")
    }

    private fun nextWeekday(from: LocalDate, weekdayIndex: Int): LocalDate {
        var date = from
        while (Dates.weekdayOf(date) != weekdayIndex) date = date.plusDays(1)
        return date
    }

    /** 两套数据各自的规模，设置页拿去显示「这套有多大」 */
    suspend fun sourceStats(): Map<DataSource, DemoStats> {
        val all = LedgerData(
            children = db.childDao().getAll().filterNot { it.deleted }.map { it.toModel() },
            courses = db.courseDao().getAll().filterNot { it.deleted }.map { it.toModel() },
            payments = db.paymentDao().getAll().filterNot { it.deleted }.map { it.toModel() },
            lessons = db.lessonDao().getAll().filterNot { it.deleted }.map { it.toModel() },
        )
        return DataSource.entries.associateWith { all.forSource(it).statsOf() }
    }

    /**
     * 导出「正在看的这一套」。
     *
     * 以前是全库导出：站在演示账本上点「导出备份」，备份文件里连真实账本一起带出去了，
     * 两套数据在文件层面也搅在一起。现在只导当套，两套各留各的文件。
     */
    suspend fun exportJson(): String {
        val s = settings.current
        val demo = currentSource().isDemo
        // 只导出活记录：墓碑是给同步用的，写进备份文件反而会在恢复时把删掉的东西带回来
        val childRows = db.childDao().getAll().filterNot { it.deleted }
            .filter { DemoIds.isDemo(it.id) == demo }
        val childIds = childRows.map { it.id }.toSet()
        val courseRows = db.courseDao().getAll().filterNot { it.deleted }
            .filter { it.childId in childIds && (demo || !DemoIds.isDemo(it.id)) }
        val courseIds = courseRows.map { it.id }.toSet()
        val snapshot = LedgerSnapshotDto(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = Dates.now(),
            deviceId = s.deviceId,
            children = childRows,
            courses = courseRows,
            payments = db.paymentDao().getAll().filterNot { it.deleted }
                .filter { it.courseId in courseIds },
            lessons = db.lessonDao().getAll().filterNot { it.deleted }
                .filter { it.courseId in courseIds },
        )
        return json.encodeToString(snapshot)
    }

    /**
     * 导入到「正在看的这一套」。
     *
     * 归属口径和 `forSource` 完全一致：孩子看 id 前缀，课程看挂在哪个孩子下，
     * 缴费和课次看挂在哪个课程下。落在外面的一律跳过并计数 —— 旧版导出的全量备份
     * 就是两套混着的样子，静默丢一半比明说更坑人。
     */
    suspend fun importJson(text: String): ImportResult {
        val snapshot = json.decodeFromString<LedgerSnapshotDto>(text)
        val now = Dates.now()
        val demo = currentSource().isDemo
        // 导入的记录全部打上当前时间戳，保证覆盖本地同名记录
        val children = snapshot.children.filter { DemoIds.isDemo(it.id) == demo }
            .map { it.copy(updatedAt = now, deleted = false) }
        val childIds = children.map { it.id }.toSet()
        val courses = snapshot.courses
            .filter { it.childId in childIds && (demo || !DemoIds.isDemo(it.id)) }
            .map { it.copy(updatedAt = now, deleted = false) }
        val courseIds = courses.map { it.id }.toSet()
        val payments = snapshot.payments.filter { it.courseId in courseIds }
            .map { it.copy(updatedAt = now, deleted = false) }
        val lessons = snapshot.lessons.filter { it.courseId in courseIds }
            .map { it.copy(updatedAt = now, deleted = false) }
        if (children.isNotEmpty()) db.childDao().upsertAll(children)
        if (courses.isNotEmpty()) db.courseDao().upsertAll(courses)
        if (payments.isNotEmpty()) db.paymentDao().upsertAll(payments)
        if (lessons.isNotEmpty()) db.lessonDao().upsertAll(lessons)
        recalcAllCourses()
        // 这里刻意不清同步游标。
        //
        // 合并判定是「本地和远端都在上次同步之后改过、且内容不同 → 造冲突副本」。
        // 一旦把 lastSyncAt 归零，所有记录都满足「改过」这个前提，只要两台设备的
        // 同一条记录有点差别就会批量生产「[冲突副本]」—— 之前一口气冒出 37 条就是这原因。
        // 导入的记录已经打上当前时间戳，本来就会作为本地更新赢下合并，游标留着更安全。
        afterWrite()
        val total = snapshot.children.size + snapshot.courses.size +
            snapshot.payments.size + snapshot.lessons.size
        val taken = children.size + courses.size + payments.size + lessons.size
        return ImportResult(
            children = children.size,
            courses = courses.size,
            payments = payments.size,
            lessons = lessons.size,
            skipped = (total - taken).coerceAtLeast(0),
        )
    }
}
