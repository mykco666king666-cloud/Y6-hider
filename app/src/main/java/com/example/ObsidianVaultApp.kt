package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.data.db.VaultDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.storage.VaultMediaRepository
import com.example.data.storage.VaultNotificationHelper
import com.example.media.VaultImageFetcher
import com.example.media.VaultImageKeyer
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ObsidianVaultApp : Application(), ImageLoaderFactory {

    lateinit var cryptoManager: VaultCryptoManager
        private set

    lateinit var database: VaultDatabase
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var mediaRepository: VaultMediaRepository
        private set

    private val appInitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        cryptoManager = VaultCryptoManager(this)
        database = VaultDatabase.getInstance(this)
        preferencesRepository = UserPreferencesRepository(this, cryptoManager)
        mediaRepository = VaultMediaRepository(this, database.vaultDao(), cryptoManager)
        VaultNotificationHelper.ensureChannels(this)

        // Pre-warm Keystore master key off the main thread so first media decode/import never stalls UI.
        appInitScope.launch {
            runCatching { cryptoManager.getOrCreateVaultDataKey() }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VaultImageKeyer())
                add(VaultImageFetcher.Factory(this@ObsidianVaultApp, cryptoManager))
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCachePolicy(CachePolicy.DISABLED)
            .crossfade(150)
            .build()
    }
}
