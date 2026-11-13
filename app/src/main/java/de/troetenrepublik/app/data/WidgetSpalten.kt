package de.troetenrepublik.app.data

// Spalten der Widget-Zeilen (Name, Kurs, Veraenderung) und Trenner zwischen Angaben. Reine Rechnung ohne Android.
// Namen werden nie abgeschnitten: sie brechen um, und wo die Breite fuer ein ganzes Wort nicht reicht, faellt zuerst die
// Kursspalte weg. Werte brechen nie um und werden nie verkleinert: passt eine Angabe nicht, zeigt das Widget die kuerzere
// Schreibweise oder die Angaben untereinander.
object WidgetSpalten {
    // Platz am Rand des Widgets (je 14 dp) und kleinste Breite der Veraenderungsspalte, wie in den Layouts
    const val RAND = 28f
    const val PROZENT_MIN = 64f
    private const val ABSTAND = 8f
    private const val SCHRIFT = 13f
    private const val TRENNER = " · "
    private val FEST: Char = 160.toChar()

    // Geschuetzte Leerstelle: ein Wert wie „16.607,77 €“ bricht an ihr nie um.
    fun fest(text: String): String = text.replace(' ', FEST)

    // Teile mit Trenner, aber nur zwischen Teilen mit Inhalt: nie ein loser Punkt am Anfang, Ende oder doppelt.
    fun mitTrenner(vararg teile: String, trenner: String = TRENNER): String =
        teile.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(trenner)

    // Breite eines Werts in dp bei sp Schriftgroesse, mit der Schriftgroesse des Geraets (skala) vergroessert.
    private fun textB(t: String, sp: Float, skala: Float, fett: Boolean = false): Float =
        if (t.isEmpty()) 0f else WidgetKarten.textBreite(t, sp, fett) * skala

    private fun laengstesWort(t: String, skala: Float): Float =
        t.split(' ', FEST).maxOfOrNull { WidgetKarten.textBreite(it, SCHRIFT) * skala } ?: 0f

    // Zeigt die Liste die Kursspalte? Ja, solange neben Kurs und Veraenderung noch das laengste Wort jedes Namens ganz
    // in die Zeile passt (der Name darf in zwei Zeilen umbrechen). Eine Entscheidung fuer alle Zeilen, damit die Spalten
    // gleich aussehen. breite null oder kleiner: unbekannt, Kursspalte bleibt.
    fun kursSichtbar(namen: List<String>, kurse: List<String>, prozente: List<String>, breite: Int, skala: Float = 1f): Boolean {
        if (breite <= 0) return true
        val s = skala.coerceIn(0.8f, 2f)
        val innen = breite - RAND
        val prozent = maxOf(PROZENT_MIN, (prozente.maxOfOrNull { textB(it, SCHRIFT, s, true) } ?: 0f) + ABSTAND)
        val kurs = (kurse.maxOfOrNull { textB(it, SCHRIFT, s) } ?: 0f).let { if (it > 0f) it + ABSTAND else 0f }
        val wort = namen.maxOfOrNull { laengstesWort(it, s) } ?: 0f
        return kurs == 0f || innen - prozent - kurs >= wort
    }

    // Der grosse Betrag: die erste Schreibweise, die in die Breite passt (zuerst mit Nachkommastellen, dann ohne), sonst die
    // letzte. Immer mit Waehrungszeichen, ohne Umbruchstelle.
    fun betragWahl(kandidaten: List<String>, breite: Int, sp: Float, skala: Float = 1f): String {
        val liste = kandidaten.filter { it.isNotBlank() }
        if (liste.isEmpty()) return ""
        val s = skala.coerceIn(0.8f, 2f)
        val innen = breite - RAND
        val wahl = if (breite <= 0) liste.first() else liste.firstOrNull { textB(fest(it), sp, s, true) <= innen } ?: liste.last()
        return fest(wahl)
    }

    // Aenderung unter dem Betrag: Betrag und Prozent mit Trenner in einer Zeile, wenn beides passt; sonst Betrag in der
    // ersten und Prozent in der zweiten Zeile. Fehlt ein Teil, steht der andere allein und es gibt keinen Trenner.
    class Aenderung(val erste: String, val zweite: String)

    fun aenderung(betrag: String, prozent: String, breite: Int, skala: Float = 1f): Aenderung {
        val b = fest(betrag.trim())
        val p = fest(prozent.trim())
        if (b.isEmpty() || p.isEmpty()) return Aenderung(b.ifEmpty { p }, "")
        val s = skala.coerceIn(0.8f, 2f)
        val eine = mitTrenner(b, p)
        if (breite <= 0 || textB(eine, SCHRIFT, s) <= breite - RAND) return Aenderung(eine, "")
        return Aenderung(b, p)
    }
}
