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
package com.nexvora.docai.feature.settings

import com.nexvora.docai.feature.study.StudyRepository
import com.nexvora.docai.feature.vault.VaultIndex
import com.nexvora.docai.feature.vault.VaultRepository
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** User-controlled backup: a plain (NOT encrypted) ZIP with the vault and study data. */
class BackupManager(private val vault: VaultRepository, private val study: StudyRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Writes a backup. With a [password] the whole ZIP is encrypted (see [BackupCrypto]); otherwise it is a plain ZIP. */
    fun create(out: OutputStream, appVersion: String, password: CharArray? = null): Int {
        var count = 0
        val target: OutputStream = if (password != null) BackupCrypto.EncryptingOutputStream(out, password) else out
        ZipOutputStream(target).use { zip ->
            fun text(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            text("manifest.txt", "NexVora DocAI backup\nversion=$appVersion\ncreated=${System.currentTimeMillis()}\nencrypted=${password != null}\n")
            text("vault/index.json", vault.indexJson())
            text("study.json", study.exportJson())
            for (doc in vault.state.value.docs) {
                val file = vault.file(doc)
                if (!file.exists()) continue
                zip.putNextEntry(ZipEntry("vault/files/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        return count
    }

    /**
     * Restores from [file] (plain ZIP or encrypted NXBK). Additive: existing documents are never deleted or overwritten.
     * Throws IllegalArgumentException with a user-facing message for wrong passwords or invalid/corrupt files.
     */
    fun restoreAny(file: File, password: CharArray?, tempDir: File): Int {
        if (!BackupCrypto.isEncrypted(file)) return restore(file)
        if (password == null) throw IllegalArgumentException("This backup is encrypted. Enter its password.")
        tempDir.mkdirs()
        val plain = File.createTempFile("restore", ".zip", tempDir)
        try {
            try {
                file.inputStream().use { i -> plain.outputStream().use { o -> BackupCrypto.decrypt(i, o, password) } }
            } catch (e: BackupCryptoException) {
                throw IllegalArgumentException(e.message ?: "The backup could not be decrypted.")
            }
            return restore(plain)
        } finally { plain.delete() }
    }

    /** Returns the number of restored documents, or throws IllegalArgumentException for invalid backups. */
    fun restore(zipFile: File): Int {
        val zip = try { ZipFile(zipFile) } catch (e: Exception) { throw IllegalArgumentException("This is not a valid backup file.") }
        zip.use {
            val indexEntry = zip.getEntry("vault/index.json") ?: throw IllegalArgumentException("This is not a NexVora DocAI backup.")
            val index = try {
                json.decodeFromString(VaultIndex.serializer(), zip.getInputStream(indexEntry).bufferedReader().readText())
            } catch (e: Exception) { throw IllegalArgumentException("The backup index is damaged.") }
            val restored = vault.mergeRestored(index) { doc ->
                val name = "vault/files/${doc.id}.${doc.ext.ifEmpty { "bin" }}"
                zip.getEntry(name)?.let { e -> zip.getInputStream(e) as InputStream }
            }
            zip.getEntry("study.json")?.let { e ->
                study.parse(zip.getInputStream(e).bufferedReader().readText())?.let { study.merge(it) }
            }
            return restored
        }
    }
}
