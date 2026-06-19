#!/bin/bash
cd "$(dirname "$0")"

FORGE_VER="10.13.4.1614"
FORGE_JAR="forge-1.7.10-$FORGE_VER-1.7.10-universal.jar"
INSTALLER_JAR="forge-1.7.10-$FORGE_VER-1.7.10-installer.jar"
PACKWIZ_JAR="packwiz-installer-bootstrap.jar"
PACK_URL="https://raw.githubusercontent.com/4iki40k/NULP-linux-minecraft/main/pack.toml"
JAVA_CMD="/usr/lib/jvm/java-8-openjdk/jre/bin/java"
MIN_RAM="2G"
MAX_RAM="4G"

if [ ! -f "$FORGE_JAR" ]; then
    echo "Installing $FORGE_JAR"
    wget -q --show-progress "https://maven.minecraftforge.net/net/minecraftforge/forge/1.7.10-$FORGE_VER-1.7.10/$INSTALLER_JAR"
    $JAVA_CMD -jar "$INSTALLER_JAR" --installServer
fi

if [ ! -f "$PACKWIZ_JAR" ]; then
    echo "Downloading Packwiz Installer"
    wget -q --show-progress "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/$PACKWIZ_JAR"
fi

if [ ! -f "eula.txt" ] || ! grep -q "eula=true" "eula.txt"; then
    echo "Accepting the EULA"
    echo "eula=true" > eula.txt
fi

echo "Synchronization of mods"
$JAVA_CMD -jar "$PACKWIZ_JAR" -g -s server "$PACK_URL"

echo "--- Запуск сервера... ---"
$JAVA_CMD -Xms${MIN_RAM} -Xmx${MAX_RAM} -jar "$FORGE_JAR" nogui
