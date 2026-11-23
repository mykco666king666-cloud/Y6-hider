package com.example.ui.screens.albums

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.db.AlbumWithStats
import com.example.data.db.VaultMediaEntity
import com.example.media.EncryptedVaultImage
import com.example.ui.components.ConfirmDeleteVaultDialog
import com.example.ui.components.MoveToAlbumBottomSheet
import com.example.ui.components.RestoreToGalleryDialog
import com.example.ui.components.VaultMediaGridCard
import com.example.ui.components.VaultModalDialog
import com.example.util.FormatUtils
import java.io.File

@Composable
fun AlbumsScreen(
    albums: List<AlbumWithStats>,
    thumbDir: File,
    onCreateAlbum: (name: String, description: String) -> Unit,
    onRenameAlbum: (albumId: String, name: String, description: String) -> Unit,
    onDeleteAlbum: (albumId: String) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAlbum by remember { mutableStateOf<AlbumWithStats?>(null) }
    var deletingAlbum by remember { mutableStateOf<AlbumWithStats?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.albums_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${albums.size} encrypted folder${if (albums.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (albums.isEmpty()) {
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
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No Albums Yet", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Organize private documents, trips, or sensitive videos into dedicated folders.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Create First Album")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(albums, key = { it.album.id }) { stats ->
                        AlbumCard(
                            stats = stats,
                            thumbDir = thumbDir,
                            onClick = { onOpenAlbum(stats.album.id) },
                            onRename = { editingAlbum = stats },
                            onDelete = { deletingAlbum = stats }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreateDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = "Create album") },
            text = { Text("New Album") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .testTag("create_album_fab")
        )
    }

    if (showCreateDialog) {
        AlbumEditDialog(
            title = "New Private Album",
            initialName = "",
            initialDescription = "",
            confirmLabel = "Create",
            onConfirm = { name, desc ->
                onCreateAlbum(name, desc)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingAlbum?.let { target ->
        AlbumEditDialog(
            title = "Rename Album",
            initialName = target.album.name,
            initialDescription = target.album.description,
            confirmLabel = "Save",
            onConfirm = { name, desc ->
                onRenameAlbum(target.album.id, name, desc)
                editingAlbum = null
            },
            onDismiss = { editingAlbum = null }
        )
    }

    deletingAlbum?.let { target ->
        VaultModalDialog(
            onDismissRequest = { deletingAlbum = null },
            title = { Text("Delete \"${target.album.name}\"?") },
            text = {
                Text(
                    "The album folder will be removed, while its ${target.itemCount} encrypted file(s) remain safe in your Main Vault."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAlbum(target.album.id)
                        deletingAlbum = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAlbum = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AlbumCard(
    stats: AlbumWithStats,
    thumbDir: File,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val thumbModel by produceState<EncryptedVaultImage?>(
        initialValue = null,
        key1 = stats.album.id,
        key2 = stats.latestThumbFileName
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = stats.latestThumbFileName?.let { File(thumbDir, it) }
            if (file != null && file.exists()) {
                EncryptedVaultImage(
                    file = file,
                    cacheKey = "album_${stats.album.id}_${file.name}"
                )
            } else {
                null
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbModel != null) {
                    AsyncImage(
                        model = thumbModel,
                        contentDescription = stats.album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        modifier = Modifier.size(44.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Album options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Folder") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stats.album.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${stats.itemCount} items • ${FormatUtils.formatBytes(stats.totalSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumEditDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    VaultModalDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("album_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("album_description_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), description.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AlbumDetailScreen(
    album: AlbumWithStats?,
    mediaItems: List<VaultMediaEntity>,
    allAlbums: List<AlbumWithStats>,
    mediaFileFor: (VaultMediaEntity) -> File,
    thumbFileFor: (VaultMediaEntity) -> File?,
    onBack: () -> Unit,
    onSuppressAutoLock: () -> Unit,
    onImportIntoAlbum: (List<Uri>) -> Unit,
    onOpenMedia: (VaultMediaEntity) -> Unit,
    onMoveMediaToAlbum: (List<String>, String?) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRestoreMedia: (List<VaultMediaEntity>, Boolean) -> Unit,
    onDeleteMedia: (List<VaultMediaEntity>) -> Unit
) {
    BackHandler { onBack() }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) onImportIntoAlbum(uris)
    }

    val selectedItems = remember(mediaItems, selectedIds) {
        mediaItems.filter { it.id in selectedIds }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) selectedIds = emptySet() else onBack()
                        }
                    ) {
                        Icon(
                            if (selectedIds.isNotEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column {
                        Text(
                            text = if (selectedIds.isNotEmpty()) "${selectedIds.size} selected"
                            else album?.album?.name ?: "Album",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (selectedIds.isEmpty() && album != null) {
                            Text(
                                text = "${mediaItems.size} items • ${FormatUtils.formatBytes(album.totalSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (selectedIds.isNotEmpty()) {
                    Row {
                        IconButton(onClick = { showMoveSheet = true }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to album")
                        }
                        IconButton(onClick = { showRestoreDialog = true }) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (mediaItems.isEmpty()) {
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
                        Text("This album is empty", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Import directly into this album or move existing vault media here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(bottom = 92.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = mediaItems,
                        key = { it.id },
                        contentType = { it.mediaType }
                    ) { item ->
                        VaultMediaGridCard(
                            item = item,
                            thumbFile = thumbFileFor(item),
                            fallbackMediaFile = mediaFileFor(item),
                            isSelectionMode = selectedIds.isNotEmpty(),
                            isSelected = item.id in selectedIds,
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (item.id in selectedIds) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                } else {
                                    onOpenMedia(item)
                                }
                            },
                            onLongClick = {
                                selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                            }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                onSuppressAutoLock()
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            icon = { Icon(Icons.Default.Add, contentDescription = "Import into album") },
            text = { Text("Add Media") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
        )
    }

    if (showMoveSheet && selectedIds.isNotEmpty()) {
        MoveToAlbumBottomSheet(
            albums = allAlbums,
            onSelectAlbum = { targetId ->
                showMoveSheet = false
                onMoveMediaToAlbum(selectedIds.toList(), targetId)
                selectedIds = emptySet()
            },
            onCreateAlbum = onCreateAlbum,
            onDismiss = { showMoveSheet = false }
        )
    }

    if (showRestoreDialog && selectedItems.isNotEmpty()) {
        RestoreToGalleryDialog(
            count = selectedItems.size,
            onRestore = { removeVault ->
                showRestoreDialog = false
                onRestoreMedia(selectedItems, removeVault)
                selectedIds = emptySet()
            },
            onDismiss = { showRestoreDialog = false }
        )
    }

    if (showDeleteConfirm && selectedItems.isNotEmpty()) {
        ConfirmDeleteVaultDialog(
            count = selectedItems.size,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteMedia(selectedItems)
                selectedIds = emptySet()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
