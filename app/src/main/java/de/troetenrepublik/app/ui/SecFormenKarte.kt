package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.ListenKern
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecFormen
import kotlinx.coroutines.isActive

/// Auf der Seite einer US-Aktie (seit 5.91): angekuendigte Verkaeufe nach Form 144 und Grossaktionaere nach
// Schedule 13D/13G. Geholt wird nur mit Kontaktadresse unter Zugaenge, wie bei Form 4. Die Karte laedt und rechnet,
// die Zeilen kommen als Eintraege der Seite (insiderAbschnitte, grossaktionaerAbschnitte).
@Composable
// mitHaltern: mit den Grossaktionaeren (auf der Seite stehen sie unter „Grosse Halter“, in den Blaettern entfallen sie).
internal fun SecFormenKarte(state: AppState, symbol: String, merker: SeitenMerker? = null, mitHaltern: Boolean = true) {
    val alle by state.secFormen.collectAsStateWithLifecycle()
    var geladen by gemerkt<Boolean>(merker, "secformen/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        // Einmal je Seite: beim Zurueckscrollen nicht erneut nachfragen
        if (geladen) return@LaunchedEffect
        try { runCatching { state.secFormenLaden(symbol) } } finally { if (isActive) geladen = true }
    }
    val s = alle[symbol]
    // Betraege in der Anzeigewaehrung wie bei den Insidermeldungen
    val geld = rememberInsiderGeld(state)
    val modelle = remember(s, geld) { if (s == null) null to null else secFormenModelle(s, geld) }
    ModellAusgabe(merker, "f144", modelle?.first)
    ModellAusgabe(merker, "f13", modelle?.second?.takeIf { mitHaltern })
}

private fun secFormenModelle(s: SecFormen.Stand, geld: InsiderGeld): Pair<ListenModell?, ListenModell?> {
    val ab = java.time.LocalDate.now().minusDays(90).toString()
    val verkaeufe = SecFormen.angekuendigt(s, ab)
    val halter = SecFormen.grossaktionaere(s)
    fun dollar(v: Double) = geld.text(v, "USD").first
    fun tag(iso: String) = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
    val m144 = if (verkaeufe.isEmpty()) null else {
        val summe = verkaeufe.sumOf { it.wert }
        ListenModell(
            titel = S.f144Titel,
            gross = true,
            kern = KernZeile(listOf(KernTeil(if (summe > 0) "${verkaeufe.size} · ${dollar(summe)}" else "${verkaeufe.size}", if (summe > 0) -1.0 else 0.0), KernTeil("Form 144"))),
            zeilen = verkaeufe.mapIndexed { i, v ->
                ZeilenDaten("$i") {
                    InsiderZeile(
                        name = v.p,
                        angaben = listOfNotNull(v.rolle.ifBlank { null }, S.f144Stueck(Fmt.compact(v.n))),
                        kauf = false,
                        verkauf = v.wert > 0,
                        betrag = if (v.wert > 0) dollar(v.wert) else "–",
                        datum = S.f144Geplant(tag(v.tag.ifBlank { v.gemeldet })),
                    )
                }
            },
            info = S.f144Hinweis,
        )
    }
    val m13 = if (halter.isEmpty()) null else ListenModell(
        titel = S.f13Titel,
        gross = true,
        kern = KernZeile(listOf(KernTeil(S.kernGross(halter.size, Fmt.pct(ListenKern.groesster(halter.map { it.anteil }) ?: 0.0, 1))))),
        zeilen = halter.mapIndexed { i, h -> ZeilenDaten("$i") { GrossaktionaerZeile(h, ::tag) } },
        info = S.f13Hinweis,
    )
    return m144 to m13
}

@Composable
private fun GrossaktionaerZeile(h: SecFormen.Halter, tag: (String) -> String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            NamenText(h.name, style = MaterialTheme.typography.titleSmall)
            PunktText(
                listOfNotNull(if (h.aktiv) S.f13Aktiv else S.f13Passiv, tag(h.tag.ifBlank { h.gemeldet }).ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.pct(h.anteil, 1), style = MaterialTheme.typography.titleSmall)
            if (h.n > 0) Text(S.f144Stueck(Fmt.compact(h.n)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
