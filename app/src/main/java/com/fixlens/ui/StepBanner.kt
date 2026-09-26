package com.fixlens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.guide.GuideView

private val Danger = Color(0xFFFF5A5F)

/**
 * Where a guided repair is (M4): the step counter and the repair's title, plus the step's caution and what the
 * user can say next. The step's own words are in the conversation card below, verbatim from the KB.
 */
@Composable
fun StepBanner(guide: GuideView, modifier: Modifier = Modifier) {
    val accent = if (guide.technician) Danger else Amber
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.copy(alpha = 0.88f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                guide.progress.uppercase(),
                color = Ink,
                style = LabelStyle.copy(fontSize = 11.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                guide.title,
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        guide.caution?.let {
            Spacer(Modifier.height(8.dp))
            Text("⚠  $it", color = accent, style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium))
        }
        guide.prompt?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Muted, style = TextStyle(fontSize = 13.sp))
        }
    }
}
