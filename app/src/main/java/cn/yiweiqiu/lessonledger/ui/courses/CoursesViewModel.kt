package cn.yiweiqiu.lessonledger.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.core.ConflictMark
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseDraft
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.PaymentDraft
import cn.yiweiqiu.lessonledger.ui.forms.CourseFormData
import cn.yiweiqiu.lessonledger.ui.forms.CourseOption
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

/**
 * 「课程」tab：孩子 → 课程列表 + 课时概览。
 * 只有一个孩子时不显示孩子切换，直接铺开课程的卡片。
 */
@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val selectedChildFlow = MutableStateFlow<String?>(null)

    data class CourseRow(
        val course: Course,
        val stats: LedgerCalc.CourseStats,
        val child: Child?,
    )

    data class UiState(
        val children: List<Child> = emptyList(),
        val selectedChildId: String? = null,
        /** 还有剩余课时、最近还在上的课 */
        val open: List<CourseRow> = emptyList(),
        /** 已结束 / 已退费 / 课时用完 */
        val closed: List<CourseRow> = emptyList(),
        val activeCount: Int = 0,
        val pendingCents: Long = 0L,
        val remainingHalf: Long = 0L,
        val monthSpendCents: Long = 0L,
        val totalPaidCents: Long = 0L,
        val defaultThreshold: Int = 5,
        val defaultRemindBefore: Int = 30,
        /** 缴费表单里"已经在记的课"，点一下就是续费 */
        val options: List<CourseOption> = emptyList(),
        /** 课程 id → 这是同一门课的第几期 */
        val periods: Map<String, Int> = emptyMap(),
        val ready: Boolean = false,
    )

    val uiState = combine(repo.data, settings.state, selectedChildFlow) { data, setting, selectedId ->
        val stats = LedgerCalc.statsForAll(data)
        val scoped = data.courses.filter { selectedId == null || it.childId == selectedId }
        val rows = scoped.map { course ->
            CourseRow(
                course = course,
                stats = stats[course.id] ?: LedgerCalc.computeStats(course, emptyList(), emptyList()),
                child = data.childById(course.childId),
            )
        }
        // 「未结课」= 还有剩余课时且没退费；已结束但没上完的也算（钱还压在里面）
        val open = rows
            .filter {
                it.stats.remainingHalf > 0 &&
                    it.course.status != CourseStatus.REFUNDED &&
                    it.course.status != CourseStatus.FINISHED
            }
            .sortedWith(
                compareBy(
                    { it.stats.nextLessonAt ?: Long.MAX_VALUE },
                    { -it.course.updatedAt },
                ),
            )
        val closed = rows
            .filterNot { r -> open.any { it.course.id == r.course.id } }
            .sortedByDescending { it.course.updatedAt }

        val summary = cn.yiweiqiu.lessonledger.domain.StatsCalc.homeSummary(data, stats)

        // 缴费表单的候选课程：送全部孩子的（表单里可以切孩子），按最近动过的排前面。
        // 最近一笔正数缴费拿来预填续费金额和课时 —— 大多数时候一期和上一期是一样的。
        val lastPaid = data.payments
            .filter { !it.deleted && it.amountCents > 0 && it.totalHalf > 0 }
            .groupBy { it.courseId }
            .mapValues { (_, list) ->
                list.maxWithOrNull(compareBy({ it.payDate }, { it.createdAt }))
            }
        val options = data.courses
            .sortedByDescending { it.updatedAt }
            .map { course ->
                CourseOption(
                    course = course,
                    stats = stats[course.id] ?: LedgerCalc.computeStats(course, emptyList(), emptyList()),
                    period = data.coursePeriod(course),
                    lastAmountCents = lastPaid[course.id]?.amountCents ?: 0L,
                    lastLessonsHalf = lastPaid[course.id]?.lessonsBoughtHalf ?: 0L,
                )
            }

        UiState(
            children = data.children,
            selectedChildId = selectedId,
            open = open,
            closed = closed,
            // 「在读」不含同步冲突副本：副本挂着同样的课程名，混进来数字就对了不上
            activeCount = scoped.count { it.status == CourseStatus.ACTIVE && !ConflictMark.isCopy(it.note) },
            pendingCents = rows.sumOf { it.stats.pendingValueCents },
            remainingHalf = rows.sumOf { it.stats.remainingHalf },
            monthSpendCents = summary.monthSpendCents,
            // 和「我的」页同一口径：只算交出去的钱，退费不减
            totalPaidCents = rows.sumOf { it.stats.grossPaidCents },
            defaultThreshold = setting.defaultThreshold,
            defaultRemindBefore = setting.defaultRemindBeforeMinutes,
            options = options,
            // 只在真的分过期时才记期次：一门从头到尾就一期的课，
            // 标个「第 1 期」纯属噪音
            periods = rows.mapNotNull { row ->
                val chain = data.courseChain(row.course)
                if (chain.size > 1) row.course.id to chain.size else null
            }.toMap(),
            ready = true,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun selectChild(childId: String?) {
        if (selectedChildFlow.value != childId) selectedChildFlow.value = childId
    }

    fun saveCourse(existing: Course?, form: CourseFormData, childId: String) {
        viewModelScope.launch {
            repo.saveCourse(
                existing = existing,
                childId = existing?.childId ?: childId,
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

    fun deleteCourse(courseId: String) {
        viewModelScope.launch { repo.deleteCourse(courseId) }
    }

    /** 缴费即录课：课程和首笔缴费一次落地 */
    fun enroll(childId: String, course: CourseDraft, pay: PaymentDraft) {
        viewModelScope.launch { repo.enroll(childId, course, pay) }
    }

    /** 续费：[startNewPeriod] 为 true 时旧课结束、新一期建出来并串上期次链 */
    fun renew(target: Course, startNewPeriod: Boolean, course: CourseDraft, pay: PaymentDraft) {
        viewModelScope.launch { repo.renew(target, startNewPeriod, course, pay) }
    }

    fun saveChild(
        name: String,
        nickname: String,
        birthday: LocalDate?,
        avatarColor: String,
        note: String,
    ) {
        viewModelScope.launch {
            repo.saveChild(null, name, nickname, birthday, avatarColor, note)
        }
    }
}
