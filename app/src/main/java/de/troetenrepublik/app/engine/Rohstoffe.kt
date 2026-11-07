package de.troetenrepublik.app.engine

// Zuordnung von Rohstoff-Kuerzeln (Terminkontrakte) und Rohstoff-ETCs/ETFs des Katalogs zu einer Erklaerung
// (Texte je Sprache in i18n/RohstoffTexte.kt). null, wenn es fuer den Wert keine gibt.
object Rohstoffe {
    private val ZUORDNUNG = mapOf(
        "GC=F" to "GOLD", "4GLD.DE" to "GOLD", "PPFB.DE" to "GOLD", "XAD5.DE" to "GOLD", "8PSG.DE" to "GOLD", "GLDA.DE" to "GOLD",
        "SI=F" to "SILBER", "8PSB.DE" to "SILBER", "XAD6.DE" to "SILBER",
        "PL=F" to "PLATIN", "PA=F" to "PALLADIUM",
        "BZ=F" to "OEL", "CL=F" to "OEL",
        "NG=F" to "GAS", "HG=F" to "KUPFER",
        "ZW=F" to "WEIZEN", "ZC=F" to "MAIS",
        "KC=F" to "KAFFEE", "CC=F" to "KAKAO", "SB=F" to "ZUCKER",
        "SXRS.DE" to "KORB", "XDBC.DE" to "KORB", "UIQK.DE" to "KORB",
        "IS0E.DE" to "MINEN", "G2X.DE" to "MINEN",
    )

    fun schluessel(symbol: String): String? = ZUORDNUNG[symbol.uppercase()]

    // Kurs ist ein Terminkontrakt (Kuerzel mit =F): dazu gehoert der Hinweis auf Rollkosten.
    fun istTermin(symbol: String): Boolean = symbol.endsWith("=F")
}
