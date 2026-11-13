package de.troetenrepublik.app.market

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Derivate in den Meldungen von Abgeordneten, Senat und Regierung: Optionen und strukturierte Produkte.
// Senat: Angaben im Namen („Option Type: Call Strike price: $75.00 Expires: 2026-08-21“).
// Repraesentantenhaus: in der Beschreibung („20 call options with a strike price of $150“).
// Papiermeldungen und OGE: im Namen („CALL/JNJ FLEX EURO PM @ 202.5 EXP 02/06/2026“).
// Im Portfolioverlauf zaehlt eine Option mit Basispreis und Verfall mit ihrem Wert nach Black-Scholes: Kurs des
// Basiswerts am Tag, Schwankung der letzten 60 Handelstage, Zins 4 Prozent, ohne Dividenden; ab dem Verfall der
// innere Wert. Ohne diese Angaben zaehlt sie als Basiswert.
object Derivate {

    enum class Art { CALL, PUT, STRUKTUR, SONST }

    data class Derivat(val art: Art, val basis: String?, val strike: Double?, val verfall: Long?, val kontrakte: Int? = null) {
        // Laesst sich aus dem Kurs des Basiswerts bewerten.
        val bewertbar: Boolean get() = (art == Art.CALL || art == Art.PUT) && !basis.isNullOrBlank() && strike != null && strike > 0 && verfall != null && verfall > 0
        // Gleiche Option, gleicher Schluessel (Kauf und spaeterer Verkauf treffen sich).
        val schluessel: String get() = "$basis|${art.name.first()}|$strike|$verfall"
    }

    const val ZINS = 0.04
    private const val TAG = 86_400_000L

    private val OPTION_TYPE = Regex("""Option Type:\s*(Call|Put)\b.*?Strike price:\s*\$?\s*([\d,]+(?:\.\d+)?).*?Expires:\s*(\d{4}-\d{2}-\d{2}|\d{1,2}/\d{1,2}/\d{2,4})""", RegexOption.IGNORE_CASE)
    private val SCHRAEG = Regex("""\b(CALL|PUT)S?\s*/\s*([A-Z][A-Z.\-]{0,6})\b""")
    private val AT_STRIKE = Regex("""@\s*\$?\s*([\d,]+(?:\.\d+)?)""")
    private val EXP = Regex("""\bEXP(?:IRES|IRY|IRATION)?\.?:?\s*(\d{1,2}/\d{1,2}/\d{2,4}|\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
    private val WORT = Regex("""\b(call|put)s?\b""", RegexOption.IGNORE_CASE)
    // Dieselben Angaben in Text ohne Leerzeichen
    private val DICHT_ART = Regex("""(call|put)s?(?:option|contract)""", RegexOption.IGNORE_CASE)
    private val DICHT_STRIKE = Regex("""strike(?:price)?(?:of)?:?\$?([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val DICHT_VERFALL = Regex("""expir[a-z]*?(?:date)?(?:of|on)?:?(\d{1,2}/\d{1,2}/\d{2,4}|\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
    private val DICHT_KONTRAKTE = Regex("""(\d[\d,]*)(?:call|put)s?(?:option|contract)""", RegexOption.IGNORE_CASE)
    private val STRUKTUR = Regex("""LINKED TO|MARKET[- ]LINKED|STRUCTURED|AUTOCALL|BUFFERED|CONTINGENT COUPON|REVERSE CONVERTIBLE""", RegexOption.IGNORE_CASE)

    // Null fuer Aktien, Fonds und Anleihen, auch fuer kuendbare Anleihen mit „CALL@“.
    fun aus(g: Kongress.Geschaeft): Derivat? {
        val text = (g.wert + " " + g.beschreibung).trim()
        OPTION_TYPE.find(text)?.let { m ->
            return Derivat(art(m.groupValues[1]), g.kuerzel, zahl(m.groupValues[2]), datum(m.groupValues[3]))
        }
        SCHRAEG.find(text)?.let { m ->
            val basis = g.kuerzel ?: m.groupValues[2].trimEnd('.', '-')
            return Derivat(art(m.groupValues[1]), basis, AT_STRIKE.find(text)?.let { zahl(it.groupValues[1]) }, EXP.find(text)?.let { datum(it.groupValues[1]) })
        }
        if (g.typ == "OP") {
            // Das Repraesentantenhaus liefert die Beschreibung teils ohne Leerzeichen.
            val dicht = text.replace(Regex("\\s+"), "")
            val a = DICHT_ART.find(dicht)?.let { art(it.groupValues[1]) } ?: WORT.find(g.wert)?.let { art(it.groupValues[1]) } ?: Art.SONST
            return Derivat(
                a, g.kuerzel,
                DICHT_STRIKE.find(dicht)?.let { zahl(it.groupValues[1]) },
                DICHT_VERFALL.find(dicht)?.let { datum(it.groupValues[1]) },
                DICHT_KONTRAKTE.find(dicht)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull(),
            )
        }
        if (STRUKTUR.containsMatchIn(text)) return Derivat(Art.STRUKTUR, null, null, null)
        return null
    }

    // Ausgeuebte Option bei einem Aktienkauf, erkannt an „Exercised“ am Anfang der Beschreibung.
    // Die Option endet an diesem Tag.
    fun ausuebung(g: Kongress.Geschaeft): Derivat? {
        if (g.typ == "OP") return null
        val dicht = g.beschreibung.replace(Regex("\\s+"), "")
        if (!dicht.startsWith("Exercised", ignoreCase = true)) return null
        val a = DICHT_ART.find(dicht) ?: return null
        return Derivat(art(a.groupValues[1]), g.kuerzel, DICHT_STRIKE.find(dicht)?.let { zahl(it.groupValues[1]) }, DICHT_VERFALL.find(dicht)?.let { datum(it.groupValues[1]) })
            .takeIf { it.bewertbar }
    }

    private fun art(s: String) = if (s.equals("put", true)) Art.PUT else Art.CALL

    private fun zahl(s: String): Double? = s.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

    // M/T/JJ, M/T/JJJJ oder JJJJ-MM-TT als Mitternacht UTC.
    fun datum(s: String): Long? = runCatching {
        val d = if (s.contains('-')) LocalDate.parse(s) else {
            val (m, t, j) = s.split('/').map { it.toInt() }
            LocalDate.of(if (j < 100) 2000 + j else j, m, t)
        }
        d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()

    // Bewertung

    // Standardnormalverteilung (Abramowitz/Stegun 26.2.17, Fehler unter 1e-7).
    fun normal(x: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val p = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        val n = 1.0 - exp(-x * x / 2) / sqrt(2 * Math.PI) * p
        return if (x >= 0) n else 1.0 - n
    }

    // Preis einer europaeischen Option nach Black-Scholes; jahre Restlaufzeit, ab 0 der innere Wert.
    fun preis(call: Boolean, kurs: Double, strike: Double, jahre: Double, vola: Double, zins: Double = ZINS): Double {
        if (jahre <= 0 || vola <= 0) return max(0.0, if (call) kurs - strike else strike - kurs)
        val w = vola * sqrt(jahre)
        val d1 = (ln(kurs / strike) + (zins + vola * vola / 2) * jahre) / w
        val d2 = d1 - w
        return if (call) kurs * normal(d1) - strike * exp(-zins * jahre) * normal(d2)
        else strike * exp(-zins * jahre) * normal(-d2) - kurs * normal(-d1)
    }

    // Tageswerte einer Option aus den Schlusskursen des Basiswerts (Tage wie in Portfolioverlauf.tag): bis zum
    // Verfall nach Black-Scholes, danach der innere Wert am Verfall. Mindestens 1 Cent, damit ein Kauf weit aus
    // dem Geld eine endliche Stueckzahl ergibt.
    fun reihe(d: Derivat, basis: TreeMap<Long, Double>): TreeMap<Long, Double> {
        val aus = TreeMap<Long, Double>()
        if (!d.bewertbar || basis.isEmpty()) return aus
        val call = d.art == Art.CALL
        val verfallTag = Portfolioverlauf.tag(d.verfall!!)
        val tage = basis.keys.toList()
        val werte = basis.values.toList()
        val renditen = DoubleArray(werte.size) { i -> if (i == 0 || werte[i - 1] <= 0) 0.0 else ln(werte[i] / werte[i - 1]) }
        val gesamt = vola(renditen, 1, renditen.size)
        var amVerfall: Double? = null
        for (i in tage.indices) {
            val tag = tage[i]
            if (tag > verfallTag) {
                if (amVerfall == null) amVerfall = max(0.01, preis(call, werte[i - 1].takeIf { i > 0 } ?: werte[i], d.strike!!, 0.0, 0.0))
                aus[tage[i]] = amVerfall
                continue
            }
            val v = if (i >= 20) vola(renditen, max(1, i - 59), i + 1) else gesamt
            val jahre = (verfallTag - tag) / 365.25
            aus[tage[i]] = max(0.01, preis(call, werte[i], d.strike!!, jahre, v))
        }
        return aus
    }

    // Schwankung pro Jahr aus Tagesrenditen von von bis vor bis, begrenzt auf 12 bis 150 Prozent.
    private fun vola(r: DoubleArray, von: Int, bis: Int): Double {
        val n = bis - von
        if (n < 5) return 0.3
        var s = 0.0
        for (i in von until bis) s += r[i]
        val m = s / n
        var q = 0.0
        for (i in von until bis) q += (r[i] - m) * (r[i] - m)
        return min(1.5, max(0.12, sqrt(q / (n - 1)) * sqrt(252.0)))
    }
}
