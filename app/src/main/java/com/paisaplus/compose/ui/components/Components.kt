package com.paisaplus.compose.ui.components

import com.paisaplus.compose.ui.methodLabel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.paisaplus.compose.data.CardNetwork
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.draw.clip
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.ui.MDI_TAG
import com.paisaplus.compose.ui.amountColor
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.paisaIcon
import com.paisaplus.compose.ui.theme.LocalDarkTheme
import com.paisaplus.compose.ui.theme.LocalGlass
import com.paisaplus.compose.ui.theme.LocalIconStyle
import com.paisaplus.compose.data.IconStyle
import androidx.compose.material3.MaterialShapes
import com.paisaplus.compose.ui.theme.glass
import androidx.compose.foundation.clickable
import androidx.compose.material3.contentColorFor
import com.paisaplus.compose.ui.theme.LocalWavy
import com.paisaplus.compose.ui.theme.LocalPaisaColors
import com.paisaplus.compose.ui.theme.TonalColors
import com.paisaplus.compose.ui.theme.rememberTonal
import com.paisaplus.compose.ui.timeFmt

/**
 * One-line money text that shrinks instead of wrapping.
 * [signed] prefixes "+"/"−" by [type]; [hidden] masks the value.
 */
@Composable
fun AmountText(
    value: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    color: Color = LocalContentColor.current,
    type: TransactionType? = null,
    signed: Boolean = false,
    hidden: Boolean = false,
) {
    val text = when {
        hidden -> "••••••"
        signed && type != null -> (if (type == TransactionType.INCOME) "+" else "−") + money(kotlin.math.abs(value))
        else -> money(value)
    }
    val spoken = when {
        hidden -> str(R.string.amount_hidden)
        type == TransactionType.INCOME -> str(R.string.income_x, text)
        type == TransactionType.EXPENSE -> str(R.string.expense_x, text)
        else -> text
    }
    BasicText(
        text,
        modifier.semantics { contentDescription = spoken },
        style = style.merge(color = if (type != null) amountColor(type) else color),
        maxLines = 1,
        // Min is low so a long amount shrinks to fit at large system font sizes instead of clipping.
        autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
    )
}

/**
 * Expressive segmented list item: rows [index] of [count] share one rounded group with 2dp seams
 * (large outer corners, 4dp inner). Clip + surfaceContainer; caller adds horizontal padding.
 */
@Composable
fun Modifier.segmented(index: Int, count: Int): Modifier {
    val big = MaterialTheme.shapes.extraLarge.topStart
    val small = androidx.compose.foundation.shape.CornerSize(4.dp)
    val first = index == 0; val last = index == count - 1
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (first) big else small, topEnd = if (first) big else small,
        bottomStart = if (last) big else small, bottomEnd = if (last) big else small,
    )
    return padding(bottom = if (last) 8.dp else 2.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
}

/** Small text pill for a transaction row ("Group payment", a card's last 4). */
@Composable
fun BadgeText(text: String) = Text(
    text, Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1,
)

/** Card network mark, Material You style: one theme colour, no background. RuPay is a wordmark (no free logo). */
@Composable
fun NetworkBadge(n: CardNetwork, tint: Color = MaterialTheme.colorScheme.primary, height: Dp = 6.5.dp) {
    val m = Modifier.clearAndSetSemantics { contentDescription = n.label }
    when (n) {
        CardNetwork.VISA -> Icon(painterResource(R.drawable.ic_visa), null, m.size(height * 3.2f, height), tint = tint)
        CardNetwork.MASTERCARD -> Icon(painterResource(R.drawable.ic_mastercard), null, m.size(height * 2.1f, height * 1.3f), tint = tint)
        CardNetwork.RUPAY -> {
            val size = with(LocalDensity.current) { (height * 1.4f).toSp() }
            Text(
                "RuPay", m, color = tint,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = size, lineHeight = size),
            )
        }
    }
}

/** Label at full size that wraps onto a second line (then ellipsizes) instead of shrinking or clipping. */
@Composable
fun WrapText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = LocalContentColor.current) =
    Text(text, modifier, color = color, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)

/** Pill segmented control: outlined track, filled pill on the selected segment. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SegmentedTabs(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val p = LocalPaisaColors.current
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val stroke = if (LocalDarkTheme.current) c.outlineVariant else c.primaryContainer
    val glass = LocalGlass.current
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = if (glass == null) BorderStroke(1.dp, stroke) else null,
        modifier = modifier.fillMaxWidth().then(if (glass != null) Modifier.glass(glass, CircleShape, c.surfaceContainer, alpha = 0.4f) else Modifier),
    ) {
        Row(Modifier.padding(4.dp).selectableGroup()) {
            options.forEach { o ->
                val on = o == selected
                val bg by animateColorAsState(if (on) p.fixedDim else Color.Transparent, spec)
                val fg by animateColorAsState(if (on) p.onFixed else c.onSurfaceVariant, spec)
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).background(bg, CircleShape)
                        .selectable(on, role = Role.Tab) { onSelect(o) }.padding(horizontal = 8.dp),
                    Alignment.Center,
                ) { Text(label(o), color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Scrollable single-choice chips; the selected one morphs from a rounded square to a pill. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val c = MaterialTheme.colorScheme
    val shapes = ToggleButtonShapes(MaterialTheme.shapes.extraSmall, MaterialTheme.shapes.small, CircleShape)
    val colors = ToggleButtonDefaults.colors(
        containerColor = c.tertiaryContainer, contentColor = c.onTertiaryContainer,
        checkedContainerColor = c.primary, checkedContentColor = c.onPrimary,
    )
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            ToggleButton(checked = o == selected, onCheckedChange = { onSelect(o) }, shapes = shapes, colors = colors) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** Primary pill CTA (56dp tall). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick, modifier.heightIn(min = 56.dp).widthIn(min = 220.dp), enabled = enabled, shape = CircleShape) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Outlined pill choice with a tonal leading circle (onboarding). */
@Composable
fun OptionCard(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, c.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(c.primaryContainer.copy(alpha = 0.6f), CircleShape), Alignment.Center) {
                Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(trailing, null, tint = c.onSurfaceVariant)
        }
    }
}

private val DefaultCategoryColor = Color(0xFFFFC107)

/** Round icon badge. With [tint], uses that colour's tonal pair; otherwise the secondary container. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color? = null, size: Dp = 40.dp, autoHue: Boolean = false) {
    val c = MaterialTheme.colorScheme
    // Colourful icons: vivid inverted pair (as ExpressiveIcon); Plain/Monet keep the soft container.
    val colorful = LocalIconStyle.current == IconStyle.COLORFUL
    // [autoHue]: an uncoloured (default amber) category gets a palette hue from its icon, so lists aren't one colour.
    val seed = if (colorful && autoHue && tint == DefaultCategoryColor) Color(iconHues[icon.name.hashCode().mod(iconHues.size)]) else tint
    val t = seed?.let { rememberTonal(it) }
    val vivid = t != null && colorful
    val bg = if (vivid) t!!.content else t?.container ?: c.secondaryContainer
    val fg = if (vivid) t!!.container else t?.content ?: c.onSecondaryContainer
    Box(Modifier.size(size).background(bg, CircleShape), Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

internal val iconHues = longArrayOf(0xFF4285F4, 0xFF34A853, 0xFFF29900, 0xFFEA4335, 0xFF9C27B0, 0xFF00ACC1, 0xFFE91E63, 0xFF5C6BC0)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val iconShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided,
    MaterialShapes.Gem, MaterialShapes.Pentagon, MaterialShapes.SoftBurst, MaterialShapes.Arch,
)

/**
 * M3 Expressive icon: a shaped container whose shape and colour follow [index].
 * Settings › Icons picks multi-colour hues, the theme's (Monet) containers, or a plain circle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveIcon(icon: ImageVector, index: Int, size: Dp = 40.dp, shaped: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val i = index.mod(iconHues.size)
    val style = LocalIconStyle.current
    val (bg, fg) = when (style) {
        // Inverted tonal pair (T40/T80 fill, T90/T30 glyph): vivid in both themes.
        IconStyle.COLORFUL -> rememberTonal(Color(iconHues[i])).let { it.content to it.container }
        IconStyle.MONET -> listOf(
            c.primaryContainer to c.onPrimaryContainer,
            c.tertiaryContainer to c.onTertiaryContainer,
            c.secondaryContainer to c.onSecondaryContainer,
        )[i % 3]
        IconStyle.PLAIN -> c.secondaryContainer to c.onSecondaryContainer
    }
    val shape = if (style == IconStyle.PLAIN || !shaped) CircleShape else iconShapes[i].toShape()
    Box(Modifier.size(size).background(bg, shape), Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

/** Flat rounded card (20dp); liquid glass tinted by [color] when glass is on. */
@Composable
fun TintCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlass.current
    if (glass != null) Box(
        modifier.glass(glass, shape, color, alpha = 0.55f).clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColorFor(color), content = content)
    }
    else if (onClick != null) Surface(onClick = onClick, color = color, shape = shape, modifier = modifier, content = content)
    else Surface(color = color, shape = shape, modifier = modifier, content = content)
}

/** Card coloured from [seed] via the tone ramp; content colour is the tonal T40/T80. */
@Composable
fun TonalCard(seed: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable (TonalColors) -> Unit) {
    val t = rememberTonal(seed)
    TintCard(modifier, t.container, onClick) {
        CompositionLocalProvider(LocalContentColor provides t.content) { content(t) }
    }
}

/** Wavy progress that flattens near zero, as in the reference. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyProgress(progress: Float, modifier: Modifier = Modifier, colors: TonalColors? = null, color: Color? = null) {
    val p = progress.coerceIn(0f, 1f)
    val wavy = LocalWavy.current
    LinearWavyProgressIndicator(
        progress = { p },
        modifier = modifier.fillMaxWidth(),
        color = color ?: colors?.content ?: MaterialTheme.colorScheme.primary,
        trackColor = colors?.track ?: MaterialTheme.colorScheme.surfaceContainerHighest,
        // Wavy off in Settings, or near zero: a flat bar.
        amplitude = { if (!wavy || it < 0.02f) 0f else 1f },
        waveSpeed = if (wavy) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Centered empty state: tonal icon, one line, optional action. */
@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(16.dp))
            Button(onAction, Modifier.heightIn(min = 48.dp), shape = CircleShape) { Text(action, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Notes the alert importer writes ("Reviewed SMS alert; Ref …"): kept for de-duplication, hidden in lists. */
private val systemNote = Regex("""^(Reviewed \S+ alert|Auto-added from \S+|Added from \S+)(; Ref \S+)?$""")

@Composable
fun TransactionRow(e: ExpenseEntity, category: CategoryEntity?, accountName: String?, modifier: Modifier = Modifier, badge: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(paisaIcon(category?.icon ?: MDI_TAG), category?.let { Color(it.color) }, autoHue = true)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val title = e.name.ifBlank { category?.name ?: str(R.string.transaction) }
            if (badge == null) Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                badge()
            }
            Text(
                listOfNotNull(category?.name, accountName, e.methodLabel()).joinToString(" · ").ifEmpty { str(R.string.no_category_or_account) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            e.note?.takeUnless { systemNote.matches(it) }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 160.dp), horizontalAlignment = Alignment.End) {
            AmountText(e.amount, type = e.type, signed = true)
            // Imported rows carry a date only (midnight); a "12:00 AM" on each of them is noise.
            val at = java.time.Instant.ofEpochMilli(e.time).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            if (at != java.time.LocalTime.MIDNIGHT) Text(
                at.format(timeFmt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shared scroll behaviour for top bars, set by the nav host: the bar is clear at rest and turns
 * solid once a list scrolls under it (standard M3), so titles never sit on top of moving content.
 */
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopBarBehavior = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.material3.TopAppBarScrollBehavior?> { null }

/** Clear at rest when the theme surface is transparent (glass / custom background), else the surface. */
@Composable
fun topBarContainer(): Color = MaterialTheme.colorScheme.surface.let { if (it.alpha == 0f) Color.Transparent else it }

/** Top bar; [onBack] null hides the arrow (tab destinations). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) =
    TopAppBar(
        scrollBehavior = LocalTopBarBehavior.current,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.back)) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainer(), scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
    )

/** Modal sheet state that opens fully expanded (replaces the deprecated skipPartiallyExpanded overload). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetState() = androidx.compose.material3.rememberBottomSheetState(
    androidx.compose.material3.SheetValue.Hidden,
    setOf(androidx.compose.material3.SheetValue.Hidden, androidx.compose.material3.SheetValue.Expanded),
)

/** Profile picture (or the name's initial) in Material's 9-sided cookie shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Avatar(image: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image) {
        value = if (image.isEmpty()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(image)?.asImageBitmap() }.getOrNull()
        }
    }
    val shape = androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape()
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer), Alignment.Center) {
        val b = bmp
        if (b != null) androidx.compose.foundation.Image(b, null, Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(
            name.trim().firstOrNull()?.uppercase() ?: "",
            style = if (size >= 80.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (b == null && name.isBlank()) Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(size * 0.55f))
    }
}
