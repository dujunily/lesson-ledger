package cn.yiweiqiu.lessonledger.data.sync

import android.content.Context
import cn.yiweiqiu.lessonledger.core.ConflictMark
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Receipts
import cn.yiweiqiu.lessonledger.data.local.AppDatabase
import cn.yiweiqiu.lessonledger.data.local.entity.ChildEntity
import cn.yiweiqiu.lessonledger.data.local.entity.CourseEntity
import cn.yiweiqiu.lessonledger.data.local.entity.LessonEntity
import cn.yiweiqiu.lessonledger.data.local.entity.PaymentEntity
import cn.yiweiqiu.lessonledger.data.local.entity.SyncLogEntity
import cn.yiweiqiu.lessonledger.data.local.entity.SyncRecord
import cn.yiweiqiu.lessonledger.data.settings.AppSettings
import cn.yiweiqiu.lessonledger.data.settings.SettingsStore
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

class DavAuthException : Exception("认证失败：请使用坚果云「应用密码」而不是登录密码")
class DavPreconditionException : Exception("远端已被其他设备修改（412）")
class DavSpaceException : Exception("坚果云空间不足，请清理（507）")
class DavServerException(val code: Int) : Exception("坚果云服务异常（$code）")
class DavUpgradeException(val remoteVersion: Int) : Exception("远端数据版本 $remoteVersion 高于本机")
class DavDecryptException(message: String) : Exception(message)

/**
 * 同步引擎：本地优先 + 记录级双向合并 + 乐观锁。
 * 任何时刻只允许一次同步在跑（Mutex）。
 */
@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val dav: DavClient,
    private val status: SyncStatusHolder,
) {

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val CONFLICT_WINDOW_MS = 5_000L
        const val MAX_BACKUPS = 10
    }

    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    /**
     * @param quiet 后台轮询专用：不把状态改成「同步中…」、不写「开始同步 / 跳过」这类空转日志。
     *              失败仍然会亮出来 —— 离线、限流、认证失败这些是该让用户知道的。
     */
    suspend fun sync(trigger: String, quiet: Boolean = false): SyncOutcome {
        val cfg = settings.current
        if (!cfg.isConfigured) {
            status.update { it.copy(phase = SyncPhase.NOT_CONFIGURED, message = "未配置坚果云账号") }
            return SyncOutcome.NotConfigured
        }

        return mutex.withLock {
            if (!quiet) {
                status.update { it.copy(phase = SyncPhase.SYNCING, message = "同步中…") }
                log("INFO", "开始同步（$trigger）")
            }

            var outcome: SyncOutcome? = null
            var attempt = 0

            while (outcome == null) {
                attempt++
                val s = settings.current
                try {
                    outcome = attemptSync(s, trigger, quiet)
                } catch (e: CancellationException) {
                    status.update { it.copy(phase = SyncPhase.OFFLINE, message = "同步已取消") }
                    throw e
                } catch (e: DavAuthException) {
                    log("ERROR", "401 认证失败：应用密码错误")
                    outcome = fail(SyncPhase.AUTH_FAILED, "认证失败，请检查坚果云应用密码", false, s.lastSyncAt)
                } catch (e: DavPreconditionException) {
                    if (attempt >= MAX_ATTEMPTS) {
                        log("ERROR", "412 连续 $attempt 次，本轮放弃")
                        // 这里说的是「写入被别的设备抢先」，不是账本里出现了两份打架的数据。
                        // 之前挂在 CONFLICT 上，用户看到「同步冲突」会以为数据坏了——其实一条冲突都没有。
                        val why = "云端写入被其他设备抢先（412），已重试 $attempt 次。账本本身没有冲突，稍后自动再试。"
                        status.update {
                            it.copy(phase = SyncPhase.SERVER_ERROR, message = why)
                        }
                        outcome = SyncOutcome.Failure(SyncPhase.SERVER_ERROR, why, true)
                    } else {
                        log("WARN", "412 冲突，重新拉取远端（第 $attempt 次重试）")
                        delay(400L * attempt)
                    }
                } catch (e: DavSpaceException) {
                    log("ERROR", "507 空间不足")
                    outcome = fail(SyncPhase.SPACE_FULL, "坚果云空间不足，请清理", false, s.lastSyncAt)
                } catch (e: DavUpgradeException) {
                    log("ERROR", "远端 schemaVersion=${e.remoteVersion} 高于本机")
                    outcome = fail(SyncPhase.UPGRADE_REQUIRED, "远端数据由更新版本写入，请升级 App", false, s.lastSyncAt)
                } catch (e: DavDecryptException) {
                    log("ERROR", "备份解密失败")
                    outcome = fail(
                        SyncPhase.SERVER_ERROR,
                        e.message ?: "备份口令不正确",
                        false,
                        s.lastSyncAt,
                    )
                } catch (e: DavServerException) {
                    log("ERROR", "服务端异常 ${e.code}")
                    // 503 基本是限流（同一账号短时间内请求太多），不是数据有问题；
                    // 说清楚「几分钟后自动再试」，用户就不会以为账本坏了又去反复点同步。
                    val why = when (e.code) {
                        503 -> "坚果云临时限流（503）：请求太密集，几分钟后自动再试。这期间照常记在本机。"
                        502, 504 -> "坚果云网关超时（${e.code}），稍后自动再试。"
                        else -> "坚果云服务暂时不可用（${e.code}）"
                    }
                    outcome = fail(SyncPhase.SERVER_ERROR, why, true, s.lastSyncAt)
                } catch (e: IOException) {
                    log("WARN", "网络异常：${e.javaClass.simpleName}")
                    outcome = fail(SyncPhase.OFFLINE, "网络不可用，稍后自动重试", true, s.lastSyncAt)
                } catch (e: Exception) {
                    log("ERROR", "同步失败：${e.javaClass.simpleName}")
                    outcome = fail(
                        SyncPhase.SERVER_ERROR,
                        e.message ?: "同步失败",
                        true,
                        s.lastSyncAt,
                    )
                }
            }
            outcome ?: SyncOutcome.Failure(SyncPhase.SERVER_ERROR, "未知错误", true)
        }
    }

    private suspend fun fail(
        phase: SyncPhase,
        message: String,
        retryable: Boolean,
        lastSyncAt: Long,
    ): SyncOutcome.Failure {
        val pending = runCatching { dirtyCount(lastSyncAt) }.getOrDefault(0)
        status.update { it.copy(phase = phase, message = message, pendingCount = pending) }
        return SyncOutcome.Failure(phase, message, retryable)
    }

    // ------------------------------------------------------------------ 单轮同步

    private suspend fun attemptSync(s: AppSettings, trigger: String, quiet: Boolean = false): SyncOutcome {
        val session = dav.openSession(s.email, s.appPassword, s.rootDirNormalized)
        val syncStart = Dates.now()
        val rootUrl = session.collectionUrl()

        // 目录逐层创建，MKCOL 405 视为已存在。
        // 建目录要发 3 个请求、而且建好之后不会再消失，所以只做一次；
        // 万一远端被清空（PROPFIND 回 404），下面会重建并把标记重新置位。
        var listing = if (s.dirsReady) {
            session.propfind(rootUrl, depth = 1)
        } else {
            session.ensureDirectories()
            session.propfind(rootUrl, depth = 1).also { if (it.code in 200..299) settings.markDirsReady() }
        }
        if (listing.code == 404 && s.dirsReady) {
            log("WARN", "远端目录不见了，重新创建")
            settings.markDirsReady(false)
            session.ensureDirectories()
            listing = session.propfind(rootUrl, depth = 1)
            if (listing.code in 200..299) settings.markDirsReady()
        }
        when {
            listing.code == 401 || listing.code == 403 -> throw DavAuthException()
            listing.code == 507 -> throw DavSpaceException()
            listing.code >= 500 -> throw DavServerException(listing.code)
        }

        val manifestUrl = session.fileUrl(DavClient.MANIFEST)
        val remoteManifest = session.get(manifestUrl)?.let { bytes ->
            runCatching { json.decodeFromString<ManifestDto>(bytes.decodeToString()) }.getOrNull()
        }

        if (remoteManifest != null && remoteManifest.schemaVersion > SCHEMA_VERSION) {
            throw DavUpgradeException(remoteManifest.schemaVersion)
        }

        // 记下“云端这份数据是谁写的”，家庭共享时可显示「老婆的手机 · 5 分钟前」
        if (remoteManifest != null && remoteManifest.lastDeviceId.isNotBlank() &&
            remoteManifest.lastDeviceId != s.deviceId
        ) {
            settings.updateRemotePeer(
                remoteManifest.lastDeviceName.ifBlank { "另一台设备" },
                remoteManifest.lastWriteAt,
            )
        }

        val preferredName =
            if (s.backupEncrypted && s.backupPassphrase.isNotBlank()) DavClient.DATA_ENC else DavClient.DATA
        // 目标文件始终用“当前配置对应的文件名”，否则会出现 data.json 里装着密文的情况
        val existing = listing.entries.firstOrNull { !it.isCollection && it.name == preferredName }
        val staleOther = listing.entries.firstOrNull {
            !it.isCollection && it.name != preferredName &&
                (it.name == DavClient.DATA || it.name == DavClient.DATA_ENC)
        }
        val dataName = preferredName
        val dataUrl = session.fileUrl(dataName)

        val dirtyBefore = dirtyCount(s.lastSyncAt)
        // 加密开关变化时，远端另一个格式的文件要清掉，避免两台设备各读一份
        val formatSwitch = staleOther != null
        val remoteUnchanged = existing != null &&
            remoteManifest != null &&
            remoteManifest.lastWriteAt <= s.lastSyncAt &&
            !formatSwitch

        if (remoteUnchanged && dirtyBefore == 0) {
            // 远端没变、本地也没改：补检查一次附件（换机/清理缓存后本地可能缺图）。
            // 后台轮询不做这件事——那是每 10 分钟一次的多余 PROPFIND，
            // 下次回前台 / 手动同步 / 真正有改动时会补上。
            if (!quiet) runCatching { syncAttachments(session, readLocalSnapshot(s)) }
            settings.updateSyncResult(s.lastSyncAt, existing?.etag, s.lastConflictCount)
            status.update {
                it.copy(
                    phase = SyncPhase.SYNCED,
                    pendingCount = 0,
                    lastSuccessAt = Dates.now(),
                    conflictCount = s.lastConflictCount,
                    message = "已是最新",
                )
            }
            if (!quiet) log("INFO", "跳过：远端未变化且本地无改动")
            return SyncOutcome.Fresh
        }

        // 拉远端全量（切格式时另一份文件才是当前数据来源，必须读它参与合并）
        val sourceEntry = existing ?: staleOther
        val oldBytes = if (sourceEntry != null) {
            val (code, body) = session.getWithCode(session.fileUrl(sourceEntry.name))
            when {
                code == 401 || code == 403 -> throw DavAuthException()
                code == 507 -> throw DavSpaceException()
                code >= 500 -> throw DavServerException(code)
            }
            body
        } else {
            null
        }
        val remoteSnapshot = oldBytes?.takeIf { it.isNotEmpty() }?.let { decodeSnapshot(it, s) }
            ?: LedgerSnapshotDto()

        // 本地全量
        val localSnapshot = readLocalSnapshot(s)

        // 逐表合并
        var conflicts = 0
        val now = Dates.now()
        val (mergedChildren, c1) = mergeTable<ChildEntity>(
            localSnapshot.children, remoteSnapshot.children,
            lastSyncAt = s.lastSyncAt,
            newId = { UUID.randomUUID().toString() },
            toConflictCopy = { e, id ->
                e.copy(
                    id = id,
                    name = ConflictMark.PREFIX + e.name,
                    updatedAt = now,
                    note = conflictNote(e.note),
                    deleted = false,
                )
            },
            normalize = { it.copy(id = "", updatedAt = 0L, createdAt = 0L) },
        )
        conflicts += c1
        val (mergedCourses, c2) = mergeTable<CourseEntity>(
            localSnapshot.courses, remoteSnapshot.courses,
            lastSyncAt = s.lastSyncAt,
            newId = { UUID.randomUUID().toString() },
            toConflictCopy = { e, id ->
                e.copy(
                    id = id,
                    courseName = ConflictMark.PREFIX + e.courseName,
                    // 副本先挂在“暂停”，避免重复生成课表、重复提醒；确认后再改回在读
                    status = CourseStatus.PAUSED.name,
                    updatedAt = now,
                    note = conflictNote(e.note),
                    deleted = false,
                )
            },
            normalize = { it.copy(id = "", updatedAt = 0L, createdAt = 0L) },
        )
        conflicts += c2
        val (mergedPayments, c3) = mergeTable<PaymentEntity>(
            localSnapshot.payments, remoteSnapshot.payments,
            lastSyncAt = s.lastSyncAt,
            newId = { UUID.randomUUID().toString() },
            toConflictCopy = { e, id -> e.copy(id = id, updatedAt = now, note = conflictNote(e.note), deleted = false) },
            normalize = { it.copy(id = "", updatedAt = 0L, createdAt = 0L) },
        )
        conflicts += c3
        val (mergedLessons, c4) = mergeTable<LessonEntity>(
            localSnapshot.lessons, remoteSnapshot.lessons,
            lastSyncAt = s.lastSyncAt,
            newId = { UUID.randomUUID().toString() },
            toConflictCopy = { e, id -> e.copy(id = id, updatedAt = now, note = conflictNote(e.note), deleted = false) },
            normalize = { it.copy(id = "", updatedAt = 0L, createdAt = 0L) },
        )
        conflicts += c4

        // 合并结果落库（同步期间用户的新改动不会被覆盖）
        applyTable(localSnapshot.children, mergedChildren) { db.childDao().upsertAll(it) }
        applyTable(localSnapshot.courses, mergedCourses) { db.courseDao().upsertAll(it) }
        applyTable(localSnapshot.payments, mergedPayments) { db.paymentDao().upsertAll(it) }
        applyTable(localSnapshot.lessons, mergedLessons) { db.lessonDao().upsertAll(it) }

        val merged = LedgerSnapshotDto(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = now,
            deviceId = s.deviceId,
            children = mergedChildren,
            courses = mergedCourses,
            payments = mergedPayments,
            lessons = mergedLessons,
        )

        // 上传前把远端旧版本存一份备份（最多 10 份）
        backupPreviousVersion(session, oldBytes)

        // 上传全量：先 .tmp 再正式文件，带 If-Match 乐观锁
        val payload = encodeSnapshot(merged, s)
        val tmpUrl = session.fileUrl("$dataName.tmp")
        runCatching { session.put(tmpUrl, payload) }

        var putResult = session.put(
            dataUrl,
            payload,
            ifMatch = existing?.etag,
            ifNoneMatch = existing == null,
        )
        // 坚果云对 PUT + If-None-Match: * 会直接回 412，哪怕文件根本还不存在。
        // 于是「云端是空的、第一次上传」被当成冲突，重试三轮全 412，最后挂一个「同步冲突」。
        // 先确认远端到底有没有这个文件：真没有就无条件写；真有说明别的设备抢先了，交给重试去合并。
        if (existing == null && putResult.code == 412) {
            val appeared = session.stat(dataUrl)
            if (appeared == null) {
                log("WARN", "服务器不接受 If-None-Match，首次上传改为无条件 PUT")
                putResult = session.put(dataUrl, payload)
            } else {
                log("WARN", "首次上传前远端已有文件，改为重新拉取合并")
            }
        }
        // 云端文件还在、ETag 也没变，服务器却回 412：说明它不认我们带的 If-Match
        // （坚果云返回的是弱校验器，或者格式和预期不一样）。这时候死磕重试永远失败，
        // 直接无条件写一次；真并发的情况 ETag 会变，仍然走重试去合并。
        if (putResult.code == 412 && existing != null) {
            val latestEtag = session.stat(dataUrl)?.etag
            if (latestEtag != null && latestEtag == existing.etag) {
                log("WARN", "云端未变化仍 412，改为无条件 PUT")
                putResult = session.put(dataUrl, payload)
            }
        }
        when {
            putResult.code == 401 || putResult.code == 403 -> throw DavAuthException()
            putResult.code == 412 -> throw DavPreconditionException()
            putResult.code == 507 -> throw DavSpaceException()
            putResult.code >= 500 -> throw DavServerException(putResult.code)
            !putResult.ok -> throw DavServerException(putResult.code)
        }
        runCatching { session.delete(tmpUrl) }

        // 加密开关切换时清理另一份数据文件
        val otherName = if (dataName == DavClient.DATA) DavClient.DATA_ENC else DavClient.DATA
        if (otherName != dataName && listing.entries.any { it.name == otherName }) {
            runCatching { session.delete(session.fileUrl(otherName)) }
        }

        val newEtag = putResult.etag ?: session.stat(dataUrl)?.etag
        val manifest = ManifestDto(
            schemaVersion = SCHEMA_VERSION,
            lastDeviceId = s.deviceId,
            lastDeviceName = s.deviceName,
            lastWriteAt = now,
            dataEtag = newEtag,
            recordCount = merged.recordCount,
        )
        val manifestPut = session.put(
            manifestUrl,
            json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
        )
        if (manifestPut.code == 401) throw DavAuthException()

        settings.updateSyncResult(syncStart, newEtag, conflicts)
        syncAttachments(session, merged)

        val remainingDirty = dirtyCount(syncStart)
        status.update {
            it.copy(
                phase = if (conflicts > 0) SyncPhase.CONFLICT else SyncPhase.SYNCED,
                pendingCount = remainingDirty,
                lastSuccessAt = Dates.now(),
                conflictCount = conflicts,
                message = when {
                    conflicts > 0 -> "发现 $conflicts 条同步冲突"
                    remainingDirty > 0 -> "已同步，还有 $remainingDirty 条待上传"
                    else -> "已同步"
                },
            )
        }
        log(
            "INFO",
            buildString {
                append("同步完成（$trigger）：")
                append("合并 ${merged.recordCount} 条")
                if (conflicts > 0) append("，冲突 $conflicts 条")
                if (remainingDirty > 0) append("，待上传 $remainingDirty 条")
            }
        )
        return SyncOutcome.Success
    }

    // ------------------------------------------------------------------ 合并

    private fun conflictNote(note: String): String =
        if (note.startsWith(ConflictMark.PREFIX.trim())) note else (ConflictMark.PREFIX + note).trim()

    private fun <T : SyncRecord> mergeTable(
        local: List<T>,
        remote: List<T>,
        lastSyncAt: Long,
        newId: () -> String,
        toConflictCopy: (T, String) -> T,
        normalize: (T) -> T,
    ): Pair<List<T>, Int> {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        val merged = mutableListOf<T>()
        var conflicts = 0

        for (id in localById.keys + remoteById.keys) {
            val localRecord = localById[id]
            val remoteRecord = remoteById[id]
            when {
                localRecord != null && remoteRecord != null -> {
                    val localWins = localRecord.updatedAt >= remoteRecord.updatedAt
                    val winner = if (localWins) localRecord else remoteRecord
                    val loser = if (localWins) remoteRecord else localRecord
                    merged += winner
                    val sameContent = normalize(localRecord) == normalize(remoteRecord)
                    val concurrent = abs(localRecord.updatedAt - remoteRecord.updatedAt) < CONFLICT_WINDOW_MS
                    // 只在「两边都真的改过」时才留副本：某一条自上次同步后没动过，
                    // 说明只是另一端单方面的改动被正常拉过来，走赢者通吃即可。
                    // 之前只看 5 秒时间窗，两台设备时钟略有偏差或同一端的数据被
                    // 回写时，就会凭空多出「[冲突副本]」垃圾数据。
                    val bothChanged = localRecord.updatedAt > lastSyncAt &&
                        remoteRecord.updatedAt > lastSyncAt
                    if (!sameContent && concurrent && bothChanged) {
                        merged += toConflictCopy(loser, newId())
                        conflicts++
                    }
                }
                localRecord != null -> merged += localRecord
                remoteRecord != null -> merged += remoteRecord
            }
        }
        return merged.sortedWith(compareBy({ it.createdAt }, { it.id })) to conflicts
    }

    private suspend fun <T : SyncRecord> applyTable(
        currentLocal: List<T>,
        merged: List<T>,
        upsertAll: suspend (List<T>) -> Unit,
    ) {
        val byId = currentLocal.associateBy { it.id }
        val toWrite = merged.filter { record ->
            val existing = byId[record.id]
            when {
                existing == null -> true
                existing.updatedAt > record.updatedAt -> false
                else -> existing != record
            }
        }
        if (toWrite.isNotEmpty()) upsertAll(toWrite)
    }

    // ------------------------------------------------------------------ 备份 / 附件

    private suspend fun backupPreviousVersion(session: DavClient.DavSession, oldBytes: ByteArray?) {
        if (oldBytes == null || oldBytes.isEmpty()) return
        runCatching {
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now())
            val url = session.collectionUrl(DavClient.BACKUPS, "data-$stamp.json", trailingSlash = false)
            session.put(url, oldBytes)
            val backups = session.propfind(session.collectionUrl(DavClient.BACKUPS), depth = 1)
                .entries.filter { !it.isCollection && it.name.startsWith("data-") }
                .sortedByDescending { it.name }
            backups.drop(MAX_BACKUPS).forEach { entry ->
                runCatching {
                    session.delete(session.collectionUrl(DavClient.BACKUPS, entry.name, trailingSlash = false))
                }
            }
        }
    }

    private suspend fun syncAttachments(session: DavClient.DavSession, snapshot: LedgerSnapshotDto) {
        val names = snapshot.payments
            .filter { !it.deleted }
            .flatMap { Receipts.decodePaths(it.receiptPaths) }
            .distinct()
        if (names.isEmpty()) return

        val remoteNames = runCatching {
            session.propfind(session.collectionUrl(DavClient.ATTACHMENTS), depth = 1)
                .entries.filter { !it.isCollection }.map { it.name }.toSet()
        }.getOrDefault(emptySet())

        val dir = Receipts.dir(context)
        for (name in names) {
            val local = File(dir, name)
            val remoteUrl = session.collectionUrl(DavClient.ATTACHMENTS, name, trailingSlash = false)
            runCatching {
                when {
                    local.exists() && !remoteNames.contains(name) -> session.put(remoteUrl, local.readBytes())
                    !local.exists() && remoteNames.contains(name) ->
                        session.get(remoteUrl)?.let { local.writeBytes(it) }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 编解码

    private fun encodeSnapshot(snapshot: LedgerSnapshotDto, s: AppSettings): ByteArray {
        val jsonBytes = json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        val gzipped = gzip(jsonBytes)
        return if (s.backupEncrypted && s.backupPassphrase.isNotBlank()) {
            BackupCrypto.encrypt(gzipped, s.backupPassphrase)
        } else {
            gzipped
        }
    }

    private fun decodeSnapshot(bytes: ByteArray, s: AppSettings): LedgerSnapshotDto {
        var data = bytes
        if (BackupCrypto.looksEncrypted(data)) {
            if (s.backupPassphrase.isBlank()) {
                throw DavDecryptException("远端数据已加密，请先在设置里填写备份口令")
            }
            data = BackupCrypto.decrypt(data, s.backupPassphrase)
        }
        if (isGzip(data)) data = gunzip(data)
        return json.decodeFromString(data.decodeToString())
    }

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size > 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { return it.readBytes() }
    }

    // ------------------------------------------------------------------ 本地读取

    suspend fun readLocalSnapshot(s: AppSettings): LedgerSnapshotDto = LedgerSnapshotDto(
        schemaVersion = SCHEMA_VERSION,
        exportedAt = Dates.now(),
        deviceId = s.deviceId,
        children = db.childDao().getAll(),
        courses = db.courseDao().getAll(),
        payments = db.paymentDao().getAll(),
        lessons = db.lessonDao().getAll(),
    )

    suspend fun dirtyCount(since: Long): Int =
        db.childDao().countDirty(since) +
            db.courseDao().countDirty(since) +
            db.paymentDao().countDirty(since) +
            db.lessonDao().countDirty(since)

    private suspend fun log(level: String, message: String) {
        runCatching {
            db.syncLogDao().insert(SyncLogEntity(at = Dates.now(), level = level, message = message))
            db.syncLogDao().trim()
        }
    }
}
