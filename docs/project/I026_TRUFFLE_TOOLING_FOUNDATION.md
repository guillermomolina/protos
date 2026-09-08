# I026 — Truffle tooling foundation

Status: **IN_PROGRESS**

Nature: non-normative runtime/compiler tooling implementation

Origin: `AUD002` hybrid Truffle-first editor-tooling architecture, explicitly
approved by the project owner on 2026-09-08.

## Purpose

Make the existing Protos Truffle execution pipeline visible to standard Truffle
instrumentation without changing Protos language semantics. The resulting
foundation should let the project prove how much debugger and dynamic-language
service behavior can be reused from GraalVM before adding Protos-specific
protocol implementations.

This work owns the runtime/compiler bridge only. It does not by itself create a
VS Code extension, select a public CLI command or option, allocate `TOOL003`, or
commit the project to a standalone static language-server architecture.

## Architectural constraints

I026 implements the selected AUD002 direction under these constraints:

- Protos semantics remain defined by the normative specification, not by
  Truffle, GraalVM, DAP, LSP, VS Code, or Java implementation convenience.
- Preserve one source of runtime truth. Debugger/tooling views adapt the existing
  Protos execution model instead of creating a parallel variable, slot, lookup,
  identity, or callable model.
- Preserve source identity alongside the existing `SourceSpan` offsets so
  Truffle `SourceSection` values identify the real Protos source unit.
- Use standard Truffle instrumentation and interop mechanisms where they can
  faithfully expose existing semantics.
- Do not expose Java fields, implementation sentinels, host reflection, or other
  implementation-only state as if they were Protos-visible slots or values.
- Ordinary execution that is not using tooling should not acquire unnecessary
  observation, synchronization, or retained-state cost.
- GraalVM DAP/LSP compatibility is a claim to be demonstrated by smoke evidence,
  not inferred merely from API integration.

## Planned slices

Before executable I026 work began, the project owner explicitly rejected treating
the current direct `compile(String).call(...)` CLI/runtime path as an
architectural compatibility constraint. The original I026-A planning slice is
therefore refined before implementation into A1-A4. The migration may preserve
an old path temporarily between slices only as staging machinery; I026-A4 must
retire it as a separate primary runtime entry path rather than institutionalize a
permanent compatibility layer.

| Slice | Status | Version | Dependencies | Scope / exit condition |
|---|---|---|---|---|
| I026-A1 | CLOSED | `0.2.260-SNAPSHOT` | — | Register `ProtosLanguage` and one per-Polyglot-context `ProtosLanguageContext`, enable the official Truffle registration annotation processor, and prove Polyglot discovery plus `Context.initialize("protos")`. Parsing/execution is intentionally not connected by this slice. |
| I026-A2 | CLOSED | `0.2.263-SNAPSHOT` | I026-A1 | `ParsingRequest.getSource()` is now the canonical Polyglot parse input. The exact Truffle `Source` is retained by the top-level and derived Closure/object roots together with the active `ProtosLanguage`; Closure-plan rematerialization preserves that same ownership. Parsing is connected, but CLI/runtime execution cutover remains A4. |
| I026-A3 | CLOSED | `0.2.265-SNAPSHOT` | I026-A2 | Resolver-loaded modules now cross the host boundary as one `ProtosModuleSource` carrying the exact canonical `ModuleKey` plus a character Truffle `Source`. Standard-library, bundled-tool and workspace-package file resolvers attach the exact file URI; delegated resolvers preserve the returned Source unchanged. Module runtime, RootActor initial-module execution and bundled-tool staging compile the `ProtosModuleSource` directly, preserve its Source on top-level/derived roots even before A4 supplies an active language instance, and fail closed on key/source mismatch. No identity-free String fallback remains in the resolver contract. |
| I026-A4 | READY | — | I026-A2 + I026-A3 | Cut CLI, REPL and top-level execution drivers over to the real Polyglot/Truffle language entry boundary and retire the old direct top-level `compile(String).call(...)` route as a separate primary runtime architecture. Public CLI UX need not change merely because its implementation does. |
| I026-B | BLOCKED_BY_DEPENDENCIES | — | I026-A4 | Map the existing exact `SourceSpan` ranges to valid Truffle `SourceSection` values on roots/execution nodes, with focused Java-side integration evidence. |
| I026-C | BLOCKED_BY_DEPENDENCIES | — | I026-B | Make the relevant AST nodes instrumentable and expose the minimal faithful `StandardTags` needed for source execution/stepping; do not tag nodes merely to satisfy a debugger UI. |
| I026-D | READY | — | I026-A1 | Expose semantically faithful Truffle interop/debug views for Protos runtime values needed by tooling, without changing Protos identity or access semantics. This may proceed independently from A2-A4. |
| I026-E | BLOCKED_BY_DEPENDENCIES | — | I026-C + I026-D | Bridge top/local debugger scopes from the existing Protos activation/context model and prove visible names/values match Protos lookup boundaries. |
| I026-F | BLOCKED_BY_DEPENDENCIES | — | I026-C + I026-E | Run a real GraalVM DAP smoke gate over Protos source: source breakpoint, stepping, stack frames, scopes and representative values. Only successful evidence permits a Protos DAP-support claim. |
| I026-G | BLOCKED_BY_DEPENDENCIES | — | I026-C + I026-E | Run a GraalVM dynamic-LSP smoke gate and record exactly which useful runtime-derived capabilities work for Protos. Do not treat this as a replacement for static Protos language intelligence. |

After I026-A3, top-level Polyglot parsing and resolver-loaded modules both retain
real Truffle Source identity instead of collapsing source units to anonymous
Strings. `ModuleKey` remains the sole Core module-cache identity; the attached
Truffle Source is implementation/tooling metadata and does not change Actor-local
module semantics. A4 is now READY to move CLI, REPL and remaining top-level
execution drivers through the initialized Polyglot language/context boundary and
retire the staged direct-entry architecture. I026-D remains independently READY.

## Deferred ownership

The following work is deliberately outside I026 and receives no identifier from
this allocation:

- public CLI selection/launch UX for debugger or language-server modes;
- a VS Code extension, including file association, syntax highlighting, run/test
  commands and debug configuration;
- a Protos-specific static language service for diagnostics, symbols,
  navigation, rename, completion or semantic tokens;
- any official bundled tool that would justify a future `TOOLxxx` allocation;
- any third-party extension/plugin model.

After I026-F/G produce real compatibility evidence, those surfaces should be
classified under the already selected project-family boundaries rather than
being forced into I026 or pre-allocated as `TOOL003`.

## Specification and compatibility boundary

I026 is implementation/tooling work. It must not change Protos observable
semantics merely to fit Truffle instrumentation or editor expectations. If a
slice exposes a genuine missing semantic rule, that dependency must go through
the normal design/specification process before dependent implementation proceeds.

Implementation versioning and adaptive validation follow `AGENTS.md`: slices
that modify production implementation are executable-impact changes and require
the normal implementation-version bump plus focused/full validation. This
allocation/architecture publication is documentation/governance only and does
not itself change the implementation version.
