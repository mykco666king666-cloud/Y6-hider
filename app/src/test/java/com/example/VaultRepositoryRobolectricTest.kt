package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.VaultDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.storage.VaultMediaRepository
import com.example.security.VaultCryptoManager
import com.example.security.WrongBackupPasswordException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultRepositoryRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: VaultDatabase
    private lateinit var crypto: VaultCryptoManager
    private lateinit var repo: VaultMediaRepository
    private lateinit var prefsRepo: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        crypto = VaultCryptoManager(context)
        repo = VaultMediaRepository(context, db.vaultDao(), crypto)
        prefsRepo = UserPreferencesRepository(context, crypto)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `import media, duplicate detection, integrity audit, safe cache clear, and album workflow`() = runTest {
        val sampleFile = File(context.cacheDir, "sample_photo.jpg")
        // Write valid minimal bytes (non-empty)
        val payload = ByteArray(4096) { (it % 251).toByte() }
        sampleFile.writeBytes(payload)
        val uri = Uri.fromFile(sampleFile)

        // Create album
        val album = repo.createAlbum("Private Trips", "Passport & docs")
        assertEquals("Private Trips", album.name)

        // Import into album
        val batch1 = repo.importMediaUris(listOf(uri), targetAlbumId = album.id)
        assertEquals(1, batch1.importedItems.size)
        assertEquals(0, batch1.duplicateFileNames.size)
        val imported = batch1.importedItems.first()
        assertEquals(album.id, imported.albumId)

        // Duplicate import is caught and skipped
        val batch2 = repo.importMediaUris(listOf(uri), targetAlbumId = album.id)
        assertEquals(0, batch2.importedItems.size)
        assertEquals(1, batch2.duplicateFileNames.size)

        // Integrity audit confirms file is intact
        val integrity = repo.runIntegrityCheck()
        assertEquals(1, integrity.checkedCount)
        assertEquals(1, integrity.healthyCount)
        assertTrue(integrity.missingFiles.isEmpty())
        assertTrue(integrity.corruptedFiles.isEmpty())

        // Safe cache clear deletes cacheDir file but keeps vault media intact
        val tempCache = File(context.cacheDir, "stale_temp.tmp").apply { writeText("temp") }
        assertTrue(tempCache.exists())
        repo.clearSafeCache()
        assertFalse(tempCache.exists())
        assertTrue(repo.mediaFileFor(imported).exists())

        // Deleting album keeps media in vault
        repo.deleteAlbum(album.id)
        val allAfterAlbumDelete = repo.observeAllMedia().first()
        assertEquals(1, allAfterAlbumDelete.size)
        assertEquals(null, allAfterAlbumDelete.first().albumId)
    }

    @Test
    fun `failed import recovery removes orphaned temp files without harming valid media`() = runTest {
        val validSrc = File(context.cacheDir, "valid_src.bin").apply { writeBytes(ByteArray(1024) { 7 }) }
        val batch = repo.importMediaUris(listOf(Uri.fromFile(validSrc)))
        val validItem = batch.importedItems.first()

        val orphanFile = File(repo.vaultMediaDir, "orphan_interrupted.ovm").apply { writeText("corrupt") }
        assertTrue(orphanFile.exists())

        val reportBefore = repo.computeStorageReport()
        assertEquals(1, reportBefore.orphanedTempFilesCount)

        val cleaned = repo.recoverFailedImports()
        assertTrue(cleaned >= 1)
        assertFalse(orphanFile.exists())
        assertTrue(repo.mediaFileFor(validItem).exists())
    }

    @Test
    fun `encrypted backup export and restore roundtrip preserves albums and media`() = runTest {
        val src = File(context.cacheDir, "secret_doc.jpg").apply { writeBytes(ByteArray(2048) { 42 }) }
        val album = repo.createAlbum("Documents")
        repo.importMediaUris(listOf(Uri.fromFile(src)), targetAlbumId = album.id)

        val backupFile = File(context.cacheDir, "test_backup.ovbackup")
        val exportRes = repo.exportEncryptedBackup(Uri.fromFile(backupFile), "test_backup.ovbackup", "VaultPass123")
        assertTrue(exportRes.isSuccess)
        assertTrue(backupFile.exists())

        // Wrong password fails cleanly
        val wrongInspect = repo.inspectEncryptedBackup(Uri.fromFile(backupFile), "WrongPass")
        assertTrue(wrongInspect.isFailure)
        assertTrue(wrongInspect.exceptionOrNull() is WrongBackupPasswordException)

        // Wipe vault and restore from encrypted backup
        repo.wipeEntireVaultMedia()
        assertEquals(0, repo.observeAllMedia().first().size)

        val goodInspect = repo.inspectEncryptedBackup(Uri.fromFile(backupFile), "VaultPass123")
        assertTrue(goodInspect.isSuccess)
        val preview = goodInspect.getOrThrow()
        assertEquals(1, preview.albumCount)
        assertEquals(1, preview.photoCount)

        val restoreRes = repo.applyBackupPreview(preview, "test_backup.ovbackup", replaceExisting = true)
        assertTrue(restoreRes.isSuccess)

        val restoredMedia = repo.observeAllMedia().first()
        val restoredAlbums = repo.observeAllAlbums().first()
        assertEquals(1, restoredMedia.size)
        assertEquals(1, restoredAlbums.size)
        assertNotNull(restoredMedia.first().albumId)
        assertTrue(repo.mediaFileFor(restoredMedia.first()).exists())
    }

    @Test
    fun `pin verification and recovery reset in UserPreferencesRepository`() = runTest {
        val recCode = crypto.generateRecoveryCode()
        prefsRepo.configurePinAndRecovery("1357", recCode)
        assertTrue(prefsRepo.verifyPin("1357"))
        assertFalse(prefsRepo.verifyPin("9999"))
        assertTrue(prefsRepo.verifyRecoveryCode(recCode.lowercase()))

        val (attempts, _) = prefsRepo.recordFailedPinAttempt()
        assertEquals(1, attempts)
        prefsRepo.resetFailedPinAttempts()
        assertEquals(0, prefsRepo.getSnapshot().failedAttempts)
    }
}
