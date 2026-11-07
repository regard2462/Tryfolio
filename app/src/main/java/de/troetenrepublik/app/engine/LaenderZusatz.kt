package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

// Laenderblatt, Abschnitt Mehr: Gesellschaft, Staat, Energie, Handel, Feiertage und Schlagzeilen.
// Alles hier liest und rechnet nur; Abrufe und Anzeige stehen in AppState und ui/LaenderMehr.kt.
object LaenderZusatz {
    enum class Gruppe { GESELLSCHAFT, STAAT, ENERGIE }
    enum class Einheit { PROZENT, JAHRE, ZAHL, TONNEN }

    // Weltbank-Kennzahlen (kennung = null: kommt aus anderer Quelle oder wird gerechnet).
    // jahrImmer: Erhebungswerte, bei denen das Jahr immer dabeisteht. hoechstAlter: aeltere Werte fallen weg.
    enum class Kennzahl(val kennung: String?, val gruppe: Gruppe, val einheit: Einheit, val vorzeichen: Boolean = false, val jahrImmer: Boolean = false, val hoechstAlter: Int? = null) {
        LEBENSERWARTUNG("SP.DYN.LE00.IN", Gruppe.GESELLSCHAFT, Einheit.JAHRE),
        MEDIANALTER(null, Gruppe.GESELLSCHAFT, Einheit.JAHRE),
        STADT("SP.URB.TOTL.IN.ZS", Gruppe.GESELLSCHAFT, Einheit.PROZENT),
        WACHSTUM("SP.POP.GROW", Gruppe.GESELLSCHAFT, Einheit.PROZENT, vorzeichen = true),
        GEBURTEN("SP.DYN.TFRT.IN", Gruppe.GESELLSCHAFT, Einheit.ZAHL),
        ERWERB("SL.TLF.CACT.ZS", Gruppe.GESELLSCHAFT, Einheit.PROZENT),
        FRAUEN("SL.TLF.CACT.FE.ZS", Gruppe.GESELLSCHAFT, Einheit.PROZENT),
        ARMUT("SI.POV.DDAY", Gruppe.GESELLSCHAFT, Einheit.PROZENT, jahrImmer = true),
        INTERNET("IT.NET.USER.ZS", Gruppe.GESELLSCHAFT, Einheit.PROZENT),
        HOCHSCHULE("SE.TER.ENRR", Gruppe.GESELLSCHAFT, Einheit.PROZENT),
        ALPHA("SE.ADT.LITR.ZS", Gruppe.GESELLSCHAFT, Einheit.PROZENT, hoechstAlter = 12),
        STEUER("GC.TAX.TOTL.GD.ZS", Gruppe.STAAT, Einheit.PROZENT),
        MILITAER("MS.MIL.XPND.GD.ZS", Gruppe.STAAT, Einheit.PROZENT),
        FORSCHUNG("GB.XPD.RSDV.GD.ZS", Gruppe.STAAT, Einheit.PROZENT),
        UEBERWEISUNG("BX.TRF.PWKR.DT.GD.ZS", Gruppe.STAAT, Einheit.PROZENT),
        TOURISMUS(null, Gruppe.STAAT, Einheit.PROZENT),
        ERNEUERBAR("EG.FEC.RNEW.ZS", Gruppe.ENERGIE, Einheit.PROZENT),
        ENERGIEIMPORT("EG.IMP.CONS.ZS", Gruppe.ENERGIE, Einheit.PROZENT, vorzeichen = true),
        STROM("EG.ELC.ACCS.ZS", Gruppe.ENERGIE, Einheit.PROZENT),
        TREIBHAUS("EN.GHG.ALL.PC.CE.AR5", Gruppe.ENERGIE, Einheit.TONNEN),
    }

    // Hilfskennungen fuer die Tourismusquote: Reiseverkehr in % der Dienstleistungsexporte mal Dienstleistungsexporte
    // durch Exporte von Waren und Diensten (alle Zahlungsbilanz, US-Dollar).
    const val REISE_ANTEIL = "BX.GSR.TRVL.ZS"
    const val DIENST_EXPORT = "BX.GSR.NFSV.CD"
    const val GESAMT_EXPORT = "BX.GSR.GNFS.CD"

    // Alle Weltbank-Kennungen, die das Blatt braucht.
    val KENNUNGEN: List<String> = Kennzahl.entries.mapNotNull { it.kennung } + listOf(REISE_ANTEIL, DIENST_EXPORT, GESAMT_EXPORT)

    class Zeile(val kennzahl: Kennzahl, val wert: Double, val jahr: Int, val mitJahr: Boolean)

    // Tourismuseinnahmen in % der Exporte; nur, wenn alle drei Reihen dasselbe Jahr haben.
    fun tourismus(a3: String, reihen: Map<String, Weltbank.Reihe?>): Weltbank.Wert? {
        val r = reihen[REISE_ANTEIL]?.werte?.get(a3) ?: return null
        val d = reihen[DIENST_EXPORT]?.werte?.get(a3)?.takeIf { it.jahr == r.jahr } ?: return null
        val g = reihen[GESAMT_EXPORT]?.werte?.get(a3)?.takeIf { it.jahr == r.jahr && it.wert > 0 } ?: return null
        val v = r.wert / 100.0 * d.wert / g.wert * 100.0
        return if (v.isFinite() && v >= 0) Weltbank.Wert(r.jahr, v) else null
    }

    // Zeilen einer Gruppe in fester Reihenfolge; Kennzahlen ohne Wert fallen weg.
    // Das Jahr steht dabei, wenn der Wert aelter ist als der juengste der Reihe (ueber alle Laender) oder jahrImmer gilt.
    fun zeilen(gruppe: Gruppe, a3: String, reihen: Map<String, Weltbank.Reihe?>, medianalter: Double?, jetztJahr: Int): List<Zeile> =
        Kennzahl.entries.filter { it.gruppe == gruppe }.mapNotNull { k ->
            val w: Weltbank.Wert? = when (k) {
                Kennzahl.MEDIANALTER -> medianalter?.let { Weltbank.Wert(MEDIAN_JAHR, it) }
                Kennzahl.TOURISMUS -> tourismus(a3, reihen)
                else -> reihen[k.kennung]?.werte?.get(a3)
            }
            if (w == null || !w.wert.isFinite()) return@mapNotNull null
            if (k.hoechstAlter != null && jetztJahr - w.jahr > k.hoechstAlter) return@mapNotNull null
            val neuestes = when (k) {
                Kennzahl.MEDIANALTER -> MEDIAN_JAHR
                Kennzahl.TOURISMUS -> reihen[REISE_ANTEIL]?.werte?.values?.maxOfOrNull { it.jahr } ?: w.jahr
                else -> reihen[k.kennung]?.werte?.values?.maxOfOrNull { it.jahr } ?: w.jahr
            }
            Zeile(k, w.wert, w.jahr, mitJahr = k.jahrImmer || (k != Kennzahl.MEDIANALTER && w.jahr < neuestes))
        }

    const val MEDIAN_JAHR = 2025

    // ---- Handel ----

    // Grobe Warengruppen nach Zolltarif-Kapiteln (HS 2-Steller); Reihenfolge wie im Erzeugerskript.
    enum class Warengruppe(val kuerzel: String, val kapitel: String) {
        AGRAR("agr", "1-24"), BRENNSTOFF("brs", "27"), ERZ("erz", "26, 72-83"), EDEL("edl", "71"), PHARMA("pha", "30"),
        CHEMIE("che", "28-29, 31-38"), KUNSTSTOFF("kun", "39-40"), TEXTIL("tex", "41-43, 50-65"), HOLZ("hol", "44-49"),
        MASCHINEN("mas", "84"), ELEKTRO("ele", "85"), FAHRZEUG("fzg", "86-89"), INSTRUMENT("ins", "90-92"), SONSTIGE("son", "25, 66-70, 93-99");

        companion object { fun von(k: String): Warengruppe? = entries.firstOrNull { it.kuerzel == k } }
    }

    // Promille: Anteil an den Warenexporten des Landes.
    // spiegel: das Land meldet nichts, die Zahlen stammen aus den Importen seiner groessten Abnehmer.
    class Handel(val jahr: Int, val wert: Double, val gueter: List<Pair<Warengruppe, Int>>, val partner: List<Pair<String, Int>>, val spiegel: Boolean = false)

    // handel.json: {"l":{"DEU":{"j":2024,"w":1.6e12,"g":[["mas",150],...],"p":[["USA",100],...]}}} (Schluessel ISO alpha-3).
    fun handelLesen(text: String): Map<String, Handel> = runCatching {
        val l = Json.parseToJsonElement(text).jsonObject["l"]?.jsonObject ?: return emptyMap()
        val aus = HashMap<String, Handel>()
        for ((k, v) in l) {
            val o = v as? JsonObject ?: continue
            val j = o["j"]?.jsonPrimitive?.intOrNull ?: continue
            val g = (o["g"] as? JsonArray).orEmpty().mapNotNull { e ->
                val a = e as? JsonArray ?: return@mapNotNull null
                val w = Warengruppe.von(a.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null) ?: return@mapNotNull null
                w to (a.getOrNull(1)?.jsonPrimitive?.intOrNull ?: return@mapNotNull null)
            }
            val p = (o["p"] as? JsonArray).orEmpty().mapNotNull { e ->
                val a = e as? JsonArray ?: return@mapNotNull null
                (a.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null) to (a.getOrNull(1)?.jsonPrimitive?.intOrNull ?: return@mapNotNull null)
            }
            if (g.isEmpty() && p.isEmpty()) continue
            aus[k] = Handel(j, o["w"]?.jsonPrimitive?.doubleOrNull ?: 0.0, g.take(5), p.take(5), o["m"]?.jsonPrimitive?.intOrNull == 1)
        }
        aus
    }.getOrDefault(emptyMap())

    // ---- Steckbrief ----

    // Sprachen: Name englisch und deutsch; weitere = Zahl der nicht gezeigten Amtssprachen.
    class Steckbrief(val flaeche: Double?, val sprachen: List<Pair<String, String>>, val weitere: Int, val zonen: List<String>, val medianalter: Double?)

    // steckbrief.json: {"l":{"CH":{"f":41291,"s":[["German","Deutsch"],...,11],"z":["Europe/Zurich"],"m":42.9}}} (Schluessel ISO alpha-2).
    fun steckbriefLesen(text: String): Map<String, Steckbrief> = runCatching {
        val l = Json.parseToJsonElement(text).jsonObject["l"]?.jsonObject ?: return emptyMap()
        val aus = HashMap<String, Steckbrief>()
        for ((k, v) in l) {
            val o = v as? JsonObject ?: continue
            val s = (o["s"] as? JsonArray).orEmpty()
            val sprachen = s.mapNotNull { e ->
                val a = e as? JsonArray ?: return@mapNotNull null
                (a.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null) to (a.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null)
            }
            val weitere = s.lastOrNull().let { (it as? JsonPrimitive)?.intOrNull?.let { n -> n - sprachen.size } ?: 0 }.coerceAtLeast(0)
            val z = (o["z"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
            aus[k] = Steckbrief(o["f"]?.jsonPrimitive?.doubleOrNull, sprachen, weitere, z, o["m"]?.jsonPrimitive?.doubleOrNull)
        }
        aus
    }.getOrDefault(emptyMap())

    // Aktuelle UTC-Versaetze der Zonen (in Sekunden, aufsteigend, ohne Doppelte); unbekannte Kennungen fallen weg.
    fun versaetze(zonen: List<String>, jetzt: Long): List<Int> {
        val t = Instant.ofEpochMilli(jetzt)
        return zonen.mapNotNull { z -> runCatching { ZoneId.of(z).rules.getOffset(t).totalSeconds }.getOrNull() }.distinct().sorted()
    }

    // "UTC+1", "UTC+5:30", "UTC−3"
    fun utcText(sekunden: Int): String {
        if (sekunden == 0) return "UTC"
        val a = abs(sekunden)
        val h = a / 3600
        val m = a % 3600 / 60
        return "UTC" + (if (sekunden < 0) "−" else "+") + h + (if (m != 0) ":" + m.toString().padStart(2, '0') else "")
    }

    // ---- Feiertage ----

    class Feiertag(val datum: LocalDate, val name: String, val lokal: String)

    // Antwort von NextPublicHolidays: nur landesweit geltende gesetzliche Feiertage ab heute, die naechsten n.
    fun feiertage(text: String, heute: LocalDate, n: Int = 3): List<Feiertag> = runCatching {
        Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if (o["global"]?.jsonPrimitive?.booleanOrNull == false) return@mapNotNull null
            val typen = (o["types"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
            if (typen.isNotEmpty() && "Public" !in typen) return@mapNotNull null
            val d = runCatching { LocalDate.parse(o["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null) }.getOrNull() ?: return@mapNotNull null
            if (d.isBefore(heute)) return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: o["localName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Feiertag(d, name, o["localName"]?.jsonPrimitive?.contentOrNull ?: name)
        }.distinctBy { it.datum }.sortedBy { it.datum }.take(n)
    }.getOrDefault(emptyList())

    // Landessprachen, in denen der lokale Feiertagsname dem deutschen Namen entspricht.
    private val DEUTSCH = setOf("DE", "AT", "CH", "LI")
    fun feiertagName(f: Feiertag, a2: String, deutsch: Boolean): String = if (deutsch && a2 in DEUTSCH) f.lokal else f.name

    // ---- Zwischenspeicher und Schlagzeilen ----

    const val FEIERTAGE_MS = 24 * 3_600_000L
    const val MELDUNGEN_MS = 30 * 60_000L

    @Serializable
    class Meldung(val titel: String, val link: String, val zeit: Long, val quelle: String)

    @Serializable
    class Meldungen(val abgerufen: Long, val liste: List<Meldung>)

    @Serializable
    class Roh(val abgerufen: Long, val text: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun meldungenLesen(text: String): Meldungen? = runCatching { json.decodeFromString(Meldungen.serializer(), text) }.getOrNull()
    fun meldungenSchreiben(m: Meldungen): String = json.encodeToString(Meldungen.serializer(), m)
    fun rohLesen(text: String): Roh? = runCatching { json.decodeFromString(Roh.serializer(), text) }.getOrNull()
    fun rohSchreiben(r: Roh): String = json.encodeToString(Roh.serializer(), r)

    fun gueltig(abgerufen: Long, jetzt: Long, dauer: Long): Boolean = jetzt >= abgerufen && jetzt - abgerufen < dauer

    // Suchbegriff der Schlagzeilen: Landesname in App-Sprache plus Wirtschaft bzw. economy.
    fun suchbegriff(landName: String, deutsch: Boolean): String = "\"$landName\" " + if (deutsch) "Wirtschaft" else "economy"

    // Neueste zuerst, ohne gleiche Titel, nur mit Link.
    fun neueste(liste: List<Meldung>, n: Int = 5): List<Meldung> =
        liste.filter { it.titel.isNotBlank() && it.link.startsWith("http") }.distinctBy { it.titel.trim().lowercase() }.sortedByDescending { it.zeit }.take(n)
}
