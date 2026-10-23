package de.troetenrepublik.app.data

import de.troetenrepublik.app.plattform.Krypto
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// App-Sperre. Die Depotdaten sind mit einem zufaelligen Datenschluessel (256 Bit) verschluesselt. Dieser liegt
// hier mit AES-256-GCM verpackt unter einem Schluessel aus PIN oder Passwort (PBKDF2-HMAC-SHA256, RUNDEN Runden).
// PIN oder Passwort werden nirgends gespeichert. Ohne sperre.json ist die App nicht gesperrt.
@OptIn(ExperimentalEncodingApi::class)
class Sperre(dir: File) {

    @Serializable
    private data class Inhalt(
        val v: Int = 1, val art: String, val salz: String, val runden: Int, val schluessel: String,
        // Fehlversuche seit dem letzten Erfolg und Ende der Wartezeit (ms).
        val fehlversuche: Int = 0, val wartenBis: Long = 0L,
    )

    companion object {
        // Kostet beim Entsperren einen Bruchteil einer Sekunde und macht Raten teuer.
        const val RUNDEN = 310_000
        const val PIN = "pin"
        const val PASSWORT = "passwort"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val pfad = File(dir, "sperre.json")
    private val datei = SichereDatei(pfad)

    @Volatile private var inhalt: Inhalt? = lesen()

    private fun lesen(): Inhalt? =
        if (!pfad.exists()) null else runCatching { json.decodeFromString<Inhalt>(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull()

    val aktiv: Boolean get() = inhalt != null

    // PIN oder PASSWORT; null ohne Sperre.
    val art: String? get() = inhalt?.art

    // null bei falschem Geheimnis. Rechnet merklich, nicht im UI-Thread aufrufen.
    fun oeffnen(geheimnis: String): ByteArray? {
        val i = inhalt ?: return null
        val kek = Krypto.ableiten(geheimnis, Base64.decode(i.salz), i.runden)
        return Krypto.entschluesseln(kek, Base64.decode(i.schluessel))?.takeIf { it.size == 32 }
    }

    // Einrichten oder aendern: verpackt den Datenschluessel neu.
    fun setzen(geheimnis: String, art: String, datenschluessel: ByteArray) {
        val salz = Krypto.zufall(16)
        val kek = Krypto.ableiten(geheimnis, salz, RUNDEN)
        val neu = Inhalt(1, art, Base64.encode(salz), RUNDEN, Base64.encode(Krypto.verschluesseln(kek, datenschluessel)))
        datei.schreiben(json.encodeToString(Inhalt.serializer(), neu).toByteArray(Charsets.UTF_8))
        inhalt = neu
    }

    val fehlversuche: Int get() = inhalt?.fehlversuche ?: 0
    val wartenBis: Long get() = inhalt?.wartenBis ?: 0L

    // Fehlversuche und Wartezeit festhalten, damit sie einen Neustart ueberstehen.
    fun fehlversucheMerken(anzahl: Int, wartenBis: Long) {
        val i = inhalt ?: return
        if (i.fehlversuche == anzahl && i.wartenBis == wartenBis) return
        val neu = i.copy(fehlversuche = anzahl, wartenBis = wartenBis)
        runCatching { datei.schreiben(json.encodeToString(Inhalt.serializer(), neu).toByteArray(Charsets.UTF_8)) }
        inhalt = neu
    }

    fun entfernen() {
        runCatching { pfad.delete() }
        inhalt = null
    }
}
