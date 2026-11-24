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
package com.nexvora.docai.core.common

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private val ILLEGAL_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

/** Makes a user-provided name safe to use as a file name (keeps Unicode letters, e.g. Bangla). */
fun sanitizeFileName(raw: String, fallback: String = "document"): String {
    val cleaned = raw.replace(ILLEGAL_FILENAME_CHARS, "_").trim().trim('.').take(120).trim()
    return cleaned.ifBlank { fallback }
}

/** Splits "report.final.pdf" into ("report.final", "pdf"). Extension is empty when there is none. */
fun splitExtension(fileName: String): Pair<String, String> {
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0 || fileName.length - dot - 1 > 8 || dot == fileName.length - 1) return fileName to ""
    return fileName.substring(0, dot) to fileName.substring(dot + 1).lowercase(Locale.ROOT)
}

fun mimeFromExtension(ext: String): String = when (ext.lowercase(Locale.ROOT)) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "txt" -> "text/plain"
    "md" -> "text/markdown"
    "json" -> "application/json"
    else -> "application/octet-stream"
}
