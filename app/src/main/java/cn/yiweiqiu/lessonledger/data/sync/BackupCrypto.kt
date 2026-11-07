package cn.yiweiqiu.lessonledger.data.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份加密（可选）：PBKDF2-HMAC-SHA256 派生密钥 + AES/GCM/NoPadding。
 * 文件格式：magic("LLB1") + salt(16) + iv(12) + ciphertext
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf(0x4C, 0x4C, 0x42, 0x31)
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain)
        return MAGIC + salt + iv + encrypted
    }

    fun decrypt(payload: ByteArray, passphrase: String): ByteArray {
        require(payload.size > MAGIC.size + SALT_LEN + IV_LEN) { "加密数据长度异常" }
        val salt = payload.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val iv = payload.copyOfRange(MAGIC.size + SALT_LEN, MAGIC.size + SALT_LEN + IV_LEN)
        val body = payload.copyOfRange(MAGIC.size + SALT_LEN + IV_LEN, payload.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    fun looksEncrypted(payload: ByteArray): Boolean =
        payload.size > MAGIC.size && MAGIC.indices.all { payload[it] == MAGIC[it] }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }
}
