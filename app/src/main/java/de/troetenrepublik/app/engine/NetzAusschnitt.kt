package de.troetenrepublik.app.engine

// Sichtbarer Ausschnitt des Netzes: Start (Firma mit Nachbarn, Radius 1), danach beliebig erweiterbar, Schritt fuer Schritt
// zuruecknehmbar. Der Ausschnitt haelt nur Knoten-Kennungen; die Kanten ergeben sich aus dem Netz und dem Filter.
class NetzAusschnitt private constructor(val start: String, val sichtbar: Set<String>, val schritte: List<Schritt>) {

    // knoten: erweiterter Knoten, neu: dabei hinzugekommene Knoten
    class Schritt(val knoten: String, val neu: List<String>)

    companion object {
        // So viele Nachbarn kommen je Erweiterung hoechstens dazu; weitere folgen beim naechsten Tippen
        const val MAX_NEU = 12

        val ALLE: Set<Netz.Gruppe> = Netz.Gruppe.entries.toSet()

        fun start(g: Netz.Graph, id: String, filter: Set<Netz.Gruppe> = ALLE, heute: String, maxNeu: Int = MAX_NEU): NetzAusschnitt {
            val nachbarn = nachbarn(g, id, filter, heute).take(maxNeu)
            return NetzAusschnitt(id, linkedSetOf(id).also { it.addAll(nachbarn) }, emptyList())
        }

        // Ausschnitt aus mehreren Knoten (etwa der Einstieg "KI-Kreislaeufe"); start bleibt der Knoten im Zentrum
        fun ausListe(start: String, ids: Collection<String>): NetzAusschnitt = NetzAusschnitt(start, LinkedHashSet(listOf(start) + ids), emptyList())

        // Nachbarn eines Knotens ueber aktive Kanten im Filter, wichtigste zuerst (Betrag in USD, Guete, Name)
        fun nachbarn(g: Netz.Graph, id: String, filter: Set<Netz.Gruppe>, heute: String): List<String> {
            val gewicht = HashMap<String, Double>()
            for (k in g.kanten(id)) {
                if (k.art.gruppe !in filter || !k.aktiv(heute)) continue
                val a = k.andere(id)
                gewicht[a] = maxOf(gewicht[a] ?: 0.0, g.gewicht(k))
            }
            return gewicht.keys.sortedWith(compareBy({ -(gewicht[it] ?: 0.0) }, { g.knoten[it]?.name ?: it }))
        }
    }

    // Noch nicht sichtbare Nachbarn eines Knotens
    fun rest(g: Netz.Graph, id: String, filter: Set<Netz.Gruppe>, heute: String): List<String> =
        nachbarn(g, id, filter, heute).filter { it !in sichtbar }

    // Erweitert um die wichtigsten noch nicht sichtbaren Nachbarn; ohne neue Nachbarn bleibt der Ausschnitt gleich (this).
    fun erweitern(g: Netz.Graph, id: String, filter: Set<Netz.Gruppe>, heute: String, maxNeu: Int = MAX_NEU): NetzAusschnitt {
        if (id !in sichtbar) return this
        val neu = rest(g, id, filter, heute).take(maxNeu)
        if (neu.isEmpty()) return this
        return NetzAusschnitt(start, LinkedHashSet(sichtbar).also { it.addAll(neu) }, schritte + Schritt(id, neu))
    }

    // Nimmt die letzte Erweiterung zurueck; null, wenn nichts zurueckzunehmen ist (Start)
    fun zurueck(): NetzAusschnitt? {
        val letzter = schritte.lastOrNull() ?: return null
        return NetzAusschnitt(start, LinkedHashSet(sichtbar).also { it.removeAll(letzter.neu.toSet()) }, schritte.dropLast(1))
    }

    fun enthaelt(id: String): Boolean = id in sichtbar
}
