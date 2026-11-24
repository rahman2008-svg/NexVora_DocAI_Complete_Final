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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.common.ExtractiveSummary
import com.nexvora.docai.core.common.PageText
import com.nexvora.docai.core.design.NexVoraButton
import com.nexvora.docai.core.design.NexVoraCard
import com.nexvora.docai.core.design.NexVoraDialog
import com.nexvora.docai.core.design.NexVoraFeatureCard
import com.nexvora.docai.core.design.NexVoraGradientButton
import com.nexvora.docai.core.design.NexVoraLoadingIndicator
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxBannerKind
import com.nexvora.docai.core.design.NxChip
import com.nexvora.docai.core.design.NxSectionTitle
import com.nexvora.docai.core.design.NxStatusBanner
import com.nexvora.docai.core.design.NxTextField
import com.nexvora.docai.core.design.NxTextInputDialog
import com.nexvora.docai.feature.ai.AiTask
import com.nexvora.docai.feature.ai.PromptBuilder
import com.nexvora.docai.feature.ai.TextChunker
import com.nexvora.docai.feature.assistant.DocumentTextExtractor
import com.nexvora.docai.feature.models.TextGenerationEngine
import com.nexvora.docai.feature.vault.VaultDoc
import com.nexvora.docai.feature.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class Module(val label: String, val sub: String, val icon: ImageVector, val color: Color) {
    SUMMARY("Summary", "Key sentences", Icons.Rounded.Summarize, Color(0xFF3DDC97)),
    MCQ("MCQ", "Practice", Icons.Rounded.FormatListNumbered, Color(0xFF8957FF)),
    FLASHCARDS("Flashcards", "Reveal & flip", Icons.Rounded.Style, Color(0xFFFFB84D)),
    NOTES("Notes", "Saved locally", Icons.Rounded.Description, Color(0xFFFF5C7A)),
    QUIZ("Quiz", "Scored test", Icons.Rounded.Quiz, Color(0xFF4DA3FF)),
    QA("Q&A", "Ask your PDF", Icons.Rounded.QuestionAnswer, Color(0xFF00D5FF)),
}

@Composable
fun StudyScreen(
    repo: StudyRepository,
    vault: VaultRepository,
    extractor: DocumentTextExtractor,
    scope: CoroutineScope,
    engine: TextGenerationEngine,
    contextLength: Int,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit,
) {
    var module by rememberSaveable { mutableStateOf<String?>(null) }
    val current = Module.entries.firstOrNull { it.name == module }
    BackHandler(enabled = current != null) { module = null }
    Column(Modifier.fillMaxSize()) {
        NexVoraTopBar(current?.label ?: "Study Assistant", onBack = { if (current != null) module = null else onBack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (current) {
                null -> {
                    NxStatusBanner("Automatic question, flashcard and answer generation needs a local AI model, which is not installed. You can write your own cards and questions, take scored quizzes, and get extractive summaries from your documents.", NxBannerKind.INFO)
                    Module.entries.toList().chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { m -> NexVoraFeatureCard(m.label, m.sub, m.icon, m.color, { module = m.name }, Modifier.weight(1f)) }
                        }
                    }
                }
                Module.SUMMARY -> SummaryModule(repo, vault, extractor, scope, engine, contextLength)
                Module.NOTES -> NotesModule(repo)
                Module.FLASHCARDS -> FlashcardsModule(repo)
                Module.MCQ -> QuizModule(repo, instantFeedback = true)
                Module.QUIZ -> QuizModule(repo, instantFeedback = false)
                Module.QA -> {
                    NxStatusBanner("Question answering over a PDF with a language model is unavailable in this build. The assistant can search the document for your keywords and show the matching pages.")
                    NexVoraGradientButton("Open document search", onOpenAssistant, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SummaryModule(repo: StudyRepository, vault: VaultRepository, extractor: DocumentTextExtractor, scope: CoroutineScope, engine: TextGenerationEngine, contextLength: Int) {
    val index by vault.state.collectAsState()
    var doc by remember { mutableStateOf<VaultDoc?>(null) }
    var pages by remember { mutableStateOf<List<PageText>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<List<String>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    val docs = index.docs.filter { it.isPdf || it.isText }
    Text("Source document", color = Nx.TextMain, fontSize = 15.sp)
    if (docs.isEmpty()) Text("No PDFs in Files yet.", color = Nx.TextSecondary, fontSize = 13.sp)
    docs.forEach { d ->
        NxChip(d.fileName, doc?.id == d.id, {
            doc = d; busy = true; error = null; summary = emptyList(); saved = false; pages = emptyList(); page = 0
            scope.launch(Dispatchers.IO) {
                try { pages = extractor.extractEmbedded(vault.file(d)) } catch (e: Exception) { error = "Could not read this document: ${e.message}" }
                busy = false
            }
        })
    }
    if (busy) NexVoraLoadingIndicator()
    error?.let { NxStatusBanner(it, NxBannerKind.ERROR) }
    if (pages.isNotEmpty()) {
        if (pages.none { it.text.isNotBlank() }) NxStatusBanner("No selectable text in this PDF. Run OCR from the assistant first.", NxBannerKind.WARNING)
        else {
            NexVoraGradientButton("Create extractive summary", { summary = ExtractiveSummary.summarize(pages.joinToString("\n") { it.text }, 6); saved = false }, Modifier.fillMaxWidth())
            if (summary.isNotEmpty()) {
                NexVoraCard {
                    Text("Extractive summary (key sentences picked from the document; not AI-written)", color = Nx.TextSecondary, fontSize = 12.sp)
                    SelectionContainer { Text(summary.joinToString("\n") { "• $it" }, color = Nx.TextMain, fontSize = 14.sp) }
                }
                NexVoraButton(if (saved) "Saved to Notes" else "Save as note", {
                    repo.saveNote(null, "Summary: ${doc?.name}", summary.joinToString("\n") { "• $it" }); saved = true
                }, enabled = !saved)
            }
            NxSectionTitle("Generate study material")
            if (!engine.isAvailable) {
                NxStatusBanner("Local AI model not installed. Automatic flashcards and questions are unavailable; create them by hand in Flashcards / MCQ.", NxBannerKind.WARNING)
            } else {
                var gen by remember { mutableStateOf<String?>(null) }
                var genBusy by remember { mutableStateOf(false) }
                fun run(task: AiTask) {
                    genBusy = true; gen = null
                    scope.launch(Dispatchers.Default) {
                        try {
                            val all = TextChunker.chunk(pages)
                            val chosen = if (all.size <= 8) all else (0 until 8).map { all[it * (all.size - 1) / 7] }
                            val prompt = PromptBuilder.build(task, "", chosen, (contextLength * 3 - 1000).coerceAtLeast(1500))
                            val raw = engine.generate(prompt.text, 900) {}
                            val name = "AI: ${doc?.name} (review before use)"
                            gen = if (task == AiTask.FLASHCARDS) AiJsonParser.flashcards(raw).let { c -> if (c.isEmpty()) "The model did not return valid flashcards. Nothing was saved." else { repo.createDeckWith(name, c); "Saved ${c.size} flashcards to a new deck. AI output can be wrong: review it." } }
                            else AiJsonParser.mcqs(raw).let { q -> if (q.isEmpty()) "The model did not return valid questions. Nothing was saved." else { repo.createSetWith(name, q); "Saved ${q.size} questions to a new set. AI output can be wrong: review it." } }
                        } catch (e: Exception) { gen = "Generation failed: ${e.message}" }
                        genBusy = false
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexVoraButton("Flashcards (AI)", { run(AiTask.FLASHCARDS) }, Modifier.weight(1f), enabled = !genBusy)
                    NexVoraButton("MCQs (AI)", { run(AiTask.QUESTIONS) }, Modifier.weight(1f), enabled = !genBusy)
                }
                if (genBusy) NexVoraLoadingIndicator()
                gen?.let { NxStatusBanner(it) }
            }
            NxSectionTitle("Source text")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexVoraButton("◀", { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0)
                Text("Page ${page + 1} / ${pages.size}", color = Nx.TextMain, modifier = Modifier.padding(top = 10.dp))
                NexVoraButton("▶", { page = (page + 1).coerceAtMost(pages.size - 1) }, enabled = page < pages.size - 1)
            }
            SelectionContainer { Text(pages[page].text.ifBlank { "(no text on this page)" }, color = Nx.TextMain, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun NotesModule(repo: StudyRepository) {
    val data by repo.data.collectAsState()
    var editId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<StudyNote?>(null) }
    if (editing) {
        NxTextField(title, { title = it }, "Title", Modifier.fillMaxWidth())
        NxTextField(body, { body = it }, "Note", Modifier.fillMaxWidth(), singleLine = false, minLines = 6)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexVoraGradientButton("Save", { repo.saveNote(editId, title, body); editing = false }, Modifier.weight(1f), enabled = title.isNotBlank() || body.isNotBlank())
            NexVoraButton("Cancel", { editing = false }, Modifier.weight(1f))
        }
    } else {
        NexVoraGradientButton("New note", { editId = null; title = ""; body = ""; editing = true }, Modifier.fillMaxWidth())
        if (data.notes.isEmpty()) Text("No notes yet.", color = Nx.TextSecondary, fontSize = 13.sp)
        data.notes.forEach { n ->
            NexVoraCard(onClick = { editId = n.id; title = n.title; body = n.body; editing = true }, contentPadding = 12) {
                Text(n.title, color = Nx.TextMain, fontSize = 15.sp)
                Text(n.body.take(120), color = Nx.TextSecondary, fontSize = 12.sp, maxLines = 3)
                Spacer(Modifier.height(6.dp))
                NexVoraButton("Delete", { delete = n }, destructive = true)
            }
        }
    }
    delete?.let { n -> NexVoraDialog("Delete note?", "\"${n.title}\" will be deleted.", "Delete", { repo.deleteNote(n.id); delete = null }, { delete = null }, destructive = true) }
}

@Composable
private fun FlashcardsModule(repo: StudyRepository) {
    val data by repo.data.collectAsState()
    var deckId by rememberSaveable { mutableStateOf<String?>(null) }
    var newDeck by remember { mutableStateOf(false) }
    var studying by remember { mutableStateOf(false) }
    var idx by remember { mutableStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var deleteDeck by remember { mutableStateOf<Deck?>(null) }
    val deck = data.decks.firstOrNull { it.id == deckId }
    if (deck == null) {
        NexVoraGradientButton("New deck", { newDeck = true }, Modifier.fillMaxWidth())
        if (data.decks.isEmpty()) Text("No decks yet. Create a deck and add your own cards.", color = Nx.TextSecondary, fontSize = 13.sp)
        data.decks.forEach { d -> NexVoraCard(onClick = { deckId = d.id; studying = false }, contentPadding = 12) {
            Text(d.name, color = Nx.TextMain, fontSize = 15.sp); Text("${d.cards.size} card(s)", color = Nx.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp)); NexVoraButton("Delete deck", { deleteDeck = d }, destructive = true) } }
    } else if (studying && deck.cards.isNotEmpty()) {
        val card = deck.cards[idx.coerceIn(0, deck.cards.size - 1)]
        Text("Card ${idx + 1} of ${deck.cards.size}", color = Nx.TextSecondary, fontSize = 13.sp)
        NexVoraCard(Modifier.fillMaxWidth().clickable { revealed = !revealed }) {
            Text(if (revealed) card.back else card.front, color = Nx.TextMain, fontSize = 20.sp)
            Text(if (revealed) "Answer · tap to hide" else "Question · tap to reveal", color = Nx.Cyan, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexVoraButton("Previous", { idx = (idx - 1).coerceAtLeast(0); revealed = false }, Modifier.weight(1f), enabled = idx > 0)
            NexVoraButton(if (revealed) "Hide" else "Reveal", { revealed = !revealed }, Modifier.weight(1f))
            NexVoraButton("Next", { idx = (idx + 1).coerceAtMost(deck.cards.size - 1); revealed = false }, Modifier.weight(1f), enabled = idx < deck.cards.size - 1)
        }
        NexVoraButton("Back to deck", { studying = false }, Modifier.fillMaxWidth())
    } else {
        Text(deck.name, color = Nx.TextMain, fontSize = 18.sp)
        NexVoraGradientButton("Study (${deck.cards.size})", { idx = 0; revealed = false; studying = true }, Modifier.fillMaxWidth(), enabled = deck.cards.isNotEmpty())
        NxTextField(front, { front = it }, "Front (question)", Modifier.fillMaxWidth())
        NxTextField(back, { back = it }, "Back (answer)", Modifier.fillMaxWidth())
        NexVoraButton("Add card", { repo.addCard(deck.id, Flashcard(front.trim(), back.trim())); front = ""; back = "" }, Modifier.fillMaxWidth(), enabled = front.isNotBlank() && back.isNotBlank())
        deck.cards.forEachIndexed { i, c -> NexVoraCard(contentPadding = 10) {
            Text(c.front, color = Nx.TextMain, fontSize = 14.sp); Text(c.back, color = Nx.TextSecondary, fontSize = 12.sp)
            NexVoraButton("Remove", { repo.deleteCard(deck.id, i) }, destructive = true) } }
        NexVoraButton("All decks", { deckId = null }, Modifier.fillMaxWidth())
    }
    if (newDeck) NxTextInputDialog("New deck", "Deck name", confirmLabel = "Create", onConfirm = { deckId = repo.createDeck(it); newDeck = false }, onDismiss = { newDeck = false })
    deleteDeck?.let { d -> NexVoraDialog("Delete deck?", "\"${d.name}\" and its ${d.cards.size} card(s) will be deleted.", "Delete", { repo.deleteDeck(d.id); deleteDeck = null }, { deleteDeck = null }, destructive = true) }
}

@Composable
private fun QuizModule(repo: StudyRepository, instantFeedback: Boolean) {
    val data by repo.data.collectAsState()
    var setId by rememberSaveable { mutableStateOf<String?>(null) }
    var newSet by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var submitted by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var opts by remember { mutableStateOf(listOf("", "", "", "")) }
    var correct by remember { mutableStateOf(0) }
    var deleteSet by remember { mutableStateOf<QuestionSet?>(null) }
    val set = data.sets.firstOrNull { it.id == setId }
    if (set == null) {
        NexVoraGradientButton("New question set", { newSet = true }, Modifier.fillMaxWidth())
        if (data.sets.isEmpty()) Text("No question sets yet. Write your own multiple-choice questions.", color = Nx.TextSecondary, fontSize = 13.sp)
        if (data.attempts.isNotEmpty()) {
            NxSectionTitle("Review history")
            data.attempts.take(8).forEach { a ->
                Text("${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(a.time))} · ${a.setName} · ${a.mode} · ${a.correct}/${a.total}", color = Nx.TextSecondary, fontSize = 12.sp)
            }
        }
        data.sets.forEach { s -> NexVoraCard(onClick = { setId = s.id; running = false }, contentPadding = 12) {
            Text(s.name, color = Nx.TextMain, fontSize = 15.sp); Text("${s.questions.size} question(s)", color = Nx.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp)); NexVoraButton("Delete set", { deleteSet = s }, destructive = true) } }
    } else if (running) {
        val result = Scoring.score(set.questions, answers)
        set.questions.forEachIndexed { i, question ->
            NexVoraCard(contentPadding = 12) {
                Text("${i + 1}. ${question.question}", color = Nx.TextMain, fontSize = 15.sp)
                question.options.forEachIndexed { oi, text ->
                    val chosen = answers[i] == oi
                    val showResult = submitted || (instantFeedback && answers.containsKey(i))
                    val color = when {
                        showResult && oi == question.correct -> Nx.Success
                        showResult && chosen -> Nx.Danger
                        chosen -> Nx.Violet
                        else -> Nx.TextSecondary
                    }
                    Text(
                        (if (chosen) "● " else "○ ") + text, color = color, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted && !(instantFeedback && answers.containsKey(i))) { answers = answers + (i to oi) }.padding(vertical = 8.dp),
                    )
                }
                val answeredNow = submitted || (instantFeedback && answers.containsKey(i))
                if (answeredNow && question.explanation.isNotBlank()) Text("Explanation: ${question.explanation}", color = Nx.Cyan, fontSize = 12.sp)
            }
        }
        if (!submitted) {
            NexVoraGradientButton(if (instantFeedback) "Finish" else "Submit answers", { submitted = true; repo.addAttempt(set, Scoring.score(set.questions, answers), if (instantFeedback) "practice" else "quiz") }, Modifier.fillMaxWidth(), enabled = answers.isNotEmpty())
        } else {
            NxStatusBanner("Score: ${result.correct} / ${result.total} (${result.percent}%)", if (result.percent >= 50) NxBannerKind.SUCCESS else NxBannerKind.WARNING)
            NexVoraButton("Try again", { answers = emptyMap(); submitted = false }, Modifier.fillMaxWidth())
        }
        NexVoraButton("Back", { running = false }, Modifier.fillMaxWidth())
    } else {
        Text(set.name, color = Nx.TextMain, fontSize = 18.sp)
        NexVoraGradientButton(if (instantFeedback) "Practice" else "Start quiz", { answers = emptyMap(); submitted = false; running = true }, Modifier.fillMaxWidth(), enabled = set.questions.isNotEmpty())
        NxSectionTitle("Add a question")
        NxTextField(q, { q = it }, "Question", Modifier.fillMaxWidth())
        opts.forEachIndexed { i, o ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                NxChip(if (correct == i) "✓" else "○", correct == i, { correct = i })
                Spacer(Modifier.padding(4.dp))
                NxTextField(o, { v -> opts = opts.toMutableList().also { it[i] = v } }, "Option ${i + 1}", Modifier.weight(1f))
            }
        }
        Text("Tap the circle to mark the correct option.", color = Nx.TextSecondary, fontSize = 12.sp)
        val candidate = Mcq(q.trim(), opts.map { it.trim() }, correct)
        NexVoraButton("Add question", { if (repo.addQuestion(set.id, candidate)) { q = ""; opts = listOf("", "", "", ""); correct = 0 } }, Modifier.fillMaxWidth(), enabled = candidate.isValid())
        set.questions.forEachIndexed { i, qq -> NexVoraCard(contentPadding = 10) {
            Text("${i + 1}. ${qq.question}", color = Nx.TextMain, fontSize = 14.sp)
            NexVoraButton("Remove", { repo.deleteQuestion(set.id, i) }, destructive = true) } }
        NexVoraButton("All sets", { setId = null }, Modifier.fillMaxWidth())
    }
    if (newSet) NxTextInputDialog("New question set", "Set name", confirmLabel = "Create", onConfirm = { setId = repo.createSet(it); newSet = false }, onDismiss = { newSet = false })
    deleteSet?.let { s -> NexVoraDialog("Delete set?", "\"${s.name}\" and its ${s.questions.size} question(s) will be deleted.", "Delete", { repo.deleteSet(s.id); deleteSet = null }, { deleteSet = null }, destructive = true) }
}
