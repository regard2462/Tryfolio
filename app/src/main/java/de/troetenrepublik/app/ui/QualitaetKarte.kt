package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.Qualitaet
import de.troetenrepublik.app.engine.Qualitaet.Punkt
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi

// Jahresende (2025-09-30) als Datum der App.
private fun jahresende(ende: String): String =
    runCatching { Fmt.date(java.time.LocalDate.parse(ende).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()) }.getOrDefault(ende)

// Werte einer Pruefung fuer die Begruendung: Renditen und Quoten in Prozent, Cashflow als Betrag, Aktien als Stueckzahl.
private fun pruefWert(punkt: Punkt, v: Double?, waehrung: String): String? = v?.let {
    when (punkt) {
        Punkt.CASHFLOW -> Fmt.mitSymbol(Fmt.compact(it), Derivatives.currencySymbol(waehrung))
        Punkt.KEINE_NEUEN_AKTIEN -> Fmt.compact(it)
        Punkt.LIQUIDITAET_STEIGT, Punkt.UMSCHLAG_STEIGT -> Fmt.num(it, 2)
        else -> Fmt.pct(it * 100, 1)
    }
}

// Piotroski-F-Score und Altman-Z-Score auf der Wertpapierseite einer Aktie, ohne Banken, Versicherer und
// Finanzwerte. Der Boersenwert fuer Altman kommt aus dem Kurs (in die Berichtswaehrung umgerechnet).
@Composable
fun QualitaetKarte(state: AppState, symbol: String, quote: Quote?, profile: MarketApi.Profile?, kopf: Boolean = true) {
    val p = profile ?: return
    if (Qualitaet.ausgenommen(p.sector, p.industry)) return
    val signal = LocalSignal.current
    val fx by state.market.fxKurse.collectAsStateWithLifecycle()
    var jahre by remember(symbol) { mutableStateOf<List<Qualitaet.Jahr>?>(null) }
    LaunchedEffect(symbol) { jahre = state.jahresabschluesse(symbol) }
    if (kopf) SectionHeader(S.qsTitel) else Spacer(Modifier.height(8.dp))
    val j = jahre ?: run { ToolNote(S.qsLaden); return }
    val (f, z) = remember(j, p, quote?.marketCap, fx) { qualitaetAus(j, p, quote, fx) }
    val letztes = j.lastOrNull()
    val waehrung = letztes?.waehrung?.ifBlank { null } ?: p.financialCurrency.ifBlank { quote?.nativeCurrency.orEmpty() }
    if (f == null && z == null) { ToolNote(S.qsKeineDaten); return }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 16.dp),
    ) {
        f?.let { pi ->
            val stufe = Qualitaet.stufe(pi)
            Text(S.qsPiotroski, style = MaterialTheme.typography.titleSmall, color = grau, modifier = Modifier.padding(horizontal = 16.dp))
            PunktText(
                S.qsScore(pi.score, pi.geprueft) + (stufe?.let { " · " + S.qsStufe(it.name) } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                color = when (stufe) { Qualitaet.Stufe.STARK -> signal.up; Qualitaet.Stufe.SCHWACH -> signal.down; else -> MaterialTheme.colorScheme.onSurface },
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 2.dp),
            )
            Text(S.qsJahre(jahresende(pi.jahr), jahresende(pi.vorjahr)), style = MaterialTheme.typography.bodySmall, color = grau,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp))
            pi.pruefungen.forEach { pr ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(24.dp).padding(top = 2.dp)) {
                        when (pr.erfuellt) {
                            true -> CheckMark(size = 14.dp, color = signal.up)
                            false -> Text("✕", style = MaterialTheme.typography.bodyMedium, color = signal.down)
                            null -> Text("?", style = MaterialTheme.typography.bodyMedium, color = grau)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(S.qsPunkt(pr.punkt.name), style = MaterialTheme.typography.bodyMedium)
                        val jetzt = pruefWert(pr.punkt, pr.jetzt, waehrung)
                        Text(
                            if (pr.erfuellt == null || jetzt == null) S.qsNichtPruefbar else S.qsBegruendung(pr.punkt.name, jetzt, pruefWert(pr.punkt, pr.vorher, waehrung)),
                            style = MaterialTheme.typography.bodySmall, color = grau,
                        )
                    }
                }
            }
        }
        z?.let { a ->
            var teile by rememberSaveable(symbol) { mutableStateOf(false) }
            if (f != null) Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clickable { teile = !teile }.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(S.qsAltman, style = MaterialTheme.typography.titleSmall, color = grau)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PunktText(
                        Fmt.num(a.z, 2) + " · " + S.qsZone(a.zone.name), style = MaterialTheme.typography.titleLarge,
                        color = when (a.zone) { Qualitaet.Zone.SICHER -> signal.up; Qualitaet.Zone.GEFAEHRDET -> signal.down; else -> MaterialTheme.colorScheme.onSurface },
                        modifier = Modifier.weight(1f),
                    )
                    AufklappPfeil(teile)
                }
                if (a.fassung == Qualitaet.Fassung.HERSTELLER) Text(S.qsAltmanJahr(jahresende(letztes?.ende.orEmpty())) + ". " + S.qsZonen, style = MaterialTheme.typography.bodySmall, color = grau)
                else Text(S.qsAltmanJahrAndere(jahresende(letztes?.ende.orEmpty())) + ". " + S.qsZonenAndere, style = MaterialTheme.typography.bodySmall, color = grau)
            }
            if (teile) {
                (if (a.fassung == Qualitaet.Fassung.HERSTELLER) listOf("WC" to a.betriebskapital, "RE" to a.ruecklagen, "EBIT" to a.ebit, "MV" to a.boersenwert, "UMSATZ" to a.umsatz)
                else listOf("WC" to a.betriebskapital, "RE" to a.ruecklagen, "EBIT" to a.ebit, "BW" to a.boersenwert)).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                        Text(S.qsBaustein(k), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
                        Text(Fmt.num(v, 2), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    ErklaerungAufklappen(S.qsHinweis, Modifier.padding(top = 4.dp))
}
