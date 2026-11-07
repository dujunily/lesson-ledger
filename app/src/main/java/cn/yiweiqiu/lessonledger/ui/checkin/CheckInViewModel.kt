package cn.yiweiqiu.lessonledger.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.Lesson
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.ui.forms.LessonFormData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    private val monthFlow = MutableStateFlow(YearMonth.now())
    private val selectedFlow = MutableStateFlow(Dates.today())

    data class DayMark(val done: Int, val pending: Int, val leave: Int, val absent: Int = 0) {
        val total: Int get() = done + pending + leave + absent
    }

    data class DayLesson(
        val lesson: Lesson,
        val course: Course,
        val child: Child?,
    )

    /** 一门课在当月的上课情况 */
    data class MonthCourseStat(
        val course: Course,
        val child: Child?,
        /** 当月扣掉的课时（半节为单位） */
        val doneHalf: Long,
        val doneCount: Int,
        val leaveCount: Int,
        val absentCount: Int,
        /** **已经过了时间**还没打卡的课次（真·逾期），以后才上的不算 */
        val pendingCount: Int,
        /** 还没到时间的待上课次：只是排着，不是欠着 */
        val upcomingCount: Int = 0,
        /** 调课 / 取消：既不算上了也不算缺，只在标签上提一句 */
        val otherCount: Int = 0,
    )

    /** 当月整体上课情况：翻月份时跟着变，一眼看到这个月上了多少 */
    data class MonthSummary(
        val doneHalf: Long = 0L,
        val doneCount: Int = 0,
        val leaveCount: Int = 0,
        val absentCount: Int = 0,
        /** 逾期未打卡（时间已经过去还没打的），未来课次不计 */
        val pendingCount: Int = 0,
        /** 未来还要上的课次 */
        val upcomingCount: Int = 0,
        val perCourse: List<MonthCourseStat> = emptyList(),
    ) {
        /** 当月有记录的课程数（包括只请了假的） */
        val courseCount: Int get() = perCourse.size
        val isEmpty: Boolean
            get() = doneCount == 0 && leaveCount == 0 && absentCount == 0 &&
                pendingCount == 0 && upcomingCount == 0
    }

    private data class CourseAccum(
        var doneHalf: Long = 0L,
        var doneCount: Int = 0,
        var leaveCount: Int = 0,
        var absentCount: Int = 0,
        var pendingCount: Int = 0,
        var upcomingCount: Int = 0,
        var otherCount: Int = 0,
    )

    data class UiState(
        val month: YearMonth = YearMonth.now(),
        val selected: LocalDate = Dates.today(),
        val today: LocalDate = Dates.today(),
        val marks: Map<LocalDate, DayMark> = emptyMap(),
        val selectedLessons: List<DayLesson> = emptyList(),
        val monthSummary: MonthSummary = MonthSummary(),
        val courses: List<Course> = emptyList(),
        val ready: Boolean = false,
    )

    val uiState = combine(repo.data, monthFlow, selectedFlow) { data, month, selected ->
        val marks = mutableMapOf<LocalDate, DayMark>()
        val courseAcc = LinkedHashMap<String, CourseAccum>()
        // 「待打卡」只在时间过了之后才算数：晚上 19:00 的课，上午就标着"待打卡"
        // 会让人以为欠了一堆；排在后面几天的课更是纯属自己吓自己。
        val now = Dates.now()
        data.lessons.forEach { lesson ->
            val date = Dates.toDate(lesson.scheduledAt)
            if (YearMonth.from(date) != month) return@forEach
            val current = marks[date] ?: DayMark(0, 0, 0, 0)
            marks[date] = when (lesson.status) {
                LessonStatus.DONE -> current.copy(done = current.done + 1)
                LessonStatus.PENDING -> current.copy(pending = current.pending + 1)
                LessonStatus.LEAVE -> current.copy(leave = current.leave + 1)
                // 缺勤也该在格子上留个痕：没去和"上了""请假"是三回事
                LessonStatus.ABSENT -> current.copy(absent = current.absent + 1)
                else -> current
            }
            // 同一趟顺便把月度汇总算了，省得再扫一遍全部课次
            val acc = courseAcc.getOrPut(lesson.courseId) { CourseAccum() }
            when (lesson.status) {
                LessonStatus.DONE -> {
                    acc.doneHalf += lesson.deductedHalf
                    acc.doneCount += 1
                }
                LessonStatus.PENDING ->
                    if (lesson.scheduledAt <= now) acc.pendingCount += 1 else acc.upcomingCount += 1
                LessonStatus.LEAVE -> acc.leaveCount += 1
                LessonStatus.ABSENT -> acc.absentCount += 1
                // 调课 / 取消既不算上了也不算缺，只记个次数，别让这行空着
                LessonStatus.RESCHEDULED, LessonStatus.CANCELLED -> acc.otherCount += 1
            }
        }

        val perCourse = courseAcc.mapNotNull { (courseId, acc) ->
            val course = data.courseById(courseId) ?: return@mapNotNull null
            MonthCourseStat(
                course = course,
                child = data.childOf(course),
                doneHalf = acc.doneHalf,
                doneCount = acc.doneCount,
                leaveCount = acc.leaveCount,
                absentCount = acc.absentCount,
                pendingCount = acc.pendingCount,
                upcomingCount = acc.upcomingCount,
                otherCount = acc.otherCount,
            )
        }.sortedWith(
            compareByDescending<MonthCourseStat> { it.doneHalf }.thenBy { it.course.courseName }
        )
        val monthSummary = MonthSummary(
            doneHalf = perCourse.sumOf { it.doneHalf },
            doneCount = perCourse.sumOf { it.doneCount },
            leaveCount = perCourse.sumOf { it.leaveCount },
            absentCount = perCourse.sumOf { it.absentCount },
            pendingCount = perCourse.sumOf { it.pendingCount },
            upcomingCount = perCourse.sumOf { it.upcomingCount },
            perCourse = perCourse,
        )

        val dayLessons = data.lessons
            .filter { Dates.toDate(it.scheduledAt) == selected }
            .sortedBy { it.scheduledAt }
            .mapNotNull { lesson ->
                val course = data.courseById(lesson.courseId) ?: return@mapNotNull null
                DayLesson(lesson, course, data.childOf(course))
            }

        UiState(
            month = month,
            selected = selected,
            today = Dates.today(),
            marks = marks,
            selectedLessons = dayLessons,
            monthSummary = monthSummary,
            courses = data.courses.filter { it.status == CourseStatus.ACTIVE },
            ready = true,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch { repo.ensureUpcomingLessons() }
    }

    fun previousMonth() {
        monthFlow.value = monthFlow.value.minusMonths(1)
    }

    fun nextMonth() {
        monthFlow.value = monthFlow.value.plusMonths(1)
    }

    fun select(date: LocalDate) {
        selectedFlow.value = date
        // 选中日期连带把月历翻到所在月份，「回到今天」时日历才不会停在别的月份
        val m = YearMonth.from(date)
        if (monthFlow.value != m) monthFlow.value = m
    }

    fun mark(lessonId: String, status: LessonStatus, deductedHalf: Long?) {
        viewModelScope.launch { repo.setLessonStatus(lessonId, status, deductedHalf) }
    }

    fun addLesson(form: LessonFormData) {
        viewModelScope.launch {
            repo.addLesson(
                courseId = form.courseId,
                date = form.date,
                time = Dates.parseTimeOrNull(form.time),
                status = form.status,
                deductedHalf = form.deductedHalf,
                note = form.note,
                // 时长优先用表单里填的（用户可能实际上了 90 分钟 / 2 小时），
                // 没填才跟那天走：这门课周末是 90 分钟的话，补周末的课就记 90
                durationMinutes = form.durationMinutes
                    ?: uiState.value.courses
                        .firstOrNull { it.id == form.courseId }?.durationOn(form.date)
                    ?: 60,
            )
        }
    }
}
