package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Indikatoren.Deutung
import de.troetenrepublik.app.engine.Indikatoren.Tendenz

// Gesamtrichtung (0.76): Wohin zeigen die Indikatoren zusammen? Gezaehlt werden Signale nach der ueblichen Lesart
// jedes Indikators (Indikatoren.tendenz), also eine Zaehlung und keine Vorhersage.
//
// Verwandte Indikatoren messen dasselbe auf leicht anderem Weg (etwa Kurs ueber dem 50- und dem 200-Tage-Durchschnitt
// oder RSI und Stochastik). Sie bilden eine Familie und zaehlen zusammen als ein Signal: Die Familie steigt oder
// faellt, wenn mehr als die Haelfte ihrer Mitglieder so zeigt; sonst gilt sie als neutral bzw. gemischt. So bekommt
// eine Gruppe aus fuenf Durchschnitten nicht das fuenffache Gewicht eines einzelnen Indikators.
object Gesamtrichtung {

    // Familie je Indikator; was hier fehlt, steht fuer sich (ichimoku, sar, atr, kerzen, squeeze).
    private val FAMILIEN = mapOf(
        "sma20" to "durchschnitt", "sma50" to "durchschnitt", "sma200" to "durchschnitt",
        "ema20" to "durchschnitt", "ema50" to "durchschnitt", "kreuz" to "durchschnitt",
        "bb" to "baender", "keltner" to "baender", "donchian" to "baender",
        "rsi" to "zone", "stoch" to "zone", "cci" to "zone", "williams" to "zone", "mfi" to "zone",
        "obv" to "umsatz", "cmf" to "umsatz",
        "macd" to "schwung", "roc" to "schwung",
        "adx" to "trendstaerke", "aroon" to "trendstaerke",
    )

    // Indikatoren, die nur der erweiterte Chart kennt und die hier aus den Tageskerzen gedeutet werden. VWAP (beginnt
    // jeden Handelstag neu) und Volumen (ohne Richtung) fehlen, weil sie aus Tageskerzen nichts Sinnvolles sagen.
    val ZUSATZ = listOf("sma20", "ema20", "ema50", "keltner", "donchian", "cci", "williams", "mfi", "adx", "roc", "aroon", "cmf", "squeeze")

    fun familie(art: String): String = FAMILIEN[art] ?: art

    // Trend aus Hoch- und Tiefpunkten (Markttechnik) als eigenes Signal der Zaehlung.
    const val TREND = "markttechnik"

    // Aufwaertstrend zaehlt steigend, Abwaertstrend fallend, ein gebrochener Trend hat keine Richtung.
    // null bei zu wenigen Tageskerzen und wenn sich kein Trend bestimmen laesst.
    fun trend(hoch: List<Double>, tief: List<Double>, werte: List<Double>): Deutung? {
        val n = werte.size
        if (n < 30 || hoch.size != n || tief.size != n) return null
        val zustand = Markttechnik.lage(hoch, tief, werte).zustand
        if (zustand == Markttechnik.Zustand.KEINER) return null
        val stufe = when (zustand) {
            Markttechnik.Zustand.AUF -> 1
            Markttechnik.Zustand.AB -> -1
            else -> 0
        }
        return Deutung(TREND, stufe, zustand.name, 0.0)
    }

    enum class Wort { STEIGEND, EHER_STEIGEND, OFFEN, EHER_FALLEND, FALLEND }

    // Ein Signal der Zaehlung: eine Familie oder ein einzelner Indikator.
    class Einheit(val schluessel: String, val mitglieder: List<Deutung>, val tendenz: Tendenz)

    class Ergebnis(val einheiten: List<Einheit>, val steigend: Int, val fallend: Int, val neutral: Int, val wort: Wort)

    private fun einheit(schluessel: String, m: List<Deutung>): Einheit {
        val t = m.map { Indikatoren.tendenz(it) }
        val n = t.size
        val tendenz = when {
            t.count { it == Tendenz.POSITIV } * 2 > n -> Tendenz.POSITIV
            t.count { it == Tendenz.NEGATIV } * 2 > n -> Tendenz.NEGATIV
            t.count { it == Tendenz.GEMISCHT } * 2 > n -> Tendenz.GEMISCHT
            else -> Tendenz.NEUTRAL
        }
        return Einheit(schluessel, m, tendenz)
    }

    // Anteil des Saldos (steigend minus fallend) an allen Signalen, ab dem das Wort kippt.
    private const val STARK = 0.5
    private const val LEICHT = 0.2

    fun berechnen(deutungen: List<Deutung>): Ergebnis? {
        val d = deutungen.distinctBy { it.art }
        if (d.isEmpty()) return null
        // Reihenfolge der ersten Nennung bleibt, damit die Aufschluesselung stabil steht
        val gruppen = LinkedHashMap<String, MutableList<Deutung>>()
        d.forEach { gruppen.getOrPut(familie(it.art)) { ArrayList() } += it }
        val einheiten = gruppen.map { (k, m) -> einheit(k, m) }
        val s = einheiten.count { it.tendenz == Tendenz.POSITIV }
        val f = einheiten.count { it.tendenz == Tendenz.NEGATIV }
        val n = einheiten.size - s - f
        val saldo = (s - f).toDouble() / einheiten.size
        val wort = when {
            s + f == 0 -> Wort.OFFEN
            saldo >= STARK -> Wort.STEIGEND
            saldo >= LEICHT -> Wort.EHER_STEIGEND
            saldo > -LEICHT -> Wort.OFFEN
            saldo > -STARK -> Wort.EHER_FALLEND
            else -> Wort.FALLEND
        }
        return Ergebnis(einheiten, s, f, n, wort)
    }

    // Deutungen der Indikatoren, die nur der erweiterte Chart kennt, aus den bereinigten Tageskerzen (aufsteigend).
    // Umsatzindikatoren (MFI, CMF) nur, wenn an den meisten der letzten 60 Tage Umsaetze vorliegen.
    fun zusatz(werte: List<Double>, hoch: List<Double>, tief: List<Double>, umsatz: List<Double>): List<Deutung> {
        val n = werte.size
        if (n < 30 || hoch.size != n || tief.size != n) return emptyList()
        val kurs = werte.last()
        val aus = ArrayList<Deutung>()
        fun durchschnitt(art: String, m: Double?) {
            if (m == null || m <= 0) return
            val ab = (kurs / m - 1) * 100
            aus += Deutung(art, if (ab >= 0) 1 else -1, if (ab >= 0) "ueber" else "unter", ab)
        }
        durchschnitt("sma20", Indikatoren.sma(werte, 20).last())
        durchschnitt("ema20", Indikatoren.ema(werte, 20).last())
        durchschnitt("ema50", Indikatoren.ema(werte, 50).last())
        // Lage im Kanal wie bei den Bollinger-Baendern: oben und unten Richtung, ausserhalb weit gelaufen
        fun kanal(art: String, b: Indikatoren.Baender) {
            val o = b.oben.last(); val u = b.unten.last()
            if (o == null || u == null || o <= u) return
            val lage = (kurs - u) / (o - u)
            val stufe = when { lage > 1.0 || lage < 0.0 -> 2; lage >= 0.8 -> 1; lage <= 0.2 -> -1; else -> 0 }
            aus += Deutung(art, stufe, "kanal", lage)
        }
        kanal("keltner", Indikatoren.keltner(hoch, tief, werte))
        kanal("donchian", Indikatoren.donchian(hoch, tief))
        // Zonen: weit gelaufen heisst gemischt, sonst keine Richtung
        fun zone(art: String, v: Double?, oben: Double, unten: Double) {
            if (v == null) return
            aus += if (v >= oben || v <= unten) Deutung(art, 2, if (v >= oben) "ueberkauft" else "ueberverkauft", v) else Deutung(art, 0, "neutral", v)
        }
        zone("cci", Indikatoren.cci(hoch, tief, werte).last(), 100.0, -100.0)
        zone("williams", Indikatoren.williamsR(hoch, tief, werte).last(), -20.0, -80.0)
        val mitUmsatz = umsatz.size == n && umsatz.takeLast(60).count { it > 0 } >= 40
        if (mitUmsatz) zone("mfi", Indikatoren.mfi(hoch, tief, werte, umsatz).last(), 80.0, 20.0)
        // Trendstaerke: nur ab ADX 25 mit der Richtung der staerkeren Linie
        val a = Indikatoren.adx(hoch, tief, werte)
        val ax = a.adx.last(); val p = a.plus.last(); val q = a.minus.last()
        if (ax != null && p != null && q != null) {
            aus += if (ax >= 25 && p > q) Deutung("adx", 1, "auf", ax) else if (ax >= 25 && q > p) Deutung("adx", -1, "ab", ax) else Deutung("adx", 0, "schwach", ax)
        }
        Indikatoren.roc(werte).last()?.let { r ->
            aus += Deutung("roc", if (r > 0) 1 else if (r < 0) -1 else 0, if (r > 0) "auf" else if (r < 0) "ab" else "flach", r)
        }
        val ar = Indikatoren.aroon(hoch, tief)
        val au = ar.auf.last(); val ab = ar.ab.last()
        if (au != null && ab != null) {
            aus += when {
                au >= 70 && ab <= 30 -> Deutung("aroon", 1, "auf", au)
                ab >= 70 && au <= 30 -> Deutung("aroon", -1, "ab", ab)
                else -> Deutung("aroon", 0, "offen", au - ab)
            }
        }
        if (mitUmsatz) Indikatoren.cmf(hoch, tief, werte, umsatz).last()?.let { c ->
            aus += Deutung("cmf", if (c > 0.05) 1 else if (c < -0.05) -1 else 0, if (c > 0.05) "kauf" else if (c < -0.05) "verkauf" else "ausgeglichen", c)
        }
        // Squeeze: nur der Austritt aus der engen Phase gibt eine Richtung; ohne Austritt zaehlt er nicht mit
        Squeeze.deutung(Squeeze.rechnen(hoch, tief, werte))?.takeIf { it.stufe != 0 }?.let { aus += it }
        return aus
    }
}
