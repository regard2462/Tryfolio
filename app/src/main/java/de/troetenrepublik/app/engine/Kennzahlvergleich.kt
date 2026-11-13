package de.troetenrepublik.app.engine

// Fundamentalvergleich mehrerer Aktien: je Kennzahl der Wert jeder Aktie und welcher davon am guenstigsten bzw.
// staerksten ist. Prozentwerte als Prozent (5,0 = 5 %), Vielfache als Zahl.
object Kennzahlvergleich {

    // NIEDRIG: kleiner ist besser (Bewertung, Verschuldung), nur positive Werte zaehlen. HOCH: groesser ist besser.
    enum class Richtung { NIEDRIG, HOCH }

    // hervorheben false: Risikozeilen, bei denen kein Wert als „bester“ markiert wird.
    enum class Zeile(val richtung: Richtung, val hervorheben: Boolean = true) {
        KGV(Richtung.NIEDRIG), KGV_ERWARTET(Richtung.NIEDRIG), KUV(Richtung.NIEDRIG), KBV(Richtung.NIEDRIG),
        EV_EBITDA(Richtung.NIEDRIG), KURS_CASHFLOW(Richtung.NIEDRIG), BRUTTOMARGE(Richtung.HOCH), OPERATIVE_MARGE(Richtung.HOCH),
        UMSATZWACHSTUM(Richtung.HOCH), ROIC(Richtung.HOCH), OWNER_EARNINGS(Richtung.HOCH), VERSCHULDUNG(Richtung.NIEDRIG),
        ZINSDECKUNG(Richtung.HOCH), DIVIDENDE(Richtung.HOCH), KURSZIEL(Richtung.HOCH),
        BETA(Richtung.NIEDRIG, false), VOLATILITAET(Richtung.NIEDRIG, false), MAX_RUECKGANG(Richtung.HOCH, false),
    }

    // Kennzahlen einer Aktie aus dem Profil; null, wo nichts vorliegt.
    data class Werte(
        val kgv: Double? = null,
        val kgvErwartet: Double? = null,
        val kuv: Double? = null,
        val kbv: Double? = null,
        val evEbitda: Double? = null,
        val bruttomarge: Double? = null,
        val operativeMarge: Double? = null,
        val umsatzwachstum: Double? = null,
        // Fremdkapital durch Eigenkapital als Verhaeltnis (1,5 = 150 %).
        val verschuldung: Double? = null,
        val dividende: Double? = null,
        // Abstand des Kursziels zum Kurs in Prozent.
        val kursziel: Double? = null,
        // Aus den Jahresabschluessen: Vielfache und Rendite in Prozent.
        val kursCashflow: Double? = null,
        val roic: Double? = null,
        val ownerEarnings: Double? = null,
        val zinsdeckung: Double? = null,
        // Risiko: Beta, Schwankung der letzten 252 Handelstage in Prozent, groesster Rueckgang in Prozent (negativ).
        val beta: Double? = null,
        val volatilitaet: Double? = null,
        val maxRueckgang: Double? = null,
    ) {
        fun wert(z: Zeile): Double? = when (z) {
            Zeile.KGV -> kgv
            Zeile.KGV_ERWARTET -> kgvErwartet
            Zeile.KUV -> kuv
            Zeile.KBV -> kbv
            Zeile.EV_EBITDA -> evEbitda
            Zeile.BRUTTOMARGE -> bruttomarge
            Zeile.OPERATIVE_MARGE -> operativeMarge
            Zeile.UMSATZWACHSTUM -> umsatzwachstum
            Zeile.VERSCHULDUNG -> verschuldung
            Zeile.DIVIDENDE -> dividende
            Zeile.KURSZIEL -> kursziel
            Zeile.KURS_CASHFLOW -> kursCashflow
            Zeile.ROIC -> roic
            Zeile.OWNER_EARNINGS -> ownerEarnings
            Zeile.ZINSDECKUNG -> zinsdeckung
            Zeile.BETA -> beta
            Zeile.VOLATILITAET -> volatilitaet
            Zeile.MAX_RUECKGANG -> maxRueckgang
        }?.takeIf { it.isFinite() }
    }

    // 0 als Angabe von Yahoo heisst meist „fehlt“; bei Verhaeltnissen und Margen gilt es daher als null.
    fun oderNull(v: Double): Double? = v.takeIf { it != 0.0 && it.isFinite() }

    // Kurs zu Kursziel in Prozent; null ohne beide.
    fun abstandKursziel(kurs: Double, ziel: Double): Double? = if (kurs > 0 && ziel > 0) (ziel / kurs - 1.0) * 100.0 else null

    // Index der besten Aktie in einer Zeile; null, wenn weniger als zwei Werte vergleichbar sind oder mehrere gleichauf liegen.
    fun bester(werte: List<Double?>, richtung: Richtung): Int? {
        val gueltig = werte.mapIndexedNotNull { i, v ->
            v?.takeIf { it.isFinite() && (richtung == Richtung.HOCH || it > 0) }?.let { i to it }
        }
        if (gueltig.size < 2) return null
        val ziel = if (richtung == Richtung.NIEDRIG) gueltig.minOf { it.second } else gueltig.maxOf { it.second }
        val beste = gueltig.filter { it.second == ziel }
        return beste.singleOrNull()?.first
    }
}
