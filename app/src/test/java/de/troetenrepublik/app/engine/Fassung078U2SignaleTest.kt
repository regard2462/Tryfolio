package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Handelnde
import de.troetenrepublik.app.market.Kongress
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 0.78: Signale-Zeilen, Blaetter, Waage und Widget rechnen mit einem Fenster und einer Zaehlung; die Waage zaehlt
// einen Anlass einmal und nimmt fuer Leerverkaeufe die Stufe der Leerverkaufszeile.
class Fassung078U2SignaleTest {

    private val tag = 86_400_000L
    private val heute = LocalDate.of(2026, 11, 12)

    @Test fun fensterEndetAmDatenstand() {
        val f = Signale.fenster("2026-11-06", heute)
        assertEquals("2026-11-06", f.bis)
        assertEquals("2026-08-08", f.ab)
        assertEquals(LocalDate.of(2026, 8, 8).toEpochDay() * tag, f.abMs)
        assertEquals((LocalDate.of(2026, 11, 6).toEpochDay() + 1) * tag - 1, f.bisMs)
        // Ohne Daten, mit unlesbarem Stand oder mit einem Stand in der Zukunft endet das Fenster heute
        assertEquals("2026-11-12", Signale.fenster(null, heute).bis)
        assertEquals("2026-11-12", Signale.fenster("unbekannt", heute).bis)
        assertEquals("2026-11-12", Signale.fenster("2026-12-01", heute).bis)
        // Dasselbe Fenster wie bei der Konvergenz
        val k = Konvergenz.fenster(LocalDate.of(2026, 11, 6))
        assertEquals(k.ab, f.ab)
        assertEquals(k.bisMs, f.bisMs)
    }

    @Test fun datenEnde() {
        assertEquals("2026-11-06", Signale.datenEnde("2026-11-06", "2026-11-01", "2026-11-12"))
        assertEquals("2026-11-01", Signale.datenEnde("", "2026-11-01", "2026-11-12"))
        assertEquals("2026-11-12", Signale.datenEnde("", null, "2026-11-12"))
        assertEquals("2026-11-12", Signale.datenEnde("2026-12-01", null, "2026-11-12"))
    }

    @Test fun insiderFenster() {
        assertEquals(90, Signale.INSIDER_TAGE)
        assertEquals("2026-08-08", Signale.insiderAb("2026-11-06", heute))
        assertEquals("2026-08-14", Signale.insiderAb(null, heute))
        assertNull(Signale.insider(null, null, null, null, heute))
        assertNull(Signale.insider(emptyList(), emptyList(), emptyList(), "2026-11-06", heute))
    }

    private fun geschaeft(kauf: Boolean, handel: Long, meldung: Long) = Kongress.Geschaeft(
        inhaber = "", wert = "", kuerzel = "AAA", typ = "ST", art = if (kauf) Kongress.Art.KAUF else Kongress.Art.VERKAUF,
        datum = handel, gemeldet = meldung, von = 1_000, bis = 15_000,
    )

    @Test fun kongressNachMeldetag() {
        val ende = LocalDate.of(2026, 11, 6)
        val f = Signale.fenster(ende.toString(), heute)
        fun t(d: LocalDate) = d.toEpochDay() * tag
        val haus = Handelnde.Eintrag(
            Handelnde.Gruppe.HAUS, "h", "n",
            geschaefte = listOf(
                // Handel vor dem Fenster, Meldung im Fenster: zaehlt
                geschaeft(true, t(ende.minusDays(120)), t(ende.minusDays(80))),
                // Handel und Meldung im Fenster: zaehlt
                geschaeft(false, t(ende.minusDays(30)), t(ende.minusDays(10))),
                // Meldung nach dem Ende der Insider-Daten: zaehlt nicht
                geschaeft(true, t(ende.minusDays(20)), t(ende.plusDays(3))),
                // Meldung vor dem Fenster: zaehlt nicht
                geschaeft(true, t(ende.minusDays(130)), t(ende.minusDays(100))),
            ),
        )
        val senat = Handelnde.Eintrag(Handelnde.Gruppe.SENAT, "s", "n", geschaefte = listOf(geschaeft(true, t(ende.minusDays(5)), t(ende))))
        val regierung = Handelnde.Eintrag(Handelnde.Gruppe.REGIERUNG, "r", "n", geschaefte = listOf(geschaeft(true, t(ende.minusDays(5)), t(ende))))
        assertEquals(Signale.Kongress(2, 1), Signale.kongress(listOf(haus, senat, regierung), f))
        val liste = Signale.kongressGeschaefte(listOf(haus, senat, regierung), f)
        assertEquals(3, liste.size)
        assertEquals(t(ende), liste.first().oeffentlich)
        assertNull(Signale.kongress(listOf(regierung), f))
    }

    private fun kennzahlen(f: BullBear.Fakten) = BullBear.auswerten(f, emptyList(), 0L).mapNotNull { it.kennzahl }

    @Test fun leerverkaufNachStufeDerZeile() {
        val hoch = BullBear.Kennzahl.LEERVERKAUF_HOCH
        // US-Werte: Anteil am Streubesitz, „viele“ ab 10 %
        assertFalse(hoch in kennzahlen(BullBear.Fakten(leerverkauf = 9.9, leerUs = true)))
        assertTrue(hoch in kennzahlen(BullBear.Fakten(leerverkauf = 10.0, leerUs = true)))
        // Uebrige Werte: gemeldete Positionen am Grundkapital, „viele“ ab 3 %
        assertFalse(hoch in kennzahlen(BullBear.Fakten(leerverkauf = 2.9)))
        assertTrue(hoch in kennzahlen(BullBear.Fakten(leerverkauf = 3.0)))
        val p = BullBear.auswerten(BullBear.Fakten(leerverkauf = 12.0, leerUs = true), emptyList(), 0L).first { it.kennzahl == hoch }
        assertEquals(listOf(12.0, 1.0), p.werte)
        assertEquals(listOf(4.0, 0.0), BullBear.auswerten(BullBear.Fakten(leerverkauf = 4.0), emptyList(), 0L).first { it.kennzahl == hoch }.werte)
        // Der Text nennt die Bezugsgroesse
        listOf(de.troetenrepublik.app.i18n.De, de.troetenrepublik.app.i18n.En).forEach { s ->
            assertTrue(s.bbKennzahl("LEERVERKAUF_HOCH", listOf("12 %", "US")) != s.bbKennzahl("LEERVERKAUF_HOCH", listOf("12 %", "")))
        }
        assertTrue(de.troetenrepublik.app.i18n.De.bbKennzahl("LEERVERKAUF_HOCH", listOf("12 %", "US")).contains("Streubesitz"))
        assertTrue(de.troetenrepublik.app.i18n.De.bbKennzahl("LEERVERKAUF_HOCH", listOf("4 %", "")).contains("Grundkapital"))
    }

    // Erste Schlagzeile der Auswahl, die das Thema trifft.
    private fun titel(t: BullBear.Thema, vararg auswahl: String) = auswahl.first { t in BullBear.themen(it) }

    @Test fun anlassZaehltEinmal() {
        val jetzt = 1_000L * tag
        fun s(titel: String) = BullBear.Schlagzeile(titel, jetzt - tag, titel)
        val auf = s(titel(BullBear.Thema.AUFSTUFUNG, "Goldman upgrades Example to buy", "Analyst upgrades Example", "Example auf Kaufen hochgestuft"))
        val zul = s(titel(BullBear.Thema.ZULASSUNG, "FDA approves Example drug", "Example wins FDA approval", "Example erhält Zulassung der FDA"))
        val news = listOf(auf, zul)
        fun themen(f: BullBear.Fakten) = BullBear.auswerten(f, news, jetzt).mapNotNull { it.thema }.toSet()

        // Ohne Kennzahl bleiben die Schlagzeilen-Themen
        assertTrue(BullBear.Thema.AUFSTUFUNG in themen(BullBear.Fakten()))
        assertTrue(BullBear.Thema.ZULASSUNG in themen(BullBear.Fakten()))
        // Gezaehlte Stufungen ersetzen die Schlagzeilen dazu, auch bei Gleichstand
        assertFalse(BullBear.Thema.AUFSTUFUNG in themen(BullBear.Fakten(stufen = 2 to 0)))
        assertFalse(BullBear.Thema.AUFSTUFUNG in themen(BullBear.Fakten(stufen = 1 to 1)))
        assertTrue(BullBear.Thema.ZULASSUNG in themen(BullBear.Fakten(stufen = 2 to 0)))
        // Zulassungen aus dem Register ersetzen die Schlagzeile; null Zulassungen nicht
        assertFalse(BullBear.Thema.ZULASSUNG in themen(BullBear.Fakten(fdaZulassungen = 1)))
        assertTrue(BullBear.Thema.ZULASSUNG in themen(BullBear.Fakten(fdaZulassungen = 0)))
    }

    @Test fun doppelteThemen() {
        assertEquals(emptySet<BullBear.Thema>(), BullBear.doppelteThemen(emptySet(), BullBear.Fakten()))
        assertEquals(setOf(BullBear.Thema.RUECKRUF), BullBear.doppelteThemen(setOf(BullBear.Kennzahl.FDA_RUECKRUF), BullBear.Fakten()))
        assertEquals(setOf(BullBear.Thema.ZAHLEN), BullBear.doppelteThemen(setOf(BullBear.Kennzahl.UEBERTROFFEN), BullBear.Fakten()))
        assertEquals(emptySet<BullBear.Thema>(), BullBear.doppelteThemen(setOf(BullBear.Kennzahl.VERFEHLT), BullBear.Fakten()))
        // Warnzeichen decken nur die Themen ihrer Art
        assertEquals(setOf(BullBear.Thema.BILANZ), BullBear.warnThemen(listOf("KORREKTUR")))
        assertEquals(setOf(BullBear.Thema.PLEITE), BullBear.warnThemen(listOf("DELISTING")))
        assertEquals(setOf(BullBear.Thema.BILANZ, BullBear.Thema.PLEITE), BullBear.warnThemen(listOf("KORREKTUR", "INSOLVENZ", "ANDERES")))
        assertEquals(emptySet<BullBear.Thema>(), BullBear.warnThemen(emptyList()))
        val f = BullBear.Fakten(warnThemen = setOf(BullBear.Thema.BILANZ))
        assertEquals(setOf(BullBear.Thema.BILANZ), BullBear.doppelteThemen(setOf(BullBear.Kennzahl.WARNZEICHEN), f))
        assertEquals(emptySet<BullBear.Thema>(), BullBear.doppelteThemen(emptySet(), f))
    }

    @Test fun texteNennenFenster() {
        listOf(de.troetenrepublik.app.i18n.De, de.troetenrepublik.app.i18n.En).forEach { s ->
            assertTrue(s.itClusterKurz.contains("30"))
            assertTrue(s.bbKennzahl("INSIDER_KAUFEN", listOf("+60")).contains("90"))
            assertTrue(s.afFeld("INSIDER").contains("180"))
            assertTrue(s.kvFensterBis(90, "06.11.2026").contains("90") && s.kvFensterBis(90, "06.11.2026").contains("06.11.2026"))
            assertTrue(s.itTitelTage(90).contains("90"))
        }
    }
}
