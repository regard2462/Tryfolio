package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Aktienkennzahlen
import de.troetenrepublik.app.engine.Eingepreist
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.Fruehindikatoren.ImPreis
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.SeitMeldung
import de.troetenrepublik.app.engine.Tendenz
import de.troetenrepublik.app.engine.TendenzRueckblick
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// Baustein „Im Preis?“ (5.92): eine Zeile mit Ampel und Satz, ob ein Signal schon im Preis steckt; ein Tipp zeigt
// heute gegen ueblich, die Terminkurve, die Staerke des Zusammenhangs und den Zeitraum, das Info-Symbol die Grenzen.
// Fuer fruehe Signale (Schaetzung aus der Geschichte, engine/Eingepreist.kt) und fuer datierte Signale wie
// Insiderkaeufe, Hoch- und Abstufungen oder Kaeufe von Abgeordneten (Lauf seit dem Signal gegen den Heimatindex).
// Ohne Schaetzung zeigt SeitMeldungZeile die Bewegung des Markts seit der Meldung.

// Lage des Signals fuer den Satz: -1 knapp, 0 ueblich, 1 reichlich (bei der Duerre: viel Duerre).
private fun lageZahl(l: Fruehindikatoren.Lage?): Int = when (l) {
    Fruehindikatoren.Lage.DARUNTER, Fruehindikatoren.Lage.KNAPP -> -1
    Fruehindikatoren.Lage.REICHLICH, Fruehindikatoren.Lage.DARUEBER -> 1
    else -> 0
}

private fun urteilZahl(u: Eingepreist.Urteil): Int = when (u) {
    Eingepreist.Urteil.PASST -> 0
    Eingepreist.Urteil.HOEHER -> 1
    else -> 2
}

// Tendenz (Rechnung in engine/Eingepreist.kt, dort auch fuer die Rueckpruefung): +1 eher long, -1 eher short, 0 keine
// klare Richtung; bei den Zinsen bezogen auf Anleihen.
internal fun tendenz(e: Eingepreist.Einordnung, mass: Fruehindikatoren.Mass? = null): Int =
    Eingepreist.tendenz(e, umgekehrt = mass == Fruehindikatoren.Mass.ZINS)

// Bei datierten Signalen: Richtung des Signals, solange erst ein Teil gelaufen ist.
internal fun tendenz(g: Eingepreist.Gelaufen): Int = Eingepreist.tendenz(g)

// Worauf sich die Tendenz bezieht, wenn es nicht aus der Seite hervorgeht.
private fun tendenzWas(ip: ImPreis): String? = when (ip.mass) {
    Fruehindikatoren.Mass.ZINS -> S.epWasAnleihen
    Fruehindikatoren.Mass.AIRLINES -> S.epWasAirlines
    Fruehindikatoren.Mass.PREIS -> S.epWasKorn(ip.markt)
    Fruehindikatoren.Mass.KURVE -> null
    Fruehindikatoren.Mass.BRANCHE -> S.epWasBranche(ip.markt)
}

// blass: die Tendenz brachte frueher keinen belegten Vorteil, die Richtung steht nur grau da (5.92 Rueckblick).
@Composable
internal fun TendenzText(richtung: Int, was: String?, modifier: Modifier = Modifier, blass: Boolean = false) {
    val sig = LocalSignal.current
    Text(
        S.epTendenz(richtung, was), style = MaterialTheme.typography.labelMedium,
        color = when { blass -> MaterialTheme.colorScheme.onSurfaceVariant; richtung > 0 -> sig.up; richtung < 0 -> sig.down; else -> MaterialTheme.colorScheme.onSurfaceVariant },
        modifier = modifier.padding(top = 2.dp),
    )
}

// ------------------------------------------------------------ Rueckblick (5.92)

// Mitgelieferte Bilanz der Tendenz (engine/TendenzRueckblick.kt, gebaut mit :desktop:tendenzRueckblick), einmal gelesen.
internal val RUECKBLICK: TendenzRueckblick.Datei? by lazy {
    runCatching { TendenzRueckblick.lesen(de.troetenrepublik.app.plattform.Plattform.asset("indikatoren/tendenz_rueckblick.json").decodeToString()) }.getOrNull()
}

internal fun rueckblick(schluessel: String, ampel: Fruehwarnung.Ampel? = null): TendenzRueckblick.Bilanz? =
    TendenzRueckblick.bilanz(RUECKBLICK?.signale?.get(schluessel), ampel)

private fun mittelText(v: Double, einheit: String): String =
    if (einheit == TendenzRueckblick.PUNKTE) S.trPunkte((if (v > 0) "+" else "") + Fmt.num(v, 2).replace('-', '−')) else Fmt.signedPct(v * 100, 1)

// Kurze Bilanz unter der Tendenz: belegt mit Trefferquote, Zeitraum, Rendite und Faellen, sonst „ohne Vorteil“.
@Composable
internal fun RueckblickZeile(b: TendenzRueckblick.Bilanz?) {
    b ?: return
    val z = b.zeitraum
    Text(
        when {
            !b.geprueft -> S.trNichtGeprueft(b.signal.status == TendenzRueckblick.ZU_KURZ)
            z != null -> S.trFrueher(b.gruppe == "rot", Fmt.pct(z.treffer * 100, 0), Fmt.pct(z.grund * 100, 0), z.wochen, mittelText(z.mittel, b.signal.einheit), z.unabhaengig)
            else -> S.trOhneVorteil
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (z != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Aufgeklappt: je Zeitraum Trefferquote, Grundquote, Rendite in Tendenzrichtung, t-Wert und unabhaengige Faelle.
@Composable
private fun RueckblickEinzelheiten(b: TendenzRueckblick.Bilanz?) {
    if (b == null || !b.geprueft || b.liste.isEmpty()) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Text(S.trDetailTitel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    b.liste.forEach { z ->
        Text(
            S.trDetailZeile(z.wochen, Fmt.pct(z.treffer * 100, 0), Fmt.pct(z.grund * 100, 0), mittelText(z.mittel, b.signal.einheit), Fmt.num(z.t, 1).replace('-', '−'), z.unabhaengig, z.belegt),
            style = MaterialTheme.typography.labelSmall, color = if (z.belegt) MaterialTheme.colorScheme.onSurface else grau,
        )
    }
    if (b.signal.von.isNotBlank()) Text(S.trGeprueft(b.signal.von.take(4), b.signal.bis.take(4)), style = MaterialTheme.typography.labelSmall, color = grau)
}

// Uebungsvorschlag bei belegter Tendenz: Optionsschein der App (Call bei long, Put bei short), Basispreis am Geld,
// Laufzeit etwa das 1,5-Fache des belegten Zeitraums. Ein Tipp gibt den Schein aus und oeffnet seine Seite.
@Composable
internal fun WettVorschlag(state: AppState, symbol: String, richtung: Int, wochen: Int, oeffnen: (String) -> Unit) {
    var w by remember(symbol, richtung, wochen) { mutableStateOf<TendenzRueckblick.Wette?>(null) }
    LaunchedEffect(symbol, richtung, wochen) {
        w = runCatching {
            val spot = state.dailyCloses(symbol).lastOrNull()?.close
            if (spot == null) null else TendenzRueckblick.wette(symbol, richtung, wochen, spot, state.volatilityFor(symbol))
        }.getOrNull()
    }
    val x = w ?: return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { scope.launch { state.derivatFuerOption(x.symbol, x.call, x.strike, x.verfallTag)?.let { oeffnen("derivative/$it") } } }
            .padding(10.dp),
    ) {
        Text(S.trIdeeTitel(x.call, S.trBasisName(symbol)), style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link)
        Text(
            S.trIdeeDaten(Fmt.num(x.strike, if (x.strike % 1.0 == 0.0) 0 else 2), Fmt.date(x.verfall), Fmt.num(x.hebel, 1)),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(S.trIdeeRisiko(Fmt.pct(x.zeitwertWoche * 100, 1)), style = MaterialTheme.typography.bodySmall, color = grau)
        Text(S.trIdeeHinweis, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 2.dp))
    }
}

// Vorschlaege zu einem fruehen Signal, nur bei belegter Tendenz und mit Richtung.
@Composable
private fun Vorschlaege(state: AppState?, oeffnen: ((String) -> Unit)?, schluessel: String, b: TendenzRueckblick.Bilanz?, richtung: Int) {
    val z = b?.zeitraum ?: return
    if (state == null || oeffnen == null || richtung == 0) return
    TendenzRueckblick.basiswerte(schluessel).forEach { (symbol, faktor) -> WettVorschlag(state, symbol, richtung * faktor, z.wochen, oeffnen) }
}

// Satz zur Einordnung in der Sprache der Oberflaeche.
internal fun imPreisSatz(ip: ImPreis, lage: Fruehindikatoren.Lage?): String {
    val e = ip.einordnung
    if (e.urteil == Eingepreist.Urteil.UNKLAR) return S.epKeine
    val u = urteilZahl(e.urteil)
    return when (ip.schluessel) {
        Fruehindikatoren.DUERRE -> S.epDuerreSatz(u, lageZahl(lage) == 1)
        Fruehindikatoren.FLUGGAESTE -> S.epFlugSatz(u)
        Fruehindikatoren.ERSTANTRAEGE -> S.epAntraegeSatz(u)
        in Fruehindikatoren.ERSATZ_MARKT -> S.epErsatzSatz(ip.schluessel, ip.markt, u)
        else -> S.epLagerSatz(u, lageZahl(lage), gas = ip.schluessel == Fruehindikatoren.GAS_US || ip.schluessel == Fruehindikatoren.GAS_EU, kurve = ip.mass == Fruehindikatoren.Mass.KURVE)
    }
}

// Stand des Markts in Worten (heute oder ueblich).
private fun massText(ip: ImPreis, y: Double): String = when (ip.mass) {
    Fruehindikatoren.Mass.KURVE -> S.epKurve(Fmt.num(abs(y), 1) + " %", y >= 0)
    Fruehindikatoren.Mass.PREIS -> S.epPreis(Fmt.pct(abs(y), 1), y >= 0, S.epKorn(ip.markt))
    Fruehindikatoren.Mass.AIRLINES -> S.epAirlines(Fmt.pct(abs(y), 1), y >= 0)
    Fruehindikatoren.Mass.ZINS -> S.epZins(Fmt.num(abs(y), 2))
    Fruehindikatoren.Mass.BRANCHE -> S.epBranche(ip.markt, Fmt.pct(abs(y), 1), y >= 0)
}

@Composable
private fun ImPreisAmpel(a: Fruehwarnung.Ampel?, groesse: Dp = 16.dp) {
    if (a != null) AmpelPunkt(fwAmpelZeichen(a), fwAmpelFarbe(a), groesse = groesse)
    else AmpelPunkt(AmpelZeichen.HINWEIS, MaterialTheme.colorScheme.surfaceContainerHighest, groesse = groesse)
}

// Zeile unter einem fruehen Signal: Ampel, „Im Preis?“ und Satz, Tendenz mit Rueckblick und bei belegter Tendenz ein
// Uebungsvorschlag (dafuer state und oeffnen); aufgeklappt die Einzelheiten.
@Composable
internal fun ImPreisZeile(
    ip: ImPreis, lage: Fruehindikatoren.Lage?, modifier: Modifier = Modifier, state: AppState? = null, oeffnen: ((String) -> Unit)? = null,
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var auf by remember(ip.schluessel) { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val e = ip.einordnung
    val richtung = tendenz(e, ip.mass)
    val schluessel = TendenzRueckblick.schluessel(ip.schluessel, ip.markt)
    val bilanz = remember(schluessel, e.ampel) { rueckblick(schluessel, e.ampel) }
    Column(modifier.fillMaxWidth().clickable { auf = !auf }.animateContentSize().padding(start = 48.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            ImPreisAmpel(e.ampel, 14.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(S.epTitel, style = MaterialTheme.typography.labelMedium)
                Text(imPreisSatz(ip, lage), style = MaterialTheme.typography.bodySmall, color = grau)
                TendenzText(richtung, tendenzWas(ip), blass = bilanz != null && !bilanz.belegt)
                if (richtung != 0) RueckblickZeile(bilanz)
                if (e.schaetzung.staerke == Eingepreist.Staerke.SCHWACH) Text(S.epUnsicher, style = MaterialTheme.typography.labelSmall, color = grau)
                Vorschlaege(state, oeffnen, schluessel, bilanz, richtung)
            }
        }
        if (!auf) return@Column
        Column(Modifier.padding(start = 22.dp, top = 6.dp)) {
            Text(S.epHeute(massText(ip, e.y)), style = MaterialTheme.typography.bodySmall)
            if (e.urteil != Eingepreist.Urteil.UNKLAR) {
                Text(S.epUeblich(massText(ip, e.erwartet)), style = MaterialTheme.typography.bodySmall)
                if (e.urteil != Eingepreist.Urteil.PASST) {
                    val hoeher = e.urteil == Eingepreist.Urteil.HOEHER
                    Text(S.epSelten(Fmt.pct(if (hoeher) 100 - e.perzentil else e.perzentil, 0), hoeher), style = MaterialTheme.typography.bodySmall, color = grau)
                }
            }
            if (ip.kurve.size >= 2) {
                Text(S.epKurveTitel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                KurvenLinie(ip.kurve)
            }
            ip.zusatz?.let { z ->
                Text(S.epZusatzTitel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                    ImPreisAmpel(z.ampel, 12.dp)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        val zr = tendenz(z)
                        val zb = remember(z.ampel) { rueckblick(TendenzRueckblick.ZYKLISCH, z.ampel) }
                        Text(if (z.urteil == Eingepreist.Urteil.UNKLAR) S.epKeine else S.epZyklischSatz(urteilZahl(z.urteil)), style = MaterialTheme.typography.bodySmall, color = grau)
                        TendenzText(zr, S.epWasZyklisch, blass = zb != null && !zb.belegt)
                        if (zr != 0) RueckblickZeile(zb)
                        Vorschlaege(state, oeffnen, TendenzRueckblick.ZYKLISCH, zb, zr)
                        Text(S.epHeute(S.epZyklisch(Fmt.pct(abs(z.y), 1), z.y >= 0)), style = MaterialTheme.typography.labelSmall, color = grau)
                        Text(S.epStaerke(z.schaetzung.staerke.ordinal, Fmt.pct(z.schaetzung.r2 * 100, 0)), style = MaterialTheme.typography.labelSmall, color = grau)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(S.epStaerke(e.schaetzung.staerke.ordinal, Fmt.pct(e.schaetzung.r2 * 100, 0)), style = MaterialTheme.typography.labelSmall, color = grau)
                    Text(S.epZeitraum(e.schaetzung.n, monatText(e.schaetzung.von), monatText(e.schaetzung.bis)), style = MaterialTheme.typography.labelSmall, color = grau)
                    RueckblickEinzelheiten(bilanz)
                }
                IconButton(onClick = { info = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    if (info) MarktInfoBlatt(S.epTitel, S.epInfo + "\n\n" + S.trInfo + "\n\n" + S.epRisiken(ip.mass.name)) { info = false }
}

// Ohne Schaetzung aus der Geschichte (0.68): Bewegung des passenden Marktpreises seit der Meldung und ob sie in Richtung
// der Meldung lief (engine/SeitMeldung.kt); ohne passenden Markt oder ohne Kurse ein kurzer Satz dazu.
@Composable
internal fun SeitMeldungZeile(state: AppState, x: Fruehindikatoren.Signal, getreide: String? = null, modifier: Modifier = Modifier) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val markt = remember(x.schluessel, getreide) { SeitMeldung.markt(x.schluessel, getreide) }
    val richtung = when (Tendenz.fruehsignal(x).richtung) {
        Tendenz.Richtung.BULLISH -> 1
        Tendenz.Richtung.BEARISH -> -1
        else -> 0
    }
    val ab = SeitMeldung.meldetag(x.schluessel, x.stand)
    var geladen by remember(markt, ab, richtung) { mutableStateOf(false) }
    var ergebnis by remember(markt, ab, richtung) { mutableStateOf<SeitMeldung.Ergebnis?>(null) }
    LaunchedEffect(markt, ab, richtung) {
        if (markt == null) return@LaunchedEffect
        ergebnis = runCatching {
            val kurse = state.dailyCloses(markt)
            val index = SeitMeldung.vergleich(x.schluessel)?.let { state.dailyCloses(it) }
            withContext(Dispatchers.Default) { SeitMeldung.berechnen(markt, kurse, index, ab, richtung) }
        }.getOrNull()
        geladen = true
    }
    if (markt != null && !geladen) return
    val datum = Fmt.date(ab * TAG_MS + TAG_MS / 2)
    val e = ergebnis
    val lauf = e?.lauf
    val bewegung = e?.bewegung
    Column(modifier.fillMaxWidth().padding(start = 48.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            ImPreisAmpel(null, 14.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(S.epTitel, style = MaterialTheme.typography.labelMedium)
                if (markt == null) {
                    Text(S.smNichtMessbar, style = MaterialTheme.typography.bodySmall, color = grau)
                } else if (e == null) {
                    Text(S.smKeineKurse, style = MaterialTheme.typography.bodySmall, color = grau)
                } else if (lauf == null || bewegung == null) {
                    Text(S.smNochNicht(datum), style = MaterialTheme.typography.bodySmall, color = grau)
                } else {
                    Text(S.smSeit(datum, S.smMarkt(markt), Fmt.signedPct(bewegung * 100, 1)), style = MaterialTheme.typography.bodySmall)
                    Text(S.smLauf(lauf.ordinal), style = MaterialTheme.typography.bodySmall, color = grau)
                }
            }
        }
    }
}

private fun monatText(tag: Long): String = Fmt.monthShortYear(YearMonth.from(LocalDate.ofEpochDay(tag)))

// Die vier naechsten Kontrakte als kleine Linie, darunter Liefermonat und Kurs.
@Composable
private fun KurvenLinie(kurve: List<Pair<Eingepreist.Kontrakt, Double>>) {
    val farbe = MaterialTheme.colorScheme.onSurface
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val linie = MaterialTheme.colorScheme.outlineVariant
    val preise = kurve.map { it.second }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val lo = preise.min()
            val hi = preise.max()
            val spanne = (hi - lo).takeIf { it > 0 } ?: (abs(hi) * 0.02 + 1e-9)
            val rand = 6.dp.toPx()
            val breite = size.width / kurve.size
            fun punkt(i: Int) = Offset(breite * (i + 0.5f), (rand + (size.height - 2 * rand) * (1 - (preise[i] - lo) / spanne)).toFloat())
            drawLine(linie, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
            val p = Path()
            preise.indices.forEach { i -> val o = punkt(i); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            drawPath(p, farbe, style = Stroke(2.dp.toPx()))
            preise.indices.forEach { i -> drawCircle(farbe, 3.dp.toPx(), punkt(i)) }
        }
        Row(Modifier.fillMaxWidth()) {
            kurve.forEach { (k, v) ->
                Text(
                    Fmt.monthShortYear(YearMonth.of(k.jahr, k.monat)) + "\n" + Fmt.num(v, if (v < 20) 3 else 2),
                    style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ------------------------------------------------------------ Datierte Signale

// Rendite seit dem Signal gegen den Heimatindex mit Einordnung. art: INSIDER, HOCH, AB, MELDUNG oder HINWEIS.
// frueherZeiten: fruehere Signale derselben Art bei diesem Wert (ms), fuer den Vergleich nach sechs Monaten.
// titel: Ueberschrift ueber der Zeile, null ohne. oeffnen: Routen fuer den Uebungsvorschlag, ohne keiner.
@Composable
internal fun ImPreisSeitZeile(
    state: AppState, symbol: String, zeit: Long, richtung: Int, art: String, land: String? = null,
    frueherZeiten: List<Long> = emptyList(), titel: String? = null, modifier: Modifier = Modifier, oeffnen: ((String) -> Unit)? = null,
) {
    var ergebnis by remember(symbol, zeit) { mutableStateOf<Pair<Eingepreist.Gelaufen, String?>?>(null) }
    LaunchedEffect(symbol, zeit, richtung) {
        ergebnis = runCatching { imPreisSeit(state, symbol, zeit, richtung, land, frueherZeiten) }.getOrNull()
    }
    val (g, indexName) = ergebnis ?: return
    GelaufenZeile(g, indexName, art, titel, modifier, symbol, state, oeffnen)
}

// Kurse und Index laden und einordnen; null ohne Kurse.
internal suspend fun imPreisSeit(
    state: AppState, symbol: String, zeit: Long, richtung: Int, land: String?, frueherZeiten: List<Long>,
): Pair<Eingepreist.Gelaufen, String?>? {
    val kurse = state.dailyCloses(symbol).takeIf { it.size > 5 } ?: return null
    val idx = Aktienkennzahlen.index(symbol, land)
    val index = idx?.let { state.dailyCloses(it.first).takeIf { k -> k.size > 5 } }
    return withContext(Dispatchers.Default) {
        val frueher = Eingepreist.frueher(kurse, index, frueherZeiten)
        Eingepreist.gelaufen(kurse, index, zeit, richtung, frueher)?.let { it to idx?.second?.takeIf { index != null } }
    }
}

// Mit symbol, state und oeffnen erscheint bei belegter Tendenz ein Uebungsvorschlag auf den Wert selbst.
@Composable
internal fun GelaufenZeile(
    g: Eingepreist.Gelaufen, indexName: String?, art: String, titel: String?, modifier: Modifier = Modifier,
    symbol: String? = null, state: AppState? = null, oeffnen: ((String) -> Unit)? = null,
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var auf by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val richtung = tendenz(g)
    val bilanz = remember(art) { rueckblick(art) }
    Column(modifier.fillMaxWidth().clickable { auf = !auf }.animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        titel?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp)) }
        Row(verticalAlignment = Alignment.Top) {
            ImPreisAmpel(g.ampel)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(S.epSeit(art, Fmt.signedPct(g.mehr * 100, 1), indexName), style = MaterialTheme.typography.bodyMedium)
                Text(S.epLauf(g.lauf.ordinal), style = MaterialTheme.typography.bodySmall, color = grau)
                TendenzText(richtung, null, blass = bilanz != null && !bilanz.belegt)
                if (richtung != 0) RueckblickZeile(bilanz)
                val z = bilanz?.zeitraum
                if (z != null && richtung != 0 && symbol != null && state != null && oeffnen != null) WettVorschlag(state, symbol, richtung, z.wochen, oeffnen)
            }
        }
        if (!auf) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 26.dp, top = 4.dp)) {
            Column(Modifier.weight(1f)) {
                val s = g.schnitt
                Text(
                    if (s != null) S.epFrueher(Fmt.signedPct(s * g.richtung * 100, 1), g.n) else S.epRichtwert(Fmt.pct(Eingepreist.RICHTWERT * 100, 0)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(S.epHandelstage(g.tage), style = MaterialTheme.typography.labelSmall, color = grau)
                RueckblickEinzelheiten(bilanz)
            }
            IconButton(onClick = { info = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (info) MarktInfoBlatt(S.epTitel, S.epSeitInfo + "\n\n" + S.trInfo + "\n\n" + S.epRisiken("SEIT")) { info = false }
}

// Juengster Insiderkauf eines Werts aus den Meldungen auf dem Geraet (SEC, BaFin, Europa, weitere Laender), hoechstens
// 180 Tage alt; die frueheren Kaeufe dienen als Vergleich nach sechs Monaten.
@Composable
internal fun InsiderImPreis(state: AppState, symbol: String, isin: String, oeffnen: ((String) -> Unit)? = null) {
    val sec by state.sec.collectAsStateWithLifecycle()
    val bafin by state.bafin.collectAsStateWithLifecycle()
    val eu by state.euInsider.collectAsStateWithLifecycle()
    val welt by state.weltInsider.collectAsStateWithLifecycle()
    val tage = remember(sec, bafin, eu, welt, symbol, isin) {
        val l = ArrayList<String>()
        sec?.geschaefte?.filter { it.kauf && it.wert > 0 && it.k.equals(symbol, true) }?.forEach { l += it.d }
        if (isin.length == 12) {
            bafin?.eigengeschaefte?.filter { it.kauf && it.isin == isin }?.forEach { l += it.d }
            eu.values.forEach { d -> d.geschaefte.filter { it.kauf && it.isin == isin }.forEach { l += it.d } }
        }
        welt[symbol]?.second?.filter { it.kauf }?.forEach { l += it.d }
        l.mapNotNull { runCatching { LocalDate.parse(it.trim().take(10)).toEpochDay() }.getOrNull() }.distinct().sorted()
    }
    val letzter = tage.lastOrNull()?.takeIf { it >= LocalDate.now().toEpochDay() - 180 } ?: return
    ImPreisSeitZeile(
        state, symbol, letzter * TAG_MS + TAG_MS / 2, 1, "INSIDER",
        frueherZeiten = tage.dropLast(1).map { it * TAG_MS + TAG_MS / 2 }, titel = S.epInsiderTitel, oeffnen = oeffnen,
    )
}

private const val TAG_MS = 86_400_000L

// Juengste Hoch- oder Abstufung der letzten 180 Tage; fruehere Urteile derselben Richtung als Vergleich.
@Composable
internal fun AnalystenImPreis(state: AppState, daten: de.troetenrepublik.app.AnalystenDaten, land: String?, oeffnen: ((String) -> Unit)? = null) {
    val urteile = daten.verlauf.urteile
    val (letztes, richtung) = remember(urteile) {
        urteile.mapNotNull { u -> de.troetenrepublik.app.engine.Analystenbilanz.richtung(u)?.let { u to it } }.maxByOrNull { it.first.zeit }
    } ?: return
    if (System.currentTimeMillis() - letztes.zeit > 180 * TAG_MS) return
    val frueher = remember(urteile, letztes) {
        urteile.filter { it.zeit < letztes.zeit && de.troetenrepublik.app.engine.Analystenbilanz.richtung(it) == richtung }.map { it.zeit }
    }
    ImPreisSeitZeile(state, daten.quelle, letztes.zeit, richtung, if (richtung > 0) "HOCH" else "AB", land, frueher, S.epAnalystenTitel, oeffnen = oeffnen)
}

// Kaeufe einer Person aus dem Kongress oder der Regierung: je Aktie der juengste Kauf der letzten 120 Tage (hoechstens
// fuenf Werte), Lauf seit der Meldung gegen den S&P 500.
@Composable
internal fun KaeufeImPreis(state: AppState, geschaefte: List<de.troetenrepublik.app.market.Kongress.Geschaeft>, onOpen: (String) -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val kaeufe = remember(geschaefte) {
        val ab = System.currentTimeMillis() - 120 * TAG_MS
        geschaefte.filter { it.kauf && !it.kuerzel.isNullOrBlank() && it.oeffentlich >= ab && (it.typ == "ST" || it.typ.isBlank()) }
            .sortedByDescending { it.oeffentlich }.distinctBy { it.kuerzel!!.uppercase() }.take(5)
    }
    if (kaeufe.isEmpty()) return
    var werte by remember(kaeufe) { mutableStateOf<List<Pair<de.troetenrepublik.app.market.Kongress.Geschaeft, Pair<Eingepreist.Gelaufen, String?>>>?>(null) }
    LaunchedEffect(kaeufe) {
        werte = kaeufe.mapNotNull { g ->
            val s = CopyRouten.symbol(g.kuerzel!!)
            runCatching { imPreisSeit(state, s, g.oeffentlich, 1, "United States", emptyList()) }.getOrNull()?.let { g to it }
        }
    }
    val w = werte?.takeIf { it.isNotEmpty() } ?: return
    var info by remember { mutableStateOf(false) }
    val meldungBilanz = remember { rueckblick(TendenzRueckblick.MELDUNG) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(S.epKaeufeTitel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { info = true }) { Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(18.dp)) }
    }
    w.forEach { (g, e) ->
        val (lauf, index) = e
        Row(Modifier.fillMaxWidth().clickable { onOpen(CopyRouten.symbol(g.kuerzel!!)) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            ImPreisAmpel(lauf.ampel)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(CopyRouten.name(g.kuerzel, g.beschreibung) + " (" + g.kuerzel + ")", style = MaterialTheme.typography.titleSmall)
                Text(S.epKaufZeile(Fmt.date(g.oeffentlich), Fmt.signedPct(lauf.mehr * 100, 1), index ?: "S&P 500"), style = MaterialTheme.typography.bodySmall)
                Text(S.epLauf(lauf.lauf.ordinal), style = MaterialTheme.typography.bodySmall, color = grau)
                val r = tendenz(lauf)
                TendenzText(r, null, blass = meldungBilanz != null && !meldungBilanz.belegt)
                if (r != 0) RueckblickZeile(meldungBilanz)
            }
        }
    }
    Text(S.epKaeufeHinweis, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    if (info) MarktInfoBlatt(S.epTitel, S.epSeitInfo + "\n\n" + S.trInfo + "\n\n" + S.epRisiken("SEIT")) { info = false }
}
