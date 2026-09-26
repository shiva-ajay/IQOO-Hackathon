package com.fixlens.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// FixLens palette, see design/logo/README.md.
internal val Ink = Color(0xFF0F1C2E)
internal val InkRaised = Color(0xFF17263B)
internal val Amber = Color(0xFFFF9F1C)
internal val Paper = Color(0xFFF6F1E7)
internal val Muted = Paper.copy(alpha = 0.56f)
internal val Hairline = Paper.copy(alpha = 0.10f)
internal val Online = Color(0xFF3DDC97)

internal val LabelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
internal val QuestionStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
internal val AnswerStyle = TextStyle(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
