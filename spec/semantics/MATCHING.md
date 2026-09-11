# Protos Matching Protocol Semantics v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-11

This document is the primary normative owner of the ratified matching-protocol
semantics introduced by D071 through D074 at specification revision `0.1.394`.
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
values those names denote. A request for a subset of logical names must not, by
language rule, require enumeration or materialization of a complete structural
view.

The logical names are an explicit matching API of the subject. They are not
aliases for local-slot names, delegated member names, indexed keys, physical
field names, or host-layout offsets unless the subject's own eventual projection
behavior deliberately chooses the same names.

Physical/internal field reordering is not observable through this contract.
Adding an implementation cache, helper slot, method slot, delegated behavior, or
other non-projected state does not automatically alter the subject's logical
matching structure.

Core v0.1 does not impose a universal positional product layout on ordinary
objects. Domain-specific positional extraction remains possible through ordinary
pattern-owned `match(subject)` behavior and its D072 capture carrier. Array, Map,
Bytes, and other indexed contents remain governed by their existing collection
and indexing semantics rather than being reclassified as object fields.

The exact selector name for named projection, the request carrier, result
carrier/order, distinction between no structural view and a missing requested
logical field, complete-view/remainder capability, and any positional
subject-deconstruction protocol remain unresolved and are not inferred by this
section.

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
- the exact named-projection selector;
- the request/result/failure carrier for structural projection;
- complete-view or `**rest`-like semantics;
- a universal positional subject-deconstruction protocol; or
- a recognition-only matcher fast path.

Until those questions are separately ratified, implementations and libraries
must not treat them as implied by D071-D074.
