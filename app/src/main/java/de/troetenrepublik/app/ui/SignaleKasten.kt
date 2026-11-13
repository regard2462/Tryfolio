package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.widgetSignaleAblegen
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.analystenDaten
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.InsiderTrend
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.Signale
import de.troetenrepublik.app.engine.Trendverlauf
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Bafin
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.SecInsider

// Signale-Zeile unter dem Chart (0.77, vorher ein Kasten ueber dem Chart seit 0.76): Insider 90 Tage, Kongress 90 Tage,
// Hoch- und Abstufungen 30 Tage, auffaellige Optionen. Zeilen ohne Daten entfallen, ohne Zeile entfaellt die ganze
// Zeile. Keine Wertung: Das Dreieck zeigt nur, auf welcher Seite die Zahl groesser ist (ohne Seite ein grauer Punkt).
// Der Knopf steht zwischen „Indikatoren“ und „Erweiterter Chart“, aufgeklappt folgen die Zeilen darunter.
private enum class Feld { INSIDER, KONGRESS, STUFEN, OPTIONEN }

// Trends knapp (0.77): Rang in der Reddit-Liste und Erwaehnungen dieser Woche gegen die Vorwoche. Nur in der
// aufgeklappten Zeile, geladen erst beim Aufklappen; nicht in der Waage (kurzfristig).
private class TrendKurz(val rang: Int, val aenderung: Double?)

private suspend fun trendKurz(state: AppState, symbol: String, name: String, krypto: Boolean): TrendKurz? {
    val quelle = if (krypto) "all-crypto" else "all-stocks"
    val liste = state.redditTrends(quelle)
    val archiv = state.trendArchiv()
    val namen = archiv.namen + liste.associate { Trendverlauf.normal(it.ticker) to it.name }
    val kuerzel = Trendverlauf.kuerzelFuer(symbol, name, namen) ?: return null
    val live = liste.firstOrNull { Trendverlauf.normal(it.ticker) == kuerzel } ?: return null
    val heute = Trendverlauf.tag(System.currentTimeMillis())
    val reihe = Trendverlauf.reihe(archiv, quelle, kuerzel, REDDIT_ZEITEN.last(), heute)
    val seit = Trendverlauf.seit(archiv, quelle)
    val woche = reihe.takeLast(7).sum()
    val vorwoche = reihe.takeLast(14).take(7).sum()
    // Die Vorwoche zaehlt nur, wenn die App sie schon mitgeschrieben hat
    val aenderung = if (seit != null && heute - seit >= 13 && vorwoche > 0) (woche.toDouble() / vorwoche - 1) * 100 else null
    return TrendKurz(live.rang, aenderung)
}

// Fenster der Zeilen Insider und Kongress: 90 Tage bis zum Ende der Insider-Daten, wie bei der Konvergenz.
internal fun signalFenster(sec: SecInsider.Datei?): de.troetenrepublik.app.engine.Konvergenz.Fenster =
    Signale.fenster(sec?.let { standEnde(it) }, de.troetenrepublik.app.engine.Uhr.heute())

// Insider-Trend der Zeile aus den Staenden auf dem Geraet. Kasten, Waage und Widget rechnen mit dieser Funktion; die
// Karten im Blatt dahinter nehmen dieselben Geschaefte und dasselbe Fenster.
internal fun signalInsider(
    sec: SecInsider.Datei?, bafin: Bafin.Datei?, eu: Map<String, de.troetenrepublik.app.market.EuInsider.Datei>,
    welt: Map<String, Pair<String, List<de.troetenrepublik.app.market.EuInsider.Geschaeft>>>, symbol: String, isin: String,
): InsiderTrend.Ergebnis? = Signale.insider(
    sec?.geschaefte?.filter { it.k.equals(symbol, true) },
    bafin?.eigengeschaefte?.filter { (isin.isNotBlank() && it.isin == isin) || it.k == symbol },
    welt[symbol]?.second ?: eu.values.map { d -> d.geschaefte.filter { isin.isNotBlank() && it.isin == isin } }.maxByOrNull { it.size },
    sec?.let { standEnde(it) }, de.troetenrepublik.app.engine.Uhr.heute(),
)

// Kongress-Zeile: Geschaefte von Haus und Senat, die im Fenster gemeldet wurden.
internal suspend fun signalKongress(state: AppState, symbol: String): Signale.Kongress? =
    Signale.kongress(HandelndeLader.laden(state, symbol).eintraege, signalFenster(state.sec.value))

// Stand der vier Signale; die Waage unter „Chancen und Risiken“ nimmt Insider, Kongress und Stufen aus derselben Rechnung.
internal class SignaleStand(
    val insider: InsiderTrend.Ergebnis?, val kongress: Signale.Kongress?, val stufen: Signale.Stufen?, val optionen: Signale.Optionen?,
) {
    // Richtung je Signal in der Reihenfolge der Zeilen: > 0 links (gruen), < 0 rechts (rot), 0 ohne Seite
    val richtungen: List<Double>
        get() = listOfNotNull(
            insider?.let { it.kaufBetrag - it.verkaufBetrag },
            kongress?.let { (it.kaeufe - it.verkaeufe).toDouble() },
            stufen?.let { (it.hoch - it.ab).toDouble() },
            optionen?.let { 0.0 },
        )
}

@Composable
internal fun rememberSignale(state: AppState, symbol: String, isin: String, profile: MarketApi.Profile?, usWert: Boolean, aktiv: Boolean): SignaleStand? {
    // Nur die Staende auf dem Geraet, ohne Abruf, wie bei Chancen und Risiken
    LaunchedEffect(symbol, isin, aktiv) {
        if (!aktiv) return@LaunchedEffect
        runCatching { state.secOhneNetz() }
        runCatching { state.bafinLaden(netz = false) }
        if (isin.length == 12) {
            de.troetenrepublik.app.market.EuInsider.landFuerIsin(isin)?.let { runCatching { state.euLaden(it, netz = false) } }
        }
    }
    val sec by state.sec.collectAsStateWithLifecycle()
    val bafin by state.bafin.collectAsStateWithLifecycle()
    val eu by state.euInsider.collectAsStateWithLifecycle()
    val welt by state.weltInsider.collectAsStateWithLifecycle()
    val jetzt = remember { System.currentTimeMillis() }
    val insider: InsiderTrend.Ergebnis? = remember(sec, bafin, eu, welt, symbol, isin) { signalInsider(sec, bafin, eu, welt, symbol, isin) }
    // Das Fenster haengt am Stand der Insider-Daten: mit neuem Stand neu zaehlen
    val kongress by produceState<Signale.Kongress?>(null, symbol, usWert, aktiv, sec?.stand) {
        value = if (!aktiv || !usWert) null else runCatching { signalKongress(state, symbol) }.getOrNull()
    }
    val stufen by produceState<Signale.Stufen?>(null, symbol, profile?.country, aktiv) {
        value = if (!aktiv) null else runCatching { state.analystenDaten(symbol, profile?.country) }.getOrNull()?.let { Signale.stufungen(it.verlauf.urteile, jetzt) }
    }
    val optionen by produceState<Signale.Optionen?>(null, symbol, usWert, aktiv) {
        value = if (!aktiv || !usWert) null else runCatching { state.market.optionskette(symbol) }.getOrNull()
            ?.let { Signale.optionen(Optionsanalyse.auswerten(it.first, it.second)) }
    }
    val ins = insider?.takeIf { it.kaeufer + it.verkaeufer > 0 }
    // Ein Signale-Widget zu diesem Wert zeigt denselben Stand wie die Seite
    LaunchedEffect(symbol, aktiv, ins, kongress, stufen, optionen) {
        if (aktiv) runCatching { state.widgetSignaleAblegen(symbol, de.troetenrepublik.app.widgetSignaleZeilen(ins, kongress, stufen, optionen)) }
    }
    return remember(aktiv, ins, kongress, stufen, optionen) {
        if (!aktiv || (ins == null && kongress == null && stufen == null && optionen == null)) null else SignaleStand(ins, kongress, stufen, optionen)
    }
}

// Knopf in der Zeile unter dem Chart: nur das Wort „Signale“, ein Tipp klappt die Liste auf.
@Composable
internal fun SignaleKnopf(state: AppState, symbol: String, stand: SignaleStand, offen: Boolean, aufZu: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.signale(symbol)) {
        Row(
            Modifier.textKlick(4.dp, onClick = aufZu).heightIn(min = 48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Schlichter Textknopf wie „Indikatoren“ und „Erweiterter Chart“: ohne Richtungszeichen und ohne Pfeil
            Text(S.sigTitel, style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        }
    }
}

// Aufgeklappte Zeilen mit Einzelheiten per Tipp
@Composable
internal fun SignaleListe(
    state: AppState, symbol: String, isin: String, name: String, quote: Quote?, profile: MarketApi.Profile?, stand: SignaleStand,
    usWert: Boolean, euInsider: Boolean, seite: SeitenWaehrung, krypto: Boolean = false, onTrends: (() -> Unit)? = null, onOpenRoute: (String) -> Unit,
) {
    var offen by remember(symbol) { mutableStateOf<Feld?>(null) }
    val trend by produceState<TrendKurz?>(null, symbol) { value = runCatching { trendKurz(state, symbol, name, krypto) }.getOrNull() }
    // Seltener Insiderkauf (nur US-Meldungen): eine kleine Zeile an der Insider-Zeile, die Einzelheiten stehen im Blatt
    val sec by state.sec.collectAsStateWithLifecycle()
    val selten by produceState<String?>(null, symbol, sec, usWert) {
        val d = sec
        value = if (!usWert || d == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { seltenFuerWert(d, d.geschaefte.filter { it.k.equals(symbol, true) }).firstOrNull()?.let { seltenText(it) } }.getOrNull()
        }
    }
    // Dollarbetraege in der Anzeigewaehrung wie die Praemie der Optionen, damit nicht Dollar neben Euro steht
    val geld: (Double) -> String = { Fmt.dollarKompakt(it) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        if (usWert) KonvergenzKern(state, symbol) { onOpenRoute(CopyRouten.KONVERGENZ) }
        stand.insider?.let { i ->
            val netto = i.kaufBetrag - i.verkaufBetrag
            SignalZeile(
                S.sgInsider, S.sgInsiderWert(i.kaeufer, i.verkaeufer), if (usWert && netto != 0.0) S.sgNetto((if (netto > 0) "+" else "−") + geld(kotlin.math.abs(netto))) else null,
                netto, i.kaufBetrag.toFloat(), i.verkaufBetrag.toFloat(), unter2 = selten, faerben = true,
            ) { offen = Feld.INSIDER }
        }
        stand.kongress?.let { k ->
            SignalZeile(
                S.sgKongress, S.sgKaeufeVerkaeufe(k.kaeufe, k.verkaeufe), null,
                (k.kaeufe - k.verkaeufe).toDouble(), k.kaeufe.toFloat(), k.verkaeufe.toFloat(), faerben = true,
            ) { offen = Feld.KONGRESS }
        }
        stand.stufen?.let { s ->
            SignalZeile(
                S.sgStufungenKurz, S.sgHochAbKurz(s.hoch, s.ab), null,
                (s.hoch - s.ab).toDouble(), s.hoch.toFloat(), s.ab.toFloat(), faerben = true,
            ) { offen = Feld.STUFEN }
        }
        stand.optionen?.let { o ->
            SignalZeile(
                S.sgOptionen, S.sgOptionenWert(o.anzahl, o.calls, o.puts), S.sgPraemie(Fmt.dollarKompakt(o.praemie)),
                0.0, o.calls.toFloat(), o.puts.toFloat(), faerben = true,
            ) { offen = Feld.OPTIONEN }
        }
        trend?.let { t ->
            SignalZeile(
                S.sgTrends, S.sgTrendWert(t.rang, t.aenderung?.let { Fmt.signedPct(it, 0) }), null,
                t.aenderung ?: 0.0, 0f, 0f,
            ) { onTrends?.invoke() ?: onOpenRoute("maerkte/trends") }
        }
    }

    val schliessen = { offen = null }
    // Oeffnet ein Blatt eine Unterseite, wartet es und steht nach dem Zurueck wieder an derselben Stelle da
    if (offen != null) WiederkehrBlatt("signale-detail") { w -> when (offen) {
        Feld.INSIDER -> InsiderBlatt(state, symbol, isin, name, usWert, euInsider, oeffnen = { w.verlassen(); onOpenRoute(it) }, onDismiss = schliessen)
        Feld.KONGRESS -> KongressBlatt(state, symbol, name, usWert, stand.kongress, oeffnen = { w.verlassen(); onOpenRoute(it) }, onDismiss = schliessen)
        Feld.STUFEN -> AnalystenBlatt(state, symbol, name, profile, seite, oeffnen = { w.verlassen(); onOpenRoute(it) }) { offen = null }
        Feld.OPTIONEN -> DetailBlatt(S.opTitel, name, schliessen) {
            val hv by produceState<Double?>(null, symbol) { value = runCatching { state.historischeVol(symbol) }.getOrNull() }
            OptionenKarte(state, symbol, quote?.nativeCurrency?.ifBlank { null } ?: "USD", hv, kopf = false, onDerivat = { w.verlassen(); onOpenRoute("derivative/$it") })
        }
        null -> {}
    } }
}

// Eine kompakte Zeile: Dreieck fuer die groessere Seite, Bezeichnung links, Zahl rechts, optional eine kleine Zeile
// darunter, dazu ein duenner Balken aus der linken (gruen) und rechten Seite (rot). richtung 0: kein Dreieck.
@Composable
internal fun SignalZeile(titel: String, wert: String, unter: String?, richtung: Double, links: Float, rechts: Float, unter2: String? = null, faerben: Boolean = false, onClick: (() -> Unit)? = null) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row {
            Box(Modifier.width(14.dp).align(Alignment.CenterVertically)) { DirectionTriangle(richtung) }
            Text(titel, Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            // Links grün, rechts rot wie der Balken darunter (Käufer und Verkäufer, Calls und Puts)
            if (faerben) Text(seitenFarben(wert, signal.up, signal.down, grau), Modifier.alignByBaseline(), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            else Text(wert, Modifier.alignByBaseline(), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
        }
        unter?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(start = 14.dp)) }
        unter2?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(start = 14.dp)) }
        val summe = links + rechts
        if (summe > 0f) {
            val anteil = links / summe
            Row(Modifier.padding(start = 14.dp, top = 3.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))) {
                if (anteil > 0f) Box(Modifier.weight(anteil).fillMaxHeight().background(signal.up))
                if (anteil < 1f) Box(Modifier.weight(1f - anteil).fillMaxHeight().background(signal.down))
            }
        }
    }
}

// Wert mit zwei Seiten, durch " · " getrennt ("3 Käufer · 1 Verkäufer", "5 · 3 Calls · 2 Puts"): die vorletzte Angabe grün,
// die letzte rot, ein davorstehender Gesamtwert und die Trennzeichen in der Textfarbe.
internal fun seitenFarben(wert: String, hoch: androidx.compose.ui.graphics.Color, runter: androidx.compose.ui.graphics.Color, trenner: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
    val teile = wert.split(" · ")
    if (teile.size < 2) return androidx.compose.ui.text.AnnotatedString(wert)
    return androidx.compose.ui.text.buildAnnotatedString {
        teile.forEachIndexed { i, t ->
            if (i > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = trenner)) { append(" · ") }
            when (i) {
                teile.size - 2 -> withStyle(androidx.compose.ui.text.SpanStyle(color = hoch)) { append(t) }
                teile.size - 1 -> withStyle(androidx.compose.ui.text.SpanStyle(color = runter)) { append(t) }
                else -> append(t)
            }
        }
    }
}
