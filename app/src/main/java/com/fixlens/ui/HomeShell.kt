package com.fixlens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixlens.R
import com.fixlens.app.FixLensViewModel
import com.fixlens.app.HomePage
import com.fixlens.app.UiState
import com.fixlens.ui.remote.MenuGlyph
import com.fixlens.ui.remote.RemoteGlyph
import kotlinx.coroutines.launch

/**
 * Home: a side drawer over three pages. Repairs (the saved sessions), the universal remote (saved IR remotes,
 * pairing, the pad) and the alerts Fixy scheduled. The models keep loading in the background meanwhile.
 */
@Composable
fun HomeShell(viewModel: FixLensViewModel, state: UiState) {
    val remote by viewModel.remoteUi.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openMenu: () -> Unit = { scope.launch { drawer.open() } }
    val due = state.alerts.count { it.delivered }

    // Registered first, so a page's own layers (the brand sheet, the pad) and the open drawer go back before it.
    BackHandler(enabled = state.home != HomePage.Repairs) { viewModel.showHome(HomePage.Repairs) }

    ModalNavigationDrawer(
        drawerState = drawer,
        // Swiping only closes it: an edge swipe on the pad or the brand list must never pull the drawer out.
        gesturesEnabled = drawer.isOpen,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Ink,
                drawerContentColor = Paper,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier.width(304.dp),
            ) {
                Column(Modifier.fillMaxHeight().padding(horizontal = 14.dp)) {
                    Spacer(Modifier.height(22.dp))
                    Image(
                        painter = painterResource(R.drawable.fixlens_logo_dark),
                        contentDescription = "FixLens",
                        modifier = Modifier.padding(start = 12.dp).height(26.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                    fun pick(page: HomePage) {
                        scope.launch { drawer.close() }
                        viewModel.showHome(page)
                    }
                    DrawerItem(
                        title = "Repairs",
                        detail = when (val n = state.sessions.size) {
                            0 -> "Start one with Fixy"
                            1 -> "1 saved repair"
                            else -> "$n saved repairs"
                        },
                        selected = state.home == HomePage.Repairs,
                        onClick = { pick(HomePage.Repairs) },
                    ) { WrenchGlyph(it, Modifier.size(22.dp)) }
                    DrawerItem(
                        title = "Universal remote",
                        detail = when (val n = remote.saved.size) {
                            0 -> "AC, TV, projector or fan"
                            1 -> "1 saved remote"
                            else -> "$n saved remotes"
                        },
                        selected = state.home == HomePage.Remote,
                        onClick = { pick(HomePage.Remote) },
                    ) { RemoteGlyph(it, Modifier.size(22.dp), restAlpha = 0.75f) }
                    val upcoming = state.alerts.size - due
                    DrawerItem(
                        title = "Alerts",
                        detail = when {
                            due > 0 -> "$due due now"
                            upcoming == 1 -> "1 scheduled by Fixy"
                            upcoming > 1 -> "$upcoming scheduled by Fixy"
                            else -> "Reminders Fixy schedules"
                        },
                        selected = state.home == HomePage.Alerts,
                        badge = due.takeIf { it > 0 },
                        onClick = { pick(HomePage.Alerts) },
                    ) { BellGlyph(it, Modifier.size(22.dp)) }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.padding(start = 12.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Online))
                        Spacer(Modifier.width(8.dp))
                        Text("Offline · everything stays on this phone", color = Muted, style = TextStyle(fontSize = 12.sp))
                    }
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = state.home,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "home",
        ) { page ->
            when (page) {
                HomePage.Repairs -> SessionsScreen(
                    state = state,
                    sessionDir = viewModel::sessionDir,
                    onNew = viewModel::newSession,
                    onOpen = viewModel::openSession,
                    onRename = viewModel::renameSession,
                    onDelete = viewModel::deleteSession,
                    onMenu = openMenu,
                    menuDot = due > 0,
                    onStartAlert = viewModel::startAlertCheck,
                )
                HomePage.Remote -> RemoteHome(viewModel, state, remote, onMenu = openMenu)
                HomePage.Alerts -> AlertsScreen(viewModel, state, onMenu = openMenu)
            }
        }
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
}

@Composable
private fun DrawerItem(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: Int? = null,
    icon: @Composable (Color) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (selected) Amber.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon(if (selected) Amber else Paper) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            Text(detail, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp))
        }
        if (badge != null) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(Amber),
                contentAlignment = Alignment.Center,
            ) { Text("$badge", color = Ink, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
        }
    }
}

/** The round menu button at the top left of every home page; [dot]: an alert is due. */
@Composable
internal fun MenuButton(onClick: () -> Unit, dot: Boolean = false) {
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Paper.copy(alpha = 0.06f))
                .border(1.dp, Hairline, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (dot) "Open menu, an alert is due" else "Open menu" },
            contentAlignment = Alignment.Center,
        ) { MenuGlyph(Paper, Modifier.size(20.dp)) }
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(9.dp).clip(CircleShape).background(Amber))
    }
}

/** A home page's top: the menu button, then a large title and a line under it. */
@Composable
internal fun PageHeader(title: String, subtitle: String, onMenu: () -> Unit, menuDot: Boolean = false) {
    Column(Modifier.statusBarsPadding()) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MenuButton(onMenu, menuDot)
        }
        Spacer(Modifier.height(28.dp))
        Text(title, color = Paper, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp))
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Muted, style = QuestionStyle.copy(fontSize = 14.sp))
    }
}

/** A small uppercase section label ("SAVED REMOTES"). */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, style = LabelStyle.copy(fontSize = 11.sp), modifier = modifier)
}

@Composable
internal fun BellGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.085f
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.2f, w * 0.72f)
            cubicTo(w * 0.28f, w * 0.62f, w * 0.27f, w * 0.52f, w * 0.27f, w * 0.44f)
            cubicTo(w * 0.27f, w * 0.28f, w * 0.37f, w * 0.18f, w * 0.5f, w * 0.18f)
            cubicTo(w * 0.63f, w * 0.18f, w * 0.73f, w * 0.28f, w * 0.73f, w * 0.44f)
            cubicTo(w * 0.73f, w * 0.52f, w * 0.72f, w * 0.62f, w * 0.8f, w * 0.72f)
            close()
        }
        drawPath(body, color, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawLine(color, Offset(w * 0.5f, w * 0.1f), Offset(w * 0.5f, w * 0.18f), stroke, StrokeCap.Round)
        drawArc(color, 20f, 140f, false, Offset(w * 0.4f, w * 0.72f), Size(w * 0.2f, w * 0.16f), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
internal fun WrenchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.11f
        // Handle, lower left to the head.
        drawLine(color, Offset(w * 0.2f, w * 0.8f), Offset(w * 0.55f, w * 0.45f), stroke, StrokeCap.Round)
        // Open-ended head: an arc with its jaw facing up-right.
        drawArc(
            color, 90f, 270f, false, Offset(w * 0.48f, w * 0.14f), Size(w * 0.38f, w * 0.38f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** A thin chevron pointing right, for rows that open something. */
@Composable
internal fun ChevronGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.12f
        drawLine(color, Offset(w * 0.38f, w * 0.22f), Offset(w * 0.66f, w * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.66f, w * 0.5f), Offset(w * 0.38f, w * 0.78f), stroke, StrokeCap.Round)
    }
}

internal val HomeCardShape = RoundedCornerShape(20.dp)

/** The raised card used across the home pages. */
internal fun Modifier.homeCard(highlight: Boolean = false): Modifier = this
    .fillMaxWidth()
    .clip(HomeCardShape)
    .background(Paper.copy(alpha = if (highlight) 0.07f else 0.04f))
    .border(1.dp, if (highlight) Amber.copy(alpha = 0.55f) else Hairline, HomeCardShape)

internal val SmallGap = Arrangement.spacedBy(10.dp)
