package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.CompanyInfo

// Regeln fuer abgelehnte Abrufe (429, 403, 5xx, Fehlerseite statt Daten). Eine Ablehnung ist nie ein leeres Ergebnis:
// Vorhandenes bleibt sichtbar, die Quelle wird bis zu einem Zeitpunkt nicht erneut gefragt, danach wieder. Die Sperre je
// Schluessel (Quelle, Host, Adresse, Wert) steht als Text zur Ablage auf dem Geraet und ueberlebt den Neustart der App.
object Abrufgedaechtnis {
    const val MINUTE = 60_000L

    // Wartezeit je aufeinanderfolgender Ablehnung ohne Retry-After: 5 Minuten, 30 Minuten, 2 Stunden, 12 Stunden
    val STAFFEL = longArrayOf(5 * MINUTE, 30 * MINUTE, 120 * MINUTE, 720 * MINUTE)
    const val HOECHSTENS_MS = 720 * MINUTE

    // Eine abgelaufene Sperre merkt sich ihre Stufe noch einen Tag, danach beginnt die Staffel von vorn
    const val VERGESSEN_MS = 24 * 60 * MINUTE
    const val MAX_EINTRAEGE = 300

    // 403 steigt nur bis zur zweiten Stufe: bei Quellen mit Schluessel kann ein falscher Schluessel dahinterstehen
    private const val STUFEN_403 = 2

    data class Sperre(val bis: Long, val stufe: Int)

    // Zugriff fuer Leser, die ihre Pause ablegen wollen (das Geraet liefert die Ablage, Tests eine Attrappe)
    interface Dienst {
        // Zeitpunkt, bis zu dem nicht gefragt wird; 0 ohne Sperre
        fun gesperrtBis(schluessel: String): Long
        // Merkt eine Ablehnung und liefert das Ende der Sperre
        fun ablehnung(schluessel: String, code: Int, retryAfter: String? = null, mindestMs: Long = 0L): Long
        fun erfolg(schluessel: String)
    }

    // Lehnt der Server ab? 0 (keine Verbindung) zaehlt hier nicht: ohne Netz ist nicht die Quelle schuld.
    fun abgelehnt(code: Int): Boolean = code == 429 || code == 403 || code == 408 || code in 500..599

    // Retry-After in Sekunden; ein Datum oder Unlesbares gilt als nicht genannt. Zwischen einer Sekunde und zwoelf Stunden.
    fun retryAfterMs(kopf: String?): Long? {
        val s = kopf?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return (s * 1000L).coerceIn(1_000L, HOECHSTENS_MS)
    }

    fun staffelMs(stufe: Int): Long = STAFFEL[(stufe - 1).coerceIn(0, STAFFEL.size - 1)]

    // Eine Antwort mit Status 200, die eine Fehlerseite statt Daten ist (Sperrseite, Wartungsseite, Anmeldung)
    fun htmlStattDaten(text: String?): Boolean {
        if (text == null) return false
        val kopf = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').take(400).lowercase()
        return kopf.startsWith("<!doctype html") || kopf.startsWith("<html") || (kopf.startsWith("<") && "<html" in kopf)
    }

    // Beschreibung und Stammdaten: ein neuer Abruf ersetzt Vorhandenes nur durch Besseres. Ohne Fund (missing) bleibt ein
    // frueherer Treffer stehen; fehlende Felder eines neuen Treffers kommen aus dem frueheren.
    fun firmaBehalten(neu: CompanyInfo, alt: CompanyInfo?): CompanyInfo {
        if (alt == null || alt.missing) return neu
        if (neu.missing) return alt.copy(fetchedAt = neu.fetchedAt)
        return neu.copy(
            isin = neu.isin.ifBlank { alt.isin },
            logoUrl = neu.logoUrl.ifBlank { alt.logoUrl },
            website = neu.website.ifBlank { alt.website },
            descriptionDe = neu.descriptionDe.ifBlank { alt.descriptionDe },
            descriptionEn = neu.descriptionEn.ifBlank { alt.descriptionEn },
            articleDe = neu.articleDe.ifBlank { alt.articleDe },
            articleEn = neu.articleEn.ifBlank { alt.articleEn },
        )
    }

    // Sperren je Schluessel. Alle Zeitpunkte kommen vom Aufrufer (steuerbar in Tests).
    class Speicher(private val maxEintraege: Int = MAX_EINTRAEGE) {
        private val karte = LinkedHashMap<String, Sperre>()

        private fun schluessel(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

        @Synchronized
        fun sperre(schluessel: String): Sperre? = karte[schluessel(schluessel)]

        // Ende der Sperre, 0 wenn keine gilt
        @Synchronized
        fun bis(schluessel: String, jetzt: Long): Long = karte[schluessel(schluessel)]?.bis?.takeIf { it > jetzt } ?: 0L

        @Synchronized
        fun gesperrt(schluessel: String, jetzt: Long): Boolean = bis(schluessel, jetzt) > jetzt

        // Eine Ablehnung: naechste Stufe, Wartezeit aus Retry-After oder der Staffel (mindestens mindestMs). Waehrend einer
        // laufenden Sperre aendert sich nichts (ein zweiter Abruf auf anderem Weg steigert die Stufe nicht).
        @Synchronized
        fun ablehnung(schluessel: String, code: Int, retryAfter: String?, jetzt: Long, mindestMs: Long = 0L): Sperre {
            val k = schluessel(schluessel)
            val alt = karte[k]?.takeIf { jetzt - it.bis < VERGESSEN_MS }
            if (alt != null && alt.bis > jetzt) return alt
            val obergrenze = if (code == 403) STUFEN_403 else STAFFEL.size
            val stufe = maxOf(minOf((alt?.stufe ?: 0) + 1, obergrenze), alt?.stufe ?: 0)
            val ms = (retryAfterMs(retryAfter) ?: maxOf(staffelMs(stufe), mindestMs)).coerceAtMost(HOECHSTENS_MS)
            val neu = Sperre(jetzt + ms, stufe)
            karte.remove(k)
            karte[k] = neu
            kuerzen(jetzt)
            return neu
        }

        // Ein Abruf kam an: Sperre und Stufe fallen weg. true, wenn es einen Eintrag gab.
        @Synchronized
        fun erfolg(schluessel: String): Boolean = karte.remove(schluessel(schluessel)) != null

        @Synchronized
        fun zuruecksetzen() = karte.clear()

        @Synchronized
        fun anzahl(): Int = karte.size

        // Abgelaufene und vergessene Eintraege weg, danach die aeltesten, bis die Grenze eingehalten ist
        private fun kuerzen(jetzt: Long) {
            karte.entries.removeAll { jetzt - it.value.bis >= VERGESSEN_MS }
            if (karte.size <= maxEintraege) return
            val nachAlter = karte.entries.sortedBy { it.value.bis }.map { it.key }
            var i = 0
            while (karte.size > maxEintraege && i < nachAlter.size) karte.remove(nachAlter[i++])
        }

        // Eine Zeile je Eintrag: Schluessel, Ende der Sperre, Stufe, durch Tabulatoren getrennt
        @Synchronized
        fun text(jetzt: Long): String {
            kuerzen(jetzt)
            return karte.entries.joinToString("\n") { "${it.key}\t${it.value.bis}\t${it.value.stufe}" }
        }

        // Liest einen abgelegten Text und fuegt ihn zum Bestand; beschaedigte Zeilen und vergessene Eintraege fallen weg.
        // Vorhandenes (juengere Sperre) hat Vorrang.
        @Synchronized
        fun laden(text: String?, jetzt: Long) {
            if (text.isNullOrEmpty()) return
            for (zeile in text.split('\n')) {
                val t = zeile.split('\t')
                if (t.size != 3 || t[0].isBlank()) continue
                val bis = t[1].toLongOrNull() ?: continue
                val stufe = t[2].toIntOrNull()?.coerceIn(1, STAFFEL.size) ?: continue
                if (jetzt - bis >= VERGESSEN_MS) continue
                val da = karte[t[0]]
                if (da == null || da.bis < bis) karte[t[0]] = Sperre(bis, stufe)
            }
            kuerzen(jetzt)
        }
    }
}
