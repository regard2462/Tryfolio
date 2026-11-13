package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.UkRegisterTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Register der Interessen des britischen Unterhauses (0.78). Der Ausschnitt unter resources/ukregister hat den Aufbau
// der Schnittstelle; Personen und Firmen sind erfunden.
class Fassung078U36UkRegisterTest {

    private val seite: UkRegister.Seite by lazy {
        UkRegister.seiteLesen(javaClass.classLoader!!.getResourceAsStream("ukregister/seite.json")!!.readBytes().toString(Charsets.UTF_8))!!
    }

    @Test
    fun seiteLesen() {
        assertEquals(46, seite.gesamt)
        assertEquals(6, seite.gelesen)
        // Der Eintrag ohne Namen der Person faellt weg
        assertEquals(listOf(9001L, 9002L, 9003L, 9004L, 9005L), seite.eintraege.map { it.id })
        val a = seite.eintraege[0]
        assertEquals("Ada Beispiel", a.person)
        assertEquals("Labour", a.partei)
        assertEquals("Nordlicht Energy plc", a.firma)
        assertEquals(701L, a.personId)
        assertNull(a.schwelleProzent)
        assertEquals(70_000L, a.schwellePfund)
        assertEquals("2026-10-19", a.gemeldet)
        assertEquals("2026-10-16", a.seit)
        assertTrue(a.aktuell)
        val b = seite.eintraege[1]
        assertEquals(15, b.schwelleProzent)
        assertNull(b.schwellePfund)
        // Leerraum im Namen zusammengefasst, Ende gesetzt
        val c = seite.eintraege[2]
        assertEquals("Nordlicht Energy PLC", c.firma)
        assertEquals("2026-09-30", c.ende)
        assertFalse(c.aktuell)
        // Ohne Meldedatum gilt der Tag der Veroeffentlichung, ohne Firmenfeld die Zusammenfassung
        assertEquals("2026-05-02", seite.eintraege[3].gemeldet)
        val e = seite.eintraege[4]
        assertEquals("Kiesel & Sohn Limited", e.firma)
        assertNull(e.schwelleProzent)
        assertNull(e.schwellePfund)
    }

    @Test
    fun unlesbareAntwort() {
        assertNull(UkRegister.seiteLesen("kein json"))
        assertNull(UkRegister.seiteLesen("{\"items\":[]}"))
        assertEquals(UkRegister.Seite(0, 0, emptyList()), UkRegister.seiteLesen("{\"totalResults\":0,\"items\":[]}"))
    }

    @Test
    fun adressen() {
        assertEquals("https://interests-api.parliament.uk/api/v1/Interests?CategoryId=8&Take=20&Skip=40", UkRegister.adresse(40))
        assertEquals("https://members.parliament.uk/member/701/registeredinterests", UkRegister.personSeite(701))
    }

    @Test
    fun sucheUndZaehlung() {
        val l = seite.eintraege
        assertEquals(5, UkRegister.suche(l, " ").size)
        assertEquals(listOf(9002L, 9004L), UkRegister.suche(l, "bruno nordlicht").map { it.id })
        assertEquals(listOf(9005L), UkRegister.suche(l, "KIESEL").map { it.id })
        assertTrue(UkRegister.suche(l, "gibtesnicht").isEmpty())
        // Drei Personen mit bestehender Beteiligung (eine mit zwei Eintraegen, eine nur mit beendeter)
        assertEquals(3, UkRegister.personen(l))
        assertEquals(listOf(9001L, 9002L, 9003L, 9004L, 9005L), UkRegister.sortiert(l.reversed()).map { it.id })
    }

    @Test
    fun firmenSchluessel() {
        assertEquals("nordlicht energy" to "plc", UkRegister.firmenSchluessel("The Nordlicht Energy p.l.c."))
        assertEquals("ionq" to "inc", UkRegister.firmenSchluessel("IonQ, Inc."))
        assertEquals("muster holdings" to "ltd", UkRegister.firmenSchluessel("Muster Holdings Co. Ltd"))
        assertEquals("kiesel and sohn" to "ltd", UkRegister.firmenSchluessel("Kiesel & Sohn Limited"))
        assertEquals("muster group" to "plc", UkRegister.firmenSchluessel("Muster Group Public Limited Company"))
        // Ohne Rechtsform oder mit zu kurzem Kern keine Zuordnung
        assertNull(UkRegister.firmenSchluessel("Nordlicht Energy"))
        assertNull(UkRegister.firmenSchluessel("AB plc"))
        assertNull(UkRegister.firmenSchluessel(""))
    }

    @Test
    fun zuordnungZurAktie() {
        val l = seite.eintraege
        // Gleicher Name und gleiche Rechtsform; die Ltd gleichen Namens und die beendete Beteiligung zaehlen nicht
        assertEquals(listOf(9001L, 9004L), UkRegister.zuFirma(l, "Nordlicht Energy PLC").map { it.id })
        assertEquals(listOf(9002L), UkRegister.zuFirma(l, "Nordlicht Energy Limited").map { it.id })
        assertEquals(listOf(9005L), UkRegister.zuFirma(l, "Kiesel and Sohn Ltd.").map { it.id })
        assertTrue(UkRegister.zuFirma(l, "Nordlicht Energy").isEmpty())
        assertTrue(UkRegister.zuFirma(l, "Nordlicht Energy Inc").isEmpty())
    }

    @Test
    fun schwelleAusDemWortlaut() {
        assertEquals(null to 70_000L, UkRegister.schwelle("(ii) Other shareholdings, valued at more than £70,000"))
        assertEquals(15 to null, UkRegister.schwelle("(i) Shareholdings: over 15% of issued share capital"))
        assertEquals(null to null, UkRegister.schwelle(""))
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(UkRegisterTexte.schluessel(true), UkRegisterTexte.schluessel(false))
        for (k in UkRegisterTexte.schluessel(true)) {
            assertTrue(k, UkRegisterTexte.text(true, k).isNotBlank() && UkRegisterTexte.text(false, k).isNotBlank())
        }
    }
}
