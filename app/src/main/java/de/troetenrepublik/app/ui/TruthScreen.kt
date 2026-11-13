package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.PotusReaktion
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.TruthArchiv
import de.troetenrepublik.app.plattform.LogoBild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Truth-Social-Beitraege aus dem Archiv Trump's Truth (trumpstruth.org, Defending Democracy Together), ohne Anmeldung
// und ohne Abruf bei Truth Social. Die neuesten 100 Beitraege, weitere auf Anfrage. Ein Tipp oeffnet den Beitrag im
// Archiv. Bilder kommen aus dem Speicher des Archivs, Videos nur als Hinweis.
@Composable
fun TruthScreen(state: AppState, onBack: () -> Unit) {
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val uri = appLinks()
    val scope = rememberCoroutineScope()
    var beitraege by remember { mutableStateOf<List<TruthArchiv.Beitrag>?>(null) }
    var fehler by remember { mutableStateOf(false) }
    var laedtMehr by remember { mutableStateOf(false) }
    var ende by remember { mutableStateOf(false) }
    var reaktion by remember { mutableStateOf<Map<String, List<PotusReaktion.Zeile>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        val l = state.copy?.truth()
        if (l == null) fehler = true else beitraege = l
    }
    // Reaktion von SPY und Sektor-ETFs nach den Beitraegen der letzten 30 Tage (0.76), sobald die Beitraege da sind
    LaunchedEffect(beitraege?.size) {
        val l = beitraege ?: return@LaunchedEffect
        reaktion = runCatching { potusReaktion(state, l) }.getOrDefault(emptyMap())
    }
    fun mehrLaden(l: List<TruthArchiv.Beitrag>) {
        if (laedtMehr || l.isEmpty()) return
        laedtMehr = true
        scope.launch {
            // Bis zum Tag des aeltesten Beitrags, bei mehr als 100 Beitraegen an einem Tag ab dem Vortag
            val tag = Math.floorDiv(l.minOf { it.zeit }, 86_400_000L)
            var neu = l
            for (bis in listOf(tag, tag - 1)) {
                val mehr = state.copy?.truth(java.time.LocalDate.ofEpochDay(bis).toString()) ?: break
                neu = TruthArchiv.zusammen(l, mehr)
                if (neu.size > l.size) break
            }
            if (neu.size == l.size) ende = true
            beitraege = neu
            laedtMehr = false
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.truthTitle, null, onBack) }
        val l = beitraege
        when {
            l == null && !fehler -> item { CopyLade() }
            l == null || l.isEmpty() -> item { EmptyHint(S.truthNone) }
            else -> {
                items(l, key = { it.link }) { b -> TruthZeile(b, reaktion[b.link]) { runCatching { uri.openUri(b.link) } } }
                if (!ende) item { ActionLine(if (laedtMehr) S.pvLoading else S.truthMore) { mehrLaden(l) } }
            }
        }
        item { Fussnote(S.truthHinweis) }
    }
}

@Composable
private fun TruthZeile(b: TruthArchiv.Beitrag, reaktion: List<PotusReaktion.Zeile>?, onClick: () -> Unit) {
    val leise = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        val zusatz = listOfNotNull(
            when (b.art) {
                TruthArchiv.Art.ZITAT -> S.truthQuote
                TruthArchiv.Art.WEITERGETEILT -> S.truthRepost
                TruthArchiv.Art.EIGEN -> null
            },
            if (b.geloescht) S.truthRemoved else null,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (b.zeit > 0) Fmt.dateTime(b.zeit) else "") + zusatz.joinToString("") { " · $it" },
                style = MaterialTheme.typography.bodySmall, color = if (b.geloescht) MaterialTheme.colorScheme.error else leise,
            )
        }
        if (b.text.isNotBlank()) {
            Text(b.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        b.bilder.firstOrNull()?.let { url ->
            if (Vorfuehrung.aktiv) Box(
                Modifier.padding(top = 8.dp).fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) else LogoBild(
                url = url, contentDescription = null,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentScale = ContentScale.Crop, onSuccess = {}, onError = {},
            )
        }
        val medien = listOfNotNull(
            if (b.bilder.size > 1) S.truthImages(b.bilder.size) else null,
            if (b.videos > 0) S.truthVideo else null,
        )
        if (medien.isNotEmpty()) {
            PunktText(medien.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = leise, modifier = Modifier.padding(top = 6.dp))
        }
        if (reaktion != null) ReaktionsLeiste(reaktion)
    }
}

// Kerzen von SPY und den passenden Sektor-ETFs ab dem aeltesten Beitrag (hoechstens 30 Tage, Kerzen unter einer Stunde),
// dann je Beitrag die Reaktion nach 1 und 3 Stunden. Beitraege ohne Wert fehlen im Ergebnis.
private suspend fun potusReaktion(state: AppState, beitraege: List<TruthArchiv.Beitrag>): Map<String, List<PotusReaktion.Zeile>> = withContext(Dispatchers.Default) {
    val jetzt = System.currentTimeMillis()
    val tag = 86_400_000L
    val relevant = beitraege.filter { it.text.isNotBlank() && it.zeit >= jetzt - 30 * tag && it.zeit <= jetzt - 3_600_000L }
    if (relevant.isEmpty()) return@withContext emptyMap()
    val von = relevant.minOf { it.zeit } - 3_600_000L
    val symbole = (listOf(PotusReaktion.MARKT) + relevant.flatMap { PotusReaktion.etfs(it.text) }).distinct()
    val kerzen = symbole.associateWith { sym -> runCatching { state.market.zeitraum(sym, von, jetzt).candles.sortedBy { it.t } }.getOrNull() }
    relevant.mapNotNull { b -> PotusReaktion.zeilen(b.text, b.zeit) { kerzen[it] }?.let { b.link to it } }.toMap()
}

// Kleine Tabelle unter dem Beitrag: je Symbol die Aenderung nach 1 und 3 Stunden, mit einem Balken (voll bei 1 %).
@Composable
private fun ReaktionsLeiste(zeilen: List<PotusReaktion.Zeile>) {
    val leise = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current
    Column(Modifier.padding(top = 8.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(48.dp))
            PotusReaktion.STUNDEN.forEach { h ->
                Text(S.poStunden(h), style = MaterialTheme.typography.labelSmall, color = leise, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            }
        }
        zeilen.forEach { z ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                Text(z.symbol, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp), maxLines = 1, softWrap = false)
                z.werte.forEach { v ->
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        if (v == null) {
                            Text("–", style = MaterialTheme.typography.bodySmall, color = leise)
                        } else {
                            val farbe = signal.forChange(v)
                            Text(Fmt.signedPct(v * 100, 2), style = MaterialTheme.typography.bodySmall, color = farbe, maxLines = 1, softWrap = false)
                            Box(Modifier.padding(top = 2.dp).fillMaxWidth((kotlin.math.abs(v) / 0.01).toFloat().coerceIn(0.04f, 1f)).height(3.dp).background(farbe))
                        }
                    }
                }
            }
        }
    }
}
