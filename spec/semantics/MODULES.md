# Protos Modules v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of module contexts, module identity, loading, caching, initialization, cycles, and module lifetime.

The material below is migrated without intended semantic change from `../PROTOS_LANGUAGE_SPEC.md`. Legacy section titles and numbering are retained so existing references remain understandable.

## Module Contexts and Top-Level Bindings

The language has no special semantic category of global variables.

Every module executes inside a `moduleContext`, which is an ordinary execution-context object. Bindings created at the top level of a module are local slots of that module context.

```js
version: "0.1"

printVersion: () => {
    print(version)
}
```

Conceptually:

```text
moduleContext
├── version
└── printVersion
```

Closures created during module execution capture the module context through the ordinary lexical-context mechanism. No separate global lookup or global assignment rule exists.

Like every execution context, a `moduleContext` delegates through the standard `Context` prototype to `Object` (see Execution Context).

Each module has its own module context. Modules do not implicitly share mutable global state.

Cross-module visibility is established explicitly: a module obtains another module's instance through `import(specifier)` and accesses its top-level bindings as slots of that module instance through ordinary member lookup. Core v0.1 introduces no dedicated import declaration syntax and no export declaration syntax or separate export mechanism. Module instance identity, caching, initialization, cycle, and failure semantics are defined in the section Module Loading, Identity, and Cycles; only host-specific module-specifier resolution remains outside Core v0.1.

Universal language facilities such as core prototypes and standard behavior may be supplied through a shared prelude or root lexical environment. Such facilities remain part of the ordinary context and lookup model rather than introducing a global-variable namespace.

The standard prelude is shared but **frozen**. Its slots may be read through ordinary lexical lookup, but unqualified `=` must never mutate a prelude slot. Attempting to modify a binding that resolves only to the frozen prelude signals an assignment error. A module that wants to shadow a prelude binding creates a new local slot with `:`.

```js
Object             // reads the prelude binding
Object = myObject   // ERROR: the prelude binding is frozen
Object: myObject    // OK: creates a module-local binding that shadows it
```

Freezing is shallow, so freezing the prelude is not by itself sufficient to make arbitrary objects referenced by its slots safe to share between Actors. The governing invariant is therefore:

> Any Protos object physically shared between Actors through the standard prelude must be semantically immutable for the duration of that sharing. Mutable Protos state reachable through standard facilities must be Actor-local.

The implementation may physically share immutable implementation artifacts — parsed syntax, bytecode, machine code, immutable metadata, and immutable constant data — where the sharing is semantically unobservable. Mutable standard-library or runtime state belongs to the Actor that uses it. This rule does not change the existing shallow-freeze semantics, does not introduce deep freeze, does not weaken Actor isolation, and does not require implementations to duplicate immutable data unnecessarily.

This preserves module isolation: modules may share immutable standard facilities, but they do not acquire shared mutable global state through the prelude.

Therefore, at module top level:

```js
x: value
```

creates `x` as a local slot of the current `moduleContext`.
## Module Loading, Identity, and Cycles

Each module executes inside a `moduleContext`, an ordinary execution-context object, as described in Module Contexts and Top-Level Bindings. The importable unit is the **module instance**. A module instance is an ordinary object, and in Core v0.1 it is the module's own `moduleContext` object:

- the module body executes with the module instance as its current execution context;
- a top-level binding created with `:` becomes a local slot of the module instance;
- `import(specifier)` yields the module instance;
- reading a member of a module instance therefore observes the module's top-level binding slots exactly as they exist at that moment.

There is no separate namespace object, wrapper, copy, or proxy. Module identity is ordinary object identity (`===`), and a module's observable surface is its current top-level slot state.

### Actor-Local Module Instances

A module instance belongs to one Actor. Each Actor owns an independent module cache.

An Actor is an isolated domain of mutable Protos state and execution. Core v0.1 assumes the Actor isolation principle:

> No shared mutable Protos memory exists between Actors.

The normative Actor concurrency model is defined in `../concurrency/ACTORS.md`. This section depends only on the isolation and ownership consequences stated here and introduces no Actor syntax.

Importing a module does not provide access to mutable module state belonging to another Actor. Conceptually:

```text
compiled/code representation of module foo
                 |
        +--------+--------+
        |                 |
     Actor A           Actor B
       |                 |
     foo@A             foo@B
       |                 |
 moduleContext A     moduleContext B
```

`foo@A` and `foo@B` are distinct module instances with distinct mutable state. There are no process-global mutable module instances. Because a module's `moduleContext` is Actor-local and is captured by closures through the ordinary lexical-context mechanism, closures created during module initialization do not create cross-Actor shared mutable lexical state; closure capture semantics are unchanged.

The runtime may physically share immutable implementation artifacts between Actors, including parsed syntax, bytecode, machine code, immutable metadata, and immutable constant data where otherwise semantically valid. Such sharing must not expose shared mutable Protos state. The observable `moduleContext` and mutable module state remain Actor-local.

### Canonical Module Identity

Module import uses three distinct concepts:

```text
module specifier
    value written by the program

ModuleKey
    canonical internal identity produced by the module resolver

module instance
    Actor-local module object returned by import
```

A module specifier is resolved relative to the importing module and the host/module-resolution environment. Resolution produces a canonical `ModuleKey`.

The exact external form of a `ModuleKey` is host-defined, but it must provide stable identity. Examples might include canonicalized file URIs, standard-library identifiers, or package identifiers. Two import requests that the resolver determines refer to the same module must produce the same `ModuleKey`.

`ModuleKey` is an internal loader/runtime concept. It is not required to be exposed as a normal language object.

The distinction is:

```text
specifier resolution / locating code
        may be host-defined

module identity / Actor-local instance / cache /
initialization / cycles / failure
        defined by Protos semantics
```

Once a canonical `ModuleKey` has been determined, the current Actor's module cache is consulted. Within one Actor:

```js
a: import("foo")
b: import("foo")
```

when both imports resolve to the same canonical `ModuleKey`, they refer to the same Actor-local module instance, so `a === b` holds. The module body is not executed again for the second import. Across Actors, the same canonical `ModuleKey` produces distinct Actor-local module instances with distinct mutable `moduleContext`s:

```text
Actor A: canonical foo -> foo@A
Actor B: canonical foo -> foo@B
```

### Cache Before Execution

When an Actor imports a canonical module that is absent from that Actor's module cache:

1. Create the module instance, creating its `moduleContext`.
2. Insert that module instance into the Actor-local module cache in state `INITIALIZING`.
3. Execute the module body in that `moduleContext`.
4. If initialization completes successfully, transition the module to `READY`.
5. If initialization fails, apply the failure semantics defined below.

The critical invariant is:

> **Cache before execute.**

The module must be discoverable through recursive imports before its body has finished executing. This prevents import recursion from repeatedly creating fresh instances.

Module initialization states conceptually include:

```text
INITIALIZING
READY
```

A module that is absent from the cache is not loaded in that Actor. A transient failure state may exist internally while a failed initialization is being handled, but a failed initialization must not remain in the cache as a successfully importable module. These states are semantic concepts and are not exposed through a public state-inspection or reflection API.

The same cache-before-execute invariant applies to an Actor's initial module when, at the start of that module's execution, it has an importable canonical identity. Before its body executes, the runtime resolves that canonical `ModuleKey` and inserts the module instance into the Actor-local module cache in state `INITIALIZING`; an importable initial module is not a mutable module instance that exists outside the cache. See The Initial Module of an Actor.

The Actor-local module cache is authoritative for the currently active module instance. Within one Actor, the cache maps canonical identity to the current active module record:

```text
ModuleKey -> ModuleRecord
```

with at most one record for a given `ModuleKey` at a time. A cache entry whose state is `INITIALIZING` or `READY` represents the active module instance for that key. Removing the entry after a failed initialization ends that instance's status as the active cached module instance. Object reachability and cache membership are distinct concepts: an object is not revoked or invalidated by the removal of its cache entry, and cache membership does not limit whether an otherwise reachable object can remain observable.

### Cyclic Imports

Cyclic module dependencies are valid. For example:

```text
A imports B
B imports A
```

must not recurse indefinitely creating new module instances. When B imports A while A is already `INITIALIZING`, B obtains the same Actor-local A module instance already present in the cache. Conceptually:

```text
import A
   |
   v
cache A as INITIALIZING
   |
   v
execute A
   |
   +--> import B
           |
           v
       cache B as INITIALIZING
           |
           v
       execute B
           |
           +--> import A
                   |
                   v
              return existing
              INITIALIZING A
```

A cycle is not rejected merely because it is cyclic.

### Partially Initialized Modules Are Observable

An import of a module already in state `INITIALIZING` returns that same module instance immediately; it does not wait for the module to become `READY`. The observable state is exactly the state of that module's `moduleContext` at that point in sequential execution.

For example, suppose module `a` executes:

```js
x: 10
b: import("./b")
y: 20
```

and module `b` executes:

```js
a: import("./a")
```

When `b` obtains `a` during the cycle:

- `a.x` exists and is readable, because `x: 10` has already executed.
- `a.y` does not yet exist, because `y: 20` has not executed.

Reading a slot that has not yet been created follows the ordinary Protos missing-slot / lookup error semantics. There is no module-specific temporal-dead-zone mechanism, no predeclaration of module slots, and no hoisting of future slot creations merely because the source text contains them later. Normal Protos slot semantics remain authoritative, and there is no special "uninitialized module binding" value.

> **A partially initialized module is the real module instance in its current state, not a placeholder copy.**

### Recursive Import Does Not Suspend

When a recursive import discovers an existing `INITIALIZING` module in the current Actor's cache, the import returns that module instance immediately. It must not suspend waiting for initialization to finish, because suspending would deadlock ordinary cyclic imports within the same Actor. No hidden Actor reentrancy or hidden suspension point is introduced for this case. Actor reentrancy remains identifiable only from explicit suspension operations.

### Successful Initialization

When execution of the module body completes normally, the cached module instance transitions:

```text
INITIALIZING -> READY
```

The same module instance and the same `moduleContext` remain cached. Subsequent imports in that Actor return that instance without re-executing the module body. No new module identity is created merely because initialization completed.

### Failed Initialization

If module initialization terminates with an unhandled error:

- the initiating `import()` fails with that error according to the normal error-propagation model;
- the failed module instance does not remain as a successfully cached module;
- the module's cache entry is removed for that failed initialization attempt;
- a later import may attempt initialization again and may create a fresh module instance.

A failed initialization attempt does not permanently poison the Actor's module cache. A failed partial module instance is not defined as reusable by a later independent import. If cyclic participants obtained a reference to the partially initialized instance before failure, no rollback of already-executed observable effects or object references is invented. Protos errors do not reverse effects that already occurred unless an existing rule explicitly says otherwise.

> Removing the failed module from the module cache does not time-travel or undo side effects already performed during its failed initialization.

A later import after cache removal creates a new initialization attempt and therefore a new module instance:

```text
import foo
    create foo#1
    cache foo#1 as INITIALIZING
    execute
    unhandled error
    remove foo#1 from cache
    import fails

later:

import foo
    create foo#2
    cache foo#2 as INITIALIZING
    ...
```

If a reference to a failed partial instance escaped during initialization, that object remains an ordinary, reachable Protos object after its cache entry is removed. It is the same object it was before failure: it is not revoked, not rolled back, and does not enter a hidden invalid-object state. It is only no longer the Actor's active cached instance for that canonical `ModuleKey`, and its continued existence does not prevent a later import from creating a fresh instance.

The failed instance and a later successful instance may therefore both remain reachable in the same Actor:

```text
foo#1
    state INITIALIZING
    a reference escapes during partial initialization
    initialization fails
    cache entry removed

later:

foo#2
    created by a new import
    becomes READY
```

with:

```text
foo#1 !== foo#2
```

Only `foo#2` is the current active cached module instance for that `ModuleKey`. This coexistence does not violate Actor isolation, because both objects belong to the same Actor. No tombstone, revocation, identity mutation, or hidden invalid-object state is introduced for a failed instance.

### Actor Lifetime and Module Lifetime

An Actor's module cache and its Actor-local module instances belong to that Actor's isolated runtime state. When the Actor dies, its module instances die with it unless some implementation artifact is independently retained for non-observable purposes. Another Actor does not inherit or take ownership of those mutable module instances. Creating a new Actor does not inherit the creator's module cache or live module contexts.

### The Initial Module of an Actor

The initial module executed by an Actor follows the same conceptual module-context model: it executes in a `moduleContext` that belongs to that Actor, and its module state is Actor-local rather than process-global. A new Actor does not inherit its creator's initial module context.

The Actor's initial module is executed directly by the Actor that owns it rather than obtained through `import()`. That direct execution does not place the initial module outside the module model when the initial module has an importable canonical identity.

Whether the Actor's initial entry is treated as an importable initial module or as a standalone non-importable entry point is determined when execution of that entry begins; the choice is not revisited while the instance exists. The importable path below always starts a module through the ordinary cached lifecycle and never adopts or re-registers an instance that was already executed as a standalone entry point. Standalone execution is described in Initial Modules Without an Importable Canonical Identity.

If the initial module can be resolved by `import()` to a canonical `ModuleKey`, then before executing its body the runtime:

1. determines or assigns that canonical `ModuleKey`;
2. creates the module instance and its `moduleContext`;
3. inserts that instance into the Actor-local module cache in state `INITIALIZING`;
4. executes the module body in that `moduleContext`;
5. transitions the instance to `READY` on successful initialization;
6. on failed initialization, removes that cache entry according to the failure semantics defined above.

The same cache-before-execute invariant that applies to imported modules therefore applies to an importable initial module, preventing creation of a second instance when another module imports the Actor's initial module during its initialization. For example:

```text
main imports b
b imports main
```

behaves conceptually as:

```text
Actor startup:

canonical main -> ModuleKey(main)

create main#1
cache ModuleKey(main) -> main#1 / INITIALIZING

execute main#1
    |
    +--> import b
            |
            v
        create b#1
        cache ModuleKey(b) -> b#1 / INITIALIZING
            |
            +--> import main
                    |
                    v
              return existing main#1
```

The recursive import must not create `main#2`.

If the Actor's initial module was registered in the cache because it has an importable canonical `ModuleKey`, and its initialization fails, the same failure rule applies as to any other failed module initialization: the cache entry is removed, no successful-looking cache entry is preserved, effects already performed are not rolled back, and the runtime does not invent an automatic retry as part of Actor startup. Whether failure of the Actor's initial module terminates that Actor or its Process remains governed by the existing Actor and root failure semantics, which this section does not redefine.

### Initial Modules Without an Importable Canonical Identity

Core v0.1 does not require the host to assign a fabricated filesystem or package identity to every possible host entry point. If, when execution of an Actor's initial entry begins, that entry is not importable through the host's module resolver and therefore has no canonical `ModuleKey` reachable by `import()`, it executes as a standalone entry point and remains outside the normal import lookup namespace. The instance created for that standalone execution:

- is Actor-local: it executes in a `moduleContext` belonging to the Actor that launched it, and that mutable `moduleContext` is not shared with another Actor;
- has no canonical `ModuleKey` and is not registered in the Actor-local module cache, so no `import()` can address it and it never aliases an imported module;
- is never retroactively assigned a canonical identity or adopted into the module cache, even if the host's resolution capabilities change after that execution began.

No fake registration under an invented import identity is required for such an entry point. Whether the Actor's initial entry is importable or standalone is determined when execution of that entry begins and is not recomputed later.

If the host later makes code equivalent to that standalone entry importable under a canonical `ModuleKey`, that does not change the identity or status of the standalone instance already created; the host may add resolution capability, but the standalone instance does not thereby acquire a `ModuleKey`. A subsequent `import()` that resolves to that canonical `ModuleKey` follows the ordinary Actor-local module-cache rules of the previous subsections: on a cache miss the runtime creates a new module instance, caches it as `INITIALIZING`, and executes its body through the lifecycle described in The Initial Module of an Actor. The standalone instance and that later cached instance are therefore distinct objects under `===`, and the module body and its side effects may execute again. This is not a double initialization of a single module instance, because the standalone instance never was the cached module instance for that `ModuleKey`. No retroactive cache registration, module-instance adoption, identity mutation, cache migration, source-code deduplication, or rollback of the standalone execution is introduced.

### `import()` and Bindings

Imports are eager by default. Lazy dependencies are expressed explicitly using ordinary language mechanisms such as closures rather than by implicit lazy-import semantics.

The module specifier is an ordinary expression. The language does not require import syntax to introduce names into the current lexical scope; an import operation simply yields the module instance, which the program can bind explicitly:

```js
math: import("math")
math.sin(x)
```

No ES-module-style static binding declarations, CommonJS `exports`, Python namespaces, or another language's module syntax are introduced. Core v0.1 defines no export declaration syntax and no separate export mechanism: a module's directly observable surface is its top-level binding slots, accessed through the module instance. Cross-module visibility remains explicit.

The exact resolver rules for files, packages, standard-library modules, search paths, and other host-specific sources are outside Core Language v0.1.

# Actor/module isolation integration migrated from the legacy concurrency ledger

## 34. Actor Module State

**CLOSED --- REVISED**

Each Actor owns its module state: an Actor-local module cache and the
module contexts (module instances) belonging to that Actor.

Actors do not inherit mutable module contexts from their creator.

If two Actors import the same module, mutable module-level state is
logically separate in each Actor.

The runtime may physically share immutable implementation artifacts such
as compiled code, immutable metadata, frozen core objects, or shared
prelude implementation, provided that the sharing is not observable as
shared mutable Protos state.

Per canonical module identity, an Actor has at most one active cached
module instance at a time; the Actor-local module cache is authoritative
for the currently active module instance. Cache membership and ordinary
object reachability are distinct. This is not a lifetime-wide "module
singleton" guarantee: an Actor is not limited to a single historical
object per canonical module identity. A module instance whose
initialization failed and whose cache entry was removed may remain
reachable through ordinary escaped references while a later fresh
instance is the Actor's active cached module instance for the same
canonical identity. Both objects belong to the same Actor, so their
coexistence does not violate Actor isolation.

The full module lifecycle rules (module instance equals its
`moduleContext`, Actor-local cache-before-execute, cache states,
cyclic-import and failure handling, and the initial module of an Actor)
are defined in the canonical module-lifecycle sections of
`MODULES.md` and the non-normative runtime integration model. This
section states only the Actor isolation and ownership consequences that
the concurrency model depends on.
## 34A. Module Implementation Sharing Is Semantically Invisible

**CLOSED**

Core v0.1 fully separates **module semantic state** from **module implementation
artifacts**.

For a given canonical module identity, each Actor's active module instance,
`moduleContext`, mutable slots, initialization state, and module-cache membership
remain Actor-local exactly as defined by the language/runtime module lifecycle.
No process-global mutable module instance exists.

An implementation may physically share artifacts that do not constitute mutable
Protos module state, including:

- parsed syntax or immutable syntax trees;
- bytecode or other executable intermediate representation;
- machine code/JIT code;
- immutable metadata;
- immutable constant data whose sharing is already semantically permitted;
- read-only loader/compiler/runtime bookkeeping whose identity is not exposed as
  a Protos value.

Such sharing is an implementation optimization only. Programs must not be able
to distinguish, through portable Core observations, whether two Actors execute
one physically shared code object or two physically duplicated ones.

In particular, implementation-artifact sharing must not cause Actors importing
the same canonical module to share:

- mutable module slots;
- lexical execution contexts;
- closure captures;
- mutable object identity created by module initialization;
- initialization progress/failure state;
- module-cache entries;
- dynamic handlers, return homes, Futures/tasks, resources, or Actor-local
  authority.

Likewise, compiling, caching, deduplicating, interning, unloading, recompiling,
or JIT-specializing implementation artifacts must not change the normative
module-instance identity or lifecycle observed by Protos code.

An implementation may choose per-Process, per-Node, or otherwise broader
physical caches for immutable artifacts, or choose no sharing at all. Cache
placement, eviction, code deduplication, compilation tiers, and artifact identity
are not Core semantic surfaces.

If a future facility exposes code identity, hot-update/version selection,
reflection over compiled artifacts, or implementation-level module handles, that
facility must define its own observable contract. It must not retroactively make
ordinary module implementation sharing visible.

This closes the former open ledger item `Module implementation sharing`; the
remaining module semantics are already fixed by the canonical Language and
Runtime module-lifecycle rules.

# Remaining concurrency/prelude integration migrated at revision 328

## 72. Standard Prelude Sharing

**CLOSED**

The standard prelude is shared between Actors and isolated P domains and is
frozen. Freezing is shallow, so freezing the prelude does not by itself make
arbitrary mutable objects referenced by its slots safe to share across isolation
domains.

Rule:

> Any Protos object physically shared across Actor/P isolation boundaries
> through the standard prelude must be semantically immutable for the duration
> of that sharing. Mutable Protos state reachable through standard facilities
> belongs to the isolation domain that uses it unless another normative rule
> explicitly provides a safe capability boundary.

Consequences:

-   The prelude itself may be physically shared, and its slots may refer
    to immutable Protos objects.
-   A prelude slot must not let two Actor/P isolation domains share mutable
    Protos state.
-   Mutable standard-library or runtime state — such as an Actor's module cache
    and module instances, or P-local mutable library state — belongs to the
    isolation domain that uses it.
-   The implementation may physically share immutable implementation
    artifacts such as parsed syntax, bytecode, machine code, immutable
    metadata, and immutable constant data where the sharing is
    semantically unobservable.

The existing rule that freeze is shallow is unchanged: no deep freeze is
introduced. Actor isolation is not weakened, and implementations are not
required to duplicate immutable data unnecessarily.
