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

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupCryptoException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { NOT_ENCRYPTED, UNSUPPORTED, WRONG_PASSWORD_OR_CORRUPT }
}

/**
 * Password-based, authenticated encryption for backups ("NXBK" format, version 1).
 *
 *  header: "NXBK" | version(1) | pbkdf2 iterations(4) | salt(16) | base IV(8)
 *  then chunks of up to 1 MiB of plaintext, each stored as: length(4) | AES-256-GCM ciphertext+tag.
 *  Chunk IV = base IV | counter(4); AAD = header | counter(4) | finalFlag(1), so reordering, truncation and
 *  tampering are detected. Key = PBKDF2-HMAC-SHA256(password, salt).
 * Wrong passwords and damaged files cannot be told apart (both fail authentication).
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'X'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val BASE_IV_LEN = 8
    private const val HEADER_LEN = 4 + 1 + 4 + SALT_LEN + BASE_IV_LEN
    const val CHUNK = 1 shl 20
    private const val TAG = 16
    private const val MIN_ITER = 100_000
    private const val MAX_ITER = 5_000_000
    const val MIN_PASSWORD_LENGTH = 8

    fun isEncrypted(file: File): Boolean = try {
        file.inputStream().use { input ->
            val m = ByteArray(4)
            input.read(m) == 4 && m.contentEquals(MAGIC)
        }
    } catch (_: IOException) { false }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    private fun iv(base: ByteArray, counter: Int): ByteArray = ByteBuffer.allocate(12).put(base).putInt(counter).array()

    private fun aad(header: ByteArray, counter: Int, final: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + 5).put(header).putInt(counter).put(if (final) 1 else 0).array()

    private fun cipher(mode: Int, key: SecretKey, header: ByteArray, baseIv: ByteArray, counter: Int, final: Boolean): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG * 8, iv(baseIv, counter)))
            updateAAD(aad(header, counter, final))
        }

    /** Wraps [out]; everything written is encrypted. Closing the stream writes the final chunk (and closes [out]). */
    class EncryptingOutputStream(
        private val out: OutputStream,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ) : OutputStream() {
        private val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        private val baseIv = ByteArray(BASE_IV_LEN).also(random::nextBytes)
        private val header = ByteBuffer.allocate(HEADER_LEN).put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(baseIv).array()
        private val key = deriveKey(password, salt, iterations)
        private val buffer = ByteArray(CHUNK)
        private var count = 0
        private var counter = 0
        private var closed = false

        init { out.write(header) }

        private fun emit(final: Boolean) {
            val ct = cipher(Cipher.ENCRYPT_MODE, key, header, baseIv, counter, final).doFinal(buffer, 0, count)
            out.write(ByteBuffer.allocate(4).putInt(ct.size).array())
            out.write(ct)
            counter++
            count = 0
        }

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off
            var left = len
            while (left > 0) {
                if (count == CHUNK) emit(false) // more data follows, so this chunk is not the last one
                val n = minOf(left, CHUNK - count)
                System.arraycopy(b, o, buffer, count, n)
                count += n; o += n; left -= n
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            emit(true)
            out.flush()
            out.close()
        }
    }

    fun encrypt(input: InputStream, output: OutputStream, password: CharArray, iterations: Int = DEFAULT_ITERATIONS) {
        EncryptingOutputStream(output, password, iterations).use { input.copyTo(it) }
    }

    /** Decrypts [input] into [output]. Throws [BackupCryptoException]. Does not close the streams. */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val data = DataInputStream(input)
        val header = ByteArray(HEADER_LEN)
        try { data.readFully(header) } catch (_: EOFException) {
            throw BackupCryptoException(BackupCryptoException.Kind.NOT_ENCRYPTED, "This is not an encrypted NexVora backup.")
        }
        if (!header.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw BackupCryptoException(BackupCryptoException.Kind.NOT_ENCRYPTED, "This is not an encrypted NexVora backup.")
        }
        val bb = ByteBuffer.wrap(header, 4, HEADER_LEN - 4)
        if (bb.get() != VERSION) throw BackupCryptoException(BackupCryptoException.Kind.UNSUPPORTED, "Unsupported backup version.")
        val iterations = bb.getInt()
        if (iterations !in MIN_ITER..MAX_ITER) throw BackupCryptoException(BackupCryptoException.Kind.WRONG_PASSWORD_OR_CORRUPT, "The backup header is damaged.")
        val salt = ByteArray(SALT_LEN).also { bb.get(it) }
        val baseIv = ByteArray(BASE_IV_LEN).also { bb.get(it) }
        val key = deriveKey(password, salt, iterations)
        val pb = PushbackInputStream(input, 1)
        var counter = 0
        while (true) {
            val len = try { DataInputStream(pb).readInt() } catch (_: EOFException) {
                throw BackupCryptoException(BackupCryptoException.Kind.WRONG_PASSWORD_OR_CORRUPT, "The backup is truncated.")
            }
            if (len < TAG || len > CHUNK + TAG) throw BackupCryptoException(BackupCryptoException.Kind.WRONG_PASSWORD_OR_CORRUPT, "The backup is damaged.")
            val ct = ByteArray(len)
            try { DataInputStream(pb).readFully(ct) } catch (_: EOFException) {
                throw BackupCryptoException(BackupCryptoException.Kind.WRONG_PASSWORD_OR_CORRUPT, "The backup is truncated.")
            }
            val next = pb.read()
            val final = next < 0
            if (!final) pb.unread(next)
            val plain = try {
                cipher(Cipher.DECRYPT_MODE, key, header, baseIv, counter, final).doFinal(ct)
            } catch (_: GeneralSecurityException) {
                throw BackupCryptoException(BackupCryptoException.Kind.WRONG_PASSWORD_OR_CORRUPT, "Wrong password, or the backup is damaged.")
            }
            output.write(plain)
            if (final) return
            counter++
        }
    }

    fun decryptToBytes(data: ByteArray, password: CharArray): ByteArray =
        ByteArrayOutputStream().also { decrypt(data.inputStream(), it, password) }.toByteArray()
}
