package com.example

import com.example.data.db.MediaType
import com.example.data.db.VaultMediaEntity
import com.example.data.preferences.SortOrder
import com.example.ui.viewmodel.MediaFilter
import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultFormatAndFilterTest {

    @Test
    fun `formatBytes formats B, KB, MB, GB cleanly`() {
        assertEquals("0 B", FormatUtils.formatBytes(0L))
        assertEquals("512 B", FormatUtils.formatBytes(512L))
        assertEquals("1.5 KB", FormatUtils.formatBytes(1536L))
        assertEquals("2.0 MB", FormatUtils.formatBytes(2L * 1024L * 1024L))
    }

    @Test
    fun `formatDuration formats mm_ss and hh_mm_ss`() {
        assertEquals("0:00", FormatUtils.formatDuration(0L))
        assertEquals("0:45", FormatUtils.formatDuration(45_000L))
        assertEquals("3:05", FormatUtils.formatDuration(185_000L))
        assertEquals("1:02:03", FormatUtils.formatDuration(3_723_000L))
    }

    @Test
    fun `filtering and sorting vault media works accurately`() {
        val items = listOf(
            VaultMediaEntity(
                id = "1",
                mediaType = MediaType.PHOTO,
                originalFileName = "zeta.jpg",
                vaultFileName = "1.ovm",
                thumbnailFileName = null,
                mimeType = "image/jpeg",
                sizeBytes = 500L,
                sha256Hash = "a",
                isFavorite = true,
                importedAtEpochMs = 100L
            ),
            VaultMediaEntity(
                id = "2",
                mediaType = MediaType.VIDEO,
                originalFileName = "alpha.mp4",
                vaultFileName = "2.ovm",
                thumbnailFileName = null,
                mimeType = "video/mp4",
                sizeBytes = 5000L,
                sha256Hash = "b",
                isFavorite = false,
                importedAtEpochMs = 300L
            ),
            VaultMediaEntity(
                id = "3",
                mediaType = MediaType.PHOTO,
                originalFileName = "beta.png",
                vaultFileName = "3.ovm",
                thumbnailFileName = null,
                mimeType = "image/png",
                sizeBytes = 1500L,
                sha256Hash = "c",
                isFavorite = false,
                importedAtEpochMs = 200L
            )
        )

        val photosOnly = items.filter { it.mediaType == MediaType.PHOTO }
        assertEquals(2, photosOnly.size)

        val byName = items.sortedBy { it.originalFileName.lowercase() }
        assertEquals(listOf("2", "3", "1"), byName.map { it.id })

        val byLargest = items.sortedByDescending { it.sizeBytes }
        assertEquals(listOf("2", "3", "1"), byLargest.map { it.id })

        val byNewest = items.sortedByDescending { it.importedAtEpochMs }
        assertEquals(listOf("2", "3", "1"), byNewest.map { it.id })
    }
}
