package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Investorenbriefe
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Handelnde
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.SecInsider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Was Investoren schreiben (5.92): Auszuege aus Briefen von Fondsmanagern zu einer Aktie mit Richtung und Einordnung
// des Eigeninteresses (Position laut 13F, Grossaktionaersmeldung oder eigener Angabe, passt die Aussage dazu, Art des
// Absenders, Werbung). Oben eine Zusammenfassung. Ohne Fundstelle erscheint bei US-Aktien nur der Link zur Suche auf
// hfbestideas.com und der Hinweis auf das SEC-Feld, sonst nichts. Antippen von ‚Mehr aus dem Brief‘ zeigt die
// Textstelle aus dem gespeicherten Brief.

private fun quartalAus(stichtag: String): String =
    runCatching { java.time.LocalDate.parse(stichtag.take(10)) }.getOrNull()?.let { "Q${(it.monthValue - 1) / 3 + 1} ${it.year}" } ?: stichtag

private fun f13(w: Investoren.Wandel?): Investorenbriefe.F13 = when (w) {
    Investoren.Wandel.NEU -> Investorenbriefe.F13.NEU
    Investoren.Wandel.ERHOEHT -> Investorenbriefe.F13.ERHOEHT
    Investoren.Wandel.VERRINGERT -> Investorenbriefe.F13.VERRINGERT
    Investoren.Wandel.VERKAUFT -> Investorenbriefe.F13.VERKAUFT
    else -> Investorenbriefe.F13.HAELT
}

// Domain fuer die Quellenzeile: „givernycapital.com“.
private fun domain(link: String): String =
    Regex("^https?://(?:www\\.|info\\.)?([^/:?#]+)").find(link)?.groupValues?.get(1) ?: link

private class Zeile(val f: Investorenbriefe.Fundstelle, val e: Investorenbriefe.Einordnung, val stand13f: String)

@Composable
internal fun InvestorenBriefeKarte(state: AppState, symbol: String, name: String, usWert: Boolean, onOpenRoute: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val secFormen by state.secFormen.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val mitSec = usWert && SecInsider.kennung(settings.secKontakt) != null
    var eigene by remember(symbol, name) { mutableStateOf<List<Investorenbriefe.Fundstelle>?>(null) }
    var briefe by remember(symbol, name) { mutableStateOf<List<Investorenbriefe.Brief>>(emptyList()) }
    var offen by remember(symbol, name) { mutableStateOf<Zeile?>(null) }
    var sec by remember(symbol, name) { mutableStateOf<List<Investorenbriefe.Fundstelle>>(emptyList()) }
    var alle by remember(symbol) { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }

    LaunchedEffect(symbol, name) {
        val suche = Investorenbriefe.Suche(name, symbol)
        suspend fun suchen(briefe: List<Investorenbriefe.Brief>) = withContext(Dispatchers.Default) {
            briefe.mapNotNull { b -> runCatching { Investorenbriefe.fundstelle(b, suche, Investorenbriefe.absender(b.absender)?.name ?: b.absender) }.getOrNull() }
        }
        // Erst der Stand auf dem Geraet, dann faellige Briefe nachholen
        val vorhanden = state.investorenbriefe(netz = false)
        if (vorhanden.isNotEmpty()) {
            briefe = vorhanden
            eigene = suchen(vorhanden)
        }
        val geladen = state.investorenbriefe()
        briefe = geladen
        eigene = suchen(geladen)
    }
    LaunchedEffect(symbol, name, mitSec) {
        if (mitSec) sec = state.investorenbriefeSec(symbol, name)
    }

    val zeilen = remember(eigene, sec, secFormen[symbol]) {
        val heute = java.time.LocalDate.now()
        val passt = HandelndeLader.passt(symbol)
        val halter = secFormen[symbol]?.halter.orEmpty()
        val basis = symbol.uppercase()
        Investorenbriefe.aktuelle(eigene.orEmpty() + sec, heute).map { f ->
            val a = Investorenbriefe.absender(f.absender)
            val inv = a?.investor?.let { id -> state.investoren.firstOrNull { it.id == id } }
            val eintrag = inv?.let { Handelnde.investoren(listOf(it), passt).firstOrNull() }
            val f13 = when {
                inv == null -> null
                eintrag == null -> Investorenbriefe.F13.KEINE
                else -> f13(eintrag.wandel)
            }
            // Grossaktionaer: der erste Teil des Absendernamens steht in einer Meldung nach 13D/13G
            val kern = (a?.name ?: f.name).substringBefore(" (").substringBefore(",").split(' ').first().lowercase()
            val schedule13 = kern.length >= 4 && halter.any { it.name.lowercase().contains(kern) }
            val firma = a != null && (basis in a.kuerzel || basis.substringBefore('.') in a.kuerzel)
            Zeile(f, Investorenbriefe.einordnung(f, a?.art ?: Investorenbriefe.Art.FONDSGESELLSCHAFT, f13, schedule13, firma), quartalAus(inv?.aktuell?.stichtag.orEmpty()))
        }
    }

    if (eigene == null) return
    if (zeilen.isEmpty() && !usWert) return

    Column {
        // Kopf wie SectionHeader, rechts das Info-Symbol
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.ibTitel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { info = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(20.dp))
            }
        }
        if (zeilen.isEmpty()) {
            Text(S.ibLeer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        } else {
            Text(S.ibKopf, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            val z = Investorenbriefe.zusammenfassung(zeilen.map { it.f to it.e })
            Text(
                listOfNotNull(
                    S.ibZusammenfassung(z.absender, z.positiv, z.negativ, z.neutral),
                    if (z.absender > 1 && z.eigeneSache > 0) S.ibEigeneSache(z.eigeneSache) else null,
                ).joinToString(" "),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 4.dp),
            )
            val deutsch = de.troetenrepublik.app.i18n.I18n.lang == de.troetenrepublik.app.data.Lang.DE
            (if (alle) zeilen else zeilen.take(3)).forEachIndexed { i, zl ->
                val f = zl.f
                val e = zl.e
                if (i > 0) Trennlinie()
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NamenText(f.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            S.ibRichtung(f.richtung), style = MaterialTheme.typography.bodySmall,
                            color = when { f.richtung > 0 -> signal.up; f.richtung < 0 -> signal.down; else -> grau },
                        )
                    }
                    PunktText(
                        listOfNotNull(f.quartal.ifBlank { null }, if (f.sec) S.ibSecBericht else e.art?.let { S.ibArt(it.name) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = grau,
                    )
                    MitBlende(f.gekuerzt, Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                        Text(if (deutsch) "„${f.auszug}“" else "“${f.auszug}”", style = MaterialTheme.typography.bodyMedium)
                    }
                    // Einordnung: Position, Passung, Werbung
                    e.belege.forEach { b ->
                        Text(S.ibBeleg(b.name, zl.stand13f), style = MaterialTheme.typography.bodySmall, color = grau)
                    }
                    e.passt?.let { p ->
                        Text(S.ibPasst(p.name), style = MaterialTheme.typography.bodySmall, color = if (e.inEigenerSache) signal.warn else grau)
                    }
                    if (e.werbung) Text(S.ibWerbung, style = MaterialTheme.typography.bodySmall, color = signal.warn)
                }
                // Die ganze Textstelle aus dem gespeicherten Brief, im Blatt
                if (!f.sec) {
                    Text(
                        S.ibMehrBrief, style = MaterialTheme.typography.bodySmall, color = signal.link,
                        modifier = Modifier.clickable { offen = zl }.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 20.dp),
                    )
                }
                QuellenZeile(S.ibQuelle(if (f.sec) "SEC" else domain(f.link))) { runCatching { uri.openUri(f.link) } }
            }
            if (zeilen.size > 3) ListenPfeil(alle) { alle = !alle }
        }
        // Suche auf einer Sammelseite nur als Link, dazu der Hinweis auf das SEC-Feld
        if (usWert) {
            QuellenZeile(S.ibSuchen) { runCatching { uri.openUri("https://www.hfbestideas.com/stock/" + symbol.uppercase().replace('-', '.')) } }
            if (SecInsider.kennung(settings.secKontakt) == null) {
                Text(
                    S.ibSecHinweis, style = MaterialTheme.typography.bodySmall, color = signal.link,
                    modifier = Modifier.clickable { onOpenRoute("zugaenge") }.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text(S.ibTitel) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    StichpunktText(S.ibInfoKurz, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text(S.ok) } },
        )
    }

    // Blatt mit der ganzen Textstelle zur Aktie aus dem gespeicherten Brief; ohne gespeicherten Text bleibt der Auszug
    offen?.let { zl ->
        val text = briefe.firstOrNull { it.absender == zl.f.absender && it.link == zl.f.link }?.text
        val stelle = text?.let { Investorenbriefe.umfeldStueck(it, zl.f.auszug) } ?: Investorenbriefe.Stueck(zl.f.auszug, zl.f.gekuerzt)
        AlertDialog(
            onDismissRequest = { offen = null },
            title = { Text(zl.f.name) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    MitBlende(stelle.gekuerzt, flaeche = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(stelle.text, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(8.dp))
                    QuellenZeile(S.ibQuelle(domain(zl.f.link))) { runCatching { uri.openUri(zl.f.link) } }
                }
            },
            confirmButton = { TextButton(onClick = { offen = null }) { Text(S.ok) } },
        )
    }
}
