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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexvora.docai.core.design.NexVoraDocumentItem
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxSectionTitle
import com.nexvora.docai.core.design.NxStatusBanner

@Composable
fun ScanHubScreen(onScanner: () -> Unit, onOcr: () -> Unit, onPdfStudio: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        NexVoraTopBar("Scan")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NxSectionTitle("Capture")
            NexVoraDocumentItem("Scan a document", "Camera with automatic edge detection, crop, filters, multi-page PDF", Icons.Rounded.DocumentScanner, Nx.Violet, onScanner)
            NexVoraDocumentItem("Import photos", "Open the scanner and tap the gallery button to import images", Icons.Rounded.DocumentScanner, Nx.Cyan, onScanner)
            NxSectionTitle("Process")
            NexVoraDocumentItem("Extract text (OCR)", "Recognise printed text in an image or PDF", Icons.Rounded.TextFields, Nx.Success, onOcr)
            NexVoraDocumentItem("Images to PDF, merge, split…", "PDF Studio tools", Icons.Rounded.PictureAsPdf, Nx.Danger, onPdfStudio)
            NxStatusBanner("Scanned PDFs are saved with the scanner's Save/Share buttons. To keep them in Files, import them from Files › + › Import files.")
        }
    }
}
