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

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Runs vault import/export on the application scope so it survives configuration changes. */
class VaultController(
    val vault: VaultRepository,
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
) {
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun displayName(uri: Uri): String {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    fun importUris(uris: List<Uri>, folderId: String) {
        if (uris.isEmpty()) {
            message.value = "No file was selected."
            return
        }
        scope.launch(Dispatchers.IO) {
            busy.value = true
            var ok = 0
            var duplicates = 0
            val errors = mutableListOf<String>()
            for (uri in uris) {
                val name = displayName(uri)
                try {
                    val input = resolver.openInputStream(uri)
                    if (input == null) errors.add("$name: cannot be opened")
                    else {
                        if (vault.importStream(name, resolver.getType(uri), input, folderId).isNew) ok++ else duplicates++
                    }
                } catch (e: Exception) {
                    errors.add("$name: ${e.message ?: "import failed"}")
                }
            }
            busy.value = false
            message.value = when {
                errors.isEmpty() -> "Imported $ok file(s)." + if (duplicates > 0) " $duplicates identical file(s) were already in the vault." else ""
                ok == 0 -> "Import failed. ${errors.first()}"
                else -> "Imported $ok file(s). Failed: ${errors.joinToString("; ")}"
            }
        }
    }

    fun exportTo(doc: VaultDoc, target: Uri?) {
        if (target == null) return // user cancelled the picker
        scope.launch(Dispatchers.IO) {
            busy.value = true
            message.value = try {
                val out = resolver.openOutputStream(target) ?: error("Cannot write to the chosen location.")
                out.use { vault.exportTo(doc, it) }
                "Exported ${doc.fileName}."
            } catch (e: Exception) {
                "Export failed: ${e.message ?: "unknown error"}"
            }
            busy.value = false
        }
    }
}
