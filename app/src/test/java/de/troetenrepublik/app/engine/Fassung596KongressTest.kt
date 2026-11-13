package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Fassung 5.96: Kongressmitglieder nach den Wahlen: Lesen der Liste von unitedstates/congress-legislators,
// Abgleich mit Ehemaligen, Frist, Zuordnung der Namen.
class Fassung596KongressTest {

    // Echte Ausschnitte der Quelle
    private val legislators = ressource("legislators-current-auszug.json")
    private val mitgliedschaften = ressource("committee-membership-auszug.json")
    private val ausschuesse = ressource("committees-auszug.json")

    // Mit der App gelieferte Mitglieder
    private val mitgeliefert: KongressBestand.Bestand by lazy { KongressBestand.lesen(asset("copytrading/kongress.json"))!! }

    // Liest Namen, Kammer, Staat, Wahlkreis, Partei und die Folge der Amtszeiten aus dem echten Ausschnitt
    @Test fun ausLegislatorsAuszug() {
        val liste = KongressBestand.ausLegislators(legislators, "2026-11-04")!!
        assertEquals(7, liste.size)
        val jeId = liste.associateBy { it.id }
        val pelosi = jeId.getValue("P000197")
        assertEquals("Nancy", pelosi.v)
        assertEquals("rep", pelosi.k)
        assertEquals("CA", pelosi.st)
        assertEquals(11, pelosi.d!!)
        assertEquals("D", pelosi.pa)
        assertEquals("1987-01-06", pelosi.seit)
        assertEquals("2027-01-03", pelosi.bis)
        val schiff = jeId.getValue("S001150")
        assertEquals("sen", schiff.k)
        assertEquals("CA", schiff.st)
        assertNull(schiff.d)
        assertEquals("D", schiff.pa)
        assertEquals("2024-12-09", schiff.seit)
        val sanders = jeId.getValue("S000033")
        assertEquals("Bernie", sanders.r)
        assertEquals("sen", sanders.k)
        assertEquals("VT", sanders.st)
        assertEquals("I", sanders.pa)
        assertEquals("2007-01-04", sanders.seit)
        val husted = jeId.getValue("H001104")
        assertEquals("sen", husted.k)
        assertEquals("OH", husted.st)
        assertEquals("R", husted.pa)
        assertEquals("2025-01-21", husted.seit)
        assertEquals("2026-11-03", husted.bis)
        val mcclain = jeId.getValue("M001232")
        assertEquals("rep", mcclain.k)
        assertEquals("MD", mcclain.st)
        assertEquals(6, mcclain.d!!)
        assertEquals("2025-01-03", mcclain.seit)
        val cantwell = jeId.getValue("C000127")
        assertEquals("sen", cantwell.k)
        assertEquals("WA", cantwell.st)
        assertEquals("D", cantwell.pa)
        assertEquals("2001-01-03", cantwell.seit)
        val aoc = jeId.getValue("O000172")
        assertEquals("rep", aoc.k)
        assertEquals("NY", aoc.st)
        assertEquals(14, aoc.d!!)
        assertEquals("2019-01-03", aoc.seit)
    }

    // Wahlsieger vor dem Amtsantritt: nur kuenftige Amtszeiten, also noch kein Mitglied
    @Test fun wahlsiegerVorDemAmtsantritt() {
        val array = Json.parseToJsonElement(legislators) as JsonArray
        val text = JsonArray(array.map { person ->
            val p = person as JsonObject
            // Alle Amtszeiten durch eine einzige ab 2027 ersetzen
            if (bioguide(p) == "H001104") JsonObject(p + ("terms" to JsonArray(listOf(zukunftsAmtszeit("rep", "OH", 6))))) else p
        }).toString()
        val vorDemAmtsantritt = KongressBestand.ausLegislators(text, "2026-11-04")!!
        assertEquals(6, vorDemAmtsantritt.size)
        assertNull(vorDemAmtsantritt.firstOrNull { it.id == "H001104" })
        val husted = KongressBestand.ausLegislators(text, "2027-01-03")!!.first { it.id == "H001104" }
        assertEquals("rep", husted.k)
        assertEquals("OH", husted.st)
        assertEquals(6, husted.d!!)
        assertEquals("D", husted.pa)
        assertEquals("2027-01-03", husted.seit)
        assertEquals("2029-01-03", husted.bis)
    }

    // Angehaengte kuenftige Amtszeit: bis folgt erst mit dem Amtsantritt, seit bleibt
    @Test fun kuenftigeAmtszeitAnhaengen() {
        val array = Json.parseToJsonElement(legislators) as JsonArray
        val text = JsonArray(array.map { person ->
            val p = person as JsonObject
            if (bioguide(p) == "M001232") {
                val amtszeiten = p["terms"] as JsonArray
                JsonObject(p + ("terms" to JsonArray(amtszeiten + zukunftsAmtszeit("rep", "MD", 6))))
            } else p
        }).toString()
        val vorher = KongressBestand.ausLegislators(text, "2026-11-04")!!.first { it.id == "M001232" }
        assertEquals("2027-01-03", vorher.bis)
        assertEquals("2025-01-03", vorher.seit)
        val nachher = KongressBestand.ausLegislators(text, "2027-01-03")!!.first { it.id == "M001232" }
        assertEquals("2029-01-03", nachher.bis)
        assertEquals("2025-01-03", nachher.seit)
    }

    // Schutz vor kaputten oder halben Dateien: Mindestzahlen je Kammer und Anteil bekannter Kennungen
    @Test fun plausibilitaet() {
        val auszug = KongressBestand.ausLegislators(legislators, "2026-11-04")!!
        assertFalse(KongressBestand.plausibel(auszug, null))
        val b = mitgeliefert
        assertTrue(KongressBestand.plausibel(b.aktuell, b))
        var nummer = 0
        val viertel = b.aktuell.filterIndexed { i, _ -> i % 4 == 0 }
        val neu = viertel + listOf("rep" to KongressBestand.MIN_HAUS, "sen" to KongressBestand.MIN_SENAT).flatMap { (k, zahl) ->
            List(zahl - viertel.count { it.k == k }) {
                KongressBestand.Mitglied(id = "X" + (++nummer).toString().padStart(6, '0'), n = "Neu", k = k, st = "CA")
            }
        }
        assertTrue(KongressBestand.plausibel(neu, null))
        assertFalse(KongressBestand.plausibel(neu, b))
    }

    // Ausschuesse aus den Ausschnitten: ganze Ausschuesse mit gekuerztem Namen, Funktion zuerst, keine Unterausschuesse
    @Test fun ausschussZuordnung() {
        val a = KongressBestand.ausschuesse(mitgliedschaften, ausschuesse)!!
        assertEquals(listOf("Commerce, Science, and Transportation", "Ranking Member"), a["C000127"]!!.first())
        // Gleiches Ergebnis wie die mitgelieferte personen.json (tools/copytrading/personen.py)
        val gebaut = de.troetenrepublik.app.market.Personeninfo.lesen(asset("copytrading/personen.json"))!!
        listOf("C000127", "S000033", "S001150", "H001104", "M001232", "O000172").forEach { id -> assertEquals(id, gebaut.p.getValue(id).a, a[id]) }
        assertEquals(6, a["C000127"]!!.size)
        // Unterausschuesse mit sechs Zeichen Kennung entfallen
        assertEquals(4, a["S001150"]!!.size)
        assertEquals(listOf("Agriculture, Nutrition, and Forestry", ""), a["S001150"]!!.first())
        assertTrue(a["H001104"]!!.any { it[0] == "Appropriations" })
        assertEquals(5, a["H001104"]!!.size)
        assertNull(KongressBestand.ausschuesse("{", ausschuesse))
        assertNull(KongressBestand.ausschuesse(mitgliedschaften, "["))
    }

    // Abgleich nach der Wahl: Ausgeschiedene werden ehemalig, Rueckkehrer wieder Mitglied, Kammerwechsel zaehlt
    @Test fun abgleichNachDerWahl() {
        val alt = KongressBestand.Bestand(
            stand = "2026-10-28",
            aktuell = KongressBestand.ausLegislators(legislators, "2026-10-28")!!,
            a = mapOf("X" to listOf(listOf("A", ""))),
        )
        val neu = alt.aktuell.filter { it.id != "H001104" && it.id != "P000197" }
        val erste = KongressBestand.abgleich(alt, neu, "2027-01-05", null)
        assertEquals("2027-01-05", erste.stand)
        assertEquals(neu, erste.aktuell)
        assertEquals(alt.a, erste.a)
        val jeId = erste.ehemalig.associateBy { it.id }
        assertEquals("2026-11-03", jeId.getValue("H001104").bis)
        assertEquals("2027-01-03", jeId.getValue("P000197").bis)
        // Ernannter Senator: bei der letzten Pruefung noch gefuehrt, also bis dahin im Amt
        val spaeter = KongressBestand.abgleich(alt.copy(stand = "2026-12-20"), neu, "2027-01-05", null).ehemalig.associateBy { it.id }
        assertEquals("2026-12-20", spaeter.getValue("H001104").bis)
        assertEquals("2027-01-03", spaeter.getValue("P000197").bis)
        // Rueckkehr: wieder in der Liste, damit aus den Ehemaligen entfernt
        val zweite = KongressBestand.abgleich(erste, neu + jeId.getValue("H001104"), "2027-01-05", null)
        assertNull(zweite.ehemalig.firstOrNull { it.id == "H001104" })
        assertNotNull(zweite.ehemalig.firstOrNull { it.id == "P000197" })
        // Wechsel in den Senat: die rep-Folge wird ehemalig
        val wechsel = KongressBestand.abgleich(
            alt,
            alt.aktuell.map { if (it.id == "O000172") it.copy(k = "sen", d = null) else it },
            "2027-01-05",
            null,
        )
        val aoc = wechsel.ehemalig.first { it.id == "O000172" }
        assertEquals("rep", aoc.k)
        assertEquals("2019-01-03", aoc.seit)
        assertEquals("2027-01-03", aoc.bis)
    }

    // Vereinigung von gespeichertem Stand und mitgebrachter Datei: Ehemalige aus beiden, geprueft vom Geraet
    @Test fun zusammenMitDemGelieferten() {
        val x = KongressBestand.Mitglied(id = "X000001", n = "X", k = "rep", st = "CA", seit = "2019-01-03", bis = "2026-11-10")
        val y = KongressBestand.Mitglied(id = "X000002", n = "Y", k = "sen", st = "TX", seit = "2015-01-06", bis = "2027-01-31")
        val gespeichert = KongressBestand.Bestand(stand = "2026-11-10", geprueft = 123L, ehemalig = listOf(x))
        val mitgebracht = KongressBestand.Bestand(stand = "2027-02-01", ehemalig = listOf(y))
        val ergebnis = KongressBestand.zusammen(gespeichert, mitgebracht)!!
        assertEquals("2027-02-01", ergebnis.stand)
        assertEquals(123L, ergebnis.geprueft)
        assertEquals(setOf("X000001", "X000002"), ergebnis.ehemalig.mapTo(HashSet()) { it.id })
        assertEquals(mitgebracht, KongressBestand.zusammen(null, mitgebracht))
        assertEquals(gespeichert, KongressBestand.zusammen(gespeichert, null))
        assertNull(KongressBestand.zusammen(null, null))
    }

    // 180 Tage nach dem Ausscheiden oder der letzten Meldung
    @Test fun fristFuerEhemalige() {
        val e = KongressBestand.Mitglied(id = "X000001", n = "Ehemaliger", k = "rep", st = "CA", bis = "2027-01-03")
        assertTrue(KongressBestand.sichtbar(e, 0L, ms("2027-06-01")))
        assertTrue(KongressBestand.sichtbar(e, 0L, ms("2027-07-02")))
        assertFalse(KongressBestand.sichtbar(e, 0L, ms("2027-07-03")))
        assertTrue(KongressBestand.sichtbar(e, ms("2027-03-01"), ms("2027-08-28")))
        assertFalse(KongressBestand.sichtbar(e, ms("2027-03-01"), ms("2027-08-29")))
    }

    // Reihenfolge bleibt, Unbekannte bleiben bei den Mitgliedern, hinter der Frist nur mit alle
    @Test fun aufteilenDerListe() {
        val mitglied = KongressBestand.Mitglied(id = "A000001", n = "Aktuell", k = "rep", st = "CA")
        val inDerFrist = KongressBestand.Mitglied(id = "A000002", n = "Frist", k = "sen", st = "OH", bis = "2027-01-03")
        val abgelaufen = KongressBestand.Mitglied(id = "A000003", n = "Abgelaufen", k = "rep", st = "TX", bis = "2025-01-01")
        val liste = listOf(
            "unbekannt" to null,
            "mitglied" to KongressBestand.Treffer(mitglied, false),
            "frist" to KongressBestand.Treffer(inDerFrist, true),
            "abgelaufen" to KongressBestand.Treffer(abgelaufen, true),
        )
        val (mitglieder, ehemalige) = KongressBestand.aufteilen(liste, { it.second }, { 0L }, ms("2027-06-01"), false)
        assertEquals(listOf("unbekannt", "mitglied"), mitglieder.map { it.first })
        assertEquals(listOf("frist"), ehemalige.map { it.first })
        val (_, alle) = KongressBestand.aufteilen(liste, { it.second }, { 0L }, ms("2027-06-01"), true)
        assertEquals(listOf("frist", "abgelaufen"), alle.map { it.first })
    }

    // Zuordnung der Namen aus den Meldungen, auch Doppelnamen und Rufnamen
    @Test fun namenZuordnen() {
        val bestand = KongressBestand.Bestand(aktuell = KongressBestand.ausLegislators(legislators, "2026-11-04")!!)
        val v = KongressBestand.Verzeichnis(bestand)
        val pelosi = v.haus("Pelosi", "CA11", "Nancy")!!
        assertEquals("P000197", pelosi.mitglied.id)
        assertFalse(pelosi.ehemalig)
        assertEquals("M001232", v.haus("McClain Delaney", "MD06", "April")!!.mitglied.id)
        assertEquals("M001232", v.haus("Delaney", "MD06", "April McClain")!!.mitglied.id)
        assertEquals("O000172", v.haus("Ocasio-Cortez", "NY14", "Alexandria")!!.mitglied.id)
        assertEquals("S000033", v.senat("Sanders", "Bernie")!!.mitglied.id)
        assertEquals("S000033", v.senat("Sanders", "Bernard")!!.mitglied.id)
        assertEquals("S001150", v.kennung("S001150")!!.mitglied.id)
        assertNull(v.haus("Unbekannt", "CA11"))
        assertNull(v.senat("Unbekannt", "Niemand"))
        assertNull(v.kennung("Q000000"))
        // Nach dem Ausscheiden nur noch ehemalig
        val ohne = KongressBestand.abgleich(bestand, bestand.aktuell.filter { it.id != "P000197" }, "2027-01-05", null)
        val jetzt = KongressBestand.Verzeichnis(ohne)
        val ehemalige = jetzt.haus("Pelosi", "CA11", "Nancy")!!
        assertEquals("P000197", ehemalige.mitglied.id)
        assertTrue(ehemalige.ehemalig)
        assertEquals("2027-01-03", ehemalige.mitglied.bis)
        assertTrue(jetzt.kennung("P000197")!!.ehemalig)
    }

    // Mitgelieferte Datei: vollstaendige Listen, gueltige Kennungen, Ehemalige ab den mitgelieferten Meldungen
    @Test fun mitgelieferteDatei() {
        val b = mitgeliefert
        assertTrue(b.aktuell.count { it.k == "rep" } >= 430)
        assertTrue(b.aktuell.count { it.k == "sen" } >= 99)
        val kennung = Regex("^[A-Z][0-9]{6}$")
        assertTrue((b.aktuell + b.ehemalig).all { kennung.matches(it.id) })
        assertEquals("2024-12-08", b.ehemalig.first { it.id == "S001150" && it.k == "rep" }.bis)
        assertTrue(b.ehemalig.all { it.bis >= "2023-07-01" })
        assertEquals(b, KongressBestand.lesen(KongressBestand.schreiben(b)))
    }

    // Echter Ausschnitt der Quelle
    private fun ressource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("kongress/$name")!!.readBytes().toString(Charsets.UTF_8)

    // Mit der App gelieferte Datei, je nach Arbeitsverzeichnis der Tests
    private fun asset(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
            .readText(Charsets.UTF_8)

    // Anfang eines Tags in Millisekunden
    private fun ms(datum: String): Long = LocalDate.parse(datum).toEpochDay() * 86_400_000L

    private fun bioguide(person: JsonObject): String =
        ((person["id"] as? JsonObject)?.get("bioguide") as? JsonPrimitive)?.content ?: ""

    // Amtszeit ab 2027, etwa fuer einen Wahlsieger
    private fun zukunftsAmtszeit(kammer: String, staat: String, wahlkreis: Int): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(kammer))
        put("start", JsonPrimitive("2027-01-03"))
        put("end", JsonPrimitive("2029-01-03"))
        put("state", JsonPrimitive(staat))
        put("district", JsonPrimitive(wahlkreis))
        put("party", JsonPrimitive("Democrat"))
    }
}
