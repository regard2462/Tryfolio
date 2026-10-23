# Broker-Anbindung (seit 4.6)

Im Profil unter „Broker verbinden“ lässt sich ein eigenes Konto bei einem Broker über dessen offizielle Schnittstelle verbinden. Die App zeigt dann das echte Depot, und Orders gehen mit dem Schlüssel des Nutzers direkt vom Gerät an den Broker. Es gibt keinen Server dazwischen.

## Ablauf in der App

1. Broker wählen.
2. Hinweise und Haftung lesen und bestätigen; beim echten Konto zusätzlich bestätigen, dass mit echtem Geld gehandelt wird.
3. Übungsdepot als Datei sichern (es wird ersetzt).
4. Schlüssel eingeben. Die App prüft ihn, lädt das Konto und ersetzt das Übungsdepot vollständig: Bargeld, Positionen, Orders, Umsätze, Sparpläne, Wetten. Watchlist und Preisalarme bleiben.

Im verbundenen Zustand:
- Depot, Orders und Umsätze werden alle 15 Sekunden mit dem Broker abgeglichen.
- Suche, Kurse und Charts kommen vom Broker. Werte, die der Broker nicht führt (Indizes, Währungen), zeigt die App weiter mit Kursen von Yahoo Finance an; handeln lassen sie sich nicht.
- Offene Orders lassen sich ändern, soweit der Broker das erlaubt: Alpaca (Stückzahl, Preis, Gültigkeit), Tradier (Preis und Gültigkeit, nicht die Stückzahl), Kraken (Stückzahl und Preis, nicht die Gültigkeit). comdirect (Preis und Gültigkeit, nicht die Stückzahl), Interactive Brokers (alles). Bei Trading 212 und Robinhood nur stornieren und neu aufgeben.
- Einzahlen, Sparpläne, Wetten, Import und die simulierten Derivate sind gesperrt.
- Auf der Startseite steht „Echtes Konto · Broker“ bzw. „Papierkonto · Broker“.
- „Trennen“ löscht die Schlüssel auf dem Gerät und legt ein neues Übungsdepot an. Ein gesichertes Übungsdepot lässt sich danach wieder importieren.

## Eingebaute Broker

| Broker | Anmeldung | Angebot | Papierkonto | Kurse und Charts | Stand |
|---|---|---|---|---|---|
| Alpaca | Schlüssel-ID und Secret (Kopfzeilen) | US-Aktien, ETFs, Krypto; USA und viele weitere Länder | ja | vom Broker (IEX-Feed, kostenlos) | nach Doku gebaut, nicht mit echtem Schlüssel getestet |
| Tradier | Access Token (Bearer) | US-Aktien und ETFs; vor allem USA | Sandbox | vom Broker (Sandbox 15 Min. verzögert) | nach Doku gebaut, nicht getestet |
| Trading 212 | Schlüssel und Secret (HTTP Basic) | Aktien und ETFs; DE, EU, UK | Demokonto | **nicht** über die Schnittstelle, weiter Yahoo Finance; Zuordnung der Kürzel über die ISIN | Schnittstelle in der Beta, Feldnamen teils unsicher; nicht getestet |
| Kraken | API-Schlüssel und privater Schlüssel (HMAC-SHA512) | Kryptowerte in EUR und USD; DE, EU, USA | nein | vom Broker | nach Doku gebaut, nicht getestet |
| Interactive Brokers | keine Schlüssel: Client Portal Gateway auf dem eigenen Rechner, Anmeldung im Browser; die App vertraut nur dem bestätigten Zertifikat des Gateways | Aktien und ETFs an Börsen weltweit; fast alle Länder | ja (Anmeldung am Gateway entscheidet) | **nicht** über die Schnittstelle (Marktdaten-Abo nötig), weiter Yahoo Finance; Kontrakte über Kürzel und Börse | nach der Client Portal Web API v1 gebaut, nicht getestet |
| comdirect | Client-ID und Secret (Entwicklerzugang), Zugangsnummer und PIN; jede Sitzung mit TAN-Freigabe (photoTAN-Push, photoTAN oder mobileTAN) | Aktien, ETFs und Fonds an deutschen Börsen (Xetra bevorzugt), Limits in Euro; Deutschland | nein | **nicht** über die Schnittstelle, weiter Yahoo Finance; Zuordnung über die ISIN | nach der Schnittstellenspezifikation (April 2020) gebaut, nicht getestet |
| Robinhood | API-Schlüssel und eigener privater Ed25519-Schlüssel (Signatur je Anfrage) | nur Kryptowerte in USD (Crypto Trading API); nur USA | nein | **nicht** über die Schnittstelle, weiter Yahoo Finance | nach Doku gebaut, nicht getestet; Aktien nur über einen KI-Zugang (MCP), nicht eingebaut |

Keiner der Broker konnte aus der Entwicklungsumgebung mit echten Schlüsseln geprüft werden. Vor der ersten echten Order mit dem Papier- bzw. Demokonto testen.

## Nicht möglich

Ohne offizielle Schnittstelle für Privatkunden: Trade Republic (die Bedingungen erlauben keinen anderen Zugang als die eigenen Apps; die Umsätze lassen sich aber als CSV-Export importieren, Profil → Portfolio importieren), ING, DKB, flatex/DEGIRO, XTB (Schnittstelle am 14.03.2025 eingestellt), Binance in der EU (seit 07/2026 eingeschränkt), lemon.markets (nur B2B). Inoffizielle Wege (z. B. pytr für Trade Republic) können gegen die Bedingungen der Broker verstoßen und werden nicht unterstützt.

Machbar, aber noch nicht eingebaut:
- Smartbroker+: Schnittstelle seit Mai 2026, Dokumentation nur auf Anfrage per E-Mail; 29,90 € im Monat ohne VIP-Status.
- Scalable Capital: nur ein MCP-Zugang für KI-Assistenten (OAuth 2.1 mit PKCE), Orders mit Vorschau und Bestätigung. Parameter und Ausgaben der Werkzeuge sind nicht veröffentlicht; ohne sie ließe sich eine Anbindung nur raten (für echtes Geld nicht vertretbar).
- Consorsbank: die REST-Schnittstelle nur für Partner (mTLS); ActiveTrader mit gRPC auf dem eigenen Rechner, dessen Schnittstellenbeschreibung (proto-Dateien) nicht öffentlich ist.
- Außerdem tastytrade (eigene OAuth-App, Kurse nur über einen WebSocket), eToro (Endpunkte noch im Umbau), Charles Schwab (eigene Entwickler-App mit wöchentlicher Neuanmeldung), Saxo, IG und Capital.com (CFDs), Bitvavo, Bitpanda Fusion, Coinbase.

## Interactive Brokers: Client Portal Gateway

- Der Nutzer startet das Gateway von IBKR (Java) auf seinem Rechner und meldet sich im Browser an (https://localhost:5000, mit zweitem Faktor). Am selben Rechner ist die Adresse `localhost:5000`; vom Handy die Adresse des Rechners im Heimnetz, dazu in `root/conf.yaml` unter `ips: allow` das Heimnetz erlauben.
- Beim Verbinden zeigt die App den SHA-256-Fingerabdruck des selbst signierten Gateway-Zertifikats; nach der Bestätigung nimmt `BrokerNetz` nur noch genau dieses an (und nur diesen Host).
- Die App hält die Sitzung mit `/tickle` wach; IBKR verlangt etwa täglich eine neue Anmeldung im Browser. Ohne Anmeldung meldet die App, wo man sich anmeldet.
- Orders über die Kontrakt-ID (Suche nach Kürzel und Börse: US-Börsen, Xetra, London, Paris und weitere); Market, Limit, Stop, Trailing-Stop in Prozent. Rückfragen von IBKR (Warnungen) bestätigt die App bis zu fünfmal und zeigt ihre Texte nach dem Senden.

## comdirect: Freigabe mit TAN

- Anmeldung in fünf Schritten nach der Spezifikation: Token mit Zugangsnummer und PIN, Session abrufen, TAN-Challenge, Session aktivieren, Token „cd_secondary“. Die Session-TAN gilt danach für alle Orders der Sitzung (Validierung meldet „TAN_FREI“; kommt etwas anderes, sendet die App die Order nicht).
- Eine TAN-Challenge fordert die App nur an, wenn der Nutzer auf „TAN anfordern“ tippt, nie beim Start oder Abgleich. Je App-Sitzung höchstens 3 Anforderungen und 2 falsche TANs (comdirect sperrt das ganze Onlinebanking ab 5 unbestätigten Challenges bzw. 3 falschen TANs).
- Der Token gilt zehn Minuten und wird verlängert, solange die App läuft. Laut comdirect muss das mit dem Beenden der App aufhören; nach einem Neustart (oder nach etwa 20 Minuten im Hintergrund) braucht es eine neue Freigabe. Bis dahin zeigt die App den letzten Stand, Orders sind gesperrt. „Trennen“ widerruft den Token.
- Orders: Ordertypen Market, Limit und Stop (Stop-Market); „bis auf Widerruf“ wird zu „gültig bis“ in 360 Tagen. Kosteninformationen und Basisinformationsblätter zeigt die App nicht.

## Schlüssel

- Android: AES-GCM mit einem Schlüssel aus dem Android-Keystore, der ab Android 9 nur bei entsperrtem Gerät nutzbar ist; die Dateien liegen im privaten Bereich der App, Sicherungen sind ausgeschlossen (`allowBackup=false`).
- Windows/Linux: AES-GCM mit einem zufälligen Schlüssel in einer Datei, die nur der eigene Benutzer lesen darf (der Datenordner ebenso). Schützt vor dem Mitlesen der Einstellungen, nicht vor Programmen unter demselben Benutzer. Besser wäre der Schlüsselbund des Betriebssystems (offen).
- Die App empfiehlt je Gerät einen eigenen Schlüssel mit möglichst wenig Rechten (ohne Auszahlung), wo möglich an die eigene IP gebunden, regelmäßig erneuert, zuerst im Papierkonto, und das Widerrufen beim Broker nach dem Trennen.
- Schlüssel stehen nie in Sicherungsdateien und werden nirgends protokolliert.

## Schutz in der App

Die App lädt Kurse, Nachrichten, Artikel und Dokumente von vielen Websites. Eine davon könnte kompromittiert sein. Damit daraus kein Zugriff auf die Schlüssel wird:

- Verbindungen zum Broker laufen über einen eigenen Weg (`BrokerNetz`), der nur die Adressen des jeweiligen Brokers erreicht, nur über HTTPS, ohne Weiterleitungen und mit begrenzter Antwortgröße.
- Doppelte Orders werden 20 Sekunden lang abgefangen. Solange ein Broker eingetragen, aber noch nicht wieder verbunden ist, bucht die App nichts wie im Übungsdepot.
- Antworten von Websites werden begrenzt (Text 16 MB, entpackte PDF- und ZIP-Inhalte 32 bzw. 64 MB), Logos nur als PNG oder JPEG bis 2048 Pixel angenommen.
- Links öffnen nur `http`/`https` im Browser, andere Schemata (Intents, Dateien, Apps) werden ignoriert. Der Artikel-Leser auf Android hat keinen Zugriff auf Dateien und Inhalte, keinen Standort und verlässt die Seite nicht für fremde Schemata.
- Desktop: Die eingebettete Seite für MarketWatch-Kurse erlaubt nur MarketWatch und die Captcha-Dienste, keine Pop-ups und keine Downloads. Mit verbundenem Broker wird sie gar nicht mehr geladen. Chromium läuft dort ohne eigene Sandbox; das bleibt eine Restgefahr.
- Auf dem Broker-Bildschirm sind Screenshots gesperrt, bei verbundenem echtem Konto zeigt Android in der App-Übersicht keine Vorschau.
- Im Übungsdepot bleibt alles uneingeschränkt.
- App-Sperre (Profil › App-Sperre, Standard aus): PIN (4 bis 8 Ziffern) oder Passwort (mindestens 8 Zeichen). Das Konto (`account.json`) liegt dann mit AES-256-GCM verschlüsselt auf dem Gerät; der zufällige Datenschlüssel steht in `sperre.json`, verpackt mit einem Schlüssel aus PIN oder Passwort (PBKDF2-HMAC-SHA256, 310.000 Runden). Gesperrt wird beim Start und nach einer Minute im Hintergrund; ab dem fünften Fehlversuch wächst die Wartezeit. Solange die Daten nicht entsperrt sind, bucht und schreibt die App nichts. Wer PIN und Passwort vergisst, kann nur neu beginnen. Einstellungen sind nicht verschlüsselt.
- Widgets (Android): Alle sechs Widgets (Depot, Werte, Einzelwert, Top & Flop, Dividenden, Schnellzugriff) zeigen mit eingeschalteter App-Sperre keine Beträge; ein Tipp öffnet nur bekannte Seiten der App (Suche, Orders, Heatmap, News, Wertpapier), mit Sperre erst nach dem Entsperren.

Was die App nicht verhindern kann: Schadsoftware, die das Gerät selbst übernommen hat.

Die GitHub-Abläufe nutzen Actions mit festen Commit-Hashes. Mit den Secrets `TROETE_KEYSTORE` und `TROETE_STORE_PW` baut der APK-Ablauf eine signierte, nicht debugbare Release-APK (siehe `.github/workflows/apk.yml`).

## Recht (Orientierung, keine Rechtsberatung)

**Erlaubnispflicht (KWG/WpIG, MiCAR, USA).** Für eine kostenlose, quelloffene App, über die Nutzer mit ihrem eigenen Schlüssel direkt bei ihrem eigenen Broker handeln (ohne Server, ohne Vergütung, ohne Beratung), spricht viel gegen eine Erlaubnispflicht als Anlage- oder Abschlussvermittlung: Die Entwickler nehmen keine Aufträge entgegen und leiten keine weiter. Eine ausdrückliche Aussage der BaFin zu solcher Software gibt es aber nicht. Die Einschätzung kann kippen durch einen Server oder Proxy in der Orderkette, durch Vergütungen (Provisionen, Affiliate, eventuell Spenden), durch Empfehlungen, Signale oder automatische Strategien und durch einen Betrieb mit Gewinnabsicht. Copytrading-Funktionen der App und die Broker-Anbindung sollten deshalb getrennt bleiben (im echten Konto ist „Nachbilden“ gesperrt).

**Haftung.** Ein Hinweis kann die Haftung nur so weit begrenzen, wie das Gesetz es erlaubt:
- Nicht ausschließbar sind die Haftung für Vorsatz (§ 276 Abs. 3 BGB) und, in vorformulierten Bedingungen, die für grobe Fahrlässigkeit sowie für Schäden an Leben, Körper oder Gesundheit (§ 309 Nr. 7 BGB); dazu das Produkthaftungsgesetz.
- Bei kostenloser Open-Source-Software haftet der Schenker ohnehin nur für Vorsatz und grobe Fahrlässigkeit (§ 521 BGB).
- Der Hinweis in der App formuliert den Ausschluss deshalb „soweit gesetzlich zulässig“ und nennt diese Ausnahmen ausdrücklich. Er dokumentiert die Zustimmung (Zeitpunkt in den Einstellungen).

**Vor der Veröffentlichung von einer Anwältin oder einem Anwalt prüfen lassen:**
1. Erlaubnispflicht nach KWG/WpIG und MiCAR für genau diese App (gegebenenfalls Anfrage bei der BaFin).
2. Wirksamkeit und Wortlaut des Haftungshinweises.
3. Datenschutz: Datenschutzerklärung (im Play Store Pflicht), Rolle der Entwickler ohne eigene Datenverarbeitung, Drittdienste wie Yahoo Finance.
4. Ausnahmen für Open Source in der neuen Produkthaftungsrichtlinie (EU) 2024/2853 und im Cyber Resilience Act; was als „kommerziell“ gilt.
5. Bedingungen der einzelnen Broker für Drittsoftware.
6. Google Play: Erklärung zu Finanzfunktionen („Stock trading and portfolio management“), für Krypto in manchen Ländern Lizenznachweise.

Quellen der Recherche (Stand 19.10.2026) u. a.:
- [BaFin-Merkblatt Anlagevermittlung](https://www.bafin.de/SharedDocs/Veroeffentlichungen/DE/Merkblatt/mb_091204_tatbestand_anlagevermittlung.html)
- [BaFin zu automatisierten Handelssystemen](https://www.bafin.de/DE/unternehmen-maerkte/erlaubnis-registrierung/fintech/beratungs-handelssysteme/beratungs-handelssysteme.html)
- Dokumentationen der Broker: [Alpaca](https://docs.alpaca.markets/docs/authentication), [Tradier](https://docs.tradier.com/docs/endpoints), [Trading 212](https://docs.trading212.com), [Kraken](https://docs.kraken.com/api/docs/guides/spot-rest-auth), [Robinhood](https://docs.robinhood.com/crypto/trading/)
- [Ende der XTB-Schnittstelle](https://www.xtb.com/en/help-center/our-platforms/does-xtb-offer-investment-automation-tools)
