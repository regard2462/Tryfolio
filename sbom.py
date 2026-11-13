#!/usr/bin/env python3
"""SBOM (CycloneDX-JSON) fuer Tryfolio aus gradle/verification-metadata.xml.

Die Verifikations-Metadatei entsteht mit
    ./gradlew --write-verification-metadata sha256
und enthaelt jede Bibliothek des Baus mit ihrer SHA-256-Pruefsumme. Dieses Skript
wandelt sie in ein Software-Bill-of-Materials im CycloneDX-1.6-Format. Aufruf:

    python sbom.py --version 0.73 [--eingabe gradle/verification-metadata.xml] [--ausgabe sbom.json] [--name Tryfolio]

Die Pruefsumme stammt vom Binaerartefakt (jar, aar, klib); nur wenn es keines gibt,
vom ersten anderen Artefakt (pom, module). Bauwerkzeuge (Android-Gradle-Plugin, Kotlin-
Compiler, Gradle-Plugins, aapt2 und aehnliche) sind anhand von Gruppe und Name erkannt und
mit scope "excluded" markiert; alles andere bleibt ohne Angabe. Die Metadatei kennt keinen
Abhaengigkeitsgraphen, daher gibt es keinen.
Deckt den Hauptbau (Android und Desktop) ab; der iOS-Bau hat sein eigenes
Gradle-Projekt im Ordner ios und damit eine eigene Metadatei.
"""

from __future__ import annotations

import argparse
import json
import time
import uuid
import xml.etree.ElementTree as ET

BINAER = (".jar", ".aar", ".klib")
NEBENARTEFAKT = ("-sources.jar", "-javadoc.jar", "-sources.zip")
BAU_GRUPPEN = ("com.android.tools", "org.gradle", "org.jetbrains.kotlin.plugin")
BAU_AUSNAHMEN = ("com.android.tools:desugar_jdk_libs",)
BAU_NAMEN = ("kotlin-gradle-", "kotlin-compiler", "kotlin-build-", "kotlin-native-", "kotlin-klib-", "kotlin-scripting-", "kotlin-daemon", "kotlin-tooling-", "kotlin-android-extensions")


def purl(group: str, name: str, version: str) -> str:
    g = group.replace("@", "%40").replace(":", "%3A").replace("/", "%2F")
    n = name.replace("@", "%40").replace(":", "%3A").replace("/", "%2F")
    return f"pkg:maven/{g}/{n}@{version}"


def ist_bauwerkzeug(group: str, name: str) -> bool:
    if f"{group}:{name}" in BAU_AUSNAHMEN:
        return False
    if name.endswith(".gradle.plugin") or name.startswith("aapt2"):
        return True
    if any(group == g or group.startswith(g + ".") for g in BAU_GRUPPEN):
        return True
    return group == "org.jetbrains.kotlin" and name.startswith(BAU_NAMEN)


def wahl_artefakt(artefakte: list[tuple[str, str]]) -> tuple[str, str] | None:
    """Binaerartefakt (jar vor aar vor klib), sonst das erste Artefakt mit Pruefsumme."""
    for endung in BINAER:
        for name, summe in artefakte:
            if name.endswith(endung) and not name.endswith(NEBENARTEFAKT):
                return name, summe
    return artefakte[0] if artefakte else None


def main() -> None:
    ap = argparse.ArgumentParser(description="CycloneDX-SBOM aus der Gradle-Verifikationsdatei")
    ap.add_argument("--version", required=True, help="Version der App, z. B. 0.73")
    ap.add_argument("--eingabe", default="gradle/verification-metadata.xml")
    ap.add_argument("--ausgabe", default="sbom.json")
    ap.add_argument("--name", default="Tryfolio")
    a = ap.parse_args()

    # Namensraum der Gradle-Datei entfernen, damit die Knotennamen passen
    knoten_alle = []
    for _, element in ET.iterparse(a.eingabe, events=("end",)):
        element.tag = element.tag.split("}")[-1]
        knoten_alle.append(element)

    je_koordinate: dict[tuple[str, str, str], list[tuple[str, str]]] = {}
    for knoten in (e for e in knoten_alle if e.tag == "component"):
        group = knoten.get("group") or ""
        artifact = knoten.get("name") or ""
        version_k = knoten.get("version") or ""
        if not artifact:
            continue
        liste = je_koordinate.setdefault((group, artifact, version_k), [])
        for art in knoten.iter("artifact"):
            # Gradle schreibt den Hash als Attribut: <sha256 value="..." origin="..."/>
            for wert in art.iter("sha256"):
                if wert.get("value") and art.get("name"):
                    liste.append((art.get("name"), wert.get("value").strip()))
                    break

    komponenten = []
    for (group, artifact, version_k), artefakte in je_koordinate.items():
        eintrag = {
            "type": "library",
            "bom-ref": purl(group, artifact, version_k),
            "group": group,
            "name": artifact,
            "version": version_k,
            "purl": purl(group, artifact, version_k),
        }
        gewaehlt = wahl_artefakt(artefakte)
        if gewaehlt:
            eintrag["hashes"] = [{"alg": "SHA-256", "content": gewaehlt[1]}]
            eintrag["properties"] = [{"name": "artifact", "value": gewaehlt[0]}]
        if ist_bauwerkzeug(group, artifact):
            eintrag["scope"] = "excluded"
        komponenten.append(eintrag)
    komponenten.sort(key=lambda k: (k["group"], k["name"], k["version"]))

    sbom = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "serialNumber": f"urn:uuid:{uuid.uuid4()}",
        "version": 1,
        "metadata": {
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "component": {"type": "application", "name": a.name, "version": a.version},
            "tools": {"components": [{"type": "application", "name": "Tryfolio sbom.py"}]},
        },
        "components": komponenten,
    }
    with open(a.ausgabe, "w", encoding="utf-8") as f:
        json.dump(sbom, f, ensure_ascii=False, indent=1)
        f.write("\n")
    bau = sum(1 for k in komponenten if k.get("scope") == "excluded")
    print(f"{len(komponenten)} Komponenten ({bau} Bauwerkzeuge) nach {a.ausgabe} geschrieben")


if __name__ == "__main__":
    main()
