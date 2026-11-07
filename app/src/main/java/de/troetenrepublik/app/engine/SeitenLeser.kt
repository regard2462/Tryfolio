package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Boersenfeeds.Notierung

// Toleranter Leser fuer Boersenseiten, die die Person selbst im Browser der App geoeffnet hat (Selbstabruf, Globus-Laenderblatt):
// Aus dem geladenen DOM (outerHTML) liest er Tabellen, erkennt die Spalten ueber ihre Ueberschrift (Name, Kuerzel, Boersenwert,
// Kurs, Aktienzahl) und rechnet Boersenwert = Spalte "Capitalization" oder Kurs mal Aktienzahl. Steht nur ein Gesamtwert auf der Seite
// ("Market capitalization (FRW) 6,849,..."), liefert er den. Die App loest keine Pruefung der Seite, sie liest nur, was angezeigt wird.
object SeitenLeser {
    // gesamt in waehrung (ISO-Code); die Firmen tragen ihre Waehrung selbst; firmen leer, wenn keine Tabelle mit Werten da ist
    data class Ergebnis(val firmen: List<Notierung>, val gesamt: Double?, val waehrung: String)

    private val NAME = Regex("name|company|issuer|security|securities|emitent|societe|valeur|instrument|description|эмитент|nom\\b")
    private val SYMBOL = Regex("symbol|ticker|code|mnemonic|isin|тикер|код")
    private val KAPITAL = Regex("capitali[sz]ation|market cap|market value|marktkapital|boersenwert|капитализ")
    private val KURS = Regex("price|closing|close|last|cours|dernier|цена|kurs")
    private val AKTIEN = Regex("(no\\.? of|number of|nb|nombre de)? ?(shares|titres)|issued|outstanding|listed (shares|securities)")
    private val NICHT_AKTIEN = Regex("volume|traded|trading|turnover|quantit|capitaux|change|variation|previous|prev")
    private val ISO = Regex("\\b(USD|EUR|GBP|UAH|ZWG|ZWL|AMD|EGP|BHD|QAR|TND|RWF|UZS|MNT|GEL|GHS|RSD|LBP|KZT)\\b")
    private val ALIAS = mapOf("FRW" to "RWF", "RF" to "RWF", "GH¢" to "GHS", "GHC" to "GHS", "₾" to "GEL", "€" to "EUR", "$" to "USD", "TL" to "TRY")

    private fun ohneAkzente(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    private fun klar(s: String): String = BoersenfeedsMehr.entitaeten(s.replace(Regex("<[^>]*>"), " ")).replace(Regex("[\\s\\u00a0\\u202f]+"), " ").trim()

    // Zahl mit Tausendertrennern: bei Punkt und Komma gilt das letzte als Dezimaltrenner; ein einzelnes Komma oder ein einzelner Punkt vor
    // genau drei Ziffern ist ein Tausendertrenner, sonst Dezimaltrenner. Klammern und Plus/Minus bleiben unbeachtet.
    fun zahl(roh: String): Double? {
        val s = roh.replace(Regex("[^0-9.,]"), "")
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        val punkt = s.lastIndexOf('.')
        val komma = s.lastIndexOf(',')
        val n = when {
            punkt >= 0 && komma >= 0 -> if (punkt > komma) s.replace(",", "") else s.replace(".", "").replace(',', '.')
            komma >= 0 -> if (s.count { it == ',' } > 1 || s.length - komma - 1 == 3) s.replace(",", "") else s.replace(',', '.')
            punkt >= 0 -> if (s.count { it == '.' } > 1 || (s.length - punkt - 1 == 3 && s.substringBefore('.').length in 1..3 && s.substringBefore('.') != "0")) s.replace(".", "") else s
            else -> s
        }
        return n.toDoubleOrNull()
    }

    private fun faktor(text: String): Double {
        val t = ohneAkzente(text)
        return when {
            Regex("trillion|\\btn\\b|\\btrn\\b|\\bbio\\b").containsMatchIn(t) -> 1e12
            Regex("billion|milliard|mlrd|\\bbn\\b|\\bmrd\\b").containsMatchIn(t) -> 1e9
            Regex("million|\\bmn\\b|\\bmln\\b|\\bmio\\b|\\(m\\)|\\bm\\b").containsMatchIn(t) -> 1e6
            Regex("thousand|'000|\\(000\\)|тыс").containsMatchIn(t) -> 1e3
            else -> 1.0
        }
    }

    private fun waehrungIn(text: String, standard: String): String {
        ISO.find(text.uppercase())?.let { return it.value }
        for ((k, v) in ALIAS) if (text.contains(k, ignoreCase = k.length > 1)) return v
        return standard
    }

    fun lesen(html: String, land: String, standardWaehrung: String, praefix: String): Ergebnis {
        val seite = html.replace(Regex("(?is)<(script|style|noscript)\\b.*?</\\1>"), " ")
        val firmen = ArrayList<Notierung>()
        for (tabelle in Regex("(?is)<table\\b[^>]*>(.*?)</table>").findAll(seite)) {
            val zeilen = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>").findAll(tabelle.groupValues[1]).map { r ->
                Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>").findAll(r.groupValues[1]).map { klar(it.groupValues[1]) }.toList()
            }.toList()
            // Kopfzeile: unter den ersten vier Zeilen die mit den meisten erkannten Spalten
            var kopfIndex = -1
            var name = -1; var symbol = -1; var kap = -1; var kurs = -1; var aktien = -1
            for (i in 0 until minOf(4, zeilen.size)) {
                val h = zeilen[i].map { ohneAkzente(it) }
                if (h.size < 3) continue
                val kapI = h.indexOfFirst { KAPITAL.containsMatchIn(it) }
                val kursI = h.indexOfFirst { KURS.containsMatchIn(it) && !NICHT_AKTIEN.containsMatchIn(it) }
                val aktI = h.indexOfFirst { AKTIEN.containsMatchIn(it) && !NICHT_AKTIEN.containsMatchIn(it) }
                if (kapI < 0 && (kursI < 0 || aktI < 0)) continue
                val symI = h.indexOfFirst { SYMBOL.containsMatchIn(it) }
                val namI = h.indexOfFirst { NAME.containsMatchIn(it) && !KAPITAL.containsMatchIn(it) }
                if (namI < 0 && symI < 0) continue
                kopfIndex = i; kap = kapI; kurs = kursI; aktien = aktI; symbol = symI; name = namI
                break
            }
            if (kopfIndex < 0) continue
            val kopf = zeilen[kopfIndex]
            val waehrung = waehrungIn(kopf.getOrElse(kap.takeIf { it >= 0 } ?: kurs) { "" } + " " + kopf.joinToString(" "), standardWaehrung)
            val skala = if (kap >= 0) faktor(kopf[kap]) else faktor(kopf.getOrElse(aktien) { "" })
            for (z in zeilen.drop(kopfIndex + 1)) {
                if (z.size <= maxOf(name, symbol, kap, kurs, aktien)) continue
                val n = (z.getOrNull(name) ?: "").ifBlank { z.getOrNull(symbol).orEmpty() }
                if (n.isBlank() || Regex("(?i)^(total|totals|sum|gesamt|summe)\\b").containsMatchIn(n)) continue
                val wert = if (kap >= 0) zahl(z[kap])?.times(skala)
                else if (kurs >= 0 && aktien >= 0) zahl(z[kurs])?.let { p -> zahl(z[aktien])?.let { a -> p * a * skala } } else null
                if (wert == null || wert <= 0 || !wert.isFinite()) continue
                val code = z.getOrNull(symbol)?.takeIf { it.isNotBlank() } ?: n
                firmen += Notierung(BoersenfeedsMehr.reinigen(n), "$praefix:$code", wert, waehrung, land, BoersenfeedsMehr.brancheAusName(n))
            }
        }
        val g = gesamtwert(seite, standardWaehrung)
        return Ergebnis(firmen, g?.first, g?.second ?: standardWaehrung)
    }

    // Gesamtwert aus dem Seitentext: "Market capitalization (FRW) 6,849,561,071,247", "Total Stock Market Capitalization 478,389,254.30 ₾",
    // "Capitalisation boursiere (TND) 25 000". Mindestens vier Stellen oder Tausendertrenner, damit Datumsteile nicht zaehlen.
    fun gesamtwert(seite: String, standard: String): Pair<Double, String>? {
        val text = klar(seite.replace(Regex("(?is)<(script|style|noscript)\\b.*?</\\1>"), " "))
        val rx = Regex("(?i)(?:total )?(?:stock )?(?:market capitali[sz]ation|capitalisation boursiere|capitalisation boursière|marktkapitalisierung)(?: ?\\(([^)]{1,6})\\))?[^0-9]{0,40}([0-9]{1,3}(?:[,. ][0-9]{3})+(?:[.,][0-9]+)?|[0-9]{4,}(?:[.,][0-9]+)?)\\s*(₾|€|\\$|[A-Za-z]{2,10})?")
        for (m in rx.findAll(text)) {
            val w = zahl(m.groupValues[2]) ?: continue
            val nachsatz = m.groupValues[3]
            val waehrung = waehrungIn(m.groupValues[1] + " " + nachsatz, standard)
            val skala = if (nachsatz.isNotBlank()) faktor(nachsatz) else 1.0
            if (w * skala > 0) return w * skala to waehrung
        }
        return null
    }

    // Zwischenspeicher: erste Zeile "Zeitpunkt;Gesamtwert;Waehrung", danach die Notierungen wie bei den anderen Feeds
    fun speichern(zeit: Long, e: Ergebnis): String =
        "$zeit;${e.gesamt?.toLong() ?: 0};${e.waehrung}\n" + Boersenfeeds.speichern(e.firmen)

    fun laden(text: String): Pair<Long, Ergebnis>? {
        val kopf = text.substringBefore('\n').split(';')
        if (kopf.size < 3) return null
        val zeit = kopf[0].toLongOrNull() ?: return null
        val gesamt = kopf[1].toDoubleOrNull()?.takeIf { it > 0 }
        return zeit to Ergebnis(Boersenfeeds.laden(text.substringAfter('\n', "")), gesamt, kopf[2])
    }
}
