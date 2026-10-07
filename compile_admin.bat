@echo off
cd /d d:\skills exchange
call gradlew.bat :app:compileDebugKotlin --console=plain > compile.log 2>&1
echo EXIT_CODE=%ERRORLEVEL%
