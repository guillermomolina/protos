# D093 — Match expression surface, arm grammar and lowering contract

Status: **RATIFIED**
Specification revision: **`0.1.405`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record; rationale for normative outer matching surface and lowering contract
Primary normative owners: `spec/PROTOS_GRAMMAR.md` (surface envelope) and `spec/semantics/MATCHING.md` (semantic lowering)
Decision issue: GitHub `#385`

## Decision

Core v0.1 selects **B-prime: a low-precedence postfix matching expression
surface** whose outer shape is:

```protos
subjectExpression match {
    case PATTERN => armBody
    case PATTERN when guardExpression => armBody
}
```

`PATTERN` is deliberately a metavariable in D093, not a selected pattern
production. D093 fixes the outer envelope, arm structure, guard attachment,
precedence and lowering contract. A later separately approved decision must
supply the concrete internal pattern grammar before the surface becomes
implementation-complete.

The spellings `match`, `case`, and `when` are **contextual structural markers**.
They remain lexically ordinary identifier spellings and are not added to the
global reserved-word set. Outside the exact structural positions defined by the
matching envelope, they retain ordinary identifier/name behavior. In particular,
D073's ordinary selector remains:

```protos
pattern.match(subject)
```

and a source-defined `match` slot/name remains ordinary.

The matching form is expression-valued. The normal value produced by the selected
arm body is the normal value of the complete matching expression.

The subject side of the surface is a complete ordinary `binary-expression`.
Consequently matching binds more weakly than the existing binary-operator
hierarchy. Conceptually:

```protos
a + b match { ... }
```

matches the result of `a + b`. The matching result is not made into a new
postfix/binary operand layer by D093; source that wants to continue from the
matching result may parenthesize it.

Every matching expression contains at least one explicit `case` arm. Arms use
the existing source-line convention: a logical newline separates arm lines and
`;` separates arms on one logical line. There is no trailing-terminator
exception specific to matching.

An arm has the conceptual form:

```protos
case PATTERN [when guardExpression] => closureBody
```

The `when` clause is exactly D092's guard phase. D093 adds no second guard
semantics, truthiness, implicit await, or pure/restricted guard language.

The `=>` token deliberately reuses Protos's existing Closure arrow as the visual
boundary into delayed arm execution. The arm body reuses the existing
`closure-body` shape: either one ordinary expression or a braced
`expression-sequence`. D093 creates no new statement-suite or branch-body
language.

Core v0.1 has no privileged `default` or `else` matching arm and no fallthrough
operation. A future catch-all surface is an ordinary irrefutable pattern under
the same `case` grammar and D092 semantics.

D093 creates no runtime `Match`, `Case`, arm descriptor, matcher registry,
capture environment, or alternate invocation authority.

## Semantic lowering contract

The postfix orientation does **not** mean that the subject receives a `match`
message. D071/D073 remain authoritative: recognition belongs to each pattern.

The observable lowering of one matching expression is:

```text
S := evaluate subjectExpression exactly once

for each arm in source order:
    outcome := attempt arm pattern against S through D073 pattern.match(S)

    outcome == false:
        continue with next arm

    valid D072 success:
        establish that arm's D088 logical binding interface

        if arm has a guard:
            evaluate guard exactly once with those logical bindings

            guard == false:
                continue with next arm
                (never reopen a D090 alternative)

            guard == true:
                continue to body selection

            other normal guard result:
                signal ordinary Error

            Error / non-local control / cancellation / explicit suspension:
                propagate

        invoke the selected arm body exactly once under D088 ordinary
        callable/Closure semantics

        normal body result:
            becomes the normal matching-expression result

        non-normal body behavior:
            propagates; arm search does not resume

if no arm is selected:
    signal the fresh ordinary Error required by D092
```

`S` is a semantic explanatory temporary, not a user-visible source binding.

All matcher effects, guard effects, Error/control/cancellation/suspension,
capture ordering, OR first-success commitment and no-rollback rules are inherited
unchanged from D071-D092.

## Comparative basis

The audit compared Rust, Scala 3, OCaml, F#, Haskell, Erlang, Elixir, Python,
Swift, Dart, C#, Java, Kotlin, Ruby, Racket, Clojure `core.match`, parser/PEG
clause grammars, and Self/Smalltalk-style message/Block control.

Scala and C# supplied the strongest independent evidence for receiver-adjacent /
postfix expression-valued matching surfaces. Rust and Dart supplied strong
evidence for `pattern => body` arm readability. OCaml/F#/Haskell/Erlang supplied
long-lived evidence for ordered clause semantics and expression-valued branch
selection. Python supplied mature evidence that contextual/soft structural words
can preserve identifier compatibility. Racket and Clojure demonstrated that
rich declarative surfaces need not preclude optimized decision structures.
Self/Smalltalk supplied the philosophy evidence for keeping selected execution
grounded in ordinary Closures/Blocks instead of creating a separate execution
universe.

Two superficially attractive prefix forms were rejected for Protos-specific
grammar reasons:

- `match(subject) { ... }` already has the shape of an ordinary call plus a
  trailing Closure and would repeat the source-stealing failure mode D051
  rejected for `if(condition) { ... }`;
- `match subject { ... }` collides with the existing
  `parent-expression { object-body }` shape unless the subject grammar is
  artificially restricted or another delimiter is added.

By contrast, `subject match { ... }` occupies a previously invalid structural
position while preserving ordinary `.match(...)` message syntax.

## Comparative scoring

Selected B-prime:

| Dimension | Score |
| --- | ---: |
| Correctness / invariant preservation | 5.0 |
| Protos alignment | 5.0 |
| Future-option resilience | 4.9 |
| Scalability | 5.0 |
| Conceptual simplicity | 4.8 |
| Portability / implementation freedom | 5.0 |
| Runtime / resource cost | 5.0 |
| Failure / operability | 5.0 |
| Reversibility / migration cost | 4.5 |
| Evidence maturity / implementation risk | 5.0 |
| **Total** | **49.2 / 50** |

Headline scores:

- **future-option resilience:** 4.9 / 5;
- **scalability:** 5.0 / 5;
- **Protos alignment:** 5.0 / 5;
- **confidence:** HIGH.

Scores are comparison aids; explicit project-owner approval selects B-prime.

## Scalability and optimization

The surface creates no per-arm runtime object, global registry, shared mutable
dispatch state, Actor/Process coordination mechanism, or backend-specific
representation.

A straightforward implementation may preserve the already-ratified linear
ordered arm walk. A compiler may instead build decision trees, indexes, jump
tables, decision DAGs, fused bytecode, or another internal representation only
when observable matcher calls/order, effects, capture interfaces, guards,
control behavior and selected result remain identical.

This keeps the surface compatible with many Tasks/Actors/Processes, alternate
runtimes, Native Image/AOT and a future Truffle Bytecode DSL implementation.

## Strongest counterarguments

The strongest semantic objection is visual: `subject match { ... }` can look as
though `match` were dispatched to the subject, contradicting D073's pattern-owned
authority.

D093 therefore makes the lowering explicit: the postfix marker does not perform
`subject.match(...)`; the subject is evaluated once and each candidate pattern
is invoked through `pattern.match(subject)`.

The strongest grammar objection is introducing contextual structural words after
D051 protected ordinary identifiers. The compatibility distinction is decisive:
D051 would have reinterpreted an already-valid ordinary call shape, whereas the
D093 postfix structural position was not a valid ordinary send/call shape.
`match`, `case`, and `when` remain lexical identifiers everywhere else.

## Regret and escape path

A future macro/syntax-extension facility might become capable of expressing the
same pattern grammar, binder scopes and delayed arms entirely as library syntax,
or a future identifier-style infix facility might compete with `expr match ...`.

The escape path is comparatively small because D093 introduces no runtime
matching institution. A future syntax facility can target or subsume the same
D071-D093 semantic contract, and source-version evolution can handle any later
surface replacement. The harder migration would have been introducing permanent
runtime `Match`/`Case` descriptor institutions now.

## Intentionally deferred

D093 does not select:

- the concrete internal `match-pattern` grammar;
- Array sequence-pattern source delimiters/components;
- Map keyed-pattern source delimiters/components;
- binder/capture declaration spelling;
- whole-subject alias spelling;
- D090 OR-pattern spelling;
- the spelling of the ordinary irrefutable/catch-all pattern;
- optional, repetition, find, subsequence, stream, or general backtracking
  pattern families;
- exhaustivity or redundancy analysis;
- a dedicated `MatchFailure` Error subtype;
- first-class Pattern reflection or mandatory capture signatures; or
- parser/runtime implementation of the matching surface.

Those remain separate decisions. In particular, D093 alone does not authorize a
parser implementation before concrete pattern syntax is ratified.
