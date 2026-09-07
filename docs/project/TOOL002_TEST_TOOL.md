# TOOL002 — Test Tool

Status: IN_PROGRESS

Nature: non-normative project implementation record

Architecture owners:

- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md`
- `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`

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
| TOOL002-A | CLOSED | Exact bundled `protos test` dispatch and tiny ordinary-Protos entry published in the same commit at implementation version `0.2.168-SNAPSHOT`; no corpus migration or test policy. |
| TOOL002-B | CLOSED | Publish the local, test-neutral `ProtosFreshProcessExecutor` over `ProtosStandaloneProcessBootstrap`, shared RootActor cooperative terminal dispatch through `ProtosRootTaskExecution`, and inert `ProtosExecutionOutcome`; every invocation uses a fresh semantic Process and terminates it before returning. No TestPlan/scheduler/worker/remote/test policy. Implementation version `0.2.169-SNAPSHOT`. |
| TOOL002-C | CLOSED | Publish test-neutral sequential private-stream capture over `ProtosFreshProcessExecutor`: one exact compiled entry gets private stdin/stdout/stderr, a fresh semantic Process and an inert outcome plus detached captured bytes. No manifest/expectation/scheduler/result-transfer policy. Implementation version `0.2.171-SNAPSHOT`. |
| TOOL002-D | CLOSED | D1-D4 are published; all retained non-Future main-manifest expectation policy is owned by bundled Protos. D4 closes at `0.2.211-SNAPSHOT`; `future-*` remains TOOL002-F. |
| TOOL002-E | READY | TOOL002-D is closed; migrate Package Tool/TOML fixtures away from Java-owned runner policy. |
| TOOL002-F | BLOCKED_BY_DEPENDENCIES | After E, preserve async/Future pending-work and terminal-outcome test coverage through production execution semantics. |
| TOOL002-G | BLOCKED_BY_DEPENDENCIES | After F, migrate Actor/Group scheduler-sensitive language coverage without a test-only concurrency model. |
| TOOL002-H | BLOCKED_BY_DEPENDENCIES | After G, add bounded parallel scheduling of independent fresh Processes, private output capture, and deterministic reporting. |
| TOOL002-I | BLOCKED_BY_DEPENDENCIES | After H, add explicit resource constraints/private capabilities where real external-resource sharing requires them. |
| TOOL002-J | BLOCKED_BY_DEPENDENCIES | After I, integrate the final Java-first / Protos-tool-second validation pipeline. |

The hard-timeout / amortized OS-worker audit remains deferred. It is not part of
TOOL002-A and is not silently made a blocker for the useful initial Test Tool.
If guaranteed recovery from non-preemptible infinite tests becomes a product
requirement, promote that question through an explicit later design/work item.

## TOOL002-A closure

TOOL002-A closes the deliberately narrow bundled-tool bootstrap boundary:

```text
protos test
    -> public Protos driver performs only exact bundled-tool selection/bootstrap
    -> protos/tools/test/Main.protos
    -> ordinary Protos execution
```

The published slice reuses the existing generic bundled-tool resolver and factors
the package/test entry execution through one small common CLI bootstrap helper.
Package retains its separately provisioned confined Filesystem authority; the
test tool receives no Filesystem authority in TOOL002-A. No separate `CLIxxx`
item is created because no independently meaningful public driver mechanism was
needed beyond this TOOL002 bootstrap.

TOOL002-A adds no test discovery, assertion, manifest, Process-per-test, timeout,
parallelism, filter, reporter, or corpus-migration policy.

## Post-A comparative architecture checkpoint — CLOSED

`docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md` completes the required checkpoint
after TOOL002-A. The comparison covers framework-driven, language-native,
process-worker, native-isolation and hermetic/distributed systems.

The checkpoint retains fresh semantic Process / RootActor isolation and adds two
important refinements before runner implementation hardens:

- manifests and future discovery feed stable CaseSpec/CaseId values and one inert
  TestPlan before physical scheduling;
- future parallel scheduling uses general capacity accounting, with capacity-1
  named resources naturally providing mutex/group behavior.

It also confirms that arbitrary hard timeout requires a separately owned physical
worker boundary, that retries must retain flaky evidence, and that result caching
should wait for an explicit hermetic input/capability model.

TOOL002-B is therefore READY. B remains deliberately mechanical: exact entry,
arguments, explicit capabilities, private streams, fresh Process/RootActor,
production execution and inert outcome. TestPlan parsing, assertions, manifests,
fixtures, resources, retry, timeout, sharding, watch, cache and reporting remain
outside B.

## Scale/distribution architecture checkpoint — SELECTED

`docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md` records the
future-scale architecture after the expanded comparative checkpoint.

The selected long-term boundary is:

```text
stable logical case attempt
        -> replaceable physical execution backend
        -> fresh semantic Protos Process / RootActor
        -> structured semantic + infrastructure evidence
```

The initial implementation remains intentionally smaller. TOOL002-B implements
only the local, test-neutral exact-entry fresh-Process mechanism. Later
OS-worker/remote backends, Run/Case/Variant/Attempt policy, capacity/resource
scheduling, sharding, affected analysis, caching and artifact infrastructure are
not silently pulled into B.

The checkpoint also records that remote/distributed execution cannot assume
exactly-once physical execution and that resource constraints eventually need
locality/scope as well as capacity. These are architecture constraints for future
layers, not new Core semantics.

## TOOL002-B closure

TOOL002-B closes the general local execution prerequisite needed by the Test
Tool without creating a test-specific host institution.

Published implementation:

- `ProtosFreshProcessExecutor` creates one fresh semantic Process/RootActor per
  invocation using the already-established standalone Process bootstrap;
- the request receives an already-selected Prelude/module-resolution environment,
  exact compiled entry, argument/environment snapshots, stream backends/Encodings
  and optional default Filesystem authority;
- `ProtosRootTaskExecution` owns the cooperative RootActor task dispatch required
  for real Future suspension/resume;
- `ProtosExecutionOutcome` carries only terminal COMPLETED/FAILED/CANCELLED data;
- Protos semantic failure is returned as FAILED outcome data rather than being
  reclassified as host/infrastructure failure;
- Process termination is requested before the executor returns;
- CLI standalone execution reuses RootActor terminal dispatch and preserves its
  existing CLI-specific error translation.

B adds no discovery, TestPlan, CaseId, expectation, assertion, fixture, scheduler,
resource, timeout, OS-worker, remote-backend, retry, cache or reporting policy.

TOOL002-C is therefore READY.

## TOOL002-C closure

TOOL002-C publishes the local sequential captured-execution layer needed before
manifest migration:

- `ProtosCapturedProcessExecution` supplies private byte-backed stdin plus
  independent stdout/stderr capture;
- it delegates semantic execution and Process lifecycle entirely to the closed
  TOOL002-B fresh-Process mechanism;
- capture buffers are detached defensively from request/result callers;
- a real `.protos` tooling fixture proves one exact case runs with independent
  stdout/stderr and a normal semantic result;
- a failure-path focal proves output committed before a Protos Error remains in
  that case's private capture;
- no TestPlan, expectation, assertion, CaseId, scheduler, retry, worker, remote,
  cache or reporter policy is added.

The C outcome remains host-inert. It does not leak arbitrary live child-Process
objects into the bundled Test Tool Process. TOOL002-D must audit the safe
Protos-side result-consumption boundary as part of migrating expectation policy.

TOOL002-D is therefore READY.

## TOOL002-D decomposition

The C -> D boundary audit found that the existing general manifest mixes simple
value/Error expectations with Future and callable/identity-sensitive cases.
Migrating all of that together would combine filesystem authority, TestPlan
representation, cross-Process value safety and several independent expectation
policies in one oversized change.

TOOL002-D therefore uses these publishable sub-slices:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D1 | CLOSED | Bootstrap-local general `execution(source)` facility for the Test Tool over TOOL002-C, returning a caller-local observation through a strict authority-free detached-value boundary. No manifest/test policy. Implementation version `0.2.174-SNAPSHOT`. |
| TOOL002-D2 | CLOSED | Grant the Test Tool one read-only tree-confined standard Filesystem rooted at the conformance corpus; bundled `Manifest.protos` uses bounded ordered readLine/Future.all windows to parse retained TSV rows into frozen CaseSpec/TestPlan tuples with named Protos accessors and validated path-based stable CaseIds. No case execution/expectation policy. Implementation version `0.2.182-SNAPSHOT`. |
| TOOL002-D3 | CLOSED | D3A, D3B and D3C are CLOSED; ordinary non-Future expectation migration is complete through D3C2C at `0.2.209-SNAPSHOT`. |
| TOOL002-D4 | CLOSED | `closure-error-parent-fresh` stays entirely inside one fresh child Process for two exact Closure invocations and fresh Error identity checking; Java direct conformance ownership is reduced to `future-*`. Implementation version `0.2.211-SNAPSHOT`. |

The `future-*` families (`future-integer`, `future-null`, `future-boolean`,
`future-error`, `future-error-parent`, `future-observation-error-identity`,
`future-cancelled`) remain deliberately covered by the later TOOL002-F slice.
This keeps the already-selected async/Future migration boundary meaningful.

### TOOL002-D1 closure

D1 publishes only general mechanism:

- `ProtosExactExecutionFacility` installs `execution` only in the explicitly
  granted initial tool module context;
- each call runs one exact source String through TOOL002-C with a fresh Process,
  private streams, empty args/environment and no default Filesystem;
- the returned frozen observation is caller-local and contains state, detached
  value/error data, and frozen captured stdout/stderr Bytes;
- `ProtosDetachedExecutionValue` never rematerializes capabilities and fails
  closed with `NonTransferableValue` for authority/execution values;
- frozen standard prelude objects may be shared, while copied identity-bearing
  data receives fresh destination identity;
- a Protos fixture owns the observable success/failure checks; Java tests cover
  the host boundary and fail-closed authority rule.

TOOL002-D2 is READY.

### TOOL002-D2 closure

D2 closes the corpus/planning prerequisite:

- the Test Tool gets a standard `filesystem` capability rooted only at
  `protos/tests/conformance`;
- a new general read-only tree backend performs secure relative traversal and
  nested opens without symlink following or write/mutation authority;
- bundled `Manifest.protos` owns manifest syntax/policy and canonical path
  validation;
- each retained row becomes a frozen internal CaseSpec tuple with stable `caseId == path`, consumed through named bundled-Protos accessors;
- the plan and its case Array are frozen inert Protos data;
- `Main.protos` constructs the plan on ordinary `protos test` startup;
- a Protos fixture verifies the first CaseSpec and reads its nested source
  through the standard Filesystem authority;
- no test case is executed and no expectation is interpreted yet.

TOOL002-D3 is READY.

### TOOL002-D3 decomposition

The post-D2 audit found three independent uncertainties inside the original
ordinary-expectation migration: complete source acquisition, single-case
expectation policy, and whole-plan sequential traversal. D3 is therefore
implemented as smaller publishable slices before the fixed/error-parent and
Float families:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3A1 | CLOSED | Bundled `Runner.readSource(spec, filesystem)` loads one complete UTF-8 case source through the D2 confined standard Filesystem/File surface. Ordered File reads are issued in bounded 16-read windows, exact bytes are accumulated before one UTF-8 decode, and the File is explicitly closed. No case execution or expectation policy. Implementation version `0.2.186-SNAPSHOT`. |
| TOOL002-D3A2 | CLOSED | `Runner.evaluateSimple(spec, source, executor)` interprets `boolean`, `null`, `integer`, and generic `error` entirely in bundled Protos. Normal mismatches return frozen `passed + observation` evidence; malformed/unsupported policy signals. Integer matching uses signed-decimal Protos parsing plus primitive `===` to preserve exact numeric family. Implementation version `0.2.189-SNAPSHOT`. |
| TOOL002-D3A3 | CLOSED | Compose TestPlan + source loader + simple evaluator through an ordered `Future.then` dependency chain built without suspending inside `Array.each`; skip unsupported kinds before source access, aggregate ordered frozen CaseRun evidence with balanced chunks, and integrate the supported subset into `Main.protos`. No reporting/parallel/exit-status policy. Implementation version `0.2.192-SNAPSHOT`. |
| TOOL002-D3B | CLOSED | D3B1 fixed-integer and D3B2A/B error-parent prerequisite/policy are published. The sequential runner owns both retained families without changing D1/D3A evidence boundaries. Implementation version `0.2.199-SNAPSHOT`. |
| TOOL002-D3C | CLOSED | D3C1 float-nan and D3C2 exact float-bits are published; final D3C2C sequential integration closes D3C at `0.2.209-SNAPSHOT`. |

D3A1 deliberately does not modify `Main.protos`: ordinary `protos test`
continues to construct the inert D2 TestPlan but does not execute it yet.
Likewise D3A1 does not call the D1 `execution` capability and owns no PASS/FAIL
or expectation-kind logic.

`Runner.readSource` preserves source acquisition semantics by accumulating File
bytes and decoding UTF-8 only after EOF. It does not decode each read chunk
independently, so a multi-byte UTF-8 scalar may cross a File.read boundary
without becoming an artificial codec error. File ordering supplies the byte
sequence; the tool does not introduce a second source resolver.

TOOL002-D3A2 is CLOSED.

D3A2 adds no Filesystem or TestPlan traversal. One already-supplied CaseSpec and
source are executed exactly once through the explicitly supplied D1 execution
capability. The bundled policy returns frozen inert evidence containing canonical
`passed` plus the complete detached observation. Synthetic Protos fixtures cover
all four supported kinds, normal mismatches, cross-family Integer rejection,
unsupported-kind fail-closed behavior and malformed expected-Integer policy.

TOOL002-D3A3 is CLOSED.

D3A3 is the first whole-plan execution composition. It runs only expectation
kinds already owned by D3A2, keeps unsupported rows inert and unread, preserves
manifest order through a Future dependency chain, and returns ordered frozen
CaseRun evidence. A 1024-case Protos stress fixture verifies exact-once ordered
stack-bounded traversal with no host Test runner policy. A second Protos fixture
uses the real confined Filesystem plus D1 fresh-Process execution and proves an
unsupported row is not read.

`Main.protos` now executes this supported subset through the same composition but
does not yet report individual cases or turn an inert mismatch into CLI exit
policy.

TOOL002-D3B is READY.

### TOOL002-D3B decomposition

The D3B audit found two independently meaningful policies, so publication is
split again:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3B1 | CLOSED | `fixed-integer` parses `FAMILY:value`, accepts exactly the eight Core fixed-width families, constructs the expected semantic value with the selected standard numeric factory, and matches through primitive `===`. Implementation version `0.2.193-SNAPSHOT`. |
| TOOL002-D3B2 | CLOSED | D3B2A published the general `Object.parent()`/prelude prerequisite and D3B2B publishes retained immediate-parent Error expectation policy in bundled Protos. Implementation version `0.2.199-SNAPSHOT`. |

D3B1 preserves the D3A3 result and sequencing contract. `error-parent` remains
skipped before source acquisition until D3B2B.

#### TOOL002-D3B2 decomposition after executable prerequisite audit

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3B2A | CLOSED | Implement the already-normative inherited `Object.parent()` reflection selector as one audited general Core representation bridge shared by ordinary lookup/reflection, restore the normative prelude `Object` binding through a temporary non-leaking root bootstrap seed, and cover ordinary/custom-parent, Error-taxonomy, represented values, root failure, and arity in Protos conformance. Implementation version `0.2.196-SNAPSHOT`. |
| TOOL002-D3B2B | CLOSED | Bundled Protos resolves the closed standard Error prototype taxonomy and matches a FAILED detached observation by exact immediate `error.parent() === expectedPrototype`; normal mismatch is inert evidence and malformed taxonomy policy fails closed. Implementation version `0.2.199-SNAPSHOT`. |

The failed pre-D3B2A policy attempt is not closure evidence. It demonstrated the
missing Core prerequisite and cleaned all patch-owned residue before publication.

TOOL002-D3B2B is CLOSED. D3B2 and D3B are CLOSED. TOOL002-D3C is READY.

### TOOL002-D3C decomposition

The D3C audit separates semantic NaN from exact raw-binary64 expectations:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3C1 | CLOSED | `float-nan` is matched entirely in bundled Protos by constructing the semantic Float NaN through ordinary `0.0 / 0.0` arithmetic and comparing the detached value through primitive `===`; payload must be `-`. Implementation version `0.2.201-SNAPSHOT`. |
| TOOL002-D3C2 | CLOSED | D3C2A parser, D3C2B exact binary64 mechanism and D3C2C sequential activation are published through `0.2.209-SNAPSHOT`. |

D3C1 deliberately leaves `float-bits` unsupported and unread by the sequential
runner. D3C remains IN_PROGRESS until D3C2 is published.

TOOL002-D3C2 is READY.

### TOOL002-D3C2 decomposition

The exact binary64 work is split into three independently publishable steps:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3C2A | CLOSED | Parse exactly 16 hex digits to an unbounded Integer raw pattern in bundled Protos; `float-bits` remains unsupported by the runner. Implementation version `0.2.205-SNAPSHOT`. |
| TOOL002-D3C2B | CLOSED | Bundled Protos reconstructs every portable non-NaN binary64 pattern exactly from D3C2A raw Integer fields using exact Float(Integer) significands plus bounded power-of-two scaling, applies sign through ordinary Float.negated(), and compares with primitive `===`. NaN raw patterns fail closed. Implementation version `0.2.208-SNAPSHOT`. |
| TOOL002-D3C2C | CLOSED | Activate `float-bits` in bundled Runner policy, evaluate exact portable non-NaN binary64 through D3C2A/B, migrate all historical unsupported sentinels to TOOL002-F `future-integer`, and preserve D3C2A/B mechanism coverage after activation. Implementation version `0.2.209-SNAPSHOT`. |

TOOL002-D3C2B and D3C2C are CLOSED. D3C2, D3C and D3 are CLOSED. TOOL002-D4 is READY.

### TOOL002-D4 closure

D4 closes the final non-Future identity-sensitive migration and the parent
TOOL002-D boundary:

- `closure-error-parent-fresh` is selected by the bundled runner without adding
  Closure transfer to D1;
- the retained source is evaluated once inside a private child-Process envelope;
- that exact candidate is invoked twice through ordinary `Error.handle`;
- both Error occurrences remain in the child identity domain while immediate
  standard parent and distinct identity are checked;
- only canonical Boolean evidence crosses the detached observation boundary;
- malformed expected Error prototype policy fails closed before source-name
  emission;
- a corpus-level Protos ownership fixture executes every D-owned main-manifest
  row and proves that only the deferred `future-*` families remain skipped;
- the legacy Java conformance harness directly executes only those `future-*`
  rows, eliminating duplicate Java ownership for D-migrated expectation policy.

Implementation version: `0.2.211-SNAPSHOT`.

TOOL002-D is CLOSED.

TOOL002-E is READY.

## Closure rule

TOOL002 closes only after all slices required for the selected initial Test Tool
outcome are implemented, validated, and published. Later optional hard-isolation
work does not block closure unless it is explicitly promoted into the parent
scope before closure.
