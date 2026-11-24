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
package com.nexvora.docai.feature.vault

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.common.NxShare
import com.nexvora.docai.core.common.formatBytes
import com.nexvora.docai.core.design.NexVoraDialog
import com.nexvora.docai.core.design.NexVoraDocumentItem
import com.nexvora.docai.core.design.NexVoraLoadingIndicator
import com.nexvora.docai.core.design.NexVoraSearchBar
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxChip
import com.nexvora.docai.core.design.NxSectionTitle
import com.nexvora.docai.core.design.NxTextInputDialog
import java.text.DateFormat
import java.util.Date

private sealed interface VaultDialog {
    data object NewFolder : VaultDialog
    data class RenameDoc(val doc: VaultDoc) : VaultDialog
    data class RenameFolder(val folder: VaultFolder) : VaultDialog
    data class Move(val doc: VaultDoc) : VaultDialog
    data class DeleteDoc(val doc: VaultDoc) : VaultDialog
    data class DeleteFolder(val folder: VaultFolder) : VaultDialog
    data class Details(val doc: VaultDoc) : VaultDialog
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

@Composable
fun VaultScreen(
    controller: VaultController,
    onBack: () -> Unit,
    onOpenPdf: (VaultDoc) -> Unit,
) {
    val context = LocalContext.current
    val vault = controller.vault
    val index by vault.state.collectAsState()
    val busy by controller.busy.collectAsState()
    val message by controller.message.collectAsState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VaultFilter.ALL) }
    var sort by remember { mutableStateOf(VaultSort.DATE_DESC) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<VaultDialog?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var exportDoc by remember { mutableStateOf<VaultDoc?>(null) }

    LaunchedEffect(message) {
        message?.let { toast(context, it); controller.message.value = null }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        controller.importUris(uris, openFolder ?: VaultRepository.OTHER)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        exportDoc?.let { controller.exportTo(it, uri) }
        exportDoc = null
    }

    val folderName = index.folders.firstOrNull { it.id == openFolder }?.name
    val listMode = query.isNotBlank() || filter != VaultFilter.ALL || showAll || openFolder != null
    val results = index.docs.query(query, filter, sort, openFolder)

    fun openDoc(doc: VaultDoc) {
        if (doc.isPdf) onOpenPdf(doc) else NxShare.open(context, vault.file(doc), doc.mime)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NexVoraTopBar(
                title = folderName ?: "My Documents",
                onBack = {
                    if (openFolder != null) openFolder = null
                    else if (showAll) showAll = false
                    else onBack()
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, "Sort", tint = Nx.TextMain)
                        }
                        DropdownMenu(sortMenu, { sortMenu = false }, containerColor = Nx.Card) {
                            VaultSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label + if (s == sort) "  ✓" else "", color = Nx.TextMain) },
                                    onClick = { sort = s; sortMenu = false },
                                )
                            }
                        }
                    }
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                NexVoraSearchBar(query, { query = it }, placeholder = "Search documents…")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    items(VaultFilter.entries.toList()) { f -> NxChip(f.label, filter == f, { filter = f }) }
                }
            }
            if (busy) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { NexVoraLoadingIndicator() }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                if (!listMode) {
                    item { NxSectionTitle("Folders") }
                    items(index.folders, key = { it.id }) { folder ->
                        var menu by remember { mutableStateOf(false) }
                        NexVoraDocumentItem(
                            title = folder.name,
                            subtitle = "${index.docsIn(folder.id).size} items",
                            icon = Icons.Rounded.Folder,
                            accent = folderColor(folder.id),
                            onClick = { openFolder = folder.id },
                            trailing = {
                                if (!folder.builtIn) Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Folder menu", tint = Nx.TextSecondary) }
                                    DropdownMenu(menu, { menu = false }, containerColor = Nx.Card) {
                                        DropdownMenuItem(text = { Text("Rename", color = Nx.TextMain) }, onClick = { menu = false; dialog = VaultDialog.RenameFolder(folder) })
                                        DropdownMenuItem(text = { Text("Delete", color = Nx.Danger) }, onClick = { menu = false; dialog = VaultDialog.DeleteFolder(folder) })
                                    }
                                }
                            },
                        )
                    }
                    item { NxSectionTitle("Recent documents") }
                    val recent = index.docs.query(sort = VaultSort.DATE_DESC).take(5)
                    if (recent.isEmpty()) {
                        item { Text("No documents yet. Tap + to import files, or scan a document and save it here.", color = Nx.TextSecondary, fontSize = 13.sp) }
                    } else {
                        items(recent, key = { "r" + it.id }) { doc -> DocRow(doc, index, vault, controller, ::openDoc, { dialog = it }, { exportDoc = it; exportLauncher.launch(it.fileName) }) }
                        item {
                            Text("View all documents", color = Nx.Cyan, fontSize = 14.sp,
                                modifier = Modifier.clickable { showAll = true }.padding(vertical = 12.dp))
                        }
                    }
                } else {
                    item { NxSectionTitle(if (results.isEmpty()) "No matching documents" else "${results.size} document(s)") }
                    items(results, key = { it.id }) { doc -> DocRow(doc, index, vault, controller, ::openDoc, { dialog = it }, { exportDoc = it; exportLauncher.launch(it.fileName) }) }
                }
            }
        }
        // Add button
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Nx.Gradient).clickable { addMenu = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Add", tint = Nx.TextMain) }
            DropdownMenu(addMenu, { addMenu = false }, containerColor = Nx.Card) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.UploadFile, null, tint = Nx.Cyan) },
                    text = { Text("Import files", color = Nx.TextMain) },
                    onClick = { addMenu = false; importLauncher.launch(arrayOf("*/*")) },
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null, tint = Nx.Cyan) },
                    text = { Text("New folder", color = Nx.TextMain) },
                    onClick = { addMenu = false; dialog = VaultDialog.NewFolder },
                )
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        VaultDialog.NewFolder -> NxTextInputDialog("New folder", "Folder name", confirmLabel = "Create",
            onConfirm = { name ->
                if (vault.createFolder(name) == null) toast(context, "A folder with this name already exists.")
                dialog = null
            }, onDismiss = { dialog = null })
        is VaultDialog.RenameDoc -> NxTextInputDialog("Rename document", "Name", d.doc.name, "Rename",
            onConfirm = { if (!vault.rename(d.doc.id, it)) toast(context, "Invalid name."); dialog = null },
            onDismiss = { dialog = null })
        is VaultDialog.RenameFolder -> NxTextInputDialog("Rename folder", "Folder name", d.folder.name, "Rename",
            onConfirm = { if (!vault.renameFolder(d.folder.id, it)) toast(context, "Cannot rename: invalid or duplicate name."); dialog = null },
            onDismiss = { dialog = null })
        is VaultDialog.Move -> NexVoraDialogMove(index.folders, d.doc.folderId,
            onPick = { vault.move(d.doc.id, it); dialog = null }, onDismiss = { dialog = null })
        is VaultDialog.Details -> DetailsDialog(d.doc, vault, index.folders.firstOrNull { it.id == d.doc.folderId }?.name ?: "", onDismiss = { dialog = null })
        is VaultDialog.DeleteDoc -> NexVoraDialog(
            title = "Delete document?",
            message = "\"${d.doc.fileName}\" will be permanently removed from NexVora DocAI. This cannot be undone.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = { vault.delete(d.doc.id); dialog = null }, onDismiss = { dialog = null })
        is VaultDialog.DeleteFolder -> NexVoraDialog(
            title = "Delete folder?",
            message = if (index.docsIn(d.folder.id).isEmpty()) "The empty folder \"${d.folder.name}\" will be removed."
            else "\"${d.folder.name}\" still contains documents. Move or delete them first.",
            confirmLabel = if (index.docsIn(d.folder.id).isEmpty()) "Delete" else "OK", destructive = index.docsIn(d.folder.id).isEmpty(),
            onConfirm = { vault.deleteFolder(d.folder.id); dialog = null }, onDismiss = { dialog = null })
    }
}

@Composable
private fun NexVoraDialogMove(
    folders: List<VaultFolder>,
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nx.Card,
        titleContentColor = Nx.TextMain,
        title = { Text("Move to folder") },
        text = {
            Column {
                folders.forEach { f ->
                    Text(
                        f.name + if (f.id == currentId) "  (current)" else "",
                        color = if (f.id == currentId) Nx.TextSecondary else Nx.TextMain,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = f.id != currentId) { onPick(f.id) }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = Nx.TextSecondary) } },
    )
}

private fun folderColor(id: String): Color = when (id) {
    "personal" -> Nx.Warning
    "education" -> Color(0xFF4DA3FF)
    "certificates" -> Nx.Success
    "business" -> Color(0xFF4DA3FF)
    "notes" -> Nx.Warning
    else -> Nx.Violet
}

@Composable
private fun DocRow(
    doc: VaultDoc,
    index: VaultIndex,
    vault: VaultRepository,
    controller: VaultController,
    onOpen: (VaultDoc) -> Unit,
    onDialog: (VaultDialog) -> Unit,
    onExport: (VaultDoc) -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val folder = index.folders.firstOrNull { it.id == doc.folderId }?.name ?: ""
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.modifiedAt))
    NexVoraDocumentItem(
        title = doc.fileName,
        subtitle = "${formatBytes(doc.sizeBytes)} · $date · $folder",
        icon = when { doc.isPdf -> Icons.Rounded.PictureAsPdf; doc.isImage -> Icons.Rounded.Image; else -> Icons.Rounded.Description },
        accent = when { doc.isPdf -> Nx.Danger; doc.isImage -> Nx.Cyan; else -> Nx.Violet },
        onClick = { onOpen(doc) },
        leading = if (doc.isPdf || doc.isImage) ({ VaultThumbnail(doc, vault.file(doc)) }) else null,
        trailing = {
            if (doc.favorite) Icon(Icons.Rounded.Star, "Favorite", tint = Nx.Warning, modifier = Modifier.size(18.dp))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Document menu", tint = Nx.TextSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = Nx.Card) {
                    @Composable
                    fun item(label: String, color: Color = Nx.TextMain, action: () -> Unit) =
                        DropdownMenuItem(text = { Text(label, color = color) }, onClick = { menu = false; action() })
                    item("Open") { onOpen(doc) }
                    item("Share") { NxShare.share(context, listOf(vault.file(doc)), doc.mime) }
                    item("Export to…") { onExport(doc) }
                    item("Details") { onDialog(VaultDialog.Details(doc)) }
                    item("Rename") { onDialog(VaultDialog.RenameDoc(doc)) }
                    item("Move to…") { onDialog(VaultDialog.Move(doc)) }
                    item(if (doc.favorite) "Remove favorite" else "Add to favorites") { vault.setFavorite(doc.id, !doc.favorite) }
                    item("Delete", Nx.Danger) { onDialog(VaultDialog.DeleteDoc(doc)) }
                }
            }
        },
    )
}


@Composable
private fun VaultThumbnail(doc: VaultDoc, file: java.io.File) {
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, doc.id, doc.sizeBytes) {
        value = VaultThumbs.get(doc, file)
    }
    val b = bmp
    if (b != null) androidx.compose.foundation.Image(
        bitmap = b.asImageBitmap(), contentDescription = "Preview of ${doc.fileName}",
        modifier = Modifier.size(44.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    ) else Icon(if (doc.isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Image, null, tint = Nx.TextSecondary)
}

@Composable
private fun DetailsDialog(doc: VaultDoc, vault: VaultRepository, folder: String, onDismiss: () -> Unit) {
    val pages by androidx.compose.runtime.produceState<Int?>(null, doc.id) {
        value = if (doc.isPdf) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.nexvora.docai.feature.pdf.PdfPageRenderer.open(vault.file(doc))?.use { it.pageCount }
        } else null
    }
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = Nx.Card, titleContentColor = Nx.TextMain, textContentColor = Nx.TextSecondary,
        title = { Text(doc.fileName) },
        text = {
            Column {
                Text("Type: ${doc.mime}")
                Text("Size: ${formatBytes(doc.sizeBytes)}")
                pages?.let { Text("Pages: $it") }
                Text("Folder: $folder")
                Text("Added: ${fmt.format(Date(doc.createdAt))}")
                Text("Modified: ${fmt.format(Date(doc.modifiedAt))}")
                doc.sha256?.let { Text("SHA-256: ${it.take(16)}…") }
                Text("Stored privately on this device (not encrypted).")
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close", color = Nx.Cyan) } },
    )
}
