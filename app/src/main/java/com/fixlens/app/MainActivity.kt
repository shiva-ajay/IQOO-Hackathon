package com.fixlens.app

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixlens.ui.CameraScreen
import com.fixlens.ui.SessionsScreen
import com.fixlens.R

// FixLens palette, see design/logo/README.md.
private val Ink = Color(0xFF0F1C2E)
private val Amber = Color(0xFFFF9F1C)
private val Paper = Color(0xFFF6F1E7)

private val FixLensColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
)

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = FixLensColors) {
                var granted by remember { mutableStateOf(allGranted()) }
                if (granted) {
                    AppRoot(viewModel())
                } else {
                    Scaffold { padding ->
                        HomeScreen(Modifier.padding(padding), onAllGranted = { granted = true })
                    }
                }
            }
        }
    }

    /**
     * Debug builds only: type a question instead of speaking it, for testing over adb:
     * `adb shell am start -n com.fixlens/.app.MainActivity --es ask "what is this?"` (also `--ez new true`, `--ez mic false`).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val vm = ViewModelProvider(this)[FixLensViewModel::class.java]
        if (intent.getBooleanExtra("new", false)) vm.newSession()
        if (intent.hasExtra("mic") && intent.getBooleanExtra("mic", true) != vm.state.value.micOn) vm.toggleMic()
        intent.getStringExtra("ask")?.let(vm::debugAsk)
    }

    private fun allGranted() = REQUIRED_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}

@Composable
private fun AppRoot(vm: FixLensViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = state.screen,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.Sessions -> SessionsScreen(
                state = state,
                sessionDir = vm::sessionDir,
                onNew = vm::newSession,
                onOpen = vm::openSession,
                onRename = vm::renameSession,
                onDelete = vm::deleteSession,
            )
            Screen.Session -> {
                BackHandler(onBack = vm::closeSession)
                CameraScreen(vm, onBack = vm::closeSession)
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier, onAllGranted: () -> Unit) {
    val context = LocalContext.current
    fun currentGrants() = REQUIRED_PERMISSIONS.associateWith {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    var grants by remember { mutableStateOf(currentGrants()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.i(TAG, "Permission result: $result")
        grants = currentGrants()
        if (grants.values.all { it }) onAllGranted()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.fixlens_logo_dark),
            contentDescription = "FixLens",
        )
        Spacer(Modifier.height(12.dp))
        Text("Offline repair assistant", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { launcher.launch(REQUIRED_PERMISSIONS) }) {
            Text("Grant camera + microphone")
        }
        Spacer(Modifier.height(16.dp))
        Text("Camera: ${if (grants[Manifest.permission.CAMERA] == true) "granted" else "not granted"}")
        Text("Microphone: ${if (grants[Manifest.permission.RECORD_AUDIO] == true) "granted" else "not granted"}")
    }
}
