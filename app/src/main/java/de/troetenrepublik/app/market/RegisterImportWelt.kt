package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.EuInsider.Geschaeft

// Selbst heruntergeladene Insidermeldungen weiterer Laender: Aegypten (EGX, Tagesbericht als PDF), Kuwait (Boursa
// Kuwait, Formular als HTML gespeichert), Katar (QSE, Seite „Insider Trades“ gespeichert) und Australien (ASX,
// Appendix 3Y als PDF). Die App liest nur, was der Nutzer selbst gespeichert hat. Australien (ASX) nur ueber diesen
// Import. Aufgerufen aus RegisterImport.erkennen; null heisst: kein Format dieses Teils.
// Die App oeffnet die Seite selbst und uebernimmt die Datei (Android: eigener Browser, Desktop: Downloads-Ordner).
// Gespeicherte NGX-Meldungen liest der Import. Katar (QSE): der Nutzer ruft die Seite im eigenen Browser auf und
// importiert sie; ein Speichern als MHTML (Handy) wird entpackt. Consob direkt ueber die Datei-Adresse, der
// Bundesanzeiger ueber den CSV-Knopf der Seite; beides laedt der Nutzer selbst.
object RegisterImportWelt {

    // Quellen zum Selbstabrufen fuer die Uebersicht im Profil: Kennung (fuer die Texte), Land und Seite (leer: die App
    // oeffnet keine Seite, die Texte beschreiben den Weg). endungen und
    // muster bestimmen am Desktop, welche neue Datei im Downloads-Ordner zur Quelle passt.
    data class Quelle(
        val id: String, val land: String, val url: String,
        val endungen: List<String> = listOf("pdf", "csv", "xlsx", "html", "htm", "json"), val muster: String = "",
    )

    val QUELLEN = listOf(
        Quelle("sedi", "CA", InsiderWelt.SEDI, endungen = listOf("pdf", "json")),
        Quelle("consob", "IT", CONSOB_DATEI, endungen = listOf("xlsx"), muster = "pncpubbl"),
        Quelle("bund", "DE", BUNDESANZEIGER, endungen = listOf("csv"), muster = "leerverkaeufe"),
        Quelle("fca", "GB", Leerpositionen.FCA_SEITE, endungen = listOf("csv"), muster = "net-short-positions"),
        Quelle("bse", "IN", "https://www.bseindia.com/corporates/Insider_Trading_new.aspx", endungen = listOf("csv")),
        Quelle("egx", "EG", "https://www.egx.com.eg/en/homepage.aspx", endungen = listOf("pdf")),
        Quelle("kw", "KW", "https://www.boursakuwait.com.kw/en/", endungen = listOf("html", "htm")),
        Quelle("qse", "QA", "", endungen = listOf("html", "htm", "mhtml", "mht")),
        Quelle("pl", "PL", "https://www.gpw.pl", endungen = listOf("pdf")),
        Quelle("li", "LI", "https://www.fma-li.li", endungen = listOf("pdf")),
        Quelle("mt", "MT", "https://www.borzamalta.com.mt", endungen = listOf("pdf")),
        Quelle("asx", "AU", "https://www.asx.com.au/markets/trade-our-cash-market/announcements", endungen = listOf("pdf")),
    )

    // Consob-Liste der Netto-Leerverkaufspositionen als Datei (die Seite fuehrt per Link genau dorthin)
    const val CONSOB_DATEI = "https://www.consob.it/documents/11973/395154/PncPubbl.xlsx"

    // Bundesanzeiger, Netto-Leerverkaufspositionen; die CSV gibt es nur ueber den Knopf der Seite (Sitzung)
    const val BUNDESANZEIGER = "https://www.bundesanzeiger.de/pub/de/nlp"

    fun erkennen(daten: ByteArray, heute: java.time.LocalDate): RegisterImport.Ergebnis? {
        if (daten.size > 4 && String(daten, 0, 4, Charsets.ISO_8859_1) == "%PDF") {
            val zeilen = PdfText.zeilen(daten)
            if (zeilen.isEmpty()) return null
            val text = zeilen.joinToString(" ")
            val dicht = text.lowercase().replace(Regex("\\s+"), "")
            val id = "imp-" + daten.contentHashCode().toUInt().toString(16)
            return when {
                dicht.contains("tradingofinsiders") -> ergebnis("EG", egx(zeilen, heute.toString()))
                dicht.contains("sharedealing") && dicht.contains("insider") -> ergebnis("NG", listOfNotNull(ngx(text, id)))
                dicht.contains("appendix3y") -> ergebnis("AU", listOfNotNull(InsiderWelt.asxLesen(zeilen, InsiderWelt.AsxMeldung(id, heute.toString(), "", ""))
                    ?.copy(q = "")))
                else -> null
            }
        }
        val html = mhtml(daten.toString(Charsets.UTF_8))
        if (!Regex("(?i)<html|<table|<tr").containsMatchIn(html.take(400_000))) return null
        return when {
            html.contains("TickerTD") || (html.contains("qe.com.qa", true) && html.contains("Insider", true)) -> ergebnis("QA", qse(html, heute.toString()))
            html.contains("boursakuwait", true) || html.contains("Boursa Kuwait", true) -> ergebnis("KW", kuwait(html, "imp-" + daten.contentHashCode().toUInt().toString(16)))
            else -> null
        }
    }

    // Ohne erkannte Geschaefte bleibt es beim Hinweis, dass die Datei unbekannt ist.
    private fun ergebnis(land: String, g: List<Geschaeft>): RegisterImport.Ergebnis? =
        if (g.isEmpty()) null else RegisterImport.Ergebnis.Insider(land, g, g.maxOf { it.v.ifBlank { it.d } })

    // Zerfallene Zahlen aus PDF-Text zusammenfuegen („16 8 . 9 0“, „2 5 ,000“, „23 / 0 9 /202 6“).
    private fun zahlen(t: String) = t.replace(Regex("(?<=\\d) (?=\\d)"), "").replace(Regex("(?<=\\d) ?\\. ?(?=\\d)"), ".")
        .replace(Regex("(?<=\\d) ?, ?(?=\\d{3}(?!\\d))"), ",").replace(Regex("(?<=\\d) ?/ ?(?=\\d)"), "/")

    // ------------------------------------------------------------ Nigeria (NGX)

    // Formular nach dem Muster der EU-Marktmissbrauchsverordnung: Name, Position, Emittent, ISIN, Art, Menge, Preis,
    // Tag. Die Felder werden zwischen den Bezeichnungen gelesen; zerfallene Woerter („Purcha s e“) zaehlen zusammengezogen.
    fun ngx(text: String, id: String): Geschaeft? {
        val t = zahlen(text.replace(Char(0xA0), ' ').replace(Regex("[‐-—]"), "-").replace(Regex("\\s+"), " "))
        fun feld(von: String, bis: String) = Regex("(?i)$von\\s*(.+?)\\s*(?:$bis)").find(t)?.groupValues?.get(1)?.trim(' ', ':', '-').orEmpty()
        val name = feld("details of the director\\s*/\\s*insider\\s*(?:\\d\\.|[a-f]\\)?)?\\s*name", "\\b2\\.|reason for the notification").take(80)
        if (name.isBlank()) return null
        val position = feld("position\\s*/\\s*status", "\\bb\\)|initial notification").take(120)
        val firma = feld("details of the issuer\\s*(?:[a-f]\\))?\\s*name", "\\bb\\)|legal entity").take(100)
        val isin = Regex("ISIN\\s*:?\\s*(NG[A-Z0-9]{10})").find(t)?.groupValues?.get(1).orEmpty()
        val natur = feld("nature of the transaction", "\\bc\\)|price\\s*\\(s\\)").lowercase().replace(" ", "")
        val art = when {
            Regex("purchase|buy|acqui").containsMatchIn(natur) -> "K"
            Regex("sale|sell|dispos").containsMatchIn(natur) -> "V"
            else -> "S"
        }
        val menge = Regex("(?i)aggregated volume\\s*:?\\s*([\\d,]+)").find(t)?.groupValues?.get(1)?.let { InsiderQuellen.zahl(it) }
            ?: Regex("(?i)([\\d,]{2,})\\s*(?:units|shares|ordinary)").findAll(feld("price\\s*\\(s\\)\\s*and\\s*volume\\s*\\(s\\)", "aggregate")).mapNotNull { InsiderQuellen.zahl(it.groupValues[1]) }.sum().takeIf { it > 0 }
            ?: return null
        val nachMenge = t.substringAfter("ggregated volume", t.substringAfter("ggregate", ""))
        val preis = Regex("(?:\\bN|NGN|₦)\\s?(\\d[\\d,]*(?:\\.\\d+)?)").find(nachMenge)?.groupValues?.get(1)?.let { InsiderQuellen.zahl(it) }
            ?: Regex("(?:@|at)\\s*(?:\\bN|NGN|₦)\\s?(\\d[\\d,]*(?:\\.\\d+)?)").find(t)?.groupValues?.get(1)?.let { InsiderQuellen.zahl(it) } ?: 0.0
        // „Date (s) of Transact i on“, „2 October , 2026“
        val datum = InsiderQuellen.tag(feld("date\\s*\\(?\\s*s?\\s*\\)?\\s*of\\s*transact\\s*i\\s*on", "\\bf\\)|place of transaction").replace(Regex("\\s+,"), ","))
            .ifEmpty { InsiderQuellen.tag(t) }
        val r = when {
            Regex("(?i)chairman|managing director|chief executive|\\bceo\\b|\\bcfo\\b|chief financial").containsMatchIn(position) -> "S"
            Regex("(?i)non[- ]?executive|director").containsMatchIn(position) -> "A"
            else -> "F"
        }
        return Geschaeft(
            e = firma, isin = isin, p = InsiderQuellen.ganz(name), r = r, t = position, i = "Aktie", art = art, d = datum, v = datum,
            n = menge, preis = if (art != "S") preis else 0.0, wert = if (art != "S") preis * menge else 0.0, w = "NGN", id = "NGX-$id",
        )
    }

    // ------------------------------------------------------------ Aegypten (EGX)

    // Tagesbericht „Trading of Insiders, Major Shareholders & Their Related Parties“: je Geschaeft Firma, Person,
    // Beziehung, Art (Buy/Sell), Menge und Preis. Eine Zeile wird an ihrer Art erkannt; die Texte davor sind Firma,
    // Person und Beziehung, die Zahlen danach Menge und Preis.
    fun egx(zeilen: List<String>, heute: String): List<Geschaeft> {
        val z = zeilen.map { zahlen(it.replace(Regex("\\s+"), " ").trim()) }.filter { it.isNotEmpty() }
        val stand = z.take(30).firstNotNullOfOrNull { InsiderQuellen.tag(it).ifEmpty { null } } ?: heute
        val arten = z.indices.filter { Regex("(?i)^(buy|sell|purchase|sale)$").matches(z[it]) }
        val aus = mutableListOf<Geschaeft>()
        for ((k, i) in arten.withIndex()) {
            val start = if (k == 0) maxOf(0, i - 4) else maxOf(arten[k - 1] + 1, i - 4)
            val texte = z.subList(start, i).filter { s -> s.any { it.isLetter() } && InsiderQuellen.tag(s).isEmpty() }.takeLast(3)
            val ende = arten.getOrNull(k + 1)?.let { minOf(it, i + 6) } ?: minOf(z.size, i + 6)
            val danach = z.subList(i + 1, ende)
            val zahlen = danach.filter { Regex("^[\\d,]+(\\.\\d+)?$").matches(it) }.mapNotNull { InsiderQuellen.zahl(it) }
            val n = zahlen.firstOrNull { it >= 1 && it % 1.0 == 0.0 } ?: continue
            val preis = zahlen.drop(1).firstOrNull() ?: 0.0
            val tag = danach.firstNotNullOfOrNull { InsiderQuellen.tag(it).ifEmpty { null } } ?: stand
            val art = if (Regex("(?i)buy|purchase").matches(z[i])) "K" else "V"
            val firma = texte.getOrNull(0).orEmpty()
            val person = texte.getOrNull(1) ?: continue
            val beziehung = texte.getOrNull(2).orEmpty()
            aus += Geschaeft(
                e = firma, isin = Regex("EG[A-Z0-9]{10}").find(firma)?.value.orEmpty(), p = person,
                r = if (Regex("(?i)board|chairman|director|ceo|managing").containsMatchIn(beziehung)) "A" else "N", t = beziehung,
                i = "Aktie", art = art, d = tag, v = stand, n = n, preis = preis, wert = n * preis, w = "EGP",
                id = "EGX-" + (firma + person + tag + n + art).hashCode().toUInt().toString(16),
            )
        }
        return aus
    }

    // Als MHTML gespeicherte Seite (Browser am Handy): quoted-printable entpacken, sonst unveraendert.
    fun mhtml(text: String): String {
        if (!text.contains("Content-Transfer-Encoding: quoted-printable", true)) return text
        val roh = text.replace(Regex("=\r?\n"), "")
        val aus = java.io.ByteArrayOutputStream(roh.length)
        var i = 0
        while (i < roh.length) {
            val c = roh[i]
            val hex = if (c == '=' && i + 2 < roh.length) roh.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) { aus.write(hex); i += 3 } else { aus.write(c.toString().toByteArray(Charsets.UTF_8)); i++ }
        }
        return aus.toString("UTF-8")
    }

    // ------------------------------------------------------------ Katar (QSE)

    // Gespeicherte Seite „Insider Trades“: Tabelle TickerTD mit Firma, Symbol, Insider, Kauf- und Verkaufsmenge;
    // der Tag steht im Kopf. Ohne Preis gibt es keinen Betrag, ohne erlaubten Link keine Original-Adresse.
    fun qse(html: String, heute: String): List<Geschaeft> {
        val tag = Regex("(?is)<th[^>]*id=\"Date\"[^>]*>(.*?)</th>").find(html)?.groupValues?.get(1)
            ?.let { InsiderQuellen.tag(InsiderQuellen.entitaeten(it.replace(Regex("<[^>]+>"), " ")).replace('-', ' ')) }?.ifEmpty { null } ?: heute
        val tabelle = Regex("(?is)<table[^>]*id=\"TickerTD\"[^>]*>(.*?)</table>").find(html)?.groupValues?.get(1) ?: return emptyList()
        return Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(tabelle).mapNotNull { r ->
            val c = Regex("(?is)<td[^>]*>(.*?)</td>").findAll(r.groupValues[1])
                .map { InsiderQuellen.entitaeten(it.groupValues[1].replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim() }.toList()
            if (c.size < 5) return@mapNotNull null
            val kauf = InsiderQuellen.zahl(c[3]) ?: 0.0
            val verkauf = InsiderQuellen.zahl(c[4]) ?: 0.0
            if (kauf <= 0 && verkauf <= 0) return@mapNotNull null
            val art = when { kauf > 0 && verkauf <= 0 -> "K"; verkauf > 0 && kauf <= 0 -> "V"; else -> "S" }
            Geschaeft(
                e = c[0], isin = "", p = c[2], r = "F", t = "", i = "Aktie", art = art, d = tag, v = tag, n = maxOf(kauf, verkauf), w = "QAR",
                id = "QSE-${c[1]}-" + (c[2] + tag + kauf + verkauf).hashCode().toUInt().toString(16), q = "", c = c[1].uppercase(),
            )
        }.toList()
    }

    // ------------------------------------------------------------ Kuwait (Boursa Kuwait)

    // Bezeichnung und Wert eines Formulars: Tabellenzeilen und Felder werden zu Zeilen, eine bekannte Bezeichnung
    // nimmt den Text dahinter (in derselben Zeile nach dem Doppelpunkt oder die naechste Zeile).
    fun formular(html: String): Map<String, String> {
        val muster = listOf(
            "firma" to "company name|issuer|listed company|company",
            "symbol" to "symbol|ticker|stock code|security code",
            "name" to "insider name|name of (?:the )?insider|insider'?s name|name",
            "position" to "position|title|relationship|capacity",
            "art" to "transaction type|type of (?:the )?transaction|nature of (?:the )?transaction|buy\\s*/\\s*sell",
            "tag" to "transaction date|date of (?:the )?transaction|trade date|date",
            "menge" to "number of shares|no\\. of shares|quantity|volume",
            "preis" to "average price|price per share|price",
            "wert" to "total value|value|amount",
        )
        val zeilen = InsiderQuellen.htmlZeilen(html)
        val aus = LinkedHashMap<String, String>()
        for ((i, z) in zeilen.withIndex()) {
            for ((k, m) in muster) {
                if (aus.containsKey(k)) continue
                val r = Regex("(?i)^(?:$m)\\s*:?\\s*(.*)$").find(z) ?: continue
                val rest = r.groupValues[1].trim()
                val wert = if (rest.isNotEmpty()) rest else zeilen.getOrNull(i + 1)?.takeIf { n -> muster.none { Regex("(?i)^(?:${it.second})\\s*:?$").matches(n) } }.orEmpty()
                if (wert.isNotBlank()) { aus[k] = wert; break }
            }
        }
        return aus
    }

    // Einzelnes Formular „Insider Disclosures“, im Browser als HTML gespeichert. Preise in Kuwait stehen oft in Fils
    // (1/1000 Dinar): ein Preis ueber 20 gilt als Fils.
    fun kuwait(html: String, id: String): List<Geschaeft> {
        val f = formular(html)
        val name = f["name"]?.takeIf { it.isNotBlank() } ?: return emptyList()
        val n = f["menge"]?.let { InsiderQuellen.zahl(it) } ?: return emptyList()
        if (n <= 0) return emptyList()
        val art = f["art"].orEmpty().lowercase().let { a -> when { Regex("buy|purchase|acqui").containsMatchIn(a) -> "K"; Regex("sell|sale|dispos").containsMatchIn(a) -> "V"; else -> "S" } }
        val roh = f["preis"]?.let { InsiderQuellen.zahl(it) } ?: 0.0
        val preis = if (roh > 20) roh / 1000 else roh
        val wert = f["wert"]?.let { InsiderQuellen.zahl(it) }?.takeIf { it > 0 } ?: (preis * n)
        val tag = InsiderQuellen.tag(f["tag"].orEmpty())
        val position = f["position"].orEmpty()
        return listOf(
            Geschaeft(
                e = f["firma"].orEmpty(), isin = "", p = name, r = if (Regex("(?i)chairman|ceo|chief executive|managing").containsMatchIn(position)) "S" else if (Regex("(?i)board|director").containsMatchIn(position)) "A" else "F",
                t = position, i = "Aktie", art = art, d = tag, v = tag, n = n, preis = if (art != "S") preis else 0.0, wert = if (art != "S") wert else 0.0, w = "KWD",
                id = "KW-$id", c = f["symbol"].orEmpty().uppercase(),
            ),
        ).filter { it.d.isNotEmpty() }
    }
}
