@echo off
setlocal
set "JAVA_HOME=d:\skills exchange\jdk11\jdk-11.0.22+7"
cd /d "d:\skills exchange"
gradlew.bat :app:compileDebugKotlin --no-daemon > build_compile.log 2>&1
echo EXITCODE=%ERRORLEVEL%



