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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.marktmachtFuer
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.BullBear
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Leerpositionen
import de.troetenrepublik.app.market.MarketApi

// Chancen und Risiken auf der Seite einer Aktie (seit 5.7 als Bull- und Bear-Case, seit 5.92 mit Analysten, Stimmung
// aus Optionen und Leerverkaeufen und Einzelheiten per Tipp): was dafuer und was dagegen spricht, aus Schlagzeilen
// (mit antippbarem Beleg) und Kennzahlen. Wie die Waage rechnet, steht hinter dem Info-Symbol.

// Einzelheiten, die ein Tipp auf einen Punkt oeffnet.
internal enum class Detail { ANALYSTEN, INSIDER, LEER, OPTIONEN, BEWERTUNG, BILANZ, EREIGNISSE, KONGRESS, FDA, WARNZEICHEN }

// Wohin ein Punkt fuehrt; null, wenn ein Tipp nur die Erklaerung zeigt.
private fun ziel(pt: BullBear.Punkt): Detail? = when (pt.kennzahl) {
    BullBear.Kennzahl.INSIDER_KAUFEN, BullBear.Kennzahl.INSIDER_VERKAUFEN -> Detail.INSIDER
    BullBear.Kennzahl.LEERVERKAUF_HOCH -> Detail.LEER
    BullBear.Kennzahl.ANALYSTEN_POSITIV, BullBear.Kennzahl.ANALYSTEN_SKEPTISCH, BullBear.Kennzahl.ANALYSTEN_VERHALTEN,
    BullBear.Kennzahl.STUFEN_HOCH, BullBear.Kennzahl.STUFEN_AB -> Detail.ANALYSTEN
    BullBear.Kennzahl.BEWERTUNG_HOCH, BullBear.Kennzahl.BEWERTUNG_NIEDRIG -> Detail.BEWERTUNG
    BullBear.Kennzahl.SCHULDEN, BullBear.Kennzahl.NETTOKASSE -> Detail.BILANZ
    BullBear.Kennzahl.UEBERTROFFEN, BullBear.Kennzahl.VERFEHLT -> Detail.EREIGNISSE
    BullBear.Kennzahl.KONGRESS_KAUFT, BullBear.Kennzahl.KONGRESS_VERKAUFT -> Detail.KONGRESS
    BullBear.Kennzahl.FDA_ZULASSUNG, BullBear.Kennzahl.FDA_RUECKRUF -> Detail.FDA
    BullBear.Kennzahl.WARNZEICHEN -> Detail.WARNZEICHEN
    else -> if (pt.thema == BullBear.Thema.ABSTUFUNG) Detail.ANALYSTEN else null
}

// Was die Dialoge der Karte vom Lader brauchen: Optionsdaten und Stufe der Waage.
internal class ChancenDialogDaten(val optionen: MarketApi.OptionVolumes?, val waageStufe: Int?)

@Composable
private fun DialogDatenMelden(merker: SeitenMerker, daten: ChancenDialogDaten?) {
    val ziel = merker.zustand<ChancenDialogDaten?>("chancen/dialog") { null }
    SideEffect {
        val alt = ziel.value
        if (alt?.optionen !== daten?.optionen || alt?.waageStufe != daten?.waageStufe) ziel.value = daten
    }
}

// Lader der Karte: holt die Staende, rechnet die Punkte einmal (Ergebnis im Merker der Seite) und gibt die Zeilen als Modell
// an die LazyColumn der Seite (chancenAbschnitt). Er zeichnet selbst nichts; Dialoge und Blaetter stehen in ChancenDialoge.
@Composable
internal fun ChancenUndRisiken(
    state: AppState, symbol: String, isin: String, quote: Quote?, profile: MarketApi.Profile?, news: List<MarketApi.NewsItem>,
    // Bull- und Bear-Case nur bei Aktien
    aktie: Boolean,
    usWert: Boolean,
    // Optionen an US-Boersen fuer diesen Wert
    mitOptionen: Boolean,
    seite: SeitenWaehrung,
    ziel: Geldanzeige,
    // [0.77] Kongress und Hoch-/Abstufungen aus denselben Daten wie die Signale-Zeile unter dem Chart
    signale: SignaleStand? = null,
    merker: SeitenMerker,
    onNews: (MarketApi.NewsItem) -> Unit,
) {
    // Nur die Staende auf dem Geraet, ohne Abruf: Die Karten mit Abruf oeffnen erst im Blatt
    LaunchedEffect(symbol, isin) {
        runCatching { state.secOhneNetz() }
        runCatching { state.bafinLaden(netz = false) }
        if (isin.length == 12) {
            de.troetenrepublik.app.market.EuInsider.landFuerIsin(isin)?.let { runCatching { state.euLaden(it, netz = false) } }
            Leerpositionen.landFuerIsin(isin)?.let { runCatching { state.lpLaden(it, netz = false) } }
        }
    }
    var optionen by gemerkt<MarketApi.OptionVolumes?>(merker, "chancen/optionen") { null }
    LaunchedEffect(symbol, mitOptionen, isin) { if (mitOptionen) optionen = runCatching { state.market.optionVolumes(symbol, isin) }.getOrNull() }
    val sec by state.sec.collectAsStateWithLifecycle()
    val bafin by state.bafin.collectAsStateWithLifecycle()
    val leer by state.leerpositionen.collectAsStateWithLifecycle()
    val fda by state.fda.collectAsStateWithLifecycle()
    val eu by state.euInsider.collectAsStateWithLifecycle()
    val welt by state.weltInsider.collectAsStateWithLifecycle()
    val achtkSchwer by de.troetenrepublik.app.market.AchtKAbruf.schwere.collectAsStateWithLifecycle()
    val fxRaten by state.market.fxKurse.collectAsStateWithLifecycle()
    val leerTitel = merker.gerechnet("chancen/leerTitel", listOf(leer, isin)) { Leerpositionen.LAENDER.firstNotNullOfOrNull { leer[it]?.titel?.get(isin) } }
    // Die Punkte laufen aus dem Aufbau: Beim Zurueckscrollen kommt das gemerkte Ergebnis, solange die Eingaben gleich sind
    val punkte = merker.gerechnet(
        "chancen/punkte",
        listOf(quote?.nativePrice, quote?.marketCap, fxRaten, profile, news, sec, bafin, leerTitel, fda, eu, welt, aktie, signale?.kongress, signale?.stufen, achtkSchwer[symbol]),
    ) {
        if (!aktie) return@gerechnet emptyList<BullBear.Punkt>()
        val fdaJetzt = fda[symbol]
        // Insider-Trend der Signale-Zeile: dieselbe Rechnung, dasselbe Fenster (90 Tage)
        val insider = signalInsider(sec, bafin, eu, welt, symbol, isin)?.wert
        // Leerverkaeufe wie in der Zeile darunter: US-Werte am Streubesitz, sonst die gemeldeten Positionen am Grundkapital
        val leerUs = usWert && (profile?.mehr?.leerQuote ?: 0.0) > 0
        val p = profile
        // Marktwert in der Waehrung der Abschlusszahlen (Kasse, Schulden, Cashflow stehen dort)
        val marktwert = p?.let { boersenwertBericht(it.financialCurrency.ifBlank { quote?.nativeCurrency.orEmpty() }, it, quote, fxRaten) } ?: 0.0
        val f = BullBear.Fakten(
            kurs = quote?.nativePrice ?: 0.0, kursziel = p?.targetPrice ?: 0.0, empfehlung = p?.recommendationMean ?: 0.0,
            // Yahoo-Werte kommen in Prozent (MarketApi rechnet mal 100), BullBear erwartet Anteile
            analysten = p?.analystCount ?: 0, umsatzWachstum = (p?.revenueGrowth ?: 0.0) / 100, gewinnWachstum = (p?.earningsGrowth ?: 0.0) / 100,
            gewinnMarge = (p?.profitMargin ?: 0.0) / 100, kasse = p?.totalCash ?: 0.0, schulden = p?.totalDebt ?: 0.0,
            freierCashflow = p?.freeCashflow ?: 0.0, marktwert = marktwert, kgv = quote?.peRatio ?: 0.0,
            kgvErwartet = p?.forwardPe ?: 0.0, beta = p?.beta ?: 0.0, ausschuettung = (p?.payoutRatio ?: 0.0) / 100,
            zumHoch = quote?.takeIf { it.weekHigh52 > 0 && it.price > 0 }?.let { it.price / it.weekHigh52 } ?: 0.0,
            dividendenRendite = (p?.dividendYield ?: 0.0) / 100, eigenkapitalRendite = (p?.returnOnEquity ?: 0.0) / 100,
            branche = p?.sector.orEmpty(), industrie = p?.industry.orEmpty(),
            ergebnisse = p?.epsHistory.orEmpty().map { it.actual to it.estimate }, insider = insider,
            leerverkauf = if (leerUs) p?.mehr?.leerQuote else leerTitel?.summe, leerUs = leerUs,
            fdaZulassungen = fdaJetzt?.zulassungen?.count { it.art != de.troetenrepublik.app.market.Fda.Art.ANWENDUNG },
            fdaRueckrufe = fdaJetzt?.rueckrufe?.count { it.klasse == 1 },
            kongress = signale?.kongress?.let { it.kaeufe to it.verkaeufe }, stufen = signale?.stufen?.let { it.hoch to it.ab }, warnzeichen = achtkSchwer[symbol],
            warnThemen = BullBear.warnThemen(
                de.troetenrepublik.app.market.AchtKAbruf.gemerkt(symbol)?.warnzeichen.orEmpty()
                    .filter { it.art.schwer && de.troetenrepublik.app.market.AchtK.zaehlt(it) }.map { it.art.name },
            ),
        )
        val schlagzeilen = BullBear.zurFirma(
            news.map { BullBear.Schlagzeile(it.title, it.published, it.id) }, symbol,
            de.troetenrepublik.app.market.Gleichwerte.stamm(quote?.name?.ifBlank { null } ?: p?.longName ?: p?.shortName ?: symbol),
        )
        BullBear.auswerten(f, schlagzeilen, System.currentTimeMillis())
    }
    val analystenDa = profile?.let { it.targetPrice > 0 || it.recommendation.isNotBlank() } == true
    // Leerverkaeufe in einer Zeile: US-Werte aus den Yahoo-Kennzahlen, sonst aus den Listen in Paris, Stockholm, London.
    // Oben das Urteil in Worten, darunter klein Zahl und Richtung (5.94).
    val leerZeile: Pair<String, String>? = run {
        val m = profile?.mehr
        if (usWert && m != null && m.leerQuote > 0) {
            S.lvStufe(Bewertungsblick.leerStufe(m.leerQuote, true)) to
                S.crLeer(Fmt.pct(m.leerQuote, 2), Bewertungsblick.leerTrend(m.leerAktien, m.leerAktienVormonat)?.ordinal, true)
        } else leerTitel?.let { t ->
            val vor = t.verlauf.getOrNull(t.verlauf.size - 5)?.s
            S.lvStufe(Bewertungsblick.leerStufe(t.summe, false)) to
                S.crLeer(Fmt.pct(t.summe, 2), Bewertungsblick.leerTrend(t.summe, vor)?.ordinal, false)
        }
    }
    val o = optionen?.takeIf { it.total > 0 }
    val waage = BullBear.waage(punkte)
    val marktmacht by androidx.compose.runtime.produceState<de.troetenrepublik.app.engine.Marktmacht.Ergebnis?>(null, symbol, aktie) {
        value = if (aktie) runCatching { state.marktmachtFuer(symbol) }.getOrNull() else null
    }
    val leer0 = punkte.isEmpty() && !analystenDa && leerZeile == null && o == null && marktmacht?.stufe == null
    DialogDatenMelden(merker, if (leer0) null else ChancenDialogDaten(o, waage?.stufe?.ordinal))
    val modell = remember(punkte, waage, analystenDa, leerZeile, o, profile, quote, seite, ziel, news, symbol, usWert, leer0, marktmacht) {
        if (leer0) null else chancenModell(merker, punkte, waage, analystenDa, leerZeile, o, profile, quote, seite, ziel, news, onNews, marktmacht)
    }
    ModellMelden(merker, "chancen", modell)
}

// Die Zeilen der Karte als Eintraege der LazyColumn: Kopf mit Info-Symbol, Analysten, Stimmung, Waage, dann je Punkt eine Zeile.
internal fun LazyListScope.chancenAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "chancen")
}

private fun chancenModell(
    merker: SeitenMerker, punkte: List<BullBear.Punkt>, waage: BullBear.Waage?, analystenDa: Boolean, leerZeile: Pair<String, String>?,
    o: MarketApi.OptionVolumes?, profile: MarketApi.Profile?, quote: Quote?, seite: SeitenWaehrung, ziel: Geldanzeige,
    news: List<MarketApi.NewsItem>, onNews: (MarketApi.NewsItem) -> Unit,
    marktmacht: de.troetenrepublik.app.engine.Marktmacht.Ergebnis? = null,
): ListenModell {
    val detail = merker.zustand<Detail?>("chancen/detail") { null }
    val info = merker.zustand("chancen/info") { false }
    // Erklaerungen der Punkte (5.95): verborgen, ein Tipp auf den Punkt zeigt oder verbirgt sie, ohne Pfeil
    val erklaert = merker.zustand("chancen/erklaert") { mutableStateMapOf<String, Boolean>() }.value
    val zeilen = ArrayList<ZeilenDaten>()
    if (analystenDa && profile != null) {
        zeilen.add(ZeilenDaten("analysten") {
            Column {
                Spacer(Modifier.height(8.dp))
                AnalystenKarte(profile, quote, ziel) { detail.value = Detail.ANALYSTEN }
                Spacer(Modifier.height(6.dp))
            }
        })
    }
    // Stimmung zur Aktie: Optionshandel und Leerverkaeufe in je einer Zeile
    o?.let { v ->
        zeilen.add(ZeilenDaten("optionen") { StimmungsZeile(S.crOptionen, if (v.offen) S.crOptionenOffen(Fmt.pct(v.longPct, 0)) else S.crOptionenWert(Fmt.pct(v.longPct, 0)), v.longPct / 100.0) { detail.value = Detail.OPTIONEN } })
    }
    leerZeile?.let { (urteil, zahl) -> zeilen.add(ZeilenDaten("leer") { StimmungsZeile(S.bhLeer, urteil, null, unter = zahl) { detail.value = Detail.LEER } }) }
    // Waage: welche Seite schwerer wiegt, als Balken; der erklaerende Satz steht hinter dem Info-Symbol
    waage?.let { w ->
        zeilen.add(ZeilenDaten("waage") {
            val signal = LocalSignal.current
            Column(Modifier.fillMaxWidth().clickable { info.value = true }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.bbWaage, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(S.bbStufe(w.stufe.ordinal), style = MaterialTheme.typography.titleSmall, color = trendFarbe(w.wert))
                }
                val plus = ((w.wert + 100) / 200f).coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
                    if (plus > 0f) Box(Modifier.weight(plus).fillMaxHeight().background(signal.up))
                    if (plus < 1f) Box(Modifier.weight(1f - plus).fillMaxHeight().background(signal.down))
                }
            }
        })
    }
    // [0.77] Marktmacht der Firma aus den Abschluessen im Speicher des Aktienfinders
    marktmacht?.takeIf { it.stufe != null }?.let { mm -> zeilen.add(ZeilenDaten("marktmacht") { MarktmachtZeile(mm) }) }
    // Punkte, die schon als Karte oder Zeile oben stehen, zaehlen in der Waage, erscheinen aber nicht doppelt
    fun doppelt(pt: BullBear.Punkt): Boolean = when (pt.kennzahl) {
        BullBear.Kennzahl.ANALYSTEN_POSITIV, BullBear.Kennzahl.ANALYSTEN_SKEPTISCH, BullBear.Kennzahl.ANALYSTEN_VERHALTEN -> analystenDa
        BullBear.Kennzahl.LEERVERKAUF_HOCH -> leerZeile != null
        else -> false
    }
    val w = profile?.financialCurrency?.ifBlank { null } ?: quote?.nativeCurrency.orEmpty()
    fun geld(v: Double) = seite.kompakt(v, w)
    fun text(pt: BullBear.Punkt): String {
        pt.thema?.let { return S.bbThema(it.name) }
        val v = pt.werte
        val kz = pt.kennzahl ?: return ""
        val werte = when (kz) {
            BullBear.Kennzahl.ANALYSTEN_POSITIV, BullBear.Kennzahl.ANALYSTEN_SKEPTISCH, BullBear.Kennzahl.ANALYSTEN_VERHALTEN ->
                listOf(Fmt.price(v[0], Derivatives.currencySymbol(quote?.nativeCurrency.orEmpty())), Fmt.signedPct(v[1], 0), v[2].toInt().toString(), v.getOrNull(3)?.let { Fmt.num(it, 1) }.orEmpty())
            BullBear.Kennzahl.NETTOKASSE, BullBear.Kennzahl.CASHFLOW_NEGATIV -> listOf(geld(kotlin.math.abs(v[0])))
            BullBear.Kennzahl.SCHULDEN, BullBear.Kennzahl.CASHFLOW_POSITIV -> listOf(geld(v[0]), Fmt.pct(v[1], 0))
            BullBear.Kennzahl.UEBERTROFFEN, BullBear.Kennzahl.VERFEHLT -> listOf(v[0].toInt().toString(), v[1].toInt().toString())
            // Ueber +300 % lag der alte Gewinn nahe null: dann keine Zahl, sondern "mehr als verdreifacht" (leerer Wert)
            BullBear.Kennzahl.GEWINNE_STEIGEN -> listOf(Fmt.num(v[0], 1), Fmt.num(v[1], 1), if (v[2] > 300.0) "" else Fmt.signedPct(v[2], 0))
            BullBear.Kennzahl.BEWERTUNG_HOCH, BullBear.Kennzahl.SCHWANKUNG -> listOf(Fmt.num(v[0], 1))
            BullBear.Kennzahl.ZYKLISCH -> emptyList()
            BullBear.Kennzahl.INSIDER_KAUFEN, BullBear.Kennzahl.INSIDER_VERKAUFEN -> listOf(v[0].toInt().toString().replace('-', '\u2212'))
            BullBear.Kennzahl.LEERVERKAUF_HOCH -> listOf(Fmt.pct(v[0], 1), if (v.getOrNull(1) == 1.0) "US" else "")
            BullBear.Kennzahl.FDA_ZULASSUNG, BullBear.Kennzahl.FDA_RUECKRUF, BullBear.Kennzahl.WARNZEICHEN -> listOf(v[0].toInt().toString())
            BullBear.Kennzahl.KONGRESS_KAUFT, BullBear.Kennzahl.KONGRESS_VERKAUFT, BullBear.Kennzahl.STUFEN_HOCH, BullBear.Kennzahl.STUFEN_AB ->
                listOf(v[0].toInt().toString(), v[1].toInt().toString())
            BullBear.Kennzahl.DIVIDENDE_SOLIDE -> listOf(Fmt.pct(v[0], 1), Fmt.pct(v[1], 0))
            BullBear.Kennzahl.BEWERTUNG_NIEDRIG -> listOf(Fmt.num(v[0], 1))
            else -> listOf(Fmt.pct(kotlin.math.abs(v.firstOrNull() ?: 0.0), 0))
        }
        return S.bbKennzahl(kz.name, werte)
    }
    listOf(BullBear.Seite.BULL to S.bbDafuer, BullBear.Seite.BEAR to S.bbDagegen).forEach { (seiteBb, titel) ->
        // Schwerste Punkte zuerst, alle sichtbar; die Erklaerung zeigt ein Tipp auf den Punkt
        val sortiert = punkte.filter { it.seite == seiteBb && !doppelt(it) }.sortedByDescending { it.gewicht }
        if (sortiert.isEmpty()) return@forEach
        val bull = seiteBb == BullBear.Seite.BULL
        zeilen.add(ZeilenDaten(if (bull) "titel-dafuer" else "titel-dagegen") {
            val signal = LocalSignal.current
            Text(
                titel, style = MaterialTheme.typography.titleSmall, color = if (bull) signal.up else signal.down,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 2.dp),
            )
        })
        sortiert.forEachIndexed { i, pt ->
            zeilen.add(ZeilenDaten("pt-${if (bull) "d" else "g"}-$i-${pt.schluessel}") {
                val signal = LocalSignal.current
                val wohin = ziel(pt)
                val warum = S.bbWarum(pt.schluessel)
                // Mit Erklaerung schaltet ein Tipp sie um, der Pfeil rechts fuehrt weiter; ohne Erklaerung fuehrt die
                // ganze Zeile weiter
                val antippen: (() -> Unit)? = when {
                    warum.isNotBlank() -> { { erklaert[pt.schluessel] = erklaert[pt.schluessel] != true } }
                    wohin != null -> { { detail.value = wohin } }
                    else -> null
                }
                Row(
                    Modifier.fillMaxWidth().then(if (antippen != null) Modifier.clickable(onClick = antippen) else Modifier)
                        .heightIn(min = 40.dp).padding(start = 20.dp, end = if (wohin != null && warum.isNotBlank()) 8.dp else 20.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    DirectionTriangle(if (bull) 1.0 else -1.0, Modifier.padding(top = 6.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text(pt), style = MaterialTheme.typography.bodyMedium)
                        if (warum.isNotBlank() && erklaert[pt.schluessel] == true) Text(warum, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
                        pt.belege.forEach { b ->
                            val n = news.firstOrNull { it.id == b.id }
                            Text(
                                S.bhZitat(b.titel) + (if (b.zeit > 0) " · " + Fmt.relative(b.zeit) else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (n != null) signal.link else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp).then(if (n != null) Modifier.textKlick(0.dp) { onNews(n) } else Modifier),
                            )
                        }
                    }
                    if (wohin != null) {
                        Spacer(Modifier.width(8.dp))
                        // Eigene Tippflaeche fuer den Pfeil, wenn der Rest der Zeile die Erklaerung umschaltet
                        val pfeil = if (warum.isNotBlank()) Modifier.clip(CircleShape).clickable { detail.value = wohin }.padding(12.dp) else Modifier.padding(top = 4.dp)
                        ChevronRight(modifier = pfeil)
                    }
                }
            })
        }
    }
    return ListenModell(
        titel = "", gross = true, kern = null, zeilen = zeilen, immerOffen = true,
        // Kopf wie SectionHeader, rechts das Info-Symbol zur Waage
        kopf = {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.bwFuerUndWider, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (punkte.isNotEmpty()) {
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable { info.value = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Info, S.crInfo, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    )
}

// Dialoge und Blaetter der Karte, ausserhalb der Liste: Sie bleiben, auch wenn die Zeilen aus dem Bild rollen.
@Composable
internal fun ChancenDialoge(
    state: AppState, merker: SeitenMerker, symbol: String, isin: String, name: String, quote: Quote?, profile: MarketApi.Profile?,
    seite: SeitenWaehrung, usWert: Boolean, euInsider: Boolean,
    // Zahl der Kongress-Zeile der Signale fuer das Blatt hinter dem Kongress-Punkt
    kongress: de.troetenrepublik.app.engine.Signale.Kongress? = null,
    onOpenRoute: (String) -> Unit,
) {
    var detail by merker.zustand<Detail?>("chancen/detail") { null }
    var info by merker.zustand("chancen/info") { false }
    val daten = merker.zustand<ChancenDialogDaten?>("chancen/dialog") { null }.value
    val o = daten?.optionen?.takeIf { it.total > 0 }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text(S.bwFuerUndWider) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    daten?.waageStufe?.let { Text(S.bbWaageSatz(it), style = MaterialTheme.typography.bodyMedium) }
                    // Gewichtung der Punkte als kleine Tabelle
                    Column {
                        listOf(S.bbGewichtKopf).plus(S.bbGewichte).forEachIndexed { i, (gewicht, beispiele) ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(gewicht, style = if (i == 0) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                                    color = if (i == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, softWrap = false, modifier = Modifier.width(76.dp))
                                Text(beispiele, style = if (i == 0) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                                    color = if (i == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text(S.bbMehrfach, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.bbHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text(S.ok) } },
        )
    }

    // Oeffnet ein Blatt eine Unterseite, wartet es und steht nach dem Zurueck wieder an derselben Stelle da
    if (detail != null) WiederkehrBlatt("chancen-detail") { w -> when (detail) {
        Detail.ANALYSTEN -> AnalystenBlatt(state, symbol, name, profile, seite, oeffnen = { w.verlassen(); onOpenRoute(it) }) { detail = null }
        Detail.INSIDER -> InsiderBlatt(state, symbol, isin, name, usWert, euInsider, oeffnen = { w.verlassen(); onOpenRoute(it) }) { detail = null }
        Detail.KONGRESS -> KongressBlatt(state, symbol, name, usWert, kongress, oeffnen = { w.verlassen(); onOpenRoute(it) }) { detail = null }
        // Die Themen aus dem Hintergrund der Seite: Zulassungen und Rueckrufe der FDA, Warnzeichen aus den 8-K
        Detail.FDA -> DetailBlatt(S.bwHintergrund, name, { detail = null }) { FdaKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name) }
        Detail.WARNZEICHEN -> DetailBlatt(S.bwHintergrund, name, { detail = null }) { AchtKKarte(state, symbol) }
        Detail.LEER -> DetailBlatt(S.bhLeer, name, { detail = null }) {
            if (usWert) LeerverkaufVerlaufKarte(state, symbol, quote, profile)
            LeerverkaufKarte(state, symbol, isin, usWert, kopf = false, oeffnen = { w.verlassen(); onOpenRoute(it) })
            if (!usWert) ErklaerungAufklappen(S.lvKurz)
            if (usWert) DarkPoolKarte(state, symbol)
        }
        Detail.OPTIONEN -> DetailBlatt(S.opTitel, name, { detail = null }) {
            o?.let { LongShortBars(it.longPct, onLong = { w.verlassen(); onOpenRoute("derivate/L/$symbol") }, onShort = { w.verlassen(); onOpenRoute("derivate/S/$symbol") }) }
            o?.let { S.opHerkunft(it.herkunft, it.offen) }?.let { QuelleZeile(it) }
            val hv by androidx.compose.runtime.produceState<Double?>(null, symbol) { value = runCatching { state.historischeVol(symbol) }.getOrNull() }
            OptionenKarte(state, symbol, quote?.nativeCurrency?.ifBlank { null } ?: "USD", hv, kopf = false, onDerivat = { w.verlassen(); onOpenRoute("derivative/$it") })
        }
        Detail.BEWERTUNG -> BewertungBlatt(state, symbol, name, quote, profile) { detail = null }
        Detail.BILANZ -> BilanzBlatt(state, symbol, name, quote, profile) { detail = null }
        Detail.EREIGNISSE -> EventsSheet(state, symbol, name, profile, seite, onDismiss = { detail = null })
        null -> {}
    } }
}

// Eine Zeile Stimmung zur Aktie: Titel, Wert, optional ein Erklaersatz und bei Optionen ein schmaler Long/Short-Balken;
// ein Tipp oeffnet die Einzelheiten.
@Composable
private fun StimmungsZeile(titel: String, wert: String, anteilLong: Double?, unter: String? = null, onClick: () -> Unit) {
    val signal = LocalSignal.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(wert, style = MaterialTheme.typography.bodyMedium)
            unter?.let { PunktText(it, Modifier.padding(top = 2.dp)) }
            anteilLong?.let { a ->
                val l = a.toFloat().coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp))) {
                    if (l > 0f) Box(Modifier.weight(l).fillMaxHeight().background(signal.up))
                    if (l < 1f) Box(Modifier.weight(1f - l).fillMaxHeight().background(signal.down.copy(alpha = 0.45f)))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        ChevronRight()
    }
}
