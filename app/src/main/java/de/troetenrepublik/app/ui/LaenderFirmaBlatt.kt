package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// [Laender Unternehmen 0.73] Blatt zu einer Firma ohne Aktienseite (kein Kuerzel bei Yahoo Finance oder nicht boersennotiert):
// Branche, Sitz, Boerse, Eigentuemer, Wert mit Jahr und der Wikipedia-Auszug, wo es einen Artikel gibt.
// Mitarbeiter, Gruendung und Eigentuemer aus Wikidata (CC0).
internal class FirmaDaten(
    val name: String, val branche: String, val boerse: String, val staat: Boolean, val wert: Double?, val jahr: Int?,
    val sitz: GlobusLand?, val kontext: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FirmaBlatt(state: AppState, f: FirmaDaten, onDismiss: () -> Unit) {
    val deutsch = I18n.locale.language == "de"
    var auszug by remember(f) { mutableStateOf<String?>(null) }
    var wd by remember(f) { mutableStateOf<de.troetenrepublik.app.market.FirmaWikidata.Angaben?>(null) }
    val links = appLinks()
    LaunchedEffect(f) {
        coroutineScope {
            launch { auszug = ohneAbbruch { state.firmaAuszug(f.name) }.getOrNull() }
            launch { wd = ohneAbbruch { state.firmaWikidata(f.name) }.getOrNull() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(f.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            if (f.branche.isNotBlank()) LbZeile(S.globusFirmaBranche, S.industryName(f.branche))
            // Sitz: die Stadt aus Wikidata, sonst das Land
            val ort = wd?.sitz?.takeIf { it.isNotBlank() } ?: f.sitz?.name(deutsch)
            ort?.let { LbZeile(S.globusFirmaSitz, it) }
            wd?.gegruendet?.let { LbZeile(S.globusFirmaGegruendet, it.toString()) }
            wd?.mitarbeiter?.let { LbZeile(S.globusFirmaMitarbeiter, Fmt.num(it.toDouble(), 0) + (wd?.mitarbeiterJahr?.let { j -> " ($j)" } ?: "")) }
            wd?.eigentuemer?.takeIf { it.isNotBlank() && !f.staat }?.let { LbZeile(S.globusFirmaEigner, it) }
            wd?.mutter?.takeIf { it.isNotBlank() }?.let { LbZeile(S.globusFirmaMutter, it) }
            if (f.boerse.isNotBlank()) LbZeile(S.globusFirmaBoerse, f.boerse)
            if (f.staat) LbZeile(S.globusFirmaEigner, S.globusFirmaStaat)
            wd?.website?.takeIf { it.isNotBlank() }?.let { w ->
                LbZeile(S.globusFirmaWebsite, w.substringAfter("://").removePrefix("www.").trimEnd('/'), MaterialTheme.colorScheme.primary) { runCatching { links.openUri(w) } }
            }
            f.wert?.let { LbZeile(S.globusFirmaWert, Fmt.compactMoney(it) + (f.jahr?.let { j -> " ($j)" } ?: "")) }
            auszug?.let { LbAbsatz(it) }
            if (f.kontext.isNotBlank()) LbHinweis(f.kontext)
            // Wikipedia verlangt die Lizenzangabe, wo ein Auszug steht
            if (auszug != null) QuellenAnhang(listOf(QuelleLink("Wikipedia (CC BY-SA 4.0)", "https://wikipedia.org/wiki/" + f.name.replace(' ', '_'))))
        }
    }
}
