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

The production hosting topology exposed during A4B is owned by ratified `PLAT001`;
see `docs/project/PLAT001_TRUFFLE_RUNTIME_HOSTING.md`.

| Slice | Status | Version | Dependencies | Scope / exit condition |
|---|---|---|---|---|
| I026-A1 | CLOSED | `0.2.260-SNAPSHOT` | — | Register `ProtosLanguage` and one per-Polyglot-context `ProtosLanguageContext`, enable the official Truffle registration annotation processor, and prove Polyglot discovery plus `Context.initialize("protos")`. Parsing/execution is intentionally not connected by this slice. |
| I026-A2 | CLOSED | `0.2.263-SNAPSHOT` | I026-A1 | `ParsingRequest.getSource()` is now the canonical Polyglot parse input. The exact Truffle `Source` is retained by the top-level and derived Closure/object roots together with the active `ProtosLanguage`; Closure-plan rematerialization preserves that same ownership. Parsing is connected, but CLI/runtime execution cutover remains A4. |
| I026-A3 | CLOSED | `0.2.265-SNAPSHOT` | I026-A2 | Resolver-loaded modules now cross the host boundary as one `ProtosModuleSource` carrying the exact canonical `ModuleKey` plus a character Truffle `Source`. Standard-library, bundled-tool and workspace-package file resolvers attach the exact file URI; delegated resolvers preserve the returned Source unchanged. Module runtime, RootActor initial-module execution and bundled-tool staging compile the `ProtosModuleSource` directly, preserve its Source on top-level/derived roots even before A4 supplies an active language instance, and fail closed on key/source mismatch. No identity-free String fallback remains in the resolver contract. |
| I026-A4 | IN_PROGRESS | — | I026-A2 + I026-A3 | Polyglot runtime-entry cutover, refined into A4A-A4B after the A3 publication exposed the activation-bearing root-task boundary. A4 closes only when A4B has migrated all primary runtime drivers and retired direct compiler/call entry as a parallel architecture. |
| I026-A4A | CLOSED | `0.2.266-SNAPSHOT` | I026-A3 | Establish one host-owned thread-confined entered Polyglot `Context`, resolve the exact current `ProtosLanguageContext` through Truffle `ContextReference`, parse exact Truffle `Source` values through `Env.parsePublic(...)` / `ProtosLanguage.parse(...)`, and execute the resulting language-bound target through the existing activation-bearing `ProtosRootTaskExecution`. No CLI route is cut over by A4A. |
| I026-A4B | IN_PROGRESS | — | I026-A4A | Implement ratified PLAT001 through ordered A4B1-A4B3; A4B closes only after multithread safety, Process-scoped hosting and driver cutover are all published. |
| I026-A4B1 | CLOSED | `0.2.267-SNAPSHOT` | I026-A4A + PLAT001 | Audited the language/context/compiler state, authorized Truffle multithread access, replaced permanent owner-thread entry with per-carrier enter/leave plus shared-read/exclusive-close lifecycle coordination, and proved overlapping same-Context execution without a global GIL or semantic ThreadLocal. Cross-Process bootstrap and Actor/P Process-context binding remain A4B2. |
| I026-A4B2 | IN_PROGRESS | — | I026-A4B1 | Implement Process-scoped PLAT001 hosting through A4B2A-A4B2B; B2 closes only after Engine/Context lifecycle plus Actor/P/bootstrap integration are both published. |
| I026-A4B2A | CLOSED | `0.2.269-SNAPSHOT` | I026-A4B1 | Establish one explicit shareable Engine owner, distinct Process-scoped Contexts, one fixed implementation-only Process-host binding, semantic-termination-triggered Context cleanup, and lifecycle isolation across independent Processes. No Actor/P carrier routing or Core-bootstrap concurrency claim yet. |
| I026-A4B2B | IN_PROGRESS | — | I026-A4B2A | Implement Actor/P/bootstrap integration through ordered A4B2B1-A4B2B3; B2B closes only after Actor routing, P routing and concurrent Core bootstrap are all published. |
| I026-A4B2B1 | CLOSED | `0.2.271-SNAPSHOT` | I026-A4B2A | Route every scheduled Actor control/Task/mailbox segment through its owning Process execution host; prove two distinct Actor carriers enter one exact Process `ProtosLanguageContext` with no Context-per-Actor mapping or carrier affinity. |
| I026-A4B2B2 | READY | — | I026-A4B2B1 | Route isolated P carriers, including nested P, through the originating Process host while preserving P transfer/isolation and physical parallelism. |
| I026-A4B2B3 | BLOCKED_BY_DEPENDENCIES | — | I026-A4B2B2 | Close concurrent Core-root bootstrap publication across hosted Processes, then close A4B2B/A4B2 and release A4B3. |
| I026-A4B3 | BLOCKED_BY_DEPENDENCIES | — | I026-A4B2 | Cut CLI, REPL, bundled-tool, workspace and remaining production drivers onto the PLAT001 substrate and retire direct compiler/call entry as a separate primary production architecture. |
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
module semantics. A4 is now IN_PROGRESS. A4A establishes the entered Polyglot execution substrate without
inventing a second representation of Protos values: a host-owned thread-confined
`Context` supplies the current `ProtosLanguageContext`, `Env.parsePublic(...)` feeds the
already-published `ProtosLanguage.parse(...)` boundary, and the resulting target still
runs through the existing activation-bearing RootActor task machinery. A4A remains valid historical staging evidence, but its owner-thread confinement is not the
selected production topology. PLAT001 is now RATIFIED and owns the durable Truffle hosting
architecture: a shareable Engine, one current multithread Context per hosted Protos Process,
no Context-per-Actor/carrier mapping, no global execution lock/GIL, and no Truffle identity
promoted into Protos semantics. A4B remains IN_PROGRESS. A4B1 is now CLOSED: Truffle permits concurrent external
carrier entry, the A4A owner-thread/permanent-entry staging rule is removed, and the
host bridge uses bounded per-execution enter/leave with only per-Context lifecycle
coordination. A4B2 is now IN_PROGRESS through A4B2A-A4B2B. A4B2A publishes the
explicit shareable Engine owner and one distinct Context per bound semantic Process, with Context
cleanup following already-complete Process termination and no host shutdown authority over live
Processes. A4B2B is now IN_PROGRESS through A4B2B1-A4B2B3. A4B2B1 routes every Actor
scheduler segment through the owning Process execution host and is CLOSED; A4B2B2 is READY for
isolated P carrier routing, while A4B2B3 remains dependency-blocked on P routing before it closes
concurrent Core-root bootstrap publication. Only A4B2B3 closure releases A4B3. I026-D remains
independently READY.

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
