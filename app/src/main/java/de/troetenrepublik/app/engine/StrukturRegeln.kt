package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.pow

// Eigentuemer und Rechte, Rechenteil: Deckblatt eines Jahres- oder Quartalsberichts (Inline-XBRL) zu Gattungen, Hinweise aus
// dem Text der Unterlagen, Schedule 13D/13G (XML), Zusammenfuehren mit dem gepflegten Buendel und die Rechte je Gattung.
// Reine Funktionen; was nicht sicher gelesen ist, bleibt null.
object StrukturRegeln {

    // ------------------------------------------------------------ Deckblatt

    class Titel(val ctx: String, val titel: String, val kuerzel: String, val boerse: String)

    // klasse: Buchstabe der Gattung (A, B ...), leer ohne Klassenachse; stueck bereits mit scale umgerechnet
    class Anzahl(val klasse: String, val stueck: Double)

    class Deckblatt(val titel: List<Titel>, val anzahl: List<Anzahl>)

    private val KONTEXT = Regex("(?s)<xbrli:context\\s+id=\"([^\"]+)\"[^>]*>(.*?)</xbrli:context>")
    private val GLIED = Regex("explicitMember[^>]*dimension=\"([^\"]+)\"[^>]*>\\s*([^<\\s]+)\\s*<")
    private val NONNUM = Regex("(?s)<ix:nonNumeric\\b([^>]*)>(.*?)</ix:nonNumeric>")
    private val NONFRAC = Regex("(?s)<ix:nonFraction\\b([^>]*)>(.*?)</ix:nonFraction>")
    private val TAG = Regex("(?s)<[^>]*>")
    private val KLASSE_GLIED = Regex("Class([A-Z])(?![a-z])")
    private val KLASSE_TITEL = Regex("(?i)\\bclass\\s*([A-Z])\\b")

    private fun attr(a: String, n: String): String? = Regex("\\b$n=\"([^\"]*)\"").find(a)?.groupValues?.get(1)

    private fun klartext(s: String): String = Zahlenmitteilung.entitaeten(s.replace(TAG, " ")).replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()

    private fun zahl(text: String, format: String?, scale: Int): Double? {
        val f = format.orEmpty().lowercase()
        if (f.contains("zerodash") || f.contains("fixed-zero")) return 0.0
        val t = text.replace(Regex("[^0-9.,]"), "")
        if (t.isEmpty()) return null
        val norm = if (f.contains("comma-decimal")) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        return norm.toDoubleOrNull()?.times(10.0.pow(scale))
    }

    // Titel der Wertpapiere und Aktienzahlen je Klasse aus dem Anfang des Hauptdokuments (Deckblatt, Inline-XBRL).
    fun deckblatt(html: String): Deckblatt {
        // Kontext: Kennung zu Klasse (Buchstabe aus dem Gliednamen der Klassenachse, sonst leer)
        val klasseJeKontext = HashMap<String, String>()
        for (m in KONTEXT.findAll(html)) {
            val glieder = GLIED.findAll(m.groupValues[2]).map { it.groupValues[1] to it.groupValues[2] }.toList()
            val klasse = glieder.firstOrNull { it.first.contains("ClassOfStock", true) }
            klasseJeKontext[m.groupValues[1]] = if (klasse == null) "" else KLASSE_GLIED.find(klasse.second)?.groupValues?.get(1) ?: ("#" + klasse.second.substringAfter(':'))
        }
        val titel = LinkedHashMap<String, String>()
        val kuerzel = HashMap<String, String>()
        val boerse = HashMap<String, String>()
        for (m in NONNUM.findAll(html)) {
            val name = attr(m.groupValues[1], "name") ?: continue
            val ctx = attr(m.groupValues[1], "contextRef") ?: continue
            val t = klartext(m.groupValues[2])
            when (name) {
                "dei:Security12bTitle" -> titel.putIfAbsent(ctx, t)
                "dei:TradingSymbol" -> kuerzel.putIfAbsent(ctx, t)
                "dei:SecurityExchangeName" -> boerse.putIfAbsent(ctx, t)
            }
        }
        val anzahl = ArrayList<Anzahl>()
        for (m in NONFRAC.findAll(html)) {
            val a = m.groupValues[1]
            if (attr(a, "name") != "dei:EntityCommonStockSharesOutstanding") continue
            val ctx = attr(a, "contextRef") ?: continue
            val v = zahl(klartext(m.groupValues[2]), attr(a, "format"), attr(a, "scale")?.toIntOrNull() ?: 0) ?: continue
            anzahl += Anzahl(klasseJeKontext[ctx].orEmpty(), v)
        }
        return Deckblatt(titel.map { (c, t) -> Titel(c, t, kuerzel[c].orEmpty(), boerse[c].orEmpty()) }, anzahl.distinctBy { it.klasse to it.stueck })
    }

    private val KEINE_AKTIE = Regex("(?i)\\bnotes?\\b|debentures?|bonds?|warrants?|\\bunits?\\b|preferred|\\brights?\\b|subordinated|convertible|\\bdue\\s+20")
    private val ADS_TITEL = Regex("(?i)american depositary|\\bADSs?\\b")
    private val ADS_VERHAELTNIS = Regex("(?i)each\\s+(?:ADS\\s+)?representing\\s+(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|eight|ten|twelve|a)\\b")
    private val ZAHLWORT = mapOf("one" to 1.0, "a" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "eight" to 8.0, "ten" to 10.0, "twelve" to 12.0)

    // Nennwertzusatz im Titel der Gattung („, $0.00001 par value per share“, „, par value $0.01“, „, no par value“), nur fuer die Anzeige
    private val ZUSATZ_OHNE_NENNWERT = Regex("(?i)\\s*,?\\s*\\(?\\s*(?:no|without)\\s+(?:stated\\s+)?par\\s+value(?:\\s+per\\s+share)?\\s*\\)?")
    private val ZUSATZ_NENNWERT = Regex(
        "(?i)\\s*,?\\s*\\(?\\s*(?:(?:par|nominal|stated)\\s+value\\s*(?:of\\s*)?(?:US\\\$|[A-Z]{3}\\s*|[\$€£]\\s*)?\\d(?:[\\d.,]*\\d)?(?:\\s*(?:per\\s+share|each))?" +
            "|(?:US\\\$|[A-Z]{3}\\s*|[\$€£]\\s*)?\\d(?:[\\d.,]*\\d)?\\s*(?:par|nominal|stated)\\s+value(?:\\s+(?:per\\s+share|each))?)\\s*\\)?",
    )

    // Name der Gattung fuer die Anzeige: ohne Nennwertzusatz („Common Stock, $0.00001 par value per share“ wird „Common Stock“)
    fun anzeigeTitel(titel: String): String {
        val kurz = titel.replace(ZUSATZ_OHNE_NENNWERT, "").replace(ZUSATZ_NENNWERT, "").trim().trimEnd(',', ' ')
        return kurz.ifBlank { titel }
    }

    fun istAktie(titel: String): Boolean = !KEINE_AKTIE.containsMatchIn(titel) || ADS_TITEL.containsMatchIn(titel) && !Regex("(?i)preferred").containsMatchIn(titel)

    // Gattungen aus dem Deckblatt. Eine einzige Gattung ohne Hinweise auf Sonderstimmrechte hat eine Stimme je Aktie
    // (Normalfall); bei mehreren Klassen bleibt die Zahl der Stimmen offen (nicht geprueft).
    fun gattungen(d: Deckblatt, hinweise: Set<String>): List<Struktur.Gattung> {
        val aktien = d.titel.filter { istAktie(it.titel) }
        val aus = ArrayList<Struktur.Gattung>()
        aktien.forEachIndexed { i, t ->
            val ads = ADS_TITEL.containsMatchIn(t.titel)
            val klasse = KLASSE_TITEL.find(t.titel)?.groupValues?.get(1)?.uppercase()
            val id = if (ads) "ADS" else klasse ?: "S$i"
            val verhaeltnis = if (ads) ADS_VERHAELTNIS.find(t.titel)?.let { m -> m.groupValues[1].toDoubleOrNull() ?: ZAHLWORT[m.groupValues[1].lowercase()] } else null
            if (aus.none { it.id == id }) aus += Struktur.Gattung(
                id = id, titel = anzeigeTitel(t.titel), kuerzel = t.kuerzel, symbole = listOfNotNull(t.kuerzel.ifBlank { null }),
                art = if (ads) Struktur.GArt.ADS else Struktur.GArt.STAMM, wirtschaftlich = !ads, ads = verhaeltnis,
            )
        }
        val ohneAds = aus.filter { it.art != Struktur.GArt.ADS }
        // Aktienzahlen: je Klasse, ohne Klasse bei genau einer Gattung
        val mitZahl = ArrayList(aus)
        for (a in d.anzahl) {
            val i = mitZahl.indexOfFirst { it.id == a.klasse }
            when {
                i >= 0 && mitZahl[i].aktien == null -> mitZahl[i] = mitZahl[i].copy(aktien = a.stueck)
                a.klasse.isEmpty() && ohneAds.size == 1 -> {
                    val j = mitZahl.indexOfFirst { it.id == ohneAds[0].id }
                    if (mitZahl[j].aktien == null) mitZahl[j] = mitZahl[j].copy(aktien = a.stueck)
                }
                a.klasse.isEmpty() && ohneAds.isEmpty() && mitZahl.none { it.id == "S" } ->
                    mitZahl += Struktur.Gattung(id = "S", titel = "", aktien = a.stueck, boerse = false)
                a.klasse.isNotEmpty() && i < 0 && !a.klasse.startsWith("#") ->
                    mitZahl += Struktur.Gattung(id = a.klasse, titel = "Class ${a.klasse}", aktien = a.stueck, boerse = false)
                a.klasse.startsWith("#") && i < 0 && ohneAds.size == 1 && ohneAds[0].aktien == null -> {
                    val j = mitZahl.indexOfFirst { it.id == ohneAds[0].id }
                    mitZahl[j] = mitZahl[j].copy(aktien = a.stueck)
                }
            }
        }
        // Eine Stimme je Aktie gilt nur, wenn der Standardsatz zum Stimmrecht im Dokument gelesen ist
        val einzeln = mitZahl.count { it.art != Struktur.GArt.ADS } == 1 && Struktur.Flag.EINE_STIMME in hinweise &&
            Struktur.Flag.MEHRSTIMMEN !in hinweise && Struktur.Flag.OHNE_STIMME !in hinweise && Struktur.Flag.GOLDEN !in hinweise
        val basis = mitZahl.firstOrNull { it.art != Struktur.GArt.ADS }?.id
        return mitZahl.map { g ->
            when {
                g.art == Struktur.GArt.ADS -> g.copy(basis = basis.takeIf { mitZahl.count { x -> x.art != Struktur.GArt.ADS } == 1 })
                einzeln -> g.copy(stimmen = 1.0)
                else -> g
            }
        }
    }

    // ------------------------------------------------------------ Hinweise aus dem Text

    private val H_MEHR = Regex("(?i)\\b(?:two|three|four|five|ten|twenty|fifty|\\d{2,3})\\s+votes?\\s+per\\s+share|super-?voting|multiple[- ]votes?|\\bten\\s+votes\\b")
    private val H_OHNE = Regex("(?i)non-?voting|no\\s+voting\\s+rights|without\\s+voting\\s+rights")
    private val H_GOLD = Regex("(?i)golden\\s+share|special\\s+share")
    private val H_VIE = Regex("(?i)variable\\s+interest\\s+entit")
    private val H_EINE = Regex("(?i)entitled\\s+to\\s+(?:one|1)\\s+vote|(?:one|1)\\s+vote\\s+per\\s+(?:share|ordinary\\s+share|common\\s+share)|each\\s+(?:share|ordinary\\s+share|common\\s+share)[^.]{0,80}?\\bone\\s+vote\\b")
    private val H_ADR = Regex("(?i)american\\s+depositary")

    // Kein Hinweis auf eine Gattung ohne Stimme: „voting and non-voting stock held by non-affiliates“ (Marktwert auf dem Deckblatt),
    // Bedingungen von Mitarbeiterplaenen („no voting rights with respect to the Restricted Stock Units“) und Vorzugsaktien,
    // die zugelassen, aber nicht ausgegeben sind.
    private val O_VORAN = Regex("(?i)\\bvoting\\s+(?:and|or|and/or)\\s*$")
    private val O_PLAN = Regex("(?i)restricted\\s+stock\\s+units?|\\bRSUs?\\b|\\bawards?\\b|stock\\s+options?|equity\\s+(?:incentive\\s+)?plan|dividend\\s+equivalent|\\bparticipants?\\b|non-employee\\s+director")
    private val O_VORZUG = Regex("(?i)preferred")
    private val O_LEER = Regex("(?i)\\bauthori[sz]ed\\b|\\bnone\\b|\\bno\\s+shares\\b|not\\s+(?:been\\s+)?issued|\\bblank\\s+check\\b")

    private fun ohneStimme(text: String): Boolean = H_OHNE.findAll(text).any { m ->
        val davor = klartext(text.substring(maxOf(0, m.range.first - 400), m.range.first))
        val danach = klartext(text.substring(m.range.last + 1, minOf(text.length, m.range.last + 401)))
        if (O_VORAN.containsMatchIn(davor)) return@any false
        val satz = davor.substringAfterLast(". ") + " " + klartext(m.value) + " " + danach.substringBefore(". ")
        !(O_PLAN.containsMatchIn(satz) || O_VORZUG.containsMatchIn(satz) && O_LEER.containsMatchIn(satz))
    }

    // Schluesselwoerter als Hinweis (nie als Zahl)
    fun hinweise(text: String): Set<String> {
        val s = LinkedHashSet<String>()
        if (H_MEHR.containsMatchIn(text)) s += Struktur.Flag.MEHRSTIMMEN
        if (ohneStimme(text)) s += Struktur.Flag.OHNE_STIMME
        if (H_GOLD.containsMatchIn(text)) s += Struktur.Flag.GOLDEN
        if (H_VIE.containsMatchIn(text)) s += Struktur.Flag.VIE
        if (H_ADR.containsMatchIn(text)) s += Struktur.Flag.ADR
        if (H_EINE.containsMatchIn(text)) s += Struktur.Flag.EINE_STIMME
        return s
    }

    // ------------------------------------------------------------ Halter

    private val FONDS = Regex("(?i)vanguard|blackrock|state street|fidelity|\\bFMR\\b|t\\.? rowe|capital (group|world|research)|geode|invesco|wellington|norges|amundi|\\bubs\\b|morgan stanley|jpmorgan|j\\.p\\. morgan|dimensional|northern trust|\\bfunds?\\b|asset management|investment")
    private val STIFTUNG = Regex("(?i)\\btrust\\b|foundation|stiftung|\\bfonds\\b.*stiftung")
    private val STAAT = Regex("(?i)government|ministry|\\bstate of\\b|republic|sovereign|kingdom|\\bstate\\b.*\\bfund\\b|\\bland\\b|freistaat")

    fun halterArt(name: String, gesellschaft: Boolean): String = when {
        FONDS.containsMatchIn(name) -> Struktur.Art.FONDS
        STIFTUNG.containsMatchIn(name) -> Struktur.Art.STIFTUNG
        STAAT.containsMatchIn(name) -> Struktur.Art.STAAT
        gesellschaft -> Struktur.Art.HOLDING
        else -> Struktur.Art.PERSON
    }

    // Halter aus der Tabelle "Security Ownership" (DEF 14A, 20-F): Prozent der Stimmen, wenn die Tabelle nur diese nennt
    fun halterAusTabelle(e: IpoProspekt.Eigner, beleg: Int): List<Struktur.Halter> {
        val aus = ArrayList<Struktur.Halter>()
        e.halter.forEach { h ->
            aus += Struktur.Halter(
                name = h.name, art = halterArt(h.name, h.gesellschaft),
                kapital = if (e.stimmrechte) null else h.anteil, stimmen = if (e.stimmrechte) h.anteil else null, beleg = beleg,
            )
        }
        e.gruppe?.let { g ->
            aus += Struktur.Halter(
                name = "", art = Struktur.Art.GRUPPE, kapital = if (e.stimmrechte) null else g, stimmen = if (e.stimmrechte) g else null, beleg = beleg,
            )
        }
        return aus
    }

    private val KLASSE_SPALTE = Regex("(?i)\\bclass\\s*([A-Z])\\b")

    // Halter aus einer Tabelle mit Spalten je Klasse und Spalte der Stimmen: Prozent je Klasse (klassen), Prozent der Gesamtstimmen
    // (stimmen) und, wo eine Spalte "gesamt" vorkommt, Prozent aller Aktien (kapital). null, wenn keine Spalte erkennbar ist
    // (dann gilt der Leser mit einer Prozentspalte, halterAusTabelle).
    fun halterAusSpalten(zeilen: List<IpoProspekt.SpaltenZeile>, gattungen: List<Struktur.Gattung>, beleg: Int): List<Struktur.Halter>? {
        val ids = gattungen.map { it.id }.toSet()
        class Art(val stimmen: Boolean, val klasse: String?, val kapital: Boolean)
        fun art(kopf: String): Art? = when {
            kopf.contains("voting") -> Art(true, null, false)
            KLASSE_SPALTE.find(kopf) != null -> KLASSE_SPALTE.find(kopf)!!.groupValues[1].uppercase().let { id -> if (ids.isEmpty() || id in ids) Art(false, id, false) else null }
            kopf.contains("total") -> Art(false, null, true)
            else -> null
        }
        var erkannt = false
        val aus = zeilen.map { z ->
            var stimmen: Double? = null
            var kapital: Double? = null
            val klassen = LinkedHashMap<String, Double>()
            for ((kopf, p) in z.spalten) {
                val a = art(kopf) ?: continue
                erkannt = true
                when {
                    a.stimmen -> if (stimmen == null) stimmen = p
                    a.klasse != null -> klassen.putIfAbsent(a.klasse, p)
                    a.kapital -> if (kapital == null) kapital = p
                }
            }
            val einzeln = klassen.size == 1
            Struktur.Halter(
                name = z.name, art = if (z.gruppe) Struktur.Art.GRUPPE else halterArt(z.name, z.gesellschaft), kapital = kapital, stimmen = stimmen,
                gattung = if (einzeln) klassen.keys.first() else null, klasse = if (einzeln) klassen.values.first() else null,
                klassen = if (einzeln) emptyMap() else klassen, beleg = beleg,
            )
        }
        return aus.takeIf { erkannt }
    }

    class Meldung13(val name: String, val prozent: Double, val titel: String, val ereignis: String = "", val zweck: String = "")

    private fun feld(x: String, vararg tags: String): String? {
        for (t in tags) Regex("<(?:\\w+:)?$t>\\s*([^<]*?)\\s*</(?:\\w+:)?$t>").find(x)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun text(s: String) = s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").trim()

    // Schedule 13G/13D (XML): Name des ersten Meldenden und sein Anteil an der Gattung; null, wenn nichts lesbar oder Anteil null
    fun meldung13(xml: String): Meldung13? {
        val name = feld(xml, "reportingPersonName")?.let { text(it) } ?: return null
        val p = feld(xml, "classPercent", "percentOfClass")?.replace(",", ".")?.toDoubleOrNull() ?: return null
        if (p <= 0.0 || p > 100.0) return null
        val datum = feld(xml, "dateOfEvent")?.let { d ->
            Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(d)?.let { "${it.groupValues[3]}-${it.groupValues[1]}-${it.groupValues[2]}" }
        }.orEmpty()
        return Meldung13(name, p, feld(xml, "securitiesClassTitle")?.let { text(it) }.orEmpty(), datum, feld(xml, "transactionPurpose")?.let { text(it) }.orEmpty())
    }

    // Treffer der Suche nach 13D/13G: Archivnummer (mit Strichen), Name des Melders, Einreichungstag; je Melder die neueste
    class Treffer13(val adsh: String, val melder: String, val cik: String, val tag: String)

    fun treffer13(suchJson: String, ziel: String, alle: Boolean = false): List<Treffer13> = runCatching {
        val hits = kotlinx.serialization.json.Json.parseToJsonElement(suchJson) as kotlinx.serialization.json.JsonObject
        val liste = ((hits["hits"] as kotlinx.serialization.json.JsonObject)["hits"] as kotlinx.serialization.json.JsonArray)
        val aus = LinkedHashMap<String, Treffer13>()
        for (h in liste) {
            val q = (h as kotlinx.serialization.json.JsonObject)["_source"] as? kotlinx.serialization.json.JsonObject ?: continue
            val ciks = (q["ciks"] as? kotlinx.serialization.json.JsonArray).orEmpty().map { (it as kotlinx.serialization.json.JsonPrimitive).content.trimStart('0') }
            val namen = (q["display_names"] as? kotlinx.serialization.json.JsonArray).orEmpty().map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            val adsh = (q["adsh"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
            val tag = (q["file_date"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val z = ziel.trimStart('0')
            val i = ciks.indexOfFirst { it != z }
            if (i < 0) continue
            val altName = namen.getOrNull(i).orEmpty().replace(Regex("\\s*\\(CIK\\s*\\d+\\)\\s*$"), "").replace(Regex("\\s{2,}\\([A-Z0-9][A-Z0-9, .\\-]*\\)\\s*$"), "").trim()
            val schluessel = if (alle) adsh else ciks[i]
            val alt = aus[schluessel]
            if (alt == null || tag > alt.tag) aus[schluessel] = Treffer13(adsh, de.troetenrepublik.app.market.Edgar13f.lesbar(altName), ciks[i], tag)
        }
        aus.values.sortedByDescending { it.tag }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ Zusammenfuehren

    private fun norm(s: String) = s.uppercase().replace(Regex("[^A-Z0-9]"), "")

    // Passt das Kuerzel oder die ISIN eines Gepflegten zu Symbol und ISIN der Seite? Boersenzusatz (.DE) zaehlt nicht,
    // Klassenzusatz (BRK-B, BRK.B) bleibt gleich.
    fun passt(s: Struktur.Sonderfall, symbol: String, isin: String, cik: String): Boolean {
        val n = norm(symbol)
        if (isin.isNotBlank() && s.isins.any { it.equals(isin, true) }) return true
        if (cik.isNotBlank() && s.ciks.any { it.trimStart('0') == cik.trimStart('0') }) return true
        return s.symbole.any { norm(it) == n }
    }

    fun gepflegt(b: Struktur.Buendel?, symbol: String, isin: String, cik: String): Struktur.Sonderfall? =
        b?.eintraege?.firstOrNull { passt(it, symbol, isin, cik) }

    // Heute (JJJJ-MM-TT) nach dem Ablauf?
    fun abgelaufen(ablauf: String, heute: String): Boolean = ablauf.isNotBlank() && heute > ablauf

    // Gepflegter Eintrag als Grundlage; Aktienzahlen und Hinweise aus den Abrufen fuellen Luecken. Halter des Buendels gehen vor.
    fun zusammen(g: Struktur.Sonderfall?, auto: Struktur.Ergebnis?, heute: String): Struktur.Ergebnis? {
        if (g == null) return auto
        val e = g.eintrag
        val autoG = auto?.gattungen.orEmpty()
        val gattungen = e.gattungen.map { x ->
            val a = autoG.firstOrNull { it.id == x.id }
            x.copy(aktien = x.aktien ?: a?.aktien, kuerzel = x.kuerzel.ifBlank { a?.kuerzel.orEmpty() })
        }.ifEmpty { autoG }
        // Belege des Abrufs hinter die des Buendels, ihre Verweise rutschen um deren Zahl
        val versatz = e.belege.size
        val belege = e.belege + auto?.belege.orEmpty()
        fun v(i: Int) = if (i < 0) i else i + versatz
        val autoHalter = auto?.halter.orEmpty().map { it.copy(beleg = v(it.beleg)) }
        // Aktivisten aus 13D stehen auch bei gepflegten Haltern dabei
        val dazu = autoHalter.filter { h -> h.aktivist && e.halter.none { it.name.equals(h.name, true) } }
        val halter = if (e.halter.isEmpty()) autoHalter else e.halter + dazu
        return e.copy(
            firma = e.firma.ifBlank { auto?.firma.orEmpty() }, gattungen = gattungen, halter = halter, belege = belege,
            flags = (e.flags + auto?.flags.orEmpty()).distinct(), mutter = e.mutter.ifBlank { auto?.mutter.orEmpty() },
            stand = g.stand, gepflegt = true, veraltet = abgelaufen(g.ablauf, heute),
            versammlungen = e.versammlungen.ifEmpty { auto?.versammlungen.orEmpty().map { it.copy(beleg = v(it.beleg)) } },
            aktivisten = e.aktivisten.ifEmpty {
                auto?.aktivisten.orEmpty().map { a -> a.copy(aktuell = a.aktuell.copy(beleg = v(a.aktuell.beleg)), verlauf = a.verlauf.map { it.copy(beleg = v(it.beleg)) }) }
            },
            geprueft = (listOf("BUENDEL") + auto?.geprueft.orEmpty()).distinct(),
        )
    }

    // ------------------------------------------------------------ Anteile berechnen

    // Anteile je Gattung: Kapital (nur Gattungen mit Anspruch, nach wirtschaftlichem Gewicht) und Stimmen. Null, wenn Aktienzahl
    // oder Stimmen irgendeiner beteiligten Gattung fehlen und kein fester Wert gepflegt ist.
    class Verteilung(val kapital: Map<String, Double?>, val stimmen: Map<String, Double?>)

    fun verteilung(gs: List<Struktur.Gattung>): Verteilung {
        val echte = gs.filter { it.art != Struktur.GArt.ADS }
        val kapGattungen = echte.filter { it.wirtschaftlich }
        val kapOk = kapGattungen.isNotEmpty() && kapGattungen.all { it.aktien != null }
        val kapSumme = if (kapOk) kapGattungen.sumOf { (it.aktien ?: 0.0) * it.gewicht } else 0.0
        val kapital = echte.associate { g ->
            g.id to (g.kapitalAnteil ?: when {
                !g.wirtschaftlich -> 0.0
                kapOk && kapSumme > 0 -> (g.aktien ?: 0.0) * g.gewicht * 100.0 / kapSumme
                else -> null
            })
        }
        val festSumme = echte.mapNotNull { it.stimmenAnteil }.sum()
        val offen = echte.filter { it.stimmenAnteil == null }
        // Gattungen ohne Stimme brauchen keine Aktienzahl
        val stimmenOk = offen.all { it.stimmen == 0.0 || (it.aktien != null && it.stimmen != null) }
        val summe = offen.sumOf { (it.aktien ?: 0.0) * (it.stimmen ?: 0.0) }
        val stimmen = echte.associate { g ->
            g.id to (g.stimmenAnteil ?: when {
                !stimmenOk -> null
                // Feste Quoten anderer Klassen nehmen den Rest der Stimmen nicht weg: der Rest verteilt sich auf die offenen
                summe > 0 -> (g.aktien ?: 0.0) * (g.stimmen ?: 0.0) * (100.0 - festSumme).coerceAtLeast(0.0) / summe
                g.stimmen == 0.0 -> 0.0
                else -> null
            })
        }
        return Verteilung(kapital, stimmen)
    }

    // Prozente eines Halters, soweit aus klasse und der Verteilung ableitbar
    fun halterAnteile(h: Struktur.Halter, v: Verteilung): Pair<Double?, Double?> {
        // Mehrere Klassen (Tabelle mit Spalten je Klasse): Summe der Anteile an den Klassen, gewichtet mit der Klasse
        fun ueberKlassen(anteil: Map<String, Double?>): Double? {
            if (h.klassen.isEmpty()) return null
            val teile = h.klassen.mapNotNull { (id, p) -> anteil[id]?.let { p * it / 100.0 } }
            return if (teile.size == h.klassen.size) teile.sum() else null
        }
        val k = h.kapital ?: ueberKlassen(v.kapital) ?: h.klasse?.let { kl -> h.gattung?.let { v.kapital[it] }?.let { kl * it / 100.0 } }
        val s = h.stimmen ?: ueberKlassen(v.stimmen) ?: h.klasse?.let { kl -> h.gattung?.let { v.stimmen[it] }?.let { kl * it / 100.0 } }
        return k to s
    }

    // ------------------------------------------------------------ Rechte des Nutzers

    enum class Board { JA, NEIN_KONTROLLE, NEIN_OHNE_STIMME, UNBEKANNT }

    class Eigen(val stueck: Double, val kapital: Double?, val stimmen: Double?)

    // Rechte der Gattung, die die Seite zeigt. null in einem Feld heisst: nicht geprueft.
    class Rechte(
        val gattung: Struktur.Gattung,
        val stimmenJeAktie: Double?,
        val stimmenAnteil: Double?,
        val kapitalAnteil: Double?,
        val board: Board,
        val kontrolle: String?,
        val ads: Boolean,
        val adsJeAktie: Double?,
        val vie: Boolean,
        val vorzugOhneStimme: Boolean,
        val genuss: Boolean,
        val eigen: Eigen?,
    )

    // Gattung der Seite: Kuerzel oder ISIN passen, sonst die einzige Gattung, sonst die einzige mit Boersenzulassung
    fun gattungDerSeite(e: Struktur.Ergebnis, symbol: String, isin: String): Struktur.Gattung? {
        val n = norm(symbol)
        e.gattungen.firstOrNull { g -> g.symbole.any { norm(it) == n } || (g.kuerzel.isNotBlank() && norm(g.kuerzel) == n) || (isin.isNotBlank() && g.isin.equals(isin, true)) }?.let { return it }
        if (e.gattungen.size == 1) return e.gattungen[0]
        return e.gattungen.filter { it.boerse && it.art != Struktur.GArt.ADS }.singleOrNull()
    }

    // Wer allein oder als gemeldete Gruppe mehr als die Haelfte aller Stimmen hat (null: niemand oder unbekannt)
    fun kontrolleVon(halter: List<Struktur.Halter>, v: Verteilung): String? =
        halter.filter { it.art != Struktur.Art.STREUBESITZ && it.art != Struktur.Art.INSTITUTIONEN }
            .mapNotNull { h -> halterAnteile(h, v).second?.takeIf { it > 50.0 }?.let { h } }
            .maxByOrNull { halterAnteile(it, v).second ?: 0.0 }?.name

    fun rechte(e: Struktur.Ergebnis, symbol: String, isin: String, stueck: Double?): Rechte? {
        val g = gattungDerSeite(e, symbol, isin) ?: return null
        val v = verteilung(e.gattungen)
        val ads = g.art == Struktur.GArt.ADS
        val basis = if (ads) e.gattungen.firstOrNull { it.id == g.basis } else g
        val stimmenJe = if (ads) basis?.stimmen else g.stimmen
        val anteilStimmen = basis?.let { v.stimmen[it.id] }
        val anteilKapital = basis?.let { v.kapital[it.id] }
        val kontrolle = kontrolleVon(e.halter, v)
        val board = when {
            stimmenJe == 0.0 -> Board.NEIN_OHNE_STIMME
            kontrolle != null -> Board.NEIN_KONTROLLE
            stimmenJe == null || anteilStimmen == null && e.halter.none { it.stimmen != null || it.klasse != null } -> Board.UNBEKANNT
            else -> Board.JA
        }
        val eigen = stueck?.takeIf { it > 0 }?.let { st ->
            val einheiten = if (ads) st * (g.ads ?: 1.0) else st
            val aktienBasis = basis?.aktien?.takeIf { it > 0 }
            val kap = if (aktienBasis != null) anteilKapital?.let { it * einheiten / aktienBasis } else null
            val sti = if (aktienBasis != null) anteilStimmen?.let { it * einheiten / aktienBasis } else null
            Eigen(st, kap, sti)
        }
        return Rechte(
            gattung = g, stimmenJeAktie = stimmenJe, stimmenAnteil = anteilStimmen, kapitalAnteil = anteilKapital,
            board = board, kontrolle = kontrolle, ads = ads, adsJeAktie = g.ads, vie = Struktur.Flag.VIE in e.flags,
            vorzugOhneStimme = g.art == Struktur.GArt.VORZUG && g.stimmen == 0.0, genuss = g.art == Struktur.GArt.GENUSS, eigen = eigen,
        )
    }

    // Streubesitz: der Rest, wenn alle genannten Halter eine Zahl haben (Kapital bzw. Stimmen getrennt)
    fun streubesitz(halter: List<Struktur.Halter>, v: Verteilung): Pair<Double?, Double?> {
        // Mit der Sammelzeile der Institutionen (Yahoo) stecken die Fonds darin; sie zaehlen dann nicht doppelt
        val mitInst = halter.any { it.art == Struktur.Art.INSTITUTIONEN }
        val echte = halter.filter {
            it.art != Struktur.Art.STREUBESITZ && it.art != Struktur.Art.GRUPPE && (it.art == Struktur.Art.INSTITUTIONEN || !(mitInst && it.art == Struktur.Art.FONDS))
        }
        if (echte.isEmpty()) return null to null
        val a = echte.map { halterAnteile(it, v) }
        val k = if (a.all { it.first != null }) (100.0 - a.sumOf { it.first ?: 0.0 }).takeIf { it >= 0.05 } else null
        val s = if (a.all { it.second != null }) (100.0 - a.sumOf { it.second ?: 0.0 }).takeIf { it >= 0.05 } else null
        return k to s
    }

    // Verschiedene Prozent (Kapital gegen Stimmen), nur dann stehen beide Werte nebeneinander
    fun weichtAb(kapital: Double?, stimmen: Double?): Boolean = kapital != null && stimmen != null && abs(kapital - stimmen) >= 0.05

    // ------------------------------------------------------------ Wikidata

    class Eigner(val id: String, val prozent: Double?)

    // Eigentuemer (P127) eines Eintrags aus Special:EntityData mit Anteil (P1107, Bruchteil), soweit genannt
    fun wikidataEigner(entityJson: String, id: String): List<Eigner> = runCatching {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(entityJson) as kotlinx.serialization.json.JsonObject
        val e = (root["entities"] as kotlinx.serialization.json.JsonObject)[id] as kotlinx.serialization.json.JsonObject
        val claims = ((e["claims"] as kotlinx.serialization.json.JsonObject)["P127"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        claims.mapNotNull { c ->
            val o = c as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val wert = (((o["mainsnak"] as? kotlinx.serialization.json.JsonObject)?.get("datavalue") as? kotlinx.serialization.json.JsonObject)?.get("value") as? kotlinx.serialization.json.JsonObject)
            val oid = (wert?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
            // Beendete Beteiligungen (Enddatum P582) zaehlen nicht
            val q = o["qualifiers"] as? kotlinx.serialization.json.JsonObject
            if (q?.containsKey("P582") == true) return@mapNotNull null
            val a = (q?.get("P1107") as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? kotlinx.serialization.json.JsonObject
            val betrag = (((a?.get("datavalue") as? kotlinx.serialization.json.JsonObject)?.get("value") as? kotlinx.serialization.json.JsonObject)?.get("amount") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
            Eigner(oid, betrag?.takeIf { it > 0 && it <= 1.0 }?.times(100.0))
        }
    }.getOrDefault(emptyList())
}
