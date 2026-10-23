package de.troetenrepublik.app.plattform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Laedt die Artikelseite und liest die Absaetze mit Jsoup aus dem HTML, nach denselben Regeln wie die Lese-Skripte der
// Web-Ansicht auf Android. Seiten, die ihren Text per Skript nachladen oder erst eine Einwilligung verlangen, liefern
// hier nichts, dann bleibt Im Browser oeffnen.
@Composable
fun ArtikelLeser(
    adresse: String,
    wikipedia: Boolean,
    auslesen: Boolean,
    modifier: Modifier,
    textDa: () -> Boolean,
    onAbsaetze: (List<String>) -> Unit,
    onEnde: () -> Unit,
) {
    val aufnehmen by rememberUpdatedState(onAbsaetze)
    val ende by rememberUpdatedState(onEnde)
    LaunchedEffect(adresse, auslesen) {
        if (!auslesen) return@LaunchedEffect
        val liste = withContext(Dispatchers.IO) { Artikel.lesen(adresse, wikipedia) }
        if (liste != null) aufnehmen(liste)
        ende()
    }
    Box(modifier)
}

internal object Artikel {
    fun lesen(adresse: String, wikipedia: Boolean): List<String>? {
        val bytes = Netz.bytes(adresse) ?: return null
        val doc = runCatching { Jsoup.parse(bytes.inputStream(), null, adresse) }.getOrNull() ?: return null
        return if (wikipedia) wikipedia(doc) else absaetze(doc)
    }

    // Zuerst der Artikelbereich, sonst die Seite, wie EXTRACT_SCRIPT.
    fun absaetze(doc: Document): List<String> {
        val wurzeln = listOf("article", "[class*=caas-body]", "[class*=article-body]", "[class*=articleBody]", "[itemprop=articleBody]", "main")
        val root: Element = wurzeln.firstNotNullOfOrNull { sel -> doc.selectFirst(sel)?.takeIf { it.select("p").size >= 2 } } ?: doc.body()
        val stop = Regex("cookie|Cookies|consent|Einwilligung|privacy", RegexOption.IGNORE_CASE)
        return root.select("p").map { it.text().replace(Regex("\\s+"), " ").trim() }
            .filter { it.length > 50 && !stop.containsMatchIn(it) }
            .distinct().take(60)
    }

    // Artikeltext ohne style, script, Fussnoten und Bearbeiten-Links, wie WIKI_SCRIPT.
    fun wikipedia(doc: Document): List<String> {
        val root = doc.selectFirst("#mw-content-text") ?: doc.body()
        return root.select("p").map { p ->
            val c = p.clone()
            c.select("style, script, sup, .mw-editsection, .noprint").remove()
            c.text().replace("­", "").replace(Regex("\\[\\d+\\]"), "").replace(Regex("\\s+"), " ").trim()
        }.filter { it.length > 50 }.distinct().take(80)
    }
}
