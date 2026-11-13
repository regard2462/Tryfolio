package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.RegierungOge
import de.troetenrepublik.app.engine.VermoegenAnzeige
import de.troetenrepublik.app.market.Vermoegen

// Weitere Mitglieder der Regierung (seit 5.91): Minister, Gouverneure der Fed und Spitzenbeamte mit Transaktions-
// meldungen (OGE 278-T) bei der OGE oder auf whitehouse.gov. Liste und Meldungen holt die App auf dem Geraet, fuer den
// eigenen Gebrauch; die Seite des Praesidenten bleibt, wie sie ist.

// Route der Seite einer Person.
internal fun ogeRoute(id: String) = "copy-o/$id"

// Personen unter dem Praesidenten in der Liste der Regierung, juengste Meldung zuerst.
internal fun LazyListScope.ogePersonen(state: AppState, d: RegierungOge.Datei?, laedt: Boolean, gefolgt: Set<String>, oeffnen: (String) -> Unit) {
    item(key = "oge-kopf") { SectionHeader(S.ogeTitel) }
    val personen = d?.personen.orEmpty()
    if (personen.isEmpty()) {
        item(key = "oge-leer") { if (laedt) CopyLade() else CopyHinweis(S.ogeLeer) }
        return
    }
    items(personen, key = { "oge-" + it.id }) { p ->
        val anzahl = p.meldungen.sumOf { it.geschaefte.size }
        // Amt in eigener Zeile, darunter Vermoegen (seit 5.92), Geschaefte und letzte Meldung
        val punkte = listOfNotNull(
            RegierungOge.vermoegenAktuell(p)?.let { v -> vermoegenUeber(RegierungOge.alsSpanne(p, v))?.let { S.rgVermoegen(it) } },
            if (anzahl > 0) S.copyTradesCount(anzahl) else S.ogeMeldungen(p.meldungen.size),
            p.letzte.takeIf { it.isNotBlank() }?.let { S.copyLast(Fmt.date(Politik.tag(it))) },
        )
        val amt = listOf(p.amt, p.behoerde).filter { it.isNotBlank() }.distinct().joinToString(", ")
        RegierungZeile(p.name, amt, punkte, "o:${p.id}" in gefolgt, Fotos.von(state, "o:${p.id}")) { oeffnen(ogeRoute(p.id)) }
    }
}

// Punkte einer Zeile mit „ · “ verbunden; umgebrochen wird nur zwischen den Punkten (geschuetzte Leerzeichen innen).
internal fun punkteZeile(punkte: List<String>): String =
    punkte.filter { it.isNotBlank() }.joinToString("\u00A0· ") { it.trim().replace(' ', '\u00A0') }

// Person der Regierung in der Liste (seit 5.92): Name, Amt in hoechstens zwei Zeilen, darunter die Punkte.
@Composable
internal fun RegierungZeile(
    name: String, amt: String?, punkte: List<String>, gefolgt: Boolean,
    foto: de.troetenrepublik.app.market.Personenbilder.Foto? = null, onClick: () -> Unit,
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonBild(foto, name, size = 38)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            if (!amt.isNullOrBlank()) Text(amt, style = MaterialTheme.typography.bodySmall, color = grau)
            if (punkte.isNotEmpty()) Text(punkteZeile(punkte), style = MaterialTheme.typography.bodySmall, color = grau)
        }
        if (gefolgt) Icon(Icons.Filled.Star, S.copyFollowing, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
    }
}

// Untergrenze des Nettovermoegens als „ueber X“ fuer die Liste; null, wenn die Kredite sie unter null druecken koennen.
internal fun vermoegenUeber(v: Vermoegen.E): String? = VermoegenAnzeige.teile(v).nettoVon?.takeIf { it > 0 }?.let { S.copyOver(vmEinzel(it)) }

// Vermoegen laut neuester Vermoegensmeldung (seit 5.92), aufklappbar mit Teilen, kurzem Hinweis und dem PDF.
@Composable
internal fun OgeVermoegenZeile(p: RegierungOge.Person, m: RegierungOge.VMeldung, laden: (String) -> Unit) {
    var offen by remember(m.pdf) { mutableStateOf(false) }
    val t = remember(p, m) { VermoegenAnzeige.teile(RegierungOge.alsSpanne(p, m)) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable { offen = !offen }.padding(vertical = 8.dp)) {
        Text(S.ogeVmTitel(m.art, m.jahr), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
        VermoegenTeileZeilen(t)
        if (offen) Column(Modifier.padding(horizontal = 20.dp)) {
            Text(listOfNotNull(vmOffenText(t), S.vmPosten(t.posten)).joinToString(" "), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 4.dp))
            StichpunktText(S.ogeVmHinweis, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (offen) {
        // Original der Meldung in den Download-Ordner
        Row(
            Modifier.fillMaxWidth().clickable { laden(m.pdf) }.padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.ogeVmPdf(Fmt.date(Politik.tag(m.stand))), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f))
            Text(S.pdfKurz, style = MaterialTheme.typography.labelSmall, color = LocalSignal.current.link)
            Icon(Icons.Outlined.FileDownload, S.pdfLaden, tint = LocalSignal.current.link, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

@Composable
fun OgePersonScreen(state: AppState, id: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val d by state.ogeRegierung.collectAsStateWithLifecycle()
    var laedt by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) {
        runCatching { state.ogeLaden(id) }
        laedt = false
    }
    val p = d?.personen?.firstOrNull { it.id == id }
    val alle = remember(p) { p?.let { RegierungOge.daten(it) }.orEmpty() }
    val gelesen = remember(alle) { alle.filter { it.second != null } }
    val offen = alle.size - gelesen.size
    val laden = rememberPdfLaden(state)
    PolitikerSeite(
        state = state, titel = p?.name ?: id, untertitel = p?.let { listOf(it.amt, it.behoerde).filter { s -> s.isNotBlank() }.distinct().joinToString(", ") }?.ifBlank { null },
        folgeKey = "o:$id",
        // Solange noch PDFs gelesen werden, gilt die Seite als ladend
        daten = if (laedt && offen > 0 && gelesen.isEmpty()) null else gelesen,
        meldungenText = S.copyFilingsAll, papier = emptyList(),
        verlaufBereit = !laedt, verlauf = { g -> VerlaufLader.kongress(state, "o:$id", g) },
        hinweis = S.ogeHinweis, notiz = null, onBack = onBack, onOpen = onOpen,
        meldungenAnzahl = alle.size, leer = !laedt && p == null,
        zusatz = p?.meldungen?.firstOrNull()?.let { m ->
            {
                RegierungOge.vermoegenAktuell(p)?.let { v -> OgeVermoegenZeile(p, v, laden) }
                if (offen > 0 && !laedt) CopyHinweis(S.ogeUngelesen(offen))
                NavRow(S.ogePdf(Fmt.date(Politik.tag(m.tag))), S.pdfLaden) { laden(m.pdf) }
            }
        },
        // Bild, Partei und PDF jeder Meldung (seit 5.92); Meldungs-Kopf o-<id>-<Nummer> wie in RegierungOge.daten
        foto = Fotos.von(state, "o:$id"),
        info = Infos.von(state, "o:$id"),
        pdf = { k -> k.docId.substringAfterLast('-').toIntOrNull()?.let { p?.meldungen?.getOrNull(it)?.pdf } },
    )
}
