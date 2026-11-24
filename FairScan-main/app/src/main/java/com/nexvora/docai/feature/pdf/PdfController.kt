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

import android.content.ContentResolver
import android.net.Uri
import com.nexvora.docai.core.common.copyUriTo
import com.nexvora.docai.core.common.displayNameOf
import com.nexvora.docai.core.common.formatBytes
import com.nexvora.docai.core.common.sanitizeFileName
import com.nexvora.docai.feature.vault.VaultDoc
import com.nexvora.docai.feature.vault.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PdfJobState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val error: String? = null,
    val results: List<VaultDoc> = emptyList(),
) {
    val progress: Float? get() = if (running && total > 0) done.toFloat() / total else null
}

/** Holds PDF Studio state on the application scope so jobs survive rotation. */
class PdfController(
    private val engine: PdfEngine,
    private val vault: VaultRepository,
    private val resolver: ContentResolver,
    private val inputDir: File,
    private val workDir: File,
    private val scope: CoroutineScope,
) {
    val inputs = MutableStateFlow<List<PdfInput>>(emptyList())
    val job = MutableStateFlow(PdfJobState())
    private var running: Job? = null

    /** Copies picked files into private cache and (for PDFs) validates them before accepting. */
    fun addInputs(uris: List<Uri>, images: Boolean, append: Boolean) { // images = accept without PDF inspection (images / locked PDFs)
        if (uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            if (!append) clearInputsInternal()
            job.update { it.copy(error = null, results = emptyList(), message = null) }
            inputDir.mkdirs()
            val accepted = mutableListOf<PdfInput>()
            val errors = mutableListOf<String>()
            for (uri in uris) {
                val name = resolver.displayNameOf(uri)
                val file = File(inputDir, "${UUID.randomUUID()}_${sanitizeFileName(name)}")
                try {
                    resolver.copyUriTo(uri, file)
                    accepted.add(
                        if (images) PdfInput(name, file, null, file.length()) else engine.inspect(file, name)
                    )
                } catch (e: PdfOpException) {
                    file.delete()
                    errors.add(e.message ?: "Cannot use $name")
                } catch (e: Exception) {
                    file.delete()
                    errors.add("$name: ${e.message ?: "cannot be read"}")
                }
            }
            inputs.update { it + accepted }
            if (errors.isNotEmpty()) job.update { it.copy(error = errors.joinToString("\n")) }
        }
    }

    fun move(index: Int, delta: Int) = inputs.update { list ->
        val to = index + delta
        if (index !in list.indices || to !in list.indices) list
        else list.toMutableList().apply { add(to, removeAt(index)) }
    }

    fun remove(index: Int) = inputs.update { list ->
        list.getOrNull(index)?.file?.delete()
        list.filterIndexed { i, _ -> i != index }
    }

    fun clear() = scope.launch(Dispatchers.IO) {
        clearInputsInternal()
        job.value = PdfJobState()
    }

    private fun clearInputsInternal() {
        inputs.value.forEach { it.file.delete() }
        inputs.value = emptyList()
    }

    fun cancel() { running?.cancel() }

    suspend fun info(): List<Pair<String, String>> = inputs.value.firstOrNull()?.let { engine.info(it) } ?: emptyList()

    fun clearResult() = job.update { PdfJobState() }

    fun run(request: PdfRequest) {
        if (job.value.running) return
        val snapshot = inputs.value
        running = scope.launch(Dispatchers.IO) {
            job.value = PdfJobState(running = true, message = "Working…")
            try {
                workDir.deleteRecursively()
                val outputs = engine.execute(request, snapshot) { d, t -> job.update { it.copy(done = d, total = t) } }
                val folder = vault.ensureFolder(VaultRepository.PDF_STUDIO_FOLDER)
                val docs = outputs.map { vault.importFile(it.file, it.displayName, folder.id).doc }
                outputs.forEach { it.file.parentFile?.deleteRecursively() }
                job.value = PdfJobState(results = docs, message = summary(request, snapshot, docs))
            } catch (e: CancellationException) {
                job.value = PdfJobState(message = "Cancelled. No output was saved.")
                throw e
            } catch (e: PdfOpException) {
                job.value = PdfJobState(error = e.message)
            } catch (e: OutOfMemoryError) {
                job.value = PdfJobState(error = "Not enough memory to process this file. Try a smaller file or a lower resolution.")
            } catch (e: Exception) {
                job.value = PdfJobState(error = "The operation failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    private fun summary(request: PdfRequest, inputs: List<PdfInput>, docs: List<VaultDoc>): String {
        val saved = "Saved to Files › ${VaultRepository.PDF_STUDIO_FOLDER}."
        if (request is PdfRequest.Compress && docs.size == 1 && inputs.isNotEmpty()) {
            val before = inputs.first().sizeBytes
            val after = docs.first().sizeBytes
            return if (after < before) {
                "Size reduced from ${formatBytes(before)} to ${formatBytes(after)} (${100 - (after * 100 / before)}% smaller). $saved"
            } else {
                "The result (${formatBytes(after)}) is not smaller than the original (${formatBytes(before)}). Try a lower quality preset. $saved"
            }
        }
        return "Done: ${docs.size} file(s) created. $saved"
    }
}
