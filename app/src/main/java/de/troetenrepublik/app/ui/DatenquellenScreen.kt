package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    val links = appLinks()
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

        // Hinweise aus dem Buendel, solange nicht ausgeblendet
        Quellen.aktiv.hinweise.filter { it.gilt(de.troetenrepublik.app.market.Aktualisierung.FASSUNG) && it.id !in gesehen }.forEach { h ->
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            ) {
                if (S.dqHinweisTitel(h).isNotBlank()) Text(S.dqHinweisTitel(h), style = MaterialTheme.typography.titleSmall)
                Text(S.dqHinweisText(h), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 4.dp)) {
                    if (h.link.isNotEmpty()) Text(
                        S.dqHinweisLink, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.textKlick(0.dp, 16.dp) { runCatching { links.openUri(h.link) } }.padding(vertical = 6.dp).padding(end = 16.dp),
                    )
                    Text(
                        S.dqAusblenden, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.textKlick(0.dp) { Quellen.hinweisGesehen(h.id) }.padding(vertical = 6.dp),
                    )
                }
            }
        }

        // Bedeutung der Ampel hinter dem Info-Symbol am Kopf
        SectionHeaderMitInfo(S.dqQuellen, S.dqQuellen, S.dqEinleitung)
        // Genutzte Quellen zuerst, die mit Problemen oben; ungenutzte am Ende
        val reihe = remember(gesundheit, jetzt) {
            Quellenbuendel.ARTEN.map { a ->
                val aus = Quellen.aktiv.quelle(a.id).aus
                Triple(a.id, Quellenbuendel.ampel(gesundheit[a.id], aus, jetzt), aus)
            }.sortedBy { (_, ampel, _) -> when (ampel) { Ampel.ROT -> 0; Ampel.GELB -> 1; Ampel.GRUEN -> 2; Ampel.GRAU -> 3 } }
        }
        reihe.forEach { (id, ampel, aus) -> QuellenReihe(id, ampel, aus, gesundheit[id], jetzt) }

        SectionHeader(S.dqDatenKopf)
        val daten by de.troetenrepublik.app.market.Daten.zustand.collectAsStateWithLifecycle()
        val inhalte by Quellen.inhalte.collectAsStateWithLifecycle()
        val datenMarke = de.troetenrepublik.app.engine.Alter.marke(daten.manifestStand)
        KeyValueRow(S.dqStandLabel, datenMarke.ifEmpty { S.dqDatenEingebaut })
        if (daten.geprueft > 0) KeyValueRow(S.dqGesucht, Fmt.relative(daten.geprueft, jetzt), hint = S.dqErgebnis(daten.ergebnis, 0))
        if (daten.abgelaufen) Text(
            S.dqDatenNichtGepflegt(datenMarke.ifEmpty { de.troetenrepublik.app.engine.Alter.marke(de.troetenrepublik.app.market.Aktualisierung.BAUDATUM) }),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (inhalte.isNotEmpty()) {
            SectionHeader(S.dqInhalteKopf)
            inhalte.entries.sortedBy { S.dqInhalt(it.key.removePrefix("risiko-")) }.forEach { (k, v) ->
                KeyValueRow(S.dqInhalt(k.removePrefix("risiko-")), if (v.gueltig > 0) Fmt.relative(v.gueltig, jetzt) else S.dqNie)
            }
        }
        SectionHeader(S.dqStandKopf)
        KeyValueRow(S.dqQuellenStandLabel, S.dqStand(stand.version, stand.geladen))
        KeyValueRow(S.dqQuellenGesucht, if (stand.geprueft > 0) Fmt.relative(stand.geprueft, jetzt) else S.dqNochNicht, hint = if (stand.geprueft > 0) S.dqErgebnis(stand.ergebnis, stand.http) else null)
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
    var offen by remember(id) { mutableStateOf(false) }
    val art = remember(id) { Quellenbuendel.ARTEN.firstOrNull { it.id == id } }
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmpelPunkt(zeichen, farbe, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(S.dqName(id), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            PunktText(zeile)
        }
        if (art != null) AufklappPfeil(offen, Modifier.padding(start = 12.dp))
    }
    if (offen && art != null) QuellenVerzeichnis(art)
}

// Eingeklappter Teil je Quelle: Lizenz in Kurzform, Links zu den Bedingungen, Rueckfall bei Ausfall. Die Daten stehen
// zentral an der Quelldefinition (Quellenbuendel.ARTEN).
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuellenVerzeichnis(a: Quellenbuendel.Art) {
    val links = appLinks()
    val stil = MaterialTheme.typography.bodySmall
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(start = 50.dp, end = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = grau)) { append(S.dqLizenzLabel + ": ") }
            append(S.dqLizenz(a.lizenz))
        }, style = stil)
        if (a.bedingungen.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(S.dqBedingungenLabel + ":", style = stil, color = grau, modifier = Modifier.padding(vertical = 6.dp))
            a.bedingungen.forEach { url ->
                Text(
                    url.substringAfter("://").substringBefore('/').removePrefix("www."), style = stil, color = LocalSignal.current.link,
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.textKlick(0.dp, 14.dp) { runCatching { links.openUri(url) } }.padding(vertical = 6.dp),
                )
            }
        }
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = grau)) { append(S.dqAusfallLabel + ": ") }
            append(S.dqAusfall(a.ersatz, a.ersatzSchluessel))
        }, style = stil)
    }
}

// Alter der App: nach 12 und nach 24 Monaten ohne neuere Fassung ein einmaliger Hinweis (je Stufe und Fassung), auch ohne
// Antwort von GitHub. aktiv: false, wenn der Nutzer den Update-Hinweis ausgeschaltet hat.
@Composable
fun AppAlterDialog(aktiv: Boolean) {
    val gesehen by Quellen.gesehen.collectAsStateWithLifecycle()
    val links = appLinks()
    val monate = remember { de.troetenrepublik.app.market.Aktualisierung.alterMonate(de.troetenrepublik.app.engine.Uhr.jetzt()) }
    val stufe = de.troetenrepublik.app.market.Aktualisierung.alterStufe(monate)
    if (!aktiv || stufe == 0) return
    val id = "app-alter-$stufe-${de.troetenrepublik.app.market.Aktualisierung.FASSUNG}"
    if (id in gesehen) return
    AlertDialog(
        onDismissRequest = { Quellen.hinweisGesehen(id) },
        title = { Text(S.altTitel(monate)) },
        text = { Text(S.altText) },
        confirmButton = { TextButton(onClick = { Quellen.hinweisGesehen(id) }) { Text(S.dqVerstanden) } },
        dismissButton = {
            TextButton(onClick = { runCatching { links.openUri(Quellen.link("releases", "https://github.com/regard2462/Tryfolio/releases")) }; Quellen.hinweisGesehen(id) }) { Text(S.dqHinweisLink) }
        },
    )
}

// Dringende Hinweise des Quellen-Buendels als Dialog, einmal je Hinweis.
@Composable
fun QuellenHinweisDialog() {
    val gesehen by Quellen.gesehen.collectAsStateWithLifecycle()
    val stand by Quellen.stand.collectAsStateWithLifecycle()
    val links = appLinks()
    val h = remember(gesehen, stand) { Quellen.aktiv.hinweise.firstOrNull { it.dringend && it.gilt(de.troetenrepublik.app.market.Aktualisierung.FASSUNG) && it.id !in gesehen } } ?: return
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
