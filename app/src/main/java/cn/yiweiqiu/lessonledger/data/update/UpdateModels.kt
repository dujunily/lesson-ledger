package cn.yiweiqiu.lessonledger.data.update

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 坚果云 `update/latest.json` 的内容。
 *
 * 这里只是「最新版本的口供」，真正的包在同一个目录的 `app-release.apk`。
 * 版本比较只认 [versionCode]（整数，单调递增），[versionName] 只用来显示。
 */
@Serializable
data class RemoteUpdate(
    val versionCode: Int = 0,
    val versionName: String = "",
    val size: Long = 0L,
    /** APK 的 MD5（小写十六进制），下载完必须核对，防止传输截断 */
    val md5: String = "",
    /** 更新说明，纯文本，一行一条 */
    val notes: String = "",
    val publishedAt: String = "",
)

/** 一次检查的结论 */
sealed interface UpdateCheck {
    /** 还没配坚果云，无从检查 */
    data object NotConfigured : UpdateCheck

    /**
     * 云端没有比本机更新的包。
     *
     * [remote] 区分三种完全不同的处境，UI 要说人话而不是一律「已是最新版本」：
     * - null：云端连 latest.json 都没有，说明这台机器还没发布过任何版本
     * - 版本等于本机：云端就是这一版
     * - 版本低于本机：本机比云端新（刚发完新版还没传，或另一台手机回滚过）
     */
    data class UpToDate(val remote: RemoteUpdate? = null) : UpdateCheck

    data class Available(val info: RemoteUpdate) : UpdateCheck
}

/** 安装器（PackageInstaller）回调回来的结果，用这个总线交给 ViewModel 显示 */
@Singleton
class InstallResultBus @Inject constructor() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun emit(text: String?) {
        _message.value = text
    }

    fun consume() {
        _message.value = null
    }
}
