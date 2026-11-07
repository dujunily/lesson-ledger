package cn.yiweiqiu.lessonledger.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 票据照片：只保存本地相对文件名，禁止申请存储权限，
 * 通过系统相册 / 相机 Intent 获取，导入时压缩到最长边 1600px。
 */
object Receipts {

    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_EDGE = 1600
    private const val QUALITY = 82

    fun dir(context: Context): File =
        File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun decodePaths(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun encodePaths(paths: List<String>): String? =
        if (paths.isEmpty()) null else json.encodeToString(paths.distinct())

    fun newPhotoTarget(context: Context): Pair<Uri, String> {
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(dir(context), name)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return uri to name
    }

    /** 从相册/相机拿到图片后压缩落盘，返回文件名 */
    fun importFromUri(context: Context, uri: Uri): String? {
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(dir(context), name)
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                decodeSampled(input)
            } ?: return null
            val rotation = readRotation(context, uri)
            val fixed = if (rotation != 0f) rotate(bitmap, rotation) else bitmap
            target.outputStream().use { out ->
                fixed.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            if (fixed !== bitmap) fixed.recycle()
            bitmap.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    /** 相机直出的照片体积很大，落盘后就地压缩，避免上传拖慢同步 */
    fun compressFile(context: Context, name: String) {
        val target = file(context, name)
        if (!target.exists()) return
        runCatching {
            val bytes = target.readBytes()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val longest = maxOf(options.outWidth, options.outHeight)
            var sample = 1
            while (longest / sample > MAX_EDGE * 2) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return
            val rotation = try {
                when (ExifInterface(target.path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) {
                0f
            }
            val fixed = if (rotation != 0f) rotate(bitmap, rotation) else bitmap
            target.outputStream().use { fixed.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (fixed !== bitmap) fixed.recycle()
            bitmap.recycle()
        }
    }

    private fun decodeSampled(input: java.io.InputStream): Bitmap? {
        val bytes = input.readBytes()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val longest = maxOf(options.outWidth, options.outHeight)
        var sample = 1
        while (longest / sample > MAX_EDGE * 2) sample *= 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun readRotation(context: Context, uri: Uri): Float = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (e: Exception) {
        0f
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
