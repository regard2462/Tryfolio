package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.ListenKern
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik

// Zulassungen und Rueckrufe der FDA (seit 5.9) bei Pharma-, Biotech- und Medizintechnik-Aktien: die letzten zwoelf
// Monate, Zulassungen mit Marke, Wirkstoff und Art, Rueckrufe mit Klasse, Produkt und Grund.
@Composable
internal fun FdaKarte(state: AppState, symbol: String, firma: String, merker: SeitenMerker? = null) {
    val alle by state.fda.collectAsStateWithLifecycle()
    LaunchedEffect(symbol, firma) { runCatching { state.fdaLaden(symbol, firma) } }
    val e = alle[symbol]
    // Kopf, Zulassungen, Rueckrufe und Fuss als einzelne Modelle: auf der Seite je Zeile ein Eintrag der LazyColumn
    val modelle = remember(e, symbol, merker) { e?.let { fdaModelle(merker, it) } }
    ModellAusgabe(merker, "fda-kopf", modelle?.get(0))
    ModellAusgabe(merker, "fda-zul", modelle?.get(1))
    ModellAusgabe(merker, "fda-rueck", modelle?.get(2))
    ModellAusgabe(merker, "fda-fuss", modelle?.get(3))
    // Quelle am Seitenende statt im Inhalt
    if (e != null && (e.zulassungen.isNotEmpty() || e.rueckrufe.isNotEmpty())) QuellenMelden(QuelleLink("openFDA (api.fda.gov)", "https://open.fda.gov"))
}

internal fun LazyListScope.fdaAbschnitte(merker: SeitenMerker) {
    listenAbschnitt(merker, "fda-kopf")
    listenAbschnitt(merker, "fda-zul")
    listenAbschnitt(merker, "fda-rueck")
    listenAbschnitt(merker, "fda-fuss")
}

// Art oder Klasse links (bricht um), der Tag rechts in derselben Zeile
@Composable
private fun ArtUndTag(art: String, tag: String, farbe: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(art, style = MaterialTheme.typography.bodySmall, color = farbe, modifier = Modifier.weight(1f))
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        Text(tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

private fun fdaTag(iso: String) = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

private fun fdaModelle(merker: SeitenMerker?, e: de.troetenrepublik.app.market.Fda.Ergebnis): List<ListenModell?> {
    // Ohne Funde keine Karte
    if (e.zulassungen.isEmpty() && e.rueckrufe.isEmpty()) return listOf(null, null, null, null)
    val kopf = ListenModell(
        titel = "", gross = true, kern = null, zeilen = emptyList(), immerOffen = true,
        kopf = { SectionHeader(S.fdaTitel) },
    )
    val zul = if (e.zulassungen.isEmpty()) null else {
        ListenModell(
            titel = S.fdaZulassungen, gross = false, kern = KernZeile(listOf(KernTeil(e.zulassungen.size.toString()))),
            info = S.fdaHinweis,
            zeilen = e.zulassungen.mapIndexed { i, z ->
                ZeilenDaten("z$i") {
                    if (i > 0) Trennlinie()
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        // Marke und Wirkstoff je in einer eigenen Zeile, nichts fällt weg
                        val wirkstoff = z.wirkstoff.lowercase().replaceFirstChar { it.uppercase() }
                        Text(z.marke, style = MaterialTheme.typography.bodyMedium)
                        if (wirkstoff.isNotBlank() && !wirkstoff.equals(z.marke, ignoreCase = true)) {
                            Text(wirkstoff, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ArtUndTag(S.fdaArt(z.art.name, z.vorrang), fdaTag(z.tag), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
    val rueck = if (e.rueckrufe.isEmpty()) null else {
        val rl = e.rueckrufe.sortedWith(compareBy<de.troetenrepublik.app.market.Fda.Rueckruf> { if (it.klasse == 0) 9 else it.klasse }.thenByDescending { it.tag })
        ListenModell(
            titel = S.fdaRueckrufe, gross = false, kern = KernZeile(listOf(KernTeil(e.rueckrufe.size.toString()))),
            info = if (zul == null) S.fdaHinweis else null,
            zeilen = rl.mapIndexed { i, r ->
                ZeilenDaten("r$i") {
                    if (i > 0) Trennlinie()
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        AufklappText(r.produkt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        AufklappText(r.grund, style = MaterialTheme.typography.bodySmall)
                        ArtUndTag(S.fdaKlasse(r.klasse, r.geraet, r.laufend), fdaTag(r.tag), if (r.klasse == 1) LocalSignal.current.warn else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
    return listOf(kopf, zul, rueck, null)
}
