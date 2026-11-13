package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.AchtK.Art
import de.troetenrepublik.app.market.AchtK.Detail
import de.troetenrepublik.app.market.AchtK.Ereignis
import de.troetenrepublik.app.market.AchtK.Grund
import de.troetenrepublik.app.market.AchtK.Unterart

// Angaben aus dem Text einer 8-K [0.77]: Der Abschnitt des Items wird herausgeschnitten und an festen Merkmalen
// erkannt (Art der Notierungsmeldung und ihr Grund, Frist, Abgang mit Name, Pruefer, Zeitraeume einer Korrektur,
// Insolvenzkapitel, Abschreibungsbetrag). Was sich nicht sicher erkennen laesst, bleibt leer. Reine Funktionen.
object AchtKDetail {
    private const val MONATSNAMEN = "January|February|March|April|May|June|July|August|September|October|November|December"
    private val MONATE = MONATSNAMEN.lowercase().split('|')
    private const val DATUM_TEIL = "(?:$MONATSNAMEN)\\s+\\d{1,2},\\s+\\d{4}"

    private fun iso(monat: String, tag: String, jahr: String): String =
        jahr + "-" + (MONATE.indexOf(monat.lowercase()) + 1).toString().padStart(2, '0') + "-" + tag.padStart(2, '0')

    private val TITEL_301 = Regex("(?i)item\\s+3\\.01\\.?\\s+notice of delisting or failure to satisfy a continued listing rule or standard;\\s*transfer of listing\\.?")
    private val TITEL_401 = Regex("(?i)item\\s+4\\.01\\.?\\s+changes (?:in|to) registrant'?s certifying accountant\\.?")
    private val TITEL_402 = Regex("(?i)item\\s+4\\.02\\.?(?:\\([a-z]\\))?\\s+non-reliance on previously issued financial statements or a related audit report or completed interim review\\.?")
    private val TITEL_103 = Regex("(?i)item\\s+1\\.03\\.?\\s+bankruptcy or receivership\\.?")
    private val TITEL_206 = Regex("(?i)item\\s+2\\.06\\.?\\s+material impairments\\.?")

    private fun abschnitt(html: String, item: String, titel: Regex): String = AchtK.abschnitt(AchtK.klartext(html), item).replace(titel, " ")

    // Angaben zur Art der Meldung; null bei Arten ohne Angaben (2.04).
    fun lesen(art: Art, html: String, emittent: String): Detail? = when (art) {
        Art.DELISTING -> notierung(abschnitt(html, "3.01", TITEL_301))
        Art.PRUEFER -> pruefer(abschnitt(html, "4.01", TITEL_401))
        Art.KORREKTUR -> korrektur(abschnitt(html, "4.02", TITEL_402))
        Art.INSOLVENZ -> Detail(kapitel = Regex("(?i)chapter\\s+(7|11|13|15)\\b").find(abschnitt(html, "1.03", TITEL_103))?.groupValues?.get(1)?.toInt() ?: 0)
        Art.ABSCHREIBUNG -> Detail(betrag = betrag(abschnitt(html, "2.06", TITEL_206)))
        Art.FUEHRUNG -> fuehrung(html, emittent)
        Art.VERBINDLICHKEIT -> null
    }

    // ------------------------------------------------------------ 3.01 Notierung

    private val ENTWARNUNG = Regex(
        "(?i)\\bnow (?:complies|in compliance)\\b|regained compliance|compliance (?:with [^.]{0,80}? )?(?:has been|was) (?:regained|restored)|" +
            "matter is (?:now )?closed|(?:deficiency|non-?compliance) (?:has been|was) (?:cured|resolved|remedied)",
    )
    private val MANGEL = Regex("(?i)not in compliance|non-?compliance|deficien|fail(?:ed|ure) to (?:meet|satisfy|comply)|below the minimum|no longer in compliance")
    private val WECHSEL_GENEHMIGT = Regex("(?i)approv\\w+[^.]{0,250}transfer|transfer[^.]{0,250}\\b(?:has|have) been approved")
    private val WECHSEL_FREIWILLIG = Regex("(?i)voluntar\\w+ (?:delist|transfer|withdraw)")
    private val WECHSEL_PLAN = Regex("(?i)(?:intends?|plans?) to (?:voluntarily )?(?:transfer|move)")
    private val BESCHLOSSEN = Regex(
        "(?i)determin\\w+ (?:to|that)[^.]{0,120}delist|will be delisted|will be suspended|trading[^.]{0,60}suspended|suspend trading|\\bForm 25\\b|" +
            "delisting (?:determination|decision)|has been delisted|(?:has|have) (?:decided|determined|resolved) to (?:delist|voluntarily delist|remove)|voluntar\\w+ delist",
    )
    private val BEDINGT = Regex("(?i)\\b(?:in the event|if|could|would|should|unless|eligible|whether)\\b")
    // "may" nur klein geschrieben: der Monat Mai steht gross
    private val BEDINGT_KANN = Regex("\\bmay\\b")
    private val FRIST_DATUM = Regex("(?i)\\b(?:until|through|by|on or before|no later than|before)\\s+($MONATSNAMEN)\\s+(\\d{1,2}),\\s+(\\d{4})")
    private val FRIST_TAGE = Regex("(?i)\\b(\\d{2,3})[- ](?:calendar|business)[- ]days?\\b|\\b(\\d{2,3})\\s+days\\b")
    private val VERNEINUNG_DAVOR = Regex("(?i)\\b(?:not|never|without|no)\\s+(?:\\w+\\s+)?$|n't\\s+(?:\\w+\\s+)?$")

    private val KURS = Regex("(?i)minimum bid price|closing bid price|bid price|below \\$1\\.00|\\b5550\\(a\\)\\(2\\)|\\b5450\\(a\\)\\(1\\)|\\b5555\\(a\\)\\(1\\)")
    private val BERICHT = Regex("(?i)\\b5250\\(c\\)\\(1\\)|timely fil|delay(?:ed)? in (?:the )?fil|delinquen\\w+ in|(?:fail\\w*|unable) to (?:timely )?file|late fil")
    private val EIGENKAPITAL = Regex(
        "(?i)stockholders'? equity|shareholders'? equity|market value of (?:listed|publicly held) securities|market capitali[sz]ation|" +
            "\\b5550\\(b\\)|\\b5450\\(b\\)|\\b5810\\(c\\)\\(3\\)\\(B\\)",
    )
    private val AUSSCHUSS = Regex(
        "(?i)audit committee[^.]{0,120}(?:independent|composition|at least|minimum|requirement)|\\b5605\\(c\\)|\\b5605\\(b\\)|" +
            "majority of (?:the )?(?:board|directors)[^.]{0,40}independent|independent directors? requirement",
    )
    private val VERSAMMLUNG = Regex(
        "(?i)\\b5620\\(a\\)|annual (?:general )?meeting of (?:stockholders|shareholders)[^.]{0,100}(?:not|failure|fail)|" +
            "(?:fail\\w*|not)[^.]{0,60}\\bhold\\b[^.]{0,60}annual (?:general )?meeting|annual (?:general )?meeting requirement",
    )
    private val BERICHT_ZEITRAUM = Regex("(?i)Form (10-K|10-Q|20-F)(?:(?!Form 10-)[^.]){0,120}?(?:ended|ending)\\s+($MONATSNAMEN)\\s+(\\d{1,2}),\\s+(\\d{4})")
    private val BERICHT_FORM = Regex("(?i)Form (10-K|10-Q|20-F)")

    // Erstes Vorkommen, das nicht verneint ist ("had not regained compliance" zaehlt nicht als Entwarnung).
    private fun positiv(r: Regex, s: String): Int? =
        r.findAll(s).firstOrNull { !VERNEINUNG_DAVOR.containsMatchIn(s.substring(maxOf(0, it.range.first - 20), it.range.first)) }?.range?.first

    // Notierungsmeldung: Entwarnung, Boersenwechsel, Delisting beschlossen, Delisting angedroht (Frist oder Streichung
    // genannt) oder Regel nicht erfuellt (Hinweis ohne Beschluss und Frist); dazu Grund und Frist.
    fun notierung(s: String): Detail {
        val entwarnung = positiv(ENTWARNUNG, s)
        val mangel = MANGEL.find(s)?.range?.first
        val unterart = when {
            entwarnung != null && (mangel == null || entwarnung < mangel) -> Unterart.ENTWARNUNG
            WECHSEL_GENEHMIGT.containsMatchIn(s) || (WECHSEL_FREIWILLIG.containsMatchIn(s) && s.contains("transfer", true)) ||
                (WECHSEL_PLAN.containsMatchIn(s) && mangel == null) -> Unterart.WECHSEL
            AchtK.saetze(s).any { BESCHLOSSEN.containsMatchIn(it) && !BEDINGT.containsMatchIn(it) && !BEDINGT_KANN.containsMatchIn(it) } -> Unterart.BESCHLOSSEN
            else -> null
        }
        if (unterart == Unterart.WECHSEL) return Detail(unterart = unterart)
        val frist = FRIST_DATUM.find(s)?.let { iso(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            ?: FRIST_TAGE.find(s)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }) + "d" }.orEmpty()
        val art = unterart ?: if (frist.isNotEmpty() || s.contains("delist", true)) Unterart.ANGEDROHT else Unterart.REGEL
        val gruende = listOf(Grund.KURS to KURS, Grund.BERICHT to BERICHT, Grund.EIGENKAPITAL to EIGENKAPITAL, Grund.AUSSCHUSS to AUSSCHUSS, Grund.VERSAMMLUNG to VERSAMMLUNG)
            .mapNotNull { (g, r) -> r.find(s)?.let { g to it.range.first } }
        val grund = gruende.minByOrNull { it.second }?.first
        val berichte = if (grund == Grund.BERICHT) berichte(s) else emptyList()
        return Detail(unterart = art, grund = grund, berichte = berichte, frist = if (art == Unterart.ANGEDROHT) frist else "")
    }

    // Nicht rechtzeitig eingereichte Berichte: "10-K;2024" (Geschaeftsjahr) und "10-Q;09/2024" (Quartalsende).
    private fun berichte(s: String): List<String> {
        val mitDatum = LinkedHashMap<String, String>()
        BERICHT_ZEITRAUM.findAll(s).forEach { m ->
            val form = m.groupValues[1].uppercase()
            if (form in mitDatum) return@forEach
            mitDatum[form] = if (form == "10-Q") (MONATE.indexOf(m.groupValues[2].lowercase()) + 1).toString().padStart(2, '0') + "/" + m.groupValues[4] else m.groupValues[4]
        }
        BERICHT_FORM.findAll(s).forEach { m -> mitDatum.getOrPut(m.groupValues[1].uppercase()) { "" } }
        return mitDatum.entries.take(3).map { it.key + ";" + it.value }
    }

    // ------------------------------------------------------------ 4.01 Pruefer

    private val PRUEFER_FIRMEN = Regex(
        "(?i)\\b(Ernst & Young|Deloitte|KPMG|PricewaterhouseCoopers|PwC|BDO|Grant Thornton|RSM|Marcum|MaloneBailey|Crowe|Baker Tilly|EisnerAmper|" +
            "Withum|Moss Adams|Cherry Bekaert|Forvis Mazars|Mazars|Plante & Moran|Plante Moran|CohnReznick|Armanino|Haskell & White|Citrin Cooperman|" +
            "Wolf & Company|Weinberg & Company|BF Borgers|Prager Metis|MSPC|Boulay|Frazier & Deeter|Dixon Hughes Goodman|Macias Gini|" +
            "Raich Ende Malter|Liggett & Webb|Fruci|Salberg|Turner Stone|M&K CPAS|Daszkal Bolton|Friedman)\\b",
    )
    private val P_RUECKTRITT = Regex("(?i)resign\\w*|declin\\w+ to (?:stand|continue|be re-?appointed|seek re-?election)|will not (?:stand|seek|continue)|ceas\\w+ to (?:be|serve|act)")
    private val P_ENTLASSUNG = Regex("(?i)dismiss\\w*|terminat\\w+ (?:the )?(?:engagement|relationship|appointment)|(?:engagement|relationship)[^.]{0,60}terminated|replaced")
    private val P_NEU = Regex("(?i)(?:appoint|engag|select|approv)\\w+[^.]{0,120}(?:accounting firm|auditor)|new independent registered public accounting firm")
    private val VERNEINUNG = Regex("(?i)\\b(?:no|not|neither|nor|without|none)\\b")
    private val STREIT = Regex("(?i)disagreement")
    private val MAENGEL = Regex("(?i)material weakness|significant deficienc|reportable event|internal control|unwilling to be associated|(?:no longer|can no longer|cannot) (?:be able to )?rely|concerns?")

    // Pruefer: wer geht (Rücktritt oder Entlassung) oder wer neu bestellt wird; ob Meinungsverschiedenheiten oder
    // Maengel der Kontrollen genannt sind (verneinte Saetze zaehlen nicht).
    fun pruefer(s: String): Detail {
        val saetze = AchtK.saetze(s, beiSemikolon = false)
        fun firma(satz: String) = PRUEFER_FIRMEN.find(satz)?.groupValues?.get(1).orEmpty()
        val rueck = saetze.firstOrNull { P_RUECKTRITT.containsMatchIn(it) && firma(it).isNotEmpty() }
        val entl = saetze.firstOrNull { P_ENTLASSUNG.containsMatchIn(it) && firma(it).isNotEmpty() }
        val neu = saetze.firstOrNull { P_NEU.containsMatchIn(it) && firma(it).isNotEmpty() && !P_RUECKTRITT.containsMatchIn(it) && !P_ENTLASSUNG.containsMatchIn(it) }
        val streit = saetze.any { STREIT.containsMatchIn(it) && !VERNEINUNG.containsMatchIn(it) }
        val maengel = saetze.any { MAENGEL.containsMatchIn(it) && !VERNEINUNG.containsMatchIn(it) }
        val neuName = neu?.let { firma(it) }.orEmpty()
        return when {
            rueck != null -> Detail(ereignis = Ereignis.PRUEFER_RUECKTRITT, pruefer = firma(rueck), neuerPruefer = neuName, streit = streit, maengel = maengel)
            entl != null -> Detail(ereignis = Ereignis.PRUEFER_ENTLASSUNG, pruefer = firma(entl), neuerPruefer = neuName, streit = streit, maengel = maengel)
            neu != null -> Detail(ereignis = Ereignis.PRUEFER_NEU, pruefer = neuName, streit = streit, maengel = maengel)
            else -> Detail(streit = streit, maengel = maengel)
        }
    }

    // ------------------------------------------------------------ 4.02 Korrektur

    private val ZEITRAUM = Regex("(?i)(?:ended|ending)\\s+(" + DATUM_TEIL + "(?:(?:,\\s*(?:and\\s+)?|\\s+and\\s+)(?:" + DATUM_TEIL + "|\\d{4}))*)")

    // Betroffene Zeitraeume als erstes und letztes Jahr, aus dem Anfang des Items.
    fun korrektur(s: String): Detail {
        val jahre = ZEITRAUM.findAll(s.take(1500)).flatMap { Regex("\\d{4}").findAll(it.groupValues[1]).map { j -> j.value.toInt() } }.toList()
        if (jahre.isEmpty()) return Detail()
        return Detail(jahrVon = jahre.min(), jahrBis = jahre.max())
    }

    // ------------------------------------------------------------ 2.06 Abschreibung

    private val BETRAG = Regex("(?i)\\$\\s?(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*(million|billion)?")

    // Erster Dollarbetrag nach "impairment", "charge" oder "write-down"; sonst der erste Betrag des Items; 0 ohne Betrag.
    fun betrag(s: String): Double {
        val ab = Regex("(?i)impairment|write-?down|charge").find(s)?.range?.first ?: 0
        val m = BETRAG.find(s, ab) ?: BETRAG.find(s) ?: return 0.0
        val zahl = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return 0.0
        return zahl * when (m.groupValues[2].lowercase()) { "million" -> 1e6; "billion" -> 1e9; else -> 1.0 }
    }

    // ------------------------------------------------------------ 5.02 Abgang von CEO oder CFO

    private val STOP = setOf(
        "on", "the", "company", "board", "directors", "chief", "executive", "officer", "financial", "president", "committee", "corporation",
        "inc", "audit", "chairman", "chair", "item", "effective", "nasdaq", "of", "and", "mr", "ms", "mrs", "dr", "vice", "operating", "general",
        "counsel", "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
        "in", "at", "as", "for", "to", "by", "following", "prior", "also", "additionally", "separately", "first", "second", "third", "class",
    )
    private val PERSON = Regex("(?:(?:Mr|Ms|Mrs|Dr)\\.\\s+)?[A-Z][A-Za-z'\\-]+(?:\\s+[A-Z]\\.)?(?:\\s+[A-Z][A-Za-z'\\-]+){1,2}|(?:Mr|Ms|Mrs|Dr)\\.\\s+[A-Z][A-Za-z'\\-]+")
    private val SOFORT = Regex("(?i)effective immediately|with immediate effect|immediate effect")
    private val WIRKSAM = Regex("(?i)effective(?: as of)?\\s+($MONATSNAMEN)\\s+(\\d{1,2}),\\s+(\\d{4})")

    // Name, Art (Rücktritt, Entlassung, Abgang), Wirkung und Nachfolge aus dem Satz, der den Abgang von CEO oder CFO
    // der Firma nennt.
    fun fuehrung(html: String, emittent: String): Detail {
        val t = AchtK.klartext(html)
        val s = AchtK.text502(t)
        val liste = AchtK.saetze(s)
        fun abgang(satz: String) = AchtK.ABGANG.findAll(satz).firstOrNull { m -> AchtK.RENTE.find(satz, m.range.first)?.range?.first != m.range.first }
        fun firmenrolle(satz: String) = AchtK.rolleGiltFuerFirma(satz, AchtK.CEO, emittent) || AchtK.rolleGiltFuerFirma(satz, AchtK.CFO, emittent)
        val i = liste.indexOfFirst { satz -> abgang(satz) != null && !AchtK.BEDINGUNG.containsMatchIn(satz) && !AchtK.nurBoard(satz) && firmenrolle(satz) }
        if (i < 0) return Detail()
        val satz = liste[i]
        val verb = abgang(satz)!!
        val v = verb.value.lowercase()
        val ereignis = when {
            Regex("terminat|remov|dismiss|fired|ousted|replaced").containsMatchIn(v) -> Ereignis.ENTLASSUNG
            Regex("resign|step|retire|depart").containsMatchIn(v) -> Ereignis.RUECKTRITT
            else -> Ereignis.ABGANG
        }
        val eigen = Firmenname.worte(emittent).firstOrNull().orEmpty()
        val vor = satz.substring(0, verb.range.first)
        val person = PERSON.findAll(vor).filter { m ->
            val tokens = m.value.split(' ', '.').filter { it.isNotEmpty() }
            val name = tokens.filter { it.lowercase() !in setOf("mr", "ms", "mrs", "dr") }
            name.isNotEmpty() && name.none { it.lowercase() in STOP } && (eigen.isEmpty() || name.first().lowercase() != eigen)
        }.lastOrNull()?.value?.replace(Regex("^(?:Mr|Ms|Mrs|Dr)\\.\\s+"), "").orEmpty()
        val naechster = liste.getOrNull(i + 1).orEmpty()
        val sofort = SOFORT.containsMatchIn(satz) || SOFORT.containsMatchIn(naechster)
        val wirksam = WIRKSAM.find(satz)?.let { iso(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.orEmpty()
        val nachfolge = liste.any { AchtK.ANTRITT.containsMatchIn(it) && firmenrolle(it) }
        return Detail(person = person, ereignis = ereignis, sofort = sofort, wirksam = wirksam, nachfolge = nachfolge)
    }
}
