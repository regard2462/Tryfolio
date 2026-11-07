package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehindikatoren.Lage as Bestand
import de.troetenrepublik.app.engine.Fruehwarnung.Ampel

// Tendenz je Eintrag im Ausblick (5.93): ob ein Signal eher fuer steigende (bullish) oder fallende Kurse (bearish)
// spricht und fuer welchen Markt. Grobe Faustregeln aus den Erklaerungen der Eintraege, keine Empfehlung.
object Tendenz {
    enum class Richtung { BULLISH, NEUTRAL, BEARISH }
    data class Lage(val richtung: Richtung, val ziel: String)

    // Maerkte, fuer die eine Richtung gilt (Texte in S.tdText)
    const val AKTIEN = "aktien"
    const val AKTIEN_EU = "aktien_eu"
    const val ANLEIHEN = "anleihen"
    const val OEL = "oel"
    const val BENZIN = "benzin"
    const val DIESEL = "diesel"
    const val GAS_US = "gas_us"
    const val GAS_EU = "gas_eu"
    const val FLUGLINIEN = "fluglinien"
    const val GETREIDE = "getreide"
    const val GOLD = "gold"
    const val CHIPS = "chips"
    const val TRANSPORT = "transport"
    const val REEDEREIEN = "reedereien"

    // Blasenzeichen: Rot spricht gegen Aktien, Gruen heisst nur „keine Uebertreibung“ und bleibt neutral.
    fun blase(a: Ampel) = Lage(if (a == Ampel.ROT) Richtung.BEARISH else Richtung.NEUTRAL, AKTIEN)

    // Gruen spricht fuer den Markt, Rot dagegen.
    fun ausAmpel(a: Ampel, ziel: String) = Lage(
        when (a) { Ampel.GRUEN -> Richtung.BULLISH; Ampel.GELB -> Richtung.NEUTRAL; Ampel.ROT -> Richtung.BEARISH }, ziel,
    )

    // Lager: knapp stuetzt den Preis, reichlich drueckt ihn.
    private fun ausLager(l: Bestand?, ziel: String) = Lage(
        when (l) {
            Bestand.DARUNTER, Bestand.KNAPP -> Richtung.BULLISH
            Bestand.REICHLICH, Bestand.DARUEBER -> Richtung.BEARISH
            else -> Richtung.NEUTRAL
        },
        ziel,
    )

    // Lager und Wirtschaftsdaten (Fruehe Signale).
    fun fruehsignal(s: Fruehindikatoren.Signal): Lage = when (s.schluessel) {
        Fruehindikatoren.ROHOEL, Fruehindikatoren.CUSHING, Fruehindikatoren.SPR -> ausLager(s.lage, OEL)
        Fruehindikatoren.BENZIN -> ausLager(s.lage, BENZIN)
        Fruehindikatoren.DESTILLAT -> ausLager(s.lage, DIESEL)
        Fruehindikatoren.GAS_US -> ausLager(s.lage, GAS_US)
        Fruehindikatoren.GAS_EU -> ausLager(s.lage, GAS_EU)
        Fruehindikatoren.FLUGGAESTE -> ausAmpel(s.ampel, FLUGLINIEN)
        // Viel Land in Duerre: Ernten leiden, Getreide wird eher teurer
        Fruehindikatoren.DUERRE -> Lage(
            when (s.lage) {
                Bestand.DARUEBER, Bestand.REICHLICH -> Richtung.BULLISH
                Bestand.DARUNTER, Bestand.KNAPP -> Richtung.BEARISH
                else -> Richtung.NEUTRAL
            },
            GETREIDE,
        )
        else -> ausAmpel(s.ampel, AKTIEN)
    }

    // Fracht, Lieferketten und Halbleiter (Ersatzquellen und weitere Quellen).
    fun ersatz(schluessel: String, ampel: Ampel): Lage = when (schluessel) {
        Ersatzquellen.FRACHT, Ersatzquellen.BAHN, "aar" -> ausAmpel(ampel, TRANSPORT)
        Ersatzquellen.HALBLEITER, "wsts" -> ausAmpel(ampel, CHIPS)
        else -> ausAmpel(ampel, AKTIEN)
    }

    // Containerfracht: steigende Raten bringen Reedereien mehr ein. Unter 2 % in der Woche neutral.
    fun containerfracht(woche: Double): Lage = Lage(
        when {
            woche >= 2 -> Richtung.BULLISH
            woche <= -2 -> Richtung.BEARISH
            else -> Richtung.NEUTRAL
        },
        REEDEREIEN,
    )

    // Richtung der Zinsen (-1 Senkung, 0 gleich, 1 Erhoehung): Senkungen stuetzen Aktien.
    fun ausZins(richtung: Int?, ziel: String = AKTIEN) = Lage(
        when (richtung) { -1 -> Richtung.BULLISH; 1 -> Richtung.BEARISH; else -> Richtung.NEUTRAL }, ziel,
    )

    // Verneinung im Namen eines Ausgangs, etwa „No change“ oder „No cuts“.
    private val NEIN = Regex("""\b(no|not|without|none)\b""")
    private fun wort(n: String, vararg w: String) = w.any { Regex("""\b""" + Regex.escape(it)).containsMatchIn(n) }

    // Zinsschritt aus dem Namen eines Ausgangs bei Polymarket, etwa „25 bps decrease“ oder „No change“. Verneinungen
    // zuerst: „No cut“ heisst gleichbleibend. Gesucht wird am Wortanfang, damit „threshold“ nicht als „hold“ zaehlt.
    fun zinsSchritt(name: String): Int? {
        val n = name.lowercase()
        return when {
            NEIN.containsMatchIn(n) || wort(n, "unchanged", "hold", "maintain", "pause") -> 0
            wort(n, "decrease", "cut", "lower") -> -1
            wort(n, "increase", "hike", "raise") -> 1
            else -> null
        }
    }

    private val ZAHL = Regex("""(\d+(?:[.,]\d+)?)""")
    private fun zahl(s: String): Double? = ZAHL.find(s)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    // Wahrscheinlichkeiten von Polymarket je Thema (Schluessel aus Markterwartung.THEMEN). ausgaenge: alle offenen
    // Ausgaenge mit Wahrscheinlichkeit; zinsSchritte: erwartete Schritte der Fed in zwoelf Monaten aus den Futures.
    fun erwartung(thema: String, ausgaenge: List<Pair<String, Double>>, zinsSchritte: Double?): Lage {
        val top = ausgaenge.maxByOrNull { it.second }
        val ja = ausgaenge.singleOrNull()?.second ?: top?.second
        return when (thema) {
            "fed" -> ausZins(top?.let { zinsSchritt(it.first) } ?: Ausblickdeutung.zinsRichtung(zinsSchritte))
            "ezb" -> ausZins(top?.let { zinsSchritt(it.first) }, AKTIEN_EU)
            // Anzahl der Senkungen im Jahr: zwei und mehr stuetzen, keine belastet
            "fed_jahr" -> when (val n = top?.let { t -> if (NEIN.containsMatchIn(t.first.lowercase())) 0.0 else zahl(t.first) }) {
                null -> ausZins(Ausblickdeutung.zinsRichtung(zinsSchritte))
                else -> Lage(if (n >= 2) Richtung.BULLISH else if (n < 1) Richtung.BEARISH else Richtung.NEUTRAL, AKTIEN)
            }
            "rezession" -> Lage(
                when (Ausblickdeutung.rezessionStufe(ja)) { 0 -> Richtung.BULLISH; 2 -> Richtung.BEARISH; else -> Richtung.NEUTRAL }, AKTIEN,
            )
            // Hohe Inflation haelt die Zinsen hoch und belastet Anleihen. Schwellen wie „4 %“ sind meist kumuliert
            // (Summe weit ueber 1): dann zaehlt die hoechste Schwelle mit mindestens 50 %, sonst der wahrscheinlichste
            // Ausgang.
            "inflation" -> {
                val summe = ausgaenge.sumOf { it.second }
                val stufe = if (summe in 0.8..1.25) top?.let { zahl(it.first) }
                else ausgaenge.filter { it.second >= 0.5 }.mapNotNull { zahl(it.first) }.maxOrNull()
                Lage(
                    when {
                        stufe == null -> if (summe in 0.8..1.25) Richtung.NEUTRAL else Richtung.BULLISH
                        stufe >= 3.5 -> Richtung.BEARISH
                        stufe <= 2.5 -> Richtung.BULLISH
                        else -> Richtung.NEUTRAL
                    },
                    ANLEIHEN,
                )
            }
            // Oelpreis: staerkste Wahrscheinlichkeit nach oben gegen die nach unten
            "oel" -> {
                fun max(vararg w: String) = ausgaenge.filter { (n, _) -> w.any { it in n.lowercase() } }.maxOfOrNull { it.second } ?: 0.0
                val hoch = max("↑", "high", "above")
                val tief = max("↓", "low", "below")
                Lage(if (hoch > tief + 0.1) Richtung.BULLISH else if (tief > hoch + 0.1) Richtung.BEARISH else Richtung.NEUTRAL, OEL)
            }
            // Eine Sperre der Meerenge treibt den Oelpreis
            "hormus" -> Lage(if ((ja ?: 0.0) >= 0.25) Richtung.BULLISH else Richtung.NEUTRAL, OEL)
            // Spannungen um Iran treiben Gold
            "iran" -> Lage(if ((ja ?: 0.0) >= 0.3) Richtung.BULLISH else Richtung.NEUTRAL, GOLD)
            else -> Lage(Richtung.NEUTRAL, AKTIEN)
        }
    }
}
