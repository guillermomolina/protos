# TOOL003 scope reconciliation after D066

Status: **SCOPE_CORRECTED — re-derive the minimum WEB001-J7B extraction path**

Parent work item: `TOOL003` / GitHub #322

Authority:

- `D061 — Standard Library API reference authority`
- `D062 — Canonical API documentation authoring convention`
- `D064 — Neutral documentation model schema and stable symbol identity`
- `D066 — Documentation authority, project Wiki, and public website topology`

## Why this reconciliation exists

TOOL003 was opened after D061/D062/D064 with a cross-consumer purpose: implement a reusable
Protos-owned model/extractor for website, CLI, IDE/LSP, search/index and future package docs.
TOOL003-A then published a neutral model and deterministic JSON implementation.

While authoring TOOL003-B, the project-owner review identified that the implementation plan had
generalized beyond the current requirement. The immediate real consumer is WEB001-J7B. A neutral
contract that *can* be reused does not require Protos to build all hypothetical consumer machinery
now.

D066 ratifies that correction without reopening D061/D062/D064.

## Published state retained

TOOL003-A remains published. D066 does not silently remove, revert or invalidate its code.

At D066 ratification time TOOL003-A provides:

- the D064 neutral model implementation;
- stable lineage/occurrence separation;
- deterministic JSON v1;
- source-provenance validation; and
- focused tests for those invariants.

Whether all of that implementation remains necessary long-term may be reviewed later through a
bounded cleanup. No cleanup is selected here.

## Unpublished TOOL003-B abandoned

The TOOL003-B ZIP authored before the D066 review was **never published** and MUST NOT be applied.

Its technical direction was not rejected because static source extraction is wrong. It was
abandoned because the slice was designed as another step in an unnecessarily broad pre-declared
cross-consumer platform.

There is therefore no repository rollback for B: the candidate existed only as an unpublished
external patch artifact.

## Correct next question

Do not continue mechanically to the original TOOL003-B/C/D sequence.

Re-derive the next bounded implementation from:

> What is the smallest Protos-owned deterministic mechanism required for WEB001-J7B to obtain the
> Standard Library documentation/reference facts for one exact canonical Protos revision while
> preserving D061/D062/D064?

That mechanism may reuse TOOL003-A where useful. It must not add CLI, IDE/LSP, package-registry,
search-index or universal graph infrastructure merely because those consumers are plausible.

## Required invariants for the re-derived path

The next path still must:

- keep canonical authored documentation in `guillermomolina/protos`;
- never execute arbitrary Standard Library modules solely to discover documentation;
- respect D062 `//!` / `///` association rules;
- preserve D064 semantic identity versus source/revision provenance separation;
- produce deterministic exact-input output where an interchange artifact is used;
- keep absolute host/private paths out of output;
- avoid a website-owned second parser/semantic authority; and
- leave API coverage/publication policy deferred unless separately decided.

## What is no longer assumed

The implementation does **not** assume that WEB001-J7B requires:

- a long-lived general-purpose documentation daemon/service;
- a public Java API for every future consumer;
- package-release/revision support in the immediate extractor path;
- CLI/IDE integration;
- a search/code-intelligence graph;
- an artifact registry; or
- a four-slice A/B/C/D implementation sequence.

Generality is earned by actual consumers.

## Relationship to the Wiki

The Protos Wiki is unrelated to the mechanical Standard Library extraction path. It is a
non-authoritative contributor/project knowledge surface and must not become an input or output
authority for WEB001-J7B API/reference generation.

## Closure state

```text
TOOL003                  IN_PROGRESS — scope reconciliation required
TOOL003_A                PUBLISHED / retained
TOOL003_B_OLD_CANDIDATE  ABANDONED_UNPUBLISHED
TOOL003_C_OLD_PLAN       NOT_AUTHORIZED_AS_AUTOMATIC_NEXT_STEP
TOOL003_D_OLD_PLAN       NOT_AUTHORIZED_AS_AUTOMATIC_NEXT_STEP
WEB001_J7B               BLOCKED until minimum extraction path is re-derived
D061_D062_D064           RATIFIED / unchanged
D066                     RATIFIED
```



## D067 coverage/publication reconciliation

D067 ratifies Candidate D for the remaining API-coverage question exposed by
the post-D066 re-derivation.

The minimum extractor MUST include every mechanically observable importable
`std:` module and top-level slot in the neutral artifact. D062-authored
documentation remains optional and independent. An entry without `//!` / `///`
documentation is emitted with missing documentation and reported in deterministic
coverage; it is not hidden, filtered, marked private, or treated as a build
failure.

This closes the coverage-policy prerequisite that the earlier scope
reconciliation intentionally left deferred. The next TOOL003 implementation is
therefore bounded to the Standard-Library-only extractor needed by WEB001-J7B:

```text
protos/lib/**/*.protos
    excluding protos/lib/core/**
        |
        v
real parser + D062 comment association
        |
        v
canonical std:<logical-name> identity
        |
        v
TOOL003-A / D064 deterministic JSON
        +
deterministic missing-doc coverage
```

No generic package `SourceUnit` API, CLI/IDE/search integration, universal
documentation graph, hide marker, publication manifest or website renderer is
part of this implementation slice.

Updated state:

```text
TOOL003                  IN_PROGRESS — minimum extractor READY
TOOL003_A                PUBLISHED / retained
TOOL003_B_OLD_CANDIDATE  ABANDONED_UNPUBLISHED
D067                     RATIFIED
WEB001_J7B               BLOCKED until minimum extractor publication
```

<!-- TOOL003-MINIMUM-STDLIB-EXTRACTOR -->
## Minimum Standard Library extractor after D067

D067 closes the final coverage/publication prerequisite left open by the D066
scope reconciliation. Candidate D requires complete mechanical inventory plus
an explicit missing-documentation state.

The bounded implementation selected from that authority is recorded in
`TOOL003_MINIMUM_STDLIB_EXTRACTOR.md`.

It is intentionally Standard-Library-specific:

- canonical input is `protos/lib/**/*.protos`, excluding physical `core/**`;
- module identity is derived only from the existing `std:` distribution mapping;
- the real Protos parser owns source structure;
- the lexer exposes only opt-in ordinary line-comment observation for D062;
- TOOL003-A owns the D064 model/JSON;
- missing `//!` / `///` prose remains `null` and is reported as D067 coverage;
- the artifact is generated on demand from an exact clean checkout rather than
  committed; and
- no generic package, CLI, IDE/LSP, search or website-renderer framework is
  introduced.

After successful publication of this slice:

```text
TOOL003                  COMPLETE AT CURRENT EARNED SCOPE
TOOL003_A                PUBLISHED / retained
TOOL003_MIN_EXTRACTOR    PUBLISHED
TOOL003_B_OLD_CANDIDATE  ABANDONED_UNPUBLISHED
D067                     RATIFIED
WEB001_J7B               READY
```
