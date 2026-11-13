package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// [Grosses Ganzes 0.73] Mitteilung "Betrifft dein Depot": ein Ereignis aus dem Einflussverzeichnis (ein Engpass mit Rueckgang
// gegen das Vorjahr, ein Spannungsgebiet mit deutlich mehr Berichten), das Werte im Depot trifft. Hoechstens eine am Tag,
// jedes Ereignis hoechstens einmal in 30 Tagen, und nur bei belegter Verbindung: Der Wert muss in der Liste der Groesse
// genannt sein (nicht nur ueber seine Branche betroffen) und im Depot liegen.
object WeltMeldung {
    const val SPANNUNG_MIN = 2.0
    const val SPERRE_TAGE = 30L
    const val MAX_NAMEN = 3

    // staerke: Vergleichszahl der Ereignisse (1 = gerade auffaellig); wert: Abweichung in Prozent bzw. Faktor
    class Ereignis(val groesse: String, val typ: Einflussverzeichnis.Typ, val wert: Double, val staerke: Double)

    class Meldung(val ereignis: Ereignis, val namen: List<String>, val rest: Int)

    // tag: Tag der letzten Mitteilung; ids: Tag der letzten Mitteilung je Ereignis
    @Serializable
    class Gemerkt(val tag: Long = 0L, val ids: Map<String, Long> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }
    fun schreiben(g: Gemerkt): String = json.encodeToString(Gemerkt.serializer(), g)
    fun lesen(text: String): Gemerkt? = runCatching { json.decodeFromString(Gemerkt.serializer(), text) }.getOrNull()

    // Auffaellige Ereignisse, die staerksten zuerst
    fun ereignisse(u: Engpaesse.Uebersicht?, spannungen: Map<String, Spannungen.Wert>): List<Ereignis> {
        val aus = ArrayList<Ereignis>()
        if (u != null) for ((stelle, lage) in Engpaesse.sortiert(u)) {
            val abw = lage.abw ?: continue
            if (abw <= Engpaesse.STOERUNG_PROZENT && (lage.vn ?: 0.0) >= Engpaesse.MIND_VORJAHR) {
                aus += Ereignis("engpass:" + stelle.id, Einflussverzeichnis.Typ.ENGPASS, abw, abw / Engpaesse.STOERUNG_PROZENT)
            }
        }
        for ((id, w) in spannungen) if (w.faktor >= SPANNUNG_MIN) {
            aus += Ereignis("spannung:$id", Einflussverzeichnis.Typ.SPANNUNG, w.faktor, w.faktor / SPANNUNG_MIN)
        }
        return aus.sortedByDescending { it.staerke }
    }

    // Die Meldung des Tages oder null (heute schon gemeldet, Ereignis zu frisch gemeldet, kein genannter Wert im Depot)
    fun waehlen(i: Einflussverzeichnis.Index, ereignisse: List<Ereignis>, depot: List<Bestand>, gemerkt: Gemerkt, heute: Long): Meldung? {
        if (gemerkt.tag == heute) return null
        for (e in ereignisse) {
            val zuletzt = gemerkt.ids[e.groesse]
            if (zuletzt != null && heute - zuletzt < SPERRE_TAGE) continue
            val treffer = Einflussverzeichnis.imDepot(i, e.groesse, depot.filter { it.gewicht > 0 }, heute)
                .filter { it.kante.ziel.art == Einflussverzeichnis.ZielArt.WERT }
            if (treffer.isEmpty()) continue
            return Meldung(e, treffer.take(MAX_NAMEN).map { it.bestand.name }, (treffer.size - MAX_NAMEN).coerceAtLeast(0))
        }
        return null
    }

    // [0.75] Einordnungen, ueber die Engpaesse und Spannungsgebiete genannte Werte im Depot nur noch abgelaufen treffen
    // (Anzahl, spaetester Ablauf in Tagen seit 1970). Fuer sie kommt keine Mitteilung mehr; die Einstellungen sagen das.
    fun ausgeblendet(i: Einflussverzeichnis.Index, depot: List<Bestand>, heute: Long): Pair<Int, Long>? =
        Einflussverzeichnis.abgelaufen(i, depot.filter { it.gewicht > 0 }.map { it.wert }, heute) { k ->
            k.ziel.art == Einflussverzeichnis.ZielArt.WERT &&
                i.groessen[k.groesse]?.typ.let { it == Einflussverzeichnis.Typ.ENGPASS || it == Einflussverzeichnis.Typ.SPANNUNG }
        }

    // Stand nach einer Meldung; alte Eintraege (aelter als die Sperre) fallen heraus
    fun merken(g: Gemerkt, m: Meldung, heute: Long): Gemerkt =
        Gemerkt(heute, g.ids.filterValues { heute - it < SPERRE_TAGE } + (m.ereignis.groesse to heute))
}
