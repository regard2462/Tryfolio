package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.engine.Alter.Zustand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

// Ehrliches Altern: Schwellen je Profil, gealterte Inhalte (Durchbrueche, Laenderdaten, Strukturen, IPO), Luecken der
// Momentaufnahmen, Manifest passt zu den eingebauten Dateien, jede gepflegte Datei hat ein Profil.
class AlterungBausteinTest {

    @After fun zurueck() { Uhr.vorstellenTage(0) }

    private fun tag(s: String): Long = LocalDate.parse(s).toEpochDay() * 86_400_000L + 3_600_000L
    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    // ------------------------------------------------------------ Schwellen

    @Test fun schwellenJeProfil() {
        val p = Alter.LAGEBILD // 9 und 24 Monate
        assertEquals(Zustand.FRISCH, Alter.zustand("2026-11-09", p, tag("2027-08-08")))
        assertEquals(Zustand.ALT, Alter.zustand("2026-11-09", p, tag("2027-08-09")))
        assertEquals(Zustand.ALT, Alter.zustand("2026-11-09", p, tag("2028-11-08")))
        assertEquals(Zustand.VERALTET, Alter.zustand("2026-11-09", p, tag("2028-11-09")))
        assertTrue(Alter.ausgeblendet("2026-11-09", p, tag("2029-01-01")))
        // Ohne zweite Schwelle wird nie ausgeblendet
        assertEquals(Zustand.ALT, Alter.zustand("2026-11-09", Alter.REKORD, tag("2031-11-09")))
        assertFalse(Alter.ausgeblendet("2020-01-01", Alter.REKORD, tag("2031-11-09")))
        assertFalse(Alter.ausgeblendet("2020-01-01", Alter.LAENDER_PERSONEN, tag("2031-11-09")))
        // Unlesbarer Stand: alt, aber nie ausgeblendet
        assertEquals(Zustand.ALT, Alter.zustand("", p, tag("2027-01-01")))
        assertFalse(Alter.ausgeblendet("kaputt", p, tag("2031-01-01")))
        // Stand in der Zukunft (verstellte Uhr): frisch
        assertEquals(Zustand.FRISCH, Alter.zustand("2030-01-01", p, tag("2026-11-09")))
        assertEquals("11/2026", Alter.marke("2026-11-09"))
        // Ungueltige Tage (31. Februar) sind kein Datum und bringen nichts zum Absturz
        assertNull(Alter.tag("2026-02-31"))
        assertEquals(Zustand.ALT, Alter.zustand("2026-02-31", p, tag("2027-01-01")))
        assertEquals("", Alter.marke("x"))
        assertEquals("03/2027", Alter.marke("2027-03"))
    }

    @Test fun jedeDateiHatEinProfil() {
        Datenbuendel.SCHEMA.keys.forEach { assertTrue(it, it in Alter.dateien()) }
        // Das Profil kann nie fehlen
        assertEquals(Alter.STANDARD, Alter.profilFuer("unbekannt.json"))
    }

    // ------------------------------------------------------------ Manifest und gepflegte Dateien

    @Test fun manifestPasstZuDenEingebautenDateien() {
        // Schlaegt das fehl, wurde eine gepflegte Datei geaendert: python tools/daten/manifest.py bauen, dann signieren
        // (tools/quellen/signieren.py daten-signieren) und daten-pruefen.
        val m = Datenbuendel.manifestLesen(asset("daten/manifest.json").readText(Charsets.UTF_8))
        assertNotNull(m)
        m!!
        val sha = MessageDigest.getInstance("SHA-256")
        for ((name, d) in m.dateien) {
            val b = asset(name).readBytes()
            assertEquals("Pruefsumme $name (manifest.py bauen)", d.sha256, Datenbuendel.hexVon(sha.digest(b)))
            assertEquals(d.bytes, b.size.toLong())
            assertTrue("Stand $name", Alter.tag(d.stand) != null)
            assertTrue("Schema $name", d.schema <= (Datenbuendel.SCHEMA[name] ?: 0))
            // Die Datei im Archiv ist dieselbe
            val im = listOf(File("../daten/$name"), File("daten/$name")).first { it.exists() }
            assertEquals("Archiv $name", d.sha256, Datenbuendel.hexVon(sha.digest(im.readBytes())))
        }
        assertEquals(Datenbuendel.SCHEMA.keys, m.dateien.keys)
    }

    @Test fun jedeGepflegteDateiMitStandIstImManifestOderBewusstAusgenommen() {
        // Dateien mit einem obersten Feld "stand" in assets/: gepflegt, also im Manifest, ausser den bekannten Ausnahmen
        // (Momentaufnahmen mit eigenem Weg, grosse Behoerdendaten, Indikatoren).
        val ausnahmen = setOf(
            "copytrading/regierung.json", "copytrading/haus.json", "copytrading/senat.json", "copytrading/sec.json", "copytrading/bafin.json",
            "copytrading/kongress.json", "copytrading/papier.json", "copytrading/praesident.json", "copytrading/vermoegen.json", "copytrading/verlauf.json",
            "copytrading/bilder.json", "copytrading/insider_fr.json", "copytrading/insider_nl.json", "copytrading/insider_se.json",
            "copytrading/leerpositionen_fr.json", "copytrading/leerpositionen_se.json",
            "behoerden/lobbying.json", "behoerden/lobby_welt.json", "behoerden/entlassungen.json",
            "globus/laender.json", "indikatoren/allgemein.json", "indikatoren/finder_rueckblick.json", "indikatoren/tendenz_rueckblick.json", "indizes/sp500_mitglieder.json",
            // Mittelwerte aus Studien- und Kursdaten (0.78), eingebaut, nicht ueber das Datenbuendel nachgeliefert
            "indikatoren/anomalien.json", "indikatoren/halbjahr.json",
            "indikatoren/finder_rueckblick_ndx100.json", "indizes/ndx100_mitglieder.json",
            // Erklaerungen der Lage: eingebaut, nicht ueber das Datenbuendel nachgeliefert
            "welt/erklaerungen.json",
            "daten/manifest.json",
        )
        val wurzel = listOf(File("src/main/assets"), File("../app/src/main/assets")).first { it.exists() }
        val m = Datenbuendel.manifestLesen(File(wurzel, "daten/manifest.json").readText(Charsets.UTF_8))!!
        val stand = Regex("^\\{\\s*\"(stand|Stand)\"\\s*:\\s*\"\\d{4}-\\d{2}")
        val fehlend = wurzel.walkTopDown().filter { it.isFile && it.extension == "json" && !it.path.replace('\\', '/').contains("/finanzwissen/") && !it.path.replace('\\', '/').contains("/anleitung/") }
            .filter { f -> f.inputStream().use { s -> val k = ByteArray(80); val n = s.read(k); String(k, 0, maxOf(n, 0)) }.let { stand.containsMatchIn(it) } }
            .map { it.relativeTo(wurzel).path.replace('\\', '/') }
            .filter { it !in m.dateien.keys && it !in ausnahmen }
            .toList()
        assertTrue("Gepflegte Dateien mit Stand ohne Eintrag im Manifest und ohne Ausnahme: $fehlend", fehlend.isEmpty())
    }

    @Test fun nachDerSchwelleIstJederGepflegteInhaltNichtMehrFrisch() {
        val m = Datenbuendel.manifestLesen(asset("daten/manifest.json").readText(Charsets.UTF_8))!!
        for ((name, d) in m.dateien) {
            val p = Alter.profilFuer(name)
            val spaeter = LocalDate.parse(d.stand).plusMonths(p.warnenMonate.toLong() + 1).toEpochDay() * 86_400_000L
            assertTrue(name, Alter.zustand(d.stand, p, spaeter) != Zustand.FRISCH)
        }
    }

    // ------------------------------------------------------------ Durchbrueche

    private val inhalt = Durchbrueche.lesenInhalt(
        """{"v":2,"stand":"2026-11-09","themen":[{"id":"krebs","e":[],"w":[{"s":"Phase 3","se":"Phase 3","de":"A","en":"A"},{"s":"Stand 05.08.2026","de":"B","en":"B"}],"r":[{"j":"2011","de":"R","en":"R"}]}]}""",
    )!!

    @Test fun lagebilderAltern() {
        val t = inhalt.themen.single()
        assertEquals(2, Durchbrueche.gealtert(t, inhalt.stand, tag("2027-06-01")).w.size)
        assertEquals("Phase 3", Durchbrueche.gealtert(t, inhalt.stand, tag("2027-06-01")).w[0].s)
        // Nach neun Monaten steht der Stand an der Marke, nach 24 Monaten fallen sie weg; Rueckblick bleibt
        val alt = Durchbrueche.gealtert(t, inhalt.stand, tag("2027-09-01"))
        assertEquals("Phase 3 · Stand 11/2026", alt.w[0].s)
        assertEquals("Phase 3 · as of 11/2026", alt.w[0].se)
        assertEquals("Stand 05.08.2026", alt.w[1].s)
        val weg = Durchbrueche.gealtert(t, inhalt.stand, tag("2029-01-01"))
        assertTrue(weg.w.isEmpty())
        assertEquals(1, weg.r.size)
    }

    @Test fun ansichtNachEinemDreiUndFuenfJahrenOhneAbsturz() {
        for (j in listOf(1, 3, 5)) {
            val jetzt = tag("2026-11-10") + 365L * j * 86_400_000L
            val b = Durchbrueche.ansicht(inhalt, emptyList(), jetzt)
            // Rueckblick bleibt, deshalb bleibt das Thema; die Lagebilder sind nach drei Jahren weg
            assertEquals(1, b.size)
            if (j >= 3) assertTrue(b.single().inhalt!!.w.isEmpty())
            assertEquals(1, b.single().inhalt!!.r.size)
        }
    }

    // ------------------------------------------------------------ Laenderdaten und Strukturen

    private val ld = LaenderDaten.lesen("""{"stand":"2026-11-06","l":{"DE":{"h":"Berlin","w":"Euro","s":"A","r":"B","f":"PARL_REP+B","m":"EU"}}}""", true)!!

    @Test fun personenMitStandNachSechsMonaten() {
        assertEquals("A", LaenderDaten.gealtert(ld, true, tag("2027-04-30")).laender.getValue("DE").oberhaupt)
        val alt = LaenderDaten.gealtert(ld, true, tag("2027-05-06")).laender.getValue("DE")
        assertEquals("A (Stand 11/2026)", alt.oberhaupt)
        assertEquals("B (Stand 11/2026)", alt.regierungschef)
        assertEquals("Berlin", alt.hauptstadt)
        assertEquals("A (as of 11/2026)", LaenderDaten.gealtert(ld, false, tag("2029-01-01")).laender.getValue("DE").oberhaupt)
        // Ohne Stand nichts
        val ohne = LaenderDaten.lesen("""{"l":{"DE":{"s":"A"}}}""", true)!!
        assertEquals("A", LaenderDaten.gealtert(ohne, true, tag("2031-01-01")).laender.getValue("DE").oberhaupt)
    }

    @Test fun strukturenAltern() {
        val s = LaenderStrukturen.lesen("""{"v":1,"l":{"ETH":[{"t":{"de":"T","en":"T"},"d":{"de":"Text","en":"Text"}}]}}""")!!
        fun d(x: LaenderStrukturen.Stand) = x.l.getValue("ETH").single().d
        assertEquals("Text", d(LaenderStrukturen.gealtert(s, "2026-11-09", tag("2027-10-01"))).de)
        assertEquals("Text (Stand 11/2026)", d(LaenderStrukturen.gealtert(s, "2026-11-09", tag("2027-11-10"))).de)
        assertEquals("Text (as of 11/2026)", d(LaenderStrukturen.gealtert(s, "2026-11-09", tag("2027-11-10"))).en)
        assertEquals("Veraltet (Stand 11/2026): Text", d(LaenderStrukturen.gealtert(s, "2026-11-09", tag("2029-11-10"))).de)
        // Kein lesbarer Stand: unveraendert
        assertEquals("Text", d(LaenderStrukturen.gealtert(s, "", tag("2031-01-01"))).de)
    }

    // ------------------------------------------------------------ Katalog

    @Test fun bevorstehendeBoersengaengeVerschwinden() {
        // Nur die Regel: Katalogstand 2026-09-24, ab sechs Monaten faellt die Liste weg
        assertFalse(Alter.ausgeblendet("2026-09-24", Alter.IPO_BEVORSTEHEND, tag("2027-03-23")))
        assertTrue(Alter.ausgeblendet("2026-09-24", Alter.IPO_BEVORSTEHEND, tag("2027-03-24")))
        assertEquals(Zustand.ALT, Alter.zustand("2026-09-24", Alter.IPO_BEVORSTEHEND, tag("2026-12-24")))
    }

    // ------------------------------------------------------------ Gesundheit der Inhalte

    @Test fun inhalteMitZaehler() {
        de.troetenrepublik.app.market.Quellen.fuerTest(null)
        val q = de.troetenrepublik.app.market.Quellen
        assertNull(q.lesen<String>("wikipedia", "wahlen") { null })
        assertEquals(1, q.gesundheit.value.getValue("wikipedia").fehler)
        assertEquals(0L, q.inhalte.value.getValue("wahlen").gueltig)
        assertEquals(1, q.inhalte.value.getValue("wahlen").inFolge)
        // Eine leere Liste trotz Antwort zaehlt als Formaenderung
        assertTrue(q.lesen("wikipedia", "wahlen") { emptyList<String>() }!!.isEmpty())
        assertEquals(2, q.gesundheit.value.getValue("wikipedia").fehler)
        assertEquals(2, q.inhalte.value.getValue("wahlen").inFolge)
        // Ein lesbares Ergebnis merkt den Zeitpunkt
        assertEquals(listOf(1), q.lesen("wikipedia", "wahlen") { listOf(1) })
        val s = q.inhalte.value.getValue("wahlen")
        assertTrue(s.gueltig > 0)
        assertEquals(0, s.inFolge)
        assertEquals(2, q.gesundheit.value.getValue("wikipedia").fehler)
        // Ein Fehler beim Lesen selbst (Ausnahme) zaehlt wie null
        assertNull(q.lesen<String>("wikipedia", "wahlen") { error("kaputt") })
        assertEquals(3, q.gesundheit.value.getValue("wikipedia").fehler)
        // Speichern und Lesen
        val text = Quellenbuendel.inhalteText(q.inhalte.value)
        assertEquals(q.inhalte.value, Quellenbuendel.inhalteLesen(text))
        assertTrue(Quellenbuendel.inhalteLesen("unsinn\nx\ty").isEmpty())
        q.fuerTest(null)
    }

    // ------------------------------------------------------------ Luecken

    @Test fun lueckeEntstehtUndSchliesstSich() {
        // Stand 28.10.2026, erster Abruf am 03.04.2028 holt nur den letzten Monat (ab 03.03.2028)
        val (stand, luecke) = Luecke.nachAbruf("2026-10-28", null, "2028-03-03", "2028-04-03")
        assertEquals("2028-04-03", stand)
        assertEquals(Luecke.Spanne("2026-10-28", "2028-03-03"), luecke)
        // Naechster Abruf, laeuft stetig weiter: die Luecke bleibt
        val (s2, l2) = Luecke.nachAbruf(stand, luecke, "2028-04-01", "2028-04-04")
        assertEquals("2028-04-04", s2)
        assertEquals(luecke, l2)
        // Ein Abruf, der bis vor die Luecke zurueckreicht, schliesst sie
        assertNull(Luecke.nachAbruf(s2, l2, "2026-10-25", "2028-04-05").second)
        // Stetig angeschlossen: keine Luecke
        assertNull(Luecke.nachAbruf("2026-10-28", null, "2026-10-26", "2026-11-10").second)
        assertNull(Luecke.nachAbruf("2026-10-28", null, "2026-11-01", "2026-11-10").second)
        // Ohne lesbaren Stand ist keine Luecke zu beziffern
        assertNull(Luecke.nachAbruf("", null, "2028-03-03", "2028-04-03").second)
    }

    @Test fun abrufBeginnUndAnzeige() {
        assertEquals("2026-11-07", Luecke.abrufBeginn("2026-11-10", "2026-11-12", 60, 30))
        assertEquals("2026-09-13", Luecke.abrufBeginn("2026-01-01", "2026-11-12", 60, 30))
        assertEquals("2026-10-13", Luecke.abrufBeginn("", "2026-11-12", 60, 30))
        val a = Luecke.anzeige("2028-04-03", Luecke.Spanne("2026-10-28", "2028-03-03"))!!
        assertEquals("2028-04-03", a.bis)
        assertNotNull(a.luecke)
        assertNull(Luecke.anzeige("kaputt", null))
        assertNull(Luecke.anzeige("2026-10-28", Luecke.Spanne("", ""))!!.luecke)
    }

    @Test fun fensterFaelleNachEinemDreiUndFuenfJahren() {
        // SEC (550 Tage), BaFin (400 Tage) und EU (60 Tage): der Stand der Pakete liegt nach der Zeit ausserhalb des Fensters, die
        // Luecke aber wird angezeigt statt einer stillen leeren Seite
        for (j in listOf(1, 3, 5)) {
            Uhr.vorstellenTage(365L * j)
            val heute = Uhr.heute()
            val abSec = heute.minusDays(550).toString()
            val (_, l) = Luecke.nachAbruf("2026-09-30", null, Luecke.abrufBeginn("2026-09-30", heute.toString(), 60, 30), heute.toString())
            if (abSec > "2026-09-30") assertNotNull("Luecke nach $j Jahren", l)
            assertNotNull(Luecke.anzeige("2026-09-30", l))
        }
    }
}
