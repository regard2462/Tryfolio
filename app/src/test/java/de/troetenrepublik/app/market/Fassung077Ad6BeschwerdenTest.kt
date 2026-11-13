package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.77, Teil AD6: Link auf die Beschwerdedatenbank der CFPB.
class Fassung077Ad6BeschwerdenTest {

    @Test fun adresseMitGeglaettetemNamen() {
        assertEquals(
            "https://www.consumerfinance.gov/data-research/consumer-complaints/search/?searchField=company&searchText=JPMORGAN+CHASE&from=0&size=25",
            Verbraucherbeschwerden.adresse("JPMorgan Chase & Co."),
        )
        assertEquals(
            "https://www.consumerfinance.gov/data-research/consumer-complaints/search/?searchField=company&searchText=WELLS+FARGO&from=0&size=25",
            Verbraucherbeschwerden.adresse("Wells Fargo & Company"),
        )
        assertNull(Verbraucherbeschwerden.adresse("  "))
    }

    @Test fun nurPassendeBranchen() {
        assertTrue(Verbraucherbeschwerden.betroffen("Banks - Diversified"))
        assertTrue(Verbraucherbeschwerden.betroffen("Credit Services"))
        assertTrue(Verbraucherbeschwerden.betroffen("Mortgage Finance"))
        assertFalse(Verbraucherbeschwerden.betroffen("Auto Manufacturers"))
        assertFalse(Verbraucherbeschwerden.betroffen("Insurance - Life"))
    }
}
