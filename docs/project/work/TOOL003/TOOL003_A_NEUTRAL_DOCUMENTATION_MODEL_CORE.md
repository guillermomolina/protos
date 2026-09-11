# TOOL003-A — Neutral documentation model core

Status: **IN_PROGRESS — A implementation foundation**

Parent work item: `TOOL003` / GitHub #322

Authority:
- `docs/project/decisions/tooling/D061_STANDARD_LIBRARY_API_REFERENCE_AUTHORITY.md`
- `docs/project/decisions/tooling/D062_CANONICAL_API_DOCUMENTATION_AUTHORING_CONVENTION.md`
- `docs/project/decisions/tooling/D064_NEUTRAL_DOCUMENTATION_MODEL_SCHEMA_AND_STABLE_SYMBOL_IDENTITY.md`

## Purpose

TOOL003-A materializes the implementation-neutral data-model boundary selected by
D064 before source extraction is connected in TOOL003-B.

This slice deliberately does **not** parse or execute Protos source. It establishes
only:

- structural module lineage identities;
- structural symbol identities;
- exact artifact scopes and symbol-occurrence keys;
- source provenance;
- optional mechanically observable callable shape;
- optional canonical Markdown payload;
- deterministic JSON v1 serialization; and
- invariant validation required before a source extractor may emit records.

The website, CLI, IDE/LSP and package-documentation consumers remain downstream.
No renderer-specific route, page, anchor, protocol or object is represented here.

## Concrete JSON v1 baseline

The initial canonical interchange format is named:

```text
protos-documentation
```

with:

```text
major = 1
minor = 0
```

The top-level canonical field order is:

```text
format
generator
provenance
modules
symbols
articles
relationships
```

`articles` and `relationships` are emitted as empty arrays in TOOL003-A because
D064 deliberately deferred supplemental-article identity/authoring and
relationship vocabulary. Later separately-ratified additive support may evolve
the minor format without redefining current identities.

## Identity mapping

Standard Library module lineage is represented structurally as:

```json
{"kind":"std","name":"std:collections/Set"}
```

Package-backed module lineage is represented structurally as:

```json
{"kind":"package","packageId":"<opaque-package-id>","module":"collections/Set"}
```

A slot symbol embeds the structural module lineage plus the top-level slot name:

```json
{
  "module":{"kind":"std","name":"std:collections/Set"},
  "slot":"contains"
}
```

No source path, line/column, callable signature, release, revision, content
identity, UUID or digest is part of `SymbolIdentity`.

The exact symbol occurrence remains conceptually:

```text
(ExactArtifactScope, SymbolIdentity)
```

and is represented by the artifact's top-level `provenance` scope plus the
structural symbol identity. The serializer therefore does not duplicate exact
scope into every symbol record.

## Exact artifact scopes

TOOL003-A supports the exact scope distinctions already permitted by D064:

```text
repositoryRevision
packageRelease
packageRevision
```

Repository scope stores a canonical `owner/name` repository coordinate and an
exact revision. Package release/revision scopes keep `PackageId`,
`ReleaseVersion` or revision, and `ContentIdentity` distinct as applicable.

These are occurrence/provenance facts. They never modify module/symbol lineage.

## Source provenance

Canonical source paths are repository/package-relative, use `/`, and reject:

- absolute POSIX paths;
- Windows drive paths;
- backslash paths;
- empty, `.` or `..` path segments.

TOOL003-A uses one normalized source-range convention:

- lines are one-based;
- columns are one-based;
- columns count Unicode scalar values, not UTF-8 bytes or JVM UTF-16 code units;
- the start is inclusive;
- the end is exclusive.

The range is provenance only and is never used for identity or collision repair.

TOOL003-B must compute this normalized range from canonical source rather than
exposing parser/JVM-native offsets directly.

## Callable shape

A slot may have one optional `callable` object:

```json
{
  "parameters":["first","second"],
  "restParameter":"additional"
}
```

Parameter order/name and optional rest parameter are mechanical facts. They do
not participate in `SymbolIdentity`, matching D064.

TOOL003-A rejects duplicate parameter names inside one callable model record.
That is data-model validation only; accepted Protos syntax remains owned by the
normative grammar/parser.

## Documentation payload

Module/symbol documentation is either JSON `null` or canonical Markdown text.

CRLF and bare CR in an already-associated documentation payload normalize to LF
for canonical interchange. The model does not infer documentation from ordinary
comments and does not decide whether an undocumented mechanically observable
slot is published as API.

That coverage/publication policy remains deliberately deferred.

## Determinism

For identical effective model input, the serializer:

- emits UTF-8;
- emits LF only;
- emits a fixed object-field order;
- sorts modules and symbols by structural semantic identity using Unicode scalar
  ordering;
- emits no timestamp;
- emits no absolute host path;
- emits empty `articles` / `relationships` in the initial baseline; and
- emits exactly one final LF.

Generator name/version are serialized inputs. Changing them therefore changes
canonical bytes, as required by D064.

## Fail-fast validation

Construction fails before serialization when:

- a Standard Library module key is not canonical `std:` identity;
- a physical Core identity is presented as `std:core` or `std:core/...`;
- a source path is absolute or non-normalized;
- duplicate module lineage records exist in one artifact;
- duplicate `SymbolIdentity` records exist in one exact artifact;
- a symbol references a module absent from that artifact; or
- required identity/provenance text is empty, contains control characters, or
  contains malformed Unicode surrogate data.

The implementation does not synthesize `#2`, source-coordinate, signature,
hash or UUID suffixes to repair identity collisions.

## Files owned by A

Production:

- `src/main/java/com/guillermomolina/protos/documentation/ProtosDocumentationModel.java`
- `src/main/java/com/guillermomolina/protos/documentation/ProtosDocumentationJson.java`

Focused tests:

- `src/test/java/com/guillermomolina/protos/documentation/ProtosDocumentationModelTest.java`
- `src/test/java/com/guillermomolina/protos/documentation/ProtosDocumentationJsonTest.java`

## Deferred to later TOOL003 slices

TOOL003-A intentionally does not implement:

- lexer/parser comment association;
- top-level source binding extraction;
- callable extraction from real Closure syntax;
- Standard Library tree traversal;
- exact Git revision artifact generation;
- supplemental articles;
- relationships;
- API coverage/publication policy;
- stability/deprecation;
- doctests;
- website routes or rendering.

TOOL003-B may now connect the real Protos source frontend to these model
mechanisms without re-deciding D061/D062/D064. If B exposes a substantive
question from the deferred list, the affected slice must stop at the normal
Dxxx approval gate.
