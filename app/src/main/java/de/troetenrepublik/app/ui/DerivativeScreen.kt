package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.risikoWarnungen
import de.troetenrepublik.app.rueckkauf
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.isFactor
import de.troetenrepublik.app.data.isKnockout
import de.troetenrepublik.app.data.isOption
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Pricing
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.r2
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Detailseite eines Hebelprodukts. Ein ausgeknocktes Produkt zeigt das Ereignis oben als Karte.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DerivativeScreen(
    state: AppState,
    id: String,
    onBack: () -> Unit,
    onOpenUnderlying: () -> Unit = {},
    // Seit 0.77: ein anderes Bein derselben Strategie oeffnen.
    onOpenDerivative: (String) -> Unit = {},
) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val clipboard = LocalClipboardManager.current
    val locale = I18n.locale

    val d = remember(id, account.positions) { state.findDerivative(id) }
    if (d == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
            TopAppBar(title = { Text(S.derivative) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
            })
        }) {
            Box(Modifier.padding(it).fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text(S.productUnavailable, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val q = quotes[d.underlying]
    val spot = q?.price ?: 0.0
    val fx = q?.fx ?: 1.0
    val rate = state.riskFree
    val now = System.currentTimeMillis()
    val price = Pricing.price(d, spot, now, fx, rate)
    val lev = Pricing.leverage(d, spot, now, fx, rate)
    val greeks = Pricing.greeks(d, spot, now, fx, rate)
    val position = valuation.positions.firstOrNull { it.position.key == d.id }
    val title = Derivatives.label(d, locale)
    val underlyingChange = q?.changePct ?: 0.0
    val knockedOut = d.isKnockedOut
    val tradable = Derivatives.tradable(d, now) && !knockedOut

    // Ausgeknockt: zuerst der Tag des Knock-outs (die Woche, wenn er nicht heute war), sonst der Monat
    val koHeute = knockedOut && MarketHours.dayStart(d.knockedOutAt) == MarketHours.dayStart(now)
    var range by rememberSaveable { mutableStateOf(if (!knockedOut) Range.M1 else if (koHeute) Range.D1 else Range.W1) }
    var series by remember(d.id) { mutableStateOf(Series(emptyList())) }
    var scrub by remember { mutableStateOf<Candle?>(null) }
    // Aus dem Optionsrechner geoeffnet: Kaufblatt mit vorbelegter Stueckzahl
    val startMenge = remember(d.id) { state.kaufMengeNehmen(d.id) }
    var sheet by remember { mutableStateOf<Side?>(if (startMenge > 0) Side.BUY else null) }
    var absoluteChange by rememberSaveable { mutableStateOf(false) }

    var serieLaedt by remember(d.id) { mutableStateOf(true) }
    LaunchedEffect(d.id, range) {
        series = Series(emptyList()); scrub = null; serieLaedt = true
        series = state.derivativeSeries(d, range)
        serieLaedt = false
    }
    LaunchedEffect(d.underlying) { state.market.track(listOf(d.underlying)) }

    val candles = series.candles
    val domain = dayAxis(range, series.dayStart, series.dayEnd, candles)
    val reference = candles.firstOrNull()?.close ?: price
    val isin = Derivatives.isinFor(d)

    // Nach unten wischen schliesst die Seite, sobald der Inhalt oben steht (auch an der Kopfzeile)
    val rollen = rememberScrollState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    Scaffold(
        modifier = Modifier.sheetDismiss(dismiss) { rollen.value == 0 },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Eigene Kopfzeile statt TopAppBar: der Produktname bricht um und wird nie abgeschnitten
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(d.underlyingName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) { padding ->
      Box(Modifier.padding(padding).fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rollen)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(S.productKind(kindKey(d)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FollowsScrub {
                    val selected = scrub
                    val shown = selected?.close ?: price
                    val diff = shown - reference
                    val diffPct = if (reference > 0) diff / reference * 100.0 else 0.0
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.price(shown), style = MaterialTheme.typography.displayMedium, modifier = Modifier.waehrungWechsel(state))
                        Spacer(Modifier.width(12.dp))
                        if (!knockedOut) ChangeValue(
                            change = diff,
                            text = if (absoluteChange) Fmt.money(kotlin.math.abs(diff)) else Fmt.pct(kotlin.math.abs(diffPct)),
                            colored = false,
                            style = MaterialTheme.typography.titleSmall,
                            triangleWidth = 10.dp,
                            modifier = Modifier.textKlick(8.dp) { absoluteChange = !absoluteChange }.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    // Unter dem Kurs: beim Ziehen durch den Chart Datum und Uhrzeit des Punkts,
                    // sonst Hebel bzw. Delta oder der Knock-out-Hinweis
                    Text(
                        selected?.let { Fmt.dateTime(it.t) }
                            ?: if (knockedOut) S.knockedOut else S.leverageValue(Fmt.num(lev, 2) + "×"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (knockedOut && selected == null) signal.down else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Nach einem Knock-out zeigt der Chart den Tag bis zum Knock-out (siehe DerivatVerlauf);
            // Faktor-Zertifikate haben einen modellierten Verlauf
            RangeSelector(
                ranges = Range.entries,
                selected = range,
                onSelect = { range = it; scrub = null },
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp, bottom = 4.dp),
            )
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                PriceChart(candles = candles, modifier = Modifier.fillMaxWidth().height(200.dp), laedt = serieLaedt, onScrub = { scrub = it }, domain = domain)
                FollowsScrub {
                    ChartCrosshair(candles = candles, selected = scrub, color = signal.line, modifier = Modifier.fillMaxWidth().height(200.dp), domain = domain)
                }
            }
            if (knockedOut) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                        .background(signal.down.copy(alpha = 0.14f)).padding(18.dp),
                ) {
                    Text(S.knockedOutBanner, style = MaterialTheme.typography.titleMedium, color = signal.down)
                    Spacer(Modifier.height(6.dp))
                    Text(S.knockedOutSince(Fmt.dateTime(d.knockedOutAt), Fmt.price(maxOf(Pricing.KO_RESIDUAL, d.restwert))), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(S.koAutoSettle(Fmt.date(d.knockedOutAt + Trading.KO_SETTLE_MS)), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Verkaufte Option: Praemie, Rueckkaufpreis und Bindung; Beine einer Strategie darunter (0.77)
            if (position != null && position.position.verkauft) StillhalterAbschnitt(account, position, state.bidAsk(d.underlying, d).second)
            else if (position != null) {
                SectionHeader(S.yourPosition)
                KeyValueRow(S.quantity, "${Fmt.qty(position.qty)} ${S.pieces}")
                KeyValueRow(S.avgPrice, Fmt.price(position.position.avgPrice))
                KeyValueRow(S.value, Fmt.money(position.value))
                KeyValueRow(
                    S.result,
                    "${Fmt.signedMoney(position.profit)}  ${Fmt.signedPct(position.profitPct)}",
                    valueColor = signal.forChange(position.profit),
                )
                if (knockedOut) KeyValueRow(S.docRefundPerPiece, Fmt.price(Pricing.KO_RESIDUAL))
            }
            if (position != null && position.position.verkauft) RollHelferAbschnitt(state, account, position, onGerollt = onBack)
            if (position != null) StillhalterGruppe(state, account, valuation, d.id, onOpen = onOpenDerivative, onGeschlossen = onBack)

            SectionHeader(S.underlying)
            AssetRow(
                symbol = d.underlying,
                name = d.underlyingName,
                subtitle = Fmt.price(spot),
                change = underlyingChange,
                changeText = Fmt.pct(kotlin.math.abs(underlyingChange)),
                onClick = onOpenUnderlying,
            )

            val (geld, brief) = state.bidAsk(d.underlying, d)
            SectionHeader(S.keyStats)
            StatPages(
                statPages(
                    listOf(
                        S.bid to Fmt.price(geld),
                        S.ask to Fmt.price(brief),
                        S.strategy to Derivatives.strategy(d),
                    )
                )
            )

            SectionHeader(S.productInfo)
            KeyValueRow(S.isin, isin, onClick = { clipboard.setText(AnnotatedString(isin)); state.showToast(S.isinCopied) })
            // Der Hebel steht unter dem Kurs; hier nur, wenn dort der Knock-out-Hinweis steht
            if (!d.kind.isFactor && knockedOut) KeyValueRow(S.leverage, "${Fmt.num(lev, 2)}×")

            if (d.kind.isKnockout) {
                KeyValueRow(S.strikePrice, Derivatives.money(d.strike, d.ccy, locale))
                val dist = Pricing.distanceToBarrier(d, spot, fx)
                KeyValueRow(S.knockOut, Derivatives.money(d.barrier, d.ccy, locale))
                KeyValueRow(S.distanceToBarrier, Fmt.pct(dist, 2), valueColor = if (dist < 5) signal.warn else null)
                KeyValueRow(S.underlyingPrice, if (q != null) Derivatives.money(q.nativePrice, q.nativeCurrency, locale) else Fmt.price(spot))
                KeyValueRow(S.maturity, S.openEnd)
            }
            if (d.kind.isFactor) {
                KeyValueRow(S.factor, "${Fmt.num(d.factor, 0)}×")
                KeyValueRow(S.referencePrice, Fmt.price(d.factorRef))
                KeyValueRow(S.underlyingPrice, Fmt.price(spot))
                KeyValueRow(S.maturity, S.openEnd)
            }
            if (d.kind.isOption) {
                KeyValueRow(S.strikePrice, Derivatives.money(d.strike, d.ccy, locale))
                KeyValueRow(S.underlyingPrice, if (q != null) Derivatives.money(q.nativePrice, q.nativeCurrency, locale) else Fmt.price(spot))
                KeyValueRow(S.maturity, Fmt.date(d.expiry))
                val restDays = ((d.expiry - now) / 86_400_000L).coerceAtLeast(0)
                KeyValueRow(S.remainingDays, S.days(restDays))
                KeyValueRow(if (Derivatives.modellVol(d)) S.modellVol else S.impliedVol, Fmt.pct(d.vol * 100))
                val inner = Pricing.intrinsic(d, spot, fx)
                InfoWertZeile(S.intrinsicValue, Fmt.price(inner), S.intrinsicValue, r2("opt.innerer"))
                InfoWertZeile(S.timeValue, Fmt.price((price - inner).coerceAtLeast(0.0)), S.timeValue, r2("opt.zeitwert"))
                InfoWertZeile(S.premium, Fmt.pct(Pricing.premiumPct(d, spot, now, fx, rate), 2), S.premium, r2("opt.aufgeld"))
                // Mit Position zum Einstand, wie der Optionsrechner; sonst zum Preis jetzt
                val be = Derivatives.gewinnschwelle(d, position?.position?.avgPrice?.takeIf { it > 0 } ?: price, fx)
                InfoWertZeile(S.breakEven, Derivatives.money(be, d.ccy, locale), S.breakEven, r2("opt.schwelle"))
                greeks?.let {
                    SectionHeader(S.sensitivities)
                    InfoWertZeile(S.delta, String.format(locale, "%.3f", it.delta), S.delta, r2("opt.delta"))
                    InfoWertZeile(S.gamma, String.format(locale, "%.4f", it.gamma), S.gamma, r2("opt.gamma"))
                    InfoWertZeile(S.vega, Fmt.price(it.vega), S.vega, r2("opt.vega"))
                    InfoWertZeile(S.theta, Fmt.price(it.theta), S.theta, r2("opt.theta"))
                }
            }

            KeyValueRow(S.currency, "EUR")
            KeyValueRow(S.ratio, ratioText(d.ratio))

            // Risiko und Modellverlauf: eine Kernzeile, der Rest hinter dem Info-Symbol
            HinweisInfo(riskText(d) + " " + if (d.kind.isFactor) S.noFactorHistory else S.modelledHistory, modifier = Modifier.padding(vertical = 16.dp))
            Spacer(Modifier.height(110.dp))
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (position != null && knockedOut) {
                    // Ausgeknockt: statt Kaufen und Verkaufen die sofortige Ausbuchung (sonst automatisch nach zwei Tagen)
                    FloatingPill(label = S.settleNow, filled = true, modifier = Modifier.weight(1f)) { if (state.settleNow(d.id)) onBack() }
                } else if (position != null && position.position.verkauft) {
                    // Verkaufte Option: nur der Rueckkauf zum Briefkurs
                    var fragen by remember { mutableStateOf(false) }
                    FloatingPill(label = S.shZurueckkaufen, filled = true, enabled = price > 0 && spot > 0 && tradable, modifier = Modifier.weight(1f)) { fragen = true }
                    if (fragen) StillhalterBestaetigen(S.shZurueckkaufen, { fragen = false }) { fragen = false; if (state.rueckkauf(d.id)) onBack() }
                } else if (!d.verkauft) {
                    if (position != null) {
                        FloatingPill(label = S.sell, filled = false, modifier = Modifier.weight(1f)) { sheet = Side.SELL }
                    }
                    FloatingPill(label = S.buy, plus = true, filled = true, enabled = price > 0 && spot > 0 && tradable, modifier = Modifier.weight(1f)) { sheet = Side.BUY }
                }
            }
        }
      }
    }

    sheet?.let { side ->
        TradeSheet(
            title = title,
            subtitle = d.underlyingName,
            price = state.bidAsk(d.underlying, d).let { if (side == Side.BUY) it.second else it.first },
            side = side,
            cash = account.frei.coerceAtLeast(0.0),
            ownedQty = position?.qty ?: 0.0,
            allowFractional = false,
            derivative = d,
            startMenge = if (side == Side.BUY) startMenge else 0,
            marketOpen = state.marketOpen(d.underlying, d),
            hoursHint = hoursLabel(MarketHours.status(null, true)),
            onDismiss = { sheet = null },
            onMarket = { qty, note ->
                if (side == Side.BUY) state.buy(d.id, title, d.underlying, qty, d, note) else state.sell(d.id, qty, note)
            },
            onLimit = { qty, limit, type, validity, note, trail ->
                state.placeOrder(d.id, title, d.underlying, side, type, qty, limit, d, validity, note, trail)
            },
            // Eigene Depotregeln (seit 5.91): Hebelprodukt, Positionsgroesse, Guthaben
            regelWarnungen = { betrag, _ -> state.kaufWarnungen(d.id, betrag, 0.0, hebel = true).map { theseVerstossText(it) } },
            // Risikoregeln (0.78): ohne Stopp gelten hier die Tagesregeln
            risikoWarnungen = { kurs, stueck, stopp, ziel -> state.risikoWarnungen(kurs, stueck, stopp, ziel).map { risikoVerstossText(it) } },
            onGekauft = { _, bruch -> state.theseNachKauf(d.underlying, null, bruch) },
        )
    }
}

fun kindKey(d: de.troetenrepublik.app.data.Derivative): String = when {
    d.kind.isKnockout -> "TURBO"
    d.kind.isFactor -> "FACTOR"
    else -> "WARRANT"
}

private fun riskText(d: de.troetenrepublik.app.data.Derivative): String = when {
    d.kind.isKnockout -> S.riskKnockout
    d.kind.isFactor -> S.riskFactor
    else -> S.riskWarrant
}
