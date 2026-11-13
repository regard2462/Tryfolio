package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.abs

// [Grosses Ganzes 0.73] Lagebild: wenige Aussagen zur Weltlage (Konjunktur und Inflation, Zinsen, Geopolitik, Rohstoffe und
// Energie, Klima, Technik), jede aus Daten gebildet, die die App ohnehin holt. Es gibt keine festen Urteilstexte, nur
// Zahlen mit Richtung gegen den Vergleichswert, keine Gesamtnote und keine Wahrscheinlichkeit fuer irgendein Ereignis.
// Gemerkt wird der Stand beim letzten Oeffnen, damit die Seite zeigt, was sich seitdem geaendert hat.
object Lagebild {
    enum class Thema { KONJUNKTUR, ZINSEN, GEOPOLITIK, ROHSTOFFE, KLIMA, TECHNIK }

    // Zahlenart: PROZENT (Wert in Prozent), ANZAHL (ganze Zahl), PUNKTE (Zahl mit Nachkommastellen, etwa Index),
    // SCHRITTE (Zinsschritte zu 0,25 Punkten), KATEGORIE (Ordnungszahl eines Zustands, etwa ENSO)
    enum class Einheit { PROZENT, ANZAHL, PUNKTE, SCHRITTE, KATEGORIE }

    // vorher: Vergleichswert (Vorjahr, vorheriger Stand) oder null; von: Zahl der Quellen/Reihen, aus denen der Wert stammt
    // (wird bei Anzeige "n Signale" genannt); schluessel zeigt auf den Text und die Seite der Kennzahl
    // posten: die wichtigsten Einzelwerte hinter der Zahl (Laender, Gebiete, Engpaesse), fuer die Uebersicht beim Aufklappen
    @Serializable
    class Kennzahl(
        val schluessel: String, val einheit: Einheit, val wert: Double, val vorher: Double? = null,
        val stand: Long? = null, val auffaellig: Boolean = false, val zusatz: String? = null,
        val posten: List<Posten> = emptyList(),
    )

    // name: Kuerzel des Landes (alpha-3), Kennung des Gebiets oder Engpasses, bei Zinsen die Monate; zusatz je nach Kennzahl
    @Serializable
    class Posten(val name: String, val wert: Double, val zusatz: String? = null)

    // [0.78] Zeile einer Tabelle unter der Karte: id je nach Tabelle (Land alpha-3, Gebiet, Engpass, Konflikt "A|B", Thema,
    // Monat JJJJ-MM); wert und vorher die Zahlen der Zeile; zahl eine zweite Zahl (davon rot, Gefechtsstufe, Durchfahrten,
    // Artikel); teile weitere Angaben (Laender eines Konflikts, Arten der Katastrophen, Schlagzeile und Quelle); zeit in ms
    @Serializable
    class Zeile(
        val id: String, val wert: Double? = null, val vorher: Double? = null, val zahl: Int = 0,
        val teile: List<String> = emptyList(), val zeit: Long? = null,
    )

    // Tabelle zum Aufklappen unter der Karte; schluessel benennt Inhalt und Text, jahr bei Jahreswerten, stand: Tag seit 1970
    @Serializable
    class Tabelle(val schluessel: String, val zeilen: List<Zeile>, val jahr: Int = 0, val stand: Long? = null)

    // ziel: Route der Seite, auf der die Daten stehen; tabellen: alles zum Nachlesen unter der Karte
    @Serializable
    class Aussage(val thema: Thema, val kennzahlen: List<Kennzahl>, val ziel: String, val tabellen: List<Tabelle> = emptyList()) {
        val signale: Int get() = kennzahlen.size
        val auffaellig: Int get() = kennzahlen.count { it.auffaellig }
        val stand: Long? get() = kennzahlen.mapNotNull { it.stand }.maxOrNull()
    }

    class Eingabe(
        val jetzt: Long,
        val imf: Imf.Datensatz? = null, val jahr: Int = 0,
        val zins: Zinserwartung.Erwartung? = null,
        val konflikte: Konflikte.Stand? = null,
        val spannungen: Map<String, Spannungen.Wert> = emptyMap(),
        val engpaesse: Engpaesse.Uebersicht? = null,
        val ensoStatus: Enso.Status? = null, val ensoIndex: Enso.Index? = null,
        val katastrophen: Katastrophen.Stand? = null,
        val durchbrueche: List<Durchbrueche.Meldung>? = null,
    )

    const val SPANNUNG_HOCH = 1.5
    const val TAGE_DURCHBRUCH = 7
    private val KLIMA_TYPEN = setOf("TC", "FL", "WF", "DR")

    private fun heute(jetzt: Long): Long = jetzt / 86_400_000L

    private fun konjunktur(e: Eingabe): Aussage? {
        val d = e.imf ?: return null
        val l = ArrayList<Kennzahl>()
        // letztes Jahr mit Weltwert: Wachstum und Inflation, Vergleich mit dem Jahr davor
        val jahr = (e.jahr downTo e.jahr - 3).firstOrNull { Imf.wert(d, Imf.WACHSTUM, Imf.WELT, it) != null }
        if (jahr != null) {
            Imf.wert(d, Imf.WACHSTUM, Imf.WELT, jahr)?.let { l += Kennzahl("WACHSTUM", Einheit.PROZENT, it, Imf.wert(d, Imf.WACHSTUM, Imf.WELT, jahr - 1), stand = d.abgerufen / 86_400_000L, zusatz = jahr.toString(), posten = groessteWirtschaften(d, Imf.WACHSTUM, jahr)) }
            Imf.wert(d, Imf.INFLATION, Imf.WELT, jahr)?.let { l += Kennzahl("INFLATION", Einheit.PROZENT, it, Imf.wert(d, Imf.INFLATION, Imf.WELT, jahr - 1), stand = d.abgerufen / 86_400_000L, zusatz = jahr.toString(), posten = groessteWirtschaften(d, Imf.INFLATION, jahr)) }
        }
        val tab = if (jahr == null) emptyList() else listOf(
            Tabelle("WACHSTUM_LAND", jeLand(d, Imf.WACHSTUM, jahr), jahr), Tabelle("INFLATION_LAND", jeLand(d, Imf.INFLATION, jahr), jahr),
        ).filter { it.zeilen.isNotEmpty() }
        return l.takeIf { it.isNotEmpty() }?.let { Aussage(Thema.KONJUNKTUR, it, "globus", tab) }
    }

    private fun zinsen(e: Eingabe): Aussage? {
        val z = e.zins ?: return null
        val l = ArrayList<Kennzahl>()
        l += Kennzahl("ZINS_HEUTE", Einheit.PROZENT, z.heute.zins, stand = heute(e.jetzt))
        val pfad = listOfNotNull(
            Posten("0", z.heute.zins), z.in3?.let { Posten("3", it.zins) }, z.in6?.let { Posten("6", it.zins) }, z.in12?.let { Posten("12", it.zins) },
        )
        z.schritte?.let { l += Kennzahl("ZINS_SCHRITTE", Einheit.SCHRITTE, it, auffaellig = abs(it) >= 2.0, stand = heute(e.jetzt), posten = pfad) }
        // Den Satz je Monat zeigt die Zinskarte im Ausblick (Weg am Ende der Karte)
        return Aussage(Thema.ZINSEN, l, "maerkte/ausblick")
    }

    private fun geopolitik(e: Eingabe): Aussage? {
        val l = ArrayList<Kennzahl>()
        e.konflikte?.let { k ->
            val kriege = k.nachLand.values.flatten().filter { it.stufe >= 2 }.map { it.seiteA + "|" + it.seiteB }.distinct().size
            l += Kennzahl("KRIEGE", Einheit.ANZAHL, kriege.toDouble(), stand = null, zusatz = k.jahr.toString(), posten = kriegsLaender(k))
        }
        if (e.spannungen.isNotEmpty()) {
            val hoch = e.spannungen.values.count { it.faktor >= SPANNUNG_HOCH }
            val bis = e.spannungen.values.maxOf { it.bis }.toLong()
            l += Kennzahl("SPANNUNG", Einheit.ANZAHL, hoch.toDouble(), stand = bis, auffaellig = hoch > 0, zusatz = e.spannungen.size.toString(), posten = spannungsGebiete(e.spannungen))
        }
        val tab = ArrayList<Tabelle>()
        e.konflikte?.let { k ->
            tab += Tabelle("KRIEGE", konfliktListe(k, 2), k.jahr)
            tab += Tabelle("KONFLIKTE", konfliktListe(k, 1), k.jahr)
        }
        if (e.spannungen.isNotEmpty()) {
            val zeilen = e.spannungen.values.filter { it.faktor.isFinite() }
                .sortedWith(compareByDescending<Spannungen.Wert> { it.faktor }.thenBy { it.id }).map { Zeile(it.id, it.faktor, zahl = it.artikel) }
            tab += Tabelle("SPANNUNG", zeilen, stand = e.spannungen.values.maxOf { it.bis }.toLong())
        }
        return l.takeIf { it.isNotEmpty() }?.let { Aussage(Thema.GEOPOLITIK, it, "globus/engpaesse", tab.filter { t -> t.zeilen.isNotEmpty() }) }
    }

    private fun rohstoffe(e: Eingabe): Aussage? {
        val u = e.engpaesse ?: return null
        val gestoert = Engpaesse.gestoerte(u)
        val l = ArrayList<Kennzahl>()
        l += Kennzahl("ENGPASS_GESTOERT", Einheit.ANZAHL, gestoert.size.toDouble(), stand = u.tag.toLong(), auffaellig = gestoert.isNotEmpty(), zusatz = Engpaesse.STELLEN.count { u.lage[it.id] != null }.toString(),
            posten = gestoert.take(MAX_POSTEN).map { (s, lage) -> Posten(s.id, lage.abw ?: 0.0) })
        Engpaesse.schlechtester(u)?.let { (s, lage) -> l += Kennzahl("ENGPASS_SCHLECHTESTER", Einheit.PROZENT, lage.abw ?: 0.0, stand = u.tag.toLong(), zusatz = s.id, posten = Engpaesse.auffaellige(u, MAX_POSTEN).map { (x, y) -> Posten(x.id, y.abw ?: 0.0) }) }
        // Alle Engpaesse, groesster Rueckgang zuerst, ohne Vorjahreswert am Ende; zahl: Durchfahrten pro Tag
        val alle = Engpaesse.sortiert(u).map { (st, lage) ->
            Zeile(st.id, lage.abw?.takeIf { it.isFinite() }, lage.vn?.takeIf { it.isFinite() }, zahl = if (lage.n.isFinite()) kotlin.math.round(lage.n).toInt() else 0)
        }
        val tab = if (alle.isEmpty()) emptyList() else listOf(Tabelle("ENGPAESSE", alle, stand = u.tag.toLong()))
        return Aussage(Thema.ROHSTOFFE, l, "globus/engpaesse", tab)
    }

    private fun klima(e: Eingabe): Aussage? {
        val l = ArrayList<Kennzahl>()
        e.ensoStatus?.let { s ->
            val z = Enso.Zustand.entries.firstOrNull { it.name == s.zustand } ?: Enso.Zustand.UNBEKANNT
            if (z != Enso.Zustand.UNBEKANNT) l += Kennzahl("ENSO_ZUSTAND", Einheit.KATEGORIE, z.ordinal.toDouble(), stand = s.abgerufen / 86_400_000L, auffaellig = z == Enso.Zustand.EL_NINO_ADVISORY || z == Enso.Zustand.LA_NINA_ADVISORY, zusatz = z.name)
        }
        e.ensoIndex?.let { idx ->
            idx.punkte.lastOrNull()?.let { p ->
                // Verlauf: die letzten Dreimonatswerte, aelteste zuerst; name: Saison und Jahr
                val verlauf = idx.punkte.takeLast(MAX_VERLAUF).map { Posten(it.saison + " " + it.jahr, it.wert) }
                l += Kennzahl("ENSO_INDEX", Einheit.PUNKTE, p.wert, idx.punkte.dropLast(1).lastOrNull()?.wert, stand = idx.abgerufen / 86_400_000L, posten = verlauf)
            }
        }
        val tab = ArrayList<Tabelle>()
        e.katastrophen?.let { k ->
            val grenze = LocalDate.ofEpochDay(heute(e.jetzt)).minusDays(Katastrophen.TAGE.toLong()).toString()
            val aktuell = k.ereignisse.filter { it.typ in KLIMA_TYPEN && it.bis >= grenze }
            l += Kennzahl("KATASTROPHEN", Einheit.ANZAHL, aktuell.size.toDouble(), stand = k.abgerufen / 86_400_000L, auffaellig = aktuell.any { it.rot }, zusatz = aktuell.count { it.rot }.toString(), posten = katastrophenLaender(aktuell))
            tab += Tabelle("KATASTROPHEN_LAND", katastrophenJeLand(aktuell), stand = k.abgerufen / 86_400_000L)
        }
        return l.takeIf { it.isNotEmpty() }?.let { Aussage(Thema.KLIMA, it, "globus/engpaesse", tab.filter { t -> t.zeilen.isNotEmpty() }) }
    }

    private fun technik(e: Eingabe): Aussage? {
        val m = e.durchbrueche ?: return null
        val grenze = e.jetzt - TAGE_DURCHBRUCH * 86_400_000L
        val neu = m.filter { it.zeit >= grenze }
        val l = ArrayList<Kennzahl>()
        l += Kennzahl("DURCHBRUCH_NEU", Einheit.ANZAHL, neu.size.toDouble(), stand = heute(e.jetzt))
        l += Kennzahl("DURCHBRUCH_THEMEN", Einheit.ANZAHL, neu.map { it.thema }.distinct().size.toDouble(), stand = heute(e.jetzt), posten = durchbruchThemen(neu))
        val themen = durchbruchJeThema(m, grenze)
        return Aussage(Thema.TECHNIK, l, "maerkte/trends", if (themen.isEmpty()) emptyList() else listOf(Tabelle("DURCHBRUCH_THEMA", themen, stand = heute(e.jetzt))))
    }

    // [0.78] Weg aus der Karte in die ausfuehrliche Seite (letzte Zeile der Tabelle): genau die Ebene oder der Abschnitt
    fun weg(t: Thema): String = when (t) {
        Thema.KONJUNKTUR -> "globus/wachstum"
        Thema.ZINSEN -> "maerkte/ausblick/zins"
        Thema.GEOPOLITIK -> "globus/engpaesse/weltlage"
        Thema.ROHSTOFFE -> "globus/engpaesse"
        Thema.KLIMA -> "globus/engpaesse/weltlage"
        Thema.TECHNIK -> "maerkte/trends/durchbrueche"
    }

    // Alle Laender mit Wert der Reihe im Jahr, hoechster zuerst; vorher: Wert im Vorjahr
    fun jeLand(d: Imf.Datensatz, kennung: String, jahr: Int): List<Zeile> =
        Imf.laenderCodes(d).mapNotNull { c ->
            val w = Imf.wert(d, kennung, c, jahr)?.takeIf { it.isFinite() } ?: return@mapNotNull null
            Zeile(c, w, Imf.wert(d, kennung, c, jahr - 1)?.takeIf { it.isFinite() })
        }.sortedWith(compareByDescending<Zeile> { it.wert }.thenBy { it.id })

    // Schluessel eines Konflikts: beide Seiten, wie die Daten sie nennen
    fun konfliktSchluessel(k: Konflikte.Konflikt): String = k.seiteA + "|" + k.seiteB

    // Konflikte einer Stufe (2 Krieg, 1 bewaffneter Konflikt), jeder einmal; teile: Laender, unter denen die Daten ihn fuehren
    fun konfliktListe(k: Konflikte.Stand, stufe: Int): List<Zeile> {
        // Ein Krieg steht nur unter den Kriegen, auch wenn ein Land ihn niedriger fuehrt
        val kriege = k.nachLand.values.flatten().filter { it.stufe >= 2 }.map { konfliktSchluessel(it) }.toSet()
        val je = LinkedHashMap<String, MutableList<String>>()
        for ((land, liste) in k.nachLand.entries.sortedBy { it.key }) for (x in liste) {
            if ((konfliktSchluessel(x) in kriege) != (stufe >= 2)) continue
            val l = je.getOrPut(konfliktSchluessel(x)) { ArrayList() }
            if (land !in l) l += land
        }
        return je.entries.sortedWith(compareByDescending<Map.Entry<String, MutableList<String>>> { it.value.size }.thenBy { it.key })
            .map { Zeile(it.key, zahl = stufe, teile = it.value) }
    }

    // Laender mit Wetterkatastrophen: wert Zahl, zahl davon rot, teile die Arten (Sturm, Flut, Brand, Duerre)
    fun katastrophenJeLand(ereignisse: List<Katastrophen.Ereignis>): List<Zeile> {
        val je = HashMap<String, MutableList<Katastrophen.Ereignis>>()
        for (e in ereignisse) for (c in e.laender.distinct()) je.getOrPut(c) { ArrayList() } += e
        val ordnung = listOf("TC", "FL", "WF", "DR")
        return je.entries.map { (c, l) -> Zeile(c, l.size.toDouble(), zahl = l.count { it.rot }, teile = l.map { it.typ }.distinct().sortedBy { ordnung.indexOf(it) }) }
            .sortedWith(compareByDescending<Zeile> { it.zahl }.thenByDescending { it.wert }.thenBy { it.id })
    }

    // Je Thema mit Meldung: wert neue Meldungen seit `grenze`, teile die juengste Schlagzeile und ihre Quelle, zeit deren Zeit
    fun durchbruchJeThema(m: List<Durchbrueche.Meldung>, grenze: Long): List<Zeile> =
        m.groupBy { it.thema }.map { (thema, l) ->
            val j = l.maxBy { it.zeit }
            Zeile(thema, l.count { it.zeit >= grenze }.toDouble(), teile = listOf(j.titel, j.quelle), zeit = j.zeit)
        }.sortedWith(compareByDescending<Zeile> { it.wert }.thenByDescending { it.zeit }.thenBy { it.id })

    // So viele Einzelwerte zeigt eine Karte hoechstens
    const val MAX_POSTEN = 5

    // So viele Dreimonatswerte des ENSO-Index zeigt der Verlauf
    const val MAX_VERLAUF = 12

    // Themen mit den meisten neuen Meldungen; name: Kennung des Themas, wert: Zahl der Meldungen
    fun durchbruchThemen(neu: List<Durchbrueche.Meldung>, n: Int = MAX_POSTEN): List<Posten> =
        neu.groupingBy { it.thema }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(n).map { Posten(it.key, it.value.toDouble()) }

    // Stufe einer Kennzahl: 0 ruhig, 1 auffaellig, 2 kritisch (Wachstum unter null, Wetterkatastrophen mit Warnstufe rot)
    fun stufe(k: Kennzahl): Int = when (k.schluessel) {
        "WACHSTUM" -> if (k.wert < 0.0) 2 else 0
        "INFLATION" -> if (k.wert >= Imf.SCHWELLE_INFLATION_ERHOEHT) 1 else 0
        "ENGPASS_SCHLECHTESTER" -> if (k.wert <= Engpaesse.STOERUNG_PROZENT) 1 else 0
        "KATASTROPHEN" -> if ((k.zusatz?.toIntOrNull() ?: 0) > 0) 2 else if (k.auffaellig) 1 else 0
        else -> if (k.auffaellig) 1 else 0
    }

    fun stufe(a: Aussage): Int = a.kennzahlen.maxOfOrNull { stufe(it) } ?: 0

    // Zahl der Kennzahlen mit Stufe ab 1 und Zahl aller Kennzahlen
    fun auffaellige(a: List<Aussage>): Pair<Int, Int> =
        a.sumOf { x -> x.kennzahlen.count { stufe(it) > 0 } } to a.sumOf { it.kennzahlen.size }

    // Die groessten Volkswirtschaften (Bruttoinlandsprodukt in Dollar im Jahr) mit ihrem Wert der Reihe `kennung`
    fun groessteWirtschaften(d: Imf.Datensatz, kennung: String, jahr: Int, n: Int = MAX_POSTEN): List<Posten> =
        Imf.laenderCodes(d).mapNotNull { c ->
            val wert = Imf.wert(d, kennung, c, jahr)?.takeIf { it.isFinite() } ?: return@mapNotNull null
            val bip = Imf.wert(d, Imf.BIP, c, jahr)?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
            Triple(c, bip, wert)
        }.sortedWith(compareByDescending<Triple<String, Double, Double>> { it.second }.thenBy { it.first }).take(n).map { Posten(it.first, it.third) }

    // Laender mit den meisten Kriegen (mindestens 1000 Gefechtstote im Jahr); wert: Zahl der Kriege im Land
    fun kriegsLaender(k: Konflikte.Stand, n: Int = MAX_POSTEN): List<Posten> =
        k.nachLand.mapNotNull { (land, liste) ->
            val z = liste.filter { it.stufe >= 2 }.map { it.seiteA + "|" + it.seiteB }.distinct().size
            if (z > 0) land to z else null
        }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).take(n).map { Posten(it.first, it.second.toDouble()) }

    // Gebiete mit dem staerksten Anstieg der Berichte (Faktor gegen davor); bei Gleichstand nach Kennung
    fun spannungsGebiete(m: Map<String, Spannungen.Wert>, n: Int = MAX_POSTEN): List<Posten> =
        m.entries.filter { it.value.faktor.isFinite() }
            .sortedWith(compareByDescending<Map.Entry<String, Spannungen.Wert>> { it.value.faktor }.thenBy { it.key })
            .take(n).map { Posten(it.key, it.value.faktor) }

    // Laender mit den meisten Ereignissen; wert: Zahl, zusatz: davon rot
    fun katastrophenLaender(ereignisse: List<Katastrophen.Ereignis>, n: Int = MAX_POSTEN): List<Posten> {
        val je = HashMap<String, IntArray>()
        for (e in ereignisse) for (c in e.laender.distinct()) {
            val a = je.getOrPut(c) { IntArray(2) }
            a[0]++
            if (e.rot) a[1]++
        }
        return je.entries.sortedWith(compareByDescending<Map.Entry<String, IntArray>> { it.value[1] }.thenByDescending { it.value[0] }.thenBy { it.key })
            .take(n).map { Posten(it.key, it.value[0].toDouble(), it.value[1].toString()) }
    }

    // Die Aussagen in fester Reihenfolge; ein Thema ohne Daten fehlt (die Anzeige nennt es als offen)
    fun aussagen(e: Eingabe): List<Aussage> =
        listOfNotNull(konjunktur(e), zinsen(e), geopolitik(e), rohstoffe(e), klima(e), technik(e))

    // Zuletzt bekannter Stand der Seite: wird beim Oeffnen sofort gezeigt, waehrend die Abrufe laufen
    @Serializable
    class Stand(val zeit: Long = 0L, val aussagen: List<Aussage> = emptyList())

    fun schreibenStand(s: Stand): String = json.encodeToString(Stand.serializer(), s)
    fun lesenStand(text: String): Stand? = runCatching { json.decodeFromString(Stand.serializer(), text) }.getOrNull()?.takeIf { it.aussagen.isNotEmpty() }

    // Neue Aussagen ueber den letzten Stand legen: was ein Abruf nicht geliefert hat (kein Netz, Zeitgrenze), bleibt vom
    // letzten Stand stehen statt als leer zu erscheinen. Je Thema und Kennzahl gilt der neue Wert, sonst der alte.
    fun zusammenfuehren(alt: List<Aussage>?, neu: List<Aussage>): List<Aussage> {
        if (alt.isNullOrEmpty()) return neu
        return Thema.entries.mapNotNull { t ->
            val n = neu.firstOrNull { it.thema == t }
            val a = alt.firstOrNull { it.thema == t }
            when {
                n == null -> a
                a == null -> n
                else -> {
                    val neuJe = n.kennzahlen.associateBy { it.schluessel }
                    val reihe = a.kennzahlen.map { neuJe[it.schluessel] ?: it } + n.kennzahlen.filter { k -> a.kennzahlen.none { it.schluessel == k.schluessel } }
                    // Tabellen ebenso: die neue gilt, eine fehlende bleibt vom letzten Stand
                    val neuTab = n.tabellen.associateBy { it.schluessel }
                    val tabellen = a.tabellen.map { neuTab[it.schluessel] ?: it } + n.tabellen.filter { x -> a.tabellen.none { it.schluessel == x.schluessel } }
                    Aussage(t, reihe, n.ziel, tabellen)
                }
            }
        }
    }

    // Zusammenfassung zum Merken: Schluessel (Thema und Kennzahl) zu Wert
    fun merken(a: List<Aussage>): Map<String, Double> =
        a.flatMap { x -> x.kennzahlen.map { (x.thema.name + "." + it.schluessel) to it.wert } }.toMap()

    @Serializable
    class Gemerkt(val zeit: Long = 0L, val werte: Map<String, Double> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }
    fun schreiben(g: Gemerkt): String = json.encodeToString(Gemerkt.serializer(), g)
    fun lesen(text: String): Gemerkt? = runCatching { json.decodeFromString(Gemerkt.serializer(), text) }.getOrNull()

    class Aenderung(val thema: Thema, val schluessel: String, val von: Double, val nach: Double, val einheit: Einheit)

    // kleinste Aenderung, die als Aenderung gilt
    private fun schwelle(k: Kennzahl): Double = when (k.einheit) {
        Einheit.PROZENT -> 0.1
        Einheit.ANZAHL -> 1.0
        Einheit.PUNKTE -> 0.3
        Einheit.SCHRITTE -> 0.5
        Einheit.KATEGORIE -> 1.0
    }

    const val MAX_AENDERUNGEN = 5
    private val OHNE_VERGLEICH = setOf("ENGPASS_SCHLECHTESTER", "DURCHBRUCH_NEU", "DURCHBRUCH_THEMEN")

    // Was sich gegen den gemerkten Stand geaendert hat; ohne gemerkten Stand nichts (erstes Oeffnen)
    fun aenderungen(alt: Gemerkt?, a: List<Aussage>): List<Aenderung> {
        if (alt == null || alt.werte.isEmpty()) return emptyList()
        val aus = ArrayList<Aenderung>()
        for (x in a) for (k in x.kennzahlen) {
            val v = alt.werte[x.thema.name + "." + k.schluessel] ?: continue
            // Der Engpass mit dem staerksten Rueckgang kann wechseln; dann sind die Prozentwerte nicht vergleichbar.
            // Zaehlungen ueber die letzten Tage (Durchbruchmeldungen) aendern sich taeglich und sagen als Aenderung nichts.
            if (k.schluessel in OHNE_VERGLEICH) continue
            if (abs(k.wert - v) >= schwelle(k) - 1e-9) aus += Aenderung(x.thema, k.schluessel, v, k.wert, k.einheit)
        }
        return aus.take(MAX_AENDERUNGEN)
    }
}
