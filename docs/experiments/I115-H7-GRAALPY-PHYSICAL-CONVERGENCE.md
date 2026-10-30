# I115-H7 — GraalPy physical convergence for `method-call` (EXPERIMENTAL)

Implementation record for the `I115` branch. This is not a research report
and not approved Protos semantics: every divergence listed here is an
owner-authorized experimental departure from the normative specification
(`spec/`), which is unchanged.

Workload: `run` over `receiver.identity(i)` inside a literal `whileTrue`
(expected result `499500`). Validation (DSL census, Tier 1/Tier 2 graphs and
timings) is pending human execution.

## Substitutions

| Protos mechanism replaced | GraalPy 25.4.4 mechanism reproduced | Files | Protos semantics potentially violated | Status |
|---|---|---|---|---|
| General lowering of a root that reads a module binding (captured-materialized reads, callback roots) | `emitNameGlobalOperation` → `ReadGlobal`; `ReadGlobalOrBuiltinNode` cached/generic split | `CanonicalToBytecodeLowerer.java`, `ProtosExperimentalReadGlobalNode.java` | Nearer-scope and receiver-slot selection of bare names; lexical authority of the reading activation | Implemented experimentally / pending validation |
| `whileTrue` send with callback Closures, prepared structured calls and published activations | `visit(StmtTy.While)`: `beginWhile`, condition, body, `endWhile` | `CanonicalToBytecodeLowerer.java` | Dynamic selection of `whileTrue` on the receiver Closure; callback Closure identity and lifetime | Implemented experimentally / pending validation |
| `PrepareSendOne` + prepared invocation (`EnterStraightLineSourceCall`, `CompleteClosureCall`, structured classification) | `emitCall` → `GetMethod` + `CallBinaryMethod`; `GetMethod.doFastPath` (PIC + fallback); `CallBinaryMethodNode.callPFunction` (DirectCallNode PIC, indirect, generic) | `CanonicalToBytecodeLowerer.java`, `ProtosExperimentalGetMethodNode.java`, `ProtosExperimentalCallBinaryMethodNode.java`, `ProtosFrameArguments.java`, `ProtosValueLookup.java` | `Object.call`/Context re-checks; ReturnHome lifecycle; caller authority promotion; suspension | Implemented experimentally / pending validation |
| `TryPrimitiveNumericAdd` → `IsResultKind(MISS)` → `PrepareSendOne` → … → `MaterializePrimitiveNumericCarrier` | `PyNumberAdd` / `PyNumberInPlaceAdd` over `PyNumberAddFastPathsBase` | `ProtosExperimentalNumberAddFastPathsBase.java`, `ProtosExperimentalNumberAddNode.java`, `ProtosExperimentalNumberInPlaceAddNode.java` | Prelude numeric-protocol admission; dispatch of `+` on overridden Integer/Float | Implemented experimentally / pending validation |
| `TryPrimitiveNumericLess` + `ExperimentalCanonicalBooleanCondition` in a `whileTrue` condition | `Lt` with primitive specializations and generic fallback, consumed as `boolean` | `ProtosExperimentalLessNode.java`, `CanonicalToBytecodeLowerer.java` | Canonical Boolean materialization of the comparison | Implemented experimentally / pending validation |
| `BindUnaryFrameParameter` prologue of a compact unary root | `CopyArguments` + `LoadLocal` | `ProtosBytecodeRootNode.java`, `CanonicalToBytecodeLowerer.java` | Presence/authority checks of the parameter binding on compact entry | Implemented experimentally / pending validation |
| Long carriers and guest Integers inside compact roots | `boxingEliminationTypes = {int.class}` with int results | `ProtosNumericValueSupport.java`, `ProtosBytecodeRootNode.java`, `ProtosHostExecutableClosure.java` | Single Integer family representation (int vs long vs guest Integer) | Implemented experimentally / pending validation |

## Recorded effects

- **Dynamic selection of `whileTrue`.** A literal `whileTrue` in an admitted
  root is a Bytecode `While`; the receiver Closure never receives the
  `whileTrue` message, so an override of `whileTrue` is not observed.
- **Closure identity and lifetime.** The condition and body Closures are never
  created; no identity, capture or lifetime exists for them.
- **Captures and lexical authority.** Bindings assigned by the callbacks are
  the physical root's `BytecodeLocal`s. The generic tiers of `CallBinaryMethod`,
  `NumberAdd`, `NumberInPlaceAdd` and `Lt` publish the caller's activation
  but do not promote its frame locals to its lexical authority, so a method
  selected there does not observe them.
- **Rebinding of globals and methods.** `ReadGlobal` reads the current value of
  the module binding at its frame-backed position, guarded by module scope
  identity (limit 3); rebinding is observed. `GetMethod` reads the current
  value of the cached slot location under the selection stability
  Assumption; a slot reassignment is observed, a removal/recreation
  invalidates. The selection Prelude is read at installation and not guarded.
  A selection that is not a Closure or not admitted is resent ordinarily by
  `CallBinaryMethod` (selection is repeated there).
- **Home and Prelude.** The method home is the cached selection's owner. The
  caller prelude is transported only when the callee registered
  `NEEDS_CALLER_PRELUDE`, without the caller-side need registration of
  `InvokeMethodOneTail`.
- **ReturnHome.** `CallBinaryMethod` always passes the primitive-accepting
  unobservable marker; the materialized ReturnHome lifecycle of the selected
  Closure is not established.
- **Continuations and suspension.** The direct and indirect tiers admit only
  callee roots proven non-suspending. A suspended outcome of the generic
  route is an internal error (`IllegalStateException`), not a continuation.
- **Numeric dispatch.** `NumberAdd`, `NumberInPlaceAdd` and `Lt` apply the
  standard numeric operation to primitive operands without the Prelude
  admission certificate. Only non-primitive operand pairs reach the ordinary
  send. Protos has no in-place `+` protocol: `NumberInPlaceAdd` is a physical
  form of the ordinary `+`.
- **`int` versus `long`.** Signed-32 literals are int carriers in roots without
  a pre-H7 dispatching site. `int + int` overflow rewrites to a `long`
  result; mixed int/long pairs use the generic route (no implicit cast in
  this type system, unlike GraalPy). Int carriers become guest Integers only
  at the return epilogue (unless the caller accepts primitives and the root
  entered through `CopyArguments`), in `guestValue`, and at the Polyglot host
  entry.
- **Truthiness versus canonical Boolean.** `Lt` produces a Java `boolean`; a
  non-primitive comparison result must be canonical `true`/`false` or the
  standard whileTrue condition Error is signalled.
- **Activation materialization.** No activation is published on the direct
  paths; `CopyArguments` writes the parameter local directly.
- **Instrumentation and stack traces.** The callbacks have no RootTag and no
  frames. The method send keeps one `CallTag` region; `NumberAdd`/`Lt` keep
  only their source sections. Stack traces show `run` and the selected method
  root only.

## Remaining physical differences from GraalPy

- The object model is not `DynamicObject`/`Shape`: the `GetMethod` guard is
  receiver identity plus the slot-selection Assumption, and the read is the
  cached `SlotCell` location, not a `Shape`-indexed property location.
- The B5+ frame header (five slots) differs from `PArguments`.
- `ReadGlobal` reads the module binding through its frame-backed authority,
  not through a globals dictionary/`Shape` property.
- `GetMethod` allocates a small descriptor per execution (expected to be
  removed by escape analysis; not measured).

## I115-H7-R1 — Regression corrections

Status: edited, pending human validation. Nothing below is claimed to pass,
and no Tier 1/Tier 2 convergence is claimed.

### Regressions corrected in code

| Regression | Cause | Correction | Files |
|---|---|---|---|
| Caller-prelude need not registered by a compact send (`ProtosI115BDeferredCallerTest` prelude tests) | `GetMethod` read the prelude through the general walker, which never registers `NEEDS_CALLER_PRELUDE` on the reading root | `GetMethod` reads the caller reference and prelude exactly as every other send selection of a compact root (`InvokeClosureOneTail.callerReference` + `admittedCallerPrelude`), registering the need once; the prelude is also a PIC guard | `ProtosExperimentalGetMethodNode.java` |
| Callee caller slot built outside the B5 producer contract | `CallBinaryMethod` decided prelude transport itself, without the caller's read prelude | The `Method` descriptor carries the caller reference and the prelude `GetMethod` read; `CallBinaryMethod` builds the slot with `InvokeMethodOneTail.callerArgument` | `ProtosExperimentalGetMethodNode.java`, `ProtosExperimentalCallBinaryMethodNode.java` |
| Exact caller published without `NEEDS_EXACT_ACTIVATION` | The generic routes published through `ProtosFrameArguments.activation` | Every generic route (method send, `NumberAdd`, `NumberInPlaceAdd`, `Lt`, overflow promotion) resolves the caller through `InvokeClosureOneTail.exactCaller` / `admittedCallerPrelude` on its own root | `ProtosExperimentalCallBinaryMethodNode.java`, `ProtosExperimentalNumberAddFastPathsBase.java`, `ProtosExperimentalNumberAddNode.java`, `ProtosExperimentalNumberInPlaceAddNode.java`, `ProtosExperimentalLessNode.java` |
| Compact roots with an H7 send published the non-suspension refinement (pre-H7 such roots, having a numeric send, did not) | `dispatches` no longer counted H7 sites | New compilation fact `invokes`; a compact root publishes the refinement only without dispatching sites and without H7 sends. Suspected cause of `terminalFallbackRegistersTheExactActivationNeedOnce` (not confirmed by execution) | `CanonicalToBytecodeLowerer.java` |
| A suspended generic send raised `IllegalStateException` | No transport in the compact root | A terminal method send (`CallBinaryMethod` constant `terminal`) returns the existing terminal transport, and the root withdraws its non-suspension proof (`currentRootEmittedTerminalInvocation`). A non-terminal site abandons the suspension and signals the standard `Error` (see limitations) | `ProtosExperimentalCallBinaryMethodNode.java`, `CanonicalToBytecodeLowerer.java` |
| A non-Boolean `<` result in a `whileTrue` condition | — (already an Error in H7; now explicit) | `Lt` validates exactly canonical `true`/`false` (`canonicalCondition`) and signals the standard `Error` at the exact caller | `ProtosExperimentalLessNode.java` |

### Contracts protected by tests

- `ProtosI115BDeferredCallerTest`: unchanged.
- `ProtosI115H7MethodCallConvergenceTest`: the exact `run` and `identity` DSL,
  root census by physical identity (`run` from the session, `identity` from
  the selected Closure's own execution plan, exactly one remaining semantic
  root, all in the same `BytecodeRootNodes`), evaluation order, one fixture
  per operation, int/long/large exactness at the host, non-Boolean `<`
  Error without running the body, zero-iteration condition side effects,
  PIC monomorphic/beyond-limit, redefinition of a hot method, and
  caller-prelude registration/convergence through `GetMethod`.

### Bytecode DSL expectations changed, with the physical reason

| Test | Old expectation | New expectation | Physical reason |
|---|---|---|---|
| H7 `runIsOneDirectCompactRoot` | `NumberAdd` present | exact sequence with two `NumberInPlaceAdd` | `sink = sink + …` and `i = i + 1` both store the left operand's binding |
| `ProtosI115MCompactLoweringTest` census | ≥1 `TryPrimitiveNumeric` | exact `NumberAdd`/`NumberInPlaceAdd`/`Lt`/legacy counts per case; no expanded MISS path or authority promotion without a legacy site | H7 sites keep their generic route inside the node |
| `ProtosI115OConstantIntegerReturnTest` arithmetic cases | long carriers, `Experimental` operation | `NumberAdd`, exact int carriers, no long carriers, no guest constants, return projection kept | signed-32 literals are int carriers in non-dispatching roots |
| `ProtosI115OConstantIntegerReturnTest.valuePositionStoreIsNotAdmitted` | long carriers | int carriers `[1, 2]`; value of the assignment checked for both branches through `r` and `s` | same representation change; semantics unchanged |
| `ProtosI115PCompactBooleanWhileTest` comparison cases | `TryPrimitiveNumericLess` + `ExperimentalCanonicalBooleanCondition` | exactly one `Lt` immediately consumed by `branch.false`; `Lt`'s validation checked directly | the condition comparison is `Lt` |
| `ProtosI115QCompactLiteralReturnTest` arithmetic/comparison/prefix | `TryPrimitiveNumeric*`, conversion | exact `NumberAdd` root; `Lt` → `branch.false`; prefix `NumberInPlaceAdd` before the exit branch | as above |

### Differences from GraalPy still open

- No `DynamicObject`/`Shape`: `GetMethod` guards receiver identity, caller
  prelude and the slot-selection Assumption.
- B5+ header instead of `PArguments`; the caller prelude is read per
  `GetMethod` execution (GraalPy has no equivalent read).
- No implicit `int → long` cast: mixed pairs use the generic route.
- `GetMethod` allocates a `Method` descriptor per execution (escape analysis
  not verified).
- Callees whose body contains an H7 send are not proven non-suspending, so
  `CallBinaryMethod` reaches them through the generic route (as pre-H7 fused
  sends did); only send-free callees such as `identity` use the
  `DirectCallNode` tier.

### Limitations not corrected

- **Suspension at a non-terminal compact send** is not supported: the
  outcome is abandoned and the standard `Error` is signalled. Preserving it
  needs a continuation in the compact root (a cold `Yield` path), which
  this slice does not add. Owner decision required.
- `int` results returned to any caller whose header carries the
  primitive-accepting ReturnHome marker; non-H7 consumers that receive one
  project it through `guestValue`.
- The divergences recorded above (Prelude numeric admission, `whileTrue`
  selection, callback identity, ReturnHome lifecycle) are unchanged.

## I115-H7-R3

Physical convergence of `primitive-method-call` with GraalPy 25.4.4.1.1
(`ReadGlobalOrBuiltinNode.readGlobalFastPath`, `GetMethod`,
`CallBinaryMethodNode.callPFunction`, `PBytecodeDSLRootNode.CopyArguments`,
`RootNodeCompiler.emitCall`/`emitGetMethod`). Starting point: I115-H7-R2
(`521202f5`), whose owner measurement was After TruffleTier 417/329 and
Final IR 866/714 (Tier 1/Tier 2), with five Tier 2 invokes. No new capture
has been taken for R3: the R3 changes are not yet backed by any measured node,
invoke or timing difference.

### Changes

- `ProtosFrameLexicalBindingAuthority`: new
  `experimentalGlobalReadLocationOrNull(name, ordinal)` and nested
  `ExperimentalGlobalReadLocation` (retained `MaterializedFrame`, declaring
  `BytecodeRootNode`, selected `BytecodeNode`, its bytecode index, physical
  local offset, ordinal). Name, ordinal and presence are checked only at
  installation. `isCurrent()` is the single guard (the root still runs the
  selected node). `read()` calls `BytecodeNode.getLocalValue` on the original
  frame with no `@TruffleBoundary`. The general accessors are unchanged.
- `ProtosExperimentalReadGlobalNode.readCached`: guarded by module identity
  and `cachedLocation.isCurrent()`, returns `cachedLocation.read()`, and is
  `@ForceQuickening`. The new `locationOrNull` installs the location; with no
  location the specialization is not installed. The specialized body no
  longer calls `readOwnerFrameBindingOrNull`, `resolveInitial`,
  `hasFrameBackedBindingAt`, `readFrameBackedBindingAt` or
  `lexicalBindingAuthorityForRuntime` (`resolveDeferredOwner`). Those calls
  remain only in `readGeneric`/`resolveInitial`.
- `ProtosExperimentalGetMethodNode`: the `Method` record is removed. `doCached`
  (`@ForceQuickening`) is guarded by receiver identity and the selection
  Assumption and returns `cachedSelection.currentValue()`. It reads neither
  `callerReference` nor `callerPrelude`, and the `prelude == cachedPrelude`
  guard is gone. `selectOrNull` installs with a `null` prelude.
  `doGeneric`/`selectGeneric` return the selected value, or `null` when no
  slot is selected.
- `CanonicalToBytecodeLowerer.emitExperimentalCompactMethodSend`:
  `Block { sv = BindStackValue(receiver); CallBinaryMethod[selector, terminal](GetMethod[selector](LoadStackValue sv), LoadStackValue sv, argument) }`.
  The receiver and the argument are each evaluated once. The builder emits
  the first reload, which directly follows the bind, as `dup`, and the
  Block's cleanup emits one `store.stackvalue`. The instruction sequence is
  `ReadGlobal, dup, GetMethod, load.stackvalue, <arg>, CallBinaryMethod, store.stackvalue`.
- `ProtosExperimentalCallBinaryMethodNode`: the operands are
  `(callable, receiver, argument)`, with constant `selector` and `terminal`.
  `doDirect` (`@ForceQuickening`) is keyed by Closure identity and builds the
  header with a `null` method home. Its caller slot comes from
  `callerArgumentIfNeeded`, which returns `null` unless the PE-constant callee
  root registered `NEEDS_CALLER_PRELUDE` (then it uses
  `admittedCallerPrelude` and `InvokeMethodOneTail.callerArgument`) or
  `NEEDS_EXACT_ACTIVATION` (then it passes the caller reference). The
  `ProtosBytecodeControlTransferException` bridge is removed from `doDirect`
  and kept in `doIndirect`. `admittedTargetOrNull` additionally requires
  `isExperimentalPrimitiveArgumentRoot()`. `doIndirect` and `doGeneric` send
  ordinarily with the constant selector. `selects`, `closureOrNull`,
  `acceptsPrimitiveArguments` and the `Method`-based `callerArgument` are
  removed.
- `ProtosFrameArguments.experimentalMethodCallOne`: documented as accepting a
  `null` home and caller. It has no new checks and no calls to the
  caller-resolution functions.
- `ProtosBytecodeRootNode.CopyArguments`: a single `copy` specialization
  stores `arguments[SUPPLIED_ARGUMENT_OFFSET]` with
  `LocalRangeAccessor.setObject`. The `hasSuppliedArgument0` guard and the
  `authoritative` (`bindUnaryFrameParameterAuthoritatively`) fallback are
  removed.
- `ProtosBytecodeRootNode.EpilogForReturn`: a root with
  `isExperimentalPrimitiveArgumentRoot()` (compilation-final) returns its
  value as is. There is no `acceptsPrimitiveResult` test and no
  `MaterializeReturnCarrier`. The trace probe is unchanged.

### Divergences (I115-H7-R3, owner-authorized)

- The cached method lookup is installed with a `null` Prelude and does not
  observe later Prelude changes.
- `methodHome` is omitted (`null`) for admitted compact callees.
- The caller is not transported (`null` caller slot) when the callee has
  registered no caller need. A callee that first needs it on that call
  cannot recover it.
- The per-call ReturnHome lifecycle is not established; the unobservable
  marker is always used.
- A control transfer out of the direct call is not translated in
  `doDirect`.
- `CopyArguments` does not recover from a wrong arity and does not inspect
  excess arguments.
- Internal carriers (for example an `int`) are returned directly by
  admitted compact roots to any caller.
- The specialized `ReadGlobal` execution skips the general location checks
  (presence, layout name): a removal of the global is not observed while
  the location is current.
- A root whose only Prelude-reading send is an H7 `GetMethod` no longer
  registers `NEEDS_CALLER_PRELUDE`. The assertions changed to this new
  contract are root D in
  `ProtosI115BDeferredCallerTest.realChainRegistersNeedsOnTheInvokedRootsAndConverges`,
  `ProtosI115BDeferredCallerTest.terminalSendRegistersTheCallerPreludeNeedOnceAndConverges`
  (which now registers the need explicitly to keep covering transport and
  convergence) and `inner` in
  `ProtosI115H7MethodCallConvergenceTest.inheritedPreludeSendRegistersAndConverges`.

### Tests changed

- `ProtosI115H7MethodCallConvergenceTest`: `RUN_DSL` and the `sendOne`
  sequence now include the stack-value instructions, and the
  evaluation-order test counts `dup`/`load.stackvalue`/`store.stackvalue`.
  `inheritedPreludeSendRegistersAndConverges` changed as described above. New
  tests: `getMethodReturnsTheSelectedValue`,
  `copyArgumentsStoresTheSingleArgumentWithoutFallback`,
  `identityIsCalledDirectlyWithoutCallerTransport`,
  `cachedGlobalLocationObservesRebinding` and
  `genericFallbackRemainsReachable`.
- `ProtosI115BDeferredCallerTest`: new
  `directMethodCallWithoutCalleeNeedsTransportsNoCaller`, and the two tests
  described above.

### Differences from GraalPy still open (not measured)

- `GetMethod` still guards receiver identity plus an Assumption rather than a
  `Shape`.
- The B5 header is a 6-slot array including the unobservable ReturnHome
  marker.
- `ReadGlobal` still computes the captured module from the B5 header
  (`capturedModuleOrNull`) for its identity guard.
- The receiver is projected with `guestValue` before the call.

### Results

Functional tests: pending human execution. Physical measurements: none
taken for R3.
