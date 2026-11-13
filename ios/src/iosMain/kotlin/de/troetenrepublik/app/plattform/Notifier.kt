package de.troetenrepublik.app.plattform

import platform.Foundation.NSUUID
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

// Mitteilungen auf iOS ueber das Mitteilungszentrum, solange die App laeuft (Hintergrundabrufe gibt es in dieser
// Fassung nicht). Gleiche Bausteine wie auf Android (Kanaele, enabled), damit AppState unveraendert bleibt.
// Auch bei offener App erscheint die Meldung oben als Banner. Ein Tipp darauf oeffnet die App; Link und Ziel der
// Meldung gehen dabei noch nicht mit.
class Notifier(private val onKlick: () -> Unit = {}) {

    companion object {
        const val CHANNEL_TRADES = "trades"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_CASH = "cash"
        const val CHANNEL_UPDATES = "updates"
        const val CHANNEL_NEWS = "news"
        const val CHANNEL_RUECKBLICK = "rueckblick"
        const val CHANNEL_COPY = "copytrading"
        const val CHANNEL_WELT = "welt"
    }

    @kotlin.concurrent.Volatile var enabled: Boolean = true

    // Kanaele, deren Art in den Einstellungen ausgeschaltet ist (0.68).
    @kotlin.concurrent.Volatile var gesperrt: Set<String> = emptySet()

    // Oeffnet eine Seite der App; setzt AppState.
    @kotlin.concurrent.Volatile var beiZiel: (String) -> Unit = {}

    @kotlin.concurrent.Volatile private var erlaubt: Boolean = false

    private val zentrum get() = UNUserNotificationCenter.currentNotificationCenter()

    // Das Mitteilungszentrum haelt den Empfaenger nur schwach; hier bleibt er am Leben
    private val empfaenger = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
        ) {
            withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
        }

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit,
        ) {
            onKlick()
            withCompletionHandler()
        }
    }

    init {
        zentrum.delegate = empfaenger
        // Beim ersten Start fragt iOS einmal nach der Erlaubnis; danach gilt die Einstellung des Systems
        zentrum.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { ok, _ ->
            erlaubt = ok
        }
    }

    fun refreshChannelNames() {
        zentrum.getNotificationSettingsWithCompletionHandler { s ->
            erlaubt = s?.authorizationStatus == UNAuthorizationStatusAuthorized || s?.authorizationStatus == UNAuthorizationStatusProvisional
        }
    }

    fun canNotify(): Boolean = erlaubt

    fun notify(channel: String, title: String, text: String, link: String? = null, ziel: String? = null) {
        if (!enabled || channel in gesperrt) return
        val inhalt = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(text)
            setThreadIdentifier(channel)
        }
        val anfrage = UNNotificationRequest.requestWithIdentifier(NSUUID().UUIDString, inhalt, null)
        zentrum.addNotificationRequest(anfrage, withCompletionHandler = null)
    }

    fun schliessen() {}
}
