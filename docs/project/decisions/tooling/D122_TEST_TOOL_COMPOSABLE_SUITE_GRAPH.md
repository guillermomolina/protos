# D122 — Test Tool composable suite graph and execution-requirement contract

Status: **RATIFIED — Candidate F selected**

Allocated: **2026-09-13**

Explicit project-owner approval: **2026-09-13**

Decision issue: GitHub #469

Primary consumer: `TOOL005` / GitHub #468

Related coordination: `TEST001` / GitHub #449

Nature: implementation-independent Test Tool suite-composition contract

Normative language effect: **none**.

## Decision boundary

`TOOL002` already establishes the durable lower Test Tool architecture:

- Test Tool policy is primarily Protos-owned;
- `CaseSpec` / `TestPlan` data is inert, deterministic and serializable;
- one semantic test execution runs in a fresh Protos `Process` / `RootActor`;
- logical test identity is separate from physical placement;
- scheduling and future OS/remote execution consume inert plans;
- D077 owns case-level resource requirements;
- D098 owns resource-catalog `provider` and provider-scoped `profile` logical
  identities.

`TEST001` then established the repository goal that existing `.protos` test
corpora are executed by the official `protos test` tool rather than through
Java/JUnit full-corpus wrappers.

The current public Test Tool bootstrap still names a bounded set of corpus roots
directly. `TOOL005` therefore needs one durable architecture for composing the
complete repository-owned Protos corpus without turning filesystem layout,
Java classes, plugins, or host-specific mechanisms into Test Tool policy.

D122 decides that suite-composition boundary.

D122 does **not** change:

- Protos language or Standard Library semantics;
- `CaseSpec` expectation semantics;
- `--jobs` semantics;
- D077/D098 resource contracts;
- provider discovery/provisioning;
- physical worker placement;
- remote-execution transport;
- Test Tool reporting/progress contracts;
- Java/JUnit ownership of Java/Truffle/runtime/host implementation tests.

## Selected contract — Candidate F

The Test Tool uses an **explicit composable suite graph**.

Conceptually:

```text
root Suite
  |
  +-- child Suite
  |     |
  |     +-- child Suite
  |     `-- corpus leaf
  |
  `-- corpus leaf
          |
          v
      inert TestPlan
          |
          v
  filter / shard / schedule
          |
          v
 replaceable execution backend
          |
          v
 fresh semantic Process
```

A suite is a stable logical node. A suite may explicitly reference child suites
and/or corpus leaves. Membership exists only through explicit composition edges.

A local descriptor becomes part of the official suite graph only when referenced
explicitly. Merely placing a descriptor or test file under a directory does not
enroll it.

## Ratified invariants

```text
SUITE_MEMBERSHIP=EXPLICIT
SUITE_COMPOSITION=RECURSIVE_EXPLICIT
RECURSIVE_AUTO_DISCOVERY=NO
SUITE_ID=STABLE_LOGICAL_IDENTITY
SUITE_ID_IS_PATH=NO
DESCRIPTORS=INERT_SERIALIZABLE_DATA
HOST_IMPLEMENTATION_IDENTITY_IN_DESCRIPTOR=NO
UNKNOWN_SUITE=FAIL_CLOSED
UNKNOWN_EXECUTION_REQUIREMENT=FAIL_CLOSED
DUPLICATE_SUITE_ID=FAIL_CLOSED
SUITE_CYCLE=FAIL_CLOSED
PLAN_BEFORE_SCHEDULING=YES
CANONICAL_REPORT_ORDER=PLAN_ORDER
PHYSICAL_EXECUTION_ORDER=INDEPENDENT
RESOURCE_REQUIREMENTS=D077
RESOURCE_PROVIDER_PROFILE=D098
D122_PROFILE_NAME_REUSE=NO
REMOTE_PLACEMENT_CHANGES_SUITE_MEANING=NO
```

## Suite identity

Every suite has a stable logical identity.

A suite identity is not its repository path. Moving a suite descriptor or corpus
within the repository does not inherently change the suite's logical identity.

The identity must remain suitable for:

- deterministic reporting;
- filtering and selection;
- persistent evidence;
- sharding;
- serialization to future physical workers;
- repository/package composition.

D122 does not ratify the exact lexical grammar or concrete identity names. If
TOOL005 exposes a substantive naming choice, it must use the normal naming
review / decision boundary.

## Explicit composition

Composition is recursive but never implicit.

A root suite may reference child suites; a child suite may reference further
suites or corpus leaves.

For example, a future repository topology may conceptually be:

```text
repository
  +-- core
  +-- stdlib
  |     +-- collections
  |     +-- csv
  |     `-- uri
  `-- tools
        +-- package
        `-- test
```

This example is illustrative only. It does not ratify those exact `SuiteId`
spellings or require that repository directories have the same structure.

The important rule is that each edge is declared explicitly.

## No authoritative recursive discovery

D122 rejects recursive convention discovery as the authority for official suite
membership.

Paths, filenames, glob matches, descriptor presence, or naming conventions may
be used by a future authoring convenience only if the result is converted into
the same explicit inert suite graph before Test Tool planning.

They may not silently decide:

- that a test belongs to the official suite;
- which mechanical execution context it requires;
- which provider/resource identity it uses;
- which host implementation executes it.

## Inert descriptors

Suite metadata is ordinary inert data.

Persisted suite metadata must not identify or embed:

- Java class names;
- Java service/provider classes;
- callbacks;
- executable paths;
- shell commands;
- shared-library paths;
- plugin artifacts;
- URLs or implementation download locations;
- live host objects;
- worker identities;
- credentials or secrets.

The host may maintain an explicitly authorized mapping from a logical suite-side
execution requirement to a concrete provisioner, but the persisted descriptor
selects only the logical requirement.

Changing Java/native/local-daemon/remote-worker implementation behind that
mapping does not redefine suite meaning.

## Suite execution requirement is not D098 `profile`

Some corpus families require different mechanical execution arrangements, for
example Actor/Group overlays, Package Tool execution or confined filesystem
provisioning.

That suite-side concept is distinct from D077/D098 case-level resources.

D098 already assigns exact meaning to:

```text
provider
profile
```

where `profile` is optional and scoped to one logical resource provider.

D122 therefore explicitly forbids reusing `profile` as the name or semantic
namespace for the suite-side mechanical execution requirement.

The exact name for that suite-side identity is deliberately not selected here.
TOOL005 must review naming before exposing one if the implementation requires a
durable name.

## Plan before scheduling

The suite graph is expanded and validated before test scheduling.

Conceptually:

```text
SuiteGraph
    |
    | validate identities / references / cycles / requirements
    v
canonical invocation plan
    |
    +-- TestPlan
    +-- TestPlan
    `-- TestPlan
          |
          v
 filter / shard / resource admission / scheduling
```

Future physical workers receive already-resolved inert plan fragments. They do
not rediscover the repository or reconstruct authoritative suite membership.

This preserves the existing TOOL002 separation between logical planning and
physical placement.

## Deterministic order

Explicit composition order defines canonical planning/reporting order.

That order does not require serial physical execution.

For example:

```text
canonical order:   A B C D
completion order:  C A D B
report authority:  A B C D
```

Existing TOOL002 deterministic result/reporting contracts remain authoritative.

## Fail-closed behavior

Suite-graph validation fails before executing cases when it encounters at least:

- duplicate `SuiteId`;
- missing referenced suite;
- composition cycle;
- invalid corpus/manifest reference;
- unknown suite execution requirement;
- ambiguous logical identity;
- an execution requirement unsupported by the selected environment.

There is no fallback based on a similar path/name and no implicit `plain`
treatment for an unknown requirement.

## Resource separation

D122 does not duplicate the existing Test Tool resource model.

A suite-side mechanical execution requirement and a case-level resource
requirement are orthogonal.

Conceptually:

```text
Suite
  |
  +-- logical suite execution requirement
  |
  `-- TestPlan / CaseSpec
          |
          `-- D077 resource requirements
                  |
                  v
             D098 catalog
             provider/profile
```

A future Actor-oriented suite can therefore use its required Test Tool execution
mechanism while an individual case independently requests a capacity-constrained
resource through D077/D098.

## Distribution and remote execution

Physical placement does not change suite identity or semantics.

The selected contract permits later execution such as:

```text
SuiteGraph
   |
   v
InvocationPlan
   |
   +-- shard 0 -> local worker
   +-- shard 1 -> OS worker
   `-- shard 2 -> remote worker
```

The worker receives logical, serializable plan data. It does not need repository
discovery or an executable collection plugin merely to determine membership.

D122 selects no remote protocol or placement policy.

## Prior-art basis

The expanded audit compared materially different models across:

- pytest / pytest-xdist;
- Go `go test`;
- Cargo/libtest/workspaces;
- JUnit Platform;
- Gradle Test Suites;
- Maven Surefire;
- Bazel;
- Buck2;
- CMake/CTest;
- Meson;
- Node native test runner;
- Deno;
- Jest;
- Vitest;
- Elixir ExUnit;
- Erlang Common Test;
- SwiftPM;
- Microsoft.Testing.Platform;
- Pharo/SUnit;
- tox.

The strongest transferable findings were:

1. Bazel/Buck2: explicit graph identity and separation from physical placement
   scale to monorepos and remote execution.
2. CTest: test resource requirements, resource availability and lifecycle are
   separate concerns.
3. Cargo/SwiftPM: package-local ownership avoids a central registry of all test
   leaves.
4. Common Test/SUnit: explicit recursive suite composition is also natural in
   dynamic/message-oriented systems.
5. pytest/Go/Node/Deno/Jest/Maven: convention discovery is ergonomic but makes
   paths/names part of hidden policy when suites need heterogeneous capabilities.
6. JUnit Platform/Microsoft.Testing.Platform: launcher/framework separation is
   valuable, but executable engine/plugin identity is too much persisted
   authority for Protos.
7. pytest-xdist: worker recollection is unnecessary when the controller already
   owns an inert serializable plan.

## Candidate comparison

Owner-requested principal axes, scored 1–10:

| Candidate | Aguante de futuro | Escalabilidad | Filosofía Protos |
| --- | ---: | ---: | ---: |
| A — hard-coded host roots | 3 | 3 | 5 |
| B — recursive convention discovery | 7 | 9 | 4 |
| C — flat explicit root registry | 8 | 7 | 10 |
| D — local descriptors + recursive discovery | 8 | 10 | 7 |
| E — root registry + optional local descriptors | 9 | 9 | 10 |
| **F — explicit composable suite graph** | **10** | **10** | **10** |
| G — executable engine/plugin registration | 10 | 9 | 5 |

Candidate E remains a viable small implementation shape, but if the root must
eventually delegate to owned subtrees it converges on F. Selecting F now avoids
making the flat root registry a permanent structural ceiling while requiring
only one additional generation-1 capability: a suite may explicitly reference
another suite.

## Generation-1 implementation consequence

TOOL005 should start with the smallest implementation that can represent the
currently hard-coded Test Tool corpus families without changing their semantics.

Generation 1 need not implement:

- remote execution;
- package-distributed suite registries;
- automatic suite generation;
- new assertion APIs;
- new filtering syntax;
- new resource policy.

It must preserve the F contract so later suites can compose without adding
another Java/`Main.protos` hard-coded branch per family.

## Relationship to TEST001

D122 releases TOOL005 from the design-decision gate.

After TOOL005 can execute repository-owned Protos corpus families through the
selected suite graph:

1. TEST001-H validates the same model from the extracted portable distribution;
2. TEST001-I retires Java/JUnit wrappers whose sole remaining purpose is to run
   those `.protos` corpora;
3. genuine Java/Truffle/runtime/host tests remain JUnit-owned.

D122 does not authorize a campaign to rewrite Java tests in Protos.
