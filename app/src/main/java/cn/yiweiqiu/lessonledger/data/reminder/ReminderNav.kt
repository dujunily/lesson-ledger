package cn.yiweiqiu.lessonledger.data.reminder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知点击后的跳转信号。
 *
 * 通知是系统拉起 MainActivity 的，而导航控制器在 Compose 的 AppRoot 里，
 * 用一个进程内的信号把"要跳打卡页"这件事传过去。
 */
object ReminderNav {

    private val _openCheckIn = MutableStateFlow(0)

    /** 每次 +1，保证连续两次触发都能被观察到 */
    val openCheckIn: StateFlow<Int> = _openCheckIn.asStateFlow()

    fun requestOpenCheckIn() {
        _openCheckIn.value += 1
    }
}
