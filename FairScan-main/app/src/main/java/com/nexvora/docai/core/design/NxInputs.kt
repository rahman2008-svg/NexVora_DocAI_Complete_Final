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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun nxTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Nx.TextMain,
    unfocusedTextColor = Nx.TextMain,
    focusedBorderColor = Nx.Violet,
    unfocusedBorderColor = Nx.Border,
    cursorColor = Nx.Cyan,
    focusedLabelColor = Nx.Violet,
    unfocusedLabelColor = Nx.TextSecondary,
    focusedPlaceholderColor = Nx.TextSecondary,
    unfocusedPlaceholderColor = Nx.TextSecondary,
    focusedContainerColor = Nx.Card,
    unfocusedContainerColor = Nx.Card,
)

@Composable
fun NxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        colors = nxTextFieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

@Composable
fun NxChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        color = if (selected) Nx.TextMain else Nx.TextSecondary,
        fontSize = 13.sp,
        modifier = modifier
            .clip(shape)
            .background(if (selected) SolidColor(Nx.Purple) else SolidColor(Nx.Card))
            .border(1.dp, if (selected) Nx.Purple else Nx.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun NxTextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmLabel: String = "OK",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nx.Card,
        titleContentColor = Nx.TextMain,
        title = { Text(title) },
        text = { NxTextField(text, { text = it }, label) },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel, color = if (text.isNotBlank()) Nx.Cyan else Nx.TextSecondary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nx.TextSecondary) } },
    )
}
