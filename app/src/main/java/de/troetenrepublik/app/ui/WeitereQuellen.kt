package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.engine.Tendenz
import de.troetenrepublik.app.engine.WeitereQuellen
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.InsiderWelt
import kotlin.math.abs

// Weitere Quellen in der Oberflaeche (seit 5.92): Containerfracht, US-Bahnen
// und Halbleiterumsaetze bei „Fracht, Lieferketten, Halbleiter“, Bundesrichter und weitere Parlamente bei Politik,
// Bluesky bei den Trends eines Werts und Links fuer Quellen, die sich nur im Browser ansehen lassen.

private const val TAG_MS = 86_400_000L

private fun oeffne(uri: androidx.compose.ui.platform.UriHandler, url: String) { runCatching { uri.openUri(url) } }

// ------------------------------------------------------------ Fracht, Bahn, Halbleiter

// Zeilen unter den Monatsreihen von FRED: Containerfracht (Woche), US-Bahnen (Woche), Halbleiterumsatz (Monat).
@Composable
internal fun WeitereSignalZeilen(w: AppState.WeitereSignale) {
    var offen by remember { mutableStateOf<String?>(null) }
    fun tippen(k: String) { offen = if (offen == k) null else k }
    w.wci?.let { x ->
        val ampel = WeitereQuellen.wciAmpel(x.woche)
        WeitereZeile(
            k = "wci", ampel = ampel, steigt = x.woche > 0, wert = "$" + Fmt.num(x.dollar, 0), auf = offen == "wci", onClick = { tippen("wci") },
            details = listOf(S.wqWoche(Fmt.signedPct(x.woche, 0)), S.wqStandTag(Fmt.date(x.tag * TAG_MS))),
            seite = WeitereQuellen.DREWRY_SEITE, tendenz = Tendenz.containerfracht(x.woche),
        )
    }
    w.bahn?.let { x ->
        WeitereZeile(
            k = "aar", ampel = WeitereQuellen.vorjahrAmpel(x.gesamt), steigt = x.gesamt > 0, wert = Fmt.signedPct(x.gesamt, 1), auf = offen == "aar", onClick = { tippen("aar") },
            details = listOfNotNull(
                S.wqEinheiten(Fmt.num(x.einheiten.toDouble(), 0)),
                S.wqBahnTeile(x.wagen?.let { Fmt.signedPct(it, 1) }, x.container?.let { Fmt.signedPct(it, 1) }).ifBlank { null },
                S.wqWocheBis(Fmt.date(x.ende * TAG_MS)),
            ),
            seite = WeitereQuellen.AAR_SEITE, tendenz = Tendenz.ersatz("aar", WeitereQuellen.vorjahrAmpel(x.gesamt)),
        )
    }
    w.halbleiter?.let { x ->
        WeitereZeile(
            k = "wsts", ampel = WeitereQuellen.vorjahrAmpel(x.vorjahr), steigt = x.vorjahr > 0, wert = Fmt.signedPct(x.vorjahr, 1), auf = offen == "wsts", onClick = { tippen("wsts") },
            details = listOf(S.ersMrdDollar(Fmt.num(x.milliarden, 1)), S.ersStand(ersMonat(x.stand))),
            seite = WeitereQuellen.WSTS_SEITE, tendenz = Tendenz.ersatz("wsts", WeitereQuellen.vorjahrAmpel(x.vorjahr)),
        )
    }
}

@Composable
private fun WeitereZeile(k: String, ampel: Ampel, steigt: Boolean, wert: String, auf: Boolean, onClick: () -> Unit, details: List<String>, seite: String, tendenz: Tendenz.Lage) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val uri = appLinks()
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AmpelPunkt(fwAmpelZeichen(ampel), fwAmpelFarbe(ampel))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NameUndWert(S.wqName(k), wert)
                Text(S.wqSatz(k, ampel.ordinal, steigt), style = MaterialTheme.typography.bodySmall, color = grau)
                TendenzZeile(tendenz, Modifier.padding(top = 2.dp))
            }
        }
        if (auf) {
            PunktText(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 28.dp, top = 6.dp), color = androidx.compose.ui.graphics.Color.Unspecified)
            Text(S.wqErklaerung(k), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(start = 28.dp, top = 4.dp))
            QuellenZeile(S.wqQuelle(k), Modifier.padding(start = 8.dp), onClick = { oeffne(uri, seite) })
        }
    }
}

// Frachtindizes, deren Daten sich nur im Browser ansehen lassen.
@Composable
internal fun FrachtBrowserLinks() {
    val uri = appLinks()
    QuellenZeile(S.wqFbxLink, onClick = { oeffne(uri, "https://www.freightos.com/enterprise/terminal/freightos-baltic-index-global-container-pricing-index/") })
    QuellenZeile(S.wqBalticLink, onClick = { oeffne(uri, "https://www.balticexchange.com/en/index.html") })
}

// Lager der Metallboersen LME und CME: Berichte im Browser.
@Composable
internal fun MetallLagerLinks() {
    val uri = appLinks()
    QuellenZeile(S.wqLmeLink, onClick = { oeffne(uri, "https://www.lme.com/en/market-data/reports-and-data/warehouse-and-stocks-reports") })
    QuellenZeile(S.wqComexLink, onClick = { oeffne(uri, "https://www.cmegroup.com/clearing/operations-and-deliveries/registrar-reports.html") })
}

// Margin-Statistik der FINRA, im Blasen-Detektor bei den Margin-Krediten.
@Composable
internal fun FinraLink() {
    val uri = appLinks()
    QuellenZeile(S.wqFinraLink, Modifier.padding(start = 8.dp), onClick = { oeffne(uri, "https://www.finra.org/rules-guidance/key-topics/margin-accounts/margin-statistics") })
}

// ------------------------------------------------------------ Insider: nur im Browser

@Composable
internal fun InsiderBrowserHinweis(land: String) {
    val url = InsiderWelt.NUR_BROWSER[land] ?: return
    val uri = appLinks()
    Column {
        SectionHeader(S.vdKarteTitel)
        Text(S.wqBrowserText(land), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Text(
            S.wqBrowserLink(land), style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link,
            modifier = Modifier.clickable { oeffne(uri, url) }.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

// ------------------------------------------------------------ Politik: Bundesrichter und weitere Parlamente

// Betrag einer Wertspanne in Dollar, kurz.
private fun dollar(v: Long) = Fmt.mitSymbol(Fmt.compact(v.toDouble()), "$")

@Composable
internal fun WeiterePolitikListe(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val token = settings.keyCourtListener.isNotBlank()
    var richter by remember { mutableStateOf<de.troetenrepublik.app.market.WeitereAbrufe.Richter?>(null) }
    var geladen by remember { mutableStateOf(false) }
    LaunchedEffect(settings.keyCourtListener) {
        geladen = false
        richter = if (token) state.richter() else null
        geladen = true
    }
    val uri = appLinks()
    val signal = LocalSignal.current
    val r = richter
    val geschaefte = remember(r) { r?.geschaefte.orEmpty().sortedByDescending { it.tag }.take(40) }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { SectionHeader(S.wqRichterTitel) }
        item { Text(S.wqRichterIntro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        when {
            !token -> item {
                Text(
                    S.wqRichterOhneZugang, style = MaterialTheme.typography.bodyMedium, color = signal.link,
                    modifier = Modifier.fillMaxWidth().clickable { oeffnen("zugaenge") }.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            !geladen -> item { EmptyHint(S.wqRichterLaedt) }
            r == null -> item { EmptyHint(state.weitere.clMeldung?.let { S.wqMeldung(it) } ?: S.copyUnavailable) }
            geschaefte.isEmpty() -> item { EmptyHint(S.wqRichterLeer) }
            else -> itemsIndexed(geschaefte, key = { i, g -> "r-$i-${g.erklaerung}" }) { _, g ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NamenText(r.namen[g.person] ?: S.wqRichterName, style = MaterialTheme.typography.titleSmall)
                        AuslaufText(g.anlage, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        Text(
                            S.wqRichterZeile(g.jahr, g.tag.takeIf { it.isNotBlank() }?.let { de.troetenrepublik.app.market.Politik.tag(it) }?.takeIf { it > 0 }?.let { Fmt.date(it) } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            S.euArt(g.art), style = MaterialTheme.typography.titleSmall,
                            color = when (g.art) { "K" -> signal.up; "V" -> signal.down; else -> MaterialTheme.colorScheme.onSurface },
                        )
                        WeitereQuellen.clSpanne(g.code)?.let { (von, bis) ->
                            Text(S.wqSpanne(dollar(von), bis?.let { dollar(it) }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (token) item { QuellenZeile(S.wqRichterQuelle, onClick = { oeffne(uri, WeitereQuellen.CL_SEITE) }) }
        item { QuellenZeile(S.wqJefsLink, onClick = { oeffne(uri, WeitereQuellen.JEFS_SEITE) }) }
        item { SectionHeader(S.wqParlamenteTitel) }
        item { Text(S.wqParlamenteText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        item { ParlamentLink(S.wqEuLink) { oeffne(uri, "https://www.europarl.europa.eu/meps/en/full-list/all") } }
        item { ParlamentLink(S.wqAusHausLink) { oeffne(uri, "https://www.aph.gov.au/Senators_and_Members/Members/Register") } }
        item { ParlamentLink(S.wqAusSenatLink) { oeffne(uri, "https://www.aph.gov.au/Parliamentary_Business/Committees/Senate/Senators_Interests") } }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ParlamentLink(text: String, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

// ------------------------------------------------------------ Trends: Bluesky und Links zu X und Stocktwits

// Kuerzel, unter dem X und Stocktwits einen Wert fuehren: nur einfache US-Kuerzel ohne Boersenendung.
internal fun cashtag(symbol: String): String? = symbol.uppercase().takeIf { Regex("^[A-Z]{1,5}$").matches(it) }

@Composable
internal fun SozialeLinks(symbol: String) {
    val tag = cashtag(symbol) ?: return
    val uri = appLinks()
    QuellenZeile(S.wqXLink, onClick = { oeffne(uri, "https://x.com/search?q=%24$tag&f=live") })
    QuellenZeile(S.wqStocktwitsLink, onClick = { oeffne(uri, "https://stocktwits.com/symbol/$tag") })
}

// Veraenderung zur Vorwoche in % fuer die Anzeige rechts, null wenn sie nichts aussagt.
internal fun bskyVeraenderung(b: WeitereQuellen.BlueskyWoche): Double? = b.veraenderung?.takeIf { abs(it) < 10_000 }
