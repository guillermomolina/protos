# I115 — GraalPy-inspired caller and B5 ABI optimizations

Status: Non-normative implementation report
Last updated: 2026-10-21
Scope: local branch `I115`, on top of the unpublished PERF038-I work

This report records how the I115 caller work adapts the on-demand caller
context model of GraalPy (and the call-site specialization of GraalJS) to the
admitted terminal call paths of Protos, which divergence it introduces, and
which semantic relaxations remain candidates only. Nothing here is normative:
`spec/` is unchanged, and no candidate below has normative approval. W2 alone
is authorized by the owner as a bounded experiment (section E); that is an
experimental authorization, not a normative approval. W1 has no approval of
either kind.

## A. Implementation references

| GraalPy / GraalJS | Protos | State |
|---|---|---|
| `PArguments` | `ProtosFrameArguments` | Implemented: the B5 header now has an explicit admitted surface (`admittedPreludeOrNull`, `admittedCompactTask`, `resolveAdmittedCaller`) that reads slots 0–4 directly, beside the general validating accessors. |
| `PFrame.Reference` | `ProtosCallerFrameReference` in B5 slot 3, beside the published activation and the raw deferred B5 array | Implemented (section F): the caller's own B5 array plus the information its callee requested (today the prelude). It never holds or builds an activation; exact identity is published through the referenced header. |
| `CallerFlagsAssumptionSet` | Per-root caller-needs state (`ProtosBytecodeRootNode.CallerNeeds`) | Implemented for two needs: `NEEDS_EXACT_ACTIVATION` and `NEEDS_CALLER_PRELUDE`. Monotonic `@CompilationFinal` bit set, invalidatable `Assumption`, one invalidation per new need, per root (and so per Context). |
| `CallContext` | Caller preparation before the invocation (`InvokeClosureOneTail.callerArgument`, `InvokeMethodOneTail.callerArgument`) | Implemented for the admitted terminal Call1/Send1: the producer reads the cached callee root's needs; without `NEEDS_CALLER_PRELUDE` nothing is prepared, with it a `ProtosCallerFrameReference` transports the caller's prelude. |
| `CalleeContext` | Entry and deferred activation publication | Implemented through the existing `publishDeferredCaller`; publication is now requested through the root's exact-activation need (`InvokeClosureOneTail.exactCaller`, `CurrentActivation`). |
| `ReadFrameNode` | Caller/Prelude/Task reads | Implemented: `callerPreludeOrNull` answers from a caller activation or a transported prelude and walks nothing; an untransported inherited prelude is recovered by the `@TruffleBoundary` `recoverCallerPreludeOrNull` (validating general walk) and registers `NEEDS_CALLER_PRELUDE`. `admittedCompactTask` is unchanged apart from following references. |
| `JSFunctionCallNode` | `InvokeMethodOneTail`, `InvokeClosureOneTail` | Implemented: both share `callerReference`, `admittedCallerPrelude` and `exactCaller`; the method hit reads the Prelude through the admitted accessor and writes the six Call1 slots directly. |
| `DirectCallNode` | Call to the cached admitted target | Unchanged (already used by both direct tiers). |
| `@InliningCutoff` | Fallback separation | Implemented on the unadmitted Prelude walk and on the unadmitted Task walk (the deep Prelude walk is now a `@TruffleBoundary`, section E); already present on the terminal `generic`/`rejectedSource` fallbacks. |
| `@TruffleBoundary` | Exceptional materialization/recovery | Kept on `publishDeferredCaller` and `materializeCompactActivation`; `recoverCallerPreludeOrNull` replaces `deepCallerPreludeOrNull` and `unadmittedCallerPreludeOrNull` as the single Prelude recovery boundary. |

### Pending

- `NEEDS_TASK`, `NEEDS_CONTEXT`, `NEEDS_RETURN_HOME` and
  `NEEDS_DYNAMIC_CONTROL` are not defined. At the current HEAD no operation
  has both a request point for them and a consumer that could skip work
  because of them: Task, Context, ReturnHome and dynamic-control reads in
  root operations all take an already published `ProtosActivation` operand,
  so the only avoidable work is the publication itself, which
  `NEEDS_EXACT_ACTIVATION` covers. Defining them without a consumer was
  explicitly excluded by the task.
- Only the admitted terminal Call1/Send1 producers consult a callee root's
  needs; they are the only producers that place a still-compact caller in
  slot 3 (section F).
- No new graph measurement exists for this change (sections D and F).

## B. Protos semantic guarantees that could be relaxed (candidates)

These are candidates for owner decision, not approved normative changes.
W7 is a Protos-specific idea and is not attributed to GraalPy.

- **W1 — Full B5 structural validation.** Skip validation of every slot on
  paths whose producer is certified. Reference: `ProtosFrameArguments`, B5
  ABI contract. Risk: malformed internal headers can produce different
  errors.
- **W2 — Cycle detection on every admitted walk.** Skip cycle checks on each
  link when references come from internal producers. Risk: a corrupt chain
  could be detected late.
- **W3 — Early physical Context creation.** Keep frame and bindings virtual
  until explicitly observed. Reference: `spec/semantics/EXECUTION_AND_CONTROL.md`,
  execution contexts. Risk: `context` identity, mutations, escaped captures.
- **W4 — Full Task preservation on every call.** Carry only the authority the
  admitted target needs. Reference: `spec/concurrency/FUTURES_AND_TASKS.md`.
  Risk: inherited Task, cancellation, ownership of dynamic control.
- **W5 — Physical ReturnHome per invocation.** Drop it on paths whose proven
  target cannot use it. Reference: `spec/semantics/CALLABLES.md`, Return
  Semantics and Return from Escaped Closures. Risk: `^`, `InvalidReturn`,
  unwinding.
- **W6 — Complete receiver and methodHome.** Use reduced references when the
  body observes neither `this`, `super` nor method provenance. Reference:
  `spec/semantics/CALLABLES.md`, Closures and Methods / Closures and `super`.
  Risk: dynamic receiver, delegation, method extraction.
- **W7 — D013 protocol preservation in specialized calls.** Allow more
  aggressive static calls when a selection certificate proves there is no
  relevant `call` override. Risk: a `call` mutation during argument
  evaluation could change the selected call. Protos-specific.
- **W8 — Full physical activation identity.** Allow virtual representations
  while identity is unobservable. Reference: `spec/semantics/CALLABLES.md`,
  `spec/semantics/EXECUTION_AND_CONTROL.md`. Risk: context identity,
  publication after suspension.
- **W9 — Uniform interpreter/compiled semantics.** Allow different physical
  paths and reduced internal validation under assumptions. Risk: different
  errors for invalid internal states. A purely physical difference (same
  guest-visible result, different internal shape) is not a semantic
  divergence; a different guest-visible error or value would be.
- **W10 — NORMAL instrumentation and reflection.** Omit debugging/inspection
  mechanisms NORMAL has not requested. Risk: observation through the
  debugger, DAP, `std:inspect`, late materialization.

## C. State of each divergence

| ID | Current contract | Proposed optimization | Implemented | Observable change | Tests affected | Approval state |
|---|---|---|---|---|---|---|
| W1 | General accessors validate every deferred link (`isDeferredCallerCandidate`, `hasValidKindSlots`) | Admitted accessors read slots by type test only | **Yes, partially**: `admittedPreludeOrNull`, `admittedCompactTask`, `resolveAdmittedCaller` (published case) and the internal `callerPreludeOrNull` skip structural validation of admitted links; since section F `callerPreludeOrNull` also answers a reference's transported prelude without validating the referenced header (the general accessors ignore the transported value and walk the real chain); shapes outside the admitted forms still take the validating walk (the Prelude walk through the `@TruffleBoundary` `recoverCallerPreludeOrNull`, the Task walk after a deoptimization). The skipped validations already exist physically | Not verified as unobservable. The admitted accessors only read headers built by internal producers, and no guest-reachable path producing a malformed header is known, but this has not been proven. For a malformed *internal* header whose slot 0 is an activation or Closure but whose kind/home slots are corrupt, the admitted accessors may answer instead of throwing `IllegalStateException` | None changed. General accessors (`compactTask`, `preludeOrNull`, `resolveExactCaller`, `compactCaller`, `activation`) keep their validation and the malformed/cycle tests are unchanged | **NOT APPROVED** — no normative approval; the potentially observable behavior with corrupt internal headers remains a divergence pending owner decision; no PLAT066 approval is claimed. W2's experimental authorization does not extend to W1 |
| W2 | Cycle detection on every walk | Skip it for internal chains | **No longer implemented** (section F): the three-link unrolled walk is removed; the admitted Prelude read walks no chain, and every walk that remains (recovery, general accessors, Task walks, publication) keeps validation and cycle detection | None for valid headers | Former W2 tests kept, docs updated; new coverage in section F | Retired as a hot-path optimization. The owner's experimental authorization is no longer exercised; no normative consolidation is requested |
| W3 | Context created per spec | Virtual until observed | No | — | — | CANDIDATE / NOT IMPLEMENTED |
| W4 | Full Task authority | Partial authority | No | — | — | CANDIDATE / NOT IMPLEMENTED |
| W5 | Physical ReturnHome | Omit for proven targets | No | — | — | CANDIDATE / NOT IMPLEMENTED |
| W6 | Full receiver/methodHome | Reduced references | No | — | — | CANDIDATE / NOT IMPLEMENTED |
| W7 | D013 selection after operands | Static calls under certificate | No | — | — | CANDIDATE / NOT IMPLEMENTED |
| W8 | Full activation identity | Virtual activation | No (already deferred by I115-B within the spec; unchanged) | — | — | CANDIDATE / NOT IMPLEMENTED |
| W9 | Uniform errors | Reduced validation under assumptions | No beyond the W1 internal-header case | — | — | CANDIDATE / NOT IMPLEMENTED |
| W10 | NORMAL instrumentation | Omit unrequested tooling | No | — | — | CANDIDATE / NOT IMPLEMENTED |
| W11 | A genuine execution context materialized before a root executes keeps authoritative bindings: creation and assignment go through its lexical authority with D179 presence and CLOSED/FROZEN restrictions (runtime contract `ProtosActivation.hasUnobservedFrameNativeExecutionContextForRuntime`) | I115-M compact roots keep every binding, assignments included, in Bytecode locals even when entered with a published activation whose Context already exists | **Yes** (I115-M) | The pre-existing Context receives no binding and is not mutated; a CLOSED or FROZEN pre-existing Context rejects no creation (section G) | `perf036aObservedActivationUpdatesAuthoritativeBinding` and `perf036aFrozenOrClosedEntryContextRejectsInitialCreation` assert the current frame-native behavior and label it as this divergence | **ACCEPTED BY THE OWNER AS AN EXPERIMENTAL D179 DIVERGENCE** — not normative, not attributed to PLAT067-B; `spec/` unchanged |

## D. Evidence before this change

### GraalPy reference for `primitive-method-call`

Earlier reference values, not results of this implementation:

| Metric | GraalPy |
|---|---:|
| Roots | 4 |
| Total DSL FULL | 61 |
| `identity` DSL FULL | 5 |
| `run` DSL FULL | 10 |
| Tier 1 `run` nodes | 169 |
| Tier 2 `run` nodes | 74 |

### Protos PERF038-I baseline

Baseline recorded by PERF038-I (not results of this implementation):

| Metric | Original baseline | Previous B5 experiment |
|---|---:|---:|
| Tier 1 After TruffleTier | 558 | 522 |
| Tier 2 After TruffleTier | 448 | 412 |
| Tier 2 Invoke | 0 | 1 |
| Tier 2 AllocatedObject | 3 | 7 |
| Tier 2 FixedGuard | 32 | 19 |

### Origin of the 59 nodes attributed to the B5 validators

Measured (earlier PERF038-I BGV diagnostic): 59 relevant nodes of the
compiled `run` graph were attributed to `isDeferredCallerCandidate`,
`callerPreludeOrNull` and `hasValidKindSlots`.

Interpretation (inferred from the PERF038-I source, not measured): on every `InvokeMethodOneTail` hit the caller-prelude guard called
`PrepareSendArguments.callerPrelude(caller)`, i.e. `preludeOrNull` of the run
root's own frame arguments. That path partially evaluated:

1. `compactClosure(arguments)` → `requireCompactCall` → `isCompactCall`,
   which evaluates `hasCompactHeader` twice (immediate-method and
   direct-call tests), each including `isCallerSlot` →
   `isDeferredCallerCandidate` → `hasValidKindSlots` on the caller slot:
   repeated length, `instanceof` and identity guards on slots 0–4 of the run
   header and of its deferred caller;
2. the PERF038-I one-level fast path of `callerPreludeOrNull`, which
   repeated `hasValidKindSlots` plus the slot 0/3/4 type tests of the
   deferred caller; and
3. the `@TruffleBoundary` call to `callerPreludeOrNullSlow`, which is the
   residual Tier 2 `Invoke` (0 → 1).

With I115 the hit uses `admittedPreludeOrNull`: one type test on slot 0, the
Closure's own prelude, and (only when it is absent) the admitted
`callerPreludeOrNull` cases 1–4; the general walker is reachable only after a
deoptimization, and no boundary remains on that path. (Superseded by section
E: the fixed path now covers three deferred links, and a longer valid chain
reaches the `@TruffleBoundary` `deepCallerPreludeOrNull`.) No reduction is claimed
until the graph capture of `ProtosPerf038ICompilationGraphTest` is rerun at
both tiers; its new `WARM_`/`COMPILED_` diagnostic lines report the
`InvokeMethodOneTail` count, guarded entries, fallback instantiation and the
run root's caller-materialization need, without asserting node counts.

## E. GraalPy-style Prelude fast path (W2)

### Physical baseline before this patch

| Measurement | Protos before patch | GraalPy 25.4 |
|---|---:|---:|
| `run` DSL FULL | 17 | 10 |
| Tier 1 After TruffleTier nodes | 359 | 169 |
| Tier 2 After TruffleTier nodes | 246 | 74 |
| Tier 2 Invoke nodes | 0 | Not measured |
| Tier 2 allocation-family nodes | 3 | Not measured |
| Tier 2 guards | 17 | Not measured |

Both Protos BGV captures (Tier 1 and Tier 2) match the corresponding
`TraceCompilation` node counts exactly.

The Tier 2 BGV attributes nine `FixedGuardNode`s to Prelude access and its
deferred walk, in `admittedPreludeOrNull`, `callerPreludeOrNull`,
`deepCallerPreludeOrNull` and `requireAcyclic`. `@InliningCutoff` on
`deepCallerPreludeOrNull` did not keep its checks out of `After TruffleTier`
(it only affects host inlining). This patch is not claimed to remove all nine
guards; only a new capture can tell.

### Tier 2 allocation-family entries

The three Tier 2 allocation-family nodes are:

- `BoxNode$AllocatingBoxNode`;
- `CommitAllocationNode`;
- `AllocatedObjectNode`.

The last two describe the single materialization of a `ProtosIntegerValue`
(the commit and the object it commits). The three entries are not three
independent heap objects.

### W2 — Admitted Prelude traversal relaxation

Status: **Retired by section F.** The text below records the experiment as
it was; the fixed-depth path, `deepCallerPreludeOrNull` and
`unadmittedCallerPreludeOrNull` no longer exist.

Former status: partially implemented in admitted accesses — owner-authorized
experiment — normative consolidation pending.

#### Original Protos guarantee

General resolution validated every deferred B5 representation, protected
every walk against cycles, and kept the Prelude inheritance semantics: a
published activation answers with its own prelude, a compact Closure with its
own prelude answers with it, and otherwise the prelude is inherited from the
caller.

#### Current implementation

- `admittedPreludeOrNull` answers, in order, the published activation in
  argument 0, the invoked Closure's own prelude, and otherwise the inherited
  prelude through `callerPreludeOrNull`. It publishes nothing and never calls
  `activation`, `resolveExactCaller` or `compactCaller`.
- `callerPreludeOrNull` is a fixed-depth, loop-free path: the caller
  reference, then up to three deferred links. Each link answers by its
  published activation, else its Closure's own prelude, else its own caller
  when that is an activation, else continues to the next link. Only length
  checks, type tests and slot reads run on it.
- A valid chain longer than the fixed links continues in
  `deepCallerPreludeOrNull`, a `@TruffleBoundary` that restarts from the
  origin with the general walker `generalCallerPreludeOrNull`: every link is
  validated by `isDeferredCallerCandidate`, the Floyd cursor of
  `requireAcyclic` rejects cycles of any length wherever they start, there is
  no depth limit, and nothing is published. It does not deoptimize, so a
  valid deep chain may take it repeatedly.
- A shape outside the admitted forms (not an `Object[]` of header length, or
  argument 0 neither activation nor Closure) still takes
  `unadmittedCallerPreludeOrNull`. The real fallback route is
  `unadmittedCallerPreludeOrNull` → `deepCallerPreludeOrNull` →
  `generalCallerPreludeOrNull`; the intermediate method is a
  `@TruffleBoundary` and requests no explicit transfer to the interpreter.
  Other fallbacks (`admittedPreludeOrNull` for a non-header shape, the
  unadmitted Task walk) keep their own transfers.
- The general accessors (`preludeOrNull`, `compactTask`,
  `resolveExactCaller`, `activation`, `compactCaller`) are unchanged.
- Both terminal operations (`InvokeClosureOneTail`, `InvokeMethodOneTail`)
  already read the caller prelude through `admittedCallerPrelude` →
  `admittedPreludeOrNull` and never resolve the exact caller on a hit; the
  root node was not changed by this patch.

#### Relaxed guarantee

The admitted path assumes headers built by authorized internal producers and
runs neither full structural validation nor cycle tracking on the links it
covers. This specific omission is within W2's experimental scope; it does not
authorize removing validation from any other walk, and it does not approve
W1 (general structural validation), which keeps no normative approval and
remains a divergence pending decision for corrupt internal headers. Valid
headers must keep Prelude identity, precedence and result, and the general
walk keeps validation and cycle detection.

#### Observable consequences

A malformed internal representation that wrongly enters the admitted path
may not raise the same error, or may raise it at a different point, than the
previous validated walk. Past the fixed links the general walker now
validates every link, so a malformed link there is rejected where the
previous unvalidated deep walk could have answered. A cycle within the fixed
links is still rejected, by the general walker after the fixed links.
No observable change is authorized for valid headers.

#### Coverage

In `ProtosI115BDeferredCallerTest`:

- Admitted accesses: `admittedPreludeAnswersPublishedCallersByIdentity`,
  `admittedPreludeKeepsOwnOverInheritedPrecedence`,
  `admittedPreludeFollowsFixedAndDeepChains` (depths 1, 2, 3 and 5, repeated,
  own prelude at the deepest link).
- General walks: `generalPreludeWalkerValidatesArbitraryChains` (short
  header, invalid argument 0, kind/Task, ReturnHome and caller slots, cycles
  of length 1, 2 and 3, a valid 64-link chain, and a malformed link past the
  fixed admitted links).
- Identity, Task and explicit publication:
  `preludeQueriesPreserveTaskAndExactCallerResolution`.
- Existing malformed-header and cycle tests are unchanged.

#### Remaining normative work

The authorization is experimental, not normative approval, and is not a
general ratification of the relaxed semantics; normative consolidation is
pending. No existing assertion was found to conflict with W2.

### GraalPy reference

Design correspondence with `oracle/graalpython` `release/graal-vm/25.4`
(`CallUnaryMethodNode`, `CallDispatchers.FunctionDirectInvokeNode`/
`SimpleDirectInvokeNode`, `ExecutionContext.CallContext`/
`PassCallerFrameNode`, `IndirectCallData.CallerFlagsAssumptionSet`):

- Specialized direct invocation: the terminal PIC entries call the cached
  target through a `DirectCallNode`.
- Minimal admitted-hit work: the hit reads the prelude through the fixed
  admitted path and builds the existing admitted B5 header.
- General dispatch outside the common path: the validating walk is behind a
  `@TruffleBoundary` (the Prelude walk) or a deoptimization (the Task walk).
- Caller-frame materialization only when demanded: publication stays behind
  `NEEDS_EXACT_ACTIVATION`.
- Assumption-based preservation of dynamic behavior: the existing guards and
  Assumptions are unchanged.

This is a structural correspondence only; the Protos implementation is not
semantically equivalent to Python.

### Post-patch measurements

**Not measured.** Validation, `TraceCompilation`, BGV and the Tier 1 and
Tier 2 captures are owner-executed.

## F. GraalPy 25.4 caller-header architecture

Reference: `oracle/graalpython`, branch `release/graal-vm/25.4`:
`PArguments`, `PFrame.Reference`, `CallerFlags`,
`IndirectCallData.CallerFlagsAssumptionSet`, `ExecutionContext.CallContext`
and `PassCallerFrameNode`, `CallDispatchers.SimpleDirectInvokeNode` and
`FunctionDirectInvokeNode`, `CallUnaryMethodNode`. The property adopted is
the one `ExecutionContext` documents: a caller passes no frame information
that its callee has not requested; when a callee discovers a need it
registers the flag, updates the Assumption, and later calls transport the
information.

### Mechanisms copied from GraalPy

- Callee-declared needs: a monotonic per-root bit set behind one
  invalidatable `Assumption` (`CallerNeeds`), invalidated once per new need.
- Need discovery at the read: the first read that finds the information
  missing registers the need (`CurrentActivation` with `callerReference`,
  and `InvokeClosureOneTail.admittedCallerPrelude` for the terminal send),
  then answers through a cold recovery path.
- Caller-side preparation consulting the callee's needs
  (`callerArgument`), compiled against the callee root's Assumption, with
  `DirectCallNode` unchanged and no preparation when nothing is requested.
- A lightweight caller reference (`ProtosCallerFrameReference`) carrying
  only requested information; the exact activation is materialized only
  when a consumer needs its identity.
- General recovery outside the compiled specializations
  (`recoverCallerPreludeOrNull`, `@TruffleBoundary`).

### Adaptations required by the B5 ABI

- The six GraalPy argument indices are not copied. B5 slots 0–4 keep their
  meaning; only the representations accepted in slot 3 grow: activation,
  raw deferred B5 array, `ProtosCallerFrameReference`, and `null` in
  published state only.
- The reference is built by the caller for the call, around the caller's
  own B5 array, instead of by the callee at entry. Exact identity is
  therefore still published into the referenced header's argument 0 by the
  existing `publishDeferredCaller`, so one invocation has one activation
  whatever number of references point to it.
- The four slot states are distinguished by type, never by `null`: a raw
  array means nothing was requested; a reference means the prelude was
  transported, with `null` a legitimate prelude value; published versus
  unpublished is read from the header's argument 0
  (`publishedActivationOrNull`).
- Only the admitted terminal Call1/Send1 producers can place a still-compact
  caller in slot 3, so they are the only producers that consult needs. Every
  ordinary producer passes the caller's `ProtosActivation`, which already
  answers every request in O(1); ordinary calls entering the same callee
  therefore need no new representation and prepare nothing new.
- A Closure with its own prelude never reads the inherited one, so its
  producer transports nothing even when its root has the need.
- Readers of slot 3 unwrap references through `deferredCallerOrNull`
  (`resolveExactCaller`, `resolveAdmittedCaller`, `resolveCallerSlot`,
  `isDeferredCallerCandidate`, `publishDeferredCaller`, `compactCaller`,
  `preludeOrNull`, `admittedPreludeOrNull`, `compactTask`,
  `admittedCompactTask`, `isCallerSlot`), so a reference never bypasses the
  validation of the header it designates in the general path.

### Unavoidable semantic differences between Python and Protos

- Protos has no Python frame object: the exact `ProtosActivation` is guest
  `context` identity and must be unique per invocation, so it is published
  in place instead of being created per request.
- Prelude, Task, dynamic control and ReturnHome have no Python counterpart.
  Only the prelude is transported; Task, Context, ReturnHome and dynamic
  control keep their existing readers (no speculative flags).
- Protos suspension, Task ownership and C-prime transport bound what any
  frame walk could cross; `TruffleRuntime.iterateFrames` is not used.

### What disappears from the hot path

The W2 fixed walk of up to three deferred links in `callerPreludeOrNull`.
After the callee root has `NEEDS_CALLER_PRELUDE`, an admitted read of an
inherited prelude is: one type test on the caller slot and one field read
of the reference. Before the need is registered (or for a producer that
cannot consult needs), the read is recovered behind a boundary by the
validating walker; that walk is never in a compiled hit that has the
information.

### State of W1 and W2

- W1: unchanged in kind and **NOT APPROVED**. The admitted Prelude read now
  trusts a reference's transported prelude without validating the
  referenced header, as it already trusted admitted headers. The general
  accessors never use a transported value: they follow the referenced
  header's real provenance with full validation and cycle detection. For
  corrupt internal headers behind a reference, only the admitted Prelude
  read may answer where a full walk throws; the general Prelude walk,
  recovery, resolution, Task and publication walks reject them.
- W2: retired as a hot-path optimization (section C); nothing relies on it.

### Guarantees kept

Prelude identity and precedence (published activation, then own prelude,
then inherited), one published activation per invocation, Task and
explicit-Task inheritance, ReturnHome slots, cycle and malformed-header
rejection in every remaining walk, PIC guards and selection Assumptions,
`DirectCallNode` direct tiers, the PROVEN/UNKNOWN entries and C-prime
continuity. A reference is immutable and is retained by the suspended
callee's frame arguments, so resumption reads the same provenance and
resolves the same single activation.

### Coverage

In `ProtosI115BDeferredCallerTest`:
`callerArgumentPreparesOnlyWhatTheCalleeRequested`,
`firstUntransportedPreludeReadRegistersTheNeedOnceAndRecovers`,
`transportedPreludeAnswersWithoutWalkingAndKeepsPrecedence`,
`referencesPublishOnceAndKeepTaskAndReturnHome`,
`deepReferenceChainResolvesOneActivationPerInvocation`,
`cyclicAndSelfReferencesAreRejectedWithoutPublication`,
`terminalSendRegistersTheCallerPreludeNeedOnceAndConverges`,
`transportedCallerSurvivesSuspensionAndResumption` (a registered need
across a real suspension; its guest caller is published, so no reference
is carried through it), `callerPreludeNeedPropagatesTransitivelyAndConverges`,
`callerNeedsAreIsolatedBetweenContexts`.
`ProtosPerf038ICompilationGraphTest` additionally prints
`CALLER_PRELUDE_NEED` beside `CALLER_MATERIALIZATION_NEED`.

### Measurements pending

Not measured. The canonical workload keeps `MODULE_DSL_FULL=52`,
`METHOD_DSL_FULL=5`, `RUN_DSL_FULL=17`, `TOTAL_DSL_FULL=74`. The W2
capture (`TIER1_NODES=484`, `TIER2_NODES=374`, `TIER2_FIXED_GUARDS=8`,
`TIER2_INVOKE_WITH_EXCEPTION=7`) is a reference only, not a result of this
change. No improvement is claimed before a new capture.

## G. W11 — compact roots entered with an existing Context

**Owner decision: I115-M is kept and the D179 divergence is accepted as
experimental.** The priority is a physical architecture close to GraalPy:
frame-native storage, deferred materialization and performance. The
restriction of compact admission to assignment-free roots (option 3) was
considered and cancelled. `spec/` is unchanged and nothing here is
normative.

### Ratified contract (unchanged)

- `EXECUTION_AND_CONTROL.md` §4: parameters and local slots belong to the
  invocation's execution context, which follows the ordinary structural
  contract (D179 C0), including CLOSED/FROZEN restrictions.
- Runtime contract: frame locals may be the only store of a root's bindings
  only while its activation's Context is neither materialized nor given a
  lexical authority (`ProtosActivation.hasUnobservedFrameNativeExecutionContextForRuntime`).
- PLAT067-B (I116-A) ratified binding elimination only for the I101-B proven
  scalar bodies (literal and alias chains).

### Accepted divergence

I115-M admits every parameterless root whose expressions belong to its
compact subset, including assignments at root level, in Boolean-control
callbacks and in `whileTrue` conditions and bodies. The compact path has no
check of a published activation and no FROZEN/CLOSED check, and it has no
authoritative fallback. Entered with a published activation whose Context
already exists, such a root:

- returns the exact guest result and keeps the activation identity;
- creates and assigns only Bytecode locals, so the Context receives no
  binding and its slots and state are unchanged;
- does not signal for a CLOSED or FROZEN Context, where the ratified
  contract would reject the initial creation.

This goes beyond the set PLAT067-B ratified and is attributed to the owner's
I115 decision, not to PLAT067-B.

### Reachability

No guest path of Core is known to reach the Context of a compact invocation:
an admitted body references neither `context` nor any Closure, sends no
message and calls nothing, and no caller holds that Context before entry.
The divergence is observed through host or tooling materialization before
entry (the two tests). A tooling evaluation that materializes and freezes
the Context while a compact root is suspended has not been analyzed.

### Coverage

`ProtosPerf025CompactCalleeExecutionTest.perf036aObservedActivationUpdatesAuthoritativeBinding`
and `perf036aFrozenOrClosedEntryContextRejectsInitialCreation` keep their
names and assert the current behavior, labeled as this divergence: exact
result, activation identity, unchanged Context slots (`localSlotsSnapshot`),
no fabricated binding, a fresh Context per invocation, and no error with an
unchanged CLOSED or FROZEN state.
