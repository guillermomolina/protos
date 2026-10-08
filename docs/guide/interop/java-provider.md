# The `java:` provider fixture

> **Status:** Non-normative HOWTO
>
> **Normative owners:** `spec/semantics/VALUES_AND_COLLECTIONS.md` (Foreign
> Values) and `spec/semantics/MODULES.md` (Foreign module instances)

## Purpose

This page shows how a Protos program uses real JDK classes through the
`java:` scheme today, and exactly where that provider comes from. It is a
worked instance of [an external provider](custom-provider.md).

## Availability

| Item | State |
|---|---|
| `JavaProvider` external fixture (`java:math`, `java:date`) | Implemented and accepted as a **test fixture** (I085-B, renamed to `java:` by I088-A) |
| Distribution | Not shipped. It exists only as source under `protos/tests/` and must be compiled by you or by the harness |
| Runtime | Portable JVM CLI with `--foreign-provider-path` only. Not available in a standard Polyglot Context or the Native CLI |
| `jdk:` scheme | Not an alias. `import("jdk:math")` fails |
| General access to any Java class | Not available. Exactly four operations exist |

Verified scope: this repository's `0.3.312-SNAPSHOT` development revision; no
release containing it is implied.

## What the fixture exposes

| Import | Member | Java backing | Arguments |
|---|---|---|---|
| `java:math` | `abs(n)` | `Math.abs(long)` | one Integer that fits a Java `long` |
| `java:math` | `max(a, b)` | `Math.max(long, long)` | two such Integers |
| `java:date` | `year(text)` | `LocalDate.parse(text).getYear()` | one ISO date String |
| `java:date` | `month(text)` | `LocalDate.parse(text).getMonthValue()` | one ISO date String |

Each operation names one exact Java overload inside the provider; Protos does
no overload resolution. Results are Java `long` values admitted as Protos
Integers. The provider id is `i085b-java`; it is host metadata, never visible
to Protos code.

## Getting the provider

The fixture is source only:

```text
protos/tests/foreign-provider/plugins/java/
├── META-INF/services/com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin
└── src/i085b/java/JavaProvider.java
```

**Through the repository harness** (recommended; builds the portable
distribution from the already-packaged JAR, compiles the fixtures, and runs
`java-math.protos` and `java-date.protos`):

```sh
make test-protos-foreign
```

**By hand**, against an extracted portable distribution `$PROTOS`, from the
repository root:

```sh
mkdir -p /tmp/java-provider/classes
javac --release 21 -proc:none -cp "$PROTOS/lib/protos.jar" \
    -d /tmp/java-provider/classes \
    protos/tests/foreign-provider/plugins/java/src/i085b/java/JavaProvider.java
cp -R protos/tests/foreign-provider/plugins/java/META-INF /tmp/java-provider/classes/
jar --create --file /tmp/java-provider/java-provider.jar -C /tmp/java-provider/classes .
```

## Using it

```protos
Math: import("java:math")
Date: import("java:date")

print(Math.abs(-42))
print(Math.max(7, 15))
print(Date.year("2026-10-08"))
print(Date.month("2026-10-08"))
```

Run it with the JAR explicitly authorized:

```sh
"$PROTOS/bin/protos" --foreign-provider-path /tmp/java-provider/java-provider.jar main.protos
```

Expected standard output:

```text
42
15
2026
10
```

The fixture also writes `I085-B-TRACE ...` lines to standard error when it is
loaded and when a session opens; the harness uses them as evidence. They are
fixture diagnostics, not Protos output.

Within one Actor, `import("java:math") === Math` is true. Without
`--foreign-provider-path`, `import("java:math")` is an ordinary resolution Error,
even when the JAR is on `CLASSPATH`.

## Errors

These all signal an Error that Protos code can handle (the fixture programs
check them with `Assertions.signals(Error, ...)`):

| Call | Reason |
|---|---|
| `import("jdk:math")` | No provider owns `jdk:` |
| `import("java:java.lang.Math")` | Unknown target: only `math` and `date` exist |
| `Math.abs("42")` | String is not accepted where an Integer is required |
| `Math.abs(1.5)` | Floats are not accepted by this provider |
| `Math.abs(9223372036854775808)` | Does not fit a Java `long` |
| `Math.max(1)` | Wrong arity |
| `Date.year("2026-13-08")`, `Date.month("2025-02-29")`, `Date.year("not a date")` | `LocalDate.parse` rejects them |
| `Date.year(2026)` | Integer is not accepted where a String is required |

## Authority

The fixture is loaded **trusted in process**, like every external provider.
Its four operations touch no I/O, but that is a property of this fixture's
code, not a guarantee Protos enforces for `java:` in general. Importing it
grants the program no Filesystem, Network, or other capability.

## Not to be confused with the host-Java catalogue provider

The runtime also contains a separate, restricted host-Java provider (I082-G,
`ProtosHostJavaCatalogue` with `ProtosPolyglotRuntimeHost.openWithHostJava`)
that also uses the `java` scheme, over classes an embedder explicitly admits
(`import("java:<binary class name>")`). At this revision it is exercised only
by JUnit tests through internal bootstrap classes; the CLI does not offer it
and no standard-Context API registers it. This page does not document it as a
usable flow.

## References

- Fixture: [`JavaProvider.java`](../../../protos/tests/foreign-provider/plugins/java/src/i085b/java/JavaProvider.java);
- programs: [`java-math.protos`](../../../protos/tests/foreign-provider/java-math.protos)
  and [`java-date.protos`](../../../protos/tests/foreign-provider/java-date.protos);
- harness: [`dist/test_external_foreign_providers.py`](../../../dist/test_external_foreign_providers.py);
- host-Java catalogue provider:
  [`ProtosHostJavaProvider.java`](../../../src/main/java/com/guillermomolina/protos/execution/ProtosHostJavaProvider.java)
  and [`ProtosHostJavaProviderTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/ProtosHostJavaProviderTest.java);
- normative: [`VALUES_AND_COLLECTIONS.md`](../../../spec/semantics/VALUES_AND_COLLECTIONS.md)
  (Foreign Values) and [`MODULES.md`](../../../spec/semantics/MODULES.md)
  (Foreign module instances).
