package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Personenbilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

// Kongressmitglieder fuer Copytrading: Abgeordnete und Senatoren aus unitedstates/congress-legislators (CC0) mit
// Partei, Staat, Wahlkreis, Amtszeit und Ausschuessen. Nach einer Wahl gleicht die App eine frisch geladene Liste mit
// dem gespeicherten Stand ab: Ausgeschiedene Mitglieder wandern mit Zeitraum nach "Ehemalig" und verschwinden nach
// einer Frist. Reine Rechenfunktionen ohne Abruf und ohne Oberflaeche.
object KongressBestand {

    @Serializable
    data class Mitglied(
        val id: String,        // Bioguide-Kennung, etwa "P000197"
        val n: String,         // Nachname
        val v: String = "",    // Vorname
        val r: String = "",    // Rufname, leer wenn keiner
        val k: String,         // "rep" oder "sen"
        val st: String,        // Bundesstaat
        val d: Int? = null,    // Wahlkreis (nur rep, 0 = ganzer Staat)
        val pa: String = "",   // R, D, I, L
        val seit: String = "", // Beginn der laufenden Folge von Amtszeiten in dieser Kammer
        val bis: String = "",  // Ende der laufenden Amtszeit, bei Ehemaligen Ende der Mitgliedschaft
    )

    @Serializable
    data class Bestand(
        val format: Int = 1,
        val stand: String = "",  // Tag der Daten
        val geprueft: Long = 0L, // letzte erfolgreiche Pruefung (ms), 0 = nie
        val aktuell: List<Mitglied> = emptyList(),
        val ehemalig: List<Mitglied> = emptyList(),
        // Ausschuesse je Kennung: [Name, Funktion]; leer = mitgelieferte personen.json gilt
        val a: Map<String, List<List<String>>> = emptyMap(),
    )

    // Treffer im Verzeichnis; ehemalig bei beendeter Folge in dieser Kammer
    data class Treffer(val mitglied: Mitglied, val ehemalig: Boolean)

    const val SEITE = "https://github.com/unitedstates/congress-legislators"
    const val AKTUELL = "https://unitedstates.github.io/congress-legislators/legislators-current.json"
    const val MITGLIEDSCHAFTEN = "https://unitedstates.github.io/congress-legislators/committee-membership-current.json"
    const val AUSSCHUESSE = "https://unitedstates.github.io/congress-legislators/committees-current.json"

    // Obergrenze fuer geladene Dateien (legislators-current.json ist heute 1,5 MB)
    const val MAX_BYTES = 12_000_000

    // Nach dem Ausscheiden kommen noch Meldungen: Transaktionsmeldungen bis 45 Tage nach dem Geschaeft, der
    // Abschlussbericht 30 Tage nach dem Ausscheiden (mit Verlaengerung bis 90 Tage mehr), bei Ausscheiden im Januar
    // der Jahresbericht bis 15. Mai. 180 Tage nach dem Ausscheiden und nach der letzten Meldung decken das ab.
    const val FRIST_TAGE = 180

    // Plausibilitaet einer geladenen Liste: Mindestzahl der Mitglieder je Kammer
    const val MIN_HAUS = 400
    const val MIN_SENAT = 90

    private val json = Json { ignoreUnknownKeys = true }

    // Bioguide-Kennung im Format der Bildadresse
    private val KENNUNG = Regex("^[A-Z][0-9]{6}$")

    // Gespeicherter Bestand; null bei kaputtem Text oder anderem Format
    fun lesen(text: String): Bestand? =
        runCatching { json.decodeFromString<Bestand>(text) }.getOrNull()?.takeIf { it.format == 1 }

    fun schreiben(b: Bestand): String = json.encodeToString(b)

    // Aktuelle Mitglieder aus legislators-current.json, Reihenfolge wie in der Datei; null wenn kein JSON-Array.
    // Wahlsieger vor dem Amtsantritt (nur kuenftige Amtszeiten) fehlen noch, einzelne kaputte Personen entfallen.
    fun ausLegislators(text: String, heute: String): List<Mitglied>? {
        val personen = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return null
        val ergebnis = mutableListOf<Mitglied>()
        for (element in personen) {
            val person = element as? JsonObject ?: continue
            val id = (person["id"] as? JsonObject)?.get("bioguide").text
            if (id == null || !KENNUNG.matches(id)) continue
            val name = person["name"] as? JsonObject ?: continue
            val amtszeiten = person["terms"] as? JsonArray ?: continue
            var stelle = -1
            for (i in amtszeiten.indices) {
                val a = amtszeiten[i] as? JsonObject ?: continue
                val start = a["start"].text ?: continue
                if (start <= heute) stelle = i
            }
            if (stelle < 0) continue
            val laufend = amtszeiten[stelle] as? JsonObject ?: continue
            val k = laufend["type"].text
            if (k != "rep" && k != "sen") continue
            var seit = laufend["start"].text ?: continue
            for (i in stelle - 1 downTo 0) {
                val vorige = amtszeiten[i] as? JsonObject ?: break
                if (vorige["type"].text != k) break
                val seitTag = tag(seit) ?: break
                val endTag = tag(vorige["end"].text) ?: break
                // Andere Kammer oder Luecke von mehr als 31 Tagen beendet die Folge
                if (seitTag - endTag > 31) break
                seit = vorige["start"].text ?: break
            }
            val n = name["last"].text ?: continue
            val st = laufend["state"].text ?: continue
            ergebnis += Mitglied(
                id = id, n = n,
                v = name["first"].text ?: "",
                r = name["nickname"].text ?: "",
                k = k, st = st,
                d = if (k == "rep") (laufend["district"] as? JsonPrimitive)?.intOrNull else null,
                pa = when (laufend["party"].text) {
                    "Republican" -> "R"
                    "Democrat" -> "D"
                    "Libertarian" -> "L"
                    else -> "I"
                },
                seit = seit,
                bis = laufend["end"].text ?: "",
            )
        }
        return ergebnis
    }

    // Schutz vor kaputten oder halben Dateien: genug Mitglieder je Kammer und, bei vorhandenem Bestand, mindestens
    // die Haelfte der bekannten Kennungen wieder dabei
    fun plausibel(neu: List<Mitglied>, alt: Bestand?): Boolean {
        if (neu.count { it.k == "rep" } < MIN_HAUS || neu.count { it.k == "sen" } < MIN_SENAT) return false
        val bekannt = alt?.aktuell?.mapTo(HashSet()) { it.id } ?: return true
        if (bekannt.isEmpty()) return true
        val ids = neu.mapTo(HashSet()) { it.id }
        return bekannt.count { it in ids } * 2 >= bekannt.size
    }

    // Ausschuesse je Kennung aus den beiden Ausschussdateien; nur ganze Ausschuesse mit vier Zeichen Kennung, keine
    // Unterausschuesse. null bei kaputtem JSON oder leerem Ergebnis.
    fun ausschuesse(mitgliedschaften: String, ausschuesse: String): Map<String, List<List<String>>>? {
        val jeAusschuss = runCatching { json.parseToJsonElement(mitgliedschaften) }.getOrNull() as? JsonObject ?: return null
        val liste = runCatching { json.parseToJsonElement(ausschuesse) }.getOrNull() as? JsonArray ?: return null
        val namen = HashMap<String, String>()
        for (element in liste) {
            val a = element as? JsonObject ?: continue
            val kennung = a["thomas_id"].text ?: continue
            namen[kennung] = a["name"].text ?: continue
        }
        val jePerson = HashMap<String, MutableList<List<String>>>()
        for ((kennung, mitglieder) in jeAusschuss) {
            if (kennung.length != 4) continue
            val name = namen[kennung] ?: continue
            val reihe = mitglieder as? JsonArray ?: continue
            for (element in reihe) {
                val m = element as? JsonObject ?: continue
                val id = m["bioguide"].text ?: continue
                jePerson.getOrPut(id) { mutableListOf() }.add(listOf(kurz(name), m["title"].text ?: ""))
            }
        }
        if (jePerson.isEmpty()) return null
        // Mit Funktion zuerst, dann nach Name
        return jePerson.mapValues { it.value.sortedWith(compareBy({ it[1].isEmpty() }, { it[0] })) }
    }

    // Gleicht einen Bestand mit einer frisch geladenen Liste ab: Folgen, die in der neuen Liste fehlen, werden mit Ende
    // ehemalig, zurueckgekehrte Mitglieder verlassen die Ehemaligen. Ausschuesse nur bei neu geladenen.
    // Ende einer Folge: das Ende der Amtszeit, hoechstens heute, aber mindestens der Tag der letzten Pruefung. Ernannte
    // Senatoren haben in der Quelle ein Ende am Tag der Nachwahl und bleiben bis zur Vereidigung der Nachfolge im Amt.
    // Bei langer Pause zwischen zwei Pruefungen ist das Ende nur eine Naeherung.
    fun abgleich(alt: Bestand, neu: List<Mitglied>, heute: String, ausschuesse: Map<String, List<List<String>>>?): Bestand {
        val aktiv = aktivSet(neu)
        val neueEhemalige = alt.aktuell.filter { (it.id to it.k) !in aktiv }.map { it.copy(bis = kleineres(groesseres(kleineres(it.bis, heute), alt.stand), heute)) }
        return Bestand(
            stand = heute,
            geprueft = alt.geprueft,
            aktuell = neu,
            ehemalig = vereinigt(alt.ehemalig + neueEhemalige, aktiv),
            a = ausschuesse ?: alt.a,
        )
    }

    // Vereinigt den gespeicherten Stand auf dem Geraet mit dem mit der App gelieferten; der neuere Stand gewinnt,
    // bei gleichem Datum der gespeicherte. Die Ehemaligen aus beiden bleiben erhalten.
    fun zusammen(gespeichert: Bestand?, mitgebracht: Bestand?): Bestand? {
        val g = gespeichert ?: return mitgebracht
        val m = mitgebracht ?: return g
        val aelterer = if (g.stand >= m.stand) m else g
        val neuerer = if (g.stand >= m.stand) g else m
        val erste = abgleich(aelterer, neuerer.aktuell, neuerer.stand, neuerer.a)
        return erste.copy(
            geprueft = g.geprueft,
            ehemalig = vereinigt(erste.ehemalig + neuerer.ehemalig, aktivSet(erste.aktuell)),
        )
    }

    // Ehemalige bleiben sichtbar, bis die Frist nach dem Ende der Mitgliedschaft oder der letzten Meldung ablaeuft
    fun sichtbar(e: Mitglied, letzteMeldung: Long, jetzt: Long): Boolean =
        jetzt <= maxOf((tag(e.bis) ?: 0L) * 86_400_000L, letzteMeldung) + FRIST_TAGE * 86_400_000L

    // Trennt eine Anzeigeliste in Mitglieder (Unbekannte bleiben dabei) und sichtbare Ehemalige; die Reihenfolge
    // bleibt. Ehemalige hinter der Frist entfallen, ausser alle (Suche ueber alles).
    fun <T> aufteilen(
        liste: List<T>,
        treffer: (T) -> Treffer?,
        letzte: (T) -> Long,
        jetzt: Long,
        alle: Boolean,
    ): Pair<List<T>, List<T>> {
        val mitglieder = mutableListOf<T>()
        val ehemalige = mutableListOf<T>()
        for (x in liste) {
            val t = treffer(x)
            when {
                t == null || !t.ehemalig -> mitglieder += x
                alle || sichtbar(t.mitglied, letzte(x), jetzt) -> ehemalige += x
            }
        }
        return mitglieder to ehemalige
    }

    // Erstes und letztes Jahr der Folge, null wenn eines fehlt
    fun jahre(e: Mitglied): Pair<Int, Int>? {
        val von = e.seit.take(4).toIntOrNull() ?: return null
        val bis = e.bis.take(4).toIntOrNull() ?: return null
        return von to bis
    }

    // Zuordnung der Namen aus den Wertpapiermeldungen zu Mitgliedern eines Bestands, getrennt nach Kammer sowie
    // aktuell und ehemalig. Die Schluessel sind die aus bilder.json ("CA11|pelosi", "sanders|bernie").
    class Verzeichnis(b: Bestand) {
        private val hausAktuell = hausSchluessel(b.aktuell.filter { it.k == "rep" })
        private val hausEhemalig = hausSchluessel(b.ehemalig.filter { it.k == "rep" })
        private val senatAktuell = senatSchluessel(b.aktuell.filter { it.k == "sen" })
        private val senatEhemalig = senatSchluessel(b.ehemalig.filter { it.k == "sen" })
        private val aktuellJeId = HashMap<String, Mitglied>()
        private val ehemaligJeId = HashMap<String, Mitglied>()

        init {
            for (m in b.aktuell) if (m.id !in aktuellJeId) aktuellJeId[m.id] = m
            for (m in b.ehemalig) {
                val bisher = ehemaligJeId[m.id]
                if (bisher == null || bisher.bis < m.bis) ehemaligJeId[m.id] = m
            }
        }

        // Abgeordnete nach Nachname und Wahlkreis ("CA11"; ohne Zahl nur der Staat), Namensvarianten wie Personenbilder
        fun haus(nachname: String, wahlkreis: String, vorname: String = ""): Treffer? {
            val st = wahlkreis.take(2).uppercase()
            if (st.length < 2 || Personenbilder.norm(Personenbilder.ohneZusatz(nachname)).isEmpty()) return null
            val namen = hausNamen(nachname, vorname)
            val d = wahlkreis.drop(2).toIntOrNull()
            if (d != null) {
                val kreis = st + d.toString().padStart(2, '0')
                for (n in namen) {
                    hausAktuell["$kreis|$n"]?.let { return Treffer(it, false) }
                    hausEhemalig["$kreis|$n"]?.let { return Treffer(it, true) }
                }
            }
            for (n in namen) {
                hausAktuell["$st|$n"]?.let { return Treffer(it, false) }
                hausEhemalig["$st|$n"]?.let { return Treffer(it, true) }
            }
            return null
        }

        // Senatoren nach Nachname und Vorname (Rufname oder erster Vorname), sonst nach eindeutigem Nachnamen
        fun senat(nachname: String, vorname: String): Treffer? {
            val n = Personenbilder.norm(Personenbilder.ohneZusatz(nachname))
            if (n.isEmpty()) return null
            val v = Personenbilder.norm(vorname.trim().substringBefore(' '))
            senatAktuell["$n|$v"]?.let { return Treffer(it, false) }
            senatEhemalig["$n|$v"]?.let { return Treffer(it, true) }
            senatAktuell[n]?.let { return Treffer(it, false) }
            senatEhemalig[n]?.let { return Treffer(it, true) }
            return null
        }

        // Nach Bioguide-Kennung, aktuell zuerst, sonst die Ehemalige mit spaetestem Ende
        fun kennung(id: String): Treffer? =
            aktuellJeId[id]?.let { Treffer(it, false) } ?: ehemaligJeId[id]?.let { Treffer(it, true) }
    }

    // Aktuell gefuehrte Folgen als Paare aus Kennung und Kammer
    private fun aktivSet(liste: List<Mitglied>): HashSet<Pair<String, String>> =
        liste.mapTo(HashSet()) { it.id to it.k }

    // Ehemalige ohne aktuell gefuehrte und ohne Doppelte (Schluessel aus Kennung, Kammer und seit), juengstes Ende zuerst
    private fun vereinigt(liste: List<Mitglied>, aktiv: Set<Pair<String, String>>): List<Mitglied> {
        val gesehen = HashSet<String>()
        return liste
            .filter { (it.id to it.k) !in aktiv }
            .filter { gesehen.add(it.id + it.k + it.seit) }
            .sortedByDescending { it.bis }
    }

    // Text eines JSON-Werts, null bei fehlendem Wert oder Objekt
    private val JsonElement?.text: String? get() = (this as? JsonPrimitive)?.contentOrNull

    // Tag in Tagen seit 1970, null bei leeren und ungueltigen Daten
    private fun tag(s: String?): Long? = s?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }

    // Das groessere der beiden Daten; ein ungueltiges b zaehlt nicht
    private fun groesseres(a: String, b: String): String {
        val x = runCatching { LocalDate.parse(a) }.getOrNull() ?: return a
        val y = runCatching { LocalDate.parse(b) }.getOrNull() ?: return a
        return if (y > x) b else a
    }

    // Das kleinere der beiden Daten; leere und ungueltige Angaben zaehlen wie heute
    private fun kleineres(bis: String, heute: String): String {
        val ende = runCatching { LocalDate.parse(bis) }.getOrNull() ?: return heute
        val tag = runCatching { LocalDate.parse(heute) }.getOrNull() ?: return heute
        return if (ende < tag) bis else heute
    }

    // Moegliche Vorsaetze der Ausschussnamen, in der Reihenfolge der Pruefung
    private val VORSAETZE = listOf(
        "House Committee on ", "Senate Committee on ", "Senate Special Committee on ", "Senate Select Committee on ",
        "House Permanent Select Committee on ", "Joint Committee on ", "Committee on ", "Senate ", "House ",
    )

    // "House Committee on Financial Services" wird zu "Financial Services"
    private fun kurz(name: String): String {
        var n = name
        for (vorn in VORSAETZE) if (n.startsWith(vorn)) {
            n = n.removePrefix(vorn)
            break
        }
        return if (n.startsWith("the ")) n.removePrefix("the ") else n
    }

    // Schluessel der Abgeordneten einer Gruppe: Wahlkreis, dazu der Staat allein bei eindeutigem Nachnamen im Staat
    private fun hausSchluessel(liste: List<Mitglied>): Map<String, Mitglied> {
        val map = HashMap<String, Mitglied>()
        val jeStaat = HashMap<String, MutableSet<String>>()
        val spaeteste = HashMap<String, Mitglied>()
        for (m in liste) {
            val n = Personenbilder.norm(Personenbilder.ohneZusatz(m.n))
            if (n.isEmpty()) continue
            val st = m.st.uppercase()
            m.d?.let { d -> tragen(map, "$st${d.toString().padStart(2, '0')}|$n", m) }
            val schluessel = "$st|$n"
            jeStaat.getOrPut(schluessel) { HashSet() }.add(m.id)
            val bisher = spaeteste[schluessel]
            if (bisher == null || bisher.bis < m.bis) spaeteste[schluessel] = m
        }
        for ((schluessel, ids) in jeStaat) if (ids.size == 1) map[schluessel] = spaeteste.getValue(schluessel)
        return map
    }

    // Schluessel der Senatoren einer Gruppe: Vorname und Rufname, dazu der Nachname allein bei Eindeutigkeit
    private fun senatSchluessel(liste: List<Mitglied>): Map<String, Mitglied> {
        val map = HashMap<String, Mitglied>()
        val jeName = HashMap<String, MutableSet<String>>()
        val spaeteste = HashMap<String, Mitglied>()
        for (m in liste) {
            val n = Personenbilder.norm(Personenbilder.ohneZusatz(m.n))
            if (n.isEmpty()) continue
            tragen(map, "$n|${Personenbilder.norm(m.v.substringBefore(' '))}", m)
            if (m.r.isNotEmpty()) tragen(map, "$n|${Personenbilder.norm(m.r.substringBefore(' '))}", m)
            jeName.getOrPut(n) { HashSet() }.add(m.id)
            val bisher = spaeteste[n]
            if (bisher == null || bisher.bis < m.bis) spaeteste[n] = m
        }
        for ((n, ids) in jeName) if (ids.size == 1) map[n] = spaeteste.getValue(n)
        return map
    }

    // Bei mehreren Folgen unter einem Schluessel gewinnt die mit dem spaetesten Ende
    private fun tragen(map: MutableMap<String, Mitglied>, schluessel: String, m: Mitglied) {
        val bisher = map[schluessel]
        if (bisher == null || bisher.bis < m.bis) map[schluessel] = m
    }

    // Namensvarianten wie Personenbilder.hausId: Nachname, Doppelname aus Vorname und Nachname, letzter Namensteil
    private fun hausNamen(nachname: String, vorname: String): List<String> {
        val ohne = Personenbilder.ohneZusatz(nachname)
        return listOf(
            Personenbilder.norm(ohne),
            Personenbilder.norm(vorname.trim().substringAfterLast(' ') + nachname),
            Personenbilder.norm(ohne.substringAfterLast(' ')),
        ).filter { it.isNotEmpty() }.distinct()
    }
}
