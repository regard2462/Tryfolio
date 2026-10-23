package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Netto-Leerverkaufspositionen (seit 5.8). Frankreich: die Liste der Autorite des marches financiers (AMF) auf
// data.gouv.fr (Licence Ouverte 2.0); nach der EU-Leerverkaufsverordnung wird jede Position ab 0,5 % des Grundkapitals
// mit Halter veroeffentlicht. Der App liegt ein Stand bei (tools/amf/holen.py, assets/copytrading/leerpositionen_fr.json);
// neuere Daten rechnet sie mit derselben Auswertung aus der Datei.
// Seit 5.9 auch Schweden (Blankningsregister der Finansinspektionen, offene Daten; Stand mit Jahresverlauf aus
// tools/fi_blankning/holen.py, die App holt die aktuellen Positionen nach) und Grossbritannien (FCA, seit Juli 2026 nur
// noch die Summe je Aktie ohne Halter; nur zur Laufzeit von fca.org.uk, nichts mitgeliefert).
object Leerpositionen {
    const val ADRESSE = "https://www.data.gouv.fr/api/1/datasets/r/c2539d1c-8531-4937-9cba-3bd8e9786cc5"
    const val FI_AKTUELL = "https://www.fi.se/BlankningsRegister/GetAktuellFile"
    const val FCA_AKTUELL = "https://www.fca.org.uk/publication/documents/aggregated-current-net-short-positions.csv"
    const val FCA_HISTORISCH = "https://www.fca.org.uk/publication/documents/aggregated-historic-net-short-positions.csv"
    const val WOCHEN = 52

    // Laender mit eigener Liste: FR (AMF), SE (FI), GB (FCA), IT (Consob, nur mitgeliefert: tools/consob/leerverkauf.py).
    val LAENDER = listOf("FR", "SE", "GB", "IT")

    // Laender, deren Liste eine Aktie mit dieser ISIN fuehren koennte (zum Nachladen, bevor sie bekannt ist).
    fun landFuerIsin(isin: String): String? = when (isin.take(2)) {
        "FR" -> "FR"
        "SE" -> "SE"
        "GB", "JE", "GG", "IM" -> "GB"
        "IT" -> "IT"
        else -> null
    }

    // Halter, Anteil am Grundkapital in Prozent, Beginn der Position (JJJJ-MM-TT).
    @Serializable
    data class Position(val h: String, val r: Double, val d: String)

    // Summe der veroeffentlichten Positionen am Tag t.
    @Serializable
    data class Punkt(val t: String, val s: Double)

    // gesamt: veroeffentlichte Summe, wenn die Liste keine Halter nennt (FCA).
    @Serializable
    data class Titel(val e: String, val positionen: List<Position> = emptyList(), val verlauf: List<Punkt> = emptyList(), val gesamt: Double? = null) {
        val summe: Double get() = gesamt ?: positionen.sumOf { it.r }
    }

    @Serializable
    data class Datei(val stand: String = "", val titel: Map<String, Titel> = emptyMap(), val abgerufen: Long = 0L, val land: String = "FR")

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // Offene Positionen je Aktie und die Wochensummen des letzten Jahres bis heute (JJJJ-MM-TT).
    fun auswerten(text: String, heute: java.time.LocalDate): Map<String, Titel> {
        val zeilen = de.troetenrepublik.app.engine.TradeRepublicImport.csv(text.trimStart('\uFEFF'), ';')
        val kopf = zeilen.firstOrNull()?.map { it.trim() } ?: return emptyMap()
        val iH = kopf.indexOf("Detenteur de la position courte nette")
        val iE = kopf.indexOf("Emetteur / issuer")
        val iR = kopf.indexOf("Ratio")
        val iIsin = kopf.indexOf("code ISIN")
        val iBeginn = kopf.indexOf("Date de debut position")
        val iEnde = kopf.indexOf("Date de fin de publication position")
        val indizes = listOf(iH, iE, iR, iIsin, iBeginn, iEnde)
        if (indizes.any { it < 0 }) return emptyMap()
        val grenze = heute.minusDays(7L * WOCHEN + 7).toString()
        data class Meldung(val beginn: String, val r: Double, val ende: String)
        val reihen = HashMap<Pair<String, String>, MutableList<Meldung>>()
        val namen = HashMap<String, String>()
        for (z in zeilen.drop(1)) {
            if (z.size <= indizes.max()) continue
            val ende = z[iEnde].trim()
            if (ende.isNotEmpty() && ende < grenze) continue
            val r = z[iR].trim().replace(',', '.').toDoubleOrNull() ?: continue
            val isin = z[iIsin].trim()
            namen[isin] = z[iE].trim()
            reihen.getOrPut(isin to z[iH].trim()) { mutableListOf() }.add(Meldung(z[iBeginn].trim().take(10), r, ende))
        }
        val tage = (WOCHEN downTo 0).map { heute.minusDays(7L * it).toString() }
        val positionen = HashMap<String, MutableList<Position>>()
        val summen = HashMap<String, DoubleArray>()
        for ((schluessel, liste) in reihen) {
            val (isin, halter) = schluessel
            liste.sortWith(compareBy<Meldung> { it.beginn }.thenBy { it.r }.thenBy { it.ende })
            val s = summen.getOrPut(isin) { DoubleArray(tage.size) }
            tage.forEachIndexed { j, tag ->
                val m = liste.lastOrNull { it.beginn <= tag } ?: return@forEachIndexed
                if (m.ende.isEmpty() || m.ende > tag) s[j] += m.r
            }
            val letzte = liste.last()
            if (letzte.ende.isEmpty()) positionen.getOrPut(isin) { mutableListOf() }.add(Position(halter, letzte.r, letzte.beginn))
        }
        return summen.mapNotNull { (isin, s) ->
            val offen = positionen[isin].orEmpty().sortedByDescending { it.r }
            if (offen.isEmpty() && s.all { it == 0.0 }) return@mapNotNull null
            isin to Titel(namen[isin].orEmpty(), offen, tage.mapIndexed { j, t -> Punkt(t, Math.round(s[j] * 100) / 100.0) })
        }.toMap()
    }

    private fun wochentage(heute: java.time.LocalDate) = (WOCHEN downTo 0).map { heute.minusDays(7L * it).toString() }

    private fun runde(v: Double) = Math.round(v * 100) / 100.0

    // ------------------------------------------------------------ Grossbritannien (FCA)

    private fun fcaTag(s: String): String = s.trim().split('/').takeIf { it.size == 3 }?.let { (t, m, j) -> "$j-${m.padStart(2, '0')}-${t.padStart(2, '0')}" }.orEmpty()

    // Summe je Aktie aus der aktuellen und der frueheren Liste der FCA: aktueller Wert und Wochenwerte des letzten Jahres.
    fun fcaAuswerten(aktuell: String, historisch: String?, heute: java.time.LocalDate): Map<String, Titel> {
        data class Stand(val von: String, val bis: String, val r: Double)
        val reihen = HashMap<String, MutableList<Stand>>()
        val namen = HashMap<String, String>()
        val jetzt = HashMap<String, Double>()
        for (z in de.troetenrepublik.app.engine.TradeRepublicImport.csv(aktuell.trimStart(Char(0xFEFF)), ',').drop(1)) {
            if (z.size < 4) continue
            val isin = z[1].trim()
            val r = z[2].trim().toDoubleOrNull() ?: continue
            val von = fcaTag(z[3])
            if (von.isEmpty()) continue
            namen[isin] = z[0].trim()
            jetzt[isin] = r
            reihen.getOrPut(isin) { mutableListOf() }.add(Stand(von, "9999-12-31", r))
        }
        if (historisch != null) {
            for (z in de.troetenrepublik.app.engine.TradeRepublicImport.csv(historisch.trimStart(Char(0xFEFF)), ',').drop(1)) {
                if (z.size < 5) continue
                val isin = z[1].trim()
                val r = z[2].trim().toDoubleOrNull() ?: continue
                val von = fcaTag(z[3])
                if (von.isEmpty()) continue
                val bis = fcaTag(z[4])
                if (bis.isEmpty()) continue
                namen.putIfAbsent(isin, z[0].trim())
                reihen.getOrPut(isin) { mutableListOf() }.add(Stand(von, bis, r))
            }
        }
        val tage = wochentage(heute)
        return reihen.mapNotNull { (isin, liste) ->
            val werte = tage.map { tag -> liste.filter { it.von <= tag && it.bis > tag }.maxByOrNull { it.von }?.r ?: 0.0 }
            val g = jetzt[isin]
            if (g == null && werte.all { it == 0.0 }) return@mapNotNull null
            isin to Titel(namen[isin].orEmpty(), verlauf = tage.mapIndexed { j, t -> Punkt(t, runde(if (j == tage.lastIndex) g ?: 0.0 else werte[j])) }, gesamt = g ?: 0.0)
        }.toMap()
    }

    // ------------------------------------------------------------ Schweden (FI)

    // Zeilen der ersten Tabelle eines OpenDocument-Tabellendokuments als Texte; Zahlenzellen mit ihrem Zahlenwert.
    fun odsZeilen(ods: ByteArray): List<List<String>> {
        val xml = java.util.zip.ZipInputStream(ods.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "content.xml" } ?: return emptyList()
            zip.readBytes().decodeToString().trimStart(Char(0xFEFF))
        }
        val zeile = Regex("<table:table-row[^>]*?(?:/>|>(.*?)</table:table-row>)", RegexOption.DOT_MATCHES_ALL)
        val zelle = Regex("<table:(?:covered-)?table-cell([^>]*?)(?:/>|>(.*?)</table:(?:covered-)?table-cell>)", RegexOption.DOT_MATCHES_ALL)
        val absatz = Regex("<text:p[^>]*>(.*?)</text:p>", RegexOption.DOT_MATCHES_ALL)
        val marke = Regex("<[^>]+>")
        fun attr(a: String, name: String) = Regex("$name=\"([^\"]*)\"").find(a)?.groupValues?.get(1)
        return zeile.findAll(xml).map { z ->
            val aus = mutableListOf<String>()
            for (c in zelle.findAll(z.groupValues[1])) {
                val a = c.groupValues[1]
                val n = (attr(a, "table:number-columns-repeated")?.toIntOrNull() ?: 1).coerceAtMost(20)
                val wert = attr(a, "office:value") ?: attr(a, "office:date-value")
                    ?: absatz.findAll(c.groupValues[2]).joinToString(" ") { textXml(it.groupValues[1].replace(marke, "")) }
                repeat(n) { aus.add(wert) }
            }
            aus
        }.toList()
    }

    private fun textXml(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun fiProzent(s: String): Double? {
        val t = s.replace(Char(0xA0), ' ').trim()
        if (t.startsWith("<")) return 0.0
        return t.replace(',', '.').toDoubleOrNull()
    }

    // Offene Positionen ab 0,5 % aus der aktuellen Datei der FI, je ISIN.
    fun fiAktuell(ods: ByteArray): Map<String, Pair<String, List<Position>>> {
        val zeilen = odsZeilen(ods)
        val k = zeilen.indexOfFirst { z -> z.any { it.trim() == "ISIN" } }
        if (k < 0) return emptyMap()
        val aus = LinkedHashMap<String, Pair<String, MutableList<Position>>>()
        for (z in zeilen.drop(k + 1)) {
            if (z.size < 5) continue
            val isin = z[2].trim()
            val r = fiProzent(z[3]) ?: continue
            if (isin.isEmpty() || r <= 0) continue
            val e = z[1].replace(Char(0xA0), ' ').trim()
            aus.getOrPut(isin) { e to mutableListOf() }.second.add(Position(z[0].replace(Char(0xA0), ' ').trim(), r, z[4].trim().take(10)))
        }
        return aus.mapValues { (_, v) -> v.first to v.second.sortedByDescending { it.r } }
    }

    // Neue offene Positionen in einen Stand einarbeiten: Positionen ersetzen, Summe des Tages an den Verlauf haengen.
    fun fortschreiben(alt: Map<String, Titel>, neu: Map<String, Pair<String, List<Position>>>, heute: java.time.LocalDate): Map<String, Titel> {
        val tag = heute.toString()
        val ab = heute.minusDays(7L * WOCHEN).toString()
        return (alt.keys + neu.keys).mapNotNull { isin ->
            val a = alt[isin]
            val n = neu[isin]
            val pos = n?.second.orEmpty()
            val verlauf = a?.verlauf.orEmpty().filter { it.t >= ab && it.t < tag } + Punkt(tag, runde(pos.sumOf { it.r }))
            if (pos.isEmpty() && verlauf.all { it.s == 0.0 }) return@mapNotNull null
            isin to Titel(n?.first ?: a?.e.orEmpty(), pos, verlauf)
        }.toMap()
    }
}
