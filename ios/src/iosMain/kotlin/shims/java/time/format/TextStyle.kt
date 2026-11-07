package java.time.format

import java.util.Locale

enum class TextStyle {
    FULL, FULL_STANDALONE, SHORT, SHORT_STANDALONE, NARROW, NARROW_STANDALONE;

    fun isStandalone(): Boolean = (ordinal and 1) == 1
    fun asStandalone(): TextStyle = entries[ordinal or 1]
    fun asNormal(): TextStyle = entries[ordinal and 1.inv()]
}

/**
 * Monats-, Wochentags-, AM/PM- und Aera-Namen wie in Java (CLDR-Daten von Java 17 und Android): Deutsch und
 * Englisch aus Tabellen, andere Sprachen ueber NSDateFormatter.
 */
internal object Namen {
    private val DE_MONATE = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    private val DE_MONATE_KURZ = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez.")
    private val DE_MONATE_KURZ_ALLEIN = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
    private val DE_TAGE = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val DE_TAGE_KURZ = listOf("Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa.", "So.")
    private val DE_TAGE_KURZ_ALLEIN = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    private val DE_TAGE_SCHMAL = listOf("M", "D", "M", "D", "F", "S", "S")

    private val EN_MONATE = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val EN_MONATE_KURZ = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val EN_TAGE = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val EN_TAGE_KURZ = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val EN_TAGE_SCHMAL = listOf("M", "T", "W", "T", "F", "S", "S")

    private val SCHMAL_MONATE = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

    /** en_GB kuerzt September seit CLDR 38 zu "Sept" (Java 17, Android 12). */
    private fun britisch(locale: Locale): Boolean = locale.language == "en" && locale.country == "GB"

    private fun grundform(stil: TextStyle) = stil.asNormal()

    /** [monat] 1 bis 12. */
    fun monat(monat: Int, stil: TextStyle, locale: Locale): String {
        val i = monat - 1
        return when (locale.language) {
            "de" -> when (grundform(stil)) {
                TextStyle.FULL -> DE_MONATE[i]
                TextStyle.SHORT -> if (stil.isStandalone()) DE_MONATE_KURZ_ALLEIN[i] else DE_MONATE_KURZ[i]
                else -> SCHMAL_MONATE[i]
            }
            "en", "" -> when (grundform(stil)) {
                TextStyle.FULL -> EN_MONATE[i]
                TextStyle.SHORT -> if (monat == 9 && britisch(locale)) "Sept" else EN_MONATE_KURZ[i]
                else -> SCHMAL_MONATE[i]
            }
            else -> jsMonat(locale.toLanguageTag(), monat, jsStil(stil), stil.isStandalone())
        }
    }

    /** [tag] 1 (Montag) bis 7 (Sonntag). */
    fun wochentag(tag: Int, stil: TextStyle, locale: Locale): String {
        val i = tag - 1
        return when (locale.language) {
            "de" -> when (grundform(stil)) {
                TextStyle.FULL -> DE_TAGE[i]
                TextStyle.SHORT -> if (stil.isStandalone()) DE_TAGE_KURZ_ALLEIN[i] else DE_TAGE_KURZ[i]
                else -> DE_TAGE_SCHMAL[i]
            }
            "en", "" -> when (grundform(stil)) {
                TextStyle.FULL -> EN_TAGE[i]
                TextStyle.SHORT -> EN_TAGE_KURZ[i]
                else -> EN_TAGE_SCHMAL[i]
            }
            else -> jsWochentag(locale.toLanguageTag(), tag, jsStil(stil), stil.isStandalone())
        }
    }

    /** [wert] 0 = vormittags, 1 = nachmittags. */
    fun ampm(wert: Int, stil: TextStyle, locale: Locale): String = when {
        grundform(stil) == TextStyle.NARROW -> if (wert == 0) "a" else "p"
        else -> if (wert == 0) "AM" else "PM"
    }

    /** [wert] 0 = vor Christus, 1 = nach Christus. */
    fun aera(wert: Int, stil: TextStyle, locale: Locale): String = when (locale.language) {
        "de" -> when (grundform(stil)) {
            TextStyle.FULL -> if (wert == 0) "v. Chr." else "n. Chr."
            TextStyle.SHORT -> if (wert == 0) "v. Chr." else "n. Chr."
            else -> if (wert == 0) "v. Chr." else "n. Chr."
        }
        else -> when (grundform(stil)) {
            TextStyle.FULL -> if (wert == 0) "Before Christ" else "Anno Domini"
            TextStyle.SHORT -> if (wert == 0) "BC" else "AD"
            else -> if (wert == 0) "B" else "A"
        }
    }

    private fun jsStil(stil: TextStyle): String = when (grundform(stil)) {
        TextStyle.FULL -> "long"
        TextStyle.SHORT -> "short"
        else -> "narrow"
    }
}

private fun jsMonat(sprache: String, monat: Int, stil: String, allein: Boolean): String {
    val f = platform.Foundation.NSDateFormatter().apply { locale = platform.Foundation.NSLocale(localeIdentifier = sprache) }
    val namen = when (stil) {
        "long" -> if (allein) f.standaloneMonthSymbols else f.monthSymbols
        "short" -> if (allein) f.shortStandaloneMonthSymbols else f.shortMonthSymbols
        else -> if (allein) f.veryShortStandaloneMonthSymbols else f.veryShortMonthSymbols
    }
    return namen.getOrNull(monat - 1) as? String ?: monat.toString()
}

// tag 1 = Montag wie in java.time; NSDateFormatter beginnt mit Sonntag
private fun jsWochentag(sprache: String, tag: Int, stil: String, allein: Boolean): String {
    val f = platform.Foundation.NSDateFormatter().apply { locale = platform.Foundation.NSLocale(localeIdentifier = sprache) }
    val namen = when (stil) {
        "long" -> if (allein) f.standaloneWeekdaySymbols else f.weekdaySymbols
        "short" -> if (allein) f.shortStandaloneWeekdaySymbols else f.shortWeekdaySymbols
        else -> if (allein) f.veryShortStandaloneWeekdaySymbols else f.veryShortWeekdaySymbols
    }
    return namen.getOrNull(tag % 7) as? String ?: tag.toString()
}