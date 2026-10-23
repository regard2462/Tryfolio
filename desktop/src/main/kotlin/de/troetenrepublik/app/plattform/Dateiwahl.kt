package de.troetenrepublik.app.plattform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import de.troetenrepublik.app.i18n.S
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

// Fenster der App, an dem die Datei-Dialoge haengen (Main.kt setzt es).
var hauptfenster: Frame? = null

// Datei sichern mit dem Datei-Dialog des Systems. fertig bekommt den Text und ob das Schreiben geklappt hat;
// ein Abbruch meldet nichts.
@Composable
fun rememberSpeichern(inhalt: () -> String, mime: String = "application/json", fertig: (text: String, ok: Boolean) -> Unit): (dateiname: String) -> Unit {
    val text by rememberUpdatedState(inhalt)
    val ende by rememberUpdatedState(fertig)
    return { name ->
        val dialog = FileDialog(hauptfenster, S.exportPortfolio, FileDialog.SAVE).apply { file = name }
        dialog.isVisible = true
        val datei = dialog.file?.let { File(dialog.directory, it) }
        if (datei != null) {
            val t = text()
            ende(t, runCatching { datei.writeText(t, Charsets.UTF_8) }.isSuccess)
        }
    }
}

// Datei einlesen: fertig bekommt den Text oder null, wenn die Datei nicht lesbar war; ein Abbruch meldet nichts.
@Composable
fun rememberOeffnen(fertig: (String?) -> Unit): () -> Unit {
    val ende by rememberUpdatedState(fertig)
    return {
        val dialog = FileDialog(hauptfenster, S.importPortfolio, FileDialog.LOAD)
        dialog.isVisible = true
        dialog.file?.let { File(dialog.directory, it) }?.let { datei ->
            ende(runCatching { datei.readBytes().let { b -> b.toString(Charsets.UTF_8).takeIf { '\uFFFD' !in it } ?: b.toString(charset("windows-1252")) } }.getOrNull())
        }
    }
}

// Datei als Bytes einlesen (PDF, Tabellen, CSV fuer den Import von Registerdaten, seit 5.9): fertig bekommt den
// Dateinamen und die Bytes oder null, wenn die Datei nicht lesbar war; ein Abbruch meldet nichts.
@Composable
fun rememberOeffnenRoh(fertig: (name: String, daten: ByteArray?) -> Unit): () -> Unit {
    val ende by rememberUpdatedState(fertig)
    return {
        val dialog = FileDialog(hauptfenster, S.imTitel, FileDialog.LOAD)
        dialog.isVisible = true
        dialog.file?.let { File(dialog.directory, it) }?.let { datei -> ende(datei.name, runCatching { datei.readBytes() }.getOrNull()) }
    }
}
