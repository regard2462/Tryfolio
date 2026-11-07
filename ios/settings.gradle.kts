// Eigenes Gradle-Build fuer iPhone und iPad, getrennt vom Build der Android- und Desktop-App im Hauptordner.
// Es hat eigene Fassungen von Kotlin und Compose Multiplatform (iOS ist erst ab Compose 1.8 stabil) und uebersetzt
// denselben gemeinsamen Code aus app/src/main/java. Android und Desktop bleiben dadurch unberuehrt.
// Aufruf aus dem Hauptordner: ./gradlew -p ios <Aufgabe>
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}
rootProject.name = "TryfolioIos"
