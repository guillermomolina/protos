# Protos Matching Protocol Semantics v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-17

This document is the primary normative owner of Core v0.1 matching semantics.
D131 supersedes the dedicated matching-language model introduced by D088,
D090, D092, D093, D095, D096, D100 and D103.

The latest matching specification revision is `0.1.416`.

Core v0.1 matching is protocol-first. The language retains one ordinary matcher
authority, ordinary callable invocation for selected bodies, direct structural
matching on standard Array/Map values, two standard helper matchers (`Any` and
`Capture`), and ordinary multi-way selection through `caseOf`.

The dedicated `match` / `case` / `when` pattern language, pattern-owned binding
syntax, OR/remainder/exact/alias/captures institutions, and matching-specific
static coverage framework are not part of Core v0.1 under D131.

## 1. Scope and architectural boundary

Matching is defined through ordinary Protos objects, messages, standard
collection state, Closures/callables, and ordinary Error/control behavior.

Matching introduces no class, record, struct, sealed variant, matcher registry,
hidden pattern type, capture frame, mutable capture sink, callback/CPS matcher
authority, or second recognition protocol.

The required public recognition authority is:

```text
pattern.match(subject)
```

Recognition policy belongs to the matcher receiver.

## 2. Matcher authority

`match` uses ordinary Protos lookup, dispatch, argument evaluation, invocation,
Error propagation, non-local control behavior, cancellation, and explicit
suspension semantics.

Matching adds no truthiness and performs no implicit Future adoption/awaiting.

There is exactly one required matcher entry point:

```text
pattern.match(subject)
```

Core v0.1 defines no paired `matches(subject)` predicate, recognition-only mode,
matcher registry, capture sink, or alternative hidden matcher interface.

### 2.1 Default ordinary matcher behavior

Core v0.1 supplies the inherited standard root behavior:

```text
Object.match(subject)
```

When ordinary lookup selects this standard root behavior, it performs exactly one
ordinary equality send:

```text
this == subject
```

The result is returned unchanged.

Therefore:

```text
false -> mismatch
true  -> successful recognition with zero captures
```

The standard root behavior does not:

- perform an identity shortcut;
- reverse equality;
- retry equality;
- call `hash`;
- use Map membership;
- coerce a result;
- reinterpret Error as mismatch; or
- implicitly await a Future.

An object may override/shadow `match(subject)` through ordinary Protos behavior to
define recognition semantics independent of its equality semantics.

## 3. Matcher outcome carrier

When consumed as a matcher result, the exact normal result contract is:

```text
false        -> mismatch
true         -> success with zero captures
[x]          -> success with one positional capture
[x, y, ...]  -> success with two-or-more positional captures
```

Only canonical `false` denotes mismatch.

Only canonical `true` denotes success without captures.

A successful matcher with captures returns a non-empty standard Array whose
elements, in order, are ordinary captured Protos values.

The empty Array is not another spelling of success-with-zero-captures.

The following normal values are invalid matcher outcomes:

```text
[]
null
any Number
any String
any Future
any ordinary object that is neither canonical Boolean nor a non-empty standard Array
```

A standard matcher consumer signals an ordinary Error when it receives an invalid
normal matcher outcome.

Error, non-local control, cancellation, suspension, and other non-normal behavior
propagate under the ordinary Protos rules.

## 4. Standard composite capture composition

A standard composite invokes each reached child only through:

```text
child.match(childSubject)
```

Children execute in the deterministic order defined by that composite.

Each reached child is invoked exactly once.

A canonical `false` result fails the composite immediately; later children are
not invoked.

A successful child contributes captures as follows:

```text
true       -> zero captures
[c1, ...]  -> c1 ... cn in carrier order
```

Only the outer matcher-result carrier is flattened.

A captured Array remains one captured value. For example:

```text
child -> [[1, 2]]
```

contributes one capture whose value is `[1, 2]`.

Before moving to the next child, the parent shallowly consumes the top-level
references of a valid child capture Array. Later mutation of that carrier Array
cannot retroactively change captures already observed by the current attempt.

If all children succeed and no captures were contributed, the composite returns
canonical `true`.

If one or more captures were contributed, the composite returns one non-empty
standard Array containing those references in composition order.

## 5. Standard helper matchers

Core v0.1 defines exactly two standard helper matcher objects.

### 5.1 `Any`

```text
Any.match(subject) -> true
```

`Any` accepts every subject and contributes no capture.

### 5.2 `Capture`

```text
Capture.match(subject) -> [subject]
```

`Capture` accepts every subject and contributes the subject as exactly one
positional capture.

`Any` and `Capture` are ordinary standard matcher objects, not keywords and not
dedicated pattern syntax.

Core v0.1 does not initially standardize `Or`, `Guard`, `Identity`, Array
remainder combinators, exact-Map combinators, or Map-remainder combinators.

## 6. Standard Array structural matching

Ordinary standard Array values provide structural recognition through
`Array.match(subject)`.

This is direct standard behavior of semantically eligible Array receivers; there
is no separate Array-pattern value family.

### 6.1 Receiver eligibility

The receiver must own standard Array indexed state.

An ordinary object does not become an Array merely because it:

- delegates to an Array;
- inherits Array behavior;
- defines `at`, `atPut`, `size`, `each`, iteration, or numeric-key behavior; or
- copies Array-like slots.

If ordinary lookup selects the standard Array `match` behavior for a receiver that
does not own standard Array indexed state, invocation signals an ordinary
invalid-receiver Error.

This rule does not fall back silently to `Object.match`.

### 6.2 Subject eligibility

If the subject does not own standard Array indexed state,
`Array.match(subject)` returns canonical `false`.

String, Bytes, Map, iterators, generators, streams, and arbitrary user-defined
indexable objects do not participate automatically.

### 6.3 Fixed exact shape

For one Array structural match attempt, the matcher Array and subject Array must
have exactly the same indexed element count.

A length mismatch returns canonical `false` before any child matcher invocation.

Core v0.1 defines no Array remainder/rest matching in this model.

### 6.4 Shallow observation

After receiver/subject eligibility is established and before invoking any child,
the attempt establishes one shallow logical observation of both:

- the matcher Array's current indexed matcher references; and
- the subject Array's current indexed subject references.

The observation fixes the current lengths and corresponding top-level references
for this attempt.

Mutation of either original Array after that observation cannot change which
matcher or subject references this attempt has already selected.

The observation is shallow: referenced mutable objects remain the same objects.

The standard implementation does not obtain shape/elements by sending ordinary
`size`, `at`, `each`, iterator, or deconstruction messages.

### 6.5 Child execution

Children execute left-to-right by ascending Array index.

For index `i`:

```text
matcherElement[i].match(subjectElement[i])
```

is invoked exactly once when reached.

Canonical `false` fails the Array match immediately.

Successful child captures compose according to section 4.

If every child succeeds, the Array matcher returns `true` or the composed
non-empty capture Array according to section 4.

## 7. Standard Map structural matching

Ordinary normal standard Map values provide open/subset structural recognition
through `Map.match(subject)`.

This is direct standard behavior of semantically eligible normal Map receivers;
there is no separate Map-pattern value family.

### 7.1 Receiver eligibility

The receiver must own normal standard Map keyed-entry state.

An ordinary object does not become a normal Map merely because it:

- delegates to a Map;
- inherits Map behavior;
- defines `at`, `atPut`, `containsKey`, `size`, `each`, iteration, or Map-like
  ordinary slots; or
- copies Map-like behavior.

If ordinary lookup selects the standard Map `match` behavior for a receiver that
does not own normal standard Map keyed-entry state, invocation signals an ordinary
invalid-receiver Error.

`IdentityMap` does not participate automatically because its key relation is a
different standard collection contract.

### 7.2 Subject eligibility

If the subject does not own normal standard Map keyed-entry state,
`Map.match(subject)` returns canonical `false`.

### 7.3 Stable shallow observation

For one Map structural match attempt, before executing any matcher-key search or
nested mapped-value matcher, the attempt establishes stable shallow observations
of:

- the matcher Map's current associations in matcher insertion order; and
- the subject Map's current associations in subject insertion order.

For each observed association, the semantic observation preserves the ordinary
stored representative key reference, mapped value reference, recorded key hash,
and relative insertion-order position.

The observation is shallow. Keys and values are not cloned or frozen.

Mutation of either Map's keyed-entry state after observation does not add, remove,
replace, or reorder associations in the current attempt.

### 7.4 Requirement interpretation

Each matcher-Map association is one requirement:

```text
query key -> mapped-value child matcher
```

Requirements are considered in matcher insertion order.

The query-key position is an ordinary Map key value; it is not itself scanned as a
matcher against subject keys.

Each requirement is resolved against the observed subject associations using the
existing normal standard-Map key search law:

1. compute the query key's current standard `hash` exactly once for that search;
2. consider only observed subject associations whose recorded hash equals it;
3. consider candidates in observed subject insertion order;
4. invoke exactly one ordinary:

   ```text
   queryKey == storedRepresentativeKey
   ```

   for each candidate actually compared;
5. select the first candidate whose equality result is canonical `true`;
6. continue candidate search on canonical `false`;
7. if no candidate matches, return canonical `false` for the containing Map match.

The search does not reverse equality, substitute identity, recompute stored
candidate hashes, invoke `Object.match` for key lookup, or perform
`containsKey`/`at` as a replacement search protocol.

A missing required association is mismatch, not the ordinary missing-key
`Map.at` Error.

### 7.5 Resolve before child matching

All required subject associations and their mapped-value references are resolved
and fixed before the first mapped-value child matcher executes.

Later matcher effects therefore cannot alter which subject values this attempt
will supply to later mapped-value child matchers.

### 7.6 Open/subset semantics and child execution

Unrelated subject associations are ignored.

Core v0.1 defines no exact Map mode and no Map remainder capture.

After all requirements resolve successfully, mapped-value child matchers execute
in matcher requirement order.

Each reached child is invoked exactly once as:

```text
childMatcher.match(selectedMappedValue)
```

Canonical `false` fails the containing Map match immediately.

Successful child captures compose according to section 4.

## 8. Ordinary multi-way selection with `caseOf`

Core v0.1 multi-way matching selection is the ordinary message:

```protos
value.caseOf(cases)
```

`caseOf` is an ordinary selector, not a keyword and not dedicated grammar.

### 8.1 Case carrier

`cases` must be a normal standard Map.

Its associations are interpreted as:

```text
matcher -> callable
```

The case carrier deliberately uses ordinary normal-Map semantics:

- insertion order determines case order;
- matcher keys use normal Map `hash` / `==`;
- equal/duplicate matcher keys cannot coexist as distinct cases;
- there is no matching-specific parallel key identity/equality relation.

If `cases` is not an eligible normal standard Map, `caseOf` signals ordinary
Error.

### 8.2 Case observation

At the beginning of one `caseOf` attempt, the current case associations are
observed shallowly in insertion order.

The matcher and callable references selected by that observation are fixed for the
attempt.

Later mutation of the original cases Map cannot add, remove, replace, or reorder
cases in the current attempt.

The observation is shallow: matcher/callable objects themselves are not cloned or
frozen.

### 8.3 Selection algorithm

Cases are attempted in observed insertion order.

For each reached association:

```text
result = matcher.match(value)
```

The result is consumed exactly as follows:

```text
false        -> continue to the next observed case
true         -> invoke callable()
[captures]   -> invoke callable(...captures)
other normal -> Error
```

A successful matcher commits immediately.

A selected callable result is returned unchanged as the complete `caseOf` result,
including `null`, either Boolean, Array, Future, or any other ordinary value.

If no matcher succeeds, `caseOf` signals one fresh ordinary Error.

Effects performed by earlier failed matchers are not rolled back.

Error, non-local control, cancellation, and suspension from a reached matcher or
selected callable propagate ordinarily.

### 8.4 No eager whole-table validation

`caseOf` defines no eager validation pass over all matcher or callable values.

An unreached matcher or callable does not fail merely because it would be invalid
if reached.

Matcher-result validation happens when that matcher is actually reached.

Callable validity/arity behavior is consumed when that callable is actually
selected and invoked.

## 9. Capture names and selected bodies

Matching owns positional capture values, not source binding names.

Capture names belong to ordinary callable/Closure parameters.

For example:

```protos
value.caseOf(%{
    [1, Capture]: x => x
    Any: () => null
})
```

When `[1, Capture]` succeeds with one capture `[v]`, the selected callable is
invoked under ordinary call spread/parameter-binding semantics as though with
one ordinary positional argument `v`.

Matching defines no selected-arm binding ABI, fixed capture-name metadata,
duplicate pattern-binding rule, OR binding-name equivalence rule, or
`captures(...)` binding form.

## 10. Removed dedicated matching-language institutions

D131 removes the following from Core v0.1:

- postfix `subject match { ... }` grammar;
- `case` arm grammar;
- `when` guard grammar and matching-specific guard/body delimiter rules;
- `@name` binder syntax;
- `_` wildcard syntax;
- dedicated Array-pattern grammar;
- Array remainder/rest pattern forms and residual Array aggregate semantics;
- dedicated Map-pattern grammar;
- exact Map mode;
- Map remainder forms and residual Map aggregate semantics;
- alias pattern syntax;
- OR pattern syntax/capability as an initial standard institution;
- OR binding-name/interface equivalence;
- fixed and dynamic `captures(...)`;
- D103 complete-arm dynamic-rest terminality;
- matching-specific selected-arm binding ABI;
- dedicated static coverage/exhaustiveness/redundancy framework; and
- pattern-arm structural unreachability source-error machinery.

These are not dormant Core v0.1 features.

Future explicit decisions may add ordinary matcher/combinator capabilities or
syntax when concrete use justifies them. Future sugar should preserve
`pattern.match(subject)` as the recognition authority unless that authority is
itself explicitly reopened by a later decision.

## 11. Optimization freedom

Implementations may inline standard matcher behaviors, scalarize unobservable
capture carriers, fuse composite loops, retain versioned/persistent snapshots, or
use other internal representations only when observable behavior remains exactly
equivalent to this specification.

Optimizations must preserve:

- ordinary matcher lookup/dispatch;
- matcher invocation order and exactly-once counts;
- ordinary equality/hash direction and call counts where specified;
- shallow observation boundaries;
- capture composition and no-recursive-flattening;
- first-success `caseOf` commitment;
- ordinary Error/control/cancellation/suspension behavior; and
- selected callable invocation semantics.

## 12. Authority reconciliation

D131 supersedes the Core v0.1 public institutions selected by D088, D090, D092,
D093, D095, D096, D100, and D103 where those decisions define the removed
dedicated pattern-language/binding/guard/OR/static-analysis surface.

The following foundations remain authoritative where consistent with D131:

- D071 — matcher-based multi-way matching architecture, rehomed in ordinary
  `caseOf`;
- D072 — exact matcher outcome/capture carrier;
- D073 — ordinary `pattern.match(subject)` invocation authority;
- D081 — inherited `Object.match(subject) -> this == subject`;
- D083 — ordered composite capture composition.

D084 remains authoritative only for the retained correctness principles now owned
by fixed `Array.match`: standard Array state eligibility, exact fixed-length
recognition, shallow pre-child observation, deterministic child order, ordinary
child matcher invocation, and capture composition. Its remainder/rest institutions
are superseded for Core v0.1 by D131.

D086 remains authoritative only for the retained correctness principles now owned
by open/subset `Map.match`: normal standard Map eligibility, stable association
observation, ordinary normal-Map key search, missing-key mismatch, resolution
before nested child matching, deterministic requirement order, open/subset
residue policy, and capture composition. Its exact/remainder institutions are
superseded for Core v0.1 by D131.

D075/D078 named structural-projection machinery is not required by the D131
standard Array/Map matching path. Any remaining normative use outside this
matching model is unaffected until independently reconciled.

## 13. Transition requirement

The specification cutover does not by itself preserve compatibility with the
removed matching language.

Implementation follow-up must remove parser, AST/lowering, runtime, tests,
conformance and documentation for rejected behavior rather than leaving dormant
acceptance paths.

Until those executable slices are complete, the repository may temporarily contain
implementation behavior that is ahead of or behind this normative cutover. That
transitional mismatch is owned by I041 and must be eliminated before I041 closes.
