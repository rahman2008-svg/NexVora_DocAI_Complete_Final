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

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** NexVora DocAI design tokens (see the reference design board). */
object Nx {
    val BgMain = Color(0xFF080914)
    val BgSecondary = Color(0xFF0D1024)
    val Card = Color(0xFF12152B)
    val CardSecondary = Color(0xFF191D3B)
    val Purple = Color(0xFF702CFF)
    val Violet = Color(0xFF8957FF)
    val DeepViolet = Color(0xFF5A2BE2)
    val Cyan = Color(0xFF00D5FF)
    val TextMain = Color(0xFFF7F7FF)
    val TextSecondary = Color(0xFFA9ACCC)

    val Success = Color(0xFF3DDC97)
    val Warning = Color(0xFFFFB84D)
    val Danger = Color(0xFFFF5C7A)
    val Border = Color(0x1FFFFFFF)

    val Gradient = Brush.horizontalGradient(listOf(DeepViolet, Purple, Violet))
    val GradientCyan = Brush.horizontalGradient(listOf(Purple, Cyan))
    val BackgroundBrush = Brush.verticalGradient(listOf(BgSecondary, BgMain))
}
