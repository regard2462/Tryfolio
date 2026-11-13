package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.DepotRegeln
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor

// Eigene Risikoregeln fuer das Uebungsdepot (0.78): Hoechstverlust je Geschaeft und je Tag, Zahl neuer Geschaefte je Tag,
// Mindestverhaeltnis von Chance zu Risiko. Sie warnen beim Kauf und verbieten nichts; Verkaeufe und Stopps prueft
// niemand. Dazu die Positionsgroesse aus mehreren Grenzen und das Ziel als Vielfaches des Risikos.
object Risikoregeln {

    enum class Regel { VERLUST_GESCHAEFT, VERLUST_TAG, GESCHAEFTE_TAG, CHANCE_RISIKO }

    // ist und grenze in der Einheit der Regel: Prozent des Depots, Anzahl oder Verhaeltnis.
    data class Befund(val regel: Regel, val ist: Double, val grenze: Double)

    // Chance zu Risiko eines Kaufs: Abstand zum Ziel durch Abstand zum Stopp; null, wenn Stopp oder Ziel fehlen oder
    // auf der falschen Seite des Kurses liegen.
    fun chanceRisiko(kurs: Double, stopp: Double, ziel: Double): Double? {
        if (!(kurs > 0) || !(stopp > 0) || !(stopp < kurs) || !(ziel > kurs) || !ziel.isFinite()) return null
        return (ziel - kurs) / (kurs - stopp)
    }

    // Anteil der Gewinner, ab dem sich Gewinne und Verluste ausgleichen (ohne Kosten): 1 / (1 + Verhaeltnis).
    fun trefferquote(verhaeltnis: Double): Double? =
        if (verhaeltnis.isFinite() && verhaeltnis > 0) 1.0 / (1.0 + verhaeltnis) else null

    // Zielkurs als Vielfaches des Risikos ueber dem Kurs.
    fun ziel(kurs: Double, stopp: Double, vielfaches: Double): Double? =
        if (kurs > 0 && stopp > 0 && stopp < kurs && vielfaches > 0) kurs + (kurs - stopp) * vielfaches else null

    // Der heutige Tag aus den Buchungen: realisierter Verlust in Euro (0, wenn der Tag im Plus liegt) und neue Geschaefte.
    data class Tag(val verlust: Double = 0.0, val geschaefte: Int = 0)

    // Neue Geschaefte sind Kaeufe von Hand oder per Order und eroeffnete Stillhalterpositionen; Sparplaene, uebernommene
    // Positionen und Rueckkaeufe verkaufter Optionen zaehlen nicht. Der Verlust ist das realisierte Ergebnis des Tages
    // vor Steuern, soweit es negativ ist; offene Positionen gehen nicht ein.
    fun tagesStand(buchungen: List<Transaction>, jetzt: Long, zone: ZoneId = ZoneId.systemDefault()): Tag {
        val heute = Instant.ofEpochMilli(jetzt).atZone(zone).toLocalDate()
        val anfang = heute.atStartOfDay(zone).toInstant().toEpochMilli()
        val ende = heute.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var ergebnis = 0.0
        var geschaefte = 0
        for (t in buchungen) {
            if (t.ts < anfang || t.ts >= ende) continue
            if (t.realized.isFinite()) ergebnis += t.realized
            val kauf = t.type == TxType.BUY && t.tag != "BUY_CLOSE" && t.via != "PLAN" && !t.via.startsWith("IMPORT")
            if (kauf || t.tag == "SELL_OPEN") geschaefte++
        }
        return Tag(verlust = if (ergebnis < 0) -ergebnis else 0.0, geschaefte = geschaefte)
    }

    // Warnungen zu einem Kauf. depot: Wertpapiere plus Guthaben; stopp und ziel 0 = nicht gesetzt. Der Verlust je
    // Geschaeft ist Stueck mal Abstand zum Stopp, ohne Gebuehren und ohne Kursluecken. Die Tagesregeln gelten fuer
    // jeden Kauf, die beiden anderen nur mit Stopp bzw. mit Stopp und Ziel.
    fun kaufPruefen(regeln: DepotRegeln, depot: Double, kurs: Double, stueck: Double, stopp: Double, ziel: Double, tag: Tag): List<Befund> {
        if (!regeln.risikoAktiv || !(depot > 0) || !(kurs > 0) || !(stueck > 0)) return emptyList()
        val aus = ArrayList<Befund>()
        val mitStopp = stopp > 0 && stopp < kurs
        if (regeln.maxVerlustGeschaeft > 0 && mitStopp) {
            val anteil = stueck * (kurs - stopp) / depot * 100.0
            if (anteil > regeln.maxVerlustGeschaeft + 1e-9) aus += Befund(Regel.VERLUST_GESCHAEFT, anteil, regeln.maxVerlustGeschaeft)
        }
        if (regeln.maxVerlustTag > 0) {
            val anteil = tag.verlust / depot * 100.0
            if (anteil >= regeln.maxVerlustTag - 1e-9) aus += Befund(Regel.VERLUST_TAG, anteil, regeln.maxVerlustTag)
        }
        if (regeln.maxGeschaefteTag > 0 && tag.geschaefte >= regeln.maxGeschaefteTag) {
            aus += Befund(Regel.GESCHAEFTE_TAG, (tag.geschaefte + 1).toDouble(), regeln.maxGeschaefteTag.toDouble())
        }
        if (regeln.minChanceRisiko > 0) {
            val v = chanceRisiko(kurs, stopp, ziel)
            if (v != null && v < regeln.minChanceRisiko - 1e-9) aus += Befund(Regel.CHANCE_RISIKO, v, regeln.minChanceRisiko)
        }
        return aus
    }

    // Was die Stueckzahl begrenzt.
    enum class Grenze { RISIKO, ANTEIL, BARGELD }

    data class Groesse(
        val stueck: Double,
        // Die Grenze mit der kleinsten Stueckzahl; bei Gleichstand die erste in der Reihenfolge Risiko, Anteil, Guthaben
        val grenze: Grenze,
        // Stueckzahl je Grenze vor dem Runden; fehlt eine Grenze, fehlt der Eintrag
        val jeGrenze: Map<Grenze, Double>,
        val stoppKurs: Double,
        val verlustJeStueck: Double,
        val hoechstverlust: Double,
        val kapital: Double,
        // Anteil des Kapitaleinsatzes am Depot in Prozent
        val anteil: Double,
    ) {
        // Schon ein Stueck ueberschreitet die engste Grenze
        val einStueckZuViel: Boolean get() = stueck <= 0.0
    }

    // Stueckzahl als kleinster Wert aus drei Grenzen: Hoechstverlust durch Risiko je Stueck, hoechster Anteil der
    // Position am Depot (maxAnteil in Prozent, 0 = ohne; position: bisheriger Wert der Position) und Guthaben nach Puffer
    // (puffer in Prozent des Depots, dazu die Ordergebuehr). ganze rundet auf ganze Stueck ab, sonst auf vier Stellen.
    fun groesse(
        depot: Double, kurs: Double, risiko: Double, stoppAbstand: Double, maxAnteil: Double, position: Double,
        bargeld: Double, puffer: Double, ganze: Boolean, gebuehr: Double = 1.0,
    ): Groesse? {
        if (depot <= 0 || kurs <= 0 || risiko <= 0 || stoppAbstand <= 0 || stoppAbstand >= 100) return null
        val stopp = kurs * (1.0 - stoppAbstand / 100.0)
        val jeStueck = kurs - stopp
        val hoechst = depot * risiko / 100.0
        val je = LinkedHashMap<Grenze, Double>()
        je[Grenze.RISIKO] = hoechst / jeStueck
        if (maxAnteil > 0) je[Grenze.ANTEIL] = (depot * maxAnteil / 100.0 - position.coerceAtLeast(0.0)).coerceAtLeast(0.0) / kurs
        je[Grenze.BARGELD] = (bargeld - depot * puffer.coerceAtLeast(0.0) / 100.0 - gebuehr).coerceAtLeast(0.0) / kurs
        val engste = je.entries.minBy { it.value }
        val roh = engste.value
        val stueck = if (ganze) floor(roh) else floor(roh * 10_000.0) / 10_000.0
        val kapital = stueck * kurs
        return Groesse(stueck, engste.key, je, stopp, jeStueck, hoechst, kapital, kapital / depot * 100.0)
    }
}
