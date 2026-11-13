package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Luecke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Aktuelle Insiderdaten (0.79): Rueckfall ueber die Meldungen der Nasdaq, Stand je Wert, tageweises Vorruecken im
// Tagesabruf der SEC und Ausuebungspreis aus Form 4. Die Antwort der Nasdaq ist eine gekuerzte echte Antwort mit
// erfundenen Namen.
class Fassung079InsiderStandTest {

    private val antwort = """
        {"data":{"title":null,"transactionTable":{"totalRecords":"298","table":{"asOf":null,"headers":{"insider":"Insider"},"rows":[
        {"insider":"MUSTER D","relation":"Director","lastDate":"11/04/2026","transactionType":"Automatic Sell","ownType":"Indirect","sharesTraded":"500,000","lastPrice":"${'$'}0.00","sharesHeld":"28,023,840","url":"/x/1"},
        {"insider":"MUSTER E","relation":"Officer","lastDate":"11/02/2026","transactionType":"Buy","ownType":"Direct","sharesTraded":"1,000","lastPrice":"${'$'}200.00","sharesHeld":"5,000","url":"/x/2"},
        {"insider":"MUSTER A","relation":"Officer","lastDate":"9/21/2026","transactionType":"Automatic Sell","ownType":"Indirect","sharesTraded":"30,460","lastPrice":"${'$'}222.19","sharesHeld":"2,687,660","url":"/x/3"},
        {"insider":"MUSTER B","relation":"Director","lastDate":"9/18/2026","transactionType":"Sell","ownType":"Indirect","sharesTraded":"1,366,000","lastPrice":"${'$'}219.72","sharesHeld":"15,988,281","url":"/x/4"},
        {"insider":"MUSTER B","relation":"Director","lastDate":"9/18/2026","transactionType":"Sell","ownType":"Direct","sharesTraded":"100,000","lastPrice":"${'$'}220.00","sharesHeld":"500,000","url":"/x/4"},
        {"insider":"MUSTER C","relation":"Officer","lastDate":"9/17/2026","transactionType":"Disposition (Non Open Market)","ownType":"Direct","sharesTraded":"45,728","lastPrice":"${'$'}212.17","sharesHeld":"70,100,666","url":"/x/5"}
        ]}}},"message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}
    """.trimIndent()

    @Test
    fun nurKaeufeUndVerkaeufeAnDerBoerseMitPreis() {
        val a = NasdaqInsider.lesen(antwort)!!
        // Zuteilung ausserhalb der Boerse und Zeile ohne Preis fallen weg
        assertEquals(listOf("MUSTER E", "MUSTER A", "MUSTER B", "MUSTER B"), a.zeilen.map { it.person })
        assertEquals(6, a.gelesen)
        assertEquals(298, a.gesamt)
        assertTrue(a.gekuerzt)
        assertEquals("2026-09-17", a.aeltester)
        assertTrue(a.zeilen[1].automatisch)
        assertEquals(30_460.0, a.zeilen[1].stueck, 0.0)
        assertEquals(222.19, a.zeilen[1].preis, 1e-9)
        assertNull(NasdaqInsider.lesen("{\"data\":null}"))
        assertNull(NasdaqInsider.lesen("kein json"))
    }

    @Test
    fun zeilenWerdenJePersonTagUndRichtungZusammengefasst() {
        val a = NasdaqInsider.lesen(antwort)!!
        val e = NasdaqInsider.auswerten(a, "mstr", "Muster AG", "123", "2026-09-01")
        assertEquals(listOf("2026-11-02", "2026-09-21", "2026-09-18"), e.geschaefte.map { it.d })
        val b = e.geschaefte.last()
        assertEquals("Muster B", b.p)
        assertEquals("MSTR", b.k)
        assertEquals("Muster AG", b.e)
        assertFalse(b.kauf)
        assertEquals("A", b.r)
        assertEquals(1_466_000.0, b.n, 0.0)
        assertEquals(100_000.0, b.nd!!, 0.0)
        assertEquals(500_000.0, b.nach!!, 0.0)
        assertEquals(1_366_000 * 219.72 + 100_000 * 220.0, b.wert, 0.01)
        assertEquals(b.wert / 1_466_000.0, b.preis, 1e-9)
        assertTrue(b.a.startsWith(NasdaqInsider.KENNUNG))
        // Plan und Rolle
        val plan = e.geschaefte[1]
        assertTrue(plan.plan)
        assertEquals("F", plan.r)
        val kauf = e.geschaefte.first()
        assertTrue(kauf.kauf)
        assertFalse(kauf.plan)
        // Antwort reicht nicht bis zum Beginn zurueck: Luecke von ab bis zur aeltesten Zeile
        assertEquals("2026-09-01", e.lueckeVon)
        assertEquals("2026-09-17", e.lueckeBis)
    }

    @Test
    fun nurTageNachDemStandUndKeineLueckeBeiVollerAntwort() {
        val a = NasdaqInsider.lesen(antwort)!!
        val e = NasdaqInsider.auswerten(a, "MSTR", "", "", "2026-09-30")
        assertEquals(listOf("2026-11-02"), e.geschaefte.map { it.d })
        // Firma ohne Namen: das Kuerzel
        assertEquals("MSTR", e.geschaefte.single().e)
        assertEquals("", e.lueckeVon)
        assertEquals("", e.lueckeBis)
    }

    @Test
    fun abTagLiegtDreiTageVorDemStand() {
        assertEquals("2026-09-27", NasdaqInsider.abTag("2026-09-30", null))
        assertEquals("2026-09-27", NasdaqInsider.abTag("2026-11-10", Luecke.Spanne("2026-09-30", "2026-11-10")))
        assertEquals("", NasdaqInsider.abTag("", null))
        assertEquals(
            "https://api.nasdaq.com/api/company/nvda/insider-trades?limit=100&type=ALL&sortColumn=lastDate&sortOrder=DESC",
            NasdaqInsider.adresse("NVDA"),
        )
        assertTrue(NasdaqInsider.abrufbar("NVDA"))
        assertFalse(NasdaqInsider.abrufbar("BRK-B"))
        assertFalse(NasdaqInsider.abrufbar("SAP.DE"))
    }

    private fun sec(a: String, p: String, d: String, kauf: Boolean) =
        SecInsider.Geschaeft(a = a, k = "MSTR", e = "Muster AG", p = p, kauf = kauf, d = d, f = d, n = 1_500_000.0, preis = 216.3, wert = 324_450_000.0, nach = 100.0)

    @Test
    fun meldungDerSecErsetztDieZeileDerNasdaq() {
        val nq = NasdaqInsider.auswerten(NasdaqInsider.lesen(antwort)!!, "MSTR", "Muster AG", "", "2026-09-01").geschaefte
        // dieselbe Person, derselbe Tag, dieselbe Richtung (Stueckzahl weicht ab, die SEC fasst anders zusammen)
        val vereint = SecInsider.vereinen(listOf(sec("0001-26-000001", "Muster B", "2026-09-18", false)), nq, "2026-01-01")
        assertEquals(1, vereint.count { it.p == "Muster B" })
        assertEquals("0001-26-000001", vereint.single { it.p == "Muster B" }.a)
        // andere Tage und Richtungen bleiben
        assertEquals(3, vereint.size)
        // Schluessel ueber Gross- und Kleinschreibung und Satzzeichen
        val punkt = SecInsider.vereinen(listOf(sec("0001-26-000002", "MUSTER b.", "2026-09-18", false)), nq, "2026-01-01")
        assertEquals(1, punkt.count { it.p.lowercase().startsWith("muster b") })
        // zweiter Abruf ersetzt die eigene Zeile statt sie zu verdoppeln
        assertEquals(nq.size, SecInsider.vereinen(nq, nq, "2026-01-01").size)
    }

    @Test
    fun standJeWertGiltNurFuerDiesenWert() {
        val d = SecInsider.Datei(
            stand = "2026-09-30", lueckeVon = "2026-09-30", lueckeBis = "2026-10-20",
            standJe = mapOf("MSTR" to SecInsider.Nq("2026-11-13"), "LUCK" to SecInsider.Nq("2026-11-13", "2026-09-27", "2026-10-05")),
        )
        val (stand, luecke) = d.standFuer("mstr")
        assertEquals("2026-11-13", stand)
        assertNull(luecke)
        // gekuerzte Antwort: Luecke bleibt sichtbar
        assertEquals(Luecke.Spanne("2026-09-27", "2026-10-05"), d.standFuer("LUCK").second)
        // anderer Wert: Gesamtstand samt Luecke
        val (s2, l2) = d.standFuer("ANDR")
        assertEquals("2026-09-30", s2)
        assertEquals(Luecke.Spanne("2026-09-30", "2026-10-20"), l2)
        // Stand je Wert aelter als der Gesamtstand zaehlt nicht
        assertEquals("2026-09-30", SecInsider.Datei(stand = "2026-09-30", standJe = mapOf("X" to SecInsider.Nq("2026-08-01"))).standFuer("X").first)
        // Speichern und Lesen behaelt den Stand je Wert
        val zurueck = SecInsider.lesen(SecInsider.schreiben(d))!!
        assertEquals(d.standJe, zurueck.standJe)
    }

    private fun tagesIndex(tag: String, zeilen: Int, gesucht: Int): List<Pair<String, String>> {
        val text = (0 until zeilen).joinToString("\n") { i ->
            val cik = if (i < gesucht) "1045810" else (2_000_000 + i).toString()
            val acc = "0001045810-26-" + (tag.takeLast(2) + i.toString().padStart(4, '0')).padStart(6, '0')
            "4                   Muster AG $i   $cik   2026-11-12   edgar/data/$cik/$acc.txt"
        }
        return SecInsider.tagesIndexLesen(text)
    }

    @Test
    fun tagesindexMitVielenZeilenBlockiertDenStandNicht() {
        val gesucht = setOf("1045810")
        val lauf = SecInsider.Vorlauf()
        // drei Tage mit je 1.722 Zeilen, davon fuenf des gesuchten Emittenten
        for (tag in listOf("2026-11-10", "2026-11-11", "2026-11-12")) {
            assertEquals(1_722, tagesIndex(tag, 1_722, 5).size)
            assertTrue(lauf.tag(tag, tagesIndex(tag, 1_722, 5), gesucht, emptySet()))
        }
        assertEquals(15, lauf.abrufen.size)
        assertEquals("2026-11-10", lauf.ab)
        assertEquals("2026-11-12", lauf.bis)
        assertTrue(lauf.abrufen.values.all { it == "1045810" })
    }

    @Test
    fun abdeckungRuecktTageweiseVorUndHaeltAnDerGrenze() {
        // 100 gesuchte Meldungen je Tag, Budget 150: der erste Tag passt, der zweite nicht mehr
        val lauf = SecInsider.Vorlauf(150)
        val gesucht = setOf("1045810")
        assertTrue(lauf.tag("2026-10-01", tagesIndex("2026-10-01", 1_722, 100), gesucht, emptySet()))
        assertFalse(lauf.tag("2026-10-02", tagesIndex("2026-10-02", 1_722, 100), gesucht, emptySet()))
        assertEquals("2026-10-01", lauf.ab)
        assertEquals("2026-10-01", lauf.bis)
        assertEquals(100, lauf.abrufen.size)
        // Auch ein einzelner Tag ueber dem Budget rueckt vor (sonst bliebe der Stand fuer immer stehen)
        val gross = SecInsider.Vorlauf(50)
        assertTrue(gross.tag("2026-10-01", tagesIndex("2026-10-01", 1_722, 100), gesucht, emptySet()))
        assertEquals("2026-10-01", gross.bis)
        // bekannte Meldungen zaehlen nicht
        val bekannt = tagesIndex("2026-10-01", 1_722, 100).map { it.first }.toSet()
        val leer = SecInsider.Vorlauf(150)
        assertTrue(leer.tag("2026-10-01", tagesIndex("2026-10-01", 1_722, 100), gesucht, bekannt))
        assertTrue(leer.abrufen.isEmpty())
    }

    @Test
    fun gesuchtSindKaeuferDerLetztenMonateUndDerGeoeffneteWert() {
        fun g(k: String, c: String, kauf: Boolean, d: String) = SecInsider.Geschaeft(a = k + d, k = k, e = k, c = c, p = "P", kauf = kauf, d = d, n = 1.0, preis = 1.0, wert = 1.0)
        val liste = listOf(
            g("AAA", "0000111", true, "2026-10-01"), g("BBB", "222", false, "2026-10-01"), g("CCC", "333", true, "2025-01-01"),
            g("DDD", "444", false, "2026-10-01"), g("EEE", "", true, "2026-10-01"),
        )
        assertEquals(setOf("111"), SecInsider.gesuchteCiks(liste, null, "2026-11-13"))
        assertEquals(setOf("111", "444"), SecInsider.gesuchteCiks(liste, "ddd", "2026-11-13"))
    }

    private fun form4(verkaufTag: String, ausuebungTag: String) = """
        <ownershipDocument><issuer><issuerCik>0000111</issuerCik><issuerName>Muster AG</issuerName><issuerTradingSymbol>MSTR</issuerTradingSymbol></issuer>
        <reportingOwner><reportingOwnerId><rptOwnerName>Muster A</rptOwnerName></reportingOwnerId>
        <reportingOwnerRelationship><isDirector>0</isDirector><isOfficer>1</isOfficer><officerTitle>Chief Financial Officer</officerTitle></reportingOwnerRelationship></reportingOwner>
        <nonDerivativeTable><nonDerivativeTransaction><transactionDate><value>$verkaufTag</value></transactionDate>
        <transactionCoding><transactionCode>S</transactionCode></transactionCoding>
        <transactionAmounts><transactionShares><value>1000</value></transactionShares><transactionPricePerShare><value>50.00</value></transactionPricePerShare></transactionAmounts>
        <postTransactionAmounts><sharesOwnedFollowingTransaction><value>4000</value></sharesOwnedFollowingTransaction></postTransactionAmounts>
        <ownershipNature><directOrIndirectOwnership><value>D</value></directOrIndirectOwnership></ownershipNature></nonDerivativeTransaction></nonDerivativeTable>
        <derivativeTable><derivativeTransaction><conversionOrExercisePrice><value>20.00</value></conversionOrExercisePrice>
        <transactionDate><value>$ausuebungTag</value></transactionDate><transactionCoding><transactionCode>M</transactionCode></transactionCoding>
        <transactionAmounts><transactionShares><value>1000</value></transactionShares></transactionAmounts></derivativeTransaction></derivativeTable></ownershipDocument>
    """.trimIndent()

    @Test
    fun ausuebungspreisAmTagDesVerkaufsWirdGelesen() {
        val g = SecInsider.form4(form4("2026-09-18", "2026-09-18"), "0001-26-000009").single()
        assertEquals(20.0, g.ap!!, 1e-9)
        assertEquals(50.0, g.preis, 1e-9)
        assertEquals(4_000.0, g.nach!!, 0.0)
        // Ausuebung an einem anderen Tag gilt nicht als Beleg fuer diesen Verkauf
        assertNull(SecInsider.form4(form4("2026-09-18", "2026-09-10"), "0001-26-000010").single().ap)
        // Speichern und Lesen
        val zurueck = SecInsider.lesen(SecInsider.schreiben(SecInsider.Datei(geschaefte = listOf(g))))!!
        assertNotNull(zurueck.geschaefte.single().ap)
    }
}
