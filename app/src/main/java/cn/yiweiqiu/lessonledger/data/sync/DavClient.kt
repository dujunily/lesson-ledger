package cn.yiweiqiu.lessonledger.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DavEntry(
    val name: String,
    val isCollection: Boolean,
    val etag: String?,
    val lastModified: Long,
    val size: Long,
)

data class DavPropfind(
    val code: Int,
    val entries: List<DavEntry>,
    val quotaAvailable: Long?,
    val quotaUsed: Long?,
) {
    val ok: Boolean get() = code == 207
}

data class DavResponse(
    val code: Int,
    val body: ByteArray? = null,
    val etag: String? = null,
    val lastModified: Long = 0L,
) {
    val ok: Boolean get() = code in 200..299
}

/**
 * 坚果云 WebDAV 客户端。
 * 只用 OkHttp 手写 PROPFIND / MKCOL / PUT / GET / DELETE，不依赖 sardine。
 *
 * 踩坑约定：
 * - MKCOL 返回 405 表示"目录已存在"，必须当成功处理
 * - PUT 带 If-Match 做乐观锁，412 表示别的设备刚写过，需要重新拉取合并
 * - 507 表示空间不足
 * - 401 表示应用密码错误（不是登录密码）
 */
@Singleton
class DavClient @Inject constructor() {

    companion object {
        const val BASE_URL = cn.yiweiqiu.lessonledger.BuildConfig.DAV_BASE_URL
        const val MANIFEST = "manifest.json"
        const val DATA = "data.json"
        const val DATA_ENC = "data.enc"
        const val BACKUPS = "backups"
        const val ATTACHMENTS = "attachments"

        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:getetag/>
    <d:getlastmodified/>
    <d:getcontentlength/>
    <d:resourcetype/>
    <d:quota-available-bytes/>
    <d:quota-used-bytes/>
  </d:prop>
</d:propfind>"""
    }

    private val xmlType = "application/xml; charset=utf-8".toMediaType()
    private val binType = "application/octet-stream".toMediaType()

    private val httpDateFormats = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
    )

    fun openSession(
        email: String,
        appPassword: String,
        rootDir: String,
    ): DavSession {
        val credential = Credentials.basic(email, appPassword, Charsets.UTF_8)
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                // 预置 Basic Auth，避免多一次 401 往返
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", credential)
                        .header("User-Agent", "LessonLedger/1.0 (Android)")
                        .build()
                )
            }
            .build()
        val segments = rootDir.split('/').filter { it.isNotBlank() }
        return DavSession(client, segments)
    }

    inner class DavSession(
        private val client: OkHttpClient,
        private val rootSegments: List<String>,
    ) {

        private val base: HttpUrl = BASE_URL.toHttpUrl()

        fun collectionUrl(vararg extra: String, trailingSlash: Boolean = true): HttpUrl {
            var builder = base.newBuilder()
            rootSegments.forEach { builder = builder.addPathSegment(it) }
            extra.filter { it.isNotBlank() }.forEach { builder = builder.addPathSegment(it) }
            if (trailingSlash) builder = builder.addPathSegment("")
            return builder.build()
        }

        fun fileUrl(name: String): HttpUrl = collectionUrl(name, trailingSlash = false)

        suspend fun propfind(url: HttpUrl, depth: Int = 1): DavPropfind = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(xmlType))
                .header("Depth", depth.toString())
                .build()
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes()
                if (response.code != 207 || bytes == null) {
                    DavPropfind(response.code, emptyList(), null, null)
                } else {
                    parsePropfind(bytes)
                }
            }
        }

        suspend fun stat(url: HttpUrl): DavEntry? = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(xmlType))
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 207) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                parsePropfind(bytes).entries.firstOrNull()
            }
        }

        /** 目录已存在（405）视为成功 */
        suspend fun mkcol(url: HttpUrl): Int = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).method("MKCOL", null).build()
            client.newCall(request).execute().use { it.code }
        }

        /** 逐层 MKCOL；已存在不算失败 */
        suspend fun ensureDirectories() {
            mkcol(collectionUrl())
            mkcol(collectionUrl(BACKUPS))
            mkcol(collectionUrl(ATTACHMENTS))
        }

        suspend fun get(url: HttpUrl): ByteArray? = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 200 -> response.body?.bytes()
                    else -> null
                }
            }
        }

        suspend fun getWithCode(url: HttpUrl): Pair<Int, ByteArray?> = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                response.code to response.body?.bytes()
            }
        }

        /**
         * 流式下载到文件，边下边回调进度。
         *
         * 更新包里是 2MB 级的 APK，用 [get] 一次性读进内存虽然也能跑，
         * 但拿不到进度条，弱网下用户会以为卡死了；这里直接落盘。
         */
        suspend fun getToFile(
            url: HttpUrl,
            target: java.io.File,
            onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
        ): Int = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) return@withContext response.code
                val body = response.body ?: return@withContext 0
                val total = body.contentLength()
                target.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                        out.flush()
                    }
                }
                response.code
            }
        }

        suspend fun put(
            url: HttpUrl,
            bytes: ByteArray,
            ifMatch: String? = null,
            ifNoneMatch: Boolean = false,
        ): DavResponse = withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .put(bytes.toRequestBody(binType))
            ifMatch?.let { builder.header("If-Match", it) }
            if (ifNoneMatch) builder.header("If-None-Match", "*")
            client.newCall(builder.build()).execute().use { response ->
                DavResponse(
                    code = response.code,
                    etag = response.header("ETag")?.trim(),
                    lastModified = parseHttpDate(response.header("Last-Modified")),
                )
            }
        }

        suspend fun delete(url: HttpUrl): Int = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).delete().build()
            client.newCall(request).execute().use { it.code }
        }

        /** 测试连接：对根目录做一次 Depth:0 的 PROPFIND */
        suspend fun testConnection(): Triple<Int, Long?, Long?> = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(base)
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(xmlType))
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes()
                if (response.code != 207 || bytes == null) {
                    Triple(response.code, null, null)
                } else {
                    val parsed = parsePropfind(bytes)
                    Triple(response.code, parsed.quotaAvailable, parsed.quotaUsed)
                }
            }
        }

        private fun parsePropfind(bytes: ByteArray): DavPropfind {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), null)

            val entries = mutableListOf<DavEntry>()
            var quotaAvailable: Long? = null
            var quotaUsed: Long? = null

            var inResponse = false
            var current = ""
            var href: String? = null
            var isCollection = false
            var etag: String? = null
            var lastModified = 0L
            var size = 0L

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "response" -> {
                            inResponse = true
                            href = null
                            isCollection = false
                            etag = null
                            lastModified = 0L
                            size = 0L
                        }
                        "collection" -> isCollection = true
                        "href" -> current = "href"
                        "getetag" -> current = "etag"
                        "getlastmodified" -> current = "lastmod"
                        "getcontentlength" -> current = "size"
                        "quota-available-bytes" -> current = "quota_avail"
                        "quota-used-bytes" -> current = "quota_used"
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (current) {
                                "href" -> if (inResponse && href == null) href = text
                                "etag" -> etag = text.trim()
                                "lastmod" -> lastModified = parseHttpDate(text)
                                "size" -> size = text.toLongOrNull() ?: 0L
                                "quota_avail" -> quotaAvailable = text.toLongOrNull()
                                "quota_used" -> quotaUsed = text.toLongOrNull()
                            }
                            current = ""
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "response" && inResponse) {
                            val rawHref = href
                            if (rawHref != null) {
                                val trimmed = rawHref.trimEnd('/')
                                val name = trimmed.substringAfterLast('/').let { decode(it) }
                                entries += DavEntry(
                                    name = name,
                                    isCollection = isCollection,
                                    etag = etag,
                                    lastModified = lastModified,
                                    size = size,
                                )
                            }
                            inResponse = false
                        }
                        current = ""
                    }
                }
                event = parser.next()
            }
            return DavPropfind(207, entries, quotaAvailable, quotaUsed)
        }

        private fun decode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }

        private fun parseHttpDate(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            for (formatter in httpDateFormats) {
                try {
                    return ZonedDateTime.parse(raw, formatter).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    // try next
                }
            }
            return 0L
        }
    }
}

