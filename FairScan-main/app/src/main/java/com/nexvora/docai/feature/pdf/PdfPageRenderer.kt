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
package com.nexvora.docai.feature.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Thread-safe wrapper around [PdfRenderer] (the platform class allows one open page at a time). */
class PdfPageRenderer private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : AutoCloseable {
    private val mutex = Mutex()
    val pageCount: Int get() = renderer.pageCount

    /** Renders page [index] (0-based) to a bitmap [widthPx] wide on a white background. */
    suspend fun renderWidth(index: Int, widthPx: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.Default) {
            val page = renderer.openPage(index)
            try {
                val w = widthPx.coerceAtLeast(1)
                val h = (page.height.toFloat() * w / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                page.close()
            }
        }
    }

    /** Renders page [index] at [dpi]; returns the bitmap and the page size in PDF points (1/72 inch). */
    suspend fun renderDpi(index: Int, dpi: Int): Pair<Bitmap, Pair<Int, Int>> = mutex.withLock {
        withContext(Dispatchers.Default) {
            val page = renderer.openPage(index)
            try {
                val pw = page.width
                val ph = page.height
                val w = (pw * dpi / 72f).toInt().coerceIn(1, 4000)
                val h = (ph * dpi / 72f).toInt().coerceIn(1, 4000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                bmp to (pw to ph)
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        /** Returns null for corrupt, encrypted or otherwise unreadable files. */
        fun open(file: File): PdfPageRenderer? {
            val pfd = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (_: Exception) {
                return null
            }
            return try {
                PdfPageRenderer(pfd, PdfRenderer(pfd))
            } catch (_: Exception) {
                runCatching { pfd.close() }
                null
            }
        }
    }
}
