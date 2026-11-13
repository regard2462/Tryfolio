package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.data.TresorLesen
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

// Auf iOS ohne Verschluesselung der App-Sperre (AES-256-GCM, PBKDF2); die App zeigt den Hinweis, dass die Sperre
// nur in den Apps fuer Android und Desktop geht. Nicht geprueftes AES-GCM soll keine Depots unlesbar machen.
object Krypto {
    val verfuegbar: Boolean = false

    // Zufall aus dem Zufallsgenerator des Systems.
    fun zufall(n: Int): ByteArray = ByteArray(n).also { b ->
        if (n > 0) b.usePinned { check(SecRandomCopyBytes(kSecRandomDefault, n.toULong(), it.addressOf(0)) == 0) { "no random" } }
    }

    fun ableiten(geheimnis: String, salz: ByteArray, runden: Int): ByteArray = throw UnsupportedOperationException("app lock is not available on iOS yet")

    fun verschluesseln(schluessel: ByteArray, klar: ByteArray): ByteArray = throw UnsupportedOperationException("app lock is not available on iOS yet")

    fun entschluesseln(schluessel: ByteArray, daten: ByteArray): ByteArray? = null
}

// [Sicherheit 0.71] Geheimnisse liegen im Schluesselbund (Keychain) von iOS: verschluesselt vom System, nur bei
// entsperrtem Geraet lesbar, ohne Uebergabe an iCloud oder andere Geraete (ThisDeviceOnly) und nicht in Sicherungen
// des Ordners. Gleiche Aufrufe wie der Tresor auf Android und Desktop. Jeder Zugriff ist gekapselt: Ein Laufzeitfehler
// oder ein Statuscode ausser "nicht gefunden" ergibt TresorLesen.Fehler, und die Einstellungen behalten ihren Klartext.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object Tresor {
    val verfuegbar: Boolean = true

    const val schutz: String = "keychain"

    private const val DIENST = "de.troetenrepublik.app.tresor"

    // Woerterbuch fuer eine Schluesselbund-Abfrage; alle selbst erzeugten Objekte werden mit schliessen() freigegeben.
    private class Frage(name: String) {
        val dict: CFMutableDictionaryRef? = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)

        init {
            setze(kSecClass, kSecClassGenericPassword)
            text(kSecAttrService, DIENST)
            text(kSecAttrAccount, name)
        }

        // Konstanten aus Security.framework und CoreFoundation gehoeren dem System
        fun setze(schluessel: CFTypeRef?, wert: CFTypeRef?) = CFDictionaryAddValue(dict, schluessel, wert)

        // Das Woerterbuch haelt eine eigene Referenz; unsere wird sofort wieder abgegeben.
        private fun eigen(schluessel: CFTypeRef?, objekt: Any?) {
            val ref = CFBridgingRetain(objekt)
            try {
                CFDictionaryAddValue(dict, schluessel, ref)
            } finally {
                CFRelease(ref)
            }
        }

        fun text(schluessel: CFTypeRef?, wert: String) = eigen(schluessel, NSString.create(string = wert))

        fun daten(schluessel: CFTypeRef?, wert: NSData) = eigen(schluessel, wert)

        fun schliessen() = CFRelease(dict)
    }

    private inline fun <T> mitFrage(name: String, aktion: (Frage) -> T): T {
        val frage = Frage(name)
        try {
            return aktion(frage)
        } finally {
            frage.schliessen()
        }
    }

    @Synchronized
    fun lesen(name: String): TresorLesen = runCatching {
        mitFrage(name) { frage ->
            frage.setze(kSecReturnData, kCFBooleanTrue)
            frage.setze(kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val ergebnis = alloc<CFTypeRefVar>()
                ergebnis.value = null
                val status = SecItemCopyMatching(frage.dict, ergebnis.ptr)
                when (status) {
                    errSecSuccess -> {
                        // Copy-Regel: Das Ergebnis gehoert uns und wird hier an Kotlin uebergeben und freigegeben
                        val daten = CFBridgingRelease(ergebnis.value) as? NSData
                        val text = daten?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
                        if (text != null) TresorLesen.Wert(text) else TresorLesen.Fehler
                    }
                    errSecItemNotFound -> TresorLesen.Leer
                    else -> TresorLesen.Fehler
                }
            }
        }
    }.getOrDefault(TresorLesen.Fehler)

    // null loescht den Eintrag. Wirft, wenn es nicht gelingt.
    @Synchronized
    fun schreiben(name: String, wert: String?) {
        if (wert == null) {
            val status = mitFrage(name) { SecItemDelete(it.dict) }
            check(status == errSecSuccess || status == errSecItemNotFound) { "keychain delete failed ($status)" }
            return
        }
        val daten = checkNotNull(NSString.create(string = wert).dataUsingEncoding(NSUTF8StringEncoding)) { "utf8 failed" }
        val update = mitFrage(name) { frage ->
            val werte = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            val ref = CFBridgingRetain(daten)
            try {
                CFDictionaryAddValue(werte, kSecValueData, ref)
                SecItemUpdate(frage.dict, werte)
            } finally {
                CFRelease(ref)
                CFRelease(werte)
            }
        }
        if (update == errSecSuccess) return
        check(update == errSecItemNotFound) { "keychain update failed ($update)" }
        val status = mitFrage(name) { frage ->
            frage.daten(kSecValueData, daten)
            frage.setze(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
            SecItemAdd(frage.dict, null)
        }
        check(status == errSecSuccess) { "keychain add failed ($status)" }
    }
}
