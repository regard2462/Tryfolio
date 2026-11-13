package de.troetenrepublik.app.engine

// Bull- und Bear-Case einer Aktie (seit 5.7): was fuer und was gegen den Wert spricht, automatisch aus Schlagzeilen
// und Kennzahlen zusammengestellt. Schlagzeilen werden nach Themen erkannt (Klagen, Ermittlungen, Vorwuerfe zur
// Bilanz, Gewinnwarnungen, Herabstufungen gegenueber Hochstufungen, Rekordzahlen, Auftraegen usw.); Kennzahlen nach
// festen Schwellen. Jede Aussage nennt ihren Grund, Schlagzeilen bleiben als Beleg dabei. Seit 5.91 hat jede Aussage
// ein Gewicht; die Waage zeigt, welche Seite schwerer wiegt. Keine Bewertung im Sinne einer Empfehlung.
object BullBear {
    enum class Seite { BULL, BEAR }

    // Themen der Schlagzeilen; Reihenfolge = Gewicht in der Anzeige (wichtigste zuerst je Seite).
    // gewicht: wie schwer die Aussage in der Waage zaehlt (1 normal, 3 sehr schwer).
    enum class Thema(val seite: Seite, val gewicht: Double) {
        PLEITE(Seite.BEAR, 3.0), BILANZ(Seite.BEAR, 3.0), ERMITTLUNG(Seite.BEAR, 2.0), KLAGE(Seite.BEAR, 1.0), WARNUNG(Seite.BEAR, 2.0),
        ABSTUFUNG(Seite.BEAR, 1.0), RUECKRUF(Seite.BEAR, 1.0), PERSONAL(Seite.BEAR, 1.0),
        UEBERNAHME(Seite.BULL, 1.0), PROGNOSE(Seite.BULL, 2.0), ZAHLEN(Seite.BULL, 1.5), AUFSTUFUNG(Seite.BULL, 1.0), ZULASSUNG(Seite.BULL, 1.5),
        AUFTRAG(Seite.BULL, 1.0), RUECKKAUF(Seite.BULL, 1.0), KI(Seite.BULL, 1.0),
    }

    // Kennzahlen-Aussagen.
    enum class Kennzahl(val seite: Seite, val gewicht: Double) {
        ANALYSTEN_POSITIV(Seite.BULL, 1.0), WACHSTUM(Seite.BULL, 1.5), GEWINNWACHSTUM(Seite.BULL, 1.5), MARGE_HOCH(Seite.BULL, 1.0),
        NETTOKASSE(Seite.BULL, 1.0), CASHFLOW_POSITIV(Seite.BULL, 1.5), UEBERTROFFEN(Seite.BULL, 1.0), INSIDER_KAUFEN(Seite.BULL, 1.5),
        GEWINNE_STEIGEN(Seite.BULL, 1.0), FDA_ZULASSUNG(Seite.BULL, 1.0),
        NAHE_HOCH(Seite.BULL, 1.0), DIVIDENDE_SOLIDE(Seite.BULL, 1.0), RENDITE_HOCH(Seite.BULL, 1.0), BEWERTUNG_NIEDRIG(Seite.BULL, 1.0),
        ANALYSTEN_SKEPTISCH(Seite.BEAR, 1.0), ANALYSTEN_VERHALTEN(Seite.BEAR, 1.0), SCHRUMPFT(Seite.BEAR, 1.5), GEWINN_FAELLT(Seite.BEAR, 1.5), VERLUST(Seite.BEAR, 2.0),
        SCHULDEN(Seite.BEAR, 1.5), CASHFLOW_NEGATIV(Seite.BEAR, 1.5), VERFEHLT(Seite.BEAR, 1.0), INSIDER_VERKAUFEN(Seite.BEAR, 1.0),
        BEWERTUNG_HOCH(Seite.BEAR, 1.0), SCHWANKUNG(Seite.BEAR, 0.5), ZYKLISCH(Seite.BEAR, 0.5), DIVIDENDE_UNGEDECKT(Seite.BEAR, 1.0),
        LEERVERKAUF_HOCH(Seite.BEAR, 1.5), FDA_RUECKRUF(Seite.BEAR, 1.5), WEIT_UNTER_HOCH(Seite.BEAR, 1.0),
        // [0.77] Kongress (90 Tage) und Hoch- gegen Abstufungen (30 Tage) wie im Signale-Kasten der Wertpapierseite
        KONGRESS_KAUFT(Seite.BULL, 1.0), STUFEN_HOCH(Seite.BULL, 1.0), KONGRESS_VERKAUFT(Seite.BEAR, 1.0), STUFEN_AB(Seite.BEAR, 1.0),
        // [0.77] Schwere Warnzeichen aus den 8-K-Meldungen (Korrektur von Abschluessen, Delisting-Hinweis, Insolvenz), 24 Monate
        WARNZEICHEN(Seite.BEAR, 2.0),
    }

    data class Schlagzeile(val titel: String, val zeit: Long, val id: String)

    // Eine Aussage: Thema einer Schlagzeile (mit Beleg) oder eine Kennzahl (mit Werten fuer den Text).
    data class Punkt(val seite: Seite, val thema: Thema? = null, val kennzahl: Kennzahl? = null, val werte: List<Double> = emptyList(), val belege: List<Schlagzeile> = emptyList()) {
        // Schlagzeilen-Themen zaehlen mit mehreren Belegen etwas mehr (hoechstens anderthalbfach)
        val gewicht: Double
            get() = thema?.let { it.gewicht * (1.0 + 0.25 * (belege.size - 1).coerceIn(0, 2)) } ?: kennzahl?.gewicht ?: 1.0

        // Schluessel fuer Text und Erklaerung
        val schluessel: String get() = thema?.name ?: kennzahl?.name.orEmpty()
    }

    enum class Stufe { KLAR_DAGEGEN, EHER_DAGEGEN, AUSGEWOGEN, EHER_DAFUER, KLAR_DAFUER }

    // Waage: Anteil der Gewichte dafuer an allen Gewichten, -100 (nur dagegen) bis +100 (nur dafuer).
    data class Waage(val wert: Int, val stufe: Stufe, val dafuer: Double, val dagegen: Double)

    fun waage(punkte: List<Punkt>): Waage? {
        val plus = punkte.filter { it.seite == Seite.BULL }.sumOf { it.gewicht }
        val minus = punkte.filter { it.seite == Seite.BEAR }.sumOf { it.gewicht }
        if (plus + minus <= 0.0) return null
        val wert = Math.round((plus - minus) / (plus + minus) * 100).toInt()
        val stufe = when {
            wert <= -50 -> Stufe.KLAR_DAGEGEN
            wert < -15 -> Stufe.EHER_DAGEGEN
            wert <= 15 -> Stufe.AUSGEWOGEN
            wert < 50 -> Stufe.EHER_DAFUER
            else -> Stufe.KLAR_DAFUER
        }
        return Waage(wert, stufe, plus, minus)
    }

    // [0.72] Waage aus den Zahlen des Aktienfinders: dieselbe Auswertung und dieselben Eingaben wie auf der
    // Wertpapierseite (Nettomarge, Kasse, Schulden, freier Cashflow, Sektor, Zahl der Analysten, Quartalsergebnisse),
    // aber ohne Schlagzeilen, Insider-Trend aus dem Finder und FDA-Daten. Nur fuer Werte mit geladenem Profil: ohne
    // Profil fehlen Marge, Wachstum und Bilanz, die Waage waere eine andere.
    fun ausFinderDaten(d: Aktienfinder.Daten, jetzt: Long): Waage? {
        if (!d.mitDetails) return null
        fun w(f: FinderVorlagen.Feld) = d.werte[f]
        val hatZahlen = d.kgv != null || d.kgvErwartet != null || d.kursziel != null || d.dividende != null || w(FinderVorlagen.Feld.UMSATZ_QUARTAL) != null
        if (!hatZahlen) return null
        val f = Fakten(
            // Der Finder kennt das Kursziel nur als Potenzial in Prozent, die Fakten rechnen mit Kurs und Preis; nur
            // das Verhaeltnis zaehlt, deshalb genuegt ein Kurs von 100
            kurs = 100.0,
            kursziel = d.kursziel?.let { pz -> 100.0 * (1.0 + pz / 100.0) } ?: 0.0,
            empfehlung = d.note ?: 0.0,
            analysten = d.analysten,
            umsatzWachstum = (w(FinderVorlagen.Feld.UMSATZ_QUARTAL) ?: 0.0) / 100.0,
            gewinnWachstum = (w(FinderVorlagen.Feld.GEWINN_QUARTAL) ?: 0.0) / 100.0,
            gewinnMarge = (d.marge ?: 0.0) / 100.0,
            kasse = d.kasse ?: 0.0,
            schulden = d.schulden ?: 0.0,
            freierCashflow = d.freierCashflow ?: 0.0,
            marktwert = d.boersenwert ?: 0.0,
            kgv = d.kgv ?: 0.0,
            kgvErwartet = d.kgvErwartet ?: 0.0,
            zumHoch = w(FinderVorlagen.Feld.HOCH52)?.let { 1.0 + it / 100.0 } ?: 0.0,
            dividendenRendite = (d.dividende ?: 0.0) / 100.0,
            eigenkapitalRendite = ((w(FinderVorlagen.Feld.ROE) ?: d.eigenkapitalRendite) ?: 0.0) / 100.0,
            beta = w(FinderVorlagen.Feld.BETA) ?: 0.0,
            ausschuettung = (w(FinderVorlagen.Feld.AUSSCHUETTUNG) ?: 0.0) / 100.0,
            branche = d.sektor,
            industrie = d.industrie,
            ergebnisse = d.ergebnisse,
            insider = d.insider,
            leerverkauf = d.leer,
            leerUs = d.leerUs,
        )
        return waage(auswerten(f, emptyList(), jetzt))
    }

    data class Fakten(
        val kurs: Double = 0.0,
        val kursziel: Double = 0.0,
        // Mittel der Empfehlungen: 1 Kaufen bis 5 Verkaufen; 0 unbekannt.
        val empfehlung: Double = 0.0,
        val analysten: Int = 0,
        // Anteile als Dezimalzahl (0,12 = 12 %), wie Yahoo sie liefert.
        val umsatzWachstum: Double = 0.0,
        val gewinnWachstum: Double = 0.0,
        val gewinnMarge: Double = 0.0,
        val kasse: Double = 0.0,
        val schulden: Double = 0.0,
        val freierCashflow: Double = 0.0,
        val marktwert: Double = 0.0,
        val kgv: Double = 0.0,
        val kgvErwartet: Double = 0.0,
        // Kurs im Verhaeltnis zum 52-Wochen-Hoch (0,9 = 10 % darunter); 0 unbekannt.
        val zumHoch: Double = 0.0,
        // Dividendenrendite und Eigenkapitalrendite als Anteil (0,03 = 3 %).
        val dividendenRendite: Double = 0.0,
        val eigenkapitalRendite: Double = 0.0,
        val beta: Double = 0.0,
        val ausschuettung: Double = 0.0,
        val branche: String = "",
        val industrie: String = "",
        // Ergebnis je Aktie der letzten Quartale: tatsaechlich und erwartet.
        val ergebnisse: List<Pair<Double, Double>> = emptyList(),
        // Insider-Trend -100 bis +100, null ohne Daten.
        val insider: Int? = null,
        // Leerverkaeufe in Prozent, null ohne Daten. leerUs: Anteil am Streubesitz (US-Werte); sonst Summe der
        // veroeffentlichten Leerverkaufspositionen am Grundkapital.
        val leerverkauf: Double? = null,
        val leerUs: Boolean = false,
        // FDA in zwoelf Monaten: Erstzulassungen und Rueckrufe der Klasse I (ernste Gefahr); null ohne Daten.
        val fdaZulassungen: Int? = null,
        val fdaRueckrufe: Int? = null,
        // [0.77] Kongress: Kaeufe und Verkaeufe der letzten 90 Tage; Hoch- und Abstufungen der letzten 30 Tage; null ohne Daten.
        val kongress: Pair<Int, Int>? = null,
        val stufen: Pair<Int, Int>? = null,
        // [0.77] Zahl der schweren Warnzeichen der letzten 24 Monate; null ohne geladene Meldungen.
        val warnzeichen: Int? = null,
        // Schlagzeilen-Themen, die diese Warnzeichen schon abdecken (warnThemen).
        val warnThemen: Set<Thema> = emptySet(),
    )

    // Themen, deren Anlass ein schweres Warnzeichen aus den 8-K schon meldet: Korrektur von Abschluessen deckt die
    // Bilanz-Schlagzeilen, Delisting-Hinweis und Insolvenz die Pleite-Schlagzeilen. arten: Namen der Arten (AchtK.Art).
    fun warnThemen(arten: Collection<String>): Set<Thema> = buildSet {
        if ("KORREKTUR" in arten) add(Thema.BILANZ)
        if ("DELISTING" in arten || "INSOLVENZ" in arten) add(Thema.PLEITE)
    }

    // Stichwortregeln der Schlagzeilen, deutsch und englisch. Jede Regel braucht Wortgrenzen und ein Umfeld, das die
    // Aussage tragen kann: "betrug" als Verb ("Der Umsatz betrug 5 Mrd.") ist kein Betrug, "beats" als Marke kein
    // Ergebnis ueber den Erwartungen, "probe" und "outage" ohne Zusatz meinen oft Raumsonden und Stromausfaelle.
    // THEMEN_MUSTER ignoriert die Gross-/Kleinschreibung, THEMEN_GENAU nicht (das Substantiv Betrug ist gross
    // geschrieben, das Verb klein).
    private val THEMEN_MUSTER: Map<Thema, List<String>> = mapOf(
        Thema.PLEITE to listOf(
            "\\bbankrupt",
            "\\bchapter 11\\b",
            "\\bgoing concern\\b",
            "\\bdelist(s|ed|ing)?\\b",
            "\\binsolven",
            "\\bzahlungsunfähig",
        ),
        Thema.BILANZ to listOf(
            "\\baccounting (fraud|irregularit\\w*|scandal|probe|errors?|problems?|issues?|concerns?|restatement|manipulation|improprieties)\\b",
            "\\b(irregularit\\w*|concerns?|questions?|doubts?) (in|about|over|with|around) (its |the |their )?accounting\\b",
            "\\brestate(s|d|ment|ments)?\\b",
            "\\bfraud",
            "\\bshort[- ]sellers? (report|attack|alleg\\w*|claims?|firm)\\b",
            "\\bshort report\\b",
            "\\bhindenburg research\\b",
            "\\bauditor (resign\\w*|quits|walks)\\b",
            "\\blate (annual |quarterly )?fil(ing|ings)\\b",
            "\\bdelays? (its )?(annual|quarterly)? ?(report|filing|results)\\b",
            "\\bbilanz(skandal|fälschung|manipulation|betrug)",
            "\\bwirtschaftsprüfer (legt|legen|tritt|treten|verweigert|verweigern|kündigt|kündigen|entzieht)",
            "\\btestat (verweigert|verwehrt|eingeschränkt)",
            "\\bgeschönt",
            "\\w+betrugs?\\b",
            "\\bbetrugs\\w*",
        ),
        Thema.ERMITTLUNG to listOf(
            "\\binvestigation\\b",
            "\\binvestigat(es|ed|ing)\\b",
            "\\bsubpoena",
            "\\bprobe (into|of)\\b",
            "\\b(internal|criminal|federal|regulatory|official|formal|fresh|widening|expanding) probe\\b",
            "\\bamid (an? |the )?(\\w+ )?probe\\b",
            "\\bprobes\\b(?! (mars|jupiter|the moon|venus|saturn|deep|space))",
            "\\b(?!(?:space|mars|lunar|moon|solar|nasa|deep|venus|jupiter|saturn|alien|robotic|atmospheric|soil|temperature|medical|ultrasound|depth|a|the|this|that|each|one|every|such|new) )\\w+ probe\\b",
            "\\b(sec|doj|ftc|eu|antitrust|criminal|federal|regulatory|government|congressional|regulators?) probe\\b",
            "\\b(sec|doj|ftc)\\b ?(investigat\\w*|subpoena\\w*|sues?|charges?|fines?|inquiry|lawsuit|complaint|settle\\w*|enforcement|scrutiny)",
            "\\b(charged|sued|fined|probed|investigated|subpoenaed) by (the )?(sec|doj|ftc|justice department)\\b",
            "\\bjustice department (probe|investigat\\w*|sues?|lawsuit|charges?|subpoena\\w*|antitrust)",
            "\\bantitrust (probe|case|lawsuit|suit|investigation|fine|charges?|scrutiny|trial|complaint|violation|regulators?|authorities)\\b",
            "\\bermittlung",
            "\\bermittler\\b",
            "\\buntersuchung(en)? (gegen|wegen)\\b",
            "\\bermittelt (\\S+ ){0,2}(gegen|wegen)\\b",
            "\\bermittelt (gegen|wegen)\\b",
            "\\bermitteln (gegen|wegen)\\b",
            "\\bkartellamt (prüft|ermittelt|verhängt|durchsucht|eröffnet|leitet)\\b",
            "\\bkartellstrafe",
            "\\bkartellverfahren",
            "\\bdurchsuchung",
            "\\bdurchsucht(e|en)? (die |das |mehrere )?(büro|zentrale|geschäftsräume|räume|firmenzentrale|filiale|standorte)",
            "\\brazzia\\b",
            "\\bstaatsanwalt",
            "\\bbafin prüft\\b",
        ),
        Thema.KLAGE to listOf(
            "\\blawsuits?\\b",
            "\\bclass action\\b",
            "\\bsues\\b",
            "\\bsued\\b",
            "\\blitigation\\b",
            "\\bverklagt",
            "\\bsammelklage",
            "\\bklage[nr]?\\b(?! (über|um))",
            "\\bklagt (gegen|auf)\\b",
        ),
        Thema.WARNUNG to listOf(
            "\\b(cuts?|lowers?|slashes|trims|reduces|withdraws|suspends) (its |their )?([\\w\$%.-]+ ){0,4}(guidance|outlook|forecasts?)\\b",
            "\\bprofit warning\\b",
            "\\b(falls?|fell|comes?|came) (up )?(short|shy)( of)? (\\w+ )?(estimates?|expectations?|forecasts?|consensus)\\b",
            "\\bearnings missed\\b",
            "\\bmiss(es|ed)? (by|analysts)\\b",
            "\\bmiss(es|ed)? (on )?(\\w+[’\\' ]?s? ){0,3}(estimates?|expectations?|forecasts?|earnings|revenue|sales|targets?|consensus|analysts|views)\\b",
            "\\bmiss(es|ed)? the mark\\b",
            "\\bgewinnwarnung",
            "\\bsenkt (die |seine |ihre )?(umsatz|gewinn)?prognose\\b",
            "\\bverfehlt (die |den |das )?(erwartungen|prognose|ziele?|schätzungen|analystenerwartungen|umsatzziel|gewinnziel|konsens)",
            "\\bkappt (die |seine |ihre )?prognose\\b",
        ),
        Thema.ABSTUFUNG to listOf(
            "\\bdowngrad(e|es|ed|ing)\\b",
            "\\bcuts price target\\b",
            "\\blowers price target\\b",
            "\\babgestuft\\b",
            "\\bsenkt (das )?kursziel",
            "\\babstufung",
        ),
        Thema.RUECKRUF to listOf(
            "\\brecall(s|ed)?\\b(?! election)",
            "\\brückruf",
            "\\bdata breach",
            "\\b(hacked|gehackt\\w*|hacker-?angriff\\w*|hacker-?attacke\\w*)\\b",
            "\\bhackers? (steal|stole|stolen|breach\\w*|attack\\w*|target\\w*|greifen|erbeuten|stehlen|legen|attackieren)\\b",
            "\\b(by|from|von) hackers?\\b",
            "\\bhackern\\b",
            "\\b(?!(?:life|tax|money|travel|growth|productivity|kitchen|content|marketing) )\\w+ hack\\b(?! (?:to|for|your|that|you|yourself))",
            "\\bcyber-?(angriff|attack)",
            "\\b(cloud|service|network|system|global|major|widespread|aws|azure|server|data center|it)[- ]outage\\b",
            "\\boutage (hits|affects|disrupts|knocks)\\b",
            "\\b(system|it|server|netz|service)-?ausfall",
        ),
        Thema.PERSONAL to listOf(
            "\\b(ceo|cfo|chief executive|vorstandschef|vorstandsvorsitzende[rn]?|finanzvorstand|konzernchef|firmenchef)\\b[\\w\\s.\\'’-]{0,30}?\\b(resigns|steps down|to step down|exits|departs|ousted|retires|tritt zurück|geht)\\b",
            "\\blay(s|ing)? off\\b",
            "\\blayoffs?\\b",
            "\\bjob cuts\\b",
            "\\bcuts? (about |around |some |more than |over )?[\\d,%.]+ ?(jobs|positions)\\b",
            "\\bcuts? [\\d.]+% of [\\w\\s]{0,30}(workers|employees|workforce|staff)\\b",
            "\\bstellenabbau\\b",
            "\\babbau von [\\d.]+ stellen",
            "\\bstreicht (\\S+ ){0,3}stellen?\\b",
            "\\bstellen ab\\b",
            "\\bstellenstreichung",
            "\\bstellen (streichen|abbauen)\\b",
            "\\bentlässt\\b",
            "\\b(massen)?entlassungen\\b",
            "\\brücktritt (des|von) (ceo|cfo|vorstandschefs|konzernchefs|finanzvorstands)",
        ),
        Thema.UEBERNAHME to listOf(
            "\\btakeover",
            "\\bto acquire\\b",
            "\\bacquisition offer\\b",
            "\\bbuyout\\b",
            "\\bübernahme(angebot|offerte|gespräche|kampf|pläne|plänen)\\b",
            "\\bübernahme (von|durch|soll|wird|ist|steht|scheitert)\\b",
            "-übernahme\\b",
            "\\bübernahme (ab|abgelehnt)\\b",
            "\\bübernimmt (den |die |das )?(konkurrent|rivale|wettbewerber|hersteller|anbieter|spezialist)",
        ),
        Thema.PROGNOSE to listOf(
            "\\b(raises?|raised|lifts?|lifted|boosts?|boosted|hikes?|hiked|ups) (its |their )?(?!(?:mortgage|rate|rates|inflation|gdp|growth|economic|global|price) )([\\w\$%.-]+ ){0,4}(guidance|outlook)\\b",
            "\\b(raises?|raised|lifts?|lifted|boosts?|boosted|hikes?|hiked|ups) (its |their )?(?!(?:mortgage|rate|rates|inflation|gdp|growth|economic|global|price) )([\\w\$%.-]+ ){0,3}(earnings|profit|revenue|sales) forecasts?\\b",
            "\\bhebt (die |seine |ihre )?(umsatz|gewinn)?prognose\\b",
            "\\berhöht (die |seine |ihre )?(umsatz|gewinn)?prognose\\b",
        ),
        Thema.ZAHLEN to listOf(
            "\\bbeats (estimates|expectations|forecasts|consensus|analysts?|views|the street|earnings|revenue|profit expectations|on (earnings|revenue|sales))\\b",
            "\\btops (\\w+ ){0,3}(estimates|expectations|forecasts|consensus)\\b",
            "\\b(topped|beat) (\\w+ )?(estimates|expectations|forecasts|consensus)\\b",
            "\\bearnings (beat|topped)\\b",
            "\\bbeat (analysts|wall street)\\b",
            "\\brecord (revenue|quarter|sales|profit|earnings)\\b",
            "\\brekord(umsatz|gewinn|quartal|ergebnis|absatz|zahlen)",
            "\\brekord (bei|beim) (umsatz|gewinn|absatz|ergebnis)",
            "\\bübertrifft (die |den )?\\w*(erwartungen|prognosen?|schätzungen|konsens)",
        ),
        Thema.AUFSTUFUNG to listOf(
            "\\bupgrades? [a-z.&\\' ]{0,40}? to (buy|overweight|outperform|neutral|equal.weight|market perform|sector perform|hold|positive|strong buy|accumulate)\\b",
            "\\bupgraded (to|by|at)\\b",
            "\\banalyst upgrades?\\b",
            "\\braises price target\\b",
            "\\bhochgestuft\\b",
            "\\bhebt (das )?kursziel",
            "\\bkaufempfehlung",
        ),
        Thema.ZULASSUNG to listOf(
            "\\bfda (approval|approves)\\b",
            "\\bwins approval\\b",
            "\\bzulassung (für|von|durch|erteilt)\\b",
            "\\berhält (die |eine )?zulassung\\b",
            "\\b(fda|ema)-zulassung",
        ),
        Thema.AUFTRAG to listOf(
            "\\b(wins|win|awarded|secures|lands|signs|inks|bags) (a |an |the )?([\$€£]?[\\d.,]+ ?(million|billion|m|b|bn)? )?(\\w+ ){0,3}contracts?\\b",
            "\\bcontract (win|award)s?\\b",
            "\\bpartnership (with|between)\\b",
            "\\bpartners with\\b",
            "\\b(signs|inks|strikes|wins|lands|secures|announces) (a |an )?(\\w+ ){0,2}deal (with|worth)\\b",
            "\\b(wins|receives|secures|lands) (an? )?(\\w+ )?orders? (from|worth)\\b",
            "\\bgroßauftrag",
            "\\b(milliarden|rekord)auftrag",
            "\\bauftrag (von|für|über|im wert)\\b",
            "\\berhält (einen |den )?aufträge?\\b",
            "\\bgewinnt (den )?auftrag\\b",
            "\\bauftragsgewinn",
            "\\b(sprung|plus|anstieg) beim [\\w-]*auftragseingang",
            "\\bpartnerschaft (mit|zwischen)\\b",
            "\\bkooperation (mit|zwischen)\\b",
            "\\bkooperiert mit\\b",
        ),
        Thema.RUECKKAUF to listOf(
            "\\bbuybacks?\\b",
            "\\brepurchase",
            "\\b(raises|hikes|boosts) (its )?dividend\\b",
            "\\brückkauf",
            "\\beigene aktien zurück\\b",
            "\\berhöht (die |seine |ihre )?dividende\\b",
        ),
        Thema.KI to listOf(
            "\\bai (demand|server|chip|boom|orders|growth)",
            "\\bartificial intelligence\\b",
            "\\bki[- ](nachfrage|boom|server|chip|rechenzentren)",
        ),
    )

    private val THEMEN_GENAU: Map<Thema, List<String>> = mapOf(
        Thema.BILANZ to listOf(
            "\\bBetrugs?\\b(?!\\s+(der|die|das|den|im|in|rund|etwa|knapp|nur|insgesamt|\\d))",
        ),
    )

    private val THEMEN: Map<Thema, List<Regex>> = THEMEN_MUSTER.mapValues { (_, l) -> l.map { Regex(it, RegexOption.IGNORE_CASE) } }
    private val THEMEN_EXAKT: Map<Thema, List<Regex>> = THEMEN_GENAU.mapValues { (_, l) -> l.map { Regex(it) } }

    // Themen einer Schlagzeile.
    fun themen(titel: String): List<Thema> =
        Thema.entries.filter { t -> THEMEN[t].orEmpty().any { it.containsMatchIn(titel) } || THEMEN_EXAKT[t].orEmpty().any { it.containsMatchIn(titel) } }

    // Schlagzeilen, die die Firma nennen: Kuerzel als Wort oder die ersten zwei Woerter des Namensstamms (mit oder ohne
    // Leerzeichen, „Super Micro“ und „Supermicro“). Yahoo ordnet einem Wert auch Meldungen ueber andere Firmen zu.
    // Bleibt nichts uebrig, zaehlen alle Schlagzeilen.
    fun zurFirma(schlagzeilen: List<Schlagzeile>, symbol: String, stamm: List<String>): List<Schlagzeile> {
        val kuerzel = symbol.substringBefore('.').lowercase()
        val worte = stamm.take(2)
        val muster = listOfNotNull(
            Regex("\\b" + Regex.escape(kuerzel) + "\\b", RegexOption.IGNORE_CASE).takeIf { kuerzel.length >= 2 },
            worte.takeIf { it.isNotEmpty() }?.let { w -> Regex(w.joinToString("\\s?") { Regex.escape(it) }, RegexOption.IGNORE_CASE) },
        )
        val passend = schlagzeilen.filter { s -> muster.any { it.containsMatchIn(s.titel) } }
        return passend.ifEmpty { schlagzeilen }
    }

    // Branchen, deren Gewinne stark mit der Konjunktur schwanken (Yahoo-Sektoren, dazu Halbleiter und Autos).
    private val ZYKLISCHE = setOf("industrials", "basic materials", "energy", "consumer cyclical", "real estate")
    private val ZYKLISCHE_INDUSTRIE = listOf("semiconductor", "auto", "airline", "steel", "chemical", "oil", "mining", "construction", "travel")

    fun zyklisch(branche: String, industrie: String): Boolean =
        branche.lowercase() in ZYKLISCHE || ZYKLISCHE_INDUSTRIE.any { industrie.lowercase().contains(it) }

    // Alle Aussagen: Schlagzeilen-Themen der letzten tage Tage (je Thema bis zu drei Belege), dann Kennzahlen.
    fun auswerten(f: Fakten, schlagzeilen: List<Schlagzeile>, jetzt: Long, tage: Int = 60): List<Punkt> {
        val ab = jetzt - tage * 86_400_000L
        val aus = ArrayList<Punkt>()
        val jeThema = LinkedHashMap<Thema, MutableList<Schlagzeile>>()
        schlagzeilen.filter { it.zeit == 0L || it.zeit >= ab }.sortedByDescending { it.zeit }.distinctBy { it.titel.trim().lowercase() }.forEach { s ->
            themen(s.titel).forEach { t -> jeThema.getOrPut(t) { mutableListOf() }.add(s) }
        }
        Thema.entries.forEach { t -> jeThema[t]?.let { aus += Punkt(t.seite, thema = t, belege = it.take(3)) } }

        fun k(kz: Kennzahl, vararg w: Double) { aus += Punkt(kz.seite, kennzahl = kz, werte = w.toList()) }
        if (f.kurs > 0 && f.kursziel > 0 && f.analysten >= 3) {
            val potenzial = (f.kursziel / f.kurs - 1) * 100
            if (potenzial >= 15 && (f.empfehlung == 0.0 || f.empfehlung <= 2.5)) k(Kennzahl.ANALYSTEN_POSITIV, f.kursziel, potenzial, f.analysten.toDouble())
            // Kursziel unter dem Kurs: skeptisch. Kursziel darueber, aber Note schwach: nur verhalten, der Text nennt beides
            else if (potenzial <= -5) k(Kennzahl.ANALYSTEN_SKEPTISCH, f.kursziel, potenzial, f.analysten.toDouble())
            else if (f.empfehlung >= 3.0) k(Kennzahl.ANALYSTEN_VERHALTEN, f.kursziel, potenzial, f.analysten.toDouble(), f.empfehlung)
        }
        if (f.umsatzWachstum >= 0.10) k(Kennzahl.WACHSTUM, f.umsatzWachstum * 100) else if (f.umsatzWachstum <= -0.05) k(Kennzahl.SCHRUMPFT, f.umsatzWachstum * 100)
        if (f.gewinnWachstum >= 0.15) k(Kennzahl.GEWINNWACHSTUM, f.gewinnWachstum * 100) else if (f.gewinnWachstum <= -0.15) k(Kennzahl.GEWINN_FAELLT, f.gewinnWachstum * 100)
        if (f.gewinnMarge >= 0.20) k(Kennzahl.MARGE_HOCH, f.gewinnMarge * 100) else if (f.gewinnMarge < 0) k(Kennzahl.VERLUST, f.gewinnMarge * 100)
        if (f.kasse > 0 && f.kasse > f.schulden) k(Kennzahl.NETTOKASSE, f.kasse - f.schulden)
        else if (f.schulden > 0 && f.marktwert > 0 && f.schulden - f.kasse > f.marktwert * 0.5) k(Kennzahl.SCHULDEN, f.schulden - f.kasse, (f.schulden - f.kasse) / f.marktwert * 100)
        if (f.freierCashflow > 0 && f.marktwert > 0 && f.freierCashflow / f.marktwert >= 0.03) k(Kennzahl.CASHFLOW_POSITIV, f.freierCashflow, f.freierCashflow / f.marktwert * 100)
        else if (f.freierCashflow < 0) k(Kennzahl.CASHFLOW_NEGATIV, f.freierCashflow)
        // Uebertroffen und verfehlt erst jenseits der Schwelle, die auch die Ampel der Geschaeftszahlen nutzt
        val letzte = Gewinnueberraschung.bilanz(f.ergebnisse)
        if (letzte.gezaehlt >= 3) {
            if (letzte.uebertroffen >= 3) k(Kennzahl.UEBERTROFFEN, letzte.uebertroffen.toDouble(), letzte.gezaehlt.toDouble())
            else if (letzte.verfehlt >= 2) k(Kennzahl.VERFEHLT, letzte.verfehlt.toDouble(), letzte.gezaehlt.toDouble())
        }
        if (f.kgv > 0 && f.kgvErwartet > 0 && f.kgvErwartet <= f.kgv * 0.8 + 1e-9) k(Kennzahl.GEWINNE_STEIGEN, f.kgv, f.kgvErwartet, (f.kgv / f.kgvErwartet - 1) * 100)
        if (f.kgv >= 50) k(Kennzahl.BEWERTUNG_HOCH, f.kgv)
        if (f.beta >= 1.5) k(Kennzahl.SCHWANKUNG, f.beta)
        if (zyklisch(f.branche, f.industrie)) k(Kennzahl.ZYKLISCH)
        if (f.ausschuettung > 1.0) k(Kennzahl.DIVIDENDE_UNGEDECKT, f.ausschuettung * 100)
        f.insider?.let { if (it >= 50) k(Kennzahl.INSIDER_KAUFEN, it.toDouble()) else if (it <= -50) k(Kennzahl.INSIDER_VERKAUFEN, it.toDouble()) }
        // Ab der Stufe „viele“ der Leerverkaufszeile: US-Werte ab 10 % des Streubesitzes, sonst ab 3 % des Grundkapitals
        f.leerverkauf?.let { if (Bewertungsblick.leerStufe(it, f.leerUs) >= 2) k(Kennzahl.LEERVERKAUF_HOCH, it, if (f.leerUs) 1.0 else 0.0) }
        f.fdaZulassungen?.let { if (it >= 1) k(Kennzahl.FDA_ZULASSUNG, it.toDouble()) }
        f.fdaRueckrufe?.let { if (it >= 1) k(Kennzahl.FDA_RUECKRUF, it.toDouble()) }
        // Wie im Signale-Kasten zaehlt die groessere Seite; Gleichstand sagt nichts ueber die Richtung
        f.kongress?.let { (kauf, verkauf) ->
            if (kauf > verkauf) k(Kennzahl.KONGRESS_KAUFT, kauf.toDouble(), verkauf.toDouble()) else if (verkauf > kauf) k(Kennzahl.KONGRESS_VERKAUFT, kauf.toDouble(), verkauf.toDouble())
        }
        f.stufen?.let { (hoch, ab) ->
            if (hoch > ab) k(Kennzahl.STUFEN_HOCH, hoch.toDouble(), ab.toDouble()) else if (ab > hoch) k(Kennzahl.STUFEN_AB, hoch.toDouble(), ab.toDouble())
        }
        f.warnzeichen?.let { if (it >= 1) k(Kennzahl.WARNZEICHEN, it.toDouble()) }
        // Kurs zum Jahreshoch: nahe dran spricht fuer intakten Trend, weit darunter fuer einen laufenden Abschwung
        if (f.zumHoch in 0.95..1.2) k(Kennzahl.NAHE_HOCH, (1 - f.zumHoch).coerceAtLeast(0.0) * 100)
        else if (f.zumHoch in 0.05..0.6) k(Kennzahl.WEIT_UNTER_HOCH, (1 - f.zumHoch) * 100)
        if (f.dividendenRendite >= 0.03 && f.ausschuettung in 0.01..0.8) k(Kennzahl.DIVIDENDE_SOLIDE, f.dividendenRendite * 100, f.ausschuettung * 100)
        if (f.eigenkapitalRendite >= 0.2 && f.gewinnMarge > 0) k(Kennzahl.RENDITE_HOCH, f.eigenkapitalRendite * 100)
        if (f.kgvErwartet in 1.0..12.0 && f.gewinnMarge > 0) k(Kennzahl.BEWERTUNG_NIEDRIG, f.kgvErwartet)
        val doppelt = doppelteThemen(aus.mapNotNull { it.kennzahl }.toSet(), f)
        return if (doppelt.isEmpty()) aus else aus.filter { it.thema == null || it.thema !in doppelt }
    }

    // Ein Anlass zaehlt in der Waage einmal: Steht die Kennzahl dazu in der Liste, entfaellt das Schlagzeilen-Thema.
    // Hoch- und Abstufungen (gezaehlt, 30 Tage) ersetzen die Schlagzeilen zu Stufungen, FDA-Zulassungen und
    // Rueckrufe aus dem Register die Schlagzeilen dazu, schwere Warnzeichen die Schlagzeilen zu Bilanz und Pleite
    // (je nach Art), uebertroffene Quartale die Schlagzeilen zu guten Zahlen.
    fun doppelteThemen(kennzahlen: Set<Kennzahl>, f: Fakten): Set<Thema> = buildSet {
        if (f.stufen != null) { add(Thema.AUFSTUFUNG); add(Thema.ABSTUFUNG) }
        if (Kennzahl.FDA_ZULASSUNG in kennzahlen) add(Thema.ZULASSUNG)
        if (Kennzahl.FDA_RUECKRUF in kennzahlen) add(Thema.RUECKRUF)
        if (Kennzahl.WARNZEICHEN in kennzahlen) addAll(f.warnThemen)
        if (Kennzahl.UEBERTROFFEN in kennzahlen) add(Thema.ZAHLEN)
    }
}
