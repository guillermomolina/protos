# Protos Implementation Changelog

Current implementation-series changes are kept in this file.

Historical implementation changelogs:

- [0.3.x](changelog/CHANGELOG-0.3.md)
- [0.2.x](changelog/CHANGELOG-0.2.md)
- [0.1.x](changelog/CHANGELOG-0.1.md)

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
