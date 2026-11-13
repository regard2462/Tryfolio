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
import de.troetenrepublik.app.engine.NasdaqGriechen
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.Optionszeile
import de.troetenrepublik.app.engine.Optionsrechner
import de.troetenrepublik.app.i18n.S

// Abschnitt „Optionen“ auf der Wertpapierseite: Put/Call-Verhaeltnis, erwartete Bewegung, Max Pain, implizite
// Volatilitaet (IV30) und auffaellige Optionen aus der Optionskette (nur Werte mit US-Optionen).
// hv: Schwankung der Aktie im letzten Jahr zum Vergleich mit der impliziten Volatilitaet.
@Composable
internal fun OptionenKarte(state: AppState, symbol: String, waehrung: String, hv: Double?, kopf: Boolean = true, onDerivat: ((String) -> Unit)? = null) {
    var daten by remember(symbol) { mutableStateOf<Pair<Double, Optionsanalyse.Ergebnis>?>(null) }
    var still by remember(symbol) { mutableStateOf<Optionsanalyse.Stillhalter?>(null) }
    var kontrakte by remember(symbol) { mutableStateOf<List<Optionsanalyse.Kontrakt>>(emptyList()) }
    // Gemerkt, damit der Rechner nach einer aus ihm geoeffneten Seite mit dem Blatt wiederkommt
    var rechner by androidx.compose.runtime.saveable.rememberSaveable(symbol) { mutableStateOf(false) }
    // Aus einer Zeile „Optionen verkaufen“ geoeffnet: der Rechner startet mit dieser Option
    var rechnerStart by remember(symbol) { mutableStateOf<de.troetenrepublik.app.engine.OptionsrechnerPlan.Start?>(null) }
    // Kuerzel der US-Notierung, wenn die Kette ueber sie laeuft; ihre Preise stehen dann in US-Dollar
    var us by remember(symbol) { mutableStateOf<String?>(null) }
    var kette by remember(symbol) { mutableStateOf<Pair<Double, List<Optionsanalyse.Kontrakt>>?>(null) }
    var gammas by remember(symbol) { mutableStateOf<Map<NasdaqGriechen.Schluessel, Double>>(emptyMap()) }
    LaunchedEffect(symbol) {
        val k = runCatching { state.market.optionskette(symbol) }.getOrNull() ?: return@LaunchedEffect
        daten = k.first to Optionsanalyse.auswerten(k.first, k.second)
        still = Optionsanalyse.stillhalter(k.first, k.second, zins = state.riskFree)
        kontrakte = k.second
        kette = k
        us = state.market.optionsNotierung(symbol)
        // Gamma vom Nasdaq, sobald die Kette steht; ohne Antwort bleibt es bei der Naeherung
        gammas = state.nasdaqGammas(symbol, k.second)
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
                if (wert.isNotEmpty()) {
                    Text(wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(6.dp))
                }
                AufklappPfeil(auf)
            }
            if (auf) StichpunktText(S.opErklaerung(schluessel), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
        }
    }

    if (kopf) SectionHeader(S.opTitel)
    us?.let {
        Text(S.opUsNotierung(it), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    // Put/Call nach Umsatz und nach offenen Kontrakten als je ein Anteilsbalken
    e.pcrUmsatz?.let { u ->
        Zeile("pcr", S.opPcr, "")
        PcrBalken(S.akVolumen, u)
        e.pcrOffen?.let { PcrBalken(S.opPcrOffen, it) }
    }
    e.bewegung?.let { b -> Zeile("bewegung", S.opBewegung(tag(e.bewegungVerfall)), "±" + preis(b) + " (±" + Fmt.pct(b / kurs * 100, 1) + ")") }
    // Die weiteren Verfaelle; der erste steht schon in der Zeile darueber
    val jeVerfall = remember(kontrakte, e.bewegungVerfall) {
        Optionsrechner.bewegungen(kurs, kontrakte, System.currentTimeMillis() / 1000).filter { e.bewegung == null || it.verfall != e.bewegungVerfall }
    }
    if (jeVerfall.isNotEmpty()) BewegungTabelle(jeVerfall, waehrung)
    e.maxPain?.let { m -> Zeile("maxpain", S.opMaxPain(tag(e.maxPainVerfall)), preis(m)) }
    e.iv30?.let { iv ->
        Zeile("iv", S.impliedVol, Fmt.pct(iv * 100, 1))
        hv?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.opHvLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Fmt.pct(it * 100, 1), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
    if (e.auffaellig.isNotEmpty()) {
        Zeile("auffaellig", S.opAuffaellig, e.auffaellig.size.toString())
        e.auffaellig.forEachIndexed { i, a ->
            if (i > 0) Trennlinie()
            val k = a.kontrakt
            KontraktTitel(k.call, preis(k.strike), k.verfall * 1000, Modifier.padding(top = 8.dp), zusatz = S.ozSeite(a.seite.name))
            WertePaare(
                listOf(
                    WertPaar(S.akVolumen, Fmt.num(k.umsatz.toDouble(), 0)),
                    WertPaar(S.akOffen, Fmt.num(k.offen.toDouble(), 0)),
                    WertPaar(S.ozGehandelt, Fmt.dollarKompakt(a.praemie)),
                ),
                Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }
    // Optionen verkaufen (5.94): je Seite eine kleine Tabelle mit festen Spalten, damit nichts umbricht
    still?.let { st ->
        Zeile("stillhalter", S.opStillhalter, tag(st.verfall) + " (" + S.opTage(st.tage) + ")")
        if ("stillhalter" in offen) {
            @Composable
            fun Tabelle(titel: String, zeilen: List<Optionsanalyse.StillhalterZeile>, call: Boolean) {
                if (zeilen.isEmpty()) return
                Text(titel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp))
                // Dieselbe Angabe wie im Werkzeug „Optionen verkaufen“; ein Tipp oeffnet den Rechner mit dieser Option
                FesteTabelle(
                    listOf(S.opBasis, S.opPraemie, S.opRenditeJahr, S.slAusserhalb),
                    zeilen.map { z ->
                        listOf(
                            preis(z.strike), Fmt.priceStellen(z.praemie, waehrung, Optionszeile.praemieStellen(z.praemie)),
                            Fmt.pct(z.jahr * 100, 1), z.wertlos?.let { Fmt.pct(it * 100, 0) } ?: "–",
                        )
                    },
                    onZeile = { i ->
                        rechnerStart = de.troetenrepublik.app.engine.OptionsrechnerPlan.Start(call, zeilen[i].strike, st.verfall)
                        rechner = true
                    },
                )
            }
            Tabelle(S.opStillCalls, st.calls, true)
            Tabelle(S.opStillPuts, st.puts, false)
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
    kette?.let { (k, kontrakte) ->
        OptionsEbenenBlock(k, kontrakte, e.maxPain, waehrung, gammas)
        OptionsVolaBlock(state, symbol, k, kontrakte)
    }
    NavRow(S.orKarte) { rechnerStart = null; rechner = true }
    ToolNote(S.opStand)
    if (rechner) OptionsrechnerBlatt(state, symbol, onDismiss = { rechner = false }, onDerivat = onDerivat, start = rechnerStart)
}
