# D126 — Test Tool corpus identity and plan-source binding contract

Status: **RATIFIED — Candidate D′ selected**

Allocated: **2026-09-13**

Explicit project-owner approval: **2026-09-13**

Decision issue: GitHub #495

Primary consumer: `TOOL005` / GitHub #468 post-A3 corpus expansion

Parent architectural authority: `D122` / GitHub #469, `D123` / GitHub #488,
and `D125` / GitHub #490

Nature: implementation-independent Test Tool corpus identity, source authority and
plan-materialization contract

Normative language effect: **none**.

## Decision boundary

D122 ratified an explicit composable Test Tool suite graph. D123 fixed stable
logical `SuiteId` identity. D125 fixed one mandatory atomic logical
`ExecutionRequirementId` per executable leaf and required exact
invocation-scoped immutable host binding.

TOOL005-A3 is now graph-driven and preserves D108/D114/D116 execution,
aggregation and failure semantics. The next corpus-expansion step exposes a
boundary that generation-1 A3 could temporarily hide: the initial
`ExecutionBinding` carries both execution mechanics and corpus/plan-source
mechanics (`filesystem` plus `planLoader`).

That arrangement is not durable because D125 explicitly allows multiple suites
to share one execution requirement. Distinct ordinary corpora such as URI and
CSV must be able to share `protos/test/ordinary` without sharing the same
physical corpus root.

D126 therefore fixes corpus identity and plan-source resolution as an
independent logical axis.

D126 does not change:

- Protos language or Standard Library semantics;
- D122 suite composition;
- D123 `SuiteId`;
- D125 `ExecutionRequirementId` identity or execution-lane semantics;
- D077/D098 resource semantics;
- TestPlan / CaseSpec semantics;
- D108/D114/D116 execution, aggregation, failure and exit classification;
- bounded `--jobs N`;
- progress/presentation policy;
- physical worker placement or remote-execution protocol;
- Java/JUnit ownership of Java/Truffle/runtime/host implementation tests.

## Selected contract — Candidate D′

Every executable suite leaf declares exactly one mandatory inert logical
`corpus`, identified by a `CorpusId`, independently of its `SuiteId` and
`ExecutionRequirementId`.

Conceptually:

```text
Executable suite leaf
  +-- SuiteId
  +-- CorpusId
  `-- ExecutionRequirementId
         |                 |
         | exact lookup    | exact lookup
         v                 v
   CorpusBinding       ExecutionBinding
         |                 |
         |                 +-- ordinary execution
         |                 +-- inspection
         |                 +-- resourceful execution
         |                 `-- resourceful inspection
         |
         `-- TestPlan materialization
               +-- source authority
               `-- loader/materializer mechanics
```

Persisted suite descriptors contain only logical identities. Concrete filesystem
objects, paths, URLs, Java classes, plugins, callbacks, workers and credentials
remain outside the descriptor.

## Concept naming

The selected public/internal concept names are:

```text
corpus
CorpusId
CorpusBinding
```

`CorpusId` identifies the logical corpus whose TestPlan must be materialized. It
does not identify:

- where that corpus is physically stored;
- which loader implementation materializes it;
- which execution lane runs it;
- which worker receives it;
- a specific content snapshot or digest.

## Mandatory leaf-local semantics

Every executable leaf has exactly one explicit corpus identity.

```text
CORPUS_PER_EXECUTABLE_LEAF=EXACTLY_ONE
CORPUS_MANDATORY=YES
DEFAULT_CORPUS=NO
CORPUS_INHERITANCE=NO
```

There is no inferred corpus from SuiteId, graph ancestry, filesystem layout,
execution requirement or an ambient default.

Reparenting a leaf does not change its corpus identity. Non-executable
composition-only suite nodes do not inherit or synthesize corpus identity.

## CorpusId identity

`CorpusId` is a stable logical identity in a semantic namespace separate from
`SuiteId`, `ExecutionRequirementId`, D077 resource keys and D098
provider/profile.

Generation 1 mechanically reuses the proven D123 lexical grammar:

```text
CorpusId := segment ("/" segment)*
segment := [a-z0-9][a-z0-9._-]*
```

Grammar reuse does not merge namespaces.

A `CorpusId` is not and must not be derived from or interpreted as:

- `SuiteId`;
- `ExecutionRequirementId`;
- filesystem or repository path;
- manifest filename;
- module/import path;
- URL;
- Java/native class, callback, plugin or service identity;
- executable/shared-library path;
- graph ancestry;
- D077 resource key;
- D098 provider/profile;
- worker identity or placement;
- content digest.

Unknown, malformed or unsupported corpus IDs fail closed. There is no
same-looking path fallback, nearest corpus, recursive discovery, SuiteId
fallback or ordinary-corpus default.

## Initial repository-owned CorpusIds

Generation 1 reserves exactly these repository-owned corpus identities:

```text
protos/corpus/conformance
protos/corpus/actor
protos/corpus/group
protos/corpus/package-toml
```

These names are logical identities only. Their slash-separated spelling is
namespace structure, not a filesystem hierarchy.

Future library/package/filesystem corpora receive additional explicit CorpusIds
when admitted by bounded TOOL005 slices; D126 does not pre-reserve those future
names.

No rule in D126 grants external packages authority to mint identities under the
`protos/...` corpus namespace.

## CorpusBinding registry

The selected host model is an explicit invocation-scoped immutable registry:

```text
CorpusId
   |
   | exact lookup
   v
CorpusBinding
```

Required semantics:

```text
HOST_CORPUS_BINDING=EXACT
HOST_CORPUS_REGISTRY=INVOCATION_SCOPED_IMMUTABLE
UNKNOWN_CORPUS=FAIL_CLOSED
MISSING_CORPUS=FAIL_CLOSED
DUPLICATE_CORPUS_BINDING=FAIL_CLOSED
VALIDATE_ALL_CORPUS_BINDINGS_BEFORE_SCHEDULING=YES
MATERIALIZE_ALL_CURRENT_PLANS_BEFORE_SCHEDULING=YES
```

The host constructs the complete corpus registry for one Test Tool invocation
before scheduling. Registry mutation during the invocation is outside D126.

Before any TestPlan case is admitted, every executable leaf's CorpusId and
ExecutionRequirementId must resolve successfully. Current plans are materialized
before case scheduling, preserving D122/D125 plan-before-scheduling behavior.

## CorpusBinding authority

A `CorpusBinding` is host-owned authority capable of materializing the TestPlan
for one logical CorpusId in the current invocation.

Generation 1 may implement a concrete binding using facilities such as:

```text
CorpusBinding
  +-- confined filesystem/source authority
  `-- plan loader/materializer
```

Those fields are implementation facts, not persisted corpus facets.

The durable meaning is:

> materialize the logical corpus identified by this CorpusId as the TestPlan
> consumed by the already-ratified Test Tool execution pipeline.

A future binding may instead materialize from:

- bundled distribution resources;
- package resources;
- a content-addressed snapshot;
- a remote source authority;
- a pre-materialized/serialized plan representation,

without renaming the CorpusId when the logical corpus is unchanged.

## Execution/corpus separation

D126 makes the following partition normative for Test Tool architecture:

```text
CorpusBinding
  -> source authority
  -> TestPlan materialization

ExecutionBinding
  -> ordinary execution
  -> inspection
  -> resourceful execution
  -> resourceful inspection
```

Required separation:

```text
EXECUTION_BINDING_OWNS_SOURCE=NO
CORPUS_BINDING_OWNS_EXECUTION=NO
```

This is a narrow refinement of D125's generation-1 concrete-binding allowance.
D125's logical `ExecutionRequirementId` contract remains ratified unchanged.
Only the temporary permission for a concrete `ExecutionBinding` to encapsulate
source/plan-loader mechanics is superseded by D126.

Creating `ordinary-uri`, `ordinary-csv` or similar requirement identities merely
to select different corpora is forbidden by this separation.

## Plan materialization and loader identity

Generation 1 does not persist an independent `PlanLoaderId`.

```text
PLAN_MATERIALIZATION_BELONGS_TO_CORPUS_BINDING=YES
PLAN_LOADER_ID_PERSISTED_INITIAL=NO
```

Current evidence does not show loader semantics varying independently from corpus
source authority in a way suite authors need to compose.

If later corpora demonstrate an independently authorable loader dimension, a
future Dxxx may introduce `PlanLoaderId` or a richer materialization contract
without renaming existing CorpusIds.

## Sharing and aliasing

D126 permits:

```text
MULTIPLE_SUITES_MAY_SHARE_CORPUS=YES
MULTIPLE_CORPUS_IDS_MAY_SHARE_PHYSICAL_SOURCE_AUTHORITY=YES
```

Two suites may refer to the same logical corpus without becoming the same
SuiteId. Conversely, two distinct CorpusIds may be backed by the same physical
source authority or host implementation while retaining distinct logical
identities.

Physical binding reuse never creates logical aliases.

## Checkout, portable distribution and remote placement

Physical location is not corpus identity.

```text
CHECKOUT_LOCATION_CHANGES_CORPUS_ID=NO
PORTABLE_DISTRIBUTION_CHANGES_CORPUS_ID=NO
REMOTE_PLACEMENT_CHANGES_CORPUS_ID=NO
```

The same CorpusId may be materialized from a repository checkout, an extracted
portable distribution or a future authorized remote source.

Worker/controller protocols exchange or resolve logical corpus/plan data. D126
selects no remote protocol, transport, cache key or placement policy.

## Prior-art basis

The audit compared corpus/source/execution models across materially different
ecosystems:

- Bazel target labels, sources, toolchains, execution groups and platforms;
- Buck2 targets, sources, external test execution and execution modifiers;
- Pants targets/sources and explicit environments;
- Gradle JVM Test Suites, SourceSets and execution targets;
- Rust Cargo/libtest targets and paths;
- SwiftPM test targets/sources;
- Go package/import-path testing;
- Microsoft.Testing.Platform and `dotnet test`;
- JUnit Platform discovery selectors and UniqueIds;
- pytest collection/rootdir/nodeids;
- CTest test commands, working directories and resources;
- Meson tests and named test setups;
- Maven Surefire provider/classpath discovery;
- Jest projects/roots/globs;
- Vitest projects/includes;
- Erlang Common Test suites/directories/configuration.

Strongest transferable findings:

1. Bazel, Buck2 and Pants separate logical work identity/source membership from
   execution-platform/environment selection.
2. Gradle provides the closest direct analogue: logical test suite, SourceSet and
   execution targets are separate concepts.
3. Buck2's execution-platform/modifier evolution warns against creating
   combinatorial requirement identities such as one execution requirement per
   corpus.
4. Cargo, SwiftPM and Go demonstrate that path-coupled models can be highly
   successful when workspace layout is intentionally semantic, but that is not
   the already-ratified Protos descriptor model.
5. JUnit, pytest, CTest, Meson, Maven, Jest and Vitest provide flexible
   path/class/module/executable/glob selection, but those locators are too
   physical or ambient to become portable Protos corpus identity.
6. Content-addressed identity is useful as a physical/cache layer but identifies
   one content version rather than the durable semantic corpus.

## Candidate comparison

Owner-requested axes, scored 1–10:

| Candidate | Aguante de futuro | Escalabilidad | Filosofía Protos |
| --- | ---: | ---: | ---: |
| A — `SuiteId -> source binding` | 6.5 | 7.5 | 5 |
| B — source in `ExecutionRequirementId` binding | 4 | 5 | 3 |
| C — persisted relative path + loader | 7.5 | 8 | 6 |
| **D′ — explicit `CorpusId` + exact host `CorpusBinding`** | **10** | **10** | **10** |
| E — persisted `CorpusId + PlanLoaderId` | 9.5 | 10 | 8.5 |
| F — `TestPlanId -> materializer` | 10 | 10 | 9 |
| G — content-addressed corpus identity | 9.5 | 10 | 7 |
| H — module/import identity | 8.5 | 9 | 8 |
| I — source capability/facet algebra | 10 | 10 | 7.5 |

## Strongest argument against D′

For many generation-1 leaves, `SuiteId` and `CorpusId` will be one-to-one. Bazel,
Buck2, Pants, Cargo and SwiftPM commonly avoid a second logical ID by attaching
sources directly to the target.

That simplification is attractive but incompatible with the Protos constraints
already ratified by D122/D123/D125: persisted suite metadata must not gain host
path/source identity, and `SuiteId` must not become a host source-routing key.

`CorpusId` buys three concrete freedoms:

1. suite composition can change without renaming the corpus;
2. one corpus can be reused by multiple suite leaves;
3. checkout/bundle/remote source binding can change without converting SuiteId
   into a locator.

## Regret scenario and escape paths

D′ deliberately preserves several future extensions without changing current
identities.

If loader semantics become an independently composable dimension:

```text
CorpusId + future PlanLoaderId
```

may be introduced by a later explicit decision.

If content-addressed distribution becomes useful, a `CorpusBinding` may resolve
CorpusId to a digest/snapshot without making the digest the durable identity.

If a corpus is naturally module-backed, its `CorpusBinding` may use module
resolution internally without making module paths universal CorpusIds.

If remote workers consume serialized plans, the binding may materialize a
transportable plan representation while preserving the same CorpusId.

## Ratified invariants

```text
D126_SELECTED_CANDIDATE=D_PRIME
CONCEPT_NAME=corpus
IDENTITY_NAME=CorpusId

CORPUS_PER_EXECUTABLE_LEAF=EXACTLY_ONE
CORPUS_MANDATORY=YES
DEFAULT_CORPUS=NO
CORPUS_INHERITANCE=NO

CORPUS_ID=STABLE_LOGICAL_IDENTITY
CORPUS_ID_GRAMMAR=segment(/segment)*
CORPUS_ID_NAMESPACE=SEPARATE
CORPUS_ID_IS_SUITE_ID=NO
CORPUS_ID_IS_EXECUTION_REQUIREMENT=NO
CORPUS_ID_IS_PATH=NO
CORPUS_ID_IS_URL=NO
CORPUS_ID_IS_MODULE_PATH=NO
CORPUS_ID_IS_HOST_IMPLEMENTATION=NO
CORPUS_ID_IS_CONTENT_DIGEST=NO

HOST_CORPUS_BINDING=EXACT
HOST_CORPUS_REGISTRY=INVOCATION_SCOPED_IMMUTABLE
UNKNOWN_CORPUS=FAIL_CLOSED
MISSING_CORPUS=FAIL_CLOSED
DUPLICATE_CORPUS_BINDING=FAIL_CLOSED
VALIDATE_ALL_CORPUS_BINDINGS_BEFORE_SCHEDULING=YES
MATERIALIZE_ALL_CURRENT_PLANS_BEFORE_SCHEDULING=YES

EXECUTION_BINDING_OWNS_SOURCE=NO
CORPUS_BINDING_OWNS_EXECUTION=NO
PLAN_MATERIALIZATION_BELONGS_TO_CORPUS_BINDING=YES
PLAN_LOADER_ID_PERSISTED_INITIAL=NO

MULTIPLE_SUITES_MAY_SHARE_CORPUS=YES
MULTIPLE_CORPUS_IDS_MAY_SHARE_PHYSICAL_SOURCE_AUTHORITY=YES

CHECKOUT_LOCATION_CHANGES_CORPUS_ID=NO
PORTABLE_DISTRIBUTION_CHANGES_CORPUS_ID=NO
REMOTE_PLACEMENT_CHANGES_CORPUS_ID=NO

INITIAL_CONFORMANCE_CORPUS=protos/corpus/conformance
INITIAL_ACTOR_CORPUS=protos/corpus/actor
INITIAL_GROUP_CORPUS=protos/corpus/group
INITIAL_PACKAGE_TOML_CORPUS=protos/corpus/package-toml
```

## Implementation consequence

After this governance record is published, TOOL005 may resume post-A3 expansion
with a bounded foundation slice that:

1. extends executable leaves with mandatory `CorpusId`;
2. installs an invocation-scoped immutable `CorpusBinding` registry;
3. migrates current filesystem/plan-loader authority out of
   `ExecutionBinding`;
4. validates all CorpusIds and ExecutionRequirementIds before scheduling;
5. materializes the current plans before case scheduling;
6. preserves the current four-suite corpus membership and all D108/D114/D116
   behavior.

Only after that foundation is closed should TOOL005 add new Java-routed Protos
corpora incrementally.

D126 does not authorize recursive discovery, path inference, persisted
PlanLoaderId, capability/facet source solving, corpus expansion inside the
ratification slice, remote protocol design, or changes to D077/D098.
