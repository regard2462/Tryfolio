package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Historie078
import de.troetenrepublik.app.engine.Historie078.Art
import de.troetenrepublik.app.engine.Historie078.Punkt
import de.troetenrepublik.app.engine.Historie078.Spanne
import de.troetenrepublik.app.engine.MitarbeiterAnsicht
import de.troetenrepublik.app.engine.MitarbeiterReihe
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Historie078Abruf
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.plattform.rememberSpeichern
import java.time.LocalDate
import java.time.ZoneId

// Karten von Paket q78e (0.76): Bewertung gegen die eigene Geschichte, Leerverkaufsverlauf mit Fail-to-Deliver und
// Reg-SHO-Liste, Verlaeufe von Umsatz und Marktwert, CSV-Export von Finanzen und Kursen. Rechnen in engine/Historie078.kt.

private fun jahr(tag: Long): String = LocalDate.ofEpochDay(tag).year.toString()

// ------------------------------------------------------------ Bewertung gegen die eigene Geschichte

private suspend fun spannenLaden(state: AppState, symbol: String, quote: Quote, p: MarketApi.Profile, kontakt: String): List<Pair<Art, Spanne>> {
    val handel = quote.nativeCurrency
    val faktor = Historie078.waehrungsFaktor(handel, p.financialCurrency) ?: return emptyList()
    val kurs = quote.nativePrice.takeIf { it > 0 } ?: return emptyList()
    val kurse = state.gesamtHistorie(symbol)
    if (kurse.isEmpty()) return emptyList()
    val quellen = ArrayList<List<Historie078.Basis>>()
    Historie078Abruf.yahooBasen(symbol)?.let { quellen += it }
    // US-Firmen in Dollar: die XBRL-Reihen der SEC reichen weiter zurueck
    val kennung = SecInsider.kennung(kontakt)
    if (kennung != null && handel == "USD" && p.financialCurrency.let { it.isBlank() || it == "USD" } && !symbol.contains('.')) {
        val splits = runCatching { state.market.splits(symbol) }.getOrNull().orEmpty()
        Historie078Abruf.secReihen(symbol, kennung, splits)?.let { quellen += it.basen }
    }
    val aus = ArrayList<Pair<Art, Spanne>>()
    for (art in Art.entries) {
        var beste: Pair<List<Punkt>, Historie078.Basis>? = null
        for (basen in quellen) {
            val reihe = Historie078.reihe(art, basen, kurse, faktor)
            val letzte = basen.lastOrNull { Historie078.vielfach(art, it, kurs * faktor) != null } ?: continue
            if (beste == null || reihe.size > beste.first.size) beste = reihe to letzte
        }
        val (reihe, letzte) = beste ?: continue
        val heute = Historie078.vielfach(art, letzte, kurs * faktor)
        Historie078.spanne(reihe, heute)?.let { aus += art to it }
    }
    return aus
}

// Balken mit der ganzen bisherigen Spanne, den mittleren 80 Prozent, dem Median als Strich und dem heutigen Wert als Punkt.
@Composable
private fun SpannenBalken(s: Spanne) {
    val spur = MaterialTheme.colorScheme.outlineVariant
    val band = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val strich = MaterialTheme.colorScheme.onSurface
    val punkt = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(22.dp).padding(vertical = 4.dp)) {
        val lo = minOf(s.min, s.heute)
        val hi = maxOf(s.max, s.heute).let { if (it - lo < 1e-9) lo + 1.0 else it }
        val rand = 7.dp.toPx()
        fun x(v: Double) = rand + ((v - lo) / (hi - lo)).toFloat() * (size.width - 2 * rand)
        val mitte = size.height / 2f
        drawLine(spur, Offset(x(lo), mitte), Offset(x(hi), mitte), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(band, Offset(x(s.unten), mitte - 5.dp.toPx()), Size(x(s.oben) - x(s.unten), 10.dp.toPx()), CornerRadius(5.dp.toPx(), 5.dp.toPx()))
        drawLine(strich, Offset(x(s.median), mitte - 7.dp.toPx()), Offset(x(s.median), mitte + 7.dp.toPx()), strokeWidth = 2.dp.toPx())
        drawCircle(punkt, 6.dp.toPx(), Offset(x(s.heute), mitte))
    }
}

@Composable
private fun SpannenZeile(art: Art, s: Spanne) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    fun mal(v: Double) = Fmt.num(v, 1) + "×"
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(S.hqArt(art.ordinal), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(mal(s.heute), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
        // Kernaussage direkt unter der Bezeichnung, darunter der Balken; der Median steht nur einmal im Satz
        Text(
            S.hqSatz(S.hqArt(art.ordinal), mal(s.heute), mal(s.median), Fmt.pct(kotlin.math.abs(s.abstand), 0), s.abstand >= 0),
            style = MaterialTheme.typography.bodyMedium,
        )
        SpannenBalken(s)
        Text(S.hqBand(mal(s.unten), mal(s.oben)), style = MaterialTheme.typography.bodySmall, color = grau)
        Text(S.hqBereich(jahr(s.von), s.n), style = MaterialTheme.typography.labelSmall, color = grau)
    }
}

// KGV, KUV, KCV und EV/EBITDA gegen die Stichtage der letzten Jahre (je Quartal als TTM). Erscheint nur, wenn genug
// Stichtage vorliegen.
@Composable
internal fun BewertungSpanneKarte(state: AppState, symbol: String, quote: Quote?, profile: MarketApi.Profile?) {
    val p = profile ?: return
    val q = quote ?: return
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var erg by remember(symbol) { mutableStateOf<List<Pair<Art, Spanne>>?>(null) }
    LaunchedEffect(symbol, settings.secKontakt) {
        erg = runCatching { spannenLaden(state, symbol, q, p, settings.secKontakt) }.getOrNull()
    }
    val e = erg?.takeIf { it.isNotEmpty() } ?: return
    SectionHeader(S.hqTitel)
    e.forEach { (art, s) -> SpannenZeile(art, s) }
}

// ------------------------------------------------------------ Beschaeftigte

// Zahl der Beschaeftigten ueber die Jahre (Wikidata) als Balken, ergaenzt um den Wert der Zeile "Mitarbeitende" aus dem
// letzten Geschaeftsbericht. Ohne Reihe von mindestens zwei Jahren keine Karte.
@Composable
internal fun MitarbeiterKarte(symbol: String, profile: MarketApi.Profile?, name: String) {
    var reihe by remember(symbol) { mutableStateOf<List<MitarbeiterReihe.Punkt>?>(null) }
    LaunchedEffect(symbol) {
        reihe = runCatching { Historie078Abruf.mitarbeiter(listOf(profile?.longName.orEmpty(), name, profile?.shortName.orEmpty()), symbol) }.getOrNull()
    }
    val jahrBericht = profile?.yearly?.lastOrNull()?.let { y ->
        y.endDate.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L)).year } ?: y.label.take(4).toIntOrNull()
    }
    val r = remember(reihe, profile?.employees, jahrBericht) {
        reihe?.takeIf { it.size >= 2 }?.let { MitarbeiterAnsicht.letzte(MitarbeiterAnsicht.mitAktuell(it, (profile?.employees ?: 0).toLong(), jahrBericht)) }?.takeIf { it.size >= 2 }
    } ?: return
    val veraenderung = remember(r) { MitarbeiterAnsicht.veraenderung(r) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(S.hqMitarbeiter(r.first().jahr), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (veraenderung != null) ChangeValue(veraenderung, Fmt.signedPct(veraenderung, 0))
        }
        MitarbeiterBalken(r)
    }
}

// ------------------------------------------------------------ Leerverkaufsverlauf, Fail-to-Deliver, Reg SHO

private fun ausduennen(l: List<Pair<Long, Double>>, hoechstens: Int = 160): List<Pair<Long, Double>> {
    if (l.size <= hoechstens) return l
    val schritt = l.size.toDouble() / hoechstens
    return (0 until hoechstens).map { l[(it * schritt).toInt()] } + l.last()
}

// Zwei Linien auf gemeinsamer Zeitachse, jede mit eigener Skala: oben der Kurs, darunter die Leerverkaeufe.
@Composable
private fun LeerKursDiagramm(leer: List<Pair<Long, Double>>, kurs: List<Pair<Long, Double>>, leerName: String) {
    if (leer.size < 3 || kurs.size < 3) return
    val farbeLeer = MaterialTheme.colorScheme.primary
    val farbeKurs = MaterialTheme.colorScheme.onSurfaceVariant
    val linie = MaterialTheme.colorScheme.outlineVariant
    val t0 = leer.first().first
    val t1 = leer.last().first.coerceAtLeast(t0 + 1L)
    val k = remember(kurs, t0, t1) { ausduennen(kurs.filter { it.first in t0..t1 }) }
    if (k.size < 3) return
    // Beschriftung in der Farbe der jeweiligen Linie, oberhalb des Diagramms statt einer Legende
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(leerName, style = MaterialTheme.typography.labelMedium, color = farbeLeer, modifier = Modifier.weight(1f))
        Text(S.hqKurs, style = MaterialTheme.typography.labelMedium, color = farbeKurs, maxLines = 1, softWrap = false)
    }
    Canvas(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 4.dp)) {
        fun x(t: Long) = (t - t0).toFloat() / (t1 - t0) * size.width
        fun zeichnen(l: List<Pair<Long, Double>>, farbe: Color, breite: Float) {
            val lo = l.minOf { it.second }
            val hi = l.maxOf { it.second }.let { if (it - lo < 1e-9) lo + 1.0 else it }
            val p = Path()
            l.forEachIndexed { i, (t, v) ->
                val y = size.height * (1f - ((v - lo) / (hi - lo)).toFloat() * 0.9f - 0.05f)
                if (i == 0) p.moveTo(x(t), y) else p.lineTo(x(t), y)
            }
            drawPath(p, farbe, style = Stroke(width = breite, cap = StrokeCap.Round))
        }
        drawLine(linie, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        zeichnen(k, farbeKurs, 1.5.dp.toPx())
        zeichnen(leer, farbeLeer, 2.dp.toPx())
    }
}

private fun tagMs(tag: Long) = tag * 86_400_000L

// Short Interest der Nasdaq im Verlauf gegen den Kurs mit Tagen bis Deckung (nur an der Nasdaq notierte Werte), dazu die
// Reg-SHO-Liste der Nasdaq und die Fail-to-Deliver der SEC (alle US-Werte, nur mit Kontaktkennung wie die uebrigen SEC-Abrufe).
@Composable
internal fun LeerverkaufVerlaufKarte(state: AppState, symbol: String, quote: Quote?, profile: MarketApi.Profile?) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val nasdaq = Historie078.nasdaqBoerse(quote?.exchange.orEmpty())
    var leer by remember(symbol) { mutableStateOf<List<Historie078.Leer>?>(null) }
    var kurse by remember(symbol) { mutableStateOf<List<Candle>>(emptyList()) }
    var liste by remember(symbol) { mutableStateOf<Set<String>?>(null) }
    var ftd by remember(symbol) { mutableStateOf<List<Historie078.Ftd>?>(null) }
    LaunchedEffect(symbol, nasdaq) {
        if (!nasdaq) return@LaunchedEffect
        leer = runCatching { Historie078Abruf.leerverkauf(symbol) }.getOrNull()
        kurse = runCatching { state.gesamtHistorie(symbol) }.getOrDefault(emptyList())
        liste = runCatching { Historie078Abruf.regShoListe(LocalDate.now(ZoneId.of("America/New_York"))) }.getOrNull()
    }
    LaunchedEffect(symbol, settings.secKontakt) {
        val kennung = SecInsider.kennung(settings.secKontakt) ?: return@LaunchedEffect
        ftd = runCatching { Historie078Abruf.ftd(symbol, kennung, LocalDate.now(ZoneId.of("America/New_York"))) }.getOrNull()
    }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val zeilen = leer.orEmpty()
    val regSho = liste?.let { symbol.uppercase().replace('-', '.') in it }
    val f = ftd
    if (zeilen.isEmpty() && regSho == null && f == null) return
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (zeilen.isNotEmpty()) {
            Text(S.hqLeerTitel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            val z = zeilen.last()
            val streu = profile?.mehr?.let { Historie078.streubesitz(it.leerAktien, it.leerQuote) }
            val anteil = streu?.let { z.aktien / it * 100.0 }
            Text(
                S.hqLeerStand(Fmt.date(tagMs(z.tag)), Fmt.num(z.aktien / 1e6, 1) + " Mio.", anteil?.let { Fmt.pct(it, 1) }),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            val reihe = remember(zeilen, streu) { zeilen.map { tagMs(it.tag) to (if (streu != null) it.aktien / streu * 100.0 else it.aktien / 1e6) } }
            val kursPunkte = remember(kurse) { kurse.map { it.t to it.close } }
            Column(Modifier.padding(horizontal = 20.dp)) {
                LeerKursDiagramm(reihe, kursPunkte, if (streu != null) S.hqLeerAnteil else S.hqLeerAktien)
                val tage = remember(zeilen) { zeilen.mapNotNull { r -> r.deckung?.let { tagMs(r.tag) to it } } }
                z.deckung?.let { d ->
                    // Titel links, aktueller Wert mit Vorwert rechts, darunter die Reihe
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(S.hqLeerTageReihe, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(
                            S.hqLeerTage(Fmt.num(d, 1), zeilen.getOrNull(zeilen.size - 2)?.deckung?.let { Fmt.num(it, 1) }),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            maxLines = 1, softWrap = false,
                        )
                    }
                }
                if (tage.size >= 3) {
                    StandSeitZeile(tage)
                    VerlaufFrei(tage, 0.0)
                }
            }
        }
        if (regSho != null) {
            Text(S.hqRegSho(regSho), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (f != null) {
            Text(S.hqFtdTitel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (f.isEmpty()) {
                Text(S.hqFtdKeine, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            } else {
                val letzte = f.last()
                val hoch = f.maxBy { it.menge }
                Text(
                    S.hqFtdStand(Fmt.date(tagMs(letzte.tag)), Fmt.num(letzte.menge, 0)),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text(
                    S.hqFtdHoch(Fmt.num(hoch.menge, 0), Fmt.date(tagMs(hoch.tag))),
                    style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp),
                )
                val punkte = remember(f) { f.map { tagMs(it.tag) to it.menge } }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(S.hqFtdReihe, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 4.dp))
                    VerlaufFrei(punkte, 0.0, wertText = { Fmt.compact(it) })
                }
            }
        }
    }
}

// ------------------------------------------------------------ Verlaeufe, Export

private suspend fun verlaeufeLaden(state: AppState, symbol: String, quote: Quote?, p: MarketApi.Profile, kontakt: String): Pair<List<Punkt>, List<Punkt>> {
    val kennung = SecInsider.kennung(kontakt)
    val kurse = runCatching { state.gesamtHistorie(symbol) }.getOrDefault(emptyList())
    val yahoo = Historie078Abruf.yahooBasen(symbol).orEmpty()
    var umsatz = yahoo.mapNotNull { b -> b.umsatz?.let { Punkt(b.tag, it) } }
    var basen = yahoo
    if (kennung != null && quote?.nativeCurrency == "USD" && p.financialCurrency.let { it.isBlank() || it == "USD" } && !symbol.contains('.')) {
        val splits = runCatching { state.market.splits(symbol) }.getOrNull().orEmpty()
        Historie078Abruf.secReihen(symbol, kennung, splits)?.let { sec ->
            if (sec.umsatz.size > umsatz.size) umsatz = sec.umsatz
            if (sec.basen.size > basen.size) basen = sec.basen
        }
    }
    val markt = if (kurse.isEmpty()) emptyList() else Historie078.marktwerte(basen, kurse)
    return umsatz to markt
}

// Umsatz (zwoelf Monate) und Marktwert ueber die Stichtage; Betraege in der Waehrung der Seite.
@Composable
internal fun VerlaeufeKarte(state: AppState, symbol: String, quote: Quote?, profile: MarketApi.Profile, seite: SeitenWaehrung) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var erg by remember(symbol) { mutableStateOf<Pair<List<Punkt>, List<Punkt>>?>(null) }
    LaunchedEffect(symbol, settings.secKontakt) { erg = runCatching { verlaeufeLaden(state, symbol, quote, profile, settings.secKontakt) }.getOrNull() }
    val (umsatz, markt) = erg ?: return
    if (umsatz.size < 3 && markt.size < 3) return
    val bericht = profile.financialCurrency.ifBlank { quote?.nativeCurrency.orEmpty() }
    val handel = quote?.nativeCurrency.orEmpty()
    SectionHeader(S.hqVerlaeufe)
    @Composable
    fun reihe(titel: String, l: List<Punkt>, waehrung: String) {
        if (l.size < 3) return
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(titel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(seite.kompakt(l.last().wert, waehrung), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            }
            val punkte = remember(l) { l.map { tagMs(it.tag) to it.wert } }
            StandSeitZeile(punkte)
            VerlaufFrei(punkte, 0.0, wertText = { seite.kompakt(it, waehrung) }, zeitText = { Fmt.date(it) })
        }
    }
    reihe(S.hqUmsatzTtm, umsatz, bericht)
    reihe(S.hqMarktwert, markt, handel)
}

// CSV-Export von Finanzen und Kursen (OHLCV, alle Tage). Dateiname und Speicherort waehlt die Person.
@Composable
internal fun ExportZeilen(state: AppState, symbol: String, profile: MarketApi.Profile, jahre: List<de.troetenrepublik.app.engine.Qualitaet.Jahr> = emptyList()) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val deutsch = settings.sprache == Lang.DE
    val bericht = profile.financialCurrency
    val finanzen = remember(profile, jahre) {
        // Ein Wert 0 im Profil heisst "nicht genannt"
        fun zeile(art: String, p: MarketApi.FinPeriod) = Historie078.FinZeile(
            art,
            p.endDate.takeIf { e -> e > 0 }?.let { e -> LocalDate.ofEpochDay(Math.floorDiv(e, 86_400_000L)).toString() }
                ?: de.troetenrepublik.app.engine.FinanzTabelle.periode(p.label),
            p.revenue.takeIf { it != 0.0 }, p.grossProfit.takeIf { it != 0.0 }, p.operatingIncome.takeIf { it != 0.0 }, p.earnings.takeIf { it != 0.0 },
        )
        Historie078.finZeilen(jahre, profile.yearly.map { zeile("Y", it) }, profile.quarterly.map { zeile("Q", it) })
    }
    val finanzenSichern = rememberSpeichern(
        inhalt = { Historie078.finanzenCsv(finanzen, bericht, deutsch) },
        mime = "text/csv",
    ) { _, ok -> state.showToast(if (ok) S.hqExportFertig(finanzen.size) else S.exportFailed) }
    var kerzen by remember(symbol) { mutableStateOf<List<Candle>>(emptyList()) }
    LaunchedEffect(symbol) { kerzen = runCatching { state.gesamtHistorie(symbol) }.getOrDefault(emptyList()) }
    val kurseSichern = rememberSpeichern(
        inhalt = { Historie078.ohlcvCsv(kerzen, deutsch) },
        mime = "text/csv",
    ) { _, ok -> state.showToast(if (ok) S.hqExportFertig(kerzen.size) else S.exportFailed) }
    val name = symbol.replace(Regex("[^A-Za-z0-9._-]"), "_")
    if (finanzen.isNotEmpty()) ActionLineWide(S.hqExportFinanzen) { finanzenSichern("tryfolio-$name-finanzen.csv") }
    if (kerzen.isNotEmpty()) ActionLineWide(S.hqExportKurse) { kurseSichern("tryfolio-$name-kurse.csv") }
}
