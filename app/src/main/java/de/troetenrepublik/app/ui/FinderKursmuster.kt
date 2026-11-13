package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.KursScanLauf
import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.KursScan
import de.troetenrepublik.app.engine.KursScan.Muster
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.horizontalScrollMaus

// Ansicht „Kursmuster“ im Aktienfinder (0.78): Scan der groessten liquiden Werte des gewaehlten Markts, dazu Depot und
// Watchlist, nach fuenf Mustern auf Tagesschlusskursen. Je Muster die Treffer mit Kennzahl, Kurs und Tagesaenderung und
// eine Zeile, wie es nach dem Muster im letzten Jahr weiterging.

// Wahl der Ansicht; bleibt erhalten, solange die App laeuft.
private object KursmusterWahl {
    var muster by mutableStateOf(Muster.HOCH20)
    var minKurs by mutableDoubleStateOf(KursScan.MIN_KURS)
    var minVolumen by mutableDoubleStateOf(KursScan.MIN_VOLUMEN)
}

private fun knapp(t: String) = t.replace(Regex("[.,]0(?=\\D|$)"), "")

private fun kursLabel(v: Double) = if (v <= 0) S.km("kursJeder") else S.km("kursAb", listOf(Fmt.euro(v, 0)))

private fun volumenLabel(v: Double) = if (v <= 0) S.km("volumenJedes") else S.km("volumenAb", listOf(knapp(Fmt.compact(v))))

// Kennzahl eines Treffers in Worten.
internal fun kursmusterZahl(m: Muster, t: KursScan.Treffer): String = when (m) {
    Muster.HOCH20, Muster.UEBER_SCHNITT20 -> S.km("zahl." + m.name, listOf(Fmt.pct(t.kennzahl, 1)))
    Muster.FLAGGE -> S.km("zahl." + m.name, listOf(Fmt.pct(t.kennzahl, 0), Fmt.pct(t.zusatz ?: 0.0, 1)))
    Muster.RSI_UEBERVERKAUFT, Muster.RSI_UMKEHR -> S.km("zahl." + m.name, listOf(Fmt.num(t.kennzahl, 0)))
}

@Composable
internal fun FinderKursmusterAnsicht(state: AppState, universum: String, daten: List<Aktienfinder.Daten>, onOpen: (String) -> Unit) {
    val w = KursmusterWahl
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val lauf by KursScanLauf.zustand.collectAsState()
    LaunchedEffect(universum) { KursScanLauf.lesen(state, universum) }
    val meins = lauf.universum == universum
    val stand = if (meins) lauf.stand else null
    // Der Filter des gezeigten Ergebnisses steht in den Pillen
    LaunchedEffect(stand?.zeit) { stand?.let { w.minKurs = it.minKurs; w.minVolumen = it.minVolumen } }
    var zeigen by remember(universum, w.muster) { mutableIntStateOf(20) }

    Row(
        Modifier.horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuePille(kursLabel(w.minKurs), w.minKurs > 0, KursScan.KURS_STUFEN.map { kursLabel(it) }, KursScan.KURS_STUFEN.indexOf(w.minKurs)) {
            w.minKurs = KursScan.KURS_STUFEN[it]
        }
        MenuePille(volumenLabel(w.minVolumen), w.minVolumen > 0, KursScan.VOLUMEN_STUFEN.map { volumenLabel(it) }, KursScan.VOLUMEN_STUFEN.indexOf(w.minVolumen)) {
            w.minVolumen = KursScan.VOLUMEN_STUFEN[it]
        }
    }

    if (lauf.laeuft) {
        val anteil = if (lauf.gesamt > 0) lauf.fertig.toFloat() / lauf.gesamt else 0f
        LinearProgressIndicator(progress = { anteil }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp))
        Text(
            S.km("laedt", listOf(Fmt.num(lauf.fertig.toDouble(), 0), Fmt.num(lauf.gesamt.toDouble(), 0))),
            style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    } else {
        if (meins && lauf.ohneAntwort) EmptyHint(S.km("ohneAntwort"))
        Spacer(Modifier.height(8.dp))
        ActionLineWide(S.km(if (stand == null) "suchen" else "neu")) { KursScanLauf.starten(state, universum, daten, w.minKurs, w.minVolumen) }
    }

    if (stand != null) {
        val jeMuster = remember(stand) { Muster.entries.associateWith { m -> KursScan.sortiert(m, stand.fuer(m)) } }
        val werte = remember(stand) { stand.treffer.map { it.symbol }.distinct().size }
        Text(
            S.km("kern", listOf(Fmt.num(werte.toDouble(), 0), Fmt.num(stand.geprueft.toDouble(), 0))),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
        )
        PunktText(
            listOfNotNull(S.afStand(Fmt.relative(stand.zeit)), stand.ohneKurse.takeIf { it > 0 }?.let { S.km("ohneKurse", listOf(it.toString())) }).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp),
        )
        Row(
            Modifier.horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Muster.entries.forEach { m ->
                Chip(S.km("muster." + m.name) + " " + Fmt.num(jeMuster.getValue(m).size.toDouble(), 0), active = w.muster == m) { w.muster = m }
            }
        }
        val m = w.muster
        Text(S.km("regel." + m.name), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp))
        stand.bilanzFuer(m)?.let { b ->
            // Rendite nach dem Muster gegen alle Werte als zwei Balken, Maßstab der größere Betrag
            val signal = LocalSignal.current
            val hoechste = maxOf(abs(b.mittel), abs(b.markt)).takeIf { it > 0 } ?: 1.0
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp)) {
                Text(S.km("rueck.titel", listOf(Fmt.num(b.faelle.toDouble(), 0))), style = MaterialTheme.typography.bodySmall, color = grau)
                LageBalkenZeile(S.km("rueck.muster"), Fmt.signedPct(b.mittel, 1), abs(b.mittel) / hoechste, signal.forChange(b.mittel), null)
                LageBalkenZeile(S.km("rueck.alle"), Fmt.signedPct(b.markt, 1), abs(b.markt) / hoechste, Kategorie.blau, null)
            }
        }
        val liste = jeMuster.getValue(m)
        if (liste.isEmpty()) EmptyHint(S.km("keine"))
        liste.take(zeigen).forEachIndexed { i, t -> if (i > 0) Trennlinie(); MusterZeile(t, m, onOpen) }
        if (liste.size > zeigen) ActionLineWide(S.km("mehr", listOf(minOf(20, liste.size - zeigen).toString()))) { zeigen += 20 }
        else if (zeigen > 20) ActionLineWide(S.km("weniger")) { zeigen = 20 }
    }
    HinweisInfo(S.km("erklaerung", listOf(KursScan.HOECHSTENS.toString())), kurz = hinweisText("hist"), modifier = Modifier.padding(top = 8.dp))
}

// Ein Treffer: Name und Kennzahl des Musters, rechts Kurs und Tagesaenderung. Ein Tipp oeffnet die Wertpapierseite.
@Composable
private fun MusterZeile(t: KursScan.Treffer, m: Muster, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(t.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleSmall)
            Text(kursmusterZahl(m, t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            t.kurs?.let { Text(Fmt.priceEuro(it), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false) }
            t.tag?.let { ChangeValue(it, Fmt.pct(abs(it), 2), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
