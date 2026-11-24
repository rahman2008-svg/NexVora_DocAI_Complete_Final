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
package com.nexvora.docai.feature.pdf

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.design.NexVoraLoadingIndicator
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.NxBannerKind
import com.nexvora.docai.core.design.NxStatusBanner
import com.nexvora.docai.core.design.Nx
import java.io.File

/** Simple in-app PDF viewer built on the platform PdfRenderer (no network, no extra library). */
@Composable
fun PdfViewerScreen(file: File, title: String, onClose: () -> Unit, initialPage: Int = 0) {
    BackHandler(onBack = onClose)
    val renderer = remember(file) { PdfPageRenderer.open(file) }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }
    Column(Modifier.fillMaxSize().background(Nx.BgMain)) {
        NexVoraTopBar(title, onBack = onClose)
        if (renderer == null) {
            Box(Modifier.padding(16.dp)) {
                NxStatusBanner(
                    "This PDF could not be opened. It may be corrupt or password-protected.",
                    NxBannerKind.ERROR,
                )
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
                LazyColumn(
                    Modifier.fillMaxSize().navigationBarsPadding(),
                    state = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items((0 until renderer.pageCount).toList()) { index ->
                        val bitmap by produceState<Bitmap?>(null, renderer, index, widthPx) {
                            value = runCatching { renderer.renderWidth(index, widthPx) }.getOrNull()
                        }
                        Column(Modifier.fillMaxWidth()) {
                            val bmp = bitmap
                            if (bmp == null) {
                                Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                                    NexVoraLoadingIndicator()
                                }
                            } else {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth,
                                )
                            }
                            Text(
                                "Page ${index + 1} / ${renderer.pageCount}",
                                color = Nx.TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
