package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.market.Bafin
import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.InsiderWelt
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.SecInsider
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Handelskalender: je Tag die geschlossenen Positionen mit realisiertem Ergebnis und die Dividenden.
// Geschlossen wird eine Position durch jeden Verkauf, auch Teilverkauf, die Rueckzahlung eines Knock-outs und den
// Verfall eines Optionsscheins. Das Ergebnis steht in der Buchung, vor Gebuehren und Steuern. Einsatz ist der
// Einstandswert der geschlossenen Stuecke, also Erloes minus Ergebnis. Tagesergebnis in Prozent: Summe der Ergebnisse
// durch Summe der Einsaetze. Knock-outs zaehlen am Tag des Knock-outs, nicht an der Rueckzahlung zwei Tage spaeter.
object TradeCalendar {

    // einsatz: Einstandswert der geschlossenen Stuecke.
    data class Trade(val tx: Transaction, val ts: Long, val einsatz: Double) {
        val realisiert: Double get() = tx.realized
        val prozent: Double get() = if (einsatz > 0) realisiert / einsatz * 100.0 else 0.0
    }

    // Erhalten: aus der Buchung, netto nach Steuern. Erwartet: nach dem Ausschuettungsplan der letzten zwoelf Monate,
    // am Zahltag und mit der geschaetzten Steuer wie im Dividendenkalender (Dividendenerwartung).
    data class Dividende(
        val key: String,
        val name: String,
        val symbol: String,
        val ts: Long,
        val brutto: Double,
        val steuer: Double,
        val stueck: Double,
        val jeStueck: Double,
        val erwartet: Boolean,
    ) {
        val netto: Double get() = brutto - steuer
    }

    // Insider-Geschaeft einer gehaltenen Aktie am Tag des Geschaefts (Kalendertag aus der Meldung, ohne Zeitzone).
    // quelle: US (SEC), EU (Register weiterer Laender), DE (BaFin); rolle: Titel im Wortlaut, sonst rolleCode der Quelle.
    data class Insider(
        val symbol: String,
        val name: String,
        val datum: LocalDate,
        val kauf: Boolean,
        val wert: Double,
        val waehrung: String,
        val person: String,
        val rolle: String,
        val rolleCode: String,
        val quelle: String,
    )

    // Gehaltene Aktie fuer die Zuordnung der Insider-Geschaefte; us: US-Aktie (SEC-Meldungen nach Kuerzel),
    // firmen: Namen der Firma fuer Register, die nur den Namen nennen.
    data class Aktie(val symbol: String, val name: String, val isin: String, val us: Boolean, val firmen: List<String> = listOf(name))

    // Arten der Markierungen in der Reihenfolge ihrer Wichtigkeit: auf der Kachel stehen hoechstens drei, die ersten.
    // FEIERTAG hat keine Markierung, der Tag wird blass. schluessel: Name in den Einstellungen.
    enum class Art(val schluessel: String) {
        RUECKBLICK("rueckblick"), ZAHLEN("zahlen"), INSIDER("insider"), KONGRESS("kongress"), VERFALL("verfall"),
        EXDIV("exdiv"), DIV_ERHALTEN("div_erhalten"), DIV_ERWARTET("div_erwartet"), ANALYSTEN("analysten"),
        KAUF("kauf"), MAKRO("makro"), FEIERTAG("feiertag");

        companion object {
            fun ausSchluessel(s: String): Art? = entries.firstOrNull { it.schluessel == s }

            // Ausgeblendete Arten aus dem Einstellungstext (Schluessel mit Komma getrennt), Unbekanntes entfaellt.
            fun lesen(text: String): Set<Art> = text.split(',').mapNotNull { ausSchluessel(it.trim()) }.toSet()

            // Einstellungstext nach Aus- oder Einschalten einer Art.
            fun schalten(text: String, art: Art, an: Boolean): String {
                val aus = lesen(text).let { if (an) it - art else it + art }
                return entries.filter { it in aus }.joinToString(",") { it.schluessel }
            }
        }
    }

    // Quartalszahlen eines gehaltenen Werts: Termin, Erwartung (Gewinn je Aktie) und, wenn vorbei und geliefert, das
    // Ergebnis. geschaetzt: Tag nur geschaetzt.
    data class Zahlen(
        val symbol: String, val name: String, val datum: LocalDate, val geschaetzt: Boolean,
        val epsErwartet: Double? = null, val eps: Double? = null, val abweichung: Double? = null, val quartal: String? = null,
    )

    // Ex-Dividende; erwartet: noch nicht eingetreten, aus dem Dividendenplan.
    data class ExDiv(val symbol: String, val name: String, val datum: LocalDate, val erwartet: Boolean)

    // Kauf oder Verkauf eines Kongressmitglieds bei einer gehaltenen Aktie am Tag des Geschaefts; haus: Repraesentantenhaus, sonst Senat.
    data class Kongress(
        val symbol: String, val name: String, val datum: LocalDate, val kauf: Boolean, val betrag: Double, val person: String, val haus: Boolean,
    )

    // Hoch- oder Abstufung eines Analystenhauses bei einer gehaltenen Aktie.
    data class Stufung(
        val symbol: String, val name: String, val datum: LocalDate, val hoch: Boolean, val haus: String, val von: String, val nach: String,
    )

    // Verfallstag eines eigenen Optionsscheins oder einer Option; verkauft: verkaufte Option. basis: Symbol des Basiswerts.
    data class Verfall(
        val basis: String, val produkt: String, val kennung: String, val datum: LocalDate, val verkauft: Boolean, val stueck: Double,
    )

    enum class MakroArt { FED, EZB, CPI }

    // Grosser Wirtschaftstermin: Zinsentscheid Fed oder EZB, US-Inflation.
    data class Makro(val datum: LocalDate, val art: MakroArt, val termin: NasdaqKalender.Wirtschaftstermin)

    // Alle weiteren Markierungen eines Tages. kaeufe: Kaufbuchungen und Sparplan-Ausfuehrungen.
    data class Zusatz(
        val zahlen: List<Zahlen> = emptyList(),
        val exDiv: List<ExDiv> = emptyList(),
        val kongress: List<Kongress> = emptyList(),
        val stufungen: List<Stufung> = emptyList(),
        val verfaelle: List<Verfall> = emptyList(),
        val makro: List<Makro> = emptyList(),
        val kaeufe: List<Transaction> = emptyList(),
    )

    data class Tag(
        val datum: LocalDate,
        val trades: List<Trade>,
        val dividenden: List<Dividende>,
        val insider: List<Insider> = emptyList(),
        val zusatz: Zusatz = Zusatz(),
    ) {
        // Arten, die an diesem Tag etwas haben, in der Reihenfolge von Art (ohne FEIERTAG).
        fun arten(rueckblick: Boolean): List<Art> = Art.entries.filter { a ->
            when (a) {
                Art.RUECKBLICK -> rueckblick
                Art.ZAHLEN -> zusatz.zahlen.isNotEmpty()
                Art.INSIDER -> insider.isNotEmpty()
                Art.KONGRESS -> zusatz.kongress.isNotEmpty()
                Art.VERFALL -> zusatz.verfaelle.isNotEmpty()
                Art.EXDIV -> zusatz.exDiv.isNotEmpty()
                Art.DIV_ERHALTEN -> dividenden.any { !it.erwartet }
                Art.DIV_ERWARTET -> dividenden.any { it.erwartet }
                Art.ANALYSTEN -> zusatz.stufungen.isNotEmpty()
                Art.KAUF -> zusatz.kaeufe.isNotEmpty()
                Art.MAKRO -> zusatz.makro.isNotEmpty()
                Art.FEIERTAG -> false
            }
        }

        // Richtungen einer Art: (nach oben, nach unten); fuer Arten ohne Richtung (false, false).
        fun richtung(art: Art): Pair<Boolean, Boolean> = when (art) {
            Art.INSIDER -> insider.any { it.kauf } to insider.any { !it.kauf }
            Art.KONGRESS -> zusatz.kongress.any { it.kauf } to zusatz.kongress.any { !it.kauf }
            Art.ANALYSTEN -> zusatz.stufungen.any { it.hoch } to zusatz.stufungen.any { !it.hoch }
            else -> false to false
        }

        val insiderKauf: Boolean get() = insider.any { it.kauf }
        val insiderVerkauf: Boolean get() = insider.any { !it.kauf }
        val realisiert: Double get() = trades.sumOf { it.realisiert }
        val einsatz: Double get() = trades.sumOf { it.einsatz }
        val prozent: Double get() = if (einsatz > 0) realisiert / einsatz * 100.0 else 0.0
        val gebuehren: Double get() = trades.sumOf { it.tx.fee }
        val steuern: Double get() = trades.sumOf { it.tx.tax }
        val dividendenErhalten: Double get() = dividenden.filter { !it.erwartet }.sumOf { it.netto }
        val dividendenErwartet: Double get() = dividenden.filter { it.erwartet }.sumOf { it.netto }
        val hatTrades: Boolean get() = trades.isNotEmpty()
    }

    data class Monat(
        val monat: YearMonth,
        val realisiert: Double,
        val trades: Int,
        val gewinne: Int,
        val verluste: Int,
        val dividendenErhalten: Double,
        val dividendenErwartet: Double,
    )

    // Auch Rueckkauf und Verfall einer verkauften Option (0.77); ihr Verkauf zur Eroeffnung schliesst nichts.
    fun schliesst(tx: Transaction): Boolean = Verkaufsbilanz.zaehlt(tx)

    // Beim Knock-out zaehlt der Knock-out selbst, sonst die Buchung.
    fun endeVon(tx: Transaction): Long = if (tx.closedAt > 0L) tx.closedAt else tx.ts

    // Bei einer verkauften Option ist der Einsatz die erhaltene Praemie: Rueckkaufwert plus Ergebnis.
    fun trade(tx: Transaction): Trade {
        val wert = tx.qty * tx.price
        return Trade(tx, endeVon(tx), (if (tx.verkaufteOption) wert + tx.realized else wert - tx.realized).coerceAtLeast(0.0))
    }

    // In der Buchung steht der Betrag nach Steuern, die Steuer daneben.
    fun dividende(tx: Transaction): Dividende =
        Dividende(tx.posKey, tx.title, tx.symbol, tx.ts, tx.amount + tx.tax, tx.tax, tx.qty, tx.price, erwartet = false)

    // erwartet: kommende Dividenden aus dem Dividendenkalender, sie stehen am erwarteten Zahltag.
    fun tage(
        transactions: List<Transaction>, erwartet: List<Dividende>, zone: ZoneId, insider: List<Insider> = emptyList(),
        zusatz: Zusatz = Zusatz(),
    ): Map<LocalDate, Tag> {
        fun tag(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val trades = transactions.filter { schliesst(it) }.map { trade(it) }.groupBy { tag(it.ts) }
        val dividenden = (transactions.filter { it.type == TxType.DIVIDEND }.map { dividende(it) } + erwartet).groupBy { tag(it.ts) }
        val insiderTage = insider.groupBy { it.datum }
        val kaeufe = zusatz.kaeufe.groupBy { tag(it.ts) }
        val zahlen = zusatz.zahlen.groupBy { it.datum }
        val exDiv = zusatz.exDiv.groupBy { it.datum }
        val kongress = zusatz.kongress.groupBy { it.datum }
        val stufungen = zusatz.stufungen.groupBy { it.datum }
        val verfaelle = zusatz.verfaelle.groupBy { it.datum }
        val makro = zusatz.makro.groupBy { it.datum }
        val alle = trades.keys + dividenden.keys + insiderTage.keys + kaeufe.keys + zahlen.keys + exDiv.keys + kongress.keys +
            stufungen.keys + verfaelle.keys + makro.keys
        return alle.associateWith { d ->
            Tag(
                d, trades[d].orEmpty().sortedBy { it.ts }, dividenden[d].orEmpty().sortedBy { it.ts }, insiderTage[d].orEmpty(),
                Zusatz(
                    zahlen[d].orEmpty(), exDiv[d].orEmpty(), kongress[d].orEmpty(), stufungen[d].orEmpty(), verfaelle[d].orEmpty(),
                    makro[d].orEmpty(), kaeufe[d].orEmpty().sortedBy { it.ts },
                ),
            )
        }
    }

    // Makrotermine eines Monats nachtraeglich in die Tage legen (sie kommen erst beim Blaettern an).
    fun mitMakro(tage: Map<LocalDate, Tag>, makro: List<Makro>): Map<LocalDate, Tag> {
        if (makro.isEmpty()) return tage
        val neu = HashMap(tage)
        for ((d, l) in makro.groupBy { it.datum }) {
            val t = neu[d] ?: Tag(d, emptyList(), emptyList())
            neu[d] = t.copy(zusatz = t.zusatz.copy(makro = l))
        }
        return neu
    }

    // Hoechstens drei Markierungen einer Kachel: die wichtigsten der Arten, die nicht ausgeblendet sind.
    fun marken(tag: Tag?, rueckblick: Boolean, aus: Set<Art>, hoechstens: Int = 3): List<Art> =
        (tag?.arten(rueckblick) ?: if (rueckblick) listOf(Art.RUECKBLICK) else emptyList()).filter { it !in aus }.take(hoechstens)

    // Kaufbuchung oder Sparplan-Ausfuehrung: kein Verkauf, kein Rueckkauf einer verkauften Option, keine Uebernahme.
    fun istKauf(tx: Transaction): Boolean =
        tx.type == TxType.BUY && !schliesst(tx) && tx.via != "IMPORT" && tx.tag != "TRANSFER_IN" && tx.qty > 0

    // Boersenfeiertag unter der Woche: an allen Boersen der gehaltenen Werte geschlossen (Laender ohne Regeln zaehlen nicht).
    fun feiertag(datum: LocalDate, laender: Set<String>): Boolean {
        val l = laender.filter { it.isNotBlank() }
        if (l.isEmpty() || datum.dayOfWeek == java.time.DayOfWeek.SATURDAY || datum.dayOfWeek == java.time.DayOfWeek.SUNDAY) return false
        return l.all { Boersenzeiten.feiertag(it, datum) }
    }

    // Quartalszahlen der gehaltenen Werte: Termine (Nasdaq, Schluessel-Anbieter, Yahoo) und fruehere Ergebnisse.
    // gehalten: Symbol zu Name. Termine anderer Werte entfallen; je Wert und Tag nur einer, das Ergebnis geht vor.
    fun zahlen(
        termine: List<Quartalszahlen.Termin>,
        ueberraschungen: Map<String, List<NasdaqKalender.Ueberraschung>>,
        gehalten: Map<String, String>,
    ): List<Zahlen> {
        val aus = ArrayList<Zahlen>()
        for ((symbol, liste) in ueberraschungen) {
            val name = gehalten[symbol] ?: continue
            liste.forEach { u ->
                if (u.eps != null) aus += Zahlen(symbol, name, LocalDate.ofEpochDay(u.tag), false, u.erwartet, u.eps, u.prozent, u.quartal)
            }
        }
        termine.filter { it.symbol in gehalten }.forEach { t ->
            val d = LocalDate.ofEpochDay(t.tag)
            if (aus.none { it.symbol == t.symbol && it.datum == d }) {
                aus += Zahlen(t.symbol, gehalten.getValue(t.symbol), d, t.geschaetzt, t.epsErwartet, t.eps, t.abweichung, t.quartal)
            }
        }
        return aus.sortedWith(compareBy({ it.datum }, { it.symbol }))
    }

    private fun utcTag(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()

    // Ex-Tage: zu jeder erhaltenen Dividende der letzte Ex-Tag vor der Zahlung (hoechstens 60 Tage davor), dazu die
    // erwarteten Ex-Tage aus dem Dividendenplan. historie: Ex-Tage (ms) je Depotschluessel.
    fun exTage(
        erhalten: List<Dividende>,
        historie: Map<String, List<Long>>,
        erwartet: List<Triple<String, String, Long>>,
    ): List<ExDiv> {
        val aus = ArrayList<ExDiv>()
        for (d in erhalten.filter { !it.erwartet }) {
            val ex = historie[d.key].orEmpty().filter { it <= d.ts && d.ts - it <= 60L * 86_400_000L }.maxOrNull() ?: continue
            aus += ExDiv(d.symbol.ifBlank { d.key }, d.name, utcTag(ex), erwartet = false)
        }
        erwartet.forEach { (symbol, name, ex) -> if (ex > 0) aus += ExDiv(symbol, name, utcTag(ex), erwartet = true) }
        return aus.distinctBy { Triple(it.symbol, it.datum, it.erwartet) }.sortedWith(compareBy({ it.datum }, { it.symbol }))
    }

    // Kauf und Verkauf von Kongressmitgliedern bei gehaltenen Aktien, am Tag des Geschaefts. gehalten: Symbol zu Name.
    fun kongress(handel: List<KongressAuswertung.Handel>, gehalten: Map<String, String>): List<Kongress> =
        handel.filter { it.symbol in gehalten }.map {
            Kongress(it.symbol, gehalten.getValue(it.symbol), utcTag(it.datum), it.kauf, it.betrag, it.name, it.gruppe == 'H')
        }.sortedWith(compareBy({ it.datum }, { it.symbol }, { it.person }))

    // Hoch- und Abstufungen eines Werts; Bestaetigungen und neutrale Erstbewertungen zaehlen nicht (wie Signale.stufungen).
    fun stufungen(symbol: String, name: String, urteile: List<Analystenbilanz.Urteil>, zone: ZoneId): List<Stufung> =
        urteile.mapNotNull { u ->
            val r = Analystenbilanz.richtung(u) ?: return@mapNotNull null
            Stufung(symbol, name, Instant.ofEpochMilli(u.zeit).atZone(zone).toLocalDate(), r > 0, u.haus, u.von, u.nach)
        }.sortedWith(compareBy({ it.datum }, { it.haus }))

    // Verfall eigener Optionsscheine und Optionen (auch verkaufter), die noch im Depot liegen und nicht ausgeknockt sind.
    fun verfaelle(positionen: List<Position>, zone: ZoneId): List<Verfall> =
        positionen.mapNotNull { p ->
            val d = p.derivative ?: return@mapNotNull null
            if (p.isKnockedOut || d.expiry <= 0L || p.qty == 0.0) return@mapNotNull null
            Verfall(d.underlying, p.name, d.id, Instant.ofEpochMilli(d.expiry).atZone(zone).toLocalDate(), p.verkauft, kotlin.math.abs(p.qty))
        }.sortedWith(compareBy({ it.datum }, { it.produkt }))

    // Grosse Wirtschaftstermine: Zinsentscheid der Fed und der EZB, US-Inflation (CPI); sonst null.
    fun makroArt(t: NasdaqKalender.Wirtschaftstermin): MakroArt? {
        val n = t.name.lowercase()
        if ("minutes" in n || "speech" in n || "speaks" in n) return null
        return when {
            t.land == "United States" && "interest rate decision" in n -> MakroArt.FED
            t.land == "Euro Zone" && ("interest rate decision" in n || "main refinancing" in n || "deposit facility" in n) -> MakroArt.EZB
            t.land == "United States" && (Regex("\\bcpi\\b").containsMatchIn(n) || "consumer price index" in n) -> MakroArt.CPI
            else -> null
        }
    }

    // Makrotermine eines Tages aus der Liste des Wirtschaftskalenders; je Art und Name nur einmal.
    fun makro(termine: List<NasdaqKalender.Wirtschaftstermin>): List<Makro> =
        termine.mapNotNull { t -> makroArt(t)?.let { Makro(LocalDate.ofEpochDay(t.tag), it, t) } }
            .distinctBy { Triple(it.datum, it.art, it.termin.name) }
            .sortedWith(compareBy({ it.datum }, { it.art }, { it.termin.minute ?: 0 }))

    // Insider-Geschaefte der gehaltenen Aktien, so gezaehlt wie im Insider-Abschnitt der Wertpapierseite: SEC nach Kuerzel
    // (nur US-Aktien), BaFin nach ISIN oder Kuerzel, Register weiterer Laender nach ISIN, Kennung oder Firmenname.
    // Nur echte Kaeufe und Verkaeufe. laender: gespeicherte Staende je Land. Andere Aktien kommen nie vor.
    fun insiderFuerDepot(
        gehalten: List<Aktie>,
        sec: List<SecInsider.Geschaeft>,
        laender: Map<String, List<EuInsider.Geschaeft>>,
        bafin: List<Bafin.Geschaeft>,
    ): List<Insider> {
        val aus = ArrayList<Insider>()
        for (a in gehalten.distinctBy { it.symbol }) {
            if (a.us) sec.filter { it.k.equals(a.symbol, true) }.forEach { g ->
                datum(g.d)?.let { aus += Insider(a.symbol, a.name, it, g.kauf, g.wert, "USD", g.p, g.t, g.r, "US") }
            }
            bafin.filter { (a.isin.isNotBlank() && it.isin == a.isin) || it.k == a.symbol }.filter { it.kauf || it.verkauf }.forEach { g ->
                datum(g.d)?.let { aus += Insider(a.symbol, a.name, it, g.kauf, g.vol ?: 0.0, g.w.ifBlank { "EUR" }, g.p, g.r, "", "DE") }
            }
            euWaehlen(a, laender).filter { it.kauf || it.verkauf }.forEach { g ->
                datum(g.d)?.let { aus += Insider(a.symbol, a.name, it, g.kauf, g.wert, g.w, g.p, g.t, g.r, "EU") }
            }
        }
        return aus.sortedWith(compareBy({ it.datum }, { it.symbol }, { it.person }))
    }

    // Register einer Aktie wie auf der Wertpapierseite: Boerse mit eigenem Land (Indien, Hongkong ...) ueber Kennung, ISIN
    // oder Namen; sonst das Land mit den meisten Meldungen zu dieser ISIN.
    fun euWaehlen(a: Aktie, laender: Map<String, List<EuInsider.Geschaeft>>): List<EuInsider.Geschaeft> {
        val ort = InsiderWelt.ort(a.symbol, a.isin)
        if (ort != null) {
            val (land, code) = ort
            return laender[land].orEmpty().filter { g -> a.firmen.ifEmpty { listOf("") }.any { InsiderWelt.passt(g, land, code, a.isin, it) } }
        }
        if (a.isin.isBlank()) return emptyList()
        val bestes = laender.values.maxByOrNull { l -> l.count { it.isin == a.isin } } ?: return emptyList()
        return bestes.filter { it.isin == a.isin }
    }

    private fun datum(iso: String): LocalDate? = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

    fun monat(tage: Map<LocalDate, Tag>, ym: YearMonth): Monat {
        val drin = tage.values.filter { YearMonth.from(it.datum) == ym }
        val alle = drin.flatMap { it.trades }
        return Monat(
            monat = ym,
            realisiert = alle.sumOf { it.realisiert },
            trades = alle.size,
            gewinne = alle.count { Verkaufsbilanz.gewinn(it.realisiert) },
            verluste = alle.count { Verkaufsbilanz.verlust(it.realisiert) },
            dividendenErhalten = drin.sumOf { it.dividendenErhalten },
            dividendenErwartet = drin.sumOf { it.dividendenErwartet },
        )
    }

    // Staerke der Faerbung, 0 bis 1, im Verhaeltnis zum groessten Betrag des Monats.
    // Mindestens 0,25, damit jeder Handelstag sichtbar bleibt.
    fun staerke(realisiert: Double, groesster: Double): Float {
        if (!(groesster > 0)) return 0.25f
        val anteil = (kotlin.math.abs(realisiert) / groesster).coerceIn(0.0, 1.0)
        return (0.25 + 0.75 * kotlin.math.sqrt(anteil)).toFloat()
    }

    // Vom ersten Monat mit Buchungen bis zum letzten mit erwarteter Dividende.
    fun spanne(transactions: List<Transaction>, erwartet: List<Dividende>, heute: YearMonth, zone: ZoneId): ClosedRange<YearMonth> {
        val erster = transactions.minOfOrNull { it.ts }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) } ?: heute
        val letzter = erwartet.maxOfOrNull { it.ts }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) } ?: heute
        return minOf(erster, heute)..maxOf(letzter, heute)
    }

    // Tag der ersten Buchung (auch eines Imports mit Buchungen aus der Vergangenheit); null ohne Buchungen.
    fun ersterTag(transactions: List<Transaction>, zone: ZoneId): LocalDate? =
        transactions.filter { it.ts > 0 }.minOfOrNull { it.ts }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    // Fuer diesen Tag laesst sich ein Rueckblick erstellen: Werktag von der ersten Buchung bis gestern.
    fun rueckblickMoeglich(tag: LocalDate, erster: LocalDate?, heute: LocalDate): Boolean =
        erster != null && !tag.isBefore(erster) && tag.isBefore(heute) &&
            tag.dayOfWeek != java.time.DayOfWeek.SATURDAY && tag.dayOfWeek != java.time.DayOfWeek.SUNDAY
}
