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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.BullBear
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Bafin
import de.troetenrepublik.app.market.Leerpositionen
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.SecInsider

// Chancen und Risiken auf der Seite einer Aktie (seit 5.7 als Bull- und Bear-Case, seit 5.92 mit Analysten, Stimmung
// aus Optionen und Leerverkaeufen und Einzelheiten per Tipp): was dafuer und was dagegen spricht, aus Schlagzeilen
// (mit antippbarem Beleg) und Kennzahlen. Wie die Waage rechnet, steht hinter dem Info-Symbol.

// Einzelheiten, die ein Tipp auf einen Punkt oeffnet.
internal enum class Detail { ANALYSTEN, INSIDER, LEER, OPTIONEN, BEWERTUNG, BILANZ, EREIGNISSE }

// Wohin ein Punkt fuehrt; null, wenn ein Tipp nur die Erklaerung zeigt.
private fun ziel(pt: BullBear.Punkt): Detail? = when (pt.kennzahl) {
    BullBear.Kennzahl.INSIDER_KAUFEN, BullBear.Kennzahl.INSIDER_VERKAUFEN -> Detail.INSIDER
    BullBear.Kennzahl.LEERVERKAUF_HOCH -> Detail.LEER
    BullBear.Kennzahl.ANALYSTEN_POSITIV, BullBear.Kennzahl.ANALYSTEN_SKEPTISCH -> Detail.ANALYSTEN
    BullBear.Kennzahl.BEWERTUNG_HOCH, BullBear.Kennzahl.BEWERTUNG_NIEDRIG -> Detail.BEWERTUNG
    BullBear.Kennzahl.SCHULDEN, BullBear.Kennzahl.NETTOKASSE -> Detail.BILANZ
    BullBear.Kennzahl.UEBERTROFFEN, BullBear.Kennzahl.VERFEHLT -> Detail.EREIGNISSE
    else -> if (pt.thema == BullBear.Thema.ABSTUFUNG) Detail.ANALYSTEN else null
}

@Composable
internal fun ChancenUndRisiken(
    state: AppState, symbol: String, isin: String, name: String, quote: Quote?, profile: MarketApi.Profile?, news: List<MarketApi.NewsItem>,
    // Bull- und Bear-Case nur bei Aktien
    aktie: Boolean,
    usWert: Boolean,
    // Register weiterer Laender fuer Insidergeschaefte in Frage
    euInsider: Boolean,
    // Optionen an US-Boersen fuer diesen Wert
    mitOptionen: Boolean,
    seite: SeitenWaehrung,
    ziel: Geldanzeige,
    onNews: (MarketApi.NewsItem) -> Unit,
    onOpenRoute: (String) -> Unit,
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
    var optionen by remember(symbol) { mutableStateOf<MarketApi.OptionVolumes?>(null) }
    LaunchedEffect(symbol, mitOptionen) { if (mitOptionen) optionen = runCatching { state.market.optionVolumes(symbol) }.getOrNull() }
    val sec by state.sec.collectAsStateWithLifecycle()
    val bafin by state.bafin.collectAsStateWithLifecycle()
    val leer by state.leerpositionen.collectAsStateWithLifecycle()
    val fda by state.fda.collectAsStateWithLifecycle()
    val eu by state.euInsider.collectAsStateWithLifecycle()
    val welt by state.weltInsider.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val leerTitel = remember(leer, isin) { Leerpositionen.LAENDER.firstNotNullOfOrNull { leer[it]?.titel?.get(isin) } }
    val punkte = remember(quote?.nativePrice, profile, news, sec, bafin, leerTitel, fda, eu, welt, aktie) {
        if (!aktie) return@remember emptyList()
        val fdaJetzt = fda[symbol]
        val ab = java.time.LocalDate.now().minusDays(90).toString()
        val insider = (sec?.geschaefte?.filter { it.k.equals(symbol, true) }?.takeIf { it.isNotEmpty() }?.let { SecInsider.trend(it, ab) }
            ?: bafin?.eigengeschaefte?.filter { isin.isNotBlank() && it.isin == isin }?.takeIf { it.isNotEmpty() }?.let { Bafin.trend(it, ab) }
            // Register weiterer Laender (seit 5.9)
            ?: (welt[symbol]?.second ?: eu.values.map { d -> d.geschaefte.filter { isin.isNotBlank() && it.isin == isin } }.maxByOrNull { it.size })
                ?.takeIf { it.isNotEmpty() }?.let { de.troetenrepublik.app.market.EuInsider.trend(it, ab) })?.wert
        val fx = quote?.fx?.takeIf { it > 0 } ?: 1.0
        val p = profile
        val f = BullBear.Fakten(
            kurs = quote?.nativePrice ?: 0.0, kursziel = p?.targetPrice ?: 0.0, empfehlung = p?.recommendationMean ?: 0.0,
            // Yahoo-Werte kommen in Prozent (MarketApi rechnet mal 100), BullBear erwartet Anteile
            analysten = p?.analystCount ?: 0, umsatzWachstum = (p?.revenueGrowth ?: 0.0) / 100, gewinnWachstum = (p?.earningsGrowth ?: 0.0) / 100,
            gewinnMarge = (p?.profitMargin ?: 0.0) / 100, kasse = p?.totalCash ?: 0.0, schulden = p?.totalDebt ?: 0.0,
            freierCashflow = p?.freeCashflow ?: 0.0, marktwert = (quote?.marketCap ?: 0.0) / fx, kgv = quote?.peRatio ?: 0.0,
            kgvErwartet = p?.forwardPe ?: 0.0, beta = p?.beta ?: 0.0, ausschuettung = (p?.payoutRatio ?: 0.0) / 100,
            zumHoch = quote?.takeIf { it.weekHigh52 > 0 && it.price > 0 }?.let { it.price / it.weekHigh52 } ?: 0.0,
            dividendenRendite = (p?.dividendYield ?: 0.0) / 100, eigenkapitalRendite = (p?.returnOnEquity ?: 0.0) / 100,
            branche = p?.sector.orEmpty(), industrie = p?.industry.orEmpty(),
            ergebnisse = p?.epsHistory.orEmpty().map { it.actual to it.estimate }, insider = insider,
            leerverkauf = leerTitel?.summe,
            fdaZulassungen = fdaJetzt?.zulassungen?.count { it.art != de.troetenrepublik.app.market.Fda.Art.ANWENDUNG },
            fdaRueckrufe = fdaJetzt?.rueckrufe?.count { it.klasse == 1 },
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
    if (punkte.isEmpty() && !analystenDa && leerZeile == null && o == null) return
    var detail by remember(symbol) { mutableStateOf<Detail?>(null) }
    // Erklaerungen der Punkte (5.95): verborgen, ein Tipp auf den Punkt zeigt oder verbirgt sie, ohne Pfeil
    val erklaert = remember(symbol) { mutableStateMapOf<String, Boolean>() }
    var info by remember { mutableStateOf(false) }
    val waage = BullBear.waage(punkte)

    Column {
        // Kopf wie SectionHeader, rechts das Info-Symbol zur Waage
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.bwFuerUndWider, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (punkte.isNotEmpty()) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { info = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Info, S.crInfo, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (analystenDa && profile != null) {
            Spacer(Modifier.height(8.dp))
            AnalystenKarte(profile, quote, ziel) { detail = Detail.ANALYSTEN }
            Spacer(Modifier.height(6.dp))
        }
        // Stimmung zur Aktie: Optionshandel und Leerverkaeufe in je einer Zeile
        o?.let { v ->
            StimmungsZeile(S.crOptionen, S.crOptionenWert(Fmt.pct(v.longPct, 0)), v.longPct / 100.0) { detail = Detail.OPTIONEN }
        }
        leerZeile?.let { (urteil, zahl) -> StimmungsZeile(S.bhLeer, urteil, null, unter = zahl) { detail = Detail.LEER } }

        // Waage: welche Seite schwerer wiegt, als Balken; der erklaerende Satz steht hinter dem Info-Symbol
        waage?.let { w ->
            Column(Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp, vertical = 8.dp)) {
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
        }
        // Punkte, die schon als Karte oder Zeile oben stehen, zaehlen in der Waage, erscheinen aber nicht doppelt
        fun doppelt(pt: BullBear.Punkt): Boolean = when (pt.kennzahl) {
            BullBear.Kennzahl.ANALYSTEN_POSITIV, BullBear.Kennzahl.ANALYSTEN_SKEPTISCH -> analystenDa
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
                BullBear.Kennzahl.ANALYSTEN_POSITIV, BullBear.Kennzahl.ANALYSTEN_SKEPTISCH ->
                    listOf(Fmt.price(v[0], Derivatives.currencySymbol(quote?.nativeCurrency.orEmpty())), Fmt.signedPct(v[1], 0), v[2].toInt().toString())
                BullBear.Kennzahl.NETTOKASSE, BullBear.Kennzahl.CASHFLOW_NEGATIV -> listOf(geld(kotlin.math.abs(v[0])))
                BullBear.Kennzahl.SCHULDEN, BullBear.Kennzahl.CASHFLOW_POSITIV -> listOf(geld(v[0]), Fmt.pct(v[1], 0))
                BullBear.Kennzahl.UEBERTROFFEN, BullBear.Kennzahl.VERFEHLT -> listOf(v[0].toInt().toString(), v[1].toInt().toString())
                BullBear.Kennzahl.GEWINNE_STEIGEN -> listOf(Fmt.num(v[0], 1), Fmt.num(v[1], 1))
                BullBear.Kennzahl.BEWERTUNG_HOCH, BullBear.Kennzahl.SCHWANKUNG -> listOf(Fmt.num(v[0], 1))
                BullBear.Kennzahl.ZYKLISCH -> emptyList()
                BullBear.Kennzahl.INSIDER_KAUFEN, BullBear.Kennzahl.INSIDER_VERKAUFEN -> listOf(v[0].toInt().toString())
                BullBear.Kennzahl.LEERVERKAUF_HOCH -> listOf(Fmt.pct(v[0], 1))
                BullBear.Kennzahl.FDA_ZULASSUNG, BullBear.Kennzahl.FDA_RUECKRUF -> listOf(v[0].toInt().toString())
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
            Text(
                titel, style = MaterialTheme.typography.titleSmall, color = if (bull) signal.up else signal.down,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 2.dp),
            )
            sortiert.forEach { pt ->
                val wohin = ziel(pt)
                val warum = S.bbWarum(pt.schluessel)
                // Mit Erklaerung schaltet ein Tipp sie um, der Pfeil rechts fuehrt weiter; ohne Erklaerung fuehrt die
                // ganze Zeile weiter
                val antippen: (() -> Unit)? = when {
                    warum.isNotBlank() -> { { erklaert[pt.schluessel] = erklaert[pt.schluessel] != true } }
                    wohin != null -> { { detail = wohin } }
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
                            AuslaufText(
                                "„" + b.titel + "“" + (if (b.zeit > 0) " · " + Fmt.relative(b.zeit) else ""),
                                maxLines = 2, style = MaterialTheme.typography.bodySmall,
                                color = if (n != null) signal.link else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp).then(if (n != null) Modifier.clickable { onNews(n) } else Modifier),
                            )
                        }
                    }
                    if (wohin != null) {
                        Spacer(Modifier.width(8.dp))
                        // Eigene Tippflaeche fuer den Pfeil, wenn der Rest der Zeile die Erklaerung umschaltet
                        val pfeil = if (warum.isNotBlank()) Modifier.clip(CircleShape).clickable { detail = wohin }.padding(12.dp) else Modifier.padding(top = 4.dp)
                        ChevronRight(height = 12.dp, modifier = pfeil)
                    }
                }
            }
        }
    }

    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text(S.bwFuerUndWider) },
            text = {
                StichpunktText(
                    listOfNotNull(waage?.let { S.bbWaageSatz(it.stufe.ordinal) }, S.bbWaageHinweis, S.bbHinweis).joinToString("\n\n"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text(S.ok) } },
        )
    }

    when (detail) {
        Detail.ANALYSTEN -> AnalystenBlatt(state, symbol, name, profile, seite, oeffnen = { detail = null; onOpenRoute(it) }) { detail = null }
        Detail.INSIDER -> DetailBlatt(S.bwThema("FUEHRUNG"), name, { detail = null }) {
            // Im Preis? (5.92): Lauf seit dem juengsten Insiderkauf
            InsiderImPreis(state, symbol, isin, oeffnen = { detail = null; onOpenRoute(it) })
            BafinKarte(state, symbol, isin, name)
            if (euInsider) EuInsiderKarte(state, symbol, isin)
            if (usWert) {
                SecKarte(state, symbol)
                SecFormenKarte(state, symbol)
            }
        }
        Detail.LEER -> DetailBlatt(S.bhLeer, name, { detail = null }) {
            LeerverkaufKarte(state, symbol, isin, usWert, kopf = false)
            if (usWert) DarkPoolKarte(state, symbol)
            ErklaerungAufklappen(S.lvKurz)
        }
        Detail.OPTIONEN -> DetailBlatt(S.opTitel, name, { detail = null }) {
            o?.let { LongShortBars(it.longPct, onLong = { onOpenRoute("derivate/L/$symbol") }, onShort = { onOpenRoute("derivate/S/$symbol") }) }
            val hv by androidx.compose.runtime.produceState<Double?>(null, symbol) { value = runCatching { state.historischeVol(symbol) }.getOrNull() }
            OptionenKarte(state, symbol, quote?.nativeCurrency?.ifBlank { null } ?: "USD", hv, kopf = false)
        }
        Detail.BEWERTUNG -> BewertungBlatt(state, symbol, name, quote, profile) { detail = null }
        Detail.BILANZ -> BilanzBlatt(state, symbol, name, quote, profile) { detail = null }
        Detail.EREIGNISSE -> EventsSheet(state, symbol, name, profile, seite, onDismiss = { detail = null })
        null -> {}
    }
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
        ChevronRight(height = 13.dp)
    }
}
