package de.troetenrepublik.app.engine

// Aktientyp nach Peter Lynch (0.78): sechs Typen, aus Gewinn- und Umsatzwachstum, Dividende, Branche, Verlustjahren
// und Kurs-Buchwert-Verhaeltnis. Die Grenzen sind Festlegungen der App. Geprueft wird in dieser Reihenfolge, der erste
// Treffer gilt:
//  1. Turnaround: Verlust im letzten Geschaeftsjahr, oder Gewinn nach einem Verlust im Vorjahr.
//  2. Zykliker: Branche schwankt mit der Konjunktur (BullBear.zyklisch).
//  3. Substanzwert: Kurs unter dem Buchwert (KBV unter 1).
//  4. Nach dem Wachstum pro Jahr (Gewinn, ohne brauchbare Gewinnreihe Umsatz):
//     - schneller Wachser ab 20 %; waechst der Umsatz dabei unter 10 %, nur stetiger Wachser;
//     - stetiger Wachser von 10 % bis unter 20 %;
//     - von 5 % bis unter 10 %: mit mindestens 2 % Dividendenrendite langsamer, sonst stetiger Wachser;
//     - langsamer Wachser unter 5 %.
// Ohne Wachstumsangabe und ohne Treffer in 1 bis 3 gibt es keinen Typ. Reine Rechnung.
object LynchTyp {

    enum class Typ { LANGSAM, STETIG, SCHNELL, ZYKLIKER, TURNAROUND, SUBSTANZ }

    const val SCHNELL_AB = 20.0
    const val STETIG_AB = 10.0
    const val LANGSAM_BIS = 5.0
    // Schneller Wachser nur, wenn auch der Umsatz mindestens so stark waechst
    const val UMSATZ_MIN = 10.0
    // Ab dieser Dividendenrendite gilt ein Wert mit 5 bis 10 % Wachstum als langsamer Wachser
    const val DIVIDENDE_AB = 2.0
    const val KBV_SUBSTANZ = 1.0

    // Pruefliste fuer schnelle Wachser: Fremd- durch Eigenkapital hoechstens 80 %, Wachstum ueber 50 % pro Jahr haelt
    // selten an.
    const val SCHULDEN_MAX = 80.0
    const val WACHSTUM_WARNUNG = 50.0

    // gewinne und umsaetze: Jahreswerte, aelteste zuerst. dividende: Rendite in Prozent (0 = keine). kbv: 0 oder
    // negativ = keine Angabe. verschuldung: Fremd- durch Eigenkapital in Prozent, null ohne Angabe.
    data class Eingabe(
        val gewinne: List<Double>,
        val umsaetze: List<Double>,
        val dividende: Double,
        val kbv: Double,
        val zyklisch: Boolean,
        val verschuldung: Double? = null,
    )

    enum class Grund { VERLUST, VERLUST_VORJAHR, BRANCHE, KBV, GEWINNWACHSTUM, UMSATZWACHSTUM }

    // schuldenOk: null ohne Angabe. Beide Pruefungen nur bei schnellen Wachsern.
    data class Pruefliste(val verschuldung: Double?, val schuldenOk: Boolean?, val wachstumZuHoch: Boolean)

    // wachstum: Gewinn pro Jahr in Prozent, umsatzwachstum: Umsatz pro Jahr; null, wo die Reihe es nicht hergibt.
    data class Ergebnis(
        val typ: Typ, val grund: Grund, val wachstum: Double?, val umsatzwachstum: Double?, val dividende: Double, val kbv: Double?,
        val pruefliste: Pruefliste?,
    )

    fun berechne(e: Eingabe): Ergebnis? {
        val gw = ReverseDcf.bisher(e.gewinne)
        val uw = ReverseDcf.bisher(e.umsaetze)
        val kbv = e.kbv.takeIf { it > 0 && it.isFinite() }
        val letzter = e.gewinne.lastOrNull()
        val vorher = e.gewinne.dropLast(1).lastOrNull()
        fun aus(t: Typ, g: Grund, liste: Pruefliste? = null) = Ergebnis(t, g, gw, uw, e.dividende, kbv, liste)
        if (letzter != null && letzter < 0) return aus(Typ.TURNAROUND, Grund.VERLUST)
        if (letzter != null && vorher != null && letzter > 0 && vorher < 0) return aus(Typ.TURNAROUND, Grund.VERLUST_VORJAHR)
        if (e.zyklisch) return aus(Typ.ZYKLIKER, Grund.BRANCHE)
        if (kbv != null && kbv < KBV_SUBSTANZ) return aus(Typ.SUBSTANZ, Grund.KBV)
        val w = gw ?: uw ?: return null
        val grund = if (gw != null) Grund.GEWINNWACHSTUM else Grund.UMSATZWACHSTUM
        val typ = when {
            w >= SCHNELL_AB -> if (gw != null && uw != null && uw < UMSATZ_MIN) Typ.STETIG else Typ.SCHNELL
            w >= STETIG_AB -> Typ.STETIG
            w >= LANGSAM_BIS -> if (e.dividende >= DIVIDENDE_AB) Typ.LANGSAM else Typ.STETIG
            else -> Typ.LANGSAM
        }
        val liste = if (typ == Typ.SCHNELL) {
            val v = e.verschuldung?.takeIf { it.isFinite() && it >= 0 }
            Pruefliste(v, v?.let { it <= SCHULDEN_MAX }, w > WACHSTUM_WARNUNG)
        } else null
        return aus(typ, grund, liste)
    }
}
