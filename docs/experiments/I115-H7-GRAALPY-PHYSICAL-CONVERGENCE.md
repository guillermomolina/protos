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
