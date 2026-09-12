# D102 — Nested workspace package source ownership and module-boundary contract

Status: **RATIFIED — Candidate A′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #421

Primary consumers: `LM009-G3P P3B` / GitHub #413 and shared workspace package runtime/source authorities

Predecessors: `D082`, `D085`, `D089` — RATIFIED

Triggered by: P3B focal validation exposing one physical source as both `member:Api` and `root:libs/member/Api`

Nature: implementation-independent package-system/source-ownership contract

Normative core-language effect: **none**. This decision constrains canonical package/module ownership and package resolution.

## Decision boundary

D085 makes the canonical package/project authority responsible for authorizing exact
workspace package roots, and P1 inventories ordinary `.protos` files only after those
roots have been bound. D089 keeps the live source inventory outside `protos.project` so
ordinary source edits do not stale project authority.

P3B validation exposed one missing rule in that architecture. Given:

```text
project/
├── Main.protos
└── libs/
    └── member/          PackageId = member
        └── Api.protos
```

with root PackageId `root` at location `""` and member PackageId `member` at
`"libs/member"`, the existing recursive inventory and exact source lookup admitted the
same physical file under two semantic identities:

```text
member:Api
root:libs/member/Api
```

The same ambiguity was not LM009-specific: runtime workspace module resolution uses the
same exact source-lookup authority, so retaining the status quo would permit one
physical source to acquire multiple PackageId/logical-module identities depending on
which package root initiated lookup.

D102 decides source ownership when canonically authorized workspace package roots are
physically nested or physically aliased. It does not redesign package manifests,
workspace membership, `protos.project`, dependency/export semantics or project
discovery.

## Selected contract — Candidate A′

Protos selects **canonical non-overlapping package source domains**.

The contract is:

1. Every canonically authorized workspace package root defines one source/module
   ownership domain.
2. Physical ownership is evaluated over the canonical physical package roots already
   produced by the existing confined package-root binding authority.
3. Distinct PackageIds MUST NOT materialize onto the same canonical physical package
   root. Such a project binding fails closed before source inventory or module lookup.
4. If canonical package root `Q` is a strict physical descendant of canonical package
   root `P`, `Q` is a hard package boundary for `P`.
5. The source domain of `P` is its canonical subtree minus every subtree rooted at a
   strictly-descendant authorized package root.
6. Equivalently, a physical source is owned by the unique **most-specific authorized
   package root** that contains it.
7. Source inventory, exact source lookup, runtime module resolution and static tooling
   MUST apply the same ownership rule. Tooling may not present a cleaner ownership
   universe than runtime, and runtime may not expose aliases hidden from tooling.
8. Package boundaries come only from already-canonical package/project authority. Source
   lookup and inventory MUST NOT search for `protos.toml`, `protos.project`, lockfiles or
   another marker to discover package boundaries.
9. Physical containment grants no cross-package module authority. A package-local
   `self:` lookup MUST NOT traverse through another authorized package root. Reaching a
   module owned by another package requires the ordinary explicit package/dependency
   authority already selected by the package graph and export rules.
10. Existing confinement, exact spelling, portable logical-module naming, case-fold
    ambiguity and regular-file rules remain authoritative inside the owning package
    domain.
11. A source reached through a symlink is judged by the canonical physical target used
    by the existing confinement authority. A parent package cannot regain ownership of
    a child-owned source through an alternate physical spelling or symlink alias.
12. The rule is provider-neutral. A future package daemon, BSP-like provider, remote
    project service or generated-source authority may supply the same non-overlapping
    package domains without changing source/module identity.

For the motivating layout, the only valid source identities are therefore:

```text
root:Main
member:Api
```

and this identity is invalid:

```text
root:libs/member/Api
```

## Formal domain rule

Let `R(P)` be the canonical physical root of authorized workspace package `P`, and let
`Desc(P)` be the set of other authorized workspace packages whose canonical physical
roots are strict descendants of `R(P)`.

The physical source domain is:

```text
Domain(P) = Subtree(R(P)) - union(Subtree(R(Q)) for Q in Desc(P))
```

Because equal canonical roots for distinct PackageIds are rejected, and nested roots
subtract their complete subtree from every ancestor, every admitted physical source has
at most one workspace PackageId owner.

For an admitted source `S`, its owner is equivalently the authorized package `P` with
the longest canonical physical root prefix containing `S`.

This longest-root formulation is explanatory, not permission for filesystem discovery:
the candidate roots are exactly the finite root set already provided by canonical
package/project authority.

## Identity and import consequences

Package identity remains `(PackageId, logicalModule)`, but D102 prevents one physical
workspace source from being independently assigned multiple such identities by nested
root containment.

A module lookup for package `P` therefore has two independent checks:

```text
logical module -> exact confined physical source
physical source -> source must still belong to Domain(P)
```

Crossing a descendant package boundary is not a filesystem operation. It is a package
graph operation. For example:

```text
root self:Main                  -> allowed when exact source exists in Domain(root)
root self:libs/member/Api       -> rejected at package boundary
root dep:member/Api             -> governed by ordinary dependency/export authority
```

D102 does not create or rename dependency syntax; the final line is conceptual shorthand
for the already-owned dependency routing contract.

## Canonical physical-root alias rejection

Canonical location strings remain part of package/project authority, but they cannot be
used to manufacture two physical owners for one directory.

For example, if two distinct authorized locations:

```text
libs/a
libs/b
```

resolve through permitted host symlink mechanics to the same canonical physical
directory, binding fails closed. Neither first-wins nor declaration-order precedence is
allowed.

This rule closes the non-nested form of the same identity bug. Without it, two PackageIds
could own exactly the same source set even though their canonical workspace location
strings differ.

## Runtime/tooling consistency

D102 deliberately rejects a split policy in which LM009 removes duplicate sources from
its index while runtime lookup continues to resolve both identities.

The following authorities must agree:

```text
canonical package directory binding
        |
        +--> exact package source lookup
        |
        +--> current source inventory
        |
        +--> runtime module resolver
        |
        `--> ProjectBinding / static tooling
```

A future persistent, remote or distributed index may cache the result, but it cannot
change ownership.

This is important for later definition/reference identity, diagnostics, debugger source
identity, breakpoints, compiler caches and incremental invalidation: one physical source
must not silently become two workspace modules merely because one authorized root is
inside another.

## No project/package discovery

Candidate A′ is not a nearest-manifest algorithm.

The forbidden architecture is:

```text
walk source path / ancestors
    -> find nearest protos.toml
    -> infer package owner
```

The selected architecture is:

```text
canonical package/project authority
    -> finite authorized PackageId/root set
    -> mechanically bind canonical physical roots
    -> derive non-overlapping domains from that set only
```

This preserves D082/D085's editor-neutral authority boundary. An editor workspace folder,
open document, current working directory or filesystem marker cannot create package
identity.

## Prior-art audit

The D102 audit compared ownership semantics rather than build-system syntax. Systems were
selected to cover hard hierarchical package boundaries, explicit source-root/source-set
models, build-server authority, and intentionally overlapping/multi-owner models.

### Bazel

Bazel provides the closest direct precedent. A package owns its directory subtree only
until another package boundary is encountered; a subdirectory containing another BUILD
package is not part of the ancestor package. Labels therefore have one package owner.

**Contribution:** strong mature evidence for hard descendant boundaries and unique
ownership at monorepo scale.

### Buck2

Buck2 likewise models hierarchical, non-overlapping packages. A nested package boundary
cuts the ancestor package rather than creating a second identity for the same source.

**Contribution:** independent large-monorepo confirmation of the Bazel-style model.

### Go modules and workspaces

Go supports explicitly selected workspace modules, including nested module roots. Module
walking stops at nested module boundaries rather than recursively absorbing their files
into the parent module.

**Contribution:** language/package-manager evidence that nested roots can remain usable
without multi-owner source identity.

### Swift Package Manager

SwiftPM gives targets explicit source roots and rejects overlapping sources between
targets instead of silently permitting one source to acquire multiple owners.

**Contribution:** strong evidence that overlap is an invariant violation worth failing,
not a convenience alias.

### Cargo / Rust

Cargo workspaces contain explicit member packages. Rust compilation roots and Cargo
target source roots keep member package compilation units separate rather than treating
all descendant `.rs` files as implicit sources of every ancestor package.

**Contribution:** mature workspace/package isolation through explicit source roots.

### Gradle

Gradle multi-project builds give each project its own source sets. Physical project
nesting does not by itself make child sources sources of the parent project.

**Contribution:** source-set architecture proving that scalable builds avoid ambient
physical-containment ownership.

### Maven

Maven modules use distinct conventional source roots (`src/main/...`, `src/test/...`). A
nested module is not recursively absorbed merely because it resides under an aggregator
project directory.

**Contribution:** long-lived evidence for explicit module source ownership.

### sbt

sbt subprojects independently own source directories and products while a build composes
those projects explicitly.

**Contribution:** additional JVM/Scala evidence for separate project source domains.

### Mill

Mill supports nested modules while resolving module sources relative to each module's own
module directory/source definitions.

**Contribution:** particularly relevant evidence that nested modules need not be banned
to keep ownership unique.

### Node.js package scope + npm/Yarn/pnpm workspaces

Node package scopes recognize nested package boundaries, and mainstream JavaScript
workspace managers model members as explicit packages. Relative filesystem imports are
more permissive than Protos package authority, so Node is not copied literally.

**Contribution:** confirms the usefulness of nested package boundaries while also
showing why physical-path access alone is insufficient encapsulation.

### Build Server Protocol (BSP)

BSP puts target/source membership behind an explicit build-system authority. Language
clients consume source ownership rather than reconstructing it from editor folders.

**Contribution:** strongest future-provider evidence for keeping D102 ownership behind
the same replaceable authority selected by D085.

### Pants

Pants permits a source to have multiple owning targets and consequently documents owner
ambiguity and the need for explicit disambiguation in some dependency-inference cases.

**Contribution:** useful negative evidence. Multi-owner models can scale operationally,
but they make ambiguity a first-class condition every downstream consumer must carry.

### .NET / MSBuild

SDK-style projects commonly use recursive source globs and provide exclusion mechanisms
to prevent unintended descendant sources from entering a parent project.

**Contribution:** negative evidence against making recursive filesystem containment the
semantic default and repairing overlaps later with exclusions.

### Python / uv

uv workspaces explicitly aggregate packages, while Python namespace packages show a
different kind of intentional namespace sharing across distributions. That model does
not require assigning the same physical file two package identities.

**Contribution:** demonstrates that shared namespace composition is distinct from shared
physical-source ownership and does not justify Candidate B.

### CMake

CMake lets build authors compose subdirectories and source lists very flexibly and does
not impose a single semantic source-owner model.

**Contribution:** evidence that build orchestration flexibility is not itself a suitable
module-identity authority for Protos.

## Candidate set

### A′ — canonical non-overlapping package source domains — SELECTED

Authorized descendant roots are hard boundaries, the most-specific authorized root owns
a source, equal canonical physical roots for distinct PackageIds fail closed, and all
runtime/tooling consumers share the same rule.

### B — overlapping multi-identity domains — status quo behavior

A physical source may be independently resolved under every authorized ancestor package
whose package-relative path reaches it.

This preserves current accidental lookup behavior but makes physical source identity
context-dependent and forces every cache/index/debug/navigation consumer to tolerate
multiple PackageId/logical-module identities for one source.

### C — static-tooling-only de-duplication

P1/LM009 would assign one owner while runtime source lookup retains overlapping aliases.

This is rejected as internally inconsistent: the IDE would describe a different module
universe from execution.

### D — prohibit nested workspace package roots

Any authorized package root physically below another is invalid.

This guarantees unique ownership but unnecessarily removes a normal and scalable
workspace/monorepo layout supported by mature systems.

### E — explicit source sets / include-exclude rules

Manifests gain source-root/glob/exclusion policy, allowing users to define disjoint
source sets explicitly.

This can be sound, but it creates a new source-membership configuration authority and
freshness surface solely to solve a problem already resolved structurally by the
canonical package root set.

### F — nearest-manifest / nearest-package-marker discovery

Ownership is inferred dynamically by walking the filesystem and selecting the nearest
manifest-like marker.

This can produce unique ownership ergonomically, but directly violates D082/D085 by
turning filesystem/editor context into package authority.

## Comparative scoring

Scores are 1–5. Confidence is `HIGH` unless noted otherwise.

| Criterion | **A′ exclusive domains** | B overlap | C tooling-only | D ban nesting | E source sets | F discovery |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | **5.0** | 2.0 | 1.0 | **5.0** | **5.0** | 3.0 |
| Protos alignment | **5.0** | 1.0 | 1.0 | 3.0 | 3.5 | 1.0 |
| Future-option resilience | **5.0** | 2.0 | 1.0 | 2.0 | **5.0** | 3.0 |
| Scalability | **5.0** | 3.0 | 2.0 | 2.0 | **5.0** | 4.0 |
| Conceptual simplicity | **5.0** | 2.0 | 1.0 | **5.0** | 3.0 | 3.0 |
| Portability / implementation freedom | **5.0** | 4.0 | 2.0 | **5.0** | **5.0** | 4.0 |
| Runtime / resource cost | **5.0** | 4.0 | 4.0 | **5.0** | 4.0 | 4.0 |
| Failure / operability | **5.0** | 2.0 | 1.0 | **5.0** | 4.0 | 3.0 |
| Reversibility / migration cost | 4.0 | 2.0 | 1.0 | 2.0 | 3.0 | 3.0 |
| Evidence maturity / implementation risk | **5.0** | 3.0 | 2.0 | **5.0** | **5.0** | **5.0** |
| **Total / 50** | **49.0** | **25.0** | **16.0** | **39.0** | **42.5** | **33.0** |

Focused project-owner criteria:

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| **A′ exclusive domains** | **5.0** | **5.0** | **5.0** |
| B overlap | 2.0 | 3.0 | 1.0 |
| C tooling-only | 1.0 | 2.0 | 1.0 |
| D ban nesting | 2.0 | 2.0 | 3.0 |
| E source sets | **5.0** | **5.0** | 3.5 |
| F discovery | 3.0 | 4.0 | 1.0 |

### Score justification — A′

`HIGH` confidence. It preserves one physical-source owner, reuses the existing finite
canonical root set, has O(number of authorized package roots) metadata rather than a
per-source authority list, admits nested monorepos, keeps runtime/tooling identical and
has direct Bazel/Buck2/Go precedent. The main migration cost is tightening current
accidental aliases; that cost is smaller now than after indexes/debugger caches become
persistent.

### Score justification — B

`HIGH` confidence. Operationally feasible — Pants demonstrates multi-owner systems can
scale — but every downstream identity consumer must either preserve ambiguity or invent
precedence. It weakens cache/debug/navigation invariants and is expensive to remove once
external code relies on aliases.

### Score justification — C

`HIGH` confidence. It superficially fixes workspace-symbol duplication but violates the
more important invariant that tooling reflects runtime/package authority. It creates
immediate definition/debugger inconsistency and therefore fails correctness independent
of implementation cost.

### Score justification — D

`HIGH` confidence. Very simple and correct, with mature evidence that rejecting invalid
layouts works, but it unnecessarily sacrifices nested package layouts central to many
large repositories. Migration and future regret are therefore substantial.

### Score justification — E

`HIGH` confidence. Mature and scalable in Cargo/Gradle/Maven/SwiftPM-like systems, but it
adds a new manifest/source-set policy layer, glob/exclusion semantics, freshness inputs
and configuration interactions that Protos does not otherwise need. It remains a valid
future extension for genuinely non-tree source sets.

### Score justification — F

`HIGH` confidence for the architectural assessment. Nearest-manifest discovery is common
and convenient, but package identity would then depend on ambient filesystem markers and
search rules rather than the explicit canonical authority already selected by
D082/D085/D089. That is a direct Protos authority-model regression.

## Future-scenario stress test

### Large monorepo with thousands of nested packages

A′ continues to use the same package graph. Inventory can prune a subtree as soon as it
reaches another authorized root, avoiding ancestor re-walk of descendant package source
trees. Package-domain metadata scales with authorized package roots rather than source
count.

### Deeply nested package hierarchy

Most-specific-root ownership remains deterministic at arbitrary depth. Every ancestor
excludes the complete descendant root subtree; no pairwise exception table is needed.

### Multiple editors and language-server sessions

All sessions consuming the same canonical project authority derive the same owner for a
source. Open-document state cannot create or move a package boundary.

### Persistent/remote workspace symbol indexes

Stable PackageId/logical-module ownership gives one cache/index identity per workspace
source. No duplicate physical-source de-duplication protocol is required later.

### Debugger and source mapping

A breakpoint/source URI cannot silently map to two workspace ModuleKeys through nested
package aliases. Alternate source-path presentation mechanisms remain orthogonal.

### Package daemon / BSP / remote provider

A provider may return already-resolved package roots/source ownership, or enough exact
root authority to derive A′ mechanically. D102 does not require local filesystem
manifest discovery and therefore survives remote/build-server authority.

### Generated or virtual sources

D102 does not decide generated-source membership. A future explicit provider may add a
non-filesystem source domain, but must still define one package owner for each admitted
source identity or explicitly reopen D102 if multi-owner generated sources are desired.

### External immutable dependencies and Standard Library

They remain outside the D082 workspace baseline. D102 does not silently promote them into
workspace package roots. If a later index layer includes them, its package ownership must
remain explicit rather than inferred from workspace containment.

### Symlink-heavy workspace layouts

Canonical physical root equality is checked before ownership. Two PackageIds cannot gain
separate ownership through alternate symlink spellings of one directory. Existing
confinement rules continue to reject escapes.

### Alternative host/filesystem or non-Truffle implementation

The contract requires canonical package-root identity and containment, not Java NIO,
Truffle Source or a particular operating system API. Another implementation may use a
different canonical-path substrate while preserving the same ownership result.

## Strongest argument against A′

The strongest objection is that physical source containment can occasionally be useful
as an intentional alias: a workspace may want one file to be addressable from an
ancestor package and a nested package without copying it. Candidate A′ forbids that
convenience and may break code that accidentally relies on current behavior.

The objection is real but not sufficient. Such aliasing makes PackageId/logical-module
identity non-unique and leaks into compiler caches, debugger/source identity,
workspace-symbol indexing, definition/reference navigation and future persistence.
Intentional cross-package reuse already has a more explicit semantic route: package
relationships/exports, or a future deliberately designed shared/generated-source
mechanism. Physical containment should not silently create a second module identity.

## Strongest regret scenario and escape path

The strongest plausible regret case is a future build/provider model with intentionally
shared generated sources that conceptually belong to several package targets at once.

If that requirement becomes real, the escape path is to introduce a separate explicit
shared/generated-source ownership model at the provider/package-graph level. Such a model
can carry stable source identity and multi-target membership deliberately without
weakening ordinary filesystem workspace packages or reintroducing nearest-manifest
inference.

D102 therefore keeps the common filesystem case simple and unique while leaving an
explicit future decision boundary for genuinely multi-owner virtual/generated sources.

## Implementation consequences

Ratification alone changes no executable implementation.

The first dependent implementation must update the shared package authorities, not patch
LM009 in isolation. At minimum the implementation must ensure:

- physical package directory binding rejects distinct PackageIds whose canonical roots
  are equal;
- exact source lookup rejects paths that enter another authorized package root;
- current source inventory prunes descendant authorized package-root subtrees;
- runtime workspace module resolution receives the same boundary through the shared
  lookup authority;
- focused regressions prove the motivating nested-member source has only `member:Api`,
  never `root:libs/member/Api`;
- regressions cover deeper nesting, canonical-root alias rejection, symlink interaction
  and ordinary nested logical modules that do not cross a package boundary.

Only after that shared authority is published may LM009-G3P P3B resume its exact
ProjectBinding-provider validation.

## Compatibility and migration

A′ intentionally tightens current accidental behavior. Source imports that traversed
through a nested authorized workspace member as though it were still part of the parent
package will fail after implementation.

This is the desirable migration point: P3B exposed the ambiguity before workspace-symbol
indexing, definition/reference behavior or persistent project indexes have been published
on top of it. Ratifying unique ownership now avoids a much more expensive compatibility
promise later.

No manifest syntax migration is required. Existing valid nested workspace members remain
valid; only cross-boundary alias access is removed.

## Intentionally deferred

D102 does not select:

- generated/virtual-source ownership;
- multi-owner generated-source semantics;
- dependency/export syntax or visibility policy;
- external immutable dependency indexing;
- Standard Library indexing;
- public BSP protocol shape;
- package-daemon transport;
- source-set/glob/include/exclude manifest syntax;
- source URI presentation/remapping policy;
- persistent index storage;
- project discovery heuristics (which remain prohibited by D082/D085);
- implementation class/helper names or data structures.

Any future requirement for intentionally shared physical or virtual source ownership must
cross a new explicit decision boundary rather than being inferred as an exception to A′.

## Ratification effect

D102 is **RATIFIED — Candidate A′ selected** by explicit project-owner approval on
2026-09-12.

The decision is governance/package-system architecture only. This ratification changes
no Java/runtime implementation, bundled Protos implementation, core language
specification, Maven implementation version or native boundary.

`LM009-G3P P3B` / #413 changes from **BLOCKED_BY_D102_DECISION** to
**BLOCKED_BY_D102_IMPLEMENTATION**. The next bounded work is the shared workspace package
source-boundary implementation. P3B may resume only after that authority is published and
validated.

## Primary prior-art references

- Bazel build encyclopedia/concepts: https://bazel.build/concepts/build-ref
- Buck2 key concepts/packages: https://buck2.build/docs/concepts/key_concepts/
- Go workspaces/modules: https://go.dev/ref/mod and https://go.dev/doc/tutorial/workspaces
- Go module loader source-boundary behavior: https://go.dev/src/cmd/go/internal/modload/search.go
- Swift Package Manager package/target model: https://docs.swift.org/package-manager/
- Cargo workspaces: https://doc.rust-lang.org/cargo/reference/workspaces.html
- Cargo targets: https://doc.rust-lang.org/cargo/reference/cargo-targets.html
- Gradle multi-project builds: https://docs.gradle.org/current/userguide/multi_project_builds.html
- Maven multi-module builds: https://maven.apache.org/guides/mini/guide-multiple-modules.html
- sbt multi-project builds: https://www.scala-sbt.org/1.x/docs/Multi-Project.html
- Mill modules: https://mill-build.org/mill/fundamentals/modules.html
- Node.js packages: https://nodejs.org/api/packages.html
- npm workspaces: https://docs.npmjs.com/cli/using-npm/workspaces
- pnpm workspaces: https://pnpm.io/workspaces
- Build Server Protocol: https://build-server-protocol.github.io/docs/specification
- Pants target/source ownership: https://www.pantsbuild.org/stable/docs/using-pants/key-concepts/targets-and-build-files
- .NET SDK default items: https://learn.microsoft.com/dotnet/core/project-sdk/overview
- uv workspaces: https://docs.astral.sh/uv/concepts/projects/workspaces/
- CMake add_subdirectory: https://cmake.org/cmake/help/latest/command/add_subdirectory.html
