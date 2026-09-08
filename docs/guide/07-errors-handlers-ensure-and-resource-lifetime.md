# Errors, Handlers, `ensure`, and Resource Lifetime

> **Status:** Non-normative programming guide
>
> **Normative owners:** `spec/semantics/ERRORS.md`,
> `spec/semantics/EXECUTION_AND_CONTROL.md`,
> `spec/semantics/CALLABLES.md`, and `spec/PROTOS_GRAMMAR.md`

Protos treats failure and cleanup as ordinary object/protocol behavior while
still giving their control effects precise semantics.

The central model is:

> Errors are objects. Signaling is a non-resumable control transfer. Handlers
> are dynamically scoped. `ensure` protects one dynamic extent and runs cleanup
> exactly once when that extent is semantically left.

There are no required `try`, `catch`, `throw`, or `finally` keywords, no second
Exception hierarchy, and no deterministic object destructor.

## Expected failures can still be ordinary values

Not every unsuccessful result has to be signaled.

A protocol may deliberately use an ordinary value when absence or failure is
part of its normal data model.

For example, an API may define:

```protos
find(...)
// -> value or null
```

when that is its actual contract.

Exceptional conditions use Error signaling instead:

```protos
problem: Error()
problem.signal()
```

Do not treat `null` as the universal replacement for failure. The protocol
defines whether an outcome is ordinary data or an Error transfer.

## Errors are ordinary objects

`Error` is a standard prototype rooted in the ordinary object model.

An application can create a more specific category by delegation:

```protos
ConfigurationError: Error {
}

MissingSetting: ConfigurationError {
}
```

Instances created from those prototypes participate in ordinary delegation:

```protos
e: MissingSetting()
```

The Error system does not introduce a separate class hierarchy beside the
normal Protos delegation graph.

## Standard Error categories use delegation

Core and normative domains define a deliberately small set of standard Error
prototypes.

Examples include:

```text
Error
SlotNotFound
InvalidSuper
InvalidReturn
Cancelled
IOError
InvalidIOArgument
IOLifecycleError
```

The immediate parent relationships are normative.

A standard failure category is a prototype, not normally a singleton failure
instance. The runtime can create an ordinary fresh Error object that delegates
to the required category.

That distinction matters for identity and handler matching.

## Standard failure occurrences are fresh objects

When a normative rule says that an operation signals a new failure but does not
already identify an existing Error object to propagate, each failure occurrence
gets fresh semantic identity.

Conceptually:

```protos
first: null
second: null

Error.handle(
    () => {
        missingName
    },
    (error) => {
        first = error
    }
)

Error.handle(
    () => {
        anotherMissingName
    },
    (error) => {
        second = error
    }
)

first === second
```

must not become true merely because both failures belong to the same category.

The category may match; the occurrence identity remains distinct.

## Signaling preserves an existing Error's identity

When you explicitly signal an Error object:

```protos
problem: Error()
problem.signal()
```

the exact `problem` object is the signaled object.

Signaling does not implicitly:

```text
clone
wrap
replace
convert
attach visible stack slots
```

A matching handler receives that exact object.

If the same Error is later re-signaled under a semantic rule that records and
replays that exact failure, its identity remains that Error in the same value
domain.

## `signal()` never returns to its signaling point

The standard protocol is:

```protos
error.signal()
```

and it is **non-resumable**.

This code:

```protos
continued: false

Error.handle(
    () => {
        Error().signal()
        continued = true
    },
    (error) => {
        null
    }
)
```

does not continue after `signal()`.

The signaling continuation has been abandoned.

The handler may determine the result of the enclosing `handle(...)` operation,
but it does not supply a value back into `signal()`.

## Core has no privileged `resume` or `retry`

Core v0.1 does not define Error operations that mean:

```text
resume the signal point
retry the protected expression
use this replacement value
restart from a named recovery point
```

A user object can define ordinary messages with names such as `resume` or
`retry`, but those names have no special Error-control authority.

Recovery that wants another attempt is written explicitly using ordinary
control mechanisms.

For example:

```protos
tryAgain: () => {
    ...
}
```

can be invoked again by application logic, but that is a new ordinary
invocation, not resumption of an abandoned `signal()` continuation.

## `handle` installs a dynamic handler

The standard handler operation is:

```protos
matchPrototype.handle(body, handler)
```

A common broad handler is:

```protos
Error.handle(
    () => {
        riskyOperation()
    },
    (error) => {
        recover(error)
    }
)
```

A narrower handler can use a specific Error prototype:

```protos
IOError.handle(
    () => {
        readData()
    },
    (error) => {
        recoverIO(error)
    }
)
```

The receiver is the match prototype.

`body` and `handler` must both be semantic Closures.

## Handler installation is eager about its arguments

Ordinary expression evaluation happens first.

Conceptually the order is:

```text
evaluate receiver
evaluate body expression
evaluate handler expression
validate body is Closure
validate handler is Closure
install handler
invoke body()
```

If validation fails, the new handler has not been installed and `body` is not
invoked.

This avoids a half-installed dynamic scope.

## Handler matching follows ordinary delegation

Suppose an Error object delegates as:

```text
someError
    -> InvalidIOArgument
    -> IOError
    -> Error
    -> Object
```

Then handlers matching any Error object on that chain are candidates.

For example:

```protos
IOError.handle(...)
```

matches `someError`.

No hidden Java class, enum tag, string code, or second exception-type system is
consulted.

Exact object identity is simply the narrowest possible delegation match when
the match object is the signaled Error itself.

## The dynamically innermost matching handler wins

Handlers are dynamically scoped.

If matching handlers are nested:

```protos
Error.handle(
    () => {
        IOError.handle(
            () => {
                readData()
            },
            (error) => {
                innerRecovery(error)
            }
        )
    },
    (error) => {
        outerRecovery(error)
    }
)
```

an `IOError` raised while the inner protected extent is active selects the
dynamically innermost matching handler.

You do not construct or order a separate handler list. Ordinary nesting defines
the dynamic order.

## A selected handler is a one-shot unwind destination

When a matching handler is selected, its handler frame becomes inactive before
unwinding crosses `ensure` scopes and before the handler Closure is invoked. Think
of that selection as committing one unwind destination for the original Error.

If crossed cleanup completes normally, control reaches that destination and the
handler receives the original Error. If crossed cleanup escapes with a later
transfer, that later transfer supersedes the original one and the consumed
destination is abandoned rather than reused for the replacement transfer.

The same consumption also gives this important rule:

> The selected handler cannot catch an Error raised by its own handler Closure.

If that Closure signals another Error, handler search continues among still
active outer handlers.

## A handler receives the exact signaled Error

The handler Closure receives one argument:

```protos
(error) => {
    ...
}
```

and that argument is the exact signaled Error object selected by the transfer.

If the handler completes normally, its normal result becomes the result of the
enclosing `handle(...)`.

For example:

```protos
result: Error.handle(
    () => {
        Error().signal()
    },
    (error) => {
        42
    }
)

// result == 42
```

This does not mean signaling resumed. The protected body was abandoned; the
handler supplied the outer `handle` result.

## Normal protected completion bypasses the handler

If `body` completes normally:

```protos
result: Error.handle(
    () => {
        object
    },
    (error) => {
        fallback
    }
)
```

then the handler Closure is not invoked and `handle` returns the exact normal
result produced by `body`.

The handler is an Error boundary, not an unconditional callback.

## Handlers belong to the dynamic execution flow

A handler is not a property stored on the Error prototype, Actor, Closure, or
module merely because those objects participate in the call.

Its active installation belongs to the current dynamic control flow.

If that same task explicitly suspends while the handler remains active, the
handler remains part of that suspended continuation and is active again when
that task resumes.

Suspension does not terminate the handler scope.

## Distinct child tasks do not inherit dynamic handlers

Creating an asynchronous child task while a handler is active does not copy the
handler into that child task's dynamic state.

The child has its own Error outcome rules.

If a child fails and its Future records that Error, a later consumer operation
that explicitly observes that failure can re-signal the recorded Error in the
consumer's **then-current** handler context.

That composition will be developed further in the Futures chapter. The
important rule here is that dynamic handlers are not ambient Actor-global
state.

## `ensure` protects one dynamic extent

Deterministic cleanup uses the ordinary protocol:

```protos
body.ensure(cleanup)
```

Both the receiver `body` and the one `cleanup` argument must be semantic
Closures.

A typical shape is:

```protos
(() => {
    useResource()
}).ensure(() => {
    releaseResource()
})
```

This is Protos' fundamental unwind-safe cleanup mechanism.

It is not special `finally` syntax.

## `ensure` validates before entering the protected extent

The conceptual sequence is:

```text
evaluate receiver/body Closure
evaluate cleanup expression
validate body Closure
validate exactly one cleanup argument
validate cleanup Closure
enter protected extent
invoke body()
```

If validation fails, neither body nor cleanup is invoked and no cleanup frame is
left installed.

## Cleanup runs on every semantic scope exit

Once the protected body begins, cleanup runs exactly once when the protected
extent is left through any Core v0.1 exit mode:

```text
normal completion
non-local return
Error unwind
cooperative cancellation unwind
```

That is the central resource-lifetime guarantee.

You do not have to duplicate cleanup along every return/Error path.

## Normal `ensure` preserves the body's exact result

If the body completes normally:

```protos
object: {}

result: (() => {
    object
}).ensure(() => {
    cleanup()
})
```

and cleanup also completes normally, then:

```protos
result === object
```

The cleanup Closure's normal result is ignored.

`ensure` does not copy, convert, canonicalize, or re-read the body's result.

## Nested cleanup is LIFO

Nested protected extents unwind structurally from inner to outer.

Conceptually:

```protos
(() => {
    (() => {
        work()
    }).ensure(() => {
        cleanupInner()
    })
}).ensure(() => {
    cleanupOuter()
})
```

exits as:

```text
cleanupInner
cleanupOuter
```

This follows ordinary dynamic nesting. Core does not expose a global cleanup
registry or programmer-visible cleanup stack.

## Suspension is not cleanup

A suspension inside the protected body is not semantic scope exit.

For example, if an explicit operation such as:

```protos
future.value()
```

suspends the current task, the `ensure` cleanup does not run merely because the
runtime temporarily unwound a host stack or moved execution to another carrier.

When the task resumes, it continues inside the same protected extent.

Cleanup still runs exactly once when that extent is actually left.

## Cleanup itself may suspend

Cleanup is ordinary Protos execution.

If cleanup needs to wait for asynchronous release, it can explicitly perform
that wait:

```protos
(() => {
    use(file)
}).ensure(() => {
    file.close().value()
})
```

If that explicit wait suspends, resumption continues in the same cleanup
activation. Already-completed cleanup effects must not be duplicated.

## Returning a Future is not the same as awaiting cleanup

If cleanup merely returns a Future:

```protos
(() => {
    work()
}).ensure(() => {
    file.close()
})
```

`ensure` does not automatically observe or flatten that Future merely because it
was returned.

The cleanup's normal result is ignored.

If deterministic release requires waiting for asynchronous completion, perform
the required observation explicitly inside cleanup according to that resource's
protocol.

## The transfer that triggered cleanup stays pending

While cleanup runs, the result/control transfer that caused scope exit remains
pending.

If cleanup finishes normally, that pending outcome continues unchanged.

So a normal cleanup does not transform:

```text
normal result
non-local return
Error unwind
cancellation unwind
```

into a different result.

## A later cleanup transfer takes precedence

Cleanup is ordinary executable code and can itself transfer control.

If cleanup begins a new transfer that leaves cleanup, that **later** transfer
supersedes the pending earlier one.

For Error precedence, when the cleanup Error **escapes cleanup**:

```text
body signals originalError
cleanup signals cleanupError
cleanupError escapes cleanup
```

the active transfer becomes `cleanupError`. If cleanup installs a handler that
completely handles `cleanupError` inside cleanup and cleanup then completes
normally, no replacement transfer escapes and `originalError` remains pending.

Core does not automatically create:

```text
suppressed errors
cause chains
aggregate exceptions
composite failure wrappers
```

to preserve both.

Libraries may build such reporting structures explicitly when desired.

## Handler selection happens before crossed cleanup

There is an important interaction between `handle` and `ensure`.

Suppose a handler has already been selected for `originalError`, and unwinding
toward that handler crosses an `ensure` scope.

The selected handler is already inactive while that cleanup runs: it is the
one-shot destination chosen for `originalError`.

If cleanup completes normally, the unwind reaches that destination and the
selected handler receives `originalError`.

If a new Error escapes cleanup, it supersedes `originalError`; the consumed
destination is abandoned and normal search among still-active outer or
cleanup-installed handlers applies. The consumed handler is not reused even if
the replacement Error would also match it.

## Cancellation-safe cleanup is narrowly protected

When cooperative cancellation has already been observed and cancellation unwind
is running cleanup, that **same already-delivered cancellation request** is not
re-delivered at suspension boundaries inside that cleanup.

This lets cleanup perform asynchronous release without immediately being
interrupted by the very cancellation it is processing.

This is not a general cancellation mask. `Future.cancel()` is idempotent, so
repeating `cancel()` on that same pending Future does not manufacture a stronger
request that pierces the shielding of the request already being unwound.

Cleanup can still fail with ordinary Errors or encounter other independent
outcomes.

If cleanup completes normally, the pending cancellation continues afterward.

If cleanup signals an Error, the later Error takes precedence and the task
fails with that Error rather than completing as cancelled.

## Resource release is explicit protocol behavior

Core v0.1 defines no deterministic object destructor.

Resources expose their own explicit operations, for example:

```protos
file.close()
socket.close()
```

The object's protocol defines what those operations mean and whether they are
synchronous or Future-returning.

`ensure` gives programmers and libraries the general control guarantee needed to
compose those explicit release operations safely.

## Garbage collection is not resource management

Do not rely on object reachability or a host garbage collector to close
deterministic external resources.

Core provides no guarantee that a GC finalizer or reachability callback will run
at the semantic point when a resource must be released.

Likewise, `ensure` is an unwind guarantee while the owning execution can still
run Protos code; it is not a promise that cleanup runs after fail-stop Process/Node
loss or forced termination that prevents further execution. Durable or distributed
recovery requires an explicit higher-level protocol.

The safe mental model is:

```text
acquire explicitly
use in a protected extent
release explicitly in cleanup
```

## Higher-level resource helpers belong in ordinary libraries

With `ensure` available, libraries can build conveniences such as conceptual:

```text
use(resource, body)
withOpen(path, body)
withLock(lock, body)
```

without adding each pattern as Core syntax or a new control mechanism.

The helper can acquire the resource, run the user Closure under `ensure`, and
perform the resource-specific release operation.

This is another example of mechanisms over institutions: one precise dynamic
cleanup primitive supports many ordinary library abstractions.

## A practical acquisition/cleanup pattern

For a resource already acquired:

```protos
(() => {
    consume(resource)
}).ensure(() => {
    resource.close().value()
})
```

is the basic shape when `close()` returns a Future whose terminal completion must
be observed before cleanup is finished.

When acquisition itself can fail or be cancelled, use the resource/domain
contract carefully: only release a resource that actually became owned, and
account for the exact commitment/custody rules of that acquisition.

The Standard Library can hide those domain-specific details behind higher-level
helpers while still relying on the same `handle`/`ensure` foundations.

## Current ordinary-Protos usage

Current repository code already composes these mechanisms in ordinary Protos.

The filesystem convenience module:

- [`../../protos/lib/io/Files.protos`](../../protos/lib/io/Files.protos)

uses `Error.handle(...)` and `ensure(...)` around asynchronous filesystem
acquisition, reads, cancellation, and cleanup.

The package tool also uses dynamic handlers at ordinary language level:

- [`../../protos/tools/package/LockFile.protos`](../../protos/tools/package/LockFile.protos).

These are implementation/library examples, not new Error semantics.

## Representative executable conformance

The retained conformance suite includes focused evidence for handler and cleanup
behavior:

- [`../../protos/tests/conformance/error/handle-normal-result.protos`](../../protos/tests/conformance/error/handle-normal-result.protos)
  covers normal protected completion;
- [`../../protos/tests/conformance/error/handle-nonresumable-body.protos`](../../protos/tests/conformance/error/handle-nonresumable-body.protos)
  demonstrates that signaling does not resume the body;
- [`../../protos/tests/conformance/error/handle-selected-inactive.protos`](../../protos/tests/conformance/error/handle-selected-inactive.protos)
  covers selected-handler deactivation;
- [`../../protos/tests/conformance/control/ensure-normal-result.protos`](../../protos/tests/conformance/control/ensure-normal-result.protos)
  covers body-result preservation;
- [`../../protos/tests/conformance/control/ensure-nested-lifo.protos`](../../protos/tests/conformance/control/ensure-nested-lifo.protos)
  covers nested LIFO cleanup;
- [`../../protos/tests/conformance/control/ensure-cancellation-nested-lifo.protos`](../../protos/tests/conformance/control/ensure-cancellation-nested-lifo.protos)
  covers cleanup during cancellation unwind.

Executable tests demonstrate current behavior. The specification remains the
normative authority.

## Practical rules to remember

1. Errors are ordinary objects rooted by delegation at `Error`.
2. A standard failure occurrence normally creates a fresh Error identity.
3. Explicitly signaling an existing Error preserves that exact object.
4. `Error.signal()` is non-resumable; code after the signal point is abandoned.
5. `matchPrototype.handle(body, handler)` installs one dynamic unwinding handler.
6. Handler matching follows ordinary Error-object delegation.
7. The dynamically innermost matching handler wins.
8. The selected handler is inactive before its handler Closure and crossed
   cleanup execute.
9. Distinct asynchronous child tasks do not inherit a parent's dynamic handlers.
10. `body.ensure(cleanup)` runs cleanup exactly once on normal, return, Error, or
    cancellation scope exit.
11. Suspension/replay is not semantic scope exit and must not duplicate cleanup.
12. Normal cleanup preserves the body's pending result/transfer; a later cleanup
    transfer supersedes it.
13. Cleanup's normal result is ignored; explicitly await asynchronous release
    when the resource protocol requires it.
14. Use explicit resource release; never depend on GC finalization for
    deterministic lifetime.
15. Build higher-level resource helpers in ordinary Protos on top of `ensure`.

## Normative references

For exact behavior, consult:

- [`../../spec/semantics/ERRORS.md`](../../spec/semantics/ERRORS.md) for Error
  objects, failure occurrence identity, `signal`, non-resumability, dynamic
  `handle`, matching, selection, and Error precedence;
- [`../../spec/semantics/EXECUTION_AND_CONTROL.md`](../../spec/semantics/EXECUTION_AND_CONTROL.md)
  for the standard Closure `ensure` protocol, scope exits, suspension, LIFO
  cleanup, pending-transfer precedence, and deterministic cleanup;
- [`../../spec/semantics/CALLABLES.md`](../../spec/semantics/CALLABLES.md) for
  Closure-family receiver rules and ordinary invocation;
- [`../../spec/PROTOS_GRAMMAR.md`](../../spec/PROTOS_GRAMMAR.md) for the fact
  that `signal`, `handle`, and `ensure` add no dedicated `throw`/`try`/`catch`/
  `finally` grammar;
- [`03-closures-methods-and-receivers.md`](03-closures-methods-and-receivers.md)
  for the Closure invocation model used by handlers and cleanup;
- [`04-control-flow-through-protocols.md`](04-control-flow-through-protocols.md)
  for the broader principle that Core control behavior remains ordinary
  protocol composition.

Those documents define the language. This chapter supplies the programmer-facing
model for failure handling and deterministic cleanup.
