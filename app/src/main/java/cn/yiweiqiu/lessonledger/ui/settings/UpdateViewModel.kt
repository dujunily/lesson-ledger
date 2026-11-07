package cn.yiweiqiu.lessonledger.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.data.update.ApkInstaller
import cn.yiweiqiu.lessonledger.data.update.InstallResultBus
import cn.yiweiqiu.lessonledger.data.update.RemoteUpdate
import cn.yiweiqiu.lessonledger.data.update.UpdateCheck
import cn.yiweiqiu.lessonledger.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 「关于」页里的更新模块。
 *
 * 一条链路：检查 → 下载（带进度）→ 校验 → 交给系统安装器 → 用户点「安装」。
 * 任何一步失败都只把原因写进 state，不改动已装版本，也不会自动降级。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: UpdateRepository,
    private val bus: InstallResultBus,
) : ViewModel() {

    data class UiState(
        val versionName: String = "",
        val configured: Boolean = false,
        val checking: Boolean = false,
        val check: UpdateCheck? = null,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val publishing: Boolean = false,
        val canInstall: Boolean = true,
        val message: String? = null,
        val isError: Boolean = false,
    )

    private val _state = MutableStateFlow(
        UiState(
            versionName = repo.currentVersionName,
            configured = repo.isConfigured(),
            canInstall = ApkInstaller.canInstall(context),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // 进「关于」页就静默查一次：配置了坚果云才会真发请求，没配就是本地判断。
        viewModelScope.launch {
            bus.message.collect { text -> if (text != null) setMessage(text, isError = false) }
        }
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (_state.value.checking || _state.value.downloading) return
        if (!repo.isConfigured()) {
            _state.update {
                it.copy(configured = false, check = UpdateCheck.NotConfigured, checking = false)
            }
            return
        }
        // silent 的那次是「发布完顺手回查」，不能把刚发布的成功提示冲掉
        _state.update { it.copy(configured = true, checking = true, message = if (silent) it.message else null) }
        viewModelScope.launch {
            runCatching { repo.check() }
                .onSuccess { result ->
                    _state.update { it.copy(checking = false, check = result) }
                    if (!silent) {
                        setMessage(
                            when (result) {
                                is UpdateCheck.Available -> "发现新版本 ${result.info.versionName}"
                                is UpdateCheck.UpToDate -> describeUpToDate(result)
                                UpdateCheck.NotConfigured -> "还没配置坚果云"
                            },
                            isError = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(checking = false) }
                    setMessage(e.message ?: "检查更新失败", isError = true)
                }
        }
    }

    fun downloadAndInstall() {
        val info = (_state.value.check as? UpdateCheck.Available)?.info ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, progress = 0f, message = null) }
        viewModelScope.launch {
            runCatching {
                val file = repo.download(info) { p ->
                    _state.update { it.copy(progress = p) }
                }
                withContext(Dispatchers.IO) { ApkInstaller.install(context, file) }
            }.onSuccess {
                _state.update { it.copy(downloading = false, progress = 1f) }
                setMessage("下载完成，请在系统弹窗里点「安装」", isError = false)
            }.onFailure { e ->
                _state.update { it.copy(downloading = false, progress = 0f) }
                setMessage(e.message ?: "下载失败", isError = true)
            }
        }
    }

    /** 把「当前这一版」发布到云端，供另一台手机更新 */
    fun publish(notes: String) {
        if (_state.value.publishing) return
        if (!repo.isConfigured()) {
            setMessage("先配置坚果云同步，才能把安装包传到云端", isError = true)
            return
        }
        _state.update { it.copy(publishing = true, message = null) }
        viewModelScope.launch {
            runCatching { repo.publishCurrent(notes) }
                .onSuccess { info ->
                    _state.update { it.copy(publishing = false) }
                    setMessage("已发布 v${info.versionName}（${info.size / 1024} KB），其它手机可以更新了", isError = false)
                    check(silent = true)
                }
                .onFailure { e ->
                    _state.update { it.copy(publishing = false) }
                    setMessage(e.message ?: "发布失败", isError = true)
                }
        }
    }

    /** 没有「安装未知应用」权限时，返回要跳的设置页 */
    fun installPermissionIntent(): Intent {
        _state.update { it.copy(canInstall = ApkInstaller.canInstall(context)) }
        return ApkInstaller.permissionIntent(context)
    }

    /** 从系统设置返回时刷新一次权限状态 */
    fun refreshPermission() {
        _state.update { it.copy(canInstall = ApkInstaller.canInstall(context), configured = repo.isConfigured()) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null, isError = false) }
        bus.consume()
    }

    private fun setMessage(text: String, isError: Boolean) {
        _state.update { it.copy(message = text, isError = isError) }
    }

    /** 「没有更新」这句话要说明白：是云端没发过，还是云端就是这一版，还是本机更靠前 */
    private fun describeUpToDate(result: UpdateCheck.UpToDate): String {
        val remote = result.remote ?: return "云端还没有发布过版本，去下面「分发」里发一版"
        return if (remote.versionCode == repo.currentVersionCode) {
            "已是最新版本（云端也是 v${remote.versionName}）"
        } else {
            "本机 v${repo.currentVersionName} 比云端的 v${remote.versionName} 新，可以发布覆盖"
        }
    }
}
