package de.troetenrepublik.app.market.broker

// Was die App bei verbundenem echten Konto zusaetzlich absichert; Plattformteile lesen es. Am Desktop oeffnen fremde
// Seiten dann im Browser des Systems statt im eingebetteten Chromium. Der laeuft ohne Sandbox im Prozess der App,
// in dem auch die Schluessel liegen.
object Netzschutz {
    @Volatile var brokerVerbunden: Boolean = false
}
