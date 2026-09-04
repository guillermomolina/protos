# Protos Core Library

This directory is reserved for distributable Core behavior written in Protos.

Its contents are non-normative: the specification under `spec/` defines the
required standard objects and observable semantics. Host bootstrap should keep
only irreducible machinery in Java and move ordinary Core construction and
behavior here as soon as the implementation can load it faithfully.

See `docs/project/CORE_BOOTSTRAP_ARCHITECTURE.md`.
