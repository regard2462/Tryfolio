package de.troetenrepublik.app.plattform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import de.troetenrepublik.app.data.TresorLesen
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

// Geheimnisse wie gespeicherte Schluessel: AES-GCM mit einem Schluessel im Android-Keystore, der das Geraet nicht
// verlaesst. Die Dateien liegen im privaten Ordner der App, der nicht gesichert wird (allowBackup=false).
// Gegenstueck fuer Windows und Linux: Tresor.kt im Modul desktop.
//
// Der Schluessel ist bewusst nicht an die Entsperrung des Geraets gebunden (kein setUnlockedDeviceRequired): Kursabrufe
// mit API-Schluesseln laufen ueber WorkManager auch bei gesperrtem Bildschirm. Ein schon vorhandener Schluessel behaelt
// seine Eigenschaften; scheitert dort das Lesen, meldet lesen() einen Fehler und nichts wird veraendert.
object Tresor {
    val verfuegbar: Boolean = true

    // Art des Schutzes, fuer den Hinweis in der App.
    const val schutz: String = "keystore"

    private const val ALIAS = "troete_tresor"
    private val ordner: File get() = File(Plattform.kontext.filesDir, "tresor").apply { mkdirs() }

    // Der Schluessel wird je Prozess einmal geladen.
    @Volatile
    private var schluesselCache: SecretKey? = null

    // Erzeugt nur dann einen neuen Schluessel, wenn es noch keine verschluesselten Dateien gibt. Fehlt der Schluessel
    // trotz vorhandener Dateien (etwa nach einer Systemwiederherstellung), wird ein Fehler gemeldet statt ihn zu ersetzen.
    @Synchronized
    private fun schluessel(): SecretKey {
        schluesselCache?.let { return it }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val vorhanden = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (vorhanden != null) return vorhanden.also { schluesselCache = it }
        check(!ks.containsAlias(ALIAS)) { "keystore entry unusable" }
        check(ordner.listFiles { f -> f.name.endsWith(".bin") }.isNullOrEmpty()) { "keystore key missing, encrypted entries exist" }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return g.generateKey().also { schluesselCache = it }
    }

    private fun datei(name: String) = File(ordner, name.filter { it.isLetterOrDigit() || it == '_' || it == '-' } + ".bin")

    @Synchronized
    fun lesen(name: String): TresorLesen = runCatching {
        val f = datei(name)
        if (!f.exists()) return TresorLesen.Leer
        val b = SichereDatei(f).lesen()
        val g = TresorFormat.entschluesseln(schluessel(), name, b)
        // Alter Eintrag ohne AAD: sofort mit AAD neu schreiben; scheitert das, bleibt die alte Datei lesbar
        if (g.ohneAad) runCatching { SichereDatei(f).schreiben(TresorFormat.verschluesseln(schluessel(), name, g.wert)) }
        TresorLesen.Wert(g.wert)
    }.getOrDefault(TresorLesen.Fehler)

    // null loescht den Eintrag. Wirft, wenn es nicht gelingt.
    @Synchronized
    fun schreiben(name: String, wert: String?) {
        val f = datei(name)
        if (wert == null) {
            check(!f.exists() || f.delete()) { "delete failed" }
            return
        }
        SichereDatei(f).schreiben(TresorFormat.verschluesseln(schluessel(), name, wert))
    }
}
