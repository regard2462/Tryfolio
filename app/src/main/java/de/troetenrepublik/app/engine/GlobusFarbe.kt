package de.troetenrepublik.app.engine

import kotlin.math.abs

// Einfaerbung des Globus ohne Oberflaeche. Farben als ARGB-Zahlen, damit Compose und das Widget-Bild sie teilen.
enum class GlobusModus {
    BOERSE, WACHSTUM, AUSBLICK, INFLATION,
    // Seit 0.69 (Globus Trend und Ebenen): weitere IWF-Ebenen, Konjunkturphase, Boersenzeiten und Anteil des Depots je Land
    ARBEITSLOS, SCHULDEN, LEISTUNG, PHASE, BOERSENZEIT, DEPOT,
    // Seit 0.69 (Engpaesse): Punkte an den Engpaessen des Seehandels, Laender gedimmt
    ENGPAESSE;

    // Ebenen mit Zeitregler (IWF-Jahreswerte)
    val mitJahr: Boolean get() = this == WACHSTUM || this == AUSBLICK || this == INFLATION || this == ARBEITSLOS || this == SCHULDEN || this == LEISTUNG
}

object GlobusFarbe {
    const val HINTERGRUND = 0xFF000000.toInt()
    // Land ohne Daten und Kugel
    const val OHNE_DATEN = 0xFF1E1E20.toInt()
    const val KUGEL = 0xFF08080A.toInt()
    const val GRENZE = 0xFF6A6A70.toInt()
    // Gleich kleine Werte sollen sich von „keine Daten“ abheben: Mindestanteil der Farbe
    private const val SOCKEL = 0.28f
    private const val NULLWERT = 0xFF3A3A3E.toInt()

    // Wert, bei dem die Farbe voll deckt.
    fun voll(m: GlobusModus): Double = when (m) {
        GlobusModus.BOERSE -> 3.0
        GlobusModus.WACHSTUM -> 6.0
        GlobusModus.AUSBLICK -> 2.0
        GlobusModus.INFLATION -> 15.0
        // Abstand von der Mitte bis zur vollen Farbe
        GlobusModus.ARBEITSLOS -> 10.0
        GlobusModus.SCHULDEN -> 60.0
        GlobusModus.LEISTUNG -> 8.0
        GlobusModus.PHASE, GlobusModus.BOERSENZEIT, GlobusModus.DEPOT -> 1.0
        // Veraenderung der Durchfahrten gegen das Vorjahr in Prozent
        GlobusModus.ENGPAESSE -> Engpaesse.VOLL_PROZENT
    }

    // Grauer Punkt der Skala: bei Arbeitslosigkeit die Schwelle des EU-Verfahrens bei Ungleichgewichten (10 %), bei
    // Staatsschulden der Referenzwert von Maastricht (60 %).
    fun mitte(m: GlobusModus): Double = when (m) {
        GlobusModus.ARBEITSLOS -> 10.0
        GlobusModus.SCHULDEN -> 60.0
        else -> 0.0
    }

    // Bei Arbeitslosigkeit und Schulden ist ein niedriger Wert der gute.
    fun niedrigGut(m: GlobusModus): Boolean = m == GlobusModus.ARBEITSLOS || m == GlobusModus.SCHULDEN

    // Staerke von -1 bis 1; bei INFLATION von 0 bis 1 (Werte unter 0 zaehlen als 0).
    fun staerke(m: GlobusModus, wert: Double): Float {
        val roh = ((wert - mitte(m)) / voll(m)).coerceIn(-1.0, 1.0)
        val s = if (niedrigGut(m)) -roh else roh
        return (if (m == GlobusModus.INFLATION) maxOf(s, 0.0) else s).toFloat()
    }

    // Farbe eines Landes: ohne Wert dunkelgrau, sonst von Grau zu hoch (up) oder tief (down) nach Betrag. Bei INFLATION
    // von Grau zu `neutral` (keine Gewinn- oder Verlustbedeutung).
    fun farbe(m: GlobusModus, wert: Double?, up: Int, down: Int, neutral: Int): Int {
        if (wert == null || !wert.isFinite()) return OHNE_DATEN
        val s = staerke(m, wert)
        val ziel = if (m == GlobusModus.INFLATION) neutral else if (s >= 0f) up else down
        return mischen(NULLWERT, ziel, SOCKEL + (1f - SOCKEL) * abs(s))
    }

    // Farben der Konjunkturphasen: Aufschwung up, Abschwaechung neutral, Abschwung down, Erholung blau.
    const val ERHOLUNG = 0xFF4C8DD6.toInt()

    fun phase(p: Fruehindikator.Phase?, up: Int, down: Int, neutral: Int): Int = when (p) {
        null -> OHNE_DATEN
        Fruehindikator.Phase.AUFSCHWUNG -> up
        Fruehindikator.Phase.ABSCHWAECHUNG -> neutral
        Fruehindikator.Phase.ABSCHWUNG -> down
        Fruehindikator.Phase.ERHOLUNG -> ERHOLUNG
    }

    // Boerse offen in der Farbe up, geschlossen dunkelgrau, ohne Boerse keine Daten.
    fun handel(offen: Boolean?, up: Int): Int = when (offen) {
        null -> OHNE_DATEN
        true -> up
        false -> NULLWERT
    }

    // Depotanteil eines Landes (0 bis 1): ab diesem Anteil deckt die Farbe voll.
    const val ANTEIL_VOLL = 0.5

    fun anteil(a: Double?, farbe: Int): Int {
        if (a == null || !a.isFinite() || a <= 0.0) return OHNE_DATEN
        return mischen(NULLWERT, farbe, SOCKEL + (1f - SOCKEL) * (a / ANTEIL_VOLL).coerceIn(0.0, 1.0).toFloat())
    }

    // Laender der Ebene Engpaesse: gedimmt, damit die Punkte vorn stehen
    const val GEDIMMT = 0xFF17171A.toInt()
    // Punkt ohne Vergleichswert
    const val PUNKT_OHNE = 0xFFB4B4BC.toInt()

    // Farbe eines Engpasses nach der Veraenderung der Durchfahrten in Prozent: Grau bei 0, Rot (down) beim Rueckgang,
    // Gruen (up) beim Zuwachs, voll bei +-40 %; ohne Vergleichswert hellgrau.
    fun engpass(abw: Double?, up: Int, down: Int): Int {
        if (abw == null || !abw.isFinite()) return PUNKT_OHNE
        val s = (abw / Engpaesse.VOLL_PROZENT).coerceIn(-1.0, 1.0).toFloat()
        return mischen(PUNKT_OHNE, if (s >= 0f) up else down, 0.15f + 0.85f * abs(s))
    }

    fun mischen(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun kanal(sh: Int): Int {
            val x = (a ushr sh) and 0xFF; val y = (b ushr sh) and 0xFF
            return (x + (y - x) * k + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (kanal(16) shl 16) or (kanal(8) shl 8) or kanal(0)
    }
}
