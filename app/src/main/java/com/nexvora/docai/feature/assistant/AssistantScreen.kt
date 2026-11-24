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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.design.NexVoraButton
import com.nexvora.docai.core.design.NexVoraDialog
import com.nexvora.docai.core.design.NexVoraLoadingIndicator
import com.nexvora.docai.core.design.NexVoraSearchBar
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxBannerKind
import com.nexvora.docai.core.design.NxChip
import com.nexvora.docai.core.design.NxStatusBanner
import com.nexvora.docai.feature.vault.VaultDoc
import com.nexvora.docai.feature.vault.VaultRepository

@Composable
fun AssistantScreen(
    controller: AssistantController,
    vault: VaultRepository,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenPage: (VaultDoc, Int) -> Unit,
) {
    val s by controller.state.collectAsState()
    val vaultIndex by vault.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.size - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        NexVoraTopBar("NexVora AI Assistant", onBack = onBack, actions = {
            IconButton(onClick = { confirmClear = true }, enabled = s.messages.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteSweep, "Clear conversation", tint = Nx.TextMain)
            }
        })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NxStatusBanner(
                if (controller.engine.isAvailable) "Local AI: ready" else "Local AI: not available. ${controller.engine.unavailableReason}",
                if (controller.engine.isAvailable) NxBannerKind.SUCCESS else NxBannerKind.WARNING,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexVoraButton(s.doc?.fileName ?: "Attach a PDF", { picking = true }, Modifier.weight(1f), icon = Icons.Rounded.AttachFile)
                NexVoraButton("Models", onOpenModels)
            }
            if (s.extracting) {
                NexVoraLoadingIndicator(linear = true)
                Text(s.status ?: "Working…", color = Nx.TextSecondary, fontSize = 12.sp)
            }
            s.error?.let { NxStatusBanner(it, NxBannerKind.ERROR) }
            if (s.noTextFound && !s.extracting) {
                NxStatusBanner("No selectable text was found (probably a scanned PDF).", NxBannerKind.WARNING)
                NexVoraButton("Run OCR on this document", { controller.runOcr() })
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { NxChip(if (controller.engine.isAvailable) "Summarize" else "Summarize (extractive)", false, { controller.summarize() }) }
                listOf("Explain this document", "Extract important information", "Translate document text", "Create notes").forEach { label ->
                    item { NxChip(if (controller.engine.isAvailable) label else "$label · needs AI", false, { controller.aiTask(label) }) }
                }
                if (s.generating) item { NxChip("Stop", true, { controller.stopGeneration() }) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (s.messages.isEmpty()) {
                Text(
                    "Ask for a word or topic. Without an AI model, results are keyword matches with page numbers taken directly from your document.",
                    color = Nx.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.messages) { m ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.isUser) Alignment.End else Alignment.Start) {
                        Text(
                            m.text, color = Nx.TextMain, fontSize = 14.sp,
                            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (m.isUser) Nx.Purple else Nx.Card).padding(12.dp),
                        )
                        if (m.pages.isNotEmpty() && s.doc != null) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                items(m.pages.distinct()) { p -> NxChip("Page $p", false, { onOpenPage(s.doc!!, p - 1) }) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { NexVoraSearchBar(input, { input = it }, placeholder = if (controller.engine.isAvailable) "Ask about the document…" else "Search in the document…") }
            Spacer(Modifier.padding(4.dp))
            IconButton(onClick = { controller.ask(input); input = "" }, enabled = input.isNotBlank() && !s.extracting) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (input.isNotBlank()) Nx.Cyan else Nx.TextSecondary)
            }
        }
    }

    if (picking) {
        val pdfs = vaultIndex.docs.filter { it.isPdf || it.isText }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = Nx.Card,
            titleContentColor = Nx.TextMain,
            title = { Text("Choose a document") },
            text = {
                if (pdfs.isEmpty()) Text("No PDFs in Files yet. Import or scan one first.", color = Nx.TextSecondary)
                else LazyColumn { items(pdfs) { d ->
                    Text(d.fileName, color = Nx.TextMain, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).let { m -> m.then(Modifier.clickableNoRipple { controller.attach(d); picking = false }) })
                } }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text("Cancel", color = Nx.TextSecondary) } },
        )
    }
    if (confirmClear) NexVoraDialog("Clear conversation?", "All messages for this document will be deleted from this device.", "Clear",
        onConfirm = { controller.clear(); confirmClear = false }, onDismiss = { confirmClear = false }, destructive = true)
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(onClick = onClick)
)
