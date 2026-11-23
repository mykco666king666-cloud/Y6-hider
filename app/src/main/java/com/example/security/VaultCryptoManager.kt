package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.math.BigInteger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedWriteResult(
    val plaintextBytes: Long,
    val sha256Hex: String
)

class WrongBackupPasswordException(message: String) : Exception(message)
class CorruptedBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Cryptographic engine for Obsidian Vault:
 * 1. PBKDF2WithHmacSHA256 salted PIN & Recovery Key hashing with constant-time verification.
 * 2. Android Keystore AES-256-GCM wrapping of the vault's 256-bit data encryption key.
 * 3. AES-256-CTR file encryption with random 16-byte per-file IV and O(1) random-access seeking
 *    for smooth ExoPlayer video playback + streaming Coil image decoding.
 * 4. Portable PBKDF2 + AES-256-GCM encrypted `.ovbackup` archive format with magic header.
 */
class VaultCryptoManager(private val context: Context) {

    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedVaultKey: SecretKey? = null

    fun generateSalt(bytes: Int = 16): ByteArray = ByteArray(bytes).also { secureRandom.nextBytes(it) }

    fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeBase64(str: String): ByteArray = Base64.decode(str, Base64.NO_WRAP)

    fun hashSecret(secret: String, salt: ByteArray, iterations: Int = PIN_ITERATIONS): String {
        val chars = secret.toCharArray()
        val keyBytes = try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(PBEKeySpec(chars, salt, iterations, 256)).encoded
        } catch (_: Exception) {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            factory.generateSecret(PBEKeySpec(chars, salt, iterations, 256)).encoded
        } finally {
            chars.fill('\u0000')
        }
        return encodeBase64(keyBytes)
    }

    fun verifySecret(secret: String, saltBase64: String, expectedHashBase64: String): Boolean {
        return try {
            val salt = decodeBase64(saltBase64)
            val actualHash = decodeBase64(hashSecret(secret, salt))
            val expectedHash = decodeBase64(expectedHashBase64)
            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    fun generateRecoveryCode(): String {
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val groups = (0 until 3).map {
            buildString {
                repeat(4) {
                    append(alphabet[secureRandom.nextInt(alphabet.length)])
                }
            }
        }
        return "OVLT-${groups.joinToString("-")}"
    }

    @Synchronized
    fun getOrCreateVaultDataKey(): SecretKey {
        cachedVaultKey?.let { return it }

        val keyFile = File(context.filesDir, ".vault_key.bin")
        val keystoreAvailable = isAndroidKeyStoreAvailable()

        if (keyFile.exists() && keyFile.length() > 16) {
            val bytes = keyFile.readBytes()
            val decodedKey = if (keystoreAvailable && bytes.size > 12) {
                try {
                    val iv = bytes.copyOfRange(0, 12)
                    val cipherText = bytes.copyOfRange(12, bytes.size)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreWrappingKey(), GCMParameterSpec(128, iv))
                    val rawKey = cipher.doFinal(cipherText)
                    SecretKeySpec(rawKey, "AES")
                } catch (_: Exception) {
                    // Fallback only if keystore key was invalidated and fallback file is raw
                    if (bytes.size == 32) SecretKeySpec(bytes, "AES") else null
                }
            } else if (bytes.size == 32) {
                SecretKeySpec(bytes, "AES")
            } else null

            if (decodedKey != null) {
                cachedVaultKey = decodedKey
                return decodedKey
            }
        }

        val rawKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val secretKey = SecretKeySpec(rawKey, "AES")
        val persisted = if (keystoreAvailable) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreWrappingKey())
                val iv = cipher.iv
                val wrapped = cipher.doFinal(rawKey)
                iv + wrapped
            } catch (_: Exception) {
                rawKey
            }
        } else {
            rawKey
        }
        keyFile.writeBytes(persisted)
        cachedVaultKey = secretKey
        return secretKey
    }

    private fun isAndroidKeyStoreAvailable(): Boolean {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateKeystoreWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Streams plaintext [input] into AES-256-CTR encrypted [destFile] with a 16-byte IV prefix,
     * computing plaintext byte length and SHA-256 digest simultaneously.
     */
    fun encryptStreamToFile(input: InputStream, destFile: File): EncryptedWriteResult {
        val key = getOrCreateVaultDataKey()
        val iv = ByteArray(16).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L

        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { fos ->
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    cos.write(buffer, 0, read)
                    totalBytes += read
                }
                cos.flush()
            }
        }
        val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
        return EncryptedWriteResult(plaintextBytes = totalBytes, sha256Hex = shaHex)
    }

    fun encryptBytesToFile(data: ByteArray, destFile: File): EncryptedWriteResult {
        return ByteArrayInputStream(data).use { encryptStreamToFile(it, destFile) }
    }

    fun openDecryptedInputStream(file: File): InputStream {
        val key = getOrCreateVaultDataKey()
        val fis = FileInputStream(file)
        val iv = ByteArray(16)
        var readTotal = 0
        while (readTotal < 16) {
            val r = fis.read(iv, readTotal, 16 - readTotal)
            if (r < 0) {
                fis.close()
                throw IllegalStateException("Corrupted vault file: missing IV header")
            }
            readTotal += r
        }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        }
        return CipherInputStream(fis, cipher)
    }

    fun openDecryptedSeekableStream(file: File, plaintextOffset: Long): InputStream {
        require(plaintextOffset >= 0L)
        val key = getOrCreateVaultDataKey()
        val raf = RandomAccessFile(file, "r")
        try {
            val fileLen = raf.length()
            if (fileLen < 16L) throw IllegalStateException("Corrupted vault file: missing header")
            val iv = ByteArray(16)
            raf.readFully(iv)

            val blockIndex = plaintextOffset / 16L
            val intraBlockSkip = (plaintextOffset % 16L).toInt()
            val blockAlignedOffset = blockIndex * 16L
            val counterIv = incrementCounterIv(iv, blockIndex)

            raf.seek(16L + blockAlignedOffset)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, IvParameterSpec(counterIv))
            }
            val rawRafStream = object : InputStream() {
                override fun read(): Int = raf.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
                override fun close() = raf.close()
            }
            val cipherStream = CipherInputStream(rawRafStream, cipher)
            if (intraBlockSkip > 0) {
                val discard = ByteArray(intraBlockSkip)
                var discarded = 0
                while (discarded < intraBlockSkip) {
                    val r = cipherStream.read(discard, discarded, intraBlockSkip - discarded)
                    if (r < 0) break
                    discarded += r
                }
            }
            return object : FilterInputStream(cipherStream) {
                override fun close() {
                    super.close()
                }
            }
        } catch (t: Throwable) {
            runCatching { raf.close() }
            throw t
        }
    }

    fun decryptedLength(file: File): Long = (file.length() - 16L).coerceAtLeast(0L)

    fun computePlaintextSha256(file: File): EncryptedWriteResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        openDecryptedInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                digest.update(buf, 0, r)
                total += r
            }
        }
        val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
        return EncryptedWriteResult(plaintextBytes = total, sha256Hex = shaHex)
    }

    fun readDecryptedBytes(file: File): ByteArray {
        val out = ByteArrayOutputStream()
        openDecryptedInputStream(file).use { it.copyTo(out) }
        return out.toByteArray()
    }

    /**
     * Streams a ZIP file into [output] encrypted with PBKDF2WithHmacSHA256 + AES-256-GCM.
     * Format: [6-byte "OVBK01"][16-byte salt][12-byte IV][AES-GCM ciphertext + 16-byte auth tag]
     */
    fun encryptBackupFileToStream(zipFile: File, password: String, output: OutputStream): Pair<String, Long> {
        val salt = generateSalt(16)
        val iv = generateSalt(12)
        val key = deriveBackupKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var totalWritten = 0L

        fun writeAndHash(bytes: ByteArray, off: Int = 0, len: Int = bytes.size) {
            digest.update(bytes, off, len)
            output.write(bytes, off, len)
            totalWritten += len
        }

        writeAndHash(BACKUP_MAGIC)
        writeAndHash(salt)
        writeAndHash(iv)

        FileInputStream(zipFile).use { fis ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = fis.read(buf)
                if (r <= 0) break
                val encChunk = cipher.update(buf, 0, r)
                if (encChunk != null && encChunk.isNotEmpty()) {
                    writeAndHash(encChunk)
                }
            }
            val finalBlock = cipher.doFinal()
            if (finalBlock != null && finalBlock.isNotEmpty()) {
                writeAndHash(finalBlock)
            }
        }
        output.flush()
        val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
        return shaHex to totalWritten
    }

    /**
     * Streams and verifies an encrypted `.ovbackup` file into [destZipFile].
     * Deletes [destZipFile] and throws [WrongBackupPasswordException] if GCM auth fails.
     */
    fun decryptBackupFile(encryptedFile: File, password: String, destZipFile: File) {
        val minSize = BACKUP_MAGIC.size + 16 + 12 + 16L
        if (encryptedFile.length() < minSize) {
            throw CorruptedBackupException("Backup file is too small or truncated.")
        }
        FileInputStream(encryptedFile).use { fis ->
            val header = ByteArray(BACKUP_MAGIC.size)
            readFully(fis, header)
            if (!header.contentEquals(BACKUP_MAGIC)) {
                throw CorruptedBackupException("Invalid Obsidian Vault backup file signature.")
            }
            val salt = ByteArray(16).also { readFully(fis, it) }
            val iv = ByteArray(12).also { readFully(fis, it) }
            val key = deriveBackupKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }

            try {
                FileOutputStream(destZipFile).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = fis.read(buf)
                        if (r <= 0) break
                        val decChunk = cipher.update(buf, 0, r)
                        if (decChunk != null && decChunk.isNotEmpty()) {
                            fos.write(decChunk)
                        }
                    }
                    val finalBlock = cipher.doFinal()
                    if (finalBlock != null && finalBlock.isNotEmpty()) {
                        fos.write(finalBlock)
                    }
                    fos.flush()
                }
            } catch (e: AEADBadTagException) {
                destZipFile.delete()
                throw WrongBackupPasswordException("Incorrect backup password or altered backup file.")
            } catch (e: Exception) {
                destZipFile.delete()
                throw CorruptedBackupException("Failed to decrypt backup archive.", e)
            }
        }
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val r = input.read(target, offset, target.size - offset)
            if (r < 0) throw CorruptedBackupException("Unexpected end of backup stream.")
            offset += r
        }
    }

    fun encryptBackupPayload(plaintextZipBytes: ByteArray, password: String, output: OutputStream): String {
        val tmp = File.createTempFile("ov_enc_", ".zip", context.cacheDir)
        return try {
            tmp.writeBytes(plaintextZipBytes)
            encryptBackupFileToStream(tmp, password, output).first
        } finally {
            tmp.delete()
        }
    }

    fun decryptBackupPayload(encryptedBytes: ByteArray, password: String): ByteArray {
        val tmpIn = File.createTempFile("ov_dec_in_", ".ovbackup", context.cacheDir)
        val tmpOut = File.createTempFile("ov_dec_out_", ".zip", context.cacheDir)
        return try {
            tmpIn.writeBytes(encryptedBytes)
            decryptBackupFile(tmpIn, password, tmpOut)
            tmpOut.readBytes()
        } finally {
            tmpIn.delete()
            tmpOut.delete()
        }
    }

    private fun deriveBackupKey(password: String, salt: ByteArray): SecretKey {
        val chars = password.toCharArray()
        val keyBytes = try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(PBEKeySpec(chars, salt, BACKUP_ITERATIONS, 256)).encoded
        } finally {
            chars.fill('\u0000')
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "obsidian_vault_master_wrapping_key_v1"
        private const val PIN_ITERATIONS = 60_000
        private const val BACKUP_ITERATIONS = 80_000
        private val BACKUP_MAGIC = "OVBK01".toByteArray(Charsets.US_ASCII)

        /**
         * Big-endian 128-bit AES counter increment: iv_out = (iv_in + blockOffset) mod 2^128
         */
        fun incrementCounterIv(iv: ByteArray, blockOffset: Long): ByteArray {
            if (blockOffset == 0L) return iv.copyOf()
            val ivInt = BigInteger(1, iv)
            val sum = ivInt.add(BigInteger.valueOf(blockOffset))
            val mod = BigInteger.ONE.shiftLeft(128)
            val wrapped = sum.mod(mod).toByteArray()
            val result = ByteArray(16)
            val srcStart = (wrapped.size - 16).coerceAtLeast(0)
            val copyLen = (wrapped.size - srcStart).coerceAtMost(16)
            System.arraycopy(wrapped, srcStart, result, 16 - copyLen, copyLen)
            return result
        }
    }
}
