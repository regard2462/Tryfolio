package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

// Staatsgeld einer Firma: was sie vom Staat als Auftraggeber (oeffentliche Auftraege) und als Foerderer (Zuschuesse, Zahlungen,
// Kredite) bekommt. Reines Lesen und Rechnen, ohne Netz; die Abrufe stehen in StaatsgeldAbruf.
//   USA:  USAspending.gov (gemeinfrei), Verpflichtungen je Monat ueber den Empfaenger der Muttergesellschaft samt Toechtern.
//   EU:   TED (Zuschlagsbekanntmachungen mit Gewinner), nur Zuschlaege, deren Gewinner alle zur Firma gehoeren.
//   UK:   Contracts Finder (Open Government Licence), nur Zuschlaege an genau einen Lieferanten, der zur Firma gehoert.
//   EU-Haushalt: Financial Transparency System der EU-Kommission, mitgeliefert (firmen/staatsgeld-eu.json).
// Zuordnung wie bei den Emissionen (FirmaName): lieber kein Treffer als ein falscher.
object Staatsgeld {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonElement?.zahl(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.doubleOrNull ?: it.content.replace(",", "").toDoubleOrNull() }

    // USA, TED, UK; EU ist der Haushalt der EU-Kommission
    enum class Quelle { USA, TED, UK, EU }

    // ZUSCHUSS umfasst Zuschuesse, Direktzahlungen und sonstige Hilfen; KREDIT zaehlt nicht in die Summe
    enum class Art { AUFTRAG, ZUSCHUSS, KREDIT }

    // Betrag in der Waehrung der Quelle; monat 1 bis 12, 0 bei Jahressummen; kaeufer: Auftraggeber, wo die Quelle ihn nennt
    class Posten(val quelle: Quelle, val art: Art, val jahr: Int, val monat: Int, val betrag: Double, val waehrung: String, val kaeufer: String = "")

    // Namen der Gewinner und Empfaenger kommen mit HTML-Zeichen aus den Quellen
    fun entschluesseln(s: String): String = s.replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").trim()

    private fun anfuehrung(s: String): String = Json.encodeToString(kotlinx.serialization.serializer<String>(), s)

    // Rechtsformen aus Europa am Ende eines Namens, die FirmaName nicht kennt (GmbH, SRL, SAS, BV, Sp. z o.o. und aehnliche)
    private val EU_FORMEN = setOf("gmbh", "mbh", "kg", "kgaa", "co", "srl", "sas", "sarl", "bv", "sl", "slu", "oy", "aps", "llp", "ug", "ohg", "sp", "z", "o", "oo", "zoo")

    // Gehoert ein Name aus einer Bekanntmachung zur Firma? Wie FirmaName.passt, nach Abtragen europaeischer Rechtsformen.
    fun passt(name: String, schluessel: List<String>): Boolean {
        val w = name.lowercase().replace(".", "").split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.toMutableList()
        while (w.size > 1 && (w.last() in EU_FORMEN || w.last() == "&")) w.removeAt(w.size - 1)
        return FirmaName.passt(w.joinToString(" "), schluessel)
    }

    // Suchwort einer Firma fuer die Quellen: Woerter des Namens ohne Rechtsform, nur Buchstaben und Ziffern
    fun suchwort(name: String): String =
        FirmaName.woerter(name).joinToString(" ") { it.filter { c -> c.isLetterOrDigit() } }.trim()

    // ------------------------------------------------------------ USA: USAspending

    const val USA = "https://api.usaspending.gov/api/v2"

    // Auftraege (A bis D), Zuschuesse (02 bis 05), Direktzahlungen (06, 10), Kredite (07, 08), sonstige Hilfen (09, 11)
    private val USA_ALLE = listOf("A", "B", "C", "D", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11")
    private val USA_HILFEN = listOf("02", "03", "04", "05", "06", "09", "10", "11")
    private fun codes(l: List<String>) = l.joinToString(",") { "\"$it\"" }

    fun usaEmpfaengerKoerper(suchwort: String) =
        """{"keyword":${anfuehrung(suchwort)},"limit":50,"page":1,"order":"desc","sort":"amount"}"""

    // ebene: P Muttergesellschaft, C Tochter, R Empfaenger ohne Mutter
    class Empfaenger(val id: String, val name: String, val ebene: String, val betrag: Double)

    fun usaEmpfaengerLesen(text: String): List<Empfaenger> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Empfaenger(
                o["id"].text() ?: return@mapNotNull null, o["name"].text() ?: return@mapNotNull null,
                o["recipient_level"].text().orEmpty(), o["amount"].zahl() ?: 0.0,
            )
        }
    }.getOrDefault(emptyList())

    // Der Empfaenger, dessen Name zur Firma passt (Muttergesellschaft oder Empfaenger ohne Mutter, nie eine Tochter unter
    // eigenem Namen). Mehrere Treffer: der mit dem hoechsten Betrag. Die Kennung erfasst bei der Mutter alle Toechter.
    fun usaEmpfaengerWaehlen(liste: List<Empfaenger>, schluessel: List<String>): Empfaenger? =
        liste.filter { it.ebene == "P" || it.ebene == "R" }
            .filter { FirmaName.passt(it.name, schluessel) }
            .maxByOrNull { it.betrag }

    private fun usaFilter(id: String, codes: List<String>, von: String, bis: String) =
        """{"recipient_id":${anfuehrung(id)},"award_type_codes":[${codes(codes)}],"time_period":[{"start_date":"$von","end_date":"$bis"}]}"""

    fun usaVerlaufKoerper(id: String, von: String, bis: String) =
        """{"group":"month","filters":${usaFilter(id, USA_ALLE, von, bis)}}"""

    // Kalendermonat zu Haushaltsjahr und Haushaltsmonat der US-Regierung (Haushaltsjahr beginnt im Oktober): 1 = Oktober
    internal fun kalender(haushaltsjahr: Int, haushaltsmonat: Int): Pair<Int, Int> {
        val monat = (haushaltsmonat + 8) % 12 + 1
        val jahr = if (haushaltsmonat <= 3) haushaltsjahr - 1 else haushaltsjahr
        return jahr to monat
    }

    // Je Monat Auftraege, Zuschuesse (mit Direktzahlungen und sonstigen Hilfen) und Kredite in Dollar
    fun usaVerlaufLesen(text: String): List<Posten> = runCatching {
        val aus = ArrayList<Posten>()
        for (e in json.parseToJsonElement(text).jsonObject["results"].arr().orEmpty()) {
            val o = e.obj() ?: continue
            val z = o["time_period"].obj() ?: continue
            val fj = z["fiscal_year"].text()?.toIntOrNull() ?: continue
            val hm = z["month"].text()?.toIntOrNull() ?: continue
            if (hm !in 1..12) continue
            val (j, m) = kalender(fj, hm)
            fun add(art: Art, v: Double) { if (v != 0.0) aus += Posten(Quelle.USA, art, j, m, v, "USD") }
            add(Art.AUFTRAG, o["Contract_Obligations"].zahl() ?: 0.0)
            add(Art.ZUSCHUSS, (o["Grant_Obligations"].zahl() ?: 0.0) + (o["Direct_Obligations"].zahl() ?: 0.0) + (o["Other_Obligations"].zahl() ?: 0.0))
            add(Art.KREDIT, o["Loan_Obligations"].zahl() ?: 0.0)
        }
        aus
    }.getOrDefault(emptyList())

    fun usaAuftraggeberKoerper(id: String, von: String, bis: String) =
        """{"filters":${usaFilter(id, listOf("A", "B", "C", "D", "02", "03", "04", "05", "06", "09", "10", "11"), von, bis)},"limit":6,"page":1}"""

    fun usaProgrammeKoerper(id: String, von: String, bis: String) =
        """{"filters":${usaFilter(id, USA_HILFEN, von, bis)},"limit":6,"page":1}"""

    // Name und Betrag je Zeile einer Kategorie (Behoerde, Programm); Beitraege ohne Betrag entfallen
    fun usaKategorieLesen(text: String): List<Pair<String, Double>> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val n = o["name"].text()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val b = o["amount"].zahl()?.takeIf { it > 0 } ?: return@mapNotNull null
            n to b
        }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ EU: TED

    const val TED = "https://api.ted.europa.eu/v3/notices/search"
    const val TED_SEITE = 250
    private val TED_FELDER = listOf("publication-date", "winner-name", "total-value", "tender-value-cur", "buyer-name")

    // Zuschlagsbekanntmachungen ab einem Datum, in denen ein Gewinnername mit dem Suchwort beginnt, neueste zuerst
    fun tedKoerper(suchwort: String, ab: LocalDate, seite: Int): String {
        val tag = ab.toString().replace("-", "")
        val q = "winner-name=\"${suchwort.replace("\"", "")}*\" AND notice-type=can-standard AND publication-date>=$tag"
        return """{"query":${anfuehrung(q)},"fields":[${TED_FELDER.joinToString(",") { "\"$it\"" }}],"limit":$TED_SEITE,"page":$seite,"scope":"ALL","paginationMode":"PAGE_NUMBER","sortField":"publication-date","sortOrder":"DESC"}"""
    }

    class Zuschlag(val nummer: String, val datum: String, val gewinner: List<String>, val wert: Double?, val waehrung: String?, val kaeufer: String)

    // Alle Namen eines Felds, das je Sprache eine Liste fuehrt; ohne Doppelte
    private fun sprachNamen(e: JsonElement?): List<String> = e.obj()?.values.orEmpty().flatMap { v -> v.arr().orEmpty().mapNotNull { it.text() } }.map { entschluesseln(it) }.filter { it.isNotEmpty() }.distinct()

    class TedSeite(val zuschlaege: List<Zuschlag>, val gesamt: Int)

    fun tedLesen(text: String): TedSeite? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val liste = o["notices"].arr() ?: return null
        val aus = liste.mapNotNull { e ->
            val n = e.obj() ?: return@mapNotNull null
            Zuschlag(
                n["publication-number"].text() ?: return@mapNotNull null,
                n["publication-date"].text().orEmpty().take(10),
                sprachNamen(n["winner-name"]),
                n["total-value"].zahl(),
                n["tender-value-cur"].arr()?.firstOrNull().text(),
                sprachNamen(n["buyer-name"]).firstOrNull().orEmpty(),
            )
        }
        TedSeite(aus, o["totalNoticeCount"].zahl()?.toInt() ?: aus.size)
    }.getOrNull()

    private fun jahrMonat(datum: String): Pair<Int, Int>? {
        if (datum.length < 7) return null
        val j = datum.substring(0, 4).toIntOrNull() ?: return null
        val m = datum.substring(5, 7).toIntOrNull() ?: return null
        return if (m in 1..12) j to m else null
    }

    // Zuschlaege, deren Gewinner alle zur Firma gehoeren (sonst ist der Betrag nicht der Firma zuzuordnen), mit Betrag und
    // Waehrung. Nur ab dem Jahr abJahr (bei abgeschnittener Liste zaehlen nur ganze Jahre).
    fun tedPosten(zuschlaege: List<Zuschlag>, schluessel: List<String>, abJahr: Int = 0): List<Posten> =
        zuschlaege.mapNotNull { z ->
            val w = z.wert?.takeIf { it > 0 } ?: return@mapNotNull null
            val cur = z.waehrung?.takeIf { it.length == 3 } ?: return@mapNotNull null
            if (z.gewinner.isEmpty() || !z.gewinner.all { passt(it, schluessel) }) return@mapNotNull null
            val (j, m) = jahrMonat(z.datum) ?: return@mapNotNull null
            if (j < abJahr) return@mapNotNull null
            Posten(Quelle.TED, Art.AUFTRAG, j, m, w, cur, z.kaeufer)
        }

    fun tedAdresse(nummer: String) = "https://ted.europa.eu/en/notice/-/detail/$nummer"

    // ------------------------------------------------------------ UK: Contracts Finder

    const val UK = "https://www.contractsfinder.service.gov.uk/api/rest/2/search_notices/json"

    fun ukKoerper(suchwort: String, ab: LocalDate) =
        """{"searchCriteria":{"keyword":${anfuehrung(suchwort)},"types":["Contract"],"statuses":["Awarded"],"publishedFrom":"$ab"},"size":1000}"""

    class UkZuschlag(val id: String, val kennung: String, val kaeufer: String, val lieferant: String, val wert: Double, val datum: String)

    fun ukLesen(text: String): List<UkZuschlag> = runCatching {
        json.parseToJsonElement(text).jsonObject["noticeList"].arr().orEmpty().mapNotNull { e ->
            val i = e.obj()?.get("item").obj() ?: return@mapNotNull null
            UkZuschlag(
                i["id"].text().orEmpty(), i["noticeIdentifier"].text() ?: return@mapNotNull null,
                entschluesseln(i["organisationName"].text().orEmpty()), entschluesseln(i["awardedSupplier"].text() ?: return@mapNotNull null),
                i["awardedValue"].zahl() ?: return@mapNotNull null,
                (i["awardedDate"].text() ?: i["publishedDate"].text()).orEmpty().take(10),
            )
        }
    }.getOrDefault(emptyList())

    // Zuschlaege an genau einen Lieferanten, der zur Firma gehoert (mehrere Lieferanten stehen kommagetrennt, der Betrag
    // gilt dann fuer alle). Betraege unter 1.000 sind Platzhalter. Eine Bekanntmachung zaehlt einmal.
    fun ukPosten(liste: List<UkZuschlag>, schluessel: List<String>): List<Posten> =
        liste.distinctBy { it.kennung }.mapNotNull { z ->
            if (z.wert < 1000 || z.lieferant.contains(",")) return@mapNotNull null
            if (!passt(z.lieferant, schluessel)) return@mapNotNull null
            val (j, m) = jahrMonat(z.datum) ?: return@mapNotNull null
            Posten(Quelle.UK, Art.AUFTRAG, j, m, z.wert, "GBP", z.kaeufer)
        }

    fun ukAdresse(id: String) = "https://www.contractsfinder.service.gov.uk/notice/$id"

    // ------------------------------------------------------------ EU-Haushalt (mitgeliefert)

    const val DATEI_EU = "firmen/staatsgeld-eu.json"

    // f: Euro je Jahr ab jahr0; p: Programme mit den Euro des letzten Jahres (je Programm eine Zeile der Datei)
    class EuFirma(val name: String, val betraege: List<Double>, val programme: List<Pair<String, Double>>)

    // Schluessel "y:KUERZEL" oder zusammengeschriebener Firmenname
    class EuDatei(val stand: String, val jahr0: Int, val firmen: Map<String, EuFirma>)

    fun euLesen(text: String): EuDatei? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        if ((o["format"] as? JsonPrimitive)?.intOrNull != 1) return null
        val jahr0 = (o["jahr0"] as? JsonPrimitive)?.intOrNull ?: return null
        val jahre = (o["jahre"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: return null
        val aus = LinkedHashMap<String, EuFirma>()
        for (e in o["firmen"].arr() ?: return null) {
            val f = e.obj() ?: continue
            val name = f["n"].text().orEmpty()
            val b = f["f"].arr().orEmpty().map { it.zahl() ?: 0.0 }
            if (b.size != jahre) continue
            val p = f["p"].arr().orEmpty().mapNotNull { x ->
                val a = x.arr() ?: return@mapNotNull null
                val n = a.getOrNull(0).text() ?: return@mapNotNull null
                val v = a.getOrNull(1).zahl() ?: return@mapNotNull null
                n to v
            }
            val kuerzel = f["y"].arr().orEmpty().mapNotNull { it.text()?.takeIf { s -> s.isNotEmpty() } }
            val schluessel = if (kuerzel.isEmpty()) listOf(FirmaName.schluessel(name)) else kuerzel.map { "y:$it" }
            val neu = EuFirma(name, b, p)
            for (k in schluessel) if (k.isNotEmpty() && aus[k] == null) aus[k] = neu
        }
        if (aus.isEmpty()) null else EuDatei(o["stand"].text().orEmpty(), jahr0, aus)
    }.getOrNull()

    // Treffer ueber das Kuerzel im Katalog, sonst ueber den Firmennamen, nur wenn eindeutig
    fun euFuer(d: EuDatei?, namen: List<String>, symbol: String?): EuFirma? {
        d ?: return null
        if (symbol != null) d.firmen["y:$symbol"]?.let { return it }
        return FirmaName.schluessel(namen).mapNotNull { d.firmen[it] }.distinct().singleOrNull()
    }

    val europa: EuDatei? by lazy { runCatching { euLesen(Daten.text(DATEI_EU)) }.getOrNull() }

    // Jahressummen des EU-Haushalts als Posten in Euro (Jahre ohne Betrag entfallen)
    fun euPosten(d: EuDatei, f: EuFirma): List<Posten> =
        f.betraege.mapIndexedNotNull { i, v -> if (v > 0) Posten(Quelle.EU, Art.ZUSCHUSS, d.jahr0 + i, 0, v, "EUR") else null }

    // ------------------------------------------------------------ Auswertung

    // Eine Zeile je Quelle: Summe der letzten zwoelf Monate (EU-Haushalt: das letzte Jahr der Datei, jahr > 0)
    class Zeile(val quelle: Quelle, val auftraege: Double, val zuschuesse: Double, val kredite: Double, val jahr: Int = 0) {
        val summe: Double get() = auftraege + zuschuesse
    }

    class Stand(
        // Auftraege plus Zuschuesse der letzten zwoelf Monate (ohne EU-Haushalt, der nur Jahreswerte hat), in Dollar
        val zwoelf: Double,
        val auftraege: Double,
        val zuschuesse: Double,
        val kredite: Double,
        // zwoelf Monate davor; null, wenn davor nichts zu sehen war
        val vorher: Double?,
        // Kalenderjahre (ohne das laufende) mit Auftraegen und Zuschuessen aller Quellen, in Dollar
        val jahre: List<Pair<Int, Double>>,
        val zeilen: List<Zeile>,
        val auftraggeber: List<Pair<String, Double>>,
        val programme: List<Pair<String, Double>>,
    ) {
        val aenderung: Double? get() = vorher?.takeIf { it > 0 && zwoelf > 0 }?.let { (zwoelf / it - 1) * 100 }
    }

    // Monatsnummer, mit der Fenster verglichen werden
    private fun nr(jahr: Int, monat: Int) = jahr * 12 + monat - 1

    // usd: Dollar je Einheit der Waehrung, null wenn kein Kurs bekannt (die Quelle bleibt dann aus der Rechnung).
    // extraAuftraggeber und extraProgramme: Summen der letzten zwoelf Monate in Dollar aus zusaetzlichen Abrufen (USA).
    fun auswerten(
        posten: List<Posten>, heute: LocalDate, usd: (String) -> Double?,
        extraAuftraggeber: List<Pair<String, Double>> = emptyList(), extraProgramme: List<Pair<String, Double>> = emptyList(),
        programmeEu: List<Pair<String, Double>> = emptyList(),
    ): Stand? {
        class P(val q: Quelle, val a: Art, val j: Int, val m: Int, val d: Double, val k: String)
        val p = posten.mapNotNull { x -> usd(x.waehrung)?.let { k -> P(x.quelle, x.art, x.jahr, x.monat, x.betrag * k, x.kaeufer) } }
        if (p.isEmpty()) return null
        // Die letzten zwoelf vollstaendigen Monate und die zwoelf davor
        val jetzt = nr(heute.year, heute.monthValue)
        val ende = jetzt - 1
        fun fenster(von: Int, bis: Int) = p.filter { it.m > 0 && nr(it.j, it.m) in von..bis }
        val aktuell = fenster(ende - 11, ende)
        val davor = fenster(ende - 23, ende - 12)
        fun summe(l: List<P>, a: Art) = l.filter { it.a == a }.sumOf { it.d }
        fun ohneKredit(l: List<P>) = summe(l, Art.AUFTRAG) + summe(l, Art.ZUSCHUSS)
        val zwoelf = ohneKredit(aktuell)
        val vorher = ohneKredit(davor).takeIf { it > 0 }
        // Kalenderjahre: nur vollstaendige, hoechstens die letzten sechs, ab dem ersten mit Betrag
        val jahreRoh = p.filter { it.a != Art.KREDIT && it.j < heute.year && it.d > 0 }.groupBy { it.j }.mapValues { (_, l) -> l.sumOf { it.d } }
        val letztes = heute.year - 1
        val jahre = (letztes - 5..letztes).mapNotNull { j -> jahreRoh[j]?.let { j to it } }
            .let { l -> val erst = l.indexOfFirst { it.second > 0 }; if (erst < 0) emptyList() else l.drop(erst) }
        val zeilen = ArrayList<Zeile>()
        for (q in listOf(Quelle.USA, Quelle.TED, Quelle.UK)) {
            val a = aktuell.filter { it.q == q }
            val alle = p.any { it.q == q }
            if (alle) zeilen += Zeile(q, summe(a, Art.AUFTRAG), summe(a, Art.ZUSCHUSS), summe(a, Art.KREDIT))
        }
        val eu = p.filter { it.q == Quelle.EU }
        eu.maxOfOrNull { it.j }?.let { j -> zeilen += Zeile(Quelle.EU, 0.0, eu.filter { it.j == j }.sumOf { it.d }, 0.0, j) }
        // Auftraggeber: Behoerden der USA, Besteller aus TED und Contracts Finder (zwoelf Monate)
        val besteller = aktuell.filter { it.a == Art.AUFTRAG && it.k.isNotEmpty() }.groupBy { it.k }.map { (k, l) -> k to l.sumOf { it.d } }
        val auftraggeber = (extraAuftraggeber + besteller).filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
        val programme = (extraProgramme + programmeEu).filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
        val kredite = summe(aktuell, Art.KREDIT)
        if (zwoelf <= 0 && kredite <= 0 && jahre.isEmpty() && eu.isEmpty()) return null
        return Stand(zwoelf, summe(aktuell, Art.AUFTRAG), summe(aktuell, Art.ZUSCHUSS), kredite, vorher, jahre, zeilen, auftraggeber, programme)
    }

    // Anteil der Summe am Jahresumsatz in Prozent; null ohne Umsatz oder wenn die Summe den Umsatz uebersteigt (zugesagte
    // Mehrjahresbetraege koennen ihn uebersteigen, dann sagt der Anteil nichts)
    fun anteil(zwoelf: Double, umsatzUsd: Double?): Double? {
        val u = umsatzUsd?.takeIf { it > 0 } ?: return null
        if (zwoelf <= 0) return null
        val a = zwoelf / u * 100
        return a.takeIf { it <= 100.0 }
    }
}
