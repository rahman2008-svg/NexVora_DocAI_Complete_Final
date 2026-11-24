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
package com.nexvora.docai.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.design.NexVoraFeatureCard
import com.nexvora.docai.core.design.Nx

data class HomeActions(
    val onScan: () -> Unit,
    val onAssistant: () -> Unit,
    val onOcr: () -> Unit,
    val onPdfStudio: () -> Unit,
    val onVault: () -> Unit,
    val onStudy: () -> Unit,
    val onTranslation: () -> Unit,
)

@Composable
fun HomeScreen(displayName: String, actions: HomeActions) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(if (displayName.isBlank()) "Hello!" else "Hello, $displayName", color = Nx.TextMain, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("What would you like to do today?", color = Nx.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Nx.Gradient).clickable(onClick = actions.onScan).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Scan & Create PDF", color = Nx.TextMain, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Turn your documents into digital files", color = Nx.TextMain.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Icon(Icons.Rounded.DocumentScanner, null, tint = Nx.TextMain, modifier = Modifier.padding(start = 12.dp).height(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        val cards = listOf(
            Triple("AI Assistant", "Chat with PDF", Icons.Rounded.Psychology) to (Nx.Cyan to actions.onAssistant),
            Triple("OCR", "Extract Text", Icons.Rounded.TextFields) to (Color(0xFF3DDC97) to actions.onOcr),
            Triple("PDF Studio", "Edit & Manage", Icons.Rounded.PictureAsPdf) to (Nx.Danger to actions.onPdfStudio),
            Triple("Document Vault", "Files & folders", Icons.Rounded.Folder) to (Nx.Warning to actions.onVault),
            Triple("Study Helper", "Notes & Quiz", Icons.Rounded.School) to (Nx.Violet to actions.onStudy),
            Triple("Translation", "Bangla · English", Icons.Rounded.Translate) to (Color(0xFF4DA3FF) to actions.onTranslation),
        )
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                row.forEach { (info, accent) ->
                    NexVoraFeatureCard(info.first, info.second, info.third, accent.first, accent.second, Modifier.weight(1f))
                }
            }
        }
    }
}
