package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.Praesidialdokumente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Praesidialdokumente (0.77): Liste im Copytrading-Reiter Politik unter Regierung und eine Zeile unter „Politik und
// Behoerden“ auf der Seite eines Werts. Ein Dokument steht dort, wenn sein Titel eine Firma des Katalogs oder ein
// Erzeugnis der Branche des Werts nennt; die Zeile sagt, was im Titel steht.

// Dokument mit dem, was im Titel genannt ist (Firmen des Katalogs, Erzeugnisse laut fester Liste).
internal class PraesidialEintrag(val dokument: Praesidialdokumente.Dokument, val genannt: List<String>)

private fun genannt(d: Praesidialdokumente.Dokument, firmen: List<String>): List<String> =
    firmen + Praesidialdokumente.themen(d).map { S.pdThema(it.id) }

// Zeile der Angaben: Datum, Art, was im Titel genannt ist.
internal fun praesidialMeta(d: Praesidialdokumente.Dokument, genannt: List<String>): String =
    listOfNotNull(
        Politik.tag(d.tag).takeIf { it > 0 }?.let { Fmt.date(it) } ?: d.tag,
        S.pdArt(d.art.name),
        genannt.takeIf { it.isNotEmpty() }?.let { S.pdImTitel(it.joinToString(", ")) },
    ).joinToString(PUNKT)

private fun ab(): String = Uhr.heute().minusYears(1).toString()

// Dokumente der letzten zwoelf Monate fuer die Liste in der Politik; null, bis sie da sind oder wenn der Abruf misslingt.
@Composable
internal fun rememberPraesidial(): List<PraesidialEintrag>? {
    var liste by remember { mutableStateOf<List<PraesidialEintrag>?>(null) }
    LaunchedEffect(Unit) {
        val dokumente = runCatching { Praesidialdokumente.laden(ab()) }.getOrNull() ?: return@LaunchedEffect
        liste = withContext(Dispatchers.Default) {
            val namen = Catalog.get().aktien.map { it.n }
            dokumente.map { PraesidialEintrag(it, genannt(it, Praesidialdokumente.firmen(it, namen))) }
        }
    }
    return liste
}

// Zahl der Dokumente der letzten 30 Tage.
internal fun praesidialNeu(liste: List<PraesidialEintrag>): Int {
    val grenze = Uhr.heute().minusDays(30).toString()
    return liste.count { it.dokument.tag >= grenze }
}

// Wunsch eines Verweises (News-Reiter „Trump“): die Liste unter Politik > Regierung gleich aufgeklappt zeigen.
internal object PraesidialStart {
    var offen = false
    fun nehmen(): Boolean = offen.also { offen = false }
}

// Verweise im News-Reiter „Trump“: Truth-Seite mit Marktreaktion und die Praesidialdokumente mit der Zahl der
// letzten 30 Tage; der Tipp fuehrt zur aufgeklappten Liste.
@Composable
internal fun TrumpVerweise(oeffnen: (String) -> Unit) {
    NavRow(S.truthTitle, S.trumpMarktreaktion) { oeffnen(CopyRouten.TRUTH) }
    val liste = rememberPraesidial()
    if (!liste.isNullOrEmpty()) {
        NavRow(S.pdTitel, S.pdNeu(praesidialNeu(liste))) {
            PraesidialStart.offen = true
            CopyStart.setzen(de.troetenrepublik.app.data.WidgetZiel.COPY_REGIERUNG)
            oeffnen(CopyRouten.START)
        }
    }
}

// Eingeklappt: Zahl der letzten 30 Tage und das Datum des juengsten, aufgeklappt die zehn juengsten, darunter der Pfeil
// fuer den Rest der zwoelf Monate.
internal fun LazyListScope.praesidialAbschnitt(
    state: de.troetenrepublik.app.AppState,
    liste: List<PraesidialEintrag>?,
    offen: Boolean,
    onOffen: () -> Unit,
    alle: Boolean,
    onAlle: () -> Unit,
) {
    if (liste.isNullOrEmpty()) return
    val neu = praesidialNeu(liste)
    val juengste = Politik.tag(liste.first().dokument.tag).takeIf { it > 0 }?.let { Fmt.date(it) }.orEmpty()
    item(key = "praesidial-kopf", contentType = "kern-kopf") {
        var info by remember { mutableStateOf(false) }
        WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.praesidial(), Modifier.fillMaxWidth()) {
            KernKopf(
                S.pdTitel,
                KernZeile(listOf(KernTeil(S.pdNeu(neu)), KernTeil(S.pdZuletzt(juengste)))),
                offen,
                onInfo = { info = true },
            ) { onOffen() }
        }
        if (info) MarktInfoBlatt(S.pdTitel, S.dp("pd.info")) { info = false }
    }
    if (!offen) return
    val sichtbar = if (alle) liste.take(MAX_LISTE) else liste.take(10)
    itemsIndexed(sichtbar, key = { _, e -> "praesidial-" + e.dokument.nummer.ifBlank { e.dokument.adresse } }, contentType = { _, _ -> "praesidial-zeile" }) { zeile, e ->
        val uri = appLinks()
        Column {
            if (zeile > 0) Trennlinie()
            Column(Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(e.dokument.adresse) } }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                PunktText(praesidialMeta(e.dokument, e.genannt), style = MaterialTheme.typography.bodySmall)
                Text(e.dokument.titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    if (liste.size > 10) item(key = "praesidial-mehr", contentType = "pfeil") {
        ListenPfeil(alle, label = if (alle) S.less else "${S.more} (${minOf(liste.size, MAX_LISTE) - 10})") { onAlle() }
    }
}

private const val MAX_LISTE = 80

// Dokumente der letzten zwoelf Monate mit Bezug zu diesem Wert (Firma im Titel oder Erzeugnis seiner Branche), je Seite
// einmal geholt. Ohne Bezug oder bei Fehlern leer.
@Composable
internal fun praesidialFuerWert(symbol: String, name: String, industrie: String, merker: SeitenMerker?): List<Pair<Praesidialdokumente.Dokument, List<String>>> {
    var treffer by gemerkt<List<Pair<Praesidialdokumente.Dokument, List<String>>>?>(merker, "pd/$symbol") { null }
    LaunchedEffect(symbol, name, industrie) {
        if (treffer != null) return@LaunchedEffect
        val dokumente = runCatching { Praesidialdokumente.laden(ab()) }.getOrNull() ?: return@LaunchedEffect
        treffer = withContext(Dispatchers.Default) {
            val katalog = Catalog.get().aktien.firstOrNull { it.s == symbol }
            val katalogName = katalog?.n ?: name
            Praesidialdokumente.fuerWert(dokumente, ab(), katalogName, industrie, katalog?.i.orEmpty()).map { (d, b) ->
                d to (if (b.firma) listOf(katalogName) else emptyList()) + b.themen.map { S.pdThema(it.id) }
            }
        }
    }
    return treffer.orEmpty()
}
