package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.data.WidgetWert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.4: Kursalarme auf die Tagesbewegung, Notizen je Wert, Reihenfolge der Widget-Listen.
class Fassung54Test {
    // Mittwoch, 21.10.2026, 14 Uhr in Berlin
    private val now = java.time.ZonedDateTime.of(2026, 10, 21, 14, 0, 0, 0, MarketHours.zone).toInstant().toEpochMilli()
    private val tag = 86_400_000L

    private fun quote(preis: Double, vortag: Double = 100.0, handel: Long = now - 60_000L, abruf: Long = now) =
        mapOf("SAP.DE" to Quote("SAP.DE", "SAP", price = preis, prevClose = vortag, updatedAt = abruf, marketTime = handel))

    private val bewegung = PriceAlert("b", "SAP.DE", "SAP", 0.0, above = true, createdAt = now - tag, tagesProzent = 5.0)

    @Test fun bewegungMeldetAbSchwelleNachObenUndUnten() {
        val acc = Account(alerts = listOf(bewegung))
        assertTrue(Trading.checkAlerts(acc, quote(104.0), now).second.isEmpty())
        val (hoch, treffer) = Trading.checkAlerts(acc, quote(105.5), now)
        assertEquals(1, treffer.size)
        assertEquals("ALERT_MOVE", hoch.transactions.last().tag)
        assertEquals(5.5, hoch.transactions.last().extra, 1e-9)
        assertEquals(1, Trading.checkAlerts(acc, quote(94.0), now).second.size)
    }

    @Test fun bewegungMeldetEinmalJeTagUndBleibt() {
        val (nach, _) = Trading.checkAlerts(Account(alerts = listOf(bewegung)), quote(107.0), now)
        assertEquals(1, nach.alerts.size)
        assertEquals(Trading.handelstag(now), nach.alerts.single().gemeldetAm)
        // Am selben Tag nicht noch einmal
        val spaeter = now + 3_600_000L
        assertTrue(Trading.checkAlerts(nach, quote(110.0, handel = spaeter - 60_000L, abruf = spaeter), spaeter).second.isEmpty())
        // Am naechsten Handelstag wieder
        val morgen = now + tag
        assertEquals(1, Trading.checkAlerts(nach, quote(93.0, handel = morgen - 60_000L, abruf = morgen), morgen).second.size)
    }

    @Test fun bewegungDesVortagsZaehltAmMorgenNicht() {
        // Morgens vor Handelsbeginn traegt der Kurs noch die Bewegung von gestern
        val morgen = now + tag
        val alt = quote(108.0, handel = now, abruf = morgen)
        assertTrue(Trading.checkAlerts(Account(alerts = listOf(bewegung)), alt, morgen).second.isEmpty())
    }

    @Test fun kursmarkeVerschwindetWeiterhin() {
        val marke = PriceAlert("m", "SAP.DE", "SAP", 105.0, above = true, createdAt = now)
        val (nach, treffer) = Trading.checkAlerts(Account(alerts = listOf(marke, bewegung)), quote(106.0), now)
        assertEquals(2, treffer.size)
        assertEquals(listOf("b"), nach.alerts.map { it.id })
    }

    @Test fun notizenBleibenBeimZusammenfuehren() {
        val alt = Account(notizen = mapOf("SAP.DE" to "Langfristig", "AAPL" to "Alt"))
        val neu = Account(notizen = mapOf("AAPL" to "Neu", "MSFT" to "Cloud"))
        val beide = Zusammenfuehren.konten(alt, neu)
        assertEquals(mapOf("SAP.DE" to "Langfristig", "AAPL" to "Alt", "MSFT" to "Cloud"), beide.notizen)
    }

    @Test fun widgetListeNachTagesaenderung() {
        val werte = listOf(
            WidgetWert("A", "", "", true, pct = 1.0),
            WidgetWert("B", "", "", false, pct = -3.0),
            WidgetWert("C", "", "", true, pct = 4.0),
        )
        assertEquals(listOf("A", "B", "C"), WidgetDaten.ordnen(werte, false).map { it.name })
        assertEquals(listOf("C", "A", "B"), WidgetDaten.ordnen(werte, true).map { it.name })
    }
}
