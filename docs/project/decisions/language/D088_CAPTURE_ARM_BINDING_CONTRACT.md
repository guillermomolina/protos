# D088 — Capture binding and arm-binding contract

Status: **RATIFIED**
Specification revision: **`0.1.402`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record; rationale for normative capture-to-arm binding semantics
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#374`

## Decision

Core v0.1 keeps D072/D083 as the sole standard runtime capture representation
and binds successful captures to a selected arm through **ordinary Protos
Closure/callable invocation**.

The binding ABI is positional:

```text
true          -> zero capture actual arguments
[c1,...,cn]   -> c1...cn as ordinary positional actual arguments
```

Source-visible binder names belong to the source/arm interface rather than to
arbitrary matcher metadata. A fixed source binding interface maps its binders
deterministically to capture positions; ordinary Closure parameter binding then
creates the corresponding ordinary parameter slots in the selected arm
invocation activation.

Bindings commit only after the whole candidate pattern has succeeded and its
D072 result has been validated. Failed attempts therefore create no
source-visible arm-binding state and require no rollback. Matcher effects remain
ordinary and are not rolled back.

Captured aggregate values remain opaque: one D072 capture is one arm argument,
including D084 Array remainders and D086 Map remainders.

Fixed binding interfaces are linear; duplicate binder names are invalid rather
than hidden equality/conjunction or shadowing semantics.

Arbitrary matchers may keep dynamic capture arity. D088 introduces no required
`captureArity`, capture-name Map, `CaptureSignature`, `CaptureFrame`, or matcher
topology introspection. An arm intentionally accepting variable capture counts
may use existing ordinary Closure rest-parameter semantics.

After recognition succeeds, an incompatible selected-arm arity is an ordinary
callable binding/arity Error, not mismatch and not permission to continue to a
later arm.

A future OR/alternative pattern presented to one fixed source arm-binding
interface must map every successful alternative onto the same ordered logical
binding interface. D088 does not choose the OR recognition/backtracking
mechanics themselves.

Whole-subject alias, guards, exhaustivity, repetition/optional semantics and
concrete pattern/binder grammar remain deferred.

## Comparative basis

The audit compared Rust, Python, OCaml, Haskell, F#, Dart, Swift, Scala 3,
Ruby, Erlang, Elixir, Racket, Java, C#, Clojure/core.match, Peggy/PEG labels,
Tree-sitter captures, Self and Io.

The strongest cross-system evidence is that alternatives sharing an arm require
a stable binding interface. Rust, Python, OCaml, Dart, Swift and Racket require
compatible/same bindings across alternatives; Scala and Ruby currently restrict
ordinary alternative bindings, demonstrating that the issue cannot safely be
left implicit.

Self and Io are especially relevant to Protos philosophy: ordinary callable
activation already turns positional actual arguments into named activation/local
slots. Protos itself already has the stronger exact mechanism through Closure
invocation activations and required/rest parameter binding.

The selected option scored 5.0/5.0 on future-option resilience, 5.0/5.0 on
scalability and 5.0/5.0 on Protos alignment, with 49.6/50 across the complete
ten-dimension comparison.

## Why names are not matcher metadata

D072 deliberately selected a small public result carrier based only on ordinary
values. Requiring every user matcher to publish names/signatures would make a
source binding convenience a mandatory runtime institution.

The source/compiler already knows the relationship between binder sites and
capture positions for standard source patterns. Keeping that mapping at the
consumer/source layer permits rich future binder syntax without adding name
hashing, schema objects, or reflection to zero-capture and arbitrary matchers.

## Why commitment waits for full success

Creating tentative source-visible bindings during matching would require
rollback across later mismatch, Error, non-local control, cancellation and
suspension. It would also make arbitrary matcher effects able to observe a
partially populated binding environment.

Post-success arm invocation gives one precise boundary: recognition first,
ordinary binding/call execution second. Matcher side effects remain visible
according to their existing rules, but arm bindings simply do not exist before
selection.

## Dynamic arity

Dynamic capture arity remains legal under D072. Requiring fixed capture
signatures now would tax arbitrary matchers and reduce future option space.

Ordinary Closure rest parameters already provide a Protos-native variadic
consumer. Future explicit pattern aggregators can alternatively publish several
dynamic values as one ordinary aggregate capture.

## Strongest counterargument

Source binders are most ergonomic when written at the structural pattern site,
while the selected ABI describes them as arm-call parameters.

The ABI does not constrain that surface. A future compiler can preserve binder
names at structural source sites while mapping them statically to capture
positions and arm parameter slots. D088 selects the semantic transport and
commit boundary, not punctuation.

## Regret and escape path

If future first-class Pattern reflection, serialization, or tooling requires
named capture descriptors independent of any arm, an optional explicit
descriptor/introspection protocol or debug metadata can be added later.

That change is additive because positional D072 captures remain authoritative.
Making runtime names mandatory now and later trying to remove them would be
substantially more disruptive.

## Intentionally deferred

D088 does not select concrete match/arm/binder syntax, OR recognition mechanics,
whole-subject alias semantics, guards, exhaustivity, repetition/optional
matching, sequence search/stream patterns, named-argument semantics,
first-class Pattern reflection, or parser/runtime implementation of the future
matching surface.
