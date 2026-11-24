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

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds tiny synthetic GGUF headers for tests (no real model weights). */
object GgufTestData {
    private fun ByteArrayOutputStream.le(size: Int, f: ByteBuffer.() -> Unit) =
        write(ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).also(f).array())
    private fun ByteArrayOutputStream.u32(v: Long) = le(4) { putInt(v.toInt()) }
    private fun ByteArrayOutputStream.u64(v: Long) = le(8) { putLong(v) }
    private fun ByteArrayOutputStream.str(s: String) { val b = s.toByteArray(); u64(b.size.toLong()); write(b) }

    fun minimal(version: Int = 3, arch: String = "llama", ctx: Long = 4096, withArrayBeforeCtx: Boolean = true): ByteArray {
        val o = ByteArrayOutputStream()
        o.write("GGUF".toByteArray()); o.u32(version.toLong()); o.u64(0); o.u64(if (withArrayBeforeCtx) 5 else 4)
        o.str("general.architecture"); o.u32(8); o.str(arch)
        o.str("general.name"); o.u32(8); o.str("Tiny Test")
        o.str("general.license"); o.u32(8); o.str("apache-2.0")
        if (withArrayBeforeCtx) { // array of 3 strings, skipped by the parser
            o.str("tokenizer.ggml.tokens"); o.u32(9); o.u32(8); o.u64(3); o.str("a"); o.str("bb"); o.str("ccc")
        }
        o.str("$arch.context_length"); o.u32(4); o.u32(ctx)
        return o.toByteArray()
    }
}
