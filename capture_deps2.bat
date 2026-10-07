@echo off
cd /d d:\skills exchange
call gradlew.bat :app:dependencies --configuration debugRuntimeClasspath --console=plain > deps_compile.txt 2>&1
echo DONE
