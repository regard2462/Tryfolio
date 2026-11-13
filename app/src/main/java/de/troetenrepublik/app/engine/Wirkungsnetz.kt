package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Einflussverzeichnis.Typ

// [Grosses Ganzes 0.73] Wirkungsnetz "Was heisst das fuer mich?": von einer Einflussgroesse zu Laendern, Branchen und den
// eigenen Werten in Depot und Watchlist. Die Anzeige nennt eine Richtung nur, wenn sie belegt ist (Kante mit Richtung
// oder gemessene Kursempfindlichkeit); sonst steht nur "betroffen". Einen Zahlenwert fuer die Wirkung gibt es nicht.
object Wirkungsnetz {
    // gemessen: +1 oder -1, wenn die Kursempfindlichkeit des Werts gegen diese Groesse belegt ist, sonst null
    class Wirkung(val bestand: Bestand, val kante: Einflussverzeichnis.Kante, val gemessen: Int?) {
        // Richtung der Anzeige: gemessen vor belegter Kante; 0 = nur betroffen
        val richtung: Int get() = gemessen ?: kante.richtung
        val art: Einflussverzeichnis.Art get() = if (gemessen != null) Einflussverzeichnis.Art.GEMESSEN else kante.art
    }

    class Eintrag(
        val groesse: Einflussverzeichnis.Groesse, val laender: List<String>, val branchen: List<String>,
        val depot: List<Wirkung>, val watchlist: List<Wirkung>, val belege: List<String>,
    ) {
        // Summe der Depotanteile der getroffenen Werte
        val depotAnteil: Double get() = depot.sumOf { it.bestand.gewicht }
        val treffer: Int get() = depot.size + watchlist.size
    }

    // Welcher Faktor der Kursempfindlichkeit passt zu einer Groesse; null, wenn keiner passt
    fun faktorFuer(groesse: String): Empfindlichkeit.Faktor? = when (groesse) {
        "zins" -> Empfindlichkeit.Faktor.ZINS10J
        "oel" -> Empfindlichkeit.Faktor.OEL
        "dollar" -> Empfindlichkeit.Faktor.DOLLAR
        "china" -> Empfindlichkeit.Faktor.CHINA
        "halbleiter" -> Empfindlichkeit.Faktor.HALBLEITER
        "rohstoff:KUPFER" -> Empfindlichkeit.Faktor.KUPFER
        "rohstoff:GOLD" -> Empfindlichkeit.Faktor.GOLD
        else -> null
    }

    // Gemessene Richtung eines Werts gegen eine Groesse: nur bei belegtem Zusammenhang
    fun gemessen(e: Empfindlichkeit.Ergebnis?, groesse: String): Int? {
        val f = faktorFuer(groesse) ?: return null
        val k = e?.koeffizienten?.firstOrNull { it.faktor == f && it.urteil == Empfindlichkeit.Urteil.BELEGT } ?: return null
        return if (k.wert > 0) 1 else -1
    }

    // Eintrag einer Groesse; null, wenn sie unbekannt oder abgelaufen ist. depot: Positionen mit Anteil, watchlist: ohne.
    fun eintrag(
        i: Einflussverzeichnis.Index, id: String, depot: List<Bestand>, watchlist: List<Bestand>,
        messungen: Map<String, Empfindlichkeit.Ergebnis>, heute: Long,
    ): Eintrag? {
        // Ein Land als Groesse: die Werte, die dort sitzen oder wirtschaften (Laenderblatt, Strukturprobleme)
        if (id.startsWith("land:")) {
            val a2 = id.removePrefix("land:")
            val land = Einflussverzeichnis.Groesse(id, Typ.LAND, "", "", "", "", 0L, 0L)
            val kante = Einflussverzeichnis.Kante(id, Einflussverzeichnis.Ziel(Einflussverzeichnis.ZielArt.LAND, a2), Einflussverzeichnis.Art.ABGELEITET, 0, "Sitz oder Taetigkeitsland des Werts", 0L, 0L)
            fun dort(b: List<Bestand>) = b.filter { it.wert.land == a2 || it.wert.sitz == a2 }.sortedByDescending { it.gewicht }.map { Wirkung(it, kante, null) }
            return Eintrag(land, listOf(a2), emptyList(), dort(depot), dort(watchlist), emptyList())
        }
        val g = i.groessen[id] ?: return null
        if (g.ablauf != 0L && g.ablauf < heute) return null
        val kanten = Einflussverzeichnis.kantenVon(i, id, heute)
        fun wirkungen(b: List<Bestand>) = Einflussverzeichnis.imDepot(i, id, b, heute).map {
            Wirkung(it.bestand, it.kante, gemessen(messungen[it.bestand.wert.symbol], id))
        }
        return Eintrag(
            g, Einflussverzeichnis.laender(i, id, heute), Einflussverzeichnis.branchen(i, id, heute),
            wirkungen(depot), wirkungen(watchlist),
            kanten.map { it.beleg }.filter { it.isNotBlank() }.distinct(),
        )
    }

    // Reihenfolge der Uebersicht: Groessen mit Treffern im Depot zuerst (nach Depotanteil), dann Watchlist, dann nach Typ
    fun uebersicht(
        i: Einflussverzeichnis.Index, depot: List<Bestand>, watchlist: List<Bestand>, messungen: Map<String, Empfindlichkeit.Ergebnis>, heute: Long,
    ): List<Eintrag> =
        i.groessen.keys.mapNotNull { eintrag(i, it, depot, watchlist, messungen, heute) }
            .filter { it.groesse.typ != Typ.ROHSTOFF || it.treffer > 0 }
            .sortedWith(compareByDescending<Eintrag> { it.depotAnteil }.thenByDescending { it.watchlist.size }.thenBy { it.groesse.typ.ordinal }.thenBy { it.groesse.id })

    // [0.75] Abgelaufene Groessen (ohne neue einfluss.json): Die Uebersicht blendet sie aus und nennt Anzahl und Ablauf.
    fun ausgeblendet(i: Einflussverzeichnis.Index, heute: Long): Pair<Int, Long>? = Einflussverzeichnis.abgelaufeneGroessen(i, heute)

    // [0.75] Ablauf (Tage seit 1970) einer einzelnen Groesse, wenn er vorbei ist; sonst null
    fun abgelaufen(i: Einflussverzeichnis.Index, id: String, heute: Long): Long? =
        i.groessen[id]?.ablauf?.takeIf { it != 0L && it < heute }

    // Groessen, die mindestens eine Position treffen
    fun mitTreffern(e: List<Eintrag>): List<Eintrag> = e.filter { it.treffer > 0 }
}
