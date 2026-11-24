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

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufInfo(
    val version: Int,
    val tensorCount: Long,
    val architecture: String?,
    val name: String?,
    val license: String?,
    val contextLength: Long?,
)

class GgufException(message: String) : IOException(message)

/**
 * Reads the GGUF header and key/value metadata (little-endian) WITHOUT loading tensors.
 * A valid header proves the file is GGUF; it does NOT prove that llama.cpp supports the architecture or
 * that the device has enough memory. Treat the result as metadata, not as a compatibility guarantee.
 */
object GgufValidator {
    private const val MAX_KV = 200_000L
    private const val MAX_TENSORS = 1_000_000L
    private const val MAX_STRING = 64L * 1024 * 1024

    fun read(file: File): GgufInfo {
        if (file.length() < 24) throw GgufException("The file is too small to be a GGUF model.")
        return file.inputStream().buffered(1 shl 16).use { read(it) }
    }

    fun read(input: InputStream): GgufInfo {
        val inp = Le(DataInputStream(if (input is BufferedInputStream) input else BufferedInputStream(input)))
        try {
            val magic = ByteArray(4).also { inp.readFully(it) }
            if (!magic.contentEquals("GGUF".toByteArray())) throw GgufException("Not a GGUF file (wrong magic bytes).")
            val version = inp.u32().toInt()
            if (version !in 2..3) throw GgufException("Unsupported GGUF version $version.")
            val tensors = inp.u64()
            val kvCount = inp.u64()
            if (tensors < 0 || tensors > MAX_TENSORS || kvCount < 0 || kvCount > MAX_KV) throw GgufException("The GGUF header is implausible (corrupt file?).")
            var arch: String? = null
            var name: String? = null
            var license: String? = null
            val ctx = HashMap<String, Long>()
            for (i in 0 until kvCount) {
                val key = inp.string()
                val type = inp.u32().toInt()
                if (type == 8) {
                    val v = inp.string()
                    when (key) { "general.architecture" -> arch = v; "general.name" -> name = v; "general.license" -> license = v }
                } else if (key.endsWith(".context_length") && (type == 4 || type == 5 || type == 10 || type == 11)) {
                    ctx[key] = if (type == 4 || type == 5) inp.u32().toLong() else inp.u64()
                } else {
                    inp.skipValue(type)
                }
            }
            val contextLength = arch?.let { ctx["$it.context_length"] } ?: ctx.values.firstOrNull()
            if (arch == null) throw GgufException("The GGUF file has no general.architecture entry.")
            return GgufInfo(version, tensors, arch, name, license, contextLength)
        } catch (e: EOFException) {
            throw GgufException("The GGUF file is truncated.")
        }
    }

    private class Le(private val d: DataInputStream) {
        fun readFully(b: ByteArray) = d.readFully(b)
        private fun buf(n: Int): ByteBuffer = ByteBuffer.wrap(ByteArray(n).also { d.readFully(it) }).order(ByteOrder.LITTLE_ENDIAN)
        fun u32(): Long = buf(4).int.toLong() and 0xFFFFFFFFL
        fun u64(): Long = buf(8).long
        fun string(): String {
            val len = u64()
            if (len < 0 || len > MAX_STRING) throw GgufException("The GGUF file is corrupt (bad string length).")
            return String(ByteArray(len.toInt()).also { d.readFully(it) }, Charsets.UTF_8)
        }
        fun skip(n: Long) {
            var left = n
            while (left > 0) {
                val s = d.skipBytes(minOf(left, Int.MAX_VALUE.toLong()).toInt())
                if (s <= 0) { d.readByte(); left-- } else left -= s
            }
        }
        fun skipValue(type: Int) {
            when (type) {
                0, 1, 7 -> skip(1)
                2, 3 -> skip(2)
                4, 5, 6 -> skip(4)
                10, 11, 12 -> skip(8)
                8 -> { val len = u64(); if (len < 0 || len > MAX_STRING) throw GgufException("The GGUF file is corrupt."); skip(len) }
                9 -> {
                    val elem = u32().toInt()
                    val count = u64()
                    if (count < 0 || count > 50_000_000L) throw GgufException("The GGUF file is corrupt (bad array).")
                    when (elem) {
                        0, 1, 7 -> skip(count)
                        2, 3 -> skip(count * 2)
                        4, 5, 6 -> skip(count * 4)
                        10, 11, 12 -> skip(count * 8)
                        else -> for (j in 0 until count) skipValue(elem)
                    }
                }
                else -> throw GgufException("Unknown GGUF value type $type.")
            }
        }
    }
}
