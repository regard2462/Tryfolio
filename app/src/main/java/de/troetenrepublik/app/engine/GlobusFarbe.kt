package de.troetenrepublik.app.engine

import kotlin.math.abs

// Einfaerbung des Globus ohne Oberflaeche. Farben als ARGB-Zahlen, damit Compose und das Widget-Bild sie teilen.
enum class GlobusModus {
    // Seit 0.73 (Gesamtbild): Punkte von 0 bis 100 je Land aus Wohlstand, Wachstum, Stabilitaet, Regierungsfuehrung und Sicherheit
    GESAMT,
    BOERSE, WACHSTUM, AUSBLICK, INFLATION,
    // Seit 0.69 (Globus Trend und Ebenen): weitere IWF-Ebenen, Konjunkturphase, Boersenzeiten und Anteil des Depots je Land
    ARBEITSLOS, SCHULDEN, LEISTUNG, PHASE, BOERSENZEIT, DEPOT,
    // Seit 0.69 (Engpaesse): Punkte an den Engpaessen des Seehandels, Laender gedimmt
    ENGPAESSE,
    // Seit 0.78 (Laender Historie): Preis zu Einkommen gegen den eigenen Schnitt seit 1975, reale Aktienrendite je Jahr seit 1870 und seit 1950
    HAUSPREISE, AKTIEN1870, AKTIEN1950,
    // Seit 0.78 (Waehrung und Kaufkraft): Preisniveau gegen das Land des Nutzers, Wechselkurs gegen die Kaufkraftparitaet zum
    // Dollar, Index der menschlichen Entwicklung und Ungleichheit der Einkommen (Gini)
    PREISNIVEAU, KKP, HDI, GINI;

    // Ebenen mit Zeitregler (IWF-Jahreswerte)
    val mitJahr: Boolean get() = this == GESAMT || this == WACHSTUM || this == AUSBLICK || this == INFLATION || this == ARBEITSLOS || this == SCHULDEN || this == LEISTUNG ||
        this == PREISNIVEAU || this == KKP
}

// Farben des Globus je Erscheinungsbild (ARGB). Dunkel ist die bisherige Fassung; Hell und Sepia haben hellen Ozean und
// Grund, sichtbare Grenzen und dunkle Beschriftung mit hellem Umriss.
class GlobusPalette(
    val hintergrund: Int, val kugel: Int, val ohneDaten: Int, val grenze: Int, val nullwert: Int, val gedimmt: Int,
    val punktOhne: Int, val rand: Int, val nacht: Int, val schrift: Int, val schriftRand: Int, val ladeSchrift: Int,
) {
    companion object {
        val DUNKEL = GlobusPalette(
            GlobusFarbe.HINTERGRUND, GlobusFarbe.KUGEL, GlobusFarbe.OHNE_DATEN, GlobusFarbe.GRENZE, 0xFF3A3A3E.toInt(), GlobusFarbe.GEDIMMT,
            GlobusFarbe.PUNKT_OHNE, 0xFF2C2C30.toInt(), 0x8C000000.toInt(), 0xFFFFFFFF.toInt(), 0xE6101014.toInt(), 0xFF9A9AA0.toInt(),
        )
        val HELL = GlobusPalette(
            0xFFFFFFFF.toInt(), 0xFFC9D9E8.toInt(), 0xFFE2E5E8.toInt(), 0xFF7D838B.toInt(), 0xFFBFC5CC.toInt(), 0xFFE4E8EC.toInt(),
            0xFF6B7078.toInt(), 0xFFB4C2CF.toInt(), 0x3D1B2B45.toInt(), 0xFF101014.toInt(), 0xE6FFFFFF.toInt(), 0xFF5F6368.toInt(),
        )
        val SEPIA = GlobusPalette(
            Erscheinung.SEPIA_GRUND, 0xFFDCCFAE.toInt(), 0xFFEDE2C6.toInt(), 0xFF8A7557.toInt(), 0xFFC9B994.toInt(), 0xFFE8DCBE.toInt(),
            0xFF7A6A52.toInt(), 0xFFC2B28E.toInt(), 0x403A2A14.toInt(), Erscheinung.SEPIA_TEXT, 0xE6F4ECD8.toInt(), Erscheinung.SEPIA_TEXT2,
        )

        fun von(dunkel: Boolean, sepia: Boolean): GlobusPalette = if (dunkel) DUNKEL else if (sepia) SEPIA else HELL
    }
}

object GlobusFarbe {
    const val HINTERGRUND = 0xFF000000.toInt()
    // Land ohne Daten und Kugel
    const val OHNE_DATEN = 0xFF1E1E20.toInt()
    const val KUGEL = 0xFF08080A.toInt()
    const val GRENZE = 0xFF6A6A70.toInt()
    // Gleich kleine Werte sollen sich von „keine Daten“ abheben: Mindestanteil der Farbe
    private const val SOCKEL = 0.28f

    // Wert, bei dem die Farbe voll deckt.
    fun voll(m: GlobusModus): Double = when (m) {
        // Gesamtpunkte von 0 bis 100: Abstand von der Mitte (50) bis zur vollen Farbe
        GlobusModus.GESAMT -> 50.0
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
        // Abweichung in Prozent vom eigenen Schnitt; reale Rendite je Jahr in Prozent
        GlobusModus.HAUSPREISE -> 40.0
        GlobusModus.AKTIEN1870, GlobusModus.AKTIEN1950 -> 8.0
        // Prozent um 100 (Preisniveau) und um 0 (Kaufkraftparitaet); Indexpunkte um 0,7; Gini-Punkte um 38
        GlobusModus.PREISNIVEAU, GlobusModus.KKP -> 60.0
        GlobusModus.HDI -> 0.3
        GlobusModus.GINI -> 15.0
    }

    // Grauer Punkt der Skala: bei Arbeitslosigkeit die Schwelle des EU-Verfahrens bei Ungleichgewichten (10 %), bei
    // Staatsschulden der Referenzwert von Maastricht (60 %), beim Gesamtbild 50 Punkte.
    fun mitte(m: GlobusModus): Double = when (m) {
        GlobusModus.GESAMT -> 50.0
        GlobusModus.ARBEITSLOS -> 10.0
        GlobusModus.SCHULDEN -> 60.0
        GlobusModus.PREISNIVEAU -> 100.0
        GlobusModus.HDI -> 0.7
        GlobusModus.GINI -> 38.0
        else -> 0.0
    }

    // Bei Arbeitslosigkeit und Schulden ist ein niedriger Wert der gute.
    fun niedrigGut(m: GlobusModus): Boolean = m == GlobusModus.ARBEITSLOS || m == GlobusModus.SCHULDEN

    // Ebenen ohne Urteil: darueber neutral, darunter blau
    fun ohneUrteil(m: GlobusModus): Boolean =
        m == GlobusModus.HAUSPREISE || m == GlobusModus.PREISNIVEAU || m == GlobusModus.KKP || m == GlobusModus.GINI

    // Staerke von -1 bis 1; bei INFLATION von 0 bis 1 (Werte unter 0 zaehlen als 0).
    fun staerke(m: GlobusModus, wert: Double): Float {
        val roh = ((wert - mitte(m)) / voll(m)).coerceIn(-1.0, 1.0)
        val s = if (niedrigGut(m)) -roh else roh
        return (if (m == GlobusModus.INFLATION) maxOf(s, 0.0) else s).toFloat()
    }

    // Farbe eines Landes: ohne Wert dunkelgrau, sonst von Grau zu hoch (up) oder tief (down) nach Betrag. Bei INFLATION
    // von Grau zu `neutral` (keine Gewinn- oder Verlustbedeutung).
    fun farbe(m: GlobusModus, wert: Double?, up: Int, down: Int, neutral: Int, p: GlobusPalette = GlobusPalette.DUNKEL): Int {
        if (wert == null || !wert.isFinite()) return p.ohneDaten
        val s = staerke(m, wert)
        // Hauspreise: kein Urteil, darueber neutral, darunter blau
        val ziel = if (m == GlobusModus.INFLATION) neutral else if (ohneUrteil(m)) (if (s >= 0f) neutral else ERHOLUNG) else if (s >= 0f) up else down
        return mischen(p.nullwert, ziel, SOCKEL + (1f - SOCKEL) * abs(s))
    }

    // Farben der Konjunkturphasen: Aufschwung up, Abschwaechung neutral, Abschwung down, Erholung blau.
    const val ERHOLUNG = 0xFF4C8DD6.toInt()

    fun phase(p: Fruehindikator.Phase?, up: Int, down: Int, neutral: Int, pal: GlobusPalette = GlobusPalette.DUNKEL): Int = when (p) {
        null -> pal.ohneDaten
        Fruehindikator.Phase.AUFSCHWUNG -> up
        Fruehindikator.Phase.ABSCHWAECHUNG -> neutral
        Fruehindikator.Phase.ABSCHWUNG -> down
        Fruehindikator.Phase.ERHOLUNG -> ERHOLUNG
    }

    // Boerse offen in der Farbe up, geschlossen dunkelgrau, ohne Boerse keine Daten.
    fun handel(offen: Boolean?, up: Int, p: GlobusPalette = GlobusPalette.DUNKEL): Int = when (offen) {
        null -> p.ohneDaten
        true -> up
        false -> p.nullwert
    }

    // Depotanteil eines Landes (0 bis 1): ab diesem Anteil deckt die Farbe voll.
    const val ANTEIL_VOLL = 0.5

    fun anteil(a: Double?, farbe: Int, p: GlobusPalette = GlobusPalette.DUNKEL): Int {
        if (a == null || !a.isFinite() || a <= 0.0) return p.ohneDaten
        return mischen(p.nullwert, farbe, SOCKEL + (1f - SOCKEL) * (a / ANTEIL_VOLL).coerceIn(0.0, 1.0).toFloat())
    }

    // Laender der Ebene Engpaesse: gedimmt, damit die Punkte vorn stehen
    const val GEDIMMT = 0xFF17171A.toInt()
    // Punkt ohne Vergleichswert
    const val PUNKT_OHNE = 0xFFB4B4BC.toInt()

    // Farbe eines Engpasses nach der Veraenderung der Durchfahrten in Prozent: Grau bei 0, Rot (down) beim Rueckgang,
    // Gruen (up) beim Zuwachs, voll bei +-40 %; ohne Vergleichswert hellgrau.
    fun engpass(abw: Double?, up: Int, down: Int, p: GlobusPalette = GlobusPalette.DUNKEL): Int {
        if (abw == null || !abw.isFinite()) return p.punktOhne
        val s = (abw / Engpaesse.VOLL_PROZENT).coerceIn(-1.0, 1.0).toFloat()
        return mischen(p.punktOhne, if (s >= 0f) up else down, 0.15f + 0.85f * abs(s))
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
