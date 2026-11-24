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
package com.nexvora.docai.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexvora.docai.feature.home.HomeActions
import com.nexvora.docai.feature.home.HomeScreen
import com.nexvora.docai.ui.theme.FairScanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Instrumented UI test (not run in the authoring environment): Home cards trigger their destinations. */
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeCardsNavigate() {
        val clicks = mutableListOf<String>()
        rule.setContent {
            FairScanTheme {
                HomeScreen(
                    "Test",
                    HomeActions(
                        onScan = { clicks += "scan" }, onAssistant = { clicks += "assistant" }, onOcr = { clicks += "ocr" },
                        onPdfStudio = { clicks += "pdf" }, onVault = { clicks += "vault" }, onStudy = { clicks += "study" },
                        onTranslation = { clicks += "translation" },
                    ),
                )
            }
        }
        rule.onNodeWithText("Hello, Test").assertExists()
        rule.onNodeWithText("Scan & Create PDF").performClick()
        rule.onNodeWithText("PDF Studio").performClick()
        rule.onNodeWithText("Translation").performClick()
        assertEquals(listOf("scan", "pdf", "translation"), clicks)
    }
}
