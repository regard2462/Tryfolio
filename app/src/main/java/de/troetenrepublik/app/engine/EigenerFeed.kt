package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.MITTEILUNG_ALLE
import de.troetenrepublik.app.data.MITTEILUNG_AUS
import de.troetenrepublik.app.data.MITTEILUNG_EIGENE
import de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE
import de.troetenrepublik.app.data.MITTEILUNG_QUELLEN
import de.troetenrepublik.app.market.Rss

// Eigene Feeds der Nachrichtenseite (5.96): jeder Feed fasst gewaehlte Quellen der App und eigene
// RSS- oder Atom-Zugaenge zu einer eigenen Kategorie zusammen. Gespeichert wird als Text mit einer
// Zeile je Datensatz, Felder durch Tabulator: F mit Kennung, Name und Melden-Flag beginnt einen
// Feed, Q nennt eine gewaehlte Quelle der App, Z einen eigenen Zugang des zuletzt begonnenen Feeds.
object EigenerFeed {

    // Grenzen und Laengen der eigenen Feeds.
    const val MAX_FEEDS = 12
    const val MAX_ZUGAENGE = 20
    const val MAX_NAME = 40
    const val MAX_ADRESSE = 400

    // Schluesselpraefix der Zugaenge als Rss.Quelle
    const val PRAEFIX = "feed:"

    // Eigener Zugang: Adresse eines RSS- oder Atom-Feeds mit Anzeigename.
    data class Zugang(val titel: String, val adresse: String)

    // Eigener Feed: Kennung, Name, gewaehlte Quellen der App, eigene Zugaenge und Melden-Flag fuer Mitteilungen.
    data class Feed(
        val kennung: String, val name: String, val quellen: List<String> = emptyList(),
        val zugaenge: List<Zugang> = emptyList(), val melden: Boolean = true,
    )

    // Tabulator und Zeilenumbrueche durch Leerzeichen ersetzen, damit der Speichertext zeilenweise bleibt.
    private fun sauber(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    // Speichertext der Feeds: je Zeile ein Datensatz, Felder durch Tabulator.
    fun text(feeds: List<Feed>): String {
        val zeilen = mutableListOf<String>()
        for (f in feeds) {
            zeilen += "F\t" + sauber(f.kennung) + "\t" + sauber(f.name) + "\t" + if (f.melden) "1" else "0"
            for (q in f.quellen) zeilen += "Q\t" + sauber(q)
            for (z in f.zugaenge) zeilen += "Z\t" + sauber(z.titel) + "\t" + z.adresse
        }
        return zeilen.joinToString("\n")
    }

    // Speichertext lesen. Kaputte Zeilen bleiben wirkungslos, ungueltige Feeds und Zugaenge fallen weg.
    fun lesen(text: String): List<Feed> {
        val feeds = mutableListOf<Feed>()
        val kennungen = HashSet<String>()
        var aktuell: Feed? = null
        for (zeile in text.lines()) {
            val teile = zeile.split('\t')
            when (teile[0]) {
                "F" -> {
                    aktuell = null
                    if (feeds.size >= MAX_FEEDS || (teile.size != 3 && teile.size != 4)) continue
                    val kennung = teile[1]
                    val name = teile[2].take(MAX_NAME)
                    if (kennung.isEmpty() || name.isBlank() || kennung in kennungen) continue
                    val neu = Feed(kennung, name, emptyList(), emptyList(), teile.size < 4 || teile[3] != "0")
                    kennungen += kennung
                    feeds += neu
                    aktuell = neu
                }
                "Q" -> {
                    val f = aktuell ?: continue
                    if (teile.size == 2 && teile[1].isNotEmpty() && teile[1] !in f.quellen) {
                        val neu = f.copy(quellen = f.quellen + teile[1])
                        feeds[feeds.size - 1] = neu
                        aktuell = neu
                    }
                }
                "Z" -> {
                    val f = aktuell ?: continue
                    if (teile.size != 3) continue
                    val bereinigteAdresse = adresse(teile[2]).adresse ?: continue
                    if (f.zugaenge.any { it.adresse == bereinigteAdresse } || f.zugaenge.size >= MAX_ZUGAENGE) continue
                    val neu = f.copy(zugaenge = f.zugaenge + Zugang(teile[1], bereinigteAdresse))
                    feeds[feeds.size - 1] = neu
                    aktuell = neu
                }
            }
        }
        return feeds
    }

    // Neue Kennung aus der Zeit in Basis 36, noetigenfalls mit Anhang bis sie noch frei ist.
    fun neueKennung(feeds: List<Feed>, jetzt: Long): String {
        val vergeben = feeds.mapTo(HashSet()) { it.kennung }
        var k = "f" + jetzt.toString(36)
        if (k in vergeben) {
            var n = 2
            while (k + "-" + n in vergeben) n++
            k += "-" + n
        }
        return k
    }

    // Feed fuer das Speichern bereinigen: Name gekuerzt, Quellen und Zugaenge ohne Leere und Dubletten.
    fun bereinigt(feed: Feed): Feed {
        val zugaenge = mutableListOf<Zugang>()
        for (z in feed.zugaenge) {
            if (zugaenge.size >= MAX_ZUGAENGE) break
            if (zugaenge.any { it.adresse == z.adresse }) continue
            zugaenge += z
        }
        return feed.copy(
            name = sauber(feed.name).trim().take(MAX_NAME),
            quellen = feed.quellen.map { sauber(it).trim() }.filter { it.isNotEmpty() }.distinct(),
            zugaenge = zugaenge,
        )
    }

    // Ob der Feed uebernommen werden kann: Name vorhanden und mindestens eine Quelle oder ein Zugang.
    fun gueltig(feed: Feed): Boolean =
        feed.name.trim().isNotEmpty() && (feed.quellen.isNotEmpty() || feed.zugaenge.isNotEmpty())

    // Feed bereinigt uebernehmen: gleiche Kennung an derselben Stelle ersetzen, sonst hinten anhaengen.
    fun speichern(feeds: List<Feed>, feed: Feed): List<Feed> {
        val b = bereinigt(feed)
        if (!gueltig(b)) return feeds
        val stelle = feeds.indexOfFirst { it.kennung == b.kennung }
        if (stelle >= 0) {
            val ergebnis = feeds.toMutableList()
            ergebnis[stelle] = b
            return ergebnis
        }
        return if (feeds.size < MAX_FEEDS) feeds + b else feeds
    }

    // Feed mit dieser Kennung wegnehmen.
    fun entfernen(feeds: List<Feed>, kennung: String): List<Feed> = feeds.filter { it.kennung != kennung }

    // Zugang anhaengen: gleiche Adresse nur einmal, Titel bereinigt, hoechstens MAX_ZUGAENGE Zugaenge.
    fun mitZugang(feed: Feed, z: Zugang): Feed {
        if (feed.zugaenge.any { it.adresse == z.adresse } || feed.zugaenge.size >= MAX_ZUGAENGE) return feed
        return feed.copy(zugaenge = feed.zugaenge + Zugang(sauber(z.titel).trim().take(80), z.adresse))
    }

    // Adresspruefung beim Hinzufuegen eines Zugangs
    enum class Fehler { LEER, SCHEMA, ZUGANGSDATEN, UNGUELTIG }

    // Bereinigte Adresse oder Fehler, genau eins von beiden ist gesetzt.
    data class Adresse(val adresse: String?, val fehler: Fehler?)

    // Muster fuer ein Schema wie javascript: am Anfang, Leerraum und ein einzelnes Label eines Hosts.
    private val SCHEMA_ANFANG = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")
    private val LEERRAUM = Regex("""\s""")
    private val LABEL = Regex("""^[\p{L}0-9-]{1,63}$""")

    // Namen von Anfrageparametern, die Zugangsdaten enthalten
    private val ZUGANGS_NAMEN = setOf(
        "password", "passwort", "pass", "pwd", "token", "access_token", "auth", "auth_token",
        "apikey", "api_key", "api-key", "key", "secret", "client_secret", "session", "sid",
    )

    // Adresse pruefen und bereinigen: nur http und https, vollstaendiger Host, ohne Zugangsdaten.
    fun adresse(eingabe: String): Adresse {
        val e = eingabe.trim()
        if (e.isEmpty() || e.equals("http://", true) || e.equals("https://", true)) return Adresse(null, Fehler.LEER)
        if (e.length > MAX_ADRESSE || LEERRAUM.containsMatchIn(e)) return Adresse(null, Fehler.UNGUELTIG)
        val schema: String
        var rest: String
        val trenner = e.indexOf("://")
        if (trenner >= 0) {
            schema = e.substring(0, trenner).lowercase()
            if (schema != "http" && schema != "https") return Adresse(null, Fehler.SCHEMA)
            rest = e.substring(trenner + 3)
        } else {
            val anfang = SCHEMA_ANFANG.find(e)
            if (anfang != null) {
                val danach = e.substring(anfang.value.length)
                if (danach.isNotEmpty() && !danach[0].isDigit()) return Adresse(null, Fehler.SCHEMA)
            }
            schema = "https"
            rest = e
        }
        var pfad = ""
        var autoritaet = rest
        val grenze = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (grenze >= 0) {
            autoritaet = rest.substring(0, grenze)
            pfad = rest.substring(grenze)
        }
        val raute = pfad.indexOf('#')
        if (raute >= 0) pfad = pfad.substring(0, raute)
        if (autoritaet.contains('@')) return Adresse(null, Fehler.ZUGANGSDATEN)
        var host = autoritaet
        var port = ""
        val doppelpunkt = autoritaet.lastIndexOf(':')
        if (doppelpunkt >= 0) {
            host = autoritaet.substring(0, doppelpunkt)
            port = autoritaet.substring(doppelpunkt + 1)
            if (port.isEmpty() || !port.all { it.isDigit() }) return Adresse(null, Fehler.UNGUELTIG)
            val nummer = port.toIntOrNull() ?: return Adresse(null, Fehler.UNGUELTIG)
            if (nummer < 1 || nummer > 65535) return Adresse(null, Fehler.UNGUELTIG)
        }
        val hostKlein = host.lowercase()
        val labels = hostKlein.split('.')
        if (labels.size < 2 || labels.any { it.startsWith("-") || it.endsWith("-") || !LABEL.matches(it) }) return Adresse(null, Fehler.UNGUELTIG)
        val frage = pfad.indexOf('?')
        if (frage >= 0) {
            for (teil in pfad.substring(frage + 1).split('&')) {
                if (teil.substringBefore('=').lowercase() in ZUGANGS_NAMEN) return Adresse(null, Fehler.ZUGANGSDATEN)
            }
        }
        return Adresse(schema + "://" + hostKlein + (if (port.isEmpty()) "" else ":$port") + pfad, null)
    }

    // Muster fuer das Ende des Feedkopfes, den Bildblock und den Titel darin.
    private val EINTRAG_ANFANG = Regex("<item|<entry", RegexOption.IGNORE_CASE)
    private val BILD_BLOCK = Regex("""<image[^>]*>[\s\S]*?</image>""", RegexOption.IGNORE_CASE)
    private val TITEL_BLOCK = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)

    // Titel des Feeds: erstes title-Element vor dem ersten Eintrag, ohne den Titel des Bilds.
    fun feedTitel(xml: String): String {
        val grenze = EINTRAG_ANFANG.find(xml)?.range?.first ?: xml.length
        val kopf = xml.substring(0, grenze)
        val ohneBild = BILD_BLOCK.replace(kopf, "")
        val titel = TITEL_BLOCK.find(ohneBild)?.groupValues?.get(1) ?: return ""
        return Rss.klartext(titel).trim().take(80)
    }

    // Ob die Quelle ein eigener Zugang ist.
    fun istZugang(q: Rss.Quelle): Boolean = q.schluessel.startsWith(PRAEFIX)

    // Adresse zum Vergleich: ohne Schema, Host klein ohne fuehrendes www., ohne abschliessenden Schraegstrich.
    private fun normal(adresse: String): String {
        var a = adresse
        val trenner = a.indexOf("://")
        if (trenner >= 0) a = a.substring(trenner + 3)
        val grenze = a.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val hostTeil = if (grenze < 0) a else a.substring(0, grenze)
        val rest = if (grenze < 0) "" else a.substring(grenze)
        var host = hostTeil.substringBefore(':').lowercase()
        if (host.startsWith("www.")) host = host.substring(4)
        val ganz = host + rest
        return if (ganz.endsWith("/")) ganz.dropLast(1) else ganz
    }

    // Erste feste Quelle, die diese Adresse abruft: Schema, www. und abschliessender Schraegstrich sind egal.
    fun eingebautZu(adresse: String, quellen: List<Rss.Quelle>): Rss.Quelle? {
        val gesucht = normal(adresse)
        return quellen.firstOrNull { !istZugang(it) && normal(it.adresse) == gesucht }
    }

    // Zugaenge aller Feeds als Abrufquellen: jede Adresse nur einmal und nur, wenn es sie nicht schon gibt.
    fun quellen(feeds: List<Feed>, vorhandene: List<Rss.Quelle>): List<Rss.Quelle> {
        val ergebnis = mutableListOf<Rss.Quelle>()
        val gesehen = HashSet<String>()
        for (f in feeds) {
            for (z in f.zugaenge) {
                if (z.adresse in gesehen) continue
                if (vorhandene.any { it.adresse == z.adresse }) continue
                if (eingebautZu(z.adresse, vorhandene) != null) continue
                gesehen += z.adresse
                ergebnis += Rss.Quelle(
                    schluessel = PRAEFIX + z.adresse,
                    name = z.titel.ifBlank { Rss.domain(z.adresse) },
                    adresse = z.adresse,
                    kategorie = Rss.Kategorie.EIGENE,
                    anbieter = Rss.Anbieter.EIGENE,
                    abstandMs = 5 * 60_000L,
                )
            }
        }
        return ergebnis
    }

    // Schluessel, deren Eintraege der Feed anzeigt: die gewaehlten plus je Zugang die passende Quelle.
    fun schluessel(feed: Feed, quellen: List<Rss.Quelle>): Set<String> {
        val ergebnis = HashSet<String>()
        ergebnis.addAll(feed.quellen)
        for (z in feed.zugaenge) {
            val quelle = quellen.firstOrNull { it.adresse == z.adresse } ?: eingebautZu(z.adresse, quellen)
            ergebnis += quelle?.schluessel ?: PRAEFIX + z.adresse
        }
        return ergebnis
    }

    // Meldungen des Feeds: Eintraege seiner Schluessel, ohne Dubletten, neueste zuerst, ohne Zeit am Ende.
    fun eintraege(alle: List<Rss.Eintrag>, feed: Feed, quellen: List<Rss.Quelle>): List<Rss.Eintrag> {
        val gesucht = schluessel(feed, quellen)
        val ids = HashSet<String>()
        val titel = HashSet<String>()
        val eigene = mutableListOf<Rss.Eintrag>()
        for (e in alle) {
            if (e.quellen.none { it in gesucht }) continue
            val t = e.titel.trim().lowercase()
            if (e.id in ids || t in titel) continue
            ids += e.id
            titel += t
            eigene += e
        }
        return eigene.filter { it.zeit > 0 }.sortedByDescending { it.zeit } + eigene.filter { it.zeit <= 0 }
    }

    // Gewaehlte Schluessel ohne Quelle: unter Quellen ausgeschaltet oder nicht mehr dabei.
    fun fehlend(feed: Feed, quellen: List<Rss.Quelle>): List<String> {
        val da = quellen.mapTo(HashSet()) { it.schluessel }
        return feed.quellen.filter { it !in da }
    }

    // Fuer Mitteilungen gewaehlte Schluessel aus dem Speichertext, je Zeile einer.
    fun meldeQuellen(text: String): Set<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toCollection(LinkedHashSet())

    // Speichertext der gewaehlten Schluessel: sortiert, je Zeile einer, ohne Umbrueche im Schluessel.
    fun meldeQuellenText(schluessel: Collection<String>): String =
        schluessel.map { sauber(it).trim() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString("\n")

    // Ob die Stufe die wichtigen Meldungen benoetigt: wichtige, eigene_wichtige und unbekannte Stufen.
    fun brauchtWichtige(stufe: String): Boolean =
        stufe != MITTEILUNG_AUS && stufe != MITTEILUNG_ALLE && stufe != MITTEILUNG_EIGENE && stufe != MITTEILUNG_QUELLEN

    // Welche frischen Schlagzeilen eine Mitteilung ausloese (5.96). frisch: neue Schlagzeilen in ihrer Reihenfolge,
    // wichtige: Kennungen der wichtigen, feeds/quellen: eigene Feeds und die abgerufenen Quellen, gewaehlt: Schluessel
    // fuer MITTEILUNG_QUELLEN. Die Reihenfolge von frisch bleibt.
    fun zuMelden(frisch: List<Rss.Eintrag>, stufe: String, wichtige: Set<String>, feeds: List<Feed>, quellen: List<Rss.Quelle>, gewaehlt: Set<String>): List<Rss.Eintrag> {
        val eigeneSchluessel = feeds.filter { it.melden }.flatMap { schluessel(it, quellen) }.toHashSet()
        return frisch.filter { e ->
            when (stufe) {
                MITTEILUNG_AUS -> false
                MITTEILUNG_ALLE -> true
                MITTEILUNG_QUELLEN -> e.quellen.any { it in gewaehlt }
                MITTEILUNG_EIGENE -> e.quellen.any { it in eigeneSchluessel }
                MITTEILUNG_EIGENE_WICHTIGE -> e.quellen.any { it in eigeneSchluessel } || e.id in wichtige
                else -> e.id in wichtige
            }
        }
    }
}
