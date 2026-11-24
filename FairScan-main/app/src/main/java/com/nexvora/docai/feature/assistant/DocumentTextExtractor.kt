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
package com.nexvora.docai.feature.assistant

import com.nexvora.docai.core.common.PageText
import com.nexvora.docai.domain.OcrService
import com.nexvora.docai.feature.ocr.OcrTextBuilder
import com.nexvora.docai.feature.pdf.PdfPageRenderer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Page-aware text extraction: embedded text first, OCR only on request. */
class DocumentTextExtractor(private val ocrService: OcrService) {

    suspend fun extractEmbedded(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<PageText> =
        withContext(Dispatchers.IO) {
            if (!file.name.endsWith(".pdf", true)) {
                return@withContext listOf(PageText(1, file.readText()))
            }
            val doc = PDDocument.load(file)
            try {
                val stripper = PDFTextStripper().apply { setSortByPosition(true) }
                val total = doc.numberOfPages
                (1..total).map { p ->
                    currentCoroutineContext().ensureActive()
                    stripper.setStartPage(p)
                    stripper.setEndPage(p)
                    onProgress(p, total)
                    PageText(p, stripper.getText(doc).trim())
                }
            } finally {
                runCatching { doc.close() }
            }
        }

    suspend fun extractWithOcr(file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<PageText> =
        withContext(Dispatchers.IO) {
            val renderer = PdfPageRenderer.open(file) ?: error("The PDF could not be opened.")
            try {
                val total = renderer.pageCount
                (0 until total).map { i ->
                    currentCoroutineContext().ensureActive()
                    onProgress(i + 1, total)
                    val (bmp, _) = renderer.renderDpi(i, 200)
                    try { PageText(i + 1, OcrTextBuilder.build(ocrService.runOcr(bmp))) } finally { bmp.recycle() }
                }
            } finally {
                renderer.close()
            }
        }
}
