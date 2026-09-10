# DOC002-F3 — Platform decision migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `d50ab69f65fb24f87ac98e0697d7e4229111245e`.

DOC002-F3 migrates the exact residual legacy flat platform-decision batch:

- `docs/project/PLAT001_TRUFFLE_RUNTIME_HOSTING.md` → `docs/project/decisions/platform/PLAT001_TRUFFLE_RUNTIME_HOSTING.md`
- `docs/project/PLAT002_NETWORK_CAPABILITY_REPRESENTATION.md` → `docs/project/decisions/platform/PLAT002_NETWORK_CAPABILITY_REPRESENTATION.md`
- `docs/project/PLAT003_TCP_LIVE_RESOURCE_ARCHITECTURE.md` → `docs/project/decisions/platform/PLAT003_TCP_LIVE_RESOURCE_ARCHITECTURE.md`
- `docs/project/PLAT004_TRUFFLE_SOURCE_SECTION_OWNERSHIP.md` → `docs/project/decisions/platform/PLAT004_TRUFFLE_SOURCE_SECTION_OWNERSHIP.md`
- `docs/project/PLAT005_TRUFFLE_INSTRUMENTATION_ARCHITECTURE.md` → `docs/project/decisions/platform/PLAT005_TRUFFLE_INSTRUMENTATION_ARCHITECTURE.md`
- `docs/project/PLAT006_TCP_HOST_IO_OPERATION_ENGINE.md` → `docs/project/decisions/platform/PLAT006_TCP_HOST_IO_OPERATION_ENGINE.md`
- `docs/project/PLAT007_IPV6_ONLY_TCP_LISTENER_ENFORCEMENT.md` → `docs/project/decisions/platform/PLAT007_IPV6_ONLY_TCP_LISTENER_ENFORCEMENT.md`
- `docs/project/PLAT008_TRUFFLE_REPLAY_SITE_IDENTITY.md` → `docs/project/decisions/platform/PLAT008_TRUFFLE_REPLAY_SITE_IDENTITY.md`
- `docs/project/PLAT009_BYTEWRITABLE_FIRST_EFFECT_GATE.md` → `docs/project/decisions/platform/PLAT009_BYTEWRITABLE_FIRST_EFFECT_GATE.md`
- `docs/project/PLAT010_ACTOR_PLATFORM_CARRIERS.md` → `docs/project/decisions/platform/PLAT010_ACTOR_PLATFORM_CARRIERS.md`
- `docs/project/PLAT011_RUNTIMEHOST_CARRIER_SUBSTRATE.md` → `docs/project/decisions/platform/PLAT011_RUNTIMEHOST_CARRIER_SUBSTRATE.md`
- `docs/project/PLAT012_VERIFIED_EXTERNAL_PACKAGE_CUSTODY_SOURCE_RESOLUTION.md` → `docs/project/decisions/platform/PLAT012_VERIFIED_EXTERNAL_PACKAGE_CUSTODY_SOURCE_RESOLUTION.md`
- `docs/project/PLAT013_TRUFFLE_DEBUGGER_INTEROP_VALUE_PROJECTION.md` → `docs/project/decisions/platform/PLAT013_TRUFFLE_DEBUGGER_INTEROP_VALUE_PROJECTION.md`
- `docs/project/PLAT015_TRUFFLE_DEBUGGER_SCOPE_PROJECTION.md` → `docs/project/decisions/platform/PLAT015_TRUFFLE_DEBUGGER_SCOPE_PROJECTION.md`

`PLAT014_TRUFFLE_COOPERATIVE_CONTINUATIONS.md` already existed at
`docs/project/decisions/platform/` on the execution-time base because it was
created after the DOC002 role-first cutover. F3 preserves that file in place and
does not rewrite it.

## Authority audit

Every one of the 14 moved legacy records was already:

- `RATIFIED`; and
- explicitly described by its `Nature:` metadata as a durable non-normative
  platform/runtime/host/tooling architecture decision.

Therefore F3 performs **no authority-wording rewrite** and no decision-content
edit. Each moved destination must equal its execution-time source except for
deterministic relative-link/path rebasing caused by relocation.

The `decisions/platform/` role remains non-normative and must not be used as an
alternate source of observable Protos semantics.

## Reference reconciliation

Current active Markdown references to the moved concrete paths, discovered from
this invocation's `PUBLICATION_BASE`, were reconciled:

- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/PLATFORM_ARCHITECTURE_DECISIONS.md`
- `docs/project/work/I026/I026_TRUFFLE_TOOLING_FOUNDATION.md`

`PLATFORM_ARCHITECTURE_DECISIONS.md` remains a registry and is not moved by F3.
If it references a moved path, that reference is reconciled in place; its later
registry relocation remains a separate DOC002-F slice.

Historical chronology, the DOC002-A inventory snapshot, retired history,
specification changelog chronology and prior DOC002 migration records preserve
old path spellings when they describe earlier repository state.

Any exact old-path consumer under `src/`, `protos/`, `dist/`, `spec/`, `.github/`
or another non-Markdown surface makes F3 fail closed rather than expanding a
documentation-only migration into executable/specification state.

## Continuation

`DOC002-E` remains **CLOSED**. `DOC002-F0` remains **RATIFIED / CLOSED**.
`DOC002-F1` and `DOC002-F2` remain **CLOSED**. `DOC002-F3` is **CLOSED**.
`DOC002-F` remains **IN_PROGRESS** and `DOC002-F4` is **READY** for bounded
cross-cutting `CORE_*` architecture migration. Registry relocation remains
sequenced after the architecture slice.

No Protos specification, observable semantics, platform decision outcome,
runtime/tooling architecture, implementation, implementation version, decision
identifier, or license term changes.
