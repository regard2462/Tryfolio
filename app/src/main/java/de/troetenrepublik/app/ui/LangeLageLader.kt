package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.engine.Damodaran
import de.troetenrepublik.app.engine.Epu
import de.troetenrepublik.app.engine.Gpr
import de.troetenrepublik.app.engine.KonfliktReihe
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.LangeLageTexte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Mitgelieferte Datei, einmal gelesen und fuer alle Karten gehalten (wie LangreihenLader). Gilt je Prozess; die Daten
// kommen ueber Daten.text, also mit dem Stand, den das Datenbuendel zuletzt geliefert hat.
internal class DateiLader<T : Any>(private val lese: () -> T?) {
    @Volatile private var wert: T? = null
    @Volatile private var versucht = false

    fun gespeichert(): T? = wert

    suspend fun laden(): T? {
        if (versucht) return wert
        val d = withContext(Dispatchers.Default) { runCatching(lese).getOrNull() }
        wert = d
        versucht = true
        return d
    }
}

internal object LangeLageDaten {
    val gpr = DateiLader { Gpr.lesen(de.troetenrepublik.app.market.Daten.text(Gpr.DATEI)) }
    val epu = DateiLader { Epu.lesen(de.troetenrepublik.app.market.Daten.text(Epu.DATEI)) }
    val damodaran = DateiLader { Damodaran.lesen(de.troetenrepublik.app.market.Daten.text(Damodaran.DATEI)) }
    val konflikte = DateiLader { KonfliktReihe.lesen(de.troetenrepublik.app.market.Daten.text(KonfliktReihe.DATEI)) }
}

// Datei der Karte: sofort der gehaltene Stand, sonst nach dem Lesen im Hintergrund
@Composable
internal fun <T : Any> DateiLader<T>.rememberDatei(): T? {
    var d by remember { mutableStateOf(gespeichert()) }
    LaunchedEffect(Unit) { if (d == null) d = laden() }
    return d
}

// Text der Karten in der Sprache der App
internal fun langeText(key: String, vararg w: String): String = LangeLageTexte.t(key, *w)

// Tag der Datei (JJJJ-MM-TT) als Datum der Anzeigesprache
internal fun tagDatum(tag: String): String {
    val t = tag.split("-")
    return if (t.size == 3 && I18n.locale.language == "de") t[2] + "." + t[1] + "." + t[0] else tag
}
