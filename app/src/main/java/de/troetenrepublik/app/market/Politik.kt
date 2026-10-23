package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneOffset

// Senat und Regierung: Wertpapiergeschaefte von US-Senatorinnen und -Senatoren und des Praesidenten sowie
// der Bestand des Vizepraesidenten. Beides liegt der App als Schnappschuss bei.
// assets/copytrading/senat.json stammt aus dem Senate-eFD-System (efdsearch.senate.gov). Es weist Abrufe von
// ausserhalb der USA ab; der Schnappschuss entsteht im GitHub-Workflow „Senat“ (tools/senat/holen.py).
// assets/copytrading/regierung.json stammt aus den Meldungen an das U.S. Office of Government Ethics (OGE Form
// 278-T fuer Geschaefte, 278e fuer den Jahresbericht). Die Meldungen des Praesidenten sind eingescannte Formulare;
// sie werden vorab ausgelesen und geprueft, Zeilen ohne sicheres Datum, sichere Art oder sicheren Betrag fehlen.
// Die Geschaefte werden auf Kongress.Geschaeft abgebildet, damit Seiten, Verlauf und Nachbilden dieselben bleiben.
object Politik {

    // Ein Geschaeft im kompakten Format beider Dateien.
    @Serializable
    data class G(
        // Datum JJJJ-MM-TT.
        val d: String,
        // Inhaber: leer selbst, SP Ehepartner, JT gemeinsam, DC Kind.
        val i: String = "",
        val k: String? = null,
        val n: String,
        // Art des Werts: Senat im Wortlaut der Meldung („Stock“), Regierung als Kuerzel (ST, EF, CS, GS, MB, OT).
        val t: String,
        // K Kauf, V Verkauf, T Teilverkauf, X Tausch.
        val a: String,
        val von: Long,
        val bis: Long? = null,
        // Kommentar der Meldung (Senat).
        val c: String? = null,
        // Spaet gemeldet, mehr als 30 Tage nach dem Geschaeft (Regierung).
        val s: Boolean = false,
    )

    @Serializable
    data class SenatMeldung(
        val id: String,
        val vorname: String,
        val nachname: String,
        val amt: String = "",
        val eingang: String,
        val titel: String = "",
        val papier: Boolean = false,
        val geschaefte: List<G> = emptyList(),
    ) {
        // Schluessel einer Person: Nachname und Vorname, ohne Satzzeichen.
        val person: String get() = sauber(nachname) + "|" + sauber(vorname)
        val name: String get() = (sauber(vorname).substringBefore(' ') + " " + sauber(nachname)).trim()
    }

    @Serializable
    data class SenatDatei(val stand: String, val quelle: String = "", val von: String = "", val meldungen: List<SenatMeldung> = emptyList())

    @Serializable
    data class RMeldung(
        val datei: String,
        val unterschrieben: String,
        val eingang: String = "",
        val seiten: Int = 0,
        val zeilen: Int = 0,
        val ausgelassen: Int = 0,
        val geschaefte: List<G> = emptyList(),
    )

    @Serializable
    data class BPos(val n: String, val k: String? = null, val t: String = "OT", val von: Long, val bis: Long? = null) {
        // Mitte der Spanne als Schaetzwert, bei offener Spanne die Untergrenze.
        val mitte: Double get() = if (bis == null) von.toDouble() else (von + bis) / 2.0
    }

    @Serializable
    data class Bestand(val stichtag: String, val datei: String = "", val positionen: List<BPos> = emptyList())

    @Serializable
    data class RPerson(val id: String, val name: String, val meldungen: List<RMeldung> = emptyList(), val bestand: Bestand? = null)

    @Serializable
    data class RDatei(val stand: String, val quelle: String = "", val personen: List<RPerson> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    fun senat(text: String): SenatDatei? = runCatching { json.decodeFromString<SenatDatei>(text) }.getOrNull()

    // Liste der Offenlegungen des Weissen Hauses; dort erscheint jede Transaktionsmeldung des Praesidenten als PDF.
    const val WEISSES_HAUS = "https://www.whitehouse.gov/disclosures/"

    // Zahl der Meldungen einer Person in dieser Liste: Transaktionsmeldungen (278-T) und Jahresberichte,
    // erkannt am Nachnamen name im Dateinamen; null, wenn keine darin steht.
    fun weissesHausMeldungen(html: String, name: String): Int? =
        Regex("href=\"([^\"]+\\.pdf)\"", RegexOption.IGNORE_CASE).findAll(html).map { it.groupValues[1] }
            .filter { it.contains(name, true) && (it.contains("Periodic-Transaction-Report", true) || it.contains("Annual-Report", true)) }
            .toSet().size.takeIf { it > 0 }
    fun regierung(text: String): RDatei? = runCatching { json.decodeFromString<RDatei>(text) }.getOrNull()

    // Meldungen eines Regierungsmitglieds bei der OGE (seit 5.9): Die OGE stellt neue 278-T oft Wochen vor
    // whitehouse.gov ein. Die Suche der OGE liefert JSON mit Datum der Aufnahme, Art und Link zum PDF.
    data class OgeMeldung(val tag: String, val art: String, val pdf: String)

    fun ogeAdresse(nachname: String) =
        "https://extapps2.oge.gov/201/Presiden.nsf/API.xsp/v3/rest?draw=1&start=0&length=100&search%5Bvalue%5D=" + java.net.URLEncoder.encode(nachname, "UTF-8")

    // Eintraege der Person („Trump, Donald J.“), neueste zuerst; nur Transaktionsmeldungen und Jahresberichte.
    fun ogeMeldungen(text: String, nachname: String, vorname: String): List<OgeMeldung> = runCatching {
        val daten = json.parseToJsonElement(text).jsonObject["data"]?.jsonArray ?: return emptyList()
        daten.mapNotNull { e ->
            val o = e.jsonObject
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            val name = s("name")
            if (!name.startsWith("$nachname, $vorname", true)) return@mapNotNull null
            val typ = s("type")
            val pdf = Regex("href='([^']+\\.pdf)'", RegexOption.IGNORE_CASE).find(typ)?.groupValues?.get(1) ?: return@mapNotNull null
            val art = typ.replace(Regex("<[^>]+>"), "").substringBefore('(').trim()
            if (!art.contains("Transaction", true) && !art.contains("Annual", true)) return@mapNotNull null
            OgeMeldung(s("docDate").take(10), art, pdf)
        }.sortedByDescending { it.tag }
    }.getOrDefault(emptyList())

    // Kennung einer OGE-Datei (die ersten acht Zeichen nach „PAS+Index/“), gleich fuer gekuerzte und volle Adressen.
    fun ogeKennung(url: String): String? = Regex("PAS\\+Index/([0-9A-Fa-f]{8})").find(url)?.groupValues?.get(1)?.uppercase()

    // Meldungen bei der OGE, die im mitgelieferten Stand noch fehlen.
    fun ogeNeu(oge: List<OgeMeldung>, p: RPerson): List<OgeMeldung> {
        val bekannt = p.meldungen.mapNotNull { ogeKennung(it.datei) }.toSet()
        return oge.filter { m -> ogeKennung(m.pdf)?.let { it !in bekannt } ?: false }
    }

    private fun sauber(s: String) = s.replace(Regex("\\s+"), " ").trim().trim(',', ' ')

    // Datum JJJJ-MM-TT als Mitternacht UTC; 0 bei unlesbarem Datum.
    fun tag(iso: String): Long = runCatching { LocalDate.parse(iso.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)

    // Art des Werts als Kuerzel wie beim Repraesentantenhaus.
    fun typ(t: String): String = when (t.trim()) {
        "Stock" -> "ST"
        "Stock Option" -> "OP"
        "Corporate Bond" -> "CS"
        "Municipal Security" -> "MB"
        "ST", "OP", "EF", "CS", "GS", "MB", "OT" -> t.trim()
        else -> "OT"
    }

    fun art(a: String): Kongress.Art = when (a) {
        "K" -> Kongress.Art.KAUF
        "V" -> Kongress.Art.VERKAUF
        "T" -> Kongress.Art.TEILVERKAUF
        else -> Kongress.Art.TAUSCH
    }

    // Kuerzel, wenn die Meldung eins nennt („--“ heisst keins).
    fun kuerzel(k: String?): String? = k?.trim()?.takeIf { it.isNotEmpty() && it.any { c -> c.isLetter() } && !it.startsWith("-") }

    fun geschaeft(g: G, gemeldet: Long): Kongress.Geschaeft =
        Kongress.Geschaeft(g.i, g.n, kuerzel(g.k), typ(g.t), art(g.a), tag(g.d), gemeldet, g.von, g.bis)

    // Senat

    data class Person(val schluessel: String, val name: String, val meldungen: List<SenatMeldung>) {
        val letzte: Long get() = meldungen.maxOfOrNull { tag(it.eingang) } ?: 0L
        val geschaefte: Int get() = meldungen.sumOf { it.geschaefte.size }
    }

    // Personen mit Meldungen, aktivste der letzten zwoelf Monate zuerst.
    fun personen(d: SenatDatei, jetzt: Long): List<Person> {
        val grenze = jetzt - 365L * 86_400_000L
        return d.meldungen.groupBy { it.person }.map { (k, l) ->
            Person(k, l.maxBy { tag(it.eingang) }.name, l.sortedByDescending { tag(it.eingang) })
        }.sortedWith(
            compareByDescending<Person> { p -> p.meldungen.filter { tag(it.eingang) >= grenze }.sumOf { it.geschaefte.size } }
                .thenByDescending { it.letzte },
        )
    }

    // Kopf einer Senatsmeldung im Format der Seiten des Repraesentantenhauses.
    fun kopf(m: SenatMeldung): Kongress.Meldung {
        val t = tag(m.eingang)
        return Kongress.Meldung(m.id, sauber(m.nachname), sauber(m.vorname), "", "", t, LocalDate.parse(m.eingang.ifBlank { "1970-01-01" }).year)
    }

    // Meldungen mit Geschaeften; Papiermeldungen haben keine (null).
    fun daten(meldungen: List<SenatMeldung>): List<Pair<Kongress.Meldung, List<Kongress.Geschaeft>?>> =
        meldungen.sortedByDescending { tag(it.eingang) }.map { m ->
            kopf(m) to if (m.papier) null else m.geschaefte.map { geschaeft(it, tag(m.eingang)) }
        }

    // Regierung

    fun kopf(p: RPerson, m: RMeldung, nr: Int): Kongress.Meldung {
        val datum = tag(m.eingang.ifBlank { m.unterschrieben })
        return Kongress.Meldung("r-${p.id}-$nr", p.name, "", "", "", datum, LocalDate.parse(m.unterschrieben.ifBlank { "1970-01-01" }).year)
    }

    fun daten(p: RPerson): List<Pair<Kongress.Meldung, List<Kongress.Geschaeft>?>> =
        p.meldungen.mapIndexed { i, m -> kopf(p, m, i) to m.geschaefte.map { geschaeft(it, tag(m.eingang.ifBlank { m.unterschrieben })) } }
            .sortedByDescending { it.first.datum }

    // Gewichte zum Nachbilden eines Bestands: Werte mit Kuerzel nach der Mitte ihrer Spanne, hoechstens anzahl.
    fun gewichte(b: Bestand, anzahl: Int): List<Pair<String, Double>> {
        val mit = b.positionen.filter { kuerzel(it.k) != null && it.mitte > 0 }.sortedByDescending { it.mitte }.take(anzahl)
        val summe = mit.sumOf { it.mitte }
        if (summe <= 0) return emptyList()
        return mit.map { kuerzel(it.k)!! to it.mitte / summe }
    }
}
