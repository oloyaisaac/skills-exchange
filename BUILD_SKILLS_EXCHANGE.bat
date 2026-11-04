@echo off
set "PROJECT_DIR=D:\skills exchange"
set "GRADLEW=%PROJECT_DIR%\gradlew.bat"
set "JDK_HOME=D:/kilo_downloads/jdk17/jdk17.0.20_10"
set "APP_DIR=%PROJECT_DIR%\app"

echo.
======================================================================
  Skills Exchange - One-Click Build Script
======================================================================
echo.

:: Step 1: Change directory to project safely (accounting for space)
echo %1
if not "%~1"=="" (cd "%~1") else cd "%PROJECT_DIR%"
echo.
Step 1: Changed to directory: %CD%
echo.

:: Step 2: Verify that gradlew.bat exists
if exist "%GRADLEW%" (
    echo Step 2: gradlew.bat found -> OK
) else (
    echo Step 2: ERROR - gradlew.bat not found at %GRADLEW%
    echo Please ensure the Gradle wrapper is present.
    pause
    exit /b 1
)
echo.

:: Step 3: Verify that the configured JDK 17 exists
if exist "%JDK_HOME%\bin\java.exe" (
    echo Step 3: JDK 17 found -> OK
    echo Java home: %JDK_HOME%
) else (
    echo Step 3: WARNING - JDK 17 not found at %JDK_HOME%
    echo The gradle.properties specifies org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10
)
echo.

:: Step 4: Display Java version
echo Step 4: Checking Java version...
"%JDK_HOME%\bin\java.exe" -version 2>&1 || echo Java version check failed
echo.

:: Step 5: Run gradlew.bat compileDebugKotlin --console=plain
echo Step 5: Compiling debug Kotlin sources...
call "%GRADLEW%" compileDebugKotlin --console=plain > NUL 2>NUL
set COMPILE_RESULT=%ERRORLEVEL%
if %COMPILE_RESULT% neq 0 (
    echo.
    echo COMPILATION FAILED (errorlevel = %COMPILE_RESULT%)
    echo.
    pause
    exit /b %COMPILE_RESULT%
) else (
    echo Compilation succeeded.
)
echo.

:: Step 6: If compilation succeeds, run gradlew.bat assembleDebug --console=plain
echo Step 6: Building debug APK...
call "%GRADLEW%" assembleDebug --console=plain > NUL 2>NUL
set APK_RESULT=%ERRORLEVEL%
if %APK_RESULT% neq 0 (
    echo.
    echo APK BUILD FAILED (errorlevel = %APK_RESULT%)
    echo.
    pause
    exit /b %APK_RESULT%
) else (
    echo APK build process initiated.
)
echo.

:: Step 9: Verify that app\build\outputs\apk\debug\app-debug.apk exists
set "APK_PATH=%APP_DIR%\build\outputs\apk\debug\app-debug.apk"
if exist "%APK_PATH%" (
    echo Step 9: APK verified -> OK
) else (
    echo Step 9: ERROR - APK not found at %APK_PATH%
    echo Build may have failed or APK path is incorrect.
    pause
    exit /b 1
)
echo.

:: Step 10: Display the APK's full path, size, and modification time
echo Step 10: APK details
if exist "%APK_PATH%" (
    for %%F in ("%APK_PATH%") do (
        echo.
        echo   Full Path: %%~fF
        echo   File Size: %%~zF bytes
        echo   Modified: %%~tF
    )
) else (
    echo   APK file not found - cannot display details.
)
echo.

:: Step 11: Display BUILD SUCCESSFUL only if all required steps pass
echo.
if exist "%APK_PATH%" (
    echo BUILD SUCCESSFUL
    echo   - Compilation: PASSED
    echo   - APK Generation: PASSED
    echo   - APK Verification: PASSED
    echo   - APK Location: %APK_PATH%
) else (
    echo BUILD INCOMPLETE
    echo   - One or more steps failed.
)
echo.

:: Step 12: Use pause at the end so the window stays open
echo.
echo Build script completed.
echo .
pause