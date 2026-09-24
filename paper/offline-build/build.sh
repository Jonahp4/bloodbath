#!/usr/bin/env bash
# Builds the Bloodbath Paper plugin without Gradle or network access. (The normal build is
# `./gradlew -p paper build`; this exists for environments that can't reach repo.papermc.io.)
#
# Compiles against the Paper API jars in offline-build/.cache/libs (or PAPER_LIBS) and packages:
#   dist/paper/Bloodbath-<version>.jar          the plugin, with the resource pack inside
#   dist/Bloodbath-ResourcePack-<version>.zip   the same pack, to host or merge yourself
# Both are byte-for-byte reproducible.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"   # paper/offline-build
ROOT="$(cd "$HERE/.." && pwd)"          # paper/
REPO="$(cd "$ROOT/.." && pwd)"          # repository root (shared resourcepack/, dist/)
VERSION="$(sed -n 's/^plugin_version=//p' "$ROOT/gradle.properties")"
LIBS="${PAPER_LIBS:-$HERE/.cache/libs}"
WORK="$HERE/.work"

if ! ls "$LIBS"/paper-api-*.jar >/dev/null 2>&1; then
	echo "No paper-api jar in $LIBS. Put the Paper API and its dependencies there, or set PAPER_LIBS." >&2
	exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/classes"
echo "> compiling against $(basename "$(ls "$LIBS"/paper-api-*.jar | head -1)")"
find "$ROOT/src/main/java" -name '*.java' | LC_ALL=C sort > "$WORK/sources"
javac --release 21 -proc:none -encoding UTF-8 -Xlint:all,-processing,-serial -Werror \
	-cp "$(ls "$LIBS"/*.jar | tr '\n' ':')" -d "$WORK/classes" @"$WORK/sources"

echo "> packaging"
python3 "$HERE/package.py" \
	--version "$VERSION" \
	--classes "$WORK/classes" \
	--resources "$ROOT/src/main/resources" \
	--pack "$REPO/resourcepack" \
	--license "$REPO/LICENSE" \
	--jar "$REPO/dist/paper/Bloodbath-$VERSION.jar" \
	--pack-zip "$REPO/dist/Bloodbath-ResourcePack-$VERSION.zip"
