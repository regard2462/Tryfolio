# US-Fassung des Finanzwissens (Oktober 2026)

Wer in der App als Land die USA wählt, bekommt im Finanzwissen bei 43 Lektionen eine eigene US-Fassung statt der deutschen. Angepasst wurde nur, was in den USA anders ist: Steuern, Altersvorsorge, Versicherungen, Immobilien, Depot und Broker, Kreditauskunft, Erbrecht. Alle anderen Lektionen (Wirtschaft, Märkte, Kennzahlen, Psychologie und so weiter) bleiben unverändert.

In der App ist das sichtbar: Jede angepasste Lektion trägt oben den Kasten „Für die USA angepasst“ mit einem Satz, was anstelle der deutschen Regeln behandelt wird, und einem Knopf „Deutsche Fassung lesen“. Die Startseite des Finanzwissens zeigt den Abschnitt „Deine Region“ mit allen angepassten Lektionen. Die Daten liegen unter `app/src/main/assets/finanzwissen/data/us/` (deutsch) und `data/en/us/` (englisch), die Übersicht in `data/regional.json`.

Rechtsstand ist 2026, beschrieben wird das Bundesrecht. Die Regeln der einzelnen Bundesstaaten werden nur dort erwähnt, wo sie für das Verständnis nötig sind.

## Angepasste Lektionen

| Lektion | Titel der US-Fassung |
|---|---|
| m02-l05 | Notwendige und verzichtbare Versicherungen in den USA |
| m02-l07 | Brutto, Netto und Grenzsteuersatz in den USA |
| m02-l09 | Kreditauskunft, Credit Score und finanzielle Ordnung in den USA |
| m02-l11 | Partnerschaft, Kinder und Erbe in den USA |
| m04-l02 | Depot und Broker in den USA |
| m04-l03 | Die erste Order bei einem US-Broker |
| m04-l09 | Kosten einer Wertpapierorder in den USA |
| m04-l11 | Das Geschäftsmodell der US-Finanzbranche |
| m05-l09 | Geldmarkt und Cash in den USA |
| m09-l05 | Ausschüttungen, Wiederanlage und Steuerlast |
| m09-l06 | Einen ETF in den USA auswählen |
| m09-l07 | Automatisch investieren und durchhalten |
| m14-l09 | Steuern und Praxis beim Trading in den USA |
| m16-l01 | Grundlagen der US-Steuer auf Kapitalerträge |
| m16-l02 | Die 0-%-Stufe und steuerbegünstigte Konten |
| m16-l03 | Wie US-Fonds und ETFs besteuert werden |
| m16-l04 | Kursverluste verrechnen |
| m16-l05 | Lot-Auswahl, Depotübertrag und Steuerstundung |
| m16-l06 | Ausländische Steuern und Konten im Ausland |
| m16-l07 | Kapitalerträge erklären: Formulare 1099, 8949 und Schedule D |
| m16-l08 | Krypto, Gold und Sammlerstücke |
| m16-l09 | Steuern rund um Immobilien |
| m16-l10 | Wertpapierdepots vererben und verschenken |
| m17-l01 | Der dreibeinige Hocker |
| m17-l02 | Grundlagen der Social Security |
| m17-l03 | Traditional und Roth IRA |
| m17-l04 | 401(k), 403(b) und der Arbeitgeber-Match |
| m17-l05 | Annuity oder Depot |
| m17-l06 | Finanzbedarf im Ruhestand |
| m17-l07 | Entnahmestrategien |
| m17-l08 | Sofortrente, Auszahlplan, Kombination |
| m17-l09 | Pflege und Krankenversicherung im Alter |
| m17-l10 | Einen Altersvorsorgeplan aufstellen |
| m17-l11 | Jenseits von IRA und 401(k) |
| m18-l01 | Kaufen oder mieten in den USA |
| m18-l02 | Die Kosten eines Hauses in den USA |
| m18-l03 | Hypotheken in den USA |
| m18-l04 | Variabel oder fest: Zinsrisiko und Stresstest |
| m18-l05 | Vermietete Immobilie als Kapitalanlage in den USA |
| m18-l06 | Steuern auf vermietete Immobilien |
| m18-l07 | Condos, HOAs und Co-ops |
| m18-l08 | REITs und andere indirekte Immobilienanlagen |
| m18-l09 | Daten zum US-Immobilienmarkt |

## Geprüft

- Jede Rechnung, Tabelle und jeder Diagrammwert wurde mit Python nachgerechnet.
- Jede Datei hat dieselbe Kennung, Gliederung und Stufe wie die deutsche Lektion und nutzt nur Bausteine, die es dort schon gibt; alle Glossar-Verweise führen zu vorhandenen Einträgen.
- Alle Lektionen wurden im Browser in beiden Sprachen angezeigt, ohne Fehler.

## Unsicher (bitte nachprüfen)

Diese Angaben stammen aus Fachwissen und konnten nicht vollständig gegen eine Quelle geprüft werden, meist Werte für 2026, die nach Redaktionsschluss der Wissensbasis veröffentlicht wurden. Wo ein Wert nur eine Annahme ist, steht das in der Lektion selbst.

- m02-l05: SGA-Grenze 2026 (1.690 $/Monat), HSA-Grenzen 2026 (4.400/8.750 $, Rev. Proc. 2025-19), Umfang der ACA-Zuschüsse 2026 (nur allgemein beschrieben), BU-Prämie „1–3 % des Einkommens“, LTD „typisch 60 %“, SSA-Aussage „1 zu 4“ für 20-Jährige, Mandatsstaaten (CA, MA, NJ, RI, DC), Staaten mit Kurzzeit-BU (CA, HI, NJ, NY, RI), NFIP-Wartezeit 30 Tage; Prämien im Beispiel als Annahmen gekennzeichnet.
- m02-l07: Steuerstufen und Standardabzug 2026 (Rev. Proc. 2025-32), Social Security Wage Base 2026 (184.500 $), Spendenabzug ohne Einzelabzüge ab 2026 (1.000/2.000 $), SALT-Obergrenze nur ohne Zahl beschrieben.
- m02-l09: Kreditkartenzinsen „häufig über 20 %“, Vergleichszeitraum 14–45 Tage, Meldung medizinischer Inkassoforderungen, wöchentliche Gratis-Auskunft, Löschfristen Chapter 13 (7 Jahre) und geschlossene Konten (10 Jahre) als Praxis der Auskunfteien.
- m02-l11: Freibetrag 15 Mio. $ und Jahresfreibetrag 19.000 $ (2026), Freibetrag für Ehepartner ohne US-Staatsbürgerschaft und Kiddie-Tax-Grenzen mit Werten 2025, UPC-Erbteil (300.000 $ plus drei Viertel), Nachlasssteuern einzelner Staaten (PA 15 %, MA 2 Mio. $, OR 1 Mio. $), FAFSA-Anrechnung (20 %/5,64 %), Details der Trump Accounts (§ 530A).
- m04-l02: 0-%-Schwelle 2026 (49.450/98.900 $), Schenkungsfreibetrag 2026 (19.000 $), Rev. Proc. 2025-32 als Fundstelle, Synapse-Insolvenz 2024 (allgemein gehalten), ACATS-Fristen (1 + 3 Geschäftstage).
- m04-l02, m04-l09: Die Rechner „kosten“ und „inflation“ zeigen weiterhin €.
- m04-l03: Schwellen für Clearly Erroneous Executions (30 Minuten, 10 % bis 25 $), NYSE Rule 7.12 als Fundstelle, Pre-Market ab 4:00, Nachthandel 20:00–4:00 bei einigen Brokern.
- m04-l09: Ausweitung von Rule 605 im Jahr 2024, ADR-Gebühren „einige Cent je Aktie“, Umtauschgebühr 0,25 % als Annahme.
- m04-l11: Surrender Charge „oft rund 7 % im ersten Jahr“, DOL-Regel 2024 „von Gerichten ausgesetzt“, FINRA-Obergrenze für Ausgabeaufschläge 8,5 %, Fundstellen zu Form CRS und Release-Nummern.
- m05-l09: kein aktuelles Zinsniveau genannt (Beispiele rechnen mit gekennzeichneten Annahmen), Treuhandkonten-Regel seit April 2024, FDIC-Auszahlung „meist binnen weniger Geschäftstage“.
- m09-l05: Grenzen 2026 der 0-/15-%-Stufe und Standardabzug (Rev. Proc. 2025-32), dauerhafter § 199A-Abzug, „viele Broker nutzen standardmäßig FIFO“.
- m09-l06: Kostenspanne 0,03–0,10 % ohne Einzelwerte, US-Anteile aus Fondsdaten statt Indexdaten, Vanguard-Indexwechsel 2012/13 „wegen Lizenzkosten“, Spread „oft 1–2 Cent“.
- m09-l07: Höchstbeträge 2026 (401(k) 24.500 $, IRA 7.500 $), Zuordnung Auto-Enrollment zu § 414A, Ausgabeaufschlag „oft bis zu 5,75 %“, Mindestbeträge nur als Spanne. Nachgerechnet: Vorteil wöchentlicher Käufe rund 580 $ (das deutsche Original nennt rund 560 $).
- m14-l09: dauerhafte Aussetzung der Miscellaneous Itemized Deductions, Jahre der Covered Securities (2011/2012/2014), kalifornischer Höchstsatz 13,3 %, Trader-Status nur allgemein; Pattern-Day-Trader-Regel wegen laufender Reform bewusst weggelassen.
- m16-l01: Grenzen 2026 der 0-/15-%-Stufe (49.450/545.500 $ ledig, 98.900/613.700 $ gemeinsam), Rev. Proc. 2025-32 als Fundstelle, dauerhafte Streichung der Miscellaneous Itemized Deductions durch Public Law 119-21, Vorzugssatz seit 1921.
- m16-l02: Standardabzug 2026 (16.100/32.200 $), Höchstbeträge 2026 (401(k) 24.500 $, IRA 7.500 $, HSA 4.400/8.750 $), Schenkungsfreibetrag 19.000 $, Kiddie-Tax-Grenzen mit Werten 2025, Senioren-Abzug 6.000 $ (2025–2028).
- m16-l03: PFIC-Beispiel vereinfacht (Annahmen gekennzeichnet), Anteil qualifizierter Dividenden 95 % als Annahme.
- m16-l04: Wash-Sale-Regel seit 1921, 3.000-$-Grenze seit 1978, Inflationsvergleich rund 14.000 $ (2024).
- m16-l05: Lebensfreibetrag 15 Mio. $ (2026), Zeitpunkt der Einzelzuordnung von Tranchen.
- m16-l06: Schwellen für Covered Expatriates (Werte 2025: 206.000 $ / 890.000 $), Standardabzug 2026, Schwellen für Form 8938, Schweiz behält in der Regel die vollen 35 % ein; Leckage-Beispiel als Annahme.
- m16-l06, m16-l07: Die Rechner „abgeltungsteuer“ und „netto“ entfallen in der US-Fassung.
- m16-l07: Grenzen 2026 der 0-/20-%-Stufe, Steuerstufen 2026 im Diagramm, genaue Fristdaten 2028/2029 („Mitte April“), Nicht-Abziehbarkeit von Anlagekosten „nach geltendem Recht“.
- m16-l08: Wash-Sale-Regel gilt weiterhin nicht für direkt gehaltene Kryptowerte, Form 1099-DA (Erlöse ab 2025, Anschaffungskosten ab Erwerb 2026), Aufhebung der DeFi-Meldepflicht 2025, Zuordnung je Wallet seit 1. Januar 2025.
- m16-l09: SALT-Grenze (40.000 $ 2025, 40.400 $ 2026, Kürzung ab 500.000/505.000 $, 10.000 $ ab 2030), 100 % Bonus-Abschreibung nach dem 19.01.2025, Ende von § 25C/§ 25D; Zins 6,5 % als Annahme.
- m16-l10: Freibetrag 15 Mio. $ (2026, ab 2027 indexiert), Jahresfreibetrag 19.000 $, Erbschaftsteuern der Bundesstaaten (etwa ein Dutzend plus DC; OR 1 Mio. $, MA 2 Mio. $; PA 0/4,5/12/15 %), Pflichtentnahmen innerhalb der Zehnjahresfrist.
- m17-l01: Grenzen 2026 (Beitragsgrenze 184.500 $, 401(k) 24.500 $ + 8.000 $, IRA 7.500 $ + 1.100 $), Abzug ab 65 (6.000 $, 2025–2028, Abschmelzen ab 75.000/150.000 $), Treuhänderbericht 2025 (2034, 81 %), URL ssa.gov/history/stool.html.
- m17-l02: Knickpunkte 1.286/7.749 $ und Lohnindex 2024, Credit je 1.890 $, Hinzuverdienstgrenzen 24.480/65.160 $, Höchstleistung 4.152 $, Part-B-Beitrag 202,90 $, COLA 2,8 %, Aufteilung OASI 10,6 % / DI 1,8 %, Treuhänderbericht 2025 (OASI 2033 mit 77 %).
- m17-l03: Abschmelzbereiche 2026 für Traditional und Roth IRA, Insolvenzschutz nur als „mehr als 1,5 Mio. $“.
- m17-l03, m17-l04: Die Rechner „abgeltungsteuer“ und „netto“ entfallen in der US-Fassung.
- m17-l04: Grenzen 2026 (24.500/8.000/11.250/72.000 $, Gehaltsgrenze 360.000 $), Schwelle der Roth-Pflicht für Nachholbeiträge (150.000 $), Kostenbeispiel des Arbeitsministeriums (227.000 gegen 163.000 $, Wortlaut nicht geprüft), Übertragung kleiner Guthaben bis 7.000 $.
- m17-l05: Grenzen 2026 der 0-/20-%-Stufe, Höchstbeträge 2026 (401(k) 24.500 $ + 8.000 $ bzw. 11.250 $ mit 60–63, IRA 7.500 $ + 1.100 $), Rev. Proc. 2025-32 als Quelle, typischer Surrender-Verlauf laut SEC, Rider-Kosten „oft 1 % oder mehr“, Tabelle V mit Faktor 20,0; Kosten 0,45 % und 2,25 % als Annahmen.
- m17-l06: Knickstellen 1.286/7.749 $, Beitragsbemessungsgrenze 184.500 $, COLA 2,8 %, Earnings Test 24.480/65.160 $, Pflegeheimkosten „deutlich über 100.000 $ pro Jahr“; Part-D- und Medigap-Beiträge als Annahmen. Der Rechner „rente“ entfällt in der US-Fassung.
- m17-l07: Standardabzug 2026 (16.100/32.200 $), Seniorenabzug 6.000 $ mit Abschmelzung ab 75.000/150.000 $.
- m17-l08: Sofortrente von rund 631 $ je 100.000 $ ist ein Modell mit eigenen Annahmen, kein Marktangebot; Lebenserwartung mit 65 „rund 18–21 Jahre“, Sicherungsgrenze „vielerorts mindestens 250.000 $“, QLAC-Grenze 210.000 $ (Wert 2025), SECURE Act §§ 203/204, Tabelle V.
- m17-l09: Medicare-Werte 2026 (Part A 1.736 $, Part B 283 $, SNF 217 $/Tag, Part D 2.100 $), IRMAA-Schwellen und -Stufen, „fast 70 %“ Pflegewahrscheinlichkeit, Heimkosten 9.000–11.000 $/Monat.
- m17-l10: OASI-Fonds 2033 mit 77 % (Treuhänderbericht 2025), Korrekturfrist für den Verdienstverlauf „etwa drei Jahre“, Postversand des Statements ab 60, „gut jeder vierte 20-Jährige“, Grenzen 2026 wie m17-l05.
- m17-l11: Saver's-Credit-Schwellen 2026, Abschmelzen des Saver's Match, HSA-Grenzen und Mindestselbstbeteiligung, Eignung von Bronze- und Katastrophentarifen ab 2026, Details der Trump Accounts, Self-Employment Tax vereinfacht gerechnet.
- m18-l01: Standardabzug 2026 für Ehepaare (32.200 $), SALT-Obergrenze ohne Betrag, 750.000-$-Grenze für den Zinsabzug dauerhaft, Hypothekenzinsen 2023–2025 „meist zwischen etwa 6 und knapp 8 %“, Case-Shiller-Rückgang „rund ein Viertel“, Spannen für Closing Costs und Grundsteuer; Zins 6,25 % als Annahme.
- m18-l01 bis l05: Die Rechner (kaufenMieten, kredit, budget, mietrendite) zeigen weiterhin €, der Regler für Kaufnebenkosten beginnt bei 5 %.
- m18-l02: Auslaufen § 25C/§ 25D nach 2025, Florida nach Surfside nur allgemein, historische Provisionen 5–6 %, URLs der CFPB- und EPA-Seiten; Kostenpositionen und Lebensdauern als Annahmen.
- m18-l03: Conforming Loan Limit 2026 (832.750 $), PMI-Faustwert 30–70 $ je 100.000 $, FHA-Prämien und 3,5 % ab Score 580, Mindestanzahlung 3 %/5 %; Wirkung eines Points als Annahme. Das Zinskurven-Diagramm entfällt in der US-Fassung.
- m18-l04: Caps 2/1/5 als übliche Struktur, Hinweisfrist 210–240 Tage, Arbeitslosenleistungen „meist bis zu 26 Wochen“, SSDI-Wartezeit fünf Monate, Rekordtiefs 2020/21 „nahe 3 %“.
- m18-l05: Fannie Mae setzt 75 % der Bruttomiete an, Mindestanzahlung bei Investorenkrediten „oft 15–25 %“, Faustwerte für Leerstand und Verwaltung; Steuersatz 22 % als Annahme.
- m18-l06: Steuertarif 2026 (37 % ab 640.600/768.700 $), 100 % Bonus-Abschreibung für Erwerb nach dem 19.01.2025, Mietobergrenzen Oregon (7 % plus Inflation, höchstens 10 %) und Kalifornien AB 1482, § 183 seit 2018 praktisch nicht abziehbar, New Jersey als Beispiel für „just cause“.
- m18-l07: SALT-Obergrenze 2026 (40.400 $, gekürzt ab 505.000 $), Floridas Regeln nach Surfside nur qualitativ, Fannie-Mae-Anforderungen (10 % Rücklagen, 15 % Rückstände), Kalifornien § 5605/§ 5550, Lizenzpflicht für Verwalter in Florida, Auslaufen § 25C/§ 25D nach 2025, „Super Lien“ ohne Zahl.
- m18-l08: BREIT-Rücknahmebeschränkung Dezember 2022 bis etwa Anfang 2024, REITs 2022 „rund ein Viertel“ im Minus, typische Rücknahmegrenzen (2 % je Monat, 5 % je Quartal), Reg-CF-Grenzen (2.500/124.000 $), Reg A Tier 2 (75 Mio. $), Haltedauer über 45 Tage für § 199A, Selbstverwaltung seit dem Tax Reform Act 1986.
- m18-l08: Der Rechner „abgeltungsteuer“ entfällt in der US-Fassung.
- m18-l09: PMMS-Jahresdurchschnitte 2019–2023 sowie Wochentief und -hoch aus dem Gedächtnis, Case-Shiller −27 % (2006–2012), 0-%-Grenze 2026, Zins 6,5 % im Diagramm als Annahme, Months' Supply 6 als Faustregel, Prop 13 mit 2 %.
- Mehrere Quellen nur mit Domain angegeben (CMS-Faktenblatt 2026, Rev. Proc. 2025-32, Saver's Credit, SSA-Faktenblatt).

## Rechner in den US-Fassungen

Rechner, die mit deutschem Steuerrecht rechnen (Abgeltungsteuer, Netto, Rente), fehlen in den US-Fassungen. Die übrigen (Kosten, Inflation, Zinseszins, Kredit, Kaufen oder Mieten, Budget, Mietrendite, Entnahme, Sparziel) sind geblieben, zeigen aber weiterhin Euro.
