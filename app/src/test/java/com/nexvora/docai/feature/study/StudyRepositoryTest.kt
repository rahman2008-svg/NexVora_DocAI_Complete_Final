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
package com.nexvora.docai.feature.study

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StudyRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun repo() = StudyRepository(tmp.root.resolve("study.json"))

    @Test fun scoringCountsOnlyCorrectAnswers() {
        val qs = listOf(Mcq("1+1?", listOf("1", "2"), 1), Mcq("2+2?", listOf("3", "4"), 1), Mcq("3+3?", listOf("6", "7"), 0))
        val r = Scoring.score(qs, mapOf(0 to 1, 1 to 0))
        assertThat(r.correct).isEqualTo(1)
        assertThat(r.total).isEqualTo(3)
        assertThat(r.percent).isEqualTo(33)
        assertThat(Scoring.score(emptyList(), emptyMap()).percent).isEqualTo(0)
    }

    @Test fun mcqValidation() {
        assertThat(Mcq("q", listOf("a", "b"), 1).isValid()).isTrue()
        assertThat(Mcq("", listOf("a", "b"), 0).isValid()).isFalse()
        assertThat(Mcq("q", listOf("a", ""), 0).isValid()).isFalse()
        assertThat(Mcq("q", listOf("a", "b"), 2).isValid()).isFalse()
    }

    @Test fun notesDecksAndSetsPersist() {
        val r = repo()
        val n = r.saveNote(null, "T", "body")
        r.saveNote(n, "T2", "body2")
        val d = r.createDeck("Biology")
        r.addCard(d, Flashcard("Q", "A"))
        val s = r.createSet("Set")
        assertThat(r.addQuestion(s, Mcq("q", listOf("a", "b"), 0))).isTrue()
        assertThat(r.addQuestion(s, Mcq("", listOf("a", "b"), 0))).isFalse()
        val again = repo().data.value
        assertThat(again.notes.single().title).isEqualTo("T2")
        assertThat(again.decks.single().cards).hasSize(1)
        assertThat(again.sets.single().questions).hasSize(1)
        r.deleteNote(n); r.deleteCard(d, 0); r.deleteSet(s); r.deleteDeck(d)
        assertThat(repo().data.value).isEqualTo(StudyData())
    }

    @Test fun mergeIsAdditive() {
        val r = repo()
        val id = r.saveNote(null, "mine", "x")
        r.merge(StudyData(notes = listOf(StudyNote(id, "other", "y", 0), StudyNote("new", "new", "z", 0))))
        assertThat(r.data.value.notes.map { it.title }).containsExactlyInAnyOrder("mine", "new")
    }
}
