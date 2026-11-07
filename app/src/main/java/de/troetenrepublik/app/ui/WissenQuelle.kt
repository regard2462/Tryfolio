package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.troetenrepublik.app.engine.WissenQuellen
import de.troetenrepublik.app.market.MarketApi

// Quelle aus einer Lektion des Finanzwissens (0.68), geoeffnet wie eine Nachricht im NewsSheet. Ob der Text gleich
// dasteht oder zuerst die Seite mit „Nur Text anzeigen“ laedt, entscheidet dort allein Rss.textErlaubt(link).
@Composable
fun WissenQuelleBlatt(link: String, titel: String, onDismiss: () -> Unit) {
    val item = remember(link, titel) {
        MarketApi.NewsItem(
            title = titel.ifBlank { WissenQuellen.anbieter(link) },
            link = link,
            published = 0L,
            source = WissenQuellen.anbieter(link),
        )
    }
    NewsSheet(item, onDismiss, actionsTop = true)
}
