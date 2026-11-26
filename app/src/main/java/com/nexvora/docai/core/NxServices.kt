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
package com.nexvora.docai.core

import android.app.ActivityManager
import android.content.Context
import com.nexvora.docai.data.OcrLanguageRepository
import com.nexvora.docai.domain.OcrService
import com.nexvora.docai.feature.assistant.AssistantController
import com.nexvora.docai.feature.assistant.DocumentTextExtractor
import com.nexvora.docai.feature.models.ModelManager
import com.nexvora.docai.feature.ai.LlmRuntime
import com.nexvora.docai.feature.models.LlamaCppEngine
import com.nexvora.docai.feature.models.LlmTranslationEngine
import com.nexvora.docai.feature.models.TextGenerationEngine
import com.nexvora.docai.feature.models.TranslationEngine
import com.nexvora.docai.feature.ocr.OcrController
import com.nexvora.docai.feature.pdf.PdfController
import com.nexvora.docai.feature.pdf.PdfEngine
import com.nexvora.docai.feature.settings.BackupManager
import com.nexvora.docai.feature.settings.NxPrefs
import com.nexvora.docai.feature.study.StudyRepository
import com.nexvora.docai.feature.vault.VaultController
import com.nexvora.docai.feature.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Application-scoped holder of all NexVora feature services (created once by AppContainer). */
class NxServices(
    context: Context,
    val scope: CoroutineScope,
    ocrService: OcrService,
    ocrLanguages: OcrLanguageRepository,
    val scannerReady: () -> Boolean,
) {
    private val files = context.filesDir
    private val cache = File(context.cacheDir, "nexvora")

    val prefs = NxPrefs(context)
    val vault = VaultRepository(File(files, "vault"))
    val vaultController = VaultController(vault, context.contentResolver, scope)
    val study = StudyRepository(File(files, "study/study.json"))
    val extractor = DocumentTextExtractor(ocrService)
    val pdf = PdfController(
        engine = PdfEngine(File(cache, "pdf-work")),
        vault = vault,
        resolver = context.contentResolver,
        inputDir = File(cache, "pdf-in"),
        workDir = File(cache, "pdf-work"),
        scope = scope,
    )
    val ocr = OcrController(ocrService, ocrLanguages, context.contentResolver, File(cache, "ocr"), scope)
    val models = ModelManager(File(files, "models"), scope)
    /** Native llama.cpp bridge: reports "engine not installed" unless libnexvora_llama.so is packaged (see docs). */
    val llm: TextGenerationEngine = LlamaCppEngine()
    val llmRuntime = LlmRuntime(
        engine = llm,
        models = models,
        scope = scope,
        availableMemory = {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        }
    )
    val assistant = AssistantController(vault, extractor, File(files, "assistant"), scope, llmRuntime)
    val backup = BackupManager(vault, study)
    val translationEngine: TranslationEngine = LlmTranslationEngine(llm)
}
