# Modules and Imports

> **Status:** Non-normative programming guide
>
> **Normative owners:** `spec/semantics/MODULES.md`,
> `spec/semantics/EXECUTION_AND_CONTROL.md`, and `spec/PROTOS_GRAMMAR.md`

The previous chapters showed that Protos tries to reuse the same mechanisms
rather than creating parallel language subsystems. Modules follow that rule too.

The central idea is:

> A module is an ordinary Actor-local execution context with a canonical loader
> identity, and `import(...)` is an ordinary eager operation that returns that
> context object.

There is no separate global-variable universe, namespace wrapper, static import
declaration, export table, or second object model for modules.

## Top-level bindings are module-context slots

Every module executes inside a `moduleContext`.

A top-level binding:

```protos
version: "0.1"

describe: () => {
    "Protos " + version
}
```

creates local slots on that module context:

```text
moduleContext
├── version
└── describe
```

This is the same slot mechanism used by other execution contexts.

Closures created while the module executes capture the module context through
ordinary lexical capture. There is no separate rule for "global" variables.

## Each module gets its own context

Two different modules do not implicitly share mutable module-level state.

If module `counter` contains:

```protos
value: 0

next: () => {
    value = value + 1
    value
}
```

then `value` belongs to that module instance's own context.

Another module can observe the state only by obtaining that module instance and
accessing its slots.

## `import(...)` is an ordinary call

Protos does not add dedicated import-declaration grammar.

You write:

```protos
math: import("some-module")
```

and `import(...)` returns a module instance.

That result is just another Protos object value from the caller's point of view,
so cross-module access uses ordinary member lookup:

```protos
math.sin(x)
math.constants
```

There is no syntax such as:

```text
import x from "..."
export x
```

in Core v0.1.

## There is no separate export mechanism

A module instance is the module's own `moduleContext`.

Therefore a top-level binding is directly observable as a slot of the imported
module instance:

```protos
settings: {
    mode: "safe"
}

run: () => {
    ...
}
```

After importing that module:

```protos
m: import("...")
m.settings
m.run()
```

uses ordinary slot/member lookup.

Core does not build a second export table, namespace object, proxy, or copy.

This makes module boundaries explicit but also means that a top-level helper
binding is not automatically private merely because the programmer did not
write an export declaration: no such declaration exists.

## Keep implementation-private state below the module surface

If state should not be a top-level module slot, put it inside another lexical
scope and expose only the intended object or Closure.

For example:

```protos
counter: (() => {
    value: 0

    ({
        next: () => {
            value = value + 1
            value
        }
    })
})()
```

Here `counter` is a module-level slot, while `value` belongs to the invoked
Closure's lexical context and is reached by the returned `next` Closure through
ordinary capture.

The useful mental rule is:

> Top-level means module-visible. Lexically nested state remains governed by
> ordinary lexical reachability.

## Import accepts exactly a semantic String

The `specifier` argument to standard `import` must be a semantic String value.

This is valid:

```protos
import("std:collections/Array")
```

An arbitrary object does not become a module specifier merely because it looks
String-like or defines conversion behavior.

Core performs no implicit:

```text
toString
formatting
path conversion
URI conversion
invocation
coercion
```

before module resolution.

Invalid specifier values signal Error before the host resolver is invoked.

## The exact String goes to the resolver

Core deliberately does not assign intrinsic path or package meaning to the
specifier text.

The String:

```protos
"./model"
```

is just an exact String at the Core boundary.

Core does not itself:

- append a source extension;
- normalize path separators;
- resolve `.` or `..`;
- case-fold;
- absolutize;
- treat it as a URL;
- treat it as a package;
- treat it as a Standard Library name.

Those are resolver/host policy decisions.

This separation is important because the same Core module lifecycle can work
with filesystem modules, bundled Standard Library modules, package modules, or
other host-defined sources without changing the language model.

## Specifier, ModuleKey, and module instance are different concepts

Module loading has three distinct layers:

```text
specifier String
    |
    | host/module resolution
    v
canonical ModuleKey
    |
    | Actor-local cache
    v
module instance
```

The **specifier** is the exact String supplied by the program.

The **ModuleKey** is the canonical identity produced by the resolver.

The **module instance** is the Actor-local Protos object returned by `import`.

Keeping these concepts separate explains several otherwise surprising results.

## Different spellings may identify the same module

A resolver may decide that two specifier Strings refer to one canonical module.

Conceptually:

```protos
a: import("a")
b: import("alias-a")
```

can satisfy:

```protos
a === b
```

when both specifiers resolve to the same canonical `ModuleKey` in the same
Actor.

The cache is keyed by the canonical identity, not by the original spelling.

So "same import String" is not the definition of module identity. The resolved
`ModuleKey` is.

## A module instance is an ordinary identity-bearing object

The module instance is the actual module context object.

Within one Actor, repeated imports of the same canonical `ModuleKey` return the
same active cached module instance:

```protos
a: import("foo")
b: import("foo")

a === b
```

is true while they refer to that same active cached instance.

There is no wrapper object whose identity differs from the module context.

This also means that ordinary member reads observe the module's actual current
slot state.

## Module instances are Actor-local

The cache belongs to an Actor.

Conceptually:

```text
canonical foo
    |
    +------ Actor A ------> foo@A
    |
    +------ Actor B ------> foo@B
```

`foo@A` and `foo@B` are different mutable module instances.

An Actor does not inherit another Actor's mutable module cache or module
contexts.

An implementation may share immutable compiled artifacts internally, but that
must not become shared mutable Protos module state.

This is the module-level consequence of Actor isolation.

## Import is eager by default

Calling:

```protos
m: import("foo")
```

loads/initializes the module when required before the call completes normally.

Core does not make dependency loading implicitly lazy.

If lazy dependency acquisition is desired, use an ordinary language mechanism,
for example a Closure:

```protos
loadFoo: () => {
    import("foo")
}
```

The laziness then comes from when `loadFoo` is invoked, not from a special
lazy-import rule.

## Cache before execute

When a canonical module is absent from an Actor's cache, the conceptual order is:

```text
1. create module instance / moduleContext
2. cache it as INITIALIZING
3. execute the module body
4. mark it READY on success
```

The important invariant is:

> The new module is cached before its body executes.

This is what makes cyclic imports possible without recursively creating an
unbounded sequence of new module objects.

## Cyclic imports are valid

Suppose A imports B and B imports A:

```text
A -> B -> A
```

The sequence is conceptually:

```text
import A
    create A
    cache A / INITIALIZING
    execute A
        import B
            create B
            cache B / INITIALIZING
            execute B
                import A
                    return cached A
```

The second import of A returns the already-existing `INITIALIZING` A instance.

It does not create A again.

## A partially initialized module is the real module

During a cycle, another module can observe a module before its body has finished.

If A executes:

```protos
x: 10
b: import("b")
y: 20
```

and B imports A during that `import("b")`, B receives the real current A module
instance.

At that moment:

```text
A.x    exists
A.y    does not exist yet
```

There is no module-specific temporal dead zone, no predeclared future slot, and
no hidden "uninitialized binding" value.

Trying to read a not-yet-created slot follows the ordinary missing-slot Error
rules.

## Recursive import does not wait for READY

When a cycle finds an `INITIALIZING` instance, `import` returns it immediately.

It does not secretly wait for the module to become `READY`, because ordinary
same-Actor cyclic initialization could deadlock if each side waited for the
other to finish.

Core module resolution and recursive import do not introduce an implicit
suspension/reentrancy point.

Explicit suspension facilities remain explicit.

## Successful initialization keeps the same identity

When the module body completes normally:

```text
INITIALIZING -> READY
```

The object is not replaced.

The same module instance remains cached, so later imports return it without
re-executing the body.

Initialization state changes; semantic object identity does not.

## Failed initialization is removed from the active cache

If initialization terminates with an unhandled Error:

```text
create foo#1
cache foo#1 / INITIALIZING
execute foo#1
    Error
remove foo#1 from cache
import fails
```

The failed attempt does not remain as the successfully importable active module.

A later import may retry:

```text
create foo#2
cache foo#2 / INITIALIZING
...
READY
```

and `foo#2` is a fresh module instance.

This prevents one failed attempt from permanently poisoning the Actor's module
cache.

## Failure does not roll back completed effects

Removing a failed module from the cache does not undo time.

If initialization already changed ordinary reachable state or exposed a
reference before failing, those completed effects are not automatically rolled
back.

An escaped reference to the failed partial instance remains an ordinary object.

Therefore it is possible for:

```text
foo#1    failed partial instance, still reachable
foo#2    later active READY instance
```

to coexist in one Actor, with:

```text
foo#1 !== foo#2
```

Only `foo#2` is the active cached instance for that `ModuleKey`.

## Import failure maps to Protos Error

Resolver failures and source-loading/initialization failures cross the standard
language boundary as Protos Error behavior.

Portable code should not depend on raw host exceptions, filesystem exception
classes, HTTP status values, or similar host-specific failure objects unless a
separate normative host/domain API explicitly exposes them.

This keeps the module mechanism portable even when resolver implementations are
host-specific.

## The initial module follows the same context model

The program entry module also executes in an Actor-local module context.

When that initial module has an importable canonical identity, it participates
in the same cache-before-execute lifecycle before its first source expression
runs.

That matters for cycles such as:

```text
main imports b
b imports main
```

because `b` must obtain the already-existing initial `main` instance rather than
causing a second `main` instance to be created.

## A standalone entry may have no ModuleKey

Core does not require every possible host-launched source to be assigned an
invented import identity.

An initial entry that is not importable through the resolver may execute as a
standalone module context:

```text
Actor-local moduleContext
no ModuleKey
not in import cache
```

It is not later retroactively adopted into the module cache.

If equivalent code later becomes importable under a canonical key, a subsequent
import creates the ordinary cached module instance; that new instance is
distinct from the earlier standalone entry object.

## Prelude facilities are shared only under immutable semantics

Universal language facilities may be supplied through the standard prelude.

The prelude is frozen. A module can read a prelude binding:

```protos
Object
Array
import
```

but ordinary unqualified assignment must not mutate a binding found only in the
frozen prelude.

A module can deliberately shadow a prelude name by creating a module-local slot:

```protos
Array: myArrayFactory
```

This follows the ordinary binding rules from chapter 1.

It is not a special module override mechanism.

## `std:` is resolver policy, not Core import syntax

The current Protos distribution supplies Standard Library modules through
specifier spellings such as:

```protos
Arrays: import("std:collections/Array")
Sets: import("std:collections/Set")
```

Those are useful current runnable spellings.

But the important conceptual boundary is:

```text
import(...)
    Core ordinary facility

"std:collections/Array"
    String interpreted by the selected Standard Library resolver
```

The `std:` prefix is not a reserved language token and does not change
`import(...)` grammar.

Likewise, other environments may support their own resolver domains without
changing Core module lifecycle semantics.

## Package resolver spellings remain a resolver concern

Current package-tool work uses resolver domains such as `self:` for package
workspace modules.

For example, package-tool source contains calls such as:

```protos
MetadataPublication: import("self:MetadataPublication")
```

That demonstrates the same Core operation with a different resolver policy.

Do not infer from such spellings that Core v0.1 defines package syntax,
filesystem semantics, or an `import self` language construct.

Package acquisition, manifests, dependency identities, and the bundled
toolchain belong to their own project/library layer and are covered separately.

## Practical rules to remember

1. Treat module top-level bindings as slots of one ordinary `moduleContext`.
2. There is no separate global-variable category.
3. `import(specifier)` is an ordinary call and eagerly returns a module instance.
4. The specifier must be exactly a semantic String; Core does not coerce it.
5. Separate specifier spelling, canonical `ModuleKey`, and module-instance
   identity in your mental model.
6. Same canonical key plus same Actor means the same active cached instance.
7. The same canonical key in different Actors means different mutable module
   instances.
8. There is no dedicated export table: top-level module slots are directly
   observable.
9. Cache-before-execute makes cycles legal and exposes the real partial module
   state reached so far.
10. A missing not-yet-created cyclic slot is an ordinary lookup Error, not a
    special uninitialized value.
11. Failed initialization removes the active cache entry and a later retry may
    create a fresh identity.
12. Failure does not roll back already-completed effects or revoke escaped
    references.
13. Treat `std:`, `self:`, filesystem spellings, and package spellings as
    resolver policy unless a relevant domain specification says otherwise.
14. Use lexical nesting when implementation state should not be a top-level
    module slot.

## Current runnable examples

The repository already contains real imports using multiple resolver domains.

Standard Library import:

- [`../../protos/benchmarks/collections/array-map.protos`](../../protos/benchmarks/collections/array-map.protos)
  imports `std:collections/Array` and invokes the returned module's ordinary
  `map` slot.

Package-workspace import:

- [`../../protos/tools/package/Main.protos`](../../protos/tools/package/Main.protos)
  imports package-tool modules such as `self:MetadataPublication` and
  `self:BootstrapMessage`.

The Standard Library resolver is implementation/distribution evidence for the
current `std:` policy:

- [`../../src/main/java/com/guillermomolina/protos/execution/ProtosStandardLibraryModuleResolver.java`](../../src/main/java/com/guillermomolina/protos/execution/ProtosStandardLibraryModuleResolver.java).

The module runtime tests exercise the Core lifecycle directly:

- [`../../src/test/java/com/guillermomolina/protos/execution/ProtosModuleRuntimeTest.java`](../../src/test/java/com/guillermomolina/protos/execution/ProtosModuleRuntimeTest.java)
  covers semantic-String validation, canonical-key aliasing, cache-before-execute,
  cycles, Actor-local caches, failed-initialization eviction, and retry.

These links demonstrate current behavior. The normative specification remains
authoritative.

## Normative references

For exact behavior, consult:

- [`../../spec/semantics/MODULES.md`](../../spec/semantics/MODULES.md) for module
  contexts, `import`, specifier validation, canonical module identity,
  Actor-local caching, initialization, cycles, failure, and initial modules;
- [`../../spec/semantics/EXECUTION_AND_CONTROL.md`](../../spec/semantics/EXECUTION_AND_CONTROL.md)
  for execution contexts and lexical behavior used by module contexts;
- [`../../spec/PROTOS_GRAMMAR.md`](../../spec/PROTOS_GRAMMAR.md) for the absence
  of dedicated import/export declarations and the ordinary expression/call
  syntax used by `import`;
- [`01-bindings-contexts-and-state.md`](01-bindings-contexts-and-state.md) for the
  binding and execution-context model reused at module top level;
- [`03-closures-methods-and-receivers.md`](03-closures-methods-and-receivers.md)
  for lexical capture of module state;
- [`05-values-identity-equality-and-collections.md`](05-values-identity-equality-and-collections.md)
  for semantic identity and `===`, which module-instance identity uses.

Those documents define the language. This chapter supplies the programmer-facing
model for using modules without importing assumptions from another language's
module system.
