# Protos Implementation Changelog

Current implementation-series changes are kept in this file.

Historical implementation changelogs:

- [0.3.x](changelog/CHANGELOG-0.3.md)
- [0.2.x](changelog/CHANGELOG-0.2.md)
- [0.1.x](changelog/CHANGELOG-0.1.md)

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
