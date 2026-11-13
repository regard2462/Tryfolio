package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt

// Optionsebenen aus der Optionskette (0.76): offene Kontrakte je Basispreis, Put- und Call-Wand und eine Naeherung
// des Gamma-Exposures. Gamma aus Black-Scholes mit der impliziten Volatilitaet des Kontrakts, Exposure je Kontrakt
// = offene Kontrakte x Gamma x Kurs^2 x 0,01; Calls zaehlen positiv, Puts negativ. Ohne Kenntnis, wer gekauft und
// wer verkauft hat, bleibt es eine Naeherung.
object OptionsEbenen {

    // echt und schaetz: Kontrakte mit offenem Interesse, deren Gamma vom Nasdaq stammt bzw. aus Black-Scholes genaehert ist
    data class Strike(
        val strike: Double, val calls: Long, val puts: Long, val gexCalls: Double, val gexPuts: Double,
        val echt: Int = 0, val schaetz: Int = 0,
    ) {
        val offen: Long get() = calls + puts
        val gexNetto: Double get() = gexCalls + gexPuts
    }

    // kumuliert: Summe des Netto-Exposures von unten bis zum jeweiligen Basispreis, gleiche Reihenfolge wie strikes.
    data class Ergebnis(
        val strikes: List<Strike>,
        val kumuliert: List<Double>,
        val callWand: Double?,
        val putWand: Double?,
        val maxPain: Double?,
        val nettoGex: Double,
        val nullpunkt: Double?,
    ) {
        // Basispreise, deren Gamma ganz vom Nasdaq stammt, und Basispreise mit mindestens einem genaeherten Kontrakt
        val strikesEcht: Int get() = strikes.count { it.echt > 0 && it.schaetz == 0 }
        val strikesNaeherung: Int get() = strikes.count { it.schaetz > 0 }
    }

    private const val TAG_S = 86_400L
    private const val JAHR_S = 365.0 * TAG_S

    // Nur Basispreise von 70 bis 130 % des Kurses; Verfaelle, die laenger als einen Tag vorbei sind, zaehlen nicht.
    // nasdaq: Gamma je Kontrakt vom Nasdaq (NasdaqGriechen); wo es fehlt, rechnet Black-Scholes mit der impliziten Volatilitaet.
    fun berechnen(
        kurs: Double, kontrakte: List<Kontrakt>, jetztS: Long, maxPain: Double?,
        nasdaq: Map<NasdaqGriechen.Schluessel, Double> = emptyMap(),
    ): Ergebnis? {
        if (kurs <= 0) return null
        val gueltig = kontrakte.filter { it.verfall > jetztS - TAG_S && it.strike in kurs * 0.7..kurs * 1.3 }
        if (gueltig.none { it.offen > 0 }) return null
        val strikes = gueltig.map { it.strike }.distinct().sorted().map { s ->
            val am = gueltig.filter { it.strike == s }
            var echt = 0
            var schaetz = 0
            fun gex(call: Boolean) = am.filter { it.call == call && it.offen > 0 }.sumOf { k ->
                val n = nasdaq[NasdaqGriechen.schluessel(k.verfall, k.strike, k.call)]
                val g = if (n != null) { echt++; n } else if (k.iv in 0.01..5.0) {
                    schaetz++
                    val t = ((k.verfall - jetztS) / JAHR_S).coerceAtLeast(0.5 / 365.0)
                    Pricing.blackScholes(kurs, k.strike, t, k.iv, k.call).gamma
                } else 0.0
                k.offen * g * kurs * kurs * 0.01
            }
            val gc = gex(true)
            val gp = gex(false)
            Strike(s, am.filter { it.call }.sumOf { it.offen }, am.filter { !it.call }.sumOf { it.offen }, gc, -gp, echt, schaetz)
        }
        val kum = ArrayList<Double>(strikes.size)
        var summe = 0.0
        strikes.forEach { summe += it.gexNetto; kum += summe }
        val callWand = (strikes.filter { it.strike >= kurs }.ifEmpty { strikes }).maxByOrNull { it.calls }?.takeIf { it.calls > 0 }?.strike
        val putWand = (strikes.filter { it.strike <= kurs }.ifEmpty { strikes }).maxByOrNull { it.puts }?.takeIf { it.puts > 0 }?.strike
        return Ergebnis(strikes, kum, callWand, putWand, maxPain, summe, nullpunkt(strikes.map { it.strike }, kum, kurs))
    }

    // Vorzeichenwechsel der kumulierten Linie, linear zwischen zwei Basispreisen. Wechsel, bei denen die Linie kaum
    // von null abweicht (unter 5 % ihres groessten Betrags, Rauschen an den Raendern), zaehlen nicht; von mehreren
    // gilt der naechste am Kurs (ohne Kurs der erste).
    fun nullpunkt(strikes: List<Double>, kum: List<Double>, kurs: Double? = null): Double? {
        val schwelle = (kum.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0) * 0.05
        val punkte = ArrayList<Double>()
        for (i in 1 until kum.size) {
            val a = kum[i - 1]
            val b = kum[i]
            if (maxOf(kotlin.math.abs(a), kotlin.math.abs(b)) < schwelle) continue
            if (a == 0.0) punkte += strikes[i - 1]
            else if ((a < 0 && b > 0) || (a > 0 && b < 0)) punkte += strikes[i - 1] + (strikes[i] - strikes[i - 1]) * (-a / (b - a))
        }
        if (kurs == null) return punkte.firstOrNull()
        return punkte.minByOrNull { kotlin.math.abs(it - kurs) }
    }

    // Die Zeilen fuers Diagramm: hoechstens maxZeilen Basispreise, die mit den meisten offenen Kontrakten, nach
    // Basispreis geordnet. Der Basispreis am naechsten zum Kurs bleibt immer dabei.
    fun fuerAnzeige(e: Ergebnis, kurs: Double, maxZeilen: Int = 21): List<Strike> {
        val nah = e.strikes.filter { it.strike in kurs * 0.85..kurs * 1.15 }
        if (nah.size <= maxZeilen) return nah
        val naechster = nah.minByOrNull { kotlin.math.abs(it.strike - kurs) }
        return (nah.sortedByDescending { it.offen }.take(maxZeilen - 1) + listOfNotNull(naechster)).distinct().sortedBy { it.strike }
    }
}
