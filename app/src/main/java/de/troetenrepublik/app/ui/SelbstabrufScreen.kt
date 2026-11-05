package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.RegisterImportWelt
import de.troetenrepublik.app.plattform.QuellenBrowser
import de.troetenrepublik.app.plattform.quellenImBrowser
import kotlinx.coroutines.launch

// Quellen zum Selbstabrufen: Register, deren Daten die Person selbst laedt. Die App oeffnet die Seite und
// uebernimmt die geladene Datei.
@Composable
fun SelbstabrufScreen(state: AppState, onBack: () -> Unit) {
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberScrollState()
    val links = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var offenId by rememberSaveable { mutableStateOf<String?>(null) }

    // Geoeffnete Quelle oder Ansehen-Quelle: Seite, erlaubte Endungen, Namensmuster und Titel.
    class Ziel(val url: String, val endungen: List<String>, val muster: String, val name: String)

    val ziel = offenId?.let { id ->
        RegisterImportWelt.QUELLEN.firstOrNull { it.id == id }?.let { Ziel(it.url, it.endungen, it.muster, S.saName(it.id)) }
            ?: NUR_ANSEHEN.firstOrNull { it.id == id }?.let { Ziel(it.url, listOf("pdf", "csv", "xlsx", "html", "htm", "json"), "", S.saAnsehenName(it.id)) }
    }

    // Quellen oeffnen im Browser der App; Ansehen-Quellen nur auf Android, am Desktop bleibt der Standardbrowser.
    fun oeffnen(id: String, url: String, quelle: Boolean) {
        if (quellenImBrowser() || quelle) offenId = id else runCatching { links.openUri(url) }
    }

    if (ziel != null) {
        // Der Browser fuellt den Bildschirm; kein Wegwischen, damit es nicht mit dem Scrollen der Seite kollidiert.
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScreenHeader(ziel.name, null, onBack = { offenId = null })
            QuellenBrowser(
                ziel.url, ziel.endungen, ziel.muster, Modifier.weight(1f).fillMaxWidth(),
                onDatei = { _, daten -> offenId = null; scope.launch { state.showToast(state.registerImport(daten)) } },
                onFehler = { state.showToast(S.saFehler) },
                onSchliessen = { offenId = null },
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }.verticalScroll(liste)) {
        ScreenHeader(S.saTitel, null, onBack)
        AufklappText(S.saEinleitung, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        RegisterImportWelt.QUELLEN.forEach { q ->
            SectionHeader(S.saName(q.id))
            Text(S.saInhalt(q.id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            S.saSchritte(q.id).forEachIndexed { i, s ->
                Text(
                    "${i + 1}. $s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            // Ohne Seite (Katar): kein Knopf, der Weg steht in den Schritten
            if (q.url.isNotBlank()) Text(
                S.saOeffnen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                modifier = Modifier.padding(horizontal = 12.dp).clickable { oeffnen(q.id, q.url, quelle = true) }.padding(8.dp),
            )
        }
        // Seit 5.92: Quellen, fuer die die App keinen Leser hat. Ehrlich gekennzeichnet, nur zum Ansehen im Browser.
        SectionHeader(S.saAnsehenTitel)
        Text(S.saAnsehenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        NUR_ANSEHEN.forEach { q ->
            Text(S.saAnsehenName(q.id), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
            Text(S.saAnsehenInhalt(q.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            Text(
                S.saOeffnen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                modifier = Modifier.padding(horizontal = 12.dp).clickable { oeffnen(q.id, q.url, quelle = false) }.padding(8.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

// Quellen nur zum Ansehen (seit 5.92): Kennung fuer die Texte und Seite. Die App ruft hier nichts selbst ab.
private class AnsehQuelle(val id: String, val url: String)

private val NUR_ANSEHEN = listOf(
    AnsehQuelle("bh", "https://www.bahrainbourse.com/"),
    AnsehQuelle("jm", "https://www.jamstockex.com/"),
    AnsehQuelle("tt", "https://www.stockex.co.tt/"),
    AnsehQuelle("il", "https://www.magna.isa.gov.il/"),
    AnsehQuelle("id", "https://www.idx.co.id/"),
    AnsehQuelle("jp", "https://disclosure2.edinet-fsa.go.jp/"),
    AnsehQuelle("kr", "https://dart.fss.or.kr/"),
    AnsehQuelle("pt", "https://www.cmvm.pt/"),
    AnsehQuelle("hr", "https://zse.hr/en"),
    AnsehQuelle("jefs", "https://pub.jefs.uscourts.gov/"),
    AnsehQuelle("ep", "https://www.europarl.europa.eu/meps/de/home"),
    AnsehQuelle("aph", "https://www.aph.gov.au/Senators_and_Members/Members/Register"),
    AnsehQuelle("oge", "https://www.oge.gov/web/oge.nsf/Officials%20Individual%20Disclosures%20Search%20Collection?OpenForm"),
)
