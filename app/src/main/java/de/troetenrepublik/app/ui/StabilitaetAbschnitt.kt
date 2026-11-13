package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.troetenrepublik.app.engine.Stabilitaet
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi

// Stabilitaet im Bilanzblatt: Nettoschulden gegen Eigenkapital, freien Cashflow und EBITDA, je mit Ampel und Schwellen.
// Ohne Schuldenangabe erscheint nichts.
@Composable
internal fun StabilitaetAbschnitt(p: MarketApi.Profile) {
    val zeilen = remember(p) { Stabilitaet.berechne(p.totalDebt, p.totalCash, p.equity, p.freeCashflow, p.ebitda) }
    if (zeilen.isEmpty()) return
    SectionHeader(S.stabTitel)
    zeilen.forEach { z ->
        val satz = when (z.lage) {
            Stabilitaet.Lage.KEINE_NETTOSCHULDEN -> S.stabKeine
            Stabilitaet.Lage.NENNER_NEGATIV -> S.stabNegativ(z.kennzahl.name)
            Stabilitaet.Lage.WERT -> S.stabSatz(
                z.kennzahl.name,
                when (z.kennzahl) {
                    Stabilitaet.Kennzahl.GEARING -> Fmt.pct(z.wert, 0)
                    else -> Fmt.num(z.wert, 1)
                },
            )
        }
        AmpelZeile(S.stabName(z.kennzahl.name), z.ampel, satz, S.stabSchwelle(z.kennzahl.name), null)
    }
}
