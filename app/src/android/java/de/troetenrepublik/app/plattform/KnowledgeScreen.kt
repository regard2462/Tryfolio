package de.troetenrepublik.app.plattform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.ui.LocalSignal
import de.troetenrepublik.app.ui.SwipeDownDismiss
import de.troetenrepublik.app.ui.rememberSwipeDownDismiss
import kotlin.math.abs

// Finanzwissen als Offline-Web-App aus assets/finanzwissen, Sprache und Erscheinungsbild gibt die App vor.
// Ein Wisch nach unten auf der Themenuebersicht schliesst den Bereich, solange sie oben steht.
// Den Rueckweg in tiefere Ebenen erledigt die Seite selbst, ihren Zustand meldet sie ueber Troete.zustand().
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KnowledgeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dark = LocalSignal.current.dark
    val back by rememberUpdatedState(onBack)
    val theme = if (dark) "dunkel" else "hell"
    val down = rememberSwipeDownDismiss(onBack)

    val rahmen = remember {
        val zustand = WebZustand()
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        val web = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            settings.textZoom = 100
            isVerticalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            setBackgroundColor(if (dark) Color.BLACK else Color.WHITE)
            addJavascriptInterface(KnowledgeBridge(zustand) { post { back() } }, "Troete")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url
                    if (u.host == "appassets.androidplatform.net") return false
                    // Quellenlinks nur als http oder https im Browser oeffnen, der Bereich selbst braucht kein Netz.
                    val schema = u.scheme?.lowercase()
                    if (schema == "https" || schema == "http") runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE))
                    }
                    return true
                }
            }
            // Fuer die USA zeigt das Finanzwissen angepasste Lektionen.
            loadUrl("https://appassets.androidplatform.net/assets/finanzwissen/index.html?theme=$theme&lang=${I18n.lang.code}&region=${de.troetenrepublik.app.engine.Regionen.aktuell.lowercase()}")
        }
        GestenRahmen(context, web, zustand)
    }
    SideEffect { rahmen.down = down }

    LaunchedEffect(theme) {
        rahmen.web.setBackgroundColor(if (dark) Color.BLACK else Color.WHITE)
        rahmen.web.evaluateJavascript("window.setzeTheme && window.setzeTheme('$theme')", null)
    }

    // Zurueck geht zuerst eine Ebene in der Seite zurueck. Waehrend einer Schliessgeste ist das aus,
    // damit die Geste die Zurueck-Animation steuert.
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val webBack = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                rahmen.web.evaluateJavascript("(window.appBack&&window.appBack())?'1':'0'") { v ->
                    if (v == null || !v.contains("1")) back()
                }
            }
        }
    }
    DisposableEffect(dispatcher) {
        dispatcher?.addCallback(webBack)
        rahmen.webBack = webBack
        onDispose { webBack.remove() }
    }

    AndroidView(
        factory = { rahmen },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
    )

    DisposableEffect(Unit) { onDispose { rahmen.web.destroy() } }
}

private class WebZustand {
    @Volatile var tiefe = 1
    @Volatile var oben = true
    @Volatile var blatt = false
}

// Von der Web-Seite aufgerufen: Schliessen und Zustand fuer die Gesten.
private class KnowledgeBridge(private val zustand: WebZustand, private val onClose: () -> Unit) {
    @JavascriptInterface
    fun close() { onClose() }

    @JavascriptInterface
    fun zustand(tiefe: Int, oben: Boolean, blatt: Boolean) {
        zustand.tiefe = tiefe
        zustand.oben = oben
        zustand.blatt = blatt
    }
}

// Erkennt die Schliessgeste, bevor die Seite sie bekommt. Alles andere geht unveraendert an die Seite.
// Bildschirmkoordinaten, weil sich die Ansicht beim Wischen mitbewegt.
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
private class GestenRahmen(context: Context, val web: WebView, private val zustand: WebZustand) : FrameLayout(context) {
    var down: SwipeDownDismiss? = null
    var webBack: OnBackPressedCallback? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var x0 = 0f
    private var y0 = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var tracker: VelocityTracker? = null

    // 0 = offen, 1 = schliessen, -1 = gehoert der Web-Seite.
    private var modus = 0

    init {
        addView(web, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // Entschieden wird schon beim Verteilen: die Web-Ansicht verbietet ihren Eltern das Abfangen,
    // sobald die Seite eigene Beruehrungs-Handler hat.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x0 = ev.rawX; y0 = ev.rawY; lastX = x0; lastY = y0
                modus = 0
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                spur(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                if (modus > 0) {
                    spur(ev)
                    down?.drag(ev.rawY - lastY)
                    lastX = ev.rawX; lastY = ev.rawY
                    return true
                }
                if (modus == 0) {
                    spur(ev)
                    val dx = ev.rawX - x0
                    val dy = ev.rawY - y0
                    if (abs(dx) >= slop || abs(dy) >= slop) {
                        // Nur auf der Startseite und nur ganz oben
                        modus = when {
                            zustand.blatt || ev.pointerCount > 1 -> -1
                            zustand.tiefe <= 1 && dy > 0 && dy > abs(dx) * 1.2f && zustand.oben && !web.canScrollVertically(-1) -> 1
                            else -> -1
                        }
                        if (modus > 0) {
                            webBack?.isEnabled = false
                            // Die Seite bekommt ein Abbrechen, ab jetzt gehoert die Geste dem Rahmen.
                            val abbruch = MotionEvent.obtain(ev)
                            abbruch.action = MotionEvent.ACTION_CANCEL
                            super.dispatchTouchEvent(abbruch)
                            abbruch.recycle()
                            lastX = ev.rawX; lastY = ev.rawY
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (modus > 0) {
                    spur(ev)
                    val t = tracker
                    val oben = ev.actionMasked == MotionEvent.ACTION_UP
                    t?.computeCurrentVelocity(1000)
                    // Der eigene Zurueck-Weg ruht, bis die Seite ausgelaufen ist, sonst bekaeme er das Zurueck.
                    val d = down
                    if (d != null) d.release(if (oben) t?.yVelocity ?: 0f else 0f) { webBack?.isEnabled = true }
                    else webBack?.isEnabled = true
                    modus = 0
                    return true
                }
                modus = 0
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // Bildschirmkoordinaten, damit die mitgleitende Ansicht die Geschwindigkeit nicht verfaelscht.
    private fun spur(ev: MotionEvent) {
        val kopie = MotionEvent.obtain(ev)
        kopie.setLocation(ev.rawX, ev.rawY)
        tracker?.addMovement(kopie)
        kopie.recycle()
    }

    override fun onDetachedFromWindow() {
        tracker?.recycle()
        tracker = null
        super.onDetachedFromWindow()
    }
}
