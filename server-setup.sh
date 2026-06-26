#!/bin/bash
# nulp-minecraft — set up + run the Crucible server on modern Java. Run from a clone of this repo.
#
# Asks where to get the Crucible core, then runs it on a modern JVM (Oracle GraalVM preferred — its
# Graal JIT generates chunks ~9-15% faster). The pack's Mixins need a core with the StackMapTable fix
# to verify on Java 24/25; a core without it is warned about.
#
# Env: CRUCIBLE_MODE=staging|own  CRUCIBLE_JAR=/path  CRUCIBLE_JAR_URL=url  CRUCIBLE_JAVA=/path/to/java
set -u
cd "$(dirname "$0")"
die() { echo "error: $*" >&2; exit 1; }
dl()  { command -v curl >/dev/null && curl -fL --retry 3 -o "$1" "$2" || wget -qO "$1" "$2"; }

PACK_URL="https://raw.githubusercontent.com/GribanovIvan/nulp-minecraft/lwjgl3ify-support/pack.toml"
CORE=crucible.jar

# --- core source ---
MODE="${CRUCIBLE_MODE:-}"
if [ -z "$MODE" ]; then
  read -rp "Crucible core: [1] latest staging from GitHub  [2] your own jar  > " a
  case "$a" in 2) MODE=own;; *) MODE=staging;; esac
fi

if [ ! -f "$CORE" ]; then
  if [ "$MODE" = staging ]; then
    url=$(curl -fsSL https://api.github.com/repos/CrucibleMC/Crucible/releases \
      | grep -oE '"browser_download_url": *"[^"]*Crucible-1\.7\.10-[^"]*\.jar"' \
      | grep -viE 'sources|javadoc' | head -1 | grep -oE 'https://[^"]*')
    [ -n "$url" ] || die "no Crucible jar in the latest release"
    echo ">> $(basename "$url")"; dl "$CORE" "$url" || die "download failed"
  else
    src="${CRUCIBLE_JAR:-}" url="${CRUCIBLE_JAR_URL:-}"
    [ -z "$src$url" ] && { read -rp "path or URL to your core jar: " in; case "$in" in http*) url=$in;; *) src=$in;; esac; }
    if [ -n "$url" ]; then dl "$CORE" "$url" || die "download failed"
    else [ -f "$src" ] || die "no such jar: $src"; cp "$src" "$CORE"; fi
  fi
fi

# Warn if the core lacks the StackMapTable fix (the pack will likely VerifyError on modern Java).
if command -v unzip >/dev/null && ! unzip -l "$CORE" 2>/dev/null | grep -q 'io/github/crucible/asm/'; then
  echo "!! this core has no StackMapTable fix — the pack may crash with a VerifyError on modern Java"
fi
# The core shades me.eigenraven.lwjgl3ify.api; the lwjgl3ify mod ships its own copy and its preInit
# identity check throws if they differ. Strip the shaded copy so both resolve from the mod jar.
if command -v zip >/dev/null && unzip -l "$CORE" 2>/dev/null | grep -q 'me/eigenraven/'; then
  zip -dq "$CORE" 'me/eigenraven/*' || true
fi

# --- Java: GraalVM, else any modern JDK ---
JAVA="${CRUCIBLE_JAVA:-$(for j in /usr/lib/jvm/*graalvm*/bin/java /usr/lib/jvm/java-2[1-9]*/bin/java /usr/lib/jvm/jdk-2*/bin/java; do [ -x "$j" ] && { echo "$j"; break; }; done)}"
[ -x "$JAVA" ] || JAVA=$(command -v java)
[ -x "$JAVA" ] || die "no modern JDK; install Oracle GraalVM 25 or set CRUCIBLE_JAVA"
"$JAVA" -version 2>&1 | grep -qi graalvm || echo ">> note: $("$JAVA" -version 2>&1|head -1) — Oracle GraalVM is ~9-15% faster at chunk-gen"

[ -f java24args.txt ] || cat > java24args.txt <<'ARGS'
-Dfile.encoding=UTF-8
-Dcrucible.weAreJava9=true
--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/jdk.internal.reflect=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-modules=jdk.dynalink
--add-opens=jdk.dynalink/jdk.dynalink.beans=ALL-UNNAMED
--add-modules=java.sql.rowset
--add-opens=java.sql.rowset/javax.sql.rowset.serial=ALL-UNNAMED
ARGS

# --- libraries (the core self-installs them on first run) ---
if [ ! -d libraries ] || [ -z "$(ls -A libraries 2>/dev/null)" ]; then
  echo ">> installing libraries..."; "$JAVA" @java24args.txt -jar "$CORE" nogui >/tmp/crucible-install.log 2>&1 || true
fi

# --- sync mods ---
[ -f packwiz-installer-bootstrap.jar ] || dl packwiz-installer-bootstrap.jar \
  "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar"
"$JAVA" -jar packwiz-installer-bootstrap.jar -g -s server "$PACK_URL"
ls mods/lwjgl3ify*.jar >/dev/null 2>&1 || echo "!! lwjgl3ify missing from mods — the pack should provide it"

grep -q eula=true eula.txt 2>/dev/null || echo eula=true > eula.txt
exec "$JAVA" @java24args.txt -Xms2G -Xmx4G -jar "$CORE" nogui
