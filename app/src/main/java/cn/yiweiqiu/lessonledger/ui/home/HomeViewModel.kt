package cn.yiweiqiu.lessonledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.data.sync.SyncPhase
import cn.yiweiqiu.lessonledger.data.sync.SyncScheduler
import cn.yiweiqiu.lessonledger.data.sync.SyncStatus
import cn.yiweiqiu.lessonledger.data.sync.SyncStatusHolder
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.domain.LedgerData
import cn.yiweiqiu.lessonledger.domain.StatsCalc
import cn.yiweiqiu.lessonledger.ui.forms.LessonFormData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val settings: SettingsStore,
    private val syncStatus: SyncStatusHolder,
    private val scheduler: SyncScheduler,
) : ViewModel() {

    /** 「今天」页的一行课次（可以直接打卡） */
    data class TodayRow(
        val lessonId: String,
        val time: String,
        val courseName: String,
        val childName: String,
        val status: LessonStatus,
    )

    /** 「最近记录 / 接下来」的一行：带日期标签的课次 */
    data class AgendaRow(
        val lessonId: String,
        val courseId: String,
        val scheduledAt: Long,
        val dayLabel: String,
        val time: String,
        val courseName: String,
        val childName: String,
        val status: LessonStatus,
    )

    data class UiState(
        val data: LedgerData = LedgerData(),
        val stats: Map<String, LedgerCalc.CourseStats> = emptyMap(),
        val reminders: List<LedgerCalc.Reminder> = emptyList(),
        val summary: StatsCalc.HomeSummary = StatsCalc.HomeSummary(0, 0, 0, 0),
        val status: SyncStatus = SyncStatus(),
        val configured: Boolean = false,
        val refreshing: Boolean = false,
        val ready: Boolean = false,
        /** 今天排了几节课、还有几节没打卡 */
        val todayCount: Int = 0,
        val todayPending: Int = 0,
        /** 今天每一节课（打卡按钮直接挂在行上） */
        val todayRows: List<TodayRow> = emptyList(),
        /** 本周（周一至今周日）共几节课、已打卡几节、还有几节没打卡 */
        val weekCount: Int = 0,
        val weekDone: Int = 0,
        val weekPending: Int = 0,
        /** 请假 / 调课 / 缺勤：这三类一旦记下就算这节课了结了，不该再挂着「没上完」 */
        val weekLeave: Int = 0,
        val weekRescheduled: Int = 0,
        val weekAbsent: Int = 0,
        /** 最近 14 天（含今天）的课次，倒序，补打卡时一眼能看到之前记到哪儿了 */
        val recentRows: List<AgendaRow> = emptyList(),
        /** 接下来 7 天要上的课（不含今天），今天没课时页面也不空 */
        val upcomingRows: List<AgendaRow> = emptyList(),
        /** 补打卡表单的课程选项 */
        val activeCourses: List<Course> = emptyList(),
    ) {
        /** 本周已了结的节数：上完的、请假的、调走的、缺勤的都算这周的事已经处理完 */
        val weekSettled: Int get() = weekDone + weekLeave + weekRescheduled + weekAbsent
    }

    val uiState = combine(
        repo.data,
        settings.state,
        syncStatus.state,
    ) { data, setting, status ->
        val stats = LedgerCalc.statsForAll(data)
        val today = Dates.today()
        val todayLessons = data.lessons.filter {
            it.status != LessonStatus.CANCELLED && Dates.toDate(it.scheduledAt) == today
        }
        // 本周：周一为一周之始，把这一周里还没打卡（待上）的课次算出来
        val weekStart = today.minusDays(Dates.weekdayOf(today).toLong())
        val weekEnd = weekStart.plusDays(6)
        val weekLessons = data.lessons.filter {
            if (it.status == LessonStatus.CANCELLED) return@filter false
            val date = Dates.toDate(it.scheduledAt)
            !date.isBefore(weekStart) && !date.isAfter(weekEnd)
        }
        val courseById = data.courses.associateBy { it.id }
        val childById = data.children.associateBy { it.id }
        val todayRows = todayLessons
            .sortedBy { it.scheduledAt }
            .mapNotNull { lesson ->
                val course = courseById[lesson.courseId] ?: return@mapNotNull null
                TodayRow(
                    lessonId = lesson.id,
                    time = Dates.formatTime(lesson.scheduledAt),
                    courseName = course.title,
                    childName = childById[course.childId]?.displayName.orEmpty(),
                    status = lesson.status,
                )
            }
        // 最近 14 天：把这段时间的课次倒序摊开，用户一眼能看到之前记到哪儿了，
        // 漏掉的课也能顺着时间找回去补。今天的课已经在上面那张卡里，这里不重复。
        val windowStart = today.minusDays(14)
        val yesterday = today.minusDays(1)
        val recentRows = data.lessons
            .filter {
                if (it.status == LessonStatus.CANCELLED) return@filter false
                val date = Dates.toDate(it.scheduledAt)
                !date.isBefore(windowStart) && !date.isAfter(yesterday)
            }
            .sortedByDescending { it.scheduledAt }
            .take(20)
            .mapNotNull { lesson ->
                val course = courseById[lesson.courseId] ?: return@mapNotNull null
                val date = Dates.toDate(lesson.scheduledAt)
                AgendaRow(
                    lessonId = lesson.id,
                    courseId = course.id,
                    scheduledAt = lesson.scheduledAt,
                    // 只写「10-31」看不出是星期几，补打卡时还得翻日历，所以日期后面带上星期
                    dayLabel = when (date) {
                        yesterday -> "昨天 ${Dates.weekdayNames[Dates.weekdayOf(date)]}"
                        else -> "${Dates.formatMonthDay(lesson.scheduledAt)} ${Dates.weekdayNames[Dates.weekdayOf(date)]}"
                    },
                    time = Dates.formatTime(lesson.scheduledAt),
                    courseName = course.title,
                    childName = childById[course.childId]?.displayName.orEmpty(),
                    status = lesson.status,
                )
            }
        // 接下来 7 天：今天没排课时，这一块能撑住页面，也方便提前知道明天要送孩子去哪
        val upcomingRows = data.lessons
            .filter {
                if (it.status == LessonStatus.CANCELLED) return@filter false
                val date = Dates.toDate(it.scheduledAt)
                date.isAfter(today) && !date.isAfter(today.plusDays(7))
            }
            .sortedBy { it.scheduledAt }
            .take(6)
            .mapNotNull { lesson ->
                val course = courseById[lesson.courseId] ?: return@mapNotNull null
                val date = Dates.toDate(lesson.scheduledAt)
                AgendaRow(
                    lessonId = lesson.id,
                    courseId = course.id,
                    scheduledAt = lesson.scheduledAt,
                    dayLabel = when (date) {
                        today.plusDays(1) -> "明天 ${Dates.weekdayNames[Dates.weekdayOf(date)]}"
                        today.plusDays(2) -> "后天 ${Dates.weekdayNames[Dates.weekdayOf(date)]}"
                        else -> "${Dates.formatMonthDay(lesson.scheduledAt)} ${Dates.weekdayNames[Dates.weekdayOf(date)]}"
                    },
                    time = Dates.formatTime(lesson.scheduledAt),
                    courseName = course.title,
                    childName = childById[course.childId]?.displayName.orEmpty(),
                    status = lesson.status,
                )
            }
        UiState(
            data = data,
            stats = stats,
            reminders = LedgerCalc.buildReminders(data, stats),
            summary = StatsCalc.homeSummary(data, stats),
            status = status,
            configured = setting.isConfigured,
            refreshing = status.phase == SyncPhase.SYNCING,
            ready = true,
            todayCount = todayLessons.size,
            todayPending = todayLessons.count { it.status == LessonStatus.PENDING },
            todayRows = todayRows,
            weekCount = weekLessons.size,
            weekDone = weekLessons.count { it.status == LessonStatus.DONE },
            weekPending = weekLessons.count { it.status == LessonStatus.PENDING },
            weekLeave = weekLessons.count { it.status == LessonStatus.LEAVE },
            weekRescheduled = weekLessons.count { it.status == LessonStatus.RESCHEDULED },
            weekAbsent = weekLessons.count { it.status == LessonStatus.ABSENT },
            recentRows = recentRows,
            upcomingRows = upcomingRows,
            activeCourses = data.courses.filter { it.status == CourseStatus.ACTIVE },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            // 绝不自动灌示例数据：换新手机时云端数据还没拉下来，
            // 先塞示例会和真实记录混在一起。演示数据只在「设置 → 演示数据」里由用户主动开。
            repo.ensureUpcomingLessons()
            if (settings.current.isConfigured) scheduler.syncNow("startup")
        }
    }

    /** 「今天」页直接打卡：扣多少节按这节课的实际时长折算，不再一律 1 节 */
    fun markDone(lessonId: String) {
        viewModelScope.launch {
            repo.setLessonStatus(lessonId, LessonStatus.DONE, deductedHalf = deductedHalfFor(lessonId))
        }
    }

    /**
     * 这一节要扣多少半天数：拿课次自己的时长除以课程的单节时长（90 分钟 / 基准 60 → 3 个半天数）。
     * 课程被删掉、或者数据还没刷出来时找不到对应关系，回落到 1 节（2），别扣成 0。
     */
    private fun deductedHalfFor(lessonId: String): Long {
        val data = uiState.value.data
        val lesson = data.lessons.firstOrNull { it.id == lessonId } ?: return 2L
        val course = data.courses.firstOrNull { it.id == lesson.courseId } ?: return 2L
        return LedgerCalc.deductedFromDuration(lesson.durationMinutes, course.durationMinutes)
    }

    /** 今天页里直接补记一节以前的课，不用先跳到打卡页 */
    fun addLesson(form: LessonFormData) {
        viewModelScope.launch {
            repo.addLesson(
                courseId = form.courseId,
                date = form.date,
                time = Dates.parseTimeOrNull(form.time),
                status = form.status,
                deductedHalf = form.deductedHalf,
                note = form.note,
                // 时长优先用表单里填的（实际上了 90 分钟 / 2 小时），没填才按那天所属星期几取
                durationMinutes = form.durationMinutes
                    ?: uiState.value.activeCourses
                        .firstOrNull { it.id == form.courseId }?.durationOn(form.date)
                    ?: 60,
            )
        }
    }

    fun refresh() = scheduler.syncNow("pull-refresh")
}
