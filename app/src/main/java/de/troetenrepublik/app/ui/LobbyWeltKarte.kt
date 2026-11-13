package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.LobbyWelt

// Lobbyarbeit in Europa (seit 5.9): je Register eine Zeile mit dem gemeldeten Jahresaufwand; ein Tipp zeigt
// Vollzeitstellen, Themen und den Link zum Eintrag. Nur, wenn die Firma in einem der Register steht. Gibt es auf der
// Seite das Thema „Politik und Behoerden“ (US-Aktien), steht die Lobbyarbeit dort als eine aufklappbare Zeile unter dem
// Lobbying in den USA; sonst als eigener Abschnitt.
@Composable
internal fun LobbyWeltKarte(state: AppState, symbol: String, merker: SeitenMerker? = null) {
    var eintraege by gemerkt<List<LobbyWelt.Eintrag>>(merker, "lobby/eintraege") { emptyList() }
    var geladen by gemerkt(merker, "lobby/geladen") { false }
    LaunchedEffect(symbol) {
        // Schon gelesen (Merker der Seite): beim Zurueckscrollen nichts neu holen
        if (geladen) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val d = state.behoerden.lobbyWelt
            eintraege = d?.firmen?.get(symbol).orEmpty().sortedBy { LobbyWelt.REIHENFOLGE.indexOf(it.l) }
        }
        geladen = true
    }
    if (eintraege.isEmpty()) {
        ModellAusgabe(merker, "lobby", null)
        return
    }
    val uri = appLinks()
    val offen = gemerkt(merker, "lobby/offen") { emptySet<String>() }
    QuellenMelden(*eintraege.map { it.l }.distinct().mapNotNull { l ->
        when (l) {
            "EU" -> QuelleLink("EU Transparency Register", "https://transparency-register.europa.eu")
            "DE" -> QuelleLink("Lobbyregister Bundestag", "https://www.lobbyregister.bundestag.de")
            "FR" -> QuelleLink("HATVP (Licence Ouverte)", "https://www.hatvp.fr")
            else -> null
        }
    }.toTypedArray())
    val beiBehoerden = merker?.zustand<ListenModell?>("modell/beh") { null }?.value != null
    val modell = remember(eintraege, merker, beiBehoerden) { if (beiBehoerden) lobbyZeileModell(eintraege, offen, uri) else lobbyModell(eintraege, offen, uri) }
    ModellAusgabe(merker, "lobby", modell)
}

internal fun LazyListScope.lobbyAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "lobby")
}

private fun betrag(v: Long) = Fmt.mitSymbol(Fmt.compact(v.toDouble()), Derivatives.currencySymbol("EUR"))

// Gemeldeter Jahresaufwand eines Registers: Spanne, Untergrenze oder Strich.
private fun aufwand(e: LobbyWelt.Eintrag): String = when {
    e.von != null && e.bis != null -> LobbyWelt.spanne(betrag(e.von), betrag(e.bis)) ?: S.lwSpanne(betrag(e.von), betrag(e.bis))
    e.von != null -> S.lwAb(betrag(e.von))
    else -> "–"
}

// Einzelheiten eines Registereintrags: Name, Berichtsjahr, Vollzeitstellen, Taetigkeiten, Themen und der Link.
@Composable
private fun RegisterEinzelheiten(e: LobbyWelt.Eintrag, uri: androidx.compose.ui.platform.UriHandler) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val zeilen = listOfNotNull(
        e.n.takeIf { it.isNotBlank() },
        e.j.takeIf { it.contains('/') }?.let { j ->
            fun tag(iso: String) = de.troetenrepublik.app.market.Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
            S.lwJahr(tag(j.substringBefore('/')), tag(j.substringAfter('/')))
        },
        e.fte?.let { S.lwFte(Fmt.num(it, 1)) },
        e.taetigkeiten?.let { S.lwTaetigkeiten(it) },
        e.themen.takeIf { it.isNotEmpty() }?.let { S.lwThemen(it.take(6).joinToString(", ")) },
    )
    zeilen.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp)) }
    if (e.q.isNotBlank()) {
        Text(
            S.lwEintrag, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
            modifier = Modifier.padding(top = 4.dp).textKlick(0.dp) { runCatching { uri.openUri(e.q) } },
        )
    }
}

// Eine Zeile im Thema „Politik und Behoerden“, gebaut wie dessen Zeilen: Bezeichnung, Jahresaufwand des ersten
// Registers, aufgeklappt je Register der Aufwand mit den Einzelheiten.
private fun lobbyZeileModell(eintraege: List<LobbyWelt.Eintrag>, offen: androidx.compose.runtime.MutableState<Set<String>>, uri: androidx.compose.ui.platform.UriHandler): ListenModell =
    ListenModell(
        titel = "", gross = true, kern = null, immerOffen = true,
        zeilen = listOf(ZeilenDaten("europa") {
            val auf = "europa" in offen.value
            Column(Modifier.fillMaxWidth().clickable { offen.value = if (auf) offen.value - "europa" else offen.value + "europa" }.padding(horizontal = 20.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.lwTitel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(aufwand(eintraege.first()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(6.dp))
                    AufklappPfeil(auf)
                }
                if (auf) Column(Modifier.padding(top = 4.dp)) {
                    eintraege.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(S.lwRegister(e.l), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Text(aufwand(e), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                        }
                        RegisterEinzelheiten(e, uri)
                    }
                    HinweisInfo(S.lwHinweis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }),
    )

private fun lobbyModell(eintraege: List<LobbyWelt.Eintrag>, offen: androidx.compose.runtime.MutableState<Set<String>>, uri: androidx.compose.ui.platform.UriHandler): ListenModell {
    return ListenModell(
        titel = S.lwTitel, gross = true, kern = null, immerOffen = true, info = S.lwHinweis,
        zeilen = eintraege.map { e ->
            ZeilenDaten(e.l) {
                val grau = MaterialTheme.colorScheme.onSurfaceVariant
                val auf = e.l in offen.value
                Column(Modifier.fillMaxWidth().clickable { offen.value = if (e.l in offen.value) offen.value - e.l else offen.value + e.l }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    // Register links, Betrag rechts in derselben Zeile; der Betrag bricht nicht um
                    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(S.lwRegister(e.l), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(aufwand(e), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(8.dp))
                        AufklappPfeil(auf)
                    }
                    if (auf) RegisterEinzelheiten(e, uri)
                }
            }
        },
    )
}
