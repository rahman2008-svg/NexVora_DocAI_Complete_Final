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

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.nexvora.docai.core.common.NxShare
import com.nexvora.docai.core.common.PageRanges
import com.nexvora.docai.core.common.formatBytes
import com.nexvora.docai.core.design.NexVoraButton
import com.nexvora.docai.core.design.NexVoraCard
import com.nexvora.docai.core.design.NexVoraGradientButton
import com.nexvora.docai.core.design.NexVoraLoadingIndicator
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxBannerKind
import com.nexvora.docai.core.design.NxChip
import com.nexvora.docai.core.design.NxStatusBanner
import com.nexvora.docai.core.design.NxTextField
import com.nexvora.docai.feature.vault.VaultDoc
import com.nexvora.docai.feature.vault.VaultRepository

enum class PdfTool(val label: String, val icon: ImageVector, val color: Color) {
    MERGE("Merge PDF", Icons.Rounded.CallMerge, Color(0xFF8957FF)),
    SPLIT("Split PDF", Icons.Rounded.CallSplit, Color(0xFF8957FF)),
    COMPRESS("Compress", Icons.Rounded.Compress, Color(0xFF8957FF)),
    ROTATE("Rotate", Icons.Rounded.RotateRight, Color(0xFF00D5FF)),
    REORDER("Reorder", Icons.Rounded.Reorder, Color(0xFF00D5FF)),
    EXTRACT("Extract", Icons.Rounded.ContentCut, Color(0xFF00D5FF)),
    IMAGE_TO_PDF("Image to PDF", Icons.Rounded.Image, Color(0xFFFFB84D)),
    PDF_TO_IMAGES("PDF to Image", Icons.Rounded.Collections, Color(0xFFFFB84D)),
    WATERMARK("Watermark", Icons.Rounded.WaterDrop, Color(0xFFFFB84D)),
    PASSWORD("Add Password", Icons.Rounded.Lock, Color(0xFFFF5C7A)),
    PAGE_NUMBERS("Add Page Number", Icons.Rounded.FormatListNumbered, Color(0xFFFF5C7A)),
    MORE("More Tools", Icons.Rounded.MoreHoriz, Color(0xFFA9ACCC)),
    DELETE_PAGES("Delete Pages", Icons.Rounded.DeleteSweep, Color(0xFF8957FF)),
    TO_TEXT("PDF to Text", Icons.Rounded.TextSnippet, Color(0xFF00D5FF)),
    INFO("PDF Info", Icons.Rounded.Info, Color(0xFF00D5FF)),
    UNLOCK("Remove Password", Icons.Rounded.LockOpen, Color(0xFFFF5C7A)),
}

private val MORE_TOOLS = listOf(PdfTool.DELETE_PAGES, PdfTool.TO_TEXT, PdfTool.INFO, PdfTool.UNLOCK)

private val MAIN_TOOLS = PdfTool.entries.take(12)

@Composable
fun PdfStudioScreen(
    controller: PdfController,
    vault: VaultRepository,
    onBack: () -> Unit,
    onOpenPdf: (VaultDoc) -> Unit,
    onPreview: (java.io.File, String) -> Unit,
) {
    var toolName by rememberSaveable { mutableStateOf<String?>(null) }
    val tool = toolName?.let { n -> PdfTool.entries.firstOrNull { it.name == n } }
    BackHandler(enabled = tool != null) {
        toolName = if (tool in MORE_TOOLS) PdfTool.MORE.name else null
        controller.clear()
    }
    Column(Modifier.fillMaxSize()) {
        NexVoraTopBar(
            title = if (tool == null) "PDF Tools" else tool.label,
            onBack = {
                if (tool == null) onBack() else {
                    toolName = if (tool in MORE_TOOLS) PdfTool.MORE.name else null
                    controller.clear()
                }
            },
        )
        when (tool) {
            null -> ToolGrid(MAIN_TOOLS) { toolName = it.name }
            PdfTool.MORE -> ToolGrid(MORE_TOOLS) { toolName = it.name }
            else -> ToolScreen(tool, controller, vault, onOpenPdf, onPreview)
        }
    }
}

@Composable
private fun ToolGrid(tools: List<PdfTool>, onPick: (PdfTool) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t ->
                    NexVoraCard(Modifier.weight(1f), onClick = { onPick(t) }, contentPadding = 12) {
                        Icon(t.icon, null, tint = t.color, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(t.label, color = Nx.TextMain, fontSize = 13.sp, maxLines = 2)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        NxStatusBanner("All PDF tools run on this device. Results are saved in Files › PDF Studio.")
    }
}

@Composable
private fun ToolScreen(tool: PdfTool, controller: PdfController, vault: VaultRepository, onOpenPdf: (VaultDoc) -> Unit, onPreview: (java.io.File, String) -> Unit) {
    val context = LocalContext.current
    val inputs by controller.inputs.collectAsState()
    val job by controller.job.collectAsState()
    val imagesTool = tool == PdfTool.IMAGE_TO_PDF
    val rawInput = imagesTool || tool == PdfTool.UNLOCK // no PDF inspection (images, or PDFs that need a password)
    val multi = tool == PdfTool.MERGE || imagesTool

    // Tool options (kept per tool visit)
    var name by remember(tool) { mutableStateOf(if (tool == PdfTool.MERGE) "merged" else "images") }
    var ranges by remember(tool) { mutableStateOf("") }
    var preset by remember(tool) { mutableStateOf(CompressPreset.MEDIUM) }
    var degrees by remember(tool) { mutableStateOf(90) }
    var png by remember(tool) { mutableStateOf(true) }
    var dpi by remember(tool) { mutableStateOf(150) }
    var wmText by remember(tool) { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember(tool) { mutableStateOf(0.3f) }
    var pw1 by remember(tool) { mutableStateOf("") }
    var pw2 by remember(tool) { mutableStateOf("") }
    var startAt by remember(tool) { mutableStateOf("1") }
    var localError by remember(tool) { mutableStateOf<String?>(null) }

    val first = inputs.firstOrNull()
    // Reorder: pre-fill the order once a file is chosen
    LaunchedEffect(tool, first?.file) {
        if (tool == PdfTool.REORDER) ranges = first?.pages?.let { n -> (1..n).joinToString(",") } ?: ""
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        localError = null
        controller.addInputs(uris, images = rawInput, append = multi)
    }
    val mime = if (imagesTool) arrayOf("image/*") else arrayOf("application/pdf")

    fun start() {
        localError = null
        val pages = first?.pages ?: 0
        val request: PdfRequest? = when (tool) {
            PdfTool.MERGE -> PdfRequest.Merge(name)
            PdfTool.SPLIT -> PageRanges.parseGroups(ranges, pages).let { if (it.ok) PdfRequest.Split(it.groups) else { localError = it.error; null } }
            PdfTool.COMPRESS -> PdfRequest.Compress(preset)
            PdfTool.ROTATE -> if (ranges.isBlank()) PdfRequest.Rotate(degrees, null)
            else PageRanges.parse(ranges, pages).let { if (it.ok) PdfRequest.Rotate(degrees, it.pages.distinct()) else { localError = it.error; null } }
            PdfTool.REORDER -> PageRanges.parse(ranges, pages).let {
                when {
                    !it.ok -> { localError = it.error; null }
                    it.pages.sorted() != (0 until pages).toList() -> { localError = "Use every page exactly once (e.g. 3,1,2)."; null }
                    else -> PdfRequest.SelectPages(it.pages, "reordered")
                }
            }
            PdfTool.EXTRACT -> PageRanges.parse(ranges, pages).let { if (it.ok) PdfRequest.SelectPages(it.pages, "extract") else { localError = it.error; null } }
            PdfTool.DELETE_PAGES -> PageRanges.parse(ranges, pages).let {
                if (!it.ok) { localError = it.error; null } else {
                    val keep = (0 until pages).filter { p -> p !in it.pages.toSet() }
                    if (keep.isEmpty()) { localError = "You cannot delete every page."; null } else PdfRequest.SelectPages(keep, "trimmed")
                }
            }
            PdfTool.IMAGE_TO_PDF -> PdfRequest.ImagesToPdf(name)
            PdfTool.PDF_TO_IMAGES -> PdfRequest.ToImages(dpi, png)
            PdfTool.WATERMARK -> PdfRequest.Watermark(wmText, opacity)
            PdfTool.PASSWORD -> if (pw1 != pw2) { localError = "The two passwords do not match."; null } else PdfRequest.Password(pw1)
            PdfTool.PAGE_NUMBERS -> startAt.toIntOrNull()?.takeIf { it in 0..9999 }?.let { PdfRequest.PageNumbers(it) } ?: run { localError = "Enter a valid start number."; null }
            PdfTool.TO_TEXT -> PdfRequest.ToText
            PdfTool.UNLOCK -> if (pw1.isEmpty()) { localError = "Enter the PDF's password."; null } else PdfRequest.Unlock(pw1)
            PdfTool.INFO, PdfTool.MORE -> null
        }
        if (request != null) controller.run(request)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NexVoraButton(
            if (inputs.isEmpty()) (if (imagesTool) "Choose images" else if (multi) "Choose PDF files" else "Choose a PDF")
            else (if (multi) "Add more files" else "Choose another file"),
            onClick = { picker.launch(mime) },
            icon = Icons.Rounded.UploadFile,
            modifier = Modifier.fillMaxWidth(),
            enabled = !job.running,
        )
        inputs.forEachIndexed { i, input ->
            NexVoraCard(contentPadding = 10) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (imagesTool) Icons.Rounded.Image else Icons.Rounded.PictureAsPdf, null, tint = Nx.Violet)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(input.name, color = Nx.TextMain, fontSize = 14.sp, maxLines = 1)
                        Text(
                            listOfNotNull(input.pages?.let { "$it page(s)" }, formatBytes(input.sizeBytes)).joinToString(" · "),
                            color = Nx.TextSecondary, fontSize = 12.sp,
                        )
                    }
                    if (multi && inputs.size > 1) {
                        IconButton(onClick = { controller.move(i, -1) }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, "Move up", tint = Nx.TextSecondary) }
                        IconButton(onClick = { controller.move(i, 1) }, enabled = i < inputs.size - 1) { Icon(Icons.Rounded.ArrowDownward, "Move down", tint = Nx.TextSecondary) }
                    }
                    IconButton(onClick = { controller.remove(i) }, enabled = !job.running) { Icon(Icons.Rounded.Close, "Remove", tint = Nx.TextSecondary) }
                }
            }
        }

        if (!imagesTool && tool != PdfTool.UNLOCK && first != null) {
            NexVoraButton("Preview", { onPreview(first.file, first.name) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PictureAsPdf)
        }
        // ---- per-tool options ----
        when (tool) {
            PdfTool.MERGE, PdfTool.IMAGE_TO_PDF -> {
                NxTextField(name, { name = it }, "Output file name", Modifier.fillMaxWidth())
                if (tool == PdfTool.MERGE) NxStatusBanner("Pages are merged in the order shown. Bookmarks and form fields of the source files are not preserved.")
                else NxStatusBanner("Each image becomes one page, in the order shown. Pages are fitted to A4 (portrait or landscape).")
            }
            PdfTool.SPLIT -> {
                NxTextField(ranges, { ranges = it }, "Ranges, e.g. 1-3, 4-6, 7", Modifier.fillMaxWidth())
                Text("Each comma-separated range becomes its own PDF file.", color = Nx.TextSecondary, fontSize = 12.sp)
                first?.pages?.let { n ->
                    if (n <= 100) NxChip("Split into single pages", false, { ranges = (1..n).joinToString(",") })
                }
            }
            PdfTool.COMPRESS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompressPreset.entries.forEach { p -> NxChip(p.label.substringBefore(" ("), preset == p, { preset = p }) }
                }
                Text(preset.label, color = Nx.TextSecondary, fontSize = 12.sp)
                NxStatusBanner(
                    "Compression re-renders every page as an image. Text will no longer be selectable or searchable, and image-heavy PDFs may not get smaller. The original is never modified.",
                    NxBannerKind.WARNING,
                )
            }
            PdfTool.ROTATE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NxChip("90° right", degrees == 90, { degrees = 90 })
                    NxChip("180°", degrees == 180, { degrees = 180 })
                    NxChip("90° left", degrees == 270, { degrees = 270 })
                }
                NxTextField(ranges, { ranges = it }, "Pages (empty = all pages)", Modifier.fillMaxWidth())
            }
            PdfTool.REORDER -> {
                NxTextField(ranges, { ranges = it }, "New page order, e.g. 3,1,2", Modifier.fillMaxWidth())
                first?.pages?.let { n -> NxChip("Reverse order", false, { ranges = (n downTo 1).joinToString(",") }) }
                Text("List every page of the document exactly once.", color = Nx.TextSecondary, fontSize = 12.sp)
            }
            PdfTool.EXTRACT, PdfTool.DELETE_PAGES -> {
                NxTextField(ranges, { ranges = it }, if (tool == PdfTool.EXTRACT) "Pages to extract, e.g. 1-3, 5" else "Pages to delete, e.g. 2, 4-5", Modifier.fillMaxWidth())
                if (tool == PdfTool.DELETE_PAGES) NxStatusBanner("A new PDF is created without these pages. The original stays unchanged.")
            }
            PdfTool.PDF_TO_IMAGES -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NxChip("PNG", png, { png = true }); NxChip("JPEG", !png, { png = false })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 150, 200).forEach { d -> NxChip("$d dpi", dpi == d, { dpi = d }) }
                }
                NxStatusBanner("One image per page. Long documents at high resolution need a lot of memory and storage.")
            }
            PdfTool.WATERMARK -> {
                NxTextField(wmText, { wmText = it }, "Watermark text (Latin characters)", Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.15f, 0.3f, 0.5f).forEach { o -> NxChip("${(o * 100).toInt()}%", opacity == o, { opacity = o }) }
                }
            }
            PdfTool.PASSWORD -> {
                NxTextField(pw1, { pw1 = it }, "Password", Modifier.fillMaxWidth())
                NxTextField(pw2, { pw2 = it }, "Repeat password", Modifier.fillMaxWidth())
                NxStatusBanner("The password cannot be recovered. The PDF is encrypted with standard PDF AES-128 encryption; keep the original if you may forget the password.", NxBannerKind.WARNING)
            }
            PdfTool.PAGE_NUMBERS -> {
                OutlinedStart(startAt) { startAt = it }
            }
            PdfTool.TO_TEXT -> NxStatusBanner("Extracts the selectable text of each page into a .txt file. Scanned PDFs need OCR instead.")
            PdfTool.UNLOCK -> {
                NxTextField(pw1, { pw1 = it }, "Current password", Modifier.fillMaxWidth())
                NxStatusBanner("Creates an unprotected copy. You must know the password; nothing is cracked or bypassed.")
            }
            PdfTool.INFO -> {
                var rows by remember(first?.file) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                LaunchedEffect(first?.file) { rows = if (first == null) emptyList() else controller.info() }
                if (rows.isNotEmpty()) NexVoraCard {
                    rows.forEach { (k, v) -> Text("$k: $v", color = Nx.TextMain, fontSize = 13.sp) }
                }
            }
            PdfTool.MORE -> {}
        }

        val canRun = inputs.isNotEmpty() && !job.running && (tool != PdfTool.MERGE || inputs.size >= 2)
        if (tool == PdfTool.INFO) { /* read-only tool: nothing to run */ } else
        if (job.running) {
            NexVoraLoadingIndicator(progress = job.progress, linear = true)
            Text("Working… ${job.done}/${job.total}", color = Nx.TextSecondary, fontSize = 12.sp)
            NexVoraButton("Cancel", onClick = { controller.cancel() }, modifier = Modifier.fillMaxWidth(), destructive = true)
        } else {
            NexVoraGradientButton("Run", onClick = ::start, enabled = canRun, modifier = Modifier.fillMaxWidth())
        }
        (localError ?: job.error)?.let { NxStatusBanner(it, NxBannerKind.ERROR) }
        if (!job.running && job.results.isNotEmpty()) {
            job.message?.let { NxStatusBanner(it, NxBannerKind.SUCCESS) }
            job.results.forEach { doc ->
                NexVoraCard(contentPadding = 12) {
                    Text(doc.fileName, color = Nx.TextMain, fontSize = 14.sp, maxLines = 1)
                    Text(formatBytes(doc.sizeBytes), color = Nx.TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexVoraButton("Open", onClick = { if (doc.isPdf) onOpenPdf(doc) else NxShare.open(context, vault.file(doc), doc.mime) })
                        NexVoraButton("Share", onClick = { NxShare.share(context, listOf(vault.file(doc)), doc.mime) })
                    }
                }
            }
            if (job.results.size > 1) {
                NexVoraButton("Share all", onClick = { NxShare.share(context, job.results.map { vault.file(it) }, job.results.first().mime) }, modifier = Modifier.fillMaxWidth())
            }
        } else if (!job.running && job.results.isEmpty()) {
            job.message?.let { NxStatusBanner(it) }
        }
    }
}

@Composable
private fun OutlinedStart(value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text("First page number") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = com.nexvora.docai.core.design.nxTextFieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
