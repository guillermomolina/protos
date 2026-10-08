# Foreign interoperability: map and availability

> **Status:** Non-normative overview
>
> **Normative owners:** `spec/semantics/VALUES_AND_COLLECTIONS.md` (Foreign
> Values), `spec/semantics/MODULES.md` (Foreign module instances),
> `spec/semantics/ERRORS.md` (`ForeignError`), `spec/concurrency/ACTORS.md`
> (§24J, §24K), and `spec/concurrency/FUTURES_AND_TASKS.md` (Synchronous
> foreign callbacks and the current Task)

This page explains how Protos reaches code that is not Protos source, and what
is actually usable at the current repository revision. It is a map, not a
tutorial. The two HOWTOs are:

- [Writing and registering an external provider](custom-provider.md);
- [The `java:` provider fixture](java-provider.md).

Foreign interoperability is **not** a universal bridge to arbitrary Java (or
other) APIs. A program reaches exactly what a provider deliberately exposes,
under the conversion and identity rules of the specification.

## Three different things

Keep these apart:

| Concept | What it is | Who owns it |
|---|---|---|
| Module resolver | Turns an exact specifier String (`std:...`, `app:...`, `./x.protos`) into a ModuleKey for **Protos source** | The host/driver; see [chapter 6](../06-modules-and-imports.md) |
| Foreign provider | Owns one import **scheme** (`java`, `inventado`, ...) and supplies modules and values that are **not** Protos source | Provider code, registered by the host |
| Provider registry | The fixed set of providers a host was started with | The host, at construction time |

From the program's side the entry point is the same ordinary call:

```protos
Math: import("java:math")
```

What happens depends on whether the running host registered a provider for the
`java` scheme. If it did, the result is a foreign module facade. If it did not,
the specifier is resolved only by the ordinary source resolvers, and an unknown
scheme fails as an ordinary Error.

## The model in brief

- **Module identity.** A foreign import yields an Actor-local Protos *facade*
  that participates in the ordinary module cache: repeated imports in one
  Actor are `===`; different Actors get different facades. The underlying
  foreign object never becomes the module identity.
- **Sessions.** A provider opens at most one session per Actor, lazily, on the
  first real use of its scheme. The session is closed with that Actor or its
  Process and is never reused. A configured but unused provider opens nothing.
- **Values.** A foreign value is converted only when lossless and
  source-classified: Boolean, true null, valid String, integral to Integer,
  binary64 to Float. Everything else stays a raw foreign reference whose `==`
  and `hash` are identity, never Java `equals`.
- **Operations.** Member read, invocation, construction, indexed access, and
  `each` work through ordinary Protos syntax only where the provider declares
  a faithful capability. Writes through `x.name = v` never become foreign
  writes. Explicit operations (`invoke`, `instantiate`, `readMember`,
  `writeMember`) live in `std:interop`.
- **Iteration.** Projected `each` is pull-based: Protos pulls the next element
  from a provider iterator and calls the block itself. The block is not handed
  to the foreign runtime.
- **Errors.** A failure detected before a foreign operation is entered is an
  ordinary Protos Error. A failure after entry is a fresh `ForeignError` with
  only safe payload slots (`language`, `operation`, `category`,
  `foreignCategory`, `message`, `cause`). Host exceptions and stacks are never
  exposed.
- **Callbacks.** The specification defines synchronous callbacks: a Protos
  Closure passed into a foreign operation and called within that operation's
  extent. See the availability table for who can use them today.
- **Isolation.** Foreign mutable state does not become shared Protos memory
  between Actors (`ACTORS.md` §24K).
- **Authority.** Loading or importing a provider provisions no Protos
  capability. Each provider runs under an authority profile selected by the
  host, never by the provider or by the import specifier.

The exact rules are in the normative owners listed at the top of this page.

## Availability at this revision

Verified scope: this repository's `0.3.312-SNAPSHOT` development revision. No
release containing any of it is implied.

| Surface | State |
|---|---|
| Shared foreign substrate (facades, cache, D188 admission/identity, `ForeignError`, pull `each`, sessions) | Implemented and accepted (I080–I083); covered by Java unit tests |
| `std:interop` explicit operations | Implemented |
| Public external provider SPI `com.guillermomolina.protos.spi.foreign` | Implemented and accepted (I085-A) |
| Loading external providers in the **portable JVM CLI**: `protos --foreign-provider-path <jars> file.protos` | Implemented and accepted (I085-B, end-to-end harness) |
| Synchronous callbacks through the **public SPI** | Not available: callbacks do not cross `spi.foreign` |
| Foreign providers in a **standard Polyglot Context** ([embedding](../embedding/java.md)) | Not available: no Context option or public API registers providers there |
| `ProtosPolyglotRuntimeHost.openWithForeignProviders(...)` | Public method, but it builds a Protos-owned runtime host, not a standard Context; running guest code on it requires internal bootstrap classes. Not a documented application flow |
| Restricted host-Java catalogue provider (`ProtosHostJavaCatalogue`, `openWithHostJava`) | Implemented (I082-G) and tested in JUnit only; same limitation as the previous row |
| External providers in the **Native CLI** | Not available: Native Image builds offer no dynamic plugin loading |
| Any provider shipped inside the Protos distribution | None. The `java:`, `inventado:`, and `oslib:` providers are test fixtures |

The practical consequence: today, the supported way for a Protos program to
use a foreign provider is to build a provider JAR against the public SPI and
pass it to the portable JVM CLI with `--foreign-provider-path`.

## Provider identity and trust

An external provider JAR is executable Java code. Passing it with
`--foreign-provider-path` is a host trust decision:

- providers are discovered only on the explicitly listed paths, never from
  `CLASSPATH`, the working directory, or the Protos installation;
- every discovered provider runs **trusted in process**: it has the JVM's own
  authority and is not sandboxed;
- each provider has a host-only `providerId()` (never visible to Protos code)
  and owns one scheme; duplicate ids or schemes, invalid schemes, and the
  source schemes `std`, `self`, `dep`, and `tool-shared` are rejected when the
  host starts.

## References

- [`spec/semantics/VALUES_AND_COLLECTIONS.md`](../../../spec/semantics/VALUES_AND_COLLECTIONS.md),
  Foreign Values (admission, identity, operations, failures, callbacks,
  `std:interop`);
- [`spec/semantics/MODULES.md`](../../../spec/semantics/MODULES.md), Foreign
  module instances;
- [`spec/semantics/ERRORS.md`](../../../spec/semantics/ERRORS.md),
  `ForeignError`;
- [`spec/concurrency/ACTORS.md`](../../../spec/concurrency/ACTORS.md) §24J
  (blocking foreign calls, callbacks) and §24K (foreign mutable state);
- [`spec/concurrency/FUTURES_AND_TASKS.md`](../../../spec/concurrency/FUTURES_AND_TASKS.md),
  Synchronous foreign callbacks and the current Task;
- [`protos/lib/interop.protos`](../../../protos/lib/interop.protos)
  (`std:interop`);
- [`ProtosForeignPullEachTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/ProtosForeignPullEachTest.java)
  and
  [`ProtosExternalForeignProviderTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/ProtosExternalForeignProviderTest.java).
