package de.troetenrepublik.app.data

import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.Fmt
import kotlin.math.abs
import kotlin.math.ceil

// Kategorie-Widgets (0.73): fuer jede Kategorie der App ein Widget, das fuer den Startbildschirm gedacht ist: wenige,
// grosse Angaben statt der App-Seite. Dieses Objekt kennt weder Android noch das Netz: Arten und Ziele, der Zuschnitt
// der Karte auf die Groesse des Widgets und der Aufbau der Karten aus schon vorliegenden Werten.
object WidgetKarten {
    // Arten der neuen Widgets, zugleich Schluessel in WidgetDaten.karten.
    const val AUSBLICK = "ausblick"
    const val BEWEGUNGEN = "bewegungen"
    const val TRENDS = "trends"
    const val DURCHBRUECHE = "durchbrueche"
    const val AKTIENFINDER = "aktienfinder"
    const val COPYTRADING = "copytrading"
    const val PROGNOSE = "prognose"
    const val ORDERS = "orders"
    const val ANALYTICS = "analytics"
    const val WISSEN = "wissen"

    // Seit 0.77: Widgets zu Unterseiten und Werkzeugen (Lage, Fruehwarnzeichen, Marktphase, Blasen-Gefahr, Termine und die
    // Werkzeuge des Depots).
    const val LAGE = "lage"
    const val FRUEHWARNUNG = "fruehwarnung"
    const val MARKTPHASE = "marktphase"
    const val BLASEN = "blasen"
    const val TERMINE = "termine"
    const val PERFORMANCE = "performance"
    const val DEPOTCHECK = "depotcheck"
    const val STREUUNG = "streuung"
    const val UMSCHICHTEN = "umschichten"
    const val CRASHTEST = "crashtest"

    // Arten bestehender Widgets, die eine Kategorie schon zeigt (Namen wie im Widgets-Objekt der Plattform).
    const val WERTE = "werte"
    const val NEWS = "news"
    const val MAERKTE = "maerkte"
    const val HEATMAP = "heatmap"
    const val GLOBUS = "globus"
    const val STIMMUNG = "stimmung"
    const val DEPOT = "depot"
    const val DIVIDENDEN = "dividenden"

    // Seit 0.78: Widgets zu Abschnitten der Maerkte, des Copytradings, der Werkzeuge und der Wertpapierseite.
    const val THEMEN = "themen"
    const val INDEXBEWEGER = "indexbeweger"
    const val SEKTOREN = "sektoren"
    const val KONGRESSFLOW = "kongressflow"
    const val STILLHALTER = "stillhalter"
    const val SIGNALE = "signale"
    const val VOLATILITAET = "volatilitaet"

    // Seit 0.77 (AD8): Widgets zu 13F-Gesamtbild und Haus-Rangliste der Analysten.
    const val MEISTGEKAUFT = "meistgekauft"
    const val HAEUSER = "haeuser"

    val ARTEN_AD8 = listOf(MEISTGEKAUFT, HAEUSER)

    // Seit 0.77: Widgets zu Konvergenz, seltenen Insiderkaeufen, Insider-Simulator, Vorschlagsdepot, Branchen (je Branche),
    // Warnzeichen (je Wert) und Praesidialdokumenten.
    const val KONVERGENZ = "konvergenz"
    const val INSIDERSELTEN = "insiderselten"
    const val INSIDERSIM = "insidersim"
    const val VORSCHLAG = "vorschlag"
    const val BRANCHE = "branche"
    const val WARNZEICHEN = "warnzeichen"
    const val PRAESIDIAL = "praesidial"

    val ARTEN_ABSCHNITTE = listOf(KONVERGENZ, INSIDERSELTEN, INSIDERSIM, VORSCHLAG, BRANCHE, WARNZEICHEN, PRAESIDIAL)

    val ARTEN_078 = listOf(THEMEN, INDEXBEWEGER, SEKTOREN, KONGRESSFLOW, STILLHALTER, SIGNALE, VOLATILITAET)

    val NEUE_ARTEN = listOf(LAGE, FRUEHWARNUNG, MARKTPHASE, BLASEN, TERMINE, PERFORMANCE, DEPOTCHECK, STREUUNG, UMSCHICHTEN, CRASHTEST)

    // Karten, die aus dem Konto gerechnet werden und nirgends auf dem Geraet liegen bleiben.
    val KONTO_ARTEN = setOf(ORDERS, ANALYTICS, DEPOTCHECK, STREUUNG, UMSCHICHTEN, PERFORMANCE, CRASHTEST)

    val ARTEN = listOf(AUSBLICK, BEWEGUNGEN, TRENDS, DURCHBRUECHE, AKTIENFINDER, COPYTRADING, PROGNOSE, ORDERS, ANALYTICS, WISSEN) + NEUE_ARTEN + ARTEN_078 + ARTEN_AD8 + ARTEN_ABSCHNITTE

    // Alle Kategorien, die sich als Widget anlegen lassen, in der Reihenfolge der Uebersicht: einzigartige, grafische Widgets
    // zuerst (Tacho, Karte, Kacheln, Kurven, Balken), Tabellen und Listen zuletzt. Durchbrueche stehen gleich nach der Stimmung.
    val ANLEGBAR = listOf(
        STIMMUNG, DURCHBRUECHE, FRUEHWARNUNG, GLOBUS, HEATMAP, BLASEN, MARKTPHASE, CRASHTEST, AUSBLICK, KONVERGENZ, VOLATILITAET, TRENDS, STREUUNG,
        PERFORMANCE, DEPOTCHECK, UMSCHICHTEN, SEKTOREN, KONGRESSFLOW, THEMEN, PROGNOSE, DEPOT, SIGNALE, STILLHALTER, INDEXBEWEGER,
        ANALYTICS, BRANCHE, MEISTGEKAUFT, HAEUSER, WARNZEICHEN, LAGE, WISSEN, DIVIDENDEN, AKTIENFINDER, VORSCHLAG, INSIDERSIM,
        BEWEGUNGEN, WERTE, MAERKTE, COPYTRADING, ORDERS, TERMINE, INSIDERSELTEN, PRAESIDIAL, NEWS,
    )

    // Seite, die ein Tipp auf das Widget oeffnet.
    fun ziel(art: String): String = when (art) {
        AUSBLICK -> "maerkte/ausblick"
        BEWEGUNGEN -> "maerkte/bewegungen"
        TRENDS, DURCHBRUECHE -> "maerkte/trends"
        AKTIENFINDER -> "aktienfinder"
        COPYTRADING -> "copytrading"
        PROGNOSE -> "pm"
        ORDERS -> "orders"
        ANALYTICS -> WidgetZiel.ANALYTICS
        WISSEN -> "knowledge"
        DEPOT -> "depotverlauf"
        DIVIDENDEN -> WidgetZiel.ANALYTICS
        LAGE -> "lagebild"
        FRUEHWARNUNG, MARKTPHASE -> WidgetZiel.STIMMUNG
        BLASEN -> "maerkte/ausblick/blasen"
        TERMINE -> "discover/dividends"
        PERFORMANCE, DEPOTCHECK, STREUUNG, UMSCHICHTEN, CRASHTEST -> WidgetZiel.ANALYTICS
        THEMEN, SEKTOREN -> "maerkte/trends"
        INDEXBEWEGER -> WidgetZiel.heatmap("MARKT", 0)
        KONGRESSFLOW -> WidgetZiel.KONGRESS_FLOW
        STILLHALTER -> WidgetZiel.DERIVATE
        // Ohne gerechnete Karte (kein Wert) fuehrt der Tipp zur Suche, um einen Wert zu finden
        SIGNALE, VOLATILITAET -> "search"
        MEISTGEKAUFT -> "copytrading"
        HAEUSER -> WidgetZiel.HAEUSER
        KONVERGENZ -> WidgetZiel.KONVERGENZ
        INSIDERSIM -> WidgetZiel.WIRKT_DAS
        INSIDERSELTEN -> WidgetZiel.COPY_INSIDER
        PRAESIDIAL -> WidgetZiel.COPY_REGIERUNG
        VORSCHLAG, BRANCHE -> "aktienfinder"
        WARNZEICHEN -> "search"
        else -> ""
    }

    // Titel der Kategorie in der App (Kachel oder Reiter).
    fun titel(art: String): String = when (art) {
        WERTE -> S.watchlist
        NEWS -> S.news
        MAERKTE -> S.maerkteTitel
        HEATMAP -> S.heatmap
        GLOBUS -> S.globusTitel
        STIMMUNG -> S.widgetStimmung
        DEPOT -> S.portfolio
        DIVIDENDEN -> S.widgetDividenden
        AUSBLICK -> S.maerkteAusblick
        BEWEGUNGEN -> S.maerkteBewegungen
        TRENDS -> S.trendsTitel
        DURCHBRUECHE -> S.dbTitel
        AKTIENFINDER -> S.afTitel
        COPYTRADING -> S.copyTitle
        PROGNOSE -> S.pmTitle
        ORDERS -> S.orders
        ANALYTICS -> S.analytics
        WISSEN -> S.knowledge
        LAGE -> S.lbTitel
        FRUEHWARNUNG -> S.fwTitel
        MARKTPHASE -> S.mpTitel
        BLASEN -> S.widgetBlasenTitel
        TERMINE -> S.widgetTermineTitel
        PERFORMANCE -> S.ptTitle("PERFORMANCE")
        DEPOTCHECK -> S.ptTitle("CHECK")
        STREUUNG -> S.ptTitle("DIVERSIFICATION")
        UMSCHICHTEN -> S.ptTitle("REBALANCE")
        CRASHTEST -> S.ptTitle("CRASH")
        THEMEN -> S.korbTitel
        INDEXBEWEGER -> S.indexBeitragTitel
        SEKTOREN -> S.stTitel
        KONGRESSFLOW -> S.flTitel
        STILLHALTER -> S.ptTitle("STILLHALTER")
        SIGNALE -> S.sigTitel
        VOLATILITAET -> S.opVola
        MEISTGEKAUFT -> S.g13Gekauft
        HAEUSER -> S.hrTitel
        KONVERGENZ -> S.konTitel
        INSIDERSELTEN -> S.skTitel
        INSIDERSIM -> S.wdClusterTitel
        VORSCHLAG -> S.vsdTitel
        BRANCHE -> S.afAnsichtBranchen
        WARNZEICHEN -> S.widgetWarnTitel
        PRAESIDIAL -> S.pdTitel
        else -> art
    }

    // ------------------------------------------------------------ Zuschnitt auf die Groesse

    // Was ein Widget in seiner Groesse zeigt: Titel, die Zeilen, ob die kleinen Zeilen darunter stehen, ob die Standzeile
    // steht, und der Hinweis, wenn keine Zeile da ist.
    class Zuschnitt(
        val titel: Boolean, val zeilen: List<WidgetZeile>, val mitUnter: Boolean, val stand: Boolean, val hinweis: String,
        // Seit 0.77: Hauptaussage sichtbar, Hoehe der Grafik in dp (0: keine)
        val haupt: Boolean = false, val grafikHoehe: Int = 0,
        // Seit 0.77 (W1): belegte und verfuegbare Hoehe in dp (ohne Rand), fuer die Frage, ob die Standardgroesse gefuellt ist
        val genutzt: Float = 0f, val verfuegbar: Float = 0f,
    )

    // Platz am Rand, Hoehe von Titel und Standzeile und einer Textzeile in dp; Schriftgroessen wie im Layout.
    private const val RAND = 28f
    private const val TITEL_H = 20f
    private const val STAND_H = 18f
    private const val ZEILE_H = 17f
    private const val ZEILE_ABSTAND = 6f
    private const val UNTER_H = 14f
    private const val SCHRIFT = 13f
    private const val SCHRIFT_UNTER = 11f
    private const val SCHRIFT_TITEL = 11f
    private const val SCHRIFT_HAUPT = 26f
    private const val HAUPT_H = 32f
    private const val MAX_ZEILEN = 8
    private const val MAX_TEXTZEILEN = 3
    // Balken unter einer Zeile (Seit 0.78): 5 dp Hoehe und 3 dp Abstand
    private const val BALKEN_H = 8f
    // Abstand oben und unten um eine Schlagzeile im News-Widget (6 dp und 2 dp)
    private const val NACHRICHT_RAND = 8f

    // Breite eines Textes in dp bei sp Schriftgroesse, geschaetzt nach der Art der Zeichen; lieber etwas zu gross.
    fun textBreite(t: String, sp: Float, fett: Boolean = false): Float {
        var b = 0f
        for (c in t) b += when {
            c == ' ' -> 0.30f
            c in "iljtf.,:;|!'" -> 0.30f
            c in "mwMW" -> 0.88f
            c.isUpperCase() -> 0.68f
            c.isDigit() -> 0.58f
            else -> 0.54f
        }
        return b * sp * if (fett) 1.08f else 1f
    }

    // Zeilen, die ein Text bei platz dp Breite braucht (Umbruch an Leerzeichen, zu lange Woerter brechen mitten ab).
    fun textZeilen(t: String, platz: Float, sp: Float): Int {
        if (t.isBlank()) return 1
        if (platz <= 0f) return Int.MAX_VALUE
        var zeilen = 1
        var rest = platz
        val leer = textBreite(" ", sp)
        for (wort in t.split(' ').filter { it.isNotEmpty() }) {
            val w = textBreite(wort, sp)
            if (w > platz) {
                // ein Wort breiter als die Zeile bricht mitten im Wort um
                val volle = ceil(w / platz).toInt()
                zeilen += if (rest == platz) volle - 1 else volle
                rest = platz - (w - (volle - 1) * platz)
                continue
            }
            if (w <= rest) rest -= w + leer
            else { zeilen++; rest = platz - w - leer }
        }
        return zeilen
    }

    private fun laengstesWort(t: String, sp: Float): Float = t.split(' ').maxOfOrNull { textBreite(it, sp) } ?: 0f

    // Schriftgroesse des Geraets (fontScale) macht jeden Text breiter: der Platz schrumpft entsprechend.
    private fun textZeilenSkaliert(t: String, platz: Float, skala: Float): Int = textZeilen(t, platz / skala, SCHRIFT)

    private class Passend(val zeile: WidgetZeile, val hoehe: Float)

    // Passt die Zeile in die Breite innen? Erst mit dem Wert rechts neben dem Text (der Text darf bis zu drei Zeilen
    // umbrechen, kein Wort bricht mitten ab), sonst mit dem Wert in der kleinen Zeile darunter. Sonst null: die Zeile fehlt,
    // statt abgeschnitten zu werden.
    private fun anpassen(z: WidgetZeile, innen: Float, skala: Float, mitUnter: Boolean): Passend? {
        val p = anpassenZeile(z, innen, skala, mitUnter)
        if (p != null || z.ersatz.isBlank() || z.ersatz == z.text) return p
        return anpassenZeile(z.copy(text = z.ersatz, ersatz = ""), innen, skala, mitUnter)
    }

    private fun anpassenZeile(z: WidgetZeile, innen: Float, skala: Float, mitUnter: Boolean): Passend? {
        fun hoehe(n: Int, unter: Boolean) = (n * ZEILE_H + ZEILE_ABSTAND + (if (unter) UNTER_H else 0f) + (if (z.balken.isNotEmpty()) BALKEN_H else 0f)) * skala
        fun unterPasst(t: String) = textBreite(t, SCHRIFT_UNTER) * skala <= innen
        val wert = if (z.wert.isBlank()) 0f else textBreite(z.wert, SCHRIFT, fett = true) * skala + 8f
        val platz = innen - wert
        if (z.klemmen > 0) {
            // Der Wert bleibt neben dem Text; der Text zeigt hoechstens klemmen Zeilen
            if (platz < 60f) return null
            val unterOk = z.unter.isNotBlank() && unterPasst(z.unter)
            return Passend(if (z.unter.isNotBlank() && !unterOk) z.copy(unter = "") else z, hoehe(minOf(z.klemmen, textZeilenSkaliert(z.text, platz, skala)), mitUnter && unterOk))
        }
        val maxText = if (z.einzeilig) 1 else MAX_TEXTZEILEN
        val n = textZeilenSkaliert(z.text, platz, skala)
        if (platz > 0f && laengstesWort(z.text, SCHRIFT) * skala <= platz && n <= maxText) {
            // Eine einzeilige Zeile zeigt ihre kleine Zeile nur, wenn sie ganz hineinpasst
            val unterOk = z.unter.isNotBlank() && unterPasst(z.unter)
            return Passend(if (z.einzeilig && z.unter.isNotBlank() && !unterOk) z.copy(unter = "") else z, hoehe(n, mitUnter && unterOk))
        }
        if (z.wert.isBlank()) return null
        val stapel = listOf(z.wert, z.unter).filter { it.isNotBlank() }.joinToString(" · ")
        if (!unterPasst(stapel)) return null
        val n2 = textZeilenSkaliert(z.text, innen, skala)
        if (laengstesWort(z.text, SCHRIFT) * skala > innen || n2 > maxText) return null
        return Passend(z.copy(wert = "", unter = stapel, unterHoch = z.hoch, gestapelt = true), hoehe(n2, true))
    }

    // Bei einer Karte mit sortierter Liste (ausgewogen): so viele Zeilen wie passen, je zur Haelfte vom Anfang und vom Ende der
    // Liste, damit auch ein kleines Widget den staerksten und den schwaechsten Eintrag zeigt. Die Reihenfolge bleibt.
    private fun ausgewogeneAuswahl(zeilen: List<WidgetZeile>, frei: Float, innen: Float, skala: Float, mitUnter: Boolean): Pair<List<WidgetZeile>, Float> {
        for (n in minOf(zeilen.size, MAX_ZEILEN) downTo 1) {
            val wahl = zeilen.take((n + 1) / 2) + zeilen.takeLast(n / 2)
            var rest = frei
            val aus = ArrayList<WidgetZeile>()
            var passt = true
            for (z in wahl) {
                val p = anpassen(z, innen, skala, mitUnter)
                if (p == null || p.hoehe > rest) { passt = false; break }
                rest -= p.hoehe
                aus += p.zeile
            }
            if (passt) return aus to rest
        }
        return emptyList<WidgetZeile>() to frei
    }

    // Titel und Zeilen der Karte fuer ein Widget mit breite mal hoehe dp. skala ist die Schriftskala des Geraets. Was nicht
    // ganz hineinpasst, fehlt, statt abgeschnitten zu werden; die wichtigste Zeile steht immer vorn. leer ist der Hinweis,
    // wenn die Karte noch keinen eigenen hat.
    fun zuschneiden(karte: WidgetKarte?, breite: Int, hoehe: Int, skala: Float = 1f, leer: String = ""): Zuschnitt {
        val s = skala.coerceIn(0.8f, 2f)
        if (karte == null) return Zuschnitt(false, emptyList(), false, false, leer)
        val innen = (breite - RAND).coerceAtLeast(40f)
        val verfuegbar = (hoehe - RAND).coerceAtLeast(0f)
        var frei = hoehe - RAND
        val stand = hoehe >= 96 && karte.stand.isNotBlank() && textBreite(karte.stand, 10f) * s <= innen
        if (stand) frei -= STAND_H * s
        val titel = karte.titel.isNotBlank() && textBreite(karte.titel, SCHRIFT_TITEL, fett = true) * s <= innen
        if (titel) frei -= TITEL_H * s
        // Hauptaussage: bricht an Leerzeichen um (hoechstens zwei Zeilen), kein Wort bricht mitten ab
        var haupt = false
        if (karte.haupt.isNotBlank()) {
            val n = textZeilen(karte.haupt, innen / (s * 1.08f), SCHRIFT_HAUPT)
            val h = (n * HAUPT_H + if (karte.hauptUnter.isNotBlank()) UNTER_H + 2f else 0f) * s
            val wort = laengstesWort(karte.haupt, SCHRIFT_HAUPT) * 1.08f * s <= innen
            val unterPasst = karte.hauptUnter.isBlank() || textBreite(karte.hauptUnter, SCHRIFT_UNTER) * s <= innen
            if (n <= 2 && wort && unterPasst && h <= frei) { haupt = true; frei -= h }
        }
        // Grafik: nur mit Hauptaussage und wenn danach noch eine Zeile (falls es Zeilen gibt) hineinpasst
        var grafikHoehe = 0
        val g = karte.grafik
        if (g != null && (haupt || karte.haupt.isBlank())) {
            val natuerlich = when (g.art) {
                WidgetGrafik.TACHO -> (innen / 2f).coerceIn(34f, 96f)
                WidgetGrafik.AMPELN -> 16f
                WidgetGrafik.BALKEN -> 12f
                else -> 36f
            } + 6f
            val naechste = if (karte.zeilen.isEmpty()) 0f else ZEILE_H + ZEILE_ABSTAND
            if (natuerlich * s + naechste * s <= frei) { grafikHoehe = (natuerlich - 6f).toInt(); frei -= natuerlich * s }
        }
        if (karte.zeilen.isEmpty()) {
            val hinweis = karte.hinweis.ifBlank { leer }.takeIf { !haupt } ?: ""
            // Der Hinweis belegt etwa eine Textzeile
            return Zuschnitt(titel, emptyList(), false, stand, hinweis, haupt, grafikHoehe, verfuegbar - frei + if (hinweis.isBlank()) 0f else ZEILE_H * s, verfuegbar)
        }

        fun passend(mitUnter: Boolean): Pair<List<WidgetZeile>, Float> {
            if (karte.ausgewogen) return ausgewogeneAuswahl(karte.zeilen, frei, innen, s, mitUnter)
            val aus = ArrayList<WidgetZeile>()
            var rest = frei
            for (z in karte.zeilen) {
                if (aus.size >= MAX_ZEILEN) break
                val p = anpassen(z, innen, s, mitUnter) ?: continue
                if (p.hoehe > rest) break
                rest -= p.hoehe
                aus += p.zeile
            }
            return aus to rest
        }
        val (mit, restMit) = passend(true)
        val (ohne, restOhne) = passend(false)
        val hatUnter = karte.zeilen.any { it.unter.isNotBlank() }
        // Die kleinen Zeilen nur, solange mindestens zwei Zeilen (oder alle) daneben Platz haben
        val mitUnter = hatUnter && mit.size >= minOf(karte.zeilen.size, 2)
        val gewaehlt = if (mitUnter) mit else ohne
        val rest = if (mitUnter) restMit else restOhne
        if (gewaehlt.isEmpty()) {
            val hinweis = if (haupt) "" else karte.hinweis.ifBlank { leer }
            return Zuschnitt(titel, emptyList(), false, stand, hinweis, haupt, grafikHoehe, verfuegbar - frei + if (hinweis.isBlank()) 0f else ZEILE_H * s, verfuegbar)
        }
        return Zuschnitt(titel, gewaehlt, mitUnter, stand, "", haupt, grafikHoehe, verfuegbar - rest, verfuegbar)
    }

    // Wie viele Schlagzeilen ganz in ein Widget der Groesse breite mal hoehe dp passen (mindestens eine): Titel bis zu zwei
    // Zeilen, darunter die Quellzeile. Eine Meldung, die nur zum Teil hineinpasst, zaehlt nicht.
    fun nachrichtenAnzahl(titel: List<String>, breite: Int, hoehe: Int, skala: Float = 1f): Int {
        val s = skala.coerceIn(0.8f, 2f)
        val innen = (breite - RAND).coerceAtLeast(40f)
        var frei = hoehe - RAND - STAND_H * s
        var n = 0
        for (t in titel) {
            val zeilen = minOf(2, textZeilen(t, innen / s, SCHRIFT))
            val h = (NACHRICHT_RAND + zeilen * ZEILE_H + UNTER_H) * s
            if (h > frei) break
            frei -= h
            n++
        }
        return maxOf(n, minOf(1, titel.size))
    }
}

// Aufbau der Karten aus Werten, die die App schon hat. Alle Texte fertig in der Sprache der App.
object WidgetKartenBau {
    private fun richtung(v: Double): Boolean? = if (v > 0.0) true else if (v < 0.0) false else null

    // Ausblick: Richtung fuer Aktien nach Wochen und Monaten als ein Wort (Urteil als Nummer wie Ausblickgesamt.Urteil: 0
    // aufwaerts, 1 seitwaerts, 2 abwaerts, 3 gemischt, 4 offen), dazu die gewichtigsten Gruende (Name, spricht dafuer).
    fun ausblick(wochen: Int, monate: Int, gruende: List<Pair<String, Boolean>>, stand: String, am: Long): WidgetKarte {
        fun farbe(u: Int): Boolean? = when (u) { 0 -> true; 2 -> false; else -> null }
        val zeilen = listOf(
            WidgetZeile(S.agWochen, S.agWort(wochen), farbe(wochen), einzeilig = true),
            WidgetZeile(S.agMonate, S.agWort(monate), farbe(monate), einzeilig = true),
        ) + gruende.take(4).map { (name, dafuer) ->
            WidgetZeile(name, if (dafuer) S.widgetGrundDafuer else S.widgetGrundDagegen, dafuer, einzeilig = true)
        }
        return WidgetKarte(S.maerkteAusblick + " · " + S.agMarkt(0), zeilen, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.AUSBLICK), am = am)
    }

    class Bewegung(val symbol: String, val name: String, val prozent: Double)

    // Bewegungen: groesste Gewinner und Verlierer des Tages im Wechsel, damit auch ein kleines Widget beide Seiten zeigt.
    fun bewegungen(gewinner: List<Bewegung>, verlierer: List<Bewegung>, stand: String, am: Long): WidgetKarte {
        val zeilen = ArrayList<WidgetZeile>()
        for (i in 0 until maxOf(gewinner.size, verlierer.size).coerceAtMost(3)) {
            for (b in listOfNotNull(gewinner.getOrNull(i), verlierer.getOrNull(i))) {
                zeilen += WidgetZeile(b.name, Fmt.signedPct(b.prozent), richtung(b.prozent), ziel = WidgetZiel.instrument(b.symbol))
            }
        }
        return WidgetKarte(
            S.maerkteBewegungen, zeilen, hinweis = if (zeilen.isEmpty()) S.widgetKeineDaten else "", stand = stand,
            ziel = WidgetKarten.ziel(WidgetKarten.BEWEGUNGEN), am = am,
        )
    }

    // Wert mit Erwaehnungen der letzten 24 Stunden und den Erwaehnungen davor (null, wenn unbekannt).
    class Trendwert(val symbol: String, val name: String, val erwaehnungen: Int, val vorher: Int?)

    // Trends: die meistdiskutierten Werte mit der Richtung der Erwaehnungen gegenueber den 24 Stunden davor.
    fun trends(werte: List<Trendwert>, stand: String, am: Long): WidgetKarte {
        val zeilen = werte.take(6).map { w ->
            val delta = w.vorher?.takeIf { it > 0 }?.let { (w.erwaehnungen.toDouble() / it - 1) * 100 }
            WidgetZeile(
                w.name, delta?.let { Fmt.signedPct(it, 0) } ?: S.copyNew, delta?.let { richtung(it) },
                unter = S.trErwaehnungen(w.erwaehnungen), ziel = WidgetZiel.instrument(w.symbol),
            )
        }
        return WidgetKarte(
            S.trendsTitel, zeilen, hinweis = if (zeilen.isEmpty()) S.widgetKeineDaten else "", stand = stand,
            ziel = WidgetKarten.ziel(WidgetKarten.TRENDS), am = am,
        )
    }

    // Schlagzeile mit Thema und fertigem Alter, beziehungsweise ein erwarteter Meilenstein mit Marke (Jahr oder Stand).
    class Durchbruch(val titel: String, val thema: String, val zusatz: String)

    // Durchbrueche: die neuesten Durchbrueche mit Thema, sonst die naechsten erwarteten Meilensteine.
    fun durchbrueche(neu: List<Durchbruch>, erwartet: List<Durchbruch>, stand: String, am: Long): WidgetKarte {
        val zeilen = if (neu.isNotEmpty()) neu.take(6).map { WidgetZeile(it.titel, unter = listOf(it.thema, it.zusatz).filter { t -> t.isNotBlank() }.joinToString(" · "), klemmen = 2) }
        else erwartet.take(6).map { WidgetZeile(it.titel, unter = listOf(S.dbErwartet, it.zusatz, it.thema).filter { t -> t.isNotBlank() }.joinToString(" · "), klemmen = 2) }
        return WidgetKarte(
            S.dbTitel, zeilen, hinweis = S.widgetDurchbruchLeer, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.DURCHBRUECHE), am = am,
        )
    }

    class Fund(val symbol: String, val name: String, val punkte: Double)

    // Aktienfinder: die bestbewerteten Werte des gewaehlten Markts mit Punktzahl 0 bis 100. Der Markt steht vor dem Stand.
    fun aktienfinder(markt: String, werte: List<Fund>, stand: String, am: Long): WidgetKarte {
        val zeilen = werte.take(6).map { w ->
            WidgetZeile(w.name, Fmt.num(w.punkte, 0), if (w.punkte >= 60) true else if (w.punkte <= 40) false else null, ziel = WidgetZiel.instrument(w.symbol))
        }
        return WidgetKarte(
            S.afTitel, zeilen, hinweis = S.widgetFinderLeer, stand = listOf(markt, stand).filter { it.isNotBlank() }.joinToString(" · "),
            ziel = WidgetKarten.ziel(WidgetKarten.AKTIENFINDER), am = am,
        )
    }

    // Letztes Geschaeft einer gefolgten Person: kauf true Kauf, false Verkauf, null Tausch oder unbekannt.
    class Handel(val person: String, val text: String, val kauf: Boolean?, val datum: String, val sortiert: Long = 0L)

    // Platz der Rangliste: Vorsprung vor dem S&P 500 als Punkte (0,05 = 5 Punkte) und Zahl der Kaeufe.
    class Platz(val name: String, val vorsprung: Double, val kaeufe: Int)

    // Copytrading: neueste Geschaefte der gefolgten Personen, sonst die Rangliste der Abgeordneten und Senatoren.
    fun copytrading(handel: List<Handel>, rangliste: List<Platz>, stand: String, am: Long): WidgetKarte {
        val ziel = WidgetKarten.ziel(WidgetKarten.COPYTRADING)
        if (handel.isNotEmpty()) return WidgetKarte(
            S.copyTitle, handel.sortedByDescending { it.sortiert }.take(6).map { WidgetZeile(it.person, it.text, it.kauf, unter = it.datum) },
            stand = stand, ziel = ziel, am = am,
        )
        val zeilen = rangliste.take(5).map { r ->
            val punkte = (if (r.vorsprung >= 0) "+" else "−") + Fmt.num(abs(r.vorsprung) * 100, 1)
            WidgetZeile(r.name, S.rgPunkte(punkte), richtung(r.vorsprung), unter = S.rgAusKaeufen(r.kaeufe))
        }
        return WidgetKarte(
            S.copyTitle, zeilen, hinweis = S.widgetCopyLeer, stand = listOf(S.rgTitel, stand).filter { it.isNotBlank() }.joinToString(" · "),
            ziel = ziel, am = am,
        )
    }

    // Wette: Titel, Wahrscheinlichkeit der fuehrenden Seite (0 bis 1), Veraenderung in Prozentpunkten, Name des Ausgangs.
    class Wette(val titel: String, val chance: Double, val aenderung: Double?, val ausgang: String, val ziel: String = "")

    // Prognosemaerkte: die wichtigsten Wetten mit Wahrscheinlichkeit; darunter die Veraenderung in 24 Stunden.
    fun prognose(wetten: List<Wette>, stand: String, am: Long, titel: String = S.pmTitle): WidgetKarte {
        val zeilen = wetten.take(5).map { w ->
            val d = w.aenderung?.takeIf { abs(it) >= 0.5 }
            val aenderung = d?.let { (if (it > 0) "+" else "−") + Fmt.num(abs(it), 0) + " " + S.fwPunkte }
            WidgetZeile(
                w.titel, prozent(w.chance), unter = listOfNotNull(w.ausgang.takeIf { it.isNotBlank() }, aenderung).joinToString(" · "),
                unterHoch = if (w.ausgang.isBlank() && d != null) d > 0 else null, ziel = w.ziel,
            )
        }
        return WidgetKarte(
            titel, zeilen, hinweis = S.pmUnreachable, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.PROGNOSE), am = am,
        )
    }

    // Wahrscheinlichkeit wie in der App: unter 1 Prozent und ueber 99 Prozent mit Zeichen.
    fun prozent(p: Double): String = when {
        p < 0.005 -> "< 1" + if (Fmt.decimalSep == ',') " %" else "%"
        p > 0.995 -> "> 99" + if (Fmt.decimalSep == ',') " %" else "%"
        else -> Fmt.pct(p * 100, 0)
    }

    class Auftrag(val name: String, val symbol: String, val text: String, val wert: String)
    class Plan(val name: String, val betrag: String, val naechster: Long, val datum: String)

    // Orders: offene Orders (Kauf oder Verkauf, Stueckzahl, Limit) und Sparplaene mit dem naechsten Termin, Plaene nach
    // Termin geordnet; Orders vor den Plaenen.
    fun orders(auftraege: List<Auftrag>, plaene: List<Plan>, stand: String, am: Long, variante: String = ""): WidgetKarte {
        val nurOrders = variante == WidgetVorgabe.ORDERS_ORDERS
        val nurPlaene = variante == WidgetVorgabe.ORDERS_PLAENE
        val o = if (nurPlaene) emptyList() else auftraege.take(if (nurOrders) 5 else 3).map { WidgetZeile(it.name, it.wert, unter = it.text, ziel = WidgetZiel.instrument(it.symbol)) }
        val p = if (nurOrders) emptyList() else plaene.sortedBy { if (it.naechster > 0) it.naechster else Long.MAX_VALUE }.take(if (nurPlaene) 5 else 3)
            .map { WidgetZeile(it.name, it.betrag, unter = if (it.datum.isBlank()) "" else S.nextExecution + " " + it.datum) }
        val titel = if (nurPlaene) S.savingsPlans else S.orders
        val ziel = if (nurPlaene) "plans" else WidgetKarten.ziel(WidgetKarten.ORDERS)
        return WidgetKarte(
            titel, o + p, hinweis = if (nurPlaene) S.widgetPlaeneLeer else if (nurOrders) S.noOpenOrders else S.widgetOrdersLeer, stand = stand, ziel = ziel, am = am,
        )
    }

    // Analytics: Rendite seit Kauf, groesster Rueckgang vom Hoch und groesste Position mit Anteil am Depot. Null, wo es
    // (noch) keinen Wert gibt.
    fun analytics(rendite: Double?, rueckgang: Double?, groessteName: String?, groessterAnteil: Double?, stand: String, am: Long): WidgetKarte {
        val zeilen = listOfNotNull(
            rendite?.let { WidgetZeile(S.widgetRendite, Fmt.signedPct(it, 1), richtung(it)) },
            // Der Wert stammt aus Portfolio Performance (Zeitraum Max), der Tipp fuehrt dorthin
            rueckgang?.let { WidgetZeile(S.widgetRueckgang, Fmt.signedPct(-abs(it), 1), if (abs(it) > 0.0) false else null, ziel = WidgetZiel.PERFORMANCE) },
            if (groessteName != null && groessterAnteil != null) WidgetZeile(groessteName, Fmt.pct(groessterAnteil, 0), unter = S.largestPosition) else null,
        )
        return WidgetKarte(
            S.analytics, zeilen, hinweis = S.widgetKeinePositionen, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.ANALYTICS), am = am,
        )
    }

    class WissenLektion(val id: String, val titel: String, val minuten: Int)
    class WissenModul(val nr: Int, val titel: String, val lektionen: List<WissenLektion>)

    // Finanzwissen: die naechste Lektion des begonnenen Moduls (das der zuletzt abgeschlossenen Lektion; ohne Fortschritt
    // das erste) und die zwei danach. Ist das Modul fertig, geht es im naechsten weiter.
    fun wissen(module: List<WissenModul>, fertig: Set<String>, zuletzt: String?, am: Long): WidgetKarte {
        val ziel = WidgetKarten.ziel(WidgetKarten.WISSEN)
        val alle = module.sumOf { it.lektionen.size }
        val erledigt = module.sumOf { m -> m.lektionen.count { it.id in fertig } }
        val start = module.indexOfFirst { m -> m.lektionen.any { it.id == zuletzt } }.takeIf { it >= 0 } ?: 0
        // Ab dem begonnenen Modul das erste, in dem noch eine Lektion offen ist
        val index = (start until module.size).firstOrNull { i -> module[i].lektionen.any { it.id !in fertig } }
            ?: module.indices.firstOrNull { i -> module[i].lektionen.any { it.id !in fertig } }
        if (index == null) return WidgetKarte(S.knowledge, hinweis = S.widgetWissenFertig, stand = S.widgetWissenStand(erledigt, alle), ziel = ziel, am = am)
        val modul = module[index]
        val offen = modul.lektionen.filter { it.id !in fertig }
        // Nach der zuletzt abgeschlossenen Lektion weiter, wenn sie in diesem Modul liegt
        val nach = modul.lektionen.indexOfFirst { it.id == zuletzt }
        val reihe = (if (nach >= 0) offen.filter { l -> modul.lektionen.indexOf(l) > nach } + offen.filter { l -> modul.lektionen.indexOf(l) < nach } else offen)
        val zeilen = reihe.take(3).mapIndexed { i, l ->
            WidgetZeile(l.titel, S.widgetWissenMinuten(l.minuten), unter = if (i == 0) S.widgetWissenModul(modul.nr, modul.titel) else "")
        }
        return WidgetKarte(S.knowledge, zeilen, stand = S.widgetWissenStand(erledigt, alle), ziel = ziel, am = am)
    }

    // ------------------------------------------------------------ Seit 0.77

    private fun ton(ampel: Int): Int = when (ampel) { 0 -> WidgetGrafik.GRUEN; 1 -> WidgetGrafik.GELB; else -> WidgetGrafik.ROT }

    // Anzeichen des Blasen-Detektors: Name, Wert in der Einheit der Seite, rot oder gelb.
    class Anzeichen(val name: String, val wert: String, val rot: Boolean)

    // Blasen-Gefahr: die Stufe gross (0 gering, 1 erhoeht, 2 hoch), darunter wie viele Anzeichen rot sind, ein Tacho in drei
    // Stufen mit dem Zeiger bei anteil (0 bis 1, Anteil der moeglichen Punkte) und die drei staerksten Anzeichen mit Wert.
    // grenzeErhoeht und grenzeHoch sind die Anteile, ab denen die Stufe wechselt.
    fun blasen(
        stufe: Int, rot: Int, gesamt: Int, anteil: Double, staerkste: List<Anzeichen>, grenzeErhoeht: Double, grenzeHoch: Double,
        stand: String, am: Long,
    ): WidgetKarte {
        val s = stufe.coerceIn(0, 2)
        val farbe = listOf(WidgetGrafik.GRUEN, WidgetGrafik.GELB, WidgetGrafik.ROT)
        return WidgetKarte(
            S.widgetBlasenTitel,
            staerkste.take(5).map { WidgetZeile(it.name, it.wert, if (it.rot) false else null, einzeilig = true) },
            hinweis = S.widgetKeineDaten, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.BLASEN), am = am,
            haupt = S.widgetBlasenStufe(s), hauptTon = farbe[s], hauptUnter = S.widgetBlasenRot(rot, gesamt),
            grafik = WidgetGrafik(
                WidgetGrafik.TACHO, listOf(grenzeErhoeht, grenzeHoch - grenzeErhoeht, 1.0 - grenzeHoch), farbe, anteil.coerceIn(0.0, 1.0),
            ),
        )
    }

    // Fruehwarnzeichen: Name, Wert, Ampel (0 gruen, 1 gelb, 2 rot).
    class Warnzeichen(val name: String, val wert: String, val ampel: Int)

    // Fruehwarnzeichen: eine Reihe Ampelpunkte, die Zahl der roten und gelben gross, darunter die roten und gelben mit Wert.
    fun fruehwarnung(zeichen: List<Warnzeichen>, stand: String, am: Long): WidgetKarte {
        val rot = zeichen.count { it.ampel == 2 }
        val gelb = zeichen.count { it.ampel == 1 }
        // Alle Zeichen, die roten zuerst, dann die gelben und die gruenen, soweit der Platz reicht
        val geordnet = zeichen.sortedByDescending { it.ampel }
        return WidgetKarte(
            S.fwTitel,
            geordnet.take(8).map { WidgetZeile(it.name, it.wert, when (it.ampel) { 2 -> false; 0 -> true; else -> null }, einzeilig = true) },
            hinweis = S.widgetKeineDaten, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.FRUEHWARNUNG), am = am,
            haupt = S.widgetFwHaupt(rot, gelb, zeichen.size), hauptTon = if (rot > 0) WidgetGrafik.ROT else if (gelb > 0) WidgetGrafik.GELB else WidgetGrafik.GRUEN,
            grafik = if (zeichen.isEmpty()) null else WidgetGrafik(WidgetGrafik.AMPELN, ton = zeichen.map { ton(it.ampel) }),
        )
    }

    // Phase eines Marktes: Name, Art (BULLE, BAER), ob Korrektur im Bullenmarkt, Abstand zum Hoch in Prozent.
    class Phase(val name: String, val art: String, val korrektur: Boolean, val vomHoch: Double)

    private fun phasenTon(p: Phase): Int = if (p.art == "BAER") WidgetGrafik.ROT else if (p.korrektur) WidgetGrafik.GELB else WidgetGrafik.GRUEN

    // Marktphase: Phase des ersten Markts gross mit Abstand zum Hoch, eine Reihe Punkte fuer alle Maerkte, je Markt eine Zeile.
    fun marktphase(phasen: List<Phase>, stand: String, am: Long): WidgetKarte {
        val erste = phasen.firstOrNull()
        return WidgetKarte(
            S.mpTitel,
            phasen.take(8).map { WidgetZeile(it.name, Fmt.signedPct(it.vomHoch, 1), richtung(it.vomHoch), unter = S.widgetPhase(it.art, it.korrektur), einzeilig = true) },
            hinweis = S.widgetKeineDaten, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.MARKTPHASE), am = am,
            haupt = erste?.let { S.widgetPhase(it.art, it.korrektur) }.orEmpty(), hauptTon = erste?.let { phasenTon(it) } ?: WidgetGrafik.NEUTRAL,
            hauptUnter = erste?.let { it.name + " · " + Fmt.signedPct(it.vomHoch, 1) }.orEmpty(),
            grafik = if (phasen.size < 2) null else WidgetGrafik(WidgetGrafik.AMPELN, ton = phasen.map { phasenTon(it) }),
        )
    }

    // Aussage der Lage: Thema, Name der Kennzahl, Wert als Text, ob auffaellig.
    class Ereignis(val thema: String, val name: String, val wert: String, val auffaellig: Boolean)

    // Lage: die drei wichtigsten Ereignisse (auffaellige zuerst) mit Wert; rot, wo die Kennzahl auffaellig ist.
    fun lage(ereignisse: List<Ereignis>, stand: String, am: Long): WidgetKarte {
        val l = ereignisse.sortedByDescending { it.auffaellig }.take(8)
        val auff = ereignisse.count { it.auffaellig }
        return WidgetKarte(
            S.lbTitel, l.map { WidgetZeile(it.name, it.wert, if (it.auffaellig) false else null, unter = it.thema, einzeilig = true) },
            hinweis = S.widgetKeineDaten, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.LAGE), am = am,
            haupt = if (ereignisse.isEmpty()) "" else S.widgetLageAuffaellig(auff), hauptTon = if (auff > 0) WidgetGrafik.ROT else WidgetGrafik.GRUEN,
        )
    }

    // Termin: Name, Datum fertig formatiert, Zusatz (Betrag, Quartal, Land), ob aus Depot oder Merkliste, Tag seit 1970.
    class Termin(val name: String, val datum: String, val zusatz: String, val eigen: Boolean, val tag: Long)

    // Termine: die naechsten fuenf, eigene und beobachtete Werte bei gleichem Tag vorn und gruen markiert. art ist die
    // Variante (dividenden, wirtschaft, quartale, ipo).
    fun termine(art: String, termine: List<Termin>, stand: String, am: Long): WidgetKarte {
        val titel = when (art) {
            WidgetVorgabe.TERMIN_WIRTSCHAFT -> S.nkWiTitel
            WidgetVorgabe.TERMIN_QUARTALE -> S.qzTitel
            WidgetVorgabe.TERMIN_IPO -> S.ipos
            else -> S.nkDivTitel
        }
        val ziel = when (art) {
            WidgetVorgabe.TERMIN_WIRTSCHAFT -> "discover/economy"
            WidgetVorgabe.TERMIN_QUARTALE -> "discover/earnings"
            WidgetVorgabe.TERMIN_IPO -> "discover/ipos"
            else -> "discover/dividends"
        }
        val l = termine.sortedWith(compareBy<Termin> { it.tag }.thenByDescending { it.eigen }).take(8)
        return WidgetKarte(
            titel, l.map { WidgetZeile(it.name, it.datum, if (it.eigen) true else null, unter = it.zusatz, einzeilig = true) },
            hinweis = S.widgetTermineLeer, stand = stand, ziel = ziel, am = am,
        )
    }

    // Performance: Rendite seit Jahresbeginn gross mit Kurve, darunter ein Jahr, Zinsfuss und groesster Rueckgang (alle als
    // Prozent, null wo es keinen Wert gibt).
    fun performance(ytd: Double?, jahr: Double?, izf: Double?, rueckgang: Double?, kurve: List<Double>, am: Long): WidgetKarte {
        val zeilen = listOfNotNull(
            jahr?.let { WidgetZeile(S.widgetEinJahr, Fmt.signedPct(it, 1), richtung(it)) },
            izf?.let { WidgetZeile(S.widgetZinsfuss, Fmt.signedPct(it, 1), richtung(it)) },
            rueckgang?.let { WidgetZeile(S.widgetRueckgang, Fmt.signedPct(-abs(it), 1), if (abs(it) > 0.0) false else null) },
        )
        return WidgetKarte(
            S.ptTitle("PERFORMANCE"), zeilen, hinweis = S.widgetKeineDaten, ziel = WidgetKarten.ziel(WidgetKarten.PERFORMANCE), am = am,
            haupt = ytd?.let { Fmt.signedPct(it, 1) }.orEmpty(),
            hauptTon = ytd?.let { if (it > 0) WidgetGrafik.GRUEN else if (it < 0) WidgetGrafik.ROT else WidgetGrafik.NEUTRAL } ?: WidgetGrafik.NEUTRAL,
            hauptUnter = if (ytd == null) "" else S.widgetZeitJahr,
            grafik = if (kurve.size < 2) null else WidgetGrafik(
                WidgetGrafik.KURVE, kurve, listOf(if (kurve.last() >= kurve.first()) WidgetGrafik.GRUEN else WidgetGrafik.ROT),
            ),
        )
    }

    // Befund des Depot-Checks: Titel, Stufe (0 gut, 1 Achtung, 2 kritisch, 3 Hinweis).
    class Befund(val titel: String, val stufe: Int)

    // Depot-Check: wie viele Regeln im gruenen Bereich, eine Reihe Ampelpunkte, darunter die schlechtesten Befunde.
    fun depotcheck(befunde: List<Befund>, am: Long): WidgetKarte {
        val bewertet = befunde.filter { it.stufe < 3 }
        val gut = bewertet.count { it.stufe == 0 }
        val schlecht = bewertet.filter { it.stufe > 0 }.sortedByDescending { it.stufe }
        val anteil = if (bewertet.isEmpty()) 1.0 else gut.toDouble() / bewertet.size
        return WidgetKarte(
            S.ptTitle("CHECK"),
            // Erst die Regeln mit Befund, dann die im gruenen Bereich, soweit der Platz reicht
            (schlecht.map { WidgetZeile(it.titel, if (it.stufe == 2) S.widgetStufeKritisch else S.widgetStufeAchtung, false, einzeilig = true) } +
                bewertet.filter { it.stufe == 0 }.map { WidgetZeile(it.titel, S.widgetStufeGut, true, einzeilig = true) }).take(8),
            hinweis = S.widgetKeinePositionen, ziel = WidgetKarten.ziel(WidgetKarten.DEPOTCHECK), am = am,
            haupt = if (bewertet.isEmpty()) "" else S.lbVonGesamt(gut.toString(), bewertet.size.toString()),
            hauptTon = if (anteil >= 0.75) WidgetGrafik.GRUEN else if (anteil >= 0.5) WidgetGrafik.GELB else WidgetGrafik.ROT,
            hauptUnter = if (bewertet.isEmpty()) "" else S.widgetCheckGruen,
            grafik = if (bewertet.isEmpty()) null else WidgetGrafik(WidgetGrafik.AMPELN, ton = bewertet.map { when (it.stufe) { 0 -> WidgetGrafik.GRUEN; 1 -> WidgetGrafik.GELB; else -> WidgetGrafik.ROT } }),
        )
    }

    // Anteil einer Anlageklasse am Depot (0 bis 1).
    class Anteil(val name: String, val anteil: Double)

    // Streuung: Anteil der groessten Position gross, ein Balken nach Anlageklassen und die Klassen darunter.
    fun streuung(groessteName: String?, groessterAnteil: Double?, klassen: List<Anteil>, am: Long): WidgetKarte {
        val l = klassen.filter { it.anteil > 0.0 }.sortedByDescending { it.anteil }
        return WidgetKarte(
            S.ptTitle("DIVERSIFICATION"), l.take(8).map { WidgetZeile(it.name, Fmt.pct(it.anteil * 100, 0), einzeilig = true) },
            hinweis = S.widgetKeinePositionen, ziel = WidgetKarten.ziel(WidgetKarten.STREUUNG), am = am,
            haupt = groessterAnteil?.let { Fmt.pct(it * 100, 0) }.orEmpty(),
            hauptTon = groessterAnteil?.let { if (it >= 0.35) WidgetGrafik.ROT else if (it >= 0.2) WidgetGrafik.GELB else WidgetGrafik.GRUEN } ?: WidgetGrafik.NEUTRAL,
            hauptUnter = if (groessteName == null) "" else S.largestPosition + " · " + groessteName,
            grafik = if (l.isEmpty()) null else WidgetGrafik(WidgetGrafik.BALKEN, l.map { it.anteil }, l.indices.map { 4 + it % 6 }),
        )
    }

    // Kategorie im Umschichten: Name, Ist- und Zielanteil in Prozent (0 bis 100).
    class Abweichung(val name: String, val ist: Double, val ziel: Double)

    // Umschichten: groesste Abweichung von den Zielgewichten gross, darunter je Kategorie die Abweichung in Handelsrichtung
    // (zu wenig = Plus und gruen, zu viel = Minus und rot) mit Ist und Ziel.
    fun umschichten(kategorien: List<Abweichung>, am: Long): WidgetKarte {
        val l = kategorien.sortedByDescending { abs(it.ist - it.ziel) }
        val max = l.firstOrNull()?.let { abs(it.ist - it.ziel) }
        return WidgetKarte(
            S.ptTitle("REBALANCE"),
            l.take(8).map {
                val d = it.ziel - it.ist
                WidgetZeile(
                    it.name, (if (d > 0.05) "+" else if (d < -0.05) "−" else "") + Fmt.num(abs(d), 1) + " " + S.fwPunkte, if (d > 0.05) true else if (d < -0.05) false else null,
                    unter = Fmt.pct(it.ist, 1) + " → " + Fmt.pct(it.ziel, 1), einzeilig = true,
                )
            },
            hinweis = S.widgetKeinePositionen, ziel = WidgetKarten.ziel(WidgetKarten.UMSCHICHTEN), am = am,
            haupt = max?.let { Fmt.num(it, 1) + " " + S.fwPunkte }.orEmpty(),
            hauptTon = max?.let { if (it < 2.0) WidgetGrafik.GRUEN else if (it < 5.0) WidgetGrafik.GELB else WidgetGrafik.ROT } ?: WidgetGrafik.NEUTRAL,
            hauptUnter = if (max == null) "" else S.widgetGroessteAbweichung,
        )
    }

    // Krise: Name, Aenderung des Depots in dieser Krise als Anteil (-0,3 = minus 30 Prozent).
    class Krise(val name: String, val aenderung: Double)

    // Crash-Test: der schlimmste Fall in Prozent gross, darunter die weiteren Krisen.
    fun crashtest(krisen: List<Krise>, am: Long): WidgetKarte {
        val l = krisen.sortedBy { it.aenderung }
        val schlimm = l.firstOrNull()
        return WidgetKarte(
            S.ptTitle("CRASH"), l.drop(1).take(7).map { WidgetZeile(it.name, Fmt.signedPct(it.aenderung * 100, 0), richtung(it.aenderung), einzeilig = true) },
            hinweis = S.widgetKeineDaten, ziel = WidgetKarten.ziel(WidgetKarten.CRASHTEST), am = am,
            haupt = schlimm?.let { Fmt.signedPct(it.aenderung * 100, 0) }.orEmpty(),
            hauptTon = schlimm?.let { if (it.aenderung <= -0.3) WidgetGrafik.ROT else if (it.aenderung <= -0.15) WidgetGrafik.GELB else WidgetGrafik.GRUEN } ?: WidgetGrafik.NEUTRAL,
            hauptUnter = schlimm?.name.orEmpty(),
        )
    }


    // ------------------------------------------------------------ Seit 0.78

    // Kuerzel ohne Boersenendung und ohne Zeichen der Indizes, kurz genug fuer die Titelzeile.
    private fun kuerzel(symbol: String): String = symbol.substringBefore('.').removePrefix("^").ifBlank { symbol }

    // Titel mit Zusatz: das Kuerzel, bei reinem Zahlenkuerzel der Name; ohne Namen steht nur der Titel.
    private fun titelMit(titel: String, symbol: String, name: String): String {
        val z = if (de.troetenrepublik.app.engine.HeatmapBeschriftung.nurZiffern(symbol)) name.trim() else kuerzel(symbol)
        return if (z.isEmpty()) titel else "$titel · $z"
    }

    private fun ton(v: Double): Int = if (v > 0) WidgetGrafik.GRUEN else if (v < 0) WidgetGrafik.ROT else WidgetGrafik.NEUTRAL

    // Balken von der Mitte aus: links der Rueckgang (rot), rechts der Anstieg (gruen), beide im Verhaeltnis zu max.
    private fun mittig(v: Double, max: Double): List<Double> {
        if (max <= 0.0) return listOf(0.0, 0.0)
        val a = (abs(v) / max).coerceIn(0.0, 1.0)
        return if (v < 0) listOf(a, 0.0) else listOf(0.0, a)
    }

    // Korb der Themenkoerbe: Name, Median der Aenderung in Prozent, Zahl steigender und aller Werte.
    class Korb(val name: String, val median: Double, val steigend: Int, val anzahl: Int)

    // Themenkoerbe: wie viele Koerbe im Plus liegen gross, darunter die Koerbe nach Median sortiert, je mit einem Balken fuer
    // den Anteil steigender Werte. Ein kleines Widget zeigt den staerksten und den schwaechsten Korb.
    fun themen(koerbe: List<Korb>, stand: String, am: Long): WidgetKarte {
        val l = koerbe.filter { it.anzahl > 0 }.sortedByDescending { it.median }
        val plus = l.count { it.median > 0 }
        return WidgetKarte(
            S.korbTitel,
            l.map { k ->
                WidgetZeile(
                    k.name, Fmt.signedPct(k.median, 1), richtung(k.median), unter = S.korbSteigen(k.steigend, k.anzahl),
                    balken = listOf(k.steigend.toDouble() / k.anzahl), einzeilig = true,
                )
            },
            hinweis = S.widgetKeineDaten, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.THEMEN), am = am, ausgewogen = true,
            haupt = if (l.isEmpty()) "" else S.lbVonGesamt(plus.toString(), l.size.toString()),
            hauptTon = if (plus * 2 > l.size) WidgetGrafik.GRUEN else if (plus * 2 < l.size) WidgetGrafik.ROT else WidgetGrafik.NEUTRAL,
            hauptUnter = if (l.isEmpty()) "" else S.widgetKoerbePlus,
        )
    }

    // Wert mit Anteil an der Bewegung des Index in Prozentpunkten (Gewicht mal Aenderung).
    class Treiber(val kuerzel: String, val symbol: String, val punkte: Double, val ersatz: String = "")

    // Bewegt den Index: die Gesamtbewegung gross, darunter die Treiber nach oben und nach unten im Wechsel (der groesste
    // Treiber nach oben zuerst) als Balken von der Mitte. quelle ist der Index der Heatmap, name sein Titel.
    fun indexBeweger(name: String, quelle: String, gesamt: Double, hoch: List<Treiber>, runter: List<Treiber>, stand: String, am: Long): WidgetKarte {
        val max = (hoch + runter).maxOfOrNull { abs(it.punkte) } ?: 0.0
        val zeilen = ArrayList<WidgetZeile>()
        for (i in 0 until maxOf(hoch.size, runter.size).coerceAtMost(4)) {
            for (t in listOfNotNull(hoch.getOrNull(i), runter.getOrNull(i))) {
                zeilen += WidgetZeile(t.kuerzel, Fmt.signedPunkte(t.punkte, 2), richtung(t.punkte), ziel = WidgetZiel.instrument(t.symbol), balken = mittig(t.punkte, max), einzeilig = true, ersatz = t.ersatz)
            }
        }
        return WidgetKarte(
            S.indexBeitragTitel, zeilen, hinweis = S.widgetKeineDaten, stand = stand, ziel = WidgetZiel.heatmap(quelle, 0), am = am,
            haupt = if (zeilen.isEmpty()) "" else Fmt.signedPct(gesamt, 2), hauptTon = ton(gesamt), hauptUnter = if (zeilen.isEmpty()) "" else name,
        )
    }

    // Branche (Branchen-ETF): Name und Aenderung des Tages in Prozent.
    class Sektor(val name: String, val tag: Double)

    // Sektoren: wie viele Branchen im Plus liegen gross, darunter alle Branchen nach Tagesaenderung sortiert, je mit einem Balken
    // von der Mitte. Ein kleines Widget zeigt die staerkste und die schwaechste.
    fun sektoren(l: List<Sektor>, stand: String, am: Long): WidgetKarte {
        val s = l.sortedByDescending { it.tag }
        val max = s.maxOfOrNull { abs(it.tag) } ?: 0.0
        val plus = s.count { it.tag > 0 }
        return WidgetKarte(
            S.stTitel, s.map { WidgetZeile(it.name, Fmt.signedPct(it.tag, 1), richtung(it.tag), balken = mittig(it.tag, max), einzeilig = true) },
            hinweis = S.widgetKeineDaten, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.SEKTOREN), am = am, ausgewogen = true,
            haupt = if (s.isEmpty()) "" else S.lbVonGesamt(plus.toString(), s.size.toString()),
            hauptTon = if (plus * 2 > s.size) WidgetGrafik.GRUEN else if (plus * 2 < s.size) WidgetGrafik.ROT else WidgetGrafik.NEUTRAL,
            hauptUnter = if (s.isEmpty()) "" else S.widgetSektorenPlus,
        )
    }

    // Kauf- und Verkaufsseite eines Sektors im Kongress: Summen (Dollar, Mitte der Spannen) und Zahl der Geschaefte.
    class FlussSektor(val name: String, val kauf: Double, val verkauf: Double, val kaeufe: Int, val verkaeufe: Int)

    private fun vorzeichen(v: Double, text: String): String = (if (v < 0) "−" else if (v > 0) "+" else "") + text

    // Kongress-Flow: der Saldo aus Kaeufen und Verkaeufen gross, darunter die Sektoren mit der groessten Summe, je mit einem
    // Balken von der Mitte (links Verkaeufe, rechts Kaeufe, im Verhaeltnis zur groessten Seite) und dem Saldo als Wert.
    fun kongressFlow(sektoren: List<FlussSektor>, stand: String, am: Long): WidgetKarte {
        val l = sektoren.sortedByDescending { it.kauf + it.verkauf }.take(6)
        val max = sektoren.maxOfOrNull { maxOf(it.kauf, it.verkauf) } ?: 0.0
        val saldo = sektoren.sumOf { it.kauf - it.verkauf }
        return WidgetKarte(
            S.flTitel,
            l.map { f ->
                val netto = f.kauf - f.verkauf
                WidgetZeile(
                    f.name, vorzeichen(netto, Fmt.dollarKompakt(abs(netto))), richtung(netto), unter = S.widgetFlussZaehlung(f.kaeufe, f.verkaeufe), einzeilig = true,
                    balken = if (max <= 0.0) emptyList() else listOf((f.verkauf / max).coerceIn(0.0, 1.0), (f.kauf / max).coerceIn(0.0, 1.0)),
                )
            },
            hinweis = S.widgetFlussLeer, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.KONGRESSFLOW), am = am,
            haupt = if (l.isEmpty()) "" else vorzeichen(saldo, Fmt.dollarKompakt(abs(saldo))), hauptTon = ton(saldo),
            hauptUnter = if (l.isEmpty()) "" else S.widgetFlussNetto,
        )
    }

    // Ein Wert aus dem Stillhalter-Werkzeug: Name, Kuerzel, Call oder Put, Basispreis, Praemie und Verfall fertig formatiert,
    // Rendite im Jahr als Anteil (0,2 = 20 Prozent).
    // Strike und Praemie in USD, Verfall in ms; formatiert wird beim Bau der Karte, damit sie einen Sprachwechsel mitmacht
    class Stillhalt(val name: String, val symbol: String, val call: Boolean, val strike: Double, val praemie: Double, val verfall: Long, val rendite: Double)

    // Stillhalter: die drei Werte mit der hoechsten Rendite im Jahr, je Wert der bessere von Put und Call. Die beste Rendite gross,
    // die Zeilen mit Balken im Verhaeltnis zur besten.
    fun stillhalter(werte: List<Stillhalt>, stand: String, am: Long): WidgetKarte {
        val l = werte.groupBy { it.symbol }.map { (_, v) -> v.maxBy { it.rendite } }.sortedByDescending { it.rendite }.take(4)
        val max = l.firstOrNull()?.rendite ?: 0.0
        return WidgetKarte(
            S.ptTitle("STILLHALTER"),
            l.map { w ->
                WidgetZeile(
                    w.name + " " + (if (w.call) S.slCall else S.slPut) + " " + Fmt.price(w.strike, "USD"), Fmt.pct(w.rendite * 100, 1),
                    einzeilig = true, ersatz = w.symbol.substringBefore('.') + " " + (if (w.call) S.slCall else S.slPut) + " " + Fmt.price(w.strike, "USD"),
                    unter = S.slPraemie + " " + Fmt.price(w.praemie, "USD") + " · " + Fmt.dayMonthNum(w.verfall), ziel = WidgetZiel.instrument(w.symbol),
                    balken = if (max <= 0.0) emptyList() else listOf((w.rendite / max).coerceIn(0.0, 1.0)),
                )
            },
            hinweis = S.widgetStillhalterLeer, stand = stand, ziel = WidgetKarten.ziel(WidgetKarten.STILLHALTER), am = am,
            haupt = if (l.isEmpty()) "" else Fmt.pct(max * 100, 1), hauptUnter = if (l.isEmpty()) "" else S.slRenditeJahr,
        )
    }

    // Zeile des Signale-Kastens: Name und Wert fertig formatiert, die Verkaufs- und die Kaufseite als Groessen (Betrag oder
    // Zahl, nur im Verhaeltnis zueinander).
    class Signal(val name: String, val wert: String, val verkauf: Double, val kauf: Double)

    // Signale eines Werts: die Zeilen des Kastens der Wertpapierseite, je mit einem Balken von der Mitte (links die Verkaufs-,
    // rechts die Kaufseite, die groessere Seite voll). Keine Wertung, keine Farbe im Wert.
    fun signale(symbol: String, zeilen: List<Signal>, stand: String, am: Long, name: String = ""): WidgetKarte = WidgetKarte(
        titelMit(S.sigTitel, symbol, name),
        zeilen.take(4).map { z ->
            val m = maxOf(z.kauf, z.verkauf)
            // Der Wert steht in der kleinen Zeile unter dem Namen: er ist zu lang fuer die Zeile des Namens
            WidgetZeile(z.name, unter = z.wert, balken = if (m <= 0.0) emptyList() else listOf(z.verkauf / m, z.kauf / m))
        },
        hinweis = S.widgetSignaleLeer, stand = stand, ziel = WidgetZiel.instrument(symbol), am = am,
    )

    // Volatilitaet eines Werts: das Verhaeltnis von IV30 zu realisierter Volatilitaet gross, darunter IV30 und realisiert als Balken
    // im Verhaeltnis zum groesseren und, sobald vorhanden, der IV-Rang (Anteil 0 bis 1).
    fun volatilitaet(symbol: String, iv30: Double?, rv30: Double?, verhaeltnis: Double?, rang: Double?, stand: String, am: Long, name: String = ""): WidgetKarte {
        val max = maxOf(iv30 ?: 0.0, rv30 ?: 0.0)
        fun balken(v: Double) = if (max <= 0.0) emptyList() else listOf((v / max).coerceIn(0.0, 1.0))
        val zeilen = listOfNotNull(
            iv30?.let { WidgetZeile(S.opIv30, Fmt.pct(it * 100, 1), balken = balken(it), einzeilig = true) },
            rv30?.let { WidgetZeile(S.opRv30, Fmt.pct(it * 100, 1), balken = balken(it), einzeilig = true) },
            rang?.let { WidgetZeile(S.opIvRang, Fmt.pct(it * 100, 0), balken = listOf(it.coerceIn(0.0, 1.0)), einzeilig = true) },
        )
        return WidgetKarte(
            titelMit(S.opVola, symbol, name), zeilen, hinweis = S.widgetVolaLeer, stand = stand, ziel = WidgetZiel.instrument(symbol), am = am,
            haupt = verhaeltnis?.let { Fmt.num(it, 2) }.orEmpty(), hauptUnter = if (verhaeltnis == null) "" else S.opIvZuRv,
        )
    }

    // ------------------------------------------------------------ Seit 0.77 (AD8)

    // Wertpapier der Liste "Alle Fonds im Quartal": Bezeichnung fertig gewaehlt (kurz, ohne Leerzeichen), Zahl der Netto-Kaeufer
    // und Zufluss in Dollar.
    class Gekauft(val bezeichnung: String, val netto: Int, val zufluss: Double)

    // Meistgekauft: die Werte mit den meisten Netto-Kaeufern, je mit Zahl, Zufluss darunter und einem Balken im Verhaeltnis
    // zum staerksten Wert. Quartal und "Netto-Kaeufer" stehen in der Standzeile.
    fun meistgekauft(werte: List<Gekauft>, quartal: String, am: Long): WidgetKarte {
        val l = werte.filter { it.netto > 0 }.take(8)
        val max = l.maxOfOrNull { it.netto } ?: 0
        return WidgetKarte(
            S.g13Gekauft,
            l.map { w ->
                WidgetZeile(
                    // Rechts die Zahl der Fonds; die kleine Zeile nennt die Einheit und den Zufluss, ohne die Zahl zu wiederholen
                    w.bezeichnung, Fmt.num(w.netto.toDouble(), 0), true, unter = S.widgetFonds("").trim() + " · " + S.widgetZufluss(Fmt.dollarKompakt(w.zufluss)),
                    balken = if (max <= 0) emptyList() else listOf(w.netto.toDouble() / max), einzeilig = true,
                )
            },
            hinweis = S.widgetGekauftLeer, stand = if (l.isEmpty()) "" else S.widgetGekauftStand(quartal),
            ziel = WidgetKarten.ziel(WidgetKarten.MEISTGEKAUFT), am = am,
        )
    }

    // Haus der Rangliste: Name, Trefferanteil (0 bis 1) und Zahl der Urteile.
    class Haus(val name: String, val quote: Double, val urteile: Int)

    // Haus-Rangliste: die drei Haeuser mit dem hoechsten Trefferanteil, je mit Anteil, Zahl der Urteile darunter und Balken.
    fun haeuser(l: List<Haus>, am: Long): WidgetKarte {
        val drei = l.take(6)
        return WidgetKarte(
            S.hrTitel,
            drei.map { h ->
                WidgetZeile(
                    h.name, Fmt.pct(h.quote * 100, 0), unter = S.hrUrteile(Fmt.num(h.urteile.toDouble(), 0)),
                    balken = listOf(h.quote.coerceIn(0.0, 1.0)), einzeilig = true,
                )
            },
            hinweis = S.hrKeine, stand = if (drei.isEmpty()) "" else S.widgetTrefferanteil, ziel = WidgetKarten.ziel(WidgetKarten.HAEUSER),
            am = am,
        )
    }
}
