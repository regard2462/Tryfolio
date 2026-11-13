package de.troetenrepublik.app.engine

// [0.78] Regeln fuer den Aufbau von Blaettern: Massstab der Anteilsbalken, Verteilung von Feldern auf zwei Spalten,
// Beschriftung von Kacheln und die gemeinsame Jahreszahl einer Legende.
object Blattaufbau {
    // Laenge eines Balkens, dessen Zahl ein Anteil in Prozent ist: Massstab 0 bis 100 %, unabhaengig vom groessten
    // Eintrag der Liste (63,9 % fuellt knapp zwei Drittel).
    fun balkenAnteil(prozent: Double): Float = if (prozent.isFinite()) (prozent / 100.0).coerceIn(0.0, 1.0).toFloat() else 0f

    // Felder zeilenweise auf zwei Spalten: schmale Felder paarweise, ein breites Feld (langer Wert) allein in seiner Zeile.
    // Ein breites Feld wartet, bis das offene schmale Feld seinen Nachbarn hat, damit keine halbe Zeile leer bleibt. Nur
    // bei ungerader Zahl schmaler Felder steht das letzte allein. Liefert je Zeile die Nummern der Felder.
    fun feldZeilen(breit: List<Boolean>): List<List<Int>> {
        val aus = ArrayList<List<Int>>()
        var offen: Int? = null
        val wartend = ArrayList<Int>()
        for (i in breit.indices) {
            if (breit[i]) {
                if (offen == null) aus += listOf(i) else wartend += i
            } else if (offen == null) {
                offen = i
            } else {
                aus += listOf(offen, i)
                offen = null
                for (w in wartend) aus += listOf(w)
                wartend.clear()
            }
        }
        for (w in wartend) aus += listOf(w)
        offen?.let { aus += listOf(it) }
        return aus
    }

    // Beschriftung einer Kachel: die erste der Bezeichnungen (ganzer Name, dann Kuerzel), die ganz in die Kachel passt;
    // -1, wenn keine passt. Nichts wird angeschnitten.
    fun kachelName(breiten: List<Int>, platz: Int): Int = breiten.indexOfFirst { it in 1..platz }

    // Jahr, das fuer alle Zeilen gleich ist und deshalb einmal statt in jeder Zeile steht; null, wenn keine Zeile ein Jahr
    // zeigt oder die Jahre sich unterscheiden (dann bleibt das Jahr an der Zeile).
    fun gemeinsamesJahr(jahre: List<Int?>): Int? {
        val mit = jahre.filterNotNull()
        if (mit.isEmpty() || mit.size != jahre.size) return null
        return mit.first().takeIf { j -> mit.all { it == j } }
    }
}
