package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Einflussverzeichnis
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import java.time.LocalDate
import java.time.YearMonth

// [Grosses Ganzes 0.73] Gemeinsame Bausteine fuer Karte, Wirkungsnetz und Lagebild.

// Vorsatz, mit dem Blaetter ueber ihren Symbol-Oeffner (onOpen) zum Wirkungsnetz einer Groesse springen
internal const val WIRKUNG_PRAEFIX = "wirkung:"

internal fun istDeutsch(): Boolean = I18n.lang == Lang.DE

// Name einer Einflussgroesse: Engpaesse, Spannungsgebiete und ENSO tragen ihre Namen in den Texten der Seiten
internal fun groesseName(g: Einflussverzeichnis.Groesse): String = when (g.typ) {
    Einflussverzeichnis.Typ.ENGPASS -> S.engName(g.kurz)
    Einflussverzeichnis.Typ.SPANNUNG -> S.spName(g.kurz)
    Einflussverzeichnis.Typ.KLIMA -> S.ensoTitel
    else -> g.name(istDeutsch()).ifBlank { g.id }
}

internal fun groesseName(i: Einflussverzeichnis.Index, id: String): String = i.groessen[id]?.let { groesseName(it) } ?: id

// Name eines Landes (alpha-2) in der App-Sprache; ohne Eintrag das Kuerzel
internal fun landName(namen: Map<String, Pair<String, String>>, a2: String): String =
    namen[a2]?.let { if (istDeutsch()) it.first else it.second } ?: a2

// [0.77] Kurzer Laendername fuer Werte in Zeilen und fuer Tabellenspalten, damit der Name nicht umbricht
private val LAND_KURZ = mapOf(
    "Demokratische Republik Kongo" to "DR Kongo", "Vereinigte Staaten" to "USA", "Vereinigte Staaten von Amerika" to "USA",
    "Vereinigtes Königreich" to "Großbritannien", "Vereinigte Arabische Emirate" to "VAE", "Zentralafrikanische Republik" to "Zentralafrika",
    "Dominikanische Republik" to "Dom. Republik", "Bosnien und Herzegowina" to "Bosnien", "Russische Föderation" to "Russland",
    "Democratic Republic of the Congo" to "DR Congo", "Congo, Democratic Republic of the" to "DR Congo", "United States" to "USA",
    "United States of America" to "USA", "United Kingdom" to "UK", "United Arab Emirates" to "UAE", "Central African Republic" to "CAR",
    "Dominican Republic" to "Dominican Rep.", "Bosnia and Herzegovina" to "Bosnia", "Russian Federation" to "Russia",
    "Volksrepublik China" to "China", "People's Republic of China" to "China", "Republik Kongo" to "Kongo", "Republic of the Congo" to "Congo",
    "Republik Korea" to "Südkorea", "Republic of Korea" to "South Korea",
)

internal fun landKurz(name: String): String = LAND_KURZ[name] ?: name

// Monat und Jahr eines Tages seit 1970
internal fun monatJahr(tag: Long): String = Fmt.monthYear(YearMonth.from(LocalDate.ofEpochDay(tag)))

// Kennzeichnung unter einer Zeile: Art (gemeldet, gemessen, abgeleitet) und Stand, dezent
internal fun artStand(art: Einflussverzeichnis.Art?, stand: Long?): String? =
    listOfNotNull(art?.let { S.abhArt(it.name) }, stand?.takeIf { it > 0 }?.let { S.abhStand(monatJahr(it)) }).joinToString(" · ").ifEmpty { null }

// Eine Zeile mit Bezeichnung links und Wert rechts (der Wert bricht nie um, lange Bezeichnungen laufen in die naechste
// Zeile), darunter Einzelheiten und die dezente Kennzeichnung. grau: veralteter Stand. Mit onClick ein Pfeil rechts.
@Composable
internal fun WeltZeile(
    label: String, wert: String, einzelheiten: List<String> = emptyList(), kennzeichnung: String? = null,
    grau: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (grau) 0.55f else 1f).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (wert.isNotEmpty()) Text(wert, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, softWrap = false, maxLines = 1)
            if (onClick != null) {
                ChevronRight(Modifier.padding(top = 5.dp))
            }
        }
        einzelheiten.filter { it.isNotBlank() }.forEach { PunktText(it, style = MaterialTheme.typography.bodySmall, color = sv) }
        kennzeichnung?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = sv.copy(alpha = 0.75f)) }
    }
}
