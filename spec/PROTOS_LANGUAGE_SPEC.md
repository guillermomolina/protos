# Core Language Specification v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04
Normative I/O-domain semantics are defined in `io/IO_CORE.md`.

Normative semantic-domain ownership is modularized under `semantics/`: `OBJECT_MODEL.md`, `EXECUTION_AND_CONTROL.md`, `CALLABLES.md`, `MODULES.md`, `ERRORS.md`, and `VALUES_AND_COLLECTIONS.md`. Compatibility headings retained in this document are navigation only.


Normative concurrency-domain semantics are defined by the modular specifications
under `concurrency/`. Non-normative unresolved concurrency design notes live in
`../docs/design/CONCURRENCY_DESIGN.md` and are not a source of observable behavior.
## Language Name

The language defined by this specification is named **Protos**.

## 1. Principles

The language is an object-oriented language based exclusively on **prototypes and delegation**.

There are no classes.

Every observable value is an object. This includes numbers, strings, booleans, closures, errors, futures, execution contexts, and `null`.

An implementation may internally represent some objects using immediate values, tagged pointers, specialized memory layouts, or other optimizations, provided that these optimizations are not observable through the language semantics.

The language has a uniform execution model. There is no fundamental semantic distinction between "global code", "local variables", and "properties". Execution always takes place within contexts, and contexts are themselves objects.

The language favors objects and messages over keywords and special syntactic constructs.

## 1.1 Identifier Syntax and Reserved Words

The primary normative owner of identifier lexing, Unicode/NFC requirements, reserved words, and contextual member-name syntax is `PROTOS_GRAMMAR.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## 1.2 Dynamic Typing

Core v0.1 is dynamically typed.

Slots and parameters do not carry mandatory static type declarations. A slot may hold objects with different behavior over time, subject only to the normal object-state and assignment rules.

Message validity is determined dynamically by receiver behavior and delegation. The language does not introduce overload resolution by declared argument type.

Implementations and tools may infer types, specialize code, or expose optional analysis, but such information must not change observable language semantics.

## 2. Objects

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 3. Slot Creation and Modification

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 4. Execution Context

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## Module Contexts and Top-Level Bindings

The primary normative contract formerly contained here has moved to `semantics/MODULES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 5. `this`

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 6. Unqualified Lookup

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 7. Unqualified Assignment

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 8. `super`

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 8.1 Evaluation Order

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 9. Closures

Closure syntax and mandatory syntactic desugaring are owned by
`PROTOS_GRAMMAR.md`. Closure value/capture/invocation semantics are owned by
`semantics/CALLABLES.md`. This heading is a compatibility/navigation anchor and
is not an independent normative owner.
## 10. Closures and Methods

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 11. Extracted Methods

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 12. Closures and `super`

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 13. Return Semantics

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 14. Return from Escaped Closures

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 15. `null`

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 16. Booleans

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 17. Iteration and Loops

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 18. Trailing Closures

Trailing-closure syntax, attachment, and mandatory desugaring are owned by
`PROTOS_GRAMMAR.md`; the resulting Closure obeys
`semantics/CALLABLES.md`. This heading is a compatibility/navigation anchor and
is not an independent normative owner.
## 19. Separators, Line Breaks, and Comments

The primary normative owner of whitespace, comments, NEWLINE tokens, separators, and continuation grammar is `PROTOS_GRAMMAR.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## 20. Object Composition

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 21. Equality and Identity

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 21.1 Custom Symbolic Binary Operators

Custom symbolic operator lexing, parsing, precedence, associativity, and
mandatory lowering are owned by `PROTOS_GRAMMAR.md`. The lowered ordinary
message send obeys the applicable callable/object semantics. This heading is a
compatibility/navigation anchor and is not an independent normative owner.
## 22. Open Objects

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 23. Closed Objects

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 24. Frozen Objects

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 25. Errors

The primary normative contract formerly contained here has moved to `semantics/ERRORS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 26. Futures

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility/navigation anchor; it defines no independent duplicate contract.

## 27. Asynchronous Execution

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility/navigation anchor; it defines no independent duplicate contract.

## 28. Future State, Resolution, Failure, and Adoption

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility/navigation anchor; it defines no independent duplicate contract.

## 29. Obtaining a Future's Value

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility/navigation anchor; it defines no independent duplicate contract.

## 30. Future Composition

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility/navigation anchor; it defines no independent duplicate contract.

## 31. Structured Concurrency

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility/navigation anchor; it defines no independent duplicate contract.

## 32. Primitives

Expressing an operation through messages does not require its entire implementation to be written in the language.

For example, arithmetic messages may ultimately execute native arithmetic primitives.

Likewise:

```js
closure.future()
```

may ultimately invoke a scheduler primitive.

A primitive is an implementation mechanism, not an exception to the object model.

## 33. Constructs Outside the Semantic Core

The core does not require these as semantic primitives:

```text
class
extends
new

var
let
const

function

async
await

for

undefined

try
catch
throw
finally
```

`return`, `if`, `else`, and `while` may eventually exist as syntactic sugar.

## 34. Core Language Invariants

```text
Everything is an object.

The language is dynamically typed.

There are no classes.

Object is the unique root object and has no delegation parent.

Every other object has exactly one delegation parent.

Every delegation chain terminates at Object.

Every object may serve as a delegation parent.

An object's delegation parent cannot change after creation.

Reads may delegate.

Writes never delegate.

: creates.
= modifies.

null is the only absence value.

A missing slot is not null; it is an error.

Execution contexts are objects; their standard prototype is `Context`.

Closures capture lexical contexts by reference.

Methods dynamically receive their receiver.

Inner closures capture that receiver.

Extracted methods remain bound to their receiver.

super preserves both the receiver and the lookup origin.

^ performs a non-local return.

Errors are objects and are signaled.

Futures are objects.

Asynchrony is a property of an execution,
not a separate category of function.

Objects may be closed or frozen.

Horizontal composition does not modify the delegation chain.

Top-level bindings are slots of a module execution context.

There is no special global-variable category.

Modules do not implicitly share mutable global state.

Actors share no mutable Protos state; any Protos object physically shared between Actors through the standard prelude is semantically immutable for the duration of that sharing.
```

## Conditional Protocol and Truthiness

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Error Signaling and Handling

The primary normative owner of Core Error signaling, dynamic handlers, matching, unwinding, and non-resumable failure semantics is `semantics/ERRORS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Module Loading, Identity, and Cycles

The primary normative contract formerly contained here has moved to `semantics/MODULES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## Indexed Access Syntax

Indexed-expression grammar and mandatory lowering are owned by `PROTOS_GRAMMAR.md`; standard collection `at`/`atPut` behavior is owned by `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Standard Array Indexed Semantics

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## Standard Array Parallel Operations

The primary normative owner of standard isolated parallel Array operations is `concurrency/PARALLEL_EXECUTION.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Invocation Arguments, Defaults, Rest, and Spread

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Polymorphic Invocation and Object Construction

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Contextual Meaning of `...`

The primary normative owner of the context-sensitive grammar and mandatory lowering of `...` is `PROTOS_GRAMMAR.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Resource Cleanup and `ensure`

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Numeric Equality Across Families

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Float Special Values and Identity

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Float Signed Zero Semantics

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Parameter Name Uniqueness

The primary normative contract formerly contained here has moved to `PROTOS_GRAMMAR.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Numeric Model

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Text, Bytes, and Character Encodings

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Prefix Operators and Protocol-Based Negation

The primary normative owner of prefix-operator syntax and mandatory message-send lowering is `PROTOS_GRAMMAR.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## String Literal Semantics

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## String Indexing, Mutability, and Encoded Representations

`String` is an immutable Unicode text value.

Core String indexing and size operate on Unicode grapheme clusters rather than bytes, encoding code units, or raw Unicode code points:

```js
text.size
text[0]
```

conceptually correspond to grapheme-count and grapheme-at-index operations.

This keeps ordinary text operations aligned with user-perceived characters.

Core v0.1 does not standardize separate `String.graphemes()` or
`String.codePoints()` convenience protocols. Libraries may provide ordinary
messages with those names, and a later standard may define explicit lower-level
text views, but portable Core code cannot rely on either protocol.

Explicit conversion between text and encoded bytes remains standardized through
the Encoding-object contract defined by the I/O model, for example:

```js
UTF8.encode(text)
```

Because `String` is immutable, operations that conceptually modify text produce a new `String`:

```js
text.uppercase()
text.replace("a", "b")
text + otherString
```

Here `otherString` denotes another semantic String value. The standard
String-family `+` operation does not accept an arbitrary non-String object.

Efficient incremental text construction may be provided by separate mutable library objects or buffer-oriented abstractions. Core v0.1 does not standardize a `StringBuilder` binding, prototype, constructor, or protocol.

`Bytes` is a mutable raw byte sequence by default. Indexed access therefore naturally follows the existing protocol:

```js
bytes[i]          // bytes.at(i)
bytes[i] = value  // bytes.atPut(i, value)
```

Encoded textual representations such as UTF-8 data, UTF-16 data, C strings, memory-backed strings, or similar objects may be first-class values with their own protocols.

Their mutability is not globally fixed by Core. An encoded representation may expose `atPut` if mutation is meaningful and supported, or omit it if the representation is immutable or read-only.

Therefore mutability is expressed behaviorally through supported messages rather than through a universal collection mutability flag.

Examples of possible first-class representations include:

```text
UTF8EncodedString
UTF16EncodedString
CString
MappedText
```

These names are illustrative; Core v0.1 does not require this exact library taxonomy.


### Exact standard String indexing semantics

Core v0.1 uses the **default extended grapheme-cluster** boundary rules of
Unicode Standard Annex #29, Unicode Text Segmentation, as synchronized with
**The Unicode Standard, Version 17.0.0** (UAX #29 revision 47), for standard
`String.size` and `String.at`.

These are the untailored default Unicode rules. A host locale, ICU version,
operating-system text service, rendering engine, editor convention, language-
specific tailoring, or later Unicode release must not change Core-visible
String boundaries. Implementations may use such facilities only when they
produce exactly the Unicode 17.0.0 default extended-grapheme-cluster result.

For a String whose exact semantic value is the scalar sequence `S`, let:

```text
G = defaultExtendedGraphemeClusters17(S)
```

where concatenating the scalar sequences of the elements of `G`, in order,
reconstructs `S` exactly. Segmentation does not normalize, case-fold, replace,
or otherwise alter the String's scalar values.

The standard `size` result is:

```text
String.size -> semantic Integer equal to length(G)
```

The result is an exact semantic `Integer`; Core does not require a fixed-width
Integer family or host-sized representation.

The standard indexed read:

```js
text.at(index)
text[index]
```

requires `index` to be a semantic `Integer`. Any Integer family is accepted
according to its mathematical Integer value. No Float-to-Integer conversion,
String parsing, truncation, wrapping, modulo reduction, or host-sized coercion
is performed.

The index must satisfy:

```text
0 <= index < text.size
```

Otherwise the operation signals an `Error`.

A successful read returns a `String` whose semantic scalar sequence is exactly
the scalar subsequence forming `G[index]`. The returned String is not normalized
or rewritten. Therefore indexing preserves the exact-scalar String model even
when the selected grapheme contains several scalars.

For example, if:

```text
S = U+0065 U+0301
```

and Unicode 17.0.0 default extended-grapheme segmentation treats that sequence
as one cluster, then `text.size` is `1` and `text[0]` is the two-scalar String
`U+0065 U+0301`, not an implicitly normalized U+00E9 String.

Standard String indexing behavior applies only to an original receiver that is
a semantic String value. Delegating to a String value or String prototype does
not confer String semantic membership. An incompatible receiver that invokes
the standard behavior signals an `Error` under the existing semantic-family
receiver rule.

`String` is immutable and Core defines no standard indexed mutation behavior
that changes a String in place. Bracket assignment does not acquire a hidden
String mutation primitive merely because bracket-read syntax is supported.
User-defined objects remain free to define their own ordinary `atPut` protocol.

This rule defines only the already-visible `String.size` and `String.at`
semantics. It does not standardize the illustrative `graphemes()`,
`codePoints()`, normalization, locale-sensitive segmentation, collation, or
text-editing protocols mentioned elsewhere.



### Standard String concatenation with `+`

The standard String-family behavior for binary `+` concatenates two semantic
String values.

```js
"hel" + "lo"     // "hello"
```

The original receiver must be a semantic String value, and the right operand
must also be a semantic String value. No Number, Boolean, `null`, arbitrary
object, prototype, or other value is implicitly converted to String.

The result is the String whose Unicode-scalar sequence is exactly the receiver's
scalar sequence followed by the right operand's scalar sequence. Concatenation
performs no Unicode normalization, grapheme resegmentation beyond that implied
later by ordinary String `size` / `at`, locale transformation, encoding, or
decoding.

String values are immutable, so concatenation mutates neither operand. The
result obeys ordinary String value identity and equality. Consequently, when
the concatenated scalar sequence equals an existing String value's scalar
sequence, `===` observes the same String value semantics:

```js
("hel" + "lo") === "hello"   // true
```

The standard operation performs no user callback, textual coercion, `hash`,
`==`, Encoding operation, or hidden suspension.

This is a standard String-family specialization of the ordinary `+` message.
Ordinary lookup and overriding rules remain unchanged. Merely delegating to a
String value or String-family prototype does not make an incompatible receiver
a semantic String.

## Maps, Hashing, and Key Equality

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Future Cancellation

Future cancellation is explicit and cooperative.

```js
future.cancel()
```

`cancel()` requests cancellation; it does not forcibly terminate an arbitrary running activation.

A task observes cancellation only at the portable cancellation boundaries defined
for Future-producing asynchronous execution above, including mandatory explicit
suspension boundaries and normatively cancellation-aware operations. Runtime,
interpreter, JIT, GC, allocation, call, and loop checkpoints are not additional
language-level cancellation boundaries merely because an implementation uses them
internally. When cancellation becomes effective, the task exits through the normal
unwind machinery so that `ensure` cleanup executes.

A cancelled Future completes in the cancelled state. Observing its result:

```js
future.value()
```

signals the standard cancellation condition/error object, conceptually `Cancelled`.

Core v0.1 does not define unsafe asynchronous thread-kill semantics.

## Future Failure and Dynamic Error Context

The primary normative owner of Future failure recording, observation, and dynamic-handler boundaries is `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Concurrency Memory Semantics

The primary normative owner of the applicable Actor-local task, Actor-isolation, completion-visibility, and isolated-P boundary contracts is `concurrency/FUTURES_AND_TASKS.md`, `concurrency/ACTORS.md`, and `concurrency/PARALLEL_EXECUTION.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Core Reflection

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.
