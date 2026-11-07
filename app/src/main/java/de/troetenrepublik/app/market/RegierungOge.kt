package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Wertpapiergeschaefte weiterer Mitglieder der US-Regierung (seit 5.91): Minister, Gouverneure der Fed und
// Spitzenbeamte melden Kaeufe und Verkaeufe auf dem Formular OGE 278-T. Die OGE verlinkt die Meldungen der vom Senat
// bestaetigten Aemter, das Weisse Haus die seiner Mitarbeiter. Beide Stellen geben Text-PDFs aus Integrity.gov aus.
// Die Berichte fallen unter 5 U.S.C. 13107 (keine kommerzielle Nutzung): die App holt Liste und PDFs nur auf dem
// Geraet und fuer den eigenen Gebrauch, im Programm liegt davon nichts. Der Praesident hat seine eigene Seite
// (Politik, mitgelieferter Stand) und fehlt hier.
object RegierungOge {
    // Meldungen ab dem Beginn der Amtszeit.
    const val SEIT = "2025-01-20"

    // Suche der OGE nach verlinkten 278-T („Transaction</a>“ trifft nur Eintraege mit PDF); hoechstens 100 je Seite.
    fun ogeListe(start: Int) =
        "https://extapps2.oge.gov/201/Presiden.nsf/API.xsp/v3/rest?draw=1&start=$start&length=100&search%5Bvalue%5D=Transaction%3C%2Fa%3E"

    const val WEISSES_HAUS = Politik.WEISSES_HAUS

    // Ein Eintrag einer Liste: Name wie „Kupor, Scott A“, Behoerde, Amt, Tag der Aufnahme, PDF und Quelle (OGE oder WH).
    data class Eintrag(val name: String, val behoerde: String, val amt: String, val tag: String, val pdf: String, val quelle: String)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // Seite der OGE-Liste: Gesamtzahl der Treffer und die verlinkten 278-T seit SEIT, ohne den Praesidenten.
    fun ogeListeLesen(text: String): Pair<Int, List<Eintrag>> = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val gesamt = o["recordsFiltered"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val liste = o["data"]?.jsonArray.orEmpty().mapNotNull { e ->
            val m = e.jsonObject
            fun s(k: String) = m[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            val typ = s("type")
            val pdf = Regex("href='([^']+\\.pdf)'", RegexOption.IGNORE_CASE).find(typ)?.groupValues?.get(1) ?: return@mapNotNull null
            if (!typ.replace(Regex("<[^>]+>"), "").contains("Transaction", true)) return@mapNotNull null
            val name = s("name").trim()
            val tag = s("docDate").take(10)
            if (tag < SEIT || praesident(name)) return@mapNotNull null
            Eintrag(name, s("agency").trim(), s("title").trim(), tag, pdf, "OGE")
        }
        gesamt to liste
    }.getOrDefault(0 to emptyList())

    private fun praesident(name: String) = name.startsWith("Trump, Donald", true)

    // Transaktionsmeldungen der Mitarbeiter auf whitehouse.gov („Borg-Jared-Periodic-Transaction-Report-05.08.25.pdf“),
    // ohne die eingescannten Meldungen des Praesidenten. Name und Tag aus dem Dateinamen; das PDF nennt sie genauer.
    fun weissesHausLesen(html: String): List<Eintrag> =
        Regex("href=\"([^\"]+\\.pdf)\"", RegexOption.IGNORE_CASE).findAll(html).map { InsiderQuellen.entitaeten(it.groupValues[1]) }
            .filter { it.contains("Periodic-Transaction-Report", true) && !it.contains("President", true) && !it.contains("Trump", true) }
            .distinct().mapNotNull { url ->
                val datei = java.net.URLDecoder.decode(url.substringAfterLast('/').replace("+", "%2B"), "UTF-8")
                val vorne = datei.substringBefore("-Periodic-Transaction-Report", "").ifBlank { return@mapNotNull null }
                val teile = vorne.split('-').filter { it.isNotBlank() }
                if (teile.size < 2) return@mapNotNull null
                val tag = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})").findAll(datei).lastOrNull()?.destructured?.let { (m, t, j) ->
                    val jahr = if (j.length == 2) "20$j" else j
                    "$jahr-${m.padStart(2, '0')}-${t.padStart(2, '0')}"
                }.orEmpty()
                Eintrag(teile.first() + ", " + teile.drop(1).joinToString(" "), "White House", "", tag, url, "WH")
            }.toList()

    // ------------------------------------------------------------ Stand auf dem Geraet

    @Serializable
    data class Meldung(
        val pdf: String,
        // Tag der Aufnahme in die Liste und Quelle (OGE, WH).
        val tag: String,
        val q: String = "OGE",
        // Gelesen (auch ohne Geschaefte, etwa bei Fonds ohne Kuerzel) und Tag der Unterschrift.
        val gelesen: Boolean = false,
        val unterschrieben: String = "",
        val geschaefte: List<Politik.G> = emptyList(),
    )

    @Serializable
    data class Person(
        val id: String,
        val name: String,
        val amt: String = "",
        val behoerde: String = "",
        val meldungen: List<Meldung> = emptyList(),
        // Verlinkte Vermoegensmeldungen (OGE 278e, seit 5.92), neueste zuerst.
        val vermoegen: List<VMeldung> = emptyList(),
    ) {
        val letzte: String get() = meldungen.maxOfOrNull { it.tag }.orEmpty()
        val offen: Int get() = meldungen.count { !it.gelesen }
    }

    // vAbgerufen: letzter Abruf der Listen der Vermoegensmeldungen (seit 5.92).
    @Serializable
    data class Datei(val abgerufen: Long = 0L, val personen: List<Person> = emptyList(), val vAbgerufen: Long = 0L)

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // Kennung einer Person aus Nach- und erstem Vornamen („kupor-scott“), gleich fuer OGE und Weisses Haus.
    fun kennung(name: String): String {
        val nach = name.substringBefore(',').trim()
        val vor = name.substringAfter(',', "").trim().split(' ').firstOrNull().orEmpty()
        return listOf(nach, vor).filter { it.isNotBlank() }.joinToString("-") { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
    }

    // „Kupor, Scott A“ als „Scott Kupor“; einzelne Buchstaben (zweite Vornamen) fallen weg.
    fun anzeige(name: String): String {
        if (!name.contains(',')) return name.trim()
        val vor = name.substringAfter(',').trim().split(' ').filter { it.length > 1 || !it[0].isLetter() }.joinToString(" ").trimEnd('.')
        return (vor + " " + name.substringBefore(',').trim()).trim()
    }

    // Neue Listeneintraege einarbeiten: jede PDF einmal, gelesene Meldungen bleiben; Amt und Behoerde nach dem
    // neuesten Eintrag der OGE.
    fun einarbeiten(alt: Datei, eintraege: List<Eintrag>, jetzt: Long): Datei {
        val personen = LinkedHashMap<String, Person>()
        alt.personen.forEach { personen[it.id] = it }
        for (e in eintraege.sortedBy { it.tag }) {
            val id = kennung(e.name)
            if (id.isBlank()) continue
            val p = personen[id] ?: Person(id, anzeige(e.name))
            val bekannt = p.meldungen.any { it.pdf == e.pdf }
            val neu = p.copy(
                amt = if (e.quelle == "OGE" || p.amt.isBlank()) e.amt.ifBlank { p.amt } else p.amt,
                behoerde = if (e.quelle == "OGE" || p.behoerde.isBlank()) e.behoerde.ifBlank { p.behoerde } else p.behoerde,
                meldungen = if (bekannt) p.meldungen else p.meldungen + Meldung(e.pdf, e.tag, e.quelle),
            )
            personen[id] = neu
        }
        return alt.copy(abgerufen = jetzt, personen = personen.values.map { p -> p.copy(meldungen = p.meldungen.sortedByDescending { it.tag }) }.sortedByDescending { it.letzte })
    }

    // Gelesene Meldung eintragen; aus dem PDF auch Amt (Weisses Haus) und Unterschrift.
    fun eintragen(d: Datei, id: String, pdf: String, b: Bericht?): Datei = d.copy(
        personen = d.personen.map { p ->
            if (p.id != id) p else p.copy(
                amt = p.amt.ifBlank { b?.amt.orEmpty() },
                meldungen = p.meldungen.map { m ->
                    if (m.pdf != pdf) m else m.copy(gelesen = true, unterschrieben = b?.unterschrieben.orEmpty(), geschaefte = b?.geschaefte.orEmpty())
                },
            )
        },
    )

    // ------------------------------------------------------------ 278-T lesen

    data class Bericht(val name: String, val amt: String, val unterschrieben: String, val geschaefte: List<Politik.G>)

    private val DATUM = Regex("(\\d{2})/(\\d{2})/(\\d{4})")
    // Art allein in der Zelle oder vor dem Tag; „Exchange Income Corp.“ ist eine Beschreibung
    private val ART = Regex("^(Purchase|Sale|Exchange)(\\s*\\(([^)]*)\\))?(?=\\s*$|\\s+\\d{2}/\\d{2}/\\d{4})", RegexOption.IGNORE_CASE)
    private val BETRAG = Regex("^(\\$|Over\\b|None\\b|Spouse\\b)", RegexOption.IGNORE_CASE)
    private val ENDE = Regex("^(PART|Endnotes|Summary of Contents|Privacy Act Statement)$|.+ - Page \\d+$")
    private val KEIN_KUERZEL = setOf("IRA", "LLC", "LP", "USD", "ETF", "REIT", "ADR", "SP", "JT", "DC", "INC", "CORP", "LTD", "NA", "II", "III", "IV", "ROTH")

    private fun iso(t: String) = DATUM.find(t)?.destructured?.let { (m, d, j) -> "$j-$m-$d" }.orEmpty()

    // Betragsspanne der OGE („$1,001 - $15,000“, „Over $50,000,000“); null, wenn keine Spanne erkennbar ist.
    fun spanne(text: String): Pair<Long, Long?>? {
        val t = text.replace(Regex("\\s+"), " ")
        Regex("(?i)over\\s*\\$\\s?([\\d,]+)").find(t)?.let { m -> return (m.groupValues[1].replace(",", "").toLong() + 1) to null }
        val z = Regex("\\$\\s?([\\d,]+)").findAll(t).mapNotNull { it.groupValues[1].replace(",", "").toLongOrNull() }.toList()
        return when {
            z.size >= 2 -> z[0] to z[1]
            z.size == 1 && Regex("-\\s*$").containsMatchIn(t.trim()) -> z[0] to null
            z.size == 1 -> z[0] to z[0]
            else -> null
        }
    }

    // Boersenkuerzel aus der Beschreibung: in Klammern am Ende („Okta, Inc. (OKTA)“) oder vorn („SPY (S&P 500 ETF)“).
    fun kuerzel(beschreibung: String): String? {
        val b = beschreibung.trim()
        Regex("\\(([A-Z][A-Z0-9]{0,5}(?:[.\\-][A-Z])?)\\)\\s*$").find(b)?.groupValues?.get(1)?.takeIf { it !in KEIN_KUERZEL }?.let { return it }
        Regex("^([A-Z]{1,5})\\s+\\(").find(b)?.groupValues?.get(1)?.takeIf { it !in KEIN_KUERZEL }?.let { return it }
        return null
    }

    // Art des Werts als Kuerzel wie beim Repraesentantenhaus: Fonds und ETFs EF, Anleihen GS oder CS, mit Kuerzel ST.
    private fun typ(beschreibung: String, kuerzel: String?): String {
        val b = beschreibung.lowercase()
        return when {
            Regex("\\betf\\b|\\bfund\\b|index|spdr|ishares|vanguard").containsMatchIn(b) -> "EF"
            Regex("treasury|t-bill|\\bbond\\b|\\bnotes?\\b due").containsMatchIn(b) -> if (b.contains("treasury") || b.contains("t-bill")) "GS" else "CS"
            Regex("\\boption\\b|\\bcall\\b|\\bput\\b").containsMatchIn(b) && kuerzel != null -> "OP"
            kuerzel != null -> "ST"
            else -> "OT"
        }
    }

    // Tabelle „Transactions“ eines 278-T aus Integrity.gov: je Zeile Nummer, Beschreibung, Art, Tag, Hinweis auf spaete
    // Benachrichtigung und Spanne. Die Zellen kommen je nach PDF einzeln oder teils zusammen („04/10/2025 No“); eine
    // Zeile beginnt mit ihrer laufenden Nummer, Kopf und Fusszeilen trennen die Seiten.
    fun berichtLesen(zeilen: List<String>): Bericht? {
        val z = zeilen.map { it.replace(Char(0xA0), ' ').replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        if (z.none { it.contains("278-T") || it.contains("Periodic Transaction Report", true) }) return null
        val info = z.indexOfFirst { it.startsWith("Filer's Information", true) || it.startsWith("Filer’s Information", true) }
        val name = z.getOrNull(info + 1).takeIf { info >= 0 }.orEmpty()
        val amt = z.getOrNull(info + 2).takeIf { info >= 0 && it?.startsWith("Electronic Signature") == false }.orEmpty()
            .substringBefore(", Trump-Vance").substringBefore(" - White House").trim()
        val unterschrieben = z.firstOrNull { it.contains("electronically signed on", true) }?.let { iso(it) }.orEmpty()
        // Tabellenbereiche: nach der Kopfzeile „AMOUNT“ bis zum Seitenende oder den Endnoten
        val bereich = ArrayList<String>()
        var drin = false
        for (s in z) {
            if (s == "AMOUNT" || s.endsWith(" AMOUNT")) { drin = true; continue }
            if (!drin) continue
            if (ENDE.matches(s) || s.length > 120) { drin = false; continue }
            bereich += s
        }
        // In Zeilen zerlegen: eine Zeile beginnt mit der naechsten laufenden Nummer
        val reihen = ArrayList<MutableList<String>>()
        var naechste = 1
        for (s in bereich) {
            val m = Regex("^(\\d{1,3})(?:\\s+(.+))?$").find(s)
            if (m != null && m.groupValues[1].toInt() == naechste) {
                reihen += mutableListOf(m.groupValues[2])
                naechste++
            } else reihen.lastOrNull()?.add(s)
        }
        val geschaefte = reihen.mapNotNull { r ->
            var art: String? = null
            var tag = ""
            val beschreibung = ArrayList<String>()
            val betrag = ArrayList<String>()
            for (s in r.filter { it.isNotBlank() }) {
                val a = ART.find(s)
                when {
                    a != null -> {
                        if (art == null) art = when {
                            a.groupValues[1].equals("Purchase", true) -> "K"
                            a.groupValues[1].equals("Exchange", true) -> "X"
                            a.groupValues[3].contains("partial", true) -> "T"
                            else -> "V"
                        }
                        if (tag.isEmpty()) tag = iso(s)
                        val rest = s.substring(a.range.last + 1).replace(DATUM, "").replace(Regex("\\b(Yes|No)\\b"), "").trim()
                        if (BETRAG.containsMatchIn(rest)) betrag += rest
                    }
                    DATUM.containsMatchIn(s) && s.startsWith(DATUM.find(s)!!.value) -> {
                        if (tag.isEmpty()) tag = iso(s)
                        val rest = s.replace(DATUM, "").replace(Regex("\\b(Yes|No)\\b"), "").trim()
                        if (BETRAG.containsMatchIn(rest)) betrag += rest
                    }
                    BETRAG.containsMatchIn(s) -> betrag += s
                    s == "Yes" || s == "No" || s.equals("See Endnote", true) -> {}
                    else -> beschreibung += s.replace(Regex("(?i)\\s*See Endnote\\s*"), " ").trim()
                }
            }
            val b = beschreibung.filter { it.isNotBlank() }.joinToString(" ").trim()
            val (von, bis) = spanne(betrag.joinToString(" ")) ?: return@mapNotNull null
            if (art == null || tag.isEmpty() || b.isEmpty()) return@mapNotNull null
            val k = kuerzel(b)
            Politik.G(d = tag, k = k, n = b, t = typ(b, k), a = art!!, von = von, bis = bis)
        }
        return Bericht(name, amt, unterschrieben, geschaefte)
    }

    // ------------------------------------------------------------ Vermoegen (seit 5.92)

    // Vermoegensmeldung (OGE 278e) einer Person: Jahresmeldung (J), Antritt (A, „Nominee“ und „New Entrant“) oder
    // Abschluss (E, „Termination“). Gelesen mit den Summen der Wertspannen wie beim Praesidenten (vermoegen.py):
    // Vermoegen aus den Teilen 2, 5 und 6, Verbindlichkeiten aus Teil 8.
    @Serializable
    data class VMeldung(
        val pdf: String,
        // Tag der Aufnahme in die Liste (bei whitehouse.gov der Monat des Hochladens) und Quelle (OGE, WH).
        val tag: String,
        val q: String = "OGE",
        val art: String = "J",
        val gelesen: Boolean = false,
        val unterschrieben: String = "",
        val jahr: Int = 0,
        val av: Long = 0,
        val ab: Long = 0,
        val ao: Boolean = false,
        val sv: Long = 0,
        val sb: Long = 0,
        val so: Boolean = false,
        // Zahl der Vermoegenswerte und der Verbindlichkeiten mit Spanne.
        val z: Int = 0,
        val n: Int = 0,
    ) {
        val hatWerte: Boolean get() = gelesen && z > 0
        val stand: String get() = unterschrieben.ifBlank { tag }
    }

    // Ein Listeneintrag einer Vermoegensmeldung.
    data class VEintrag(val name: String, val tag: String, val pdf: String, val art: String, val quelle: String)

    // Suchbegriffe der OGE fuer verlinkte Vermoegensmeldungen: Jahresmeldungen seit 2025, Antritt und Abschluss.
    fun ogeVermoegenSuchen(jahr: Int): List<String> = (2025..jahr + 1).map { "Annual ($it)</a>" } + listOf("Nominee 278</a>", "Termination</a>")

    fun ogeSuche(begriff: String, start: Int) =
        "https://extapps2.oge.gov/201/Presiden.nsf/API.xsp/v3/rest?draw=1&start=$start&length=100&search%5Bvalue%5D=" +
            java.net.URLEncoder.encode(begriff, "UTF-8").replace("+", "%20")

    private fun artAus(text: String): String? = when {
        text.contains("Termination", true) -> "E"
        text.contains("Annual", true) -> "J"
        text.contains("Nominee", true) || text.contains("New Entrant", true) -> "A"
        else -> null
    }

    // Seite einer Suche nach Vermoegensmeldungen: Gesamtzahl und die verlinkten Meldungen seit SEIT, ohne den Praesidenten.
    fun ogeVermoegenLesen(text: String): Pair<Int, List<VEintrag>> = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val gesamt = o["recordsFiltered"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val liste = o["data"]?.jsonArray.orEmpty().mapNotNull { e ->
            val m = e.jsonObject
            fun s(k: String) = m[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            val typ = s("type")
            val pdf = Regex("href='([^']+\\.pdf)'", RegexOption.IGNORE_CASE).find(typ)?.groupValues?.get(1) ?: return@mapNotNull null
            // Nur die verlinkte Beschriftung zaehlt („Annual (2026)</a> (Amended …)“)
            val art = artAus(Regex(">([^<]*)</a>").find(typ)?.groupValues?.get(1).orEmpty()) ?: return@mapNotNull null
            val name = s("name").trim()
            val tag = s("docDate").take(10)
            if (tag < SEIT || praesident(name)) return@mapNotNull null
            VEintrag(name, tag, pdf, art, "OGE")
        }
        gesamt to liste
    }.getOrDefault(0 to emptyList())

    private val WH_PFAD = Regex("/uploads/(\\d{4})/(\\d{2})/")
    private val WH_KEIN_NAME = Regex("(?i)President|Trump|Vance|Memo|Waiver|Agreement|Certif|Letter|Report-to-Congress|Periodic")

    // Vermoegensmeldungen der Mitarbeiter auf whitehouse.gov: „Borg-Jared-2026-Annual.pdf“ (Jahresmeldung),
    // „Boyle-Daniel-Termination.pdf“ (Abschluss) und „Adams-Patrick.pdf“ (Antritt). Tag ist der Monat des Hochladens;
    // die Art steht genauer im PDF.
    fun weissesHausVermoegen(html: String): List<VEintrag> =
        Regex("href=\"([^\"]+\\.pdf)\"", RegexOption.IGNORE_CASE).findAll(html).map { InsiderQuellen.entitaeten(it.groupValues[1]) }
            .distinct().mapNotNull { url ->
                val datei = runCatching { java.net.URLDecoder.decode(url.substringAfterLast('/').replace("+", "%2B"), "UTF-8") }.getOrNull()
                    ?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: return@mapNotNull null
                if (WH_KEIN_NAME.containsMatchIn(datei)) return@mapNotNull null
                val art = when {
                    datei.contains("Termination", true) -> "E"
                    datei.contains("Annual", true) -> "J"
                    else -> "A"
                }
                val vorne = datei.replace(Regex("(?i)-Termination(-Report)?.*$"), "").replace(Regex("(?i)-\\d{4}-Annual.*$"), "")
                    .replace(Regex("-\\d{1,2}$"), "")
                if (!Regex("^[A-Za-z'’.]+(-[A-Za-z'’.]+){1,3}$").matches(vorne)) return@mapNotNull null
                val teile = vorne.split('-')
                val tag = WH_PFAD.find(url)?.destructured?.let { (j, m) -> "$j-$m-01" } ?: return@mapNotNull null
                if (tag < SEIT.take(7) + "-01") return@mapNotNull null
                VEintrag(teile.first() + ", " + teile.drop(1).joinToString(" "), tag, url, art, "WH")
            }.toList()

    // Vermoegensmeldungen den Personen der Liste zuordnen (gleiche Kennung); gelesene bleiben, neueste zuerst.
    // Personen ohne 278-T bekommen keinen eigenen Eintrag.
    fun vermoegenEinarbeiten(d: Datei, eintraege: List<VEintrag>): Datei {
        val nachPerson = eintraege.groupBy { kennung(it.name) }
        return d.copy(
            personen = d.personen.map { p ->
                val neu = nachPerson[p.id].orEmpty().filter { e -> p.vermoegen.none { it.pdf == e.pdf } }
                if (neu.isEmpty()) p
                else p.copy(vermoegen = (p.vermoegen + neu.distinctBy { it.pdf }.map { VMeldung(it.pdf, it.tag, it.quelle, it.art) }).sortedByDescending { it.tag })
            },
        )
    }

    // Naechste zu lesende Vermoegensmeldung einer Person: die neueste ungelesene vor der ersten mit Werten. Ein
    // gescanntes oder leeres PDF ueberspringt so zur naechstaelteren.
    fun vermoegenOffen(p: Person): VMeldung? = p.vermoegen.sortedByDescending { it.tag }.takeWhile { !it.hatWerte }.firstOrNull { !it.gelesen }

    // Neueste gelesene Vermoegensmeldung mit Werten; null ohne oeffentliche Meldung.
    fun vermoegenAktuell(p: Person): VMeldung? = p.vermoegen.sortedByDescending { it.tag }.firstOrNull { it.hatWerte }

    // Gelesene Vermoegensmeldung eintragen; ohne Bericht (kein 278e, kein Text) bleibt sie gelesen ohne Werte.
    fun vermoegenEintragen(d: Datei, id: String, pdf: String, b: VBericht?): Datei = d.copy(
        personen = d.personen.map { p ->
            if (p.id != id) p else p.copy(
                vermoegen = p.vermoegen.map { m ->
                    if (m.pdf != pdf) m else if (b == null) m.copy(gelesen = true) else m.copy(
                        gelesen = true, art = b.art.ifBlank { m.art }, unterschrieben = b.unterschrieben, jahr = b.jahr,
                        av = b.av, ab = b.ab, ao = b.ao, sv = b.sv, sb = b.sb, so = b.so, z = b.z, n = b.n,
                    )
                },
            )
        },
    )

    // Spanne einer gelesenen Meldung im Format der Jahresmeldungen (Vermoegen.E), fuer die gemeinsame Anzeige.
    fun alsSpanne(p: Person, m: VMeldung): Vermoegen.E =
        Vermoegen.E(m.pdf, p.name, "", "", m.jahr, m.av, m.ab, m.ao, m.sv, m.sb, m.so, m.z)

    data class VBericht(
        val art: String, val jahr: Int, val unterschrieben: String,
        val av: Long, val ab: Long, val ao: Boolean, val sv: Long, val sb: Long, val so: Boolean, val z: Int, val n: Int,
    )

    private val REIHE = Regex("^(\\d{1,3}(?:\\.\\d{1,3})*)(?:\\s+(.*))?$")
    // Wert hinter der Spalte EIF (Yes, No, N/A); Einkuenfte stehen erst dahinter und haben eigene Spannen.
    private val WERT = Regex("\\b(?:Yes|No|N/A)\\s+(?:See Endnote\\s+)?(\\$\\s?[\\d,]+\\s*-\\s*\\$\\s?[\\d,]+|(?:Spouse/DC\\s+)?Over\\s+\\$\\s?[\\d,]+)", RegexOption.IGNORE_CASE)
    private val SCHULD = Regex("(\\$\\s?[\\d,]+\\s*-\\s*\\$\\s?[\\d,]+|Over\\s+\\$\\s?[\\d,]+)", RegexOption.IGNORE_CASE)
    private val SEITE = Regex(".+ - Page \\d+$")
    // Hypotheken auf das selbst genutzte Haus: das Haus selbst steht nicht in der Meldung, der Kredit zaehlt deshalb nicht.
    private val EIGENHEIM = Regex("(?i)(personal|primary) residence")

    // Moegliche naechste Zeilennummer einer Tabelle nach akt („1.4“ -> 1.4.1, 1.5, 2).
    private fun naechste(akt: String?): Set<String> {
        if (akt == null) return setOf("1")
        val t = akt.split('.').map { it.toInt() }
        val aus = hashSetOf("$akt.1")
        for (i in t.size downTo 1) aus += (t.take(i - 1) + (t[i - 1] + 1)).joinToString(".")
        return aus
    }

    // Vermoegensmeldung (OGE Form 278e) aus Integrity.gov lesen. Ueberschriften und Tabellen stehen je Seite getrennt,
    // die Tabellen erkennt der Leser deshalb an ihrem Kopf: „DESCRIPTION EIF VALUE INCOME TYPE …“ bei Vermoegens-
    // werten (Teile 2, 5 und 6), „CREDITOR NAME TYPE AMOUNT …“ bei Verbindlichkeiten (Teil 8). Eine Zeile beginnt mit
    // ihrer Nummer (1, 1.1, 2 …), ein Kopf auf der naechsten Seite setzt die Tabelle fort. Je Vermoegenswert zaehlt die
    // Spanne direkt hinter der Spalte EIF; Werte ohne Spanne („value not readily ascertainable“) fehlen. Abbezahlte
    // Kredite und Hypotheken auf das eigene Haus zaehlen nicht. null, wenn das PDF keine 278e ist.
    fun vermoegenLesen(zeilen: List<String>): VBericht? {
        val z = zeilen.map { it.replace(Char(0xA0), ' ').replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        if (z.none { it.contains("278e") }) return null
        val art = z.firstOrNull { it.startsWith("Report Type:", true) }?.let { artAus(it) }.orEmpty()
        val unterschrieben = z.firstOrNull { it.contains("electronically signed on", true) }?.let { iso(it) }.orEmpty()
        val jahr = z.firstNotNullOfOrNull { Regex("^(?:Report Year|Year \\(Annual Report only\\)):\\s*(\\d{4})").find(it)?.groupValues?.get(1)?.toInt() }
            ?: unterschrieben.take(4).toIntOrNull() ?: 0
        // Tabellenzeilen sammeln: Art der Tabelle (A Vermoegen, S Verbindlichkeiten, X sonst), Nummer, Text
        val reihen = ArrayList<Triple<Char, String, StringBuilder>>()
        var tabelle: Char? = null
        var kopf: StringBuilder? = null
        var akt: String? = null
        for (s in z) {
            if (s == "Summary of Contents" || s == "Privacy Act Statement") break
            if (SEITE.matches(s)) continue
            if (s == "#" || s.startsWith("# ")) { kopf = StringBuilder(s.drop(1)); tabelle = null; continue }
            val m = REIHE.find(s)
            if (kopf != null) {
                if (m == null || (m.groupValues[1] != "1" && m.groupValues[1] !in naechste(akt))) { kopf.append(' ').append(s); continue }
                val k = kopf.toString().uppercase()
                tabelle = when {
                    k.contains("EIF") && k.contains("VALUE") -> 'A'
                    k.contains("CREDITOR") -> 'S'
                    else -> 'X'
                }
                kopf = null
                // „1“ beginnt eine neue Tabelle, sonst geht die vorige auf der neuen Seite weiter
                if (m.groupValues[1] !in naechste(akt)) akt = null
            }
            val t = tabelle ?: continue
            if (m != null && m.groupValues[1] in naechste(akt)) {
                akt = m.groupValues[1]
                reihen += Triple(t, akt, StringBuilder(m.groupValues[2]))
            } else reihen.lastOrNull()?.third?.append(' ')?.append(s)
        }
        var av = 0L; var ab = 0L; var ao = false; var anzahl = 0
        var sv = 0L; var sb = 0L; var so = false; var schulden = 0
        for ((t, _, text) in reihen) {
            val zeile = text.toString()
            when (t) {
                'A' -> {
                    val w = WERT.find(zeile) ?: continue
                    val (von, bis) = spanne(w.groupValues[1]) ?: continue
                    av += von; ab += bis ?: von; ao = ao || bis == null; anzahl++
                }
                'S' -> {
                    if (zeile.contains("paid off", true) || EIGENHEIM.containsMatchIn(zeile)) continue
                    val w = SCHULD.find(zeile) ?: continue
                    val (von, bis) = spanne(w.groupValues[1]) ?: continue
                    sv += von; sb += bis ?: von; so = so || bis == null; schulden++
                }
            }
        }
        return VBericht(art, jahr, unterschrieben, av, ab, ao, sv, sb, so, anzahl, schulden)
    }

    // ------------------------------------------------------------ Darstellung

    // Meldungen einer Person im Format der Seiten des Kongresses; ungelesene haben noch keine Geschaefte (null).
    fun daten(p: Person): List<Pair<Kongress.Meldung, List<Kongress.Geschaeft>?>> =
        p.meldungen.mapIndexed { i, m ->
            val datum = Politik.tag(m.tag)
            Kongress.Meldung("o-${p.id}-$i", p.name, "", "", "", datum, m.tag.take(4).toIntOrNull() ?: 0) to
                if (m.gelesen) m.geschaefte.map { Politik.geschaeft(it, datum) } else null
        }.sortedByDescending { it.first.datum }

    // Alle gelesenen Geschaefte einer Person.
    fun geschaefte(p: Person): List<Kongress.Geschaeft> = daten(p).flatMap { it.second.orEmpty() }
}
