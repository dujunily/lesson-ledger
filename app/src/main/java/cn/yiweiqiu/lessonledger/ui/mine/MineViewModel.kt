package cn.yiweiqiu.lessonledger.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.core.ConflictMark
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.DataSource
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 「我的」= 家庭视角页：每个孩子一张卡（剩余课时 / 待消耗 / 累计缴费），
 * 设置类入口全部收进右上角齿轮。
 */
@HiltViewModel
class MineViewModel @Inject constructor(
    repo: LedgerRepository,
    settings: SettingsStore,
) : ViewModel() {

    data class ChildCard(
        val childId: String,
        val name: String,
        val age: Int?,
        val colorHex: String,
        /** 在读课程数 */
        val activeCourses: Int,
        /** 剩余课时（半节），÷2 得节数 */
        val remainingHalf: Long,
        /** 待消耗金额（分） */
        val pendingCents: Long,
        /** 累计缴费（分）：所有课程真实交过的钱（含定金），退费单算，不含冲突副本的重复记录 */
        val paidCents: Long,
        /** 有课程已超扣课时 */
        val overdrawn: Boolean,
    )

    data class UiState(
        val children: List<ChildCard> = emptyList(),
        val activeCourseCount: Int = 0,
        val lastSyncAt: Long = 0L,
        val configured: Boolean = false,
        /** 当前在看哪一套：演示模式下要在页面上说清楚，免得把示例账当真账 */
        val source: DataSource = DataSource.MINE,
    )

    val uiState = combine(repo.data, settings.state) { data, setting ->
        val stats = LedgerCalc.statsForAll(data)
        val cards = data.children.filter { !it.deleted }.map { child ->
            // 冲突副本不是真课程（正主还在），排除掉，在读数和剩余课时才不会虚高
            val active = data.coursesOf(child.id)
                .filter { !it.deleted && it.status == CourseStatus.ACTIVE && !ConflictMark.isCopy(it.note) }
            val cs = active.mapNotNull { stats[it.id] }
            // 累计缴费看的是「真实交了多少钱」：孩子名下所有课程的实缴金额都算进来，
            // 已结束 / 已退费的课程也不例外——那笔钱是真金白银交过的；
            // 冲突副本的缴费记录是同一笔钱的重复拷贝，排除掉
            val childCourseIds = data.courses
                .filter { it.childId == child.id && !ConflictMark.isCopy(it.note) }
                .map { it.id }
                .toSet()
            val grossPaid = data.payments
                .filter { !it.deleted && !ConflictMark.isCopy(it.note) && it.amountCents > 0 }
                .filter { it.courseId in childCourseIds }
                .sumOf { it.amountCents }
            ChildCard(
                childId = child.id,
                name = child.displayName,
                age = child.age,
                colorHex = child.avatarColor,
                activeCourses = active.size,
                remainingHalf = cs.sumOf { it.remainingHalf },
                pendingCents = cs.sumOf { it.pendingValueCents },
                paidCents = grossPaid,
                overdrawn = cs.any { it.isOverdrawn },
            )
        }
        UiState(
            children = cards,
            activeCourseCount = data.courses.count {
                !it.deleted && it.status == CourseStatus.ACTIVE && !ConflictMark.isCopy(it.note)
            },
            lastSyncAt = setting.lastSyncAt,
            configured = setting.isConfigured,
            source = setting.effectiveSource,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}
