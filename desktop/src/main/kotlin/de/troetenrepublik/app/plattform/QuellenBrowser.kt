package de.troetenrepublik.app.plattform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.SelbstabrufDatei
import de.troetenrepublik.app.i18n.S
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Seite einer Quelle ohne eigenen Browser (seit 5.93): Die App oeffnet die Seite im Standardbrowser und beobachtet
// den Downloads-Ordner. Eine neue Datei mit passender Endung und Namensmuster uebernimmt sie automatisch, nur eine je
// Oeffnen und hoechstens 15 Minuten lang. Der Ordnerpfad erscheint nur in der Anzeige, nie in einem Web-Abruf.

// Der Desktop hat keinen eingebauten Browser; die Seite oeffnet sich im Standardbrowser.
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
    val datei by rememberUpdatedState(onDatei)
    val fehler by rememberUpdatedState(onFehler)
    val schliessen by rememberUpdatedState(onSchliessen)
    val ordner = remember { downloadOrdner() }

    Column(modifier.padding(20.dp)) {
        Text(S.saWartet(ordner.path), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = schliessen) { Text(S.cancel) }
    }

    LaunchedEffect(adresse) {
        val start = System.currentTimeMillis()
        // Standardbrowser oeffnen; ohne Browser gibt es nichts zu beobachten
        if (!withContext(Dispatchers.IO) { Browser.oeffnen(adresse) }) {
            fehler()
            return@LaunchedEffect
        }
        val groessen = HashMap<String, Long>()
        val abgelehnt = HashSet<String>()
        while (System.currentTimeMillis() - start < 15 * 60_000L) {
            delay(1500)
            val kandidaten = withContext(Dispatchers.IO) {
                ordner.listFiles()?.filter {
                    it.isFile && it.lastModified() >= start - 2000 && it.length() <= 40L * 1024 * 1024 &&
                        it.name !in abgelehnt && SelbstabrufDatei.passt(it.name, endungen, muster)
                }.orEmpty()
            }
            for (f in kandidaten) {
                // Erst lesen, wenn die Groesse zwischen zwei Runden gleich ist (der Browser schreibt noch)
                if (!SelbstabrufDatei.stabil(groessen[f.name], f.length())) {
                    groessen[f.name] = f.length()
                    continue
                }
                try {
                    datei(f.name, withContext(Dispatchers.IO) { f.readBytes() })
                    return@LaunchedEffect
                } catch (e: IOException) {
                    // nicht lesbare Datei in dieser Runde nicht wieder versuchen
                    abgelehnt += f.name
                }
            }
        }
        fehler()
        schliessen()
    }
}

// Downloads-Ordner des Nutzers: unter Linux aus .config/user-dirs.dirs (lokalisierter Ordner), sonst Downloads im
// Benutzerverzeichnis, sonst das Benutzerverzeichnis selbst (wie freieDatei in Herunterladen.kt).
private fun downloadOrdner(): File {
    val heim = File(System.getProperty("user.home"))
    val xdg = File(heim, ".config/user-dirs.dirs").takeIf { it.isFile }
        ?.let { SelbstabrufDatei.downloadOrdnerAusXdg(it.readText(), heim.path) }
        ?.let(::File)
        ?.takeIf { it.isDirectory }
    if (xdg != null) return xdg
    return File(heim, "Downloads").takeIf { it.isDirectory } ?: heim
}
