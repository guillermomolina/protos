# Protos Core Library

This directory is reserved for distributable Core behavior written in Protos.

Its contents are non-normative: the specification under `spec/` defines the
required standard objects and observable semantics. Host bootstrap should keep
only irreducible machinery in Java and move ordinary Core construction and
behavior here as soon as the implementation can load it faithfully.

See `docs/project/CORE_BOOTSTRAP_ARCHITECTURE.md`.

## Source naming

When one distributable Core source has one canonical named public concept as its clear dominant owner, its filename preserves that exact Protos name and case. Private bootstrap identities that are subordinate implementation pieces of that public concept do not defeat the correspondence; therefore `Actor.protos` owns `Actor` while also constructing the private `_coreActorRefPrototype`, `_coreGroupRefPrototype`, and `_coreSendOperationPrototype` helpers. This is a conceptual correspondence rule, not a blanket PascalCase rule. True responsibility or aggregation sources may retain descriptive names; the current reviewed exceptions are `error_taxonomy.protos` and `prelude.protos`, while `import.protos` already preserves the exact lowercase name of the public `import` facility.
