package de.troetenrepublik.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Vorgabe ist Dunkel.
enum class Appearance { SYSTEM, LIGHT, DARK }

enum class Lang(val code: String) {
    DE("de"), EN("en");

    companion object {
        // Sprache des Systems: Deutsch, wenn das Geraet auf Deutsch steht, sonst Englisch.
        fun system(): Lang = if (java.util.Locale.getDefault().language == "de") DE else EN
    }
}

// Stufen der Mitteilungen zu neuen Schlagzeilen (Settings.newsMitteilung).
const val MITTEILUNG_WICHTIGE = "wichtige"
const val MITTEILUNG_ALLE = "alle"
const val MITTEILUNG_AUS = "aus"

data class Settings(
    val appearance: Appearance = Appearance.DARK,
    // Feste Sprachwahl; gilt nur, wenn languageAuto aus ist.
    val language: Lang = Lang.DE,
    // Sprache des Systems folgen.
    val languageAuto: Boolean = true,
    val refreshSeconds: Int = 15,
    // Eigener Zinssatz auf das Guthaben, in Hundertstelprozent (225 = 2,25 %). Gilt, wenn interestAuto aus ist.
    val interestBps: Int = 200,
    // Zinssatz automatisch dem EZB-Einlagesatz folgen lassen (Vorgabe).
    val interestAuto: Boolean = true,
    // Zuletzt geladener EZB-Einlagesatz in Hundertstelprozent, Zeitpunkt des Abrufs und Gueltigkeitsbeginn.
    val ecbBps: Int = 0,
    val ecbFetchedAt: Long = 0L,
    val ecbSince: String = "",
    val lastMaintainDay: Long = 0L,
    val lastInterestDay: Long = 0L,
    val onboarded: Boolean = false,
    // Nach der Einrichtung einer neuen Installation einmal die Kurzanleitung zeigen.
    val guidePending: Boolean = false,
    val metric: Metric = Metric.TODAY_PCT,
    val watchMetric: WatchMetric = WatchMetric.TODAY_PCT,
    // Handelsspanne in Basispunkten (100 bp = 1 %).
    val spreadStockBps: Int = 20,
    val spreadDerivativeBps: Int = 50,
    // Anzeigename; die Initiale steht im Avatar.
    val ownerName: String = "",
    val hideBalances: Boolean = false,
    val shakeToHide: Boolean = false,
    val notifications: Boolean = true,
    // Chart in Gruen/Rot statt neutral.
    val coloredChart: Boolean = false,
    // Kacheln der Startseite untereinander (zwei je Reihe) statt als Reihe zum Wischen.
    val kachelRaster: Boolean = true,
    // Indikatoren im Kurschart der Wertpapierseite; anfangs alle aus.
    val indSma50: Boolean = false,
    val indSma200: Boolean = false,
    val indRsi: Boolean = false,
    val indBollinger: Boolean = false,
    val indMacd: Boolean = false,
    val indStoch: Boolean = false,
    val indAtr: Boolean = false,
    val indObv: Boolean = false,
    val indIchimoku: Boolean = false,
    val indSar: Boolean = false,
    // Freistellungsauftrag in EUR; 0 = nicht eingerichtet.
    val allowance: Double = 0.0,
    val taxId: String = "",
    // Selbst eingetragene Nachrichtenfeeds: je Zeile Name, Tabulator, Adresse.
    val newsEigene: String = "",
    // Abgeschaltete Nachrichtenquellen, Schluessel mit Komma getrennt.
    val newsAus: String = "",
    // Bis zu diesem Zeitpunkt gelten Schlagzeilen als gelesen (letztes Oeffnen der Nachrichtenseite).
    val newsGelesenBis: Long = 0L,
    // Mitteilungen zu neuen Schlagzeilen: „wichtige“ (nur die markierten), „alle“ oder „aus“.
    val newsMitteilung: String = MITTEILUNG_AUS,
    // Schlagzeilen bis zu diesem Zeitpunkt sind gemeldet oder waren schon bekannt; 0 = noch nie geprueft.
    val newsGemeldetBis: Long = 0L,
    // Werte, die schon als viel besprochen auf Reddit gemeldet wurden: „Kuerzel:Tag“ durch Komma getrennt.
    val redditGemeldet: String = "",
    // Gemerkte Schlagzeilen: je Zeile Zeit, Link, Titel, Quelle (durch Tabulatoren getrennt).
    val newsGemerkt: String = "",
    // Copytrading: gefolgte Personen und Investoren, je Zeile „k:<Schluessel>“ oder „i:<id>“.
    val copyGefolgt: String = "",
    // Zuletzt gesehene Meldung je Gefolgtem: Zeilen „<Schluessel>, Tabulator, <Meldung>“.
    val copyGesehen: String = "",
    // Mitteilungen bei Copytrading: Personen mit Glocke, je Zeile ein Schluessel wie bei copyGefolgt.
    val copyGlocke: String = "",
    // Gesetzt, sobald alle bisher Gefolgten einmal die Glocke bekommen haben.
    val copyGlockeUebernommen: Boolean = false,
    // Nur noch zum Lesen alter Einstellungen.
    val anzeigeUsd: Boolean = false,
    // Stufe der Anzeige: 0 gewaehlte Waehrung, dann US-Dollar und Euro (de.troetenrepublik.app.ui.Fmt.stufen).
    val anzeigeStufe: Int = 0,
    // Zuletzt bekannter Kurs, US-Dollar je Euro, damit die Anzeige auch ohne Netz beim Start stimmt.
    val usdJeEuro: Double = 0.0,
    // Land des Nutzers (ISO-Kuerzel, de.troetenrepublik.app.engine.Regionen); leer = Deutschland.
    val land: String = "",
    // Waehrung der Anzeige. Gebucht wird in Euro; anzeigeUsd schaltet auf die zweite Waehrung (US-Dollar, bei
    // US-Dollar als Waehrung auf Euro).
    val waehrung: String = "EUR",
    // Zuletzt bekannter Kurs der waehrung je Euro, fuer den Start ohne Netz; 0 = noch keiner.
    val waehrungJeEuro: Double = 0.0,
    // Handelszeiten: Heimatboerse des Landes, eigene oder rund um die Uhr (de.troetenrepublik.app.engine.MarketHours).
    val handelszeiten: String = "land",
    // Eigene Handelszeiten werktags, Minuten nach Mitternacht Ortszeit.
    val handelVon: Int = 8 * 60,
    val handelBis: Int = 22 * 60,
    // Verbundener Broker; leer = Uebungsdepot. Die Schluessel liegen verschluesselt im Tresor der Plattform.
    val broker: String = "",
    // Echtes Geld beim Broker (false: sein Papier- oder Demokonto).
    val brokerLive: Boolean = false,
    // Zeitpunkt, zu dem der Nutzer den Hinweisen zur Broker-Verbindung zugestimmt hat.
    val brokerZustimmung: Long = 0L,
    // Eigene Schluessel fuer Dienste mit Anmeldung. Sie bleiben auf dem Geraet, gehen nur an den jeweiligen Anbieter
    // und sind nicht in Sicherungen enthalten.
    val keyGuardian: String = "",
    val keyFinnhub: String = "",
    val keyAlphaVantage: String = "",
    val keyDeepl: String = "",
    val keyFec: String = "",
    val keyUspto: String = "",
    // Oeffentliche Polymarket-Adresse (0x und 40 Zeichen); nur zum Lesen der eigenen Positionen.
    val polyAdresse: String = "",
    // Kontaktadresse fuer Abrufe bei der SEC (Form 4); die SEC verlangt sie in der Kennung. Leer: kein Abruf.
    val secKontakt: String = "",
    // Reddit: Client-ID einer eigenen App bei Reddit und, bei „script“ oder „web app“, ihr Secret.
    val keyRedditId: String = "",
    val keyRedditSecret: String = "",
    // Mitteilung bei einer neuen Fassung der App.
    val updateHinweis: Boolean = true,
    // Letzte Abfrage nach einer neuen Fassung und ob sie eine Antwort bekam.
    val updateGeprueft: Long = 0L,
    val updateOk: Boolean = false,
    // Neuere Fassung als diese App mit der Seite zum Herunterladen; leer, wenn keine bekannt ist.
    val updateFassung: String = "",
    val updateLink: String = "",
    // Zuletzt gemeldete Fassung; jede wird nur einmal gemeldet.
    val updateGemeldet: String = "",
    // Erweiterter Chart: Zeichnungen je Wert als JSON (de.troetenrepublik.app.engine.ChartZeichnungen).
    val chartZeichnungen: String = "",
    // Erweiterter Chart: eingeschaltete Indikatoren, durch Komma getrennt.
    val chartIndikatoren: String = "volumen",
    // Erweiterter Chart: „linie“, „kerzen“ oder „heikin“.
    val chartArt: String = "kerzen",
    // Werte im Widget „Werte“, durch Komma getrennt; leer = die ersten sechs der Watchlist.
    val widgetWerte: String = "",
    // Screenshots: Bildschirmfotos.ERLAUBT, Bildschirmfotos.BROKER (Vorgabe) oder Bildschirmfotos.GESPERRT.
    val screenshots: String = Bildschirmfotos.BROKER,
    // Tagesrueckblick am Abend und Wochenrueckblick am Freitag.
    val tagesrueckblick: Boolean = true,
) {
    // Eigene Feeds als Paare Name zu Adresse.
    val eigeneFeeds: List<Pair<String, String>>
        get() = newsEigene.lines().mapNotNull { z ->
            val teile = z.split('\t')
            if (teile.size == 2 && teile[1].isNotBlank()) teile[0] to teile[1] else null
        }

    // Wirksame Sprache der Oberflaeche.
    val sprache: Lang get() = if (languageAuto) Lang.system() else language

    val quellenAus: Set<String> get() = newsAus.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    // Wirksamer Zinssatz in Prozent.
    val interestPct: Double get() = (if (interestAuto && ecbBps > 0) ecbBps else interestBps) / 100.0
}

// Einstellungen in einem DataStore; Android und Desktop legen ihn jeweils selbst an.
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object K {
        val DARK = booleanPreferencesKey("dark")
        val FOLLOW = booleanPreferencesKey("follow_system")
        val APPEARANCE = stringPreferencesKey("appearance")
        val LANGUAGE = stringPreferencesKey("language")
        val LANGUAGE_AUTO = booleanPreferencesKey("language_auto")
        val REFRESH = intPreferencesKey("refresh")
        val INTEREST = intPreferencesKey("interest")
        val INTEREST_BPS = intPreferencesKey("interest_bps")
        val INTEREST_AUTO = booleanPreferencesKey("interest_auto")
        val ECB_BPS = intPreferencesKey("ecb_bps")
        val ECB_AT = longPreferencesKey("ecb_at")
        val ECB_SINCE = stringPreferencesKey("ecb_since")
        val LAST_DAY = longPreferencesKey("last_day")
        val LAST_INTEREST = longPreferencesKey("last_interest")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val GUIDE_PENDING = booleanPreferencesKey("guide_pending")
        val METRIC = stringPreferencesKey("metric")
        val WATCH_METRIC = stringPreferencesKey("watch_metric")
        val SPREAD_STOCK = intPreferencesKey("spread_stock")
        val SPREAD_DERIV = intPreferencesKey("spread_deriv")
        val OWNER = stringPreferencesKey("owner")
        val IBAN = stringPreferencesKey("iban")
        val PIN = stringPreferencesKey("pin_hash")
        val HIDE = booleanPreferencesKey("hide_balances")
        val SHAKE = booleanPreferencesKey("shake_to_hide")
        val NOTIFY = booleanPreferencesKey("notifications")
        val COLORED = booleanPreferencesKey("colored_chart")
        val KACHEL_RASTER = booleanPreferencesKey("kachel_raster")
        val IND_SMA50 = booleanPreferencesKey("ind_sma50")
        val IND_SMA200 = booleanPreferencesKey("ind_sma200")
        val IND_RSI = booleanPreferencesKey("ind_rsi")
        val IND_BB = booleanPreferencesKey("ind_bollinger")
        val IND_MACD = booleanPreferencesKey("ind_macd")
        val IND_STOCH = booleanPreferencesKey("ind_stoch")
        val IND_ATR = booleanPreferencesKey("ind_atr")
        val IND_OBV = booleanPreferencesKey("ind_obv")
        val IND_ICHIMOKU = booleanPreferencesKey("ind_ichimoku")
        val IND_SAR = booleanPreferencesKey("ind_sar")
        val ALLOWANCE = doublePreferencesKey("allowance")
        val TAX_ID = stringPreferencesKey("tax_id")
        val NEWS_EIGENE = stringPreferencesKey("news_eigene")
        val NEWS_AUS = stringPreferencesKey("news_aus")
        val NEWS_GELESEN = longPreferencesKey("news_gelesen")
        val NEWS_MITTEILUNG = stringPreferencesKey("news_mitteilung")
        val LAND = stringPreferencesKey("land")
        val ANZEIGE_STUFE = intPreferencesKey("anzeige_stufe")
        val WAEHRUNG = stringPreferencesKey("waehrung")
        val WAEHRUNG_JE_EURO = doublePreferencesKey("waehrung_je_euro")
        val HANDELSZEITEN = stringPreferencesKey("handelszeiten")
        val HANDEL_VON = intPreferencesKey("handel_von")
        val HANDEL_BIS = intPreferencesKey("handel_bis")
        val BROKER = stringPreferencesKey("broker")
        val BROKER_LIVE = booleanPreferencesKey("broker_live")
        val BROKER_ZUSTIMMUNG = longPreferencesKey("broker_zustimmung")
        val NEWS_GEMELDET = longPreferencesKey("news_gemeldet")
        val REDDIT_GEMELDET = stringPreferencesKey("reddit_gemeldet")
        val NEWS_GEMERKT = stringPreferencesKey("news_gemerkt")
        val COPY_GEFOLGT = stringPreferencesKey("copy_gefolgt")
        val COPY_GESEHEN = stringPreferencesKey("copy_gesehen")
        val COPY_GLOCKE = stringPreferencesKey("copy_glocke")
        val CHART_ZEICHNUNGEN = stringPreferencesKey("chart_zeichnungen")
        val CHART_INDIKATOREN = stringPreferencesKey("chart_indikatoren")
        val CHART_ART = stringPreferencesKey("chart_art")
        val WIDGET_WERTE = stringPreferencesKey("widget_werte")
        val SCREENSHOTS = stringPreferencesKey("screenshots")
        val TAGESRUECKBLICK = booleanPreferencesKey("tagesrueckblick")
        val COPY_GLOCKE_UEBERNOMMEN = booleanPreferencesKey("copy_glocke_uebernommen")
        val ANZEIGE_USD = booleanPreferencesKey("anzeige_usd")
        val USD_JE_EURO = doublePreferencesKey("usd_je_euro")
        val KEY_GUARDIAN = stringPreferencesKey("key_guardian")
        val KEY_FINNHUB = stringPreferencesKey("key_finnhub")
        val KEY_ALPHAVANTAGE = stringPreferencesKey("key_alphavantage")
        val KEY_DEEPL = stringPreferencesKey("key_deepl")
        val KEY_FEC = stringPreferencesKey("key_fec")
        val KEY_USPTO = stringPreferencesKey("key_uspto")
        val POLY_ADRESSE = stringPreferencesKey("poly_adresse")
        val SEC_KONTAKT = stringPreferencesKey("sec_kontakt")
        val KEY_REDDIT_ID = stringPreferencesKey("key_reddit_id")
        val KEY_REDDIT_SECRET = stringPreferencesKey("key_reddit_secret")
        val UPDATE_HINWEIS = booleanPreferencesKey("update_hinweis")
        val UPDATE_GEPRUEFT = longPreferencesKey("update_geprueft")
        val UPDATE_OK = booleanPreferencesKey("update_ok")
        val UPDATE_FASSUNG = stringPreferencesKey("update_fassung")
        val UPDATE_LINK = stringPreferencesKey("update_link")
        val UPDATE_GEMELDET = stringPreferencesKey("update_gemeldet")
        // Gesetzt, sobald die Sichtschutz-Schalter einmal auf aus zurueckgesetzt wurden.
        val DEFAULTS_23 = booleanPreferencesKey("defaults_23")
        val DEFAULTS_48 = booleanPreferencesKey("defaults_48")
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        // Aeltere Installationen speichern den Zinssatz in ganzen Prozent.
        val bps = p[K.INTEREST_BPS] ?: p[K.INTEREST]?.let { it * 100 } ?: d.interestBps
        // Aeltere Installationen: zwei Schalter statt der Dreierwahl. Ohne Eintrag gilt Dunkel.
        val appearance = p[K.APPEARANCE]?.let { v -> runCatching { Appearance.valueOf(v) }.getOrNull() }
            ?: if (p[K.FOLLOW] == false && p[K.DARK] == false) Appearance.LIGHT else Appearance.DARK
        return Settings(
            appearance = appearance,
            language = p[K.LANGUAGE]?.let { v -> runCatching { Lang.valueOf(v) }.getOrNull() } ?: d.language,
            languageAuto = p[K.LANGUAGE_AUTO] ?: d.languageAuto,
            refreshSeconds = p[K.REFRESH] ?: d.refreshSeconds,
            interestBps = bps,
            interestAuto = p[K.INTEREST_AUTO] ?: d.interestAuto,
            ecbBps = p[K.ECB_BPS] ?: d.ecbBps,
            ecbFetchedAt = p[K.ECB_AT] ?: d.ecbFetchedAt,
            ecbSince = p[K.ECB_SINCE] ?: d.ecbSince,
            lastMaintainDay = p[K.LAST_DAY] ?: d.lastMaintainDay,
            lastInterestDay = p[K.LAST_INTEREST] ?: d.lastInterestDay,
            onboarded = p[K.ONBOARDED] ?: d.onboarded,
            guidePending = p[K.GUIDE_PENDING] ?: d.guidePending,
            metric = p[K.METRIC]?.let { v -> runCatching { Metric.valueOf(v) }.getOrNull() } ?: d.metric,
            watchMetric = p[K.WATCH_METRIC]?.let { v -> runCatching { WatchMetric.valueOf(v) }.getOrNull() } ?: d.watchMetric,
            spreadStockBps = p[K.SPREAD_STOCK] ?: d.spreadStockBps,
            spreadDerivativeBps = p[K.SPREAD_DERIV] ?: d.spreadDerivativeBps,
            ownerName = p[K.OWNER] ?: d.ownerName,
            // Einmal auf aus zurueckgesetzt; erst eine spaetere Umschaltung gilt.
            hideBalances = if (p[K.DEFAULTS_23] == true) p[K.HIDE] ?: d.hideBalances else false,
            shakeToHide = if (p[K.DEFAULTS_23] == true) p[K.SHAKE] ?: d.shakeToHide else false,
            notifications = p[K.NOTIFY] ?: d.notifications,
            coloredChart = p[K.COLORED] ?: d.coloredChart,
            kachelRaster = p[K.KACHEL_RASTER] ?: d.kachelRaster,
            indSma50 = p[K.IND_SMA50] ?: d.indSma50,
            indSma200 = p[K.IND_SMA200] ?: d.indSma200,
            indRsi = p[K.IND_RSI] ?: d.indRsi,
            indBollinger = p[K.IND_BB] ?: d.indBollinger,
            indMacd = p[K.IND_MACD] ?: d.indMacd,
            indStoch = p[K.IND_STOCH] ?: d.indStoch,
            indAtr = p[K.IND_ATR] ?: d.indAtr,
            indObv = p[K.IND_OBV] ?: d.indObv,
            indIchimoku = p[K.IND_ICHIMOKU] ?: d.indIchimoku,
            indSar = p[K.IND_SAR] ?: d.indSar,
            allowance = p[K.ALLOWANCE] ?: d.allowance,
            taxId = p[K.TAX_ID] ?: d.taxId,
            newsEigene = p[K.NEWS_EIGENE] ?: d.newsEigene,
            newsAus = p[K.NEWS_AUS] ?: d.newsAus,
            newsGelesenBis = p[K.NEWS_GELESEN] ?: d.newsGelesenBis,
            // Einmal auf aus zurueckgesetzt; erst eine spaetere Wahl gilt.
            newsMitteilung = if (p[K.DEFAULTS_48] == true) p[K.NEWS_MITTEILUNG] ?: d.newsMitteilung else MITTEILUNG_AUS,
            land = p[K.LAND] ?: d.land,
            // Alte Einstellung anzeigeUsd: Dollar ist die zweite Stufe
            anzeigeStufe = p[K.ANZEIGE_STUFE] ?: if (p[K.ANZEIGE_USD] == true) 1 else 0,
            waehrung = p[K.WAEHRUNG] ?: d.waehrung,
            waehrungJeEuro = p[K.WAEHRUNG_JE_EURO] ?: d.waehrungJeEuro,
            handelszeiten = p[K.HANDELSZEITEN] ?: d.handelszeiten,
            handelVon = p[K.HANDEL_VON] ?: d.handelVon,
            handelBis = p[K.HANDEL_BIS] ?: d.handelBis,
            broker = p[K.BROKER] ?: d.broker,
            brokerLive = p[K.BROKER_LIVE] ?: d.brokerLive,
            brokerZustimmung = p[K.BROKER_ZUSTIMMUNG] ?: d.brokerZustimmung,
            newsGemeldetBis = p[K.NEWS_GEMELDET] ?: d.newsGemeldetBis,
            redditGemeldet = p[K.REDDIT_GEMELDET] ?: d.redditGemeldet,
            newsGemerkt = p[K.NEWS_GEMERKT] ?: d.newsGemerkt,
            copyGefolgt = p[K.COPY_GEFOLGT] ?: d.copyGefolgt,
            copyGesehen = p[K.COPY_GESEHEN] ?: d.copyGesehen,
            copyGlocke = p[K.COPY_GLOCKE] ?: d.copyGlocke,
            chartZeichnungen = p[K.CHART_ZEICHNUNGEN] ?: d.chartZeichnungen,
            chartIndikatoren = p[K.CHART_INDIKATOREN] ?: d.chartIndikatoren,
            chartArt = p[K.CHART_ART] ?: d.chartArt,
            widgetWerte = p[K.WIDGET_WERTE] ?: d.widgetWerte,
            screenshots = p[K.SCREENSHOTS] ?: d.screenshots,
            tagesrueckblick = p[K.TAGESRUECKBLICK] ?: d.tagesrueckblick,
            copyGlockeUebernommen = p[K.COPY_GLOCKE_UEBERNOMMEN] ?: d.copyGlockeUebernommen,
            anzeigeUsd = p[K.ANZEIGE_USD] ?: d.anzeigeUsd,
            usdJeEuro = p[K.USD_JE_EURO] ?: d.usdJeEuro,
            keyGuardian = p[K.KEY_GUARDIAN] ?: d.keyGuardian,
            keyFinnhub = p[K.KEY_FINNHUB] ?: d.keyFinnhub,
            keyAlphaVantage = p[K.KEY_ALPHAVANTAGE] ?: d.keyAlphaVantage,
            keyDeepl = p[K.KEY_DEEPL] ?: d.keyDeepl,
            keyFec = p[K.KEY_FEC] ?: d.keyFec,
            keyUspto = p[K.KEY_USPTO] ?: d.keyUspto,
            polyAdresse = p[K.POLY_ADRESSE] ?: d.polyAdresse,
            secKontakt = p[K.SEC_KONTAKT] ?: d.secKontakt,
            keyRedditId = p[K.KEY_REDDIT_ID] ?: d.keyRedditId,
            keyRedditSecret = p[K.KEY_REDDIT_SECRET] ?: d.keyRedditSecret,
            updateHinweis = p[K.UPDATE_HINWEIS] ?: d.updateHinweis,
            updateGeprueft = p[K.UPDATE_GEPRUEFT] ?: d.updateGeprueft,
            updateOk = p[K.UPDATE_OK] ?: d.updateOk,
            updateFassung = p[K.UPDATE_FASSUNG] ?: d.updateFassung,
            updateLink = p[K.UPDATE_LINK] ?: d.updateLink,
            updateGemeldet = p[K.UPDATE_GEMELDET] ?: d.updateGemeldet,
        )
    }

    val flow: Flow<Settings> = dataStore.data.map { read(it) }

    suspend fun update(block: (Settings) -> Settings) {
        // Ein Schreibfehler (voller Speicher) darf die App nicht beenden; die Einstellung gilt dann bis zum Neustart
        runCatching { schreiben(block) }
    }

    private suspend fun schreiben(block: (Settings) -> Settings) {
        dataStore.edit { p ->
            val n = block(read(p))
            p[K.APPEARANCE] = n.appearance.name
            p[K.LANGUAGE] = n.language.name
            p[K.LANGUAGE_AUTO] = n.languageAuto
            p[K.REFRESH] = n.refreshSeconds
            p[K.INTEREST_BPS] = n.interestBps
            p[K.INTEREST_AUTO] = n.interestAuto
            p[K.ECB_BPS] = n.ecbBps
            p[K.ECB_AT] = n.ecbFetchedAt
            p[K.ECB_SINCE] = n.ecbSince
            p[K.LAST_DAY] = n.lastMaintainDay
            p[K.LAST_INTEREST] = n.lastInterestDay
            p[K.ONBOARDED] = n.onboarded
            p[K.GUIDE_PENDING] = n.guidePending
            p[K.METRIC] = n.metric.name
            p[K.WATCH_METRIC] = n.watchMetric.name
            p[K.SPREAD_STOCK] = n.spreadStockBps
            p[K.SPREAD_DERIV] = n.spreadDerivativeBps
            p[K.OWNER] = n.ownerName
            // Alte Eintraege (IBAN, ungesalzener PIN-Hash) werden beim naechsten Schreiben entfernt
            p.remove(K.IBAN)
            p.remove(K.PIN)
            p[K.HIDE] = n.hideBalances
            p[K.SHAKE] = n.shakeToHide
            p[K.NOTIFY] = n.notifications
            p[K.COLORED] = n.coloredChart
            p[K.KACHEL_RASTER] = n.kachelRaster
            p[K.IND_SMA50] = n.indSma50
            p[K.IND_SMA200] = n.indSma200
            p[K.IND_RSI] = n.indRsi
            p[K.IND_BB] = n.indBollinger
            p[K.IND_MACD] = n.indMacd
            p[K.IND_STOCH] = n.indStoch
            p[K.IND_ATR] = n.indAtr
            p[K.IND_OBV] = n.indObv
            p[K.IND_ICHIMOKU] = n.indIchimoku
            p[K.IND_SAR] = n.indSar
            p[K.ALLOWANCE] = n.allowance
            p[K.TAX_ID] = n.taxId
            p[K.NEWS_EIGENE] = n.newsEigene
            p[K.NEWS_AUS] = n.newsAus
            p[K.NEWS_GELESEN] = n.newsGelesenBis
            p[K.NEWS_MITTEILUNG] = n.newsMitteilung
            p[K.LAND] = n.land
            p[K.ANZEIGE_STUFE] = n.anzeigeStufe
            p[K.WAEHRUNG] = n.waehrung
            p[K.WAEHRUNG_JE_EURO] = n.waehrungJeEuro
            p[K.HANDELSZEITEN] = n.handelszeiten
            p[K.HANDEL_VON] = n.handelVon
            p[K.HANDEL_BIS] = n.handelBis
            p[K.BROKER] = n.broker
            p[K.BROKER_LIVE] = n.brokerLive
            p[K.BROKER_ZUSTIMMUNG] = n.brokerZustimmung
            p[K.NEWS_GEMELDET] = n.newsGemeldetBis
            p[K.REDDIT_GEMELDET] = n.redditGemeldet
            p[K.NEWS_GEMERKT] = n.newsGemerkt
            p[K.COPY_GEFOLGT] = n.copyGefolgt
            p[K.COPY_GESEHEN] = n.copyGesehen
            p[K.COPY_GLOCKE] = n.copyGlocke
            p[K.CHART_ZEICHNUNGEN] = n.chartZeichnungen
            p[K.CHART_INDIKATOREN] = n.chartIndikatoren
            p[K.CHART_ART] = n.chartArt
            p[K.WIDGET_WERTE] = n.widgetWerte
            p[K.SCREENSHOTS] = n.screenshots
            p[K.TAGESRUECKBLICK] = n.tagesrueckblick
            p[K.COPY_GLOCKE_UEBERNOMMEN] = n.copyGlockeUebernommen
            p[K.ANZEIGE_USD] = n.anzeigeUsd
            p[K.USD_JE_EURO] = n.usdJeEuro
            p[K.KEY_GUARDIAN] = n.keyGuardian.trim()
            p[K.KEY_FINNHUB] = n.keyFinnhub.trim()
            p[K.KEY_ALPHAVANTAGE] = n.keyAlphaVantage.trim()
            p[K.KEY_DEEPL] = n.keyDeepl.trim()
            p[K.KEY_FEC] = n.keyFec.trim()
            p[K.KEY_USPTO] = n.keyUspto.trim()
            p[K.POLY_ADRESSE] = n.polyAdresse.trim()
            p[K.SEC_KONTAKT] = n.secKontakt.trim()
            p[K.KEY_REDDIT_ID] = n.keyRedditId.trim()
            p[K.KEY_REDDIT_SECRET] = n.keyRedditSecret.trim()
            p[K.UPDATE_HINWEIS] = n.updateHinweis
            p[K.UPDATE_GEPRUEFT] = n.updateGeprueft
            p[K.UPDATE_OK] = n.updateOk
            p[K.UPDATE_FASSUNG] = n.updateFassung
            p[K.UPDATE_LINK] = n.updateLink
            p[K.UPDATE_GEMELDET] = n.updateGemeldet
            p[K.DEFAULTS_23] = true
            p[K.DEFAULTS_48] = true
        }
    }

    companion object {
    }
}

// Wann die App Screenshots und Bildschirmaufnahmen zulaesst.
object Bildschirmfotos {
    // Ueberall erlaubt, auch auf der Seite mit den Broker-Schluesseln.
    const val ERLAUBT = "erlaubt"
    // Erlaubt, nur nicht auf der Seite mit den Broker-Schluesseln (Vorgabe).
    const val BROKER = "broker"
    // In der ganzen App gesperrt; die App-Uebersicht zeigt kein Bild.
    const val GESPERRT = "gesperrt"
    val ALLE = listOf(ERLAUBT, BROKER, GESPERRT)
}
