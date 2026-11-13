package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// Verflechtungen (0.79): Netz aus Firmen, Personen, Staaten, Fonds und Sammelknoten, verbunden durch Kanten mit Art, Umfang,
// Status, Guete und Primaerlink. Das gepflegte Buendel liegt in assets/netz/kanten.json, automatisch gemeldete Kanten
// (SEC, GLEIF, Wikidata) kommen je Firma dazu. Dieses Modell rechnet nur, ohne Netz und ohne Oberflaeche.
object Netz {
    const val DATEI = "netz/kanten.json"

    enum class Typ(val schluessel: String) { FIRMA("firma"), PERSON("person"), STAAT("staat"), FONDS("fonds"), SAMMEL("sammel") }

    // Richtung einer Kante: von nach nach. liefert: von liefert an nach. kauft und rechenzentrum: von kauft bei nach.
    // lizenz: von gibt die Lizenz an nach. haelt_anteil, finanziert, optionsschein, garantie, tochter: von gibt oder haelt, nach erhaelt.
    enum class Art(val schluessel: String, val gruppe: Gruppe, val kapital: Boolean) {
        LIEFERT("liefert", Gruppe.LIEFERUNG, false),
        KAUFT("kauft", Gruppe.LIEFERUNG, false),
        RECHENZENTRUM("rechenzentrum", Gruppe.LIEFERUNG, false),
        LIZENZ("lizenz", Gruppe.LIEFERUNG, false),
        HAELT_ANTEIL("haelt_anteil", Gruppe.BETEILIGUNG, true),
        TOCHTER("tochter", Gruppe.BETEILIGUNG, false),
        FINANZIERT("finanziert", Gruppe.FINANZIERUNG, true),
        OPTIONSSCHEIN("optionsschein", Gruppe.FINANZIERUNG, true),
        GARANTIE("garantie", Gruppe.FINANZIERUNG, true),
        // Ungerichtet: gleiche Maerkte (von und nach sind austauschbar)
        KONKURRIERT("konkurriert", Gruppe.WETTBEWERB, false),
        // Person (von) sitzt im Verwaltungsrat oder Aufsichtsrat der Firma (nach), mit Rolle
        MANDAT("mandat", Gruppe.MANDAT, false),
        // Staat oder Behoerde (von) gibt der Firma (nach) Zuschuss, Kredit oder Beteiligung, mit Programm
        STAAT_FINANZIERT("staat_finanziert", Gruppe.STAATSGELD, false);

        // Geschaeftsbeziehung (Umsatz fliesst vom Abnehmer zum Lieferanten)
        val handel: Boolean get() = gruppe == Gruppe.LIEFERUNG

        // Hat eine Richtung (alle ausser Wettbewerb)
        val gerichtet: Boolean get() = this != KONKURRIERT
    }

    // Filter der Ansicht
    enum class Gruppe { LIEFERUNG, BETEILIGUNG, FINANZIERUNG, WETTBEWERB, MANDAT, STAATSGELD }

    // ZUGESAGT, AUSGEZAHLT, ZURUECKGEZOGEN: Staatsgeld
    enum class Status(val schluessel: String) {
        DEFINITIV("definitiv"), ABSICHT("absichtserklaerung"), BEENDET("beendet"),
        ZUGESAGT("zugesagt"), AUSGEZAHLT("ausgezahlt"), ZURUECKGEZOGEN("zurueckgezogen"),
    }

    enum class Guete { A, B, C }

    // GEPFLEGT: aus dem Buendel mit Beleg je Kante. GEMELDET: von der Firma selbst oder einem Register gemeldet (SEC, GLEIF, Wikidata).
    enum class Herkunft { GEPFLEGT, GEMELDET }

    enum class Einheit { USD, EUR, GW, PCT, AKTIEN }

    enum class Bezug { STIMMEN, KAPITAL, UMSATZ, KLASSE }

    class Knoten(
        val id: String, val typ: Typ, val name: String, val nameEn: String? = null, val symbole: List<String> = emptyList(),
        val cik: String? = null, val lei: String? = null, val wikidata: String? = null,
        val domain: String? = null,
    ) {
        fun anzeige(deutsch: Boolean): String = if (deutsch) name else nameEn ?: name

        // Symbol fuer die Wertpapierseite (das erste)
        val symbol: String? get() = symbole.firstOrNull()
    }

    // wert in USD, GW, Prozent oder Stueck. hoechstens: "bis zu".
    class Umfang(val wert: Double, val einheit: Einheit, val bezug: Bezug? = null, val hoechstens: Boolean = false)

    class Quelle(val name: String, val url: String, val primaer: Boolean)

    class Kante(
        val id: String, val von: String, val nach: String, val art: Art, val umfang: Umfang?, val seit: String?, val bis: String?,
        val status: Status, val guete: Guete, val quelle: Quelle, val weitere: List<Quelle>, val stand: String, val ablauf: String?,
        val hinweis: String?, val hinweisEn: String?, val bedingt: Boolean, val teil: String?, val schluessel: Boolean,
        val herkunft: Herkunft = Herkunft.GEPFLEGT,
        // Mandat: Rolle (Director, Chair ...); Staatsgeld: Programm
        val rolle: String? = null,
        val programm: String? = null,
        val programmEn: String? = null,
    ) {
        // Status; ein ueberfaelliger Pflegetermin (ablauf) macht aus einer Kante kein Vertragsende, sie bleibt sichtbar (ungeprueft)
        @Suppress("UNUSED_PARAMETER")
        fun statusAm(heute: String): Status = status

        // Der Pflegetermin der Angabe ist ueberschritten
        fun ungeprueft(heute: String): Boolean = ablauf != null && ablauf < heute

        // Gilt noch: weder beendet noch abgelaufen noch zurueckgezogen
        fun aktiv(heute: String): Boolean = statusAm(heute).let { it != Status.BEENDET && it != Status.ZURUECKGEZOGEN }

        // Schluessel fuer die Doppelpruefung; Wettbewerb ist ungerichtet
        fun paar(): Triple<String, String, Art> = if (art == Art.KONKURRIERT && von > nach) Triple(nach, von, art) else Triple(von, nach, art)

        // Wer an wen zahlt (Geld, Kapital, Lizenzgebuehr): bei Lieferung und Lizenz der Abnehmer an den Lieferanten
        fun zahler(): String = if (art == Art.LIEFERT || art == Art.LIZENZ) nach else von
        fun empfaenger(): String = if (art == Art.LIEFERT || art == Art.LIZENZ) von else nach

        // Lieferant und Abnehmer bei Geschaeftsbeziehungen, sonst null
        val lieferant: String? get() = when (art) { Art.LIEFERT -> von; Art.KAUFT, Art.RECHENZENTRUM -> nach; else -> null }
        val abnehmer: String? get() = when (art) { Art.LIEFERT -> nach; Art.KAUFT, Art.RECHENZENTRUM -> von; else -> null }

        fun andere(id: String): String = if (von == id) nach else von
    }

    // Normalisierter Name fuer den Abgleich (grob: Rechtsform und Einzelzeichen fallen weg). Nur fuer Suchbegriffe und Anzeige,
    // nicht zum Zusammenfuehren von Knoten (dafuer nameVoll).
    fun nameKern(s: String): String = de.troetenrepublik.app.market.SecBericht.nameKern(s)

    // Woerter, die allein keine Firma bezeichnen
    private val ALLGEMEIN = setOf(
        "global", "holdings", "holding", "group", "international", "systems", "energy", "capital", "financial", "technologies", "technology",
        "partners", "resources", "industries", "services", "solutions", "america", "american", "united", "first", "national", "general", "meta",
    )

    private val RECHTSFORM = setOf("inc", "incorporated", "corp", "corporation", "co", "company", "ltd", "limited", "plc", "nv", "se", "ag", "sa", "llc", "lp", "the")

    // Voller normierter Name: Kleinbuchstaben, "&" als "and", Satzzeichen weg, Rechtsform am Ende weg, alle Woerter bleiben
    // (auch einzelne Buchstaben). Leer, wenn nur ein allgemeines Wort uebrig bleibt ("Global", "Holdings").
    fun nameVoll(s: String): String {
        val w = s.lowercase().replace("&", " and ").replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (w.size > 1 && w.last() in RECHTSFORM) w.removeAt(w.size - 1)
        if (w.isNotEmpty() && w.first() == "the") w.removeAt(0)
        if (w.isEmpty() || (w.size == 1 && w[0] in ALLGEMEIN)) return ""
        return w.joinToString(" ")
    }

    // Link auf ein einzelnes Dokument (Filing, Mitteilung, Berichtsseite, Eintrag). Sammelseiten gelten nicht: Startseiten ohne
    // Pfad, die Firmenuebersicht bei EDGAR und Suchseiten.
    fun einzeldokument(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val rest = url.removePrefix("https://")
        val host = rest.substringBefore('/')
        if (!host.contains('.')) return false
        val nachHost = rest.substringAfter('/', "")
        val pfad = nachHost.substringBefore('#').substringBefore('?').trim('/')
        val fragment = nachHost.substringAfter('#', "")
        val klein = nachHost.lowercase()
        if (pfad.isEmpty() && !fragment.contains('/')) return false
        // Startseite in einer Sprache ("/en", "/de-de")
        if (Regex("(?i)[a-z]{2}([-_][a-z]{2})?").matches(pfad) && !fragment.contains('/')) return false
        if ("edgar/browse" in klein || "browse-edgar" in klein || klein.startsWith("edgar/search") || "cgi-bin" in klein) return false
        if (pfad.lowercase() in setOf("search", "investors", "ir", "about", "company", "news")) return false
        return true
    }

    // Kuerzel ohne Klassenzusatz und Schreibweise ("BRK.B" gleich "BRK-B")
    fun symbolSchluessel(s: String): String = s.trim().uppercase().replace('/', '-').replace(Regex("^([A-Z0-9]+)\\.([A-KM-Z])$"), "$1-$2")

    class Graph(val knoten: Map<String, Knoten>, val kanten: List<Kante>) {
        private val nachKnoten: Map<String, List<Kante>> by lazy {
            val m = HashMap<String, MutableList<Kante>>()
            for (k in kanten) { m.getOrPut(k.von) { ArrayList() }.add(k); if (k.nach != k.von) m.getOrPut(k.nach) { ArrayList() }.add(k) }
            m
        }
        private val nachSymbol: Map<String, Knoten> by lazy {
            val m = HashMap<String, Knoten>()
            for (k in knoten.values) for (s in k.symbole) m.putIfAbsent(symbolSchluessel(s), k)
            m
        }
        private val nachName: Map<String, Knoten> by lazy {
            val m = HashMap<String, Knoten>()
            for (k in knoten.values) if (k.typ == Typ.FIRMA) nameVoll(k.name).takeIf { it.isNotEmpty() }?.let { m.putIfAbsent(it, k) }
            m
        }
        private val nachCik: Map<String, Knoten> by lazy { knoten.values.filter { it.cik != null }.associateBy { it.cik!!.trimStart('0') } }
        private val nachLei: Map<String, Knoten> by lazy { knoten.values.filter { it.lei != null }.associateBy { it.lei!! } }
        private val nachWikidata: Map<String, Knoten> by lazy { knoten.values.filter { it.wikidata != null }.associateBy { it.wikidata!! } }

        fun kanten(id: String): List<Kante> = nachKnoten[id].orEmpty()

        fun knotenFuerSymbol(symbol: String): Knoten? = nachSymbol[symbolSchluessel(symbol)]

        // Abgleich zuerst ueber Kennungen (CIK, LEI, Wikidata, Kuerzel), der Name ist die letzte Stufe: voller normierter Name,
        // nie ein allgemeines Wort allein. Personen gleichen nie ueber den Namen ab.
        fun knotenFuerKennung(cik: String? = null, lei: String? = null, wikidata: String? = null, symbol: String? = null): Knoten? =
            cik?.let { nachCik[it.trimStart('0')] } ?: lei?.let { nachLei[it] } ?: wikidata?.let { nachWikidata[it] } ?: symbol?.let { knotenFuerSymbol(it) }

        fun knotenFuerName(name: String): Knoten? = nameVoll(name).takeIf { it.isNotEmpty() }?.let { nachName[it] }

        // Zulieferer, Abnehmer und Beteiligungen, Finanzierung und Lizenzen einer Firma
        fun zulieferer(id: String): List<Kante> = kanten(id).filter { it.abnehmer == id }
        fun abnehmer(id: String): List<Kante> = kanten(id).filter { it.lieferant == id }
        fun kapital(id: String): List<Kante> =
            kanten(id).filter { it.art.gruppe == Gruppe.BETEILIGUNG || it.art.gruppe == Gruppe.FINANZIERUNG || it.art == Art.LIZENZ }
        fun wettbewerber(id: String): List<Kante> = kanten(id).filter { it.art == Art.KONKURRIERT }
        fun mandate(id: String): List<Kante> = kanten(id).filter { it.art == Art.MANDAT }
        fun staatsgeld(id: String): List<Kante> = kanten(id).filter { it.art == Art.STAAT_FINANZIERT }

        // Fuegt Knoten und Kanten hinzu; vorhandene Knoten und Kanten (nach id) bleiben. Eine gemeldete Kante fehlt, wenn das gepflegte
        // Buendel dieselbe Art zwischen denselben Knoten schon hat (das Buendel nennt die Quelle).
        fun mit(neueKnoten: Collection<Knoten>, neueKanten: Collection<Kante>): Graph {
            val k = LinkedHashMap(knoten)
            for (n in neueKnoten) k.putIfAbsent(n.id, n)
            val ids = kanten.mapTo(HashSet()) { it.id }
            val paare = kanten.filter { it.herkunft != Herkunft.GEMELDET }.mapTo(HashSet()) { it.paar() }
            val neu = ArrayList(kanten)
            for (e in neueKanten) {
                if (e.von !in k || e.nach !in k) continue
                if (!ids.add(e.id)) continue
                if (e.herkunft == Herkunft.GEMELDET && e.paar() in paare) continue
                neu.add(e)
            }
            return Graph(k, neu)
        }

        // Gewicht einer Kante fuer die Reihenfolge beim Erweitern: Betrag in USD, dann Guete
        fun gewicht(e: Kante): Double = (e.umfang?.takeIf { it.einheit == Einheit.USD }?.wert ?: 0.0) + (3 - e.guete.ordinal) * 1.0
    }

    class Ergebnis(val graph: Graph, val stand: String, val probleme: List<String>)

    private val json = Json { ignoreUnknownKeys = true }
    private val DATUM = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val ZIFFERN = Regex("^[0-9]{1,10}$")
    private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.zahl(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    private fun JsonObject.wahr(k: String): Boolean = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true

    private fun <T> Array<T>.finde(s: String?, schluessel: (T) -> String): T? = firstOrNull { schluessel(it) == s }

    private fun quelle(o: JsonObject?): Quelle? {
        o ?: return null
        val name = o.text("name") ?: return null
        val url = o.text("url") ?: return null
        return Quelle(name, url, o.wahr("primaer"))
    }

    // Liest das Buendel; null bei kaputtem JSON. Fehlerhafte Knoten und Kanten fallen weg und stehen in probleme.
    fun lesen(text: String, herkunft: Herkunft = Herkunft.GEPFLEGT): Ergebnis? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        val probleme = ArrayList<String>()
        val knoten = LinkedHashMap<String, Knoten>()
        for (e in (o["knoten"] as? JsonArray).orEmpty()) {
            val n = e as? JsonObject ?: continue
            val id = n.text("id")
            val typ = Typ.entries.toTypedArray().finde(n.text("typ")) { it.schluessel }
            val name = n.text("name")
            if (id == null || typ == null || name == null) { probleme += "Knoten ohne id, typ oder name: $id"; continue }
            if (id in knoten) { probleme += "Knoten doppelt: $id"; continue }
            val cik = n.text("cik")
            if (cik != null && !ZIFFERN.matches(cik)) probleme += "Knoten $id: cik ungueltig"
            knoten[id] = Knoten(
                id, typ, name, n.text("nameEn"),
                (n["symbole"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf { s -> s.isNotBlank() } },
                cik, n.text("lei"), n.text("wikidata"), n.text("domain")?.takeIf { DOMAIN.matches(it) },
            )
        }
        val kanten = ArrayList<Kante>()
        val ids = HashSet<String>()
        for (e in (o["kanten"] as? JsonArray).orEmpty()) {
            val k = e as? JsonObject ?: continue
            val id = k.text("id") ?: "?"
            val art = Art.entries.toTypedArray().finde(k.text("art")) { it.schluessel }
            val status = Status.entries.toTypedArray().finde(k.text("status")) { it.schluessel }
            val guete = Guete.entries.firstOrNull { it.name == k.text("guete") }
            val von = k.text("von"); val nach = k.text("nach")
            val q = quelle(k["quelle"] as? JsonObject)
            val stand = k.text("stand")
            val fehler = when {
                art == null -> "Art ungueltig"
                status == null -> "Status ungueltig"
                guete == null -> "Guete ungueltig"
                von == null || von !in knoten -> "von unbekannt: $von"
                nach == null || nach !in knoten -> "nach unbekannt: $nach"
                von == nach -> "von gleich nach"
                q == null -> "Quelle fehlt"
                !einzeldokument(q.url) -> "Link ist kein einzelnes Dokument: ${q.url}"
                stand == null || !DATUM.matches(stand) -> "Stand ungueltig"
                guete == Guete.A && !q.primaer -> "Guete A ohne Primaerquelle"
                !ids.add(id) -> "Kante doppelt: $id"
                else -> null
            }
            if (fehler != null) { probleme += "Kante $id: $fehler"; continue }
            val u = (k["umfang"] as? JsonObject)?.let { uo ->
                val w = uo.zahl("wert"); val ei = Einheit.entries.firstOrNull { it.name == uo.text("einheit") }
                if (w == null || ei == null) { probleme += "Kante $id: Umfang ungueltig"; null }
                else Umfang(w, ei, Bezug.entries.firstOrNull { it.name == uo.text("bezug") }, uo.wahr("hoechstens"))
            }
            val ablauf = k.text("ablauf")?.takeIf { DATUM.matches(it) }
            kanten += Kante(
                id, von!!, nach!!, art!!, u, k.text("seit"), k.text("bis"), status!!, guete!!, q!!,
                (k["weitere"] as? JsonArray).orEmpty().mapNotNull { quelle(it as? JsonObject) }, stand!!, ablauf,
                k.text("hinweis"), k.text("hinweisEn"), k.wahr("bedingt"), k.text("teil"), k.wahr("schluessel"), herkunft,
                k.text("rolle"), k.text("programm"), k.text("programmEn"),
            )
        }
        if (knoten.isEmpty() || kanten.isEmpty()) return null
        Ergebnis(Graph(knoten, kanten), o.text("stand").orEmpty(), probleme)
    }.getOrNull()

    // Eingebautes oder geprueft nachgeladenes Buendel, einmal je Prozess gelesen
    val buendel: Ergebnis? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }

    // Das gepflegte Netz (leer, falls das Buendel fehlt)
    fun basis(): Graph = buendel?.graph ?: Graph(emptyMap(), emptyList())
}
