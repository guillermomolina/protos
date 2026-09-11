# TOOL003 — Minimum Standard Library documentation extractor

Status: **IMPLEMENTATION SLICE — publish to release WEB001-J7B**

Parent work item: `TOOL003` / GitHub #322

Authority:

- D061 — Standard Library API reference authority and generation model;
- D062 — canonical `//!` / `///` documentation authoring convention;
- D064 — neutral documentation model and stable symbol identity;
- D066 — earned-generality documentation topology correction; and
- D067 — complete mechanical inventory with explicit documented/undocumented state.

Implementation version: `0.2.350-SNAPSHOT`

## Purpose

This slice implements only the real current consumer requirement that remained
after D066:

> Produce one deterministic D064 Standard Library documentation artifact from
> one exact canonical Protos checkout, without executing Standard Library
> modules and without making the website parse Protos source independently.

It is deliberately not the pre-D066 generic TOOL003-B design.

## Bounded data flow

```text
exact clean Protos checkout
        |
        | protos/lib/**/*.protos
        | excluding protos/lib/core/**
        v
real Protos lexer/parser
        |
        | D062 //! / /// association
        | top-level bare slot + callable facts
        v
TOOL003-A D064 Artifact
        |
        +--> deterministic JSON on stdout
        |
        +--> deterministic D067 coverage on stderr
```

No module is executed.

## Source discovery and identity

The extractor traverses the canonical `protos/lib` tree and includes only
`.protos` sources whose first logical segment is not `core`, case-insensitively.

For each source:

```text
protos/lib/<logical-name>.protos
    -> std:<logical-name>
```

The implementation enforces the existing portable Standard Library segment
rules and rejects ASCII-case-fold sibling ambiguity instead of inventing a
second resolver naming policy.

The physical repository-relative path remains provenance. It is not semantic
module identity.

## Structural extraction

`ProtosParser` remains the sole owner of Protos source structure.

The extractor records only mechanically knowable facts needed by D061/D064:

- canonical importable `std:` module identity;
- top-level bare slot identity;
- source provenance;
- direct/grouped Closure parameter names;
- rest-parameter name when present.

Member-slot creation, nested slots and implementation-body details do not become
module API symbols merely because they occur in the file.

## Documentation comment observation

Normal lexical/parser behavior remains unchanged. `ProtosLexer` gains one
opt-in line-comment observation path used only by documentation tooling.

Without an observer, ordinary tokenization allocates no comment records and
continues to skip comments exactly as before.

The extractor interprets the observed ordinary comments under D062:

- line-leading `//!` blocks are module documentation;
- line-leading `///` blocks document the immediately following documentable
  top-level slot;
- a contiguous block permits only one logical newline plus horizontal
  indentation between marker lines;
- one optional ASCII space after the marker is removed from each Markdown line;
- blank lines not expressed as an empty documentation-marker line break
  association;
- an ordinary comment or source construct between a `///` block and its slot
  breaks association;
- `///` in nested/unsupported placement is a validation error;
- `//!` after the first top-level construct is a validation error; and
- at most one module-documentation block is accepted.

The markers remain ordinary `//` comments to the language.

## D067 coverage

Every mechanically discovered importable module and top-level slot is emitted,
including entries without authored documentation.

Missing documentation remains `null` in the D064 artifact.

Coverage is rendered deterministically as counts plus sorted missing identities:

```text
modules.total=...
modules.documented=...
modules.undocumented=...
symbols.total=...
symbols.documented=...
symbols.undocumented=...
missing.module=std:...
missing.symbol=std:...::slot
```

Missing documentation is not a build failure.

## Exact revision and artifact lifetime

The executable entry point derives the exact revision from Git. It does not
accept a caller-supplied revision independently of the checkout.

Before emission it rejects:

- tracked repository modifications relative to `HEAD`; and
- untracked content under `protos/lib`.

This keeps the source and generator implementation tied to the revision carried
by `RepositoryRevisionScope`.

The generated JSON is intentionally **not committed** to `protos`. A committed
artifact whose provenance claimed the commit containing that same generated
artifact would create a self-reference problem. Instead the artifact is
generated on demand from the exact checkout that a consumer has selected.

The project source therefore owns the extractor and schema; downstream build
systems own ephemeral generated output.

## Invocation boundary

After compiling the exact checkout, a consumer can run:

```text
java -cp target/classes \
  com.guillermomolina.protos.documentation.ProtosStandardLibraryDocumentationExtractor \
  <repository-root>
```

The canonical D064 JSON is written to stdout. D067 coverage is written to
stderr.

This is an implementation/tool entry point, not a newly selected public Protos
CLI command. D062 deliberately left CLI command spelling outside its decision,
and this slice does not cross that gate.

## Explicit non-goals

This slice does not add:

- a generic `SourceUnit` extraction API;
- package-release/package-registry extraction;
- CLI/IDE/LSP/search consumers;
- a universal documentation graph beyond D064;
- website rendering;
- supplemental-article identity/association;
- visibility/export semantics;
- stability/deprecation vocabulary;
- a documentation hide marker;
- a publication manifest;
- doctests;
- runtime documentation state; or
- any Standard Library semantic change.

Generality remains deferred until a concrete second consumer earns it.

## Validation target

Focused tests cover:

- opt-in comment observation without token-stream changes;
- module/symbol documentation association;
- documented and undocumented D067 inventory;
- direct/grouped Closure callable facts;
- `core` exclusion;
- deterministic JSON;
- invalid D062 placement;
- case-fold naming ambiguity; and
- extraction of the current canonical Standard Library without invented
  `std:core` identities.

Publication validation remains authoritative for the complete candidate.

## Publication consequence

When this slice is published successfully:

```text
TOOL003_A                PUBLISHED / retained
TOOL003_MIN_EXTRACTOR    PUBLISHED
TOOL003_B_OLD_CANDIDATE  ABANDONED_UNPUBLISHED
D067                     RATIFIED
WEB001_J7B               READY
```

TOOL003 can then close at the current earned scope: the Protos-owned model plus
the artifact-producing Standard Library extractor required by the first real
consumer. Any later consumer may reuse these mechanisms but must not retroactively
justify speculative infrastructure.
