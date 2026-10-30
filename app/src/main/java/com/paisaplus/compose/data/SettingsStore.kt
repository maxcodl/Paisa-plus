package com.paisaplus.compose.data

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class BgMode { THEME, SOLID, GRADIENT, IMAGE }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true, // Material You (wallpaper colours) on Android 12+; the brand seed elsewhere
    val amoled: Boolean = false,
    /** ARGB seed used when Monet is off; 0 = the built-in brand palette. */
    val accent: Int = 0,
    /** Multiplier on every corner radius (0 = square, 1 = default, 1.5 = rounder). */
    val corners: Float = 1f,
    val navLabels: Boolean = true,
    val haptics: Boolean = true,
    val bg: BgMode = BgMode.THEME,
    val bgColor: Int = 0xFF95492B.toInt(),
    /** content:// uri with a persisted read grant; device-local. */
    val bgImage: String = "",
    /** 0..0.9 black scrim over SOLID/GRADIENT/IMAGE backgrounds. */
    val bgDim: Float = 0.4f,
    /** Squiggly (wavy) progress bars; off = flat, still bars. */
    val wavy: Boolean = true,
    /** Liquid-glass cards and tab bar (refraction on API 33+, blur on 31+). */
    val glass: Boolean = true,
    /** Multipliers on each glass surface's designed blur / refraction / tint (1 = default). */
    val glassBlur: Float = 1f,
    val glassRefraction: Float = 1f,
    val glassTint: Float = 1f,
    val icons: IconStyle = IconStyle.COLORFUL,
    /** M3 scheme variant (com.materialkolor.PaletteStyle name): TonalSpot, Vibrant, Expressive, … */
    val scheme: String = "TonalSpot",
    /** M3 contrast level: -1 reduced, 0 standard, 0.5 medium, 1 high. */
    val contrast: Float = 0f,
    /** 2025 (M3 Expressive) colour spec instead of 2021. */
    val expressiveSpec: Boolean = true,
    /** 0..1: how much of the accent's chroma floods background and surfaces. */
    val surfaceTint: Float = 0f,
    val font: AppFont = AppFont.GOOGLE_SANS_FLEX,
)

enum class AppFont(val label: String) { GOOGLE_SANS_FLEX("Google Sans Flex"), OUTFIT("Outfit") }

/** Shaped icon containers: fixed multi-colour hues, the theme's (Monet) containers, or plain. */
enum class IconStyle(val label: String) { COLORFUL(str(R.string.colourful)), MONET(str(R.string.monet)), PLAIN(str(R.string.plain)) }

/** Number formatting: ISO 4217 currency + BCP-47 locale (default ₹ / en-IN). */
data class MoneyFormat(val currency: String = "INR", val locale: String = "en-IN")

data class LockSettings(val enabled: Boolean = false, val afterMinutes: Int = 1, val secure: Boolean = false)

/** APP: the app's accent colours under glass. NOTHING: flat monochrome card with a red accent, like Nothing OS widgets. */
enum class WidgetStyle { APP, NOTHING }

/** Nothing style's accent: Nothing red or the wallpaper's (Monet) primary. */
enum class WidgetAccent { RED, WALLPAPER }

/** Home-screen widget look: [opacity] 0.2–1 (1 = solid), [sheen] glass highlight (APP only), [addButton] on the balance widget. */
data class WidgetPrefs(val style: WidgetStyle = WidgetStyle.APP, val opacity: Float = 0.85f, val sheen: Boolean = true, val addButton: Boolean = true, val accent: WidgetAccent = WidgetAccent.WALLPAPER)

/** Message import: which notification sources to read; [wifiOnly] for the optional model download (~56 MB). */
/** [apps]: banking apps whose notifications are read as bank alerts (Settings › Sources › Banking apps). */
data class MessagePrefs(
    val sms: Boolean = true, val gmail: Boolean = true, val wifiOnly: Boolean = true, val autoAdd: Boolean = true,
    val apps: Set<String> = com.paisaplus.compose.messages.AutoAdd.bankAppPackages,
)

enum class UpdateFrequency(val days: Int) { OFF(0), DAILY(1), WEEKLY(7), MONTHLY(30) }

/** In-app updates from GitHub releases. [notified]: last version a notification was shown for (once per version). */
data class UpdatePrefs(val frequency: UpdateFrequency = UpdateFrequency.WEEKLY, val beta: Boolean = false, val lastCheck: Long = 0, val notified: String = "", val prompted: String = "", val shizuku: Boolean = false)

private val Context.store by preferencesDataStore("settings")

// Key names match the Flutter app's settings box (lib/src/core/constants.dart).
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val modeKey = stringPreferencesKey("key_theme_mode")
    private val dynamicKey = booleanPreferencesKey("keydynamicColor")
    private val amoledKey = booleanPreferencesKey("key_amoled")
    private val hideBalanceKey = booleanPreferencesKey("key_hide_balance")
    private val accentKey = intPreferencesKey("key_accent")
    private val cornersKey = floatPreferencesKey("key_corners")
    private val navLabelsKey = booleanPreferencesKey("key_nav_labels")
    private val hapticsKey = booleanPreferencesKey("key_haptics")
    private val bgKey = stringPreferencesKey("key_background")
    private val bgColorKey = intPreferencesKey("key_background_color")
    private val bgImageKey = stringPreferencesKey("key_background_image")
    private val bgDimKey = floatPreferencesKey("key_background_dim")
    private val wavyKey = booleanPreferencesKey("key_wavy")
    private val glassKey = booleanPreferencesKey("key_glass")
    private val iconsKey = stringPreferencesKey("key_icon_style")
    private val glassBlurKey = floatPreferencesKey("key_glass_blur")
    private val glassRefractionKey = floatPreferencesKey("key_glass_refraction")
    private val glassTintKey = floatPreferencesKey("key_glass_tint")
    private val schemeKey = stringPreferencesKey("key_scheme_style")
    private val contrastKey = floatPreferencesKey("key_contrast")
    private val specKey = booleanPreferencesKey("key_expressive_spec")
    private val surfaceTintKey = floatPreferencesKey("key_surface_tint")
    private val fontKey = stringPreferencesKey("key_font")
    private val userImageKey = stringPreferencesKey("key_user_image")
    private val localeKey = stringPreferencesKey("key_locale")
    private val tabsKey = stringPreferencesKey("key_tabs")
    private val balanceModesKey = stringPreferencesKey("key_balance_modes")
    private val lockKey = booleanPreferencesKey("key_lock")
    private val lockMinutesKey = intPreferencesKey("key_lock_minutes")
    private val secureKey = booleanPreferencesKey("key_secure")
    private val excludeTransfersKey = booleanPreferencesKey("key_exclude_transfers")
    private val monthStartKey = intPreferencesKey("key_month_start")
    private val widgetOpacityKey = floatPreferencesKey("key_widget_opacity")
    private val widgetStyleKey = stringPreferencesKey("key_widget_style")
    private val widgetSheenKey = booleanPreferencesKey("key_widget_sheen")
    private val widgetAddKey = booleanPreferencesKey("key_widget_add_button")
    private val widgetAccentKey = stringPreferencesKey("key_widget_accent")
    private val msgSmsKey = booleanPreferencesKey("key_msg_sms")
    private val msgGmailKey = booleanPreferencesKey("key_msg_gmail")
    private val modelWifiKey = booleanPreferencesKey("key_model_wifi_only")
    private val msgAutoKey = booleanPreferencesKey("key_msg_auto_add")
    private val autoRepairKey = booleanPreferencesKey("key_auto_add_repaired_v1")
    private val updFreqKey = stringPreferencesKey("key_update_frequency")
    private val updBetaKey = booleanPreferencesKey("key_update_beta")
    private val updLastKey = longPreferencesKey("key_update_last_check")
    private val updNotifiedKey = stringPreferencesKey("key_update_notified")
    private val updPromptedKey = stringPreferencesKey("key_update_prompted")
    private val updShizukuKey = booleanPreferencesKey("key_update_shizuku")

    private val userNameKey = stringPreferencesKey("key_user_name")
    private val currencyKey = stringPreferencesKey("key_currency")
    private val onboardedKey = booleanPreferencesKey("key_onboarded")

    val hideBalance = ctx.store.data.map { it[hideBalanceKey] ?: false }
    suspend fun setHideBalance(v: Boolean) = ctx.store.edit { it[hideBalanceKey] = v }

    val excludeTransfers = ctx.store.data.map { it[excludeTransfersKey] ?: true }
    suspend fun setExcludeTransfers(v: Boolean) = ctx.store.edit { it[excludeTransfersKey] = v }

    /** Day of the month a budget month starts on (1–31; see periodOf). */
    val monthStart = ctx.store.data.map { (it[monthStartKey] ?: 1).coerceIn(1, 31) }
    suspend fun setMonthStart(day: Int) = ctx.store.edit { it[monthStartKey] = day.coerceIn(1, 31) }

    val userName = ctx.store.data.map { it[userNameKey].orEmpty() }
    /** Absolute path of the copied profile picture; empty = none. */
    val userImage = ctx.store.data.map { it[userImageKey].orEmpty() }
    suspend fun setUserImage(path: String) = ctx.store.edit { it[userImageKey] = path }
    suspend fun setUserName(v: String) = ctx.store.edit { it[userNameKey] = v }

    val money = ctx.store.data.map { MoneyFormat(it[currencyKey]?.ifEmpty { null } ?: "INR", it[localeKey]?.ifEmpty { null } ?: "en-IN") }
    suspend fun setMoney(m: MoneyFormat) = ctx.store.edit { it[currencyKey] = m.currency; it[localeKey] = m.locale }

    /** Balance card / widget pages, in order. */
    val balanceModes = ctx.store.data.map { BalanceMode.parse(it[balanceModesKey]) }
    suspend fun setBalanceModes(m: List<BalanceMode>) = ctx.store.edit { it[balanceModesKey] = BalanceMode.format(m) }

    private val msgAppsKey = androidx.datastore.preferences.core.stringSetPreferencesKey("key_msg_apps")
    val messages = ctx.store.data.map { MessagePrefs(it[msgSmsKey] ?: true, it[msgGmailKey] ?: true, it[modelWifiKey] ?: true, it[msgAutoKey] ?: true, it[msgAppsKey] ?: com.paisaplus.compose.messages.AutoAdd.bankAppPackages) }
    suspend fun setMessages(m: MessagePrefs) = ctx.store.edit { it[msgSmsKey] = m.sms; it[msgGmailKey] = m.gmail; it[modelWifiKey] = m.wifiOnly; it[msgAutoKey] = m.autoAdd; it[msgAppsKey] = m.apps }

    private val rechargeCatKey = booleanPreferencesKey("key_cat_recharge_v1")
    val rechargeCategoryAdded = ctx.store.data.map { it[rechargeCatKey] ?: false }
    suspend fun setRechargeCategoryAdded() = ctx.store.edit { it[rechargeCatKey] = true }

    val autoAddRepaired =ctx.store.data.map { it[autoRepairKey] ?: false }
    suspend fun setAutoAddRepaired() = ctx.store.edit { it[autoRepairKey] = true }

    val updates = ctx.store.data.map { p ->
        UpdatePrefs(
            UpdateFrequency.entries.firstOrNull { it.name == p[updFreqKey] } ?: UpdateFrequency.WEEKLY,
            p[updBetaKey] ?: false, p[updLastKey] ?: 0L, p[updNotifiedKey].orEmpty(), p[updPromptedKey].orEmpty(), p[updShizukuKey] ?: false,
        )
    }
    suspend fun setUpdates(u: UpdatePrefs) = ctx.store.edit {
        it[updFreqKey] = u.frequency.name; it[updBetaKey] = u.beta; it[updLastKey] = u.lastCheck; it[updNotifiedKey] = u.notified; it[updPromptedKey] = u.prompted; it[updShizukuKey] = u.shizuku
    }

    val widget = ctx.store.data.map { p ->
        WidgetPrefs(
            style = WidgetStyle.entries.firstOrNull { it.name == p[widgetStyleKey] } ?: WidgetStyle.APP,
            opacity = (p[widgetOpacityKey] ?: 0.85f).coerceIn(0.2f, 1f),
            sheen = p[widgetSheenKey] ?: true,
            addButton = p[widgetAddKey] ?: true,
            accent = WidgetAccent.entries.firstOrNull { it.name == p[widgetAccentKey] } ?: WidgetAccent.WALLPAPER,
        )
    }
    suspend fun setWidget(w: WidgetPrefs) = ctx.store.edit {
        it[widgetStyleKey] = w.style.name
        it[widgetOpacityKey] = w.opacity
        it[widgetSheenKey] = w.sheen
        it[widgetAddKey] = w.addButton
        it[widgetAccentKey] = w.accent.name
    }

    /** Bottom-bar tabs, comma-separated Tab names; null = default set. */
    val tabs = ctx.store.data.map { it[tabsKey] }
    suspend fun setTabs(v: String) = ctx.store.edit { it[tabsKey] = v }

    val lock = ctx.store.data.map { LockSettings(it[lockKey] ?: false, it[lockMinutesKey] ?: 1, it[secureKey] ?: false) }
    suspend fun setLock(l: LockSettings) = ctx.store.edit { it[lockKey] = l.enabled; it[lockMinutesKey] = l.afterMinutes; it[secureKey] = l.secure }

    private val automationIntroKey = booleanPreferencesKey("key_automation_intro_seen")
    val automationIntroSeen = ctx.store.data.map { it[automationIntroKey] ?: false }
    suspend fun finishAutomationIntro() = ctx.store.edit { it[automationIntroKey] = true }

    val onboarded = ctx.store.data.map { it[onboardedKey] ?: false }
    suspend fun setOnboarded(v: Boolean) = ctx.store.edit { it[onboardedKey] = v }

    // Device-local: never exported or overwritten by a restore.
    private val backupDirKey = stringPreferencesKey("key_backup_dir")
    private val lastBackupKey = longPreferencesKey("key_last_backup")
    // Image files live in app storage / device URIs, not in the backup.
    private val local = setOf(automationIntroKey.name, backupDirKey.name, lastBackupKey.name, "key_background_image", "key_user_image")

    val backupDir = ctx.store.data.map { it[backupDirKey]?.ifEmpty { null } }
    suspend fun setBackupDir(uri: String?) = ctx.store.edit { if (uri == null) it.remove(backupDirKey) else it[backupDirKey] = uri }
    val lastBackup = ctx.store.data.map { it[lastBackupKey] }
    suspend fun setLastBackup(millis: Long) = ctx.store.edit { it[lastBackupKey] = millis }

    /** Every portable preference, tagged by type so a restore recreates the same keys. */
    suspend fun export(): JsonObject = buildJsonObject {
        ctx.store.data.first().asMap().forEach { (k, v) ->
            if (k.name in local) return@forEach
            if (v is Set<*>) { // banking apps to read
                put(k.name, buildJsonObject { put("type", "stringset"); put("value", kotlinx.serialization.json.JsonArray(v.map { JsonPrimitive(it.toString()) })) })
                return@forEach
            }
            val tagged = when (v) {
                is Boolean -> "bool" to JsonPrimitive(v)
                is String -> "string" to JsonPrimitive(v)
                is Int -> "int" to JsonPrimitive(v)
                is Long -> "long" to JsonPrimitive(v)
                is Float -> "float" to JsonPrimitive(v)
                is Double -> "double" to JsonPrimitive(v)
                else -> return@forEach // string sets / byte arrays: none used
            }
            put(k.name, buildJsonObject { put("type", tagged.first); put("value", tagged.second) })
        }
    }

    /** Replaces portable preferences with [o] (from [export]); device-local keys survive. */
    suspend fun import(o: JsonObject) = ctx.store.edit { p ->
        p.asMap().keys.filter { it.name !in local }.forEach { p.remove(it) }
        o.forEach { (name, e) ->
            if (name in local) return@forEach
            val t = (e as? JsonObject) ?: return@forEach
            (t["value"] as? kotlinx.serialization.json.JsonArray)?.let { a ->
                if ((t["type"] as? JsonPrimitive)?.content == "stringset") p[androidx.datastore.preferences.core.stringSetPreferencesKey(name)] = a.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
                return@forEach
            }
            val v = t["value"] as? JsonPrimitive ?: return@forEach
            when ((t["type"] as? JsonPrimitive)?.content) {
                "bool" -> v.booleanOrNull?.let { p[booleanPreferencesKey(name)] = it }
                "string" -> p[stringPreferencesKey(name)] = v.content
                "int" -> v.intOrNull?.let { p[intPreferencesKey(name)] = it }
                "long" -> v.longOrNull?.let { p[longPreferencesKey(name)] = it }
                "float" -> v.floatOrNull?.let { p[floatPreferencesKey(name)] = it }
                "double" -> v.doubleOrNull?.let { p[doublePreferencesKey(name)] = it }
            }
        }
    }

    val theme = ctx.store.data.map { p ->
        ThemeSettings(
            mode = p[modeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[dynamicKey] ?: true,
            amoled = p[amoledKey] ?: false,
            accent = p[accentKey] ?: 0,
            corners = p[cornersKey] ?: 1f,
            navLabels = p[navLabelsKey] ?: true,
            haptics = p[hapticsKey] ?: true,
            bg = p[bgKey]?.let { runCatching { BgMode.valueOf(it) }.getOrNull() } ?: BgMode.THEME,
            bgColor = p[bgColorKey] ?: 0xFF95492B.toInt(),
            bgImage = p[bgImageKey].orEmpty(),
            bgDim = p[bgDimKey] ?: 0.4f,
            wavy = p[wavyKey] ?: true,
            glass = p[glassKey] ?: true,
            glassBlur = p[glassBlurKey] ?: 1f,
            glassRefraction = p[glassRefractionKey] ?: 1f,
            glassTint = p[glassTintKey] ?: 1f,
            icons = p[iconsKey]?.let { runCatching { IconStyle.valueOf(it) }.getOrNull() } ?: IconStyle.COLORFUL,
            scheme = p[schemeKey] ?: "TonalSpot",
            contrast = (p[contrastKey] ?: 0f).coerceIn(-1f, 1f),
            expressiveSpec = p[specKey] ?: true,
            surfaceTint = (p[surfaceTintKey] ?: 0f).coerceIn(0f, 1f),
            font = p[fontKey]?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: AppFont.GOOGLE_SANS_FLEX,
        )
    }

    suspend fun setTheme(t: ThemeSettings) = ctx.store.edit {
        it[modeKey] = t.mode.name
        it[dynamicKey] = t.dynamicColor
        it[amoledKey] = t.amoled
        it[accentKey] = t.accent
        it[cornersKey] = t.corners
        it[navLabelsKey] = t.navLabels
        it[hapticsKey] = t.haptics
        it[bgKey] = t.bg.name
        it[bgColorKey] = t.bgColor
        it[bgImageKey] = t.bgImage
        it[bgDimKey] = t.bgDim
        it[wavyKey] = t.wavy
        it[glassKey] = t.glass
        it[glassBlurKey] = t.glassBlur
        it[glassRefractionKey] = t.glassRefraction
        it[glassTintKey] = t.glassTint
        it[iconsKey] = t.icons.name
        it[schemeKey] = t.scheme
        it[contrastKey] = t.contrast
        it[specKey] = t.expressiveSpec
        it[surfaceTintKey] = t.surfaceTint
        it[fontKey] = t.font.name
    }
}
