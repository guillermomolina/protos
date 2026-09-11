# D082 — Workspace-symbol project, source-set, and index authority

Status: **RATIFIED — Candidate A′ selected**  
Decision family: `Dxxx` implementation-independent tooling architecture  
GitHub issue: #367  
Primary consumer: `LM009-G3` / #360  
Allocated: 2026-09-11  
Approved: 2026-09-11  
Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Decision summary

Protos selects **Candidate A′ — Canonical Project Binding + partitioned incremental
workspace index + exact live-document overlay** for future workspace-symbol support.

The selected architecture fixes the authority boundary but deliberately does **not**
claim that LM009-G3 is implementable yet. G3 remains blocked until an editor-neutral
canonical project/source-inventory authority exists.

The selected contract is:

1. An LSP `workspaceFolder` is coordination input, **not** Protos project/module
   identity.
2. A language-server session may own zero or more **exact Protos project bindings**.
3. Project membership and complete workspace source inventory come only from a
   canonical Protos package/project authority. G3 must not infer them by recursive
   filesystem scan, URI ancestry, basename conventions, open-document membership,
   exported-module lists, or editor-specific heuristics.
4. Every exact project binding owns a separate incremental symbol-index domain.
   Project identity is retained internally even though LSP ultimately aggregates
   workspace-symbol results.
5. Baseline `workspace/symbol` scope is mutable/workspace package source belonging
   to the bound project. External dependencies and `std:` modules are outside the
   baseline and may later be added as separate immutable/coarser layers.
6. Current immutable open-document snapshots form an exact **live overlay** over a
   canonical project source only after exact mapping to that source. Opening a loose
   `.protos` document never creates project/module identity.
7. `workspace/symbol` aggregates results over all exact project indexes in the
   client session because the LSP request carries no individual workspace identity.
8. Index construction is lazy/pay-for-use. After construction, source changes update
   only the affected project domain; project-graph changes invalidate the affected
   binding and require refresh from the canonical authority.
9. Baseline index lifetime is the language-server session. Persistence, sharding and
   remote indexes remain optional future optimizations and are not identity semantics.
10. D079 remains authoritative for the indexed declaration unit and LSP presentation:
    explicit named slot creation projected uniformly as `Property`. D082 introduces no
    competing Variable/Function/Method declaration taxonomy.
11. A future requirement for mutually incompatible toolchains across roots may move
    project domains into separate language-server processes without changing the
    project-binding/index contract.
12. Approval of D082 is **not** authorization to fabricate the missing canonical
    project-binding/source-inventory provider inside G3.

## Why a decision is required

LM009-G2 can compute document symbols from one immutable parsed source snapshot. A
`workspace/symbol` request is materially different: it needs a project-wide domain
and therefore needs exact answers to all of the following:

- which Protos project/resolution roots participate;
- which packages and source modules belong to each project;
- whether dependency and Standard Library sources are in scope;
- how multiple roots compose in one client session;
- how open unsaved buffers interact with disk-backed project sources;
- when project indexes are built, invalidated and destroyed; and
- whether editor paths/filesystem layout may be treated as semantic project identity.

The current repository does not already provide a mechanical answer suitable for G3.
The LSP workspace edge intentionally owns no Protos package/module authority. The
exact workspace resolver needs an explicit project root and validated execution plan;
the exact source lookup resolves an already-known package/module identity but does not
enumerate a complete source set; and the existing canonical package preflight executes
the bundled Package Tool through Polyglot/Truffle, outside LM009-G's static-service
baseline.

Therefore G3 cannot safely choose a project model as an implementation detail.

## Existing constraints preserved

D082 preserves these already-published boundaries:

- the real Protos parser/source/module/package authorities remain canonical;
- TypeScript/VS Code never becomes a parallel semantic authority;
- URI prefixes, parent-directory search, basenames and recursive filesystem scans do
  not become module/package identity;
- static language intelligence works on unexecuted source and does not require live
  guest execution;
- the language server remains client-session-owned and toolchain-matched;
- ordinary runtime execution pays no indexing cost when the service is absent;
- D079 remains authoritative for the symbol unit and baseline LSP kind; and
- G4 definition identity/resolution remains a separate decision surface.

## Comparative prior-art survey

The expanded audit covered mature language servers, compiler/build integrations and
indexing architectures across several families. The important question was not merely
whether they implement `workspace/symbol`, but **where project authority lives, how
source membership is established, how open buffers override disk state, how indexes
scale, and how multi-root/dependency state is isolated**.

### rust-analyzer

rust-analyzer provides the strongest overall architectural precedent. It keeps build
system/project discovery separate from the semantic database: Cargo workspaces or
explicit project descriptions are translated into a semantic project graph, and the
analysis layer consumes that graph rather than reinterpreting Cargo inside every IDE
feature. Mutable workspace and library/dependency sources can use different indexing
strategies. Incremental recomputation remains demand-driven.

Relevant lesson for Protos: **the index consumes project authority; it does not own
project authority**. A future Protos project-binding snapshot plays the role of the
semantic boundary between package/project tooling and static analysis.

### clangd

clangd demonstrates the best layered-index architecture. Project knowledge starts from
build/compilation authority rather than symbol-search heuristics. It then layers a
live open-file index over broader background/static/remote indexes. The live layer
keeps unsaved edits current while large project indexes may lag or live elsewhere.

Relevant lesson for Protos: exact live buffers should override broader project-source
content without creating identity. Persistent or remote indexes can remain later
optimizations behind the same semantic boundary.

### gopls

gopls models client sessions through per-folder/view state and immutable snapshots.
Snapshots include disk files plus unsaved overlays. Workspace-symbol requests aggregate
across relevant views because LSP does not carry an individual workspace identity.
It also distinguishes workspace-local search from expanded dependency/stdlib scope.

Relevant lesson for Protos: maintain **per-project custody** and aggregate only at the
protocol query boundary; do not collapse all roots into one semantic index.

### Haskell Language Server + hie-bios

HLS/hie-bios is the clearest precedent for authority separation. The build tool is
responsible for describing the environment; the IDE integration asks for a concrete
file/component/build mapping rather than independently reimplementing Cabal, Stack,
Bazel or another build system.

Relevant lesson for Protos: the missing project-binding provider should be an
editor-neutral project/package authority that the language server consumes, not an
LSP-specific duplicate of Package Tool semantics.

### SourceKit-LSP + build-system/BSP integration

SourceKit-LSP separates editor protocol handling from build-system authority. Build
integration supplies targets, sources and per-document build options; SourceKit-LSP
can then index/analyze without pretending that workspace-folder layout is sufficient
semantic authority.

Relevant lesson for Protos: a small reusable project-binding/source-inventory contract
is preferable to teaching the LSP implementation the internal package/build model.

### TypeScript / tsserver

tsserver has a mature ProjectService and computes language-service navigation over
project membership established before symbol queries. Configured/external projects are
strong precedent for this authority order. Inferred-project heuristics are deliberately
**not** adopted for Protos because convenient editor discovery must not become module
identity.

### Pyright

Pyright maintains analysis services per workspace/project domain and prioritizes open
source while retaining broader project state. Its history also illustrates that a
program/AST cache and a dedicated whole-workspace symbol index are distinct concerns.

Relevant lesson for Protos: per-project ownership is sound, but workspace-symbol
support should have a real project-wide index rather than being rebranded open-file
state.

### Ruby LSP

Ruby LSP provides two useful precedents: live reindexing of changed source and strong
root isolation. Its VS Code integration may use one server process per root when roots
can require incompatible Ruby/dependency environments.

Relevant lesson for Protos: process-per-root is a valuable future escape path, but it
is too expensive to make baseline policy while one toolchain-matched session can
faithfully host multiple project domains.

### Eclipse JDT / JDT LS

JDT demonstrates that heavyweight workspace/project models plus persistent background
indexes can scale to very large mature codebases. It also demonstrates the conceptual
and resource cost of a large central workspace institution.

Relevant lesson for Protos: persistent indexing is viable at scale, but should not be
baseline identity/ownership policy. Protos should pay for that machinery only if scale
requires it.

### OCaml-LSP + Dune/Merlin

OCaml tooling strongly separates build/project authority from editor features through
Dune/Merlin integration. It also exposes the stale-state problem when build-derived
state and current unsaved editor content are not separate layers.

Relevant lesson for Protos: **project binding and source content are distinct
contracts**. Package/project authority selects identity/membership; the open-document
snapshot can override current content.

### Dart Analysis Server

Dart's analysis-context/workspace evolution demonstrates that too many project/context
instances can become a memory-scaling problem and that first-class workspace modeling
is preferable to unbounded per-package duplication.

Relevant lesson for Protos: partition semantic index state by project, but do not
instantiate heavyweight independent analysis universes for every package/module.

### ElixirLS

ElixirLS shows the convenience and cost of broadly indexing workspace, dependencies and
standard libraries. It delivers rich search but expands startup, memory and invalidation
surface.

Relevant lesson for Protos: dependencies and `std:` should be optional future index
layers, not unavoidable baseline scope.

## Prior-art suitability for Protos

Scores are 0–10 and measure suitability as a precedent for this decision, not the
overall quality of each tool.

| System | Future resilience | Scalability | Protos philosophy | Total / 30 |
| --- | ---: | ---: | ---: | ---: |
| rust-analyzer | 10 | 10 | 10 | **30** |
| clangd | 10 | 10 | 9 | **29** |
| gopls | 9 | 9 | 8 | **26** |
| HLS + hie-bios | 9 | 7 | 10 | **26** |
| SourceKit-LSP | 9 | 9 | 8 | **26** |
| TypeScript / tsserver | 9 | 9 | 6 | **24** |
| Eclipse JDT LS | 9 | 9 | 5 | **23** |
| OCaml-LSP + Dune/Merlin | 8 | 6 | 8 | **22** |
| Pyright | 8 | 7 | 7 | **22** |
| Ruby LSP | 8 | 6 | 7 | **21** |
| Dart Analysis Server | 8 | 7 | 6 | **21** |
| ElixirLS | 7 | 6 | 5 | **18** |

The strongest evidence is not one implementation copied wholesale. It is the recurring
architecture:

```text
canonical build/project authority
        ↓
project snapshot / graph / binding
        ↓
per-project semantic/index state
        ↑
live editor overlay
        ↓
session-level query aggregation
```

## Candidate set

### A′ — canonical project binding + partitioned incremental index + live overlay

**SELECTED.**

This selects the target architecture but deliberately keeps G3 blocked until the
canonical provider exists.

### A — create project/source authority inside G3 immediately

Rejected. It would make one LSP feature parse/interpret package metadata, select roots
and enumerate modules, creating a second package/project authority in the wrong owner.

### B — recursively scan LSP workspace folders

Rejected. Editor folder membership is not Protos package/module identity. It fails for
parent folders containing several projects, source files outside canonical projects,
workspace members, dependency boundaries and future non-filesystem source custody.

### C — advertise open documents as workspace symbols

Rejected as the public feature, retained conceptually as the live-overlay layer.
Unopened project source would disappear, so the result is not faithfully project-wide.

### D — eager global index of all roots, dependencies and Standard Library

Rejected for baseline. It conflates project domains, raises startup/memory cost and
makes users pay for dependency indexing they may never use.

### E — one language-server process per project root

Deferred as an escape path. Strong isolation is useful if future roots require
incompatible toolchains, but present evidence does not justify multiplying process and
lifecycle cost now.

### F — run Package Tool preflight and rebuild on each workspace-symbol query

Rejected. It requires guest/Polyglot execution, repeats expensive work, still lacks an
exact complete source inventory contract and scales poorly.

### G — pure defer, select no architecture

Rejected despite being semantically safe. It makes no progress and leaves future
implementation vulnerable to a convenient but incorrect filesystem-scan shortcut.

## Mandatory GITHUB010 scorecard

Scores are 1–5. Confidence is HIGH unless noted.

| Criterion | A′ | A | B | C | D | E | F | G |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | 5 | 3 | 2 | 2 | 4 | 5 | 4 | 5 |
| Protos alignment | 5 | 2 | 2 | 4 | 2 | 3 | 2 | 5 |
| Future-option resilience | 5 | 3 | 2 | 4 | 3 | 4 | 3 | 5 |
| Scalability | 5 | 4 | 4 | 5 | 2 | 3 | 1 | 5 |
| Conceptual simplicity | 4 | 2 | 4 | 5 | 3 | 2 | 3 | 5 |
| Portability / implementation freedom | 5 | 3 | 4 | 5 | 4 | 4 | 2 | 5 |
| Runtime / resource cost | 4 | 3 | 4 | 5 | 1 | 2 | 1 | 5 |
| Failure / operability | 5 | 3 | 2 | 5 | 3 | 4 | 3 | 5 |
| Reversibility / migration cost | 4 | 2 | 3 | 5 | 3 | 3 | 4 | 5 |
| Evidence maturity / implementation risk | 5 | 2 | 4 | 4 | 4 | 4 | 3 | 5 |
| **Total / 50** | **47** | **27** | **31** | **44** | **29** | **34** | **26** | **50** |

Arithmetic is not the authority. C and G score highly because they are cheap/safe, but
C is not a truthful workspace feature and G delivers no architecture or feature.

### Three-axis project-owner comparison

| Candidate | Future endurance /10 | Scalability /10 | Protos philosophy /10 | Total /30 |
| --- | ---: | ---: | ---: | ---: |
| **A′** | **10** | **10** | **10** | **30** |
| G | 10 | 10 | 9 | 29 |
| C | 8 | 10 | 8 | 26 |
| E | 8 | 6 | 6 | 20 |
| A | 6 | 8 | 3 | 17 |
| D | 6 | 5 | 3 | 14 |
| B | 4 | 7 | 2 | 13 |
| F | 4 | 2 | 5 | 11 |

## Why A′ is the most Protos-aligned option

A′ keeps mechanisms small and authorities explicit:

- package/project semantics remain owned by package/project tooling;
- static analysis consumes a bounded immutable binding rather than rebuilding policy;
- indexes are local to the exact project that needs them;
- open-document state overrides content without inventing identity;
- dependency/stdlib/persistence/remote machinery is opt-in rather than baseline tax;
- no runtime object registry, Actor/Task/Process state or Truffle execution node is
  involved; and
- an alternative runtime/backend can implement the same project-binding contract with
  different machinery.

This keeps ordinary things ordinary and avoids creating an IDE-specific project
institution.

## Failure model

The selected architecture must fail closed when exact project authority is absent or
stale:

- a parent folder containing several projects is not recursively promoted to one
  project;
- a `.protos` file under an editor folder but outside any binding is not silently
  indexed as workspace source;
- exported modules are not treated as the complete internal source inventory;
- dependency private source is not admitted merely because it exists on disk;
- an overlay cannot create a second module identity;
- failure to refresh/index one project does not poison unrelated project domains; and
- project-graph changes invalidate the affected binding rather than mutating guessed
  membership piecemeal.

## Future-scenario stress test

### Very large projects

A′ can add persistent shards, static indexes or a remote index behind the project
binding without changing semantic authority. The live overlay remains the freshest
source layer.

### Many project roots

Each root/binding remains a separate domain. Workspace queries merge result sets only
at the request boundary. No root can accidentally rewrite another root's package/module
identity.

### Unsaved edits

The matching open-document snapshot overrides current source content while retaining
the canonical source/module identity. Loose buffers remain document-only.

### Dependencies and Standard Library

Later implementations may add immutable/coarser dependency or `std:` index layers
without altering baseline local-workspace semantics.

### Package graph changes

Manifest, lock or workspace-membership changes invalidate the binding. The canonical
project authority reconstructs it; the symbol index does not infer incremental package
semantics itself.

### Alternative runtime / Bytecode DSL / Native Image

The architecture depends on parser/source/project authority, not Truffle execution
nodes. Backend migration does not change the contract.

### Distributed / Actor / Process execution

Irrelevant to source indexing. Runtime concurrency domains are not consulted.

### Multi-toolchain roots

If future roots require incompatible Protos versions/toolchains, the same project
binding/index contract can be hosted process-per-root without changing symbol identity.

## Regret and escape analysis

**Plausible future requirement that could make A′ regrettable:** one editor session may
need roots that cannot share one language-server process because their Protos toolchains
or package semantics are mutually incompatible.

**Escape path:** keep the same exact project binding and per-project index contract but
host each domain in a separate process. The current decision deliberately defines no
server-global semantic identity that would make that split incompatible.

Another future requirement may demand project + dependencies + Standard Library search.
That can be added as explicit secondary index layers or an expanded search scope without
changing the local-project baseline.

## Strongest argument against A′

A′ ratifies an architecture that cannot yet be implemented end-to-end because the
repository lacks the required editor-neutral canonical project/source-inventory
provider. Pure deferral would avoid making any commitment until that provider exists.

The reason to ratify A′ anyway is that the architecture is strongly supported by
multiple mature implementations, constrains only the consumer-side authority boundary,
and explicitly leaves the provider API/implementation open. It prevents a later
filesystem scan or open-document approximation from becoming accidental project policy
while preserving the provider's implementation freedom.

## Explicitly deferred

D082 does **not** select:

- the formal work family, API or implementation of the missing canonical project-binding
  provider;
- how editors/sessions choose or configure bindings;
- persistent/remote index storage or serialization format;
- dependency/Standard Library search UI or policy;
- fuzzy ranking, scoring or result limits;
- G4 definition identity/resolution;
- references/rename/completion/hover/signature semantics; or
- automatic activation of process-per-root hosting.

Those questions retain their own future approval boundaries.

## Release boundary

After this ratification:

- `D082` is `RATIFIED`;
- `LM009-G2` remains `CLOSED`;
- `LM009-G3` becomes `BLOCKED_BY_PROJECT_BINDING_PREREQUISITE`, **not READY**;
- G3 must not implement raw folder scanning, an open-document-only approximation,
  per-query Package Tool execution or a global dependency/stdlib index as a substitute;
- `LM009-G4` remains unstarted and separately decision-gated if definition identity or
  resolution exposes new policy; and
- no Protos specification, runtime/native behavior, public language semantics or Maven
  implementation version changes.

## Approval record

The project owner explicitly approved Candidate A′ on 2026-09-11 after the expanded
cross-language/tool comparison and the explicit future-endurance, scalability and
Protos-philosophy scoring. The approval includes the refined name and boundary:

**Canonical Project Binding + partitioned incremental workspace index + exact
live-document overlay.**
