package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Einflussverzeichnis
import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Szenarien
import de.troetenrepublik.app.engine.Waehrungsschock
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.katalogWertbild
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.szenarienMessungen
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// [Szenarien 0.74] "Was waere, wenn": Rechenbeispiele fuer das Depot neben dem Crash-Test. Rechenszenarien zeigen eine
// Spanne aus gemessenen Kursempfindlichkeiten, Ereignisse nur die betroffenen Positionen. Die Rechnung steht in
// engine/Szenarien, die Messungen in SzenarienLader.

private const val SKALA_MIN = 0.05
private const val BEITRAEGE = 5

// Gewaehlte Stufen je Szenario als "OEL=60.0;ZINS=2.0" (uebersteht das Drehen des Geraets)
private fun stufenLesen(t: String): Map<String, Double> =
    t.split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { v -> v[1].toDoubleOrNull()?.let { v[0] to it } } }.toMap()

private fun stufenSchreiben(m: Map<String, Double>): String = m.entries.joinToString(";") { it.key + "=" + it.value }

private fun schockText(s: Szenarien.Szenario, v: Double): String = if (s.punkte) S.szPunkte(v.roundToInt()) else Fmt.signedPct(v, 0)

// "-2 % bis -5 %": ganze Prozent; sind beide Enden gleich gerundet, kommen Stellen dazu
private fun spanneText(von: Double, bis: Double, stellen: Int = 0): String {
    val (a, b) = Szenarien.anzeigeReihenfolge(von, bis)
    var d = stellen
    while (d < 2 && Fmt.signedPct(a * 100, d) == Fmt.signedPct(b * 100, d)) d++
    return S.szSpanne(Fmt.signedPct(a * 100, d), Fmt.signedPct(b * 100, d))
}

@Composable
internal fun SzenarienTool(state: AppState, onOpen: (String) -> Unit, zumWaehrungsschock: (() -> Unit)? = null) {
    val valuation = depotBewertung(state)
    val signal = LocalSignal.current
    val items = valuation.positions.filter { it.value > 0 }
    MarktInfoKopf(S.szTitel, S.szInfo)
    if (items.isEmpty()) { ToolNote(S.ptNoPositions); return }
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val heim = Waehrungsschock.code(LocalFremdesDepot.current?.heim ?: "EUR")
    val direkt = items.filter { !it.position.isDerivative }
    val symbole = remember(direkt.map { it.position.symbol to it.value }) {
        direkt.groupBy { it.position.symbol }.mapValues { (_, l) -> l.sumOf { it.value } }.entries.sortedByDescending { it.value }.map { it.key }
    }
    val schluessel = symbole.joinToString("|")
    var fortschritt by remember { mutableStateOf(0 to 0) }
    var messungen by remember { mutableStateOf<Map<String, Szenarien.Messung>?>(null) }
    LaunchedEffect(schluessel) {
        messungen = null
        messungen = state.szenarienMessungen(symbole) { a, b -> fortschritt = a to b }
    }
    val etf by produceState<Map<String, Map<String, Double>>?>(null, schluessel) {
        value = de.troetenrepublik.app.market.ohneAbbruch { state.etfLaender(items) }.getOrNull()?.first ?: emptyMap()
    }
    val gemessen = messungen
    val verteilung = etf
    if (gemessen == null || verteilung == null) {
        LeerZustand(if (fortschritt.second > 0) S.szFortschritt(fortschritt.first, fortschritt.second) else S.toolLoading)
        return
    }
    val posten = remember(items, quotes, verteilung, heim) {
        items.map { vp ->
            val s = vp.position.symbol
            val waehrung = Waehrungsschock.verteilung(s, quotes[s]?.nativeCurrency?.takeIf { it.isNotBlank() } ?: Symbols.find(s)?.currency ?: "EUR") { k -> verteilung[k] }
            Szenarien.Posten(s, vp.position.name, vp.value, messbar = !vp.position.isDerivative, waehrung = waehrung)
        }
    }
    var wahlText by rememberSaveable { mutableStateOf("") }
    val wahl = remember(wahlText) { stufenLesen(wahlText) }
    fun wert(s: Szenarien.Szenario) = Szenarien.begrenzen(s, wahl[s.name] ?: s.standard)
    val zeilen = remember(posten, gemessen, wahl) {
        Szenarien.Szenario.entries.map { s -> Szenarien.rechnen(s, Szenarien.begrenzen(s, wahl[s.name] ?: s.standard), posten, gemessen) }
    }
    val skala = max(SKALA_MIN, zeilen.filter { it.belegt > 0.0 }.maxOfOrNull { max(abs(it.von), abs(it.bis)) } ?: 0.0)
    zeilen.forEach { z ->
        if (z.szenario.berechenbar) {
            SzenarioZeile(
                z, skala, signal.up, signal.down, onOpen,
                onStufe = { v -> wahlText = stufenSchreiben(wahl + (z.szenario.name to v)) },
            )
        } else {
            val b = remember(posten, gemessen, heim, z.szenario) { Szenarien.betroffene(z.szenario, posten, gemessen, heim) }
            // Beim Dollar steht die reine Umrechnung da, wie sie der Waehrungsschock im Crash-Test rechnet
            val gewaehlt = wert(z.szenario)
            val umrechnung = if (z.szenario == Szenarien.Szenario.DOLLAR) remember(posten, heim, gewaehlt) { Szenarien.dollarUmrechnung(posten, heim, gewaehlt) } else null
            if (b != null) OhneZahlZeile(
                S.szName(z.szenario.name, schockText(z.szenario, gewaehlt)), b, onOpen,
                wert = umrechnung, verweis = if (umrechnung != null) zumWaehrungsschock else null,
                steuerung = { WertWahl(z.szenario, gewaehlt) { v -> wahlText = stufenSchreiben(wahl + (z.szenario.name to v)) } },
            ) { x ->
                listOfNotNull(
                    S.szRichtung(x.richtung).takeIf { it.isNotBlank() },
                    S.szDollarAnteil(Fmt.pct(x.dollar * 100, 0)).takeIf { x.dollar >= 0.005 },
                )
            }
        }
    }

    // Ereignisse ohne Zahl
    val total = items.sumOf { it.value }
    val bestand = remember(direkt, total) {
        direkt.groupBy { it.position.symbol }.map { (s, l) ->
            Bestand(katalogWertbild(s), l.first().position.name, if (total > 0) l.sumOf { it.value } / total else 0.0)
        }
    }
    Zwischenkopf(S.szEreignisse, Modifier.padding(top = 12.dp))
    val index = remember { Einflussverzeichnis.geladen }
    val heute = remember { LocalDate.now().toEpochDay() }
    Szenarien.Ereignis.entries.forEach { e ->
        OhneZahlZeile(S.szEreignis(e.name), remember(bestand, e) { Szenarien.ereignis(e, index, bestand, heute) }, onOpen) { b ->
            listOf(if (b.rohstoffe.isNotEmpty()) S.szBraucht(b.rohstoffe.joinToString(", ") { it.name(istDeutsch()) }) else b.beleg)
        }
    }
    // Verkaufte Optionen zaehlen mit ihrem Betrag zu den nicht berechneten Derivaten
    val alle = valuation.positions.sumOf { abs(it.value) }
    val derivate = if (alle > 0) valuation.positions.filter { it.position.isDerivative }.sumOf { abs(it.value) } / alle else 0.0
    if (derivate > 0.0005) LeerZustand(S.szDerivate(Fmt.pct(derivate * 100, 0)))
}

@Composable
private fun SzenarioZeile(
    z: Szenarien.Zeile, skala: Double, auf: Color, ab: Color, onOpen: (String) -> Unit, onStufe: (Double) -> Unit,
) {
    var offen by rememberSaveable("sz-" + z.szenario.name) { mutableStateOf(false) }
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    val belegt = z.belegt > 0.0005
    val farbe = when {
        !belegt -> sv
        z.von >= 0.0 -> auf
        z.bis <= 0.0 -> ab
        else -> sv
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = !offen }.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.szName(z.szenario.name, schockText(z.szenario, z.schock)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (belegt) spanneText(z.von, z.bis) else S.szKeinBeleg, style = MaterialTheme.typography.bodyMedium, color = farbe,
                    textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                )
                AufklappPfeil(offen, Modifier.padding(start = 10.dp))
            }
            Spacer(Modifier.height(6.dp))
            SpannenBalken(if (belegt) z.von else null, z.bis, skala, farbe)
            Text(
                S.szBelegt(Fmt.pct(z.belegt * 100, 0)), style = MaterialTheme.typography.labelSmall, color = sv,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (offen) {
            WertWahl(z.szenario, z.schock, onStufe)
            if (z.beitraege.isEmpty()) {
                LeerZustand(S.szKeineBelegten)
            } else {
                Zwischenkopf(S.szGroessteBeitraege)
                var alleBeitraege by rememberSaveable("szb-" + z.szenario.name) { mutableStateOf(false) }
                (if (alleBeitraege) z.beitraege else z.beitraege.take(BEITRAEGE)).forEach { b ->
                    WeltZeile(
                        b.posten.name, spanneText(b.von, b.bis, 1), listOf(S.szAnteil(Fmt.pct(b.gewicht * 100, 1))),
                        onClick = { onOpen(b.posten.symbol) },
                    )
                }
                if (z.beitraege.size > BEITRAEGE) ListenPfeil(alleBeitraege, label = if (alleBeitraege) null else S.bbAlle(z.beitraege.size)) { alleBeitraege = !alleBeitraege }
            }
        }
    }
}

// Vorgaben als Auswahl und ein Regler fuer einen eigenen Wert innerhalb der Grenzen des Szenarios
@Composable
private fun WertWahl(s: Szenarien.Szenario, wert: Double, onWert: (Double) -> Unit) {
    if (s.stufen.size > 1) ToolChoice(null, s.stufen.map { schockText(s, it) to it }, wert, onWert)
    SliderRow(S.szEigenerWert, schockText(s, wert), wert.toFloat(), s.min.toFloat()..s.max.toFloat(), s.schritt.toFloat()) { onWert(Szenarien.begrenzen(s, it.toDouble())) }
}

// Schmaler Balken um die Nulllinie: von bis bis (Anteil des Depots) auf gemeinsamer Skala; ohne von nur die Spur
@Composable
private fun SpannenBalken(von: Double?, bis: Double, skala: Double, farbe: Color) {
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    val null_ = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(spur, cornerRadius = r)
        val mitte = size.width / 2f
        if (von != null) {
            fun x(v: Double) = (mitte + (v / skala).coerceIn(-1.0, 1.0).toFloat() * mitte)
            val a = minOf(x(von), x(bis))
            val b = maxOf(x(von), x(bis))
            val breite = (b - a).coerceAtLeast(size.height)
            val links = ((a + b) / 2f - breite / 2f).coerceIn(0f, size.width - breite)
            drawRoundRect(farbe, topLeft = Offset(links, 0f), size = Size(breite, size.height), cornerRadius = r)
        }
        drawRect(null_, topLeft = Offset(mitte - 0.5.dp.toPx(), 0f), size = Size(1.dp.toPx(), size.height))
    }
}

// Zeile ohne Zahl (Ereignis oder nicht berechenbares Szenario): rechts "nicht berechenbar", darunter der Depotanteil der
// betroffenen Positionen; aufgeklappt die Positionen mit ihren Einzelheiten. wert: beim Dollar die reine Umrechnung
// (Anteil des Depots) statt "nicht berechenbar", verweis fuehrt aufgeklappt zum Waehrungsschock im Crash-Test.
@Composable
private fun OhneZahlZeile(
    titel: String, z: Szenarien.EreignisZeile, onOpen: (String) -> Unit, wert: Double? = null, verweis: (() -> Unit)? = null,
    steuerung: (@Composable () -> Unit)? = null,
    einzelheiten: (Szenarien.Betroffen) -> List<String>,
) {
    var offen by rememberSaveable("sz-" + z.kennung) { mutableStateOf(false) }
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    val treffer = z.betroffen.isNotEmpty()
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = !offen }.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (wert != null) Fmt.signedPct(wert * 100, 1) + "  " + S.dp("sz.umrechnung") else S.szNichtBerechenbar, style = MaterialTheme.typography.bodyMedium,
                    color = if (wert != null) LocalSignal.current.forChange(wert) else sv, maxLines = 1, softWrap = false,
                )
                AufklappPfeil(offen, Modifier.padding(start = 10.dp))
            }
            Text(
                if (treffer) S.szBetrifft(Fmt.pct(z.anteil * 100, 0)) else S.szKeinTreffer,
                style = MaterialTheme.typography.labelSmall, color = sv, modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (offen && steuerung != null) steuerung()
        if (offen && verweis != null) NavRow(S.dp("sz.crash"), S.dp("sz.crashUnter"), verweis)
        // [0.75] Abgelaufene Einordnungen (einfluss.json ohne neue Fassung) stehen da, statt dass die Liste still kuerzer wird
        z.abgelaufen?.let { (n, bis) -> WeltZeile(S.abhAbgelaufen, n.toString(), listOf(S.abhAbgelaufenHinweis(Fmt.date(bis * 86_400_000L)))) }
        if (offen && treffer) {
            Zwischenkopf(S.szBetroffenePositionen)
            z.betroffen.forEach { b ->
                WeltZeile(
                    b.name, Fmt.pct(b.gewicht * 100, 1), einzelheiten(b).filter { it.isNotBlank() },
                    b.beleg.takeIf { it.isNotBlank() }?.let { artStand(b.art, null) }, onClick = { onOpen(b.symbol) },
                )
            }
        }
    }
}
