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

    // Shiller-KGV (CAPE): Kurs geteilt durch den inflationsbereinigten Gewinn der letzten zehn Jahre. Langjaehriger
    // Schnitt etwa 18, im Jahr 1929 gut 32, Ende 1999 gut 44 (Hoechstwert 44,20 im Dezember). Ab 22 gelb, ab 30 rot.
    fun cape(c: Ersatzquellen.Cape?): Indikator? = c?.takeIf { it.wert.isFinite() && it.wert > 0 }?.let {
        Indikator(CAPE, it.wert, if (it.wert >= 30) Ampel.ROT else if (it.wert >= 22) Ampel.GELB else Ampel.GRUEN, it.rang, it.stand)
    }

    // Margin-Kredite: Wachstum gegen das Vorjahr in %. Auf Kredit gekaufte Aktien wuchsen vor den Hochs von 2000 und
    // 2021 um mehr als die Haelfte im Jahr. Ab 20 % gelb, ab 40 % rot.
    fun margin(m: Ersatzquellen.Margin?): Indikator? = m?.takeIf { it.wachstum.isFinite() }?.let {
        Indikator(MARGIN, it.wachstum, if (it.wachstum >= 40) Ampel.ROT else if (it.wachstum >= 20) Ampel.GELB else Ampel.GRUEN, it.milliarden, it.stand)
    }

    // Kurs-Gewinn-Verhaeltnis des S&P 500 (SPY, letzte zwoelf Monate). Langjaehriger Schnitt etwa 16.
    fun kgv(pe: Double?): Indikator? = pe?.takeIf { it.isFinite() && it > 0 }?.let {
        Indikator(KGV, it, if (it >= 25) Ampel.ROT else if (it >= 20) Ampel.GELB else Ampel.GRUEN)
    }

    // Buffett-Indikator: Wert aller US-Aktien (Wilshire 5000, ein Punkt etwa eine Milliarde Dollar) im Verhaeltnis
    // zur Wirtschaftsleistung, in Prozent. Langjaehriger Schnitt etwa 100 %, im Jahr 2000 rund 140 %. Das BIP kommt
    // seit 5.94 als juengstes Quartal aufs Jahr gerechnet von FRED (stand = erster Tag des Quartals), sonst als
    // Jahreswert der Weltbank.
    fun buffett(wilshirePunkte: Double?, bipDollar: Double?, stand: Long? = null): Indikator? {
        val w = wilshirePunkte?.takeIf { it.isFinite() && it > 0 } ?: return null
        val b = bipDollar?.takeIf { it.isFinite() && it > 0 } ?: return null
        val p = w * 1e9 / b * 100
        return Indikator(BUFFETT, p, if (p >= 150) Ampel.ROT else if (p >= 110) Ampel.GELB else Ampel.GRUEN, stand = stand)
    }

    // Gewinnrendite der Aktien (100 / KGV) minus Rendite zehnjaehriger US-Staatsanleihen, in Prozentpunkten. Unter
    // null bringen sichere Anleihen mehr als die Gewinne der Aktien; dann haengt viel an kuenftigem Wachstum.
    fun praemie(pe: Double?, zins10: Double?): Indikator? {
        val k = pe?.takeIf { it.isFinite() && it > 0 } ?: return null
        val z = zins10?.takeIf { it.isFinite() } ?: return null
        val p = 100 / k - z
        return Indikator(PRAEMIE, p, if (p < 0) Ampel.ROT else if (p < 2) Ampel.GELB else Ampel.GRUEN)
    }

    // Abstand des S&P 500 zu seiner 200-Tage-Linie in Prozent: weit darueber gilt als heiss gelaufen.
    fun trend(zu200: Double?): Indikator? = zu200?.takeIf { it.isFinite() }?.let {
        Indikator(TREND, it, if (it >= 15) Ampel.ROT else if (it >= 8) Ampel.GELB else Ampel.GRUEN)
    }

    // Anteil der zehn groessten Werte am Boersenwert aller (S&P 500 im Katalog), in Prozent. Frueher um 20 %.
    fun konzentration(boersenwerte: List<Double>): Indikator? {
        val w = boersenwerte.filter { it.isFinite() && it > 0 }.sortedDescending()
        if (w.size < 50) return null
        val p = w.take(10).sum() / w.sum() * 100
        return Indikator(KONZENTRATION, p, if (p >= 35) Ampel.ROT else if (p >= 27) Ampel.GELB else Ampel.GRUEN)
    }

    // Marktbreite: Vorsprung des S&P 500 nach Boersenwert (SPY) vor dem gleichgewichteten (RSP) in zwoelf Monaten, in
    // Prozentpunkten. Tragen nur wenige grosse Werte den Anstieg, steht er auf schmalen Fuessen.
    fun breite(spyJahr: Double?, rspJahr: Double?): Indikator? {
        val a = spyJahr?.takeIf { it.isFinite() } ?: return null
        val b = rspJahr?.takeIf { it.isFinite() } ?: return null
        val d = a - b
        return Indikator(BREITE, d, if (d >= 10) Ampel.ROT else if (d >= 4) Ampel.GELB else Ampel.GRUEN)
    }

    // Boersengang-Fieber: Kursplus des Renaissance-IPO-ETF in zwoelf Monaten, in Prozent.
    fun ipo(jahr: Double?): Indikator? = jahr?.takeIf { it.isFinite() }?.let {
        Indikator(IPO, it, if (it >= 50) Ampel.ROT else if (it >= 20) Ampel.GELB else Ampel.GRUEN)
    }

    // Stimmung: Fear & Greed der Aktien von CNN (sonst eigener Index) von 0 bis 100; Gier ueber 75 gilt als extrem (wie im
    // Stimmungsbarometer: bis 75 Gier, darueber extreme Gier).
    fun stimmung(wert: Double?): Indikator? = wert?.takeIf { it.isFinite() }?.let {
        Indikator(STIMMUNG, it, if (it > 75) Ampel.ROT else if (it >= 60) Ampel.GELB else Ampel.GRUEN)
    }

    // Erwaehnungen: wo die Aufrufe der letzten sieben Tage im Vergleich zum Jahr liegen, 0 bis 100 (siehe rang).
    fun erwaehnung(aufrufe: List<Int>): Indikator? = rang(aufrufe)?.let {
        Indikator(ERWAEHNUNG, it, if (it >= 80) Ampel.ROT else if (it >= 60) Ampel.GELB else Ampel.GRUEN)
    }

    // Erwartetes Gewinnwachstum im naechsten Geschaeftsjahr in Prozent. Kraeftig steigende Gewinne rechtfertigen
    // hoehere Kurse und sprechen gegen eine Blase.
    fun gewinn(wachstum: Double?): Indikator? = wachstum?.takeIf { it.isFinite() }?.let {
        Indikator(GEWINN, it, if (it >= 10) Ampel.GRUEN else if (it >= 3) Ampel.GELB else Ampel.ROT)
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
        if (liste.size < 3) return null
        val punkte = liste.sumOf { when (it.ampel) { Ampel.ROT -> 2; Ampel.GELB -> 1; Ampel.GRUEN -> 0 }.toInt() }
        val anteil = punkte / (2.0 * liste.size)
        return when {
            anteil >= 0.6 -> Stufe.HOCH
            anteil >= 1.0 / 3 -> Stufe.ERHOEHT
            else -> Stufe.GERING
        }
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
