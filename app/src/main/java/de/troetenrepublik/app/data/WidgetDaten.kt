package de.troetenrepublik.app.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// Inhalt der Widgets auf dem Startbildschirm: fertig formatiert und in der Sprache der App, damit
// das Widget nur noch Texte setzt. Mit App-Sperre steht nur hinweis da, keine Betraege.
@Serializable
data class WidgetDaten(
    val stand: String = "",
    val titelDepot: String = "",
    val titelWerte: String = "",
    // Text, der anstelle der Werte erscheint.
    val hinweis: String = "",
    // App-Sperre eingeschaltet: alle Widgets zeigen nur ein Schloss und hinweis.
    val gesperrt: Boolean = false,
    val depotWert: String = "",
    // Dieselbe Summe ohne Nachkommastellen, fuer schmale Widgets (der grosse Betrag wird nie verkleinert)
    val depotWertKurz: String = "",
    val depotAenderung: String = "",
    val depotHoch: Boolean = true,
    val werte: List<WidgetWert> = emptyList(),
    // Tagesverlauf des Depots fuer die Kurslinie (ausgeduennt); leer, solange er fehlt.
    val depotKurve: List<Double> = emptyList(),
    // Positionen im Depot, groesste zuerst: Quelle „Depot“ des Widgets „Werte“.
    val titelBestand: String = "",
    val bestand: List<WidgetWert> = emptyList(),
    val bestandHinweis: String = "",
    // Einzelwerte je Symbol, mit Tagesverlauf; einzelStandard gilt fuer Widgets ohne eigene Wahl.
    val einzel: Map<String, WidgetWert> = emptyMap(),
    val einzelStandard: String = "",
    // Gewinner und Verlierer des Tages unter den Positionen.
    val titelMover: String = "",
    val gewinner: List<WidgetWert> = emptyList(),
    val verlierer: List<WidgetWert> = emptyList(),
    val moverHinweis: String = "",
    // Dividenden: erhalten im laufenden Jahr, erwartet in zwoelf Monaten, die naechsten Zahlungen.
    val titelDividenden: String = "",
    val divErhaltenText: String = "",
    val divErhalten: String = "",
    val divErwartetText: String = "",
    val divErwartet: String = "",
    val divNaechste: List<WidgetWert> = emptyList(),
    val divHinweis: String = "",
    // Beschriftungen des Schnellzugriffs in der Reihenfolge von SCHNELL_ZIELE.
    val schnell: List<String> = emptyList(),
    // Seit 5.94: Beschriftung je Ziel aus SCHNELL_AUSWAHL, die Kacheln sind je Widget waehlbar.
    val schnellNamen: Map<String, String> = emptyMap(),
    // Wichtige Indizes, Gold und Bitcoin mit Tagesaenderung.
    val titelMaerkte: String = "",
    val maerkte: List<WidgetWert> = emptyList(),
    // Seit 5.4: Depot je Zeitraum (ZEIT_HEUTE, ZEIT_JAHR, ZEIT_KAUF) fuer das Depot-Widget.
    val depotZeitraeume: Map<String, WidgetZeitraum> = emptyMap(),
    // Werte fuer Widgets mit eigener Liste, je Symbol.
    val listenWerte: Map<String, WidgetWert> = emptyMap(),
    // Stimmungsbarometer je Schluessel (STIMMUNG_AKTIEN, STIMMUNG_KRYPTO, seit 5.95 STIMMUNG_GESAMT).
    val titelStimmung: String = "",
    val stimmung: Map<String, WidgetStimmung> = emptyMap(),
    // Schlagzeilen zu den Positionen, neueste zuerst.
    val titelNews: String = "",
    val news: List<WidgetNachricht> = emptyList(),
    val newsHinweis: String = "",
    // Seit 5.94: die wichtigsten Schlagzeilen aller Quellen (wie der Reiter „Wichtigste“), neueste zuerst.
    val titelNewsWichtig: String = "",
    val newsWichtig: List<WidgetNachricht> = emptyList(),
    val newsWichtigHinweis: String = "",
    // Seit 0.68: alle Schlagzeilen (NEWS_ALLE) und je eigenem Feed (newsFeed(kennung)), neueste zuerst, mit Titel je
    // Inhalt; newsListenLaedt, solange eine gewaehlte Liste noch fehlt, newsListenKeine bei leerer Liste.
    val newsListen: Map<String, List<WidgetNachricht>> = emptyMap(),
    val newsListenTitel: Map<String, String> = emptyMap(),
    val newsListenLaedt: String = "",
    val newsListenKeine: String = "",
    // Seit 0.68: Heatmaps je Auswahl (Schluessel heatmapSchluessel), Kacheln wie in der App; heatmapHinweis, solange eine fehlt.
    val heatmaps: Map<String, WidgetHeatmap> = emptyMap(),
    val heatmapHinweis: String = "",
    // Seit 0.69: Werte fuer das Widget „Globus“ je Einfaerbung (GlobusModus.name), darin je Land (alpha-3) der Wert in Prozent.
    val globus: Map<String, Map<String, Double>> = emptyMap(),
    val globusTitel: Map<String, String> = emptyMap(),
    // Seit 0.73: Karten der Kategorie-Widgets je Art (WidgetKarten.ARTEN), fertig formatiert wie alles andere.
    val karten: Map<String, WidgetKarte> = emptyMap(),
    val kartenHinweis: String = "",
) {
    companion object {
        // Ziele der Knoepfe im Schnellzugriff (Routen der App).
        val SCHNELL_ZIELE = listOf("search", "orders", "maerkte", "news")

        // Alle Ziele, aus denen der Schnellzugriff vier waehlen kann (5.94).
        val SCHNELL_AUSWAHL = SCHNELL_ZIELE + listOf("heatmap", "activity", "plans", "rueckblick", "depotverlauf")

        // Inhalt des News-Widgets: Schlagzeilen zu den Positionen oder nur die wichtigsten (5.94).
        const val NEWS_DEPOT = "depot"
        const val NEWS_WICHTIG = "wichtig"
        // Seit 0.68: alle Schlagzeilen und ein eigener Feed (Praefix plus Kennung des Feeds)
        const val NEWS_ALLE = "alle"
        const val NEWS_FEED = "feed:"
        fun newsFeed(kennung: String): String = NEWS_FEED + kennung
        // Seit 0.77: gemerkte Schlagzeilen und ein Anbieter-Reiter der Nachrichtenseite (Name aus Rss.Anbieter)
        const val NEWS_GEMERKT = "gemerkt"
        const val NEWS_ANBIETER = "anbieter:"
        fun newsAnbieter(name: String): String = NEWS_ANBIETER + name.uppercase()

        const val ZEIT_HEUTE = "heute"
        const val ZEIT_JAHR = "jahr"
        const val ZEIT_KAUF = "kauf"

        const val STIMMUNG_AKTIEN = "aktien"
        const val STIMMUNG_KRYPTO = "krypto"
        // Gesamtstimmung aus allen Barometern (5.95)
        const val STIMMUNG_GESAMT = "gesamt"
        // Alle Barometer untereinander (0.69)
        const val STIMMUNG_ALLE = "alle"
        // Seit 0.77 alle Barometer der Seite Maerkte, Reiter Stimmung
        const val STIMMUNG_AAII = "aaii"
        const val STIMMUNG_WSB = "wsb"
        const val STIMMUNG_SORGEN = "sorgen"
        const val STIMMUNG_VIX = "vix"
        const val STIMMUNG_SICHER = "sicher"
        const val STIMMUNG_PUTCALL = "putcall"
        // Reihenfolge wie in der App: Gesamtstimmung, Aktien USA, Privatanleger, Krypto, WSB, Sorgen, Schwankung, Sichere Haefen, Put/Call
        val STIMMUNG_REIHE = listOf(
            STIMMUNG_GESAMT, STIMMUNG_AKTIEN, STIMMUNG_AAII, STIMMUNG_KRYPTO, STIMMUNG_WSB,
            STIMMUNG_SORGEN, STIMMUNG_VIX, STIMMUNG_SICHER, STIMMUNG_PUTCALL,
        )

        // Reihenfolge einer Liste: wie gewaehlt oder nach Tagesaenderung, staerkster Gewinner zuerst.
        fun ordnen(werte: List<WidgetWert>, nachAenderung: Boolean): List<WidgetWert> =
            if (nachAenderung) werte.sortedByDescending { it.pct } else werte

        // Schluessel einer Heatmap im Widget: Quelle (HeatmapQuelle oder Index) und Zeitraum (Index in HEATMAP_ZEITRAEUME).
        fun heatmapSchluessel(quelle: String, zeitraum: Int): String = "$quelle|$zeitraum"
    }
}

// Depot in einem Zeitraum: Aenderung als Betrag (leer bei ausgeblendeten Betraegen) und in Prozent, dazu der Verlauf.
@Serializable
data class WidgetZeitraum(
    val titel: String,
    val betrag: String,
    val prozent: String,
    val hoch: Boolean,
    val kurve: List<Double> = emptyList(),
)

// Stimmungsbarometer: Wert 0 bis 100, Stufe als Text und als Nummer 0 (extreme Angst) bis 4 (extreme Gier).
@Serializable
data class WidgetStimmung(val titel: String, val wert: Int, val stufe: String, val stufeNr: Int, val quelle: String = "")

// Schlagzeile im News-Widget; zeile nennt Wert und Alter, ziel oeffnet den Wert.
@Serializable
data class WidgetNachricht(val titel: String, val zeile: String, val ziel: String)

// Heatmap im Widget (0.68): Titel, Kacheln in der Reihenfolge der App, voll = Aenderung fuer volle Farbe, ziel oeffnet
// die Heatmap mit derselben Auswahl; hinweis steht da, wenn keine Kachel da ist.
@Serializable
data class WidgetHeatmap(val titel: String, val kacheln: List<WidgetKachel>, val voll: Double, val ziel: String = "", val hinweis: String = "")

// Kachel der Heatmap: Kuerzel, Anteil an der Flaeche, Aenderung in Prozent (null ohne Kurs), fertig formatiert mit zwei
// und mit einer Nachkommastelle.
@Serializable
data class WidgetKachel(val kuerzel: String, val gewicht: Double, val aenderung: Double? = null, val prozent: String = "", val prozentKurz: String = "")

// Karte eines Kategorie-Widgets (0.73): Titel, Zeilen in der Reihenfolge ihrer Wichtigkeit, Hinweis bei leerer Karte,
// Stand der Daten (Zeit der Rechnung) und die Seite, die ein Tipp oeffnet. Welche Zeilen in welcher Groesse stehen,
// entscheidet WidgetKarten.zuschneiden.
@Serializable
data class WidgetKarte(
    val titel: String,
    val zeilen: List<WidgetZeile> = emptyList(),
    val hinweis: String = "",
    val stand: String = "",
    val ziel: String = "",
    // Zeitpunkt der Rechnung in ms; 0, wenn die Karte noch nie gerechnet wurde.
    val am: Long = 0L,
    // Seit 0.77: die Hauptaussage gross unter dem Titel (Zahl oder Stufe), mit Farbe (Ton) und kleiner Zeile darunter, dazu
    // eine kleine Grafik (Tacho, Ampelreihe, Balken, Kurve) zwischen Hauptaussage und Zeilen.
    val haupt: String = "",
    val hauptTon: Int = WidgetGrafik.NEUTRAL,
    val hauptUnter: String = "",
    val grafik: WidgetGrafik? = null,
    // Seit 0.78: die Zeilen sind sortiert und die Karte zeigt, soweit der Platz reicht, je zur Haelfte den Anfang und das Ende.
    val ausgewogen: Boolean = false,
    // Sprache, in der die Karte gerechnet wurde (Lang.name); nach einem Sprachwechsel wird sie neu gerechnet.
    val sprache: String = "",
)

// Kleine Grafik eines Widgets. Ton je Abschnitt oder Punkt: NEUTRAL, GRUEN, GELB, ROT oder ein Grauton der Reihe 4 bis 9.
// TACHO: Halbkreis aus Abschnitten (werte = Breite je Abschnitt, ton = Farbe), marke = Zeiger von 0 bis 1.
// AMPELN: eine Reihe Punkte (ton je Punkt). BALKEN: ein waagerechter Balken (werte = Anteile, ton = Farbe je Abschnitt).
// KURVE: Linie ueber werte, ton gibt die Farbe an.
@Serializable
data class WidgetGrafik(
    val art: String,
    val werte: List<Double> = emptyList(),
    val ton: List<Int> = emptyList(),
    val marke: Double = 0.0,
) {
    companion object {
        const val TACHO = "tacho"
        const val AMPELN = "ampeln"
        const val BALKEN = "balken"
        const val KURVE = "kurve"
        const val NEUTRAL = 0
        const val GRUEN = 1
        const val GELB = 2
        const val ROT = 3
    }
}

// Eine Zeile der Karte: text links (darf umbrechen), wert rechts (bricht nie um), unter eine kleine Zeile darunter.
// hoch faerbt den Wert wie Gewinn und Verlust; null laesst ihn in der Schriftfarbe.
@Serializable
data class WidgetZeile(
    val text: String,
    val wert: String = "",
    val hoch: Boolean? = null,
    val unter: String = "",
    val ziel: String = "",
    // Faerbt die kleine Zeile darunter (etwa die Veraenderung einer Wahrscheinlichkeit).
    val unterHoch: Boolean? = null,
    // Vom Zuschnitt gesetzt: der Wert steht in der kleinen Zeile, weil er neben dem Text nicht passt.
    val gestapelt: Boolean = false,
    // Seit 0.78: ein schmaler Balken unter der Zeile. Ein Wert (0 bis 1): Balken von links, gefaerbt wie der Wert (hoch).
    // Zwei Werte (je 0 bis 1): von der Mitte aus, links rot (der erste), rechts gruen (der zweite).
    val balken: List<Double> = emptyList(),
    // Seit 0.77 (AD8): der Text steht in einer Zeile; passt er nicht, faellt die Zeile weg, statt umzubrechen.
    val einzeilig: Boolean = false,
    // Seit 0.77: kuerzerer Text (etwa das Kuerzel), wenn text in einer Zeile nicht passt; ohne ihn faellt die Zeile weg.
    val ersatz: String = "",
    // Seit 0.77: der Text zeigt hoechstens so viele Zeilen und wird am Ende abgeschnitten, wenn er laenger ist; die Zeile faellt
    // deswegen nicht weg und der Wert rechts bleibt stehen (0: Zeilenzahl nach einzeilig).
    val klemmen: Int = 0,
)

// Eine Zeile oder ein Einzelwert. ziel ist die Seite beim Antippen (WidgetZiel), kurve der Tagesverlauf (nur bei
// Einzelwerten).
@Serializable
data class WidgetWert(
    val name: String,
    val kurs: String,
    val aenderung: String,
    val hoch: Boolean,
    val ziel: String = "",
    val kurve: List<Double> = emptyList(),
    // Seit 5.4: Tagesaenderung als Betrag (fuer „Betrag statt Prozent“) und als Zahl (zum Sortieren).
    val betrag: String = "",
    val pct: Double = 0.0,
)

// Wohin ein Tipp auf ein Widget fuehrt: nur diese Ziele nimmt die App von aussen an.
object WidgetZiel {
    private val SYMBOL = Regex("[A-Za-z0-9.^=:_-]{1,40}")

    // Seiten, die eine Mitteilung oeffnen darf.
    val MITTEILUNG_ZIELE = setOf("rueckblick", "aktienfinder")

    // Ziele aelterer Widgets, die erst beim naechsten Auffrischen die neuen bekommen.
    val ALTE_ZIELE = setOf("heatmap")

    // Seiten der Widgets „Stimmung“ (Reiter Stimmung der Märkte) und „Globus“ (Reiter Globus der Märkte), seit 0.69.
    const val STIMMUNG = "marktphase"
    const val GLOBUS = "globus"

    // Seiten der Kategorie-Widgets (0.73): Analytics (Blatt), Copytrading, Prognosemaerkte, Finanzwissen und die Reiter
    // der Maerkte, die ein eigenes Widget haben.
    const val ANALYTICS = "analytics"
    // Seite Kongress-Flow im Copytrading (CopyRouten.KONGRESS_FLOW), seit 0.78.
    const val KONGRESS_FLOW = "copy-flow"
    // Derivate-Liste der Suche mit den Optionswerkzeugen (Stillhalter, auffaellige Kontrakte, Optionsrechner)
    const val DERIVATE = "discover/filter/DERIVATIVES"
    // Werkzeug Haus-Rangliste in Analytics (Blatt Analytics mit geoeffnetem Werkzeug), seit 0.77
    const val HAEUSER = "analytics/HAEUSER"
    // Werkzeug Portfolio Performance in Analytics, Ziel der Zeile „Groesster Rueckgang“ (0.78)
    const val PERFORMANCE = "analytics/PERFORMANCE"
    // Konvergenz (Insider, Kongress, Fonds) und Lernseite Wirkt das? mit dem Insider-Simulator, seit 0.77
    const val KONVERGENZ = "copy-kv"
    const val WIRKT_DAS = "copy-wd"
    // Rangliste der Abgeordneten (CopyRouten.RANGLISTE), Sprungziel fuer die Anleitungsfotos
    const val RANGLISTE = "copy-rang"
    // Copytrading mit gewaehltem Reiter: Vorstaende (Insidergeschaefte) und Politik, Teil Regierung, seit 0.77
    const val COPY_INSIDER = "copytrading/VORSTAENDE"
    const val COPY_REGIERUNG = "copytrading/POLITIK/REGIERUNG"
    val KATEGORIE_ZIELE = setOf(
        ANALYTICS, "copytrading", "pm", "knowledge", "maerkte/trends", "maerkte/bewegungen", "maerkte/ausblick",
        // Seit 0.77: Lage, Blasen-Anzeichen im Ausblick, Kalender, Depotverlauf, Orders, Sparplaene
        "lagebild", "maerkte/ausblick/blasen", "discover/dividends", "discover/economy", "discover/earnings", "discover/ipos",
        "depotverlauf", "orders", "plans", KONGRESS_FLOW, DERIVATE, HAEUSER, PERFORMANCE, KONVERGENZ, WIRKT_DAS, COPY_INSIDER, COPY_REGIERUNG, RANGLISTE,
    )

    fun instrument(symbol: String) = "instrument/$symbol"

    // Heatmap mit Quelle und Zeitraum (0.68), Ziel des Heatmap-Widgets.
    fun heatmap(quelle: String, zeitraum: Int) = "heatmap/$quelle/$zeitraum"
    private val HEATMAP = Regex("[A-Z0-9]{1,12}/[0-5]")

    // Laenderansicht des Globus (Maerkte, Reiter Globus) zu einem Land, Ziel des Tipps auf ein Land im Globus-Widget (0.76).
    private val LAND = Regex("[A-Z]{2}")
    fun land(a2: String) = "land/$a2"

    // Ereignis der Prognosemaerkte (PmRouten.ereignis): Boerse und umkodierte Kennung
    private val PM_EREIGNIS = Regex("(POLYMARKET|KALSHI|MANIFOLD|METACULUS)/[A-Za-z0-9._%~-]{1,120}")

    // Eine Schlagzeile der Nachrichtenseite (5.94): Kennung aus dem Link, damit das Ziel kurz und harmlos bleibt.
    fun nachrichtKennung(id: String): String =
        id.fold(1125899906842597L) { h, c -> 31 * h + c.code }.toULong().toString(36)
    fun nachricht(id: String) = "nachricht/" + nachrichtKennung(id)
    private val KENNUNG = Regex("[0-9a-z]{1,14}")
    fun derivat(id: String) = "derivative/$id"

    // Gibt ziel nur zurueck, wenn es eine bekannte Seite ist.
    fun pruefen(ziel: String?): String? {
        if (ziel == null) return null
        if (ziel in WidgetDaten.SCHNELL_AUSWAHL || ziel in MITTEILUNG_ZIELE || ziel in ALTE_ZIELE || ziel == "news" || ziel == STIMMUNG || ziel == GLOBUS || ziel in KATEGORIE_ZIELE) return ziel
        val teil = ziel.substringAfter('/', "")
        return when {
            ziel.startsWith("instrument/") && SYMBOL.matches(teil) -> ziel
            ziel.startsWith("derivative/") && SYMBOL.matches(teil) -> ziel
            ziel.startsWith("nachricht/") && KENNUNG.matches(teil) -> ziel
            ziel.startsWith("heatmap/") && HEATMAP.matches(teil) -> ziel
            ziel.startsWith("land/") && LAND.matches(teil) -> ziel
            ziel.startsWith("pm-e/") && PM_EREIGNIS.matches(teil) -> ziel
            else -> null
        }
    }

    // Hoechstens max Punkte, gleichmaessig ausgewaehlt; der letzte bleibt.
    fun ausduennen(werte: List<Double>, max: Int = 80): List<Double> {
        val v = werte.filter { it.isFinite() }
        if (v.size <= max) return v
        val schritt = (v.size - 1).toDouble() / (max - 1)
        return List(max) { i -> v[(i * schritt).roundToInt().coerceAtMost(v.size - 1)] }
    }
}
