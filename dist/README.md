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

## Archive identity verification

`DIST001-B2` adds a non-executing archive verifier:

```sh
python3 dist/build_portable.py
python3 dist/verify_portable.py --require-clean-source
```

The verifier opens the ZIP directly. It checks the ZIP CRCs and one-root layout,
requires `SOURCE.txt` to identify the exact repository `HEAD` and a clean source
tree, and verifies that `SHA256SUMS` covers every distributed file except itself
with no missing, extra, duplicate, unsafe, or mismatched entries.

This slice deliberately does not extract or execute Protos.

## Outside-checkout CWD smoke

`DIST001-B3` validates relocation and caller-working-directory behavior:

```sh
python3 dist/build_portable.py
python3 dist/verify_portable.py --require-clean-source
sh dist/smoke_cwd_package.sh
```

The smoke first relies on B2's direct archive identity/checksum verification,
then extracts the ZIP into a temporary toolchain tree outside the Git checkout
and creates a distinct temporary project directory. From that project directory
it executes a relative `.protos` source path and runs `protos package manifest`
against a `protos.toml` that exists only in the caller project.

If the validation host matches the selected GraalVM Community JDK 22 contract,
the extracted launcher runs unchanged with the bundled optimizing runtime. If the
host does not match, B3 sets the documented unsupported-runtime override and
moves the optimizing-runtime JARs out of `lib/runtime` **only inside the
disposable extracted smoke copy**. This forces B3 to exercise the fallback
Truffle path instead of loading a runtime/JDK combination outside the selected
contract. The original archive is not modified.

This isolation is intentional: B3 owns relocation/CWD behavior, while
`DIST001-B4` owns the bundled Test Tool and exact `HotSpotTruffleRuntime` proof
for the selected optimizing-runtime contract. The smoke prints captured launcher
stdout/stderr before failing so a runtime or command failure is diagnosable.


## Bundled Test Tool smoke

`DIST001-B4A` validates that the Test Tool bundled in the extracted distribution
can bootstrap and execute its current bundled plan:

```sh
python3 dist/build_portable.py
python3 dist/verify_portable.py --require-clean-source
sh dist/smoke_test_tool.sh
```

The smoke extracts a disposable toolchain outside the checkout and invokes public
`protos test` from a separate temporary project directory. It requires the
current public Test Tool bootstrap marker and argument marker after successful
execution.

If the validation JDK does not match the selected GraalVM Community JDK 22
contract, B4A applies the same disposable-copy optimizer isolation established by
B3 and exercises fallback Truffle. That proves bundled Test Tool portability
without treating the host runtime as supported.

Exact optimizing-runtime validation is deliberately separate:
`DIST001-B4B` owns the intact optimizer classpath and exact
`com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime` proof.

## DIST001 boundary

DIST001-A proves that the distribution can be constructed and that its archive
contains the required relocatable toolchain assets and exact source/runtime
metadata.

DIST001-B separately executes the extracted archive outside the repository
checkout and proves CWD-sensitive behavior plus required bundled facilities.
Until DIST001-B closes, this archive must not be described as a validated public
release.
