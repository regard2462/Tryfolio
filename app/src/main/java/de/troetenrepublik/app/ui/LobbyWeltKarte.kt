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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.LobbyWelt

// Lobbyarbeit in Europa (seit 5.9): je Register eine Zeile mit dem gemeldeten Jahresaufwand; ein Tipp zeigt
// Vollzeitstellen, Themen und den Link zum Eintrag. Nur, wenn die Firma in einem der Register steht.
@Composable
internal fun LobbyWeltKarte(state: AppState, symbol: String) {
    var eintraege by remember(symbol) { mutableStateOf<List<LobbyWelt.Eintrag>>(emptyList()) }
    var stand by remember { mutableStateOf("") }
    LaunchedEffect(symbol) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val d = state.behoerden.lobbyWelt
            eintraege = d?.firmen?.get(symbol).orEmpty().sortedBy { LobbyWelt.REIHENFOLGE.indexOf(it.l) }
            stand = d?.stand.orEmpty()
        }
    }
    if (eintraege.isEmpty()) return
    val uri = LocalUriHandler.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var offen by remember(symbol) { mutableStateOf<Set<String>>(emptySet()) }
    fun betrag(v: Long) = Fmt.mitSymbol(Fmt.compact(v.toDouble()), Derivatives.currencySymbol("EUR"))
    Column {
        SectionHeader(S.lwTitel)
        Text(S.lwHinweis, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        eintraege.forEach { e ->
            val auf = e.l in offen
            Column(Modifier.fillMaxWidth().clickable { offen = if (auf) offen - e.l else offen + e.l }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.lwRegister(e.l), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            e.von != null && e.bis != null -> S.lwSpanne(betrag(e.von), betrag(e.bis))
                            e.von != null -> S.lwAb(betrag(e.von))
                            else -> "–"
                        },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    )
                }
                if (auf) {
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
                            modifier = Modifier.padding(top = 4.dp).clickable { runCatching { uri.openUri(e.q) } },
                        )
                    }
                }
            }
        }
        Text(S.lwQuelle(eintraege.map { it.l }.distinct()), style = MaterialTheme.typography.labelSmall, color = grau,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }
}
