package de.troetenrepublik.app.plattform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

// Datei aus dem Netz in den Download-Ordner laden (seit 5.92, etwa PDFs der Meldungen von Politikern). Desktop: in den
// Ordner Downloads im Benutzerverzeichnis (sonst das Benutzerverzeichnis selbst), danach mit dem Standardprogramm oeffnen.
// Gleichnamige Dateien bekommen eine Nummer. meldung bekommt true, wenn die Datei gespeichert ist, sonst false.
@Composable
fun rememberHerunterladen(meldung: (Boolean) -> Unit): (url: String, dateiname: String) -> Unit {
    val scope = rememberCoroutineScope()
    return { url, name ->
        scope.launch {
            val datei = withContext(Dispatchers.IO) {
                val bytes = Netz.bytes(url, 40 * 1024 * 1024, Netz.BROWSER) ?: return@withContext null
                runCatching { freieDatei(name).also { it.writeBytes(bytes) } }.getOrNull()
            }
            meldung(datei != null)
            if (datei != null) withContext(Dispatchers.IO) {
                runCatching { if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) Desktop.getDesktop().open(datei) }
            }
        }
    }
}

private fun freieDatei(name: String): File {
    val heim = File(System.getProperty("user.home"))
    val ordner = File(heim, "Downloads").takeIf { it.isDirectory } ?: heim
    val sauber = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
    var f = File(ordner, sauber)
    var n = 2
    while (f.exists()) {
        f = File(ordner, sauber.substringBeforeLast('.') + " ($n)." + sauber.substringAfterLast('.', "pdf"))
        n++
    }
    return f
}
