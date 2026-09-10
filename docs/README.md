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

Start with the [project documentation index](project/README.md) for the
role-first durable project tree.

The ratified destination architecture is the
[`DOC002-B role-first path contract`](project/work/DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md).
The original 107-file inventory and alternatives remain in the historical
[`DOC002-A audit`](project/work/DOC002/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md).
DOC002-G9 completed the staged migration/final rescan; GitHub Issue #156
preserves the live coordination and closure history.

Durable project records are organized by role. GitHub Issues remain the live
work-coordination surface and the `Protos Development` Project remains the live
scheduling/status surface.

- `project/work/<formal-work-item>/` contains records primarily owned by one
  formal work item.
- `project/decisions/` contains non-normative durable decision records split by
  language, tooling, and platform role.
- `project/architecture/` contains cross-cutting implementation architecture.
- `project/governance/` contains maintained project/repository rationale and policy.
- `project/registries/IMPLEMENTATION_STATUS.md` is the durable implementation
  registry and closure-evidence ledger, not a live progress tracker.
- `project/registries/IMPLEMENTATION_BLOCKERS.md` is the durable blocker and
  unblock-condition ledger.
- `project/registries/PLATFORM_ARCHITECTURE_DECISIONS.md` is the durable
  platform-decision registry.
- `project/evidence/` contains immutable/snapshot evidence grouped by genuine
  formal owner where one exists.
- `project/history/OPEN_TASKS.md` is the retired historical backlog snapshot.
- `project/architecture/CORE_BOOTSTRAP_ARCHITECTURE.md` records the non-normative
  bootstrap/Core implementation boundary.

Work-item-specific design and lifecycle records belong below their owner
directory, for example
`project/work/LIB001/LIB001_COLLECTIONS_DESIGN.md`. They may capture alternatives,
adopted implementation contracts, dependencies, and rejected approaches without
becoming normative language semantics.

The repository intentionally has no parallel root `TODO.md`. Exploratory work
belongs in GitHub Discussions; bounded actionable work belongs in GitHub Issues
and the `Protos Development` Project.

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
