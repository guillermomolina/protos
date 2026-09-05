# LIB001 Collections Design Record

Status: Set/IdentitySet contract closed; LIB001-A and LIB001-B closed; LIB001-C ready
Work item: `LIB001`
Nature: Project design record; **non-normative**

## Purpose

This document records the initial design investigation for the Protos Standard
Library Collections work item. It exists so implementation does not begin from a
familiar collection hierarchy by default and so later agents can distinguish
questions that have already been investigated from questions that remain open.

The normative Protos specification under `spec/` remains authoritative. This
record does not add Core value families, prototypes, transfer rules, iteration
semantics, syntax, or other language semantics. If implementation pressure
reveals a missing Core/runtime semantic prerequisite, that prerequisite must be
tracked and resolved outside `LIB001` before library code relies on it.

## Design objective

Build useful general-purpose collection facilities while preserving the Protos
principles of a small universe, mechanisms over institutions, ordinary library
behavior, visible semantic distinctions, minimal shared state, Actor isolation,
and pay-only-for-what-you-use scaling.

The starting question is therefore not "which familiar collection classes
should Protos have?". It is:

> Which useful collection laws and operations can emerge from the Core
> mechanisms Protos already has, and what is the smallest additional library
> structure required for the remainder?

## Repository constraints established by the audit

The design is constrained by the current Core contracts rather than by prior-art
API familiarity:

- Core already owns standard `Array`, `Map`, and `IdentityMap`; LIB001 must not
  duplicate or redefine their semantics.
- Core intentionally does not standardize one general iterable/spreadable
  protocol. Generalizing iteration would require its own explicit normative
  contract for order, effects, suspension, mutation visibility, and related
  interactions.
- Standard `Array.each` and keyed `Map.each`/`IdentityMap.each` have deliberately
  different callback shapes. Protos has strict argument binding, so a Self-style
  convention that simply passes extra traversal arguments cannot be assumed.
- `Map` membership uses its existing `hash` + `==` semantics. `IdentityMap`
  membership uses semantic identity and identity hashing. A library abstraction
  must not hide that distinction behind a supposedly uniform stronger law.
- Actor transfer of an ordinary object traverses local slots and the immutable
  delegation-parent edge transitively. Closures are not Actor-transferable.
  Therefore a library prototype containing ordinary Closure methods in a data
  object's transfer graph can make otherwise ordinary collection data
  non-transferable.
- Module instances are Actor-local. Behavior loaded from an ordinary library
  module can therefore remain local while ordinary data crosses Actor
  boundaries under existing transfer rules.
- Object freezing is shallow. A wrapper whose own mutation state differs from a
  mutable backing collection would create a two-state consistency problem.
- Core Array replacement is fixed-size: `atPut` replaces an existing element and
  does not append, insert, remove, create holes, or grow the Array.
- Core already uses the names `parallelMap`, `parallelFilter`,
  `parallelFindIndex`, `parallelReduce`, and `parallelSort`. Sequential library
  algorithms should prefer vocabulary coherent with that existing surface when
  the semantics align.

## Prior-art coverage

The audit intentionally compared *design models*, not a popularity list. Primary
or official documentation was preferred.

| Model / pressure | Main references | Lesson for Protos |
| --- | --- | --- |
| Mature message-oriented collection protocol | Smalltalk / Pharo / GNU Smalltalk | A small traversal basis can support a rich durable protocol; the protocol is more valuable than copying the class hierarchy or `species` machinery. |
| Prototype/delegation-native collections | Self | Traits and small required operations show how behavior can be derived without a class hierarchy; Self's permissive block arity cannot be copied into strict-arity Protos. |
| Minimal traversal plus reusable algorithms | Ruby `Enumerable` | Rich behavior can arise from one small traversal contract, but Protos should not invent a universal contract before its laws are needed. |
| Universal sequence abstraction | Clojure | Separating data from sequence processing is powerful, but a universal `seq` layer carries persistence/laziness assumptions not currently justified for LIB001. |
| Iterator/adaptor model with explicit ownership | Rust | Iterator composition is powerful but introduces traversal state/lifetime semantics that eager LIB001 algorithms do not yet require. |
| Protocol/ABC hierarchy | Python | Generic mixins can reduce duplication, but broad hierarchy and complexity assumptions can leak cost or misleading guarantees. |
| Long-lived OO interface hierarchy | Java Collections | `HashSet` validates Map-backed Set representation; `IdentityHashMap` demonstrates the danger of forcing semantically different equality laws under one overly strong common contract; optional operations, iterators, and backed views carry substantial semantics. |
| Containers, algorithms, adaptors, ranges | C++ STL | Storage, algorithms, and interface roles need not form one inheritance tree; `stack`/`queue` show useful roles/adaptors can sit over existing storage; ranges demonstrate how much semantic machinery a true generic traversal model eventually needs. |
| External algorithms and LINQ | C# / .NET | Reusable behavior can live outside data representation; LINQ strongly validates module-like external algorithms, while deferred execution/enumerators show costs that eager LIB001 should avoid initially. |
| Module-oriented collections in Actor ecosystem | Erlang / Elixir | Module-centric APIs compose naturally with isolated processes/Actors; Erlang `sets` can use maps internally and Elixir `Enum`/`MapSet` reinforce separation of data from behavior. |
| Capability-oriented collection protocols / value semantics | Swift | Small capability laws can be valuable, but `Sequence` versus `Collection` and mutable versus range-replaceable distinctions show that generic iteration/growth concepts need explicit semantics. `SetAlgebra` is useful as algebraic law inspiration without requiring a Protos hierarchy. |

Primary reference entry points used during the audit include:

- Smalltalk / GNU Smalltalk collections: https://www.gnu.org/software/smalltalk/manual/
- Pharo books: https://books.pharo.org/
- Self collections: https://handbook.selflanguage.org/2024.1/collections.html
- Ruby Enumerable: https://docs.ruby-lang.org/en/master/Enumerable.html
- Clojure sequences: https://clojure.org/reference/sequences
- Rust iterators: https://doc.rust-lang.org/std/iter/
- Python collection ABCs: https://docs.python.org/3/library/collections.abc.html
- Java Collections Framework: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Collection.html
- C++ containers/ranges: https://en.cppreference.com/w/cpp/container and https://en.cppreference.com/w/cpp/ranges
- .NET LINQ / collections: https://learn.microsoft.com/dotnet/api/system.linq.enumerable
- Erlang sets: https://www.erlang.org/doc/apps/stdlib/sets.html
- Elixir Enum / MapSet: https://hexdocs.pm/elixir/Enum.html and https://hexdocs.pm/elixir/MapSet.html
- Swift collection protocols / SetAlgebra: https://developer.apple.com/documentation/swift/sequence-and-collection-protocols and https://developer.apple.com/documentation/swift/setalgebra

Future prior-art research should be question-driven. Add another language or
runtime when it represents a materially different design model or directly
answers an unresolved Protos question; do not expand the survey merely to
accumulate examples.

## Architecture alternatives evaluated for Set

### A. Dedicated `Set` prototype with methods

Shape:

```text
set
  -> Set prototype
       -> add Closure
       -> remove Closure
       -> each Closure
       -> ...
```

This gives attractive receiver syntax, but the prototype is part of the
ordinary delegation graph. Under current Actor-transfer rules its Closure-valued
behavior becomes part of the traversed graph and is non-transferable. Repairing
that by recognizing library prototypes specially at the transfer boundary would
create privileged standard-library identities and pairwise Actor/library rules.

**Current disposition: rejected for initial LIB001.**

### B. Wrapper Set containing a backing Map

Shape:

```text
set
  -> backing: Map
```

This separates behavior from the backing representation but introduces two
identities and two mutation-state layers for one logical collection. In
particular, ordinary `set.freeze()` is shallow and would not freeze the backing
Map. Fixing that requires wrapper-specific mutation mediation or special freeze
semantics, while the backing object remains ordinarily reachable unless another
institution is introduced.

**Current disposition: rejected for initial LIB001.**

### C. Set as a library role over Map keys

Shape:

```text
Map
  -> ordinary keyed-mapping role
  -> Set-of-keys role supplied by an Actor-local library module
```

No new runtime value or wrapper is introduced. Existing Map state already owns
membership, hashing/equality, identity, mutation state, traversal snapshot/order,
Actor transfer, alias/cycle behavior, and open/closed/frozen semantics.

`IdentitySet` is the corresponding role over `IdentityMap`, preserving the
existing semantic-identity membership law rather than pretending both keyed
families have the same equality semantics.

**Current disposition: adopted for initial LIB001, refined by the focused API
audit below.**

This is a design recommendation, not a normative statement that every future
Set facility must forever use this representation.

## Recommended Set / IdentitySet laws

The focused API audit closes the initial Set/IdentitySet contract. The earlier
architecture C remains preferred, with one important refinement: the Standard
Library does **not** promise that an arbitrary Map containing unrelated mapped
values can simultaneously be mutated as a Set while preserving those values.
Core has no atomic public `putIfAbsent`-style operation, and normal Map key
search may execute user `hash` / `==` behavior with effects or suspension. A
library implementation based on `containsKey` followed by `atPut` would perform
two searches and could not faithfully provide that stronger promise.

The initial Set representation is therefore a small **library invariant over an
ordinary standard Map**, not a new runtime family:

```text
Set         = standard Map       with every member stored as key -> true
IdentitySet = standard IdentityMap with every member stored as key -> true
```

The canonical Boolean `true` is the exact marker used by constructors and
`add`. It is immutable, Actor-transferable, has no auxiliary identity, and makes
the otherwise observable Map representation explicit rather than pretending it
is hidden. A value produced by the Set module remains an ordinary Map for all
Core observations; a value produced by the IdentitySet module remains an
ordinary IdentityMap.

A standard Map/IdentityMap that already satisfies the corresponding `key ->
true` invariant may be used with the library contract. Directly changing one of
its mapped values to another object, or otherwise replacing/overriding the
ordinary keyed behavior on which the module relies, breaks the Set-library
invariant; LIB001 does not add a runtime Set tag, validation bit, wrapper, or
privileged recovery rule for such values.

The closed initial laws are:

1. **No Set runtime family, tag, wrapper, or Set prototype.** Set is ordinary
   Map state plus Actor-local module behavior.
2. **Canonical representation marker.** Every association introduced by the
   Set/IdentitySet modules maps its representative member key to canonical
   `true`.
3. **Membership follows the underlying keyed law.** Set uses standard Map
   `hash` + `==`; IdentitySet uses standard IdentityMap semantic identity
   (`identityHashOf` + `===`). No equality-strategy object is introduced.
4. **Representative and insertion order come from Core Map.** Re-adding an
   already-matching member retains the existing representative key and insertion
   position because standard `atPut` replaces only the mapped value.
5. **Set algebra ignores traversal order mathematically, but observable
   traversal/results are deterministic.** The exact rules are stated below.
6. **No special Actor rule.** Set data crosses Actors only through the existing
   Map/IdentityMap transfer contracts; the receiving Actor imports behavior
   locally when needed.
7. **No special open/closed/frozen rule.** The underlying Map state is
   authoritative; the library neither deep-freezes nor maintains a second
   mutation-state layer.
8. **Ordinary Map equality/hash remain untouched.** Library membership
   equivalence is explicit through `sameMembers`; LIB001 does not redefine `==`
   or `hash` for mutable Map-backed Sets.

## Closed initial Set / IdentitySet surface

The portable modules are:

```text
std:collections/Set
std:collections/IdentitySet
```

Their imported module instances are ordinarily invokable. No separate `empty`,
`of`, `new`, constructor object, or syntax is introduced:

```protos
sets: import("std:collections/Set")
identitySets: import("std:collections/IdentitySet")

empty: sets()
values: sets(a, b, c)
identities: identitySets(a, b, c)
```

The module-local `call(...elements)` factory receives the normal already-
evaluated positional vector under the ordinary Closure/rest rules, creates one
fresh open standard Map/IdentityMap, and processes elements in supplied order by
ordinary keyed insertion with marker `true`.

Duplicate handling is therefore not a second Set-specific key algorithm. When a
later supplied element matches an existing member under the underlying keyed
law, standard `atPut` keeps the original representative key and insertion
position while replacing its mapped value with the same canonical `true`.
Constructor key `hash` / `==` effects and failures are ordinary Map effects and
are not rolled back.

The exact initial operations are:

```text
call(...elements)              -> fresh open Map-backed Set
contains(set, element)         -> Boolean
add(set, element)              -> set
remove(set, element)           -> set
size(set)                      -> Integer
each(set, block)               -> set

union(left, right)             -> fresh Set
intersection(left, right)      -> fresh Set
difference(left, right)        -> fresh Set

sameMembers(left, right)       -> Boolean
isSubset(left, right)          -> Boolean
isSuperset(left, right)        -> Boolean
isDisjoint(left, right)        -> Boolean
```

`identity_set` exposes the same operation names and result shapes where they are
meaningful, with IdentityMap membership semantics throughout. The parallel
surface is convenience, not a common prototype or hidden shared hierarchy.

### Basic observation and mutation contracts

`contains(set, element)` delegates membership to the underlying keyed
`containsKey` law and returns its canonical Boolean result. `size(set)` returns
the underlying Map/IdentityMap association count.

`add(set, element)` performs one ordinary `atPut(element, true)` on the
well-formed Set and, after successful completion, returns the exact Set object.
It does not manufacture a Boolean "changed" result and does not perform a
separate preflight lookup. On an already-present member, the well-formed
representation already maps that representative key to `true`, so the ordinary
replacement preserves membership, representative identity, and insertion
position.

`remove(set, element)` performs the ordinary keyed `remove(element)` operation,
ignores the removed marker value, and after success returns the exact Set object.
An absent member therefore follows Core Map removal and signals an Error rather
than returning `false`, `null`, or another absence sentinel. The library does
not promise to recover and return the stored representative key when the query
is merely `==`-equivalent to a distinct stored object; the public Map removal
protocol returns the mapped value, not that representative key.

Open/closed/frozen behavior follows directly from Core Map rules. In particular:

```text
open Set:
    add absent       allowed
    add present      allowed
    remove present   allowed

closed Set:
    add present      allowed (ordinary value replacement true -> true)
    add absent       Error
    remove           Error

frozen Set:
    add              Error
    remove           Error
```

Read-only operations remain available whenever their underlying Map operations
are available.

### `each` callback and snapshot contract

`each(set, block)` exposes members only, not representation markers. It visits
one element argument per entry in the underlying Map's insertion-order shallow
snapshot and returns the exact Set object after normal completion:

```text
block(element)
```

The implementation may derive this through ordinary Map/IdentityMap `each`, so
its member sequence inherits the underlying association snapshot: later
insertions, removals, or marker replacements do not alter the already-established
visit sequence, and nested traversals establish independent snapshots.

A Standard Library module cannot reproduce Core's internal non-invoking
callability preflight without adding a new primitive. LIB001 does not add one.
Callback validation therefore occurs through the actual ordinary invocation of
`block(element)`. An empty Set neither invokes nor otherwise inspects `block` and
returns normally. On a non-empty Set, the first attempted invocation may signal
an ordinary lookup/arity/invocation Error; that failure stops traversal and
completed effects are not rolled back. Callback arity is never prevalidated.

### Fresh-result algebra and deterministic order

Only `add` and `remove` mutate their Set argument in the initial surface. The
algebraic combinators return fresh open Sets and do not mutate either input:

- `union(left, right)`: insert left members in left traversal order, then right
  members not already represented in the result in right traversal order;
- `intersection(left, right)`: keep matching left representatives in left
  traversal order;
- `difference(left, right)`: keep non-matching left representatives in left
  traversal order.

No initial `formUnion`, `unionInto`, `intersectInPlace`, `subtract`, or other
mutating algebra synonym is added merely for familiarity.

The Boolean predicates are deterministic and short-circuit in the traversal
order implied by their left/right roles:

- `isSubset(left, right)` traverses `left` and stops at the first member absent
  from `right`;
- `isSuperset(left, right)` is the corresponding right-in-left membership test
  and therefore traverses `right`;
- `isDisjoint(left, right)` traverses `left` and stops at the first member found
  in `right`;
- `sameMembers(left, right)` first compares sizes; unequal sizes return `false`
  without key search, while equal sizes perform the same deterministic subset
  check from `left` into `right`.

Normal Set algebra uses Map `hash` / `==` and therefore may execute user behavior
in the exact ordinary keyed operations used to build/query results. IdentitySet
uses the non-overridable IdentityMap key law. No LIB001 operation introduces a
transaction or rolls back already-completed user effects after a later Error or
control transfer.

### Equality and hashing boundary

A Set remains an identity-bearing mutable Map. Consequently:

```text
left == right
```

continues to mean the ordinary Map/Object equality contract and does **not**
become mathematical Set equality merely because both values satisfy the
library invariant. `sameMembers(left, right)` is the explicit membership-
equivalence operation.

LIB001 likewise adds no content-derived `hash` for mutable Sets. Code that needs
structural/persistent hashing requires a separately designed policy rather than
silently destabilizing the standard Map identity hash.

## Array algorithms direction

After the Set/IdentitySet foundation, the next candidate LIB001 surface is eager
sequential Array algorithms corresponding to vocabulary already present in Core
parallel operations:

```text
map
filter
findIndex
reduce
sort
```

Initial direction:

- eager rather than lazy;
- transformations that naturally produce a sequence return a fresh standard
  Array rather than attempting Smalltalk-style automatic result "species"
  preservation;
- do not add a universal `Iterator`, `Enumerable`, `Sequence`, `Stream`, or view
  abstraction merely to implement these operations;
- exact callback validation, result/failure behavior, sorting contract, mutation
  snapshot behavior, and opportunities to share laws with the parallel surface
  must be re-audited before API publication.

A lazy traversal/pipeline layer may be justified by future measured or semantic
pressure (for example repeated large temporary Arrays, infinite sequences, or
resource traversal). If needed, it should be a separately designed abstraction
with explicit lifetime, suspension, mutation-visibility, and error-timing rules.

## Deliberately deferred collection ideas

The following are useful prior-art concepts but are not justified merely by
familiarity and are outside the initial LIB001 nucleus:

- Bag / multiset;
- Queue / Deque / Stack;
- PriorityQueue;
- SortedSet / tree-backed Set;
- Iterator / Enumerable / Sequence;
- lazy Stream / pipeline abstractions;
- backed collection Views;
- one universal `Collection` prototype or hierarchy.

C++ container adaptors in particular suggest that Stack/Queue-like concepts may
later be roles over suitable storage rather than new representations. Core Array
is intentionally fixed-size under ordinary indexed mutation, so a growable
sequential storage requirement must be demonstrated and designed rather than
silently added to Array.

## Import and distribution convention

The general naming contract is recorded in `docs/project/STANDARD_LIBRARY_NAMING.md`.
CLI004 introduced standard-distribution resolution; CLI005 closes exact-case
portable naming before further LIB001 surface accumulates.

CLI004 closes the standard-library resolution prerequisite without changing Core
module semantics. The official CLI host resolver uses the reserved distribution
specifier namespace:

```text
std:<logical-module-name>
```

For LIB001, the portable form is therefore, for example:

```protos
sets: import("std:collections/Set")
```

The resolver contract is intentionally narrow:

- `std:` names are absolute distribution identifiers and never participate in
  user/project search paths or relative resolution;
- the canonical `ModuleKey` is the same logical `std:` identifier, independent
  of the installation filesystem path and importing module;
- the physical `.protos` extension is a distribution detail and is not part of
  the specifier;
- portable logical-name segments begin with an ASCII letter and then use only
  ASCII letters, digits, or `_`, with `/` only as the segment separator;
- case is significant in the canonical `std:` identity, and each physical
  directory/file component must match the distributed spelling exactly even
  on a case-insensitive host filesystem;
- sibling distribution entries that differ only by case are forbidden and
  are rejected as ambiguous if encountered;
- the first segment `core`, in any ASCII case, is excluded because
  `protos/lib/core/` is bootstrap Core, not importable Standard Library;
- Windows reserved device-name segments are excluded case-insensitively so
  one standard distribution remains materializable across supported hosts;
- a missing or invalid `std:` name fails through the existing Core import Error
  path with no fallback to a local file or package of the same name;
- the official resolver introduced by CLI004 intentionally does not define
  third-party package, relative-file, registry, versioning, or network lookup.

The source file for `std:collections/Set` is distributed at
`protos/lib/collections/Set.protos`. That physical mapping is host/distribution
policy under the existing module-resolution boundary; it is not a new Core
language rule.

## Remaining questions before Array slices are implemented

The focused Set/IdentitySet audit closes the resolver, representation,
constructor, mutator-result, marker-value, duplicate, algebra, order, callback,
absence, and equality-boundary questions for the initial Set surface.

Array algorithms remain intentionally less frozen. Before `LIB001-D` begins, a
fresh current-main audit must close the exact sequential contracts for:

1. callback callability/failure timing and whether the library can or should
   mirror any Core preflight behavior;
2. shallow snapshot timing and mutation visibility for `map`, `filter`, and
   `findIndex`;
3. exact `findIndex` absence result without inventing truthiness or a hidden
   sentinel;
4. `reduce` empty-input behavior, accumulator forms, callback arity/order, and
   result propagation;
5. `sort` comparator contract, result freshness, stability/order guarantees,
   callback effects/failures, and alignment with (without copying concurrency
   rules from) `parallelSort`;
6. whether the initial sequential Array API remains module-only after concrete
   implementation ergonomics are tested.

No unresolved Set question above is a blocker for `LIB001-A`.

## Implementation sequencing recommendation

LIB001 is partitioned into bounded slices so source, conformance evidence, and
publication remain reviewable. The canonical slice statuses live in
`docs/project/IMPLEMENTATION_STATUS.md`.

Recommended execution order is:

1. **LIB001-A — Set/IdentitySet construction and observation:** add the two
   ordinary stdlib modules with the `key -> true` representation, variadic
   module invocation, `contains`, and `size`, plus focal language-level tests.
2. **LIB001-B — Set/IdentitySet mutation and iteration:** add `add`, `remove`,
   and one-argument `each`, covering open/closed/frozen behavior, duplicate
   representatives, insertion order, snapshot mutation, failures, and the empty-
   Set callback boundary.
3. **LIB001-C — Set algebra and Set-area conformance:** add fresh-result
   `union`, `intersection`, `difference`, `sameMembers`, `isSubset`,
   `isSuperset`, and `isDisjoint`; validate deterministic order, normal versus
   identity membership, Actor transfer of underlying data, aliasing/cycles where
   applicable, and absence of any new native/runtime boundary.
4. **LIB001-D — eager Array map/filter/findIndex:** only after the recorded
   focused Array API audit closes that slice's remaining contracts.
5. **LIB001-E — Array reduce/sort and final LIB001 closure:** close the remaining
   eager Array surface, perform final cross-slice validation, and mark top-level
   LIB001 CLOSED only after publication evidence is complete.

The Set slices are intentionally sequential because B builds on the constructors
and basic role established by A, and C builds on the mutation/iteration surface
from B. The Array work is conceptually independent of Set algebra, but D remains
OPEN rather than pretending its still-unresolved callback/absence contracts are
ready. Its recommended placement after C is project sequencing, not a new Core
semantic dependency.

Do not add Bag, Queue/Deque/Stack, PriorityQueue, SortedSet, Iterator/Enumerable,
lazy Streams, collection Views, or a universal Collection hierarchy merely to
complete this work item. Any such facility must earn a later LIB item or an
explicit extension of LIB001 through demonstrated need and a fresh design audit.

## Acceptance tests for future LIB001 design changes

A proposed Collections abstraction should be rejected or redesigned when it
requires one or more of the following without a strong independent semantic
need:

- a new Core value family or runtime tag;
- a privileged standard-library identity recognized by Actor transfer;
- implicit deep freeze or hidden backing-state synchronization;
- a universal traversal state/lifetime model required only by eager algorithms;
- conflating `Map` and `IdentityMap` equality laws;
- hidden global/shared mutable state;
- changes to simple Core programs that do not import the library;
- host/JVM-specific semantics leaking into the portable library contract;
- a hierarchy whose primary purpose is classification rather than behavior that
  cannot be expressed through existing mechanisms.

The preferred design is the one that eliminates the most independent rules while
remaining ordinary Protos code and preserving implementation freedom.
