package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.GewinnSchnitt
import de.troetenrepublik.app.engine.Historie078
import de.troetenrepublik.app.engine.Qualitaet
import de.troetenrepublik.app.engine.Splits
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import kotlin.math.abs

// KGV auf den Durchschnittsgewinn fuer die Wertpapierseite: Rechnung in engine/GewinnSchnitt.kt, hier der Kurs in der
// Berichtswaehrung und die Splitliste, die nur bei Verdacht auf einen Split geholt wird.
@Composable
internal fun rememberGewinnSchnitt(
    state: AppState, symbol: String, quote: Quote?, p: MarketApi.Profile, seite: SeitenWaehrung, jahre: List<Qualitaet.Jahr>?,
): GewinnSchnitt.Ergebnis? {
    val aktien = p.mehr.aktien
    var splits by remember(symbol) { mutableStateOf<List<Splits.Split>>(emptyList()) }
    val verdacht = remember(jahre, aktien) { jahre != null && GewinnSchnitt.splitVerdacht(jahre, aktien) }
    LaunchedEffect(symbol, verdacht) {
        if (verdacht) splits = runCatching { state.market.splits(symbol) }.getOrNull().orEmpty()
    }
    val handel = quote?.nativeCurrency.orEmpty()
    val kurs = quote?.nativePrice ?: 0.0
    return remember(jahre, splits, aktien, kurs, handel, seite, p.financialCurrency) {
        val j = jahre?.takeIf { it.isNotEmpty() } ?: return@remember null
        val bericht = j.last().waehrung.ifBlank { p.financialCurrency }.ifBlank { handel }
        val k = berichtsKurs(kurs, handel, bericht, seite) ?: return@remember null
        GewinnSchnitt.berechne(j, splits, aktien, k)
    }
}

// Kurs der Handelswaehrung in der Berichtswaehrung der Gewinne; null ohne Wechselkurs.
private fun berichtsKurs(kurs: Double, handel: String, bericht: String, seite: SeitenWaehrung): Double? {
    if (kurs <= 0) return null
    Historie078.waehrungsFaktor(handel, bericht)?.let { return kurs * it }
    val (v, w) = seite.nach(bericht).umrechnen(kurs, handel)
    return v.takeIf { w == bericht }
}

// Eine Zeile, nur wenn der letzte Jahresgewinn weit vom Schnitt abweicht.
internal fun gewinnSchnittHinweis(e: GewinnSchnitt.Ergebnis?): String? {
    if (e == null || e.lage == GewinnSchnitt.Lage.GLEICH) return null
    return S.kgvSchnittHinweis(e.letztesJahr, Fmt.pct(abs(e.abweichung), 0), e.jahre, e.lage == GewinnSchnitt.Lage.UEBER)
}
