/*
 * Copyright 2026 NexVora Lab's Ofc
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.nexvora.docai.feature.vault

import android.graphics.Bitmap
import android.util.LruCache
import com.nexvora.docai.core.common.decodeBitmapFile
import com.nexvora.docai.feature.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Small in-memory thumbnail cache (30 entries) for the document list. */
object VaultThumbs {
    private val cache = LruCache<String, Bitmap>(30)

    suspend fun get(doc: VaultDoc, file: File): Bitmap? {
        val key = "${doc.id}:${doc.sizeBytes}"
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            try {
                if (doc.isImage) decodeBitmapFile(file, 160)
                else PdfPageRenderer.open(file)?.use { if (it.pageCount > 0) it.renderWidth(0, 160) else null }
            } catch (_: Throwable) { null }
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }
}
