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
package com.nexvora.docai.feature.ocr

import android.content.ContentResolver
import android.net.Uri
import com.nexvora.docai.core.common.copyUriTo
import com.nexvora.docai.core.common.decodeBitmapFile
import com.nexvora.docai.core.common.displayNameOf
import com.nexvora.docai.data.OcrLanguageRepository
import com.nexvora.docai.domain.OcrService
import com.nexvora.docai.feature.pdf.PdfPageRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class OcrUiState(
    val installed: Set<String> = emptySet(),
    val active: Set<String> = emptySet(),
    val running: Boolean = false,
    val status: String? = null,
    val text: String = "",
    val sourceName: String? = null,
    val error: String? = null,
)

/** Real OCR using the upstream Tesseract integration. Handwriting recognition is NOT claimed. */
class OcrController(
    private val ocrService: OcrService,
    private val languages: OcrLanguageRepository,
    private val resolver: ContentResolver,
    private val tmpDir: File,
    private val scope: CoroutineScope,
) {
    val state = MutableStateFlow(OcrUiState())
    private var job: Job? = null

    fun refreshLanguages() {
        scope.launch {
            val installed = languages.getInstalledLanguages()
            val active = languages.getActiveLanguages()
            state.update { it.copy(installed = installed, active = active) }
        }
    }

    fun setLanguage(code: String, enabled: Boolean) {
        scope.launch {
            languages.setLanguageEnabled(code, enabled)
            refreshLanguages()
        }
    }

    fun cancel() { job?.cancel() }

    fun clear() { state.update { it.copy(text = "", sourceName = null, error = null, status = null) } }

    fun recognize(uri: Uri, isPdf: Boolean) {
        if (state.value.running) return
        job = scope.launch(Dispatchers.IO) {
            state.update { it.copy(running = true, error = null, text = "", status = "Preparing…") }
            val file = File(tmpDir.apply { mkdirs() }, UUID.randomUUID().toString())
            try {
                val active = languages.getActiveLanguages()
                if (active.isEmpty() || ocrService.languageString().isEmpty()) {
                    state.update { it.copy(running = false, error = "No OCR language is active. Install and enable a language first (English / Bengali).", status = null) }
                    return@launch
                }
                val name = resolver.displayNameOf(uri)
                resolver.copyUriTo(uri, file)
                val text = if (isPdf) ocrPdf(file) else ocrImage(file)
                state.update {
                    it.copy(
                        running = false, sourceName = name, status = null, text = text,
                        error = if (text.isBlank()) "No text was recognised. Check the OCR language and image quality (handwriting is not supported)." else null,
                    )
                }
            } catch (e: CancellationException) {
                state.update { it.copy(running = false, status = null, error = "Cancelled.") }
                throw e
            } catch (e: OutOfMemoryError) {
                state.update { it.copy(running = false, status = null, error = "Not enough memory for this file.") }
            } catch (e: Exception) {
                state.update { it.copy(running = false, status = null, error = "OCR failed: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                file.delete()
            }
        }
    }

    private suspend fun ocrImage(file: File): String {
        val bmp = decodeBitmapFile(file, 3000) ?: error("This file is not a supported image.")
        state.update { it.copy(status = "Recognising text…") }
        return try { OcrTextBuilder.build(ocrService.runOcr(bmp)) } finally { bmp.recycle() }
    }

    private suspend fun ocrPdf(file: File): String {
        val renderer = PdfPageRenderer.open(file) ?: error("The PDF could not be opened (corrupt or password-protected).")
        val sb = StringBuilder()
        try {
            val total = renderer.pageCount
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                state.update { it.copy(status = "Recognising page ${i + 1} of $total…") }
                val (bmp, _) = renderer.renderDpi(i, 200)
                try {
                    val t = OcrTextBuilder.build(ocrService.runOcr(bmp))
                    if (t.isNotBlank()) sb.append("--- Page ${i + 1} ---\n").append(t).append("\n\n")
                } finally { bmp.recycle() }
            }
        } finally { renderer.close() }
        return sb.toString().trim()
    }
}
