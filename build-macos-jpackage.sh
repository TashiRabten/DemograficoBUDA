#!/bin/bash
# ========================================
# DemograficoBUDA - macOS PKG Builder
# Uses Maven + jlink + jpackage
# ========================================
set -euo pipefail

APP_NAME="DemograficoBUDA"
APP_VERSION="0.1.0"
MAIN_CLASS="com.buda.demografico.DemograficoBUDAMain"
MAIN_JAR="${APP_NAME}-1.0-SNAPSHOT-shaded.jar"
VENDOR="Tashi Rabten"

# Update these paths to match your environment
JDK_JMODS="/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home/jmods"
JAVAFX_JMODS="/Users/Shared/javafx-jmods-24.0.1"

BUILD_ROOT="/Users/Shared/${APP_NAME}"
INPUT_DIR="${BUILD_ROOT}/input"
OUTPUT_DIR="${BUILD_ROOT}/output-macos"
RUNTIME_DIR="${BUILD_ROOT}/runtime-macos"
ICON_PATH="src/main/resources/icons/pramana.icns"

MODULES="java.base,java.desktop,java.sql,java.net.http,java.prefs,javafx.controls,javafx.fxml,javafx.graphics"

echo "========================================"
echo "${APP_NAME} - macOS PKG Builder"
echo "========================================"
echo

echo "[1/5] Building shaded JAR..."
mvn package -DskipTests
echo

echo "[2/5] Preparing jpackage input..."
rm -rf "${INPUT_DIR}"
mkdir -p "${INPUT_DIR}"
cp "target/${MAIN_JAR}" "${INPUT_DIR}/${MAIN_JAR}"
echo

echo "[3/5] Creating custom JRE with jlink..."
if ! command -v jlink >/dev/null 2>&1; then
  echo "ERROR: jlink not found. Install a JDK (17+) that ships with jlink."
  exit 1
fi
rm -rf "${RUNTIME_DIR}"
jlink --module-path "${JDK_JMODS}:${JAVAFX_JMODS}" \
      --add-modules "${MODULES}" \
      --bind-services \
      --output "${RUNTIME_DIR}" \
      --strip-debug --compress=2 --no-header-files --no-man-pages
echo

echo "[4/5] Checking jpackage..."
if ! command -v jpackage >/dev/null 2>&1; then
  echo "ERROR: jpackage not found. Install a JDK (17+) that ships with jpackage."
  exit 1
fi
echo

echo "[5/5] Creating macOS PKG..."
rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"
jpackage --type pkg \
         --input "${INPUT_DIR}" \
         --dest "${OUTPUT_DIR}" \
         --name "${APP_NAME}" \
         --main-jar "${MAIN_JAR}" \
         --main-class "${MAIN_CLASS}" \
         --runtime-image "${RUNTIME_DIR}" \
         --icon "${ICON_PATH}" \
         --app-version "${APP_VERSION}" \
         --vendor "${VENDOR}" \
         --java-options "--enable-native-access=javafx.graphics" \
         --java-options "-Dprism.order=sw,j2d" \
         --java-options "-Djava.awt.im.style=on-the-spot" \
         --java-options "-Djavafx.embed.singleThread=true" \
         --description "${APP_NAME} - Coleta Demográfica Budista"
echo

echo "========================================"
echo "Build complete!"
echo "PKG: ${OUTPUT_DIR}/${APP_NAME}-${APP_VERSION}.pkg"
echo "========================================"
