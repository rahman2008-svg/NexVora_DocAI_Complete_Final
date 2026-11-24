/*
 * Copyright 2025-2026 The FairScan authors
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
package com.nexvora.docai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nexvora.docai.core.design.Nx

// NexVora DocAI palette (dark-only, matches the reference design).
private val DarkColorScheme = darkColorScheme(
    primary = Nx.Violet,
    onPrimary = Nx.TextMain,
    primaryContainer = Nx.DeepViolet,
    onPrimaryContainer = Nx.TextMain,
    secondary = Nx.Cyan,
    onSecondary = Nx.BgMain,
    secondaryContainer = Nx.CardSecondary,
    onSecondaryContainer = Nx.TextMain,
    tertiary = Nx.Purple,
    onTertiary = Nx.TextMain,
    tertiaryContainer = Nx.CardSecondary,
    onTertiaryContainer = Nx.TextMain,
    error = Nx.Danger,
    onError = Nx.BgMain,
    errorContainer = Color(0xFF5A1325),
    onErrorContainer = Nx.TextMain,
    background = Nx.BgMain,
    onBackground = Nx.TextMain,
    surface = Nx.Card,
    onSurface = Nx.TextMain,
    surfaceVariant = Nx.CardSecondary,
    onSurfaceVariant = Nx.TextSecondary,
    outline = Color(0xFF4A4F7A)
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline
)

@Composable
fun FairScanTheme(
    // NexVora DocAI is dark-only; the parameter is kept for source compatibility with upstream call sites.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}