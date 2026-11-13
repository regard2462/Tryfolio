package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Treemap.Rechteck

// Lage von Gruppen, Kacheln und Kopfzeilen der Heatmap beim Zoomen. Das Layout steht im Massstab der Zoomstufe s (Kopfzeile
// und Rand dort in echten Pixeln); bei beliebigem Zoom z gilt k = z / s >= 1. Die Gruppenflaeche waechst mit k, Rand und
// Kopfzeile behalten ihre Pixelgroesse, die Kacheln fuellen den Rest. So liegt die Kopfzeile immer direkt ueber den
// eigenen Kacheln, und der Text wird nie skaliert.
object HeatmapZoomLage {
    // Gruppenflaeche aus dem Layout auf dem Bildschirm
    fun flaeche(g: Rechteck, k: Float, vx: Float, vy: Float) = Rechteck(g.x * k + vx, g.y * k + vy, g.w * k, g.h * k)

    // Kachel t einer Gruppe g (beide im Layout) auf dem Bildschirm. rand ist der Rand je Seite, oben der Abstand vom
    // Gruppenanfang bis zu den Kacheln (Kopfzeile inklusive), beides in Pixeln.
    fun kachel(t: Rechteck, g: Rechteck, rand: Float, oben: Float, k: Float, vx: Float, vy: Float): Rechteck {
        val gz = flaeche(g, k, vx, vy)
        val iw = (g.w - 2 * rand).coerceAtLeast(0f)
        val ih = (g.h - oben - rand).coerceAtLeast(0f)
        val zw = (gz.w - 2 * rand).coerceAtLeast(0f)
        val zh = (gz.h - oben - rand).coerceAtLeast(0f)
        val fx = if (iw > 0f) zw / iw else 1f
        val fy = if (ih > 0f) zh / ih else 1f
        return Rechteck(gz.x + rand + (t.x - g.x - rand) * fx, gz.y + oben + (t.y - g.y - oben) * fy, t.w * fx, t.h * fy)
    }

    // [0.78] Ausschnitt einer vergroesserbaren Karte: Bildschirm = Flaeche mal zoom plus Versatz (vx, vy).
    data class Ausschnitt(val zoom: Float = 1f, val vx: Float = 0f, val vy: Float = 0f) {
        val vergroessert: Boolean get() = zoom > 1.01f
    }

    // Versatz so begrenzen, dass die Flaeche w x h den Rahmen immer ganz fuellt.
    fun begrenzt(a: Ausschnitt, w: Float, h: Float): Ausschnitt =
        a.copy(vx = a.vx.coerceIn(minOf(w - w * a.zoom, 0f), 0f), vy = a.vy.coerceIn(minOf(h - h * a.zoom, 0f), 0f))

    // Zoomen um den Punkt (mx, my) mit faktor und zugleich um (px, py) verschieben; der Punkt unter den Fingern bleibt
    // unter den Fingern. Zoom zwischen 1 und max. Ungueltige Eingaben (NaN beim Loslassen) aendern nichts.
    fun zoomen(a: Ausschnitt, mx: Float, my: Float, faktor: Float, px: Float, py: Float, w: Float, h: Float, max: Float): Ausschnitt {
        if (!(mx.isFinite() && my.isFinite() && faktor.isFinite() && px.isFinite() && py.isFinite()) || faktor <= 0f) return a
        val neu = (a.zoom * faktor).coerceIn(1f, max)
        val k = neu / a.zoom
        return begrenzt(Ausschnitt(neu, mx - (mx - a.vx) * k + px, my - (my - a.vy) * k + py), w, h)
    }

    // Mausrad mit Strg: eine Raste nach oben (delta < 0) vergroessert um RAD_SCHRITT, nach unten verkleinert.
    const val RAD_SCHRITT = 1.2f

    fun rad(a: Ausschnitt, mx: Float, my: Float, delta: Float, w: Float, h: Float, max: Float): Ausschnitt {
        if (!delta.isFinite() || delta == 0f) return a
        val f = if (delta < 0f) RAD_SCHRITT else 1f / RAD_SCHRITT
        return zoomen(a, mx, my, f, 0f, 0f, w, h, max)
    }

    // Zoomstufe, fuer die die Beschriftung gesetzt ist: die groesste Stufe bis zum Zoom.
    fun stufe(zoom: Float, stufen: List<Float>): Float = stufen.lastOrNull { it <= zoom + 1e-3f } ?: 1f

    // Eine Geste gehoert der Karte, wenn zwei Finger aufliegen oder schon vergroessert ist; sonst dem Rollen der Seite.
    fun gehoertKarte(finger: Int, a: Ausschnitt): Boolean = finger >= 2 || a.vergroessert

    // Oben links der Kopfzeile einer Gruppe (gz auf dem Bildschirm) in Pixeln, oder null, wenn der Text nicht ganz im
    // sichtbaren Ausschnitt sichtB x sichtH und in der Gruppe liegt. Am linken oder rechten Rand rueckt er in den sichtbaren
    // Teil der Gruppe; oben und unten wird nichts verschoben, weil sonst Kacheln darunter lagen.
    fun kopfPosition(gz: Rechteck, textB: Float, textH: Float, rand: Float, padX: Float, sichtB: Float, sichtH: Float): Pair<Float, Float>? {
        val y = gz.y + rand
        if (y < 0f || y + textH > sichtH || y + textH > gz.y + gz.h) return null
        val xMin = maxOf(gz.x, 0f) + padX
        val xMax = minOf(gz.x + gz.w, sichtB) - padX - textB
        if (xMin > xMax) return null
        return xMin to y
    }
}
