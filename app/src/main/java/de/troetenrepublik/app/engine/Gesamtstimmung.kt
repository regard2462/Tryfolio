package de.troetenrepublik.app.engine

// Gesamtstimmung (seit 5.95): uebergeordnetes Stimmungsbarometer von 0 (extreme Angst) bis 100
// (extreme Gier) aus allen Stimmungsbarometern und Fruehwarnzeichen der App plus weiteren freien
// Quellen. Jeder Bestandteil wird vorher auf 0..100 normiert (hoch = Gier); eng verwandte Masse
// liegen in Gruppen, damit Verwandtes nicht doppelt zaehlt. Fehlende Bestandteile fallen heraus,
// ihr Gewicht geht an die uebrigen derselben Gruppe; fehlt eine ganze Gruppe, werden die
// Gruppengewichte der uebrigen Gruppen auf 100 % hochgerechnet. Keine Empfehlung.
object Gesamtstimmung {

    // Art der Normierung eines Bestandteils auf 0..100
    enum class Art { PERZENTIL, SPANNE, GRENZEN, FERTIG, AMPEL }

    // Ein Bestandteil mit Gewicht relativ zu den anderen Mitgliedern seiner Gruppe
    data class Mitglied(val schluessel: String, val gewicht: Double, val art: Art)

    // Gruppe eng verwandter Masse; Gewicht in Prozent, alle Gruppengewichte zusammen ergeben 100
    data class Gruppe(val schluessel: String, val gewicht: Double, val mitglieder: List<Mitglied>)

    // Begruendung der Gewichte: Schwankung am hoechsten, Optionspreise reagieren taeglich und gelten
    // als das verlaesslichste kurzfristige Angstmass; VIX, Terminstruktur, VVIX und MOVE haengen eng
    // zusammen, darum eine Gruppe. Kurs, Breite und Kredit je hoch: taeglich, lange Geschichte, je
    // eigene Seite des Marktes. CNN nur 10, weil der Index sich mit Kurs, Breite, Schwankung,
    // Optionen, Kredit und sicheren Haefen ueberschneidet. Umfrage 10 als einzige direkte Befragung
    // von Anlegern. Positionierung wochenweise und traege, Netz laut und wenig aussagekraeftig,
    // Krypto ein eigener Markt, Konjunktur langsam und eher Wirtschaft als Boerse, Hebel nur
    // vierteljaehrlich und verzoegert.
    val GRUPPEN = listOf(
        Gruppe("schwankung", 14.0, listOf(
            Mitglied("vix", 3.0, Art.PERZENTIL), Mitglied("vix_struktur", 2.0, Art.PERZENTIL),
            Mitglied("vvix", 1.0, Art.PERZENTIL), Mitglied("move", 1.0, Art.PERZENTIL))),
        Gruppe("kurs", 14.0, listOf(
            Mitglied("momentum", 2.0, Art.PERZENTIL), Mitglied("trend_sp500", 1.0, Art.PERZENTIL),
            Mitglied("trend_dax", 1.0, Art.PERZENTIL))),
        Gruppe("breite", 12.0, listOf(
            Mitglied("breite200", 2.0, Art.GRENZEN), Mitglied("hochtief", 2.0, Art.GRENZEN),
            Mitglied("rsp", 1.0, Art.PERZENTIL))),
        Gruppe("kredit", 12.0, listOf(
            Mitglied("hochzins", 2.0, Art.PERZENTIL), Mitglied("stress", 1.0, Art.PERZENTIL))),
        Gruppe("cnn", 10.0, listOf(Mitglied("cnn", 1.0, Art.FERTIG))),
        Gruppe("umfrage", 10.0, listOf(Mitglied("aaii", 1.0, Art.FERTIG))),
        Gruppe("sicher", 8.0, listOf(Mitglied("sicher", 1.0, Art.PERZENTIL))),
        Gruppe("position", 6.0, listOf(
            Mitglied("cot_sp500", 2.0, Art.SPANNE), Mitglied("cot_nasdaq", 1.0, Art.SPANNE))),
        Gruppe("netz", 4.0, listOf(
            Mitglied("wsb", 1.0, Art.FERTIG), Mitglied("sorgen", 1.0, Art.PERZENTIL))),
        Gruppe("krypto", 4.0, listOf(Mitglied("krypto", 1.0, Art.FERTIG))),
        Gruppe("konjunktur", 4.0, listOf(
            Mitglied("umich", 2.0, Art.PERZENTIL), Mitglied("zinskurve", 1.0, Art.AMPEL),
            Mitglied("sahm", 1.0, Art.GRENZEN))),
        Gruppe("hebel", 2.0, listOf(Mitglied("margin", 1.0, Art.GRENZEN))))

    // Alle Schluessel in Anzeigereihenfolge
    val ALLE: List<String> = GRUPPEN.flatMap { g -> g.mitglieder.map { it.schluessel } }

    // Gruppe eines Schluessels, null wenn unbekannt
    fun gruppeVon(schluessel: String): String? {
        for (g in GRUPPEN) if (g.mitglieder.any { it.schluessel == schluessel }) return g.schluessel
        return null
    }

    // Normierungsart eines Schluessels, null wenn unbekannt
    fun artVon(schluessel: String): Art? {
        for (g in GRUPPEN) for (m in g.mitglieder) if (m.schluessel == schluessel) return m.art
        return null
    }

    // Eingang eines Bestandteils: normierter Wert 0..100, Rohwert in seiner Einheit, Stand in ms
    data class Wert(val wert: Double, val roh: Double? = null, val stand: Long = 0L, val roh2: Double? = null)

    // Bestandteil im Ergebnis mit tatsaechlichem Anteil am Gesamtwert (alle zusammen ergeben 1)
    data class Bestandteil(
        val schluessel: String, val gruppe: String, val art: Art, val wert: Double,
        val roh: Double?, val roh2: Double?, val stand: Long, val anteil: Double)

    // Gesamtergebnis; gruppen sind Gruppenanteile, fehlen die Schluessel ohne Daten
    data class Ergebnis(
        val wert: Double, val stufe: Stimmungsbarometer.Stufe, val teile: List<Bestandteil>,
        val fehlen: List<String>, val gruppen: List<Pair<String, Double>>, val stand: Long) {
        val vorhanden: Int get() = teile.size
        val moeglich: Int get() = teile.size + fehlen.size
    }

    // Weniger Bestandteile oder Gruppen geben kein Ergebnis
    const val MINDESTENS = 3
    const val MINDESTENS_GRUPPEN = 2
    // Mindestsumme der Gruppengewichte mit Daten, sonst koennten wenige langsame Teile allein einen Gesamtwert ergeben
    const val MINDEST_GEWICHT = 30.0

    // Gesamtwert aus allen vorhandenen Bestandteilen. Unbekannte und nicht endliche Werte zaehlen
    // als fehlend, Werte werden auf 0..100 begrenzt. Das Gewicht fehlender Mitglieder geht an die
    // uebrigen der Gruppe, das fehlender Gruppen an die uebrigen Gruppen. teile und gruppen sind
    // nach Anteil absteigend sortiert, bei Gleichstand in Anzeige- bzw. Gruppenreihenfolge.
    fun rechnen(werte: Map<String, Wert>): Ergebnis? {
        val vorh = LinkedHashMap<String, Wert>()
        for (s in ALLE) {
            val w = werte[s] ?: continue
            if (!w.wert.isFinite()) continue
            vorh[s] = w.copy(wert = w.wert.coerceIn(0.0, 100.0))
        }
        if (vorh.size < MINDESTENS) return null
        val nutzbar = GRUPPEN.filter { g -> g.mitglieder.any { it.schluessel in vorh } }
        if (nutzbar.size < MINDESTENS_GRUPPEN) return null
        val gruppenSumme = nutzbar.sumOf { it.gewicht }
        if (gruppenSumme < MINDEST_GEWICHT) return null
        val ort = HashMap<String, Int>(ALLE.size)
        for ((i, s) in ALLE.withIndex()) ort[s] = i
        val ortGruppe = HashMap<String, Int>(GRUPPEN.size)
        for ((i, g) in GRUPPEN.withIndex()) ortGruppe[g.schluessel] = i
        val teile = ArrayList<Bestandteil>()
        var gesamt = 0.0
        for (g in nutzbar) {
            val da = g.mitglieder.filter { it.schluessel in vorh }
            val summe = da.sumOf { it.gewicht }
            for (m in da) {
                val w = vorh[m.schluessel] ?: continue
                val anteil = g.gewicht / gruppenSumme * (m.gewicht / summe)
                teile += Bestandteil(m.schluessel, g.schluessel, m.art, w.wert, w.roh, w.roh2, w.stand, anteil)
                gesamt += anteil * w.wert
            }
        }
        return Ergebnis(
            gesamt, Stimmungsbarometer.stufe(gesamt),
            teile.sortedWith(compareByDescending<Bestandteil> { it.anteil }.thenBy { ort[it.schluessel] ?: 0 }),
            ALLE.filter { it !in vorh },
            nutzbar.map { it.schluessel to it.gewicht / gruppenSumme }
                .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { ortGruppe[it.first] ?: 0 }),
            teile.maxOf { it.stand })
    }

    // ---- Normierung

    // Rang des letzten Werts unter den letzten fenster Werten (mit ihm selbst), mal 100. Bei gier
    // zaehlt der Anteil kleiner oder gleich dem letzten, sonst groesser oder gleich. Nicht endliche
    // Werte fallen vorher weg; weniger als mindestens Werte geben null.
    fun perzentil(reihe: List<Double>, gier: Boolean, fenster: Int = 260, mindestens: Int = 20): Double? {
        val werte = reihe.filter { it.isFinite() }.takeLast(fenster)
        if (werte.size < mindestens) return null
        val x = werte.last()
        val treffer = werte.count { if (gier) it <= x else it >= x }
        return 100.0 * treffer / werte.size
    }

    // Linear zwischen angst (0) und gier (100), beidseitig begrenzt. angst darf ueber gier liegen,
    // dann faellt die Skala; angst gleich gier gibt 50.
    fun grenzen(x: Double, angst: Double, gier: Double): Double =
        if (angst == gier) 50.0 else ((x - angst) / (gier - angst) * 100.0).coerceIn(0.0, 100.0)

    // Ampel als Wert: Gruen 70, Gelb 40, Rot 20
    fun ampel(a: Fruehwarnung.Ampel): Double = when (a) {
        Fruehwarnung.Ampel.GRUEN -> 70.0
        Fruehwarnung.Ampel.GELB -> 40.0
        Fruehwarnung.Ampel.ROT -> 20.0
    }

    // Zeitreihe als Bestandteil: Perzentil der Werte, roh = letzter Wert, Stand = letzte Zeit
    fun ausReihe(reihe: List<Pair<Long, Double>>, gier: Boolean, fenster: Int = 260, mindestens: Int = 20): Wert? {
        val letzte = reihe.lastOrNull() ?: return null
        val p = perzentil(reihe.map { it.second }, gier, fenster, mindestens) ?: return null
        return Wert(p, letzte.second, letzte.first)
    }

    // Fruehwarnzeichen als Bestandteil, Schluessel bleibt; roh = z.wert, Stand = letzte Verlaufszeit.
    // VIX_STRUKTUR, STRESS (Wochenwerte, zwei Jahre) und HOCHZINS (Tageswerte, zwei Jahre) als
    // Angst-Perzentil, die Trends als Gier-Perzentil, Zinskurve als Ampel, Sahm mit festen Grenzen.
    // Alles andere (auch VIX, der kommt aus dem Schwankungsbarometer) gibt null.
    fun ausZeichen(z: Fruehwarnung.Zeichen): Pair<String, Wert>? {
        val stand = z.verlauf.lastOrNull()?.first ?: 0L
        fun p(gier: Boolean, fenster: Int = 260, mindestens: Int = 20): Double? =
            perzentil(z.verlauf.map { it.second }, gier, fenster, mindestens)
        return when (z.schluessel) {
            Fruehwarnung.VIX_STRUKTUR -> p(gier = false)?.let { z.schluessel to Wert(it, z.wert, stand) }
            Fruehwarnung.STRESS -> p(gier = false, fenster = 104)?.let { z.schluessel to Wert(it, z.wert, stand) }
            Fruehwarnung.HOCHZINS -> p(gier = false, fenster = 520, mindestens = 60)?.let { z.schluessel to Wert(it, z.wert, stand) }
            Fruehwarnung.TREND_SP500, Fruehwarnung.TREND_DAX -> p(gier = true)?.let { z.schluessel to Wert(it, z.wert, stand) }
            Fruehwarnung.ZINSKURVE -> z.schluessel to Wert(ampel(z.ampel), z.wert, stand)
            Fruehwarnung.SAHM -> z.schluessel to Wert(grenzen(z.wert, 0.5, -0.1), z.wert, stand)
            else -> null
        }
    }

    // ---- Marktbreite

    // Kurslage eines S&P-500-Werts; null oder kleiner gleich 0 heisst unbekannt
    data class Kurslage(val kurs: Double, val linie200: Double?, val hoch52: Double?, val tief52: Double?)

    // Zaehlung ueber mehrere Kurslagen; nur Werte mit kurs ueber 0 zaehlen
    data class Breite(val ueber200: Int, val mitLinie: Int, val hochs: Int, val tiefs: Int, val mitSpanne: Int)

    // Hoch heisst hoechstens 3 % unter dem 52-Wochen-Hoch, tief hoechstens 3 % ueber dem -Tief
    fun breite(lagen: List<Kurslage>): Breite {
        var ueber200 = 0; var mitLinie = 0; var hochs = 0; var tiefs = 0; var mitSpanne = 0
        for (l in lagen) {
            if (l.kurs <= 0) continue
            val linie = l.linie200
            if (linie != null && linie > 0) {
                mitLinie++
                if (l.kurs >= linie) ueber200++
            }
            val hoch = l.hoch52
            val tief = l.tief52
            if (hoch != null && hoch > 0 && tief != null && tief > 0) {
                mitSpanne++
                if (l.kurs >= 0.97 * hoch) hochs++
                if (l.kurs <= 1.03 * tief) tiefs++
            }
        }
        return Breite(ueber200, mitLinie, hochs, tiefs, mitSpanne)
    }

    // Anteil ueber der 200-Tage-Linie, 20 % gilt als Angst (0) und 80 % als Gier (100); mindestens ist die kleinste Zahl Werte mit Linie
    fun breite200(b: Breite, stand: Long, mindestens: Int = 100): Wert? {
        if (b.mitLinie < mindestens) return null
        val anteil = 100.0 * b.ueber200 / b.mitLinie
        return Wert(grenzen(anteil, 20.0, 80.0), anteil, stand)
    }

    // Hochs minus Tiefs in Prozent aller Werte mit Spanne, feste Grenzen: -30 % ergeben 0 und +30 % ergeben 100.
    // Ein Verhaeltnis Hochs zu Hochs plus Tiefs laege im steigenden Markt fast immer bei Gier, weil Tiefs dann selten sind.
    fun hochTief(b: Breite, stand: Long, mindestens: Int = 100): Wert? {
        if (b.mitSpanne < mindestens) return null
        return Wert(grenzen(100.0 * (b.hochs - b.tiefs) / b.mitSpanne, -30.0, 30.0), b.hochs.toDouble(), stand, b.tiefs.toDouble())
    }

    // ---- Weitere Quellen

    // Seite der Verbraucherbefragung der University of Michigan bei FRED, fuer die Quellenzeile mit Link
    const val UMICH_SEITE = "https://fred.stlouisfed.org/series/UMCSENT"

    // Margin-Kredite: Wachstum gegen Vorjahr, -20 % gilt als Angst (0) und +30 % als Gier (100);
    // Stand ist in Tagen seit 1970 und wird zu Millisekunden.
    fun margin(m: Ersatzquellen.Margin): Wert =
        Wert(grenzen(m.wachstum, -20.0, 30.0), m.wachstum, m.stand * 86_400_000L)

    // Alle Eingaben aus den vorhandenen Barometern, Zeichen und Positionen; zusatz kommt unveraendert dazu
    fun eingaben(
        cnn: Stimmungsbarometer.Barometer?, aktien: Stimmungsbarometer.Barometer?, vix: Stimmungsbarometer.Barometer?,
        sicher: Stimmungsbarometer.Barometer?, aaii: Stimmungsbarometer.Barometer?, krypto: Stimmungsbarometer.Barometer?,
        wsb: Stimmungsbarometer.RedditBarometer?, sorgen: Stimmungsbarometer.Barometer?,
        zeichen: List<Fruehwarnung.Zeichen>, cot: List<Positionierung.Lage>, zusatz: Map<String, Wert>,
    ): Map<String, Wert> {
        val aus = LinkedHashMap<String, Wert>()
        cnn?.let { aus["cnn"] = Wert(it.wert, it.wert, it.stand) }
        // Eigener Aktienindex: Schwung und Breite als eigene Bestandteile
        aktien?.let { b ->
            b.teile.firstOrNull { it.schluessel == "momentum" }?.let { aus["momentum"] = Wert(it.wert, null, b.stand) }
            b.teile.firstOrNull { it.schluessel == "breite" }?.let { aus["rsp"] = Wert(it.wert, null, b.stand) }
        }
        vix?.let { aus["vix"] = Wert(it.wert, it.teile.firstOrNull()?.wert, it.stand) }
        sicher?.let { aus["sicher"] = Wert(it.wert, it.teile.firstOrNull()?.wert, it.stand) }
        aaii?.let { aus["aaii"] = Wert(it.wert, it.wert, it.stand) }
        krypto?.let { aus["krypto"] = Wert(it.wert, it.wert, it.stand) }
        // WSB nur bei klarer Lage, sonst waere der Wert geraten
        if (wsb != null && wsb.lage?.unklar != true)
            aus["wsb"] = Wert(wsb.barometer.wert, wsb.barometer.wert, wsb.barometer.stand)
        sorgen?.let { aus["sorgen"] = Wert(it.wert, null, it.stand) }
        for (z in zeichen) ausZeichen(z)?.let { aus[it.first] = it.second }
        for (l in cot) when (l.schluessel) {
            "sp500" -> aus["cot_sp500"] = Wert(l.index.toDouble(), l.anteil, l.stand)
            "nasdaq" -> aus["cot_nasdaq"] = Wert(l.index.toDouble(), l.anteil, l.stand)
        }
        aus.putAll(zusatz)
        return aus
    }
}
