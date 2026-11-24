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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.design.NexVoraBackground
import com.nexvora.docai.core.design.NexVoraLoadingIndicator
import com.nexvora.docai.core.design.NexVoraLogo
import com.nexvora.docai.core.design.Nx
import kotlinx.coroutines.delay

/**
 * Splash with real initialisation: waits (max 6 s) until the document-segmentation model used by the
 * scanner has loaded, then continues. It never loops forever; the scanner handles a missing model itself.
 */
@Composable
fun SplashScreen(vaultReady: () -> Boolean, scannerReady: () -> Boolean, onDone: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0.05f) }
    var status by remember { mutableStateOf("Initializing your workspace…") }
    LaunchedEffect(Unit) {
        status = "Opening your document vault…"
        while (!vaultReady()) delay(50)
        progress = 0.4f
        status = "Preparing the scanner…"
        var waited = 0
        while (!scannerReady() && waited < 6000) { delay(100); waited += 100 }
        progress = 1f
        status = if (scannerReady()) "Ready" else "Scanner is still loading in the background"
        delay(250)
        onDone()
    }
    NexVoraBackground {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            NexVoraLogo(120.dp)
            Spacer(Modifier.height(20.dp))
            Text("NexVora", color = Nx.TextMain, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("DocAI", color = Nx.Violet, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Scan. Understand. Organize. Create.", color = Nx.TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(40.dp))
            NexVoraLoadingIndicator(progress = progress, linear = true, modifier = Modifier.fillMaxWidth(0.6f))
            Spacer(Modifier.height(10.dp))
            Text(status, color = Nx.TextSecondary, fontSize = 12.sp)
        }
    }
}
