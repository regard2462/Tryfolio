package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.BullBear
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Bafin
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.SecInsider

// Bull- und Bear-Case auf der Seite einer Aktie (seit 5.7): was dafuer und was dagegen spricht, aus Schlagzeilen
// (mit antippbarem Beleg) und Kennzahlen. Ohne Aussagen bleibt die Karte weg.
@Composable
internal fun BullBearKarte(
    state: AppState, symbol: String, isin: String, quote: Quote?, profile: MarketApi.Profile?, news: List<MarketApi.NewsItem>,
    onNews: (MarketApi.NewsItem) -> Unit,
) {
    val sec by state.sec.collectAsStateWithLifecycle()
    val bafin by state.bafin.collectAsStateWithLifecycle()
    val leer by state.leerpositionen.collectAsStateWithLifecycle()
    val fda by state.fda.collectAsStateWithLifecycle()
    val eu by state.euInsider.collectAsStateWithLifecycle()
    val welt by state.weltInsider.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val punkte = remember(quote?.nativePrice, profile, news, sec, bafin, leer, fda, eu, welt) {
        val leerSumme = de.troetenrepublik.app.market.Leerpositionen.LAENDER.firstNotNullOfOrNull { leer[it]?.titel?.get(isin) }?.summe
        val fdaJetzt = fda[symbol]
        val ab = java.time.LocalDate.now().minusDays(90).toString()
        val insider = (sec?.geschaefte?.filter { it.k.equals(symbol, true) }?.takeIf { it.isNotEmpty() }?.let { SecInsider.trend(it, ab) }
            ?: bafin?.eigengeschaefte?.filter { isin.isNotBlank() && it.isin == isin }?.takeIf { it.isNotEmpty() }?.let { Bafin.trend(it, ab) }
            // Register weiterer Laender (seit 5.9)
            ?: (welt[symbol]?.second ?: eu.values.map { d -> d.geschaefte.filter { isin.isNotBlank() && it.isin == isin } }.maxByOrNull { it.size })
                ?.takeIf { it.isNotEmpty() }?.let { de.troetenrepublik.app.market.EuInsider.trend(it, ab) })?.wert
        val fx = quote?.fx?.takeIf { it > 0 } ?: 1.0
        val p = profile
        val f = BullBear.Fakten(
            kurs = quote?.nativePrice ?: 0.0, kursziel = p?.targetPrice ?: 0.0, empfehlung = p?.recommendationMean ?: 0.0,
            // Yahoo-Werte kommen in Prozent (MarketApi rechnet mal 100), BullBear erwartet Anteile
            analysten = p?.analystCount ?: 0, umsatzWachstum = (p?.revenueGrowth ?: 0.0) / 100, gewinnWachstum = (p?.earningsGrowth ?: 0.0) / 100,
            gewinnMarge = (p?.profitMargin ?: 0.0) / 100, kasse = p?.totalCash ?: 0.0, schulden = p?.totalDebt ?: 0.0,
            freierCashflow = p?.freeCashflow ?: 0.0, marktwert = (quote?.marketCap ?: 0.0) / fx, kgv = quote?.peRatio ?: 0.0,
            kgvErwartet = p?.forwardPe ?: 0.0, beta = p?.beta ?: 0.0, ausschuettung = (p?.payoutRatio ?: 0.0) / 100,
            branche = p?.sector.orEmpty(), industrie = p?.industry.orEmpty(),
            ergebnisse = p?.epsHistory.orEmpty().map { it.actual to it.estimate }, insider = insider,
            leerverkauf = leerSumme,
            fdaZulassungen = fdaJetzt?.zulassungen?.count { it.art != de.troetenrepublik.app.market.Fda.Art.ANWENDUNG },
            fdaRueckrufe = fdaJetzt?.rueckrufe?.count { it.klasse == 1 },
        )
        val schlagzeilen = BullBear.zurFirma(
            news.map { BullBear.Schlagzeile(it.title, it.published, it.id) }, symbol,
            de.troetenrepublik.app.market.Gleichwerte.stamm(quote?.name?.ifBlank { null } ?: p?.longName ?: p?.shortName ?: symbol),
        )
        BullBear.auswerten(f, schlagzeilen, System.currentTimeMillis())
    }
    if (punkte.isEmpty()) return
    val w = profile?.financialCurrency?.ifBlank { null } ?: quote?.nativeCurrency.orEmpty()
    fun geld(v: Double) = Fmt.mitSymbol(Fmt.compact(v), Derivatives.currencySymbol(w))
    fun text(pt: BullBear.Punkt): String {
        pt.thema?.let { return S.bbThema(it.name) }
        val v = pt.werte
        val kz = pt.kennzahl ?: return ""
        val werte = when (kz) {
            BullBear.Kennzahl.ANALYSTEN_POSITIV, BullBear.Kennzahl.ANALYSTEN_SKEPTISCH ->
                listOf(Fmt.price(v[0], Derivatives.currencySymbol(quote?.nativeCurrency.orEmpty())), Fmt.signedPct(v[1], 0), v[2].toInt().toString())
            BullBear.Kennzahl.NETTOKASSE, BullBear.Kennzahl.CASHFLOW_NEGATIV -> listOf(geld(kotlin.math.abs(v[0])))
            BullBear.Kennzahl.SCHULDEN, BullBear.Kennzahl.CASHFLOW_POSITIV -> listOf(geld(v[0]), Fmt.pct(v[1], 0))
            BullBear.Kennzahl.UEBERTROFFEN, BullBear.Kennzahl.VERFEHLT -> listOf(v[0].toInt().toString(), v[1].toInt().toString())
            BullBear.Kennzahl.GEWINNE_STEIGEN -> listOf(Fmt.num(v[0], 1), Fmt.num(v[1], 1))
            BullBear.Kennzahl.BEWERTUNG_HOCH, BullBear.Kennzahl.SCHWANKUNG -> listOf(Fmt.num(v[0], 1))
            BullBear.Kennzahl.ZYKLISCH -> emptyList()
            BullBear.Kennzahl.INSIDER_KAUFEN, BullBear.Kennzahl.INSIDER_VERKAUFEN -> listOf(v[0].toInt().toString())
            BullBear.Kennzahl.LEERVERKAUF_HOCH -> listOf(Fmt.pct(v[0], 1))
            BullBear.Kennzahl.FDA_ZULASSUNG, BullBear.Kennzahl.FDA_RUECKRUF -> listOf(v[0].toInt().toString())
            else -> listOf(Fmt.pct(kotlin.math.abs(v.firstOrNull() ?: 0.0), 0))
        }
        return S.bbKennzahl(kz.name, werte)
    }
    Column {
        SectionHeader(S.bbTitel)
        listOf(BullBear.Seite.BULL to S.bbDafuer, BullBear.Seite.BEAR to S.bbDagegen).forEach { (seite, titel) ->
            val liste = punkte.filter { it.seite == seite }
            if (liste.isEmpty()) return@forEach
            Text(
                titel, style = MaterialTheme.typography.titleSmall, color = if (seite == BullBear.Seite.BULL) signal.up else signal.down,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 2.dp),
            )
            liste.forEach { pt ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(if (seite == BullBear.Seite.BULL) "▲" else "▼", style = MaterialTheme.typography.bodySmall,
                        color = if (seite == BullBear.Seite.BULL) signal.up else signal.down, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text(pt), style = MaterialTheme.typography.bodyMedium)
                        pt.belege.forEach { b ->
                            val n = news.firstOrNull { it.id == b.id }
                            Text(
                                "„" + b.titel + "“" + (if (b.zeit > 0) " · " + Fmt.relative(b.zeit) else ""),
                                style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp).then(if (n != null) Modifier.clickable { onNews(n) } else Modifier),
                            )
                        }
                    }
                }
            }
        }
        Text(
            S.bbHinweis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
