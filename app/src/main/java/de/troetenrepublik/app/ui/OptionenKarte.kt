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
internal fun OptionenKarte(state: AppState, symbol: String, waehrung: String, hv: Double?, kopf: Boolean = true) {
    var daten by remember(symbol) { mutableStateOf<Pair<Double, Optionsanalyse.Ergebnis>?>(null) }
    var still by remember(symbol) { mutableStateOf<Optionsanalyse.Stillhalter?>(null) }
    // Kuerzel der US-Notierung, wenn die Kette ueber sie laeuft; ihre Preise stehen dann in US-Dollar
    var us by remember(symbol) { mutableStateOf<String?>(null) }
    LaunchedEffect(symbol) {
        val k = runCatching { state.market.optionskette(symbol) }.getOrNull() ?: return@LaunchedEffect
        daten = k.first to Optionsanalyse.auswerten(k.first, k.second)
        still = Optionsanalyse.stillhalter(k.first, k.second)
        us = state.market.optionsNotierung(symbol)
    }
    var offen by remember(symbol) { mutableStateOf<Set<String>>(emptySet()) }
    val d = daten ?: return
    val waehrung = if (us != null) "USD" else waehrung
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
                Text(wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(6.dp))
                ChevronDown(width = 9.dp, color = grau.copy(alpha = 0.45f), modifier = Modifier.rotate(if (auf) 180f else 0f))
            }
            if (auf) Text(S.opErklaerung(schluessel), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
        }
    }

    if (kopf) SectionHeader(S.opTitel)
    us?.let {
        Text(S.opUsNotierung(it), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    e.pcrUmsatz?.let { u -> Zeile("pcr", S.opPcr, Fmt.num(u, 2) + (e.pcrOffen?.let { " · " + Fmt.num(it, 2) } ?: "")) }
    e.bewegung?.let { b -> Zeile("bewegung", S.opBewegung(tag(e.bewegungVerfall)), "±" + preis(b) + " (±" + Fmt.pct(b / kurs * 100, 1) + ")") }
    e.maxPain?.let { m -> Zeile("maxpain", S.opMaxPain(tag(e.maxPainVerfall)), preis(m)) }
    e.ivAmGeld?.let { iv -> Zeile("iv", S.impliedVol, Fmt.pct(iv * 100, 0) + (hv?.let { " · " + S.opHv(Fmt.pct(it * 100, 0)) } ?: "")) }
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
    // Stillhalter-Rechner (5.94): je Seite eine kleine Tabelle mit festen Spalten, damit nichts umbricht
    still?.let { st ->
        Zeile("stillhalter", S.opStillhalter, tag(st.verfall) + " · " + S.opTage(st.tage))
        if ("stillhalter" in offen) {
            @Composable
            fun Tabelle(titel: String, zeilen: List<Optionsanalyse.StillhalterZeile>) {
                if (zeilen.isEmpty()) return
                Text(titel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp))
                @Composable
                fun Reihe(a: String, b: String, c: String, d: String, kopfzeile: Boolean) {
                    val st2 = if (kopfzeile) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
                    val farbe = if (kopfzeile) grau else MaterialTheme.colorScheme.onSurface
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
                        listOf(a, b, c, d).forEachIndexed { i, t ->
                            Text(
                                t, style = st2, color = farbe, maxLines = 1, softWrap = false,
                                textAlign = if (i == 0) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Reihe(S.opBasis, S.opPraemie, S.opRenditeJahr, S.opAusuebung, true)
                zeilen.forEach { z ->
                    Reihe(preis(z.strike), preis(z.praemie), Fmt.pct(z.jahr * 100, 1), z.wahrscheinlich?.let { Fmt.pct(it * 100, 0) } ?: "–", false)
                }
            }
            Tabelle(S.opStillCalls, st.calls)
            Tabelle(S.opStillPuts, st.puts)
            val c = st.calls.firstOrNull()
            val p = st.puts.firstOrNull()
            Text(
                S.opStillBeispiel(
                    c?.let { listOf(preis(it.strike), preis(it.praemie), Fmt.pct(it.rendite * 100, 1), Fmt.pct((it.ausgeuebt ?: 0.0) * 100, 1), preis(it.schwelle)) },
                    p?.let { listOf(preis(it.strike), preis(it.praemie), Fmt.pct(it.rendite * 100, 1), preis(it.schwelle)) },
                ),
                style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
    }
    QuellenZeile(S.opQuelle)
}
