# Protos Test Tool Architecture Audit

Status: selected architecture; non-normative; implementation tracked by TOOL002

Related architecture and project material:

- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/design/PACKAGE_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md`
- `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`
- `docs/design/STANDARD_LIBRARY_IDEAS.md`
- `protos/tests/conformance/README.md`
- `spec/concurrency/ACTORS.md`
- `spec/concurrency/PARALLEL_EXECUTION.md`
- `spec/io/PROCESS_IO.md`
- `spec/semantics/MODULES.md`
- `spec/semantics/ERRORS.md`

This document records the selected architecture for `protos test`. It remains
deliberately non-normative and does not add Core syntax or change the language
specification. The implementation has now been promoted to project work item
`TOOL002`; promotion commits the project to the selected initial architecture and
sequencing, while the decisions explicitly listed as open below remain open.

The goal is to move Protos-language test orchestration out of Java/JUnit while
retaining Java tests for Java/runtime/host implementation behavior.

The selected architectural direction is:

> **Java validates the Java/runtime implementation first; a bundled test tool
> written primarily in Protos then runs Protos-language tests through ordinary
> Protos execution semantics.**

The package tool is the primary architectural precedent: policy and tool behavior
belong in Protos while the host retains only irreducible bootstrap and execution
mechanisms.

## Post-A comparative checkpoint

The required expanded post-TOOL002-A comparison is recorded in
`docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md`. That checkpoint retains the fresh
semantic Process isolation boundary while adding two architectural refinements
before TOOL002-B: an inert TestPlan/stable-case planning boundary above the
general executor, and future scheduler resource accounting based on capacities
rather than Boolean groups alone. It also confirms that arbitrary hard timeout
requires a separately owned physical worker boundary and is not part of
TOOL002-B.

## Scale and distribution architecture checkpoint

The follow-on scale/distribution architecture is selected in
`docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`.

That checkpoint keeps the ordinary fresh semantic Process/RootActor boundary but
makes future physical scale explicit:

```text
logical TestPlan / CaseAttempt
        |
        v
replaceable execution backend
        |
        +-- local same-runtime backend
        +-- future amortized OS-worker backend
        `-- future remote backend
        |
        v
fresh semantic Protos Process
```

The Test Tool must distinguish logical run/case/variant/attempt identity from
worker placement and must keep semantic Protos outcomes separate from
infrastructure outcomes such as worker loss or unavailable capacity. Distributed
execution does not assume exactly-once physical execution.

Future resource scheduling is further refined from capacity alone to capacity
plus access/locality scope so a host-local port does not serialize an entire
cluster while a truly global external resource can. TestPlan data must remain
inert and capable of serialization; implementations may stream/page it at very
large scale.

TOOL002-B remains local and mechanical. It does not implement the TestPlan,
identity policy, scheduler, resources, OS workers or remote execution; it must
only avoid baking physical placement or test-specific policy into the general
fresh-Process executor.

## Current repository problem

Many tests already express the behavior under test as ordinary `.protos` source,
but Java/JUnit still owns the runner mechanics and expectation interpretation.
Examples include the general conformance harness and package-tool TOML
conformance harness. Java currently reads manifests, creates execution state,
loads `.protos` source, interprets expectation kinds such as `boolean`, `integer`,
`error`, and `future-*`, advances pending work, and decides pass/fail.

That structure keeps Protos test cases portable, but Java remains the test runner.
The intended evolution is to preserve the Protos-owned corpus while moving test
policy, orchestration, expectations, filtering, reporting, and eventually richer
test helpers into ordinary bundled Protos tooling.

## Prior-art comparison

The audit compared several established test architectures. The comparison is
about design boundaries rather than API popularity.

### Go

`go test` is deeply integrated into the toolchain. Test binaries are generated
per package and tests can opt into intra-package parallelism. This provides useful
precedent for toolchain-managed isolation and execution, but it also places more
testing convention and generation logic inside the language toolchain than
Protos currently needs.

Useful lesson for Protos:

- the toolchain may own mechanical execution/isolation without owning test
  semantics;
- parallel execution should be bounded and reproducible;
- a test runner does not require an operating-system process per individual test.

Rejected transfer:

- compiler/test-specific source conventions or generated test entry semantics are
  unnecessary when ordinary Protos programs plus manifests already express the
  corpus.

### Rust

Rust's built-in test harness uses compiler-recognized `#[test]` items and
`rustc --test`/`libtest`, with tests normally running on multiple threads inside a
test executable.

Useful lesson for Protos:

- bounded parallel execution and filtering/reporting belong in tooling;
- integration targets can provide stronger isolation than individual test
  functions.

Rejected transfer:

- no parser/compiler-recognized test attribute, test keyword, special test item,
  or compiler test mode is justified for Protos at this stage.

### Python / pytest

Python's standard `unittest` discovery and most pytest behavior are implemented
in the language rather than in the interpreter. `pytest-xdist` adds process-worker
parallelism when stronger isolation or distribution is useful.

Useful lesson for Protos:

- discovery, suites, expectations, filtering, fixtures, and reporting can remain
  ordinary language-level tooling;
- stronger operating-system isolation can be layered later using amortized
  workers instead of forcing every test into a separate process.

### Ruby / Minitest

Minitest demonstrates that a useful test framework can be implemented with very
little privileged runtime knowledge and can reuse ordinary language mechanisms.

Useful lesson for Protos:

- assertions/helpers should be library/tooling behavior, not Core syntax or
  privileged runtime objects.

### Elixir / ExUnit

ExUnit is the closest conceptual prior art for Protos concurrency. Tests run in
isolated BEAM processes, modules can opt into concurrent execution, and resource
or grouping constraints can serialize cases that must not overlap.

Useful lesson for Protos:

- testing can use the language's native isolation/concurrency model rather than a
  parallel testing model bolted on beside it;
- per-test isolation and bounded concurrent scheduling compose well;
- resource-group serialization is preferable to disabling suite-wide
  parallelism.

Protos can use an even stronger semantic boundary than one Actor-like process:
its own `Process` already groups one RootActor, hosted Actors, lifecycle/failure
state, bootstrap snapshots, streams, and explicit capabilities.

### Node and Deno

Modern Node and Deno runners show the value of parallel test-module execution,
per-test/module output isolation, and explicit authority/permission boundaries.
Node commonly obtains strong isolation with child processes; Deno also exposes a
permission-oriented model.

Useful lesson for Protos:

- capture test stdout/stderr independently and report it deterministically;
- test authority should be explicit and minimal;
- operating-system workers are a valid later hard-isolation layer.

Rejected transfer:

- Protos should not make testing a new runtime-global institution when the
  bundled-tool model is sufficient.

## Selected architecture

Use four conceptual layers:

```text
A. Java/runtime implementation tests
B. bundled Protos test tool
C. general fresh-Process execution mechanism
D. Protos test corpus
```

### A. Java/runtime implementation tests

Java/JUnit remains appropriate when the subject under test is specifically Java
or host/runtime implementation machinery, for example:

- parser implementation details that cannot be expressed as observable Protos
  behavior;
- Truffle lowering/execution machinery;
- representation bridges;
- host backends;
- bootstrap implementation invariants;
- native-boundary guards;
- scheduler internals where the Java mechanism itself is the subject.

These tests run first.

### B. Bundled Protos test tool

The test tool should be distributed like the package tool and launched through
one public CLI:

```text
protos test
```

Conceptually its source belongs under:

```text
protos/tools/test/
    Main.protos
    Manifest.protos
    Runner.protos
    Expectations.protos
    Reporter.protos
```

The exact module split is not fixed by this audit.

The Protos tool should own:

- manifest parsing and inert TestPlan construction;
- stable case identity assignment before physical scheduling;
- suite/case selection;
- filtering;
- expectation interpretation;
- assertion/helper policy;
- expected-Error handling policy;
- result aggregation;
- deterministic reporting;
- exit status policy;
- test-order randomization/seed policy;
- parallel scheduling policy;
- resource/group constraints.

Java must not become the owner of concepts such as `test`, `suite`, `assert`,
`expected error`, tags, filters, or reporting merely because the current runtime
is implemented in Java.

### B -> C planning boundary: inert TestPlan

The post-A comparative audit makes suite planning explicit. Current manifests and
any future discovery mechanism should produce stable CaseSpec/CaseId records and
one inert TestPlan before physical scheduling. Filtering, seeded order, future
sharding, resource accounting and retry policy consume that plan. The exact
representation is ordinary Test Tool policy and remains open.

The general executor below does not interpret CaseIds, expectations, tags,
fixtures, resources, retries or reporting. It receives only an exact mechanical
execution request and returns an inert outcome.

### C. General fresh-Process execution mechanism

Do not introduce a Java `TestExecutor`, `TestRuntime`, privileged `Test` object,
or test-specific intrinsic.

Instead, expose/reuse a general host mechanism capable of mechanically executing
one exact Protos entry in a fresh semantic Process:

```text
exact execution plan / module resolver
        +
entry source or module
        +
application arguments
        +
explicit capabilities
        +
private standard streams
        |
        v
fresh Protos Process
        |
        v
fresh RootActor
        |
        v
execution
        |
        v
inert ExecutionOutcome
```

This mechanism should be useful to `protos run`, `protos test`, and potentially
future build/tooling flows rather than being a testing pet.

`PACKAGE_TOOL_ARCHITECTURE.md` already anticipates a future `protos test` using
package-tool read-only preflight to obtain an exact `PackageExecutionPlan`; the
test architecture should compose with that boundary instead of defining a second
package/module resolution system.

### D. Protos test corpus

Existing `.protos` tests remain valuable and should not be rewritten merely to
complete the architectural migration.

The current manifest expectation model may initially remain intact, for example:

```text
case.protos    boolean          true
case.protos    integer          42
case.protos    error            -
case.protos    future-integer   10
```

The first migration should move interpretation of those rows from Java to Protos.
A later design may introduce ordinary Protos assertion/helper modules or
self-asserting tests, but that is a separate API decision.

The initial manifest migration should therefore compile existing rows to an
inert TestPlan with stable case identity rather than dispatching directly from
manifest parsing. Future discovery may coexist, but it must produce the same
planning shape instead of creating another executor path.

### TOOL002-D3A2 simple expectation result boundary

D3A2 selects the first single-case expectation-policy result shape without
introducing Java-owned test semantics. `Runner.evaluateSimple(spec, source,
executor)` supports exactly `boolean`, `null`, `integer`, and generic `error`.

A normal semantic mismatch is data, not a Test Tool control failure. A2 returns
a frozen ordinary-Protos tuple consumed through named accessors:

```text
SimpleCaseResult
    passed       canonical Boolean
    observation  exact D1 detached observation
```

The retained observation keeps terminal state, detached value/Error and captured
stdout/stderr available to later aggregation/reporting. Unsupported expectation
kinds and malformed expected-value syntax remain Test Tool policy errors and
signal rather than being mislabeled as test failures.

The `integer` policy parses the manifest's signed decimal in Protos and compares
the detached result with primitive `===`. Numeric semantic identity includes the
numeric family, so an equal-magnitude Float or fixed-width integer does not
satisfy an ordinary unbounded Integer expectation. This preserves the Java
harness's former `ProtosIntegerValue` family check without moving that family
policy into a new Java Test abstraction.

D3A2 owns no Filesystem access, source acquisition, TestPlan traversal,
scheduling, reporting or `Main.protos` integration. D3A3 composes those already
separated boundaries.

### TOOL002-D3A3 sequential supported-case composition

D3A3 composes the already-separated D2 TestPlan, D3A1 source acquisition and
D3A2 simple expectation policy without introducing a second runner universe.

`Runner.runSimpleWithLoader(plan, sourceLoader, executor)` iterates the inert
CaseSpec Array only to build a dependency chain. An already-resolved
`Future.all()` result seeds that chain. For each CaseSpec whose expectation is
owned by D3A2, one `Future.then` continuation is attached to the prior selected
case. The continuation loads exactly that case source, performs exactly one D1
execution through D3A2 and records the resulting evidence. Therefore:

- selected cases begin in TestPlan order;
- a later selected case cannot start before the prior selected case resolves;
- `Array.each` does not suspend inside its callback;
- the number of live Protos call frames does not grow linearly with case count;
- each actual test still runs in the fresh semantic Process supplied by D1/B/C.

Unsupported expectation kinds are skipped before source acquisition. D3A3 does
not reinterpret, preflight or partially execute them; D3B, D3C, D4 and F retain
their ownership. Normal D3A2 mismatches are appended as inert `passed=false`
evidence and do not stop later cases. A source/policy/control failure fails the
Future chain and therefore fails closed instead of becoming a test mismatch.

Per-case run evidence is an ordered frozen Array of frozen tuples:

```text
CaseRun
    CaseSpec
    SimpleCaseResult
```

The final frozen run tuple exposes named accessors for ordered case results,
selected count, passed count and skipped count. Result accumulation uses the same
balanced-chunk strategy established by D2, avoiding repeated whole-result Array
copying.

`Main.protos` now invokes `Runner.runSimple(plan, filesystem, execution)` so the
public `protos test` command executes the currently supported D3A subset. It
retains the existing bootstrap stdout. D3A3 deliberately does not add per-case
reporting, failure rendering, CLI filtering, exit-status-on-mismatch policy or
parallel scheduling. Those remain later Test Tool responsibilities; TOOL002-H
still owns bounded outer parallel execution and deterministic reporting.

### TOOL002-D3B1 fixed-integer expectation boundary

D3B is subdivided because fixed-width family/value matching and Error-parent
taxonomy matching are independent policy surfaces. D3B1 owns only
`fixed-integer`; D3B2 remains responsible for `error-parent`.

The retained manifest spelling is `FAMILY:value`. D3B1 parses that spelling in
bundled Protos, accepts exactly the eight Core fixed-width family names, parses
the signed decimal payload through the existing D3A2 Integer parser, and uses
the selected standard numeric conversion factory to construct the expected
fixed-width semantic value.

Matching uses primitive `===` against the detached D1 observation. Core numeric
semantic identity includes both numeric family and exact value, so the rule
rejects an ordinary Integer, another fixed-width family, or another value while
accepting a separately materialized value of the same family and value.

Malformed syntax, unknown family, invalid decimal, and out-of-range expected
values fail closed as Test Tool policy. D3B1 changes no execution, Filesystem,
TestPlan, result shape, sequencing, reporting, or Java-owned runtime mechanism.

### TOOL002-D3B2A parent-reflection prerequisite

The first D3B2 policy attempt exposed that the Test Tool had no language-level
way to observe an Error occurrence's immediate delegation parent: the normative
Core `Object.parent()` reflection selector was absent from the implementation.

D3B2 must not compensate with an `errorParent` field in the host observation, a
Test-only native Closure, a hidden Error-category tag, or ancestry matching.
Those would duplicate or weaken ordinary language reflection solely for testing.

D3B2 is therefore split again:

```text
D3B2A  implement Object.parent() and restore the normative prelude Object binding
D3B2B  consume that ordinary reflection from bundled Protos error-parent policy
```

D3B2A is a Core prerequisite, not Test semantics. D1 remains unchanged and
authority-free. D3B2B remains responsible for the retained immediate-parent
expectation and will not be published until D3B2A is available on `origin/main`.

### TOOL002-D3B2B error-parent expectation boundary

D3B2B consumes D3B2A through ordinary language semantics only. The retained
`error-parent` expected payload resolves against the closed Core v0.1 standard
Error prototype taxonomy. The bundled Test Tool introduces no host Error tag,
class lookup, hidden ancestry channel, or extension to the D1 detached
observation.

For one valid case, `evaluateSimple` requires:

```text
observation.state === "failed"
observation.value === null
observation.error !== null
observation.error.parent() === expectedStandardErrorPrototype
```

The final comparison is primitive semantic identity. It intentionally checks the
Error occurrence's **immediate** delegation parent, matching the retained
manifest contract and the normative standard-failure construction rule. Broader
delegation ancestry is not accepted: an occurrence parented by `EncodingError`
does not satisfy expected `IOError`.

A category mismatch is ordinary test evidence (`passed=false`) and therefore
does not abort the D3A3 sequential chain. An unknown expected standard Error
prototype is malformed Test Tool policy and signals fail-closed. D1 remains
authority-free: it reconstructs the Error occurrence while retaining frozen
standard Prelude prototype identity, and D3B2B observes that result through the
ordinary `parent()` reflection published by D3B2A.

D3B2B changes no Filesystem, Process, scheduling, reporting, result shape,
detached-value mechanics, or Java-owned Test taxonomy. D3B is CLOSED after D3B1
and D3B2B; D3C owns the remaining `float-bits` and `float-nan` policy.

### TOOL002-D3C1 semantic Float NaN expectation boundary

D3C is split because semantic NaN matching and exact raw-binary64 reconstruction
have different implementation risk. D3C1 owns only retained `float-nan`;
D3C2 remains responsible for `float-bits`.

Core v0.1 defines one semantic NaN value in the Float family even though an
implementation may use different IEEE NaN payload/sign representations. The
bundled Test Tool therefore constructs an expected semantic NaN through ordinary
standard Float arithmetic:

```text
0.0 / 0.0
```

and matches the detached case result with primitive semantic identity:

```text
observation.value === expectedNaN
```

This simultaneously requires the Float semantic family and the semantic NaN
value. An Integer, finite/infinite Float, or arbitrary identity-bearing object
cannot satisfy the expectation. D3C1 does not inspect host `double` bits, expose
NaN payload/sign state, add reflection, or extend the D1 detached observation.

The retained payload for `float-nan` must be exactly `-`; any other payload is
malformed Test Tool policy and fails closed. A valid case also requires a
COMPLETED observation with null Error.

D3C1 changes no Filesystem, Process, scheduling, result shape, reporting, runtime
numeric protocol, or Java-owned Test taxonomy. D3C2 remains the sole owner of
`float-bits` and must preserve exact binary64 including signed zero without
turning host raw-bit representation into a new general Core reflection surface.

### TOOL002-D3C2A exact float-bits payload parser

D3C2 is decomposed before any binary64 construction mechanism is selected.

D3C2A owns only the retained `float-bits` payload grammar already exercised by
the Java conformance harness: exactly sixteen hexadecimal digits representing one
unsigned 64-bit binary64 bit pattern. Bundled Protos parses those digits to an
ordinary unbounded Integer in the inclusive range `0..2^64-1`.

The parser accepts `0-9`, `a-f`, and `A-F`, rejects every other octet, and rejects
any payload whose encoded length is not exactly sixteen octets. Because each
accepted character is ASCII, octet count and character count coincide.

D3C2A deliberately does **not** add `float-bits` to `isSimpleExpectation` and
does not execute a `float-bits` case. It therefore commits no representation,
construction, comparison, reflection, runtime, or detached-observation decision
for binary64 values. D3C2B owns that next mechanism audit.

### TOOL002-D3C2B exact binary64 construction/comparison mechanism

D3C2B consumes the exact unsigned 64-bit Integer pattern produced by D3C2A but
still does not activate `float-bits` as a runner expectation. The mechanism is
implemented entirely in bundled Protos and adds no host bit-reflection primitive.

For a non-NaN binary64 pattern, bundled Protos decomposes the Integer pattern
arithmetically into sign, exponent and fraction fields using exact Integer
`div`/`mod`. It then reconstructs the semantic Float as follows:

- exponent `2047`, zero fraction -> positive infinity before sign application;
- exponent `0` -> fraction times `2^-1074`, including both signed zeros and all
  subnormals;
- exponent `1..2046` -> `(2^52 + fraction) * 2^(exponent - 1075)`.

The significand is at most `2^53-1`, so the standard `Float(Integer)` conversion
is exact before power-of-two scaling. Powers of two are constructed with
logarithmic exponentiation-by-squaring over ordinary Float arithmetic. Every
factor is itself exactly representable, the exponent range is bounded to
`-1074..971`, and the final product is exactly the encoded binary64 value. This
avoids one recursive frame per exponent step and introduces no raw-host-bit
surface.

The sign is applied through ordinary `Float.negated()`, preserving the existing
semantic distinction between `+0.0` and `-0.0`. Comparison uses primitive `===`.
For non-NaN Floats Core semantic identity is exact raw-binary64 identity, so the
same rule distinguishes signed zero and adjacent finite values while rejecting a
non-Float family.

Patterns with exponent `2047` and nonzero fraction are rejected as malformed
`float-bits` policy. Core deliberately collapses all semantic Float NaNs and does
not expose payload/sign bits; retained NaN cases belong to `float-nan`.

D3C2B changes no D1 detached observation, runtime numeric protocol, Filesystem,
Process, sequencing, result shape or reporting. D3C2C remains solely responsible
for adding `float-bits` to `isSimpleExpectation`, whole-plan selection and final
D3C/D3 closure.

### TOOL002-D3C2C float-bits sequential integration

D3C2C is the final D3 ordinary-expectation integration slice. It activates
`float-bits` in `Runner.isSimpleExpectation` and evaluates one completed
observation by parsing the retained 16-hex payload through D3C2A, reconstructing
the portable non-NaN binary64 value through D3C2B, and comparing the detached
value by primitive semantic identity.

The policy requires a COMPLETED observation with null Error. Exact identity
therefore preserves the Float semantic family and all portable non-NaN binary64
distinctions, including positive versus negative zero. A raw NaN payload remains
invalid under `float-bits`; semantic NaN expectations continue to use
`float-nan` and expose no payload/sign bits.

Activating the final D3 family invalidates historical tests that used
`float-bits` merely as a convenient unsupported sentinel. D3C2C migrates those
sentinels to `future-integer`, which remains owned by TOOL002-F, while the D3C2A
parser and D3C2B reconstruction tests are updated to assert that their mechanisms
remain available after activation.

No runtime, D1 detached-observation, Filesystem, Process, scheduling, reporting,
or CLI exit-policy surface changes. D3C2, D3C and D3 close here. D4 becomes
READY for the remaining non-Future identity-sensitive
`closure-error-parent-fresh` expectation and Java ownership reconciliation.

## Isolation audit

The normal isolation boundary should be **one fresh Protos Process per test
case**.

### Why not one shared Activation

A shared Activation directly shares execution context and observable mutable
state and therefore allows case-order contamination.

### Why not merely a fresh Activation in one Actor

Actor-local module cache, execution domain, pending work, Actor-local mutable
state, handlers/lifecycle interactions, and other Actor state can still couple
cases.

### Why not merely a new Actor

A fresh Actor gives strong mutable-state isolation but remains inside the same
Process failure/lifecycle/authority domain. Tests of Process, RootActor,
capability bootstrap, Actor creation, Process termination, standard streams, and
other process-level behavior would not start from a clean semantic root.

### Why Process is the natural boundary

A Protos `Process` is a semantic failure/execution domain, not an operating-system
process. One Process owns:

- exactly one RootActor;
- its hosted Actor incarnations;
- Process lifecycle/failure state;
- stable bootstrap argument/environment snapshots;
- independent standard-stream bindings and Encoding associations;
- an optional explicitly granted default Filesystem capability.

A new Process therefore provides the clean semantic root that a general language
test needs while allowing many Processes to coexist inside one host runtime.

The host may share immutable implementation artifacts such as compiled code,
metadata, frozen Core objects, and frozen prelude representation when such
physical sharing cannot become observable mutable Protos state. Consequently,
Process-per-test does **not** imply rebuilding all of Core for every test.

## Parallel execution audit

The normal test suite should be designed for **parallel execution by default**,
subject to a bounded job limit and explicit resource constraints.

Conceptually:

```text
protos test
    |
    | jobs = N
    +-- Process T1 -- RootActor -- test 1
    +-- Process T2 -- RootActor -- test 2
    +-- Process T3 -- RootActor -- test 3
    `-- ...
```

Each test Process remains free to exercise real Protos concurrency internally:

```text
Process T1
    +-- Futures / Actor-local tasks
    +-- Actors
    +-- ActorGroups
    `-- isolated `Closure.parallel(...)` work
```

The runner must not replace production concurrency with a serial test-only
scheduler merely to make tests easier to reason about.

### Do not use `Closure.parallel` as the test isolation mechanism

`Closure.parallel(...)` (P) is intentionally a CPU-parallel isolated-computation
boundary. P does not inherit Actor, Process, I/O, open-resource, or scheduler
authority, and values without a P-transfer contract fail the boundary.

General tests must be able to exercise Process, Filesystem, I/O, Actors,
ActorGroups, Futures, and P itself. Therefore P is something tests may use and
test, not the runner's sandbox mechanism.

### Bounded outer parallelism

The test tool introduces an outer layer of concurrency on top of concurrency that
may occur inside each test. Oversubscription can therefore come from:

```text
parallel test Processes
    x
Actors per test
    x
P work per test
```

The initial `jobs=auto` policy should be conservative and CPU-aware. A reasonable
starting point is approximately the host's available processor count, with
`--jobs 1` available for deterministic reduction/debugging. Exact defaults must
be validated by benchmark rather than standardized from intuition.

The runner should not expose internal scheduler/carrier objects as public test
semantics.

### Reproducible ordering

Parallel completion order must not become test semantics.

A future runner should support a reproducible suite-order seed so accidental
case-order dependencies can be exposed and replayed, conceptually:

```text
protos test --seed N
protos test --jobs 1 --seed N
```

Reporting order may be made deterministic independently from physical completion
order.

## Explicit resources and parallel safety

Fresh Process isolation removes shared mutable Protos state between cases, but it
cannot automatically isolate external resources such as writable host files,
databases, fixed ports, service accounts, simulators, hardware or other
explicitly shared capabilities.

Prefer private capabilities and case-private resources wherever practical. When
sharing is real, the post-A comparative audit generalizes the earlier Boolean
resource/group idea into **capacity accounting**:

```text
global execution capacity = N
case A requires 1 slot
case B requires 4 slots

resource db:integration capacity = 1
resource simulator capacity = 3
```

A named resource with capacity 1 behaves as a mutex; larger capacities behave as
semaphores/pools. This one model can express mutual exclusion, heavyweight cases
and scarce devices without creating separate scheduler institutions. Exact
declaration syntax and the initial `jobs=auto` formula remain open and require
later design/benchmark evidence.

TOOL002-B does not implement this scheduler policy.

## Output isolation

Every test Process should normally receive private captured stdout/stderr rather
than writing concurrently to the runner terminal.

The reporter can then implement policies such as:

```text
PASS -> suppress captured output by default
FAIL -> show captured stdout/stderr
verbose mode -> show successful-case output too
```

This keeps physical parallel completion from corrupting human-readable output and
requires no operating-system process per test because Process standard streams
are already independently provisioned host bindings.

## The hard-timeout problem remains open

The audit found one important unresolved boundary.

Core deliberately does not define arbitrary preemption of an indefinitely
running non-suspending Protos segment. A test that never completes and never
reaches a semantic suspension/scheduling point may monopolize its current Actor
and carrier indefinitely.

Process termination must not be implemented by unsafe host thread termination
merely for testing convenience.

Therefore a same-runtime Process-per-test runner cannot by itself guarantee a
hard kill of every possible infinite test.

A later hard-isolation design should evaluate **amortized operating-system
workers**, conceptually:

```text
controller
    +-- worker JVM 1 -> several fresh test Processes over time
    +-- worker JVM 2 -> several fresh test Processes over time
    `-- worker JVM N -> several fresh test Processes over time
```

If one non-preemptible test wedges a worker beyond an external deadline, the
controller can terminate/recreate that worker and continue the suite. This is
closer to pytest-xdist/worker-process isolation than to spawning one OS process
per test and preserves the low-cost Process-per-test path for normal cases.

This worker model is **not selected by this audit**. It remains an explicit open
design question to resolve only when hard timeout/crash isolation is promoted to
implementation work.

## Decisions closed by this audit

The initial audit plus the required post-A expanded comparison select:

```text
bundled `protos test` written primarily in Protos        YES
Java/JUnit retained for Java/runtime-specific tests      YES
Java tests execute before Protos tests                   YES
no testing syntax/parser/compiler special form           YES
no privileged global Test object                         YES
no Java-owned assertion/suite/expectation policy         YES
fresh Protos Process per ordinary test case              YES
fresh RootActor per ordinary test case                   YES
OS process per ordinary case                             NO
separate inert TestPlan before physical scheduling       YES
stable case identity before scheduling                   YES
future discovery feeds the same TestPlan boundary        YES
parallel test execution as the normal target             YES
bounded outer parallelism                                YES
capacity-based scheduler/resource model                  YES
private per-test stdout/stderr                            YES
explicit per-test capabilities                           YES
`Closure.parallel` as runner isolation                    NO
P/Actors/Groups/Futures usable inside tests               YES
preserve existing manifests during initial migration     YES
assertions may coexist with external expectations        YES
hard arbitrary same-runtime timeout guarantee             NO
initial silent retry / hidden flaky evidence              NO
initial test-result caching                               NO
```

## Decisions intentionally left open

```text
exact `protos test` CLI flags
exact current-manifest migration syntax/evolution
future discovery convention and scope
exact textual stable CaseId representation
concrete ordinary-Protos TestPlan/CaseSpec representation
assertion/helper library API
fixture/setup/cleanup API and scopes
filter/tag syntax
seed/randomization CLI and default policy
initial `jobs=auto` formula after benchmarks
per-case execution-capacity declaration syntax
named-resource capacity/pool declaration syntax
machine-readable report schema
hard timeout semantics and OS worker/controller architecture
retry/flaky policy and CLI
shard selection CLI and stable partition algorithm
watch-mode dependency graph/source
cache hermeticity and input/environment/capability fingerprint model
crash diagnostics for future physical workers
```

These open items must not be silently decided by TOOL002-B. The expanded
comparative checkpoint is complete and no longer blocks the general
fresh-Process mechanism.

## TOOL002-B fresh-Process execution closure

TOOL002-B publishes the first local backend mechanism without introducing test
policy:

```text
already-selected Prelude/module resolver + exact compiled entry
        |
        v
ProtosFreshProcessExecutor
        |
        v
fresh Protos Process / RootActor
        |
        v
ProtosRootTaskExecution
        |
        v
inert ProtosExecutionOutcome
```

The mechanism reuses `ProtosStandaloneProcessBootstrap` for Process-local
arguments, Environment, standard streams/Encodings and optional default
Filesystem authority. Each invocation owns one fresh Process and requests its
termination before returning. The terminal outcome contains no Process, Actor,
task, worker, retry, CaseId or reporter handle.

CLI standalone execution now consumes the same RootActor terminal-dispatch
mechanism and translates FAILED/CANCELLED outcomes into its existing diagnostics.
That reuse keeps the executor itself free of CLI or Test Tool policy.

TOOL002-B intentionally does not construct TestPlans, discover tests, interpret
expectations, allocate resources, schedule multiple cases, enforce hard timeout,
create OS/remote workers, retry, cache or report results. Those remain later
layers selected by the comparative and scale/distribution architecture.

## TOOL002-C single-case captured execution closure

TOOL002-C closes the next mechanical layer without moving test policy into Java:

```text
one exact already-compiled Protos entry
        |
        v
ProtosCapturedProcessExecution
        |
        +-- private stdin bytes
        +-- private stdout capture
        +-- private stderr capture
        |
        v
TOOL002-B ProtosFreshProcessExecutor
        |
        v
fresh Process / RootActor
        |
        v
inert semantic outcome + detached captured bytes
```

The C wrapper is still test-neutral. It knows nothing about manifests,
expectations, assertions, case identity, filtering, reporting, scheduling,
resources, retries or workers.

The initial private capture implementation is in-memory because C is strictly
sequential. This is not a commitment that future massively parallel execution
must retain all output in controller memory; the scale/distribution architecture
still permits bounded/artifact-backed capture at the later scheduler/reporting
layer.

C also deliberately does **not** expose arbitrary child-Process result objects
directly into the bundled tool Process. TOOL002-D is the first slice that needs
a Protos-side result/expectation consumption boundary, so that boundary must be
audited there rather than invented as an incidental host object leak in C.

## TOOL002-D result-consumption boundary

TOOL002-C deliberately stopped before exposing child-Process values to the
bundled tool. TOOL002-D begins by closing that boundary without weakening
Process isolation.

The selected initial mechanism is:

```text
bundled Test Tool Process
        |
        | bootstrap-local `execution(source)`
        v
TOOL002-C private captured execution
        |
        v
fresh child Process / RootActor
        |
        v
semantic outcome
        |
        v
strict authority-free detached snapshot
        |
        v
caller-local observation object
```

`execution` is a host-granted bundled-tool capability, not a Core/prelude
binding, intrinsic, service locator or test-specific global object. Its first
implementation accepts one already-selected source String and grants the child
empty arguments/environment, private UTF-8 streams and no Filesystem authority.

The returned observation has ordinary caller-local slots `state`, `value`,
`error`, `stdout` and `stderr`. No live Process, Actor, task, Future, Closure,
stream, Filesystem/File or other child authority may cross this boundary.
Non-transferable results fail with the existing `NonTransferableValue` category.

This is intentionally stricter than Actor transfer: ActorRef/GroupRef/Process
capabilities are **not** rematerialized for observation. The Test Tool needs
data, not delegated authority into a completed case.

TOOL002-D is decomposed because the current manifest contains substantially
different expectation families:

```text
D1  Protos-consumable exact execution + detached observation
D2  confined corpus read + manifest -> inert TestPlan / stable CaseId
D3  ordinary scalar/Error expectation interpretation in Protos
D4  remaining non-Future Closure/error-identity expectations
```

The existing `future-*` expectation families remain assigned to TOOL002-F, whose
purpose is specifically to preserve async/Future pending-work and terminal
outcome coverage. D does not silently absorb that later slice.

## TOOL002-D2 confined corpus and initial TestPlan closure

TOOL002-D2 establishes the planning input boundary without executing cases.

The Test Tool receives one explicit standard Filesystem capability whose
authority root is exactly `protos/tests/conformance`. The host mechanism is the
general `ProtosNioReadOnlyTreeFilesystemBackend`:

- authority is read-only and existing-file only;
- the root and each intermediate directory are pinned through
  `SecureDirectoryStream`;
- every traversal/open is relative and uses `NOFOLLOW_LINKS`;
- rooted, parent/non-normal, empty, host-multi-component and escaping paths fail;
- providers unable to preserve secure directory confinement fail closed.

No corpus-specific read primitive is introduced.

`protos/tools/test/Manifest.protos` owns TSV interpretation. It preserves the
standard `TextReader.readLine(65536)` framing/budget contract but issues reads in
bounded windows of 512 ordered Futures and awaits each window with `Future.all`.
This avoids retaining one Protos call frame per manifest row while keeping the
reader's specified LF/CR/CRLF behavior and ordered input domain. Malformed rows
are rejected, each path is validated as a canonical relative slash-separated
path, and D2 produces:

```text
TestPlan
    cases: Array(
        CaseSpec {
            caseId
            path
            expectation
            expected
        },
        ...
    )
```

Each CaseSpec tuple, the cases Array and the plan tuple are frozen through
ordinary rest-parameter capture semantics. Named bundled-Protos accessors preserve
the conceptual `caseId`, `path`, `expectation`, `expected` and `planCases` fields;
tuple positions remain a private D2 representation. D2 therefore introduces no
Java-owned CaseSpec/TestPlan class and needs no structural mutation operation merely
to make plan data inert. The initial `caseId` is exactly the validated canonical
manifest path. That identity is stable independently of later physical scheduling,
completion order, worker or retry identity.

D2 materializes the current plan because the retained corpus is finite and this
slice is sequential. Construction uses balanced Array chunks rather than copying
the complete growing plan once per row. Manifest I/O is likewise bounded by a
fixed 512-read window, so the parser no longer consumes one recursive Protos frame
per row; batch recursion grows only once per 512 rows. The selected scale
architecture still permits later paging/streaming for much larger suites without
changing CaseId or CaseSpec meaning.

D2 does **not** execute a CaseSpec, interpret an expectation, select retries,
schedule work or expose corpus Filesystem authority to child case Processes.
The D1 `execution(source)` child still receives no default Filesystem.

## Tracked implementation sequencing

`TOOL002` adopts the following cost-aware sequence. Preserve one coherent
repository state after every slice and re-audit the current `origin/main` before
each subsequent slice:

1. **Test tool bootstrap** — CLOSED by TOOL002-A.
2. **Post-A expanded comparative architecture checkpoint** — CLOSED by
   `docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md`; TOOL002-B is READY.
3. **Fresh-Process execution mechanism** — CLOSED by TOOL002-B with the local,
   test-neutral `ProtosFreshProcessExecutor` / `ProtosExecutionOutcome` boundary.
4. **Single-case sequential runner** — CLOSED by TOOL002-C with private stdin/stdout/stderr
   and detached capture over the general fresh-Process mechanism.
5. **Manifest/expectation migration** — IN_PROGRESS through TOOL002-D2; D1 safe execution observation and D2 confined corpus + inert TestPlan/CaseId are CLOSED. D3 ordinary expectations is READY; D4 remains dependent and `future-*` remains assigned to TOOL002-F.
6. **Package-tool fixture migration** — move Protos package-tool/TOML fixtures
   away from Java-owned runner logic.
7. **Async/Future coverage** — preserve current pending-work/terminal-outcome
   expectations through ordinary production execution semantics.
8. **Actor/Group coverage** — migrate scheduler-sensitive language tests without
   introducing a test-only concurrency model.
9. **Bounded parallel scheduler** — schedule independent CaseSpecs with explicit
   capacity accounting, private output capture, reproducible seeded plan order
   and deterministic reporting independent of completion order.
10. **Resource capacities/private capabilities** — add named capacity/pool
    constraints where real external-resource sharing requires them.
11. **CI/launcher integration** — validate Java implementation tests first and
    then invoke the Protos test tool for the Protos corpus.

Hard OS-worker containment, retries, sharding/distribution, watch/incremental
selection and result caching remain later explicitly promoted work unless a
future audit adds them to the parent scope. Do not merge implementation slices
merely to reach parallel execution sooner; isolation correctness and the
mechanism/policy boundary precede concurrency optimization.

## Intended final validation shape

The desired repository validation pipeline is conceptually:

```text
static/source governance
        |
        v
Java/runtime implementation tests
        |
        | PASS
        v
build runnable Protos toolchain
        |
        v
bundled `protos test`
        |
        v
Protos-language conformance/tool/regression corpus
        |
        v
license/publication checks
```

Java remains the implementation language of the current runtime where appropriate,
but it stops being the semantic owner of the Protos test corpus.
