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
// gewichtet nach dem Betrag. Hat die Quelle den Preis selbst (Insider, Investoren), gilt der. Verkaeufe mindern den
// Bestand; nach einem vollstaendigen Verkauf beginnt der Einstieg mit dem naechsten Kauf neu (siehe einstaende).
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
        // Bestand vor und nach dem Geschaeft in Stueck, wenn die Quelle ihn meldet (Form 4).
        val vor: Double? = null,
        val nach: Double? = null,
        // Belegter Einstandspreis je Stueck eines Verkaufs (Ausuebungspreis einer Option am selben Tag), wenn die Quelle ihn nennt.
        val einstand: Double? = null,
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
        // Verkaufter Anteil des seit dem letzten Komplettverkauf Gekauften (0 bis 1) und Tag des letzten Verkaufs.
        val reduziert: Double = 0.0,
        val verkauftAm: Long = 0L,
        // Ganz verkauft: einstieg ist dann der letzte Einstiegskurs vor dem Verkauf, betrag der Kaufbetrag des zuletzt
        // verkauften Rests, abstand der heutige Kurs gegen diesen Einstieg.
        val verkauft: Boolean = false,
        // Mittlerer Verkaufskurs in heutiger Stueckelung, wenn jede Verkaufsmeldung einen Preis nennt.
        val verkaufskurs: Double? = null,
    )

    // Gehaltene Positionen und die zuletzt ganz verkauften (je Person und Aktie die juengste abgeschlossene Runde).
    // ohneKauf: Verkaeufe von Personen ohne Kauf davor in den Daten (etwa Aktien aus Zuteilungen), ohne Einstiegskurs.
    data class Einstiege(val gehalten: List<Einstand>, val verkauft: List<Einstand>, val ohneKauf: List<Verkauf> = emptyList())

    // Verkaeufe einer Person in einer Aktie ohne Kauf davor: Summe der Betraege, Tag des letzten Verkaufs, mittlerer
    // Verkaufskurs in heutiger Stueckelung (nur wenn jede Meldung einen Preis nennt), heutiger Kurs dagegen (abstand),
    // und wie weit der gemeldete Bestand vom ersten bis zum letzten Verkauf gesunken ist (reduziert, 0 bis 1; null,
    // wenn die Quelle den Bestand nicht bei jedem Verkauf meldet).
    data class Verkauf(
        val person: String, val name: String, val symbol: String, val betrag: Double, val verkauftAm: Long, val verkaeufe: Int,
        val verkaufskurs: Double? = null, val aktuell: Double? = null, val abstand: Double? = null, val reduziert: Double? = null,
        // Gemeldeter Bestand nach dem letzten Verkauf in heutiger Stueckelung und der Gewinn, wenn jeder Verkauf einen belegten
        // Einstandspreis hat (Ausuebungspreis); sonst null.
        val restStueck: Double? = null, val gewinn: GeschaeftsBilanz.Gewinn? = null,
    )

    // Laufender Bestand einer Person in einer Aktie seit dem letzten vollstaendigen Verkauf.
    // betrag: Kaufbetrag des noch gehaltenen Teils; mitKurs: Summe aus Betrag mal Kurs; stueck: Betrag durch Kurs;
    // blind: Kaeufe ohne Kurs (zaehlen zum Bestand, nicht zum Einstieg).
    private class Lauf(val name: String) {
        var betrag = 0.0
        var mitKurs = 0.0
        var stueck = 0.0
        var blind = 0.0
        var kaeufe = 0
        var erstes = 0L
        var letztes = 0L
        // Alle Kaufbetraege der Runde, Tag des letzten Verkaufs, Verkaeufe mit Preis (Betrag und Stueck), Verkauf ohne Preis
        var gekauft = 0.0
        var verkauftAm = 0L
        var vkBetrag = 0.0
        var vkStueck = 0.0
        var vkOhnePreis = false
        // Nur fuer Verkaeufe ohne Kauf davor: Summe und Zahl der Verkaeufe, gemeldeter Bestand vor dem ersten und nach dem
        // letzten Verkauf (heutige Stueckelung), Verkauf ohne Bestandsangabe
        var vkSumme = 0.0
        var vkAnzahl = 0
        var bestandVor: Double? = null
        var bestandNach: Double? = null
        var bestandFehlt = false
        // Verkaeufe mit belegtem Einstandspreis: Stueck, Summe Stueck mal Einstand, Verkauf ohne Beleg
        var apStueck = 0.0
        var apSumme = 0.0
        var apFehlt = false

        val reduziert: Double get() = if (gekauft > 0) (1 - (betrag + blind) / gekauft).coerceIn(0.0, 1.0) else 0.0
        val verkaufskurs: Double? get() = if (!vkOhnePreis && vkStueck > 0) vkBetrag / vkStueck else null

        fun mal(f: Double) { betrag *= f; mitKurs *= f; stueck *= f; blind *= f }
    }

    // Ausserhalb dieser Spanne (Preis der Meldung durch bereinigten Schlusskurs des Tages) gilt ein Preis als unerklaert.
    const val PREIS_MIN = 0.8
    const val PREIS_MAX = 1.25

    // Unter diesem Rest gilt ein Bestand als ganz verkauft (Rundung der gemeldeten Stueckzahlen).
    private const val REST = 1e-6

    // Je Person und Aktie, die sie noch haelt, der Einstiegskurs. Geschaefte laufen in der Reihenfolge der Tage (am
    // selben Tag Kaeufe zuerst): Ein Kauf erhoeht den Bestand, ein Verkauf mindert ihn anteilig, der Einstieg des Rests
    // bleibt dabei gleich. Ist alles verkauft, endet die Runde: Die Aktie steht dann unter verkauft (mit dem letzten
    // Einstiegskurs), ein spaeterer Kauf beginnt neu, fruehere Kaeufe zaehlen dann nicht mehr. Verkaeufe ohne Kauf davor (Bestand aus der Zeit vor den Daten) bleiben ohne Wirkung.
    // Wie viel ein Verkauf wegnimmt: mit Preis in der Meldung die Stueckzahl (Betrag durch Preis) gegen die gekauften
    // Stueck, ohne Preis (Betragsspannen) der Betrag gegen den Kaufbetrag des Rests.
    // kurs liefert den Schlusskurs am Tag (von Yahoo, auf die heutige Stueckelung umgerechnet); aktuell den heutigen.
    // Kaeufe ohne Kurs bleiben beim Einstieg aussen vor. Der Preis einer Meldung steht in der Einheit seines Tages und
    // wird mit den Splits danach (faktor: Symbol, nach, bis) auf die heutige Stueckelung umgerechnet.
    fun einstaende(
        geschaefte: List<Geschaeft>, kurs: (String, Long) -> Double?, aktuell: (String) -> Double?,
        faktor: (String, Long, Long) -> Double = SplitFaktoren::zwischen,
    ): List<Einstand> = einstiege(geschaefte, kurs, aktuell, faktor).gehalten

    // Wie einstaende, dazu die ganz verkauften Positionen. Teilverkaeufe stehen an der gehaltenen Position (reduziert).
    fun einstiege(
        geschaefte: List<Geschaeft>, kurs: (String, Long) -> Double?, aktuell: (String) -> Double?,
        faktor: (String, Long, Long) -> Double = SplitFaktoren::zwischen,
        verkaufAb: Long = 0L,
        splitBekannt: (String) -> Boolean = { true },
    ): Einstiege {
        val lauf = LinkedHashMap<Pair<String, String>, Lauf>()
        val zu = LinkedHashMap<Pair<String, String>, Lauf>()
        // Verkaeufe ohne Kauf davor (ab verkaufAb). Je Person und Aktie gibt es am Ende hoechstens einen Eintrag:
        // Ein spaeterer Kauf loest diesen hier ab, Verkaeufe nach einer abgeschlossenen Runde zaehlen zu ihr.
        val nur = LinkedHashMap<Pair<String, String>, Lauf>()
        for (g in geschaefte.sortedWith(compareBy<Geschaeft> { it.datum }.thenByDescending { it.kauf })) {
            if (g.betrag <= 0) continue
            val k = g.person to g.symbol
            val f = faktor(g.symbol, g.datum, Long.MAX_VALUE).takeIf { it > 0 && it.isFinite() } ?: 1.0
            // Gemeldeter Preis in heutiger Stueckelung gegen den bereinigten Schlusskurs des Tages. Weicht er trotz der
            // bekannten Splits deutlich ab (Splitdaten fehlen oder sind unvollstaendig, etwa nach mehreren Zusammenlegungen
            // oder einem Kuerzelwechsel), gilt der Schlusskurs. Der Betrag bleibt: Er ist echtes Geld, nur der Preis steht in
            // der alten Stueckelung; Betrag durch Schlusskurs ergibt die Stueckzahl von heute. Laesst sich der Preis gar
            // nicht pruefen (kein Schlusskurs und keine Splitdaten), zaehlt das Geschaeft wie eines ohne Preis.
            val roh = g.preis?.takeIf { it > 0 }?.let { it / f }
            val schluss = if (roh != null || g.kauf) kurs(g.symbol, g.datum)?.takeIf { it > 0 } else null
            val unerklaert = roh != null && schluss != null && (roh / schluss < PREIS_MIN || roh / schluss > PREIS_MAX)
            val preis = when {
                roh == null -> null
                unerklaert -> schluss
                schluss == null && !splitBekannt(g.symbol) -> null
                else -> roh
            }
            val betrag = g.betrag
            if (g.kauf) {
                nur.remove(k)
                val l = lauf.getOrPut(k) { Lauf(g.name) }
                val p = preis ?: schluss
                l.gekauft += betrag
                if (p == null) { l.blind += betrag; continue }
                if (l.kaeufe == 0) l.erstes = g.datum
                l.letztes = g.datum
                l.kaeufe++
                l.betrag += betrag
                l.mitKurs += betrag * p
                l.stueck += betrag / p
            } else {
                val l = lauf[k]
                if (l == null) {
                    val z = zu[k]
                    if (z != null) {
                        z.verkauftAm = g.datum
                        if (preis != null) { z.vkBetrag += betrag; z.vkStueck += betrag / preis } else z.vkOhnePreis = true
                    } else if (g.datum >= verkaufAb) {
                        val n = nur.getOrPut(k) { Lauf(g.name) }
                        n.verkauftAm = g.datum
                        n.vkSumme += betrag
                        n.vkAnzahl++
                        if (preis != null) { n.vkBetrag += betrag; n.vkStueck += betrag / preis } else n.vkOhnePreis = true
                        val ap = g.einstand?.takeIf { it > 0 }?.let { it / f }
                        if (preis != null && ap != null) { n.apStueck += betrag / preis; n.apSumme += betrag / preis * ap } else n.apFehlt = true
                        val vor = g.vor
                        val nach = g.nach
                        if (vor == null || nach == null || vor <= 0 || nach < 0) { n.bestandFehlt = true; n.bestandNach = null }
                        else { if (n.bestandVor == null) n.bestandVor = vor * f; n.bestandNach = nach * f }
                    }
                    continue
                }
                val anteil = if (preis != null && l.stueck > 0 && l.blind <= 0) betrag / preis / l.stueck else betrag / (l.betrag + l.blind)
                l.verkauftAm = g.datum
                if (preis != null) { l.vkBetrag += betrag; l.vkStueck += betrag / preis } else l.vkOhnePreis = true
                if (anteil >= 1 - REST) {
                    // Runde beendet: Der Stand vor dem letzten Verkauf bleibt als verkaufte Position stehen
                    lauf.remove(k)
                    zu[k] = l
                } else l.mal(1 - anteil)
            }
        }
        val heute = HashMap<String, Double?>()
        fun aus(k: Pair<String, String>, l: Lauf, verkauft: Boolean): Einstand? {
            if (l.kaeufe == 0 || l.betrag <= 0) return null
            val jetzt = heute.getOrPut(k.second) { aktuell(k.second) }?.takeIf { it > 0 } ?: return null
            val einstieg = l.mitKurs / l.betrag
            return Einstand(
                k.first, l.name, k.second, einstieg, jetzt, jetzt / einstieg - 1, l.erstes, l.letztes, l.kaeufe, l.betrag,
                if (verkauft) 1.0 else l.reduziert, l.verkauftAm, verkauft, l.verkaufskurs,
            )
        }
        val ohneKauf = nur.map { (k, n) ->
            val vk = n.verkaufskurs
            val jetzt = heute.getOrPut(k.second) { aktuell(k.second) }?.takeIf { it > 0 }
            val vor = n.bestandVor
            val nach = n.bestandNach
            Verkauf(
                k.first, n.name, k.second, n.vkSumme, n.verkauftAm, n.vkAnzahl, vk, jetzt, if (vk != null && jetzt != null) jetzt / vk - 1 else null,
                if (n.bestandFehlt || vor == null || nach == null) null else (1 - nach / vor).coerceIn(0.0, 1.0),
                restStueck = if (n.bestandFehlt) null else nach,
                gewinn = if (!n.apFehlt && n.apStueck > 0 && vk != null) GeschaeftsBilanz.Gewinn(n.vkBetrag - n.apSumme, vk / (n.apSumme / n.apStueck) - 1) else null,
            )
        }
        return Einstiege(lauf.mapNotNull { (k, l) -> aus(k, l, false) }, zu.mapNotNull { (k, l) -> aus(k, l, true) }, ohneKauf)
    }

    // Eine Aktie, die Personen der Gruppe ganz verkauft haben: mittlerer Einstiegskurs der Verkaeufer (nach Betrag
    // gewichtet), heutiger Kurs dagegen, Tag des letzten Verkaufs. namen: die juengsten Verkaeufer mit ihrem Tag.
    data class Ausstieg(
        val symbol: String, val einstieg: Double, val aktuell: Double, val abstand: Double, val personen: Int, val letzter: Long,
        val namen: List<Pair<String, Long>> = emptyList(),
    )

    // Eine Aktie, die Personen der Gruppe ohne Kauf davor verkauft haben: Summe, Personen, letzter Verkauf, mittlerer
    // Verkaufskurs (nur wenn ihn alle Verkaeufer haben) und der heutige Kurs dagegen. namen: die groessten Verkaeufer
    // mit Betrag und gemeldeter Minderung ihres Bestands.
    data class Abgabe(
        val symbol: String, val betrag: Double, val personen: Int, val letzter: Long, val verkaufskurs: Double?, val abstand: Double?,
        val namen: List<Triple<String, Double, Double?>> = emptyList(),
    )

    // Verkaeufe ohne Kauf davor je Aktie, groesste Summe zuerst.
    fun abgaben(l: List<Verkauf>): List<Abgabe> = l.groupBy { it.symbol }.map { (s, v) ->
        val betrag = v.sumOf { it.betrag }
        val stueck = if (v.all { (it.verkaufskurs ?: 0.0) > 0 }) v.sumOf { it.betrag / it.verkaufskurs!! } else 0.0
        val vk = if (stueck > 0) betrag / stueck else null
        val jetzt = v.firstNotNullOfOrNull { it.aktuell }
        Abgabe(
            s, betrag, v.map { it.person }.toSet().size, v.maxOf { it.verkauftAm }, vk, if (vk != null && jetzt != null) jetzt / vk - 1 else null,
            v.sortedByDescending { it.betrag }.take(3).map { Triple(it.name, it.betrag, it.reduziert) },
        )
    }.sortedByDescending { it.betrag }

    // Ganz verkaufte Positionen je Aktie, juengster Verkauf zuerst.
    fun ausstiege(verkauft: List<Einstand>): List<Ausstieg> = verkauft.filter { it.verkauft }.groupBy { it.symbol }.mapNotNull { (s, l) ->
        val summe = l.sumOf { it.betrag }
        if (summe <= 0) return@mapNotNull null
        val ein = l.sumOf { it.betrag * it.einstieg } / summe
        val jetzt = l.first().aktuell
        val nachTag = l.sortedByDescending { it.verkauftAm }.distinctBy { it.person }
        Ausstieg(s, ein, jetzt, jetzt / ein - 1, nachTag.size, nachTag.first().verkauftAm, nachTag.take(3).map { it.name to it.verkauftAm })
    }.sortedByDescending { it.letzter }

    // Ein Insidergeschaeft aus einem Register mit eigener Waehrung (BaFin, weitere Laender) fuer die Einstiegsrechnung
    // einer Aktie. Nur Kaeufe und Verkaeufe am Markt mit Person, Tag und Betrag; die Waehrung der Meldung muss die der
    // Kurse sein (kursWaehrung), sonst liesse sich der Preis nicht mit dem Kurs vergleichen.
    fun insiderGeschaeft(
        person: String, symbol: String, kauf: Boolean, verkauf: Boolean, tagIso: String, betrag: Double?, preis: Double?,
        waehrung: String, kursWaehrung: String,
    ): Geschaeft? {
        if (kauf == verkauf || person.isBlank() || betrag == null || betrag <= 0) return null
        if (waehrung.isBlank() || waehrung != kursWaehrung) return null
        val t = tag(tagIso).takeIf { it > 0 } ?: return null
        return Geschaeft(person.trim().lowercase(), person.trim(), symbol, kauf, t, betrag, preis?.takeIf { it > 0 })
    }

    // Nur die Aktien unter dem Einstiegskurs, groesster Abschlag zuerst. Kaeufe unter einem Tausendstel aller Kaeufe der
    // Person bleiben weg: Bei Fonds mit Tausenden Positionen stuenden sonst Kleinstposten mit Ausreisserkursen vorn.
    const val MIN_ANTEIL_EINSTIEG = 0.001
    fun unterEinstieg(e: List<Einstand>): List<Einstand> {
        val summe = e.groupBy { it.person }.mapValues { (_, l) -> l.sumOf { it.betrag } }
        return e.filter { !it.verkauft && it.abstand < 0 && it.betrag >= (summe[it.person] ?: 0.0) * MIN_ANTEIL_EINSTIEG }.sortedBy { it.abstand }
    }

    // Eine Aktie, die unter dem durchschnittlichen Einstiegskurs der Gruppe steht.
    // namen: die groessten Kaeufer (nach Betrag) mit ihrem eigenen Abstand zum Einstiegskurs und dem verkauften Anteil
    data class Abschlag(
        val symbol: String, val einstieg: Double, val aktuell: Double, val abstand: Double, val kaeufer: Int, val betrag: Double,
        val namen: List<Triple<String, Double, Double>> = emptyList(),
    )

    // Durchschnittlicher Einstiegskurs der Gruppe je Aktie: die Einstiegskurse der Personen, gewichtet nach ihrem
    // Betrag. Gezeigt werden die Aktien unter diesem Durchschnitt, groesster Abschlag zuerst.
    // Ganz verkaufte Positionen zaehlen nicht mit.
    fun abschlaege(e: List<Einstand>): List<Abschlag> = e.filter { !it.verkauft }.groupBy { it.symbol }.mapNotNull { (s, l) ->
        val summe = l.sumOf { it.betrag }
        if (summe <= 0) return@mapNotNull null
        val ein = l.sumOf { it.betrag * it.einstieg } / summe
        val jetzt = l.first().aktuell
        val abstand = jetzt / ein - 1
        if (abstand >= 0) null else Abschlag(
            s, ein, jetzt, abstand, l.map { it.person }.toSet().size, summe,
            l.sortedByDescending { it.betrag }.distinctBy { it.person }.take(3).map { Triple(it.name, it.abstand, it.reduziert) },
        )
    }.sortedBy { it.abstand }

    // Investoren (13F): gemeldete Bestaende des letzten Quartals und die Veraenderung gegenueber dem Vorquartal als
    // Kaeufe und Verkaeufe. Preis ist der Kurs zum Quartalsende (Wert durch Stueck), Zeitraeume laufen nach dem Tag der
    // Meldung. Nur Aktien mit Kuerzel.
    // Splits zwischen den beiden Stichtagen (faktor) rechnen die Stueckzahl des Vorquartals auf die neue Stueckelung um,
    // sonst waere der Sprung ein Kauf.
    fun ausInvestoren(
        liste: List<Investoren.Investor>, symbol: (String) -> String = { it },
        faktor: (String, Long, Long) -> Double = SplitFaktoren::zwischen,
    ): Pair<List<Geschaeft>, List<Bestand>> {
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
            val vorStichtag = tag(vorQ.stichtag)
            val gemeldet = tag(q.eingereicht)
            for (s in jetzt.keys + vorher.keys) {
                val n = jetzt[s]?.first ?: 0.0
                // Wer das Papier noch haelt, dessen Vorbestand zaehlt in der Stueckelung des neuen Stichtags
                val a = (vorher[s]?.first ?: 0.0) * (if (n > 0) faktor(s, vorStichtag, stichtag).takeIf { it > 0 && it.isFinite() } ?: 1.0 else 1.0)
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
            trades += Geschaeft(key, g.p, s, g.kauf, tag(g.d), g.wert, g.preis.takeIf { it > 0 }, vor = g.vor, nach = g.nach, einstand = g.ap)
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
