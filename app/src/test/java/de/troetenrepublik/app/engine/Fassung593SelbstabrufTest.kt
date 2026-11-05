package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.RegisterImportWelt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93: Dateien des Selbstabrufs. passt/stabil fuer die Ordner-Beobachtung des Desktops, dateiname und
// dataUrl fuer den Download des App-Browsers, downloadOrdnerAusXdg fuer den lokalisieren Downloads-Ordner und
// die Quellenliste ohne NGX.
class Fassung593SelbstabrufTest {

    @Test fun dateiPasstZurQuelle() {
        assertTrue(SelbstabrufDatei.passt("PncPubbl.xlsx", listOf("xlsx"), "pncpubbl"))
        assertTrue(SelbstabrufDatei.passt("pncpubbl20261026.xlsx", listOf("xlsx"), "pncpubbl"))
        assertFalse(SelbstabrufDatei.passt("bericht.pdf", listOf("xlsx"), "pncpubbl"))
        // Teildateien des Browsers und versteckte Dateien zaehlen nie
        assertFalse(SelbstabrufDatei.passt("x.pdf.crdownload", listOf("pdf"), ""))
        assertFalse(SelbstabrufDatei.passt("x.pdf.part", listOf("pdf"), ""))
        assertFalse(SelbstabrufDatei.passt("a.tmp", listOf("pdf", "tmp"), ""))
        assertFalse(SelbstabrufDatei.passt(".hidden.pdf", listOf("pdf"), ""))
        assertFalse(SelbstabrufDatei.passt("ohnepunkt", listOf("pdf"), ""))
        // Leeres Muster nimmt jeden Namen mit passender Endung, Grossbuchstaben zaehlen mit
        assertTrue(SelbstabrufDatei.passt("Bericht.PDF", listOf("pdf"), ""))
        assertTrue(SelbstabrufDatei.passt("seite.html", listOf("html", "htm"), ""))
        // Muster muss im Namen stecken
        assertFalse(SelbstabrufDatei.passt("andere.xlsx", listOf("xlsx"), "pncpubbl"))
    }

    @Test fun groesseStabil() {
        assertFalse(SelbstabrufDatei.stabil(null, 5L))
        assertTrue(SelbstabrufDatei.stabil(5L, 5L))
        assertFalse(SelbstabrufDatei.stabil(5L, 6L))
        assertFalse(SelbstabrufDatei.stabil(0L, 0L))
    }

    @Test fun dateinameAusDispositionOderUrl() {
        assertEquals("PncPubbl.xlsx", SelbstabrufDatei.dateiname("attachment; filename=\"PncPubbl.xlsx\"", "https://x.y/f"))
        assertEquals("Bericht ä.pdf", SelbstabrufDatei.dateiname("attachment; filename*=UTF-8''Bericht%20%C3%A4.pdf", "https://x.y/f"))
        assertEquals("Bericht ä.pdf", SelbstabrufDatei.dateiname("attachment; filename*=utf-8''Bericht%20%C3%A4.pdf", "https://x.y/f"))
        // Der einfache filename= endet vor Semikolon und Anfuehrungszeichen
        assertEquals("a", SelbstabrufDatei.dateiname("attachment; filename=\"a;b.pdf\"", "https://x.y/f"))
        assertEquals("datei.pdf", SelbstabrufDatei.dateiname(null, "https://x.y/a/b/datei.pdf?x=1"))
        assertEquals("datei.pdf", SelbstabrufDatei.dateiname(null, "https://x.y/a/b/datei.pdf#anker"))
        assertEquals("download", SelbstabrufDatei.dateiname(null, "blob:https://x/123"))
        assertEquals("download", SelbstabrufDatei.dateiname(null, "data:text/plain,x"))
        assertEquals("download", SelbstabrufDatei.dateiname("attachment", "https://x.y/"))
        assertEquals("download", SelbstabrufDatei.dateiname("   ", "https://x.y/"))
        // Lange Namen werden auf 120 Zeichen begrenzt
        assertEquals(120, SelbstabrufDatei.dateiname("attachment; filename=\"" + "l".repeat(200) + ".pdf\"", "https://x.y/").length)
    }

    @Test fun dataAdresseDekodieren() {
        assertEquals("Hallo", String(SelbstabrufDatei.dataUrl("data:text/plain;base64,SGFsbG8=")!!))
        assertEquals("a,b", String(SelbstabrufDatei.dataUrl("data:text/csv,a%2Cb")!!))
        // Ein Plus im nicht-base64-Teil bleibt Plus
        assertEquals("a+b", String(SelbstabrufDatei.dataUrl("data:text/csv,a+b")!!))
        assertNull(SelbstabrufDatei.dataUrl("data:text/plain"))
        assertNull(SelbstabrufDatei.dataUrl("data:text/plain;base64,!!!"))
        assertNull(SelbstabrufDatei.dataUrl("https://x.y/f"))
    }

    @Test fun downloadsOrdnerAusXdg() {
        assertEquals("/home/u/Herunterladen", SelbstabrufDatei.downloadOrdnerAusXdg("XDG_DOWNLOAD_DIR=\"\$HOME/Herunterladen\"\n", "/home/u"))
        assertEquals("/home/u/Downloads", SelbstabrufDatei.downloadOrdnerAusXdg("XDG_DOWNLOAD_DIR=/home/u/Downloads\n", "/home/u"))
        assertNull(SelbstabrufDatei.downloadOrdnerAusXdg("XDG_DESKTOP_DIR=\"\$HOME/Schreibtisch\"\n", "/home/u"))
        assertNull(SelbstabrufDatei.downloadOrdnerAusXdg("", "/home/u"))
    }

    @Test fun quellenOhneNgx() {
        assertEquals(12, RegisterImportWelt.QUELLEN.size)
        assertTrue(RegisterImportWelt.QUELLEN.none { it.id == "ngx" })
        assertEquals(RegisterImportWelt.QUELLEN.size, RegisterImportWelt.QUELLEN.map { it.id }.distinct().size)
        assertTrue(RegisterImportWelt.QUELLEN.all { it.endungen.isNotEmpty() })
        val consob = RegisterImportWelt.QUELLEN.first { it.id == "consob" }
        assertEquals(listOf("xlsx"), consob.endungen)
        assertEquals("pncpubbl", consob.muster)
        assertTrue("html" in RegisterImportWelt.QUELLEN.first { it.id == "kw" }.endungen)
        assertEquals("", RegisterImportWelt.QUELLEN.first { it.id == "qse" }.url)
        assertEquals(RegisterImportWelt.CONSOB_DATEI, consob.url)
        assertEquals("leerverkaeufe", RegisterImportWelt.QUELLEN.first { it.id == "bund" }.muster)
        assertEquals(listOf("pdf"), RegisterImportWelt.QUELLEN.first { it.id == "mt" }.endungen)
    }
}
