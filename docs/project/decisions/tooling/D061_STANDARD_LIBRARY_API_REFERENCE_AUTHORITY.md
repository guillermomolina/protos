# D061 — Standard Library API reference authority and generation model

Status: **RATIFIED**

Allocated: **2026-09-10**

Explicit project-owner approval: **2026-09-10**

Nature: durable implementation-independent tooling/documentation architecture decision

Triggered by: `WEB001-J7B` / GitHub #301 after WEB001-J7A exact-SHA source-browser publication

Decision issue: GitHub #311

Primary consumers: WEB001-J7B/J7C, future Protos documentation tooling, CLI/IDE/LSP/package-documentation consumers

Normative language effect: **none**. D061 selects how Standard Library API documentation
facts are obtained, authored, validated and distributed. It does not add language syntax,
exports, privacy, reflection, types, annotations, runtime documentation objects, or new
Standard Library semantics. Normative observable Protos and Standard Library semantics
remain owned by the applicable material under `spec/`.

## Problem

WEB001-J7A can safely render exact Standard Library source because source identity is
mechanical. A true API reference is different: implementation structure can show what is
present, but it cannot by itself declare documentation intent, stability, semantic
contracts, effects, errors, capability requirements, compatibility promises, or whether
a source-backed Core facility is an importable `std:` API.

The website must therefore not infer semantic promises from `protos/lib/**`, and it must
not become a second authority by maintaining an independent hand-written API corpus.

D061 decides the canonical ownership and data flow before WEB001-J7B implements any
semantic/API reference.

## Current Protos constraints

1. A module instance is its ordinary `moduleContext`; top-level bindings become slots of
   that module instance. Core v0.1 has no separate export declaration or export namespace.
2. The observable imported module surface is therefore mechanically connected to top-level
   source structure, but source presence alone does not express documentation/stability
   intent.
3. `std:` resolution is a host/tooling policy. The current standard-library resolver
   rejects a first logical segment named `core`; therefore `protos/lib/core/**` is
   distributable source-backed Core material, not an importable `std:core/...` namespace.
4. Existing `LIBxxx` records contain valuable selected surfaces and rationale, but they are
   non-normative project/design records shaped around work history rather than a uniform
   API documentation model.
5. Core v0.1 currently has only ordinary line/block comments. There is no special language
   category of documentation comment.
6. `protos-website` is a read-only exact-SHA renderer/curator. It must consume canonical
   Protos-owned documentation data rather than invent API meaning.
7. The project design philosophy prefers mechanisms over institutions, ordinary things
   remaining ordinary, composability, pay-only-for-use, and scaling by composition.

## Ecosystem audit

The project-owner approval followed an explicit cross-language documentation/tooling audit.
The recurring mature pattern is not one particular comment spelling; it is the separation
of mechanically known symbol facts from authored semantic explanation.

### Rust / rustdoc

Rustdoc consumes compiler-known declarations and visibility/reachability while combining
them with source-adjacent documentation. The renderer does not manually re-declare the
program's symbol graph. This provides strong drift resistance.

### Go / go doc / pkg.go.dev

Go's documentation convention is lightweight and source-adjacent. Tooling derives declaration
identity mechanically and associates ordinary documentation prose with those declarations.
The same source model feeds command-line, web and language-service tooling.

### Java / Javadoc

Javadoc attaches documentation to compiler-known program elements. Signatures and containment
come from the language model rather than from a separately maintained website representation.

### C# / XML documentation

C# tooling associates structured documentation with compiler symbols and can emit an
intermediate documentation artifact keyed by stable symbol identities. This demonstrates the
utility of a reusable machine-readable layer separate from any final website renderer.

### Swift / Symbol Graph + DocC

Swift provides the closest architectural analogue to the selected direction: compiler-produced
symbol information is combined with source-authored documentation and richer conceptual
material, then rendered by downstream tooling. Mechanical truth and authored explanation
remain distinct inputs.

### Kotlin / Dokka

Dokka consumes Kotlin/Java program structure and KDoc/Javadoc instead of treating generated HTML
as an authority. The documentation engine remains separate from language/runtime semantics.

### Haskell / Haddock

Haddock uses declarations, signatures and exports mechanically and associates documentation with
those symbols. Structural facts can remain useful even when prose is incomplete.

### Dart / dart doc

Dart tooling consumes analyzer-known declarations plus source documentation and validates the
documentation against actual program structure.

### TypeScript / TypeDoc

TypeDoc derives declarations from the TypeScript model and combines them with source comments.
The pattern again avoids a website-specific duplicate symbol universe.

### C / C++ / Doxygen

Doxygen demonstrates both the strengths and the risks of source extraction. It can generate a
large amount of structure automatically and can emit neutral XML for downstream consumers, but
source visibility alone does not determine semantic/stability intent. This supports extraction
for facts, not inference for promises.

### Ruby / RDoc

RDoc shows that a dynamic language can generate useful symbol-oriented documentation through
static/source parsing without executing arbitrary application code.

### Python / pydoc and maintained Standard Library manuals

Python demonstrates two useful but distinct patterns: runtime/docstring introspection is
convenient for discoverability, while the maintained Standard Library manuals provide richer
semantic explanation. For Protos, runtime loading would be a poor canonical extractor because
module initialization may have effects and authority requirements.

### Julia

Julia's docstrings integrate well with runtime/REPL discovery but make documentation a more
explicit runtime/language institution. D061 deliberately avoids requiring such a category in
Protos.

### Elixir / ExDoc

Elixir combines compiler/module metadata and explicit documentation metadata, and distinguishes
whether something appears in documentation from whether it is semantically callable/exported.
That distinction is directly useful for Protos: documentation intent must not redefine module
semantics.

### Smalltalk / Pharo

Smalltalk-family practice keeps explanatory material close to classes/methods/objects while
preserving reflection and object semantics independently. It reinforces source locality without
requiring documentation to become a separate semantic universe.

## Alternatives evaluated

### A — infer the complete API from `protos/lib/**`

Mechanical extraction has excellent freshness for names and source locations, but cannot
correctly infer stability, semantic contracts, effects, errors, capability requirements or
documentation intent. It also misclassifies physical `lib/core` placement as potential `std:`
identity unless extra policy is reintroduced.

Rejected as a complete model. Mechanical extraction remains a component of the selected model.

### B — use existing `LIBxxx` work/design records as API authority

These records are valuable design evidence and often contain exact selected surfaces, but they
are deliberately non-normative and lifecycle/history shaped. Promoting them to user-facing API
authority would conflate project history with maintained product documentation.

Rejected.

### C — maintain API metadata/pages in `protos-website`

This gives the renderer direct control but creates a second semantic/documentation corpus,
requires synchronization with Protos source, and makes non-web consumers depend on a website
repository.

Rejected.

### D — source-adjacent documentation comments only

This provides excellent locality and contributor ergonomics and matches several mature
ecosystems. By itself, however, it does not define a reusable symbol model, stable identity,
cross-consumer interchange or how mechanically observable facts are validated.

Retained as a likely authoring component, but not selected as the whole architecture.

### E — separate explicit structured API contract

A Protos-owned structured contract would provide clear authority and reusable machine-readable
data. Its main weakness is duplication: module identity, selectors, callable parameter shapes and
source locations would be written manually even though Protos tooling can derive them.

Retained as a fallback for facts that genuinely cannot be extracted mechanically.

### F — Protos-owned extraction + canonical authored docs + neutral model

Selected.

Mechanically observable facts come from a Protos-owned source/symbol extractor; semantic and
documentation intent is authored explicitly in the canonical `guillermomolina/protos`
repository; validation combines the two layers; and one versioned implementation-neutral
documentation model is emitted for website, CLI, IDE/LSP and future package documentation.

## Comparative scoring

Scores use the project-owner requested dimensions on a 1–10 scale.

| Candidate | Future viability | Scalability | Protos philosophy | Total |
| --- | ---: | ---: | ---: | ---: |
| A — source inference only | 5 | 8 | 5 | 18/30 |
| B — `LIBxxx` records as API | 4 | 4 | 6 | 14/30 |
| C — website-owned API corpus | 3 | 5 | 2 | 10/30 |
| D — source-adjacent docs only | 8 | 8 | 8 | 24/30 |
| E — explicit structured contract | 9 | 8 | 7 | 24/30 |
| **F — extracted facts + authored docs + neutral model** | **10** | **10** | **10** | **30/30** |

The score is not authority by itself; Candidate F became selected only through explicit
project-owner approval after the comparative audit.

## Ratified decision — Candidate F

The durable model is:

```text
canonical Protos source
        |
        | mechanically observable facts
        v
Protos-owned symbol/documentation extractor
        ^
        |
canonical authored documentation/metadata
in guillermomolina/protos
        |
        +----------------------+
                               |
                               v
              validation + versioned neutral
                  documentation model
                               |
              +----------------+----------------+
              |                |                |
              v                v                v
       protos-website        CLI/tooling       IDE/LSP
```

### Mechanical layer

A Protos-owned extractor may derive only facts that are objectively determined from the
applicable source/tooling model, including when available:

- canonical importable module identity;
- canonical source path and revision;
- top-level slot/selector identity;
- source location;
- callable parameter names and rest-parameter shape when structurally knowable; and
- other similarly mechanical facts whose extraction does not require guessing programmer
  intent.

The mechanical layer must not infer:

- return types absent an independently defined type model;
- stability/deprecation promises;
- semantic laws or behavioral contracts;
- error taxonomy beyond independently authoritative facts;
- side-effect or purity guarantees;
- authority/capability requirements;
- thread/concurrency guarantees;
- compatibility guarantees; or
- documentation/publication intent from implementation-body shape.

### Authored documentation layer

Human-authored API explanation remains canonical project content in
`guillermomolina/protos`, close to the implementation or module/family owner.

This layer may describe summaries, contracts, errors, effects, capability requirements,
stability, examples and cross-links, but it does **not** acquire independent normative
language authority merely by being consumed by the API documentation pipeline. Observable
semantics remain governed by the applicable normative specification and ratified semantic
material.

The exact authoring convention is intentionally not selected by D061. In particular, D061
does not ratify `///`, annotations, docstrings, sidecar YAML/JSON, Markdown front matter, or
another spelling/format.

### Neutral documentation model

The combined validated output must be a versioned implementation-neutral documentation
model owned by Protos tooling/project sources, not by Astro/Starlight or another website
framework.

Its purpose is to allow multiple consumers to use one symbol/documentation truth:

- `protos-website`;
- future `protos doc` or related CLI tooling;
- IDE hover/completion/detail surfaces;
- LSP/static-language-service tooling;
- package documentation/indexing; and
- future non-web renderers.

The neutral model itself is documentation/tooling data. It does not create runtime objects
or language semantics.

### Validation and drift resistance

The pipeline must fail rather than silently invent or retain stale identities when mechanical
facts and authored documentation disagree.

At minimum, later implementation must establish stable checks for:

- documented module identity resolving to the intended canonical module;
- documented symbol identity still existing at the expected module/top-level boundary;
- structural callable facts matching extracted source facts when those facts are represented;
- duplicate documentation identity;
- stale documentation for removed/renamed symbols; and
- undocumented mechanically discovered surface according to whatever coverage policy is later
  explicitly selected.

Coverage policy itself is not silently selected here: D061 does not require every mechanically
observable top-level slot to be published as user-facing stable API.

### Core versus Standard Library

`protos/lib/core/**` may remain visible in the WEB001-J7A source browser because it is canonical
source. It must **not** be automatically represented as `std:core/...` API.

The semantic/API reference for importable Standard Library modules must use actual canonical
`std:` identities recognized by the standard-library resolution policy. Source directory
placement is not sufficient API identity.

Core behavior belongs under the applicable Core/language reference and specification authority
unless a separately approved documentation model says otherwise.

## Scalability

Candidate F scales in several independent dimensions.

### Library size

Mechanical identities are extracted once rather than manually copied into every renderer.
Growing from tens to hundreds or thousands of modules/symbols therefore increases documentation
data approximately with the API itself instead of multiplying synchronization work across
website, IDE and CLI representations.

### Multiple consumers

The neutral model prevents each consumer from independently parsing Protos source and inventing
slightly different rules. Consumer count does not multiply semantic/documentation authority.

### Third-party packages

The architecture can later be applied to package-owned modules without requiring those packages
to use Astro/Starlight or the Protos website repository. A package can provide source plus the
selected canonical authoring convention, while Protos tooling emits the same neutral model.

### Parallel/versioned builds

Documentation extraction is deterministic per exact source revision. Different package/release
versions can be documented independently without global mutable registries or runtime module
execution.

### Runtime cost

Normal Protos programs pay no runtime cost for documentation extraction or rendering. The model
is build/tooling-time only.

## Protos alignment

Candidate F preserves the project's design philosophy:

- **mechanisms over institutions:** source extraction + ordinary documentation metadata rather
  than a new runtime `Documentation` object or language-level export/doc subsystem;
- **ordinary things remain ordinary:** modules and slots keep their existing semantics;
- **general rules beat special cases:** one symbol/documentation pipeline can serve stdlib,
  packages, web, CLI and IDE consumers;
- **pay only for what you use:** documentation tooling is inactive during ordinary execution;
- **scale by composition:** the same source/model pipeline composes into additional renderers;
- **generality is earned:** only mechanically reliable facts are generalized;
- **minimize shared mutable state:** exact-revision immutable documentation artifacts replace
  a mutable website-owned registry; and
- **semantics before implementation:** the documentation architecture does not let Astro,
  source layout or runtime convenience define API semantics.

## Rejected implementation shortcuts

D061 explicitly rejects:

- scraping generated HTML as canonical documentation data;
- making `protos-website` the semantic/API documentation authority;
- executing arbitrary modules merely to discover documentation;
- treating every top-level source slot as automatically stable/documented public API;
- treating every `protos/lib/**` path as an importable `std:` API;
- copying signatures manually into a second manifest when they are mechanically extractable;
- promoting historical `LIBxxx` design records into normative API manuals; and
- adding export/private/doc syntax solely to make documentation generation easier.

## Intentionally deferred decisions

D061 does **not** select:

- exact documentation-comment spelling (`///`, ordinary preceding `//`, block comments, etc.);
- whether symbol-level prose lives solely adjacent to source or may use sidecar Markdown;
- the exact schema/serialization format of the neutral model;
- stable symbol-ID encoding;
- public/private/documented coverage policy for ordinary top-level slots;
- deprecation/stability metadata vocabulary;
- cross-package linking format;
- whether a future CLI command is named `protos doc`;
- package publication requirements for documentation;
- website layout/presentation of the API model; or
- a language-level documentation/reflection API.

Any deferred item that materially constrains future tools/packages/ecosystem behavior must cross
the normal Dxxx approval gate when it becomes necessary.

## Downstream work

D061 releases the architectural blocker on WEB001-J7B, but WEB001 must not implement the
cross-ecosystem extractor/model inside website code.

The expected sequence is:

1. allocate the appropriate Protos-owned tooling/documentation work for the extractor and
   neutral model;
2. resolve any substantive authoring/schema/identity decisions through their own Dxxx gate;
3. prove the mechanism first on a bounded importable family such as `std:collections`;
4. extend it to the remaining importable Standard Library;
5. then let WEB001-J7B/J7C consume the exact-SHA neutral model as a pure renderer.

## Ratification closure

The project owner explicitly approved **Candidate F** on 2026-09-10 after the exhaustive
cross-language comparison and scoring against future viability, scalability and Protos
philosophy.

Result:

```text
D061       RATIFIED — Candidate F
WEB001-J7A VALID — exact-SHA source browser unchanged
WEB001-J7B ARCHITECTURE RELEASED — implementation remains dependent on Protos-owned doc tooling
WEBSITE     NOT API AUTHORITY
SOURCE      MECHANICAL FACT AUTHORITY ONLY
DOC MODEL   VERSIONED / NEUTRAL / PROTOS-OWNED
```

This ratification changes no Protos specification, implementation, implementation version,
runtime behavior, public Standard Library semantics, module/export semantics, source files,
package format, website repository, or deployment configuration.
