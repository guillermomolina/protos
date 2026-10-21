# Protos Implementation Changelog

Current implementation-series changes are kept in this file.

Historical implementation changelogs:

- [0.3.x](changelog/CHANGELOG-0.3.md)
- [0.2.x](changelog/CHANGELOG-0.2.md)
- [0.1.x](changelog/CHANGELOG-0.1.md)

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
