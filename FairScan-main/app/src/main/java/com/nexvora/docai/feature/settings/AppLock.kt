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

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.docai.core.design.NexVoraBackground
import com.nexvora.docai.core.design.NexVoraGradientButton
import com.nexvora.docai.core.design.NexVoraLogo
import com.nexvora.docai.core.design.Nx

/**
 * App Lock uses the Android system authentication UI: biometrics or the device PIN/pattern/password
 * (BiometricPrompt on Android 11+, the system confirm-credential screen on Android 8–10).
 * It gates access to the UI only; stored files are NOT encrypted by this feature.
 */
class AppLock(private val activity: ComponentActivity, private val prefs: NxPrefs) {
    var locked by mutableStateOf(prefs.appLockEnabled)
        private set
    lateinit var credentialLauncher: ActivityResultLauncher<Intent>
    private var pending: ((Boolean, String?) -> Unit)? = null
    private var stoppedAt = 0L
    private var prompting = false

    fun isDeviceSecure(): Boolean =
        (activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    fun onStopped() { stoppedAt = System.currentTimeMillis() }

    fun onStarted() {
        if (prefs.appLockEnabled && !locked && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > RELOCK_AFTER_MS) locked = true
    }

    fun unlockedByUser() { locked = false }

    fun disableLockState() { locked = false }

    fun authenticate(title: String, onResult: (ok: Boolean, error: String?) -> Unit) {
        if (prompting) return
        if (!isDeviceSecure()) { onResult(false, "Set up a screen lock (PIN, pattern, password or biometrics) in Android settings first."); return }
        prompting = true
        val done: (Boolean, String?) -> Unit = { ok, err -> prompting = false; onResult(ok, err) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) promptApi30(title, done)
        else {
            val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            @Suppress("DEPRECATION")
            val intent = km.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) done(false, "Authentication is not available on this device.")
            else { pending = done; credentialLauncher.launch(intent) }
        }
    }

    fun onCredentialResult(ok: Boolean) {
        pending?.invoke(ok, if (ok) null else "Authentication was cancelled.")
        pending = null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun promptApi30(title: String, done: (Boolean, String?) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = done(true, null)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = done(false, errString?.toString())
        })
    }

    companion object { const val RELOCK_AFTER_MS = 30_000L }
}

@Composable
fun LockScreen(lock: AppLock) {
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    fun unlock() = lock.authenticate("Unlock NexVora DocAI") { ok, err -> if (ok) lock.unlockedByUser() else error = err }
    LaunchedEffect(Unit) { unlock() }
    NexVoraBackground {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            NexVoraLogo(96.dp)
            Spacer(Modifier.height(16.dp))
            Text("NexVora DocAI is locked", color = Nx.TextMain, fontSize = 20.sp)
            error?.let { Text(it, color = Nx.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(24.dp))
            NexVoraGradientButton("Unlock", { unlock() })
        }
    }
}
