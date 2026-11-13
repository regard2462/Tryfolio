package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// Eingepreist gegen tatsaechlich (0.76): erwartete Bewegung zu den Quartalszahlen aus dem Straddle der Optionen,
// uebliche Bewegung aus den letzten Zahlenterminen, tatsaechliche Bewegung nach dem Termin.
object Zahlenreaktion {

    // schritt: Kursschritt der Reaktion in Prozent mit Vorzeichen, tag: Handelstag der Reaktion (Tage seit 1970),
    // genau: der Termin stammt aus einer Quelle mit Datum (sonst aus dem Umsatzsprung der Kerzen erkannt).
    data class Reaktion(val tag: Long, val schritt: Double, val genau: Boolean)

    enum class Art { STRADDLE, IV }

    // prozent: erwartete Bewegung als Betrag, verfall: erster Verfall nach dem Termin in Sekunden
    data class Erwartet(val prozent: Double, val art: Art, val verfall: Long)

    // Die Einordnung eines Termins: alle Werte sind Betraege in Prozent, tatsaechlich mit Vorzeichen.
    data class Einordnung(
        val erwartet: Erwartet?,
        val ueblich: Double?,
        val tatsaechlich: Double?,
        val reaktionen: List<Reaktion>,
    )

    const val ANZAHL = 8
    const val MIN_TERMINE = 3
    private const val TAG_S = 86_400L
    // Hoechstabstand zwischen Termin und Verfall, darueber sagt der Straddle nichts mehr ueber den Termin
    private const val MAX_ABSTAND_TAGE = 40
    // Ein Umsatzsprung zaehlt ab dem Doppelten des ueblichen Tagesumsatzes
    private const val SPRUNG = 2.0
    private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

    private fun tagVon(t: Long, zone: ZoneId): Long = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toEpochDay()

    private fun median(w: List<Double>): Double? {
        if (w.isEmpty()) return null
        val s = w.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun schritt(von: Candle, nach: Candle): Double? =
        if (von.close > 0 && nach.close > 0) (nach.close / von.close - 1.0) * 100.0 else null

    // Reaktion auf einen Zahlentermin mit bekanntem Tag. Vor Boersenbeginn oder im Handel reagiert der Kurs am Tag
    // selbst, nach Boersenschluss am Folgetag; das zeigt der groessere Umsatz der beiden Tage.
    fun reaktion(kerzen: List<Candle>, tag: Long, zone: ZoneId = NEW_YORK): Reaktion? {
        if (kerzen.size < 2) return null
        val i = kerzen.indexOfFirst { tagVon(it.t, zone) >= tag }
        if (i < 1) return null
        if (tagVon(kerzen[i].t, zone) - tag > 3) return null
        val nach = i + 1 < kerzen.size && kerzen[i + 1].volume > kerzen[i].volume && kerzen[i].volume > 0
        val a = if (nach) i else i - 1
        val b = if (nach) i + 1 else i
        val s = schritt(kerzen[a], kerzen[b]) ?: return null
        return Reaktion(tagVon(kerzen[b].t, zone), s, true)
    }

    // Reaktion rund um einen erwarteten Tag ohne bekannten Termin: der Tag mit dem groessten Umsatz im Fenster, wenn
    // er mindestens das Doppelte des mittleren Umsatzes der 60 Tage davor erreicht.
    fun sprung(kerzen: List<Candle>, mitte: Long, zone: ZoneId = NEW_YORK, halbe: Int = 20): Reaktion? {
        if (kerzen.size < 20) return null
        val tage = kerzen.map { tagVon(it.t, zone) }
        val idx = tage.indices.filter { tage[it] in (mitte - halbe)..(mitte + halbe) }
        if (idx.isEmpty()) return null
        val i = idx.maxByOrNull { kerzen[it].volume } ?: return null
        if (i < 1) return null
        val davor = kerzen.subList(max(0, idx.first() - 60), idx.first()).map { it.volume }.filter { it > 0 }
        val ueblich = median(davor) ?: return null
        if (kerzen[i].volume < SPRUNG * ueblich) return null
        val s = schritt(kerzen[i - 1], kerzen[i]) ?: return null
        return Reaktion(tage[i], s, false)
    }

    // Die letzten Reaktionen, neueste zuerst. exakt: Tage seit 1970 der bekannten Termine, quartalsenden: Tage
    // seit 1970 der Quartalsenden (die Zahlen kommen etwa fuenf Wochen danach). Aeltere Termine werden im
    // Quartalsabstand rueckwaerts gesucht.
    fun reaktionen(
        kerzen: List<Candle>, exakt: List<Long>, quartalsenden: List<Long> = emptyList(), zone: ZoneId = NEW_YORK, anzahl: Int = ANZAHL,
    ): List<Reaktion> {
        if (kerzen.size < 20) return emptyList()
        val aus = ArrayList<Reaktion>()
        fun nah(tag: Long) = aus.any { abs(it.tag - tag) <= 40 }
        exakt.sortedDescending().forEach { t -> reaktion(kerzen, t, zone)?.let { r -> if (!nah(r.tag)) aus += r } }
        val erwartet = ArrayList<Long>()
        quartalsenden.sortedDescending().forEach { e -> erwartet += e + 35 }
        // Rueckwaerts im Quartalsabstand ab dem aeltesten bekannten Termin
        var aeltester = (exakt + erwartet).minOrNull()
        val ersterTag = tagVon(kerzen.first().t, zone)
        while (aeltester != null && erwartet.size + exakt.size < anzahl + 2) {
            aeltester -= 91
            if (aeltester < ersterTag + 30) break
            erwartet += aeltester
        }
        erwartet.sortedDescending().forEach { c ->
            if (aus.size >= anzahl) return@forEach
            if (!nah(c)) sprung(kerzen, c, zone)?.let { r -> if (!nah(r.tag)) aus += r }
        }
        return aus.sortedByDescending { it.tag }.take(anzahl)
    }

    // Uebliche Bewegung: Median der Betraege der letzten Reaktionen; ohneTag laesst die Reaktion dieses Termins
    // aus (Tage seit 1970 des Termins, drei Tage Spielraum). Unter MIN_TERMINE Termine keine Angabe.
    fun ueblich(r: List<Reaktion>, ohneTag: Long? = null, anzahl: Int = ANZAHL): Double? {
        val w = r.filter { ohneTag == null || abs(it.tag - ohneTag) > 3 }.sortedByDescending { it.tag }.take(anzahl)
        if (w.size < MIN_TERMINE) return null
        return median(w.map { abs(it.schritt) })
    }

    // Tatsaechliche Bewegung zu einem Termin: die Reaktion am Termintag oder dem Tag danach (bis drei Tage).
    fun tatsaechlich(r: List<Reaktion>, termin: Long): Double? =
        r.filter { it.tag - termin in 0..3 }.minByOrNull { it.tag }?.schritt

    // Erwartete Bewegung: Straddle (Call plus Put zum Strike am Kurs, jeweils Mitte aus Geld und Brief, sonst
    // letzter Preis) des ersten Verfalls, der die Reaktion noch enthaelt, geteilt durch den Kurs. Ohne brauchbare
    // Preise aus der impliziten Volatilitaet: 0,8 mal IV mal Wurzel aus der Laufzeit in Jahren.
    // ab: erster zulaessiger Verfallstag (Tage seit 1970), bei Zahlen nach Boersenschluss der Tag nach dem Termin.
    fun erwartet(kurs: Double, kontrakte: List<Kontrakt>, ab: Long, jetztS: Long): Erwartet? {
        if (kurs <= 0 || kontrakte.isEmpty()) return null
        val verfall = kontrakte.map { it.verfall }.distinct().sorted().firstOrNull { it / TAG_S >= ab } ?: return null
        if (verfall / TAG_S - ab > MAX_ABSTAND_TAGE) return null
        val k = kontrakte.filter { it.verfall == verfall }
        val strike = k.map { it.strike }.distinct().minByOrNull { abs(it - kurs) } ?: return null
        val call = k.firstOrNull { it.call && it.strike == strike }
        val put = k.firstOrNull { !it.call && it.strike == strike }
        if (call != null && put != null && call.mitte > 0 && put.mitte > 0) {
            return Erwartet((call.mitte + put.mitte) / kurs * 100.0, Art.STRADDLE, verfall)
        }
        val iv = listOfNotNull(call?.iv, put?.iv).filter { it > 0.01 }
        if (iv.isEmpty()) return null
        val tage = max(1.0, (verfall - jetztS) / TAG_S.toDouble())
        return Erwartet(0.8 * iv.average() * sqrt(tage / 365.0) * 100.0, Art.IV, verfall)
    }

    // Ueberraschung in Prozent des Erwarteten; null bei Erwartung null.
    fun ueberraschung(ist: Double?, erwartet: Double?): Double? = Gewinnueberraschung.abweichung(ist, erwartet)

    // Faktor von tatsaechlich zu erwartet; null ohne beides.
    fun verhaeltnis(tatsaechlich: Double?, erwartet: Double?): Double? =
        if (tatsaechlich == null || erwartet == null || erwartet <= 0.0) null else abs(tatsaechlich) / erwartet

    fun einordnen(
        termin: Long, erwartet: Erwartet?, reaktionen: List<Reaktion>, vergangen: Boolean,
    ): Einordnung = Einordnung(
        erwartet = erwartet,
        ueblich = ueblich(reaktionen, ohneTag = if (vergangen) termin else null),
        tatsaechlich = if (vergangen) tatsaechlich(reaktionen, termin) else null,
        reaktionen = reaktionen,
    )
}
