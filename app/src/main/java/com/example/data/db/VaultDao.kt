package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_media WHERE importStatus = 'COMPLETED' ORDER BY importedAtEpochMs DESC")
    fun observeCompletedMedia(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE importStatus != 'COMPLETED' ORDER BY importedAtEpochMs DESC")
    fun observeFailedOrIncompleteMedia(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE importStatus = 'COMPLETED' AND albumId = :albumId ORDER BY importedAtEpochMs DESC")
    fun observeMediaInAlbum(albumId: String): Flow<List<VaultMediaEntity>>

    @Query(
        """
        SELECT a.*,
               COUNT(CASE WHEN m.importStatus = 'COMPLETED' THEN 1 END) AS itemCount,
               COALESCE(SUM(CASE WHEN m.importStatus = 'COMPLETED' THEN m.sizeBytes ELSE 0 END), 0) AS totalSizeBytes,
               (SELECT m2.thumbnailFileName FROM vault_media m2
                WHERE m2.albumId = a.id AND m2.importStatus = 'COMPLETED'
                ORDER BY m2.importedAtEpochMs DESC LIMIT 1) AS latestThumbFileName,
               (SELECT m3.id FROM vault_media m3
                WHERE m3.albumId = a.id AND m3.importStatus = 'COMPLETED'
                ORDER BY m3.importedAtEpochMs DESC LIMIT 1) AS latestMediaId
        FROM albums a
        LEFT JOIN vault_media m ON m.albumId = a.id
        GROUP BY a.id
        ORDER BY a.updatedAtEpochMs DESC
        """
    )
    fun observeAlbumsWithStats(): Flow<List<AlbumWithStats>>

    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun observeAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM backup_records ORDER BY createdAtEpochMs DESC")
    fun observeBackupRecords(): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM vault_media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): VaultMediaEntity?

    @Query("SELECT * FROM vault_media WHERE id IN (:ids)")
    suspend fun getMediaByIds(ids: List<String>): List<VaultMediaEntity>

    @Query("SELECT * FROM vault_media WHERE sha256Hash = :sha256Hash AND importStatus = 'COMPLETED' LIMIT 1")
    suspend fun findCompletedByHash(sha256Hash: String): VaultMediaEntity?

    @Query("SELECT * FROM vault_media WHERE importStatus = 'COMPLETED'")
    suspend fun getAllCompletedMediaOnce(): List<VaultMediaEntity>

    @Query("SELECT * FROM vault_media WHERE importStatus != 'COMPLETED'")
    suspend fun getFailedOrIncompleteMediaOnce(): List<VaultMediaEntity>

    @Query("SELECT * FROM albums")
    suspend fun getAllAlbumsOnce(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: VaultMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(items: List<VaultMediaEntity>)

    @Update
    suspend fun updateMedia(item: VaultMediaEntity)

    @Query("UPDATE vault_media SET albumId = :albumId WHERE id IN (:mediaIds)")
    suspend fun moveMediaToAlbum(mediaIds: List<String>, albumId: String?)

    @Query("UPDATE vault_media SET isFavorite = :isFavorite WHERE id = :mediaId")
    suspend fun setFavorite(mediaId: String, isFavorite: Boolean)

    @Query("DELETE FROM vault_media WHERE id IN (:mediaIds)")
    suspend fun deleteMediaByIds(mediaIds: List<String>)

    @Query("DELETE FROM vault_media WHERE importStatus != 'COMPLETED'")
    suspend fun deleteFailedOrIncompleteMedia()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbumById(albumId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupRecord(record: BackupRecordEntity)

    @Query("DELETE FROM vault_media")
    suspend fun deleteAllMedia()

    @Query("DELETE FROM albums")
    suspend fun deleteAllAlbums()

    @Transaction
    suspend fun replaceAllVaultData(albums: List<AlbumEntity>, media: List<VaultMediaEntity>) {
        deleteAllMedia()
        deleteAllAlbums()
        if (albums.isNotEmpty()) insertAlbums(albums)
        if (media.isNotEmpty()) insertMediaList(media)
    }
}
