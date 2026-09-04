# Protos Callables v0.1

Language version: 0.1
Document revision: 332
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

The value of the final expression in a closure is its normal return value.

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

Every invocation exposes the arguments supplied by the caller through the reserved intrinsic `args`.

`args` is not an ordinary writable identifier and cannot be shadowed by a parameter or local slot.

`args` contains exactly the explicit argument expressions from the call site, after evaluation and in source order. It does not contain the receiver and does not contain the caller activation.

For example:

```js
dog.move(10, 20)
```

inside `move`:

```js
this       // dog
args[0]    // 10
args[1]    // 20
args.size  // 2
```

The receiver remains available through `this`. Caller introspection, if exposed in the future, belongs to execution-context reflection rather than the argument collection.

Default parameters are supported. Defaults apply only when the corresponding argument was not supplied by the caller.

```js
foo: (a, b = 10) => {
    ...
}
```

For:

```js
foo(1)
```

`b` is bound to `10`, while `args` still contains only the caller-supplied value:

```text
args.size == 1
```

A closure may declare one trailing rest parameter:

```js
foo: (first, ...rest) => {
    ...
}
```

The rest parameter is bound to the standard invocation-argument collection defined below, containing the remaining caller-supplied arguments.

Argument spread is supported at call sites:

```js
pack: (...items) => items
values: pack(10, 20, 30)
f(...values)
```

which invokes `f` with the elements of `values` as individual positional arguments, preserving their order. Here `pack` is a rest-capturing closure: its rest parameter binds the ordinary collection containing `10`, `20`, and `30`, and the spread expands that collection's elements into `f`'s arguments.

These facilities are intended to make invocation forwarding and dynamic arity ordinary language operations:

```js
forward: (...args) => {
    target(...args)
}
```

Protocols analogous to Smalltalk block invocation helpers may therefore be implemented using ordinary callable objects and spread, for example conceptually:

```js
f.value(10, 20)
f.values(pack(10, 20))
```

where such protocol methods delegate to normal invocation. No overload resolution by argument type is introduced by these facilities.

Argument lists and parameter lists are comma-separated lists: `,` is the only separator between elements, a comma is a separator rather than a terminator, and trailing commas are syntax errors. Newlines inside the delimiters are continuation/layout under the newline-continuation rules in Separators, Line Breaks, and Comments; they never substitute for a required comma.

## Polymorphic Invocation and Object Construction

This section owns invocation/call-protocol consequences. Object creation, delegation-parent, slot-construction, and object-state semantics remain owned by `OBJECT_MODEL.md`; syntax and mandatory parse/lowering rules remain owned by `../PROTOS_GRAMMAR.md`.


Parentheses are a polymorphic invocation syntax. Invoking an object uses that object's call protocol; callability is therefore behavior, not a special static category reserved exclusively for closures.

Conceptually:

```js
receiver(a, b)
```

performs the receiver's ordinary invocation protocol with the evaluated arguments.

`Closure` provides the standard executable implementation of that protocol. `Object` provides the standard object-construction implementation inherited by ordinary prototypes.

The default construction behavior is conceptually:

```text
Object.call(...args):
    instance = createObject(parent = this)
    send(instance, "init", args)
    return instance
```

Thus:

```js
Point(10, 20)
```

creates a fresh object whose immutable delegation parent is `Point`, sends `init(10, 20)` to the fresh object, and returns that fresh object.

`init` is an ordinary overridable message. Its return value is ignored by the default construction protocol; the construction expression returns the created instance.

If initialization signals an error, construction fails and the construction expression produces no successful instance result.

`Object` provides a default `init` behavior that accepts zero arguments and signals an argument-count error when arguments are supplied. Therefore:

```js
Thing()
```

works for a prototype that does not define its own `init`, while:

```js
Thing(1, 2)
```

requires compatible initialization behavior.

Alternative constructors are ordinary named messages rather than overloads:

```js
Point.fromPolar(radius, angle)
Point.fromJson(data)
Point.origin()
```

Such messages may internally invoke the normal construction protocol.

Object-literal/prototype syntax remains distinct:

```js
Point {
    x: 10
    y: 20
}
```

directly creates an object whose parent is `Point` and evaluates the object body as slot definitions. It does not implicitly send `init`.

Core v0.1 does not define a combined object-construction form in which `Point(args) { ... }` means "construct and then evaluate this object body".

When the token sequence `Point(args) { ... }` is otherwise valid under trailing-closure syntax, the braces denote a parameterless trailing closure appended to the invocation's arguments: the form desugars as `Point(args, () => { ... })`. The braces do not become an object-construction body.
