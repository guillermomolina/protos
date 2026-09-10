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
- `[]` is an indexing protocol, not dynamic slot access;
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

This matters for every standard operation that requires a Boolean result:

```protos
==
<
<=
>
>=
and
or
while
```

The standard contract requires canonical `true` or canonical `false`; there is
no truthiness conversion.

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
important Programming Guide rule here is that Number identity is value-based.

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

## Bracket syntax is an ordinary indexing protocol

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

Each successful call creates a fresh open Array identity whose indexed elements
are the exact supplied values.

A very important rule is:

```protos
Array(3)
```

means:

```text
one element
index 0 -> 3
```

It does **not** mean "allocate an Array of length 3".

Core has no special numeric-length Array constructor.

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
reduction, and sorting over Core Arrays.

Those algorithms do not replace the Core `Array` family or introduce a second
runtime Array representation.

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
  for `null`, Booleans, identity/equality, indexed access, Array, Map,
  IdentityMap, Bytes, and standard collection/value protocols;
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
