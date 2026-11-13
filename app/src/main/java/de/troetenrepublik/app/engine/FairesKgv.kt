package de.troetenrepublik.app.engine

import kotlin.math.sqrt

// Faires KGV (0.78): welches KGV zur Firma passt, aus drei Eigenschaften. Die Stufen sind Festlegungen der App.
//   faires KGV = (Sockel + Aufschlag Stabilitaet + Aufschlag Marktstellung) × Faktor Rentabilitaet
//  - Sockel 8: das KGV einer Firma ohne besondere Staerken.
//  - Stabilitaet, 0,5 bis 2 Punkte: Schwankung der Jahresgewinne (Standardabweichung durch Mittelwert, mindestens drei
//    Jahre). Bis 0,15 gibt 2, bis 0,30 gibt 1,5, bis 0,50 gibt 1, darueber oder mit einem Verlustjahr 0,5.
//  - Marktstellung, 0 bis 3 Punkte: Punktzahl der Marktmacht (0 bis 100, ab zwei Bausteinen) mal 3 durch 100.
//    Ersatzweise die Umsatzrendite in fuenf Stufen: bis 3 % gibt 0, bis 5 % 0,75, bis 7 % 1,5, bis 12 % 2,25,
//    darueber 3.
//  - Rentabilitaet: Eigenkapitalrendite durch 12 %, begrenzt auf 0,7 bis 1,4. Eine Firma mit 12 % Rendite behaelt die
//    Summe, eine schwaechere bekommt einen Abschlag, eine staerkere einen Zuschlag.
// Fehlt eine der drei Angaben, gibt es kein Ergebnis. Reine Rechnung.
object FairesKgv {

    const val SOCKEL = 8.0
    const val MIN_JAHRE = 3

    // Schwankung der Gewinne (Variationskoeffizient) bis zu dieser Grenze gibt den Aufschlag daneben.
    val STABIL_STUFEN = listOf(0.15 to 2.0, 0.30 to 1.5, 0.50 to 1.0)
    const val STABIL_MIN = 0.5

    // Umsatzrendite in Prozent bis zu dieser Grenze gibt den Aufschlag daneben.
    val MARGE_STUFEN = listOf(3.0 to 0.0, 5.0 to 0.75, 7.0 to 1.5, 12.0 to 2.25)
    const val MARKT_MAX = 3.0

    const val RENDITE_NORMAL = 12.0
    const val FAKTOR_MIN = 0.7
    const val FAKTOR_MAX = 1.4

    // schwankung: Variationskoeffizient der Gewinne, null bei einem Verlustjahr. ausMarktmacht: die Marktstellung stammt
    // aus der Marktmacht, sonst aus der Umsatzrendite. marktWert: Punktzahl der Marktmacht bzw. Umsatzrendite in Prozent.
    data class Ergebnis(
        val fair: Double,
        val stabil: Double, val schwankung: Double?, val jahre: Int,
        val markt: Double, val ausMarktmacht: Boolean, val marktWert: Double,
        val faktor: Double, val rendite: Double,
    )

    // Aufschlag fuer stabile Gewinne mit der Schwankung; null mit weniger als drei Jahren.
    fun stabilitaet(gewinne: List<Double>): Pair<Double, Double?>? {
        val g = gewinne.filter { it.isFinite() }
        if (g.size < MIN_JAHRE) return null
        if (g.any { it <= 0 }) return STABIL_MIN to null
        val m = g.average()
        val vk = sqrt(g.sumOf { (it - m) * (it - m) } / g.size) / m
        return (STABIL_STUFEN.firstOrNull { vk <= it.first }?.second ?: STABIL_MIN) to vk
    }

    // Aufschlag aus der Umsatzrendite in Prozent.
    fun marktAusMarge(marge: Double): Double = MARGE_STUFEN.firstOrNull { marge <= it.first }?.second ?: MARKT_MAX

    // Aufschlag aus der Punktzahl der Marktmacht (0 bis 100).
    fun marktAusPunkten(punkte: Double): Double = punkte.coerceIn(0.0, 100.0) / 100.0 * MARKT_MAX

    fun faktor(ekRendite: Double): Double = (ekRendite / RENDITE_NORMAL).coerceIn(FAKTOR_MIN, FAKTOR_MAX)

    // gewinne: Jahresgewinne, aelteste zuerst. marktmacht: Punktzahl 0 bis 100, nur wenn sie eine Stufe hat.
    // umsatzrendite und ekRendite in Prozent; null ohne Angabe.
    fun berechne(gewinne: List<Double>, marktmacht: Double?, umsatzrendite: Double?, ekRendite: Double?): Ergebnis? {
        val (stabil, vk) = stabilitaet(gewinne) ?: return null
        val roe = ekRendite?.takeIf { it.isFinite() } ?: return null
        val mm = marktmacht?.takeIf { it.isFinite() }
        val marge = umsatzrendite?.takeIf { it.isFinite() }
        val markt = mm?.let { marktAusPunkten(it) } ?: marge?.let { marktAusMarge(it) } ?: return null
        val f = faktor(roe)
        return Ergebnis((SOCKEL + stabil + markt) * f, stabil, vk, gewinne.count { it.isFinite() }, markt, mm != null, mm ?: marge!!, f, roe)
    }

    // Aktuelles KGV gegen das faire in Prozent (positiv: teurer als fair); null ohne positives KGV.
    fun abstand(kgv: Double?, fair: Double): Double? = if (kgv != null && kgv > 0 && fair > 0) (kgv / fair - 1.0) * 100.0 else null

    // Ampel der Zeile (Anzeige der App): gruen bis zum fairen KGV, gelb bis 25 % darueber, sonst rot.
    const val SPIELRAUM = 25.0

    fun ampel(abstand: Double?): Bewertungsblick.Ampel? = when {
        abstand == null -> null
        abstand <= 0.0 -> Bewertungsblick.Ampel.GRUEN
        abstand <= SPIELRAUM -> Bewertungsblick.Ampel.GELB
        else -> Bewertungsblick.Ampel.ROT
    }
}
