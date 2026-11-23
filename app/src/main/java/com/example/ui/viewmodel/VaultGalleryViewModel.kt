package com.example.ui.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ObsidianVaultApp
import com.example.data.db.AlbumWithStats
import com.example.data.db.BackupRecordEntity
import com.example.data.db.MediaType
import com.example.data.db.VaultMediaEntity
import com.example.data.preferences.SortOrder
import com.example.data.storage.BackupPreview
import com.example.data.storage.DeviceMediaCandidate
import com.example.data.storage.ImportBatchResult
import com.example.data.storage.ImportProgress
import com.example.data.storage.IntegrityReport
import com.example.data.storage.StorageReport
import com.example.data.storage.VaultNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class MediaFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    FAVORITES("Favorites")
}

data class GalleryUiState(
    val searchQuery: String = "",
    val filter: MediaFilter = MediaFilter.ALL,
    val selectedIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val importProgress: ImportProgress? = null,
    val lastImportSummary: ImportBatchResult? = null,
    val pendingOriginalDeleteUris: List<Uri> = emptyList(),
    val storageReport: StorageReport? = null,
    val isCheckingStorage: Boolean = false,
    val integrityReport: IntegrityReport? = null,
    val isCheckingIntegrity: Boolean = false,
    val isBackupBusy: Boolean = false,
    val pendingBackupPreview: Pair<BackupPreview, String>? = null,
    val toastMessage: String? = null
)

class VaultGalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObsidianVaultApp
    val mediaRepo = app.mediaRepository
    private val prefsRepo = app.preferencesRepository

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    val allMedia: StateFlow<List<VaultMediaEntity>> = mediaRepo.observeAllMedia().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val failedMedia: StateFlow<List<VaultMediaEntity>> = mediaRepo.observeFailedMedia().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val albumsWithStats: StateFlow<List<AlbumWithStats>> = mediaRepo.observeAlbumsWithStats().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val backupRecords: StateFlow<List<BackupRecordEntity>> = mediaRepo.observeBackupRecords().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredMedia: StateFlow<List<VaultMediaEntity>> = combine(
        allMedia,
        _uiState.map { it.searchQuery to it.filter }.distinctUntilChanged(),
        prefsRepo.preferencesFlow.map { it.sortOrder }.distinctUntilChanged()
    ) { items, (query, filter), sortOrder ->
        applyFilterAndSort(items, query, filter, sortOrder)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshStorageReport()
    }

    fun applyFilterAndSort(
        items: List<VaultMediaEntity>,
        query: String,
        filter: MediaFilter,
        sortOrder: SortOrder
    ): List<VaultMediaEntity> {
        val q = query.trim().lowercase()
        val filtered = items.filter { item ->
            val matchesFilter = when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> item.mediaType == MediaType.PHOTO
                MediaFilter.VIDEOS -> item.mediaType == MediaType.VIDEO
                MediaFilter.FAVORITES -> item.isFavorite
            }
            val matchesQuery = q.isEmpty() ||
                item.originalFileName.lowercase().contains(q) ||
                item.mimeType.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
        return when (sortOrder) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.importedAtEpochMs }
            SortOrder.OLDEST -> filtered.sortedBy { it.importedAtEpochMs }
            SortOrder.LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.originalFileName.lowercase() }
        }
    }

    fun mediaFileFor(item: VaultMediaEntity): File = mediaRepo.mediaFileFor(item)
    fun thumbFileFor(item: VaultMediaEntity): File? = mediaRepo.thumbFileFor(item)

    suspend fun loadDeviceMedia(mediaType: MediaType): List<DeviceMediaCandidate> =
        mediaRepo.queryDeviceMedia(mediaType)

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: MediaFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { prefsRepo.setSortOrder(order) }
    }

    fun toggleSelection(mediaId: String) {
        _uiState.update { state ->
            val next = if (mediaId in state.selectedIds) {
                state.selectedIds - mediaId
            } else {
                state.selectedIds + mediaId
            }
            state.copy(selectedIds = next)
        }
    }

    fun selectAll(ids: List<String>) {
        _uiState.update { it.copy(selectedIds = ids.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun importMediaUris(uris: List<Uri>, targetAlbumId: String? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = null) }
            val result = mediaRepo.importMediaUris(
                uris = uris,
                targetAlbumId = targetAlbumId,
                allowDuplicates = false,
                onProgress = { prog ->
                    _uiState.update { it.copy(importProgress = prog) }
                }
            )
            val prefs = prefsRepo.getSnapshot()
            val askDelete = prefs.askDeleteOriginalAfterImport && result.importedSourceUris.isNotEmpty()
            val summaryMsg = buildString {
                append("Imported ${result.importedItems.size} file(s)")
                if (result.duplicateFileNames.isNotEmpty()) {
                    append(" • ${result.duplicateFileNames.size} duplicate(s) skipped")
                }
                if (result.failedItems.isNotEmpty()) {
                    append(" • ${result.failedItems.size} failed")
                }
            }
            if (prefs.importAlertsEnabled && result.importedItems.isNotEmpty()) {
                VaultNotificationHelper.postVaultEvent(
                    app,
                    "Media Encrypted in Vault",
                    summaryMsg
                )
            }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importProgress = null,
                    lastImportSummary = result,
                    pendingOriginalDeleteUris = if (askDelete) result.importedSourceUris else emptyList(),
                    toastMessage = summaryMsg
                )
            }
            refreshStorageReport()
        }
    }

    fun buildDeleteOriginalsSender(): IntentSender? {
        val uris = _uiState.value.pendingOriginalDeleteUris
        return mediaRepo.buildDeleteOriginalsIntentSender(uris)
    }

    fun attemptPreROriginalsDelete(): Pair<Int, IntentSender?> {
        val uris = _uiState.value.pendingOriginalDeleteUris
        var deleted = 0
        for (uri in uris) {
            val (ok, sender) = mediaRepo.deleteOriginalPreR(uri)
            if (ok) deleted++
            if (sender != null) return deleted to sender
        }
        return deleted to null
    }

    fun dismissOriginalDeletePrompt(message: String? = null) {
        _uiState.update {
            it.copy(
                pendingOriginalDeleteUris = emptyList(),
                toastMessage = message ?: it.toastMessage
            )
        }
    }

    fun createAlbum(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            mediaRepo.createAlbum(name, description)
            _uiState.update { it.copy(toastMessage = "Album \"$name\" created.") }
        }
    }

    fun renameAlbum(albumId: String, newName: String, description: String? = null) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            mediaRepo.renameAlbum(albumId, newName, description)
            _uiState.update { it.copy(toastMessage = "Album renamed.") }
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            mediaRepo.deleteAlbum(albumId)
            _uiState.update { it.copy(toastMessage = "Album deleted. Media kept in Vault.") }
        }
    }

    fun moveSelectedToAlbum(targetAlbumId: String?) {
        val ids = _uiState.value.selectedIds.toList()
        moveMediaToAlbum(ids, targetAlbumId)
    }

    fun moveMediaToAlbum(ids: List<String>, targetAlbumId: String?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            mediaRepo.moveMediaToAlbum(ids, targetAlbumId)
            _uiState.update {
                it.copy(
                    selectedIds = emptySet(),
                    toastMessage = "Moved ${ids.size} item(s)."
                )
            }
        }
    }

    fun toggleFavorite(mediaId: String) {
        viewModelScope.launch { mediaRepo.toggleFavorite(mediaId) }
    }

    fun restoreToGallery(items: List<VaultMediaEntity>, removeFromVault: Boolean) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            mediaRepo.restoreMediaToGallery(items, removeFromVault)
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(
                            selectedIds = emptySet(),
                            toastMessage = "Restored $count item(s) to device gallery."
                        )
                    }
                    refreshStorageReport()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(toastMessage = err.message ?: "Failed to restore media.")
                    }
                }
        }
    }

    fun deleteItems(items: List<VaultMediaEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            mediaRepo.deleteVaultItems(items)
            _uiState.update {
                it.copy(
                    selectedIds = emptySet(),
                    toastMessage = "Permanently deleted ${items.size} vault item(s)."
                )
            }
            refreshStorageReport()
        }
    }

    fun refreshStorageReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingStorage = true) }
            val report = mediaRepo.computeStorageReport()
            _uiState.update { it.copy(storageReport = report, isCheckingStorage = false) }
        }
    }

    fun clearSafeCache() {
        viewModelScope.launch {
            val freed = mediaRepo.clearSafeCache()
            val report = mediaRepo.computeStorageReport()
            _uiState.update {
                it.copy(
                    storageReport = report,
                    toastMessage = "Cleared ${com.example.util.FormatUtils.formatBytes(freed)} of temporary cache safely."
                )
            }
        }
    }

    fun runIntegrityCheck() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingIntegrity = true) }
            val report = mediaRepo.runIntegrityCheck()
            _uiState.update {
                it.copy(
                    integrityReport = report,
                    isCheckingIntegrity = false,
                    toastMessage = "Verified ${report.healthyCount}/${report.checkedCount} vault files."
                )
            }
        }
    }

    fun recoverFailedImports() {
        viewModelScope.launch {
            val cleaned = mediaRepo.recoverFailedImports()
            val report = mediaRepo.computeStorageReport()
            _uiState.update {
                it.copy(
                    storageReport = report,
                    toastMessage = "Cleaned $cleaned incomplete import(s)/temp file(s)."
                )
            }
        }
    }

    fun exportBackup(destUri: Uri, fileName: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            mediaRepo.exportEncryptedBackup(destUri, fileName, password)
                .onSuccess { record ->
                    val prefs = prefsRepo.getSnapshot()
                    if (prefs.backupRemindersEnabled) {
                        VaultNotificationHelper.postVaultEvent(
                            app,
                            "Encrypted Backup Exported",
                            "Saved ${record.itemCount} items into $fileName."
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isBackupBusy = false,
                            toastMessage = "Encrypted backup saved (${record.itemCount} items)."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isBackupBusy = false,
                            toastMessage = err.message ?: "Backup export failed."
                        )
                    }
                }
        }
    }

    fun inspectBackupForRestore(sourceUri: Uri, fileName: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            mediaRepo.inspectEncryptedBackup(sourceUri, password)
                .onSuccess { preview ->
                    _uiState.update {
                        it.copy(
                            isBackupBusy = false,
                            pendingBackupPreview = preview to fileName
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isBackupBusy = false,
                            toastMessage = err.message ?: "Unable to open backup."
                        )
                    }
                }
        }
    }

    fun confirmApplyBackup(replaceExisting: Boolean) {
        val pending = _uiState.value.pendingBackupPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true, pendingBackupPreview = null) }
            mediaRepo.applyBackupPreview(pending.first, pending.second, replaceExisting)
                .onSuccess { record ->
                    _uiState.update {
                        it.copy(
                            isBackupBusy = false,
                            toastMessage = "Restored ${record.itemCount} item(s) from backup."
                        )
                    }
                    refreshStorageReport()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isBackupBusy = false,
                            toastMessage = err.message ?: "Backup restore failed."
                        )
                    }
                }
        }
    }

    fun cancelPendingBackupPreview() {
        _uiState.value.pendingBackupPreview?.first?.decryptedZipFile?.delete()
        _uiState.update { it.copy(pendingBackupPreview = null) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
