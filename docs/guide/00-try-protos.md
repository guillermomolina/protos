# Try Protos

The recommended first-run artifact on the declared Native target is the
self-contained **Protos 0.3.312 Native prerelease**. On supported Linux x86_64
systems with dynamic glibc 2.39 or newer, you can download one archive, verify
it, extract it, and run Protos without installing Java/GraalVM, Maven, or a
Protos source checkout.

The Protos Dev Container remains the preconfigured editor/debugging path, and
the portable POSIX/JVM artifact remains available as the compatibility fallback
for users who intentionally provide the supported external GraalVM/JDK runtime.

## Release status

Protos 0.3.312 is a public **prerelease** of the reference implementation:

| | |
|---|---|
| Release | [`v0.3.312`](https://github.com/guillermomolina/protos/releases/tag/v0.3.312) |
| Source revision | `f8ff34498f3a193c9bfe15f215a181c23504a6ad` |
| Core specification revision | `0.1.451` |
| Core language | v0.1 **draft** specification; experimental |

The release publishes exactly two archives, each with an adjacent `.sha256`
file, plus a `RELEASE_MANIFEST.txt`:

| Archive | Role | External Java |
|---|---|---|
| `protos-0.3.312-native-linux-x86_64.zip` | Recommended first run | Not required |
| `protos-0.3.312-posix-jvm.zip` | Compatibility fallback, Java embedding | GraalVM Community 25.4.4.1.1 / JDK 25.0.4.1.1 |

The version in this repository's `pom.xml` (`-SNAPSHOT`) identifies the
development line, not a published release. Only the archives attached to a
GitHub Release are public artifacts.

## Option 1: Self-contained Native release

This is the recommended first-run path when your host matches the published
Native platform contract.

### Supported target

The 0.3.312 Native artifact supports exactly:

- Linux;
- x86_64;
- dynamic glibc linkage;
- glibc 2.39 or newer;
- the compatibility CPU-ISA policy used by the release.

The release was built on an Oracle Linux 10 baseline. It does not claim Native
support for macOS, Windows, AArch64, musl, or glibc older than 2.39.

### Download and verify

Download the Native archive and its adjacent checksum from the
[Protos 0.3.312 GitHub prerelease](https://github.com/guillermomolina/protos/releases/tag/v0.3.312):

```sh
curl -LO https://github.com/guillermomolina/protos/releases/download/v0.3.312/protos-0.3.312-native-linux-x86_64.zip
curl -LO https://github.com/guillermomolina/protos/releases/download/v0.3.312/protos-0.3.312-native-linux-x86_64.zip.sha256
sha256sum -c protos-0.3.312-native-linux-x86_64.zip.sha256
```

The expected archive SHA-256 is:

```text
70f893d225419713b312054f3558e2e4b3f4169e8e7bd37e015aff92157d067b
```

Extract the archive:

```sh
unzip protos-0.3.312-native-linux-x86_64.zip
cd protos-0.3.312-native-linux-x86_64
```

The extracted directory is relocatable. Keep its internal layout intact.

### Run Protos

Verify the release:

```sh
./bin/protos --version
```

Evaluate source directly:

```sh
./bin/protos -e 'print("Hello, Protos!")'
```

Or create `hello.protos` containing:

```protos
print("Hello, Protos!")
```

and run:

```sh
./bin/protos hello.protos
```

You can invoke `bin/protos` directly from the extracted tree or add that `bin`
directory to your `PATH`. No external Java/GraalVM runtime or Maven is required
for this Native artifact after extraction.

The same published Native artifact also contains the supported REPL, Package /
Workspace execution, Test Tool, language server, and debug adapter surfaces.

### Native limitations

- Guest Protos code runs interpreter-only: Truffle guest JIT compilation is
  unavailable in the Native executable while
  [oracle/graal#14579](https://github.com/oracle/graal/issues/14579) remains
  unresolved (PLAT045). Program behavior is unchanged; long-running programs are
  slower than on the portable JVM.
- `--foreign-provider-path` is not available: the Native executable does not
  load plugins dynamically.
- Java embedding is not available; it needs the portable JVM runtime.

The full comparison is in
[The `protos` command line](tools/cli.md#native-and-portable-jvm).

## Option 2: Protos Dev Container

Use the Dev Container when you want a ready-to-use VS Code environment with the
supported runtime, official extension, canonical examples, and debugging
support already configured.

### Prerequisites

You need:

- Git;
- Docker or another environment supported by VS Code Dev Containers;
- Visual Studio Code;
- the VS Code Dev Containers extension.

Clone the Dev Container repository:

```sh
git clone https://github.com/guillermomolina/protos-devcontainer.git
cd protos-devcontainer
code .
```

Open the repository in its Dev Container when prompted by VS Code.

Once the container is ready, verify the installed Protos release:

```sh
protos --version
```

The container already provides its selected Protos distribution, supported
GraalVM runtime, and the official Protos VS Code extension. You do not need to
install another Protos runtime inside the container.

### Run a canonical example

The Dev Container includes a curated snapshot of examples from its corresponding
Protos release.

For example:

```sh
protos examples/basics/slots.protos
```

That program creates and updates a slot and prints the resulting value.

Browse `examples/README.md` for the available examples covering algorithms,
basics, closures, collections, concurrency, control flow, objects, and paths.

The examples bundled in the Dev Container are a convenience snapshot. Their
canonical source remains the main Protos repository.

### Write and debug your own program

You can create your own `.protos` files directly in the Dev Container workspace
and run them with the `protos` command.

For example, create `hello.protos` with:

```protos
print("Hello, Protos!")
```

and run:

```sh
protos hello.protos
```

Open a `.protos` file in VS Code and use the Protos debugging support provided
by the official extension.

## Option 3: Portable POSIX/JVM fallback

The same 0.3.312 prerelease also publishes:

```text
protos-0.3.312-posix-jvm.zip
```

This artifact is the compatibility fallback and external-consumer artifact. It
is **not self-contained**: it requires the supported external runtime contract
published with the release:

```text
GraalVM Community 25.4.4.1.1
JDK 25.0.4.1.1
```

Do not treat Java bytecode release 21 as a generic Java 21+ compatibility claim.
Use the exact release metadata when provisioning the runtime.

Download the portable archive and its adjacent `.sha256` file from the
[0.3.312 release](https://github.com/guillermomolina/protos/releases/tag/v0.3.312),
verify it with `sha256sum -c`, extract it, keep the distribution layout intact,
and use its `bin/protos` launcher. The expected portable archive SHA-256 is:

```text
ab9cc9de494cc9189eb29ad2f2f110e53997c49adcb1d863814ddd5b89ce6d29
```

For example:

```sh
curl -LO https://github.com/guillermomolina/protos/releases/download/v0.3.312/protos-0.3.312-posix-jvm.zip
curl -LO https://github.com/guillermomolina/protos/releases/download/v0.3.312/protos-0.3.312-posix-jvm.zip.sha256
sha256sum -c protos-0.3.312-posix-jvm.zip.sha256
unzip protos-0.3.312-posix-jvm.zip
cd protos-0.3.312
```

The launcher uses `$PROTOS_JAVA` when set, otherwise `$JAVA_HOME/bin/java`,
otherwise `java` from `PATH`. Point it at the supported GraalVM:

```sh
export JAVA_HOME=/path/to/graalvm-community-25.4.4.1.1
./bin/protos --version
./bin/protos -e 'print("Hello, Protos!")'
```

By default the launcher rejects a runtime that does not match the published
contract; the extracted `RUNTIME.txt` records it. The portable JVM keeps the
optimizing runtime with guest JIT, and it is the artifact to use for
[Java embedding](embedding/java.md) and
[external foreign providers](interop/custom-provider.md).

For ordinary first use on the declared Native target, prefer Option 1 so no
external JVM/runtime provisioning is required.

## Where to go next

Once the first program runs:

- read the [Protos Programming Guide](README.md);
- use [The `protos` command line](tools/cli.md) for `run`, `format`, `lint`,
  `debug`, and exit codes, and the [Test Tool](tools/test-tool.md) for
  `protos test`;
- browse the [Standard Library guides](library/README.md);
- read [Process, I/O, Filesystems, and Authority](11-process-io-filesystems-and-authority.md#what-each-launcher-grants)
  to see why a standalone program has no default Filesystem or Network;
- embed Protos from Java with the [embedding guide](embedding/java.md)
  (portable JVM only);
- explore the canonical [tutorials](../../protos/tutorials/README.md);
- explore the task-oriented [examples](../../protos/examples/README.md);
- use the
  [Protos Dev Container](https://github.com/guillermomolina/protos-devcontainer)
  for a preconfigured editor/runtime environment;
- use the
  [official Protos VS Code extension](https://github.com/guillermomolina/protos-vscode-extension)
  for language and debugging integration.

The normative language specification remains under [`../../spec/`](../../spec/).
This guide explains how to get started with the published implementation; it
does not redefine Protos language semantics.
