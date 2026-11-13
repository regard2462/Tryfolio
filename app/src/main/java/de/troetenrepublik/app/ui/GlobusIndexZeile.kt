package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Boersenlinks
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.GlobusIndizes
import de.troetenrepublik.app.market.IndexSelbst
import de.troetenrepublik.app.market.IndexStand
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// [Globus Boersen 0.73] Zeile des Leitindex im Laenderblatt: Name und Tagesaenderung, bei altem Stand grau mit dem Datum,
// bei Laendern ohne Kurs sachlich "Keine Boerse" oder "Kein frei abrufbarer Index"; Laender mit Selbstabruf bieten dort das Laden an.
@Composable
internal fun IndexZeile(state: AppState, a2: String, index: GlobusIndizes.Index?, stand: IndexStand.Wert?, onInfo: (Pair<String, String>) -> Unit) {
    val signal = LocalSignal.current
    val stumm by state.globusStumm.collectAsStateWithLifecycle()
    val quelleStumm = a2 in stumm
    val heute = remember { LocalDate.now() }
    val frisch = stand != null && IndexStand.frisch(stand, heute)
    val selbst = IndexSelbst.fuer(a2)
    val ausSelbst = stand?.quelle?.startsWith("selbst:") == true
    if (index == null && selbst == null) {
        if (a2 in IndexStand.OHNE_BOERSE) LbZeile(S.lbBoerseLabel, S.lbKeineBoerse) else LbZeile(S.lbKeinIndexLabel, S.lbKeinIndex)
        return
    }
    // Der Index, aus dem der gezeigte Wert stammt
    val gewaehlt: GlobusIndizes.Index? = when {
        stand == null -> index
        stand.quelle.startsWith("yahoo") -> GlobusIndizes.NACH_LAND[a2]
        stand.quelle.startsWith("direkt:") -> de.troetenrepublik.app.market.BoersenDirekt.QUELLEN.firstOrNull { "direkt:" + it.id == stand.quelle }?.let { GlobusIndizes.Index(it.id, it.index) }
        else -> null
    }
    val info: Pair<String, String>?
    val label: String
    if (gewaehlt != null) {
        label = when {
            gewaehlt.etf -> S.lbEtfLabel(gewaehlt.symbol)
            gewaehlt.laenderIndex -> S.lbLaenderIndexLabel(gewaehlt.name)
            else -> S.lbIndexLabel(gewaehlt.name)
        }
        info = label to when {
            gewaehlt.etf -> S.globusIndexEtf(gewaehlt.name)
            gewaehlt.laenderIndex -> S.lbLaenderIndexInfo(gewaehlt.name)
            else -> S.lbIndexInfo(gewaehlt.name)
        }
    } else if (selbst != null) {
        label = S.lbIndexSelbstLabel(selbst.index)
        info = label to S.lbIndexSelbstInfo(selbst.index)
    } else {
        LbZeile(S.lbKeinIndexLabel, S.lbKeinIndex)
        return
    }
    val farbe = if (stand != null && frisch) (if (stand.prozent >= 0) signal.up else signal.down) else MaterialTheme.colorScheme.onSurfaceVariant
    LbZeile(label, stand?.let { Fmt.signedPct(it.prozent) } ?: "–", farbe) { onInfo(info) }
    if (stand != null && !frisch) {
        val ms = stand.kursDatum.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        LbZeile(S.lbStandVom, Fmt.date(ms))
    }
    // Quelle mehrfach ohne Antwort und kein frischer Wert: einmal knapp sagen, woran es liegen kann
    if (quelleStumm && !frisch) LbHinweis(S.lbIndexQuelleStumm)
    // Selbstabruf: nur anbieten, wo kein frischer Wert aus Yahoo oder Direktabruf vorliegt
    if (selbst != null && (stand == null || ausSelbst || !frisch)) {
        val uri = appLinks()
        val scope = rememberCoroutineScope()
        var offen by remember(a2) { mutableStateOf(false) }
        val imBrowser = de.troetenrepublik.app.plattform.quellenImBrowser()
        Text(
            S.lbIndexSelbstOeffnen, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable { if (imBrowser) offen = true else runCatching { uri.openUri(selbst.adresse) } }.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        if (offen) SelbstSeite(
            Boersenlinks.Link(selbst.name, selbst.adresse), onZu = { offen = false },
            onSeite = { html ->
                offen = false
                scope.launch { state.showToast(if (state.indexSelbstUebernehmen(selbst.id, html)) S.globusIndexSelbstFertig else S.globusIndexSelbstLeer) }
            },
        )
    }
}
