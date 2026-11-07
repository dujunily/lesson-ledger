package cn.yiweiqiu.lessonledger.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.LedgerData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ChildrenViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    data class ChildRow(
        val child: Child,
        val activeCourses: Int,
        val totalPaidCents: Long,
        val pendingCents: Long,
    )

    data class UiState(
        val rows: List<ChildRow> = emptyList(),
        val data: LedgerData = LedgerData(),
        val ready: Boolean = false,
    )

    val uiState = repo.data
        .map { data ->
            val stats = cn.yiweiqiu.lessonledger.domain.LedgerCalc.statsForAll(data)
            UiState(
                rows = data.children.map { child ->
                    val courses = data.coursesOf(child.id)
                    val paid = data.payments
                        .filter { p -> courses.any { it.id == p.courseId } && p.amountCents > 0 }
                        .sumOf { it.amountCents }
                    ChildRow(
                        child = child,
                        activeCourses = courses.count { it.status == CourseStatus.ACTIVE },
                        totalPaidCents = paid,
                        pendingCents = courses.sumOf { stats[it.id]?.pendingValueCents ?: 0L },
                    )
                },
                data = data,
                ready = true,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun saveChild(
        existing: Child?,
        name: String,
        nickname: String,
        birthday: LocalDate?,
        avatarColor: String,
        note: String,
    ) {
        viewModelScope.launch {
            repo.saveChild(existing, name, nickname, birthday, avatarColor, note)
        }
    }

    fun deleteChild(childId: String) {
        viewModelScope.launch { repo.deleteChild(childId) }
    }
}
