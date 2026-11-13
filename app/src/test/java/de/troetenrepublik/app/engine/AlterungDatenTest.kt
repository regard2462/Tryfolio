package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Daten
import de.troetenrepublik.app.market.Quellen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate

// Datenbuendel: Signaturkette (Hauptschluessel, Zertifikat, Datenschluessel, Manifest), Pruefsumme, Schema, Mindestfassung,
// Probelesen, abgelaufenes Zertifikat, fehlendes Netz, Rueckruf, und die Zeitreise um ein, drei und fuenf Jahre.
class AlterungDatenTest {

    private val sha512: (ByteArray) -> ByteArray = { MessageDigest.getInstance("SHA-512").digest(it) }
    private fun sha256(b: ByteArray) = Datenbuendel.hexVon(MessageDigest.getInstance("SHA-256").digest(b))
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun sig(seed: ByteArray, text: String) = hex(Ed25519.signieren(seed, text.toByteArray(), sha512))

    // Testschluessel: nie die echten
    private val hauptSeed = ByteArray(32) { 1 }
    private val datenSeed = ByteArray(32) { 2 }
    private val datenSeed2 = ByteArray(32) { 3 }
    private val fremdSeed = ByteArray(32) { 9 }
    private val hauptPub = Ed25519.oeffentlich(hauptSeed, sha512)

    private lateinit var ordner: File
    private val assets = HashMap<String, ByteArray>()

    private val NAME = "steuerwerte.json"
    private val ALT = """{"v":1,"stand":"2026-11-09","jahre":{"2026":{"pauschbetrag":1000,"pauschbetragPaar":2000,"basiszins":3.2}}}"""
    private val NEU = """{"v":1,"stand":"2027-02-01","unbekanntesFeld":true,"jahre":{"2026":{"pauschbetrag":1000,"pauschbetragPaar":2000,"basiszins":3.2},"2027":{"pauschbetrag":1100,"pauschbetragPaar":2200,"basiszins":2.9}}}"""

    @Before fun vorbereiten() {
        ordner = Files.createTempDirectory("daten-test").toFile()
        assets.clear()
        assets[NAME] = ALT.toByteArray()
        assets["daten/manifest.json"] = manifestText(1, "2026-11-09", mapOf(NAME to eintrag(ALT, 1, "2026-11-09"))).toByteArray()
        Daten.assetLeser = { assets[it] ?: error("kein Asset $it") }
        Daten.haupt = { listOf(hauptPub) }
        Daten.fuerTest(ordner)
    }

    @After fun aufraeumen() {
        Daten.assetLeser = { error("kein Asset") }
        Daten.haupt = { Quellen.schluessel }
        Daten.fuerTest(null)
        Uhr.vorstellenTage(0)
        ordner.deleteRecursively()
    }

    private fun eintrag(inhalt: String, fassung: Int, stand: String, schema: Int = 1, minApp: String = "0.73", sha: String? = null, bytes: Int? = null): String =
        """{"fassung":$fassung,"sha256":"${sha ?: sha256(inhalt.toByteArray())}","bytes":${bytes ?: inhalt.toByteArray().size},"stand":"$stand","schema":$schema,"minApp":"$minApp"}"""

    private fun manifestText(version: Long, erzeugt: String, dateien: Map<String, String>): String =
        """{"manifest":1,"version":$version,"erzeugt":"$erzeugt","dateien":{""" + dateien.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + "}}"

    private fun zertText(version: Long, seed: ByteArray, bis: String) =
        """{"zertifikat":1,"version":$version,"schluessel":"${hex(Ed25519.oeffentlich(seed, sha512))}","ausgestellt":"2026-11-10","gueltigBis":"$bis"}"""

    // Ein Archiv als Antworten: Zertifikat, Manifest, Dateien; Schluessel und Fehler waehlbar
    private class Archiv(val dateien: MutableMap<String, String> = HashMap())

    private fun archiv(
        cert: String = zertText(1, datenSeed, "2030-11-10"), certSig: ByteArray = hauptSeed,
        manifest: String, manifestSig: ByteArray = datenSeed, inhalte: Map<String, String> = mapOf(NAME to NEU),
    ): Archiv {
        val a = Archiv()
        a.dateien["schluessel.json"] = cert
        a.dateien["schluessel.json.sig"] = sig(certSig, cert)
        a.dateien["manifest.json"] = manifest
        a.dateien["manifest.json.sig"] = sig(manifestSig, manifest)
        inhalte.forEach { (n, t) -> a.dateien[n] = t }
        return a
    }

    private fun holen(a: Archiv?): suspend (String, Int) -> Daten.Abruf = { url, _ ->
        val basis = Datenbuendel.BASEN.first { url.startsWith(it) }
        val t = a?.dateien?.get(url.removePrefix(basis))
        if (a == null) Daten.Abruf(0, null) else if (t == null) Daten.Abruf(404, null) else Daten.Abruf(200, t)
    }

    private fun lauf(a: Archiv?): String = runBlocking { Daten.aktualisieren(holen(a)) }

    private fun gutesManifest(inhalt: String = NEU, fassung: Int = 2, version: Long = 2, stand: String = "2027-02-01", erzeugt: String = "2027-02-01", schema: Int = 1, minApp: String = "0.73") =
        manifestText(version, erzeugt, mapOf(NAME to eintrag(inhalt, fassung, stand, schema, minApp)))

    // ------------------------------------------------------------ Kette

    @Test fun neuereFerndateiWirdUebernommen() {
        assertEquals(ALT, Daten.text(NAME))
        assertEquals("NEU", lauf(archiv(manifest = gutesManifest())))
        assertEquals(NEU, Daten.text(NAME))
        assertEquals("2027-02-01", Daten.stand(NAME))
        assertEquals(2, Daten.fassung(NAME))
        // Neustart: der Stand kommt geprueft vom Geraet
        Daten.fuerTest(ordner)
        assertEquals(NEU, Daten.text(NAME))
        // Das Lesen mit dem echten Leser klappt, unbekannte Felder stoeren nicht
        assertEquals(1100.0, Steuerwerte.lesen(Daten.text(NAME))!!.jahre.getValue("2027").pauschbetrag, 0.0)
    }

    @Test fun gleicheOderAelterereFassungLaesstDasEingebauteStehen() {
        assertEquals("NEU", lauf(archiv(manifest = gutesManifest(NEU, fassung = 1))))
        assertEquals(ALT, Daten.text(NAME))
        assertEquals(1, Daten.fassung(NAME))
    }

    @Test fun falscheManifestSignaturWirdAbgelehnt() {
        assertEquals("SIGNATUR", lauf(archiv(manifest = gutesManifest(), manifestSig = fremdSeed)))
        assertEquals(ALT, Daten.text(NAME))
    }

    @Test fun zertifikatNurVomHauptschluessel() {
        // Der Datenschluessel darf sich nicht selbst zertifizieren
        assertEquals("SIGNATUR", lauf(archiv(manifest = gutesManifest(), certSig = datenSeed)))
        assertEquals(ALT, Daten.text(NAME))
    }

    @Test fun datenschluesselSigniertNieDasQuellenbuendel() {
        val buendel = """{"version":9,"notices":[{"id":"x","title":"T"}]}"""
        val pk = listOf(hauptPub)
        assertTrue(Quellenbuendel.pruefen(buendel.toByteArray(), sig(hauptSeed, buendel), pk, 1, sha512) is Quellenbuendel.Ergebnis.Angenommen)
        assertEquals(Quellenbuendel.Ergebnis.Abgelehnt(Quellenbuendel.Grund.SIGNATUR), Quellenbuendel.pruefen(buendel.toByteArray(), sig(datenSeed, buendel), pk, 1, sha512))
        // Auch nicht mit den echten Schluesseln der App
        assertEquals(Quellenbuendel.Ergebnis.Abgelehnt(Quellenbuendel.Grund.SIGNATUR), Quellenbuendel.pruefen(buendel.toByteArray(), sig(datenSeed, buendel), Quellen.schluessel, 1, sha512))
    }

    @Test fun pruefsummeGroesseSchemaMindestfassung() {
        // Falsche Pruefsumme
        val schlecht = manifestText(2, "2027-02-01", mapOf(NAME to eintrag(NEU, 2, "2027-02-01", sha = sha256("anders".toByteArray()))))
        lauf(archiv(manifest = schlecht)); assertEquals(ALT, Daten.text(NAME))
        // Falsche Groesse
        lauf(archiv(manifest = manifestText(3, "2027-02-01", mapOf(NAME to eintrag(NEU, 2, "2027-02-01", bytes = 5))))); assertEquals(ALT, Daten.text(NAME))
        // Neueres Schema, als die App lesen kann
        lauf(archiv(manifest = gutesManifest(version = 4, schema = 2))); assertEquals(ALT, Daten.text(NAME))
        // Mindestfassung neuer als die App
        lauf(archiv(manifest = gutesManifest(version = 5, minApp = "9.0"))); assertEquals(ALT, Daten.text(NAME))
        // Datei zu gross (Manifest nennt mehr als die Grenze): Eintrag faellt weg
        val gross = manifestText(6, "2027-02-01", mapOf(NAME to eintrag(NEU, 2, "2027-02-01", bytes = Datenbuendel.MAX_DATEI + 1)))
        lauf(archiv(manifest = gross)); assertEquals(ALT, Daten.text(NAME))
        // Und mit allem richtig geht es
        assertEquals("NEU", lauf(archiv(manifest = gutesManifest(version = 7)))); assertEquals(NEU, Daten.text(NAME))
    }

    @Test fun unlesbareFerndateiVerdraengtDasEingebauteNie() {
        // Pruefsumme stimmt, aber der Leser versteht die Datei nicht (Schema-Verletzung)
        val kaputt = """{"v":1,"jahre":{}}"""
        lauf(archiv(manifest = gutesManifest(kaputt), inhalte = mapOf(NAME to kaputt)))
        assertEquals(ALT, Daten.text(NAME))
        // Kein JSON
        val text = "kein json"
        lauf(archiv(manifest = gutesManifest(text, version = 3), inhalte = mapOf(NAME to text)))
        assertEquals(ALT, Daten.text(NAME))
    }

    @Test fun unbekannteDateiImManifestWirdUebergangen() {
        val m = manifestText(2, "2027-02-01", mapOf(NAME to eintrag(NEU, 2, "2027-02-01"), "neu/zukunft.json" to eintrag("{}", 1, "2027-02-01")))
        assertEquals("NEU", lauf(archiv(manifest = m, inhalte = mapOf(NAME to NEU, "neu/zukunft.json" to "{}"))))
        assertEquals(NEU, Daten.text(NAME))
        assertFalse(File(ordner, "daten/neu/zukunft.json").exists())
    }

    @Test fun unsichererNameWirdVerworfen() {
        val m = manifestText(2, "2027-02-01", mapOf("../x.json" to eintrag("{}", 1, "2027-02-01"), "/etc/x.json" to eintrag("{}", 1, "2027-02-01"), NAME to eintrag(NEU, 2, "2027-02-01")))
        val mf = Datenbuendel.manifestLesen(m)!!
        assertEquals(setOf(NAME), mf.dateien.keys)
        assertTrue(Datenbuendel.nameSicher("globus/strukturen.json"))
        assertFalse(Datenbuendel.nameSicher("globus/../x.json"))
        assertFalse(Datenbuendel.nameSicher("x.exe"))
    }

    // ------------------------------------------------------------ Netz, Ablauf, Rueckruf

    @Test fun ohneNetzBleibtDasEingebauteUndNichtsStuerzt() {
        assertEquals("NETZ", lauf(null))
        assertEquals(ALT, Daten.text(NAME))
        // Nur das Zertifikat da, kein Manifest
        val a = archiv(manifest = gutesManifest()); a.dateien.remove("manifest.json")
        lauf(a)
        assertEquals(ALT, Daten.text(NAME))
    }

    @Test fun abgelaufenesZertifikatLaesstDieLetztenDateienStehenUndMeldetDenStand() {
        // Zuerst ein gueltiger Lauf
        assertEquals("NEU", lauf(archiv(manifest = gutesManifest())))
        // Vier Jahre und ein Tag spaeter: das Zertifikat (bis 2030-11-10) ist abgelaufen
        val bis = LocalDate.of(2030, 11, 10)
        Uhr.vorstellenTage(bis.toEpochDay() + 5 - Uhr.heute().toEpochDay())
        Daten.fuerTest(ordner)
        assertEquals(NEU, Daten.text(NAME))
        assertEquals("ABGELAUFEN", lauf(archiv(manifest = gutesManifest(NEU, 3, 3))))
        assertTrue(Daten.zustand.value.abgelaufen)
        assertEquals("2027-02-01", Daten.zustand.value.manifestStand)
        // Die neue Datei wurde nicht uebernommen
        assertEquals(2, Daten.fassung(NAME))
    }

    @Test fun neuesZertifikatRuftDenAltenDatenschluesselZurueck() {
        assertEquals("NEU", lauf(archiv(manifest = gutesManifest())))
        // Version 2 des Zertifikats mit neuem Datenschluessel; ein Manifest mit dem alten Schluessel gilt nicht mehr
        val z2 = zertText(2, datenSeed2, "2031-01-01")
        val altesMitNeuemZert = archiv(cert = z2, manifest = gutesManifest(NEU, 3, 3), manifestSig = datenSeed)
        assertEquals("SIGNATUR", lauf(altesMitNeuemZert))
        assertEquals(2, Daten.fassung(NAME))
        // Mit dem neuen Schluessel geht es
        assertEquals("NEU", lauf(archiv(cert = z2, manifest = gutesManifest(NEU, 3, 4), manifestSig = datenSeed2)))
        assertEquals(3, Daten.fassung(NAME))
        // Ein altes Zertifikat (Version 1) ersetzt das neue nicht mehr
        assertEquals("SIGNATUR", lauf(archiv(manifest = gutesManifest(NEU, 4, 5), manifestSig = datenSeed)))
        assertEquals(3, Daten.fassung(NAME))
    }

    @Test fun manifestNachAblaufDesZertifikatsGiltNicht() {
        // Das Manifest ist nach dem Ablauf des Zertifikats erzeugt (bis 2030-11-10): es gilt nicht, auch wenn das Zertifikat heute noch gilt
        assertEquals("ABGELAUFEN", lauf(archiv(manifest = gutesManifest(erzeugt = "2031-02-01"))))
        assertEquals(ALT, Daten.text(NAME))
    }

    @Test fun zertifikatUndManifestLassenSichEinzelnPruefen() {
        val z = zertText(1, datenSeed, "2030-11-10")
        val ok = Datenbuendel.zertifikatPruefen(z.toByteArray(), sig(hauptSeed, z), listOf(hauptPub), 0, "2026-11-10", sha512)
        assertTrue(ok is Datenbuendel.Ergebnis.Ok)
        val zert = (ok as Datenbuendel.Ergebnis.Ok).wert
        assertFalse(zert.abgelaufen("2030-11-10"))
        assertTrue(zert.abgelaufen("2030-11-11"))
        val abgelaufen = Datenbuendel.zertifikatPruefen(z.toByteArray(), sig(hauptSeed, z), listOf(hauptPub), 0, "2031-01-01", sha512)
        assertEquals(Datenbuendel.Grund.ABGELAUFEN, (abgelaufen as Datenbuendel.Ergebnis.Nein).grund)
        // Ohne Ablaufpruefung (Laden vom Geraet) gilt es
        assertTrue(Datenbuendel.zertifikatPruefen(z.toByteArray(), sig(hauptSeed, z), listOf(hauptPub), 0, "2031-01-01", sha512, ablaufPruefen = false) is Datenbuendel.Ergebnis.Ok)
        // Form
        val kaputt = """{"zertifikat":1,"version":1}"""
        assertEquals(Datenbuendel.Grund.FORM, (Datenbuendel.zertifikatPruefen(kaputt.toByteArray(), sig(hauptSeed, kaputt), listOf(hauptPub), 0, "2026-11-10", sha512) as Datenbuendel.Ergebnis.Nein).grund)
    }

    // ------------------------------------------------------------ Zeitreise

    @Test fun zeitreiseUmEinDreiUndFuenfJahre() {
        val namen = Datenbuendel.manifestLesen(String(assets["daten/manifest.json"]!!))!!.dateien.keys
        for (jahre in listOf(1, 3, 5)) {
            Uhr.vorstellenTage(365L * jahre)
            for (n in namen) {
                // Nichts stuerzt ab, der Zustand ist immer bestimmt
                val z = Daten.alter(n)
                assertTrue(z in Alter.Zustand.values())
                assertTrue(Daten.stand(n).isNotEmpty())
                assertTrue(Daten.text(n).isNotEmpty())
            }
        }
    }
}
