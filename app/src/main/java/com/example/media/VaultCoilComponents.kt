package com.example.media

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.example.security.VaultCryptoManager
import okio.buffer
import okio.source
import java.io.File

/**
 * Model wrapper passed to Coil's AsyncImage to decode AES-CTR encrypted `.ovm` / `.ovt` files
 * completely in-memory/streaming without ever writing unencrypted copies to public storage.
 */
data class EncryptedVaultImage(
    val file: File,
    val cacheKey: String
)

class VaultImageKeyer : Keyer<EncryptedVaultImage> {
    override fun key(data: EncryptedVaultImage, options: Options): String = data.cacheKey
}

class VaultImageFetcher(
    private val context: Context,
    private val data: EncryptedVaultImage,
    private val cryptoManager: VaultCryptoManager
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val stream = cryptoManager.openDecryptedInputStream(data.file)
        return SourceResult(
            source = ImageSource(
                source = stream.source().buffer(),
                context = context
            ),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(
        private val context: Context,
        private val cryptoManager: VaultCryptoManager
    ) : Fetcher.Factory<EncryptedVaultImage> {
        override fun create(
            data: EncryptedVaultImage,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = VaultImageFetcher(context.applicationContext, data, cryptoManager)
    }
}
