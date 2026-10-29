package com.paisaplus.compose.ui.theme

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * What glass surfaces refract plus the user's tuning (Settings › Liquid glass): [blur], [refraction]
 * and [tint] scale each surface's own defaults, 1 = as designed.
 */
data class Glass(val backdrop: Backdrop, val blur: Float = 1f, val refraction: Float = 1f, val tint: Float = 1f)

/** The app background as glass; null = glass off. */
val LocalGlass = staticCompositionLocalOf<Glass?> { null }

/**
 * Liquid-glass surface (Kyant0/AndroidLiquidGlass): vibrancy + blur (API 31+) + edge lens (API 33+)
 * over [g]'s backdrop, then [surface] at [alpha]. Without blur the tint goes near-opaque so text stays legible.
 * [shape] must be corner-based (RoundedCornerShape / CircleShape) for the lens.
 */
fun Modifier.glass(g: Glass, shape: Shape, surface: Brush, alpha: Float = 0.5f, blur: Dp = 12.dp, lens: Dp = 16.dp): Modifier {
    val tinted = (alpha * g.tint).coerceIn(0f, 1f)
    val a = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) maxOf(tinted, 0.92f) else tinted
    return drawBackdrop(
        backdrop = g.backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blur.toPx() * g.blur)
            lens(lens.toPx() * g.refraction, lens.toPx() * 2 * g.refraction)
        },
        onDrawSurface = { drawRect(surface, alpha = a) },
    )
}

fun Modifier.glass(g: Glass, shape: Shape, tint: Color, alpha: Float = 0.5f, blur: Dp = 12.dp, lens: Dp = 16.dp) =
    glass(g, shape, SolidColor(tint.copy(alpha = 1f)), alpha * tint.alpha, blur, lens)
