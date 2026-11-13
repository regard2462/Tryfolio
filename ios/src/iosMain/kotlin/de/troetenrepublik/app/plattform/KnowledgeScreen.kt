package de.troetenrepublik.app.plattform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ios.Dateien
import de.troetenrepublik.app.ios.alsNSData
import de.troetenrepublik.app.ui.LocalSignal
import de.troetenrepublik.app.ui.ScreenHeader
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKURLSchemeHandlerProtocol
import platform.WebKit.WKURLSchemeTaskProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

// Finanzwissen auf iOS: Die Lern-Bibliothek (assets/finanzwissen) laeuft in einer WKWebView. Die Dateien kommen ueber
// ein eigenes Schema (tryfolio-assets) aus dem App-Paket, wie auf Android ueber den WebViewAssetLoader; so laden auch
// die Datenabrufe der Seite (fetch). Quellenlinks oeffnen in Safari. Schliessen ueber den Pfeil oben oder Troete.close().
@Composable
fun KnowledgeScreen(onBack: () -> Unit) {
    val dark = LocalSignal.current.dark
    val sepia = LocalSignal.current.sepia
    val back by rememberUpdatedState(onBack)
    val theme = if (dark) "dunkel" else if (sepia) "sepia" else "hell"
    val ansicht = remember { Finanzwissen.ansicht(theme) { back() } }

    LaunchedEffect(theme) {
        ansicht.web.backgroundColor = if (dark) UIColor.blackColor else if (sepia) UIColor(red = 0.957, green = 0.925, blue = 0.847, alpha = 1.0) else UIColor.whiteColor
        ansicht.web.evaluateJavaScript("window.setzeTheme && window.setzeTheme('$theme')", completionHandler = null)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(S.knowledge, null, onBack = {
            // Zuerst eine Ebene in der Seite zurueck, auf der Startseite schliessen
            ansicht.web.evaluateJavaScript("(window.appBack&&window.appBack())?'1':'0'") { v, _ ->
                if (v?.toString() != "1") back()
            }
        })
        UIKitView(factory = { ansicht.web }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
    ZurueckTaste { back() }
}

internal object Finanzwissen {
    const val SCHEMA = "tryfolio-assets"

    class Ansicht(val web: WKWebView, val halter: List<NSObject>)

    private val typen = mapOf(
        "html" to "text/html", "js" to "text/javascript", "css" to "text/css", "json" to "application/json", "svg" to "image/svg+xml",
        "png" to "image/png", "jpg" to "image/jpeg", "woff2" to "font/woff2", "woff" to "font/woff", "ico" to "image/x-icon", "txt" to "text/plain",
    )

    // Liefert Dateien aus dem Ordner assets im App-Paket.
    private class Lieferant : NSObject(), WKURLSchemeHandlerProtocol {
        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, startURLSchemeTask: WKURLSchemeTaskProtocol) {
            val url = startURLSchemeTask.request.URL
            val pfad = url?.path.orEmpty().removePrefix("/")
            val wurzel = NSBundle.mainBundle.resourcePath
            val bytes = if (url == null || wurzel == null || pfad.contains("..")) null
                // Steuerwerte aus dem Datenbuendel (geprueft vom Geraet, sonst eingebaut)
                else if (pfad == de.troetenrepublik.app.engine.Steuerwerte.WEB_PFAD) de.troetenrepublik.app.engine.Steuerwerte.fuerWebansicht()
                else if (pfad == de.troetenrepublik.app.engine.Lernbeispiele.WEB_PFAD) de.troetenrepublik.app.engine.Lernbeispiele.fuerWebansicht()
                else Dateien.lesen("$wurzel/assets/$pfad")
            val typ = typen[pfad.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"
            val antwort = NSHTTPURLResponse(
                uRL = url ?: NSURL.URLWithString("$SCHEMA://app/")!!,
                statusCode = if (bytes == null) 404 else 200,
                HTTPVersion = "HTTP/1.1",
                headerFields = mapOf<Any?, Any?>("Content-Type" to typ, "Cache-Control" to "no-cache"),
            )
            startURLSchemeTask.didReceiveResponse(antwort!!)
            if (bytes != null) startURLSchemeTask.didReceiveData(bytes.alsNSData())
            startURLSchemeTask.didFinish()
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, stopURLSchemeTask: WKURLSchemeTaskProtocol) {}
    }

    // Nimmt Troete.close() und Troete.zustand(...) der Seite entgegen.
    private class Bruecke(private val schliessen: () -> Unit) : NSObject(), WKScriptMessageHandlerProtocol {
        override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
            if (didReceiveScriptMessage.body?.toString() == "close") schliessen()
        }
    }

    // Seiten der Bibliothek bleiben in der Ansicht, Links nach draussen gehen an Safari.
    private class Wegweiser : NSObject(), WKNavigationDelegateProtocol {
        override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (WKNavigationActionPolicy) -> Unit) {
            val u = decidePolicyForNavigationAction.request.URL
            val schema = u?.scheme?.lowercase()
            if (schema == SCHEMA || schema == "about") {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                return
            }
            if (schema == "https" || schema == "http") u?.absoluteString?.let { Browser.oeffnen(it) }
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        }
    }

    fun ansicht(theme: String, schliessen: () -> Unit): Ansicht {
        val lieferant = Lieferant()
        val bruecke = Bruecke(schliessen)
        val wegweiser = Wegweiser()
        val konfiguration = WKWebViewConfiguration().apply {
            setURLSchemeHandler(lieferant, forURLScheme = SCHEMA)
            userContentController.addScriptMessageHandler(bruecke, name = "troete")
            userContentController.addUserScript(
                WKUserScript(
                    source = "window.Troete={close:function(){window.webkit.messageHandlers.troete.postMessage('close')},zustand:function(){}};",
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                ),
            )
        }
        val web = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = konfiguration)
        web.navigationDelegate = wegweiser
        web.opaque = false
        val region = de.troetenrepublik.app.engine.Regionen.aktuell.lowercase()
        NSURL.URLWithString("$SCHEMA://app/finanzwissen/index.html?theme=$theme&lang=${I18n.lang.code}&region=$region")?.let {
            web.loadRequest(NSURLRequest.requestWithURL(it))
        }
        return Ansicht(web, listOf(lieferant, bruecke, wegweiser))
    }
}
