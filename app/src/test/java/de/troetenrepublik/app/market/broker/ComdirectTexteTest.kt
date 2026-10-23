package de.troetenrepublik.app.market.broker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

// Antworten von comdirect lesen; Beispiele nach der Schnittstellenspezifikation.
class ComdirectTexteTest {
    @Test fun zeitstempelMitKommaUndKurzemVersatz() {
        val erwartet = OffsetDateTime.parse("2020-01-15T10:30:00.123456+01:00").toInstant().toEpochMilli()
        assertEquals(erwartet, ComdirectTexte.zeit("2020-01-15T10:30:00,123456+01"))
        assertEquals(erwartet, ComdirectTexte.zeit("2020-01-15T10:30:00.123456+01:00"))
        assertEquals(java.time.LocalDate.parse("2020-01-15").toEpochDay() * 86_400_000L, ComdirectTexte.zeit("2020-01-15"))
        assertNull(ComdirectTexte.zeit(""))
    }

    @Test fun challengeAusDemKopf() {
        val push = ComdirectTexte.challenge("""{"id":"7654321","typ":"P_TAN_PUSH","availableTypes":["P_TAN_PUSH","P_TAN","M_TAN"]}""")!!
        assertEquals("7654321", push.id)
        assertEquals("P_TAN_PUSH", push.verfahren)
        assertNull(push.bild)
        assertEquals(listOf("P_TAN_PUSH", "P_TAN", "M_TAN"), push.verfuegbar)
        val sms = ComdirectTexte.challenge("""{ "id":" 7654321 ", "typ":"M_TAN", "challenge":"+49-160-99XXXX", "availableTypes":["P_TAN","M_TAN"] }""")!!
        assertEquals("7654321", sms.id)
        assertEquals("+49-160-99XXXX", sms.text)
        val foto = ComdirectTexte.challenge("""{"id":"1","typ":"P_TAN","challenge":"iVBORw0KGgo=","availableTypes":["P_TAN"]}""")!!
        assertEquals("iVBORw0KGgo=", foto.bild)
        // Bei Orders mit Session-TAN
        assertEquals("TAN_FREI", ComdirectTexte.challenge("""{"id":"9","typ":"TAN_FREI","availableTypes":["M_TAN","P_TAN"]}""")!!.verfahren)
        assertNull(ComdirectTexte.challenge(null))
        assertNull(ComdirectTexte.challenge("""{"typ":"P_TAN"}"""))
    }
}
