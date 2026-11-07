package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.EuInsider.Geschaeft

// Gemeinsamer Leser fuer das EU-einheitliche Formular nach Art. 19 MAR (seit 5.91): Abschnitte 1 bis 4 mit Name,
// Funktion, Emittent, LEI, Instrument und Kennung, Art des Geschaefts, Preisen und Volumen, Summen, Datum und Ort.
// Die Beschriftungen kommen englisch, deutsch, italienisch, tschechisch, ungarisch, daenisch oder zweisprachig. Der
// Text stammt aus PDFs (PdfText.zeilenNachLage, Tabellenzellen durch Tabulatoren getrennt) oder aus HTML. Daneben
// die Kurzform der nordischen und baltischen Mitteilungen („Name: …“, „Transaction date: …“, „Volume: …; Unit
// price: …“). Kauf und Verkauf zaehlen nur ohne Plan, Option, Zuteilung oder Schenkung; alles andere ist Sonstiges.
object MarFormular {
    // Meldung, aus der das Formular stammt: Kennung, Link, Tag der Veroeffentlichung und Werte fuer fehlende Felder.
    data class Meldung(val id: String, val link: String = "", val publiziert: String = "", val firma: String = "", val isin: String = "", val code: String = "")

    // Zeilen einer PDF-Meldung nach Lage; liefert die Lage nichts, der Text in Stromreihenfolge.
    fun pdfZeilen(pdf: ByteArray): List<String> = PdfText.zeilenNachLage(pdf).ifEmpty { PdfText.zeilen(pdf) }

    // Sichtbarer Text einer Seite als Zeilen, Tabellenzellen einer Zeile durch Tabulatoren getrennt.
    fun htmlZeilen(html: String): List<String> {
        var s = html.replace(Regex("(?is)<(script|style|head)\\b.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)</t[dh]\\s*>"), "\t")
        s = s.replace(Regex("(?i)<(br|/p|/tr|/div|/li|/h\\d|/table)\\b[^>]*>"), "\n")
        s = InsiderQuellen.entitaeten(s.replace(Regex("<[^>]+>"), " "))
        return s.split('\n').map { z ->
            z.split('\t').map { it.replace(Char(0xA0), ' ').replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\t")
        }.filter { it.isNotEmpty() }
    }

    // Ist der Text ein Formular nach Art. 19 MAR (Vorlage oder Kurzform)?
    fun istFormular(zeilen: List<String>): Boolean = istKurzform(zeilen) || zeilen.count { z -> FELDER.any { (f, r) -> f in setOf("ABS1", "ABS4", "ART", "PREISE", "DATUM") && r.containsMatchIn(ohneMarke(z.substringBefore('\t'))) } } >= 3

    // Geschaefte aller Formulare im Text.
    fun lesen(zeilen: List<String>, m: Meldung): List<Geschaeft> = runCatching { if (istKurzform(zeilen)) kurzform(zeilen, m) else vorlage(zeilen, m) }.getOrDefault(emptyList())

    // ------------------------------------------------------------ Woerter

    // Bezeichnung als Muster, das verirrte Leerzeichen zwischen Buchstaben duldet („Na me“, „Aggreg ated“).
    private fun muster(vararg texte: String): Regex = Regex("^(?:" + texte.joinToString("|") { t ->
        t.map { c -> if (c == ' ') "\\s*" else Regex.escape(c.toString()) + "\\s?" }.joinToString("")
    } + ")(?:\\s?\\(?\\d{1,2}\\)?(?![\\d.,/-]))?(?![\\p{L}])", RegexOption.IGNORE_CASE)

    // Abschnitte und Felder der Vorlage in der Reihenfolge der Pruefung (laengere Bezeichnungen zuerst).
    private val FELDER: List<Pair<String, Regex>> = listOf(
        "ABS1" to muster("details of the person discharging", "details of the pdmr", "details of pdmr", "dati relativi alla persona", "angaben zu den personen", "angaben zu der person",
            "a vezetői feladatokat ellátó", "vezetői feladatokat ellátó", "údaje o osobě", "oplysninger om personen",
            "informacija apie vadovaujamas pareigas", "ziņas par personu", "informācija par personu", "upplýsingar um einstaklinginn", "coordonnées de la personne"),
        "ABS2" to muster("reason for the notification", "reason for notification", "motivo della notifica", "grund der meldung", "az értesítés indoka", "důvod oznámení", "årsag til underretningen", "pranešimo priežastis", "paziņojuma iemesls", "paziņojums iemesls", "ástæða tilkynningar", "motif de la notification"),
        "ABS3" to muster("details of the issuer", "dati relativi all'emittente", "angaben zum emittenten", "a kibocsátóra", "údaje o emitentovi", "oplysninger om udstederen", "oplysninger om udsteder",
            "informacija apie emitentą", "ziņas par emitentu", "informācija par emitentu", "upplýsingar um útgefanda", "coordonnées de l'émetteur", "coordonnées de l’émetteur"),
        "ABS4" to muster("details of the transaction(s)", "details of the transactions", "details of the transaction", "dati relativi all'operazione", "angaben zum geschäft", "angaben zu den geschäften",
            "az ügylet(ek) részletei", "az ügyletek részletei", "údaje o obchodu", "údaje o obchodech", "údaje o transakci", "oplysninger om transaktionen", "oplysninger om transaktion",
            "išsami informacija apie sandorį", "ziņas par darījumu", "upplýsingar um viðskiptin", "détails de la transaction", "détail de la transaction"),
        "BESCHREIBUNG" to muster("description of the financial instrument", "description of the financial", "description of the instrument", "description of the", "descrizione dello strumento",
            "beschreibung des finanzinstruments", "beschreibung des finanzinstrument", "a pénzügyi eszköz leírása", "pénzügyi eszköz leírása", "popis finančního nástroje", "beskrivelse af det finansielle instrument", "beskrivelse af",
            "finansinės priemonės rūšis", "finansinės priemonės aprašymas", "finanšu instrumenta apraksts", "lýsing á fjármálagerningnum", "description de l'instrument financier", "description de l’instrument financier"),
        "KENNUNG" to muster("identification code", "codice di identificazione", "kennung", "azonosító kód", "identifikační kód", "identifikationskode", "isin-code", "isin code", "identifikavimo kodas", "identifikācijas kods", "auðkenniskóði", "code d'identification", "code d’identification"),
        "ART" to muster("nature of the transaction", "nature of transaction", "natura dell'operazione", "art des geschäfts", "art des geschäftes", "az ügylet jellege", "povaha obchodu", "povaha transakce",
            "transaktionens art", "arten af transaktionen", "nature of the", "sandorio pobūdis", "darījuma raksturojums", "eðli viðskiptanna", "nature de la transaction"),
        "PREISE" to muster("price(s) and volume(s)", "price(s) and volume", "price(s) and volumes", "prices and volumes", "price and volume", "prezzo/i e volume/i", "prezzo/i e volume", "preis(e) und volumen",
            "ár(ak) és volumen(ek)", "cena (ceny) a objem (objemy)", "cena (ceny) a objem", "pris(er) og mængde(r)", "pris(er) og volumen", "price(s) and", "prices and",
            "kaina ir apimtis", "cena(-as) un apjoms(-i)", "cena (-as) un apjoms (-i)", "verð og magn", "prix et volume(s)", "prix et volume", "prix et volumes"),
        "SUMME" to muster("aggregated information", "aggregate information", "aggregated", "informazioni aggregate", "aggregierte informationen", "összesített információ", "souhrnné informace", "aggregerede oplysninger",
            "apibendrinta informacija", "apkopota informācija", "apkopotā informācija", "verð - samanteknar upplýsingar", "samanlagt magn", "informations agrégées"),
        "DATUM" to muster("date of the transaction", "date of transaction", "data dell'operazione", "datum des geschäfts", "az ügylet dátuma", "datum obchodu", "datum transakce", "dato for transaktionen", "date of the", "sandorio data", "darījuma datums", "viðskiptadagur", "date de la transaction"),
        "ORT" to muster("place of the transaction", "place of transaction", "luogo dell'operazione", "ort des geschäfts", "az ügylet helye", "místo obchodu", "místo transakce", "sted for transaktionen", "place of the", "sandorio vieta", "darījuma vieta", "staður viðskiptanna", "lieu de la transaction"),
        "MELDUNG" to muster("initial notification", "notifica iniziale", "erstmeldung", "első értesítés", "první oznámení", "første underretning", "pirminis pranešimas", "sākotnējais paziņojums", "upphafleg tilkynning", "notification initiale"),
        "POSITION" to muster("position", "posizione", "pozíció", "stellung", "funkce", "postavení", "stilling", "pareigos", "amats", "staða", "fonction / poste", "fonction/poste", "fonction"),
        "LEI" to muster("lei", "auðkenni lögaðila"),
        "NACHNAME" to muster("cognome/last name", "cognome", "last name", "surname", "family name", "nachname", "příjmení", "efternavn"),
        "NAME" to muster("nome/first name", "first name", "full name", "name", "nome", "név", "jméno", "navn", "vorname", "pavadinimas", "vardas", "vārds, uzvārds", "vārds,uzvārds", "nosaukums", "nafn útgefanda", "nafn", "nom", "denominazione"),
    )

    // Woerter, aus denen Bezeichnungen bestehen; ein Stueck nur aus solchen Woertern ist keine Angabe.
    private val VOKABEL = ("transaction transactions transaktion transaktionen instrument instruments type of the financial identification code nature price prices volume volumes " +
        "aggregated aggregate information date place amendment initial notification details person discharging managerial responsibilities status " +
        "position issuer name description and average total la il lo del dei al alla nosaukums uzvārds vārds statuss amats statusas titill tipo di dello strumento finanziario natura operazione prezzo prezzi volumi aggregate informazioni data luogo " +
        "codice identificazione descrizione per i e a modifica notifica iniziale posizione qualifica nome first last emittente " +
        "art des geschäfts preis preise volumen ort datum kennung beschreibung finanzinstruments " +
        "ár ak árak és volumen ek összesített információ az ügylet jellege dátuma helye pénzügyi eszköz leírása eszköz típusa azonosító kód " +
        "cena ceny objem objemy povaha obchodu datum místo popis nástroje souhrnné informace " +
        "pris priser mængde mængder s an by surname isin units operation qualifica occupata denominazione fisiche giuridiche persone persona for natural legal persons " +
        "le che esercitano esercita funzioni funzione amministrazione controllo o direzione strettamente associate associata indicare riguarda una se applicabile " +
        "nominativo ruolo reason motivo della notifica").split(' ').filter { it.isNotEmpty() }.toSet()

    // Aufzaehlungen vor einer Bezeichnung („a)“, „4.“, „c)8“, „—“) und Fussnotenziffern vor Text; Zahlen wie
    // „10.3786“ oder „2026-10-21“ bleiben.
    private fun ohneMarke(s: String): String =
        s.trim().replace(Regex("^(?:(?:[a-hA-H]\\s?\\)|\\(?[a-hA-H]\\)|\\d{1,2}\\s?[.)](?!\\d)|\\d(?=\\s|$)|\\d{1,2}(?=\\s+\\p{L})|[-—–•·]+(?=\\s|\\p{L}|$))\\s*)+"), "").trim()

    private val KURZ = setOf("per", "le", "o", "e", "i", "a", "di", "an", "by", "s", "che", "se", "una", "for", "and", "the", "of", "ak", "ek", "az", "és")

    // Rest hinter einer Bezeichnung ohne fuehrende Bezeichnungswoerter, Satzzeichen und Fussnotenziffern
    // („/ Identification codeIT0003132476“ wird „IT0003132476“, „Qualifica/Position - Status(1) - CFO“ wird „CFO“).
    private fun ohneBezeichnung(rest: String): String {
        var t = rest.trim()
        while (true) {
            val m = Regex("^(?:[/:;,.()\\[\\]\\-–—•]+|\\(?\\d{1,2}\\)?(?![\\d.,/-])|\\(s\\)|([\\p{L}]+))\\s*").find(t) ?: break
            val wort = m.groupValues[1]
            if (wort.isNotEmpty()) {
                // Ein angeklebter Wert nach einem Bezeichnungswort („codeIT0003132476“): nur das Wort abtrennen
                val klein = wort.lowercase()
                val vokabel = VOKABEL.firstOrNull { klein.startsWith(it) && it.length >= 3 && (klein == it || t.length > it.length && t[it.length].isUpperCase()) }
                // Kurze Woerter („Per“, „Le“, „di“) nur vor einem weiteren Bezeichnungswort, sonst koennten sie zum Namen gehoeren
                val naechstes = t.substring(m.range.last + 1).substringBefore(' ').lowercase().trim { !it.isLetter() }
                if (klein in VOKABEL && (klein !in KURZ || (naechstes in VOKABEL && naechstes !in KURZ))) { t = t.substring(m.range.last + 1); continue }
                if (vokabel != null) { t = t.substring(vokabel.length); continue }
                break
            }
            if (m.value.isEmpty()) break
            t = t.substring(m.range.last + 1)
        }
        return t.trim()
    }

    private fun nurVokabel(s: String): Boolean {
        val w = s.lowercase().replace(Regex("\\(\\d+\\)|\\(s\\)|\\(e\\)|/i\\b|\\(er\\)|\\(ek\\)|\\(ak\\)"), " ").split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (w.isEmpty()) return !Regex("\\d").containsMatchIn(s) && !Regex("(?i)n/?a").containsMatchIn(s)
        if (Regex("\\d{3}|\\d[.,]\\d").containsMatchIn(s)) return false
        return w.all { it in VOKABEL }
    }

    // ------------------------------------------------------------ Vorlage

    private class Feld(val art: String, val zeile: Int) {
        val werte = ArrayList<Pair<Int, String>>()   // Zeile und Stueck
        val roh = StringBuilder()                     // Bezeichnung und Werte (fuer die Reihenfolge der Spalten)
        fun text() = werte.joinToString(" ") { it.second }.replace(Regex("\\s+"), " ").trim()
        fun zeilen(): List<List<String>> = werte.groupBy { it.first }.toSortedMap().values.map { l -> l.map { it.second } }
    }

    // Felder in der Reihenfolge des Texts; Werte eines Felds sind alle Stuecke bis zur naechsten Bezeichnung.
    private fun felder(zeilen: List<String>): List<Feld> {
        val aus = ArrayList<Feld>()
        var akt: Feld? = null
        for ((i, z) in zeilen.withIndex()) {
            for (stueckRoh in z.split('\t')) {
                val stueck = stueckRoh.trim()
                if (stueck.isEmpty()) continue
                val ohne = ohneMarke(stueck)
                if (ohne.isEmpty()) continue
                val treffer = FELDER.firstNotNullOfOrNull { (f, r) -> r.find(ohne)?.let { f to it } }
                // Eine Bezeichnung zaehlt nur am Anfang eines Stuecks; ihr Rest („Identification codeIT0003132476“) ist ein Wert
                if (treffer != null && !(treffer.first in setOf("NAME", "POSITION", "LEI", "NACHNAME") && akt?.art == treffer.first && akt.werte.isEmpty() && akt.zeile == i)) {
                    val f = Feld(treffer.first, i)
                    aus += f
                    akt = f
                    f.roh.append(ohne).append(' ')
                    val rest = ohneBezeichnung(ohne.substring(treffer.second.range.last + 1))
                    if (rest.isNotEmpty() && !nurVokabel(rest)) f.werte += i to rest
                    continue
                }
                val f = akt ?: continue
                f.roh.append(stueck).append(' ')
                if (!nurVokabel(ohne)) f.werte += i to stueck.replace(Regex("^\\(\\d\\)\\s*"), "")
            }
        }
        // Mittig gesetzte Werte stehen manchmal eine Zeile ueber ihrer Bezeichnung: ein leeres Feld nimmt den
        // letzten Wert des vorigen Felds, wenn dieser in der Zeile direkt davor steht und das vorige Feld noch weitere hat
        for (k in 1 until aus.size) {
            val f = aus[k]
            val vor = aus[k - 1]
            val text = setOf("NAME", "POSITION", "LEI", "BESCHREIBUNG", "KENNUNG", "ART", "DATUM", "ORT")
            if (f.werte.isEmpty() && f.art in text && vor.art in text && vor.werte.map { it.first }.distinct().size >= 2 && vor.werte.last().first in (f.zeile - 1)..f.zeile)
                f.werte += vor.werte.removeAt(vor.werte.lastIndex)
        }
        return aus
    }

    private class Block {
        var beschreibung = ""; var kennung = ""; var art = ""; var datum = ""; var ort = ""
        var preise: Feld? = null
        val summen = ArrayList<Feld>()
        var text = StringBuilder()
    }

    private fun vorlage(zeilen: List<String>, m: Meldung): List<Geschaeft> {
        val felder = felder(zeilen.map { it.replace(Char(0xA0), ' ') })
        // Formulare: jeder Abschnitt 1 nach einem Abschnitt 4 (oder ein Name nach Geschaeftsangaben) beginnt ein neues
        val formulare = ArrayList<MutableList<Feld>>()
        var gesehen4 = false
        for (f in felder) {
            val neu = formulare.isEmpty() || (f.art == "ABS1" && gesehen4) || (f.art == "NAME" && gesehen4 && formulare.last().none { it.art == "ABS1" } && formulare.last().any { it.art == "DATUM" })
            if (neu) { formulare += mutableListOf<Feld>(); gesehen4 = false }
            if (f.art in setOf("ABS4", "ART", "PREISE", "DATUM")) gesehen4 = true
            formulare.last() += f
        }
        val aus = ArrayList<Geschaeft>()
        for (form in formulare) aus += formular(form, m)
        return aus
    }

    private fun formular(form: List<Feld>, m: Meldung): List<Geschaeft> {
        // Abschnitt eines Felds: der zuletzt gesehene Abschnittskopf; ohne Kopf zaehlt die Reihenfolge der Felder
        var abschnitt = 1
        var person = ""; var nachname = ""; var funktion = ""; var emittent = ""; var grund = ""
        val bloecke = ArrayList<Block>()
        fun block(): Block = bloecke.lastOrNull() ?: Block().also { bloecke += it }
        for (f in form) {
            when (f.art) {
                "ABS1" -> abschnitt = 1
                "ABS2" -> { abschnitt = 2; grund = f.text() }
                "ABS3" -> abschnitt = 3
                "ABS4" -> { abschnitt = 4; if (bloecke.isEmpty() || bloecke.last().art.isNotEmpty() || bloecke.last().datum.isNotEmpty()) bloecke += Block() }
                "NAME" -> {
                    val t = f.text()
                    if (t.isEmpty()) Unit
                    else if (abschnitt <= 2 && person.isEmpty()) person = t
                    else if (abschnitt == 3 || (person.isNotEmpty() && emittent.isEmpty() && abschnitt != 2)) { if (emittent.isEmpty()) emittent = t; abschnitt = 3 }
                }
                "NACHNAME" -> if (abschnitt <= 2 && nachname.isEmpty()) nachname = f.text()
                "POSITION" -> { if (funktion.isEmpty()) funktion = f.text(); abschnitt = maxOf(abschnitt, 2) }
                "LEI" -> abschnitt = maxOf(abschnitt, 3)
                "BESCHREIBUNG" -> {
                    abschnitt = 4
                    if (bloecke.isEmpty() || bloecke.last().art.isNotEmpty()) bloecke += Block()
                    block().beschreibung = f.text()
                }
                "KENNUNG" -> { abschnitt = 4; if (block().kennung.isEmpty()) block().kennung = f.text() }
                "ART" -> { abschnitt = 4; if (block().art.isNotEmpty()) bloecke += Block(); block().art = f.text().ifEmpty { " " } }
                "PREISE" -> { abschnitt = 4; block().preise = f }
                "SUMME" -> { abschnitt = 4; block().summen += f }
                "DATUM" -> { abschnitt = 4; block().datum = f.text() }
                "ORT" -> { abschnitt = 4; block().ort = f.text() }
            }
            if (abschnitt == 4) block().text.append(f.roh).append(' ')
        }
        val name = name("$person $nachname".trim())
        if (name.isEmpty()) return emptyList()
        val funktionText = "$grund $funktion".replace(Regex("(?i)^(?:-\\s*)?"), "").replace(Regex("\\s+"), " ").trim()
        val nahe = Regex("(?i)closely (associated|related|connected)|strettamente (associat|legat)|persona collegata|eng verbunden|szoros kapcsolatban|úzce spojen|osoba blízká|nærtstående|nærstående|related party|glaudžiai susij|cieši saistīt|nákomin|lähedalt seotud").containsMatchIn(funktionText)
        val fuer = Regex("(?i)(?:closely (?:associated|related|connected) (?:to|with)|strettamente (?:associat[oa]|legat[oa]) (?:a|con)|szoros kapcsolatban áll)\\s*(?:mr\\.?|mrs\\.?|ms\\.?|dott\\.?|sig\\.?)?\\s*([\\p{Lu}][\\p{L}'’.-]+(?:\\s+[\\p{Lu}][\\p{L}'’.-]+){1,3})")
            .find(funktionText)?.groupValues?.get(1)?.trim().orEmpty()
        val rolle = InsiderQuellen.rolle(englisch(funktionText), nahe)
        val aus = ArrayList<Geschaeft>()
        var isinVorher = ""
        var beschreibungVorher = ""
        for (b in bloecke) {
            val blockText = b.text.toString()
            val isin = isinIn(b.kennung).ifEmpty { isinIn(b.beschreibung) }.ifEmpty { isinIn(blockText) }.ifEmpty { isinVorher }.ifEmpty { m.isin }
            if (isin.isNotEmpty()) isinVorher = isin
            val beschreibung = b.beschreibung.ifBlank { beschreibungVorher }
            if (beschreibung.isNotBlank()) beschreibungVorher = beschreibung
            // Ein Datum lange vor der Veroeffentlichung (Planbeginn, Geburtstag) ist nicht das des Geschaefts
            val datum = datum(b.datum).ifEmpty { datum(blockText) }
                .takeIf { d -> m.publiziert.length != 10 || d >= java.time.LocalDate.parse(m.publiziert).minusDays(400).toString() }.orEmpty()
            val (n, preis, w) = betraege(b, isin)
            if (datum.isEmpty() || n <= 0) continue
            val aktie = istAktie(beschreibung) || (!keineAktie(beschreibung) && Regex("(?i)\\bshares?\\b|\\bazioni\\b|\\bakci[eí]\\b|részvény|\\baktie").containsMatchIn(b.art))
            val art = if (!aktie) "S" else art(b.art, blockText)
            val wert = if (art == "S") 0.0 else n * preis
            aus += Geschaeft(
                e = emittent.ifBlank { m.firma }.let { kurz(it) }, isin = isin, p = name, fuer = fuer, r = rolle, t = funktionText.take(160),
                i = if (aktie) "Aktie" else beschreibung.take(60), art = art, d = datum, v = m.publiziert,
                n = n, preis = if (art == "S") 0.0 else preis, wert = wert, w = w.ifEmpty { waehrungZurIsin(isin) },
                o = b.ort.take(80), id = m.id, q = m.link, c = m.code,
            )
        }
        return aus
    }

    // ------------------------------------------------------------ Preise und Mengen

    private val ZAHL = Regex("(?<![\\p{L}\\d])(?:[A-Z]{3}\\s?|[€$£]\\s?)?\\d[\\d.,' ]*\\d(?:\\s?(?:[A-Z]{3}|€|%))?|(?<![\\p{L}\\d.,])\\d(?![\\d.,])(?:\\s?(?:[A-Z]{3}|€))?")
    private val DATUMSMUSTER = Regex("\\b\\d{4}\\s?-\\s?\\d{1,2}\\s?-\\s?\\d{1,2}\\b|\\b\\d{1,2}\\s?[./]\\s?\\d{1,2}\\s?[./]\\s?\\d{2,4}\\b|\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b|\\b\\d{4}\\.\\s?\\d{1,2}\\.\\s?\\d{1,2}\\.?")

    // Zahl mit Waehrung; pence fuer Londoner Preise in Pence („512.3p“, „GBX 512.3“).
    private class Token(val roh: String, val waehrung: String, val pence: Boolean = false) {
        val ziffern = roh.replace(Regex("[^0-9.,]"), "")
        val prozent = roh.contains('%')
    }

    // Zahlen eines Stuecks ohne Datumsangaben, Uhrzeiten und Fussnoten („(6)“).
    private fun tokens(s: String): List<Token> {
        val t = s.replace(Regex("\\(\\d\\)"), " ").replace(DATUMSMUSTER, " ")
            // zerfallene Zahlen („105 .095 ,01112“)
            .replace(Regex("(?<=\\d) ?([.,]) ?(?=\\d)"), "$1")
        return ZAHL.findAll(t).flatMap { r ->
            // Leerzeichen trennt Spalten, ausser bei Tausendergruppen („1 000“)
            val pence = r.value.contains("GBX") || Regex("^\\s?(?:p|pence)(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(t.substring(r.range.last + 1))
            val w = if (pence) "GBP" else Regex("[A-Z]{3}|€").find(r.value)?.value?.replace("€", "EUR").orEmpty()
            r.value.trim().split(Regex(" (?!\\d{3}(?!\\d))")).mapNotNull { x -> x.trim().takeIf { Regex("\\d").containsMatchIn(it) }?.let { Token(it, w, pence) } }
        }.toList()
    }

    // Dezimalkomma im Text: „27,1500“, „1.891.898,40“, „339,23“; Punkt bei „339.23“, „1,891,898.40“.
    private fun dezimalKomma(texte: List<String>): Boolean? {
        var komma = 0; var punkt = 0
        for (t in texte) {
            komma += Regex("\\d\\.\\d{3},\\d|(?<![\\d.,])\\d+,\\d{1,2}(?![\\d.,])|\\d,\\d{4,}(?![\\d.,])").findAll(t).count()
            punkt += Regex("\\d,\\d{3}\\.\\d|(?<![\\d.,])\\d+\\.\\d{1,2}(?![\\d.,])|\\d\\.\\d{4,}(?![\\d.,])").findAll(t).count()
        }
        return when { komma > punkt -> true; punkt > komma -> false; else -> null }
    }

    private fun wert(z: String, komma: Boolean?, ganz: Boolean = false): Double? {
        var s = z.replace(Regex("[^0-9.,]"), "")
        if (s.isEmpty()) return null
        if (ganz) return s.replace(Regex("[.,](?=\\d{3}(?!\\d))"), "").replace(",", ".").toDoubleOrNull()
        val k = s.lastIndexOf(','); val p = s.lastIndexOf('.')
        s = when {
            k >= 0 && p >= 0 -> if (k > p) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            k >= 0 -> if (komma == true || s.count { it == ',' } == 1 && s.length - k - 1 != 3) s.replace(',', '.') else s.replace(",", "")
            p >= 0 && s.count { it == '.' } > 1 -> s.replace(".", "")
            p >= 0 && komma == true && s.length - p - 1 == 3 -> s.replace(".", "")
            else -> s
        }
        return s.toDoubleOrNull()
    }

    // Stueckzahl, Durchschnittspreis und Waehrung eines Geschaefts: Paare aus Preis und Menge in c), geprueft an d).
    private fun betraege(b: Block, isin: String): Triple<Double, Double, String> {
        val pv = b.preise?.zeilen().orEmpty()
        val sz = b.summen.flatMap { it.zeilen() }
        val alles = (pv + sz).flatten()
        val komma = dezimalKomma(alles)
        // Spaltenfolge aus den Ueberschriften: Menge zuerst, wenn „Volume“ vor „Price“ steht
        val kopf = (b.preise?.roh?.toString().orEmpty()).lowercase()
        val iv = Regex("volum|mængde|objem|menge").find(kopf)?.range?.first ?: Int.MAX_VALUE
        val ip = Regex("pri[cs]e|prezz|preis|\\bár|cena|pris").find(kopf)?.range?.first ?: Int.MAX_VALUE
        val mengeZuerst = iv < ip
        val waehrung = (if (pv.flatten().flatMap { tokens(it) }.any { it.pence }) "GBP" else null) ?: alles.firstNotNullOfOrNull { s -> Regex("\\b(EUR|DKK|ISK|SEK|NOK|CZK|HUF|PLN|USD|GBP|CHF|RON|BGN)\\b|€").find(s)?.value?.replace("€", "EUR") }.orEmpty()
        fun preisartig(t: Token) = t.waehrung.isNotEmpty() || Regex("[.,]\\d{1,2}$|[.,]\\d{4,}$").containsMatchIn(t.ziffern)
        val paare = ArrayList<Pair<Token, Token>>()
        val einzeln = ArrayList<Token>()
        for (z in pv) {
            val t = z.flatMap { tokens(it) }.filter { !it.prozent || Regex("(?i)%").containsMatchIn(kopf) }
            when {
                t.size == 2 -> {
                    val (a, c) = t
                    paare += when {
                        preisartig(a) && !preisartig(c) -> a to c
                        preisartig(c) && !preisartig(a) -> c to a
                        mengeZuerst -> c to a
                        else -> a to c
                    }
                }
                t.size == 1 -> einzeln += t[0]
            }
        }
        // Einzelne Zahlen untereinander: abwechselnd Preis und Menge, oder erst alle Preise, dann alle Mengen
        if (paare.isEmpty() && einzeln.size >= 2) {
            val p = einzeln.filter { preisartig(it) }; val v = einzeln.filter { !preisartig(it) }
            if (p.size == v.size) paare += p.zip(v)
            else einzeln.chunked(2).filter { it.size == 2 }.forEach { (a, c) -> paare += if (mengeZuerst) c to a else a to c }
        }
        // Mengen mit Tausenderpunkt („5.000“) zeigen ein Dezimalkomma bei den Preisen („10,400“), mit Tausenderkomma umgekehrt
        val kommaPreis = komma ?: when {
            paare.any { Regex("\\d\\.\\d{3}(?!\\d)").containsMatchIn(it.second.ziffern) && !it.second.ziffern.contains(',') } -> true
            paare.any { Regex("\\d,\\d{3}(?!\\d)").containsMatchIn(it.second.ziffern) && !it.second.ziffern.contains('.') } -> false
            else -> null
        }
        var n = 0.0; var summe = 0.0
        for ((p, v) in paare) {
            val menge = wert(v.roh, komma, ganz = true) ?: continue
            val preis = (wert(p.roh, kommaPreis) ?: 0.0).let { if (p.pence) it / 100 else it }
            if (menge <= 0) continue
            n += menge; summe += menge * preis
        }
        // Summenabschnitt: Menge und Betrag. Nennt er die Menge aus c), gelten die Paare; sonst gilt er, und der Betrag
        // ist Durchschnittspreis oder Gesamtbetrag, je nachdem, was naeher am Durchschnitt der Paare liegt (ohne Paare:
        // Gesamtbetrag, wenn er als Preis je Aktie unglaubwuerdig hoch waere)
        val st = sz.flatten().flatMap { tokens(it) }.filter { !it.prozent }
        if (st.isNotEmpty()) {
            val mitN = n > 0 && st.any { t -> (wert(t.roh, komma, ganz = true) ?: -1.0).let { kotlin.math.abs(it - n) <= maxOf(0.5, n * 0.001) } }
            val v = st.firstOrNull { !preisartig(it) }
            val vN = v?.let { wert(it.roh, komma, ganz = true) } ?: 0.0
            if (!mitN && v != null && vN > 0) {
                val x = st.firstOrNull { it !== v }?.let { wert(it.roh, kommaPreis) } ?: 0.0
                val schnitt = if (n > 0) summe / n else 0.0
                val preisS = when {
                    x <= 0 -> schnitt
                    schnitt > 0 -> if (kotlin.math.abs(x / vN - schnitt) < kotlin.math.abs(x - schnitt)) x / vN else x
                    x > 20_000 && x > vN -> x / vN
                    else -> x
                }
                n = vN
                summe = preisS * vN
            }
        }
        val preis = if (n > 0) summe / n else 0.0
        val w = waehrung.ifEmpty { waehrungZurIsin(isin) }
        // Unglaubwuerdige Preise (verrutschte Spalten) gelten als nicht lesbar
        val glaubhaft = preis > 0 && preis < 1_000_000 && n * preis < 2e10
        return Triple(n, if (glaubhaft) preis else 0.0, w)
    }

    // ------------------------------------------------------------ Begriffe

    // Fremdsprachige Begriffe auf englische abbilden, damit die Regeln aus InsiderQuellen gelten.
    private val BEGRIFFE = listOf(
        "acquisto|acquisizione" to "purchase", "compravendita" to "purchase", "cessione|vendita|alienazione|cession" to "sale", "achat" to "purchase", "souscription" to "subscription", "attribution gratuite|attribution" to "grant", "sottoscrizione" to "subscription", "assegnazione|attribuzione" to "grant",
        "esercizio" to "exercise", "gratuit[aoie]" to "free", "piano" to "plan", "nákup" to "purchase", "nabytí" to "acquisition", "prodej" to "sale", "pozbytí|zcizení" to "disposal",
        "úpis" to "subscription", "opce|opcí" to "option", "vételi|vétel|vásárlás" to "purchase", "eladási|eladás|értékesítés" to "sale", "jegyzés" to "subscription", "opció" to "option",
        "juttatás" to "grant", "erwerb" to "acquisition", "veräußerung" to "disposal", "verkauf" to "sale", "kauf" to "purchase", "zeichnung" to "subscription", "zuteilung" to "grant", "ausübung" to "exercise",
        "køb|købt" to "purchase", "salg|solgt" to "sale", "tildeling" to "grant", "udnyttelse" to "exercise", "hankinta" to "acquisition", "luovutus" to "disposal", "merkintä" to "subscription",
        "förvärv" to "acquisition", "avyttring" to "disposal", "teckning" to "subscription",
        "pirkimas|pirkimo|įsigijimas|pirkšana|iegāde|kaup|ost" to "purchase", "pardavimas|pardavimo|perleidimas|pārdošana|atsavināšana|sala|müük" to "sale",
        "parakstīšanās|pasirašymas|märkimine" to "subscription",
        "valdybos narys|valdes loceklis|juhatuse liige" to "management board", "generalinis direktorius|valdes priekšsēdētājs|forstjóri|juhatuse esimees" to "chief executive",
        "stebėtojų taryb\\w*|padomes\\s+\\w+|nõukogu\\s+\\w+" to "supervisory", "stjórnarformaður" to "chairman", "stjórnarmaður" to "board member",
        "amministratore delegato|direttore generale" to "chief executive", "presidente" to "chairman", "vice ?presidente" to "board member", "consigliere|amministratore" to "board member",
        "sindaco|collegio sindacale" to "supervisory", "dirigente con responsabilità strategiche|dirigente" to "manager", "předseda představenstva|generální ředitel" to "chief executive",
        "finanční ředitel" to "chief financial", "člen představenstva" to "management board", "dozorčí rad[ay]" to "supervisory", "vezérigazgató" to "chief executive",
        "gazdasági igazgató|pénzügyi igazgató" to "chief financial", "igazgatóság" to "board member", "felügyelőbizottság" to "supervisory", "vorstand\\p{L}*|\\p{L}*vorstands\\p{L}*" to "management board",
        "aufsichtsrat\\p{L}*|\\p{L}*aufsichtsrats\\p{L}*" to "supervisory", "adm\\. direktør|administrerende direktør" to "chief executive", "bestyrelse" to "board member", "direktion" to "management board",
        "toimitusjohtaja" to "chief executive", "talousjohtaja" to "chief financial", "hallitu[sk]" to "board member", "verkställande direktör" to "chief executive", "styrelse" to "board member",
    ).map { (a, b) -> Regex("(?i)(?<![\\p{L}])(?:$a)(?![\\p{L}])") to b }

    fun englisch(s: String): String = BEGRIFFE.fold(s.lowercase()) { t, (r, e) -> r.replace(t, e) }

    // Anleihen, Optionen, Fonds und Aehnliches nach der Beschreibung.
    private val KEINE_AKTIE = Regex("obbligazion|bond|kötvény|dluhopis|obligation|obligacij|obligācij|skuldabréf|võlakiri|notes\\b|warrant|opzion|opció|opce|option|diritt|derivat|certificat|fondo|fund|\\bunits?\\b|bevis|optioner|strumento di debito|debt instrument")

    private fun keineAktie(beschreibung: String) = KEINE_AKTIE.containsMatchIn(beschreibung.lowercase())

    // Aktie nach der Beschreibung des Instruments, in allen Sprachen der Vorlage.
    fun istAktie(beschreibung: String): Boolean {
        val s = beschreibung.lowercase()
        if (keineAktie(s)) return false
        return Regex("share|azion[ei]|akci[eí]|részvény|aktie|aktier|osake|stock|equity|ordinary|depositary|\\bsdr\\b|\\bgdr\\b|\\badr\\b|action|akcij|aktsia|hlutabréf").containsMatchIn(s) || InsiderQuellen.istAktie(s)
    }

    // Art des Geschaefts: Kauf oder Verkauf nur ohne Plan, Option, Zuteilung, Schenkung oder Erbe; die Frage nach
    // Art. 19 Abs. 6 Buchst. e (Zusammenhang mit Aktienoptionsprogrammen) zaehlt nur mit der Antwort Ja.
    fun art(natur: String, umfeld: String = ""): String {
        val frage = Regex("(?is)(a norma dell'articolo 19|pursuant to article 19\\s*\\(?6\\)?|gemäß artikel 19|az 596/2014/eu rendelet 19).{0,400}?(programme|programma|programm|program)\\b\\s*([\\p{L}]*)")
        val antwort = frage.find(umfeld)?.groupValues?.get(3)?.lowercase().orEmpty()
        if (antwort in setOf("si", "sì", "yes", "ja", "igen", "ano")) return "S"
        // Verkauf in ein Rueckkaufprogramm der Firma bleibt ein Verkauf
        val t = englisch(frage.replace(natur, " ")).lowercase().replace(Regex("buy[- ]?back program\\w*|share repurchase program\\w*|tilbagekøbsprogram\\w*"), "rkp")
        if (Regex("gift|donation|dono|donazione|schenkung|ajándék|darování|\\bdar\\b|gave|inherit|eredità|successione|erbschaft|öröklés|dědictví|arv\\b|pledge|pegno|zálog|zástav|lending|prestito|conversion|conversione|split|bonus|dividend|scrip").containsMatchIn(t)) return "S"
        if (Regex("option|stock option|warrant|vesting|incentive|share-based|matching|performance|restricted|\\bplan\\b|program|exercise|grant|award|allotment|subscription|free|receipt").containsMatchIn(t)) return "S"
        return InsiderQuellen.art(t)
    }

    // Pruefziffer einer ISIN (Luhn ueber die Ziffernfolge, Buchstaben A=10 bis Z=35).
    fun isinGueltig(isin: String): Boolean {
        if (!Regex("^[A-Z]{2}[A-Z0-9]{9}\\d$").matches(isin)) return false
        val ziffern = isin.map { if (it.isDigit()) it.toString() else (it - 'A' + 10).toString() }.joinToString("")
        var summe = 0
        for ((k, c) in ziffern.reversed().withIndex()) {
            var d = c - '0'
            if (k % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            summe += d
        }
        return summe % 10 == 0
    }

    // Erste gueltige ISIN im Text, auch angeklebt („Identification codeIT0003132476“) oder mit Leerzeichen („XS 3503273719“).
    fun isinIn(s: String): String {
        val t = s.uppercase().replace(Regex("\\b([A-Z]{2}) (\\d{10})\\b"), "$1$2")
        for (i in 0..t.length - 12) {
            if (i + 12 < t.length && t[i + 12].isLetterOrDigit()) continue
            val k = t.substring(i, i + 12)
            if (isinGueltig(k) && k.count { it.isDigit() } >= 5) return k
        }
        return ""
    }

    // Datum als JJJJ-MM-TT; auch mit zerfallenen Ziffern („202 6 -1 0 -23“) und ungarisch („2026.10.20.“).
    fun datum(s: String): String {
        val t = s.replace(Char(0xA0), ' ')
        Regex("(\\d{4})\\.\\s?(\\d{1,2})\\.\\s?(\\d{1,2})").find(t)?.let { m ->
            return "%s-%02d-%02d".format(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt()).takeIf { plausibel(it) }.orEmpty()
        }
        InsiderQuellen.tag(t).takeIf { plausibel(it) }?.let { return it }
        val dicht = t.replace(Regex("(?<=\\d)\\s+(?=[\\d./-])|(?<=[./-])\\s+(?=\\d)"), "")
        return InsiderQuellen.tag(dicht).takeIf { plausibel(it) }.orEmpty()
    }

    private fun plausibel(d: String) = d.length == 10 && d >= "2000-01-01" && d <= java.time.LocalDate.now().plusDays(2).toString() &&
        runCatching { java.time.LocalDate.parse(d) }.isSuccess

    // Name ohne Zusaetze („(purchase via …)“), bei Gesellschaften ohne Sitz und Register.
    private fun name(s: String): String {
        var t = s.replace(Regex("\\s+"), " ").replace(Regex("\\([^)]*\\)"), " ").replace(Regex("\\s+"), " ").trim().trim(',', ';', ':', '-')
        if (t.length > 50) t = t.substringBefore(", ").substringBefore(" having ").trim()
        // „Nachname, Vorname“ aus der Kurzform
        Regex("^([\\p{L}'’-]+(?: [\\p{L}'’-]+)?), ([\\p{L}'’-]+(?: [\\p{L}'’-]+)?)$").find(t)?.let { m ->
            if (!Regex("(?i)\\b(a/s|as|ab|oy|oyj|sia|uab|ltd|plc|s\\.?p\\.?a|gmbh|ag|se|nv|bv|inc)\\b").containsMatchIn(t)) t = "${m.groupValues[2]} ${m.groupValues[1]}"
        }
        return InsiderQuellen.ganz(t).take(80)
    }

    private fun kurz(s: String) = s.replace(Regex("\\s+"), " ").replace(Regex("\\s*\\((?:trading symbol|ticker)[^)]*\\)", RegexOption.IGNORE_CASE), "").trim().take(80)

    // Waehrung nach dem Land der ISIN, wenn die Meldung keine nennt.
    fun waehrungZurIsin(isin: String) = when (isin.take(2)) {
        "DK" -> "DKK"; "IS" -> "ISK"; "CZ" -> "CZK"; "HU" -> "HUF"; "SE" -> "SEK"; "NO" -> "NOK"; "PL" -> "PLN"; "GB" -> "GBP"; "CH" -> "CHF"; "LI" -> "CHF"; "RO" -> "RON"; "US" -> "USD"
        else -> "EUR"
    }

    // ------------------------------------------------------------ Kurzform (Nasdaq Nordic und Baltikum)

    private val K_NAME = Regex("(?i)^(?:name|nimi|namn|nam|nimi/nimetus)\\s*:\\s*(.+)$")
    private val K_POSITION = Regex("(?i)^(?:position|asema|befattning|ametikoht)\\s*:\\s*(.+)$")
    private val K_EMITTENT = Regex("(?i)^(?:issuer|liikkeeseenlaskija|emittent)\\s*:\\s*(.+)$")
    private val K_DATUM = Regex("(?i)^(?:transaction date|date of (?:the )?transaction|liiketoimen päivämäärä|transaktionsdatum|transaktionens datum|tehingu kuupäev)\\s*:\\s*(.+)$")
    private val K_ORT = Regex("(?i)^(?:venue|place of (?:the )?transaction|kauppapaikka|handelsplats|tehingu koht)\\s*:\\s*(.+)$|^(outside a trading venue|kauppapaikan ulkopuolella|utanför en handelsplats)$")
    private val K_TYP = Regex("(?i)^(?:instrument type|instrumenttityyppi|instrumentin tyyppi|typ av instrument|instrument typ|instrumenttyp|instrument)\\s*:\\s*(.+)$")
    private val K_ISIN = Regex("(?i)^isin\\s*:\\s*([A-Z]{2}[A-Z0-9]{9}\\d)")
    private val K_ART = Regex("(?i)^(?:nature of (?:the )?transaction|liiketoimen luonne|transaktionens art|transaktionens karaktär|tehingu olemus)\\s*:\\s*(.+)$")
    private val K_MENGE = Regex("(?i)(?:volume|volyymi|volym|kogus)\\s*:\\s*([\\d][\\d ,.]*)")
    private const val K_PREISWORT = "unit price|yksikköhinta|enhetspris|volume[- ]weighted average price|average price|keskihinta|volymvägt genomsnittspris|medelpris|keskmine hind|hind|price"
    private val K_SUMME = Regex("(?i)^(?:aggregated transactions|liiketoimien yhdistetyt tiedot|aggregerade transaktioner|aggregerad information)")
    private val K_TRENNER = Regex("^[-_=–—]{8,}$")
    // Beginn einer meldenden Person; „Person … in Issuer“ (bei Nahestehenden) leitet die Fuehrungsperson dahinter ein
    private val K_PERSON = Regex("(?i)^person subject to the notification|^ilmoitusvelvollinen|^den anmälningsskyldiga|^anmälningsskyldig|^person discharging managerial responsibilities\\s*/\\s*person closely associated")
    private val K_FUEHRUNG = Regex("(?i)person discharging managerial responsibilities|johtotehtävissä toimiva henkilö|person i ledande ställning")

    fun istKurzform(zeilen: List<String>): Boolean = zeilen.any { K_DATUM.containsMatchIn(it.trim()) } && zeilen.any { K_MENGE.containsMatchIn(it) }

    // Abschnitte zwischen Trennlinien; Angaben zur Person und zum Emittenten gelten weiter, bis neue kommen. Ein
    // Abschnitt mit mehreren Daten enthaelt je Datum ein Geschaeft.
    private fun kurzform(zeilen: List<String>, m: Meldung): List<Geschaeft> {
        val z = zeilen.flatMap { it.split('\t') }.map { it.trim() }.filter { it.isNotEmpty() }
        val abschnitte = ArrayList<MutableList<String>>().apply { add(mutableListOf()) }
        for (s in z) if (K_TRENNER.matches(s)) abschnitte += mutableListOf<String>() else abschnitte.last() += s
        val aus = ArrayList<Geschaeft>()
        // Dezimalkomma nach der ganzen Mitteilung („7,3“ entscheidet auch ueber „7,485“)
        val komma = dezimalKomma(z)
        var person = ""; var funktion = ""; var fuer = ""; var fuerFunktion = ""; var emittent = ""; var isinAlle = ""; var typAlle = ""
        for (a in abschnitte) {
            var inFuehrung = false
            for (s in a) {
                when {
                    K_PERSON.containsMatchIn(s) -> { person = ""; funktion = ""; fuer = ""; fuerFunktion = ""; inFuehrung = false }
                    K_FUEHRUNG.containsMatchIn(s) && K_NAME.find(s) == null -> inFuehrung = true
                    K_NAME.find(s) != null -> { val v = K_NAME.find(s)!!.groupValues[1].trim(); if (inFuehrung) { if (fuer.isEmpty()) fuer = v } else { person = v; funktion = "" } }
                    K_POSITION.find(s) != null -> { val v = K_POSITION.find(s)!!.groupValues[1].trim(); if (inFuehrung) { if (fuerFunktion.isEmpty()) fuerFunktion = v } else funktion = v }
                    K_EMITTENT.find(s) != null -> emittent = K_EMITTENT.find(s)!!.groupValues[1].trim()
                }
            }
            val daten = a.indices.filter { K_DATUM.containsMatchIn(a[it]) }
            if (daten.isEmpty()) {
                // Angaben vor dem ersten Geschaeft (Emittent, ISIN, Instrument) gelten fuer alle
                a.firstNotNullOfOrNull { K_ISIN.find(it)?.groupValues?.get(1) }?.let { isinAlle = it }
                a.firstNotNullOfOrNull { K_TYP.find(it)?.groupValues?.get(1) }?.let { typAlle = it }
                continue
            }
            val teile = if (daten.size == 1) listOf(a.toList()) else daten.mapIndexed { k, i -> a.subList(if (k == 0) 0 else i, daten.getOrNull(k + 1) ?: a.size) }
            for (block in teile) kurzGeschaeft(block, m, person, funktion, fuer, fuerFunktion, emittent, isinAlle, typAlle, komma)?.let { aus += it }
        }
        return aus
    }

    private fun kurzGeschaeft(block: List<String>, m: Meldung, person: String, funktion: String, fuer: String, fuerFunktion: String, emittent: String, isinAlle: String, typAlle: String, kommaText: Boolean?): Geschaeft? {
        val tag = block.firstNotNullOfOrNull { K_DATUM.find(it)?.groupValues?.get(1) }?.let { datum(it) }.orEmpty()
        val ort = block.firstNotNullOfOrNull { b -> K_ORT.find(b)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } }.orEmpty()
        val typ = block.firstNotNullOfOrNull { K_TYP.find(it)?.groupValues?.get(1) } ?: typAlle
        val isin = block.firstNotNullOfOrNull { K_ISIN.find(it)?.groupValues?.get(1) } ?: isinAlle.ifEmpty { m.isin }
        val natur = block.firstNotNullOfOrNull { K_ART.find(it)?.groupValues?.get(1) }.orEmpty()
        // Einzelne Geschaefte und die Summe; die Summe gilt, wenn vorhanden
        val summeAb = block.indexOfFirst { K_SUMME.containsMatchIn(it) }
        val komma = dezimalKomma(block) ?: kommaText
        fun paare(l: List<String>): List<Pair<Double, Double?>> =
            Regex("(?i)(?:volume|volyymi|volym|kogus)\\s*:\\s*([\\d][\\d ,.]*\\d|\\d)[;,]?\\s*(?:\\(?\\d\\)?\\s*:?\\s*)?(?:$K_PREISWORT)\\s*:\\s*(?:[A-Z]{3}\\s?|€\\s?)?([\\d][\\d ,.]*\\d|\\d|n/a)")
                .findAll(l.joinToString(" ")).mapNotNull { r ->
                    val v = wert(r.groupValues[1].replace(" ", ""), null, ganz = true) ?: return@mapNotNull null
                    v to r.groupValues[2].takeIf { !it.equals("n/a", true) }?.let { wert(it.replace(" ", ""), komma) }
                }.toList()
        val einzeln = paare(if (summeAb >= 0) block.subList(0, summeAb) else block)
        val summe = if (summeAb >= 0) paare(block.subList(summeAb, block.size)) else emptyList()
        val liste = summe.ifEmpty { einzeln }
        val n = liste.sumOf { it.first }
        val betrag = liste.sumOf { it.first * (it.second ?: 0.0) }
        val w = Regex("(?i)(?:$K_PREISWORT)\\s*:\\s*(?:([A-Z]{3})\\s?)?[\\d][\\d ,.]*\\s*([A-Z]{3}(?![\\p{L}])|€)?").findAll(block.joinToString(" "))
            .map { it.groupValues[1].ifEmpty { it.groupValues[2] }.replace("€", "EUR") }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val aktie = typ.isBlank() || istAktie(englisch(typ)) || Regex("(?i)osake|aktie").containsMatchIn(typ)
        val art = if (!aktie) "S" else art(natur)
        val nahe = Regex("(?i)closely associated|lähipiiri|närstående|lähedalt seotud|legal entity related|related to").containsMatchIn(funktion)
        if (tag.isEmpty() || n <= 0 || person.isBlank()) return null
        // Bei Nahestehenden ohne eigenen Abschnitt steht die Fuehrungsperson in der Funktion („related to Priit Vaikmaa, board member“)
        val fuerName = fuer.ifBlank { Regex("(?i)related to\\s+([\\p{Lu}][\\p{L}'’-]+(?:\\s+[\\p{Lu}][\\p{L}'’-]+){1,3})").find(funktion)?.groupValues?.get(1).orEmpty() }
        return Geschaeft(
            e = kurz(emittent.ifBlank { m.firma }), isin = isin, p = name(person), fuer = if (nahe) name(fuerName) else "",
            r = InsiderQuellen.rolle(englisch(if (nahe) "$funktion $fuerFunktion" else funktion), nahe), t = (if (nahe && fuerFunktion.isNotBlank()) "$funktion, $fuerFunktion" else funktion).take(160),
            i = if (aktie) "Aktie" else typ.take(60), art = art, d = tag, v = m.publiziert, n = n,
            preis = if (art == "S" || n <= 0) 0.0 else betrag / n, wert = if (art == "S") 0.0 else betrag,
            w = w.ifEmpty { waehrungZurIsin(isin) }, o = ort.take(80), id = m.id, q = m.link, c = m.code,
        )
    }
}
