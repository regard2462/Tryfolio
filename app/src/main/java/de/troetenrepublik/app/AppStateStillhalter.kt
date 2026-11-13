package de.troetenrepublik.app

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.engine.StillhalterDepot
import de.troetenrepublik.app.engine.TradeResult
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.Fmt

// Verkaufte Optionen und Strategien aus dem Optionsrechner (0.77): anlegen, Vorschau, Rueckkauf und Schliessen einer
// Gruppe, mit denselben Pruefungen, Handelszeiten und Spreads wie ein einzelner Handel.

private fun fehlerText(code: String?, extra: Double): String? = code?.let { S.error(it, if (extra != 0.0) Fmt.euro(extra) else "") }

// Strategie in einem Zug anlegen: gekaufte Teile zum Briefkurs, verkaufte Optionen zum Geldkurs. Fehlt Deckung oder
// Guthaben, wird nichts gebucht (StillhalterDepot.eroeffnen).
fun AppState.strategieAnlegen(teile: List<AppState.KaufTeil>, gruppe: String): Boolean {
    val jetzt = System.currentTimeMillis()
    for (t in teile) {
        if (!Trading.isQuoteFresh(quotes.value[t.symbol], jetzt)) { showToast(S.refreshFirst); return false }
        if (!marketOpen(t.symbol, t.derivative)) { showToast(S.marketClosedOrders); return false }
    }
    registerDerivatives(teile.mapNotNull { it.derivative })
    var msg = ""
    var ok = false
    store.update { acc ->
        val r = strategieRechnen(acc, teile, gruppe, jetzt)
        ok = r.ok
        msg = fehlerText(r.error, r.extra) ?: S.orGekauft
        r.account
    }
    showToast(msg)
    return ok
}

// Dieselbe Rechnung ohne zu buchen, fuer die Vorschau im Blatt: Bindung, gekaufte Aktien, fehlende Deckung.
fun AppState.strategieVorschau(teile: List<AppState.KaufTeil>, gruppe: String): TradeResult =
    strategieRechnen(account.value, teile, gruppe, System.currentTimeMillis())

private fun AppState.strategieRechnen(acc: Account, teile: List<AppState.KaufTeil>, gruppe: String, jetzt: Long): TradeResult {
    val beine = teile.map { t ->
        StillhalterDepot.Bein(t.key, t.name, t.symbol, t.qty, t.derivative,
            Trading.tradePrice(t.symbol, t.derivative, quotes.value, jetzt, spread, t.qty > 0, riskFree))
    }
    val fx = quotes.value[teile.firstOrNull()?.symbol.orEmpty()]?.fx ?: 1.0
    return StillhalterDepot.eroeffnen(acc, beine, gruppe, fx, jetzt, allowance = settingsFlow.value.allowance)
}

// Verkaufte Option ganz zurueckkaufen, zum Briefkurs und zu den Handelszeiten wie beim Kauf.
fun AppState.rueckkauf(key: String): Boolean {
    val pos = account.value.positions.firstOrNull { it.key == key && it.verkauft } ?: run {
        showToast(S.error("NOT_IN_DEPOT", "")); return false
    }
    val jetzt = System.currentTimeMillis()
    if (!Trading.isQuoteFresh(quotes.value[pos.symbol], jetzt)) { showToast(S.refreshFirst); return false }
    if (!marketOpen(pos.symbol, pos.derivative)) { showToast(S.marketClosedOrders); return false }
    val preis = Trading.tradePrice(pos.symbol, pos.derivative, quotes.value, jetzt, spread, true, riskFree)
    registerDerivatives(listOfNotNull(pos.derivative))
    var msg = ""
    var ok = false
    store.update { acc ->
        val r = StillhalterDepot.rueckkauf(acc, key, -pos.qty, preis, jetzt, allowance = settingsFlow.value.allowance)
        ok = r.ok
        msg = fehlerText(r.error, r.extra) ?: S.shRueckkaufDone(Fmt.qty(-pos.qty), Fmt.priceEuro(preis))
        r.account
    }
    showToast(msg)
    return ok
}

// Alle Beine einer Gruppe in einem Zug schliessen: verkaufte zum Brief-, gekaufte zum Geldkurs.
fun AppState.gruppeSchliessen(gruppe: String): Boolean {
    val beine = StillhalterDepot.gruppe(account.value, gruppe)
    if (beine.isEmpty()) { showToast(S.error("NOT_IN_DEPOT", "")); return false }
    val jetzt = System.currentTimeMillis()
    for (p in beine) {
        if (!Trading.isQuoteFresh(quotes.value[p.symbol], jetzt)) { showToast(S.refreshFirst); return false }
        if (!marketOpen(p.symbol, p.derivative)) { showToast(S.marketClosedOrders); return false }
    }
    registerDerivatives(beine.mapNotNull { it.derivative })
    var msg = ""
    var ok = false
    store.update { acc ->
        val r = StillhalterDepot.gruppeSchliessen(acc, gruppe, { p ->
            Trading.tradePrice(p.symbol, p.derivative, quotes.value, jetzt, spread, p.verkauft, riskFree)
        }, jetzt, allowance = settingsFlow.value.allowance)
        ok = r.ok
        msg = fehlerText(r.error, r.extra) ?: S.shGruppeGeschlossen
        r.account
    }
    showToast(msg)
    return ok
}
