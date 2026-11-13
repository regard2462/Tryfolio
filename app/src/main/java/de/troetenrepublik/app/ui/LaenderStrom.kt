package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Blattaufbau
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.StromMarkt
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.StromTexte
import de.troetenrepublik.app.market.StromAbruf
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

// Strom im Laenderblatt (Abschnitt "Strom"): Grosshandelspreis (Day-Ahead) der Gebotszone des Landes mit Tageswert,
// Einordnung und Verlauf der letzten 30 Tage, nur mit dem ENTSO-E-Token unter Zugaenge. Fuer Deutschland dazu Erzeugung
// nach Quelle und Verbrauch aus SMARD, ohne Schluessel. Beides zeigt zuerst den gespeicherten Stand und laedt dann nach.

private const val TAG_MS = 86_400_000L
private const val STUNDE_MS = 3_600_000L

private fun st(key: String, vararg w: String) = StromTexte.t(key, *w)

private fun tagText(tag: Long) = Fmt.dayMonthNumJahr(tag * TAG_MS + TAG_MS / 2)

private fun meldungSchluessel(m: StromAbruf.Meldung?): String = when (m) {
    StromAbruf.Meldung.SCHLUESSEL -> "m.schluessel"
    StromAbruf.Meldung.ABGELEHNT -> "m.abgelehnt"
    StromAbruf.Meldung.LIMIT -> "m.limit"
    StromAbruf.Meldung.NETZ -> "m.netz"
    StromAbruf.Meldung.KEINE_DATEN, null -> "m.keine"
}

@Composable
internal fun LaenderStrom(state: AppState, land: GlobusLand, onOpen: (String) -> Unit) {
    val zone = remember(land.a2) { StromMarkt.zone(land.a2) } ?: return
    val deutschland = zone.a2 == "DE"
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val mitToken = StromMarkt.tokenGueltig(settings.keyEntsoe)
    var preise by remember(zone.a2) { mutableStateOf<StromAbruf.Preise?>(null) }
    var preiseFertig by remember(zone.a2, mitToken) { mutableStateOf(!mitToken) }
    var erzeugung by remember(zone.a2) { mutableStateOf<StromAbruf.Erzeugung?>(null) }
    var erzeugungFertig by remember(zone.a2) { mutableStateOf(!deutschland) }
    var info by remember(zone.a2) { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(zone.a2, mitToken) {
        preise = withContext(Dispatchers.IO) { state.stromPreiseGespeichert(zone.a2) } ?: preise
        if (!mitToken) return@LaunchedEffect
        preise = ohneAbbruch { state.stromPreise(zone.a2) }.getOrNull() ?: preise
        preiseFertig = true
    }
    LaunchedEffect(deutschland) {
        if (!deutschland) return@LaunchedEffect
        erzeugung = withContext(Dispatchers.IO) { state.stromErzeugungGespeichert() } ?: erzeugung
        erzeugung = ohneAbbruch { state.stromErzeugung() }.getOrNull() ?: erzeugung
        erzeugungFertig = true
    }

    val heute = remember { Math.floorDiv(System.currentTimeMillis(), TAG_MS) }
    val tage = preise?.tage.orEmpty()
    val lage = remember(tage, heute) { StromMarkt.lage(tage, heute) }
    val mix = remember(erzeugung) { erzeugung?.reihen?.let(StromMarkt::mix) }
    val signal = LocalSignal.current

    LaenderAbschnitt(st("titel"), "strom") {
        // ---- Grosshandelspreis der Gebotszone
        val zusatz = zone.zusatz?.let { " ($it)" }.orEmpty()
        when {
            lage != null -> {
                val t = lage.tag
                val titel = (if (t.tag == heute) st("preis.heute") else st("preis.am", tagText(t.tag))) + zusatz
                LbZeile(titel, Fmt.num(t.mittel, 1) + " " + st("einheit")) { info = st("info.titel") to st("info.preis") }
                StromMarkt.morgen(tage, heute)?.let { LbZeile(st("morgen"), Fmt.num(it.mittel, 1) + " " + st("einheit")) }
                lage.gegenVortag?.let { StromAenderung(st("vortag"), it) }
                lage.gegenSchnitt?.let { StromAenderung(st("schnitt"), it) }
                LbZeile(st("spanne"), st("spanne.wert", Fmt.num(t.min, 0), Fmt.num(t.max, 0)))
                val verlauf = tage.filter { it.tag <= heute }.takeLast(30)
                if (verlauf.size >= 3) {
                    LbUnterkopf(st("verlauf"))
                    StromKurve(verlauf.map { it.mittel }, tagText(verlauf.first().tag), tagText(verlauf.last().tag)) { Fmt.num(it, 0) }
                }
                StromMeldung(preise?.meldung, preise?.stand ?: 0L)
                QuellenLeiste(listOf(st("quelle.entsoe") to StromMarkt.ENTSOE_SEITE))
            }
            !mitToken -> StromOhneToken { onOpen("zugaenge") }
            !preiseFertig && tage.isEmpty() ->
                Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            else -> StromMeldung(preise?.meldung ?: StromAbruf.Meldung.KEINE_DATEN, 0L)
        }

        // ---- Erzeugung und Verbrauch in Deutschland
        if (deutschland) {
            LbUnterkopf(st("erz.titel"))
            when {
                mix != null -> {
                    val reihen = erzeugung?.reihen.orEmpty()
                    LbZeile(st("erz.erneuerbar"), Fmt.pct(mix.erneuerbar, 0), signal.up) { info = st("erz.info.titel") to st("erz.info") }
                    val fossil = MaterialTheme.colorScheme.onSurfaceVariant
                    BalkenListe(mix.anteile.filter { it.prozent >= 0.1 }.map {
                        BalkenZeile(st("q." + it.schluessel), Blattaufbau.balkenAnteil(it.prozent), Fmt.pct(it.prozent, 1), if (it.erneuerbar) signal.up else fossil)
                    })
                    StromMarkt.last(reihen["last"].orEmpty())?.let { l ->
                        LbZeile(st("erz.verbrauch"), st("gw", Fmt.num(l.jetzt / 1000.0, 1)))
                        LbZeile(st("erz.verbrauch24"), st("gw", Fmt.num(l.mittel24 / 1000.0, 1)))
                    }
                    val verlauf = remember(reihen) { StromMarkt.erneuerbarVerlauf(reihen) }
                    if (verlauf.size >= 12) {
                        LbUnterkopf(st("erz.verlauf"))
                        StromKurve(verlauf.map { it.second }, Fmt.dayMonthNum(verlauf.first().first), Fmt.dayMonthNum(verlauf.last().first)) { Fmt.pct(it, 0) }
                    }
                    LbHinweis(st("erz.bis", Fmt.dateTime(mix.bis + STUNDE_MS)))
                    erzeugung?.meldung?.let { StromMeldung(it, 0L) }
                    QuellenLeiste(listOf(st("quelle.smard") to StromMarkt.SMARD_SEITE))
                }
                !erzeugungFertig ->
                    Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                else -> StromMeldung(erzeugung?.meldung ?: StromAbruf.Meldung.KEINE_DATEN, 0L)
            }
        }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Meldung zum letzten Abruf in der Fehlerfarbe, sonst bei bekanntem Stand dessen Zeitpunkt
@Composable
private fun StromMeldung(m: StromAbruf.Meldung?, stand: Long) {
    if (m != null) {
        Text(
            st(meldungSchluessel(m)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
    } else if (stand > 0L) LbHinweis(st("stand", Fmt.dateTime(stand)))
}

// Veraenderung in Prozent mit gezeichnetem Dreieck, Bezeichnung links und Wert rechts in einer Zeile
@Composable
private fun StromAenderung(label: String, prozent: Double) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        ChangeValue(prozent, Fmt.pct(abs(prozent), 1), style = MaterialTheme.typography.bodyMedium)
    }
}

// Ohne Token: eine Zeile, die zu den Zugaengen fuehrt
@Composable
private fun StromOhneToken(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(st("ohne"), style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link, modifier = Modifier.weight(1f))
        ChevronRight()
    }
}

// Verlaufslinie mit Hoch und Tief am linken Rand (ausserhalb der Daten) und dem ersten und letzten Tag darunter
@Composable
private fun StromKurve(werte: List<Double>, links: String, rechts: String, format: (Double) -> String) {
    if (werte.size < 2) return
    val lo = werte.min()
    val hi = werte.max()
    val linie = MaterialTheme.colorScheme.primary
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val stil = MaterialTheme.typography.labelSmall
    val messer = rememberTextMeasurer()
    val tHi = remember(hi, stil) { messer.measure(format(hi), stil) }
    val tLo = remember(lo, stil) { messer.measure(format(lo), stil) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val rand = 8.dp.toPx()
            val x0 = 44.dp.toPx()
            val breite = size.width - x0
            val oben = rand
            val unten = size.height - rand
            val spanne = (hi - lo).takeIf { it > 0.0 } ?: 1.0
            fun x(i: Int) = x0 + breite * i / (werte.size - 1)
            fun y(v: Double) = unten - ((v - lo) / spanne).toFloat() * (unten - oben)
            drawLine(grau.copy(alpha = 0.25f), Offset(x0, oben), Offset(size.width, oben), 1.dp.toPx())
            drawLine(grau.copy(alpha = 0.25f), Offset(x0, unten), Offset(size.width, unten), 1.dp.toPx())
            val pfad = Path()
            werte.forEachIndexed { i, v -> if (i == 0) pfad.moveTo(x(i), y(v)) else pfad.lineTo(x(i), y(v)) }
            val flaeche = Path().apply {
                addPath(pfad)
                lineTo(x(werte.size - 1), unten)
                lineTo(x(0), unten)
                close()
            }
            drawPath(flaeche, linie.copy(alpha = 0.12f))
            drawPath(pfad, linie, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawText(tHi, color = grau, topLeft = Offset(0f, (oben - tHi.size.height / 2f).coerceAtLeast(0f)))
            drawText(tLo, color = grau, topLeft = Offset(0f, (unten - tLo.size.height / 2f).coerceAtMost(size.height - tLo.size.height)))
        }
        Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(links, style = stil, color = grau, maxLines = 1, softWrap = false)
            Text(rechts, style = stil, color = grau, maxLines = 1, softWrap = false)
        }
    }
}
