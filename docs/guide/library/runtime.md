# Runtime metadata: `std:runtime`

`std:runtime` answers read-only facts about the Protos runtime your program is
running on: which implementation and version, which host Engine, and which
distribution platform.

```protos
Runtime: import("std:runtime")
metadata: Runtime.info()

metadata.implementation
metadata.implementationVersion
metadata.engineVersion
metadata.platform
```

The module is a single source,
[`protos/lib/runtime.protos`](../../../protos/lib/runtime.protos). Its normative
owner is §73 "Standard Runtime Metadata" in
[`spec/semantics/MODULES.md`](../../../spec/semantics/MODULES.md).

## `info()`

`Runtime.info()` is the module's only operation. It takes no arguments and
answers a snapshot object with exactly these String fields:

| Field | Value |
|---|---|
| `implementation` | Always `"Protos"` |
| `implementationVersion` | The distribution's version, the same text `protos --version` prints; `"development"` for an unpackaged build |
| `engineVersion` | The version of the host Engine running the current Context; treat its format as opaque |
| `platform` | `"NATIVE_IMAGE"` in the Native distribution, `"JVM"` on the portable JVM distribution |

Things to know:

- **The snapshot is frozen.** Adding or assigning a slot on it signals an
  Error. It is an ordinary Object whose parent is `Object`.
- **Every call creates a new object.** Two calls answer distinct snapshots;
  nothing is cached, and a later call never changes an earlier snapshot.
- **Values are read when you call `info()`**, not when you import the module.
- **`platform` is not a performance flag.** It tells the JVM distribution from
  the Native one; it says nothing about JIT compilation or optimization.
- **There is no execution mode.** Protos runs the same code with or without a
  debugger attached, so the snapshot has no mode field. Calling `info()` never
  starts a debugger.
- **Wrong arity signals an Error.** `Runtime.info(1)` signals a fresh ordinary
  Error, as does a call made when the runtime cannot supply its metadata.

## No extra authority

Importing `std:runtime` grants nothing. A snapshot holds only Strings: no host
objects, Engine or Context handles, Actors, Tasks, or diagnostic privileges.
