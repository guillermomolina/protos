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

    mapfile -t generated < <(
        find "${classes_dir}/com/guillermomolina/protos"             -type f             -name '*Gen$*LibraryExports*.class'             -print         | sort
    )

    if (( ${#generated[@]} == 0 )); then
        echo "no generated Truffle LibraryExports classes found" >&2
        return 1
    fi

    classes=("${provider}")

    for path in "${generated[@]}"; do
        class="${path#${classes_dir}/}"
        class="${class%.class}"
        class="${class//\//.}"
        classes+=("${class}")
    done

    external_receiver_exports=(
        'com.guillermomolina.protos.execution.ProtosBytecodeTagTreeNodeExports'
        'com.guillermomolina.protos.execution.ProtosBytecodeTagTreeNodeExportsGen'
    )

    for class in "${external_receiver_exports[@]}"; do
        path="${classes_dir}/${class//./\/}.class"
        if [[ ! -f "${path}" ]]; then
            echo "missing external-receiver Truffle export initialization class: ${path}" >&2
            return 1
        fi
        classes+=("${class}")
    done

    inline_support_generated=(
        'com.guillermomolina.protos.execution.ProtosBytecodeRootNodeGen$MaterializeCurrentClosure_Node'
        'com.guillermomolina.protos.execution.ProtosBytecodeRootNodeGen$IsStandardNumericSelection_Node'
        'com.guillermomolina.protos.execution.ProtosFunctionCallDispatchersFactory$FunctionCachedCallNodeGen$Inlined'
        'com.guillermomolina.protos.execution.ProtosFunctionCallDispatchersFactory$BuiltinFunctionCachedCallNodeGen$Inlined'
        'com.guillermomolina.protos.execution.ProtosFunctionCallDispatchersFactory$BuiltinMethodCachedCallNodeGen$Inlined'
        'com.guillermomolina.protos.execution.ProtosFunctionCallDispatchersFactory$FunctionDirectInvokeNodeGen$Inlined'
        'com.guillermomolina.protos.execution.ProtosFunctionCallDispatchersFactory$FunctionIndirectInvokeNodeGen$Inlined'
        'com.guillermomolina.protos.execution.ProtosLexicalSelectionNodeGen'
        'com.guillermomolina.protos.execution.ProtosLexicalSelectionNodeGen$Uncached'
    )

    # I115-S82: generated dispatcher nodes with hosted Truffle field descriptors.
    # Initialize the generated metadata at image build time, not during guest calls.
    dispatcher_generated_dir="${classes_dir}/com/guillermomolina/protos/execution"
    dispatcher_call_node="${dispatcher_generated_dir}/ProtosFunctionCallDispatchersFactory$CallNodeGen.class"

    if [[ ! -f "${dispatcher_call_node}" ]]; then
        echo "missing generated dispatcher CallNodeGen: ${dispatcher_call_node}" >&2
        return 1
    fi

    mapfile -t dispatcher_generated < <(
        find "${dispatcher_generated_dir}" \
            -maxdepth 1 \
            -type f \
            \( -name 'ProtosFunctionCallDispatchersFactory$*NodeGen.class' \
               -o -name 'ProtosFunctionCallDispatchersFactory$*NodeGen$Uncached.class' \
               -o -name 'ProtosFunctionCallDispatchersFactory$*NodeGen$Inlined.class' \) \
            -printf '%f\n' \
            | sort
    )

    for file in "${dispatcher_generated[@]}"; do
        class="com.guillermomolina.protos.execution.${file%.class}"
        inline_support_generated+=("${class}")
    done

    for class in "${inline_support_generated[@]}"; do
        path="${classes_dir}/${class//./\/}.class"
        if [[ ! -f "${path}" ]]; then
            echo "missing generated InlineSupport class: ${path}" >&2
            return 1
        fi
        classes+=("${class}")
    done

    bytecode_structural_generated=(
        'com.guillermomolina.protos.execution.ProtosBytecodeRootNodeGen'
        'com.guillermomolina.protos.execution.ProtosBytecodeRootNodeGen$1'
        'com.guillermomolina.protos.execution.ProtosBytecodeRootNodeGen$Bytecode'
        'com.guillermomolina.protos.execution.ProtosBytecodeRootNodeGen$AbstractBytecodeNode'
    )

    append_initial_bytecode_policy() {
        root_class="$1"
        uninitialized="${root_class}\$UninitializedBytecodeNode"
        uncached="${root_class}\$UncachedBytecodeNode"
        uncached_tail="${root_class}\$UncachedBytecodeNodeTailCall"

        uninitialized_path="${classes_dir}/${uninitialized//./\/}.class"
        uncached_path="${classes_dir}/${uncached//./\/}.class"
        uncached_tail_path="${classes_dir}/${uncached_tail//./\/}.class"

        if [[ -f "${uninitialized_path}" && -f "${uncached_path}" ]]; then
            echo "ambiguous generated Bytecode initial tier for ${root_class}" >&2
            return 1
        fi

        if [[ -f "${uncached_path}" ]]; then
            bytecode_structural_generated+=("${uncached}")

            if [[ -f "${uncached_tail_path}" ]]; then
                bytecode_structural_generated+=("${uncached_tail}")
            fi

            for suffix in TagNode VirtualState; do
                class="${root_class}\$${suffix}"
                path="${classes_dir}/${class//./\/}.class"
                if [[ -f "${path}" ]]; then
                    bytecode_structural_generated+=("${class}")
                fi
            done
            return 0
        fi

        if [[ -f "${uninitialized_path}" ]]; then
            bytecode_structural_generated+=("${uninitialized}")
            return 0
        fi

        echo "missing generated Bytecode initial tier for ${root_class}" >&2
        return 1
    }

    append_initial_bytecode_policy \
        'com.guillermomolina.protos.execution.ProtosBytecodeRootNodeGen' \
        || return 1

    for class in "${bytecode_structural_generated[@]}"; do
        path="${classes_dir}/${class//./\/}.class"
        if [[ ! -f "${path}" ]]; then
            echo "missing generated Bytecode structural class: ${path}" >&2
            return 1
        fi
        classes+=("${class}")
    done

    execution_dir="${classes_dir}/com/guillermomolina/protos/execution"

    mapfile -t bytecode_operation_generated < <(
        find "${execution_dir}" \
            -maxdepth 1 \
            -type f \
            -name 'ProtosBytecodeRootNodeGen$*_Node.class' \
            -printf '%f\n' \
            | sort
    )

    for file in "${bytecode_operation_generated[@]}"; do
        class="com.guillermomolina.protos.execution.${file%.class}"
        classes+=("${class}")
    done

    safe_hosted_constants=(
        'com.guillermomolina.protos.execution.ProtosTextReaderCPrimeExecution$Advance'
        'com.guillermomolina.protos.runtime.ProtosCoreErrors$StandardError'
        'com.guillermomolina.protos.execution.ProtosBytecodeRootNode$LexicalFrameLayout'
        'com.guillermomolina.protos.execution.ProtosFunctionCallDispatchers$ReturnHomeMode'
    )

    for class in "${safe_hosted_constants[@]}"; do
        path="${classes_dir}/${class//./\/}.class"
        if [[ ! -f "${path}" ]]; then
            echo "missing safe hosted-constant class: ${path}" >&2
            return 1
        fi
        classes+=("${class}")
    done

    declare -A seen_classes=()
    unique_classes=()

    for class in "${classes[@]}"; do
        if [[ -z "${seen_classes[${class}]+x}" ]]; then
            seen_classes["${class}"]=1
            unique_classes+=("${class}")
        fi
    done

    classes=("${unique_classes[@]}")

    mkdir -p "$(dirname "${output}")"

    joined="$(IFS=,; printf '%s' "${classes[*]}")"
    {
        printf '%s\n' "--initialize-at-build-time=${joined}"
        printf '%s\n' "-H:+UnlockExperimentalVMOptions"
        printf '%s\n' "-H:Preserve=package=org.eclipse.lsp4j.*"
        printf '%s\n' "-H:-UnlockExperimentalVMOptions"
        printf '%s\n' "--enable-native-access=org.graalvm.truffle"
    } > "${output}"

    printf 'native-image build-time generated classes: %d\n' "${#classes[@]}"
}

main "$@"
