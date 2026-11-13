package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung078U6SelbstabrufTest {

    // Die Browser-Seiten aus „Selbst anstoßen“ stehen unter „Nur zum Ansehen“, mit Namen und Inhalt in beiden Sprachen.
    @Test fun browserSeitenStehenUnterNurAnsehen() {
        val neu = listOf("lme", "comex", "fbx", "baltic", "finra")
        val liste = NUR_ANSEHEN.associateBy { it.id }
        assertEquals(18, NUR_ANSEHEN.size)
        assertEquals(NUR_ANSEHEN.size, liste.size)
        assertEquals(Anstossen.LME_BERICHT, liste.getValue("lme").url)
        assertEquals(Anstossen.FBX, liste.getValue("fbx").url)
        assertEquals(Anstossen.BALTIC, liste.getValue("baltic").url)
        assertEquals(Anstossen.FINRA_MARGIN, liste.getValue("finra").url)
        // Lager, Fracht und Margin oeffnen wie bisher im Standardbrowser
        assertTrue(neu.all { liste.getValue(it).extern })
        assertTrue(NUR_ANSEHEN.filter { it.id !in neu }.none { it.extern })
        for (sprache in listOf(Lang.DE, Lang.EN)) {
            I18n.lang = sprache
            for (q in NUR_ANSEHEN) {
                assertTrue(q.id, S.saAnsehenName(q.id) != q.id)
                assertTrue(q.id, S.saAnsehenInhalt(q.id).isNotBlank())
            }
        }
        I18n.lang = Lang.DE
    }

    // In „Selbst anstoßen“ bleibt in der Gruppe Browser die eine Zeile zum Selbstabruf.
    @Test fun selbstAnstossenBehaeltEineZeile() {
        assertEquals(listOf(Anstossen.Eintrag.SELBSTABRUF), Anstossen.eintraege(Anstossen.Gruppe.BROWSER))
    }
}
