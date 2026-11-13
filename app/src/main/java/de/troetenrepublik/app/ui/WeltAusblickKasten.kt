package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.WeltAusblick
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// [Globus Gesten 0.70] Weltwirtschaft neu: Weltwachstum je Jahr als Balken (Prognose abgesetzt), Treiber laut IWF,
// Verschiebung der Anteile am Welt-BIP und Aufsteiger.
// [0.71] Balken farbig nach Vorzeichen, Zahlen mittig ueber dem Balken, Treiber mit haengendem Einzug, Anteile je
// Wirtschaftsraum farbig, ausgeschriebene Namen und Laender antippbar.
private const val WEO_ADRESSE = "https://www.imf.org/en/Publications/WEO"

// Quelle fuer den Anhang am Ende des Globus-Reiters
internal fun weltAusblickQuelle() = QuelleLink(S.wwQuelle, WEO_ADRESSE)

@Composable
internal fun WeltAusblickKasten(d: Imf.Datensatz, karte: GlobusKarte, jahr: Int, onLand: (GlobusLand) -> Unit) {
    val balken = remember(d, jahr) { WeltAusblick.balken(d, jahr) }
    val letzt = remember(d) { WeltAusblick.letztesJahr(d) }
    if (balken.size < 2 || letzt == null) return
    val signal = LocalSignal.current
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val deutsch = I18n.locale.language == "de"
    fun name(code: String) = if (code == WeltAusblick.EURO) S.wwEuroraum else S.wwRaum(code, karte.nachA3(code)?.name(deutsch) ?: code)

    Zwischenkopf(S.wwBalkenTitel)
    WachstumsBalken(balken, signal.up, signal.down, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(170.dp))
    val z = remember(d, jahr, letzt) { WeltAusblick.zuwachs(d, jahr, letzt) }
    if (z != null) {
        KeyValueRow(S.wwSchnittLabel(z.bis), Fmt.pct(z.proJahr, 1), onClick = { info = S.wwSchnittLabel(z.bis) to S.wwSchnittInfo(z.von, z.bis) })
        KeyValueRow(S.wwGesamtLabel(z.bis), Fmt.signedPct(z.gesamt, 0), onClick = { info = S.wwGesamtLabel(z.bis) to S.wwGesamtInfo(z.von, z.bis) })
    }

    // Wer das Wachstum traegt: Beitrag der groessten Raeume zum Weltwachstum, dazu wo es sich beschleunigt oder abschwaecht
    val tragen = remember(d, jahr, letzt) { WeltAusblick.beitraege(d, jahr, letzt) }
    if (tragen != null && z != null) {
        val (liste, rest) = tragen
        KopfMitInfo(S.wwTragenTitel) { info = S.wwTragenTitel to S.wwTragenInfo(jahr, letzt, Fmt.pct(z.proJahr, 1)) }
        val hoechst = maxOf(liste.maxOf { it.punkte }, rest, 0.01)
        val zeilen = liste.mapIndexed { i, b ->
            BalkenZeile(name(b.raum), (b.punkte / hoechst).toFloat(), Fmt.num(b.punkte, 1) + " " + S.fwPunkte, Kategorie.farbe(i), antippen(b.raum, karte, onLand))
        } + BalkenZeile(S.wwUebrige, (rest.coerceAtLeast(0.0) / hoechst).toFloat(), Fmt.num(rest, 1) + " " + S.fwPunkte, MaterialTheme.colorScheme.outlineVariant)
        BalkenListe(zeilen)
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (b in liste) {
                val delta = b.aenderung ?: continue
                if (abs(delta) < TEMPO_SCHWELLE) continue
                val nun = Fmt.pct(b.schnitt, 1); val frueher = Fmt.pct(b.frueher ?: continue, 1)
                LbStichpunkt(if (delta > 0) S.wwBeschleunigt(name(b.raum), nun, frueher) else S.wwSchwaecht(name(b.raum), nun, frueher))
            }
        }
    }

    val jahre = remember(jahr, letzt) { listOf(Imf.ERSTES_JAHR, jahr, letzt).distinct() }
    val raeume = remember(d, jahre) { WeltAusblick.verschiebung(d, jahre) }
    if (raeume.isNotEmpty() && jahre.size >= 2) {
        KopfMitInfo(S.wwAnteileTitel) { info = S.wwAnteileTitel to S.wwAnteileInfo }
        val hoechst = raeume.maxOf { r -> r.anteile.maxOf { it.second } }
        raeume.forEachIndexed { i, r ->
            val land = if (r.raum == WeltAusblick.EURO) null else karte.nachA3(r.raum)
            AnteilZeile(
                name(r.raum), r.anteile, hoechst, Kategorie.farbe(i), land?.let { { onLand(it) } },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
            )
        }
        val gewinner = raeume.maxByOrNull { it.aenderung }
        val verlierer = raeume.minByOrNull { it.aenderung }?.takeIf { it.aenderung < 0 && it.raum != gewinner?.raum }
        fun punkte(v: Double) = (if (v > 0) "+" else if (v < 0) "−" else "") + Fmt.num(abs(v), 1)
        if (gewinner != null && gewinner.aenderung > 0) Text(
            S.wwVerschiebung(name(gewinner.raum), punkte(gewinner.aenderung), verlierer?.let { name(it.raum) }, verlierer?.let { punkte(it.aenderung) }, letzt),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }

    // Aufsteiger: schnellstes Wachstum und Platzgewinne in der Rangliste nach Wirtschaftsleistung
    val schnell = remember(d, jahr, letzt) { WeltAusblick.schnellste(d, jahr, letzt) }
    val plaetze = remember(d, jahr, letzt) { WeltAusblick.platzgewinner(d, jahr, letzt) }
    if (schnell.isNotEmpty() || plaetze.isNotEmpty()) Zwischenkopf(S.wwAufsteigerTitel)
    if (schnell.isNotEmpty()) {
        LbUnterkopf(S.wwSchnellsteTitel(letzt))
        for (sc in schnell) LbZeile(name(sc.code), Fmt.pct(sc.wert, 1), signal.up, antippen(sc.code, karte, onLand))
    }
    if (plaetze.isNotEmpty()) {
        LbUnterkopf(S.wwPlaetzeTitel(letzt))
        for (p in plaetze) LbZeile(name(p.code), S.wwPlatz(p.von, p.bis), signal.up, antippen(p.code, karte, onLand))
    }

    // Fester Begruendungstext des IWF: nur mit sichtbarem Stand und solange der Ausblick aktuell ist
    if (WeltAusblick.begruendungGilt(java.time.LocalDate.now(), letzt)) {
        Zwischenkopf(S.wwTreiberTitel)
        Text(S.wwStand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (t in S.wwTreiber) LbStichpunkt(t)
        }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Ab diesem Unterschied (Prozentpunkte) gegenueber den letzten fuenf Jahren steht eine Zeile zu Beschleunigung oder Abschwaechung
private const val TEMPO_SCHWELLE = 0.3

// Antippen eines Raums: das Laenderblatt, ausser beim Euroraum
private fun antippen(code: String, karte: GlobusKarte, onLand: (GlobusLand) -> Unit): (() -> Unit)? =
    if (code == WeltAusblick.EURO) null else karte.nachA3(code)?.let { l -> { onLand(l) } }

// Zwischenueberschrift, die ihren Infotext beim Antippen zeigt
@Composable
private fun KopfMitInfo(text: String, onInfo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onInfo).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// Balken je Jahr mit Wert und Jahr mittig ueber dem Balken; vergangene Jahre farbig nach Vorzeichen, Prognosejahre als
// Umriss mit leichter Fuellung, getrennt durch eine gestrichelte Linie und mit Aufschrift.
@Composable
private fun WachstumsBalken(balken: List<WeltAusblick.Balken>, auf: Color, ab: Color, modifier: Modifier) {
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.outline
    val schrift = MaterialTheme.colorScheme.onSurface
    val prognoseSchrift = MaterialTheme.colorScheme.primary
    val stil = TextStyle(fontSize = 11.sp, color = schrift)
    val prognoseText = S.wwPrognose
    Canvas(modifier) {
        val dpx = density
        val th = 14f * dpx
        val kopf = 18f * dpx
        val fuss = 18f * dpx
        val n = balken.size
        val slot = size.width / n
        val bw = slot * 0.62f
        val hi = max(balken.maxOf { it.wert }, 0.0)
        val lo = min(balken.minOf { it.wert }, 0.0)
        val oben = kopf + th
        val unten = size.height - fuss - (if (lo < 0) th else 0f)
        val spanne = (hi - lo).coerceAtLeast(0.5)
        fun y(v: Double) = oben + (unten - oben) * (1f - ((v - lo) / spanne).toFloat())
        val null0 = y(0.0)
        drawLine(grau, Offset(0f, null0), Offset(size.width, null0), strokeWidth = 1f * dpx)
        val ersterPrognose = balken.indexOfFirst { it.prognose }
        if (ersterPrognose > 0) {
            val x = slot * ersterPrognose
            drawLine(grau, Offset(x, kopf - 4f * dpx), Offset(x, size.height - fuss), strokeWidth = 1f * dpx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * dpx, 5f * dpx)))
        }
        if (ersterPrognose >= 0) text(messer, prognoseText, Offset(slot * ersterPrognose, 0f), stil.copy(color = prognoseSchrift), size.width - slot * ersterPrognose)
        balken.forEachIndexed { i, b ->
            val x0 = slot * i + (slot - bw) / 2f
            val yw = y(b.wert)
            val top = min(yw, null0)
            val h = abs(yw - null0).coerceAtLeast(1f)
            val r = CornerRadius(2f * dpx)
            val farbe = if (b.wert >= 0) auf else ab
            if (b.prognose) {
                drawRoundRect(farbe.copy(alpha = 0.22f), Offset(x0, top), Size(bw, h), r)
                drawRoundRect(farbe, Offset(x0, top), Size(bw, h), r, style = Stroke(1.5f * dpx))
            } else drawRoundRect(farbe.copy(alpha = 0.85f), Offset(x0, top), Size(bw, h), r)
            val wertText = (if (b.wert < 0) "−" else "") + Fmt.num(abs(b.wert), 1)
            val ty = if (b.wert >= 0) top - th else top + h + 1f * dpx
            text(messer, wertText, Offset(slot * i, ty), stil, slot, zentriert = true)
            text(messer, b.jahr.toString(), Offset(slot * i, size.height - fuss + 3f * dpx), stil.copy(color = schrift.copy(alpha = if (b.prognose) 1f else 0.7f), fontSize = 10.sp), slot, zentriert = true)
        }
    }
}

private fun DrawScope.text(messer: TextMeasurer, t: String, pos: Offset, stil: TextStyle, breite: Float, zentriert: Boolean = false) {
    val l = messer.measure(t, stil, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    // Eigenes Zentrieren: textAlign im measure zentriert nur innerhalb der gemessenen Breite, nicht im Feld
    val x = if (zentriert) pos.x + (breite - l.size.width) / 2f else pos.x
    drawText(l, topLeft = Offset(x, pos.y))
}

// Eine Zeile je Raum: Name, dann Balken fuer die Jahre mit Jahreszahl davor und Anteil dahinter. Laender sind
// antippbar und oeffnen das Laenderblatt; der Euroraum hat kein Blatt.
@Composable
private fun AnteilZeile(name: String, anteile: List<Pair<Int, Double>>, hoechst: Double, farbe: Color, onClick: (() -> Unit)?, modifier: Modifier) {
    val messer = rememberTextMeasurer()
    val schrift = MaterialTheme.colorScheme.onSurface
    val stil = TextStyle(fontSize = 10.sp, color = schrift)
    Row(
        modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(104.dp).padding(end = 6.dp))
        Canvas(Modifier.weight(1f).height((anteile.size * 15 + 2).dp)) {
            val dpx = density
            val jw = 30f * dpx
            val ww = 46f * dpx
            val maxB = (size.width - jw - ww).coerceAtLeast(1f)
            val zeile = size.height / anteile.size
            anteile.forEachIndexed { i, (j, a) ->
                val yy = zeile * i
                val bh = zeile - 3f * dpx
                val b = (maxB * (a / hoechst)).toFloat().coerceAtLeast(1f)
                val letzter = i == anteile.lastIndex
                text(messer, j.toString(), Offset(0f, yy + (bh - 12f * dpx) / 2f), stil.copy(color = schrift.copy(alpha = 0.7f)), jw)
                drawRoundRect(farbe.copy(alpha = if (letzter) 0.9f else if (i == 0) 0.35f else 0.6f), Offset(jw, yy), Size(b, bh), CornerRadius(2f * dpx))
                text(messer, Fmt.pct(a, 1), Offset(jw + b + 4f * dpx, yy + (bh - 12f * dpx) / 2f), stil, ww)
            }
        }
    }
}
