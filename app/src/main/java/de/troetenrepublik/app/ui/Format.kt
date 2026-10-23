package de.troetenrepublik.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// Zahlen und Daten in der Schreibweise der gewaehlten Sprache: Deutsch "1.234,56 €", "12,34 %",
// "22.09.2026"; Englisch "€1,234.56", "12.34%", "22 Sep 2026".
// Anzeigewaehrung: money, price, signedMoney und compactMoney zeigen die Betraege in der gewaehlten Waehrung
// (basis, Vorgabe Euro); ein Tipp schaltet weiter auf US-Dollar und Euro (stufen), umgerechnet mit dem aktuellen
// Kurs. Was gebucht, eingegeben, versteuert oder abgerechnet wird, bleibt in Euro und nutzt euro, priceEuro,
// signedEuro und compactEuro.
object Fmt {
    private val loc: Locale get() = I18n.locale
    private val de: Boolean get() = loc.language == "de"

    // Dezimaltrennzeichen fuer Eingabefelder.
    val decimalSep: Char get() = if (de) ',' else '.'
    val groupSep: Char get() = if (de) '.' else ','

    private fun withSymbol(num: String, symbol: String, negative: Boolean): String {
        val sign = if (negative) "−" else ""
        return if (de) "$sign$num $symbol" else if (symbol.length == 1) "$sign$symbol$num" else "$sign$num $symbol"
    }

    // Stufe der Anzeige in stufen: 0 = gewaehlte Waehrung.
    var stufe by mutableStateOf(0)

    // US-Dollar je Euro; 0 = noch kein Kurs.
    var usdJeEuro by mutableStateOf(0.0)

    // Gewaehlte Waehrung der Anzeige und ihr Kurs je Euro (0 = noch keiner; bis dahin Euro).
    var basis by mutableStateOf("EUR")
    var basisJeEuro by mutableStateOf(0.0)

    // Reihenfolge beim Antippen: gewaehlte Waehrung, US-Dollar, Euro (in Euro bucht die App), ohne Doppelte.
    val stufen: List<String> get() = listOf(basis, "USD", "EUR").distinct()

    // Einheiten von waehrung je Euro; 0 = kein Kurs.
    fun jeEuro(waehrung: String): Double = when (waehrung) {
        "EUR" -> 1.0
        "USD" -> usdJeEuro
        basis -> basisJeEuro
        else -> 0.0
    }

    // Waehrung, in der money und price gerade zeigen; fehlt ein Kurs, die naechste mit Kurs.
    val anzeigeWaehrung: String get() =
        stufen.getOrNull(stufe)?.takeIf { jeEuro(it) > 0 } ?: basis.takeIf { jeEuro(it) > 0 } ?: "EUR"

    // Angezeigt wird gerade nicht die gewaehlte Waehrung.
    val altAktiv: Boolean get() = anzeigeWaehrung != (basis.takeIf { jeEuro(it) > 0 } ?: "EUR")

    // Die naechste Stufe beim Antippen waere wieder die gewaehlte Waehrung.
    val letzteStufe: Boolean get() = stufe >= stufen.size - 1

    val inUsd: Boolean get() = anzeigeWaehrung == "USD"

    val anzeigeJeEuro: Double get() = jeEuro(anzeigeWaehrung)

    val symbol: String get() = Derivatives.currencySymbol(anzeigeWaehrung)

    // Betrag in Euro in der Anzeigewaehrung.
    fun anzeige(v: Double): Double = v * anzeigeJeEuro

    fun money(v: Double, digits: Int = 2): String {
        val a = anzeige(v)
        return withSymbol(String.format(loc, "%,.${digits}f", abs(a)), symbol, a < 0)
    }

    // Betrag, der schon in der Waehrung mit dem Zeichen currency vorliegt (Heimatwaehrung eines Werts).
    fun moneyIn(v: Double, currency: String, digits: Int = 2): String =
        withSymbol(String.format(loc, "%,.${digits}f", abs(v)), currency, v < 0)

    // Immer in Euro: Buchungen, Eingaben, Steuern, Belege.
    fun euro(v: Double, digits: Int = 2): String =
        withSymbol(String.format(loc, "%,.${digits}f", abs(v)), "€", v < 0)

    // Ohne Waehrungszeichen.
    fun num(v: Double, digits: Int = 2): String = String.format(loc, "%,.${digits}f", v)

    // Kurse: kleine Kurse brauchen mehr Nachkommastellen. Ohne currency ist der Kurs in Euro und wird in der
    // Anzeigewaehrung gezeigt; mit currency steht er bereits in dieser Waehrung (Heimatboerse, Kennzahlen).
    fun price(v: Double, currency: String? = null): String =
        if (currency == null) priceIn(anzeige(v), symbol) else priceIn(v, currency)

    // Kurs in Euro, unabhaengig von der Anzeigewaehrung.
    fun priceEuro(v: Double, currency: String = "€"): String = priceIn(v, currency)

    private fun priceIn(v: Double, currency: String): String {
        val digits = when {
            v == 0.0 -> 2
            abs(v) >= 1 -> 2
            abs(v) >= 0.01 -> 4
            else -> 6
        }
        return withSymbol(String.format(loc, "%,.${digits}f", abs(v)), currency, v < 0)
    }

    fun signedMoney(v: Double): String = (if (v > 0) "+" else "") + money(v)

    fun signedEuro(v: Double): String = (if (v > 0) "+" else "") + euro(v)

    // Fertig formatierte Zahl mit Waehrungszeichen an der Stelle, die die Sprache vorsieht.
    fun mitSymbol(text: String, symbol: String): String = withSymbol(text, symbol, false)

    // Dollarbetrag (Meldungen aus den USA, Depots der Investoren, Prediction Markets) in der Anzeigewaehrung:
    // Wert und Zeichen. Ohne Wechselkurs bleibt er in Dollar.
    fun ausDollar(v: Double): Pair<Double, String> {
        val usd = usdJeEuro
        if (anzeigeWaehrung == "USD" || usd <= 0) return v to "$"
        return v / usd * anzeigeJeEuro to symbol
    }

    // Dollarbetrag in Kurzform in der Anzeigewaehrung: "18,60 Mio. €" / "$20.10 m".
    fun dollarKompakt(v: Double): String {
        val (x, s) = ausDollar(v)
        return withSymbol(compact(abs(x)), s, x < 0)
    }

    fun signedPct(v: Double, digits: Int = 2): String =
        (if (v > 0) "+" else if (v < 0) "−" else "") + pct(abs(v), digits)

    fun pct(v: Double, digits: Int = 2): String =
        String.format(loc, "%,.${digits}f", v) + (if (de) " %" else "%")

    fun qty(v: Double): String =
        if (abs(v - Math.round(v)) < 1e-9) Math.round(v).toString()
        else String.format(loc, "%,.4f", v).trimEnd('0').trimEnd(decimalSep)

    fun compact(v: Double): String = when {
        abs(v) >= 1e12 -> String.format(loc, "%,.2f", v / 1e12) + (if (de) " Bio." else " tn")
        abs(v) >= 1e9 -> String.format(loc, "%,.2f", v / 1e9) + (if (de) " Mrd." else " bn")
        abs(v) >= 1e6 -> String.format(loc, "%,.2f", v / 1e6) + (if (de) " Mio." else " m")
        abs(v) >= 1e3 -> String.format(loc, "%,.1f", v / 1e3) + (if (de) " Tsd." else " k")
        else -> String.format(loc, "%,.0f", v)
    }

    // Kompakter Geldbetrag fuer Blasen: "78,8 Tsd. €" / "€78.8k".
    fun compactMoney(v: Double): String = compactIn(anzeige(v), symbol)

    fun compactEuro(v: Double): String = compactIn(v, "€")

    private fun compactIn(v: Double, symbol: String): String {
        val a = abs(v)
        val (num, suffix) = when {
            a >= 1e9 -> String.format(loc, "%,.1f", a / 1e9) to (if (de) " Mrd." else "bn")
            a >= 1e6 -> String.format(loc, "%,.1f", a / 1e6) to (if (de) " Mio." else "m")
            a >= 1e4 -> String.format(loc, "%,.1f", a / 1e3) to (if (de) " Tsd." else "k")
            a >= 100 -> String.format(loc, "%,.0f", a) to ""
            else -> String.format(loc, "%,.2f", a) to ""
        }
        return withSymbol(num + suffix, symbol, v < 0)
    }

    private fun fmt(pattern: String) = DateTimeFormatter.ofPattern(pattern, loc)
    private fun at(ts: Long) = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())

    fun date(ts: Long): String = fmt(if (de) "dd.MM.yyyy" else "d MMM yyyy").format(at(ts))
    fun time(ts: Long): String = fmt("HH:mm").format(at(ts))
    fun dateTime(ts: Long): String = fmt(if (de) "dd.MM.yyyy, HH:mm" else "d MMM yyyy, HH:mm").format(at(ts))
    fun dayMonth(ts: Long): String = fmt(if (de) "dd. MMM" else "d MMM").format(at(ts))
    // Tag und Monat so kurz wie moeglich: "16.10." / "16 Oct".
    fun dayMonthNum(ts: Long): String = fmt(if (de) "dd.MM." else "d MMM").format(at(ts))
    fun dayOfMonth(ts: Long): String = fmt("dd").format(at(ts))
    fun monthShort(ts: Long): String = fmt("MMM").format(at(ts))
    fun monthYear(ym: YearMonth): String = ym.format(fmt("MMMM yyyy"))
    fun monthShortYear(ym: YearMonth): String = ym.format(fmt("MMM yy"))
    // Monatschip: "Sept." im laufenden Jahr, sonst "Dez. 25".
    fun monthChip(ym: YearMonth, now: YearMonth = YearMonth.now()): String =
        if (ym.year == now.year) ym.format(fmt("MMM")) else ym.format(fmt("MMM yy"))
    fun weekdayDateTime(ts: Long): String = fmt(if (de) "EEEE, dd.MM.yyyy HH:mm" else "EEEE, d MMM yyyy HH:mm").format(at(ts))
    fun shortDate(ts: Long): String = fmt(if (de) "dd MMM yy" else "d MMM yy").format(at(ts))

    // Fuer die Achse unter dem Chart.
    fun axis(ts: Long, intraday: Boolean): String = if (intraday) time(ts) else dayMonth(ts)

    fun relative(ts: Long, now: Long = System.currentTimeMillis()): String {
        val d = now - ts
        return when {
            d < 60_000 -> S.justNow
            d < 3_600_000 -> S.minutesAgo(d / 60_000)
            d < 86_400_000 -> S.hoursAgo(d / 3_600_000)
            d < 7 * 86_400_000L -> S.daysAgo(d / 86_400_000L)
            else -> date(ts)
        }
    }

    // "in 3 Tagen", "morgen", "heute" fuer Termine.
    fun untilDay(ts: Long, now: Long = System.currentTimeMillis()): String {
        val days = (ts - now) / 86_400_000L
        return when {
            days < 0 -> date(ts)
            days == 0L -> S.today.lowercase()
            days == 1L -> S.tomorrow.lowercase()
            days < 31 -> S.inDays(days)
            days < 60 -> S.inOneMonth
            else -> S.inMonths(days / 30)
        }
    }
}
