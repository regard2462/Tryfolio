package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Zweitnotiz
import de.troetenrepublik.app.market.Zweitnotiz.Kandidat
import org.junit.Assert.assertEquals
import org.junit.Test

// Zuordnung der deutschen Zweitnotiz: ein wenig gehandelter Platz (Hannover, Hamburg) bleibt nicht 30 Tage.
// Beispiel SK Hynix (000660.KS): HY9H.HA hat vier Umsaetze am Tag, HY9H.F Hunderte.
class Fassung077S2ZweitnotizTest {
    private val tag = 86_400_000L

    @Test fun frankfurtGilt30TageJederAndereEinen() {
        assertEquals(30 * tag, Zweitnotiz.fristFuer("HY9H.F"))
        assertEquals(tag, Zweitnotiz.fristFuer("HY9H.HA"))
        assertEquals(tag, Zweitnotiz.fristFuer("HY9H.HM"))
        assertEquals(tag, Zweitnotiz.fristFuer("HY9H.SG"))
        assertEquals(tag, Zweitnotiz.fristFuer(""))
    }

    @Test fun neusucheOhneTrefferBehaeltDieAlteZuordnung() {
        assertEquals("HY9H.HA", Zweitnotiz.beibehalten("HY9H.HA", ""))
        assertEquals("HY9H.F", Zweitnotiz.beibehalten("HY9H.HA", "HY9H.F"))
        assertEquals("", Zweitnotiz.beibehalten("", ""))
    }

    // Lookup vom 11.11.2026 (Preise in EUR, gekuerzt): bei einem Heimatkurs von 868 EUR (1.277.000 KRW) passt Frankfurt.
    private val treffer = listOf(
        Kandidat("HY9H.F", "FRA", 878.0), Kandidat("HY9H.SG", "STU", 878.0), Kandidat("HY9H.MU", "MUN", 888.0),
        Kandidat("HY9H.DU", "DUS", 884.0), Kandidat("HY9H.HM", "HAM", 844.0), Kandidat("HY9H.HA", "HAN", 870.0),
        Kandidat("HY9.F", "FRA", 130.0),
    )

    @Test fun frankfurtVorHannoverWennBeidePassen() {
        assertEquals("HY9H.F", Zweitnotiz.waehleNotierung(treffer, 868.0))
    }

    @Test fun passtFrankfurtNichtFolgtDerNaechstePlatz() {
        // Frankfurt (878) und Stuttgart (878) weichen mehr als 3 % ab, Hannover (870) nicht
        val l = listOf(Kandidat("HY9H.F", "FRA", 878.0), Kandidat("HY9H.SG", "STU", 878.0), Kandidat("HY9H.HA", "HAN", 870.0))
        assertEquals("HY9H.HA", Zweitnotiz.waehleNotierung(l, 845.0))
        // am naechsten Tag mit passendem Heimatkurs wird die Zuordnung auf Frankfurt verbessert
        assertEquals("HY9H.F", Zweitnotiz.beibehalten("HY9H.HA", Zweitnotiz.waehleNotierung(l, 868.0).orEmpty()))
    }
}
