package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Abrufgedaechtnis
import de.troetenrepublik.app.engine.Uhr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

// Ablage der Sperren nach abgelehnten Abrufen auf dem Geraet (Regeln in engine/Abrufgedaechtnis.kt). Gilt fuer die ganze
// App: Quellen mit Bundel (Wikipedia, SEC, FRED, Nasdaq ...), Hosts ohne Quelle (Nachrichtenfeeds) und einzelne Werte.
// Geschrieben wird gebuendelt und im Hintergrund, nie im Aufruf; ohne Ordner (Tests, vor dem Start) bleibt es im Speicher.
object AbrufAblage : Abrufgedaechtnis.Dienst {
    val speicher = Abrufgedaechtnis.Speicher()
    @Volatile private var datei: StandDatei? = null
    private val gestartet = AtomicBoolean(false)
    private val geplant = AtomicBoolean(false)
    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Einmal je Prozess: die abgelegten Sperren des letzten Laufs lesen
    fun starten(ordner: File) {
        if (!gestartet.compareAndSet(false, true)) return
        val d = SichereStandDatei(File(ordner, "abrufsperren.txt"))
        datei = d
        runCatching { speicher.laden(d.lesen(), Uhr.jetzt()) }
    }

    override fun gesperrtBis(schluessel: String): Long = speicher.bis(schluessel, Uhr.jetzt())

    fun gesperrt(schluessel: String): Boolean = speicher.gesperrt(schluessel, Uhr.jetzt())

    override fun ablehnung(schluessel: String, code: Int, retryAfter: String?, mindestMs: Long): Long {
        val s = speicher.ablehnung(schluessel, code, retryAfter, Uhr.jetzt(), mindestMs)
        ablegen()
        return s.bis
    }

    override fun erfolg(schluessel: String) {
        if (speicher.erfolg(schluessel)) ablegen()
    }

    // Alle Sperren aufheben (Zaehler der Datenquellen zuruecksetzen)
    fun zuruecksetzen() {
        speicher.zuruecksetzen()
        ablegen()
    }

    // Schreiben nach kurzer Pause, mehrere Aenderungen in einem Schritt
    private fun ablegen() {
        if (datei == null || !geplant.compareAndSet(false, true)) return
        bereich.launch {
            delay(1_500L)
            geplant.set(false)
            val d = datei ?: return@launch
            runCatching { d.schreiben(speicher.text(Uhr.jetzt())) }
        }
    }
}
