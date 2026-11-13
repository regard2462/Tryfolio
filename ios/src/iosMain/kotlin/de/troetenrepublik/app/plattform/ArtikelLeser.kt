package de.troetenrepublik.app.plattform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.withContext

// Laedt die Artikelseite und liest die Absaetze aus dem HTML, nach denselben Regeln wie am Desktop (dort mit Jsoup),
// hier vereinfacht ueber Muster. Seiten, die ihren Text per Skript nachladen oder erst eine Einwilligung verlangen,
// liefern nichts, dann bleibt Im Browser oeffnen.
@Composable
fun ArtikelLeser(
    adresse: String,
    wikipedia: Boolean,
    auslesen: Boolean,
    modifier: Modifier,
    textDa: () -> Boolean,
    onAbsaetze: (List<String>) -> Unit,
    onEnde: () -> Unit,
    // Ohne Web-Ansicht gibt es keine geladene Seite, aus der sich lesen liesse.
    onGeladen: (Boolean) -> Unit = {},
    leseAnfrage: Int = 0,
    onLesemodus: (List<String>) -> Unit = {},
    // Hauptdokument abgewiesen oder nicht geladen: HTTP-Status ab 400, 0 bei Ladefehler (kein Netz, Zeitueberschreitung,
    // Zertifikat). Hier ohne Web-Ansicht unbenutzt.
    onFehler: (Int) -> Unit = {},
) {
    val aufnehmen by rememberUpdatedState(onAbsaetze)
    val ende by rememberUpdatedState(onEnde)
    LaunchedEffect(adresse, auslesen) {
        if (!auslesen) return@LaunchedEffect
        val liste = withContext(de.troetenrepublik.app.ios.ioDispatcher) { Artikel.lesen(adresse, wikipedia) }
        if (liste != null) aufnehmen(liste)
        ende()
    }
    Box(modifier)
}

internal object Artikel {
    private val OHNE = Regex("<(script|style|noscript|template|svg)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ABSATZ = Regex("<p(\\s[^>]*)?>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val MARKE = Regex("<[^>]+>")
    private val LEER = Regex("\\s+")
    private val STOP = Regex("cookie|Cookies|consent|Einwilligung|privacy", RegexOption.IGNORE_CASE)
    private val FUSSNOTE = Regex("\\[\\d+\\]")
    private val SUP = Regex("<sup[^>]*>.*?</sup>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ENTITAET = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")

    fun lesen(adresse: String, wikipedia: Boolean): List<String>? {
        val bytes = Netz.bytes(adresse, kennung = Netz.BROWSER) ?: return null
        val html = OHNE.replace(bytes.decodeToString(), " ")
        return if (wikipedia) wikipedia(html) else absaetze(html)
    }

    // Zuerst der Artikelbereich, sonst die ganze Seite.
    fun absaetze(html: String): List<String> {
        val start = listOf("<article", "caas-body", "article-body", "articleBody", "<main").map { html.indexOf(it, ignoreCase = true) }
            .firstOrNull { it >= 0 } ?: 0
        val bereich = html.substring(start).let { b -> if (absaetzeIn(b).size >= 2) b else html }
        return absaetzeIn(bereich).filter { it.length > 50 && !STOP.containsMatchIn(it) }.distinct().take(60)
    }

    // Artikeltext ohne Fussnoten, wie am Desktop.
    fun wikipedia(html: String): List<String> {
        val start = html.indexOf("mw-content-text").takeIf { it >= 0 } ?: 0
        return absaetzeIn(SUP.replace(html.substring(start), "")).map { it.replace("­", "").replace(FUSSNOTE, "").trim() }
            .filter { it.length > 50 }.distinct().take(80)
    }

    private fun absaetzeIn(html: String): List<String> =
        ABSATZ.findAll(html).map { text(it.groupValues[2]) }.toList()

    private fun text(teil: String): String = entitaeten(MARKE.replace(teil, " ")).replace(LEER, " ").trim()

    private fun entitaeten(s: String): String = ENTITAET.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { zeichen(it) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { zeichen(it) } ?: m.value
            else -> when (e.lowercase()) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                "auml" -> if (e[0] == 'A') "Ä" else "ä"
                "ouml" -> if (e[0] == 'O') "Ö" else "ö"
                "uuml" -> if (e[0] == 'U') "Ü" else "ü"
                "szlig" -> "ß"; "euro" -> "€"; "ndash" -> "–"; "mdash" -> "—"; "hellip" -> "…"
                "lsquo" -> "‘"; "rsquo" -> "’"; "ldquo" -> "“"; "rdquo" -> "”"; "bdquo" -> "„"
                else -> m.value
            }
        }
    }

    private fun zeichen(c: Int): String =
        if (c in 1..0xFFFF) c.toChar().toString()
        else if (c in 0x10000..0x10FFFF) (c - 0x10000).let { charArrayOf((0xD800 + (it shr 10)).toChar(), (0xDC00 + (it and 0x3FF)).toChar()).concatToString() }
        else ""
}
