# Protos Core Library

This directory is reserved for distributable Core behavior written in Protos.

Its contents are non-normative: the specification under `spec/` defines the
required standard objects and observable semantics. Host bootstrap should keep
only irreducible machinery in Java and move ordinary Core construction and
behavior here as soon as the implementation can load it faithfully.

See `docs/project/CORE_BOOTSTRAP_ARCHITECTURE.md`.

## Source naming

When one distributable Core source has one canonical named standard object or prototype as its clear conceptual owner, its filename preserves that exact Protos name and case. This is a conceptual correspondence rule, not a blanket PascalCase rule. Responsibility or aggregation sources may retain descriptive names; the current reviewed exceptions are `actor.protos`, `error_taxonomy.protos`, and `prelude.protos`, while `import.protos` already preserves the exact lowercase name of the public `import` facility.
