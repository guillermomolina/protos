# Supplying application modules from Java (`app:`)

> **Status:** Non-normative HOWTO
>
> **Normative owners:** `spec/semantics/MODULES.md`,
> `spec/concurrency/ACTORS.md` (§8 `Actor.spawn`), and `spec/io/PROCESS_IO.md`
> (Standard Polyglot embedding bootstrap and authority)

## Purpose

A Java host often needs to ship Protos modules of its own: code that the main
evaluation imports, or that a spawned Actor runs. In a standard Polyglot
Context there is no filesystem or classpath search for such modules. The host
supplies them explicitly through:

```java
com.guillermomolina.protos.execution.ProtosEmbeddedModules
        .install(Context context, Map<String, String> modules)
```

Each entry maps an exact `app:` specifier to the Protos source characters of
that module. The Protos program then reaches it through ordinary
`import("app:...")` or `Actor.spawn("app:...", ...)`.

This page extends [Embedding Protos in a Java application](java.md); read that
first for the Context lifecycle, class path, and authority model.

## Availability

| Capability | State |
|---|---|
| `ProtosEmbeddedModules.install` with `import("app:...")` | Implemented and accepted (I087-2 / PLAT055) on the portable JVM runtime |
| `Actor.spawn("app:...", ...)` | Implemented and accepted (same slice) |
| Filesystem/classpath lookup, relative `app:` imports, aliases | Not available by design |
| Native Image embedding hosts / Native CLI | Not supported |
| `app:` in the CLI, Test Tool, or other Protos-owned drivers | Not available: installation is rejected on driver-owned Contexts |

Verified scope: this repository's `0.3.312-SNAPSHOT` development revision; no
release containing it is implied.

## Example

```java
import com.guillermomolina.protos.execution.ProtosEmbeddedModules;
import java.util.Map;
import org.graalvm.polyglot.Context;

public final class AppModules {
    private static final String WORKER = """
            greeting: "hello"
            start: (initial) => {
                count: initial
                {
                    increment: () => {
                        count = count + 1
                        count
                    }
                }
            }
            """;

    public static void main(String[] args) {
        try (Context context =
                Context.newBuilder("protos").allowCreateThread(true).build()) {
            // 1. Install once, after build and before the first evaluation.
            ProtosEmbeddedModules.install(context, Map.of("app:worker", WORKER));

            // 2. Ordinary import.
            String greeting =
                    context.eval("protos", "import(\"app:worker\").greeting").asString();

            // 3. The same module as the initial module of a new Actor.
            int count = context.eval("protos",
                            "w: Actor.spawn(\"app:worker\", \"start\", 41)\n"
                                    + "w.request(\"increment\").value()")
                    .asInt();

            System.out.println(greeting + " " + count);
        }
    }
}
```

Build and run it with the class path from [the embedding page](java.md#minimal-example).
Expected output: `hello 42`.

This is the same scenario that the portable smoke runs from an external
application against the distributed JAR (`application-modules` mode of
[`dist/Plat054EmbeddingProbe.java`](../../../dist/Plat054EmbeddingProbe.java)).

`allowCreateThread(true)` is needed only for the `Actor.spawn` part; plain
imports work without it.

## Rules of the catalog

**Exact specifiers.** A key must be `app:` followed by a non-empty name. The
key is used verbatim as the canonical ModuleKey: no normalization, no extension
inference, no case folding, no relative resolution. `import("app:worker")`
finds the entry; `import("app:worker.protos")`, `import("worker")`, and
`import("APP:worker")` do not.

**Ordinary module semantics.** Catalog modules follow
[chapter 6](../06-modules-and-imports.md) unchanged:

- within one Actor, every `import("app:x")` returns the same cached instance
  and the body runs once;
- two entries with identical source are still two modules
  (`import("app:a") !== import("app:b")`);
- cyclic imports observe the real, partially initialized module;
- a failed initialization is an ordinary Protos Error, the cache entry is
  removed, and a later import retries with a fresh instance;
- each Actor has its own instances: a spawned Actor initializes its own copy
  of `app:worker`, and the RootActor's copy is independent of it.

**Lazy loading.** A module is compiled only when first imported. An entry with
a syntax error has no effect until something imports it.

**Host evaluations never become catalog modules.** Calling
`context.eval(Source.newBuilder("protos", code, "app:worker").build())` does
not evaluate or replace the catalog module named `app:worker`: Source names,
URIs, and content never create a ModuleKey. Every host evaluation stays a
standalone entry.

**Other domains are unaffected.** `std:` modules still resolve from the
Standard Library alongside the catalog. A specifier that is not in the catalog,
including an unknown `app:` one, fails as an ordinary resolution Error.

## Installation lifecycle

Install **exactly once per Context**, **after** building it and **before** its
first evaluation:

```text
Context.newBuilder("protos").build()
    context.initialize("protos")      allowed before install
    context.getBindings("protos")     allowed before install
    context.parse("protos", ...)      allowed before install
ProtosEmbeddedModules.install(...)    once
context.eval(...)                     first evaluation starts the Process
```

- The `modules` map is validated and copied. Later changes to your map have no
  effect, and an invalid entry rejects the whole installation: nothing is
  published.
- An empty map is a valid installation (and still counts as the one
  installation).
- Installation does not create the Process.
- Installation and the first evaluation are atomic with respect to each other:
  if they race on two threads, either the bootstrap sees the whole catalog or
  `install` fails with `IllegalStateException`.
- Each Context has its own catalog, even when several Contexts share one
  `Engine`. A Context without a catalog cannot import another Context's
  `app:` modules.

## Errors from `install`

| Exception | When |
|---|---|
| `NullPointerException` | `context`, `modules`, a key, or a source is `null` |
| `IllegalArgumentException` | A key is not `app:` + non-empty name (for example `app:`, `std:...`, `java:x`, ` app:x`), or the Context does not support Protos |
| `IllegalStateException` | The Context is closed, already has a catalog, has started (or failed) its Process bootstrap, or is owned by a Protos host driver |

Failures *inside* Protos (unknown specifier, failing module body) are ordinary
Protos Errors seen by the guest, not Java exceptions from `install`. If such an
Error escapes a host entry unhandled, it terminates the Process as described in
[the embedding page](java.md#common-mistakes).

## Authority

Supplying code grants no authority. A catalog module:

- does not receive `process`, `filesystem`, or `network` slots; only the
  initial module of the first host evaluation receives the bootstrap-local
  slots;
- cannot reach Filesystem, Network, host access, or thread creation beyond
  what the Context itself allows;
- receives capabilities only when Protos code passes them explicitly, for
  example as an argument to a function the module exports.

## References

Implementation and evidence:

- [`ProtosEmbeddedModules.java`](../../../src/main/java/com/guillermomolina/protos/execution/ProtosEmbeddedModules.java)
  (public API and Javadoc);
- [`ProtosApplicationModuleResolver.java`](../../../src/main/java/com/guillermomolina/protos/execution/ProtosApplicationModuleResolver.java)
  (exact catalog lookup in front of the Standard Library resolver);
- [`ProtosEmbeddedModulesTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/ProtosEmbeddedModulesTest.java)
  (identity, cycles, retry, Actor-local instances, installation order,
  atomicity, shared Engines, authority);
- [`dist/Plat054EmbeddingProbe.java`](../../../dist/Plat054EmbeddingProbe.java)
  (external application, `application-modules` mode).

Normative specification:

- [`spec/semantics/MODULES.md`](../../../spec/semantics/MODULES.md) for
  ModuleKey identity, Actor-local caching, cycles, failure, and host-initiated
  evaluations;
- [`spec/concurrency/ACTORS.md`](../../../spec/concurrency/ACTORS.md) §8 for
  `Actor.spawn` and the initial module of an Actor;
- [`spec/io/PROCESS_IO.md`](../../../spec/io/PROCESS_IO.md) for embedding
  bootstrap and authority.

The `app:` spelling itself is host resolver policy of this embedding API, not a
Core language institution (see
[chapter 6](../06-modules-and-imports.md#import-accepts-exactly-a-semantic-string)).
