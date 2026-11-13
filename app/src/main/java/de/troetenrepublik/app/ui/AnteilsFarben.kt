package de.troetenrepublik.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import de.troetenrepublik.app.engine.DepotStreuung

// Gemeinsame, gedaempfte Farben fuer Anteilsbalken (Streuung, Wovon sie lebt, Foerderlaender, ETF-Anteile). Basis ist die
// Kategorie-Palette der App ohne Gruen und Rot; die Saettigung ist zurueckgenommen, indem jede Farbe ein Stueck zum
// Grau der Flaeche gemischt wird. Die Spur hinter dem Balken bleibt dunkelgrau (surfaceVariant).
object AnteilsFarben {
    // Reihenfolge der Folge: Blau, Tuerkis, Ocker, Lavendel, Rosa, Oliv, Schiefer
    private val FOLGE = listOf(0, 3, 1, 2, 4, 5, 6)

    private fun gedaempft(c: Color, dunkel: Boolean): Color =
        lerp(c, if (dunkel) Color(0xFF8A8D91) else Color(0xFF7A7D82), 0.32f)

    // i-te Farbe einer ruhigen Folge, nach Rang in der Liste
    @Composable
    fun folge(i: Int): Color = gedaempft(Kategorie.farbe(FOLGE[Math.floorMod(i, FOLGE.size)]), LocalSignal.current.dark)

    // Eine Region hat in der ganzen App dieselbe Farbe
    @Composable
    fun region(r: DepotStreuung.Region): Color {
        val dunkel = LocalSignal.current.dark
        return when (r) {
            DepotStreuung.Region.NORDAMERIKA -> gedaempft(Kategorie.farbe(0), dunkel)
            DepotStreuung.Region.EUROPA -> gedaempft(Kategorie.farbe(3), dunkel)
            DepotStreuung.Region.ASIEN_PAZIFIK -> gedaempft(Kategorie.farbe(1), dunkel)
            DepotStreuung.Region.LATEINAMERIKA -> gedaempft(Kategorie.farbe(4), dunkel)
            DepotStreuung.Region.NAHOST_AFRIKA -> gedaempft(Kategorie.farbe(2), dunkel)
            DepotStreuung.Region.UEBRIGE -> gedaempft(Kategorie.farbe(6), dunkel)
        }
    }

    // Region aus einer Bezeichnung der Berichte (EMEA, Asien-Pazifik, Amerika ohne USA) oder einem Laendercode;
    // null, wenn sie sich keiner Region zuordnen laesst
    fun regionVon(name: String, code: String = ""): DepotStreuung.Region? {
        if (code.length == 2) return DepotStreuung.region(code.uppercase())
        val n = name.lowercase()
        return when {
            ("ohne" in n || "ex-u" in n || "excl" in n) && ("usa" in n || "u.s" in n || "us" in n.split(" ", "-")) -> DepotStreuung.Region.LATEINAMERIKA
            "emea" in n || "europ" in n -> DepotStreuung.Region.EUROPA
            "asia" in n || "asien" in n || "pazific" in n || "pacific" in n || "pazifik" in n || "apac" in n || "china" in n || "japan" in n -> DepotStreuung.Region.ASIEN_PAZIFIK
            "latin" in n || "lateinamerika" in n -> DepotStreuung.Region.LATEINAMERIKA
            "united states" in n || "vereinigte staaten" in n || "usa" in n || "u.s." in n || "north america" in n || "nordamerika" in n || "canada" in n || "kanada" in n -> DepotStreuung.Region.NORDAMERIKA
            "middle east" in n || "nahost" in n || "africa" in n || "afrika" in n -> DepotStreuung.Region.NAHOST_AFRIKA
            else -> null
        }
    }

    // Farbe fuer die Streuungszahl nach Herfindahl: unter 1.500 gruen, bis 2.500 neutral, darueber rot
    @Composable
    fun herfindahl(hhi: Double): Color {
        val s = LocalSignal.current
        return when {
            hhi < 1500.0 -> s.up
            hhi <= 2500.0 -> MaterialTheme.colorScheme.onSurface
            else -> s.down
        }
    }
}
