package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

// [Grosses Ganzes 0.73] Einflussverzeichnis: verbindet Einflussgroessen (Zinsen, Oel, Engpaesse, Programme, Rohstoffe ...)
// mit Laendern, Branchen und einzelnen Werten. Jede Verbindung traegt ihre Art (gemeldet, gemessen, abgeleitet), einen
// Beleg, einen Stand und ein Ablaufdatum; abgelaufene Verbindungen fallen heraus. Grundlage sind die Dateien in
// assets/welt (ueber WeltDaten gelesen) und die bestehenden Kuerzellisten der Engpaesse, Spannungsgebiete und von ENSO.
// Branchen wirken auf alle Katalogwerte der Branche (abgeleitet), einzelne Werte nur, wenn sie genannt sind.
object Einflussverzeichnis {
    enum class Art { GEMESSEN, GEMELDET, ABGELEITET }

    enum class Typ { ZINS, ENERGIE, WAEHRUNG, NACHFRAGE, ZYKLUS, ROHSTOFF, ENGPASS, SPANNUNG, KLIMA, PROGRAMM, DURCHBRUCH, LAND }

    // PROFIL: Rohstoffe, die ein Unternehmen nach seinem Profil (Rohstoffprofil) betreffen, nicht nach der Branche
    enum class ZielArt { WERT, BRANCHE, LAND, PROFIL }

    data class Ziel(val art: ZielArt, val schluessel: String)

    // richtung: +1 steigt mit der Groesse, -1 faellt, 0 nur betroffen; im: Branchenverbindung gilt nur fuer Firmen in diesen
    // Laendern (null: ueberall); stand und ablauf in Tagen seit 1970, ablauf 0 = ohne
    class Kante(
        val groesse: String, val ziel: Ziel, val art: Art, val richtung: Int, val beleg: String,
        val stand: Long, val ablauf: Long, val im: Set<String>? = null,
        // [0.77] Beleg englisch (leer: nur deutsch vorhanden)
        val belegEn: String = "",
    ) {
        fun beleg(deutsch: Boolean) = if (deutsch || belegEn.isBlank()) beleg else belegEn
    }

    // de und en leer: der Name kommt aus den Texten der jeweiligen Seite (Engpass, Spannungsgebiet)
    class Groesse(
        val id: String, val typ: Typ, val de: String, val en: String, val kurzDe: String, val kurzEn: String,
        val stand: Long, val ablauf: Long,
    ) {
        // Kennung ohne Vorsatz (engpass:chokepoint6 -> chokepoint6)
        val kurz: String get() = id.substringAfter(':')
        fun name(deutsch: Boolean) = if (deutsch) de else en
        fun kette(deutsch: Boolean) = if (deutsch) kurzDe else kurzEn
    }

    // prozent: Anteil an der Weltfoerderung (0 = nicht veroeffentlicht)
    class Anteil(val land: String, val prozent: Int)

    class Rohstoff(
        val id: String, val de: String, val en: String, val kapitel: String, val laender: List<Anteil>,
        val kritisch: Boolean, val strategisch: Boolean, val kurs: String?,
        // [0.76] eigene Quelle und Jahr der Foerderanteile, wo sie nicht aus den USGS-Summaries stammen (Uran: World Nuclear Association)
        val quelle: String? = null, val quelleUrl: String? = null, val jahr: Int = 0,
        // [0.76] embargo: ein Embargo gegen Russland und Kasachstan trifft die Lieferkette, auch wenn die drei groessten Foerderlaender
        // unter HOHE_KONZENTRATION bleiben (Uran: Kasachstan, Russland und Usbekistan zusammen 51 Prozent der Foerderung)
        val embargo: Boolean = false,
        // [0.76] Weltfoerderung des Jahres mengenJahr in einheit (t, kg oder m3), die Foerdermengen je Land (laender sind die Anteile),
        // der Rest ohne Laenderangabe und der Preis des Jahres preisJahr in US-Dollar je Einheit (preisQuelle: Kuerzel der Preisquelle)
        val mengen: Mengen? = null,
    ) {
        fun name(deutsch: Boolean) = if (deutsch) de else en

        // Anteil der drei groessten Foerderlaender in Prozent; null ohne veroeffentlichte Anteile
        val konzentration: Int? get() =
            laender.map { it.prozent }.filter { it > 0 }.sortedDescending().take(3).sum().takeIf { it > 0 }
    }

    // Foerdermengen eines Rohstoffs: je Land die Menge in einheit, rest ohne Laenderangabe; preis in US-Dollar je Einheit (null: kein Preis)
    class Mengen(
        val einheit: String, val jahr: Int, val laender: List<LandMenge>, val rest: Double,
        val preis: Double?, val preisJahr: Int, val preisQuelle: String?,
        // Kuerzel der Quelle der Mengen (myb: USGS Minerals Yearbook, mcs: USGS Mineral Commodity Summaries); null in aelteren Staenden
        val mengenQuelle: String? = null,
    )

    class LandMenge(val land: String, val menge: Double)

    // Foerdermenge eines Unternehmens (Meldung des Unternehmens oder einer Statistik), Jahr dazu
    class FirmaMenge(val menge: Double, val jahr: Int)

    // Kapazitaet der Bergwerke eines Unternehmens bei einem Rohstoff, anteilig nach Eigentum, je Land (Einheit wie bei den Mengen
    // des Rohstoffs); verwertbar von gesamt Anlagen des Unternehmens haben eine Kapazitaetsangabe
    class Anlage(val menge: Double, val laender: List<LandMenge>, val jahr: Int, val verwertbar: Int, val gesamt: Int)

    class Index(
        val groessen: Map<String, Groesse>, val kanten: List<Kante>, val rohstoffe: Map<String, Rohstoff>,
        val branchenRohstoffe: Map<String, List<String>>, val rohstoffStand: Long, val rohstoffQuelle: String, val rohstoffJahr: Int,
        val stand: Long,
        // [0.76] Foerdermengen von Unternehmen: Kuerzel (Grossschreibung) -> Rohstoff -> Menge
        val firmenMengen: Map<String, Map<String, FirmaMenge>> = emptyMap(),
        // Kapazitaet der Bergwerke von Unternehmen: Kuerzel (Grossschreibung) -> Rohstoff -> Anlage
        val anlagen: Map<String, Map<String, Anlage>> = emptyMap(),
    )

    // Was ueber einen Wert bekannt ist: Kuerzel, Branche (Schluessel wie in Industry), Land fuer die Streuung und Sitz (alpha-2)
    // [0.76] industrie: feine Industrie (Yahoo) und beschreibung: Geschaeftsbeschreibung, wenn geladen; daraus bestimmt
    // Rohstoffprofil die Rohstoffe des Unternehmens
    class Wertbild(
        val symbol: String, val branche: String?, val land: String?, val sitz: String? = null,
        val industrie: String? = null, val beschreibung: String? = null,
    )

    class Verweis(val groesse: Groesse, val kante: Kante)

    // Eine Position im Depot oder auf der Watchlist; gewicht: Anteil am Depot (0 fuer Watchlist)
    class Bestand(val wert: Wertbild, val name: String, val gewicht: Double)

    class Treffer(val bestand: Bestand, val kante: Kante)

    val EU: Set<String> = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "PL", "PT", "RO", "SK", "SI", "ES", "SE",
    )

    // ISO alpha-3 zu alpha-2 fuer die Anrainer der Engpaesse und die Laender der Spannungsgebiete
    private val A3: Map<String, String> = mapOf(
        "IRN" to "IR", "OMN" to "OM", "ARE" to "AE", "YEM" to "YE", "DJI" to "DJ", "ERI" to "ER", "EGY" to "EG", "PAN" to "PA",
        "MYS" to "MY", "IDN" to "ID", "SGP" to "SG", "TWN" to "TW", "CHN" to "CN", "TUR" to "TR", "ESP" to "ES", "MAR" to "MA",
        "ZAF" to "ZA", "GBR" to "GB", "FRA" to "FR", "DNK" to "DK", "SWE" to "SE", "RUS" to "RU", "UKR" to "UA", "KOR" to "KR",
        "JPN" to "JP", "PHL" to "PH", "AUS" to "AU", "PNG" to "PG", "CUB" to "CU", "MEX" to "MX", "HTI" to "HT", "DOM" to "DO",
        "USA" to "US", "CHL" to "CL", "ARG" to "AR", "TLS" to "TL", "BLR" to "BY", "LBN" to "LB", "SYR" to "SY", "SAU" to "SA",
        "PRK" to "KP", "VNM" to "VN", "IND" to "IN", "PAK" to "PK", "POL" to "PL", "LTU" to "LT", "EST" to "EE", "LVA" to "LV",
        "VEN" to "VE", "GUY" to "GY", "SDN" to "SD", "SSD" to "SS", "MLI" to "ML", "BFA" to "BF", "NER" to "NE", "ISR" to "IL",
    )

    fun alpha2(a3: String): String? = A3[a3]

    @Serializable
    private class GruppeD(
        val art: String = "ABGELEITET", val r: Int = 0, val beleg: String = "",
        val branchen: List<String> = emptyList(), val laender: List<String> = emptyList(), val werte: List<String> = emptyList(),
        val im: List<String> = emptyList(), val stand: String = "", val ben: String = "",
    )

    @Serializable
    private class GroesseD(
        val id: String, val typ: String, val de: String = "", val en: String = "", val kd: String = "", val ke: String = "",
        val stand: String = "", val ablauf: String = "", val gruppen: List<GruppeD> = emptyList(),
    )

    @Serializable
    private class EinflussD(val v: Int = 1, val stand: String = "", val groessen: List<GroesseD> = emptyList())

    @Serializable
    private class AnteilD(val c: String, val p: Int = 0)

    @Serializable
    private class RohstoffD(
        val id: String, val de: String = "", val en: String = "", val u: String = "", val l: List<AnteilD> = emptyList(),
        val k: Int = 0, val s: Int = 0, val kurs: String? = null,
        val q: String? = null, val qu: String? = null, val j: Int = 0, val e: Int = 0,
        val me: String = "t", val mj: Int = 0, val lm: List<MengeD> = emptyList(), val ru: Double = 0.0,
        val pr: Double? = null, val pj: Int = 0, val pq: String? = null, val mq: String? = null,
    )

    @Serializable
    private class MengeD(val c: String, val m: Double = 0.0)

    @Serializable
    private class FirmaD(val r: String, val m: Double = 0.0, val j: Int = 0)

    @Serializable
    private class AnlageD(val m: Double = 0.0, val l: List<MengeD> = emptyList(), val n: Int = 0, val t: Int = 0)

    @Serializable
    private class AnlagenFirmaD(val s: List<String> = emptyList(), val r: Map<String, AnlageD> = emptyMap())

    @Serializable
    private class AnlagenD(val v: Int = 1, val jahr: Int = 0, val firmen: List<AnlagenFirmaD> = emptyList())

    @Serializable
    private class RohstoffeD(
        val v: Int = 1, val stand: String = "", val jahr: Int = 0, val quelle: String = "",
        val rohstoffe: List<RohstoffD> = emptyList(), val branchen: Map<String, List<String>> = emptyMap(),
        val firmen: Map<String, List<FirmaD>> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    // Mengen aus den Feldern der Datei; null, wenn die Datei sie nicht hat (aeltere Stande) oder die Weltmenge fehlt
    private fun mengenAus(x: RohstoffD): Mengen? {
        if (x.lm.isEmpty() && x.ru <= 0.0) return null
        val laender = x.lm.filter { it.m > 0.0 }.map { LandMenge(it.c, it.m) }
        return Mengen(x.me, x.mj, laender, x.ru.coerceAtLeast(0.0), x.pr?.takeIf { it > 0.0 }, x.pj, x.pq, x.mq)
    }

    // "2026-11-10" oder "2026-11" als Tag seit 1970; 0 bei Fehlern
    fun tag(text: String): Long = runCatching {
        val t = text.trim()
        if (t.length == 7) LocalDate.parse("$t-01").toEpochDay() else LocalDate.parse(t).toEpochDay()
    }.getOrDefault(0L)

    // Reihenfolge der Arten: gemessen vor gemeldet vor abgeleitet
    private fun artVon(s: String): Art = Art.entries.firstOrNull { it.name == s } ?: Art.ABGELEITET

    private fun ziele(g: GruppeD): List<Ziel> =
        g.branchen.map { Ziel(ZielArt.BRANCHE, it) } + g.laender.map { Ziel(ZielArt.LAND, it) } + g.werte.map { Ziel(ZielArt.WERT, it) }

    private fun imMenge(l: List<String>): Set<String>? =
        if (l.isEmpty()) null else l.flatMapTo(HashSet()) { if (it == "EU") EU else listOf(it) }

    // Der Index aus den mitgelieferten Dateien, einmal gebaut
    val geladen: Index by lazy { aufbauen(WeltDaten.text(WeltDaten.EINFLUSS), WeltDaten.text(WeltDaten.ROHSTOFFE), anlagen = WeltDaten.text(WeltDaten.ANLAGEN)) }

    // Baut den Index aus den Dateien (null: Datei fehlt) und den bestehenden Listen.
    fun aufbauen(
        einfluss: String?, rohstoffe: String?,
        engpaesse: List<Engpaesse.Stelle> = Engpaesse.STELLEN,
        spannungen: List<Spannungen.Gebiet> = Spannungen.GEBIETE,
        enso: List<String> = Enso.WERTE,
        durchbrueche: List<Durchbrueche.Thema> = Durchbrueche.THEMEN,
        anlagen: String? = null,
    ): Index {
        val e = einfluss?.let { runCatching { json.decodeFromString(EinflussD.serializer(), it) }.getOrNull() }
        val r = rohstoffe?.let { runCatching { json.decodeFromString(RohstoffeD.serializer(), it) }.getOrNull() }
        val listenStand = tag(e?.stand.orEmpty())
        val groessen = LinkedHashMap<String, Groesse>()
        val kanten = ArrayList<Kante>()

        fun groesse(id: String, typ: Typ, de: String = "", en: String = "", kd: String = "", ke: String = "", stand: Long = listenStand, ablauf: Long = 0L) {
            if (id !in groessen) groessen[id] = Groesse(id, typ, de, en, kd, ke, stand, ablauf)
        }

        // Bestehende Kuerzellisten: je Stelle, Gebiet und ENSO eine Groesse mit ihren Werten und Laendern
        for (s in engpaesse) {
            val id = "engpass:" + s.id
            groesse(id, Typ.ENGPASS)
            val beleg = "Handliste der Route (IWF PortWatch)"
            s.werte.forEach { kanten += Kante(id, Ziel(ZielArt.WERT, it), Art.ABGELEITET, 0, beleg, listenStand, 0L) }
            s.anrainer.mapNotNull { alpha2(it) }.forEach { kanten += Kante(id, Ziel(ZielArt.LAND, it), Art.ABGELEITET, 0, "Anrainer der Route", listenStand, 0L) }
        }
        for (g in spannungen) {
            val id = "spannung:" + g.id
            groesse(id, Typ.SPANNUNG)
            val beleg = "Handliste der Region (GDELT)"
            g.werte.forEach { kanten += Kante(id, Ziel(ZielArt.WERT, it), Art.ABGELEITET, 0, beleg, listenStand, 0L) }
            g.laender.mapNotNull { alpha2(it) }.forEach { kanten += Kante(id, Ziel(ZielArt.LAND, it), Art.ABGELEITET, 0, "Beteiligte Staaten", listenStand, 0L) }
        }
        groesse("klima:enso", Typ.KLIMA)
        enso.forEach { kanten += Kante("klima:enso", Ziel(ZielArt.WERT, it), Art.ABGELEITET, 0, "Handliste Agrarwerte (NOAA ENSO)", listenStand, 0L) }
        for (t in durchbrueche) {
            val id = "durchbruch:" + t.id
            groesse(id, Typ.DURCHBRUCH, t.de, t.en)
            t.etfs.forEach { kanten += Kante(id, Ziel(ZielArt.WERT, it), Art.ABGELEITET, 0, "Fonds zum Thema", listenStand, 0L) }
        }

        // Datei: Groessen und Verbindungen (Namen aus der Datei haben Vorrang vor leeren)
        for (g in e?.groessen.orEmpty()) {
            val typ = Typ.entries.firstOrNull { it.name == g.typ } ?: continue
            val stand = tag(g.stand).takeIf { it > 0 } ?: listenStand
            val ablauf = tag(g.ablauf)
            val alt = groessen[g.id]
            if (alt == null || alt.de.isBlank()) {
                groessen[g.id] = Groesse(g.id, typ, g.de.ifBlank { alt?.de.orEmpty() }, g.en.ifBlank { alt?.en.orEmpty() }, g.kd, g.ke, stand, ablauf)
            }
            for (gr in g.gruppen) {
                val gs = tag(gr.stand).takeIf { it > 0 } ?: stand
                for (z in ziele(gr)) kanten += Kante(g.id, z, artVon(gr.art), gr.r, gr.beleg, gs, ablauf, if (z.art == ZielArt.BRANCHE) imMenge(gr.im) else null, gr.ben)
            }
        }

        // Rohstoffe: Groesse je Rohstoff, Branchen und Foerderlaender als abgeleitete Verbindungen
        val rohstoffStand = tag(r?.stand.orEmpty())
        val roh = LinkedHashMap<String, Rohstoff>()
        for (x in r?.rohstoffe.orEmpty()) {
            roh[x.id] = Rohstoff(x.id, x.de, x.en, x.u, x.l.map { Anteil(it.c, it.p) }.sortedByDescending { it.prozent }, x.k == 1, x.s == 1, x.kurs, x.q, x.qu, x.j, x.e == 1, mengenAus(x))
            val id = "rohstoff:" + x.id
            groesse(id, Typ.ROHSTOFF, x.de, x.en, "", "", rohstoffStand)
            val quelle = r?.quelle.orEmpty()
            x.l.forEach { kanten += Kante(id, Ziel(ZielArt.LAND, it.c), Art.ABGELEITET, 0, quelle, rohstoffStand, 0L) }
            x.kurs?.let { kanten += Kante(id, Ziel(ZielArt.WERT, it), Art.ABGELEITET, 0, quelle, rohstoffStand, 0L) }
            // [0.76] Welche Werte ein Rohstoff trifft, entscheidet das Profil des Unternehmens (Rohstoffprofil)
            kanten += Kante(id, Ziel(ZielArt.PROFIL, x.id), Art.ABGELEITET, 0, "Geschaeftsmodell des Unternehmens, " + quelle, rohstoffStand, 0L)
        }
        for ((branche, liste) in r?.branchen.orEmpty()) {
            for (id in liste) if (id in roh) kanten += Kante("rohstoff:$id", Ziel(ZielArt.BRANCHE, branche), Art.ABGELEITET, 0, "Verwendung der Branche, " + r?.quelle.orEmpty(), rohstoffStand, 0L)
        }
        val firmen = r?.firmen.orEmpty().entries.associate { (s, l) -> s.uppercase() to l.associate { it.r to FirmaMenge(it.m, it.j) } }
        val an = anlagen?.let { runCatching { json.decodeFromString(AnlagenD.serializer(), it) }.getOrNull() }
        val kapazitaeten = HashMap<String, Map<String, Anlage>>()
        for (f in an?.firmen.orEmpty()) {
            val je = f.r.filterKeys { it in roh }.mapValues { (_, a) -> Anlage(a.m, a.l.filter { it.m > 0.0 }.map { LandMenge(it.c, it.m) }, an?.jahr ?: 0, a.n, a.t) }.filterValues { it.menge > 0.0 }
            if (je.isNotEmpty()) for (s in f.s) kapazitaeten[s.uppercase()] = je
        }
        return Index(groessen, kanten, roh, r?.branchen.orEmpty(), rohstoffStand, r?.quelle.orEmpty(), r?.jahr ?: 0, listenStand, firmen, kapazitaeten)
    }

    private fun gueltig(k: Kante, heute: Long) = k.ablauf == 0L || k.ablauf >= heute

    private fun passt(i: Index, k: Kante, w: Wertbild): Boolean = when (k.ziel.art) {
        ZielArt.WERT -> k.ziel.schluessel.equals(w.symbol, ignoreCase = true)
        // Rohstoffe haengen am Profil des Unternehmens; die Branchenverbindung der Rohstoffe dient nur der Anzeige
        ZielArt.BRANCHE -> !k.groesse.startsWith("rohstoff:") && w.branche != null && k.ziel.schluessel == w.branche &&
            (k.im == null || (w.land != null && w.land in k.im) || (w.sitz != null && w.sitz in k.im))
        ZielArt.PROFIL -> Rohstoffprofil.betrifft(i, w, k.ziel.schluessel)
        ZielArt.LAND -> false
    }

    // Verbindungen, die einen Wert treffen; abgelaufene fallen heraus. Je Groesse die staerkste (Wert vor Branche).
    fun fuerWert(i: Index, w: Wertbild, heute: Long): List<Verweis> {
        val je = LinkedHashMap<String, Verweis>()
        for (k in i.kanten) {
            if (!gueltig(k, heute) || !passt(i, k, w)) continue
            val g = i.groessen[k.groesse] ?: continue
            val alt = je[k.groesse]
            if (alt == null || (k.ziel.art == ZielArt.WERT && alt.kante.ziel.art != ZielArt.WERT)) je[k.groesse] = Verweis(g, k)
        }
        return je.values.sortedWith(compareBy({ it.groesse.typ.ordinal }, { it.kante.art.ordinal }, { it.groesse.id }))
    }

    // [0.77] Belege, die nur sagen, dass der Wert auf einer Liste steht, ohne den Weg der Wirkung zu nennen
    fun istListenbeleg(beleg: String) = beleg.startsWith("Handliste") || beleg == "Fonds zum Thema" || beleg == "Branche des Themas"

    // [0.77] Die Verbindung einer Groesse zu diesem Wert, die einen Weg nennt (Wert vor Branche, gemessene Art vor abgeleiteter);
    // null, wenn nur Listenbelege da sind
    fun wegKante(i: Index, w: Wertbild, groesseId: String, heute: Long): Kante? =
        i.kanten.filter { it.groesse == groesseId && gueltig(it, heute) && passt(i, it, w) && !istListenbeleg(it.beleg) && it.beleg.isNotBlank() }
            .minWithOrNull(compareBy({ if (it.ziel.art == ZielArt.WERT) 0 else 1 }, { it.art.ordinal }))

    // [0.75] Groessen, die Werte nur noch ueber abgelaufene Verbindungen treffen (einfluss.json ohne neue Fassung): Anzahl
    // und spaetester Ablauf in Tagen seit 1970. Die Karte nennt sie als Luecke, statt still kuerzer zu werden; erfunden
    // wird nichts. null, wenn keine. nimm: nur diese Verbindungen zaehlen (Szenarien: Groessen des Ereignisses,
    // Mitteilung: Engpaesse und Spannungsgebiete mit genanntem Wert).
    fun abgelaufen(i: Index, werte: List<Wertbild>, heute: Long, nimm: (Kante) -> Boolean = { true }): Pair<Int, Long>? {
        val gueltige = HashSet<String>()
        val alte = HashMap<String, Long>()
        for (k in i.kanten) {
            if (i.groessen[k.groesse]?.typ == Typ.ROHSTOFF || !nimm(k) || werte.none { passt(i, k, it) }) continue
            if (gueltig(k, heute)) gueltige += k.groesse else alte[k.groesse] = maxOf(alte[k.groesse] ?: 0L, k.ablauf)
        }
        val nur = alte.filterKeys { it !in gueltige }
        return if (nur.isEmpty()) null else nur.size to nur.values.max()
    }

    // [0.75] Groessen, die ganz abgelaufen sind (Ablauf vorbei, ohne neue einfluss.json): Anzahl und spaetester Ablauf
    // (Tage seit 1970), unabhaengig vom Depot. Das Wirkungsnetz blendet sie aus und sagt das. null, wenn keine.
    fun abgelaufeneGroessen(i: Index, heute: Long): Pair<Int, Long>? {
        val alte = i.groessen.values.filter { it.typ != Typ.ROHSTOFF && it.typ != Typ.LAND && it.ablauf != 0L && it.ablauf < heute }
        return if (alte.isEmpty()) null else alte.size to alte.maxOf { it.ablauf }
    }

    // Alle gueltigen Verbindungen einer Groesse
    fun kantenVon(i: Index, id: String, heute: Long): List<Kante> = i.kanten.filter { it.groesse == id && gueltig(it, heute) }

    // Laender, Branchen und Werte, die eine Groesse trifft
    fun laender(i: Index, id: String, heute: Long): List<String> =
        kantenVon(i, id, heute).filter { it.ziel.art == ZielArt.LAND }.map { it.ziel.schluessel }.distinct()

    fun branchen(i: Index, id: String, heute: Long): List<String> =
        kantenVon(i, id, heute).filter { it.ziel.art == ZielArt.BRANCHE }.map { it.ziel.schluessel }.distinct()

    fun werte(i: Index, id: String, heute: Long): List<String> =
        kantenVon(i, id, heute).filter { it.ziel.art == ZielArt.WERT }.map { it.ziel.schluessel }.distinct()

    // Welche Groessen treffen einen Wert (Umkehrabfrage ohne Sortierung nach Typ)
    fun groessenFuerWert(i: Index, w: Wertbild, heute: Long): List<Groesse> = fuerWert(i, w, heute).map { it.groesse }

    // Positionen, die eine Groesse trifft, mit der staerksten Verbindung; nach Depotanteil absteigend
    fun imDepot(i: Index, id: String, bestand: List<Bestand>, heute: Long): List<Treffer> {
        val kanten = kantenVon(i, id, heute)
        val aus = ArrayList<Treffer>()
        for (b in bestand) {
            val treffer = kanten.filter { passt(i, it, b.wert) }
            if (treffer.isEmpty()) continue
            val k = treffer.firstOrNull { it.ziel.art == ZielArt.WERT } ?: treffer.minWith(compareBy({ it.art.ordinal }, { it.stand }))
            aus += Treffer(b, k)
        }
        return aus.sortedByDescending { it.bestand.gewicht }
    }

    // Rohstoffe, die eine Branche braucht, in Reihenfolge der Datei
    fun rohstoffeFuerBranche(i: Index, branche: String): List<Rohstoff> =
        i.branchenRohstoffe[branche].orEmpty().mapNotNull { i.rohstoffe[it] }

    // [0.76] Rohstoffe, die ein Unternehmen braucht (Profil statt Branche); sec: Angaben aus Einreichungen, wenn geladen.
    // [0.77] Nur Einsatzstoffe: Eigene Erzeugnisse und blosse Nennungen sind kein Bedarf.
    fun rohstoffeFuerWert(i: Index, w: Wertbild, sec: Rohstoffprofil.SecAngaben? = null): List<Rohstoff> =
        Rohstoffprofil.fuer(i, w, sec).treffer.filter { it.einsatz }.mapNotNull { i.rohstoffe[it.id] }

    // Groesse eines Rohstoffs nach seinem Kuerzel am Terminmarkt (HG=F -> Kupfer)
    fun rohstoffFuerKurs(i: Index, symbol: String): Rohstoff? = i.rohstoffe.values.firstOrNull { it.kurs.equals(symbol, ignoreCase = true) }

    // Rohstoffbindung einer Branche, 0 bis 100: 100 minus 12,5 je Rohstoff der Branche, dessen drei groesste Foerderlaender
    // zusammen mindestens 80 % der Weltfoerderung stellen (abgeleitet aus USGS, nicht gemessen). null, wenn fuer die Branche
    // keine Rohstoffe erfasst sind.
    const val HOHE_KONZENTRATION = 80
    fun rohstoffBindung(i: Index, branche: String?): Double? {
        if (branche == null) return null
        return bindung(rohstoffeFuerBranche(i, branche))
    }

    // [0.76] Dieselbe Rechnung fuer die Rohstoffe des Unternehmens (Rohstoffprofil)
    fun rohstoffBindung(i: Index, w: Wertbild, sec: Rohstoffprofil.SecAngaben? = null): Double? = bindung(rohstoffeFuerWert(i, w, sec))

    private fun bindung(liste: List<Rohstoff>): Double? {
        if (liste.isEmpty()) return null
        return (100.0 - 12.5 * liste.count { (it.konzentration ?: 0) >= HOHE_KONZENTRATION }).coerceAtLeast(0.0)
    }

    // Wie weit ist der Stand her: Monate seit dem Stand; ab 18 grau, ab 30 nicht mehr zeigen
    fun alterMonate(stand: Long, heute: Long): Int = if (stand <= 0L) 0 else ((heute - stand) / 30L).toInt().coerceAtLeast(0)

    const val GRAU_AB_MONATE = 18
    const val AUSBLENDEN_AB_MONATE = 30
}
