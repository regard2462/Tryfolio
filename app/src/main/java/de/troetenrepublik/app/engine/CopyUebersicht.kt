package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.SecInsider
import java.time.LocalDate
import java.time.ZoneOffset

// Uebersicht je Gruppe im Copytrading (Repraesentantenhaus, Senat, Regierung, Investoren, Insider): groesste Positionen,
// Meistgehandeltes und Werte unter dem Einstiegskurs. Alle Zahlen sind Schaetzungen aus den gemeldeten Geschaeften:
// Betragsspannen zaehlen mit ihrer Mitte, Investoren mit den gemeldeten Bestaenden.
//
// Einstiegskurs: Schlusskurs am Tag des Geschaefts, bei mehreren Kaeufen derselben Person der Mittelwert der Kurse,
// gewichtet nach dem Betrag. Hat die Quelle den Preis selbst (Insider, Investoren), gilt der.
object CopyUebersicht {

    const val TAG = 86_400_000L

    // Waehlbare Zeitraeume fuer Meistgehandeltes, in Tagen.
    val ZEITRAEUME = listOf(30, 90, 365)

    // Ein Kauf oder Verkauf. person ist der Schluessel der Person (k:, n:, r:, o:, i: wie beim Folgen), symbol das
    // Kuerzel fuer Yahoo, betrag die Mitte der Spanne in Dollar. preis: Preis je Stueck, wenn die Quelle ihn nennt.
    // gemeldet: Tag der Meldung, falls die Zeitraeume danach statt nach dem Geschaeft laufen sollen (Investoren).
    data class Geschaeft(
        val person: String,
        val name: String,
        val symbol: String,
        val kauf: Boolean,
        val datum: Long,
        val betrag: Double,
        val preis: Double? = null,
        val gemeldet: Long = 0L,
    ) {
        val stichtag: Long get() = if (gemeldet > 0) gemeldet else datum
    }

    // Gemeldeter Bestand einer Person (Wert in Dollar).
    data class Bestand(val person: String, val name: String, val symbol: String, val wert: Double)

    data class Halter(val person: String, val name: String, val wert: Double)

    data class Position(val symbol: String, val wert: Double, val personen: Int, val halter: List<Halter>)

    // Je Person und Aktie die laufende Summe: Kaeufe zaehlen dazu, Verkaeufe ziehen ab, nie unter null (wer Aktien
    // verkauft, die schon vor den Daten im Depot lagen, drueckt spaetere Kaeufe nicht ins Minus).
    private fun bestaende(geschaefte: List<Geschaeft>): Map<Pair<String, String>, Pair<String, Double>> {
        val aus = HashMap<Pair<String, String>, Pair<String, Double>>()
        val sortiert = geschaefte.sortedWith(compareBy<Geschaeft> { it.datum }.thenByDescending { it.kauf })
        for (g in sortiert) {
            val k = g.person to g.symbol
            val alt = aus[k]?.second ?: 0.0
            val neu = if (g.kauf) alt + g.betrag else maxOf(0.0, alt - g.betrag)
            aus[k] = g.name to neu
        }
        return aus
    }

    // Groesste Positionen der Gruppe: je Person der gemeldete Bestand, sonst die Summe aus Kaeufen minus Verkaeufen.
    // Sortiert nach Gesamtwert; je Aktie die groessten Halter (hoechstens haelter).
    fun positionen(geschaefte: List<Geschaeft>, gemeldet: List<Bestand> = emptyList(), haelter: Int = 3): List<Position> {
        val wert = HashMap<Pair<String, String>, Pair<String, Double>>()
        for ((k, v) in bestaende(geschaefte)) if (v.second > 0) wert[k] = v
        for (b in gemeldet) if (b.wert > 0) wert[b.person to b.symbol] = b.name to b.wert
        return wert.entries.groupBy({ it.key.second }) { Halter(it.key.first, it.value.first, it.value.second) }.map { (s, l) ->
            val sortiert = l.sortedByDescending { it.wert }
            Position(s, l.sumOf { it.wert }, l.size, sortiert.take(haelter))
        }.sortedByDescending { it.wert }
    }

    data class Umsatz(
        val symbol: String,
        val kaeufe: Int,
        val verkaeufe: Int,
        val kaufVolumen: Double,
        val verkaufVolumen: Double,
        val personen: Int,
    ) {
        val anzahl: Int get() = kaeufe + verkaeufe
        val volumen: Double get() = kaufVolumen + verkaufVolumen
    }

    enum class Ordnung { ANZAHL, VOLUMEN }

    // Geschaefte der letzten tage Tage je Aktie, Kaeufe und Verkaeufe getrennt.
    fun meistgehandelt(geschaefte: List<Geschaeft>, jetzt: Long, tage: Int, ordnung: Ordnung = Ordnung.ANZAHL): List<Umsatz> {
        val ab = jetzt - tage * TAG
        val l = geschaefte.filter { it.stichtag in ab..(jetzt + TAG) }.groupBy { it.symbol }.map { (s, g) ->
            val k = g.filter { it.kauf }
            val v = g.filterNot { it.kauf }
            Umsatz(s, k.size, v.size, k.sumOf { it.betrag }, v.sumOf { it.betrag }, g.map { it.person }.toSet().size)
        }
        return when (ordnung) {
            Ordnung.ANZAHL -> l.sortedWith(compareByDescending<Umsatz> { it.anzahl }.thenByDescending { it.volumen })
            Ordnung.VOLUMEN -> l.sortedWith(compareByDescending<Umsatz> { it.volumen }.thenByDescending { it.anzahl })
        }
    }

    // Eine Person und eine Aktie: gewichteter Einstiegskurs, heutiger Kurs und Abstand (-0,1 = 10 % darunter).
    data class Einstand(
        val person: String,
        val name: String,
        val symbol: String,
        val einstieg: Double,
        val aktuell: Double,
        val abstand: Double,
        val erstes: Long,
        val letztes: Long,
        val kaeufe: Int,
        val betrag: Double,
    )

    // Je Person und Aktie, die sie noch haelt (Summe aus Kaeufen minus Verkaeufen groesser null), der Einstiegskurs.
    // kurs liefert den Schlusskurs am Tag; aktuell den heutigen. Kaeufe ohne Kurs bleiben aussen vor.
    fun einstaende(geschaefte: List<Geschaeft>, kurs: (String, Long) -> Double?, aktuell: (String) -> Double?): List<Einstand> {
        val noch = bestaende(geschaefte).filterValues { it.second > 0 }.keys
        val heute = HashMap<String, Double?>()
        return geschaefte.filter { it.kauf && (it.person to it.symbol) in noch }.groupBy { it.person to it.symbol }.mapNotNull { (k, l) ->
            val jetzt = heute.getOrPut(k.second) { aktuell(k.second) }?.takeIf { it > 0 } ?: return@mapNotNull null
            val mitKurs = l.mapNotNull { g -> (g.preis?.takeIf { it > 0 } ?: kurs(g.symbol, g.datum)?.takeIf { it > 0 })?.let { g to it } }
            val summe = mitKurs.sumOf { it.first.betrag }
            if (mitKurs.isEmpty() || summe <= 0) return@mapNotNull null
            val einstieg = mitKurs.sumOf { it.first.betrag * it.second } / summe
            Einstand(
                k.first, l.first().name, k.second, einstieg, jetzt, jetzt / einstieg - 1,
                mitKurs.minOf { it.first.datum }, mitKurs.maxOf { it.first.datum }, mitKurs.size, summe,
            )
        }
    }

    // Nur die Aktien unter dem Einstiegskurs, groesster Abschlag zuerst.
    fun unterEinstieg(e: List<Einstand>): List<Einstand> = e.filter { it.abstand < 0 }.sortedBy { it.abstand }

    // Eine Aktie, die unter dem durchschnittlichen Einstiegskurs der Gruppe steht.
    // namen: die groessten Kaeufer (nach Betrag) mit ihrem eigenen Abstand zum Einstiegskurs
    data class Abschlag(
        val symbol: String, val einstieg: Double, val aktuell: Double, val abstand: Double, val kaeufer: Int, val betrag: Double,
        val namen: List<Pair<String, Double>> = emptyList(),
    )

    // Durchschnittlicher Einstiegskurs der Gruppe je Aktie: die Einstiegskurse der Personen, gewichtet nach ihrem
    // Betrag. Gezeigt werden die Aktien unter diesem Durchschnitt, groesster Abschlag zuerst.
    fun abschlaege(e: List<Einstand>): List<Abschlag> = e.groupBy { it.symbol }.mapNotNull { (s, l) ->
        val summe = l.sumOf { it.betrag }
        if (summe <= 0) return@mapNotNull null
        val ein = l.sumOf { it.betrag * it.einstieg } / summe
        val jetzt = l.first().aktuell
        val abstand = jetzt / ein - 1
        if (abstand >= 0) null else Abschlag(
            s, ein, jetzt, abstand, l.map { it.person }.toSet().size, summe,
            l.sortedByDescending { it.betrag }.distinctBy { it.person }.take(3).map { it.name to it.abstand },
        )
    }.sortedBy { it.abstand }

    // Investoren (13F): gemeldete Bestaende des letzten Quartals und die Veraenderung gegenueber dem Vorquartal als
    // Kaeufe und Verkaeufe. Preis ist der Kurs zum Quartalsende (Wert durch Stueck), Zeitraeume laufen nach dem Tag der
    // Meldung. Nur Aktien mit Kuerzel.
    fun ausInvestoren(liste: List<Investoren.Investor>, symbol: (String) -> String = { it }): Pair<List<Geschaeft>, List<Bestand>> {
        val trades = ArrayList<Geschaeft>()
        val bestand = ArrayList<Bestand>()
        for (inv in liste) {
            val q = inv.aktuell ?: continue
            val key = "i:" + inv.id
            fun aktien(qu: Investoren.Quartal?) = qu?.positionen.orEmpty().filter { it.art == "aktie" && !it.kuerzel.isNullOrBlank() && it.stueck > 0 && it.wert > 0 }
            val jetzt = aktien(q).groupBy { symbol(it.kuerzel!!) }.mapValues { (_, l) -> l.sumOf { it.stueck } to l.sumOf { it.wert } }
            jetzt.forEach { (s, v) -> bestand += Bestand(key, inv.name, s, v.second) }
            val vorQ = inv.vorher ?: continue
            val vorher = aktien(vorQ).groupBy { symbol(it.kuerzel!!) }.mapValues { (_, l) -> l.sumOf { it.stueck } to l.sumOf { it.wert } }
            val stichtag = tag(q.stichtag)
            val gemeldet = tag(q.eingereicht)
            for (s in jetzt.keys + vorher.keys) {
                val n = jetzt[s]?.first ?: 0.0
                val a = vorher[s]?.first ?: 0.0
                // Preis zum Stichtag, bei verkauften Werten der des Vorquartals
                val preis = jetzt[s]?.let { it.second / it.first } ?: vorher[s]?.let { it.second / it.first } ?: continue
                val delta = n - a
                if (Math.abs(delta) <= a * 0.005 || delta == 0.0) continue
                trades += Geschaeft(key, inv.name, s, delta > 0, stichtag, Math.abs(delta) * preis, preis, gemeldet)
            }
        }
        return trades to bestand
    }

    // Insider an US-Boersen (Form 4): Kaeufe und Verkaeufe mit Preis; als Bestand der Stand nach dem letzten Geschaeft
    // je Person und Aktie, bewertet mit dessen Preis.
    fun ausInsidern(liste: List<SecInsider.Geschaeft>, symbol: (String) -> String = { it }): Pair<List<Geschaeft>, List<Bestand>> {
        val trades = ArrayList<Geschaeft>()
        val bestand = HashMap<Pair<String, String>, Pair<String, Triple<String, Double, Double>>>()
        for (g in liste) {
            if (g.k.isBlank() || g.wert <= 0) continue
            val s = symbol(g.k)
            val key = "s:" + g.p
            trades += Geschaeft(key, g.p, s, g.kauf, tag(g.d), g.wert, g.preis.takeIf { it > 0 })
            val nach = g.nach
            if (nach != null && nach > 0 && g.preis > 0) {
                val k = key to s
                // neuestes Geschaeft zaehlt; bei gleichem Tag das spaetere in der Liste
                val alt = bestand[k]
                if (alt == null || g.d >= alt.second.first) bestand[k] = g.p to Triple(g.d, nach, g.preis)
            }
        }
        return trades to bestand.map { (k, v) -> Bestand(k.first, v.first, k.second, v.second.second * v.second.third) }
    }

    fun tag(iso: String): Long = runCatching { LocalDate.parse(iso.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)
}
