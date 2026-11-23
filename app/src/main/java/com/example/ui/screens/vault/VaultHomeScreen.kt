package com.example.ui.screens.vault

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.db.AlbumWithStats
import com.example.data.db.MediaType
import com.example.data.db.VaultMediaEntity
import com.example.data.preferences.SortOrder
import com.example.data.storage.DeviceMediaCandidate
import com.example.security.PermissionAccessStatus
import com.example.security.VaultPermissionManager
import com.example.security.VaultPermissionsSnapshot
import com.example.ui.components.ConfirmDeleteVaultDialog
import com.example.ui.components.DeviceGalleryPickerSheet
import com.example.ui.components.MediaPermissionDeniedDialog
import com.example.ui.components.MoveToAlbumBottomSheet
import com.example.ui.components.OriginalDeleteConfirmationDialog
import com.example.ui.components.RestoreToGalleryDialog
import com.example.ui.components.VaultMediaGridCard
import com.example.ui.components.VaultPremiumCard
import com.example.ui.viewmodel.GalleryUiState
import com.example.ui.viewmodel.MediaFilter
import com.example.util.FormatUtils
import java.io.File

@Composable
fun VaultHomeScreen(
    allMedia: List<VaultMediaEntity>,
    filteredMedia: List<VaultMediaEntity>,
    albums: List<AlbumWithStats>,
    uiState: GalleryUiState,
    currentSortOrder: SortOrder,
    permissionsSnapshot: VaultPermissionsSnapshot,
    mediaFileFor: (VaultMediaEntity) -> File,
    thumbFileFor: (VaultMediaEntity) -> File?,
    onLoadDeviceMedia: suspend (MediaType) -> List<DeviceMediaCandidate>,
    onRefreshPermissions: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (MediaFilter) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onClearSelection: () -> Unit,
    onOpenMedia: (VaultMediaEntity) -> Unit,
    onImportUris: (List<Uri>) -> Unit,
    onSuppressAutoLock: () -> Unit,
    onBuildDeleteOriginalsSender: () -> android.content.IntentSender?,
    onAttemptPreRDelete: () -> Pair<Int, android.content.IntentSender?>,
    onDismissOriginalDeletePrompt: (String?) -> Unit,
    onMoveSelectedToAlbum: (String?) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRestoreSelected: (List<VaultMediaEntity>, Boolean) -> Unit,
    onDeleteSelected: (List<VaultMediaEntity>) -> Unit,
    onLockNow: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var fabExpanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var activeGallerySheetType by remember { mutableStateOf<MediaType?>(null) }
    var deniedMediaPromptType by remember { mutableStateOf<MediaType?>(null) }
    var galleryRefreshCounter by remember { mutableStateOf(0) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && activeGallerySheetType != null) {
                galleryRefreshCounter++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris)
    }

    val safDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris)
    }

    val photosPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        VaultPermissionManager.markPhotosPrompted(context)
        onRefreshPermissions()
        val updated = VaultPermissionManager.checkPhotosStatus(context)
        if (updated == PermissionAccessStatus.ALLOWED || updated == PermissionAccessStatus.LIMITED_ACCESS) {
            galleryRefreshCounter++
            activeGallerySheetType = MediaType.PHOTO
        } else {
            deniedMediaPromptType = MediaType.PHOTO
        }
    }

    val videosPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        VaultPermissionManager.markVideosPrompted(context)
        onRefreshPermissions()
        val updated = VaultPermissionManager.checkVideosStatus(context)
        if (updated == PermissionAccessStatus.ALLOWED || updated == PermissionAccessStatus.LIMITED_ACCESS) {
            galleryRefreshCounter++
            activeGallerySheetType = MediaType.VIDEO
        } else {
            deniedMediaPromptType = MediaType.VIDEO
        }
    }

    val handleImportPhotosClick: () -> Unit = {
        val status = VaultPermissionManager.checkPhotosStatus(context)
        when (status) {
            PermissionAccessStatus.ALLOWED, PermissionAccessStatus.LIMITED_ACCESS -> {
                galleryRefreshCounter++
                activeGallerySheetType = MediaType.PHOTO
            }
            PermissionAccessStatus.NOT_ALLOWED -> {
                if (permissionsSnapshot.photosPermanentlyDenied) {
                    deniedMediaPromptType = MediaType.PHOTO
                } else {
                    onSuppressAutoLock()
                    photosPermissionLauncher.launch(VaultPermissionManager.photosRequestPermissions())
                }
            }
            PermissionAccessStatus.NOT_REQUIRED -> {
                galleryRefreshCounter++
                activeGallerySheetType = MediaType.PHOTO
            }
        }
    }

    val handleImportVideosClick: () -> Unit = {
        val status = VaultPermissionManager.checkVideosStatus(context)
        when (status) {
            PermissionAccessStatus.ALLOWED, PermissionAccessStatus.LIMITED_ACCESS -> {
                galleryRefreshCounter++
                activeGallerySheetType = MediaType.VIDEO
            }
            PermissionAccessStatus.NOT_ALLOWED -> {
                if (permissionsSnapshot.videosPermanentlyDenied) {
                    deniedMediaPromptType = MediaType.VIDEO
                } else {
                    onSuppressAutoLock()
                    videosPermissionLauncher.launch(VaultPermissionManager.videosRequestPermissions())
                }
            }
            PermissionAccessStatus.NOT_REQUIRED -> {
                galleryRefreshCounter++
                activeGallerySheetType = MediaType.VIDEO
            }
        }
    }

    val deleteOriginalsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onDismissOriginalDeletePrompt("Original gallery media removed.")
        } else {
            onDismissOriginalDeletePrompt("Original gallery media kept.")
        }
    }

    val selectedItems = remember(allMedia, uiState.selectedIds) {
        allMedia.filter { it.id in uiState.selectedIds }
    }
    val isSelectionMode = uiState.selectedIds.isNotEmpty()

    val photoCount = remember(allMedia) { allMedia.count { it.mediaType == MediaType.PHOTO } }
    val videoCount = remember(allMedia) { allMedia.count { it.mediaType == MediaType.VIDEO } }
    val totalBytes = remember(allMedia) { allMedia.sumOf { it.sizeBytes } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Header or Contextual Multi-Select Bar
            if (isSelectionMode) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                            Text(
                                text = "${uiState.selectedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row {
                            IconButton(onClick = { onSelectAll(filteredMedia.map { it.id }) }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                            }
                            IconButton(onClick = { showMoveSheet = true }) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to album")
                            }
                            IconButton(onClick = { showRestoreDialog = true }) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore to gallery")
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete permanently",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "AES-256 Encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("sort_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort media")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        trailingIcon = {
                                            if (order == currentSortOrder) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSortChange(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onLockNow,
                            modifier = Modifier.testTag("lock_now_button")
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Lock Vault Now",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Compact Stats Strip
            VaultPremiumCard(
                modifier = Modifier.fillMaxWidth(),
                highlightBorder = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatColumn("Photos", photoCount.toString())
                    StatColumn("Videos", videoCount.toString())
                    StatColumn("Encrypted", FormatUtils.formatBytes(totalBytes))
                    StatColumn("Albums", albums.size.toString())
                }
            }

            Spacer(Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search encrypted photos & videos…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_search_input")
            )

            Spacer(Modifier.height(10.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(MediaFilter.entries) { filter ->
                    FilterChip(
                        selected = uiState.filter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("filter_chip_${filter.name}")
                    )
                }
            }

            // Import Progress Banner
            AnimatedVisibility(
                visible = uiState.isImporting,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                VaultPremiumCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    highlightBorder = true
                ) {
                    val prog = uiState.importProgress
                    Text(
                        text = if (prog != null) {
                            "Encrypting ${prog.currentIndex}/${prog.totalCount}: ${prog.currentFileName}"
                        } else {
                            "Preparing encrypted import…"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    if (prog != null && prog.totalCount > 0) {
                        LinearProgressIndicator(
                            progress = { prog.currentIndex.toFloat() / prog.totalCount.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Grid or Empty State
            if (filteredMedia.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_vault),
                            contentDescription = "Empty vault illustration",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(190.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = if (allMedia.isEmpty()) {
                                stringResource(R.string.empty_vault_title)
                            } else {
                                "No Matching Media"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (allMedia.isEmpty()) {
                                stringResource(R.string.empty_vault_subtitle)
                            } else {
                                "Try clearing your search or switching media filters."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (allMedia.isEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    onSuppressAutoLock()
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                modifier = Modifier.testTag("empty_import_cta")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.import_media))
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vault_media_grid")
                ) {
                    items(
                        items = filteredMedia,
                        key = { it.id },
                        contentType = { it.mediaType }
                    ) { item ->
                        VaultMediaGridCard(
                            item = item,
                            thumbFile = thumbFileFor(item),
                            fallbackMediaFile = mediaFileFor(item),
                            isSelectionMode = isSelectionMode,
                            isSelected = item.id in uiState.selectedIds,
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleSelect(item.id)
                                } else {
                                    onOpenMedia(item)
                                }
                            },
                            onLongClick = { onToggleSelect(item.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        if (fabExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { fabExpanded = false }
                    )
            )
        }

        // Speed-Dial Import FAB
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
        ) {
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpeedDialRow(
                        label = "Browse Files (SAF)",
                        icon = Icons.Default.FolderOpen,
                        onClick = {
                            fabExpanded = false
                            onSuppressAutoLock()
                            safDocumentLauncher.launch(arrayOf("image/*", "video/*"))
                        }
                    )
                    SpeedDialRow(
                        label = "Import Videos",
                        icon = Icons.Default.VideoLibrary,
                        onClick = {
                            fabExpanded = false
                            handleImportVideosClick()
                        }
                    )
                    SpeedDialRow(
                        label = "Import Photos",
                        icon = Icons.Default.PhotoLibrary,
                        onClick = {
                            fabExpanded = false
                            handleImportPhotosClick()
                        }
                    )
                }
            }

            ExtendedFloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
                icon = {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Import media"
                    )
                },
                text = { Text(if (fabExpanded) "Close" else "Import") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("import_fab")
            )
        }
    }

    // Dialogs & Sheets
    if (uiState.pendingOriginalDeleteUris.isNotEmpty()) {
        OriginalDeleteConfirmationDialog(
            itemCount = uiState.pendingOriginalDeleteUris.size,
            onRemoveOriginals = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val sender = onBuildDeleteOriginalsSender()
                    if (sender != null) {
                        onSuppressAutoLock()
                        deleteOriginalsLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        onDismissOriginalDeletePrompt("System picker items remain read-only; originals untouched.")
                    }
                } else {
                    val (deleted, sender) = onAttemptPreRDelete()
                    if (sender != null) {
                        onSuppressAutoLock()
                        deleteOriginalsLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        onDismissOriginalDeletePrompt(
                            if (deleted > 0) "Removed $deleted original file(s)."
                            else "Originals preserved by system."
                        )
                    }
                }
            },
            onKeepOriginals = {
                onDismissOriginalDeletePrompt(null)
            }
        )
    }

    if (showDeleteConfirm && selectedItems.isNotEmpty()) {
        ConfirmDeleteVaultDialog(
            count = selectedItems.size,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteSelected(selectedItems)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showRestoreDialog && selectedItems.isNotEmpty()) {
        RestoreToGalleryDialog(
            count = selectedItems.size,
            onRestore = { removeVault ->
                showRestoreDialog = false
                onRestoreSelected(selectedItems, removeVault)
            },
            onDismiss = { showRestoreDialog = false }
        )
    }

    if (showMoveSheet) {
        MoveToAlbumBottomSheet(
            albums = albums,
            onSelectAlbum = { albumId ->
                showMoveSheet = false
                onMoveSelectedToAlbum(albumId)
            },
            onCreateAlbum = onCreateAlbum,
            onDismiss = { showMoveSheet = false }
        )
    }

    activeGallerySheetType?.let { sheetType ->
        val accessStatus = if (sheetType == MediaType.VIDEO) {
            permissionsSnapshot.videosAccess
        } else {
            permissionsSnapshot.photosAccess
        }
        DeviceGalleryPickerSheet(
            mediaType = sheetType,
            accessStatus = accessStatus,
            refreshKey = galleryRefreshCounter,
            onLoadDeviceMedia = onLoadDeviceMedia,
            onImportSelectedUris = onImportUris,
            onManageLimitedSelection = {
                onSuppressAutoLock()
                if (sheetType == MediaType.VIDEO) {
                    videosPermissionLauncher.launch(VaultPermissionManager.videosRequestPermissions())
                } else {
                    photosPermissionLauncher.launch(VaultPermissionManager.photosRequestPermissions())
                }
            },
            onOpenAppSettings = {
                onSuppressAutoLock()
                VaultPermissionManager.openAppSettings(context)
            },
            onOpenSystemPhotoPicker = {
                onSuppressAutoLock()
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(
                        if (sheetType == MediaType.VIDEO) {
                            ActivityResultContracts.PickVisualMedia.VideoOnly
                        } else {
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        }
                    )
                )
            },
            onDismiss = { activeGallerySheetType = null }
        )
    }

    deniedMediaPromptType?.let { deniedType ->
        val isPermDenied = if (deniedType == MediaType.VIDEO) {
            permissionsSnapshot.videosPermanentlyDenied
        } else {
            permissionsSnapshot.photosPermanentlyDenied
        }
        MediaPermissionDeniedDialog(
            mediaType = deniedType,
            permanentlyDenied = isPermDenied,
            onRetryPermission = {
                onSuppressAutoLock()
                if (deniedType == MediaType.VIDEO) {
                    videosPermissionLauncher.launch(VaultPermissionManager.videosRequestPermissions())
                } else {
                    photosPermissionLauncher.launch(VaultPermissionManager.photosRequestPermissions())
                }
            },
            onOpenAppSettings = {
                onSuppressAutoLock()
                VaultPermissionManager.openAppSettings(context)
            },
            onUsePhotoPicker = {
                onSuppressAutoLock()
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(
                        if (deniedType == MediaType.VIDEO) {
                            ActivityResultContracts.PickVisualMedia.VideoOnly
                        } else {
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        }
                    )
                )
            },
            onDismiss = { deniedMediaPromptType = null }
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpeedDialRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}
