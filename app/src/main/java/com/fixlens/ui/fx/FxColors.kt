// Shared palette for the effects ported from Libraries.dev
// (MIT, github.com/Jakubantalik/Libraries.dev). See LICENSE-libraries-dev.txt.
package com.fixlens.ui.fx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/** FixLens brand colours, kept internal so they never clash with the app's own names. */
internal object FxColors {
    val Ink = Color(0xFF0F1C2E)
    val Amber = Color(0xFFFF9F1C)
    val Paper = Color(0xFFF6F1E7)
}

/** Rotate a colour's hue by [degrees] (HSV), keeping saturation, value and alpha. */
internal fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = alpha)
}

/** Blend toward the brand's paper white. */
internal fun Color.towardPaper(fraction: Float): Color = lerp(this, FxColors.Paper, fraction)
