package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 5.95: Titel, Zeilenzusammenfuehrung und Inhalt der Lizenzsammlung.
class Fassung595LizenzenTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private fun wurzel() = listOf("../THIRD_PARTY_LICENSES.txt", "THIRD_PARTY_LICENSES.txt").map { File(it) }.first { it.exists() }

    @Test
    fun titelErkennen() {
        val absaetze = Lizenztext.absaetze(
            "==========\nApache License 2.0\n==========\n\nKurzer Einleitungstext.\n\nAndroid app\n-----------\nInhalt des Abschnitts.\n"
        )
        assertEquals(4, absaetze.size)
        assertTrue(absaetze[0].titel)
        assertEquals("Apache License 2.0", absaetze[0].text)
        assertFalse(absaetze[1].titel)
        assertTrue(absaetze[2].titel)
        assertEquals("Android app", absaetze[2].text)
        assertFalse(absaetze[3].titel)
    }

    @Test
    fun unterstreichungTrenntBlock() {
        val absaetze = Lizenztext.absaetze(
            "Vor der Zwischenueberschrift steht noch ein Satz mit etwas mehr Laenge.\n" +
                "1. Definitions\n" +
                "--------------\n" +
                "Contributor means each individual or legal entity that creates a Contribution.\n"
        )
        assertEquals(3, absaetze.size)
        assertFalse(absaetze[0].titel)
        assertTrue(absaetze[1].titel)
        assertEquals("1. Definitions", absaetze[1].text)
        assertFalse(absaetze[2].titel)
    }

    @Test
    fun zeilenVerbinden() {
        val absatz = Lizenztext.absaetze(
            "Tryfolio enthaelt Bibliotheken anderer Projekte, die unter eigenen\n" +
                "Lizenzen stehen; diese Datei gibt die Lizenztexte wieder und dient\n" +
                "der Anzeige in der App."
        ).single()
        assertFalse(absatz.titel)
        assertFalse(absatz.text.contains("\n"))

        val kurz = Lizenztext.absaetze(
            "Copyright (c) 2011 Google Inc.\nCopyright (c) 2010, Google Inc."
        ).single()
        assertEquals(2, kurz.text.split("\n").size)

        val liste = Lizenztext.absaetze(
            "Diese Aufzaehlung hat eine lange Einleitungszeile mit mehr als fuenfzig Zeichen.\n" +
                "1. Erster Punkt\n" +
                "Diese Zeile ist ebenfalls lang genug, um weiterem Text voranzugehen hier.\n" +
                "- Stichpunkt\n" +
                "Copyright (c) Beispiel"
        ).single()
        assertEquals(5, liste.text.split("\n").size)
    }

    @Test
    fun crlfWieLf() {
        val lf = "Eine Zeile mit deutlich mehr als fuenfzig Zeichen fuer den Zeilenzusammenhang.\n" +
            "Noch eine Zeile mit deutlich mehr als fuenfzig Zeichen fuer den Anschluss.\n"
        assertEquals(Lizenztext.absaetze(lf), Lizenztext.absaetze(lf.replace("\n", "\r\n")))
    }

    @Test
    fun sammeldatei() {
        val datei = asset(Lizenztext.DATEI)
        assertTrue(datei.readBytes().contentEquals(wurzel().readBytes()))
        val text = datei.readText()
        for (nadel in listOf(
            "Version 2.0, January 2004",
            "Mozilla Public License Version 2.0",
            "Jonathan Hedley",
            "Marshall A. Greenblatt",
            "Independent JPEG Group",
            "The FreeType Project",
            "UNICODE LICENSE V3",
            "PNG Reference Library License version 2",
            "Jean-loup Gailly",
            "JogAmp Community",
            "Apache Commons Compress",
            "publicsuffix",
            "Stephen Colebourne",
            "This product includes DNG technology under license by Adobe Systems",
            "https://android.googlesource.com/platform/frameworks/base",
            "Copyright 2009-2024 JogAmp Community",
            "Copyright 2010 - 2019 JogAmp Community",
            "Sun Microsystems",
            "LWJGL",
            "Brian Paul",
            "SGI Free Software License B",
            "Typecast",
            "PNGJ",
            "Apache Avro"
        )) {
            assertTrue(text.contains(nadel))
        }
        assertFalse(text.contains("BSD 4-Clause"))
        assertTrue(text.contains("CLASSPATH EXCEPTION", ignoreCase = true))
        assertFalse(text.lines().any { zeile -> zeile.any { c -> c.code == 9 } })
        val absaetze = Lizenztext.absaetze(text)
        assertTrue(absaetze.size > 50)
        assertTrue(absaetze.count { it.titel } >= 15)
    }

    // Runde 3: Apache 1.1 von Typecast in der Batik-Fassung und SGI-Vermerk der GLU-Dateien
    @Test
    fun typecastUndSgi() {
        val text = asset(Lizenztext.DATEI).readText()
        val flach = text.replace(Regex("\\s+"), " ")
        val typecast = text.indexOf("Typecast (Apache License 1.1)\n-----------------------------")
        assertTrue(typecast >= 0)
        val block = text.substring(typecast)
        assertTrue(block.contains("The Apache Software License, Version 1.1"))
        assertTrue(block.contains("4. The names \"Batik\" and  \"Apache Software Foundation\" must  not  be"))
        assertTrue(block.contains("Apache Software Foundation, please see <http://www.apache.org/>."))
        assertTrue(text.indexOf("Typecast (Apache License 1.1)") > text.indexOf("A.3) The JOGL source tree"))
        assertTrue(text.indexOf("Typecast (Apache License 1.1)") < text.indexOf("A.6) The JOGL source tree"))
        val sgi = "The Original Code is: OpenGL Sample Implementation, Version 1.2.1, released January 26, 2000, " +
            "developed by Silicon Graphics, Inc. The Original Code is Copyright (c) 1991-2000 Silicon Graphics, Inc. " +
            "Copyright in any portions created by third parties is as indicated elsewhere herein. All Rights Reserved."
        assertTrue(flach.contains(sgi))
        assertTrue(flach.indexOf(sgi) > flach.indexOf("L.3) The JOGL source tree"))
        assertTrue(flach.indexOf(sgi) < flach.indexOf("L.4) The JOGL source tree"))
    }
}
