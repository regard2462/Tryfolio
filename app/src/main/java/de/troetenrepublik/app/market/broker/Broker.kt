package de.troetenrepublik.app.market.broker

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity

// Ein echtes Konto bei einem Broker, verbunden ueber dessen Schnittstelle mit dem Schluessel des Nutzers.
// Die Anfragen gehen direkt vom Geraet an den Broker. Betraege kommen in der Kontowaehrung BrokerStand.waehrung.
interface Broker : Kursquelle {
    val art: BrokerArt

    // Echtes Geld (false: Papier- oder Demokonto des Brokers).
    val live: Boolean

    // Kontostand, Positionen, Orders und Umsaetze; wirft BrokerFehler bei falschem Schluessel oder ohne Verbindung.
    suspend fun stand(): BrokerStand

    // Zurueck kommt die Order-Nummer des Brokers.
    suspend fun order(auftrag: Auftrag): String

    suspend fun stornieren(id: String)

    // Was sich an einer offenen Order fuer symbol aendern laesst; null: nichts, nur stornieren.
    fun aenderbar(symbol: String): Aenderbar? = null

    // Offene Order id aendern: Menge, Preis, Trailing-Abstand und Gueltigkeit aus auftrag, soweit aenderbar.
    suspend fun aendern(id: String, auftrag: Auftrag): Unit = throw BrokerFehler("ABGELEHNT")

    // Der Wert (App-Symbol) laesst sich bei diesem Broker handeln; Kurse kommen nur von ihm, wenn er ihn auch fuehrt.
    fun handelbar(symbol: String): Boolean = fuehrt(symbol)

    fun ordertypen(symbol: String): Set<OrderType>

    fun bruchstuecke(symbol: String): Boolean

    // Limit- und Stoppreise gehen in Euro an den Broker statt in der Handelswaehrung des Werts.
    val preisInEuro: Boolean get() = false

    // Zweiter Faktor fuer die Sitzung; null: nicht noetig.
    val freigabe: Freigabe? get() = null

    // Hinweise des Brokers zur zuletzt gesendeten Order.
    fun letzteHinweise(): List<String> = emptyList()

    // Beim Trennen: die Sitzung beim Broker beenden, soweit er das anbietet.
    suspend fun abmelden() {}
}

// Freigabe einer Sitzung mit TAN. Eine Challenge fordert die App nur auf Tippen der Person an, nie von selbst:
// fuenf Challenges ohne eingeloeste TAN oder drei falsche TANs sperren beim Broker das ganze Onlinebanking.
interface Freigabe {
    // Die Sitzung ist freigegeben und ihr Token noch gueltig.
    val aktiv: Boolean

    // Genau eine TAN-Challenge anfordern, vorher wenn noetig anmelden. verfahren: gewuenschtes TAN-Verfahren oder null.
    suspend fun anfordern(verfahren: String?): TanChallenge

    // Die Challenge c einloesen; tan bei photoTAN und mobileTAN, null bei photoTAN-Push (Freigabe in der App).
    suspend fun bestaetigen(c: TanChallenge, tan: String?)
}

// Eine TAN-Challenge: verfahren P_TAN_PUSH, P_TAN (photoTAN-Grafik in bild, Base64-PNG) oder M_TAN (text: Rufnummer).
data class TanChallenge(val id: String, val verfahren: String, val bild: String?, val text: String, val verfuegbar: List<String>)

data class Aenderbar(val menge: Boolean, val preis: Boolean, val gueltigkeit: Boolean)

// Fehler des Brokers mit seiner Meldung (oder einer der App: SCHLUESSEL, NETZ, ABGELEHNT).
class BrokerFehler(val code: String, val meldung: String? = null) : Exception(meldung ?: code)

data class BrokerStand(
    val waehrung: String,
    val cash: Double,
    // Kaufkraft laut Broker (bei Margin-Konten mehr als cash).
    val kaufkraft: Double,
    val positionen: List<BrokerPosition>,
    val orders: List<BrokerOrder>,
    val umsaetze: List<BrokerUmsatz>,
    // Konto-Nummer oder -Name zur Anzeige.
    val konto: String = "",
)

data class BrokerPosition(
    val symbol: String,
    val name: String,
    val menge: Double,
    // Durchschnittlicher Einstand je Stueck in waehrung.
    val einstand: Double,
    val waehrung: String,
    val art: AssetType = AssetType.STOCK,
)

data class BrokerOrder(
    val id: String,
    val symbol: String,
    val name: String,
    val seite: Side,
    val typ: OrderType,
    val menge: Double,
    val limit: Double?,
    val stop: Double?,
    val trailPct: Double?,
    val status: OrderStatus,
    val erstellt: Long,
    val ausgefuehrt: Long?,
    val kurs: Double?,
    val waehrung: String,
    val gueltigkeit: Validity,
    val ausgefuehrteMenge: Double = 0.0,
)

data class BrokerUmsatz(
    val id: String,
    val zeit: Long,
    val typ: TxType,
    val symbol: String?,
    val menge: Double,
    val kurs: Double,
    // Betrag mit Vorzeichen (Zufluss positiv) in waehrung.
    val betrag: Double,
    val waehrung: String,
    val gebuehr: Double = 0.0,
    val text: String = "",
)

data class Auftrag(
    val symbol: String,
    val seite: Side,
    val typ: OrderType,
    val menge: Double,
    // Limit (LIMIT) oder Ausloeser (STOP) in der Handelswaehrung.
    val preis: Double? = null,
    val trailPct: Double? = null,
    val gueltigkeit: Validity = Validity.DAY,
    // Eigene Kennung der Order, wo der Broker eine annimmt (eine doppelt gesendete Order lehnt er dann ab).
    val kennung: String = "tr-" + System.currentTimeMillis().toString(36) + "-" + kotlin.random.Random.nextLong().toULong().toString(36),
)
