package com.fixlens.ui.remote

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.ir.AcChange
import com.fixlens.ir.AcMode
import com.fixlens.ir.AcState
import com.fixlens.ir.BrandPicker
import com.fixlens.ir.Button
import com.fixlens.ir.DeviceKind
import com.fixlens.ir.RemoteCommand
import com.fixlens.ir.RemoteUi
import com.fixlens.ui.Amber
import com.fixlens.ui.Hairline
import com.fixlens.ui.Ink
import com.fixlens.ui.Muted
import com.fixlens.ui.Paper

private val SheetShape = RoundedCornerShape(24.dp)

@Composable
private fun SheetSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .background(Ink.copy(alpha = 0.95f))
            .border(1.dp, Hairline, SheetShape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) { content() }
}

/**
 * "Which brand is it?": device tabs, a search field, what the camera suggested, the brands people usually
 * have, then every brand in the catalog. Sits above the mic, so the brand can also be said aloud.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrandSheet(
    picker: BrandPicker,
    onKind: (DeviceKind) -> Unit,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember(picker.kind) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val matches = remember(query, picker.all) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) picker.all
        else picker.all.filter { it.lowercase().contains(q) }.sortedBy { if (it.lowercase().startsWith(q)) 0 else 1 }
    }
    fun pick(brand: String) {
        keyboard?.hide()
        onPick(brand)
    }
    SheetSurface(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Which brand is it?", color = Paper, style = SheetTitle, modifier = Modifier.weight(1f))
                IconButton("Close", onClose) { CloseGlyph(Muted, Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Segmented(
                options = DeviceKind.entries.map { it to kindLabel(it) },
                selected = picker.kind,
                onSelect = onKind,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Paper.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchGlyph(Muted, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search ${picker.all.size} brands", color = Muted, style = RowText)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(30) },
                        singleLine = true,
                        textStyle = RowText.copy(color = Paper),
                        cursorBrush = SolidColor(Amber),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { matches.firstOrNull()?.let(::pick) }),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search brands" },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                if (query.isBlank()) {
                    if (picker.suggestions.isNotEmpty()) {
                        item { SectionNote("Closest to what I read") }
                        items(picker.suggestions, key = { "s-$it" }) { BrandRow(it, highlight = true) { pick(it) } }
                    }
                    if (picker.featured.isNotEmpty()) {
                        item { SectionNote("Common brands") }
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                picker.featured.forEach { brand -> Chip(brand) { pick(brand) } }
                            }
                        }
                    }
                    item { SectionNote("All brands") }
                }
                items(matches, key = { "a-$it" }) { BrandRow(it) { pick(it) } }
                if (matches.isEmpty()) {
                    item { Text("No brand called \"$query\". Try another spelling.", color = Muted, style = RowText, modifier = Modifier.padding(vertical = 14.dp)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("You can also hold the mic and say the brand.", color = Muted, style = TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
private fun SectionNote(text: String) {
    Text(text, color = Muted, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun BrandRow(brand: String, highlight: Boolean = false, onClick: () -> Unit) {
    Text(
        brand,
        color = if (highlight) Amber else Paper,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = RowText.copy(fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
    )
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Paper,
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Paper.copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Paper.copy(alpha = 0.05f))
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                color = if (on) Paper else Muted,
                maxLines = 1,
                style = TextStyle(fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Paper.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(role = Role.Tab, onClick = { onSelect(value) })
                    .semantics { this.selected = on }
                    .padding(vertical = 9.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun kindLabel(kind: DeviceKind) = when (kind) {
    DeviceKind.Ac -> "AC"
    DeviceKind.Tv -> "TV"
    DeviceKind.Projector -> "Projector"
    DeviceKind.Fan -> "Fan"
}

/**
 * The paired device's remote, laid out like the real one. AC: the last sent state (IR is one-way, so it's
 * labelled "last sent", never "current"), temperature, mode, fan, swing, power. TV, projector and fan: their keys.
 */
@Composable
fun RemotePad(ui: RemoteUi, onCommand: (RemoteCommand) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val profile = ui.profile ?: return
    SheetSurface(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteGlyph(Amber, Modifier.size(22.dp), restAlpha = 0.75f)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.label, color = Paper, style = SheetTitle)
                    Text(ui.lastSent?.let { "Last sent: $it" } ?: "Nothing sent yet", color = Muted, style = TextStyle(fontSize = 12.sp))
                }
                IconButton("Close remote pad", onClose) { CloseGlyph(Muted, Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            when (profile.kind) {
                DeviceKind.Ac -> AcPad(profile.acState ?: AcState(), onCommand)
                DeviceKind.Tv -> TvPad(onCommand)
                DeviceKind.Projector -> ProjectorPad(onCommand)
                DeviceKind.Fan -> FanPad(onCommand)
            }
        }
    }
}

@Composable
private fun AcPad(state: AcState, onCommand: (RemoteCommand) -> Unit) {
    fun ac(change: AcChange) = onCommand(RemoteCommand.Ac(change))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.power) "${state.tempC}°" else "Off",
                    color = Paper,
                    style = TextStyle(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Light),
                )
                Text(if (state.power) "${state.mode.label}, fan ${state.fan.label.lowercase()}" else "Last sent", color = Muted, style = TextStyle(fontSize = 13.sp))
            }
            Key("Cooler", size = 60.dp, onClick = { ac(AcChange.Cooler) }) { PlusMinusGlyph(Paper, plus = false, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Key("Warmer", size = 60.dp, onClick = { ac(AcChange.Warmer) }) { PlusMinusGlyph(Paper, plus = true, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        Segmented(
            options = listOf(AcMode.Cool, AcMode.Dry, AcMode.Fan, AcMode.Auto, AcMode.Heat).map { it to it.label },
            selected = state.mode,
            onSelect = { ac(AcChange.SetMode(it)) },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey("Fan ${state.fan.label.lowercase()}") { ac(AcChange.SetFan(null)) }
            WideKey(if (state.swing) "Swing on" else "Swing off") { ac(AcChange.Swing) }
            WideKey(if (state.power) "Turn off" else "Turn on", accent = true) { ac(if (state.power) AcChange.Off else AcChange.On) }
        }
    }
}

@Composable
private fun TvPad(onCommand: (RemoteCommand) -> Unit) {
    fun press(b: Button) = onCommand(RemoteCommand.Press(b))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Key("Power", accent = true, onClick = { press(Button.Power) }) { PowerGlyph(Amber, Modifier.size(22.dp)) }
            Key("Input", onClick = { press(Button.Input) }) { KeyText("Input") }
            Key("Mute", onClick = { press(Button.Mute) }) { MuteGlyph(Paper, Modifier.size(22.dp)) }
            Key("Home", onClick = { press(Button.Home) }) { KeyText("Home") }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Rocker("Volume", "Vol", { press(Button.VolUp) }, { press(Button.VolDown) })
            DPad(::press)
            Rocker("Channel", "Ch", { press(Button.ChUp) }, { press(Button.ChDown) })
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey("Back") { press(Button.Back) }
            WideKey("Menu") { press(Button.Menu) }
        }
    }
}

@Composable
private fun ProjectorPad(onCommand: (RemoteCommand) -> Unit) {
    fun press(b: Button) = onCommand(RemoteCommand.Press(b))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Key("Power", accent = true, onClick = { press(Button.Power) }) { PowerGlyph(Amber, Modifier.size(22.dp)) }
            Key("Input", onClick = { press(Button.Input) }) { KeyText("Input") }
            Key("Menu", onClick = { press(Button.Menu) }) { KeyText("Menu") }
            Key("Back", onClick = { press(Button.Back) }) { KeyText("Back") }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Rocker("Volume", "Vol", { press(Button.VolUp) }, { press(Button.VolDown) })
            DPad(::press)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Key("Blank screen", onClick = { press(Button.Blank) }) { KeyText("Blank") }
                Key("Freeze", onClick = { press(Button.Freeze) }) { KeyText("Freeze") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Most projectors need Power pressed twice to switch off.", color = Muted, style = TextStyle(fontSize = 12.sp))
    }
}

@Composable
private fun FanPad(onCommand: (RemoteCommand) -> Unit) {
    fun press(b: Button) = onCommand(RemoteCommand.Press(b))
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey("Power", accent = true) { press(Button.Power) }
            WideKey("Speed") { press(Button.Speed) }
            WideKey("Swing") { press(Button.Swing) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey("Slower") { press(Button.SpeedDown) }
            WideKey("Faster") { press(Button.SpeedUp) }
            WideKey("Timer") { press(Button.Timer) }
        }
    }
}

@Composable
private fun DPad(press: (Button) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Key("Up", size = 48.dp, onClick = { press(Button.Up) }) { ArrowGlyph(Paper, Arrow.Up, Modifier.size(20.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Key("Left", size = 48.dp, onClick = { press(Button.Left) }) { ArrowGlyph(Paper, Arrow.Left, Modifier.size(20.dp)) }
            Key("OK", size = 56.dp, onClick = { press(Button.Ok) }) { KeyText("OK") }
            Key("Right", size = 48.dp, onClick = { press(Button.Right) }) { ArrowGlyph(Paper, Arrow.Right, Modifier.size(20.dp)) }
        }
        Key("Down", size = 48.dp, onClick = { press(Button.Down) }) { ArrowGlyph(Paper, Arrow.Down, Modifier.size(20.dp)) }
    }
}

/** A volume or channel rocker: + over its name over −. */
@Composable
private fun Rocker(name: String, short: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(28.dp)).background(Paper.copy(alpha = 0.07f)).border(1.dp, Hairline, RoundedCornerShape(28.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Key("$name up", size = 52.dp, plain = true, onClick = onUp) { PlusMinusGlyph(Paper, plus = true, modifier = Modifier.size(18.dp)) }
        Text(short, color = Muted, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(vertical = 2.dp))
        Key("$name down", size = 52.dp, plain = true, onClick = onDown) { PlusMinusGlyph(Paper, plus = false, modifier = Modifier.size(18.dp)) }
    }
}

/** A remote key: squeezes while pressed and ticks like a real button. */
@Composable
private fun Key(
    description: String,
    size: Dp = 56.dp,
    accent: Boolean = false,
    plain: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(size)
            .pressScale(pressed)
            .clip(RoundedCornerShape(size / 2))
            .then(if (plain) Modifier else Modifier.background(Paper.copy(alpha = if (pressed) 0.14f else 0.07f)))
            .then(if (plain) Modifier else Modifier.border(1.dp, if (accent) Amber.copy(alpha = 0.5f) else Hairline, RoundedCornerShape(size / 2)))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RowScope.WideKey(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .pressScale(pressed)
            .clip(RoundedCornerShape(14.dp))
            .background(Paper.copy(alpha = if (pressed) 0.14f else 0.07f))
            .border(1.dp, if (accent) Amber.copy(alpha = 0.5f) else Hairline, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (accent) Amber else Paper, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun KeyText(text: String) {
    Text(text, color = Paper, maxLines = 1, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium))
}

private val SheetTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
private val RowText = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
