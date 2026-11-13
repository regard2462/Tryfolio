package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Firmenname
import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.IpoKalender.Status
import java.time.LocalDate

// Uebersicht der Boersengaenge (0.77): Kopfzahlen, grosse Angebote, Platzhalterbetrag des Prospekts, Suchtext und
// Namensabgleich fuer die Berichte, Monate des Kalenderabrufs. Reine Funktionen ohne Oberflaeche.
object IpoUebersicht {

    // Hoechstens so viele grosse Angebote im Block; fuer jedes davon wird ein Bericht gesucht (nie mehr als acht).
    const val GROSSE_ANZAHL = 6

    // Ab diesem Volumen (Dollar) zaehlt ein anstehendes oder eingereichtes Angebot als gross.
    const val GROSS_AB = 250_000_000.0

    // Zeitraum der Zahl „gestartet“ im Kopf.
    const val KOPF_TAGE = 30

    // Listen mit mehr Zeilen zeigen die ersten und klappen den Rest ein.
    const val ZEILEN_OFFEN = 8

    // Der Bericht bleibt so lange gespeichert (Millisekunden).
    const val BERICHT_HALTBAR_MS = 60 * 60_000L

    private val RECHTSFORM = Regex(
        "(?i)[,\\s]+(pbc|llc|l\\.l\\.c\\.|lp|l\\.p\\.|inc\\.?|corp\\.?|corporation|ltd\\.?|limited|plc|b\\.v\\.|bv|gmbh)\\.?\\s*$",
    )

    // Firmenname ohne Rechtsform („Acme, PBC“ wird „Acme“), Gattung und Notierungszusatz.
    fun firmenKern(name: String): String {
        var s = Firmenname.bereinigt(name)
        while (true) {
            val t = s.replace(RECHTSFORM, "").trim().trimEnd(',', ' ')
            if (t == s || t.isBlank()) break
            s = t
        }
        return s
    }

    // Suchtext der Schlagzeilen: Firmenname in Anfuehrungszeichen plus IPO.
    fun suchtext(name: String): String = "\"" + firmenKern(name) + "\" IPO"

    // Die Schlagzeile nennt die Firma: der Kern des Namens steht als ganzes Wort im Titel (mindestens drei Zeichen).
    fun nameTrifft(titel: String, name: String): Boolean {
        val k = firmenKern(name)
        if (k.length < 3) return false
        return Muster.re("(?<![\\p{L}\\p{N}])" + Regex.escape(k) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(titel)
    }

    // Die jüngste Schlagzeile, die die Firma nennt; null ohne Treffer. Eintraege: Titel, Link, Zeit.
    // Schlagzeilen zu Termin und Preis (Erstnotiz, Handelsstart, Preisspanne) gehen vor, solange sie hoechstens
    // TERMIN_TAGE aelter sind als die juengste; sonst gewinnt die juengste. Ein Datum wird daraus nicht gelesen.
    fun juengste(meldungen: List<LaenderZusatz.Meldung>, name: String): LaenderZusatz.Meldung? {
        val treffer = meldungen.filter { it.titel.isNotBlank() && it.link.startsWith("http") && nameTrifft(it.titel, name) }
        val neueste = treffer.maxByOrNull { it.zeit } ?: return null
        return treffer.filter { nenntTermin(it.titel) && neueste.zeit - it.zeit <= TERMIN_TAGE * 86_400_000L }.maxByOrNull { it.zeit } ?: neueste
    }

    const val TERMIN_TAGE = 10

    private val TERMIN_WORT = Regex(
        "(?i)erstnotiz|handelsstart|b\u00f6rsenstart|b\u00f6rsendeb\u00fct|b\u00f6rsenpremiere|preisspanne|zeichnungsfrist|ausgabepreis|" +
            "price range|pricing|priced|prices ipo|sets terms|trading debut|market debut|first day of trading|set to list|to debut|roadshow",
    )

    // Die Schlagzeile handelt von Termin oder Preis des Boersengangs.
    fun nenntTermin(titel: String): Boolean = TERMIN_WORT.containsMatchIn(titel)

    // Eingereicht ohne Preis: der Betrag der Quelle ist der Platzhalter des Prospekts (Grundlage der Gebuehr), kein
    // Angebotsvolumen.
    fun platzhalter(a: IpoKalender.Angebot): Boolean = a.status == Status.EINGEREICHT && a.preis == null && a.volumen != null

    // Nach Volumen groesste anstehende und eingereichte Angebote ohne Mantelgesellschaften.
    fun grosse(liste: List<IpoKalender.Angebot>, n: Int = GROSSE_ANZAHL): List<IpoKalender.Angebot> =
        liste.filter { !it.spac && (it.status == Status.ANGEKUENDIGT || it.status == Status.EINGEREICHT) && (it.volumen ?: 0.0) >= GROSS_AB }
            .sortedWith(compareByDescending<IpoKalender.Angebot> { it.volumen ?: 0.0 }.thenBy { it.name })
            .take(n)

    // Reihenfolge einer Liste: Mantelgesellschaften zuletzt, sonst grosse Angebote zuerst.
    fun grossZuerst(liste: List<IpoKalender.Angebot>): List<IpoKalender.Angebot> =
        liste.sortedWith(compareBy<IpoKalender.Angebot> { it.spac }.thenByDescending { it.volumen ?: 0.0 }.thenBy { it.name })

    // Summe der echten Volumen (ohne Platzhalter).
    fun volumenSumme(liste: List<IpoKalender.Angebot>): Double =
        liste.filter { !platzhalter(it) }.sumOf { it.volumen ?: 0.0 }

    data class Kopf(val anstehend: Int, val eingereicht: Int, val gestartet: Int)

    // Zahlen im Kopf: anstehend, eingereicht, in den letzten 30 Tagen gestartet (heute als Tag seit 1970).
    fun kopf(liste: List<IpoKalender.Angebot>, heute: Long): Kopf = Kopf(
        anstehend = liste.count { it.status == Status.ANGEKUENDIGT },
        eingereicht = liste.count { it.status == Status.EINGEREICHT },
        gestartet = liste.count { it.status == Status.PREIS && it.tag != null && it.tag >= heute - KOPF_TAGE && it.tag <= heute },
    )

    // Monate des Kalenderabrufs: der Folgemonat, der laufende und die zwei davor.
    fun abrufMonate(heute: LocalDate): List<LocalDate> = listOf(heute.plusMonths(1), heute, heute.minusMonths(1), heute.minusMonths(2))

    // Aeltere Monate fuer den Kursverlauf nach dem Start: vom dritten Vormonat zurueck bis monate vor heute.
    fun rueckblickMonate(heute: LocalDate, monate: Int = IpoBlatt.RUECKBLICK_MONATE): List<LocalDate> = (3..monate).map { heute.minusMonths(it.toLong()) }
}
