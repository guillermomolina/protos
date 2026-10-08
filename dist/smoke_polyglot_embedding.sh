# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
# FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
# https://github.com/guillermomolina/protos
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

# PLAT054-3C: the distributed lib/protos.jar alone supports an option-free
# standard Polyglot embedding. The probe runs from a project directory outside
# the checkout with only lib/protos.jar and lib/runtime/*.jar on the class path
# (no protos/lib tree, no PROTOS_HOME), and checks the Core precedence
# protos.CoreRoot > language home > packaged resource.

set -eu

fail() {
    echo "PLAT054-3C ERROR: $*" >&2
    exit 1
}

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

archive=${1:-}
if [ -z "$archive" ]; then
    archive=$(find "$ROOT/target/distributions" -maxdepth 1 -type f \
        -name 'protos-*-posix-jvm.zip' | sort | tail -n 1)
fi
[ -n "${archive:-}" ] || fail "portable distribution archive not found"
[ -f "$archive" ] || fail "archive does not exist: $archive"
case "$archive" in
    /*) ;;
    *) archive=$ROOT/$archive ;;
esac

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    java_bin=$JAVA_HOME/bin/java
    javac_bin=$JAVA_HOME/bin/javac
else
    java_bin=java
    javac_bin=javac
fi

tmp=$(mktemp -d "${TMPDIR:-/tmp}/protos-plat054c.XXXXXX")
trap 'rm -rf "$tmp"' EXIT HUP INT TERM

extract=$tmp/extract
embed=$tmp/embed
home=$tmp/home
project=$tmp/project
cache=$tmp/resource-cache
java_tmp=$tmp/java-tmp
classes=$tmp/classes
mkdir -p "$extract" "$embed/runtime" "$home/protos" "$project" "$cache" "$java_tmp" "$classes"
unzip -q "$archive" -d "$extract"
set -- "$extract"/*
[ "$#" -eq 1 ] || fail "archive must extract exactly one top-level entry"
toolchain=$1

repo_real=$(CDPATH= cd -- "$ROOT" && pwd -P)
tmp_real=$(CDPATH= cd -- "$tmp" && pwd -P)
case "$tmp_real/" in "$repo_real"/*) fail "smoke directory is inside repository checkout" ;; esac

jar=$embed/protos.jar
cp "$toolchain/lib/protos.jar" "$jar"
cp "$toolchain"/lib/runtime/*.jar "$embed/runtime/"
for entry in files sha256 lib/core/Context.protos lib/test/Assertions.protos; do
    unzip -l "$jar" "META-INF/resources/protos/core/$entry" >/dev/null 2>&1 ||
        fail "lib/protos.jar does not package Core resource entry: $entry"
done

# A language home distinguishable from the packaged Core by one extra module.
cp -R "$toolchain/protos/lib" "$home/protos/lib"
mkdir -p "$home/protos/lib/plat054probe"
printf 'origin: "marker"\n' >"$home/protos/lib/plat054probe/Marker.protos"
# Only the copied JARs and the separate home remain; the toolchain tree is gone.
rm -rf "$extract"

classpath=$jar:$embed/runtime/*
"$javac_bin" -cp "$classpath" -d "$classes" "$ROOT/dist/Plat054EmbeddingProbe.java" ||
    fail "probe compilation failed"

run_probe() {
    label=$1
    shift
    if (cd "$project" && env -u PROTOS_HOME "$java_bin" \
        --enable-native-access=ALL-UNNAMED \
        -Dtruffle.UseFallbackRuntime=true \
        -Dpolyglot.engine.WarnInterpreterOnly=false \
        -Dpolyglot.engine.userResourceCache="$cache" \
        -Djava.io.tmpdir="$java_tmp" \
        "$@") >"$tmp/$label.stdout" 2>"$tmp/$label.stderr"; then
        :
    else
        echo "--- $label stdout ---" >&2
        cat "$tmp/$label.stdout" >&2 || true
        echo "--- $label stderr ---" >&2
        cat "$tmp/$label.stderr" >&2 || true
        fail "$label probe failed"
    fi
    cat "$tmp/$label.stdout"
}

probe_cp=$classes:$classpath
run_probe packaged -cp "$probe_cp" Plat054EmbeddingProbe packaged
unpacked=$(find "$cache" -path '*/lib/core/Context.protos' | wc -l)
[ "$unpacked" -eq 1 ] || fail "expected one unpacked packaged Core, found $unpacked"

run_probe home -Dorg.graalvm.language.protos.home="$home" -cp "$probe_cp" \
    Plat054EmbeddingProbe home
run_probe override -cp "$probe_cp" Plat054EmbeddingProbe override "$home/protos/lib/core"
run_probe invalid-override -cp "$probe_cp" Plat054EmbeddingProbe invalid-override \
    "$home/protos/lib/core/missing"

[ -z "$(find "$java_tmp" -name '*.protos' -print -quit)" ] ||
    fail "Core sources were materialized in the Java temporary directory"
[ -z "$(ls -A "$project")" ] || fail "the embedding wrote into the caller working directory"

echo "PLAT054_JAR_CONTAINS_CORE: PASS"
echo "PLAT054_OUTSIDE_CHECKOUT: PASS"
echo "PLAT054_3C_SMOKE: PASS"
