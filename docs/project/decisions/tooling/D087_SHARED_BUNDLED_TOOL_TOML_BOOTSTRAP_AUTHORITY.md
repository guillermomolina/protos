# D087 — Shared bundled-tool TOML bootstrap authority and import boundary

Status: **RATIFIED — Candidate B selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #372

Triggered by: `TOOL002-I5` publication `16a72bbd2cf0f05ca40d799c3e2930412b1d8a65`

Primary consumer: `TOOL002-I` / GitHub #96

Nature: implementation-independent bundled-tool/bootstrap architecture

Normative language effect: **none**.

## Decision boundary

D077 selected strict/versioned TOML 1.0 as the initial persistent encoding family
for Test Tool resource requirements and the environment resource catalog, while
keeping schema meaning in Test Tool rather than TOML itself.

Implementation then reached a parser-ownership boundary:

- the existing canonical bundled-Protos TOML syntax/document machinery lives in
  `protos/tools/package/TomlSyntax.protos` and `TomlDocument.protos`;
- Package Tool reaches those modules through tool-local `self:` imports;
- `ProtosBundledToolModuleResolver` deliberately confines `self:` to one exact
  bundled tool and otherwise delegates only the existing public `std:` space;
- Test Tool therefore cannot reuse that implementation without either changing
  the bundled-tool sharing boundary, duplicating parser authority, making Package
  Tool an accidental provider, promoting a public Standard Library API, or moving
  the authority across the host boundary.

D087 selects how reusable bootstrap parsing is shared. It does not redefine TOML,
D077, Package Tool package semantics, or Protos language semantics.

## Selected architecture — private shared bootstrap layer

Candidate B is selected.

The durable architecture is:

```text
bundled Package Tool schema ----\
                                 +--> private toolchain/bootstrap TOML engine
bundled Test Tool schema -------/
```

The shared layer is toolchain-owned and private. It is reusable by bundled tools
without becoming a public Standard Library contract merely because it is shared.

The selected contract is:

1. one canonical shared source layer owns reusable TOML syntax/document parsing;
2. that layer remains implemented in ordinary Protos source;
3. bundled tools may reach it only through an explicit resolver-private
   toolchain/bootstrap authority or namespace;
4. `self:` keeps its existing meaning and remains exactly tool-local;
5. project/package/runtime resolvers do not gain ambient access to the private
   toolchain namespace;
6. Package Tool and Test Tool use the same parser mechanics while retaining
   separate schema and semantic ownership;
7. TOML dialect selection is explicit and versioned by the consuming persisted
   schema rather than implicitly following whatever dialect a generic parser
   happens to accept;
8. current D077/package schema generations remain pinned to TOML 1.0 until an
   explicit later schema decision selects another dialect;
9. no public `std:toml` API, module layout, public data model or compatibility
   promise is selected by D087;
10. no generic bundled-tool package/dependency graph is selected yet; and
11. ordinary runtime/project execution pays no shared-parser loading cost unless
    a bundled tool actually imports the shared layer.

The exact private import spelling is an implementation detail. A spelling such as
`tool-shared:` may be used by the current implementation, but D087 does not make
that spelling a public Protos module-specifier contract.

## Authority separation

Sharing parser mechanics does not merge tool semantics.

```text
private shared TOML layer
    owns: TOML lexical/syntactic/document mechanics

Package Tool schema
    owns: protos.toml fields, package metadata invariants and package semantics

Test Tool D077 schema
    owns: resource requirement/catalog fields, validation and scheduling-facing
          inert representation
```

A parser node shape is therefore not itself package or Test Tool semantics.
Consumers validate parsed data against their own explicit schema generation.

## Explicit dialect pinning

D087 requires the consuming format generation to select its accepted TOML dialect
explicitly.

Conceptually:

```text
package manifest schema v1       -> TOML 1.0
D077 requirements schema v1      -> TOML 1.0
D077 resource catalog schema v1  -> TOML 1.0
future schema generation         -> separately selected dialect
```

The implementation does not have to expose a public `parse(text, dialect)` API.
Equivalent private entry points or parser configuration are valid, provided an
upgrade of a future generic/public TOML facility cannot silently broaden the
accepted syntax of an older persisted schema generation.

## Confinement and failure

The private shared layer is part of bundled-tool bootstrap authority, not project
module authority.

A conforming implementation must preserve these properties:

- `self:` resolution never escapes the importing bundled tool;
- only the bundled-tool resolver path selected by the toolchain may reach shared
  bootstrap modules;
- ordinary project/package resolution rejects the private toolchain namespace;
- exact path/case/confinement validation remains fail-closed;
- an unavailable, malformed or forbidden private import fails as tooling/module
  infrastructure evidence rather than fabricating guest language semantics;
- no live capability, filesystem handle, provider, credential or mutable global
  authority is embedded in the TOML parser layer.

## Why public `std:toml` is not selected

A public TOML library is a legitimate future Standard Library project, but TOOL002
must not decide its public API accidentally while solving bootstrap reuse.

A future `std:toml` may:

- wrap the same private engine;
- reuse selected lower-level implementation pieces;
- expose a different stable user-facing document model; or
- use another implementation while retaining conformance.

Package/Test Tool bootstrap formats remain independently pinned by their schema
contracts.

## Why Package Tool is not the provider

Test Tool does not import Package Tool merely because Package Tool happened to be
the first consumer of the TOML parser.

Making Package Tool the provider would create the wrong ownership direction:

```text
Test Tool -> Package Tool -> TOML parser
```

and would cause future formatter/linter/documentation/build tools to accumulate
transitive dependence on Package Tool for unrelated syntax mechanics.

D087 instead gives the reusable mechanism its own private toolchain owner.

## Why host/JVM parser authority is not selected

A Java/JVM TOML parser could centralize implementation today, but would make a
current hosting choice the durable owner of an otherwise portable toolchain
mechanism.

Keeping the shared engine in Protos preserves:

- alternate runtime/backend freedom;
- Native Image without a new TOML dependency/reflection surface;
- direct reuse by bundled Protos tools; and
- the option to optimize or replace the implementation later behind the same
  private toolchain boundary.

## Why generated copies are not selected

Generating one source into each tool closure avoids a resolver change but creates
multiple materialized parser identities and adds build/distribution/debugging
machinery.

The selected shared root keeps one runtime identity and one source of truth.

## Why a general internal package graph is deferred

A fully versioned internal package/dependency graph could support independently
versioned bundled tools and complex shared dependency trees, but current need is
one small bootstrap parser family.

D087 follows the Protos rule of preferring the smallest composable mechanism over
a premature institution.

If independently versioned bundled tools later require incompatible shared-module
versions, the private shared root can be promoted into an explicitly versioned
internal tool-module/package graph without changing D077 persisted formats.

## Prior-art findings

The comparative audit in GitHub #372 covered materially different ecosystems.
The recurring evidence is that reuse and public API exposure are separate design
choices.

### LLVM / Clang

LLVM centralizes reusable support/tooling infrastructure in shared libraries and
builds tools above those layers. The useful precedent is library-first toolchain
composition without requiring every shared helper to become a language Standard
Library contract.

### Go

Go explicitly distinguishes reusable public packages from `internal` packages and
uses internal packages extensively for toolchain implementation. This directly
supports a reusable-but-private authority boundary.

### Rust / Cargo

Cargo separates TOML parser/encoding machinery from Cargo-owned schema structures
and higher-level package semantics. This supports D087's parser/schema/semantics
separation.

### Swift / SwiftSyntax

Swift moved parser infrastructure toward reusable Swift implementation rather
than leaving tools dependent on a compiler-host-specific parser boundary. This is
strong evidence for retaining the parser in Protos rather than making the JVM the
durable authority.

### TypeScript and Roslyn

Both ecosystems share compiler/parser machinery across multiple tooling consumers
while maintaining separate service/workspace/public API layers. Shared
implementation does not require identical public representation.

### Java / JDK tooling

`jdk.compiler` demonstrates a toolchain layer distinct from ordinary runtime core
libraries. Tooling infrastructure need not be promoted into the smallest runtime
surface merely to be reused.

### Python `tomllib`

Python's public TOML support demonstrates the value of a general public parser but
also shows why persisted Protos formats need explicit dialect pinning: public
library dialect support can evolve independently from older schema generations.

## Candidate set and focused scores

Scores are 1–5.

| Candidate | Future resilience | Scalability | Protos alignment |
| --- | ---: | ---: | ---: |
| A — public `std:toml` | 2.5 | 5.0 | 2.5 |
| **B — private shared bootstrap layer** | **5.0** | **5.0** | **5.0** |
| C — Package Tool cross-import | 2.5 | 3.0 | 3.0 |
| D — duplicate parser per tool | 1.5 | 2.0 | 1.5 |
| E — host/JVM parser | 2.5 | 5.0 | 2.5 |
| F — generated copies | 3.5 | 3.5 | 3.5 |
| G — general internal tool package graph now | 5.0 | 5.0 | 4.0 |
| H — defer | 3.0 | 1.0 | 3.0 |

## Full comparative scorecard

| Candidate | Correctness | Protos | Future | Scale | Simplicity | Portability | Cost | Failure/ops | Reversible | Evidence |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A | 4.5 | 2.5 | 2.5 | 5.0 | 4.0 | 5.0 | 4.0 | 4.0 | 2.0 | 5.0 |
| **B** | **5.0** | **5.0** | **5.0** | **5.0** | **4.5** | **5.0** | **5.0** | **5.0** | **4.5** | **4.5** |
| C | 4.5 | 3.0 | 2.5 | 3.0 | 4.0 | 4.5 | 5.0 | 3.5 | 2.5 | 4.0 |
| D | 3.0 | 1.5 | 1.5 | 2.0 | 3.5 | 5.0 | 4.0 | 2.0 | 2.5 | 5.0 |
| E | 4.5 | 2.5 | 2.5 | 5.0 | 4.0 | 2.0 | 5.0 | 4.5 | 3.0 | 5.0 |
| F | 4.0 | 3.5 | 3.5 | 3.5 | 2.5 | 5.0 | 4.0 | 3.0 | 3.5 | 4.0 |
| G | 5.0 | 4.0 | 5.0 | 5.0 | 3.0 | 5.0 | 4.5 | 4.5 | 4.0 | 4.0 |

The scorecard is comparative evidence rather than arithmetic decision authority.
Candidate B is selected because it satisfies the hard ownership/confinement
constraints while preserving the strongest future escape paths with less
institutional complexity than Candidate G.

## Future-scenario stress test

### More bundled tools

Additional formatter, linter, documentation or build tools can reuse the same
private syntax/document machinery without making Package Tool their owner and
without parser copies.

### TOML 1.1 or later

Older persisted schema generations remain pinned to TOML 1.0. New generations may
select a later dialect explicitly.

### Future public `std:toml`

D087 does not constrain its public API. A public facility can reuse, wrap or
replace the private engine while tool schema compatibility remains stable.

### Alternate runtime / Native Image

The parser remains ordinary Protos source and requires only an equivalent private
bundled-tool resolution boundary from another implementation.

### Distributed Test Tool

Persistent requirement data is parsed into inert D077 records before scheduling
and distribution. Workers need the inert plan data, not parser authority.

### Independently versioned bundled tools

This is the strongest regret scenario. If one installation must host bundled tools
with mutually incompatible versions of shared bootstrap modules, a single
coherently-versioned shared root is insufficient.

The escape path is to promote the private root into a versioned internal tool
module/package graph while retaining parser/schema separation and all persisted
D077 formats.

## Rejected alternatives

### A — public `std:toml`

Rejected for this decision because it prematurely creates a public compatibility
surface and couples bootstrap persisted formats to a generic library lifecycle.

### C — Package Tool cross-import

Rejected because the first consumer is not the correct long-term owner of a
reusable mechanism.

### D — duplicate parser per tool

Rejected because parser fixes, limits and dialect acceptance can drift across
multiple independently mutable copies.

### E — host/JVM parser

Rejected because it makes the current host implementation the durable parser
owner and weakens alternate-runtime freedom.

### F — generated copies

Rejected because it exchanges a small resolver boundary for build-time generation,
multiple materialized identities and distribution/debugging complexity.

### G — general internal tool package graph now

Deferred rather than rejected forever. It is the natural evolution if actual
independent tool versioning or complex shared dependency graphs appear.

### H — defer

Rejected because it leaves already-ratified D077 persistent functionality blocked
without reducing the eventual architecture question.

## Downstream release

D087 releases the next bounded TOOL002-I work that needs canonical TOML parsing.
That implementation may establish the private shared source root and private
resolver path, migrate the existing Package Tool TOML syntax/document modules to
that shared owner, and let Test Tool consume the same engine.

It must still stop before inventing any D077-deferred contract, including:

- physical requirement-sidecar filename;
- catalog CLI spelling/source path;
- exact public scope vocabulary;
- provider API/profile contract;
- reservation/fairness/retry/timeout/remote/sharding policy; or
- a general internal bundled-tool dependency graph.

## Explicitly deferred

D087 does not select:

- public `std:toml` API/module/data model;
- TOML 1.1 or later adoption for an existing persisted schema generation;
- final private namespace spelling as a public contract;
- independently versioned bundled-tool dependency resolution;
- physical D077 requirement/catalog source names;
- catalog scope vocabulary;
- provider/capability API; or
- scheduling/reservation semantics beyond D076/D077.
