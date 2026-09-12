# D100 — Match-arm guard / Closure-arrow disambiguation contract

Status: **RATIFIED**
Specification revision: **`0.1.408`**
Explicit project-owner approval: **2026-09-12**
Nature: non-normative language-decision record; rationale for the normative D093 guard/arm grammar boundary
Primary normative owner: `spec/PROTOS_GRAMMAR.md`
Decision issue: GitHub `#417`
Primary consumer: I038-B / GitHub `#391`

## Decision

Core v0.1 selects **D100-A-prime: contextual delimiter-first guard root**.

Inside the `when` clause of a D093 match arm, the first `=>` encountered at the
guard expression's own structural nesting level is the arm delimiter.

Consequently an ordinary Closure expression is not admitted as the **ungrouped
root** of a match-arm guard.

Examples:

```protos
case p when ready => body
case p when x => y => body
case p when (x => y) => body
case p when accepts(x => x) => body
```

The ambiguous-looking second form has exactly one Core v0.1 parse: guard `x`,
first `=>` as arm delimiter, and arm body `y => body`.

Grouping makes a Closure's extent explicit; grouped/nested Closures remain
ordinary expressions. D092 remains authoritative after parsing: guard evaluation
still requires canonical `true` or `false` and otherwise signals ordinary Error.

## Structural rule

The delimiter-first rule is structural, not line-oriented. A logical newline
before the arm delimiter does not change arrow ownership. Implementations must
not choose the delimiter by rightmost-arrow search, greedy root-Closure parsing,
backtracking, type information, runtime guard values, or indentation.

D100 introduces no Guard runtime object, restricted Boolean DSL, new token,
reserved word, second Closure syntax, second arrow token, or second guard path.

## Comparative basis

The audit compared Dart, C#, Scala 3, Haskell, Rust, OCaml/F#, Kotlin, Swift,
Java, and parser/tooling precedents. Dart supplied the closest direct precedent:
its switch-expression pattern design documents the same `when expression =>`
versus function-literal-arrow ambiguity and resolves it by excluding an
ungrouped root function literal while allowing grouping. C# and Scala 3 provide
independent evidence for narrowing the guard root syntactically where needed.

## Comparative scoring

| Dimension | Score |
| --- | ---: |
| Correctness / invariant preservation | 5.0 |
| Protos alignment | 5.0 |
| Future-option resilience | 5.0 |
| Scalability | 5.0 |
| Conceptual simplicity | 4.8 |
| Portability / implementation freedom | 5.0 |
| Runtime / resource cost | 5.0 |
| Failure / operability | 5.0 |
| Reversibility / migration cost | 5.0 |
| Evidence maturity / implementation risk | 5.0 |
| **Total** | **49.8 / 50** |

Headline scores: future 5.0/5, scalability 5.0/5, Protos alignment 5.0/5,
confidence HIGH.

## Strongest counterargument

D093 described guards as ordinary expressions. D100 adds one contextual source
restriction: an ungrouped Closure cannot be the root guard expression. The value
capability is preserved because `(x => y)` and nested Closures remain ordinary.

## Regret and escape path

A future syntax system might want unrestricted root expressions before the same
`=>` delimiter. A later language revision can add an unambiguous delimiter or
explicitly delimited guard surface without changing D092 runtime semantics.

## Intentionally deferred

D100 does not change D092 guard runtime semantics, D093 postfix orientation,
D095 patterns, D096 coverage semantics, Closure runtime semantics, or parser
architecture beyond conformance to this source rule.
