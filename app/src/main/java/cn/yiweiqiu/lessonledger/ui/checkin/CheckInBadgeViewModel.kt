package cn.yiweiqiu.lessonledger.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 底栏「打卡」入口的角标数据源。
 *
 * 单独拎一个轻量 ViewModel，是为了让底栏能拿到「今天还有几节没打卡」，
 * 又不必把整个 HomeViewModel 在 NavHost 外面再建一份。
 * 挂在 Activity 作用域上，全 App 只有一份，数据随仓库变更自动刷新。
 */
@HiltViewModel
class CheckInBadgeViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    /** 今天还剩几节没打卡（已上/请假/缺勤的不算） */
    val pendingToday: StateFlow<Int> = repo.data
        .map { data ->
            val today = Dates.today()
            data.lessons.count {
                it.status == LessonStatus.PENDING && Dates.toDate(it.scheduledAt) == today
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
