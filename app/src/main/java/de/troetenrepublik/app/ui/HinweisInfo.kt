package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.HinweisTexte
import de.troetenrepublik.app.i18n.I18n

internal fun hinweisText(key: String): String = HinweisTexte.text(I18n.lang == Lang.DE, key)

private const val KURZ_MAX = 120

// Textblock unter einem Werkzeug oder einer Ansicht: eine Kernzeile, die Methode steht hinter dem Info-Symbol.
// Ohne `kurz` ist die Kernzeile die erste Zeile (Pflichthinweis) oder der erste Satz, das Blatt zeigt den Rest.
// Kurze Texte (eine Zeile, kein Absatz) stehen ganz da.
@Composable
fun HinweisInfo(text: String, kurz: String? = null, modifier: Modifier = Modifier) {
    val teile = remember(text, kurz) { hinweisTeilen(text, kurz) }
    if (teile == null) ToolNote(text)
    else InfoZeile(teile.first, hinweisText("titel"), teile.second, modifier)
}

internal fun hinweisTeilen(text: String, kurz: String?): Pair<String?, String>? {
    val t = text.trim()
    if (kurz != null) return kurz to t
    val zeile = t.indexOf('\n').let { if (it < 0) t.length else it }
    if (zeile == t.length && t.length <= 110) return null
    val erste = t.substring(0, zeile)
    if (erste.startsWith("•")) return null to t
    val ende = if (erste.length <= KURZ_MAX) erste.length else erste.indexOf(". ").let { if (it in 1 until KURZ_MAX) it + 1 else -1 }
    if (ende < 0) return null to t
    val rest = t.substring(ende).trim()
    return if (rest.isEmpty()) null else t.substring(0, ende).trim() to rest
}
