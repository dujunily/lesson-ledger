package cn.yiweiqiu.lessonledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.domain.Category
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.StatsCalc
import cn.yiweiqiu.lessonledger.domain.StatsPeriod
import cn.yiweiqiu.lessonledger.domain.StatsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

/**
 * 统计数字的钻取目标：点了看板上的某个数字/柱子/行，
 * 就带这些条件打开「缴费明细」页。null 的维度表示不限制。
 */
data class StatsDrill(
    val period: StatsPeriod? = null,
    val childId: String? = null,
    val category: Category? = null,
    val org: String? = null,
    val month: YearMonth? = null,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    // 默认「全部」：家长打开统计先看总账，想看近况再切「本月」
    private val periodFlow = MutableStateFlow(StatsPeriod.ALL)
    private val childFlow = MutableStateFlow<String?>(null)

    data class UiState(
        val period: StatsPeriod = StatsPeriod.ALL,
        val children: List<Child> = emptyList(),
        val selectedChildId: String? = null,
        val result: StatsResult? = null,
    )

    val uiState = combine(repo.data, periodFlow, childFlow) { data, period, childId ->
        UiState(
            period = period,
            children = data.children,
            selectedChildId = childId,
            result = StatsCalc.compute(data, period, childId),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setPeriod(period: StatsPeriod) {
        periodFlow.value = period
    }

    fun setChild(childId: String?) {
        childFlow.value = childId
    }

    suspend fun buildCsv(): String {
        val data = repo.data.first()
        return StatsCalc.buildCsv(data, periodFlow.value, childFlow.value)
    }
}
