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

set -eu

fail() {
    echo "DIST001-B4B ERROR: $*" >&2
    exit 1
}

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
EXPECTED_FEATURE=25
EXPECTED_JAVA_VERSION=25.0.4.1
EXPECTED_TRUFFLE_VERSION=25.3.4.1
EXPECTED_RUNTIME_CLASS=com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime

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

runtime_home=${PROTOS_DIST001_B4B_JAVA_HOME:-${JAVA_HOME:-}}
[ -n "$runtime_home" ] || fail \
    "selected primary GraalVM runtime not configured; set JAVA_HOME or PROTOS_DIST001_B4B_JAVA_HOME"

java_bin=$runtime_home/bin/java
javac_bin=$runtime_home/bin/javac
[ -x "$java_bin" ] || fail "selected runtime has no executable java: $java_bin"
[ -x "$javac_bin" ] || fail "selected runtime has no executable javac: $javac_bin"

props=$("$java_bin" -XshowSettings:properties -version 2>&1) ||
    fail "cannot inspect selected runtime"
feature=$(printf '%s\n' "$props" |
    sed -n 's/^[[:space:]]*java\.specification\.version = //p' | head -n 1)
java_version=$(printf '%s\n' "$props" |
    sed -n 's/^[[:space:]]*java\.version = //p' | head -n 1)
runtime_identity=$(printf '%s\n' "$props" | grep -Ei 'graalvm|java\.vendor|java\.vm\.name|java\.runtime\.name' || true)

[ "$feature" = "$EXPECTED_FEATURE" ] ||
    fail "selected runtime feature is ${feature:-unknown}, expected $EXPECTED_FEATURE"
[ "$java_version" = "$EXPECTED_JAVA_VERSION" ] ||
    fail "selected runtime java.version is ${java_version:-unknown}, expected $EXPECTED_JAVA_VERSION"
printf '%s\n' "$runtime_identity" | grep -Fqi GraalVM ||
    fail "selected runtime metadata does not identify GraalVM"

tmp=$(mktemp -d "${TMPDIR:-/tmp}/protos-dist001b4b.XXXXXX")
trap 'rm -rf "$tmp"' EXIT HUP INT TERM

toolchain_parent=$tmp/toolchain
probe_classes=$tmp/probe-classes
mkdir -p "$toolchain_parent" "$probe_classes"
unzip -q "$archive" -d "$toolchain_parent"

set -- "$toolchain_parent"/*
[ "$#" -eq 1 ] || fail "archive must extract exactly one top-level entry"
toolchain=$1
[ -d "$toolchain" ] || fail "archive top-level entry is not a directory"

repo_real=$(CDPATH= cd -- "$ROOT" && pwd -P)
toolchain_real=$(CDPATH= cd -- "$toolchain" && pwd -P)
case "$toolchain_real/" in
    "$repo_real"/*) fail "extracted toolchain is inside repository checkout" ;;
esac

runtime_meta=$toolchain/RUNTIME.txt
[ -f "$runtime_meta" ] || fail "extracted RUNTIME.txt is missing"

meta_feature=$(sed -n 's/^java_feature=//p' "$runtime_meta")
meta_truffle=$(sed -n 's/^truffle_runtime_version=//p' "$runtime_meta")
meta_runtime=$(sed -n 's/^optimizing_runtime=//p' "$runtime_meta")
[ "$meta_feature" = "$EXPECTED_FEATURE" ] ||
    fail "RUNTIME.txt java_feature drift: ${meta_feature:-missing}"
[ "$meta_truffle" = "$EXPECTED_TRUFFLE_VERSION" ] ||
    fail "RUNTIME.txt truffle_runtime_version drift: ${meta_truffle:-missing}"
[ "$meta_runtime" = HotSpotTruffleRuntime ] ||
    fail "RUNTIME.txt optimizing_runtime drift: ${meta_runtime:-missing}"

runtime_dir=$toolchain/lib/runtime
[ -d "$runtime_dir" ] || fail "distribution runtime directory missing"
runtime_jar=$runtime_dir/truffle-runtime-$EXPECTED_TRUFFLE_VERSION.jar
[ -f "$runtime_jar" ] || fail "exact optimizing runtime jar missing: $runtime_jar"
[ -z "$(find "$toolchain/lib" -maxdepth 1 -type d -name 'runtime.dist001b*-disabled' -print -quit)" ] ||
    fail "fresh extraction unexpectedly contains disabled-runtime smoke state"

launcher=$toolchain/bin/protos
[ -x "$launcher" ] || fail "extracted launcher is missing or not executable"

launcher_out=$tmp/launcher.stdout
launcher_err=$tmp/launcher.stderr
if (
    unset PROTOS_ALLOW_UNSUPPORTED_RUNTIME
    export PROTOS_JAVA="$java_bin"
    "$launcher" --version
) >"$launcher_out" 2>"$launcher_err"; then
    :
else
    rc=$?
    echo "--- selected-runtime launcher stdout ---" >&2
    cat "$launcher_out" >&2 || true
    echo "--- selected-runtime launcher stderr ---" >&2
    cat "$launcher_err" >&2 || true
    fail "launcher rejected selected runtime (exit=$rc)"
fi

grep -E '^Protos [^[:space:]]+' "$launcher_out" >/dev/null ||
    fail "launcher version marker missing on selected runtime"
if grep -Fqi 'unsupported runtime' "$launcher_err"; then
    cat "$launcher_err" >&2 || true
    fail "selected runtime unexpectedly used unsupported-runtime path"
fi

classpath=$toolchain/lib/protos.jar:$runtime_dir/*
"$javac_bin" \
    -cp "$classpath" \
    -d "$probe_classes" \
    "$ROOT/dist/Dist001RuntimeProbe.java"

probe_out=$tmp/probe.stdout
probe_err=$tmp/probe.stderr
if "$java_bin" \
    --enable-native-access=ALL-UNNAMED \
    -cp "$probe_classes:$classpath" \
    Dist001RuntimeProbe >"$probe_out" 2>"$probe_err"; then
    :
else
    rc=$?
    echo "--- optimizing runtime probe stdout ---" >&2
    cat "$probe_out" >&2 || true
    echo "--- optimizing runtime probe stderr ---" >&2
    cat "$probe_err" >&2 || true
    fail "optimizing runtime probe failed (exit=$rc)"
fi

actual_runtime=$(tail -n 1 "$probe_out" | tr -d '\r')
[ "$actual_runtime" = "$EXPECTED_RUNTIME_CLASS" ] || {
    echo "--- optimizing runtime probe stdout ---" >&2
    cat "$probe_out" >&2 || true
    echo "--- optimizing runtime probe stderr ---" >&2
    cat "$probe_err" >&2 || true
    fail "runtime class $actual_runtime != $EXPECTED_RUNTIME_CLASS"
}

echo "DIST_B4B_OUTSIDE_CHECKOUT_CHECK: PASS"
echo "DIST_SELECTED_JDK_CHECK: PASS java.version=$java_version"
echo "DIST_SELECTED_RUNTIME_GATE_CHECK: PASS"
echo "DIST_OPTIMIZER_JAR_INTACT_CHECK: PASS version=$EXPECTED_TRUFFLE_VERSION"
echo "DIST_OPTIMIZING_RUNTIME_CHECK: PASS class=$actual_runtime"
echo "DIST001_B4B_SMOKE: PASS"
