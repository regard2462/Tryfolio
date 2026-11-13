package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

// Datenbuendel, Rechenteil: gepflegte Dateien (Durchbrueche, Strukturen, Laenderdaten, Katalog ...) kommen aus einem
// signierten Manifest im Projektarchiv (daten/manifest.json), das je Datei Fassung, SHA-256, Groesse, Stand, Schema und
// Mindest-App-Fassung nennt. Signaturkette: Hauptschluessel (oder Ersatz) signiert das Zertifikat des Datenschluessels
// (daten/schluessel.json: Schluessel, gueltig bis, Version), der Datenschluessel signiert das Manifest. Der Datenschluessel
// signiert nie das Quellen-Buendel (Adressen). Eine Datei aus der Ferne wird nur genommen, wenn Kette, Pruefsumme, Schema und
// Mindestfassung stimmen und sie neuer ist als die eingebaute. Ein neues Zertifikat mit hoeherer Version ruft den
// bisherigen Datenschluessel zurueck.
object Datenbuendel {

    // Wo das Archiv liegt (Ordner mit manifest.json, schluessel.json und den Dateien): wie das Quellen-Buendel im App-Repo auf
    // main, ueber jsDelivr und als dritter, vorbereiteter Ort auf GitHub Pages. Alle Hosts stehen in Quellenbuendel.PROJEKT_HOSTS.
    val BASEN = listOf(
        "https://raw.githubusercontent.com/regard2462/Tryfolio/main/daten/",
        "https://cdn.jsdelivr.net/gh/regard2462/Tryfolio@main/daten/",
        "https://regard2462.github.io/Tryfolio/daten/",
    )

    const val MAX_DATEI = 8_000_000
    const val MAX_GESAMT = 64_000_000L
    const val MAX_MANIFEST = 64_000
    const val MAX_ZERTIFIKAT = 4_000

    // Dateien, die ein Manifest liefern darf, mit dem hoechsten Schema, das diese Fassung der App lesen kann. Unbekannte
    // Dateien eines Manifests (aus einer spaeteren Fassung) werden uebergangen.
    val SCHEMA: Map<String, Int> = mapOf(
        "durchbrueche/inhalt.json" to 1,
        "globus/strukturen.json" to 1,
        "globus/laenderdaten.json" to 1,
        "globus/etf-laender.json" to 1,
        "globus/sanktionen.json" to 1,
        "globus/konflikte.json" to 1,
        "globus/firmen.json" to 1,
        "globus/firmen-privat.json" to 1,
        "globus/handel.json" to 1,
        "globus/ersatz.json" to 1,
        "globus/steckbrief.json" to 1,
        "globus/taetigkeit.json" to 1,
        "globus/wiki-wirtschaft.json" to 1,
        "katalog.json" to 1,
        "copytrading/investoren.json" to 1,
        "copytrading/personen.json" to 1,
        "copytrading/amt.json" to 1,
        "steuerwerte.json" to 1,
        "welt/einfluss.json" to 1,
        "welt/rohstoffe.json" to 1,
        "welt/anlagen.json" to 1,
        "indikatoren/warnzeichen_rueckblick.json" to 1,
        "copytrading/cluster.json" to 1,
        "etf-rueckwaerts.json" to 1,
        "analysten/haeuser.json" to 1,
        "copytrading/gesamt13f.json" to 1,
        "firmen/emissionen.json" to 1,
        "firmen/emissionen-eu.json" to 1,
        "firmen/staatsgeld-eu.json" to 1,
        "copytrading/sec_historie.json" to 1,
        "globus/jst.json" to 1,
        "globus/hauspreise.json" to 1,
        "globus/waehrung.json" to 1,
        "globus/bigmac.json" to 1,
        "globus/ranglisten.json" to 1,
        "indikatoren/rezession_nyfed.json" to 1,
        "indikatoren/cot_archiv.json" to 1,
        "indikatoren/krypto_fng.json" to 1,
        "indikatoren/kursmuster.json" to 1,
        "indikatoren/zeichen_rueckblick.json" to 1,
        "welt/oel.json" to 1,
        "welt/energie.json" to 1,
        "welt/gold.json" to 1,
        "globus/schulden.json" to 1,
        "indikatoren/lange_reihen.json" to 1,
        "indikatoren/btc_frueh.json" to 1,
        "indikatoren/lebensrenditen.json" to 1,
        "globus/langreihen.json" to 1,
        "etf-abstand.json" to 1,
        "globus/leitzins.json" to 1,
        "indikatoren/fed_punkte.json" to 1,
        "netz/kanten.json" to 1,
        "netz/zulieferer.json" to 1,
        "indikatoren/gpr.json" to 1,
        "indikatoren/epu.json" to 1,
        "welt/damodaran.json" to 1,
        "globus/konflikte-reihe.json" to 1,
    )

    class Zertifikat(val version: Long, val schluessel: ByteArray, val ausgestellt: String, val gueltigBis: String) {
        // Heute (JJJJ-MM-TT) nach dem Ablauf?
        fun abgelaufen(heute: String): Boolean = heute > gueltigBis
    }

    class Datei(val fassung: Int, val sha256: String, val bytes: Long, val stand: String, val schema: Int, val minApp: String)

    class Manifest(val version: Long, val erzeugt: String, val dateien: Map<String, Datei>)

    enum class Grund { SIGNATUR, FORM, ABGELAUFEN, VERSION }

    sealed class Ergebnis<out T> {
        class Ok<T>(val wert: T) : Ergebnis<T>()
        class Nein(val grund: Grund) : Ergebnis<Nothing>()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val TAG = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val NAME = Regex("^[a-z0-9][a-z0-9_./-]{0,120}\\.json$")

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.zahl(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    // Name sicher (kein Ruecksprung, kein absoluter Pfad)?
    fun nameSicher(n: String): Boolean = NAME.matches(n) && !n.contains("..") && !n.contains("//")

    fun zertifikatLesen(text: String): Zertifikat? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        if (o.zahl("zertifikat") != 1L) return null
        val version = o.zahl("version")?.takeIf { it > 0 } ?: return null
        val schluessel = o.text("schluessel")?.takeIf { HEX64.matches(it) } ?: return null
        val bis = o.text("gueltigBis")?.takeIf { TAG.matches(it) } ?: return null
        Zertifikat(version, Quellenbuendel.hex(schluessel), o.text("ausgestellt").orEmpty(), bis)
    }.getOrNull()

    // Zertifikat des Datenschluessels: Signatur mit Haupt- oder Ersatzschluessel, Form, hoehere Version als die aktive
    // (aktiveVersion 0: beliebig, etwa beim Laden aus dem Zwischenspeicher) und, mit ablaufPruefen, noch nicht abgelaufen.
    fun zertifikatPruefen(
        bytes: ByteArray, signatur: String, haupt: List<ByteArray>, aktiveVersion: Long, heute: String,
        sha512: (ByteArray) -> ByteArray, ablaufPruefen: Boolean = true,
    ): Ergebnis<Zertifikat> {
        if (bytes.size > MAX_ZERTIFIKAT) return Ergebnis.Nein(Grund.FORM)
        if (!Quellenbuendel.signaturGueltig(bytes, signatur, haupt, sha512)) return Ergebnis.Nein(Grund.SIGNATUR)
        val z = zertifikatLesen(bytes.toString(Charsets.UTF_8)) ?: return Ergebnis.Nein(Grund.FORM)
        if (z.version <= aktiveVersion) return Ergebnis.Nein(Grund.VERSION)
        if (ablaufPruefen && z.abgelaufen(heute)) return Ergebnis.Nein(Grund.ABGELAUFEN)
        return Ergebnis.Ok(z)
    }

    // Manifest aus dem Text, ohne Signatur (der eingebaute Stand liegt im Programm). Unbekannte Felder zaehlen nicht,
    // Dateien mit unsicherem Namen oder falschen Angaben fehlen im Ergebnis; null bei falscher Grundform.
    fun manifestLesen(text: String): Manifest? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        if (o.zahl("manifest") != 1L) return null
        val version = o.zahl("version")?.takeIf { it > 0 } ?: return null
        val erzeugt = o.text("erzeugt")?.takeIf { TAG.matches(it) } ?: return null
        val dateien = (o["dateien"] as? JsonObject ?: return null).mapNotNull { (name, e) ->
            val d = e as? JsonObject ?: return@mapNotNull null
            if (!nameSicher(name)) return@mapNotNull null
            val fassung = d.zahl("fassung")?.takeIf { it in 1..1_000_000 }?.toInt() ?: return@mapNotNull null
            val sha = d.text("sha256")?.takeIf { HEX64.matches(it) } ?: return@mapNotNull null
            val bytes = d.zahl("bytes")?.takeIf { it in 1..MAX_DATEI } ?: return@mapNotNull null
            val stand = d.text("stand")?.takeIf { TAG.matches(it) } ?: return@mapNotNull null
            val schema = d.zahl("schema")?.takeIf { it >= 1 }?.toInt() ?: return@mapNotNull null
            name to Datei(fassung, sha, bytes, stand, schema, d.text("minApp").orEmpty().take(20))
        }.toMap()
        Manifest(version, erzeugt, dateien)
    }.getOrNull()

    // Manifest aus der Ferne: Signatur mit dem Schluessel des Zertifikats, Form, hoehere Version als die aktive, erzeugt nicht
    // nach Ablauf des Zertifikats und, mit ablaufPruefen, Zertifikat heute noch gueltig.
    fun manifestPruefen(
        bytes: ByteArray, signatur: String, zert: Zertifikat, aktiveVersion: Long, heute: String,
        sha512: (ByteArray) -> ByteArray, ablaufPruefen: Boolean = true,
    ): Ergebnis<Manifest> {
        if (bytes.size > MAX_MANIFEST) return Ergebnis.Nein(Grund.FORM)
        if (!Quellenbuendel.signaturGueltig(bytes, signatur, listOf(zert.schluessel), sha512)) return Ergebnis.Nein(Grund.SIGNATUR)
        val m = manifestLesen(bytes.toString(Charsets.UTF_8)) ?: return Ergebnis.Nein(Grund.FORM)
        if (m.erzeugt > zert.gueltigBis) return Ergebnis.Nein(Grund.ABGELAUFEN)
        if (ablaufPruefen && zert.abgelaufen(heute)) return Ergebnis.Nein(Grund.ABGELAUFEN)
        if (m.version <= aktiveVersion) return Ergebnis.Nein(Grund.VERSION)
        return Ergebnis.Ok(m)
    }

    // Gleiche Pruefung ohne Versions- und Ablaufvergleich, fuer das Laden eines frueher geprueften Standes vom Geraet.
    fun manifestGeprueft(bytes: ByteArray, signatur: String, zert: Zertifikat, sha512: (ByteArray) -> ByteArray): Manifest? {
        if (!Quellenbuendel.signaturGueltig(bytes, signatur, listOf(zert.schluessel), sha512)) return null
        return manifestLesen(bytes.toString(Charsets.UTF_8))
    }

    // Soll die Datei aus der Ferne geholt werden? Nur bekannte Dateien, Schema und Mindestfassung muessen passen, und die
    // Fassung muss hoeher sein als die eingebaute und die schon vorhandene.
    fun holen(name: String, d: Datei, eingebaut: Datei?, vorhanden: Datei?, appFassung: String): Boolean {
        val schema = SCHEMA[name] ?: return false
        if (d.schema > schema) return false
        if (d.minApp.isNotEmpty() && de.troetenrepublik.app.market.Aktualisierung.neuer(d.minApp, appFassung)) return false
        return d.fassung > maxOf(eingebaut?.fassung ?: 0, vorhanden?.fassung ?: 0)
    }

    // Stimmen Groesse und Pruefsumme?
    fun dateiPasst(bytes: ByteArray, d: Datei, sha256: (ByteArray) -> ByteArray): Boolean =
        bytes.size.toLong() == d.bytes && bytes.size <= MAX_DATEI && hexVon(sha256(bytes)) == d.sha256

    fun hexVon(b: ByteArray): String {
        val s = StringBuilder(b.size * 2)
        for (x in b) { val v = x.toInt() and 0xff; s.append("0123456789abcdef"[v shr 4]); s.append("0123456789abcdef"[v and 15]) }
        return s.toString()
    }
}
