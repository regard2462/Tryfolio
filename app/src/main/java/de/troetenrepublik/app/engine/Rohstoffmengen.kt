package de.troetenrepublik.app.engine

// [0.76] Menge und Wert der Weltfoerderung eines Rohstoffs, aufgeschluesselt nach Foerderlaendern.
// Menge je Land aus der Datei welt/rohstoffe.json; die Weltmenge ist immer die Summe der Laender plus Rest ohne Laenderangabe.
// Wert = Menge x Preis des Jahres (US-Dollar, die Anzeige rechnet in die Anzeigewaehrung um). Ohne Preis gibt es keinen Wert.
object Rohstoffmengen {
    // land null: Uebrige Laender (Rest ohne Laenderangabe)
    class Zeile(val land: String?, val menge: Double, val anteil: Double, val wert: Double?)

    class Aufschluesselung(
        val einheit: String, val jahr: Int, val welt: Double, val weltWert: Double?, val preisJahr: Int, val preisQuelle: String?,
        val zeilen: List<Zeile>,
    )

    const val QUELLE_MCS = "mcs"
    const val QUELLE_MYB = "myb"

    // Quellen der Mengen und Foerderanteile der gezeigten Rohstoffe: myb (Minerals Yearbook), sonst mcs (auch ohne Mengen und in
    // aelteren Datenstaenden ohne Quellenfeld)
    fun mengenQuellen(rohstoffe: List<Einflussverzeichnis.Rohstoff>): Set<String> =
        rohstoffe.map { if (it.mengen?.mengenQuelle == QUELLE_MYB) QUELLE_MYB else QUELLE_MCS }.toSet()

    // Anteile unter 0,01 Prozent stehen als "< 0,01 %", nie als "0,00 %"
    const val ANTEIL_KLEIN = 0.01
    fun anteilKlein(anteil: Double): Boolean = anteil > 0.0 && anteil < ANTEIL_KLEIN

    // Wert einer Menge; null ohne Preis
    fun wert(menge: Double, preis: Double?): Double? = preis?.let { menge * it }

    // Alle Laender nach Menge absteigend, der Rest zuletzt; null, wenn der Rohstoff keine Mengen hat (aeltere Datenstaende)
    fun aufschluesseln(r: Einflussverzeichnis.Rohstoff): Aufschluesselung? {
        val m = r.mengen ?: return null
        val laender = m.laender.filter { it.menge > 0.0 }.sortedWith(compareByDescending<Einflussverzeichnis.LandMenge> { it.menge }.thenBy { it.land })
        val rest = m.rest.coerceAtLeast(0.0)
        val welt = laender.sumOf { it.menge } + rest
        if (welt <= 0.0) return null
        val zeilen = laender.map { Zeile(it.land, it.menge, it.menge / welt * 100.0, wert(it.menge, m.preis)) }.toMutableList()
        if (rest > 0.0) zeilen += Zeile(null, rest, rest / welt * 100.0, wert(rest, m.preis))
        return Aufschluesselung(m.einheit, m.jahr, welt, wert(welt, m.preis), m.preisJahr, m.preisQuelle, zeilen)
    }

    // Kapazitaet der Bergwerke eines Unternehmens: land null wird nie gebraucht, die Laender stehen nach Kapazitaet absteigend
    class KapazitaetZeile(val land: String, val menge: Double, val anteil: Double)

    class Kapazitaet(val einheit: String, val jahr: Int, val gesamt: Double, val verwertbar: Int, val anlagen: Int, val zeilen: List<KapazitaetZeile>) {
        // Nicht jede Anlage des Unternehmens nennt eine Kapazitaet
        val luecken: Boolean get() = verwertbar < anlagen
    }

    // Kapazitaet je Land; null ohne Einheit des Rohstoffs (keine Mengen) oder ohne Kapazitaet
    fun kapazitaet(r: Einflussverzeichnis.Rohstoff, a: Einflussverzeichnis.Anlage?): Kapazitaet? {
        val einheit = r.mengen?.einheit ?: return null
        if (a == null) return null
        val laender = a.laender.filter { it.menge > 0.0 }.sortedWith(compareByDescending<Einflussverzeichnis.LandMenge> { it.menge }.thenBy { it.land })
        val gesamt = laender.sumOf { it.menge }
        if (gesamt <= 0.0) return null
        return Kapazitaet(einheit, a.jahr, gesamt, a.verwertbar, a.gesamt, laender.map { KapazitaetZeile(it.land, it.menge, it.menge / gesamt * 100.0) })
    }
}
