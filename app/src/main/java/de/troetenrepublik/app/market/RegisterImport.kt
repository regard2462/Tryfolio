package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.EuInsider.Geschaeft

// Registerdaten, die der Nutzer selbst herunterlaedt und in die App holt (seit 5.9): Die Quellen sperren
// automatische Abrufe oder verbieten die Weitergabe, deshalb liegt nichts davon im Programm. Die Daten bleiben auf
// dem Geraet. Erkannt werden die Gesamtliste der BSE (Indien, CSV), die Tabelle der Consob (Italien, XLSX) und
// Registerdateien im Format der App (JSON, etwa aus SEDI-Wochenberichten umgewandelt).
object RegisterImport {
    sealed class Ergebnis {
        data class Insider(val land: String, val geschaefte: List<Geschaeft>, val stand: String) : Ergebnis()
        data class Leer(val datei: Leerpositionen.Datei) : Ergebnis()
        data class Unbekannt(val pdf: Boolean) : Ergebnis()
    }

    fun erkennen(daten: ByteArray, heute: java.time.LocalDate): Ergebnis {
        if (daten.size > 4 && String(daten, 0, 4, Charsets.ISO_8859_1) == "%PDF") return Ergebnis.Unbekannt(pdf = true)
        if (daten.size > 2 && daten[0] == 'P'.code.toByte() && daten[1] == 'K'.code.toByte()) {
            val b = xlsx(daten)
            if (b.keys.any { it.contains("Current", true) } && b.keys.any { it.contains("Historic", true) }) {
                return Ergebnis.Leer(consob(b, heute))
            }
            return Ergebnis.Unbekannt(pdf = false)
        }
        val text = daten.toString(Charsets.UTF_8).trimStart('﻿')
        if (text.trimStart().startsWith("{")) {
            EuInsider.lesen(text)?.takeIf { it.geschaefte.isNotEmpty() }?.let { return Ergebnis.Insider(it.land, it.geschaefte, it.stand) }
            Leerpositionen.lesen(text)?.takeIf { it.titel.isNotEmpty() }?.let { return Ergebnis.Leer(it) }
        }
        if (text.startsWith("Security Code")) {
            val g = bse(text, heute.minusDays(365).toString())
            return Ergebnis.Insider("IN", g, g.maxOfOrNull { it.v.ifBlank { it.d } }.orEmpty())
        }
        return Ergebnis.Unbekannt(pdf = false)
    }

    // ------------------------------------------------------------ BSE (Indien)

    private val MONATE = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun tag(s: String): String {
        val m = Regex("^(\\d{1,2})[ -]([A-Za-z]{3})[ -](\\d{4})$").find(s.trim()) ?: return ""
        val mon = MONATE.indexOf(m.groupValues[2].lowercase()) + 1
        if (mon == 0) return ""
        return "%s-%02d-%02d".format(m.groupValues[3], mon, m.groupValues[1].toInt())
    }

    private fun rolle(kategorie: String): String {
        val k = kategorie.lowercase()
        return when {
            "relative" in k -> "N"
            "kmp" in k || "key managerial" in k -> "S"
            "promoter" in k && "director" in k -> "S"
            "director" in k -> "A"
            "promoter" in k || "trust" in k || "connected" in k -> "N"
            else -> "F"
        }
    }

    // Gesamtliste „Download All Records“ (SEBI PIT): Kauf oder Verkauf nur an der Boerse, Pfaendungen fallen weg.
    fun bse(text: String, ab: String): List<Geschaeft> {
        val zeilen = de.troetenrepublik.app.engine.TradeRepublicImport.csv(text, ',')
        val kopf = zeilen.firstOrNull()?.map { it.trim() } ?: return emptyList()
        fun i(teil: String) = kopf.indexOfFirst { it.startsWith(teil, true) }
        val iCode = i("Security Code"); val iName = i("Security Name"); val iPerson = i("Name of Person"); val iKat = i("Category of person")
        val iTyp = i("Type of Securities Acquired"); val iMenge = i("Number of Securities Acquired"); val iWert = i("Value")
        val iArt = i("Transaction Type"); val iVon = i("Date of acquisition of shares/sale of shares/Date of Allotment(From")
        val iMeldung = i("Date of Intimation"); val iModus = i("Mode of Acquisition"); val iOrt = i("Exchange on which")
        if (listOf(iCode, iName, iPerson, iMenge, iWert, iArt, iVon).any { it < 0 }) return emptyList()
        val aus = LinkedHashMap<String, Geschaeft>()
        for (z in zeilen.drop(1)) {
            fun f(k: Int) = z.getOrNull(k)?.trim().orEmpty()
            val artRoh = f(iArt).lowercase()
            if (artRoh !in setOf("acquisition", "disposal", "buy", "sell", "sale")) continue
            val d = tag(f(iVon)).ifEmpty { tag(f(iMeldung)) }
            if (d.isEmpty() || d < ab) continue
            val n = f(iMenge).replace(",", "").toDoubleOrNull() ?: continue
            if (n <= 0) continue
            val wert = f(iWert).replace(",", "").toDoubleOrNull() ?: 0.0
            val modus = f(iModus).lowercase()
            val person = f(iPerson).replace(Regex("\\s+"), " ")
            val markt = modus in setOf("market purchase", "market sale", "market", "block deal")
            val plan = "trust" in person.lowercase() && Regex("esop|esos|employee|welfare|benefit|stock option|incentive").containsMatchIn(person.lowercase())
            val art = when {
                !markt || plan || wert <= 0 -> "S"
                artRoh == "acquisition" || artRoh == "buy" -> "K"
                else -> "V"
            }
            val typ = f(iTyp)
            val code = f(iCode)
            val g = Geschaeft(
                e = f(iName).replace(Regex("\\s+"), " "), isin = "", p = person, r = rolle(f(iKat)), t = f(iKat),
                i = if (typ.equals("Equity", true) || typ.equals("Equity Shares", true)) "Aktie" else typ, art = art, d = d, v = tag(f(iMeldung)),
                n = n, preis = if (art != "S") wert / n else 0.0, wert = if (art != "S") wert else 0.0, w = "INR", o = f(iOrt),
                id = "BSE-$code-${(person + d + n + artRoh + wert).hashCode().toUInt().toString(16)}",
                q = "https://www.bseindia.com/corporates/Insider_Trading_new.aspx", c = code,
            )
            aus[g.id] = g
        }
        return aus.values.toList()
    }

    // ------------------------------------------------------------ Consob (Italien)

    // Blaetter einer XLSX-Datei als Zeilen von Texten (geteilte Texte aufgeloest).
    fun xlsx(daten: ByteArray): Map<String, List<List<String>>> {
        val teile = HashMap<String, String>()
        java.util.zip.ZipInputStream(daten.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) teile[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        fun ent(s: String) = InsiderQuellen.entitaeten(s)
        val texte = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(teile["xl/sharedStrings.xml"].orEmpty())
            .map { si -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1]).joinToString("") { ent(it.groupValues[1]) } }.toList()
        val rels = Regex("<Relationship [^>]*Id=\"([^\"]+)\"[^>]*Target=\"([^\"]+)\"").findAll(teile["xl/_rels/workbook.xml.rels"].orEmpty())
            .associate { it.groupValues[1] to it.groupValues[2].removePrefix("/").let { t -> if (t.startsWith("xl/")) t else "xl/$t" } }
        val aus = LinkedHashMap<String, List<List<String>>>()
        for (b in Regex("<sheet [^>]*name=\"([^\"]+)\"[^>]*r:id=\"([^\"]+)\"").findAll(teile["xl/workbook.xml"].orEmpty())) {
            val xml = teile[rels[b.groupValues[2]]] ?: continue
            val zeilen = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { row ->
                val zeile = ArrayList<String>()
                for (c in Regex("<c r=\"([A-Z]+)\\d+\"([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1])) {
                    val spalte = c.groupValues[1].fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1
                    while (zeile.size < spalte) zeile.add("")
                    val innen = c.groupValues[3]
                    val v = Regex("<v>(.*?)</v>").find(innen)?.groupValues?.get(1).orEmpty()
                    zeile.add(when {
                        "t=\"s\"" in c.groupValues[2] -> texte.getOrElse(v.toIntOrNull() ?: -1) { "" }
                        "t=\"inlineStr\"" in c.groupValues[2] -> Regex("<t[^>]*>(.*?)</t>").findAll(innen).joinToString("") { ent(it.groupValues[1]) }
                        else -> ent(v)
                    })
                }
                zeile
            }.toList()
            aus[ent(b.groupValues[1])] = zeilen
        }
        return aus
    }

    private fun excelTag(v: String): String =
        v.toDoubleOrNull()?.let { java.time.LocalDate.of(1899, 12, 30).plusDays(it.toLong()).toString() }
            ?: Regex("^(\\d{2})/(\\d{2})/(\\d{4})").find(v.trim())?.let { "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }.orEmpty()

    // Offene Positionen und Wochensummen des letzten Jahres wie bei Schweden (Leerpositionen.fortschreiben).
    fun consob(b: Map<String, List<List<String>>>, heute: java.time.LocalDate): Leerpositionen.Datei {
        data class Z(val h: String, val e: String, val isin: String, val r: Double, val d: String)
        fun lesen(blatt: List<List<String>>) = blatt.mapNotNull { z ->
            if (z.size < 7 || z[4].trim().length != 12) return@mapNotNull null
            val r = z[5].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val d = excelTag(z[6]).ifEmpty { return@mapNotNull null }
            Z(z[0].trim(), z[2].trim(), z[4].trim().uppercase(), r, d)
        }
        val aktuell = lesen(b.entries.firstOrNull { it.key.contains("Current", true) }?.value.orEmpty())
        val historisch = lesen(b.entries.firstOrNull { it.key.contains("Historic", true) }?.value.orEmpty())
        val stand = b.entries.firstOrNull { it.key.contains("Pubb", true) }?.value.orEmpty().firstNotNullOfOrNull { z -> z.firstOrNull()?.let { excelTag(it) }?.takeIf { it.isNotEmpty() } }
            ?: heute.toString()
        val bis = java.time.LocalDate.parse(stand)
        val tage = (Leerpositionen.WOCHEN downTo 0).map { bis.minusDays(7L * it).toString() }
        val namen = HashMap<String, String>()
        val reihen = HashMap<Pair<String, String>, MutableList<Pair<String, Double>>>()
        for (z in historisch + aktuell) {
            namen[z.isin] = z.e
            reihen.getOrPut(z.isin to z.h.lowercase()) { mutableListOf() }.add(z.d to z.r)
        }
        val offenePaare = aktuell.filter { it.r > 0 }.map { it.isin to it.h.lowercase() }.toSet()
        val summen = HashMap<String, DoubleArray>()
        for ((paar, liste) in reihen) {
            liste.sortBy { it.first }
            // Nicht mehr offene Positionen ohne Schlussmeldung enden nach ihrer letzten Meldung
            if (paar !in offenePaare && liste.last().second > 0) liste.add(java.time.LocalDate.parse(liste.last().first).plusDays(1).toString() to 0.0)
            val s = summen.getOrPut(paar.first) { DoubleArray(tage.size) }
            tage.forEachIndexed { j, t -> liste.lastOrNull { it.first <= t }?.let { s[j] += it.second } }
        }
        val offen = aktuell.filter { it.r > 0 }.groupBy { it.isin }
        val titel = summen.mapNotNull { (isin, s) ->
            val pos = offen[isin].orEmpty().sortedByDescending { it.r }.map { Leerpositionen.Position(it.h, it.r, it.d) }
            s[s.lastIndex] = pos.sumOf { it.r }
            if (pos.isEmpty() && s.all { it == 0.0 }) null
            else isin to Leerpositionen.Titel(namen[isin].orEmpty(), pos, tage.indices.map { Leerpositionen.Punkt(tage[it], Math.round(s[it] * 100) / 100.0) })
        }.toMap()
        return Leerpositionen.Datei(stand = stand, titel = titel, land = "IT")
    }
}
