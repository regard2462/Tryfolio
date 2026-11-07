#!/bin/bash
# Fotolauf fuer die Kurzanleitung im Emulator: App und Test installieren, Statusleiste leeren,
# je Sprache den Test „AnleitungsBilder“ laufen lassen und die Fotos abholen. Aufruf aus .github/workflows/anleitungsbilder.yml.
set -u
paket=de.troetenrepublik.app
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Statusleiste leer: keine Uhrzeit, kein Netz, kein Akku und keine Mitteilungen. Der Inhalt laeuft beim Rollen sichtbar
# darunter durch, deshalb werden die Symbole ausgeblendet statt die Leiste zu uebermalen. Der Demo-Modus bleibt aus,
# er blendet nach einer Weile ein Satellitensymbol ein. Vorhandene Mitteilungen raeumt „Alle löschen“ im Schirm ab.
adb shell settings put secure icon_blacklist clock,battery,wifi,mobile,airplane,cast,hotspot,location,alarm_clock,volume,zen,bluetooth,managed_profile,rotate,headset,vpn,ethernet,nfc,data_saver,satellite
adb shell am broadcast -a com.android.systemui.demo -e command exit || true
adb shell cmd statusbar expand-notifications
sleep 2
adb shell uiautomator dump /sdcard/leiste.xml >/dev/null
b=$(adb shell cat /sdcard/leiste.xml | tr '>' '\n' | grep 'content-desc="Clear all notifications' | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 | grep -oE '[0-9]+' | tr '\n' ' ')
if [ -n "$b" ]; then set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2; fi
adb shell cmd statusbar collapse

# Keine Fehlerdialoge des Systems („... isn't responding“): Der langsame Emulator meldet sonst den Launcher als
# haengend, und der Dialog liegt ueber der App. Den Launcher braucht der Fotolauf nicht.
adb shell settings put global hide_error_dialogs 1 || true
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
adb shell input keyevent KEYCODE_HOME || true
sleep 20

mkdir -p roh
for s in de en; do
  adb shell pm clear "$paket"
  # Ohne Mitteilungen der App, sonst steht ihr Symbol in der Statusleiste; „user-fixed“ unterdrueckt die Rueckfrage
  adb shell pm revoke "$paket" android.permission.POST_NOTIFICATIONS || true
  adb shell pm set-permission-flags "$paket" android.permission.POST_NOTIFICATIONS user-fixed || true
  adb shell am instrument -w -e sprache "$s" -e class "$paket.AnleitungsBilder" "$paket.test/androidx.test.runner.AndroidJUnitRunner" | tee "roh/protokoll-$s.txt"
  adb pull "/sdcard/Android/data/$paket/files/anleitung/$s" "roh/" || echo "Keine Fotos fuer $s"
  mv "roh/$s/ablauf.txt" "roh/protokoll-$s-ablauf.txt" 2>/dev/null || true
done
ls -la roh roh/* || true
