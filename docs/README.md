# Protos Documentation

`docs/` contains non-normative project documentation. Normative language and
standard-library semantics live under `spec/`.

The documentation tree is organized by purpose rather than by when a document
was created.

## `guide/`

Non-normative programming guidance for people learning and using Protos.

The guide explains mental models, relates familiar programming concepts to
Protos, and points to executable material under `protos/tutorials/` and
`protos/examples/`. It should explain *how to think and program in Protos*
without becoming a second language specification. When guide prose and `spec/`
disagree, the applicable normative specification wins.

## `design/`

Non-normative language and architecture design material.

- `PROTOS_DESIGN_PHILOSOPHY.md` explains the principles used to evaluate Protos
  design choices.
- `CONCURRENCY_DESIGN.md` records unresolved and directional concurrency design
  work that has not been promoted into normative specification.
- `STRUCTURED_DATA_AND_SERIALIZATION.md` records cross-format architectural
  investigation for structured data, document models, application-data mapping,
  and object persistence without making any one format a universal serialization
  model.
- `IDEAS.md` preserves the pre-GitHub historical idea archive. New exploratory
  possibilities belong in GitHub Discussions.

Material in `design/` never defines observable Protos semantics. A design
decision becomes authoritative only when it is incorporated into its normative
owner under `spec/`.

## `project/`

Start with the [project documentation index](project/README.md) for the ratified role-first navigation and legacy-path transition rules.

The current repository-documentation information-architecture audit is [`DOC002-A`](project/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md). It is a non-normative audit artifact; GitHub Issue #156 remains the live coordination surface for DOC002.

The selected destination architecture is the ratified [`DOC002-B role-first path contract`](project/work/DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md). New unambiguous durable project records use that role-first model; existing flat paths remain compatibility locations until bounded migration slices move them.

Durable project records, historical implementation evidence, blockers, and
non-normative design records tied to formally tracked work items. Live actionable
coordination is GitHub-native: Issues own the work item and the `Protos
Development` Project owns scheduling/status.

- `OPEN_TASKS.md` is a retired historical backlog snapshot. Do not add new work
  or mirror GitHub state into it.
- `IMPLEMENTATION_STATUS.md` is a durable implementation registry and
  closure-evidence ledger, not a live progress tracker.
- `IMPLEMENTATION_BLOCKERS.md` records durable normative implementation blockers
  and their unblock conditions.
- `CORE_BOOTSTRAP_ARCHITECTURE.md` defines the non-normative boundary between
  irreducible host bootstrap machinery and Core behavior implemented in Protos.
- Work-item-specific `*_DESIGN.md` records capture investigated alternatives,
  adopted implementation contracts, dependencies, and rejected approaches for a
  formally tracked item without becoming normative language semantics. Existing
  `LIB001_COLLECTIONS_DESIGN.md` is the precedent for Standard Library work.

The repository intentionally has no parallel root `TODO.md`. Exploratory work
belongs in GitHub Discussions; bounded actionable work belongs in GitHub Issues
and the `Protos Development` Project instead of an unstructured catch-all list or
a second repository backlog.

## Adding documentation

Before adding a new top-level category, prefer placing the document in an
existing category whose purpose matches it. If no category fits, define the
new category's responsibility here rather than creating an ad-hoc directory.

Use `docs/design/` for cross-cutting or still-exploratory architecture that may
inform more than one tracked work item. Once a bounded `Ixxx`, `CLIxxx`,
`LIBxxx`, `LMxxx`, or other formally tracked item adopts concrete implementation
choices, keep that work-item-specific design record under `docs/project/` and
link back to the broader design material when useful. Neither location can
override `spec/`.
