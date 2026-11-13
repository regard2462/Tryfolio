# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class de.troetenrepublik.app.**$$serializer { *; }
-keepclassmembers class de.troetenrepublik.app.** { *** Companion; }
-keepclasseswithmembers class de.troetenrepublik.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Bruecke der Finanzwissen-Web-App
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# Eigener Code bleibt vollstaendig und unter seinem Namen (Serialisierung, Widgets, Arbeitsauftraege); R8 optimiert
# vor allem die Bibliotheken wie Compose
-keep class de.troetenrepublik.app.** { *; }
