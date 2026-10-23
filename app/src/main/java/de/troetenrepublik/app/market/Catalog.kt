package de.troetenrepublik.app.market

import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Instrument
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Katalog fuer die Entdecken-Seiten der Suche: Aktien grosser Indizes, ETFs, Basiswerte fuer Derivate und Boersengaenge.
// Liegt als assets/katalog.json bei, Stand im Feld Data.stand. Kurse kommen live von Yahoo.
// Beim Laden werden die Werte bei Symbols angemeldet, damit Suche, Namen und Gattung stimmen.
object Catalog {

    @Serializable
    data class Stock(
        val s: String,
        val n: String,
        val c: String = "",
        val i: String = "",
        val x: List<String> = emptyList(),
        val w: String = "",
    )

    @Serializable
    data class Etf(
        val s: String,
        val n: String,
        val a: String = "",
        val isin: String = "",
        val i: String = "",
        val r: String = "",
        val x: String? = null,
        val ter: Double? = null,
        val d: String = "",
    )

    // Basiswert fuer Derivate, der keine Aktie ist.
    @Serializable
    data class Underlying(
        val s: String,
        @SerialName("n_de") val nameDe: String,
        @SerialName("n_en") val nameEn: String,
        val k: String,
        val r: String = "",
        val c: String = "",
        val w: String = "",
    )

    @Serializable
    data class IpoUpcoming(
        val name: String,
        @SerialName("text_de") val textDe: String = "",
        @SerialName("text_en") val textEn: String = "",
        @SerialName("zeitraum_de") val periodDe: String = "",
        @SerialName("zeitraum_en") val periodEn: String = "",
        val quelle: String = "",
    )

    @Serializable
    data class IpoDone(
        val s: String,
        val name: String,
        val boerse: String = "",
        val datum: String = "",
        val preis: Double? = null,
        val waehrung: String = "",
        @SerialName("text_de") val textDe: String = "",
        @SerialName("text_en") val textEn: String = "",
        val i: String = "",
        val c: String = "",
        val quelle: String = "",
    )

    @Serializable
    data class Ipos(
        val stand: String = "",
        val bevorstehend: List<IpoUpcoming> = emptyList(),
        val abgeschlossen: List<IpoDone> = emptyList(),
    )

    @Serializable
    data class Data(
        val stand: String = "",
        val aktien: List<Stock> = emptyList(),
        val etfs: List<Etf> = emptyList(),
        val basiswerte: List<Underlying> = emptyList(),
        val ipos: Ipos = Ipos(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var data: Data? = null

    // Beim ersten Aufruf geladen, danach im Speicher gehalten.
    fun get(): Data = data ?: synchronized(this) {
        data ?: runCatching {
            json.decodeFromString<Data>(Plattform.asset("katalog.json").decodeToString())
        }.getOrDefault(Data()).also { d ->
            data = d
            register(d)
        }
    }

    // Fuer Tests.
    internal fun set(d: Data) {
        data = d
        register(d)
    }

    fun loaded(): Data? = data

    private fun register(d: Data) {
        val list = ArrayList<Instrument>(d.aktien.size + d.etfs.size + d.basiswerte.size)
        d.aktien.forEach { list += Instrument(it.s, it.n, AssetType.STOCK, currencyOf(it.w)) }
        d.etfs.forEach { list += Instrument(it.s, it.n, AssetType.ETF, "EUR", it.isin) }
        d.basiswerte.forEach { u -> list += Instrument(u.s, u.nameDe, typeOf(u.k), currencyOf(u.w)) }
        d.ipos.abgeschlossen.forEach { list += Instrument(it.s, it.name, AssetType.STOCK, currencyOf(it.waehrung)) }
        Symbols.register(list)
    }

    private fun currencyOf(w: String): String = when (w) {
        "" -> "EUR"
        "GBp", "GBX" -> "GBP"
        else -> w
    }

    fun typeOf(k: String): AssetType = when (k) {
        "INDEX" -> AssetType.INDEX
        "ROHSTOFF" -> AssetType.COMMODITY
        "WAEHRUNG" -> AssetType.CURRENCY
        "KRYPTO" -> AssetType.CRYPTO
        else -> AssetType.STOCK
    }

    // Schluessel wie in Industry, sonst null.
    fun industryOf(symbol: String): String? {
        val d = data ?: return null
        return d.aktien.firstOrNull { it.s == symbol }?.i ?: d.etfs.firstOrNull { it.s == symbol }?.i
    }

    fun indexName(code: String): String = INDEX_NAMES[code] ?: code

    private val INDEX_NAMES = mapOf(
        "SP500" to "S&P 500", "NDX" to "Nasdaq 100", "DJIA" to "Dow Jones", "RUSSELL2000" to "Russell 2000",
        "DAX" to "DAX", "MDAX" to "MDAX", "SDAX" to "SDAX", "TECDAX" to "TecDAX", "ESTX50" to "Euro Stoxx 50",
        "STOXX600" to "Stoxx Europe 600", "FTSE100" to "FTSE 100", "CAC40" to "CAC 40", "SMI" to "SMI", "AEX" to "AEX",
        "IBEX35" to "IBEX 35", "FTSEMIB" to "FTSE MIB", "ATX" to "ATX", "OMXS30" to "OMX Stockholm 30",
        "OMXC25" to "OMX Copenhagen 25", "OBX" to "OBX", "OMXH25" to "OMX Helsinki 25", "NIKKEI225" to "Nikkei 225",
        "MSCIWORLD" to "MSCI World", "FTSEALLWORLD" to "FTSE All-World", "MSCIACWI" to "MSCI ACWI",
        "MSCIEM" to "MSCI Emerging Markets", "MSCIEUROPE" to "MSCI Europe",
    )

    // Fuer das Flaggenzeichen im Filter, leer steht fuer Welt.
    fun indexCountry(code: String): String = when (code) {
        "SP500", "NDX", "DJIA", "RUSSELL2000" -> "US"
        "DAX", "MDAX", "SDAX", "TECDAX" -> "DE"
        "ESTX50", "STOXX600", "MSCIEUROPE" -> "EU"
        "FTSE100" -> "GB"; "CAC40" -> "FR"; "SMI" -> "CH"; "AEX" -> "NL"; "IBEX35" -> "ES"; "FTSEMIB" -> "IT"; "ATX" -> "AT"
        "OMXS30" -> "SE"; "OMXC25" -> "DK"; "OBX" -> "NO"; "OMXH25" -> "FI"; "NIKKEI225" -> "JP"
        else -> ""
    }
}

// Branchen als feste Schluessel, die Texte stehen in der Sprachschicht.
object Industry {
    val STOCK_KEYS = listOf(
        "ALKOHOL_TABAK", "BANKEN", "BAU", "BERGBAU", "HOLDINGS", "BIOTECH", "CHEMIE", "COMPUTER", "DIENSTLEISTUNGEN",
        "ELEKTRO", "HALBLEITER", "VERSORGER", "FAHRZEUGE", "LUFTFAHRT", "FORST", "GASTGEWERBE", "GESUNDHEIT", "HANDEL",
        "IMMOBILIEN", "SOFTWARE", "EINZELHANDEL", "KONSUM", "MASCHINEN", "NAHRUNG", "PHARMA", "MEDIEN", "ROHSTOFFE",
        "TELEKOM", "TEXTIL", "UMWELT", "VERKEHR", "VERSICHERUNG", "OEL_GAS",
    )
    val ETF_KEYS = listOf("SEKTORUEBERGREIFEND", "LARGE_CAPS", "MID_CAPS", "SMALL_CAPS", "UNTERNEHMENSANLEIHEN", "STAATSANLEIHEN", "GELDMARKT")
    val ALL = STOCK_KEYS + ETF_KEYS
}

object Region {
    val ALL = listOf("ASIEN_PAZIFIK", "DEUTSCHLAND", "EUROPA", "GLOBAL", "NORDAMERIKA", "SCHWELLENLAENDER")

    fun ofCountry(c: String): String = when (c) {
        "DE" -> "DEUTSCHLAND"
        "US", "CA", "BM", "KY" -> "NORDAMERIKA"
        "JP", "AU", "NZ", "HK", "SG", "KR", "TW" -> "ASIEN_PAZIFIK"
        "CN", "IN", "BR", "MX", "ZA", "SA", "AE", "TR", "ID", "TH", "MY", "PH", "CL", "PE", "CO", "AR" -> "SCHWELLENLAENDER"
        "" -> "GLOBAL"
        else -> "EUROPA"
    }
}
