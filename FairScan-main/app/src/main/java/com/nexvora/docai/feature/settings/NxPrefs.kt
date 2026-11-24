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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small, non-sensitive preferences (display name, App Lock switch). */
class NxPrefs(context: Context) {
    private val sp = context.getSharedPreferences("nexvora_prefs", Context.MODE_PRIVATE)

    var displayName by mutableStateOf(sp.getString("display_name", "") ?: "")
        private set
    var appLockEnabled by mutableStateOf(sp.getBoolean("app_lock", false))
        private set

    fun updateDisplayName(value: String) { displayName = value.trim().take(40); sp.edit().putString("display_name", displayName).apply() }
    fun updateAppLock(enabled: Boolean) { appLockEnabled = enabled; sp.edit().putBoolean("app_lock", enabled).apply() }
}
