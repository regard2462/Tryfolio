package de.troetenrepublik.app.plattform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import de.troetenrepublik.app.ios.Dateien
import de.troetenrepublik.app.ios.alsBytes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

// Dateien auf iOS: Sichern ueber das Teilen-Menue (dort auch „In Dateien sichern“), Einlesen ueber die Dateiauswahl
// des Systems. Die Auswahl liefert eine Kopie der Datei; die App braucht keinen Zugriff auf andere Ordner.

// Oberster sichtbarer Bildschirm, von dem aus sich Teilen-Menue und Dateiauswahl oeffnen.
internal fun obersterBildschirm(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) vc = vc.presentedViewController ?: return vc
}

// Datei mit dateiname im Zwischenordner der App ablegen und das Teilen-Menue zeigen. fertig bekommt den Text und ob
// die Person das Sichern abgeschlossen hat; ein Abbruch meldet nichts.
@Composable
fun rememberSpeichern(inhalt: () -> String, mime: String = "application/json", fertig: (text: String, ok: Boolean) -> Unit): (dateiname: String) -> Unit {
    val text by rememberUpdatedState(inhalt)
    val ende by rememberUpdatedState(fertig)
    return { name ->
        val t = text()
        val pfad = NSTemporaryDirectory().trimEnd('/') + "/" + sauber(name)
        if (!Dateien.schreiben(pfad, t.encodeToByteArray())) ende(t, false)
        else teilen(pfad) { ok -> if (ok) ende(t, true) }
    }
}

// Datei einlesen: fertig bekommt den Text oder null, wenn die Datei nicht lesbar war; ein Abbruch meldet nichts.
@Composable
fun rememberOeffnen(fertig: (String?) -> Unit): () -> Unit {
    val ende by rememberUpdatedState(fertig)
    return {
        auswaehlen { _, daten ->
            ende(daten?.let { b -> b.decodeToString().takeIf { '�' !in it } ?: de.troetenrepublik.app.ios.jvm.dekodieren(b, java.nio.charset.Charset.forName("windows-1252")) })
        }
    }
}

// Datei als Bytes einlesen (PDF, Tabellen, CSV): fertig bekommt den Dateinamen und die Bytes oder null, wenn die Datei
// nicht lesbar war; ein Abbruch meldet nichts.
@Composable
fun rememberOeffnenRoh(fertig: (name: String, daten: ByteArray?) -> Unit): () -> Unit {
    val ende by rememberUpdatedState(fertig)
    return { auswaehlen { name, daten -> ende(name, daten) } }
}

// Datei aus dem Netz laden und ueber das Teilen-Menue anbieten (Sichern in Dateien, Oeffnen in einer anderen App).
// meldung bekommt true, wenn die Datei geladen ist, sonst false.
@Composable
fun rememberHerunterladen(meldung: (Boolean) -> Unit): (url: String, dateiname: String) -> Unit {
    val scope = rememberCoroutineScope()
    val melden by rememberUpdatedState(meldung)
    return { url, name ->
        scope.launch {
            val pfad = withContext(de.troetenrepublik.app.ios.ioDispatcher) {
                val bytes = Netz.bytes(url, 40 * 1024 * 1024, Netz.BROWSER) ?: return@withContext null
                (NSTemporaryDirectory().trimEnd('/') + "/" + sauber(name)).takeIf { Dateien.schreiben(it, bytes) }
            }
            melden(pfad != null)
            if (pfad != null) teilen(pfad) {}
        }
    }
}

private fun sauber(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }

private fun teilen(pfad: String, fertig: (Boolean) -> Unit) {
    val vc = obersterBildschirm() ?: return fertig(false)
    val menue = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(pfad)), applicationActivities = null)
    menue.completionWithItemsHandler = { _, ok, _, _ -> fertig(ok) }
    // iPad: Das Menue braucht einen Ankerpunkt
    menue.popoverPresentationController?.sourceView = vc.view
    vc.presentViewController(menue, animated = true, completion = null)
}

// Die Dateiauswahl haelt ihren Empfaenger nur schwach; er bleibt hier, bis eine Datei gewaehlt oder abgebrochen ist.
private var offeneAuswahl: Auswahl? = null

private class Auswahl(private val fertig: (String, ByteArray?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        offeneAuswahl = null
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val zugriff = url.startAccessingSecurityScopedResource()
        val daten = NSData.dataWithContentsOfURL(url)?.alsBytes()
        if (zugriff) url.stopAccessingSecurityScopedResource()
        fertig(url.lastPathComponent ?: "datei", daten)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        offeneAuswahl = null
    }
}

private fun auswaehlen(fertig: (String, ByteArray?) -> Unit) {
    val vc = obersterBildschirm() ?: return
    val a = Auswahl(fertig)
    offeneAuswahl = a
    val wahl = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
    wahl.delegate = a
    wahl.allowsMultipleSelection = false
    vc.presentViewController(wahl, animated = true, completion = null)
}
