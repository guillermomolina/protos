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

## Durable project records

Durable, non-normative project records live in the separate
[`guillermomolina/protos-project-docs`](https://github.com/guillermomolina/protos-project-docs)
repository.

Start with its
[project documentation index](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/README.md)
for the role-first durable project tree.

The ratified destination architecture is the
[`DOC002-B role-first path contract`](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/work/DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md).
The original 107-file inventory and alternatives remain in the historical
[`DOC002-A audit`](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/work/DOC002/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md).
DOC002-G9 completed the staged migration/final rescan; GitHub Issue #156
preserves the live coordination and closure history.

The documentation repository is a durable project-record store, not the
operational project control plane. GitHub Issues in `guillermomolina/protos`
remain the canonical live work-state and coordination surface; the
`Protos Development` Project remains the derived scheduling/status dashboard.

The durable corpus preserves the `docs/project/**` role-first hierarchy,
including:

- `docs/project/work/<formal-work-item>/` for records primarily owned by one
  formal work item;
- `docs/project/decisions/` for non-normative durable decisions;
- `docs/project/architecture/` for cross-cutting implementation architecture;
- `docs/project/governance/` for maintained project/repository rationale and policy;
- `docs/project/registries/` for durable registries and closure evidence;
- `docs/project/evidence/` for immutable or snapshot-like evidence;
- `docs/project/history/` for retired or superseded historical snapshots.

The Protos repository intentionally has no parallel root `TODO.md`. Exploratory work
belongs in GitHub Discussions; bounded actionable work belongs in GitHub Issues
and the `Protos Development` Project.

## Adding documentation

Before adding a new top-level category, prefer placing the document in an
existing category whose purpose matches it. If no category fits, define the
new category's responsibility here rather than creating an ad-hoc directory.

Use `docs/design/` for cross-cutting or still-exploratory architecture that may
inform more than one tracked work item. Once a bounded `Ixxx`, `CLIxxx`,
`LIBxxx`, `LMxxx`, or other formally tracked item adopts concrete implementation
choices, keep that work-item-specific durable record under
`docs/project/**` in `guillermomolina/protos-project-docs` and link back to the
broader product-local design material when useful. Neither location can
override `spec/`.
