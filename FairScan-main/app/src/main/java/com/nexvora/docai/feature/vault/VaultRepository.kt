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
package com.nexvora.docai.feature.vault

import com.nexvora.docai.core.common.mimeFromExtension
import com.nexvora.docai.core.common.sanitizeFileName
import com.nexvora.docai.core.common.splitExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Local document store. Files live in `<root>/files/<id>.<ext>` (app-private storage, not encrypted)
 * and metadata in `<root>/index.json`. Nothing here touches the network.
 */
class VaultRepository(
    private val root: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val filesDir = File(root, "files")
    private val indexFile = File(root, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private val _state = MutableStateFlow(VaultIndex(defaultFolders(), emptyList()))
    val state: StateFlow<VaultIndex> = _state

    init { load() }

    companion object {
        const val OTHER = "other"
        const val PDF_STUDIO_FOLDER = "PDF Studio"

        fun defaultFolders() = listOf(
            VaultFolder("personal", "Personal", true),
            VaultFolder("education", "Education", true),
            VaultFolder("certificates", "Certificates", true),
            VaultFolder("business", "Business", true),
            VaultFolder("notes", "Notes", true),
            VaultFolder(OTHER, "Other", true),
        )
    }

    fun load() = synchronized(lock) {
        filesDir.mkdirs()
        var index = try {
            if (indexFile.exists()) json.decodeFromString(VaultIndex.serializer(), indexFile.readText()) else null
        } catch (e: Exception) {
            // Corrupt index: keep a copy for diagnosis and start clean (files stay on disk).
            runCatching { indexFile.copyTo(File(root, "index.json.corrupt"), overwrite = true) }
            null
        } ?: VaultIndex(defaultFolders(), emptyList())
        val missingBuiltIns = defaultFolders().filter { b -> index.folders.none { it.id == b.id } }
        // Drop entries whose file disappeared (e.g. storage cleared by the user).
        index = index.copy(
            folders = missingBuiltIns + index.folders,
            docs = index.docs.filter { File(filesDir, "${it.id}.${it.ext.ifEmpty { "bin" }}").exists() },
        )
        _state.value = index
        persist()
    }

    private fun persist() {
        root.mkdirs()
        val tmp = File(root, "index.json.tmp")
        tmp.writeText(json.encodeToString(VaultIndex.serializer(), _state.value))
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun update(block: (VaultIndex) -> VaultIndex) = synchronized(lock) {
        _state.value = block(_state.value)
        persist()
    }

    fun file(doc: VaultDoc): File = File(filesDir, "${doc.id}.${doc.ext.ifEmpty { "bin" }}")

    /** Copies [input] into the vault. Throws [IOException] (e.g. no space left) after cleaning up. */
    fun importStream(displayName: String, mime: String?, input: InputStream, folderId: String = OTHER): ImportOutcome {
        val (rawBase, ext) = splitExtension(sanitizeFileName(displayName, "document"))
        val base = rawBase.ifBlank { "document" }
        val id = UUID.randomUUID().toString()
        filesDir.mkdirs()
        val target = File(filesDir, "$id.${ext.ifEmpty { "bin" }}")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            input.use { src ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                    }
                }
            }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        if (target.length() == 0L) {
            target.delete()
            throw IOException("The selected file is empty.")
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        _state.value.docs.firstOrNull { it.sha256 == hash }?.let { existing ->
            target.delete()
            return ImportOutcome(existing, false)
        }
        val now = clock()
        val folder = if (_state.value.folders.any { it.id == folderId }) folderId else OTHER
        val doc = VaultDoc(
            id = id, name = base, ext = ext,
            mime = mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: mimeFromExtension(ext),
            sizeBytes = target.length(), folderId = folder, createdAt = now, modifiedAt = now, sha256 = hash,
        )
        update { it.copy(docs = it.docs + doc) }
        return ImportOutcome(doc, true)
    }

    fun importFile(source: File, displayName: String = source.name, folderId: String = OTHER): ImportOutcome =
        importStream(displayName, null, source.inputStream(), folderId)

    fun rename(id: String, newName: String): Boolean {
        val clean = sanitizeFileName(splitExtension(newName).first, "")
        if (clean.isEmpty()) return false
        var found = false
        update { idx ->
            idx.copy(docs = idx.docs.map { if (it.id == id) { found = true; it.copy(name = clean, modifiedAt = clock()) } else it })
        }
        return found
    }

    fun move(id: String, folderId: String): Boolean {
        if (_state.value.folders.none { it.id == folderId }) return false
        var found = false
        update { idx -> idx.copy(docs = idx.docs.map { if (it.id == id) { found = true; it.copy(folderId = folderId) } else it }) }
        return found
    }

    fun setFavorite(id: String, favorite: Boolean) =
        update { idx -> idx.copy(docs = idx.docs.map { if (it.id == id) it.copy(favorite = favorite) else it }) }

    fun delete(id: String): Boolean {
        val doc = _state.value.docs.firstOrNull { it.id == id } ?: return false
        file(doc).delete()
        update { idx -> idx.copy(docs = idx.docs.filter { it.id != id }) }
        return true
    }

    fun createFolder(name: String): VaultFolder? {
        val clean = name.trim().take(60)
        if (clean.isEmpty() || _state.value.folders.any { it.name.equals(clean, ignoreCase = true) }) return null
        val folder = VaultFolder(UUID.randomUUID().toString(), clean)
        update { it.copy(folders = it.folders + folder) }
        return folder
    }

    fun ensureFolder(name: String): VaultFolder =
        _state.value.folders.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: createFolder(name)!!

    fun renameFolder(id: String, name: String): Boolean {
        val clean = name.trim().take(60)
        val folder = _state.value.folders.firstOrNull { it.id == id } ?: return false
        if (folder.builtIn || clean.isEmpty() ||
            _state.value.folders.any { it.id != id && it.name.equals(clean, ignoreCase = true) }
        ) return false
        update { idx -> idx.copy(folders = idx.folders.map { if (it.id == id) it.copy(name = clean) else it }) }
        return true
    }

    /** Only empty, user-created folders can be deleted (documents are never deleted implicitly). */
    fun deleteFolder(id: String): Boolean {
        val folder = _state.value.folders.firstOrNull { it.id == id } ?: return false
        if (folder.builtIn || _state.value.docsIn(id).isNotEmpty()) return false
        update { idx -> idx.copy(folders = idx.folders.filter { it.id != id }) }
        return true
    }

    fun exportTo(doc: VaultDoc, out: OutputStream) {
        file(doc).inputStream().use { it.copyTo(out) }
    }

    fun totalBytes(): Long = _state.value.docs.sumOf { it.sizeBytes }

    fun indexJson(): String = json.encodeToString(VaultIndex.serializer(), _state.value)

    /**
     * Additive restore: documents whose id already exists are skipped; folders are merged by id.
     * Ids and extensions coming from an untrusted backup are validated before touching the file system.
     * Returns the number of restored documents.
     */
    fun mergeRestored(backup: VaultIndex, openFile: (VaultDoc) -> InputStream?): Int {
        val idOk = Regex("[A-Za-z0-9-]{1,64}")
        val extOk = Regex("[a-z0-9]{0,8}")
        var restored = 0
        filesDir.mkdirs()
        val existing = _state.value
        val newFolders = backup.folders.filter { f -> existing.folders.none { it.id == f.id } && idOk.matches(f.id) }
            .map { it.copy(builtIn = false) }
        val newDocs = mutableListOf<VaultDoc>()
        for (doc in backup.docs) {
            if (existing.docs.any { it.id == doc.id } || !idOk.matches(doc.id) || !extOk.matches(doc.ext)) continue
            val input = openFile(doc) ?: continue
            val target = File(filesDir, "${doc.id}.${doc.ext.ifEmpty { "bin" }}")
            try {
                input.use { src -> target.outputStream().use { src.copyTo(it) } }
            } catch (e: IOException) { target.delete(); continue }
            val folderOk = (existing.folders + newFolders).any { it.id == doc.folderId }
            newDocs.add(doc.copy(sizeBytes = target.length(), folderId = if (folderOk) doc.folderId else OTHER))
            restored++
        }
        update { it.copy(folders = it.folders + newFolders, docs = it.docs + newDocs) }
        return restored
    }
}
