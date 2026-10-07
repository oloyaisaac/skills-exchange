@echo off
set BOM=https://repo1.maven.org/maven2/com/google/firebase/firebase-bom/34.15.0/firebase-bom-34.15.0.pom
echo BOM_URL=%BOM%
curl -s -o d:\skills exchange\bom.xml %BOM%
echo CURL_EXIT=%ERRORLEVEL%
findstr /c:"firebase-auth-ktx" d:\skills exchange\bom.xml >nul
if %ERRORLEVEL%==0 ( type d:\skills exchange\bom.xml )
