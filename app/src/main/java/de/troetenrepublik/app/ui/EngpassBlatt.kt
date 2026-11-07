package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Engpaesse
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.ohneAbbruch

// [Engpaesse] Ebene „Engpaesse“ des Globus: Liste der Engpaesse und Blatt zu einem Engpass.

private fun tagText(tag: Int): String = Fmt.date(tag * 86_400_000L + 43_200_000L)

// Veraenderung in Prozent; Rueckgang ab der Stoerungsschwelle in der Verlustfarbe
@Composable
private fun abwFarbe(abw: Double?): Color? {
    val signal = LocalSignal.current
    return when {
        abw == null -> null
        abw <= Engpaesse.STOERUNG_PROZENT -> signal.down
        abw >= -Engpaesse.STOERUNG_PROZENT -> signal.up
        else -> null
    }
}

// Die Engpaesse nach Veraenderung der Durchfahrten, staerkster Rueckgang zuerst. Acht stehen oben, der Pfeil zeigt alle.
@Composable
internal fun EngpassListe(u: Engpaesse.Uebersicht?, onOeffnen: (String) -> Unit) {
    if (u == null) {
        Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        return
    }
    val alle = remember(u) { Engpaesse.sortiert(u) }
    var offen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(S.engListe, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        for ((s, l) in if (offen) alle else alle.take(8)) {
            val a = l.abw
            KeyValueRow(S.engName(s.id), a?.let { Fmt.signedPct(it, 0) } ?: Fmt.num(l.n, 0), valueColor = abwFarbe(a), onClick = { onOeffnen(s.id) })
        }
        if (alle.size > 8) ListenPfeil(offen) { offen = !offen }
        Text(
            S.engStand(tagText(u.tag)) + PUNKT + S.engQuelleKurz, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

private class BlattDaten(
    val verlauf: Engpaesse.Verlauf?,
    val meldungen: List<de.troetenrepublik.app.engine.LaenderZusatz.Meldung>,
    val sanktionen: de.troetenrepublik.app.engine.Sanktionen.Liste?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EngpassBlatt(state: AppState, id: String, u: Engpaesse.Uebersicht?, karte: GlobusKarte?, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val stelle = Engpaesse.stelle(id) ?: return
    var daten by remember(id) { mutableStateOf<BlattDaten?>(null) }
    var info by remember(id) { mutableStateOf<Pair<String, String>?>(null) }
    var artikel by remember(id) { mutableStateOf<MarketApi.NewsItem?>(null) }
    LaunchedEffect(id) {
        val v = ohneAbbruch { state.engpassVerlauf(id) }.getOrNull()
        val s = ohneAbbruch { state.risikoSanktionenListe() }.getOrNull()
        daten = BlattDaten(v, emptyList(), s)
        val m = ohneAbbruch { state.engpassMeldungen(id, S.engSuche(id)) }.getOrDefault(emptyList())
        daten = BlattDaten(v, m, s)
    }
    val d = daten
    val tage = d?.verlauf?.tage
    val lage = remember(d, u) { tage?.let { t -> Engpaesse.lage(t, t.last().t) } ?: u?.lage?.get(id) }
    val letzter = tage?.lastOrNull()?.t ?: u?.tag
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(S.engName(id), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (lage == null) {
                Text(
                    if (d == null || u == null && d.verlauf == null) S.loading else S.engKeineDaten,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                val a = lage.abw
                if (a != null) Text(
                    S.engGegenVorjahr(Fmt.signedPct(a, 0)), style = MaterialTheme.typography.titleMedium, color = abwFarbe(a) ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), maxLines = 1, softWrap = false,
                )
                KeyValueRow(S.engProTag, Fmt.num(lage.n, 0), onClick = { info = S.engProTag to S.engInfoProTag })
                lage.vn?.let { KeyValueRow(S.engVorjahr, Fmt.num(it, 0), onClick = { info = S.engVorjahr to S.engInfoVorjahr }) }
                if (lage.k >= 1.0 || (lage.vk ?: 0.0) >= 1.0) KeyValueRow(S.engTanker, zeile(lage.k, lage.vk), onClick = { info = S.engTanker to S.engInfoSchiffsart })
                if (lage.c >= 1.0 || (lage.vc ?: 0.0) >= 1.0) KeyValueRow(S.engContainer, zeile(lage.c, lage.vc), onClick = { info = S.engContainer to S.engInfoSchiffsart })
                if (letzter != null) Text(
                    S.engStand(tagText(letzter)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            val reihe = remember(tage) { tage?.let { Engpaesse.glatt(it) }.orEmpty() }
            if (reihe.size >= 30) {
                AbschnittKopf(S.engVerlauf)
                Verlaufsbild(reihe, lage?.vn, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(88.dp))
            }

            AbschnittKopf(S.engBedeutung)
            Text(S.engInfo(id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
            AbschnittKopf(S.engWarenTitel)
            Text(S.engWaren(id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))

            SanktionsZeilen(stelle.anrainer, karte, d?.sanktionen) { info = it }
            if (stelle.werte.isNotEmpty()) {
                AbschnittKopf(S.engWerte)
                WerteListe(stelle.werte, onOpen)
            }
            SchlagzeilenListe(d?.meldungen.orEmpty()) { artikel = it }
            Text(
                S.engQuelle(id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    artikel?.let { NewsSheet(it, onDismiss = { artikel = null }) }
}

// „123 (Vorjahr 150, −18 %)“ ohne Umbruch innerhalb des Wertes
private fun zeile(jetzt: Double, vorjahr: Double?): String {
    val v = vorjahr?.takeIf { it > 0.0 } ?: return Fmt.num(jetzt, 0)
    return Fmt.num(jetzt, 0) + " (" + Fmt.signedPct((jetzt / v - 1.0) * 100.0, 0) + ")"
}

// Linie des Sieben-Tage-Schnitts ueber ein Jahr, von null an; gestrichelt der Schnitt desselben Zeitraums im Vorjahr
@Composable
private fun Verlaufsbild(reihe: List<Pair<Int, Double>>, vorjahr: Double?, modifier: Modifier) {
    val farbe = MaterialTheme.colorScheme.onSurface
    val grau = MaterialTheme.colorScheme.outline
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val hi = maxOf(reihe.maxOf { it.second }, vorjahr ?: 0.0).coerceAtLeast(1.0)
            val t0 = reihe.first().first.toFloat()
            val t1 = reihe.last().first.toFloat().coerceAtLeast(t0 + 1f)
            fun x(t: Int) = (t - t0) / (t1 - t0) * size.width
            fun y(v: Double) = size.height * (1f - (v / hi).toFloat())
            if (vorjahr != null) drawLine(grau, Offset(0f, y(vorjahr)), Offset(size.width, y(vorjahr)), strokeWidth = 1.2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            val p = Path()
            reihe.forEachIndexed { i, (t, v) -> if (i == 0) p.moveTo(x(t), y(v)) else p.lineTo(x(t), y(v)) }
            drawPath(p, farbe, style = Stroke(width = 2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tagText(reihe.first().first), style = MaterialTheme.typography.labelSmall, color = text, maxLines = 1, softWrap = false)
            Text(tagText(reihe.last().first), style = MaterialTheme.typography.labelSmall, color = text, maxLines = 1, softWrap = false)
        }
    }
}
