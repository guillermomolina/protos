# Protos Matching Protocol Semantics v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-11

This document is the primary normative owner of the ratified matching-protocol
semantics introduced by D071-D075 and D078. D075 ratifies the exact named
structural-projection request/result/failure contract, while D078 ratifies
open/subset named-object matching and declines a generic complete-view/remainder
protocol in Core v0.1. The latest matching specification revision is
`0.1.404`.
It deliberately does **not** define concrete matching-expression grammar,
case/arm/default syntax, which source expressions or future surface forms denote
ordinary value patterns, concrete guard syntax, exhaustivity, standard pattern taxonomy, or named-binding syntax. Those remain unresolved
until separately ratified.

## 1. Scope and architectural boundary

Protos matching is protocol-oriented rather than based on a closed compiler-owned
pattern universe.

Recognition policy belongs to the pattern side. A subject may separately expose
an explicit logical structural view to generic structural patterns. These are
distinct responsibilities: the pattern decides whether/how it recognizes a
subject; the subject decides which logical structural components it chooses to
expose.

Matching introduces no class, record, struct, sealed-variant, matcher registry,
hidden pattern type, or host-reflection institution.

## 2. Pattern-owned recognition

The required public matcher authority is the ordinary one-argument selector:

```text
pattern.match(subject)
```

There is exactly one required semantic matcher entry point. Core v0.1 does not
require a paired `matches(subject)` predicate selector, a caller-visible
recognition/extraction mode or hint, a callback/CPS matcher path, or a mutable
capture sink.

`match` uses ordinary Protos lookup, dispatch, argument evaluation, invocation,
Error propagation, non-local control behavior, cancellation, and explicit
suspension semantics. Matching adds no truthiness and no implicit `Future.value`
or other implicit awaiting/adoption step.

A future standard matching construct that evaluates a subject expression must
evaluate that subject exactly once at the matching-expression boundary. Arms are
semantically considered in source order, and the first successful arm wins.
Execution of a selected arm remains ordinary invocation of the selected callable
/ Closure according to the existing callable semantics.

No concrete matching-expression or arm syntax is selected by this revision.

### 2.1 Default ordinary value-pattern behavior

Core v0.1 supplies a standard root matcher behavior so an ordinary Protos value
can act as an ordinary zero-capture value pattern without a hidden wrapper,
literal-specific recognition path, matcher registry, or second public matching
operator.

The standard root selector is:

```text
Object.match(subject)
```

When ordinary lookup selects this standard root behavior, the original matcher
receiver remains the pattern value. For one invocation, the behavior performs
**exactly one ordinary equality send with the original pattern receiver:**

```text
this == subject
```

The equality result is returned unchanged. Under the existing equality contract,
canonical `false` therefore becomes the D072 no-match result and canonical
`true` becomes D072 successful recognition with zero captures. The standard
root behavior never produces a capture Array.

The equality operation is ordinary Protos behavior. Its lookup, dispatch,
effects, Error behavior, non-local control, cancellation, and explicit suspension
compose exactly as they do outside matching. The existing equality result
contract remains authoritative: an invalid normal `==` result is an equality
protocol violation and is not converted to mismatch or truthiness.

The standard root matcher does **not**:

- perform an `===` identity pre-check or shortcut;
- call `subject == this` as a fallback or symmetry repair;
- invoke `==` more than once for one root matcher invocation;
- consult `hash`, `identityHashOf`, Map membership, or another indexing/hash
  mechanism;
- coerce a result, apply truthiness, retry, or reinterpret an Error as mismatch;
- implicitly await or adopt a Future; or
- introduce a special rule for Number, String, `true`, `false`, `null`, or
  another literal/value family.

An object that wants recognition semantics different from its ordinary semantic
equality may override or shadow `match(subject)` through ordinary object behavior.
That selected override is then the same D073 matcher authority and need not use
`==` at all. This allows domain matchers such as ranges, regular expressions, or
other abstractions to define recognition without redefining their ordinary
equality relation.

D081 defines the semantics of this ordinary value-pattern default; it does not
select concrete matching grammar or which source expressions are admitted as
value-pattern forms. A future syntax decision may denote an ordinary value as a
pattern only by preserving the single `pattern.match(subject)` semantic path.

Implementations may specialize standard built-in value cases, inline the root
matcher/equality path, or build literal decision structures only when observable
behavior is identical to ordinary lookup and the exactly-once `match`/`==`
semantics above. In particular, an optimization must not skip an observable
custom equality invocation merely because `this === subject` is already known.

## 3. Matcher outcome and positional-capture carrier

When the result of `pattern.match(subject)` is consumed as a matcher outcome, the
normal result contract is exact:

```text
false        -> no match
true         -> successful match with zero captures
[x]          -> successful match with one positional capture
[x, y, ...]  -> successful match with two-or-more positional captures
```

Only the canonical Boolean `false` denotes no match. Only the canonical Boolean
`true` denotes successful recognition with zero captures. A successful matcher
with captures returns a **non-empty standard Array** whose elements, in order,
are the ordinary captured Protos values.

Every captured element may itself be any ordinary Protos value, including
`null`, either Boolean, an Array, Closure, Future, or arbitrary object. Therefore
`[null]`, `[false]`, `[true]`, and `[[]]` are all unambiguous one-capture results.
In particular a variadic/domain capture whose value is an empty Array is carried
as `[[]]`; the empty outer Array is not a second spelling for zero captures.

The following normal results are invalid matcher outcomes:

```text
[]
null
any Number
any String
any Future
any ordinary object that is neither canonical Boolean nor a non-empty standard Array
```

A standard matching consumer signals an ordinary `Error` when it receives an
invalid normal matcher outcome. Error, non-local return, cancellation, explicit
suspension behavior, and any other non-normal control transfer produced while
invoking the matcher propagate according to the already-existing Protos rules;
they are never encoded inside this result carrier.

A capture-producing matcher may return its non-empty Array even when a caller
ultimately ignores those captures. This revision deliberately defines no second
recognition-only semantic path. A later optional optimization protocol may be
considered only by a separate decision and must not redefine the success
relation established here.

### 3.1 Standard composite capture composition

For any standard composite pattern whose separately defined semantics evaluate an
ordered set or sequence of child matcher attempts, each immediate child remains
an **opaque ordinary matcher**. The composite invokes that child only through the
D073 authority:

```text
child.match(childSubject)
```

D083 does not create a new standard pattern taxonomy or concrete matching syntax.
It defines how a standard composite that already has child attempts composes
their D072 outcomes.

For one standard composite attempt, child attempts occur in the composite's
deterministic semantic child order. When that child domain is ordered, such as an
ordered child list, the order is left-to-right. Each attempted child is invoked
exactly once. A normal canonical `false` result makes the composite fail
immediately; later children are not invoked.

A successful child contributes captures according to its exact D072 result:

```text
true         -> contribute zero captures
[c1, ...]    -> contribute c1 ... cn in carrier order
```

Only the **outer D072 carrier level** participates in this concatenation.
Captured values are never recursively flattened merely because a capture value is itself an Array
or another collection. For example:

```text
child -> [[1, 2]]
```

publishes exactly one capture whose ordinary value is the Array `[1, 2]`. Amid
other captures, a standard parent may therefore produce:

```text
[a, [1, 2], b]
```

but it must not reinterpret that result as:

```text
[a, 1, 2, b]
```

Likewise, `true` contributes no capture, `[true]` contributes one captured
Boolean, `[false]` contributes one captured Boolean, and `[[]]` contributes one
captured empty Array. The containing composite inserts no placeholder for a
zero-capture child.

Before invoking the next child, the standard composite shallowly consumes the
top-level indexed element references of each valid child capture Array. Mutation
of that carrier Array after this observation cannot retroactively change which
capture references the current composite attempt has already observed. This is
not a deep copy: a captured mutable object remains that same ordinary object.

If every child succeeds and the concatenated capture sequence is empty, the
composite returns canonical `true`. If every child succeeds and one or more
captures were contributed, the composite returns one standard non-empty Array
containing those capture references in composition order.

An invalid normal child matcher outcome is an ordinary D072 protocol violation
and causes `Error` at the consuming composite boundary; it is not mismatch or
zero-capture success. Error, non-local control, cancellation, explicit
suspension, and other non-normal behavior produced by a child propagate under
ordinary Protos rules. Effects already performed by earlier attempted children
are not rolled back when a later child mismatches or fails.

Aggregation is explicit at the pattern's public result boundary. A pattern that
semantically wants several values to constitute **one capture** publishes an
ordinary aggregate value as one D072 capture. For example, a future repetition
or rest pattern that chooses to capture an Array `[v1, v2, v3]` as one value
publishes:

```text
[[v1, v2, v3]]
```

D083 does not choose repetition, optional, rest, or whole-subject pattern
semantics themselves.

Arbitrary user-defined `match(subject)` implementations remain constrained by
D072 and their own documented behavior only. D083 does not require child-pattern
topology introspection, a fixed capture arity, capture-name metadata, a
`CaptureFrame`, a mutable capture sink, a callback/CPS path, or another matcher
authority.

A future standard alternative/or-pattern form that exposes fixed source-level
bindings must provide a stable binding interface across its successful
alternatives, but D083 does not select the mechanism for proving or representing
that stability. Source binding names, duplicate-name rules, whole-subject alias
syntax, guard syntax, exhaustivity, and concrete match/arm grammar remain separate
decisions.

Implementations may inline or fuse standard composite layers, pre-size or
eliminate intermediate carrier Arrays, or write captures directly into internal
frame storage only when the observable result is equivalent to the semantics
above. Such optimization must preserve matcher dispatch, required call count and
order, Error/control/suspension behavior, shallow capture-value boundaries, and
the prohibition on recursive flattening.

### 3.2 Standard Array sequence-pattern semantics

Core v0.1 standard sequence matching is a standard pattern-owned specialization
for subjects that own **standard Array indexed state**. It does not define a
generic positional-object view or infer sequence membership from ordinary
indexing behavior.

An ordinary object does not become eligible for this standard sequence-pattern
contract merely because it defines or inherits `at`, `atPut`, `size`, `each`, an
iterator, numeric-key behavior, or other collection-like messages. Delegating to
an Array or copying Array behavior likewise does not confer standard Array
indexed state. `String`, `Bytes`, `Map`, iterators, generators, streams, and
arbitrary user-defined indexable objects do not participate automatically.

A standard sequence pattern presented with an ineligible subject returns
canonical `false` as ordinary mismatch. D084 introduces no implicit conversion,
registration table, `Sequence` family, host-type test, generic positional
deconstruction protocol, or fallback call to a subject-side sequence view.

#### 3.2.1 Fixed shape and one explicit remainder

A standard sequence pattern with `N` fixed child components and no remainder
component requires the subject Array's current indexed element count to be
exactly `N`. A different length causes canonical `false` before any element
child matcher is invoked. Fixed sequence matching is therefore exact-length by
default; extra elements are not silently ignored.

A standard sequence pattern may contain **at most one semantic remainder component**.
The remainder may occur between a fixed prefix and fixed suffix. For a pattern
with `P` fixed prefix children and `S` fixed suffix children, the subject length
must satisfy:

```text
length >= P + S
```

The remainder denotes exactly the contiguous unmatched middle range. It may
contain zero elements. More than one semantic remainder component is outside the
standard Core v0.1 sequence-pattern contract; Core does not choose a greediness,
backtracking, split-distribution, or subsequence-search policy for such a form.

A remainder component whose standard semantics merely accepts and discards the
unmatched middle does not require that middle to be traversed or materialized.
A remainder component that must itself receive the unmatched aggregate is given
the remainder value defined below.

#### 3.2.2 Shallow logical observation before child matching

For one standard sequence-pattern attempt, after standard Array receiver
eligibility is established and before **any** nested sequence child matcher is
invoked, the sequence matcher establishes one shallow logical observation of
the subject Array state needed by that attempt.

It determines the current indexed element count once for the attempt and
captures the element references required by all fixed prefix/suffix positions.
If a remainder child requires the unmatched aggregate, the unmatched middle
element references are captured as part of the same pre-child observation. A
discard-only remainder need not observe the middle element references.

The observation is shallow. If an observed Array element is a mutable ordinary
object, the same object reference is supplied to the child matcher; D084 does
not deep-copy or freeze captured element values.

Mutation of the original Array's indexed positions after this pre-child
observation cannot change which element references the current sequence-pattern
attempt has already selected. D084 introduces no transaction, deep snapshot,
global lock, or new synchronization primitive around those ordinary element
objects.

The standard sequence matcher performs this Array observation through the
standard Array semantic state. It **does not send ordinary `size`, `at`, `each`, iterator, or deconstruction messages**
to determine the sequence shape or obtain the observed standard Array elements.
This preserves the existing distinction between standard Array state and
ordinary user-defined indexing protocols.

#### 3.2.3 Remainder aggregate value

When a remainder component must receive the unmatched range as one ordinary
value, that value is a fresh **frozen standard Array** whose indexed elements are
exactly the shallowly observed unmatched middle element references, in original
ascending subject-index order.

An empty unmatched range therefore produces a fresh frozen empty standard Array.
Each semantically materialized remainder aggregate has distinct standard Array
identity for that attempt. Freezing is shallow: mutable objects referenced by
the remainder are not themselves frozen or cloned.

An implementation may defer physical remainder construction, share backing
storage, or use another internal representation only when the observable result
is exactly that of the required fresh frozen standard Array, including its
distinct identity, frozen behavior, element order, and independence from later
indexed mutation of the original subject Array.

If the remainder component merely accepts/discards any unmatched range and does
not semantically require a remainder subject value, implementations must not be
required to allocate such an Array merely to preserve an invisible artifact.

#### 3.2.4 Child execution and D083 composition

After the pre-child observation is established, child matchers execute in the
standard sequence pattern's semantic left-to-right component order: fixed prefix
children, the remainder child when one exists, then fixed suffix children.

Each attempted child is invoked exactly once. Canonical `false`, invalid matcher
outcomes, Error, non-local control, cancellation, explicit suspension, prior
effects, and capture concatenation obey the existing D072/D073/D083 contracts.

A remainder Array is one ordinary child subject. If a child matcher captures
that Array as one value, D072 carries it as one capture and D083 preserves it as
one capture. For example, a captured remainder value `[r1, r2]` may contribute:

```text
[[r1, r2]]
```

to a child result and remains the single captured Array value `[r1, r2]` in the
containing composite's capture sequence. It is never recursively flattened into
separate `r1` and `r2` captures.

#### 3.2.5 Boundary and future evolution

D084 defines standard finite Array sequence-pattern semantics only. It does not
standardize:

- Map/keyed patterns or Map remainder capture;
- String- or Bytes-specific pattern semantics;
- iterator, generator, stream, lazy, or infinite-sequence matching;
- find/subsequence/search patterns;
- repetition or optional-pattern semantics;
- a generic user-extensible sequence observation protocol;
- a standard `Sequence` semantic family;
- source syntax for sequence components, rest, capture names, or arms; or
- guard syntax, exhaustivity, identity-pattern syntax, or recognition-only fast paths.

A user/library pattern may already define domain-specific sequence recognition
through ordinary `pattern.match(subject)`. A future generic opt-in sequence
protocol remains possible only through a separate explicit decision backed by
concrete interoperability evidence.

Implementations may specialize standard Array receiver tests, scalarize the
pre-child observation into frame slots, avoid unused remainder work, fuse
standard child matchers, or optimize fresh frozen remainder storage only when
the observable behavior remains identical to this contract.

### 3.3 Standard Map keyed-pattern semantics

Core v0.1 standard keyed matching is a standard pattern-owned specialization for
subjects that own **normal standard `Map` keyed-entry state**. It does not
define a generic mapping capability or infer Map-pattern participation from
ordinary indexing, member, or iteration behavior.

An ordinary object does not become eligible for this standard Map-pattern
contract merely because it defines or inherits `at`, `atPut`, `containsKey`,
`size`, `each`, another iteration facility, String-keyed indexed contents, or
Map-like ordinary slots. Delegating to a Map or copying Map behavior likewise
does not confer standard Map keyed-entry state. `IdentityMap` does not
participate automatically because its identity-key relation is a distinct
standard collection contract.

A standard Map pattern presented with an ineligible subject returns canonical
`false` as ordinary mismatch. D086 introduces no implicit conversion, generic
`Mapping` family, mapping registry, host-type test, generic keyed-projection
protocol, or fallback call to subject-side mapping behavior.

#### 3.3.1 Keyed requirements and stable association observation

For one standard Map-pattern attempt, before any query-key `hash` / `==`
behavior or nested mapped-value child matcher is executed, the matcher
establishes one **stable shallow logical snapshot** of the subject Map's current
associations.

For every association in that snapshot, the semantic observation preserves:

```text
stored representative key reference
mapped value reference
recorded key hash
relative insertion-order position
```

The observation is shallow. Keys and mapped values remain the same ordinary
objects and are not cloned, frozen, or otherwise transformed merely because
matching observes them.

Mutation of the original Map's keyed-entry state after snapshot establishment
does not add, remove, replace, or reorder associations in the current matching
attempt. D086 introduces no transaction, rollback, global Map lock, or deep
snapshot of contained key/value objects.

The snapshot is semantic, not a required physical representation. An
implementation may use copied association references, versioned/persistent
table state, copy-on-write storage, retained generations, or another mechanism
when the observable result is identical. A Map pattern that does not request a
remainder is not required merely by D086 to allocate a public copied Map.

Each standard keyed requirement consists semantically of:

```text
query key value
mapped-value child pattern
```

The query key is an ordinary value used for Map key lookup. D086 does **not**
make that position an arbitrary pattern scanned against every subject key.
Which source forms construct or evaluate query-key values remains a separate
surface-language decision.

#### 3.3.2 Key resolution uses the existing normal-Map relation

Each keyed requirement is resolved independently against the stable association
snapshot using the existing normal standard-Map key-search relation from
`VALUES_AND_COLLECTIONS.md`.

For one keyed requirement, the matcher:

1. computes the query key's current standard `hash` exactly once for that search;
2. applies the existing standard Map hash-result and comparison-scope contracts;
3. considers only snapshot associations whose recorded hash equals that query
   hash;
4. considers those candidates in snapshot insertion order;
5. sends exactly one ordinary:

   ```text
   queryKey == storedRepresentativeKey
   ```

   for each candidate that is actually compared;
6. selects the first candidate whose comparison returns canonical `true`;
7. continues to the next candidate on canonical `false`; and
8. returns canonical `false` for the containing Map pattern when no candidate
   matches.

The standard Map pattern does not reverse equality, try both directions,
substitute `===`, recompute candidate recorded hashes, use `Object.match` as a
key relation, or perform `containsKey` followed by `at`. Map's existing
`hash` + query-side `==` search remains the sole standard key-equivalence
authority for normal Map matching.

`hash` and `==` remain ordinary Protos behavior. Their effects, Error,
non-local control, cancellation, explicit suspension, invalid-result failures,
and existing Map comparison-scope restrictions remain observable and propagate
unchanged. Effects are not rolled back.

A missing requested key is ordinary pattern mismatch, not the ordinary
missing-key `Map.at` Error. Conversely, a present association whose mapped value
is `null`, `false`, `true`, an Array, Closure, Future, or any other ordinary
value remains present and supplies that exact value to its mapped-value child
pattern.

All keyed requirements are resolved and their selected mapped-value references
are fixed before the first mapped-value child matcher is invoked. Later child
effects therefore cannot change which subject associations or mapped-value
references were selected for the current attempt.

#### 3.3.3 Repeated/equivalent keyed requirements

Multiple keyed requirements may select the same snapshot association.

Each requirement performs its own ordinary Map key search under §3.3.2. If two
query key values both resolve to the same association, their mapped-value child
patterns are repeated constraints on that same selected mapped-value reference.

D086 introduces no duplicate-query-key validation pass or duplicate-key Error.
For residue accounting, a subject association selected by one or more
requirements counts as selected once.

#### 3.3.4 Residue policy: open, exact, or matched remainder

A standard Map pattern has one semantic residue policy:

```text
ignore          -> open/subset matching
require-empty   -> exact keyed matching
match-remainder -> match the unmatched associations as one Map value
```

`ignore` is the default. Every keyed requirement must resolve and every
mapped-value child attempted later must succeed, but unrelated snapshot
associations do not themselves cause mismatch. Consequently a zero-requirement
open standard Map pattern recognizes any eligible normal standard Map.

For `require-empty`, structural resolution succeeds only when every snapshot
association was selected by at least one keyed requirement. No remainder Map
needs to be allocated merely to test that no unmatched association remains.

For `match-remainder`, the residual association set is every snapshot
association not selected by any keyed requirement. The remainder child receives
the ordinary remainder value defined below.

Residue policy does not change key equality, keyed-requirement search, or
mapped-value selection. It only determines what the pattern requires of
unselected snapshot associations.

#### 3.3.5 Fresh frozen standard Map remainder

When `match-remainder` semantically requires the residue value, that value is a
fresh **frozen normal standard `Map`** containing exactly the unmatched snapshot
associations in their original relative insertion order.

For each retained association, remainder construction preserves:

```text
stored representative key reference
mapped value reference
recorded key hash
relative insertion order
```

Remainder construction does not send ordinary user-visible `hash`, `==`,
`atPut`, `each`, or another iteration/key protocol merely to rebuild those
associations. In particular, a mutable stored key whose current `hash` has
changed does not have its recorded hash silently recomputed while the remainder
is created.

An empty residual set produces a fresh frozen empty normal standard Map.

The remainder is a distinct identity-bearing Map object for each semantically
materialized attempt. Freezing is shallow: the representative key and mapped
value objects are not recursively frozen or cloned.

Implementations may defer physical remainder materialization until the
remainder child is reached, share internal backing state, or use another
representation only when observable behavior is exactly that of the required
fresh frozen normal standard Map, including distinct Map identity, frozen
behavior, preserved association state/order, and independence from later
keyed-entry mutation of the original subject Map.

An open/subset pattern that ignores residue must not be required to materialize
a remainder Map. An exact pattern need only establish residual emptiness.

#### 3.3.6 Child execution and D083 composition

After structural key resolution and residue validation are complete,
mapped-value child matchers execute in deterministic keyed-requirement order.
Each attempted child is invoked exactly once and receives the mapped-value
reference fixed during the snapshot-resolution phase.

For `match-remainder`, the remainder child executes after the mapped-value
children if all earlier children succeed. Implementations may therefore defer
remainder materialization until that child is actually reached.

Canonical `false`, invalid matcher outcomes, Error, non-local control,
cancellation, explicit suspension, prior effects, and positional capture
composition obey the existing D072/D073/D083 contracts.

A remainder Map is one ordinary child subject. If a child matcher captures that
Map as one value, D072 carries it as one capture and D083 preserves it as one
capture. Its entries are never recursively converted into separate captures.

#### 3.3.7 Boundary and future evolution

D086 defines standard finite normal-Map keyed-pattern semantics only. It does not
standardize:

- `IdentityMap` keyed-pattern participation;
- arbitrary key-pattern scanning/search across subject entries;
- defaults or optional behavior for absent keys;
- a generic user-extensible keyed-projection/mapping protocol;
- keyed patterns for arbitrary `at`/`containsKey` objects;
- Map-entry repetition, quantification, or search patterns;
- concrete Map-pattern, exactness, remainder, key-expression, capture, or arm
  syntax;
- named capture/binding spelling or duplicate binding-name rules;
- guard syntax, exhaustivity, identity-pattern syntax, or recognition-only fast paths.

A user/library pattern may already define domain-specific keyed recognition
through ordinary `pattern.match(subject)`. A future generic opt-in keyed
observation protocol, `IdentityMap` specialization, or entry-search pattern
remains possible only through a separate explicit decision backed by concrete
use evidence.

Implementations may specialize normal standard Map receiver checks, retain
versioned snapshot state, optimize query lookup, scalarize selected values, test
residual emptiness without materializing a Map, and lazily materialize remainder
state only when all observable behavior remains identical to this contract.

### 3.4 Capture-to-arm binding ABI

D072/D083 remain the sole standard runtime capture representation. D088
introduces no named matcher-result carrier, binding Map, `CaptureFrame`,
`CaptureSignature`, capture-name registry, or required matcher-topology
introspection.

Once one candidate pattern has **completely succeeded** and its normal outcome
has been validated under D072, the selected arm consumes that successful capture
interface through **ordinary Protos callable/Closure invocation**.

The capture-to-arm calling convention is:

```text
D072 result              selected-arm capture actuals
-----------              ----------------------------
true                     zero capture actual arguments
[c1, ..., cn]            c1, ..., cn as n positional actual arguments
```

Each positional capture is passed as the exact ordinary captured value. A
captured Array, Map, Closure, Future, or other aggregate/value remains one
argument. D088 never recursively expands a capture merely because that captured
value is itself a collection.

#### 3.4.1 Binding commitment occurs only after complete success

Source-visible arm bindings do not exist during tentative or incomplete
matching.

A containing pattern may perform ordinary matcher calls, produce intermediate
captures, execute effects, and later mismatch. Such prior matcher effects remain
governed by the existing D083 rules and are not rolled back, but no
source-visible selected-arm binding state is created merely because an earlier
subpattern already succeeded.

The selected arm's ordinary invocation activation and its parameter bindings are
established only after the candidate pattern has succeeded completely and its
D072 carrier has been accepted.

D088 therefore requires no tentative binding environment, mutation log, lexical
rollback protocol, or binding transaction.

#### 3.4.2 Source names belong to the arm/source interface

A source-visible binding name belongs to the source arm/binding interface, not
to arbitrary matcher metadata.

For a fixed source binding interface, the language implementation establishes a
deterministic ordered mapping from source binders to D072 capture positions.
Those capture values become ordinary positional actual arguments of the
selected arm, and ordinary Closure parameter binding makes the corresponding
names ordinary parameter slots in the arm invocation activation.

Concrete source syntax may later place binder names visually at nested
structural pattern sites. Such syntax may compile those source sites to capture
positions and ordinary arm parameters; it does not require the runtime
`pattern.match(subject)` result to carry those names.

Within one fixed source arm-binding interface, binding names are **linear**:
each source binding name is declared at most once. Duplicate binder names do not
implicitly mean equality, conjunction, shadowing, or last-write-wins behavior.
Recognition constraints belong to patterns rather than to duplicate consumer
names.

#### 3.4.3 Fixed and dynamic capture arity

A fixed arm-binding interface consumes captures positionally.

When the producer's capture interface and the fixed consumer interface are
statically/source-structurally known to be incompatible, an implementation
should reject that source before execution rather than deliberately construct an
arm invocation known to fail.

Arbitrary matcher objects remain free to produce any D072-valid capture count
allowed by their semantics. D088 does not require them to publish a fixed
capture arity or capture-name/signature metadata.

A consumer that intentionally accepts a variable number of captures may use the
already-standard ordinary Closure rest-parameter semantics. Excess positional
capture actuals then participate in the same ordinary rest binding used by any
other Closure invocation, including its fresh frozen rest Array contract.

If a candidate pattern has already succeeded but ordinary selected-arm
invocation cannot accept the supplied capture arity, the resulting callable
binding/arity failure is an **ordinary invocation Error**. It is not retroactive
pattern mismatch and does not authorize trying a later arm.

D088 introduces no implicit missing capture, placeholder argument, capture
padding, silent capture dropping, or automatic aggregate conversion merely to
make an incompatible arm callable.

#### 3.4.4 Future alternative-pattern binding compatibility

D088 does not define OR/alternative recognition, retry, ordering, side-effect,
or backtracking semantics.

It fixes the consumer-side invariant such a later standard alternative form must
respect:

> when several alternatives are exposed through one fixed source arm-binding
> interface, every successful alternative must map its public captures onto the
> same ordered logical arm-binding interface.

Different alternatives may obtain a logical binding from different structural
positions, but the selected arm must not receive a branch-dependent binding
layout.

This compatibility may be proved or rejected by source/compiler pattern
structure. D088 does not require arbitrary runtime matcher objects to publish
capture names solely to support future alternatives.

#### 3.4.5 Ordinary invocation semantics remain authoritative

After successful capture conversion into positional actual arguments, ordinary
Closure/callable semantics remain authoritative for activation creation,
parameter binding, `args`, rest binding, receiver/callable behavior, Error,
non-local return, cancellation, explicit suspension, and all other ordinary
invocation behavior.

Passing a captured value to an arm preserves ordinary Protos argument-passing
identity. D088 performs no cloning merely to create a binding.

An implementation may specialize a known standard pattern and known arm,
scalarize capture values directly into arm parameter/frame state, or eliminate
an otherwise unobservable intermediate capture Array only when observable
behavior is exactly equivalent to:

```text
pattern.match(subject)
        ↓
D072 validation / D083 capture sequence
        ↓
ordinary selected-arm invocation with positional capture actuals
```

Such specialization must preserve matcher dispatch, matcher call count/order,
effects, Error/control/suspension behavior, aggregate capture boundaries,
ordinary arm invocation behavior, and failures that would be observable from an
invalid or incompatible result/interface.

#### 3.4.6 Boundary and future evolution

D088 does not standardize:

- concrete `match`, `case`, arm, default, binder, or capture syntax;
- OR/alternative recognition/backtracking semantics themselves;
- whole-subject alias/binder semantics or spelling;
- guard syntax or exhaustivity;
- repetition or optional-pattern semantics;
- sequence find/subsequence or stream matching;
- named arguments or another callable parameter category;
- a first-class pattern reflection API;
- mandatory capture-name/arity/signature metadata on arbitrary matchers; or
- parser/runtime implementation of the future matching surface.

A future explicit whole-subject alias can remain additive by contributing the
subject as one ordinary capture at a separately ratified position. Optional
tooling/debug or pattern-introspection metadata can likewise map source names to
capture positions without changing the D072/D083 runtime carrier or this
capture-to-arm ABI.

### 3.5 Standard ordered alternative-pattern semantics

D090 defines the standard semantic behavior of an alternative-pattern composite
without selecting concrete source syntax.

For one alternative-pattern attempt, the alternatives have one deterministic
semantic order. They are attempted in that order. Each attempted alternative is
invoked exactly once through the existing D073 matcher authority:

```text
alternative.match(subject)
```

Every attempted alternative receives the same ordinary subject value supplied to
the containing alternative-pattern attempt. The alternative composite does not
re-evaluate, clone, freeze, snapshot, or otherwise replace that subject merely
because an earlier alternative mismatched.

#### 3.5.1 Ordered D072 outcome handling

Each attempted alternative's normal result is consumed only through D072:

```text
alternative result       alternative-composite behavior
------------------       ------------------------------
false                    try the next alternative
true                     succeed immediately with zero captures
[c1, ..., cn]            succeed immediately with those captures
```

Only canonical `false` advances to the next alternative.

A valid successful D072 result commits the alternative composite immediately.
No later alternative is attempted after that success. D090 introduces no
branch-success ranking, longest/best match, speculative comparison of several
successful branches, or parallel race among alternatives.

An invalid normal D072 result signals ordinary `Error` at the consuming
alternative boundary. Error, non-local control, cancellation, explicit
suspension, and other non-normal control behavior propagate normally from the
currently attempted matcher and are not reinterpreted as mismatch.

If the current alternative suspends, the containing alternative attempt
suspends at that point. A later alternative is not started concurrently merely
because it could eventually match.

#### 3.5.2 Effects and first-success commitment

Ordered alternative matching is not transactional.

An attempted alternative may perform ordinary effects and later return canonical
`false`. Those effects are not rolled back. The next alternative, if any,
executes in the ordinary program state that exists after them.

Once one alternative succeeds, selection for that alternative composite is
final. A later failure outside the composite does not reopen it or continue with
a later alternative. In particular, if a future matching surface places a guard
or another outer condition after alternative recognition, failure of that later
condition does not make D090 resume the already-successful alternative composite
at its next branch.

D090 does not define concrete guard syntax; D092 §3.6 owns guard evaluation,
arm continuation, and terminal no-selection semantics.

#### 3.5.3 Capture and binding interface

D090 adds no new matcher-result carrier. D072/D083 remain authoritative.

A successful alternative contributes the successful D072 capture interface to
the containing matching operation. One capture remains one ordinary value;
Array, Map, Closure, Future, remainder, or other aggregate captures are not
recursively flattened merely because they crossed an alternative boundary.

D088 remains authoritative for source-visible bindings and selected-arm
invocation.

When several alternatives are exposed through one fixed source arm-binding
interface, every successful alternative must be projectable onto the same
ordered logical D088 binding interface. Different alternatives may obtain a
logical binding from different structural positions, but one selected arm must
not receive a branch-dependent logical binding layout.

That compatibility belongs to the source/consumer structure. D090 does not
require arbitrary runtime matcher objects to publish capture names, fixed
capture arity, a `CaptureSignature`, branch tag, binding Map, `CaptureFrame`, or
matcher-topology metadata merely to participate in alternatives.

Arbitrary matchers may retain dynamic D072 capture arity. A consumer
intentionally accepting a variable number of captures may use ordinary Closure
rest-parameter semantics. A fixed incompatibility that is provable from the
source/pattern structure should be rejected before execution. If recognition
has already succeeded and ordinary selected-arm invocation cannot accept the
actual capture arity, D088's ordinary callable binding/arity `Error` applies; it
does not retroactively become mismatch and does not cause another alternative
to be attempted.

#### 3.5.4 Nesting and optimization freedom

Nested standard alternative composites are semantically associative with
respect to their ordered attempt sequence. Grouping alternatives does not change
the left-to-right sequence in which their leaf alternatives are attempted.
Alternative choice is not commutative: reordering alternatives may change which
matcher runs, which effects occur, or which successful result wins.

Implementations may flatten nested standard alternative nodes, inline standard
matchers, scalarize or eliminate unobservable intermediate capture carriers, or
build specialized decision structures only when observable behavior is exactly
equivalent to this section.

Such optimization must preserve:

- ordinary matcher authority and lookup/dispatch behavior;
- the alternatives actually attempted and their semantic order;
- exactly-once invocation of each attempted alternative;
- first-success commitment;
- D072 outcome validation;
- ordinary effects and their visibility to later attempted alternatives;
- Error, non-local control, cancellation, and explicit suspension behavior;
- aggregate capture boundaries; and
- D088 fixed/dynamic arm-binding behavior.

In particular, an implementation may not replace effectful ordered matcher sends
with an unordered hash/index lookup, parallel race, or speculative multi-branch
execution merely because the alternatives appear otherwise optimizable.

#### 3.5.5 Boundary and future evolution

D090 does not standardize:

- concrete alternative, `match`, `case`, arm, default, or binder grammar;
- the spelling of an OR operator or whether one source spelling exists;
- concrete guard syntax; D092 §3.6 defines guard evaluation, arm continuation,
  and terminal no-selection semantics;
- exhaustivity or redundancy checking;
- repetition, optional, find, subsequence, or general backtracking patterns;
- whole-subject alias semantics or syntax;
- first-class Pattern reflection or mandatory capture-signature metadata;
- recognition-only matcher fast paths; or
- parser/runtime implementation of a future alternative-pattern surface.

Optional tooling/debug metadata or a future explicit pattern-introspection
protocol may describe source branches and logical binding projections without
changing the standard D072/D083/D088/D090 runtime contracts.

### 3.6 Standard guarded-arm selection and terminal no-selection

D092 defines the standard semantic behavior of guards and arm continuation
without selecting concrete source syntax.

The subject expression of a future standard matching construct remains evaluated
exactly once at the matching-expression boundary under §2. Arms are considered
in their deterministic semantic source order. Each candidate arm begins by
attempting its pattern through the existing D073 matcher authority against that
same already-evaluated subject value.

A canonical `false` pattern result is ordinary arm mismatch and proceeds directly
to the next arm without evaluating that arm's guard or body. A valid successful
D072 result establishes the candidate arm's D088 logical binding interface and
then proceeds according to the guard rules below.

#### 3.6.1 Guard evaluation and strict Boolean result

An arm with no guard is accepted immediately after its pattern succeeds. D092
does not model the absence of a guard as an implicit hidden Closure invocation or
Boolean send.

For a guarded arm, the guard is evaluated **exactly once** after complete pattern
success and before the arm body is selected. The source-visible binders defined
by that arm's D088 interface are available to the guard with the same ordinary
captured values that the arm body would receive if selected.

D092 does not add a second capture or binding carrier for guards. Implementations
may realize those binder values through ordinary activation slots, arguments,
frame state, or another internal representation, but the observable values and
binding interface remain those defined by D088.

Guard evaluation is ordinary Protos evaluation. Ordinary lookup, dispatch,
effects, Error signaling, non-local control, cancellation, and explicit
suspension semantics apply. There is no guard-specific pure/restricted
sublanguage, whitelist, truthiness, coercion, implicit `Future.value()`, or other
implicit await/adoption step.

A normally completing guard must produce exactly one canonical Boolean:

```text
true     -> accept/select this arm
false    -> reject this arm and continue with the next arm
```

Any other normal result signals an ordinary standard `Error` occurrence at the
guard-result boundary. In particular `null`, Numbers, Strings, Arrays, ordinary
objects, and Future values are invalid guard results rather than truthy/falsy
values.

Error, non-local control, cancellation, explicit suspension, and other non-normal
control behavior from the guard propagate normally. They are not reinterpreted
as canonical `false` and do not cause a later arm to be attempted.

#### 3.6.2 Guard rejection, effects, and D090 commitment

Canonical `false` rejects the **current arm**, not its already-successful pattern.
The matching operation then considers the next arm in source order.

Guard rejection never reopens a successful D090 alternative composite. If an
alternative pattern has committed to one branch under §3.5 and the containing
arm's guard later returns canonical `false`, the matching operation proceeds to
the next arm. It does not resume the alternative composite at a later branch and
does not invoke the successful pattern again.

Matching and guard evaluation are not transactional. Effects already performed
by a candidate pattern or its guard are not rolled back when the pattern
mismatches or the guard returns canonical `false`. A later arm therefore observes
the ordinary reachable program state that exists after those effects.

The D088 source bindings belonging to a rejected candidate remain scoped to that
arm's guard/body interface; they do not become ambient bindings for later arms.
This requires no rollback mechanism because arm bindings are not installed as
mutations of unrelated outer binding state.

A later arm is attempted against the same subject value produced by the one
matching-expression subject evaluation. D092 does not re-evaluate, clone, freeze,
or snapshot the subject between arms merely because an earlier guard rejected an
arm.

#### 3.6.3 Selected-arm body and result

A successful pattern with no guard, or a successful pattern whose guard returns
canonical `true`, selects that arm.

Only after this acceptance does the arm body execute. D088 remains authoritative
for mapping the successful D072 capture interface to the arm's ordinary
callable/Closure binding interface.

The selected arm body's normal result is the normal result of the matching
operation. Error, non-local control, cancellation, explicit suspension, and other
non-normal control from the selected body propagate under the existing ordinary
Protos rules. Such behavior does not resume arm search.

If a selected body suspends, the matching operation suspends at that ordinary
continuation point. A later arm is not speculatively or concurrently attempted.

#### 3.6.4 Terminal no-selection

If every arm either returns canonical `false` from pattern recognition or is
rejected by a canonical-`false` guard, and no arm is selected, the matching
operation signals one **fresh ordinary `Error`** under the standard failure
occurrence rules in `ERRORS.md`.

D092 introduces no `MatchFailure` standard Error prototype, no canonical-`null`
fallback result, and no implicit default branch. A normal selected arm remains
free to return `null`; that successful normal result is distinct from terminal
no-selection.

D092 does not select concrete catch-all/default syntax. If a future source
surface provides `default`, `else`, wildcard, or equivalent catch-all spelling,
that form must be semantically an ordinary irrefutable arm participating in the
same ordered arm-selection rules rather than a privileged fallback mechanism
that bypasses matching/guard semantics.

General static exhaustivity is not required by D092. A compiler or tool may prove
that a particular closed standard pattern set is exhaustive and optimize away an
unreachable no-selection path, but that proof does not create a closed pattern
universe or change the general runtime semantics for arbitrary matcher objects.

#### 3.6.5 Optimization and scaling

For an arm selected at position `k`, a straightforward implementation performs
the ordered matcher attempts needed to reach that arm and evaluates guards only
for successful candidate patterns. D092 requires no semantic per-arm rollback
log, speculative binding environment, global guard registry, or parallel branch
state.

Implementations may inline standard patterns and guards, fuse pattern/binding/
guard/body control flow, scalarize unobservable capture carriers, or build
specialized decision structures only when observable behavior is identical.

Such optimization must preserve:

- one evaluation of the subject expression at the matching boundary;
- semantic arm order and every observable matcher attempt;
- D072 result validation and D088 binding values;
- exactly one evaluation of each guard whose candidate pattern succeeds;
- strict canonical-Boolean guard result handling;
- ordinary effect visibility across rejected arms;
- the D090 no-reopen rule;
- Error, non-local control, cancellation, and explicit suspension behavior;
- selected-arm body/result behavior; and
- fresh ordinary Error behavior for reachable terminal no-selection.

In particular, an implementation may not duplicate an effectful guard, move it
before pattern success, evaluate later guards speculatively, or treat a guard
failure/control transfer as ordinary arm rejection merely to simplify a decision
tree.

#### 3.6.6 Boundary and future evolution

D092 does not standardize:

- concrete `match`, `case`, guard, arm, default, wildcard, or arrow grammar;
- exhaustivity or redundancy checking;
- whole-subject alias semantics or syntax;
- optional, repetition, find, subsequence, stream, or general backtracking
  patterns;
- first-class Pattern reflection or mandatory capture-signature metadata;
- a pure/restricted guard sublanguage or effect system;
- a dedicated no-match Error subtype;
- recognition-only matcher fast paths; or
- parser/runtime implementation of a future matching surface.

## 4. Explicit structural deconstruction boundary

Generic structural matching must not infer a subject's logical structure from
its implementation or ordinary object topology.

In particular, structural matching must not implicitly:

- enumerate local slots;
- treat ordinary delegated member lookup as structural fields;
- infer semantic-family membership or structural shape from `parent()`;
- treat indexed contents as ordinary object fields; or
- reflect JVM, Truffle, native, or other host representation.

This preserves the existing separation between object slots, delegation,
semantic-family membership, indexed collection state, and host implementation
machinery.

## 5. Named selective logical projection

For the subject-side half of generic object structural matching, the selected
architecture is a **named, selective logical projection**.

A generic structural matcher requests the logical field names it actually needs.
The subject controls which logical names it exposes and what ordinary Protos
values those names denote. A request for a subset of logical names does not, by
language rule, require enumeration or materialization of a complete structural
view.

The required public projection selector is the ordinary variadic message:

```text
subject.deconstructFields(...names)
```

Here `field` means a logical matching field. It does not mean a local slot,
delegated member, indexed entry, physical field, host-layout offset, or other
representation detail. The selector name deliberately keeps named logical
projection distinct from any future separately ratified positional
subject-deconstruction capability.

### 5.1 Request contract

A standard generic structural-projection request supplies zero or more
**pairwise-distinct semantic String values** as ordinary positional arguments.
Their ordinary argument order is the correspondence order for a successful
projection result. A dynamic standard Array of names may use the existing call
spread mechanism; named projection introduces no request-object, request-Array,
Map, Set, mode object, or second invocation mechanism.

A conforming generic structural matcher forms the complete requested-name vector
before projection and invokes `deconstructFields` once with that vector. It does
not probe one field at a time, retry projection, or use `null`, a Boolean, a
special Array, or another mode value to request a complete view.

The standard root behavior is:

```text
Object.deconstructFields(...names) -> false
```

for a valid request. Therefore an ordinary object that does not opt into named
structural projection inherits a normal no-view result through ordinary
delegation. An object opts in by ordinary overriding/shadowing of
`deconstructFields`; no registry, protocol type, `respondsTo` institution,
manual `parent()` traversal, or caught missing-lookup Error is required.

Invalid request arguments are not structural mismatch. A standard consumer or
standard root behavior that receives a non-String requested name or a duplicate
requested name signals an ordinary `Error` at the violated protocol boundary.

### 5.2 Normal result carrier

For a valid request, the normal result contract is exact:

```text
false        -> subject exposes no named structural view for this attempt
null         -> named structural view exists, but at least one requested logical name is unavailable
true         -> successful projection of zero requested names
[v1, ...]    -> successful projection of N > 0 requested names
```

Only canonical `false` denotes no exposed named structural view. Only canonical
`null` denotes participation in named structural projection with one or more
requested logical names unavailable. Both outcomes make the containing generic
structural pattern fail normally; they remain distinct inside the projection
protocol so absence of the capability is not conflated with absence of a
requested logical field.

For a request containing zero names, canonical `true` is the only successful
normal result. An empty outer Array is invalid rather than a second spelling of
zero-name success.

For a successful request containing `N > 0` names, the result must be a standard
non-empty Array of exactly `N` elements. Result elements correspond **one-to-one**
to requested names in request order:

```text
result[i] -> logical value for requested name names[i]
```

Each projected value is an ordinary Protos value and may itself be `null`,
`false`, `true`, an Array, Closure, Future, or arbitrary object. Thus `[null]`
means successful projection of one logical field whose value is `null`; it is not
the missing-field sentinel.

Any other normal result is invalid projection output. This includes an Array of
the wrong length, an empty Array, `true` for a nonzero request, a Number, String,
Map, Future, or arbitrary object outside the exact carrier above. A standard
structural-matching consumer signals an ordinary `Error` at that protocol
boundary rather than silently interpreting invalid output as mismatch.

### 5.3 Exactly-once observation and shallow snapshot

For one generic structural-projection attempt, the consumer:

1. forms the complete ordered request;
2. invokes `deconstructFields` **exactly once**;
3. completes and validates that invocation before any nested field subpattern is executed;
4. for a successful Array result, captures a **shallow ordered snapshot** of the returned Array's indexed element references immediately; and
5. runs nested field subpatterns against that captured ordered value sequence.

The consumer does not re-read the returned Array after nested matching begins and
does not re-invoke the subject to refresh a field. Mutating or aliasing the result
Array after the snapshot therefore cannot change which projected references the
current attempt observes. This is not a deep copy: if a projected value is itself
a mutable object, nested matching observes that same ordinary object.

`deconstructFields` remains ordinary Protos behavior. Error, non-local return,
cancellation, and explicit suspension propagate according to the existing
language rules. Matching adds no implicit await, adoption, retry, transaction,
or atomicity guarantee around an explicitly suspending projection method.

### 5.4 Representation and future-capability boundary

Physical/internal field reordering is not observable through this contract.
Adding an implementation cache, helper slot, method slot, delegated behavior, or
other non-projected state does not automatically alter the subject's logical
matching structure.

Core v0.1 does not impose a universal positional product layout on ordinary
objects. Domain-specific positional extraction remains possible through ordinary
pattern-owned `match(subject)` behavior and its D072 capture carrier. Array, Map,
Bytes, and other indexed contents remain governed by their existing collection
and indexing semantics rather than being reclassified as object fields.

D078 resolves generic **named-object** complete-view/remainder behavior for
Core v0.1 by standardizing **open/subset matching without generic remainder
capture**. This does not add a special full-view argument, sentinel, or mode to
`deconstructFields`, and it does not add a second complete-view authority.

D080 resolves the generic positional subject-deconstruction question for
Core v0.1: arbitrary objects do **not** acquire a universal positional product
layout or a required subject-side positional deconstruction protocol. Positional
domain extraction remains available through pattern-owned `match(subject)`, and
intrinsically ordered values remain governed by their own collection/indexing
semantics.

### 5.5 Open/subset named-object matching

Generic named object structural matching is **open/subset**. Only logical field
names explicitly requested through the D075 `deconstructFields(...names)`
operation participate in that structural attempt.

A subject may expose additional logical fields that the pattern did not request.
Those unrequested logical fields:

- do not cause the structural pattern to fail;
- are not enumerated or materialized merely because matching occurs;
- do not become captures implicitly; and
- do not become observable through slots, delegated lookup, indexed state,
  prototype ancestry, host reflection, or another fallback path.

Core v0.1 does not standardize a generic named-object `**rest`-like capture, a
complete logical-field schema, or a required complete-view operation. In
particular it requires no `deconstructAllFields`, `deconstructFieldNames`,
`deconstructFieldsAndRest`, `DeconstructionView`, or equivalent second
deconstruction authority.

A future whole-subject binding facility, if separately ratified, may bind the
original subject without requiring logical-field enumeration. D078 does not
select such syntax or binding semantics.

Collection-specific Map/sequence matching and remainder behavior is owned
by the separately ratified collection-specific contracts where defined;
D078 itself does not select or redefine those semantics.

A future complete logical-view facility may be considered only through a
separate explicit decision backed by concrete use evidence. Such a facility must
not silently redefine the D075 selective projection contract or make existing
open/subset patterns observe newly added logical fields.

### 5.6 Positional subject-deconstruction boundary

Core v0.1 does **not** require arbitrary objects to expose a generic positional logical-deconstruction protocol.

The generic subject-side structural protocol for ordinary record/object-like
matching remains the named selective D075 operation:

```text
subject.deconstructFields(...names)
```

D080 introduces no required `deconstruct()`, `deconstructPositions`,
`componentN`, ordered positional-schema metadata, dedicated positional-view
object, or request-polymorphic deconstruction selector.

No positional order is inferred from local slots, delegated members, source or
declaration order, D075 request/name order, indexed state, prototype ancestry,
host representation, or another reflective property. Adding or reordering
ordinary implementation state therefore cannot silently alter a subject's
generic positional matching contract, because Core defines no such contract.

This boundary does not remove positional extraction from the language.
A domain-specific pattern may use the already-ratified
`pattern.match(subject)` authority and return ordinary D072 positional captures.
Different patterns may therefore expose different legitimate domain views of
the same subject without forcing the subject to choose one universal component
ordering.

Likewise, Array and other intrinsically ordered or future sequence/tuple-like
values remain governed by their own collection/indexing semantics. D080 does
not reclassify indexed contents as generic object fields and does not select
future sequence-pattern syntax.

An object or library may expose an ordinary domain API whose values have
positional meaning. D080 only declines to elevate one such convention into a
required generic matching protocol for arbitrary objects.

A future opt-in subject-side positional protocol may be considered only by a
separate explicit decision backed by ecosystem evidence that a shared positional
contract provides independent value beyond pattern-owned extraction and
collection semantics. Such a future protocol must not silently derive order
from D075 named projection or redefine existing matching behavior.

## 6. Effects, ordering, and implementation freedom

Matching protocols are ordinary Protos behavior and may therefore have ordinary
observable effects unless a later, narrower contract explicitly says otherwise.
Implementations must not duplicate, omit, reorder, or replace observable matcher
or structural-projection behavior merely because a specialized implementation is
available.

Implementations may inline, open-code, scalar-replace, build decision structures,
cache semantically invisible implementation data, or otherwise specialize
matching when and only when the resulting observable Protos behavior is
equivalent to the ordinary protocol semantics.

No global matcher registry, global structural-schema registry, shared mutable
matching state, scheduler requirement, host reflection dependency, or runtime
backend is mandated by this specification.

## 7. Explicitly unresolved surface

This revision intentionally does not select:

- a `match` keyword or expression grammar;
- case/arm/default syntax;
- which source expressions or future surface forms denote ordinary value patterns;
- guard syntax or exhaustivity;
- a standard built-in pattern taxonomy;
- named capture/binding syntax;
- repetition and optional-pattern semantics;
- sequence find/subsequence and iterator/stream pattern semantics;
- whole-subject alias/binding syntax and semantics;
- a recognition-only matcher fast path.

Until those questions are separately ratified, implementations and libraries
must not treat them as implied by D071-D075 or D078.
