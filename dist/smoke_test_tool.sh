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
    echo "DIST001-B4A ERROR: $*" >&2
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

tmp=$(mktemp -d "${TMPDIR:-/tmp}/protos-dist001b4a.XXXXXX")
trap 'rm -rf "$tmp"' EXIT HUP INT TERM

toolchain_parent=$tmp/toolchain
project=$tmp/project
mkdir -p "$toolchain_parent" "$project"
unzip -q "$archive" -d "$toolchain_parent"

set -- "$toolchain_parent"/*
[ "$#" -eq 1 ] || fail "archive must extract exactly one top-level entry"
toolchain=$1
[ -d "$toolchain" ] || fail "archive top-level entry is not a directory"

repo_real=$(CDPATH= cd -- "$ROOT" && pwd -P)
toolchain_real=$(CDPATH= cd -- "$toolchain" && pwd -P)
project_real=$(CDPATH= cd -- "$project" && pwd -P)
case "$toolchain_real/" in "$repo_real"/*) fail "extracted toolchain is inside repository checkout" ;; esac
case "$project_real/" in "$repo_real"/*) fail "smoke project is inside repository checkout" ;; esac
case "$project_real/" in "$toolchain_real"/*) fail "smoke project is nested inside extracted toolchain" ;; esac
case "$toolchain_real/" in "$project_real"/*) fail "extracted toolchain is nested inside smoke project" ;; esac

launcher=$toolchain/bin/protos
[ -x "$launcher" ] || fail "extracted bin/protos is missing or not executable"
runtime_meta=$toolchain/RUNTIME.txt
[ -f "$runtime_meta" ] || fail "extracted RUNTIME.txt is missing"

expected_feature=$(sed -n 's/^java_feature=//p' "$runtime_meta")
expected_vendor=$(sed -n 's/^java_vendor_contains=//p' "$runtime_meta")
[ -n "$expected_feature" ] && [ -n "$expected_vendor" ] ||
    fail "RUNTIME.txt runtime gate is incomplete"

if [ -n "${PROTOS_JAVA:-}" ]; then
    java_bin=$PROTOS_JAVA
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    java_bin=$JAVA_HOME/bin/java
else
    java_bin=java
fi

runtime_properties=$("$java_bin" -XshowSettings:properties -version 2>&1) ||
    fail "cannot inspect validation Java runtime"
actual_feature=$(printf '%s\n' "$runtime_properties" |
    sed -n 's/^[[:space:]]*java\.specification\.version = //p' | head -n 1)
actual_vendor=$(printf '%s\n' "$runtime_properties" |
    sed -n 's/^[[:space:]]*java\.vendor = //p' | head -n 1)
actual_vendor_version=$(printf '%s\n' "$runtime_properties" |
    sed -n 's/^[[:space:]]*java\.vendor\.version = //p' | head -n 1)

runtime_matches=1
[ "$actual_feature" = "$expected_feature" ] || runtime_matches=0
printf '%s\n%s\n' "$actual_vendor" "$actual_vendor_version" |
    grep -Fqi "$expected_vendor" || runtime_matches=0

if [ "$runtime_matches" -eq 1 ]; then
    unset PROTOS_ALLOW_UNSUPPORTED_RUNTIME || true
    runtime_mode=supported
else
    # B4A owns bundled Test Tool portability only. Exact optimizing-runtime
    # validation remains B4B. Do not combine Truffle 24 optimizer jars with an
    # unselected host JDK; isolate them only in this disposable extraction.
    export PROTOS_ALLOW_UNSUPPORTED_RUNTIME=1
    runtime_dir=$toolchain/lib/runtime
    [ -d "$runtime_dir" ] || fail "distribution runtime directory is missing"
    disabled_dir=$toolchain/lib/runtime.dist001b4a-disabled
    mv "$runtime_dir" "$disabled_dir"
    mkdir "$runtime_dir"
    [ -n "$(find "$disabled_dir" -maxdepth 1 -type f -name '*.jar' -print -quit)" ] ||
        fail "distribution optimizer runtime directory contained no jars"
    runtime_mode=fallback-isolated
fi

stdout_file=$project/test.stdout
stderr_file=$project/test.stderr

if (cd "$project" && "$launcher" test) >"$stdout_file" 2>"$stderr_file"; then
    :
else
    rc=$?
    echo "DIST001-B4A command failed: Test Tool (exit=$rc)" >&2
    echo "--- test stdout ---" >&2
    cat "$stdout_file" >&2 || true
    echo "--- test stderr ---" >&2
    cat "$stderr_file" >&2 || true
    echo "--- end test diagnostics ---" >&2
    fail "extracted Test Tool failed"
fi

grep -Fx 'Protos test tool bootstrap' "$stdout_file" >/dev/null || {
    echo "--- test stdout ---" >&2
    cat "$stdout_file" >&2 || true
    echo "--- test stderr ---" >&2
    cat "$stderr_file" >&2 || true
    fail "Test Tool bootstrap marker missing"
}

grep -Fx 'test' "$stdout_file" >/dev/null || {
    echo "--- test stdout ---" >&2
    cat "$stdout_file" >&2 || true
    echo "--- test stderr ---" >&2
    cat "$stderr_file" >&2 || true
    fail "Test Tool argument marker missing"
}

if [ "$runtime_mode" = fallback-isolated ]; then
    grep -F 'unsupported runtime override enabled' "$stderr_file" >/dev/null ||
        fail "unsupported-host Test Tool smoke did not report runtime override"
fi

echo "DIST_B4A_OUTSIDE_CHECKOUT_CHECK: PASS"
echo "DIST_B4A_RUNTIME_ISOLATION_CHECK: PASS mode=$runtime_mode"
echo "DIST_BUNDLED_TEST_TOOL_CHECK: PASS"
echo "DIST001_B4A_SMOKE: PASS"
