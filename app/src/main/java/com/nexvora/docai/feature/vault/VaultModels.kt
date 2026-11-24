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

import kotlinx.serialization.Serializable

@Serializable
data class VaultFolder(val id: String, val name: String, val builtIn: Boolean = false)

@Serializable
data class VaultDoc(
    val id: String,
    val name: String,
    val ext: String,
    val mime: String,
    val sizeBytes: Long,
    val folderId: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val favorite: Boolean = false,
    /** SHA-256 of the file content (used for duplicate detection); null for entries created by older builds. */
    val sha256: String? = null,
) {
    val fileName: String get() = if (ext.isEmpty()) name else "$name.$ext"
    val isPdf: Boolean get() = mime == "application/pdf" || ext == "pdf"
    val isImage: Boolean get() = mime.startsWith("image/")
    val isText: Boolean get() = mime.startsWith("text/")
}

/** Result of an import: [isNew] is false when an identical file was already in the vault (nothing was copied). */
data class ImportOutcome(val doc: VaultDoc, val isNew: Boolean)

@Serializable
data class VaultIndex(val folders: List<VaultFolder>, val docs: List<VaultDoc>) {
    fun docsIn(folderId: String): List<VaultDoc> = docs.filter { it.folderId == folderId }
}

enum class VaultFilter(val label: String) { ALL("All"), PDF("PDF"), IMAGES("Images"), OTHER("Other"), FAVORITES("Favorites") }

enum class VaultSort(val label: String) { DATE_DESC("Newest"), NAME_ASC("Name"), SIZE_DESC("Size") }

fun List<VaultDoc>.query(
    text: String = "",
    filter: VaultFilter = VaultFilter.ALL,
    sort: VaultSort = VaultSort.DATE_DESC,
    folderId: String? = null,
): List<VaultDoc> {
    val q = text.trim().lowercase()
    return asSequence()
        .filter { folderId == null || it.folderId == folderId }
        .filter { q.isEmpty() || it.fileName.lowercase().contains(q) }
        .filter {
            when (filter) {
                VaultFilter.ALL -> true
                VaultFilter.PDF -> it.isPdf
                VaultFilter.IMAGES -> it.isImage
                VaultFilter.OTHER -> !it.isPdf && !it.isImage
                VaultFilter.FAVORITES -> it.favorite
            }
        }
        .toList()
        .let { list ->
            when (sort) {
                VaultSort.DATE_DESC -> list.sortedByDescending { it.modifiedAt }
                VaultSort.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                VaultSort.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            }
        }
}
