package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Wirtschaftsdaten je Land aus dem DataMapper des IWF (World Economic Outlook), frei und ohne Schluessel.
// Antwort: {"values": {"<Kennung>": {"<ISO3>": {"<Jahr>": Zahl, ...}, ...}, ...}, "api": {...}}. Neben Laendern stehen
// dort Gruppen (EURO, WE, ...) und die Welt (WEOWORLD); Kosovo heisst UVK, Westjordanland und Gaza WBG.
object Imf {
    const val WACHSTUM = "NGDP_RPCH"
    const val INFLATION = "PCPIPCH"
    const val ARBEITSLOS = "LUR"
    const val SCHULDEN = "GGXWDG_NGDP"
    const val LEISTUNG = "BCA_NGDPD"
    const val BIP = "NGDPD"
    // [Globus Wirtschaft] BIP je Kopf in Dollar und kaufkraftbereinigt, Haushaltssaldo in % des BIP, Bevoelkerung in Millionen,
    // Anteil am Welt-BIP (Kaufkraft) in %
    const val BIP_KOPF = "NGDPDPC"
    const val BIP_KOPF_KKP = "PPPPC"
    const val HAUSHALT = "GGXCNL_NGDP"
    const val BEVOELKERUNG = "LP"
    const val WELT_ANTEIL = "PPPSH"
    val INDIKATOREN = listOf(WACHSTUM, INFLATION, ARBEITSLOS, SCHULDEN, LEISTUNG, BIP, BIP_KOPF, BIP_KOPF_KKP, HAUSHALT, BEVOELKERUNG, WELT_ANTEIL)

    // Stand der gespeicherten Datei: 2 mit den fuenf Reihen oben, 3 mit Jahren ab 2000. Aeltere Staende werden einmal neu geladen.
    const val STAND = 3

    // Eine Abfrage fuer alle Kennungen, mit allen Jahren (periods wirkt nicht).
    val ADRESSE = "https://www.imf.org/external/datamapper/api/v1/" + INDIKATOREN.joinToString("/")

    // Kennung der Welt in den Daten, unter dieser Kennung im Datensatz
    const val WELT = "WEOWORLD"

    // Natural Earth nennt Kosovo KOS und Palaestina PSE, der IWF UVK und WBG.
    private val IWF_ZU_KARTE = mapOf("UVK" to "KOS", "WBG" to "PSE")

    // Ab 2000: Zeitregler des Globus und langjaehriger Trend der Konjunkturphase
    const val ERSTES_JAHR = 2000

    // Kennung -> Land (Kuerzel der Karte) -> Jahr -> Wert
    @Serializable
    data class Datensatz(val abgerufen: Long = 0L, val werte: Map<String, Map<String, Map<Int, Double>>> = emptyMap(), val stand: Int = 0)

    private val json = Json { ignoreUnknownKeys = true }

    // Liest die Antwort. gueltig: die Laenderkuerzel der Karte; alles andere (Gruppen) faellt weg, die Welt bleibt.
    // null, wenn nichts Brauchbares darin steht.
    fun lesen(text: String, abgerufen: Long, gueltig: Set<String>): Datensatz? {
        val wurzel = runCatching { json.parseToJsonElement(text).jsonObject["values"] as? JsonObject }.getOrNull() ?: return null
        val aus = HashMap<String, Map<String, Map<Int, Double>>>()
        for (kennung in INDIKATOREN) {
            val je = (wurzel[kennung] as? JsonObject) ?: continue
            val laender = HashMap<String, Map<Int, Double>>()
            for ((code, reihe) in je) {
                val ziel = IWF_ZU_KARTE[code] ?: code
                if (ziel != WELT && ziel !in gueltig) continue
                val jahre = HashMap<Int, Double>()
                for ((jahr, wert) in (reihe as? JsonObject).orEmpty()) {
                    val j = jahr.toIntOrNull() ?: continue
                    if (j < ERSTES_JAHR) continue
                    val d = runCatching { wert.jsonPrimitive.doubleOrNull }.getOrNull() ?: continue
                    jahre[j] = d
                }
                if (jahre.isNotEmpty()) laender[ziel] = jahre
            }
            if (laender.isNotEmpty()) aus[kennung] = laender
        }
        if (aus[WACHSTUM].isNullOrEmpty()) return null
        return Datensatz(abgerufen, aus, STAND)
    }

    fun schreiben(d: Datensatz): String = json.encodeToString(Datensatz.serializer(), d)

    fun lesenGespeichert(text: String): Datensatz? = runCatching { json.decodeFromString(Datensatz.serializer(), text) }.getOrNull()
        ?.takeIf { it.werte[WACHSTUM].orEmpty().isNotEmpty() }

    // Wie lange ein Abruf gilt: ein Tag.
    const val GUELTIG_MS = 24 * 3_600_000L

    fun veraltet(d: Datensatz?, jetzt: Long): Boolean = d == null || d.stand < STAND || jetzt - d.abgerufen >= GUELTIG_MS || jetzt < d.abgerufen

    // Zahlen eines Landes fuer ein Jahr (jahr) und das folgende.
    data class Land(
        val wachstum: Double?,
        val wachstumNaechstes: Double?,
        val inflation: Double?,
        val arbeitslosigkeit: Double?,
        val schulden: Double?,
        val leistungsbilanz: Double?,
        // Wirtschaftsleistung in Milliarden Dollar
        val bip: Double?,
        // [Globus Wirtschaft]
        val bipKopf: Double? = null,
        val bipKopfKkp: Double? = null,
        val haushalt: Double? = null,
        // Bevoelkerung in Millionen
        val bevoelkerung: Double? = null,
        val weltAnteil: Double? = null,
    ) {
        // Aenderung des Wachstums im naechsten Jahr in Prozentpunkten
        val ausblick: Double? get() = if (wachstum != null && wachstumNaechstes != null) wachstumNaechstes - wachstum else null
        val leer: Boolean get() = wachstum == null && wachstumNaechstes == null && inflation == null && arbeitslosigkeit == null &&
            schulden == null && leistungsbilanz == null
    }

    fun wert(d: Datensatz, kennung: String, land: String, jahr: Int): Double? = d.werte[kennung]?.get(land)?.get(jahr)

    fun land(d: Datensatz, code: String, jahr: Int): Land = Land(
        wert(d, WACHSTUM, code, jahr), wert(d, WACHSTUM, code, jahr + 1), wert(d, INFLATION, code, jahr),
        wert(d, ARBEITSLOS, code, jahr), wert(d, SCHULDEN, code, jahr), wert(d, LEISTUNG, code, jahr), wert(d, BIP, code, jahr),
        wert(d, BIP_KOPF, code, jahr), wert(d, BIP_KOPF_KKP, code, jahr), wert(d, HAUSHALT, code, jahr),
        wert(d, BEVOELKERUNG, code, jahr), wert(d, WELT_ANTEIL, code, jahr),
    )

    fun laenderCodes(d: Datensatz): Set<String> = d.werte[WACHSTUM].orEmpty().keys - WELT

    // Aussage zu einem Land, aus den Zahlen. a und b: die Zahlen, die der Satz nennt.
    enum class Art { REZESSION, REZESSION_FOLGEJAHR, ZIEHT_AN, SCHWAECHT_AB, STAGNATION, DEFLATION, HOHE_INFLATION, ERHOEHTE_INFLATION,
        HOHE_ARBEITSLOSIGKEIT, HOHE_SCHULDEN, UEBERSCHUSS, DEFIZIT }

    data class Hinweis(val art: Art, val a: Double, val b: Double = 0.0)

    const val SCHWELLE_AUSBLICK = 0.5
    const val SCHWELLE_INFLATION_HOCH = 10.0
    const val SCHWELLE_INFLATION_ERHOEHT = 5.0
    const val SCHWELLE_ARBEITSLOSIGKEIT = 10.0
    const val SCHWELLE_SCHULDEN = 100.0
    const val SCHWELLE_LEISTUNGSBILANZ = 5.0

    // Kurze Einordnung in der Reihenfolge ihrer Wichtigkeit, hoechstens `max`.
    fun einordnen(l: Land, max: Int = 4): List<Hinweis> {
        val h = ArrayList<Hinweis>()
        val w = l.wachstum
        val n = l.wachstumNaechstes
        if (w != null && w < 0) h += Hinweis(Art.REZESSION, w)
        else if (n != null && n < 0) h += Hinweis(Art.REZESSION_FOLGEJAHR, n)
        else if (w != null && w < 1.0 && (n == null || n < 1.0)) h += Hinweis(Art.STAGNATION, w)
        if (w != null && n != null && h.none { it.art == Art.REZESSION || it.art == Art.REZESSION_FOLGEJAHR }) {
            if (n - w >= SCHWELLE_AUSBLICK) h += Hinweis(Art.ZIEHT_AN, w, n)
            else if (n - w <= -SCHWELLE_AUSBLICK) h += Hinweis(Art.SCHWAECHT_AB, w, n)
        }
        val i = l.inflation
        if (i != null) when {
            i >= SCHWELLE_INFLATION_HOCH -> h += Hinweis(Art.HOHE_INFLATION, i)
            i >= SCHWELLE_INFLATION_ERHOEHT -> h += Hinweis(Art.ERHOEHTE_INFLATION, i)
            i < 0 -> h += Hinweis(Art.DEFLATION, i)
        }
        l.arbeitslosigkeit?.let { if (it >= SCHWELLE_ARBEITSLOSIGKEIT) h += Hinweis(Art.HOHE_ARBEITSLOSIGKEIT, it) }
        l.schulden?.let { if (it >= SCHWELLE_SCHULDEN) h += Hinweis(Art.HOHE_SCHULDEN, it) }
        l.leistungsbilanz?.let {
            if (it >= SCHWELLE_LEISTUNGSBILANZ) h += Hinweis(Art.UEBERSCHUSS, it)
            else if (it <= -SCHWELLE_LEISTUNGSBILANZ) h += Hinweis(Art.DEFIZIT, it)
        }
        return h.take(max)
    }

    // Weltuebersicht
    // von und bis: Wachstum im Jahr und im Folgejahr, nur bei Aufsteigern und Abschwaechern
    data class Rang(val code: String, val wert: Double, val von: Double? = null, val bis: Double? = null)

    data class Ueberblick(
        val jahr: Int,
        val welt: Double?,
        val weltNaechstes: Double?,
        // Weltwachstum von jahr an, soweit vorhanden (hoechstens sechs Jahre)
        val pfad: List<Double>,
        val laender: Int,
        val rezession: Int,
        // Anteil der Laender mit Rezession an der Wirtschaftsleistung aller Laender mit Zahlen (0 bis 1), null ohne BIP
        val rezessionAnteil: Double?,
        val aufsteiger: List<Rang>,
        val abschwaecher: List<Rang>,
        val hoheInflation: List<Rang>,
        val hoheInflationAnzahl: Int,
        val schuldenSchwach: List<Rang>,
        val schuldenSchwachAnzahl: Int,
    )

    // Nur Laender ab dieser Wirtschaftsleistung (Milliarden Dollar) zaehlen bei Aufsteigern und Abschwaechern, sonst
    // bestimmen Kleinstaaten mit grossen Spruengen die Liste.
    const val MIN_BIP = 25.0
    const val SCHULDEN_WACHSTUM_MAX = 1.5

    fun ueberblick(d: Datensatz, jahr: Int, zeilen: Int = 3): Ueberblick {
        val laender = laenderCodes(d).associateWith { land(d, it, jahr) }.filterValues { !it.leer }
        val mitWachstum = laender.filterValues { it.wachstum != null }
        val rez = mitWachstum.filterValues { it.wachstum!! < 0 }
        val bipAlle = mitWachstum.values.sumOf { it.bip ?: 0.0 }
        val anteil = if (bipAlle > 0 && mitWachstum.values.any { it.bip != null }) rez.values.sumOf { it.bip ?: 0.0 } / bipAlle else null
        val gross = laender.filterValues { (it.bip ?: 0.0) >= MIN_BIP && it.ausblick != null }
        val hoch = laender.filterValues { (it.inflation ?: -1.0) >= SCHWELLE_INFLATION_HOCH }
        val schuld = laender.filterValues {
            (it.schulden ?: 0.0) >= SCHWELLE_SCHULDEN && (it.wachstum ?: 99.0) <= SCHULDEN_WACHSTUM_MAX
        }
        return Ueberblick(
            jahr = jahr,
            welt = wert(d, WACHSTUM, WELT, jahr),
            weltNaechstes = wert(d, WACHSTUM, WELT, jahr + 1),
            pfad = (0..5).map { wert(d, WACHSTUM, WELT, jahr + it) }.takeWhile { it != null }.filterNotNull(),
            laender = mitWachstum.size,
            rezession = rez.size,
            rezessionAnteil = anteil,
            aufsteiger = gross.map { Rang(it.key, it.value.ausblick!!, it.value.wachstum, it.value.wachstumNaechstes) }.filter { it.wert > 0 }.sortedByDescending { it.wert }.take(zeilen),
            abschwaecher = gross.map { Rang(it.key, it.value.ausblick!!, it.value.wachstum, it.value.wachstumNaechstes) }.filter { it.wert < 0 }.sortedBy { it.wert }.take(zeilen),
            hoheInflation = hoch.map { Rang(it.key, it.value.inflation!!) }.sortedByDescending { it.wert }.take(zeilen),
            hoheInflationAnzahl = hoch.size,
            schuldenSchwach = schuld.map { Rang(it.key, it.value.schulden!!) }.sortedByDescending { it.wert }.take(zeilen),
            schuldenSchwachAnzahl = schuld.size,
        )
    }
}
