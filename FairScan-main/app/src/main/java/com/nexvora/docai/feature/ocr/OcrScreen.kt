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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.nexvora.docai.core.common.NxShare
import com.nexvora.docai.core.design.NexVoraButton
import com.nexvora.docai.core.design.NexVoraCard
import com.nexvora.docai.core.design.NexVoraLoadingIndicator
import com.nexvora.docai.core.design.NexVoraSearchBar
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxBannerKind
import com.nexvora.docai.core.design.NxChip
import com.nexvora.docai.core.design.NxStatusBanner
import com.nexvora.docai.data.OcrLanguage
import com.nexvora.docai.feature.vault.VaultRepository
import java.util.Locale

@Composable
fun OcrScreen(
    controller: OcrController,
    vault: VaultRepository,
    onBack: () -> Unit,
    onManageLanguages: () -> Unit,
) {
    val context = LocalContext.current
    val s by controller.state.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { controller.refreshLanguages() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> controller.recognize(u, false) } }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> controller.recognize(u, true) } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(s.text.toByteArray()) }
            android.widget.Toast.makeText(context, "Text exported.", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure { android.widget.Toast.makeText(context, "Export failed.", android.widget.Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize()) {
        NexVoraTopBar("OCR – Extract Text", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Language", color = Nx.TextMain, fontSize = 15.sp)
            if (s.installed.isEmpty()) {
                NxStatusBanner("No OCR language is installed. Download English and/or Bengali (about 1–5 MB each) from the language manager. This is the only network use of OCR and starts only when you tap download.", NxBannerKind.WARNING)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.installed.sorted()) { code ->
                        NxChip(OcrLanguage(code).displayName(Locale.getDefault()), code in s.active, { controller.setLanguage(code, code !in s.active) })
                    }
                }
            }
            NexVoraButton("Manage OCR languages", onManageLanguages, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexVoraButton("Image", { pickImage.launch(arrayOf("image/*")) }, Modifier.weight(1f), enabled = !s.running, icon = Icons.Rounded.Image)
                NexVoraButton("PDF", { pickPdf.launch(arrayOf("application/pdf")) }, Modifier.weight(1f), enabled = !s.running, icon = Icons.Rounded.PictureAsPdf)
            }
            if (s.running) {
                NexVoraLoadingIndicator(linear = true)
                Text(s.status ?: "Working…", color = Nx.TextSecondary, fontSize = 12.sp)
                NexVoraButton("Cancel", { controller.cancel() }, Modifier.fillMaxWidth(), destructive = true)
            }
            s.error?.let { NxStatusBanner(it, NxBannerKind.ERROR) }
            if (s.text.isNotBlank()) {
                val matches = if (query.isBlank()) 0 else Regex(Regex.escape(query.trim()), RegexOption.IGNORE_CASE).findAll(s.text).count()
                NexVoraSearchBar(query, { query = it }, placeholder = "Search in extracted text")
                if (query.isNotBlank()) Text("$matches match(es)", color = if (matches > 0) Nx.Cyan else Nx.TextSecondary, fontSize = 12.sp)
                NexVoraCard {
                    s.sourceName?.let { Text(it, color = Nx.TextSecondary, fontSize = 12.sp) }
                    SelectionContainer { Text(s.text, color = Nx.TextMain, fontSize = 14.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexVoraButton("Copy", { NxShare.copyText(context, "OCR text", s.text) }, Modifier.weight(1f), icon = Icons.Rounded.ContentCopy)
                    NexVoraButton("Share", { NxShare.shareText(context, s.text) }, Modifier.weight(1f), icon = Icons.Rounded.Share)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexVoraButton("Export TXT", { export.launch("ocr_text.txt") }, Modifier.weight(1f), icon = Icons.Rounded.Save)
                    NexVoraButton("Save to Files", {
                        runCatching { vault.importStream("OCR ${s.sourceName ?: "text"}.txt", "text/plain", s.text.byteInputStream(), "notes") }
                            .onSuccess { android.widget.Toast.makeText(context, "Saved to Files › Notes.", android.widget.Toast.LENGTH_SHORT).show() }
                    }, Modifier.weight(1f))
                }
            }
            NxStatusBanner("Printed text only. Handwriting recognition is not supported. Accuracy depends on image quality and the chosen language.")
        }
    }
}
