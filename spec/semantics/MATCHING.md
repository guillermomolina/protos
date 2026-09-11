# Protos Matching Protocol Semantics v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-11

This document is the primary normative owner of the ratified matching-protocol
semantics introduced by D071-D075 and D078. D075 ratifies the exact named
structural-projection request/result/failure contract, while D078 ratifies
open/subset named-object matching and declines a generic complete-view/remainder
protocol in Core v0.1. The latest matching specification revision is
`0.1.396`.
It deliberately does **not** define concrete matching-expression grammar,
case/arm/default syntax, literal-pattern semantics, guards, exhaustivity,
standard pattern taxonomy, nested-capture flattening, or named-binding syntax.
Those remain unresolved until separately ratified.

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

A universal positional subject-deconstruction protocol remains unresolved.

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

Collection-specific Map/sequence remainder behavior is a separate design
question because indexed/keyed collections already have their own explicit
structural protocols. D078 does not select those semantics.

A future complete logical-view facility may be considered only through a
separate explicit decision backed by concrete use evidence. Such a facility must
not silently redefine the D075 selective projection contract or make existing
open/subset patterns observe newly added logical fields.

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
- literal/equality pattern semantics;
- guards or exhaustivity;
- a standard built-in pattern taxonomy;
- nested capture flattening;
- named capture/binding syntax;
- collection-specific Map/sequence remainder semantics;
- whole-subject alias/binding syntax and semantics;
- a universal positional subject-deconstruction protocol; or
- a recognition-only matcher fast path.

Until those questions are separately ratified, implementations and libraries
must not treat them as implied by D071-D075 or D078.
