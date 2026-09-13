# Matching Expressions

> **Status:** Non-normative programming guide
>
> **Primary normative owners:** `spec/semantics/MATCHING.md` and
> `spec/PROTOS_GRAMMAR.md`
>
> **Implemented capability:** `I038 — Matching expression implementation`

Matching in Protos is an expression-level control-flow mechanism built from
ordinary object behavior rather than from a closed compiler-owned hierarchy of
pattern types.

The key mental model is:

> A pattern owns recognition. Matching evaluates one subject once, tries arms in
> source order, and asks each attempted pattern to recognize the relevant subject
> through ordinary `pattern.match(subject)` behavior.

The `match` surface adds convenient structural pattern forms, binders, guards,
and static source checks around that protocol. It does not move recognition
authority to the subject, create a hidden matcher registry, or introduce a
second equality system.

This chapter explains the programmer-facing surface. The normative definition
remains in
[`MATCHING.md`](../../spec/semantics/MATCHING.md) and the concrete grammar in
[`PROTOS_GRAMMAR.md`](../../spec/PROTOS_GRAMMAR.md).

## Start with the postfix expression

The outer form is postfix and expression-valued:

```protos
subjectExpression match {
    case PATTERN => armBody
    case PATTERN when guardExpression => armBody
}
```

For one matching expression:

1. `subjectExpression` is evaluated exactly once;
2. arms are considered in source order;
3. a mismatching pattern advances to the next arm;
4. a successful pattern exposes its bindings to that arm;
5. a guard, when present, is evaluated after successful recognition;
6. the first accepted arm executes;
7. the arm body's result becomes the result of the complete matching expression.

For example:

```protos
value match {
    case 0 => "zero"
    case 1 => "one"
    case @other => other
}
```

The final arm binds any remaining subject to `other`, so the expression always
selects an arm unless matching itself produces an Error or another ordinary
non-local control transfer.

The syntax:

```protos
subject match { ... }
```

does **not** mean:

```protos
subject.match(...)
```

The subject is the value being recognized. Matcher authority stays on the
pattern side.

## Ordinary values are ordinary patterns

Every ordinary value can participate through the standard root matcher behavior.

When ordinary lookup selects `Object.match(subject)`, the matcher performs
exactly one ordinary equality send:

```protos
this == subject
```

and returns that equality result unchanged.

That means ordinary literal/value patterns use the existing semantic equality
model rather than a special literal-matching relation:

```protos
status match {
    case "ready" => 1
    case "busy" => 2
    case _ => 0
}
```

A value can override or shadow `match(subject)` through ordinary object
behavior. Domain matchers therefore do not need a compiler registration API.

For example, a matcher can define its own recognition rule:

```protos
positive: {
    match: (value) => value > 0
}

value match {
    case positive => "positive"
    case _ => "not positive"
}
```

The call to `positive.match(value)` is ordinary Protos behavior. Effects, Error
propagation, non-local control, cancellation, and explicit suspension behave as
they do for other ordinary sends.

Matching does not add truthiness and does not implicitly await a Future.

## Matcher results have one small protocol

When a `match(subject)` result is consumed by matching, the valid normal
outcomes are:

```text
false        no match
true         successful match with zero captures
[x]          successful match with one positional capture
[x, y, ...]  successful match with multiple positional captures
```

The capture carrier is a **non-empty standard Array**.

Only canonical `false` means mismatch. Only canonical `true` means
zero-capture success.

An empty Array is not another spelling for success with zero captures:

```text
[]      invalid matcher outcome
null    invalid matcher outcome
42      invalid matcher outcome
"yes"   invalid matcher outcome
Future  invalid matcher outcome
```

A normal invalid matcher result causes an ordinary Error at the matching
consumer boundary.

Captured values remain ordinary values. A capture whose value happens to be an
Array or Map is still one capture; matching does not recursively flatten nested
collections.

## `@name` binds and `_` discards

Two source forms are universally irrefutable:

```protos
@name
_
```

`@name` succeeds and contributes the current subsubject as one capture.

`_` succeeds and contributes no capture.

For example:

```protos
value match {
    case @captured => captured
}
```

and:

```protos
value match {
    case 0 => "zero"
    case _ => "something else"
}
```

Bindings become source-visible only after the complete candidate pattern has
succeeded. A nested child may have produced captures internally before a later
child mismatches, but that does not publish partial arm bindings.

Binding names are linear inside one fixed arm-binding interface. Reusing the same
fixed binder name is a source error; duplicate binders do not mean equality,
shadowing, or rebinding.

## Aliases capture the current subsubject as well as nested bindings

An alias has the form:

```protos
@name: nestedPattern
```

It captures the current subsubject first, then applies the nested pattern and
appends that nested pattern's captures.

For example:

```protos
value match {
    case @whole: [@first, ...] => {
        Array(whole, first)
    }
    case _ => null
}
```

If the Array arm succeeds, `whole` is the complete subject and `first` is the
first Array element.

The alias is not a mutation of an outer binding and does not publish `whole`
when its nested pattern later mismatches.

## `captures(...)` names captures from an opaque matcher

A user-defined matcher may return positional captures without exposing a
compiler-visible internal pattern structure.

The source can name those positions at the consumer boundary:

```protos
subject match {
    case matcher captures(left, right) => {
        Array(left, right)
    }
}
```

`captures(...)` does **not** send a `captures` message to `matcher`. It does not
change matcher metadata and does not replace the normal `match(subject)` result.
It only declares how the successful D072 positional capture sequence is consumed
by this arm.

A variable capture tail uses the ordinary rest-binding shape:

```protos
subject match {
    case matcher captures(first, ...rest) => {
        Array(first, rest)
    }
}
```

Here `first` consumes the first capture and `rest` is bound through the ordinary
Closure rest-argument contract.

Current source restrictions include:

- `captures()` is invalid;
- fixed names must be unique;
- at most one rest name is present and it is final in that local interface;
- a dynamic `...rest` segment must also be terminal in the **complete logical
  arm-binding order**, not merely terminal inside its local `captures(...)`
  spelling.

That last rule keeps the runtime carrier a simple positional sequence. Protos
does not invent suffix reservation, capture padding, hidden named carriers, or
branch-dependent layouts to make a non-terminal dynamic segment fit.

## Array patterns match standard Array indexed state

Array pattern syntax uses brackets:

```protos
items match {
    case [@first, @second] => Array(first, second)
    case _ => null
}
```

Without a remainder, the length is exact. The pattern above matches a standard
Array with exactly two indexed elements.

Array patterns apply specifically to subjects that own standard Array indexed
state. An arbitrary object does not become eligible merely because it has
`size`, `at`, iteration, or other Array-like messages.

An ineligible subject is ordinary mismatch.

### Array remainder

One remainder may appear between a fixed prefix and suffix:

```protos
items match {
    case [@first, ...@middle, @last] => {
        Array(first, middle, last)
    }
    case _ => null
}
```

The subject needs enough elements for the fixed prefix and suffix. The middle
range may be empty.

When a remainder is supplied to a nested pattern, that nested pattern receives a
fresh frozen standard Array containing the unmatched middle element references
in order.

Therefore:

```protos
...@middle
```

binds `middle` to one Array value. It does not turn every middle element into a
separate capture.

A bare:

```protos
...
```

accepts and discards the remainder.

For one Array-pattern attempt, the relevant Array shape and element references
are shallowly observed before nested element matchers run. Later mutation of the
original Array's indexed positions cannot change which references that attempt
already selected. The referenced element objects themselves are not deep-copied
or frozen.

## Map patterns match normal standard Map associations

Map pattern syntax uses `%{ ... }`:

```protos
record match {
    case %{ "name": @name } => name
    case _ => null
}
```

A normal Map pattern is **open/subset by default**. The example requires a
matching `"name"` association but does not reject unrelated associations.

Map patterns apply to normal standard `Map` keyed-entry state. An arbitrary
object with `at`, `containsKey`, iteration, or other Map-like behavior does not
automatically qualify. `IdentityMap` also does not automatically participate in
the normal Map-pattern contract.

### Exact Map matching

Use `exact` when no unselected associations may remain:

```protos
record match {
    case exact %{ "name": @name } => name
    case _ => null
}
```

This pattern accepts only an eligible Map whose associations are completely
accounted for by the keyed requirements.

The current grammar does not combine `exact` with a Map remainder.

### Map remainder

A remainder can receive the associations not selected by keyed requirements:

```protos
record match {
    case %{ "name": @name, ...@rest } => {
        Array(name, rest)
    }
    case _ => null
}
```

`rest` is one fresh frozen normal standard Map preserving the unmatched
associations' stored representative key references, mapped value references,
recorded hashes, and relative insertion order.

A bare `...` accepts and discards the remainder instead.

For one Map-pattern attempt, Protos establishes the stable shallow association
snapshot before evaluating query-key expressions or nested mapped-value
matchers. Query-key expressions are then evaluated once left-to-right, required
associations are resolved against that stable snapshot, and only then do the
mapped-value child matchers run in source order.

Missing required keys are pattern mismatch, not the ordinary missing-key
`Map.at` Error.

## OR patterns are ordered and commit to the first success

The OR spelling is:

```protos
left | right
```

Alternatives are tried in source order against the same current subject.

Only canonical `false` advances to the next alternative. The first valid success
commits the OR pattern immediately.

For example:

```protos
value match {
    case [1, @x] | [2, @x] => x
    case _ => null
}
```

Both successful alternatives expose the same ordered logical binding interface,
so the selected arm receives one `x` regardless of which branch matched.

This is invalid:

```protos
value match {
    case [1, @x] | [2, @y] => x
}
```

because the fixed alternatives do not expose the same logical binding names.

OR matching is ordered, not transactional:

- effects performed by a failing earlier alternative are not rolled back;
- a successful branch is not reconsidered later;
- alternatives are not raced or ranked by "best" match;
- a later outer guard failure does not reopen the committed OR and continue with
  its next branch.

Dynamic capture interfaces obey the same logical-interface rule:

```protos
subject match {
    case left captures(head, ...rest) |
         right captures(head, ...rest) => {
        Array(head, rest)
    }
}
```

## Guards run after successful recognition

A guard is introduced with `when`:

```protos
value match {
    case @candidate when acceptable(candidate) => candidate
    case _ => null
}
```

The guard runs exactly once only after the pattern has completely succeeded.
That arm's bindings are visible to the guard.

A normally returning guard must produce a canonical Boolean:

```text
true   accept this arm
false  reject this arm and continue with the next arm
```

There is no truthiness conversion. `null`, a Number, a String, an Array, an
ordinary object, or a Future is not a guard Boolean and causes an ordinary Error
when returned normally as the guard result.

A canonical-`false` guard continues with the next **arm**. It does not retry a
different branch of an OR pattern that already succeeded.

Effects performed by the successful pattern or by a false guard are not rolled
back before the next arm runs.

## `=>` after `when` has an explicit delimiter rule

The arm delimiter is the first eligible top-level `=>` after the guard.

Therefore:

```protos
subject match {
    case p when x => y => body
}
```

has one Core v0.1 interpretation:

- guard: `x`;
- first `=>`: arm delimiter;
- body: the Closure expression `y => body`.

When a Closure expression belongs inside the guard, make that ownership
structural by grouping it or nesting it inside another expression:

```protos
subject match {
    case p when (x => y) => body
}
```

or:

```protos
subject match {
    case p when accepts(x => x) => body
}
```

A newline before the arm delimiter does not change this ownership rule.

This avoids a whitespace-sensitive ambiguity between a guard Closure and the arm
boundary.

## Arm order, Errors, and no-match behavior are ordinary and explicit

A pattern mismatch is not an Error. It just advances matching to the next
appropriate alternative or arm.

An Error or another non-normal control transfer from a matcher is different: it
propagates under ordinary Protos rules and is not silently converted into
mismatch.

The same distinction applies to guards.

If every arm mismatches or has a guard that returns canonical `false`, matching
signals one fresh ordinary Error.

There is no implicit:

```text
null fallback
default value
MatchFailure subtype
retry of an earlier successful OR
```

A selected arm is free to return `null`; that is a successful matching result
and is distinct from terminal no-selection.

The existing Error model is explained in
[chapter 07](07-errors-handlers-ensure-and-resource-lifetime.md).

## Static checks are deliberately conservative

Protos keeps arbitrary matcher objects open, so static analysis cannot pretend
that all patterns belong to one closed algebraic universe.

Core v0.1 uses a conservative coverage model:

```text
PROVEN_EXHAUSTIVE
PROVEN_NON_EXHAUSTIVE
UNKNOWN
```

`UNKNOWN` is not a source error and is not the same as proven
non-exhaustiveness.

The analyzer does not execute arbitrary matcher behavior, equality, Map
`hash`/`==`, Map query-key expressions, or guards merely to prove coverage.

### Structural unreachability is a source error

Some facts are fixed by syntax and therefore checked strictly.

An arm after an unguarded universally irrefutable arm is invalid:

```protos
value match {
    case _ => 1
    case other => 2
}
```

Likewise:

```protos
value match {
    case @anything => 1
    case other => 2
}
```

The same rule applies inside one OR: an alternative after an already reachable
unguarded universal-irrefutable alternative is structurally unreachable.

A **guarded** irrefutable arm does not make following arms unreachable because
the guard may return `false`:

```protos
value match {
    case @candidate when acceptable(candidate) => candidate
    case _ => null
}
```

Richer sound redundancy or non-exhaustiveness findings may be exposed as
warnings/lints. They do not silently enlarge the Core source-error set.

## Bindings are ordinary selected-arm arguments

The implementation may optimize matching internally, but the programmer-facing
binding model is intentionally small.

Conceptually:

```text
pattern.match(subject)
        |
        v
false / true / [capture1, capture2, ...]
        |
        v
successful capture sequence
        |
        v
ordinary selected-arm invocation
```

`true` supplies zero capture arguments.

A non-empty capture Array supplies its elements as ordinary positional actual
arguments to the selected arm.

This is why the following features compose without introducing a separate
binding object:

- `@name` contributes one positional capture;
- `_` contributes none;
- aliases contribute the whole current subsubject before nested captures;
- nested Array/Map patterns concatenate their child captures in semantic order;
- `captures(...)` gives source names to an opaque matcher's positional results;
- OR alternatives must agree on the logical binding interface;
- guards see the same logical values the arm body would receive;
- a remainder Array or Map remains one ordinary aggregate capture.

The selected arm therefore follows the same callable/Closure rules described in
[chapter 03](03-closures-methods-and-receivers.md).

## Recognition remains separate from structural convenience

The built-in Array and Map pattern forms are standard structural
specializations. They do not establish a rule that every "sequence-like" or
"mapping-like" object is automatically destructurable.

For another domain, ordinary objects can define domain-specific matchers through
`match(subject)`.

This preserves the prototype/delegation model described in
[chapter 02](02-objects-delegation-and-composition.md): matching adds syntax and
standard pattern families, but not a parallel class/type institution.

Likewise, ordinary value matching continues to use the equality behavior
described in
[chapter 05](05-values-identity-equality-and-collections.md).

## A composed example

The useful surface becomes clearest when features are combined.

Assume `message` is already bound to a normal standard Map:

```protos
message match {
    case %{
        "kind": "data",
        "payload": [@head, ...@middle, @last],
        ...@metadata
    } when head != last => {
        Array(head, middle, last, metadata)
    }

    case %{ "kind": "ping" } => "pong"

    case _ => null
}
```

Read this in stages:

1. evaluate `message` once;
2. try the first Map pattern;
3. require the `"kind"` association to match the ordinary value pattern
   `"data"`;
4. require `"payload"` to contain a standard Array matching the nested
   prefix/remainder/suffix structure;
5. bind `head`, the fresh frozen remainder Array `middle`, and `last`;
6. bind `metadata` to the fresh frozen Map of unselected top-level associations;
7. evaluate the guard with those bindings;
8. if the guard is `true`, return the body result;
9. if the pattern mismatches or the guard is `false`, try the next arm;
10. eventually `_` provides an explicit catch-all.

There is no hidden subject-side deconstruction request and no implicit rollback
between these steps.

## A second composed example with an opaque matcher

The retained conformance surface also covers an opaque matcher whose positional
captures become visible to a guard:

```protos
matcher: {
    match: (subject) => {
        Array(subject, 10, 20)
    }
}

marker: {}

marker match {
    case matcher captures(first, ...rest)
        when (first === marker) && (rest.size() == 2) => {
        (rest[0] == 10) && (rest[1] == 20)
    }

    case _ => false
}
```

The matcher returns three positional captures. `captures(first, ...rest)` binds
the first directly and places the remaining two in the ordinary rest Array.
Those same bindings are visible to the guard and, if the guard accepts the arm,
to the body.

The important boundary is the matcher's public result. The source-side
`captures(...)` interface names positions for the consumer; it does not change
how the matcher recognizes `marker`.

## Practical checklist

When writing a matching expression, ask:

1. **What is the subject?** It is evaluated once.
2. **What object owns recognition?** The pattern, through `match(subject)`.
3. **Could this matcher return captures?** If opaque, name them explicitly with
   `captures(...)`.
4. **Are fixed binding names unique?** Duplicate fixed binders are invalid.
5. **If using OR, do all successful alternatives expose the same logical binding
   interface?**
6. **If using a dynamic capture rest, is it terminal in the complete logical
   binding order?**
7. **Is an Array pattern really intended for standard Array state?**
8. **Is a Map pattern intentionally open, exact, or capturing a remainder?**
9. **Does every guard return canonical `true` or `false` normally?**
10. **Is a catch-all wanted?** Without one, reachable terminal no-selection is an
    ordinary Error.
11. **Did an unguarded `_` or `@name` make later source structurally
    unreachable?**
12. **Does a Closure belong inside a guard?** Group or nest it so `=>` ownership
    is explicit.

## Implementation-backed examples

The syntax used in this chapter is not hypothetical. Representative forms are
covered by the retained parser and conformance material published with I038,
including:

- [`ProtosParserMatchTest`](../../src/test/java/com/guillermomolina/protos/parser/ProtosParserMatchTest.java)
  for the outer envelope, aliases, `captures(...)`, Array/Map forms, guards,
  D100 delimiter ownership, binding linearity, OR-interface checks, and
  structural unreachability;
- [`binder-wildcard.protos`](../../protos/tests/conformance/matching/binder-wildcard.protos)
  for binder/wildcard behavior;
- [`array-remainder-bindings.protos`](../../protos/tests/conformance/matching/array-remainder-bindings.protos)
  for Array remainder binding;
- [`or-same-subject.protos`](../../protos/tests/conformance/matching/or-same-subject.protos)
  for ordered OR subject behavior;
- [`or-dynamic-captures.protos`](../../protos/tests/conformance/matching/or-dynamic-captures.protos)
  for dynamic capture interfaces across OR alternatives; and
- [`guard-dynamic-rest-visible.protos`](../../protos/tests/conformance/matching/guard-dynamic-rest-visible.protos)
  for guard visibility of dynamic capture-rest bindings.

Those files are conformance evidence, not a second specification. If they ever
disagree with the normative specification, the specification remains
authoritative.
