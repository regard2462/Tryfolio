package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Halbjahr
import de.troetenrepublik.app.i18n.HalbjahrTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S

internal fun halbjahrText(key: String, vararg w: String): String = HalbjahrTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Halbjahresregel eines Index: mittlere Rendite von November bis April gegen Mai bis Oktober und wie oft der Winter vorn
// lag; aufgeklappt beide Halbjahre als Balken und der Vergleich „nur im Winter investiert“ gegen „durchgehend investiert“
// als Rendite pro Jahr (Zeile im Abschnitt „Muster der Vergangenheit“, ui/MusterVergangenheit.kt)
@Composable
internal fun HalbjahrZeile(x: Halbjahr.Index, monat: Int, auf: Boolean, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    MusterZeile(
        halbjahrText("name", S.vmIndex(x.id)), Fmt.signedPct(x.winter.mittel, 1),
        halbjahrText("satz", Fmt.signedPct(x.sommer.mittel, 1), x.winterVorn.toString(), x.jahre.toString(), x.vonJahr.toString()), auf, onClick,
    ) {
        val werte = listOf(x.winter, x.sommer)
        val namen = listOf(halbjahrText("winter"), halbjahrText("sommer"))
        MusterBalken(werte, if (Halbjahr.imWinter(monat)) 0 else 1, name = { namen[it] }) { i ->
            S.vmBalkenKopf(namen[i], Fmt.signedPct(werte[i].mittel, 1), werte[i].positiv, werte[i].anzahl)
        }
        Spacer(Modifier.height(6.dp))
        Text(halbjahrText("proJahr"), style = MaterialTheme.typography.labelSmall, color = grau)
        NameUndWert(halbjahrText("halten"), Fmt.signedPct(x.haltenJahr, 1), Modifier.padding(top = 2.dp))
        NameUndWert(halbjahrText("regel"), Fmt.signedPct(x.regelJahr, 1), Modifier.padding(top = 2.dp))
        Text(
            halbjahrText(if (x.haltenJahr >= x.regelJahr) "fazit.halten" else "fazit.regel"), style = MaterialTheme.typography.bodySmall,
            color = grau, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            halbjahrText("grundlage", S.vmGrundlage(x.id, x.vonJahr, x.bisJahr)), style = MaterialTheme.typography.labelSmall, color = grau,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
