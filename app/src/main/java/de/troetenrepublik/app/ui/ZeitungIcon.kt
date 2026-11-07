package de.troetenrepublik.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Zeitung ohne gezackten Rand (5.93): Blatt mit runden Ecken und umgeschlagener Spalte links, ausgespart Schlagzeile,
// Bild und zwei Textzeilen.
internal val ZeitungIcon: ImageVector by lazy {
    ImageVector.Builder("Zeitung", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(7.5f, 3.5f)
            lineTo(19f, 3.5f)
            quadTo(21.5f, 3.5f, 21.5f, 6f)
            lineTo(21.5f, 18f)
            quadTo(21.5f, 20.5f, 19f, 20.5f)
            lineTo(5f, 20.5f)
            quadTo(2f, 20.5f, 2f, 17.5f)
            lineTo(2f, 10f)
            quadTo(2f, 8.5f, 3.5f, 8.5f)
            lineTo(5f, 8.5f)
            lineTo(5f, 6f)
            quadTo(5f, 3.5f, 7.5f, 3.5f)
            close()
            // Schlagzeile
            moveTo(8f, 6.5f); lineTo(18.5f, 6.5f); lineTo(18.5f, 9f); lineTo(8f, 9f); close()
            // Bild
            moveTo(8f, 11f); lineTo(12.5f, 11f); lineTo(12.5f, 17.5f); lineTo(8f, 17.5f); close()
            // Textzeilen
            moveTo(14.5f, 11f); lineTo(18.5f, 11f); lineTo(18.5f, 12.8f); lineTo(14.5f, 12.8f); close()
            moveTo(14.5f, 15.7f); lineTo(18.5f, 15.7f); lineTo(18.5f, 17.5f); lineTo(14.5f, 17.5f); close()
        }
    }.build()
}
