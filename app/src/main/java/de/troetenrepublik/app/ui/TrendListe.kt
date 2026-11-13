package de.troetenrepublik.app.ui

import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.engine.Branchentrends
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.engine.Sektortabelle
import de.troetenrepublik.app.engine.Themenkoerbe
import de.troetenrepublik.app.engine.TrendListe
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// Liste „Branchen und Themen“ im Reiter Trends: Branchen, Themenkoerbe und Themen der Durchbrueche in einer Liste
// (Zuordnung in engine/TrendListe.kt). Die zugeklappte Zeile sieht aus wie eine Wertzeile: rundes Zeichen, Name, eine
// gedaempfte Unterzeile, rechts die Kursentwicklung. Aufgeklappt folgen als eigene Eintraege der Liste: Verlauf der
// Branche, die Aktien des Korbs mit Breite und Median und die Tabelle der Durchbrueche; bei mehreren Teilen stehen
// Aktien und Durchbrueche hinter einer Zwischenzeile.

// Daten der Liste fuer den gewaehlten Zeitraum
internal class TrendListeDaten(
    val zeilen: List<TrendListe.Zeile>,
    // Kurse der Branchen sind da
    val geladen: Boolean,
    val kurse: Map<String, List<Candle>>,
    val zahlen: Map<String, Map<String, Double>>,
    val quotes: Map<String, Quote>,
    val tage: Int,
    val inhalt: Durchbrueche.Inhalt?,
    val neuStand: NeuStand,
    val jetzt: Long,
    val deutsch: Boolean,
    val links: UriHandler,
)

// Was offen ist: die Zeile, darin die Zwischenzeile (aktien oder db:<thema>) und der Unterabschnitt der Durchbruch-Tabelle
@Stable
internal class TrendListeZustand {
    var offen by mutableStateOf<String?>(null)
    var teil by mutableStateOf<String?>(null)
    var unter by mutableStateOf<String?>(null)
    var info by mutableStateOf(false)
    var artikel by mutableStateOf<MarketApi.NewsItem?>(null)

    fun zeile(id: String) {
        offen = if (offen == id) null else id
        teil = null
        unter = null
    }

    fun teil(k: String) {
        teil = if (teil == k) null else k
        unter = null
    }

    // Oeffnet die Zeile eines Durchbruch-Themas mit seiner Tabelle; liefert den Schluessel der Zeile in der Liste
    fun zuThema(thema: String): String? {
        val e = TrendListe.zuDurchbruch(thema) ?: return null
        offen = e.id
        teil = DB + thema
        unter = null
        return ZEILE + e.id
    }
}

private const val ZEILE = "t-"
private const val DB = "db:"
private const val AKTIEN = "aktien"
internal const val TREND_KOPF = "trend-kopf"

// Laedt Kurse der Branchen, Aenderungen der Korbwerte und die Durchbrueche und baut daraus die Zeilen. zeitraum: Stelle
// in Branchentrends.ZEITRAEUME.
@Composable
internal fun rememberTrendListe(state: AppState, zeitraum: Int): TrendListeDaten {
    val deutsch = I18n.lang == Lang.DE
    val links = appLinks()
    var kurse by remember { mutableStateOf<Map<String, List<Candle>>?>(null) }
    var themenKurse by remember { mutableStateOf<Map<String, List<Candle>>>(emptyMap()) }
    // KGV, Dividendenrendite und Fondsgroesse je ETF (eine Abfrage fuer alle)
    var zahlen by remember { mutableStateOf<Map<String, Map<String, Double>>>(emptyMap()) }
    val symbole = remember { Themenkoerbe.ALLE_SYMBOLE }
    var quotes by remember { mutableStateOf<Map<String, Quote>>(emptyMap()) }
    var inhalt by remember { mutableStateOf(state.durchbruecheInhaltBekannt()) }
    var meldungen by remember { mutableStateOf(state.durchbruecheStand()) }
    var fertig by remember { mutableStateOf(meldungen != null) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { kurse = state.branchenKurse() }
            // Themen-ETFs der Zeilen ohne Branche: Kurse wie die Branchen-ETFs, ein Abruf
            launch { themenKurse = runCatching { state.schlusskurse(TrendListe.THEMEN_ETFS, "1y") }.getOrDefault(emptyMap()) }
            launch { zahlen = runCatching { state.kennzahlen(Branchentrends.BRANCHEN.map { it.symbol } + TrendListe.THEMEN_ETFS, Sektortabelle.FELDER) }.getOrDefault(emptyMap()) }
            launch { quotes = runCatching { state.market.snapshot(symbole) }.getOrDefault(emptyMap()) }
            launch { inhalt = state.durchbruecheInhalt() }
        }
    }
    LaunchedEffect(deutsch) {
        ohneAbbruch { state.durchbrueche() }.getOrNull()?.let { meldungen = it }
        fertig = true
    }
    val tage = Branchentrends.ZEITRAEUME.getOrElse(zeitraum) { 7 }
    // Korbwerte: beim Tag aus den Kursen von heute, sonst aus Schlusskursen ueber den Zeitraum
    var lang by remember(tage) { mutableStateOf<Map<String, Double>?>(null) }
    LaunchedEffect(tage) { if (tage > 1) lang = runCatching { ZeitraumKurse.aenderungen(state, symbole, tage) }.getOrDefault(emptyMap()) }
    val koerbe = remember(quotes, lang, tage) {
        val aenderung = if (tage <= 1) quotes.mapNotNull { (s, q) -> q.takeIf { it.prevClose > 0 && it.price > 0 }?.let { s to it.sitzungPct } }.toMap() else lang.orEmpty()
        Themenkoerbe.KOERBE.associate { it.schluessel to Themenkoerbe.auswerten(it, aenderung) }
    }
    val alleKurse = remember(kurse, themenKurse) { themenKurse + kurse.orEmpty() }
    val kursJe = remember(alleKurse, tage) {
        (Branchentrends.BRANCHEN.map { it.symbol } + TrendListe.THEMEN_ETFS).mapNotNull { s -> alleKurse[s]?.let { Branchentrends.veraenderung(it, tage) }?.let { s to it } }.toMap()
    }
    // Zeitpunkt des Aufbaus, damit die Alter beim Scrollen nicht springen
    val jetzt = remember(meldungen) { System.currentTimeMillis() }
    val bloecke = remember(inhalt, meldungen, jetzt) { Durchbrueche.trendBloecke(inhalt, meldungen.orEmpty(), jetzt) }
    val zeilen = remember(kursJe, koerbe, bloecke) { TrendListe.zeilen(kursJe, koerbe, bloecke) }
    // Ohne Abruf und ohne gespeicherten Stand steht „Nicht geladen“, nicht „Keine neuen Durchbrüche“
    val neuStand = if (meldungen != null) NeuStand.GELADEN else if (fertig) NeuStand.FEHLT else NeuStand.LAEDT
    return remember(zeilen, kurse, alleKurse, zahlen, quotes, tage, inhalt, neuStand, jetzt, deutsch, links) {
        TrendListeDaten(zeilen, kurse != null, alleKurse, zahlen, quotes, tage, inhalt, neuStand, jetzt, deutsch, links)
    }
}

// Die Eintraege der Liste. Jede Zeile und jeder aufgeklappte Teil ist ein eigener Eintrag, damit das Rollen fluessig bleibt.
internal fun LazyListScope.trendListe(
    state: AppState, d: TrendListeDaten, z: TrendListeZustand, zeitraum: Int, onZeitraum: (Int) -> Unit, onOpen: (String) -> Unit,
) {
    item(key = TREND_KOPF, contentType = "kopf") {
        WidgetLangDruck(state, WidgetVorgabe.sektoren(), Modifier.fillMaxWidth()) { InfoUeberschrift(S.tlTitel) { z.info = true } }
        RangeSelector(
            HEATMAP_ZEITRAEUME, selected = HEATMAP_ZEITRAEUME.getOrNull(zeitraum),
            onSelect = { r -> onZeitraum(HEATMAP_ZEITRAEUME.indexOf(r).coerceAtLeast(0)) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
    if (!d.geladen) { item(key = "trend-laedt") { EmptyHint(S.trLaedt) }; return }
    if (d.zeilen.isEmpty()) { item(key = "trend-leer") { EmptyHint(S.heatmapLeer) }; return }
    d.zeilen.forEach { zeile ->
        val e = zeile.eintrag
        val auf = z.offen == e.id
        val schluessel = ZEILE + e.id
        item(key = schluessel, contentType = "zeile") {
            // Langes Druecken legt das Widget der Art an: Sektoren, Themenkoerbe oder Durchbrueche
            val vorgabe = when {
                zeile.branche != null -> WidgetVorgabe.sektoren()
                zeile.korb != null -> WidgetVorgabe.themen()
                else -> WidgetVorgabe.abschnitt("durchbrueche")
            }
            WidgetLangDruck(state, vorgabe, Modifier.fillMaxWidth()) {
                TrendZeile(zeile, zeilenName(e, d.deutsch), d.zahlen) { z.zeile(e.id) }
            }
        }
        if (!auf) return@forEach
        zeile.fonds?.let { f ->
            item(key = "$schluessel-verlauf", contentType = "verlauf") {
                val marktwert = remember(d.zahlen, f) { Sektortabelle.zeile(f, null, d.zahlen[f]).marktwert }
                BranchenDepot(state, f, d.kurse[f].orEmpty(), d.tage, marktwert) { onOpen(f) }
            }
        }
        zeile.korb?.let { k ->
            val aktienAuf = zeile.einTeil || z.teil == AKTIEN
            item(key = "$schluessel-aktien", contentType = "teil") {
                WidgetLangDruck(state, WidgetVorgabe.themen(), Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        if (!zeile.einTeil) TeilZeile(S.stocks, null, k.anzahl.toString(), MaterialTheme.colorScheme.onSurfaceVariant, aktienAuf) { z.teil(AKTIEN) }
                        KorbKennzahlen(k, zeile.korbMedian)
                    }
                }
            }
            if (aktienAuf) items(k.werte, key = { "$schluessel-a-" + it.first }, contentType = { "aktie" }) { (s, v) ->
                val q = d.quotes[s]
                // Aktien des Korbs eingerueckt und kleiner, durch feine Linien getrennt (0.79)
                Column(Modifier.fillMaxWidth()) {
                    Trennlinie(
                        Modifier.padding(start = 70.dp, end = 20.dp), thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                    AssetRow(
                        state = state, symbol = s,
                        name = Symbols.find(s)?.name ?: q?.name?.ifBlank { null } ?: s,
                        subtitle = q?.takeIf { it.price > 0 }?.let { Fmt.price(it.price) },
                        change = v, changeText = Fmt.pct(abs(v)), eingerueckt = true, onClick = { onOpen(s) },
                    )
                }
            }
        }
        zeile.bloecke.forEach { b ->
            val id = b.thema.id
            val dbAuf = zeile.einTeil || z.teil == DB + id
            if (!zeile.einTeil) item(key = "$schluessel-db-$id", contentType = "teil") {
                WidgetLangDruck(state, WidgetVorgabe.abschnitt("durchbrueche"), Modifier.fillMaxWidth()) {
                    TeilZeile(
                        S.dbTitel, if (zeile.bloecke.size > 1) b.thema.name(d.deutsch) else null,
                        b.neu.takeIf { it.isNotEmpty() }?.let { S.dbNeuAnzahl(it.size) }, MaterialTheme.colorScheme.primary, dbAuf,
                    ) { z.teil(DB + id) }
                }
            }
            if (dbAuf) item(key = "$schluessel-tabelle-$id", contentType = "tabelle") {
                DurchbruchTabelle(
                    state, b, d.jetzt, d.deutsch, d.neuStand, z.unter, { k -> z.unter = if (z.unter == k) null else k },
                    onOpen, { z.artikel = it }, { d.links.openUri(it) },
                )
            }
        }
    }
}

// Blaetter der Liste: Erklaerung hinter der Ueberschrift und die angetippte Meldung. Steht ausserhalb der Liste.
@Composable
internal fun TrendListeBlaetter(d: TrendListeDaten, z: TrendListeZustand) {
    if (z.info) {
        // Stand des gepflegten Teils („JJJJ-MM-TT“ in der Datei)
        val stand = d.inhalt?.stand?.split("-")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size >= 2 }?.let { S.dbStand(it[0], it[1]) }
        val text = listOfNotNull(S.trBranchenHinweis, S.dbTitel + ": " + S.dbInfo, stand).joinToString("\n\n")
        MarktInfoBlatt(S.tlTitel, text) { z.info = false }
    }
    z.artikel?.let { NewsSheet(it, onDismiss = { z.artikel = null }) }
}

private fun zeilenName(e: TrendListe.Eintrag, deutsch: Boolean): String =
    TrendListe.name(e, deutsch) ?: Themenkoerbe.KOERBE.indexOfFirst { it.schluessel == e.korb }.takeIf { it >= 0 }?.let { S.korbName(it) } ?: e.id

// Zugeklappte Zeile im Aufbau der Wertzeilen: rundes Zeichen (Logo des ETF, sonst Anfangsbuchstabe), Name, eine
// gedaempfte Unterzeile und rechts die Kursentwicklung (ETF, sonst Median des Korbs). Unterzeile: KGV und Dividende
// des ETF, ohne ETF die Zahl der Aktien des Korbs, ohne Korb die Zahl neuer Durchbrueche.
@Composable
private fun TrendZeile(z: TrendListe.Zeile, name: String, zahlen: Map<String, Map<String, Double>>, onKlick: () -> Unit) {
    val f = z.fonds
    val unter = remember(z, zahlen) {
        when {
            f != null -> {
                val k = Sektortabelle.zeile(f, null, zahlen[f])
                listOfNotNull(k.kgv?.let { S.stKgv + " " + Fmt.num(it, 1) }, k.rendite?.let { S.stRendite + " " + Fmt.pct(it, 2) }).joinToString(" · ").ifEmpty { f }
            }
            z.korb != null -> S.tlAktien(z.korb.anzahl)
            z.neu > 0 -> S.tlNeu(z.neu)
            else -> S.dbTitel
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onKlick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (f != null) InstrumentLogo(f, name) else InstrumentAvatar(z.eintrag.id, name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            PunktText(unter, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        z.wert?.let {
            Spacer(Modifier.width(10.dp))
            MetricValue(it, Fmt.pct(abs(it)))
        }
    }
}

// Kennzahlen des Korbs im aufgeklappten Teil: Breite als Balken mit „n von m steigen“ und, neben einer Branche, der
// Median der Aktien. Bezeichnung links, Wert rechts.
@Composable
private fun KorbKennzahlen(k: Themenkoerbe.Ergebnis, median: Double?) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(start = 70.dp, end = 20.dp).padding(bottom = 8.dp)) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val anteil = (k.steigend ?: 0.0).toFloat().coerceIn(0f, 1f)
            Row(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(signal.down.copy(alpha = 0.35f))) {
                if (anteil > 0f) Box(Modifier.weight(anteil).height(6.dp).background(signal.up))
                if (anteil < 1f) Spacer(Modifier.weight(1f - anteil))
            }
            Spacer(Modifier.width(12.dp))
            Text(S.korbSteigen(k.anzahlSteigend, k.anzahl), style = MaterialTheme.typography.labelMedium, color = grau, maxLines = 1, softWrap = false)
        }
        if (median != null) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.tlMedian, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            MetricValue(median, Fmt.pct(abs(median)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Zwischenzeile in einer aufgeklappten Zeile mit mehreren Teilen: Titel, klein darunter ein Zusatz, rechts eine Zahl
@Composable
private fun TeilZeile(titel: String, zusatz: String?, rechts: String?, rechtsFarbe: Color, offen: Boolean, onKlick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onKlick).padding(start = 70.dp, end = 20.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.labelLarge)
            if (zusatz != null) NamenText(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rechts != null) {
            Spacer(Modifier.width(12.dp))
            Text(rechts, style = MaterialTheme.typography.bodySmall, color = rechtsFarbe, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.width(8.dp))
        AufklappPfeil(offen)
    }
}

// Antippbare Ueberschrift mit Info-Symbol rechts: der Tipp oeffnet die Erklaerung.
@Composable
private fun InfoUeberschrift(titel: String, onInfo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onInfo).padding(start = 20.dp, end = 8.dp)
            .padding(top = LocalAbschnittOben.current - 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp).size(20.dp),
        )
    }
}
