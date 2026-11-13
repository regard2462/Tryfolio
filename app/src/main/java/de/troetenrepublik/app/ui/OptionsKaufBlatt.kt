package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.strategieAnlegen
import de.troetenrepublik.app.strategieVorschau
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.StillhalterDepot
import de.troetenrepublik.app.engine.Optionsrechner.Art
import de.troetenrepublik.app.engine.OptionsrechnerPlan
import de.troetenrepublik.app.engine.OptionsrechnerPlan.Teil
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S

// Strategie in einem Zug ins Depot: je Teil eine Zeile mit Stueck und Kurs (gekauft zum Brief-, verkauft zum Geldkurs),
// darunter Kosten bzw. Einnahme und die Bindung fuer verkaufte Optionen. Gebucht wird ueber StillhalterDepot mit
// denselben Pruefungen und Gebuehren wie ein einzelner Handel, alle Teile oder keiner. Liegen die Aktien eines gedeckten
// Calls schon im Depot, werden sie nur gebunden.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsKaufBlatt(state: AppState, symbol: String, name: String, ccy: String, teile: List<Teil>, spot: Double = 0.0, onDismiss: () -> Unit) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val konto by state.account.collectAsStateWithLifecycle()
    val jetzt = remember(teile) { System.currentTimeMillis() }
    val gruppe = remember(teile) { if (OptionsrechnerPlan.gruppiert(teile)) OptionsrechnerPlan.gruppe(jetzt) else "" }
    val scheine = remember(teile) { teile.map { if (it.art == Art.AKTIE) null else OptionsrechnerPlan.optionsschein(symbol, name, ccy, it, jetzt, spot, gruppe) } }
    LaunchedEffect(scheine) { state.registerDerivatives(scheine.filterNotNull()) }
    // Kurse neu lesen, wenn sie sich bewegen: verkauft zum Geld-, gekauft zum Briefkurs
    val preise = remember(teile, quotes) { scheine.mapIndexed { i, d -> state.bidAsk(symbol, d).let { if (teile[i].verkauft) it.first else it.second } } }
    val liste = remember(teile, scheine) {
        teile.indices.map { i ->
            val d = scheine[i]
            AppState.KaufTeil(d?.id ?: symbol, if (d == null) name else Derivatives.label(d, I18n.locale), symbol, teile[i].stueck.toDouble(), d)
        }
    }
    // Vorschau mit dem Depot von jetzt: gekaufte Aktien, Bindung, fehlende Deckung oder fehlendes Guthaben
    val vorschau = remember(liste, quotes, konto) { state.strategieVorschau(liste, gruppe) }
    val aktienVorher = konto.positions.firstOrNull { it.key == symbol && it.derivative == null }?.qty ?: 0.0
    val aktienGekauft = if (vorschau.ok) (vorschau.account.positions.firstOrNull { it.key == symbol && it.derivative == null }?.qty ?: 0.0) - aktienVorher else null
    val bindung = if (vorschau.ok && gruppe.isNotBlank()) StillhalterDepot.bindung(vorschau.account, gruppe) else null
    // Ausserhalb der Handelszeit: Hinweis wie im Kaufblatt, der Knopf ruht
    val offen = remember(teile, quotes) { scheine.all { state.marketOpen(symbol, it) } }
    val mitVerkauf = teile.any { it.verkauft }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(false),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(if (mitVerkauf) S.shStrategie else S.buy, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp))
            var netto = 0.0
            teile.forEachIndexed { i, t ->
                val d = scheine[i]
                // Aktienbein: gekauft wird nur, was nicht schon frei im Depot liegt
                val stueck = if (d == null) aktienGekauft ?: t.stueck.toDouble() else kotlin.math.abs(t.stueck.toDouble())
                val betrag = preise[i] * stueck
                netto += if (t.verkauft) -betrag else betrag
                val kauf = S.orKaufHint(Fmt.num(stueck, 0), Fmt.priceEuro(preise[i]), d?.let { Fmt.dayMonth(it.expiry) }.orEmpty())
                val hint = when {
                    d != null || stueck >= t.stueck - 1e-9 -> kauf
                    stueck <= 1e-9 -> S.shNurGebunden(Fmt.num(t.stueck.toDouble(), 0))
                    else -> kauf + " · " + S.shNurGebunden(Fmt.num(t.stueck - stueck, 0))
                }
                KeyValueRow(
                    label = d?.let { (if (t.verkauft) S.orVerkauf + " · " else "") + Derivatives.label(it, I18n.locale) } ?: name,
                    value = if (t.verkauft) "+" + Fmt.euro(betrag) else Fmt.euro(betrag),
                    hint = hint,
                )
            }
            KeyValueRow(if (netto >= 0) S.orKosten else S.orErloes, Fmt.euro(kotlin.math.abs(netto)))
            bindung?.let { b ->
                if (b.cash > 0.005) KeyValueRow(S.shBindung, Fmt.euro(b.cash))
                if (b.stueck > 1e-9) KeyValueRow(S.shGebundeneAktien, "${Fmt.num(b.stueck, 0)} ${S.pieces}")
            }
            val meldung = when {
                !offen -> hoursLabel(MarketHours.status(null, true)).ifBlank { S.closed }
                !vorschau.ok -> S.error(vorschau.error.orEmpty(), if (vorschau.extra != 0.0) Fmt.euro(vorschau.extra) else "")
                else -> null
            }
            meldung?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
                )
            }
            FloatingPill(
                label = if (mitVerkauf) S.orAusfuehren else S.buy, plus = true, enabled = offen && vorschau.ok && preise.all { it > 0 },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                if (state.strategieAnlegen(liste, gruppe)) onDismiss()
            }
        }
    }
}
