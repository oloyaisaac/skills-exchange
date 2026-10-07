@echo off
cd /d d:\skills exchange
call gradlew.bat :app:dependencies --configuration debugCompileClasspath --console=plain > deps_comp3.txt 2>&1
echo DONE
