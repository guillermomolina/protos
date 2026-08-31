# Protia Runtime Semantics v0.1

Language version: 0.1  
Document revision: 8  
Status: Draft  
Last updated: 2026-08-30

This document defines executable-style pseudocode for the core runtime operations of Protia.

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

`Object` is the standard root prototype for ordinary objects. Reflective structural operations such as `removeSlot(name)`, `close()`, and `freeze()` are ordinary messages provided through `Object`, with runtime primitives implementing their structural effects.

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

    while current != null:
        result = lookupLocal(current, name)

        if result != NOT_FOUND:
            return result

        current = current.parent

    signal SlotNotFound(
        receiver = receiver,
        name = name
    )
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

There is no distinct runtime-language `Method` value category. The core executable value is `Closure`. A member read never executes a closure. If member lookup yields a closure, the runtime may represent the receiver binding as a lightweight `BoundClosure` wrapper/view, but this is binding metadata rather than a separate method object in Protia semantics.

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

A method send must preserve both the original receiver and the object where the method was found.

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

    callable = result.value

    if not isCallable(callable):
        signal NotCallable(
            value = callable,
            message = message
        )

    return invokeMethod(
        callable = callable,
        receiver = receiver,
        methodHome = result.home,
        arguments = arguments
    )
```

---

# 12. Method Invocation

```text
function invokeMethod(callable, receiver, methodHome, arguments):
    closure = unwrapClosure(callable)

    activation = createActivation(
        closure = closure,
        arguments = arguments,
        receiver = receiver,
        methodHome = methodHome,
        establishReturnHome = true
    )

    return executeActivation(activation)
```

The important rule is:

```text
receiver != methodHome
```

in general.

For:

```text
rex → dog → animal
```

if `rex.speak()` finds `speak` in `animal`:

```text
receiver   = rex
methodHome = animal
```

Inside the method:

```text
this === rex
```

---

# 13. Plain Closure Call

A direct call such as:

```js
f(a, b)
```

does not perform member lookup.

```text
function call(callable, arguments):
    if not isCallable(callable):
        signal NotCallable(callable)

    if isBoundClosure(callable):
        return invokeMethod(
            callable = callable.closure,
            receiver = callable.boundReceiver,
            methodHome = callable.boundMethodHome,
            arguments = arguments
        )

    closure = unwrapClosure(callable)

    activation = createActivation(
        closure = closure,
        arguments = arguments,
        receiver = closure.capturedThis,
        methodHome = closure.capturedMethodHome,
        establishReturnHome =
            (closure.capturedReturnHome == null)
    )

    return executeActivation(activation)
```

This preserves the receiver of extracted closures/method references. The plain call does not infer a receiver from the slot name; it uses the binding metadata created when the closure-valued member was read.

---

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

# 15. Activation Creation

Protia uses a Smalltalk/Squeak-style **home activation** for `^`.

A method invocation always establishes a fresh return home. A module-level function closure has no captured return home, so calling it also establishes a fresh home. A closure created inside an already active function or method captures that existing home; an ordinary call of that nested closure preserves it.

```text
function createActivation(
    closure,
    arguments,
    receiver,
    methodHome,
    establishReturnHome
):
    if length(arguments) != length(closure.parameters):
        signal ArgumentCountError()

    context = new Object(
        parent = standardContextPrototype
    )

    for each (parameter, argument):
        createSlot(
            target = context,
            name = parameter,
            value = argument
        )

    if establishReturnHome:
        returnHome = new ReturnTarget()
        ownsReturnHome = true
    else:
        returnHome = closure.capturedReturnHome
        ownsReturnHome = false

    return Activation(
        context = context,

        lexicalParent =
            closure.lexicalContext,

        receiver = receiver,

        methodHome = methodHome,

        returnHome = returnHome,
        ownsReturnHome = ownsReturnHome
    )
```

The distinction between owning and merely sharing a return home is essential. A nested block must allow `^` to unwind through its own activation rather than catching the return itself.

A method invocation dynamically supplies `receiver` and `methodHome` and establishes a new return home, even if the closure object was originally created inside another activation. This keeps installed method behavior independent while preserving Smalltalk-style non-local return for ordinary nested blocks.

An ordinary nested closure invocation uses the captured receiver/method-home metadata and the captured return home.

---

# 16. `super`

`super` preserves the original receiver but changes where lookup begins. It is not a runtime object or first-class value. The parser lowers only `super.message(arguments...)` to a super-send operation. Bare `super`, passing it as a value, assigning it, or extracting `super.message` without a call is invalid.

Conceptually, a super send uses execution metadata available through `context`: the receiver is the current receiver and the lookup origin is the parent of the current `methodHome`.

```text
function sendSuper(activation, message, arguments):
    if activation.methodHome == null:
        signal InvalidSuper()

    lookupStart = activation.methodHome.parent

    if lookupStart == null:
        signal SlotNotFound(
            receiver = activation.receiver,
            name = message
        )

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

# 21. Composition

For:

```js
target: parent {
    ...traitA
    ...traitB

    move: () => {
        ...
    }
}
```

composition is conceptually structural.

```text
function composeSlots(target, source):
    for each local slot in source:
        name = slot.name

        if target already has local slot name:
            markConflict(target, name, source)
        else:
            provisionally copy slot to target
```

After the full object body has been processed:

```text
function resolveCompositionConflicts(target):
    for each unresolved conflict:
        if object body explicitly declared local slot with same name:
            conflict is resolved
        else:
            signal CompositionConflict(
                target = target,
                name = conflict.name
            )
```

Composition never changes `target.parent`.

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
    return send(
        receiver = a,
        message = "==",
        arguments = [b]
    )
```

`==` is ordinary object behavior and may be customized.

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
                result = call(
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

The stored value is an ordinary Protia error object.

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
            transformed = call(
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

        Call(callableExpr, arguments):
            callable = evaluate(
                callableExpr,
                activation
            )

            values = evaluateArguments(
                arguments,
                activation
            )

            return call(
                callable,
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

They are implementation services exposed through ordinary Protia objects and messages whenever practical.

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

Protia has no special global-variable category.

Every module executes inside a `moduleContext`, which is an ordinary Protia object. A binding created at the top level of a module is therefore simply a local slot of that module's execution context.

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

The exact module loading, import, export, initialization, and cyclic-dependency semantics remain to be specified.

Core invariant:

```text
There are no global variables as a special runtime category.

Top-level bindings are slots of a module execution context.

Modules do not implicitly share mutable global state.
```
