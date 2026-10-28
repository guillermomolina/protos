#!/usr/bin/env sh
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

protos_install_error() {
    printf 'protos-install: %s\n' "$*" >&2
    return 1
}

# Read the JSON token stream structurally. Only top-level release-object
# fields are eligible; nested asset fields and release-body text are ignored.
# Output is withheld until the complete JSON array has been consumed.
protos_install_release_tags() {
    awk '
        function finish_literal() {
            if (depth == 2 && expecting && key == "draft") {
                draft = literal
            }
            literal = ""
            expecting = 0
            key = ""
        }

        function finish_string() {
            if (depth == 2 && role == "key") {
                key = token
            } else if (depth == 2 && role == "value") {
                if (key == "tag_name") {
                    tag = token
                }
                expecting = 0
                key = ""
            }
            token = ""
            role = ""
        }

        BEGIN {
            depth = 0
            quoted = 0
            escaped = 0
            expecting = 0
            invalid = 0
            root_array = 0
            count = 0
            literal = ""
            token = ""
            key = ""
            role = ""
        }

        {
            for (i = 1; i <= length($0); i++) {
                c = substr($0, i, 1)

                if (quoted) {
                    if (escaped) {
                        escaped = 0
                        if (role != "") {
                            token = token "\\" c
                        }
                    } else if (c == "\\") {
                        escaped = 1
                    } else if (c == "\"") {
                        quoted = 0
                        finish_string()
                    } else if (role != "") {
                        token = token c
                    }
                    continue
                }

                if (c ~ /^[ \t\r]$/) {
                    if (literal != "") {
                        finish_literal()
                    }
                    continue
                }

                if (c == "\"") {
                    quoted = 1
                    token = ""
                    role = ""
                    if (depth == 2) {
                        role = expecting ? "value" : "key"
                    }
                    continue
                }

                if (depth == 2 && expecting &&
                    c ~ /^[A-Za-z0-9-]$/) {
                    literal = literal c
                    continue
                }

                if (literal != "") {
                    finish_literal()
                }

                if (c == "{" || c == "[") {
                    if (depth == 0 && c == "[") {
                        root_array = 1
                    }

                    if (depth == 2) {
                        expecting = 0
                        key = ""
                    }

                    depth++
                    container[depth] = c

                    if (depth == 2 && c == "{") {
                        tag = ""
                        draft = ""
                        key = ""
                        expecting = 0
                    }
                } else if (c == "}" || c == "]") {
                    expected = c == "}" ? "{" : "["

                    if (depth < 1 || container[depth] != expected) {
                        invalid = 1
                    }

                    if (depth == 2 && c == "}" &&
                        tag ~ /^v[0-9]+[.][0-9]+[.][0-9]+$/ &&
                        draft == "false") {
                        tags[++count] = tag
                    }

                    depth--
                    if (depth < 0) {
                        invalid = 1
                        depth = 0
                    }
                } else if (c == ":" && depth == 2) {
                    expecting = 1
                } else if (c == "," && depth == 2) {
                    expecting = 0
                    key = ""
                }
            }
        }

        END {
            if (invalid || quoted || depth != 0 || !root_array ||
                count == 0) {
                print "invalid or empty GitHub release response" > "/dev/stderr"
            } else {
                for (i = 1; i <= count; i++) {
                    print tags[i]
                }
            }
        }
    '
}

# Order published numeric release tags by descending semantic version.
# This uses POSIX awk and does not depend on sort -V, jq, or Python.
protos_install_tags_descending() {
    awk -F. '
        function better(candidate, previous) {
            if (previous == 0) {
                return 1
            }

            if (major[candidate] != major[previous]) {
                return major[candidate] > major[previous]
            }

            if (minor[candidate] != minor[previous]) {
                return minor[candidate] > minor[previous]
            }

            if (patch[candidate] != patch[previous]) {
                return patch[candidate] > patch[previous]
            }

            return candidate < previous
        }

        {
            count++
            tags[count] = $0

            major_text = $1
            sub(/^v/, "", major_text)

            major[count] = major_text + 0
            minor[count] = $2 + 0
            patch[count] = $3 + 0
        }

        END {
            for (position = 1; position <= count; position++) {
                best = 0

                for (candidate = 1; candidate <= count; candidate++) {
                    if (!used[candidate] && better(candidate, best)) {
                        best = candidate
                    }
                }

                if (best == 0) {
                    break
                }

                print tags[best]
                used[best] = 1
            }
        }
    '
}

# Parse the exact public release envelope. No downloaded data is evaluated
# as shell code. Only validated, selected Native fields are emitted.
protos_install_manifest_native() {
    awk -v wanted_tag="$2" -v host_os="$3" -v host_arch="$4" '
        function hex_digest(value) {
            return length(value) == 64 &&
                   value ~ /^[0123456789abcdef]+$/
        }

        function missing_or_invalid(value) {
            return value == "" || index(value, "\r") != 0
        }

        {
            line = $0
            equals = index(line, "=")

            if (equals <= 1 || index(line, "\r") != 0) {
                invalid = 1
            } else {
                key = substr(line, 1, equals - 1)
                value = substr(line, equals + 1)

                if (key !~ /^[A-Za-z0-9_.-]+$/ ||
                    (key in data)) {
                    invalid = 1
                } else {
                    data[key] = value
                }
            }
        }

        END {
            version = substr(wanted_tag, 2)

            if (data["release_envelope_format"] != \
                    "protos-public-prerelease-envelope-v2" ||
                data["distribution_model"] != "JVM_PLUS_NATIVE" ||
                data["source_repository"] != "https://github.com/guillermomolina/protos" ||
                data["release_tag"] != wanted_tag ||
                data["release_version"] != version ||
                version !~ /^[0-9]+[.][0-9]+[.][0-9]+$/ ||
                length(data["source_revision"]) != 40 ||
                data["source_revision"] !~ /^[0123456789abcdef]+$/ ||
                data["asset_count"] !~ /^[0-9]+$/) {
                invalid = 1
            }

            count = data["asset_count"] + 0

            if (count < 2 || count > 24) {
                invalid = 1
            }

            portable_count = 0
            native_count = 0
            selected_count = 0

            if (!invalid) {
                for (i = 0; i < count; i++) {
                    prefix = "asset." i "."
                    kind = data[prefix "kind"]

                    if (kind == "portable-jvm") {
                        portable_count++
                    } else if (kind == "native") {
                        native_count++

                        if (data[prefix "target_os"] == host_os &&
                            data[prefix "target_arch"] == host_arch) {
                            selected_count++
                            selected_prefix = prefix
                        }
                    } else {
                        invalid = 1
                    }
                }
            }

            if (portable_count != 1 ||
                native_count < 1 ||
                selected_count != 1) {
                invalid = 1
            }

            p = selected_prefix

            if (!invalid) {
                expected_archive = "protos-" version "-native-" \
                                   host_os "-" host_arch ".zip"

                platform = data[p "target_os"] "/" \
                           data[p "target_arch"] "/" \
                           data[p "libc_family"] "/" \
                           data[p "libc_abi_min"] "/" \
                           data[p "linkage"] "/" \
                           data[p "cpu_isa_assumption"]

                if (data[p "role"] != "recommended-first-run" ||
                    data[p "distribution_format"] != \
                        "protos-native-image-posix-v1" ||
                    data[p "external_java_required"] != "false" ||
                    data[p "native_runtime_kind"] != \
                        "graalvm-native-image-truffle" ||
                    data[p "libc_family"] != "glibc" ||
                    data[p "linkage"] != "dynamic" ||
                    data[p "cpu_isa_assumption"] != "compatibility" ||
                    data[p "platform_identity"] != platform ||
                    data[p "archive"] != expected_archive ||
                    data[p "checksum"] != expected_archive ".sha256" ||
                    !hex_digest(data[p "archive_sha256"]) ||
                    !hex_digest(data[p "checksum_sha256"]) ||
                    data[p "libc_abi_min"] !~ \
                        /^[0-9]+[.][0-9]+([.][0-9]+)?$/ ||
                    missing_or_invalid(data[p "elf_interpreter"]) ||
                    substr(data[p "elf_interpreter"], 1, 1) != "/" ||
                    missing_or_invalid(data[p "dt_needed"]) ||
                    index(data[p "dt_needed"], "libc.so.6") == 0 ||
                    missing_or_invalid(data[p "shared_library_closure"]) ||
                    missing_or_invalid(data[p "glibc_symbol_versions"]) ||
                    missing_or_invalid(data[p "libc_abi_observed_max"])) {
                    invalid = 1
                }
            }

            if (!invalid) {
                print "RELEASE_TAG=" wanted_tag
                print "RELEASE_VERSION=" version
                print "SOURCE_REVISION=" data["source_revision"]
                print "NATIVE_ARCHIVE=" data[p "archive"]
                print "NATIVE_SHA256=" data[p "archive_sha256"]
                print "CHECKSUM_FILE=" data[p "checksum"]
                print "CHECKSUM_SHA256=" data[p "checksum_sha256"]
                print "MIN_GLIBC=" data[p "libc_abi_min"]
                print "CPU_ISA=" data[p "cpu_isa_assumption"]
                print "PLATFORM=" data[p "platform_identity"]
                print "ELF_INTERPRETER=" data[p "elf_interpreter"]
            }
        }
    ' "$1"
}

# The release contract determines the minimum glibc, not this script.
protos_install_glibc_compatible() {
    awk -v current="$1" -v required="$2" '
        BEGIN {
            current_count = split(current, c, ".")
            required_count = split(required, r, ".")
            compatible = 1

            if (current_count < 2 || current_count > 3 ||
                required_count < 2 || required_count > 3) {
                compatible = 0
            }

            for (i = 1; i <= 3; i++) {
                if (c[i] !~ /^[0-9]+$/ || r[i] !~ /^[0-9]+$/) {
                    if (i <= 2) {
                        compatible = 0
                    }
                }
            }

            if (compatible) {
                for (i = 1; i <= 3; i++) {
                    actual = c[i] + 0
                    minimum = r[i] + 0

                    if (actual > minimum) {
                        break
                    }

                    if (actual < minimum) {
                        compatible = 0
                        break
                    }
                }
            }

            if (compatible) {
                print "yes"
            } else {
                print "no"
            }
        }
    '
}

# Validate the ZIP entry names before any member is extracted.
protos_install_archive_layout() {
    vi_zip=$1
    vi_root=$2
    vi_work=$3

    if ! unzip -Z1 "$vi_zip" > "$vi_work/entries.txt" 2>/dev/null; then
        protos_install_error "cannot read Native ZIP entry names"
        return 1
    fi

    if ! unzip -Z -l "$vi_zip" > "$vi_work/permissions.txt" 2>/dev/null; then
        protos_install_error "cannot read Native ZIP entry types"
        return 1
    fi

    vi_layout=$(awk -v prefix="$vi_root/" '
        {
            name = $0
            count++

            if (index(name, prefix) != 1 ||
                name == prefix ||
                index(name, "\\") != 0 ||
                name ~ /[[:cntrl:]]/ ||
                name ~ /\/\// ||
                name ~ /(^|\/)[.][.]?(\/|$)/ ||
                name ~ /(^|\/)(target|[.]git)(\/|$)/ ||
                name ~ /[.]jar$/ ||
                (name in seen)) {
                invalid = 1
            }

            seen[name] = 1

            if (index(name, prefix "protos/lib/") == 1) {
                has_lib = 1
            }
            if (index(name, prefix "protos/tools/") == 1) {
                has_tools = 1
            }
            if (index(name, prefix "protos/tests/") == 1) {
                has_tests = 1
            }
        }

        END {
            required[1] = prefix "bin/protos"
            required[2] = prefix "libexec/protos-native"
            required[3] = prefix "LICENSE.TXT"
            required[4] = prefix "README.md"
            required[5] = prefix "SOURCE.txt"
            required[6] = prefix "RUNTIME.txt"
            required[7] = prefix "DEPENDENCIES.txt"
            required[8] = prefix "SHA256SUMS"

            for (i = 1; i <= 8; i++) {
                if (!(required[i] in seen)) {
                    invalid = 1
                }
            }

            if (!has_lib || !has_tools || !has_tests || count < 11) {
                invalid = 1
            }

            if (invalid) {
                print "FAIL"
            } else {
                print "PASS"
            }
        }
    ' "$vi_work/entries.txt")

    if [ "$vi_layout" != PASS ]; then
        protos_install_error "unsafe or incomplete Native ZIP layout"
        return 1
    fi

    # Official Native ZIPs contain regular files only. Comparing listing
    # counts also rejects ambiguous newline-containing entry names.
    vi_entry_count=$(wc -l < "$vi_work/entries.txt")
    vi_regular_count=$(awk '
        {
            permissions = substr($0, 1, 10)
            if (length(permissions) == 10 &&
                substr(permissions, 1, 1) == "-" &&
                substr(permissions, 2, 9) ~ /^[rwxstST-]+$/) {
                regular++
            }
        }
        END {
            print regular + 0
        }
    ' "$vi_work/permissions.txt")

    if [ "$vi_entry_count" != "$vi_regular_count" ]; then
        protos_install_error \
            "Native ZIP contains non-regular, ambiguous, or unsafe entries"
        return 1
    fi

    if ! unzip -tqq "$vi_zip" >/dev/null 2>&1; then
        protos_install_error "Native ZIP integrity or CRC validation failed"
        return 1
    fi

    return 0
}

# Download only the Native member named by the validated release manifest.
protos_install_verify_download() {
    vi_work=$1
    vi_tag=$2
    vi_selection=$3

    for vi_tool in sha256sum cmp unzip wc; do
        if ! command -v "$vi_tool" >/dev/null 2>&1; then
            protos_install_error "missing required command: $vi_tool"
            return 1
        fi
    done

    vi_archive=$(printf '%s\n' "$vi_selection" |
        awk -F= '$1 == "NATIVE_ARCHIVE" {print $2}')
    vi_archive_sha=$(printf '%s\n' "$vi_selection" |
        awk -F= '$1 == "NATIVE_SHA256" {print $2}')
    vi_checksum=$(printf '%s\n' "$vi_selection" |
        awk -F= '$1 == "CHECKSUM_FILE" {print $2}')
    vi_checksum_sha=$(printf '%s\n' "$vi_selection" |
        awk -F= '$1 == "CHECKSUM_SHA256" {print $2}')
    vi_version=$(printf '%s\n' "$vi_selection" |
        awk -F= '$1 == "RELEASE_VERSION" {print $2}')
    vi_revision=$(printf '%s\n' "$vi_selection" |
        awk -F= '$1 == "SOURCE_REVISION" {print $2}')
    vi_platform=$(printf '%s\n' "$vi_selection" |
        awk -F= '$1 == "PLATFORM" {print $2}')
    vi_min_glibc=$(printf '%s\n' "$vi_selection" |
        awk -F= '$1 == "MIN_GLIBC" {print $2}')

    vi_base="https://github.com/guillermomolina/protos/releases/download/$vi_tag"

    if ! curl -fsSL --connect-timeout 10 --max-time 60 \
        -o "$vi_work/$vi_checksum" "$vi_base/$vi_checksum"; then
        protos_install_error "cannot download Native checksum"
        return 1
    fi

    vi_actual_checksum_sha=$(sha256sum "$vi_work/$vi_checksum" |
        awk '{print $1}')

    if [ "$vi_actual_checksum_sha" != "$vi_checksum_sha" ]; then
        protos_install_error "Native checksum-file SHA-256 mismatch"
        return 1
    fi

    printf '%s  %s\n' \
        "$vi_archive_sha" "$vi_archive" > "$vi_work/expected.sha256"

    if ! cmp -s "$vi_work/expected.sha256" "$vi_work/$vi_checksum"; then
        protos_install_error "Native checksum-file content mismatch"
        return 1
    fi

    if ! curl -fsSL --connect-timeout 10 --max-time 300 \
        -o "$vi_work/$vi_archive" "$vi_base/$vi_archive"; then
        protos_install_error "cannot download Native ZIP"
        return 1
    fi

    vi_actual_archive_sha=$(sha256sum "$vi_work/$vi_archive" |
        awk '{print $1}')

    if [ "$vi_actual_archive_sha" != "$vi_archive_sha" ]; then
        protos_install_error "Native ZIP SHA-256 mismatch"
        return 1
    fi

    vi_root=${vi_archive%.zip}

    if ! protos_install_archive_layout \
        "$vi_work/$vi_archive" "$vi_root" "$vi_work"; then
        return 1
    fi

    if ! unzip -p "$vi_work/$vi_archive" \
        "$vi_root/SOURCE.txt" > "$vi_work/source.txt"; then
        protos_install_error "cannot read Native SOURCE.txt"
        return 1
    fi

    if ! unzip -p "$vi_work/$vi_archive" \
        "$vi_root/RUNTIME.txt" > "$vi_work/runtime.txt"; then
        protos_install_error "cannot read Native RUNTIME.txt"
        return 1
    fi

    for vi_required_source in \
        "artifact_kind=public-prerelease" \
        "public_release=true" \
        "source_dirty=false" \
        "source_repository=https://github.com/guillermomolina/protos" \
        "source_revision=$vi_revision" \
        "release_version=$vi_version" \
        "release_tag=$vi_tag"; do
        if ! grep -Fx -- "$vi_required_source" \
            "$vi_work/source.txt" >/dev/null; then
            protos_install_error "Native SOURCE.txt identity mismatch"
            return 1
        fi
    done

    vi_target_os=$(printf '%s\n' "$vi_platform" |
        awk -F/ '{print $1}')
    vi_target_arch=$(printf '%s\n' "$vi_platform" |
        awk -F/ '{print $2}')

    for vi_required_runtime in \
        "distribution_format=protos-native-image-posix-v1" \
        "native_runtime_kind=graalvm-native-image-truffle" \
        "external_java_required=false" \
        "target_os=$vi_target_os" \
        "target_arch=$vi_target_arch" \
        "libc_family=glibc" \
        "libc_abi_min=$vi_min_glibc" \
        "linkage=dynamic" \
        "cpu_isa_assumption=compatibility"; do
        if ! grep -Fx -- "$vi_required_runtime" \
            "$vi_work/runtime.txt" >/dev/null; then
            protos_install_error "Native RUNTIME.txt identity mismatch"
            return 1
        fi
    done

    printf '%s\n' 'NATIVE_CHECKSUM_VERIFIED=PASS'
    printf '%s\n' 'NATIVE_ARCHIVE_VERIFIED=PASS'
    printf '%s\n' 'NATIVE_LAYOUT_VERIFIED=PASS'
    return 0
}

# Compare the requested release with the currently active release.
# A normal installation never silently downgrades.
protos_install_version_order() {
    awk -v active="$1" -v requested="$2" '
        BEGIN {
            sub(/^v/, "", active)
            sub(/^v/, "", requested)

            split(active, a, ".")
            split(requested, r, ".")

            result = "equal"

            for (i = 1; i <= 3; i++) {
                if (r[i] + 0 < a[i] + 0) {
                    result = "downgrade"
                    break
                }
                if (r[i] + 0 > a[i] + 0) {
                    result = "upgrade"
                    break
                }
            }

            print result
        }
    '
}

# Activate only after the complete, verified Native tree passes both smokes.
protos_install_activate_version() {
    pa_work=$1
    pa_tag=$2
    pa_selection=$3

    case "${HOME:-}" in
        /*) ;;
        *)
            protos_install_error "HOME must be an absolute directory"
            return 1
            ;;
    esac

    if [ "$HOME" = / ]; then
        protos_install_error "refusing a filesystem-root HOME"
        return 1
    fi

    if ! command -v flock >/dev/null 2>&1; then
        protos_install_error "flock is required for safe concurrent installation"
        return 1
    fi

    pa_bin_dir="$HOME/.local/bin"
    pa_wrapper="$pa_bin_dir/protos"
    pa_data="$HOME/.local/share/protos"
    pa_versions="$pa_data/versions"
    pa_marker="$pa_data/.native-installer-managed"
    pa_signature='# PROTOS_NATIVE_INSTALLER_MANAGED_V1'

    # Never replace a launcher that was not installed by this installer.
    if [ -e "$pa_wrapper" ] || [ -L "$pa_wrapper" ]; then
        if [ -L "$pa_wrapper" ] ||
           [ ! -f "$pa_wrapper" ] ||
           ! grep -Fx -- "$pa_signature" "$pa_wrapper" >/dev/null; then
            protos_install_error \
                "refusing to overwrite an existing non-managed protos command"
            return 1
        fi
    fi

    # Do not adopt an unrelated installation directory.
    if [ -e "$pa_data" ] || [ -L "$pa_data" ]; then
        if [ -L "$pa_data" ] ||
           [ ! -f "$pa_marker" ] ||
           [ -L "$pa_marker" ] ||
           ! grep -Fx -- "$pa_signature" "$pa_marker" >/dev/null; then
            protos_install_error \
                "refusing to adopt a non-managed Protos data directory"
            return 1
        fi
    else
        if ! mkdir -p "$pa_versions"; then
            protos_install_error "cannot create versioned installation directory"
            return 1
        fi
        printf '%s\n' "$pa_signature" > "$pa_marker"
    fi

    if ! mkdir -p "$pa_versions" "$pa_bin_dir"; then
        protos_install_error "cannot prepare user-local installation directories"
        return 1
    fi

    # The lock is released automatically when this installer process ends,
    # including after an unexpected termination.
    if [ -L "$pa_data/.installer.lock" ]; then
        protos_install_error "refusing a symlinked installer lock"
        return 1
    fi

    exec 9>"$pa_data/.installer.lock"

    if ! flock -n 9; then
        protos_install_error "another Protos installation is running"
        return 1
    fi

    pa_active=
    if [ -e "$pa_wrapper" ] || [ -L "$pa_wrapper" ]; then
        if [ -L "$pa_wrapper" ] ||
           ! grep -Fx -- "$pa_signature" "$pa_wrapper" >/dev/null; then
            protos_install_error "managed wrapper changed during installation"
            return 1
        fi

        pa_active=$(sed -n \
            's/^# PROTOS_VERSION=//p' "$pa_wrapper")

        case "$pa_active" in
            v[0-9]*.[0-9]*.[0-9]*) ;;
            *)
                protos_install_error "existing managed wrapper has invalid identity"
                return 1
                ;;
        esac

        pa_order=$(protos_install_version_order "$pa_active" "$pa_tag")

        if [ "$pa_order" = downgrade ]; then
            protos_install_error \
                "refusing downgrade from $pa_active to $pa_tag; use explicit rollback"
            return 1
        fi
    fi

    pa_archive=$(printf '%s\n' "$pa_selection" |
        awk -F= '$1 == "NATIVE_ARCHIVE" {print $2}')
    pa_version=$(printf '%s\n' "$pa_selection" |
        awk -F= '$1 == "RELEASE_VERSION" {print $2}')
    pa_root=${pa_archive%.zip}
    pa_version_dir="$pa_versions/$pa_tag"
    pa_created=no

    if [ -L "$pa_version_dir" ]; then
        protos_install_error "refusing a symlinked version directory"
        return 1
    fi

    if [ -e "$pa_version_dir" ]; then
        if [ ! -d "$pa_version_dir" ]; then
            protos_install_error "version path is not a directory"
            return 1
        fi
    else
        pa_stage="$pa_versions/.staging-$pa_tag-$$"

        if [ -e "$pa_stage" ]; then
            protos_install_error "staging path already exists"
            return 1
        fi

        if ! mkdir "$pa_stage"; then
            protos_install_error "cannot create installation staging directory"
            return 1
        fi

        if ! unzip -q "$pa_work/$pa_archive" -d "$pa_stage"; then
            rm -r -- "$pa_stage"
            protos_install_error "cannot extract verified Native ZIP"
            return 1
        fi

        pa_candidate="$pa_stage/$pa_root"

        if [ ! -x "$pa_candidate/bin/protos" ] ||
           [ ! -x "$pa_candidate/libexec/protos-native" ]; then
            rm -r -- "$pa_stage"
            protos_install_error "extracted Native executables are not executable"
            return 1
        fi

        pa_version_output=$("$pa_candidate/bin/protos" --version 2>&1)

        if [ "$?" -ne 0 ]; then
            rm -r -- "$pa_stage"
            protos_install_error "Native --version smoke failed"
            return 1
        fi

        case "$pa_version_output" in
            *"$pa_version"*) ;;
            *)
                rm -r -- "$pa_stage"
                protos_install_error "Native --version identity mismatch"
                return 1
                ;;
        esac

        if ! "$pa_candidate/bin/protos" \
            -e 'print("I131 install smoke")' >/dev/null; then
            rm -r -- "$pa_stage"
            protos_install_error "Native -e smoke failed"
            return 1
        fi

        if ! mv "$pa_candidate" "$pa_version_dir"; then
            rm -r -- "$pa_stage"
            protos_install_error "cannot finalize versioned Native tree"
            return 1
        fi

        rmdir "$pa_stage"
        pa_created=yes
    fi

    # Recheck an existing version as well. It is never overwritten.
    if [ ! -x "$pa_version_dir/bin/protos" ] ||
       [ ! -x "$pa_version_dir/libexec/protos-native" ]; then
        protos_install_error "installed Native version is incomplete"
        return 1
    fi

    pa_check=$("$pa_version_dir/bin/protos" --version 2>&1)

    if [ "$?" -ne 0 ]; then
        protos_install_error "installed Native version failed --version"
        return 1
    fi

    case "$pa_check" in
        *"$pa_version"*) ;;
        *)
            protos_install_error "installed Native version identity mismatch"
            return 1
            ;;
    esac

    if ! "$pa_version_dir/bin/protos" \
        -e 'print("I131 install smoke")' >/dev/null; then
        protos_install_error "installed Native version failed -e"
        return 1
    fi

    if [ "$pa_active" = "$pa_tag" ]; then
        printf 'INSTALL_STATUS=%s\n' ALREADY_ACTIVE
        printf 'ACTIVE_VERSION=%s\n' "$pa_tag"
        return 0
    fi

    # This wrapper is the only active-version pointer. Its final rename is
    # atomic, and the old wrapper remains untouched on earlier failures.
    if ! protos_install_write_wrapper "$pa_bin_dir" "$pa_tag"; then
        return 1
    fi

    if [ "$pa_created" = yes ]; then
        printf 'INSTALL_STATUS=%s\n' INSTALLED
    else
        printf 'INSTALL_STATUS=%s\n' ACTIVATED_EXISTING
    fi

    printf 'ACTIVE_VERSION=%s\n' "$pa_tag"
    printf 'PROTOS_COMMAND=%s\n' "$pa_wrapper"

    case ":${PATH:-}:" in
        *":$pa_bin_dir:"*) ;;
        *)
            printf 'PATH_SETUP=Add %s to your current shell PATH\n' \
                "$pa_bin_dir"
            ;;
    esac

    return 0
}

# A wrapper contains only a validated numeric tag and resolves the Native
# distribution by its actual, versioned directory.
protos_install_write_wrapper() {
    ww_bin=$1
    ww_tag=$2
    ww_signature='# PROTOS_NATIVE_INSTALLER_MANAGED_V1'
    ww_tmp="$ww_bin/.protos-install-$$"

    if [ -e "$ww_tmp" ] || [ -L "$ww_tmp" ]; then
        protos_install_error "temporary wrapper path already exists"
        return 1
    fi

    if ! printf '%s\n' \
        '#!/bin/sh' \
        "$ww_signature" \
        "# PROTOS_VERSION=$ww_tag" \
        "if protos_root=\$(CDPATH= cd -- \"\$HOME/.local/share/protos/versions/$ww_tag\" && pwd -P); then" \
        '    exec "$protos_root/bin/protos" "$@"' \
        'else' \
        '    printf "%s\n" "protos: installed Native version is unavailable" >&2' \
        '    false' \
        'fi' > "$ww_tmp"; then
        protos_install_error "cannot write managed Native wrapper"
        return 1
    fi

    if ! chmod 755 "$ww_tmp"; then
        rm -f -- "$ww_tmp"
        protos_install_error "cannot make wrapper executable"
        return 1
    fi

    if ! mv -f -- "$ww_tmp" "$ww_bin/protos"; then
        rm -f -- "$ww_tmp"
        protos_install_error "cannot atomically activate wrapper"
        return 1
    fi

    return 0
}

# Offline lifecycle operations never call GitHub and never download a JAR.
# Uninstall intentionally retains versioned trees for safe recovery.
protos_install_offline_manage() {
    om_mode=$1
    om_tag=$2
    om_signature='# PROTOS_NATIVE_INSTALLER_MANAGED_V1'

    case "${HOME:-}" in
        /*) ;;
        *)
            protos_install_error "HOME must be an absolute directory"
            return 1
            ;;
    esac

    if [ "$HOME" = / ]; then
        protos_install_error "refusing a filesystem-root HOME"
        return 1
    fi

    # Offline operations also fail before touching managed files if a
    # required host tool is unavailable.
    if [ "$om_mode" = rollback ]; then
        for om_required_tool in awk grep flock chmod mv; do
            if ! command -v "$om_required_tool" >/dev/null 2>&1; then
                protos_install_error "missing required command: $om_required_tool"
                return 1
            fi
        done
    else
        for om_required_tool in grep flock rm; do
            if ! command -v "$om_required_tool" >/dev/null 2>&1; then
                protos_install_error "missing required command: $om_required_tool"
                return 1
            fi
        done
    fi

    om_root="$HOME/.local/share/protos"
    om_marker="$om_root/.native-installer-managed"
    om_wrapper="$HOME/.local/bin/protos"

    if [ "$om_mode" = rollback ]; then
        om_valid_tag=$(awk -v tag="$om_tag" '
            BEGIN {
                if (tag ~ /^v[0-9]+[.][0-9]+[.][0-9]+$/) {
                    print "yes"
                }
            }
        ')

        if [ "$om_valid_tag" != yes ]; then
            protos_install_error "rollback requires a strict numeric --tag"
            return 1
        fi
    fi

    if [ ! -d "$om_root" ] ||
       [ -L "$om_root" ] ||
       [ ! -f "$om_marker" ] ||
       [ -L "$om_marker" ] ||
       ! grep -Fx -- "$om_signature" "$om_marker" >/dev/null; then
        protos_install_error "no recognized managed Native installation"
        return 1
    fi

    if [ -e "$om_wrapper" ] || [ -L "$om_wrapper" ]; then
        if [ -L "$om_wrapper" ] ||
           [ ! -f "$om_wrapper" ] ||
           ! grep -Fx -- "$om_signature" "$om_wrapper" >/dev/null; then
            protos_install_error "refusing to modify a non-managed protos command"
            return 1
        fi
    fi

    if ! command -v flock >/dev/null 2>&1; then
        protos_install_error "flock is required for safe lifecycle operations"
        return 1
    fi

    if [ -L "$om_root/.installer.lock" ]; then
        protos_install_error "refusing a symlinked installer lock"
        return 1
    fi

    exec 9>"$om_root/.installer.lock"

    if ! flock -n 9; then
        protos_install_error "another Protos lifecycle operation is running"
        return 1
    fi

    if [ -e "$om_wrapper" ] || [ -L "$om_wrapper" ]; then
        if [ -L "$om_wrapper" ] ||
           [ ! -f "$om_wrapper" ] ||
           ! grep -Fx -- "$om_signature" "$om_wrapper" >/dev/null; then
            protos_install_error "managed wrapper changed during operation"
            return 1
        fi
    fi

    if [ "$om_mode" = uninstall ]; then
        if [ -f "$om_wrapper" ]; then
            if ! rm -f -- "$om_wrapper"; then
                protos_install_error "cannot remove managed wrapper"
                return 1
            fi
            printf '%s\n' 'UNINSTALL_STATUS=DEACTIVATED'
        else
            printf '%s\n' 'UNINSTALL_STATUS=ALREADY_INACTIVE'
        fi

        printf 'VERSIONS_RETAINED=%s\n' "$om_root/versions"
        return 0
    fi

    om_version_dir="$om_root/versions/$om_tag"
    om_version=${om_tag#v}

    if [ -L "$om_version_dir" ] ||
       [ ! -d "$om_version_dir" ] ||
       [ ! -x "$om_version_dir/bin/protos" ] ||
       [ ! -x "$om_version_dir/libexec/protos-native" ]; then
        protos_install_error "requested rollback version is not installed"
        return 1
    fi

    for om_required in \
        "release_tag=$om_tag" \
        "release_version=$om_version" \
        "source_repository=https://github.com/guillermomolina/protos"; do
        if ! grep -Fx -- "$om_required" \
            "$om_version_dir/SOURCE.txt" >/dev/null 2>&1; then
            protos_install_error "rollback version has invalid source identity"
            return 1
        fi
    done

    om_version_output=$("$om_version_dir/bin/protos" --version 2>&1)

    if [ "$?" -ne 0 ]; then
        protos_install_error "rollback version failed --version"
        return 1
    fi

    case "$om_version_output" in
        *"$om_version"*) ;;
        *)
            protos_install_error "rollback --version identity mismatch"
            return 1
            ;;
    esac

    if ! "$om_version_dir/bin/protos" \
        -e 'print("I131 rollback smoke")' >/dev/null; then
        protos_install_error "rollback version failed -e smoke"
        return 1
    fi

    if ! protos_install_write_wrapper \
        "$HOME/.local/bin" "$om_tag"; then
        return 1
    fi

    printf '%s\n' 'ROLLBACK_STATUS=ACTIVATED'
    printf 'ACTIVE_VERSION=%s\n' "$om_tag"
    return 0
}

protos_install_main() {
    mode=
    pinned_tag=

    while [ "$#" -gt 0 ]; do
        case "$1" in
            --help)
                mode=help
                shift
                ;;
            --list-releases)
                mode=list
                shift
                ;;
            --inspect)
                mode=inspect
                shift
                ;;
            --verify-download)
                mode=verify
                shift
                ;;
            --rollback)
                mode=rollback
                shift
                ;;
            --uninstall)
                mode=uninstall
                shift
                ;;
            --tag)
                if [ "$#" -lt 2 ]; then
                    protos_install_error "--tag requires a version"
                    return 1
                fi
                pinned_tag=$2
                shift 2
                ;;
            *)
                protos_install_error "unknown option: $1"
                return 1
                ;;
        esac
    done

    if [ "$mode" = help ]; then
        printf '%s\n' \
            'Usage: sh install.sh --list-releases' \
            '       sh install.sh --inspect [--tag vMAJOR.MINOR.PATCH]' \
            '       sh install.sh --verify-download [--tag vMAJOR.MINOR.PATCH]' \
            '       sh install.sh [--tag vMAJOR.MINOR.PATCH]' \
            '       sh install.sh --rollback --tag vMAJOR.MINOR.PATCH' \
            '       sh install.sh --uninstall'
        return 0
    fi

    if [ -z "$mode" ]; then
        mode=install
    fi

    if [ "$mode" = list ] && [ -n "$pinned_tag" ]; then
        protos_install_error "--tag is not valid with --list-releases"
        return 1
    fi

    if [ "$mode" = rollback ]; then
        if [ -z "$pinned_tag" ]; then
            protos_install_error "--rollback requires --tag vMAJOR.MINOR.PATCH"
            return 1
        fi
        protos_install_offline_manage rollback "$pinned_tag"
        return $?
    fi

    if [ "$mode" = uninstall ]; then
        if [ -n "$pinned_tag" ]; then
            protos_install_error "--uninstall does not accept --tag"
            return 1
        fi
        protos_install_offline_manage uninstall ""
        return $?
    fi

    # Verify mode-specific host tools before contacting GitHub.
    # No dependency is installed or upgraded automatically.
    for required_tool in curl awk mktemp grep rm; do
        if ! command -v "$required_tool" >/dev/null 2>&1; then
            protos_install_error "missing required command: $required_tool"
            return 1
        fi
    done

    if [ "$mode" = list ]; then
        if ! command -v cat >/dev/null 2>&1; then
            protos_install_error "missing required command: cat"
            return 1
        fi
    else
        for required_tool in uname getconf; do
            if ! command -v "$required_tool" >/dev/null 2>&1; then
                protos_install_error "missing required command: $required_tool"
                return 1
            fi
        done
    fi

    if [ "$mode" = verify ] || [ "$mode" = install ]; then
        for required_tool in sha256sum cmp unzip wc; do
            if ! command -v "$required_tool" >/dev/null 2>&1; then
                protos_install_error "missing required command: $required_tool"
                return 1
            fi
        done
    fi

    if [ "$mode" = install ]; then
        for required_tool in flock mv chmod mkdir rmdir sed; do
            if ! command -v "$required_tool" >/dev/null 2>&1; then
                protos_install_error "missing required command: $required_tool"
                return 1
            fi
        done
    fi

    if [ -n "$pinned_tag" ]; then
        case "$pinned_tag" in
            v[0-9]*.[0-9]*.[0-9]*) ;;
            *)
                protos_install_error "invalid release tag: $pinned_tag"
                return 1
                ;;
        esac
    fi

    workdir=$(mktemp -d "${TMPDIR:-/tmp}/protos-install.XXXXXXXX")

    if [ -z "$workdir" ] || [ ! -d "$workdir" ]; then
        protos_install_error "cannot create a temporary directory"
        return 1
    fi

    releases_url='https://api.github.com/repos/guillermomolina/protos/releases?per_page=100'

    if ! curl -fsSL --connect-timeout 10 --max-time 60 \
        -H 'Accept: application/vnd.github+json' \
        -o "$workdir/releases.json" "$releases_url"; then
        rm -r -- "$workdir"
        protos_install_error "cannot download the published release list"
        return 1
    fi

    if ! protos_install_release_tags \
        < "$workdir/releases.json" > "$workdir/tags"; then
        rm -r -- "$workdir"
        protos_install_error "cannot parse GitHub releases"
        return 1
    fi

    if [ "$mode" = list ]; then
        if [ ! -s "$workdir/tags" ]; then
            rm -r -- "$workdir"
            protos_install_error "no published release tags found"
            return 1
        fi

        cat "$workdir/tags"
        rm -r -- "$workdir"
        return 0
    fi

    if [ -n "$pinned_tag" ]; then
        if ! grep -Fx -- "$pinned_tag" "$workdir/tags" >/dev/null; then
            rm -r -- "$workdir"
            protos_install_error \
                "requested tag is not a published official release"
            return 1
        fi

        printf '%s\n' "$pinned_tag" > "$workdir/ordered-tags"
    else
        if ! protos_install_tags_descending \
            < "$workdir/tags" > "$workdir/ordered-tags"; then
            rm -r -- "$workdir"
            protos_install_error "cannot order published release tags"
            return 1
        fi
    fi

    if [ ! -s "$workdir/ordered-tags" ]; then
        rm -r -- "$workdir"
        protos_install_error "no published numeric release tags found"
        return 1
    fi

    if ! command -v uname >/dev/null 2>&1 ||
       ! command -v getconf >/dev/null 2>&1; then
        rm -r -- "$workdir"
        protos_install_error "uname and getconf are required"
        return 1
    fi

    host_system=$(uname -s)
    host_arch=$(uname -m)

    if [ "$host_system" != Linux ]; then
        rm -r -- "$workdir"
        protos_install_error "this installer currently supports Linux only"
        return 1
    fi

    reported_glibc=$(getconf GNU_LIBC_VERSION 2>/dev/null)

    case "$reported_glibc" in
        "glibc "*) host_glibc=${reported_glibc#glibc } ;;
        *)
            rm -r -- "$workdir"
            protos_install_error \
                "cannot identify glibc; musl and unknown libc are unsupported"
            return 1
            ;;
    esac

    selected_tag=
    selected=

    # A published release is a candidate, not automatically a compatible
    # Native installation. Try candidates in descending numeric order.
    # An explicit --tag is a strict pin and never falls back.
    while IFS= read -r candidate_tag; do
        manifest_url="https://github.com/guillermomolina/protos/releases/download/${candidate_tag}/RELEASE_MANIFEST.txt"

        if ! curl -fsSL --connect-timeout 10 --max-time 60 \
            -o "$workdir/RELEASE_MANIFEST.txt" "$manifest_url"; then
            rm -r -- "$workdir"
            protos_install_error \
                "cannot fetch official release manifest for $candidate_tag"
            return 1
        fi

        candidate_selection=$(protos_install_manifest_native \
            "$workdir/RELEASE_MANIFEST.txt" \
            "$candidate_tag" linux "$host_arch")

        if [ -z "$candidate_selection" ]; then
            if [ -n "$pinned_tag" ]; then
                rm -r -- "$workdir"
                protos_install_error \
                    "pinned release has no valid compatible Native artifact"
                return 1
            fi

            printf 'protos-install: skipping %s: no compatible Native identity\n' \
                "$candidate_tag" >&2
            continue
        fi

        candidate_minimum_glibc=$(printf '%s\n' "$candidate_selection" |
            awk -F= '$1 == "MIN_GLIBC" { print $2 }')

        candidate_compatible=$(protos_install_glibc_compatible \
            "$host_glibc" "$candidate_minimum_glibc")

        if [ "$candidate_compatible" != yes ]; then
            if [ -n "$pinned_tag" ]; then
                rm -r -- "$workdir"
                protos_install_error \
                    "host glibc $host_glibc is below required $candidate_minimum_glibc"
                return 1
            fi

            printf 'protos-install: skipping %s: requires glibc %s; host has %s\n' \
                "$candidate_tag" "$candidate_minimum_glibc" \
                "$host_glibc" >&2
            continue
        fi

        selected_tag=$candidate_tag
        selected=$candidate_selection
        break
    done < "$workdir/ordered-tags"

    if [ -z "$selected" ]; then
        rm -r -- "$workdir"
        protos_install_error \
            "no published Native release supports $host_arch with glibc $host_glibc"
        return 1
    fi

    if [ "$mode" = verify ] || [ "$mode" = install ]; then
        if ! protos_install_verify_download \
            "$workdir" "$selected_tag" "$selected"; then
            rm -r -- "$workdir"
            return 1
        fi
    fi

    if [ "$mode" = install ]; then
        if ! protos_install_activate_version \
            "$workdir" "$selected_tag" "$selected"; then
            rm -r -- "$workdir"
            return 1
        fi
    fi

    printf '%s\n' "$selected"
    printf 'HOST_GLIBC=%s\n' "$host_glibc"
    printf '%s\n' 'INSPECTION=PASS'

    rm -r -- "$workdir"
    return 0
}

protos_install_main "$@"
