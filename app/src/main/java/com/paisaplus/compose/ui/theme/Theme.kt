package com.paisaplus.compose.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import com.paisaplus.compose.data.BgMode
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.paisaplus.compose.R
import com.paisaplus.compose.data.ThemeMode
import com.paisaplus.compose.data.ThemeSettings
import com.paisaplus.compose.data.AppFont
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

private fun outfit(w: Int) = Font(
    R.font.outfit,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Outfit = FontFamily(outfit(300), outfit(400), outfit(500), outfit(600), outfit(700))

// Google Sans Flex: the M3 Expressive typeface (variable wght / wdth / ROND). [round] = 0 regular, 100 fully rounded.
private fun sansFlex(w: Int, round: Float) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("ROND", round)),
)
private fun sansFlexFamily(round: Float) = FontFamily(listOf(300, 400, 500, 600, 700, 800).map { sansFlex(it, round) })
/** Fully rounded (ROND 100): the Expressive voice, used for every style so the app reads as one face. */
private val SansFlex = sansFlexFamily(100f)

/**
 * DESIGN.md "Typography": one family everywhere, proportional numerals. Sizes sit 1sp above the
 * M3 baseline for legibility. Emphasized = same size, +200 weight, same face.
 */
private fun paisaTypography(font: AppFont): Typography {
    val base = if (font == AppFont.OUTFIT) Outfit else SansFlex
    val bold = base
    fun s(size: Int, line: Int, weight: Int) = TextStyle(fontFamily = base, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight))
    fun TextStyle.em() = copy(fontFamily = bold, fontWeight = FontWeight((fontWeight?.weight ?: 400) + 200))
    val dl = s(57, 64, 400); val dm = s(45, 52, 400); val ds = s(40, 48, 600)
    val hl = s(32, 40, 400); val hm = s(28, 36, 400); val hs = s(25, 32, 500)
    val tl = s(23, 30, 500); val tm = s(19, 26, 500); val ts = s(16, 22, 600)
    val bl = s(17, 25, 400); val bm = s(15, 21, 400); val bs = s(13, 18, 400)
    val ll = s(16, 21, 600); val lm = s(13, 17, 500); val lsm = s(12, 16, 500)
    return Typography(
        displayLarge = dl, displayMedium = dm, displaySmall = ds,
        headlineLarge = hl, headlineMedium = hm, headlineSmall = hs,
        titleLarge = tl, titleMedium = tm, titleSmall = ts,
        bodyLarge = bl, bodyMedium = bm, bodySmall = bs,
        labelLarge = ll, labelMedium = lm, labelSmall = lsm,
        displayLargeEmphasized = dl.em(), displayMediumEmphasized = dm.em(), displaySmallEmphasized = ds.em(),
        headlineLargeEmphasized = hl.em(), headlineMediumEmphasized = hm.em(), headlineSmallEmphasized = hs.em(),
        titleLargeEmphasized = tl.em(), titleMediumEmphasized = tm.em(), titleSmallEmphasized = ts.em(),
        bodyLargeEmphasized = bl.em(), bodyMediumEmphasized = bm.em(), bodySmallEmphasized = bs.em(),
        labelLargeEmphasized = ll.em(), labelMediumEmphasized = lm.em(), labelSmallEmphasized = lsm.em(),
    )
}

private val PaisaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Built-in brand seed (accent = 0). */
private val BrandSeed = Color(0xFF95492B)

/** Roles outside ColorScheme: money direction and the segmented-tab fixed accent. */
data class PaisaColors(
    val income: Color,
    val expense: Color,
    val incomeFill: Color,
    val expenseFill: Color,
    val fixedDim: Color = Color(0xFFFFB59A),
    val onFixed: Color = Color(0xFF3A0B00),
)

private val LightPaisa = PaisaColors(Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF66BB6A), Color(0xFFEF5350))
private val DarkPaisa = PaisaColors(Color(0xFF4EDE80), Color(0xFFFFB4AB), Color(0xFF66BB6A), Color(0xFFEF5350))

val LocalPaisaColors = staticCompositionLocalOf { LightPaisa }
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Pure black behind everything; cards keep the scheme's (Monet-tinted) containers, like Nothing's widgets. */
private fun ColorScheme.amoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black, // Home and the tab bar sit on this
)

/** The app's colour scheme for [settings] (accent / Monet, variant, contrast, colourful surfaces); also used by the widgets. */
fun paisaScheme(ctx: android.content.Context, settings: ThemeSettings, dark: Boolean): ColorScheme {
    val monet = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val style = PaletteStyle.entries.firstOrNull { it.name == settings.scheme } ?: PaletteStyle.TonalSpot
    // Untouched Monet keeps the system's own scheme (which also honours Android 14+ system contrast);
    // any variant/contrast change regenerates from the wallpaper seed.
    val systemMonet = monet && style == PaletteStyle.TonalSpot && settings.contrast == 0f
    val system = if (monet) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else null
    var scheme = when {
        systemMonet -> system!!
        else -> dynamicColorScheme(
            seedColor = when {
                monet -> Color(ctx.getColor(android.R.color.system_accent1_500))
                settings.accent != 0 -> Color(settings.accent)
                else -> BrandSeed
            },
            isDark = dark,
            style = style,
            contrastLevel = settings.contrast.toDouble(),
            specVersion = if (settings.expressiveSpec) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021,
        )
    }
    // With wallpaper colours, backgrounds and cards stay the system's own Monet neutrals whatever the colour style,
    // so the app matches the launcher and Nothing-style widgets; the style still shapes the accents.
    if (system != null && !systemMonet) scheme = scheme.copy(
        background = system.background, onBackground = system.onBackground,
        surface = system.surface, onSurface = system.onSurface,
        surfaceVariant = system.surfaceVariant, onSurfaceVariant = system.onSurfaceVariant,
        surfaceContainerLowest = system.surfaceContainerLowest, surfaceContainerLow = system.surfaceContainerLow,
        surfaceContainer = system.surfaceContainer, surfaceContainerHigh = system.surfaceContainerHigh,
        surfaceContainerHighest = system.surfaceContainerHighest, surfaceBright = system.surfaceBright, surfaceDim = system.surfaceDim,
        outline = system.outline, outlineVariant = system.outlineVariant,
    )
    return scheme.saturateSurfaces(scheme.primary, settings.surfaceTint)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaisaTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    var scheme = remember(dark, settings.dynamicColor, settings.accent, settings.scheme, settings.contrast, settings.expressiveSpec, settings.surfaceTint) {
        paisaScheme(ctx, settings, dark)
    }
    if (dark && settings.amoled) scheme = scheme.amoled()
    // Glass needs the background in its own layer, so it takes the same path as a custom background.
    val backdrop = settings.bg != BgMode.THEME || settings.glass
    val themeBg = scheme.background
    val glow = scheme.primary to scheme.tertiary
    // A custom background shows through: screens paint background/surface, so make those clear.
    if (backdrop) scheme = scheme.copy(background = Color.Transparent, surface = Color.Transparent)
    // Segmented-tab fixed accent: primary T80 with its dark on-colour, in both themes.
    val paisa = (if (dark) DarkPaisa else LightPaisa).copy(
        fixedDim = if (dark) scheme.primary else scheme.inversePrimary,
        onFixed = if (dark) scheme.onPrimary else scheme.onPrimaryContainer,
    )
    val haptics = LocalHapticFeedback.current
    CompositionLocalProvider(
        LocalPaisaColors provides paisa,
        LocalDarkTheme provides dark,
        LocalHapticFeedback provides if (settings.haptics) haptics else NoHaptics,
        LocalNavLabels provides settings.navLabels,
        LocalWavy provides settings.wavy,
        LocalIconStyle provides settings.icons,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = remember(settings.corners) { scaledShapes(settings.corners) },
            typography = remember(settings.font) { paisaTypography(settings.font) },
        ) {
            // Always the same tree: switching glass/background must not rebuild the UI (it reset screen state).
            Backdrop(settings, dark, themeBg, glow, enabled = backdrop) { content() }
        }
    }
}

val LocalNavLabels = staticCompositionLocalOf { true }
val LocalWavy = staticCompositionLocalOf { true }
/** Settings › Month starts on (1 = calendar months). */
val LocalMonthStart = staticCompositionLocalOf { 1 }
val LocalIconStyle = staticCompositionLocalOf { com.paisaplus.compose.data.IconStyle.COLORFUL }

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

private fun scaledShapes(k: Float) = Shapes(
    extraSmall = RoundedCornerShape((8 * k).dp),
    small = RoundedCornerShape((12 * k).dp),
    medium = RoundedCornerShape((16 * k).dp),
    large = RoundedCornerShape((20 * k).dp),
    extraLarge = RoundedCornerShape((28 * k).dp),
)

/**
 * Solid / gradient / image behind every screen, with a black dim for legibility. The theme
 * background gets two soft accent glows so glass has something to refract. With glass on,
 * this layer is recorded and provided as [LocalGlass].
 */
@Composable
private fun Backdrop(s: ThemeSettings, dark: Boolean, themeBg: Color, glow: Pair<Color, Color>, enabled: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val base = if (s.bg == BgMode.THEME) themeBg else if (dark) Color(0xFF1A110E) else Color(0xFFFFF8F6)
    val layer = rememberLayerBackdrop()
    val image by produceState<ImageBitmap?>(null, s.bg, s.bgImage) {
        value = if (s.bg != BgMode.IMAGE || s.bgImage.isEmpty()) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(s.bgImage)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                o.inSampleSize = generateSequence(1) { it * 2 }.first { o.outWidth / it <= 1440 || it >= 64 }
                o.inJustDecodeBounds = false
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val accent = Color(s.bgColor)
    Box(Modifier.fillMaxSize()) {
        // Plain theme without glass: no layer; the screens paint their own background.
        if (enabled) Box(
            Modifier.fillMaxSize().then(if (s.glass) Modifier.layerBackdrop(layer) else Modifier).drawBehind {
                drawRect(base)
                when (s.bg) {
                    BgMode.SOLID -> drawRect(accent)
                    BgMode.GRADIENT -> drawRect(Brush.linearGradient(listOf(accent, base), end = Offset(size.width, size.height)))
                    BgMode.THEME -> {
                        val a = if (dark) 0.28f else 0.22f
                        drawRect(Brush.radialGradient(listOf(glow.first.copy(alpha = a), Color.Transparent), Offset(size.width * 0.1f, size.height * 0.12f), size.width * 0.9f))
                        drawRect(Brush.radialGradient(listOf(glow.second.copy(alpha = a), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.62f), size.width))
                    }
                    else -> Unit
                }
            },
        ) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (s.bg != BgMode.THEME) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.bgDim.coerceIn(0f, 0.9f))))
        }
        CompositionLocalProvider(LocalGlass provides if (enabled && s.glass) Glass(layer, s.glassBlur, s.glassRefraction, s.glassTint) else null) { content() }
    }
}
