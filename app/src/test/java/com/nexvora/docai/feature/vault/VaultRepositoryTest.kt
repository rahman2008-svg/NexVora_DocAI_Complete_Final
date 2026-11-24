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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException

class VaultRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000L

    private fun repo() = VaultRepository(tmp.root.resolve("vault")) { now++ }
    private fun bytes(s: String) = ByteArrayInputStream(s.toByteArray())

    @Test fun startsWithSixBuiltInFolders() {
        assertThat(repo().state.value.folders.map { it.name })
            .containsExactly("Personal", "Education", "Certificates", "Business", "Notes", "Other")
    }

    @Test fun importStoresFileAndMetadata() {
        val r = repo()
        val doc = r.importStream("Report.pdf", null, bytes("data"), "business").doc
        assertThat(doc.name).isEqualTo("Report")
        assertThat(doc.mime).isEqualTo("application/pdf")
        assertThat(doc.folderId).isEqualTo("business")
        assertThat(r.file(doc).readText()).isEqualTo("data")
        assertThat(r.state.value.docs).hasSize(1)
    }

    @Test fun emptyImportIsRejected() {
        val r = repo()
        try { r.importStream("empty.txt", null, bytes(""), "other"); throw AssertionError("expected IOException") }
        catch (e: IOException) { assertThat(r.state.value.docs).isEmpty() }
    }

    @Test fun persistsAcrossInstances() {
        val doc = repo().importStream("a.txt", null, bytes("x")).doc
        val reloaded = repo()
        assertThat(reloaded.state.value.docs.map { it.id }).containsExactly(doc.id)
    }

    @Test fun renameMoveFavoriteDelete() {
        val r = repo()
        val doc = r.importStream("a.pdf", null, bytes("x")).doc
        assertThat(r.rename(doc.id, "b.pdf")).isTrue()
        assertThat(r.rename(doc.id, "///")).isTrue() // sanitised to "___"
        assertThat(r.move(doc.id, "personal")).isTrue()
        assertThat(r.move(doc.id, "missing")).isFalse()
        r.setFavorite(doc.id, true)
        assertThat(r.state.value.docs.single().favorite).isTrue()
        val file = r.file(doc)
        assertThat(r.delete(doc.id)).isTrue()
        assertThat(file.exists()).isFalse()
        assertThat(r.state.value.docs).isEmpty()
    }

    @Test fun folderRules() {
        val r = repo()
        val f = r.createFolder("Taxes")!!
        assertThat(r.createFolder("taxes")).isNull()
        assertThat(r.renameFolder("personal", "X")).isFalse()
        assertThat(r.renameFolder(f.id, "Taxes 2026")).isTrue()
        val doc = r.importStream("a.pdf", null, bytes("x"), f.id).doc
        assertThat(r.deleteFolder(f.id)).isFalse() // not empty: documents are never deleted implicitly
        r.delete(doc.id)
        assertThat(r.deleteFolder(f.id)).isTrue()
        assertThat(r.deleteFolder("other")).isFalse()
    }

    @Test fun corruptIndexFallsBackToDefaults() {
        val root = tmp.root.resolve("vault").apply { mkdirs() }
        root.resolve("index.json").writeText("{not json")
        val r = VaultRepository(root)
        assertThat(r.state.value.docs).isEmpty()
        assertThat(root.resolve("index.json.corrupt").exists()).isTrue()
    }

    @Test fun missingFilesAreDroppedFromIndex() {
        val r = repo()
        val doc = r.importStream("a.pdf", null, bytes("x")).doc
        r.file(doc).delete()
        assertThat(repo().state.value.docs).isEmpty()
    }

    @Test fun queryFiltersAndSorts() {
        val r = repo()
        val a = r.importStream("alpha.pdf", null, bytes("12345")).doc
        val b = r.importStream("beta.png", null, bytes("1")).doc
        r.setFavorite(b.id, true)
        val docs = r.state.value.docs
        assertThat(docs.query("alp").map { it.id }).containsExactly(a.id)
        assertThat(docs.query(filter = VaultFilter.PDF).map { it.id }).containsExactly(a.id)
        assertThat(docs.query(filter = VaultFilter.IMAGES).map { it.id }).containsExactly(b.id)
        assertThat(docs.query(filter = VaultFilter.FAVORITES).map { it.id }).containsExactly(b.id)
        assertThat(docs.query(sort = VaultSort.NAME_ASC).map { it.id }).containsExactly(a.id, b.id)
        assertThat(docs.query(sort = VaultSort.SIZE_DESC).map { it.id }).containsExactly(a.id, b.id)
        assertThat(docs.query(sort = VaultSort.DATE_DESC).map { it.id }).containsExactly(b.id, a.id)
    }

    @Test fun identicalContentIsNotDuplicated() {
        val r = repo()
        val first = r.importStream("a.pdf", null, bytes("same"))
        val second = r.importStream("copy of a.pdf", null, bytes("same"))
        assertThat(first.isNew).isTrue()
        assertThat(second.isNew).isFalse()
        assertThat(second.doc.id).isEqualTo(first.doc.id)
        assertThat(r.state.value.docs).hasSize(1)
        assertThat(r.importStream("b.pdf", null, bytes("different")).isNew).isTrue()
    }
}
