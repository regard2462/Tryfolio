# -*- coding: utf-8 -*-
"""Tests fuer den Umsetzer der Form-4-XML und die Indexdatei im Nachtrag (python -m unittest tools/sec/test_holen.py)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import holen  # noqa: E402

XML = """<?xml version="1.0"?>
<ownershipDocument>
  <documentType>4</documentType>
  <aff10b5One>1</aff10b5One>
  <issuer>
    <issuerCik>0000012345</issuerCik>
    <issuerName>EXAMPLE WIDGETS, INC.</issuerName>
    <issuerTradingSymbol>exw</issuerTradingSymbol>
  </issuer>
  <reportingOwner>
    <reportingOwnerId><rptOwnerName>Muster Anna</rptOwnerName></reportingOwnerId>
    <reportingOwnerRelationship><isDirector>1</isDirector><isOfficer>0</isOfficer></reportingOwnerRelationship>
  </reportingOwner>
  <reportingOwner>
    <reportingOwnerId><rptOwnerName>Beispiel Bernd</rptOwnerName></reportingOwnerId>
    <reportingOwnerRelationship><isDirector>0</isDirector><isOfficer>true</isOfficer><officerTitle>Chief Executive Officer</officerTitle></reportingOwnerRelationship>
  </reportingOwner>
  <derivativeTable>
    <derivativeTransaction>
      <conversionOrExercisePrice><value>5.00</value></conversionOrExercisePrice>
      <transactionDate><value>2026-10-06</value></transactionDate>
      <transactionCoding><transactionCode>M</transactionCode></transactionCoding>
      <transactionAmounts><transactionShares><value>1000</value></transactionShares></transactionAmounts>
    </derivativeTransaction>
  </derivativeTable>
  <nonDerivativeTable>
    <nonDerivativeTransaction>
      <transactionDate><value>2026-10-05</value></transactionDate>
      <transactionCoding><transactionCode>S</transactionCode></transactionCoding>
      <transactionAmounts>
        <transactionShares><value>600</value></transactionShares>
        <transactionPricePerShare><value>20.00</value></transactionPricePerShare>
      </transactionAmounts>
      <postTransactionAmounts><sharesOwnedFollowingTransaction><value>5000</value></sharesOwnedFollowingTransaction></postTransactionAmounts>
      <ownershipNature><directOrIndirectOwnership><value>D</value></directOrIndirectOwnership></ownershipNature>
    </nonDerivativeTransaction>
    <nonDerivativeTransaction>
      <transactionDate><value>2026-10-06</value></transactionDate>
      <transactionCoding><transactionCode>S</transactionCode></transactionCoding>
      <transactionAmounts>
        <transactionShares><value>400</value></transactionShares>
        <transactionPricePerShare><value>21.00</value></transactionPricePerShare>
      </transactionAmounts>
      <postTransactionAmounts><sharesOwnedFollowingTransaction><value>900</value></sharesOwnedFollowingTransaction></postTransactionAmounts>
      <ownershipNature><directOrIndirectOwnership><value>I</value></directOrIndirectOwnership></ownershipNature>
    </nonDerivativeTransaction>
    <nonDerivativeTransaction>
      <transactionDate><value>2026-10-06</value></transactionDate>
      <transactionCoding><transactionCode>A</transactionCode></transactionCoding>
      <transactionAmounts><transactionShares><value>50</value></transactionShares></transactionAmounts>
    </nonDerivativeTransaction>
    <nonDerivativeTransaction>
      <transactionDate><value>2026-10-07</value></transactionDate>
      <transactionCoding><transactionCode>P</transactionCode></transactionCoding>
      <transactionAmounts>
        <transactionShares><value>100</value></transactionShares>
        <transactionPricePerShare><value>19.50</value></transactionPricePerShare>
      </transactionAmounts>
      <postTransactionAmounts><sharesOwnedFollowingTransaction><value>5100</value></sharesOwnedFollowingTransaction></postTransactionAmounts>
    </nonDerivativeTransaction>
  </nonDerivativeTable>
</ownershipDocument>
"""


class Form4Test(unittest.TestCase):
    def test_umsetzung(self):
        aus = {}
        holen.form4_geschaefte(XML, "0000012345-26-000001", "2026-10-08", aus)
        self.assertEqual(set(aus), {("0000012345-26-000001", False), ("0000012345-26-000001", True)})
        v = aus[("0000012345-26-000001", False)]
        self.assertEqual((v["k"], v["c"], v["p"], v["r"], v["t"]), ("EXW", "12345", "Beispiel Bernd", "S", "Chief Executive Officer"))
        self.assertEqual(v["e"], "Example Widgets, Inc.")
        self.assertEqual(v["f"], "2026-10-08")
        self.assertEqual(v["d"], "2026-10-06")
        self.assertAlmostEqual(v["n"], 1000.0)
        self.assertAlmostEqual(v["nd"], 600.0)  # indirekte Zeile zaehlt nicht fuer den direkten Anteil
        self.assertAlmostEqual(v["wert"], 600 * 20 + 400 * 21)
        self.assertEqual(v["nach"], 5000.0)  # Bestand nur aus direkten Zeilen
        self.assertTrue(v["plan"])
        self.assertEqual(v["ap"], 5.0)
        k = aus[("0000012345-26-000001", True)]
        self.assertTrue(k["kauf"])
        self.assertFalse(k["plan"])
        self.assertEqual(k["nach"], 5100.0)
        self.assertNotIn("ap", k)

    def test_fertigstellen_wie_datensatz(self):
        aus = {}
        holen.form4_geschaefte(XML, "0000012345-26-000001", "2026-10-08", aus)
        liste = holen.fertigstellen(aus, "2026-10-01")
        self.assertEqual(len(liste), 2)
        v = [g for g in liste if not g["kauf"]][0]
        self.assertAlmostEqual(v["preis"], 20.4)
        self.assertEqual(v["wert"], 20400.0)
        self.assertEqual(v["nd"], 600.0)

    def test_fremde_und_leere_dokumente(self):
        aus = {}
        holen.form4_geschaefte("<ownershipDocument><documentType>3</documentType></ownershipDocument>", "x", "2026-10-08", aus)
        holen.form4_geschaefte("kein xml", "y", "2026-10-08", aus)
        holen.form4_geschaefte(XML.replace("exw", "NONE"), "z", "2026-10-08", aus)
        self.assertEqual(aus, {})

    def test_nur_boersengeschaefte(self):
        aus = {}
        holen.form4_geschaefte(XML.replace("<transactionCode>S</transactionCode>", "<transactionCode>F</transactionCode>"),
                               "a", "2026-10-08", aus)
        self.assertEqual(set(aus), {("a", True)})

    def test_index_und_einreichung(self):
        idx = ("Form Type   Company Name   CIK   Date Filed   File Name\n" + "-" * 80 + "\n"
               "4               ACME CORP                  111  20261001  edgar/data/111/0000111111-26-000001.txt\n"
               "4               ACME CORP                  111  20261001  edgar/data/222/0000111111-26-000001.txt\n"
               "4/A             ACME CORP                  111  20261001  edgar/data/111/0000111111-26-000002.txt\n"
               "424B2           ACME CORP                  111  20261001  edgar/data/111/0000111111-26-000003.txt\n")
        self.assertEqual(holen.indexzeilen(idx), {"0000111111-26-000001": "edgar/data/111/0000111111-26-000001.txt"})
        txt = ("<SEC-DOCUMENT>\n<DOCUMENT>\n<TYPE>4\n<XML>\n<?xml version=\"1.0\"?>\n<ownershipDocument>\n</ownershipDocument>\n"
               "</XML>\n</DOCUMENT>\n</SEC-DOCUMENT>").encode("utf-8")
        self.assertTrue(holen.xml_aus_einreichung(txt).startswith("<ownershipDocument>"))
        self.assertEqual(holen.xml_aus_einreichung(b"<SEC-DOCUMENT>ohne</SEC-DOCUMENT>"), "")

    def test_feiertage(self):
        import datetime as dt
        self.assertTrue(holen.feiertag(dt.date(2026, 10, 12)))
        self.assertTrue(holen.feiertag(dt.date(2026, 11, 11)))
        self.assertTrue(holen.feiertag(dt.date(2026, 11, 26)))
        self.assertFalse(holen.feiertag(dt.date(2026, 10, 13)))


if __name__ == "__main__":
    unittest.main()
