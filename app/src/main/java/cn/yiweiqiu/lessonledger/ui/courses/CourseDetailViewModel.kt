package cn.yiweiqiu.lessonledger.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseDraft
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.Lesson
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.Payment
import cn.yiweiqiu.lessonledger.domain.PaymentDraft
import cn.yiweiqiu.lessonledger.ui.forms.CourseFormData
import cn.yiweiqiu.lessonledger.ui.forms.CourseOption
import cn.yiweiqiu.lessonledger.ui.forms.LessonFormData
import cn.yiweiqiu.lessonledger.ui.forms.PaymentFormData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val selectedId = MutableStateFlow<String?>(null)

    data class UiState(
        val course: Course? = null,
        val child: Child? = null,
        val stats: LedgerCalc.CourseStats? = null,
        /** 按日期倒序 */
        val payments: List<Payment> = emptyList(),
        val lessons: List<Lesson> = emptyList(),
        /** 还没上的课次：按时间正序，最近的一节在最上面 */
        val pendingLessons: List<Lesson> = emptyList(),
        /** 已经处理过的课次（已上 / 请假 / 缺勤等）：按时间倒序，最近的在最上面 */
        val pastLessons: List<Lesson> = emptyList(),
        /** 被用户手动删掉、但还留着墓碑的课次（可一键恢复） */
        val removedSlots: List<Lesson> = emptyList(),
        /** 续费表单要用的现状摘要（第几期、上次买了多少） */
        val option: CourseOption? = null,
        /** 交过定金、还没补尾款的那几笔 */
        val pendingDeposits: List<Payment> = emptyList(),
        /** 同一个孩子的在读课程：补打卡时可以换一门课记，不用退回去再进 */
        val siblingCourses: List<Course> = emptyList(),
        val absentDeducts: Boolean = true,
        val fallbackThreshold: Int = 5,
        val loaded: Boolean = false,
    )

    val uiState = combine(repo.data, repo.removedSlots, settings.state, selectedId) { data, removedAll, setting, id ->
        if (id == null) return@combine UiState()
        val course = data.courseById(id) ?: return@combine UiState(loaded = true)
        val paymentsAsc = data.paymentsOf(course.id)
        val lessons = data.lessonsOf(course.id)
        val removed = removedAll
            .filter { it.courseId == course.id }
            .filter { cn.yiweiqiu.lessonledger.core.Dates.toDate(it.scheduledAt) >= cn.yiweiqiu.lessonledger.core.Dates.today() }
            .sortedBy { it.scheduledAt }
        val stats = LedgerCalc.computeStats(course, paymentsAsc, lessons)
        val lastPaid = paymentsAsc.lastOrNull { it.amountCents > 0 && it.totalHalf > 0 }
        UiState(
            course = course,
            child = data.childOf(course),
            stats = stats,
            payments = paymentsAsc.sortedByDescending { it.payDate },
            lessons = lessons.sortedByDescending { it.scheduledAt },
            // 上课记录分两段看：接下来要上的按时间正序（下一节在最上面），
            // 上过的按时间倒序（最近一次在最上面），混在一起怎么排都别扭
            pendingLessons = lessons
                .filter { it.status == LessonStatus.PENDING }
                .sortedBy { it.scheduledAt },
            pastLessons = lessons
                // 核销记录（历史课时一次性结清）不挂在任何一天上，
                // 列进来会变成一条「1970-01-01 已上」，改成在信息卡里写「其中结清 N 节」
                .filter { it.status != LessonStatus.PENDING }
                .filter { it.scheduledAt > 0L }
                .sortedByDescending { it.scheduledAt },
            removedSlots = removed,
            option = CourseOption(
                course = course,
                stats = stats,
                period = data.coursePeriod(course),
                lastAmountCents = lastPaid?.amountCents ?: 0L,
                lastLessonsHalf = lastPaid?.lessonsBoughtHalf ?: 0L,
            ),
            absentDeducts = setting.absentDeducts,
            fallbackThreshold = setting.defaultThreshold,
            pendingDeposits = data.pendingDeposits(course.id),
            siblingCourses = data.courses
                .filter { it.childId == course.childId && !it.deleted && it.status == CourseStatus.ACTIVE }
                .sortedBy { it.courseName },
            loaded = true,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun select(courseId: String?) {
        if (selectedId.value != courseId) selectedId.value = courseId
    }

    fun savePayment(existing: Payment?, form: PaymentFormData) {
        val courseId = uiState.value.course?.id ?: return
        viewModelScope.launch {
            repo.savePayment(
                existing = existing,
                courseId = courseId,
                payDate = form.payDate,
                amountCents = form.amountCents,
                originalAmountCents = form.originalAmountCents,
                lessonsBoughtHalf = form.lessonsBoughtHalf,
                lessonsBonusHalf = form.lessonsBonusHalf,
                payMethod = form.payMethod,
                validUntil = form.validUntil,
                receipts = form.receipts,
                note = form.note,
                kind = form.kind,
                groupKey = form.groupKey,
                creditCents = form.creditCents,
            )
        }
    }

    /**
     * 补尾款：只记钱，不碰课时。
     *
     * 走仓库里专门的那条路径，groupKey 指向定金那条记录，两笔就归到同一个批次里，
     * 单价与待消耗金额仍然按整期算，不会因为拆成两次付而变化。
     */
    fun payBalance(deposit: Payment, form: PaymentFormData) {
        val courseId = uiState.value.course?.id ?: return
        viewModelScope.launch {
            repo.payDepositBalance(
                depositId = deposit.id,
                courseId = courseId,
                payDate = form.payDate,
                amountCents = form.amountCents,
                payMethod = form.payMethod,
                note = form.note,
            )
        }
    }

    /** 在详情页里改主意、要给另一门新课程交钱时走这条 */
    fun enroll(course: CourseDraft, pay: PaymentDraft, onCreated: (String) -> Unit) {
        val childId = uiState.value.course?.childId ?: return
        viewModelScope.launch {
            onCreated(repo.enroll(childId, course, pay))
        }
    }

    /** 续费：接续或开新一期，新一期时把新课程 id 回调出去让页面跳过去 */
    fun renew(startNewPeriod: Boolean, course: CourseDraft, pay: PaymentDraft, onCreated: (String) -> Unit) {
        val target = uiState.value.course ?: return
        viewModelScope.launch {
            val id = repo.renew(target, startNewPeriod, course, pay)
            if (id != target.id) onCreated(id)
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch { repo.deletePayment(paymentId) }
    }

    fun refund(lessonsHalf: Long, refundCents: Long, note: String) {
        val courseId = uiState.value.course?.id ?: return
        val stats = uiState.value.stats
        viewModelScope.launch {
            repo.addRefund(courseId, LocalDate.now(), refundCents, lessonsHalf, note)
            // 把剩余课时整段退掉（或退超了）就说明这门课不再上了，
            // 顺手归档成「已退费」—— 否则它会以「在读、剩 0 节」的样子赖在列表里，
            // 首页的待消耗余额也会一直挂着一门空课
            val left = (stats?.remainingHalf ?: 0L) - lessonsHalf
            if (left <= 0L) repo.updateCourseStatus(courseId, CourseStatus.REFUNDED)
        }
    }

    fun settleLessons(lessonsHalf: Long, note: String) {
        val courseId = uiState.value.course?.id ?: return
        viewModelScope.launch { repo.settleLessons(courseId, lessonsHalf, note) }
    }

    fun setLessonStatus(lessonId: String, status: LessonStatus, deductedHalf: Long? = null) {
        viewModelScope.launch { repo.setLessonStatus(lessonId, status, deductedHalf) }
    }

    fun addLesson(form: LessonFormData) {
        viewModelScope.launch {
            // 表单里可以切到别的课程，得按表单里选的那门课取时长，不能一律用当前页这门
            val minutes = form.durationMinutes ?: courseOf(form.courseId)?.durationOn(form.date) ?: 60
            if (form.status == LessonStatus.PENDING) {
                // 排课：复用「课程 + 日期」的固定 id，跟自动课次同一天不会重复出现两条
                repo.addManualLesson(
                    courseId = form.courseId,
                    date = form.date,
                    time = cn.yiweiqiu.lessonledger.core.Dates.parseTimeOrNull(form.time),
                    // 时长按那天所属星期几取：周末 90 分钟的课不该记成 60
                    durationMinutes = minutes,
                    note = form.note,
                )
            } else {
                repo.addLesson(
                    courseId = form.courseId,
                    date = form.date,
                    time = cn.yiweiqiu.lessonledger.core.Dates.parseTimeOrNull(form.time),
                    status = form.status,
                    deductedHalf = form.deductedHalf,
                    note = form.note,
                    durationMinutes = minutes,
                )
            }
        }
    }

    /** 表单里可能选了同孩子的另一门课，取时长/时间要按那一门来 */
    private fun courseOf(courseId: String): Course? =
        uiState.value.siblingCourses.firstOrNull { it.id == courseId } ?: uiState.value.course

    /**
     * 补打卡：把漏记的那节课补上。
     *
     * 同一天已经有一条「待上」的课次（自动排的或手动加的）时，直接把那条改成已上，
     * 不再新插一条——否则上课记录里同一天会出现两条，看着像重复记了。
     */
    fun backfillLesson(form: LessonFormData) {
        viewModelScope.launch {
            val course = uiState.value.course
            // 表单里换了别的课程时，当前页面只有本课程的课次，查不到那门课的同日记录，
            // 直接按新增处理，不去动别的课程的课次。
            val existing = if (form.courseId == course?.id) {
                uiState.value.lessons.firstOrNull {
                    cn.yiweiqiu.lessonledger.core.Dates.toDate(it.scheduledAt) == form.date &&
                        it.status == LessonStatus.PENDING
                }
            } else {
                null
            }
            if (existing != null) {
                repo.setLessonStatus(
                    lessonId = existing.id,
                    status = form.status,
                    deductedHalf = form.deductedHalf,
                    note = form.note.trim().takeIf { it.isNotBlank() },
                    // 实际上了 90 分钟 / 2 小时的话，把那天那条记录的时长也改过来
                    durationMinutes = form.durationMinutes,
                )
            } else {
                repo.addLesson(
                    courseId = form.courseId,
                    date = form.date,
                    time = cn.yiweiqiu.lessonledger.core.Dates.parseTimeOrNull(form.time),
                    status = form.status,
                    deductedHalf = form.deductedHalf,
                    note = form.note,
                    durationMinutes = form.durationMinutes
                        ?: courseOf(form.courseId)?.durationOn(form.date) ?: 60,
                )
            }
        }
    }

    fun deleteLesson(lessonId: String) {
        viewModelScope.launch { repo.deleteLesson(lessonId) }
    }

    fun restoreLesson(lessonId: String) {
        viewModelScope.launch { repo.restoreLesson(lessonId) }
    }

    fun saveCourse(form: CourseFormData) {
        val course = uiState.value.course ?: return
        viewModelScope.launch {
            repo.saveCourse(
                existing = course,
                childId = course.childId,
                orgName = form.orgName,
                courseName = form.courseName,
                category = form.category,
                teacher = form.teacher,
                location = form.location,
                weekdayMask = form.weekdayMask,
                startTime = form.startTime,
                startTimesJson = form.startTimesJson,
                durationMinutes = form.durationMinutes,
                durationMinutesJson = form.durationMinutesJson,
                warningThreshold = form.warningThreshold,
                remindBeforeMinutes = form.remindBeforeMinutes,
                status = form.status,
                note = form.note,
            )
        }
    }

    fun deleteCourse() {
        val course = uiState.value.course ?: return
        viewModelScope.launch { repo.deleteCourse(course.id) }
    }
}
