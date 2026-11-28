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
| Explicit `clock` (`protos.AllowClock`) | Only with explicit `true`; default `false` (D253-C1 / I163) |
| Explicit `entropy` (`protos.AllowEntropy`) | Only when the embedding Context selects `true`; default `false` (D243-C / I157) |
| Explicit `execution` (`protos.AllowExecution`) | Only with explicit `true` **and** a host that permits process creation (`allowCreateProcess(true)`); host file access is not required and is never granted by it; default `false`. Partial: with an embedder-supplied process handler `wait()` fails with `IOError` (Protos cannot verify its provenance), and a `Context` close cannot be retried when a child, pipe or pump is still outstanding (D229-B1 / I166) |
| Foreign providers (`java:` and other schemes) in a standard Context | Not available: no Context option or API configures them; see [Foreign interoperability](../interop/README.md) |
| Native Image embedding hosts | Not supported. The Native CLI has no Java embedding |

Verified scope: this repository's `0.3.312-SNAPSHOT` development revision.
That is not a statement that any published release contains this API. Before
relying on it, check the release notes of the distribution you actually use.

## Prerequisites

- The exact supported GraalVM Community 25.6.5.1 / JDK 25.0.5.1
  required by the current PLAT073-C/DIST022 portable runtime. Protos
  implementation classes target Java 25 (classfile major 69); a Java 21
  runtime cannot load them. Older releases keep their published runtime
  contracts; do not infer support for any arbitrary JDK 25 distribution.
- The class path entries of that distribution: `lib/protos.jar` and every JAR
  in `lib/runtime/`. These supply the Protos language and the GraalVM Polyglot
  and Truffle runtime.

The repository's portable smoke (`dist/smoke/smoke_polyglot_embedding.sh`) compiles
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
the bootstrap-local slots: `process` always, `filesystem` / `network` only
when their Context permissions allow them, and `entropy` only when explicitly
selected by `protos.AllowEntropy`, and `clock` only by `protos.AllowClock`
(see below). A source with a syntax error fails before bootstrap.

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

The standalone CLI separately accepts `--allow-resolver` for its application
entries. This does not add a Resolver option to the standard Polyglot `Context`
API or make socket access imply name-resolution authority. The reusable
`ProtosStandaloneHostedSession.open` overload can independently select
`NetworkGrant`, `ResolverGrant`, `EntropyGrant` and `ClockGrant`; legacy
overloads continue selecting `ResolverGrant.NONE`.

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
- **Entropy.** `entropy` is present only when the Context explicitly selects
  `option("protos.AllowEntropy", "true")`; its default is `false`. Socket,
  file, thread, foreign and `allowAllAccess` permissions do **not** imply it.
- **Threads.** `Actor.spawn` needs `allowCreateThread(true)` to run the new
  Actor. In a Context that forbids thread creation, `Actor.spawn` still returns
  an `ActorRef`, but the incarnation terminates and later requests fail as
  ordinary Future failures.

None of these optional bootstrap-local slots is injected into later host
entries. Entropy is non-transferable to another Actor or to P; use an
ActorRef broker to request ordinary completed Bytes when another Actor needs
random data.

## Explicit Entropy authorization

Use one independent option on the `Context` builder. It is **not** inferred
from `allowIO`, sockets, threads, `allowAllAccess`, host access, inspection,
foreign interop or an imported module. The default is `false`.

```java
try (Context context = Context.newBuilder("protos")
        .option("protos.AllowEntropy", "true")
        .build()) {
    context.eval("protos", "sample: entropy.bytes(16).value()\n");
    // The result remains secret application data; do not print its bytes.
}
```

The grant is installed only in the first valid initial RootActor module of
this Context. Subsequent host `eval` entries do not acquire it by default;
other Actors do not inherit it and it cannot be copied through ordinary
Actor message/reply/spawn or isolated P transfer. An authorized Actor may
serve `Bytes` results to other Actors through an ActorRef broker.

`entropy.bytes(count)` returns `Future<Bytes>` for an Integer in `0..65536`.
It suspends only when an ordinary Future is observed in a suspendible execution
context; no blocking generator runs inside the Actor turn. `entropy.close()`
is the normal idempotent Future-returning lifecycle operation. Context
finalization and Process termination withdraw this Context's grant without
affecting other Contexts' grants. Native CLI execution has the same Entropy
semantics, but Java `Context` embedding into Native Image is not supported.

See [Process, I/O and authority](../11-process-io-filesystems-and-authority.md#secure-entropy-is-an-explicit-rootactor-capability-d243-c)
and the normative [`spec/io/ENTROPY.md`](../../../spec/io/ENTROPY.md).

## Hosting a RootActor message service

Everything above is ordinary Polyglot embedding: each `eval` is a direct entry
that evaluates source and returns its result, and that does not change. A Java
host that instead wants the RootActor itself to be a long-lived message service
(D225-B1, `spec/concurrency/ACTORS.md` §32) opts in explicitly with
`com.guillermomolina.protos.execution.ProtosRootActorServiceSession` from
`protos.jar`. It is a separate host API, not a `Context` option, and the
`protos` command line does not offer it.

**Which embedding entry should I use?**

| Entry | Use it for |
|---|---|
| A Polyglot `Context` and `eval` | Ordinary one-shot evaluation: run a program or expression and read its result |
| `ProtosRootActorServiceSession` | A long-lived Protos application service, such as an HTTP Host whose Workers call the RootActor |
| The `app:` application module catalog | Supplying Protos modules by exact `app:` name, for example an HTTP Worker factory, to either entry |

In every case the application, the HTTP Worker handler factory, request
handling and service logic stay written in Protos. Java selects the entry, the
grants and the module catalog; it is not an application server.

```java
try (ProtosRootActorServiceSession service =
        ProtosRootActorServiceSession.open(
                coreRoot,                    // the distribution's protos/lib/core
                Path.of("service.protos"),   // the entry module
                "service",                   // its own top-level bootstrap binding
                List.of(),                   // process.args()
                System.in, System.out, System.err,
                NetworkGrant.HOST_NETWORK,   // or NetworkGrant.NONE
                ResolverGrant.NONE)) {       // or ResolverGrant.HOST_RESOLVER
    service.awaitTermination();             // until the Protos program ends it
}
```

`open` resolves the file as the entry module, bootstraps one Process with
exactly the selected grants, and lets the existing RootActor execute that module
once and invoke its `service` binding. The returned Protos object becomes the
RootActor's behavior, and `open` returns once the RootActor is `READY`. If the
binding is missing, inherited, not callable, returns `null` or a Number, or an
Error escapes, the Process terminates and `open` throws
`ProtosRootActorServiceSession.InitializationFailedException`; nothing is left
open.

After `open` the application is Protos code: the service creates its own Actors
and hands them `Actor.group(Actor.current())`, and they send it ordinary
messages. The session has no message API and runs no Java receive loop or
callback. It owns the Process, its Context, the runtime host (and with it any
granted Network or Resolver) until you call `close()`, which terminates the
Process, waits for it and releases those resources; `close()` is idempotent.
`awaitTermination()` and `isTerminated()` observe a Process that ended by itself,
for example after a fatal RootActor failure; `close()` is still required.

A second overload adds one last argument, `Map<String, String>
applicationModules`: exact `app:` identifiers mapped to Protos source text, the
same catalog rules as [Supplying application modules from Java](application-modules.md).
The session validates and copies it before it opens anything (an invalid entry
throws `IllegalArgumentException` or `NullPointerException`), then resolves the
entry file and its `./` modules first, the catalog next and `std:` last, for the
whole Process lifetime. The catalog is source, not authority or a service
registry. It is how code that other Actors import by name, such as an HTTP
Worker factory, reaches them, because a Worker resolves that name inside its own
Actor, where a `./` path has no meaning:

```java
String handler = Files.readString(Path.of("root-service-handler.protos")); // Protos source
try (ProtosRootActorServiceSession session =
        ProtosRootActorServiceSession.open(
                coreRoot, Path.of("root-service-host.protos"), "service", List.of(),
                System.in, System.out, System.err,
                NetworkGrant.HOST_NETWORK, ResolverGrant.NONE,
                Map.of("app:root-service-handler", handler))) {
    session.awaitTermination();
}
```

The service, its HTTP Host and the handler factory all stay Protos source; Java
only selects the entry, the grants and the catalog. Neither
`ProtosStandaloneHostedSession` nor a Polyglot `Context.eval` changes: both
remain direct entries, and a Polyglot `Context` still receives `app:` modules
only through `ProtosEmbeddedModules.install`.

`NetworkGrant` (`ProtosWorkspacePackageApplicationExecution.NetworkGrant`) and
`ResolverGrant` (`ProtosStandaloneHostedExecution.ResolverGrant`) are
independent, and choosing the service entry grants neither. Granted capabilities
are bound only in the RootActor's initial module; Actors the service creates
never receive them (see [Process, I/O, Filesystems, and
Authority](../11-process-io-filesystems-and-authority.md#the-rootactor-service-entry-receives-the-same-grants)).

## Common mistakes

| Symptom | Cause |
|---|---|
| A name defined in one `eval` is missing in the next | Each entry has its own module context; there is no accumulated top-level scope |
| `PolyglotException` from `execute(1.5)` or `execute(true)` | Only the scalar types listed above cross from Java |
| Every later `eval` fails after an Error | An unhandled Error escaping a host entry terminates the RootActor and the Process; it is never restarted. Handle Errors in Protos (`Error.handle(...)`) when the Process must survive |
| `filesystem` is not bound | The Context was not granted file access |
| `entropy` is not bound | `protos.AllowEntropy` is absent or `false`; socket and broad host permissions do not authorize Entropy |
| Spawned Actor never answers | The Context was built without `allowCreateThread(true)` |
| Concurrent `eval` from two Java threads fails | Unsafe concurrent entry into the same RootActor is rejected, not serialized |

## Native Image

Protos does not support embedding in a Native Image host. The Native CLI
(`protos` native release) runs Protos programs but offers no Java embedding API.
Everything on this page applies to the portable JVM runtime only.

## References

Implementation and evidence:

- [`ProtosLanguage.java`](../../../src/main/java/com/guillermomolina/protos/execution/host/embedding/ProtosLanguage.java)
  (`protos.CoreRoot` option, binding scope, Context close);
- [`dist/Plat054EmbeddingProbe.java`](../../../dist/Plat054EmbeddingProbe.java)
  and [`dist/smoke/smoke_polyglot_embedding.sh`](../../../dist/smoke/smoke_polyglot_embedding.sh)
  (external application against the distributed JAR);
- [`ProtosEmbeddingLifecycleTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/integration/interop/ProtosEmbeddingLifecycleTest.java),
  [`ProtosEmbeddedFilesystemTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/integration/filesystem/ProtosEmbeddedFilesystemTest.java),
  and [`ProtosEmbeddedNetworkTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/integration/network/ProtosEmbeddedNetworkTest.java).

Normative specification:

- [`spec/io/PROCESS_IO.md`](../../../spec/io/PROCESS_IO.md), Standard Polyglot
  embedding bootstrap and authority;
- [`spec/semantics/MODULES.md`](../../../spec/semantics/MODULES.md),
  Host-initiated evaluations in an embedded RootActor;
- [`spec/semantics/CALLABLES.md`](../../../spec/semantics/CALLABLES.md) for
  receiver-bound Closure extraction;
- [`spec/concurrency/FUTURES_AND_TASKS.md`](../../../spec/concurrency/FUTURES_AND_TASKS.md)
  §29, Suspendible host-initiated RootActor entries.

## Explicit Clock authorization

```java
try (Context context = Context.newBuilder("protos")
        .option("protos.AllowClock", "true")
        .build()) {
    context.eval("protos", "Instant: import(\"std:datetime/instant\")\n"
            + "now: Instant(clock.epochNanoseconds())\n");
}
```

The option defaults to `false`. Only the first initial RootActor module receives
its private `clock` grant; later evaluations, imported MODULEs and spawned Actors
receive none. `allowAllAccess`, I/O/host permissions and `protos.AllowEntropy`
never imply Clock. Grants have independent Context/Process ownership and are
retired on Process termination, Context shutdown or host revocation. Closing one
Context does not revoke another's grant. The operation is synchronous and has no
guest `close()`. Samples are exact epoch-nanoseconds Integers, with no monotonic
or physical-resolution promise. This Java embedding is supported on the JVM;
Java Native Image embedding remains unsupported. The standalone Native CLI
supports its own explicit `--allow-clock` authorization.

## Explicit external execution authorization

```java
try (Context context = Context.newBuilder("protos")
        .option("protos.AllowExecution", "true")
        .allowCreateProcess(true)
        .allowIO(IOAccess.NONE) // execution needs no guest file access
        .processHandler(new com.guillermomolina.protos.execution.ProtosPosixProcessHandler())
        .build()) {
    context.eval("protos", "child: execution.start(\"/bin/echo\", [\"hi\"], Map(), null)\n");
}
```

`protos.AllowExecution` defaults to `false`. It is the only selection of the
grant: `allowCreateProcess`, `allowAllAccess`, `allowIO`, `protos.AllowEntropy`,
`protos.AllowClock` and every other permission never imply it. Conversely, the
selection never widens host permissions: provisioning requires only
`allowCreateProcess(true)`, the same permission `TruffleLanguage.Env.newProcessBuilder`
checks, and when the Context does not allow it no grant is provisioned.

Four things stay distinct. *Host process-creation authorization*
(`allowCreateProcess`) lets the Context create processes at all. *The selected
`ProcessHandler`* decides which provider performs the creation; authorization
does not identify it. *Guest Filesystem access* (`allowIO`, and the guest
`filesystem` slot) is a separate grant: execution neither requires nor confers
it, and a Context with `IOAccess.NONE` can hold `execution` and still have no
`filesystem`. *The native working directory* of a launch is a host path, the
Context's working directory captured once at provisioning (or the explicit
directory of the request), passed to the provider by path; it involves no guest
file access. Only the first initial RootActor module
receives `execution`; later evaluations, imported MODULEs and spawned Actors
receive none implicitly. Guest code may delegate the grant explicitly to another
Actor of the same Process, as a message argument or an `Actor.spawn`
initialization argument: the receiver gets a fresh Actor-local view with the
same authority, usable only in that Actor, and revoking the grant revokes every
view. The grant and its views never enter isolated parallel execution. Each
child belongs to the Actor whose `start` created it: it and its endpoints are
usable only from that Actor, even through a delegated grant, so delegating the
grant never delegates the children the sender already started. Children and
their endpoints never cross Actors or enter parallel execution; an `ExitStatus`
is plain terminated data and does.

`execution.start(executable, arguments, environment, workingDirectory)` takes
exactly four arguments and launches the program directly, without a shell. The
`Map` is the complete child environment (nothing is inherited); duplicate names
are judged by the host's native name equivalence, the same one that governs
`process.environment()`. `null` selects the grant's default working directory:
the Context's current working directory, captured once when the grant is
provisioned and applied explicitly to every such launch. Children expose
`stdin()`, `stdout()`, `stderr()`, `wait()` and `terminate()`.

**Custody.** The Process keeps each child until three things are true: the
native supervisor confirmed that the child was reaped, the release of each of
its three native endpoint handles was confirmed, and its pipe transport (its
pump threads and pipes) stopped. A child that was reaped and whose handles were
closed stays in custody while one of its pumps is still running. A termination
request, a forced termination or a supervisor failure is never taken as proof of
reaping, and a handle whose close failed stays in custody (it is never closed a
second time). If a child was already created when `start` failed (for example
its endpoints could not be connected), the `start` Future stays pending while
that child is terminated, reaped and released, and fails with `IOError` only
afterwards; if that cleanup cannot be confirmed (a failed supervisor or an
uncertain handle release), the Future stays pending and the child stays in
custody. Exit of
the child is not EOF: an exited child whose output was not read keeps delivering
the remaining bytes, and a descendant can keep a pipe writer open. Endpoints are
released by `close()` or by the Process cleanup. Revoking the grant, requesting
a close and confirming a release are distinct: revocation stops new guest
operations; a guest `close()` of an output endpoint releases the logical reader
and completes without blocking the Actor; the native release is confirmed only
when the endpoint's pump closed the native handle, which it does after EOF, a
failure or the end of its current transfer. Until then the pump may stay blocked while
the child, or a descendant that keeps the other end of the pipe open, stays
silent, and the child stays in custody (with its admission slot). Closing stdin completes only after the pump handed every accepted byte to
the child, so a child that never reads its input keeps that close pending; the
bounded worker pool does not change either case. Closing the Context or
terminating the Process revokes guest access, requests termination of live
children, closes every endpoint handle still owned (also of children that
already exited), waits briefly for launches already in progress, and then
reclaims each child with a bounded wait (about two seconds, then a forced
termination and up to two more seconds). A child that still cannot be reclaimed
stays tracked, and a later close retries it.

**A close can refuse to complete (standalone and `protos run` hosts only).**
This section describes Protos's own Process host
(`ProtosPolyglotRuntimeHost`), not a `Context` that your code owns. Its refusal
handling is a current implementation measure that is not a Truffle
`Context.close()` behavior, and it remains a known limitation of this partial
implementation. If a launch is still in progress when the bounded wait ends, or
a child cannot be shown to be reaped, released and its transport stopped, the
close goes through these distinct states:

1. **Close refused.** The host keeps the Process and its custody and does not
   call `Context.close()` at all. Waiting for the Process's terminal
   disposition fails with `IllegalStateException`; guest access stays revoked.
   The refusal is attributed to that Process Context only: it stays counted as
   active by its `ProtosPolyglotRuntimeHost`, and other Contexts are not
   affected.
2. **Reclamation in progress.** The host's grant stays retained (with its
   workers) while any launch, child or pipe transport is still outstanding.
   Each retry reclaims again with the same bounded waits; it never repeats a
   reap or closes an uncertain handle a second time.
3. **Reclamation confirmed.** Every launch finished and every child was reaped,
   its handles were released and its transport stopped.
4. **Context really closed.** Only then does a retry call `Context.close()`.
   The Context is counted as closed exactly once, after that call succeeds.
   `ProtosPolyglotRuntimeHost.close()` performs this retry first, and
   `retryRefusedProcessContextClosesForRuntime()` performs it on demand. A
   retry succeeds only if the custody was discharged in the meantime; nothing
   guarantees that it ever will (a child whose supervisor failed, or a pipe kept
   open by a descendant, keeps it refused).

The host never calls `Context.close()` while children are in custody, for a
specific reason: on the Truffle version in use, `Context.close()` closes the
Context first and only then reports a live sub-process, so a second call would
prove nothing. For the same reason, if `Context.close()` itself fails after
custody was discharged, that failure is final and is not retried.

In a standard embedding, where your code owns the `Context`, Protos reclaims
children while the Context is finalized and disposed, with the same bounded
waits. If that cannot be established, the Process keeps custody of the
children, and Truffle reports any child it created that is still alive. Your
host cannot retry `Context.close()`: Truffle does not guarantee that a second
close is meaningful, and Protos offers no API to retry the reclamation after
your `Context` has closed. Any child, pipe or pump still outstanding at that
point is released only when it finishes by itself. Until every resource can be
closed safely in this case, external execution in a user-owned `Context`
remains partial.

**Resources.** Each Process admits at most 16 children at a time; a `start`
beyond that fails with `IOCapacityExhausted` before anything is launched, and
the slot is returned only after the child left custody and its pipe transport
stopped. Each child uses three pump threads and three bounded pipes; one
selector thread per grant serves all endpoint reads and writes, so a blocked
read never holds a worker. Launches and termination requests run on at most 16
host threads per grant, with at most 64 waiting requests; the threads start only
on first use. Buffering per endpoint is finite (one small chunk plus the OS pipe
capacity): a guest that stops reading stops the child's output, and nothing is
drained in advance.

**Provider selection and evidence.** Protos's standalone host installs
`ProtosPosixProcessHandler` on its Context builder when execution is selected.
An external embedding host must explicitly install that provider before building
its Context, as above. A third case is a Context that allows creation but whose
provider was not verified: `allowCreateProcess(true)` authorizes creation and
does not identify the selected handler. `TruffleLanguage.Env` does not expose
that identity. Such a Context cannot be declared conformant to
`EXTERNAL_PROCESS.md` §6. No guest option or self-declared token verifies it,
and failure to load the POSIX library never selects the JDK provider as fallback.

The Linux provider implements owned pipes and a cached terminal POSIX wait
status. Reap does not close stdout or stderr: EOF requires every writer,
including any descendant writer, to close. The JDK provider can retire its
process streams when the direct child exits and lose later descendant output;
it does not establish this guarantee. Its diagnostic remains separate from the
POSIX regressions.

The supplied validation evidence reports 33 passing embedding tests on the JVM
before this consolidation; it does not validate the final candidate or the
complete focal suite. The embedding bootstrap no longer requires host file
access for execution; the strict `IOAccess.NONE` regressions (grant present,
no `filesystem`, real POSIX launch, intact arguments, complete stdout and EOF)
remain to be run. The Native CLI case without `--allow-filesystem` is a
separate surface and does not demonstrate the embedding case, nor the reverse.
Native Image has not been validated, including packaged-library loading,
FFM downcalls, and survival of EPIPE/SIGPIPE. The native regression requires a
failed write followed by another child launch and guest arithmetic in the same
host process; an IOError alone is insufficient evidence of survival.

**Uncertain creation.** All existing Java wrappers, status storage, native
arguments, the PID output cell and symbol bindings are prepared before calling
the native start. The POSIX provider receives the host PID from that same
creating call, written from the fork result before the exec handshake; there is
no later native PID query. The PID is host-side identity for the Java `Process`
API only and is never exposed to guest code or used as authority. Closing the
argument arena remains a possible failure point after creation. A non-null native handle with a launch error means a child was
created but reap was not confirmed. The launcher retains it in its existing
reclamation loop; it cannot publish a definitive launch failure while custody
remains. Persistent `waitpid` failure, failed signalling, or an unconfirmed
stream close or handle release can keep that loop occupied indefinitely.
The loop also catches `Error` during reclamation: it is a retained limitation,
not an accredited recovery mechanism for a damaged JVM. `ProcessHandler.start`
provides no additional public channel to return an uncertain child with an
exception. This remains a publication blocker; no timeout abandons the handle
and no second supervisor or process registry is introduced.

The transport memoizes a failed physical close as an uncertain release and
never calls the physical close again. The Java stream's `closed` flag records
that a close was attempted, not confirmation of release. A failed FFM invocation
can leave native release uncertain, retaining custody. Ordinary negative native
read/write/close results become `IOException`; runtime exceptions and JVM errors
now propagate out of those provider methods without conversion to IOException.

**`wait()` and provider provenance.** A `Context` you build yourself carries a
process handler Protos cannot identify through the language environment, even
when it is `ProtosPosixProcessHandler`: the outcome stays unknown, so
`child.wait()` fails with `IOError` and a numeric exit value never becomes a
guest `ExitStatus`. Its completion still observes the confirmed reap,
independently of EOF. Contexts that the Protos runtime host builds itself (the
CLI and runtime-owned Processes) install the native POSIX provider and record
that fact in the host configuration; there `wait()` resolves to an
`ExitStatus` decoded from the real POSIX status (normal exit with its code, or
signal termination with a null exit code). A supervision failure always fails
`wait()` with `IOError` and keeps the child in custody.
The standalone CLI selects the same grant with `--allow-execution`.
The Context-close refusal mechanism above remains an independent unresolved
integration limitation. See
[`spec/io/EXTERNAL_PROCESS.md`](../../../spec/io/EXTERNAL_PROCESS.md).
