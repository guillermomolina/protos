# Values, Identity, Equality, and Collections

> **Status:** Non-normative programming guide
>
> **Normative owners:** `spec/semantics/VALUES_AND_COLLECTIONS.md`,
> `spec/semantics/OBJECT_MODEL.md`, `spec/semantics/CALLABLES.md`, and
> `spec/PROTOS_GRAMMAR.md`

The previous chapters established that Protos uses one object model, ordinary
lookup, ordinary Closures, and ordinary protocols even for control flow.
Values and collections follow the same principle.

The central idea is:

> Every Protos value participates in the object model, but not every value has
> the same identity law or the same storage protocol.

Understanding that distinction prevents several common mistakes:

- `null` means a real value, not a failed lookup;
- `===` asks about semantic identity, not customizable equality;
- `==` is behavioral and may be customized;
- postfix `receiver[index]` is an indexing protocol, while primary `[items...]`
  is ordinary-`Array(...)` construction sugar;
- `Map` and `IdentityMap` intentionally use different key laws;
- Core collections and Standard Library algorithms are different layers.

## `null` is a value, not missing state

Core has one canonical `null` value.

```protos
x: null
```

means that the binding `x` exists and contains `null`.

That is different from a missing lookup:

```protos
missingName
```

A failed lookup signals an Error. It does not silently produce `null`.

This distinction is useful whenever absence is part of application data:

```protos
result: null
```

The program can deliberately store and return `null` while still relying on
lookup failure to expose missing names and slots.

## Canonical Booleans are values too

Core has exactly two semantic Boolean values:

```protos
true
false
```

They are canonical singleton values.

There is no standard `Boolean` prototype whose descendants automatically become
Booleans. Delegating to `true`, `false`, or an object that exposes similarly
named selectors does not grant Boolean membership.

Comparison operators (`==`, `<`, `<=`, `>`, `>=`) and lazy Boolean
`and`/`or` require canonical Boolean results. Standard conditional and loop
protocols also require canonical Boolean decisions: the contextual
`if`, `while`, and suffix `until` forms lower to ordinary messages, not
standard methods named `.if()`, `.while()` or `.until()`. There is no
truthiness conversion. See [control flow](04-control-flow-through-protocols.md).

## Identity is semantic

The primitive identity operator is:

```protos
a === b
```

`===` is not a user-overridable protocol.

For an ordinary identity-bearing object, identity means that both expressions
denote the same semantic individual:

```protos
a: { value: 1 }
b: { value: 1 }
c: a

a === b  // false
a === c  // true
```

The fact that two objects currently have the same visible slots does not make
them the same object.

Identity is semantic rather than a JVM-address, allocation, wrapper, or cache
property. An implementation can change representation without changing the
answer to `===`.

## Some Core values use value identity

Core v0.1 has a closed set of value-identity families.

The semantic value itself determines identity for:

- Number values;
- String values;
- canonical `true`;
- canonical `false`;
- canonical `null`.

For example:

```protos
1 === 1
"hello" === "hello"
("hel" + "lo") === "hello"
true === true
null === null
```

Those results do not depend on whether the implementation interns, boxes,
caches, or reconstructs the values.

The set is deliberately closed. Properties such as these do **not** grant value
identity to another object:

```text
immutable
closed
frozen
structurally equal
interned by an implementation
backed by shared host storage
```

Arrays, Maps, IdentityMaps, Bytes, Closures, Futures, Errors, execution
contexts, module instances, and ordinary objects remain identity-bearing unless
a normative rule explicitly says otherwise.

## Delegation does not transfer value identity

An ordinary object can delegate to a value object without becoming that value
family.

Conceptually:

```protos
wrapped: (42) {
    label: "answer"
}
```

`wrapped` remains an ordinary identity-bearing object. Its parent relationship
does not make:

```protos
wrapped === 42
```

true.

This is another example of Protos separating delegation from classification.
Inheritance of behavior is not conversion of semantic identity.

## Strings are immutable value objects

String operations do not mutate a String in place.

When an operation produces different text, the result is another String value:

```protos
a: "hel"
b: a + "lo"

b === "hello"
```

Because Strings use value identity, an implementation may share or intern their
storage freely without exposing that choice through `===`.

Every semantic String value receives its standard behavior through the standard
`String` delegation path. An ordinary object that merely delegates to `String`
is still not itself a semantic String value.

## Numbers are value-identity values

Numbers follow the same identity principle:

```protos
x: 40 + 2

x === 42
```

The identity observation is about the semantic numeric value, not about a
particular host numeric object.

Different numeric representations may still belong to more specific numeric
protocols, but the programmer should not reason about `===` using JVM boxing,
cache ranges, addresses, or allocation behavior.

The detailed numeric model remains owned by the normative specification. The
important Programming Guide rule here is that Number identity is value-based
within each semantic family. Mathematical equality across families does not
automatically mean identical numeric-family identity (see below).

## Working with Integer, Float, Fraction, and Complex

Protos has one `Number` hierarchy, with distinct semantic numeric families:
`SmallInteger` and `BigInteger` are the concrete exact `Integer` families
(selected automatically by value), `Fraction` is an exact non-integral rational,
`Float` is IEEE binary64, and `Complex` has real and imaginary components.
Ordinary Integers are unbounded: arithmetic promotes beyond the signed-64
range instead of overflowing or wrapping.

```protos
large: 9223372036854775807 + 1  // BigInteger
half: 1 / 2                       // exact Fraction
whole: 6 / 3                      // exact Integer 2
sum: (1 / 2) + (1 / 2)           // exact Integer 1
```

`/` between Integers is exact rational division, **not** truncated integer
division. Integral results normalize to `Integer`; non-integral results become
canonical `Fraction` values. `Fraction(1, 2)` is **not** a public constructor:
use ordinary exact arithmetic such as `1 / 2`. An exact zero divisor signals
an `Error`. For integer-only quotient and remainder, use `div`, `mod`, or `%`
(with truncation toward zero for `div`).

### Bitwise operators on Integer

Integers support fixed bitwise and shift operators. Each one is ordinary syntax
for an ordinary Integer message, so the operator and the message are
interchangeable:

| Operator | Message |
|---|---|
| `a & b` | `a.bitAnd(b)` |
| `a \| b` | `a.bitOr(b)` |
| `a ~ b` | `a.bitXor(b)` |
| `~a` | `a.bitNot()` |
| `a << n` | `a.shiftLeft(n)` |
| `a >> n` | `a.shiftRight(n)` |

```protos
12 & 10      // 8
12 | 10      // 14
12 ~ 10      // 6
~0           // -1
1 << 64      // 18446744073709551616 (a BigInteger, no overflow)
(-3) >> 1    // -2
```

Integers have no implicit width: they behave as infinite two's-complement
values, so `~0` is `-1` and `1 << 64` simply grows. `>>` is an arithmetic
shift (it rounds toward negative infinity). If you need a fixed width, mask
explicitly, for example `value & 4294967295` for 32 bits. Shift counts must be
non-negative Integers; a negative count signals an `Error`.

`&` and `|` are not the Boolean operators `&&` and `||`: they always evaluate
both operands and send an ordinary message, while `&&` and `||` short-circuit.
Binary `~` is XOR; `^` keeps its meaning as non-local return.

Precedence, from tightest to loosest, is: unary `~` (with `!` and unary `-`),
arithmetic (`*` `/` `%`, then `+` `-`), shifts (`<<` `>>`), `&`, binary `~`,
`|`, comparisons, equality, `&&`, and finally `||`. All binary bitwise and
shift operators associate to the left. The table makes `1 | 6 & 3` mean
`1 | (6 & 3)`, but parentheses are cheap and usually clearer when operators
are mixed.

The lexer uses maximal munch, so separate adjacent operator characters with
spaces: `a ~ ~b` is XOR with the complement of `b`, while `a~~b` is invalid;
`a & -b` is valid, while `a&-b` is invalid. Likewise write `a << -1` rather
than `a<<-1`, although that expression still signals an `Error` when it runs:
a negative shift count is a runtime error, never a shift in the opposite
direction.

Because Integer is unlimited, fixed-width fields are always expressed with
explicit masks rather than an implicit word size:

```protos
1 | 6 & 3             // 3: 1 | (6 & 3)
12 ~ ~10              // -7: XOR with the complement of 10
(1 << 8) | 17         // 273: pack two fields
(~0) & 4294967295     // 4294967295: explicit low 32-bit mask
```

Every operator is equivalent to its ordinary message send, so dispatch is
unchanged: a non-Integer object that implements `bitAnd` (or any other of the
six selectors) answers the corresponding operator too.

See the progressive
[`04-bitwise-operators.protos` tutorial](../../protos/tutorials/01-values-and-slots/04-bitwise-operators.protos)
and the practical
[`bit-flags-and-masks.protos` permissions-and-masks example](../../protos/examples/numbers/bit-flags-and-masks.protos).

Mixing `Integer` or `Fraction` with a `Float` produces a `Float`: the exact
operand is converted to binary64 **before** the arithmetic operation, so
precision may be lost. `Float(value)` explicitly rounds an exact Integer or
Fraction to binary64; `Integer(value)` accepts a Float only if it is finite and
mathematically integral and does not truncate a fractional value.

```protos
mixed: (1 / 2) + 0.25             // Float 0.75
rounded: Float(1 / 3)             // rounded binary64 approximation
(1 / 2) == 0.5                   // true: exact mathematical equality
(1 / 2) === 0.5                  // false: different semantic families
(1 / 10) == 0.1                  // false: binary64 0.1 is not exactly 1/10
```

`Float` division by zero follows IEEE rules (infinities or NaN as appropriate),
not the exact Integer/Integer zero-divisor `Error`. Numeric equality and ordering
do **not** first convert exact values to binary64. The exactness distinction is
important when choosing values as Map or IdentityMap keys.

Create a Complex value with the ordinary `Complex(real, imaginary)` factory.
Both components must be semantic real numbers; no implicit conversion or
imaginary-number literal is involved. `real()` and `imaginary()` work on all
semantic Numbers. Exact Integer-zero imaginary components normalize back to
the real value, while Float signed zero is retained as a Complex component.
Complex supports standard `+`, `-`, `*`, and `/`, but no numeric ordering.

```protos
z: Complex(3, 4)
z.real()                          // 3
z.imaginary()                     // 4
Complex(3, 0) === 3               // true: canonical real result
Complex(3, 0.0) === 3             // false: Float-zero imaginary part retained
```

The Standard Library supplies square roots via an **explicit** import; `sqrt`
is not a `Number` message and is not a Prelude function:

```protos
Math: import("std:math/Math")
Math.sqrt(4)                      // exact Integer 2
Math.sqrt(1 / 4)                  // exact Fraction 1/2
Math.sqrt(-4)                     // Complex(0, 2)
Math.sqrt(Complex(3, 4))          // Complex(2, 1)
```

`Math.sqrt(value)` requires exactly one semantic Number. Exact roots stay
exact when rational; other roots are binary64 approximations within the
specified accuracy contract. Negative reals yield principal Complex roots.
For exact Integer algorithms, `std:math/Integer` separately provides `gcd`,
`lcm`, `factorial`, `pow`, and `powMod`; these are Standard Library functions,
not implicit arithmetic or methods on every Number.

See the executable
[`exact-and-complex.protos` example](../../protos/examples/numbers/exact-and-complex.protos),
the [`std:math` sources](../../protos/lib/math/Math.protos), and the
[Standard Library index](library/README.md).

## `===` and `==` answer different questions

The two operators are intentionally different.

```text
===
    non-overridable semantic identity

==
    customizable semantic equality protocol
```

For ordinary objects:

```protos
left: { value: 1 }
right: { value: 1 }

left === right
```

is false unless both names denote the same semantic individual.

`left == right`, however, depends on the selected equality behavior.

A type-like prototype may define equality in terms of application state:

```protos
Point: {
    ==: (other) => {
        (x == other.x) &&
            (y == other.y)
    }
}
```

That changes equality behavior. It does not change identity.

## Equality results are strictly Boolean

A `==` implementation must normally return exactly:

```protos
true
```

or:

```protos
false
```

Returning an arbitrary object, `null`, or a Future is not a truthy/falsy
equality result. It violates the equality-result contract and signals Error at
the applicable standard boundary.

The same strict Boolean-result principle applies to the standard ordering
operators:

```text
<  <=  >  >=
```

This makes comparison code predictable because callers never have to guess how
an arbitrary object should be coerced to a Boolean.

## Hashes are not identity

Hashing supports keyed protocols, but a hash integer is never proof of identity.

Two distinct objects may legally have the same hash.

So this reasoning is invalid:

```text
hash(a) == hash(b)
therefore
a === b
```

Likewise, an identity hash is not required to be globally unique.

Identity is decided by identity semantics. Equality is decided by equality
semantics. Hashing is an indexing aid used by protocols such as Map.

Keep those three concepts separate.

## Postfix bracket syntax is an ordinary indexing protocol

Indexed read:

```protos
receiver[index]
```

means the indexing operation:

```protos
receiver.at(index)
```

Indexed write:

```protos
receiver[index] = value
```

means:

```protos
receiver.atPut(index, value)
```

The syntax does not bypass lookup and does not impose one universal collection
model.

Any object can participate by implementing the corresponding protocol.

## Indexed access is not slot access

This distinction is fundamental:

```protos
object.name
```

uses slot/member lookup.

This:

```protos
object["name"]
```

uses the `at` indexing protocol.

They are not automatically equivalent.

An object can even support both with unrelated state:

```protos
map.description: "users"
map["description"] = someUser
```

The first operation addresses a slot. The second addresses indexed contents.

Adding a slot does not make an object indexable, and implementing `at` does not
turn indexed entries into slots.

## `:` does not create indexed entries

Slot creation uses `:`:

```protos
object.name: value
```

There is no indexed counterpart:

```protos
object[index]: value
```

That form is invalid.

Indexed mutation is expressed through `atPut`, directly or through `[] =`.

Whether `atPut` inserts, replaces, rejects, extends, or requires an existing
position belongs to the receiver's protocol.

This lets Array and Map use the same bracket syntax while retaining different
invariants.

## Array construction is ordinary invocation

The standard prelude `Array` object acts as a factory through ordinary call
semantics:

```protos
Array()
Array(10)
Array(10, 20, 30)
```

Protos also provides bracket construction syntax:

```protos
[]
[10]
[10, 20, 30]
```

This is syntax sugar, not another collection mechanism. The lowering is exact:

```text
[]              -> Array()
[10]            -> Array(10)
[10, 20, 30]    -> Array(10, 20, 30)
```

Spread is the existing call spread:

```protos
middle: [20, 30]
values: [10, ...middle, 40]
```

and nested/postfix forms compose normally:

```protos
nested: [[10], 20]
first: nested[0][0]
```

`Array` remains an ordinary lookup. Bracket construction does not secretly
capture the standard-prelude factory:

```protos
Array: (a, b) => a * 100 + b

[4, 2]  // 402
```

So `[a, b]` behaves exactly like `Array(a, b)` under the binding visible at that
point. With the ordinary standard-prelude `Array`, each successful construction
creates a fresh open Array identity whose indexed elements are the exact supplied
values.

A very important rule is:

```protos
[3]
```

and equivalently:

```protos
Array(3)
```

mean:

```text
one element
index 0 -> 3
```

They do **not** mean "allocate an Array of length 3".

Core has no special numeric-length Array constructor, holes, comprehensions,
repetition/fill form, or generic collection-literal conversion.

## Arrays are shallow containers

Array construction keeps the exact supplied objects:

```protos
item: { value: 1 }
items: Array(item)

items[0] === item
```

The element is not cloned or frozen merely because it was inserted into an
Array.

This shallow behavior is consistent with ordinary Protos reference semantics.

## Standard Array indexing is positional

For the standard Array protocol:

```protos
items.at(index)
items.atPut(index, value)
items.size()
```

the index is an Integer position.

Standard reads and writes require a valid in-range position. `atPut` replaces
the indexed value at that position; it is not a generic "grow to arbitrary
index" operation.

Bracket syntax can express the same protocol:

```protos
x: items[0]
items[0] = replacement
```

Again, indexed state is distinct from the Array object's ordinary slots.

## Collection state and slot state remain orthogonal

Object openness and collection mutation are related by each protocol's own
rules, not by a blanket interpretation of `[]` as slot mutation.

For example, the standard Array protocol can replace an existing indexed
element through `atPut` when that Array state permits the operation even though
indexed assignment is not slot assignment.

Freezing prevents mutation of mutable standard collection state.

The practical rule is:

> Ask which protocol owns the state being changed.

For:

```protos
array.label = "x"
```

the answer is the slot model.

For:

```protos
array[0] = "x"
```

the answer is the Array indexed-state protocol.

## `Array.each` iterates a shallow snapshot

The standard `Array.each` callback traversal observes a shallow pre-callback
snapshot of the indexed elements.

Conceptually:

```protos
items.each((element) => {
    consume(element)
})
```

means that the traversal set is established before user callbacks begin.

The snapshot is shallow: its elements are the same semantic element objects, not
deep copies.

This makes mutation during callback execution easier to reason about: a callback
may cause later state changes, but those changes do not retroactively redefine
which element references were captured for that traversal.

The callback itself uses ordinary polymorphic invocation.

## `%{...}` defines a Map's initial associations

For the common equality-keyed case, Protos has concise populated Map
construction:

```protos
m: %{
    "language": "Protos"
    "version": 1
}
```

Entries may also share a line with `;`:

```protos
m: %{ "language": "Protos"; "version": 1 }
```

This syntax creates a standard Map and defines its initial associations. It is
important not to read it as hidden repeated indexed assignment.

Construction first looks up ordinary `Map`. The selected `call` behavior must be
the canonical standard Map factory behavior, either directly or inherited
through ordinary delegation.

That means ordinary prototype specialization works:

```protos
MyMap: Map {
    label: "custom"
}

Map: MyMap

m: %{ "answer": 42 }
```

The resulting Map is fresh standard Map state whose parent is `MyMap`.

A nearer custom `call` override is different. It does not become construction
authority merely because it is named `Map` or happens to return an indexable
object. `%{...}` rejects that factory before executing the custom `call` and
before evaluating entries.

For every entry, Protos evaluates the key and then the value. Only after both
finish normally does it define the initial association.

Initial definition uses normal Map hashing and directed equality, but it does
**not** send `atPut`. An `atPut` override on an otherwise eligible Map prototype
therefore cannot intercept initial construction.

Keys remain ordinary expressions:

```protos
key: "language"

m: %{
    key: "Protos"
}
```

The key above is the value `"language"`. A bare key name is not converted
implicitly to a String.

Duplicate/equal initial keys are errors:

```protos
%{
    "version": 1
    "version": 2
}
```

does not mean "last value wins". The second initial definition conflicts with
the first under the normal Map key-selection law and signals Error.

This differs deliberately from later indexed mutation:

```protos
m: %{ "version": 1 }
m["version"] = 2
```

The second line is ordinary indexed assignment and therefore ordinary `atPut`.
Standard Map mutation may replace the existing value there.

Map construction is a primary expression, so postfix indexing works directly:

```protos
%{ "answer": 42 }["answer"]
```

`IdentityMap` deliberately stays explicit:

```protos
identity: IdentityMap()
```

It has a different key law and receives no `%{...}` syntax. There is also no
generic `%Factory{...}` form.

Map construction and structural `Map.match(subject)` are separate operations
with separate semantics. `%{...}` constructs a Map value; it is not pattern
syntax.

## Map is keyed by equality

The standard `Map` family uses the key's ordinary equality/hash protocols.

A typical Map:

```protos
m: Map()

m["language"] = "Protos"
m["version"] = 1
m["version"] = 2

m["language"]
m["version"]
m.size()
```

Updating an already-matching key replaces its value rather than creating a
second entry.

Map preserves insertion order for its keyed traversal.

The key law is behavioral: finding a matching key involves the keyed hash/equality
protocol rather than semantic object identity alone.

## Map and custom equality compose

Because Map uses ordinary equality behavior, application-defined key types can
participate.

If a key customizes equality, its hashing behavior must be designed coherently
for keyed use. Map is not required to ignore hashing and linearly test every
possible object.

This is a useful power but also a responsibility: mutable equality/hash state is
a poor Map key design because changing the properties used by those protocols
can undermine later lookup.

For identity-based keyed state, use IdentityMap instead.

## IdentityMap is keyed by `===` semantics

`IdentityMap` deliberately uses a different law.

Consider two different objects with the same visible contents:

```protos
left: { value: 1 }
right: { value: 1 }

left === right  // false
```

An IdentityMap treats them as distinct keys:

```protos
m: IdentityMap()

m[left] = "left"
m[right] = "right"

m.size()  // 2
```

It does not call user-defined `==` to decide that those ordinary objects are
the same key.

The operation relies on semantic identity plus identity hashing.

## Value-identity keys still collapse by value in IdentityMap

IdentityMap does not mean "physical allocation map".

Because Numbers and Strings use value identity:

```protos
a: "hel" + "lo"
b: "hello"

a === b
```

is true.

Those values therefore denote the same semantic IdentityMap key.

This is exactly why the distinction between semantic identity and physical
representation matters.

## Map and IdentityMap solve different problems

Use Map when key sameness should follow semantic equality:

```text
"same customer number"
"same path value"
"same application key"
```

Use IdentityMap when key sameness should follow semantic identity:

```text
this exact mutable object
this exact Closure
this exact identity-bearing capability/value
```

Neither is the "faster version" of the other at the language level. They expose
different semantics.

## Map mutation has key-specific rules

The standard Map operations include:

```protos
at(key)
atPut(key, value)
containsKey(key)
remove(key)
size()
each(callback)
```

An absent `at` signals Error rather than returning `null`.

That keeps "missing key" distinct from:

```protos
m[key] = null
```

where the key exists and deliberately stores `null`.

Replacing an existing entry and adding a new key are semantically distinct.
Adding/removing keyed structure requires the appropriate open mutable state;
frozen state rejects mutation.

## Map iteration observes insertion order

Map traversal preserves insertion order.

The standard `each` callback receives the key and value:

```protos
m.each((key, value) => {
    consume(key, value)
})
```

Like other standard collection callbacks, invocation is ordinary protocol
invocation rather than a second callable universe.

The traversal uses a keyed snapshot so callback effects do not rewrite the
already-selected traversal sequence underneath the current iteration.

IdentityMap exposes the analogous keyed operations while using its identity key
law.

## Bytes is another identity-bearing collection

Core `Bytes` represents indexed byte state.

It is not in the closed value-identity set, so a Bytes object remains an
identity-bearing collection even when two Bytes currently contain the same byte
sequence.

That is different from String:

```text
String
    immutable value identity

Bytes
    identity-bearing indexed byte state
```

Encoding and byte-I/O behavior are covered in later guide material. The
important point here is that "sequence-like" does not imply value identity.

## Core collections and Standard Library algorithms are different layers

Core provides the semantic collection mechanisms such as:

```text
Array
Map
IdentityMap
Bytes
```

The Standard Library builds higher-level behavior using those mechanisms.

For example:

```text
std:collections/Array
```

provides ordinary Protos algorithms such as mapping, filtering, indexed search,
reduction, and sorting over Core Arrays, plus isolated-parallel variants of
them composed from `closure.parallel(...)` and Futures (see chapter 09).

Those algorithms do not replace the Core `Array` family or introduce a second
runtime Array representation.

The same module also provides copy/edit operations and a default ascending
sort:

```protos
Arrays: import("std:collections/Array")

values: Array(10, 20, 30)

appended: Arrays.append(values, 40)
combined: Arrays.concat(values, Array(40, 50))
middle: Arrays.slice(values, 1, 3)
inserted: Arrays.insertAt(values, 1, 15)
removed: Arrays.removeAt(values, 1)
reversed: Arrays.reverse(values)
sorted: Arrays.sort(Array(30, 10, 20))
```

The resulting contents are:

```text
appended = [10, 20, 30, 40]
combined = [10, 20, 30, 40, 50]
middle   = [20, 30]
inserted = [10, 15, 20, 30]
removed  = [10, 30]
reversed = [30, 20, 10]
sorted   = [10, 20, 30]
```

The bracketed lists on the right are explanatory pictures of each Array's
elements. They are not Protos values, and they do not mean that the result
is structurally equal (`==`) to some other Array.

Core Arrays still have a fixed length. None of these functions grows or edits
`values`; each answers a **new** open Array with its own identity and storage,
and `values` remains `[10, 20, 30]`. A few rules to keep in mind:

- Indexes are strict: they must be Integers within range (`0 <= start <= end
  <= size` for `slice`, `0 <= index <= size` for `insertAt`,
  `0 <= index < size` for `removeAt`). Floats, negative indexes, and
  out-of-range values signal an `Error`; nothing is converted or clamped.
- Copies are shallow: nested objects are shared by identity, not cloned.
- `append` and `insertAt` add an Array argument as one element; only `concat`
  splices another Array's elements in.
- `sort(array)` without a comparator uses the ordinary `<` of the elements,
  so it suits numbers but is not a total order over arbitrary values. An
  explicit `null` comparator is not replaced by the default.

The module also has ordered queries and fresh prefix/suffix copies:

```protos
Arrays: import("std:collections/Array")
values: Array(10, 20, 30)

found: Arrays.find(values, (item) => item > 15)
present: Arrays.contains(values, 20)
someLarge: Arrays.any(values, (item) => item > 25)
allPositive: Arrays.all(values, (item) => item > 0)
firstTwo: Arrays.take(values, 2)
tail: Arrays.drop(values, 2)
```

```text
found       = 20
present     = true
someLarge   = true
allPositive = true
firstTwo    = [10, 20]
tail        = [30]
```

`find` answers the first matching **original element**, or `null` when none
matches. A matching `null` is indistinguishable from a failed search: use
`Arrays.findIndex` when that distinction matters. `contains` scans left to
right using the element's ordinary `==` behavior (no hash table or identity
shortcut). `find`, `any` and `all` require exact Boolean predicate results;
anything other than `true` or `false` signals `InvalidPredicateResult`.
They stop as soon as their answers are known: an empty Array gives `false`
for `any` and `true` for `all`, without invoking callbacks.

Callbacks and equality operations work on a shallow snapshot made before the
first callback, so an effect that edits the source cannot change the values
still to be visited. `take` and `drop` accept only nonnegative Integers and
saturate at the Array size; even an empty/full/no-op result is a new open,
independent shallow Array. Neither operation mutates the input.

For one-to-many mapping, pairing, grouping, and deduplication, use the same Array module:

```protos
Arrays: import("std:collections/Array")
values: Array(1, 2, 2, 3)

expanded: Arrays.flatMap(values, (x) => { Array(x, x * 10) })
paired: Arrays.zip(values, Array("a", "b", "c"))
groups: Arrays.partition(values, (x) => { x.mod(2) == 0 })
unique: Arrays.distinct(values)
```

```text
expanded  = [1, 10, 2, 20, 2, 20, 3, 30]
paired    = [[1, "a"], [2, "b"], [2, "c"]]
groups[0] = [2, 2]                 (matches)
groups[1] = [1, 3]                 (rejects)
unique    = [1, 2, 3]
```

`flatMap` requires each mapper result to be a real Array and flattens only
one level. It snapshots each returned Array immediately, so reusing the
same mutable chunk as scratch space does not corrupt earlier results.
`zip` stops at the shorter length, returning new pairs without padding.
`partition` invokes a predicate once per element, requires actual `true`
or `false`, and returns two stable-order buckets, even when either is empty.
`distinct` preserves the first occurrence using the ordinary hashed Set
semantics (`hash` plus `==`), **not** the identity comparison `===`.

All four operations preserve the original elements by reference while
creating fresh open Arrays with independent indexed storage. They work
with closed, frozen and descendant-factory input Arrays, and take a
shallow snapshot before any callback or equality/hash effects. They do
not modify the source Array. `flatMap` and `partition` do not inspect an
unused callback when the input is empty; any invalid nonempty callback
result or equality/hash failure propagates normally.

## Set and IdentitySet are Standard Library abstractions

Current Standard Library modules include:

```text
std:collections/Set
std:collections/IdentitySet
```

They deliberately reuse existing keyed semantics:

```text
Set
    built on Map
    membership follows equality-key semantics

IdentitySet
    built on IdentityMap
    membership follows identity-key semantics
```

There is no separate Core Set runtime family required merely because set
operations are useful.

This is a good example of the Protos design principle "mechanisms over
institutions": higher-level collection abstractions can be written in ordinary
Protos when Core already supplies the necessary semantic foundation.

## Directed Integer ranges

`Range` (ascending) and `InvertedRange` (descending) are two ordinary, frozen
Core prototypes available in every module without an import, just like `Map`.
Invoke them like any other prototype:

```protos
Range(0, 10).each(i => print(i))                  // 0 .. 9
Range(0, 10).eachStep(2, i => print(i))           // 0, 2, 4, 6, 8
InvertedRange(10, 0).eachStep(-2, i => print(i))  // 10, 8, 6, 4, 2

Range(0, 3).invert().each(i => print(i))          // 3, 2, 1
Range(0, 3).reverse().each(i => print(i))         // 2, 1, 0
```

For ordinary collection traversal and counted iteration, the contextual
[`for` expressions](04-control-flow-through-protocols.md#the-contextual-for-forms-are-each-and-eachstep)
are often the shorter source spelling. The
[executable `for` tutorial](../../protos/tutorials/04-control-flow/04-contextual-for.protos)
demonstrates them. Explicit `Range`, `InvertedRange`, `each` and `eachStep`
remain useful when working with the range objects or controlling their method
dispatch directly.

The first bound is included and the second excluded, so every range is
half-open. Both bounds must be Integers. Each constructor picks the prototype
that matches the order of its bounds, and keeps the bounds exactly as you wrote
them:

```protos
Range(3, 0).each(i => print(i))          // 3, 2, 1  (an InvertedRange)
InvertedRange(0, 3).each(i => print(i))  // 0, 1, 2  (a Range)

Range(3, 0).parent() === InvertedRange   // true
Range(3, 3).parent() === Range           // true: empty, prototype kept
InvertedRange(3, 3).parent() === InvertedRange  // true: empty, prototype kept
```

The direction belongs to the prototype of the result, not to the constructor
you wrote: `Range.eachStep` needs a positive step and `InvertedRange.eachStep`
a negative one, so `Range(5, 0).eachStep(-2, ...)` visits 5, 3, 1 while
`Range(5, 0).eachStep(2, ...)` signals an `Error`.

That `Range(a, b)` can answer an object whose parent is `InvertedRange` is
similar to exact arithmetic answering a different numeric family; it does not
mean ranges compare numerically by value. Ranges still compare by identity.

`invert()` swaps the bounds and the direction, so it can visit different
elements. `reverse()` keeps exactly the same elements and reverses their
order. Their meanings do not depend on which constructor built the range. Both answer new ranges; instances are frozen, with reflectable `start`
and `stop` slots, and compare by identity.

The `std:collections/Range` module still exists for compatibility: its
`Range` and `InvertedRange` members are the very same objects
(`import("std:collections/Range").Range === Range`).

The earlier module helpers `Ranges.each(start, stop, block)` and
`Ranges.reverseEach(start, stop, block)` no longer exist.

## Vectors and matrices

Arrays hold any values. For numeric linear algebra, the Standard Library module
`std:math/LinearAlgebra` provides two prototypes, `Vector` and `Matrix`, whose
elements must be genuine Numbers. Import the module explicitly and bind both
prototypes before calling them:

```protos
LA: import("std:math/LinearAlgebra")
Vector: LA.Vector
Matrix: LA.Matrix

v: Vector(1, 2, 3)                 // three elements
m: Matrix(2, 3, 1, 2, 3, 4, 5, 6)  // 2 × 3, row-major values
m.matvec(v)[1]                     // 4 + 10 + 18 == 32
```

Vectors use the ordinary zero-based `v[i]` indexing; matrices use
`at(row, column)`. Shapes must match exactly: nothing is broadcast, reshaped or
converted, and every scalar step is the ordinary Number arithmetic described
above. Results are fresh identity-bearing objects. See
[Vectors and matrices](library/linear-algebra.md) for the full API, Complex
behavior, and the fixed reduction order.

## Equality choice propagates into collection choice

A useful design sequence is:

```text
What does "same" mean for my domain?
        |
        +-- semantic equality --> ==
        |                         |
        |                         +--> Map / Set
        |
        +-- semantic identity --> ===
                                  |
                                  +--> IdentityMap / IdentitySet
```

Choose the identity/equality law first. Then choose the collection whose key
semantics match it.

Do not choose Map versus IdentityMap based only on familiarity or presumed
performance.

## Practical rules to remember

1. Treat `null` as an actual stored value; missing lookup is an Error.
2. Remember that canonical `true` and `false` are the only semantic Booleans.
3. Use `===` for non-overridable semantic identity.
4. Use `==` for customizable semantic equality.
5. Keep identity, equality, and hashes conceptually separate.
6. Remember the closed value-identity set: Numbers, Strings, `true`, `false`,
   and `null`.
7. Do not infer value identity from immutability, freezing, structural equality,
   or host interning.
8. Read `object[index]` as `object.at(index)`, never as dynamic slot lookup.
9. Remember that `Array(3)` creates one element containing `3`, not three empty
   positions.
10. Use Map when keys follow equality and IdentityMap when keys follow identity.
11. Distinguish an absent Map key from a present key whose value is `null`.
12. Treat Set/IdentitySet and Array algorithms as Standard Library abstractions
    built on Core mechanisms rather than new Core runtime families.

## Run the executable lessons

For arithmetic, [`exact-and-complex.protos`](../../protos/examples/numbers/exact-and-complex.protos)
demonstrates exact division, mixed Float arithmetic, Complex components, and
`std:math/Math`.

The current tutorial material includes direct examples for keyed collections:

- [`../../protos/tutorials/05-collections/01-maps.protos`](../../protos/tutorials/05-collections/01-maps.protos)
  demonstrates equality-keyed Map creation, insertion, replacement, lookup, and
  size;
- [`../../protos/tutorials/05-collections/02-identity-maps.protos`](../../protos/tutorials/05-collections/02-identity-maps.protos)
  demonstrates distinct identity-bearing objects as separate IdentityMap keys.

The current Standard Library collection sources are also ordinary Protos and are
useful as larger examples:

- [`../../protos/lib/collections/Array.protos`](../../protos/lib/collections/Array.protos)
  implements higher-level Array algorithms over the Core Array surface;
- [`../../protos/lib/collections/Set.protos`](../../protos/lib/collections/Set.protos)
  builds equality-based set behavior over Map;
- [`../../protos/lib/collections/IdentitySet.protos`](../../protos/lib/collections/IdentitySet.protos)
  builds identity-based set behavior over IdentityMap.

These files demonstrate current runnable behavior. The specification remains the
normative authority.

## Normative references

For exact behavior, consult:

- [`../../spec/semantics/VALUES_AND_COLLECTIONS.md`](../../spec/semantics/VALUES_AND_COLLECTIONS.md)
  for `null`, Booleans, numeric families/arithmetic, identity/equality,
  indexed access, Array, Map, IdentityMap, Bytes, and standard
  collection/value protocols;
- [`../../spec/semantics/OBJECT_MODEL.md`](../../spec/semantics/OBJECT_MODEL.md)
  for open/closed/frozen object state, delegation, slots, and semantic-family
  receiver domains;
- [`../../spec/semantics/CALLABLES.md`](../../spec/semantics/CALLABLES.md) for
  ordinary factory specialization and collection callback invocation;
- [`../../spec/PROTOS_GRAMMAR.md`](../../spec/PROTOS_GRAMMAR.md) for literal,
  equality/comparison, bracket-indexing, and assignment syntax;
- [`02-objects-delegation-and-composition.md`](02-objects-delegation-and-composition.md)
  for the object-state rules that collections remain orthogonal to;
- [`04-control-flow-through-protocols.md`](04-control-flow-through-protocols.md)
  for strict canonical Boolean decisions and lazy callback composition.

Those documents define the language. This chapter supplies a programmer-facing
model for choosing the right identity, equality, and collection semantics.
