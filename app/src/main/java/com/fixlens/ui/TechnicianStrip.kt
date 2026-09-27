package com.fixlens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Under a technician-only guide: a row of nearby technicians to call. DEMO ONLY: the people and prices are made up,
// nothing is fetched (the app has no network), and no real business is named. A booking partner would plug in here.

private data class SampleTech(val name: String, val rating: Float, val jobs: Int, val km: Float, val visitDelta: Int)

private val SAMPLE_TECHS = listOf(
    SampleTech("Ravi Kumar", 4.8f, 412, 1.2f, 0),
    SampleTech("Imran Shaikh", 4.7f, 268, 2.4f, -50),
    SampleTech("Suresh Reddy", 4.9f, 530, 3.1f, 50),
    SampleTech("Anil Varma", 4.6f, 187, 4.0f, -100),
)

/** The kind of technician for a KB appliance, and a typical example visit charge in rupees (made up for the demo). */
private fun trade(appliance: String?): Pair<String, Int> = when (appliance) {
    "car" -> "Car mechanic" to 499
    "bike" -> "Bike mechanic" to 299
    "air_conditioner" -> "AC technician" to 399
    "refrigerator" -> "Fridge technician" to 349
    "washing_machine" -> "Washing machine technician" to 349
    "television" -> "TV technician" to 349
    "laptop" -> "Laptop technician" to 399
    "water_heater" -> "Geyser technician" to 299
    "water_purifier" -> "RO purifier technician" to 299
    "microwave" -> "Microwave technician" to 349
    "inverter" -> "Inverter technician" to 349
    else -> "Technician" to 349
}

@Composable
fun TechnicianStrip(appliance: String?, modifier: Modifier = Modifier) {
    val (trade, visit) = trade(appliance)
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Technicians near you", color = Paper, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.width(8.dp))
            Text(
                "Sample",
                color = Muted,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Hairline, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(SAMPLE_TECHS) { TechCard(it, trade, visit + it.visitDelta) }
        }
    }
}

@Composable
private fun TechCard(t: SampleTech, trade: String, visit: Int) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(212.dp)
            .clip(shape)
            .background(Ink.copy(alpha = 0.93f))
            .border(1.dp, Hairline, shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(InkRaised).border(1.dp, Amber.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.name.split(" ").mapNotNull { it.firstOrNull() }.joinToString(""), color = Paper, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(t.name, color = Paper, maxLines = 1, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(trade, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Amber)) { append("★ ") }
                append("%.1f".format(t.rating))
                withStyle(SpanStyle(color = Muted)) { append("  ${t.jobs} jobs  ·  ${"%.1f".format(t.km)} km") }
            },
            color = Paper,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)) { append("₹$visit") }
                withStyle(SpanStyle(color = Muted, fontSize = 12.sp)) { append("  visiting charge") }
            },
        )
    }
}
