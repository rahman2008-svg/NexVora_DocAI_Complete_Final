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
package com.nexvora.docai.feature.ai

import com.nexvora.docai.feature.models.ModelManager
import com.nexvora.docai.feature.models.ModelRecord
import com.nexvora.docai.feature.models.TextGenerationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface LlmStatus {
    data object NoEngine : LlmStatus
    data object NoModel : LlmStatus
    data class Loading(val name: String) : LlmStatus
    data class Ready(val name: String) : LlmStatus
    data class Failed(val message: String) : LlmStatus
}

/** Loads/unloads models into the engine, with a free-memory check and a configurable context length. */
class LlmRuntime(
    val engine: TextGenerationEngine,
    private val models: ModelManager,
    private val scope: CoroutineScope,
    private val availableMemory: () -> Long,
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
) {
    val status = MutableStateFlow<LlmStatus>(initial())
    var contextLength: Int = 2048
        set(value) { field = value.coerceIn(512, 8192) }

    private fun initial(): LlmStatus = when {
        !engine.engineInstalled -> LlmStatus.NoEngine
        engine.isAvailable -> LlmStatus.Ready(engine.loadedModelName ?: "model")
        else -> LlmStatus.NoModel
    }

    fun load(record: ModelRecord) {
        if (!engine.engineInstalled) { status.value = LlmStatus.NoEngine; return }
        val file = models.file(record)
        val needed = (record.sizeBytes * 1.2).toLong()
        if (availableMemory() < needed) {
            status.value = LlmStatus.Failed("Not enough free memory to load this model (needs about ${needed / (1024 * 1024)} MB). Close other apps or choose a smaller model.")
            return
        }
        status.value = LlmStatus.Loading(record.name)
        scope.launch {
            val ctx = record.contextLength?.let { minOf(it, contextLength.toLong()).toInt() } ?: contextLength
            val r = engine.load(file, record.name, ctx, threads)
            status.value = r.fold({ LlmStatus.Ready(record.name) }, { LlmStatus.Failed(it.message ?: "Loading failed.") })
        }
    }

    fun unload() { engine.unload(); status.value = initial() }
}
