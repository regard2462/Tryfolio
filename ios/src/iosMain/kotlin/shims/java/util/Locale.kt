package java.util

import platform.Foundation.ISOCountryCodes
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.localizedStringForCountryCode
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.preferredLanguages

/**
 * Ersatz fuer java.util.Locale: Sprache, Land, Variante, Standardsprache des Geraets und Laendernamen
 * ueber NSLocale.
 */
class Locale(language: String, country: String = "", variant: String = "") {

    val language: String = language.lowercase()
    val country: String = country.uppercase()
    val variant: String = variant

    /** Wie Kotlin den Java-Getter getDisplayCountry() sieht. */
    val displayCountry: String get() = getDisplayCountry(getDefault())
    val displayLanguage: String get() = getDisplayLanguage(getDefault())
    val displayName: String get() = getDisplayName(getDefault())

    fun getDisplayCountry(): String = getDisplayCountry(getDefault())

    /** Name des Landes in der Sprache von [inLocale]; ohne Namen der Code selbst, ohne Land "". */
    fun getDisplayCountry(inLocale: Locale): String =
        if (country.isEmpty()) "" else jsAnzeigename(country, "region", inLocale.sprachKennung())

    fun getDisplayLanguage(): String = getDisplayLanguage(getDefault())

    fun getDisplayLanguage(inLocale: Locale): String =
        if (language.isEmpty()) "" else jsAnzeigename(language, "language", inLocale.sprachKennung())

    fun getDisplayName(): String = getDisplayName(getDefault())

    fun getDisplayName(inLocale: Locale): String {
        val sprache = getDisplayLanguage(inLocale)
        val land = getDisplayCountry(inLocale)
        return when {
            sprache.isEmpty() -> land
            land.isEmpty() -> sprache
            else -> "$sprache ($land)"
        }
    }

    fun toLanguageTag(): String {
        val teile = mutableListOf(language.ifEmpty { "und" })
        if (country.isNotEmpty()) teile += country
        if (variant.isNotEmpty()) teile += variant
        return teile.joinToString("-")
    }

    /** Kennung fuer NSLocale: leere Sprache (ROOT) gilt als Englisch. */
    internal fun sprachKennung(): String = if (language.isEmpty()) "en" else toLanguageTag()

    override fun toString(): String {
        val sb = StringBuilder(language)
        if (country.isNotEmpty() || variant.isNotEmpty()) sb.append('_').append(country)
        if (variant.isNotEmpty()) sb.append('_').append(variant)
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean =
        other is Locale && other.language == language && other.country == country && other.variant == variant

    override fun hashCode(): Int = (language.hashCode() * 31 + country.hashCode()) * 31 + variant.hashCode()

    enum class Category { DISPLAY, FORMAT }

    companion object {
        val ENGLISH = Locale("en")
        val FRENCH = Locale("fr")
        val GERMAN = Locale("de")
        val ITALIAN = Locale("it")
        val JAPANESE = Locale("ja")
        val KOREAN = Locale("ko")
        val CHINESE = Locale("zh")
        val SIMPLIFIED_CHINESE = Locale("zh", "CN")
        val TRADITIONAL_CHINESE = Locale("zh", "TW")
        val FRANCE = Locale("fr", "FR")
        val GERMANY = Locale("de", "DE")
        val ITALY = Locale("it", "IT")
        val JAPAN = Locale("ja", "JP")
        val KOREA = Locale("ko", "KR")
        val CHINA = SIMPLIFIED_CHINESE
        val PRC = SIMPLIFIED_CHINESE
        val TAIWAN = TRADITIONAL_CHINESE
        val UK = Locale("en", "GB")
        val US = Locale("en", "US")
        val CANADA = Locale("en", "CA")
        val CANADA_FRENCH = Locale("fr", "CA")
        val ROOT = Locale("", "")

        private var standard: Locale? = null

        /** Bevorzugte Sprache des Geraets, sonst die Region des Geraets. */
        fun getDefault(): Locale = standard ?: forLanguageTag(jsStandardSprache()).also { standard = it }

        fun getDefault(category: Category): Locale = getDefault()

        fun setDefault(newLocale: Locale) { standard = newLocale }

        fun setDefault(category: Category, newLocale: Locale) { standard = newLocale }

        /** "de-DE" -> de_DE; Skript- und Erweiterungsteile fallen weg. */
        fun forLanguageTag(languageTag: String): Locale {
            val teile = languageTag.replace('_', '-').split('-').filter { it.isNotEmpty() }
            if (teile.isEmpty()) return ROOT
            val sprache = teile[0].takeIf { it.length in 2..8 && it.all { c -> c.isLetter() } && !it.equals("und", true) } ?: ""
            var land = ""
            var variante = ""
            for (t in teile.drop(1)) {
                if (t.length == 1) break  // Erweiterung (-u-, -x-)
                when {
                    land.isEmpty() && t.length == 4 && t.all { it.isLetter() } -> {}  // Skript
                    land.isEmpty() && ((t.length == 2 && t.all { it.isLetter() }) || (t.length == 3 && t.all { it.isDigit() })) -> land = t
                    variante.isEmpty() && t.length >= 4 -> variante = t
                }
            }
            return Locale(sprache, land, variante)
        }

        fun getAvailableLocales(): Array<Locale> = arrayOf(ROOT, ENGLISH, US, UK, GERMAN, GERMANY, FRENCH, FRANCE, ITALIAN, ITALY)

        fun getISOCountries(): Array<String> = jsLaendercodes().split(',').filter { it.length == 2 }.toTypedArray()
    }
}

private fun jsStandardSprache(): String =
    (platform.Foundation.NSLocale.preferredLanguages.firstOrNull() as? String ?: platform.Foundation.NSLocale.currentLocale.localeIdentifier).replace('_', '-')

private fun jsAnzeigename(code: String, art: String, sprache: String): String {
    val l = platform.Foundation.NSLocale(localeIdentifier = sprache)
    val n = if (art == "region") l.localizedStringForCountryCode(code) else l.localizedStringForLanguageCode(code)
    return n ?: code
}

private fun jsLaendercodes(): String = platform.Foundation.NSLocale.ISOCountryCodes.joinToString(",")
