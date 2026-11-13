package de.troetenrepublik.app.engine

// Eigentuemer und Rechte, Teil Aktionaere im Handeln: Absicht eines Aktivisten aus Item 4 des Schedule 13D (Schluesselwortregeln)
// und Abstimmungsergebnisse der Hauptversammlung aus Item 5.07 eines 8-K. Reine Funktionen auf Text und HTML.
object StrukturAktion {

    // ------------------------------------------------------------ Aktivisten (Schedule 13D, Item 4)

    private val KEIN_PLAN = Regex("(?i)no\\s+(?:present\\s+)?plans?\\s+or\\s+proposals?|does\\s+not\\s+(?:currently\\s+)?have\\s+any\\s+plans?|not\\s+have\\s+any\\s+present\\s+plans?")
    private val R_BOARD = Regex("(?i)\\bboard\\b[^.]{0,160}(?:nominat|appoint|representation|designat|seats?\\b|elect)|(?:nominat|appoint|designat)[^.]{0,120}(?:director|board)")
    private val R_VERKAUF = Regex("(?i)sale\\s+of\\s+the\\s+(?:company|issuer)|sell\\s+the\\s+(?:company|issuer)|strategic\\s+alternatives|take[- ]private|going[- ]private|business\\s+combination|acquire\\s+all|merger|acquisition\\s+of\\s+the\\s+(?:company|issuer)")
    private val R_ABSPALTUNG = Regex("(?i)spin-?\\s?off|separation\\s+of|divest|carve-?\\s?out|break-?\\s?up|sale\\s+of\\s+(?:certain\\s+|its\\s+)?(?:assets|businesses|business|divisions?|units?)")
    private val R_RUECKKAUF = Regex("(?i)repurchase|buy-?\\s?back|return\\s+(?:of\\s+)?capital|special\\s+dividend")
    private val R_STRATEGIE = Regex("(?i)strateg|operational|capital\\s+allocation|cost\\s+(?:reduction|structure|saving)|\\bmargins?\\b|change\\s+in\\s+(?:the\\s+)?management|replace[^.]{0,60}(?:chief|ceo|management)|leadership|governance")

    // Vorratssaetze: "may", "from time to time", "reserve the right", Aufzaehlungen aller Moeglichkeiten. Sie stehen in fast jeder
    // Meldung und zaehlen nicht.
    private val VORRAT = Regex("(?i)\\b(?:may|might|could|would\\s+consider|from\\s+time\\s+to\\s+time|reserves?\\s+the\\s+right|reserved\\s+the\\s+right|at\\s+any\\s+time|including,?\\s+without\\s+limitation|any\\s+or\\s+all|potential(?:ly)?|possible|possibility)\\b")

    // Konkrete Handlung oder Absicht: nominiert, Brief, Vorschlag, Vereinbarung
    private val KONKRET = Regex(
        "(?i)has\\s+nominated|have\\s+nominated|intends?\\s+to\\s+(?:nominate|propose|seek|engage|vote\\s+against|call|replace|urge|demand|request)|plans?\\s+to\\s+nominate|" +
            "delivered\\s+a\\s+(?:letter|notice)|sent\\s+a\\s+(?:letter|notice)|submitted\\s+a\\s+(?:letter|notice|nomination)|nomination\\s+notice|\\bproposes?\\b|has\\s+proposed|have\\s+proposed|" +
            "settlement\\s+agreement|cooperation\\s+agreement|has\\s+requested|have\\s+requested|\\bdemands?\\b|\\burges?\\b|has\\s+urged|\\bcalls?\\s+on\\s+the\\s+(?:board|company|issuer)",
    )

    // Einordnung der Absicht aus Item 4: Nur Saetze mit einer konkreten Handlung zaehlen, Vorratssaetze und Saetze, die "keine
    // Plaene" sagen, nicht. Ohne Treffer: nur Beteiligung. Die Reihenfolge ist die der Schluessel in Struktur.Absicht.
    fun absicht(zweck: String): List<String> {
        val saetze = zweck.split(Regex("(?<=[.;])\\s+")).filter {
            it.isNotBlank() && !KEIN_PLAN.containsMatchIn(it) && !VORRAT.containsMatchIn(it) && KONKRET.containsMatchIn(it)
        }
        val t = saetze.joinToString(" ")
        val aus = ArrayList<String>()
        if (R_BOARD.containsMatchIn(t)) aus += Struktur.Absicht.BOARD
        if (R_VERKAUF.containsMatchIn(t)) aus += Struktur.Absicht.VERKAUF
        if (R_ABSPALTUNG.containsMatchIn(t)) aus += Struktur.Absicht.ABSPALTUNG
        if (R_RUECKKAUF.containsMatchIn(t)) aus += Struktur.Absicht.RUECKKAUF
        if (R_STRATEGIE.containsMatchIn(t)) aus += Struktur.Absicht.STRATEGIE
        return aus.ifEmpty { listOf(Struktur.Absicht.BETEILIGUNG) }
    }

    // Aktiv heisst: mindestens eine Absicht ausser der blossen Beteiligung
    fun aktiv(a: List<String>): Boolean = a.any { it != Struktur.Absicht.BETEILIGUNG }

    // Aktivisten aus den Meldungen eines Melders (neueste zuerst): die juengste oben, die uebrigen als Verlauf. Nur Melder, deren
    // juengste Meldung eine aktive Absicht hat, zaehlen als Aktivist; die Beleg-Zuordnung kommt vom Aufrufer.
    fun aktivist(name: String, liste: List<Struktur.Verlauf>): Struktur.Aktivist? {
        val neu = liste.firstOrNull() ?: return null
        if (!aktiv(neu.absicht) && liste.none { aktiv(it.absicht) }) return null
        return Struktur.Aktivist(name, neu, liste.drop(1).take(4))
    }

    // ------------------------------------------------------------ Hauptversammlung (8-K Item 5.07)

    private val TAG = Regex("(?s)<[^>]*>")
    private val ZEILE = Regex("(?is)<tr\\b.*?</tr>")
    private val ZELLE = Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>")

    private fun klartext(html: String): String =
        Zahlenmitteilung.entitaeten(html.replace(TAG, " ")).replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    private fun zellen(zeile: String): List<String> =
        ZELLE.findAll(zeile).map { klartext(it.groupValues[1]) }.filter { it.isNotEmpty() && it != "%" && it != "$" && it != ")" }.toList()

    private fun stimmZahl(s: String): Double? {
        val t = s.replace(",", "").replace(Regex("\\s"), "")
        if (t == "-" || t == "—" || t == "–") return 0.0
        return t.toDoubleOrNull()
    }

    fun art(text: String): String {
        val t = text.lowercase()
        return when {
            Regex("shareholder proposal|stockholder proposal|proposal (?:submitted|presented) by|shareholder-?proposed|stockholder-?proposed").containsMatchIn(t) -> Struktur.BArt.ANTRAG
            Regex("say[- ]on[- ]pay|executive compensation|named executive|advisory vote").containsMatchIn(t) && !Regex("frequency").containsMatchIn(t) -> Struktur.BArt.VERGUETUNG
            Regex("ratif|auditor|independent registered|accounting firm").containsMatchIn(t) -> Struktur.BArt.PRUEFER
            Regex("elect").containsMatchIn(t) -> Struktur.BArt.WAHL
            else -> Struktur.BArt.SONST
        }
    }

    // Beschluesse aus dem Text eines 8-K ab "Item 5.07": Tabellen mit Kopf "For" und "Against" oder "Withheld". Der Text vor der
    // Tabelle benennt den Beschluss; bei Wahlen steht je Kandidat eine Zeile mit dem Namen.
    fun beschluesse(html: String): List<Struktur.Beschluss> {
        val start = Regex("(?i)item\\s*5\\.07").findAll(html).map { it.range.first }.lastOrNull() ?: return emptyList()
        val ende = Regex("(?i)item\\s*(?:5\\.08|7\\.01|8\\.01|9\\.01)|\\bSIGNATURES?\\b").find(html, start + 10)?.range?.first ?: html.length
        val teil = html.substring(start, ende)
        val aus = ArrayList<Struktur.Beschluss>()
        var letzterText = ""
        var i = 0
        while (i < teil.length) {
            val tabelle = teil.indexOf("<table", i, ignoreCase = true)
            val text = klartext(teil.substring(i, if (tabelle < 0) teil.length else tabelle))
            if (text.isNotBlank()) letzterText = text.takeLast(300)
            if (tabelle < 0) break
            val tEnde = teil.indexOf("</table", tabelle, ignoreCase = true).let { if (it < 0) teil.length else it }
            val zeilen = ZEILE.findAll(teil.substring(tabelle, tEnde)).map { zellen(it.value) }.filter { it.isNotEmpty() }.toList()
            val kopfIdx = zeilen.indexOfFirst { z -> z.any { it.equals("for", true) } && z.any { Regex("(?i)^(against|withh)").containsMatchIn(it) } }
            if (kopfIdx >= 0) {
                val kopf = zeilen[kopfIdx].map { it.lowercase() }
                val fuer = kopf.indexOfFirst { it == "for" }
                val gegen = kopf.indexOfFirst { it.startsWith("against") || it.startsWith("withh") }
                val enth = kopf.indexOfFirst { it.startsWith("abstain") }
                val brok = kopf.indexOfFirst { it.contains("broker") }
                val titel = letzterText.substringAfterLast("Proposal", letzterText).trimStart(' ', ':', '.', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9').ifBlank { letzterText }.take(160)
                val art = art(letzterText + " " + zeilen.take(kopfIdx).joinToString(" ") { it.joinToString(" ") })
                for (z in zeilen.drop(kopfIdx + 1)) {
                    // Ist die erste Zelle ein Name, stehen die Zahlen eine Zelle weiter als in der Kopfzeile
                    val mitName = stimmZahl(z[0]) == null
                    val versatz = if (mitName && z.size == kopf.size + 1) 1 else 0
                    fun wert(k: Int): Double? = if (k < 0) 0.0 else z.getOrNull(k + versatz)?.let { stimmZahl(it) }
                    val f = wert(fuer) ?: continue
                    val g = wert(gegen) ?: continue
                    val kandidat = art == Struktur.BArt.WAHL && mitName
                    aus += Struktur.Beschluss(
                        titel = if (kandidat) z[0] else titel, art = art, fuer = f, gegen = g, enthaltung = wert(enth) ?: 0.0, broker = wert(brok) ?: 0.0,
                    )
                }
            }
            i = (teil.indexOf('>', tEnde).takeIf { it >= 0 } ?: teil.length) + 1
        }
        return aus
    }

    // Kernzahlen einer Versammlung: Zustimmung zur Verguetung, Antraege (angenommen, abgelehnt), schwache Beschluesse
    // (Verguetung oder Wahl mit weniger als 70 Prozent Zustimmung)
    class Kern(val verguetung: Double?, val angenommen: Int, val abgelehnt: Int, val schwach: Int)

    fun kern(v: Struktur.Versammlung): Kern {
        val verg = v.beschluesse.firstOrNull { it.art == Struktur.BArt.VERGUETUNG }?.zustimmung
        val antraege = v.beschluesse.filter { it.art == Struktur.BArt.ANTRAG }
        val angenommen = antraege.count { (it.zustimmung ?: 0.0) > 50.0 }
        val schwach = v.beschluesse.count { (it.art == Struktur.BArt.VERGUETUNG || it.art == Struktur.BArt.WAHL) && (it.zustimmung ?: 100.0) < 70.0 }
        return Kern(verg, angenommen, antraege.size - angenommen, schwach)
    }

    // Ein Beschluss ist auffaellig: schwache Zustimmung bei Verguetung oder Wahl, angenommener Aktionaersantrag
    fun auffaellig(b: Struktur.Beschluss): Boolean {
        val z = b.zustimmung ?: return false
        return ((b.art == Struktur.BArt.VERGUETUNG || b.art == Struktur.BArt.WAHL) && z < 70.0) || (b.art == Struktur.BArt.ANTRAG && z > 50.0)
    }
}
