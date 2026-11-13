package de.troetenrepublik.app.market

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

// Auftragsbestand aus den XBRL-Angaben der SEC (companyconcept, us-gaap:RevenueRemainingPerformanceObligation): der
// vertraglich zugesagte Umsatz, der noch nicht erfasst ist. Stand je Quartal aus 10-Q und 10-K, dazu der letzte
// Jahresumsatz fuer das Vielfache. Den Anteil, der innerhalb von zwoelf Monaten zu Umsatz wird, liefert die
// Schnittstelle ohne Zeitachse (die steht nur im Dokument), er bleibt deshalb weg.
object Auftragsbestand {
    const val BEGRIFF = "RevenueRemainingPerformanceObligation"
    val UMSATZ_BEGRIFFE = listOf(
        "RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues", "RevenueFromContractWithCustomerIncludingAssessedTax", "SalesRevenueNet",
    )

    private val FORMEN = setOf("10-K", "10-K/A", "10-Q", "10-Q/A", "20-F", "40-F")

    data class Punkt(val beginn: String, val ende: String, val wert: Double, val form: String, val eingang: String)

    // verlauf: die letzten Quartalsstaende, aelteste zuerst; vorjahr: Stand rund ein Jahr vor dem aktuellen
    data class Ergebnis(val aktuell: Punkt, val verlauf: List<Punkt>, val vorjahr: Punkt?, val umsatz: Double?, val umsatzJahr: Int?) {
        // Veraenderung zum Vorjahr in Prozent
        val aenderung: Double? get() = vorjahr?.takeIf { it.wert > 0 }?.let { (aktuell.wert / it.wert - 1) * 100 }

        // Auftragsbestand geteilt durch den Jahresumsatz
        val faktor: Double? get() = umsatz?.takeIf { it > 0 }?.let { aktuell.wert / it }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun adresse(cik: String, begriff: String): String =
        "https://data.sec.gov/api/xbrl/companyconcept/CIK" + cik.trimStart('0').padStart(10, '0') + "/us-gaap/$begriff.json"

    // Werte der Antwort in US-Dollar; leer bei Fehlern oder anderer Waehrung
    fun reihe(text: String): List<Punkt> = runCatching {
        json.parseToJsonElement(text).jsonObject["units"]!!.jsonObject["USD"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            Punkt(
                beginn = o["start"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                ende = o["end"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                wert = o["val"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                form = o["form"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                eingang = o["filed"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    private fun tag(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

    // Je Stichtag ein Wert (der zuletzt eingereichte), nur aus Jahres- und Quartalsberichten, aelteste zuerst
    fun staende(punkte: List<Punkt>): List<Punkt> =
        punkte.filter { it.form in FORMEN }.groupBy { it.ende }.map { (_, g) -> g.maxBy { it.eingang } }.sortedBy { it.ende }

    // Jahresumsatz: das letzte volle Geschaeftsjahr (340 bis 380 Tage), das hoechstens 15 Tage nach dem Stichtag endet
    // und nicht aelter als 400 Tage vor ihm ist
    fun jahresumsatz(punkte: List<Punkt>, stichtag: LocalDate): Punkt? =
        punkte.filter { it.form in FORMEN && it.beginn.isNotBlank() }.filter { p ->
            val b = tag(p.beginn) ?: return@filter false
            val e = tag(p.ende) ?: return@filter false
            val dauer = ChronoUnit.DAYS.between(b, e)
            val abstand = ChronoUnit.DAYS.between(e, stichtag)
            dauer in 340..380 && abstand in -15..400
        }.groupBy { it.ende }.map { (_, g) -> g.maxBy { it.eingang } }.maxByOrNull { it.ende }

    // Auswertung; null ohne Staende, bei Wert 0 oder wenn die Firma die Angabe seit mehr als 550 Tagen nicht mehr meldet.
    // umsatz: Reihe des ersten Umsatzbegriffs, den die Firma meldet (leer, wenn keiner da ist)
    fun auswerten(bestand: List<Punkt>, umsatz: List<Punkt>, heute: LocalDate): Ergebnis? {
        val s = staende(bestand)
        val aktuell = s.lastOrNull() ?: return null
        val ende = tag(aktuell.ende) ?: return null
        if (aktuell.wert <= 0 || ChronoUnit.DAYS.between(ende, heute) > 550) return null
        val vorjahrZiel = ende.minusYears(1)
        val vorjahr = s.filter { it !== aktuell }.minByOrNull { p -> tag(p.ende)?.let { abs(ChronoUnit.DAYS.between(it, vorjahrZiel)) } ?: Long.MAX_VALUE }
            ?.takeIf { p -> tag(p.ende)?.let { abs(ChronoUnit.DAYS.between(it, vorjahrZiel)) <= 25 } == true }
        val verlauf = s.filter { p -> tag(p.ende)?.let { ChronoUnit.DAYS.between(it, ende) <= 800 } == true }.takeLast(9)
        val jahr = jahresumsatz(umsatz, ende)
        return Ergebnis(aktuell, verlauf, vorjahr, jahr?.wert, jahr?.ende?.take(4)?.toIntOrNull())
    }

    private fun abs(v: Long) = if (v < 0) -v else v
}

// Abruf je Firma: zuerst der Auftragsbestand (404: meldet die Firma nicht, dann endet es), danach der Jahresumsatz ueber den
// ersten Begriff, der Werte liefert. Hoechstens fuenf kleine Abfragen, Ergebnisse bleiben zwoelf Stunden im Speicher, auch
// „keine Daten“; Netzfehler werden nicht gemerkt.
object AuftragsbestandAbruf {
    private val sperre = Mutex()
    private val speicher = ConcurrentHashMap<String, Pair<Long, Auftragsbestand.Ergebnis?>>()
    private const val GUELTIG_MS = 12 * 3_600_000L

    private class Fehler : Exception()

    private suspend fun reihe(cik: String, begriff: String, kennung: String): List<Auftragsbestand.Punkt> {
        val a = FirmaSec.anfrage(Auftragsbestand.adresse(cik, begriff), kennung, max = 1_000_000)
        return when {
            a.ok -> Auftragsbestand.reihe(a.text.orEmpty())
            a.code == 404 -> emptyList()
            else -> throw Fehler()
        }
    }

    suspend fun laden(cik: String, kennung: String, jetzt: Long = System.currentTimeMillis(), heute: LocalDate = LocalDate.now(java.time.ZoneOffset.UTC)): Auftragsbestand.Ergebnis? =
        sperre.withLock {
            val bekannt = speicher[cik]
            if (bekannt != null && jetzt - bekannt.first < GUELTIG_MS) return@withLock bekannt.second
            val ergebnis = try {
                val bestand = reihe(cik, Auftragsbestand.BEGRIFF, kennung)
                if (bestand.isEmpty()) null
                else {
                    var umsatz = emptyList<Auftragsbestand.Punkt>()
                    val ende = Auftragsbestand.staende(bestand).lastOrNull()?.ende?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    for (b in Auftragsbestand.UMSATZ_BEGRIFFE) {
                        val r = reihe(cik, b, kennung)
                        if (ende != null && Auftragsbestand.jahresumsatz(r, ende) != null) { umsatz = r; break }
                    }
                    Auftragsbestand.auswerten(bestand, umsatz, heute)
                }
            } catch (e: Fehler) {
                return@withLock null
            } catch (e: FirmaSec.Gesperrt) {
                return@withLock null
            }
            speicher[cik] = jetzt to ergebnis
            ergebnis
        }
}
