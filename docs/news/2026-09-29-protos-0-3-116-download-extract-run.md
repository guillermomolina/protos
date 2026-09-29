# Protos 0.3.116: download, extract, run

**September 29, 2026**

Protos **0.3.116** is now available as a public prerelease with a self-contained
Native distribution for the declared Linux x86_64 / glibc 2.39+ target.

For users on that target, trying Protos no longer starts with installing a JDK,
GraalVM, Maven, or checking out the source tree. Download the archive, verify it,
extract it, and run `bin/protos`.

## The short path

```sh
curl -LO https://github.com/guillermomolina/protos/releases/download/v0.3.116/protos-0.3.116-native-linux-x86_64.zip
curl -LO https://github.com/guillermomolina/protos/releases/download/v0.3.116/protos-0.3.116-native-linux-x86_64.zip.sha256
sha256sum -c protos-0.3.116-native-linux-x86_64.zip.sha256
unzip protos-0.3.116-native-linux-x86_64.zip
./protos-0.3.116-native-linux-x86_64/bin/protos -e 'print("Hello, Protos!")'
```

The Native archive SHA-256 is:

```text
61fd90b39a43c574900b3c61d4fe2e65e100166ced66e495281b336f934883e8
```

## One release, two distribution roles

The release keeps two distribution artifacts from the same exact candidate:

- **Native** — the recommended first-run artifact on the declared supported
  Native target. It is self-contained with respect to Java/GraalVM and Maven.
- **Portable POSIX/JVM** — the compatibility fallback and external-consumer
  artifact. It requires the published GraalVM Community 25.4.4.1.1 / JDK
  25.0.4.1.1 runtime contract.

The portable archive remains useful when an external JVM runtime is the intended
integration boundary; it is no longer the recommended first step for a user who
can run the Native artifact.

## What the Native distribution carries

The published Native archive supports the ordinary user and tooling surfaces
validated for the release: version reporting, direct `-e` execution, source-file
execution, Package / Workspace execution, the Test Tool, REPL, language server,
and debug adapter.

It also retains the validated optimizing Truffle guest/runtime-compilation path;
this is not an interpreter-only packaging shortcut.

## Supported Native target

The Native support claim is intentionally narrow and explicit:

```text
OS                     Linux
architecture           x86_64
libc                   dynamic glibc
minimum glibc          2.39
build baseline         Oracle Linux 10
CPU ISA policy         compatibility
```

This release does **not** claim Native support for macOS, Windows, AArch64,
musl, or glibc older than 2.39.

## Prerelease status

Protos 0.3.116 remains an experimental **prerelease**, and the Protos/Core v0.1
specification remains draft. The Native artifact changes the first-run
experience; it is not a claim of stable-language status or broad platform
support.

## Try it

Download **Protos 0.3.116** from the
[GitHub Release](https://github.com/guillermomolina/protos/releases/tag/v0.3.116).

The maintained [Try Protos guide](../guide/00-try-protos.md) covers the Native
first-run path, the Dev Container, and the portable JVM fallback.
