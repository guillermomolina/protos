# Core Runtime Semantics v0.1

Language version: 0.1  
Document revision: 30  
Status: Draft  
Last updated: 2026-08-31

This document defines executable-style pseudocode for the core runtime operations of the language.

It complements:

- `LANGUAGE_SPEC.md`
- `GRAMMAR.md`

The goal is not to mandate an implementation strategy, but to define observable behavior precisely enough that different interpreters or VMs can behave consistently.

---

## 1. Runtime Concepts

The pseudocode assumes the following conceptual runtime entities.

```text
Object
    localSlots
    parent
    state          // open, closed, frozen

Activation
    context
    lexicalParent
    receiver       // this
    arguments      // caller-supplied positional arguments
    methodHome
    returnHome     // target of ^, optional outside callable execution
    ownsReturnHome // true only for the invocation that established returnHome
    parentTask     // optional structured-concurrency owner

Closure
    code
    parameters
    lexicalContext
    capturedThis
    capturedMethodHome
    capturedReturnHome // optional home captured from enclosing callable activation
    boundReceiver      // optional
    boundMethodHome    // optional

LookupResult
    value
    home

SlotReference
    owner
    name

Future
    state          // pending, resolved, failed, cancelled
    value
    error
```

These fields are conceptual. An implementation may represent them differently.

`Object` is the unique root prototype. It has no delegation parent. Every other the language object has exactly one immutable delegation parent, so every delegation chain terminates at `Object`. The absence of a parent on `Object` is structural; it is not represented by `null` or by any other the language object. Reflective structural operations such as `removeSlot(name)`, `close()`, and `freeze()` are ordinary messages provided through `Object`, with runtime primitives implementing their structural effects.

---

# 2. Core Invariants

The algorithms below preserve these rules:

```text
Reads may delegate.

Writes never delegate.

: creates a local slot.

= modifies an existing writable slot.

A missing slot is an error.

null is not the result of failed lookup.

this is the original receiver of a message send.

super changes lookup origin, not receiver.

Closures capture lexical contexts by reference.

Object construction contexts are not captured as lexical scopes merely because they own method slots.

Extracted methods remain bound to their receiver.

^ returns to the owning activation.

Object has no delegation parent.

Every other object has exactly one delegation parent.

Every delegation chain terminates at Object.

A delegation parent cannot change after object creation.
```

---

# 3. Local Slot Lookup

```text
function lookupLocal(object, name):
    if object.localSlots contains name:
        return LookupResult(
            value = object.localSlots[name],
            home = object
        )

    return NOT_FOUND
```

This operation never checks the object's parent.

---

# 4. Delegating Object Lookup

```text
function lookupSlot(receiver, name, start = receiver):
    current = start

    loop:
        result = lookupLocal(current, name)

        if result != NOT_FOUND:
            return result

        if current is Object:
            signal SlotNotFound(
                receiver = receiver,
                name = name
            )

        current = delegationParent(current)
```

The returned `home` is the object where the slot was physically found.

Example:

```text
rex → dog → animal
```

If `speak` belongs to `animal`:

```text
lookupSlot(rex, "speak")
```

returns conceptually:

```text
value = animal.localSlots["speak"]
home  = animal
```

The receiver is still `rex`.

---

# 5. Unqualified Lookup

Unqualified lookup searches lexical contexts first, then the receiver delegation chain.

```text
function lookupName(activation, name):
    context = activation.context

    while context != null:
        result = lookupLocal(context, name)

        if result != NOT_FOUND:
            return result.value

        context = lexicalParentOf(context)

    if activation.receiver != null:
        result = lookupSlot(
            receiver = activation.receiver,
            name = name
        )

        return bindIfMethod(
            value = result.value,
            receiver = activation.receiver,
            methodHome = result.home
        )

    signal SlotNotFound(
        name = name
    )
```

A failed lookup never returns `null`.

---

# 6. Explicit Member Read

```text
function readMember(receiver, name):
    result = lookupSlot(
        receiver = receiver,
        name = name
    )

    return bindIfMethod(
        value = result.value,
        receiver = receiver,
        methodHome = result.home
    )
```

Therefore:

```js
f: dog.speak
```

produces a bound callable value if `speak` is a method closure.

---

# 7. Method Binding

There is no distinct runtime-language `Method` value category. The core executable value is `Closure`. A member read never executes a closure. If member lookup yields a closure, the runtime may represent the receiver binding as a lightweight `BoundClosure` wrapper/view, but this is binding metadata rather than a separate method object in the language semantics.

```text
function bindIfMethod(value, receiver, methodHome):
    if not isClosure(value):
        return value

    return BoundClosure(
        closure = value,
        boundReceiver = receiver,
        boundMethodHome = methodHome
    )
```

Binding a method does not copy its lexical environment.

It only records the receiver and the home from which the method was obtained.

---

# 8. Slot Creation

Unqualified creation:

```js
x: value
```

means:

```text
createSlot(
    target = activation.context,
    name = "x",
    value = value
)
```

Explicit creation:

```js
object.x: value
```

means:

```text
createSlot(
    target = object,
    name = "x",
    value = value
)
```

The operation is:

```text
function createSlot(target, name, value):
    if target.state == frozen:
        signal FrozenObject(target)

    if target.state == closed:
        signal ClosedObject(target)

    if target.localSlots contains name:
        signal SlotAlreadyExists(
            target = target,
            name = name
        )

    target.localSlots[name] = value

    return value
```

Creation never searches the delegation chain.

---

# 9. Explicit Member Assignment

For:

```js
object.x = value
```

the runtime performs:

```text
function assignMember(target, name, value):
    if target.state == frozen:
        signal FrozenObject(target)

    if not target.localSlots contains name:
        signal SlotNotFound(
            receiver = target,
            name = name
        )

    target.localSlots[name] = value

    return value
```

No parent is ever searched.

Thus:

```text
reads delegate
writes do not delegate
```

is preserved.

---

# 10. Unqualified Assignment

For:

```js
x = value
```

the runtime searches writable lexical contexts first.

If none contains `x`, it may modify a slot belonging locally to `this`.

It never modifies a slot inherited by `this`.

```text
function assignName(activation, name, value):
    context = activation.context

    while context != null:
        if context.localSlots contains name:
            return assignMember(
                target = context,
                name = name,
                value = value
            )

        context = lexicalParentOf(context)

    receiver = activation.receiver

    if receiver != null and receiver.localSlots contains name:
        return assignMember(
            target = receiver,
            name = name,
            value = value
        )

    signal SlotNotFound(
        name = name
    )
```

---

# 11. Message Send

A receiver-aware message send preserves both the original receiver and the object where the slot was found.

For:

```js
dog.speak(a, b)
```

the runtime conceptually performs:

```text
send(
    receiver = dog,
    message = "speak",
    arguments = [a, b],
    lookupStart = dog
)
```

Algorithm:

```text
function send(receiver, message, arguments, lookupStart = receiver):
    result = lookupSlot(
        receiver = receiver,
        name = message,
        start = lookupStart
    )

    value = result.value

    if isClosure(value):
        return invokeClosureAsMethod(
            closure = value,
            receiver = receiver,
            methodHome = result.home,
            arguments = arguments
        )

    return invoke(
        receiver = value,
        arguments = arguments
    )
```

Closure-valued slots receive method semantics because `this` and `methodHome` must describe the receiver-aware send. Other invokable values use their own invocation protocol.

---

# 12. Closure Method Invocation

```text
function invokeClosureAsMethod(
    closure,
    receiver,
    methodHome,
    arguments
):
    activation = createActivation(
        closure = closure,
        arguments = arguments,
        receiver = receiver,
        methodHome = methodHome,
        establishReturnHome = true
    )

    return executeActivation(activation)
```

For:

```text
rex → dog → animal
```

if `rex.speak()` finds closure `speak` in `animal`:

```text
receiver   = rex
methodHome = animal
```

and inside the method:

```text
this === rex
```

---

# 13. Polymorphic Invocation

A plain call such as:

```js
f(a, b)
```

does not perform member lookup on the source-level identifier. It evaluates the call receiver and invokes that value.

```text
function invoke(receiver, arguments):
    if isBoundClosure(receiver):
        return invokeClosureAsMethod(
            closure = receiver.closure,
            receiver = receiver.boundReceiver,
            methodHome = receiver.boundMethodHome,
            arguments = arguments
        )

    if isClosure(receiver):
        activation = createActivation(
            closure = receiver,
            arguments = arguments,
            receiver = receiver.capturedThis,
            methodHome = receiver.capturedMethodHome,
            establishReturnHome =
                (receiver.capturedReturnHome == null)
        )

        return executeActivation(activation)

    callBehavior = lookupInvocationBehavior(receiver)

    if callBehavior == NOT_FOUND:
        signal NotCallable(receiver)

    return executeInvocationBehavior(
        callBehavior = callBehavior,
        invocationReceiver = receiver,
        arguments = arguments
    )
```

`lookupInvocationBehavior` is the runtime view of the language's ordinary invocation/call protocol. Standard `Closure` behavior executes code; ordinary prototypes inherit default construction behavior from `Object`; user objects may specialize the protocol.

The implementation may specialize these cases directly rather than literally allocating or sending an intermediate `call` message, provided observable semantics are unchanged.

# 14. Closure Creation

When evaluating:

```js
(x) => {
    body
}
```

the runtime creates:

```text
function createClosure(activation, parameters, code):
    return Closure(
        code = code,
        parameters = parameters,

        lexicalContext =
            lexicalContextForClosureCreation(activation),

        capturedThis =
            activation.receiver,

        capturedMethodHome =
            activation.methodHome,

        capturedReturnHome =
            activation.returnHome
    )
```

The lexical context is captured by reference.

The closure does not copy local slot values.


Object construction requires one important distinction: the object being constructed is a slot-creation context, but it is not automatically a lexical capture scope for method closures declared in its body.

Conceptually:

```text
function lexicalContextForClosureCreation(activation):
    if activation is evaluating an object body
       and activation.context is the object being constructed:
        return activation.lexicalParent

    return activation.context
```

Therefore a method closure installed on a prototype captures genuine enclosing lexical contexts, while bare object-state names are resolved later against the dynamic receiver (`this`) and its delegation chain.

For example, if `speak` is declared on `animal` and invoked as `dog.speak()`, a bare `name` inside `speak` resolves through `dog` before `animal`, unless a genuine lexical binding named `name` shadows it.

---

## Parameter Signature Validation

Executable closure metadata must contain unique parameter names.

Duplicate names are a source/signature validation error and must not be resolved dynamically by overwriting or rebinding activation slots. This includes collisions involving a rest parameter.

The runtime may assume validated closure parameter metadata. Defensive implementations may still reject malformed internal metadata, but such rejection is not the normal language-level execution path.

---

# 15. Activation Creation and Argument Binding

The language uses a Smalltalk/Squeak-style **home activation** for `^`.

Every invocation records the caller-supplied positional arguments before default substitution. The reserved intrinsic `args` exposes an immutable view of that original vector.

```text
function createActivation(
    closure,
    arguments,
    receiver,
    methodHome,
    establishReturnHome
):
    context = new Object(
        parent = standardContextPrototype
    )

    activation = Activation(
        context = context,
        lexicalParent = closure.lexicalContext,
        receiver = receiver,
        arguments = immutableArgumentCollection(arguments),
        methodHome = methodHome
    )

    bindParametersLeftToRight(
        activation = activation,
        parameters = closure.parameters,
        arguments = arguments
    )

    if establishReturnHome:
        activation.returnHome = new ReturnTarget()
        activation.ownsReturnHome = true
    else:
        activation.returnHome = closure.capturedReturnHome
        activation.ownsReturnHome = false

    return activation
```

Parameter binding is conceptually:

```text
function bindParametersLeftToRight(
    activation,
    parameters,
    arguments
):
    argumentIndex = 0

    for parameter in parameters from left to right:
        if parameter is rest:
            restValues = arguments[argumentIndex .. end]
            createSlot(
                activation.context,
                parameter.name,
                immutableArgumentCollection(restValues)
            )
            argumentIndex = length(arguments)
            continue

        if argumentIndex < length(arguments):
            value = arguments[argumentIndex]
            argumentIndex += 1
        else if parameter has defaultExpression:
            value = evaluate(
                parameter.defaultExpression,
                activation
            )
        else:
            signal ArgumentCountError()

        createSlot(
            activation.context,
            parameter.name,
            value
        )

    if argumentIndex < length(arguments):
        signal ArgumentCountError()
```

Default expressions therefore observe earlier parameter bindings and the invocation context. They do not alter `args`.

A method invocation dynamically supplies `receiver` and `methodHome` and establishes a fresh return home. A module-level function closure with no captured return home likewise establishes one. A nested closure preserves its captured home.

# 16. `super`

`super` preserves the original receiver but changes where lookup begins. It is not a runtime object or first-class value. The parser lowers only `super.message(arguments...)` to a super-send operation. Bare `super`, passing it as a value, assigning it, or extracting `super.message` without a call is invalid.

Conceptually, a super send uses execution metadata available through `context`: the receiver is the current receiver and the lookup origin is the parent of the current `methodHome`.

```text
function sendSuper(activation, message, arguments):
    if activation.methodHome == null:
        signal InvalidSuper()

    if activation.methodHome is Object:
        signal SlotNotFound(
            receiver = activation.receiver,
            name = message
        )

    lookupStart = delegationParent(activation.methodHome)

    return send(
        receiver = activation.receiver,
        message = message,
        arguments = arguments,
        lookupStart = lookupStart
    )
```

Thus:

```text
this
```

does not change.

Only the lookup origin changes.

---

# 17. Normal Return

The value of the final expression of an activation is its normal result.

Only the activation that **owns** a return home catches a matching non-local return and completes that home. Nested block activations sharing the same home must rethrow the control transfer so that it reaches the owner.

```text
function executeActivation(activation):
    if activation.ownsReturnHome:
        mark activation.returnHome as ACTIVE

    try:
        result = evaluateSequence(
            activation.code,
            activation
        )

        return result

    catch NonLocalReturn jump:
        if activation.ownsReturnHome
           and jump.target == activation.returnHome:
            return jump.value

        rethrow jump

    finally:
        if activation.ownsReturnHome:
            mark activation.returnHome as COMPLETED
```

---

# 18. Non-local Return

For:

```js
^value
```

the runtime performs:

```text
function nonLocalReturn(activation, value):
    target = activation.returnHome

    if target == null or target.state != ACTIVE:
        signal InvalidReturn()

    raise NonLocalReturn(
        target = target,
        value = value
    )
```

A direct `^` in a function or method and a `^` in any ordinary nested closure created during that invocation therefore target the same home activation.

The control transfer may be implemented using exceptions, continuations, stack unwinding, tagged jumps, or another mechanism.

Only the observable behavior is specified.

---

# 19. Escaped Closure Return

Example:

```js
make: () => {
    () => {
        ^42
    }
}

f: make()
f()
```

The inner closure captures the return home established by the invocation of `make`.

After `make()` returns, that home is `COMPLETED`. When `f()` later performs `^42`, `InvalidReturn` is signaled.

The runtime must not silently reinterpret the operation as a local return from `f`.

---

# 20. Object Construction

```text
function createObject(parent, body, activation):
    // Source-level object creation always supplies exactly one parent.
    // A bare object literal supplies Object. Only Object itself has no parent.
    require parent is a the language object

    object = new Object(
        parent = parent,
        state = open
    )

    constructionActivation = Activation(
        context = object,
        lexicalParent = activation.context,
        receiver = object,
        methodHome = null,
        returnHome = activation.returnHome,
        ownsReturnHome = false
    )

    evaluateSequence(
        body,
        constructionActivation
    )

    return object
```

This pseudocode expresses the language's uniform context model.

An implementation may use a specialized construction context provided that observable lookup and creation semantics are identical.

---

# 21. Object Composition

For:

```js
target: parent {
    ...sourceA
    ...sourceB

    move: () => {
        ...
    }
}
```

composition is structural flattening of local slot bindings. A composition source is an ordinary object; there is no runtime `Trait` value kind.

The runtime must distinguish three things while constructing an object:

```text
explicitLocalDeclarations
composedContributionsByName
the final local slot table
```

Each composition expression evaluates its source normally and records that source's local slot bindings:

```text
function collectComposition(source, construction):
    requireObject(source)

    for each local slot in source:
        construction.composedContributions[slot.name].append(slot.value)
```

The slot **binding** is copied; `slot.value` itself is not cloned. If two receivers compose a slot whose value is the same mutable object, both resulting local slots initially refer to that same object.

Explicit local declarations in the receiving object are recorded independently of textual order. After the complete body has been evaluated sufficiently to determine its composition contributions and explicit local declarations, the final slots are resolved per name:

```text
function resolveComposedSlot(construction, name):
    if construction.hasExplicitLocalDeclaration(name):
        return construction.explicitLocalValue(name)

    contributions = construction.composedContributions[name]

    if contributions.count == 1:
        return contributions[0]

    if contributions.count > 1:
        signal CompositionConflict(
            target = construction.target,
            name = name
        )
```

Thus an explicit local declaration has priority over any composed contribution and resolves a collision between multiple sources. In the absence of an explicit local declaration, two or more contributions with the same name are an error. Source order never selects a winner.

The rule is uniform for closure-valued slots, immutable values, mutable objects, and all other slot contents. Composition never changes `target.parent` and adds no alternate lookup path. Once construction succeeds, composed slots are ordinary local slots of `target`.

### Composition-source transformations

`without` and `alias` are ordinary object messages. They do not mutate their receiver and produce ordinary objects that may subsequently be used as composition sources.

Conceptually:

```text
function without(receiver, name):
    requireLocalSlot(receiver, name)

    result = newOrdinaryObject()
    for each local slot in receiver:
        if slot.name != name:
            createLocalSlot(result, slot.name, slot.value)

    return result
```

and:

```text
function alias(receiver, sourceName, aliasName):
    requireLocalSlot(receiver, sourceName)

    if receiver.hasLocalSlot(aliasName):
        signal AliasConflict(aliasName)

    result = copyLocalSlotBindingsIntoNewObject(receiver)
    createLocalSlot(result, aliasName, receiver.localSlot(sourceName).value)
    return result
```

`alias` preserves the original `sourceName`; it adds `aliasName`. Both names initially refer to the same stored object. Neither operation clones slot values.

Because the returned value is an ordinary object, the composition machinery itself remains unchanged: `...` simply evaluates its operand and composes that object's local slots. Missing source names and alias-name collisions are errors rather than silent no-ops or overwrites.

---

# 22. Removing Local Slots

`removeSlot(name)` is an ordinary message inherited from `Object`. Its primitive behavior affects only the receiver's local slot table and never delegates.

```text
function removeLocalSlot(object, name):
    if object.state == frozen:
        signal FrozenObject(object)

    if object.state == closed:
        signal ClosedObject(object)

    if not object.localSlots.contains(name):
        signal LocalSlotNotFound(object, name)

    value = object.localSlots[name]
    object.localSlots.remove(name)

    invalidateShapeAssumptions(object)

    return value
```

If a delegated slot with the same name exists, removing the local slot exposes that delegated slot to subsequent reads. No parent object is modified.

---

# 23. Closing Objects

```text
function closeObject(object):
    if object.state == frozen:
        return object

    object.state = closed

    return object
```

A closed object:

```text
may modify existing slots
may not create slots
may not delete slots
```

Closing is shallow.

---

# 24. Freezing Objects

```text
function freezeObject(object):
    object.state = frozen
    return object
```

A frozen object:

```text
may not create slots
may not delete slots
may not modify slot values
```

Freezing is shallow.

---

# 25. Identity

Identity is a semantic property and must not leak the runtime representation chosen for an object.

Built-in immutable value objects use value identity. In v0.1 this includes at least `Number`, `String`, `Boolean`, and `null`. Ordinary identity-bearing objects use an individual identity token or an equivalent runtime mechanism.

Conceptually:

```text
function identical(a, b):
    if isBuiltInValueObject(a) or isBuiltInValueObject(b):
        if valueIdentityKind(a) != valueIdentityKind(b):
            return false

        return sameSemanticValue(a, b)

    return sameObjectIdentity(a, b)
```

Consequences include:

```text
identical(1, 1)                         == true
identical("hello", "hello")             == true
identical("hel" + "lo", "hello")      == true
identical(true, true)                   == true
identical(null, null)                   == true

identical(newObject(), newObject())     == false
```

`Number` and `String` are immutable value objects. String operations never mutate an existing String in place; a changed textual value is another String value. A runtime may intern, share, inline, box, unbox, or otherwise optimize these values without changing `===`.

`true`, `false`, and `null` are canonical singleton values.

`===` is not overrideable. Hash codes are not identity: a hash collision must never cause two distinct identity-bearing objects to compare identical.

---

# 25. Semantic Equality

```text
function semanticEqual(a, b):
    result = send(
        receiver = a,
        message = "==",
        arguments = [b]
    )

    if result !== true and result !== false:
        signal InvalidEqualityResult(result)

    return result
```

`==` is ordinary object behavior and may be customized, but it has a strict protocol result contract: the result must be canonical `true` or `false`, or the operation must signal an error.

---

## Equality and Comparison Result Validation

Runtime implementations of the standard comparison protocol must validate their result when necessary:

```text
function requireBooleanComparisonResult(result):
    if result !== true and result !== false:
        signal InvalidComparisonResult(result)
    return result
```

The exact internal error object names are not fixed by Core v0.1, but arbitrary non-Boolean values must not be accepted as truthy or falsy comparison results.

---

# 26. Lazy Boolean Operators

```text
function evaluateAnd(leftExpression, rightExpression, activation):
    left = evaluate(leftExpression, activation)

    rightClosure = createClosure(
        activation = activation,
        parameters = [],
        code = rightExpression
    )

    return send(
        receiver = left,
        message = "and",
        arguments = [rightClosure]
    )
```

Likewise:

```text
function evaluateOr(leftExpression, rightExpression, activation):
    left = evaluate(leftExpression, activation)

    rightClosure = createClosure(
        activation = activation,
        parameters = [],
        code = rightExpression
    )

    return send(
        receiver = left,
        message = "or",
        arguments = [rightClosure]
    )
```

The right-hand expression is therefore evaluated only if the receiving boolean behavior chooses to invoke the closure.

---

# 27. Error Signaling

Errors are objects.

```text
function signal(error, activation):
    handlerContext = activation

    while handlerContext != null:
        handler = findMatchingHandler(
            handlerContext,
            error
        )

        if handler != NOT_FOUND:
            return invokeHandler(
                handler = handler,
                error = error
            )

        handlerContext =
            dynamicParentOf(handlerContext)

    terminateAtExecutionBoundary(error)
```

The exact syntax and standard handler API are intentionally not fixed by this document.

The runtime architecture should not prevent resumable conditions from being added later.

---

# 28. Future Creation

Calling:

```js
work.future()
```

eventually reaches a scheduling primitive.

Conceptually:

```text
function executeAsFuture(closure, parentActivation):
    future = new Future(
        state = pending
    )

    task = scheduler.createTask(
        owner = parentActivation,
        body = () => {
            try:
                result = invoke(
                    closure,
                    []
                )

                resolveFuture(
                    future,
                    result
                )

            catch error:
                failFuture(
                    future,
                    error
                )
        }
    )

    scheduler.schedule(task)

    return future
```

The scheduler implementation is not observable semantics.

---

# 29. Future Resolution

```text
function resolveFuture(future, value):
    if future.state != pending:
        signal InvalidFutureState()

    if isFuture(value):
        adoptFuture(
            destination = future,
            source = value
        )

        return

    future.state = resolved
    future.value = value

    wakeWaiters(future)
```

This performs automatic Future flattening.

---

# 30. Future Failure

```text
function failFuture(future, error):
    if future.state != pending:
        signal InvalidFutureState()

    future.state = failed
    future.error = error

    wakeWaiters(future)
```

There is no separate promise-rejection type.

The stored value is an ordinary the language error object.

---

# 31. Waiting for a Future

For:

```js
future.value()
```

conceptually:

```text
function awaitFutureValue(future, activation):
    switch future.state:

        case resolved:
            return future.value

        case failed:
            signal(
                future.error,
                activation
            )

        case cancelled:
            signal(
                FutureCancelled(),
                activation
            )

        case pending:
            scheduler.suspend(
                activation,
                until = future
            )

            return awaitFutureValue(
                future,
                activation
            )
```

Suspending an activation does not imply blocking an OS thread.

---

# 32. Future Composition

For:

```js
future.then(value) {
    transform(value)
}
```

conceptually:

```text
function futureThen(source, transformClosure):
    destination = new Future(
        state = pending
    )

    onFutureCompletion(source, result => {

        if result is failure:
            failFuture(
                destination,
                result.error
            )

            return

        try:
            transformed = invoke(
                transformClosure,
                [result.value]
            )

            resolveFuture(
                destination,
                transformed
            )

        catch error:
            failFuture(
                destination,
                error
            )
    })

    return destination
```

If `transformed` is itself a Future, `resolveFuture` adopts it and flattens the result.

---

# 33. Structured Concurrency

Asynchronous work belongs by default to the execution context that created it.

Conceptually:

```text
function registerChildTask(parentActivation, task):
    parentActivation.childTasks.add(task)
```

When the owner is cancelled:

```text
function cancelActivation(activation):
    mark activation as cancelled

    for each task in activation.childTasks:
        if not task.detached:
            cancelTask(task)
```

Detachment:

```text
function detachFuture(future):
    future.task.detached = true
    remove future.task from its owner

    return future
```

Exact cancellation guarantees should be refined in a later specification.

---

# 34. Canonical Evaluation Sketch

A minimal evaluator can be described as:

```text
function evaluate(node, activation):

    match node:

        Literal(value):
            return value

        Lookup(name):
            return lookupName(
                activation,
                name
            )

        Member(receiverExpr, name):
            receiver = evaluate(
                receiverExpr,
                activation
            )

            return readMember(
                receiver,
                name
            )

        Create(targetExpr?, name, valueExpr):
            value = evaluate(
                valueExpr,
                activation
            )

            target =
                activation.context
                if targetExpr is absent
                else evaluate(
                    targetExpr,
                    activation
                )

            return createSlot(
                target,
                name,
                value
            )

        Assign(targetExpr?, name, valueExpr):
            value = evaluate(
                valueExpr,
                activation
            )

            if targetExpr is absent:
                return assignName(
                    activation,
                    name,
                    value
                )

            target = evaluate(
                targetExpr,
                activation
            )

            return assignMember(
                target,
                name,
                value
            )

        Closure(parameters, body):
            return createClosure(
                activation,
                parameters,
                body
            )

        Call(receiverExpr, arguments):
            receiver = evaluate(
                receiverExpr,
                activation
            )

            values = evaluateArguments(
                arguments,
                activation
            )

            return invoke(
                receiver,
                values
            )

        Send(receiverExpr, message, arguments):
            receiver = evaluate(
                receiverExpr,
                activation
            )

            values = evaluateArguments(
                arguments,
                activation
            )

            return send(
                receiver,
                message,
                values
            )

        Return(valueExpr):
            value = evaluate(
                valueExpr,
                activation
            )

            nonLocalReturn(
                activation,
                value
            )

        Sequence(expressions):
            result = null

            for each expression:
                result = evaluate(
                    expression,
                    activation
                )

            return result
```

This sketch is intentionally small.

Most high-level language behavior should be expressed through ordinary objects and message sends rather than by adding evaluator cases.

---

# 35. Runtime Boundary

The following operations may require implementation primitives:

```text
object allocation
slot storage
object identity
native arithmetic
I/O
filesystem access
process creation
network access
scheduler interaction
activation suspension/resumption
garbage collection
```

These primitives do not alter the object model.

They are implementation services exposed through ordinary the language objects and messages whenever practical.

---

# 36. Design Test

A proposed new feature should normally be rejected as a new runtime primitive if it can be expressed cleanly using:

```text
objects
slots
delegation
closures
message sends
errors
futures
```

The runtime should grow only when the language requires behavior that cannot be implemented faithfully above that layer.

---

# Module Contexts and Top-Level Bindings

the language has no special global-variable category.

Every module executes inside a `moduleContext`, which is an ordinary the language object. A binding created at the top level of a module is therefore simply a local slot of that module's execution context.

For example:

```js
version: "0.1"

printVersion: () => {
    print(version)
}
```

is conceptually:

```text
moduleContext
├── version
└── printVersion
```

Closures created by top-level module execution capture the module context through the normal lexical-context mechanism. No separate global lookup rule is required.

Conceptually, lexical lookup may eventually reach the module context:

```text
context
    ↓
captured lexical contexts
    ↓
moduleContext
```

After lexical lookup is exhausted, the ordinary receiver/delegation lookup rules continue as specified elsewhere in this document.

Modules do not implicitly share mutable global state. Each module has its own module context. Cross-module visibility must be established explicitly by the module/import/export mechanism defined by a future module specification.

Universal language facilities such as core prototypes and standard behavior may be made available through a shared prelude or root environment. Such an environment is part of lexical/runtime setup and does not create a separate global-variable semantic category.

The standard prelude is a shared **frozen** context. Lookup may read its slots normally, but assignment may not modify them. Consequently, an unqualified assignment whose only matching slot is in the prelude fails with the ordinary non-writable/frozen assignment error. Shadowing is explicit slot creation in the module context.

```text
print("hello")     -> read prelude.print
print = myPrint     -> ERROR
print: myPrint      -> create moduleContext.print
```

Runtime initialization MUST freeze the prelude before executing user modules. `assignName` MUST respect that frozen state and MUST NOT special-case the prelude by mutating it.

Top-level creation:

```js
x: value
```

is equivalent to creating a local slot on the current module context when the current activation's `context` is that module context.

Conceptually:

```text
function createModuleContext(preludeContext):
    require preludeContext.state == frozen

    return Object(
        parent = standardContextPrototype,
        state = open
    )
```

```text
function executeModule(module, preludeContext):
    moduleContext = createModuleContext(preludeContext)

    activation = Activation(
        context = moduleContext,
        lexicalParent = preludeContext,
        receiver = moduleContext,
        methodHome = null,
        returnHome = null,
        ownsReturnHome = false
    )

    result = executeModuleBody(
        module.code,
        activation
    )

    return ModuleExecution(
        context = moduleContext,
        result = result
    )
```

Module loading, canonical identity, initialization states, cycle handling, and failure caching are defined later in this document; host-specific resolution and package policy remain outside Core v0.1.

Core invariant:

```text
There are no global variables as a special runtime category.

Top-level bindings are slots of a module execution context.

Modules do not implicitly share mutable global state.
```


## Dynamic Typing Runtime Requirement

Runtime dispatch is dynamic. Slots and parameters carry objects, not mandatory declared static types.

An implementation may specialize nodes based on observed receiver shapes, prototypes, numeric representations, or inferred types, but deoptimization must preserve the same semantics when those assumptions stop holding.

No runtime overload-resolution mechanism based on declared argument types is part of Core v0.1.

## Conditional Message Semantics

There is no runtime-wide `toBoolean`, truthiness table, or implicit Boolean coercion.

Conditional operations use normal message dispatch.

Conceptually:

```text
send(condition, "ifTrue", [block])
send(condition, "ifFalse", [block])
```

are ordinary sends. The standard objects `true` and `false` implement these messages with the expected Boolean behavior.

Logical operators preserve laziness by passing the right-hand expression as a closure:

```text
a && b  =>  send(a, "and", [closure(() => b)])
a || b  =>  send(a, "or",  [closure(() => b)])
```

The receiver decides whether to invoke the supplied closure.

Objects other than `true` and `false` may implement the same protocol. If a receiver does not understand the message, normal message-lookup failure semantics apply.

Equality and comparison implementations, including user-defined ones, are constrained by their protocol contract to return the canonical Boolean objects `true` or `false`, or signal an error. Returning another object is an invalid protocol result.

An implementation may use inline caches, specialized AST nodes, partial evaluation, or JIT compilation for common cases such as receivers known to be `true` or `false`, or numeric `+`. These optimizations must be observationally equivalent to the corresponding ordinary message sends.


## Error Signaling, Handler Matching, and Unwinding

Errors are ordinary objects. Signaling an error searches dynamically active handlers from the current activation outward.

Each handler has a match prototype. A handler matches when that prototype occurs in the signaled error object's delegation chain. Matching therefore uses the normal prototype/delegation model rather than a class hierarchy or static type test.

Conceptually:

```text
function signal(error, activation):
    handler = nearestMatchingDynamicHandler(error, activation)

    if handler == none:
        terminateAsUnhandled(error)

    unwindTo(handler.activation)
    invokeHandler(handler, error)
```

Conceptually, prototype matching is:

```text
function handlerMatches(handler, error):
    current = error

    loop:
        if current === handler.matchPrototype:
            return true

        if current === Object:
            return false

        current = current.parent
```

`Object` is the unique root and has no parent; no `null` or hidden sentinel is used as a delegation-chain terminator.

Core v0.1 handlers are **unwinding handlers**. Invoking a matching handler abandons the signaling continuation. If the handler returns normally, execution continues according to the handler-installation construct; it does not return a value to the original `signal` operation and does not resume immediately after the signaling point.

Core v0.1 does not expose resumable conditions, `resume`, `retry`, or equivalent operations. Implementations should keep signaling, handler search, and stack transfer conceptually separable so that a later explicit resumable-condition facility can be introduced without redefining error objects or prototype-based handler matching.


## Module Registry and Canonical Module Identity

Module loading uses a registry keyed by canonical internal module identity.

Conceptually:

```text
ModuleRegistry:
    ModuleKey -> ModuleRecord

ModuleRecord:
    state
    moduleContext?
    value?
    failure?
```

Possible states are:

```text
UNLOADED
LOADING
LOADED
FAILED
```

A module specifier is not itself the cache key. The loader first resolves it relative to the importing module:

```text
resolve(importerKey, moduleSpecifier) -> ModuleKey
```

`ModuleKey` must be canonical and stable within the registry. Equivalent requests for the same module must resolve to the same key.

For a file-backed host this may conceptually involve normalization such as:

```text
"./lib/../lib/foo.pt"
    -> canonical file identity
    -> file:///project/lib/foo.pt
```

The exact key representation is host-defined and need not be visible to language code.

Conceptual import logic:

```text
function importModule(importerKey, specifier):
    key = resolve(importerKey, specifier)
    record = registry.lookupOrCreate(key)

    if record.state == LOADED:
        return record.value

    if record.state == FAILED:
        signal record.failure

    if record.state == LOADING:
        signal ModuleInitializationCycle(key)

    record.state = LOADING
    record.moduleContext = createModuleContext(frozenPrelude)

    try:
        value = executeModule(key, record.moduleContext)
        record.value = value
        record.state = LOADED
        return value
    catch initializationFailure:
        record.failure = initializationFailure
        record.state = FAILED
        signal initializationFailure
```

A module value is not made available while its record is `LOADING`. This intentionally forbids observable partially initialized exports.

Successful initialization produces exactly one cached module value. The value may be any language object.

Imports are eager by default. Lazy dependency behavior is expressed explicitly using ordinary closures or other language mechanisms rather than by changing import evaluation semantics.

The private `moduleContext` and the module value are distinct concepts. The runtime must not implicitly expose the entire module context as the module's public result unless the module itself deliberately returns or constructs such an object.

Host-specific resolution policy, package lookup, standard-library naming, remote sources, and package-manager behavior are outside Core Runtime Semantics v0.1.


## Indexed Access Lowering

The runtime has no separate semantic indexing primitive required by the language.

The parser or semantic-lowering phase rewrites bracket forms to normal sends:

```text
receiver[index]
    -> Send(receiver, "at", [index])

receiver[index] = value
    -> Send(receiver, "atPut", [index, value])
```

Conceptual evaluation for indexed write:

```text
receiverValue = evaluate(receiver)
indexValue = evaluate(index)
assignedValue = evaluate(value)
send(receiverValue, "atPut", [indexValue, assignedValue])
result = assignedValue
```

Each subexpression is evaluated once and in left-to-right order.

`at` and `atPut` are ordinary selectors. Arrays, maps, strings, foreign objects, user-defined collections, or unrelated domain objects may implement either message. The runtime must not impose array-specific dispatch merely because bracket syntax was used.

Consistent with the existing assignment-expression rule, indexed assignment evaluates to the value written. The return value of the underlying `atPut` message is not the value of the indexed assignment expression.


## Invocation Argument Binding

Each invocation records the caller-supplied positional arguments before default substitution.

Conceptually:

```text
Activation
    context
    receiver
    arguments
    methodHome
    returnHome
    ...
```

The intrinsic `args` resolves to an immutable ordinary collection representing `Activation.arguments`.

For a receiver-aware send:

```js
receiver.message(a, b)
```

the invocation state is conceptually:

```text
this = receiver
args = [evaluated(a), evaluated(b)]
```

The receiver is not inserted into `args`.

Parameter binding proceeds from left to right. Caller-supplied arguments bind first. Missing parameters with default expressions evaluate those defaults in the new invocation context. The original `args` collection is not modified by default substitution.

A trailing rest parameter receives an ordinary collection containing caller-supplied arguments that were not consumed by preceding positional parameters.

Spread arguments are evaluated left-to-right with surrounding arguments. After evaluation, their elements are expanded into the outgoing positional argument sequence exactly once.

Conceptually:

```text
f(...values)
```

becomes a normal invocation whose outgoing argument vector contains the elements produced by the spread operation.

The runtime may optimize argument vectors, rest collections, and `args` views, but observable semantics must remain those of ordinary immutable collections.

No dispatch by argument type is implied. These mechanisms support dynamic arity, forwarding, and user-defined helper protocols without introducing method-overload resolution.


## Polymorphic Call Protocol and Default Construction (Normative Detail)

Invocation is a protocol operation on the evaluated receiver.

Conceptually:

```text
Call(receiverExpression, arguments):
    receiver = evaluate(receiverExpression)
    args = evaluateArgumentsLeftToRight(arguments)
    return invoke(receiver, args)
```

`invoke` is polymorphic. Closures provide executable-call behavior. Ordinary prototypes inherit default construction-call behavior from `Object`.

The default `Object` call behavior is conceptually:

```text
function objectCall(prototype, args):
    instance = createObject(parent = prototype)

    signalOrReturn = send(instance, "init", args)

    return instance
```

The result of `init` is deliberately ignored. Successful construction returns the fresh instance.

If `init` signals, normal error-unwinding semantics apply and the construction call does not successfully return the instance.

The standard `Object.init` accepts no arguments. A non-empty argument vector handled by the inherited default initialization signals an argument-count error.

`init` is found through ordinary message lookup beginning at the fresh instance, so a prototype may specialize initialization simply by providing an `init` slot.

Alternative constructors require no runtime facility. Named constructor-like messages are ordinary sends and may invoke the receiver or another prototype through the same call protocol.

Object-literal creation:

```text
Object(parent, body)
```

remains a separate semantic AST operation. It creates a fresh object with the given parent and evaluates its object body; it does not implicitly invoke `init`.

The implementation may specialize closure invocation and standard object construction in Truffle nodes or JIT-compiled paths, but observable behavior must remain equivalent to the polymorphic call protocol.


## Unwind-Safe Cleanup

The runtime maintains cleanup registrations associated with dynamic execution scopes.

Conceptually:

```text
ensure(body, cleanup):
    register cleanup for protected dynamic scope

    execute body

    on leaving the protected scope:
        execute cleanup
```

Cleanup runs when the protected scope is exited by:

```text
normal completion
non-local return (^)
error unwind
```

If cleanup returns normally, the original completion or control transfer continues.

If cleanup signals an error, the cleanup error becomes the active control transfer. A previously active non-local return or error unwind does not continue past that point.

This behavior uses the same general runtime machinery that tracks non-local control transfer and dynamic handlers, but resumable conditions are not required for Core v0.1.

A future condition that is resumed without leaving the protected dynamic scope must not trigger its cleanup merely because the condition was signaled.

The runtime may represent cleanup registrations as unwind records, dynamic frames, or another implementation-specific structure. The representation is not observable.

No GC finalizer or reachability callback is part of the deterministic resource-lifetime semantics.


## Exact Cross-Family Numeric Equality

Numeric `==` is defined by mathematical value rather than by coercing one operand into the representation of the other.

Conceptually:

```text
function numericEqual(a, b):
    require isNumeric(a)
    require isNumeric(b)

    return exactNumericValueCompare(a, b)
```

`exactNumericValueCompare` must be representation-aware enough to avoid rounding-induced false equality.

For Integer-versus-Float comparison, the runtime must not simply convert an arbitrary-precision Integer to Float. It may instead compare the exact Integer against the exact finite binary value represented by the Float.

For example:

```text
9007199254740992 == 9007199254740992.0  -> true
9007199254740993 == 9007199254740992.0  -> false
```

Cross-family numeric equality must be symmetric when both sides complete normally.

Numeric semantic identity is stricter. Conceptually:

```text
numericIdentity(a, b):
    return sameSemanticNumericFamily(a, b)
       and sameNumericIdentityValue(a, b)
```

Therefore:

```text
1 === 1.0               -> false
UInt8(1) === 1          -> false
Int32(1) === UInt32(1)  -> false
```

The implementation may use optimized paths, but it must preserve this distinction between exact numeric equality and numeric-family-sensitive identity.

Special Float identity/equality rules for NaN and signed zero are defined separately.

## Float NaN Semantic Identity

IEEE-754 permits multiple NaN encodings, including distinct payloads and categories such as quiet and signaling NaNs.

Core v0.1 does not expose those representation distinctions through ordinary semantic identity.

Conceptually:

```text
function floatSemanticIdentity(a, b):
    if isNaN(a) and isNaN(b):
        return true

    return sameFloatIdentityValue(a, b)
```

By contrast, numeric equality follows IEEE-style NaN comparison behavior:

```text
NaN == anyNumericValue  -> false
anyNumericValue == NaN  -> false
```

including:

```text
NaN == NaN -> false
```

A runtime may preserve NaN payloads internally or expose them through an explicit low-level representation protocol, but payload/sign/boxing differences must not make ordinary `===` distinguish semantic NaN values.

`NaN` is not required to be implemented as a canonical singleton object. The runtime may produce many host-level or boxed NaN representations while preserving:

```text
nanA === nanB -> true
```

for semantic Float NaN values.

## Float Signed Zero Runtime Semantics

Float semantic equality and identity treat signed zero differently.

Conceptually:

```text
function floatNumericEqual(a, b):
    if isZero(a) and isZero(b):
        return true

    return ieeeNumericEqual(a, b)
```

Semantic identity preserves the sign distinction:

```text
function floatSemanticIdentity(a, b):
    if isNaN(a) and isNaN(b):
        return true

    if isZero(a) and isZero(b):
        return sameZeroSign(a, b)

    return sameFloatIdentityValue(a, b)
```

Therefore:

```text
+0.0 == -0.0   -> true
+0.0 === -0.0  -> false
```

The implementation may use native IEEE-754 operations or specialized representations, but must preserve these observable semantics.

## Numeric Runtime Semantics

Integer arithmetic is semantically exact and must not expose host-machine overflow. A runtime may specialize common integer operations using native machine widths and promote transparently to arbitrary-precision storage when required.

Such specialization is not observable through identity, equality, message lookup, or arithmetic results.

Fixed-width integer objects have explicit range and width semantics. Ordinary operations that exceed the representable range signal an error rather than silently wrapping. Separate explicitly wrapping protocols may be provided.

Numeric protocol dispatch remains ordinary receiver-based message lookup. Integer-only messages such as bit operations are found through the receiver's delegation chain; there is no static overload resolution.

Floating-point values may use an IEEE-754-compatible host representation provided observable language semantics are preserved.

Byte order is applied only when encoding or decoding numeric values to or from byte sequences. Endianness is not stored as an intrinsic property of the abstract numeric value.


## String and Bytes Runtime Separation

The runtime must preserve the semantic distinction between Unicode text and raw byte sequences.

A `String` represents abstract Unicode text. Its internal storage format is implementation-specific and must not be observable as though it were the String's semantic encoding.

A `Bytes` value represents an ordered sequence of byte values with no implicit character encoding.

Encoding and decoding are explicit operations parameterized by an encoding protocol/object. Implementations may intrinsify common encodings such as UTF-8 while preserving ordinary observable message semantics.

The exact meaning of String indexing and String size is defined separately from byte representation; internal code-unit layout must not determine those operations accidentally.


## Text Indexing and Mutability Runtime Semantics

Observable `String.size` and `String.at` semantics are based on Unicode grapheme clusters, not on the runtime's internal byte or code-unit representation.

Implementations may cache grapheme boundaries, specialize common ASCII/Latin text, or use representation-specific fast paths, provided observable indexing semantics remain unchanged.

`String` values are immutable. Runtime optimizations such as interning, deduplication, compact encodings, ropes, slices, or structural sharing are permitted when they preserve value semantics.

`Bytes` values are mutable raw byte sequences.

Encoded text representations are ordinary objects whose mutability is protocol-defined. The runtime must not infer writability merely from the fact that an object contains bytes or represents text.


## Map and Hash Runtime Semantics

Normal `Map` lookup uses the key protocol pair `hash` and `==`.

Map does not define a separate equality-result convention. It relies on the language-wide `==` contract: canonical `true` or `false`, or an error.

Implementations may use hash tables, inline caches, specialized key representations, or other internal structures, provided observable semantics follow the language-level equality/hash contract.

The required invariant for correctly behaving keys is:

```text
a == b  =>  a.hash == b.hash
```

The hash/equality behavior relevant to a key must remain stable while that key is present in a `Map`.

If user code violates this invariant or mutates a key so that its relevant hash/equality behavior changes, the runtime is not required to repair or reindex that entry automatically. Later operations may fail to locate it or may observe otherwise inconsistent key behavior at the language level. This does not permit memory unsafety or corruption of the runtime itself.

`IdentityMap` uses primitive semantic identity (`===`) together with a stable `identityHash`.

`hash` values need only be valid within the current execution. The runtime may salt hashes per process. Persisted hash values must therefore not rely on the ordinary `hash` protocol.

Map iteration preserves insertion order as an observable collection property.

## Custom Operator Runtime Note

Custom symbolic operators do not create a separate runtime dispatch mechanism. After lexing and parsing, a custom binary operator is an ordinary message selector.

For example:

```js
a |> b
```

lowers conceptually to an ordinary send whose selector is `"|>"`.

The permitted symbolic character alphabet is a parser/lexer rule and is not mutable at runtime.
