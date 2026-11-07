package de.troetenrepublik.app.plattform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import de.troetenrepublik.app.engine.GlobusAnsicht
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.GlobusPfad
import de.troetenrepublik.app.engine.GlobusZeichner

// Globus als Bitmap, etwa fuer ein Widget. Gleiche Geometrie und Farbberechnung wie die Ansicht in der App.
object GlobusBild {

    private class AndroidPfad(val pfad: Path) : GlobusPfad {
        override fun nach(x: Float, y: Float) = pfad.moveTo(x, y)
        override fun linie(x: Float, y: Float) = pfad.lineTo(x, y)
        override fun schliessen() = pfad.close()
    }

    // breite, hoehe: Pixel des Bildes; lon, lat: Blickmitte in Grad. farbe: ARGB je Land (zum Beispiel
    // GlobusFarbe.farbe mit den Werten des Landes), null = Land ohne Daten. hintergrund: GlobusFarbe.HINTERGRUND oder
    // 0 fuer durchsichtig. fuellung: Anteil der kleineren Bildseite, den die Kugel ausfuellt.
    fun zeichne(
        karte: GlobusKarte, breite: Int, hoehe: Int, lon: Double, lat: Double,
        farbe: (GlobusLand) -> Int?,
        hintergrund: Int = GlobusFarbe.HINTERGRUND,
        fuellung: Float = 0.96f,
    ): Bitmap {
        val bild = Bitmap.createBitmap(breite.coerceAtLeast(1), hoehe.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bild)
        if (hintergrund != 0) c.drawColor(hintergrund)
        val r = minOf(breite, hoehe) / 2f * fuellung
        val a = GlobusAnsicht()
        a.setze(lon, lat, breite / 2f, hoehe / 2f, r)
        val fuell = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        c.drawCircle(breite / 2f, hoehe / 2f, r, fuell.apply { color = GlobusFarbe.KUGEL })
        val pfad = Path().apply { fillType = Path.FillType.EVEN_ODD }
        val ziel = AndroidPfad(pfad)
        val mindest = 0.6f
        for (l in karte.laender) {
            pfad.rewind()
            if (!GlobusZeichner.flaeche(l, a, ziel, mindest)) continue
            fuell.color = farbe(l) ?: GlobusFarbe.OHNE_DATEN
            c.drawPath(pfad, fuell)
        }
        val strich = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(0.7f, r / 220f)
            color = GlobusFarbe.GRENZE
            alpha = 190
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        pfad.rewind()
        GlobusZeichner.linien(karte.linien, a, ziel, mindest)
        c.drawPath(pfad, strich)
        strich.color = 0xFF2C2C30.toInt(); strich.alpha = 255
        c.drawCircle(breite / 2f, hoehe / 2f, r, strich)
        return bild
    }
}
