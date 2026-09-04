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

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Maps, Hashing, and Key Equality

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Future Cancellation

The primary normative owner of Future cancellation state, `cancel()`, cancellation
boundaries, unwind effects, and cancelled-result observation is
`concurrency/FUTURES_AND_TASKS.md`; standard Error construction/category semantics
are owned by `semantics/ERRORS.md`. This heading remains as a
compatibility/navigation anchor and is not an independent normative owner.

## Future Failure and Dynamic Error Context

The primary normative owner of Future failure recording, observation, and dynamic-handler boundaries is `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Concurrency Memory Semantics

The primary normative owner of the applicable Actor-local task, Actor-isolation, completion-visibility, and isolated-P boundary contracts is `concurrency/FUTURES_AND_TASKS.md`, `concurrency/ACTORS.md`, and `concurrency/PARALLEL_EXECUTION.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.

## Core Reflection

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility/navigation anchor and is not an independent normative owner.
