package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Analysis
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.Bewertungsblick.Ampel
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.Kapitalkennzahlen
import de.troetenrepublik.app.engine.Qualitaet
import de.troetenrepublik.app.engine.ReverseDcf
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import kotlin.math.abs
import kotlin.math.roundToInt

// Bewertung und Kennzahlen auf der Wertpapierseite (5.92): sechs Kernwerte, darunter bei Aktien drei Ampelzeilen
// (Bewertung, Bilanz, Wachstum) in Alltagssprache, jede antippbar fuer die Einzelheiten. Alle Kennzahlen stehen im
// Blatt, der Vergleich mit aehnlichen Aktien auf eigener Seite.

// Betraege aus der Berichtswaehrung in der Waehrung, in der die Seite gerade Kurse zeigt. Ohne Wechselkurs bleibt ein
// Betrag in seiner Waehrung.
class SeitenWaehrung(val ziel: String, private val fx: Map<String, Double>) {

    fun umrechnen(wert: Double, waehrung: String): Pair<Double, String> {
        if (waehrung.isBlank() || ziel.isBlank() || waehrung == ziel) return wert to waehrung.ifBlank { ziel }
        val von = ExchangeRates.rate(waehrung, fx) ?: return wert to waehrung
        val nach = ExchangeRates.rate(ziel, fx) ?: return wert to waehrung
        return wert / von * nach to ziel
    }

    fun kompakt(wert: Double, waehrung: String): String {
        val (v, w) = umrechnen(wert, waehrung)
        return (if (v < 0) "−" else "") + Fmt.mitSymbol(Fmt.compact(abs(v)), Derivatives.currencySymbol(w))
    }

    fun preis(wert: Double, waehrung: String): String {
        val (v, w) = umrechnen(wert, waehrung)
        return Fmt.price(v, Derivatives.currencySymbol(w))
    }

    // Dieselben Wechselkurse mit anderer Zielwaehrung.
    fun nach(waehrung: String): SeitenWaehrung = SeitenWaehrung(waehrung, fx)
}

@Composable
internal fun ampelFarbe(a: Ampel?): Color = when (a) {
    Ampel.GRUEN -> LocalSignal.current.up
    Ampel.GELB -> LocalSignal.current.warn
    Ampel.ROT -> LocalSignal.current.down
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun ampelZeichen(a: Ampel?): String = when (a) {
    Ampel.GRUEN -> AmpelZeichen.GUT
    Ampel.GELB -> AmpelZeichen.ACHTUNG
    Ampel.ROT -> AmpelZeichen.KRITISCH
    null -> AmpelZeichen.HINWEIS
}

// Zeile mit Ampel, Titel und einem Satz; ein Tipp oeffnet die Einzelheiten.
@Composable
internal fun AmpelZeile(titel: String, ampel: Ampel?, satz: String, unter: String? = null, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmpelPunkt(ampelZeichen(ampel), ampelFarbe(ampel), Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(satz, style = MaterialTheme.typography.bodyMedium)
            if (!unter.isNullOrBlank()) PunktText(unter)
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            ChevronRight(height = 13.dp)
        }
    }
}

// Blatt fuer Einzelheiten: Titel, Name des Werts, darunter der Inhalt zum Rollen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailBlatt(titel: String, name: String, onDismiss: () -> Unit, inhalt: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(titel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            NamenText(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            inhalt()
        }
    }
}

// Was die Bewertungszeile braucht: heutiges KGV, Ziel-KGV (Branche oder 15), eingepreistes, bisheriges und
// erwartetes Wachstum.
internal data class Bewertungslage(
    val kgv: Double, val zielKgv: Double, val noetig: ReverseDcf.Ergebnis, val bisher: Double?, val erwartet: Double?,
    val bewertung: Bewertungsblick.Bewertung,
)

internal fun bewertungslage(p: MarketApi.Profile, quote: Quote?, branche: Double?): Bewertungslage? {
    val kgv = p.trailingPe.takeIf { it > 0 } ?: quote?.peRatio?.takeIf { it > 0 } ?: return null
    if (p.eps < 0) return null
    val zielKgv = branche?.roundToInt()?.coerceIn(8, 35)?.toDouble() ?: Bewertungsblick.ZIEL_KGV
    val noetig = ReverseDcf.wachstum(kgv, Bewertungsblick.RENDITE, zielKgv, Bewertungsblick.JAHRE) ?: return null
    val bisher = ReverseDcf.bisher(p.yearly.map { it.earnings })
    val erwartet = p.forwardPe.takeIf { it > 0 }?.let { (kgv / it - 1.0) * 100.0 }
        ?: p.mehr.gewinnWachstumFolgejahr.takeIf { it != 0.0 }
    return Bewertungslage(kgv, zielKgv, noetig, bisher, erwartet, Bewertungsblick.bewertung(noetig.wachstum, bisher, erwartet))
}

// Boersenwert in der Berichtswaehrung; quote.marketCap steht in Euro. null, wenn keine Umrechnung moeglich ist.
internal fun boersenwertBericht(waehrung: String, p: MarketApi.Profile, quote: Quote?, fx: Map<String, Double>): Double? = run {
    val r = ExchangeRates.rate(waehrung, fx) ?: return@run null
    quote?.marketCap?.takeIf { it > 0 }?.let { it * r }
        ?: p.marketCap.takeIf { it > 0 }?.let { mc -> ExchangeRates.rate(quote?.nativeCurrency.orEmpty(), fx)?.let { mc / it * r } }
}

// Piotroski und Altman aus den Jahresabschluessen; der Boersenwert fuer Altman in der Berichtswaehrung.
internal fun qualitaetAus(jahre: List<Qualitaet.Jahr>, p: MarketApi.Profile, quote: Quote?, fx: Map<String, Double>): Pair<Qualitaet.Piotroski?, Qualitaet.Altman?> {
    val f = Qualitaet.piotroski(jahre)
    val letztes = jahre.lastOrNull()
    val waehrung = letztes?.waehrung?.ifBlank { null } ?: p.financialCurrency.ifBlank { quote?.nativeCurrency.orEmpty() }
    val z = letztes?.let { l -> Qualitaet.fassung(p.sector, p.industry)?.let { Qualitaet.altman(l, boersenwertBericht(waehrung, p, quote, fx), it) } }
    return f to z
}

// Kurs/Cashflow, Zinsdeckung, ROIC und Owner Earnings aus dem letzten Geschaeftsjahr; Banken und Versicherer wie bei
// Piotroski und Altman ausgenommen.
internal fun kapitalAus(jahre: List<Qualitaet.Jahr>, p: MarketApi.Profile, quote: Quote?, fx: Map<String, Double>): Kapitalkennzahlen.Ergebnis? {
    if (Qualitaet.ausgenommen(p.sector, p.industry)) return null
    val waehrung = jahre.lastOrNull()?.waehrung?.ifBlank { null } ?: p.financialCurrency.ifBlank { quote?.nativeCurrency.orEmpty() }
    return Kapitalkennzahlen.berechne(jahre, boersenwertBericht(waehrung, p, quote, fx))
}

@Composable
internal fun BewertungUndKennzahlen(
    state: AppState,
    symbol: String,
    name: String,
    quote: Quote?,
    profile: MarketApi.Profile?,
    aktie: Boolean,
    kern: List<Pair<String, String>>,
    seite: SeitenWaehrung,
    onRowClick: (String) -> Unit,
    aktion: (String) -> Pair<String, () -> Unit>?,
    onAlle: () -> Unit,
    onFinanzen: () -> Unit,
    onVergleich: () -> Unit,
) {
    var blatt by remember(symbol) { mutableStateOf<String?>(null) }
    SectionHeader(if (aktie) S.bkTitel else S.keyFigures)
    StatPages(listOf(kern), onRowClick = onRowClick, aktion = aktion)
    val p = profile
    if (aktie && p != null) {
        val fx by state.market.fxKurse.collectAsStateWithLifecycle()
        // Bewertung: eingepreistes Wachstum gegen das bisherige, dazu der faire Wert bei bisherigem Wachstum
        var branche by remember(symbol) { mutableStateOf<Double?>(null) }
        LaunchedEffect(symbol) { branche = branchenKgv(state, symbol) }
        val lage = remember(p, quote?.peRatio, branche) { bewertungslage(p, quote, branche) }
        val bank = Bewertungsblick.bilanzgeschaeft(p.industry)
        if (bank) {
            // Banken und Versicherer nach Buchwert und Eigenkapitalrendite (5.94), das KGV sagt bei ihnen wenig
            val b = remember(p) { Bewertungsblick.bankBewertung(p.priceToBook, p.returnOnEquity) }
            if (b != null) AmpelZeile(
                S.bkBewertung, b.ampel,
                S.bkBankSatz(Fmt.num(b.kbv, 2), Fmt.pct(b.roe, 1), b.angemessen?.let { Fmt.num(it, 2) }),
                S.bkBankUnter(Fmt.pct(Bewertungsblick.BANK_KAPITALKOSTEN, 0), Fmt.pct(Bewertungsblick.BANK_WACHSTUM, 0)),
                onClick = onAlle,
            )
        } else if (lage != null) {
            val e = lage.noetig
            // Art: 1 mehr als (oberer Rand), -1 weniger als (unterer Rand), 2 der Gewinn darf sogar schrumpfen
            val art = when {
                e.ausserhalb -> if (e.wachstum > 0) 1 else -1
                e.wachstum < 0 -> 2
                else -> 0
            }
            val satz = S.bkBewertungSatz(Fmt.pct(abs(e.wachstum), 0), art, lage.bisher?.let { Fmt.signedPct(it, 0) })
            val waehrung = quote?.nativeCurrency.orEmpty()
            val kurs = quote?.nativePrice ?: 0.0
            val annahme = Bewertungsblick.annahme(lage.bisher ?: lage.erwartet)
            val fair = annahme?.let { Bewertungsblick.fairerWert(kurs, lage.kgv, it, lage.zielKgv) }
            val unter = if (fair != null && fair > 0 && annahme != null) {
                val abstand = (kurs / fair - 1.0) * 100.0
                S.bkFairerWert(seite.preis(fair, waehrung), Fmt.pct(annahme, 0), Fmt.pct(abs(abstand), 0), abstand >= 0)
            } else null
            AmpelZeile(S.bkBewertung, lage.bewertung.ampel, satz, unter) { blatt = "bewertung" }
        } else if (p.eps < 0 || (quote?.peRatio ?: 0.0) < 0) {
            AmpelZeile(S.bkBewertung, null, S.bkBewertungVerlust, null) { blatt = "bewertung" }
        }
        // Bilanz einer Bank: Gesamtkapitalrendite und Eigenkapitalquote (5.94)
        if (bank) {
            var jahre by remember(symbol) { mutableStateOf<List<Qualitaet.Jahr>?>(null) }
            LaunchedEffect(symbol) { jahre = state.jahresabschluesse(symbol) }
            val letztes = jahre?.lastOrNull()
            val bb = remember(p, letztes) {
                Bewertungsblick.bankBilanz(p.mehr.gesamtkapitalRendite, Bewertungsblick.ekQuote(letztes?.bilanzsumme, letztes?.verbindlichkeiten))
            }
            if (bb != null) AmpelZeile(
                S.balance, bb.ampel, S.bkBankBilanzSatz(bb.roa?.let { Fmt.pct(it, 2) }, bb.ekQuote?.let { Fmt.pct(it, 1) }), S.bkBankBilanzUnter,
                onClick = onAlle,
            )
        }
        // Bilanz: Ampel und ein Satz, Piotroski und Altman erst im Blatt
        if (!Qualitaet.ausgenommen(p.sector, p.industry)) {
            var jahre by remember(symbol) { mutableStateOf<List<Qualitaet.Jahr>?>(null) }
            LaunchedEffect(symbol) { jahre = state.jahresabschluesse(symbol) }
            val j = jahre
            if (j == null) AmpelZeile(S.balance, null, S.loading, null, null)
            else {
                val (f, z) = remember(j, p, quote?.marketCap, fx) { qualitaetAus(j, p, quote, fx) }
                Bewertungsblick.bilanz(f, z)?.let { b ->
                    AmpelZeile(S.balance, b.ampel, S.bkBilanzSatz(b.ampel.ordinal, b.gruende.map { S.bkGrund(it.name) })) { blatt = "bilanz" }
                }
            }
        }
        // Wachstum: Umsatz und Gewinn des letzten Geschaeftsjahrs in der Waehrung der Seite
        val latest = p.yearly.lastOrNull()
        val prev = p.yearly.dropLast(1).lastOrNull()
        val umsatz = latest?.revenue?.takeIf { it > 0 } ?: p.revenue.takeIf { it > 0 }
        if (umsatz != null) {
            val fw = p.financialCurrency.ifBlank { quote?.nativeCurrency.orEmpty() }
            // Das Quartalswachstum nur ohne Geschaeftsjahr, sonst passte es nicht zur Beschriftung
            val uw = Bewertungsblick.veraenderung(latest?.revenue, prev?.revenue) ?: p.revenueGrowth.takeIf { it != 0.0 && latest == null }
            val gewinn = latest?.earnings?.takeIf { it != 0.0 }
            val gw = Bewertungsblick.veraenderung(latest?.earnings, prev?.earnings)
            AmpelZeile(
                S.bkWachstum, Bewertungsblick.wachstum(uw, gw),
                S.bkWachstumSatz(seite.kompakt(umsatz, fw), uw?.let { Fmt.signedPct(it, 0) }, gewinn?.let { seite.kompakt(it, fw) }, gw?.let { Fmt.signedPct(it, 0) }),
                latest?.label?.takeIf { it.isNotBlank() }?.let { S.bkWachstumUnter(it.take(4)) },
            ) { onFinanzen() }
        }
    }
    ActionLineWide(S.bwAlleKennzahlen, onClick = onAlle)
    if (aktie) NavRow(S.kvEinstieg, S.kvEinstiegSub, onVergleich)

    when (blatt) {
        "bewertung" -> BewertungBlatt(state, symbol, name, quote, profile) { blatt = null }
        "bilanz" -> BilanzBlatt(state, symbol, name, quote, profile) { blatt = null }
    }
}

// Einzelheiten zur Bewertung: was im Kurs steckt (mit waehlbaren Annahmen) und der faire Wert zum Selbstrechnen.
@Composable
internal fun BewertungBlatt(state: AppState, symbol: String, name: String, quote: Quote?, profile: MarketApi.Profile?, onDismiss: () -> Unit) {
    DetailBlatt(S.bkBewertung, name, onDismiss) {
        ReverseDcfKarte(state, symbol, quote, profile)
        if ((profile?.eps ?: 0.0) > 0) {
            SectionHeader(S.bkFairSelbst)
            FairValueTool(state, symbol, profile)
        } else ToolNote(S.bkBewertungVerlust)
    }
}

// Einzelheiten zur Bilanz: Piotroski-F-Score mit allen Pruefungen und Altman-Z-Score.
@Composable
internal fun BilanzBlatt(state: AppState, symbol: String, name: String, quote: Quote?, profile: MarketApi.Profile?, onDismiss: () -> Unit) {
    DetailBlatt(S.balance, name, onDismiss) { QualitaetKarte(state, symbol, quote, profile) }
}

// Weitere Kennzahlen einer Aktie fuer das Blatt „Alle Kennzahlen“, in Gruppen. Dividendenwachstum und Veraenderung der
// Aktienzahl kommen aus Dividenden und Jahresabschluessen, die erst im Blatt geladen werden.
@Composable
internal fun mehrKennzahlen(state: AppState, symbol: String, quote: Quote?, p: MarketApi.Profile, seite: SeitenWaehrung): List<Pair<String, List<Pair<String, String>>>> {
    var dividendenWachstum by remember(symbol) { mutableStateOf<Double?>(null) }
    var aktienJahr by remember(symbol) { mutableStateOf<Double?>(null) }
    var abschluss by remember(symbol) { mutableStateOf<List<Qualitaet.Jahr>>(emptyList()) }
    LaunchedEffect(symbol) {
        val ev = runCatching { state.dividendsFor(symbol) }.getOrDefault(emptyList())
        if (ev.isNotEmpty()) dividendenWachstum = Analysis.dividends(ev).growth
        val j = runCatching { state.jahresabschluesse(symbol) }.getOrDefault(emptyList())
        abschluss = j
        val a = j.lastOrNull()?.aktien
        val b = j.dropLast(1).lastOrNull()?.aktien
        aktienJahr = Bewertungsblick.veraenderung(a, b)
    }
    val fx by state.market.fxKurse.collectAsStateWithLifecycle()
    val m = p.mehr
    val fw = p.financialCurrency.ifBlank { quote?.nativeCurrency.orEmpty() }
    val hw = quote?.nativeCurrency.orEmpty()
    fun mal(v: Double) = "${Fmt.num(v, 1)}×"
    fun pct(v: Double) = Fmt.pct(v, 1)
    // Boersenwert in der Berichtswaehrung fuer die Free-Cashflow-Rendite
    val boersenwert = p.marketCap.takeIf { it > 0 }?.let { mc -> seite.nach(fw).umrechnen(mc, hw).takeIf { it.second == fw }?.first }
    val kap = remember(abschluss, p, quote?.marketCap, fx) { kapitalAus(abschluss, p, quote, fx) }
    val bewertung = listOfNotNull(
        p.priceToSales.takeIf { it > 0 }?.let { S.bkKuv to mal(it) },
        p.priceToBook.takeIf { it > 0 }?.let { S.priceToBook to mal(it) },
        p.evToEbitda.takeIf { it > 0 }?.let { S.bkEvEbitda to mal(it) },
        kap?.kursCashflow?.let { S.bkKursCashflow to mal(it) },
        m.peg.takeIf { it > 0 }?.let { S.bkPeg to Fmt.num(it, 2) },
        // Freier Cashflow der letzten zwoelf Monate aus den Abschluessen, sonst Yahoos Wert aus financialData
        (abschluss.lastOrNull()?.freierCashflowLaufend ?: p.freeCashflow.takeIf { it != 0.0 })?.let { fcf ->
            if (boersenwert != null && boersenwert > 0) S.bkFcfRendite to Fmt.signedPct(fcf / boersenwert * 100, 1) else null
        },
        kap?.ownerRendite?.let { S.bkOwnerEarnings to Fmt.signedPct(it, 1) },
    )
    val rentabel = listOfNotNull(
        p.returnOnEquity.takeIf { it != 0.0 }?.let { S.returnOnEquity to pct(it) },
        m.gesamtkapitalRendite.takeIf { it != 0.0 }?.let { S.bkGesamtkapitalRendite to pct(it) },
        kap?.roic?.let { S.bkRoic to pct(it) },
        p.grossMargin.takeIf { it != 0.0 }?.let { S.grossMargin to pct(it) },
        p.operatingMargin.takeIf { it != 0.0 }?.let { S.operatingMargin to pct(it) },
        m.ebitdaMarge.takeIf { it != 0.0 }?.let { S.bkEbitdaMarge to pct(it) },
        p.profitMargin.takeIf { it != 0.0 }?.let { S.profitMargin to pct(it) },
    )
    val kraft = listOfNotNull(
        p.debtToEquity.takeIf { it > 0 }?.let { S.bkVerschuldung to Fmt.num(it, 2) },
        kap?.zinsdeckung?.let { S.bkZinsdeckung to mal(it) },
        m.liquiditaet1.takeIf { it > 0 }?.let { S.bkLiquiditaet to Fmt.num(it, 2) },
        p.totalCash.takeIf { it > 0 }?.let { S.totalCash to seite.kompakt(it, fw) },
        p.totalDebt.takeIf { it > 0 }?.let { S.totalDebt to seite.kompakt(it, fw) },
        p.freeCashflow.takeIf { it != 0.0 }?.let { S.freeCashflow to seite.kompakt(it, fw) },
    )
    val dividende = listOfNotNull(
        p.dividendRate.takeIf { it > 0 }?.let { S.dividendPerShare to seite.preis(it, hw) },
        p.payoutRatio.takeIf { it > 0 }?.let { S.payoutRatio to pct(it) },
        m.dividendeFuenfJahre.takeIf { it > 0 }?.let { S.bkDividendeFuenf to Fmt.pct(it, 2) },
        dividendenWachstum?.let { S.bkDividendenWachstum to Fmt.signedPct(it, 1) },
    )
    val aktionaere = listOfNotNull(
        m.leerQuote.takeIf { it > 0 }?.let { S.bkLeerQuote to Fmt.pct(it, 2) },
        m.leerTage.takeIf { it > 0 }?.let { S.bkLeerTage to Fmt.num(it, 1) },
        m.insider.takeIf { it > 0 }?.let { S.bkInsider to Fmt.pct(it, 1) },
        m.institutionen.takeIf { it > 0 }?.let { S.bkInstitutionen to Fmt.pct(it, 1) },
        m.aktien.takeIf { it > 0 }?.let { S.bkAktien to Fmt.compact(it) },
        aktienJahr?.let { S.bkAktienVeraenderung to Fmt.signedPct(it, 1) },
    )
    val erwartung = listOfNotNull(
        if (p.targetLow > 0 && p.targetHigh > 0) S.bkZielSpanne to S.bkVonBis(seite.preis(p.targetLow, hw), seite.preis(p.targetHigh, hw)) else null,
        m.kurszielMedian.takeIf { it > 0 }?.let { S.bkZielMedian to seite.preis(it, hw) },
        m.gewinnErwartet.takeIf { it != 0.0 }?.let { S.bkGewinnErwartet to seite.preis(it, fw) },
        m.gewinnWachstumJahr.takeIf { it != 0.0 }?.let { S.bkGewinnWachstumJahr to Fmt.signedPct(it, 1) },
        m.gewinnWachstumFolgejahr.takeIf { it != 0.0 }?.let { S.bkGewinnWachstumFolge to Fmt.signedPct(it, 1) },
        m.umsatzWachstumFolgejahr.takeIf { it != 0.0 }?.let { S.bkUmsatzWachstumFolge to Fmt.signedPct(it, 1) },
        p.earningsDate.takeIf { it > System.currentTimeMillis() - 86_400_000L }?.let { S.bkNaechsteZahlen to Fmt.date(it) },
        m.jahresVeraenderung.takeIf { it != 0.0 }?.let { S.bkJahresVeraenderung to Fmt.signedPct(it, 1) },
    )
    return listOf("BEWERTUNG" to bewertung, "RENTABILITAET" to rentabel, "FINANZKRAFT" to kraft, "DIVIDENDE" to dividende,
        "AKTIONAERE" to aktionaere, "ERWARTUNG" to erwartung).filter { it.second.isNotEmpty() }
}
