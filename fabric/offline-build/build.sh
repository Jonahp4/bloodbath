#!/usr/bin/env bash
# Builds the mod jar WITHOUT Fabric Loom / Mojang downloads.
#
# The normal way to build is `./gradlew build` (see README). This script exists for environments
# where maven.fabricmc.net / Mojang are unreachable: it compiles the sources against the small
# Yarn-named API stubs in ./stubs, remaps the bytecode to Fabric intermediary names with Remap.java,
# and verifies every Minecraft reference against the official 1.21.11 intermediary mappings.
#
# Needs: JDK 21+, curl. Downloads (cached in .cache/): ASM from Maven Central, the Yarn .mapping
# files it needs and the intermediary tiny file from raw.githubusercontent.com.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"          # fabric/
REPO="$(cd "$ROOT/.." && pwd)"          # repository root (shared resourcepack/, dist/)
CACHE="$HERE/.cache"
WORK="$HERE/.work"
VERSION="$(grep '^mod_version=' "$ROOT/gradle.properties" | cut -d= -f2)"
MC=1.21.11
OUT="$REPO/dist/fabric/unchartedsmp-bloodbath-$VERSION.jar"

fetch() { # url dest
	[ -s "$2" ] && return 0
	mkdir -p "$(dirname "$2")"
	curl -fsSL --retry 3 -o "$2.tmp" "$1" && mv "$2.tmp" "$2"
}

for a in asm asm-commons asm-tree; do
	fetch "https://repo1.maven.org/maven2/org/ow2/asm/$a/9.8/$a-9.8.jar" "$CACHE/lib/$a.jar"
done
fetch "https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/$MC.tiny" "$CACHE/intermediary-$MC.tiny"
while read -r cls; do
	[ -z "$cls" ] || [[ "$cls" == \#* ]] && continue
	fetch "https://raw.githubusercontent.com/FabricMC/yarn/$MC/mappings/net/minecraft/$cls.mapping" "$CACHE/yarn-$MC/$cls.mapping"
done < "$HERE/yarn-classes.txt"

rm -rf "$WORK" && mkdir -p "$WORK"/{stubs,named,remapped,tool,jar}
CP="$CACHE/lib/asm.jar:$CACHE/lib/asm-commons.jar:$CACHE/lib/asm-tree.jar"

echo "> compiling stubs"
javac -nowarn -d "$WORK/stubs" $(find "$HERE/stubs" -name '*.java')
echo "> compiling mod"
javac --release 21 -Xlint:all -Werror -cp "$WORK/stubs" -d "$WORK/named" $(find "$ROOT/src/main/java" -name '*.java')
echo "> remapping + verifying"
javac -nowarn -cp "$CP" -d "$WORK/tool" "$HERE/Remap.java"
java -cp "$CP:$WORK/tool" Remap "$CACHE/yarn-$MC" "$HERE/manual.mapping" "$CACHE/intermediary-$MC.tiny" \
	"$WORK/stubs" "$WORK/named" "$WORK/remapped" "$HERE/verified-refs.txt"

echo "> packaging"
cp -r "$WORK/remapped/." "$WORK/jar/"
cp -r "$ROOT/src/main/resources/." "$WORK/jar/"
mkdir -p "$WORK/jar/assets" && cp -r "$REPO/resourcepack/assets/unchartedsmp" "$WORK/jar/assets/"
sed -i "s/\${version}/$VERSION/" "$WORK/jar/fabric.mod.json"
cp "$REPO/LICENSE" "$WORK/jar/LICENSE_unchartedsmp"
mkdir -p "$WORK/jar/META-INF" "$(dirname "$OUT")"
printf 'Manifest-Version: 1.0\nFabric-Mapping-Namespace: intermediary\n' > "$WORK/jar/META-INF/MANIFEST.MF"
rm -f "$OUT"
(cd "$WORK/jar" && find . -exec touch -d '1980-01-02 00:00' {} + && jar --create --file "$OUT" --manifest META-INF/MANIFEST.MF $(ls -A | grep -v '^META-INF$'))
echo "Built $OUT"
