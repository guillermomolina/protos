# D103 — Dynamic capture rest composition and arm-binding interface contract

Status: **RATIFIED**
Specification revision: **`0.1.409`**
Explicit project-owner approval: **2026-09-12**
Nature: non-normative language-decision record; rationale for the normative D083/D088/D095 dynamic-capture composition boundary
Primary normative owners: `spec/semantics/MATCHING.md` and `spec/PROTOS_GRAMMAR.md`
Decision issue: GitHub `#422`
Primary consumer: I038 / GitHub `#391`

## Decision

Core v0.1 selects **D103-A: terminal dynamic capture segment**.

A variable-arity capture segment introduced by an opaque matcher capture
interface containing `...rest` may participate in a composed pattern only when
that variable segment is **terminal in the final ordered arm-binding interface**.

Terminality is judged over the complete logical binding interface exposed by the
whole arm pattern after applying the already-ratified structural ordering rules.
It is not judged merely inside the local `captures(...)` spelling.

Thus:

```protos
case matcher captures(first, ...rest) => ...
```

is valid with respect to D103 because the variable segment is terminal.

A structural form whose final child contributes the variable tail can likewise
remain valid when the complete interface is representable by an ordinary
Closure parameter list conceptually equivalent to:

```text
fixed1, fixed2, ..., ...rest
```

But:

```protos
case [matcher captures(first, ...middle), @last] => ...
```

is invalid because another source binding position follows the variable segment.

This is a source/static interface validity rule. Implementations must reject a
statically visible non-terminal dynamic segment; they must not reinterpret it by
partitioning captures from both ends, silently aggregating the dynamic segment,
dropping captures, or introducing a second binding carrier.

## Structural remainder is not a dynamic capture segment

D084/D086 structural remainder binding is categorically different from D103
dynamic matcher capture arity.

For example:

```protos
case [@first, ...@middle, @last] => ...
```

has three fixed logical binding positions. `middle` receives one ordinary
capture whose value is the fresh frozen D084 remainder Array. It is not a
variable number of positional capture actuals.

The analogous D086 Map remainder remains one ordinary Map-valued capture.

Therefore a structural remainder binder does not by itself trigger D103's
terminality restriction.

## Existing authorities preserved

D103 does not replace or reinterpret the existing matching contracts:

- D072 remains the sole ordinary matcher outcome carrier:
  canonical `false`, canonical `true`, or a non-empty standard Array of
  positional captures;
- D083 remains authoritative for shallow ordered capture concatenation;
- D088 remains authoritative for conversion of the final capture sequence into
  ordinary positional actual arguments of the selected Closure/callable;
- D090 remains authoritative for OR first-success behavior;
- D095 remains authoritative for `captures(required..., ...rest)` and structural
  source syntax.

D103 only closes the composition boundary exposed when a D083 child contributes
an unbounded number of captures before another source binding position.

## Ordinary Closure ABI

The selected rule deliberately preserves the direct D088 lowering model:

```text
successful D072/D083 capture sequence
        ↓
ordinary positional arguments
        ↓
ordinary selected-arm Closure parameter binding
```

A terminal dynamic segment maps directly onto the already-standard final Closure
rest parameter.

D103 introduces no:

- `CaptureFrame`;
- `CaptureSignature`;
- dynamic binding descriptor;
- bidirectional capture partitioner;
- hidden capture grouping;
- alternate arm invocation convention;
- named-argument mechanism; or
- second matcher authority.

If ordinary selected-arm invocation still fails because an arbitrary matcher
produces an incompatible actual capture count, D088's ordinary invocation Error
remains authoritative and no later arm is retried.

## Comparative basis

The audit compared Rust, Python, Ruby, Racket, Scala 3, F#, OCaml,
Erlang/Elixir, Haskell, parser/PEG-style capture systems and variadic
destructuring/binding models.

The dominant pattern in mature systems is that a structural remainder in the
middle is represented as **one aggregate binding** (for example a list, slice or
rest value), while genuinely variable-arity extractor/capture behavior is not
flattened into an arbitrary non-terminal region of a later ordinary positional
call interface.

Python, Rust, Ruby, Racket and BEAM reinforce the aggregate-rest model for
structural destructuring. Scala extractors demonstrate that richer variable
extraction is possible, but only with a correspondingly richer extractor/binding
institution. Parser systems such as ANTLR likewise commonly expose repetition as
a collection-valued capture rather than injecting an unknown number of
positional bindings between later fixed positions.

Because Protos has already ratified the flat D072/D083 carrier and ordinary D088
Closure invocation ABI, retaining terminal variadicity is the smallest
composition rule consistent with those authorities.

## Comparative scoring

Selected D103-A:

| Dimension | Score |
| --- | ---: |
| Correctness / invariant preservation | 5.0 |
| Protos alignment | 5.0 |
| Future-option resilience | 4.9 |
| Scalability | 5.0 |
| Conceptual simplicity | 5.0 |
| Portability / implementation freedom | 5.0 |
| Runtime / resource cost | 5.0 |
| Failure / operability | 5.0 |
| Reversibility / migration cost | 4.8 |
| Evidence maturity / implementation risk | 5.0 |
| **Total** | **49.7 / 50** |

Headline scores:

- future-option resilience: **4.9 / 5**;
- scalability: **5.0 / 5**;
- Protos alignment: **5.0 / 5**;
- confidence: **HIGH**.

## Scalability

The final binding interface remains representable as:

```text
fixed-prefix bindings + optional terminal dynamic tail
```

This remains linear and backend-neutral for deep nested patterns, large capture
counts, OR compatibility checking, the legacy AST backend, Bytecode DSL,
debugging and future tooling.

No runtime boundary markers are needed to reconstruct where one dynamic segment
ended before a later fixed binding, because Core v0.1 simply disallows that
shape.

Multiple dynamic segments in one flattened final interface are valid only if
composition rules can reduce them to one terminal variable segment without
ambiguity. Under the current D072/D083 flat carrier, an earlier dynamic segment
followed by another binding or dynamic segment is non-terminal and invalid.

## Strongest counterargument

D103-A rejects some source forms that the D095 parser grammar can describe, such
as a nested opaque matcher with `captures(...rest)` followed by a later binder.

A more expressive language could reserve fixed suffix positions and partition a
capture sequence from both ends, or could carry segment-boundary metadata.

The project declines those choices because a flat D072/D083 result contains no
general boundary information for multiple variable segments, and adding such
metadata would reopen the deliberately ordinary D088 Closure ABI.

## Regret and escape path

If future Protos programs demonstrate strong demand for non-terminal dynamic
capture segments, a later decision may add an explicit syntax whose semantics
include aggregation or segment boundaries.

That future feature can be additive. Existing D103 source retains its meaning,
and the direct D088 path remains the simple fast path.

The reverse direction is substantially harder: once ordinary source is allowed
to depend on implicit repartitioning or a hidden richer carrier, removing that
institution becomes a breaking semantic change.

## Intentionally deferred

D103 does not select:

- new syntax for segmented/non-terminal variadic binding;
- a richer matcher outcome carrier;
- capture reflection or runtime metadata;
- optional/repetition/search patterns;
- Map/Array runtime implementation details;
- OR implementation mechanics beyond the existing D090/D088 interface rules;
- guard behavior;
- Bytecode DSL implementation strategy; or
- optimizer-specific capture materialization.
