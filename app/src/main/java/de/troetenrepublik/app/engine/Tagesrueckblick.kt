package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Wichtig
import de.troetenrepublik.app.market.alsGanzesWort
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

// Tagesrueckblick am Abend jedes Handelstags: Markt, wichtige Nachrichten und Bewegungen im Depot, bei groesseren
// Bewegungen mit der wahrscheinlichen Ursache. woche fasst die Rueckblicke einer Woche zusammen.
// Die Ursache ist eine Vermutung: die am besten bewertete Schlagzeile des Tages, die den Wert nennt (Bewertung wie
// in Wichtig). Ohne passende Schlagzeile zaehlt, ob der Wert mit seinem Markt lief.
object Tagesrueckblick {

    // Ein Markt des Rueckblicks: Schluessel aus MAERKTE und Tagesaenderung in Prozent.
    @Serializable
    data class Markt(val schluessel: String, val aenderung: Double)

    @Serializable
    data class Nachricht(val titel: String, val quelle: String, val link: String, val zeit: Long)

    // Eine Bewegung im Depot: Prozent und Euro des Tages, die vermutete Nachricht oder der Markt, mit dem der Wert lief.
    @Serializable
    data class Bewegung(
        val symbol: String,
        val name: String,
        val prozent: Double,
        val betrag: Double,
        val grund: Nachricht? = null,
        val markt: String? = null,
        val marktAenderung: Double? = null,
    )

    // Ein Wert ausserhalb des Depots (grosse Aktie am Markt, Watchlist): Tagesaenderung und vermuteter Grund.
    @Serializable
    data class Spitze(val symbol: String, val name: String, val prozent: Double, val grund: Nachricht? = null)

    @Serializable
    data class Rueckblick(
        // Tag (Tage seit 1970, Ortszeit).
        val tag: Long,
        val erstellt: Long,
        val markt: List<Markt> = emptyList(),
        val depotWert: Double = 0.0,
        val depotAenderung: Double = 0.0,
        val depotProzent: Double? = null,
        val bewegungen: List<Bewegung> = emptyList(),
        val nachrichten: List<Nachricht> = emptyList(),
        // Vor dem US-Boersenschluss von Hand erstellt; die Zahlen koennen sich noch aendern.
        val vorlaeufig: Boolean = false,
        // Branchen des Tages (Schluessel: Kuerzel des ETFs aus Branchentrends.BRANCHEN), staerkste zuerst.
        val branchen: List<Markt> = emptyList(),
        // Groesste Gewinner und Verlierer unter den grossen Aktien.
        val gewinner: List<Spitze> = emptyList(),
        val verlierer: List<Spitze> = emptyList(),
        // Staerkste Bewegungen der Watchlist ohne Werte im Depot.
        val merkliste: List<Spitze> = emptyList(),
        // Nachtraeglich aus Buchungen und historischen Tageskursen erstellt (Tag aus der Vergangenheit, nie abends erzeugt).
        val nachtraeglich: Boolean = false,
    )

    @Serializable
    data class Archiv(val tage: List<Rueckblick> = emptyList())

    // Ein Wert im Depot fuer den Rueckblick: Wert und Aenderung heute in Euro, Suchbegriffe fuer Schlagzeilen.
    data class Position(val symbol: String, val name: String, val wert: Double, val aenderung: Double, val begriffe: List<String>) {
        val prozent: Double get() = (wert - aenderung).let { basis -> if (basis > 0) aenderung / basis * 100 else 0.0 }
    }

    // Die Maerkte des Rueckblicks: Schluessel, Name, Kuerzel bei Yahoo.
    val MAERKTE = listOf(
        Triple("DAX", "DAX", "^GDAXI"),
        Triple("ESTX50", "Euro Stoxx 50", "^STOXX50E"),
        // MSCI World ueber den ETF an der US-Boerse, der Index selbst ist bei Yahoo nicht verlaesslich abrufbar.
        Triple("WORLD", "MSCI World", "URTH"),
        Triple("SP500", "S&P 500", "^GSPC"),
        Triple("NDX", "Nasdaq 100", "^NDX"),
        Triple("NIKKEI", "Nikkei 225", "^N225"),
        Triple("BTC", "Bitcoin", "BTC-EUR"),
        Triple("GOLD", "Gold", "GC=F"),
    )

    fun marktName(schluessel: String): String = MAERKTE.firstOrNull { it.first == schluessel }?.second ?: schluessel

    // Massstab fuer das Depot: der MSCI World, sonst der S&P 500, sonst der erste Markt.
    fun leitmarkt(markt: List<Markt>): Markt? =
        markt.firstOrNull { it.schluessel == "WORLD" } ?: markt.firstOrNull { it.schluessel == "SP500" } ?: markt.firstOrNull()

    // Hoechstens so viele Gewinner, Verlierer und Werte der Watchlist.
    const val SPITZEN = 3

    // Die Watchlist zaehlt erst ab einem Prozent.
    const val MIN_MERKLISTE = 1.0

    // Groesste Gewinner (hoch) oder Verlierer unter werte.
    fun spitzen(werte: List<Spitze>, hoch: Boolean): List<Spitze> =
        werte.filter { if (hoch) it.prozent >= MIN_PROZENT else it.prozent <= -MIN_PROZENT }
            .sortedBy { if (hoch) -it.prozent else it.prozent }.take(SPITZEN)

    // Staerkste Bewegungen der Watchlist, ohne Werte, die schon im Depot stehen.
    fun merklisteAuswahl(werte: List<Spitze>, imDepot: Set<String>): List<Spitze> =
        werte.filter { it.symbol !in imDepot && abs(it.prozent) >= MIN_MERKLISTE }.sortedByDescending { abs(it.prozent) }.take(SPITZEN)

    // So lange bleiben Rueckblicke gespeichert (gut ein Jahr).
    const val AUFBEWAHREN_TAGE = 400

    // Ab dieser Uhrzeit (Ortszeit, Minuten seit Mitternacht) ist der Rueckblick faellig, nach US-Boersenschluss.
    const val AB_MINUTE = 22 * 60 + 10

    // Bis zu dieser Uhrzeit am Morgen wird der Rueckblick des Vortags nachgeholt.
    const val NACHHOLEN_BIS_MINUTE = 8 * 60

    // Uhrzeiten vor dieser Minute gelten als morgens: der Rueckblick fasst dann den vorigen Handelstag zusammen.
    const val MORGENS_BIS_MINUTE = 12 * 60

    // Waehlbare Uhrzeiten (Minuten seit Mitternacht, Ortszeit); Vorgabe AB_MINUTE.
    val UHRZEITEN = listOf(7 * 60, 8 * 60, 18 * 60, 19 * 60, 20 * 60, 21 * 60, AB_MINUTE, 23 * 60)

    fun morgens(ab: Int): Boolean = ab < MORGENS_BIS_MINUTE

    // Uhrzeit als "22:10" (padStart statt String.format, das es auf iOS nicht gibt).
    fun uhrzeit(minute: Int): String =
        (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')

    // Hoechstens so viele Bewegungen und Nachrichten je Tag.
    const val BEWEGUNGEN = 5
    const val NACHRICHTEN = 6

    // Kleinere Bewegungen (Prozent) zaehlen nicht.
    const val MIN_PROZENT = 0.5

    private fun werktag(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    // Tag, fuer den jetzt ein Rueckblick faellig ist; sonst null. Abends: Werktag ab der Uhrzeit, der Vortag bis
    // NACHHOLEN_BIS_MINUTE. Morgens: der Vortag ab der Uhrzeit. Mit einem endgueltigen Rueckblick ist der Tag erledigt.
    fun faellig(jetzt: Long, archiv: Archiv, zone: ZoneId = ZoneId.systemDefault(), ab: Int = AB_MINUTE): Long? {
        val z = Instant.ofEpochMilli(jetzt).atZone(zone)
        val minute = z.hour * 60 + z.minute
        val d = when {
            morgens(ab) -> if (minute >= ab) z.toLocalDate().minusDays(1) else return null
            minute >= ab -> z.toLocalDate()
            minute < NACHHOLEN_BIS_MINUTE -> z.toLocalDate().minusDays(1)
            else -> return null
        }
        if (!werktag(d)) return null
        val tag = d.toEpochDay()
        return tag.takeIf { t -> archiv.tage.none { it.tag == t && !it.vorlaeufig } }
    }

    // Gilt ein jetzt von Hand erstellter Rueckblick fuer heute als vorlaeufig? Morgens immer, der endgueltige entsteht am naechsten Morgen.
    fun vorlaeufig(jetzt: Long, ab: Int = AB_MINUTE, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (morgens(ab)) return true
        val z = Instant.ofEpochMilli(jetzt).atZone(zone)
        return z.hour * 60 + z.minute < ab
    }

    // Naechster Zeitpunkt (ms) nach jetzt, an dem es ab Uhr ist und ein Rueckblick faellig wird (abends der Tag, morgens der Vortag ein Werktag).
    fun naechsteAusfuehrung(jetzt: Long, ab: Int = AB_MINUTE, zone: ZoneId = ZoneId.systemDefault()): Long {
        val heute = Instant.ofEpochMilli(jetzt).atZone(zone).toLocalDate()
        for (i in 0L..9L) {
            val tag = heute.plusDays(i)
            val gilt = if (morgens(ab)) werktag(tag.minusDays(1)) else werktag(tag)
            if (!gilt) continue
            val ms = tag.atTime(ab / 60, ab % 60).atZone(zone).toInstant().toEpochMilli()
            if (ms > jetzt) return ms
        }
        return jetzt + 24 * 3_600_000L
    }

    // Beginn des Nachrichtenfensters eines Tags: der Vorabend ab 22 Uhr (Meldungen nach dem US-Schluss zaehlen mit).
    fun fensterBeginn(tag: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(tag).atStartOfDay(zone).toInstant().toEpochMilli() - 2 * 3_600_000L

    // Der Markt, mit dem ein Wert vor allem laeuft: US-Werte mit dem S&P 500, deutsche mit dem DAX, Krypto mit Bitcoin.
    fun marktFuer(symbol: String): String {
        val s = symbol.uppercase()
        return when {
            s.endsWith("-EUR") || s.endsWith("-USD") -> "BTC"
            s.endsWith(".T") -> "NIKKEI"
            s.endsWith(".DE") || s.endsWith(".F") || s.endsWith(".SG") || s.endsWith(".MU") || s.endsWith(".BE") || s.endsWith(".DU") || s.endsWith(".HM") -> "DAX"
            '.' in s -> "ESTX50"
            else -> "SP500"
        }
    }

    // Rueckblick fuer tag. nachrichten: Schlagzeilen der Feeds; eigene: Schlagzeilen je Kuerzel (Nachrichten zum
    // Wert); beide werden auf das Fenster des Tags begrenzt.
    fun erstellen(
        tag: Long,
        jetzt: Long,
        markt: List<Markt>,
        positionen: List<Position>,
        nachrichten: List<Rss.Eintrag>,
        eigene: Map<String, List<Rss.Eintrag>> = emptyMap(),
        vorlaeufig: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
        branchen: List<Markt> = emptyList(),
        // Tagesaenderung der grossen Aktien am Markt (ohne Grund).
        marktwerte: List<Spitze> = emptyList(),
        // Tagesaenderung der Werte auf der Watchlist (ohne Grund).
        merkliste: List<Spitze> = emptyList(),
        // Wichtige Nachrichten nur auf Englisch (Settings.wichtigNurEnglisch)
        nurEnglisch: Boolean = false,
    ): Rueckblick {
        val von = fensterBeginn(tag, zone)
        val bis = LocalDate.ofEpochDay(tag + 1).atStartOfDay(zone).toInstant().toEpochMilli() + NACHHOLEN_BIS_MINUTE * 60_000L
        val imFenster = { e: Rss.Eintrag -> e.zeit in von..minOf(bis, jetzt) }
        val tagesNachrichten = nachrichten.filter(imFenster)
        val alleBegriffe = positionen.flatMap { it.begriffe }.distinct()

        // Wichtige Nachrichten wie auf der Nachrichtenseite, je Geschichte die erste
        val ids = Wichtig.wichtige(tagesNachrichten, alleBegriffe, nurEnglisch)
        val punkte = Wichtig.punkte(tagesNachrichten, alleBegriffe)
        val wichtig = tagesNachrichten.indices.filter { tagesNachrichten[it].id in ids }
            .sortedWith(compareByDescending<Int> { punkte[it].punkte }.thenByDescending { tagesNachrichten[it].zeit })
            .take(NACHRICHTEN)
            .map { nachricht(tagesNachrichten[it]) }

        val wert = positionen.sumOf { it.wert }
        val aenderung = positionen.sumOf { it.aenderung }
        val basis = wert - aenderung
        val maerkte = markt.associate { it.schluessel to it.aenderung }

        val bewegungen = positionen.filter { abs(it.prozent) >= MIN_PROZENT && it.wert > 0 }
            .sortedByDescending { abs(it.aenderung) }
            .take(BEWEGUNGEN)
            .map { p ->
                val kandidaten = tagesNachrichten.filter { e -> p.begriffe.any { b -> alsGanzesWort(e.titel, b) } }
                    .map { it to false } + eigene[p.symbol].orEmpty().filter(imFenster).map { it to true }
                val grund = grund(kandidaten, p.begriffe)
                val m = marktFuer(p.symbol)
                val ma = maerkte[m]
                // Ohne Nachricht zaehlt, ob der Wert in dieselbe Richtung lief wie sein Markt.
                val mitMarkt = grund == null && ma != null && ma * p.prozent > 0 && abs(p.prozent) <= abs(ma) * 3 + 1
                Bewegung(p.symbol, p.name, p.prozent, p.aenderung, grund?.let { nachricht(it) }, if (mitMarkt) m else null, if (mitMarkt) ma else null)
            }

        // Werte ausserhalb des Depots: der Grund wie bei den Bewegungen, ohne den Vergleich mit dem Markt
        val mitGrund = { sp: Spitze ->
            val begriffe = Wichtig.depotBegriffe(listOf(sp.name), listOf(sp.symbol))
            val kandidaten = tagesNachrichten.filter { e -> begriffe.any { b -> alsGanzesWort(e.titel, b) } }
                .map { it to false } + eigene[sp.symbol].orEmpty().filter(imFenster).map { it to true }
            sp.copy(grund = grund(kandidaten, begriffe)?.let { nachricht(it) })
        }
        val imDepot = positionen.map { it.symbol }.toSet()

        return Rueckblick(
            tag = tag, erstellt = jetzt, markt = markt,
            depotWert = wert, depotAenderung = aenderung, depotProzent = if (basis > 0) aenderung / basis * 100 else null,
            bewegungen = bewegungen, nachrichten = wichtig, vorlaeufig = vorlaeufig,
            branchen = branchen.sortedByDescending { it.aenderung },
            gewinner = spitzen(marktwerte, hoch = true).map(mitGrund),
            verlierer = spitzen(marktwerte, hoch = false).map(mitGrund),
            merkliste = merklisteAuswahl(merkliste, imDepot).map(mitGrund),
        )
    }

    // Die am besten bewertete Schlagzeile; Nachrichten zum Wert selbst gehen bei gleichen Punkten vor.
    private fun grund(kandidaten: List<Pair<Rss.Eintrag, Boolean>>, begriffe: List<String>): Rss.Eintrag? {
        val bewertet = kandidaten.distinctBy { it.first.titel.lowercase() }
            .filter { Wichtig.ausgeschlossen(it.first) == null }
            .map { (e, eigen) -> Triple(e, eigen, Wichtig.bewerten(e, begriffe).punkte) }
        val beste = bewertet.maxWithOrNull(compareBy<Triple<Rss.Eintrag, Boolean, Int>> { it.third }.thenBy { it.second }.thenBy { it.first.zeit })
            ?: return null
        // Ohne erkanntes Ereignis nur eine Nachricht zum Wert selbst
        return beste.first.takeIf { beste.third > 0 || beste.second }
    }

    private fun nachricht(e: Rss.Eintrag) = Nachricht(e.titel, e.herausgeber.ifBlank { e.quelle }, e.link, e.zeit)

    // Rueckblick eintragen: ersetzt einen vorhandenen desselben Tags, alte Tage fallen weg; neuester zuerst.
    fun eintragen(a: Archiv, r: Rueckblick): Archiv {
        val alle = a.tage.filter { it.tag != r.tag } + r
        // Gemessen am neuesten Tag, damit ein nachtraeglich erstellter alter Tag die neueren nicht verdraengt
        val neuester = alle.maxOf { it.tag }
        return Archiv(alle.filter { it.tag > neuester - AUFBEWAHREN_TAGE }.sortedByDescending { it.tag })
    }

    // Rueckblick fuer die Vergangenheit

    // Ein Wert im Bestand eines vergangenen Tags (heutige Einheiten, wie die Kurse von Yahoo nach Splits).
    data class Bestand(val symbol: String, val name: String, val stueck: Double)

    // Schluss eines Tags und der davor. amTag: der Wert hat an diesem Tag gehandelt (sonst Feiertag oder Luecke).
    data class Tagesschluss(val schluss: Double, val davor: Double?, val amTag: Boolean) {
        val prozent: Double? get() = if (amTag && davor != null && davor > 0) (schluss / davor - 1) * 100 else null
    }

    // Tag (Tage seit 1970) einer Tageskerze; Yahoo stempelt sie mit der Eroeffnung der Boerse, die UTC-Tage stimmen.
    fun kerzenTag(t: Long): Long = Math.floorDiv(t, 86_400_000L)

    // So alt darf der letzte Schluss vor dem Tag hoechstens sein, sonst gilt der Wert als nicht notiert (Tage).
    const val MAX_LUECKE_TAGE = 10

    fun schlussAm(kerzen: List<de.troetenrepublik.app.data.Candle>, tag: Long): Tagesschluss? {
        val gueltig = kerzen.filter { it.close.isFinite() && it.close > 0 }
        val i = gueltig.indexOfLast { kerzenTag(it.t) <= tag }
        if (i < 0) return null
        val d = kerzenTag(gueltig[i].t)
        if (tag - d > MAX_LUECKE_TAGE) return null
        return if (d == tag) Tagesschluss(gueltig[i].close, gueltig.getOrNull(i - 1)?.close, true)
        else Tagesschluss(gueltig[i].close, null, false)
    }

    // Wertpapiere im Depot am Ende von tag, aus den Buchungen (Derivate und Werte ohne Kurs zaehlen nicht).
    // splits: Aktiensplits uebernommener Depots, siehe Trading.holdingsAt.
    fun bestandAm(konto: de.troetenrepublik.app.data.Account, tag: Long, zone: ZoneId = ZoneId.systemDefault(), splits: Map<String, List<Splits.Split>> = emptyMap()): List<Bestand> {
        val ende = beginn(tag + 1, zone) - 1
        return Trading.holdingsAt(konto, ende, splits).values
            .filter { it.derivative == null && it.symbol.isNotBlank() && !it.symbol.startsWith(TradeRepublicImport.OHNE_KURS) }
            .groupBy { it.symbol }
            .map { (sym, l) -> Bestand(sym, l.first().name.ifBlank { sym }, l.sumOf { it.qty }) }
            .sortedBy { it.symbol }
    }

    // Yahoo-Zeitraum, der von tag bis jetzt reicht; null, wenn der Tag weiter zurueckliegt als zehn Jahre.
    fun abrufZeitraum(tag: Long, jetzt: Long): String? {
        val alter = tag(jetzt) - tag + MAX_LUECKE_TAGE
        return when {
            alter <= 30 -> "1mo"
            alter <= 90 -> "3mo"
            alter <= 180 -> "6mo"
            alter <= 365 -> "1y"
            alter <= 730 -> "2y"
            alter <= 1825 -> "5y"
            alter <= 3650 -> "10y"
            else -> null
        }
    }

    // Rueckblick fuer einen vergangenen Tag: Bestand aus den Buchungen, Tagesaenderungen aus Tageskursen.
    // kurse: je Symbol des Bestands (EUR); marktKurse: je Yahoo-Kuerzel der Maerkte und Branchen (nur Prozente zaehlen).
    // Null, wenn weder Markt noch Depot Kurse fuer den Tag haben.
    fun vergangenheit(
        tag: Long,
        jetzt: Long,
        bestand: List<Bestand>,
        kurse: Map<String, List<de.troetenrepublik.app.data.Candle>>,
        marktKurse: Map<String, List<de.troetenrepublik.app.data.Candle>>,
        nachrichten: List<Rss.Eintrag> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        nurEnglisch: Boolean = false,
    ): Rueckblick? {
        val markt = MAERKTE.mapNotNull { (k, _, sym) -> marktKurse[sym]?.let { schlussAm(it, tag) }?.prozent?.let { Markt(k, it) } }
        val branchen = Branchentrends.BRANCHEN.mapNotNull { b -> marktKurse[b.symbol]?.let { schlussAm(it, tag) }?.prozent?.let { Markt(b.symbol, it) } }
        val positionen = bestand.mapNotNull { b ->
            val s = kurse[b.symbol]?.let { schlussAm(it, tag) } ?: return@mapNotNull null
            val aenderung = if (s.amTag && s.davor != null) b.stueck * (s.schluss - s.davor) else 0.0
            Position(b.symbol, b.name, b.stueck * s.schluss, aenderung, Wichtig.depotBegriffe(listOf(b.name), listOf(b.symbol)))
        }
        if (markt.isEmpty() && positionen.isEmpty()) return null
        return erstellen(tag, jetzt, markt, positionen, nachrichten, zone = zone, branchen = branchen, nurEnglisch = nurEnglisch).copy(nachtraeglich = true)
    }

    // Woche

    data class Woche(
        // Montag der Woche (Tage seit 1970).
        val montag: Long,
        // Rueckblicke der Woche, neuester zuerst.
        val tage: List<Rueckblick>,
        val depotAenderung: Double,
        val depotProzent: Double?,
        // Wochenaenderung je Markt in Prozent (Tage verkettet).
        val markt: List<Markt>,
        // Groesste Bewegungen der Woche: Euro und verkettete Prozent je Wert.
        val bewegungen: List<Bewegung>,
        val nachrichten: List<Nachricht>,
        // Branchen in der Woche (Tage verkettet), staerkste zuerst.
        val branchen: List<Markt> = emptyList(),
    ) {
        val freitag: Long get() = montag + 4
    }

    fun montag(tag: Long): Long = tag - (LocalDate.ofEpochDay(tag).dayOfWeek.value - 1)

    // Alle Wochen des Archivs, neueste zuerst.
    fun wochen(a: Archiv): List<Woche> = a.tage.groupBy { montag(it.tag) }.entries.sortedByDescending { it.key }.map { (m, t) -> woche(m, t) }

    fun woche(montag: Long, tage: List<Rueckblick>): Woche {
        val sortiert = tage.sortedByDescending { it.tag }
        val chrono = sortiert.reversed()
        val prozent = chrono.mapNotNull { it.depotProzent }
        val markt = MAERKTE.mapNotNull { (k, _, _) ->
            val werte = chrono.mapNotNull { r -> r.markt.firstOrNull { it.schluessel == k }?.aenderung }
            if (werte.isEmpty()) null else Markt(k, (werte.fold(1.0) { acc, v -> acc * (1 + v / 100) } - 1) * 100)
        }
        val bewegungen = chrono.flatMap { it.bewegungen }.groupBy { it.symbol }.map { (s, l) ->
            val p = (l.fold(1.0) { acc, b -> acc * (1 + b.prozent / 100) } - 1) * 100
            // Die Nachricht des Tags mit der groessten Bewegung dieses Werts
            val staerkste = l.maxBy { abs(it.betrag) }
            Bewegung(s, l.last().name, p, l.sumOf { it.betrag }, staerkste.grund, staerkste.markt, staerkste.marktAenderung)
        }.sortedByDescending { abs(it.betrag) }.take(BEWEGUNGEN)
        val nachrichten = sortiert.flatMap { it.nachrichten.take(2) }.distinctBy { it.titel.lowercase() }.take(8)
        val branchen = chrono.flatMap { it.branchen }.groupBy { it.schluessel }
            .map { (k, l) -> Markt(k, (l.fold(1.0) { acc, b -> acc * (1 + b.aenderung / 100) } - 1) * 100) }
            .sortedByDescending { it.aenderung }
        return Woche(
            montag = montag, tage = sortiert, depotAenderung = chrono.sumOf { it.depotAenderung },
            depotProzent = if (prozent.isEmpty()) null else (prozent.fold(1.0) { acc, v -> acc * (1 + v / 100) } - 1) * 100,
            markt = markt, bewegungen = bewegungen, nachrichten = nachrichten, branchen = branchen,
        )
    }

    // true am letzten Handelstag der Woche (Freitag); dann kommt mit dem Tagesrueckblick der Wochenrueckblick.
    fun wochenende(tag: Long): Boolean = LocalDate.ofEpochDay(tag).dayOfWeek == DayOfWeek.FRIDAY

    // Tagesnummer eines Zeitpunkts in Ortszeit.
    fun tag(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    // Mitternacht eines Tags in Millisekunden (Ortszeit), fuer die Anzeige des Datums.
    fun beginn(tag: Long, zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.ofEpochDay(tag).atStartOfDay(zone).toInstant().toEpochMilli()
}
