# Protos Native Image: a native executable with Truffle guest compilation

**September 26, 2026**

Protos can now be built as a GraalVM Native Image while retaining the Truffle
optimizing runtime. The resulting native `protos` executable runs Protos
programs and can compile guest code at runtime.

This brings the optimizing execution path into a native executable alongside
the existing JVM development and runtime path.

## Build and test

From the root of a current Protos checkout, with the project's GraalVM Native
Image and native build prerequisites available, run:

```sh
make -C build/native build
```

The executable is produced at `target/native/protos`. Native developer tooling
lives under `build/native/`.

To rebuild the executable and run the Native regression suite:

```sh
make -C build/native test
```

The maintained [Native Image instructions](../../README.md#native-image) are
the entry point for current commands. The test target includes the native
build; running both commands is unnecessary when the goal is validation.

## What was validated

The accepted implementation built successfully and passed `--version`,
`--help`, and basic Protos execution checks. Under forced Truffle guest
compilation, both generated Protos Bytecode DSL roots reached Tier 2,
with `OPT_FAILED=0` and no recurrence of the `FrameWithoutBoxing`
materialization failure in that validation.

This establishes that the native executable has working guest runtime
compilation, rather than merely starting an interpreter packaged as a native
binary. The retained regression suite checks CLI execution and compilation of
both roots so this capability can be tested again as Protos evolves.

The original accepted runtime-compilation evidence is pinned to Protos
[`7f033b059e2e25a1a6d1776e7eb1f960f7ab814e`](https://github.com/guillermomolina/protos/commit/7f033b059e2e25a1a6d1776e7eb1f960f7ab814e).
The developer commands above are published in
[`d65445aa5bb008903b1a580a0ee2acae46cf2a12`](https://github.com/guillermomolina/protos/commit/d65445aa5bb008903b1a580a0ee2acae46cf2a12).

## Scope and current limitations

Native Image is an additional build capability. The ordinary JVM path remains
available, and native builds are separate from portable distribution creation
and release publication.

This announcement does not publish an official downloadable native release or
establish a supported native platform matrix. It also does not establish
production readiness or full native feature/conformance parity. The external
Protos resource tree remains part of the architecture; this is not a promise
of a complete, self-contained single-file toolchain.

Successful guest compilation is not a performance benchmark. No startup,
memory, or throughput improvement is claimed here, and this milestone does
not establish that the existing Protos performance gap has been closed.

## Technical evidence

- [Native Image implementation and closure](https://github.com/guillermomolina/protos/issues/711#issuecomment-5843249816)
- [Retained implementation evidence](https://github.com/guillermomolina/protos-project-docs/blob/69e72ca85a9d8d9c1af596a7d027900fb05928e2/docs/project/evidence/I069/I069_NATIVE_IMAGE_GUEST_RUNTIME_COMPILATION_IMPLEMENTATION.md)
- [Protos repository](https://github.com/guillermomolina/protos)
