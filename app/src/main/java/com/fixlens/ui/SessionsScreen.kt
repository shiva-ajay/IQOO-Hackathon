package com.fixlens.ui

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.R
import com.fixlens.app.UiState
import com.fixlens.session.RepairSession
import com.fixlens.ui.fx.OrbState
import com.fixlens.ui.fx.ThinkingOrb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Home's first page: the saved repair sessions, and the way into a new one. The models load in the background
 * meanwhile. [onMenu] opens the side drawer (universal remote, alerts); a due reminder shows above the list.
 */
@Composable
fun SessionsScreen(
    state: UiState,
    sessionDir: (String) -> File,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMenu: () -> Unit,
    menuDot: Boolean,
    onStartAlert: (String) -> Unit,
) {
    var menuFor by remember { mutableStateOf<RepairSession?>(null) }
    var renaming by remember { mutableStateOf<RepairSession?>(null) }
    var deleting by remember { mutableStateOf<RepairSession?>(null) }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        MenuButton(onMenu, menuDot)
                        Spacer(Modifier.width(14.dp))
                        Image(
                            painter = painterResource(R.drawable.fixlens_logo_dark),
                            contentDescription = "FixLens",
                            modifier = Modifier.height(28.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        EngineChip(state)
                    }
                    Spacer(Modifier.height(36.dp))
                    Text(
                        "Your repairs",
                        color = Paper,
                        style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Fixy remembers each repair. Everything stays on this phone.",
                        color = Muted,
                        style = QuestionStyle.copy(fontSize = 14.sp),
                    )
                    Spacer(Modifier.height(24.dp))
                    NewRepairButton(onNew)
                    state.alerts.firstOrNull { it.delivered }?.let { alert ->
                        Spacer(Modifier.height(12.dp))
                        DueAlert(alert.title, alert.body) { onStartAlert(alert.id) }
                    }
                    if (state.sessions.isNotEmpty()) {
                        Spacer(Modifier.height(28.dp))
                        Text("RECENT", color = Muted, style = LabelStyle.copy(fontSize = 11.sp))
                    }
                }
            }
            if (state.sessionsLoaded && state.sessions.isEmpty()) {
                item { EmptyState() }
            }
            items(state.sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    thumbnail = session.turns.lastOrNull { it.keyframe != null }?.keyframe
                        ?.let { File(sessionDir(session.id), it) },
                    onClick = { onOpen(session.id) },
                    onLongClick = { menuFor = session },
                )
            }
        }
    }

    menuFor?.let { s ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = InkRaised,
            title = { Text(s.title, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text("${s.turns.size} questions · ${relativeTime(s.updated)}", color = Muted) },
            confirmButton = { TextButton(onClick = { renaming = s; menuFor = null }) { Text("Rename", color = Amber) } },
            dismissButton = { TextButton(onClick = { deleting = s; menuFor = null }) { Text("Delete", color = Paper) } },
        )
    }
    renaming?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = InkRaised,
            title = { Text("Rename repair", color = Paper) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Paper, unfocusedTextColor = Paper,
                        focusedBorderColor = Amber, unfocusedBorderColor = Hairline, cursorColor = Amber,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { onRename(s.id, text); renaming = null }) { Text("Save", color = Amber) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = Muted) } },
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = InkRaised,
            title = { Text("Delete this repair?", color = Paper) },
            text = { Text("\"${s.title}\" and its photos will be removed from this phone.", color = Muted) },
            confirmButton = { TextButton(onClick = { onDelete(s.id); deleting = null }) { Text("Delete", color = Amber) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Muted) } },
        )
    }
}

@Composable
private fun EngineChip(state: UiState) {
    val label = when {
        state.error != null -> "Setup needed"
        state.engineReady -> "Ready · on-device"
        else -> "Getting ready"
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Paper.copy(alpha = 0.06f))
            .border(1.dp, Hairline, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.engineReady || state.error != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (state.error != null) Amber else Online))
        } else {
            ThinkingOrb(state = OrbState.Thinking, size = 14.dp)
        }
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "engine",
        ) { Text(it, color = Paper, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)) }
    }
}

@Composable
private fun NewRepairButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Amber)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(16.dp)) {
                val s = size.width * 0.14f
                drawLine(Ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), s, StrokeCap.Round)
                drawLine(Ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), s, StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("New repair", color = Ink, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
            Text("Point the camera and ask Fixy", color = Ink.copy(alpha = 0.7f), style = TextStyle(fontSize = 13.sp))
        }
    }
}

/** A reminder Fixy scheduled that is due now: tap to start the check. */
@Composable
private fun DueAlert(title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .homeCard(highlight = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BellGlyph(Amber, Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Due now · $title", color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(body, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp))
        }
        Spacer(Modifier.width(8.dp))
        Text("Start", color = Amber, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(session: RepairSession, thumbnail: File?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val last = session.turns.lastOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Paper.copy(alpha = 0.04f))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(thumbnail)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = session.title,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "title",
            ) {
                Text(
                    it, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            if (last != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    last.answer, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
            Spacer(Modifier.height(6.dp))
            val count = session.turns.size
            Text(
                "$count ${if (count == 1) "question" else "questions"} · ${relativeTime(session.updated)}",
                color = Paper.copy(alpha = 0.38f),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun Thumbnail(file: File?) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = file?.takeIf { it.exists() }?.let {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(it.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
            }
        }
    }
    Box(
        Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)).background(InkRaised),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            ThinkingOrb(state = OrbState.Idle, size = 22.dp)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThinkingOrb(state = OrbState.Idle, size = 56.dp)
        Spacer(Modifier.height(20.dp))
        Text("No repairs yet", color = Paper, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Text(
            "Start a new repair, point the camera at what's broken\nand tell Fixy what's happening.",
            color = Muted,
            style = QuestionStyle.copy(fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        )
    }
}

private fun relativeTime(millis: Long): String =
    if (System.currentTimeMillis() - millis < DateUtils.MINUTE_IN_MILLIS) "Just now"
    else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
