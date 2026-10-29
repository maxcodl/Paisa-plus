package com.paisaplus.compose

import androidx.annotation.PluralsRes
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale

/**
 * App language: "" follows the system (and Android 13+ per-app language), otherwise a BCP-47 tag.
 * Kept in plain SharedPreferences because it must be readable synchronously in attachBaseContext.
 */
object L10n {
    val languages = listOf("" to "System", "en" to "English", "pt-BR" to "Português (Brasil)")
    private var res: Resources? = null

    fun tag(ctx: Context): String = ctx.getSharedPreferences("l10n", Context.MODE_PRIVATE).getString("lang", "").orEmpty()

    /** [base] with the chosen language applied; also sets the default Locale so dates/numbers follow it. */
    fun wrap(base: Context): Context {
        val t = tag(base)
        val ctx = if (t.isEmpty()) base else {
            val locale = Locale.forLanguageTag(t)
            Locale.setDefault(locale)
            base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
        }
        res = ctx.resources
        return ctx
    }

    /** Saves [tag] and restarts the app so every screen, enum label and widget reloads in that language. */
    fun set(ctx: Context, tag: String) {
        ctx.getSharedPreferences("l10n", Context.MODE_PRIVATE).edit().putString("lang", tag).commit()
        val app = ctx.applicationContext
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Runtime.getRuntime().exit(0)
    }

    internal fun plural(@PluralsRes id: Int, n: Int): String = res?.getQuantityString(id, n, n) ?: ""

    internal fun get(@StringRes id: Int, args: Array<out Any?>): String =
        res?.let { if (args.isEmpty()) it.getString(id) else it.getString(id, *args) } ?: "" // "" only in JVM unit tests
}

/** Localized string, callable from composables, view models and workers alike. */
fun str(@StringRes id: Int, vararg args: Any?): String = L10n.get(id, args)

/** Localized quantity string ("1 card" / "5 cards"), [n] as its %1$d. */
fun plural(@PluralsRes id: Int, n: Int): String = L10n.plural(id, n)
