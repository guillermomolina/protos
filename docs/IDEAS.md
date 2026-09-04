# Protos Ideas

This file records non-normative ideas that may be worth exploring in the future.
An entry here is not a language commitment, an implementation requirement, or an
open normative design decision.

Ideas should remain here while their value, scope, or shape is still exploratory.
If an idea becomes concrete implementation work, move or reference it from
`OPEN_TASKS.md`. If it requires a normative language decision, move the question
to the appropriate specification design process instead.

## I001 — Concurrency Model Advisor (C/P/A)

Status: IDEA

### Motivation

Protos deliberately distinguishes three concurrency models with different
semantic purposes:

- C: cooperative work sharing one mutable execution domain;
- P: isolated parallel computation without persistent identity;
- A: persistent isolated identity, state, lifecycle, and messaging.

Because these boundaries are explicit, tooling may be able to detect code whose
observed or statically inferred workload characteristics suggest that another
model would better express the programmer's intent.

### Possible diagnostics

Examples worth investigating include:

- C -> P: CPU-bound cooperative work with long non-suspending execution segments
  that can monopolize its mutable execution domain;
- P -> A: repeated isolated work that is actually maintaining persistent state,
  identity, lifecycle, or message-oriented interaction;
- A -> P: Actors used only to perform isolated computations with no meaningful
  persistent identity or lifecycle;
- A/P -> C: cases where isolation or persistent identity adds no useful semantic
  boundary and ordinary cooperative composition may be sufficient.

These are hypotheses for diagnostics, not normative rules.

### Possible approaches

The advisor could combine:

- static AST or semantic analysis;
- compiler/linter diagnostics;
- IDE hints;
- optional runtime profiling;
- development-time reports combining static and observed behavior.

The parser itself should not need to own this policy merely because it constructs
the AST. A later semantic-analysis/tooling phase may be a better static home.

### Non-goals and safety boundary

The advisor must be advisory by default. It must not silently change C, P, or A
semantics on behalf of the program.

Changing concurrency model can change isolation, snapshot boundaries, mutable
authority, lifetime, failure propagation, cancellation, identity, messaging, and
other observable behavior. Any future automated transformation would therefore
need a separately proven semantics-preserving contract rather than a heuristic.

Diagnostics should be explainable: a suggestion should identify the evidence
that motivated it and the semantic consequences the programmer should consider.

### Open questions

- Which patterns can be identified soundly by static analysis?
- Which useful signals require runtime observation?
- Which suggestions can be made with high confidence without excessive noise?
- Should diagnostics live in the compiler, a linter, IDE tooling, runtime
  profiling, or a combination?
- Can tooling estimate when the cost of crossing a P or A boundary outweighs the
  expected benefit?
- How should intentional model choices suppress inappropriate diagnostics?
