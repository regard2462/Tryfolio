package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.data.TresorLesen
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Geheimnisse wie gespeicherte Schluessel: AES-GCM mit einem zufaelligen Schluessel. Der Eintragsname ist als AAD an das
// Chiffrat gebunden. Unter Windows liegt der Schluessel nur mit DPAPI (Benutzerbereich) geschuetzt neben den Daten, ein
// Programm unter demselben Benutzer kann ihn trotzdem entschluesseln. Unter Linux und macOS ist er eine Datei, die nur der
// eigene Benutzer lesen darf (ein Systemschluesselbund ist ohne zusaetzliche Abhaengigkeit nicht erreichbar). Das schuetzt
// vor dem Mitlesen der Einstellungen und vor Sicherungskopien ohne Schluesseldatei.
object Tresor {
    val verfuegbar: Boolean = true

    val schutz: String get() = if (schluesselSchutz != null) "dpapi" else "datei"

    private val ordner: File get() = File(Plattform.daten, "tresor").apply { mkdirs(); nurIch(this) }
    private val random = SecureRandom()

    // Schuetzt die 32 Byte des Schluessels fuer die Ablage auf der Platte (Windows: DPAPI).
    internal interface Schluesselschutz {
        fun schuetzen(klar: ByteArray): ByteArray
        fun oeffnen(geschuetzt: ByteArray): ByteArray
    }

    internal var schluesselSchutz: Schluesselschutz? =
        if (System.getProperty("os.name").orEmpty().startsWith("Windows")) DpapiPowerShell else null

    // Der Schluessel wird je Prozess und Ordner einmal geladen (ein Aufruf von PowerShell kostet etwa eine Sekunde).
    private var schluesselCache: Pair<String, SecretKeySpec>? = null

    // Fuer Tests: Zwischenspeicher leeren und den Schutz ersetzen.
    @Synchronized
    internal fun zuruecksetzen(schutz: Schluesselschutz?) {
        schluesselSchutz = schutz
        schluesselCache = null
    }

    private fun nurIch(f: File) {
        runCatching {
            val rechte = if (f.isDirectory) setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
            else setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            Files.setPosixFilePermissions(f.toPath(), rechte)
        }.onFailure {
            // Windows: nur der Besitzer
            f.setReadable(false, false); f.setReadable(true, true)
            f.setWritable(false, false); f.setWritable(true, true)
        }
    }

    // Ein neuer Schluessel entsteht nur, wenn es noch keine verschluesselten Eintraege gibt; sonst waere alles Vorhandene
    // unlesbar. Bei fehlender oder beschaedigter Schluesseldatei mit vorhandenen Eintraegen gibt es einen Fehler.
    // Eine Klartext-Schluesseldatei aus aelteren Fassungen wird unter Windows beim ersten Zugriff geschuetzt abgelegt und
    // erst danach geloescht; gelingt der Schutz nicht, bleibt sie unveraendert.
    @Synchronized
    private fun schluessel(): SecretKeySpec {
        val dir = ordner
        schluesselCache?.let { if (it.first == dir.absolutePath) return it.second }
        val klarDatei = File(dir, "schluessel")
        val geschuetztDatei = File(dir, "schluessel.dpapi")
        val schutz = schluesselSchutz
        var bytes: ByteArray? = null
        if (schutz != null && geschuetztDatei.exists()) {
            bytes = schutz.oeffnen(SichereDatei(geschuetztDatei).lesen())
            check(bytes.size == 32) { "key file damaged" }
        } else if (klarDatei.exists() && klarDatei.length() == 32L) {
            bytes = klarDatei.readBytes()
            if (schutz != null) {
                val klar: ByteArray = bytes
                runCatching {
                    SichereDatei(geschuetztDatei).schreiben(schutz.schuetzen(klar))
                    nurIch(geschuetztDatei)
                    check(schutz.oeffnen(SichereDatei(geschuetztDatei).lesen()).contentEquals(klar)) { "protect check failed" }
                    klarDatei.delete()
                }.onFailure { geschuetztDatei.delete() }
            }
        }
        if (bytes == null) {
            check(dir.listFiles { x -> x.name.endsWith(".bin") }.isNullOrEmpty()) { "key file missing or damaged, encrypted entries exist" }
            val neu = ByteArray(32).also { random.nextBytes(it) }
            bytes = neu
            val geschuetzt = schutz?.let { runCatching { it.schuetzen(neu) }.getOrNull() }
            if (geschuetzt != null) {
                SichereDatei(geschuetztDatei).schreiben(geschuetzt)
                nurIch(geschuetztDatei)
            } else {
                SichereDatei(klarDatei).schreiben(neu)
                nurIch(klarDatei)
            }
        }
        return SecretKeySpec(bytes, "AES").also { schluesselCache = dir.absolutePath to it }
    }

    private fun datei(name: String) = File(ordner, name.filter { it.isLetterOrDigit() || it == '_' || it == '-' } + ".bin")

    // Aufbau einer Datei: 12 Byte Startwert, Chiffrat mit Pruefsumme.
    internal fun verschluesseln(schluessel: SecretKeySpec, name: String, wert: String): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, schluessel, GCMParameterSpec(128, iv))
        c.updateAAD(name.toByteArray(Charsets.UTF_8))
        return iv + c.doFinal(wert.toByteArray(Charsets.UTF_8))
    }

    // Zweiter Teil des Ergebnisses: true bei einem alten Eintrag ohne AAD.
    internal fun entschluesseln(schluessel: SecretKeySpec, name: String, b: ByteArray): Pair<String, Boolean> {
        fun versuch(aad: ByteArray?): String {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, schluessel, GCMParameterSpec(128, b.copyOfRange(0, 12)))
            if (aad != null) c.updateAAD(aad)
            return c.doFinal(b.copyOfRange(12, b.size)).toString(Charsets.UTF_8)
        }
        return try {
            versuch(name.toByteArray(Charsets.UTF_8)) to false
        } catch (e: AEADBadTagException) {
            versuch(null) to true
        }
    }

    @Synchronized
    fun lesen(name: String): TresorLesen = runCatching {
        val f = datei(name)
        if (!f.exists()) return TresorLesen.Leer
        val (wert, ohneAad) = entschluesseln(schluessel(), name, SichereDatei(f).lesen())
        // Alter Eintrag ohne AAD: sofort mit AAD neu schreiben; scheitert das, bleibt die alte Datei lesbar
        if (ohneAad) runCatching {
            SichereDatei(f).schreiben(verschluesseln(schluessel(), name, wert))
            nurIch(f)
        }
        TresorLesen.Wert(wert)
    }.getOrDefault(TresorLesen.Fehler)

    // null loescht den Eintrag. Wirft, wenn es nicht gelingt.
    @Synchronized
    fun schreiben(name: String, wert: String?) {
        val f = datei(name)
        if (wert == null) {
            check(!f.exists() || f.delete()) { "delete failed" }
            return
        }
        SichereDatei(f).schreiben(verschluesseln(schluessel(), name, wert))
        nurIch(f)
    }

    // DPAPI ueber Windows PowerShell (CurrentUser). Die Daten laufen ueber Standardeingabe und -ausgabe als Base64, nie
    // ueber die Befehlszeile.
    private object DpapiPowerShell : Schluesselschutz {
        override fun schuetzen(klar: ByteArray) = aufruf("Protect", klar)
        override fun oeffnen(geschuetzt: ByteArray) = aufruf("Unprotect", geschuetzt)

        private fun aufruf(funktion: String, eingabe: ByteArray): ByteArray {
            val skript = "\$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Security; " +
                "\$b=[Convert]::FromBase64String([Console]::In.ReadLine()); " +
                "\$r=[Security.Cryptography.ProtectedData]::$funktion(\$b,\$null,'CurrentUser'); " +
                "[Console]::Out.WriteLine([Convert]::ToBase64String(\$r))"
            val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", skript)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            p.outputStream.use { it.write((Base64.getEncoder().encodeToString(eingabe) + "\n").toByteArray()) }
            val aus = p.inputStream.readBytes()
            check(p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0) { "dpapi failed" }
            return Base64.getDecoder().decode(aus.toString(Charsets.US_ASCII).trim())
        }
    }
}
