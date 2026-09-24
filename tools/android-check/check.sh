#!/usr/bin/env bash
# Compilazione di controllo di :androidApp dove l'SDK Android non c'è (D44).
#
# Compila i sorgenti Kotlin dell'app contro il framework vero di Android 15 — il jar
# `android-all` che Robolectric pubblica su Maven Central — con un R generato dalle
# risorse e due interfacce finte al posto di androidx.sqlite. Trova gli errori di API,
# di tipi e di import prima che li trovi il committente.
#
# NON controlla: risorse e manifest (serve aapt2), R8, lint, e il comportamento.
#
# Uso: tools/android-check/check.sh     (dalla radice del repository)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/inknote-android-check"
WORK="$CACHE/work"  # fuori dal repository: un build di Gradle dentro un altro lo confonde
ANDROID_ALL="15-robolectric-13954326"
mkdir -p "$CACHE" "$WORK/gen"

fetch() { # url file: Maven Central limita le richieste, quindi si riprova con calma
  local url="$1" out="$2"
  [ -s "$out" ] && [ "$(stat -c %s "$out")" -gt 10000 ] && return 0
  for i in 1 2 3 4 5 6; do
    curl -sS -o "$out" "$url" || true
    [ "$(stat -c %s "$out" 2>/dev/null || echo 0)" -gt 10000 ] && return 0
    echo "download limitato, riprovo fra $((i * 20)) s…" >&2; sleep $((i * 20))
  done
  echo "download fallito: $url" >&2; return 1
}

fetch "https://repo1.maven.org/maven2/org/robolectric/android-all/$ANDROID_ALL/android-all-$ANDROID_ALL.jar" "$CACHE/android-all.jar"
fetch "https://repo1.maven.org/maven2/app/cash/sqldelight/android-driver/2.0.2/android-driver-2.0.2-release.aar" "$CACHE/driver.aar"
(cd "$CACHE" && unzip -o -q driver.aar classes.jar && mv classes.jar driver.jar)

(cd "$ROOT" && ./gradlew -q :core:model:jvmJar :core:ink:jvmJar :core:geometry:jvmJar :core:capture:jvmJar :core:store:jvmJar)
RUNTIME="$(find "$HOME/.gradle/caches/modules-2" -name 'runtime-jvm-2.0.2.jar' | head -1)"

# R: un intero finto per ogni risorsa. Basta al compilatore, non dice niente su aapt.
python3 - "$ROOT/androidApp/src/main/res" "$WORK/gen/R.kt" <<'PY'
import glob, os, re, sys
res, out = sys.argv[1], sys.argv[2]
kinds = {}
for f in glob.glob(res + '/values*/strings.xml'):
    for n in re.findall(r'<string name="([^"]+)"', open(f).read()):
        kinds.setdefault('string', set()).add(n)
for d in os.listdir(res):
    base = d.split('-')[0]
    if base in ('drawable', 'layout', 'xml', 'mipmap'):
        for f in os.listdir(os.path.join(res, d)):
            kinds.setdefault(base, set()).add(f.rsplit('.', 1)[0])
for f in glob.glob(res + '/layout/*.xml'):
    for n in re.findall(r'@\+id/([A-Za-z0-9_]+)', open(f).read()):
        kinds.setdefault('id', set()).add(n)
lines, i = ['package app.inknote.android', '', 'object R {'], 0x7f000000
for kind, names in sorted(kinds.items()):
    lines.append(f'    object {kind} {{')
    for n in sorted(names):
        i += 1
        lines.append(f'        const val {n}: Int = {i}')
    lines.append('    }')
open(out, 'w').write('\n'.join(lines + ['}', '']))
PY

cat > "$WORK/settings.gradle.kts" <<KTS
rootProject.name = "android-check"
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
KTS
cat > "$WORK/build.gradle.kts" <<KTS
plugins { kotlin("jvm") version "2.1.21" }
kotlin { jvmToolchain(21) }
sourceSets["main"].kotlin.srcDirs("$ROOT/androidApp/src/main/kotlin", "gen", "$ROOT/tools/android-check/stubs")
dependencies {
    compileOnly(files("$CACHE/android-all.jar", "$CACHE/driver.jar"))
    implementation(files(
        "$ROOT/core/model/build/libs/model-jvm.jar",
        "$ROOT/core/ink/build/libs/ink-jvm.jar",
        "$ROOT/core/geometry/build/libs/geometry-jvm.jar",
        "$ROOT/core/capture/build/libs/capture-jvm.jar",
        "$ROOT/core/store/build/libs/store-jvm.jar",
        "$RUNTIME",
    ))
}
KTS
cp -r "$ROOT/gradle" "$WORK/" && cp "$ROOT/gradlew" "$WORK/"
cd "$WORK" && ./gradlew -q compileKotlin && echo "androidApp: compila contro Android 15."
