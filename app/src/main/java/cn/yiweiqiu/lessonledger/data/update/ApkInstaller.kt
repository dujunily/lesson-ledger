package cn.yiweiqiu.lessonledger.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

/**
 * 用 PackageInstaller 会话装自己下载的包。
 *
 * 不走「Intent.ACTION_VIEW + FileProvider」那套，原因有三：
 * - 不需要 FileProvider 暴露 cache 目录（少一个 content provider 攻击面）
 * - Android 14 起对 file:// 和 targetSdk 34+ 的隐式授权限制越来越紧
 * - 会话 API 能拿到明确的安装结果回调（成功/取消/失败原因）
 */
object ApkInstaller {

    const val ACTION_INSTALL_RESULT = "cn.yiweiqiu.lessonledger.action.INSTALL_RESULT"
    const val EXTRA_SESSION = "session_id"

    /** 用户是否已允许本 App「安装未知应用」 */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * 跳「安装未知应用」授权页。
     * 小米等 ROM 上这个 Intent 有可能被改成自家页面，所以失败要能兜到应用详情页。
     */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0L, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setAction(ACTION_INSTALL_RESULT)
                .putExtra(EXTRA_SESSION, sessionId)
            // Android 12+ 要求显式声明可变性：系统要往这个 Intent 里塞 EXTRA_INTENT
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}

/**
 * 安装结果回执。
 *
 * STATUS_PENDING_USER_ACTION 是正常路径：系统弹「是否安装」确认框，
 * 这里必须把 EXTRA_INTENT 起起来，否则会表现为「点了安装没反应」。
 */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var bus: InstallResultBus

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_INSTALL_RESULT) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm == null) return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            // 装成功后进程会被替换，这条多半显示不出来，但保留以便降级场景
            PackageInstaller.STATUS_SUCCESS -> bus.emit("已安装，正在重启…")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                bus.emit(
                    when (status) {
                        PackageInstaller.STATUS_FAILURE_ABORTED -> "安装被取消"
                        PackageInstaller.STATUS_FAILURE_BLOCKED -> "系统拦截了这次安装：${message.orEmpty()}"
                        PackageInstaller.STATUS_FAILURE_CONFLICT -> "签名冲突：这台手机上装的是另一个签名的课时簿"
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "与当前系统不兼容：${message.orEmpty()}"
                        PackageInstaller.STATUS_FAILURE_INVALID -> "安装包损坏：${message.orEmpty()}"
                        PackageInstaller.STATUS_FAILURE_STORAGE -> "手机存储空间不足"
                        else -> "安装失败：${message.orEmpty()}"
                    },
                )
            }
        }
    }
}
