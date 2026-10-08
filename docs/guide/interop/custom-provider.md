# Writing and registering an external foreign provider

> **Status:** Non-normative HOWTO
>
> **Normative owners:** `spec/semantics/VALUES_AND_COLLECTIONS.md` (Foreign
> Values) and `spec/semantics/MODULES.md` (Foreign module instances)

## Purpose

An external provider lets a Protos program `import("scheme:target")` and use
modules and values implemented outside Protos (in Java, through a native
library, or through another runtime), without changing or rebuilding Protos.
Read the [interoperability map](README.md) first for the model and its limits.

## Availability

| Step | State |
|---|---|
| Implementing `ProtosForeignProviderPlugin` against `lib/protos.jar` | Implemented and accepted (I085-A) |
| Loading it with `protos --foreign-provider-path` on the **portable JVM** distribution | Implemented and accepted (I085-B) |
| Loading it in a standard Polyglot `Context` | Not available |
| Loading it in the Native CLI | Not available |
| Receiving Protos callbacks (Closures) through the SPI | Not available |

Verified scope: this repository's `0.3.312-SNAPSHOT` development revision; no
release containing it is implied.

## Prerequisites

- An extracted Protos portable JVM distribution (`$PROTOS`).
- A JDK to compile the provider. The repository harness compiles fixtures with
  `javac --release 21` against `$PROTOS/lib/protos.jar` only.

## The contract in one table

All types are in `com.guillermomolina.protos.spi.foreign`.

| Method | Purpose | When Protos calls it |
|---|---|---|
| `providerId()` | Host-only identity; never visible to Protos code | At registration |
| `scheme()` | The import scheme owned: `[a-z][a-z0-9+.-]*`, not `std`, `self`, `dep`, `tool-shared` | At registration |
| `canonicalTarget(target)` | Canonical, stable spelling of the text after `scheme:`; throw to reject | On import, before any session |
| `openSession(environment)` | One logical session; at most one per Actor | First real use in an Actor |
| `session.acquireModule(target)` | Returns the opaque module handle | On first import of that target in the Actor |
| `valueOperations()` | The provider's single value contract | Once, lazily |

`ProtosForeignValueOperations` classifies handles and implements the
operations the provider chooses to support:

- `classify(session, handle)` returns a `ProtosForeignValueClass`: a lossless
  scalar (`booleanValue`, `absent`, `text`, `integral`, `binary64`) or a
  `handle(identityKey, capabilities)`;
- capabilities (`EXECUTABLE`, `INSTANTIABLE`, `INDEXED_READ`,
  `INDEXED_WRITE`, `ITERABLE`, `MEMBER_READ`, `MEMBER_WRITE`) decide which
  ordinary Protos operations are projected;
- `canFaithfullyReadMember` / `readMember`, `execute`, `instantiate`,
  `writeMember`, `readElement` / `writeElement`, and `openIterator` /
  `iteratorHasNext` / `iteratorNext` default to "unsupported";
- `acceptsArgument` is checked before entry; arguments arrive as
  `ProtosForeignArgumentValue` (Boolean, `null`, `String`, `BigInteger`,
  `Double`, or a handle of the same session);
- `describeFailure` sanitizes a thrown exception into the `ForeignError`
  payload; the default exposes nothing.

The provider never implements Protos semantics: ModuleKeys, facades, caching,
identity, Actor isolation, conversion, Errors, and lifetime belong to Protos.

## Minimal provider

This provider owns the `demo` scheme and exposes one module, `numbers`, with
one operation, `twice`. It is a reduced form of the tested fixture
[`OpaqueProvider.java`](../../../protos/tests/foreign-provider/plugins/opaque/src/i085b/opaque/OpaqueProvider.java).

```java
package example.demo;

import com.guillermomolina.protos.spi.foreign.ProtosForeignArgumentValue;
import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginEnvironment;
import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginSession;
import com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass.Capability;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass.Kind;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueOperations;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

public final class DemoProvider implements ProtosForeignProviderPlugin {
    /** The module handle. */
    static final class Numbers {}

    /** The `twice` member bound to its receiver. */
    record Twice(Numbers receiver) {}

    public DemoProvider() {} // ServiceLoader needs a public no-argument constructor

    public String providerId() { return "example-demo"; }

    public String scheme() { return "demo"; }

    public String canonicalTarget(String target) {
        if (!target.equals("numbers")) {
            throw new IllegalArgumentException("unknown module: " + target);
        }
        return target;
    }

    public ProtosForeignPluginSession openSession(ProtosForeignPluginEnvironment environment) {
        return new ProtosForeignPluginSession() {
            public Object acquireModule(String target) { return new Numbers(); }

            public void close() {}
        };
    }

    public ProtosForeignValueOperations valueOperations() {
        return new ProtosForeignValueOperations() {
            public String language() { return "example-demo"; }

            public ProtosForeignValueClass classify(
                    ProtosForeignPluginSession session, Object handle) {
                if (handle instanceof Long number) {
                    return ProtosForeignValueClass.integral(BigInteger.valueOf(number));
                }
                if (handle instanceof Twice) {
                    return ProtosForeignValueClass.handle(null, Set.of(Capability.EXECUTABLE));
                }
                return ProtosForeignValueClass.handle(handle, Set.of(Capability.MEMBER_READ));
            }

            public boolean acceptsArgument(ProtosForeignArgumentValue argument) {
                return argument.kind() == Kind.INTEGER;
            }

            public boolean canFaithfullyReadMember(
                    ProtosForeignPluginSession session, Object handle, String name) {
                return handle instanceof Numbers && name.equals("twice");
            }

            public Object readMember(
                    ProtosForeignPluginSession session, Object handle, String name) {
                return new Twice((Numbers) handle);
            }

            public Object execute(
                    ProtosForeignPluginSession session,
                    Object handle,
                    List<ProtosForeignArgumentValue> arguments) {
                if (arguments.size() != 1) {
                    throw new IllegalArgumentException("twice expects one argument");
                }
                long value = ((BigInteger) arguments.get(0).value()).longValueExact();
                return Math.multiplyExact(value, 2);
            }
        };
    }
}
```

Declare it as a service in
`META-INF/services/com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin`:

```text
example.demo.DemoProvider
```

## Build and register

```sh
mkdir -p classes/META-INF/services
javac --release 21 -cp "$PROTOS/lib/protos.jar" -d classes example/demo/DemoProvider.java
echo example.demo.DemoProvider \
    > classes/META-INF/services/com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin
jar --create --file demo-provider.jar -C classes .
```

Keep the JAR outside the Protos installation; nothing needs to be copied into
it. Then run a program with:

```sh
"$PROTOS/bin/protos" --foreign-provider-path demo-provider.jar main.protos
```

`--foreign-provider-path` must come before the file, `-e`, or REPL execution.
It accepts a list of JARs or class directories separated by the platform path
separator, and may be repeated.

## Using it from Protos

```protos
Numbers: import("demo:numbers")

print(Numbers.twice(21))
print(import("demo:numbers") === Numbers)
```

Expected output:

```text
42
true
```

What happens:

1. `import("demo:numbers")` asks the provider for `canonicalTarget("numbers")`,
   opens the Actor's session, and acquires the module handle. The result is an
   Actor-local facade, cached under the canonical key.
2. `Numbers.twice` is an ordinary member read. Because the module handle has
   `MEMBER_READ` and `canFaithfullyReadMember` answers true, Protos calls
   `readMember` and admits the bound `Twice` handle.
3. `(21)` is ordinary invocation; the `EXECUTABLE` capability projects it to
   `execute`. The Integer crosses as a `BigInteger`; the `Long` result is
   classified as integral and admitted as the Protos Integer `42`.

## Errors

| Situation | Protos result |
|---|---|
| No provider owns the scheme (JAR not passed) | Ordinary resolution Error on `import` |
| `canonicalTarget` throws (unknown target) | `import` fails with an Error |
| `acquireModule` throws | Failed initialization: the facade is removed from the cache; a later import may retry |
| `acceptsArgument` rejects an argument, wrong capability, closed session | Ordinary Error **before** entry; the provider is not called |
| `execute` (or another operation) throws | Fresh `ForeignError`; its payload contains only what `describeFailure` returns |

A program that leaves such an Error unhandled exits with failure, as any other
unhandled Error does.

## Authority and isolation

- The JAR runs **trusted in process** with the JVM's authority. Protos does not
  sandbox it. Only load providers you trust.
- Without `--foreign-provider-path`, nothing is discovered, even when the JAR
  is on `CLASSPATH` or in the working directory.
- `ProtosForeignPluginEnvironment` exposes only the provider id and
  host-supplied options; it gives no access to the Process, Actors, or Protos
  capabilities. The CLI supplies no options.
- Each Actor has its own session and its own facades. Every handle stays bound
  to the session that produced it; using it after that session closes fails.
- Configured providers that a program never imports open no session.

## Testing a provider

The repository harness
[`dist/test_external_foreign_providers.py`](../../../dist/test_external_foreign_providers.py)
(run by `make test-protos-foreign`) is the reference workflow: it extracts the
portable distribution, compiles each fixture under
[`protos/tests/foreign-provider/plugins/`](../../../protos/tests/foreign-provider/plugins/)
against `lib/protos.jar` only, and runs the `.protos` programs in
[`protos/tests/foreign-provider/`](../../../protos/tests/foreign-provider/) as
separate processes.

## References

- SPI sources:
  [`spi/foreign/`](../../../src/main/java/com/guillermomolina/protos/spi/foreign/);
- loader:
  [`ProtosExternalProviderPluginLoader.java`](../../../src/main/java/com/guillermomolina/protos/execution/ProtosExternalProviderPluginLoader.java);
- CLI option:
  [`ProtosCli.java`](../../../src/main/java/com/guillermomolina/protos/cli/ProtosCli.java);
- tests:
  [`ProtosExternalForeignProviderTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/ProtosExternalForeignProviderTest.java);
- normative: [`VALUES_AND_COLLECTIONS.md`](../../../spec/semantics/VALUES_AND_COLLECTIONS.md)
  (Foreign Values), [`MODULES.md`](../../../spec/semantics/MODULES.md)
  (Foreign module instances), [`ERRORS.md`](../../../spec/semantics/ERRORS.md)
  (`ForeignError`).
