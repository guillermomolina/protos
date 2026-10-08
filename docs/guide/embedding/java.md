# Embedding Protos in a Java application

> **Status:** Non-normative HOWTO
>
> **Normative owners:** `spec/io/PROCESS_IO.md` (Standard Polyglot embedding
> bootstrap and authority) and `spec/semantics/MODULES.md` (Host-initiated
> evaluations in an embedded RootActor)

## Purpose

Use this page when a Java application needs to evaluate Protos source and call
Protos Closures through the standard GraalVM Polyglot API
(`org.graalvm.polyglot.Context`). No Protos-specific bootstrap class is needed
for ordinary evaluation, binding reads, and Closure invocation.

To supply your own Protos modules from Java, continue with
[Application modules (`app:`)](application-modules.md) after this page.

## Availability

| Capability | State |
|---|---|
| Standard Context construction, `eval`, bindings, `Value.execute` | Implemented and accepted (I086 / PLAT054) on the portable JVM runtime |
| Core packaged inside `protos.jar` (no `protos.CoreRoot`, no language home) | Implemented and accepted (PLAT054-3C) |
| Java scalar arguments to Protos Closures | Implemented: `Byte`, `Short`, `Integer`, `Long`, `String` only |
| Suspending `Future.value()` inside a host-invoked Closure | Implemented (PLAT054-3E2) |
| Default `filesystem` / `network` slots | Only when the Context grants file / socket access (PLAT054-3E3, 3E4) |
| Foreign providers (`java:` and other schemes) in a standard Context | Not available: no Context option or API configures them; see [Foreign interoperability](../interop/README.md) |
| Native Image embedding hosts | Not supported. The Native CLI has no Java embedding |

Verified scope: this repository's `0.3.312-SNAPSHOT` development revision.
That is not a statement that any published release contains this API. Before
relying on it, check the release notes of the distribution you actually use.

## Prerequisites

- A JDK able to run the Protos portable JVM distribution.
- The class path entries of that distribution: `lib/protos.jar` and every JAR
  in `lib/runtime/`. These supply the Protos language and the GraalVM Polyglot
  and Truffle runtime.

The repository's portable smoke (`dist/smoke_polyglot_embedding.sh`) compiles
and runs an external application with exactly that class path, outside the
checkout. No Maven coordinates for consuming Protos as a library dependency are
documented here; none are verified as published.

## Minimal example

```java
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public final class HelloProtos {
    public static void main(String[] args) {
        try (Context context = Context.newBuilder("protos").build()) {
            context.eval("protos", "add: (a, b) => { a + b }\n");
            Value add = context.getBindings("protos").getMember("add");
            System.out.println(add.execute(2, 3).asInt());
        }
    }
}
```

Compile and run it against the extracted distribution (`$PROTOS` is the
extracted directory):

```sh
javac -cp "$PROTOS/lib/protos.jar:$PROTOS/lib/runtime/*" HelloProtos.java
java -cp ".:$PROTOS/lib/protos.jar:$PROTOS/lib/runtime/*" HelloProtos
```

Expected output: `5`.

The portable smoke additionally passes `--enable-native-access=ALL-UNNAMED`
and runs with `-Dtruffle.UseFallbackRuntime=true` and
`-Dpolyglot.engine.WarnInterpreterOnly=false`, because it validates packaging,
not the optimizing runtime. Without those flags the JVM or Truffle may print
warnings; they are not Protos failures.

## What happens, step by step

**Building the Context** creates no Protos Process. Querying
`context.getBindings("protos")` before any evaluation also creates nothing and
returns an empty scope.

**The first valid evaluation** bootstraps Core, the Process, and its RootActor,
and runs as the RootActor's initial module. Only that initial module receives
the bootstrap-local slots: `process` always, and `filesystem` / `network` only
when granted (see below). A source with a syntax error fails before bootstrap.

**Later evaluations** are further direct entries into the same RootActor. Each
one gets a fresh standalone `moduleContext`. There is no REPL namespace: a
binding created by one `eval` is not visible to the next one as a bare name.
Use `import(...)` for shared code, or read results from the host side.

**The bindings view** shows the own local slots of the module context of the
last entry that completed normally. It is read-only from Java. An entry that
fails leaves the previous selection unchanged.

**Reading a Closure** from the bindings produces a fresh receiver-bound
extraction. A `Value` you retain keeps exactly that Closure, even after later
evaluations change the selected module context.

**Arguments** passed to `Value.execute(...)` cross as follows:

- Java `Byte`, `Short`, `Integer`, `Long` become Protos Integers of the same
  value;
- Java `String` becomes a Protos String (an unpaired surrogate is rejected);
- a Protos-valued `Value` passes through unchanged;
- every other Java value (floating point, `Boolean`, `Character`,
  `BigInteger`, collections, lambdas, arbitrary objects) is rejected before any
  guest code runs. That rejection is not fatal to the Process.

**Suspension**: a Closure called through `execute()` may call `value()` on a
pending Future; the call suspends and `execute()` returns only the final result
or failure.

**Closing the Context** terminates the Process, revokes Process-local authority,
and joins the Process's Actor carrier threads. No guest code runs after that.

## Core library location

`protos.jar` carries Core as a Truffle internal resource, so no option is
required. Core resolution order is:

1. the `protos.CoreRoot` option, when set
   (`Context.newBuilder("protos").option("protos.CoreRoot", dir)`); an invalid
   directory fails explicitly, without fallback;
2. the Protos language home, when it contains `protos/lib/core`;
3. the Core packaged in `protos.jar`.

Loading Core grants the guest no Filesystem or Network authority.

## Authority and thread policy

The Context's own Polyglot permissions bound what Protos may do:

- **Filesystem.** The initial module receives `filesystem` only when the
  Context effectively allows file access, for example
  `allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build())` or a
  custom `fileSystem(...)` provider. Its base is the Context's working
  directory inside that provider. If that base cannot be safely confined, the
  first evaluation fails as a bootstrap failure.
- **Network.** `network` is present only when the Context effectively grants
  socket access.
- **Threads.** `Actor.spawn` needs `allowCreateThread(true)` to run the new
  Actor. In a Context that forbids thread creation, `Actor.spawn` still returns
  an `ActorRef`, but the incarnation terminates and later requests fail as
  ordinary Future failures.

Neither slot is injected into later host entries; pass the capability
explicitly if other code needs it.

## Common mistakes

| Symptom | Cause |
|---|---|
| A name defined in one `eval` is missing in the next | Each entry has its own module context; there is no accumulated top-level scope |
| `PolyglotException` from `execute(1.5)` or `execute(true)` | Only the scalar types listed above cross from Java |
| Every later `eval` fails after an Error | An unhandled Error escaping a host entry terminates the RootActor and the Process; it is never restarted. Handle Errors in Protos (`Error.handle(...)`) when the Process must survive |
| `filesystem` is not bound | The Context was not granted file access |
| Spawned Actor never answers | The Context was built without `allowCreateThread(true)` |
| Concurrent `eval` from two Java threads fails | Unsafe concurrent entry into the same RootActor is rejected, not serialized |

## Native Image

Protos does not support embedding in a Native Image host. The Native CLI
(`protos` native release) runs Protos programs but offers no Java embedding API.
Everything on this page applies to the portable JVM runtime only.

## References

Implementation and evidence:

- [`ProtosLanguage.java`](../../../src/main/java/com/guillermomolina/protos/execution/ProtosLanguage.java)
  (`protos.CoreRoot` option, binding scope, Context close);
- [`dist/Plat054EmbeddingProbe.java`](../../../dist/Plat054EmbeddingProbe.java)
  and [`dist/smoke_polyglot_embedding.sh`](../../../dist/smoke_polyglot_embedding.sh)
  (external application against the distributed JAR);
- [`ProtosEmbeddingLifecycleTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/ProtosEmbeddingLifecycleTest.java),
  [`ProtosEmbeddedFilesystemTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/ProtosEmbeddedFilesystemTest.java),
  and [`ProtosEmbeddedNetworkTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/ProtosEmbeddedNetworkTest.java).

Normative specification:

- [`spec/io/PROCESS_IO.md`](../../../spec/io/PROCESS_IO.md), Standard Polyglot
  embedding bootstrap and authority;
- [`spec/semantics/MODULES.md`](../../../spec/semantics/MODULES.md),
  Host-initiated evaluations in an embedded RootActor;
- [`spec/semantics/CALLABLES.md`](../../../spec/semantics/CALLABLES.md) for
  receiver-bound Closure extraction;
- [`spec/concurrency/FUTURES_AND_TASKS.md`](../../../spec/concurrency/FUTURES_AND_TASKS.md)
  §29, Suspendible host-initiated RootActor entries.
