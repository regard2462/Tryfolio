package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.engine.ComexLager
import de.troetenrepublik.app.engine.LmeLager
import de.troetenrepublik.app.engine.Tendenz
import de.troetenrepublik.app.engine.WeitereQuellen
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.LagerDatei
import de.troetenrepublik.app.plattform.rememberOeffnenRoh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.troetenrepublik.app.market.InsiderWelt
import kotlin.math.abs

// Weitere Quellen in der Oberflaeche (seit 5.92): Containerfracht, US-Bahnen
// und Halbleiterumsaetze bei „Fracht, Lieferketten, Halbleiter“, Bundesrichter, britisches Unterhaus und weitere Parlamente bei Politik,
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
            tendenz = Tendenz.containerfracht(x.woche),
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
            tendenz = Tendenz.ersatz("aar", WeitereQuellen.vorjahrAmpel(x.gesamt)),
        )
    }
    w.halbleiter?.let { x ->
        WeitereZeile(
            k = "wsts", ampel = WeitereQuellen.vorjahrAmpel(x.vorjahr), steigt = x.vorjahr > 0, wert = Fmt.signedPct(x.vorjahr, 1), auf = offen == "wsts", onClick = { tippen("wsts") },
            details = listOf(S.ersMrdDollar(Fmt.num(x.milliarden, 1)), S.ersStand(ersMonat(x.stand))),
            tendenz = Tendenz.ersatz("wsts", WeitereQuellen.vorjahrAmpel(x.vorjahr)),
        )
    }
}

@Composable
private fun WeitereZeile(k: String, ampel: Ampel, steigt: Boolean, wert: String, auf: Boolean, onClick: () -> Unit, details: List<String>, tendenz: Tendenz.Lage) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AmpelPunkt(fwAmpelZeichen(ampel), fwAmpelFarbe(ampel))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NameUndWert(S.wqName(k), wert)
                Text(S.wqSatz(k, ampel.ordinal, steigt), style = MaterialTheme.typography.bodySmall, color = grau)
                TendenzZeile(tendenz, Modifier.padding(top = 2.dp), ausblickStaerke(k))
            }
        }
        if (auf) {
            PunktText(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 28.dp, top = 6.dp), color = androidx.compose.ui.graphics.Color.Unspecified)
            StichpunktText(S.wqErklaerung(k), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(start = 28.dp, top = 4.dp))
        }
    }
}

// Frachtindizes, deren Daten sich nur im Browser ansehen lassen.
@Composable
internal fun FrachtBrowserLinks() {
    val uri = appLinks()
    QuellenZeile(S.wqFbxLink, onClick = { oeffne(uri, Anstossen.FBX) })
    QuellenZeile(S.wqBalticLink, onClick = { oeffne(uri, Anstossen.BALTIC) })
}

// Lager der Metallboersen: die LME mit Bestaenden je Metall aus den Monatsdateien (market/LmeLagerAbruf.kt) und die COMEX
// aus den Dateien je Metall (market/ComexLagerAbruf.kt). Beide Dateiarten laedt die Person selbst im Browser und oeffnet
// sie mit der App; eine Auswahl erkennt die Art am Inhalt.
@Composable
internal fun MetallLagerLinks(state: AppState) {
    val uri = appLinks()
    val scope = rememberCoroutineScope()
    var ergebnis by remember { mutableStateOf<de.troetenrepublik.app.market.LmeLagerAbruf.Ergebnis?>(null) }
    var comex by remember { mutableStateOf<ComexLager.Stand?>(null) }
    LaunchedEffect(Unit) {
        ergebnis = state.lmeLagerStand()
        comex = withContext(Dispatchers.Default) { state.comexLagerStand() }
    }
    val einlesen = rememberOeffnenRoh { _, daten ->
        scope.launch {
            val art = if (daten == null) LagerDatei.Art.KEINE else withContext(Dispatchers.Default) { LagerDatei.einlesen(daten, state.lmeLager, state.comexLager).art }
            when (art) {
                LagerDatei.Art.LME -> ergebnis = state.lmeLagerStand()
                LagerDatei.Art.COMEX -> comex = withContext(Dispatchers.Default) { state.comexLagerStand() }
                LagerDatei.Art.KEINE -> state.showToast(S.lmeUnbekannt)
            }
        }
    }
    val tage = ergebnis?.tage
    val stand = remember(tage) { tage?.let { LmeLager.auswerten(it) } }
    if (stand != null) LmeLagerKarte(stand)
    comex?.let { ComexLagerKarte(it) }
    QuellenZeile(S.lmeBrowser, onClick = { oeffne(uri, Anstossen.LME_BERICHT) })
    QuellenZeile(S.wqComexLink, onClick = { oeffne(uri, ComexLager.BERICHT) })
    QuellenZeile(S.lmeEinlesen, onClick = einlesen)
    val quellen = listOfNotNull(
        stand?.let { QuelleLink("London Metal Exchange", LmeLager.BERICHT) },
        comex?.let { QuelleLink("CME Group", ComexLager.BERICHT) },
    )
    if (quellen.isNotEmpty()) QuellenAnhang(quellen)
}

private fun lmeTonnen(v: Double) = S.lmeTonnen(Fmt.num(v, 0))

private fun comexMenge(v: Double, einheit: String) = S.comexMenge(Fmt.num(v, 0), einheit)

// Eine Zeile der Lagerkarte: Name, Veraenderung, Verlauf, Wert, dazu beim Antippen Einzelwerte und der Verlauf gross.
private class LagerZeile(
    val schluessel: String, val name: String, val aenderung: Double?, val verlauf: List<Pair<Long, Double>>, val wert: String,
    val format: (Double) -> String, val einzel: List<Pair<String, String>>, val verlaufText: String,
)

// Kernaussage zuerst (Kernmetall, Bestand, Veraenderung, Stand), die Liste aller Metalle eingeklappt.
@Composable
private fun LagerKarte(titel: String, kern: LagerZeile, stand: String, vergleich: String, zeilen: List<LagerZeile>) {
    var offen by rememberSaveable { mutableStateOf(false) }
    var gewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = !offen }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kern.name + " " + kern.wert, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                kern.aenderung?.let { a ->
                    Spacer(Modifier.width(10.dp))
                    ChangeValue(a, Fmt.pct(abs(a), 1), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stand, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.width(12.dp))
        AufklappPfeil(offen)
    }
    if (offen) {
        // Die Vergleichszeile nur, wenn es eine Veraenderung zu zeigen gibt
        if (zeilen.any { it.aenderung != null }) Text(vergleich, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 2.dp))
        for (z in zeilen) {
            LagerZeileAnsicht(z, gewaehlt == z.schluessel) { gewaehlt = if (gewaehlt == z.schluessel) null else z.schluessel }
        }
    }
}

// Eine Zeile je Metall: Name mit Veraenderung, kleine Verlaufslinie (ab zwei Werten), Bestand rechts. Ein Tipp zeigt
// darunter die Einzelwerte und, ab zwei Werten, den Verlauf gross.
@Composable
private fun LagerZeileAnsicht(z: LagerZeile, gross: Boolean, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(z.name, style = MaterialTheme.typography.bodyMedium)
                z.aenderung?.let { a -> ChangeValue(a, Fmt.pct(abs(a), 1), style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.width(8.dp))
            Verlaufslinie(z.verlauf.map { it.second }, grau, Modifier.width(56.dp).height(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(z.wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
        }
        if (gross) {
            for ((k, v) in z.einzel) KeyValueRow(k, v)
            if (z.verlauf.size >= 2) {
                val punkte = remember(z.verlauf) { z.verlauf.map { it.first * TAG_MS to it.second } }
                VerlaufDiagramm(punkte, grau, z.format, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
                Text(z.verlaufText, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun LmeLagerKarte(stand: LmeLager.Stand) {
    val zeilen = remember(stand) {
        stand.metalle.map { m ->
            val monate = m.verlauf.map { java.time.LocalDate.ofEpochDay(it.first).let { d -> d.year * 12 + d.monthValue } }.distinct().size
            LagerZeile(m.kuerzel, S.lmeName(m.kuerzel), m.aenderung, m.verlauf, lmeTonnen(m.tonnen), { lmeTonnen(it) }, emptyList(), S.lmeVerlauf(monate.coerceAtMost(LmeLager.MONATE)))
        }
    }
    LagerKarte(S.lmeTitel, zeilen.first(), S.lmeStand(Fmt.date(stand.tag * TAG_MS)), S.lmeVormonat, zeilen)
}

@Composable
private fun ComexLagerKarte(stand: ComexLager.Stand) {
    val zeilen = remember(stand) {
        stand.metalle.map { m ->
            val e = m.letzter
            LagerZeile(
                m.schluessel, S.comexName(m.schluessel), m.aenderung, m.verlauf, comexMenge(e.gesamt, e.einheit), { comexMenge(it, e.einheit) },
                listOfNotNull(
                    e.registered?.let { S.comexLieferbar to comexMenge(it, e.einheit) },
                    e.eligible?.let { S.comexEingelagert to comexMenge(it, e.einheit) },
                ),
                S.comexVerlauf(m.verlauf.size),
            )
        }
    }
    LagerKarte(S.comexTitel, zeilen.first(), S.lmeStand(Fmt.date(stand.tag * TAG_MS)), S.comexVorigerTag, zeilen)
}

// Duenne Linie ueber alle Werte, ohne Achsen; nur der Verlauf, ohne Bewertung.
@Composable
private fun Verlaufslinie(werte: List<Double>, farbe: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    if (werte.size < 2) { Spacer(modifier); return }
    val lo = werte.min()
    val hi = werte.max()
    Canvas(modifier) {
        val spanne = (hi - lo).takeIf { it > 0 } ?: 1.0
        val p = androidx.compose.ui.graphics.Path()
        werte.forEachIndexed { i, v ->
            val x = i.toFloat() / (werte.size - 1) * size.width
            val y = size.height * (1f - ((v - lo) / spanne).toFloat())
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, farbe, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

// Margin-Statistik der FINRA, im Blasen-Detektor bei den Margin-Krediten.
@Composable
internal fun FinraLink() {
    val uri = appLinks()
    QuellenZeile(S.wqFinraLink, Modifier.padding(start = 8.dp), onClick = { oeffne(uri, Anstossen.FINRA_MARGIN) })
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
            modifier = Modifier.textKlick(20.dp) { oeffne(uri, url) }.padding(horizontal = 20.dp, vertical = 8.dp),
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
    // Zehn Zeilen zuerst, ein Pfeil zeigt alle; die Register im Browser sind in einer Zeile gebuendelt
    var alleRichter by rememberSaveable { mutableStateOf(false) }
    var registerAuf by rememberSaveable { mutableStateOf(false) }
    // Gemeldete Beteiligungen britischer Abgeordneter (0.78, ui/UkRegisterUi.kt)
    val uk = rememberUkRegister(state)
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        ukRegisterAbschnitt(uk)
        // Die Beschreibung steht hinter dem Info-Symbol an der Ueberschrift
        item { MarktInfoKopf(S.wqRichterTitel, S.wqRichterIntro) }
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
            else -> itemsIndexed(if (alleRichter) geschaefte else geschaefte.take(10), key = { i, g -> "r-$i-${g.erklaerung}" }) { _, g ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NamenText(r.namen[g.person] ?: S.wqRichterName, style = MaterialTheme.typography.titleSmall)
                        Text(g.anlage, style = MaterialTheme.typography.bodySmall)
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
        if (geschaefte.size > 10) item(key = "richter-mehr") {
            ListenPfeil(alleRichter, label = if (alleRichter) null else S.bbAlle(geschaefte.size)) { alleRichter = !alleRichter }
        }
        item(key = "register-browser") {
            Row(
                Modifier.fillMaxWidth().clickable { registerAuf = !registerAuf }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.wqRegisterBrowser, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                AufklappPfeil(registerAuf)
            }
        }
        if (registerAuf) {
            item { ParlamentLink(S.wqJefsLink) { oeffne(uri, WeitereQuellen.JEFS_SEITE) } }
            item { ParlamentLink(S.wqEuLink) { oeffne(uri, "https://www.europarl.europa.eu/meps/en/full-list/all") } }
            item { ParlamentLink(S.wqAusHausLink) { oeffne(uri, "https://www.aph.gov.au/Senators_and_Members/Members/Register") } }
            item { ParlamentLink(S.wqAusSenatLink) { oeffne(uri, "https://www.aph.gov.au/Parliamentary_Business/Committees/Senate/Senators_Interests") } }
        }
        // Alle Quellen in einem Anhang am Seitenende; das UK-Register nach der Open Parliament Licence, sobald Daten zu sehen sind
        item(key = "quellen") {
            QuellenAnhang(
                listOfNotNull(
                    if (token) QuelleLink("CourtListener (Free Law Project)", WeitereQuellen.CL_SEITE) else null,
                    if (!uk.liste.value.isNullOrEmpty()) UK_REGISTER_QUELLE else null,
                ),
            )
        }
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
