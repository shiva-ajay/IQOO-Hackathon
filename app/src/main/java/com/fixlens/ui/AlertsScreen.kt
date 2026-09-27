package com.fixlens.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fixlens.alerts.Alert
import com.fixlens.alerts.AlertScheduler
import com.fixlens.alerts.Alerts
import com.fixlens.app.FixLensViewModel
import com.fixlens.app.UiState
import com.fixlens.ui.fx.OrbState
import com.fixlens.ui.fx.ThinkingOrb
import com.fixlens.ui.remote.PrimaryButton
import com.fixlens.ui.remote.SecondaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The reminders Fixy scheduled by itself after routine checks (the KB says how often). Due ones first, with
 * "Start the check"; then the upcoming ones, which can be cancelled or fired early to show the notification.
 */
@Composable
fun AlertsScreen(viewModel: FixLensViewModel, state: UiState, onMenu: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(AlertScheduler.notificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = AlertScheduler.notificationsAllowed(context)
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = AlertScheduler.notificationsAllowed(context)
    }
    val allow: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !allowed &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Permission granted but switched off (or refused twice): only the settings page can turn it on.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    val due = state.alerts.filter { it.delivered }
    val upcoming = state.alerts.filterNot { it.delivered }
    val list = rememberLazyListState()
    // A notification tap lands on its alert.
    LaunchedEffect(state.focusAlert, state.alerts.size) {
        val id = state.focusAlert ?: return@LaunchedEffect
        val index = Alerts.ordered(state.alerts).indexOfFirst { it.id == id }
        // Header, then "DUE NOW" and its cards, then "SCHEDULED BY FIXY" and its cards.
        if (index >= 0) list.animateScrollToItem(2 + index + if (index >= due.size && due.isNotEmpty()) 1 else 0)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Ink),
        state = list,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "Alerts",
                subtitle = "Checks Fixy scheduled after your repairs, as often as the makers' manuals say.",
                onMenu = onMenu,
                menuDot = false,
            )
            if (!allowed) {
                Spacer(Modifier.height(20.dp))
                NotificationsOff(allow)
            }
            if (state.alerts.isEmpty()) EmptyAlerts()
        }
        if (due.isNotEmpty()) {
            item { SectionLabel("DUE NOW", Modifier.padding(top = 16.dp)) }
            items(due, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    focused = alert.id == state.focusAlert,
                    onStart = { viewModel.startAlertCheck(alert.id) },
                    onDismiss = { viewModel.dismissAlert(alert.id) },
                    onTest = null,
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionLabel("SCHEDULED BY FIXY", Modifier.padding(top = 16.dp)) }
            items(upcoming, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    focused = alert.id == state.focusAlert,
                    onStart = { viewModel.startAlertCheck(alert.id) },
                    onDismiss = { viewModel.dismissAlert(alert.id) },
                    onTest = { viewModel.testAlert(alert.id) },
                )
            }
        }
    }
}

@Composable
private fun AlertCard(alert: Alert, focused: Boolean, onStart: () -> Unit, onDismiss: () -> Unit, onTest: (() -> Unit)?) {
    val now = System.currentTimeMillis()
    Column(Modifier.homeCard(highlight = alert.delivered || focused).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Amber.copy(alpha = if (alert.delivered) 0.2f else 0.1f)),
                contentAlignment = Alignment.Center,
            ) { BellGlyph(Amber, Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.title, color = Paper, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Text(
                    if (alert.delivered) "Due since ${day(alert.dueAt)}" else "${relative(alert.dueAt, now)} · ${day(alert.dueAt)}",
                    color = if (alert.delivered) Amber else Muted,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(alert.body, color = Paper.copy(alpha = 0.9f), style = QuestionStyle)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingOrb(state = OrbState.Idle, size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Scheduled by Fixy ${day(alert.createdAt)}" +
                    (alert.sessionTitle?.let { " after “$it”" } ?: "") +
                    " · ${Alerts.everyInterval(alert.afterDays)}",
                color = Muted,
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
            )
        }
        Spacer(Modifier.height(14.dp))
        if (alert.delivered) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Dismiss", onDismiss, Modifier.weight(1f))
                PrimaryButton("Start the check", onStart, Modifier.weight(1f))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallAction("Start now", onStart)
                onTest?.let { SmallAction("Test in 5 s", it) }
                Spacer(Modifier.weight(1f))
                SmallAction("Cancel", onDismiss, color = Muted)
            }
        }
    }
}

@Composable
private fun NotificationsOff(onAllow: () -> Unit) {
    Row(Modifier.homeCard(highlight = true).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Notifications are off", color = Paper, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text("Reminders still show here, but can't pop up on your phone.", color = Muted, style = TextStyle(fontSize = 13.sp))
        }
        Spacer(Modifier.width(12.dp))
        PrimaryButton("Allow", onAllow)
    }
}

@Composable
private fun EmptyAlerts() {
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Paper.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
            BellGlyph(Paper.copy(alpha = 0.7f), Modifier.size(30.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No alerts yet", color = Paper, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Text(
            "Finish a routine check with Fixy, like the engine oil or the AC filters,\nand Fixy schedules the next one here.",
            color = Muted,
            style = QuestionStyle.copy(fontSize = 14.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color = Amber) {
    Text(
        text,
        color = color,
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private fun day(millis: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(millis))

/** "In 7 days", "Tomorrow", "In 5 seconds". */
private fun relative(millis: Long, now: Long): String =
    if (millis - now < DateUtils.MINUTE_IN_MILLIS) "In a few seconds"
    else DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
