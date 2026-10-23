package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.i18n.S

// Abschnitt „Optionen“ auf der Wertpapierseite: Put/Call-Verhaeltnis, erwartete Bewegung, Max Pain, implizite
// Volatilitaet und auffaellige Umsaetze aus der Optionskette (nur Werte mit US-Optionen).
// hv: Schwankung der Aktie im letzten Jahr zum Vergleich mit der impliziten Volatilitaet.
@Composable
internal fun OptionenKarte(state: AppState, symbol: String, waehrung: String, hv: Double?) {
    var daten by remember(symbol) { mutableStateOf<Pair<Double, Optionsanalyse.Ergebnis>?>(null) }
    LaunchedEffect(symbol) {
        val k = runCatching { state.market.optionskette(symbol) }.getOrNull() ?: return@LaunchedEffect
        daten = k.first to Optionsanalyse.auswerten(k.first, k.second)
    }
    var offen by remember(symbol) { mutableStateOf<Set<String>>(emptySet()) }
    val d = daten ?: return
    val kurs = d.first
    val e = d.second
    if (e.pcrUmsatz == null && e.maxPain == null && e.bewegung == null) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    fun preis(v: Double) = Fmt.price(v, waehrung)
    fun tag(s: Long?) = s?.let { Fmt.dayMonth(it * 1000) } ?: ""

    @Composable
    fun Zeile(schluessel: String, titel: String, wert: String) {
        val auf = schluessel in offen
        Column(Modifier.fillMaxWidth().clickable { offen = if (auf) offen - schluessel else offen + schluessel }.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                ChevronDown(width = 9.dp, color = grau.copy(alpha = 0.45f), modifier = Modifier.rotate(if (auf) 180f else 0f))
            }
            if (auf) Text(S.opErklaerung(schluessel), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
        }
    }

    SectionHeader(S.opTitel)
    e.pcrUmsatz?.let { u -> Zeile("pcr", S.opPcr, Fmt.num(u, 2) + (e.pcrOffen?.let { " · " + Fmt.num(it, 2) } ?: "")) }
    e.bewegung?.let { b -> Zeile("bewegung", S.opBewegung(tag(e.bewegungVerfall)), "±" + preis(b) + " (±" + Fmt.pct(b / kurs * 100, 1) + ")") }
    e.maxPain?.let { m -> Zeile("maxpain", S.opMaxPain(tag(e.maxPainVerfall)), preis(m)) }
    e.ivAmGeld?.let { iv -> Zeile("iv", S.opIv, Fmt.pct(iv * 100, 0) + (hv?.let { " · " + S.opHv(Fmt.pct(it * 100, 0)) } ?: "")) }
    if (e.auffaellig.isNotEmpty()) {
        Zeile("auffaellig", S.opAuffaellig, e.auffaellig.size.toString())
        e.auffaellig.forEach { a ->
            val k = a.kontrakt
            Text(
                S.opAuffaelligZeile(
                    if (k.call) "Call" else "Put", preis(k.strike), tag(k.verfall), Fmt.num(k.umsatz.toDouble(), 0), Fmt.num(k.offen.toDouble(), 0),
                    Fmt.dollarKompakt(a.praemie), a.seite.name,
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
            )
        }
    }
    Text(S.opQuelle, style = MaterialTheme.typography.labelSmall, color = grau.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
}
