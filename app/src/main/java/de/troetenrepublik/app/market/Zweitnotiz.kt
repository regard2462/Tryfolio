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
        type == AssetType.STOCK && !KursQuellen.istExtern(symbol) && !symbol.endsWith(".DE") && !symbol.endsWith(".F") &&
            !symbol.startsWith("^") && !symbol.contains('=') && !symbol.endsWith("-EUR") && !symbol.endsWith("-USD")

    // Suchbegriff aus dem Namen: wie Firmenname.bereinigt (Gattung, Notierungszusatz, Nennwert und Rechtsform
    // weg); gilt ueberall, wo die App nach einem Namen sucht.
    fun suchbegriff(name: String): String = Firmenname.bereinigt(name)

    // Suchbegriffe fuer die Suche nach der US-Notierung: der bereinigte Name und, falls er ein Komma enthaelt,
    // zusaetzlich der Teil davor (Yahoo-Kurznamen wie "Ericsson, Telefonab. L M ser. B" -> "Ericsson").
    fun usSuchbegriffe(name: String): List<String> = Firmenname.suchbegriffe(name)

    // Treffer der Yahoo-Suche: Symbol, Kuerzel der Boerse, letzter Kurs (an deutschen Plaetzen in EUR).
    data class Kandidat(val symbol: String, val boerse: String, val preis: Double, val name: String = "")

    // US-Boersen, an denen Yahoo Optionen fuehrt (fuer Hinterlegungsscheine auslaendischer Aktien). Yahoo liefert
    // die Suche teils mit den Kuerzeln NYSE, NAS, NASDAQ, AMEX, ARCA und BATS.
    val US_BOERSEN = setOf("NYQ", "NMS", "NGM", "NCM", "ASE", "PCX", "BTS", "NYSE", "NAS", "NASDAQ", "AMEX", "ARCA", "BATS")

    // Woerter, die der US-Name zusaetzlich tragen darf (Toyota Motor, Sony Group, Banco Santander, Koninklijke Philips).
    private val ZUSATZ = setOf("group", "motor", "motors", "company", "banco", "koninklijke")

    // Woerter, die nur der Heimatname traegt, etwa "Telefonaktiebolaget LM Ericsson" gegen "Ericsson".
    private val HEIM_ZUSATZ = setOf("telefonaktiebolaget", "aktiebolaget", "lm", "koninklijke", "group", "holding", "motor")

    // US-Notierung derselben Firma unter den Treffern: ein Kuerzel ohne Boersenzusatz an einer US-Boerse, dessen
    // Name die Woerter des Suchbegriffs in Folge enthaelt und sonst hoechstens Woerter aus ZUSATZ, oder umgekehrt
    // der Heimatname die Woerter des US-Namens in Folge und sonst hoechstens Woerter aus HEIM_ZUSATZ. Ein blosser
    // Namensanfang reicht nicht: „Puma Biotechnology“ ist nicht PUMA SE, „Mitsubishi UFJ Financial Group“ nicht
    // die Mitsubishi Corporation.
    fun usNotierung(begriff: String, kandidaten: List<Kandidat>): String? {
        val b = Firmenname.worte(begriff)
        if (b.isEmpty()) return null
        fun passt(name: String): Boolean {
            val n = Firmenname.worte(name)
            if (n == b) return true
            if (Firmenname.endetAufInc(name)) return false
            if (n.size > b.size) return (0..n.size - b.size).any { i ->
                n.subList(i, i + b.size) == b && (n.subList(0, i) + n.subList(i + b.size, n.size)).all { it in ZUSATZ }
            }
            // Umkehrung: der Heimatname traegt die Woerter des US-Namens und sonst nur Woerter aus HEIM_ZUSATZ;
            // ein US-Name aus blossen Einzelbuchstaben reicht nicht
            if (n.size < b.size && n.any { it.length >= 2 }) return (0..b.size - n.size).any { i ->
                b.subList(i, i + n.size) == n && (b.subList(0, i) + b.subList(i + n.size, b.size)).all { it in HEIM_ZUSATZ }
            }
            return false
        }
        return kandidaten.firstOrNull {
            it.boerse in US_BOERSEN && Regex("^[A-Z]{1,5}$").matches(it.symbol) && passt(it.name)
        }?.symbol
    }

    // US-Suche ueber alle Suchbegriffe: das Kuerzel der ersten passenden Notierung, "", wenn alle Begriffe
    // beantwortet wurden und keiner passt, null, wenn eine Suche ohne Antwort blieb (Netzfehler). Der zweite
    // Begriff wird nur gefragt, wenn der erste nichts Passendes ergab; eine leere Trefferliste fuehrt zum
    // naechsten Begriff.
    suspend fun usSuchen(name: String, suche: suspend (String) -> List<Kandidat>?): String? {
        for (begriff in usSuchbegriffe(name)) {
            val treffer = suche(begriff) ?: return null
            usNotierung(begriff, treffer)?.let { return it }
        }
        return ""
    }

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

    // So lange gilt eine gemerkte Zuordnung. Frankfurt (.F) ist der Platz mit den meisten Umsaetzen und gilt 30 Tage. Jeder
    // andere Platz (oder keiner) wird nach einem Tag neu gesucht: die Zuordnung entstand an einem Tag, an dem Frankfurt
    // wegen eines veralteten letzten Kurses nicht passte, und ein Platz mit vier Umsaetzen am Tag (Hannover, Hamburg)
    // ergibt im Tageschart eine Stufe und einen Kurs von heute Morgen.
    const val BEVORZUGT = ".F"
    const val FRIST_BEVORZUGT = 30L * 86_400_000L
    const val FRIST_SONST = 86_400_000L

    fun fristFuer(zweit: String): Long = if (zweit.endsWith(BEVORZUGT)) FRIST_BEVORZUGT else FRIST_SONST

    // Eine Neusuche ohne Treffer nimmt eine gemerkte Zuordnung nicht zurueck: der schlechtere Platz bleibt besser als keiner.
    fun beibehalten(alt: String, gefunden: String): String = gefunden.ifEmpty { alt }

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

    // Endungen der deutschen Handelsplaetze bei Yahoo: Xetra, Stuttgart, Frankfurt, Muenchen, Duesseldorf, Hamburg,
    // Hannover, Berlin. Das Kuerzel davor ist an allen Plaetzen dasselbe.
    private val ENDUNGEN = listOf(".DE", ".SG", ".F", ".MU", ".DU", ".HM", ".HA", ".BE")

    // Dieselbe Aktie an den anderen deutschen Handelsplaetzen (0.77). An einem einzelnen Platz gibt es morgens oft nur
    // zwei, drei Umsaetze; zusammen ergeben die Plaetze einen dichten Verlauf aus echten Kursen.
    fun schwestern(symbol: String): List<String> {
        val endung = ENDUNGEN.firstOrNull { symbol.endsWith(it) } ?: return emptyList()
        val stamm = symbol.removeSuffix(endung)
        if (stamm.isEmpty()) return emptyList()
        return ENDUNGEN.filter { it != endung }.map { stamm + it }
    }

    // Weicht der mittlere Kurs eines Platzes weiter vom Hauptplatz ab, ist es nicht dieselbe Aktie.
    const val PLATZ_GLEICH = 0.05

    private fun mittel(werte: List<Double>): Double {
        val s = werte.sorted()
        return if (s.isEmpty()) Double.NaN else if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    // Kurse mehrerer Handelsplaetze derselben Aktie zu einer Reihe: je Zeitschritt der mittlere Kurs der Plaetze, die
    // dort einen Umsatz haben. haupt ist die Reihe der Zweitnotiz; ein Platz, dessen mittlerer Kurs um mehr als
    // PLATZ_GLEICH von ihr abweicht, bleibt weg (ohne Kurse am Hauptplatz gilt der Platz mit den meisten Kursen).
    fun vereine(haupt: List<Candle>, weitere: List<List<Candle>>, schrittMs: Long = 120_000L): List<Candle> {
        fun gut(l: List<Candle>) = l.filter { it.close.isFinite() && it.close > 0 }
        val h = gut(haupt)
        val w = weitere.map { gut(it) }.filter { it.isNotEmpty() }
        if (w.isEmpty()) return h
        val bezug = mittel((if (h.isNotEmpty()) h else w.maxByOrNull { it.size }!!).map { it.close })
        val passend = w.filter { abs(mittel(it.map { c -> c.close }) / bezug - 1.0) <= PLATZ_GLEICH }
        if (passend.isEmpty()) return h
        val jeSchritt = java.util.TreeMap<Long, MutableList<Candle>>()
        for (l in listOf(h) + passend) for (c in l) jeSchritt.getOrPut(c.t / schrittMs) { ArrayList() }.add(c)
        return jeSchritt.map { (k, cs) ->
            Candle(
                k * schrittMs, mittel(cs.map { it.open }.filter { it.isFinite() && it > 0 }.ifEmpty { cs.map { it.close } }),
                cs.maxOf { maxOf(it.high, it.close) }, cs.minOf { if (it.low > 0) minOf(it.low, it.close) else it.close },
                mittel(cs.map { it.close }), cs.sumOf { it.volume },
            )
        }
    }

    // Abstand zu den Heimatkursen, ab dem ein Kurs der Zweitnotiz als eigene Zeit gilt (eine Minute).
    private const val RAND_MS = 60_000L
}
