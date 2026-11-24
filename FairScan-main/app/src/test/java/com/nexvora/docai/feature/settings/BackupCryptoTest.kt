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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.Random

class BackupCryptoTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pw = "correct horse battery".toCharArray()
    private fun enc(data: ByteArray, password: CharArray = pw): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(data.inputStream(), out, password, iterations = 100_000)
        return out.toByteArray()
    }

    @Test fun roundTripSmallAndMultiChunk() {
        for (size in listOf(0, 1, 1000, BackupCrypto.CHUNK, BackupCrypto.CHUNK + 1, 2 * BackupCrypto.CHUNK + 123)) {
            val data = ByteArray(size).also { Random(size.toLong()).nextBytes(it) }
            val encrypted = enc(data)
            assertThat(BackupCrypto.decryptToBytes(encrypted, pw)).isEqualTo(data)
        }
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val data = "very secret document text".repeat(20).toByteArray()
        assertThat(String(enc(data), Charsets.ISO_8859_1)).doesNotContain("secret document")
    }

    @Test fun wrongPasswordIsRejected() {
        val e = try { BackupCrypto.decryptToBytes(enc("x".toByteArray()), "wrong password!".toCharArray()); null } catch (e: BackupCryptoException) { e }
        assertThat(e!!.kind).isEqualTo(BackupCryptoException.Kind.WRONG_PASSWORD_OR_CORRUPT)
    }

    @Test fun tamperingAndTruncationAreDetected() {
        val encrypted = enc(ByteArray(3 * BackupCrypto.CHUNK / 2) { it.toByte() })
        val flipped = encrypted.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        val truncated = encrypted.copyOf(encrypted.size - 20)
        for (bad in listOf(flipped, truncated, encrypted.copyOf(10))) {
            try { BackupCrypto.decryptToBytes(bad, pw); throw AssertionError("expected failure") } catch (_: BackupCryptoException) { }
        }
    }

    @Test fun plainDataIsNotRecognisedAsEncrypted() {
        val f = tmp.newFile("plain.zip").apply { writeBytes(byteArrayOf(0x50, 0x4b, 3, 4, 0)) }
        assertThat(BackupCrypto.isEncrypted(f)).isFalse()
        val g = tmp.newFile("enc").apply { writeBytes(enc("a".toByteArray())) }
        assertThat(BackupCrypto.isEncrypted(g)).isTrue()
        try { BackupCrypto.decryptToBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34), pw); throw AssertionError() }
        catch (e: BackupCryptoException) { assertThat(e.kind).isEqualTo(BackupCryptoException.Kind.NOT_ENCRYPTED) }
    }
}
