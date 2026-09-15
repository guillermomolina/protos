# D135 — Test Tool deterministic guest-bootstrap fixture ownership

Status: **RATIFIED — Candidate A′ selected**

Allocated: **2026-09-15**

Explicit project-owner approval: **2026-09-15**

Decision issue: GitHub #528

Primary consumer: `TOOL005` / GitHub #468

Parent architectural authority:

- `D122` — Test Tool composable suite graph
- `D123` — Test Tool SuiteId identity
- `D125` — Test Tool execution-requirement identity and host binding
- `D126` — Test Tool corpus identity and plan-source binding
- `D129` — Test Tool per-case project-tree provisioning
- `D133` — Test Tool per-case CaseAuthority scheduling
- `D134` — CaseAuthority lifecycle failure classification

Nature: implementation-independent Test Tool execution/bootstrap ownership contract

Normative language effect: **none**.

## Decision boundary

`TOOL005` exists to move repository-owned `.protos` corpora out of Java/JUnit full-corpus runners and into the official `protos test` / TOOL002 lane while retaining Java/JUnit ownership of Java, Truffle, runtime, host and bootstrap implementation evidence.

The Package Tool corpus cutover proved the expanded public Test Tool path, but the final Java-vs-Protos ownership audit found another Java-owned Protos corpus runner:

```text
src/test/java/com/guillermomolina/protos/conformance/
    ProtosProcessSnapshotLanguageConformanceTest.java

protos/tests/conformance/process/manifest.tsv
    15 Protos cases
```

That JUnit class does more than read and execute the manifest. Before every case it creates deterministic guest bootstrap state:

- an exact Process arguments snapshot;
- an exact Environment snapshot and native name-domain behavior;
- a primary `process` capability;
- an independent `otherProcess` capability;
- an `emptyProcess` capability;
- fresh host/runtime objects per test case.

The 15 `.protos` cases therefore cannot simply be enrolled under `protos/test/ordinary` without deciding where this deterministic bootstrap authority belongs in the Test Tool architecture.

D135 decides that ownership boundary.

D135 does **not** change:

- Protos language semantics;
- Standard Library semantics;
- Test Tool assertion syntax or public test syntax;
- D122 suite composition semantics;
- D123 `SuiteId` identity;
- D125 atomic `ExecutionRequirementId` semantics;
- D126 `CorpusId` / `CorpusBinding` ownership;
- D077/D098 resource semantics;
- D129/D133 `CaseAuthority` semantics;
- D108/D114/D116 aggregation, failure, progress or exit classification;
- bounded `--jobs N`;
- physical worker placement or remote execution protocol;
- Java/JUnit ownership of Java/Truffle/runtime/host/bootstrap implementation tests.

## Selected contract — Candidate A′

The deterministic Process-snapshot bootstrap is owned by one dedicated atomic `ExecutionRequirementId`.

The selected repository-owned identities are:

```text
SuiteId                = protos/process-snapshot
CorpusId               = protos/corpus/process-snapshot
ExecutionRequirementId = protos/test/process-snapshot
```

The three identities remain orthogonal:

```text
SuiteId
    -> logical suite identity

CorpusId
    -> logical corpus identity
    -> source authority
    -> TestPlan materialization

ExecutionRequirementId
    -> coherent execution semantics
    -> deterministic guest bootstrap
    -> guest execution
```

The D126 `CorpusBinding` materializes the existing 15-case TestPlan only.

The D125 `ExecutionBinding` for:

```text
protos/test/process-snapshot
```

owns the deterministic execution semantics required by the corpus.

Every admitted test attempt receives fresh equivalent bootstrap state before the selected `.protos` source is executed.

Concrete Java/runtime objects remain implementation details behind the exact host binding and are not persisted in suite, corpus or case descriptors.

## Per-attempt bootstrap contract

For every admitted process-snapshot test attempt, the execution binding creates fresh deterministic state equivalent to the former JUnit harness.

Conceptually:

```text
admit case
    |
    v
fresh semantic execution state
    |
    +-- exact arguments snapshot
    |
    +-- exact Environment snapshot
    |
    +-- primary process capability
    |
    +-- independent otherProcess capability
    |
    +-- emptyProcess capability
    |
    v
execute selected .protos source
```

Required invariants:

```text
BOOTSTRAP_PER_ATTEMPT=FRESH
BOOTSTRAP_SHARED_MUTABLE_STATE=NO
BOOTSTRAP_AMBIENT_DISCOVERY=NO
BOOTSTRAP_PATH_IDENTITY=NO
BOOTSTRAP_HOST_CLASS_IDENTITY_IN_DESCRIPTOR=NO
BOOTSTRAP_PLUGIN_IDENTITY_IN_DESCRIPTOR=NO
```

Parallel execution under `--jobs N` must not cause two cases to share mutable Process, argument or Environment fixture state.

Retries, if introduced by future policy, must receive a fresh bootstrap attempt rather than reusing physical state from a previous attempt.

## CorpusBinding separation

D126 remains authoritative:

```text
CORPUS_BINDING_OWNS_EXECUTION=NO
EXECUTION_BINDING_OWNS_SOURCE=NO
```

Therefore:

```text
protos/corpus/process-snapshot
```

identifies and materializes the logical corpus, but does not select or construct guest bootstrap state.

The physical source root and manifest representation remain host-owned `CorpusBinding` implementation details.

Changing checkout layout, portable-distribution layout or future remote source placement does not rename the `CorpusId`.

## ExecutionRequirement ownership

D125 explicitly defines `ExecutionRequirementId` as the stable logical identity of one coherent execution lane.

The process-snapshot fixture is uniform across the whole motivating corpus and is not currently authored or composed independently from the execution lane.

Therefore the smallest sufficient design is one new atomic execution requirement:

```text
protos/test/process-snapshot
```

Its exact host binding may encapsulate the mechanics required to produce the fresh guest bootstrap and execute the selected source.

No structured persisted execution facets are introduced.

No capability-set algebra is introduced.

No inheritance/default requirement is introduced.

## CaseAuthority remains separate

D129/D133 CaseAuthority remains the mechanism for case-scoped **physical authority** such as an isolated project tree.

D135 does not widen CaseAuthority into a generic fixture/context injection mechanism.

Required invariants:

```text
CASE_AUTHORITY_OWNS_BOOTSTRAP=NO
PROCESS_SNAPSHOT_BOOTSTRAP_IS_CASE_AUTHORITY=NO
CASE_AUTHORITY_DESCRIPTOR_ADDED=NO
```

The fact that both mechanisms may create fresh per-case state does not merge their semantics.

The distinction is:

```text
CaseAuthority
    -> physical case-scoped authority / external capability materialization

ExecutionRequirement
    -> coherent guest execution semantics and bootstrap
```

## D077/D098 resource separation

The Process-snapshot bootstrap is not a schedulable capacity resource and does not participate in D077/D098 accounting.

Required invariants:

```text
PROCESS_SNAPSHOT_BOOTSTRAP_IS_D077_RESOURCE=NO
PROCESS_SNAPSHOT_BOOTSTRAP_HAS_PROVIDER_PROFILE=NO
RESOURCE_CAPACITY_ACCOUNTING_CHANGED=NO
```

No provider/profile identity is introduced.

No resource reservation is created merely to represent guest bootstrap.

## No independent BootstrapFixtureId in generation 1

D135 deliberately does **not** introduce:

```text
BootstrapFixtureId
BootstrapFixtureBinding
fixture capability algebra
fixture composition rules
fixture inheritance
fixture conflict/precedence semantics
```

Current evidence shows one coherent bootstrap lane, not independently authored bootstrap dimensions.

Introducing a separate fixture identity today would require the project to pay for a new durable identity namespace, an additional registry, descriptor composition rules, validation semantics, compatibility rules, combination/conflict semantics, migration rules and additional distributed/portable representation questions.

No current corpus requires that machinery.

## No bootstrap descriptor in TestPlan / CaseSpec

The deterministic fixture is not persisted as bootstrap data in `TestPlan` or `CaseSpec`.

D135 does not add fields describing arguments, environment entries, Process instances, Process variants, host name-domain callbacks or Java/native implementation objects.

Required invariants:

```text
BOOTSTRAP_DESCRIPTOR_IN_TESTPLAN=NO
BOOTSTRAP_DESCRIPTOR_IN_CASESPEC=NO
BOOTSTRAP_HOST_OBJECTS_PERSISTED=NO
```

If future cases genuinely require case-specific bootstrap values, that new evidence must be evaluated separately rather than pre-installing a fixture schema now.

## No test-only Protos API

D135 does not add a privileged public or test-only guest API allowing arbitrary Process snapshot construction merely to eliminate the Java wrapper.

Required invariants:

```text
TEST_ONLY_PROTOS_API=NO
PUBLIC_PROCESS_FACTORY_ADDED=NO
LANGUAGE_SEMANTICS_CHANGED=NO
STANDARD_LIBRARY_SURFACE_CHANGED=NO
```

If Process-construction capability becomes independently useful to normal Protos programs, it must be designed for that real language/runtime requirement rather than smuggled in as testing infrastructure.

## Java / Protos test-lane consequence

The architectural target remains:

```text
Java / JUnit
    -> Java implementation
    -> Truffle
    -> runtime
    -> host
    -> bootstrap implementation mechanics

Protos / TOOL002
    -> bin/protos test --jobs N
    -> repository-owned .protos semantic/conformance corpora
```

After the D135 consumer implementation is green:

- the 15 process-snapshot `.protos` cases are owned by `protos test`;
- the Java/JUnit full-corpus runner for those cases is no longer required;
- genuine Java/bootstrap implementation tests remain Java/JUnit-owned;
- no semantic corpus must remain dependent on Java solely for corpus iteration and expected-result interpretation.

This separation is required for TOOL005 closure.

## Candidate set and decision rationale

### Candidate A′ — dedicated atomic ExecutionRequirementId

**Selected.**

One new logical execution requirement owns the coherent Process-snapshot bootstrap lane.

### Candidate B — CorpusBinding owns bootstrap execution

Rejected because it violates D126:

```text
CORPUS_BINDING_OWNS_EXECUTION=NO
```

and would make corpus identity select execution semantics.

### Candidate C — CaseAuthority owns bootstrap fixture

Rejected for generation 1 because it would widen CaseAuthority from physical authority to generic fixture/context injection and duplicate one identical descriptor across all 15 cases.

### Candidate D′ — independent BootstrapFixtureId axis

Deferred. Architecturally credible if independently composable bootstrap dimensions become real, but overengineered for current evidence.

### Candidate E — bootstrap descriptor in TestPlan / CaseSpec

Rejected for current requirements because it exposes implementation-oriented bootstrap schema despite the entire current corpus sharing one coherent setup.

### Candidate F — guest self-bootstrap / test-only helper API

Rejected because it would add guest-visible testing machinery or language/runtime capability solely to avoid a runner binding.

### Candidate G — retain Java/JUnit full-corpus runner

Rejected because it fails TOOL005's explicit goal and prevents clean Java-vs-Protos test-lane separation.

## Comparative scoring

Scores: 1 = poor, 5 = strongest. Confidence: H = high, M = medium.

| Criterion | A′ ExecutionRequirement | C CaseAuthority | D′ BootstrapFixtureId | E CaseSpec descriptor |
|---|---:|---:|---:|---:|
| Correctness / invariant preservation | 5 H | 4 H | 5 H | 4 M |
| Protos alignment | 5 H | 3 H | 4 H | 3 M |
| Present-need proportionality | 5 H | 3 H | 2 H | 1 H |
| Incremental growth | 5 H | 3 M | 5 H | 4 M |
| Future-option resilience | 4 H | 4 M | 5 H | 5 M |
| Scalability | 4 H | 4 M | 5 H | 4 M |
| Conceptual simplicity | 5 H | 3 H | 2 H | 1 H |
| Portability / implementation freedom | 5 H | 4 H | 5 H | 4 M |
| Runtime / resource cost | 5 H | 4 H | 4 H | 4 M |
| Failure / operability | 5 H | 4 H | 4 M | 3 M |
| Deferral / reversibility / migration | 4 H | 3 M | 5 M | 2 M |
| Evidence maturity / implementation risk | 5 H | 4 H | 3 M | 3 M |

Arithmetic totals:

```text
A′ = 57 / 60
C  = 43 / 60
D′ = 49 / 60
E  = 38 / 60
```

The totals are comparison aids only.

Candidate D′ carries a qualitative overengineering red flag because it makes the project pay now for independent fixture composition that no current corpus requires.

Candidate A′ has the strongest present proportionality while preserving an explicit future escape path.

## Incremental-design analysis

### Pay for what you need

Candidate A′ adds exactly one thing required today:

```text
protos/test/process-snapshot
```

No current user, corpus author or implementation pays for a second fixture identity namespace, new descriptor algebra or conflict solver.

### Grow as you need

If future corpora demonstrate independently composable dimensions such as Actor execution × Process snapshot fixture × Package execution × OS-specific substrate, existing atomic requirement IDs remain valid.

A later Dxxx may introduce an independently authored fixture axis while keeping existing `SuiteId`, `CorpusId`, D077/D098 resource semantics, CaseAuthority semantics and `protos/test/process-snapshot` as a valid coherent lane.

### Cost of deferral

Deferring `BootstrapFixtureId` does **not** require a foundational rewrite today.

If independent composition becomes real later, the bounded migration would be:

1. define the new fixture identity contract;
2. add a fixture registry/binding layer;
3. extend executable leaf descriptors;
4. migrate only leaves that benefit from independent composition;
5. retain existing atomic requirements as valid coherent bindings.

The migration does not require changing Protos language semantics, corpus identity, test files, D077 resources or CaseAuthority.

### Smallest sufficient solution

The smallest design satisfying all current requirements is:

```text
one new ExecutionRequirementId
one exact host binding
fresh bootstrap per admitted attempt
one new CorpusId / suite leaf
no additional semantic axis
```

### Speculation burden of proof

A possible future explosion such as:

```text
protos/test/process-snapshot
protos/test/actor-process-snapshot
protos/test/package-process-snapshot
protos/test/group-process-snapshot
...
```

would be concrete evidence that independent execution/bootstrap axes are multiplying combinatorially.

That is the trigger for a future structural decision.

The mere possibility of that future is not sufficient evidence to install the fixture algebra in generation 1.

## Regret scenario

The strongest credible regret scenario for A′ is requirement-ID proliferation caused by real independent combinations of execution and bootstrap semantics.

If that pattern appears, the atomic token model would begin encoding a Cartesian product of independent dimensions.

At that point the project should allocate a new Dxxx rather than continue minting combination IDs.

D135 explicitly treats that as a future decision trigger.

## Failure semantics

D135 introduces no new outcome class.

Bootstrap implementation failures remain execution/harness infrastructure failures under the existing Test Tool execution/infrastructure contract.

Guest failure remains guest failure.

No guest CaseRun may be fabricated when execution fails before a guest observation exists.

D108/D114/D116 remain authoritative for aggregation and invocation outcome.

D135 does not change D134 CaseAuthority lifecycle failure semantics because the Process-snapshot bootstrap is not CaseAuthority.

## Portable distribution and remote execution

The selected logical identity:

```text
protos/test/process-snapshot
```

does not identify Java implementation classes, repository paths, worker processes, plugin names or local machine state.

A checkout, extracted portable distribution or future remote worker may provide an authorized exact binding for the same logical requirement.

Physical placement does not rename the requirement.

This preserves TEST001-H's portable-distribution objective.

## Ratified invariants

```text
D135_SELECTED_CANDIDATE=A_PRIME

SUITE_ID=protos/process-snapshot
CORPUS_ID=protos/corpus/process-snapshot
EXECUTION_REQUIREMENT_ID=protos/test/process-snapshot

BOOTSTRAP_OWNERSHIP=EXECUTION_REQUIREMENT
BOOTSTRAP_PER_ATTEMPT=FRESH
BOOTSTRAP_SHARED_MUTABLE_STATE=NO
BOOTSTRAP_AMBIENT_DISCOVERY=NO

CORPUS_BINDING_OWNS_BOOTSTRAP=NO
CASE_AUTHORITY_OWNS_BOOTSTRAP=NO
PROCESS_SNAPSHOT_BOOTSTRAP_IS_CASE_AUTHORITY=NO

PROCESS_SNAPSHOT_BOOTSTRAP_IS_D077_RESOURCE=NO
RESOURCE_CAPACITY_ACCOUNTING_CHANGED=NO

BOOTSTRAP_FIXTURE_ID_INITIAL=NO
BOOTSTRAP_DESCRIPTOR_IN_TESTPLAN=NO
BOOTSTRAP_DESCRIPTOR_IN_CASESPEC=NO

TEST_ONLY_PROTOS_API=NO
LANGUAGE_SEMANTICS_CHANGED=NO
STANDARD_LIBRARY_SURFACE_CHANGED=NO

JAVA_FULL_CORPUS_RUNNER_AFTER_CUTOVER=NO
AUTOMATIC_TEST_CI_REACTIVATED_BY_D135=NO
```

## TOOL005 implementation consequence

D135 releases the final Process-snapshot corpus migration work in TOOL005.

The dependent implementation may now:

1. reserve `SuiteId = protos/process-snapshot`;
2. reserve `CorpusId = protos/corpus/process-snapshot`;
3. reserve `ExecutionRequirementId = protos/test/process-snapshot`;
4. add an exact `CorpusBinding` that materializes the existing 15-case `protos/tests/conformance/process/manifest.tsv` plan;
5. add an exact execution binding whose per-attempt execution creates the deterministic fresh Process/arguments/Environment bootstrap;
6. enroll the suite in `RepositorySuite`;
7. prove the 15 cases through `bin/protos test --jobs N`;
8. retire the Java/JUnit full-corpus runner for those `.protos` cases after the public path is green;
9. preserve genuinely host/runtime-specific bootstrap mechanism tests as Java/JUnit tests;
10. re-audit `src/test/java/**` for any remaining Java class acting as a repository-owned `.protos` full-corpus runner before declaring TOOL005 closed.

## CI consequence

D135 does not reactivate automatic CI.

The desired eventual CI architecture is explicitly two-lane:

```text
Java/JUnit lane
    -> Java / Truffle / runtime / host / bootstrap implementation tests

Protos lane
    -> protos test --jobs N
    -> repository-owned Protos corpora
```

Automatic test CI remains suspended until that separation is complete and the GITHUB017 reactivation audit explicitly validates the final two-lane workflow.

## Approval provenance

Candidate A′ was explicitly approved by the project owner in the active design interaction on **2026-09-15**.

The approval applies exactly to:

```text
D135_SELECTED_CANDIDATE=A_PRIME
```

with deterministic Process-snapshot guest bootstrap owned by the dedicated atomic `ExecutionRequirementId`:

```text
protos/test/process-snapshot
```

No approval is inferred for any later fixture algebra, new public Protos API, resource model, CaseAuthority widening or CI reactivation.
