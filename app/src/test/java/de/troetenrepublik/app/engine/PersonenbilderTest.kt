package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Personenbilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Zuordnung der Profilbilder: Wahlkreis vor Bundesstaat, Rufname, eindeutiger Nachname.
class PersonenbilderTest {

    private val b = Personenbilder(
        kongressFoto = "https://bilder.example/{id}.jpg",
        haus = mapOf("XY03|muster" to "M000003", "XY|muster" to "M000003", "XY07|beispiel" to "B000007", "XY|beispiel" to "B000007"),
        senat = mapOf("probe|thomas" to "P000001", "probe|tom" to "P000001", "probe" to "P000001"),
        personen = mapOf("anleger" to Personenbilder.Foto(url = "https://bilder.example/a.jpg", autor = "Jemand", lizenz = "CC BY 2.0"), "leer" to Personenbilder.Foto()),
    )

    @Test fun abgeordneteNachWahlkreisUndNachname() {
        assertEquals("https://bilder.example/M000003.jpg", b.haus("Muster", "XY03")?.url)
        assertEquals("https://bilder.example/M000003.jpg", b.haus("Müster", "XY")?.url)
        assertEquals("https://bilder.example/B000007.jpg", b.haus("Beispiel", "XY7")?.url)
        assertNull(b.haus("Muster", "ZZ03"))
    }

    @Test fun senatorenUndPersonen() {
        assertEquals("https://bilder.example/P000001.jpg", b.senator("Probe", "Tom H")?.url)
        assertEquals("https://bilder.example/P000001.jpg", b.senator("Probe", "Unbekannt")?.url)
        assertEquals("CC BY 2.0", b.person("anleger")?.lizenz)
        assertNull(b.person("leer"))
        assertEquals(true, b.haus("Muster", "XY03")?.kongress)
    }

    @Test fun mitgelieferteFotosGehenVor() {
        val m = Personenbilder(personen = mapOf("anleger" to Personenbilder.Foto(url = "https://bilder.example/a.jpg", lokal = "copytrading/bilder/anleger.jpg", lizenz = "CC0")))
        assertEquals("asset:copytrading/bilder/anleger.jpg", m.person("anleger")?.adresse)
        assertEquals("https://bilder.example/a.jpg", b.person("anleger")?.adresse)
    }
}
