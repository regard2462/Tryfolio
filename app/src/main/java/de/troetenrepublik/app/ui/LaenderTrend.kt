package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Boersenzeiten
import de.troetenrepublik.app.engine.Fruehindikator
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.Konjunkturphase
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.ohneAbbruch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// [Globus Trend und Ebenen] Monat "2026-09" als "09/2026"
internal fun trendMonat(m: String): String = if (m.length >= 7) m.substring(5, 7) + "/" + m.substring(0, 4) else m

// Abschnitt Trend im Laenderblatt: Fruehindikator und Konjunkturphase der OECD, Wirtschaftsstimmung (EU), Boerse jetzt und
// Vergleich mit einem zweiten Land. Die Wachstumskurve steht seit 0.70 als Balken im Abschnitt Wirtschaft.
@Composable
internal fun LaenderTrend(state: AppState, land: GlobusLand, imf: Imf.Datensatz?, jahr: Int) {
    var cli by remember { mutableStateOf(state.fruehindikatorGespeichert()) }
    var esi by remember { mutableStateOf<Fruehindikator.Datensatz?>(null) }
    var karte by remember { mutableStateOf<GlobusKarte?>(null) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var waehlen by remember { mutableStateOf(false) }
    var vergleich by remember(land.a3) { mutableStateOf<GlobusLand?>(null) }

    LaunchedEffect(land.a3) {
        ohneAbbruch { state.fruehindikatorDaten() }.getOrNull()?.let { cli = it }
        esi = ohneAbbruch { state.esiDaten() }.getOrNull()
        karte = ohneAbbruch { state.globusKarte() }.getOrNull()
    }

    val signal = LocalSignal.current
    val monat = remember { YearMonth.now() }
    val jetzt = remember(land.a3) { Instant.now() }
    val de = I18n.locale.language == "de"
    val phaseFarbe = { p: Fruehindikator.Phase? ->
        Color(GlobusFarbe.phase(p, signal.up.toArgb(), signal.down.toArgb(), Kategorie.ocker.toArgb()))
    }

    val cliReihe = Fruehindikator.aktuell(cli?.laender?.get(land.a3), monat)
    val phaseErg = Konjunkturphase.von(cli, imf, land.a3, monat, jahr)
    val phase = phaseErg?.phase
    val esiReihe = Fruehindikator.aktuell(esi?.laender?.get(land.a2), monat)
    val handel = Boersenzeiten.NACH_LAND[land.a2]
    val status = Boersenzeiten.status(land.a2, jetzt)
    val hatVergleich = imf != null && !Imf.land(imf, land.a3, jahr).leer
    if (cliReihe == null && phase == null && esiReihe == null && handel == null && !hatVergleich) return

    LaenderAbschnitt(S.trendTitel, "trend") {
        if (cliReihe != null) {
            val l = cliReihe.last()
            LbZeile(S.trendFrueh, Fmt.num(l.wert, 1) + " (" + trendMonat(l.monat) + ")") { info = S.trendFrueh to S.trendFruehInfo }
        }
        if (phase != null && phaseErg != null) {
            LbZeile(S.trendPhaseLabel, S.trendPhase(phase), phaseFarbe(phase)) { info = S.trendPhaseLabel to S.trendPhaseInfo }
            LbHinweis(if (phaseErg.ausCli) S.trendPhaseCli(trendMonat(phaseErg.monat.orEmpty())) else S.trendPhaseImf(phaseErg.jahr))
        }
        if (esiReihe != null) {
            val l = esiReihe.last()
            LbZeile(S.trendEsi, Fmt.num(l.wert, 1) + " (" + trendMonat(l.monat) + ")") { info = S.trendEsi to S.trendEsiInfo }
        }
        if (handel != null && status != null) {
            val zeit = Boersenzeiten.kurz(status.wechsel, ZoneId.systemDefault(), jetzt, I18n.locale)
            val text = if (status.offen) S.trendOffenBis(zeit) else S.trendOeffnet(zeit)
            LbZeile(
                S.trendBoerse, text, if (status.offen) signal.up else null,
            ) { info = S.trendBoerse to S.trendBoerseInfo(handel.name, Boersenzeiten.zeiten(handel, if (de) "und" else "and", if (de) "bis" else "to"), handel.zone.id) }
            if (Boersenzeiten.verkuerzt(land.a2, jetzt.atZone(handel.zone).toLocalDate())) LbHinweis(S.trendVerkuerzt)
        }
        if (hatVergleich) {
            LbZeile(S.trendVergleichen, vergleich?.name(de) ?: S.trendLandWaehlen) { waehlen = true }
            val v = vergleich
            if (v != null && imf != null) VergleichsTabelle(land, v, imf, jahr, cli, monat)
        }
        val quellen = ArrayList<Pair<String, String>>()
        if (imf != null) quellen += LaenderQuellen.iwf(land.a3)
        if (cliReihe != null || phaseErg?.ausCli == true) quellen += LaenderQuellen.oecd()
        if (esiReihe != null) quellen += LaenderQuellen.eurostat()
        QuellenLeiste(quellen)
    }

    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    val k = karte
    if (waehlen && k != null && imf != null) LandAuswahl(k, imf, land) { vergleich = it; waehlen = false }
}

// Schlüssel für die alphabetische Reihenfolge: ohne Umlaute und Akzente
private fun sortSchluessel(name: String): String =
    name.lowercase().replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")
        .replace("é", "e").replace("è", "e").replace("ç", "c").replace("ô", "o").replace("í", "i").replace("ã", "a").replace("å", "a")

// Liste der Länder mit IWF-Daten, alphabetisch, mit Suchfeld.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandAuswahl(karte: GlobusKarte, imf: Imf.Datensatz, aktuell: GlobusLand, onWahl: (GlobusLand?) -> Unit) {
    val de = I18n.locale.language == "de"
    var suche by remember { mutableStateOf("") }
    val alle = remember(karte, imf, de) {
        val codes = Imf.laenderCodes(imf)
        karte.laender.filter { it.a3 in codes && it.a3 != aktuell.a3 }.sortedBy { sortSchluessel(it.name(de)) }
    }
    val treffer = remember(alle, suche) {
        val q = sortSchluessel(suche.trim())
        if (q.isEmpty()) alle else alle.filter { sortSchluessel(it.name(de)).contains(q) }
    }
    ModalBottomSheet(
        onDismissRequest = { onWahl(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(S.trendLandWaehlen, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            OutlinedTextField(
                value = suche, onValueChange = { suche = it }, singleLine = true, label = { Text(S.trendSuche) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(treffer, key = { it.a3 }) { l ->
                    Text(
                        l.name(de), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onWahl(l) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

// Zweispaltige Tabelle der IWF-Werte beider Länder und der Konjunkturphase.
@Composable
private fun VergleichsTabelle(a: GlobusLand, b: GlobusLand, imf: Imf.Datensatz, jahr: Int, cli: Fruehindikator.Datensatz?, monat: YearMonth) {
    val de = I18n.locale.language == "de"
    val x = Imf.land(imf, a.a3, jahr)
    val y = Imf.land(imf, b.a3, jahr)
    fun pct(v: Double?) = v?.let { Fmt.pct(it, 1) }
    fun sig(v: Double?) = v?.let { Fmt.signedPct(it, 1) }
    fun ph(code: String) = Konjunkturphase.von(cli, imf, code, monat, jahr)?.let { S.trendPhase(it.phase) }
    val zeilen = listOf(
        Triple(S.globusWachstumJahr(jahr), sig(x.wachstum), sig(y.wachstum)),
        Triple(S.globusWachstumJahr(jahr + 1), sig(x.wachstumNaechstes), sig(y.wachstumNaechstes)),
        Triple(S.globusInflationJahr(jahr), pct(x.inflation), pct(y.inflation)),
        Triple(S.globusArbeitslos, pct(x.arbeitslosigkeit), pct(y.arbeitslosigkeit)),
        Triple(S.globusSchulden, pct(x.schulden), pct(y.schulden)),
        Triple(S.globusLeistung, sig(x.leistungsbilanz), sig(y.leistungsbilanz)),
        Triple(S.trendPhaseLabel, ph(a.a3), ph(b.a3)),
    ).plus(vergleichKaufkraft(a, b, imf, jahr)).filter { it.second != null || it.third != null }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S.trendVergleich, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.15f))
            Text(a.name(de), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text(b.name(de), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        for ((label, wa, wb) in zeilen) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.15f))
                Text(wa ?: "–", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                Text(wb ?: "–", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    VergleichUmrechnung(a, b, imf, jahr)
}
