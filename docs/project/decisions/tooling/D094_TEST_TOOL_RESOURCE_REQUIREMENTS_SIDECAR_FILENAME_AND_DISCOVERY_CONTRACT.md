# D094 — Test Tool resource-requirements sidecar filename and discovery contract

Status: **RATIFIED — Candidate A′ refined selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #388

Triggered by: `TOOL002-I6D` publication `7a7083bfeccfd4a70e3ab31e95610125266f2028`

Primary consumer: `TOOL002-I` / GitHub #96

Predecessors: `D076`, `D077`, `D087`, `D091` — RATIFIED

Nature: implementation-independent Test Tool corpus-metadata acquisition contract

Normative language effect: **none**.

## Decision boundary

D077 selected sparse intrinsic resource requirements as an optional strict,
versioned TOML 1.0 document associated with a corpus, while deliberately leaving
the physical filename and discovery mechanism unresolved. I6C parses supplied
requirements text, and I6D/D091 strictly joins those declarations to the complete
associated TestPlan before filtering or sharding.

D094 selects only how the v1 corpus requirements document is physically named,
located, discovered and distinguished from absence or malformed/inaccessible
metadata. It does not select the environment resource-catalog source/CLI,
resource scope vocabulary, provider APIs, reservation/fairness/retry/timeout,
remote transport, CAS format, public `std:toml`, or a general Protos metadata
namespace.

## Selected contract — fixed exact corpus-root sibling

Candidate A′ refined is selected.

The v1 logical corpus layout is:

```text
<corpus root>/
├── manifest.tsv
└── resource-requirements.toml   # optional
```

The selected filename is exactly:

```text
resource-requirements.toml
```

The schema generation remains owned by the document field
`resource-requirements-version`; the filename does not carry a duplicate `v1`
version suffix.

### Root and authority

The sidecar is an exact direct child of the same canonical corpus root from which
`manifest.tsv` is loaded. Acquisition uses only the already-authorized corpus
`Filesystem` capability. D094 introduces no host absolute path, current-working-
directory authority, parent-directory traversal, user-home lookup, environment
fallback or repository-root rediscovery.

This makes the contract a property of the logical corpus tree rather than of the
host placement of that tree.

### Exact direct-child discovery

Discovery uses capability-confined direct-child observation of the corpus root,
conceptually:

```text
filesystem.entries(Path.relative())
```

The Test Tool looks for the exact stored direct-child name
`resource-requirements.toml`.

The existing Filesystem contract is authoritative for this observation:
returned names are exact semantic String spellings, are not case-folded or
normalized, and the final child kind is observed without following links.

No alternate spelling, alias, case-insensitive match, filename family or
heuristic near-match is part of the D094 contract.

### Absence

If no exact direct child named `resource-requirements.toml` exists, the corpus
has zero resource-requirement declarations.

Absence is therefore the ordinary simple/resource-free case. It requires no TOML
parse, no resource catalog, no provider and no live resource machinery.

### Present-entry shape

If the exact child exists, its observed kind must be:

```text
"regular"
```

An exact child classified as `"link"`, `"directory"` or `"other"` is invalid
corpus/configuration evidence and fails closed.

In particular, D094 does not follow a symlink/junction/reparse-style indirection
for integrity-relevant Test Tool metadata. This preserves the same corpus-tree
meaning across native filesystems, captured Filesystems, archives, CAS-backed
materialization and remote execution environments that may not share host link
semantics.

### Read and validation failure

After exact regular-child discovery, the Test Tool acquires the sidecar through
the same corpus Filesystem under ordinary existing/read File semantics.

Any failure after presence is established remains a failure. The implementation
must not translate permission/confinement/backend/I/O/cancellation/read failure
into sidecar absence.

Likewise, malformed TOML, unsupported `resource-requirements-version`, strict
I6C schema failure, D091 orphan/ambiguous reference failure, or another I6D join
failure remains explicit configuration/infrastructure evidence and never becomes
an empty requirements set.

D094 specifically rejects the implementation pattern:

```text
try open sidecar
catch any failure -> pretend no sidecar exists
```

because that could silently remove resource protection from tests.

### Exactly one source; no override or merge

For v1 there are exactly zero or one resource-requirements documents per corpus.
The canonical sibling is the sole source.

There is no public requirements-sidecar:

- CLI override;
- environment-variable override;
- user-home override;
- parent/project search;
- secondary source;
- include chain;
- fragment glob;
- inheritance;
- merge;
- last-wins precedence.

Internal parser/join tests may continue supplying text or inert declarations
directly; testability does not imply a public alternate-source contract.

### Ordering relative to execution selection

The durable ordering is:

```text
corpus Filesystem root
    -> manifest.tsv / complete TestPlan
    -> exact optional resource-requirements.toml discovery
    -> I6C strict parse
    -> I6D/D091 strict full-TestPlan join
    -> validated planning data
    -> invocation filtering / selection
    -> sharding / placement
    -> reservation / execution
```

Filtering, sharding or worker placement never changes which requirements sidecar
belongs to the corpus and never redefines sidecar absence.

## Ownership rationale

D077 separates two intentionally different authorities:

```text
repository/corpus              execution environment
-----------------              ---------------------
resource requirements          resource catalog
```

Requirements describe what an existing test intrinsically needs. The catalog
describes what one environment can provide. D094 therefore does not copy an
explicit per-invocation source-selection mechanism from environment inventory
systems merely for symmetry.

A future catalog source may legitimately be selected explicitly per invocation
without making corpus requirements invocation-dependent.

## Prior-art findings

The expanded comparative audit in GitHub #388 covered test runners, build
systems, package managers, CI systems and project/config discovery tools.

### CTest

CTest separates intrinsic per-test resource declarations from an external
resource specification describing execution-environment inventory. The latter
may be supplied explicitly per run. This supports Protos keeping a different
acquisition contract for corpus requirements and environment catalog data.

### JUnit Jupiter

`ResourceLock` metadata is structurally attached to an existing test class or
method. Although D077 intentionally chose a sparse sidecar instead of inline
annotations, JUnit reinforces the ownership principle that intrinsic scheduling
metadata travels with the test corpus rather than being selected by each run.

### cargo-nextest

nextest uses a stable workspace-relative repository config location while also
supporting explicit override and broader profiles/selector policy. D094 adopts
the useful fixed-root locality but rejects override layering for its much
narrower intrinsic requirement authority.

### Bazel and Buck2

`BUILD`/`BUILD.bazel` and `BUCK` represent package-local graph authority anchored
to a known directory. Their separate user/tool configuration mechanisms are more
flexible. D094 follows the intrinsic-graph side of that distinction.

### Cargo, Go, TypeScript and Gradle

These systems use conventional project markers/config files, often with ancestor
search or explicit project overrides because command invocation may start without
an already-selected project root. Protos Test Tool already receives the exact
corpus Filesystem authority, so repeating root discovery would add ambiguity
without recovering missing information.

### pytest, Jest, Vitest and ESLint

These ecosystems support multiple conventional filenames, parent search,
fallbacks and/or explicit overrides to integrate with heterogeneous project
layouts. Their mature experience demonstrates the ergonomic value of flexibility
but also the precedence, authority and diagnosability cost of multiple active
configuration identities. D094 has one fixed TOML dialect and one already-known
corpus root, so it does not need that complexity.

### CMake Presets

CMake distinguishes project-owned `CMakePresets.json` from developer-local
`CMakeUserPresets.json`, reinforcing that configuration discovery should follow
ownership classes instead of merging them implicitly.

### GitHub Actions and GitLab CI

GitHub's fixed `.github/workflows/` namespace demonstrates a scalable repository
metadata-directory architecture. GitLab demonstrates the additional power and
complexity of custom paths, includes and composition. Those are credible future
options if Protos acquires multiple independent corpus-metadata families, but are
not required for one v1 sidecar.

### Maven Surefire

Surefire's primary-POM configuration is the coherent unified-manifest control
candidate. D077 already rejected migrating the ordinary corpus into a richer
primary representation merely for sparse resource metadata, so D094 does not
reopen that decision.

## Candidate set

### A′ — exact fixed sibling, automatically discovered

**Selected.** One exact optional regular file at the corpus root, no override or
search chain, with fail-closed distinction between absence and malformed or
inaccessible metadata.

### B — explicit source only

Rejected because forgetting or replacing the source would make intrinsic resource
protection invocation-dependent for the same corpus.

### C — manifest/descriptor-declared relative sidecar

Rejected for v1 because it introduces a pointer/descriptor indirection solely to
locate one document whose location can be defined directly by convention. It
remains viable if future corpus layouts need independently selectable metadata.

### D — convention plus public override

Rejected because the same corpus could acquire multiple competing requirement
truths depending on invocation. This flexibility is more appropriate for the
separate environment catalog plane.

### E — cwd/parent/home/environment search chain

Rejected because Protos already possesses the exact corpus root through explicit
Filesystem authority. Ambient search would make behavior host-placement and
invocation-location dependent and would scale poorly to captured/CAS/remote
corpora.

### F — unify requirements into the primary manifest

Rejected under the current predecessor architecture because it would reopen D077's
selected sparse sidecar model.

### G — fixed metadata namespace directory

Credible strongest future alternative. A layout such as a future dedicated
corpus/test metadata directory scales well if several independent metadata
families emerge, but selecting that directory today would create a general
namespace institution for one actual file and would force adjacent ownership
questions D094 does not need to answer.

### H — fragment directory / multiple merged sidecars

Credible for extremely large multi-owner corpora, but rejected for v1 because it
introduces merge order, cross-file duplicate handling, versioning/atomicity,
partial-materialization and diagnostic-source rules that D077's single optional
document does not require.

## Focused scores

Scores are 1–5.

| Candidate | Future resilience | Scalability | Protos alignment |
| --- | ---: | ---: | ---: |
| **A′ fixed exact sibling** | **4.8** | **5.0** | **5.0** |
| B explicit only | 4.0 | 4.5 | 2.5 |
| C manifest pointer | 4.7 | 4.5 | 3.5 |
| D convention + override | 4.5 | 4.5 | 2.5 |
| E search chain | 3.2 | 3.0 | 1.5 |
| F unified primary manifest | 4.5 | 4.5 | 3.0 |
| G fixed metadata namespace | **5.0** | **5.0** | 4.0 |
| H fragment directory / merge | **5.0** | **5.0** | 2.5 |

## Full comparative scorecard

| Criterion | **A′ sibling** | B explicit | C pointer | D override | E search | F unified | G namespace | H fragments |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | **5.0** | 3.0 | **5.0** | 3.5 | 2.5 | **5.0** | **5.0** | 4.0 |
| Protos alignment | **5.0** | 2.5 | 3.5 | 2.5 | 1.5 | 3.0 | 4.0 | 2.5 |
| Future-option resilience | **4.8** | 4.0 | 4.7 | 4.5 | 3.2 | 4.5 | **5.0** | **5.0** |
| Scalability | **5.0** | 4.5 | 4.5 | 4.5 | 3.0 | 4.5 | **5.0** | **5.0** |
| Conceptual simplicity | **5.0** | 3.5 | 3.0 | 3.5 | 2.0 | 4.0 | 4.0 | 2.0 |
| Portability / implementation freedom | **5.0** | 4.5 | **5.0** | 4.5 | 2.5 | **5.0** | **5.0** | **5.0** |
| Runtime / resource cost | **5.0** | 4.5 | 4.5 | 4.8 | 3.0 | 3.5 | 4.8 | 4.0 |
| Failure / operability | **5.0** | 3.0 | 4.5 | 3.0 | 2.0 | **5.0** | **5.0** | 3.5 |
| Reversibility / migration | 4.5 | 4.0 | 4.0 | 4.0 | 3.0 | 2.0 | 4.5 | 3.5 |
| Evidence maturity / risk | **5.0** | **5.0** | 4.0 | **5.0** | **5.0** | **5.0** | 4.5 | 4.5 |

## Filename selection

The canonical name `resource-requirements.toml` is selected because it uses the
D076/D077 concept directly and cannot be confused with the separate environment
resource catalog.

Names such as `resources.toml` or `test-resources.toml` are rejected as ambiguous
between demand and inventory. Generic `requirements.toml` is rejected because it
also commonly denotes package dependencies. A `-v1` suffix is rejected because
schema generation is already explicit inside the document.

## Scalability and future stress

### Large corpora and monorepos

Discovery remains local to one explicitly selected corpus root and performs no
recursive/project-wide search. Independent corpus roots can be processed in
parallel without parent inheritance or shared mutable discovery state.

### Sharding and remote execution

Discovery, parse and full-plan join occur once before the validated plan is
sharded. Workers therefore need no requirements-discovery protocol. If a remote
system instead materializes the complete corpus tree, the same relative filename
remains valid.

### Archives, captured Filesystems and CAS

The contract is expressed entirely as a logical relative tree layout. No absolute
host path is persisted. A captured tree or content-addressed tree can therefore
preserve `manifest.tsv` and `resource-requirements.toml` identically even when its
physical storage mechanism is unrelated to a native filesystem path.

### Generated requirements

A future generator can materialize the canonical sidecar as a corpus-production
step. Ordinary Test Tool execution need not gain a public alternate requirements
source merely to support generation or internal tests.

### Multiple future metadata families

If several real Test Tool/corpus metadata documents appear, a fixed metadata
namespace directory may become preferable. D094 intentionally leaves that
institution unselected until the multiplicity exists.

## Regret scenario and escape path

The most plausible regret scenario is rapid proliferation of corpus metadata
(`timeouts`, `tags`, `fixtures`, permissions, and similar documents) or a need for
independently owned metadata fragments. In that future, one root-level sibling
per metadata family may become cluttered.

The escape path is clean because D077 schema semantics and CaseSpec planning do
not depend on the physical path. A later layout generation can introduce a fixed
metadata namespace and migrate the sidecar while retaining deterministic support
for old v1 corpora where needed. Scheduler, resource-key, provider and TestPlan
contracts need not change.

## Strongest argument against A′

A dedicated metadata namespace is marginally more future-proof if many corpus
metadata families are already known to be imminent. D094 nevertheless selects
A′ because the project currently has one concrete sparse document, while a
namespace directory would establish broader cross-tool ownership and hierarchy
questions prematurely. The reversible path from one sibling to a later metadata
namespace is cheaper than paying that institution before it is needed.

## Implementation consequence

D094 releases bounded `TOOL002-I6E` implementation. I6E may:

- observe the canonical corpus root with existing `Filesystem.entries`;
- distinguish exact sidecar absence from a present malformed/non-regular entry;
- read the exact regular sidecar through the existing corpus Filesystem;
- feed its text to I6C and attach through I6D/D091 before filtering/sharding; and
- preserve the simple resource-free path when the sidecar is absent.

I6E must not add a public requirements-path override, search parent/home/env
locations, merge multiple requirement sources, select the catalog source/CLI,
define scope vocabulary or provider APIs, introduce a general metadata directory,
or change public Protos language semantics.
