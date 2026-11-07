package cn.yiweiqiu.lessonledger.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import cn.yiweiqiu.lessonledger.BuildConfig
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.data.sync.DavAuthException
import cn.yiweiqiu.lessonledger.data.sync.DavClient
import cn.yiweiqiu.lessonledger.data.sync.DavServerException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用坚果云当更新源。
 *
 * 远端布局（都在同步根目录下，和账本数据同一个账号，互不干扰）：
 * ```
 * /keshibu/update/latest.json       ← 版本号、md5、更新说明
 * /keshibu/update/app-release.apk   ← 只留最新一版，覆盖写（坚果云自己留历史版本）
 * ```
 *
 * 为什么不用应用商店：这个 App 不上架，两台手机之间能自己升级就够。
 * 为什么不用自己的服务器：坚果云是已经配好的 WebDAV，一个 PUT/GET 就完事，
 * 不需要域名、证书、对象存储和一分钱运维成本。
 *
 * 安全边界：下载完必须过三道校验才允许交给安装器——
 * 1. MD5 与 latest.json 一致（防传输截断）
 * 2. APK 里的包名是本 App（防放错文件）
 * 3. APK 的签名证书与本机已装版本完全一致（防云端被换包）
 * 三道任何一道不过，直接删包报错，不做「忽略并安装」的选项。
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dav: DavClient,
    private val settings: SettingsStore,
) {

    companion object {
        /** 远端子目录，跟着同步根目录走（默认 /keshibu/update/） */
        const val DIR = "update"
        private const val META = "latest.json"
        private const val APK = "app-release.apk"
        private const val CACHE_DIR = "update"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    fun isConfigured(): Boolean = settings.current.isConfigured

    // ------------------------------------------------------------------ 检查

    suspend fun check(): UpdateCheck {
        if (!settings.current.isConfigured) return UpdateCheck.NotConfigured
        val session = sessionOrThrow()
        val (code, bytes) = session.getWithCode(session.fileInDir(META))
        when {
            code == 401 || code == 403 -> throw DavAuthException()
            // 还没发布过任何版本，不算错误
            code == 404 -> return UpdateCheck.UpToDate(remote = null)
            bytes == null -> throw DavServerException(code)
        }
        val info = try {
            json.decodeFromString<RemoteUpdate>(bytes.decodeToString())
        } catch (e: Exception) {
            throw IllegalStateException("云端 latest.json 格式不对：${e.message}")
        }
        return if (info.versionCode > BuildConfig.VERSION_CODE) {
            UpdateCheck.Available(info)
        } else {
            UpdateCheck.UpToDate(remote = info)
        }
    }

    // ------------------------------------------------------------------ 下载

    suspend fun download(info: RemoteUpdate, onProgress: (Float) -> Unit = {}): File {
        val session = sessionOrThrow()
        val target = apkFile()
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val code = session.getToFile(session.fileInDir(APK), target) { done, total ->
            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }
        if (code == 401 || code == 403) throw DavAuthException()
        if (code != 200) throw DavServerException(code)
        if (!target.exists() || target.length() == 0L) throw IllegalStateException("下载到的文件是空的")

        // 1) MD5
        if (info.md5.isNotBlank()) {
            val actual = md5Of(target)
            if (!actual.equals(info.md5.trim(), ignoreCase = true)) {
                target.delete()
                throw IllegalStateException("下载的文件校验不通过（md5 不符），已删除，请重试")
            }
        }
        // 2) 包名 + versionCode  3) 签名
        verifyApk(target, info)

        onProgress(1f)
        return target
    }

    // ------------------------------------------------------------------ 发布

    /**
     * 把「当前正在运行的这一版」传到云端，供其它设备更新。
     *
     * 这是开发/自用动作，不是普通用户功能：只有自己两台手机之间分发，没有渠道审核。
     * 不读外部文件，直接读自己已安装的 base.apk，所以永远不会传错包。
     */
    suspend fun publishCurrent(notes: String, onProgress: (Float) -> Unit = {}): RemoteUpdate {
        val session = sessionOrThrow()
        val appInfo = context.applicationInfo
        val splits = appInfo.splitSourceDirs.orEmpty().filter { it.isNotBlank() }
        if (splits.isNotEmpty()) {
            throw IllegalStateException("这个包有 ${splits.size} 个 split，单文件分发会装不上，先打成 universal 包")
        }
        val self = File(appInfo.sourceDir)
        if (!self.exists()) throw IllegalStateException("读不到已安装的安装包：${self.path}")

        val bytes = self.readBytes()
        val info = RemoteUpdate(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            size = bytes.size.toLong(),
            md5 = md5Of(bytes),
            notes = notes.trim(),
            publishedAt = OffsetDateTime.now().toString(),
        )

        onProgress(0.1f)
        // 目录已存在时 MKCOL 回 405，按成功处理
        session.mkcol(session.collectionUrl(DIR))
        onProgress(0.2f)

        val apkResp = session.put(session.fileInDir(APK), bytes)
        if (!apkResp.ok) throw DavServerException(apkResp.code)
        onProgress(0.9f)

        val metaResp = session.put(
            session.fileInDir(META),
            json.encodeToString(RemoteUpdate.serializer(), info).toByteArray(),
        )
        if (!metaResp.ok) throw DavServerException(metaResp.code)
        onProgress(1f)
        return info
    }

    // ------------------------------------------------------------------ 内部

    private fun sessionOrThrow(): DavClient.DavSession {
        val s = settings.current
        if (!s.isConfigured) {
            throw IllegalStateException("先去「设置 → 同步」填好坚果云账号，更新才能走云端")
        }
        return dav.openSession(s.email, s.appPassword, s.rootDirNormalized)
    }

    private fun DavClient.DavSession.fileInDir(name: String): HttpUrl =
        collectionUrl(DIR, name, trailingSlash = false)

    private fun apkFile(): File = File(File(context.cacheDir, CACHE_DIR), APK)

    private fun md5Of(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun md5Of(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyApk(file: File, info: RemoteUpdate) {
        val archive = context.packageManager.getPackageArchiveInfo(file.path, signFlags())
            ?: run {
                file.delete()
                throw IllegalStateException("这个文件不是合法安装包，已删除")
            }
        if (archive.packageName != context.packageName) {
            file.delete()
            throw IllegalStateException("云端这个包的包名是 ${archive.packageName}，不是课时簿，已拒绝安装")
        }
        if (info.versionCode > 0 && archiveVersionCode(archive) != info.versionCode) {
            file.delete()
            throw IllegalStateException("包内版本号与 latest.json 不一致，已拒绝安装")
        }
        val expected = signerKeys(installedPackageInfo())
        val actual = signerKeys(archive)
        if (expected.isEmpty() || actual.isEmpty() || !actual.all { it in expected }) {
            file.delete()
            throw IllegalStateException("签名与本机版本不一致，已拒绝安装（可能云端包被替换过）")
        }
    }

    private fun installedPackageInfo(): PackageInfo? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, signFlags())
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun archiveVersionCode(info: PackageInfo): Int =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode

    /** Android 8/9 上没有 GET_SIGNING_CERTIFICATES，退回老的 signatures 字段 */
    private fun signFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    private fun signerKeys(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }
}
