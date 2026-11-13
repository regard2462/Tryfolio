package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.IpoUebersicht
import de.troetenrepublik.app.engine.LaenderZusatz
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.IpoKalender
import kotlin.math.abs

// Gemeinsame Bausteine der Boersengaenge (0.77): Zeile mit Logo, Name, Stand als farbige Marke und dem wichtigsten Wert
// und der Kopf mit den drei Zahlen. Benutzt von der Seite der Boersengaenge und
// vom Abschnitt unter Maerkte, Bewegungen.

private const val IPO_LOGO = 36

// Logo mit festem Platz: mit Kuerzel der Logo-Baustein der App (ohne Logo dessen Buchstabenfeld), ohne Kuerzel der
// Anfangsbuchstabe des Namens im Kreis.
@Composable
internal fun IpoLogo(a: IpoKalender.Angebot, groesse: Int = IPO_LOGO) {
    val symbol = a.symbol
    if (symbol != null) {
        InstrumentLogo(symbol, a.name, groesse)
        return
    }
    val buchstabe = a.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "–"
    Box(
        Modifier.size(groesse.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(buchstabe, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (groesse * 0.4).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

// Kleine Marke: Text in der Farbe auf einem Grund in derselben Farbe, schwach.
@Composable
internal fun IpoMarke(text: String, farbe: Color) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = farbe, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).background(farbe.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

// Farbe des Stands: anstehend und eingereicht je ein Akzent der Kategoriefarben, gestartet nach der Entwicklung in Gruen
// oder Rot, zurueckgezogen gedaempft.
@Composable
internal fun ipoMarkenFarbe(status: IpoKalender.Status, entwicklung: Double?): Color = when (status) {
    IpoKalender.Status.ANGEKUENDIGT -> Kategorie.farbe(0)
    IpoKalender.Status.EINGEREICHT -> Kategorie.farbe(2)
    IpoKalender.Status.PREIS -> if (entwicklung != null && entwicklung != 0.0) LocalSignal.current.forChange(entwicklung) else Kategorie.farbe(3)
    IpoKalender.Status.ZURUECK -> MaterialTheme.colorScheme.onSurfaceVariant
}

// Eine Zeile: Logo, Name einzeilig, darunter Stand mit Datum als Marke und Kuerzel, Boerse; rechts der wichtigste Wert
// (Entwicklung seit Ausgabe, sonst Preis oder Spanne, sonst Volumen), darunter klein das Volumen. Der Betrag im
// Prospekt eines eingereichten Angebots ohne Preis ist ein Platzhalter und steht als „laut Prospekt“.
@Composable
internal fun IpoZeile(a: IpoKalender.Angebot, seit: Double?, ersterTag: Double?, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val entwicklung = seit ?: ersterTag
    val datum = a.tag?.let { Fmt.dayMonthNum(it * IPO_TAG_MS + 12 * 3_600_000L) }.orEmpty()
    val stand = S.ipoStatusTag(a.status.ordinal, datum).trim()
    val preis = a.preis?.let(::ipoPreisText)
    val platzhalter = IpoUebersicht.platzhalter(a)
    val volumen = a.volumen?.let(::ipoDollar)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IpoLogo(a)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(a.name, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                IpoMarke(stand, ipoMarkenFarbe(a.status, entwicklung))
                Spacer(Modifier.width(8.dp))
                PunktZeile(
                    listOfNotNull(a.symbol, a.boerse?.let(::boerseKurz), ersterTag?.let { S.ipoErsterTag(Fmt.signedPct(it, 1)) }),
                    Modifier.weight(1f), color = grau, maxZeilen = 1,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            when {
                seit != null -> MetricValue(seit, Fmt.pct(abs(seit), 1))
                preis != null -> Text(preis, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                volumen != null -> Text(volumen, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            }
            val unten = when {
                // Platzhalterbetrag: der Wert steht oben, der Zusatz klein darunter
                platzhalter -> if (volumen != null) S.ipoLautProspektKurz else null
                seit != null || preis != null -> volumen
                else -> null
            }
            if (unten != null || a.spac) Row(verticalAlignment = Alignment.CenterVertically) {
                if (a.spac) {
                    IpoMarke("SPAC", LocalSignal.current.warn)
                    if (unten != null) Spacer(Modifier.width(6.dp))
                }
                if (unten != null) Text(unten, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
            }
        }
    }
}

// Kopf der Uebersicht: anstehend, eingereicht und in den letzten 30 Tagen gestartet, je Zahl in der Farbe der Marke.
@Composable
internal fun IpoKopf(k: IpoUebersicht.Kopf, onWahl: (Int) -> Unit) {
    val zahlen = listOf(k.anstehend, k.eingereicht, k.gestartet)
    val farben = listOf(Kategorie.farbe(0), Kategorie.farbe(2), Kategorie.farbe(3))
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        for (i in 0..2) {
            Column(Modifier.weight(1f).clickable { onWahl(i) }.padding(end = 8.dp)) {
                Box(Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(farben[i]))
                Text(zahlen[i].toString(), style = MaterialTheme.typography.headlineSmall, color = farben[i], maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 6.dp))
                Text(S.ipoKopfLabel(i), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                if (i == 2) Text(S.ipoKopfTage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}
