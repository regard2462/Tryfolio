package de.troetenrepublik.app.plattform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

// Fragt mit dem vorgeschlagenen Namen nach dem Ziel und schreibt inhalt dorthin. Ein Abbruch meldet nichts.
// mime ist die Dateiart fuer die Ablage, damit der Name seine Endung behaelt.
@Composable
fun rememberSpeichern(inhalt: () -> String, mime: String = "application/json", fertig: (text: String, ok: Boolean) -> Unit): (dateiname: String) -> Unit {
    val context = LocalContext.current
    val text by rememberUpdatedState(inhalt)
    val ende by rememberUpdatedState(fertig)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Wenige Kilobyte: direkt schreiben, damit nichts an einer abgebrochenen Coroutine haengt.
        val t = text()
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(t.toByteArray(Charsets.UTF_8)); it.flush() } != null
        }.getOrDefault(false)
        ende(t, ok)
    }
    return { name -> launcher.launch(name) }
}

// UTF-8, sonst Windows-1252 (CSV-Exporte deutscher Banken).
internal fun textAus(b: ByteArray): String =
    b.toString(Charsets.UTF_8).takeIf { '\uFFFD' !in it } ?: b.toString(charset("windows-1252"))

// fertig bekommt null, wenn die Datei nicht lesbar war. Ein Abbruch meldet nichts.
@Composable
fun rememberOeffnen(fertig: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val ende by rememberUpdatedState(fertig)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ende(runCatching { context.contentResolver.openInputStream(uri)?.use { textAus(it.readBytes()) } }.getOrNull())
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) }
}

// Datei als Bytes einlesen (PDF, Tabellen, CSV fuer den Import von Registerdaten, seit 5.9): fertig bekommt den
// Dateinamen und die Bytes oder null, wenn die Datei nicht lesbar war. Ein Abbruch meldet nichts.
@Composable
fun rememberOeffnenRoh(fertig: (name: String, daten: ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val ende by rememberUpdatedState(fertig)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        ende(name, runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull())
    }
    return { launcher.launch(arrayOf("*/*")) }
}
