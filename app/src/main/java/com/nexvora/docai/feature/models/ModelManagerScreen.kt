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
package com.nexvora.docai.feature.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.common.displayNameOf
import com.nexvora.docai.core.common.formatBytes
import com.nexvora.docai.core.design.NexVoraButton
import com.nexvora.docai.core.design.NexVoraCard
import com.nexvora.docai.core.design.NexVoraDialog
import com.nexvora.docai.core.design.NexVoraLoadingIndicator
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxBannerKind
import com.nexvora.docai.core.design.NxChip
import com.nexvora.docai.core.design.NxSectionTitle
import com.nexvora.docai.core.design.NxStatusBanner
import com.nexvora.docai.core.design.NxTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import android.net.Uri

private sealed interface AddMode {
    data object Download : AddMode
    data class Import(val uri: Uri, val fileName: String) : AddMode
}

@Composable
fun ModelManagerScreen(manager: ModelManager, runtime: com.nexvora.docai.feature.ai.LlmRuntime, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed by manager.installed.collectAsState()
    val downloads by manager.downloads.collectAsState()
    var add by remember { mutableStateOf<AddMode?>(null) }
    var toDelete by remember { mutableStateOf<ModelRecord?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val llmStatus by runtime.status.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) add = AddMode.Import(uri, context.contentResolver.displayNameOf(uri))
    }

    Column(Modifier.fillMaxSize()) {
        NexVoraTopBar("Model Manager", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val st = llmStatus) {
                com.nexvora.docai.feature.ai.LlmStatus.NoEngine -> NxStatusBanner("Engine: not installed. " + runtime.engine.unavailableReason + " See docs/LLAMA_CPP_INTEGRATION.md to add the native engine.", NxBannerKind.WARNING)
                com.nexvora.docai.feature.ai.LlmStatus.NoModel -> NxStatusBanner("Engine installed. Local AI model not loaded: import a GGUF model and tap Load.", NxBannerKind.WARNING)
                is com.nexvora.docai.feature.ai.LlmStatus.Loading -> NxStatusBanner("Loading ${st.name}…")
                is com.nexvora.docai.feature.ai.LlmStatus.Ready -> NxStatusBanner("Loaded: ${st.name}. Generation has not been verified by NexVora on your device; test with a short question first.", NxBannerKind.SUCCESS)
                is com.nexvora.docai.feature.ai.LlmStatus.Failed -> NxStatusBanner(st.message, NxBannerKind.ERROR)
            }
            NxStatusBanner("Translation uses the loaded language model. There is no separate translation engine and no online fallback.")
            NexVoraCard {
                Text("Storage", color = Nx.TextMain, fontSize = 15.sp)
                Text("Models use ${formatBytes(manager.usedBytes())} · ${formatBytes(manager.freeBytes())} free on this device", color = Nx.TextSecondary, fontSize = 13.sp)
                Text("Models are stored privately in the app's files folder. Large models may not run on every phone.", color = Nx.TextSecondary, fontSize = 12.sp)
            }
            NxSectionTitle("Installed models")
            if (installed.isEmpty()) Text("No models installed. No model is bundled with this app.", color = Nx.TextSecondary, fontSize = 13.sp)
            installed.forEach { m ->
                NexVoraCard(contentPadding = 12) {
                    Text(m.name, color = Nx.TextMain, fontSize = 15.sp)
                    Text("${m.kind.label} · ${formatBytes(m.sizeBytes)}", color = Nx.TextSecondary, fontSize = 12.sp)
                    Text("License: ${m.license}", color = Nx.TextSecondary, fontSize = 12.sp)
                    m.architecture?.let { Text("Architecture: $it" + (m.contextLength?.let { c -> " · trained context $c tokens" } ?: "") + " (support by the engine is not guaranteed)", color = Nx.TextSecondary, fontSize = 12.sp) }
                    if (m.source != null) Text("Source: ${m.source}" + if (m.checksumVerified) " · SHA-256 verified" else " · checksum not verified", color = Nx.TextSecondary, fontSize = 12.sp)
                    Text("Needs about ${formatBytes((m.sizeBytes * 1.2).toLong())} of free memory to load.", color = Nx.TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (m.kind == ModelKind.LLM) {
                            val loaded = (llmStatus as? com.nexvora.docai.feature.ai.LlmStatus.Ready)?.name == m.name
                            NexVoraButton(if (loaded) "Unload" else "Load", { if (loaded) runtime.unload() else runtime.load(m) }, enabled = runtime.engine.engineInstalled)
                        }
                        NexVoraButton("Delete", { toDelete = m }, destructive = true)
                    }
                }
            }
            downloads.forEach { (id, st) ->
                NexVoraCard(contentPadding = 12) {
                    when (st) {
                        is DownloadState.Downloading -> {
                            Text("Downloading… ${formatBytes(st.downloaded)}" + (st.total?.let { " of ${formatBytes(it)}" } ?: ""), color = Nx.TextMain, fontSize = 14.sp)
                            NexVoraLoadingIndicator(progress = st.total?.let { st.downloaded.toFloat() / it }, linear = true)
                            Spacer(Modifier.height(8.dp))
                            NexVoraButton("Cancel", { manager.cancel(id) }, destructive = true)
                        }
                        is DownloadState.Failed -> {
                            Text("Download failed: ${st.message}", color = Nx.Danger, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            NexVoraButton("Dismiss", { manager.dismissFailure(id) })
                        }
                    }
                }
            }
            NxSectionTitle("Add a model")
            NxStatusBanner("No verified model catalog is bundled. Import a file from your device, or download from an HTTPS link you trust. Enter the license shown on the publisher's page. Downloads start only when you confirm.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexVoraButton("Import file", { picker.launch(arrayOf("*/*")) }, Modifier.weight(1f))
                NexVoraButton("Download link", { add = AddMode.Download }, Modifier.weight(1f))
            }
            message?.let { NxStatusBanner(it) }
        }
    }

    add?.let { mode ->
        var name by remember { mutableStateOf((mode as? AddMode.Import)?.fileName ?: "") }
        var url by remember { mutableStateOf("https://") }
        var license by remember { mutableStateOf("") }
        var sha by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf(ModelKind.LLM) }
        val urlError = if (mode is AddMode.Download) ModelManager.validateUrl(url) else null
        AlertDialog(
            onDismissRequest = { add = null },
            containerColor = Nx.Card,
            titleContentColor = Nx.TextMain,
            title = { Text(if (mode is AddMode.Download) "Download model" else "Import model") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode is AddMode.Download) Text("The file is downloaded directly from this address to your device. Nothing else is sent.", color = Nx.TextSecondary, fontSize = 12.sp)
                    NxTextField(name, { name = it }, "Display name", Modifier.fillMaxWidth())
                    if (mode is AddMode.Download) {
                        NxTextField(url, { url = it }, "HTTPS link", Modifier.fillMaxWidth())
                        urlError?.let { Text(it, color = Nx.Danger, fontSize = 12.sp) }
                        NxTextField(sha, { sha = it }, "SHA-256 (optional, recommended)", Modifier.fillMaxWidth())
                    }
                    NxTextField(license, { license = it }, "License (e.g. Apache-2.0)", Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModelKind.entries.forEach { k -> NxChip(k.label.substringBefore(" ("), kind == k, { kind = k }) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && (mode !is AddMode.Download || urlError == null),
                    onClick = {
                        if (mode is AddMode.Download) {
                            manager.download(name.trim(), kind, license.trim(), url.trim(), sha.trim().ifEmpty { null })
                        } else if (mode is AddMode.Import) {
                            scope.launch {
                                val err = withContext(Dispatchers.IO) {
                                    val input = context.contentResolver.openInputStream(mode.uri)
                                    if (input == null) "The file could not be opened."
                                    else manager.importStream(name.trim(), kind, license.trim(), input, mode.fileName)
                                }
                                message = err ?: "Model imported."
                            }
                        }
                        add = null
                    },
                ) { Text(if (mode is AddMode.Download) "Download" else "Import", color = Nx.Cyan) }
            },
            dismissButton = { TextButton(onClick = { add = null }) { Text("Cancel", color = Nx.TextSecondary) } },
        )
    }
    toDelete?.let { m ->
        NexVoraDialog("Delete model?", "\"${m.name}\" (${formatBytes(m.sizeBytes)}) will be removed from this device.", "Delete",
            onConfirm = { if ((llmStatus as? com.nexvora.docai.feature.ai.LlmStatus.Ready)?.name == m.name) runtime.unload(); manager.delete(m.id); toDelete = null }, onDismiss = { toDelete = null }, destructive = true)
    }
}
