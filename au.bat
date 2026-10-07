@echo off
set OUT=d:\skills exchange\auth-unpack\entries.txt
if exist "%OUT%" del "%OUT%"
D:\kilo_downloads\jdk17\jdk17.0.20_10\bin\jar tf "d:\gradle-home\caches\modules-2\files-2.1\com.google.firebase\firebase-auth\24.1.0\fa1507898894d1e5ddd31ac764eda08a6235e115\firebase-auth-24.1.0.aar" > "%OUT%"
echo JAR_LIST_ABOVE
type "%OUT%"
