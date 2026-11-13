package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.engine.WichtigThemen
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.WichtigTexte
import de.troetenrepublik.app.market.Rss

// Unterkategorie „Durchbrüche“ im Reiter „Wichtigste“ der News (0.79): eine Zeile mit der Zusammenfassung (Meldungen und
// Themen) und dem Pfeil. Aufgeklappt folgen die Themen wie in Maerkte, Trends, jedes wieder eingeklappt mit Zahl; darin
// die Meldungen (ein Tipp oeffnet den Artikel) und der Weg zum Thema. Jede Zeile ist ein eigener Eintrag der Liste.
internal fun LazyListScope.wichtigDurchbrueche(
    teil: WichtigThemen.DurchbruchTeil,
    deutsch: Boolean,
    jetzt: Long,
    offen: Boolean,
    gruppe: String?,
    onOffen: () -> Unit,
    onGruppe: (String) -> Unit,
    onArtikel: (Rss.Eintrag) -> Unit,
    onThema: (String) -> Unit,
) {
    item(key = "wd-kopf", contentType = "wd-kopf") {
        Row(
            Modifier.fillMaxWidth().zeilenKlick(onClick = onOffen).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.dbTitel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                WichtigTexte.durchbrueche(deutsch, teil.anzahl, teil.themen), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false,
            )
            Spacer(Modifier.width(8.dp))
            AufklappPfeil(offen)
        }
    }
    if (offen) teil.gruppen.forEach { g ->
        val id = g.thema.id
        val auf = gruppe == id
        item(key = "wd-g-$id", contentType = "wd-gruppe") {
            Column(Modifier.fillMaxWidth()) {
                Trennlinie()
                Row(
                    Modifier.fillMaxWidth().zeilenKlick(onClick = { onGruppe(id) }).padding(start = 36.dp, end = 20.dp).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g.thema.name(deutsch), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(S.dbNeuAnzahl(g.meldungen.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    AufklappPfeil(auf)
                }
            }
        }
        if (auf) {
            g.meldungen.forEach { m ->
                item(key = "wd-m-$id-${m.link}", contentType = "wd-meldung") {
                    val alter = Durchbrueche.alter(m.zeit, jetzt)
                    Column(
                        Modifier.fillMaxWidth()
                            .zeilenKlick(onClick = { onArtikel(WichtigThemen.alsEintrag(m)) })
                            .padding(start = 36.dp, end = 20.dp).padding(vertical = 8.dp),
                    ) {
                        Text(m.titel, style = MaterialTheme.typography.bodyMedium.schlagzeile())
                        PunktText(
                            listOfNotNull(m.quelle.takeIf { it.isNotBlank() }, S.dbAlter(alter.wert, alter.einheit)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "wd-w-$id", contentType = "wd-weg") {
                Row(
                    Modifier.fillMaxWidth().zeilenKlick(onClick = { onThema(id) }).padding(start = 36.dp, end = 20.dp).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(WichtigTexte.zumThema(deutsch), style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ChevronRight()
                }
            }
        }
    }
    item(key = "wd-ende", contentType = "wd-ende") { Trennlinie() }
}
