package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Dividenden wiederanlegen (5.92): Nettobetrag nach Steuer ohne Ordergebuehr in Bruchstuecke derselben Aktie, Kurs
// und Spread wie beim Sparplan, eigene Buchung, je Position abweichend von der Einstellung.
class Fassung592WiederanlageTest {
    private val tag = 86_400_000L
    private val kauf = 1_760_000_000_000L
    private val ex = kauf + 30 * tag
    private val jetzt = ex + 5 * tag

    private fun depot(): Account {
        val a = Trading.deposit(Account(), 10_000.0, kauf - tag)
        return Trading.buy(a, "SAP.DE", "SAP", "SAP.DE", 10.0, 200.0, now = kauf).account
    }

    private fun kurse(preis: Double = 250.0, frisch: Boolean = true) =
        mapOf("SAP.DE" to Quote("SAP.DE", price = preis, updatedAt = if (frisch) jetzt - 60_000L else jetzt - 3_600_000L))

    private val dividende = listOf(Trading.Dividend("SAP.DE", ex, 2.0))

    @Test fun ohneEinstellungBleibtEsGuthaben() {
        val a = Trading.bookDividends(depot(), dividende, jetzt)
        val b = Trading.dividendenWiederanlegen(a, kurse(), jetzt, Trading.Spread(), global = false)
        assertEquals(a, b)
        assertEquals("", a.transactions.single { it.type == TxType.DIVIDEND }.via)
    }

    @Test fun nettoBetragWirdInBruchstueckeAngelegt() {
        val vorher = depot()
        val a = Trading.bookDividends(vorher, dividende, jetzt, wiederanlageGlobal = true)
        val div = a.transactions.single { it.type == TxType.DIVIDEND }
        assertEquals(Trading.WIEDERANLAGE, div.via)
        // 10 Stueck zu 2 € brutto, Abgeltungsteuer ohne Freistellungsauftrag
        assertEquals(20.0, div.amount + div.tax, 1e-9)
        assertTrue(div.tax > 0)
        val b = Trading.dividendenWiederanlegen(a, kurse(), jetzt, Trading.Spread(stockBps = 20.0), global = true)
        val kaufBuchung = b.transactions.single { it.type == TxType.BUY && it.via == Trading.VIA_DIVIDENDE }
        assertEquals(Trading.wiederanlageId(div.id), kaufBuchung.id)
        // Briefkurs wie beim Sparplan, keine Gebuehr
        assertEquals(Pricing.ask(250.0, 20.0), kaufBuchung.price, 1e-9)
        assertEquals(0.0, kaufBuchung.fee, 0.0)
        assertEquals(div.amount / kaufBuchung.price, kaufBuchung.qty, 1e-9)
        assertTrue(b.transactions.none { it.type == TxType.FEE && it.ts == jetzt })
        // Das Guthaben ist wieder wie vor der Dividende, die Position um die Bruchstuecke groesser
        assertEquals(vorher.cash, b.cash, 1e-6)
        assertEquals(10.0 + kaufBuchung.qty, b.positions.single().qty, 1e-9)
        // Ein zweiter Lauf legt nichts doppelt an
        assertEquals(b, Trading.dividendenWiederanlegen(b, kurse(), jetzt + 60_000L, Trading.Spread(), global = true))
    }

    @Test fun eigeneWahlDerPositionGehtVor() {
        val aus = depot().copy(wiederanlage = mapOf("SAP.DE" to false))
        val a = Trading.bookDividends(aus, dividende, jetzt, wiederanlageGlobal = true)
        assertEquals("", a.transactions.single { it.type == TxType.DIVIDEND }.via)

        val an = depot().copy(wiederanlage = mapOf("SAP.DE" to true))
        val b = Trading.bookDividends(an, dividende, jetzt, wiederanlageGlobal = false)
        val c = Trading.dividendenWiederanlegen(b, kurse(), jetzt, Trading.Spread(), global = false)
        assertEquals(1, c.transactions.count { it.via == Trading.VIA_DIVIDENDE })
        assertFalse(Trading.wiederanlegen(aus, "SAP.DE", global = true))
        assertTrue(Trading.wiederanlegen(Account(), "SAP.DE", global = true))
    }

    @Test fun ohneFrischenKursWartetDieDividende() {
        val a = Trading.bookDividends(depot(), dividende, jetzt, wiederanlageGlobal = true)
        assertEquals(a, Trading.dividendenWiederanlegen(a, kurse(frisch = false), jetzt, Trading.Spread(), global = true))
        // Spaeter mit frischem Kurs wird sie angelegt, nach der Frist nicht mehr
        val spaeter = jetzt + 2 * tag
        val frisch = mapOf("SAP.DE" to Quote("SAP.DE", price = 240.0, updatedAt = spaeter))
        assertEquals(1, Trading.dividendenWiederanlegen(a, frisch, spaeter, Trading.Spread(), global = true).transactions.count { it.via == Trading.VIA_DIVIDENDE })
        val zuSpaet = ex + 3 * tag + Trading.WIEDERANLAGE_FRIST_MS + tag
        val alt = mapOf("SAP.DE" to Quote("SAP.DE", price = 240.0, updatedAt = zuSpaet))
        assertEquals(a, Trading.dividendenWiederanlegen(a, alt, zuSpaet, Trading.Spread(), global = true))
    }

    @Test fun verkauftOderAusgeschaltetBleibtGuthaben() {
        val a = Trading.bookDividends(depot(), dividende, jetzt, wiederanlageGlobal = true)
        // Inzwischen ausgeschaltet
        assertEquals(a, Trading.dividendenWiederanlegen(a, kurse(), jetzt, Trading.Spread(), global = false))
        // Position verkauft
        val verkauft = Trading.sell(a, "SAP.DE", 10.0, 250.0, now = jetzt - 1000L).account
        assertEquals(verkauft, Trading.dividendenWiederanlegen(verkauft, kurse(), jetzt, Trading.Spread(), global = true))
    }

    @Test fun hoechstensDasVorhandeneGuthaben() {
        val a = Trading.bookDividends(depot(), dividende, jetzt, wiederanlageGlobal = true)
        val wenig = a.copy(cash = 5.0)
        val b = Trading.dividendenWiederanlegen(wenig, kurse(), jetzt, Trading.Spread(), global = true)
        val k = b.transactions.single { it.via == Trading.VIA_DIVIDENDE }
        assertEquals(5.0, -k.amount, 1e-9)
        assertEquals(0.0, b.cash, 1e-9)
    }
}
