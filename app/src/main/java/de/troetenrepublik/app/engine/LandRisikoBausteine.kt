package de.troetenrepublik.app.engine

// [0.76] Die Bausteine hinter dem Landrisiko (LandRisiko.punkte): Ausgangswert und Abzuege in Punkten, damit die Karte
// zeigen kann, woraus die Zahl besteht. Die Summe der Bausteine ergibt die Punkte, solange sie nicht an 0 oder 100 stoessen.
object LandRisikoBausteine {
    // key: BASIS, SANKTIONEN_EU, SANKTIONEN_US, REGISTER, KONFLIKT, KRIEG, REISE, STRUKTUR; wert: Punkte (Abzuege negativ)
    class Baustein(val key: String, val wert: Double)

    fun zerlegen(f: LandRisiko.Fakten): List<Baustein> {
        if (LandRisiko.punkte(f) == null) return emptyList()
        val aus = ArrayList<Baustein>()
        var basis = f.wgi ?: 50.0
        if ((f.konflikt ?: 0) > 0 && f.wgi != null && f.stabilitaet != null) basis = (f.wgi * 6.0 - f.stabilitaet) / 5.0
        aus += Baustein("BASIS", basis)
        if (f.sanktionenEu > 0) aus += Baustein("SANKTIONEN_EU", -10.0)
        if (f.sanktionenUs > 0) aus += Baustein("SANKTIONEN_US", -10.0)
        val os = f.openSanctions ?: 0
        if (os >= 50) aus += Baustein("REGISTER", -10.0) else if (os >= 5) aus += Baustein("REGISTER", -5.0)
        when (f.konflikt ?: 0) {
            0 -> {}
            1 -> aus += Baustein("KONFLIKT", -10.0)
            else -> aus += Baustein("KRIEG", -20.0)
        }
        when (f.reise) {
            4 -> aus += Baustein("REISE", -10.0)
            3 -> aus += Baustein("REISE", -5.0)
            else -> {}
        }
        if (f.strukturen > 0) aus += Baustein("STRUKTUR", -3.0 * f.strukturen.coerceIn(0, 3))
        return aus
    }
}
