package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Alter
import de.troetenrepublik.app.engine.FedPunkte
import de.troetenrepublik.app.engine.Zinserwartung
import de.troetenrepublik.app.i18n.FedPunkteTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor

// [Fed-Projektionen 0.78] Ausblick: Punktdiagramm der Fed neben der Zinserwartung des Markts. Kernzeile mit dem Median
// zum Jahresende, darunter je Jahr die Punkte der Teilnehmer mit Median und, soweit die Futures reichen, der Markt.

private fun ft(key: String, vararg w: String): String = FedPunkteTexte.text(I18n.lang == Lang.DE, key, w.toList())

private fun datum(tag: LocalDate): String = Fmt.date(tag.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

// Die Datei einmal gelesen; eine nachgeholte Sitzung bleibt fuer die Laufzeit gemerkt
internal object FedPunkteLader {
    @Volatile private var stand: FedPunkte.Datei? = null
    @Volatile private var versucht = false

    fun gespeichert(): FedPunkte.Datei? = stand

    suspend fun laden(): FedPunkte.Datei? {
        if (versucht) return stand
        val d = withContext(Dispatchers.Default) {
            runCatching { FedPunkte.lesen(de.troetenrepublik.app.market.Daten.text(FedPunkte.DATEI)) }.getOrNull()
        }
        stand = d
        versucht = true
        return d
    }

    fun merken(d: FedPunkte.Datei) { stand = d }
}

// zins: Erwartung des Markts aus den Fed-Funds-Futures, null solange sie fehlt. Ohne Datei bleibt die Karte weg.
@Composable
internal fun FedPunkteKarte(state: AppState, zins: Zinserwartung.Erwartung?) {
    var datei by remember { mutableStateOf(FedPunkteLader.gespeichert()) }
    LaunchedEffect(Unit) {
        val d = datei ?: FedPunkteLader.laden() ?: return@LaunchedEffect
        datei = d
        // Neue Sitzung seit dem Stand der Datei: Seite der Federal Reserve nachholen (ein Tag gespeichert)
        ohneAbbruch { state.fedPunkteNeu(d.sitzungen.last().tag) }.getOrNull()?.let { neu ->
            val mit = FedPunkte.mit(d, neu)
            FedPunkteLader.merken(mit)
            datei = mit
        }
    }
    val d = datei ?: return
    val kern = remember(d) { FedPunkte.kern(d) } ?: return
    var info by remember { mutableStateOf(false) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val markt = remember(zins, kern) {
        kern.sitzung.spalten.mapNotNull { sp -> sp.toIntOrNull()?.let { j -> FedPunkte.marktEnde(zins?.pfad.orEmpty(), j)?.let { sp to it } } }.toMap()
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { info = true }.padding(16.dp),
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ft("titel"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(18.dp),
            )
        }
        Text(ft("kern", kern.jahr.toString(), Fmt.pct(kern.median, 2)), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
        // Einordnung gegen den heutigen Leitzins: Abstand des Medians in Prozentpunkten mit Dreieck
        zins?.heute?.zins?.let { heute ->
            val abstand = kern.median - heute
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ft("aktuell", Fmt.pct(heute, 2)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                ChangeValue(abstand, Fmt.num(kotlin.math.abs(abstand), 2) + " " + S.fwPunkte, style = MaterialTheme.typography.bodyMedium, colored = false)
            }
        }
        Text(ft("unter", datum(kern.sitzung.tag), kern.teilnehmer.toString()), style = MaterialTheme.typography.bodySmall, color = grau)
        Spacer(Modifier.height(10.dp))
        PunkteBild(kern.sitzung, markt)
        markt[kern.jahr.toString()]?.let { FedZeile(ft("markt", kern.jahr.toString()), Fmt.pct(it, 2)) }
        if (kern.vorher != null && kern.vorherTag != null) FedZeile(ft("vorher", kern.jahr.toString(), datum(kern.vorherTag)), Fmt.pct(kern.vorher, 2))
    }
    if (info) {
        val stand = Alter.tag(d.stand)?.let { Fmt.date(it * 86_400_000L) } ?: d.stand
        MarktInfoBlatt(ft("titel"), ft("info", stand)) { info = false }
    }
}

// Bezeichnung links (bricht um), Wert rechts einzeilig
@Composable
private fun FedZeile(label: String, wert: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).alignByBaseline())
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
    }
}

// Je Spalte (Jahr, langfristig) die Punkte der Teilnehmer auf der Hoehe ihres Satzes, nebeneinander um die Spaltenmitte.
// Der Median als Strich mit Zahl ueber der Spalte, der Markt als Raute am rechten Spaltenrand.
@Composable
private fun PunkteBild(s: FedPunkte.Sitzung, markt: Map<String, Double>) {
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val raster = MaterialTheme.colorScheme.outlineVariant
    val punktFarbe = MaterialTheme.colorScheme.primary
    val schrift = MaterialTheme.colorScheme.onSurface
    val marktFarbe = Kategorie.ocker
    val beschreibung = ft("bild")
    val lang = ft("lang")
    val marktKurz = ft("markt.kurz")
    val mediane = remember(s) { s.spalten.map { FedPunkte.median(s, it) } }
    Canvas(Modifier.fillMaxWidth().height(210.dp).semantics { contentDescription = beschreibung }) {
        val d = density
        val klein = TextStyle(fontSize = 10.sp, color = grau)
        val fett = TextStyle(fontSize = 11.sp, color = schrift, fontWeight = FontWeight.SemiBold)
        val alle = s.zeilen.map { it.satz } + markt.values
        val lo = floor(alle.min() * 2) / 2 - 0.25
        val hi = ceil(alle.max() * 2) / 2 + 0.25
        val schritt = if (hi - lo > 4) 1.0 else 0.5
        val striche = generateSequence(ceil(lo / schritt) * schritt) { it + schritt }.takeWhile { it <= hi + 1e-9 }.toList()
        val achse = striche.map { messer.measure(Fmt.num(it, 1), klein, maxLines = 1, softWrap = false) }
        val links = achse.maxOf { it.size.width } + 6f * d
        val oben = 18f * d
        val unten = 16f * d
        val h = size.height - oben - unten
        val b = size.width - links
        fun y(v: Double) = oben + h * (1f - ((v - lo) / (hi - lo)).toFloat())
        for ((i, v) in striche.withIndex()) {
            drawLine(raster, Offset(links, y(v)), Offset(size.width, y(v)), strokeWidth = 1f * d)
            drawText(achse[i], topLeft = Offset(links - 6f * d - achse[i].size.width, y(v) - achse[i].size.height / 2f))
        }
        val n = s.spalten.size
        val breite = b / n
        val meiste = s.zeilen.maxOf { z -> z.stimmen.max() }
        // Achtelschritte sollen sich nicht ueberdecken
        val radius = minOf(breite * 0.78f / (2f * meiste), h / ((hi - lo) / 0.125).toFloat() * 0.5f, 3.5f * d).coerceAtLeast(1.2f * d)
        for ((i, spalte) in s.spalten.withIndex()) {
            val mitte = links + breite * (i + 0.5f)
            for (z in s.zeilen) {
                val zahl = z.stimmen[i]
                if (zahl == 0) continue
                val start = mitte - (zahl - 1) * radius
                for (k in 0 until zahl) drawCircle(punktFarbe, radius, Offset(start + k * 2f * radius, y(z.satz)))
            }
            mediane[i]?.let { m ->
                drawLine(schrift, Offset(mitte - breite * 0.42f, y(m)), Offset(mitte + breite * 0.42f, y(m)), strokeWidth = 1.5f * d)
                val t = messer.measure(Fmt.num(m, 2), fett, maxLines = 1, softWrap = false)
                drawText(t, topLeft = Offset((mitte - t.size.width / 2f).coerceIn(links, size.width - t.size.width), 0f))
            }
            markt[spalte]?.let { m ->
                val x = links + breite * (i + 1) - 7f * d
                val r = 4f * d
                drawPath(Path().apply { moveTo(x, y(m) - r); lineTo(x + r, y(m)); lineTo(x, y(m) + r); lineTo(x - r, y(m)); close() }, marktFarbe)
            }
            val name = messer.measure(if (spalte == FedPunkte.LANG) lang else spalte, klein, maxLines = 1, softWrap = false)
            drawText(name, topLeft = Offset((mitte - name.size.width / 2f).coerceIn(links, size.width - name.size.width), size.height - unten + 3f * d))
        }
    }
    // Die Raute des Marktes steht in einer Legende unter der Grafik, nie zwischen den Punkten
    if (markt.isNotEmpty()) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) {
            val c = center
            val r = size.minDimension / 2f
            drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, marktFarbe)
        }
        Spacer(Modifier.width(6.dp))
        Text(marktKurz, style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
    }
}
