package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Beneish
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.BeneishTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.engine.Qualitaet
import de.troetenrepublik.app.engine.Qualitaet.Punkt
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi

// Jahresende (2025-09-30) als Datum der App.
private fun jahresende(ende: String): String =
    runCatching { Fmt.date(java.time.LocalDate.parse(ende).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()) }.getOrDefault(ende)

// Werte einer Pruefung fuer die Begruendung: Renditen und Quoten in Prozent, Cashflow als Betrag, Aktien als Stueckzahl.
// Aktienzahlen: zwei verschiedene Werte, die gerundet gleich aussaehen, stehen in Millionen mit so vielen Stellen, dass
// der Unterschied zu sehen ist
private const val NBSP = '\u00A0'

internal fun aktienText(v: Double, anderer: Double?): String {
    val grob = Fmt.compact(v)
    if (anderer == null || anderer == v || Fmt.compact(anderer) != grob || kotlin.math.abs(v) < 1e6) return grob
    val stellen = if (kotlin.math.abs(v - anderer) >= 5e4) 1 else 3
    // Einheit aus der Kurzform ("Mio." oder "m"): alles nach der Zahl, ohne das feste Leerzeichen davor
    val einheit = Fmt.compact(1e6).dropWhile { it.isDigit() || it == ',' || it == '.' }.trim { it.isWhitespace() || it == NBSP }
    return Fmt.num(v / 1e6, stellen) + NBSP + einheit
}

private fun pruefWert(punkt: Punkt, v: Double?, waehrung: String, anderer: Double? = null): String? = v?.let {
    when (punkt) {
        Punkt.CASHFLOW -> Fmt.mitSymbol(Fmt.compact(it), Derivatives.currencySymbol(waehrung))
        Punkt.KEINE_NEUEN_AKTIEN -> aktienText(it, anderer)
        Punkt.LIQUIDITAET_STEIGT, Punkt.UMSCHLAG_STEIGT -> Fmt.num(it, 2)
        else -> Fmt.pct(it * 100, 1)
    }
}

// Piotroski-F-Score und Altman-Z-Score auf der Wertpapierseite einer Aktie, ohne Banken, Versicherer und
// Finanzwerte. Der Boersenwert fuer Altman kommt aus dem Kurs (in die Berichtswaehrung umgerechnet).
@Composable
fun QualitaetKarte(state: AppState, symbol: String, quote: Quote?, profile: MarketApi.Profile?, kopf: Boolean = true) {
    val p = profile ?: return
    if (Qualitaet.ausgenommen(p.sector, p.industry)) return
    val signal = LocalSignal.current
    val fx by state.market.fxKurse.collectAsStateWithLifecycle()
    var jahre by remember(symbol) { mutableStateOf<List<Qualitaet.Jahr>?>(null) }
    LaunchedEffect(symbol) { jahre = state.jahresabschluesse(symbol) }
    if (kopf) SectionHeader(S.qsTitel) else Spacer(Modifier.height(8.dp))
    val j = jahre ?: run { ToolNote(S.qsLaden); return }
    val (f, z) = remember(j, p, quote?.marketCap, fx) { qualitaetAus(j, p, quote, fx) }
    val letztes = j.lastOrNull()
    val waehrung = letztes?.waehrung?.ifBlank { null } ?: p.financialCurrency.ifBlank { quote?.nativeCurrency.orEmpty() }
    val m = remember(j) { Beneish.berechne(j) }
    if (f == null && z == null && m == null) { ToolNote(S.qsKeineDaten); return }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 16.dp),
    ) {
        f?.let { pi ->
            val stufe = Qualitaet.stufe(pi)
            // Eingeklappt wie Altman und Beneish: Kernzahl und Pfeil, die Prüfpunkte erst beim Aufklappen
            var pruefOffen by rememberSaveable(symbol) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clickable { pruefOffen = !pruefOffen }.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(S.qsPiotroski, style = MaterialTheme.typography.titleSmall, color = grau)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    PunktText(
                        S.qsScore(pi.score, pi.geprueft) + (stufe?.let { " · " + S.qsStufe(it.name) } ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                        color = when (stufe) { Qualitaet.Stufe.STARK -> signal.up; Qualitaet.Stufe.SCHWACH -> signal.down; else -> MaterialTheme.colorScheme.onSurface },
                        modifier = Modifier.weight(1f),
                    )
                    AufklappPfeil(pruefOffen)
                }
                Text(S.qsJahre(jahresende(pi.jahr), jahresende(pi.vorjahr)), style = MaterialTheme.typography.bodySmall, color = grau)
            }
            if (pruefOffen) pi.pruefungen.forEach { pr ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(24.dp).padding(top = 2.dp)) {
                        when (pr.erfuellt) {
                            true -> CheckMark(size = 14.dp, color = signal.up)
                            false -> KreuzMark(size = 14.dp, color = signal.down)
                            null -> PunktMark(size = 14.dp, color = grau)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(S.qsPunkt(pr.punkt.name), style = MaterialTheme.typography.bodyMedium)
                        val jetzt = pruefWert(pr.punkt, pr.jetzt, waehrung, pr.vorher)
                        Text(
                            if (pr.erfuellt == null || jetzt == null) S.qsNichtPruefbar
                            else begruendungFest(pr.punkt.name, jetzt, pruefWert(pr.punkt, pr.vorher, waehrung, pr.jetzt)) +
                                (if (pr.punkt == Punkt.KEINE_NEUEN_AKTIEN && pr.erfuellt == true && pr.jetzt != null && pr.vorher != null && pr.jetzt > pr.vorher) "\n" + S.qsAktienSpielraum else ""),
                            style = MaterialTheme.typography.bodySmall, color = grau,
                        )
                    }
                }
            }
        }
        z?.let { a ->
            var teile by rememberSaveable(symbol) { mutableStateOf(false) }
            if (f != null) Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clickable { teile = !teile }.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(S.qsAltman, style = MaterialTheme.typography.titleSmall, color = grau)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PunktText(
                        Fmt.num(a.z, 2) + " · " + S.qsZone(a.zone.name), style = MaterialTheme.typography.titleLarge,
                        color = when (a.zone) { Qualitaet.Zone.SICHER -> signal.up; Qualitaet.Zone.GEFAEHRDET -> signal.down; else -> MaterialTheme.colorScheme.onSurface },
                        modifier = Modifier.weight(1f),
                    )
                    AufklappPfeil(teile)
                }
                if (a.fassung == Qualitaet.Fassung.HERSTELLER) Text(S.qsAltmanJahr(jahresende(letztes?.ende.orEmpty())) + ". " + S.qsZonen, style = MaterialTheme.typography.bodySmall, color = grau)
                else Text(S.qsAltmanJahrAndere(jahresende(letztes?.ende.orEmpty())) + ". " + S.qsZonenAndere, style = MaterialTheme.typography.bodySmall, color = grau)
            }
            if (teile) {
                (if (a.fassung == Qualitaet.Fassung.HERSTELLER) listOf("WC" to a.betriebskapital, "RE" to a.ruecklagen, "EBIT" to a.ebit, "MV" to a.boersenwert, "UMSATZ" to a.umsatz)
                else listOf("WC" to a.betriebskapital, "RE" to a.ruecklagen, "EBIT" to a.ebit, "BW" to a.boersenwert)).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                        Text(S.qsBaustein(k), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
                        Text(Fmt.num(v, 2), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        m?.let { b ->
            if (f != null || z != null) Spacer(Modifier.height(10.dp))
            BeneishBlock(symbol, b)
        }
    }
    ErklaerungAufklappen(S.qsHinweis + (if (m != null) "\n\n" + beneishText("info", mWert(Beneish.SCHWELLE)) else ""), Modifier.padding(top = 4.dp))
}

internal fun beneishText(key: String, vararg w: String): String = BeneishTexte.text(I18n.lang == Lang.DE, key, w.toList())

// M-Score mit zwei Stellen und dem Minuszeichen der App
internal fun mWert(v: Double): String = (if (v < 0) "−" else "") + Fmt.num(kotlin.math.abs(v), 2)

// Beneish-M-Score: Wert und Einordnung, darunter die Lage auf der Skala mit der Schwelle als Marke; ein Tipp zeigt die
// acht Kennzahlen (Rechnung in engine/Beneish.kt).
@Composable
private fun BeneishBlock(symbol: String, b: Beneish.Ergebnis) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val farbe = if (b.auffaellig) signal.down else MaterialTheme.colorScheme.onSurface
    var teile by rememberSaveable(symbol) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { teile = !teile }.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(beneishText("titel"), style = MaterialTheme.typography.titleSmall, color = grau)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PunktText(
                mWert(b.m) + " · " + beneishText(if (b.auffaellig) "auffaellig" else "unauffaellig"), style = MaterialTheme.typography.titleLarge,
                color = farbe, modifier = Modifier.weight(1f),
            )
            AufklappPfeil(teile)
        }
        Spacer(Modifier.height(6.dp))
        LageBalken(Beneish.lage(b.m), if (b.auffaellig) signal.down else Kategorie.blau, marke = Beneish.lage(Beneish.SCHWELLE))
        Spacer(Modifier.height(6.dp))
        Text(beneishText("satz", jahresende(b.jahr), mWert(Beneish.SCHWELLE)), style = MaterialTheme.typography.bodySmall, color = grau)
    }
    if (teile) {
        Text(
            beneishText("teile"), style = MaterialTheme.typography.labelSmall, color = grau,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 2.dp, bottom = 2.dp),
        )
        b.teile.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                Text(beneishText(t.kennzahl.name), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (t.kennzahl == Beneish.Kennzahl.TATA) Fmt.signedPct(t.wert * 100, 1) else Fmt.num(t.wert, 2),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

// Begruendung eines Punkts: Werte bleiben zusammen, umgebrochen wird nur nach der Bezeichnung und am Komma vor dem
// Vergleichswert.
private fun begruendungFest(punkt: String, jetzt: String, vorher: String?): String {
    val t = S.qsBegruendung(punkt, jetzt.replace(' ', '\u00A0'), vorher)
    val i = t.lastIndexOf(", ")
    return if (i < 0) t else t.substring(0, i + 2) + t.substring(i + 2).replace(' ', '\u00A0')
}

// Gezeichnetes Kreuz im Stil des Hakens (CheckMark): gleiche Strichstärke, runde Enden.
@Composable
private fun KreuzMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 14.dp, color: androidx.compose.ui.graphics.Color, stroke: androidx.compose.ui.unit.Dp = 2.dp) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val st = stroke.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.18f), androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.82f), st, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.18f), androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.82f), st, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

// Grauer Punkt für einen nicht prüfbaren Punkt.
@Composable
private fun PunktMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 14.dp, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(modifier.size(size)) { drawCircle(color, radius = this.size.minDimension * 0.22f) }
}
