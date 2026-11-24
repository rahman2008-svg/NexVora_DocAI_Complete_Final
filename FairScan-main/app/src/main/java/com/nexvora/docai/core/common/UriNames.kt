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

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

fun ContentResolver.displayNameOf(uri: Uri): String {
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

/** Streams [uri] into [target]; throws [IOException] when the source cannot be opened or is empty. */
fun ContentResolver.copyUriTo(uri: Uri, target: File) {
    val input = openInputStream(uri) ?: throw IOException("The file could not be opened.")
    try {
        input.use { src -> target.outputStream().use { out -> src.copyTo(out) } }
    } catch (e: IOException) {
        target.delete()
        throw e
    }
    if (target.length() == 0L) {
        target.delete()
        throw IOException("The selected file is empty.")
    }
}
