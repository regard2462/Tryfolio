package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Risikoregeln

// Warnungen der eigenen Risikoregeln zu einem Kauf (0.78): Kurs und Stueckzahl der Order, Stopp und Ziel in Euro
// (0 = nicht gesetzt). Tagesverlust und Zahl der Geschaefte stammen aus den Buchungen von heute.
fun AppState.risikoWarnungen(kurs: Double, stueck: Double, stopp: Double, ziel: Double): List<Risikoregeln.Befund> {
    val konto = account.value
    if (!konto.depotRegeln.risikoAktiv) return emptyList()
    val v = valuation.value
    val tag = Risikoregeln.tagesStand(konto.transactions, System.currentTimeMillis())
    return Risikoregeln.kaufPruefen(konto.depotRegeln, v.securities + v.cash, kurs, stueck, stopp, ziel, tag)
}
