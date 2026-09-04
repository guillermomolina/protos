# Protos Documentation

`docs/` contains non-normative project documentation. Normative language and
standard-library semantics live under `spec/`.

The documentation tree is organized by purpose rather than by when a document
was created.

## `design/`

Non-normative language and architecture design material.

- `PROTOS_DESIGN_PHILOSOPHY.md` explains the principles used to evaluate Protos
  design choices.
- `CONCURRENCY_DESIGN.md` records unresolved and directional concurrency design
  work that has not been promoted into normative specification.
- `IDEAS.md` records exploratory possibilities that are not yet design
  commitments or concrete implementation work.

Material in `design/` never defines observable Protos semantics. A design
decision becomes authoritative only when it is incorporated into its normative
owner under `spec/`.

## `project/`

Operational project state.

- `OPEN_TASKS.md` is the canonical ledger of concrete non-normative work that
  can proceed without an unresolved semantic decision.
- `IMPLEMENTATION_BLOCKERS.md` records implementation work blocked on unresolved
  normative semantics.

The repository intentionally has no parallel root `TODO.md`; project work should
be classified in these ledgers instead of accumulating in an unstructured
catch-all list.

## Adding documentation

Before adding a new top-level category, prefer placing the document in an
existing category whose purpose matches it. If no category fits, define the
new category's responsibility here rather than creating an ad-hoc directory.
