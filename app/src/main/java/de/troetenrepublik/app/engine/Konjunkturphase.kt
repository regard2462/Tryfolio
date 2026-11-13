package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehindikator.Phase
import java.time.YearMonth

// Konjunkturphase fuer jedes Land mit IWF-Daten. Zwei Wege mit denselben vier Phasen:
// 1. OECD-Fruehindikator (CLI), wo er vorliegt: monatlich und aktueller (siehe Fruehindikator.phase).
// 2. IWF-Wachstum, sonst: Das reale Wachstum des laufenden Jahres liegt ueber oder unter dem eigenen Trend des Landes, die
//    Richtung kommt aus der IWF-Prognose fuer das Folgejahr.
//    Trend: Median des realen Wachstums der letzten zehn Jahre vor dem laufenden Jahr. Das Jahr 2020 (Pandemie) bleibt
//    draussen. Der Median ist unempfindlich gegen einzelne Ausreisser wie den Aufholsprung 2021, ein Mittelwert waere es
//    nicht. Mindestens vier Jahreswerte, sonst keine Phase.
//    Richtung: Wachstum im Folgejahr gleich oder hoeher als im laufenden Jahr = steigend. Fehlt die Prognose, zaehlt das
//    laufende Jahr gegen das Vorjahr.
object Konjunkturphase {
    const val FENSTER = 10
    const val MIN_JAHRE = 4
    const val AUSGELASSEN = 2020

    // Woher die Phase stammt: Fruehindikator (monat "JJJJ-MM") oder IWF-Wachstum (jahr und Folgejahr)
    class Ergebnis(val phase: Phase, val ausCli: Boolean, val monat: String? = null, val jahr: Int = 0)

    private fun median(w: List<Double>): Double {
        val s = w.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    // Trend des Landes (Median) oder null bei zu wenigen Jahren
    fun trend(d: Imf.Datensatz, code: String, jahr: Int): Double? {
        val w = (jahr - FENSTER until jahr).filter { it != AUSGELASSEN }.mapNotNull { Imf.wertGenau(d, Imf.WACHSTUM, code, it) }
        return if (w.size >= MIN_JAHRE) median(w) else null
    }

    fun phase(ueber: Boolean, steigend: Boolean): Phase =
        if (ueber) (if (steigend) Phase.AUFSCHWUNG else Phase.ABSCHWAECHUNG) else (if (steigend) Phase.ERHOLUNG else Phase.ABSCHWUNG)

    fun ausImf(d: Imf.Datensatz?, code: String, jahr: Int): Phase? {
        if (d == null) return null
        val jetzt = Imf.wertGenau(d, Imf.WACHSTUM, code, jahr) ?: return null
        val trend = trend(d, code, jahr) ?: return null
        val spaeter = Imf.wertGenau(d, Imf.WACHSTUM, code, jahr + 1)
        val steigend = if (spaeter != null) spaeter >= jetzt else {
            val vorher = Imf.wertGenau(d, Imf.WACHSTUM, code, jahr - 1) ?: return null
            jetzt >= vorher
        }
        return phase(jetzt > trend, steigend)
    }

    // Phase eines Landes (alpha-3): OECD-Fruehindikator, sonst IWF-Wachstum, sonst null.
    fun von(cli: Fruehindikator.Datensatz?, imf: Imf.Datensatz?, code: String, monat: YearMonth, jahr: Int): Ergebnis? {
        val reihe = Fruehindikator.aktuell(cli?.laender?.get(code), monat)
        Fruehindikator.phase(reihe)?.let { return Ergebnis(it, true, reihe!!.last().monat) }
        return ausImf(imf, code, jahr)?.let { Ergebnis(it, false, null, jahr) }
    }

    // Laender im Abschwung unter allen Laendern mit Phase, dazu ihr Anteil an der Wirtschaftsleistung (BIP in Dollar).
    class Welt(val abschwung: Int, val mitPhase: Int, val bipAbschwung: Double, val bipGesamt: Double) {
        val bipAnteil: Double? get() = if (bipGesamt > 0) bipAbschwung / bipGesamt else null
    }

    fun welt(cli: Fruehindikator.Datensatz?, imf: Imf.Datensatz?, monat: YearMonth, jahr: Int): Welt {
        if (imf == null) return Welt(0, 0, 0.0, 0.0)
        var n = 0; var ab = 0; var bipAb = 0.0; var bip = 0.0
        for (code in Imf.laenderCodes(imf)) {
            val p = von(cli, imf, code, monat, jahr)?.phase ?: continue
            val b = Imf.wert(imf, Imf.BIP, code, jahr) ?: 0.0
            n++; bip += b
            if (p == Phase.ABSCHWUNG) { ab++; bipAb += b }
        }
        return Welt(ab, n, bipAb, bip)
    }
}
