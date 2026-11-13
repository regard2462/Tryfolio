package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Blasen
import de.troetenrepublik.app.engine.Ersatzquellen
import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.engine.WarnRueckblick
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

// [0.78] Karte je Blasenzeichen als Blatt: Kopf mit Ampel, Wert und Stand, ein Satz, was das Zeichen misst, die Skala mit
// den Zonen aus Blasen.SCHWELLEN (dieselben Schwellen wie die Ampel) und den Vergleichspunkten aus Blasen.VERGLEICHE,
// der Verlauf mit den Schwellen als Linien, wo die Quelle eine Reihe liefert, drei Stichpunkte und die Verweise.
// Geoeffnet aus dem Gesamtausblick (Blasenzeichen auf Rot) und aus dem Blasen-Detektor. zumDetektor: Sprung zum
// Blasen-Detektor im Ausblick, null im Detektor selbst.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlasenKarteBlatt(
    state: AppState, i: Blasen.Indikator, lage: AppState.BlasenLage?, oeffnen: (String) -> Unit,
    zumDetektor: (() -> Unit)?, onDismiss: () -> Unit,
) {
    val k = i.schluessel
    val verlauf = lage?.verlaeufe?.get(k).orEmpty()
    val punkte = remember(k, verlauf) { Blasen.vergleiche(k, verlauf) }
    var glossar by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rueckblick by remember { mutableStateOf(false) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustandHier(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().rollenMitStand().padding(bottom = 32.dp)) {
            // Kopf: Ampel und Name, darunter der Wert gross mit Einordnung und Stand
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                AmpelPunkt(fwAmpelZeichen(i.ampel), fwAmpelFarbe(i.ampel))
                Spacer(Modifier.width(12.dp))
                Text(blNameAlle(k), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(blWert(i), style = MaterialTheme.typography.headlineMedium, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val lageText = blLageAlle(k, i.ampel.ordinal)
                    if (lageText.isNotBlank()) Text(lageText, style = MaterialTheme.typography.bodyMedium)
                    blkStandText(i, lage)?.let { Text(S.blkStand(it), style = MaterialTheme.typography.labelSmall, color = grau) }
                }
            }
            if (k == Blasen.CAPE) i.zusatz?.let {
                Text(S.blkRang(Fmt.pct(it, 0)), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            }
            val kern = S.blkKern(k)
            if (kern.isNotBlank()) Text(kern, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp))

            // Skala mit Zonen, Wert und Vergleichspunkten; die Werte der Punkte als Liste darunter
            BlasenSkala(k, i.wert, blWert(i), punkte, Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
            if (punkte.isNotEmpty()) PunktText(
                punkte.joinToString(PUNKT) { blkMarke(it) + " " + blZahl(k, it.wert) },
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
            )

            // Staerke des Zeichens in Worten (volle Balken: gemessen, Umriss: gesetzt)
            blasenStaerke(k).let { KeyValueRow(S.blkStaerke, S.blkStaerkeWert(it.stufe, it.gemessen)) }

            // Zahlen, die nur einzelne Zeichen haben
            if (k == Blasen.MARGIN) i.zusatz?.let { KeyValueRow(S.blkKredite, S.ersMrdDollar(Fmt.num(it, 0))) }
            if (k == Blasen.BUFFETT) {
                val bip = i.stand?.let { blkQuartal(it) } ?: lage?.bipJahr?.toString()
                if (bip != null) KeyValueRow(S.blkBip, bip, hint = S.quelleWeltbank.takeIf { i.stand == null })
            }

            // Verlauf mit den Schwellen als waagrechte Linien
            if (verlauf.size >= 2) {
                val signal = LocalSignal.current
                val linien = Blasen.SCHWELLEN[k]?.let { listOf(it.gelb to signal.warn, it.rot to signal.down) }.orEmpty()
                Zwischenkopf(S.blkVerlauf, Modifier.padding(top = 8.dp))
                VerlaufDiagramm(
                    verlauf, MaterialTheme.colorScheme.onSurface, { blZahl(k, it) }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    lueckeMs = remember(verlauf) { blkLuecke(verlauf) }, linien = linien,
                )
            }

            // Ab wann Gelb und Rot in einer Zeile; warum es warnt und die Schwaeche hinter dem Info-Symbol
            val warum = listOf(S.blkWarum(k), S.blkSchwaeche(k)).filter { it.isNotBlank() }
            val schwellen = blkSchwellenText(k)
            if (warum.isNotEmpty() || schwellen != null) {
                InfoZeile(schwellen, blNameAlle(k), warum.joinToString("\n") { STICHPUNKT + it }, Modifier.padding(top = 12.dp))
            }

            // Verweise: Blasen-Detektor, Rueckblick, Stimmung, Begriff im Finanzwissen, FINRA
            zumDetektor?.let { NavRow(S.blTitel) { it() } }
            val bilanz = remember(k) { blkRueckblick(k)?.let { WarnRueckblick.bilanz(WarnRueckblick.datei, "sp500", it) } }
            if (bilanz != null) NavRow(S.wrTitel, S.wrKurzzeile(bilanz.treffer, bilanz.auswertbar, bilanz.fehlalarme)) { rueckblick = true }
            if (k == Blasen.STIMMUNG) NavRow(S.widgetStimmung, S.sbCnn) { oeffnen("maerkte/stimmung") }
            val begriff = S.blkBegriff(k)
            val begriffText by produceState<String?>(null, begriff) {
                value = begriff?.let { b -> withContext(Dispatchers.Default) { blkGlossarText(b) } }
            }
            begriffText?.let { t -> begriff?.let { b -> NavRow(b, S.knowledge) { glossar = b to t } } }
            if (k == Blasen.MARGIN) FinraLink()

            QuellenAnhang(
                listOfNotNull(
                    QuelleLink("Robert Shiller", Ersatzquellen.SHILLER_SEITE).takeIf { k == Blasen.CAPE },
                    fredQuelle(Ersatzquellen.MARGIN_REIHE).takeIf { k == Blasen.MARGIN },
                    fredQuelle("GDP").takeIf { k == Blasen.BUFFETT && i.stand != null },
                    QuelleLink(S.quelleWeltbank, "https://data.worldbank.org").takeIf { k == Blasen.BUFFETT && i.stand == null && lage?.bipJahr != null },
                ),
                Modifier.padding(top = 8.dp),
            )
        }
    }
    glossar?.let { (b, t) -> MarktInfoBlatt(b, t) { glossar = null } }
    if (rueckblick) WarnRueckblickSheet(state) { rueckblick = false }
}

// Stand der Daten: Monat beim CAPE, Quartal bei den Margin-Krediten, sonst der Tag der Abfrage.
private fun blkStandText(i: Blasen.Indikator, lage: AppState.BlasenLage?): String? = when {
    i.stand != null && i.schluessel == Blasen.CAPE -> ersMonat(i.stand)
    i.stand != null && i.schluessel == Blasen.MARGIN -> blkQuartal(i.stand)
    lage != null && lage.stand > 0 -> Fmt.date(lage.stand)
    else -> null
}

private fun blkQuartal(tag: Long): String {
    val d = java.time.LocalDate.ofEpochDay(tag)
    return S.ersQuartal((d.monthValue - 1) / 3 + 1, d.year)
}

private fun blkMarke(p: Blasen.Punkt): String = if (p.marke == Blasen.SCHNITT) S.blkSchnitt else p.marke

// „Gelb ab 22, Rot ab 30“ aus Blasen.SCHWELLEN; null ohne Schwellen.
internal fun blkSchwellenText(k: String): String? = Blasen.SCHWELLEN[k]?.let {
    S.blkSchwellen(blZahl(k, it.gelb), blZahl(k, it.rot), it.richtung == Blasen.Richtung.FAELLT, it.rotUeber)
}

// Zahl in der Einheit des Zeichens fuer Skala, Vergleichspunkte und Verlauf (echtes Minuszeichen, ohne Plus).
internal fun blZahl(k: String, v: Double): String {
    val prozent = k in setOf(Blasen.MARGIN, Blasen.BUFFETT, Blasen.TREND, Blasen.KONZENTRATION, Blasen.IPO, Blasen.GEWINN)
    val ganz = abs(v - Math.round(v)) < 0.05 || (prozent && abs(v) >= 10)
    val stellen = if (ganz) 0 else 1
    val minus = if (v < 0 && Math.round(abs(v) * if (ganz) 1.0 else 10.0) > 0) "−" else ""
    return when {
        prozent -> minus + Fmt.pct(abs(v), stellen)
        k == Blasen.PRAEMIE || k == Blasen.BREITE -> minus + Fmt.num(abs(v), stellen) + " " + S.fwPunkte
        else -> minus + Fmt.num(abs(v), stellen)
    }
}

// Luecke, ab der die Linie abreisst: dreimal der uebliche Abstand der Punkte, mindestens 14 Tage.
private fun blkLuecke(r: List<Pair<Long, Double>>): Long {
    val abstaende = r.zipWithNext { a, b -> b.first - a.first }.sorted()
    val ueblich = abstaende.getOrNull(abstaende.size / 2) ?: 0L
    return maxOf(14 * 86_400_000L, ueblich * 3)
}

// Zeichen im Rueckblick „Haette man es sehen koennen?“
private fun blkRueckblick(k: String): String? = when (k) {
    Blasen.CAPE -> WarnRueckblick.CAPE
    Blasen.KGV -> WarnRueckblick.KGV
    Blasen.PRAEMIE -> WarnRueckblick.PRAEMIE
    Blasen.MARGIN -> WarnRueckblick.MARGIN
    Blasen.BUFFETT -> WarnRueckblick.BUFFETT
    Blasen.TREND -> WarnRueckblick.HITZE
    else -> null
}

// Glossar des Finanzwissens (assets/finanzwissen, je Sprache eine Datei), einmal je Datei gelesen.
private val glossarSpeicher = HashMap<String, Map<String, String>>()

internal fun blkGlossarText(begriff: String): String? {
    val datei = if (I18n.lang == Lang.DE) "finanzwissen/data/glossar.json" else "finanzwissen/data/en/glossar.json"
    val m = synchronized(glossarSpeicher) { glossarSpeicher[datei] }
        ?: runCatching { blkGlossarLesen(de.troetenrepublik.app.plattform.Plattform.asset(datei).decodeToString()) }.getOrNull()
            ?.also { synchronized(glossarSpeicher) { glossarSpeicher[datei] = it } }
        ?: return null
    return m[begriff]?.takeIf { it.isNotBlank() }
}

// Begriff und Text je Eintrag; Verweise [[Begriff|Anzeige]] und Fettschrift werden zu einfachem Text.
internal fun blkGlossarLesen(text: String): Map<String, String> = runCatching {
    val liste = Json.parseToJsonElement(text) as? JsonArray ?: return emptyMap()
    liste.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val b = (o["begriff"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        val t = (o["text"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        b to t.replace(Regex("""\[\[(?:[^\]|]+\|)?([^\]]+)\]\]"""), "$1").replace("**", "")
    }.toMap()
}.getOrDefault(emptyMap())

// Spur (0, 1, ...) je Beschriftung in der gegebenen Reihenfolge: die erste Spur, in der sie keine schon gesetzte
// beruehrt (mit abstand dazwischen); null, wenn keine der maxSpuren frei ist.
internal fun blkSpuren(bereiche: List<Pair<Float, Float>>, maxSpuren: Int, abstand: Float): List<Int?> {
    val belegt = List(maxSpuren) { ArrayList<Pair<Float, Float>>() }
    return bereiche.map { (a, b) ->
        val spur = (0 until maxSpuren).firstOrNull { s -> belegt[s].none { (c, d) -> a < d + abstand && c < b + abstand } }
        spur?.also { belegt[it] += a to b }
    }
}

private class SkalaMarke(val x: Float, val links: Float, val text: TextLayoutResult, val vergleich: Boolean, val spur: Int)

// Waagrechte Skala: Zonen Gruen, Gelb, Rot nach Blasen.SCHWELLEN, darueber die Marke am aktuellen Wert mit dem Wert,
// darunter die Schwellen und die Vergleichspunkte. Beschriftungen, die sich ueberschneiden, rutschen in eine zweite oder
// dritte Zeile; was auch dort keinen Platz hat, steht nur in der Liste unter der Skala. Ohne Schwellen keine Skala.
@Composable
private fun BlasenSkala(k: String, wert: Double, wertText: String, punkte: List<Blasen.Punkt>, modifier: Modifier) {
    val s = Blasen.SCHWELLEN[k] ?: return
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val vorn = MaterialTheme.colorScheme.onSurface
    val flaeche = MaterialTheme.colorScheme.surfaceContainer
    val klein = MaterialTheme.typography.labelSmall.copy(color = grau)
    val gross = MaterialTheme.typography.labelLarge.copy(color = vorn, fontWeight = FontWeight.SemiBold)
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val (lo, hi) = remember(k, wert, punkte) { Blasen.skala(k, wert, punkte.map { it.wert }) }
    val zonen = remember(k, lo, hi) { Blasen.zonen(k, lo, hi) }
    val schnitt = S.blkSchnitt
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val breite = constraints.maxWidth.toFloat()
        fun x(v: Double): Float = ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * breite
        val wertLayout = remember(wertText, gross) { messer.measure(wertText, gross, maxLines = 1, softWrap = false) }
        // Erst die Schwellen, dann die Vergleichspunkte: bei Enge behalten die Schwellen ihren Platz in der ersten Zeile
        val unten = remember(k, lo, hi, punkte, breite, klein, schnitt) {
            val roh = listOf(s.gelb, s.rot).map { Triple(it, blZahl(k, it), false) } +
                punkte.map { Triple(it.wert, if (it.marke == Blasen.SCHNITT) schnitt else it.marke, true) }
            val gemessen = roh.map { (v, t, vergleich) ->
                val l = messer.measure(t, klein, maxLines = 1, softWrap = false)
                val px = x(v)
                Triple(px, (px - l.size.width / 2f).coerceIn(0f, (breite - l.size.width).coerceAtLeast(0f)), l) to vergleich
            }
            val abstand = with(dichte) { 6.dp.toPx() }
            val spuren = blkSpuren(gemessen.map { (g, _) -> g.second to g.second + g.third.size.width }, 3, abstand)
            gemessen.mapIndexedNotNull { n, (g, vergleich) -> spuren[n]?.let { SkalaMarke(g.first, g.second, g.third, vergleich, it) } }
        }
        val spurZahl = (unten.maxOfOrNull { it.spur } ?: -1) + 1
        val zeile = remember(klein) { messer.measure("0", klein, maxLines = 1).size.height.toFloat() }
        val obenPx = wertLayout.size.height + with(dichte) { 6.dp.toPx() }
        val bahnPx = with(dichte) { 12.dp.toPx() }
        val stichPx = with(dichte) { 5.dp.toPx() }
        val hoehePx = obenPx + bahnPx + stichPx + spurZahl * (zeile + with(dichte) { 2.dp.toPx() })
        Canvas(Modifier.fillMaxWidth().height(with(dichte) { hoehePx.toDp() })) {
            val bahnOben = obenPx
            val bahnUnten = obenPx + bahnPx
            // Zonen in der abgerundeten Bahn
            val bahn = Path().apply { addRoundRect(RoundRect(0f, bahnOben, size.width, bahnUnten, CornerRadius(bahnPx / 2))) }
            clipPath(bahn) {
                for (z in zonen) {
                    val farbe = when (z.ampel) { Ampel.GRUEN -> signal.up; Ampel.GELB -> signal.warn; Ampel.ROT -> signal.down }
                    drawRect(farbe.copy(alpha = 0.85f), Offset(x(z.von), bahnOben), Size(x(z.bis) - x(z.von), bahnPx))
                }
            }
            // Beschriftungen unter der Bahn mit Strich zur Stelle; erst alle Striche, dann die Schrift auf eigener Flaeche,
            // damit ein Strich zur zweiten Zeile keine Schrift der ersten kreuzt
            fun oben(b: SkalaMarke) = bahnUnten + stichPx + b.spur * (zeile + 2.dp.toPx())
            for (b in unten) drawLine(
                if (b.vergleich) vorn.copy(alpha = 0.7f) else grau.copy(alpha = 0.6f),
                Offset(b.x, if (b.vergleich) bahnOben else bahnUnten), Offset(b.x, oben(b)), strokeWidth = 1.dp.toPx(),
            )
            for (b in unten) {
                drawRect(flaeche, Offset(b.links - 2.dp.toPx(), oben(b)), Size(b.text.size.width + 4.dp.toPx(), b.text.size.height.toFloat()))
                drawText(b.text, topLeft = Offset(b.links, oben(b)))
            }
            // Marke am aktuellen Wert: Kreis auf der Bahn, der Wert darueber
            val xw = x(wert)
            val mitte = (bahnOben + bahnUnten) / 2
            drawCircle(flaeche, radius = 8.dp.toPx(), center = Offset(xw, mitte))
            drawCircle(vorn, radius = 5.5.dp.toPx(), center = Offset(xw, mitte))
            val links = (xw - wertLayout.size.width / 2f).coerceIn(0f, (size.width - wertLayout.size.width).coerceAtLeast(0f))
            drawText(wertLayout, topLeft = Offset(links, 0f))
        }
    }
}
