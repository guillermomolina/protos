# Protos Implementation Changelog

Current implementation-series changes are kept in this file.

Historical implementation changelogs:

- [0.3.x](changelog/CHANGELOG-0.3.md)
- [0.2.x](changelog/CHANGELOG-0.2.md)
- [0.1.x](changelog/CHANGELOG-0.1.md)

## 0.4.34-SNAPSHOT

- **I115 — experimental GraalPy-shaped source-function root and Context lookup.**
  - Resolve an already prepared source Closure's bytecode activation root directly
    when its execution plan belongs to the entered Context and needs no
    Context-local projection. Preserve the general target lookup for deferred
    plans and Context-local projections, rather than assuming all Closures
    share a physical target or lifetime.
  - The shared `ProtosFunctionCallDispatchers` resolves its entered
    `ProtosLanguageContext` using `current(node)` instead of the host-side
    `currentIfEnteredForRuntime()` lookup.
  - Update source-call admission tests and deferred-caller tests to pin the
    historical selection of a source function despite an instance-local
    `call` override (including overrides created during argument evaluation).
    A separate explicit `late.call(7)` still selects that local method.
  - Original source delta: 3 modified files, 0 added, 0 deleted. All source
    and test blobs are reproduced verbatim from the historical commit.
  - **Historical semantic limitations:** the source-function direct-dispatch
    rule bypasses an instance-local `Object.call` override on `callOne(late, 7)`.
    This is a known D013/dynamic-message-selection semantic divergence, not
    evidence that Protos' ordinary message semantics were recovered. Deferred
    caller, ReturnHome, Task/C-prime, suspension/resumption, cancellation,
    structured cleanup, non-local transfer and D179 C0 require independent
    conformance. `long`-only integer representation and internal specialized
    message execution remain project-wide requirements, not certified here.
    No runtime tests, Truffle graphs, timing or performance acceptance were
    run for this reconstructed versioned historical commit.
  - Original implementation: `b3730cd8abbfdcb1674c650019a3ab4c572472a0`.

## 0.4.33-SNAPSHOT

- **I116 — experimental unification of Bytecode DSL semantic execution and on-demand Truffle tooling**
  in the chronological I115 source branch. This is the original implementation
  state, not a certification of semantic or performance equivalence.
  - Replace the separate prepared-call, guarded-send and callback entry
    mechanisms with `ProtosFunctionCallDispatchers`, `ProtosCallRequest`,
    `ProtosBoundMethod` and fixed-arity `CallNilaryMethod`, `CallUnaryMethod`,
    `CallTernaryMethod`, `CallQuaternaryMethod` and `CallVarargsMethod` nodes.
    Add Context-owned builtin call roots and dispatch source/native Closures
    through the shared B5 argument protocol, including selected method homes.
  - Route structured Boolean/control, collection and Map protocols, Task-owned
    execution, host-entry continuation and I/O C-prime through shared call
    requests and dispatcher-owned invocation handling. The revised continuation
    transport intends to resume a suspended callee without repeating its
    original call; cancellation and ReturnHome ownership require independent
    behavioral conformance checks.
  - Reconcile Truffle tag-tree and debugger scope access with on-demand
    activation materialization. Remove historical inline callback binding
    preparation/tooling, guarded send helpers and other superseded code.
    The removal of inline-specific scope projection is a tooling-behavior
    change requiring explicit debugger and instrumentation verification.
  - Original source delta: 87 files (9 added, 71 modified, 7 deleted).
    The entire implementation, test and source file tree delta is copied
    byte-for-byte from the original commit. The historical tests were not
    run while constructing this versioned replay; no tests, Tier 1/Tier 2
    graphs or timing results are claimed for this reconstructed version.
  - **Historical semantic limitations:** unification alone does not prove
    `Object.call` override selection, frozen-state or lexical authority,
    exactly-once ReturnHome completion, Task cancellation, C-prime ownership,
    suspension/resumption, structured cleanup, non-local transfers or D179 C0.
    The signed-64 `long` numeric contract and specialized internal message
    execution still require independent regression gates across all paths;
    compatibility of on-demand Truffle tooling remains unverified here.
  - Original implementation: `0c1df650522717a390089eaeac111135935a7862`.

## 0.4.32-SNAPSHOT

- **I115 — experimental convergence of the unary Closure PIC and local `run` root**
  with GraalPy-style fixed-arity dispatch. This entry records the original
  implementation state; replaying its bytes is not semantic acceptance.
  - Rework the `CallUnaryMethod` direct-call PIC around weakly held concrete
    Closure identity (limit three), with guarded canonical `Object.call`
    selection and root-call, code, and Context-projection assumptions.
    On saturation the generic tier handles the megamorphic transition.
  - Specialize `MaterializeCurrentClosure` for noncapturing Closures whose
    `ReturnHome` is unobservable, without binding or publishing a creator
    activation. Select the experimental local `Call1`/`run` result transport
    from compilation facts rather than an invocation name or a separate PIC.
  - Add `ProtosI115UnaryPICConvergenceTest` for fresh Closure identity,
    override/restore behavior, cache saturation, capture/ReturnHome cases,
    and local unary call-root structure. Historical tests are preserved,
    not executed by this versioned replay; no Tier 1/Tier 2 graph or timing
    measurements are claimed here.
  - **Historical semantic limitations:** the lightweight noncapturing
    Closure may omit creator metadata, and the admitted compact path can
    bypass caller/Task/ReturnHome lifecycle and Context admission. Dynamic
    `Object.call` overrides, suspension and non-local control transfer
    require ordinary fallback and have not been proven equivalent on all
    paths. The prior experimental H7 numeric-protocol and Boolean-control
    divergences and D179 C0 remain open. The signed-64 `long` contract is
    retained in the source replay, but full semantic and performance gates
    are not established by this commit.
  - Original implementation: `4c69f201c560abed905b0a38065b6df6dda1c754`.
    All four original source/test blobs are preserved byte-for-byte.

## 0.4.31-SNAPSHOT

- **I115 — experimental convergence of H7 signed-64 numeric fast paths and
  direct Closure calls with GraalPy.** This is a historical implementation
  checkpoint; the following code and tests are preserved as originally
  committed, not rerun or independently accepted by this replay.
  - Replace 32-bit `int`/`Integer` compact numeric paths with primitive
    signed-64 `long`/`Long` literals, arithmetic and comparisons. The H7
    `NumberAdd` fast path uses checked `long` addition; signed-64 overflow
    promotes to the exact large guest Integer using the effective Prelude.
    A host-supplied Java `Integer` is normalized at guest boundaries and
    is no longer an admitted 32-bit internal numeric carrier.
  - Extend the H11 reduced unary call ABI to transport the effective Prelude
    when a callee registers `NEEDS_CALLER_PRELUDE`, including cold recovery
    on initial numeric overflow. Keep deferred caller transport where it
    is needed, and allow raw `Long` results to cross the admitted compact
    root and Polyglot boundary without premature guest materialization.
  - Update method-call, static/recursive Closure, compact-lowering and
    factorial test expectations. Original tests cover signed-64 literals,
    recursive arithmetic, exact overflow, lazy Prelude propagation and
    host return representation; none are executed as part of this replay.
  - **Historical semantic limitations:** some direct H7 method-call paths
    no longer require a global proof of non-suspension, so exceptional
    continuations and non-terminal suspension may diverge. Experimental
    arithmetic does not always perform the ordinary Prelude numeric-protocol
    admission or dynamic `+`/comparison selection. `Object.call`, Boolean
    control, caller authority, `ReturnHome`, Task lifecycle and D179 C0
    retain historical open divergences. This local `long` migration does
    not establish full language-wide semantic or performance acceptance,
    and no Tier 1/Tier 2 graph evidence is claimed by the replay.
  - Original implementation: `4ac5d115f7c00294be1a8a0133b988eab80708b9`.
    All fourteen original source/test blobs are preserved byte-for-byte.

## 0.4.30-SNAPSHOT

- **I115-C6 — reconcile Bytecode DSL call and Boolean-control oracles with
  the GraalPy-shaped physical lowering.** This is a historical migration
  checkpoint, not a completed semantic or performance acceptance.
  - Add `ProtosCallUnaryMethodNode` with a specialized unary Closure call
    and its generic fallback; converge fixed-arity terminal lowering on
    `CallUnaryMethod` / `CallNilaryMethod`, and retain `CallVarargsMethod`
    for spread and variable arity. Move shared Closure admission helpers
    into `ProtosClosureCallPreparation`; retire the obsolete composed
    `InvokeClosureArguments` operation. The new unary node is the one added
    original source file in this commit.
  - For host Closures with observable `ReturnHome`, add an ordinary-entry
    fallback with RootActor ownership, prepared call completion and
    continuation handling. The narrow no-home direct/indirect route remains
    experimental for Closures with unobservable homes.
  - Reconcile builder, profiling, continuation, CallN/spread, tail-call,
    PERF042 and deferred-caller test oracles with the new physical DSL.
    Update the nested Boolean-control oracle to expect inline conditional
    branches and the H7 `NumberAdd` operation, while still recording
    dynamic-receiver fallback cases. No tests are executed in this replay.
  - **Historical semantic limitations:** retaining the experimental
    compact paths does not prove dynamic `Object.call` selection,
    `ReturnHome`, caller authority, suspension, cancellation or
    non-local control-transfer equivalence; D179 C0 also remains open.
    GraalPy-shaped H7 arithmetic/Boolean paths still use Java `int`
    carriers beyond the H10 `long` attribute lane, in conflict with the
    Protos long-only design. Structural oracle changes are not Tier 1/
    Tier 2 graph evidence or a correctness/performance acceptance.
  - Original implementation: `bbba82478bc48721b75d01b587b5cdbf397f90ac`.
    All thirteen original source/test blobs (one addition) are preserved
    byte-for-byte, without editing the code or historical commits.

## 0.4.29-SNAPSHOT

- **I115-H11 — experimental convergence of Closure and method calls on a compact unary ABI.**
  - Replace the H7 method-call B5-like experimental header with two selected
    unary argument layouts: `[supplied0]` for callees that do not require a
    lexical owner; `[selectedClosure, supplied0]` for roots using `ReadGlobal`.
    A compilation-final callee-root fact selects the layout after lowering.
    `CopyArguments` and `ReadGlobal` recognize these layouts alongside B5.
  - Apply the same reduced unary call protocol to admitted direct Closure
    sends and experimental `CallBinaryMethod`, including indirect tiers where
    admitted. Preserve the selected home only in the generic method lookup
    tier; the cached `GetMethod` path reads a Shape-backed selected value
    through a cached `DynamicObject.GetNode` without copying the slot value.
  - Extend H7 convergence and deferred-caller test coverage for layouts with
    and without captured lexical owner, generic home selection and reduced
    caller publication. These original tests are not run on this replay.
  - **Historical semantic limitations:** the optimized unary ABI omits the
    receiver, method home, caller, `Task` and `ReturnHome` transport, and
    non-local control transfers are not fully translated. Direct admission
    does not establish equivalence to dynamic `Object.call` selection or
    general suspension/continuation semantics. H7 still contains Java `int`
    carriers outside the H10 attribute `long` lane, so Protos' long-only
    goal is not yet met; D179 C0 and other existing experimental divergences
    remain unresolved. No complete correctness or performance acceptance is
    claimed for this historical point.
  - Original implementation: `b429ee096421fc55f326cfff9b986d0155b9d4a8`.
    All ten original source/test blobs are preserved byte-for-byte.

## 0.4.28-SNAPSHOT

- **I115-H10 — experimental 64-bit Shape-backed attribute operations.**
  - Replace `ExperimentalSetAttribute.directInt` with a signed-64 `directLong`
    specialization and use a `PropertyGetter.getLong` fast path in
    `ExperimentalGetAttribute`, with a guest-value Object fallback.
    Compact numeric assignment emits a `Long` constant for eligible
    attribute stores rather than narrowing the value to `int`.
  - Keep one authoritative Shape-backed `DynamicObject` store: normalize
    Protos guest Integer values and residual Java `Integer` carriers into
    Java `Long` on storage, then return guest-facing `ProtosIntegerValue`
    representations from standard binding reads, snapshots and removals.
    Read admission no longer mutates storage simply to convert to `int`.
  - Extend tests for signed-64 storage, large positive and negative values,
    type generalization, removal/recreation and the typed/object fallback.
    A generalized Shape may continue using an Object location after recreate.
  - **Historical semantic limitations:** this H10 attribute-storage lane
    uses `long` (signed 64-bit), but other I115-H7 experimental operations
    still use Java `int` and raw `Integer` carriers, so Protos' global
    long-only design is not yet achieved. The H8/H9 direct write retains
    the deliberate bypass of frozen-state mutation checks; other known
    divergences in `Object.call`, `ReturnHome`, suspension and D179 C0
    remain unresolved. No complete semantic or performance acceptance
    is implied, and no behavioral tests run on this historical replay.
  - Original implementation: `f8dce47fbf371e5033c3fccd178cfb592c8867ac`.
    All six source/test blobs are copied without modification.

## 0.4.27-SNAPSHOT

- **I115-H9 — experimental Shape-backed object attribute access.**
  Replace the ordinary object's prior map/cell value storage with one
  authoritative `DynamicObject` whose `Shape` holds the local slot values.
  The `LinkedHashMap` continues to index names and optional PIC `SlotCell`
  handles but no longer duplicates guest values.
  - Introduce `ExperimentalGetAttribute`, with a cached `PropertyGetter`
    and a primitive `int` specialization followed by an Object fallback.
    The lowering of admitted compact member reads now uses this operation
    instead of `ReadMemberAtRoot`; call-site guards and assumptions govern
    selected locations and non-Closure continuity.
  - Specialize `ExperimentalSetAttribute` for primitive `int` writes and
    Object writes using `DynamicObject.PutNode`. Both use the same selected
    Shape-backed storage as ordinary lookup and writes; invalidation of
    value-lookup dependencies remains the selected-slot responsibility.
  - The experimental readable-slot admission may normalize an existing
    signed-32 guest `ProtosIntegerValue` into a raw Java `Integer` in the
    authoritative store. Extend the compact slot read/write tests and
    pay-as-you-grow storage tests for the new physical representation.
  - **Historical semantic limitations:** the optimized write retains H8's
    deliberate bypass of frozen-state checks and guest value normalization.
    Primitive `int` (`Integer`) carriers remain in storage and at compact
    boundaries, in conflict with Protos' long-only Integer design. Caching
    Shape-backed locations is experimental and does not establish general
    equivalence with dynamic message selection, mutable object semantics or
    lexical rebinding under D179 C0. Previous divergences involving
    `Object.call`, `ReturnHome` and suspension remain unresolved.
    This historical replay does not claim compilation, behavioral tests,
    numerical conformance or a measured performance improvement.
  - Original implementation: `24ad02ed3a21f946e7222be64c144e4700e64cdd`.
    All seven original source/test blobs remain unchanged in this replay.

## 0.4.26-SNAPSHOT

- **I115-H8 — experimental GraalPy-shaped compact object-slot writes.**
  Introduce `ExperimentalSetAttribute` in the Bytecode DSL for a compact
  explicit assignment to an existing member of a module-captured object.
  The receiver is evaluated before the right-hand side, and the write
  executes as one specialized bytecode operation, not a prepared send.
  - At specialization installation, cache the identity of an exact
    `ProtosObjectValue` and its stable physical `SlotCell` through
    `ProtosValueLookup.experimentalWritableSelection`. A valid Truffle
    assumption protects the selected location, while slot-value changes
    invalidate value-lookup dependencies. The slow tier uses ordinary
    `assignLocalSlot` and emits a guest error for an invalid mutation.
  - Expand compact-root admission and emission for explicit member stores
    and selected member reads. Reuse `ReadGlobal`, `ReadMemberAtRoot`, and
    the R5 raw-Integer return convention on the experimental path.
  - Extend `ProtosPrimitiveSlotWriteRegressionTest` to assert the physical
    `ExperimentalSetAttribute` shape and that the stored value may remain
    an internal Java `Integer` carrier. These are historical source tests;
    none were executed in this versioned replay.
  - **Historical semantic limitations:** the optimized write intentionally
    bypasses frozen-state checks and guest numeric normalization, allowing
    mutation that ordinary Protos would reject and permitting raw `int`
    (`Integer`) storage rather than the guest long-only Integer contract.
    It is NOT a normative implementation of `slotAtPut` or ordinary slot
    mutation. Prior experimental divergences in dynamic `Object.call`
    selection, `ReturnHome`, suspension, and D179 C0 lexical authority
    remain unproven or unresolved. No successful compilation, complete
    semantic test gate, or performance improvement is claimed.
  - Original implementation: `ba680dd2c93c9994f81164dc4b395279a455a1d6`.
    All five original source/test blobs remain unchanged in this replay.

## 0.4.25-SNAPSHOT

- **I115-H7-R5 — retain raw Integer carriers across compact returns and Polyglot host entry.**
  Preserve an H7 compact result as a Java `Integer` through both the bytecode
  root epilogue and the source-backed Polyglot host boundary, instead of
  allocating a guest `ProtosIntegerValue` at either location.
  - Add the compilation-final root flag `experimentalH7RawIntegerReturn`
    when compact lowering admits an H7 invocation. `EpilogForReturn` preserves
    raw `Integer` results for that root; other result types and generic roots
    retain their preceding projection behavior.
  - Use the same root flag in direct and indirect `ProtosHostExecutableClosure`
    entry. A flagged root returns an `Integer` unchanged; a non-flagged root
    still converts its `Integer` to a guest numeric value at the host edge.
  - Extend the original tests to inspect the **actual Java return type** at
    both boundaries and check that wider numbers and ordinary roots do not
    accidentally use this special case. No tests were run on this historical
    versioned replay; this is source-level reconstruction, not validation.
  - **Historical semantic limitations:** R5 deliberately exposes a Java `int`
    carrier (`Integer`) across the host boundary, conflicting with Protos's
    long-only `Integer` design. This historical exception does not establish
    preservation of guest Integer identity, dynamic `Object.call` selection,
    `ReturnHome` lifecycle, suspension or all caller/Prelude transport.
    Existing H7 numeric protocol deviations, D179 C0 lexical retargeting,
    and R4's version-dependent physical Object-frame indexing remain open.
    No compilation, comprehensive test acceptance, or performance gain is
    asserted for this replay.
  - Original implementation: `52e3f221bde99841c2a395a42af269bb8d98ca4c`.
    The four original source/test files retain their exact Git blobs.

## 0.4.24-SNAPSHOT

- **I115-H7-R4 — validated direct Object-frame ReadGlobal fast path.**
  Specialize the experimental H7 module-global read for compact Closure
  invocations using a PIC of up to three Closure identities, instead of
  repeatedly traversing the captured module's lexical chain on every read.
  - Resolve the module and its frame-backed physical `Object` location at
    PIC installation behind `TruffleBoundary`. Require that the selected
    binding, bytecode root, frame offset and physical Object value agree
    with the public Bytecode local-value API before admitting the fast path.
  - Use a hardcoded `USER_LOCALS_START_INDEX = 2` in the frame indexing:
    this is an internal generated layout assumption for Truffle 25.4.4.1.1,
    **not a portable Truffle API guarantee**. Revalidate on upgrades.
  - On the hot path, read `MaterializedFrame.getObject(physicalIndex)`
    directly, without `ProtosNumericValueSupport.guestValue` or a lexical
    lookup. Read the current slot value rather than caching a prior value,
    so an Object rebinding remains visible. A primitive frame-tag change
    invalidates the optimized site and recovers via a cold generic read.
  - Published invocations, non-Object physical slots, and locations without
    a valid admission use `readGeneric`. Extend the H7 tests with Object
    rebinding, layout/guard and primitive-tag transition probes.
  - **Historical limitations:** the cached frame mapping does not recheck
    binding presence or lexical layout on every read; deletion or late
    retargeting may violate D179 C0. This step does not resolve H7's `int`
    carriers versus Protos's `long`-only contract, `Object.call` dispatch,
    Caller/Prelude transport, nonterminal suspension or `ReturnHome`.
    The original source and tests are experimental; no new performance or
    comprehensive semantic acceptance is claimed for this replay.
  - Original implementation: `bb8fab40beacb1514bc9713bbf7642e2d985767b`.
    No compilation or behavioral tests were run on this historical replay;
    three original files retain their Git blobs unchanged.

## 0.4.23-SNAPSHOT

- **I115-H7-R3 — converge primitive-method-call bytecode with GraalPy 25.4.4.1.1.**
  Continue the experimental H7 `primitive-method-call` workload using the
  GraalPy-shaped `ReadGlobal`, `GetMethod`, `CallBinaryMethod`, `CopyArguments`
  and return epilogue, without changing the normative specification.
  - Introduce `ExperimentalGlobalReadLocation`: cache the exact physical
    frame-backed module-binding location at specialization installation;
    guard the selected BytecodeNode with `isCurrent()` and read its current
    value directly. The fast path does **not** recheck binding presence or
    layout; deletion and late lexical retargeting are not guaranteed (D179 C0).
  - Make `GetMethod` return the selected physical slot **value**, rather than
    a `Method` descriptor; retain identity and slot-stability guards but
    remove caller-Prelude selection and its cache guard from this hot path.
    Thus some inherited-Prelude needs are no longer registered there.
  - Lower the receiver once with `BindStackValue`, reload for `GetMethod`
    and `CallBinaryMethod`, and evaluate the explicit argument once, after
    method selection. `CallBinaryMethod` consumes `(callable, receiver,
    argument)`, with generic fallback retained for non-admitted selections.
    Direct compact method calls omit `methodHome` and normally carry no
    caller reference unless a callee root has registered a caller need.
  - Simplify the admitted `CopyArguments` and return paths: the unary
    parameter is copied directly from its B5 slot, without a per-call arity
    or publication fallback; experimental roots return primitive results
    without a caller-side carrier projection test. An internal `int` carrier
    may escape. H7's `int` paths conflict with Protos's `long`-only design.
  - Update H7 structural tests and B5 deferred-caller expectations. The
    original experiment document cites **R2** After TruffleTier 417/329,
    Final IR 866/714 (Tier 1/Tier 2) and five Tier 2 invokes. R3 has no new graph capture,
    invocation-count or timing measurement. These R2
    numbers are historical observations, not measurements of this replay.
  - **Historical limitations:** dynamic message overrides, Caller/Prelude
    transport, nonterminal suspension, `ReturnHome`, binding presence and
    D179 C0 lexical semantics are not established as equivalent. This is
    an experimental checkpoint, not an accepted semantic/performance gate.
  - Original implementation: `94e321dbb94ff8d6f65e380cd80a71bbdff3048f`.
    No compilation or behavioral tests were run on this historical replay;
    the ten original source, test and experiment files retain their Git blobs.

## 0.4.22-SNAPSHOT

- **I115-H7-R2 — preserve deferred B5 callers across direct sends.**
  A guarded, synchronous source `TryDirectSendZero` can retain the caller's
  unmaterialized B5 frame when the caller inherits its Prelude, instead of
  eagerly publishing a `ProtosActivation` merely for method dispatch. Roots
  owning their own Prelude retain the existing exact-caller route.
  - Query the selected callee root's `NEEDS_CALLER_PRELUDE` contract and
    transport the inherited prelude through `InvokeMethodOneTail.callerArgument`
    only when requested; otherwise keep the caller reference deferred. The
    callee may register that need on its first untransported read.
  - Change `compactImmediateMethodCallZeroNoHome` to accept a general B5
    caller reference, not only an already materialized activation.
  - Make the generic `InvokeClosureOneTail.exactCaller` request
    `NEEDS_EXACT_ACTIVATION` even if its incoming caller is already published;
    distinguish that conservative fallback from the direct Send0 route.
  - Restrict experimental `CallBinaryMethod` direct/indirect target admission
    to non-native, non-suspending source Closures with **unobservable**
    invocation `ReturnHome`; an observable captured home stays on ordinary
    send dispatch. This prevents silently discarding a materialized home.
  - Remove temporary `PROTOS_I115_TRACE_CALLER_NEEDS` probes. Extend H7
    method-call and deferred-caller structural tests: the A/B/C/D chain now
    records inherited-prelude needs on B, C and D, not A, with convergence
    after transport is established.
  - **Historical limits:** this is a targeted correction within the unfinished
    H7 experiment, not a complete semantic or performance gate. H7's `int`
    carriers remain contrary to Protos's `long`-only integer design; generic
    nonterminal suspension, dynamic message overrides and lexical authority
    are not proven equivalent. Earlier D179 C0 lexical-retargeting and H6
    host-entry / RootActor divergences are not resolved by this commit.
  - Original implementation: `a38f551b39a7681ec05c4154142f16488278a264`.
    Replayed without compilation or behavioral tests; all six original code
    and test files are required to retain their historical Git blob IDs.

## 0.4.21-SNAPSHOT

- **I115-H7 Unfinished** — experimental GraalPy-shaped physical convergence for a
  `receiver.identity(i)` method-call workload inside a literal `whileTrue`
  (reference result `499500`). This is a historical *unfinished* checkpoint,
  not a validated implementation or a normative specification change.
  - Add experimental Bytecode DSL operations `GetMethod`, `CallBinaryMethod`,
    `NumberAdd`, `NumberInPlaceAdd`, `Lt` and `CopyArguments`; introduce guarded
    method selection, small direct/indirect Closure call caches, and generic
    send fallbacks. Existing source-level message syntax remains unchanged.
  - Lower admitted literal `whileTrue` expressions as an internal `While`
    without creating condition/body Closure objects. Overrides of `whileTrue`
    and observable callback identity, capture and lifetime are not respected.
  - Replace H5's frozen `ReadGlobal` specialization with a module binding
    read that can observe later module mutations; late nearer lexical
    retargeting required by D179 C0 is still not fully preserved.
  - Specialize arithmetic and local storage on `int`, `long`, and `double`,
    with selected exact-overflow promotion and generic fallbacks. **Historical
    nonconformance:** `int` guest carriers contradict Protos's long-only
    Integer architecture. Primitive numeric fast paths bypass the Prelude
    protocol / overridden operators, and some mixed `int`/`long` pairs fall
    through to generic dispatch. This is not an endorsed long-only design.
  - The generic dispatch paths may publish a caller activation without
    promoting its frame-local bindings to lexical authority; nonterminal
    suspension has no correct continuation in the experimental compact root.
    Dynamic method selection, caller authority, cancellation and ReturnHome
    are not established as equivalent by the new optimization.
  - Add the non-normative experiment report and H7 regression tests. The
    original H7 note explicitly says that instruction censuses, Tier 1/Tier 2
    graphs and timings were pending human execution. A temporary
    `PROTOS_I115_TRACE_CALLER_NEEDS` diagnostic remains in the original code.
  - Reconstructed byte-for-byte (for original files) from commit
    `b51a9a5f82271dc0c6b41897a0afc25e3105e3cc`. No behavioral tests or
    compilation were performed during this versioned historical replay.

## 0.4.20-SNAPSHOT

- `I115-H6` (experimental collapse of source-backed Java/Polyglot host entry):
  enter the guest with exactly one compact no-home B5 call when a source-backed
  executable receives zero or one supplied argument. The installed call target
  is invoked directly or indirectly; the target-null generic path remains.
  - Remove host-entry processing from this admitted route: no Context
    revalidation, Process or RootActor admission, host-entry extent or Task,
    ReturnHome completion, suspension/continuation driver, bridged
    control-transfer translation, failure mapping, or guest-result projection.
    Raw guest-call results cross the Polyglot boundary without conversion.
  - Add focused H6 host-entry regression tests and revise interop and future-
    suspension oracles to reflect the narrower experimental entry contract.
    These original test changes are evidence of historical intent, not proof
    that ordinary host entry remains semantically equivalent.
  - **Historical semantic limitations:** bypassing RootActor and Context
    checks can violate normal concurrency and lifecycle guarantees; omitting
    suspension, ReturnHome, and control-transfer handling can change observable
    program behavior. Existing H5 stale global-value caching and H3 wrapping
    long arithmetic remain, as does the D179 C0 late-retargeting divergence.
    This is not a normative change or a complete correctness/performance gate.
  - Reconstructed from original I115 commit
    `f8e51ff6b76fbbe011c77dfdcc43f9f7ac26afb8`.
    No behavioral tests were run on this versioned historical replay.

## 0.4.19-SNAPSHOT

- `I115-H5` (experimental factorial hot-path reductions): retain the physical
  factorial root while removing experimental unary dispatcher/context helpers
  and restoring one direct `InvokeExperimentalClosureOneTail` Bytecode DSL
  operation for statically admitted recursive Integer callees.
  - The optimized Call1 resolves the target once when installing its direct
    specialization and invokes it with a reduced static unary header, without
    transporting the caller, Prelude, Task or ReturnHome. Non-admitted callees
    continue through the ordinary Call1 path.
  - Simplify module `ReadGlobal` to an unguarded specialization-installation
    value cache; an uncached interpreter still resolves each execution.
    Remove primitive return-carrier projection for experimental callee roots.
  - Update factorial and structural oracles, removing three experimental
    call-dispatch/context source files no longer used by this iteration.
  - **Historical semantic divergences:** the cached global value does not
    observe later rebinding, module/Context changes or authority mutations;
    the direct static invocation does not recheck `Object.call`, receiver
    identity or Context, and omits suspension and bridged control-transfer
    handling. The existing H3 wrapping `long` arithmetic and D179 C0 late
    lexical-retargeting divergence remain. This is not normative conformance.
  - Reconstructed from original I115 commit
    `d319618e17e7c36a9f1713784ac0564a284d2f36`. No behavioral tests
    were run on this versioned historical replay.

## 0.4.18-SNAPSHOT

- `I115-H4-B` (converge experimental unary Closure dispatch on B5):
  move the experimental direct Call1 path back to the existing B5+ frame
  header and its caller transport, instead of the separate seven-slot ABI7.
  - Stage the Closure and caller in the B5+ header, with dynamic per-receiver
    admission and the ordinary D013 terminal Call1 fallback for misses. The
    revised path accounts for target stability, ReturnHome and suspension
    handling without certifying general semantic equivalence.
  - Remove the unused `ProtosExperimentalCalleeContext` and its private
    per-invocation state. Use B5 as the callee frame contract and eliminate
    the ABI7-specific lowering admission and header implementation.
  - Revisit the experimental module `ReadGlobal` prelude cache: inspect the
    module on every read, and avoid caching an outer scope that could be
    shadowed by a nearer scope. Refresh structural factorial tests.
  - **Historical limitations:** the separate H3 static recursive unary lane
    retains the wrapping `long` overflow divergence; D179 C0 late lexical
    retargeting remains unproved for module-owned `ReadGlobal`. The altered
    bytecode tagging and generator configuration remain experimental. This
    historical replay performs no compilation, tests or acceptance gates.
  - Reconstructed from original I115 commit
    `883c77744936fa8e2fe65fb6272987f13745d315`.

## 0.4.17-SNAPSHOT

- `WIP I115-H4-B` (experimental GraalPy-style unary-call infrastructure):
  port an experimental `CallUnaryMethodNode`-like direct call path with
  `ProtosExperimentalCallUnaryNode`, dispatch helpers, caller/callee contexts,
  and a separate seven-slot GraalPy-inspired frame-argument ABI (ABI7).
  - Admit the direct path only for a statically certified ABI7-compatible
    recursive Integer callee; use the ordinary B5 terminal Call1 for
    non-admitted callees. Introduce a per-Closure code-stability Assumption
    and specialized module `ReadGlobal` support.
  - Experiment with automatic RootTag/RootBodyTag tagging, Bytecode DSL
    serialization configuration, frame bytecode-index storage and an optional
    JVM-controlled call-trace instrumentation; these are not validated as
    behaviorally equivalent to the preceding instrumentation configuration.
  - Extend factorial and incompatible-callee tests; the original commit is
    explicitly WIP and does not establish successful compilation, complete
    regression coverage or a passing semantic/performance acceptance gate.
  - **Historical semantic divergences:** the specialized route does not
    preserve all dynamic `Object.call` selection, caller/Task/Prelude/
    `ReturnHome` transport, suspension, bridged control transfers, null-result
    validation or B5 interoperability. The experimental `ReadGlobal` behavior
    still diverges from D179 C0 late lexical retargeting; H3's wrapping `long`
    arithmetic and disabled exact-overflow test also remain. These are
    historical characteristics, not normative changes or an acceptance.
  - Reconstructed from original I115 commit
    `0b75afa903a33c3fba0acca1c7b9300531aaa2f4`.
    No behavioral tests have been run on this versioned historical replay.

## 0.4.16-SNAPSHOT

- `I115-H4-A` (experimental GraalPy-style direct terminal Call1 for factorial
  `run()`): add `InvokeExperimentalClosureOneTail`, with a cached
  Closure-identity PIC and `DirectCallNode` for a structurally admitted
  terminal call to a certified H3 recursive Integer Closure. Non-admitted
  invocations retain the general `InvokeClosureOneTail` path.
  - Record the certified recursive module binding during lowering and
    restrict the new operation to the corresponding terminal unary call
    of a parameterless Closure root; preserve other ordinary call sites.
  - Update the historical factorial structural expectations: three
    physical roots and the six-instruction `run()` including one
    `InvokeExperimentalClosureOneTail`. These are source-test oracles,
    not measurements made during this versioned replay.
  - **Historical semantic divergence:** the direct PIC ignores dynamic
    `Object.call` replacement and cross-Context projection, reuses a
    cached target without a code-stability invalidation equivalent,
    transports no caller, Task, Prelude or ReturnHome, and does not
    check ReturnHome observability or potential suspension. It also
    bypasses bridged control-transfer translation and null-result
    checking and returns directly to the calling root's epilogue.
  - H3's wrapping signed-64 `long` overflow and the earlier D179 C0
    late-lexical-retargeting divergence remain unresolved. This is an
    intermediate experimental optimization, not a complete semantic,
    suspension, Context-isolation or performance acceptance gate.
  - Reconstructed from original I115 commit
    `aac8ee5fc091491c4b04447ae4c9dc271fdc2e7a`.
    No behavioral tests were run on this versioned historical replay.

## 0.4.15-SNAPSHOT

- `I115-H3` (experimental factorial bytecode hot path): specialize the
  previously admitted compact recursive factorial lane, without widening
  its correctness or method-selection guarantees.
  - Bind the recursive unary Integer parameter directly to its Bytecode DSL
    local using `ExperimentalLongArgument0` and `StoreLocal`; move the
    experimental static-unary argument to the usual B5 supplied-argument
    offset. The private frame representation still is not equivalent to
    an ordinary B5+ invocation.
  - Make the admitted `ExperimentalIntegerGreaterBool` long-only and perform
    direct wrapping signed-64 subtraction and multiplication without the
    earlier exact overflow promotion or generic Integer fallback.
  - Initialize the static direct-call target once in the first entered
    Context, without a per-invocation Context guard. A call from a different
    Context may retain the original target, and the direct path no longer
    translates a bridged control transfer.
  - Update the historical factorial FULL DSL expectations: 3 physical roots;
    module 17, run 6 and factorial 19 instructions, total 42. The original
    overflow-to-large-Integer regression test is explicitly `@Disabled` in
    this historical commit. These are source-test expectations, not results
    measured by this versioned replay.
  - **Historical semantic divergence:** signed-64 overflow wrapping conflicts
    with exact Protos Integer semantics; the experimental recursive call also
    bypasses dynamic `Object.call` selection, caller/Task/ReturnHome context,
    and some cross-Context/control-transfer guarantees. Earlier D179 C0
    module-binding lexical-retargeting divergence is not resolved. No
    semantic, suspension, or performance acceptance gate is established.
  - Reconstructed from original I115 commit
    `29d4f0e6e2089e901c164d886d31d83645ff4545`.
    No behavioral tests were run on this versioned historical replay.

## 0.4.14-SNAPSHOT

- `I115-H2` (PLAT066-C / experimental recursive factorial): restore the
  narrow compact recursive factorial lowering on top of the H1-FIX ordinary
  dispatch and frame-lexical-authority fallbacks.
  - Recognize the selected unary Integer recursive Closure and reintroduce
    the experimental `InvokeStaticClosureOneTail` execution lane with the
    reduced static unary argument layout; other roots keep the H1 ordinary
    B5+ invocation and selection paths.
  - Re-enable compact signed-64 arithmetic/comparison operations and exact
    overflow promotion in this admitted lane; leave numeric protocol lookup
    and dynamic selection to ordinary paths outside its strict admission.
  - Restore structural assertions for `factorial(20)` and its historical
    Bytecode DSL census: three roots, module 17, factorial 18, run 6,
    and 41 instructions in total. These are test expectations from the
    original I115 commit, not measurements on this versioned replay.
  - **Historical semantic limitation:** the reduced two-slot static unary
    ABI is not an equivalent replacement for B5+; the experimental recursive
    lane deliberately does not preserve every dynamic `Object.call` override,
    module-binding retargeting, or D179 C0 late lexical-selection guarantee.
    This snapshot does not establish full Protos semantic conformance or a
    general optimization acceptance gate.
  - Reconstructed from original I115 commit
    `a49cab249116bd59680ad50b3ec9ea192726dbda`.
    No behavioral tests were run on this versioned historical replay.

## 0.4.13-SNAPSHOT

- `I115-H1-FIX` (restore ordinary dispatch and frame lexical authority in compact lowering):
  retire the experimental reduced static-unary argument layout and the
  `InvokeStaticClosureOneTail` bypass for locally bound Closures. Preserve
  ordinary B5+ call arguments and `InvokeClosureOneTail`, allowing the
  standard `Object.call` selection and its override/fallback behavior.
  - Remove the static-local-Closure-call canonical admission and associated
    experimental recursive/static-call paths, while keeping the admitted
    compact lowering paths that remain eligible after the correction.
  - Use shared guarded primitive-numeric operations with ordinary dispatch
    on a numeric miss rather than the dedicated experimental Integer path;
    retain exact numeric services, overflow handling and resumable fallback.
  - Restore frame lexical-authority promotion for compact roots with proven
    declaration order, and reconcile parameter/frame-local admission.
  - Update structural and behavior tests for ordinary terminal call selection,
    numeric fallback and compact frame authority.
  - **Historical limitation:** these repairs address specific invalid
    experimental assumptions but do not establish full Protos conformance.
    The remaining compact and module-global specializations, including the
    prior `ReadGlobal` versus D179 C0 issue, need independent acceptance.
  - Reconstructed from original I115 commit
    `1c8c57f0eb75534fa004ec37d8ab6799d2b05dca`.
    No behavioral tests were run on this versioned historical replay.

## 0.4.12-SNAPSHOT

- `I115` (post-rebase Boolean control selector reconciliation): restrict
  experimental compact Boolean-control admission to `ifTrue`, `ifFalse`
  and `ifTrueIfFalse`; the `if` and `else` selector forms no longer enter
  this specialized branch.
  - Remove the shortcut that classified receiver-selected control results
    unconditionally as BOOLEAN. Preserve the existing branch-result join
    classification instead, avoiding an unjustified compact type proof.
  - **Historical limitation:** this is a narrowly scoped correction to the
    experimental I115 compact lowerer. It does not establish a complete
    override, method-selection, suspension or semantic-conformance gate.
  - Reconstructed from original I115 commit `8b839b40e22087187be18922d5e13becd05b7c6a`.
    No behavioral tests were run on this versioned historical replay.

## 0.4.11-SNAPSHOT

- `I115-R6` (experimental GraalPy-style module-global reads): introduce the
  `ReadGlobal` Bytecode DSL operation for lexically resolved ROOT/module
  bindings; read the live module authority directly, without materializing
  the caller or emitting captured-owner selection and conditional fallback.
  - Keep module presence, mutation and `PRESENT(null)` observable; only an
    ABSENT module binding consults outer scopes such as the Prelude through
    a cold fallback. Do not fall back to the receiver.
  - Reject inline-callback admission for module-owned reads and leave
    other captured reads on their existing owner-selection paths.
  - Extend capture, structural and factorial tests. The original I115-R6
    report records a reduction of factorial `run()` FULL DSL instructions
    from 17 (R5) to 6 (R6); this is historical evidence, not a measurement
    made on this versioned replay.
  - **Historical semantic divergence:** the statically resolved module read
    ignores a nearer binding created later, contrary to D179 C0's late
    lexical-retargeting behavior. The added design note is non-normative,
    grants no approval for changing `spec/`, and does not establish complete
    Protos semantic equivalence or a final performance gate.
  - Reconstructed from original I115 commit
    `48db4247d1d63e31a75fe908fe731b13536a0009`. No behavioral tests were
    run on this versioned historical replay.

## 0.4.10-SNAPSHOT

- `I115-R4` (experimental recursive factorial / Tier 1 graph evidence):
  extend compact Bytecode DSL lowering to selected unary Integer Closure roots
  and statically recognized recursive self-calls, retaining the signed-64
  `long` carrier model and using the existing static unary call path.
  - Register recursive module Closure bindings before nested root lowering
    and publish pending execution-plan cells early enough for recursive
    references to use the existing `InvokeStaticClosureOneTail` operation.
  - Admit certain module-level sequences consisting of named Closure
    creations to the frame-native authority path, and collapse immediate
    enclosing-scope captured reads into `ReadCapturedFrameLocalAtRoot`.
  - Add a deterministic `factorial(20)` correctness and Bytecode DSL census
    fixture, optional Tier 1 compiler-graph capture, and update captured-read
    structural oracles to recognize the revised lowering shape.
  - **Historical semantic limitation:** the experimental static recursive
    admission intentionally does not preserve every dynamic Object.call
    selection, module-binding retargeting, or dynamic presence guarantee.
    Physical instruction counts and opt-in compiler graphs are evidence,
    not a complete semantic or performance acceptance gate.
  - Reconstructed from original I115 commit
    `ce5d7af4a73fa630db7f526bb456b8115707385f`.
    No behavioral tests have been run on the versioned historical replay.

## 0.4.9-SNAPSHOT

- `I115` (host-to-guest Call0/Call1 fast entry): streamline the admitted
  non-suspending Polyglot `Value.execute()` path and extend static terminal
  `Call1` non-suspension evidence to a narrowly proved local-Closure pattern.
  - Refine effective-root non-suspension for a static terminal one-argument
    Closure call only when the callee body and supplied argument satisfy
    structural restrictions; retain UNKNOWN for unproved calls.
  - Cache host-entry eligibility for Closures with unobservable invocation
    `ReturnHome`, use direct compact Call0/Call1 B5 frame preparation and
    admit eligible host arguments without an extra supplied-argument array.
    The frame itself is still allocated, and fallback preserves normal
    conversion, call handling and suspension machinery.
  - Expand host-entry/Future tests for the static proof, argument provenance,
    fast and fallback routes, and correctness of eligible calls.
  - **Historical semantic limitation:** this optimization continues the
    temporary non-atomic RootActor admission bypass introduced in 0.4.8.
    Non-suspension alone does not establish concurrent-entry, reentrancy or
    termination equivalence, nor does the narrow static proof establish
    complete language conformance.
  - Reconstructed from original I115 commit
    `a0372478c1bad7faa22fbb6871324f451f0f3972`. No behavioral tests
    were run on this versioned historical replay.

## 0.4.8-SNAPSHOT

- `I115` (host entry / Call0): introduce a fast host-to-guest execution path for
  extracted source-backed Closures in standard Polyglot embedding when their
  effective root is proven non-suspending. Cache the proof at adapter creation
  and invoke the prepared guest call through its direct target.
  - For eligible `Value.execute()` calls, avoid conventional per-call RootActor
    admission and host-entry extent management; preserve argument conversion,
    owning-Context checks, B5 call preparation and return projection. Keep the
    ordinary admission/suspension path for unproven or ineligible cases.
  - Reject an unexpected pending continuation as a violated non-suspension
    precondition; retain fatal-error handling on the fast route. Extend
    host-entry/Future and canonical-callable interop test coverage.
  - **Historical semantic limitation:** the fast route deliberately omits
    exclusive RootActor admission. Its non-atomic owner/liveness check allows
    concurrency, reentrancy and termination races; non-suspension is not an
    equivalence proof for Actor isolation. The new design note
    `docs/design/I115_HOST_ENTRY_TEMPORARY_DEVIATION.md` is expressly
    non-normative. This snapshot does not establish full semantic conformance.
  - Reconstructed from original I115 commit
    `1510fdb7ccc8384da89eb2b534c046e3ab867df1`. No behavioral tests
    have been run on this versioned replay.

## 0.4.7-SNAPSHOT

- `I115-R` (GraalPy-inspired caller handling / PERF038-I / PLAT066-C):
  introduce `ProtosCallerFrameReference` for admitted terminal calls, preserving
  the exact caller's B5 header and optionally transporting its effective Prelude
  without eagerly publishing a `ProtosActivation`.
  - Record per-root caller needs (`NEEDS_EXACT_ACTIVATION` and
    `NEEDS_CALLER_PRELUDE`) with monotonic, invalidatable assumptions so
    subsequent calls prepare only requested information; retain validating
    general accessors for unadmitted and malformed representations.
  - Extend the terminal-call specialization to eligible one-argument method
    sends through `InvokeMethodOneTail`, preserving selection and fallbacks;
    refine compact-root non-suspension and captured lexical access paths.
  - Use exact Integer services on compact arithmetic overflow and results
    outside signed-64 range; this repairs numerical overflow handling but
    does not establish general user-defined method-selection equivalence.
  - Include terminal-call, deferred-caller, compact-frame and compiler-graph
    tests, plus the non-normative GraalPy caller-optimization report.
  - **Historical limitation:** this intermediate implementation retains
    experimental compact and caller paths. Its design report does not grant
    normative approval to all proposed relaxations; a complete semantic,
    suspension and performance gate is not asserted for this replay.
  - Reconstructed from original I115 commit
    `d9b803ca4299d4195964e5bcbd56e43d929b858a`.
    No behavioral tests have been run on the versioned replay.

## 0.4.6-SNAPSHOT

- `I115-Q` (PLAT066-C / PERF039) extends the experimental compact Bytecode DSL lowering for literal Integer returns and constant Boolean control.
  - Return an eligible signed-64 Integer terminal literal as a prepared guest `ProtosIntegerValue` constant, without compact return-carrier projection; keep the prior representation for unadmitted expressions and other terminal families.
  - Admit `whileTrue` conditions whose complete body has a proven Boolean final value, not only an Integer comparison (`I115-P`). Preserve the condition's preceding expressions on every iteration and use a direct primitive Boolean for `true`/`false` literals in structured control.
  - Add I115-P Boolean-while and I115-Q literal-return regression/structural tests, plus an opt-in compiler-graph capture oracle. Physical instruction censuses and opt-in captures are measurements, not proof of a general optimization or semantics gate.
  - **Historical semantic limitation:** this is an experimental compact subset inherited from I115-M/N. It does not establish full protocol-override behavior, arbitrary method selection, suspension, capture/ReturnHome equivalence, or complete Protos semantic conformance.
  - Reconstructed from original I115 commit `cfde3264318a7fab8b9df1a31f448c626210e553`. No behavioral tests have been run on this versioned replay.

## 0.4.5-SNAPSHOT

- `I115-O` (PLAT066-C / PERF041): optimize certified constant Integer
  returns in experimental compact Bytecode DSL roots.
  - Admit a returned root-local binding only when its terminal use is a
    lookup and all other stores to it are eligible literal statements
    (signed-64 Integer, `null`, `true` or `false`), with at least one
    Integer store and no other read of that binding.
  - Store prepared guest Integer constants directly at eligible write sites
    and skip the compact return-carrier projection for the proven return.
    Retain the previous lowering and return handling for unadmitted roots.
  - Add constant-provenance, conditional-assignment, and physical Bytecode
    census coverage, including the PERF041 variable-`ifTrue` case.
  - **Historical semantic limitation:** this was an experimental
    specialization layered on the I115-M/N compact path, not proof of
    general method-selection, override, suspension, or numerical-equivalence
    guarantees. Full language conformance was not established at this step.
  - Reconstructed from original I115 commit
    `9d41d119967a493e7236f7a0e3e76aeff2499fb9`;
    no behavioral tests were run on this versioned replay.

## 0.4.4-SNAPSHOT

- `I115-M/N` (PLAT066-C / PERF039 / PERF041): generalize the experimental
  compact Bytecode DSL lowering path for admitted root-local Integer operations,
  Boolean control and simple `whileTrue` loops.
  - Use specialized Integer operations and local Bytecode storage within the
    admitted compact roots, avoiding prepared message sends and persistent
    frame-backed lexical authority on those narrowly proven paths.
  - Lower admitted standard Boolean selectors (`ifTrue`, `ifFalse`,
    `ifTrueIfFalse`, `if`, `else`) to structured `IfThen`/`IfThenElse`
    operations. Require canonical Boolean conditions, never truthiness.
  - Add I115-M/N root and Boolean-control regression/structural tests and a
    PERF039 primitive-while census with opt-in real compiler-graph capture.
  - **Historical semantic limitation:** the experimental Integer operations
    do not generally preserve operator method selection, custom numeric
    methods or arbitrary-precision overflow promotion. The special Boolean
    lane is likewise not evidence of equivalence for arbitrary receiver
    overrides. This intermediate snapshot does not claim full Protos semantic
    conformance.
  - Reconstructed from original I115 commit `8de92642fa98eaa060fa39132fc97dfb472cc141`. No behavioral
    tests have been run on this versioned replay.

- `PERF041` (test-only, historical commit `e835fc1a5d068fd83da9413e53159d94d02f4c19`):
  add observational physical Bytecode DSL censuses for the primitive
  `true.ifTrue` and variable `value.ifTrue` workloads. Enumerate all
  source-group roots and instructions without filtering auxiliary roots,
  fallback paths or instrumentation. Verify the guest result but pin no
  static instruction-count budget. No behavioral tests were run during
  this historical replay; the Maven version remains `0.4.4-SNAPSHOT`.

## 0.4.3-SNAPSHOT

- `I115-H` (PLAT066-C): introduce an experimental static invocation path for a
  provably local Closure whose only use is a terminal one-argument call.
  - Use canonical identity and body-shape proofs to emit `InvokeStaticClosureOneTail`
    and a reduced unary call argument layout (`[marker, supplied0]`) instead of
    the normal Closure call preparation for the narrowly admitted case.
  - Add a lightweight non-capturing Closure representation when the body cannot
    observe its creator's `ReturnHome`; keep ordinary creation and fallback
    for cases outside the structural proof.
  - Add independent-builder and static-call structural/runtime tests that
    describe the admitted paths and their limitations.
  - **Historical semantic limitation:** this path was explicitly experimental
    and did not establish normative equivalence for dynamic D013 selection,
    Closure identity, creator context, caller/Task ownership or `ReturnHome`.
    It is not evidence of complete Protos semantic conformance.
  - Reconstructed from original I115 commit
    `7af12ea1c0c163da8de4bb745c6a245770914ba0`; no behavioral tests
    have been run on this versioned replay.

## 0.4.2-SNAPSHOT

- `I115-G` (PLAT066-C): classify whether each created Closure actually
  captures its creator's execution context. For a non-capturing Closure,
  reuse the creator's outer lexical environment instead of retaining its
  invocation's frame/activation; keep the capturing path for Closures that
  read, write or otherwise observe creator bindings.
  - Store eligible ordinary Closure bindings in plain Bytecode DSL locals
    instead of installing persistent lexical frame authority. Preserve the
    distinct storage path for genuinely captured bindings.
  - Extend lexical capture, terminal-call budget, independent-builder and
    PERF042 identity-root structural tests for the compact local layout.
  - **Historical semantic limitation:** at this point the optimization was
    explicitly recorded as diverging from the unconditional creator-context
    capture required by `spec/semantics/CALLABLES.md`, pending a design/semantic
    reconciliation. This entry does not claim full conformance or acceptance.
  - Historical reconstruction of original commit
    `015f76f946f2857c25f278142d475f2daf61e496` from I115. No new
    behavioral tests have been run on this versioned replay.

## 0.4.1-SNAPSHOT

- `PERF042` (PLAT066-C / I115): lower eligible terminal calls of a lexically
  bound one-argument Closure through the physical `InvokeClosureOneTail`
  operation instead of copying the callee's body into the caller during
  Bytecode DSL lowering. The callee retains its separate CallTarget and
  GraalVM decides whether to inline between targets.
  - Keep other I096 inline candidates on their existing paths, including
    immediate Closure literals and C0/C2/N calls. Preserve dynamic D013
    call selection and fallback; this lowering is not a non-suspension proof.
  - Update physical-root structural tests, terminal-call budgets and the
    opt-in compiler graph capture oracle for the new lexical C1 topology.
  - Historical reconstruction of `c00e6e8e2c33` from the I115 development
    branch. This is an intermediate development snapshot, not a claim that
    the full Protos semantic conformance suite passed at this revision.

## 0.4.0-SNAPSHOT

- Start the 0.4.x implementation development series and archive the complete 0.3.x implementation history. This transition changes only version and changelog metadata; it does not change Protos runtime behavior or the language specification.
