package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.77, Teil AD6: klinische Studien (ClinicalTrials.gov), Fahrzeugrueckrufe (NHTSA), Produktrueckrufe (CPSC).
// Die Antworten sind gekuerzte Ausschnitte der echten Schnittstellen.
class Fassung077Ad6RegisterTest {

    // ---------------------------------------------------------------- Studien

    private val studien = """{"totalCount":455,"studies":[
{"protocolSection":{"identificationModule":{"nctId":"NCT07595549","briefTitle":"A Study to Learn About the Study Medicine Called Berobenatide (PF-08653944) in People With Overweight or Obesity"},"statusModule":{"overallStatus":"RECRUITING","primaryCompletionDateStruct":{"date":"2028-05-04"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"Pfizer"}},"conditionsModule":{"conditions":["Obesity","Overweight and/or Obesity","Overweight"]},"designModule":{"phases":["PHASE3"]},"armsInterventionsModule":{"interventions":[{"type":"DRUG","name":"Placebo"},{"type":"DRUG","name":"PF-08653944"}]}}},
{"protocolSection":{"identificationModule":{"nctId":"NCT03577197","briefTitle":"Southeast Netherlands Advanced Metastatic Breast Cancer Registry"},"statusModule":{"overallStatus":"RECRUITING","primaryCompletionDateStruct":{"date":"2027-12-31"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"Academisch Ziekenhuis Maastricht"}},"conditionsModule":{"conditions":["Advanced Breast Cancer","Metastatic Breast Cancer"]},"designModule":{}}},
{"protocolSection":{"identificationModule":{"nctId":"NCT07122986","briefTitle":"A Study to Learn About a Vaccine Against E Coli in Healthy Adults"},"statusModule":{"overallStatus":"ACTIVE_NOT_RECRUITING","primaryCompletionDateStruct":{"date":"2028-05-08"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"Pfizer"}},"conditionsModule":{"conditions":["Healthy Participants"]},"designModule":{"phases":["PHASE1"]},"armsInterventionsModule":{"interventions":[{"type":"BIOLOGICAL","name":"E coli vaccine 1 Dose A"},{"type":"BIOLOGICAL","name":"Placebo"}]}}},
{"protocolSection":{"identificationModule":{"nctId":"NCT00000001","briefTitle":"Beispielstudie Spaetphase"},"statusModule":{"overallStatus":"RECRUITING","primaryCompletionDateStruct":{"date":"2027-03"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"Seagen, a wholly owned subsidiary of Pfizer"}},"conditionsModule":{"conditions":["Breast Cancer"]},"designModule":{"phases":["PHASE2","PHASE3"]},"armsInterventionsModule":{"interventions":[{"type":"DRUG","name":"Disitamab vedotin"}]}}}
],"nextPageToken":"ZVNj7o2Elu8o3lpyXdC5577umpOQJJxsYfai2_g"}"""

    private val beendet = """{"studies":[
{"protocolSection":{"identificationModule":{"nctId":"NCT06103734","briefTitle":"A Study to Learn About Zavegepant as a Treatment for Multiple Migraine Attacks in Adult Participants"},"statusModule":{"overallStatus":"TERMINATED","whyStopped":"Strategic decision to cancel the study based on adjusted clinical development plan.","primaryCompletionDateStruct":{"date":"2026-08-02"},"completionDateStruct":{"date":"2026-08-02"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"Pfizer"}},"conditionsModule":{"conditions":["Migraine"]},"designModule":{"phases":["PHASE3"]},"armsInterventionsModule":{"interventions":[{"type":"COMBINATION_PRODUCT","name":"Zavegepant 10 mg"},{"type":"COMBINATION_PRODUCT","name":"Placebo - Control 1"}]}}},
{"protocolSection":{"identificationModule":{"nctId":"NCT06973720","briefTitle":"A Phase 2b Study to Evaluate the Efficacy and Safety of Once-Monthly MET097 in Adults With Obesity or Overweight"},"statusModule":{"overallStatus":"COMPLETED","primaryCompletionDateStruct":{"date":"2025-12-16"},"completionDateStruct":{"date":"2026-10-19"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"Pfizer"}},"conditionsModule":{"conditions":["Obesity","Overweight or Obesity"]},"designModule":{"phases":["PHASE2"]},"armsInterventionsModule":{"interventions":[{"type":"DRUG","name":"MET097"},{"type":"DRUG","name":"Placebo"}]}}}
]}"""

    @Test fun studienNamenUndSponsor() {
        val n = Studien.namen("Pfizer Inc.")
        assertEquals(listOf("PFIZER", "SEAGEN"), n)
        assertTrue(Studien.passtSponsor("Seagen, a wholly owned subsidiary of Pfizer", n))
        val merck = Studien.namen("Merck & Co., Inc.")
        assertEquals(listOf("MERCK"), merck)
        assertTrue(Studien.passtSponsor("Merck Sharp & Dohme LLC", merck))
        assertTrue(Studien.passtSponsor("Peloton Therapeutics, Inc., a subsidiary of Merck & Co., Inc. (Rahway, New Jersey USA)", merck))
        assertFalse(Studien.passtSponsor("Merck Healthcare KGaA, Darmstadt, Germany, an affiliate of Merck KGaA, Darmstadt, Germany", merck))
        val lilly = Studien.namen("Eli Lilly and Company")
        assertTrue(Studien.passtSponsor("Eli Lilly and Company", lilly))
        assertTrue(Studien.passtSponsor("Morphic Therapeutic, Inc. (A Wholly Owned Subsidiary of Eli Lilly and Company)", lilly))
        assertFalse(Studien.passtSponsor("Academisch Ziekenhuis Maastricht", n))
        assertTrue(Studien.passtSponsor("ModernaTX, Inc.", Studien.namen("Moderna, Inc.")))
    }

    @Test fun studienLesenUndPhasen() {
        val n = Studien.namen("Pfizer Inc.")
        val l = Studien.lesen(studien, n)
        assertEquals(listOf("NCT07595549", "NCT07122986", "NCT00000001"), l.map { it.nct })
        assertEquals(listOf(3, 1, 3), l.map { it.phase })
        // Placebo ist nie das geprueft Mittel
        assertEquals("PF-08653944", l[0].mittel)
        assertEquals("Obesity", l[0].gebiet)
        assertEquals(Triple(1, 0, 2), Studien.jePhase(l))
        assertEquals(3, Studien.hoechstePhase(listOf("PHASE2", "PHASE3")))
        assertEquals(0, Studien.hoechstePhase(listOf("NA")))
        assertEquals("2027-03-31", Studien.tag("2027-03"))
    }

    @Test fun naechsteAbschluesseNurSpaetphase() {
        val l = Studien.lesen(studien, Studien.namen("Pfizer"))
        val bald = Studien.naechste(l, "2026-11-11")
        // Phase 1 fehlt, die Monatsangabe 2027-03 kommt vor 2028-05-04
        assertEquals(listOf("NCT00000001", "NCT07595549"), bald.map { it.nct })
        // Ein Abschluss in der Vergangenheit zaehlt nicht
        assertTrue(Studien.naechste(l, "2029-01-01").isEmpty())
    }

    @Test fun beendeteStudien() {
        val b = Studien.beendet(Studien.lesen(beendet, Studien.namen("Pfizer")), "2026-11-11")
        assertEquals(listOf("NCT06103734", "NCT06973720"), b.map { it.nct })
        assertEquals("TERMINATED", b[0].status)
        assertTrue(b[0].grund.startsWith("Strategic decision"))
        assertEquals("Zavegepant 10 mg", b[0].mittel)
        // Studien mit Enddatum in der Zukunft sind nicht beendet
        assertTrue(Studien.beendet(Studien.lesen(beendet, Studien.namen("Pfizer")), "2025-01-01").isEmpty())
    }

    @Test fun studienAdressen() {
        val a = Studien.laufendAdresse("ELI LILLY")
        assertTrue(a.startsWith("https://clinicaltrials.gov/api/v2/studies?query.lead=ELI+LILLY&filter.overallStatus=RECRUITING%7C"))
        assertTrue(Studien.fertigAdresse("PFIZER", "2025-11-11", "2026-11-11").contains("RANGE%5B2025-11-11,2026-11-11%5D"))
    }

    // ---------------------------------------------------------------- Fahrzeuge

    private val rueckrufe = """{"meta":{"status":200},"results":[
{"nhtsaCampaignNumber":"26V737","manufacturerName":"Daimler Trucks North America, LLC","recall573Date":"2026-11-06T18:06:06Z","potaff":3894,"subject":"Driveline May Separate"},
{"nhtsaCampaignNumber":"26V730","manufacturerName":"Harley-Davidson Motor Company","recall573Date":"2026-11-03T22:55:38Z","potaff":21643,"subject":"Neutral Gear Displays When Transmission is Actually in Gear"},
{"nhtsaCampaignNumber":"26V726","manufacturerName":"Ford Motor Company","recall573Date":"2026-11-02T17:43:17Z","potaff":33578,"subject":"Map Lights May Remain Illuminated/FMVSS 101"},
{"nhtsaCampaignNumber":"26V724","manufacturerName":"Ford Motor Company","recall573Date":"2026-11-02T12:55:44Z","potaff":8650,"subject":"Loose 12-Volt Cable May Cause Loss of Drive Power"},
{"nhtsaCampaignNumber":"26V718","manufacturerName":"General Motors, LLC","recall573Date":"2026-10-29T16:54:24Z","potaff":12728,"subject":"Improperly Tightened Steering Gear Assembly Nut"},
{"nhtsaCampaignNumber":"26V600","manufacturerName":"Chrysler (FCA US, LLC)","recall573Date":"2026-09-29T16:54:24Z","potaff":1000,"subject":"Beispiel"},
{"nhtsaCampaignNumber":"26V500","manufacturerName":"Honda (American Honda Motor Co.)","recall573Date":"2026-08-29T16:54:24Z","potaff":2000,"subject":"Beispiel"},
{"nhtsaCampaignNumber":"26V400","manufacturerName":"Forest River, Inc.","recall573Date":"2026-07-29T16:54:24Z","potaff":50,"subject":"Beispiel"},
{"nhtsaCampaignNumber":"24V100","manufacturerName":"Ford Motor Company","recall573Date":"2024-01-10T16:54:24Z","potaff":99999,"subject":"Zu alt"}
]}"""

    private val untersuchungen = """{"meta":{"status":200},"results":[
{"id":1989455,"openDate":"2026-10-23T12:00:00Z","investigationType":"DP","status":"O","subject":"Camshaft and Lifter Failure in Ford 7.3L Gasoline Engines","description":"<p>The Office of Defects Investigation (ODI) received a petition on June 13, 2026, requesting an investigation into camshaft and lifter failures in Ford 7.3L gasoline engines used in 2021-2022 Ford E-350 and 2021 Ford F59 and F-750 vehicles.</p>","nhtsaId":"DP26011"},
{"id":1989456,"openDate":"2026-03-19T12:00:00Z","investigationType":"EA","status":"O","subject":"FSD Collisions in Reduced Roadway Visibility Conditions","description":"<p>The Office of Defects Investigation (ODI) is opening this Engineering Analysis to evaluate Tesla&rsquo;s Full Self Driving Beta and Full Self Driving (Supervised) (collectively, FSD) degradation detection system.</p>","nhtsaId":"EA26002"},
{"id":1989457,"openDate":"2026-09-14T12:00:00Z","investigationType":"DP","status":"C","subject":"Driveshaft to rear differential connection failure","description":"<p>The Office of Defects Investigation (ODI) received a petition on September 1, 2026, requesting an investigation into an alleged defect in BMW rear differential assemblies.</p>","nhtsaId":"DP26009"},
{"id":1989458,"openDate":"2026-10-09T12:00:00Z","investigationType":"RQ","status":"O","subject":"Upper Grille Deflector Detachment","description":"<p>118 complaints alleging detachment of the upper grille deflector on 2022-2024 model year (MY) GMC Sierra vehicles.</p>","nhtsaId":"RQ26003"}
]}"""

    @Test fun rueckrufeLesenUndZuordnen() {
        val alle = Fahrzeugrueckrufe.rueckrufeLesen(rueckrufe)!!
        assertEquals(9, alle.size)
        val ford = Fahrzeugrueckrufe.rueckrufeFuer(alle, Fahrzeugrueckrufe.namen("Ford Motor Company"), "2024-11-11")
        assertEquals(listOf("26V726", "26V724"), ford.map { it.nummer })
        assertEquals(33578L + 8650L, Fahrzeugrueckrufe.Ergebnis(ford, emptyList()).fahrzeuge)
        assertEquals("26V726", Fahrzeugrueckrufe.groesste(ford).first().nummer)
        assertEquals(listOf("26V718"), Fahrzeugrueckrufe.rueckrufeFuer(alle, Fahrzeugrueckrufe.namen("General Motors Company"), "2024-11-11").map { it.nummer })
        assertEquals(listOf("26V600"), Fahrzeugrueckrufe.rueckrufeFuer(alle, Fahrzeugrueckrufe.namen("Stellantis N.V."), "2024-11-11").map { it.nummer })
        assertEquals(listOf("26V500"), Fahrzeugrueckrufe.rueckrufeFuer(alle, Fahrzeugrueckrufe.namen("Honda Motor Co., Ltd."), "2024-11-11").map { it.nummer })
        assertEquals(listOf("26V730"), Fahrzeugrueckrufe.rueckrufeFuer(alle, Fahrzeugrueckrufe.namen("Harley-Davidson, Inc."), "2024-11-11").map { it.nummer })
        // Eine Firma ohne Eintrag bekommt nichts, auch nicht ueber einen Teilnamen
        assertTrue(Fahrzeugrueckrufe.rueckrufeFuer(alle, Fahrzeugrueckrufe.namen("Tesla, Inc."), "2024-11-11").isEmpty())
        assertTrue(Fahrzeugrueckrufe.rueckrufeFuer(alle, Fahrzeugrueckrufe.namen("Forrest Industries"), "2024-11-11").isEmpty())
    }

    @Test fun untersuchungenNurOffeneMitNennung() {
        val u = Fahrzeugrueckrufe.untersuchungenLesen(untersuchungen)!!
        assertEquals(listOf("DP", "EA", "DP", "RQ"), u.map { it.art })
        assertEquals(listOf("DP26011"), Fahrzeugrueckrufe.untersuchungenFuer(u, Fahrzeugrueckrufe.namen("Ford Motor Company")).map { it.nummer })
        assertEquals(listOf("EA26002"), Fahrzeugrueckrufe.untersuchungenFuer(u, Fahrzeugrueckrufe.namen("Tesla, Inc.")).map { it.nummer })
        assertEquals(listOf("RQ26003"), Fahrzeugrueckrufe.untersuchungenFuer(u, Fahrzeugrueckrufe.namen("General Motors Company")).map { it.nummer })
        // Die BMW-Untersuchung ist abgeschlossen
        assertTrue(Fahrzeugrueckrufe.untersuchungenFuer(u, Fahrzeugrueckrufe.namen("Bayerische Motoren Werke AG")).isEmpty())
    }

    @Test fun fahrzeugBranchen() {
        assertTrue(Fahrzeugrueckrufe.betroffen("Auto Manufacturers"))
        assertTrue(Fahrzeugrueckrufe.betroffen("Auto Parts"))
        assertFalse(Fahrzeugrueckrufe.betroffen("Trucking"))
        assertFalse(Fahrzeugrueckrufe.betroffen("Banks - Diversified"))
        assertEquals("https://api.nhtsa.gov/recalls?max=100&sort=recall573Date&order=desc&offset=300", Fahrzeugrueckrufe.rueckrufAdresse(300))
        assertNotNull(Fahrzeugrueckrufe.rueckrufeLesen("""{"results":[]}"""))
        assertEquals(null, Fahrzeugrueckrufe.rueckrufeLesen("""{"message":"Missing Authentication Token"}"""))
    }

    // ---------------------------------------------------------------- Produkte

    private val cpsc = """[
{"RecallID":10467,"RecallNumber":"26067","RecallDate":"2025-11-06T00:00:00","URL":"https://www.cpsc.gov/Recalls/2026/Peloton-Recalls-Original-Series-Bike-Exercise-Bikes-Due-to-Fall-and-Injury-Hazards","Title":"Peloton Recalls Original Series Bike+ Exercise Bikes Due to Fall and Injury Hazards","Products":[{"Name":"Peloton Original Series Bike+ Model PL02","NumberOfUnits":"About 833,000"}],"Manufacturers":[],"Retailers":[{"Name":"Peloton and Dick's Sporting Goods stores nationwide and online at onepeloton.com"}],"Importers":[{"Name":"Peloton Interactive Inc., of New York, N.Y."}],"Hazards":[{"Name":"The bike's seat post assembly can break during use, posing fall and injury hazards to the user."}]},
{"RecallID":10455,"RecallNumber":"26054","RecallDate":"2025-10-30T00:00:00","URL":"https://www.cpsc.gov/Recalls/2026/Polaris-Industries-Recalls-Ranger","Title":"Polaris Industries Recalls Model Year 2025-2026 Ranger XP 1000 NorthStar Recreational Off-Road Vehicles (ROVs) Due to Risk of Serious Injury from Fire Hazard","Products":[{"Name":"Model Year 2025-2026 Ranger XP 1000 NorthStar Recreational Off-Road Vehicles (ROVs)"}],"Manufacturers":[{"Name":"Polaris Industries Inc., of Medina, Minn."}],"Importers":[],"Hazards":[{"Name":"An improperly routed alternator cable can become damaged."}]},
{"RecallID":11045,"RecallNumber":"27066","RecallDate":"2026-11-05T00:00:00","URL":"https://cpsc.gov/Recalls/2027/Arlopu-Recalls-Slide-Playsets","Title":"Arlopu Recalls Slide Playsets Due to Risk of Serious Injury and Death from Head and Neck Entrapment Hazards","Products":[{"Name":"Arlopu Slide Playsets"}],"Manufacturers":[],"Importers":[{"Name":"SHENZHENSHIMINGXIAOSHANGMAOYOUXIANGONGSI dba Arlopu, of China"}],"Hazards":[{"Name":"The recalled products contain multiple openings."}]},
{"RecallID":5,"RecallNumber":"07001","RecallDate":"2007-02-06T00:00:00","URL":"https://www.cpsc.gov/x","Title":"Easy-Bake Ovens Recalled for Repair Due to Entrapment and Burn Hazards","Products":[{"Name":"Easy-Bake Ovens"}],"Manufacturers":[{"Name":"Easy-Bake, a division of Hasbro, Inc., of Pawtucket, R.I."}],"Importers":[],"Hazards":[{"Name":"Children can get fingers caught."}]}
]"""

    @Test fun produktrueckrufeZuordnen() {
        val peloton = Produktrueckrufe.lesen(cpsc, Produktrueckrufe.namen("Peloton Interactive, Inc."), "2024-11-11")
        assertEquals(listOf("10467"), peloton.map { it.id })
        assertEquals("Peloton Original Series Bike+ Model PL02", peloton[0].produkt)
        assertEquals("Fall and Injury Hazards", peloton[0].gefahr)
        assertEquals("2025-11-06", peloton[0].tag)
        val polaris = Produktrueckrufe.lesen(cpsc, Produktrueckrufe.namen("Polaris Inc."), "2024-11-11")
        assertEquals(listOf("10455"), polaris.map { it.id })
        assertEquals("Risk of Serious Injury from Fire Hazard", polaris[0].gefahr)
        // Ohne Datum vor dem Stichtag und ohne fremde Firma
        assertTrue(Produktrueckrufe.lesen(cpsc, Produktrueckrufe.namen("Hasbro, Inc."), "2024-11-11").isEmpty())
        assertEquals(listOf("5"), Produktrueckrufe.lesen(cpsc, Produktrueckrufe.namen("Hasbro, Inc."), "2000-01-01").map { it.id })
        assertTrue(Produktrueckrufe.lesen(cpsc, Produktrueckrufe.namen("Whirlpool Corporation"), "2000-01-01").isEmpty())
    }

    @Test fun produktHilfen() {
        assertEquals("Peloton", Produktrueckrufe.titelFirma("Peloton Recalls Original Series Bike+ Exercise Bikes Due to Fall and Injury Hazards"))
        assertEquals("Easy-Bake Ovens", Produktrueckrufe.titelFirma("Easy-Bake Ovens Recalled for Repair Due to Entrapment and Burn Hazards"))
        assertEquals("Whirlpool Corporation", Produktrueckrufe.bereinigt("Whirlpool Corporation, of Benton Harbor, Mich."))
        assertEquals(listOf("PROCTER"), Produktrueckrufe.suchbegriffe(Produktrueckrufe.namen("The Procter & Gamble Company")))
        assertEquals(listOf("MATTEL", "FISHER"), Produktrueckrufe.suchbegriffe(Produktrueckrufe.namen("Mattel, Inc.")))
        assertTrue(Produktrueckrufe.betroffen("Furnishings, Fixtures & Appliances"))
        assertTrue(Produktrueckrufe.betroffen("Leisure"))
        assertFalse(Produktrueckrufe.betroffen("Banks - Regional"))
        assertEquals(
            "https://www.saferproducts.gov/RestWebServices/Recall?format=json&RecallDateStart=2024-11-11&RecallTitle=Peloton",
            Produktrueckrufe.titelAdresse("Peloton", "2024-11-11"),
        )
        assertEquals("Slip hazard", Produktrueckrufe.gefahr("Foo Recalls Bar", "Slip hazard. More text here."))
    }
}
