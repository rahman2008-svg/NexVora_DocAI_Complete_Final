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
import com.nexvora.docai.feature.vault.VaultRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun manager(name: String): Triple<BackupManager, VaultRepository, StudyRepository> {
        val dir = tmp.root.resolve(name).apply { mkdirs() }
        val v = VaultRepository(dir.resolve("vault"))
        val s = StudyRepository(dir.resolve("study.json"))
        return Triple(BackupManager(v, s), v, s)
    }

    @Test fun backupRoundTrip() {
        val (a, va, sa) = manager("a")
        val doc = va.importStream("Report.pdf", null, "hello".byteInputStream(), "business").doc
        sa.saveNote(null, "Note", "body")
        val zip = tmp.newFile("b.zip")
        zip.outputStream().use { assertThat(a.create(it, "1.0.0")).isEqualTo(1) }

        val (b, vb, sb) = manager("b")
        assertThat(b.restore(zip)).isEqualTo(1)
        val restored = vb.state.value.docs.single()
        assertThat(restored.id).isEqualTo(doc.id)
        assertThat(vb.file(restored).readText()).isEqualTo("hello")
        assertThat(sb.data.value.notes.single().title).isEqualTo("Note")
        assertThat(b.restore(zip)).isEqualTo(0) // additive: nothing duplicated
    }

    @Test fun invalidZipIsRejected() {
        val (m, _, _) = manager("c")
        val notZip = tmp.newFile("x.zip").apply { writeText("nope") }
        try { m.restore(notZip); throw AssertionError("expected failure") } catch (e: IllegalArgumentException) { }
        val other = tmp.newFile("y.zip")
        ZipOutputStream(other.outputStream()).use { it.putNextEntry(ZipEntry("random.txt")); it.write(1); it.closeEntry() }
        try { m.restore(other); throw AssertionError("expected failure") } catch (e: IllegalArgumentException) { }
    }

    @Test fun maliciousIdsInBackupAreIgnored() {
        val (m, v, _) = manager("d")
        val zip: File = tmp.newFile("evil.zip")
        val index = """{"folders":[],"docs":[{"id":"../../evil","name":"x","ext":"txt","mime":"text/plain","sizeBytes":1,"folderId":"other","createdAt":1,"modifiedAt":1}]}"""
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("vault/index.json")); it.write(index.toByteArray()); it.closeEntry()
            it.putNextEntry(ZipEntry("vault/files/../../evil.txt")); it.write("pwned".toByteArray()); it.closeEntry()
        }
        assertThat(m.restore(zip)).isEqualTo(0)
        assertThat(v.state.value.docs).isEmpty()
        assertThat(tmp.root.resolve("evil.txt").exists()).isFalse()
    }

    @Test fun encryptedBackupRoundTripAndWrongPassword() {
        val (a, va, _) = manager("e1")
        va.importStream("Secret.pdf", null, "top secret".byteInputStream(), "personal")
        val file = tmp.newFile("enc.nxbackup")
        file.outputStream().use { a.create(it, "1.0.0", "password123".toCharArray()) }
        assertThat(BackupCrypto.isEncrypted(file)).isTrue()
        assertThat(String(file.readBytes(), Charsets.ISO_8859_1)).doesNotContain("top secret")

        val (b, vb, _) = manager("e2")
        val tmpDir = tmp.newFolder("tmpdir")
        try { b.restoreAny(file, "wrong-password".toCharArray(), tmpDir); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) { }
        try { b.restoreAny(file, null, tmpDir); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) { }
        assertThat(vb.state.value.docs).isEmpty()
        assertThat(b.restoreAny(file, "password123".toCharArray(), tmpDir)).isEqualTo(1)
        assertThat(vb.file(vb.state.value.docs.single()).readText()).isEqualTo("top secret")
    }
}
