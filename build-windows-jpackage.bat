@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ========================================
REM DemograficoBUDA - Windows EXE Builder
REM Uses Maven + jlink + jpackage
REM ========================================

REM ----- Configuration -----
set APP_NAME=DemograficoBUDA
set APP_VERSION=0.1.0
set MAIN_CLASS=com.buda.demografico.DemograficoBUDAMain
set MAIN_JAR=%APP_NAME%-1.0-SNAPSHOT-shaded.jar
set ICON_PATH=src\main\resources\icons\pramana.ico

REM Update these paths to match your local JDK and JavaFX jmods
set JDK_JMODS="C:\Program Files\Java\jdk-24\jmods"
set JAVAFX_JMODS="C:\Program Files\javafx-jmods-24.0.1"

set MODULES=java.base,java.desktop,java.sql,java.net.http,java.prefs,javafx.controls,javafx.fxml,javafx.graphics

echo ========================================
echo %APP_NAME% - Windows EXE Builder
echo ========================================
echo.

echo [1/5] Cleaning previous builds...
call mvn clean
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven clean failed
    pause
    exit /b 1
)
echo.

echo [2/5] Building shaded JAR...
call mvn package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven package failed
    pause
    exit /b 1
)
echo.

echo [3/5] Creating custom JRE with jlink...
where jlink >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: jlink not found. Use a JDK that bundles jlink (JDK 17+).
    pause
    exit /b 1
)

if exist "target\runtime" rmdir /s /q "target\runtime"
jlink --module-path %JDK_JMODS%;%JAVAFX_JMODS% ^
      --add-modules %MODULES% ^
      --bind-services ^
      --output "target\runtime" ^
      --strip-debug --compress=2 --no-header-files --no-man-pages
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: jlink failed
    pause
    exit /b 1
)
echo.

echo [4/5] Preparing jpackage input...
if exist "target\jpackage-input" rmdir /s /q "target\jpackage-input"
mkdir "target\jpackage-input"

if not exist "target\%MAIN_JAR%" (
    echo ERROR: %MAIN_JAR% not found in target\. Adjust MAIN_JAR in this script.
    pause
    exit /b 1
)
copy "target\%MAIN_JAR%" "target\jpackage-input\%MAIN_JAR%" >nul
echo.

echo [5/5] Creating Windows EXE with jpackage...
where jpackage >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: jpackage not found. Use a JDK that bundles jpackage (JDK 17+).
    pause
    exit /b 1
)

if exist "output" rmdir /s /q output
mkdir output

jpackage --type exe ^
         --input "target\jpackage-input" ^
         --dest "output" ^
         --name "%APP_NAME%" ^
         --main-jar "%MAIN_JAR%" ^
         --main-class "%MAIN_CLASS%" ^
         --runtime-image "target\runtime" ^
         --icon "%ICON_PATH%" ^
         --win-shortcut --win-menu ^
         --app-version "%APP_VERSION%" ^
         --vendor "Tashi Rabten" ^
         --win-upgrade-uuid "f3fd4c60-5b35-4b73-a3c7-3dfc4fd1e6dd" ^
         --java-options "--enable-native-access=javafx.graphics" ^
         --java-options "-Dprism.order=sw,j2d" ^
         --java-options "-Djava.awt.im.style=on-the-spot" ^
         --java-options "-Djavafx.embed.singleThread=true" ^
         --description "%APP_NAME% - Coleta Demográfica Budista"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: jpackage failed
    pause
    exit /b 1
)
echo.

echo ========================================
echo Build completed successfully!
echo Output directory: output\
echo ========================================
echo.
pause
