# D092 — Guard evaluation, arm continuation and no-match semantics

Status: **RATIFIED**
Specification revision: **`0.1.404`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record; rationale for normative guarded-arm selection and terminal no-selection semantics
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#381`

## Decision

Core v0.1 uses **ordinary post-pattern strict-Boolean guards** for guarded match
arms, followed by deterministic continuation to the next arm on canonical
`false`.

A candidate arm's pattern is attempted under the already-ratified matching
protocol. Only after that complete pattern succeeds with a valid D072 result may
the guard, if any, be evaluated. D088 remains the binding ABI: the successful
capture interface determines the ordinary source-visible binder values available
to that arm. D092 makes those same logical binder values visible to the guard and
to the eventual arm body without introducing another capture/binding carrier.

Pattern success alone does not select a guarded arm body. The guard is the final
acceptance gate for that arm:

```text
pattern mismatch      -> next arm
pattern success,
  no guard            -> select this arm
pattern success,
  guard == true       -> select this arm
pattern success,
  guard == false      -> next arm
```

A guard is ordinary Protos evaluation. It may use ordinary messages, Closures,
effects, Error signaling, non-local control, cancellation, and explicit
suspension exactly as those facilities behave elsewhere. D092 introduces no
guard-specific pure/restricted sublanguage, whitelist, truthiness, implicit
`Future.value()`, or hidden adoption/await behavior.

A normally completing guard must produce exactly canonical `true` or canonical
`false`. Any other normal result is invalid guard output and signals an ordinary
standard `Error` occurrence. In particular `null`, Numbers, Strings, Arrays,
ordinary objects, and Future values are not coerced to Boolean.

Canonical `false` rejects only the current **arm**. It does not reinterpret the
already-successful pattern as mismatch and never reopens a successful D090
alternative composite. A D090 alternative chosen by first-success commitment
therefore remains chosen even when the containing arm's guard later returns
`false`; matching proceeds to the next arm, not to another alternative inside the
same pattern.

Error, non-local control, cancellation, and explicit suspension from guard
evaluation propagate normally. They are not guard rejection and do not cause a
later arm to be tried. A selected arm body's normal result is the result of the
matching operation; Error/control/cancellation/suspension from that body likewise
propagate and do not resume arm search.

Matching and guards are not transactional. Effects performed by a candidate
pattern or its guard remain visible when the pattern later mismatches or the
guard returns `false`. Source bindings belonging to a rejected candidate arm
remain scoped to that arm's guard/body interface and do not become ambient
bindings for later arms; no rollback mechanism is needed or introduced.

If every arm either mismatches or is rejected by a canonical-false guard, and no
ordinary irrefutable/catch-all arm is selected, the matching operation signals
one **fresh ordinary `Error`** under the existing standard failure-occurrence
rules. D092 introduces no `MatchFailure` prototype, canonical-`null` fallback, or
implicit default result.

A future source-level `default`, `else`, wildcard, or equivalent catch-all form,
if one is adopted, must be semantically an ordinary irrefutable arm rather than
a privileged fallback execution path that bypasses the arm-selection rules.
Concrete spelling remains outside D092.

The subject expression remains evaluated exactly once at the matching-expression
boundary under the existing D071/D073 contract. Arms are considered in semantic
source order. A later arm receives the same already-evaluated subject value; if
earlier pattern/guard effects mutate ordinary reachable state, the later arm
observes that ordinary state.

Implementations may fuse pattern, binding, guard and body machinery, inline
standard matchers, eliminate unobservable intermediate carriers, or build
decision structures only when all observable behavior is preserved: attempted
arm/pattern order, matcher calls, D072 validation, D088 binding values, exactly
one guard evaluation for each successful guarded candidate, strict Boolean guard
results, effects, no D090 reopening, control/suspension propagation, selected
body behavior, and terminal no-selection Error.

## Comparative basis

The audit compared Rust, Python, Scala, Haskell, OCaml, F#, Erlang, Elixir,
Swift, Dart, C#, Java, Ruby, Racket, parser/PEG predicate models, and
Self/Smalltalk-style ordinary Boolean/Closure control.

OCaml, Dart, Python, F#, Scala, Swift, C#, Java and Racket provide strong evidence
for the basic sequence **pattern success -> bindings visible -> guard -> next
arm on false**. OCaml and Dart align especially closely with D090 because guard
rejection continues at the arm level.

Rust is an important negative precedent for this exact architecture: a match
guard associated with an OR-pattern arm may be evaluated while Rust tries
different OR alternatives. D090 has already selected first-success commitment
inside an alternative composite, so Protos must not import that reopening
behavior.

Erlang and Elixir provide the strongest alternative architecture: guards are a
restricted side-effect-free-ish sublanguage chosen to improve predictability and
optimization. That model is mature, but it would create a second category of
legal Protos expression while arbitrary `pattern.match(subject)` remains ordinary
effectful Protos behavior. D092 therefore keeps guards ordinary rather than
creating an isolated guard institution.

Self and Smalltalk reinforce the Protos-side control principle: ordinary
Boolean/Closure/message behavior is preferable to a new privileged control
universe when the ordinary mechanisms already express the required behavior.

For terminal no-selection, OCaml, Scala, Ruby, Racket, Erlang/Elixir, and C#
provide strong precedent for a runtime failure path; Swift/Dart/Java show the
benefits of exhaustivity where a closed static domain exists. Protos deliberately
supports arbitrary runtime matcher objects, so mandatory general exhaustivity
would require a closed pattern/type institution that D071-D090 do not define.

## Comparative scoring

For the selected guard architecture A-prime:

| Dimension | Score |
| --- | ---: |
| Correctness / invariant preservation | 5.0 |
| Protos alignment | 5.0 |
| Future-option resilience | 5.0 |
| Scalability | 5.0 |
| Conceptual simplicity | 5.0 |
| Portability / implementation freedom | 5.0 |
| Runtime / resource cost | 4.5 |
| Failure / operability | 5.0 |
| Reversibility / migration cost | 5.0 |
| Evidence maturity / implementation risk | 5.0 |
| **Total** | **49.5 / 50** |

For terminal no-selection N1 (fresh ordinary `Error`):

| Dimension | Score |
| --- | ---: |
| Correctness / invariant preservation | 5.0 |
| Protos alignment | 5.0 |
| Future-option resilience | 5.0 |
| Scalability | 5.0 |
| Conceptual simplicity | 5.0 |
| Portability / implementation freedom | 5.0 |
| Runtime / resource cost | 5.0 |
| Failure / operability | 5.0 |
| Reversibility / migration cost | 4.5 |
| Evidence maturity / implementation risk | 5.0 |
| **Total** | **49.5 / 50** |

Confidence is **HIGH** for both selections. Scores are comparative evidence, not
the decision authority; the project-owner approval selects A-prime + N1.

## Scalability and future-option resilience

Selecting the `k`th arm requires at most the ordinary ordered pattern attempts
needed to reach it plus guards only for candidate patterns that actually
succeeded. D092 introduces no per-arm transaction, rollback log, speculative
environment, global registry, shared mutable guard state, or parallel branch
coordination.

The architecture remains compatible with many Tasks, Actors and Processes
because all state is local to the ordinary matching execution and existing
activation/binding machinery. A guard that explicitly suspends suspends that
execution at the ordinary continuation boundary; D092 does not race later arms
while it is suspended.

A future Bytecode DSL or alternate runtime may lower the semantics into branches,
jumps or decision DAGs. Standard pure cases may be optimized aggressively, but
arbitrary observable matcher/guard calls cannot be reordered, duplicated, or
eliminated merely for speed.

Future exhaustivity analysis remains possible for compiler-known closed subsets.
Such a proof may eliminate an unreachable terminal Error in optimized code
without changing the general semantic rule for open arbitrary matchers.

## Strongest counterargument

The strongest alternative is the Erlang/Elixir family: restricting guards to a
small side-effect-free set makes matching more declarative, simplifies static
reasoning, and permits stronger optimization.

That advantage is real. It is not sufficient for Protos because matching already
permits arbitrary ordinary matcher code with effects, Error/control and
suspension. Restricting only guards would not make the overall matching process
pure; it would instead create a new privileged expression subset solely for one
phase of matching.

If future experience establishes a real need for statically analyzable pure
predicates, Protos can add an optional separately specified predicate abstraction
or analysis discipline without changing D092's ordinary guard semantics.

## Regret and escape path

A future compiler/tooling ecosystem may want guaranteed pure guards for exhaustive
decision-DAG optimization, static redundancy proofs, or deterministic distributed
query compilation.

The escape path is additive: introduce a separately approved pure/predicate
subset or annotation whose stronger guarantees permit additional optimization,
while ordinary guards keep D092 behavior. Removing a mandatory restricted guard
language after ecosystem code depended on its whitelist would be harder.

For terminal failure, future users may want to catch no-match separately from
other ordinary Errors. The escape path is likewise additive: a later decision may
introduce a `MatchFailure`-like standard Error prototype for this failure rule.
D092 intentionally does not create that taxonomy before concrete evidence
requires it.

## Intentionally deferred

D092 does not select:

- concrete `match`, `case`, guard, arm, default, wildcard, or arrow syntax;
- concrete Array/Map/alternative/binder surface spelling;
- exhaustivity or redundancy analysis;
- whole-subject alias syntax;
- optional, repetition, find, subsequence, stream, or general backtracking
  pattern families;
- first-class Pattern reflection or mandatory capture-signature metadata;
- a pure/restricted guard sublanguage or static effect system;
- a dedicated `MatchFailure` standard Error prototype;
- recognition-only matcher fast paths; or
- parser/runtime implementation of the future matching surface.
