# Changelog

All notable changes to the Protos implementation project will be documented in this file.

For specification changes, see [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.2.35-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P53 message-send spread test so it exercises spread flattening
  with an already-materialized `ProtosArrayValue` instead of depending on the
  still-pending standard `Array.call` factory specialization.
- Project implementation version changed from `0.2.34-SNAPSHOT` to `0.2.35-SNAPSHOT`.

### Notes

- No message-send runtime semantics are changed.
- No standard Array factory behavior is introduced by this correction.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.34-SNAPSHOT] - 2026-09-04

### Added

- Added executable lowering for `CanonicalSend` through a dedicated
  `ProtosSendNode`.
- Added direct ordinary message invocation for arbitrary Protos receivers using
  the existing semantic value lookup bridge, preserving original receiver and
  physical `methodHome`.
- Added coverage for local and inherited method sends, argument/spread handling,
  and missing-message Core Error behavior.
- Project implementation version changed from `0.2.33-SNAPSHOT` to `0.2.34-SNAPSHOT`.

### Changed

- Callable lowering now recursively supports nested `CanonicalSend` expressions
  in closure bodies and defaults.

### Notes

- Message send remains ordinary slot lookup plus Closure activation; no parallel
  host dispatch mechanism is introduced.
- D027/B003 remains untouched: this change does not choose a parent for
  canonical `true` or `false` and does not introduce a `Boolean` prototype.
- No normative specification change is introduced.


## [0.2.33-SNAPSHOT] - 2026-09-04

### Fixed

- Allowed semantic member lookup to traverse ordinary-object delegation chains
  without requiring a Core prelude when no represented value boundary is
  crossed.
- Required the Core prelude lazily only when the semantic lookup walker actually
  reaches represented Integer or Float values.
- Restored extracted-Closure member-read execution in minimal activations while
  preserving represented numeric lookup through source-backed Core prototypes.
- Project implementation version changed from `0.2.32-SNAPSHOT` to `0.2.33-SNAPSHOT`.

### Notes

- D027/B003 remains untouched. No parent for canonical `true` or `false` is
  selected and no `Boolean` prototype is introduced.
- No normative specification change is introduced.


## [0.2.32-SNAPSHOT] - 2026-09-04

### Fixed

- Routed member lookup through the semantic value walker even when the initial
  receiver is an ordinary object, so an ordinary delegation chain can continue
  through a represented numeric value and then into its source-backed
  `Integer`/`Float`/`Number` prototype chain.
- Prevented the legacy `ProtosObjectValue.lookupSlot` host exception from
  escaping that mixed ordinary/represented delegation path.
- Preserved the existing language-level Core Error behavior for represented
  value families whose prototype bridge is not implemented in this slice.
- Project implementation version changed from `0.2.31-SNAPSHOT` to `0.2.32-SNAPSHOT`.

### Notes

- No delegation parent is selected for canonical `true` or `false`; D027/B003
  remains untouched.
- No `Boolean` prototype or Boolean fallback is introduced.
- No normative specification change is introduced.


## [0.2.31-SNAPSHOT] - 2026-09-04

### Fixed

- Restored minimal/internal `ProtosPrelude` construction without requiring
  source-backed `Number`, `Integer`, and `Float` bindings in every prelude.
  Full Core bootstrap remains responsible for loading and validating the numeric
  hierarchy.
- Ordinary `ProtosObjectValue` member reads no longer require an owning Core
  prelude. A prelude is required only when lookup crosses from a represented
  non-ordinary runtime value into its source-backed standard prototype chain.
- Corrected P52 numeric receiver-binding tests to use member extraction followed
  by the already-supported ordinary `CanonicalCall` path instead of unsupported
  `CanonicalSend` lowering.
- Project implementation version changed from `0.2.30-SNAPSHOT` to `0.2.31-SNAPSHOT`.

### Notes

- Numeric hierarchy semantics from P52 are unchanged.
- D027/B003 remains untouched: no parent is chosen for canonical `true` or
  `false`, and no `Boolean` prototype is introduced.
- No normative specification change is introduced.


## [0.2.30-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard `Number`, `Integer`, and `Float` prototype objects
  under `protos/lib/core/`.
- Added the runtime value-lookup bridge that maps semantic Integer and Float
  value representations into those source-backed ordinary prototype chains.
- Added coverage for numeric prototype hierarchy, inherited numeric lookup,
  exact receiver preservation, and ordinary objects delegating to numeric
  values.
- Added implementation blocker B003 so canonical Boolean parentage remains
  explicitly deferred to D027 rather than being guessed by the runtime.

### Changed

- Core bootstrap now loads and validates `Number -> Object`,
  `Integer -> Number`, and `Float -> Number`, then publishes those exact objects
  in the frozen prelude.
- Member lookup, invocation lookup, and activation receiver fallback now use the
  semantic value lookup bridge when a Core prelude is available.
- Project implementation version changed from `0.2.29-SNAPSHOT` to `0.2.30-SNAPSHOT`.

### Notes

- This change intentionally does not choose a parent for canonical `true` or
  `false`, does not create a `Boolean` prototype, and does not install Boolean
  lookup behavior. That work remains blocked on D027.
- String/null and the remaining standard value families are not assigned
  substitute parentage by this increment.
- No normative specification change is introduced.


## [0.2.29-SNAPSHOT] - 2026-09-04

### Added

- Added ordinary parenthesized invocation through the normative `call` slot protocol.
- Added `CanonicalCall` lowering, target-before-argument evaluation, spread flattening, ordinary `call` lookup, Closure validation, receiver/method-home binding, and terminal direct Closure activation.
- Installed standard `Object.call` and `Object.init` as ordinary Closure-valued slots on `Object`, with default construction and D022's `Object.init() -> this` result.

### Changed

- `ProtosClosureValue` now participates in the ordinary object/delegation model as a direct child of `Object`; standard Closure invocation therefore inherits `Object.call` rather than using a hidden callable flag.
- Extracted/bound Closure wrappers preserve ordinary local slots and structural state while replacing only receiver/method-home binding metadata.
- Project implementation version changed from `0.2.28-SNAPSHOT` to `0.2.29-SNAPSHOT`.

### Tests

- Added end-to-end coverage for plain Closure calls, local and inherited `call`, incompatible shadowing, default construction, overridden and standard `init`, spread, nested calls, and non-local return across ordinary calls.

### Notes

- Host-represented primitive value prototype bridging and standard Array/Map/numeric `call` specializations remain later implementation layers.
- No normative specification change is introduced.


## [0.2.28-SNAPSHOT] - 2026-09-04

### Changed

- Closed implementation blocker B002 for the runtime semantics of
  `Object.without(name)` and `Object.alias(sourceName, aliasName)` structural
  views.
- Structural-view results now always use the unique root `Object` as their
  immediate delegation parent rather than accepting an implementation-selected
  parent.
- The result remains a fresh open ordinary object regardless of the source
  object's parent or open/closed/frozen state.
- Project implementation version changed from `0.2.27-SNAPSHOT` to `0.2.28-SNAPSHOT`.

### Tests

- Extended object-runtime coverage for fresh identity, root-`Object` parent,
  open result state, frozen-source behavior, shallow exact-value copying,
  mutation independence, and delegated alias-name non-collision.

### Notes

- This block implements the now-closed runtime object semantics without exposing
  new Protos-visible messages yet; ordinary message dispatch remains pending the
  invocation-protocol work.
- `CanonicalCall` remains deliberately unopened while the current specification
  does not yet define the portable inheritance/replacement mechanism of the
  ordinary invocation protocol.
- No normative specification change is introduced.


## [0.2.27-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard `InvalidReturn` under `protos/lib/core/`, with
  direct delegation to the standard `Error` prototype.
- Added `ProtosReturnNode` and a dedicated internal non-local-return control
  transfer carrying the exact target home and result value.
- Callable-plan lowering now lowers canonical `^value` in Closure defaults and
  bodies.
- Added fresh `InvalidReturn` construction through the activation-owned Core
  prelude.

### Changed

- Selected Closure invocation now catches a non-local return only when the
  invocation owns the exact target home; nested invocations sharing a captured
  home rethrow the transfer unchanged.
- An owned home is still completed on every exit path, including a handled
  non-local return.
- Project implementation version changed from `0.2.26-SNAPSHOT` to `0.2.27-SNAPSHOT`.

### Tests

- Added integrated coverage for direct active `^`, `^` from a default
  expression, nested captured-home propagation, escaped-Closure
  `InvalidReturn`, fresh Error identity, and the exact source-backed
  `InvalidReturn -> Error` prototype relationship.
- Extended Core bootstrap coverage for the standard `InvalidReturn` prototype.

### Notes

- This block closes non-local return execution for already selected Closures.
- Ordinary `CanonicalCall`, message send, `super`, and polymorphic object
  invocation remain outside this block.
- No normative specification change is introduced.


## [0.2.26-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosClosureInvoker` for executing an already selected Closure through
  activation establishment, normative parameter binding, and body execution.
- Closure execution plans now own reusable Truffle call targets for binding and
  body execution.

### Changed

- An invocation-owned return home is completed when that invocation leaves its
  dynamic binding/body extent, including failure exits.
- Nested Closure invocations that reuse a captured return home never complete
  that home themselves.
- Project implementation version changed from `0.2.25-SNAPSHOT` to `0.2.26-SNAPSHOT`.

### Tests

- Added coverage for selected-Closure invocation, normal owned-home completion,
  binding failure propagation, and preservation of a captured nested return home.

### Notes

- This increment still does not lower or dispatch `CanonicalCall`/message sends.
- Non-local `^` transfer itself remains unopened; this slice establishes the
  lifecycle boundary it will target.
- No normative specification change is introduced.


## [0.2.25-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P47 callable-lowering test to import `ProtosParser` from its
  actual `com.guillermomolina.protos.parser` package.

### Changed

- Project implementation version changed from `0.2.24-SNAPSHOT` to `0.2.25-SNAPSHOT`.

### Notes

- No production implementation or Protos semantics changed in this corrective
  commit.



## [0.2.24-SNAPSHOT] - 2026-09-04

### Added

- Closure lowering now prepares an implementation-private execution plan
  containing the parameter-binding node and body node without invoking either.
- Callable-plan lowering recognizes `args` in Closure defaults and bodies and
  lowers it to `ProtosArgsNode`.
- Materialized Closure values retain their prepared execution plan, including
  across extracted-method binding.

### Changed

- General program/module lowering continues to reject `args`; only callable
  plans receive invocation-context `args` lowering.
- Project implementation version changed from `0.2.23-SNAPSHOT` to `0.2.24-SNAPSHOT`.

### Tests

- Added coverage proving both a default expression and a Closure body plan can
  observe the exact activation-owned `args` Array.

### Notes

- This increment does not execute calls, parameter binding automatically, or
  Closure bodies automatically; it only prepares the complete callable plan.
- Return-home completion and `^` execution remain unopened.
- No normative specification change is introduced.


## [0.2.23-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosArgsNode`, an invocation-only execution node that returns the
  exact standard frozen Array already established on the current Closure
  activation.

### Changed

- Project implementation version changed from `0.2.22-SNAPSHOT` to `0.2.23-SNAPSHOT`.

### Tests

- Added focused coverage proving `args` returns the exact activation-owned Array,
  retaining its source-backed standard `Array` parent and frozen state.

### Notes

- This increment intentionally does not enable `args` in the general
  program/module lowerer because Core defines it as an invocation-context
  binding and does not assign a substitute value outside invocation.
- Callable-body/default lowering will use this node when the Closure execution
  plan is connected.
- No normative specification change is introduced.


## [0.2.22-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosParameterBindingNode`, implementing the normative left-to-right
  Closure parameter-binding algorithm over an already established invocation
  activation.
- Binding supports supplied arguments, real-activation default evaluation,
  trailing rest capture as a distinct fresh frozen standard Array, and generic
  argument-count Error signaling.

### Changed

- Project implementation version changed from `0.2.21-SNAPSHOT` to `0.2.22-SNAPSHOT`.

### Tests

- Added coverage for earlier-parameter visibility from defaults, exact `args`
  preservation, distinct frozen rest Arrays, missing required parameters, and
  deferred excess-argument detection.

### Notes

- Parameter names are created only after their supplied/default value is
  obtained; there is no predeclaration or arity preflight.
- Slot-creation conflicts during parameter establishment use ordinary generic
  Error signaling.
- This increment does not yet execute `CanonicalCall`, lower the `args`
  intrinsic, execute Closure bodies, or complete return homes.
- No normative specification change is introduced.


## [0.2.21-SNAPSHOT] - 2026-09-04

### Added

- Closures now preserve their implementation-private owning `ProtosPrelude`
  together with lexical contexts and callable control metadata.
- Added `ProtosActivation.forClosureInvocation(...)`, which atomically
  establishes a fresh execution context, exact captured receiver and lexical
  contexts, source-backed frozen `args`, `methodHome`, and return-home state.
- Invocation activations record whether they own a newly established return home
  or reuse a captured lexical home.

### Changed

- Closure literal materialization now captures the exact owning prelude.
- Project implementation version changed from `0.2.20-SNAPSHOT` to `0.2.21-SNAPSHOT`.

### Tests

- Added coverage for complete top-level Closure activation establishment and for
  nested Closure reuse of the exact captured return home.

### Notes

- This increment still does not execute `CanonicalCall`, bind parameters or
  defaults/rest, execute Closure bodies, complete return homes, or execute `^`.
- No partial observable call path is introduced.
- No normative specification change is introduced.


## [0.2.20-SNAPSHOT] - 2026-09-04

### Added

- Added implementation-private activation `methodHome` state matching the
  normative `super` lookup model.
- Closure literals now capture the current activation's `methodHome` together
  with receiver and return-home metadata.

### Changed

- Object-construction execution preserves enclosing `methodHome` metadata while
  continuing not to become a lexical capture scope.
- Project implementation version changed from `0.2.19-SNAPSHOT` to `0.2.20-SNAPSHOT`.

### Tests

- Added focused coverage proving construction preserves the exact enclosing
  method home.

### Notes

- This increment does not execute `super`, perform callable dispatch, or create
  method activations yet.
- No normative specification change is introduced.


## [0.2.19-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosReturnHome`, an implementation-private identity object with
  explicit active/completed lifecycle state for callable non-local return
  ownership.
- `ProtosActivation` can now carry an optional lexical return home, and object
  construction preserves the enclosing home.
- `ProtosClosureValue` now captures and preserves the exact lexical return home,
  including across extracted-method binding.

### Changed

- Closure literal materialization now records the current activation's return
  home when one exists.
- Project implementation version changed from `0.2.18-SNAPSHOT` to
  `0.2.19-SNAPSHOT`.

### Tests

- Added focused return-home lifecycle and object-construction propagation
  coverage.

### Notes

- This increment does not yet execute `^`, create callable activations, or
  establish fresh invocation return homes.
- It does not add activation-level `methodHome`; `super` activation state remains
  a separate subsequent slice.
- No normative specification change is introduced.


## [0.2.18-SNAPSHOT] - 2026-09-04

### Added

- Added implementation-private invocation-argument state to `ProtosActivation`.
  Non-invocation activations expose no argument Array.
- Added `ProtosPrelude.newFrozenArray(...)`, producing a fresh frozen standard
  Array with the exact source-backed `Array` prototype.

### Changed

- Test preludes now include the mandatory standard `Array` binding introduced
  by the source-backed Core bootstrap.
- Project implementation version changed from `0.2.17-SNAPSHOT` to
  `0.2.18-SNAPSHOT`.

### Tests

- Extended Core bootstrap coverage for fresh frozen standard Array
  materialization.

### Notes

- This increment prepares the activation representation required by the
  normative `args` semantics but does not yet establish callable activations,
  bind parameters, or lower the `args` intrinsic.
- No observable invocation shortcut or partial call execution is introduced.
- No normative specification change is introduced.


## [0.2.17-SNAPSHOT] - 2026-09-04

### Added

- Added distributable `protos/lib/core/array.protos`, defining the standard
  `Array` prototype as an ordinary child of `Object`.
- Added `ProtosPrelude.arrayPrototype()` and `newArray(...)` so runtime
  machinery can materialize standard Arrays with the exact source-backed
  Array prototype as delegation parent.

### Changed

- Core bootstrap now loads and validates the `Array` binding before freezing
  the standard prelude and installs that exact source-created object alongside
  `Context` and `Error`.
- Project implementation version changed from `0.2.16-SNAPSHOT` to
  `0.2.17-SNAPSHOT`.

### Tests

- Extended Core bootstrap coverage to verify the exact source-backed Array
  binding, its delegation parent, and the parent of a materialized standard
  Array value.

### Notes

- This increment does not implement Array invocation, `at`, `atPut`, `size`,
  `each`, `args`, rest binding, or any other Array protocol.
- No hardcoded Java Array prototype singleton is introduced.
- No normative specification change is introduced.


## [0.2.16-SNAPSHOT] - 2026-09-04

### Added

- Added distributable `protos/lib/core/error.protos`, defining the mandatory
  standard `Error` prototype as an ordinary child of `Object`.

### Changed

- Core bootstrap now executes both `context.protos` and `error.protos`, validates
  their exact required parent relationships, installs both bindings into the
  frozen standard prelude, and exposes `Error` through that prelude.
- Runtime language failures now create fresh error objects whose parent is the
  exact source-backed `Error` prototype owned by the current activation's
  prelude.
- `ProtosCoreErrors` is now a stateless runtime factory; its process-global
  `ERROR_PROTOTYPE` singleton and `errorPrototype()` accessor were removed.
- Project implementation version changed from `0.2.15-SNAPSHOT` to
  `0.2.16-SNAPSHOT`.

### Tests

- Runtime error tests now use an explicit test prelude rather than the removed
  production Error singleton.
- Core bootstrap coverage verifies the source-backed Error binding and its
  direct delegation to Object.

### Notes

- A module-local binding named `Error` cannot redirect runtime-generated Core
  errors because runtime failure identity is obtained from the activation's
  owning prelude, not ordinary shadowable lexical lookup.
- No second Error identity or process-global standard Error object remains.
- No normative specification change is introduced.


## [0.2.15-SNAPSHOT] - 2026-09-04

### Changed

- Activations created by a `ProtosPrelude` now retain an implementation-private
  reference to that exact owning prelude.
- Object-construction activations preserve the owning prelude from their
  enclosing activation.
- Legacy direct activation construction remains available for bootstrap and
  focused runtime tests and carries no implicit process-global prelude.
- Project implementation version changed from `0.2.14-SNAPSHOT` to
  `0.2.15-SNAPSHOT`.

### Tests

- Added coverage that module activations retain their exact prelude and that
  object-construction activations propagate it unchanged.

### Notes

- This is runtime plumbing only and introduces no Protos-visible binding,
  lookup rule, identity, or behavior change.
- The explicit prelude reference is the foundation for resolving standard Core
  identities such as `Error` without using shadowable lexical lookup or
  process-global standard-object singletons.
- No normative specification change is introduced.


## [0.2.14-SNAPSHOT] - 2026-09-04

### Changed

- `ProtosPrelude` now owns an explicit frozen ordinary Protos bindings context
  rather than only retaining the `Context` prototype reference.
- `ProtosCoreBootstrap` now constructs that real prelude context after loading
  `Context`: it delegates to `Context`, contains the exact `Context` binding,
  and is frozen before becoming observable to later runtime stages.
- Added `ProtosPrelude.newModuleActivation()` so a fresh module context captures
  the frozen standard prelude through the ordinary lexical-context mechanism.
- Project implementation version changed from `0.2.13-SNAPSHOT` to
  `0.2.14-SNAPSHOT`.

### Tests

- Added coverage for frozen prelude structure and ordinary lexical lookup of the
  `Context` binding from a module activation.
- Extended Core bootstrap coverage to verify the source-backed prelude context.

### Notes

- This does not add any new standard objects or hardcoded Core behavior.
- The prelude remains explicit runtime state; no process-global mutable prelude
  is introduced.
- No normative specification change is introduced.


## [0.2.13-SNAPSHOT] - 2026-09-04

### Removed

- Removed the temporary static `ProtosCorePrelude` and its Java-constructed
  standard `Context` prototype.
- Removed the obsolete tests that treated that static scaffold as the Core
  prelude.

### Changed

- Migrated remaining test execution-context construction to explicit
  `ProtosPrelude` state or, for the Core bootstrap test itself, to the
  irreducible root-backed bootstrap context.
- Updated the Core bootstrap architecture note to record that the Java-side
  `Context` scaffold has been retired.
- Project implementation version changed from `0.2.12-SNAPSHOT` to
  `0.2.13-SNAPSHOT`.

### Notes

- The standard `Context` identity is now constructed by
  `protos/lib/core/context.protos` through `ProtosCoreBootstrap`.
- No replacement process-global standard prototype is introduced.
- No normative specification change is introduced.


## [0.2.12-SNAPSHOT] - 2026-09-04

### Added

- Added explicit `ProtosPrelude` runtime state whose `Context` prototype is
  supplied rather than hardcoded.
- Added `ProtosCoreBootstrap`, which executes distributable
  `protos/lib/core/context.protos` through the ordinary source pipeline and
  returns a prelude backed by the resulting ordinary `Context` object.
- Added focused coverage for explicit prelude context creation and source-backed
  Core bootstrap.

### Changed

- Project implementation version changed from `0.2.11-SNAPSHOT` to
  `0.2.12-SNAPSHOT`.

### Notes

- Core bootstrap uses a short-lived internal root-backed context only to create
  the first standard `Context`; that bootstrap context is not a Protos module
  instance or a new standard prototype.
- No process-global mutable prelude state is introduced. `ProtosPrelude` is an
  explicit object so later Actor ownership can remain local.
- The older static `ProtosCorePrelude` remains for one migration increment and
  is not extended.
- No normative specification change is introduced.


## [0.2.11-SNAPSHOT] - 2026-09-04

### Added

- Added the first executable distributable Core source,
  `protos/lib/core/context.protos`.
- The Core source constructs the standard `Context` prototype as an ordinary
  Protos object with `Object` as its delegation parent.
- Added coverage that loads and executes that Core source through the ordinary
  source-file/compiler pipeline and observes the resulting `Context` binding.

### Changed

- Project implementation version changed from `0.2.10-SNAPSHOT` to `0.2.11-SNAPSHOT`.

### Notes

- The existing Java-side `Context` in `ProtosCorePrelude` remains temporary
  bootstrap scaffolding for now. This increment establishes the source-side
  replacement before changing activation construction to consume it.
- No special Core parser, AST, object constructor, or Java-side standard
  prototype was added.
- No normative specification change is introduced.


## [0.2.10-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosSourceFileLoader` to read UTF-8 Protos source from a host `Path`
  and compile it through the existing ordinary source compiler pipeline.
- Added focused coverage for successful UTF-8 loading and host file-loading
  failure propagation.

### Changed

- Project implementation version changed from `0.2.9-SNAPSHOT` to
  `0.2.10-SNAPSHOT`.

### Notes

- This is host-side implementation plumbing, not the Protos language I/O model.
  It does not expose `Path`, Java NIO, or file-loading behavior to Protos code.
- The loader deliberately does not define module identity, import caching,
  bootstrap order, or Core object identities. Those higher-level semantics stay
  outside this increment.
- This entry point is intended for the upcoming `protos/lib/core/` bootstrap
  loader and ordinary module-loading machinery.
- No normative specification change is introduced.


## [0.2.9-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosSourceCompiler`, a single source-to-execution entry point that
  composes the existing parser, canonicalizer, Truffle lowerer, and call-target
  construction pipeline.
- Added focused coverage proving compiled source preserves ordinary sequence,
  literal, object-construction, and parser-failure behavior.

### Changed

- Project implementation version changed from `0.2.8-SNAPSHOT` to
  `0.2.9-SNAPSHOT`.

### Notes

- This is implementation plumbing only. It introduces no new syntax, lookup,
  invocation, object, or bootstrap semantics.
- The new entry point is intended to be reused by the upcoming
  `protos/lib/core/` loader so Core source and user source travel through the
  same ordinary compiler pipeline.
- No normative specification change is introduced.


## [0.2.8-SNAPSHOT] - 2026-09-04

### Fixed

- Fixed `ProtosArgumentVectorNodeTest` to use the actual `SourceSpan` constructor
  instead of a nonexistent `SourceSpan.unknown()` helper.
- Project implementation version changed from `0.2.7-SNAPSHOT` to
  `0.2.8-SNAPSHOT`.

### Notes

- This is a test-compilation correction only; caller argument-vector semantics
  and implementation behavior are unchanged.
- No normative specification change is introduced.


## [0.2.7-SNAPSHOT] - 2026-09-04

### Added

- Added the caller-supplied positional-vector evaluation stage for future
  polymorphic invocation.
- Ordinary argument items are evaluated exactly once from left to right.
- Spread items require standard Array indexed state and append a shallow
  ascending-index snapshot at their exact evaluation position.
- Invalid spread sources signal Core `Error` immediately and prevent later
  argument evaluation.

### Changed

- Project implementation version changed from `0.2.6-SNAPSHOT` to `0.2.7-SNAPSHOT`.

### Notes

- This increment deliberately stops before Closure activation and parameter
  binding. It therefore does not need to manufacture the standard frozen
  `args` Array before the Core `Array` prototype is available from
  `protos/lib/core/`.
- No standard Array prototype or protocol behavior is hardcoded in Java.
- No normative specification change is introduced.


## [0.2.6-SNAPSHOT] - 2026-09-04

### Changed

- Removed temporary Java-side `Number`, `Integer`, and `Float` prototype objects
  from `ProtosCorePrelude`.
- Removed the Java-side numeric-family prototype resolver and the tests that
  treated those temporary objects as the implementation's standard numeric
  prelude.
- Kept only the explicitly documented temporary `Context` bootstrap scaffold.
- Project implementation version changed from `0.2.5-SNAPSHOT` to
  `0.2.6-SNAPSHOT`.

### Notes

- Numeric value representations and already-implemented numeric literal/identity
  semantics are unchanged.
- This corrects an implementation-architecture regression: standard numeric
  prototype objects belong to the future `protos/lib/core/` bootstrap path
  rather than a growing hardcoded Java standard library.
- No normative specification change is introduced.


## [0.2.5-SNAPSHOT] - 2026-09-04

### Added

- Added an internal standard-Array object representation with receiver-owned
  dense indexed state distinct from ordinary object slots.
- Array indexed reads and updates use mathematical `BigInteger` indices,
  preserve exact element references, reject negative/out-of-range indices, and
  never grow or create holes.
- Closed Arrays may replace existing indexed elements while frozen Arrays reject
  replacement before index validation.
- Added detached shallow indexed snapshots for future call-spread and iteration
  semantics.

### Changed

- `ProtosObjectValue` is now extensible internally so specialized object
  representations can retain the ordinary object/delegation/slot model without
  adding parallel language object categories.
- Project implementation version changed from `0.2.4-SNAPSHOT` to
  `0.2.5-SNAPSHOT`.

### Notes

- This increment adds representation only. It does not hardcode or expose the
  standard `Array` prelude object or its protocol methods in Java.
- The explicit parent supplied to each Array instance preserves the normative
  Array-factory rule and will allow `protos/lib/core/` to own the standard
  prototype object when Core bootstrap is available.
- No normative specification change is introduced.


## [0.2.4-SNAPSHOT] - 2026-09-04

### Added

- Added execution of canonical composition items inside object bodies.
- Composition evaluates its source first, copies effective local bindings into
  the object under construction, and makes successful contributions immediately
  visible to later body items.
- Direct local declarations reserve their names structurally across the complete
  receiving object body, excluding those names from every composition item.
- Composition conflicts and invalid non-ordinary composition sources now signal
  Core `Error` objects instead of leaking host exceptions.

### Changed

- Canonical object-body lowering now supplies the object's structural reservation
  set to each composition item while preserving strict left-to-right body
  execution.
- Project implementation version changed from `0.2.3-SNAPSHOT` to
  `0.2.4-SNAPSHOT`.

### Notes

- Composition reuses the existing atomic runtime contribution helper, so a
  conflicting item installs none of its effective bindings.
- `without` and `alias` remain blocked by B002 and are not exposed by this
  increment.
- No normative specification change is introduced.


## [0.2.3-SNAPSHOT] - 2026-09-04

### Added

- Added Truffle execution for canonical object expressions without composition.
- Bare object expressions now create fresh open ordinary objects delegating to
  the unique `Object` root.
- Explicit parent expressions are evaluated before object-body execution and
  their exact result becomes the constructed object's immutable delegation
  parent.
- Object bodies execute through construction activations, so local slot creation
  targets the new object while Closures skip the construction object as a lexical
  capture scope.

### Changed

- Canonical-to-Truffle lowering now accepts `CanonicalObject` when all body
  expressions are otherwise supported by the current execution slice.
- Project implementation version changed from `0.2.2-SNAPSHOT` to
  `0.2.3-SNAPSHOT`.

### Notes

- Canonical composition execution remains a separate following increment.
- No normative specification change is introduced; this implements the current
  object-model and execution-context contracts.


## [0.2.2-SNAPSHOT] - 2026-09-04

### Added

- Added explicit object-construction activations whose current context and
  receiver are the object under construction while Closure capture skips that
  construction object.
- Added transitive construction-scope skipping so Closures created inside nested
  object bodies capture only genuine enclosing lexical contexts.

### Changed

- Closure materialization now obtains its lexical capture chain from activation
  semantics instead of unconditionally capturing the activation's current
  context.
- Project implementation version changed from `0.2.1-SNAPSHOT` to
  `0.2.2-SNAPSHOT`.

### Notes

- This is implementation architecture for already-specified object-construction
  and lexical-capture semantics; no normative specification change is introduced.
- The new construction-activation boundary is intended to support subsequent
  canonical object execution and Core source bootstrap.


## [0.2.1-SNAPSHOT] - 2026-09-04

### Added

- Added executable numeric literal materialization and semantic identity execution.
- Added the runtime ordinary-object foundation with immutable delegation parents,
  local/delegated lookup, slot mutation, structural open/closed/frozen state,
  local-slot removal and snapshots, composition views, and atomic composition
  contributions.
- Added activation-context lookup ordering, Core error signaling, `this` and
  `context` execution, bare/member slot mutation, member lookup-home preservation,
  Closure materialization, and extracted-method binding foundations.
- Added empty-Sequence execution returning canonical `null`.
- Added canonical object composition-reservation discovery for direct local
  declarations.
- Added the initial standard `Context` bootstrap scaffold and fresh execution
  contexts delegating through `Context` to `Object`.
- Added the non-normative Core bootstrap architecture and reserved
  `protos/lib/core/` for standard objects and behavior implemented in Protos.

### Changed

- Project implementation version changed from `0.2.0-SNAPSHOT` to
  `0.2.1-SNAPSHOT`.
- Established an explicit repository rule requiring every committed executable
  implementation or distributable Core-library change to bump the Maven
  implementation patch version and add its corresponding root changelog entry.
- Limited Java-side standard-object construction to irreducible or explicitly
  temporary bootstrap scaffolding; ordinary Core behavior should move to
  `protos/lib/core/` as soon as it can be loaded faithfully.

### Notes

- This entry catches up implementation release metadata that was not maintained
  during the preceding incremental runtime/execution work.
- Protos Core language version remains 0.1.
- No normative language semantics are changed by the bootstrap architecture
  decision; observable Core behavior remains defined exclusively by `spec/`.
- The current Java-side `Context` bootstrap is temporary scaffolding until the
  Core source loader can construct it faithfully.

## [0.2.0-SNAPSHOT] - 2026-09-01

### Added

- Added canonical String literal execution as ordinary immutable Protos String values.

- Added initial Canonical-to-Truffle lowering for canonical `true`, `false`, and `null` singleton literals and non-empty sequences.

- Added a Truffle CallTarget entry point for executing Protos expression trees through the root-node boundary.
- Added the initial Truffle root execution boundary, delegating directly to the executable expression tree without introducing language-value semantics.

- Added the first executable Truffle node: non-empty expression sequences execute strictly left-to-right and return the final child result.
- Added the initial Truffle execution-node boundary with source-span preservation and the Truffle API dependency.
- Added canonical call-spread lowering with a contextual `CanonicalSpread(expression)` marker for ordinary, member, and super invocation arguments.
- Added dedicated canonical indexed-assignment lowering that preserves receiver/index/value evaluation structure and the distinct `atPut` assignment-result semantics.
- Added dedicated canonical lowering for non-spread `super.message(arguments...)` operations, preserving super lookup semantics separately from ordinary message sends.
- Added dedicated canonical intrinsic nodes for the reserved execution-context expressions `this`, `context`, and `args`, keeping them distinct from ordinary lexical lookup.
- Added canonical lowering for non-spread calls, preserving the semantic distinction between ordinary `Call(receiver, arguments)` and member message `Send(receiver, message, arguments)` forms.
- Added canonical object lowering with optional explicit parent, canonical object-body sequencing, and `Compose(object)` nodes for contextual composition items.
- Added canonical `Return(value)` lowering for the `^ expression` non-local return form.
- Added canonical `Create`/`Assign` lowering for bare and explicit-member slot writes while leaving indexed assignment for its distinct `atPut` semantics.
- Added canonical lowering for indexed reads as ordinary one-argument `at` message sends.
- Added canonical lowering for lazy `&&` and `||` as `and`/`or` message sends whose right-hand side is wrapped in a parameterless canonical Closure.
- Added canonical Closure and parameter lowering, including mandatory normalization of expression-bodied Closures to a one-expression canonical Sequence.
- Added canonical lowering for semantic equality/inequality and non-overridable identity/non-identity, with a dedicated canonical identity node.
- Added canonical lowering for the standard comparison operators `<`, `<=`, `>`, and `>=` as ordinary one-argument message sends.
- Added canonical lowering for the standard arithmetic operators `+`, `-`, `*`, `/`, and `%` as ordinary one-argument message sends.
- Added canonical lowering for custom symbolic binary operators as ordinary one-argument message sends while leaving standard binary operators for dedicated semantic lowering.
- Added canonical lowering for Core prefix `-` and `!` as ordinary zero-argument `negated` and `not` sends.
- Added the canonical semantic AST foundation and the first Surface AST canonicalization slice for literals, name lookup, grouping, member reads, and sequences.
- Established `.protos` as the project source-file extension and added initial non-normative tutorial, task-oriented example, and portable benchmark corpora.
- Added benchmark workloads for recursion, slots, closure and method calls, object creation, delegation depth, and monomorphic/polymorphic dispatch.
- Added parser support for same-line parameterless trailing closures as the final argument of ordinary call suffixes.
- Added closure surface AST integration and deterministic parsing for closure parameters, defaults, rest parameters, and braced or expression bodies.
- Added parser support for object expressions, parent expressions, and contextual object composition items.
- Added parser support for structural super message sends without making `super` a first-class expression.
- Added parsing for slot creation and assignment with grammar-defined target restrictions.
- Added deterministic parsing for the grammar-defined non-local return expression.
- Added parser support for the separate custom binary-operator precedence domain and its required standard/custom mixing errors.
- Added deterministic parsing for the standard unary and binary operator precedence ladder.
- Added parser support for same-line semicolon expression separators with the grammar-defined error cases.
- Extended the parser foundation with parenthesized expressions, member access, calls, indexing, argument spread, and leading-dot continuation.
- Added the first deterministic parser foundation with source-aware errors and a portable surface AST.
- Added source-aware lexer token occurrences with portable half-open source spans as parser infrastructure.
- Expanded lexer conformance coverage for raw Unicode scalar handling across every Core String form, including rejection of unpaired surrogates.
- Expanded lexer conformance coverage for numeric termination at structural delimiters, logical newlines, and standard and custom operators.
- Expanded lexer conformance coverage for uppercase radix-prefix commitment and case-insensitive exponent completion errors.
- Expanded lexer conformance coverage for malformed numeric/identifier adjacency across ASCII, reserved-word, underscore, and Unicode identifier continuations.
- Expanded lexer conformance coverage for single-line String raw-newline rejection, raw-source indentation matching, and interpolation-looking escape rejection.
- Expanded lexer conformance coverage for triple-double String CR/CRLF delimiter-newline handling and shared escape semantics.
- Expanded lexer conformance coverage for Unicode escape digit-count boundaries, hexadecimal case handling, and the closed String escape set.
- Expanded lexer conformance coverage for exact, case-sensitive reserved-word recognition and the closed Core v0.1 reserved-word set.
- Expanded lexer conformance coverage for triple-double String structural indentation, including absent and empty prefixes, blank-line exemption, and exact SPACE/TAB matching.
- Expanded lexer conformance coverage for the symbolic-operator alphabet, maximal-munch classification, and comment/operator lexical precedence.
- Expanded lexer conformance coverage for numeric separator placement, unsupported suffixes and radix floats, special-value identifiers, and valid token boundaries.
- Expanded lexer conformance coverage for line-comment termination, non-nesting block comments, first-delimiter closure, and comment delimiters inside Strings.
- Expanded lexer conformance coverage for String normalization independence, raw Unicode scalar content, quote-run boundaries, and the absence of triple-single String syntax.
- Expanded lexer conformance coverage for Core v0.1 String quote-run boundaries, unterminated String forms, and the closed whitespace rules.
- Added bundled Unicode 17.0.0 character-property and normalization data used by the lexer independently of the host JDK Unicode version.
- Added conformance tests against the official Unicode 17.0.0 `XID_Start`, `XID_Continue`, and normalization test data.

### Changed

- Decoupled canonical literal kinds from parser surface literal kinds with an explicit Surface-to-Canonical mapping.
- Corrected malformed parser imports introduced by slot-creation and assignment support.
- Restored the canonical NEWLINE token value after adding source-aware token occurrences.
- Fixed source-aware newline token occurrence emission so canonical NEWLINE lexemes remain valid Java Strings.
- Updated GitHub Actions CI to current supported `actions/checkout` and `actions/setup-java` major versions.
- Relicensed Protos from the Server Side Public License (SSPL) v1 to the OSI-approved Adaptive Public License 1.0 (APL-1.0). The complete license, including the completed Exhibit A, is in [LICENSE.TXT](LICENSE.TXT).
- Project implementation version changed from `0.1.6-SNAPSHOT` to `0.2.0-SNAPSHOT`.

### Notes

- Protos Core language version remains 0.1.
- No language semantics changed.

### Fixed

- Fixed parser newline continuation while a member suffix or structural `super` message send is necessarily incomplete.
- Fixed parser newline continuation after `...` in spread arguments and rest parameters, matching the grammar's necessarily-incomplete construct rule.
## [0.1.6-SNAPSHOT] - 2026-08-31

### Added

- Expanded lexer support to cover the lexical rules defined by the current Core v0.1 specification.
- Added lexer support for comments, Unicode-aware identifiers, reserved words, numeric literal forms, multiline strings, ellipsis, and custom symbolic operators.
- Added lexical validation for malformed string escape sequences and invalid Unicode scalar values.
- Added regression coverage for supplementary Unicode escape sequences.

### Changed

- Updated lexer tests to match the current specification for reserved words, period tokenization, and numeric literals adjacent to periods.
- Unicode escape decoding now preserves supplementary Unicode code points instead of truncating them to Java `char` values.

### Notes

- The lexer implementation is still under specification-compliance review.
- Unicode identifier handling requires further review for exact `XID_Start` and `XID_Continue` compliance.
- Specification changes are documented separately in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.5-SNAPSHOT] - 2026-08-31

### Notes
- No implementation changes in this session.
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.4-SNAPSHOT] - 2026-08-31

### Added
- Initial minimal Protos lexer implementation under `com.guillermomolina.protos.lexer`.
- Token model and token type definitions to represent the current lexical grammar.
- Basic lexical support for:
  - identifiers and reserved intrinsic keywords
  - numeric literals, including radix literals (`0x`, `0b`, `0o`)
  - string literals with escape handling
  - punctuation and structural tokens
  - operators and custom symbolic operators
  - newline-delimited token separation
- Focused lexer regression tests covering the implemented lexical behavior.
- Project licensing metadata by adding the license text in the then-current `LICENSE` file and referencing it from the README.

### Changed
- Added JUnit 5 to support lexer-focused test coverage.
- Updated project version from `0.1.3-SNAPSHOT` to `0.1.4-SNAPSHOT` as a conservative patch bump for the completed milestone.
- Documented the chosen license in the project README for this version.

### Notes
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).
