package de.troetenrepublik.app.plattform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// Auf Android wirkt der Bildschutz (Einstellung Screenshots im Profil).
val bildschutzMoeglich: Boolean = true

// Zahl der Stellen, die gerade Schutz verlangen (ganze App, Broker-Seite); erst bei null wird er aufgehoben.
private var schutzZaehler = 0

// Solange aktiv: keine Screenshots und Bildschirmaufnahmen, in der App-Uebersicht geschwaerzt.
// Auf Windows und Linux ohne Wirkung.
@Composable
fun Bildschutz(aktiv: Boolean) {
    val act = LocalContext.current.activity() ?: return
    DisposableEffect(aktiv, act) {
        if (aktiv) {
            schutzZaehler++
            act.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (aktiv) {
                schutzZaehler = (schutzZaehler - 1).coerceAtLeast(0)
                if (schutzZaehler == 0) act.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

// Solange aktiv (echtes Konto beim Broker), zeigt die App-Uebersicht kein Bild der App, damit Kontostaende dort
// nicht lesbar sind. Screenshots bleiben moeglich. Ab Android 13.
@Composable
fun VorschauSchutz(aktiv: Boolean) {
    val act = LocalContext.current.activity() ?: return
    DisposableEffect(aktiv) {
        if (Build.VERSION.SDK_INT >= 33) act.setRecentsScreenshotEnabled(!aktiv)
        onDispose { if (Build.VERSION.SDK_INT >= 33) act.setRecentsScreenshotEnabled(true) }
    }
}
