package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.LaenderZusatz
import de.troetenrepublik.app.engine.LaenderZusatz.Einheit
import de.troetenrepublik.app.engine.LaenderZusatz.Gruppe
import de.troetenrepublik.app.engine.Weltbank
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// Alles, was der Abschnitt zu einem Land zeigt; ohne Netz oder ohne Daten sind Teile leer.
private class MehrDaten(
    val steck: LaenderZusatz.Steckbrief?,
    val reihen: Map<String, Weltbank.Reihe?>,
    val handel: LaenderZusatz.Handel?,
    val karte: GlobusKarte?,
    val feiertage: List<LaenderZusatz.Feiertag>,
    val meldungen: List<LaenderZusatz.Meldung>,
)

private suspend fun laden(state: AppState, land: GlobusLand, deutsch: Boolean): MehrDaten = coroutineScope {
    val feiertage = async { ohneAbbruch { state.globusFeiertage(land.a2) }.getOrDefault(emptyList()) }
    val meldungen = async { ohneAbbruch { state.globusMeldungen(land.a2, land.name(deutsch)) }.getOrDefault(emptyList()) }
    val steck = ohneAbbruch { state.globusSteckbrief() }.getOrNull()?.get(land.a2)
    val handel = ohneAbbruch { state.globusHandel() }.getOrNull()?.get(land.a3)
    val karte = ohneAbbruch { state.globusKarte() }.getOrNull()
    // Die Weltbank-Abrufe laufen gleichzeitig (hoechstens sechs), jeder ist eine Woche gespeichert.
    val reihen = ohneAbbruch { state.weltbankViele(LaenderZusatz.KENNUNGEN) }.getOrDefault(emptyMap())
    MehrDaten(steck, reihen, handel, karte, feiertage.await(), meldungen.await())
}

private fun wertText(z: LaenderZusatz.Zeile): String {
    val k = z.kennzahl
    val v = z.wert
    val text = when (k.einheit) {
        Einheit.PROZENT -> if (k.vorzeichen && v < 0) Fmt.signedPct(v, 1) else Fmt.pct(v, 1)
        Einheit.JAHRE -> S.globusMehrJahre(Fmt.num(v, 1))
        Einheit.ZAHL -> Fmt.num(v, 2)
        Einheit.TONNEN -> Fmt.num(v, 1) + " t"
    }
    return if (z.mitJahr) "$text (${z.jahr})" else text
}

@Composable
private fun Ueberschrift(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp))
}

// Abschnitt Gesellschaft, Staat, Energie, Handel, Feiertage und Schlagzeilen im Laenderblatt.
@Composable
internal fun LaenderMehr(state: AppState, land: GlobusLand) {
    val deutsch = I18n.locale.language == "de"
    var daten by remember(land) { mutableStateOf<MehrDaten?>(null) }
    var fertig by remember(land) { mutableStateOf(false) }
    var info by remember(land) { mutableStateOf<Pair<String, String>?>(null) }
    var artikel by remember(land) { mutableStateOf<MarketApi.NewsItem?>(null) }
    val jahr = remember { java.time.LocalDate.now().year }

    LaunchedEffect(land) {
        daten = ohneAbbruch { laden(state, land, deutsch) }.getOrNull()
        fertig = true
    }
    if (!fertig) {
        Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        return
    }
    val d = daten ?: return
    val jetzt = System.currentTimeMillis()
    val steck = d.steck
    fun zeilen(g: Gruppe) = LaenderZusatz.zeilen(g, land.a3, d.reihen, steck?.medianalter, jahr)

    // Gesellschaft samt Steckbrief
    val gesellschaft = zeilen(Gruppe.GESELLSCHAFT)
    val versaetze = steck?.let { LaenderZusatz.versaetze(it.zonen, jetzt) }.orEmpty()
    val flaeche = steck?.flaeche?.takeIf { it > 0 }
    val sprachen = steck?.sprachen.orEmpty()
    if (gesellschaft.isNotEmpty() || flaeche != null || sprachen.isNotEmpty() || versaetze.isNotEmpty()) {
        Ueberschrift(S.globusMehrGesellschaft)
        if (flaeche != null) KeyValueRow(S.globusMehrFlaeche, Fmt.num(flaeche, if (flaeche < 100) 1 else 0) + " km²", onClick = { info = S.globusMehrFlaeche to S.globusMehrFlaecheInfo })
        if (sprachen.isNotEmpty() && steck != null) {
            val namen = sprachen.joinToString(", ") { if (deutsch) it.second else it.first } + if (steck.weitere > 0) " " + S.globusMehrWeitere(steck.weitere) else ""
            val titel = S.globusMehrSprachen(sprachen.size + steck.weitere)
            KeyValueRow(titel, namen, onClick = { info = titel to S.globusMehrSprachenInfo })
        }
        if (versaetze.isNotEmpty()) {
            val text = if (versaetze.size <= 3) versaetze.joinToString(", ") { LaenderZusatz.utcText(it) }
            else LaenderZusatz.utcText(versaetze.first()) + " " + S.globusMehrBis + " " + LaenderZusatz.utcText(versaetze.last())
            val titel = S.globusMehrZeitzone(versaetze.size)
            KeyValueRow(titel, text, onClick = { info = titel to S.globusMehrZeitzoneInfo })
        }
        for (z in gesellschaft) {
            val titel = S.globusMehrLabel(z.kennzahl)
            KeyValueRow(titel, wertText(z), onClick = { info = titel to S.globusMehrInfo(z.kennzahl) })
        }
    }

    for (g in listOf(Gruppe.STAAT, Gruppe.ENERGIE)) {
        val zs = zeilen(g)
        if (zs.isEmpty()) continue
        Ueberschrift(if (g == Gruppe.STAAT) S.globusMehrStaat else S.globusMehrEnergie)
        for (z in zs) {
            val titel = S.globusMehrLabel(z.kennzahl)
            KeyValueRow(titel, wertText(z), onClick = { info = titel to S.globusMehrInfo(z.kennzahl) })
        }
    }

    // Handel: Exportgueter und Abnehmer aus der gebuendelten Datei
    val h = d.handel
    if (h != null) {
        val partner = h.partner.mapNotNull { (a3, p) -> d.karte?.nachA3(a3)?.let { it.name(deutsch) to p } }
        if (h.gueter.isNotEmpty() || partner.isNotEmpty()) {
            Ueberschrift(S.globusMehrHandel)
            val spiegel = if (h.spiegel) " " + S.globusMehrSpiegel else ""
            if (h.wert > 0) {
                val titel = S.globusMehrExporte(h.jahr)
                KeyValueRow(titel, Fmt.dollarKompakt(h.wert), onClick = { info = titel to S.globusMehrExporteInfo })
            }
            if (h.gueter.isNotEmpty()) {
                Zwischenkopf(S.globusMehrGueter)
                for ((g, p) in h.gueter) {
                    val titel = S.globusMehrGruppe(g)
                    KeyValueRow(titel, Fmt.pct(p / 10.0, 1), onClick = { info = titel to S.globusMehrGruppeInfo(g, h.jahr) + spiegel })
                }
            }
            if (partner.isNotEmpty()) {
                Zwischenkopf(S.globusMehrPartner)
                for ((name, p) in partner) KeyValueRow(name, Fmt.pct(p / 10.0, 1), onClick = { info = name to S.globusMehrPartnerInfo(name, h.jahr) + spiegel })
            }
        }
    }

    // Feiertage
    if (d.feiertage.isNotEmpty()) {
        Ueberschrift(S.globusMehrFeiertage)
        for (f in d.feiertage) {
            val titel = LaenderZusatz.feiertagName(f, land.a2, deutsch)
            val ts = f.datum.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            KeyValueRow(titel, Fmt.date(ts), onClick = { info = titel to S.globusMehrFeiertagInfo })
        }
    }

    // Schlagzeilen: antippen oeffnet den Artikel wie in den News
    if (d.meldungen.isNotEmpty()) {
        Ueberschrift(S.globusMehrSchlagzeilen)
        d.meldungen.forEachIndexed { i, m ->
            if (i > 0) Trennlinie()
            Column(Modifier.fillMaxWidth().clickable { artikel = MarketApi.NewsItem(title = m.titel, link = m.link, published = m.zeit, source = m.quelle) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(m.titel, style = MaterialTheme.typography.titleSmall.schlagzeile(), lineHeight = 22.sp)
                PunktText(
                    listOfNotNull(m.zeit.takeIf { it > 0 }?.let { Fmt.relative(it) }, m.quelle.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val etwas = gesellschaft.isNotEmpty() || flaeche != null || sprachen.isNotEmpty() || versaetze.isNotEmpty() || h != null || d.feiertage.isNotEmpty() || d.meldungen.isNotEmpty() ||
        zeilen(Gruppe.STAAT).isNotEmpty() || zeilen(Gruppe.ENERGIE).isNotEmpty()
    if (etwas) Text(
        S.globusMehrQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )

    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    artikel?.let { NewsSheet(it, onDismiss = { artikel = null }) }
}
