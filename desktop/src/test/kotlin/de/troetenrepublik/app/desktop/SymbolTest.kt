package de.troetenrepublik.app.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xml.sax.InputSource
import java.io.File

// Das Programmsymbol fuer Windows, Linux und macOS entsteht aus dem Android-Symbol (Monogramm T mit Kerzen):
// weisses Feld mit runden Ecken, darin der Vordergrund des adaptiven Symbols. Die Bilder landen in
// build/symbol; desktop/icons und src/main/resources/troete.png sind Kopien davon (ICO per Pillow).
class SymbolTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun symbolAusAndroidVektor() {
        val xml = File("../app/src/main/res/drawable/ic_launcher_foreground.xml")
        val vektor = xml.inputStream().use { loadXmlImageVector(InputSource(it), Density(1f)) }
        val ziel = File("build/symbol").apply { mkdirs() }
        for (groesse in listOf(512, 256)) {
            ImageComposeScene(groesse, groesse, Density(1f)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22)).background(Color.White)) {
                    // Wie auf dem Handy nur die sichtbaren mittleren 72 von 108 Einheiten des adaptiven Symbols
                    Image(rememberVectorPainter(vektor), null, Modifier.fillMaxSize().scale(108f / 72f))
                }
            }.use { scene ->
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(ziel, "troete-$groesse.png").writeBytes(png)
                assertTrue(png.size > 1000)
            }
        }
    }
}
