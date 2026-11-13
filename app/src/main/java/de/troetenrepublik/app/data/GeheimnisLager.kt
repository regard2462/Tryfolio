package de.troetenrepublik.app.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Ergebnis eines Lesezugriffs auf den Tresor der Plattform: Wert vorhanden, kein Eintrag oder Fehler (gesperrtes
// Geraet, ungueltiger Schluessel, beschaedigte Datei). Ein Fehler ist nie dasselbe wie "leer".
sealed interface TresorLesen {
    data class Wert(val text: String) : TresorLesen
    data object Leer : TresorLesen
    data object Fehler : TresorLesen
}

// Schnittstelle zum Tresor der Plattform, damit die Einstellungen auch mit einem Ersatz laufen (Einheiten-Tests).
interface TresorSpeicher {
    val verfuegbar: Boolean

    fun lesen(name: String): TresorLesen

    // null loescht den Eintrag. Wirft, wenn das Schreiben oder Loeschen misslingt.
    fun schreiben(name: String, wert: String?)
}

// Der Tresor der jeweiligen Plattform (Keystore, Datei, Schluesselbund).
object PlattformTresor : TresorSpeicher {
    override val verfuegbar: Boolean get() = de.troetenrepublik.app.plattform.Tresor.verfuegbar

    override fun lesen(name: String): TresorLesen = de.troetenrepublik.app.plattform.Tresor.lesen(name)

    override fun schreiben(name: String, wert: String?) = de.troetenrepublik.app.plattform.Tresor.schreiben(name, wert)
}

// Ein geheimes Feld der Einstellungen: Name im Tresor, Schluessel des alten Klartexteintrags in der Datei und der
// Zugriff auf das Feld in Settings.
internal class GeheimFeld(
    val name: String,
    val klartext: Preferences.Key<String>,
    val holen: (Settings) -> String,
    val setzen: (Settings, String) -> Settings,
)

// Haelt die geheimen Felder im Speicher und gleicht sie mit dem Tresor ab. Regeln:
// - Der Tresor wird je Feld einmal gelesen (bei einem Fehler beim naechsten Zugriff erneut).
// - Nach einem Lesefehler wird fuer das Feld nichts in den Tresor geschrieben und nichts geloescht.
// - Geloescht wird nur, wenn das Feld ausdruecklich geleert wurde und sicher gelesen war.
// - Alte Klartexteintraege werden erst entfernt, wenn der Wert aus dem Tresor zurueckgelesen identisch ist.
internal class GeheimnisLager(private val tresor: TresorSpeicher, private val felder: List<GeheimFeld>) {
    private val sperre = Mutex()

    private class Stand {
        // null: noch nicht gelesen
        var tresor: TresorLesen? = null
        // Wert, der in dieser Sitzung gilt, obwohl der Tresor ihn nicht aufnehmen konnte
        var lokal: String? = null
    }

    private val staende = felder.associate { it.name to Stand() }

    private fun stand(f: GeheimFeld) = staende.getValue(f.name)

    private fun lesbar(s: Stand) = s.tresor is TresorLesen.Wert || s.tresor is TresorLesen.Leer

    // Liest alle Felder, die noch nicht oder mit Fehler gelesen sind. Gibt zurueck, ob sich ein Stand geaendert hat.
    private fun laden(): Boolean {
        var geaendert = false
        for (f in felder) {
            val s = stand(f)
            val alt = s.tresor
            if (alt != null && alt != TresorLesen.Fehler) continue
            val neu = runCatching { tresor.lesen(f.name) }.getOrDefault(TresorLesen.Fehler)
            s.tresor = neu
            if (alt != null && neu != alt) geaendert = true
        }
        return geaendert
    }

    // Setzt die geheimen Felder in die aus der Datei gelesenen Einstellungen ein. Ohne Tresor bleibt der Klartext.
    suspend fun einsetzen(basis: Settings): Settings {
        if (!tresor.verfuegbar) return basis
        return sperre.withLock {
            laden()
            felder.fold(basis) { s, f ->
                val st = stand(f)
                val imTresor = (st.tresor as? TresorLesen.Wert)?.text?.takeIf { it.isNotBlank() }
                f.setzen(s, st.lokal ?: imTresor ?: f.holen(s))
            }
        }
    }

    // Versucht Felder mit Lesefehler erneut zu lesen; true, wenn sich dadurch etwas geaendert hat.
    suspend fun neuLesen(): Boolean {
        if (!tresor.verfuegbar) return false
        return sperre.withLock { laden() }
    }

    // Uebertraegt die Aenderungen von alt (so angezeigt) auf neu in Tresor und Datei.
    suspend fun anwenden(p: MutablePreferences, alt: Settings, neu: Settings) {
        if (!tresor.verfuegbar) {
            for (f in felder) p[f.klartext] = f.holen(neu).trim()
            return
        }
        sperre.withLock {
            laden()
            for (f in felder) anwenden(p, f, f.holen(alt).trim(), f.holen(neu).trim())
        }
    }

    private fun anwenden(p: MutablePreferences, f: GeheimFeld, alt: String, neu: String) {
        val s = stand(f)
        val klartext = p[f.klartext]
        if (neu != alt) {
            when {
                // Tresor nicht lesbar: nichts dort anfassen, der neue Wert bleibt in der Datei
                !lesbar(s) -> {
                    s.lokal = neu
                    if (neu.isBlank()) p.remove(f.klartext) else p[f.klartext] = neu
                }
                neu.isBlank() -> {
                    if (runCatching { tresor.schreiben(f.name, null) }.isSuccess) {
                        s.tresor = TresorLesen.Leer
                        s.lokal = null
                    } else s.lokal = ""
                    p.remove(f.klartext)
                }
                schreibenUndPruefen(f, s, neu) -> {
                    s.lokal = null
                    p.remove(f.klartext)
                }
                else -> {
                    s.lokal = neu
                    p[f.klartext] = neu
                }
            }
        } else if (klartext != null) {
            uebernehmen(p, f, s, klartext.trim())
        }
    }

    // Alter Klartext aus der Datei: in den Tresor, zurueckgelesen und verglichen, dann erst aus der Datei entfernt.
    private fun uebernehmen(p: MutablePreferences, f: GeheimFeld, s: Stand, klartext: String) {
        if (klartext.isBlank()) {
            p.remove(f.klartext)
            return
        }
        when (val t = s.tresor) {
            is TresorLesen.Wert -> if (t.text == klartext) {
                s.lokal = null
                p.remove(f.klartext)
            }
            // Ein anderer Wert im Tresor wird nie ueberschrieben
            TresorLesen.Leer -> if (schreibenUndPruefen(f, s, klartext)) {
                s.lokal = null
                p.remove(f.klartext)
            }
            else -> {}
        }
    }

    private fun schreibenUndPruefen(f: GeheimFeld, s: Stand, wert: String): Boolean {
        if (runCatching { tresor.schreiben(f.name, wert) }.isFailure) return false
        val zurueck = runCatching { tresor.lesen(f.name) }.getOrDefault(TresorLesen.Fehler)
        s.tresor = zurueck
        return zurueck == TresorLesen.Wert(wert)
    }
}
