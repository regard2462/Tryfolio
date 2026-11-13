package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

// Optionsrechner: Strategien mit bis zu vier Beinen aus der Optionskette (Yahoo), Auszahlung bei Verfall und zu einem
// Datum (Black-Scholes mit der impliziten Volatilitaet des Beins), Break-even, groesster Gewinn und Verlust, Chance auf
// Gewinn (lognormal), Spanne von einer Standardabweichung und die Griechen der Gesamtposition.
// Alle Betraege gelten je Kontrakt (100 Aktien). Beim Aktienbein steht menge 1 fuer 100 Aktien.
object Optionsrechner {

    const val KONTRAKT = 100.0
    const val MAX_BEINE = 4
    private const val TAG_S = 86_400L

    enum class Vorlage {
        LONG_CALL, LONG_PUT, COVERED_CALL, CASH_SECURED_PUT, BULL_CALL_SPREAD, BEAR_PUT_SPREAD,
        STRADDLE, STRANGLE, IRON_CONDOR, BUTTERFLY, COLLAR, SCHUTZ_PUT, EIGENE,
    }

    // Schutz-Put: Laufzeit von mindestens einem halben Jahr.
    const val SCHUTZ_PUT_TAGE = 182

    // Mindestlaufzeit einer Vorlage in Tagen, 0 ohne Vorgabe.
    fun mindestTage(v: Vorlage): Int = if (v == Vorlage.SCHUTZ_PUT) SCHUTZ_PUT_TAGE else 0

    // Verfall, mit dem die Vorlage aufgestellt wird: der gewaehlte, wenn er lang genug laeuft, sonst der erste Termin
    // mit der Mindestlaufzeit, ohne einen solchen der spaeteste.
    fun verfallFuer(v: Vorlage, termine: List<Long>, verfall: Long, jetztS: Long): Long {
        val mind = mindestTage(v)
        if (mind <= 0 || tage(verfall, jetztS) >= mind) return verfall
        return termine.sorted().firstOrNull { tage(it, jetztS) >= mind } ?: termine.maxOrNull() ?: verfall
    }

    enum class Art { CALL, PUT, AKTIE }

    // menge: Kontrakte, negativ = verkauft. strike beim Aktienbein ohne Bedeutung (0).
    data class Leg(val art: Art, val menge: Int, val strike: Double)

    // Ein Bein mit Preis und Volatilitaet aus der Kette. preis ist je Aktie (Einstandskurs beim Aktienbein).
    data class Bein(val art: Art, val menge: Int, val strike: Double, val preis: Double, val iv: Double)

    // wert null: unbegrenzt.
    data class Grenze(val wert: Double?)

    data class Ergebnis(
        val einsatz: Double,
        val breakEven: List<Double>,
        val maxGewinn: Grenze,
        val maxVerlust: Grenze,
        val chance: Double?,
        val band: Pair<Double, Double>?,
        val sigma: Double?,
    )

    data class Greeks(val delta: Double, val gamma: Double, val theta: Double, val vega: Double)

    data class Bewegung(val verfall: Long, val tage: Int, val betrag: Double, val prozent: Double, val von: Double, val bis: Double)

    // ------------------------------------------------------------ Kette

    fun verfaelle(kontrakte: List<Kontrakt>, jetztS: Long): List<Long> =
        kontrakte.map { it.verfall }.filter { it > jetztS - TAG_S / 2 }.distinct().sorted()

    fun tage(verfall: Long, jetztS: Long): Int = ((verfall - jetztS) / TAG_S).toInt().coerceAtLeast(0)

    fun strikes(kontrakte: List<Kontrakt>, verfall: Long, call: Boolean): List<Double> =
        kontrakte.filter { it.verfall == verfall && it.call == call }.map { it.strike }.distinct().sorted()

    fun naechster(liste: List<Double>, ziel: Double): Double? = liste.minByOrNull { abs(it - ziel) }

    // Volatilitaet am Geld des Verfalls, Mittel aus Call und Put am Basispreis nahe dem Kurs.
    fun ivAmGeld(kurs: Double, kontrakte: List<Kontrakt>, verfall: Long): Double? {
        val am = kontrakte.filter { it.verfall == verfall }
        val s = am.map { it.strike }.distinct().minByOrNull { abs(it - kurs) } ?: return null
        return am.filter { it.strike == s && it.iv in 0.01..5.0 }.map { it.iv }.takeIf { it.isNotEmpty() }?.average()
    }

    private fun jahre(tage: Int) = max(tage, 0) / 365.0

    // Beine mit Preisen: Mitte aus Geld und Brief, sonst letzter Preis, sonst Black-Scholes mit der Volatilitaet.
    // null, wenn ein Basispreis im Verfall fehlt oder kein Preis zu finden ist.
    fun beine(kurs: Double, kontrakte: List<Kontrakt>, verfall: Long, legs: List<Leg>, jetztS: Long): List<Bein>? {
        if (legs.isEmpty()) return null
        val ersatz = ivAmGeld(kurs, kontrakte, verfall) ?: 0.30
        val t = jahre(tage(verfall, jetztS))
        return legs.map { l ->
            if (l.art == Art.AKTIE) return@map Bein(Art.AKTIE, l.menge, 0.0, kurs, 0.0)
            val k = kontrakte.firstOrNull { it.verfall == verfall && it.call == (l.art == Art.CALL) && it.strike == l.strike } ?: return null
            val iv = k.iv.takeIf { it in 0.01..5.0 } ?: ersatz
            val preis = k.mitte.takeIf { it > 0 }
                ?: Pricing.blackScholes(kurs, k.strike, t, iv, k.call).price.takeIf { it > 0 }
                ?: return null
            Bein(l.art, l.menge, l.strike, preis, iv)
        }
    }

    // ------------------------------------------------------------ Vorlagen

    // Beine einer Vorlage. Ein Schritt u ist etwa 5 % vom Kurs in Basispreisstufen. null, wenn die Kette zu wenig
    // Basispreise hat.
    fun vorlage(v: Vorlage, kurs: Double, kontrakte: List<Kontrakt>, verfall: Long): List<Leg>? {
        val calls = strikes(kontrakte, verfall, true)
        val puts = strikes(kontrakte, verfall, false)
        if (calls.isEmpty() || puts.isEmpty() || kurs <= 0) return null
        val abstaende = calls.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        val stufe = abstaende.getOrNull(abstaende.size / 2) ?: return null
        val u = max(1, round(kurs * 0.05 / stufe).toInt())
        fun wahl(liste: List<Double>, offset: Int): Double? {
            val i0 = liste.indexOf(naechster(liste, kurs) ?: return null)
            return liste.getOrNull(i0 + offset)
        }
        fun c(menge: Int, offset: Int) = wahl(calls, offset)?.let { Leg(Art.CALL, menge, it) }
        fun p(menge: Int, offset: Int) = wahl(puts, offset)?.let { Leg(Art.PUT, menge, it) }
        val aktie = Leg(Art.AKTIE, 1, 0.0)
        val liste: List<Leg?> = when (v) {
            Vorlage.LONG_CALL -> listOf(c(1, 0))
            Vorlage.LONG_PUT -> listOf(p(1, 0))
            Vorlage.COVERED_CALL -> listOf(aktie, c(-1, u))
            Vorlage.CASH_SECURED_PUT -> listOf(p(-1, -u))
            Vorlage.BULL_CALL_SPREAD -> listOf(c(1, 0), c(-1, u))
            Vorlage.BEAR_PUT_SPREAD -> listOf(p(1, 0), p(-1, -u))
            Vorlage.STRADDLE -> listOf(c(1, 0), p(1, 0))
            Vorlage.STRANGLE -> listOf(c(1, u), p(1, -u))
            Vorlage.IRON_CONDOR -> listOf(p(1, -2 * u), p(-1, -u), c(-1, u), c(1, 2 * u))
            Vorlage.BUTTERFLY -> listOf(c(1, -u), c(-2, 0), c(1, u))
            Vorlage.COLLAR -> listOf(aktie, p(1, -u), c(-1, u))
            Vorlage.SCHUTZ_PUT -> listOf(aktie, p(1, -u))
            Vorlage.EIGENE -> return null
        }
        if (liste.any { it == null }) return null
        return liste.filterNotNull()
    }

    // ------------------------------------------------------------ Auszahlung

    // Gewinn oder Verlust bei Verfall, wenn der Kurs s betraegt.
    fun guvVerfall(beine: List<Bein>, s: Double): Double = beine.sumOf { b ->
        val innen = when (b.art) {
            Art.CALL -> max(0.0, s - b.strike)
            Art.PUT -> max(0.0, b.strike - s)
            Art.AKTIE -> s
        }
        b.menge * (innen - b.preis) * KONTRAKT
    }

    // Gewinn oder Verlust zu einem Datum mit restJahre bis zum Verfall (Black-Scholes mit der Volatilitaet des Beins).
    fun guvDatum(beine: List<Bein>, s: Double, restJahre: Double): Double = beine.sumOf { b ->
        val wert = when (b.art) {
            Art.AKTIE -> s
            else -> Pricing.blackScholes(s, b.strike, restJahre, b.iv, b.art == Art.CALL).price
        }
        b.menge * (wert - b.preis) * KONTRAKT
    }

    fun griechen(beine: List<Bein>, s: Double, restJahre: Double): Greeks {
        var d = 0.0; var g = 0.0; var th = 0.0; var ve = 0.0
        for (b in beine) {
            if (b.art == Art.AKTIE) { d += b.menge; continue }
            val x = Pricing.blackScholes(s, b.strike, restJahre, b.iv, b.art == Art.CALL)
            d += b.menge * x.delta; g += b.menge * x.gamma; th += b.menge * x.theta; ve += b.menge * x.vega
        }
        return Greeks(d * KONTRAKT, g * KONTRAKT, th * KONTRAKT, ve * KONTRAKT)
    }

    // Nullstellen der Auszahlung bei Verfall. Sie ist zwischen den Basispreisen linear, also genau.
    fun breakEven(beine: List<Bein>): List<Double> {
        val stuetzen = (listOf(0.0) + beine.filter { it.art != Art.AKTIE }.map { it.strike }).distinct().sorted()
        val ende = (stuetzen.last().coerceAtLeast(1.0)) * 50
        val punkte = stuetzen + ende
        val werte = punkte.map { guvVerfall(beine, it) }
        val roots = ArrayList<Double>()
        fun add(x: Double) { if (x > 0 && roots.none { abs(it - x) < 1e-6 * max(1.0, x) }) roots += x }
        for (i in 0 until punkte.size - 1) {
            val a = punkte[i]; val b = punkte[i + 1]; val va = werte[i]; val vb = werte[i + 1]
            if (abs(va) < 1e-9) add(a)
            else if (va * vb < 0) add(a + (b - a) * (-va) / (vb - va))
        }
        if (abs(werte.last()) < 1e-9) add(punkte.last())
        return roots.sorted()
    }

    fun grenzen(beine: List<Bein>): Pair<Grenze, Grenze> {
        val stuetzen = (listOf(0.0) + beine.filter { it.art != Art.AKTIE }.map { it.strike }).distinct()
        val werte = stuetzen.map { guvVerfall(beine, it) }
        val hang = beine.sumOf { b -> if (b.art == Art.PUT) 0.0 else b.menge.toDouble() }
        val gewinn = if (hang > 0) Grenze(null) else Grenze(werte.max())
        val verlust = if (hang < 0) Grenze(null) else Grenze(werte.min())
        return gewinn to verlust
    }

    private fun verteilung(x: Double, kurs: Double, t: Double, sigma: Double, zins: Double): Double {
        if (x <= 0) return 0.0
        return Pricing.normCdf((ln(x / kurs) - (zins - sigma * sigma / 2) * t) / (sigma * sqrt(t)))
    }

    // Chance, bei Verfall im Gewinn zu liegen: Kurs lognormal mit sigma, Drift gleich dem Zins.
    fun chance(beine: List<Bein>, kurs: Double, tage: Int, sigma: Double, roots: List<Double> = breakEven(beine), zins: Double = Pricing.RATE): Double {
        val t = max(tage.toDouble(), 0.5) / 365.0
        val grenzen = listOf(0.0) + roots
        var p = 0.0
        for (i in grenzen.indices) {
            val von = grenzen[i]
            val bis = grenzen.getOrNull(i + 1)
            val probe = if (bis == null) max(von * 1.5, von + 1.0) else (von + bis) / 2
            val punkt = if (bis == null && roots.isEmpty()) kurs else probe
            if (guvVerfall(beine, punkt) > 0) {
                p += (bis?.let { verteilung(it, kurs, t, sigma, zins) } ?: 1.0) - verteilung(von, kurs, t, sigma, zins)
            }
        }
        return p.coerceIn(0.0, 1.0)
    }

    fun band(kurs: Double, tage: Int, sigma: Double, zins: Double = Pricing.RATE): Pair<Double, Double> {
        val t = max(tage.toDouble(), 0.5) / 365.0
        val m = (zins - sigma * sigma / 2) * t
        val w = sigma * sqrt(t)
        return kurs * exp(m - w) to kurs * exp(m + w)
    }

    // zins: risikoloser Zins als Anteil, auf der Oberflaeche der aus den Einstellungen (wie die Seite eines Scheins).
    fun auswerten(beine: List<Bein>, kurs: Double, kontrakte: List<Kontrakt>, verfall: Long, jetztS: Long, zins: Double = Pricing.RATE): Ergebnis {
        val tg = tage(verfall, jetztS)
        val optionen = beine.filter { it.art != Art.AKTIE }
        val sigma = ivAmGeld(kurs, kontrakte, verfall) ?: optionen.map { it.iv }.takeIf { it.isNotEmpty() }?.average()
        val roots = breakEven(beine)
        val (g, v) = grenzen(beine)
        return Ergebnis(
            einsatz = optionen.sumOf { it.menge * it.preis } * KONTRAKT,
            breakEven = roots,
            maxGewinn = g, maxVerlust = v,
            chance = sigma?.let { chance(beine, kurs, tg, it, roots, zins) },
            band = sigma?.let { band(kurs, tg, it, zins) },
            sigma = sigma,
        )
    }

    // Darstellungsbereich des Diagramms: Spanne der Strikes und der Standardabweichung, mit Rand.
    fun bereich(beine: List<Bein>, kurs: Double, band: Pair<Double, Double>?): Pair<Double, Double> {
        val ks = beine.filter { it.art != Art.AKTIE }.map { it.strike } + kurs + listOfNotNull(band?.first, band?.second)
        val lo = ks.min(); val hi = ks.max()
        val rand = max((hi - lo) * 0.15, kurs * 0.04)
        return max(0.0, lo - rand) to hi + rand
    }

    // ------------------------------------------------------------ Erwartete Bewegung je Verfall

    // Straddle am Geld je Verfall: Call und Put am Basispreis nahe dem Kurs, beide mit Preis.
    fun bewegungen(kurs: Double, kontrakte: List<Kontrakt>, jetztS: Long): List<Bewegung> {
        if (kurs <= 0) return emptyList()
        return verfaelle(kontrakte, jetztS).mapNotNull { v ->
            val am = kontrakte.filter { it.verfall == v }
            val strike = am.map { it.strike }.distinct()
                .filter { s -> am.any { it.call && it.strike == s && it.mitte > 0 } && am.any { !it.call && it.strike == s && it.mitte > 0 } }
                .minByOrNull { abs(it - kurs) } ?: return@mapNotNull null
            val betrag = am.first { it.call && it.strike == strike }.mitte + am.first { !it.call && it.strike == strike }.mitte
            Bewegung(v, tage(v, jetztS), betrag, betrag / kurs * 100, kurs - betrag, kurs + betrag)
        }
    }

    // n gleichmaessig verteilte Kurse von lo bis hi.
    fun punkte(lo: Double, hi: Double, n: Int): List<Double> = List(n) { lo + (hi - lo) * it / (n - 1).toDouble() }
}
