#!/bin/bash
# Fotolauf fuer die Kurzanleitung im Emulator: App und Test installieren, Statusleiste beruhigen (Demo-Modus),
# je Sprache den Test „AnleitungsBilder“ laufen lassen und die Fotos abholen. Aufruf aus .github/workflows/anleitungsbilder.yml.
set -u
paket=de.troetenrepublik.app
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Statusleiste wie auf einem frischen Telefon: 10:00 Uhr, volles Netz, voller Akku, keine Mitteilungen
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

# Keine Fehlerdialoge des Systems („... isn't responding“): Der langsame Emulator meldet sonst den Launcher als
# haengend, und der Dialog liegt ueber der App. Den Launcher braucht der Fotolauf nicht.
adb shell settings put global hide_error_dialogs 1 || true
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
adb shell input keyevent KEYCODE_HOME || true
sleep 20

mkdir -p roh
for s in de en; do
  adb shell pm clear "$paket"
  adb shell pm grant "$paket" android.permission.POST_NOTIFICATIONS || true
  adb shell am instrument -w -e sprache "$s" -e class "$paket.AnleitungsBilder" "$paket.test/androidx.test.runner.AndroidJUnitRunner" | tee "roh/protokoll-$s.txt"
  adb pull "/sdcard/Android/data/$paket/files/anleitung/$s" "roh/" || echo "Keine Fotos fuer $s"
  mv "roh/$s/ablauf.txt" "roh/protokoll-$s-ablauf.txt" 2>/dev/null || true
done
ls -la roh roh/* || true
