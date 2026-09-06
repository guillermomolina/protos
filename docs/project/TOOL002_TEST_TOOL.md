# TOOL002 — Test Tool

Status: READY

Nature: non-normative project implementation record

Architecture owners:

- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_ARCHITECTURE.md`

Normative dependencies inspected by the architecture include:

- `spec/io/PROCESS_IO.md`
- `spec/concurrency/ACTORS.md`
- `spec/concurrency/PARALLEL_EXECUTION.md`
- `spec/semantics/MODULES.md`
- `spec/semantics/ERRORS.md`

Canonical summary:

- `docs/project/IMPLEMENTATION_STATUS.md`

## Purpose

TOOL002 implements the official toolchain-bundled `protos test` experience while
moving Protos-language test policy out of Java/JUnit and into ordinary bundled
Protos tooling wherever the language can express that policy naturally.

Java/JUnit remains appropriate for Java/runtime/host implementation behavior.
The intended validation order is Java implementation tests first, followed by
the Protos Test Tool for the Protos-language corpus.

## Selected architecture

The promoted architecture already closes these directions:

- the test tool is bundled and written primarily in Protos;
- no test syntax, compiler mode, privileged global Test object, or Java-owned
  assertion/suite/expectation policy is introduced;
- one fresh semantic Protos Process / RootActor is the normal isolation boundary
  per ordinary test case;
- outer test parallelism is bounded and independent from concurrency exercised
  inside each test;
- stdout/stderr are private per test and reporting is deterministic;
- capabilities are explicit and real shared resources may require tool-level
  serialization constraints;
- `Closure.parallel` is usable inside tests but is not the runner isolation
  mechanism;
- existing manifests/corpus are preserved during the initial migration.

The architecture also intentionally leaves exact CLI flags, future assertion
helper APIs, discovery policy, `jobs=auto`, resource-declaration syntax, and hard
OS-worker timeout/crash policy open. Implementation slices must not silently
freeze those decisions unless their own audited scope requires and resolves them.

## Initial tracked slices

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-A | READY | Add exact bundled `protos test` dispatch and a tiny Protos entry. Do not migrate the corpus in this slice. |
| TOOL002-B | BLOCKED_BY_DEPENDENCIES | After A, establish/reuse a general fresh-Process exact-execution mechanism. It must not be a Java `TestExecutor` or another test-specific privileged runtime institution. |
| TOOL002-C | BLOCKED_BY_DEPENDENCIES | After B, execute one `.protos` case sequentially in a fresh Process and capture inert outcome plus private streams. |
| TOOL002-D | BLOCKED_BY_DEPENDENCIES | After C, migrate existing general conformance manifest/expectation interpretation from Java to Protos while retaining the corpus. |
| TOOL002-E | BLOCKED_BY_DEPENDENCIES | After D, migrate Package Tool/TOML fixtures away from Java-owned runner policy. |
| TOOL002-F | BLOCKED_BY_DEPENDENCIES | After E, preserve async/Future pending-work and terminal-outcome test coverage through production execution semantics. |
| TOOL002-G | BLOCKED_BY_DEPENDENCIES | After F, migrate Actor/Group scheduler-sensitive language coverage without a test-only concurrency model. |
| TOOL002-H | BLOCKED_BY_DEPENDENCIES | After G, add bounded parallel scheduling of independent fresh Processes, private output capture, and deterministic reporting. |
| TOOL002-I | BLOCKED_BY_DEPENDENCIES | After H, add explicit resource constraints/private capabilities where real external-resource sharing requires them. |
| TOOL002-J | BLOCKED_BY_DEPENDENCIES | After I, integrate the final Java-first / Protos-tool-second validation pipeline. |

The hard-timeout / amortized OS-worker audit remains deferred. It is not part of
TOOL002-A and is not silently made a blocker for the useful initial Test Tool.
If guaranteed recovery from non-preemptible infinite tests becomes a product
requirement, promote that question through an explicit later design/work item.

## First implementation boundary: TOOL002-A

TOOL002-A should be deliberately narrow:

```text
protos test
    -> public Protos driver performs only exact bundled-tool selection/bootstrap
    -> protos/tools/test/Main.protos
    -> ordinary Protos execution
```

The slice should prove the common bundled-tool acquisition boundary and create
no test-discovery, assertion, manifest, Process-per-test, timeout, parallelism,
filter, or reporting policy in Java.

If the audit finds that the current driver lacks a genuinely general bundled-tool
dispatch/index mechanism useful across package/test/bench/etc., that mechanism
may justify separate `CLIxxx` work. Do not create one CLI item merely to hardcode
a second command.

## Closure rule

TOOL002 closes only after all slices required for the selected initial Test Tool
outcome are implemented, validated, and published. Later optional hard-isolation
work does not block closure unless it is explicitly promoted into the parent
scope before closure.
