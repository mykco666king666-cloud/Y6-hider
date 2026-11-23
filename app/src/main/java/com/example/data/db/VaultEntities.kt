package com.example.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class MediaType {
    PHOTO,
    VIDEO
}

enum class ImportStatus {
    COMPLETED,
    IN_PROGRESS,
    FAILED
}

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val coverMediaId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vault_media",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("albumId"),
        Index("sha256Hash"),
        Index("mediaType"),
        Index("importStatus")
    ]
)
data class VaultMediaEntity(
    @PrimaryKey val id: String,
    val albumId: String? = null,
    val mediaType: MediaType,
    val originalFileName: String,
    val vaultFileName: String,
    val thumbnailFileName: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val sha256Hash: String,
    val originalUriString: String? = null,
    val importStatus: ImportStatus = ImportStatus.COMPLETED,
    val isFavorite: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val importedAtEpochMs: Long = System.currentTimeMillis(),
    val failureReason: String? = null
)

@Entity(tableName = "backup_records")
data class BackupRecordEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val createdAtEpochMs: Long,
    val sizeBytes: Long,
    val itemCount: Int,
    val albumCount: Int,
    val checksumSha256: String,
    val operationType: String // "EXPORT" or "RESTORE"
)

data class AlbumWithStats(
    @Embedded val album: AlbumEntity,
    val itemCount: Int,
    val totalSizeBytes: Long,
    val latestThumbFileName: String?,
    val latestMediaId: String?
)

class VaultConverters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.PHOTO)

    @TypeConverter
    fun fromImportStatus(value: ImportStatus): String = value.name

    @TypeConverter
    fun toImportStatus(value: String): ImportStatus =
        runCatching { ImportStatus.valueOf(value) }.getOrDefault(ImportStatus.FAILED)
}
