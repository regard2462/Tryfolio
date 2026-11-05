package de.troetenrepublik.app.plattform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler

// Seite einer Quelle auf iPhone und iPad: kein eingebauter Browser mit Download-Erkennung. Die Seite oeffnet
// sich im Standardbrowser, die geladene Datei kommt ueber den Import in die App.

fun quellenImBrowser(): Boolean = false

@Composable
fun QuellenBrowser(
    adresse: String,
    endungen: List<String>,
    muster: String,
    modifier: Modifier,
    onDatei: (name: String, daten: ByteArray) -> Unit,
    onFehler: () -> Unit,
    onSchliessen: () -> Unit,
) {
    val links = LocalUriHandler.current
    val fehler by rememberUpdatedState(onFehler)
    val schliessen by rememberUpdatedState(onSchliessen)
    LaunchedEffect(adresse) {
        if (runCatching { links.openUri(adresse) }.isFailure) fehler()
        schliessen()
    }
}
