#!/usr/bin/env bash

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
native_bin="${root}/target/native/protos"

if [[ ! -x "${native_bin}" ]]; then
    echo "NATIVE_BINARY=MISSING" >&2
    exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

version_log="${tmp_dir}/version.log"
help_log="${tmp_dir}/help.log"
smoke_log="${tmp_dir}/smoke.log"
jit_log="${tmp_dir}/forced-jit.log"

PROTOS_HOME="${root}" \
    "${native_bin}" \
    --version \
    >"${version_log}" 2>&1
version_status=$?

PROTOS_HOME="${root}" \
    "${native_bin}" \
    --help \
    >"${help_log}" 2>&1
help_status=$?

PROTOS_HOME="${root}" \
    "${native_bin}" \
    -e '1' \
    >"${smoke_log}" 2>&1
smoke_status=$?

PROTOS_HOME="${root}" \
    "${native_bin}" \
    -Dpolyglot.engine.AllowExperimentalOptions=true \
    -Dpolyglot.engine.BackgroundCompilation=false \
    -Dpolyglot.engine.CompileImmediately=true \
    -Dpolyglot.engine.TraceCompilation=true \
    -Dpolyglot.engine.CompilationFailureAction=Print \
    -e '1' \
    >"${jit_log}" 2>&1
jit_status=$?

opt_failed="$(
    grep -Eic \
        '\bopt failed\b' \
        "${jit_log}" \
        || true
)"

frame_failures="$(
    grep -Eic \
        'FrameWithoutBoxing.*should not be materialized|should not be materialized.*FrameWithoutBoxing' \
        "${jit_log}" \
        || true
)"

compilation_failures="$(
    grep -Eic \
        'Compilation failed|Internal error' \
        "${jit_log}" \
        || true
)"

helper_tier2="$(
    grep -Ei \
        'opt done.*ProtosBytecodeRootNodeGen.*Tier 2|ProtosBytecodeRootNodeGen.*Tier 2.*opt done' \
        "${jit_log}" \
        | wc -l
)"

semantic_tier2="$(
    grep -Ei \
        'opt done.*ProtosSemanticBytecodeRootNodeGen.*Tier 2|ProtosSemanticBytecodeRootNodeGen.*Tier 2.*opt done' \
        "${jit_log}" \
        | wc -l
)"

opt_done="$(
    grep -Eic \
        '\bopt done\b' \
        "${jit_log}" \
        || true
)"

echo "NATIVE_VERSION_STATUS=${version_status}"
echo "NATIVE_HELP_STATUS=${help_status}"
echo "NATIVE_GUEST_SMOKE_STATUS=${smoke_status}"
echo "NATIVE_FORCED_JIT_STATUS=${jit_status}"
echo "OPT_DONE=${opt_done}"
echo "OPT_FAILED=${opt_failed}"
echo "FRAME_WITHOUT_BOXING_FAILURES=${frame_failures}"
echo "COMPILATION_FAILURES=${compilation_failures}"
echo "HELPER_BYTECODE_ROOT_TIER2=${helper_tier2}"
echo "SEMANTIC_BYTECODE_ROOT_TIER2=${semantic_tier2}"

if [[ "${version_status}" -ne 0 ]]; then
    cat "${version_log}" >&2
    echo "NATIVE_VERSION_SMOKE=FAIL" >&2
    exit 1
fi

if [[ "${help_status}" -ne 0 ]]; then
    cat "${help_log}" >&2
    echo "NATIVE_HELP_SMOKE=FAIL" >&2
    exit 1
fi

if [[ "${smoke_status}" -ne 0 ]]; then
    cat "${smoke_log}" >&2
    echo "NATIVE_GUEST_SMOKE=FAIL" >&2
    exit 1
fi

if [[ "${jit_status}" -ne 0 ]]; then
    cat "${jit_log}" >&2
    echo "NATIVE_FORCED_GUEST_JIT=FAIL" >&2
    exit 1
fi

if [[ "${opt_failed}" -ne 0 ]]; then
    cat "${jit_log}" >&2
    echo "NATIVE_FORCED_GUEST_JIT=FAIL_OPT_FAILED" >&2
    exit 1
fi

if [[ "${frame_failures}" -ne 0 ]]; then
    cat "${jit_log}" >&2
    echo "NATIVE_FORCED_GUEST_JIT=FAIL_FRAME_WITHOUT_BOXING" >&2
    exit 1
fi

if [[ "${compilation_failures}" -ne 0 ]]; then
    cat "${jit_log}" >&2
    echo "NATIVE_FORCED_GUEST_JIT=FAIL_COMPILATION" >&2
    exit 1
fi

if [[ "${helper_tier2}" -lt 1 ]]; then
    cat "${jit_log}" >&2
    echo "HELPER_BYTECODE_ROOT_TIER2=FAIL" >&2
    exit 1
fi

if [[ "${semantic_tier2}" -lt 1 ]]; then
    cat "${jit_log}" >&2
    echo "SEMANTIC_BYTECODE_ROOT_TIER2=FAIL" >&2
    exit 1
fi

echo "NATIVE_VERSION_SMOKE=PASS"
echo "NATIVE_HELP_SMOKE=PASS"
echo "NATIVE_GUEST_SMOKE=PASS"
echo "NATIVE_FORCED_GUEST_JIT=PASS"
echo "HELPER_BYTECODE_ROOT_TIER2=PASS"
echo "SEMANTIC_BYTECODE_ROOT_TIER2=PASS"
echo "FRAME_WITHOUT_BOXING_REGRESSION=PASS"
echo "NATIVE_REGRESSION_SUITE=PASS"
