package de.troetenrepublik.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.troetenrepublik.app.engine.Kursmuster
import de.troetenrepublik.app.i18n.S

// Saisonmuster eines Index: mittlere Rendite je Kalendermonat und wie oft der Monat im Plus endete, der laufende Monat
// hervorgehoben (Zeile im Abschnitt „Muster der Vergangenheit“, ui/MusterVergangenheit.kt)
@Composable
internal fun SaisonZeile(x: Kursmuster.Index, monat: Int, auf: Boolean, onClick: () -> Unit) {
    val w = x.monate.getOrNull(monat - 1) ?: return
    val ab = x.monatVon.take(4).toIntOrNull() ?: x.vonJahr
    MusterZeile(S.vmSaisonName(S.vmIndex(x.id), S.vmMonat(monat)), Fmt.signedPct(w.mittel, 1), S.vmSaisonSatz(w.positiv, w.anzahl, ab), auf, onClick) {
        MusterBalken(x.monate, monat - 1, name = { S.vmMonat(it + 1).take(1) }) { i ->
            S.vmBalkenKopf(S.vmMonat(i + 1), Fmt.signedPct(x.monate[i].mittel, 1), x.monate[i].positiv, x.monate[i].anzahl)
        }
        Text(S.vmGrundlage(x.id, ab, x.monatBis.take(4).toIntOrNull() ?: x.bisJahr), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
