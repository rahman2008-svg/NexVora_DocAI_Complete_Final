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

import com.nexvora.docai.core.design.NxTab

/** Destinations of the NexVora shell. [Scanner] hands control to the upstream FairScan scanner flow. */
enum class NxRoute {
    Splash, Home, ScanHub, Vault, Settings, Assistant, Ocr, PdfStudio, Study, Translation,
    ModelManager, Privacy, About, Backup, OcrLanguages, ScannerSettings, Libraries, Storage, Scanner;

    val tab: NxTab?
        get() = when (this) {
            Home -> NxTab.HOME
            ScanHub, Ocr -> NxTab.SCAN
            Vault -> NxTab.FILES
            Settings, ModelManager, Privacy, About, Backup, OcrLanguages, ScannerSettings, Libraries, Storage -> NxTab.SETTINGS
            else -> null
        }
}

/** Immutable back stack for the shell (pure Kotlin so it can be unit-tested). */
data class ShellStack(val routes: List<NxRoute>) {
    init { require(routes.isNotEmpty()) }

    val current: NxRoute get() = routes.last()

    fun push(route: NxRoute): ShellStack = if (route == current) this else copy(routes = routes + route)

    /** Bottom-bar tab selection: Home alone, other tabs sit on top of Home. */
    fun selectTab(route: NxRoute): ShellStack =
        ShellStack(if (route == NxRoute.Home) listOf(NxRoute.Home) else listOf(NxRoute.Home, route))

    /** Returns null when there is nothing to go back to (caller should let the system handle back). */
    fun pop(): ShellStack? = if (routes.size <= 1) null else copy(routes = routes.dropLast(1))

    fun serialize(): String = routes.joinToString(",") { it.name }

    companion object {
        fun initial(skipSplash: Boolean = false, scannerFirst: Boolean = false) = when {
            scannerFirst -> ShellStack(listOf(NxRoute.Scanner))
            skipSplash -> ShellStack(listOf(NxRoute.Home))
            else -> ShellStack(listOf(NxRoute.Splash))
        }

        fun parse(text: String): ShellStack {
            val routes = text.split(',').mapNotNull { name -> NxRoute.entries.firstOrNull { it.name == name } }
            return if (routes.isEmpty()) ShellStack(listOf(NxRoute.Home)) else ShellStack(routes)
        }
    }
}
