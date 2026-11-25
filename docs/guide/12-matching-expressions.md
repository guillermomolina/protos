# Protocol-First Matching and Case Selection

> **Status:** Non-normative programming guide
>
> **Primary normative owners:** `spec/semantics/MATCHING.md` and
> `spec/PROTOS_GRAMMAR.md`; Closure behavior is owned by
> `spec/semantics/CALLABLES.md`, and Map construction and identity by
> `spec/semantics/VALUES_AND_COLLECTIONS.md`

## Compare a value and select a case

Three related forms serve different purposes:

- `pattern.match(subject)` recognizes a subject through the matcher protocol.
- `match(subject) { ... }` is idiomatic multi-way selection among ordered matchers.
- `subject.caseOf(cases)` is the explicit selection protocol, useful with an
  existing cases Map or existing callable actions.

Start with constant selection, then try structural matchers once you understand
matcher outcomes and positional captures.

## Understand the model

Matching in current Protos uses ordinary protocols. Contextual `match` supplies
selection sugar over those protocols; it does not introduce a dedicated pattern
language or a second matching mechanism.

The core idea is:

```text
pattern.match(subject)
```

The matcher is the receiver. The subject is the argument.

There is no postfix `subject match { ... }` expression, no `case` grammar, no
matching-only binders, wildcard, guards, OR-pattern institution, exact/remainder
pattern syntax, or `captures(...)` source form in Core v0.1.

The words `case`, `when`, `exact`, and `captures` are ordinary names. `match`
is also an ordinary name, except in the one exact contextual
`match(subject) { ... }` selection form described below, which is sugar over
the ordinary `caseOf` message.

## The root matcher protocol

Every ordinary object inherits the standard root behavior:

```text
Object.match(subject) -> this == subject
```

`Object.match` performs exactly one ordinary equality send with the matcher as
the equality receiver and returns that result unchanged.

For ordinary values this means:

```protos
42.match(42)
"ready".match(status)
```

uses the same customizable equality model as ordinary `==`. Matching does not
introduce a second equality relation.

A domain object may define or override `match(subject)` like any other method:

```protos
Positive: {
    match: (subject) => subject > 0
}

Positive.match(12)
```

No registry, type declaration, pattern class, or compiler hook is required.

## Matcher results

A matching consumer recognizes exactly these normal outcomes:

```text
false                     mismatch
true                      success with zero captures
[one, or, more, values]   success with positional captures
```

The capture carrier must be a **non-empty standard Array**.

An empty Array is not another spelling for zero-capture success. Other normally
returned values such as `null`, Numbers, Strings, arbitrary objects, or Futures
are invalid matcher outcomes when consumed by structural matching or `caseOf`
and signal ordinary Error.

Captured Arrays remain one captured value; capture composition does not
recursively flatten nested collections.

## `Any` and `Capture`

Core initially provides exactly two standard helper matchers:

```protos
Any.match(subject)
Capture.match(subject)
```

`Any.match(subject)` always returns canonical `true`.
`Capture.match(subject)` returns `[subject]`, preserving the exact subject
reference.

`Any` and `Capture` are ordinary standard objects, not keywords.

## Structural Array matching

A standard Array is itself a structural matcher:

```protos
matcher: [1, Capture, Any]
subject: [1, "payload", 99]

result: matcher.match(subject)
```

`Array.match` is exact-length matching.

For one attempt, Protos shallowly observes both matcher and subject Arrays before
invoking any child matcher. Reached child matchers run exactly once from left to
right:

```text
matcherElement.match(subjectElement)
```

A child `false` fails immediately. `true` contributes no capture. A non-empty
standard Array contributes its outer elements to the positional capture
sequence.

If no child contributes captures, the result is canonical `true`; otherwise the
result is one new standard Array containing captures in child order.

Core v0.1 defines no Array remainder matcher.

## Structural Map matching

A normal standard Map is also a structural matcher:

```protos
matcher: Map()
matcher["kind"] = "data"
matcher["payload"] = Capture

subject: Map()
subject["kind"] = "data"
subject["payload"] = 42
subject["extra"] = "ignored"

result: matcher.match(subject)
```

`Map.match` is **open/subset matching**. Every matcher association must resolve
and match, while unrelated subject associations are ignored.

For one attempt, Protos establishes stable shallow observations of matcher
requirements and subject associations. Requirements are processed in matcher
insertion order.

Key selection uses the ordinary normal-Map law:

1. evaluate the query key's current `hash`;
2. consider same-hash subject associations in subject insertion order;
3. send query-side `queryKey == storedRepresentativeKey`;
4. select the first canonical-`true` result.

Key objects are not themselves interpreted as matchers during association
lookup.

All required subject associations are resolved and their mapped-value references
fixed **before the first nested value matcher runs**. Mapped-value matchers then
run in matcher insertion order.

Missing required associations yield canonical `false`. Child matcher outcomes
compose captures through the same `false` / `true` / non-empty-Array protocol.

Core v0.1 defines no exact-Map mode and no Map-remainder capture.

## Multi-way selection with `caseOf`

Multi-way selection is an ordinary message:

```protos
value.caseOf(cases)
```

`cases` is a normal standard Map whose keys are matchers and whose values are
callables:

```protos
cases: Map()

cases["ready"] = () => "can run"
cases[Capture] = (other) => other

status.caseOf(cases)
```

The cases Map is shallowly observed in insertion order when the attempt starts.
Later mutation does not rewrite that attempt.

Each reached matcher is asked once:

```text
matcher.match(value)
```

The result controls selection:

```text
false        continue
true         action()
[captures]   action(...captures)
```

The first success commits. Unreached matchers and actions are not eagerly
validated or invoked.

The selected callable's result is returned unchanged, including `null`. If no
case succeeds, `caseOf` signals one fresh ordinary Error.

Because the carrier is an ordinary normal Map, its normal unique-key,
hash/equality, and insertion-order semantics are part of the model.

<a id="contextual-match"></a>

## Contextual `match(subject) { ... }`

### Constant selection

For ordinary multi-way selection, the idiomatic spelling is the contextual
`match` expression:

```protos
result: match(value) {
    0: 11
    1: 23
    2: 37
    Any: 53
}
```

It is exactly the ordinary send:

```protos
result: value.caseOf(%{
    0: () => 11
    1: () => 23
    2: () => 37
    Any: () => 53
})
```

The table follows ordinary `%{ ... }` Map construction rules, without the `%`.
Each complete right-hand expression becomes an action. An explicit Closure
literal, even inside parentheses, is preserved. Every other expression becomes
the body of an ordinary parameterless lexical Closure. That is why a simple
action such as `0: 11` needs no written `() =>`. Classification is syntactic;
it never depends on the runtime type of the expression's value.

The subject is evaluated exactly once. The ordinary Map is constructed before
ordinary lookup of `caseOf` on that subject. Standard `caseOf` selects the first
successful matcher and returns the selected action's exact result.

### Explicit actions and captures

```protos
describe: (value) => {
    match(value) {
        ["data", Capture]: payload => payload
        ["sum", Capture, Capture]: (a, b) => a + b
        Any: null
    }
}
```

`describe(["data", 42])` returns `42`, `describe(["sum", 10, 20])` returns
`30`, and `describe(["unknown"])` returns `null`.

Matchers return `false`, `true`, or a non-empty standard Array of captures.
Captures become positional arguments to the selected Closure. Its arity,
defaults, rest parameter, and argument binding follow ordinary callable rules.
The parameter names belong to that Closure; no special pattern grammar creates
variables for them. `Any` and `Capture` are ordinary objects, not reserved words.

A parameterless action can ignore captures:

```protos
match(["data", 42]) {
    ["data", Capture]: "received"
}
```

The result is `"received"`; the implicit action does not need the payload.

### Closure literals, Closure values, and objects

```protos
handler: () => 5

match(1) { 1: handler }

match(1) { 1: handler() }

match(1) { 1: (() => 7) }

match(1) { 1: { x: 10 } }

match(1) {
    1: () => {
        x: 10
        x + 1
    }
}
```

In order, these five selections return:

1. The same Closure `handler`, without executing it: the implicit action
   evaluates the name and returns its value.
2. `5`: the implicit action invokes `handler()`.
3. `7`: the grouped explicit Closure is the action itself and is invoked.
4. An object with slot `x` equal to `10`. `{ x: 10 }` is an Object expression,
   not an implicit instruction block.
5. `11`: the explicit Closure executes its two-expression braced body.

Use an explicit Closure with a braced body for an action containing several
expressions. If an action must return a literal Closure as a value, keep an
outer action Closure, for example `1: () => (() => 7)`.

### Evaluation order and effects

```protos
log: ""

subject: () => {
    log = log + "s"
    2
}

key: (name, value) => {
    log = log + name
    value
}

result: match(subject()) {
    key("a", 1): log = log + "A"
    key("b", 2): log = log + "B"
    key("c", 3): log = log + "C"
}
```

Both `result` and `log` contain `"sabcB"`:

1. Evaluating the subject once appends `"s"` and produces `2`.
2. Map construction evaluates and defines all keys in source order, appending
   `"a"`, `"b"`, and `"c"`. Ordinary Map `hash`, equality, unique-key, and
   insertion-order rules apply.
3. Each action Closure is created during construction according to ordinary
   lexical capture rules. Its body does not run at creation.
4. Standard selection invokes the reached matchers in order: `1` fails and
   `2` succeeds. The matcher `3` is not invoked.
5. Only the selected action executes, appending `"B"`. Assignment returns the
   assigned String, so it is also the selection result.

Keys are eager even when their matchers are unreached. A later key expression
can produce effects or signal an Error before selection ever begins. This is
different from executing its matcher or action. Lazy action execution is a
guarantee of standard `caseOf`.

### First success and priority

Put specific matchers before broad ones:

```protos
match(["data", 42]) {
    ["data", Capture]: payload => payload
    Any: null
}
```

`Any` at the beginning would succeed immediately and prevent later matchers
from running. That is ordinary first-success selection, not a compilation
error or a statically unreachable branch. There is no static exhaustiveness,
fall-through, matching-specific guard syntax, or implicit Future adoption.
The contextual spelling makes no performance promise; shared specialization
is separate work under PERF044.

### Ordinary Errors

Each of the following snippets signals an Error; they are failure examples,
not programs that finish normally without an Error handler:

```protos
match(1) {
    2: "two"
}
```

No matcher succeeds, so standard `caseOf` signals its no-selection Error.

```protos
match(1) {
    1: "one"
    1: "duplicate"
}
```

The duplicate key signals an Error during ordinary Map construction, before
`caseOf` lookup or selection.

```protos
match(1) {}
```

An empty table is syntactically valid and constructs an empty Map. Standard
`caseOf` then signals its no-selection Error. Use the ordinary
[Error-handling mechanism](07-errors-handlers-ensure-and-resource-lifetime.md)
when a program needs to recover from these failures.

### Contextual recognition and ordinary calls

Recognition requires all of the following:

- a bare, ungrouped identifier `match` as the callee;
- exactly one explicit argument, which is not spread;
- an attached opening `{` after `)`, with no intervening logical NEWLINE.

The table uses ordinary Map construction entries, including empty tables,
newlines, and semicolon separators. Once the contextual header is recognized,
a malformed table is a syntax error: it is never retried as an ordinary
trailing Closure.

These remain ordinary calls:

```protos
matcher.match(subject)
```

```protos
(match)(value) { body }
```

```protos
match(value, () => { body })
```

So do `match(value)` without an attached table, member-receiver calls,
multiple-argument calls, and calls with spread arguments. Grouping the callee
is the escape for an ordinary trailing Closure call. The approved source
incompatibility is precise: an earlier ordinary call using bare `match`, one
argument, and an attached trailing Closure may now need `(match)(value) { body }`.

The contextual subject is an ordinary expression, before named-argument
reinterpretation:

```protos
match(flag: true) {
    true: 11
    Any: 23
}
```

This creates slot `flag` with value `true` while evaluating the subject and
selects `11`. `flag: true` is not a named-argument association here.

### Expression composition

A selection can nest in another selection:

```protos
match(0) {
    0: match(1) {
        1: "nested"
        Any: "inner fallback"
    }
    Any: "outer fallback"
}
```

This returns `"nested"`. Ordinary postfix operations apply to the complete
selected result, without additional grouping:

```protos
match(1) { 1: { name: "selected" } }.name

adder: (n) => n + 1
match(1) { 1: adder }(41)

match(1) { 1: [10, 20] }[1]
```

The results are `"selected"`, `42`, and `20`. The call suffix invokes the
Closure returned as a value by the implicit action; it does not change that
action's syntactic classification.

### Custom caseOf

```protos
subject: {
    caseOf: (cases) => cases
}

received: match(subject) {
    1: 2
    3: x => x
}
```

`received` is the ordinary Map supplied to that custom method. No standard
selection takes place. Calling `received[1]()` produces `2`; calling
`received[3](4)` produces `4`.

A custom `caseOf` may retain the Map, return it, or invoke its actions later.
Those Closures preserve ordinary lexical capture by reference. The guarantee
that only one selected action executes belongs to standard `caseOf`, not to
an arbitrary custom implementation.

### When to use explicit caseOf

| Situation | Why keep the explicit protocol? |
|---|---|
| Teaching the protocol | Expose the ordinary message and Map carrier |
| Existing cases Map | Pass the table directly without rebuilding it |
| Dynamically constructed Map | Preserve its construction and chosen actions |
| Existing callable actions | Supply those callables directly for invocation |
| Custom protocol implementation | Make forwarding or protocol behavior explicit |
| Equivalence and regression tests | Preserve evidence for the canonical mechanism |

These forms are not equivalent in general:

```protos
value.caseOf(%{
    Any: handler
})
```

```protos
match(value) {
    Any: handler
}
```

Standard `caseOf` in the first form invokes the existing callable `handler`.
In the second, it invokes an implicit parameterless action that evaluates and
returns `handler`. Never replace these mechanically.

## Guard-like behavior needs no special syntax

Core v0.1 has no matching-specific `when` syntax. A matcher simply implements the
predicate it needs:

```protos
Positive: {
    match: (subject) => subject > 0
}
```

A matcher that needs to capture after checking a condition can return a
non-empty Array on success and `false` otherwise. The protocol remains
`matcher.match(subject)`; no separate guard phase is required.

## Matching remains ordinary Protos

Useful boundaries:

- matcher authority is on the receiver of `match`;
- equality remains ordinary `==`;
- Array and Map structural behavior belongs to those standard collection
  protocols;
- `caseOf` is an ordinary Object message using an ordinary Map carrier;
- Errors, effects, control transfer, and suspension follow ordinary execution;
- matching uses ordinary messages, without a dedicated pattern language.

## Unsupported forms

Use ordinary calls rather than these unsupported forms:

```text
subject match { ... }
case ...
when ...
@name
_
Array/Map pattern grammar
Array/Map remainders
exact Map patterns
OR patterns
captures(...)
```

Those forms are not supported in Core v0.1. Use ordinary `match` and `caseOf`
messages, or the contextual `match(subject) { ... }` selection sugar, instead.

## Practical checklist

1. Identify the matcher object.
2. Send `matcher.match(subject)`.
3. Expect only `false`, `true`, or a non-empty standard Array from matcher
   outcomes consumed by matching.
4. Use `Capture` for one positional capture.
5. Use `Any` for unconditional zero-capture success.
6. Use Arrays for exact-length structural matching.
7. Use Maps for open/subset structural matching.
8. Use `match(value) { ... }`, or `value.caseOf(cases)` directly, for ordered
   multi-way selection.
9. Remember that Map keys still follow ordinary Map `hash` / `==` semantics.
10. Treat `caseOf` no-selection as Error unless a case explicitly succeeds.

## Normative references

See [Matching](../../spec/semantics/MATCHING.md) for matcher outcomes,
structural projection, and case selection, and the
[grammar](../../spec/PROTOS_GRAMMAR.md) for accepted source forms.

Continue with the [contextual match tutorial](../../protos/tutorials/04-control-flow/05-contextual-match.protos)
and the cookbook examples for [status dispatch](../../protos/examples/control-flow/status-dispatch.protos),
[structural messages](../../protos/examples/control-flow/structural-dispatch.protos),
and [explicit case selection](../../protos/examples/control-flow/custom-case-selection.protos).

[Back to the guide](README.md)
