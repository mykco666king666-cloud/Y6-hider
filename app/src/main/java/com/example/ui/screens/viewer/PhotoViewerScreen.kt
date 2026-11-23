package com.example.ui.screens.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.db.AlbumWithStats
import com.example.data.db.VaultMediaEntity
import com.example.media.EncryptedVaultImage
import com.example.ui.components.ConfirmDeleteVaultDialog
import com.example.ui.components.MediaDetailsBottomSheet
import com.example.ui.components.MoveToAlbumBottomSheet
import com.example.ui.components.RestoreToGalleryDialog
import java.io.File

@Composable
fun PhotoViewerScreen(
    photos: List<VaultMediaEntity>,
    initialPhotoId: String,
    albums: List<AlbumWithStats>,
    mediaFileFor: (VaultMediaEntity) -> File,
    onBack: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveToAlbum: (String, String?) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRestoreToGallery: (VaultMediaEntity, Boolean) -> Unit,
    onDeletePhoto: (VaultMediaEntity) -> Unit
) {
    BackHandler { onBack() }

    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val startIndex = remember(photos, initialPhotoId) {
        photos.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = startIndex) { photos.size }
    val currentPhoto = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]

    var showChrome by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]
            ZoomableEncryptedPhoto(
                file = mediaFileFor(photo),
                cacheKey = "full_${photo.id}",
                contentDescription = photo.originalFileName,
                onTap = { showChrome = !showChrome }
            )
        }

        // Top Bar
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = currentPhoto.originalFileName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        IconButton(onClick = { onToggleFavorite(currentPhoto.id) }) {
                            Icon(
                                imageVector = if (currentPhoto.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (currentPhoto.isFavorite) Color(0xFFF43F5E) else Color.White
                            )
                        }
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(Icons.Default.Info, contentDescription = "File details", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Bottom Actions
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showMoveSheet = true }) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = "Move to album", tint = Color.White)
                    }
                    IconButton(onClick = { showRestoreDialog = true }) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore to gallery", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        val albumName = albums.find { it.album.id == currentPhoto.albumId }?.album?.name
        MediaDetailsBottomSheet(
            item = currentPhoto,
            albumName = albumName,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showMoveSheet) {
        MoveToAlbumBottomSheet(
            albums = albums,
            onSelectAlbum = { albumId ->
                showMoveSheet = false
                onMoveToAlbum(currentPhoto.id, albumId)
            },
            onCreateAlbum = onCreateAlbum,
            onDismiss = { showMoveSheet = false }
        )
    }

    if (showRestoreDialog) {
        RestoreToGalleryDialog(
            count = 1,
            onRestore = { removeVault ->
                showRestoreDialog = false
                onRestoreToGallery(currentPhoto, removeVault)
                if (removeVault && photos.size <= 1) onBack()
            },
            onDismiss = { showRestoreDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteVaultDialog(
            count = 1,
            onConfirm = {
                showDeleteDialog = false
                onDeletePhoto(currentPhoto)
                if (photos.size <= 1) onBack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun ZoomableEncryptedPhoto(
    file: File,
    cacheKey: String,
    contentDescription: String,
    onTap: () -> Unit
) {
    var scale by remember(cacheKey) { mutableFloatStateOf(1f) }
    var offset by remember(cacheKey) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(cacheKey) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(cacheKey) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    if (nextScale > 1f) {
                        val maxOffsetX = (size.width * (nextScale - 1f)) / 2f
                        val maxOffsetY = (size.height * (nextScale - 1f)) / 2f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = EncryptedVaultImage(file = file, cacheKey = cacheKey),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
