package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import de.troetenrepublik.app.engine.Boersenlinks
import de.troetenrepublik.app.market.IndexSelbst
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
    val links = appLinks()
    val scope = rememberCoroutineScope()
    var offenId by rememberSaveable { mutableStateOf<String?>(null) }
    // Aufgeklappte Quellenzeilen, mit | getrennt (gemerkt)
    var offene by rememberSaveable { mutableStateOf("") }
    // [Globus Boersen 0.73] Boersenindex im Browser der App
    var indexId by rememberSaveable { mutableStateOf<String?>(null) }
    // Aufgeklappte Zeile der Liste (Typ und Kennung)
    var aufId by rememberSaveable { mutableStateOf<String?>(null) }

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

    IndexSelbst.QUELLEN.firstOrNull { it.id == indexId }?.let { q ->
        SelbstSeite(
            Boersenlinks.Link(q.name, q.adresse), onZu = { indexId = null },
            onSeite = { html ->
                indexId = null
                scope.launch { state.showToast(if (state.indexSelbstUebernehmen(q.id, html)) S.globusIndexSelbstFertig else S.globusIndexSelbstLeer) }
            },
        )
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
        // Eine Zeile je Quelle, nach Region; Beschreibung, Schritte und Oeffnen erst beim Aufklappen (eine Zeile offen)
        val eintraege = selbstEintraege()
        REGIONEN.forEach { region ->
            val inRegion = eintraege.filter { it.region == region }
            if (inRegion.isEmpty()) return@forEach
            SectionHeader(S.saRegion(region))
            inRegion.forEach { e ->
                val offen = aufId == e.schluessel
                Row(
                    Modifier.fillMaxWidth().clickable { aufId = if (offen) null else e.schluessel }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(S.saTyp(e.typ), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    AufklappPfeil(offen)
                }
                if (offen) {
                    when (e.typ) {
                        "import" -> {
                            Text(S.saInhalt(e.id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                            S.saSchritte(e.id).forEachIndexed { i, st ->
                                Text(
                                    "${i + 1}. $st", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                                )
                            }
                            // Ohne Seite (Katar): kein Knopf, der Weg steht in den Schritten
                            if (e.url.isNotBlank()) SeiteOeffnen { oeffnen(e.id, e.url, quelle = true) }
                        }
                        "index" -> {
                            Text(e.zusatz, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                            Text(S.saIndexText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                            SeiteOeffnen { if (quellenImBrowser()) indexId = e.id else runCatching { links.openUri(e.url) } }
                        }
                        else -> {
                            Text(S.saAnsehenInhalt(e.id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                            Text(S.saAnsehenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                            SeiteOeffnen { if (e.extern) runCatching { links.openUri(e.url) } else oeffnen(e.id, e.url, quelle = false) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Trennlinie(Modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// Quellen nur zum Ansehen (seit 5.92): Kennung fuer die Texte und Seite. Die App ruft hier nichts selbst ab.
// extern: immer im Standardbrowser (Lager, Fracht, Margin; vorher eigene Zeilen unter „Selbst anstossen“).
internal class AnsehQuelle(val id: String, val url: String, val extern: Boolean = false)

internal val NUR_ANSEHEN = listOf(
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
    AnsehQuelle("lme", Anstossen.LME_BERICHT, extern = true),
    AnsehQuelle("comex", de.troetenrepublik.app.engine.ComexLager.BERICHT, extern = true),
    AnsehQuelle("fbx", Anstossen.FBX, extern = true),
    AnsehQuelle("baltic", Anstossen.BALTIC, extern = true),
    AnsehQuelle("finra", Anstossen.FINRA_MARGIN, extern = true),
)

// Eine Zeile der Liste: Typ ist „import“ (Datei einlesen), „index“ (Boersenindex pruefen) oder „ansehen“.
private class SelbstEintrag(val typ: String, val id: String, val region: String, val name: String, val url: String, val extern: Boolean = false, val zusatz: String = "") {
    val schluessel get() = "$typ.$id"
}

// Reihenfolge der Regionen in der Liste.
private val REGIONEN = listOf("AM", "EU", "AS", "AF", "WL")

// Region je Quelle: AM Amerika, EU Europa, AS Asien und Ozeanien, AF Naher Osten und Afrika, WL Maerkte und Rohstoffe.
private val REGION_JE_ID = mapOf(
    "sedi" to "AM", "consob" to "EU", "bund" to "EU", "fca" to "EU", "bse" to "AS", "egx" to "AF", "kw" to "AF", "qse" to "AF",
    "pl" to "EU", "li" to "EU", "mt" to "EU", "asx" to "AS",
    "adx" to "AF", "luse" to "AF", "bvrd" to "AM", "ttse" to "AM", "ilboursa" to "AF",
    "bh" to "AF", "jm" to "AM", "tt" to "AM", "il" to "AF", "id" to "AS", "jp" to "AS", "kr" to "AS", "pt" to "EU", "hr" to "EU",
    "jefs" to "AM", "stooq-ar" to "AM", "ep" to "EU", "aph" to "AS", "oge" to "AM",
)

private fun selbstEintraege(): List<SelbstEintrag> = buildList {
    RegisterImportWelt.QUELLEN.forEach { add(SelbstEintrag("import", it.id, REGION_JE_ID[it.id] ?: "WL", S.saName(it.id), it.url)) }
    IndexSelbst.QUELLEN.forEach { add(SelbstEintrag("index", it.id, REGION_JE_ID[it.id] ?: "WL", it.name, it.adresse, zusatz = it.index)) }
    NUR_ANSEHEN.forEach { add(SelbstEintrag("ansehen", it.id, REGION_JE_ID[it.id] ?: "WL", S.saAnsehenName(it.id), it.url, it.extern)) }
}

@Composable
private fun SeiteOeffnen(onClick: () -> Unit) {
    Text(
        S.saOeffnen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
        modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { onClick() }.padding(8.dp),
    )
}
