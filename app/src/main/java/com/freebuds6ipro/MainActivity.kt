package com.freebuds6ipro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.freebuds6ipro.ui.App
import com.freebuds6ipro.ui.tr

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF80CBC4)) else lightColorScheme(primary = Color(0xFF00695C))) {
                Surface {
                    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
                    val micL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                        if (ok) pending.value?.invoke() else vm.message = tr("إذن الميكروفون مطلوب للتسجيل", "Microphone permission is required")
                        pending.value = null
                    }
                    val btL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.bt.start() }
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) btL.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        else vm.bt.start()
                    }
                    App(vm) { action ->
                        if (granted(Manifest.permission.RECORD_AUDIO)) action()
                        else { pending.value = action; micL.launch(Manifest.permission.RECORD_AUDIO) }
                    }
                }
            }
        }
    }
}
