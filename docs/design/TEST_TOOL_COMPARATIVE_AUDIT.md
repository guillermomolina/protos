# Protos Test Tool Expanded Comparative Architecture Audit

Status: completed post-TOOL002-A architecture checkpoint; non-normative

Work item: `TOOL002`
Checkpoint: required post-A comparative architecture audit
Historical repository checkpoint audited: `18f7e88e8bee8c2824b2bde4db35fb2a4a67cdf3`

Related Protos architecture and project records:

- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`
- `docs/project/TOOL002_TEST_TOOL.md`
- `docs/project/registries/IMPLEMENTATION_STATUS.md`
- `spec/io/PROCESS_IO.md`
- `spec/semantics/MODULES.md`
- `spec/concurrency/ACTORS.md`
- `spec/concurrency/PARALLEL_EXECUTION.md`
- `spec/semantics/ERRORS.md`

## Purpose

TOOL002-A intentionally proved only the exact bundled `protos test` bootstrap.
Before TOOL002-B creates the general fresh-Process execution mechanism, this
audit challenges the selected test-tool architecture against mature systems with
substantially different execution models.

The question is not which framework has the largest feature list. The question
is which architectural lessons survive translation into Protos while preserving
its existing language properties:

- testing policy should live primarily in ordinary Protos tooling;
- Java/host code should own mechanical host/runtime boundaries, not test
  institutions;
- a Protos `Process` is a semantic execution/failure domain, not an OS process;
- authority is explicit and capability-confined;
- Actors, Futures, ActorGroups and P remain production semantics inside tests;
- simple tests should not pay for distributed/hard-isolation machinery they do
  not use;
- future scaling should compose the same mechanisms rather than creating a
  second testing universe.

This checkpoint is documentation/design work. It changes no Core semantics,
runtime behavior, implementation version, CLI spelling, manifest syntax, or
public assertion API.

## Systems compared

The audit reviewed the following families because together they exercise the
important design alternatives rather than merely repeating one style of unit
testing.

| Ecosystem / system | Architectural lesson examined |
|---|---|
| Python `pytest` / `unittest` / `pytest-xdist` | language-level policy, fixtures, discovery, output capture, worker distribution and worker lifecycle |
| Rust `cargo test` / `libtest` / `cargo-nextest` | compiler-integrated tests versus external runner, process-per-test, retries, timeout, sharding and resource groups |
| Go `go test` | package-oriented integration, caching, filtering, shuffle/seed, fuzzing and toolchain ownership |
| Java JUnit + Maven Surefire + Gradle Test | framework/runner split, forked JVM workers, parallelism, resource locks and rerun policy |
| Node built-in test runner / Vitest | process/worker pools, file isolation, watch mode, sharding and randomization |
| .NET test platform / xUnit/NUnit/MSTest | platform/framework separation, host processes, parallel modules and crash/hang diagnostics |
| Elixir ExUnit / Erlang Common Test | native lightweight-process isolation, async cases, groups, setup and timetraps |
| CTest / GoogleTest | executable-level isolation, resource allocation, fixtures, timeout, repeat/shuffle and sharding |
| Bazel test execution | hermetic execution contracts, declared inputs, private outputs, sharding and remote execution |
| Buck2 test runner | build-system/test-runner separation, local resource pools and remote/distributed execution |
| Ruby RSpec | randomized order, reproducible seeds and order-dependency minimization |

The official references used for this checkpoint are listed at the end of this
document.

## 1. Isolation: retain fresh Protos Process per ordinary test

The existing Protos choice survives the comparison.

Several mature systems obtain their strongest ordinary isolation from OS
processes or forked worker processes. `cargo-nextest` deliberately runs each test
in its own process because an OS process gives the runner lifecycle ownership,
output capture, crash accounting, retry and hard-timeout termination. Node's
built-in runner also defaults to process isolation at test-file granularity.
Surefire/Gradle can fork JVMs, CTest naturally executes external test processes,
and .NET test platforms host tests in separate test-host processes.

That does **not** imply that Protos should map one ordinary test case to one OS
process. Protos already has a stronger language-semantic boundary than a function,
activation or Actor: a fresh Protos Process provides a fresh RootActor,
Process-local lifecycle/failure domain, bootstrap argument/environment snapshot,
standard-stream bindings and explicitly provisioned capabilities. Multiple such
Processes can coexist in one host runtime without making physical placement part
of the language model.

ExUnit/Common Test reinforce the general principle: useful test isolation can
reuse the language/runtime's native isolation unit rather than forcing a foreign
parallel-testing abstraction on top. Protos should therefore keep:

```text
ordinary test case
    -> fresh semantic Protos Process
    -> fresh RootActor
    -> production Actor/Future/Group/P behavior inside the case
```

The important distinction is now sharper:

```text
semantic isolation     fresh Protos Process       normal path
hard physical isolation optional OS worker layer  later policy
```

TOOL002-B must implement only the first line.

## 2. Planning and execution must be separate

The original architecture had an exact execution plan for one fresh Process but
did not make the **suite planning boundary** explicit enough.

The comparison shows why that boundary matters. `pytest-xdist` workers perform
collection themselves and must agree on the collected suite; this is flexible,
but it also creates repeated collection/setup costs and makes worker-local
session state important. In contrast, nextest, Bazel, CTest and build-system
runners benefit from knowing stable units of work before dispatch: they can
filter, reorder, allocate resources, retry, shard or distribute those units
without teaching the low-level executor what a test means.

Protos should therefore introduce a conceptual, inert planning artifact:

```text
manifest / future discovery
        |
        v
stable CaseSpec / CaseId records
        |
        v
inert TestPlan
        |
        +--> filter
        +--> seeded order
        +--> shard
        +--> resource accounting
        +--> retry policy (future)
        |
        v
general exact-entry fresh-Process executor
```

`TestPlan`, `CaseSpec` and `CaseId` are architecture concepts for ordinary bundled
tool data. This audit does **not** make them Core prototypes, Java classes that
own test policy, reserved syntax, fixed public API names, or a new manifest
format.

The initial existing manifest should eventually compile to this plan. If Protos
later adds recursive discovery, package-native discovery or generated tests,
those mechanisms should compile to the same planning representation rather than
creating another execution path.

### Stable case identity

A mature runner needs stable case identity before physical scheduling. Stable
IDs support:

- deterministic filtering;
- reproducible seeded ordering;
- failure reruns;
- sharding;
- machine-readable reports;
- future history/cache lookup;
- order-dependency diagnostics.

The exact textual spelling remains open. Physical completion order must never be
used as identity.

## 3. The executor must remain mechanically test-neutral

The comparison strengthens, rather than weakens, the earlier prohibition on a
Java `TestExecutor` institution.

TOOL002-B should expose/reuse a general mechanism with roughly this information:

```text
exact module/execution plan
entry
application arguments
explicit capabilities
private stdin/stdout/stderr + Encodings
        |
        v
fresh Protos Process + RootActor
        |
        v
production execution to a terminal semantic outcome
        |
        v
inert ExecutionOutcome
```

The mechanism may be used later by `protos run`, `protos test`, benchmark tooling
or other exact-entry flows. It must not know:

- case IDs;
- tags or filters;
- expected values/errors;
- assertions;
- fixture/setup policy;
- test resources or groups;
- retries or flake classification;
- reporting format;
- sharding;
- watch mode;
- test-result caching.

That separation is the main architectural protection obtained from this audit.

## 4. Discovery and manifests: one plan, more than one producer

The existing manifest corpus should remain the first migration target. Rewriting
working tests solely to obtain a fashionable discovery convention would add risk
without improving semantics.

Pytest, Go, Rust, Node and JUnit all demonstrate useful convention/discovery
models, but many of those conventions depend on language-specific declarations,
attributes, filenames, reflection or compiler support. Protos should not add a
`test` keyword, annotation, compiler mode or privileged reflection convention to
copy them.

Selected direction:

- preserve current explicit manifests during migration;
- manifests are **input policy**, not executor semantics;
- compile manifests to the inert TestPlan;
- future discovery may coexist and must produce the same CaseSpec/TestPlan shape;
- do not decide recursive discovery spelling in TOOL002-B or TOOL002-C.

This gives Protos pytest-like extensibility without adopting pytest's requirement
that every distributed worker independently rediscover the suite.

## 5. Assertions and external expectations may coexist

The current conformance corpus uses external expectation rows such as Boolean,
Integer, Error and Future-result expectations. JUnit, pytest, ExUnit, xUnit,
GoogleTest and RSpec demonstrate the usability of in-language assertions, while
Bazel/CTest-style execution also shows that process-level pass/fail contracts can
remain useful.

There is no need to force one model to eliminate the other.

Selected direction:

- migrate the existing manifest/expectation model first;
- later ordinary Protos assertion/helper modules may provide self-asserting tests;
- no assertion syntax, compiler transformation, privileged `Test` global or Core
  intrinsic is justified;
- both styles should ultimately reduce to ordinary case outcomes consumed by the
  same tool-level reporting pipeline.

The assertion API remains deliberately open.

## 6. Fixtures: structured tool policy, not hidden authority

Pytest fixture scopes, JUnit lifecycle callbacks, ExUnit setup contexts and CTest
fixtures all show the value of reusable setup/cleanup. They also show a common
risk: broad fixture scope can introduce hidden shared mutable state and make
parallel scheduling harder.

For Protos, fixture policy should be added only after the single-case and
manifest migration paths are correct.

Future fixture design should preserve these constraints:

- setup/teardown is ordinary Protos tool/library behavior;
- capabilities supplied to a test are explicit;
- case-private resources are preferred to suite-global mutable fixtures;
- broader shared fixtures must declare the scheduling/resource consequence;
- asynchronous acquisition/cleanup must preserve normal Future and resource
  lifecycle semantics rather than inventing test-only suspension;
- no Java-owned fixture graph is required.

No fixture API is selected by this checkpoint.

## 7. Parallel scheduling: move from a job count to capacity accounting

The original architecture selected bounded outer parallelism and resource-group
serialization. The comparison reveals that a Boolean group lock is too narrow as
the eventual model.

Nextest test groups can act as mutexes or semaphores. JUnit resource locks and
isolated tests serialize conflicting work. CTest exposes processors,
`RESOURCE_LOCK` and `RESOURCE_GROUPS`; Buck2 exposes named local resource pools.
These systems converge on the concept that scheduling consumes **capacity**.

Selected conceptual model:

```text
global execution capacity = N

case A consumes 1 execution slot
case B consumes 4 execution slots

resource db:integration capacity = 1
resource simulator capacity = 3
resource gpu capacity = 2
```

A named resource with capacity 1 naturally behaves as a mutex. A capacity larger
than 1 behaves as a semaphore/pool. This avoids one special mechanism for locks,
another for heavy tests and another for scarce devices.

The exact syntax and default `jobs=auto` formula remain open. The scheduler must
also account for the fact that one test Process may itself create Actors and P
work; benchmark evidence should set conservative defaults rather than intuition.

Private capabilities remain preferable to shared resources wherever practical.

## 8. Reproducibility: plan order, execution order and report order are distinct

Go, ExUnit, RSpec, Node and GoogleTest all provide seeded shuffle/randomization
mechanisms. Mature runners also separate physical parallel completion from stable
reporting.

Protos should explicitly distinguish:

```text
logical TestPlan order
physical dispatch/completion order
human/machine report order
```

A future seeded permutation acts on the plan and records the seed. `--jobs 1`
with the same plan/seed should be available as a deterministic reduction path.
The exact CLI flags are not selected here.

Stable CaseIds are the prerequisite for reproducible sharding and reruns.

## 9. Output: private capture remains the default

Pytest, nextest, Node and most build/test runners capture output to prevent
parallel cases from corrupting the terminal and to surface diagnostics on
failure.

The existing Protos direction remains selected:

- every ordinary test Process receives private stdout/stderr bindings;
- successful output may be suppressed by default;
- failed-case output can be attached to that case deterministically;
- machine reporting consumes structured outcomes rather than parsing human text;
- output capture is execution mechanism; display policy belongs to the Protos
  test tool.

TOOL002-B may provide private streams and inert captured data, but it must not
select human reporting policy.

## 10. Hard timeout and crash containment require a physical boundary

This audit strongly confirms the earlier warning.

Nextest, CTest, Node, Surefire/Gradle, Bazel and .NET test hosts can kill or
replace a hung test because the runner owns an OS-process/worker boundary. A
fresh semantic Protos Process inside one runtime does not magically make an
indefinitely non-suspending segment safely preemptible.

Therefore:

- TOOL002-B must not promise arbitrary hard timeout;
- never implement hard timeout with unsafe host thread termination;
- normal tests should still use low-cost fresh semantic Processes;
- if guaranteed recovery from non-preemptible hangs or host crashes becomes a
  requirement, add a later **amortized OS worker/controller layer**;
- one OS worker may execute several fresh Protos Processes over its lifetime;
- the controller may kill/recreate a wedged worker and retain already completed
  case outcomes.

This is a physical containment layer around the semantic Process model, not a
replacement for it.

## 11. Retries and flaky tests: evidence must not disappear

Nextest, Surefire and other runners support reruns. Retries can improve developer
workflow but can also hide nondeterminism when a pass-after-failure is reported as
a clean pass.

No initial retry feature is selected. If introduced later:

- each attempt must remain observable in the structured result;
- a pass after earlier failure is classified as flaky/unstable evidence, not an
  ordinary first-pass success;
- retry policy belongs to the scheduler/tool, not the executor;
- deterministic semantic failures should not be silently retried by default.

This keeps retries from becoming a correctness shortcut.

## 12. Result caching: defer until hermetic inputs exist

Go test caching and Bazel demonstrate the power of reusing test results when the
runner can identify all relevant inputs. Bazel also demonstrates the discipline
required: hermeticity, declared dependencies, private temporary/output areas and
stable execution contracts.

Protos does not yet have an equivalent complete test-input/hermeticity model.
Caching test results now would risk returning stale success after hidden external
state changed.

Selected direction:

- no initial test-result cache;
- stable CaseIds/TestPlans are useful prerequisites but are not enough;
- revisit caching only after an explicit input/environment/capability fingerprint
  model exists;
- a case with undeclared external authority must not be treated as cacheable merely
  because its source file is unchanged.

## 13. Sharding and distribution: planning concern, not Process semantics

Nextest, Bazel, GoogleTest and Node demonstrate that stable deterministic sharding
is useful for large suites and CI. Buck2/Bazel show that a later executor may also
place work remotely when inputs/resources are representable.

Protos should not build distribution into TOOL002-B. Instead:

- stable CaseIds and the inert TestPlan make deterministic sharding possible;
- a shard selects CaseSpecs before execution;
- each selected case still receives the same fresh semantic Process contract;
- local, OS-worker and future remote execution must preserve the same observable
  case semantics and explicit authority;
- exact shard algorithm/CLI remains future policy.

The language does not change universes when the suite grows.

## 14. Watch/incremental mode: dependency layer above planning

Node/Vitest and build tools demonstrate the usefulness of watching changed inputs
and rerunning affected tests. This depends on a dependency graph, not merely a
test executor.

A future watch mode should therefore:

```text
changed inputs
    -> affected stable CaseIds
    -> filtered TestPlan
    -> ordinary scheduler/executor
```

TOOL002-B/C/D should not acquire file-watching or incremental dependency policy.

## 15. Property testing, fuzzing and benchmarks remain separate policies

Go fuzzing and mature property-testing ecosystems demonstrate that generated
inputs need seed/corpus/shrinking semantics beyond ordinary example tests.
Likewise benchmark runners need warmup, timing and statistical policies that are
not test pass/fail semantics.

Protos may later reuse TestPlan-like scheduling, fresh-Process execution and
resource accounting, but it should not collapse these institutions:

```text
example/conformance tests   TOOL002 policy
property/fuzz testing       future explicit testing policy/tool extension
benchmarks                  separate bundled tool / PERF project evidence
```

Shared mechanisms do not imply shared user-facing semantics.

## Falsification matrix

The comparative audit attempted to invalidate the existing choices rather than
merely collect supporting examples.

| Question | Strong counterexample considered | Result for Protos |
|---|---|---|
| Should every case be an OS process? | nextest/CTest/Node gain hard kill and crash isolation from OS processes | **No for normal cases.** Fresh semantic Process is cheaper and matches Protos semantics; optional OS workers remain a later containment layer. |
| Should tests share one Actor/runtime context? | thread/function runners can be very cheap | **No.** Process/RootActor/bootstrap/lifecycle tests require a clean semantic root. |
| Should workers discover independently? | pytest-xdist supports flexible plugin-driven collection | **Not required.** Prefer one inert TestPlan before scheduling; future discovery compiles to it. |
| Should Protos add test syntax/annotations? | Rust/Go/JUnit integrate test declarations deeply | **No.** Existing ordinary programs/manifests are sufficient and avoid a new language category. |
| Should manifests be removed? | convention discovery is convenient in pytest/Go/Node | **No initial rewrite.** Preserve manifests; allow future discovery as another TestPlan producer. |
| Should assertions replace external expectations? | most unit frameworks use assertions | **No forced replacement.** Both are ordinary policy layers and can coexist. |
| Are resource groups enough? | nextest/CTest/Buck expose capacities/pools | **Refine.** Generalize to capacity accounting; capacity=1 subsumes mutex groups. |
| Should retries be automatic? | nextest/Surefire can rerun failures | **No initial default.** Future retries retain every attempt and explicit flaky evidence. |
| Should result caching ship early? | Go/Bazel obtain major speedups | **No.** Defer until hermetic inputs/environment/capabilities can be fingerprinted. |
| Should TOOL002-B solve hard timeout? | process-hosted runners can kill a worker | **No.** Same-runtime arbitrary hard preemption is not a valid Protos semantic guarantee. |
| Should sharding/distribution change test semantics? | Bazel/Buck distribute work | **No.** Sharding selects CaseSpecs; each case keeps the same semantic Process contract. |

## Refined selected architecture

The post-A architecture is now:

```text
Java/runtime implementation tests
        |
        v
bundled `protos test` policy
        |
        +-- current manifests
        +-- future discovery
        +-- filtering/tags
        +-- seed/order policy
        |
        v
stable CaseSpec / CaseId values
        |
        v
inert TestPlan
        |
        +-- future shard selection
        +-- scheduler capacity/resource accounting
        +-- future retry policy
        |
        v
general exact-entry fresh-Process executor
        |
        +-- explicit capabilities
        +-- private streams
        +-- fresh Process / RootActor
        +-- production execution semantics
        |
        v
inert ExecutionOutcome
        |
        v
Protos expectation/assertion aggregation + deterministic reporter
        |
        v
human and machine results

optional later physical containment:
controller -> amortized OS workers -> fresh Protos Processes
```

The plan and outcome are inert data boundaries. The executor remains mechanical;
policy remains in ordinary Protos tooling.

## Consequences for TOOL002-B

The comparative checkpoint is complete. TOOL002-B may now proceed.

TOOL002-B should establish/reuse the smallest general mechanism that can:

1. create a fresh Protos Process and RootActor;
2. use an exact already-selected module/execution plan and entry;
3. provision exact application arguments;
4. provision only explicitly supplied capabilities;
5. provision independent stdin/stdout/stderr bindings and Encodings;
6. execute the entry with ordinary production scheduling until its semantic
   terminal outcome;
7. return an inert result containing the terminal outcome and captured stream
   data needed by higher layers;
8. cleanly terminate/release Process-local host resources after the execution
   boundary finishes.

TOOL002-B must **not** implement TestPlan parsing, stable CaseId policy, discovery,
manifest interpretation, assertions, expectation interpretation, fixtures,
resource allocation, retries, hard timeout, sharding, watch mode, caching or
reporting. Those are consumer/tool policies above the mechanism.

If the smallest correct general execution mechanism requires an independently
meaningful shared driver/runtime work item, that need must be audited explicitly;
it must not be created merely to rename TOOL002-B.

## Consequences for later TOOL002 slices

The existing sequence remains useful with these refinements:

- **TOOL002-C** proves one CaseSpec-equivalent exact case sequentially through
  the fresh-Process executor with private streams and inert outcome.
- **TOOL002-D** migrates current manifest/expectation interpretation into Protos,
  assigns stable case identity, and constructs the initial inert TestPlan.
- **TOOL002-E/F/G** migrate package-tool fixtures and async/concurrency-sensitive
  corpus behavior without a test-only runtime model.
- **TOOL002-H** adds bounded parallel scheduling over the TestPlan with explicit
  global/per-case capacity accounting, reproducible seeded plan order and
  deterministic reporting independent of completion order.
- **TOOL002-I** adds named resource capacities/pools and private capability
  provisioning where external resources require them.
- **TOOL002-J** closes the Java-first / Protos-tool-second repository validation
  pipeline and machine/human reporting integration required by the selected
  initial scope.

Retries, hard OS-worker containment, sharding/distribution, watch/incremental
selection and result caching remain later explicitly promoted work unless a
future audit adds them to TOOL002 before parent closure.

## Decisions retained or newly closed

```text
bundled `protos test` written primarily in Protos        YES
Java/JUnit retained for Java/runtime-specific tests      YES
fresh semantic Protos Process per ordinary test case     YES
fresh RootActor per ordinary test case                   YES
OS process per ordinary case                             NO
separate inert TestPlan before physical scheduling       YES
stable case identity before scheduling                   YES
current manifests preserved as first plan producer       YES
future discovery allowed through same TestPlan boundary  YES
assertions may coexist with external expectations        YES
assertion syntax/compiler mode/privileged Test object    NO
bounded outer parallelism                                YES
scheduler generalized around capacity accounting         YES
private per-case stdout/stderr                            YES
explicit capabilities/private resources preferred        YES
capacity-1 named resource subsumes mutex group            YES
P/Actors/Groups/Futures remain production semantics      YES
P as runner isolation                                    NO
hard arbitrary same-runtime timeout guarantee             NO
future amortized OS workers for hard containment possible YES
silent automatic retry / hidden flake evidence            NO
initial test-result caching                               NO
sharding/distribution changes case semantics              NO
```

## Decisions intentionally still open

```text
exact `protos test` CLI flags
exact current-manifest migration syntax/evolution
future discovery convention and scope
exact textual stable CaseId representation
concrete ordinary-Protos TestPlan/CaseSpec representation
assertion/helper module API
fixture/setup/cleanup API and scopes
filter/tag syntax
seed/randomization CLI and default policy
initial jobs=auto formula after benchmark evidence
per-case execution-capacity declaration syntax
named-resource capacity/pool declaration syntax
machine-readable report schema
hard-timeout threshold/policy and OS worker/controller design
retry/flaky policy and CLI
shard selection CLI and stable partition algorithm
watch-mode dependency graph/source
cache hermeticity and input/environment/capability fingerprint model
crash dump/diagnostic policy for future physical workers
```

None of these open choices is required to implement TOOL002-B's mechanical
fresh-Process boundary.

## Follow-on massive-scale/distribution checkpoint

A later scale-focused comparison extends this audit without invalidating its
conclusions. The selected result is recorded separately in
`docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`.

The follow-on closes additional architecture boundaries needed before local
executor code hardens:

- logical Run/Case/Variant/Attempt identity is distinct from worker identity;
- semantic Protos outcomes are distinct from infrastructure outcomes;
- remote execution must not assume exactly-once physical execution;
- TestPlan/CaseSpec data is inert, serializable and may be streamed/paged at
  large scale rather than rediscovered independently by every worker;
- physical execution backends may evolve from local same-runtime to amortized
  OS-worker and remote pools while each attempt still receives a fresh semantic
  Protos Process;
- scheduling resources require capacity plus locality/scope, and may later need
  access modes, rather than a single global mutex interpretation;
- affected analysis and safe caching reduce work before distributed execution;
- private output capture must permit bounded/artifact-backed representation.

These refinements do not add remote execution to the initial TOOL002 scope and do
not block TOOL002-B. They constrain B only to remain local, test-neutral and
backend-compatible.

## Official references reviewed

### Python / pytest

- https://docs.pytest.org/en/stable/how-to/fixtures.html
- https://docs.pytest.org/en/stable/how-to/capture-stdout-stderr.html
- https://pytest-xdist.readthedocs.io/en/stable/distribution.html
- https://pytest-xdist.readthedocs.io/en/stable/how-to.html

### Rust

- https://doc.rust-lang.org/book/ch11-02-running-tests.html
- https://nexte.st/docs/design/why-process-per-test/
- https://nexte.st/docs/configuration/
- https://nexte.st/docs/features/test-groups/
- https://nexte.st/docs/ci-features/partitioning/
- https://nexte.st/docs/features/retries/

### Go

- https://pkg.go.dev/cmd/go#hdr-Test_packages
- https://pkg.go.dev/testing
- https://go.dev/doc/tutorial/fuzz

### Java

- https://junit.org/junit5/docs/current/user-guide/#writing-tests-parallel-execution
- https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html
- https://docs.gradle.org/current/userguide/java_testing.html

### Node / Vitest

- https://nodejs.org/api/test.html
- https://vitest.dev/config/pool.html

### .NET

- https://learn.microsoft.com/dotnet/core/testing/
- https://learn.microsoft.com/dotnet/core/tools/dotnet-test
- https://learn.microsoft.com/visualstudio/test/vstest-console-options

### Erlang / Elixir

- https://hexdocs.pm/ex_unit/ExUnit.html
- https://hexdocs.pm/ex_unit/ExUnit.Case.html
- https://www.erlang.org/doc/apps/common_test/write_test_chapter.html

### C / C++

- https://cmake.org/cmake/help/latest/manual/ctest.1.html
- https://cmake.org/cmake/help/latest/prop_test/RESOURCE_LOCK.html
- https://cmake.org/cmake/help/latest/prop_test/RESOURCE_GROUPS.html
- https://google.github.io/googletest/advanced.html

### Bazel / Buck2

- https://bazel.build/reference/test-encyclopedia
- https://bazel.build/reference/be/general#test_suite
- https://buck2.build/docs/rule_authors/test_execution/
- https://buck2.build/docs/rule_authors/test_execution/#local-resources

### Ruby / RSpec

- https://rspec.info/features/3-13/rspec-core/command-line/order/
- https://rspec.info/features/3-13/rspec-core/command-line/bisect/

## Audit conclusion

The existing TOOL002 direction survives comparison, but with two important
refinements before implementation hardens:

1. establish an inert TestPlan/stable-case planning boundary above the general
   fresh-Process executor;
2. generalize future parallel resource scheduling from Boolean groups to capacity
   accounting.

Hard timeout remains intentionally outside the same-runtime semantic Process
mechanism, and caching/retries/distribution remain higher-level policies rather
than reasons to contaminate TOOL002-B.

With this checkpoint recorded, TOOL002-B is architecturally **READY**.
