package com.example.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.security.VaultCryptoManager
import java.io.EOFException
import java.io.File
import java.io.InputStream
import kotlin.math.min

/**
 * Custom Media3 DataSource that decrypts AES-256-CTR vault video files on the fly
 * with full O(1) random-access seeking support so ExoPlayer can scrub smoothly.
 */
@OptIn(UnstableApi::class)
class VaultMediaDataSource(
    private val cryptoManager: VaultCryptoManager
) : BaseDataSource(/* isNetwork = */ false) {

    private var dataSpec: DataSpec? = null
    private var decryptedStream: InputStream? = null
    private var bytesRemaining: Long = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val filePath = uri.path ?: throw IllegalArgumentException("Missing file path in URI: $uri")
        val file = File(filePath)
        if (!file.exists()) throw IllegalStateException("Vault media file missing: $filePath")

        val totalPlaintextLen = cryptoManager.decryptedLength(file)
        if (dataSpec.position > totalPlaintextLen) {
            throw EOFException("Requested offset ${dataSpec.position} beyond length $totalPlaintextLen")
        }

        decryptedStream = cryptoManager.openDecryptedSeekableStream(file, dataSpec.position)
        val remainingFromPos = totalPlaintextLen - dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            min(dataSpec.length, remainingFromPos)
        } else {
            remainingFromPos
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = decryptedStream ?: return C.RESULT_END_OF_INPUT
        val toRead = min(length.toLong(), bytesRemaining).toInt()
        val bytesRead = stream.read(buffer, offset, toRead)
        if (bytesRead <= 0) return C.RESULT_END_OF_INPUT

        bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        try {
            decryptedStream?.close()
        } finally {
            decryptedStream = null
            if (opened) {
                opened = false
                transferEnded()
            }
            dataSpec = null
        }
    }

    class Factory(private val cryptoManager: VaultCryptoManager) : DataSource.Factory {
        override fun createDataSource(): DataSource = VaultMediaDataSource(cryptoManager)
    }
}
