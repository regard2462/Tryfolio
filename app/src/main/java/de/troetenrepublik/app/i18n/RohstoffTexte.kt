package de.troetenrepublik.app.i18n

// Erklaerungen zu Rohstoffen fuer die Wertpapierseite: je Rohstoff vier Abschnitte (was es ist, was den Preis
// bewegt, wie man anlegt, ausserdem). Schluessel aus engine/Rohstoffe.kt. Die App erklaert, sie beraet nicht.
internal object RohstoffTexteDe {
    val TITEL = listOf("Was es ist", "Was den Preis bewegt", "So legt man an", "Außerdem")

    const val TERMIN = "Der Kurs hier ist der nächstfällige Terminkontrakt. Produkte auf Terminkontrakte müssen regelmäßig in den nächsten Kontrakt wechseln. Ist der teurer (Contango), kostet jeder Wechsel Rendite. Solche Produkte entwickeln sich deshalb über Jahre oft schlechter als der angezeigte Preis."

    val TEXTE: Map<String, List<String>> = mapOf(
        "GOLD" to listOf(
            "Ein Edelmetall, das seit Jahrtausenden als Wertaufbewahrung dient. Gekauft wird es vor allem für Schmuck, von Anlegern und von Notenbanken, die Industrie braucht nur wenig. Der Preis gilt in US-Dollar je Feinunze (31,1 Gramm).",
            "Am stärksten wirken die Realzinsen, also Zins minus Inflation. Sinken sie, wird Gold attraktiver, weil es selbst keine Zinsen zahlt. Ein schwacher Dollar macht Gold für Käufer außerhalb der USA billiger. Dazu kommen Krisen und Unsicherheit sowie die Käufe der Notenbanken, die seit 2022 jedes Jahr über 1.000 Tonnen gekauft haben.",
            "Physisch als Barren oder Münzen, über börsengehandelte Gold-ETCs (bei Xetra-Gold entspricht ein Anteil einem Gramm Gold, mit Anspruch auf Auslieferung) oder indirekt über Aktien von Goldminen, die stärker schwanken.",
            "Gold zahlt weder Zinsen noch Dividenden, der Ertrag kommt allein aus dem Preis. Es gab lange Verlustphasen. Von 1980 bis 2001 verlor Gold nach Inflation rund 80 % an Wert. In Deutschland sind Gewinne aus physischem Gold und aus ETCs mit verbrieftem Lieferanspruch wie Xetra-Gold nach einem Jahr Haltedauer steuerfrei. Andere ETCs gelten oft als normales Wertpapier.",
        ),
        "SILBER" to listOf(
            "Ein Edelmetall, das zugleich ein Industriemetall ist: Mehr als die Hälfte der Nachfrage kommt aus der Industrie, etwa für Solarzellen und Elektronik. Der Preis gilt in US-Dollar je Feinunze.",
            "Silber folgt oft dem Gold, schwankt aber deutlich stärker, weil auch die Konjunktur zählt. Viel Silber fällt als Nebenprodukt beim Abbau anderer Metalle an, das Angebot reagiert deshalb kaum auf den Preis. Ein beliebter Vergleich ist das Gold-Silber-Verhältnis: wie viele Unzen Silber eine Unze Gold kostet.",
            "Physisch als Münzen oder Barren, über Silber-ETCs oder über Aktien von Silberminen.",
            "Physisches Silber kostet in Deutschland Mehrwertsteuer, Gold nicht. Das macht kleine Mengen Silber für Privatleute teurer. Wie Gold zahlt Silber keine Zinsen oder Dividenden.",
        ),
        "PLATIN" to listOf(
            "Ein seltenes Edelmetall. Ein großer Teil geht in Katalysatoren, vor allem für Dieselautos, dazu in Schmuck und Industrie.",
            "Das Angebot ist stark konzentriert: Rund 70 % kommen aus Südafrika, wo Stromausfälle und Streiks die Förderung stören können. Auf der Nachfrageseite zählen Autobau, Abgasregeln und der Wechsel zu Elektroautos.",
            "Über ETCs auf Platin, physisch als Barren oder über Terminkontrakte.",
            "Der Markt ist klein, deshalb schwankt der Preis stark. Platin war lange teurer als Gold und kostet seit Jahren deutlich weniger.",
        ),
        "PALLADIUM" to listOf(
            "Ein Edelmetall, das fast nur in Katalysatoren für Benzinautos gebraucht wird.",
            "Ein großer Teil kommt aus Russland und Südafrika, Sanktionen und Förderprobleme bewegen den Preis. Elektroautos brauchen keinen Katalysator und senken langfristig die Nachfrage.",
            "Über ETCs auf Palladium oder über Terminkontrakte.",
            "Einer der schwankungsreichsten Rohstoffe: Der Preis hat sich von 2016 bis 2022 mehr als verfünffacht und danach wieder mehr als halbiert.",
        ),
        "OEL" to listOf(
            "Rohöl ist der wichtigste Energierohstoff der Welt. Es gibt zwei Referenzsorten: Brent aus der Nordsee als Maßstab für Europa und WTI aus den USA. Beide werden in US-Dollar je Barrel (159 Liter) gehandelt.",
            "Angebot und Nachfrage: die Fördermengen der OPEC und ihrer Partner (OPEC+), die Schieferölförderung in den USA, die Weltkonjunktur, Lagerbestände und Konflikte in Förderländern. Steigt der Ölpreis stark, steigen auch Inflation und Kosten vieler Firmen.",
            "Privatanleger kommen fast nur über Terminkontrakte heran, also über ETCs, Zertifikate oder Hebelprodukte. Eine Alternative sind Aktien von Ölkonzernen, die zusätzlich Dividenden zahlen.",
            "Im April 2020 fiel der WTI-Kontrakt kurz auf minus 37 US-Dollar: Die Lager waren voll und niemand wollte das Öl abnehmen. Das zeigt, wie stark Lager und Termine den Preis prägen.",
        ),
        "GAS" to listOf(
            "Erdgas zum Heizen, für Strom und Industrie. Der Kurs hier ist Henry Hub, der Preis in den USA. Europa hat einen eigenen Markt (TTF in den Niederlanden), dessen Preis oft ganz anders läuft.",
            "Wetter und Jahreszeit (kalte Winter, heiße Sommer), Füllstände der Speicher, Förderung und Flüssiggas-Exporte (LNG).",
            "Fast nur über Terminkontrakte, also ETCs und Zertifikate. Die Wechselkosten zwischen den Kontrakten sind bei Gas besonders hoch.",
            "Einer der schwankungsreichsten Märkte überhaupt: Tagesbewegungen von 10 % kommen vor. Gas-ETCs haben über viele Jahre trotz zeitweiser Preissprünge große Teile ihres Werts verloren.",
        ),
        "KUPFER" to listOf(
            "Das wichtigste Industriemetall für Strom: Leitungen, Motoren, Stromnetze, Elektroautos und Bauwirtschaft. Der Preis hier gilt in US-Dollar je Pfund.",
            "Weil Kupfer fast überall gebraucht wird, gilt sein Preis als Konjunkturbarometer („Dr. Copper“). China verbraucht rund die Hälfte des Kupfers der Welt. Gefördert wird vor allem in Chile und Peru. Neue Minen brauchen viele Jahre bis zur Förderung.",
            "Über ETCs auf Kupfer oder Terminkontrakte, indirekt über Aktien von Bergbaukonzernen.",
            "Energiewende und Stromnetze erhöhen langfristig den Bedarf, eine Konjunkturschwäche in China drückt kurzfristig den Preis.",
        ),
        "WEIZEN" to listOf(
            "Eines der wichtigsten Grundnahrungsmittel. Der Kurs hier ist der Terminkontrakt in Chicago, in US-Cent je Scheffel (Bushel, rund 27 kg).",
            "Wetter und Ernten in den großen Anbauländern, Exporte aus Russland und der Ukraine, Lagerbestände und Exportverbote. Der Krieg in der Ukraine ließ den Preis 2022 auf ein Rekordhoch springen.",
            "Nur über Terminkontrakte, also ETCs oder Agrar-Rohstoffkörbe.",
            "Agrarpreise folgen dem Erntejahr und dem Wetter, Prognosen sind schwierig. Höhere Getreidepreise treffen vor allem ärmere Länder.",
        ),
        "MAIS" to listOf(
            "Mais dient als Futter, Lebensmittel und Rohstoff für Bioethanol. Der Kurs hier ist der Terminkontrakt in Chicago, in US-Cent je Scheffel.",
            "Ernten in den USA, Brasilien und Argentinien, das Wetter in der Wachstumszeit, der Ölpreis (ein großer Teil des US-Maises wird zu Ethanol) und die Nachfrage aus China.",
            "Nur über Terminkontrakte, also ETCs oder Agrar-Rohstoffkörbe.",
            "Der Wochenbericht des US-Landwirtschaftsministeriums zu Anbau und Ernte bewegt den Preis oft stark.",
        ),
        "KAFFEE" to listOf(
            "Der Kurs hier ist Arabica, die Sorte für die meisten Röstkaffees, in US-Cent je Pfund. Die günstigere Sorte Robusta wird getrennt gehandelt.",
            "Das Wetter in Brasilien, dem größten Erzeuger (Frost und Dürre), Ernten in Kolumbien und Vietnam, Transportkosten und der Kurs des brasilianischen Real.",
            "Nur über Terminkontrakte, also ETCs oder Agrar-Rohstoffkörbe, indirekt über Aktien von Kaffeehändlern und Röstern.",
            "Kaffeesträucher brauchen Jahre bis zur ersten Ernte. Fällt eine Ernte aus, bleibt der Preis deshalb oft länger hoch.",
        ),
        "KAKAO" to listOf(
            "Rohstoff für Schokolade. Der Kurs hier ist der Terminkontrakt in New York, in US-Dollar je Tonne.",
            "Rund 60 % der Ernte kommen aus der Elfenbeinküste und Ghana. Wetter, Pflanzenkrankheiten und die festen Ankaufpreise dieser Staaten bestimmen das Angebot.",
            "Nur über Terminkontrakte, also ETCs oder Agrar-Rohstoffkörbe.",
            "2024 stieg der Preis nach schlechten Ernten zeitweise auf über 10.000 US-Dollar je Tonne, ein Vielfaches des Üblichen. Schokolade wurde deutlich teurer.",
        ),
        "ZUCKER" to listOf(
            "Rohzucker aus Zuckerrohr. Der Kurs hier ist der Terminkontrakt in New York, in US-Cent je Pfund.",
            "Ernten in Brasilien, Indien und Thailand, Exportregeln Indiens und der Ölpreis: Brasilien macht aus Zuckerrohr auch Ethanol für Autos und verschiebt die Ernte je nach Preis.",
            "Nur über Terminkontrakte, also ETCs oder Agrar-Rohstoffkörbe.",
            "Agrarrohstoffe schwanken mit dem Wetter und laufen oft unabhängig von Aktien.",
        ),
        "KORB" to listOf(
            "Ein Fonds auf einen Rohstoffindex: Energie, Industriemetalle, Edelmetalle und je nach Index Agrarrohstoffe und Vieh in einem Korb. Der Fonds bildet den Index über ein Tauschgeschäft (Swap) mit einer Bank ab.",
            "Die Preise der enthaltenen Rohstoffe, vor allem Energie, dazu die Kosten beim Wechsel der Terminkontrakte und der Zins auf das hinterlegte Geld.",
            "Als Sparplan oder als Beimischung im Depot. Ein Korb verteilt das Geld auf viele Rohstoffe.",
            "Rohstoffkörbe bringen keine laufenden Erträge wie Dividenden. Über lange Zeiträume lagen ihre Renditen meist deutlich unter denen von Aktien. Sie können aber in Jahren mit hoher Inflation helfen, etwa 2021 und 2022.",
        ),
        "MINEN" to listOf(
            "Ein Fonds mit Aktien von Unternehmen, die Gold fördern. Angelegt wird in Firmen, Gold selbst besitzt man damit nicht.",
            "Der Goldpreis wirkt mit Hebel: Die Förderkosten bleiben ungefähr gleich, ein höherer Goldpreis steigert den Gewinn deshalb überproportional. Minenaktien bewegen sich oft zwei- bis dreimal so stark wie Gold. Dazu kommen Kosten für Energie und Löhne, Führung, Bilanz und politische Risiken in den Förderländern.",
            "Als Beimischung neben physischem Gold oder einem Gold-ETC, nie als Ersatz dafür.",
            "Anders als Gold zahlen viele Minen Dividenden. In Krisen fallen Minenaktien manchmal mit dem Aktienmarkt, während Gold steigt.",
        ),
    )
}

internal object RohstoffTexteEn {
    val TITEL = listOf("What it is", "What moves the price", "How to invest", "Also")

    const val TERMIN = "The price shown is the front-month futures contract. Products on futures have to roll into the next contract regularly. If that contract is more expensive (contango), every roll costs return. Over years such products therefore often lag the price shown."

    val TEXTE: Map<String, List<String>> = mapOf(
        "GOLD" to listOf(
            "A precious metal that has served as a store of value for thousands of years. It is bought mainly for jewellery, by investors and by central banks; industry needs little. The price is quoted in US dollars per troy ounce (31.1 grams).",
            "Real interest rates matter most, meaning interest minus inflation. When they fall, gold becomes more attractive because it pays no interest itself. A weak dollar makes gold cheaper for buyers outside the US. Crises and uncertainty add to demand, as do central banks, which have bought more than 1,000 tonnes every year since 2022.",
            "Physically as bars or coins, through exchange-traded gold ETCs (one Xetra-Gold share equals one gram of gold with a right to delivery) or indirectly through gold mining shares, which swing more.",
            "Gold pays neither interest nor dividends; the return comes from the price alone. There have been long losing phases. From 1980 to 2001 gold lost about 80% of its value after inflation. In Germany, gains on physical gold and on ETCs with a right to delivery such as Xetra-Gold are tax-free after one year. Other ETCs are often taxed like ordinary securities.",
        ),
        "SILBER" to listOf(
            "A precious metal that is also an industrial metal: more than half of demand comes from industry, for example solar cells and electronics. The price is quoted in US dollars per troy ounce.",
            "Silver often follows gold but swings much more, because the economy matters too. Much silver is a by-product of mining other metals, so supply barely reacts to the price. A popular yardstick is the gold-silver ratio: how many ounces of silver one ounce of gold costs.",
            "Physically as coins or bars, through silver ETCs or through silver mining shares.",
            "In Germany physical silver carries VAT, gold does not, which makes small amounts of silver more expensive for private buyers. Like gold, silver pays no interest or dividends.",
        ),
        "PLATIN" to listOf(
            "A rare precious metal. A large share goes into catalytic converters, mainly for diesel cars, plus jewellery and industry.",
            "Supply is highly concentrated: about 70% comes from South Africa, where power cuts and strikes can disrupt mining. On the demand side, car production, emission rules and the shift to electric cars matter.",
            "Through platinum ETCs, physically as bars or through futures.",
            "The market is small, so the price swings a lot. Platinum was long more expensive than gold and has cost far less for years.",
        ),
        "PALLADIUM" to listOf(
            "A precious metal used almost only in catalytic converters for petrol cars.",
            "A large share comes from Russia and South Africa; sanctions and mining problems move the price. Electric cars need no catalytic converter and reduce demand in the long run.",
            "Through palladium ETCs or futures.",
            "One of the most volatile commodities: the price rose more than fivefold from 2016 to 2022 and then more than halved.",
        ),
        "OEL" to listOf(
            "Crude oil is the world's most important energy commodity. There are two benchmarks: Brent from the North Sea for Europe and WTI from the US. Both trade in US dollars per barrel (159 litres).",
            "Supply and demand: output of OPEC and its partners (OPEC+), US shale production, the world economy, inventories and conflicts in producing countries. A sharp rise in oil lifts inflation and costs for many companies.",
            "Private investors can reach it almost only through futures, meaning ETCs, certificates or leveraged products. An alternative is shares of oil companies, which also pay dividends.",
            "In April 2020 the WTI contract briefly fell to minus 37 US dollars: storage was full and nobody wanted to take delivery. It shows how much storage and contract dates shape the price.",
        ),
        "GAS" to listOf(
            "Natural gas for heating, power and industry. The price here is Henry Hub, the US price. Europe has its own market (TTF in the Netherlands), whose price often moves very differently.",
            "Weather and season (cold winters, hot summers), storage levels, production and liquefied gas exports (LNG).",
            "Almost only through futures, meaning ETCs and certificates. Roll costs between contracts are especially high for gas.",
            "One of the most volatile markets of all: daily moves of 10% happen. Gas ETCs have lost large parts of their value over many years despite temporary price spikes.",
        ),
        "KUPFER" to listOf(
            "The most important industrial metal for electricity: cables, motors, power grids, electric cars and construction. The price here is in US dollars per pound.",
            "Because copper is used almost everywhere, its price is seen as an economic barometer (“Dr. Copper”). China uses about half of the world's copper. It is mined mainly in Chile and Peru. New mines take many years to start production.",
            "Through copper ETCs or futures, indirectly through mining shares.",
            "The energy transition and power grids raise demand in the long run. A slowdown in China pushes the price down in the short run.",
        ),
        "WEIZEN" to listOf(
            "One of the most important staple foods. The price here is the Chicago futures contract, in US cents per bushel (about 27 kg).",
            "Weather and harvests in the big growing countries, exports from Russia and Ukraine, inventories and export bans. The war in Ukraine sent the price to a record high in 2022.",
            "Only through futures, meaning ETCs or agricultural commodity baskets.",
            "Agricultural prices follow the crop year and the weather, forecasts are hard. Higher grain prices hit poorer countries most.",
        ),
        "MAIS" to listOf(
            "Corn is used as feed, food and raw material for bioethanol. The price here is the Chicago futures contract, in US cents per bushel.",
            "Harvests in the US, Brazil and Argentina, weather during the growing season, the oil price (a large share of US corn becomes ethanol) and demand from China.",
            "Only through futures, meaning ETCs or agricultural commodity baskets.",
            "The US Department of Agriculture's crop reports often move the price sharply.",
        ),
        "KAFFEE" to listOf(
            "The price here is Arabica, the variety in most roasted coffee, in US cents per pound. The cheaper Robusta trades separately.",
            "Weather in Brazil, the largest producer (frost and drought), harvests in Colombia and Vietnam, freight costs and the Brazilian real.",
            "Only through futures, meaning ETCs or agricultural commodity baskets, indirectly through shares of coffee traders and roasters.",
            "Coffee plants take years until their first harvest, so after a crop failure the price often stays high for a while.",
        ),
        "KAKAO" to listOf(
            "The raw material for chocolate. The price here is the New York futures contract, in US dollars per tonne.",
            "About 60% of the crop comes from Côte d'Ivoire and Ghana. Weather, plant diseases and the fixed purchase prices of these countries determine supply.",
            "Only through futures, meaning ETCs or agricultural commodity baskets.",
            "In 2024 the price rose above 10,000 US dollars per tonne at times after poor harvests, several times the usual level. Chocolate became much more expensive.",
        ),
        "ZUCKER" to listOf(
            "Raw cane sugar. The price here is the New York futures contract, in US cents per pound.",
            "Harvests in Brazil, India and Thailand, India's export rules and the oil price: Brazil also turns sugar cane into ethanol for cars and shifts its crop depending on prices.",
            "Only through futures, meaning ETCs or agricultural commodity baskets.",
            "Agricultural commodities swing with the weather and often move independently of shares.",
        ),
        "KORB" to listOf(
            "A fund on a commodity index: energy, industrial metals, precious metals and, depending on the index, agriculture and livestock in one basket. The fund tracks the index through a swap with a bank.",
            "The prices of the commodities inside, above all energy, plus the cost of rolling futures and the interest on the collateral.",
            "As a savings plan or a small addition to a portfolio. A basket spreads the money across many commodities.",
            "Commodity baskets pay no ongoing income such as dividends. Over long periods their returns were mostly well below those of shares. They can help in years of high inflation, such as 2021 and 2022.",
        ),
        "MINEN" to listOf(
            "A fund holding shares of companies that mine gold. You invest in companies and do not own any gold.",
            "The gold price works with leverage: mining costs stay roughly the same, so a higher gold price lifts profits disproportionately. Mining shares often move two to three times as much as gold. Costs for energy and wages, management, balance sheets and political risk in mining countries add to that.",
            "As an addition next to physical gold or a gold ETC, never as a substitute for it.",
            "Unlike gold, many miners pay dividends. In crises mining shares sometimes fall with the stock market while gold rises.",
        ),
    )
}
