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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning

/** Full-screen NexVora background (dark gradient + one soft purple glow, cheap to draw). */
@Composable
fun NexVoraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Nx.BackgroundBrush)
            .background(
                Brush.radialGradient(
                    colors = listOf(Nx.Purple.copy(alpha = 0.22f), Color.Transparent),
                    radius = 900f,
                )
            )
    ) { content() }
}

@Composable
fun NexVoraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Nx.Card,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, Nx.Border), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding.dp),
        content = content,
    )
}

@Composable
fun NexVoraGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (enabled) Nx.Gradient else SolidColor(Nx.CardSecondary))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Nx.TextMain else Nx.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            color = if (enabled) Nx.TextMain else Nx.TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NexVoraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val tint = when {
        !enabled -> Nx.TextSecondary.copy(alpha = 0.5f)
        destructive -> Nx.Danger
        else -> Nx.TextMain
    }
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(Nx.CardSecondary)
            .border(BorderStroke(1.dp, Nx.Border), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tint, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NexVoraTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Nx.TextMain)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            color = Nx.TextMain,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

enum class NxTab { HOME, SCAN, FILES, SETTINGS }

@Composable
fun NexVoraBottomNavigation(selected: NxTab?, onSelect: (NxTab) -> Unit, onCamera: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Nx.BgSecondary)
            .border(BorderStroke(1.dp, Nx.Border))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavItem("Home", Icons.Rounded.Home, selected == NxTab.HOME, Modifier.weight(1f)) { onSelect(NxTab.HOME) }
        NavItem("Scan", Icons.Rounded.DocumentScanner, selected == NxTab.SCAN, Modifier.weight(1f)) { onSelect(NxTab.SCAN) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(56.dp)
                    .shadow(12.dp, CircleShape, ambientColor = Nx.Purple, spotColor = Nx.Purple)
                    .clip(CircleShape)
                    .background(Nx.Gradient)
                    .clickable(onClick = onCamera),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = "Scan a document", tint = Nx.TextMain, modifier = Modifier.size(28.dp))
            }
        }
        NavItem("Files", Icons.Rounded.Folder, selected == NxTab.FILES, Modifier.weight(1f)) { onSelect(NxTab.FILES) }
        NavItem("Settings", Icons.Rounded.Settings, selected == NxTab.SETTINGS, Modifier.weight(1f)) { onSelect(NxTab.SETTINGS) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) Nx.Violet else Nx.TextSecondary
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun NexVoraSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Nx.Card)
            .border(BorderStroke(1.dp, Nx.Border), shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Nx.TextSecondary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Nx.TextSecondary, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Nx.TextMain),
                cursorBrush = SolidColor(Nx.Cyan),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun NexVoraFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NexVoraCard(modifier = modifier, onClick = onClick, contentPadding = 14) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(title, color = Nx.TextMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Nx.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NexVoraDocumentItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color = Nx.Violet,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { if (leading != null) leading() else Icon(icon, null, tint = accent) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Nx.TextMain, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Nx.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun NexVoraDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nx.Card,
        titleContentColor = Nx.TextMain,
        textContentColor = Nx.TextSecondary,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Nx.Danger else Nx.Cyan)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = Nx.TextSecondary) } },
    )
}

/** Indeterminate when [progress] is null, determinate otherwise. */
@Composable
fun NexVoraLoadingIndicator(modifier: Modifier = Modifier, progress: Float? = null, linear: Boolean = false) {
    if (linear) {
        if (progress == null) {
            LinearProgressIndicator(modifier = modifier.fillMaxWidth(), color = Nx.Cyan, trackColor = Nx.CardSecondary)
        } else {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth(), color = Nx.Cyan, trackColor = Nx.CardSecondary)
        }
    } else {
        if (progress == null) {
            CircularProgressIndicator(modifier = modifier.size(28.dp), color = Nx.Cyan, strokeWidth = 3.dp)
        } else {
            CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.size(28.dp), color = Nx.Cyan, strokeWidth = 3.dp)
        }
    }
}

enum class NxBannerKind { INFO, WARNING, SUCCESS, ERROR }

/** Inline status message used for honest "not available / failed / done" states. */
@Composable
fun NxStatusBanner(text: String, kind: NxBannerKind = NxBannerKind.INFO, modifier: Modifier = Modifier) {
    val (color, icon) = when (kind) {
        NxBannerKind.INFO -> Nx.Cyan to Icons.Rounded.Info
        NxBannerKind.WARNING -> Nx.Warning to Icons.Rounded.Warning
        NxBannerKind.SUCCESS -> Nx.Success to Icons.Rounded.CheckCircle
        NxBannerKind.ERROR -> Nx.Danger to Icons.Rounded.Warning
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.4f)), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Nx.TextMain, fontSize = 13.sp)
    }
}

@Composable
fun NxSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Nx.TextMain,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        modifier = modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}
