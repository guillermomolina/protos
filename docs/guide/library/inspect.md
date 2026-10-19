# Runtime inspection: `std:inspect`

`std:inspect` gives explicit, implementation-specific diagnostics about how the
running Protos implementation represents a value or a Closure: its
representation family, what kind of Closure it is, its physical Bytecode, and its
physical Truffle node tree. It exists for implementers, tool authors, and
curious users investigating the runtime. It is not a reflection API.

```protos
Inspect: import("std:inspect")

square: (x) => x * x

Inspect.representation(square)
Inspect.callableInfo(square)
Inspect.bytecode(square)
Inspect.truffleTree(square)
```

This example works only when the host has already enabled `protos.Inspect=true`
for the Context (see below). Each line answers a String.

The module is a single source,
[`protos/lib/inspect.protos`](../../../protos/lib/inspect.protos). Its normative
owner is §73A "Standard Runtime Inspection" in
[`spec/semantics/MODULES.md`](../../../spec/semantics/MODULES.md).

## Enabling inspection

Inspection is disabled by default. The host enables it for one Context with the
Boolean Context option `protos.Inspect`:

```java
Context context = Context.newBuilder("protos")
        .option("protos.Inspect", "true")
        .build();
```

- Without the option, or with `"false"`, `import("std:inspect")` signals an
  ordinary `Error`. Guest code cannot grant itself the authority.
- The option is fixed for the life of the Context. Two Contexts of the same
  Engine can have different settings.
- `protos.Inspect` is independent of debugging. Starting a debugger does not
  enable inspection, and enabling inspection does not start a debugger.
  Inspecting never changes how the Context runs your code.
- Each operation checks the authority of the Context it runs in, so keeping a
  reference to one of its functions grants nothing in another Context.

There is no command-line flag for this option.

## Operations

Each operation takes exactly one argument and answers a non-empty String. The
text is diagnostic: its format is not a stable API, may change in any release,
and must not be parsed by programs.

| Operation | Accepts | Describes |
|---|---|---|
| `representation(value)` | Any value | The value's representation family (for example `Integer`, `String`, `Array`, `Closure (source-backed)`, `Object (ordinary)`, a runtime capability, or an opaque foreign value). It never shows contents: no slots, elements, String text, captures, or handles. |
| `callableInfo(closure)` | A Closure | Whether the Closure has a native body or a source definition. For a source-backed Closure: its declared parameter count, the source span of its definition, the source name when a plan is prepared, and whether that plan is prepared or deferred. |
| `bytecode(closure)` | A source-backed Closure | The real Bytecode DSL instructions of the Closure's physical activation root in this Context: bytecode index, instruction name, and the names and kinds of its arguments. Constant operand values are never printed. |
| `truffleTree(closure)` | A source-backed Closure | The compact physical Truffle node tree of that same root. |

None of the operations calls the inspected Closure. A Closure that has never run
can still be inspected: if its executable plan has not been prepared yet, the
request prepares it on demand. Programs that never import `std:inspect` pay
nothing for its existence, but an individual inspection can be expensive.

### Native and source-backed Closures

Closures provided by the runtime itself have a native body. `callableInfo`
reports them as native Closures. They have no Protos source definition, so
`bytecode` and `truffleTree` signal an ordinary `Error` for them instead of
inventing a representation.

### Bytecode, Truffle tree, and compiler IR

These are three different layers, and `std:inspect` shows only the first two:

- **Bytecode** is the instruction sequence the Protos Bytecode DSL interpreter
  executes for the Closure in this Context.
- **The Truffle tree** is the physical node tree of that same executable root.
  It is not the semantic syntax tree of your source.
- **Graal compiler IR** is the optimizing compiler's internal graph. No operation
  shows it, and none reports whether code is compiled, optimized, or JIT-ready.

Very large output is cut at a fixed limit and marked as truncated.

## Errors

Every failure is an ordinary `Error`, and each one is a new object:

- importing the module, or calling an operation, without the `protos.Inspect`
  authority;
- calling an operation with zero or more than one argument;
- passing a non-Closure to `callableInfo`, `bytecode`, or `truffleTree`;
- asking for `bytecode` or `truffleTree` of a native Closure, or when no source
  or physical representation is available in this Context;
- a failure to prepare the Closure's executable plan on demand.

An operation never answers an empty String in place of an error.

## Native and JVM distributions

The public contract of `std:inspect` is the same in the portable JVM
distribution and the Native executable: the same four operations, the same
authority, and the same Error behavior. What they answer is not:

- The diagnostic text is specific to the running implementation and can differ
  between the distributions and between releases.
- A physical representation may be unavailable on a given runtime. When the
  requested representation is unavailable, the operation signals a fresh
  ordinary `Error`; it never answers an empty or substitute String.
- Nothing guarantees equivalent instructions, equivalent trees, or the same
  availability of physical representations across JVM and Native Image, and no
  output indicates JIT status.

Use `std:runtime` when you need portable facts about the runtime.
