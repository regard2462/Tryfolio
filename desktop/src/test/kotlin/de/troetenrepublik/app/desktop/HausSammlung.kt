package de.troetenrepublik.app.desktop

import de.troetenrepublik.app.market.HausDaten
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.PdfText
import de.troetenrepublik.app.market.Vermoegen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// Baut copytrading/haus.json und copytrading/vermoegen.json: alle Transaktionsmeldungen des Repraesentantenhauses seit
// 2024 und die neuesten Jahresmeldungen. Laeuft nur mit TROETE_HAUS=<Arbeitsordner>; die PDFs bleiben dort liegen,
// ein zweiter Lauf laedt nur Neues. Danach haus.json und vermoegen.json nach app/src/main/assets/copytrading kopieren.
class HausSammlung {

    private val api = MarketApi()
    private val gleichzeitig = Semaphore(3)

    private suspend fun pdf(ordner: File, url: String, name: String): ByteArray? {
        val f = File(ordner, name)
        if (f.exists() && f.length() > 0) return f.readBytes()
        val b = gleichzeitig.withPermit { api.datei(url, 20_000_000) } ?: return null
        f.writeBytes(b)
        return b
    }

    private fun feld(b: String, n: String) = Regex("<$n>(.*?)</$n>", RegexOption.DOT_MATCHES_ALL).find(b)?.groupValues?.get(1)?.trim().orEmpty()

    @Test
    fun sammeln() = runBlocking {
        val wurzel = System.getenv("TROETE_HAUS")
        assumeTrue("nur mit TROETE_HAUS", !wurzel.isNullOrBlank())
        val ordner = File(wurzel).apply { mkdirs() }
        val ptr = File(ordner, "ptr").apply { mkdirs() }
        val jahrOrdner = File(ordner, "jahr").apply { mkdirs() }
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val xmls = (2024..heute.year).associateWith { j ->
            val f = File(ordner, "${j}FD.xml")
            val x = api.datei(Kongress.indexAdresse(j), 64_000_000)?.let { Kongress.xmlAusZip(it) }
            x?.also { f.writeText(it) } ?: f.takeIf { it.exists() }?.readText()
        }
        val meldungen = xmls.values.filterNotNull().flatMap { Kongress.verzeichnis(it) }.distinctBy { it.docId }
        println("Meldungen: ${meldungen.size}, elektronisch ${meldungen.count { it.elektronisch }}")
        val liste = meldungen.map { m ->
            async {
                val g = if (!m.elektronisch) null
                else pdf(ptr, Kongress.meldungAdresse(m.jahr, m.docId), "${m.docId}.pdf")?.let { Kongress.geschaefte(PdfText.zeilen(it)) }
                HausDaten.M(
                    m.docId, m.nachname, m.vorname, m.wahlkreis, HausDaten.iso(m.datum), m.jahr,
                    p = !m.elektronisch, g = g.orEmpty().map { HausDaten.g(it) },
                )
            }
        }.awaitAll()
        val ohne = liste.count { !it.p && it.g.isEmpty() }
        println("ohne Geschaefte: $ohne, Geschaefte: ${liste.sumOf { it.g.size }}")
        File(ordner, "haus.json").writeText(HausDaten.schreiben(HausDaten.Datei(1, heute.toString(), liste.sortedByDescending { it.d })))

        // Jahresmeldungen: je Person die neueste elektronische (Typ O, Nummernkreis 1)
        val jahres = xmls.flatMap { (j, x) ->
            if (x == null) emptyList() else Regex("<Member>(.*?)</Member>", RegexOption.DOT_MATCHES_ALL).findAll(x).mapNotNull { mm ->
                val b = mm.groupValues[1]
                if (feld(b, "FilingType") != "O") return@mapNotNull null
                val doc = feld(b, "DocID").takeIf { it.matches(Regex("^1\\d{7}$")) } ?: return@mapNotNull null
                Vermoegen.Kopf(doc, feld(b, "Last"), feld(b, "First"), feld(b, "StateDst"), feld(b, "Year").toIntOrNull() ?: j, feld(b, "FilingDate"))
            }.toList()
        }
        val neueste = jahres.groupBy { "${it.nachname}|${it.vorname}|${it.wahlkreis.take(2)}" }.values.map { l -> l.maxBy { it.jahr * 100_000_000L + it.docId.toLong() } }
        println("Jahresmeldungen: ${neueste.size}")
        val vermoegen = neueste.map { k ->
            async {
                val b = pdf(jahrOrdner, "https://disclosures-clerk.house.gov/public_disc/financial-pdfs/${k.jahr}/${k.docId}.pdf", "${k.docId}.pdf")
                b?.let { Vermoegen.auswerten(k, PdfText.zeilen(it)) }
            }
        }.awaitAll().filterNotNull()
        println("Vermoegen ausgewertet: ${vermoegen.size}")
        File(ordner, "vermoegen.json").writeText(Vermoegen.schreiben(Vermoegen.Datei(1, heute.toString(), vermoegen)))
    }
}
