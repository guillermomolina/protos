# Protos Callables v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Closure values and capture, methods, invocation, arguments, receiver binding, and return/control behavior owned by callable execution. Lexical forms, trailing-closure attachment, operator parsing/precedence, and mandatory syntactic desugarings are owned by `../PROTOS_GRAMMAR.md`.

The material below is migrated without intended semantic change from `../PROTOS_LANGUAGE_SPEC.md`. Legacy section titles and numbering are retained so existing references remain understandable.

## 9. Closures

Closure lexical forms are defined normatively by `../PROTOS_GRAMMAR.md`, including
parameter syntax, expression-bodied closures, single-parameter shorthand, and
the parse boundaries of `=>`.

Every syntactic Closure form recognized by the grammar creates the same Core
`Closure` value kind. Creating a Closure does not invoke it.

A Closure captures its genuine lexical execution contexts **by reference**, not
by value. It also preserves the callable control metadata required by the
following sections for `this`, `super`, receiver binding, and return homes.

```js
makeCounter: () => {
    n: 0

    () => {
        n = n + 1
        n
    }
}
```

Therefore repeated invocation of the returned Closure observes and updates the
same captured lexical slot while that captured context remains alive.

The grammar's different Closure spellings do not create different callable
semantics. In particular, braced versus expression bodies and parenthesized
versus single-simple-parameter shorthand do not introduce JavaScript-style
callable categories.
## 10. Closures and Methods

The language has one executable value kind in the core language: **Closure**. There is no separate `Method` value type.

A closure installed as an object slot acts as a method when it is reached through a message send. "Method" therefore describes an invocation role, not a distinct kind of object.

```js
animal: {
    speak: () => {
        print(name)
    }
}
```

A call:

```js
dog.speak()
```

dynamically binds `this` to `dog`.

A closure created during that execution captures this receiver lexically:

```js
animal: {
    speaker: () => {
        () => {
            print(name)
        }
    }
}

f: dog.speaker()
f()
```

`f` retains `this === dog`.
## 11. Extracted Methods

Reading a closure-valued slot does not execute it. It reads the executable value. When that value is obtained through receiver lookup, The language preserves the receiver and lookup origin as binding metadata so that a later plain call has the same receiver semantics as the original method reference.

This does not create a distinct `Method` object type; the resulting value is still a closure semantically, with receiver binding metadata.

```js
f: dog.speak
```

A subsequent:

```js
f()
```

retains `this === dog`.

The language therefore does not reproduce JavaScript's lost-`this` behavior when a method is extracted.
## 12. Closures and `super`

A closure created inside a method retains the information required to resolve `super`.

```js
dog: animal {
    action: () => {
        f: () => {
            super.action()
        }

        f()
    }
}
```

The closure preserves both the original receiver and the `methodHome` required to continue delegation correctly.
## 13. Return Semantics

A braced Closure body is evaluated as the ordinary semantic `Sequence` defined by `EXECUTION_AND_CONTROL.md`. When that body completes normally, the Closure's normal return value is the Sequence result. Therefore a braced Closure with zero body expressions, such as `() => {}`, returns canonical `null` by the Sequence rule; this is not a separate Closure-specific empty-body rule.

```js
square: (x) => {
    x * x
}
```

In an expression-bodied closure, the single body expression is the final expression and supplies that value, exactly as in the equivalent braced form: `square: (x) => x * x`.

Early return is expressed using:

```js
^value
```

The language follows the Smalltalk/Squeak **home activation** model for non-local return.

A top-level function or method invocation establishes a return home. Closures created lexically during that invocation capture that same home rather than creating a new one merely because they are called.

```js
find: (items) => {
    items.each((item) => {
        item.valid.ifTrue() {
            ^item
        }
    })

    null
}
```

`^item` returns from the active invocation of `find`, not merely from either nested closure.

A direct `^` in `find` targets the same home activation:

```js
find: (items) => {
    ^42
    null
}
```

Closures defined at module level have no enclosing function return home. When such a closure is invoked as a function, that invocation establishes its own return home. Method invocation likewise establishes a fresh return home for the invoked method.

A closure created inside an active function or method invocation captures that invocation's return home. Calling such a closure as an ordinary nested block does not replace the captured home.

`return value` may eventually exist as syntactic sugar for `^value`, but `return` is not part of the fundamental semantics.
## 14. Return from Escaped Closures

A nested closure may outlive the activation that owns its captured return home.

```js
make: () => {
    () => {
        ^42
    }
}

f: make()
f()
```

The closure returned by `make` still refers to the completed invocation of `make`. Therefore executing `^42` later signals `InvalidReturn`.

The runtime must not reinterpret that operation as a local return from `f`.
## 18. Trailing Closures

Trailing-closure syntax, same-line attachment, newline/comment behavior, and its
mandatory desugaring are owned by `../PROTOS_GRAMMAR.md`.

After that desugaring, the appended value is an ordinary parameterless
`Closure`. It has exactly the same capture, invocation, receiver, return-home,
error, and Future/task semantics as any other Closure passed explicitly in the
same argument position. This document defines those callable semantics; it does
not independently define when trailing-closure syntax parses.
## 21.1 Custom Symbolic Binary Operators

The lexical alphabet, reserved spellings, maximal-munch classification,
precedence restrictions, associativity, parse validity, and mandatory lowering
of custom symbolic binary operators are owned by `../PROTOS_GRAMMAR.md`.

When the grammar lowers a valid custom symbolic binary operator expression to an
ordinary one-argument message send, this document contributes no special
operator invocation mechanism: ordinary message lookup, receiver binding,
argument evaluation already fixed by the applicable semantic owners, and
ordinary Closure invocation apply. A symbolic selector does not create a second
callable kind or a privileged dispatch path.

## Invocation Arguments, Defaults, Rest, and Spread

This section is the primary normative owner of the argument vector and Closure
parameter-binding algorithm. `../PROTOS_GRAMMAR.md` owns only the syntactic forms
of ordinary, default, and rest parameters and of call spread. The standard Array
representation used by `args`, rest bindings, and spread extraction is owned by
`VALUES_AND_COLLECTIONS.md`; that representation does not define a second
binding algorithm.

### Caller-supplied positional vector

A call first evaluates its call receiver/target as required by the ordinary call
form, then evaluates its explicit argument items strictly from left to right.
Argument evaluation completes before Closure activation creation or parameter
binding begins.

For each ordinary argument item, its expression is evaluated exactly once and
its resulting value contributes one element to the caller-supplied positional
vector.

For each spread argument `...expression`, the spread expression is evaluated
exactly once at its left-to-right position. Standard call-spread extraction then
uses the standard Array rules in `VALUES_AND_COLLECTIONS.md`: the source must
have standard Array indexed state and contributes a shallow ascending-index
snapshot of its elements at that point. Those contributed elements are appended
to the same positional vector. Empty spread contributes zero elements.

A trailing closure that the grammar attaches and desugars is an ordinary final
argument. Its creation occurs at the position established by that desugaring,
after the preceding explicit argument items, and its Closure value contributes
one final element to the same vector.

If evaluation of the call receiver/target, an ordinary argument, a spread
expression, or spread extraction signals an Error or performs another non-local
control transfer, evaluation stops immediately. No Closure activation for that
call is created, no parameter/default binding begins, and effects already
performed by earlier evaluation are not rolled back.

Once this phase completes, let `supplied` denote the resulting positional vector
and let `N` be its length. Spread therefore composes with invocation by producing
ordinary positional elements before binding; it does not invoke a second
parameter-binding algorithm.

### Activation establishment

After the complete caller-supplied vector exists and ordinary dispatch has
selected the Closure to invoke, the Closure invocation establishes its activation
before evaluating any default expression.

The activation has a fresh execution-context object with the ordinary lexical
parent, receiver (`this`), `methodHome`, and captured lexical relationships
specified elsewhere in this document and in `EXECUTION_AND_CONTROL.md`.
The reserved intrinsic `context` denotes that fresh activation context throughout
parameter binding and body execution.

The activation's return-home relationship is also established before the first
parameter is bound. An invocation that owns a fresh return home makes that home
active for the whole dynamic extent of parameter binding and body execution. A
nested Closure invocation that uses a captured home uses that same captured home
while binding defaults, exactly as it does while executing its body.

The reserved intrinsic `args` is established from `supplied` before parameter
binding begins. It denotes a fresh frozen standard Array containing exactly the
`N` caller-supplied positional elements in order. It contains neither the
receiver nor the caller activation, and it never contains values produced by
default expressions. `args` is not an ordinary writable identifier and cannot be
shadowed by a parameter or local slot.

### Normative parameter-binding algorithm

Let `i = 0`. Process the declared parameters strictly from left to right. Parameter
names are already unique by the grammar/signature rules.

For each parameter:

1. If it is the trailing rest parameter, create its local parameter slot with a
   fresh frozen standard Array containing exactly `supplied[i]` through
   `supplied[N - 1]` in order, or an empty Array when `i == N`. Then set `i = N`.
   No default applies to a rest parameter.
2. Otherwise, if `i < N`, take `supplied[i]` as the parameter value and increment
   `i` by one. A default expression on that parameter is not evaluated at all.
3. Otherwise, if the parameter has a default expression, evaluate that expression
   exactly once in the current invocation activation. Do **not** increment `i`.
   If evaluation completes normally, its exact resulting value becomes the
   parameter value.
4. Otherwise, signal an argument-count Error immediately. No later parameter,
   default expression, rest binding, or body expression is evaluated.
5. After a non-rest parameter value has been obtained normally, create that
   parameter name as a local slot of the activation context with the exact value.

After the final parameter, if `i < N`, signal an argument-count Error. A trailing
rest parameter necessarily consumes the complete remaining suffix, so such an
extra-argument failure cannot occur when rest is present.

Binding a parameter is ordinary local-slot establishment on the activation
context. There is no parameter hoisting, predeclaration, temporal-dead-zone
object, hidden `uninitialized` Protos value, or second parameter namespace.
Consequently, a parameter name becomes a local binding only after its supplied or
default value has been obtained normally and its slot has been created.

A default expression can therefore read every earlier successfully bound
parameter through ordinary bare-name lookup. The parameter currently being
bound and every later parameter are not yet local bindings. A bare reference to
one of those names follows the ordinary unqualified lookup algorithm in
`EXECUTION_AND_CONTROL.md`: it may resolve in a captured lexical context or, when
applicable, on `this` and its delegation chain; if no such binding exists, the
ordinary lookup Error is signaled. In particular, `(a = b, b = 1)` does not read
the later parameter's future value `1` merely because `b` appears in the
signature.

Because defaults execute in the real activation, they may observe the ordinary
`this`, `context`, and `args` intrinsics and may perform any effects otherwise
permitted to an ordinary expression. If a default explicitly creates a local
slot whose name must later be established as a parameter slot, the later
parameter-slot creation follows ordinary slot-creation conflict semantics; no
special overwrite or parameter reservation occurs.

### `args`, rest, and default interaction

`args` always denotes the complete flattened caller-supplied positional vector,
including elements contributed by spread and any desugared trailing Closure.
Defaults never add elements to `args`, replace elements in it, or shift positional
assignment.

A rest parameter contains only the still-unconsumed suffix of that same caller-
supplied vector. Values produced by defaults are never inserted into rest. The
rest Array is a distinct fresh frozen standard Array from `args`, including when
both are empty or contain the same references. Both collections are shallow:
the argument objects themselves retain their ordinary identities and aliasing.

For example, for `(a = 1, ...rest)`:

```text
call()        -> args = [],       a = 1,  rest = []
call(10)      -> args = [10],     a = 10, rest = []
call(10, 20)  -> args = [10, 20], a = 10, rest = [20]
```

### Failure and control-transfer precedence

The observable precedence is the order of the algorithm above, not a separate
arity preflight:

- receiver/target evaluation and argument/spread evaluation happen before
  invocation and therefore before every arity, parameter-binding, or default
  failure;
- a missing required parameter is detected when left-to-right binding reaches
  that parameter, so defaults and effects of earlier parameters may already have
  occurred;
- an excess-argument failure is detected only after all non-rest parameters have
  consumed their supplied values; when excess supplied arguments exist, every
  non-rest parameter necessarily receives a supplied value, so no default is
  evaluated merely before discovering the excess;
- a default-expression Error or other control transfer stops binding immediately
  and takes precedence over every later missing-parameter/default/body outcome;
- a parameter-slot creation failure likewise stops binding before later
  parameters or the body.

No parameter-binding failure rolls back effects already performed by argument
expressions, earlier defaults, or other ordinary operations. Earlier parameter
slots already created in the activation are likewise not conceptually rolled
back. If `context` or a Closure capturing that context escaped through an effect
before the invocation failed, ordinary object/capture lifetime rules continue to
apply to the reachable partial activation context.

Error signaling during a default uses the ordinary dynamic handler environment
of the invocation. Binding installs no implicit handler frame. Core Error
signaling remains non-resumable, so an Error that exits the default abandons the
remaining binding/body path unless it was handled inside an ordinary nested
handler boundary that itself returns a normal value to the default expression.

A `^value` executed while evaluating a default uses exactly the same return home
that the Closure body would use. If it targets an active home, ordinary non-local
return unwinding begins immediately: the current parameter is not bound from that
default, later parameters/defaults and the body do not execute, and applicable
cleanup/structured-ownership rules run as for the same transfer from the body.
If the captured home is no longer active, ordinary `InvalidReturn` semantics
apply.

Parameter binding itself creates no hidden suspension point. A default expression
may explicitly suspend only through an operation that is already a suspension
point under the concurrency/Future rules. Across such a suspension, the
activation and every earlier established parameter binding remain live and
ordinary Actor-local scheduling rules apply.

### Representation and optimization boundary

The fresh standard Arrays required for `args` and rest, and the spread snapshot
semantics, are specified in `VALUES_AND_COLLECTIONS.md`. Implementations may
scalar-replace, virtualize, share immutable backing storage, or otherwise avoid
physical activation/Array copies only when `===`, reflection, mutation failure,
capture, escape, evaluation order, and all other observable semantics remain as
specified.

These rules add no Actor transferability exception. The activation context and
captured execution control remain subject to the existing Closure/context Actor
and isolated-parallel boundaries; argument and rest values cross such boundaries
only through the already-applicable value-transfer rules. Defaults do not grant
ambient Process/I/O authority, do not create an additional task kind, and do not
change Future ownership merely because they run during binding.

These facilities make invocation forwarding and dynamic arity ordinary language
operations:

```js
forward: (...items) => {
    target(...items)
}
```

No overload resolution by argument type is introduced.

## Polymorphic Invocation and Object Construction

This section is the primary normative owner of the ordinary polymorphic
invocation protocol. Object creation, delegation-parent, slot construction, and
object-state semantics remain owned by `OBJECT_MODEL.md`; member lookup remains
owned by `EXECUTION_AND_CONTROL.md`; syntax and mandatory parse/lowering rules
remain owned by `../PROTOS_GRAMMAR.md`.

### `call` is the ordinary invocation protocol

Parenthesized invocation is defined entirely in terms of the ordinary slot named
`call`. There is no hidden callable bit, internal invocation property, separate
callable hierarchy, wrapper object, or runtime registry.

For a completed caller-supplied positional vector `supplied`, invoking target
`receiver` performs these steps:

1. Perform ordinary member lookup for the name `call`, starting at `receiver`
   itself and continuing through its ordinary immutable delegation chain exactly
   as for the member read `receiver.call`.
2. Preserve `receiver` as the original receiver and preserve the object that owns
   the selected `call` slot as `methodHome`, exactly as for an ordinary message
   lookup selecting a Closure-valued slot.
3. The selected slot value must be a `Closure`. If lookup finds no `call`, the
   ordinary missing-slot Error from that lookup is signaled. If lookup succeeds
   but the selected value is not a `Closure`, invocation signals an `Error`
   before any activation or parameter/default binding for that selected value.
4. Directly activate that selected Closure in method role with `this === receiver`,
   the preserved `methodHome`, and `supplied` as its caller-supplied argument
   vector. Activation, arity/default/rest binding, `args`, returns, Errors, and
   explicit suspension then follow the ordinary Closure rules in this document.

Steps 1–4 occur only after target evaluation and all ordinary/spread/trailing
argument evaluation described above have completed. Thus an Error or non-local
control transfer during target or argument evaluation wins over `call` lookup;
a lookup failure wins over any activation/binding/body behavior; and effects of
already evaluated arguments are not rolled back.

The direct Closure activation in step 4 is the terminal executable operation of
the Core callable model. It is **not** another source-level parenthesized
invocation of the selected `call` value. Consequently `receiver(args...)` is not
specified as the source rewrite `receiver.call(args...)`, and no infinite
regress through `call.call.call...` exists. The lookup and receiver semantics are
those of ordinary message lookup; activation of the already selected Closure is
the irreducible execution operation already required by the Core rule that
Closure is the single executable value kind.

A slot named `call` is otherwise completely ordinary. It may be created,
assigned, shadowed, inherited, copied, composed, or aliased under the same rules
as any other slot. No assignment to `call` executes its value.

For example:

```js
obj: {
    call: (x) => x
}

obj(42)       // 42
f: obj.call   // reads; does not execute
f(42)         // 42, with the extracted binding metadata for obj.call
```

The read `obj.call` follows the existing extracted-method rule: because the
selected slot value is a Closure, the resulting Closure preserves `obj` as its
receiver and the lookup origin as `methodHome`. Merely storing a Closure in any
slot, including `call`, never invokes it.

If an object inherits `call`, lookup selects the inherited slot but binds `this`
to the original invocation receiver, not to the ancestor that owns the slot. A
nearer local `call` shadows an inherited one. A local non-Closure `call` therefore
makes parenthesized invocation fail at step 3 even when an ancestor has a valid
`call`; lookup does not skip an incompatible shadowing value.

Composition and copying use the ordinary shallow slot rules of
`OBJECT_MODEL.md`. Copying a local `call` slot copies the same stored Closure
reference; it does not preserve a receiver binding from the source object.
Receiver/methodHome binding is established when the copied slot is later reached
through lookup. By contrast, explicitly extracting `obj.call` first produces the
ordinary receiver-bound Closure described in §11, and storing or aliasing that
already extracted Closure preserves its existing binding metadata exactly as for
any other extracted method.

### Callability inspection

Whenever this specification says an API validates that an object is
`invokable`, `callable`, or "ordinarily invokable" without invoking it, the test
is exactly this structural protocol check:

```text
ordinary lookup of candidate.call succeeds
and the selected slot value is a Closure
```

The check performs ordinary read-only lookup only. It does not activate the
selected Closure, evaluate defaults, perform arity preflight, invoke user code,
introduce a suspension point, or pin/cache the selected Closure for a later
invocation. Unless the owning API explicitly says otherwise, a later invocation
performs a fresh ordinary `call` lookup, so ordinary intervening mutation or
shadowing remains observable.

If validation itself encounters the ordinary lookup failure or an incompatible
selected value, the owning API signals the Error it specifies for a non-invokable
argument. Receiver/argument evaluation required to obtain that candidate has
already occurred according to that API's own ordering rules.

This definition applies uniformly to collection callbacks such as `Array.each`
and `Bytes.each`, Boolean callbacks, Actor bootstrap targets, and the isolated
parallel (`P`) worker/comparator boundaries. Those APIs do not recognize Closure
identity as a separate shortcut and do not execute the callback merely to test
callability.

### Standard `Object.call`: Closure execution and default construction

The standard `Object` object owns an ordinary local `call` slot whose value is a
standard Closure. This one inherited behavior supplies both standard Closure
execution and default object construction without introducing a separate
`Closure` prototype, hidden callable property, or second dispatch mechanism.
Conceptually, after `Object.call` has been selected and directly activated with
invocation receiver `this`:

```text
Object.call(...args):
    if this is a Closure value:
        directly activate this Closure value with args
        using its captured/bound receiver, methodHome, lexical context,
        and return-home semantics
        return that activation's result

    instance = createObject(parent = this)
    send(instance, "init", args)
    return instance
```

The Closure branch is the terminal executable operation already defined above;
it does not perform another `this(args...)` lookup. A Closure that inherits
`Object.call` therefore executes its own body. A receiver-bound Closure produced
by §11 retains its preserved receiver and `methodHome` when that body executes.
A Closure may nevertheless define or acquire a nearer ordinary `call` slot; in
that case ordinary lookup selects the nearer behavior instead, exactly as for any
other object.

For non-Closure receivers, inherited `Object.call` performs default construction.
Because `call` uses ordinary delegation, every object that does not define a
nearer `call` inherits this behavior through the unique delegation chain ending
at `Object`. There is no privileged "prototype" flag controlling this
inheritance: any object may be a delegation parent, and an ordinary non-Closure
object with no nearer `call` is therefore constructible by inherited
`Object.call`. An object can deliberately make itself non-invokable by shadowing
`call` with a non-Closure value, subject to its ordinary mutability rules.

Thus:

```js
Point(10, 20)
```

creates a fresh object whose immutable delegation parent is `Point`, sends
`init(10, 20)` to that fresh object, and returns that fresh object. `init` is an
ordinary overridable message. Its return value is ignored; an Error or other
control transfer from initialization propagates normally and the construction
expression has no successful instance result.

`Object` provides a default `init` behavior that accepts zero arguments and
signals an argument-count Error when arguments are supplied. Therefore `Thing()`
works for an object that does not override `init`, while `Thing(1, 2)` requires
compatible initialization behavior.

Alternative constructors such as `Point.fromPolar(radius, angle)` are ordinary
named messages and may internally use the same parenthesized invocation
protocol.

### Standard factory specialization

Standard objects described elsewhere as specializing ordinary polymorphic
invocation do so by the same mechanism: they own a nearer ordinary local `call`
slot whose value is their standard factory/conversion Closure, thereby shadowing
`Object.call`. This includes `Array`, `Map`, `IdentityMap`, and the standard
numeric conversion prototypes. Delegation inherits those factory behaviors
normally, with the actual invocation receiver remaining `this`; a nearer user or
standard `call` shadows them normally. Standard receiver-domain and result rules
for each factory remain owned by the corresponding domain specification.

Arity, defaults, rest, spread, Errors, non-local return, and explicit suspension
for user-defined or standard `call` Closures are not a second invocation system:
they are exactly the Closure rules above. Lookup and callability checks create no
hidden suspension; suspension is observable only if execution of the selected
Closure explicitly reaches an operation that is already a suspension point.

### Object literals and trailing closures remain distinct

Object-literal/prototype syntax remains distinct:

```js
Point {
    x: 10
    y: 20
}
```

directly creates an object whose parent is `Point` and evaluates the object body
as slot definitions. It does not implicitly send `init` or `call`.

Core v0.1 does not define a combined object-construction form in which
`Point(args) { ... }` means "construct and then evaluate this object body". When
that token sequence is valid under trailing-closure syntax, the braces denote a
parameterless trailing Closure appended to the invocation arguments: the form
desugars as `Point(args, () => { ... })`.