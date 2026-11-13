package de.troetenrepublik.app.engine

import java.io.File

// Hilfen der Tests zu den Verflechtungen: kleine Netze von Hand bauen, das Buendel aus den Assets lesen.
internal object NetzTestHilfe {

    fun asset(name: String): File = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    val buendel: Netz.Ergebnis by lazy { Netz.lesen(asset(Netz.DATEI).readText(Charsets.UTF_8))!! }

    fun knoten(id: String, typ: Netz.Typ = Netz.Typ.FIRMA, name: String = id, symbole: List<String> = emptyList()) =
        Netz.Knoten(id, typ, name, symbole = symbole)

    var zaehler = 0

    fun kante(
        von: String, art: Netz.Art, nach: String, umfang: Netz.Umfang? = null, status: Netz.Status = Netz.Status.DEFINITIV,
        guete: Netz.Guete = Netz.Guete.A, bedingt: Boolean = false, teil: String? = null, schluessel: Boolean = false, ablauf: String? = null,
        rolle: String? = null, herkunft: Netz.Herkunft = Netz.Herkunft.GEPFLEGT,
    ) = Netz.Kante(
        "t${zaehler++}", von, nach, art, umfang, null, null, status, guete, Netz.Quelle("Quelle", "https://example.org/q", true), emptyList(), "2026-11-13",
        ablauf, null, null, bedingt, teil, schluessel, herkunft, rolle, null, null,
    )

    fun usd(mrd: Double, hoechstens: Boolean = false) = Netz.Umfang(mrd * 1e9, Netz.Einheit.USD, null, hoechstens)
    fun gw(x: Double) = Netz.Umfang(x, Netz.Einheit.GW)
    fun pct(x: Double, bezug: Netz.Bezug) = Netz.Umfang(x, Netz.Einheit.PCT, bezug)

    // Netz aus Knoten-Kennungen und Kanten; Typ je Kennung ueber typen (sonst Firma)
    fun netz(vararg kanten: Netz.Kante, typen: Map<String, Netz.Typ> = emptyMap()): Netz.Graph {
        val ids = kanten.flatMap { listOf(it.von, it.nach) }.distinct()
        return Netz.Graph(ids.associateWith { knoten(it, typen[it] ?: Netz.Typ.FIRMA, it, listOf(it)) }, kanten.toList())
    }

    const val HEUTE = "2026-11-13"
}
