package de.troetenrepublik.app

import de.troetenrepublik.app.data.WidgetKarten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Widget-Auswahl: echte Vorschaubilder statt handgebauter Layouts, einzigartige Widgets oben, Tabellen unten.
class Fassung078U57Test {

    private val res = listOf("src/main/res", "../app/src/main/res").map { File(it) }.first { it.exists() }
    private val manifest = listOf("src/main/AndroidManifest.xml", "../app/src/main/AndroidManifest.xml").map { File(it) }.first { it.exists() }

    private val infos = File(res, "xml").listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith("_info.xml") }!!.sortedBy { it.name }

    // Feste Reihenfolge der Empfaenger im Manifest (Klassen ohne "Widget")
    private val reihenfolge = listOf(
        "Stimmung", "Durchbrueche", "Fruehwarnung", "Globus", "Heatmap", "Blasen", "Marktphase", "Crashtest", "Ausblick", "Konvergenz", "Volatilitaet",
        "Trends", "Streuung", "Performance", "Depotcheck", "Umschichten", "Sektoren", "KongressFlow", "Themen", "Prognose", "Depot",
        "Einzelwert", "Signale", "Stillhalter", "IndexBeweger", "Analytics", "Branche", "Meistgekauft", "Haeuser", "Warnzeichen", "Lage",
        "Wissen", "Schnell", "Dividenden", "Aktienfinder", "Vorschlagsdepot", "InsiderSimulator", "Mover", "Bewegungen",
        "Werte", "Maerkte", "Copytrading", "Orders", "Termine", "InsiderSelten", "Praesidial", "News",
    )

    @Test
    fun siebenundvierzigInfoDateien() {
        assertEquals(47, infos.size)
    }

    @Test
    fun keineInfoDateiHatEinVorschauLayout() {
        infos.forEach { assertFalse("previewLayout in ${it.name}", it.readText().contains("previewLayout")) }
        assertEquals(0, File(res, "layout").listFiles { f -> f.name.startsWith("widget_vorschau_") }!!.size)
    }

    @Test
    fun jedeInfoDateiHatEinBildInBeidenSprachen() {
        val bilder = Regex("android:previewImage=\"@drawable/(\\w+)\"")
        val namen = infos.map { f ->
            val m = bilder.find(f.readText())
            assertTrue("kein previewImage in ${f.name}", m != null)
            m!!.groupValues[1]
        }
        assertEquals(47, namen.toSet().size)
        namen.forEach { n ->
            assertTrue("fehlt de: $n", File(res, "drawable-nodpi/$n.webp").exists())
            assertTrue("fehlt en: $n", File(res, "drawable-en-nodpi/$n.webp").exists())
        }
    }

    @Test
    fun manifestReihenfolge() {
        val klassen = Regex("android:name=\"\\.plattform\\.(\\w+)Widget\"").findAll(manifest.readText()).map { it.groupValues[1] }.toList()
        assertEquals(reihenfolge, klassen)
        // Erste drei: Stimmung, Durchbrueche, Fruehwarnung; Listen und Tabellen am Ende
        assertEquals(listOf("Stimmung", "Durchbrueche", "Fruehwarnung"), klassen.take(3))
        assertEquals("News", klassen.last())
    }

    @Test
    fun anlegbarFolgtDerReihenfolgeUndBleibtVollstaendig() {
        // Dieselbe Reihenfolge wie im Manifest (ohne Einzelwert, Top & Flop und Schnellzugriff, die keine Kategorie sind)
        val ohne = setOf("Einzelwert", "Mover", "Schnell")
        val erwartet = reihenfolge.filter { it !in ohne }.map { it.lowercase().let { k -> if (k == "vorschlagsdepot") "vorschlag" else if (k == "insidersimulator") "insidersim" else k } }
        assertEquals(erwartet, WidgetKarten.ANLEGBAR)
        assertEquals(WidgetKarten.ANLEGBAR.size, WidgetKarten.ANLEGBAR.toSet().size)
        val frueher = setOf(WidgetKarten.WERTE, WidgetKarten.NEWS, WidgetKarten.MAERKTE, WidgetKarten.HEATMAP, WidgetKarten.GLOBUS, WidgetKarten.STIMMUNG, WidgetKarten.DEPOT, WidgetKarten.DIVIDENDEN) + WidgetKarten.ARTEN
        assertEquals(frueher, WidgetKarten.ANLEGBAR.toSet())
    }
}
