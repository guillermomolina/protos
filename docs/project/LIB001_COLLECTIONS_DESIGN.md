# LIB001 Collections Design Record

Status: Initial design audit complete; implementation not started
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

**Current recommendation: use C as the architecture to falsify during the first
implementation design slice.**

This is a design recommendation, not a normative statement that every future
Set facility must forever use this representation.

## Recommended Set / IdentitySet laws

The initial implementation should be designed around these laws unless a fresh
current-main audit exposes a contradiction:

1. **No Set runtime family or tag.** Set membership is a library interpretation
   of Map keys, not a new Core semantic category.
2. **Any compatible Map may play the Set role.** Applying Set operations to an
   existing Map observes/manipulates key membership on that same object; no
   hidden conversion or wrapper identity is required.
3. **Values do not define Set membership.** Membership depends only on key
   presence. A Set `add` applied to an already-present key must not overwrite an
   existing mapping value merely to normalize a hidden presence marker.
4. **Fresh Set construction may use an ordinary conventional Map value.** The
   exact value used for entries created by a Set constructor/add operation is
   intentionally still an API-design question because the underlying Map makes
   it observable through ordinary keyed access.
5. **Set equality/hash semantics come from Map keys.** Normal Set uses Map's
   existing `hash` + `==` key law. IdentitySet uses IdentityMap's existing
   semantic-identity law. No equality-strategy object is introduced initially.
6. **Set algebra ignores order; traversal does not need to.** Mathematical
   membership laws are order-independent. When traversal or a result-building
   operation exposes order, the design should preserve deterministic behavior
   derived from the underlying Map's defined traversal order rather than invent
   a contradictory "unordered" fiction.
7. **No special Actor rule.** Set-role data crosses Actors only through the
   already-applicable Map/IdentityMap transfer contracts; behavior is loaded
   locally from the library module.
8. **No special freeze/close rule.** Because the role is the same underlying
   object, ordinary Map/IdentityMap mutation state remains authoritative.

Candidate deterministic result-order rules to validate during API design:

- union: left keys in left traversal order, then right keys not already present,
  in right traversal order;
- intersection: surviving left keys in left traversal order;
- subtraction/difference: surviving left keys in left traversal order.

## Candidate initial Set surface

The exact spelling and return contracts are **not frozen** by this record. The
small initial capability set to design first is:

```text
empty
of
contains
add
remove
size
each
union
intersection
subtract
isSubset
isSuperset
isDisjoint
```

An analogous identity-set module should expose the same concepts only where the
semantics remain genuinely parallel. Do not introduce a common prototype merely
to make the names line up.

Questions such as whether mutators return the collection, affected member,
previous mapping value, a Boolean changed flag, or another ordinary result must
be resolved by comparing current Protos mutator conventions and composition
needs, not by copying Java/C#/Smalltalk convention.

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

## Import and distribution gap

Core module semantics already accept exact String specifiers and delegate
canonical resolution/source retrieval to a host module resolver. However, the
ordinary CLI/bootstrap path observed during this audit still needs a fresh
current-main check for how distributable standard-library modules under
`protos/lib/collections/` become resolvable in normal execution.

Do not hard-code a collections-specific import exception. Before LIB001 source is
published, design and implement the smallest general host/distribution resolver
path that makes standard-library modules ordinary resolvable modules. If that
requires Core/runtime implementation work rather than library code, track it
under the appropriate non-LIB work owner.

No import spelling is fixed here. Candidates such as `collections/set`,
`std/collections/set`, or another package convention must be evaluated together
with collision rules, relative imports, installation layout, and future
third-party package resolution.

## Open questions before implementation slices are frozen

A fresh current-main audit must close at least these questions:

1. What is the general standard-library module resolver/distribution convention?
2. What exact module names and import spellings are portable?
3. What are the exact normal results of Set/IdentitySet mutating operations?
4. Which conventional value is stored for a newly introduced Set-role key, given
   that ordinary Map access can observe it?
5. What are the exact constructor forms (`empty`, `of`, or alternatives),
   argument validation rules, duplicate handling, and evaluation order?
6. Which Set algebra operations are mutating versus fresh-result operations?
7. What exact deterministic traversal/result order should each operation expose?
8. Should initial Array sequential algorithms be module functions only, or is
   there a separately justified ordinary behavior-composition mechanism that
   preserves Actor transfer and current Core boundaries?
9. Which callback-domain, failure, suspension, and mutation-snapshot laws can be
   reused from existing Core sequential/parallel operations without silently
   changing their semantics?

## Implementation sequencing recommendation

Do not implement LIB001 as one large patch. After re-auditing the then-current
main branch:

1. close the general standard-library resolution/import prerequisite if it is
   still missing;
2. finalize the smallest Set/IdentitySet public contracts and implement them as
   ordinary distributable Protos modules;
3. validate Set algebra, Map/IdentityMap law preservation, mutation state, Actor
   transfer, aliasing/cycles, and no new native/runtime boundary;
4. add eager Array algorithms in small independently testable slices;
5. only then decide whether another concrete collection or a reusable traversal
   abstraction has earned its complexity through demonstrated duplication or a
   real workload.

Before assigning concrete `LIB001-A`, `LIB001-B`, ... slices, verify the current
repository and persist the resulting slice plan in the canonical project ledger.

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
