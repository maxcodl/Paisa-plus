package com.paisaplus.compose.ui.settings

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import com.paisaplus.compose.data.currentPeriod
import com.paisaplus.compose.data.periodRange
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.paisaplus.compose.ui.components.*
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.paisaplus.compose.data.IconStyle
import com.paisaplus.compose.data.BalanceMode
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.Routes
import com.paisaplus.compose.Tab
import com.paisaplus.compose.TabConfig
import com.paisaplus.compose.data.BgMode
import com.paisaplus.compose.data.Money
import com.paisaplus.compose.data.ThemeMode
import com.paisaplus.compose.data.WidgetStyle
import com.paisaplus.compose.data.WidgetAccent
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.theme.LocalDarkTheme
import com.paisaplus.compose.ui.theme.paisaScheme
import java.util.Locale

internal val accents = listOf(0, 0xFF95492B, 0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFF00838F, 0xFFEF6C00, 0xFF455A64).map { it.toInt() }
private val bgColors = listOf(0xFF95492B, 0xFF1A237E, 0xFF004D40, 0xFF4A148C, 0xFF263238, 0xFF880E4F, 0xFF33691E).map { it.toInt() }
private val lockAfter = listOf(0, 1, 5, 15)
private const val SOURCE_URL = "https://github.com/maxcodl/Paisa-plus/"

// M3 scheme variants (com.materialkolor.PaletteStyle names) → one-line description.
internal val schemeSummary = linkedMapOf(
    "TonalSpot" to str(R.string.calm_low_chroma_default),
    "Vibrant" to str(R.string.saturated_colours_tinted_surfaces),
    "Expressive" to str(R.string.playful_hue_shifts),
    "Fidelity" to str(R.string.stays_true_to_the_accent),
    "Content" to str(R.string.accent_faithful_content_led),
    "Rainbow" to str(R.string.colourful_accents_neutral_surfaces),
    "FruitSalad" to str(R.string.bold_mixed_hues),
    "Neutral" to str(R.string.near_greyscale),
    "Monochrome" to str(R.string.pure_greyscale),
)

private fun schemeLabel(s: String) = s.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

private fun contrastLabel(c: Float) = when {
    c < -0.25f -> str(R.string.reduced)
    c < 0.25f -> str(R.string.standard)
    c < 0.75f -> str(R.string.medium)
    else -> str(R.string.high)
}

private val dayMonth = java.time.format.DateTimeFormatter.ofPattern("d MMM")
private val backupWhen = java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm")

/** "Day 30 · October = 30 Sep – 29 Oct"; day 1 explains the salary rule instead. */
private fun monthStartSummary(start: Int): String {
    if (start <= 1) return str(R.string.day_1_calendar_months)
    val m = currentPeriod(start)
    val (from, to) = periodRange(m, start)
    return str(R.string.day_x_x_x_x, start, m.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()), from.format(dayMonth), to.format(dayMonth))
}

private val balanceIcons = listOf(Icons.Rounded.AccountBalance, Icons.Rounded.AccountBalanceWallet, Icons.Rounded.CreditCard, Icons.Rounded.Insights)

/** Settings sub-pages, opened from the hub as `settings/{name}`. */
enum class SettingsPage(val icon: ImageVector, val hue: Int, val title: Int, val summary: Int) {
    MONEY(Icons.Rounded.Payments, 1, R.string.money_totals, R.string.money_totals_summary),
    COLOURS(Icons.Rounded.Palette, 6, R.string.colours_theme, R.string.colours_theme_summary),
    LOOK(Icons.Rounded.TextFields, 4, R.string.look_feel, R.string.look_feel_summary),
    BACKGROUND(Icons.Rounded.Wallpaper, 2, R.string.background_glass, R.string.background_glass_summary),
    HOME(Icons.Rounded.Dashboard, 5, R.string.home_navigation, R.string.home_navigation_summary),
    WIDGETS(Icons.Rounded.Widgets, 3, R.string.widgets, R.string.widgets_summary),
    PRIVACY(Icons.Rounded.Lock, 0, R.string.privacy_security, R.string.privacy_security_summary),
    DATA(Icons.Rounded.Backup, 7, R.string.backup_data, R.string.backup_data_summary),
}

/** Large collapsing title over a padded list; shared by the hub and every sub-page. */
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.back)) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 24.dp), content = content)
    }
}

/** Hub: profile card, the sub-pages in four groups, About. */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, open: (String) -> Unit) {
    val name by vm.userName.collectAsStateWithLifecycle()
    val photo by vm.userImage.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    fun ListGroupScope.page(p: SettingsPage) = item { LinkRow(p.icon, p.hue, str(p.title), str(p.summary)) { open("${Routes.SETTINGS}/${p.name}") } }

    SettingsScaffold(str(R.string.settings), onBack) {
        // Account-style card on top, like phone settings.
        item {
            TintCard(Modifier.fillMaxWidth(), onClick = { open(Routes.PROFILE) }, shape = MaterialTheme.shapes.extraLarge) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(photo, name, 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name.ifBlank { str(R.string.your_profile) }, style = MaterialTheme.typography.titleLarge)
                        Text(str(R.string.name_photo_shortcuts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { ListGroup { page(SettingsPage.MONEY) } }
        item { ListHeader(str(R.string.appearance)) }
        item { ListGroup { page(SettingsPage.COLOURS); page(SettingsPage.LOOK); page(SettingsPage.BACKGROUND) } }
        item { ListHeader(str(R.string.home_and_widgets)) }
        item { ListGroup { page(SettingsPage.HOME); page(SettingsPage.WIDGETS) } }
        item { ListHeader(str(R.string.security_and_data)) }
        item { ListGroup { page(SettingsPage.PRIVACY); page(SettingsPage.DATA) } }

        item { ListHeader(str(R.string.about)) }
        item {
            ListGroup {
                item { PrefRow(Icons.Rounded.Info, 4, "Paisa+", str(R.string.version_x, version)) }
                item { LinkRow(Icons.Rounded.Code, 2, str(R.string.source_code), "github.com/maxcodl/Paisa-plus") { uri.openUri(SOURCE_URL) } }
            }
        }
        item {
            Text(
                str(R.string.made_with_love), Modifier.fillMaxWidth().padding(top = 24.dp).clickable { uri.openUri(SOURCE_URL) },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

/** One settings sub-page; every page reads the same state and only draws its own rows. */
@Composable
fun SettingsPageScreen(vm: AppViewModel, page: SettingsPage, onBack: () -> Unit, open: (String) -> Unit) {
    val t by vm.theme.collectAsStateWithLifecycle()
    val money by vm.money.collectAsStateWithLifecycle()
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    val excludeTransfers by vm.excludeTransfers.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val balanceModes by vm.balanceModes.collectAsStateWithLifecycle()
    val widget by vm.widget.collectAsStateWithLifecycle()
    val monthStart by vm.monthStart.collectAsStateWithLifecycle()
    val lock by vm.lock.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val canLock = remember { (ctx as? FragmentActivity)?.let(AppLock::available) ?: false }
    var currencySheet by remember { mutableStateOf(false) }
    val backupDir by vm.backupDir.collectAsStateWithLifecycle()
    val lastBackup by vm.lastBackup.collectAsStateWithLifecycle()
    val backupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setBackupDir) }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setTheme(t.copy(bg = BgMode.IMAGE, bgImage = uri.toString()))
        }
    }

    SettingsScaffold(str(page.title), onBack) {
        when (page) {
            SettingsPage.MONEY -> {
                item {
                    ListGroup {
                        item {
                            val cur = runCatching { java.util.Currency.getInstance(money.currency) }.getOrNull()
                            LinkRow(Icons.Rounded.Payments, 1, str(R.string.currency), "${cur?.getSymbol(Locale.forLanguageTag(money.locale)) ?: ""} ${money.currency} · ${cur?.displayName ?: ""}") { currencySheet = true }
                        }
                        item {
                            DropdownRow(Icons.Rounded.Numbers, 0, str(R.string.number_format), Money.locales, money.locale, { Locale.forLanguageTag(it).displayName }, { Money.formatter(money.copy(locale = it)).format(1234567.5) }) { vm.setMoney(money.copy(locale = it)) }
                        }
                    }
                }
                item { ListHeader(str(R.string.totals)) }
                item {
                    ListGroup {
                        item {
                            PrefBlock(Icons.Rounded.CalendarMonth, 4, str(R.string.month_starts_on), monthStartSummary(monthStart)) {
                                SettingSlider(monthStart.toFloat(), 1f..31f, 29, str(R.string.month_starts_on_day)) { vm.setMonthStart(kotlin.math.round(it).toInt()) }
                            }
                        }
                        item { SwitchRow(Icons.Rounded.SwapHoriz, 5, str(R.string.exclude_transfers_from_totals), str(R.string.category_transfer_stays_in_balances), excludeTransfers) { vm.setExcludeTransfers(it) } }
                    }
                }
            }

            SettingsPage.COLOURS -> {
                item {
                    ListGroup {
                        item {
                            PrefBlock(Icons.Rounded.DarkMode, 7, str(R.string.theme)) {
                                SegmentedTabs(ThemeMode.entries, t.mode, { str(when (it) { ThemeMode.SYSTEM -> R.string.system_language; ThemeMode.LIGHT -> R.string.light; ThemeMode.DARK -> R.string.dark }) }, { vm.setTheme(t.copy(mode = it)) })
                            }
                        }
                        item { SwitchRow(Icons.Rounded.Contrast, 7, str(R.string.pure_black_in_dark_theme), null, t.amoled) { vm.setTheme(t.copy(amoled = it)) } }
                    }
                }
                item { ListHeader(str(R.string.colours)) }
                item {
                    ListGroup {
                        if (Build.VERSION.SDK_INT >= 31) item { SwitchRow(Icons.Rounded.Wallpaper, 4, str(R.string.wallpaper_colours), str(R.string.monet_colours_from_your_wallpaper), t.dynamicColor) { vm.setTheme(t.copy(dynamicColor = it)) } }
                        if (!t.dynamicColor || Build.VERSION.SDK_INT < 31) item {
                            PrefBlock(Icons.Rounded.Palette, 6, str(R.string.accent_colour)) { Swatches(accents, t.accent, firstIsDefault = true) { vm.setTheme(t.copy(accent = it)) } }
                        }
                        item {
                            PrefBlock(Icons.Rounded.ColorLens, 2, "${str(R.string.colour_style)} · ${schemeLabel(t.scheme)}", schemeSummary[t.scheme], inset = false) {
                                SchemeCarousel(t) { vm.setTheme(t.copy(scheme = it)) }
                            }
                        }
                        item { SwitchRow(Icons.Rounded.AutoAwesome, 3, str(R.string.expressive_colours), str(R.string.material_3_expressive_2025_colour), t.expressiveSpec) { vm.setTheme(t.copy(expressiveSpec = it)) } }
                    }
                }
                item { ListHeader(str(R.string.fine_tune)) }
                item {
                    ListGroup {
                        item {
                            PrefBlock(Icons.Rounded.Tonality, 0, str(R.string.contrast), contrastLabel(t.contrast)) {
                                SettingSlider(t.contrast, -1f..1f, 3, str(R.string.contrast)) { vm.setTheme(t.copy(contrast = it)) }
                            }
                        }
                        item {
                            PrefBlock(Icons.Rounded.FormatColorFill, 5, str(R.string.colourful_surfaces), if (t.surfaceTint == 0f) str(R.string.off_neutral_backgrounds) else str(R.string.x_accent_in_backgrounds, (t.surfaceTint * 100).toInt())) {
                                SettingSlider(t.surfaceTint, 0f..1f, 9, str(R.string.colourful_surfaces)) { vm.setTheme(t.copy(surfaceTint = it)) }
                            }
                        }
                    }
                }
            }

            SettingsPage.LOOK -> {
                item {
                    ListGroup {
                        item {
                            val lang = remember { com.paisaplus.compose.L10n.tag(ctx) }
                            val langs = com.paisaplus.compose.L10n.languages
                            DropdownRow(Icons.Rounded.Translate, 1, str(R.string.language), langs.map { it.first }, lang, { tag -> if (tag.isEmpty()) str(R.string.system_language) else langs.first { it.first == tag }.second }, { str(R.string.language_restarts) }) {
                                if (it != lang) com.paisaplus.compose.L10n.set(ctx, it)
                            }
                        }
                        item {
                            PrefBlock(Icons.Rounded.TextFields, 4, str(R.string.font)) {
                                SegmentedTabs(com.paisaplus.compose.data.AppFont.entries, t.font, { it.label }, { vm.setTheme(t.copy(font = it)) })
                            }
                        }
                        item {
                            PrefBlock(Icons.Rounded.Interests, 3, str(R.string.icons), str(R.string.colourful_monet_theme_or_plain)) {
                                SegmentedTabs(IconStyle.entries, t.icons, { it.label }, { vm.setTheme(t.copy(icons = it)) })
                            }
                        }
                        item {
                            PrefBlock(Icons.Rounded.RoundedCorner, 2, str(R.string.corner_roundness)) {
                                SettingSlider(t.corners, 0f..1.5f, 5, str(R.string.corner_roundness)) { vm.setTheme(t.copy(corners = it)) }
                            }
                        }
                    }
                }
                item { ListHeader(str(R.string.motion_and_feel)) }
                item {
                    ListGroup {
                        item { SwitchRow(Icons.AutoMirrored.Rounded.Label, 0, str(R.string.tab_labels), null, t.navLabels) { vm.setTheme(t.copy(navLabels = it)) } }
                        item { SwitchRow(Icons.Rounded.Vibration, 1, str(R.string.haptics), null, t.haptics) { vm.setTheme(t.copy(haptics = it)) } }
                        item { SwitchRow(Icons.Rounded.Waves, 5, str(R.string.wavy_progress_bars), null, t.wavy) { vm.setTheme(t.copy(wavy = it)) } }
                    }
                }
            }

            SettingsPage.BACKGROUND -> {
                item {
                    ListGroup {
                        item {
                            PrefBlock(Icons.Rounded.Image, 4, str(R.string.background)) {
                                SegmentedTabs(BgMode.entries, t.bg, { str(when (it) { BgMode.THEME -> R.string.theme; BgMode.SOLID -> R.string.solid; BgMode.GRADIENT -> R.string.gradient; BgMode.IMAGE -> R.string.image }) }, { m ->
                                    if (m == BgMode.IMAGE && t.bgImage.isEmpty()) image.launch(arrayOf("image/*")) else vm.setTheme(t.copy(bg = m))
                                })
                                if (t.bg == BgMode.SOLID || t.bg == BgMode.GRADIENT) Column(Modifier.padding(top = 12.dp)) {
                                    Swatches(bgColors, t.bgColor) { vm.setTheme(t.copy(bgColor = it)) }
                                }
                                if (t.bg == BgMode.IMAGE) OutlinedButton({ image.launch(arrayOf("image/*")) }, Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text(str(R.string.choose_image)) }
                            }
                        }
                        if (t.bg != BgMode.THEME) item {
                            PrefBlock(Icons.Rounded.Brightness6, 2, str(R.string.dim), "${(t.bgDim * 100).toInt()}%") {
                                SettingSlider(t.bgDim, 0f..0.9f, 0, str(R.string.background_dim)) { vm.setTheme(t.copy(bgDim = it)) }
                            }
                        }
                    }
                }
                item { ListHeader(str(R.string.liquid_glass)) }
                item {
                    ListGroup {
                        item { SwitchRow(Icons.Rounded.BlurOn, 0, str(R.string.liquid_glass), if (Build.VERSION.SDK_INT < 33) str(R.string.full_refraction_needs_android_13) else str(R.string.glass_cards_and_tab_bar), t.glass) { vm.setTheme(t.copy(glass = it)) } }
                        // 0..2× each surface's designed value; 1 = default.
                        if (t.glass && Build.VERSION.SDK_INT >= 31) item {
                            PrefBlock(Icons.Rounded.BlurLinear, 6, str(R.string.blur)) { SettingSlider(t.glassBlur, 0f..2f, 7, str(R.string.glass_blur)) { vm.setTheme(t.copy(glassBlur = it)) } }
                        }
                        if (t.glass && Build.VERSION.SDK_INT >= 33) item {
                            PrefBlock(Icons.Rounded.Lens, 5, str(R.string.refraction)) { SettingSlider(t.glassRefraction, 0f..2f, 7, str(R.string.glass_refraction)) { vm.setTheme(t.copy(glassRefraction = it)) } }
                        }
                        if (t.glass) item {
                            PrefBlock(Icons.Rounded.Opacity, 7, str(R.string.tint)) { SettingSlider(t.glassTint, 0.2f..1.8f, 7, str(R.string.glass_tint)) { vm.setTheme(t.copy(glassTint = it)) } }
                        }
                        if (t.glass && (t.glassBlur != 1f || t.glassRefraction != 1f || t.glassTint != 1f)) item {
                            PrefRow(Icons.Rounded.RestartAlt, 3, str(R.string.reset_glass), onClick = { vm.setTheme(t.copy(glassBlur = 1f, glassRefraction = 1f, glassTint = 1f)) })
                        }
                    }
                }
            }

            SettingsPage.HOME -> {
                item { ListHeader(str(R.string.balance_card)) }
                item {
                    // Pages of the Home card (swipe) and the widget (tap ›), in this order; at least one stays on.
                    ListGroup {
                        BalanceMode.entries.forEachIndexed { i, m ->
                            val on = m in balanceModes
                            item {
                                SwitchRow(balanceIcons[i], i + 1, m.label, null, on, enabled = !on || balanceModes.size > 1) { v ->
                                    vm.setBalanceModes(BalanceMode.entries.filter { if (it == m) v else it in balanceModes })
                                }
                            }
                        }
                    }
                }
                item { ListHeader(str(R.string.bottom_bar_home_up_to, TabConfig.MAX - 1)) }
                item {
                    ListGroup {
                        val rest = tabs.drop(1) + (Tab.entries - tabs.toSet() - Tab.HOME)
                        rest.forEach { tab ->
                            val on = tab in tabs
                            val i = tabs.indexOf(tab)
                            item {
                                PrefRow(
                                    tab.icon, tab.ordinal, tab.label, if (on) str(R.string.position_x, i) else null,
                                    enabled = on || tabs.size < TabConfig.MAX, role = Role.Checkbox,
                                    onClick = { vm.setTabs(if (on) tabs - tab else tabs + tab) },
                                ) {
                                    if (on) {
                                        IconButton({ vm.setTabs(tabs.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 1) { Icon(Icons.Rounded.KeyboardArrowUp, str(R.string.move_x_left, tab.label)) }
                                        IconButton({ vm.setTabs(tabs.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < tabs.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, str(R.string.move_x_right, tab.label)) }
                                    }
                                    Checkbox(on, null, enabled = on || tabs.size < TabConfig.MAX)
                                }
                            }
                        }
                    }
                }
            }

            SettingsPage.WIDGETS -> {
                item {
                    // Light / dark follows Colours & theme; corners follow the launcher's own widget radius.
                    ListGroup {
                        item {
                            PrefBlock(Icons.Rounded.Palette, 4, str(R.string.widget_style)) {
                                SegmentedTabs(WidgetStyle.entries, widget.style, { str(if (it == WidgetStyle.APP) R.string.widget_style_app else R.string.widget_style_nothing) }, { vm.setWidget(widget.copy(style = it)) })
                            }
                        }
                        item {
                            PrefBlock(Icons.Rounded.Opacity, 7, str(R.string.widget_opacity), "${(widget.opacity * 100).toInt()}%") {
                                SettingSlider(widget.opacity, 0.2f..1f, 7, str(R.string.widget_opacity)) { vm.setWidget(widget.copy(opacity = it)) }
                            }
                        }
                        if (widget.style == WidgetStyle.NOTHING) item {
                            PrefBlock(Icons.Rounded.ColorLens, 6, str(R.string.widget_accent), str(R.string.card_uses_wallpaper_neutral)) {
                                SegmentedTabs(WidgetAccent.entries, widget.accent, { str(if (it == WidgetAccent.RED) R.string.nothing_red else R.string.wallpaper_colours) }, { vm.setWidget(widget.copy(accent = it)) })
                            }
                        }
                        if (widget.style == WidgetStyle.APP) item { SwitchRow(Icons.Rounded.AutoAwesome, 3, str(R.string.glass_sheen), null, widget.sheen) { vm.setWidget(widget.copy(sheen = it)) } }
                        item { SwitchRow(Icons.Rounded.AddCircle, 5, str(R.string.widget_add_button), str(R.string.on_the_balance_widget), widget.addButton) { vm.setWidget(widget.copy(addButton = it)) } }
                    }
                }
            }

            SettingsPage.PRIVACY -> {
                item {
                    ListGroup {
                        item { SwitchRow(Icons.Rounded.VisibilityOff, 3, str(R.string.hide_amounts), str(R.string.mask_balances_in_the_app), hidden) { vm.toggleHideBalance() } }
                        item { SwitchRow(Icons.Rounded.Screenshot, 3, str(R.string.block_screenshots), str(R.string.also_hides_the_app_in), lock.secure) { vm.setLock(lock.copy(secure = it)) } }
                    }
                }
                item { ListHeader(str(R.string.app_lock)) }
                item {
                    ListGroup {
                        item { SwitchRow(Icons.Rounded.Lock, 0, str(R.string.app_lock), if (canLock) str(R.string.unlock_with_fingerprint_or_screen) else str(R.string.set_a_screen_lock_first), lock.enabled && canLock, enabled = canLock) { vm.setLock(lock.copy(enabled = it)) } }
                        if (lock.enabled && canLock) item {
                            DropdownRow(Icons.Rounded.Timer, 2, str(R.string.lock_after), lockAfter, lock.afterMinutes, { if (it == 0) str(R.string.immediately) else str(R.string.x_min, it) }) { vm.setLock(lock.copy(afterMinutes = it)) }
                        }
                    }
                }
            }

            SettingsPage.DATA -> {
                item {
                    ListGroup {
                        // Same switch as Backup & restore › Daily backup: on = pick a folder (WorkManager runs it daily, keeps the last few).
                        item {
                            SwitchRow(
                                Icons.Rounded.CloudSync, 6, str(R.string.daily_backup),
                                if (backupDir == null) str(R.string.daily_backup_off_summary)
                                else str(R.string.daily_keeps_last_x_n, com.paisaplus.compose.data.BackupManager.KEEP) + str(R.string.last_backup) +
                                    (lastBackup?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(backupWhen) } ?: str(R.string.not_yet)),
                                backupDir != null,
                            ) { on -> if (on) backupFolder.launch(null) else vm.setBackupDir(null) }
                        }
                        if (backupDir != null) item { PrefRow(Icons.Rounded.Backup, 3, str(R.string.back_up_now), onClick = vm::backupNow) }
                        item { LinkRow(Icons.Rounded.Backup, 0, str(R.string.backup_restore), str(R.string.export_restore_daily_auto_backup)) { open(Routes.BACKUP) } }
                        item { LinkRow(Icons.AutoMirrored.Rounded.Message, 2, str(R.string.message_import_title), "") { open(Routes.IMPORT_MESSAGES) } }
                        item { LinkRow(Icons.Rounded.UploadFile, 2, str(R.string.import_bank_statement), "CSV") { open(Routes.IMPORT_CSV) } }
                    }

                }
            }
        }
    }

    if (currencySheet) CurrencySheet(money.currency, money.locale, { vm.setMoney(money.copy(currency = it)); currencySheet = false }) { currencySheet = false }
}

/** A row showing the current choice; tapping opens a dropdown menu. [summary] defaults to the choice's label. */
@Composable
private fun <T> DropdownRow(icon: ImageVector, hue: Int, title: String, options: List<T>, selected: T, label: (T) -> String, summary: ((T) -> String)? = null, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PrefRow(icon, hue, title, summary?.let { "${label(selected)} · ${it(selected)}" } ?: label(selected), onClick = { open = true }) {
            Icon(Icons.Rounded.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }, Modifier.align(Alignment.BottomEnd)) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(label(o)) },
                    onClick = { open = false; onPick(o) },
                    trailingIcon = if (o == selected) ({ Icon(Icons.Rounded.Check, str(R.string.selected)) }) else null,
                )
            }
        }
    }
}

/** Colour styles as swipeable cards, each previewing its own primary / secondary / tertiary / container colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemeCarousel(t: com.paisaplus.compose.data.ThemeSettings, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val dark = LocalDarkTheme.current
    val styles = schemeSummary.keys.toList()
    val schemes = remember(t.dynamicColor, t.accent, t.contrast, t.expressiveSpec, dark) { styles.map { paisaScheme(ctx, t.copy(scheme = it, surfaceTint = 0f), dark) } }
    val state = rememberCarouselState(styles.indexOf(t.scheme).coerceAtLeast(0)) { styles.size }
    HorizontalMultiBrowseCarousel(
        state, preferredItemWidth = 132.dp, Modifier.fillMaxWidth().height(148.dp),
        itemSpacing = 8.dp, contentPadding = PaddingValues(horizontal = 16.dp),
    ) { i ->
        val s = schemes[i]
        val picked = styles[i] == t.scheme
        Column(
            Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge).background(s.surfaceContainerHigh)
                .then(if (picked) Modifier.maskBorder(androidx.compose.foundation.BorderStroke(3.dp, s.primary), MaterialTheme.shapes.extraLarge) else Modifier)
                .clickable(role = Role.RadioButton) { onPick(styles[i]) }
                .semantics { contentDescription = schemeLabel(styles[i]) }
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(s.primary, s.secondary, s.tertiary).forEach { Box(Modifier.size(24.dp).background(it, CircleShape)) }
            }
            Box(Modifier.fillMaxWidth().height(36.dp).background(s.primaryContainer, MaterialTheme.shapes.medium), Alignment.Center) {
                if (picked) Icon(Icons.Rounded.Check, null, tint = s.onPrimaryContainer)
            }
            Text(schemeLabel(styles[i]), style = MaterialTheme.typography.labelLarge, color = s.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Saves once when the drag ends, not on every frame. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: String, onCommit: (Float) -> Unit) {
    val state = remember(range, steps, value) { SliderState(value, steps, range) }
    Slider(
        state = state,
        onValueChange = { state.value = it },
        modifier = Modifier.semantics { contentDescription = label },
        onValueChangeFinished = { onCommit(state.value) },
    )
}

@Composable
internal fun Swatches(colors: List<Int>, selected: Int, firstIsDefault: Boolean = false, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { i, c ->
            val isDefault = firstIsDefault && i == 0
            val fill = if (isDefault) Color(0xFF95492B) else Color(c)
            Box(
                Modifier.size(48.dp).background(fill, CircleShape)
                    .border(if (c == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable(role = Role.RadioButton) { onPick(c) }
                    .semantics { contentDescription = if (isDefault) str(R.string.default_brown) else str(R.string.colour_x, i + 1) },
                Alignment.Center,
            ) { if (c == selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySheet(selected: String, locale: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val loc = Locale.forLanguageTag(locale)
    val list = remember(q) {
        Money.currencies.filter { q.isBlank() || it.currencyCode.contains(q, true) || it.displayName.contains(q, true) || it.getSymbol(loc).contains(q) }
    }
    ModalBottomSheet(onDismiss, sheetState = rememberSheetState()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, placeholder = { Text(str(R.string.search_rupee_inr_dollar)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.currencyCode }) { c ->
                Surface(onClick = { onPick(c.currencyCode) }, color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.getSymbol(loc), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Column(Modifier.weight(1f)) {
                            Text(c.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(c.currencyCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.currencyCode == selected) Icon(Icons.Rounded.Check, str(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
