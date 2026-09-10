# DOC002-F4 — Cross-cutting Core architecture migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `d686f4f604cf49dbb9b7677c7bee19f870a02507`.

DOC002-F4 migrates the exact residual flat `CORE_*` cross-cutting architecture
set:

- `docs/project/CORE_BOOTSTRAP_ARCHITECTURE.md` → `docs/project/architecture/CORE_BOOTSTRAP_ARCHITECTURE.md`
- `docs/project/CORE_NATIVE_BOUNDARY.md` → `docs/project/architecture/CORE_NATIVE_BOUNDARY.md`

## Authority audit

`CORE_BOOTSTRAP_ARCHITECTURE.md` already declares itself
`non-normative implementation architecture`, explicitly states that it does not
define language semantics, and leaves observable behavior to normative `spec/`.

`CORE_NATIVE_BOUNDARY.md` is the evolving implementation-maintenance record
consumed by Core-native-boundary review. Its architectural owner explicitly
classifies that inventory as an implementation-maintenance constraint rather
than a source of language semantics.

Therefore F4 performs **no architecture/semantic wording rewrite** in either
moved record. Each destination must equal its execution-time source except for
deterministic relative-link/path rebasing caused by the relocation.

## Reference reconciliation

Current active Markdown references to the moved concrete paths, discovered from
this invocation's `PUBLICATION_BASE`, were reconciled:

- `AGENTS.md`
- `docs/guide/09-isolated-parallel-execution.md`
- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/work/LIB002/LIB002_TEXT_ENCODING_DESIGN.md`
- `docs/project/work/LIB004/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md`
- `protos/lib/core/README.md`

This intentionally includes Markdown outside `docs/` where needed, such as
`AGENTS.md` or `protos/lib/core/README.md`. Changing a documentation link there
does not alter executable Protos/Core behavior.

Historical chronology, the DOC002-A inventory snapshot, retired history,
specification changelog chronology and prior DOC002 migration records preserve
old path spellings when they describe earlier repository state.

There is no approved non-Markdown compatibility exception in F4. Discovery of
an executable/configuration/non-Markdown consumer of an old path makes
publication fail closed.

## Continuation

`DOC002-E` remains **CLOSED**. `DOC002-F0` remains **RATIFIED / CLOSED**.
`DOC002-F1`, `DOC002-F2`, and `DOC002-F3` remain **CLOSED**. `DOC002-F4` is
**CLOSED**. `DOC002-F` remains **IN_PROGRESS** and `DOC002-F5` is **READY** for
the bounded durable-registry migration.

No Protos specification, observable semantics, Core architecture meaning,
native-boundary meaning/count, implementation/runtime behavior, implementation
version, public API, or license term changes.
