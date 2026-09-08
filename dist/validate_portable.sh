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
    echo "DIST001-B5 ERROR: $*" >&2
    exit 1
}

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

source_mode=require-clean
artifact_mode=development
release_baseline=
archive=
while [ "$#" -gt 0 ]; do
    case "$1" in
        --archive)
            [ "$#" -ge 2 ] || fail "--archive requires a path"
            archive=$2
            shift 2
            ;;
        --allow-dirty-source)
            source_mode=allow-dirty
            shift
            ;;
        --require-clean-source)
            source_mode=require-clean
            shift
            ;;
        --public-prerelease)
            artifact_mode=public-prerelease
            shift
            ;;
        --release-baseline)
            [ "$#" -ge 2 ] || fail "--release-baseline requires an exact commit SHA"
            release_baseline=$2
            shift 2
            ;;
        *)
            fail "unknown argument: $1"
            ;;
    esac
done

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

command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required"
archive_sha_before=$(sha256sum "$archive" | awk '{print $1}')

case "$artifact_mode" in
    development)
        [ -z "$release_baseline" ] ||
            fail "--release-baseline is valid only with --public-prerelease"
        case "$source_mode" in
            require-clean)
                python3 "$ROOT/dist/verify_portable.py" \
                    --archive "$archive" \
                    --require-clean-source
                ;;
            allow-dirty)
                python3 "$ROOT/dist/verify_portable.py" \
                    --archive "$archive" \
                    --allow-dirty-source
                ;;
            *)
                fail "internal source-mode error: $source_mode"
                ;;
        esac
        ;;
    public-prerelease)
        [ "$source_mode" = require-clean ] ||
            fail "public prerelease B5 validation requires clean source"
        [ -n "$release_baseline" ] ||
            fail "--public-prerelease requires --release-baseline"
        python3 "$ROOT/dist/verify_portable.py" \
            --archive "$archive" \
            --public-prerelease \
            --release-baseline "$release_baseline" \
            --require-clean-source
        ;;
    *)
        fail "internal artifact-mode error: $artifact_mode"
        ;;
esac

# B3 and B4A deliberately use their existing host-sensitive fallback isolation
# on a non-selected development JDK. Each smoke extracts its own disposable copy;
# the archive itself must remain byte-for-byte unchanged.
sh "$ROOT/dist/smoke_cwd_package.sh" "$archive"
sh "$ROOT/dist/smoke_test_tool.sh" "$archive"

# B4B remains the exact optimizing-runtime gate, now against the already
# provisioned DIST002 primary runtime. The smoke defaults to JAVA_HOME; the
# historical PROTOS_DIST001_B4B_JAVA_HOME override remains optional only for
# explicit compatibility invocation. No fallback or unsupported override is
# accepted here.
sh "$ROOT/dist/smoke_optimizing_runtime.sh" "$archive"

archive_sha_after=$(sha256sum "$archive" | awk '{print $1}')
[ "$archive_sha_before" = "$archive_sha_after" ] ||
    fail "cross-slice validation modified the distribution archive"

echo "DIST_B5_SINGLE_ARCHIVE_CHECK: PASS sha256=$archive_sha_after"
echo "DIST_B5_ARTIFACT_MODE_CHECK: PASS mode=$artifact_mode"
echo "DIST_B5_ARCHIVE_IDENTITY_CHECK: PASS"
echo "DIST_B5_CWD_PACKAGE_CHECK: PASS"
echo "DIST_B5_TEST_TOOL_CHECK: PASS"
echo "DIST_B5_OPTIMIZING_RUNTIME_CHECK: PASS"
echo "DIST001_B5_CROSS_SLICE: PASS"
