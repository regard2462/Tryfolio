package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.engine.Quellenbuendel
import de.troetenrepublik.app.engine.Quellenbuendel.Ampel
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Quellen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val DATENQUELLEN = "datenquellen"

// Datenquellen (seit 5.92): Ampel je Quelle mit letztem Erfolg, Hinweise und Stand des signierten Quellen-Buendels.
// Die Zaehler fuehrt Quellen.abfang bei jedem Abruf ueber MarketApi.
@Composable
fun DatenquellenScreen(onBack: () -> Unit) {
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberScrollState()
    val links = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val gesundheit by Quellen.gesundheit.collectAsStateWithLifecycle()
    val stand by Quellen.stand.collectAsStateWithLifecycle()
    val gesehen by Quellen.gesehen.collectAsStateWithLifecycle()
    var sucht by remember { mutableStateOf(false) }
    // Zeitangaben einmal je Minute frisch
    var jetzt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); jetzt = System.currentTimeMillis() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }.verticalScroll(liste)) {
        ScreenHeader(S.dqTitel, null, onBack)
        AufklappText(S.dqEinleitung, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

        // Hinweise aus dem Buendel, solange nicht ausgeblendet
        Quellen.aktiv.hinweise.filter { it.id !in gesehen }.forEach { h ->
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            ) {
                if (S.dqHinweisTitel(h).isNotBlank()) Text(S.dqHinweisTitel(h), style = MaterialTheme.typography.titleSmall)
                Text(S.dqHinweisText(h), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 4.dp)) {
                    if (h.link.isNotEmpty()) Text(
                        S.dqHinweisLink, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.clickable { runCatching { links.openUri(h.link) } }.padding(vertical = 6.dp).padding(end = 16.dp),
                    )
                    Text(
                        S.dqAusblenden, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.clickable { Quellen.hinweisGesehen(h.id) }.padding(vertical = 6.dp),
                    )
                }
            }
        }

        SectionHeader(S.dqQuellen)
        // Genutzte Quellen zuerst, die mit Problemen oben; ungenutzte am Ende
        val reihe = remember(gesundheit, jetzt) {
            Quellenbuendel.ARTEN.map { a ->
                val aus = Quellen.aktiv.quelle(a.id).aus
                Triple(a.id, Quellenbuendel.ampel(gesundheit[a.id], aus, jetzt), aus)
            }.sortedBy { (_, ampel, _) -> when (ampel) { Ampel.ROT -> 0; Ampel.GELB -> 1; Ampel.GRUEN -> 2; Ampel.GRAU -> 3 } }
        }
        reihe.forEach { (id, ampel, aus) -> QuellenReihe(id, ampel, aus, gesundheit[id], jetzt) }

        SectionHeader(S.dqStandKopf)
        KeyValueRow(S.dqStandLabel, S.dqStand(stand.version, stand.geladen))
        KeyValueRow(S.dqGesucht, if (stand.geprueft > 0) Fmt.relative(stand.geprueft, jetzt) + " · " + S.dqErgebnis(stand.ergebnis, stand.http) else S.dqNochNicht)
        Column(Modifier.padding(horizontal = 8.dp)) {
            ActionLine(if (sucht) S.dqSucht else S.dqSuchen) {
                if (sucht) return@ActionLine
                sucht = true
                scope.launch {
                    runCatching { Quellen.aktualisieren(MarketApi()) }
                    sucht = false
                }
            }
            ActionLine(S.dqZuruecksetzen) { Quellen.zaehlerZuruecksetzen() }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun QuellenReihe(id: String, ampel: Ampel, aus: Boolean, g: Quellenbuendel.Gesundheit?, jetzt: Long) {
    val farbe: Color = when (ampel) {
        Ampel.GRUEN -> LocalSignal.current.up
        Ampel.GELB -> LocalSignal.current.warn
        Ampel.ROT -> LocalSignal.current.down
        Ampel.GRAU -> LocalSignal.current.rand
    }
    val zeichen = when (ampel) {
        Ampel.GRUEN -> AmpelZeichen.GUT
        Ampel.GELB -> AmpelZeichen.ACHTUNG
        Ampel.ROT -> AmpelZeichen.KRITISCH
        Ampel.GRAU -> ""
    }
    val erfolg = g?.letzterErfolg?.takeIf { it > 0 }?.let { Fmt.relative(it, jetzt) }
    val zeile = when {
        aus -> S.dqAus
        g == null || ampel == Ampel.GRAU -> S.dqNie
        g.inFolge == 0 && g.umweg.isNotEmpty() -> S.dqUmweg(erfolg.orEmpty())
        g.inFolge == 0 -> S.dqAntwortet(erfolg.orEmpty())
        else -> S.dqFehler(S.dqArt(g.art, g.code), erfolg)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AmpelPunkt(zeichen, farbe, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(S.dqName(id), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            PunktText(zeile)
        }
    }
}

// Dringende Hinweise des Quellen-Buendels als Dialog, einmal je Hinweis.
@Composable
fun QuellenHinweisDialog() {
    val gesehen by Quellen.gesehen.collectAsStateWithLifecycle()
    val stand by Quellen.stand.collectAsStateWithLifecycle()
    val links = LocalUriHandler.current
    val h = remember(gesehen, stand) { Quellen.aktiv.hinweise.firstOrNull { it.dringend && it.id !in gesehen } } ?: return
    AlertDialog(
        onDismissRequest = { Quellen.hinweisGesehen(h.id) },
        title = { Text(S.dqHinweisTitel(h)) },
        text = { Text(S.dqHinweisText(h)) },
        confirmButton = { TextButton(onClick = { Quellen.hinweisGesehen(h.id) }) { Text(S.dqVerstanden) } },
        dismissButton = if (h.link.isEmpty()) null else {
            { TextButton(onClick = { runCatching { links.openUri(h.link) }; Quellen.hinweisGesehen(h.id) }) { Text(S.dqHinweisLink) } }
        },
    )
}
