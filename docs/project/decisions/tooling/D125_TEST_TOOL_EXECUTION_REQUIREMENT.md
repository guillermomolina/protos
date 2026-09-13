# D125 — Test Tool suite execution-requirement identity and host binding contract

Status: **RATIFIED — Candidate B′ selected**

Allocated: **2026-09-13**

Explicit project-owner approval: **2026-09-13**

Decision issue: GitHub #490

Primary consumer: `TOOL005-A3` / GitHub #477

Parent architectural authority: `D122` / GitHub #469 and `D123` / GitHub #488

Nature: implementation-independent Test Tool execution-requirement and host-binding contract

Normative language effect: **none**.

## Decision boundary

D122 ratified an explicit composable Test Tool suite graph with inert serializable
metadata and explicitly separated suite-side mechanical execution requirements
from D077/D098 case-level resources. D123 then fixed stable `SuiteId` identity.

TOOL005-A2 materialized the current repository root plus the four current corpus
leaves. TOOL005-A3 must now make execution graph-driven without replacing the
existing four hard-coded execution blocks by an equivalent `SuiteId` switch.

D125 closes the remaining suite-side execution requirement boundary. It does not
change:

- Protos language or Standard Library semantics;
- `SuiteId` identity under D123;
- TestPlan / CaseSpec semantics;
- D077 resource requirements or D098 provider/profile semantics;
- bounded `--jobs N` semantics;
- progress, final result or exit classification;
- physical worker placement or remote-execution protocol;
- Java/JUnit ownership of Java/Truffle/runtime/host implementation tests.

## Selected contract — Candidate B′

Every executable corpus leaf declares exactly one mandatory, atomic, inert
logical `executionRequirement` identified by an `ExecutionRequirementId`.

Conceptually:

```text
Suite leaf
  +-- SuiteId
  +-- corpus / plan input
  `-- ExecutionRequirementId
             |
             | exact lookup
             v
    invocation-scoped immutable host binding
             |
             +-- confined source / plan-loader mechanics
             +-- selected Prelude / resolver
             +-- ordinary execution + inspection
             `-- resourceful execution + inspection
```

The descriptor contains only the logical requirement identity. Concrete host
objects and implementation details remain outside persisted suite metadata.

## Concept naming

The selected public/internal concept names are:

```text
executionRequirement
ExecutionRequirementId
```

D125 explicitly rejects these names for this concept:

- `profile` — already owned by D098 as a provider-scoped resource identity;
- `provider` — already owned by D098 and implies concrete provisioning;
- `runner` / `engine` — imply executable implementation identity;
- `target` / `platform` — imply physical placement;
- `environment` — is too broad and would invite resource/configuration coupling.

## Mandatory leaf-local semantics

Every executable corpus leaf has exactly one explicit requirement.

```text
REQUIREMENT_PER_EXECUTABLE_LEAF=EXACTLY_ONE
REQUIREMENT_MANDATORY=YES
DEFAULT_REQUIREMENT=NO
INHERITANCE=NO
```

There is no implicit ordinary/default route. A new executable leaf that omits a
requirement fails validation before any case starts.

Requirements are leaf-local. Reparenting a leaf in the D122 suite graph never
changes its execution meaning.

Non-executable composition-only suite nodes do not acquire inherited execution
requirements.

## Atomic generation-1 requirement

Generation 1 uses one atomic logical token rather than a set of independently
composable capabilities or a persisted structure such as loader/prelude/route.

```text
CAPABILITY_SET_INITIAL=NO
STRUCTURED_FACETS_INITIAL=NO
```

The current repository provides evidence for coherent execution lanes, not for
independently recombinable loader, Prelude, filesystem, inspection and routing
axes. Persisting those current host facets would overfit implementation structure;
introducing a capability algebra would prematurely require compatibility,
conflict, precedence and satisfaction semantics.

A future implementation behind one stable `ExecutionRequirementId` may itself
become more sophisticated without changing suite descriptors.

## ExecutionRequirementId identity

`ExecutionRequirementId` is a stable logical identity in a namespace separate
from `SuiteId`, D077 resource keys, and D098 provider/profile.

Generation 1 reuses D123's proven portable lexical grammar mechanically:

```text
ExecutionRequirementId := segment ("/" segment)*
segment := [a-z0-9][a-z0-9._-]*
```

Grammar reuse does not merge semantic namespaces.

An `ExecutionRequirementId` is not and must not be derived from:

- `SuiteId`;
- filesystem path;
- corpus path or manifest path;
- graph ancestry;
- Java class, slot, plugin, service or callback identity;
- executable or shared-library path;
- URL or repository locator;
- D077 resource key;
- D098 provider or profile;
- worker identity or physical placement.

Unknown, malformed or unsupported requirement IDs fail closed rather than being
normalized, guessed or mapped to a nearby/default requirement.

## Initial repository-owned requirement identities

Generation 1 reserves exactly these repository-owned requirement IDs:

```text
protos/test/ordinary
protos/test/actor
protos/test/group
protos/test/package
```

Their initial meanings are logical execution lanes:

- `protos/test/ordinary` — current ordinary Test Tool conformance execution lane;
- `protos/test/actor` — current Actor-overlay execution lane;
- `protos/test/group` — current Group-overlay execution lane;
- `protos/test/package` — current Package Tool execution lane, including the
  currently required package-side plan/loading mechanics.

These names identify execution semantics, not the current corpus identities.
For example, `protos/package-toml` remains the current SuiteId while
`protos/test/package` is its execution requirement.

Future suites may share an existing requirement. Two different requirements may
also resolve to the same concrete host binding without becoming aliases.

No rule in D125 grants external packages authority to mint identities under the
`protos/...` requirement namespace. Future package/federation namespace authority
remains a separate decision when a real consumer requires it.

## Corpus and plan identity remain separate

The corpus/plan input and its execution requirement are distinct properties.

Conceptually:

```text
suite A -> corpus A -> requirement X
suite B -> corpus B -> requirement X
```

The requirement must not become a second SuiteId or corpus identity. Changing a
corpus path or splitting/merging suite composition does not inherently rename the
execution requirement.

## Host binding registry

The selected host model is one explicit invocation-scoped immutable registry:

```text
ExecutionRequirementId
        |
        | exact lookup
        v
ExecutionBinding
```

Required semantics:

```text
HOST_BINDING=EXACT
HOST_REGISTRY=INVOCATION_SCOPED_IMMUTABLE
UNKNOWN_REQUIREMENT=FAIL_CLOSED
MISSING_REQUIREMENT=FAIL_CLOSED
DUPLICATE_BINDING=FAIL_CLOSED
VALIDATE_BEFORE_SCHEDULING=YES
```

The complete registry for one Test Tool invocation is constructed before suite
planning/scheduling begins. Registry mutation during the invocation is not part
of D125.

Planning validates that every executable leaf's requirement can be satisfied
before admitting any case. There is no fallback from an unknown requirement to
the ordinary lane and no discovery from whatever Java classes/plugins happen to
be present.

## Concrete binding boundary

A concrete `ExecutionBinding` is host-owned and may encapsulate the coherent
mechanics needed to materialize and execute a plan, including as appropriate:

- confined source authority;
- plan/manifest loading mechanics;
- selected Prelude / resolver;
- ordinary async execution;
- ordinary inspection;
- resourceful async execution;
- resourceful inspection.

These are implementation facts behind the logical binding. D125 does not persist
those individual facets and does not expose Java/native implementation identity
through the suite graph.

Replacing one binding implementation without changing its logical contract does
not rename the requirement or suite.

## Resource separation

D125 does not duplicate or absorb D077/D098.

Conceptually:

```text
Suite leaf
  +-- ExecutionRequirementId  -> mechanical execution lane
  `-- TestPlan / CaseSpec
          `-- D077 resources
                  `-- D098 provider/profile catalog
```

A case may request a capacity-constrained resource independently of the suite's
execution requirement. The requirement cannot use D098 `profile` as an alias or
secondary namespace.

Presentation, progress labels and final result classification remain separate as
well.

## Placement and remote execution

Physical placement does not redefine an execution requirement.

The same logical requirement may be satisfied by an authorized local, isolated
OS or remote-worker environment. A worker/controller exchanges logical plan data,
not Java classes, plugin names, executable paths, URLs or worker-specific
implementation identifiers.

Conceptually:

```text
resolved leaf requirement R
          |
          +-- local worker  -> local authorized binding for R
          +-- OS worker     -> environment binding for R
          `-- remote worker -> environment binding for R
```

D125 selects no remote protocol and no placement policy.

## Prior-art basis

The audit compared materially different execution-requirement/binding models
across:

- Kubernetes Dynamic Resource Allocation (DRA);
- Bazel toolchains, execution groups and execution platforms;
- Buck2 toolchains, exec dependencies, execution platforms and execution
  modifiers;
- CTest fixtures, resources and environment;
- Meson named test setups;
- Gradle JVM Test Suites and targets/toolchains;
- Maven Surefire/Failsafe provider/engine selection;
- pytest fixtures/markers/plugins and pytest-xdist;
- JUnit Platform TestEngine/configuration separation;
- Microsoft.Testing.Platform capabilities;
- Erlang Common Test configuration/groups/distributed execution;
- Elixir ExUnit CaseTemplate/setup;
- Rust Cargo test targets/harness/required features;
- SwiftPM test targets/settings.

The strongest transferable findings were:

1. Kubernetes DRA, Bazel and Buck2 strongly separate logical requirements from
   concrete environment/provider implementation.
2. Bazel/Buck2 prove the model can scale to remote execution, but Buck2 also
   provides direct evidence that unrestricted multi-axis execution-platform
   combinations can create configuration explosion.
3. CTest demonstrates that execution setup, resources and scheduling constraints
   should remain distinct concepts rather than one universal profile.
4. Meson and Gradle reinforce that suite identity and execution context are
   distinct.
5. JUnit/Maven/pytest demonstrate useful extension models but rely on executable
   engine/plugin/fixture discovery, which is too much implementation authority for
   inert Protos suite descriptors.
6. Microsoft.Testing.Platform demonstrates replaceable capability/binding
   evolution, while its host interfaces remain implementation-side rather than
   persisted suite identity.

## Candidate comparison

Owner-requested principal axes, scored 1–10:

| Candidate | Aguante de futuro | Escalabilidad | Filosofía Protos |
| --- | ---: | ---: | ---: |
| A — SuiteId directly selects host route | 4 | 4 | 3 |
| **B′ — mandatory atomic logical requirement + exact host binding** | **10** | **10** | **10** |
| C — unordered capability set composed by host | 9 | 10 | 7 |
| D — structured persisted loader/prelude/route facets | 9 | 9 | 7 |
| E — parent-suite inherited requirement | 7 | 8 | 5 |
| F — separate SuiteId -> requirement registry | 8 | 8 | 6 |
| G — Bazel-like multi-axis toolchain/platform model | 10 | 10 | 8 |

## Strongest argument against B′

Atomic requirements can multiply if future suites genuinely require independent
combinations such as Actor semantics plus a distinct package loader plus a
special OS substrate. A structured/capability model could represent those axes
without minting a token for every combination.

D125 deliberately does not pay that complexity cost before real corpus evidence
shows those axes are independently authorable. The current four arrangements are
coherent execution lanes.

## Regret scenario and escape path

If future corpus families prove that several execution dimensions vary
independently, existing B′ descriptors remain valid.

A registered `ExecutionRequirementId` may first resolve internally through a
richer toolchain/capability solver without changing persisted suite metadata. If
suite authors eventually need to express those dimensions independently, a later
explicit Dxxx may extend the descriptor contract.

That escape path preserves existing stable requirement IDs, SuiteIds, TestPlans
and D077/D098 resource semantics.

## Ratified invariants

```text
D125_SELECTED_CANDIDATE=B_PRIME
CONCEPT_NAME=executionRequirement
IDENTITY_NAME=ExecutionRequirementId
REQUIREMENT_PER_EXECUTABLE_LEAF=EXACTLY_ONE
REQUIREMENT_MANDATORY=YES
DEFAULT_REQUIREMENT=NO
INHERITANCE=NO
REQUIREMENT_ID=STABLE_LOGICAL_IDENTITY
REQUIREMENT_ID_GRAMMAR=segment(/segment)*
REQUIREMENT_ID_NAMESPACE=SEPARATE
REQUIREMENT_ID_IS_SUITE_ID=NO
REQUIREMENT_ID_IS_PATH=NO
REQUIREMENT_ID_IS_PROVIDER_PROFILE=NO
REQUIREMENT_ID_IS_PLACEMENT=NO
REQUIREMENT_ID_IS_HOST_IMPLEMENTATION=NO
CAPABILITY_SET_INITIAL=NO
STRUCTURED_FACETS_INITIAL=NO
CORPUS_IDENTITY_SEPARATE=YES
DISPLAY_SEPARATE=YES
D077_D098_RESOURCES_SEPARATE=YES
HOST_BINDING=EXACT
HOST_REGISTRY=INVOCATION_SCOPED_IMMUTABLE
UNKNOWN_REQUIREMENT=FAIL_CLOSED
MISSING_REQUIREMENT=FAIL_CLOSED
DUPLICATE_BINDING=FAIL_CLOSED
VALIDATE_BEFORE_SCHEDULING=YES
MULTIPLE_REQUIREMENTS_MAY_SHARE_BINDING=YES
REMOTE_PLACEMENT_CHANGES_REQUIREMENT=NO
HOST_CLASS_PATH_PLUGIN_IN_DESCRIPTOR=NO
INITIAL_ORDINARY_REQUIREMENT=protos/test/ordinary
INITIAL_ACTOR_REQUIREMENT=protos/test/actor
INITIAL_GROUP_REQUIREMENT=protos/test/group
INITIAL_PACKAGE_REQUIREMENT=protos/test/package
```

## Implementation consequence

After this governance record is published, TOOL005-A3 may extend executable leaf
descriptors to carry the selected requirement IDs, materialize an invocation
binding registry for the four existing execution lanes, validate all requirements
before case scheduling, and replace the current nested hard-coded plan sequencing
with graph-driven iteration/aggregation while preserving current TestPlan,
resource, progress and exit behavior.

D125 does not authorize corpus expansion, recursive auto-discovery, capability-set
resolution, requirement inheritance, remote protocol design, or changes to
D077/D098 resource semantics.
