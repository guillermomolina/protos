# D069 — Test Tool parallelism control and default

Status: **RATIFIED — Candidate A′ selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #333

Nature: implementation-independent public Test Tool scheduling policy

Triggered by: `TOOL002-H` / GitHub #95 after H2B2 publication.

Primary consumer: `TOOL002-H2B3`

Normative language effect: **none**.

## Decision boundary

TOOL002-H2B1/H2B2 already provide one ordinary-Protos bounded scheduler whose
input is `maxInFlight`. The remaining public-policy question is what stable
Test Tool concept supplies that bound and what happens when the caller supplies
nothing.

D069 decides only that public logical-capacity contract. It does **not** select
JVM carrier topology, resource declarations/weights, hard timeout/kill, retries,
sharding, remote execution, persistent configuration, reporting policy or an
automatic hardware-derived jobs formula.

## Ratified decision — explicit logical capacity, serial default

The public control is:

```text
protos test --jobs N
```

where `N` is a positive ordinary Integer.

The semantic contract is:

```text
jobs = global Test Tool logical execution-slot capacity
```

not:

```text
jobs = JVM thread count
jobs = platform-thread count
jobs = CPU count
jobs = OS-process count
jobs = physical worker count
```

When `--jobs` is absent:

```text
jobs = 1
```

`--jobs 1` is the stable serial/reproduction reduction path.

At the H2 stage each admitted CaseSpec consumes one execution slot, so the
published bounded runner receives:

```text
maxInFlight = jobs
```

A malformed, missing-value, zero, negative or duplicate `--jobs` occurrence must
fail explicitly rather than clamp, guess or silently fall back.

The policy belongs to bundled Protos Test Tool code through ordinary
`process.args()` handling. Host submission machinery receives already-admitted
work and MUST NOT reinterpret `jobs` as its own executor/thread limit.

## Why `jobs` is capacity rather than a carrier count

D055 already separates three layers:

```text
Test Tool scheduling policy
        |
        v
one exact asynchronous execution -> ordinary Future
        |
        v
replaceable physical execution backend
```

D069 keeps that separation intact. The logical capacity survives changes from
local same-runtime Contexts to future OS workers or remote execution and survives
changes in JVM carrier machinery.

It also composes directly with the already-selected TOOL002-I direction. A later
resource-aware scheduler may evolve from:

```text
case A -> 1 global execution slot
case B -> 1 global execution slot
```

to:

```text
case A -> 1 global execution slot
heavy case -> multiple global capacity units
DB case -> execution capacity + named exclusive resource
gpu case -> execution capacity + named GPU capacity
```

without changing what `--jobs N` means.

## Exhaustive prior-art survey

The decision was compared across language-native test frameworks, external test
orchestrators, build systems and resource-aware schedulers. The goal was not to
copy a familiar flag spelling but to understand what remains stable when suites,
inner concurrency, resources and execution backends grow.

### Apple Swift Testing

Swift Testing runs eligible tests in parallel by default and uses ordinary Swift
concurrency. Local traits such as serialization constrain when tests may overlap;
newer dependency-oriented mechanisms continue the direction toward declaring
conflicts close to the affected tests.

Useful lesson: concurrency inside the test framework should remain ordinary
language/runtime concurrency, and restrictions should be composable rather than
implemented as a second execution universe.

Not adopted: letting the language runtime alone define the Test Tool's outer
admission width. D055 already assigns that policy to the Protos Test Tool.

### Apple SwiftPM / Xcode

SwiftPM and Xcode act as external orchestrators and expose explicit worker-count
controls independently of the concurrency used inside one test. This separation
is directly relevant to Protos:

```text
external test orchestration capacity
        !=
concurrency exercised inside a test
```

Useful lesson: the outer scheduler may own a stable worker/capacity control while
leaving test-internal language concurrency untouched.

### Rust libtest

Rust's built-in test harness exposes an explicit test-thread count and retains a
single-threaded reduction mode. Its model is conceptually small and easy to
reason about, but it is primarily a width limit rather than a full future
resource-capacity model.

Useful lesson: keep the public reduction control simple and reproducible.

### cargo-nextest

Nextest exposes explicit jobs/test-thread capacity and allows a test to require
multiple threads/capacity units. This is one of the closest precedents to Protos's
future capacity model because the global limit can outlive a one-slot-per-test
implementation.

Useful lesson: define the public knob as scheduler capacity, not as an
implementation-specific thread promise.

### Go test

Go separates package-level execution concurrency from test-level `t.Parallel`
concurrency. Automatic hardware-derived defaults exist, but the two concurrency
domains remain conceptually distinct.

Useful lesson: outer orchestration and inner program/test concurrency are
separate policy domains. Protos already has that separation through fresh test
Processes versus Actors/Tasks/Futures/P inside a case.

### pytest + xdist

Pytest core remains sequential while xdist adds explicit worker counts and
`auto`-style modes. This provides strong evidence that a stable explicit numeric
control can precede and coexist with later automatic selection.

Useful lesson: an automatic mode need not be part of the first public contract.

### Node.js test runner

Node provides an explicit test-concurrency control and uses a hardware-derived
default in its process-isolated mode.

Useful lesson: automatic defaults are viable once the runner has a sufficiently
mature isolation/resource story.

Not adopted now: tying Protos's public default directly to host parallelism before
TOOL002-I and benchmark evidence exist.

### JUnit

JUnit supports fixed, dynamic and custom parallel-execution strategies. Its
executor/pool semantics demonstrate that a configured logical parallelism value
can interact with concrete pool behavior in ways that require additional limits
to obtain a strict maximum.

Useful lesson for Protos: preserve the stronger invariant already proven by H2B
— `maxInFlight = N` means no more than N admitted exact case executions — and do
not let host pool heuristics redefine that bound.

### Gradle Test

Gradle's test process parallelism defaults conservatively and is enabled by an
explicit maximum. Its documentation also highlights shared filesystem/database
resources as reasons not to assume all suites are safely parallel merely because
hardware is available.

Useful lesson: a serial default remains a legitimate modern choice while
resource declarations are incomplete.

### Maven Surefire

Surefire can combine fork counts, thread counts, parallel modes and Maven-level
parallelism. It scales operationally but exposes several partially overlapping
concurrency controls.

Useful lesson by contrast: Protos should keep one Test Tool capacity concept and
avoid making users reason about multiple independent outer concurrency knobs.

### .NET test platform

The .NET test ecosystem supports module/process-level parallel execution and
processor-aware defaults/configuration.

Useful lesson: hardware-aware auto modes can be useful, but the hardware metric
and isolation level become part of the operational compatibility surface.

### Elixir ExUnit

ExUnit combines a global concurrency ceiling with local declarations that govern
which tests/modules may overlap.

Useful lesson: global capacity plus local conflict/resource declarations is a
scalable composition, matching the direction reserved for TOOL002-I.

### CTest

CTest exposes a global parallel capacity and can also assign tests processor
weights and named resources. Its model scales from a simple `-j N` to resource
pools without discarding the original global-capacity abstraction.

This is the strongest structural precedent for Protos's long-term model.

### Bazel

Bazel can derive test/job concurrency automatically from host CPU/RAM and
integrates those decisions with a mature local/remote resource scheduler.

Useful lesson: `auto` works best when embedded in a broader resource model. The
fact that Bazel can make a defensible automatic decision does not imply that
CPU-count alone is sufficient before such a model exists.

### Buck2

Buck2 cleanly separates high-level test orchestration from replaceable execution
backends, including remote execution.

Useful lesson: a logical admission control should survive backend replacement and
must not mean a specific local carrier/thread topology.

## Candidate set

### A′ — explicit `--jobs N`, absent => `1`

**Selected.**

One positive integer denotes global Test Tool execution-slot capacity. No
hardware-derived automatic formula is selected yet.

### B — `--jobs N|auto`, absent => `1`

Credible future extension, but rejected for initial ratification because it would
freeze an automatic formula before resource accounting and benchmarks establish
what host signal is appropriate for Protos.

### C — `--jobs N|auto`, absent => `auto`

Rejected for now. It would change ordinary `protos test` execution to implicit
outer concurrency before resource/conflict declarations exist and before the
interaction with test-internal Actors/P work has been benchmarked.

### D — Boolean parallel flag with hidden width

Rejected. It obscures the actual admission capacity and leaves no stable
reproduction value except by disabling the feature entirely.

### E — persistent profile/config owns jobs first

Deferred. A configuration institution and precedence model are not required to
solve H2 and would create a durable surface before a concrete need exists.

### F — resource-aware + automatic scheduling now

Architecturally strong but premature. TOOL002-I intentionally owns the resource
model and must not be silently implemented as part of D069/H2.

### G — hidden fixed/host-derived default without a public control

Hard-rejected. It changes execution behavior without a stable serial reduction
control and moves Test Tool scheduling policy toward host mechanism.

## Common ten-dimension scorecard

Scores are 1–5. Confidence is HIGH unless noted otherwise.

| Criterion | A′ explicit N, default 1 | B N/auto, default 1 | C default auto | E config/profile first | F resources+auto now |
| --- | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | **5** — strict bound and current default retained | 5 | 4 — undeclared conflicts may surface implicitly | 5 | 5 |
| Protos alignment | **5** — one mechanism, no hidden heuristic | 4 | 3 | 3 | 4 |
| Future-option resilience | **5** — auto/resources/backends remain open | 4 | 3 | 4 | **5** |
| Scalability | 4 — explicit tuning required | 5 | **5** | 5 | **5** |
| Conceptual simplicity | **5** | 4 | 4 | 2 | 3 |
| Portability / implementation freedom | **5** — logical slots only | 4 — auto metric becomes portable contract | 4 | 4 | 4 |
| Runtime / resource cost | **5** — no surprise outer concurrency | 4 | 3 | 4 | 4 |
| Failure / operability | **5** — explicit reproducible N | 4 | 3 | 4 | 4 |
| Reversibility / migration | **5** | 4 | 2 | 3 | 3 |
| Evidence maturity / implementation risk | **5** | 5 | 5 | 4 | 4 |
| **Total / 50** | **49** | **43** | **36** | **38** | **41** |

Candidate D is structurally weaker than A′/B because its hidden width defeats the
explicit-capacity requirement. Candidate G is disqualified and not scored.

## Focused future / scale / Protos scorecard

The project owner additionally requested explicit scoring on the three most
important long-term dimensions. Scores are 1–10.

| Candidate | Future resilience | Scalability | Protos philosophy | Total |
| --- | ---: | ---: | ---: | ---: |
| **A′ — explicit N, default 1** | **10** | **9** | **10** | **29/30** |
| B — N/auto, default 1 | 9 | 9.5 | 8 | 26.5/30 |
| C — N/auto, default auto | 8 | **10** | 7 | 25/30 |
| D — Boolean parallel + hidden width | 6 | 8 | 6 | 20/30 |
| E — profiles/config first | 9 | 9 | 6 | 24/30 |
| F — resources + auto now | **10** | **10** | 7 now / 9 after I | 27/30 now |

A′ wins because it preserves the capacity abstraction that scales later while
refusing to freeze an unearned automatic formula today.

## Scalability and future-scenario stress test

### Very large suites

The scheduler already operates on stable TestPlan/CaseSpec order and a bounded
number of in-flight executions. Increasing explicit `N` scales outer throughput
without changing result ordering or case semantics.

### Tests that create many Actors/Tasks/Futures/P operations

The outer capacity remains independent of inner concurrency. The serial default
avoids multiplying two independent concurrency domains before resource accounting
and benchmarks quantify safe automatic behavior.

### TOOL002-I resources

D069 deliberately leaves room for global execution capacity to compose with named
resource capacities and weighted cases. No D069 spelling or meaning must change.

### CI and reproducibility

CI can pin an exact `--jobs N` independent of machine size. `--jobs 1` remains a
portable reproduction path for failures that may depend on outer overlap.

### OS-worker backend

`jobs` remains logical admission capacity even if one case later executes inside
an amortized worker process. It does not become process-count semantics.

### Remote/distributed backend

The same logical bound can limit outstanding case attempts while a later placement
layer chooses nodes/workers. Remote topology does not change the public meaning.

### Alternative JVM/Truffle carrier

PLAT023 may select the initial host carrier, and later platform decisions may
replace it. `jobs` remains unchanged because it belongs above that boundary.

### Container/CPU quota changes

An explicit value remains reproducible. A later `auto` decision may use a more
appropriate quota-aware signal without retroactively redefining numeric values.

## Failure modes and counterexamples

- `--jobs 0` / negative / malformed: explicit Test Tool failure.
- duplicate `--jobs`: explicit failure; no precedence guess.
- outer capacity exceeds useful host capacity: permitted explicit user choice;
  the Test Tool does not silently clamp it to a host heuristic.
- resource-conflicting cases under `N > 1`: TOOL002-I owns future declaration and
  admission rules; D069 does not pretend the conflict model already exists.
- one case internally saturates CPUs through Actors/P: outer capacity remains a
  separate logical decision and can be reduced explicitly.
- physical completion order differs from plan order: H2B deterministic projection
  remains authoritative.

## Strongest argument against A′

H2 exists specifically to deliver parallel speed, but ordinary `protos test`
remains serial until the caller opts in. Modern runners such as Swift Testing,
Node, Rust/nextest and mature build schedulers often exploit host parallelism by
default, so A′ delays a visible performance benefit and asks users/CI to select a
number manually.

The project accepts that cost because Protos does not yet have TOOL002-I resource
weights/conflict declarations or benchmark evidence for how outer test Processes
compose with inner Actors/P work. Freezing a CPU-derived default now would turn a
provisional implementation heuristic into public policy.

## What future requirement could make A′ regrettable?

A mature ecosystem may reasonably expect `protos test` to exploit safe local
capacity automatically with no per-invocation tuning.

### Escape path

Add an explicitly designed `--jobs auto` after TOOL002-I and benchmark evidence
identify a defensible, quota-aware formula. A later separately approved
compatibility decision may make `auto` the default. Numeric `--jobs N` and the
serial `--jobs 1` path remain unchanged.

## Protos rationale

Candidate A′ is the most Protos-aligned current choice because it preserves:

- **mechanisms over institutions** — one ordinary capacity number, no premature
  profile/configuration subsystem;
- **ordinary things remain ordinary** — Test Tool policy is ordinary bundled
  Protos over `process.args()` and the existing scheduler;
- **pay only for what you use** — callers that do not request outer concurrency do
  not pay its runtime/resource cost by default;
- **scale by composition** — the same capacity composes later with resource
  accounting, OS workers and remote placement;
- **keep platform differences at the boundary** — CPU/thread/carrier details do
  not become public `jobs` semantics;
- **semantic distinctions remain visible** — outer Test Tool admission remains
  distinct from inner Actor/Task/P concurrency;
- **generality must be earned** — automatic heuristics and configuration are
  added only when evidence justifies them.

## Intentionally deferred

D069 does not decide:

- `--jobs auto` spelling or formula;
- whether a later release defaults to automatic capacity;
- a short `-j` alias;
- environment-variable control;
- persistent Test Tool configuration/profile files or precedence;
- per-case capacity weights or named resources (TOOL002-I);
- JVM carrier topology (PLAT023);
- hard timeout/kill or OS-worker lifecycle;
- retry/flaky policy;
- sharding/distribution/remote placement;
- result caching;
- priority/fairness policy beyond the already-ratified bounded admission and
  deterministic reporting requirements.

## Ratification closure

The project owner explicitly approved Candidate A′ on 2026-09-11 after the
expanded comparison across Apple Swift Testing/SwiftPM/Xcode, Rust/libtest,
cargo-nextest, Go, pytest/xdist, Node, JUnit, Gradle, Maven Surefire, .NET,
Elixir ExUnit, CTest, Bazel and Buck2, with focused future-resilience,
scalability and Protos-philosophy scoring.

D069 is therefore `RATIFIED`.

Ratification changes no Core normative specification, executable implementation,
Maven implementation version, native boundary, JVM carrier or resource policy.
It releases the public jobs-policy dependency of TOOL002-H2B3. H2B3 remains
independently blocked on the separately owned PLAT023 carrier decision.
