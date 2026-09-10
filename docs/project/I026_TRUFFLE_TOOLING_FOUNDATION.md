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
- Ratified `PLAT004` owns `SourceSection` placement: executable roots own exact Truffle `Source` identity, execution nodes retain only compact source ranges, and `SourceSection` is projected on demand after adoption rather than eagerly retained per node.
- Ratified `PLAT005` owns Truffle instrumentation coverage/tagging: one common replay-aware `ProtosExpressionNode` instrumentation mechanism, explicit canonical-role metadata, and a baseline limited to `StatementTag` + `CallTag`; root/expression/variable/custom tags, Truffle yield/resume mapping and debugger-value exposure remain deferred to their owning evidence gates.
- Ratified `PLAT008` owns replay-site identity under Truffle wrappers and future backend rewrites: replay compares a logical site, the current AST represents it with the recursively unwrapped guest `ProtosExpressionNode` delegate at zero permanent per-node cost, live replacements must preserve/remap it, and a future Bytecode DSL may use a backend-native stable location without changing replay semantics.
- Ratified `PLAT013` owns Truffle debugger/interop value projection: real Protos runtime values provide only faithful side-effect-free read-only baseline facets; ordinary object members project local slots only; synthetic scopes/contextual views remain separate adapters; delegated lookup, Closure execution, debugger mutation and initial Map/IdentityMap hash-entry interop are not implicitly enabled.
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
| I026-A4 | CLOSED | `0.2.301-SNAPSHOT` | I026-A2 + I026-A3 | Polyglot runtime-entry cutover complete: A4A public parse substrate plus A4B production hosting/driver migration are published, and direct compiler/call entry is no longer a parallel production Process architecture. |
| I026-A4A | CLOSED | `0.2.266-SNAPSHOT` | I026-A3 | Establish one host-owned thread-confined entered Polyglot `Context`, resolve the exact current `ProtosLanguageContext` through Truffle `ContextReference`, parse exact Truffle `Source` values through `Env.parsePublic(...)` / `ProtosLanguage.parse(...)`, and execute the resulting language-bound target through the existing activation-bearing `ProtosRootTaskExecution`. No CLI route is cut over by A4A. |
| I026-A4B | CLOSED | `0.2.301-SNAPSHOT` | I026-A4A | PLAT001 implementation complete through A4B1 multithread safety, A4B2 Process-scoped hosting/A+ projection, and A4B3 production-driver cutover/legacy-entry retirement. |
| I026-A4B1 | CLOSED | `0.2.267-SNAPSHOT` | I026-A4A + PLAT001 | Audited the language/context/compiler state, authorized Truffle multithread access, replaced permanent owner-thread entry with per-carrier enter/leave plus shared-read/exclusive-close lifecycle coordination, and proved overlapping same-Context execution without a global GIL or semantic ThreadLocal. Cross-Process bootstrap and Actor/P Process-context binding remain A4B2. |
| I026-A4B2 | CLOSED | `0.2.280-SNAPSHOT` | I026-A4B1 | Complete Process-scoped PLAT001 hosting: Engine/Context lifecycle, Actor/P placement, frozen concurrent Core publication and A+ Context-local execution plans/CallTargets are published; A4B3 is released. |
| I026-A4B2A | CLOSED | `0.2.269-SNAPSHOT` | I026-A4B1 | Establish one explicit shareable Engine owner, distinct Process-scoped Contexts, one fixed implementation-only Process-host binding, semantic-termination-triggered Context cleanup, and lifecycle isolation across independent Processes. No Actor/P carrier routing or Core-bootstrap concurrency claim yet. |
| I026-A4B2B | CLOSED | `0.2.280-SNAPSHOT` | I026-A4B2A | Actor routing, P routing and B2B3 frozen Core/A+ executable-layer closure are all published with no global guest lock or Context-per-Actor/P mapping. |
| I026-A4B2B1 | CLOSED | `0.2.271-SNAPSHOT` | I026-A4B2A | Route every scheduled Actor control/Task/mailbox segment through its owning Process execution host; prove two distinct Actor carriers enter one exact Process `ProtosLanguageContext` with no Context-per-Actor mapping or carrier affinity. |
| I026-A4B2B2 | CLOSED | `0.2.273-SNAPSHOT` | I026-A4B2B1 | Propagate Process host placement through isolated P domains, enter the owning Process Context around guest execution, preserve sibling physical parallelism, and prove nested P inherits the same Context without exposing Process authority. |
| I026-A4B2B3 | CLOSED | `0.2.280-SNAPSHOT` | I026-A4B2B2 + D049/B010 | A frozen publication and B A+ Context-local executable projection are both published; B010 is CLOSED and distinct Process Contexts never reuse one another's projected root CallTargets. |
| I026-A4B2B3A | CLOSED | `0.2.277-SNAPSHOT` | I026-A4B2B2 + D049 | Publish one atomic frozen standard-root cutover plus frozen shared standard graph/captures; concurrent bootstraps reuse and validate the completed required root surface without a guest execution lock. |
| I026-A4B2B3B | CLOSED | `0.2.280-SNAPSHOT` | I026-A4B2B3A + PLAT001 A+ | Globally shared source-backed root Closure plans project through a bounded cache owned by the entered `ProtosLanguageContext`; two Process Contexts on one Engine overlap while using distinct plans and parameter/body CallTargets; EXCLUSIVE retained. |
| I026-A4B3 | CLOSED | `0.2.301-SNAPSHOT` | I026-A4B2 | CLI/REPL, bundled tools, exact/fresh/captured/workspace, ordinary modules, RootActor initial modules and the remaining production Process creator all use Process-scoped public-parse hosting; the final architecture guard prevents direct compiler entry from returning in Process creators. EXCLUSIVE retained; REUSE/SHARED deferred. |
| I026-B | CLOSED | `0.2.303-SNAPSHOT` | I026-A4 + PLAT004 | Map the existing exact `SourceSpan` ranges to valid Truffle `SourceSection` values on roots/execution nodes under ratified PLAT004 root-owned Source / node-local range / on-demand projection, with focused Java-side integration evidence. |
| I026-C | CLOSED | `0.2.311-SNAPSHOT` | I026-B + PLAT005 + PLAT008 | Implement common `InstrumentableNode` wrappers, exact `StatementTag`/`CallTag` canonical-role tagging and PLAT008 logical replay-site normalization; prove wrapper-transparent replay identity and compact no-source/no-token node metadata without changing Protos semantics. |
| I026-D | IN_PROGRESS | `0.2.317-SNAPSHOT` | I026-A1 + PLAT013 | D1 publishes ordinary Object local-member interop and D2 publishes exact String/Boolean/null scalar facets; numeric, Array and remaining safe runtime-family facets stay in D. |
| I026-D1 | CLOSED | `0.2.316-SNAPSHOT` | I026-D + PLAT013 | Direct InteropLibrary receiver support on real ProtosObjectValue instances plus the implementation-only ProtosRepresentedValue marker as opaque TruffleObject; exact-class ordinary Objects enumerate/read only local slots through an immutable member-name array adapter, reject writes/delegated lookup, preserve cycles as the same guest value and keep inherited runtime families out of this tranche. |
| I026-D2 | CLOSED | `0.2.317-SNAPSHOT` | I026-D1 + PLAT013 | Expose direct exact read-only Truffle scalar facets on real ProtosStringValue, ProtosBooleanValue and ProtosNullValue through isString/asString, isBoolean/asBoolean and isNull, with no wrapper/conversion, no numeric/array/member/execution facet and focused D1 member-read composition evidence. |
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
scheduler segment through the owning Process execution host and is CLOSED. A4B2B2 is also CLOSED:
isolated P domains now retain implementation-only Process host placement, sibling P carriers enter the
same Process Context concurrently, and nested P inherits that placement without gaining Process
authority. D049 resolves B010's independent standard-root structural-state/isolation question. A4B2B3 is now CLOSED: A publishes atomic frozen Core construction and complete shared-standard graph sealing, while B publishes the already-approved A+ `ProtosLanguageContext`-local executable projection with concurrent multi-Process no-cross-CallTarget evidence. B010, A4B2B and A4B2 are CLOSED. A4B3 is IN_PROGRESS after publishing the ordinary CLI/REPL Process-context cutover in `0.2.287-SNAPSHOT`; bundled-tool + exact/fresh/captured/workspace hosting is published in `0.2.291-SNAPSHOT`; module/initial-module public-parse routing is published in `0.2.297-SNAPSHOT`; final legacy-entry retirement remains the last mechanical phase under the same I026-A4B3 work item. I026-D remains independently READY.

## I026-D value interop progress

`I026-D1` closes in `0.2.316-SNAPSHOT` as the first executable consumer of ratified PLAT013.
Real `ProtosObjectValue` instances are InteropLibrary receivers directly; the
implementation-only `ProtosRepresentedValue` bridge also becomes a bare
`TruffleObject` marker so specialized semantic values can cross member reads
without yet selecting primitive/string/boolean/null facets. No universal
debugger-value wrapper or global cache is introduced. Exact ordinary
Objects expose only their current local-slot surface. Member-name enumeration is
an immutable tooling adapter snapshot, reads use local reflection only, reject accidental host-only non-interop slot
values, preserve specialized represented values and cycles as the original guest
value, and keep write/remove/insert messages unsupported. Inherited runtime families are deliberately not given object-member
projection by this tranche, preventing Array/Map/Closure/resource behavior from
being selected accidentally through Java inheritance. Display is a bounded
host-opaque `Object` label.

Delegated lookup, Closure extraction/execution, debugger mutation,
Map/IdentityMap hash entries, primitive facets, Array indexed facets and I026-E
scope topology remain outside D1. No Protos specification or observable language
semantics change.

### I026-D2 simple scalar facets

`I026-D2` closes in `0.2.317-SNAPSHOT`. `ProtosStringValue`, `ProtosBooleanValue` and
`ProtosNullValue` remain the exact guest values and now export only their direct
side-effect-free Truffle scalar facets: String `isString/asString`, Boolean
`isBoolean/asBoolean`, and null `isNull`. No host conversion or universal
debugger wrapper is introduced, and D1 local-member reads preserve the same
guest object before the consumer observes the scalar facet.

D2 deliberately adds no numeric, member, array, hash, executable, mutation or
scope facet. Integer/fixed-integer/Float projection, Array indexed projection,
Map/IdentityMap hash interop, Closure execution and I026-E scope topology remain
outside this slice. No Protos specification or observable language semantics
change.

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

## I026-A4 production-entry closure

I026-A4 closes in `0.2.301-SNAPSHOT`. A4B3's final guard establishes the production boundary structurally: every
source/main owner that creates a standalone semantic Process must also bind a Process-scoped Polyglot
Context before guest source entry, and no such Process creator may directly compile guest source with
`new ProtosSourceCompiler().compile(...)`. Direct compiler use remains valid behind
`ProtosLanguage.parse`, during pre-Process Core/bootstrap preparation, and in deliberately unhosted
Java semantic harnesses; those uses are not production Process entry points.

A4B3 and A4B therefore close with A4. `ContextPolicy.EXCLUSIVE` remains active and
`REUSE`/`SHARED` remain deferred. I026-B is released to READY; I026-D remains independently READY.

PLAT004 is RATIFIED and is the durable platform owner for I026-B source-section placement. I026-B remains READY after publication of that decision; I026-C continues to own instrumentability and tag selection.

## I026-B SourceSection mapping closure

I026-B closes in `0.2.303-SNAPSHOT` as the first executable consumer of ratified PLAT004. `ProtosRootNode`
remains the sole structural owner of the exact Truffle `Source` for each executable tree and
reports its own section from the retained body span. Adopted `ProtosExpressionNode` instances
retain only their existing half-open `SourceSpan` and derive `SourceSection` values on demand
through the owning root. Unadopted nodes and source-less roots return no fabricated location;
a retained span that exceeds the owning Source fails explicitly rather than being clipped or
remapped. Focused evidence also guards the expression base against per-node `Source` or
`SourceSection` authority. No instrumentation tags, debugger scopes, Protos semantics or
ContextPolicy changes are introduced. I026-C is released to READY.

## PLAT005 instrumentation architecture release

PLAT005 is RATIFIED and is the durable platform owner for I026-C instrumentability/tag coverage. The selected baseline uses one common `ProtosExpressionNode` instrumentation/wrapper mechanism below evaluator replay, with immutable canonical-role tag metadata. `StatementTag` maps only direct executable children of `CanonicalSequence`; `CallTag` maps Call/Send/SuperSend. Physical helper roots are not promoted to semantic roots, and Root/RootBody/Expression/variable/custom tags, Truffle yield/resume mapping and debugger-visible value conversion remain deliberately deferred. Publication of PLAT005 releases I026-C from its platform-decision blocker to READY without changing executable implementation or Protos semantics.

## PLAT008 replay-site identity architecture release

PLAT008 is RATIFIED and is the durable platform owner for replay-site identity under I026-C instrumentation. Replay compares a logical execution site rather than a physical Truffle wrapper/probe. The current AST backend represents that identity at zero permanent per-node allocation with the recursively unwrapped original guest `ProtosExpressionNode`; physical wrapper execution remains intact for real probe events, while completed replay returns retained results before wrapper/delegate re-execution so tooling events are not duplicated. SourceSpan/SourceSection are not replay identities, no global/per-node replay registry is introduced, and any future replacement that can cross a live suspension must preserve or remap logical identity. A future Bytecode DSL backend may use BytecodeLocation or another backend-native stable location under the same contract. Publication releases I026-C from its PLAT008 blocker to READY without changing executable implementation or Protos semantics.


## I026-C instrumentation closure

I026-C closes in `0.2.311-SNAPSHOT` as the executable consumer of ratified PLAT005 and PLAT008. `ProtosExpressionNode` is the common Truffle instrumentable base and generated-wrapper owner; only direct executable children of canonical sequences carry `StatementTag`, while Call/Send/SuperSend carry `CallTag`. Instrument-visible outgoing values remain deliberately suppressed until I026-D owns a faithful interop view, and instrument-introduced values fail closed rather than entering guest execution.

Replay remains semantically unchanged: the physical wrapper path executes so probes can observe real execution, but the continuation tape records only the recursively unwrapped logical replay-site identity. A completed replay event returns its retained result before wrapper/delegate execution and therefore cannot emit a duplicate execution event. The current AST representation adds only one compact tag byte per execution node and no per-node `Source`, `SourceSection`, replay token, registry, lock or source-derived identity. PLAT004 SourceSection ownership and PLAT001 `ContextPolicy.EXCLUSIVE` remain unchanged; `REUSE`/`SHARED`, Root/RootBody/Expression/variable/custom tags, yield/resume mapping and debugger value exposure remain deferred.

## PLAT013 debugger/interop architecture release

PLAT013 is RATIFIED after explicit project-owner approval and exhaustive cross-language/runtime review. I026-D is READY under the selected C′ architecture: real Protos values remain the authoritative interop receivers for a read-only semantic-minimum surface, ordinary object members are local-slot reflection only, and synthetic debugger scopes/contextual views remain separate adapters. The ratification introduces no I026-D implementation, no Protos semantic change and no new DAP/LSP compatibility claim. I026-E remains dependent on I026-D.
