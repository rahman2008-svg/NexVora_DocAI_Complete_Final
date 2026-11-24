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
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import com.nexvora.docai.core.common.sanitizeFileName
import com.nexvora.docai.core.common.splitExtension
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PdfOpException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class PdfInput(val name: String, val file: File, val pages: Int?, val sizeBytes: Long)

data class PdfOutput(val file: File, val displayName: String)

enum class CompressPreset(val label: String, val dpi: Int, val quality: Int) {
    HIGH("High quality (150 dpi)", 150, 80),
    MEDIUM("Balanced (110 dpi)", 110, 65),
    LOW("Smallest (80 dpi)", 80, 50),
}

sealed interface PdfRequest {
    data class Merge(val name: String) : PdfRequest
    data class Split(val groups: List<List<Int>>) : PdfRequest
    data class Compress(val preset: CompressPreset) : PdfRequest
    data class Rotate(val degrees: Int, val pages: List<Int>?) : PdfRequest
    data class SelectPages(val pages: List<Int>, val suffix: String) : PdfRequest
    data class ImagesToPdf(val name: String) : PdfRequest
    data class ToImages(val dpi: Int, val png: Boolean) : PdfRequest
    data class Watermark(val text: String, val opacity: Float) : PdfRequest
    data class Password(val password: String) : PdfRequest
    data class Unlock(val password: String) : PdfRequest
    data class PageNumbers(val startAt: Int) : PdfRequest
    data object ToText : PdfRequest
}

/**
 * Local PDF operations built on pdfbox-android (structure edits) and the platform PdfRenderer
 * (rasterising). Every function reads/writes files only; nothing is sent over the network.
 */
class PdfEngine(private val workDir: File) {

    fun inspect(file: File, name: String): PdfInput {
        val pages = PdfPageRenderer.open(file)?.use { it.pageCount }
            ?: throw PdfOpException("\"$name\" could not be opened. It may be corrupt or password-protected.")
        if (pages == 0) throw PdfOpException("\"$name\" has no pages.")
        return PdfInput(name, file, pages, file.length())
    }

    suspend fun execute(
        request: PdfRequest,
        inputs: List<PdfInput>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<PdfOutput> = withContext(Dispatchers.IO) {
        if (inputs.isEmpty()) throw PdfOpException("Choose at least one file first.")
        workDir.mkdirs()
        val outputs = when (request) {
            is PdfRequest.Merge -> listOf(merge(inputs, request.name, onProgress))
            is PdfRequest.Split -> split(inputs.first(), request.groups, onProgress)
            is PdfRequest.Compress -> listOf(compress(inputs.first(), request.preset, onProgress))
            is PdfRequest.Rotate -> listOf(rotate(inputs.first(), request.degrees, request.pages, onProgress))
            is PdfRequest.SelectPages -> listOf(selectPages(inputs.first(), request.pages, request.suffix, onProgress))
            is PdfRequest.ImagesToPdf -> listOf(imagesToPdf(inputs, request.name, onProgress))
            is PdfRequest.ToImages -> toImages(inputs.first(), request.dpi, request.png, onProgress)
            is PdfRequest.Watermark -> listOf(watermark(inputs.first(), request.text, request.opacity, onProgress))
            is PdfRequest.Password -> listOf(addPassword(inputs.first(), request.password))
            is PdfRequest.Unlock -> listOf(unlock(inputs.first(), request.password))
            is PdfRequest.PageNumbers -> listOf(pageNumbers(inputs.first(), request.startAt, onProgress))
            PdfRequest.ToText -> listOf(toText(inputs.first(), onProgress))
        }
        verify(request, inputs, outputs)
        outputs
    }

    // ---- verification: never report success for an output that cannot be read back -------------

    private fun verify(request: PdfRequest, inputs: List<PdfInput>, outputs: List<PdfOutput>) {
        fun fail(msg: String): Nothing {
            outputs.forEach { it.file.parentFile?.deleteRecursively() }
            throw PdfOpException("Output verification failed: $msg")
        }
        if (outputs.isEmpty()) fail("no file was produced")
        for (o in outputs) if (!o.file.exists() || o.file.length() == 0L) fail("${o.displayName} is empty")
        val expected: List<Int>? = when (request) {
            is PdfRequest.Merge -> listOf(inputs.sumOf { it.pages ?: 0 })
            is PdfRequest.Split -> request.groups.map { it.size }
            is PdfRequest.Compress, is PdfRequest.Watermark, is PdfRequest.PageNumbers -> listOf(inputs.first().pages ?: 0)
            is PdfRequest.Rotate, is PdfRequest.Unlock -> listOf(inputs.first().pages ?: 0).takeIf { request is PdfRequest.Rotate }
            is PdfRequest.SelectPages -> listOf(request.pages.size)
            is PdfRequest.ImagesToPdf -> listOf(inputs.size)
            else -> null
        }
        when (request) {
            is PdfRequest.Password -> {
                val out = outputs.single().file
                val opens = try { PDDocument.load(out, request.password).useDoc { it.isEncrypted && it.numberOfPages > 0 } } catch (_: Exception) { false }
                if (!opens) fail("the protected file cannot be opened with the password")
                val openWithoutPassword = try { PDDocument.load(out).close(); true } catch (_: Exception) { false }
                if (openWithoutPassword) fail("the file is not actually password-protected")
            }
            is PdfRequest.ToImages -> for (o in outputs) {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(o.file.path, b)
                if (b.outWidth <= 0) fail("${o.displayName} is not a valid image")
            }
            PdfRequest.ToText -> {}
            else -> outputs.forEachIndexed { i, o ->
                val pages = try { PDDocument.load(o.file).useDoc { it.numberOfPages } } catch (e: Exception) { fail("${o.displayName} cannot be opened (${e.message})") }
                if (pages <= 0) fail("${o.displayName} has no pages")
                if (expected != null && expected.getOrNull(i) != null && pages != expected[i]) fail("${o.displayName} has $pages pages, expected ${expected[i]}")
            }
        }
    }

    /** Document properties for the "PDF Info" tool. Encrypted files cannot be read without their password. */
    suspend fun info(input: PdfInput): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val rows = mutableListOf("File" to input.name, "Size" to com.nexvora.docai.core.common.formatBytes(input.sizeBytes))
        try {
            PDDocument.load(input.file).useDoc { doc ->
                rows += "Pages" to doc.numberOfPages.toString()
                rows += "PDF version" to doc.version.toString()
                val box = doc.getPage(0).mediaBox
                rows += "Page size" to "${Math.round(box.width * 25.4f / 72f)} × ${Math.round(box.height * 25.4f / 72f)} mm"
                val i = doc.documentInformation
                listOf("Title" to i.title, "Author" to i.author, "Subject" to i.subject, "Creator" to i.creator, "Producer" to i.producer)
                    .forEach { (k, v) -> if (!v.isNullOrBlank()) rows += k to v }
                i.creationDate?.let { rows += "Created" to java.text.DateFormat.getDateTimeInstance().format(it.time) }
            }
        } catch (e: IOException) {
            rows += "Details" to if (e.javaClass.simpleName == "InvalidPasswordException") "Password-protected" else "Unreadable: ${e.message}"
        }
        rows
    }

    private fun unlock(input: PdfInput, password: String): PdfOutput {
        val out = outFile("${baseOf(input)}_unlocked", "pdf")
        val doc = try {
            PDDocument.load(input.file, password)
        } catch (e: IOException) {
            if (e.javaClass.simpleName == "InvalidPasswordException") throw PdfOpException("The password is wrong.", e)
            throw PdfOpException("\"${input.name}\" could not be read: ${e.message ?: "corrupt file"}", e)
        }
        doc.useDoc {
            if (!it.isEncrypted) throw PdfOpException("This PDF is not password-protected.")
            it.setAllSecurityToBeRemoved(true)
            saveTo(it, out)
        }
        return out
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun outFile(name: String, ext: String): PdfOutput {
        val dir = File(workDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val clean = sanitizeFileName(name, "output")
        return PdfOutput(File(dir, "$clean.$ext"), "$clean.$ext")
    }

    private fun baseOf(input: PdfInput) = splitExtension(input.name).first

    private fun load(input: PdfInput): PDDocument = try {
        PDDocument.load(input.file)
    } catch (e: IOException) {
        if (e.javaClass.simpleName == "InvalidPasswordException") {
            throw PdfOpException("\"${input.name}\" is password-protected. Encrypted PDFs are not supported by this tool.", e)
        }
        throw PdfOpException("\"${input.name}\" could not be read: ${e.message ?: "corrupt file"}", e)
    }

    private fun saveTo(doc: PDDocument, out: PdfOutput) {
        try {
            doc.save(out.file)
        } catch (e: IOException) {
            out.file.delete()
            throw PdfOpException("Saving failed (is the storage full?): ${e.message}", e)
        }
    }

    private inline fun <T> PDDocument.useDoc(block: (PDDocument) -> T): T =
        try { block(this) } finally { runCatching { close() } }

    // ---- structure operations ------------------------------------------------------------

    private suspend fun merge(inputs: List<PdfInput>, name: String, onProgress: (Int, Int) -> Unit): PdfOutput {
        if (inputs.size < 2) throw PdfOpException("Choose at least two PDF files to merge.")
        val sources = mutableListOf<PDDocument>()
        val out = outFile(name.ifBlank { "merged" }, "pdf")
        val target = PDDocument()
        try {
            inputs.forEachIndexed { i, input ->
                currentCoroutineContext().ensureActive()
                val doc = load(input).also { sources.add(it) }
                for (page in doc.pages) target.importPage(page)
                onProgress(i + 1, inputs.size)
            }
            saveTo(target, out)
        } finally {
            runCatching { target.close() }
            sources.forEach { runCatching { it.close() } }
        }
        return out
    }

    private suspend fun split(input: PdfInput, groups: List<List<Int>>, onProgress: (Int, Int) -> Unit): List<PdfOutput> {
        val results = mutableListOf<PdfOutput>()
        load(input).useDoc { src ->
            groups.forEachIndexed { i, pages ->
                currentCoroutineContext().ensureActive()
                val target = PDDocument()
                try {
                    pages.forEach { target.importPage(src.getPage(it)) }
                    val out = outFile("${baseOf(input)}_part${i + 1}", "pdf")
                    saveTo(target, out)
                    results.add(out)
                } finally {
                    runCatching { target.close() }
                }
                onProgress(i + 1, groups.size)
            }
        }
        return results
    }

    private suspend fun selectPages(input: PdfInput, pages: List<Int>, suffix: String, onProgress: (Int, Int) -> Unit): PdfOutput {
        if (pages.isEmpty()) throw PdfOpException("No pages selected.")
        val out = outFile("${baseOf(input)}_$suffix", "pdf")
        load(input).useDoc { src ->
            val target = PDDocument()
            try {
                pages.forEachIndexed { i, p ->
                    currentCoroutineContext().ensureActive()
                    target.importPage(src.getPage(p))
                    onProgress(i + 1, pages.size)
                }
                saveTo(target, out)
            } finally {
                runCatching { target.close() }
            }
        }
        return out
    }

    private suspend fun rotate(input: PdfInput, degrees: Int, pages: List<Int>?, onProgress: (Int, Int) -> Unit): PdfOutput {
        val out = outFile("${baseOf(input)}_rotated", "pdf")
        load(input).useDoc { doc ->
            val indices = pages ?: (0 until doc.numberOfPages).toList()
            indices.forEachIndexed { i, p ->
                currentCoroutineContext().ensureActive()
                val page = doc.getPage(p)
                page.rotation = (((page.rotation + degrees) % 360) + 360) % 360
                onProgress(i + 1, indices.size)
            }
            saveTo(doc, out)
        }
        return out
    }

    private fun addPassword(input: PdfInput, password: String): PdfOutput {
        if (password.length < 4) throw PdfOpException("Use a password with at least 4 characters.")
        val out = outFile("${baseOf(input)}_protected", "pdf")
        load(input).useDoc { doc ->
            val policy = StandardProtectionPolicy(UUID.randomUUID().toString(), password, AccessPermission())
            policy.setEncryptionKeyLength(128)
            policy.setPreferAES(true)
            doc.protect(policy)
            saveTo(doc, out)
        }
        return out
    }

    // ---- page content operations ---------------------------------------------------------

    private fun requireLatin(text: String) {
        if (text.isBlank()) throw PdfOpException("Enter some text first.")
        if (text.any { it.code !in 32..255 }) {
            throw PdfOpException("Watermark text is limited to Latin characters (the built-in PDF font cannot draw Bangla or emoji).")
        }
    }

    private suspend fun watermark(input: PdfInput, text: String, opacity: Float, onProgress: (Int, Int) -> Unit): PdfOutput {
        requireLatin(text)
        val out = outFile("${baseOf(input)}_watermarked", "pdf")
        load(input).useDoc { doc ->
            val font = PDType1Font.HELVETICA_BOLD
            val total = doc.numberOfPages
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val page = doc.getPage(i)
                val box = page.mediaBox
                val w = box.width
                val h = box.height
                val fontSize = (min(w, h) / (text.length * 0.55f).coerceAtLeast(4f)).coerceIn(24f, 96f)
                val textWidth = font.getStringWidth(text) / 1000f * fontSize
                val angle = Math.toRadians(45.0)
                val tx = box.lowerLeftX + w / 2f - (textWidth / 2f) * cos(angle).toFloat()
                val ty = box.lowerLeftY + h / 2f - (textWidth / 2f) * sin(angle).toFloat()
                PDPageContentStream(doc, page, AppendMode.APPEND, true, true).use { cs ->
                    val gs = PDExtendedGraphicsState()
                    gs.setNonStrokingAlphaConstant(opacity.coerceIn(0.05f, 1f))
                    cs.setGraphicsStateParameters(gs)
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.setNonStrokingColor(0.45f, 0.35f, 0.75f)
                    cs.setTextMatrix(Matrix.getRotateInstance(angle, tx, ty))
                    cs.showText(text)
                    cs.endText()
                }
                onProgress(i + 1, total)
            }
            saveTo(doc, out)
        }
        return out
    }

    private suspend fun pageNumbers(input: PdfInput, startAt: Int, onProgress: (Int, Int) -> Unit): PdfOutput {
        val out = outFile("${baseOf(input)}_numbered", "pdf")
        load(input).useDoc { doc ->
            val font = PDType1Font.HELVETICA
            val size = 10f
            val total = doc.numberOfPages
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val page = doc.getPage(i)
                val box = page.mediaBox
                val label = "${i + startAt} / ${total + startAt - 1}"
                val width = font.getStringWidth(label) / 1000f * size
                PDPageContentStream(doc, page, AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.setNonStrokingColor(0.2f, 0.2f, 0.2f)
                    cs.newLineAtOffset(box.lowerLeftX + (box.width - width) / 2f, box.lowerLeftY + 18f)
                    cs.showText(label)
                    cs.endText()
                }
                onProgress(i + 1, total)
            }
            saveTo(doc, out)
        }
        return out
    }

    private suspend fun toText(input: PdfInput, onProgress: (Int, Int) -> Unit): PdfOutput {
        val out = outFile("${baseOf(input)}_text", "txt")
        val sb = StringBuilder()
        load(input).useDoc { doc ->
            val stripper = PDFTextStripper()
            stripper.setSortByPosition(true)
            val total = doc.numberOfPages
            for (i in 1..total) {
                currentCoroutineContext().ensureActive()
                stripper.setStartPage(i)
                stripper.setEndPage(i)
                val text = stripper.getText(doc).trim()
                if (text.isNotEmpty()) sb.append("--- Page ").append(i).append(" ---\n").append(text).append("\n\n")
                onProgress(i, total)
            }
        }
        if (sb.isBlank()) {
            throw PdfOpException("No selectable text was found. This looks like a scanned PDF: use OCR instead.")
        }
        out.file.writeText(sb.toString())
        return out
    }

    // ---- raster based operations ---------------------------------------------------------

    private suspend fun compress(input: PdfInput, preset: CompressPreset, onProgress: (Int, Int) -> Unit): PdfOutput {
        val renderer = PdfPageRenderer.open(input.file)
            ?: throw PdfOpException("\"${input.name}\" could not be rendered (corrupt or encrypted).")
        val out = outFile("${baseOf(input)}_compressed", "pdf")
        val target = PDDocument()
        try {
            val total = renderer.pageCount
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val (bmp, size) = renderer.renderDpi(i, preset.dpi)
                val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, preset.quality, it) }.toByteArray()
                bmp.recycle()
                val page = PDPage(PDRectangle(size.first.toFloat(), size.second.toFloat()))
                target.addPage(page)
                val image = JPEGFactory.createFromByteArray(target, jpeg)
                PDPageContentStream(target, page).use { cs ->
                    cs.drawImage(image, 0f, 0f, size.first.toFloat(), size.second.toFloat())
                }
                onProgress(i + 1, total)
            }
            saveTo(target, out)
        } finally {
            runCatching { target.close() }
            renderer.close()
        }
        return out
    }

    private suspend fun imagesToPdf(inputs: List<PdfInput>, name: String, onProgress: (Int, Int) -> Unit): PdfOutput {
        val out = outFile(name.ifBlank { "images" }, "pdf")
        val target = PDDocument()
        try {
            inputs.forEachIndexed { i, input ->
                currentCoroutineContext().ensureActive()
                val bmp = decodeImage(input) ?: throw PdfOpException("\"${input.name}\" is not a supported image.")
                val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
                val jpeg = ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
                val iw = flat.width.toFloat()
                val ih = flat.height.toFloat()
                if (bmp !== flat) bmp.recycle()
                flat.recycle()
                val (pw, ph) = if (iw >= ih) 842f to 595f else 595f to 842f
                val scale = min(pw / iw, ph / ih)
                val dw = iw * scale
                val dh = ih * scale
                val page = PDPage(PDRectangle(pw, ph))
                target.addPage(page)
                val image = JPEGFactory.createFromByteArray(target, jpeg)
                PDPageContentStream(target, page).use { cs ->
                    cs.drawImage(image, (pw - dw) / 2f, (ph - dh) / 2f, dw, dh)
                }
                onProgress(i + 1, inputs.size)
            }
            saveTo(target, out)
        } finally {
            runCatching { target.close() }
        }
        return out
    }

    private suspend fun toImages(input: PdfInput, dpi: Int, png: Boolean, onProgress: (Int, Int) -> Unit): List<PdfOutput> {
        val renderer = PdfPageRenderer.open(input.file)
            ?: throw PdfOpException("\"${input.name}\" could not be rendered (corrupt or encrypted).")
        val results = mutableListOf<PdfOutput>()
        try {
            val total = renderer.pageCount
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val (bmp, _) = renderer.renderDpi(i, dpi)
                val out = outFile("${baseOf(input)}_page${i + 1}", if (png) "png" else "jpg")
                try {
                    out.file.outputStream().use {
                        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
                    }
                } catch (e: IOException) {
                    throw PdfOpException("Saving failed (is the storage full?): ${e.message}", e)
                } finally {
                    bmp.recycle()
                }
                results.add(out)
                onProgress(i + 1, total)
            }
        } finally {
            renderer.close()
        }
        return results
    }

    /** Decodes an image honouring EXIF orientation on Android 9+ and capping the size to save memory. */
    private fun decodeImage(input: PdfInput, maxSide: Int = 2600): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(input.file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val m = max(info.size.width, info.size.height)
                if (m > maxSide) {
                    val s = maxSide.toFloat() / m
                    decoder.setTargetSize(
                        (info.size.width * s).toInt().coerceAtLeast(1),
                        (info.size.height * s).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(input.file.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            BitmapFactory.decodeFile(input.file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) {
        null
    }
}
