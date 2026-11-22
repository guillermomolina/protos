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

#!/usr/bin/env bash

main() {
    classes_dir="${1:?classes directory required}"
    output="${2:?output file required}"

    provider="com.guillermomolina.protos.execution.ProtosLanguageProvider"

    provider_path="${classes_dir}/${provider//./\/}.class"
    if [[ ! -f "${provider_path}" ]]; then
        echo "missing generated language provider: ${provider_path}" >&2
        return 1
    fi

    # Implementation packages initialized at image build time. Each entry is a
    # package prefix: the interpreter, generated nodes, dispatchers and runtime
    # objects. CLI, tooling, LSP and third-party packages are not listed.
    build_time_packages=(
        'com.guillermomolina.protos.execution'
        'com.guillermomolina.protos.runtime'
    )

    for package in "${build_time_packages[@]}"; do
        package_dir="${classes_dir}/${package//./\/}"
        if [[ ! -d "${package_dir}" ]]; then
            echo "missing implementation package: ${package_dir}" >&2
            return 1
        fi
    done

    # Keep host native linkage and Unicode case tables out of the image heap.
    # Process-provider instances and library loading belong to execution time.
    run_time_classes=(
        'com.guillermomolina.protos.execution.ProtosPosixProcessHandler'
        'com.guillermomolina.protos.execution.ProtosLinuxConfinedFilesystemNative$Bridge'
        'com.guillermomolina.protos.execution.ProtosRegexUnicodeFacility$CaseOrbits'
    )

    for class in "${run_time_classes[@]}"; do
        path="${classes_dir}/${class//./\/}.class"
        if [[ ! -f "${path}" ]]; then
            echo "missing run-time initialized class: ${path}" >&2
            return 1
        fi
    done

    mkdir -p "$(dirname "${output}")"

    joined="$(IFS=,; printf '%s' "${build_time_packages[*]}")"
    {
        printf '%s\n' "--initialize-at-build-time=${joined}"
        printf '%s\n' "--initialize-at-run-time=$(IFS=,; printf '%s' "${run_time_classes[*]}")"
        printf '%s\n' "-H:MaxRuntimeCompileMethods=20000"
        printf '%s\n' "-H:+UnlockExperimentalVMOptions"
        printf '%s\n' "-H:Preserve=package=org.eclipse.lsp4j.*"
        printf '%s\n' "-H:-UnlockExperimentalVMOptions"
        printf '%s\n' "--enable-native-access=org.graalvm.truffle"
    } > "${output}"

    printf 'native-image build-time packages: %d\n' "${#build_time_packages[@]}"
}

main "$@"
