package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

// [Globus Gesten 0.73] Flaechen, auf denen ein Wisch nach unten die eigene Seite nie schliesst (Globus, gezoomte
// Heatmap): Ziehen dort bewegt nur den Inhalt. Eine Flaeche gehoert zu der Seite im Seitenstapel, auf der sie liegt,
// und gilt nur fuer deren Wisch. Seiten, die darueber liegen, lassen sich dort wie ueberall wegwischen.
internal object WischSperre {
    class Flaeche internal constructor(val seite: Any?, val aktiv: () -> Boolean, val rechteck: () -> Rect?)

    private val flaechen = ArrayList<Flaeche>()

    fun anmelden(seite: Any?, aktiv: () -> Boolean, rechteck: () -> Rect?): Flaeche =
        Flaeche(seite, aktiv, rechteck).also { flaechen += it }

    fun abmelden(f: Flaeche) { flaechen.remove(f) }

    // pos in Fensterkoordinaten, seite: die Seite, deren Wisch gerade geprueft wird
    fun enthaelt(pos: Offset, seite: Any?): Boolean {
        for (f in flaechen) {
            if (f.seite !== seite || !f.aktiv()) continue
            val r = f.rechteck() ?: continue
            if (pos.x >= r.left && pos.x <= r.right && pos.y >= r.top && pos.y <= r.bottom) return true
        }
        return false
    }
}

// aktiv: ob die Sperre gerade gilt (Heatmap: nur hineingezoomt). Die Seite kommt aus LocalSeite.
@Composable
internal fun Modifier.wischSperre(aktiv: () -> Boolean = { true }): Modifier {
    val seite = LocalSeite.current
    val lage = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val jetzt by rememberUpdatedState(aktiv)
    DisposableEffect(lage, seite) {
        val f = WischSperre.anmelden(seite, { jetzt() }) {
            val c = lage[0]?.takeIf { it.isAttached }
            c?.let { Rect(it.localToWindow(Offset.Zero), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())) }
        }
        onDispose { WischSperre.abmelden(f) }
    }
    return this.onGloballyPositioned { lage[0] = it }
}
