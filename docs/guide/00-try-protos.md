# Try Protos

The recommended first-run method on supported Linux is the **version-independent
Native Image installer**. It finds a compatible published Native prerelease,
verifies the official distribution, and activates Protos in your home directory.
It does not require Java, GraalVM, Maven, root access, or a source checkout.

The Dev Container remains the preconfigured editor/debugging path. The portable
JVM release remains a separately installed distribution for users who need its
optimizing runtime, Java embedding, or foreign providers.

## Published releases

Protos publishes prereleases on its [GitHub Releases page](https://github.com/guillermomolina/protos/releases).
The installer uses the published release list, not the GitHub `releases/latest`
shortcut (which excludes prereleases), and does not hard-code a Protos version.
For a supported host it selects the newest compatible Native release by numeric
version, then checks the release's `RELEASE_MANIFEST.txt`, adjacent SHA-256,
archive contents, source identity, and Native runtime contract before activation.
An explicitly requested version never falls back to another version.

Published releases continue to have a Native ZIP and a separate portable JVM
ZIP, each with an adjacent `.sha256` file, plus the official release manifest.
The development `pom.xml` version is not a published release identifier.

## Option 1: Linux Native installer (recommended)

### Supported target and requirements

The current Native target is **Linux x86_64 with dynamic glibc**, with a
published baseline of **glibc 2.39 or newer**. Each release manifest determines
its precise minimum; the installer can select an older published Native release
if a newer one requires a newer glibc. There is no claim of Native support for
macOS, Windows, AArch64, musl, or older glibc without published evidence.

The host needs common command-line tools including `sh`, `curl`, `awk`, `unzip`,
`sha256sum`, and `flock`. Missing commands or an unsupported runtime cause an
actionable error. The installer never upgrades glibc, installs system packages,
or modifies shell startup files automatically.

### One-command install or update

Run the stable installer URL on the supported host:

```sh
curl -fsSL https://raw.githubusercontent.com/guillermomolina/protos/main/dist/install.sh | sh
"$HOME/.local/bin/protos" --version
"$HOME/.local/bin/protos" -e 'print("Hello, Protos!")'
```

Re-running the first command checks for a newer eligible Native release. It
does not silently downgrade an active installation. The whole verified Native
tree, including its Standard Library, tools, and tests, stays together under
`~/.local/share/protos/versions/`; the managed launcher lives at
`~/.local/bin/protos`. No Java/JAR is installed.

If the installer reports that `~/.local/bin` is not on your shell `PATH`, you
can use the absolute command shown above or update your current shell manually:

```sh
export PATH="$HOME/.local/bin:$PATH"
protos --version
```

### Inspect before running

Piping a remotely downloaded script directly into `sh` requires trusting its
current contents. To examine it first, download it as a file, review it, and
use the same downloaded bytes for every subsequent action:

```sh
curl -fsSLo protos-install.sh https://raw.githubusercontent.com/guillermomolina/protos/main/dist/install.sh
less protos-install.sh
sh protos-install.sh --list-releases
sh protos-install.sh --inspect
sh protos-install.sh --verify-download
sh protos-install.sh
```

`--inspect` selects the release and reports its platform/runtime identity
without installing it. `--verify-download` additionally downloads and checks
the Native checksum and ZIP without activating anything. Neither command
requires Java or Maven.

### Pin, roll back, and uninstall

To choose a specific **published numeric** tag, assign it to `TAG` (for example,
`TAG=v0.3.398`) before using these commands:

```sh
sh protos-install.sh --tag "$TAG"
sh protos-install.sh --rollback --tag "$TAG"
sh protos-install.sh --uninstall
```

`--tag` installs only that published Native version and rejects an unsupported
host or invalid release. `--rollback` operates offline and activates only a
previously installed, verified Native tree. `--uninstall` deactivates and
removes the installer-managed `protos` command but **retains versioned trees**
for recovery; it does not purge them or remove unrelated user files.
An existing command not owned by this installer is never replaced or removed.

### Run Protos from another directory

Create `hello.protos` in any working directory with this content:

```protos
print("Hello, Protos!")
```

Run the file without changing to the installation directory:

```sh
"$HOME/.local/bin/protos" hello.protos
```

The same Native distribution also bundles the supported REPL, Package /
Workspace execution, Test Tool, language server, and debug adapter surfaces.

### Manual Native archive alternative

You can still install without the script: select a compatible published release
on [GitHub Releases](https://github.com/guillermomolina/protos/releases),
download its `protos-VERSION-native-linux-x86_64.zip` and adjacent `.sha256`
asset, run `sha256sum -c` on the checksum file, extract the ZIP, and invoke the
extracted `bin/protos`. Keep the extracted tree intact: the launcher needs
its companion resources. Consult the selected release's manifest for its
exact checksum and host contract; do not reuse a checksum from another release.

### Native limitations

- Guest Protos code currently runs interpreter-only: Truffle guest JIT
  compilation is unavailable in the Native executable while
  [oracle/graal#14579](https://github.com/oracle/graal/issues/14579) remains
  unresolved (PLAT045). Long-running programs are slower than on the portable
  JVM, but the Native distribution remains independently executable.
- `--foreign-provider-path` is unavailable in Native: dynamic Java-provider
  plugins require the portable JVM release.
- Java embedding also requires the portable JVM distribution.

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

## Option 3: Portable POSIX/JVM distribution (manual)

The separately published portable ZIP (`protos-VERSION-posix-jvm.zip`) is for
the optimizing GraalVM runtime, Java embedding, and supported external foreign
providers. It is **not self-contained** and is **never** installed or chosen as
a fallback by `dist/install.sh`.

Select the desired release on [GitHub Releases](https://github.com/guillermomolina/protos/releases).
Download its portable ZIP and adjacent `.sha256` file, verify the digest with
`sha256sum -c`, extract the ZIP, and retain the distribution layout. Obtain the
exact required GraalVM/JDK identity from that release's manifest and extracted
`RUNTIME.txt`; do not assume any generic Java 21+ or arbitrary JVM works.

The portable launcher uses `$PROTOS_JAVA` when present, otherwise
`$JAVA_HOME/bin/java`, otherwise `java` on `PATH`. Configure the supported
release-specific GraalVM before invoking the extracted `bin/protos`.

The portable distribution keeps the guest JIT where supported and is the
appropriate artifact for [Java embedding](embedding/java.md) and
[external foreign providers](interop/custom-provider.md).

For ordinary first use on the declared Native target, prefer Option 1: it
does not require external JVM provisioning.

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
