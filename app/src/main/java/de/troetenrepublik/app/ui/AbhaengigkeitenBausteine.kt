package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Einflussverzeichnis.Art
import de.troetenrepublik.app.engine.Rohstoffmengen
import de.troetenrepublik.app.i18n.S

// [0.76] Bausteine der Karte "Abhaengigkeiten und Risiken": ruhige Zeilen mit kleiner Marke fuer die Art der Angabe
// (gemeldet, gemessen, abgeleitet), Balken fuer Anteile und Punkte, Einzelheiten zum Aufklappen direkt auf der Seite.

// Kleine Marke fuer die Art: voller Punkt gemeldet, halber Punkt gemessen, Ring abgeleitet. Ohne Art bleibt die Stelle frei,
// damit die Bezeichnungen untereinander stehen.
@Composable
internal fun ArtMarke(art: Art?, modifier: Modifier = Modifier) {
    val farbe = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val m = if (art == null) modifier else modifier.semantics { contentDescription = S.abhArt(art.name) }
    Canvas(m.size(8.dp)) {
        if (art == null) return@Canvas
        val r = size.minDimension / 2f
        val strich = 1.4.dp.toPx()
        when (art) {
            Art.GEMELDET -> drawCircle(farbe, radius = r)
            Art.ABGELEITET -> drawCircle(farbe, radius = r - strich / 2f, style = Stroke(strich))
            Art.GEMESSEN -> {
                drawCircle(farbe, radius = r - strich / 2f, style = Stroke(strich))
                drawArc(farbe, startAngle = 90f, sweepAngle = 180f, useCenter = true, size = Size(r * 2f, r * 2f))
            }
        }
    }
}

// Waagerechter Balken in voller Breite; anteil 0 bis 1
@Composable
internal fun KartenBalken(anteil: Float, farbe: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(anteil.coerceIn(0.02f, 1f)).fillMaxHeight().background(farbe))
    }
}

// Zeile der Karte: Marke, Bezeichnung links, Wert rechts (bricht nie um, lange Bezeichnungen laufen in die naechste Zeile),
// optional zweite Zeile und Balken. Mit details klappt ein Tipp die Einzelheiten direkt darunter auf.
@Composable
internal fun KartenZeile(
    label: String,
    wert: String = "",
    art: Art? = null,
    unter: String? = null,
    anteil: Float? = null,
    balkenFarbe: Color = MaterialTheme.colorScheme.primary,
    marke: (@Composable () -> Unit)? = null,
    grau: Boolean = false,
    startOffen: Boolean = false,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var offen by rememberSaveable(label, unter) { mutableStateOf(startOffen) }
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().alpha(if (grau) 0.55f else 1f)
            .then(if (details != null) Modifier.clickable { offen = !offen } else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtMarke(art)
            Spacer(Modifier.width(10.dp))
            if (marke != null) { marke(); Spacer(Modifier.width(6.dp)) }
            if (wert.isNotEmpty()) BezeichnungWert(label, wert, Modifier.weight(1f))
            else Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (details != null) {
                Spacer(Modifier.width(10.dp))
                AufklappPfeil(offen)
            }
        }
        unter?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = sv, modifier = Modifier.padding(start = 18.dp, top = 1.dp))
        }
        if (anteil != null) KartenBalken(anteil, balkenFarbe, Modifier.padding(start = 18.dp, top = 6.dp))
        if (offen && details != null) Column(Modifier.padding(start = 18.dp, top = 6.dp)) { details() }
    }
}

// Bezeichnung links, Wert rechts in einer Zeile (gemeinsame Regel ZeilenLayout): Passt beides nicht nebeneinander, bricht die
// Bezeichnung links um und der Wert bleibt rechts. Nur ein Wert ueber zwei Drittel der Breite steht darunter; solche
// Werte gibt die Karte nicht aus (kurze Laendernamen). Der Wert bricht nie um, die Schrift bleibt gleich.
@Composable
private fun BezeichnungWert(label: String, wert: String, modifier: Modifier) {
    val stil = MaterialTheme.typography.bodyMedium
    androidx.compose.ui.layout.Layout(
        content = {
            Text(label, style = stil)
            Text(wert, style = stil, textAlign = TextAlign.End, softWrap = false, maxLines = 1)
        },
        modifier = modifier,
    ) { teile, c ->
        val abstand = 12.dp.roundToPx()
        val breite = c.maxWidth
        val w = teile[1].measure(androidx.compose.ui.unit.Constraints(maxWidth = breite))
        val ganz = teile[0].maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
        if (de.troetenrepublik.app.engine.ZeilenLayout.art(ganz, w.width, breite, abstand) != de.troetenrepublik.app.engine.ZeilenLayout.Art.WERT_DARUNTER) {
            val l = teile[0].measure(androidx.compose.ui.unit.Constraints(maxWidth = (breite - w.width - abstand).coerceAtLeast(0)))
            val h = maxOf(l.height, w.height)
            layout(breite, h) { l.place(0, (h - l.height) / 2); w.place(breite - w.width, (h - w.height) / 2) }
        } else {
            val l = teile[0].measure(androidx.compose.ui.unit.Constraints(maxWidth = breite))
            layout(breite, l.height + w.height) { l.place(0, 0); w.place(breite - w.width, l.height) }
        }
    }
}

// Einzelheit unter einer aufgeklappten Zeile
@Composable
internal fun DetailText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
}

// Einzelheit mit Wert rechts
@Composable
internal fun DetailZeile(label: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, softWrap = false, maxLines = 1, textAlign = TextAlign.End)
    }
}

// Ein Land mit Anteil als kleiner Balken (Foerderung eines Rohstoffs)
@Composable
internal fun LandBalken(name: String, prozent: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        KartenBalken(prozent / 100f, AnteilsFarben.folge(0), Modifier.width(84.dp))
        Spacer(Modifier.width(8.dp))
        Text(Fmt.pct(prozent.toDouble(), 0), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 40.dp))
    }
}

// [0.76] Eine Zeile der Laendertabelle eines Rohstoffs: Name, Menge, Anteil und Wert (null: kein Preis)
internal class MengenZeile(val name: String, val menge: String, val anteil: String, val wert: String?)

// Anteil in Prozent: unter 0,01 Prozent "< 0,01 %", sonst zwei Stellen unter 1 Prozent und eine darueber
internal fun prozentText(anteil: Double): String =
    if (Rohstoffmengen.anteilKlein(anteil)) "< " + Fmt.pct(Rohstoffmengen.ANTEIL_KLEIN, 2) else Fmt.pct(anteil, if (anteil < 1.0) 2 else 1)

// Die Tabelle passt, wenn neben den Zahlenspalten noch mindestens nameMin fuer den Namen bleibt (alle Masse in Pixeln);
// sonst steht der Name mit dem Anteil oben und Menge und Wert darunter.
internal fun tabelleAlsSpalten(breite: Int, zahlenBreite: Int, nameMin: Int): Boolean = breite - zahlenBreite >= nameMin

// Laender eines Rohstoffs als Tabelle mit Kopfzeile: Name links (lange Namen laufen in die naechste Zeile), Menge, Anteil und
// Wert rechtsbuendig, nie umbrochen. Ist neben den Zahlen zu wenig Platz fuer den Namen, wechselt der Aufbau: Name und Anteil
// in einer Zeile, Menge und Wert darunter. Die Schrift bleibt gleich.
@Composable
internal fun MengenTabelle(kopf: MengenZeile, zeilen: List<MengenZeile>) {
    val stil = MaterialTheme.typography.bodySmall
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    val mitWert = zeilen.any { it.wert != null }
    val alle = listOf(kopf) + zeilen
    androidx.compose.ui.layout.Layout(
        content = {
            alle.forEachIndexed { i, z ->
                val farbe = if (i == 0) sv else MaterialTheme.colorScheme.onSurface
                Text(z.name, style = stil, color = farbe)
                Text(z.menge, style = stil, color = farbe, softWrap = false, maxLines = 1)
                Text(z.anteil, style = stil, color = farbe, softWrap = false, maxLines = 1)
                if (mitWert) Text(z.wert.orEmpty(), style = stil, color = farbe, softWrap = false, maxLines = 1)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) { teile, c ->
        val je = if (mitWert) 4 else 3
        val inf = androidx.compose.ui.unit.Constraints()
        val abstand = 12.dp.roundToPx()
        val nameMin = 110.dp.roundToPx()
        val breite = c.maxWidth
        val zeilenAnzahl = teile.size / je
        // Jede Zelle wird nur einmal gemessen; die Zahlenzellen mit voller Breite, der Name erst, wenn die Breite feststeht
        val zahlenZellen = (0 until zeilenAnzahl).map { z -> (1 until je).map { s -> teile[z * je + s].measure(inf) } }
        val zahlen = (0 until je - 1).map { s -> zahlenZellen.maxOf { it[s].width } }
        val zahlenBreite = zahlen.sum() + abstand * (je - 1)
        val spalten = tabelleAlsSpalten(breite, zahlenBreite, nameMin)
        val nameBreite = if (spalten) breite - zahlenBreite else (breite - zahlen[1] - abstand).coerceAtLeast(0)
        // Passt das laengste Wort eines Namens nicht in die Namensspalte, steht der Name allein ueber volle Breite und die
        // Zahlen darunter; so wird nie mitten im Wort getrennt
        val eigeneZeile = BooleanArray(zeilenAnzahl) { z -> teile[z * je].minIntrinsicWidth(Int.MAX_VALUE) > nameBreite }
        val namen = (0 until zeilenAnzahl).map { z ->
            teile[z * je].measure(androidx.compose.ui.unit.Constraints(maxWidth = if (eigeneZeile[z]) breite else nameBreite))
        }
        val hoehen = IntArray(zeilenAnzahl) { z ->
            if (eigeneZeile[z]) namen[z].height + (if (spalten) zahlenZellen[z].maxOf { it.height }
                else zahlenZellen[z][1].height + maxOf(zahlenZellen[z][0].height, zahlenZellen[z].getOrNull(2)?.height ?: 0)) + 6.dp.roundToPx()
            else if (spalten) maxOf(namen[z].height, zahlenZellen[z].maxOf { it.height }) + 4.dp.roundToPx()
            else maxOf(namen[z].height, zahlenZellen[z][1].height) + maxOf(zahlenZellen[z][0].height, zahlenZellen[z].getOrNull(2)?.height ?: 0) + 6.dp.roundToPx()
        }
        layout(breite, hoehen.sum()) {
            var y = 0
            for (z in 0 until zeilenAnzahl) {
                val n = namen[z]
                val zz = zahlenZellen[z]
                if (eigeneZeile[z]) {
                    n.place(0, y)
                    val y1 = y + n.height
                    if (spalten) {
                        var xEnde = breite
                        for (s in je - 2 downTo 0) { zz[s].place(xEnde - zz[s].width, y1); xEnde -= zahlen[s] + abstand }
                    } else {
                        zz[1].place(breite - zz[1].width, y1)
                        val y2 = y1 + zz[1].height
                        zz[0].place(0, y2)
                        if (zz.size > 2) zz[2].place(breite - zz[2].width, y2)
                    }
                } else if (spalten) {
                    n.place(0, y)
                    var xEnde = breite
                    for (s in je - 2 downTo 0) { zz[s].place(xEnde - zz[s].width, y); xEnde -= zahlen[s] + abstand }
                } else {
                    n.place(0, y); zz[1].place(breite - zz[1].width, y)
                    val y2 = y + maxOf(n.height, zz[1].height)
                    zz[0].place(0, y2)
                    if (zz.size > 2) zz[2].place(breite - zz[2].width, y2)
                }
                y += hoehen[z]
            }
        }
    }
}

// Titel eines Unterabschnitts; darunter, falls vorhanden, eine kleine Zeile (Stand, Zeitraum)
@Composable
internal fun AbschnittKopf(titel: String, zusatz: String?) {
    Zwischenkopf(titel)
    zusatz?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), modifier = Modifier.padding(horizontal = 20.dp))
    }
}

// Kernaussagen zum Unternehmen: kurze Saetze mit Punkt
@Composable
internal fun KernBlock(saetze: List<String>) {
    if (saetze.isEmpty()) return
    Zwischenkopf(S.abhKernTitel)
    saetze.forEach {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

// Die drei Marken mit ihren Worten, einmal fuer die ganze Karte; ein Tipp erklaert sie
@Composable
internal fun LegendeZeile() {
    var offen by remember { mutableStateOf(false) }
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable { offen = true }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Art.GEMELDET, Art.GEMESSEN, Art.ABGELEITET).forEach {
            ArtMarke(it)
            Spacer(Modifier.width(5.dp))
            Text(S.abhArt(it.name), style = MaterialTheme.typography.labelSmall, color = sv, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(14.dp))
        }
        Icon(Icons.Outlined.Info, S.infoErklaerung, tint = sv, modifier = Modifier.size(14.dp))
    }
    if (offen) MarktInfoBlatt(S.abhTitel, S.abhInfo) { offen = false }
}

// Unauffaellige Zeile mit Pfeil, etwa der Weg zum Wirkungsnetz am Ende der Karte
@Composable
internal fun LeiseVerweis(titel: String, untertitel: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (untertitel.isNotBlank()) Text(untertitel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        }
        ChevronRight()
    }
}

// Kleine graue Zeile ohne Wert, etwa eine Luecke
@Composable
internal fun LeiseZeile(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}
