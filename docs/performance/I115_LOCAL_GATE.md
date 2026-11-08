# I115 — Autonomous semantic + structural gate

This implementation runs **entirely from `guillermomolina/protos`**. It does not need
`protos-benchmarks`, Podman, another checkout, or remote services at runtime.

## Budget and provenance

The original immutable `tools/baselines/i115-graal25.5.json` is preserved. Its
41 observed compiled roots correspond to 18 benchmark programs, of which 23
are Protos Closure roots and 18 are framework `Value.execute()` roots. Values
are compared **per root**, not summed. Every missing or newly compiled root
fails. For every guest root, its available `UNCACHED` / `CACHED` static Bytecode
DSL instruction count must have exactly the same coverage and not increase.

The original root hashes include their original Source URI. Local sources use
an explicit `i115:/cases/<case>.protos` URI to avoid filesystem-path dependence.
The gate matches the **frozen source's ascending semantic closure spans** to
its historical root order, checks identical observed labels across separate
JVM captures, and rejects missing, extra, ambiguous or non-Closure compiled
roots. It never matches roots on graph size or number of executed instructions.
This matching policy is suitable for the frozen workloads; it must be revised
if their source or root inventory changes rather than silently resetting the
reference. SHA-256 hashes freeze all 18 case source files.

The local gate reuses the original **Polyglot `Value.execute()` call shape**,
`FirstTierCompilationThreshold=100`, `LastTierCompilationThreshold=1000000`
(Tier 1) or `1000` (Tier 2), OSR off, no background compilation, 2001 calls,
and original real instrumentation shape for static Bytecode instructions
(one cold call plus 100 measured calls in a separate diagnostic JVM). The number of nodes is read from the
first `IR` counter in Graal `TraceCompilation`, **after TruffleTier**. This is
not a binary-BGV graph parser. BGV files must also be generated and nonempty,
and the collected counts must be compatible with the imported historical
baseline. If the Truffle version or trace semantics differ, the gate fails;
**do not change limits until the producer difference is explained**.

The script records git HEAD, dirty status, diff digest and SHA-256 of untracked
files. It **does not require a clean checkout**. Raw JVM logs, BGV dumps and
per-case derived records remain under `target/i115-semantic-performance-gate/`.
No timing result or improvement claim is inferred.

## Usage

From the Protos checkout (host or devcontainer, with the existing JDK 25 and
GraalVM 25.5.5.0.3 Maven build dependencies):

```sh
make test-semantic-performance-gate
make i115-performance-baseline
make i115-performance-probe
```

The first command runs Python-only unit tests; the second checks that the
already-imported baseline is present and valid without modifying it. The third
runs the **structural-only local measurement** and explicitly reports
`I115_GATE=NOT_RUN_SEMANTIC`: it cannot satisfy the complete gate.

The final mandatory gate is:

```sh
make semantic-performance-gate
```

It builds the current working tree, runs `make test` and `make check`, runs 54
fresh **local JVM** captures, verifies correctness for every invocation, checks
the complete 41-root budget, and retains an immutable report. It does not bump
`pom.xml` or `CHANGELOG.md`; the human's normal publication process applies.

If the compiler/JDK version or attribution fails, the gate fails closed. The
JVM/Truffle processor JAR must already exist in `~/.m2`, as for a normal Protos
build. This implementation performs no download.

## Scope and limitations

The 41-root budget covers **observed compiled roots**, not all interpreted
Core / initialization roots. The static DSL budget is enforced only for the
23 paired compiled guest roots, independently for both interpreter states
observed in the imported reference. Extra interpreted roots that never compile
are not covered by this baseline. A future broader budget needs original
historical evidence for those roots; it cannot be guessed from 41 rows.

A passing `make test` is not mathematical proof of all Actor/concurrency
semantics; adding focused semantic tests remains necessary. A failed graph
count can reflect an environment/producer mismatch; it is a refusal to assert
no regression until comparability is established, not proof of a regression.
