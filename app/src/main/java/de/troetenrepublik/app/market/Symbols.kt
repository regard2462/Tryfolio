package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Instrument

// Die Symbole folgen der Schreibweise von Yahoo Finance:
// Xetra-Werte enden auf .DE, Kryptowerte auf -EUR, US-Werte stehen ohne Zusatz.
// Branchen stehen als englische Schluessel in der Schreibweise von Yahoo und werden uebersetzt.
object Symbols {

    val dax = listOf(
        Instrument("SAP.DE", "SAP", AssetType.STOCK, "EUR", "DE0007164600", "Software - Application", "sap.com"),
        Instrument("SIE.DE", "Siemens", AssetType.STOCK, "EUR", "DE0007236101", "Specialty Industrial Machinery", "siemens.com"),
        Instrument("ALV.DE", "Allianz", AssetType.STOCK, "EUR", "DE0008404005", "Insurance - Diversified", "allianz.com"),
        Instrument("DTE.DE", "Deutsche Telekom", AssetType.STOCK, "EUR", "DE0005557508", "Telecom Services", "telekom.com"),
        Instrument("AIR.DE", "Airbus", AssetType.STOCK, "EUR", "NL0000235190", "Aerospace & Defense", "airbus.com"),
        Instrument("MBG.DE", "Mercedes-Benz Group", AssetType.STOCK, "EUR", "DE0007100000", "Auto Manufacturers", "mercedes-benz.com"),
        Instrument("BMW.DE", "BMW", AssetType.STOCK, "EUR", "DE0005190003", "Auto Manufacturers", "bmw.com"),
        Instrument("VOW3.DE", "Volkswagen Vz.", AssetType.STOCK, "EUR", "DE0007664039", "Auto Manufacturers", "volkswagen.de"),
        Instrument("BAS.DE", "BASF", AssetType.STOCK, "EUR", "DE000BASF111", "Chemicals", "basf.com"),
        Instrument("BAYN.DE", "Bayer", AssetType.STOCK, "EUR", "DE000BAY0017", "Drug Manufacturers - General", "bayer.com"),
        Instrument("DBK.DE", "Deutsche Bank", AssetType.STOCK, "EUR", "DE0005140008", "Banks - Regional", "db.com"),
        Instrument("RHM.DE", "Rheinmetall", AssetType.STOCK, "EUR", "DE0007030009", "Aerospace & Defense", "rheinmetall.com"),
        Instrument("IFX.DE", "Infineon", AssetType.STOCK, "EUR", "DE0006231004", "Semiconductors", "infineon.com"),
        Instrument("ADS.DE", "Adidas", AssetType.STOCK, "EUR", "DE000A1EWWW0", "Footwear & Accessories", "adidas.com"),
        Instrument("MRK.DE", "Merck", AssetType.STOCK, "EUR", "DE0006599905", "Drug Manufacturers - General", "merckgroup.com"),
        Instrument("DHL.DE", "DHL Group", AssetType.STOCK, "EUR", "DE0005552004", "Integrated Freight & Logistics", "dhl.com"),
        Instrument("MUV2.DE", "Münchener Rück", AssetType.STOCK, "EUR", "DE0008430026", "Insurance - Reinsurance", "munichre.com"),
        Instrument("HEN3.DE", "Henkel Vz.", AssetType.STOCK, "EUR", "DE0006048432", "Household & Personal Products", "henkel.com"),
        Instrument("EOAN.DE", "E.ON", AssetType.STOCK, "EUR", "DE000ENAG999", "Utilities - Diversified", "eon.com"),
        Instrument("RWE.DE", "RWE", AssetType.STOCK, "EUR", "DE0007037129", "Utilities - Renewable", "rwe.com"),
        Instrument("ZAL.DE", "Zalando", AssetType.STOCK, "EUR", "DE000ZAL1111", "Internet Retail", "zalando.com"),
        Instrument("P911.DE", "Porsche AG", AssetType.STOCK, "EUR", "DE000PAG9113", "Auto Manufacturers", "porsche.com"),
    )

    val usa = listOf(
        Instrument("AAPL", "Apple", AssetType.STOCK, "USD", "US0378331005", "Consumer Electronics", "apple.com"),
        Instrument("MSFT", "Microsoft", AssetType.STOCK, "USD", "US5949181045", "Software - Infrastructure", "microsoft.com"),
        Instrument("NVDA", "NVIDIA", AssetType.STOCK, "USD", "US67066G1040", "Semiconductors", "nvidia.com"),
        Instrument("AMZN", "Amazon", AssetType.STOCK, "USD", "US0231351067", "Internet Retail", "amazon.com"),
        Instrument("GOOGL", "Alphabet", AssetType.STOCK, "USD", "US02079K3059", "Internet Content & Information", "abc.xyz"),
        Instrument("META", "Meta Platforms", AssetType.STOCK, "USD", "US30303M1027", "Internet Content & Information", "meta.com"),
        Instrument("TSLA", "Tesla", AssetType.STOCK, "USD", "US88160R1014", "Auto Manufacturers", "tesla.com"),
        Instrument("AMD", "AMD", AssetType.STOCK, "USD", "US0079031078", "Semiconductors", "amd.com"),
        Instrument("NFLX", "Netflix", AssetType.STOCK, "USD", "US64110L1061", "Entertainment", "netflix.com"),
        Instrument("PLTR", "Palantir", AssetType.STOCK, "USD", "US69608A1088", "Software - Infrastructure", "palantir.com"),
        Instrument("COIN", "Coinbase", AssetType.STOCK, "USD", "US19260Q1076", "Financial Data & Stock Exchanges", "coinbase.com"),
        Instrument("JPM", "JPMorgan Chase", AssetType.STOCK, "USD", "US46625H1005", "Banks - Diversified", "jpmorganchase.com"),
        Instrument("V", "Visa", AssetType.STOCK, "USD", "US92826C8394", "Credit Services", "visa.com"),
        Instrument("KO", "Coca-Cola", AssetType.STOCK, "USD", "US1912161007", "Beverages - Non-Alcoholic", "coca-colacompany.com"),
        Instrument("MCD", "McDonald's", AssetType.STOCK, "USD", "US5801351017", "Restaurants", "mcdonalds.com"),
        Instrument("DIS", "Walt Disney", AssetType.STOCK, "USD", "US2546871060", "Entertainment", "thewaltdisneycompany.com"),
        Instrument("BA", "Boeing", AssetType.STOCK, "USD", "US0970231058", "Aerospace & Defense", "boeing.com"),
        Instrument("XOM", "Exxon Mobil", AssetType.STOCK, "USD", "US30231G1022", "Oil & Gas Integrated", "exxonmobil.com"),
        Instrument("LLY", "Eli Lilly", AssetType.STOCK, "USD", "US5324571083", "Drug Manufacturers - General", "lilly.com"),
        Instrument("AVGO", "Broadcom", AssetType.STOCK, "USD", "US11135F1012", "Semiconductors", "broadcom.com"),
    )

    val etfs = listOf(
        Instrument("EUNL.DE", "iShares Core MSCI World", AssetType.ETF, "EUR", "IE00B4L5Y983", "World", "ishares.com"),
        Instrument("VWCE.DE", "Vanguard FTSE All-World", AssetType.ETF, "EUR", "IE00BK5BQT80", "World", "vanguard.de"),
        Instrument("IS3N.DE", "iShares Core MSCI EM IMI", AssetType.ETF, "EUR", "IE00BKM4GZ66", "Emerging Markets", "ishares.com"),
        Instrument("SXR8.DE", "iShares Core S&P 500", AssetType.ETF, "EUR", "IE00B5BMR087", "USA", "ishares.com"),
        Instrument("EXS1.DE", "iShares Core DAX", AssetType.ETF, "EUR", "DE0005933931", "Germany", "ishares.com"),
        Instrument("XAIX.DE", "Xtrackers AI & Big Data", AssetType.ETF, "EUR", "IE00BGV5VN51", "Technology", "xtrackers.com"),
        Instrument("IQQH.DE", "iShares Global Clean Energy", AssetType.ETF, "EUR", "IE00B1XNHC34", "Clean Energy", "ishares.com"),
        Instrument("4GLD.DE", "Xetra-Gold", AssetType.ETF, "EUR", "DE000A0S9GB0", "Gold", "deutsche-boerse.com"),
    )

    val crypto = listOf(
        Instrument("BTC-EUR", "Bitcoin", AssetType.CRYPTO, "EUR", "", "Crypto"),
        Instrument("ETH-EUR", "Ethereum", AssetType.CRYPTO, "EUR", "", "Crypto"),
        Instrument("SOL-EUR", "Solana", AssetType.CRYPTO, "EUR", "", "Crypto"),
        Instrument("XRP-EUR", "XRP", AssetType.CRYPTO, "EUR", "", "Crypto"),
        Instrument("ADA-EUR", "Cardano", AssetType.CRYPTO, "EUR", "", "Crypto"),
        Instrument("DOGE-EUR", "Dogecoin", AssetType.CRYPTO, "EUR", "", "Crypto"),
    )

    val indices = listOf(
        Instrument("^GDAXI", "DAX", AssetType.INDEX, "EUR", "", "Index"),
        Instrument("^GSPC", "S&P 500", AssetType.INDEX, "USD", "", "Index"),
        Instrument("^NDX", "Nasdaq 100", AssetType.INDEX, "USD", "", "Index"),
        Instrument("^STOXX50E", "Euro Stoxx 50", AssetType.INDEX, "EUR", "", "Index"),
    )

    val all: List<Instrument> = dax + usa + etfs + crypto + indices

    // Kuerzel fuer die Krypto-Symbolgrafiken.
    fun cryptoCode(symbol: String): String = symbol.substringBefore('-').lowercase()

    private val bySymbol: Map<String, Instrument> = all.associateBy { it.symbol }

    // Werte aus dem Katalog der Entdecken-Seiten (siehe Catalog); die feste Liste oben hat Vorrang.
    private val extra = java.util.concurrent.ConcurrentHashMap<String, Instrument>()
    @Volatile private var extraList: List<Instrument> = emptyList()

    fun register(instruments: List<Instrument>) {
        instruments.forEach { if (it.symbol !in bySymbol) extra[it.symbol] = it }
        extraList = extra.values.sortedBy { it.name.lowercase() }
    }

    fun find(symbol: String): Instrument? = bySymbol[symbol] ?: extra[symbol]

    fun alle(): List<Instrument> = all + extraList

    fun name(symbol: String): String = find(symbol)?.name ?: symbol

    // Gattung eines Werts, auch fuer Werte aus der Freitextsuche (Symbolmuster).
    fun typeOf(symbol: String): AssetType = find(symbol)?.type ?: when {
        symbol.startsWith("^") -> AssetType.INDEX
        symbol.endsWith("-EUR") || symbol.endsWith("-USD") -> AssetType.CRYPTO
        symbol.endsWith("=F") -> AssetType.COMMODITY
        symbol.endsWith("=X") -> AssetType.CURRENCY
        else -> AssetType.STOCK
    }

    // Startwerte fuer die Watchlist eines frischen Kontos.
    val defaultWatchlist = listOf("SAP.DE", "AAPL", "NVDA", "BTC-EUR", "EUNL.DE", "^GDAXI")

    val popular = listOf("NVDA", "AAPL", "SAP.DE", "TSLA", "RHM.DE", "MSFT", "AMZN", "BTC-EUR").mapNotNull(::find)

    fun search(query: String): List<Instrument> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return (all + extraList).filter {
            it.name.lowercase().contains(q) ||
                it.symbol.lowercase().contains(q) ||
                it.isin.lowercase().startsWith(q) ||
                it.sector.lowercase().contains(q)
        }.sortedBy { if (it.name.lowercase().startsWith(q) || it.symbol.lowercase().startsWith(q)) 0 else 1 }.take(60)
    }
}
