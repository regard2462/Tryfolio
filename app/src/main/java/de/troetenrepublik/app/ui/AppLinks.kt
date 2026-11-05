package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import de.troetenrepublik.app.engine.AppLinkRegel
import de.troetenrepublik.app.engine.WissenQuellen
import de.troetenrepublik.app.plattform.Plattform

// UriHandler der Plattform, der immer extern oeffnet (Browser).
val LocalExterneLinks = staticCompositionLocalOf<UriHandler?> { null }

// Fuer Knoepfe wie "Im Browser oeffnen" und fuer Anmeldeseiten.
@Composable
fun externeLinks(): UriHandler = LocalExterneLinks.current ?: LocalUriHandler.current

private class AppLinkOeffner(private val extern: UriHandler, private val zeigen: (String) -> Unit) : UriHandler {
    override fun openUri(uri: String) {
        val a = WissenQuellen.adresse(uri)
        if (Plattform.webAnsicht && a != null && AppLinkRegel.imApp(a)) zeigen(a) else extern.openUri(uri)
    }
}

// Wurzel der Oberflaeche: Webseiten aus der ganzen App oeffnen im Betrachter der App, Ausnahmen
// (AppLinkRegel) und andere Plattformen extern.
@Composable
fun AppLinks(content: @Composable () -> Unit) {
    val extern = LocalUriHandler.current
    var link by remember { mutableStateOf<String?>(null) }
    val oeffner = remember(extern) { AppLinkOeffner(extern) { link = it } }
    CompositionLocalProvider(LocalExterneLinks provides extern, LocalUriHandler provides oeffner) {
        content()
        link?.let { l -> WissenQuelleBlatt(l, "") { link = null } }
    }
}
