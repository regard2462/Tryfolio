package de.troetenrepublik.app.plattform

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Datei aus dem Netz in den Download-Ordner laden (seit 5.92, etwa PDFs der Meldungen von Politikern). Android: ueber
// den DownloadManager des Systems mit Benachrichtigung; ein Tipp darauf oeffnet die Datei. Bis Android 9 landet sie im
// Download-Ordner der App, weil der allgemeine Ordner dort eine Speichererlaubnis braucht.
// meldung bekommt true, sobald der Download laeuft, false, wenn er nicht starten konnte.
@Composable
fun rememberHerunterladen(meldung: (Boolean) -> Unit): (url: String, dateiname: String) -> Unit {
    val context = LocalContext.current.applicationContext
    return { url, name -> meldung(herunterladen(context, url, name)) }
}

private fun herunterladen(context: Context, url: String, name: String): Boolean = runCatching {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val r = DownloadManager.Request(Uri.parse(url))
        .setTitle(name)
        .setMimeType(if (name.endsWith(".pdf", true)) "application/pdf" else "application/octet-stream")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        // Allgemeine Browser-Kennung wie bei den Logos, keine Angaben zum Nutzer
        .addRequestHeader("User-Agent", de.troetenrepublik.app.market.Browserkennung.android())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    else r.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
    dm.enqueue(r)
    true
}.getOrDefault(false)
