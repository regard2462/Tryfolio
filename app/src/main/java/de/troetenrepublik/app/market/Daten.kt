package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Alter
import de.troetenrepublik.app.engine.Datenbuendel
import de.troetenrepublik.app.engine.Datenbuendel.Ergebnis
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// Gemeinsamer Zugriff auf gepflegte Dateien (Durchbrueche, Strukturen, Laenderdaten, Katalog ...): text(name) und bytes(name)
// liefern die neuere von eingebauter Datei und zuletzt geprueftem Stand aus dem Datenbuendel (Datenbuendel). Ohne Netz,
// ohne gueltige Kette oder bei kaputter Datei gilt die eingebaute. Einmal am Tag fragt die App das signierte Manifest
// des Projektarchivs; nichts wird geloescht oder ersetzt, bevor Signaturkette, Pruefsumme, Schema, Mindestfassung und ein
// Probelesen mit dem Leser der Datei bestanden sind. Ist das Zertifikat des Datenschluessels abgelaufen, bleibt die App auf den
// zuletzt geprueften Dateien und kennzeichnet sie ueber das Alter.
object Daten {

    // Antwort eines Abrufs: HTTP-Status (0 ohne Verbindung) und Text.
    class Abruf(val code: Int, val text: String?)

    // Stand des Buendels fuer die Seite Datenquellen.
    data class Zustand(
        val manifestVersion: Long = 0,
        // Datum des letzten Manifests (JJJJ-MM-TT), leer ohne
        val manifestStand: String = "",
        val zertifikatBis: String = "",
        val abgelaufen: Boolean = false,
        val geprueft: Long = 0,
        // NEU, AKTUELL, NETZ, SIGNATUR, FORM, ABGELAUFEN, OHNE_ZERTIFIKAT
        val ergebnis: String = "",
    )

    // Eingebaute Dateien lesen (Tests ersetzen das).
    @Volatile internal var assetLeser: (String) -> ByteArray = { Plattform.asset(it) }

    // Hauptschluessel, die das Zertifikat des Datenschluessels signieren duerfen (Tests setzen eigene).
    @Volatile internal var haupt: () -> List<ByteArray> = { Quellen.schluessel }

    private val _zustand = MutableStateFlow(Zustand())
    val zustand: StateFlow<Zustand> = _zustand

    private val gestartet = AtomicBoolean(false)
    @Volatile private var ordner: File? = null
    @Volatile private var zertifikat: Datenbuendel.Zertifikat? = null
    @Volatile private var manifest: Datenbuendel.Manifest? = null
    // Geprueft gelesene Dateien vom Geraet, mit der Pruefsumme, zu der sie passten
    private val speicher = ConcurrentHashMap<String, Pair<String, ByteArray>>()
    private val pruefungen = ConcurrentHashMap<String, Boolean>()

    private fun sha512(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(b)
    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    @Volatile private var eingebautMerk: Map<String, Datenbuendel.Datei>? = null
    private val eingebaut: Map<String, Datenbuendel.Datei>
        get() = eingebautMerk ?: runCatching { Datenbuendel.manifestLesen(assetLeser("daten/manifest.json").decodeToString())?.dateien }.getOrNull().orEmpty().also { eingebautMerk = it }

    // Probelesen mit dem Leser der Datei: eine Ferndatei, die der Leser nicht versteht, ersetzt die eingebaute nie.
    internal val PRUEFER: Map<String, (String) -> Boolean> = mapOf(
        "durchbrueche/inhalt.json" to { t -> de.troetenrepublik.app.engine.Durchbrueche.lesenInhalt(t) != null },
        "globus/strukturen.json" to { t -> de.troetenrepublik.app.engine.LaenderStrukturen.lesen(t) != null },
        "globus/laenderdaten.json" to { t -> de.troetenrepublik.app.engine.LaenderDaten.lesen(t, true) != null },
        "globus/sanktionen.json" to { t -> de.troetenrepublik.app.engine.Sanktionen.listeLesen(t) != null },
        "globus/konflikte.json" to { t -> de.troetenrepublik.app.engine.Konflikte.lesen(t) != null },
        "globus/firmen.json" to { t -> de.troetenrepublik.app.engine.LaenderFirmen.buendel(t).isNotEmpty() },
        "globus/firmen-privat.json" to { t -> de.troetenrepublik.app.engine.LaenderFirmen.privat(t).isNotEmpty() },
        "globus/handel.json" to { t -> de.troetenrepublik.app.engine.LaenderZusatz.handelLesen(t).isNotEmpty() },
        "globus/ersatz.json" to { t -> de.troetenrepublik.app.engine.ImfErsatz.dateiLesen(t) != null },
        "globus/steckbrief.json" to { t -> de.troetenrepublik.app.engine.LaenderZusatz.steckbriefLesen(t).isNotEmpty() },
        "globus/taetigkeit.json" to { t -> de.troetenrepublik.app.engine.Taetigkeit.lesenDatei(t).isNotEmpty() },
        "copytrading/investoren.json" to { t -> Investoren.lesen(t).isNotEmpty() },
        "copytrading/gesamt13f.json" to { t -> Gesamt13f.lesen(t) != null },
        "firmen/emissionen.json" to { t -> FirmaEmissionen.lesen(t) != null },
        "firmen/emissionen-eu.json" to { t -> FirmaEmissionen.lesen(t) != null },
        "firmen/staatsgeld-eu.json" to { t -> Staatsgeld.euLesen(t) != null },
        "copytrading/personen.json" to { t -> Personeninfo.lesen(t) != null },
        "copytrading/amt.json" to { t -> RegierungOge.amtLesen(t) != null },
        "copytrading/sec_historie.json" to { t -> de.troetenrepublik.app.engine.InsiderHistorie.lesen(t) != null },
        "steuerwerte.json" to { t -> de.troetenrepublik.app.engine.Steuerwerte.lesen(t) != null },
        "etf-rueckwaerts.json" to { t -> de.troetenrepublik.app.engine.EtfRueckwaerts.lesen(t) != null },
        "globus/jst.json" to { t -> de.troetenrepublik.app.engine.HistorieJst.lesen(t) != null },
        "globus/hauspreise.json" to { t -> de.troetenrepublik.app.engine.Hauspreise.lesen(t) != null },
        "indikatoren/lange_reihen.json" to { t -> de.troetenrepublik.app.engine.LangeReihen.lesen(t) != null },
        "globus/waehrung.json" to { t -> de.troetenrepublik.app.engine.Waehrungsstaerke.lesen(t) != null },
        "globus/bigmac.json" to { t -> de.troetenrepublik.app.engine.BigMac.lesen(t) != null },
        "globus/ranglisten.json" to { t -> de.troetenrepublik.app.engine.Ranglisten.lesen(t) != null },
        "indikatoren/rezession_nyfed.json" to { t -> de.troetenrepublik.app.engine.RezessionNyFed.lesen(t) != null },
        "indikatoren/cot_archiv.json" to { t -> de.troetenrepublik.app.engine.Positionierung.archivLesen(t).isNotEmpty() },
        "indikatoren/krypto_fng.json" to { t -> de.troetenrepublik.app.engine.KryptoArchiv.lesen(t).isNotEmpty() },
        "indikatoren/kursmuster.json" to { t -> de.troetenrepublik.app.engine.Kursmuster.lesen(t) != null },
        "indikatoren/zeichen_rueckblick.json" to { t -> de.troetenrepublik.app.engine.ZeichenRueckblick.lesen(t) != null },
        "welt/oel.json" to { t -> de.troetenrepublik.app.engine.OelWelt.lesen(t) != null },
        "welt/energie.json" to { t -> de.troetenrepublik.app.engine.EnergieWelt.lesen(t) != null },
        "welt/gold.json" to { t -> de.troetenrepublik.app.engine.GoldReserven.lesen(t) != null },
        "globus/schulden.json" to { t -> de.troetenrepublik.app.engine.Schuldenlast.lesen(t) != null },
        "indikatoren/btc_frueh.json" to { t -> de.troetenrepublik.app.engine.BtcFrueh.lesen(t) != null },
        "indikatoren/lebensrenditen.json" to { t -> de.troetenrepublik.app.engine.Lebensrenditen.lesen(t) != null },
        "globus/langreihen.json" to { t -> de.troetenrepublik.app.engine.Langreihen.lesen(t) != null },
        "etf-abstand.json" to { t -> de.troetenrepublik.app.engine.IndexAbstand.lesen(t) != null },
        "globus/leitzins.json" to { t -> de.troetenrepublik.app.engine.Leitzins.lesen(t) != null },
        "indikatoren/fed_punkte.json" to { t -> de.troetenrepublik.app.engine.FedPunkte.lesen(t) != null },
        "netz/kanten.json" to { t -> de.troetenrepublik.app.engine.Netz.lesen(t) != null },
        "netz/zulieferer.json" to { t -> de.troetenrepublik.app.engine.NetzZulieferer.lesen(t) != null },
        "indikatoren/gpr.json" to { t -> de.troetenrepublik.app.engine.Gpr.lesen(t) != null },
        "indikatoren/epu.json" to { t -> de.troetenrepublik.app.engine.Epu.lesen(t) != null },
        "welt/damodaran.json" to { t -> de.troetenrepublik.app.engine.Damodaran.lesen(t) != null },
        "globus/konflikte-reihe.json" to { t -> de.troetenrepublik.app.engine.KonfliktReihe.lesen(t) != null },
    )

    // Alle uebrigen Dateien: wenigstens ein JSON-Objekt
    private fun objekt(t: String): Boolean = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(t) is kotlinx.serialization.json.JsonObject }.getOrDefault(false)

    internal fun probeLesen(name: String, text: String): Boolean = (PRUEFER[name] ?: ::objekt)(text) && objekt(text)

    // ------------------------------------------------------------ Lesen fuer Leser

    // Neuere Fassung von eingebauter Datei und geprueftem Stand vom Geraet, sonst die eingebaute.
    fun bytes(name: String): ByteArray {
        val e = manifest?.dateien?.get(name)
        if (e != null && ordner != null && (eingebaut[name]?.fassung ?: 0) < e.fassung) {
            vomGeraet(name, e)?.let { return it }
        }
        return assetLeser(name)
    }

    fun text(name: String): String = bytes(name).decodeToString()

    // Datei vom Geraet, nur wenn Groesse und Pruefsumme zum Manifest passen (einmal je Prozess geprueft).
    private fun vomGeraet(name: String, e: Datenbuendel.Datei): ByteArray? {
        speicher[name]?.let { (sha, b) -> if (sha == e.sha256) return b }
        val o = ordner ?: return null
        val b = runCatching { File(o, "daten/$name").readBytes() }.getOrNull() ?: return null
        if (!Datenbuendel.dateiPasst(b, e, ::sha256)) return null
        speicher[name] = e.sha256 to b
        return b
    }

    // Strukturprobleme, nach dem Stand der Datei gealtert (LaenderStrukturen.gealtert); null, wenn unlesbar.
    fun strukturen(): de.troetenrepublik.app.engine.LaenderStrukturen.Stand? {
        val n = de.troetenrepublik.app.engine.LaenderStrukturen.DATEI
        return de.troetenrepublik.app.engine.LaenderStrukturen.lesen(text(n))?.let { de.troetenrepublik.app.engine.LaenderStrukturen.gealtert(it, stand(n), Uhr.jetzt()) }
    }

    // Fassung und Stand der Datei, die bytes liefert.
    private fun gewaehlt(name: String): Datenbuendel.Datei? {
        val e = manifest?.dateien?.get(name)
        val i = eingebaut[name]
        if (e != null && ordner != null && (i?.fassung ?: 0) < e.fassung && vomGeraet(name, e) != null) return e
        return i
    }

    // Tag der Daten (JJJJ-MM-TT) der gelieferten Datei; leer, wenn unbekannt.
    fun stand(name: String): String = gewaehlt(name)?.stand.orEmpty()

    fun fassung(name: String): Int = gewaehlt(name)?.fassung ?: 0

    // Alterszustand der Datei nach ihrem Profil.
    fun alter(name: String, jetzt: Long = Uhr.jetzt()): Alter.Zustand = Alter.zustand(stand(name), Alter.profilFuer(name), jetzt)

    // ------------------------------------------------------------ Start und Zwischenspeicher

    // Einmal je Prozess: geprueften Stand laden und danach hoechstens einmal am Tag das Manifest fragen.
    fun starten(datenOrdner: File, scope: CoroutineScope) {
        if (!gestartet.compareAndSet(false, true)) return
        laden(datenOrdner)
        val o = File(datenOrdner, "daten")
        val letzter = runCatching { File(o, "geprueft.txt").readText().trim().split(' ') }.getOrNull()
        var geprueft = letzter?.getOrNull(0)?.toLongOrNull() ?: 0L
        var ok = letzter?.getOrNull(1) == "1"
        scope.launch(Dispatchers.IO) {
            val api = MarketApi()
            while (isActive) {
                val jetzt = Uhr.jetzt()
                if (Aktualisierung.faellig(jetzt, geprueft, ok)) {
                    val e = runCatching { aktualisieren { url, max -> api.anfrage(url, mapOf("Accept" to "application/json, text/plain", "User-Agent" to Ersatzabrufe.KENNUNG), max = max).let { Abruf(it.code, it.text) } } }.getOrDefault("NETZ")
                    ok = e != "NETZ"
                    geprueft = jetzt
                    runCatching { o.mkdirs(); File(o, "geprueft.txt").writeText("$geprueft ${if (ok) 1 else 0}") }
                }
                delay(60_000)
            }
        }
    }

    // Geprueften Stand vom Geraet laden: Zertifikat und Manifest werden wie aus dem Netz geprueft, nur ohne Ablauf und
    // Versionsvergleich (beides galt beim Holen).
    internal fun laden(datenOrdner: File) {
        ordner = datenOrdner
        val o = File(datenOrdner, "daten")
        runCatching {
            val zb = File(o, "schluessel.json").readBytes()
            val zs = File(o, "schluessel.json.sig").readText()
            val z = (Datenbuendel.zertifikatPruefen(zb, zs, haupt(), 0, Uhr.heute().toString(), ::sha512, ablaufPruefen = false) as? Ergebnis.Ok)?.wert ?: return@runCatching
            zertifikat = z
            val mb = File(o, "manifest.json").readBytes()
            val ms = File(o, "manifest.json.sig").readText()
            val m = Datenbuendel.manifestGeprueft(mb, ms, z, ::sha512) ?: return@runCatching
            manifest = m
            _zustand.value = _zustand.value.copy(manifestVersion = m.version, manifestStand = m.erzeugt, zertifikatBis = z.gueltigBis, abgelaufen = z.abgelaufen(Uhr.heute().toString()))
        }
    }

    // Nur fuer Tests: alles zuruecksetzen.
    internal fun fuerTest(datenOrdner: File?) {
        ordner = datenOrdner
        zertifikat = null
        manifest = null
        eingebautMerk = null
        speicher.clear()
        _zustand.value = Zustand()
        if (datenOrdner != null) laden(datenOrdner)
    }

    // ------------------------------------------------------------ Abruf

    // Fragt die Orte der Reihe nach: Zertifikat (nur mit hoeherer Version), Manifest, fehlende Dateien. Liefert den Ausgang:
    // NEU (etwas Neues uebernommen), AKTUELL, NETZ (nichts erreichbar), SIGNATUR, FORM, ABGELAUFEN, OHNE_ZERTIFIKAT.
    suspend fun aktualisieren(holen: suspend (String, Int) -> Abruf): String {
        val heute = Uhr.heute().toString()
        var ausgang = "NETZ"
        for (basis in Datenbuendel.BASEN) {
            // Zertifikat: das gespeicherte bleibt, solange kein neueres mit gueltiger Signatur kommt
            val zb = holen(basis + "schluessel.json", Datenbuendel.MAX_ZERTIFIKAT)
            val zs = if (zb.text != null && zb.code in 200..299) holen(basis + "schluessel.json.sig", 2_000) else null
            if (zb.code == 0 || zs == null || zs.code !in 200..299) { if (zb.code != 0 && ausgang == "NETZ") ausgang = "FORM"; continue }
            val zbytes = zb.text.orEmpty().toByteArray(Charsets.UTF_8)
            val aktiv = zertifikat
            when (val z = Datenbuendel.zertifikatPruefen(zbytes, zs.text.orEmpty(), haupt(), aktiv?.version ?: 0, heute, ::sha512)) {
                is Ergebnis.Ok -> {
                    zertifikat = z.wert
                    speichern("schluessel.json", zbytes)
                    speichern("schluessel.json.sig", zs.text.orEmpty().toByteArray())
                }
                is Ergebnis.Nein -> when (z.grund) {
                    Datenbuendel.Grund.VERSION -> Unit
                    Datenbuendel.Grund.ABGELAUFEN -> { ausgang = "ABGELAUFEN" }
                    else -> { ausgang = z.grund.name; continue }
                }
            }
            val zert = zertifikat ?: continue
            _zustand.value = _zustand.value.copy(zertifikatBis = zert.gueltigBis, abgelaufen = zert.abgelaufen(heute))
            if (zert.abgelaufen(heute)) { ausgang = "ABGELAUFEN"; break }

            // Manifest
            val mb = holen(basis + "manifest.json", Datenbuendel.MAX_MANIFEST)
            val ms = if (mb.text != null && mb.code in 200..299) holen(basis + "manifest.json.sig", 2_000) else null
            if (mb.code == 0 || ms == null || ms.code !in 200..299) { if (mb.code != 0 && ausgang == "NETZ") ausgang = "FORM"; continue }
            val mbytes = mb.text.orEmpty().toByteArray(Charsets.UTF_8)
            var neu = false
            when (val m = Datenbuendel.manifestPruefen(mbytes, ms.text.orEmpty(), zert, manifest?.version ?: 0, heute, ::sha512)) {
                is Ergebnis.Ok -> {
                    manifest = m.wert
                    speichern("manifest.json", mbytes)
                    speichern("manifest.json.sig", ms.text.orEmpty().toByteArray())
                    neu = true
                    _zustand.value = _zustand.value.copy(manifestVersion = m.wert.version, manifestStand = m.wert.erzeugt)
                }
                is Ergebnis.Nein -> when (m.grund) {
                    Datenbuendel.Grund.VERSION -> Unit
                    Datenbuendel.Grund.ABGELAUFEN -> { ausgang = "ABGELAUFEN"; break }
                    else -> { ausgang = m.grund.name; continue }
                }
            }
            val geholt = dateienHolen(basis, holen)
            ausgang = if (neu || geholt > 0) "NEU" else "AKTUELL"
            break
        }
        _zustand.value = _zustand.value.copy(geprueft = Uhr.jetzt(), ergebnis = ausgang)
        // Fuer die Seite Datenquellen: lesbar angekommen oder nicht
        if (ausgang == "NEU" || ausgang == "AKTUELL") Quellen.inhaltGelesen("datenbuendel", true)
        else if (ausgang == "SIGNATUR" || ausgang == "FORM") Quellen.inhaltGelesen("datenbuendel", false)
        return ausgang
    }

    // Holt jede Datei des Manifests, die neuer ist als die eingebaute und noch nicht (richtig) auf dem Geraet liegt.
    // Zaehlt die uebernommenen Dateien. Eine misslungene Datei bleibt ausser Betracht, der Rest laeuft weiter.
    private suspend fun dateienHolen(basis: String, holen: suspend (String, Int) -> Abruf): Int {
        val m = manifest ?: return 0
        var summe = 0L
        var anzahl = 0
        for ((name, d) in m.dateien) {
            if (!Datenbuendel.holen(name, d, eingebaut[name], null, Aktualisierung.FASSUNG)) continue
            if (vomGeraet(name, d) != null) continue
            if (summe + d.bytes > Datenbuendel.MAX_GESAMT) break
            val a = holen(basis + name, d.bytes.toInt() + 1)
            if (a.code !in 200..299 || a.text == null) continue
            val bytes = a.text.toByteArray(Charsets.UTF_8)
            if (!Datenbuendel.dateiPasst(bytes, d, ::sha256)) continue
            if (!probeLesen(name, a.text)) continue
            summe += d.bytes
            speicher.remove(name)
            if (speichern("daten/$name", bytes)) anzahl++
        }
        return anzahl
    }

    private fun speichern(name: String, bytes: ByteArray): Boolean {
        val o = ordner ?: return false
        val ziel = File(o, if (name.startsWith("daten/")) name else "daten/$name")
        return runCatching { ziel.parentFile?.mkdirs(); SichereDatei(ziel).schreiben(bytes) }.isSuccess
    }
}
