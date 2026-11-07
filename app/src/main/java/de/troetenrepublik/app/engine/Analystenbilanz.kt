package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// Trefferbilanz der Analystenhaeuser bei einer Aktie (5.92). Grundlage sind die Hoch- und Abstufungen aus Yahoo
// (upgradeDowngradeHistory) und die Tageskurse der Aktie und ihres Vergleichsindex. Gemessen wird, wie die Aktie
// 3, 6 und 12 Monate nach einem Urteil gegen den Index lief: Eine Hochstufung trifft, wenn die Aktie besser lief als
// der Index, eine Abstufung, wenn sie schlechter lief. So zaehlt die Marktphase nicht mit.
//
// Vorsichtsmassnahmen gegen Zufallstreffer:
// - Kleine Stichproben: Die Trefferquote eines Hauses wird zur Mitte (50 %) gezogen, umso staerker, je weniger
//   Urteile es gibt; eine eigene Quote erscheint erst ab MIN_URTEILE.
// - Zeitverfall: Aeltere Urteile zaehlen in der Bilanz weniger (Halbwertszeit drei Jahre), in der aktuellen
//   Einschaetzung deutlich weniger (Halbwertszeit sechs Monate); Urteile ueber ein Jahr alt gelten nicht mehr.
// - Herdenverhalten: Stufen mehrere Haeuser binnen weniger Tage in dieselbe Richtung um, zaehlt das einzelne Urteil
//   weniger, weil es meist auf dieselbe Nachricht folgt.
// - Gewichte sind begrenzt (0,5 bis 1,5), damit kein einzelnes Haus die bereinigte Einschaetzung bestimmt.
// Interessenkonflikte (etwa Bankgeschaefte mit der Firma) sind aus den Daten nicht erkennbar.
object Analystenbilanz {

    // Handelstage bis zur Messung: etwa 3, 6 und 12 Monate.
    val HORIZONTE = listOf(63, 126, 252)

    // Ab so vielen bewerteten Urteilen zeigt ein Haus eine eigene Trefferquote.
    const val MIN_URTEILE = 3

    // Staerke der Schrumpfung: wie viele gedachte Urteile mit 50 % Treffern jedes Haus mitbringt.
    const val SCHRUMPFUNG = 4.0

    private const val TAG = 86_400_000L
    private const val JAHR = 365.25 * TAG

    // Ein Urteil wie bei Yahoo: Haus, Zeitpunkt (ms), Einstufung nachher und vorher, Art (up, down, init, main, reit),
    // Kursziel nachher und vorher (0 = keins).
    data class Urteil(
        val haus: String, val zeit: Long, val nach: String, val von: String, val aktion: String,
        val ziel: Double = 0.0, val zielVorher: Double = 0.0,
    )

    // Verteilung der Empfehlungen in einem Monat (periode „0m“ = aktueller Monat, „-3m“ = vor drei Monaten).
    data class Verteilung(val periode: String, val starkKaufen: Int, val kaufen: Int, val halten: Int, val verkaufen: Int, val starkVerkaufen: Int) {
        val summe: Int get() = starkKaufen + kaufen + halten + verkaufen + starkVerkaufen

        // Mittel auf der Skala -2 (stark verkaufen) bis +2 (stark kaufen); null ohne Stimmen.
        val mittel: Double? get() = if (summe == 0) null
            else (2.0 * starkKaufen + kaufen - verkaufen - 2.0 * starkVerkaufen) / summe
    }

    // Einstufung als Zahl von -2 (stark verkaufen) bis +2 (stark kaufen); null, wenn unbekannt.
    fun stufe(einstufung: String): Int? {
        val e = einstufung.trim().lowercase().replace('_', ' ').replace('-', ' ')
        if (e.isBlank()) return null
        return when {
            e in setOf("strong buy", "top pick", "conviction buy", "strong outperform") -> 2
            e in setOf("strong sell") -> -2
            // Sell zaehlt wie Buy einfach (Haeuser mit drei Stufen), nur Strong Sell doppelt
            e in setOf("sell") || e.contains("underperform") || e.contains("underweight") || e in setOf("reduce", "moderate sell", "negative", "cautious", "trim") -> -1
            e.contains("outperform") || e.contains("overweight") || e.contains("buy") ||
                e in setOf("accumulate", "add", "positive", "long term buy", "speculative buy") -> 1
            e.contains("perform") || e.contains("weight") || e.contains("neutral") || e.contains("hold") ||
                e in setOf("in line", "inline", "mixed", "fair value", "market", "sector") -> 0
            else -> null
        }
    }

    // Mittel (-2 bis +2) als Stufe gerundet.
    fun stufeAus(mittel: Double): Int = mittel.roundToInt().coerceIn(-2, 2)

    // Richtung eines Urteils: +1 fuer eine Hochstufung oder eine Erstbewertung mit Kaufen, -1 fuer eine Abstufung oder
    // eine Erstbewertung mit Verkaufen, null fuer Bestaetigungen und neutrale Erstbewertungen.
    fun richtung(u: Urteil): Int? {
        val nach = stufe(u.nach)
        val von = stufe(u.von)
        return when (u.aktion.lowercase()) {
            "up" -> if (nach != null && von != null && nach < von) null else 1
            "down" -> if (nach != null && von != null && nach > von) null else -1
            "init" -> nach?.takeIf { it != 0 }?.let { if (it > 0) 1 else -1 }
            else -> null
        }
    }

    private fun tag(t: Long): Long = Math.floorDiv(t, TAG)

    // Index der ersten Kerze am Tag von t oder danach; -1, wenn keine folgt.
    private fun abTag(kurse: List<Candle>, t: Long): Int {
        val d = tag(t)
        var lo = 0
        var hi = kurse.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (tag(kurse[mid].t) < d) lo = mid + 1 else hi = mid
        }
        return if (lo < kurse.size) lo else -1
    }

    // Index der letzten Kerze am Tag von t oder davor; -1, wenn keine vorher liegt.
    private fun bisTag(kurse: List<Candle>, t: Long): Int {
        val d = tag(t)
        var lo = 0
        var hi = kurse.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (tag(kurse[mid].t) <= d) lo = mid + 1 else hi = mid
        }
        return lo - 1
    }

    // Rendite der Aktie gegen den Index (Anteil, 0,05 = 5 % besser) von Schluss am Urteilstag bis tage Handelstage
    // spaeter. Der Schlusskurs des Urteilstags enthaelt die erste Reaktion, die zaehlt also nicht als Treffer.
    // Ohne Index die einfache Rendite. null, wenn die Zeit noch nicht verstrichen ist.
    fun mehrRendite(kurse: List<Candle>, index: List<Candle>?, zeit: Long, tage: Int): Double? {
        val i = abTag(kurse, zeit)
        if (i < 0) return null
        val j = i + tage
        if (j >= kurse.size) return null
        val a = kurse[i].close
        val b = kurse[j].close
        if (!(a > 0) || !(b > 0)) return null
        val r = b / a
        if (index.isNullOrEmpty()) return r - 1.0
        val ia = bisTag(index, kurse[i].t)
        val ib = bisTag(index, kurse[j].t)
        if (ia < 0 || ib < 0 || ib <= ia) return null
        val x = index[ia].close
        val y = index[ib].close
        if (!(x > 0) || !(y > 0)) return null
        return r / (y / x) - 1.0
    }

    // Ein bewertetes Urteil: Richtung, Rendite gegen den Index je Horizont (null = noch offen) und ob es in einer Herde
    // entstand.
    data class Bewertet(val urteil: Urteil, val richtung: Int, val mehr: List<Double?>, val herde: Boolean) {
        // Treffer je Horizont: true, false oder null (offen).
        val treffer: List<Boolean?> get() = mehr.map { m -> m?.let { it * richtung > 0 } }

        // Anteil der Treffer ueber die verstrichenen Horizonte; null, wenn keiner verstrichen ist.
        val anteil: Double? get() = treffer.filterNotNull().takeIf { it.isNotEmpty() }?.let { l -> l.count { it }.toDouble() / l.size }
    }

    // Herde: binnen HERDE_TAGE haben mindestens zwei andere Haeuser in dieselbe Richtung umgestuft.
    const val HERDE_TAGE = 3

    fun herde(u: Urteil, richtung: Int, alle: List<Pair<Urteil, Int>>): Boolean =
        alle.count { (v, r) -> v.haus != u.haus && r == richtung && abs(tag(v.zeit) - tag(u.zeit)) <= HERDE_TAGE } >= 2

    fun bewerten(urteile: List<Urteil>, kurse: List<Candle>, index: List<Candle>?): List<Bewertet> {
        val mitRichtung = urteile.mapNotNull { u -> richtung(u)?.let { u to it } }
        return mitRichtung.map { (u, r) ->
            Bewertet(u, r, HORIZONTE.map { mehrRendite(kurse, index, u.zeit, it) }, herde(u, r, mitRichtung))
        }
    }

    // Bilanz und aktuelle Haltung eines Hauses.
    data class Haus(
        val name: String,
        // Juengstes Urteil des Hauses
        val letztes: Urteil,
        // Aktuelle Stufe (-2 bis +2), null, wenn das letzte Urteil aelter als ein Jahr oder unbekannt ist
        val stufe: Int?,
        // Kursziel aus dem letzten Jahr, 0 = keins
        val ziel: Double,
        val zielZeit: Long,
        // Treffer und bewertete Urteile je Horizont
        val treffer: List<Pair<Int, Int>>,
        // Zahl der bewerteten Urteile (mindestens ein Horizont verstrichen)
        val bewertet: Int,
        // Rohe Trefferquote (Anteil der Treffer ueber alle verstrichenen Horizonte), null unter MIN_URTEILE
        val quote: Double?,
        // Zur Mitte gezogene Trefferquote
        val geschrumpft: Double,
        // Gewicht in der bereinigten Einschaetzung (0,5 bis 1,5)
        val gewicht: Double,
        // Einstufung und Kursziel passen nicht zusammen (Kaufen mit Ziel unter dem Kurs oder umgekehrt)
        val widerspruch: Boolean,
    )

    data class Ergebnis(
        // Haeuser mit aktueller Haltung zuerst (nach Gewicht), danach die uebrigen
        val haeuser: List<Haus>,
        // Mittel der aktuellen Haltungen (-2 bis +2), ungewichtet und nach Trefferquote und Alter gewichtet
        val roh: Double?,
        val bereinigt: Double?,
        // Mittleres Kursziel ungewichtet und gewichtet
        val zielRoh: Double?,
        val zielBereinigt: Double?,
        // Treffer und bewertete Urteile aller Haeuser je Horizont
        val gesamt: List<Pair<Int, Int>>,
        // Haeuser mit aktueller Haltung
        val aktive: Int,
        // Anteil der bewerteten Urteile, die in einer Herde entstanden
        val herdenAnteil: Double,
        // Gemessen gegen einen Index (sonst einfache Rendite)
        val mitIndex: Boolean,
    ) {
        // Laengster Horizont mit bewerteten Urteilen (Index in HORIZONTE), -1 ohne.
        val horizont: Int get() = gesamt.indices.lastOrNull { gesamt[it].second > 0 } ?: -1

        // Die Bilanz traegt kaum: zu wenige Urteile oder Trefferquote nahe am Zufall (laengster Horizont).
        val wenigAussagekraft: Boolean get() {
            val (t, n) = gesamt.getOrNull(horizont) ?: return true
            if (n < 10) return true
            val q = t.toDouble() / n
            return q in 0.45..0.55
        }
    }

    // Wertet alle Urteile aus. kurs: aktueller Kurs in der Waehrung der Kursziele (fuer Widersprueche), jetzt in ms.
    fun auswerten(urteile: List<Urteil>, kurse: List<Candle>, index: List<Candle>?, kurs: Double, jetzt: Long): Ergebnis {
        val bewertet = bewerten(urteile, kurse, index)
        val nachHaus = urteile.groupBy { it.haus.trim() }
        val haeuser = nachHaus.map { (name, liste) ->
            val sortiert = liste.sortedByDescending { it.zeit }
            val letztes = sortiert.first()
            val aktuell = (jetzt - letztes.zeit) <= JAHR
            val stufe = if (aktuell) stufe(letztes.nach) else null
            val zielUrteil = sortiert.firstOrNull { it.ziel > 0 && jetzt - it.zeit <= JAHR }
            val eigene = bewertet.filter { it.urteil.haus.trim() == name }
            val treffer = HORIZONTE.indices.map { h ->
                val l = eigene.mapNotNull { it.treffer[h] }
                l.count { it } to l.size
            }
            // Gewichtete Bilanz: aeltere Urteile und Herdenurteile zaehlen weniger
            var summe = 0.0
            var gewichte = 0.0
            var anzahl = 0
            for (b in eigene) {
                val a = b.anteil ?: continue
                val w = 0.5.pow((jetzt - b.urteil.zeit) / (3 * JAHR)) * (if (b.herde) 0.5 else 1.0)
                summe += a * w
                gewichte += w
                anzahl++
            }
            val alleTreffer = treffer.sumOf { it.first }
            val alleBewertet = treffer.sumOf { it.second }
            val roh = if (anzahl >= MIN_URTEILE && alleBewertet > 0) alleTreffer.toDouble() / alleBewertet else null
            val geschrumpft = (summe + SCHRUMPFUNG * 0.5) / (gewichte + SCHRUMPFUNG)
            val gewicht = (geschrumpft / 0.5).coerceIn(0.5, 1.5)
            val widerspruch = zielUrteil != null && stufe != null && kurs > 0 && zielUrteil === letztes &&
                ((stufe > 0 && letztes.ziel < kurs * 0.97) || (stufe < 0 && letztes.ziel > kurs * 1.03))
            Haus(
                name, letztes, stufe, zielUrteil?.ziel ?: 0.0, zielUrteil?.zeit ?: 0L, treffer, anzahl, roh, geschrumpft, gewicht, widerspruch,
            )
        }
        // Aktuelle Haltung: gewichtet nach Trefferquote, Alter und Herde
        val herdenHaeuser = bewertet.filter { it.herde }.map { it.urteil }.toSet()
        var rohSumme = 0.0
        var rohZahl = 0
        var gSumme = 0.0
        var gGewicht = 0.0
        var zielSumme = 0.0
        var zielZahl = 0
        var zielGSumme = 0.0
        var zielGewicht = 0.0
        for (h in haeuser) {
            val alter = 0.5.pow((jetzt - h.letztes.zeit).coerceAtLeast(0L) / (JAHR / 2))
            val herde = if (h.letztes in herdenHaeuser) 0.8 else 1.0
            val w = h.gewicht * alter * herde
            h.stufe?.let { s ->
                rohSumme += s; rohZahl++
                gSumme += s * w; gGewicht += w
            }
            if (h.ziel > 0) {
                val wz = h.gewicht * 0.5.pow((jetzt - h.zielZeit).coerceAtLeast(0L) / (JAHR / 2))
                zielSumme += h.ziel; zielZahl++
                zielGSumme += h.ziel * wz; zielGewicht += wz
            }
        }
        val gesamt = HORIZONTE.indices.map { h ->
            val l = bewertet.mapNotNull { it.treffer[h] }
            l.count { it } to l.size
        }
        val mitAnteil = bewertet.filter { it.anteil != null }
        return Ergebnis(
            haeuser = haeuser.sortedWith(compareByDescending<Haus> { it.stufe != null }.thenByDescending { it.gewicht }.thenByDescending { it.letztes.zeit }),
            roh = if (rohZahl > 0) rohSumme / rohZahl else null,
            bereinigt = if (gGewicht > 0) gSumme / gGewicht else null,
            zielRoh = if (zielZahl > 0) zielSumme / zielZahl else null,
            zielBereinigt = if (zielGewicht > 0) zielGSumme / zielGewicht else null,
            gesamt = gesamt,
            aktive = rohZahl,
            herdenAnteil = if (mitAnteil.isEmpty()) 0.0 else mitAnteil.count { it.herde }.toDouble() / mitAnteil.size,
            mitIndex = !index.isNullOrEmpty(),
        )
    }
}
