package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Auswertung
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Portfolioverlauf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// So viele der meistgehandelten Werte bekommen Kurse; je 20 eine Sammelabfrage.
private const val AUSWERTUNG_WERTE = 60

// Auswertung der Geschaefte einer Person: Kaeufe und Verkaeufe danach, Groesse und Haeufigkeit, auffaellige
// Monate und Geschaefte vor grossen Kursbewegungen. Kurse: fuenf Jahre Tageskurse, bis zu 20 Werte je Abfrage,
// dazu der S&P 500 als Vergleich.
@Composable
fun AuswertungKarte(state: AppState, geschaefte: List<Kongress.Geschaeft>) {
    val signal = LocalSignal.current
    var ergebnis by remember(geschaefte) { mutableStateOf<Auswertung.Ergebnis?>(null) }
    var laedt by remember(geschaefte) { mutableStateOf(true) }
    LaunchedEffect(geschaefte) {
        if (geschaefte.isEmpty()) { laedt = false; return@LaunchedEffect }
        val werte = Auswertung.werte(geschaefte, AUSWERTUNG_WERTE)
        val symbole = werte.associateWith { CopyRouten.symbol(it) }
        val kerzen = coroutineScope {
            (symbole.values.toList() + "^GSPC").distinct().chunked(20).map { teil -> async { state.schlusskurse(teil, "5y") } }.awaitAll()
        }.fold(HashMap<String, List<de.troetenrepublik.app.data.Candle>>()) { m, x -> m.apply { putAll(x) } }
        ergebnis = withContext(Dispatchers.Default) {
            val kurse = werte.mapNotNull { k -> kerzen[symbole.getValue(k)]?.takeIf { it.size > 20 }?.let { k to Portfolioverlauf.karte(it) } }.toMap()
            Auswertung.rechnen(
                geschaefte, kurse, kerzen["^GSPC"]?.let { Portfolioverlauf.karte(it) },
                { k -> Catalog.industryOf(CopyRouten.symbol(k))?.let { S.industryName(it) } },
                System.currentTimeMillis(),
            )
        }
        laedt = false
    }

    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.awTitle)
        val e = ergebnis
        if (e == null) {
            Text(if (laedt) S.awLoading else S.awNone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        } else {
            AuswertungInhalt(e, signal)
        }
    }
}

// Kaeufe und Verkaeufe danach als kleine Tabelle (Zeitraum, im Plus, besser als der Markt, Mitte),
// Groesse und Haeufigkeit als Wertezeilen, Hinweise als Stichpunkte hinter „Mehr“.
@Composable
private fun AuswertungInhalt(e: Auswertung.Ergebnis, signal: Signal) {
    Column(Modifier.fillMaxWidth()) {
        fun pct(teil: Int, ganz: Int) = Fmt.pct(if (ganz > 0) teil * 100.0 / ganz else 0.0, 0)

        if (e.kaeufe.isNotEmpty()) {
            Unterkopf(S.awKaeufeDanach(e.kaeufe.maxOf { it.anzahl }))
            TabellenZeile(listOf("", S.awImPlus, S.awBesserAlsMarkt, S.awMitte), kopf = true)
            e.kaeufe.forEach { h ->
                TabellenZeile(listOf(S.awHorizon(h.tage), pct(h.treffer, h.anzahl), pct(h.gegenMarkt, h.anzahl), Fmt.signedPct(h.median * 100, 1)),
                    farbe = signal.forChange(h.median))
            }
        }
        if (e.verkaeufe.isNotEmpty()) {
            Unterkopf(S.awVerkaeufeDanach(e.verkaeufe.maxOf { it.anzahl }))
            TabellenZeile(listOf("", S.awGefallen, S.awSchwaecherAlsMarkt, S.awMitte), kopf = true)
            e.verkaeufe.forEach { h ->
                TabellenZeile(listOf(S.awHorizon(h.tage), pct(h.treffer, h.anzahl), pct(h.gegenMarkt, h.anzahl), Fmt.signedPct(h.median * 100, 1)),
                    farbe = signal.forChange(h.median))
            }
        }

        Unterkopf(S.awFrequency)
        val f = e.haeufigkeit
        KeyValueRow(S.awPerMonth, Fmt.num(f.proMonat, 1))
        KeyValueRow(S.awTypical, Fmt.dollarKompakt(f.medianBetrag))
        KeyValueRow(S.awBuyShare, Fmt.pct(f.kaufAnteil * 100, 0))
        // Kauf gegen Verkauf als Zwei-Farben-Balken
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(6.dp).clip(RoundedCornerShape(3.dp))) {
            val kauf = f.kaufAnteil.toFloat().coerceIn(0f, 1f)
            if (kauf > 0f) Box(Modifier.weight(kauf).fillMaxHeight().background(signal.up))
            if (kauf < 1f) Box(Modifier.weight(1f - kauf).fillMaxHeight().background(signal.down))
        }
        // Stärkster Monat und große Geschäfte als eine Zeile
        PunktZeile(
            S.awHaeufTeile(f.staerksterMonat?.let { Fmt.monthYear(it) }, Fmt.num(f.staerksterAnzahl.toDouble(), 0), Fmt.num(f.ueberMillion.toDouble(), 0)),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        if (e.auffaellig.isNotEmpty()) {
            Unterkopf(S.awUnusual)
            e.auffaellig.forEach { m -> KeyValueRow(Fmt.monthYear(m.monat), S.awUnusualKurz(Fmt.num(m.anzahl.toDouble(), 0), Fmt.num(m.schnitt, 0))) }
        }

        e.vorBewegung?.let { v ->
            Unterkopf(S.awBeforeMoves)
            if (v.kaeufe > 0) KeyValueRow(S.awVorAnstieg(v.kaeufe), S.awMitZufall(pct(v.vorAnstieg, v.kaeufe), Fmt.pct(v.basisKauf * 100, 0)))
            if (v.verkaeufe > 0) KeyValueRow(S.awVorRueckgang(v.verkaeufe), S.awMitZufall(pct(v.vorRueckgang, v.verkaeufe), Fmt.pct(v.basisVerkauf * 100, 0)))
        }

        AufklappText(S.awHinweise(e.bewertet, e.werte), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), nachZeile = true)
    }
}

@Composable
private fun Unterkopf(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 4.dp))
}

// Zeile der kleinen Tabelle: erste Spalte breiter, die Zahlen rechtsbuendig; die letzte Spalte in der Farbe der Richtung.
@Composable
private fun TabellenZeile(zellen: List<String>, kopf: Boolean = false, farbe: androidx.compose.ui.graphics.Color? = null) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (kopf) 2.dp else 4.dp)) {
        zellen.forEachIndexed { i, z ->
            if (kopf) {
                Beschriftung(
                    z,
                    style = MaterialTheme.typography.labelSmall,
                    color = grau,
                    textAlign = if (i == 0) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(if (i == 0) 1.1f else 1f),
                )
            } else {
                Text(
                    z,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (i == zellen.lastIndex && farbe != null) farbe else MaterialTheme.colorScheme.onSurface,
                    textAlign = if (i == 0) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(if (i == 0) 1.1f else 1f),
                )
            }
        }
    }
}
