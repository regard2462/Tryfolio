package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.XlsTabelle

// Eine Dateiauswahl fuer beide Metallboersen: erkannt wird am Inhalt, nicht am Namen. Die Excel-Datei der LME ist ein
// ZIP-Archiv (xlsx), die der COMEX eine OLE2-Verbunddatei (xls).
object LagerDatei {

    enum class Art { LME, COMEX, KEINE }

    class Ergebnis(val art: Art, val anzahl: Int)

    private fun istZip(b: ByteArray) = b.size > 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()

    fun einlesen(bytes: ByteArray, lme: LmeLagerAbruf, comex: ComexLagerAbruf): Ergebnis {
        if (XlsTabelle.istVerbunddatei(bytes)) {
            val n = comex.einlesen(bytes)
            return if (n > 0) Ergebnis(Art.COMEX, n) else Ergebnis(Art.KEINE, 0)
        }
        if (istZip(bytes)) {
            val n = lme.einlesen(bytes)
            return if (n > 0) Ergebnis(Art.LME, n) else Ergebnis(Art.KEINE, 0)
        }
        return Ergebnis(Art.KEINE, 0)
    }
}
