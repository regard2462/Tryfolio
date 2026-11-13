package de.troetenrepublik.app.data

import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.Fmt

// Karten der Widgets zu den Abschnitten von 0.77 (Konvergenz, seltene Insiderkaeufe, Insider-Simulator, Vorschlagsdepot, Branche,
// Warnzeichen, Praesidialdokumente). Wie WidgetKartenBau: kein Netz, nur Aufbau aus fertigen Werten. Namen stehen in einer Zeile;
// passt der Name nicht, steht das Kuerzel (WidgetZeile.ersatz), passt auch das nicht, fehlt die Zeile.
object WidgetKartenBauAbschnitte {

    private fun richtung(v: Double): Boolean? = if (v > 0.0) true else if (v < 0.0) false else null

    private fun ton(v: Double): Int = if (v > 0) WidgetGrafik.GRUEN else if (v < 0) WidgetGrafik.ROT else WidgetGrafik.NEUTRAL

    private fun kuerzel(symbol: String): String = symbol.trim().substringBefore('.').removePrefix("^").ifBlank { symbol.trim() }

    // ------------------------------------------------------------ Konvergenz

    // Wert, bei dem mindestens zwei von drei Gruppen kaufen: Zahl der Insider, der Abgeordneten und der Netto-Kaeufer unter den
    // Fonds (0: die Gruppe kauft nicht).
    class Konv(val name: String, val symbol: String, val insider: Int, val kongress: Int, val fonds: Int) {
        val gruppen: Int get() = listOf(insider, kongress, fonds).count { it > 0 }
    }

    // Konvergenz: so viele Werte wie in die Hoehe passen (bis acht), die Liste steht schon nach Zahl der Gruppen geordnet. Unter dem
    // Namen die Zahlen je kaufender Gruppe wie auf der Seite ("Insider 11 · Kongress 2 · Fonds 5"). Rechts steht die Zahl der Gruppen
    // nur, wenn die Liste auch Werte mit zwei Gruppen enthaelt. bis ist das letzte Datum des Fensters in ms (0: unbekannt).
    fun konvergenz(l: List<Konv>, bis: Long, am: Long): WidgetKarte {
        val mitZwei = l.any { it.gruppen < 3 }
        val zeilen = l.take(8).map { k ->
            WidgetZeile(
                k.name, if (mitZwei) k.gruppen.toString() + "/3" else "",
                unter = listOfNotNull(
                    (S.kvInsider + " " + Fmt.num(k.insider.toDouble(), 0)).takeIf { k.insider > 0 },
                    (S.kvKongress + " " + Fmt.num(k.kongress.toDouble(), 0)).takeIf { k.kongress > 0 },
                    (S.kvFonds + " " + Fmt.num(k.fonds.toDouble(), 0)).takeIf { k.fonds > 0 },
                ).joinToString(" · "),
                einzeilig = true, ersatz = kuerzel(k.symbol),
            )
        }
        return WidgetKarte(
            S.konTitel, zeilen, hinweis = S.kvLeer,
            stand = if (zeilen.isEmpty() || bis <= 0L) "" else S.widgetKonvergenzStand(Fmt.dayMonthNum(bis)),
            ziel = WidgetKarten.ziel(WidgetKarten.KONVERGENZ), am = am,
        )
    }

    // ------------------------------------------------------------ Seltene Insiderkaeufe

    // Seltener Kauf: art wie InsiderSeltenheit.Art (0 Woche, 1 groesster, 2 erster), tag des Kaufs in ms, monate der Luecke,
    // zahl der Kaeufer und faktor (fertig formatiert) zum bisher groessten Kauf wie in der Aussage der App.
    class Selten(
        val name: String, val symbol: String, val art: Int, val tag: Long, val betrag: Double, val monate: Int, val seitBeginn: Boolean, val zahl: Int, val faktor: String, val jahr: Int = 0,
    )

    // Seltene Kaeufe: die juengsten, so viele wie in die Hoehe passen (bis acht). Der Betrag steht immer rechts in der Zeile des
    // Namens, ein zu langer Name wird abgeschnitten; darunter dieselbe kurze Aussage wie in der App. bis ist das Ende der Daten
    // in ms (0: unbekannt).
    fun selten(l: List<Selten>, bis: Long, am: Long): WidgetKarte {
        val zeilen = l.sortedByDescending { it.tag }.take(8).map { s ->
            WidgetZeile(s.name, Fmt.dollarKompakt(s.betrag), unter = S.skAussage(s.art, s.monate, s.seitBeginn, s.zahl, s.faktor, s.jahr), klemmen = 1)
        }
        return WidgetKarte(
            S.skTitel, zeilen, hinweis = S.widgetSeltenLeer,
            stand = if (zeilen.isEmpty() || bis <= 0L) "" else S.widgetSeltenStand(Fmt.dayMonthNum(bis)),
            ziel = WidgetKarten.ziel(WidgetKarten.INSIDERSELTEN), am = am,
        )
    }

    // ------------------------------------------------------------ Insider-Simulator

    // Ergebnis der Rueckschau mit den Standardregeln: Renditen als Anteil (0,05 = 5 %), Trefferanteil 0 bis 1.
    class Sim(
        val rendite: Double, val index: Double, val treffer: Double, val halten: Int, val cluster: Int, val jahr: String,
        val median: Double = 0.0, val schlechtester: Double = 0.0, val schlechtesterWert: String = "",
    )

    // Insider-Simulator: die mittlere Rendite der Cluster-Kaeufe gross (neutral), darunter die Haltedauer, dann was die Seite
    // "Wirkt das?" zeigt: der S&P 500 im selben Zeitraum, der Vorsprung gegen ihn in Prozentpunkten (gruen oder rot), der Anteil der
    // Cluster, die ihn geschlagen haben, die Zahl der Faelle mit Beginn, der Median und der schlechteste Cluster.
    fun insiderSim(s: Sim?, am: Long): WidgetKarte {
        if (s == null) return WidgetKarte(S.wdClusterTitel, hinweis = S.widgetSimLeer, ziel = WidgetKarten.ziel(WidgetKarten.INSIDERSIM), am = am)
        val vorsprung = (s.rendite - s.index) * 100
        val vorzeichen = if (vorsprung < 0) "−" else if (vorsprung > 0) "+" else ""
        return WidgetKarte(
            S.wdClusterTitel,
            listOf(
                WidgetZeile("S&P 500", Fmt.signedPct(s.index * 100, 1), einzeilig = true),
                WidgetZeile(S.widgetSimVorsprung, S.widgetPunkte(vorzeichen + Fmt.num(kotlin.math.abs(vorsprung), 1)), richtung(vorsprung), einzeilig = true),
                WidgetZeile(S.widgetTrefferanteil, Fmt.pct(s.treffer * 100, 0), balken = listOf(s.treffer.coerceIn(0.0, 1.0)), einzeilig = true),
                WidgetZeile(S.widgetSimFaelle, Fmt.num(s.cluster.toDouble(), 0), unter = S.widgetSimSeit(s.jahr), einzeilig = true),
                WidgetZeile(S.wdClusterMedian, Fmt.signedPct(s.median * 100, 1), einzeilig = true),
                WidgetZeile(S.widgetSimSchlechtester, Fmt.signedPct(s.schlechtester * 100, 0), richtung(s.schlechtester), unter = s.schlechtesterWert, einzeilig = true),
            ),
            hinweis = S.widgetSimLeer, stand = S.widgetSimStand(Fmt.num(s.cluster.toDouble(), 0), s.jahr),
            ziel = WidgetKarten.ziel(WidgetKarten.INSIDERSIM), am = am,
            haupt = Fmt.signedPct(s.rendite * 100, 1), hauptTon = WidgetGrafik.NEUTRAL, hauptUnter = S.wdClusterRendite(s.halten),
        )
    }

    // ------------------------------------------------------------ Vorschlagsdepot

    // Position des Depots: Gewicht als Anteil (0,08 = 8 %) und Sektor fertig benannt.
    class Position(val name: String, val symbol: String, val gewicht: Double, val sektor: String = "")

    // Vorschlagsdepot: die groessten Positionen, so viele wie in die Hoehe passen (bis acht), je Zeile Name und Gewicht, klein darunter
    // der Sektor; kein Balken (die Gewichte liegen nah beieinander). Standzeile: Zahl der Werte und der Sektoren. geladen: der
    // Aktienfinder hat Daten (sonst steht ein Hinweis, dass er nicht geladen ist).
    fun vorschlag(l: List<Position>, anzahl: Int, sektoren: Int, geladen: Boolean, am: Long): WidgetKarte {
        val oben = l.sortedByDescending { it.gewicht }.take(8)
        return WidgetKarte(
            S.vsdTitel,
            oben.map { p -> WidgetZeile(p.name, Fmt.pct(p.gewicht * 100, 1), unter = p.sektor, einzeilig = true, ersatz = kuerzel(p.symbol)) },
            hinweis = if (geladen) S.widgetVorschlagLeer else S.widgetFinderUngeladen,
            stand = if (oben.isEmpty()) "" else S.vsdWerte(anzahl) + " · " + S.vsdSektoren(sektoren),
            ziel = WidgetKarten.ziel(WidgetKarten.VORSCHLAG), am = am,
        )
    }

    // ------------------------------------------------------------ Branche

    // Aktie einer Branche mit ihrer Gesamtpunktzahl (0 bis 100).
    class BrancheWert(val name: String, val symbol: String, val punkte: Double)

    // Branche: die fuenf besten Aktien der Branche mit Punkten und Balken; der Titel ist der Name der Branche.
    fun branche(titel: String, l: List<BrancheWert>, anzahl: Int, geladen: Boolean, am: Long): WidgetKarte {
        val oben = l.sortedByDescending { it.punkte }.take(8)
        return WidgetKarte(
            titel,
            oben.map { w ->
                WidgetZeile(
                    w.name, Fmt.num(w.punkte, 0), balken = listOf((w.punkte / 100.0).coerceIn(0.0, 1.0)), einzeilig = true, ersatz = kuerzel(w.symbol),
                )
            },
            hinweis = if (geladen) S.widgetBrancheLeer else S.widgetFinderUngeladen, stand = if (oben.isEmpty()) "" else S.fbrWerte(anzahl),
            ziel = WidgetKarten.ziel(WidgetKarten.BRANCHE), am = am,
        )
    }

    // ------------------------------------------------------------ Warnzeichen

    // Warnzeichen aus einer 8-K: Bezeichnung fertig, Tag der Meldung in ms, schwer (Korrektur, Delisting, Insolvenz).
    class Warn(val titel: String, val tag: Long, val schwer: Boolean)

    // Warnzeichen eines Werts: die Zahl der letzten 24 Monate gross (gelb, bei einem schweren Zeichen rot), darunter das juengste
    // mit Datum. Ohne Warnzeichen steht eine ruhige Zeile.
    fun warnzeichen(symbol: String, l: List<Warn>, stand: String, am: Long): WidgetKarte {
        val titel = S.widgetWarnTitel + " · " + kuerzel(symbol)
        val ziel = WidgetZiel.instrument(symbol)
        if (l.isEmpty()) return WidgetKarte(titel, hinweis = S.widgetWarnKeine, stand = stand, ziel = ziel, am = am)
        val neu = l.maxBy { it.tag }
        return WidgetKarte(
            titel, listOf(WidgetZeile(neu.titel, Fmt.date(neu.tag), if (neu.schwer) false else null)), stand = stand, ziel = ziel, am = am,
            haupt = Fmt.num(l.size.toDouble(), 0), hauptTon = if (l.any { it.schwer }) WidgetGrafik.ROT else WidgetGrafik.GELB, hauptUnter = S.widgetWarnUnter,
        )
    }

    // Karte mit einem Hinweis statt der Zahl (kein Kontakt unter Zugaenge, kein US-Wert, Meldungen nicht abrufbar).
    fun warnzeichenHinweis(symbol: String, hinweis: String, am: Long): WidgetKarte =
        WidgetKarte(S.widgetWarnTitel + " · " + kuerzel(symbol), hinweis = hinweis, ziel = WidgetZiel.instrument(symbol), am = am)

    // ------------------------------------------------------------ Praesidialdokumente

    // Dokument: Titel, Bezeichnung der Art (Executive Order, Proklamation, Memorandum) und Tag in ms.
    class Dok(val titel: String, val art: String, val tag: Long)

    // Praesidialdokumente: die juengsten, so viele wie in die Hoehe passen (bis acht). Der Titel steht ueber bis zu zwei Zeilen,
    // darunter klein Datum und Art ("05.11. · Proklamation").
    fun praesidial(l: List<Dok>, stand: String, am: Long): WidgetKarte {
        val drei = l.sortedByDescending { it.tag }.take(8)
        return WidgetKarte(
            S.pdTitel,
            drei.map { d -> WidgetZeile(d.titel, unter = listOfNotNull(Fmt.dayMonthNum(d.tag).takeIf { d.tag > 0L }, d.art).joinToString(" · "), klemmen = 2) },
            hinweis = S.widgetPraesidialLeer, stand = if (drei.isEmpty()) "" else stand,
            ziel = WidgetKarten.ziel(WidgetKarten.PRAESIDIAL), am = am,
        )
    }
}
