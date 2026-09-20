# Protos Standard Library work

These instructions apply to `LIBxxx` and to distributable Standard Library
API changes under `protos/lib/`.

`LIBxxx` owns distributable Standard Library API. Bundled tool implementation
modules are not Standard Library merely because they ship with the
toolchain.

Hand-written Protos source follows the source-style and capability-reuse
rules in `protos/AGENTS.md`.

Apply the shared implementation discipline in `AGENTS.work/IMPLEMENTATION.md`
for validation, versioning, changelog, and publication. New public Protos
APIs use the established Protos documentation mechanism.
