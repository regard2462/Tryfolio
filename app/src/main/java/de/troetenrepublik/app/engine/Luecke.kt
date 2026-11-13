package de.troetenrepublik.app.engine

import java.time.LocalDate

// Luecken in Momentaufnahmen (Insidergeschaefte der SEC, der BaFin und der EU-Register): Der mitgelieferte Stand reicht bis zu
// einem Tag (stand). Holt die App spaeter nur die letzten Wochen nach, bleibt zwischen Stand und dem Beginn des nachgeholten
// Zeitraums ein Loch. Statt es zu verschweigen, merkt sich die App die Spanne und zeigt "Daten bis TT.MM.JJJJ" samt Luecke.
object Luecke {

    // Spanne ohne Daten: von ist der letzte Tag mit Daten vor dem Loch, bis der erste Tag der nachgeholten Daten.
    data class Spanne(val von: String, val bis: String) {
        val leer: Boolean get() = von.isEmpty() || bis.isEmpty()
    }

    // Wie viele Tage Abstand zwischen Stand und Beginn des Abrufs als lueckenlos gelten (Wochenenden, Meldefristen).
    const val TOLERANZ_TAGE = 4

    private fun tag(s: String): Long? = runCatching { LocalDate.parse(s).toEpochDay() }.getOrNull()

    // Ergebnis eines Abrufs, der lueckenlos von abdeckungAb bis heute reicht: der neue Stand (heute) und die Luecke.
    // Beginnt der Abruf spaeter als stand + Toleranz, entsteht (oder waechst) eine Luecke von stand bis abdeckungAb.
    // Ein Abruf, der bis vor den Beginn der alten Luecke zurueckreicht, schliesst sie.
    fun nachAbruf(stand: String, alt: Spanne?, abdeckungAb: String, heute: String): Pair<String, Spanne?> {
        val s = tag(stand)
        val ab = tag(abdeckungAb) ?: return heute to alt?.takeIf { !it.leer }
        var luecke: Spanne? = alt?.takeIf { !it.leer }
        // Ohne lesbaren Stand laesst sich keine Luecke beziffern
        if (s != null && ab - s > TOLERANZ_TAGE) {
            luecke = if (luecke == null) Spanne(stand, abdeckungAb)
            else Spanne(minOf(luecke.von, stand), maxOf(luecke.bis, abdeckungAb))
        }
        // Reicht der Abruf bis vor die alte Luecke, ist sie gefuellt
        luecke?.let { l -> val lv = tag(l.von); if (lv != null && ab <= lv + TOLERANZ_TAGE) luecke = null }
        return heute to luecke
    }

    // Beginn des Zeitraums, den ein Abruf holt: ab dem Stand (drei Tage Puffer), hoechstens maxTage zurueck; liegt der Stand
    // weiter zurueck, beginnt der Abruf maxTage vor heute und es entsteht eine Luecke.
    fun abrufBeginn(stand: String, heute: String, maxTage: Int, ohneStand: Int): String {
        val h = tag(heute) ?: return heute
        val s = tag(stand)
        val frueh = h - maxTage
        val von = if (s == null) h - ohneStand else maxOf(s - 3, frueh)
        return LocalDate.ofEpochDay(von).toString()
    }

    // Was die Seite zeigt: Datum der Daten und, wenn vorhanden, die Luecke (Daten bis, Luecke von bis).
    class Anzeige(val bis: String, val luecke: Spanne?)

    fun anzeige(stand: String, luecke: Spanne?): Anzeige? =
        if (tag(stand) == null) null else Anzeige(stand, luecke?.takeIf { !it.leer })
}
