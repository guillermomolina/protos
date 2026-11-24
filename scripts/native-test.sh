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

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
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
test_tool_log="${tmp_dir}/test-tool.log"
probe_log="${tmp_dir}/guest-jit.log"
probe_source="${tmp_dir}/guest-jit.protos"
dap_log="${tmp_dir}/dap.log"
lsp_log="${tmp_dir}/lsp.log"
regex_log="${tmp_dir}/regex.log"
inspect_source="${tmp_dir}/inspect.protos"

# Native guest JIT: the executable must compile guest code with Truffle Tier 2.
# History: PLAT045 (#772) shipped an interpreter-only fallback runtime while
# oracle/graal#14579 prevented Bytecode DSL Tier-2 compilation in Native Image;
# that policy is superseded by observed Native Tier-2 success.
#
# The probe exercises f() (a guest Bytecode DSL root) and g() (a guest root
# whose loop runs through the whileTrue builtin) with immediate, synchronous
# compilation. After I115 the two root families involved are
# ProtosBytecodeRootNode (generated ProtosBytecodeRootNodeGen) and
# ProtosBuiltinCallRootNode; each family must reach a successful Tier-2
# compilation. Several instances of one family never count as coverage of the
# other. Any opt failure, compilation failure, FrameWithoutBoxing failure or
# fallback-runtime marker fails the check even when the program still runs.
{
    echo 'x: 1'
    echo 'f: () => { x }'
    echo 'g: () => { (() => false).whileTrue(() => { null }) }'
    for ((probe_iteration = 0; probe_iteration < 500; probe_iteration++)); do
        echo 'f()'
        echo 'g()'
    done
    echo 'print(f())'
} >"${probe_source}"

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
    test --file "${root}/protos/tests/library/uri/parse.protos" \
    >"${test_tool_log}" 2>&1
test_tool_status=$?

# I091 / PLAT056: exercise the actual Native Image interpreter against the
# public numeric semantics, not only the trivial -e '1' smoke test.
# These are focused existing conformance files, not the full Protos suite.
native_numeric_status=0
native_numeric_cases=0

for numeric_file in \
    "protos/tests/conformance/integer/prototype-and-receiver.protos" \
    "protos/tests/conformance/integer/primitive-call-boundaries.protos" \
    "protos/tests/conformance/number/fraction-relations.protos" \
    "protos/tests/conformance/float/special-values.protos"
do
    case_log="${tmp_dir}/numeric-${native_numeric_cases}.log"

    PROTOS_HOME="${root}" \
        "${native_bin}" \
        test --file "${root}/${numeric_file}" \
        >"${case_log}" 2>&1
    case_status=$?

    if [[ "${case_status}" -ne 0 ]] || \
       ! grep -Eq '^[[:space:]]*[1-9][0-9]* passed, 0 failed[[:space:]]*$' "${case_log}"; then
        cat "${case_log}" >&2
        echo "NATIVE_NUMERIC_CASE_FAILURE=${numeric_file}" >&2
        native_numeric_status=1
    fi

    native_numeric_cases=$((native_numeric_cases + 1))
done

# I130-C2: run the existing std:tls/client suites on the actual Native
# executable. They use fixtures and simulated duplexes: they exercise
# admission, DER certificates, JSSE/X.509 primitive availability, ClientHello
# generation, identity, and controlled error paths, but do not prove a
# complete handshake with an authenticated server or cacerts provenance.
native_tls_status=0
native_tls_cases=0

for tls_file in \
    "protos/tests/library/tls/client-admission.protos" \
    "protos/tests/library/tls/client-handshake.protos" \
    "protos/tests/library/tls/identity.protos"
do
    case_log="${tmp_dir}/tls-${native_tls_cases}.log"

    PROTOS_HOME="${root}" \
        "${native_bin}" \
        test --file "${root}/${tls_file}" \
        >"${case_log}" 2>&1
    case_status=$?

    if [[ "${case_status}" -ne 0 ]] || \
       ! grep -Eq '^[[:space:]]*[1-9][0-9]* passed, 0 failed[[:space:]]*$' "${case_log}"; then
        cat "${case_log}" >&2
        echo "NATIVE_TLS_CASE_FAILURE=${tls_file}" >&2
        native_tls_status=1
    fi

    native_tls_cases=$((native_tls_cases + 1))
done

# I159 / D244-C: execute the real public HMAC-SHA256 module in the
# rebuilt Native Image, including RFC 4231 known-answer vectors, verification,
# input ownership, domain errors and private-module boundary.
native_hmac_status=0
native_hmac_cases=0

for hmac_file in \
    "protos/tests/library/crypto/hmac_sha256/vectors-and-boundaries.protos" \
    "protos/tests/library/crypto/hmac_sha256/verification-and-ownership.protos" \
    "protos/tests/library/crypto/hmac_sha256/invalid-inputs-and-private-boundary.protos"
do
    case_log="${tmp_dir}/hmac-${native_hmac_cases}.log"

    PROTOS_HOME="${root}" \
        "${native_bin}" \
        test --file "${root}/${hmac_file}" \
        >"${case_log}" 2>&1
    case_status=$?

    if [[ "${case_status}" -ne 0 ]] || \
       ! grep -Eq '^[[:space:]]*[1-9][0-9]* passed, 0 failed[[:space:]]*$' "${case_log}"; then
        cat "${case_log}" >&2
        echo "NATIVE_HMAC_CASE_FAILURE=${hmac_file}" >&2
        native_hmac_status=1
    fi

    native_hmac_cases=$((native_hmac_cases + 1))
done

test_tool_output_ok=1
for expected_line in \
    "[library/uri] 0/4" \
    "[library/uri] 1/4" \
    "[library/uri] 2/4" \
    "[library/uri] 3/4" \
    "[library/uri] 4/4 passed" \
    "4 passed, 0 failed"
do
    if ! grep -Fqx -- "${expected_line}" "${test_tool_log}"; then
        test_tool_output_ok=0
    fi
done

test_tool_context_teardown_failures="$(
    grep -Fic \
        'Polyglot runtime host cannot close while Process Contexts are active' \
        "${test_tool_log}" \
        || true
)"

# I115-S97: the fallback runtime must preserve the exact caller when a
# compact Closure materializes its activation. A READ_ONLY stack walk that
# compares cloned B5 arguments by identity fails these three cases.
native_caller_recovery_failures=0

run_native_caller_recovery_case() {
    local case_name="$1"
    local expression="$2"
    local expected="$3"
    local case_out="${tmp_dir}/caller-${case_name}.out"
    local case_err="${tmp_dir}/caller-${case_name}.err"
    local case_expected="${tmp_dir}/caller-${case_name}.expected"

    printf '%s\n' "${expected}" >"${case_expected}"

    PROTOS_HOME="${root}" \
        timeout -k 3s 20s "${native_bin}" -e "${expression}" \
        >"${case_out}" 2>"${case_err}"
    local case_status=$?

    echo "NATIVE_I115_CALLER_${case_name}_STATUS=${case_status}"

    if [[ "${case_status}" -ne 0 ]] || \
       ! cmp -s "${case_expected}" "${case_out}" || \
       grep -Eq 'I115: caller frame unavailable|I115_S93_TASK_THROWABLE|Internal error:|Runtime error:' "${case_err}"; then
        cat "${case_out}" "${case_err}" >&2
        echo "NATIVE_I115_CALLER_CASE_FAILURE=${case_name}" >&2
        native_caller_recovery_failures=$((native_caller_recovery_failures + 1))
    fi
}

run_native_caller_recovery_case \
    NESTED_CALL \
    'f: () => { print("INNER"); 7 }; print(f())' \
    $'INNER\n7'

run_native_caller_recovery_case \
    OBJECT_IN_CLOSURE \
    'f: () => { value: {}; 7 }; print(f())' \
    '7'

run_native_caller_recovery_case \
    CLI_MODULE \
    'M: import("std:cli/command_line"); print(M)' \
    '<object>'

PROTOS_HOME="${root}" \
    python3 "${root}/scripts/native-test-dap-stacktrace.py" "${native_bin}" \
    >"${dap_log}" 2>&1
dap_status=$?

PROTOS_HOME="${root}" \
    python3 "${root}/scripts/native-test-lsp-workspace-symbols.py" "${native_bin}" \
    >"${lsp_log}" 2>&1
lsp_status=$?

PROTOS_HOME="${root}" \
    "${native_bin}" \
    test --file "${root}/protos/tests/library/regex/compile.protos" \
    >"${regex_log}" 2>&1
regex_status=$?

PROTOS_HOME="${root}" \
    "${native_bin}" \
    -Dpolyglot.engine.AllowExperimentalOptions=true \
    -Dpolyglot.engine.TraceCompilation=true \
    -Dpolyglot.engine.CompileImmediately=true \
    -Dpolyglot.engine.BackgroundCompilation=false \
    -Dpolyglot.engine.CompilationFailureAction=Print \
    "${probe_source}" \
    >"${probe_log}" 2>&1
probe_status=$?

# CLI010: --inspect explicitly authorizes std:inspect for file and -e
# execution; without it the import is an ordinary Error. Only the Integer
# representation is checked: Native bytecode and truffleTree dumps need not
# match the JVM ones.
{
    echo 'Inspect: import("std:inspect")'
    echo 'print(Inspect.representation(1))'
} >"${inspect_source}"

inspect_failures=0

run_inspect_case() {
    local name="$1"
    local expected_status="$2"
    shift 2
    local case_out="${tmp_dir}/inspect-${name}.out"
    local case_err="${tmp_dir}/inspect-${name}.err"
    local case_ok=1

    PROTOS_HOME="${root}" \
        "${native_bin}" \
        "$@" \
        >"${case_out}" 2>"${case_err}"
    local case_status=$?

    if [[ "${case_status}" -ne "${expected_status}" ]]; then
        case_ok=0
    elif [[ "${expected_status}" -eq 0 ]]; then
        if ! grep -Fq 'Integer' "${case_out}"; then
            case_ok=0
        fi
    elif ! grep -Fq 'Error' "${case_err}" || \
         grep -Fq 'Internal error' "${case_err}" || \
         grep -Fq 'Integer' "${case_out}"; then
        case_ok=0
    fi

    echo "NATIVE_INSPECT_${name}_STATUS=${case_status}"
    if [[ "${case_ok}" -ne 1 ]]; then
        cat "${case_out}" "${case_err}" >&2
        echo "NATIVE_INSPECT_CASE_FAILURE=${name}" >&2
        inspect_failures=$((inspect_failures + 1))
    fi
}

inspect_eval_source="$(cat "${inspect_source}")"

run_inspect_case FILE_DENIED 1 "${inspect_source}"
run_inspect_case FILE_AUTHORIZED 0 --inspect "${inspect_source}"
run_inspect_case EVAL_DENIED 1 -e "${inspect_eval_source}"
run_inspect_case EVAL_AUTHORIZED 0 --inspect -e "${inspect_eval_source}"

# Independent Resolver CLI authority. No case performs a host DNS query.
resolver_failures=0
resolver_cases=0
resolver_source="${tmp_dir}/resolver.protos"
resolver_probe='print(context.hasSlot("resolver"))
print(context.hasSlot("network"))
if(context.hasSlot("resolver")) { print(resolver != null); resolver.close().value() }'
printf '%s\n' "${resolver_probe}" >"${resolver_source}"

run_resolver_case() {
    local name="$1"
    local expected_status="$2"
    local expected_output="$3"
    shift 3
    local case_out="${tmp_dir}/resolver-${name}.out"
    local case_err="${tmp_dir}/resolver-${name}.err"
    PROTOS_HOME="${root}" "${native_bin}" "$@" >"${case_out}" 2>"${case_err}"
    local case_status=$?
    resolver_cases=$((resolver_cases + 1))
    echo "NATIVE_RESOLVER_${name}_STATUS=${case_status}"
    if [[ "${case_status}" -ne "${expected_status}" ]] ||
       [[ "$(cat "${case_out}")" != "${expected_output}" ]] ||
       grep -Fq 'Internal error' "${case_err}"; then
        cat "${case_out}" "${case_err}" >&2
        echo "NATIVE_RESOLVER_CASE_FAILURE=${name}" >&2
        resolver_failures=$((resolver_failures + 1))
    fi
}

run_resolver_case FILE_ABSENT 0 $'false\nfalse' "${resolver_source}"
run_resolver_case FILE_ONLY 0 $'true\nfalse\ntrue' --allow-resolver "${resolver_source}"
run_resolver_case EVAL_ABSENT 0 $'false\nfalse' -e "${resolver_probe}"
run_resolver_case EVAL_ONLY 0 $'true\nfalse\ntrue' --allow-resolver -e "${resolver_probe}"
run_resolver_case NETWORK_ONLY 0 $'false\ntrue' --allow-network -e "${resolver_probe}"
run_resolver_case BOTH 0 $'true\ntrue\ntrue' --allow-network --allow-resolver -e "${resolver_probe}"
run_resolver_case DUPLICATE 2 '' --allow-resolver --allow-resolver -e null
run_resolver_case RUN_DUPLICATE 2 '' --allow-resolver run --allow-resolver Main
run_resolver_case DEBUG_DUPLICATE 2 '' --allow-resolver debug --allow-resolver entry.protos
for resolver_bad in --allow-resolver= --allow-resolver=true --allow-resolver=false; do
    run_resolver_case "MALFORMED-${resolver_bad}" 2 '' "${resolver_bad}" -e null
done
for resolver_tool in test lint package format language-server; do
    run_resolver_case "TOOL-PREFIX-${resolver_tool}" 2 '' --allow-resolver "${resolver_tool}"
    run_resolver_case "TOOL-SUFFIX-${resolver_tool}" 2 '' "${resolver_tool}" --allow-resolver
    run_resolver_case "TOOL-MALFORMED-${resolver_tool}" 2 '' "${resolver_tool}" --allow-resolver=true
done
run_resolver_case HELP 2 '' --allow-resolver --help
run_resolver_case VERSION 2 '' --allow-resolver --version
resolver_args='print(context.hasSlot("resolver"))
print(process.args().size())
print(process.args().at(0))'
run_resolver_case EVAL_ARGUMENT 0 $'false\n1\n--allow-resolver' -e "${resolver_args}" --allow-resolver
printf '%s\n' "${resolver_args}" >"${resolver_source}"
run_resolver_case FILE_ARGUMENT 0 $'false\n1\n--allow-resolver' "${resolver_source}" --allow-resolver
run_resolver_case CLEAN_ARGS 0 $'true\n1\n--allow-resolver' --allow-resolver "${resolver_source}" --allow-resolver

# External OS execution (EXTERNAL_PROCESS.md): denied by default, granted only by
# --allow-execution to the RootActor, never inherited by spawned Actors, launching
# directly with real stream EOF, and a child left running is reclaimed at close.
# No ExitStatus is asserted: the decorated Process exposes no verified terminal category.
execution_failures=0
execution_cases=0
execution_probe='print(context.hasSlot("execution"))'
execution_launch='child: execution.start("/bin/echo", ["native-child"], Map(), null).value()
text: ""
chunk: child.stdout().read(65536).value()
while(chunk !== null) {
    text = text + Encoding.UTF8.decode(chunk)
    chunk = child.stdout().read(65536).value()
}
err: child.stderr().read(65536).value()
print(text == "native-child\n")
print(err === null)'
execution_worker="${tmp_dir}/execution-worker.protos"
printf '%s\n' 'worker: () => { { inherited: () => { context.hasSlot("execution") } } }' 'null' \
    >"${execution_worker}"
execution_spawn="${tmp_dir}/execution-spawn.protos"
printf '%s\n' 'worker: Actor.spawn("./execution-worker.protos", "worker")' \
    'print(worker.request("inherited").value())' 'worker.stop()' >"${execution_spawn}"

run_execution_case() {
    local name="$1"
    local expected_status="$2"
    local expected_output="$3"
    shift 3
    local case_out="${tmp_dir}/execution-${name}.out"
    local case_err="${tmp_dir}/execution-${name}.err"
    PROTOS_HOME="${root}" "${native_bin}" "$@" >"${case_out}" 2>"${case_err}"
    local case_status=$?
    execution_cases=$((execution_cases + 1))
    echo "NATIVE_EXECUTION_${name}_STATUS=${case_status}"
    if [[ "${case_status}" -ne "${expected_status}" ]] ||
       [[ "$(cat "${case_out}")" != "${expected_output}" ]] ||
       grep -Fq 'Internal error' "${case_err}"; then
        cat "${case_out}" "${case_err}" >&2
        echo "NATIVE_EXECUTION_CASE_FAILURE=${name}" >&2
        execution_failures=$((execution_failures + 1))
    fi
}

run_execution_case DEFAULT_DENIED 0 'false' -e "${execution_probe}"
run_execution_case OTHERS_DENIED 0 'false' --allow-clock --allow-entropy -e "${execution_probe}"
run_execution_case GRANTED 0 'true' --allow-execution -e "${execution_probe}"
run_execution_case SPAWNED_NOT_INHERITED 0 'false' --allow-execution "${execution_spawn}"
if [[ -x /bin/echo ]]; then
    run_execution_case LAUNCH_AND_STREAMS 0 $'true\ntrue' --allow-execution -e "${execution_launch}"
fi
# Binary stdin: bytes written to /bin/cat return unchanged on stdout once stdin is closed.
execution_stdin='child: execution.start("/bin/cat", [], Map(), null).value()
child.stdin().write(Encoding.UTF8.encode("native-stdin")).value()
child.stdin().close().value()
text: ""
chunk: child.stdout().read(65536).value()
while(chunk !== null) {
    text = text + Encoding.UTF8.decode(chunk)
    chunk = child.stdout().read(65536).value()
}
print(text == "native-stdin")'
if [[ -x /bin/cat ]]; then
    run_execution_case STDIN_CLOSE 0 'true' --allow-execution -e "${execution_stdin}"
fi
# The output writer belongs to a descendant after the direct child has exited.
execution_descendant='child: execution.start("/bin/sh",
    ["-c", "(/bin/sleep 0.2; printf late) & printf early"], Map(), null).value()
child.stdin().close().value()
child.wait().value()
text: ""
chunk: child.stdout().read(65536).value()
while(chunk !== null) {
    text = text + Encoding.UTF8.decode(chunk)
    chunk = child.stdout().read(65536).value()
}
print(text)'
if [[ -x /bin/sh && -x /bin/sleep ]]; then
    run_execution_case DESCENDANT_OUTPUT 0 'earlylate' --allow-execution -e "${execution_descendant}"
fi
# The Native host installs its own POSIX provider: wait() resolves to an ExitStatus
# decoded from the real reaped status. 143 is a normal exit, never a signal guess;
# a self-delivered SIGTERM is a signal termination without an exit code.
execution_status='run: (script) => {
    child: execution.start("/bin/sh", ["-c", script], Map(), null).value()
    child.stdin().close().value()
    child.wait().value()
}
zero: run("exit 0")
print(zero.exited() && (zero.exitCode() == 0) && (zero.signaled() == false))
normal: run("exit 143")
print(normal.exited() && (normal.exitCode() == 143) && (normal.signaled() == false))
killed: run("kill -TERM $$")
print((killed.exited() == false) && (killed.exitCode() === null) && killed.signaled())'
if [[ -x /bin/sh ]]; then
    run_execution_case EXIT_STATUS 0 $'true\ntrue\ntrue' --allow-execution -e "${execution_status}"
fi
# Execution is independent of the guest Filesystem capability.
run_execution_case NO_FILESYSTEM 0 'false' --allow-execution \
    -e 'print(context.hasSlot("filesystem"))'
if [[ -x /usr/bin/env ]]; then
    run_execution_case EMPTY_ENVIRONMENT 0 'true' --allow-execution -e '
child: execution.start("/usr/bin/env", [], Map(), null).value()
print(child.stdout().read(65536).value() === null)'
fi
# Close stdout in the direct child while a descendant retains stderr. Waiting for
# the direct child must neither close stderr nor classify its open writer as EOF.
if [[ -x /bin/sh && -x /bin/sleep ]]; then
    run_execution_case INDEPENDENT_STDERR_AFTER_REAP 0 $'true\nlate-error' --allow-execution -e '
child: execution.start("/bin/sh",
    ["-c", "exec 1>&-; (/bin/sleep 0.2; printf late-error >&2) &"], Map(), null).value()
child.stdin().close().value()
child.wait().value()
print(child.stdout().read(65536).value() === null)
text: ""
chunk: child.stderr().read(65536).value()
while(chunk !== null) {
    text = text + Encoding.UTF8.decode(chunk)
    chunk = child.stderr().read(65536).value()
}
print(text)'
fi
# Wait establishes that /bin/true has closed stdin. A 1 MiB write stays within
# the guest admission limit and exceeds the transport pipe. Capacity refusal
# must fail this regression rather than masquerade as EPIPE. The second child and final
# marker execute IN THIS SAME native host: an IOError alone does not prove that
# SIGPIPE left the Native Image process alive. No global signal policy is changed.
if [[ -x /bin/true && -x /bin/echo ]]; then
    run_execution_case SIGPIPE_HOST_SURVIVES 0 $'true\ntrue\n42' --allow-execution -e '
child: execution.start("/bin/true", [], Map(), null).value()
child.wait().value()
payload: "x"
index: 0
while(index < 20) { payload = payload + payload; index = index + 1 }
broken: IOError.handle(() => {
    IOCapacityExhausted.handle(() => {
        child.stdin().write(Encoding.UTF8.encode(payload)).value()
        false
    }, (error) => { false })
}, (error) => { true })
print(broken)
next: execution.start("/bin/echo", ["survived"], Map(), null).value()
text: ""
chunk: next.stdout().read(65536).value()
while(chunk !== null) {
    text = text + Encoding.UTF8.decode(chunk)
    chunk = next.stdout().read(65536).value()
}
print(text == "survived\n")
print(40 + 2)'
fi
# The live child is a private copy of sleep under this run's temporary directory, so
# its absolute command line identifies it exclusively; no PID is exposed to Protos.
# After the CLI has exited, the harness checks with ps that no process with exactly
# that command line is still running and fails the case if one is. It never signals a
# PID observed through ps, whose identity may change between observation and signal;
# the fixture's duration is finite, so a leaked fixture ends on its own.
if [[ -x /bin/sleep ]] && command -v ps >/dev/null 2>&1; then
    execution_fixture="${tmp_dir}/protos-execution-fixture-sleep"
    cp /bin/sleep "${execution_fixture}"
    chmod +x "${execution_fixture}"
    run_execution_case LIVE_CHILD_RECLAIMED 0 'started' --allow-execution \
        -e "execution.start(\"${execution_fixture}\", [\"30\"], Map(), null).value()
print(\"started\")"
    execution_fixture_pids="$(ps -A -o pid=,args= | awk -v want="${execution_fixture} 30" \
        '{ pid = $1; $1 = ""; sub(/^ /, ""); if ($0 == want) print pid }')"
    if [[ -n "${execution_fixture_pids}" ]]; then
        echo "NATIVE_EXECUTION_LIVE_CHILD_RECLAIMED_ALIVE=true"
        echo "NATIVE_EXECUTION_CASE_FAILURE=LIVE_CHILD_RECLAIMED_STILL_RUNNING" >&2
        execution_failures=$((execution_failures + 1))
    else
        echo "NATIVE_EXECUTION_LIVE_CHILD_RECLAIMED_ALIVE=false"
    fi
fi

# I155 / D248-B: the explicit --allow-filesystem=<directory> grant on the real
# Native executable. Every case asserts its exit status; success cases also
# assert guest output and host-side effects, failure cases assert that no guest
# expression ran and that no internal error escaped.
fs_root="${tmp_dir}/i155-data"
fs_source="${tmp_dir}/i155-app.protos"
mkdir -p "${fs_root}/nested/deeper"
fs_write_read='Files: import("std:io/files")
path: Path.relative().child("nested").child("note.txt")
Files.writeAllText(filesystem, path, "native-granted", Encoding.UTF8).value()
print(Files.readAllText(filesystem, path, Encoding.UTF8).value())'
fs_read_note='Files: import("std:io/files")
print(Files.readAllText(filesystem, Path.relative().child("nested").child("note.txt"), Encoding.UTF8).value())'
fs_tree='Files: import("std:io/files")
deeper: Path.relative().child("nested").child("deeper")
Files.writeAllText(filesystem, deeper.child("a.txt"), "moved", Encoding.UTF8).value()
filesystem.replace(deeper.child("a.txt"), deeper.child("b.txt")).value()
kinds: ""
filesystem.entries(deeper).value().each((entry) => { kinds = kinds + entry.name + ":" + entry.kind })
filesystem.remove(deeper.child("b.txt")).value()
remaining: "nonempty"
if(filesystem.entries(deeper).value().size() == 0) { remaining = "empty" }
print("entries-" + kinds + "-" + remaining)'
fs_open_at_exit='file: filesystem.open(Path.relative().child("held.txt"), write: true, create: true).value()
print("held")'
printf '%s\n' "${fs_write_read}" >"${fs_source}"

fs_failures=0

run_fs_case() {
    local name="$1"
    local expected_status="$2"
    local expected_output="$3"
    shift 3
    local case_out="${tmp_dir}/i155-${name}.out"
    local case_err="${tmp_dir}/i155-${name}.err"
    local case_ok=1

    PROTOS_HOME="${root}" \
        "${native_bin}" \
        "$@" \
        >"${case_out}" 2>"${case_err}"
    local case_status=$?

    if [[ "${case_status}" -ne "${expected_status}" ]]; then
        case_ok=0
    elif [[ -n "${expected_output}" ]] && ! grep -Fq "${expected_output}" "${case_out}"; then
        case_ok=0
    elif grep -Fq 'Internal error' "${case_err}"; then
        case_ok=0
    elif [[ "${expected_status}" -ne 0 ]] && grep -Fq 'ran' "${case_out}"; then
        case_ok=0
    fi

    echo "NATIVE_I155_${name}_STATUS=${case_status}"
    if [[ "${case_ok}" -ne 1 ]]; then
        cat "${case_out}" "${case_err}" >&2
        echo "NATIVE_I155_CASE_FAILURE=${name}" >&2
        fs_failures=$((fs_failures + 1))
    fi
}

# Platform qualification: an unsupported provider must fail closed (exit 1
# with the provisioning diagnostic and no guest effect), never succeed.
fs_platform="SUPPORTED"
# I156 confinement verdicts; each is set only by the case that actually ran.
# NOT_EXECUTED_UNSUPPORTED_PLATFORM: the grant was refused before any case ran.
fs_symlink_status="NOT_EXECUTED_UNSUPPORTED_PLATFORM"
fs_bind_status="NOT_EXECUTED_UNSUPPORTED_PLATFORM"
PROTOS_HOME="${root}" "${native_bin}" "--allow-filesystem=${fs_root}" -e 'null' \
    >"${tmp_dir}/i155-probe.out" 2>"${tmp_dir}/i155-probe.err"
fs_probe_status=$?
if [[ "${fs_probe_status}" -ne 0 ]]; then
    # Only a controlled provisioning refusal counts as an unsupported platform;
    # any other failure of the probe is a regression.
    if [[ "${fs_probe_status}" -eq 1 ]] && \
       grep -Fq 'cannot authorize Filesystem root' "${tmp_dir}/i155-probe.err" && \
       ! grep -Fq 'Internal error' "${tmp_dir}/i155-probe.err"; then
        fs_platform="UNSUPPORTED"
    else
        fs_platform="PROBE_FAILED"
        cat "${tmp_dir}/i155-probe.out" "${tmp_dir}/i155-probe.err" >&2
        echo "NATIVE_I155_CASE_FAILURE=PROBE" >&2
        fs_failures=$((fs_failures + 1))
    fi
    run_fs_case FAIL_CLOSED 1 "" "--allow-filesystem=${fs_root}" -e "${fs_write_read}"
    if ! grep -Fq 'cannot authorize Filesystem root' "${tmp_dir}/i155-FAIL_CLOSED.err" || \
       [[ -e "${fs_root}/nested/note.txt" ]]; then
        echo "NATIVE_I155_CASE_FAILURE=FAIL_CLOSED_EFFECT" >&2
        fs_failures=$((fs_failures + 1))
    fi
    # The grant must also stay absent without the option on such a platform.
    run_fs_case NO_GRANT 1 "" -e 'filesystem'
else
    run_fs_case NO_GRANT 1 "" -e 'filesystem'
    run_fs_case EVAL_WRITE_READ 0 "native-granted" "--allow-filesystem=${fs_root}" -e "${fs_write_read}"
    rm -f "${fs_root}/nested/note.txt"
    run_fs_case SOURCE_WRITE_READ 0 "native-granted" "--allow-filesystem=${fs_root}" "${fs_source}"
    run_fs_case PERSISTENCE 0 "native-granted" "--allow-filesystem=${fs_root}" -e "${fs_read_note}"
    run_fs_case TREE_OPERATIONS 0 "entries-b.txt:regular-empty" "--allow-filesystem=${fs_root}" -e "${fs_tree}"
    run_fs_case POST_ENTRY_ARGUMENT 1 "" -e 'filesystem' "--allow-filesystem=${fs_root}"
    run_fs_case DUPLICATE 2 "" "--allow-filesystem=${fs_root}" "--allow-filesystem=${fs_root}" -e 'null'
    run_fs_case BARE 2 "" --allow-filesystem -e 'null'
    run_fs_case EMPTY 2 "" "--allow-filesystem=" -e 'null'
    run_fs_case MISSING_ROOT 1 "" "--allow-filesystem=${tmp_dir}/i155-missing" -e 'print("ran")'
    if [[ -e "${tmp_dir}/i155-missing" ]]; then
        echo "NATIVE_I155_CASE_FAILURE=MISSING_ROOT_CREATED" >&2
        fs_failures=$((fs_failures + 1))
    fi
    run_fs_case FILE_ROOT 1 "" "--allow-filesystem=${fs_source}" -e 'print("ran")'
    if ln -s "${fs_root}" "${tmp_dir}/i155-link" 2>/dev/null; then
        run_fs_case SYMLINK_ROOT 1 "" "--allow-filesystem=${tmp_dir}/i155-link" -e 'print("ran")'
    else
        echo "NATIVE_I155_SYMLINK_ROOT_STATUS=SKIPPED_NO_SYMLINK_FIXTURE"
    fi
    run_fs_case TOOL_PACKAGE 2 "" "--allow-filesystem=${fs_root}" package
    run_fs_case TOOL_TEST 2 "" test "--allow-filesystem=${fs_root}"
    run_fs_case TOOL_LINT 2 "" lint "--allow-filesystem=${fs_root}"
    run_fs_case NO_NETWORK 1 "" "--allow-filesystem=${fs_root}" -e 'network'
    run_fs_case NO_RESOLVER 1 "" "--allow-filesystem=${fs_root}" -e 'resolver'
    # Cleanup: a File left open at exit is revoked by Process termination, and
    # the next invocation can remove its entry.
    run_fs_case OPEN_AT_EXIT 0 "held" "--allow-filesystem=${fs_root}" -e "${fs_open_at_exit}"
    run_fs_case CLEANUP_REMOVE 0 "removed" "--allow-filesystem=${fs_root}" \
        -e 'filesystem.remove(Path.relative().child("held.txt")).value()
print("removed")'
    if [[ -e "${fs_root}/held.txt" ]]; then
        echo "NATIVE_I155_CASE_FAILURE=CLEANUP_ENTRY_REMAINS" >&2
        fs_failures=$((fs_failures + 1))
    fi

    # I156: kernel confinement of the FFM backend on the real executable. A
    # symbolic link inside the root never reaches the outside directory.
    fs_outside="${tmp_dir}/i156-outside"
    mkdir -p "${fs_outside}"
    printf 'outside\n' >"${fs_outside}/secret.txt"
    if ln -s "${fs_outside}" "${fs_root}/escape" 2>/dev/null; then
        fs_failures_before_symlink=${fs_failures}
        run_fs_case OUTSIDE_VIA_SYMLINK 1 "" "--allow-filesystem=${fs_root}" -e 'Files: import("std:io/files")
Files.writeAllText(filesystem, Path.relative().child("escape").child("new.txt"), "x", Encoding.UTF8).value()
print("ran")'
        if [[ -e "${fs_outside}/new.txt" ]]; then
            echo "NATIVE_I155_CASE_FAILURE=OUTSIDE_VIA_SYMLINK_EFFECT" >&2
            fs_failures=$((fs_failures + 1))
        fi
        rm -f "${fs_root}/escape"
        if [[ "${fs_failures}" -eq "${fs_failures_before_symlink}" ]]; then
            fs_symlink_status="EXECUTED_REFUSED"
        else
            fs_symlink_status="FAILED"
        fi
    else
        fs_symlink_status="NOT_EXECUTED_NO_SYMLINK_FIXTURE"
    fi
    # A bind mount below the root is never traversed. Creating one needs
    # privileges; an explicit admission is printed instead of a pass.
    mkdir -p "${fs_root}/bound"
    # The source lives under the same temporary directory: this is the
    # same-device case only; the cross-device case is covered by the JVM test
    # otherDeviceBindMountIsNeverTraversed.
    if mount --bind "${fs_outside}" "${fs_root}/bound" 2>/dev/null; then
        fs_failures_before_bind=${fs_failures}
        run_fs_case BIND_MOUNT_SAME_DEVICE 1 "" "--allow-filesystem=${fs_root}" -e 'Files: import("std:io/files")
print(Files.readAllText(filesystem, Path.relative().child("bound").child("secret.txt"), Encoding.UTF8).value())
print("ran")'
        if ! umount "${fs_root}/bound" 2>/dev/null; then
            echo "NATIVE_I155_CASE_FAILURE=BIND_MOUNT_UNMOUNT" >&2
            fs_failures=$((fs_failures + 1))
        fi
        if [[ "${fs_failures}" -eq "${fs_failures_before_bind}" ]]; then
            fs_bind_status="EXECUTED_REFUSED_SAME_DEVICE"
        else
            fs_bind_status="FAILED"
        fi
    else
        fs_bind_status="ADMISSION_HOST_REFUSES_BIND_MOUNT"
    fi
    rmdir "${fs_root}/bound" 2>/dev/null
    if [[ "$(cat "${fs_outside}/secret.txt")" != "outside" ]]; then
        echo "NATIVE_I155_CASE_FAILURE=OUTSIDE_CONTENT_CHANGED" >&2
        fs_failures=$((fs_failures + 1))
    fi
fi

# I156 / PLAT073-C: the Filesystem bridge is Java FFM only. The executable must
# not ship or depend on a private Protos JNI library.
native_private_libraries="$(
    find "$(dirname "${native_bin}")" -maxdepth 2 -name '*.so' 2>/dev/null \
        | grep -Ei 'protos|confined|filesystem' || true
)"
native_needed_private="$(
    readelf -d "${native_bin}" 2>/dev/null \
        | grep -E 'NEEDED' | grep -Ei 'protos|confined|filesystem' || true
)"
if [[ -n "${native_private_libraries}" || -n "${native_needed_private}" ]]; then
    echo "${native_private_libraries}${native_needed_private}" >&2
    echo "NATIVE_I155_CASE_FAILURE=PRIVATE_NATIVE_LIBRARY" >&2
    fs_failures=$((fs_failures + 1))
fi

# I170 / PLAT077-A: the generic FFM provider integrated in this executable.
# Default-off: no provider without --allow-library; authorization alone loads
# nothing; libraries load lazily when the application acquires ffi:<alias>.
# Only OS-provided shared objects are loaded; nothing is bundled.
ffm_failures=0
ffm_cases=0
ffm_cases_dir="${root}/protos/tests/foreign-provider"

run_ffm_case() {
    local name="$1"
    local expected_status="$2"
    local expected_output="$3"
    shift 3
    local case_out="${tmp_dir}/i170-${name}.out"
    local case_err="${tmp_dir}/i170-${name}.err"
    local case_ok=1

    (cd "${tmp_dir}" && PROTOS_HOME="${root}" "${native_bin}" "$@") \
        >"${case_out}" 2>"${case_err}"
    local case_status=$?
    ffm_cases=$((ffm_cases + 1))

    if [[ "${expected_status}" == "nonzero" ]]; then
        if [[ "${case_status}" -eq 0 ]] || grep -Fq 'ran' "${case_out}"; then
            case_ok=0
        fi
    elif [[ "${case_status}" -ne "${expected_status}" ]]; then
        case_ok=0
    fi
    if [[ "${case_ok}" -ne 1 ]]; then
        :
    elif [[ -n "${expected_output}" ]] && ! grep -Fq "${expected_output}" "${case_out}"; then
        case_ok=0
    elif grep -Eq 'Internal error|MissingForeignRegistrationError|UnsatisfiedLinkError' \
            "${case_out}" "${case_err}"; then
        case_ok=0
    fi

    echo "NATIVE_I170_${name}_STATUS=${case_status}"
    if [[ "${case_ok}" -ne 1 ]]; then
        cat "${case_out}" "${case_err}" >&2
        echo "NATIVE_I170_CASE_FAILURE=${name}" >&2
        ffm_failures=$((ffm_failures + 1))
    fi
}

run_ffm_case ORDINARY 0 'i170-ordinary' -e 'print("i170-ordinary")'
run_ffm_case AUTHORIZED_NO_IMPORT 0 'i170-no-import' \
    --allow-library sqlite3=libsqlite3.so.0 -e 'print("i170-no-import")'
run_ffm_case STD_FFI_NO_ACQUISITION 0 'i170-std-ffi' \
    --allow-library sqlite3=/nonexistent/i170/libsqlite3.so.0 \
    -e 'FFI: import("std:ffi")
FFI.out(FFI.i32)
print("i170-std-ffi")'
run_ffm_case STD_SQLITE_NO_CONNECTION 0 'i170-std-sqlite' \
    --allow-library sqlite3=/nonexistent/i170/libsqlite3.so.0 \
    -e 'SQLite: import("std:sqlite")
print("i170-std-sqlite")'
run_ffm_case NO_AUTHORIZATION_NO_ROUTE 0 'i170-no-route' \
    -e 'Error.handle(() => import("ffi:sqlite3"), (error) => { print("i170-no-route") })'
run_ffm_case ALLOW_LIBRARY_BARE 2 '' --allow-library sqlite3 -e 'null'
run_ffm_case ALLOW_LIBRARY_EMPTY_TARGET 2 '' --allow-library 'sqlite3=' -e 'null'
run_ffm_case ALLOW_LIBRARY_INVALID_ALIAS 2 '' --allow-library '../x=libsqlite3.so.0' -e 'null'
run_ffm_case ALLOW_LIBRARY_DUPLICATE 2 '' \
    --allow-library sqlite3=libsqlite3.so.0 --allow-library sqlite3=libsqlite3.so.0 -e 'null'
# A Native executable never loads a dynamic Java provider JAR.
run_ffm_case PROVIDER_PATH_REFUSED 2 '' \
    --foreign-provider-path "${tmp_dir}" -e 'print("ran")'
run_ffm_case MISSING_LIBRARY 0 'library-unavailable' \
    --allow-library missing=/nonexistent/i170/libnone.so \
    -e 'error: null
ForeignError.handle(() => import("ffi:missing"), (e) => { error = e })
print(error.foreignCategory)'

# Declarative std:ffi layouts: no library grant, no SQLite or C compiler needed.
run_ffm_case LAYOUTS 0 'FFM-LAYOUTS-PASS' "${ffm_cases_dir}/ffm-layouts.protos"

# SQLite: the OS-provided libsqlite3.so.0 only.
ffm_sqlite_status="EXECUTED"
if ! { ldconfig -p 2>/dev/null | grep -Fq 'libsqlite3.so.0'; }; then
    ffm_sqlite_status="NOT_EXECUTED_NO_OS_LIBSQLITE3"
    echo "NATIVE_I170_CASE_FAILURE=NO_OS_LIBSQLITE3" >&2
    ffm_failures=$((ffm_failures + 1))
else
    ffm_db="${tmp_dir}/i170-durable.sqlite"
    run_ffm_case SQLITE_MEMORY_QUERY 0 'LIB029-QUERY-PASS' \
        --allow-library sqlite3=libsqlite3.so.0 "${ffm_cases_dir}/sqlite-query.protos"
    run_ffm_case SQLITE_TEXT_BLOB 0 'LIB030-BIND-TEXT-BLOB-PASS' \
        --allow-library sqlite3=libsqlite3.so.0 "${ffm_cases_dir}/sqlite-bind-text-blob.protos"
    run_ffm_case SQLITE_LOCAL_TRANSACTIONS 0 'LIB031-DURABLE-PASS' \
        --allow-library sqlite3=libsqlite3.so.0 "${ffm_cases_dir}/sqlite-durable.protos" \
        local "${tmp_dir}/i170-local.sqlite"
    run_ffm_case SQLITE_SEED 0 'LIB031-DURABLE-PASS' \
        --allow-library sqlite3=libsqlite3.so.0 "${ffm_cases_dir}/sqlite-durable.protos" \
        seed "${ffm_db}"
    run_ffm_case SQLITE_REOPEN 0 'LIB031-DURABLE-PASS' \
        --allow-library sqlite3=libsqlite3.so.0 "${ffm_cases_dir}/sqlite-durable.protos" \
        verify "${ffm_db}"
    run_ffm_case SQLITE_LIFECYCLE 0 'LIB029-LIFECYCLE-PASS' \
        --allow-library sqlite3=libsqlite3.so.0 \
        "${ffm_cases_dir}/sqlite-lifecycle.protos"
    run_ffm_case SQLITE_UNAUTHORIZED 0 'unauthorized-library' \
        --allow-library other=libsqlite3.so.0 \
        -e 'error: null
ForeignError.handle(() => import("ffi:sqlite3"), (e) => { error = e })
print(error.foreignCategory)'
fi
# Persistent SQLite files stay inside the temporary directory only.

# Independent C fixtures, compiled into the temporary directory only.
ffm_fixture_status="EXECUTED"
ffm_cc="$(command -v cc || command -v gcc || command -v clang || true)"
if [[ -z "${ffm_cc}" ]]; then
    ffm_fixture_status="NOT_EXECUTED_NO_C_COMPILER"
    echo "NATIVE_I170_CASE_FAILURE=NO_C_COMPILER" >&2
    ffm_failures=$((ffm_failures + 1))
elif ! "${ffm_cc}" -shared -fPIC -O2 -std=c11 -Wall -Werror \
        -o "${tmp_dir}/libffi_test.so" "${ffm_cases_dir}/native/ffi_test.c" || \
     ! "${ffm_cc}" -shared -fPIC -O2 -std=c11 -Wall -Werror \
        -o "${tmp_dir}/libffi_second.so" "${ffm_cases_dir}/native/ffi_second.c"; then
    ffm_fixture_status="FIXTURE_COMPILE_FAILED"
    echo "NATIVE_I170_CASE_FAILURE=FIXTURE_COMPILE" >&2
    ffm_failures=$((ffm_failures + 1))
else
    run_ffm_case TWO_LIBRARIES 0 'FFM-LIBRARY-ALLOWLIST-PASS' \
        --allow-library "first=${tmp_dir}/libffi_test.so" \
        --allow-library "second=${tmp_dir}/libffi_second.so" \
        "${ffm_cases_dir}/ffm-library-allowlist.protos" "${tmp_dir}/i170-second-marker.txt"
    run_ffm_case SCALAR_LAYOUT_ABI 0 'FFM-SCALAR-LAYOUT-ABI-PASS' \
        --allow-library "first=${tmp_dir}/libffi_test.so" \
        --allow-library "second=${tmp_dir}/libffi_second.so" \
        "${ffm_cases_dir}/ffm-scalar-layout-abi.protos"
    run_ffm_case ROOT_ACTOR 0 'FFM-ROOT-ACTOR-PASS' \
        --allow-library "first=${tmp_dir}/libffi_test.so" \
        --allow-library "second=${tmp_dir}/libffi_second.so" \
        "${ffm_cases_dir}/ffm-root-actor.protos"
    run_ffm_case POINTERS 0 'FFM-POINTERS-PASS' \
        --allow-library "first=${tmp_dir}/libffi_test.so" \
        --allow-library "second=${tmp_dir}/libffi_second.so" \
        "${ffm_cases_dir}/ffm-pointers.protos"
    run_ffm_case MISSING_SYMBOL 0 'symbol-not-found' \
        --allow-library "first=${tmp_dir}/libffi_test.so" \
        -e 'FFI: import("std:ffi")
first: import("ffi:first")
error: null
ForeignError.handle(() => FFI.bind(first, "ffi_test_no_such_symbol", FFI.i32, Array()), (e) => { error = e })
print(error.foreignCategory)'
    # D(D,D,D)-shaped declaration of a void(void) counter: exact but not
    # registered in this image, so it is refused before linking or entering C.
    run_ffm_case UNSUPPORTED_SIGNATURE 0 'i170-unsupported-refused touched=0' \
        --allow-library "first=${tmp_dir}/libffi_test.so" \
        -e 'FFI: import("std:ffi")
first: import("ffi:first")
refused: false
Error.handle(() => FFI.bind(first, "ffi_test_touch", FFI.f64, Array(FFI.f64, FFI.f64, FFI.f64)), (e) => { refused = true })
count: FFI.bind(first, "ffi_test_touch_count", FFI.i32, Array())
(refused && (count() == 0)).ifTrue(() => { print("i170-unsupported-refused touched=0") })'
fi

# Truffle's fallback-runtime warning must be absent: guest JIT is required.
fallback_markers="$(
    grep -Eic \
        'fallback runtime that does not support runtime compilation' \
        "${probe_log}" \
        || true
)"

opt_failed="$(
    grep -Eic \
        '\bopt failed\b' \
        "${probe_log}" \
        || true
)"

frame_failures="$(
    grep -Eic \
        'FrameWithoutBoxing.*should not be materialized|should not be materialized.*FrameWithoutBoxing' \
        "${probe_log}" \
        || true
)"

compilation_failures="$(
    grep -Eic \
        'Compilation failed|Internal error' \
        "${probe_log}" \
        || true
)"

opt_done="$(
    grep -Eic \
        '\bopt done\b' \
        "${probe_log}" \
        || true
)"

# The final print(f()) must yield exactly the line 1: a successful compilation
# with a wrong result is not a pass.
probe_result_ok=0
if grep -Eq '^1$' "${probe_log}"; then
    probe_result_ok=1
fi

bytecode_root_tier2="$(
    { grep -Ei '\bopt done\b' "${probe_log}" || true; } \
        | { grep -E 'Tier ?2' || true; } \
        | { grep -c 'ProtosBytecodeRootNodeGen' || true; }
)"

builtin_root_tier2="$(
    { grep -Ei '\bopt done\b' "${probe_log}" || true; } \
        | { grep -E 'Tier ?2' || true; } \
        | { grep -c 'ProtosBuiltinCallRootNode' || true; }
)"

echo "NATIVE_VERSION_STATUS=${version_status}"
echo "NATIVE_HELP_STATUS=${help_status}"
echo "NATIVE_GUEST_SMOKE_STATUS=${smoke_status}"
echo "NATIVE_TEST_TOOL_STATUS=${test_tool_status}"
echo "NATIVE_NUMERIC_CONFORMANCE_STATUS=${native_numeric_status}"
echo "NATIVE_NUMERIC_CONFORMANCE_FILES=${native_numeric_cases}"
echo "NATIVE_TLS_CONFORMANCE_STATUS=${native_tls_status}"
echo "NATIVE_TLS_CONFORMANCE_FILES=${native_tls_cases}"
echo "NATIVE_HMAC_CONFORMANCE_STATUS=${native_hmac_status}"
echo "NATIVE_HMAC_CONFORMANCE_FILES=${native_hmac_cases}"
echo "NATIVE_TEST_TOOL_OUTPUT_OK=${test_tool_output_ok}"
echo "NATIVE_TEST_TOOL_CONTEXT_TEARDOWN_FAILURES=${test_tool_context_teardown_failures}"
cat "${dap_log}"
echo "NATIVE_DAP_STACKTRACE_STATUS=${dap_status}"
echo "NATIVE_LSP_WORKSPACE_SYMBOLS_STATUS=${lsp_status}"
echo "NATIVE_REGEX_STATUS=${regex_status}"
echo "NATIVE_INSPECT_FAILURES=${inspect_failures}"
echo "NATIVE_RESOLVER_CASES=${resolver_cases}"
echo "NATIVE_RESOLVER_FAILURES=${resolver_failures}"
echo "NATIVE_I155_FILESYSTEM_PLATFORM=${fs_platform}"
echo "NATIVE_I155_FILESYSTEM_FAILURES=${fs_failures}"
echo "NATIVE_I156_OUTSIDE_VIA_SYMLINK_STATUS=${fs_symlink_status}"
echo "NATIVE_I156_BIND_MOUNT_STATUS=${fs_bind_status}"
# T04/T05 (parent moved outside the root) are verified only by the JVM test
# ProtosLinuxConfinedFilesystemSecurityTest; this script never reports them.
echo "NATIVE_GUEST_JIT_STATUS=${probe_status}"
echo "NATIVE_GUEST_JIT_RESULT_OK=${probe_result_ok}"
echo "NATIVE_FALLBACK_RUNTIME_MARKERS=${fallback_markers}"
echo "OPT_DONE=${opt_done}"
echo "BYTECODE_ROOT_TIER2_COMPILATIONS=${bytecode_root_tier2}"
echo "BUILTIN_CALL_ROOT_TIER2_COMPILATIONS=${builtin_root_tier2}"
echo "OPT_FAILED=${opt_failed}"
echo "FRAME_WITHOUT_BOXING_FAILURES=${frame_failures}"
echo "COMPILATION_FAILURES=${compilation_failures}"

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

if [[ "${test_tool_status}" -ne 0 ]]; then
    cat "${test_tool_log}" >&2
    echo "NATIVE_TEST_TOOL_SMOKE=FAIL_STATUS" >&2
    exit 1
fi

if [[ "${test_tool_output_ok}" -ne 1 ]]; then
    cat "${test_tool_log}" >&2
    echo "NATIVE_TEST_TOOL_SMOKE=FAIL_OUTPUT" >&2
    exit 1
fi

if [[ "${native_numeric_status}" -ne 0 ]]; then
    echo "NATIVE_NUMERIC_CONFORMANCE=FAIL" >&2
    exit 1
fi

if [[ "${native_tls_status}" -ne 0 ]] || [[ "${native_tls_cases}" -ne 3 ]]; then
    echo "NATIVE_TLS_CONFORMANCE=FAIL" >&2
    exit 1
fi

if [[ "${test_tool_context_teardown_failures}" -ne 0 ]]; then
    cat "${test_tool_log}" >&2
    echo "NATIVE_TEST_TOOL_SMOKE=FAIL_CONTEXT_TEARDOWN" >&2
    exit 1
fi

if [[ "${dap_status}" -ne 0 ]]; then
    cat "${dap_log}" >&2
    echo "NATIVE_DAP_STACKTRACE_REGRESSION=FAIL" >&2
    exit 1
fi

if [[ "${inspect_failures}" -ne 0 ]]; then
    echo "NATIVE_INSPECT=FAIL" >&2
    exit 1
fi

if [[ "${fs_failures}" -ne 0 ]]; then
    echo "NATIVE_I155_FILESYSTEM=FAIL" >&2
    exit 1
fi

if [[ "${probe_status}" -ne 0 ]]; then
    cat "${probe_log}" >&2
    echo "NATIVE_GUEST_JIT=FAIL_STATUS" >&2
    exit 1
fi

if [[ "${probe_result_ok}" -ne 1 ]]; then
    cat "${probe_log}" >&2
    echo "NATIVE_GUEST_JIT=FAIL_RESULT" >&2
    exit 1
fi

if [[ "${fallback_markers}" -ne 0 ]]; then
    cat "${probe_log}" >&2
    echo "NATIVE_GUEST_JIT=FAIL_FALLBACK_RUNTIME_SELECTED" >&2
    exit 1
fi

if [[ "${opt_failed}" -ne 0 ]]; then
    cat "${probe_log}" >&2
    echo "NATIVE_GUEST_JIT=FAIL_OPT_FAILED" >&2
    exit 1
fi

if [[ "${frame_failures}" -ne 0 ]]; then
    cat "${probe_log}" >&2
    echo "NATIVE_GUEST_JIT=FAIL_FRAME_WITHOUT_BOXING" >&2
    exit 1
fi

if [[ "${compilation_failures}" -ne 0 ]]; then
    cat "${probe_log}" >&2
    echo "NATIVE_GUEST_JIT=FAIL_COMPILATION" >&2
    exit 1
fi

if [[ "${bytecode_root_tier2}" -lt 1 ]]; then
    cat "${probe_log}" >&2
    echo "NATIVE_GUEST_JIT=FAIL_NO_BYTECODE_ROOT_TIER2" >&2
    exit 1
fi

if [[ "${builtin_root_tier2}" -lt 1 ]]; then
    cat "${probe_log}" >&2
    echo "NATIVE_GUEST_JIT=FAIL_NO_BUILTIN_CALL_ROOT_TIER2" >&2
    exit 1
fi

echo "NATIVE_VERSION_SMOKE=PASS"
echo "NATIVE_HELP_SMOKE=PASS"
echo "NATIVE_GUEST_SMOKE=PASS"
echo "NATIVE_TEST_TOOL_SMOKE=PASS"
echo "NATIVE_NUMERIC_CONFORMANCE=PASS"
echo "NATIVE_TLS_CONFORMANCE=PASS"
echo "NATIVE_DAP_STACKTRACE_REGRESSION=PASS"
echo "NATIVE_INSPECT=PASS"
# Distinct I155 verdicts: PASS means the positive and negative grant cases ran
# and passed; REJECTED_UNSUPPORTED_PLATFORM means only the controlled refusal
# and its absence of effects were verified, not grant write/read behavior.
if [[ "${fs_platform}" == "SUPPORTED" ]]; then
    echo "NATIVE_I155_FILESYSTEM=PASS"
    fs_verified="grant-read-write,tree-operations,persistence,usage-errors,provisioning-refusals,tool-refusal,no-implied-network-resolver,cleanup,no-private-native-library"
    if [[ "${fs_symlink_status}" == "EXECUTED_REFUSED" ]]; then
        fs_verified="${fs_verified},kernel-confined-symlink"
    fi
    if [[ "${fs_bind_status}" == "EXECUTED_REFUSED_SAME_DEVICE" ]]; then
        fs_verified="${fs_verified},same-device-bind-mount-refused"
    fi
    echo "NATIVE_I155_FILESYSTEM_VERIFIED=${fs_verified}"
    echo "NATIVE_I156_NOT_VERIFIED_HERE=t04-t05,other-device-bind-mount"
else
    echo "NATIVE_I155_FILESYSTEM=REJECTED_UNSUPPORTED_PLATFORM"
    echo "NATIVE_I155_FILESYSTEM_VERIFIED=fail-closed-refusal,no-effect,default-absence"
fi
echo "NATIVE_GUEST_JIT=PASS"
echo "FRAME_WITHOUT_BOXING_REGRESSION=PASS"
cat "${lsp_log}"

if [[ "${resolver_failures}" -ne 0 ]]; then
    echo "NATIVE_RESOLVER=FAIL" >&2
    false
elif [[ "${execution_failures}" -ne 0 ]]; then
    echo "NATIVE_EXTERNAL_EXECUTION=FAIL" >&2
    false
elif [[ "${lsp_status}" -ne 0 ]]; then
    echo "NATIVE_LSP_WORKSPACE_SYMBOLS=FAIL" >&2
    false
elif [[ "${regex_status}" -ne 0 ]] || \
     ! grep -Eq '^[[:space:]]*[1-9][0-9]* passed, 0 failed[[:space:]]*$' "${regex_log}"; then
    cat "${regex_log}" >&2
    echo "NATIVE_REGEX=FAIL" >&2
    false
elif [[ "${native_hmac_status}" -ne 0 ]] || \
     [[ "${native_hmac_cases}" -ne 3 ]]; then
    echo "NATIVE_HMAC_SHA256=FAIL" >&2
    false
elif [[ "${native_caller_recovery_failures}" -ne 0 ]]; then
    echo "NATIVE_I115_CALLER_RECOVERY=FAIL" >&2
    false
elif [[ "${ffm_failures}" -ne 0 ]]; then
    echo "NATIVE_I170_FFM=FAIL sqlite=${ffm_sqlite_status} fixtures=${ffm_fixture_status}" >&2
    false
else
    echo "NATIVE_I170_FFM=PASS cases=${ffm_cases}"
    echo "NATIVE_REGEX=PASS"
    echo "NATIVE_HMAC_SHA256=PASS"
    echo "NATIVE_I115_CALLER_RECOVERY=PASS"
    echo "NATIVE_RESOLVER=PASS"
    echo "NATIVE_EXTERNAL_EXECUTION=PASS cases=${execution_cases}"
    echo "NATIVE_REGRESSION_SUITE=PASS"
fi
