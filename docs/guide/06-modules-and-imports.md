# Modules and Imports

> **Status:** Non-normative programming guide
>
> **Normative owners:** `spec/semantics/MODULES.md`,
> `spec/semantics/EXECUTION_AND_CONTROL.md`, and `spec/PROTOS_GRAMMAR.md`

The previous chapters showed that Protos tries to reuse the same mechanisms
rather than creating parallel language subsystems. Modules follow that rule too.

The central idea is:

> One `import(String)` loads either a **SOURCE** (the entire source execution
> context) or a **MODULE** (a distinct public object). The resolver selects the
> target kind; the spelling of a prefix alone does not define it.

## SOURCE: its top-level context is importable

An explicitly authorized SOURCE file, such as `prices.protos`, executes in an
ordinary `moduleContext`. Its bare top-level bindings are slots of that context:

```protos
// prices.protos
base: 40
privateByConvention: 2
quote: () => { base + privateByConvention }
```

```protos
// main.protos, in the same directory
Prices: import("./prices.protos")
print(Prices.quote())
print(Prices.privateByConvention)
```

Running `bin/protos <path-to-main.protos>` returns the SOURCE's **complete**
context on import. Nothing named `privateByConvention` is automatically hidden;
SOURCE has no export boundary. Closures capture the SOURCE context through the
ordinary lexical rules. The actual runnable fixture is
[`04-source-full-context.protos`](../../protos/tutorials/08-language-interactions/04-source-full-context.protos)
with its [neighboring source](../../protos/tutorials/08-language-interactions/04-source-full-context/pricing.protos).

## MODULE: an explicit public object and private context

A logical MODULE is a directory with a **mandatory** `module.protos` initializer.
When a resolver selects that directory as MODULE, the initializer executes in a
private context (`context`) while its receiver (`this`) is a **different public
module object**. The public object delegates to ordinary `Object` and its own
slots are the published interface.

```protos
// Prices/module.protos — executed by a MODULE-aware host, such as protos run
Rules: import("./Rules.protos")
secretDiscount: 3
this.quote: (subtotal) => { subtotal + Rules.fee - secretDiscount }
this.boundaryDistinct: context !== this
```

```protos
// Prices/Rules.protos — ordinary SOURCE inside the same package
fee: 7
```

```protos
// Main/module.protos — MODULE entry in the same package
Prices: import("self:Prices")
result: Prices.quote(40)  // 44
```

`Rules` and `secretDiscount` are private **initializer** bindings. `this.quote`
and `this.boundaryDistinct` create public **own slots immediately**; there is no
`export` keyword, export table, implicit top-level publication, namespace proxy,
hoisting, or completion-time export copy. `context !== this` is true during
MODULE initialization. The initializer's final expression does not replace the
public object. An exported Closure captures the initializer's lexical state,
including private bindings, through normal capture. Publication does not copy
or reparent an exported object or Closure. Read a published prototype from the
module first (`Range: module.Range`), then call `Range(...)`; a direct
`module.Range(...)` is instead an ordinary message send to `module`.

The example above belongs to the runnable
[`06-package-exports` tutorial project](../../protos/tutorials/08-language-interactions/06-package-exports/package.toml)
and must be launched from **its project directory** with `protos run Main`,
not by running `Main/module.protos` as a standalone source.

## Visibility and lexical lookup

Inside the MODULE initializer, unqualified lookup searches its private
initializer context, then the public module object's **own slots**, then the
frozen prelude. The ordinary object delegation chain is **not** a lexical lookup
chain. Outside the MODULE, `module.quote` uses ordinary member lookup on the
public object. `module.secretDiscount` has no such public own slot, so absent
another ordinary matching delegated slot it signals `Error`. Do not mistake this
boundary for deep privacy: a public method can deliberately return a reference
to another object or Closure; reachability still follows ordinary object rules.
The public module object and its initializer context are two distinct objects,
not a new special `Module` class or a private namespace attached by magic.

## Ordinary import, no declarations

`import(specifier)` remains a normal eager call, not dedicated import grammar:

```protos
Prices: import("self:Prices")
```

The import result is an ordinary object, so `Prices.quote(40)` is ordinary
member access/invocation. The prefixes `std:`, `self:` and `dep:` are domain and
authority choices of the host resolver, **not** a rule that all prefixed imports
must be MODULEs or all local files must be SOURCEs. The resolved, authorized
**target kind** determines MODULE versus SOURCE. For this reason do not invent
an import declaration or copy JavaScript `export` syntax into Protos.

## Import accepts exactly a semantic String

The `specifier` argument to standard `import` must be a semantic String value.

This is valid:

```protos
import("std:collections/array")
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

## Direct-file execution supplies a local module namespace

The official direct-file and debug-file CLI hosts add one concrete resolver
domain without changing the Core meaning of `import`.

Given:

```text
app/
├── main.protos
├── helper.protos
└── lib/
    └── parser.protos
```

running:

```text
protos app/main.protos
```

allows explicit local module specifiers:

```protos
Helper: import("./helper.protos")
Parser: import("./lib/parser.protos")
```

`./` and `../` are interpreted relative to the **importing local source**, not
relative to the process working directory.

So if `lib/parser.protos` contains:

```protos
Helper: import("../helper.protos")
```

the import resolves inside the same selected local source tree.

The direct entry's containing directory is the initial local-source root. A
normalized import may move upward with `..` only while it remains inside that
tree:

```text
main.protos -> ./lib/parser.protos       allowed
lib/parser.protos -> ../helper.protos    allowed
main.protos -> ../outside.protos         Error
```

The initial direct-file policy is intentionally exact:

- `/` is the portable local-module separator;
- no process-CWD fallback exists;
- no `.protos` extension is appended implicitly;
- no directory/index or search-path probing occurs;
- component names must match exactly, including case;
- imported symbolic-link/reparse traversal is not followed;
- a local source-loading namespace does not grant a general guest `Filesystem`
  capability.

The direct entry itself has a canonical module identity before its first source
expression executes. Therefore an ordinary cycle:

```text
main -> helper -> main
```

returns the already-existing partially initialized `main` module instance rather
than executing the entry a second time.

`protos debug <file>` uses the same local module namespace and identity rules as
ordinary direct-file execution.

Locationless execution does not invent a filesystem base. `-e`, the REPL, stdin,
and generated/in-memory sources have no implicit `./` local namespace and do not
fall back to the current working directory.

This direct-file domain does **not** mean that every import is a relative path.
Other resolver domains remain distinct:

```protos
Std: import("std:collections/array")
```

Package execution continues to own `self:` and `dep:`. Absolute-file, `file:`,
network/URL, Git, wider/multiple source-root, and similar domains remain possible
future resolver additions rather than alternate meanings of `import`.

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

## Module instance identity belongs to the resolved target kind

Within one Actor, repeated imports of one canonical `ModuleKey` return the same
active object (`a === b`). The key includes the resolved target **kind**: the
MODULE public object and a SOURCE execution context never become the same
cached object even if the MODULE publishes a value read from that SOURCE.
Published values retain their **own** identities; for instance, `Ranges.Range`
can be identical to the frozen Prelude `Range` while `Ranges !== Ranges.Range`.
There is no wrapper or namespace proxy for either source-backed kind.

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

The Actor creates the result and caches it as `INITIALIZING` **before** executing
its source. For a SOURCE the result is its context; for a MODULE it is the
public object, with a separate private initializer context. Successful
initialization marks the **same object** `READY`, without a replacement or a
second export pass. This is why recursive imports can observe the current
partial result rather than loading it twice.

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

## A partially initialized import is the real instance

A recursive import returns the actual `INITIALIZING` instance immediately. If a
SOURCE has executed `x: 10` but not `y: 20`, the recursive importer can read
`x` but not `y`. For a MODULE only `this.x: 10` becomes visible; a bare `x: 10`
remains in the private initializer context. `this.y` does not exist until its
slot-creation expression runs. Reading an absent member follows ordinary
missing-slot `Error`, not a special temporal-dead-zone rule. There is no hidden
module-specific waiting or predeclaration.

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

The program entry executes in an Actor-local context. A standalone file
entry is SOURCE; a logical package entry selects a MODULE public object and
a separate initializer context.

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
standalone SOURCE context:

```text
Actor-local SOURCE moduleContext
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
Arrays: import("std:collections/array")
Sets: import("std:collections/set")
```

Those are useful current runnable spellings.

But the important conceptual boundary is:

```text
import(...)
    Core ordinary facility

"std:collections/array"
    String interpreted by the selected Standard Library resolver
```

The `std:` prefix is not a reserved language token and does not change
`import(...)` grammar.

Likewise, other environments may support their own resolver domains without
changing Core module lifecycle semantics.

## Packages: publishing logical MODULE directories

For package execution, the public CLI uses `protos run <entry>` from the **root
package directory**. The only manifest for a root or a workspace member is
`package.toml` (TOML 1.0, `manifest-version = 1`). An exact legacy
`protos.toml` entry is rejected, including alongside a valid `package.toml`.

```toml
manifest-version = 1

[package]
id = "root"
version = "1.0.0"

[exports]
Prices = "Prices"
```

`[exports]` maps external logical module names to **internal directories** with
`module.protos`: `Prices = "Prices"` means `Prices/module.protos`, **not**
`Prices.protos`, an object called `Prices`, or the SOURCE `Prices/Rules.protos`.
The package itself can use its private MODULE paths with `self:Prices` even if
not externally exported. Consumers of an authorized dependency use only
`dep:<alias>/<export>`; they cannot bypass its `[exports]` with a private MODULE
path or import its `.protos` SOURCE files. All module paths, case and entry
kinds are exact. Even within a package, explicit relative `./`/`../` `.protos`
imports require SOURCE authorization and must stay inside their owner; direct
SOURCE import of `module.protos` is forbidden.

`protos package manifest` validates the current directory's manifest without
resolving dependencies. `protos run <entry>` additionally performs package
preflight and requires an already consistent `protos.lock`. **The current CLI
cannot generate the lock**, nor install, update, fetch or publish packages.
`protos.project` keeps its filename when such project metadata is used.
The current content-identity method is `protos-package-tree-v2`; the methods
`protos-resolution-input-v1` and `protos-project-metadata-v1` are unchanged.
These are different inputs and should not be conflated.

The executable [package lesson](../../protos/tutorials/08-language-interactions/06-package-exports/package.toml),
[billing cookbook](../../protos/examples/modules/package-invoice/package.toml),
[Package Tool guide](tools/package-tool.md) and
[manifest schema v1](../design/PACKAGE_MANIFEST_SCHEMA_V1.md) cover the
concrete host mode, lock prerequisites and structural fields; there is no
package registry CLI today. The newest source-tree behavior should not be
assumed to be available in older published distributions.

## Host-supplied domains: `app:` and foreign providers

Two further resolver domains exist only when a host sets them up. Both reuse
the same `import(...)` and the same module lifecycle described above.

**`app:` application modules.** A Java host embedding Protos through a
standard Polyglot Context can install a catalog of in-memory modules:

```protos
Worker: import("app:worker")
```

The exact `app:` String is the canonical ModuleKey; there is no relative
lookup, alias, extension, or filesystem/classpath search, and supplying code
grants no authority. See
[Supplying application modules from Java](embedding/application-modules.md).

**Foreign providers.** A host can register providers that own an import scheme
and supply modules that are not Protos source:

```protos
Math: import("java:math")
```

The result is an Actor-local facade that follows the ordinary cache, cycle,
failure, and retry rules; the foreign object never becomes the module
identity. Which schemes exist depends entirely on the providers the host
registered. See the [interoperability map](interop/README.md) for what is
available today.

## Practical rules to remember

1. `import` is one ordinary eager call whose exact semantic String is passed to the resolver.
2. A SOURCE returns its complete top-level execution context, not a filtered export.
3. A MODULE requires a directory with `module.protos`; it returns its separate public object.
4. Bare initializer slots stay private; `this.Name: value` publishes an own slot immediately.
5. `context` and `this` differ in a MODULE initializer; Closures retain normal lexical capture.
6. The MODULE object, a published prototype, and an imported SOURCE context have distinct identities.
7. Same canonical key and Actor gives the same active instance; different Actors have separate mutable state.
8. Cycles observe only slots already created; failure evicts the active cache entry for a possible retry.
9. Module publication provides no deep privacy, no `export` declaration, and no special binding table.
10. Package `[exports]` names public MODULE directories; `dep:` cannot access private SOURCEs.
11. `package.toml` replaces `protos.toml` with no fallback; `protos.lock` is still required for `run`.

## Current runnable examples

- The [existing direct-file example](../../protos/examples/modules/direct-file-import/main.protos)
  and [SOURCE lesson](../../protos/tutorials/08-language-interactions/04-source-full-context.protos)
  run with `bin/protos <file>` from the product root. They return complete SOURCE contexts.
- The [MODULE identity lesson](../../protos/tutorials/08-language-interactions/05-public-module-object.protos)
  runs with the same direct-file host but imports the actual `std:collections/range`
  public MODULE, including its separately published frozen Range values.
- The [package-exports lesson](../../protos/tutorials/08-language-interactions/06-package-exports/package.toml),
  [pricing cookbook](../../protos/examples/modules/public-module-pricing/package.toml)
  and [workspace billing cookbook](../../protos/examples/modules/package-invoice/package.toml)
  each contain `package.toml`, `protos.lock` and real `module.protos` entries.
  Run `protos package manifest` and `protos run Main` **from each project's own
  directory** using the current source-HEAD build. Do not invoke their
  `module.protos` entry as a standalone file: that would select SOURCE semantics
  and would not provide the package resolver.
- The [Package Tool source](../../protos/tools/package/WorkspacePlan.protos) uses
  direct internal SOURCE imports for planning; this is not a public package
  install/lock command.

The [runtime tests](../../src/test/java/com/guillermomolina/protos/execution/modules/ProtosModuleRuntimeTest.java)
cover Actor caching, recursive imports, failed initialization and retry.
The [DOC020 learning integration tests](../../src/test/java/com/guillermomolina/protos/cli/ProtosDoc020LearningMaterialsTest.java)
exercise package entries and selected expected failures. The normative
specification remains authoritative.

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
