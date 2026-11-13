package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Region

// Einstiege Indizes, Rohstoffe, Waehrungen und Krypto der Suche: Gruppe je Wert und gepflegte Zusatzlisten neben den
// Basiswerten des Katalogs. Alle Kuerzel sind die der Kursquelle; Stand der Listen 12.11.2026.
object Suchgruppen {

    // land: ISO-Code des Heimatmarkts (nur Indizes); waehrung: Handelswaehrung laut Kursquelle.
    class Wert(val symbol: String, val de: String, val en: String = de, val land: String = "", val waehrung: String = "EUR")

    // ------------------------------------------------------------ Indizes

    // Reihenfolge der Regionen in der Auswahl.
    val REGIONEN = listOf("DEUTSCHLAND", "EUROPA", "NORDAMERIKA", "ASIEN_PAZIFIK", "SCHWELLENLAENDER")

    val INDIZES = listOf(
        Wert("^IXIC", "Nasdaq Composite", land = "US", waehrung = "USD"),
        Wert("^GSPTSE", "S&P/TSX Composite", land = "CA", waehrung = "CAD"),
        Wert("^STOXX", "Stoxx Europe 600", land = "EU"),
        Wert("^IBEX", "IBEX 35", land = "ES"),
        Wert("FTSEMIB.MI", "FTSE MIB", land = "IT"),
        Wert("^AEX", "AEX", land = "NL"),
        Wert("^ATX", "ATX", land = "AT"),
        Wert("^BFX", "BEL 20", land = "BE"),
        Wert("PSI20.LS", "PSI", land = "PT"),
        Wert("^OMX", "OMX Stockholm 30", land = "SE", waehrung = "SEK"),
        Wert("^OMXC25", "OMX Copenhagen 25", land = "DK", waehrung = "DKK"),
        Wert("^OMXH25", "OMX Helsinki 25", land = "FI"),
        Wert("OBX.OL", "OBX", land = "NO", waehrung = "NOK"),
        Wert("WIG20.WA", "WIG20", land = "PL", waehrung = "PLN"),
        Wert("^AXJO", "S&P/ASX 200", land = "AU", waehrung = "AUD"),
        Wert("^NZ50", "S&P/NZX 50", land = "NZ", waehrung = "NZD"),
        Wert("^KS11", "KOSPI", land = "KR", waehrung = "KRW"),
        Wert("^TWII", "TAIEX", land = "TW", waehrung = "TWD"),
        Wert("^STI", "Straits Times", land = "SG", waehrung = "SGD"),
        Wert("000001.SS", "SSE Composite", land = "CN", waehrung = "CNY"),
        Wert("^BSESN", "BSE Sensex", land = "IN", waehrung = "INR"),
        Wert("^NSEI", "Nifty 50", land = "IN", waehrung = "INR"),
        Wert("^JKSE", "IDX Composite", land = "ID", waehrung = "IDR"),
        Wert("^BVSP", "Bovespa", land = "BR", waehrung = "BRL"),
        Wert("^MXX", "S&P/BMV IPC", land = "MX", waehrung = "MXN"),
        Wert("XU100.IS", "BIST 100", land = "TR", waehrung = "TRY"),
        Wert("^TA125.TA", "TA-125", land = "IL", waehrung = "ILS"),
    )

    // Region eines Index: die des Katalogs, sonst die des Heimatmarkts; Indizes ueber mehrere Laender Europas zaehlen
    // zu Europa. Ohne Angabe „GLOBAL“.
    fun indexRegion(land: String, katalogRegion: String = ""): String = when {
        katalogRegion.isNotBlank() && katalogRegion != "GLOBAL" -> katalogRegion
        land == "EU" -> "EUROPA"
        else -> Region.ofCountry(land)
    }

    // ------------------------------------------------------------ Rohstoffe

    val ROHSTOFF_GRUPPEN = listOf("EDELMETALLE", "ENERGIE", "INDUSTRIEMETALLE", "AGRAR")

    val ROHSTOFFE = listOf(
        Wert("HO=F", "Heizöl", "Heating Oil", waehrung = "USD"),
        Wert("RB=F", "Benzin", "Gasoline", waehrung = "USD"),
        Wert("ALI=F", "Aluminium", "Aluminium", waehrung = "USD"),
        Wert("ZS=F", "Sojabohnen", "Soybeans", waehrung = "USX"),
        Wert("ZL=F", "Sojaöl", "Soybean Oil", waehrung = "USX"),
        Wert("ZM=F", "Sojaschrot", "Soybean Meal", waehrung = "USD"),
        Wert("CT=F", "Baumwolle", "Cotton", waehrung = "USX"),
        Wert("OJ=F", "Orangensaft", "Orange Juice", waehrung = "USX"),
        Wert("ZO=F", "Hafer", "Oats", waehrung = "USX"),
        Wert("ZR=F", "Reis", "Rough Rice", waehrung = "USD"),
        Wert("LE=F", "Lebendrind", "Live Cattle", waehrung = "USX"),
        Wert("HE=F", "Mageres Schwein", "Lean Hogs", waehrung = "USX"),
    )

    private val ROHSTOFF_GRUPPE: Map<String, String> = buildMap {
        listOf("GC", "SI", "PL", "PA").forEach { put(it, "EDELMETALLE") }
        listOf("CL", "BZ", "NG", "HO", "RB").forEach { put(it, "ENERGIE") }
        listOf("HG", "ALI").forEach { put(it, "INDUSTRIEMETALLE") }
        listOf("ZW", "ZC", "ZS", "ZL", "ZM", "ZO", "ZR", "KC", "CC", "SB", "CT", "OJ", "LE", "HE").forEach { put(it, "AGRAR") }
    }

    // Gruppe eines Rohstoff-Futures nach seinem Kuerzel („GC=F“); leer, wenn er in keiner Gruppe gefuehrt ist.
    fun rohstoffGruppe(symbol: String): String = ROHSTOFF_GRUPPE[symbol.substringBefore('=').uppercase()].orEmpty()

    // ------------------------------------------------------------ Waehrungen

    val WAEHRUNGS_GRUPPEN = listOf("HAUPT", "NEBEN", "SCHWELLEN", "OHNE_EURO")

    private val HAUPT = setOf("USD", "JPY", "GBP", "CHF", "AUD", "CAD", "NZD")
    private val NEBEN = setOf("SEK", "NOK", "DKK", "SGD", "HKD", "ILS")
    private val SCHWELLEN = setOf("PLN", "CZK", "HUF", "TRY", "ZAR", "MXN", "BRL", "INR", "CNY", "KRW", "IDR", "THB")

    val WAEHRUNGEN: List<Wert> = (listOf("AUD", "CAD", "NZD") + NEBEN + SCHWELLEN).map { Wert("EUR$it=X", "EUR/$it", waehrung = it) }

    // Gruppe eines Waehrungspaars („EURUSD=X“) aus Sicht des Euro: Hauptpaare mit den meistgehandelten Waehrungen,
    // Nebenpaare mit weiteren Waehrungen entwickelter Laender, Schwellenlaender; Paare ohne Euro eigene Gruppe.
    // Leer bei unbekannter Gegenwaehrung oder unlesbarem Kuerzel.
    fun waehrungsGruppe(symbol: String): String {
        val paar = symbol.substringBefore('=').uppercase()
        if (paar.length != 6) return ""
        val a = paar.take(3)
        val b = paar.drop(3)
        if (a != "EUR" && b != "EUR") return "OHNE_EURO"
        return when (if (a == "EUR") b else a) {
            in HAUPT -> "HAUPT"
            in NEBEN -> "NEBEN"
            in SCHWELLEN -> "SCHWELLEN"
            else -> ""
        }
    }

    // ------------------------------------------------------------ Krypto

    val KRYPTO_KATEGORIEN = listOf("LAYER1", "ZAHLUNG", "STABLECOIN", "DEFI", "INFRASTRUKTUR", "MEME")

    val KRYPTO = listOf(
        Wert("BNB-EUR", "BNB"), Wert("TRX-EUR", "TRON"), Wert("USDT-EUR", "Tether"), Wert("USDC-EUR", "USDC"),
        Wert("DAI-EUR", "Dai"), Wert("SHIB-EUR", "Shiba Inu"), Wert("BCH-EUR", "Bitcoin Cash"), Wert("XLM-EUR", "Stellar"),
        Wert("XMR-EUR", "Monero"), Wert("ETC-EUR", "Ethereum Classic"), Wert("ATOM-EUR", "Cosmos"), Wert("ALGO-EUR", "Algorand"),
        Wert("HBAR-EUR", "Hedera"), Wert("ICP-EUR", "Internet Computer"), Wert("FIL-EUR", "Filecoin"), Wert("AAVE-EUR", "Aave"),
        Wert("MKR-EUR", "Maker"), Wert("UNI7083-EUR", "Uniswap"), Wert("POL28321-EUR", "Polygon"), Wert("CRO-EUR", "Cronos"),
        Wert("VET-EUR", "VeChain"), Wert("CRV-EUR", "Curve DAO"), Wert("XTZ-EUR", "Tezos"), Wert("EOS-EUR", "EOS"),
    )

    private val KRYPTO_KATEGORIE: Map<String, String> = buildMap {
        // Eigene Basisnetze mit Smart Contracts oder eigener Kette
        listOf("BTC", "ETH", "SOL", "ADA", "DOT", "AVAX", "BNB", "TRX", "ETC", "ATOM", "ALGO", "HBAR", "ICP", "XTZ", "EOS", "VET", "CRO").forEach { put(it, "LAYER1") }
        listOf("XRP", "LTC", "BCH", "XLM", "XMR").forEach { put(it, "ZAHLUNG") }
        listOf("USDT", "USDC", "DAI").forEach { put(it, "STABLECOIN") }
        listOf("AAVE", "MKR", "UNI", "CRV").forEach { put(it, "DEFI") }
        // Orakel, Speicher, zweite Ebene
        listOf("LINK", "FIL", "POL").forEach { put(it, "INFRASTRUKTUR") }
        listOf("DOGE", "SHIB").forEach { put(it, "MEME") }
    }

    // Kuerzel der Muenze aus dem Kurskuerzel: „UNI7083-EUR“ wird „UNI“ (die Kursquelle haengt bei mehrdeutigen Kuerzeln
    // eine Nummer an).
    fun kryptoKuerzel(symbol: String): String = symbol.substringBefore('-').uppercase().trimEnd { it.isDigit() }

    // Kategorie einer Kryptowaehrung; leer, wenn sie in keiner gefuehrt ist.
    fun kryptoKategorie(symbol: String): String = KRYPTO_KATEGORIE[kryptoKuerzel(symbol)].orEmpty()

    // ------------------------------------------------------------ Listen der Krypto-Seite

    enum class KryptoListe { MARKTWERT, GEWINNER, VERLIERER, UMSATZ, HOCH }

    // tag und woche: Veraenderung in Prozent; marktwert und umsatz (24 Stunden) in Euro; hoch52: 52-Wochen-Hoch in Euro.
    // 0 oder null heisst unbekannt.
    data class KryptoZeile(
        val symbol: String,
        val name: String,
        val kurs: Double = 0.0,
        val tag: Double? = null,
        val woche: Double? = null,
        val marktwert: Double = 0.0,
        val umsatz: Double = 0.0,
        val hoch52: Double = 0.0,
    ) {
        val kategorie: String get() = kryptoKategorie(symbol)
        // Abstand zum 52-Wochen-Hoch in Prozent (0 am Hoch); null ohne Kurs oder Hoch.
        val unterHoch: Double? get() = if (kurs > 0 && hoch52 > 0) ((hoch52 - kurs) / hoch52 * 100.0).coerceAtLeast(0.0) else null
    }

    // Zeilen einer Liste in ihrer Reihenfolge; Werte ohne die noetige Angabe fallen weg. Gewinner sind nur Werte im
    // Plus, Verlierer nur Werte im Minus. wochenweise waehlt bei Gewinnern und Verlierern die Woche statt des Tages.
    // kategorie leer: alle.
    fun kryptoListe(zeilen: List<KryptoZeile>, liste: KryptoListe, wochenweise: Boolean = false, kategorie: String = ""): List<KryptoZeile> {
        val z = if (kategorie.isEmpty()) zeilen else zeilen.filter { it.kategorie == kategorie }
        fun aenderung(k: KryptoZeile): Double? = (if (wochenweise) k.woche else k.tag)?.takeIf { it.isFinite() }
        return when (liste) {
            KryptoListe.MARKTWERT -> z.filter { it.marktwert > 0 }.sortedByDescending { it.marktwert }
            KryptoListe.UMSATZ -> z.filter { it.umsatz > 0 }.sortedByDescending { it.umsatz }
            KryptoListe.GEWINNER -> z.filter { (aenderung(it) ?: 0.0) > 0 }.sortedByDescending { aenderung(it) }
            KryptoListe.VERLIERER -> z.filter { (aenderung(it) ?: 0.0) < 0 }.sortedBy { aenderung(it) }
            KryptoListe.HOCH -> z.filter { it.unterHoch != null }.sortedBy { it.unterHoch }
        }
    }

    // Anteil von Bitcoin am Marktwert der gefuehrten Muenzen in Prozent und die Zahl der Muenzen mit Marktwert. Nur
    // wenn fuer mindestens vier Fuenftel der Liste ein Marktwert vorliegt, sonst null. Das ist nicht die Dominanz am
    // ganzen Markt: Muenzen ausserhalb der Liste fehlen in der Summe.
    fun bitcoinAnteil(zeilen: List<KryptoZeile>): Pair<Double, Int>? {
        val mit = zeilen.filter { it.marktwert > 0 }
        val btc = mit.firstOrNull { kryptoKuerzel(it.symbol) == "BTC" } ?: return null
        if (zeilen.isEmpty() || mit.size * 5 < zeilen.size * 4) return null
        return btc.marktwert / mit.sumOf { it.marktwert } * 100.0 to mit.size
    }
}
