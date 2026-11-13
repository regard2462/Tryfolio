package de.troetenrepublik.app.plattform

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Aufbau einer Tresordatei: Laenge des Startwerts (1 Byte), Startwert, Chiffrat mit Pruefsumme. Der Eintragsname ist als
// zusaetzlich authentifizierte Daten (AAD) an das Chiffrat gebunden, eine Datei laesst sich damit nicht unter einem anderen
// Namen einspielen. Aeltere Dateien ohne AAD bleiben lesbar und werden beim Lesen neu geschrieben.
internal object TresorFormat {
    class Gelesen(val wert: String, val ohneAad: Boolean)

    fun verschluesseln(schluessel: SecretKey, name: String, wert: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, schluessel)
        c.updateAAD(name.toByteArray(Charsets.UTF_8))
        val iv = c.iv
        return byteArrayOf(iv.size.toByte()) + iv + c.doFinal(wert.toByteArray(Charsets.UTF_8))
    }

    fun entschluesseln(schluessel: SecretKey, name: String, b: ByteArray): Gelesen {
        val n = b[0].toInt()
        val spec = GCMParameterSpec(128, b.copyOfRange(1, 1 + n))
        val chiffrat = b.copyOfRange(1 + n, b.size)
        fun versuch(aad: ByteArray?): String {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, schluessel, spec)
            if (aad != null) c.updateAAD(aad)
            return c.doFinal(chiffrat).toString(Charsets.UTF_8)
        }
        return try {
            Gelesen(versuch(name.toByteArray(Charsets.UTF_8)), false)
        } catch (e: AEADBadTagException) {
            Gelesen(versuch(null), true)
        }
    }
}
