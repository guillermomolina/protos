# D064 — Neutral documentation model schema and stable symbol identity

Status: **RATIFIED — Candidate G-prime**

Allocated: **2026-09-10**

Explicit project-owner approval: **2026-09-10**

Nature: durable implementation-independent tooling/documentation architecture decision

Triggered by: D061 / GitHub #311, D062 / GitHub #313 and WEB001-J7B / GitHub #301

Decision issue: GitHub #317

Normative language effect: **none**. D064 defines a neutral documentation interchange model and
stable documentation-symbol identity contract. It does not add Protos syntax, runtime types,
reflection state, export/private semantics, overload semantics, Standard Library behavior or
execution semantics.

## Upstream boundary

D061 ratified the Protos-owned documentation architecture:

- mechanically observable module/symbol facts come from Protos-owned extraction;
- authored API explanation remains canonical in `guillermomolina/protos`;
- extraction and authored material are validated together;
- downstream consumers share one versioned implementation-neutral model; and
- the website is a renderer/consumer rather than API authority.

D062 ratified Candidate E-prime for authoring:

- `//!` documents the containing module;
- `///` documents the immediately following documentable top-level symbol;
- both are tooling-only conventions over ordinary Protos `//` comments;
- ordinary comments remain non-API comments; and
- canonical supplemental Markdown carries longer narrative material.

D064 resolves only the data-model, stable-identity, provenance, evolution and deterministic
interchange boundary required before a reusable extractor/model proof can be implemented.

## Problem

The documentation pipeline needs one artifact usable by website, CLI, IDE/LSP, search/index tools
and future package documentation without making any of these representations authoritative.

Identity must survive changes that do not change the documented semantic binding, including:

- source-file moves;
- harmless formatting changes;
- line/column shifts;
- repository checkout location changes; and
- callable-shape evolution of an existing top-level slot.

At the same time the model must distinguish exact appearances of a symbol in different releases,
revisions or package instances, must not collapse true symbol/module renames, and must not encode
parser, AST, Truffle, website-route or local-filesystem implementation details as durable identity.

## Prior-art audit

The decision packet and ratification review compared mature and materially distinct documentation,
compiler-symbol and code-intelligence models across the following ecosystems.

### Swift Symbol Graph / DocC

Swift Symbol Graph provides the strongest complete graph precedent: a versioned interchange model
contains semantic symbol nodes and typed relationships while keeping source/presentation facts
separate. Its precise identifiers demonstrate that semantic node identity scales across tooling,
but Swift's mangled declaration universe is richer than Protos needs.

**Lesson:** adopt the versioned semantic graph boundary, not Swift-specific mangling or nominal
language categories.

### Rust / rustdoc JSON

Rustdoc emits a transformed documentation model instead of exposing compiler internals directly.
Its numeric/opaque item IDs are intentionally meaningful only inside one emitted JSON blob.

**Lesson:** artifact-local compact IDs are valid implementation optimizations but must never be
confused with durable cross-artifact `SymbolIdentity`.

### Go

Go tooling identifies package-level declarations semantically by package/name relationships while
keeping source positions as location facts.

**Lesson:** logical package/module plus declared binding name is a stronger identity basis than
path plus line/column.

### C# / Roslyn documentation IDs

C# documentation IDs include qualified semantic names and overload-disambiguating signature facts.
That complexity is justified because multiple members with the same name can be different semantic
entities.

**Lesson:** semantic IDs scale, but Protos must not import overload dimensions that its top-level
slot model does not have.

### Java / Javadoc Doclet

Modern Javadoc exposes language-model elements to tooling instead of requiring renderers to consume
javac AST implementation classes.

**Lesson:** the neutral model must represent stable program concepts, not parser/compiler objects.

### Kotlin / Dokka

Dokka separates documentables and durable resource identity from later page/content rendering.

**Lesson:** documentation identity must not be an Astro/page hierarchy.

### TypeScript / TypeDoc

TypeDoc validates the usefulness of a serializable intermediate reflection model but also shows
that model-instance numeric IDs are not sufficient as durable public identities.

### Clang USR

Clang USRs prove that compiler-derived semantic identities can support large cross-file indexes.

**Lesson:** cross-file semantic identity is valuable, but a compiler-mangled opaque USR is a larger
institution than current Protos semantics require.

### Doxygen XML

Doxygen demonstrates that renderer-independent structured interchange and incremental compound
processing can scale to very large heterogeneous codebases.

**Lesson:** neutral interchange scales; Doxygen's universal declaration taxonomy is intentionally
broader than the Protos documentation universe.

### Haddock and odoc

Haddock and odoc bind documentation navigation to modules/declarations and resolve semantic paths
before presentation. Odoc additionally combines declaration documentation with longer narrative
units.

**Lesson:** semantic identity and long-form articles can coexist without making output filenames or
HTML routes authoritative.

### Dart / dartdoc

Dart tooling consumes analyzer-known declarations and authored Markdown rather than treating
rendered pages as identity authority.

### Elixir / ExDoc

ExDoc's module/name/arity identity is a useful dynamic-language precedent. Arity participates there
because the language makes it semantically identifying.

**Lesson:** in Protos callable shape remains data unless Protos someday acquires a genuine
same-slot overload-like semantic distinction.

### Self

Self is especially relevant to Protos because its programming environment associates information
with objects and slots and treats module transport at slot granularity.

**Lesson:** slot identity, not source coordinates, is the natural documentation unit for a
prototype language. Protos need not import Self's live-image annotation institution.

### Smalltalk / Pharo

Smalltalk browsers organize methods by their semantic owner and selector rather than by source
position.

**Lesson:** selector/binding-centric identity is robust under source movement.

### Io

Io's prototype/slot model reinforces the same structural lesson: documentation naturally attaches
to proto/slot identity, not a nominal type universe or page route.

### Clojure and Racket

Clojure Vars and Racket bindings reinforce the distinction between a semantic named binding and
metadata such as arglists, documentation and source location.

**Lesson:** callable shape and provenance can change while the binding lineage remains the same.

### SCIP

SCIP demonstrates scalable global semantic symbol keys and language-agnostic index interchange.
Its global keys intentionally include package-version information because SCIP principally indexes
exact code occurrences.

**Lesson:** version-qualified occurrence identity is useful, but Protos documentation also needs a
stable lineage identity that remains comparable across releases.

### LSIF

LSIF demonstrates the value of a reusable precomputed language-server graph, but an LSP-shaped
transport would make editor/navigation protocol concerns part of documentation authority.

### Kythe

Kythe deliberately separates several identity dimensions and revision/provenance facts and shows
that persistent semantic graphs can be transformed into optimized serving/index formats.

**Lesson:** keep durable identity dimensions minimal and allow later serving representations
without redefining semantic identity.

## Cross-ecosystem conclusions

1. Source path, line and column are provenance, not durable semantic identity.
2. Renderer-local or artifact-local numeric IDs are useful optimizations, not durable identities.
3. Semantic package/module/binding identity scales across files and tools.
4. Compiler mangling can scale but is unnecessary when the language has a smaller semantic
   universe.
5. Release/revision/content identity must be distinct from symbol-lineage identity so one API
   symbol can be followed across releases.
6. A normalized graph-like model scales better than a renderer/page tree for multiple consumers.
7. The graph should remain compact and add relationship kinds only when they are earned.
8. Documentation interchange must not serialize parser ASTs, Truffle objects or runtime object
   graphs.
9. Format/schema version and generator version are independent facts.
10. Prototype-language prior art strongly favors module/slot binding identity.

## Candidate comparison

| Candidate | Future | Scale | Protos | Total | Outcome |
| --- | ---: | ---: | ---: | ---: | --- |
| A — source path + line/column | 3 | 6 | 4 | 13/30 | reject |
| B — hierarchical page/module tree | 7 | 7 | 8 | 22/30 | viable renderer model, weak authority model |
| C — artifact-local numeric IDs | 6 | 9 | 7 | 22/30 | useful locally, not durable |
| D — persistent UUID/content-hash identity | 6 | 9 | 5 | 20/30 | hidden registry/state or edit-sensitive identity |
| E — generic SCIP/Kythe/LSIF-style graph | 9 | 10 | 6 | 25/30 | scalable but unnecessarily broad |
| F — parser/AST/Truffle serialization | 4 | 6 | 2 | 12/30 | reject |
| **G-prime — compact semantic graph with separate occurrence/provenance identity** | **10** | **10** | **10** | **30/30** | **RATIFIED** |

## Ratified decision — Candidate G-prime

### 1. Keep semantic lineage identity separate from exact occurrence identity

D064 ratifies two related but distinct concepts.

`SymbolIdentity` answers:

> Which semantic documented binding lineage is this?

`SymbolOccurrenceKey` answers:

> Which exact appearance of that semantic binding in one exact documentation artifact is this?

Conceptually:

```text
ModuleLineageIdentity =
    canonical std:<logical-module-name>
    | (PackageId, logical-module-name)

SymbolIdentity =
    (ModuleLineageIdentity, top-level-slot-name)

SymbolOccurrenceKey =
    (ExactArtifactScope, SymbolIdentity)
```

The exact serialized spelling of these structures may be defined mechanically by the schema, but
no independent UUID/hash registry may become a second source of identity.

### 2. Standard Library module identity

For the official Standard Library, use the canonical `std:` module identity selected by the
standard-library resolver, for example:

```text
std:collections/Set
```

The physical path such as:

```text
protos/lib/collections/Set.protos
```

is provenance. It is not module identity.

This preserves the already-established distinction that physical `protos/lib/core/**` placement
does not imply an importable `std:core/...` identity.

### 3. External package module identity

When package-system identity is available, external package-backed module lineage uses:

```text
(PackageId, logical-module-name)
```

It must not reconstruct identity from:

- dependency alias;
- human-facing package locator;
- registry/repository URL;
- release version;
- VCS revision;
- `ContentIdentity`;
- source/cache path; or
- transport artifact digest.

D064 consumes the package system's durable `PackageId` authority; it does not redefine how
`PackageId` is generated or authenticated.

### 4. ExactArtifactScope

An exact documentation artifact carries enough scope to distinguish simultaneous appearances of
the same semantic symbol lineage in different releases/revisions.

Depending on the resolved source kind, exact scope may include the already-authoritative exact
facts needed to distinguish that artifact, for example:

- exact VCS revision;
- exact `ReleaseVersion`;
- applicable `ContentIdentity`; and
- package/repository provenance kind.

Those facts identify the exact artifact/occurrence, not the durable `SymbolIdentity` lineage.

This permits indexes to load, for example, package `1.x`, `2.x` and `main` simultaneously while
still recognizing `collections/Set :: contains` as one semantic lineage when the package/module/
slot identity is unchanged.

### 5. Callable shape is mechanical data, not current identity

For current Protos top-level slots, parameter names/order and rest-parameter shape are properties
of the identified slot.

They do **not** participate in `SymbolIdentity` because one canonical module cannot simultaneously
contain two different top-level slots with the same slot name under a separate overload identity.

Therefore a change from conceptually:

```text
contains: (set, element) => { ... }
```

to another callable shape remains a change to the same `contains` symbol lineage. Documentation
and API-diff tooling should report changed callable facts rather than manufacturing a new symbol.

If Protos later introduces genuinely overload-like same-slot identity semantics, that is a new
substantive semantic condition and requires a new explicit design decision rather than silently
expanding D064 identity dimensions.

### 6. Rename semantics

Renaming the logical module or top-level slot creates a new `SymbolIdentity`.

For example:

```text
std:collections/Set :: contains
std:collections/Set :: has
```

are different semantic identities.

Tooling must not infer continuity from source similarity, line proximity, matching implementation
body, content hashes or edit distance.

A future explicit relation such as `renamedFrom` may connect identities when independently
justified, but D064 does not ratify such a relationship kind or rename policy.

### 7. Binding identity is not runtime value identity

Documentation identifies the module/top-level binding. Two distinct module slots remain distinct
documentation symbols even if their current runtime values happen to reference the same object or
Closure.

Conversely, replacing the value stored in an existing top-level slot does not by itself allocate a
new documentation identity when module/slot lineage remains unchanged.

This keeps documentation out of JVM/Truffle object identity and matches Protos' ordinary slot
model.

### 8. Neutral model is graph-lite, not a renderer tree

The canonical model is normalized around entities and explicit references:

```text
artifact
  metadata
  modules[]
  symbols[]
  articles[]
  relationships[]
```

`symbols[]` reference module semantic identity. Relationships reference semantic identities or
exact occurrences only according to the relationship's explicitly defined semantics.

Website navigation hierarchy, filenames, Astro routes and HTML anchors are downstream
presentation concerns and are not authority.

### 9. Initial structural kinds remain deliberately small

The initial neutral model recognizes only documentation-structural categories it currently earns:

- `module`;
- `slot`; and
- `article`.

`kind` is documentation structure. It is **not** a nominal Protos `type` system and does not create
runtime classification semantics.

A `slot` may carry an optional mechanically extracted `callable` structure containing parameter
and rest-shape facts.

New kinds require a real documentation-model need. They must not be invented merely to mirror
Java/Swift/C#/compiler declaration taxonomies.

### 10. Authored documentation payload

Canonical `//!` and `///` content enters the neutral model as Markdown text associated with the
resolved module/symbol identity.

Generated HTML is never the authoritative documentation payload.

Supplemental articles are separate document entities and may relate to modules/symbols. D064 does
not silently select the source-side article-ID/association convention; the first model proof may
contain zero supplemental articles if necessary.

### 11. Provenance is explicit and non-identifying

Each exact artifact/record may carry source provenance such as:

- repository/package lineage;
- exact revision/release/content identity as applicable;
- canonical logical module identity;
- repository-relative source path; and
- normalized source range.

Absolute host paths, usernames, home directories, cache directories, credentials, tokens and other
machine-local/private execution details are forbidden from the canonical artifact.

A source move or line-number change therefore changes provenance while preserving semantic
identity.

### 12. JSON v1 canonical interchange

The first canonical serialization is UTF-8 JSON.

Reasons:

- directly consumable by website/Node tooling;
- straightforward for JVM CLI tooling;
- convenient for IDE/LSP integrations and static indexes;
- human inspectable and diffable;
- no generated binding/runtime dependency;
- suitable for exact-revision static artifacts; and
- sufficient for current and foreseeable Standard Library/package documentation scale.

This choice is a serialization boundary, not a permanent prohibition on optimized derived formats.
Future sharded JSON, SQLite/index databases, CBOR, Protocol Buffers or other serving/cache formats
may be derived without redefining `SymbolIdentity`.

### 13. Format evolution

Top-level metadata includes at least conceptually:

```text
format.name
format.major
format.minor
generator.name
generator.version
provenance
modules
symbols
articles
relationships
```

Compatibility rules:

1. incompatible model or meaning changes increment `format.major`;
2. backward-compatible additive fields/relationship kinds may increment `format.minor`;
3. consumers must reject unsupported major versions;
4. consumers may ignore unknown additive minor fields they do not need; and
5. generator version is provenance/tooling metadata and does not redefine schema semantics.

### 14. Deterministic exact-input artifacts

For the same effective generation input — including the serialized format/generator metadata — the
canonical JSON must be byte-deterministic.

The v1 canonical emitter therefore uses:

- UTF-8;
- LF newlines;
- no timestamps;
- no absolute/machine-local paths;
- deterministic object-field emission;
- modules/symbols/articles sorted by canonical semantic identity;
- relationships sorted by a deterministic relationship key;
- one documented normalized source-range convention; and
- one final newline.

A digest of the final artifact may be used for cache/integrity identity. It must never become
`SymbolIdentity`.

### 15. Duplicate identity is an error

Within one `ExactArtifactScope`, the extractor/model validator must reject duplicate records for the
same canonical `SymbolIdentity`.

Tooling must not repair a collision by fabricating identifiers such as:

```text
contains#2
contains@line42
contains(set,element)
```

If the language later acquires a real additional identity dimension, that semantic boundary must
be designed explicitly.

### 16. Local compact indexes are allowed

An implementation may assign dense numeric indexes to entities/relationships inside one artifact
or one derived serving structure for memory/performance reasons.

Such indexes are explicitly local, replaceable optimizations. They are never externally durable
semantic symbol identities.

### 17. Incremental and large-package scaling

The identity contract supports incremental extraction naturally:

- unchanged module/slot identities can be matched without source-coordinate heuristics;
- changed source ranges update provenance only;
- changed callable shape updates symbol facts on the same lineage;
- renamed modules/slots are explicit remove/add identity changes; and
- exact occurrence scope prevents collisions when multiple versions/revisions coexist.

A large consumer may shard or index by package/module identity while preserving the same canonical
semantic keys. Scaling does not require a different documentation universe.

### 18. Multi-package and multi-version coexistence

Indexes may simultaneously contain:

```text
PackageId P / 1.7.0 / collections/Set / contains
PackageId P / 2.0.0 / collections/Set / contains
PackageId P / main  / collections/Set / contains
```

The exact occurrences are different because their `ExactArtifactScope` differs. The semantic
`SymbolIdentity` lineage remains comparable when package/module/slot identity is unchanged.

This separation mirrors Protos' package-system distinction among `PackageId`, release/revision,
`ContentIdentity` and retrieval/source provenance.

## Why G-prime is the Protos-aligned option

G-prime minimizes the semantic/tooling universe while preserving future scale.

It follows existing Protos design philosophy directly:

- **small universe:** only module/slot/article documentation concepts currently earned;
- **mechanisms over institutions:** structural semantic keys instead of a hidden UUID registry;
- **ordinary things remain ordinary:** top-level slots remain the documentation symbols;
- **semantic distinctions remain visible:** lineage, exact occurrence, source provenance, release
  identity and renderer URL remain separate concepts;
- **pay only for use:** no runtime reflection/documentation state or global symbol registry;
- **scale by composition:** large/multi-version indexes use the same identity model plus derived
  serving representations; and
- **generality is earned:** no nominal declaration taxonomy or universal code graph is introduced
  before Protos needs it.

## Deliberate non-selections and deferrals

D064 does **not** select:

- which mechanically observable but undocumented slots are published as API;
- stable/experimental/deprecated vocabulary;
- doctest/executable-example policy;
- website URL/layout/anchor presentation;
- runtime documentation/reflection API;
- package-registry publication requirements beyond compatibility with the model;
- supplemental article authoring/identity syntax;
- a rename/alias relationship vocabulary;
- an overload/type system; or
- a universal code-intelligence graph.

Any of these that becomes a substantive prerequisite must cross the ordinary Dxxx approval gate.

## Downstream consequences

After D064 publication:

1. a reusable Protos-owned documentation extractor/model proof may target this identity/schema
   architecture;
2. WEB001-J7B/J7C may consume the neutral model without becoming API authority;
3. CLI/IDE/LSP/search tooling may share semantic identities without using website URLs;
4. future package documentation may preserve symbol lineage across releases while exact occurrence
   scope distinguishes coexisting versions; and
5. implementation work must not add hidden UUID/content/source-coordinate identities as shortcuts.

D064 authorizes architecture/schema implementation consistent with this decision. It does not by
itself authorize any separately deferred coverage, stability, doctest, article-identity or
presentation policy.

## Change classification

`VALIDATION_CLASS=GOVERNANCE_DOCUMENTATION_ONLY`

This ratification changes only durable tooling/documentation governance records and navigation. It
changes no Protos specification, executable implementation/runtime, Maven implementation version,
public Standard Library semantics, package format, website implementation or deployment state.
