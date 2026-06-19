#!/bin/bash
source "$(dirname "$0")/.env"*
export mcdir=/run/media/ivan/btrfs/minecraft
export instance=$mcdir/versions/new
export name=$(printf $instance|rev|cut -d/ -f1|rev)
cd $instance

ARGS=()

# Динамічне виділення пам'яті (вся вільна ОЗП)
ARGS+=(-Xmx$(free -m | awk 'NR==2{print $4}')m)

# Обов'язково для ідентифікації jar-файлу
ARGS+=(-Dminecraft.client.jar=$name.jar)

# Оптимізації JVM для Java 25 (GraalVM)
ARGS+=(-XX:-OmitStackTraceInFastThrow)
ARGS+=(-XX:-DontCompileHugeMethods)
ARGS+=(-XX:MaxNodeLimit=240000)
ARGS+=(-XX:NodeLimitFudgeFactor=16000)
ARGS+=(-XX:+UseCompactObjectHeaders)

# Дозволи доступу до пам'яті для Java 25
ARGS+=(--sun-misc-unsafe-memory-access=allow)

# Фікси Forge для роботи на нових версіях Java
ARGS+=(-Dfml.ignoreInvalidMinecraftCertificates=true)
ARGS+=(-Dfml.ignorePatchDiscrepancies=true)

# Нативні бібліотеки
ARGS+=(-Djava.library.path=$instance/natives-linux-x86_64)

# Класпас (усі необхідні бібліотеки гри)
ARGS+=(-cp $mcdir/libraries/com/mojang/netty/1.8.8/netty-1.8.8.jar:$mcdir/libraries/com/mojang/realms/1.3.5/realms-1.3.5.jar:$mcdir/libraries/org/apache/httpcomponents/httpclient/4.3.3/httpclient-4.3.3.jar:$mcdir/libraries/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3.jar:$mcdir/libraries/org/apache/httpcomponents/httpcore/4.3.2/httpcore-4.3.2.jar:$mcdir/libraries/java3d/vecmath/1.3.1/vecmath-1.3.1.jar:$mcdir/libraries/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar:$mcdir/libraries/com/ibm/icu/icu4j-core-mojang/51.2/icu4j-core-mojang-51.2.jar:$mcdir/libraries/net/sf/jopt-simple/jopt-simple/4.5/jopt-simple-4.5.jar:$mcdir/libraries/com/paulscode/codecjorbis/20101023/codecjorbis-20101023.jar:$mcdir/libraries/com/paulscode/codecwav/20101023/codecwav-20101023.jar:$mcdir/libraries/com/paulscode/libraryjavasound/20101123/libraryjavasound-20101123.jar:$mcdir/libraries/com/paulscode/librarylwjglopenal/20100824/librarylwjglopenal-20100824.jar:$mcdir/libraries/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar:$mcdir/libraries/io/netty/netty-all/4.0.10.Final/netty-all-4.0.10.Final.jar:$mcdir/libraries/commons-codec/commons-codec/1.9/commons-codec-1.9.jar:$mcdir/libraries/net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar:$mcdir/libraries/net/java/jutils/jutils/1.0.0/jutils-1.0.0.jar:$mcdir/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar:$mcdir/libraries/com/mojang/authlib/1.5.21/authlib-1.5.21.jar:$mcdir/libraries/org/apache/logging/log4j/log4j-api/2.0-beta9/log4j-api-2.0-beta9.jar:$mcdir/libraries/org/apache/logging/log4j/log4j-core/2.0-beta9/log4j-core-2.0-beta9.jar:$mcdir/libraries/org/lwjgl/lwjgl-freetype-natives-linux/3.4.2-20260213.173042-1/lwjgl-freetype-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-freetype/3.4.2-20260213.173042-1/lwjgl-freetype-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-harfbuzz/3.4.2-20260213.173042-1/lwjgl-harfbuzz-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-jemalloc-natives-linux/3.4.2-20260213.173042-1/lwjgl-jemalloc-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-jemalloc/3.4.2-20260213.173042-1/lwjgl-jemalloc-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-natives-linux/3.4.2-20260213.173042-1/lwjgl-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-nuklear-natives-linux/3.4.2-20260213.173042-1/lwjgl-nuklear-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-nuklear/3.4.2-20260213.173042-1/lwjgl-nuklear-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-openal-natives-linux/3.4.2-20260213.173042-1/lwjgl-openal-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-openal/3.4.2-20260213.173042-1/lwjgl-openal-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-opengl-natives-linux/3.4.2-20260213.173042-1/lwjgl-opengl-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-opengl/3.4.2-20260213.173042-1/lwjgl-opengl-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-sdl-natives-linux/3.4.2-20260213.173042-1/lwjgl-sdl-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-sdl/3.4.2-20260213.173042-1/lwjgl-sdl-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-spng-natives-linux/3.4.2-20260213.173042-1/lwjgl-spng-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-spng/3.4.2-20260213.173042-1/lwjgl-spng-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-stb-natives-linux/3.4.2-20260213.173042-1/lwjgl-stb-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-stb/3.4.2-20260213.173042-1/lwjgl-stb-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-tinyfd-natives-linux/3.4.2-20260213.173042-1/lwjgl-tinyfd-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-tinyfd/3.4.2-20260213.173042-1/lwjgl-tinyfd-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-zstd-natives-linux/3.4.2-20260213.173042-1/lwjgl-zstd-natives-linux-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl-zstd/3.4.2-20260213.173042-1/lwjgl-zstd-3.4.2-20260213.173042-1.jar:$mcdir/libraries/org/lwjgl/lwjgl/3.4.2-20260213.173042-1/lwjgl-3.4.2-20260213.173042-1.jar:$mcdir/libraries/tv/twitch/twitch/5.16/twitch-5.16.jar:$mcdir/libraries/com/github/GTNewHorizons/lwjgl3ify/3.0.13/lwjgl3ify-3.0.13-forgePatches.jar:$mcdir/libraries/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar:$mcdir/libraries/com/typesafe/akka/akka-actor_2.11/2.3.3/akka-actor_2.11-2.3.3.jar:$mcdir/libraries/com/typesafe/config/1.2.1/config-1.2.1.jar:$mcdir/libraries/org/scala-lang/scala-actors-migration_2.11/1.1.0/scala-actors-migration_2.11-1.1.0.jar:$mcdir/libraries/org/scala-lang/scala-compiler/2.11.1/scala-compiler-2.11.1.jar:$mcdir/libraries/org/scala-lang/plugins/scala-continuations-library_2.11/1.0.2/scala-continuations-library_2.11-1.0.2.jar:$mcdir/libraries/org/scala-lang/plugins/scala-continuations-plugin_2.11.1/1.0.2/scala-continuations-plugin_2.11.1-1.0.2.jar:$mcdir/libraries/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar:$mcdir/libraries/org/scala-lang/scala-parser-combinators_2.11/1.0.1/scala-parser-combinators_2.11-1.0.1.jar:$mcdir/libraries/org/scala-lang/scala-reflect/2.11.1/scala-reflect-2.11.1.jar:$mcdir/libraries/org/scala-lang/scala-swing_2.11/1.0.1/scala-swing_2.11-1.0.1.jar:$mcdir/libraries/org/scala-lang/scala-xml_2.11/1.0.2/scala-xml_2.11-1.0.2.jar:$mcdir/libraries/lzma/lzma/0.0.1/lzma-0.0.1.jar:$mcdir/libraries/com/google/guava/guava/17.0/guava-17.0.jar:$instance/$name.jar)

# GTNH завантажувач класів та доступ до нативної пам'яті (LWJGL3)
ARGS+=(-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader)
ARGS+=(--enable-native-access ALL-UNNAMED)

# Відкриття внутрішніх модулів для сумісності з новою Java
ARGS+=(--add-opens java.base/java.io=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.lang.invoke=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.lang.ref=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.lang.reflect=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.lang=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.net.spi=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.net=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.nio.channels=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.nio.charset=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.nio.file=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.nio=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.text=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.time.chrono=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.time.format=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.time.temporal=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.time.zone=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.time=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.util.concurrent.locks=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.util.jar=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.util.zip=ALL-UNNAMED)
ARGS+=(--add-opens java.base/java.util=ALL-UNNAMED)
ARGS+=(--add-opens java.base/jdk.internal.loader=ALL-UNNAMED)
ARGS+=(--add-opens java.base/jdk.internal.misc=ALL-UNNAMED)
ARGS+=(--add-opens java.base/jdk.internal.ref=ALL-UNNAMED)
ARGS+=(--add-opens java.base/jdk.internal.reflect=ALL-UNNAMED)
ARGS+=(--add-opens java.base/sun.nio.ch=ALL-UNNAMED)
ARGS+=(--add-opens java.desktop/com.sun.imageio.plugins.png=ALL-UNNAMED)
ARGS+=(--add-opens java.desktop/sun.awt.image=ALL-UNNAMED)
ARGS+=(--add-opens java.desktop/sun.awt=ALL-UNNAMED)
ARGS+=(--add-opens java.sql.rowset/javax.sql.rowset.serial=ALL-UNNAMED)
ARGS+=(--add-opens jdk.dynalink/jdk.dynalink.beans=ALL-UNNAMED)
ARGS+=(--add-opens jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED,java.naming)

exec /usr/lib/jvm/java-25-graalvm/bin/java "${ARGS[@]}" \
    com.gtnewhorizons.retrofuturabootstrap.MainStartOnFirstThread \
    --username $MC_USERNAME \
    --version $name \
    --gameDir $instance \
    --assetsDir $mcdir/assets \
    --assetIndex 1.7.10 \
    --uuid $MC_UUID \
    --accessToken $MC_ACCESS_TOKEN \
    --userProperties {} \
    --userType $MC_USER_TYPE \
    --tweakClass cpw.mods.fml.common.launcher.FMLTweaker
