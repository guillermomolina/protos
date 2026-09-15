# Try Protos

The quickest way to try Protos is to use the official Protos Dev Container. It
provides a ready-to-use environment with a pinned Protos release, its supported
GraalVM runtime, the official VS Code extension, canonical examples, and
debugging support.

You can also install the official Protos distribution manually if you prefer to
manage the runtime and installation yourself.

## Option 1: Protos Dev Container

This is the recommended path for getting started quickly.

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

The container already provides the selected Protos distribution, its supported
GraalVM runtime, and the official Protos VS Code extension. You do not need to
install another Protos runtime inside the container.

### Run a canonical example

The Dev Container includes a curated snapshot of examples from the corresponding
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

The Dev Container is intended to make this path work without installing the
language runtime or editor integration separately.

## Option 2: Manual installation

The official Protos release distribution is the supported starting point for a
manual installation.

Go to the Protos releases page:

https://github.com/guillermomolina/protos/releases

Select the release you want to install and read its release metadata before
choosing a runtime. Protos distributions are published against a specific
supported GraalVM/JDK stack; do not assume that an arbitrary Java installation
is equivalent.

Download the appropriate portable distribution and, when integrity information
is published with the release, verify the downloaded artifact before installing
it.

Extract the archive into a location of your choice. The distribution owns its
internal launcher and runtime layout, so keep the extracted distribution
structure intact.

Make its `bin/protos` launcher available from your shell, either by invoking it
directly or by adding an appropriate launcher location to your `PATH`.

Verify the installation:

```sh
protos --version
```

Then create:

```text
hello.protos
```

containing:

```protos
print("Hello, Protos!")
```

and run:

```sh
protos hello.protos
```

The official distribution launcher is the normal manual execution path. Direct
assembly of the implementation with commands such as `java -jar` is not
required for ordinary Protos use.

## Where to go next

Once the first program runs:

- read the [Protos Programming Guide](README.md);
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
