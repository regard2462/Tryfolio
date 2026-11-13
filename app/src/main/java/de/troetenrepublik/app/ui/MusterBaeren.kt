package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Baerenmaerkte
import de.troetenrepublik.app.i18n.S

// Baerenmaerkte des S&P 500 (Zeile im Abschnitt „Muster der Vergangenheit“, ui/MusterVergangenheit.kt): Kernzeile mit dem
// heutigen Abstand zum Hoch und dem Mittel aller Rueckgaenge ab 20 %. Aufgeklappt jede Zeile ein Rueckgang mit Beginn,
// Tiefe (als Balken und Zahl), Monaten bis zum Tief und bis zur Erholung; oben der heutige Abstand im selben Massstab.
@Composable
internal fun BaerZeile(state: AppState, b: Baerenmaerkte.Bild, auf: Boolean, onClick: () -> Unit) {
    // Heutiger Abstand zum Hoch aus der Marktphase (Prozent, hoechstens 0); ohne Kurs bleibt der Wert weg
    var vomHoch by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) { vomHoch = runCatching { state.marktphase() }.getOrNull()?.firstOrNull { it.schluessel == "sp500" }?.vomHoch }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    MusterZeile(
        S.vmBaerName, vomHoch?.let { Fmt.signedPct(it, 1) }.orEmpty(),
        S.vmBaerSatz(b.liste.size, b.vonJahr, Fmt.num(b.mittelTiefe, 0), Baerenmaerkte.monate(b.mittelBisTief)), auf, onClick,
    ) {
        val max = remember(b) { b.liste.maxOf { it.tiefe } }
        BaerTabelle(S.vmBaerBeginn, null, max, S.vmBaerTiefe, S.vmBaerTief, S.vmBaerErholt, kopf = true)
        vomHoch?.let { BaerTabelle(S.vmBaerJetzt, -it, max, Fmt.signedPct(it, 1), "", "", jetzt = true) }
        for (x in b.liste.asReversed()) {
            val d = java.time.LocalDate.ofEpochDay(x.hoch.toLong())
            BaerTabelle(
                "%02d/%d".format(d.monthValue, d.year), x.tiefe, max, Fmt.signedPct(-x.tiefe, 1),
                Baerenmaerkte.monate(x.bisTief).toString(), x.bisErholt?.let { Baerenmaerkte.monate(it).toString() } ?: S.vmBaerOffen,
            )
        }
        Text(S.vmBaerEinheit, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 4.dp))
    }
}

// Eine Tabellenzeile: Beginn, Balken der Tiefe im gemeinsamen Massstab, Tiefe, Monate bis zum Tief, Monate bis zur Erholung.
// Die Spalten der Zahlen haben feste Breiten, damit nichts umbricht; der Balken nimmt den Rest.
@Composable
private fun BaerTabelle(
    beginn: String, tiefe: Double?, max: Double, tiefeText: String, bisTief: String, bisErholt: String, kopf: Boolean = false, jetzt: Boolean = false,
) {
    val stil = if (kopf) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val farbe = if (kopf) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val balken = if (jetzt) MaterialTheme.colorScheme.primary else LocalSignal.current.down.copy(alpha = 0.7f)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(beginn, Modifier.width(60.dp), style = stil, color = farbe, maxLines = 1, softWrap = false)
        Box(Modifier.weight(1f).height(8.dp)) {
            if (tiefe != null && max > 0) Box(
                Modifier.fillMaxWidth((tiefe / max).toFloat().coerceIn(0.01f, 1f)).height(8.dp).clip(RoundedCornerShape(2.dp)).background(balken),
            )
        }
        Text(tiefeText, Modifier.width(68.dp), style = stil, color = farbe, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        Text(bisTief, Modifier.width(44.dp), style = stil, color = farbe, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        Text(bisErholt, Modifier.width(64.dp), style = stil, color = farbe, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
    }
}
