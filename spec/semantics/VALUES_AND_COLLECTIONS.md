# Protos Values and Collections v0.1

Language version: 0.1
Document revision: 327
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Core immutable value families, equality/identity, indexed access, and standard collection/value protocols.

The material below is migrated without intended semantic change from `../PROTOS_LANGUAGE_SPEC.md`. Legacy section titles and numbering are retained so existing references remain understandable.

## 15. `null`

Exactly one object represents absence:

```js
null
```

There is no `undefined`.

```js
x: null
```

means the slot `x` exists and contains `null`.

A failed lookup signals an error. It does not evaluate to `null`.

`null` is a singleton object and may respond to messages like any other object.
## 16. Booleans

`true` and `false` are singleton objects.

Conditional control flow is semantically implemented through messages sent to these objects.

Conceptually:

```js
condition.ifTrue() {
    ...
}

condition.ifFalse() {
    ...
}
```

Closures provide lazy evaluation.

Possible `if`/`else` syntax may exist as sugar, but does not define the fundamental semantics.
## 17. Iteration and Loops

No primitive `for` construct is required.

```js
users.each((user) => {
    print(user.name)
})

users.map((user) => {
    user.name
})

1.to(10).each((i) => {
    print(i)
})
```

A `while` operation requires a reevaluated condition and therefore semantically operates on a closure:

```js
(() => i < 10).while() {
    i = i + 1
}
```

A future `while (...) { ... }` form may be syntactic sugar.
## 18. Trailing Closures

The parentheses of a call always contain call arguments. A call may be followed by one trailing closure, which is appended as the final argument of the invocation.

A trailing closure is always parameterless:

```js
transaction(options) {
    work()
}
```

is exactly equivalent to:

```js
transaction(
    options,
    () => {
        work()
    }
)
```

A trailing closure never has its own parameter list. Core v0.1 provides no parameterized trailing-closure syntax: the former form `foo(args...) (params...) { body }` is not part of Core v0.1, and `items.each() (item) { print(item) }` is not trailing-closure syntax. Such forms do not become two adjacent same-line expressions merely because parameterized trailing closures were removed: whitespace alone does not separate expressions, and no implicit adjacency-based expression separation exists.

A closure that requires parameters is passed explicitly as an ordinary closure expression in ordinary call-argument position:

```js
items.each((item) => {
    print(item)
})
```

Likewise, instead of a parameterized trailing closure after `collection.reduce(initial)`, write an explicit closure argument according to ordinary call syntax:

```js
collection.reduce(initial, (acc, item) => {
    ...
})
```

The exact position of the closure among a particular API's arguments is defined by that API. The trailing-closure sugar only appends a parameterless closure as the final argument.

The call parentheses are never reinterpreted as the parameter list of a trailing closure. Therefore:

```js
items.each(item) {
    print(item)
}
```

means:

```js
items.each(
    item,
    () => {
        print(item)
    }
)
```

The `item` inside the call parentheses is an ordinary explicit call argument. It is not a parameter declaration for the trailing closure.

A parameter list exists only where ordinary closure syntax requires it, before `=>`. `(x)` is always an ordinary parenthesized expression, and `(x) => { body }` is always an ordinary closure expression; there is no third interpretation of `(x)` as the parameter declaration of a trailing closure. This resolves issue B6 structurally: the parser needs no special lookahead to distinguish `(x)` from a trailing-closure parameter list, no parameter list is inferred from a parenthesized expression, and no semantic/type-based interpretation decides whether parentheses contain closure parameters. When a Closure has exactly one simple parameter, its parentheses may be omitted (see Closures); the result, such as `items.each(item => print(item))`, is an ordinary explicit Closure in ordinary call-argument position and never a trailing closure.

A trailing closure introduces no new runtime value kind: it is syntactic sugar for an ordinary Closure appended as the final call argument. Trailing-closure syntax does not alter closure semantics.

A trailing closure is attached only when no logical `NEWLINE` token intervenes between the completed call and the closure body. The call is complete when its `argument-list` ends; a `NEWLINE` token at that point acts as an expression separator under the complete-expression newline rule (see Separators, Line Breaks, and Comments). Therefore:

```js
foo() {
    body
}
```

is a call with a parameterless trailing closure, while:

```js
foo()
{
    body
}
```

does not attach the braces to `foo()` as a trailing closure. `foo()` is syntactically complete, so the logical `NEWLINE` after it separates expressions. `{` is not a complete-before-newline continuation exception: the only such exception remains the leading structural member-access `.` rule, and it does not generalize to `{`. What the separated `{ ... }` may mean, if anything, is governed by the ordinary grammar independently; the normative claim here is only that it is not attached as a trailing closure to the preceding call. This closes issue B7.

Blank lines and semicolons do not attach a trailing closure: repeated separating `NEWLINE` tokens have the same effect as one separating `NEWLINE`, and `;` is an expression separator, so `foo(); { body }` is not a trailing closure on `foo()`.

Indentation plays no role in the decision: the rule concerns logical `NEWLINE` tokens, not physical source formatting. The two forms:

```js
foo()
{
    body
}
```

and:

```js
foo()
    {
        body
    }
```

are equivalent with respect to the newline rule; both contain a separating logical `NEWLINE` after the completed call, so neither attaches the braces as a trailing closure. Horizontal whitespace between the completed call and the closure body is permitted: `foo()    { body }` remains valid trailing-closure syntax.

Comments follow the existing lexical rules. A block comment behaves as whitespace and consumes any logical newlines inside it without producing `NEWLINE` tokens, so `foo() /* comment */ { body }` and:

```js
foo() /*
    comment
*/ {
    body
}
```

both attach. A line comment does not consume its terminating logical newline: that newline is tokenized normally, so `foo() // comment` followed by `{ body }` on the next source line does not attach. No special comment-sensitive trailing-closure rule exists; the result follows entirely from tokenization.

A trailing closure is therefore permitted only when the parser sees the closure body as part of the same continuing token sequence after the completed call suffix, with no intervening `NEWLINE` token. A valid trailing closure remains a parameterless closure appended as the final call argument; this revision does not restore parameterized trailing closures.
## 21. Equality and Identity

`===` represents **semantic identity** and is not customizable. Its result must not depend on allocation, interning, boxing, tagged values, or any other implementation strategy.

For ordinary identity-bearing objects, identity means that both expressions denote the same individual object:

```js
a: { x: 1 }
b: { x: 1 }
c: a

a === b  // false
a === c  // true
```

Some Core values have **value identity**: their semantic value determines
identity rather than a particular allocation. The Core v0.1 value-identity set
is closed and consists exactly of:

- numeric values in the `Number` family;
- `String` values;
- the canonical Boolean values `true` and `false`;
- the canonical `null` value.

No implementation, host platform, optimization, standard-library extension, or
ordinary Protos program may add another Core v0.1 value-identity category.
Future language versions may extend this set only by an explicit normative
language change.

```js
1 === 1                    // true
"hello" === "hello"        // true
("hel" + "lo") === "hello" // true
true === true              // true
null === null              // true
```

Being immutable, closed, frozen, interned, canonicalized, structurally equal,
or backed by the same host representation does not by itself grant value
identity. Every object outside the closed set above has individual object
identity.

In particular, ordinary objects, closed or frozen ordinary objects, Closures,
Arrays, Maps, Futures, errors, execution contexts, module instances, and
standard prototype objects remain identity-bearing objects unless a normative
rule explicitly places the value itself in one of the closed value-identity
families above.

Delegation does not transfer value identity. An ordinary object whose parent is
a Number, String, Boolean, or `null` value remains an ordinary identity-bearing
object, as already required by the delegation rules.

`===` is non-overridable, so user code cannot opt an object into value identity
by defining equality behavior. `==` remains the customizable protocol for
semantic equality.


`Number` objects are immutable value objects.

`String` objects are immutable value objects. An operation on a String never changes that String in place; an operation that produces different text produces another String value. Implementations may freely share or intern String storage because such sharing cannot change observable identity semantics.

`true`, `false`, and `null` are canonical singleton values.

Ordinary mutable objects, closures, arrays and other identity-bearing objects retain individual object identity even when their contents happen to be equal. The exact collection model is specified separately.

`==` represents semantic equality and may be customized through object behavior.

The equality protocol has a strict result contract:

```text
==  -> true | false | error
!=  -> true | false | error
```

An implementation of `==` must return one of the canonical Boolean objects `true` or `false`, or signal an error. Returning any other object is an invalid equality result.

The same Boolean-result contract applies to the standard comparison operators:

```text
<   <=   >   >=
```

They return canonical `true` or `false`, or signal an error. The language defines no truthiness conversion for interpreting arbitrary comparison results. For built-in immutable value objects, `==` and `===` may naturally produce the same result, but they remain different operations: `==` is behavioral and customizable, while `===` is a non-overridable identity primitive.

Identity is never defined by comparing hash codes. A runtime may derive or cache hashes from identity where appropriate, but hash collisions cannot make distinct identity-bearing objects identical.
## 21.1 Custom Symbolic Binary Operators

### Custom Operator Lexical Alphabet

Custom symbolic binary operators are formed from the following operator characters:

```text
! $ % & * + - / < = > ? @ \ ^ | ~
```

The following punctuation is structural and is not part of the custom-operator alphabet:

```text
. : ; , ( ) { } [ ]
```

In particular, `.` is reserved for member access, `:` for slot creation, and `;` for explicit expression separation.

The lexer recognizes reserved and standard operator tokens before classifying a remaining valid symbolic sequence as a custom operator.

Reserved or standard symbolic tokens include:

```text
=>  =  ==  ===  !=  !==  <=  >=  &&  ||
+   -  *   /   %   <   >   !   ^
```

The exact one-character spellings `!` and `^` are reserved/standard tokens and are not custom binary selectors: `a ! b` and `a ^ b` are syntax errors. Their existing roles are unchanged wherever they appear: `!` lowers to `not()` as a prefix operator and `^` performs a non-local return. Symbolic token classification is purely lexical and independent of parser position: maximal munch first forms the longest valid spelling, which is classified as a reserved/standard token when it exactly matches a reserved/standard spelling and as `CUSTOM_OPERATOR` otherwise. The characters `!` and `^` remain members of the custom operator alphabet, so longer spellings containing them, such as `!!`, `^^`, `!^`, and `^!`, are `CUSTOM_OPERATOR` tokens and may be used as custom binary selectors, for example `a !! b` and `a ^^ b`.

A symbolic sequence composed from the operator alphabet that is not otherwise reserved or standard may be used as a custom binary selector, for example:

```js
a @ b
a |> b
a <=> b
a ~~ b
a ** b
```

The lexical alphabet is fixed by the language grammar. Modules, imports, runtime objects, or operator declarations cannot extend it.

The formal lexical definition of `custom-binary-operator` — its `operator-character` alphabet, `symbolic-operator-spelling` candidate form, maximal-munch formation, and reserved-spelling classification — is normative in the grammar's Custom Operator Lexing rules.


User-defined symbolic binary operators are permitted as ordinary message selectors.

A custom operator expression:

```js
a @ b
```

lowers to an ordinary receiver-based send of the symbolic selector to `a`, with `b` as its argument.

All custom binary operators have the same precedence relative to one another and associate left-to-right:

```js
a @ b |> c
```

means:

```js
(a @ b) |> c
```

Core v0.1 deliberately defines no implicit precedence relationship between custom binary operators and the standard operator groups. Mixing them without explicit grouping is therefore invalid:

```js
a + b @ c      // invalid
a @ b * c      // invalid
```

Parentheses make the intended grouping explicit:

```js
(a + b) @ c
a @ (b * c)
```

Modules, imports, declarations, or runtime mutation cannot change parser precedence.
## Indexed Access Syntax

Bracket indexing is syntactic sugar over ordinary message sends. Indexing is not a privileged runtime operation and is not restricted to arrays.

Slot/member access and indexed access are distinct mechanisms. Member syntax accesses the object's slot/delegation model: `object.name` performs ordinary slot lookup. Indexed syntax invokes the indexing protocol: `object[key]` lowers to `object.at(key)`. Indexed access is **not** dynamic slot access. In particular, `object["foo"]` is not defined to be equivalent to `object.foo`; the two expressions may return completely different values. An object does not automatically become indexable merely because it has slots — `object[key]` works only according to the `at` protocol implemented or inherited by that object.

Indexed read:

```js
receiver[index]
```

lowers conceptually to:

```js
receiver.at(index)
```

Indexed write:

```js
receiver[index] = value
```

lowers conceptually to:

```js
receiver.atPut(index, value)
```

The `=` in indexed assignment does **not** mean "modify an already-existing indexed entry". The syntax itself imposes no universal existence requirement on the key or index. Whether `atPut` creates a new indexed entry, replaces an existing one, extends a collection, requires an existing or in-range index, rejects the operation, or implements some other domain-specific behavior is defined by the receiver's `atPut` protocol. For example, a `Map` may define `map[key] = value` to create the key/value entry when the key is absent and replace its value when the key is already present, while an `Array` may require the index to be within a permitted range. User-defined objects may implement their own `at` / `atPut` behavior; the indexing syntax does not impose `Map` semantics on every indexable object.

Any object may support bracket syntax by implementing the corresponding messages.

Conversely, an indexable object remains an ordinary Protos object and may have ordinary slots, methods, delegation, and openness/frozen state in addition to indexed contents. Indexed contents are not automatically object slots, and a slot is not automatically indexed content. For example:

```js
map.description: "users"
map["description"] = user
```

may coexist and refer to entirely different things: the first operation concerns a slot of `map`, the second concerns `map`'s indexing protocol. Likewise, adding a slot named `foo` does not imply that `object["foo"]` will return that slot; only the receiver's `at` implementation determines the result. Indexability is protocol-based behavior, not a special object kind.

Indexed slot creation is forbidden. `:` is specifically the slot-creation operator and cannot be applied to an indexed target:

```js
object[index]: value     // syntax error
object["foo"]: value     // syntax error
object.foo[index]: value // syntax error
```

There is no indexed equivalent of slot creation in Core v0.1 and no `atCreate`-style protocol. Indexed mutation is expressed solely through `atPut`. `:` operates on the slot model while `[]` operates on the indexing protocol, and the two mechanisms remain distinct. There is no automatic relationship between String-valued indexes and slot names: `object["foo"]: value` must not be interpreted as `object.foo: value`.

The bracket forms do not bypass normal message lookup, mutability rules, or error signaling. The meaning of an index, accepted index types, bounds behavior, and storage semantics are defined by the receiver's protocol.

Evaluation order is left-to-right. For:

```js
getReceiver()[getIndex()] = makeValue()
```

the runtime evaluates `getReceiver()`, then `getIndex()`, then `makeValue()`, and finally performs the `atPut` message send.

The selector names `at` and `atPut` are part of the Core v0.1 indexed-access protocol.

The slot openness rules that govern `:` — for example, creating a slot on an object that does not permit slot creation fails according to the existing semantics — do not automatically apply to `atPut`. `atPut` is an ordinary protocol operation; its behavior is defined by the receiver/protocol and any existing mutability or frozen rules. This revision does not redesign `open`, `closed`, or `frozen` semantics.

These are valid:

```js
foo: value
object.foo: value
object[index].foo: value

foo = value
object.foo = value
object[index] = value
object[index].foo = value
object.foo[index] = value
```

These are syntax errors:

```js
object[index]: value
object["foo"]: value
object.foo[index]: value
```

`object.foo` and `object["foo"]` are not equivalent unless the object's own `at` implementation deliberately makes them behave that way. Reflection facilities, if they provide dynamic slot access or creation, remain separate from `[]`.
## Standard Array Indexed Semantics

### Standard Array construction through ordinary invocation

The standard prelude `Array` object specializes the ordinary polymorphic
invocation protocol as an Array factory.

A call:

```js
Array()
Array(a)
Array(a, b, c)
```

creates a fresh **open standard Array** whose receiver-owned indexed elements
are exactly the supplied positional argument objects, in order.

There is no argument-type overload. In particular:

```js
Array(3)
```

creates a one-element Array whose element at index `0` is the exact supplied
Integer object `3`. It does **not** mean "create an Array of length 3".
Core v0.1 defines no implicit length constructor, fill constructor, or
numeric-special constructor behavior.

The factory is shallow: argument objects are neither cloned nor frozen. The new
Array contains the exact supplied object references.

Each successful invocation creates a fresh Array identity, including
zero-argument invocation. Two empty Arrays created by separate calls are not
identical under `===`.

The created Array's delegation parent is the object whose standard Array-factory
invocation behavior was selected as the invocation receiver. Therefore the
ordinary prototype mechanism composes with Array construction:

```js
MyArray: Array {
    label: "custom"
}

values: MyArray(10, 20)
```

`values` owns standard Array indexed state, while its delegation parent is
`MyArray`. `MyArray` itself does not acquire indexed state merely by delegating
to `Array`.

This rule does not weaken the standard Array receiver-domain invariant.
Inheriting or copying ordinary indexed operations such as `at`, `atPut`,
`size`, or `each` still does not confer indexed state on their receiver.
The Array-factory invocation behavior is different: it creates a **new** object
with standard Array state and does not mutate or reclassify the invocation
receiver.

The standard Array factory does not send `init` to the created Array and does
not use `Object`'s default child-plus-`init` construction path. This is an
ordinary specialization of the existing polymorphic invocation protocol, not a
second construction syntax or callable category. A prototype that wants
different construction behavior may specialize its ordinary invocation
behavior in the same way any other object may.

Argument expressions and call spread are evaluated before invocation under the
existing left-to-right call rules. Once invocation begins, standard Array
construction performs no user callback, conversion, equality, hashing,
iteration, or hidden suspension.



A standard `Array` is an identity-bearing object with receiver-owned indexed
element state. Its indexed contents are distinct from its ordinary local slots,
in the same sense that standard Map entries are distinct from ordinary slots.

At any observation point, an Array's indexed state is a finite dense sequence of
element references with logical indices:

```text
0, 1, 2, ... length - 1
```

Core v0.1 defines no holes and no negative-from-end indexing for standard
Arrays.

The standard indexed read:

```js
array.at(index)
```

requires `index` to be a semantic `Integer`. Any Integer family is accepted
according to its mathematical Integer value; no Float-to-Integer conversion,
String parsing, truncation, wrapping, or host-sized coercion is performed. The
Integer must satisfy:

```text
0 <= index < current Array length
```

Otherwise the operation signals an `Error`. A successful `at(index)` returns the
exact element object stored at that position.

The standard indexed update:

```js
array.atPut(index, value)
```

has the same Integer and bounds requirement and replaces exactly the existing
element at that index. Standard `Array.atPut` does not append, grow the Array,
create a hole, shift elements, or otherwise change the Array's indexed length.
A successful call returns the exact `value` object supplied to it.

Consequently bracket syntax has the same standard Array behavior:

```js
array[index]          // standard array.at(index)
array[index] = value  // standard array.atPut(index, value)
```

while retaining the existing syntax-level rule that indexed assignment itself
evaluates to `value`.

Standard Array indexed behavior operates only on an original receiver that owns
standard Array indexed state. Delegation, copying, aliasing, composition, or
otherwise obtaining a standard Array method does not confer Array element
storage on an ordinary object and does not redirect the operation to an
ancestor's Array state. An incompatible receiver signals an `Error` before
performing Array-indexed work.

Array element replacement follows the ordinary object state boundary:

```text
open Array
    -> existing elements may be replaced

closed Array
    -> existing elements may still be replaced

frozen Array
    -> element replacement is prohibited
```

Closing or freezing remains shallow and does not close or freeze element
objects. For `atPut`, a receiver already frozen when the standard method begins
signals an `Error` before index validation or element mutation. A closed Array
still validates the index and may replace the existing element.

Read-only `at` remains available on open, closed, and frozen Arrays.

This section defines the standard semantics of Array indexed state and the
already-existing `at` / `atPut` protocol. It does not add Array literal syntax,
a constructor API, insertion/removal selectors, slicing, negative indexing,
automatic growth, or a second collection hierarchy. Such facilities require
their own explicit contracts if standardized later.

Standard Array `==` and `hash` remain governed by the existing Core default:
without an explicit user override, Arrays use semantic object identity and
`identityHashOf`; element contents are not traversed merely for equality or
hashing.

### Standard Array size

The standard `Array.size` operation returns a semantic `Integer` equal to the
receiver's current indexed element count.

For an Array whose indexed positions are:

```text
0, 1, 2, ... length - 1
```

the result is exactly the mathematical Integer `length`. Core does not require a
particular fixed-width Integer family and does not permit host-size overflow,
wrapping, saturation, or truncation to alter the result.

`size` is a read-only observation. It does not invoke element behavior, does not
traverse or copy the elements merely to establish the semantic result, and does
not mutate the Array. It is available for open, closed, and frozen Arrays.

The existing standard Array receiver-domain rule applies: an object that merely
delegates to an Array or obtains the standard `size` behavior does not thereby
own Array indexed state.

### Standard Array iteration

The standard iteration selector for `Array` is:

```js
array.each(block)
```

`block` must be invokable through the ordinary polymorphic invocation protocol. It need not be a Closure: any value that an ordinary parenthesized call can invoke is accepted. Standard `Array.each` validates this callability after ordinary receiver and argument evaluation and standard Array receiver validation, but before establishing the iteration snapshot or invoking callback behavior. The standard operation then invokes `block` through ordinary polymorphic invocation once with one argument for each Array element.

At the start of `each`, after ordinary receiver and argument evaluation and
after standard Array receiver validation, the operation establishes a shallow
logical snapshot of the receiver's current indexed element sequence. Snapshot
order is ascending Array index order:

```text
0, 1, 2, ... snapshotLength - 1
```

Each snapshot element is the exact object stored at that Array position at the
snapshot point. `each` then invokes `block` once for every snapshot element, in
that order, passing only that element object as the callback argument.

If every callback returns normally, `each` returns the original Array receiver.

The snapshot is shallow. It does not clone, freeze, or otherwise isolate element
objects. Mutating a mutable element through any ordinary reference remains
ordinary mutation of that element and is visible normally.

Replacing an Array element after the snapshot has been established does not
change the value visited for that snapshot position. Likewise, any future
standard operation that changes Array length cannot retroactively add or remove
visits from an already-established `each` snapshot unless that future operation
is explicitly specified to do so.

The callback may replace elements of the same Array through `atPut` whenever
that mutation is otherwise permitted by the Array's open/closed/frozen state.
Such replacement does not alter the current iteration snapshot. Mutations of
other objects likewise remain ordinary effects.

If a callback reaches an explicit suspension point, the Actor may execute other
runnable Actor-local work. Other work may read or mutate the Array according to
the ordinary Array and Actor rules; `each` introduces no Array-wide lock,
mutation prohibition, or hidden scheduling dependency. When the callback
resumes, the current `each` invocation continues with its already-established
snapshot.

If a callback signals an error or exits the `each` invocation through an
ordinary non-local control transfer, no later snapshot element is visited.
Effects already completed by earlier callbacks are not rolled back.

The standard Array receiver-domain rule applies: inheriting, copying, composing,
aliasing, or otherwise obtaining the standard `each` behavior does not confer
Array indexed state on an incompatible receiver.

An implementation need not allocate an eager copied Array merely to implement
the snapshot. Persistent element storage, versioned views, copy-on-write state,
or another representation is valid when the observable shallow-snapshot
behavior is identical. Arrays that are never iterated pay no semantic cost for
iteration snapshots.
## Standard Array Parallel Operations

The standard parallel Array operations are concurrency-domain facilities:

```text
array.parallelMap(worker, arguments...)          -> Future
array.parallelFilter(predicate, arguments...)    -> Future
array.parallelFindIndex(predicate, arguments...) -> Future
array.parallelReduce(reducer, arguments...)      -> Future
array.parallelSort(less, arguments...)            -> Future
```

Their normative isolation, snapshot, transfer, ordering, failure-selection,
cancellation, publication, and implementation-freedom semantics are owned by
`concurrency/PARALLEL_EXECUTION.md` §71.6A–§71.6E.

These operations remain ordinary standard Array behaviors reached through
ordinary message lookup. They introduce no additional syntax or executable value
kind. The general Array receiver-domain and polymorphic invocation rules defined
by this language specification continue to apply where referenced by the
concurrency-domain contract.

Core v0.1 defines no standard `Array.parallelEach(...)`; that concurrency-domain
boundary is likewise owned by `PROTOS_CONCURRENCY_MODEL.md`.
