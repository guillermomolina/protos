# Protos Callables v0.1

Language version: 0.1
Document revision: 331
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
