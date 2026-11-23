package com.example.ui.screens.viewer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ObsidianVaultApp
import com.example.data.db.AlbumWithStats
import com.example.data.db.VaultMediaEntity
import com.example.media.VaultMediaDataSource
import com.example.ui.components.ConfirmDeleteVaultDialog
import com.example.ui.components.MediaDetailsBottomSheet
import com.example.ui.components.RestoreToGalleryDialog
import com.example.util.FormatUtils
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: VaultMediaEntity?,
    albums: List<AlbumWithStats>,
    mediaFileFor: (VaultMediaEntity) -> File,
    onBack: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRestoreToGallery: (VaultMediaEntity, Boolean) -> Unit,
    onDeleteVideo: (VaultMediaEntity) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx: android.content.Context? = context
        while (ctx is android.content.ContextWrapper && ctx !is Activity) {
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    val exitCleanly: () -> Unit = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBack()
    }

    BackHandler { exitCleanly() }

    if (video == null) {
        LaunchedEffect(Unit) { exitCleanly() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val app = context.applicationContext as ObsidianVaultApp
    val videoFile = remember(video.id) { mediaFileFor(video) }
    var playbackError by remember(video.id) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(video.id) {
        val dataSourceFactory = VaultMediaDataSource.Factory(app.cryptoManager)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = error.localizedMessage ?: "Unable to decode video stream on this device."
                    }
                })
                setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
                prepare()
                playWhenReady = true
            }
    }

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLandscape by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        )

        playbackError?.let { errMsg ->
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Playback Error", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(errMsg, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.58f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
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
                    IconButton(onClick = exitCleanly) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = video.originalFileName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FormatUtils.formatDuration(video.durationMs)} • ${FormatUtils.formatBytes(video.sizeBytes)}",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Toggle aspect ratio", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            isLandscape = !isLandscape
                            activity?.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    ) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate fullscreen", tint = Color.White)
                    }
                    IconButton(onClick = { onToggleFavorite(video.id) }) {
                        Icon(
                            imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (video.isFavorite) Color(0xFFF43F5E) else Color.White
                        )
                    }
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(Icons.Default.Info, contentDescription = "File details", tint = Color.White)
                    }
                    IconButton(onClick = { showRestoreDialog = true }) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        val albumName = albums.find { it.album.id == video.albumId }?.album?.name
        MediaDetailsBottomSheet(
            item = video,
            albumName = albumName,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showRestoreDialog) {
        RestoreToGalleryDialog(
            count = 1,
            onRestore = { removeVault ->
                showRestoreDialog = false
                onRestoreToGallery(video, removeVault)
                if (removeVault) exitCleanly()
            },
            onDismiss = { showRestoreDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteVaultDialog(
            count = 1,
            onConfirm = {
                showDeleteDialog = false
                onDeleteVideo(video)
                exitCleanly()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
