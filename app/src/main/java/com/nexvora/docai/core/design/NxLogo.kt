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
package com.nexvora.docai.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Original NexVora DocAI mark (document + flowing purple/cyan ribbon + sparkle), drawn as vector. */
@Composable
fun NexVoraLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        fun x(v: Float) = v / 108f * s
        // Rounded-square tile
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Nx.BgSecondary, Nx.DeepViolet.copy(alpha = 0.6f))),
            size = Size(s, s),
            cornerRadius = CornerRadius(x(26f)),
        )
        // Document body
        val doc = Path().apply {
            moveTo(x(42f), x(28f)); lineTo(x(62f), x(28f)); lineTo(x(72f), x(38f)); lineTo(x(72f), x(72f))
            quadraticTo(x(72f), x(78f), x(66f), x(78f)); lineTo(x(42f), x(78f))
            quadraticTo(x(36f), x(78f), x(36f), x(72f)); lineTo(x(36f), x(34f))
            quadraticTo(x(36f), x(28f), x(42f), x(28f)); close()
        }
        drawPath(doc, Brush.linearGradient(listOf(Nx.TextMain, Color(0xFFC9B8FF)), Offset(x(36f), x(28f)), Offset(x(72f), x(78f))))
        // Folded corner
        val fold = Path().apply {
            moveTo(x(62f), x(28f)); lineTo(x(62f), x(34f)); quadraticTo(x(62f), x(38f), x(66f), x(38f)); lineTo(x(72f), x(38f)); close()
        }
        drawPath(fold, Nx.Violet)
        // Text lines
        listOf(44f to 58f, 51f to 66f, 58f to 66f).forEach { (y, xe) ->
            drawRoundRect(Nx.Purple, Offset(x(41f), x(y)), Size(x(xe - 41f), x(2.5f)), CornerRadius(x(1.2f)))
        }
        // Ribbon
        val ribbon = Path().apply {
            moveTo(x(26f), x(70f)); cubicTo(x(40f), x(88f), x(68f), x(88f), x(84f), x(58f))
            cubicTo(x(74f), x(74f), x(50f), x(80f), x(26f), x(70f)); close()
        }
        drawPath(ribbon, Brush.linearGradient(listOf(Nx.DeepViolet, Nx.Violet, Nx.Cyan), Offset(x(26f), x(70f)), Offset(x(84f), x(58f))))
        // Sparkle
        val star = Path().apply {
            moveTo(x(78f), x(22f)); lineTo(x(79.6f), x(26.4f)); lineTo(x(84f), x(28f)); lineTo(x(79.6f), x(29.6f))
            lineTo(x(78f), x(34f)); lineTo(x(76.4f), x(29.6f)); lineTo(x(72f), x(28f)); lineTo(x(76.4f), x(26.4f)); close()
        }
        drawPath(star, Nx.Cyan)
    }
}
