package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Alter
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Leitzins
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.LeitzinsTexte
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

// [Leitzins 0.78] Laenderblatt: Leitzins der Zentralbank aus den Daten der BIS. Die Kernzeile (Satz und seit wann) steht
// immer da, aufgeklappt der Verlauf als Treppe und die letzten Zinsschritte.

private fun lt(key: String, vararg w: String): String = LeitzinsTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Jahre, die der Verlauf hoechstens zurueckreicht
private const val VERLAUF_JAHRE = 30

// Zinsschritte, die die Liste zeigt
private const val SCHRITTE_GEZEIGT = 6

// Die Datei einmal gelesen, gilt fuer alle Laender
internal object LeitzinsLader {
    @Volatile private var stand: Leitzins.Datei? = null
    @Volatile private var versucht = false

    fun gespeichert(): Leitzins.Datei? = stand

    suspend fun laden(): Leitzins.Datei? {
        if (versucht) return stand
        val d = withContext(Dispatchers.Default) {
            runCatching { Leitzins.lesen(de.troetenrepublik.app.market.Daten.text(Leitzins.DATEI)) }.getOrNull()
        }
        stand = d
        versucht = true
        return d
    }
}

private fun datum(tag: LocalDate): String = Fmt.date(tag.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

@Composable
internal fun LaenderLeitzins(state: AppState, land: GlobusLand) {
    var datei by remember { mutableStateOf(LeitzinsLader.gespeichert()) }
    LaunchedEffect(Unit) { if (datei == null) datei = LeitzinsLader.laden() }
    val d = datei ?: return
    val gebuendelt = remember(d, land) { Leitzins.fuer(d, land.a2) } ?: return
    // Neuere Tageswerte der BIS (ein Tag gespeichert); ohne Netz bleibt der gebuendelte Stand
    var reihe by remember(gebuendelt) { mutableStateOf(gebuendelt) }
    LaunchedEffect(gebuendelt) {
        ohneAbbruch { state.globusLeitzinsTage(Leitzins.gebiet(land.a2), gebuendelt) }.getOrNull()?.let { reihe = Leitzins.ergaenzen(gebuendelt, it) }
    }
    val kern = remember(reihe) { Leitzins.kern(reihe) } ?: return
    // Endet die Reihe vor mehr als zwei Jahren (Land ohne laufende Meldung), bleibt der Abschnitt weg
    val heute = remember { LocalDate.now() }
    if (reihe.bis < heute.year * 12 + heute.monthValue - 1 - 24) return
    var offen by rememberSaveable("leitzins") { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    QuellenLeiste(listOf(LaenderQuellen.bis()))

    HistorieKopf(lt("titel"), offen) { offen = !offen }
    Row(Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(Fmt.pct(kern.satz, 2), style = MaterialTheme.typography.headlineSmall, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(10.dp))
        val seit = if (kern.nurMonat) Fmt.monthShortYear(YearMonth.of(kern.seit.year, kern.seit.monthValue)) else datum(kern.seit)
        Text(lt("seit", seit), style = MaterialTheme.typography.bodyMedium, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
    }
    if (offen) {
        val ab = remember(reihe) { Leitzins.verlaufAb(reihe, VERLAUF_JAHRE) }
        val punkte = remember(reihe, ab) { Leitzins.treppe(reihe, ab) }
        val abJahr = (ab / 12).toString()
        if (punkte.size >= 2) {
            LbUnterkopf(lt("verlauf", abJahr))
            HistDiagramm(
                listOf(HistLinie("", punkte, MaterialTheme.colorScheme.primary)), punkte.first().first, punkte.last().first, log = false, marken = emptyList(),
                achsenText = { Fmt.pct(it, if (abs(it - Math.round(it)) < 1e-9) 0 else 1) }, endeText = { Fmt.pct(it, 2) },
                beschreibung = lt("verlauf", abJahr), hoehe = 170.dp,
            )
            Leitzins.spanne(reihe, ab)?.let { (tief, hoch) -> LbZeile(lt("spanne", abJahr), lt("spanne.wert", Fmt.pct(tief, 2), Fmt.pct(hoch, 2))) }
        }
        val schritte = reihe.schritte.takeLast(SCHRITTE_GEZEIGT).reversed()
        if (schritte.isNotEmpty()) {
            LbUnterkopf(lt("schritte"))
            for (s in schritte) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(datum(s.tag), style = MaterialTheme.typography.bodyMedium, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                    ChangeValue(
                        s.aenderung, lt("schritt", Fmt.num(abs(s.aenderung), 2), Fmt.pct(s.nachher, 2)), style = MaterialTheme.typography.bodyMedium,
                        colored = false, nullAusText = false,
                    )
                }
            }
        }
    }
    if (info) {
        val stand = Alter.tag(d.stand)?.let { Fmt.date(it * 86_400_000L) } ?: d.stand
        MarktInfoBlatt(lt("titel"), lt("info", stand)) { info = false }
    }
}
