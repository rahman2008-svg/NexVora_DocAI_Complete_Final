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
package com.nexvora.docai.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexvora.docai.core.NxServices
import com.nexvora.docai.core.design.NexVoraBackground
import com.nexvora.docai.core.design.NexVoraBottomNavigation
import com.nexvora.docai.core.design.NxTab
import com.nexvora.docai.feature.assistant.AssistantScreen
import com.nexvora.docai.feature.home.HomeActions
import com.nexvora.docai.feature.home.HomeScreen
import com.nexvora.docai.feature.home.ScanHubScreen
import com.nexvora.docai.feature.home.SplashScreen
import com.nexvora.docai.feature.models.ModelManagerScreen
import com.nexvora.docai.feature.ocr.OcrScreen
import com.nexvora.docai.feature.pdf.PdfStudioScreen
import com.nexvora.docai.feature.pdf.PdfViewerScreen
import com.nexvora.docai.feature.settings.AboutScreen
import com.nexvora.docai.feature.settings.AppLock
import com.nexvora.docai.feature.settings.BackupScreen
import com.nexvora.docai.feature.settings.PrivacyScreen
import com.nexvora.docai.feature.settings.SettingsActions
import com.nexvora.docai.feature.settings.SettingsScreen
import com.nexvora.docai.feature.study.StudyScreen
import com.nexvora.docai.feature.translation.TranslationScreen
import com.nexvora.docai.feature.vault.VaultScreen
import java.io.File

private val ShellStackSaver = Saver<ShellStack, String>(save = { it.serialize() }, restore = { ShellStack.parse(it) })

private data class ViewerTarget(val file: File, val title: String, val page: Int)

/**
 * NexVora shell: splash, dashboard, bottom navigation and all NexVora features.
 * The upstream FairScan scanner flow is hosted unchanged through [scannerContent].
 */
@Composable
fun NexVoraShell(
    services: NxServices,
    lock: AppLock,
    startInScanner: Boolean,
    scannerContent: @Composable (exitScanner: () -> Unit) -> Unit,
    ocrLanguagesContent: @Composable (onBack: () -> Unit) -> Unit,
    scannerSettingsContent: @Composable (onBack: () -> Unit) -> Unit,
    librariesContent: @Composable (onBack: () -> Unit) -> Unit,
) {
    var stack by rememberSaveable(stateSaver = ShellStackSaver) { mutableStateOf(ShellStack.initial(scannerFirst = startInScanner)) }
    var viewer by remember { mutableStateOf<ViewerTarget?>(null) }
    val route = stack.current
    val vault = services.vault

    fun go(r: NxRoute) { stack = stack.push(r) }
    fun back() { stack = stack.pop() ?: ShellStack(listOf(NxRoute.Home)) }

    BackHandler(enabled = viewer != null) { viewer = null }
    BackHandler(enabled = viewer == null && route != NxRoute.Scanner && route != NxRoute.Splash && stack.routes.size > 1) { back() }

    if (route == NxRoute.Scanner) {
        scannerContent { if (startInScanner) Unit else back() }
        return
    }

    NexVoraBackground {
        viewer?.let { v ->
            PdfViewerScreen(v.file, v.title, onClose = { viewer = null }, initialPage = v.page)
            return@NexVoraBackground
        }
        if (route == NxRoute.Splash) {
            SplashScreen(vaultReady = { true }, scannerReady = services.scannerReady) { stack = ShellStack(listOf(NxRoute.Home)) }
            return@NexVoraBackground
        }
        val showBottom = route.tab != null && route in setOf(NxRoute.Home, NxRoute.ScanHub, NxRoute.Vault, NxRoute.Settings)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).let { if (showBottom) it else it.navigationBarsPadding() }) {
                when (route) {
                    NxRoute.Home -> HomeScreen(
                        services.prefs.displayName,
                        HomeActions(
                            onScan = { go(NxRoute.Scanner) }, onAssistant = { go(NxRoute.Assistant) }, onOcr = { go(NxRoute.Ocr) },
                            onPdfStudio = { go(NxRoute.PdfStudio) }, onVault = { go(NxRoute.Vault) }, onStudy = { go(NxRoute.Study) },
                            onTranslation = { go(NxRoute.Translation) },
                        ),
                    )
                    NxRoute.ScanHub -> ScanHubScreen({ go(NxRoute.Scanner) }, { go(NxRoute.Ocr) }, { go(NxRoute.PdfStudio) })
                    NxRoute.Vault -> VaultScreen(services.vaultController, onBack = { back() }, onOpenPdf = { d -> viewer = ViewerTarget(vault.file(d), d.fileName, 0) })
                    NxRoute.Settings -> SettingsScreen(
                        services.prefs, lock,
                        SettingsActions(
                            onModels = { go(NxRoute.ModelManager) }, onOcrLanguages = { go(NxRoute.OcrLanguages) },
                            onScannerSettings = { go(NxRoute.ScannerSettings) }, onPrivacy = { go(NxRoute.Privacy) },
                            onBackup = { go(NxRoute.Backup) }, onStorage = { go(NxRoute.Storage) }, onLibraries = { go(NxRoute.Libraries) }, onAbout = { go(NxRoute.About) },
                        ),
                    )
                    NxRoute.Assistant -> AssistantScreen(
                        services.assistant, vault, onBack = { back() }, onOpenModels = { go(NxRoute.ModelManager) },
                        onOpenPage = { d, p -> viewer = ViewerTarget(vault.file(d), d.fileName, p) },
                    )
                    NxRoute.Ocr -> OcrScreen(services.ocr, vault, onBack = { back() }, onManageLanguages = { go(NxRoute.OcrLanguages) })
                    NxRoute.PdfStudio -> PdfStudioScreen(services.pdf, vault, onBack = { back() }, onOpenPdf = { d -> viewer = ViewerTarget(vault.file(d), d.fileName, 0) }, onPreview = { f, n -> viewer = ViewerTarget(f, n, 0) })
                    NxRoute.Study -> StudyScreen(services.study, vault, services.extractor, services.scope, services.llm, services.llmRuntime.contextLength, onBack = { back() }, onOpenAssistant = { go(NxRoute.Assistant) })
                    NxRoute.Translation -> TranslationScreen(services.translationEngine, onBack = { back() }, onOpenModels = { go(NxRoute.ModelManager) })
                    NxRoute.ModelManager -> ModelManagerScreen(services.models, services.llmRuntime, onBack = { back() })
                    NxRoute.Privacy -> PrivacyScreen(onBack = { back() })
                    NxRoute.Storage -> com.nexvora.docai.feature.settings.StorageScreen(vault, services.models, onBack = { back() })
                    NxRoute.About -> AboutScreen(onBack = { back() }, onLibraries = { go(NxRoute.Libraries) })
                    NxRoute.Backup -> BackupScreen(services.backup, services.scope, onBack = { back() })
                    NxRoute.OcrLanguages -> ocrLanguagesContent { back() }
                    NxRoute.ScannerSettings -> scannerSettingsContent { back() }
                    NxRoute.Libraries -> librariesContent { back() }
                    NxRoute.Splash, NxRoute.Scanner -> {}
                }
            }
            if (showBottom) {
                NexVoraBottomNavigation(
                    selected = route.tab,
                    onSelect = { tab ->
                        stack = stack.selectTab(
                            when (tab) {
                                NxTab.HOME -> NxRoute.Home
                                NxTab.SCAN -> NxRoute.ScanHub
                                NxTab.FILES -> NxRoute.Vault
                                NxTab.SETTINGS -> NxRoute.Settings
                            }
                        )
                    },
                    onCamera = { go(NxRoute.Scanner) },
                )
            }
        }
    }
}
