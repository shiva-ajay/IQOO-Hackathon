package com.fixlens.app

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
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
import androidx.compose.runtime.LaunchedEffect
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
import com.fixlens.alerts.AlertScheduler
import com.fixlens.ui.HomeShell
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
        if (savedInstanceState == null) openAlert(intent)
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
     * `adb shell am start -n com.fixlens/.app.MainActivity --es ask "what is this?"` (also `--ez new true`).
     * Marker testing (docs/marker-tracking.md): `--es ask "point: <phrase>"` forces what to point at,
     * `--es image /sdcard/.../x.jpg` uses a file instead of the camera, `--ez testbox true` and `--ez freeze true`
     * draw the mapping checks, `--es grounding contract|native` and `--es coords norm|px` switch prompt and scale.
     * Voice: `--es say "<text>"` speaks a line (timings in the log); `--ef ttsspeed F` changes its speed.
     * IR remote (docs/ir-remote-plan.md §11): `--ez irinfo true` logs the hardware; `--ez irfake true` pretends to
     * send; `--es ir "tv/LG/mute"` or `"ac/LG/cool 24"` sends one code; `--es irpair "tv:LG"` opens pairing;
     * `--es irtake "take the remote"` runs a spoken remote request; `--es irpress vol_up` shows a key pop-up.
     * Alerts: `--es remind car_check_engine_oil --ei remindsec 5` schedules that entry's reminder 5 s from now
     * (keep it under 10 s: a longer lead gets the one-hour delivery window). `--es alerttitle "…" --es alertbody "…"`
     * give a demo alert its own words.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (openAlert(intent)) return
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val vm = ViewModelProvider(this)[FixLensViewModel::class.java]
        intent.getStringExtra("remind")?.let {
            vm.debugRemind(it, intent.getIntExtra("remindsec", 5), intent.getStringExtra("alerttitle"), intent.getStringExtra("alertbody"))
        }
        if (intent.getBooleanExtra("new", false)) vm.newSession()
        fun flag(name: String) = if (intent.hasExtra(name)) intent.getBooleanExtra(name, false) else null
        vm.debugSettings(flag("testbox"), flag("freeze"), intent.getStringExtra("grounding"), intent.getStringExtra("coords"))
        intent.getStringExtra("ask")?.let { vm.debugAsk(it, intent.getStringExtra("image")) }
        if (intent.hasExtra("ttsspeed")) vm.debugVoice(intent.getFloatExtra("ttsspeed", 1f))
        intent.getStringExtra("say")?.let(vm::debugSay)
        val irInfo = intent.getBooleanExtra("irinfo", false)
        val irFake = flag("irfake")
        val irSend = intent.getStringExtra("ir")
        val irPair = intent.getStringExtra("irpair")
        val irTake = intent.getStringExtra("irtake")
        val irPress = intent.getStringExtra("irpress")
        if (irInfo || irFake != null || irSend != null || irPair != null || irTake != null || irPress != null) {
            vm.debugRemote(irInfo, irFake, irSend, irPair, irTake, irPress)
        }
    }

    /**
     * While pairing waits for a response, volume up = "it responded" and volume down = "no" (one hand aims the
     * phone). Otherwise the keys change the volume as usual.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if ((up || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && allGranted()) {
            val vm = ViewModelProvider(this)[FixLensViewModel::class.java]
            if (vm.remoteVolumeKeyUsable(up)) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) vm.remoteVolumeKey(up)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // A reminder may have gone off while the app was in the background.
        ViewModelProvider(this)[FixLensViewModel::class.java].refreshAlerts()
    }

    /** A tap on one of Fixy's reminder notifications (alerts/AlertScheduler). True if [intent] was one. */
    private fun openAlert(intent: Intent?): Boolean {
        if (intent?.action != AlertScheduler.ACTION_OPEN) return false
        val id = intent.getStringExtra(AlertScheduler.EXTRA_ALERT) ?: return false
        ViewModelProvider(this)[FixLensViewModel::class.java].openAlert(id, intent.getBooleanExtra(AlertScheduler.EXTRA_START, false))
        return true
    }

    private fun allGranted() = REQUIRED_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}

@Composable
private fun AppRoot(vm: FixLensViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Fixy just scheduled a reminder: ask once for the notification permission it needs (Android 13+).
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.i(TAG, "Notification permission: $granted")
    }
    val context = LocalContext.current
    LaunchedEffect(state.askNotifications) {
        if (!state.askNotifications) return@LaunchedEffect
        vm.notificationsAsked()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    AnimatedContent(
        targetState = state.screen,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.Sessions -> HomeShell(vm, state)
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
