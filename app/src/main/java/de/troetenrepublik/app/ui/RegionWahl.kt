package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Regionen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Plattform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

// 450 -> "7:30", 1440 -> "24:00"
internal fun uhrzeit(minuten: Int): String = "${minuten / 60}:${"%02d".format(minuten % 60)}"

// Der Vorschlag nach der Zeitzone zuerst, dann alle uebrigen nach Namen.
@Composable
internal fun LandAuswahl(gewaehlt: String, onWahl: (String) -> Unit) {
    val deutsch = I18n.lang == Lang.DE
    val vorschlag = remember { Regionen.vorschlag() }
    val uebrige = remember(deutsch) { Regionen.LAENDER.filter { it.code != vorschlag }.sortedBy { it.name(deutsch) } }
    Column(Modifier.fillMaxWidth()) {
        Hinweiszeile(S.regVorschlag)
        LandZeile(Regionen.land(vorschlag), gewaehlt, deutsch, onWahl)
        Hinweiszeile(S.regAndere)
        uebrige.forEach { LandZeile(it, gewaehlt, deutsch, onWahl) }
    }
}

@Composable
private fun LandZeile(l: Regionen.Land, gewaehlt: String, deutsch: Boolean, onWahl: (String) -> Unit) =
    OptionRow(l.name(deutsch), S.waehrungName(l.waehrung) + " · " + l.boerse, l.code == gewaehlt) { onWahl(l.code) }

@Composable
private fun Hinweiszeile(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 14.dp, bottom = 4.dp))
}

// Die Waehrung des Landes zuerst.
@Composable
internal fun WaehrungAuswahl(gewaehlt: String, land: String, onWahl: (String) -> Unit) {
    val eigene = Regionen.land(land).waehrung
    Column(Modifier.fillMaxWidth()) {
        (listOf(eigene) + Regionen.WAEHRUNGEN.filter { it != eigene }).forEach { w ->
            OptionRow("${S.waehrungName(w)} ($w)", "", w == gewaehlt) { onWahl(w) }
        }
    }
}

// Wie an der Heimatboerse des Landes, eigene Zeiten oder rund um die Uhr.
@Composable
internal fun ZeitenAuswahl(land: String, modus: String, von: Int, bis: Int, onAendern: (modus: String, von: Int, bis: Int) -> Unit) {
    val l = Regionen.land(land)
    val deutsch = I18n.lang == Lang.DE
    val heimat = MarketHours.zeitenFuer(land, MarketHours.LAND, von, bis).aktien?.label.orEmpty()
    Column(Modifier.fillMaxWidth()) {
        OptionRow(S.regZeitenLand(l.name(deutsch)), S.regZeitenLandHint(l.boerse, heimat, l.code == Regionen.DE.code), modus == MarketHours.LAND) {
            onAendern(MarketHours.LAND, von, bis)
        }
        OptionRow(S.regZeitenEigene, S.regZeitenEigeneHint, modus == MarketHours.EIGENE) { onAendern(MarketHours.EIGENE, von, bis) }
        if (modus == MarketHours.EIGENE) {
            Text(S.regVonBis(uhrzeit(von), uhrzeit(bis)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            // Viertelstunden von 0:00 bis 24:00, mindestens eine Stunde offen.
            RangeSlider(
                value = von.toFloat()..bis.toFloat(),
                onValueChange = { r ->
                    val a = (Math.round(r.start / 15f) * 15).coerceIn(0, 23 * 60)
                    val b = (Math.round(r.endInclusive / 15f) * 15).coerceIn(a + 60, 24 * 60)
                    onAendern(MarketHours.EIGENE, a, b)
                },
                valueRange = 0f..1440f,
                steps = 95,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        OptionRow(S.regZeitenImmer, S.regZeitenImmerHint, modus == MarketHours.IMMER) { onAendern(MarketHours.IMMER, von, bis) }
    }
}

// Quelle: data/regional.json, 0 ohne Datei.
internal fun angepassteLektionen(land: String): Int = runCatching {
    val text = Plattform.asset("finanzwissen/data/regional.json").decodeToString()
    Json.parseToJsonElement(text).jsonObject[land.lowercase()]?.jsonObject?.get("lektionen")?.jsonObject?.size ?: 0
}.getOrDefault(0)

// Abschnitt im Profil: Land, Waehrung und Handelszeiten. Dazu, was die Wahl nicht aendert (Steuern nach deutschem
// Recht) und was sie im Finanzwissen anpasst.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegionAbschnitt(state: AppState) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val deutsch = I18n.lang == Lang.DE
    val land = Regionen.land(settings.land.ifBlank { Regionen.DE.code })
    var blatt by remember { mutableStateOf<String?>(null) }
    val z = MarketHours.zeiten
    SectionHeader(S.regionTitel)
    NavRow(S.regLand, land.name(deutsch)) { blatt = "land" }
    NavRow(S.regWaehrung, "${S.waehrungName(settings.waehrung)} (${settings.waehrung})") { blatt = "waehrung" }
    NavRow(S.regZeiten, S.tradingHoursFor(z.aktien?.label, z.derivate?.label, z.wochenende, land.code != Regionen.DE.code).replace("\n", " · ")) { blatt = "zeiten" }
    if (Fmt.stufen.size > 1) {
        Text(S.regAnzeige, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
        Column(Modifier.padding(horizontal = 8.dp)) {
            Fmt.stufen.forEachIndexed { i, w ->
                val kurs = Fmt.jeEuro(w)
                OptionRow("${S.waehrungName(w)} ($w)", if (w != "EUR" && kurs > 0) S.fxRate("EUR", w, Fmt.num(kurs, 4)) else "", Fmt.anzeigeWaehrung == w) {
                    state.waehrungStufe(i)
                }
            }
        }
        Text(S.regAnzeigeHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    if (land.code != Regionen.DE.code) Text(S.regSteuerHinweis(land.name(deutsch)), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    val angepasst = remember(land.code) { angepassteLektionen(land.code) }
    if (angepasst > 0) Text(S.regWissenUs(angepasst), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

    blatt?.let { art ->
        ModalBottomSheet(
            onDismissRequest = { blatt = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp).padding(bottom = 28.dp)) {
                val s = settings
                when (art) {
                    "land" -> {
                        Text(S.regLandTitel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp))
                        LandAuswahl(land.code) { neu -> state.regionSetzen(neu, s.waehrung, s.handelszeiten, s.handelVon, s.handelBis); blatt = null }
                    }
                    "waehrung" -> {
                        Text(S.regWaehrungTitel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp))
                        Text(S.regWaehrungText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        WaehrungAuswahl(s.waehrung, land.code) { neu ->
                            state.regionSetzen(land.code, neu, s.handelszeiten, s.handelVon, s.handelBis)
                            state.waehrungStufe(0)
                            blatt = null
                        }
                    }
                    else -> {
                        Text(S.regZeitenTitel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp))
                        Text(S.regZeitenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        ZeitenAuswahl(land.code, s.handelszeiten, s.handelVon, s.handelBis) { m, v, b -> state.regionSetzen(land.code, s.waehrung, m, v, b) }
                    }
                }
            }
        }
    }
}
