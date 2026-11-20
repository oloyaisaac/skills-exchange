@echo off
setlocal
cd /d "d:\skills exchange"
adb logcat -c
start "logcatbg" /b cmd /c "adb logcat -v time > repro-focus.txt"
rem Fresh launch to ensure deterministic ROLE_SELECTION start
adb shell am force-stop com.aistudio.skillsexchange.nxptvq
timeout /t 1 >nul
adb shell am start -n com.aistudio.skillsexchange.nxptvq/com.example.MainActivity
timeout /t 4 >nul
adb shell input tap 337 1190
rem Tap Continue as Client
timeout /t 2 >nul
adb shell uiautomator dump /sdcard/d.xml >nul 2>&1
adb shell cat /sdcard/d.xml > repro-g1.xml
rem Tap Continue with Google
adb shell input tap 360 516
timeout /t 1 >nul
adb shell uiautomator dump /sdcard/d.xml >nul 2>&1
adb shell cat /sdcard/d.xml > repro-g2.xml
timeout /t 1 >nul
adb shell uiautomator dump /sdcard/d.xml >nul 2>&1
adb shell cat /sdcard/d.xml > repro-g3.xml
timeout /t 3 >nul
adb shell uiautomator dump /sdcard/d.xml >nul 2>&1
adb shell cat /sdcard/d.xml > repro-g4.xml
rem Stop logcat
taskkill /im adb.exe /f >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq logcatbg" >nul 2>&1
echo done
endlocal