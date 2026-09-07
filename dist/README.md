# Protos portable development distribution

This directory owns the build-time layout for `DIST001`.

It does not create a Git tag or GitHub Release. The resulting archive is a
development distribution until a later explicitly selected release candidate
passes DIST001-B/D/E.

## Build

From the repository root:

```sh
make dist
```

or:

```sh
python3 dist/build_portable.py
```

The build first packages the current Maven project, resolves the selected
optimizing Truffle runtime dependency closure, and creates:

```text
target/distributions/protos-<implementation-version>-posix-jvm.zip
```

The archive contains a relocatable root:

```text
protos-<version>/
├── bin/protos
├── lib/
│   ├── protos.jar
│   └── runtime/
├── protos/
│   ├── lib/
│   ├── tools/
│   ├── tests/
│   ├── examples/
│   └── tutorials/
├── LICENSE.TXT
├── README.md
├── SOURCE.txt
├── RUNTIME.txt
├── DEPENDENCIES.txt
└── SHA256SUMS
```

`bin/protos` sets `PROTOS_HOME` to the extracted distribution root but preserves
the caller's current working directory. Project-relative paths therefore remain
relative to the project invoking Protos rather than to the toolchain install.

## Initial runtime contract

DIST001-A deliberately uses the exact optimizing stack already retained by
`PERF002-B`:

```text
GraalVM Community Edition for JDK 22
org.graalvm.truffle:truffle-runtime:24.0.0
expected optimizing runtime: HotSpotTruffleRuntime
```

The project Maven bytecode target remains Java 21, but that is not by itself a
distribution support claim.

The extracted launcher defaults to rejecting a different Java feature version or
a runtime whose vendor metadata does not identify GraalVM. Developers may set:

```sh
PROTOS_ALLOW_UNSUPPORTED_RUNTIME=1
```

to experiment on another runtime, but that execution is outside the DIST001-A
supported runtime contract.

The repository development container may use a newer GraalVM/JDK. Development
build compatibility and end-user optimizing-runtime support are intentionally
different claims. Moving the distribution to a newer GraalVM/Truffle stack
requires explicit revalidation rather than silently following the build
container.

## DIST001 boundary

DIST001-A proves that the distribution can be constructed and that its archive
contains the required relocatable toolchain assets and exact source/runtime
metadata.

DIST001-B separately executes the extracted archive outside the repository
checkout and proves CWD-sensitive behavior plus required bundled facilities.
Until DIST001-B closes, this archive must not be described as a validated public
release.
