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
package com.nexvora.docai.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ModelTraining
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.common.copyUriTo
import com.nexvora.docai.core.common.formatBytes
import com.nexvora.docai.core.design.NexVoraButton
import com.nexvora.docai.core.design.NexVoraCard
import com.nexvora.docai.core.design.NexVoraDocumentItem
import com.nexvora.docai.core.design.NexVoraLogo
import com.nexvora.docai.core.design.NexVoraTopBar
import com.nexvora.docai.core.design.Nx
import com.nexvora.docai.core.design.NxBannerKind
import com.nexvora.docai.core.design.NxSectionTitle
import com.nexvora.docai.core.design.NxStatusBanner
import com.nexvora.docai.core.design.NxTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val APP_VERSION = "1.0.0"

data class SettingsActions(
    val onModels: () -> Unit,
    val onOcrLanguages: () -> Unit,
    val onScannerSettings: () -> Unit,
    val onPrivacy: () -> Unit,
    val onBackup: () -> Unit,
    val onLibraries: () -> Unit,
    val onAbout: () -> Unit,
    val onStorage: () -> Unit = {},
)

@Composable
fun SettingsScreen(prefs: NxPrefs, lock: AppLock, actions: SettingsActions) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(prefs.displayName) }
    Column(Modifier.fillMaxSize()) {
        NexVoraTopBar("Settings")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NexVoraCard(onClick = actions.onAbout) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexVoraLogo(56.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("NexVora DocAI $APP_VERSION", color = Nx.TextMain, fontSize = 16.sp)
                        Text("Developer: Prince AR Abdur Rahman", color = Nx.TextSecondary, fontSize = 12.sp)
                        Text("Publisher: NexVora Lab's Ofc", color = Nx.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
            NxTextField(name, { name = it; prefs.updateDisplayName(it) }, "Your name (shown on Home)", Modifier.fillMaxWidth())
            NxSectionTitle("Security")
            NexVoraDocumentItem(
                "App Lock", if (prefs.appLockEnabled) "On · biometrics or device PIN/pattern/password" else "Off", Icons.Rounded.Lock, Nx.Violet, {},
                trailing = {
                    Switch(
                        checked = prefs.appLockEnabled,
                        onCheckedChange = { want ->
                            lock.authenticate(if (want) "Confirm to turn on App Lock" else "Confirm to turn off App Lock") { ok, err ->
                                if (ok) { prefs.updateAppLock(want); if (!want) lock.disableLockState() }
                                else Toast.makeText(context, err ?: "Not changed.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Nx.Purple),
                    )
                },
            )
            NexVoraDocumentItem("Privacy & Security", "Storage, permissions, data handling", Icons.Rounded.Security, Nx.Cyan, actions.onPrivacy)
            NxSectionTitle("Features")
            NexVoraDocumentItem("Model Manager", "Local AI / translation model files", Icons.Rounded.ModelTraining, Nx.Violet, actions.onModels)
            NexVoraDocumentItem("OCR languages", "Download English / Bengali text data", Icons.Rounded.TextFields, Nx.Success, actions.onOcrLanguages)
            NexVoraDocumentItem("Scanner & export", "Colour mode, export folder, quality", Icons.Rounded.DocumentScanner, Nx.Warning, actions.onScannerSettings)
            NexVoraDocumentItem("Storage & cache", "Space used by documents, models and temporary files", Icons.Rounded.Description, Nx.Warning, actions.onStorage)
            NexVoraDocumentItem("Backup & Restore", "Export or import your data", Icons.Rounded.Backup, Nx.Danger, actions.onBackup)
            NxSectionTitle("About")
            NexVoraDocumentItem("About NexVora DocAI", "Version $APP_VERSION · license & attribution", Icons.Rounded.Info, Nx.Cyan, actions.onAbout)
            NexVoraDocumentItem("Open-source licenses", "Libraries used by this app", Icons.Rounded.Description, Nx.TextSecondary, actions.onLibraries)
        }
    }
}

@Composable
private fun InfoPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        NexVoraTopBar(title, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Para(head: String, body: String) {
    NexVoraCard {
        Text(head, color = Nx.TextMain, fontSize = 15.sp)
        Text(body, color = Nx.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) = InfoPage("Privacy & Security", onBack) {
    NxStatusBanner("NexVora DocAI has no analytics, no advertising, no cloud account and no tracking. Your documents are never uploaded.", NxBannerKind.SUCCESS)
    Para("Where documents are stored", "Inside the app's private storage on this device (Files, PDF Studio results, notes, quizzes). Other apps cannot read it, but it is not encrypted; anyone who can unlock your phone and open the app can read it. App Lock only protects access to the app screens.")
    Para("Permissions used", "Camera: scanning. Internet: only for downloads you start yourself (OCR language data, model files you choose). Biometrics: App Lock. Storage (Android 9 and older only): saving exports. Files are picked with the system file picker, so no broad storage access is requested.")
    Para("AI models", "Model files are kept in the app's private 'models' folder, can be deleted at any time, and are never uploaded. This build contains no AI inference engine, so models cannot be run yet.")
    Para("Does information leave the device?", "Only when you start a download (the download server sees the usual connection data) or when you share/export a file yourself.")
    Para("Backups", "Android cloud auto-backup is turned off. Backup & Restore creates a ZIP only when you ask for it, and you choose where it is saved. The ZIP is not encrypted.")
}

@Composable
fun AboutScreen(onBack: () -> Unit, onLibraries: () -> Unit) = InfoPage("About NexVora DocAI", onBack) {
    NexVoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexVoraLogo(64.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text("NexVora DocAI", color = Nx.TextMain, fontSize = 20.sp)
                Text("Scan. Understand. Organize. Create.", color = Nx.TextSecondary, fontSize = 12.sp)
                Text("Version $APP_VERSION", color = Nx.TextSecondary, fontSize = 12.sp)
            }
        }
    }
    Para("Developer / publisher", "Developer: Prince AR Abdur Rahman\nPublisher: NexVora Lab's Ofc")
    Para("Copyright", "© 2026 NexVora Lab's Ofc. All Rights Reserved. (applies to NexVora-authored material; it does not restrict the rights the GPL gives you over this program)")
    Para("Based on FairScan", "NexVora DocAI is a modified version of FairScan 2.4.0, © The FairScan authors, licensed under the GNU General Public License v3.0 or later. The scanner engine (camera, edge detection, perspective correction, PDF export, OCR) comes from FairScan.")
    Para("License", "This program is free software under GPL-3.0-or-later and comes with NO WARRANTY. If you receive this app, you are entitled to its complete corresponding source code; see the README for how it is provided.")
    NexVoraButton("Open-source licenses", onLibraries, Modifier.fillMaxWidth())
}

@Composable
fun BackupScreen(backup: BackupManager, scope: CoroutineScope, onBack: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var encrypt by remember { mutableStateOf(true) }
    var pw1 by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<File?>(null) }
    var restorePw by remember { mutableStateOf("") }
    val tempDir = File(context.cacheDir, "nexvora/restore")
    val passwordOk = !encrypt || (pw1.length >= BackupCrypto.MIN_PASSWORD_LENGTH && pw1 == pw2)

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val password = if (encrypt) pw1.toCharArray() else null
            message = withContext(Dispatchers.IO) {
                try {
                    val out = context.contentResolver.openOutputStream(uri) ?: error("Cannot write to this location.")
                    val n = backup.create(out, APP_VERSION, password)
                    "Backup created with $n document(s)" + if (encrypt) " (encrypted)." else " (NOT encrypted)."
                } catch (e: Exception) { "Backup failed: ${e.message}" }
            }
            busy = false
        }
    }

    fun runRestore(file: File, password: CharArray?) {
        scope.launch {
            busy = true
            message = withContext(Dispatchers.IO) {
                try {
                    "Restored ${backup.restoreAny(file, password, tempDir)} new document(s). Existing items were kept."
                } catch (e: IllegalArgumentException) { e.message ?: "Invalid backup." }
                catch (e: Exception) { "Restore failed: ${e.message}" }
            }
            if (!BackupCrypto.isEncrypted(file) || message?.startsWith("Restored") == true) file.delete()
            busy = false
        }
    }

    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val copied = withContext(Dispatchers.IO) {
                tempDir.mkdirs()
                val tmp = File(tempDir, "picked.bak")
                try { context.contentResolver.copyUriTo(uri, tmp); tmp } catch (e: Exception) { message = "Could not read the file: ${e.message}"; null }
            }
            busy = false
            if (copied != null) {
                if (BackupCrypto.isEncrypted(copied)) { restorePw = ""; pendingRestore = copied }
                else runRestore(copied, null)
            }
        }
    }
    InfoPage("Backup & Restore", onBack) {
        NxStatusBanner("A backup contains your Files documents, folders, notes, flashcards and question sets. It does NOT contain AI models, OCR language data or app settings. Choose where it is saved; nothing is uploaded.", NxBannerKind.INFO)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = encrypt, onCheckedChange = { encrypt = it }, colors = SwitchDefaults.colors(checkedTrackColor = Nx.Purple))
            Text("  Encrypt with a password (AES-256-GCM)", color = Nx.TextMain, fontSize = 14.sp)
        }
        if (encrypt) {
            NxTextField(pw1, { pw1 = it }, "Password (min. ${BackupCrypto.MIN_PASSWORD_LENGTH} characters)", Modifier.fillMaxWidth())
            NxTextField(pw2, { pw2 = it }, "Repeat password", Modifier.fillMaxWidth())
            NxStatusBanner("There is no way to recover a forgotten password. Wrong passwords and damaged files are reported the same way.", NxBannerKind.WARNING)
        } else {
            NxStatusBanner("Without a password the backup is a plain ZIP that anyone can read.", NxBannerKind.WARNING)
        }
        NexVoraButton("Create backup…", { create.launch(if (encrypt) "nexvora-docai-backup.nxbackup" else "nexvora-docai-backup.zip") }, Modifier.fillMaxWidth(), enabled = !busy && passwordOk)
        NexVoraButton("Restore from backup…", { restore.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = !busy)
        Text("Restore only adds items that are not already present; nothing is overwritten or deleted.", color = Nx.TextSecondary, fontSize = 12.sp)
        message?.let { NxStatusBanner(it) }
    }
    pendingRestore?.let { file ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { file.delete(); pendingRestore = null },
            containerColor = Nx.Card, titleContentColor = Nx.TextMain,
            title = { Text("Encrypted backup") },
            text = { NxTextField(restorePw, { restorePw = it }, "Password", Modifier.fillMaxWidth()) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { pendingRestore = null; runRestore(file, restorePw.toCharArray()) }, enabled = restorePw.isNotEmpty()) {
                    Text("Restore", color = Nx.Cyan)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { file.delete(); pendingRestore = null }) { Text("Cancel", color = Nx.TextSecondary) } },
        )
    }
}

@Composable
fun StorageScreen(vault: com.nexvora.docai.feature.vault.VaultRepository, models: com.nexvora.docai.feature.models.ModelManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val tmpDir = File(context.cacheDir, "nexvora")
    var tmpBytes by remember { mutableStateOf(tmpDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) }
    var message by remember { mutableStateOf<String?>(null) }
    InfoPage("Storage & cache", onBack) {
        val fmt: (Long) -> String = ::formatBytes
        NexVoraCard {
            Text("Documents in Files: ${fmt(vault.totalBytes())}", color = Nx.TextMain, fontSize = 14.sp)
            Text("AI / translation models: ${fmt(models.usedBytes())}", color = Nx.TextMain, fontSize = 14.sp)
            Text("Temporary files (cache): ${fmt(tmpBytes)}", color = Nx.TextMain, fontSize = 14.sp)
            Text("Free on device: ${fmt(context.filesDir.usableSpace)}", color = Nx.TextSecondary, fontSize = 13.sp)
        }
        NexVoraButton("Clear temporary files", {
            tmpDir.deleteRecursively()
            tmpBytes = 0
            message = "Temporary files cleared. Your documents, notes and models were not touched."
        }, Modifier.fillMaxWidth(), destructive = true)
        message?.let { NxStatusBanner(it, NxBannerKind.SUCCESS) }
        NxStatusBanner("Clearing the cache does not delete documents. Delete documents in Files and models in Model Manager.")
    }
}
