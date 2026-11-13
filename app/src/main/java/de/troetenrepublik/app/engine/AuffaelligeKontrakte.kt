package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt

// Auffaellige Optionen je Wert (0.78): die Grundregel aus Optionsanalyse.auffaellig (Umsatz gegen offene Kontrakte,
// Verfall, Praemie), hier mit waehlbarem Mindestvolumen. Zeigt nur Volumen, nicht die Handelsrichtung.
object AuffaelligeKontrakte {

    val MIN_VOLUMEN = listOf(100L, 500L, 1000L, 5000L)
    const val JE_WERT = 3

    enum class Art { ALLE, CALLS, PUTS }

    // abstand: Basispreis zum Kurs als Anteil (0,05 = fuenf Prozent ueber dem Kurs); praemie: Volumen x Mitte x 100
    // verhaeltnis: Volumen durch offene Kontrakte; bei 0 offenen Kontrakten gibt es keinen Faktor (neu, verhaeltnis 0)
    data class Zeile(val kontrakt: Kontrakt, val verhaeltnis: Double, val abstand: Double, val praemie: Double) {
        val neu: Boolean get() = kontrakt.offen <= 0L
    }

    // callUmsatz und putUmsatz: Tagesvolumen der Kette, unabhaengig vom Filter, gerechnet wie Put/Call (Optionsanalyse.umsatz)
    data class Wert(val zeilen: List<Zeile>, val callUmsatz: Long, val putUmsatz: Long)

    private const val TAG_S = 86_400L

    fun verhaeltnis(k: Kontrakt): Double = if (k.offen <= 0L) 0.0 else k.umsatz.toDouble() / k.offen

    // Null, wenn der Kurs fehlt oder kein Kontrakt den Filter besteht
    fun auswerten(kurs: Double, kontrakte: List<Kontrakt>, minVolumen: Long, art: Art, jetztS: Long, max: Int = JE_WERT): Wert? {
        if (kurs <= 0) return null
        val gueltig = kontrakte.filter { it.verfall > jetztS - TAG_S }
        if (Optionsanalyse.ohneOffene(gueltig)) return null
        val zeilen = gueltig
            .filter { it.umsatz >= minVolumen && it.umsatz > 0 }
            .filter { art == Art.ALLE || (art == Art.CALLS) == it.call }
            .filter { Optionsanalyse.auffaellig(it, jetztS) }
            .map { Zeile(it, verhaeltnis(it), it.strike / kurs - 1, Optionsanalyse.praemie(it)) }
            // Neue Kontrakte ohne Faktor folgen nach denen mit Faktor, nach Volumen geordnet
            .sortedWith(compareByDescending<Zeile> { it.verhaeltnis }.thenByDescending { it.kontrakt.umsatz })
            .take(max)
        if (zeilen.isEmpty()) return null
        val umsatz = Optionsanalyse.umsatz(gueltig, jetztS)
        return Wert(zeilen, umsatz.calls, umsatz.puts)
    }
}
