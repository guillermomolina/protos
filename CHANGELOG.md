# Protos Implementation Changelog

Current implementation-series changes are kept in this file.

Historical implementation changelogs:

- [0.3.x](changelog/CHANGELOG-0.3.md)
- [0.2.x](changelog/CHANGELOG-0.2.md)
- [0.1.x](changelog/CHANGELOG-0.1.md)

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
