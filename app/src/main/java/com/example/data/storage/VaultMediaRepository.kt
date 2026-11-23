package com.example.data.storage

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import coil.Coil
import com.example.data.db.AlbumEntity
import com.example.data.db.AlbumWithStats
import com.example.data.db.BackupRecordEntity
import com.example.data.db.ImportStatus
import com.example.data.db.MediaType
import com.example.data.db.VaultDao
import com.example.data.db.VaultMediaEntity
import com.example.security.CorruptedBackupException
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class InsufficientStorageException(message: String) : Exception(message)

data class ImportProgress(
    val currentIndex: Int,
    val totalCount: Int,
    val currentFileName: String,
    val completedCount: Int,
    val duplicateCount: Int,
    val failedCount: Int
)

data class ImportBatchResult(
    val importedItems: List<VaultMediaEntity>,
    val importedSourceUris: List<Uri>,
    val duplicateFileNames: List<String>,
    val failedItems: List<Pair<String, String>>
)

data class StorageReport(
    val totalVaultBytes: Long,
    val photoBytes: Long,
    val videoBytes: Long,
    val thumbnailBytes: Long,
    val cacheBytes: Long,
    val availableDeviceBytes: Long,
    val totalDeviceBytes: Long,
    val photoCount: Int,
    val videoCount: Int,
    val failedImportCount: Int,
    val orphanedTempFilesCount: Int
)

data class IntegrityReport(
    val checkedCount: Int,
    val healthyCount: Int,
    val missingFiles: List<VaultMediaEntity>,
    val corruptedFiles: List<VaultMediaEntity>
)

data class DeviceMediaCandidate(
    val uri: Uri,
    val mediaType: MediaType,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateAddedSec: Long
)

@Serializable
data class BackupAlbumEntry(
    val id: String,
    val name: String,
    val description: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Serializable
data class BackupMediaEntry(
    val id: String,
    val albumId: String? = null,
    val mediaType: String,
    val originalFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sha256Hash: String,
    val isFavorite: Boolean,
    val createdAtEpochMs: Long,
    val importedAtEpochMs: Long,
    val zipMediaPath: String,
    val zipThumbPath: String? = null
)

@Serializable
data class BackupManifest(
    val formatVersion: Int = 1,
    val createdAtEpochMs: Long,
    val albums: List<BackupAlbumEntry>,
    val media: List<BackupMediaEntry>
)

data class BackupPreview(
    val createdAtEpochMs: Long,
    val albumCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val totalMediaBytes: Long,
    val decryptedZipFile: File,
    val manifest: BackupManifest
)

class VaultMediaRepository(
    private val context: Context,
    private val dao: VaultDao,
    private val cryptoManager: VaultCryptoManager
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val vaultMediaDir: File by lazy {
        File(context.filesDir, "vault_media").apply {
            mkdirs()
            File(this, ".nomedia").apply { if (!exists()) createNewFile() }
        }
    }

    val vaultThumbsDir: File by lazy {
        File(context.filesDir, "vault_thumbs").apply {
            mkdirs()
            File(this, ".nomedia").apply { if (!exists()) createNewFile() }
        }
    }

    fun observeAllMedia(): Flow<List<VaultMediaEntity>> = dao.observeCompletedMedia()
    fun observeFailedMedia(): Flow<List<VaultMediaEntity>> = dao.observeFailedOrIncompleteMedia()
    fun observeMediaInAlbum(albumId: String): Flow<List<VaultMediaEntity>> = dao.observeMediaInAlbum(albumId)
    fun observeAlbumsWithStats(): Flow<List<AlbumWithStats>> = dao.observeAlbumsWithStats()
    fun observeAllAlbums(): Flow<List<AlbumEntity>> = dao.observeAllAlbums()
    fun observeBackupRecords(): Flow<List<BackupRecordEntity>> = dao.observeBackupRecords()

    suspend fun getMediaById(id: String): VaultMediaEntity? = dao.getMediaById(id)

    fun mediaFileFor(item: VaultMediaEntity): File = File(vaultMediaDir, item.vaultFileName)
    fun thumbFileFor(item: VaultMediaEntity): File? =
        item.thumbnailFileName?.let { File(vaultThumbsDir, it) }

    suspend fun createAlbum(name: String, description: String = ""): AlbumEntity {
        val now = System.currentTimeMillis()
        val album = AlbumEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        dao.insertAlbum(album)
        return album
    }

    suspend fun renameAlbum(albumId: String, newName: String, newDescription: String? = null) {
        val existing = dao.getAlbumById(albumId) ?: return
        dao.updateAlbum(
            existing.copy(
                name = newName.trim(),
                description = newDescription?.trim() ?: existing.description,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteAlbum(albumId: String) {
        dao.deleteAlbumById(albumId)
    }

    suspend fun moveMediaToAlbum(mediaIds: List<String>, targetAlbumId: String?) {
        if (mediaIds.isEmpty()) return
        dao.moveMediaToAlbum(mediaIds, targetAlbumId)
    }

    suspend fun toggleFavorite(mediaId: String) {
        val item = dao.getMediaById(mediaId) ?: return
        dao.setFavorite(mediaId, !item.isFavorite)
    }

    suspend fun setCustomAvatarFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val avatarFile = File(context.filesDir, "profile_avatar_custom.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val rawBytes = input.readBytes()
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
            val sample = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 512, 512)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                ?: throw IllegalStateException("Could not decode selected profile image.")
            FileOutputStream(avatarFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            bmp.recycle()
        } ?: throw IllegalStateException("Unable to open selected image.")
        avatarFile.absolutePath
    }

    /**
     * Queries MediaStore for device photos or videos accessible under the app's current OS grant
     * (Full Access or Android 14+ Selected Photos/Videos Limited Access).
     */
    suspend fun queryDeviceMedia(mediaType: MediaType, limit: Int = 250): List<DeviceMediaCandidate> =
        withContext(Dispatchers.IO) {
            val collection = if (mediaType == MediaType.VIDEO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }

            val projection = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.DATE_ADDED)
                if (mediaType == MediaType.VIDEO) {
                    add(MediaStore.Video.VideoColumns.DURATION)
                }
            }.toTypedArray()

            val results = mutableListOf<DeviceMediaCandidate>()
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val durCol = if (mediaType == MediaType.VIDEO) {
                        cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                    } else -1

                    while (cursor.moveToNext() && results.size < limit) {
                        if (idCol < 0) continue
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(collection, id)
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "media_$id" else "media_$id"
                        val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                        val dateAdded = if (dateCol >= 0 && !cursor.isNull(dateCol)) cursor.getLong(dateCol) else 0L
                        val dur = if (durCol >= 0 && !cursor.isNull(durCol)) cursor.getLong(durCol) else 0L
                        results.add(
                            DeviceMediaCandidate(
                                uri = contentUri,
                                mediaType = mediaType,
                                displayName = name,
                                sizeBytes = size,
                                durationMs = dur,
                                dateAddedSec = dateAdded
                            )
                        )
                    }
                }
            }
            results
        }

    /**
     * Imports user-selected URIs into encrypted app-private storage (`vault_media` + `vault_thumbs`).
     * - Checks available internal storage before writing.
     * - Detects duplicates by SHA-256 hash unless [allowDuplicates] is true.
     * - Verifies encrypted file integrity before marking [ImportStatus.COMPLETED].
     */
    suspend fun importMediaUris(
        uris: List<Uri>,
        targetAlbumId: String? = null,
        allowDuplicates: Boolean = false,
        onProgress: (ImportProgress) -> Unit = {}
    ): ImportBatchResult = withContext(Dispatchers.IO) {
        val imported = mutableListOf<VaultMediaEntity>()
        val importedUris = mutableListOf<Uri>()
        val duplicates = mutableListOf<String>()
        val failures = mutableListOf<Pair<String, String>>()

        uris.forEachIndexed { index, uri ->
            val meta = queryUriMetadata(uri)
            onProgress(
                ImportProgress(
                    currentIndex = index + 1,
                    totalCount = uris.size,
                    currentFileName = meta.displayName,
                    completedCount = imported.size,
                    duplicateCount = duplicates.size,
                    failedCount = failures.size
                )
            )

            val mediaId = UUID.randomUUID().toString()
            val vaultFileName = "$mediaId.ovm"
            val thumbFileName = "$mediaId.ovt"
            val destFile = File(vaultMediaDir, vaultFileName)
            val thumbFile = File(vaultThumbsDir, thumbFileName)

            val isVideo = meta.mimeType.startsWith("video/")
            val mediaType = if (isVideo) MediaType.VIDEO else MediaType.PHOTO

            // Reserve 10 MB safety cushion
            val available = getAvailableInternalBytes()
            if (meta.reportedSize > 0 && meta.reportedSize + 10L * 1024L * 1024L > available) {
                val msg = "Insufficient device storage for ${meta.displayName}"
                failures.add(meta.displayName to msg)
                dao.insertMedia(
                    VaultMediaEntity(
                        id = mediaId,
                        albumId = targetAlbumId,
                        mediaType = mediaType,
                        originalFileName = meta.displayName,
                        vaultFileName = vaultFileName,
                        thumbnailFileName = null,
                        mimeType = meta.mimeType,
                        sizeBytes = meta.reportedSize,
                        sha256Hash = "",
                        originalUriString = uri.toString(),
                        importStatus = ImportStatus.FAILED,
                        failureReason = msg
                    )
                )
                return@forEachIndexed
            }

            // Record IN_PROGRESS first for crash/interruption recoverability
            val pendingEntity = VaultMediaEntity(
                id = mediaId,
                albumId = targetAlbumId,
                mediaType = mediaType,
                originalFileName = meta.displayName,
                vaultFileName = vaultFileName,
                thumbnailFileName = null,
                mimeType = meta.mimeType,
                sizeBytes = meta.reportedSize,
                sha256Hash = "",
                originalUriString = uri.toString(),
                importStatus = ImportStatus.IN_PROGRESS
            )
            dao.insertMedia(pendingEntity)

            try {
                val writeResult = context.contentResolver.openInputStream(uri)?.use { stream ->
                    cryptoManager.encryptStreamToFile(stream, destFile)
                } ?: throw IllegalStateException("Cannot open source media stream.")

                if (writeResult.plaintextBytes <= 0L) {
                    throw IllegalStateException("Selected media file is empty.")
                }

                // Duplicate detection by plaintext SHA-256
                if (!allowDuplicates) {
                    val existing = dao.findCompletedByHash(writeResult.sha256Hex)
                    if (existing != null) {
                        destFile.delete()
                        dao.deleteMediaByIds(listOf(mediaId))
                        duplicates.add(meta.displayName)
                        return@forEachIndexed
                    }
                }

                // Verify copied encrypted file decrypts cleanly and matches hash/size
                val verifyResult = cryptoManager.computePlaintextSha256(destFile)
                if (verifyResult.plaintextBytes != writeResult.plaintextBytes ||
                    verifyResult.sha256Hex != writeResult.sha256Hex
                ) {
                    throw IllegalStateException("Post-import verification failed.")
                }

                // Extract dimensions, duration & generate encrypted thumbnail
                val extracted = extractDimensionsAndCreateThumb(
                    uri = uri,
                    mediaFile = destFile,
                    mediaType = mediaType,
                    thumbDest = thumbFile
                )

                val completedEntity = pendingEntity.copy(
                    thumbnailFileName = if (extracted.hasThumb) thumbFileName else null,
                    sizeBytes = writeResult.plaintextBytes,
                    width = extracted.width,
                    height = extracted.height,
                    durationMs = extracted.durationMs,
                    sha256Hash = writeResult.sha256Hex,
                    importStatus = ImportStatus.COMPLETED,
                    importedAtEpochMs = System.currentTimeMillis(),
                    failureReason = null
                )
                dao.updateMedia(completedEntity)
                imported.add(completedEntity)
                importedUris.add(uri)
            } catch (e: Exception) {
                destFile.delete()
                thumbFile.delete()
                val reason = e.message ?: "Import interrupted"
                dao.updateMedia(
                    pendingEntity.copy(
                        importStatus = ImportStatus.FAILED,
                        failureReason = reason
                    )
                )
                failures.add(meta.displayName to reason)
            }
        }

        ImportBatchResult(
            importedItems = imported,
            importedSourceUris = importedUris,
            duplicateFileNames = duplicates,
            failedItems = failures
        )
    }

    /**
     * Builds an official Android system confirmation IntentSender to delete original device gallery media.
     * Never silently deletes public media.
     */
    fun buildDeleteOriginalsIntentSender(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
            }.getOrNull()
        } else {
            null
        }
    }

    /**
     * For Android 10 (API 29) or below where createDeleteRequest is unavailable:
     * attempts ContentResolver.delete and catches RecoverableSecurityException to return its IntentSender.
     */
    fun deleteOriginalPreR(uri: Uri): Pair<Boolean, IntentSender?> {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            (rows > 0) to null
        } catch (sec: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                false to sec.userAction.actionIntent.intentSender
            } else {
                false to null
            }
        } catch (_: Exception) {
            false to null
        }
    }

    /**
     * Restores vault items back to the public Android gallery via MediaStore.
     */
    suspend fun restoreMediaToGallery(
        items: List<VaultMediaEntity>,
        removeVaultCopyAfterRestore: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var restoredCount = 0
            val resolver = context.contentResolver

            for (item in items) {
                val sourceFile = mediaFileFor(item)
                if (!sourceFile.exists()) continue

                val collection = if (item.mediaType == MediaType.VIDEO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relDir = if (item.mediaType == MediaType.VIDEO) {
                            Environment.DIRECTORY_MOVIES + "/ObsidianVault"
                        } else {
                            Environment.DIRECTORY_PICTURES + "/ObsidianVault"
                        }
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relDir)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val destUri = resolver.insert(collection, values)
                    ?: throw IllegalStateException("MediaStore refused restore entry for ${item.originalFileName}")

                try {
                    resolver.openOutputStream(destUri)?.use { out ->
                        cryptoManager.openDecryptedInputStream(sourceFile).use { input ->
                            input.copyTo(out, 64 * 1024)
                        }
                        out.flush()
                    } ?: throw IllegalStateException("Cannot open gallery output stream.")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                        resolver.update(destUri, done, null, null)
                    }
                    restoredCount++

                    if (removeVaultCopyAfterRestore) {
                        deleteVaultItems(listOf(item))
                    }
                } catch (e: Exception) {
                    runCatching { resolver.delete(destUri, null, null) }
                    throw e
                }
            }
            restoredCount
        }
    }

    suspend fun deleteVaultItems(items: List<VaultMediaEntity>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        items.forEach { item ->
            mediaFileFor(item).delete()
            thumbFileFor(item)?.delete()
        }
        dao.deleteMediaByIds(items.map { it.id })
    }

    suspend fun computeStorageReport(): StorageReport = withContext(Dispatchers.IO) {
        val completed = dao.getAllCompletedMediaOnce()
        val failed = dao.getFailedOrIncompleteMediaOnce()

        var photoBytes = 0L
        var videoBytes = 0L
        var photoCount = 0
        var videoCount = 0

        completed.forEach { item ->
            val diskLen = mediaFileFor(item).takeIf { it.exists() }?.length() ?: item.sizeBytes
            if (item.mediaType == MediaType.PHOTO) {
                photoBytes += diskLen
                photoCount++
            } else {
                videoBytes += diskLen
                videoCount++
            }
        }

        val thumbBytes = vaultThumbsDir.listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" }
            ?.sumOf { it.length() } ?: 0L

        val cacheBytes = directorySize(context.cacheDir) + (context.externalCacheDir?.let { directorySize(it) } ?: 0L)

        val referencedFiles = completed.map { it.vaultFileName }.toSet()
        val orphanCount = vaultMediaDir.listFiles()
            ?.count { it.isFile && it.name != ".nomedia" && it.name !in referencedFiles } ?: 0

        val stat = StatFs(context.filesDir.absolutePath)
        StorageReport(
            totalVaultBytes = photoBytes + videoBytes + thumbBytes,
            photoBytes = photoBytes,
            videoBytes = videoBytes,
            thumbnailBytes = thumbBytes,
            cacheBytes = cacheBytes,
            availableDeviceBytes = stat.availableBytes,
            totalDeviceBytes = stat.totalBytes,
            photoCount = photoCount,
            videoCount = videoCount,
            failedImportCount = failed.size,
            orphanedTempFilesCount = orphanCount
        )
    }

    /**
     * Safely clears ONLY temporary/cache directories and Coil memory cache.
     * NEVER deletes vault_media, vault_thumbs, or databases.
     */
    suspend fun clearSafeCache(): Long = withContext(Dispatchers.IO) {
        val before = directorySize(context.cacheDir) + (context.externalCacheDir?.let { directorySize(it) } ?: 0L)
        deleteChildrenOnly(context.cacheDir)
        context.externalCacheDir?.let { deleteChildrenOnly(it) }
        runCatching {
            Coil.imageLoader(context).memoryCache?.clear()
        }
        before
    }

    suspend fun runIntegrityCheck(): IntegrityReport = withContext(Dispatchers.IO) {
        val completed = dao.getAllCompletedMediaOnce()
        var healthy = 0
        val missing = mutableListOf<VaultMediaEntity>()
        val corrupted = mutableListOf<VaultMediaEntity>()

        for (item in completed) {
            val file = mediaFileFor(item)
            if (!file.exists() || file.length() <= 16L) {
                missing.add(item)
                continue
            }
            val verified = runCatching {
                val res = cryptoManager.computePlaintextSha256(file)
                res.sha256Hex == item.sha256Hash && res.plaintextBytes == item.sizeBytes
            }.getOrDefault(false)

            if (verified) healthy++ else corrupted.add(item)
        }

        IntegrityReport(
            checkedCount = completed.size,
            healthyCount = healthy,
            missingFiles = missing,
            corruptedFiles = corrupted
        )
    }

    suspend fun recoverFailedImports(): Int = withContext(Dispatchers.IO) {
        val completed = dao.getAllCompletedMediaOnce()
        val validMediaNames = completed.map { it.vaultFileName }.toSet()
        val validThumbNames = completed.mapNotNull { it.thumbnailFileName }.toSet()
        var cleaned = 0

        vaultMediaDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name != ".nomedia" && f.name !in validMediaNames) {
                if (f.delete()) cleaned++
            }
        }
        vaultThumbsDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name != ".nomedia" && f.name !in validThumbNames) {
                if (f.delete()) cleaned++
            }
        }
        val failedCount = dao.getFailedOrIncompleteMediaOnce().size
        dao.deleteFailedOrIncompleteMedia()
        cleaned + failedCount
    }

    /**
     * Exports all albums, metadata, and decrypted media into a password-encrypted `.ovbackup` archive.
     */
    suspend fun exportEncryptedBackup(
        destUri: Uri,
        destFileName: String,
        password: String
    ): Result<BackupRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val albums = dao.getAllAlbumsOnce()
            val mediaList = dao.getAllCompletedMediaOnce()

            val tempZip = File.createTempFile("ov_export_", ".zip", context.cacheDir)
            try {
                val manifestMedia = mutableListOf<BackupMediaEntry>()
                ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
                    for (item in mediaList) {
                        val src = mediaFileFor(item)
                        if (!src.exists()) continue

                        val zipMediaPath = "media/${item.id}_${sanitizeFileName(item.originalFileName)}"
                        zos.putNextEntry(ZipEntry(zipMediaPath))
                        cryptoManager.openDecryptedInputStream(src).use { it.copyTo(zos, 64 * 1024) }
                        zos.closeEntry()

                        var zipThumbPath: String? = null
                        val thumbSrc = thumbFileFor(item)
                        if (thumbSrc != null && thumbSrc.exists()) {
                            zipThumbPath = "thumbs/${item.id}.jpg"
                            zos.putNextEntry(ZipEntry(zipThumbPath))
                            cryptoManager.openDecryptedInputStream(thumbSrc).use { it.copyTo(zos, 32 * 1024) }
                            zos.closeEntry()
                        }

                        manifestMedia.add(
                            BackupMediaEntry(
                                id = item.id,
                                albumId = item.albumId,
                                mediaType = item.mediaType.name,
                                originalFileName = item.originalFileName,
                                mimeType = item.mimeType,
                                sizeBytes = item.sizeBytes,
                                width = item.width,
                                height = item.height,
                                durationMs = item.durationMs,
                                sha256Hash = item.sha256Hash,
                                isFavorite = item.isFavorite,
                                createdAtEpochMs = item.createdAtEpochMs,
                                importedAtEpochMs = item.importedAtEpochMs,
                                zipMediaPath = zipMediaPath,
                                zipThumbPath = zipThumbPath
                            )
                        )
                    }

                    val manifest = BackupManifest(
                        createdAtEpochMs = System.currentTimeMillis(),
                        albums = albums.map {
                            BackupAlbumEntry(
                                id = it.id,
                                name = it.name,
                                description = it.description,
                                createdAtEpochMs = it.createdAtEpochMs,
                                updatedAtEpochMs = it.updatedAtEpochMs
                            )
                        },
                        media = manifestMedia
                    )
                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                val (checksum, encryptedSize) = context.contentResolver.openOutputStream(destUri)?.use { out ->
                    cryptoManager.encryptBackupFileToStream(tempZip, password, out)
                } ?: throw IllegalStateException("Unable to open backup destination.")

                val record = BackupRecordEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = destFileName,
                    createdAtEpochMs = System.currentTimeMillis(),
                    sizeBytes = encryptedSize,
                    itemCount = manifestMedia.size,
                    albumCount = albums.size,
                    checksumSha256 = checksum,
                    operationType = "EXPORT"
                )
                dao.insertBackupRecord(record)
                record
            } finally {
                tempZip.delete()
            }
        }
    }

    /**
     * Decrypts and validates a `.ovbackup` archive into a temporary inspected preview before touching any vault data.
     */
    suspend fun inspectEncryptedBackup(
        sourceUri: Uri,
        password: String
    ): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val tempEnc = File.createTempFile("ov_import_enc_", ".ovbackup", context.cacheDir)
            val tempZip = File.createTempFile("ov_import_dec_", ".zip", context.cacheDir)
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(tempEnc).use { input.copyTo(it, 64 * 1024) }
                } ?: throw IllegalStateException("Cannot open selected backup file.")

                cryptoManager.decryptBackupFile(tempEnc, password, tempZip)

                var manifestJson: String? = null
                ZipInputStream(FileInputStream(tempZip)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "manifest.json") {
                            manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
                val manifestStr = manifestJson
                    ?: throw CorruptedBackupException("Backup manifest.json is missing.")
                val manifest = runCatching { json.decodeFromString<BackupManifest>(manifestStr) }
                    .getOrElse { throw CorruptedBackupException("Backup manifest is corrupted.", it) }

                val photoCount = manifest.media.count { it.mediaType == MediaType.PHOTO.name }
                val videoCount = manifest.media.count { it.mediaType == MediaType.VIDEO.name }
                val totalBytes = manifest.media.sumOf { it.sizeBytes }

                BackupPreview(
                    createdAtEpochMs = manifest.createdAtEpochMs,
                    albumCount = manifest.albums.size,
                    photoCount = photoCount,
                    videoCount = videoCount,
                    totalMediaBytes = totalBytes,
                    decryptedZipFile = tempZip,
                    manifest = manifest
                )
            } catch (t: Throwable) {
                tempZip.delete()
                throw t
            } finally {
                tempEnc.delete()
            }
        }
    }

    /**
     * Applies a verified [BackupPreview] to the vault.
     * Supports Merge (keeps existing items & adds missing) or Replace (clears existing only after all files extract cleanly).
     */
    suspend fun applyBackupPreview(
        preview: BackupPreview,
        sourceFileName: String,
        replaceExisting: Boolean
    ): Result<BackupRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val extractDir = File(context.cacheDir, "ov_restore_${UUID.randomUUID()}").apply { mkdirs() }
            val createdVaultFiles = mutableListOf<File>()
            try {
                // Stage 1: Unzip entries safely into extractDir (guarding against zip-slip)
                ZipInputStream(FileInputStream(preview.decryptedZipFile)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = File(extractDir, entry.name)
                            if (!outFile.canonicalPath.startsWith(extractDir.canonicalPath)) {
                                throw SecurityException("Blocked unsafe ZIP path: ${entry.name}")
                            }
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { zis.copyTo(it, 64 * 1024) }
                        }
                        entry = zis.nextEntry
                    }
                }

                // Stage 2: Encrypt & verify every media file into vault_media before touching DB
                val newAlbums = preview.manifest.albums.map {
                    AlbumEntity(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        createdAtEpochMs = it.createdAtEpochMs,
                        updatedAtEpochMs = it.updatedAtEpochMs
                    )
                }

                val restoredMedia = mutableListOf<VaultMediaEntity>()
                for (entry in preview.manifest.media) {
                    if (!replaceExisting && dao.findCompletedByHash(entry.sha256Hash) != null) {
                        continue
                    }
                    val plainMedia = File(extractDir, entry.zipMediaPath)
                    if (!plainMedia.exists()) {
                        throw CorruptedBackupException("Missing media payload in backup: ${entry.originalFileName}")
                    }
                    val newId = if (replaceExisting) entry.id else UUID.randomUUID().toString()
                    val vaultFileName = "$newId.ovm"
                    val vaultDest = File(vaultMediaDir, vaultFileName)
                    val writeRes = FileInputStream(plainMedia).use {
                        cryptoManager.encryptStreamToFile(it, vaultDest)
                    }
                    createdVaultFiles.add(vaultDest)

                    if (entry.sha256Hash.isNotEmpty() && writeRes.sha256Hex != entry.sha256Hash) {
                        throw CorruptedBackupException("Checksum mismatch for ${entry.originalFileName}")
                    }

                    var thumbFileName: String? = null
                    val plainThumb = entry.zipThumbPath?.let { File(extractDir, it) }
                    if (plainThumb != null && plainThumb.exists()) {
                        thumbFileName = "$newId.ovt"
                        val thumbDest = File(vaultThumbsDir, thumbFileName)
                        FileInputStream(plainThumb).use { cryptoManager.encryptStreamToFile(it, thumbDest) }
                        createdVaultFiles.add(thumbDest)
                    }

                    restoredMedia.add(
                        VaultMediaEntity(
                            id = newId,
                            albumId = entry.albumId,
                            mediaType = runCatching { MediaType.valueOf(entry.mediaType) }.getOrDefault(MediaType.PHOTO),
                            originalFileName = entry.originalFileName,
                            vaultFileName = vaultFileName,
                            thumbnailFileName = thumbFileName,
                            mimeType = entry.mimeType,
                            sizeBytes = writeRes.plaintextBytes,
                            width = entry.width,
                            height = entry.height,
                            durationMs = entry.durationMs,
                            sha256Hash = writeRes.sha256Hex,
                            importStatus = ImportStatus.COMPLETED,
                            isFavorite = entry.isFavorite,
                            createdAtEpochMs = entry.createdAtEpochMs,
                            importedAtEpochMs = entry.importedAtEpochMs
                        )
                    )
                }

                // Stage 3: Atomically update Room database
                if (replaceExisting) {
                    val oldItems = dao.getAllCompletedMediaOnce()
                    dao.replaceAllVaultData(newAlbums, restoredMedia)
                    val keepFiles = createdVaultFiles.map { it.name }.toSet()
                    oldItems.forEach { old ->
                        if (old.vaultFileName !in keepFiles) mediaFileFor(old).delete()
                        old.thumbnailFileName?.let { t ->
                            if (t !in keepFiles) File(vaultThumbsDir, t).delete()
                        }
                    }
                } else {
                    val existingAlbumIds = dao.getAllAlbumsOnce().map { it.id }.toSet()
                    val missingAlbums = newAlbums.filter { it.id !in existingAlbumIds }
                    if (missingAlbums.isNotEmpty()) dao.insertAlbums(missingAlbums)
                    if (restoredMedia.isNotEmpty()) dao.insertMediaList(restoredMedia)
                }

                val record = BackupRecordEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = sourceFileName,
                    createdAtEpochMs = System.currentTimeMillis(),
                    sizeBytes = preview.totalMediaBytes,
                    itemCount = restoredMedia.size,
                    albumCount = newAlbums.size,
                    checksumSha256 = preview.manifest.media.firstOrNull()?.sha256Hash?.take(16) ?: "verified",
                    operationType = "RESTORE"
                )
                dao.insertBackupRecord(record)
                record
            } catch (t: Throwable) {
                createdVaultFiles.forEach { it.delete() }
                throw t
            } finally {
                extractDir.deleteRecursively()
                preview.decryptedZipFile.delete()
            }
        }
    }

    suspend fun wipeEntireVaultMedia() = withContext(Dispatchers.IO) {
        dao.deleteAllMedia()
        dao.deleteAllAlbums()
        deleteChildrenOnly(vaultMediaDir)
        deleteChildrenOnly(vaultThumbsDir)
        File(vaultMediaDir, ".nomedia").apply { if (!exists()) createNewFile() }
        File(vaultThumbsDir, ".nomedia").apply { if (!exists()) createNewFile() }
    }

    private data class UriMeta(
        val displayName: String,
        val mimeType: String,
        val reportedSize: Long
    )

    private fun queryUriMetadata(uri: Uri): UriMeta {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = "vault_item_${System.currentTimeMillis()}"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        return UriMeta(name, mime, size)
    }

    private data class ExtractedDimensions(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val hasThumb: Boolean
    )

    private fun extractDimensionsAndCreateThumb(
        uri: Uri,
        mediaFile: File,
        mediaType: MediaType,
        thumbDest: File
    ): ExtractedDimensions {
        return if (mediaType == MediaType.VIDEO) {
            var width = 0
            var height = 0
            var duration = 0L
            var hasThumb = false
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime()
                if (frame != null) {
                    val thumbBmp = scaleBitmapMax(frame, 480)
                    val baos = ByteArrayOutputStream()
                    thumbBmp.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                    cryptoManager.encryptBytesToFile(baos.toByteArray(), thumbDest)
                    hasThumb = true
                    if (thumbBmp !== frame) thumbBmp.recycle()
                    frame.recycle()
                }
            } catch (_: Exception) {
                // Fallback safely if retriever fails on exotic codec
            } finally {
                runCatching { retriever.release() }
            }
            ExtractedDimensions(width, height, duration, hasThumb)
        } else {
            var width = 0
            var height = 0
            var hasThumb = false
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                cryptoManager.openDecryptedInputStream(mediaFile).use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                width = bounds.outWidth.coerceAtLeast(0)
                height = bounds.outHeight.coerceAtLeast(0)

                val sample = calculateInSampleSize(width, height, 480, 480)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = cryptoManager.openDecryptedInputStream(mediaFile).use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
                if (bmp != null) {
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                    cryptoManager.encryptBytesToFile(baos.toByteArray(), thumbDest)
                    hasThumb = true
                    bmp.recycle()
                }
            }
            ExtractedDimensions(width, height, 0L, hasThumb)
        }
    }

    private fun scaleBitmapMax(source: Bitmap, maxDim: Int): Bitmap {
        val ratio = minOf(maxDim.toFloat() / source.width, maxDim.toFloat() / source.height)
        if (ratio >= 1f) return source
        val targetW = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun getAvailableInternalBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBytes
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        dir.walkTopDown().forEach { if (it.isFile) sum += it.length() }
        return sum
    }

    private fun deleteChildrenOnly(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
}
