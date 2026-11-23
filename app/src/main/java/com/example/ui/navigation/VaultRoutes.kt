package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface VaultRoute {
    @Serializable
    data object VaultHome : VaultRoute

    @Serializable
    data object Albums : VaultRoute

    @Serializable
    data class AlbumDetail(val albumId: String) : VaultRoute

    @Serializable
    data object Storage : VaultRoute

    @Serializable
    data object Profile : VaultRoute

    @Serializable
    data object Settings : VaultRoute

    @Serializable
    data object BackupRestore : VaultRoute

    @Serializable
    data object Permissions : VaultRoute

    @Serializable
    data class PhotoViewer(val mediaId: String, val albumId: String? = null) : VaultRoute

    @Serializable
    data class VideoPlayer(val mediaId: String) : VaultRoute
}
