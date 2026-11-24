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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nexvora.docai.feature.ai.GgufException
import com.nexvora.docai.feature.ai.GgufInfo
import com.nexvora.docai.feature.ai.GgufValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

enum class ModelKind(val label: String) {
    LLM("Language model (GGUF)"), TRANSLATION("Translation model"), EMBEDDING("Embedding model")
}

@Serializable
data class ModelRecord(
    val id: String,
    val name: String,
    val kind: ModelKind,
    val fileName: String,
    val sizeBytes: Long,
    /** License text exactly as entered by the user from the publisher's page; "Unknown" if not provided. */
    val license: String,
    val source: String?,
    val installedAt: Long,
    /** From the GGUF header (LLM kind only). The architecture is NOT guaranteed to be supported by the engine. */
    val architecture: String? = null,
    val contextLength: Long? = null,
    val checksumVerified: Boolean = false,
)

sealed interface DownloadState {
    data class Downloading(val downloaded: Long, val total: Long?) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Stores user-provided model files in app-private storage. No model is bundled and no catalog URL is
 * hard-coded: files come from a local import or from a URL the user types (HTTPS only, user-initiated).
 * Downloads, integrity checks and inference have NOT been verified on a device.
 */
class ModelManager(
    private val root: File,
    private val scope: CoroutineScope,
    private val freeSpace: () -> Long = { root.apply { mkdirs() }.usableSpace },
) {
    companion object {
        const val SAFETY_MARGIN = 64L * 1024 * 1024
        fun validateUrl(url: String): String? {
            val u = url.trim()
            if (!u.startsWith("https://")) return "Only HTTPS links are accepted."
            return try { URL(u); null } catch (_: Exception) { "This is not a valid URL." }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val index = File(root, "models.json")
    private val _installed = MutableStateFlow(load())
    val installed: StateFlow<List<ModelRecord>> = _installed
    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads
    private val jobs = HashMap<String, Job>()

    private fun load(): List<ModelRecord> = try {
        if (index.exists()) json.decodeFromString(ListSerializer(ModelRecord.serializer()), index.readText())
            .filter { File(root, it.fileName).exists() } else emptyList()
    } catch (_: Exception) { emptyList() }

    private fun persist() {
        root.mkdirs()
        index.writeText(json.encodeToString(ListSerializer(ModelRecord.serializer()), _installed.value))
    }

    fun freeBytes(): Long = freeSpace()
    fun usedBytes(): Long = _installed.value.sumOf { it.sizeBytes }
    fun file(record: ModelRecord) = File(root, record.fileName)

    private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "model" }

    /** Copies a local file into model storage. Returns an error message or null on success. */
    fun importStream(name: String, kind: ModelKind, license: String, input: InputStream, originalName: String): String? {
        root.mkdirs()
        val id = UUID.randomUUID().toString()
        val fileName = "$id-${safeName(originalName)}"
        val target = File(root, fileName)
        return try {
            input.use { src -> target.outputStream().use { out -> src.copyTo(out) } }
            if (target.length() == 0L) { target.delete(); return "The file is empty." }
            val info = validate(target, kind) ?: return validationError(target)
            register(ModelRecord(id, name, kind, fileName, target.length(), license.ifBlank { "Unknown" }, null, System.currentTimeMillis(),
                info.architecture, info.contextLength))
            null
        } catch (e: IOException) {
            target.delete()
            "Import failed (is the storage full?): ${e.message}"
        }
    }

    private var lastValidationError: String? = null

    /** LLM models must be valid GGUF. Returns the header info (or a placeholder for other kinds); null = invalid. */
    private fun validate(file: File, kind: ModelKind): GgufInfo? {
        lastValidationError = null
        if (kind != ModelKind.LLM) return GgufInfo(0, 0, null, null, null, null)
        return try { GgufValidator.read(file) } catch (e: GgufException) { lastValidationError = e.message; null } catch (e: IOException) { lastValidationError = e.message; null }
    }

    private fun validationError(file: File): String { file.delete(); return "Not a usable GGUF model: ${lastValidationError ?: "unreadable file"}" }

    private fun register(r: ModelRecord) {
        _installed.update { it + r }
        persist()
    }

    fun download(name: String, kind: ModelKind, license: String, url: String, sha256: String?) {
        validateUrl(url)?.let { err -> _downloads.update { it + (name to DownloadState.Failed(err)) }; return }
        val id = UUID.randomUUID().toString()
        _downloads.update { it + (id to DownloadState.Downloading(0, null)) }
        jobs[id] = scope.launch(Dispatchers.IO) {
            val fileName = "$id-${safeName(url.substringAfterLast('/').substringBefore('?'))}"
            val part = File(root.apply { mkdirs() }, "$fileName.part")
            try {
                val conn = URL(url.trim()).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                try {
                    if (conn.responseCode != 200) throw IOException("Server answered HTTP ${conn.responseCode}.")
                    val total = conn.contentLengthLong.takeIf { it > 0 }
                    if (total != null && total + SAFETY_MARGIN > freeSpace()) throw IOException("Not enough free storage for this model.")
                    val digest = sha256?.takeIf { it.isNotBlank() }?.let { MessageDigest.getInstance("SHA-256") }
                    var done = 0L
                    var lastReport = 0L
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                digest?.update(buf, 0, n)
                                done += n
                                if (done - lastReport > 512 * 1024) {
                                    lastReport = done
                                    _downloads.update { it + (id to DownloadState.Downloading(done, total)) }
                                }
                            }
                        }
                    }
                    if (digest != null) {
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        if (!actual.equals(sha256!!.trim(), ignoreCase = true)) throw IOException("Checksum mismatch: the file is not the expected one.")
                    }
                } finally { conn.disconnect() }
                val target = File(root, fileName)
                if (!part.renameTo(target)) throw IOException("Could not finalise the downloaded file.")
                val info = validate(target, kind)
                if (info == null) { target.delete(); throw IOException("Downloaded file is not a usable GGUF model: ${lastValidationError ?: "unreadable"}") }
                register(ModelRecord(id, name, kind, fileName, target.length(), license.ifBlank { "Unknown" }, url.trim(), System.currentTimeMillis(),
                    info.architecture, info.contextLength, checksumVerified = !sha256.isNullOrBlank()))
                _downloads.update { it - id }
            } catch (e: CancellationException) {
                part.delete()
                _downloads.update { it - id }
                throw e
            } catch (e: Exception) {
                part.delete()
                _downloads.update { it + (id to DownloadState.Failed(e.message ?: "Download failed.")) }
            } finally {
                jobs.remove(id)
            }
        }
    }

    fun cancel(id: String) { jobs[id]?.cancel() }
    fun dismissFailure(id: String) = _downloads.update { it - id }

    fun delete(id: String): Boolean {
        val r = _installed.value.firstOrNull { it.id == id } ?: return false
        file(r).delete()
        _installed.update { list -> list.filter { it.id != id } }
        persist()
        return true
    }
}
