# PLAT033 — optimizing Truffle runtime dependency and packaging authority

Status: **RATIFIED — Candidate A′ selected**

Nature: durable non-normative JVM/Truffle dependency, launcher and packaging architecture decision

Approved by project owner: **2026-09-13**

GitHub Issue: **#483**

Primary consumer: `PERF006-C` / GitHub #482.

Normative effect: **none**. PLAT033 changes no Protos language semantics, object model, execution semantics, Task/Future/Actor/Process behavior, Error/control behavior, standard-library contract, source/tooling semantics or scheduler policy. It selects only the dependency and packaging authority through which ordinary intended JVM execution receives the selected optimizing Truffle runtime.

## Decision

Select **Candidate A′ — one exact-version external Graal/Truffle runtime authority, upstream runtime artifacts intact, module-oriented consumption preferred, no accidental optimizer flattening into `protos.jar`**.

The durable architecture is:

```text
one canonical Graal / Truffle / Polyglot runtime authority
                     |
          exact coordinates / closure
                     |
        +------------+-------------+
        |            |             |
        v            v             v
 Maven/Surefire   checkout      distribution
                    runtime
        |            |             |
        +------------+-------------+
                     |
                     v
          intact upstream runtime jars
        module-path preferred where viable
```

The Protos application/language artifact remains distinct:

```text
protos.jar
    -> Protos application/language code
    -> application dependencies intentionally owned by that artifact
    -> NOT accidental owner of the optimizing Truffle runtime closure
```

In particular, `truffle-runtime`, `truffle-compiler` and the optimizer closure must not enter `protos.jar` merely because Maven scope selection feeds Maven Shade.

## One authority

There must be exactly one canonical repository authority for the current Graal/Truffle/Polyglot coordinates and optimizing runtime closure.

Maven/Surefire, checkout execution, portable distribution, CI validation and future packaging work must consume or mechanically derive from that authority. A second independently edited runtime POM or launcher-specific coordinate list may exist only as generated/mechanically verified projection, not as a competing source of truth.

The current `dist/runtime-pom.xml` therefore may be retained only if PERF006-C makes it mechanically subordinate to the canonical authority or replaces it with an equivalent single-authority mechanism.

Exact zero-drift remains mandatory.

## Runtime artifacts stay intact

Graal/Truffle runtime artifacts remain upstream artifacts:

- no relocation of `org.graalvm.*` or `com.oracle.truffle.*`;
- no flattening that discards their module descriptors as the normal architecture;
- no rewriting their package identity;
- no repackaging merely to recover `java -jar` convenience;
- no launcher dependency on user-local Maven repository paths.

This preserves upstream dependency identity, service metadata, module boundaries, upgrade diagnostics and the future Native Image/AOT path.

## Module-oriented runtime plane

The preferred durable runtime placement for Graal/Truffle components is the Java module path.

PLAT033 does **not** require Protos itself to become a JPMS module in PERF006-C1. A bounded hybrid launcher is allowed:

```text
Protos/application code
    -> class path while still non-modular

Graal/Truffle runtime components
    -> module path where the selected dependency graph supports it
```

A temporary class-path projection for a specific surface is acceptable only when it remains mechanically derived from the same canonical runtime authority and does not become a second packaging architecture.

Future conversion of Protos itself to JPMS is deliberately not required by PLAT033.

## Maven/Surefire

Ordinary Maven/Surefire execution is an intended optimizing-runtime surface after PERF006 closes.

PERF006-C may therefore make the selected optimizer closure available to Surefire/test execution, but it must do so without causing Maven Shade to absorb that closure into `protos.jar`.

The dependency graph used by tests and the dependency graph materialized for checkout/distribution must remain the same canonical runtime family and exact version.

Fallback-warning suppression is not a substitute for runtime availability.

## Checkout execution

Normal checkout `bin/protos` is also an intended optimizing-runtime surface.

PERF006-C may materialize a repository-managed checkout runtime directory such as conceptually:

```text
target/runtime/*
```

or another bounded generated runtime location, provided that:

- it is derived from the canonical runtime authority;
- it is reproducible;
- it is not committed as vendored binary state unless separately approved;
- it does not depend on `~/.m2` at launch time;
- `bin/protos` can prove the intended runtime identity;
- distribution and checkout do not acquire divergent runtime semantics.

Exact path/name is implementation detail unless later evidence exposes a durable choice.

## Portable distribution

The existing portable layout remains conceptually valid:

```text
lib/protos.jar
lib/runtime/*
```

PLAT033 strengthens its authority model rather than discarding it.

PERF006-C must reconcile distribution materialization so that `lib/runtime/*` is produced from the same canonical runtime closure used for Maven/Surefire and checkout execution.

`RUNTIME.txt`, exact GraalVM/JDK/Truffle version checks, `HotSpotTruffleRuntime` evidence and DIST002 zero-drift guarantees remain mandatory.

DAP/tool runtime dependencies must continue to compose with this runtime plane without becoming a second coordinate authority.

## Native Image / AOT

PLAT033 deliberately preserves Native Image/AOT as a future path.

It therefore rejects a durable architecture whose convenience depends on flattening the complete Graal/Truffle runtime into an application fat JAR and deleting module information.

No Native Image implementation is authorized here. The requirement is architectural non-regression: ordinary JVM packaging must not unnecessarily make later native packaging harder.

## Host-specific fallback

The comparative audit found valid environments where a Truffle language may intentionally use fallback execution because host/classloader/native-library constraints differ from a normal language launcher.

PLAT033 therefore does not state that every conceivable embedder must always load `HotSpotTruffleRuntime`.

Instead:

- Maven/Surefire: intended optimizer surface;
- normal checkout CLI: intended optimizer surface;
- portable distribution: intended optimizer surface;
- CI/runtime validation: must prove those identities;
- any future host that intentionally requires fallback must obtain explicit documented authority for that host.

Ordinary Protos execution must not remain fallback-by-default after PERF006 closes.

## Comparative implementation audit

The approval followed an exhaustive review of the current Truffle language catalogue and materially relevant historical implementations.

The scores below evaluate each implementation as a **precedent for PLAT033**, not as a score for the language itself.

| Implementation | Future endurance | Scalability | Protos philosophy | PLAT033 reading |
| --- | ---: | ---: | ---: | --- |
| GraalJS | 10.0 | 10.0 | 9.5 | primary modern Oracle precedent |
| GraalPy | 10.0 | 9.5 | 9.0 | primary modern precedent plus native distribution evidence |
| GraalWasm | 10.0 | 10.0 | 9.5 | strong modular language-closure precedent |
| Espresso | 9.5 | 9.0 | 8.5 | strong standalone/runtime-resource precedent |
| Sulong / LLVM | 9.5 | 9.0 | 8.5 | strong explicit runtime/resource-boundary precedent |
| Enso | 9.5 | 9.5 | 10.0 | closest external component/runtime-plane precedent |
| TruffleRuby | 9.5 | 9.0 | 8.5 | strong JVM/native standalone separation |
| TruffleSqueak | 8.5 | 8.5 | 8.0 | useful JVM/native standalone evidence |
| TRegex | 9.0 | 10.0 | 9.0 | strong supplied-runtime/modular-service evidence |
| SimpleLanguage principle | 8.5 | 8.5 | 9.0 | separation principle retained; old loading mechanics historical |
| Apple Pkl JVM fat-JAR path | 7.0 | 8.0 | 6.5 | serious counterexample, not selected |
| Apple Pkl native path | 9.0 | 9.0 | 8.0 | strong evidence for keeping native packaging separate |
| FastR | 7.0 | 7.5 | 6.5 | distribution-layer lesson, less transferable mechanics |
| grCUDA historical deployment | 3.0 | 5.0 | 3.5 | negative evidence for language-home/JDK mutation |
| SOMns historical deployment | 3.5 | 4.0 | 5.5 | useful runtime research, obsolete packaging precedent |
| TruffleSOM historical deployment | 3.5 | 4.0 | 5.0 | historical packaging only |
| Yona historical deployment | 2.5 | 3.0 | 4.5 | archived/historical negative evidence |

### Modern Oracle/Graal family

GraalJS, GraalPy, GraalWasm, Espresso and Sulong provide the strongest upstream-supported direction: language/runtime dependencies are ordinary artifacts with module-oriented composition rather than language-home installation or arbitrary embedder fat-JAR flattening.

For PLAT033 the important property is not a particular aggregate artifact name. It is the durable separation:

```text
embedder/application
    !=
language implementation
    !=
optimizing runtime closure
```

while the dependency graph still composes reproducibly.

### Enso

Enso is the closest architectural analogue to Protos' existing `lib/runtime/*` direction.

It explicitly models Graal components such as Polyglot, Truffle API/runtime/compiler as a runtime/component plane and keeps embedder-side and language-side authority separate.

Its larger manual dependency inventory is more complexity than Protos currently needs, but the authority boundary strongly supports A′.

### Apple Pkl

Pkl is the strongest counterexample and was evaluated as such, not dismissed.

Its Java CLI deliberately includes `truffle-runtime` in runtime dependencies and builds/tests a fat executable JAR. Its build also has to treat Graal/Truffle packages specially, remove module descriptors, handle multi-release/service-file details and maintain a distinct Native Image path.

That demonstrates that fat-JAR packaging can be engineered successfully when self-contained `java -jar` is a product requirement.

For Protos, however:

- `bin/protos` already exists;
- portable distribution already has `lib/runtime/*`;
- runtime identity is already explicit;
- Native Image/AOT should remain easy to pursue;
- one external runtime authority removes rather than adds architecture.

Pkl therefore validates Candidate C as real but does not outweigh A′ for Protos.

Pkl's host-specific fallback behavior in Gradle also supplies an important separate lesson: fallback may be appropriate for a constrained embedder, but should be an explicit host policy, not accidental absence of the optimizing runtime.

### TruffleRuby, GraalPy and TruffleSqueak

Their JVM/native standalone split demonstrates that an intact JVM runtime closure and future native executable are complementary product forms.

Protos does not need to flatten the JVM optimizer into `protos.jar` in order to preserve a simple future native distribution.

### Historical language-home systems

grCUDA, SOMns, TruffleSOM, Yona and older SimpleLanguage-era loading patterns reflect earlier Truffle packaging approaches: JDK/language-home mutation, copied classpaths or close `mx` tree coupling.

They are useful negative evidence because the modern Truffle ecosystem has moved away from making those installation locations the primary dependency authority.

PLAT033 therefore prohibits depending on GraalVM installation mutation as the Protos runtime-closure model.

## Candidate comparison

### Candidate A′ — one external modular runtime authority

**Selected.**

- Future endurance: **10/10**
- Scalability: **10/10**
- Protos philosophy: **10/10**

Strengths:

- one source of truth;
- explicit runtime identity;
- same closure across tests, checkout and distribution;
- no accidental Shade ownership;
- upstream artifacts remain inspectable;
- module evolution remains open;
- Native Image/AOT remains open;
- no user-machine Maven path at execution time;
- existing `lib/runtime/*` design is reused instead of discarded.

### Candidate B — external runtime plane but permanent flat class path

Rejected as the durable target, though a bounded implementation projection may be used where required.

- Future endurance: **8.5/10**
- Scalability: **9.5/10**
- Protos philosophy: **9/10**

It preserves most authority benefits but intentionally gives up some JPMS/module-integrity evolution.

### Candidate C — fat JAR containing optimizer runtime, Apple-Pkl-style

Rejected for Protos.

- Future endurance: **6.5/10**
- Scalability: **8/10**
- Protos philosophy: **6/10**

It is viable engineering, but Protos already owns a runtime-directory launcher and would gain little from flattening while paying module/AOT/build-special-case costs.

### Candidate D — root POM and distribution runtime POM as independent authorities

Rejected.

- Future endurance: **5/10**
- Scalability: **6.5/10**
- Protos philosophy: **4/10**

It permits silent coordinate/closure drift and makes every future runtime upgrade a multi-authority reconciliation problem.

### Candidate E — rely on installed GraalVM / language homes

Rejected.

- Future endurance: **2/10**
- Scalability: **5/10**
- Protos philosophy: **2.5/10**

It couples Protos to machine installation mutation and historical Truffle deployment mechanics.

### Candidate F — optimizer only in distribution, fallback in development/tests

Rejected.

- Future endurance: **2/10**
- Scalability: **7/10**
- Protos philosophy: **2/10**

It recreates the exact PERF006 integrity problem: development/test behavior and shipped behavior run on materially different execution runtimes.

### Candidate G — make Protos Native-Image-only now

Rejected for this decision.

- Future endurance: **9/10**
- Scalability: **8.5/10**
- Protos philosophy: **7/10 at current maturity**

It may become a future distribution option, but forcing it now unnecessarily broadens PERF006 and removes the useful JVM development/embedding surface.

## Ratified invariants

```text
one canonical runtime coordinate/closure authority        YES
same runtime semantics across Maven/checkout/dist         YES
exact Graal/Truffle version zero-drift                    YES

upstream Graal/Truffle runtime jars intact                YES
module-path preferred                                     YES
bounded hybrid classpath + module-path allowed            YES

truffle-runtime accidentally shaded into protos.jar       NO
truffle-compiler accidentally shaded into protos.jar      NO
independent drifting dist runtime authority               NO
launcher dependency on ~/.m2                              NO
GraalVM installation/language-home mutation               NO
fallback warning suppression as fix                       NO

Maven/Surefire intended optimizer surface                 YES
checkout CLI intended optimizer surface                   YES
portable distribution intended optimizer surface         YES
future Native Image/AOT path preserved                    YES
```

## Consequence for PERF006-C

PLAT033 releases PERF006-C from its architecture block.

PERF006-C1 may now implement runtime-closure materialization, but only within A′:

1. establish one canonical runtime dependency authority;
2. make Maven/Surefire consume the exact optimizing closure;
3. ensure Shade does not absorb the optimizer closure into `protos.jar`;
4. materialize a reproducible checkout runtime projection;
5. update `bin/protos` only as required to consume that generated runtime plane;
6. prove exact `HotSpotTruffleRuntime` identity for Maven/Surefire and checkout execution;
7. leave distribution reconciliation to the bounded C2 slice unless C1 can reuse the authority without broadening scope.

No Protos semantics, scheduler behavior or Graal version change is authorized.

## Deliberately deferred

PLAT033 does not decide:

- exact canonical metadata file/POM name;
- exact `target/runtime` directory spelling;
- whether the canonical dependency authority is represented by Maven dependency management, a dedicated runtime POM, generated metadata or an equivalent single-authority repository mechanism;
- full JPMS modularization of Protos;
- Native Image implementation;
- future Graal/Truffle upgrade cadence;
- support policy for third-party embedders;
- installer/package-manager formats;
- whether a later release ships both JVM and native executable forms.

Those remain implementation/release decisions unless later evidence exposes another durable architecture choice.
