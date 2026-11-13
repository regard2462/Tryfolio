package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import de.troetenrepublik.app.engine.Netz
import de.troetenrepublik.app.engine.NetzLayout
import kotlin.math.abs
import kotlin.math.sqrt

// Zeichnung des Netzes (Karte und Vollbild): Kaesten mit dem ganzen Namen, Kanten als Linien mit Pfeilspitze (gezeichnetes
// Dreieck), Art und Betrag am Mittelpunkt der Kante, Guete-Marke daneben. Absichtserklaerungen gestrichelt, beendete und
// zurueckgezogene grau. Die Vorbereitung misst die Schrift, die Zeichnung rechnet nichts mehr.

// Durchmesser des runden Logos in dp. logoD: Durchmesser in px (0: kein Logo), logoX: Abstand des Logos vom linken Rand,
// textX: Abstand des Textes vom linken Rand (bei Knoten mit Logo reserviert die Breite Logo plus Abstand).
internal const val NETZ_LOGO_DP = 26

internal class ZGroesse(val text: TextLayoutResult, val b: Float, val h: Float, val logoD: Float, val logoX: Float, val textX: Float)

internal class ZKnoten(
    val id: String, val typ: Netz.Typ, val text: TextLayoutResult, val b: Float, val h: Float, val x: Float, val y: Float,
    val start: Boolean, val mehr: Boolean,
    val logoD: Float, val logoX: Float, val textX: Float, val symbol: String?, val domain: String?, val name: String,
) {
    fun enthaelt(px: Float, py: Float): Boolean = abs(px - x) <= b / 2f && abs(py - y) <= h / 2f
}

internal class ZKante(
    val kante: Netz.Kante, val x1: Float, val y1: Float, val x2: Float, val y2: Float, var mx: Float, var my: Float,
    var label: TextLayoutResult?, var gut: TextLayoutResult?, var lb: Float, var lh: Float, val hervor: Boolean, val dimm: Boolean, val grau: Boolean,
) {
    fun enthaelt(px: Float, py: Float, rand: Float): Boolean {
        if (label != null && abs(px - mx) <= lb / 2f + rand && abs(py - my) <= lh / 2f + rand) return true
        // Nahe an der Linie
        val dx = x2 - x1; val dy = y2 - y1
        val l2 = dx * dx + dy * dy
        if (l2 < 1f) return false
        val t = (((px - x1) * dx + (py - y1) * dy) / l2).coerceIn(0f, 1f)
        val ex = x1 + t * dx - px; val ey = y1 + t * dy - py
        return sqrt(ex * ex + ey * ey) <= rand
    }
}

internal class ZNetz(val knoten: List<ZKnoten>, val kanten: List<ZKante>) {
    // Ausdehnung der Zeichnung: Knoten (mit Logo) und die gezeichneten Beschriftungen der Kanten
    val links: Float get() = minOf(knoten.minOfOrNull { it.x - it.b / 2f } ?: 0f, kanten.filter { it.label != null }.minOfOrNull { it.mx - it.lb / 2f } ?: Float.MAX_VALUE)
    val rechts: Float get() = maxOf(knoten.maxOfOrNull { it.x + it.b / 2f } ?: 0f, kanten.filter { it.label != null }.maxOfOrNull { it.mx + it.lb / 2f } ?: -Float.MAX_VALUE)
    val oben: Float get() = minOf(knoten.minOfOrNull { it.y - it.h / 2f } ?: 0f, kanten.filter { it.label != null }.minOfOrNull { it.my - it.lh / 2f } ?: Float.MAX_VALUE)
    val unten: Float get() = maxOf(knoten.maxOfOrNull { it.y + it.h / 2f } ?: 0f, kanten.filter { it.label != null }.maxOfOrNull { it.my + it.lh / 2f } ?: -Float.MAX_VALUE)

    fun knotenAn(px: Float, py: Float): ZKnoten? = knoten.lastOrNull { it.enthaelt(px, py) }
    fun kanteAn(px: Float, py: Float, rand: Float): ZKante? = kanten.lastOrNull { it.enthaelt(px, py, rand) }
}

internal class ZFarben(
    val text: Color, val flaeche: Color, val rand: Color, val grau: Color, val gutA: Color, val gutB: Color, val gutC: Color, val start: Color,
    val knoten: Color, val aufStart: Color, val ring: Color,
)

internal class ZSchrift(val messer: TextMeasurer, val knoten: TextStyle, val person: TextStyle, val kante: TextStyle, val dichte: Float)

// Schrift messen: Name bricht an Wortgrenzen um, nie abgeschnitten. maxBox: groesste Breite des ganzen Kastens in px (null: ohne
// Grenze ausser der Standardbreite des Namens), der Name bricht dann frueher um; ein einzelnes Wort bleibt ganz. logo: Firmen
// und Fonds mit Logo.
internal fun messeKnoten(s: ZSchrift, g: Netz.Graph, id: String, maxBox: Float? = null, logo: Boolean = true): ZGroesse {
    val k = g.knoten[id]
    val person = k?.typ == Netz.Typ.PERSON
    val mitLogo = logo && (k?.typ == Netz.Typ.FIRMA || k?.typ == Netz.Typ.FONDS)
    val name = NetzAnzeige.name(g, id)
    val stil = if (person) s.person else s.knoten
    // Mindestbreite: das laengste Wort (Bindestrich-Teile zusammen), damit nur an Leerzeichen umbrochen wird
    val wortBreite = name.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { s.messer.measure(it, stil).size.width } ?: 0
    val logoD = if (mitLogo) NETZ_LOGO_DP * s.dichte else 0f
    val luecke = if (mitLogo) 8f * s.dichte else 0f
    val links = (if (mitLogo) 6f else if (person) 8f else 12f) * s.dichte
    val rechts = (if (person) 8f else 12f) * s.dichte
    val py = (if (person) 4f else if (mitLogo) 6f else 8f) * s.dichte
    val textX = links + logoD + luecke
    val standard = (if (person) 110f else 150f) * s.dichte
    val umbruch = if (maxBox != null) minOf(standard, maxBox - textX - rechts) else standard
    val r = s.messer.measure(name, stil, constraints = Constraints(maxWidth = maxOf(umbruch.toInt(), wortBreite)))
    return ZGroesse(r, textX + r.size.width + rechts, maxOf(r.size.height.toFloat(), logoD) + 2 * py, logoD, links, textX)
}

// Schnittpunkt der Linie vom Mittelpunkt eines Kastens zum Ziel mit dem Rand des Kastens
private fun rand(cx: Float, cy: Float, b: Float, h: Float, tx: Float, ty: Float): Offset {
    val dx = tx - cx; val dy = ty - cy
    if (dx == 0f && dy == 0f) return Offset(cx, cy)
    val t = minOf(if (dx != 0f) b / 2f / abs(dx) else Float.MAX_VALUE, if (dy != 0f) h / 2f / abs(dy) else Float.MAX_VALUE)
    return Offset(cx + dx * t, cy + dy * t)
}

// Baut die Zeichnung: Kaesten an ihren Plaetzen, Kanten (parallele Kanten versetzt) mit Beschriftung.
// kreis: Kanten (id) der hervorgehobenen Kreislaeufe; null: keine Hervorhebung.
internal fun baueNetz(
    s: ZSchrift, g: Netz.Graph, ids: List<String>, kanten: List<Netz.Kante>, pos: Map<String, NetzLayout.Punkt>, groessen: Map<String, ZGroesse>,
    start: String?, mehr: Set<String>, kreis: Set<String>?, heute: String, mitBeschriftung: Boolean,
): ZNetz {
    val kn = ids.mapNotNull { id ->
        val p = pos[id] ?: return@mapNotNull null
        val gr = groessen[id] ?: return@mapNotNull null
        ZKnoten(
            id, g.knoten[id]?.typ ?: Netz.Typ.FIRMA, gr.text, gr.b, gr.h, p.x, p.y, id == start, id in mehr,
            gr.logoD, gr.logoX, gr.textX, g.knoten[id]?.symbol, g.knoten[id]?.domain, NetzAnzeige.name(g, id),
        )
    }
    val nach = kn.associateBy { it.id }
    val gruppen = kanten.filter { it.von in nach && it.nach in nach }.groupBy { listOf(it.von, it.nach).sorted().joinToString("|") }
    val zk = ArrayList<ZKante>()
    for (liste in gruppen.values) {
        liste.forEachIndexed { i, k ->
            val a = nach.getValue(k.von); val b = nach.getValue(k.nach)
            val dx = b.x - a.x; val dy = b.y - a.y
            val l = maxOf(sqrt(dx * dx + dy * dy), 1f)
            val versatz = (i - (liste.size - 1) / 2f) * 24f * s.dichte
            val ox = -dy / l * versatz; val oy = dx / l * versatz
            val p1 = rand(a.x, a.y, a.b, a.h, b.x + ox, b.y + oy)
            val p2 = rand(b.x, b.y, b.b, b.h, a.x + ox, a.y + oy)
            val text = if (mitBeschriftung) NetzAnzeige.kantenText(k) else ""
            val lr = if (text.isNotEmpty()) s.messer.measure(text, s.kante) else null
            val gr = if (mitBeschriftung) s.messer.measure(k.guete.name, s.kante) else null
            val pad = 5f * s.dichte
            val chip = if (gr != null) gr.size.width + 6f * s.dichte else 0f
            val lb = if (lr != null) lr.size.width + chip + 2 * pad else 0f
            val lh = if (lr != null) maxOf(lr.size.height, gr?.size?.height ?: 0) + 2 * pad * 0.6f else 0f
            zk += ZKante(
                k, p1.x + ox * 0f, p1.y, p2.x, p2.y, (p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f, lr, gr, lb, lh,
                hervor = kreis != null && k.id in kreis, dimm = kreis != null && k.id !in kreis,
                grau = !k.aktiv(heute),
            )
        }
    }
    if (mitBeschriftung) beschriftungenVerteilen(zk, kn, s.dichte)
    return ZNetz(kn, zk)
}

// Beschriftungen entlang ihrer Kante zwischen 30 und 70 Prozent verschieben, bis sie keine andere Beschriftung und keinen
// Knoten beruehren. Reihenfolge: hervorgehobene, dann Guete A, dann der Rest. Was nirgends frei liegt, wird nicht beschriftet
// (die Kante bleibt antippbar, das Blatt zeigt alles).
private fun beschriftungenVerteilen(zk: List<ZKante>, kn: List<ZKnoten>, dichte: Float) {
    val luft = 3f * dichte
    val belegt = ArrayList<ZKante>()
    val reihenfolge = zk.sortedBy { if (it.hervor) 0 else if (it.kante.guete == Netz.Guete.A) 1 else 2 }
    val schritte = floatArrayOf(0.5f, 0.42f, 0.58f, 0.35f, 0.65f, 0.3f, 0.7f)
    for (k in reihenfolge) {
        if (k.label == null) continue
        var gefunden = false
        for (t in schritte) {
            val cx = k.x1 + (k.x2 - k.x1) * t; val cy = k.y1 + (k.y2 - k.y1) * t
            val frei = kn.none { n -> abs(cx - n.x) < (n.b + k.lb) / 2f + luft && abs(cy - n.y) < (n.h + k.lh) / 2f + luft } &&
                belegt.none { o -> abs(cx - o.mx) < (o.lb + k.lb) / 2f + luft && abs(cy - o.my) < (o.lh + k.lh) / 2f + luft }
            if (frei) { k.mx = cx; k.my = cy; gefunden = true; break }
        }
        if (gefunden) belegt += k else { k.label = null; k.gut = null; k.lb = 0f; k.lh = 0f }
    }
}

// Ebene mit den Logos ueber dem Canvas. Sie bekommt dieselbe Abbildung wie die Zeichnung (transform setzt translationX/Y und
// scaleX/Y, der Ursprung ist links oben), die Logos sitzen an ihren Canvas-Koordinaten. Die Abbildung wird erst beim Zeichnen
// der Ebene gelesen, Zoomen und Verschieben berechnen also nichts neu. Kein Zeigerverhalten: Tippen laeuft ueber den Canvas.
@Composable
internal fun NetzLogos(knoten: List<ZKnoten>, transform: GraphicsLayerScope.() -> Unit) {
    val dichte = LocalDensity.current
    Box(Modifier.fillMaxSize().graphicsLayer { transformOrigin = TransformOrigin(0f, 0f); transform() }) {
        for (n in knoten) {
            if (n.logoD <= 0f) continue
            key(n.id) {
                val x = (n.x - n.b / 2f + n.logoX).roundToInt()
                val y = (n.y - n.logoD / 2f).roundToInt()
                Box(Modifier.offset { IntOffset(x, y) }.size(with(dichte) { n.logoD.toDp() })) {
                    // Ohne Kuerzel das Buchstabenfeld, ohne Netzabruf
                    val sym = n.symbol
                    val dom = n.domain
                    when {
                        sym != null -> InstrumentLogo(sym, n.name, NETZ_LOGO_DP)
                        dom != null -> QuellenLogo(dom, n.name, NETZ_LOGO_DP.dp)
                        else -> InstrumentAvatar(n.id, n.name, NETZ_LOGO_DP)
                    }
                }
            }
        }
    }
}

private fun DrawScope.pfeilspitze(zu: Offset, von: Offset, farbe: Color, groesse: Float) {
    val dx = zu.x - von.x; val dy = zu.y - von.y
    val l = maxOf(sqrt(dx * dx + dy * dy), 1f)
    val ux = dx / l; val uy = dy / l
    val bx = zu.x - ux * groesse; val by = zu.y - uy * groesse
    val halb = groesse * 0.45f
    val pfad = Path().apply {
        moveTo(zu.x, zu.y)
        lineTo(bx - uy * halb, by + ux * halb)
        lineTo(bx + uy * halb, by - ux * halb)
        close()
    }
    drawPath(pfad, farbe)
}

// Zeichnet Kanten, dann Kaesten, dann die Beschriftungen der Kanten
internal fun DrawScope.zeichneNetz(z: ZNetz, f: ZFarben, dichte: Float, mitBeschriftung: Boolean, weitere: Set<String> = emptySet()) {
    val strich = 1.6f * dichte
    for (k in z.kanten) {
        val farbe = NetzAnzeige.farbe(k.kante.art)
        val alpha = when { k.grau -> 0.35f; k.dimm -> 0.22f; else -> 1f }
        val absicht = k.kante.status == Netz.Status.ABSICHT
        val c = (if (k.grau) f.grau else if (absicht) Color(0xFFE5883A) else farbe).copy(alpha = alpha)
        // Kreislauf: gelb hinterlegt
        if (k.hervor) drawLine(Color(0xFFF5C33B).copy(alpha = 0.38f), Offset(k.x1, k.y1), Offset(k.x2, k.y2), strokeWidth = strich * 7f)
        drawLine(
            c, Offset(k.x1, k.y1), Offset(k.x2, k.y2), strokeWidth = if (k.hervor) strich * 2.2f else strich,
            pathEffect = if (absicht) PathEffect.dashPathEffect(floatArrayOf(10f * dichte, 7f * dichte)) else null,
        )
        if (k.kante.art.gerichtet) pfeilspitze(Offset(k.x2, k.y2), Offset(k.x1, k.y1), c, 11f * dichte)
    }
    for (n in z.knoten) {
        val person = n.typ == Netz.Typ.PERSON
        val tl = Offset(n.x - n.b / 2f, n.y - n.h / 2f)
        val groesse = Size(n.b, n.h)
        // Einzeiliger Name: Pille, mehrzeilig: staerkere Rundung der Ecken
        val rad = if (person || n.text.lineCount <= 1) n.h / 2f else minOf(16f * dichte, n.h / 2f)
        val r = CornerRadius(rad)
        // Dezenter Schatten
        drawRoundRect(Color.Black.copy(alpha = 0.06f), tl + Offset(-1f * dichte, 1.5f * dichte), Size(n.b + 2f * dichte, n.h + 3f * dichte), CornerRadius(rad + dichte))
        drawRoundRect(Color.Black.copy(alpha = 0.10f), tl + Offset(0f, 1.5f * dichte), groesse, r)
        drawRoundRect(if (n.start) f.start else f.knoten, tl, groesse, r)
        if (!n.start) {
            val pe = if (n.typ == Netz.Typ.SAMMEL || n.typ == Netz.Typ.STAAT) PathEffect.dashPathEffect(floatArrayOf(5f * dichte, 4f * dichte)) else null
            drawRoundRect(f.rand.copy(alpha = 0.7f), tl, groesse, r, style = Stroke(1.2f * dichte, pathEffect = pe))
        }
        if (n.logoD > 0f) {
            // Helle Unterlage hinter dem Logo, damit es auch auf der Startfarbe sitzt
            drawCircle(f.knoten, n.logoD / 2f + 2f * dichte, Offset(tl.x + n.logoX + n.logoD / 2f, n.y))
        }
        drawText(n.text, color = if (n.start) f.aufStart else f.text, topLeft = Offset(tl.x + n.textX, n.y - n.text.size.height / 2f))
        if (n.mehr) {
            // Plus an der Ecke des Kastens: weitere Nachbarn zum Aufklappen
            val cx = n.x + n.b / 2f - rad * 0.29f; val cy = n.y - n.h / 2f + rad * 0.29f
            val rr = 8f * dichte
            drawCircle(f.ring, rr + 1.8f * dichte, Offset(cx, cy))
            drawCircle(f.start, rr, Offset(cx, cy))
            drawLine(f.aufStart, Offset(cx - rr * 0.5f, cy), Offset(cx + rr * 0.5f, cy), strokeWidth = 1.8f * dichte)
            drawLine(f.aufStart, Offset(cx, cy - rr * 0.5f), Offset(cx, cy + rr * 0.5f), strokeWidth = 1.8f * dichte)
        }
    }
    if (!mitBeschriftung) return
    for (k in z.kanten) {
        val lr = k.label ?: continue
        val gr = k.gut
        val alpha = when { k.grau -> 0.55f; k.dimm -> 0.35f; else -> 1f }
        val tl = Offset(k.mx - k.lb / 2f, k.my - k.lh / 2f)
        drawRoundRect(f.flaeche.copy(alpha = 0.92f * alpha), tl, Size(k.lb, k.lh), CornerRadius(6f * dichte))
        drawRoundRect(NetzAnzeige.farbe(k.kante.art).copy(alpha = 0.7f * alpha), tl, Size(k.lb, k.lh), CornerRadius(6f * dichte), style = Stroke(1f * dichte))
        val pad = 5f * dichte
        drawText(lr, color = f.text.copy(alpha = alpha), topLeft = Offset(tl.x + pad, k.my - lr.size.height / 2f))
        if (gr != null) {
            val gf = when (k.kante.guete) { Netz.Guete.A -> f.gutA; Netz.Guete.B -> f.gutB; Netz.Guete.C -> f.gutC }
            drawText(gr, color = gf.copy(alpha = alpha), topLeft = Offset(tl.x + pad + lr.size.width + 6f * dichte, k.my - gr.size.height / 2f))
        }
    }
}
