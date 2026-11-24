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
package com.nexvora.docai.feature.translation

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.common.NxShare
import com.nexvora.docai.core.design.NexVoraButton
import com.nexvora.docai.core.design.NexVoraCard
import com.nexvora.docai.core.design.NexVoraGradientButton
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxBannerKind
import com.nexvora.docai.core.design.NxChip
import com.nexvora.docai.core.design.NxStatusBanner
import com.nexvora.docai.core.design.NxTextField
import com.nexvora.docai.feature.models.TranslationEngine
import java.util.Locale
import kotlinx.coroutines.launch

private data class Lang(val code: String, val name: String)

private val LANGS = listOf(Lang("bn", "Bangla"), Lang("en", "English"))

@Composable
fun TranslationScreen(engine: TranslationEngine, onBack: () -> Unit, onOpenModels: () -> Unit) {
    val context = LocalContext.current
    var from by remember { mutableStateOf(LANGS[0]) }
    var to by remember { mutableStateOf(LANGS[1]) }
    var input by remember { mutableStateOf("") }
    // Only a real engine may fill this. With no engine the Translate button stays disabled.
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun translate() {
        output = ""; error = null; busy = true
        job = scope.launch {
            try {
                output = engine.translate(input, from.name, to.name) { piece -> output += piece }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { error = "Translation failed: ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
        }
    }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS } }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }

    fun listen() {
        val text = output.ifBlank { input }
        val lang = if (output.isBlank()) from else to
        if (text.isBlank()) return
        if (!ttsReady) { Toast.makeText(context, "Text-to-speech is not ready or not installed.", Toast.LENGTH_SHORT).show(); return }
        val r = tts.setLanguage(Locale.forLanguageTag(lang.code))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(context, "No ${lang.name} voice is installed on this device.", Toast.LENGTH_LONG).show(); return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexvora-tts")
    }

    Column(Modifier.fillMaxSize()) {
        NexVoraTopBar("Translation", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!engine.isAvailable) {
                NxStatusBanner(engine.unavailableReason, NxBannerKind.WARNING)
                NexVoraButton("Open Model Manager", onOpenModels, Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NxChip(from.name, true, { from = LANGS.first { it != from }.takeIf { it != to } ?: from })
                IconButton(onClick = { val f = from; from = to; to = f; }) { Icon(Icons.Rounded.SwapHoriz, "Swap languages", tint = Nx.Cyan) }
                NxChip(to.name, true, { to = LANGS.first { it != to }.takeIf { it != from } ?: to })
            }
            NxTextField(input, { input = it }, "Text in ${from.name}", Modifier.fillMaxWidth(), singleLine = false, minLines = 4)
            if (busy) NexVoraButton("Stop", { engine.let { job?.cancel() } }, Modifier.fillMaxWidth(), destructive = true)
            else NexVoraGradientButton("Translate", { translate() }, Modifier.fillMaxWidth(), enabled = engine.isAvailable && input.isNotBlank())
            error?.let { NxStatusBanner(it, NxBannerKind.ERROR) }
            if (engine.isAvailable) NxStatusBanner("Translated by the local language model on this device. Quality depends on the model and is not verified; check important text.")
            NexVoraCard {
                Text(output.ifBlank { "The ${to.name} translation will appear here once a translation model is installed." }, color = if (output.isBlank()) Nx.TextSecondary else Nx.TextMain, fontSize = 15.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexVoraButton("Copy", { NxShare.copyText(context, "Translation", output.ifBlank { input }) }, Modifier.weight(1f), enabled = input.isNotBlank() || output.isNotBlank(), icon = Icons.Rounded.ContentCopy)
                NexVoraButton("Share", { NxShare.shareText(context, output.ifBlank { input }) }, Modifier.weight(1f), enabled = input.isNotBlank() || output.isNotBlank(), icon = Icons.Rounded.Share)
                NexVoraButton("Listen", { listen() }, Modifier.weight(1f), enabled = input.isNotBlank() || output.isNotBlank(), icon = Icons.AutoMirrored.Rounded.VolumeUp)
            }
            if (output.isBlank()) NxStatusBanner("Until a translation exists, Copy, Share and Listen act on your input text (read aloud in ${from.name}).")
        }
    }
}
