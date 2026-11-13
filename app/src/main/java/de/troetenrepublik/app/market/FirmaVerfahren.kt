package de.troetenrepublik.app.market

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// Rechtsstreit und Strafen zu einer Firma. Quellen: Verfahren aus dem Jahresbericht (Jahresbericht.kt), Rueckstellung und
// moegliche Verluste aus den XBRL-Angaben der SEC, Litigation Releases und Administrative Proceedings der SEC (Liste auf
// sec.gov, Suche ueber den Namen) und Anlagen mit Geldbussen aus EPA ECHO (Umwelt). Zugeordnet wird nur ueber den
// Firmennamen und nur, wenn er eindeutig ist; Unsicheres bleibt weg.
object Verfahren {
    enum class Art { BERICHT, SEC_URTEIL, SEC_VERFAHREN, UMWELT, ARBEITSSCHUTZ }

    // betrag: Geldbusse in Dollar, nur wo die Quelle sie nennt; stelle: Gericht oder Behoerde; datum (ISO) und zusatz
    // (Art des Verstosses) nur, wo die Quelle sie nennt
    data class Eintrag(
        val art: Art, val titel: String, val stelle: String, val jahr: Int, val betrag: Double?, val link: String,
        val datum: String = "", val zusatz: String = "",
    )

    // rueckstellung und moeglich: juengste Angaben aus den XBRL-Daten (Dollar); umwelt: Summe der Strafen der
    // zugeordneten Anlagen in den letzten fuenf Jahren; arbeitsschutz: OSHA-Strafen der letzten fuenf Jahre (nur mit
    // Schluessel des Arbeitsministeriums, FirmaArbeitsschutz.kt)
    data class Ergebnis(
        val eintraege: List<Eintrag>, val rueckstellung: Double?, val moeglich: Double?, val umwelt: Double?,
        val arbeitsschutz: Arbeitsschutz.Ergebnis? = null,
    ) {
        val leer: Boolean get() = eintraege.isEmpty() && rueckstellung == null && moeglich == null && umwelt == null && arbeitsschutz == null

        // Eintraege mit Betrag zuerst (groesster oben), danach nach Jahr, neuester zuerst
        val sortiert: List<Eintrag>
            get() = eintraege.sortedWith(compareByDescending<Eintrag> { it.betrag ?: -1.0 }.thenByDescending { it.jahr })
    }

    private val ZUSATZ = setOf(
        "inc", "corp", "corporation", "co", "company", "llc", "lp", "l", "p", "group", "holdings", "holding", "usa", "us", "america", "americas",
        "north", "international", "intl", "services", "capital", "ltd", "limited", "plc", "&", "the",
    )

    // Allgemeine Woerter, die allein als Firmenname nicht eindeutig sind
    private val GENERISCH = setOf(
        "united", "american", "general", "national", "global", "first", "central", "pacific", "atlantic", "standard", "universal",
        "international", "advanced", "applied", "digital", "energy", "power", "western", "eastern", "southern", "northern", "target",
        "premier", "summit", "pioneer", "liberty", "freedom", "heritage", "genesis", "alliance", "enterprise", "continental", "frontier",
    )

    // Rechtsformen und Sammelwoerter am Ende des Firmennamens, die zum Vergleich wegfallen („Goldman Sachs Group“)
    private val ENDWORT = setOf("group", "holdings", "holding", "company", "co", "corp", "corporation", "inc", "plc", "ltd", "limited", "llc", "lp", "ag", "se", "sa", "nv")

    // Name mit Schreibweise des Originals ohne Rechtsform und Sammelwort am Ende
    fun kurzname(firma: String): String {
        val w = Firmenname.bereinigt(firma).split(' ').filter { it.isNotBlank() }.toMutableList()
        while (w.size > 1 && w.last().lowercase().trim('.', ',') in ENDWORT) w.removeAt(w.lastIndex)
        return w.joinToString(" ")
    }

    private fun woerter(name: String): List<String> {
        val w = Firmenname.worte(name).toMutableList()
        while (w.size > 1 && w.first() == "the") w.removeAt(0)
        return w
    }

    private fun kern(name: String): List<String> {
        val w = woerter(name).toMutableList()
        while (w.size > 1 && w.last() in ENDWORT) w.removeAt(w.lastIndex)
        return w
    }

    // Passt der Name einer Partei (SEC-Verfahren) eindeutig zur Firma? Die Woerter der Firma (ohne Rechtsform) stehen am
    // Anfang des Parteinamens. Besteht der Firmenname aus einem Wort, duerfen danach nur Rechtsform- und Gebietszusaetze
    // folgen; bei mehreren Woertern (ab acht Buchstaben) genuegt der Anfang (Tochtergesellschaften wie „Goldman Sachs Asset
    // Management“).
    fun passt(firma: String, partei: String): Boolean {
        val f = kern(firma)
        val k = woerter(partei)
        if (f.isEmpty() || k.size < f.size || k.take(f.size) != f) return false
        val rest = k.drop(f.size)
        if (rest.isEmpty()) return f.size >= 2 || f[0] !in GENERISCH
        if (f.size >= 2 && f.joinToString("").length >= 8) return true
        return f[0] !in GENERISCH && f[0].length >= 4 && rest.all { it in ZUSATZ || it in ENDWORT }
    }

    // Wie passt, aber fuer Anlagenamen der Umweltbehoerde („EXXONMOBIL BAYTOWN REFINERY“): Der Firmenname ohne Leerzeichen
    // muss genau die ersten Woerter der Anlage ergeben (Schreibweisen „Exxon Mobil“ und „ExxonMobil“ zaehlen gleich).
    fun anlagePasst(firma: String, anlage: String): Boolean {
        val f = kern(firma)
        val fs = f.joinToString("")
        if (fs.length < 6 || (f.size == 1 && f[0] in GENERISCH)) return false
        var acc = ""
        for (w in woerter(anlage)) {
            acc += w
            if (acc.length >= fs.length) return acc == fs
        }
        return false
    }

    // Passt der Betriebsname einer Inspektion (OSHA, „WAL-MART STORES INC“) eindeutig zur Firma? Der Firmenname ohne
    // Rechtsform muss den Betriebsnamen ganz ergeben (ab vier Buchstaben) oder dessen erste Woerter bilden (ab sechs,
    // anlagePasst). Ein allgemeines Wort allein reicht nie.
    fun betriebPasst(firma: String, betrieb: String): Boolean {
        val f = kern(firma)
        val fs = f.joinToString("")
        if (fs.length < 4 || (f.size == 1 && f[0] in GENERISCH)) return false
        if (kern(betrieb).joinToString("") == fs) return true
        return anlagePasst(firma, betrieb)
    }

    // ------------------------------------------------------------ SEC-Listen

    class SecTreffer(val tag: String, val name: String, val adresse: String)

    const val SEC_URTEILE = "https://www.sec.gov/enforcement-litigation/litigation-releases"
    const val SEC_VERFAHREN = "https://www.sec.gov/enforcement-litigation/administrative-proceedings"

    fun secSuche(basis: String, name: String): String = basis + "?populate=" + URLEncoder.encode(kurzname(name), "UTF-8")

    private val ZEILE = Regex("(?s)<tr[^>]*>(.*?)</tr>")
    private val DATUM = Regex("<time datetime=\"(\\d{4}-\\d{2}-\\d{2})")
    private val PARTEI = Regex("release-view__respondents'><a href='([^']*)'>([^<]*)</a>")

    private fun entitaeten(s: String) = s.replace("&amp;", "&").replace("&#039;", "'").replace("&quot;", "\"").replace("&#x27;", "'").replace("&nbsp;", " ").trim()

    // Zeilen der Liste (Datum, Parteien, Adresse); Adressen relativ zu sec.gov werden ergaenzt
    fun secZeilen(html: String): List<SecTreffer> = ZEILE.findAll(html).mapNotNull { m ->
        val z = m.groupValues[1]
        val tag = DATUM.find(z)?.groupValues?.get(1) ?: return@mapNotNull null
        val p = PARTEI.find(z) ?: return@mapNotNull null
        val link = p.groupValues[1].let { if (it.startsWith("http")) it else "https://www.sec.gov$it" }
        SecTreffer(tag, entitaeten(p.groupValues[2]), link)
    }.toList()

    private val TRENNER = Regex("\\s+and\\s+|;")

    // Treffer, deren Partei (auch einer von mehreren) eindeutig zur Firma passt, seit dem Jahr ab
    fun secEintraege(art: Art, firma: String, zeilen: List<SecTreffer>, ab: Int): List<Eintrag> =
        zeilen.filter { z ->
            (z.tag.take(4).toIntOrNull() ?: 0) >= ab && (passt(firma, z.name) || z.name.split(TRENNER).any { passt(firma, it.trim()) })
        }.map { Eintrag(art, it.name, "SEC", it.tag.take(4).toIntOrNull() ?: 0, null, it.adresse) }.take(8)

    // ------------------------------------------------------------ EPA ECHO

    class Anlage(val name: String, val ort: String, val staat: String, val betrag: Double, val letzte: String, val adresse: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun echoSuche(term: String): String =
        "https://echodata.epa.gov/echo/echo_rest_services.get_facilities?output=JSON&p_fn=" + URLEncoder.encode(term, "UTF-8") + "&p_fea=Y"

    fun echoSeite(qid: String): String =
        "https://echodata.epa.gov/echo/echo_rest_services.get_qid?output=JSON&qid=$qid&pageno=1&qcolumns=1,3,4,62,63,64,144"

    // QueryID und Zahl der Treffer der Suche; null bei Fehlern (auch „Queryset Limit“)
    fun echoKennung(text: String): Pair<String, Int>? = runCatching {
        val r = json.parseToJsonElement(text).jsonObject["Results"]!!.jsonObject
        val id = r["QueryID"]?.jsonPrimitive?.contentOrNull ?: return null
        id to (r["QueryRows"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0)
    }.getOrNull()

    private fun dollar(s: String?): Double = s?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull() ?: 0.0

    fun echoAnlagen(text: String): List<Anlage> = runCatching {
        json.parseToJsonElement(text).jsonObject["Results"]!!.jsonObject["Facilities"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val name = o["FacName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Anlage(
                name, o["FacCity"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["FacState"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                dollar(o["FacTotalPenalties"]?.jsonPrimitive?.contentOrNull), o["FacDateLastPenalty"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                o["DfrUrl"]?.jsonPrimitive?.contentOrNull.orEmpty().replace(" ", ""),
            )
        }
    }.getOrDefault(emptyList())

    // Anlagen mit Strafen, die eindeutig zur Firma gehoeren; Doppelte (zweite Schreibweise der Suche) entfallen ueber Name und Ort
    fun echoEintraege(firma: String, anlagen: List<Anlage>): List<Eintrag> =
        anlagen.filter { it.betrag > 0 && anlagePasst(firma, it.name) }.distinctBy { it.name + "|" + it.ort + "|" + it.staat }
            .sortedByDescending { it.betrag }
            .map { a -> Eintrag(Art.UMWELT, a.name, "EPA" + (if (a.staat.isNotBlank()) ", " + a.staat else ""), a.letzte.takeLast(4).toIntOrNull() ?: 0, a.betrag, a.adresse) }

    // Suchbegriffe fuer ECHO: bereinigter Name und die Schreibweise ohne Leerzeichen („Exxon Mobil“, „ExxonMobil“)
    fun echoBegriffe(firma: String): List<String> {
        val n = kurzname(firma)
        val ohne = n.replace(" ", "")
        return listOf(n, ohne).filter { it.length >= 4 }.distinct()
    }
}

// Abruf: Rueckstellung und moegliche Verluste (XBRL), SEC-Listen, ECHO. Ergebnisse bleiben zwoelf Stunden im Speicher.
object VerfahrenAbruf {
    private val api by lazy { MarketApi() }
    private val sperre = Mutex()
    private val speicher = ConcurrentHashMap<String, Pair<Long, Verfahren.Ergebnis>>()
    private const val GUELTIG_MS = 12 * 3_600_000L

    // Ein Betrag der XBRL-Angaben: juengster Stand, hoechstens 550 Tage alt; null ohne Wert
    private suspend fun betrag(cik: String, begriff: String, kennung: String, heute: LocalDate): Double? {
        val a = runCatching { FirmaSec.anfrage(Auftragsbestand.adresse(cik, begriff), kennung, max = 1_000_000) }.getOrNull() ?: return null
        if (!a.ok) return null
        val s = Auftragsbestand.staende(Auftragsbestand.reihe(a.text.orEmpty())).lastOrNull() ?: return null
        val ende = runCatching { LocalDate.parse(s.ende) }.getOrNull() ?: return null
        return s.wert.takeIf { it > 0 && java.time.temporal.ChronoUnit.DAYS.between(ende, heute) <= 550 }
    }

    private suspend fun secListe(basis: String, firma: String, kennung: String): List<Verfahren.SecTreffer> {
        val a = runCatching { FirmaSec.anfrage(Verfahren.secSuche(basis, firma), kennung, max = 1_500_000, accept = "text/html") }.getOrNull() ?: return emptyList()
        return if (a.ok) Verfahren.secZeilen(a.text.orEmpty()) else emptyList()
    }

    private suspend fun umwelt(firma: String): List<Verfahren.Anlage> {
        val aus = ArrayList<Verfahren.Anlage>()
        for (t in Verfahren.echoBegriffe(firma)) {
            delay(300)
            val s = runCatching { api.anfrage(Verfahren.echoSuche(t), mapOf("Accept" to "application/json"), max = 200_000) }.getOrNull() ?: continue
            val (id, zeilen) = Verfahren.echoKennung(s.text.orEmpty()) ?: continue
            if (zeilen == 0 || zeilen > 6_000) continue
            delay(300)
            val p = runCatching { api.anfrage(Verfahren.echoSeite(id), mapOf("Accept" to "application/json"), max = 8_000_000) }.getOrNull() ?: continue
            if (p.ok) aus += Verfahren.echoAnlagen(p.text.orEmpty())
        }
        return aus
    }

    // Alles zur Firma. bericht: Verfahren aus dem Jahresbericht (kann leer sein), link: Adresse des Berichts.
    suspend fun laden(
        cik: String, firma: String, kennung: String, bericht: List<Jahresbericht.Verfahren>, berichtLink: String,
        jetzt: Long = System.currentTimeMillis(), heute: LocalDate = LocalDate.now(java.time.ZoneOffset.UTC),
    ): Verfahren.Ergebnis = sperre.withLock {
        val bekannt = speicher[cik]
        if (bekannt != null && jetzt - bekannt.first < GUELTIG_MS) return@withLock bekannt.second
        val ab = heute.year - 10
        val aus = ArrayList<Verfahren.Eintrag>()
        bericht.forEach { aus += Verfahren.Eintrag(Verfahren.Art.BERICHT, it.titel, it.stelle, it.jahr, null, berichtLink) }
        val rueck = runCatching { betrag(cik, "LossContingencyAccrualAtCarryingValue", kennung, heute) }.getOrNull()
        val moeglich = runCatching { betrag(cik, "LossContingencyEstimateOfPossibleLoss", kennung, heute) }.getOrNull()
        runCatching { aus += Verfahren.secEintraege(Verfahren.Art.SEC_URTEIL, firma, secListe(Verfahren.SEC_URTEILE, firma, kennung), ab) }
        runCatching { aus += Verfahren.secEintraege(Verfahren.Art.SEC_VERFAHREN, firma, secListe(Verfahren.SEC_VERFAHREN, firma, kennung), ab) }
        val echo = runCatching { Verfahren.echoEintraege(firma, umwelt(firma)) }.getOrDefault(emptyList())
        aus += echo.take(10)
        val e = Verfahren.Ergebnis(aus, rueck, moeglich, echo.sumOf { it.betrag ?: 0.0 }.takeIf { it > 0 })
        speicher[cik] = jetzt to e
        e
    }
}
