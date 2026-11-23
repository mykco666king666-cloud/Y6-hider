package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.CorruptedBackupException
import com.example.security.VaultCryptoManager
import com.example.security.WrongBackupPasswordException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultCryptoAndPinTest {

    @Test
    fun `salted PBKDF2 pin hashing never stores plaintext and verifies accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)
        val salt = crypto.generateSalt()
        val saltB64 = crypto.encodeBase64(salt)
        val hash = crypto.hashSecret("4829", salt)

        assertNotEquals("4829", hash)
        assertTrue(crypto.verifySecret("4829", saltB64, hash))
        assertFalse(crypto.verifySecret("4820", saltB64, hash))
        assertFalse(crypto.verifySecret("", saltB64, hash))
    }

    @Test
    fun `recovery code generation and verification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)
        val code = crypto.generateRecoveryCode()
        assertTrue(code.startsWith("OVLT-"))
        assertEquals(19, code.length)

        val salt = crypto.generateSalt()
        val hash = crypto.hashSecret(code, salt)
        assertTrue(crypto.verifySecret(code, crypto.encodeBase64(salt), hash))
        assertFalse(crypto.verifySecret("OVLT-0000-0000-0000", crypto.encodeBase64(salt), hash))
    }

    @Test
    fun `AES-CTR file encryption supports exact random-access seeking for ExoPlayer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)
        val plaintext = Random(42).nextBytes(128 * 1024 + 37)
        val destFile = File(context.filesDir, "seek_test.ovm")

        val writeRes = crypto.encryptStreamToFile(ByteArrayInputStream(plaintext), destFile)
        assertEquals(plaintext.size.toLong(), writeRes.plaintextBytes)
        assertEquals(plaintext.size.toLong(), crypto.decryptedLength(destFile))

        // Verify SHA-256 check
        val verified = crypto.computePlaintextSha256(destFile)
        assertEquals(writeRes.sha256Hex, verified.sha256Hex)

        // Test unaligned and block-aligned seek offsets
        val testOffsets = listOf(0L, 1L, 15L, 16L, 17L, 31L, 32L, 1023L, 65535L, 99999L, plaintext.size.toLong() - 50)
        for (offset in testOffsets) {
            val len = minOf(512, plaintext.size - offset.toInt())
            val slice = ByteArray(len)
            crypto.openDecryptedSeekableStream(destFile, offset).use { stream ->
                var readTotal = 0
                while (readTotal < len) {
                    val r = stream.read(slice, readTotal, len - readTotal)
                    if (r < 0) break
                    readTotal += r
                }
                assertEquals("Read mismatch at offset $offset", len, readTotal)
            }
            val expectedSlice = plaintext.copyOfRange(offset.toInt(), offset.toInt() + len)
            assertArrayEquals("Decrypted bytes mismatch at offset $offset", expectedSlice, slice)
        }
        destFile.delete()
    }

    @Test
    fun `encrypted backup detects wrong password and corrupted payload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)
        val payload = "ObsidianVaultBackupPayloadTest123456".toByteArray()
        val out = ByteArrayOutputStream()

        crypto.encryptBackupPayload(payload, "StrongPass99!", out)
        val encrypted = out.toByteArray()

        // Valid password decrypts cleanly
        val decrypted = crypto.decryptBackupPayload(encrypted, "StrongPass99!")
        assertArrayEquals(payload, decrypted)

        // Wrong password throws WrongBackupPasswordException
        var wrongPasswordCaught = false
        try {
            crypto.decryptBackupPayload(encrypted, "WrongPass")
        } catch (_: WrongBackupPasswordException) {
            wrongPasswordCaught = true
        }
        assertTrue(wrongPasswordCaught)

        // Corrupted payload byte throws WrongBackupPasswordException or CorruptedBackupException
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0xFF).toByte()
        var tamperCaught = false
        try {
            crypto.decryptBackupPayload(tampered, "StrongPass99!")
        } catch (_: WrongBackupPasswordException) {
            tamperCaught = true
        } catch (_: CorruptedBackupException) {
            tamperCaught = true
        }
        assertTrue(tamperCaught)

        // Truncated/invalid header throws CorruptedBackupException
        var badHeaderCaught = false
        try {
            crypto.decryptBackupPayload("BADHEADER".toByteArray(), "StrongPass99!")
        } catch (_: CorruptedBackupException) {
            badHeaderCaught = true
        }
        assertTrue(badHeaderCaught)
    }
}
