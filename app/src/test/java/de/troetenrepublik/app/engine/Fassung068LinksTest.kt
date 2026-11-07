package de.troetenrepublik.app.engine

import de.troetenrepublik.app.ui.quellenText
import org.junit.Assert.*
import org.junit.Test

class Fassung068LinksTest {
    // Normale Webseiten zeigt die App selbst
    @Test
    fun webseitenImApp() {
        assertTrue(AppLinkRegel.imApp("https://www.nasdaq.com/market-activity/dividends"))
        assertTrue(AppLinkRegel.imApp("https://www.destatis.de/DE/Presse/x.html?a=1"))
    }

    // Kein Weblink oder Datei: extern
    @Test
    fun keinWebOderDatei() {
        assertFalse(AppLinkRegel.imApp("tel:0123"))
        assertFalse(AppLinkRegel.imApp("https://example.org/bericht.PDF?x=1"))
        assertFalse(AppLinkRegel.imApp("https://github.com/a/b/releases/download/v1/x.apk"))
    }

    // Ausgenommene Rechner oeffnen extern
    @Test
    fun ausgenommeneRechner() {
        assertFalse(AppLinkRegel.imApp("https://github.com/a/b"))
        assertFalse(AppLinkRegel.imApp("https://buymeacoffee.com/x"))
        assertFalse(AppLinkRegel.imApp("https://x.com/search?q=a"))
    }

    // Anmeldung und Schluessel oeffnen extern
    @Test
    fun anmeldungExtern() {
        assertFalse(AppLinkRegel.imApp("https://bsky.app/settings/app-passwords"))
        assertFalse(AppLinkRegel.imApp("https://finnhub.io/register"))
    }

    // Ein Wort vor dem Doppelpunkt wird zur Beschriftung
    @Test
    fun quellenTextBeschriftung() {
        assertEquals("Quelle · Nasdaq", quellenText("Quelle: Nasdaq"))
        assertEquals("Sources · SEC, BaFin", quellenText("Sources: SEC, BaFin"))
    }

    // Mehrere Woerter oder kein Doppelpunkt bleiben
    @Test
    fun quellenTextUnveraendert() {
        assertEquals("Optionskette von Yahoo: Stand", quellenText("Optionskette von Yahoo: Stand"))
        assertEquals("Kurz", quellenText("Kurz"))
    }
}
