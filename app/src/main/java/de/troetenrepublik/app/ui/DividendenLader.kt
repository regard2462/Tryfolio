package de.troetenrepublik.app.ui

import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.PortfolioTools

// Ausschuettungen eines Bestands fuer die erwarteten Dividenden (Dividendenkalender, Handelskalender): die Ex-Tage der
// letzten zwei Jahre und, wo das Profil sie nennt, der naechste angekuendigte Ex-Tag und Zahltag.
internal suspend fun dividendenQuelle(state: AppState, key: String, name: String, symbol: String, qty: Double): PortfolioTools.DividendSource {
    val events = de.troetenrepublik.app.market.ohneAbbruch { state.dividendenPrognose(symbol) }.getOrNull().orEmpty()
    val profil = de.troetenrepublik.app.market.ohneAbbruch { state.profileFor(symbol) }.getOrNull()
    return PortfolioTools.DividendSource(
        key, name, qty, events.map { it.exDate to it.amount },
        exAngekuendigt = profil?.exDividendDate ?: 0L, zahlungAngekuendigt = profil?.dividendDate ?: 0L,
    )
}
