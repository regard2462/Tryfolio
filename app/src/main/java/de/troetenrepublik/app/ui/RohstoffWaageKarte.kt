package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.RohstoffHandel
import de.troetenrepublik.app.engine.RohstoffWaage
import de.troetenrepublik.app.engine.RohstoffWaage.Richtung
import de.troetenrepublik.app.engine.RohstoffWaage.Treiber
import de.troetenrepublik.app.engine.Signale
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.rohstoffEingaben
import kotlin.math.abs

// Fuer und Wider bei Rohstoffen (0.78): Treiber aus Messgroessen mit festen Schwellen (engine/RohstoffWaage.kt). Kernzeile
// mit der Zahl der Punkte dafuer und dagegen, aufgeklappt die Waage und je Treiber eine Zeile mit Zahl und Stand; ein
// Tipp auf einen Punkt zeigt den Wirkungsweg.

internal val EIA_QUELLE = QuelleLink("U.S. Energy Information Administration (EIA)", "https://www.eia.gov")

// Zahl mit Vorzeichen, ohne Einheit
private fun vz(v: Double, stellen: Int): String = (if (v > 0) "+" else if (v < 0) "−" else "±") + Fmt.num(abs(v), stellen)

private fun monatName(ms: Long): String =
    java.time.LocalDate.ofEpochDay(Math.floorDiv(ms, 86_400_000L)).let { Fmt.monthYear(java.time.YearMonth.of(it.year, it.monthValue)) }

// Aussage des Punkts, etwa „Realzins fällt“
private fun aussage(p: RohstoffWaage.Punkt): String {
    val w = when (p.treiber) {
        Treiber.SAISON -> listOf(monatName(p.stand).substringBefore(' '))
        Treiber.ENSO -> listOf(if (p.vergleich > 0) "El Niño" else "La Niña")
        else -> emptyList()
    }
    return S.roh("p." + p.treiber.name + "." + p.richtung.ordinal, w)
}

// Messung mit Stand, etwa „1,82 % · −0,31 Punkte in 3 Monaten · 10.11.2026“
private fun messung(p: RohstoffWaage.Punkt): String {
    val w = when (p.treiber) {
        Treiber.REALZINS, Treiber.INFLATION -> listOf(Fmt.pct(p.wert, 2), vz(p.vergleich, 2))
        Treiber.DOLLAR -> listOf(Fmt.num(p.wert, 1), Fmt.signedPct(p.vergleich, 1))
        Treiber.POSITIONIERUNG -> listOf(Fmt.signedPct(p.wert, 0), p.vergleich.toInt().toString())
        Treiber.TREND, Treiber.BOERSENLAGER -> listOf(Fmt.signedPct(p.vergleich, 1))
        Treiber.SAISON -> listOf(Fmt.signedPct(p.wert, 1), p.vergleich.toInt().toString(), (p.zusatz ?: 0.0).toInt().toString())
        Treiber.LAGER, Treiber.TERMINKURVE -> listOf(Fmt.signedPct(p.wert, 1))
        Treiber.ENSO -> listOf(vz(p.wert, 1))
        Treiber.KONJUNKTUR -> listOf(Fmt.pct(p.wert, 0))
        Treiber.OPEC -> listOf(Fmt.num(p.wert, 1), Fmt.signedPct(p.vergleich, 1))
        Treiber.WELTLAGER -> listOf(Fmt.num(p.wert, 0), Fmt.signedPct(p.vergleich, 1))
    }
    val stand = when (p.treiber) {
        Treiber.SAISON -> null
        Treiber.KONJUNKTUR, Treiber.OPEC, Treiber.WELTLAGER -> p.stand.takeIf { it > 0 }?.let { monatName(it) }
        else -> p.stand.takeIf { it > 0 }?.let { Fmt.date(it) }
    }
    return listOfNotNull(S.roh("m." + p.treiber.name, w), stand).joinToString(PUNKT)
}

// Lader: holt die Messungen einmal je Seite (Ergebnis im Merker) und meldet das Modell fuer rohstoffWaageAbschnitt.
@Composable
internal fun RohstoffWaageLader(state: AppState, symbol: String, schluessel: String, merker: SeitenMerker) {
    var eingaben by gemerkt<RohstoffWaage.Eingaben?>(merker, "rohwaage/eingaben") { null }
    LaunchedEffect(symbol) { if (eingaben == null) eingaben = runCatching { state.rohstoffEingaben(symbol, schluessel) }.getOrNull() }
    val e = eingaben
    val punkte = remember(e) { e?.let { RohstoffWaage.auswerten(schluessel, it) }.orEmpty() }
    if (punkte.any { it.treiber == Treiber.REALZINS }) QuellenMelden(fredQuelle(de.troetenrepublik.app.RohstoffSpeicher.REALZINS))
    if (punkte.any { it.treiber == Treiber.INFLATION }) QuellenMelden(fredQuelle(de.troetenrepublik.app.RohstoffSpeicher.INFLATION))
    if (punkte.any { it.treiber == Treiber.LAGER || it.treiber == Treiber.OPEC || it.treiber == Treiber.WELTLAGER }) QuellenMelden(EIA_QUELLE)
    val sprache = I18n.lang
    val modell = remember(punkte, sprache) { if (punkte.isEmpty()) null else waageModell(merker, punkte) }
    ModellMelden(merker, "rohwaage", modell)
}

internal fun LazyListScope.rohstoffWaageAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "rohwaage")
}

private fun waageModell(merker: SeitenMerker, punkte: List<RohstoffWaage.Punkt>): ListenModell {
    val erklaert = merker.zustand("rohwaage/erklaert") { mutableStateMapOf<String, Boolean>() }.value
    val waage = RohstoffWaage.waage(punkte)
    val dafuer = punkte.count { it.richtung == Richtung.STUETZT }
    val dagegen = punkte.count { it.richtung == Richtung.BELASTET }
    val zeilen = ArrayList<ZeilenDaten>()
    waage?.let { w ->
        zeilen.add(ZeilenDaten("waage") {
            val signal = LocalSignal.current
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.roh("saldo"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(S.bbStufe(w.stufe.ordinal), style = MaterialTheme.typography.titleSmall, color = trendFarbe(w.wert), maxLines = 1, softWrap = false)
                }
                val plus = ((w.wert + 100) / 200f).coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
                    if (plus > 0f) Box(Modifier.weight(plus).fillMaxHeight().background(signal.up))
                    if (plus < 1f) Box(Modifier.weight(1f - plus).fillMaxHeight().background(signal.down))
                }
            }
        })
    }
    listOf(Richtung.STUETZT to S.bbDafuer, Richtung.BELASTET to S.bbDagegen, Richtung.NEUTRAL to S.roh("neutral")).forEach { (r, titel) ->
        val liste = punkte.filter { it.richtung == r }.sortedByDescending { it.gewicht }
        if (liste.isEmpty()) return@forEach
        zeilen.add(ZeilenDaten("titel-" + r.name) {
            val signal = LocalSignal.current
            Text(
                titel, style = MaterialTheme.typography.titleSmall,
                color = when (r) { Richtung.STUETZT -> signal.up; Richtung.BELASTET -> signal.down; Richtung.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant },
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 2.dp),
            )
        })
        liste.forEach { p ->
            zeilen.add(ZeilenDaten("pt-" + p.treiber.name) {
                val k = p.treiber.name
                Row(
                    Modifier.fillMaxWidth().clickable { erklaert[k] = erklaert[k] != true }.heightIn(min = 40.dp).padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Box(Modifier.width(17.dp).padding(top = 6.dp)) {
                        DirectionTriangle(when (r) { Richtung.STUETZT -> 1.0; Richtung.BELASTET -> -1.0; Richtung.NEUTRAL -> 0.0 })
                    }
                    Column(Modifier.weight(1f)) {
                        Text(aussage(p), style = MaterialTheme.typography.bodyMedium)
                        PunktText(messung(p), Modifier.padding(top = 1.dp))
                        if (erklaert[k] == true) Text(
                            S.roh("w.$k"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                        )
                    }
                }
            })
        }
    }
    return ListenModell(
        titel = S.bwFuerUndWider, gross = true,
        kern = KernZeile(
            listOf(
                KernTeil(S.roh("k.dafuer", listOf(dafuer.toString())), if (dafuer > 0) 1.0 else 0.0),
                KernTeil(S.roh("k.dagegen", listOf(dagegen.toString())), if (dagegen > 0) -1.0 else 0.0),
            ),
            waage?.let { KernTeil(S.bbStufe(it.stufe.ordinal)) },
        ),
        zeilen = zeilen, info = S.roh("info"),
    )
}

// ------------------------------------------------------------ Signale aus dem Kurs

// Aufgeklappte Signale eines Rohstoffs unter dem Chart: Trend, Abstand zu Jahreshoch und -tief, Durchbruch, Schwankung,
// dazu die Spekulanten (CFTC) und auffaellige Optionen auf den passenden ETF. Zeilen ohne Daten entfallen.
@Composable
internal fun RohstoffSignaleListe(state: AppState, symbol: String, schluessel: String) {
    val kurs by produceState<RohstoffWaage.Kurssignale?>(null, symbol) {
        value = runCatching { RohstoffWaage.kurssignale(state.kurseNativ(symbol).map { Math.floorDiv(it.t, 86_400_000L) to it.close }) }.getOrNull()
    }
    val rang by produceState<Int?>(null, schluessel) {
        value = RohstoffWaage.cotMarkt(schluessel)?.let { m -> runCatching { state.positionierung(nur = setOf(m)).firstOrNull { it.schluessel == m }?.index }.getOrNull() }
    }
    val etf = remember(symbol) { RohstoffHandel.etf(symbol)?.kuerzel }
    val optionen by produceState<Signale.Optionen?>(null, etf) {
        value = etf?.let { e -> runCatching { state.market.optionskette(e) }.getOrNull()?.let { Signale.optionen(Optionsanalyse.auswerten(it.first, it.second)) } }
    }
    val k = kurs
    if (k == null && rang == null && optionen == null) return
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        k?.let { s ->
            s.trend?.let { t -> SignalReihe(S.roh("s.trend"), Fmt.signedPct(t, 1), if (abs(t) >= RohstoffWaage.TREND_PROZENT) t else 0.0) }
            SignalReihe(S.roh("s.hoch"), Fmt.signedPct(s.hoch, 1), if (RohstoffWaage.amHoch(s)) 1.0 else 0.0)
            SignalReihe(S.roh("s.tief"), Fmt.signedPct(s.tief, 1), if (RohstoffWaage.amTief(s)) -1.0 else 0.0)
            if (s.durchbruch != 0) SignalReihe(S.roh("s.durchbruch"), S.roh(if (s.durchbruch > 0) "s.oben" else "s.unten"), s.durchbruch.toDouble())
            s.vola?.let { v -> SignalReihe(S.roh("s.vola"), Fmt.pct(v, 0), 0.0) }
            s.volaJahr?.let { j -> SignalReihe(S.roh("s.volaJahr"), Fmt.pct(j, 0), 0.0) }
        }
        rang?.let { r -> SignalReihe(S.roh("s.position"), S.roh("s.rang", listOf(r.toString())), 0.0) }
        optionen?.let { o -> SignalReihe(S.sgOptionen, S.sgOptionenWert(o.anzahl, o.calls, o.puts), 0.0, unter = etf + PUNKT + S.sgPraemie(Fmt.dollarKompakt(o.praemie)), links = o.calls.toFloat(), rechts = o.puts.toFloat(), faerben = true) }
    }
}

// Dieselbe Zeile wie bei den Signalen der Aktien (SignalZeile): Dreieck (ohne Richtung keins), Bezeichnung links, Wert rechts, dünner Balken.
@Composable
private fun SignalReihe(titel: String, wert: String, richtung: Double, unter: String? = null, links: Float = 0f, rechts: Float = 0f, faerben: Boolean = false) {
    SignalZeile(titel, wert, unter, richtung, links, rechts, faerben = faerben)
}
