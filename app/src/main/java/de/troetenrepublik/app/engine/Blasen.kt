package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Fruehwarnung.Ampel

// Blasen-Detektor (seit 5.92): Messgroessen, die vor frueheren Uebertreibungen am Aktienmarkt hoch standen, und
// solche, die dagegen sprechen. Jede bekommt eine Ampel: Rot heisst „spricht fuer eine Ueberhitzung“, Gruen „spricht
// dagegen“. Keine sagt den Zeitpunkt eines Absturzes voraus; Bewertungen koennen lange hoch bleiben.
// Daten: Yahoo (Kurse, KGV, Wilshire 5000, Renaissance-IPO-ETF, Rendite 10-jaehriger US-Anleihen), Weltbank
// (Bruttoinlandsprodukt der USA, CC BY 4.0), Wikimedia (Seitenaufrufe), CNN Fear & Greed (sonst eigener Angst-Gier-Index). Aus den Ersatzquellen
// (engine/Ersatzquellen.kt) kommen dazu das Shiller-KGV von shillerdata.com und die Margin-Kredite aus der
// Finanzierungsrechnung der Fed (FRED).
object Blasen {

    const val KGV = "kgv"
    const val BUFFETT = "buffett"
    const val PRAEMIE = "praemie"
    const val TREND = "trend"
    const val KONZENTRATION = "konzentration"
    const val BREITE = "breite"
    const val IPO = "ipo"
    const val STIMMUNG = "stimmung"
    const val ERWAEHNUNG = "erwaehnung"
    const val GEWINN = "gewinn"
    const val CAPE = "cape"
    const val MARGIN = "margin"

    // Reihenfolge der Anzeige: erst was fuer eine Blase spraeche, dann was dagegen spricht.
    val DAFUER = listOf(CAPE, KGV, BUFFETT, MARGIN, TREND, KONZENTRATION, IPO, STIMMUNG, ERWAEHNUNG)
    val DAGEGEN = listOf(GEWINN, PRAEMIE, BREITE)

    // Weltbank: Bruttoinlandsprodukt der USA in Dollar, die letzten Jahre.
    const val BIP = "https://api.worldbank.org/v2/country/US/indicator/NY.GDP.MKTP.CD?format=json&per_page=5"

    // Wikipedia-Artikel, deren Aufrufe als Mass fuer das Gerede ueber eine Blase dienen.
    const val BLASEN_ARTIKEL = "Economic_bubble"

    // zusatz und stand nur bei einigen: beim CAPE der Rang seit 1881 in % und der Monat (Tag seit 1970), bei den
    // Margin-Krediten der Stand in Milliarden Dollar und das Quartal.
    data class Indikator(val schluessel: String, val wert: Double, val ampel: Ampel, val zusatz: Double? = null, val stand: Long? = null)

    // ------------------------------------------------------------ Schwellen und Vergleichspunkte

    // STEIGT: hohe Werte sprechen fuer eine Blase, FAELLT: niedrige.
    enum class Richtung { STEIGT, FAELLT }

    // Schwellen einer Messgroesse. STEIGT: ab gelb Gelb, ab rot Rot (rotUeber: erst ueber rot). FAELLT: unter gelb Gelb,
    // unter rot Rot. von und bis: ueblicher Bereich der Skala auf der Karte (Wert und Vergleichspunkte erweitern ihn).
    data class Schwellen(
        val gelb: Double, val rot: Double, val von: Double, val bis: Double,
        val richtung: Richtung = Richtung.STEIGT, val rotUeber: Boolean = false,
    )

    // Aus diesen Schwellen rechnen die Ampeln und zeichnet die Karte ihre Skala (ui/BlasenKarte.kt).
    val SCHWELLEN: Map<String, Schwellen> = mapOf(
        CAPE to Schwellen(22.0, 30.0, 5.0, 50.0),
        MARGIN to Schwellen(20.0, 40.0, -40.0, 80.0),
        KGV to Schwellen(20.0, 25.0, 5.0, 40.0),
        BUFFETT to Schwellen(110.0, 150.0, 50.0, 220.0),
        TREND to Schwellen(8.0, 15.0, -20.0, 30.0),
        KONZENTRATION to Schwellen(27.0, 35.0, 10.0, 50.0),
        IPO to Schwellen(20.0, 50.0, -60.0, 120.0),
        STIMMUNG to Schwellen(60.0, 75.0, 0.0, 100.0, rotUeber = true),
        ERWAEHNUNG to Schwellen(60.0, 80.0, 0.0, 100.0),
        GEWINN to Schwellen(10.0, 3.0, -10.0, 30.0, Richtung.FAELLT),
        PRAEMIE to Schwellen(2.0, 0.0, -3.0, 8.0, Richtung.FAELLT),
        BREITE to Schwellen(4.0, 10.0, -15.0, 25.0),
    )

    // Ampel einer Messgroesse aus SCHWELLEN; ohne Eintrag Gruen.
    fun ampel(schluessel: String, wert: Double): Ampel {
        val s = SCHWELLEN[schluessel] ?: return Ampel.GRUEN
        return when (s.richtung) {
            Richtung.STEIGT -> when {
                if (s.rotUeber) wert > s.rot else wert >= s.rot -> Ampel.ROT
                wert >= s.gelb -> Ampel.GELB
                else -> Ampel.GRUEN
            }
            Richtung.FAELLT -> when {
                wert < s.rot -> Ampel.ROT
                wert < s.gelb -> Ampel.GELB
                else -> Ampel.GRUEN
            }
        }
    }

    // Ein Abschnitt der Skala mit seiner Farbe.
    data class Zone(val von: Double, val bis: Double, val ampel: Ampel)

    // Zonen von links nach rechts zwischen lo und hi, aus denselben Schwellen wie die Ampel. Leer ohne Schwellen.
    fun zonen(schluessel: String, lo: Double, hi: Double): List<Zone> {
        val s = SCHWELLEN[schluessel] ?: return emptyList()
        val teile = when (s.richtung) {
            Richtung.STEIGT -> listOf(Zone(lo, s.gelb, Ampel.GRUEN), Zone(s.gelb, s.rot, Ampel.GELB), Zone(s.rot, hi, Ampel.ROT))
            Richtung.FAELLT -> listOf(Zone(lo, s.rot, Ampel.ROT), Zone(s.rot, s.gelb, Ampel.GELB), Zone(s.gelb, hi, Ampel.GRUEN))
        }
        return teile.map { Zone(it.von.coerceIn(lo, hi), it.bis.coerceIn(lo, hi), it.ampel) }.filter { it.bis > it.von }
    }

    // Bereich der Skala: der uebliche Bereich, erweitert um den Wert und die Vergleichspunkte mit etwas Rand. Ohne
    // Schwellen ein Bereich um den Wert.
    fun skala(schluessel: String, wert: Double, punkte: List<Double> = emptyList()): Pair<Double, Double> {
        val s = SCHWELLEN[schluessel]
        val alle = (punkte + wert).filter { it.isFinite() }
        if (s == null) {
            val b = kotlin.math.abs(wert).coerceAtLeast(1.0)
            return (wert - b) to (wert + b)
        }
        val rand = (s.bis - s.von) * 0.04
        val lo = minOf(s.von, (alle.minOrNull() ?: s.von) - rand)
        val hi = maxOf(s.bis, (alle.maxOrNull() ?: s.bis) + rand)
        return lo to hi
    }

    // Marke eines Vergleichspunkts fuer den langjaehrigen Schnitt (sonst steht dort ein Jahr).
    const val SCHNITT = "schnitt"

    // Vergleichspunkt auf der Skala. wert: Wert aus der Ueberlieferung (null: nur aus der Reihe). vonJahr bis bisJahr:
    // Jahre, deren Hoechstwert in der Reihe den Punkt liefert (die Marke ist dann das Jahr des Hoechstwerts).
    // reiheSchnitt: beim Schnitt das Mittel der ganzen Reihe statt wert.
    data class Vergleich(val marke: String, val wert: Double?, val vonJahr: Int? = null, val bisJahr: Int? = null, val reiheSchnitt: Boolean = false)

    // Vergleichspunkte je Messgroesse, wie sie in den Kommentaren der Rechnung oben stehen.
    val VERGLEICHE: Map<String, List<Vergleich>> = mapOf(
        CAPE to listOf(
            Vergleich(SCHNITT, 17.0, reiheSchnitt = true), Vergleich("1929", 32.0, 1929, 1929),
            Vergleich("1999", 44.2, 1999, 2000), Vergleich("2021", null, 2021, 2021),
        ),
        MARGIN to listOf(Vergleich("2000", null, 1999, 2000), Vergleich("2021", null, 2021, 2021)),
        KGV to listOf(Vergleich(SCHNITT, 16.0)),
        BUFFETT to listOf(Vergleich(SCHNITT, 100.0), Vergleich("2000", 140.0)),
        KONZENTRATION to listOf(Vergleich(SCHNITT, 20.0), Vergleich("2000", 25.0)),
    )

    // Ein gezeigter Vergleichspunkt; ausReihe: aus den geladenen Daten gerechnet.
    data class Punkt(val marke: String, val wert: Double, val ausReihe: Boolean)

    // Vergleichspunkte einer Messgroesse. reihe: (Zeit in ms, Wert), aelteste zuerst; was sie hergibt, ersetzt den
    // ueberlieferten Wert. Punkte ohne Wert und ohne Daten fehlen.
    fun vergleiche(schluessel: String, reihe: List<Pair<Long, Double>> = emptyList()): List<Punkt> {
        val tagMs = 86_400_000L
        fun jahr(t: Long) = java.time.LocalDate.ofEpochDay(Math.floorDiv(t, tagMs)).year
        val r = reihe.filter { it.second.isFinite() }
        return VERGLEICHE[schluessel].orEmpty().mapNotNull { v ->
            when {
                v.reiheSchnitt && r.size >= 120 -> Punkt(v.marke, r.sumOf { it.second } / r.size, true)
                v.vonJahr != null && v.bisJahr != null -> {
                    val im = r.filter { jahr(it.first) in v.vonJahr..v.bisJahr }
                    val hoch = im.maxByOrNull { it.second }
                    if (hoch != null) Punkt(jahr(hoch.first).toString(), hoch.second, true) else v.wert?.let { Punkt(v.marke, it, false) }
                }
                else -> v.wert?.let { Punkt(v.marke, it, false) }
            }
        }
    }

    // Shiller-KGV (CAPE): Kurs geteilt durch den inflationsbereinigten Gewinn der letzten zehn Jahre. Langjaehriger
    // Schnitt etwa 17, im Jahr 1929 gut 32, Ende 1999 gut 44 (Hoechstwert 44,20 im Dezember). Ab 22 gelb, ab 30 rot.
    fun cape(c: Ersatzquellen.Cape?): Indikator? = c?.takeIf { it.wert.isFinite() && it.wert > 0 }?.let {
        Indikator(CAPE, it.wert, ampel(CAPE, it.wert), it.rang, it.stand)
    }

    // Margin-Kredite: Wachstum gegen das Vorjahr in %. Auf Kredit gekaufte Aktien wuchsen vor den Hochs von 2000 und
    // 2021 um mehr als die Haelfte im Jahr. Ab 20 % gelb, ab 40 % rot.
    fun margin(m: Ersatzquellen.Margin?): Indikator? = m?.takeIf { it.wachstum.isFinite() }?.let {
        Indikator(MARGIN, it.wachstum, ampel(MARGIN, it.wachstum), it.milliarden, it.stand)
    }

    // Kurs-Gewinn-Verhaeltnis des S&P 500 (SPY, letzte zwoelf Monate). Langjaehriger Schnitt etwa 16.
    fun kgv(pe: Double?): Indikator? = pe?.takeIf { it.isFinite() && it > 0 }?.let {
        Indikator(KGV, it, ampel(KGV, it))
    }

    // Buffett-Indikator: Wert aller US-Aktien (Wilshire 5000, ein Punkt etwa eine Milliarde Dollar) im Verhaeltnis
    // zur Wirtschaftsleistung, in Prozent. Langjaehriger Schnitt etwa 100 %, im Jahr 2000 rund 140 %. Das BIP kommt
    // seit 5.94 als juengstes Quartal aufs Jahr gerechnet von FRED (stand = erster Tag des Quartals), sonst als
    // Jahreswert der Weltbank.
    fun buffett(wilshirePunkte: Double?, bipDollar: Double?, stand: Long? = null): Indikator? {
        val w = wilshirePunkte?.takeIf { it.isFinite() && it > 0 } ?: return null
        val b = bipDollar?.takeIf { it.isFinite() && it > 0 } ?: return null
        val p = w * 1e9 / b * 100
        return Indikator(BUFFETT, p, ampel(BUFFETT, p), stand = stand)
    }

    // Gewinnrendite der Aktien (100 / KGV) minus Rendite zehnjaehriger US-Staatsanleihen, in Prozentpunkten. Unter
    // null bringen sichere Anleihen mehr als die Gewinne der Aktien; dann haengt viel an kuenftigem Wachstum.
    fun praemie(pe: Double?, zins10: Double?): Indikator? {
        val k = pe?.takeIf { it.isFinite() && it > 0 } ?: return null
        val z = zins10?.takeIf { it.isFinite() } ?: return null
        val p = 100 / k - z
        return Indikator(PRAEMIE, p, ampel(PRAEMIE, p))
    }

    // Abstand des S&P 500 zu seiner 200-Tage-Linie in Prozent: weit darueber gilt als heiss gelaufen.
    fun trend(zu200: Double?): Indikator? = zu200?.takeIf { it.isFinite() }?.let {
        Indikator(TREND, it, ampel(TREND, it))
    }

    // Je Firma ein Kuerzel: Zweitgattungen wie GOOG und GOOGL, FOX und FOXA, NWS und NWSA tragen den Boersenwert der
    // ganzen Firma und zaehlen sonst doppelt. Es bleibt das erste Kuerzel der Firma in der Liste. land: Sitzland je Kuerzel.
    fun jeFirma(symbole: List<String>, namen: Map<String, String>, land: Map<String, String?>): List<String> {
        val eintraege = symbole.map { GleicheFirma.Eintrag(it, namen[it] ?: it, land[it]?.ifBlank { null }, null) }
        return GleicheFirma.gruppen(eintraege).map { symbole[it.first()] }
    }

    // Anteil der zehn groessten Werte am Boersenwert aller (S&P 500 im Katalog), in Prozent. Frueher um 20 %.
    fun konzentration(boersenwerte: List<Double>): Indikator? {
        val w = boersenwerte.filter { it.isFinite() && it > 0 }.sortedDescending()
        if (w.size < 50) return null
        val p = w.take(10).sum() / w.sum() * 100
        return Indikator(KONZENTRATION, p, ampel(KONZENTRATION, p))
    }

    // Marktbreite: Vorsprung des S&P 500 nach Boersenwert (SPY) vor dem gleichgewichteten (RSP) in zwoelf Monaten, in
    // Prozentpunkten. Tragen nur wenige grosse Werte den Anstieg, steht er auf schmalen Fuessen.
    fun breite(spyJahr: Double?, rspJahr: Double?): Indikator? {
        val a = spyJahr?.takeIf { it.isFinite() } ?: return null
        val b = rspJahr?.takeIf { it.isFinite() } ?: return null
        val d = a - b
        return Indikator(BREITE, d, ampel(BREITE, d))
    }

    // Boersengang-Fieber: Kursplus des Renaissance-IPO-ETF in zwoelf Monaten, in Prozent.
    fun ipo(jahr: Double?): Indikator? = jahr?.takeIf { it.isFinite() }?.let {
        Indikator(IPO, it, ampel(IPO, it))
    }

    // Stimmung: Fear & Greed der Aktien von CNN (sonst eigener Index) von 0 bis 100; Gier ueber 75 gilt als extrem (wie im
    // Stimmungsbarometer: bis 75 Gier, darueber extreme Gier).
    fun stimmung(wert: Double?): Indikator? = wert?.takeIf { it.isFinite() }?.let {
        Indikator(STIMMUNG, it, ampel(STIMMUNG, it))
    }

    // Erwaehnungen: wo die Aufrufe der letzten sieben Tage im Vergleich zum Jahr liegen, 0 bis 100 (siehe rang).
    fun erwaehnung(aufrufe: List<Int>): Indikator? = rang(aufrufe)?.let {
        Indikator(ERWAEHNUNG, it, ampel(ERWAEHNUNG, it))
    }

    // Erwartetes Gewinnwachstum im naechsten Geschaeftsjahr in Prozent. Kraeftig steigende Gewinne rechtfertigen
    // hoehere Kurse und sprechen gegen eine Blase.
    fun gewinn(wachstum: Double?): Indikator? = wachstum?.takeIf { it.isFinite() }?.let {
        Indikator(GEWINN, it, ampel(GEWINN, it))
    }

    // Ein Wert fuer das Gewinnwachstum: Boersenwert, Kurs (gleiche Waehrung), Gewinn je Aktie der letzten zwoelf
    // Monate und erwartet im naechsten Geschaeftsjahr.
    data class Gewinne(val boersenwert: Double, val kurs: Double, val epsJetzt: Double, val epsErwartet: Double)

    // Gewinnwachstum aller Werte zusammen, gewichtet nach Aktienzahl (Summe der Gewinne). Werte mit Verlust bleiben
    // aussen vor; die Erwartung je Wert zaehlt hoechstens doppelt und mindestens halb so viel wie der heutige Gewinn,
    // damit ein einmaliger Gewinneinbruch nicht alles verzerrt. Null bei weniger als 50 Werten.
    fun gewinnwachstum(werte: List<Gewinne>): Double? {
        val gut = werte.filter {
            it.boersenwert > 0 && it.kurs > 0 && it.epsJetzt > 0 && it.epsErwartet.isFinite() && it.boersenwert.isFinite()
        }
        if (gut.size < 50) return null
        val jetzt = gut.sumOf { it.boersenwert / it.kurs * it.epsJetzt }
        val spaeter = gut.sumOf { it.boersenwert / it.kurs * it.epsErwartet.coerceIn(it.epsJetzt * 0.5, it.epsJetzt * 2) }
        return if (jetzt > 0) (spaeter / jetzt - 1) * 100 else null
    }

    // Wo der Schnitt der letzten sieben Tage unter allen Siebentagesschnitten der Reihe liegt, 0 (so wenig wie nie)
    // bis 100 (so viel wie nie). aufrufe aelteste zuerst; null bei weniger als 60 Tagen.
    fun rang(aufrufe: List<Int>): Double? {
        if (aufrufe.size < 60) return null
        val schnitte = (6 until aufrufe.size).map { i -> aufrufe.subList(i - 6, i + 1).average() }
        val jetzt = schnitte.last()
        val darunter = schnitte.count { it < jetzt }
        return darunter * 100.0 / (schnitte.size - 1).coerceAtLeast(1)
    }

    // FRED-Reihe GDP (Milliarden Dollar, aufs Jahr gerechnet, gemeinfrei): juengstes Quartal als (Tag seit 1970, Dollar).
    fun bipFredLesen(csv: String): Pair<Long, Double>? =
        Fruehindikatoren.fredLesen(csv).lastOrNull()?.takeIf { it.second > 0 }?.let { (t, w) -> t to w * 1e9 }

    // Wirtschaftsleistung aus der Antwort der Weltbank: das juengste Jahr mit Wert (Jahr, Dollar).
    fun bipLesen(text: String): Pair<Int, Double>? = runCatching {
        val w = kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonArray ?: return null
        val daten = w.getOrNull(1) as? kotlinx.serialization.json.JsonArray ?: return null
        daten.mapNotNull { e ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val jahr = (o["date"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: return@mapNotNull null
            val wert = (o["value"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            jahr to wert
        }.maxByOrNull { it.first }
    }.getOrNull()

    enum class Stufe { GERING, ERHOEHT, HOCH }

    // Gesamtbild: Rot zaehlt zwei Punkte, Gelb einen. Ab einem Drittel der moeglichen Punkte erhoeht, ab 60 % hoch.
    // Null bei weniger als drei Messgroessen.
    fun gesamt(liste: List<Indikator>): Stufe? {
        val anteil = anteil(liste) ?: return null
        return when {
            anteil >= GRENZE_HOCH -> Stufe.HOCH
            anteil >= GRENZE_ERHOEHT -> Stufe.ERHOEHT
            else -> Stufe.GERING
        }
    }

    const val GRENZE_ERHOEHT = 1.0 / 3
    const val GRENZE_HOCH = 0.6

    // Anteil der moeglichen Punkte (0 bis 1), auf dem die Stufe beruht; null bei weniger als drei Messgroessen.
    fun anteil(liste: List<Indikator>): Double? {
        if (liste.size < 3) return null
        val punkte = liste.sumOf { when (it.ampel) { Ampel.ROT -> 2; Ampel.GELB -> 1; Ampel.GRUEN -> 0 }.toInt() }
        return punkte / (2.0 * liste.size)
    }

    // Wie weit eine rote Messgroesse ueber ihrer Rot-Schwelle liegt (1 = genau auf der Schwelle), zum Ordnen der staerksten.
    // Steigende: Wert durch Rot-Schwelle. Fallende: 1 plus Abstand unter der Rot-Schwelle, gemessen an der Gelb-Schwelle.
    private fun ueberschuss(i: Indikator): Double {
        val s = SCHWELLEN[i.schluessel] ?: return 1.0
        return when (s.richtung) {
            Richtung.STEIGT -> i.wert / s.rot
            Richtung.FAELLT -> 1 + (s.rot - i.wert) / s.gelb
        }
    }

    // Die n staerksten Anzeichen fuer eine Ueberhitzung: erst die roten nach Abstand zur Schwelle, mit Platz danach die gelben
    // in der Reihenfolge der Anzeige. Nur gezaehlte Messgroessen.
    fun staerkste(liste: List<Indikator>, n: Int): List<Indikator> {
        val reihenfolge = DAFUER + DAGEGEN
        val l = gezaehlt(liste)
        val rot = l.filter { it.ampel == Ampel.ROT }.sortedByDescending { ueberschuss(it) }
        val gelb = l.filter { it.ampel == Ampel.GELB }.sortedBy { reihenfolge.indexOf(it.schluessel) }
        return (rot + gelb).take(n)
    }

    // Zaehlung fuer den Kopf des Ausblicks: gezaehlt werden nur die Messgroessen dieses Detektors (DAFUER und DAGEGEN).
    // Die fruehen Signale und die Fruehwarnzeichen der Seite Stimmung haben ihre eigene Zaehlung.
    class Zaehlung(
        val rot: Int, val gelb: Int, val gruen: Int,
        val dafuerRot: Int, val dafuerGesamt: Int, val dagegenRot: Int, val dagegenGesamt: Int,
    ) {
        val gesamt: Int get() = rot + gelb + gruen
    }

    private fun gezaehlt(liste: List<Indikator>): List<Indikator> = liste.filter { it.schluessel in DAFUER || it.schluessel in DAGEGEN }

    fun zaehlung(liste: List<Indikator>): Zaehlung {
        val l = gezaehlt(liste)
        val dafuer = l.filter { it.schluessel in DAFUER }
        val dagegen = l.filter { it.schluessel in DAGEGEN }
        return Zaehlung(
            l.count { it.ampel == Ampel.ROT }, l.count { it.ampel == Ampel.GELB }, l.count { it.ampel == Ampel.GRUEN },
            dafuer.count { it.ampel == Ampel.ROT }, dafuer.size, dagegen.count { it.ampel == Ampel.ROT }, dagegen.size,
        )
    }

    // Die gezaehlten Messgroessen fuer die Uebersicht im Kopf: Rot zuerst, dann Gelb, dann Gruen; innerhalb einer Farbe in der
    // Reihenfolge der Anzeige (erst DAFUER, dann DAGEGEN).
    fun uebersicht(liste: List<Indikator>): List<Indikator> {
        val reihenfolge = DAFUER + DAGEGEN
        return gezaehlt(liste).sortedWith(
            compareBy<Indikator> { when (it.ampel) { Ampel.ROT -> 0; Ampel.GELB -> 1; Ampel.GRUEN -> 2 } }.thenBy { reihenfolge.indexOf(it.schluessel) },
        )
    }

    // Eine Branche im Blasen-Check: Kursplus in zwoelf Monaten und Abstand zur 200-Tage-Linie, beide in Prozent.
    data class Sektor(val symbol: String, val jahr: Double, val zu200: Double, val ampel: Ampel) {
        val hitze: Double get() = zu200 + jahr / 2
    }

    // Rot, wenn eine Branche in einem Jahr um mindestens 40 % gestiegen ist und 20 % ueber ihrer 200-Tage-Linie steht;
    // Gelb ab 12 % ueber der Linie oder 30 % im Jahr. Null bei weniger als 200 Kursen.
    fun sektor(symbol: String, kurse: List<Candle>): Sektor? {
        val k = kurse.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        if (k.size < 200) return null
        val linie = k.takeLast(200).sumOf { it.close } / 200
        val zu200 = (k.last().close / linie - 1) * 100
        val jahr = Branchentrends.veraenderung(k, 365) ?: Branchentrends.veraenderung(k, 330) ?: return null
        val ampel = when {
            jahr >= 40 && zu200 >= 20 -> Ampel.ROT
            zu200 >= 12 || jahr >= 30 -> Ampel.GELB
            else -> Ampel.GRUEN
        }
        return Sektor(symbol, jahr, zu200, ampel)
    }
}
