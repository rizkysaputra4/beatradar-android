package com.boimin.beatradar.i18n

import android.content.Context
import android.icu.text.PluralRules
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A downloaded language: string resource name → text; a plural form is `<name>#<quantity>`. It arrives in [chunks]
 * (one per screen or area, see [LanguageSync]); [loaded] are the ones here so far. Strings of a chunk not loaded yet
 * show in English.
 */
@Serializable
data class LanguagePack(
    val code: String,
    val nativeName: String,
    val version: Int,
    val strings: Map<String, String>,
    val chunks: List<String> = emptyList(),
    val loaded: Set<String> = emptySet(),
) {
    val locale: Locale get() = Locale.forLanguageTag(code)
    val missing: List<String> get() = chunks.filter { it !in loaded }
}

/**
 * The app's text. English lives in `res/values/strings*.xml` and is the only language built in; any other comes as one
 * [LanguagePack] from the server ([LanguageSync]), and only that one is kept on the phone. Every string the user sees
 * goes through [str] / [strPlural] (composables) or [Context.str] / [Context.strPlural], which look the resource's name
 * up in the pack and fall back to the English resource.
 */
object Strings {
    private val _pack = MutableStateFlow<LanguagePack?>(null)
    /** The language in use, or null for English. */
    val pack: StateFlow<LanguagePack?> = _pack

    private val names = ConcurrentHashMap<Int, String>()
    private var rules: Pair<String, PluralRules>? = null

    private fun file(context: Context) = File(context.filesDir, "language/pack.json")

    /** Loads the kept pack, before anything is drawn, so the first screen is already in the right language. */
    fun load(context: Context) {
        val f = file(context)
        _pack.value = if (f.exists()) runCatching { I18nJson.decodeFromString(LanguagePack.serializer(), f.readText()) }.getOrNull() else null
    }

    /** Keeps [pack] as the only language on the phone (null = English, nothing kept). */
    internal fun use(context: Context, pack: LanguagePack?) {
        val f = file(context)
        if (pack == null) {
            f.delete()
        } else {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "pack.json.tmp")
            tmp.writeText(I18nJson.encodeToString(LanguagePack.serializer(), pack))
            tmp.renameTo(f)
        }
        _pack.value = pack
    }

    private fun name(context: Context, id: Int): String = names.getOrPut(id) { context.resources.getResourceEntryName(id) }

    fun get(context: Context, pack: LanguagePack?, @StringRes id: Int, args: Array<out Any?>): String {
        val text = pack?.strings?.get(name(context, id)) ?: return context.getString(id, *args)
        return if (args.isEmpty()) text else runCatching { String.format(pack.locale, text, *args) }.getOrElse { context.getString(id, *args) }
    }

    fun plural(context: Context, pack: LanguagePack?, @PluralsRes id: Int, count: Int, args: Array<out Any?>): String {
        val english = { context.resources.getQuantityString(id, count, *args) }
        if (pack == null) return english()
        val name = name(context, id)
        val quantity = pluralRules(pack).select(count.toDouble())
        val text = pack.strings["$name#$quantity"] ?: pack.strings["$name#other"] ?: return english()
        return runCatching { String.format(pack.locale, text, *args) }.getOrElse { english() }
    }

    private fun pluralRules(pack: LanguagePack): PluralRules {
        rules?.let { (code, r) -> if (code == pack.code) return r }
        return PluralRules.forLocale(pack.locale).also { rules = pack.code to it }
    }
}

/** The text of string resource [id] in the app's language, formatted with [args]. Redraws when the language changes. */
@Composable
fun str(@StringRes id: Int, vararg args: Any?): String {
    val pack by Strings.pack.collectAsState()
    return Strings.get(LocalContext.current, pack, id, args)
}

/** The plural form of [id] for [count] in the app's language, formatted with [args] (usually including [count]). */
@Composable
fun strPlural(@PluralsRes id: Int, count: Int, vararg args: Any?): String {
    val pack by Strings.pack.collectAsState()
    return Strings.plural(LocalContext.current, pack, id, count, args)
}

/** [str] outside Compose: notifications, the widget, toasts, share text. */
fun Context.str(@StringRes id: Int, vararg args: Any?): String = Strings.get(this, Strings.pack.value, id, args)

/** [strPlural] outside Compose. */
fun Context.strPlural(@PluralsRes id: Int, count: Int, vararg args: Any?): String = Strings.plural(this, Strings.pack.value, id, count, args)

/** The locale text is shown in: the pack's, or English. For number and date formatting that should match the words. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = Strings.pack.value?.locale ?: Locale.ENGLISH
