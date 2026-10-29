package com.paisaplus.compose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.hypot

/** Container / content / track colours derived from one item colour (DESIGN.md "Tonal Card Rule"). */
@Immutable
data class TonalColors(val container: Color, val content: Color, val track: Color)

// chisle: CIELAB L* stands in for HCT tone (M3 tone *is* L*); hue drifts slightly vs HCT, chroma is capped per tone.
private fun tone(seed: Color, l: Double, maxChroma: Double): Color {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(seed.toArgb(), lab)
    val c = hypot(lab[1], lab[2])
    val k = if (c > maxChroma) maxChroma / c else 1.0
    return Color(ColorUtils.LABToColor(l, lab[1] * k, lab[2] * k))
}

fun tonalColors(seed: Color, dark: Boolean): TonalColors =
    if (dark) TonalColors(tone(seed, 30.0, 24.0), tone(seed, 80.0, 30.0), tone(seed, 40.0, 24.0))
    else TonalColors(tone(seed, 90.0, 12.0), tone(seed, 40.0, 40.0), tone(seed, 80.0, 18.0))

/**
 * Floods background/surfaces with [accent]'s hue at strength [k] (0..1), keeping each role's tone (L*)
 * so container hierarchy and on-colour contrast survive. Chroma tapers toward black/white to stay in gamut.
 */
fun androidx.compose.material3.ColorScheme.saturateSurfaces(accent: Color, k: Float): androidx.compose.material3.ColorScheme {
    if (k <= 0f) return this
    val p = DoubleArray(3).also { ColorUtils.colorToLAB(accent.toArgb(), it) }
    val pc = hypot(p[1], p[2]).coerceAtLeast(1.0)
    fun t(c: Color): Color {
        val lab = DoubleArray(3).also { ColorUtils.colorToLAB(c.toArgb(), it) }
        val l = lab[0]
        val chroma = kotlin.math.min(pc, 48.0) * (kotlin.math.min(l, 100 - l) / 14.0).coerceIn(0.0, 1.0) * k
        return Color(ColorUtils.LABToColor(l, lab[1] * (1 - k) + p[1] / pc * chroma, lab[2] * (1 - k) + p[2] / pc * chroma))
    }
    return copy(
        background = t(background), surface = t(surface), surfaceVariant = t(surfaceVariant),
        surfaceDim = t(surfaceDim), surfaceBright = t(surfaceBright),
        surfaceContainerLowest = t(surfaceContainerLowest), surfaceContainerLow = t(surfaceContainerLow),
        surfaceContainer = t(surfaceContainer), surfaceContainerHigh = t(surfaceContainerHigh),
        surfaceContainerHighest = t(surfaceContainerHighest),
    )
}

@Composable
fun rememberTonal(seed: Color): TonalColors {
    val dark = LocalDarkTheme.current
    return remember(seed, dark) { tonalColors(seed, dark) }
}
