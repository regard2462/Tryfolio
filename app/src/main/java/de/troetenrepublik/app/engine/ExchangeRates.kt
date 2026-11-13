package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote

// Einen nicht umgerechneten Preis in fremder Waehrung nie als EUR ausgeben.
object ExchangeRates {
    fun rate(currency: String, rates: Map<String, Double>): Double? {
        val value = when (currency) {
            "EUR" -> 1.0
            "GBp", "GBX" -> rates["GBP"]?.times(100)
            // US-Cent: Yahoo notiert einige Agrar-Futures in USX
            "USd", "USX" -> rates["USD"]?.times(100)
            // Tel Aviv notiert in Agorot, Johannesburg in Cent
            "ILA" -> rates["ILS"]?.times(100)
            "ZAc", "ZAC" -> rates["ZAR"]?.times(100)
            else -> rates[currency]
        }
        return value?.takeIf { it.isFinite() && it > 0 }
    }

    // Hauptwaehrung einer Pence-Notierung. Yahoo nennt den Boersenwert in der Hauptwaehrung (Pfund, Schekel, Rand),
    // auch wenn der Kurs in Pence, Agorot oder Cent steht (SHEL.L: Kurs 3701 GBp, Boersenwert 209,6 Mrd GBP bei 5,66 Mrd Aktien).
    fun hauptwaehrung(currency: String): String = when (currency) {
        "GBp", "GBX" -> "GBP"
        "USd", "USX" -> "USD"
        "ILA" -> "ILS"
        "ZAc", "ZAC" -> "ZAR"
        else -> currency
    }

    // Waehrung, deren Paar EUR<Waehrung>=X den Kurs fuehrt: Pence und US-Cent zaehlen als Pfund und Dollar; null fuer EUR.
    fun basisWaehrung(currency: String): String? = if (currency.isEmpty() || currency == "EUR") null else hauptwaehrung(currency)

    // Kerzen, die mit dem heutigen Kurs (Einheiten je Euro: heute) in Euro umgerechnet sind, auf den Wechselkurs des jeweiligen
    // Tages bringen. verlauf: Tagesschlusskurse des Paars EUR<Waehrung>=X, aufsteigend. Eine Kerze nimmt den Schluss der
    // letzten Tageskerze bis zu ihrem Zeitpunkt (vor dem Beginn der Reihe den ersten); ab dem Beginn der letzten gilt der
    // heutige Kurs. Ohne Verlauf bleiben die Kerzen unveraendert.
    fun mitTageskurs(kerzen: List<Candle>, heute: Double, verlauf: List<Candle>): List<Candle> {
        if (kerzen.isEmpty() || verlauf.size < 2 || !heute.isFinite() || heute <= 0.0) return kerzen
        var i = 0
        return kerzen.map { c ->
            while (i + 1 < verlauf.size && verlauf[i + 1].t <= c.t) i++
            val kurs = verlauf[i].close
            val faktor = if (c.t >= verlauf.last().t || !kurs.isFinite() || kurs <= 0.0) 1.0 else heute / kurs
            if (faktor == 1.0) c else c.copy(open = c.open * faktor, high = c.high * faktor, low = c.low * faktor, close = c.close * faktor)
        }
    }

    // Wechselkurs fuer Betraege, die Yahoo in der Hauptwaehrung meldet (Boersenwert), nicht in der Kurseinheit.
    fun rateHaupt(currency: String, rates: Map<String, Double>): Double? = rate(hauptwaehrung(currency), rates)

    fun toEur(q: Quote, rates: Map<String, Double>): Quote? {
        val rate = rate(q.nativeCurrency, rates) ?: return null
        if (!q.price.isFinite() || q.price <= 0) return null
        val rateCap = rateHaupt(q.nativeCurrency, rates) ?: rate
        return q.copy(
            price = q.price / rate, prevClose = q.prevClose / rate,
            open = q.open / rate, dayHigh = q.dayHigh / rate, dayLow = q.dayLow / rate,
            weekHigh52 = q.weekHigh52 / rate, weekLow52 = q.weekLow52 / rate,
            marketCap = q.marketCap / rateCap, currency = "EUR", nativePrice = q.price,
        )
    }
}
