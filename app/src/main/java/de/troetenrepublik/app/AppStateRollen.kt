package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.Optionsrechner
import de.troetenrepublik.app.engine.OptionsrechnerPlan
import de.troetenrepublik.app.engine.StillhalterDepot
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.Fmt

// Verkauften Put rollen (0.78): die Position ganz zurueckkaufen (Briefkurs) und dieselbe Stueckzahl des Kontrakts neu
// zur Eroeffnung verkaufen (Geldkurs), mit denselben Pruefungen wie Rueckkauf und Anlegen einzeln. Scheitert ein
// Schritt, wird nichts gebucht. spot: Kurs des Basiswerts aus der Optionskette, zu dem der neue Preis gilt.
fun AppState.rollen(key: String, neu: Optionsanalyse.Kontrakt, spot: Double): Boolean {
    val pos = account.value.positions.firstOrNull { it.key == key && it.verkauft }
    val alt = pos?.derivative
    if (pos == null || alt == null) { showToast(S.error("NOT_IN_DEPOT", "")); return false }
    val jetzt = System.currentTimeMillis()
    if (!Trading.isQuoteFresh(quotes.value[pos.symbol], jetzt)) { showToast(S.refreshFirst); return false }
    if (!marketOpen(pos.symbol, alt)) { showToast(S.marketClosedOrders); return false }
    val stueck = -pos.qty
    val gruppe = OptionsrechnerPlan.gruppe(jetzt)
    val teil = OptionsrechnerPlan.Teil(Optionsrechner.Art.PUT, -stueck.toInt(), neu.strike, neu.verfall, neu.iv, neu.mitte)
    val schein = OptionsrechnerPlan.optionsschein(pos.symbol, alt.underlyingName, alt.ccy, teil, jetzt, spot, gruppe)
    registerDerivatives(listOf(alt, schein))
    val kauf = Trading.tradePrice(pos.symbol, alt, quotes.value, jetzt, spread, true, riskFree)
    val verkauf = Trading.tradePrice(pos.symbol, schein, quotes.value, jetzt, spread, false, riskFree)
    val fx = quotes.value[pos.symbol]?.fx ?: 1.0
    val freibetrag = settingsFlow.value.allowance
    var msg = ""
    var ok = false
    store.update { acc ->
        val zurueck = StillhalterDepot.rueckkauf(acc, key, stueck, kauf, jetzt, allowance = freibetrag)
        val r = if (!zurueck.ok) zurueck else StillhalterDepot.eroeffnen(
            zurueck.account, listOf(StillhalterDepot.Bein(schein.id, Derivatives.label(schein, I18n.locale), pos.symbol, -stueck, schein, verkauf)),
            gruppe, fx, jetzt, allowance = freibetrag,
        )
        ok = r.ok
        msg = r.error?.let { S.error(it, if (r.extra != 0.0) Fmt.euro(r.extra) else "") } ?: S.rhGerollt
        if (r.ok) r.account else acc
    }
    showToast(msg)
    return ok
}
