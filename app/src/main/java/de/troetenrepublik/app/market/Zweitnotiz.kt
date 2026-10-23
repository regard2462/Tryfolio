package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

// Zweitnotiz an einem deutschen Handelsplatz fuer auslaendische Aktien. Gehandelt wird von 7:30 bis 23:00 Uhr, Yahoo
// liefert Kurse aber vom Heimatplatz: Kopenhagen, Bruessel oder Paris oeffnen um 9:00 Uhr, New York um 15:30 Uhr
// (vorboerslich ab 10:00 Uhr). Die meisten dieser Aktien werden auch in Frankfurt, Muenchen oder Stuttgart gehandelt,
// dort von 8:00 bis 22:00 Uhr. Ausserhalb der Handelszeit des Heimatplatzes gilt deshalb der juengere Kurs der
// deutschen Notierung, und der Tageschart ergaenzt aus ihr die Zeit vor der Eroeffnung und nach dem Schluss.
// Waehrend der Heimatplatz handelt, zaehlt allein er (mehr Umsatz, dichtere Kurse).
object Zweitnotiz {

    // Deutsche Handelsplaetze in der Reihenfolge, in der sie genommen werden (Yahoo-Kuerzel der Boerse).
    val PLAETZE = listOf("FRA", "STU", "MUN", "BER", "DUS", "HAM", "HAN")

    // Groesste Abweichung zwischen deutscher Notierung und Heimatkurs (in EUR), damit eine Notierung als dieselbe Aktie gilt.
    const val GLEICH = 0.03

    // Hoechstens so weit darf ein deutscher Kurs vom letzten Heimatkurs abweichen, sonst gilt er als Fehlkurs.
    const val AUSREISSER = 0.15

    // Nur Aktien, die nicht ohnehin in Deutschland notieren, brauchen eine Zweitnotiz.
    fun braucht(symbol: String, type: AssetType): Boolean =
        type == AssetType.STOCK && !symbol.endsWith(".DE") && !symbol.endsWith(".F") &&
            !symbol.startsWith("^") && !symbol.contains('=') && !symbol.endsWith("-EUR") && !symbol.endsWith("-USD")

    // Suchbegriff aus dem Namen: ohne Gattung in Klammern und ohne Rechtsform, die Yahoo anders schreibt.
    fun suchbegriff(name: String): String {
        var s = name.replace(Regex("\\(.*?\\)"), " ")
        val weg = Regex(
            "(?i)(,?\\s+(inc\\.?|corp\\.?|corporation|co\\.?|ltd\\.?|limited|plc|ag|se|sa|s\\.a\\.|n\\.v\\.|nv|asa|ab|oyj|a/s|s\\.p\\.a\\.|spa|vz\\.?|st\\.?|holdings?|& co\\. kgaa))+\\s*$",
        )
        s = s.replace(weg, " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    // Treffer der Yahoo-Suche: Symbol, Kuerzel der Boerse, letzter Kurs (an deutschen Plaetzen in EUR).
    data class Kandidat(val symbol: String, val boerse: String, val preis: Double)

    // Passende deutsche Notierung zu einem Heimatkurs heimatEur (in EUR): der erste Handelsplatz der
    // Reihenfolge PLAETZE, an dem eine Notierung hoechstens GLEICH vom Heimatkurs abweicht; gibt es dort
    // mehrere (etwa Stamm- und Vorzugsaktie), die naechstliegende. Hinterlegungsscheine mit anderem
    // Bezugsverhaeltnis fallen so heraus (Umicore in Frankfurt: 5,10 EUR gegen 21,10 EUR in Bruessel).
    fun waehleNotierung(kandidaten: List<Kandidat>, heimatEur: Double): String? {
        if (!(heimatEur > 0)) return null
        for (platz in PLAETZE) {
            val passend = kandidaten
                .filter { it.boerse == platz && it.preis > 0 && abs(it.preis / heimatEur - 1.0) <= GLEICH }
                .minByOrNull { abs(it.preis / heimatEur - 1.0) }
            if (passend != null) return passend.symbol
        }
        return null
    }

    // Kurs fuer die Anzeige aus Heimatkurs heimat und Kurs der Zweitnotiz zweit (beide in EUR). Handelt
    // der Heimatplatz, zaehlt er. Sonst gilt die Zweitnotiz, wenn ihr Kurs juenger ist; die Bezugsgroesse der
    // Tagesveraenderung (Vortagesschluss am Heimatplatz) bleibt.
    fun waehle(heimat: Quote, zweit: Quote?): Quote {
        if (zweit == null || heimat.isOpen) return heimat
        if (!zweit.price.isFinite() || zweit.price <= 0) return heimat
        if (zweit.marketTime <= heimat.marketTime + 60_000L) return heimat
        if (heimat.price > 0 && abs(zweit.price / heimat.price - 1.0) > AUSREISSER) return heimat
        val fx = heimat.fx
        return heimat.copy(
            price = zweit.price,
            nativePrice = if (fx > 0) zweit.price / fx else heimat.nativePrice,
            marketState = if (zweit.isOpen) "REGULAR" else heimat.marketState,
            marketTime = zweit.marketTime,
            secondaryExchange = zweit.exchange.ifBlank { zweit.symbol },
        )
    }

    // So viel juenger muss ein Kurs einer gleichen Notierung sein, damit er den eigenen ersetzt (fuenf Minuten).
    const val VERZUG = 5 * 60_000L

    // Deutsche Plaetze, deren Kurse Yahoo verzoegert liefert (Xetra und Regionalboersen).
    fun verzoegert(symbol: String): Boolean =
        listOf(".DE", ".F", ".SG", ".MU", ".BE", ".DU", ".HM", ".HA").any { symbol.endsWith(it) }

    // Juengster Kurs aus gleichen Notierungen derselben Aktie. Yahoo liefert Xetra und die deutschen Regionalboersen
    // 15 bis 30 Minuten verzoegert, Kopenhagen oder New York dagegen in Echtzeit. Ein anderer Kurs gilt nur, wenn er
    // mindestens VERZUG juenger ist und hoechstens AUSREISSER abweicht; die Bezugsgroesse der Tagesveraenderung
    // (Vortagesschluss dieser Notierung) bleibt.
    fun frischer(eigen: Quote, andere: List<Quote>): Quote {
        val bester = andere.filter {
            it.price.isFinite() && it.price > 0 && it.marketTime > eigen.marketTime + VERZUG &&
                (eigen.price <= 0 || abs(it.price / eigen.price - 1.0) <= AUSREISSER)
        }.maxByOrNull { it.marketTime } ?: return eigen
        val fx = eigen.fx
        return eigen.copy(
            price = bester.price,
            nativePrice = if (fx > 0) bester.price / fx else eigen.nativePrice,
            marketState = if (bester.isOpen) "REGULAR" else eigen.marketState,
            marketTime = bester.marketTime,
            secondaryExchange = bester.exchange.ifBlank { bester.symbol },
        )
    }

    // Tageskurse von Heimatplatz heimat und Zweitnotiz zweit (beide in EUR) zusammenfuehren. Die
    // Zweitnotiz fuellt die Zeit vor dem ersten und nach dem letzten Heimatkurs desselben Handelstags.
    // Liegt der Handelstag der Zweitnotiz nach dem des Heimatplatzes (morgens vor dessen Eroeffnung, Yahoo
    // liefert dann noch den Vortag), zeigt der Tageschart allein die Zweitnotiz.
    fun mische(heimat: Series, zweit: Series, zone: ZoneId): Series {
        val h = heimat.candles
        val z = zweit.candles
        if (z.isEmpty()) return heimat
        if (h.isEmpty()) return zweit
        fun tag(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val tagHeimat = tag(h.first().t)
        val tagZweit = tag(z.last().t)
        // Morgens vor der Eroeffnung des Heimatplatzes der heutige deutsche Verlauf, sobald er eine Linie ergibt.
        // Ein einzelner Umsatz (etwa um 8:05 Uhr) ersetzt den Vortag nicht, sonst gaebe es keinen Verlauf.
        if (tagZweit.isAfter(tagHeimat)) return if (z.size >= 2) zweit else heimat
        if (tagZweit.isBefore(tagHeimat)) return heimat
        val erster = h.first().t
        val letzter = h.last().t
        val davor = z.filter { it.t < erster - RAND_MS && tag(it.t) == tagHeimat }
        val danach = z.filter { it.t > letzter + RAND_MS }
        if (davor.isEmpty() && danach.isEmpty()) return heimat
        val kerzen: List<Candle> = davor + h + danach
        // Handelsfenster: nur solche, die zu ihren Kursen passen (nachts meldet Yahoo fuer den Heimatplatz oft
        // schon den naechsten Tag); die Kurse selbst liegen immer im Fenster. Passt keines: gleichmaessig verteilt.
        val fenster = listOf(heimat, zweit).filter { it.fensterPasst }
        if (fenster.isEmpty()) return Series(kerzen)
        val start = minOf(fenster.minOf { it.dayStart }, kerzen.first().t)
        val ende = maxOf(fenster.maxOf { it.dayEnd }, kerzen.last().t)
        return Series(kerzen, start, ende)
    }

    // Abstand zu den Heimatkursen, ab dem ein Kurs der Zweitnotiz als eigene Zeit gilt (eine Minute).
    private const val RAND_MS = 60_000L
}
