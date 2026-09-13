# LIB012 — Semantic Versioning Standard Library design

Status: **IN_PROGRESS — LIB012-0 A′ RATIFIED; LIB012-A READY**

Owning work item: GitHub Issue `#429` — `LIB012 — Semantic version parsing, comparison and compatibility utilities`

Research/decision sub-item: GitHub Issue `#481` — `LIB012-0 — Semantic Versioning prior-art and Standard Library design audit`

Nature: project Standard Library design record; **non-normative**

Explicit project-owner approval: **2026-09-13**

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Purpose

This record closes the LIB012-0 exhaustive comparative research checkpoint and
records the selected initial architecture for a public Semantic Versioning
facility in the Protos Standard Library.

The selected architecture is **Candidate A′ — strict SemVer 2.0.0 Version kernel
only**.

The public library boundary is deliberately narrower than a package manager:

```text
semantic version value / syntax / precedence
                    !=
dependency requirement language
                    !=
package-manager resolution policy
```

LIB012 therefore standardizes strict Semantic Versioning value semantics without
promoting Cargo/npm/NuGet/Maven/PEP 440/TOOL001 dependency policy into generic
Standard Library behavior.

## Existing Protos pressure

Package Tool already contains ordinary-Protos implementations that prove the
basic mechanism is viable without a host bridge:

- `protos/tools/package/ReleaseVersion.protos` parses and compares
  `MAJOR.MINOR.PATCH[-PRERELEASE]` using SemVer 2.0.0 precedence and arbitrary-
  precision Protos Integer components;
- `protos/tools/package/DependencyConstraint.protos` owns package-specific exact,
  caret and bounded-interval requirements plus prerelease admission;
- `docs/design/PACKAGE_IDENTITY_VERSIONING.md` intentionally rejects build
  metadata for published Package Tool `ReleaseVersion` values;
- `docs/design/PACKAGE_VERSION_RESOLUTION.md` keeps requirement syntax and
  resolution policy separate from version identity.

Those are implementation and domain evidence, not an already-public general
SemVer contract. In particular, Package Tool's rejection of `+BUILD` is a
package-release policy restriction, while SemVer 2.0.0 itself permits build
metadata.

## Normative external standard baseline

LIB012-A targets Semantic Versioning 2.0.0 syntax:

```text
MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]
```

with the SemVer 2.0.0 invariants relevant to the public value:

- exactly three non-negative decimal core components;
- no leading zeroes in multi-digit core components;
- prerelease identifiers are non-empty ASCII `[0-9A-Za-z-]` components separated
  by `.`;
- numeric prerelease identifiers have no leading zeroes;
- build identifiers use the same ASCII character family, are non-empty, and may
  be numeric with leading zeroes;
- precedence compares major/minor/patch numerically and then prerelease according
  to SemVer 2.0.0;
- numeric prerelease identifiers sort below non-numeric identifiers;
- build metadata does **not** participate in precedence;
- SemVer itself does not define a dependency-range language;
- SemVer itself imposes no `u64`, JavaScript-safe-integer, machine-word, or
  fixed-decimal-digit ceiling on numeric components.

Primary reference: <https://semver.org/spec/v2.0.0.html>

## Exhaustive comparative prior-art audit

The research deliberately covered both strict-SemVer implementations and
non-SemVer version systems. Scores below measure fitness as a direct precedent
for LIB012, not the quality of each ecosystem for its own requirements.

### Rust `semver`

Rust provides one of the strongest direct precedents: a structured Version value,
strict parsing, explicit prerelease/build fields, and a dedicated
`cmp_precedence` relation that ignores build metadata as SemVer requires.
`VersionReq` is a separate concept.

The main Protos mismatch is the host-level `u64` cap on major/minor/patch. Protos
already has arbitrary-precision Integer and should not inherit that accidental
limit.

Reference: <https://docs.rs/semver/latest/semver/>

Focused scores: future **4.5/5**, scale **5/5**, Protos fit **4.5/5**.

### Elixir `Version`

Elixir is architecturally close to the desired split: Version and Requirement are
separate concepts, build metadata is preserved, and precedence follows SemVer.
Its practical numeric-component limit is an implementation/API constraint rather
than a SemVer requirement and is not needed in Protos.

Reference: <https://hexdocs.pm/elixir/Version.html>

Focused scores: future **4.5/5**, scale **5/5**, Protos fit **4.5/5**.

### Swift Package Manager

SwiftPM provides strict semantic-version package requirements and keeps version
selection operations such as exact/up-to-next-major/up-to-next-minor distinct
from the underlying version identity. It is strong evidence for keeping package
selection policy out of the Version kernel.

Reference: <https://docs.swift.org/package-manager/PackageDescription/PackageDescription.html>

Focused scores: future **4.5/5**, scale **4.5/5**, Protos fit **4/5**.

### Go `x/mod/semver` and Go Modules

Go demonstrates a useful two-layer architecture: a compact comparison kernel plus
module-specific conventions such as leading `v`, partial forms, pseudo-versions,
major-version path suffixes and `+incompatible`. Those conventions are valuable
in Go but are not SemVer 2.0.0 syntax and should remain ecosystem adapters rather
than become LIB012 baseline behavior.

References:

- <https://pkg.go.dev/golang.org/x/mod/semver>
- <https://go.dev/ref/mod>

Focused scores: future **4/5**, scale **5/5**, Protos fit **3.5/5**.

### npm / Node `semver`

`node-semver` is the richest SemVer range prior art in the survey. It supports
primitive comparators, conjunction, union, hyphen ranges, X-ranges, tilde,
caret, partial versions, prerelease admission, loose parsing and coercion.

That maturity is useful evidence for a possible future Requirement layer, but it
also demonstrates why range algebra is a substantially larger semantic system
than Version parsing/comparison. `clean`, `coerce`, loose parsing and npm range
syntax are intentionally not selected for the LIB012 Version kernel.

Reference: <https://github.com/npm/node-semver>

Focused scores: future **4/5**, scale **3.5/5**, Protos fit **3/5**.

### Cargo dependency requirements

Cargo uses SemVer values but owns package-specific requirement semantics. A bare
`1.2.3` dependency is caret-compatible rather than exact; tilde, wildcard and
comparison forms are supported; prereleases require explicit admission rules.

This directly conflicts with current TOOL001, where a bare full version is exact.
The conflict is decisive evidence that there is no universal "SemVer range"
syntax for LIB012 to copy.

Reference: <https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html>

Focused scores as a generic Requirement precedent: future **4/5**, scale
**4.5/5**, Protos fit **3/5**.

### NuGet

Modern NuGet supports SemVer 2.0.0 but overlays repository/package compatibility
rules including normalization, historic four-component compatibility and its own
interval range syntax. Those are NuGet domain semantics, not strict SemVer
parsing rules.

Reference: <https://learn.microsoft.com/nuget/concepts/package-versioning>

Focused scores: future **3.5/5**, scale **4.5/5**, Protos fit **2.5/5**.

### Composer

Composer combines SemVer-oriented versions with a rich package requirement
language, stability policy, caret/tilde/wildcard/comparator/union forms and
package-manager-specific normalization. It is useful evidence for future package
constraint ergonomics but too policy-heavy for a generic Version kernel.

Reference: <https://getcomposer.org/doc/articles/versions.md>

Focused scores: future **3.5/5**, scale **4/5**, Protos fit **2.5/5**.

### Python packaging / PEP 440

PEP 440 is intentionally **not SemVer**. It adds epochs, arbitrary release
segments, pre/post/dev releases, local versions, normalization and a separate
specifier language.

Its importance to LIB012 is architectural: it disproves the idea that one generic
`std:version/Version` can honestly represent every major ecosystem by accepting
more spellings. A PEP 440 value and a SemVer value are different standards and
must not be silently conflated.

Reference: <https://peps.python.org/pep-0440/>

Focused scores as a direct LIB012 model: future **2.5/5**, scale **4.5/5**,
Protos fit **2/5**.

### RubyGems / Bundler

RubyGems has a broad legacy-compatible Version model with numeric/string segments,
prerelease interpretation, canonical comparison behavior and a requirement
language including the pessimistic `~>` operator. It is mature package-system
prior art but not a direct strict-SemVer model.

References:

- <https://docs.ruby-lang.org/en/master/Gem/Version.html>
- <https://guides.rubygems.org/gemfile/>

Focused scores: future **2.5/5**, scale **4/5**, Protos fit **2/5**.

### Maven

Maven `ComparableVersion` accepts arbitrary component structures, qualifier
aliases/orderings and normalization rules that deliberately serve Maven's legacy
version ecosystem. Dependency mediation then adds separate graph policy.

Maven is strong negative evidence against a supposedly universal Version
abstraction: absorbing ecosystem-specific qualifier rules makes the semantic
surface much larger and less portable than strict SemVer.

Reference: <https://maven.apache.org/ref/current/maven-artifact/apidocs/org/apache/maven/artifact/versioning/ComparableVersion.html>

Focused scores: future **2.5/5**, scale **4/5**, Protos fit **1.5/5**.

### Gradle / Ivy-style dependency versions

Gradle must interoperate with several dependency ecosystems and therefore owns
its own rich version ordering and selector semantics, including special status
handling and repository metadata. This is appropriate for dependency resolution
but is not a neutral strict-SemVer value model.

Reference: <https://docs.gradle.org/current/userguide/dependency_versions.html>

Focused scores: future **2.5/5**, scale **4.5/5**, Protos fit **1.5/5**.

### Cabal / Haskell PVP

Cabal's Version values and version-range algebra are tied to Haskell package
versioning conventions and PVP compatibility practice rather than SemVer 2.0.0.
The explicit separation between a Version and a range expression is useful; the
range/compatibility meanings are not generic SemVer policy.

Reference: <https://pvp.haskell.org/>

Focused scores: future **3/5**, scale **4.5/5**, Protos fit **2.5/5**.

### Debian/dpkg

Debian package versions use epoch/upstream-version/debian-revision and a
well-defined ordering algorithm containing Debian-specific lexical rules. It is a
highly scalable production ordering system and intentionally not SemVer.

Its value to LIB012 is primarily falsification: `Version` is not one universal
cross-ecosystem semantic type.

Reference: <https://www.debian.org/doc/debian-policy/ch-controlfields.html#version>

Focused scores as a LIB012 model: future **2/5**, scale **5/5**, Protos fit
**1/5**.

### RPM EVR

RPM similarly uses Epoch-Version-Release with package-system-specific comparison
rules. It is proven at distribution scale, but its semantics are the wrong domain
for a strict SemVer library.

Reference: <https://rpm.org/docs/latest/manual/dependencies.html>

Focused scores as a LIB012 model: future **2.5/5**, scale **5/5**, Protos fit
**1.5/5**.

## Focused prior-art score summary

| System | Aguante de futuro | Escalabilidad | Filosofía Protos |
| --- | ---: | ---: | ---: |
| Rust `semver` | **4.5** | **5.0** | **4.5** |
| Elixir `Version` | **4.5** | **5.0** | **4.5** |
| SwiftPM | **4.5** | **4.5** | **4.0** |
| Go `x/mod/semver` | **4.0** | **5.0** | **3.5** |
| npm `node-semver` | 4.0 | 3.5 | 3.0 |
| Cargo requirements | 4.0 | 4.5 | 3.0 |
| Composer | 3.5 | 4.0 | 2.5 |
| NuGet | 3.5 | 4.5 | 2.5 |
| Cabal / PVP | 3.0 | 4.5 | 2.5 |
| Python / PEP 440 | 2.5 | 4.5 | 2.0 |
| RubyGems | 2.5 | 4.0 | 2.0 |
| Maven | 2.5 | 4.0 | 1.5 |
| Gradle | 2.5 | 4.5 | 1.5 |
| RPM EVR | 2.5 | **5.0** | 1.5 |
| Debian/dpkg | 2.0 | **5.0** | 1.0 |

## Cross-ecosystem conclusions

### Strict SemVer values are a stable domain

Where ecosystems actually use SemVer 2.0.0 values, the core grammar and
precedence model are stable enough to standardize independently.

### There is no standard SemVer range language

Major ecosystems intentionally disagree:

```text
Cargo       bare 1.2.3 -> caret-compatible
TOOL001     bare 1.2.3 -> exact
npm         rich comparator/range algebra
NuGet       interval syntax
RubyGems    ~>
Composer    caret/tilde/wildcard/union + stability policy
PEP 440     different version standard + different specifier language
Maven       Maven-specific version/range semantics
```

Therefore range syntax is not part of LIB012-A.

### Coercion is adapter policy

Inputs such as `v1.2.3`, `1.2`, Maven qualifiers, PEP 440 values, Go
pseudo-versions or RPM/Debian versions can be meaningful in their own ecosystems.
A strict SemVer parser must not silently coerce them.

### Protos should not inherit host integer limits

SemVer defines numeric comparison, not a fixed integer representation. Protos
already owns arbitrary-precision Integer and current Package Tool code proves the
mechanism. LIB012 therefore selects no arbitrary `u64`, safe-integer or digit
ceiling.

### Complete value identity is distinct from precedence

For example:

```text
1.0.0+linux
1.0.0+windows
```

have equal SemVer precedence but contain different build metadata. LIB012 keeps
those facts distinct:

```text
complete value equality/hash    includes build metadata
SemVer precedence comparison    ignores build metadata
```

No arbitrary lexical ordering of build metadata is introduced merely to create a
total order that SemVer does not define.

## Candidate architectures

### Candidate A′ — strict SemVer Version kernel only — SELECTED

Initial public facility owns only:

- strict SemVer 2.0.0 parsing;
- canonical formatting;
- complete semantic version value representation;
- SemVer precedence comparison.

No Range/Requirement syntax or package-resolution behavior is part of the first
contract.

### Candidate B′ — Version plus ecosystem-neutral Requirement algebra

Add primitive comparator/conjunction/union structures while deliberately avoiding
npm/Cargo spelling policy.

This is plausible future work but premature for LIB012-A. Even a supposedly
neutral requirement algebra must choose prerelease admission, normalization,
partial-version behavior and matching laws. Those deserve their own evidence and
real non-Package-Tool consumer.

### Candidate C — Version plus npm/Cargo-style range language

Feature-rich and familiar, but necessarily selects package-ecosystem policy that
SemVer itself does not define. Rejected for the baseline library.

### Candidate D — promote current TOOL001 ReleaseVersion/DependencyConstraint

Minimal implementation work, but it would turn Package Tool restrictions
(no build metadata, exact bare versions, selected caret/interval semantics) into a
general-purpose contract. Rejected as the public architecture.

### Candidate E — generic multi-standard `std:version/Version`

Attempt to accept/normalize SemVer, PEP 440, Maven/RubyGems/Debian/RPM-like
versions under one abstraction. Rejected because these systems define genuinely
different value and ordering semantics rather than mere alternate spellings.

## Full candidate scorecard

Scores are 1–5; arithmetic is an aid, not authority. Confidence for A′ is HIGH
for the architecture boundary and HIGH for specification feasibility.

| Criterion | A′ strict Version | B′ + neutral Requirement | C ecosystem ranges | D promote TOOL001 | E multi-standard |
| --- | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | **5.0** | 4.5 | 4.0 | 4.0 | 2.5 |
| Protos alignment | **5.0** | 4.0 | 2.5 | 3.0 | 1.5 |
| Future-option resilience | **5.0** | 4.5 | 3.5 | 3.0 | 2.5 |
| Scalability | **5.0** | 4.5 | 4.0 | **5.0** | 3.0 |
| Conceptual simplicity | **5.0** | 4.0 | 3.0 | 4.0 | 2.0 |
| Portability / implementation freedom | **5.0** | 4.5 | 4.0 | 4.0 | 2.5 |
| Runtime / resource cost | **5.0** | 4.5 | 4.0 | **5.0** | 3.0 |
| Failure / operability | **5.0** | 4.5 | 3.5 | 4.0 | 2.5 |
| Reversibility / migration cost | **5.0** | 4.5 | 3.0 | 3.0 | 2.0 |
| Evidence maturity / implementation risk | **5.0** | 4.0 | **5.0** | **5.0** | 3.5 |
| **Average** | **5.00** | 4.35 | 3.65 | 4.00 | 2.55 |

Focused project-owner axes:

| Candidate | Aguante de futuro | Escalabilidad | Filosofía Protos |
| --- | ---: | ---: | ---: |
| **A′ strict Version** | **5.0** | **5.0** | **5.0** |
| B′ + neutral Requirement | 4.5 | 4.5 | 4.0 |
| C ecosystem ranges | 3.5 | 4.0 | 2.5 |
| D promote TOOL001 | 3.0 | 5.0 | 3.0 |
| E multi-standard | 2.5 | 3.0 | 1.5 |

## Ratified public identity

The canonical initial module identity is:

```text
std:semver/SemVer
```

with intended physical source:

```text
protos/lib/semver/SemVer.protos
```

This name is intentionally standard-specific. `std:version/Version` is rejected
for the initial facility because PEP 440, Maven, RubyGems, Debian/RPM and other
version domains do not share one honest ordering/normalization semantics.

## Ratified initial semantic surface

The initial public operations are conceptually:

```text
SemVer.parse(text)                  -> version
SemVer.format(version)              -> String
SemVer.comparePrecedence(a, b)      -> -1 | 0 | 1
```

Exact mechanical record validation/constructor details remain LIB012-A
implementation work unless they expose a new substantive public choice.

The initial value model is ordinary inspectable Protos data, conceptually:

```text
{
    major: Integer
    minor: Integer
    patch: Integer
    prerelease: Array<Integer | String>
    build: Array<String>
}
```

The representation principles are ratified even if the bounded implementation
chooses a canonical tag/envelope to distinguish validated values:

- numeric core components are ordinary arbitrary-precision Integer;
- numeric prerelease identifiers are represented numerically so their ordering
  cannot accidentally become lexical;
- non-numeric prerelease identifiers preserve exact ASCII String spelling/case;
- build identifiers remain Strings because numeric-looking build identifiers do
  not have numeric precedence semantics and leading zeroes are valid;
- parsed/canonical value data must not depend on JVM classes or host-library
  Version objects.

## Equality, hashing and precedence

A complete semantic value includes build metadata. Therefore complete structural
value equality/hash includes build metadata.

SemVer precedence is a separate relation:

```text
SemVer.comparePrecedence(
    SemVer.parse("1.0.0+linux"),
    SemVer.parse("1.0.0+windows")
) == 0
```

while the two complete values remain distinct.

LIB012-A must not invent an arbitrary ordering for build metadata merely to turn
precedence into a total order.

## Parsing and canonical formatting

The baseline parser is strict SemVer 2.0.0. It does not provide baseline coercion,
cleanup or compatibility parsing.

Examples rejected by the strict parser include:

```text
v1.2.3
1.2
1
01.2.3
1.02.3
1.2.03
1.2.3-
1.2.3-alpha..1
1.2.3-01
```

whereas build metadata may contain numeric-looking identifiers with leading
zeroes because SemVer permits them:

```text
1.2.3+001
```

Canonical formatting emits the strict SemVer spelling represented by the value.
No locale, Unicode normalization, case folding, host version formatter or package
manager normalization participates.

## Resource and scale model

Parsing and formatting are synchronous, deterministic, authority-free local data
processing.

Target complexity for input length `n` is:

```text
time:   O(n)
memory: O(n) for retained identifier text/value data
```

Numeric comparison uses ordinary arbitrary-precision Integer semantics; work
scales with the magnitude/length of the compared numeric components rather than
silently truncating to a machine word.

No global registry, cache, worker, Future, Task, Actor coordination, filesystem,
network, clock, locale or random source is required. Many Actors/Processes may
parse/compare independently.

The implementation must avoid input-proportional host recursion and arbitrary
fixed-length rejection. Resource exhaustion remains ordinary implementation
resource pressure rather than a SemVer semantic ceiling.

## TOOL001 boundary after ratification

LIB012-0 does **not** change Package Tool behavior.

Current Package Tool `ReleaseVersion` remains allowed to enforce its narrower
published-release contract:

```text
MAJOR.MINOR.PATCH[-PRERELEASE]
```

and reject build metadata even though public `std:semver/SemVer` accepts it.

Likewise these remain TOOL001-owned until separately promoted by evidence:

- exact/caret/bounded dependency constraint syntax;
- prerelease admission for dependency requirements;
- candidate selection;
- retained lock selection;
- yank/update/resolution behavior.

A later migration may make TOOL001 reuse the generic LIB012 mechanism internally,
but reuse must preserve existing Package Tool semantics and must not make Package
Tool bootstrap depend on package resolution.

## Strongest argument against A′

The strongest argument is convenience: many consumers asking for SemVer also want
range matching. Shipping Version-only first may cause a small amount of temporary
duplication in tools that already have requirement logic.

That cost is accepted because requirement syntax is exactly where ecosystems
diverge. Adding it prematurely would freeze much more policy than the stable
SemVer value standard requires. A later Requirement layer can reuse the Version
value without breaking it.

## Future-regret scenarios and escape paths

### A general non-SemVer version abstraction becomes necessary

Do not broaden `SemVer` into a union of unrelated standards. Add independent
standard/domain modules and explicit adapters where a real use case proves them.
The specific `std:semver/SemVer` identity remains honest and stable.

### Multiple consumers need requirement/range algebra

Open a separately researched LIB012 extension or later LIB work item. It may
introduce a generic SemVer Requirement value only after selecting exact matching,
prerelease, normalization and syntax rules. The Version kernel remains unchanged.

### Package Tool wants to reuse LIB012

Keep a thin package-owned policy layer that rejects build metadata and preserves
TOOL001 dependency semantics. Generic parsing/precedence can be reused without
promoting package rules.

### A future runtime uses a different host representation

The public model uses ordinary Protos values and arbitrary-precision Integer, so
no JVM/Rust/JavaScript version class is semantically required.

## Intentionally deferred

LIB012-0 deliberately does not select:

- a public Range/Requirement syntax;
- caret/tilde/wildcard semantics;
- unions/intersections/exclusions;
- prerelease admission for dependency matching;
- package compatibility promises;
- solver behavior;
- release selection/update/yank behavior;
- registry/network/filesystem behavior;
- `v` prefixes or partial-version compatibility input;
- loose parsing, cleanup or coercion;
- PEP 440/Maven/NuGet/RubyGems/Debian/RPM adapters;
- Package Tool migration to public LIB012;
- a universal cross-version-standard hierarchy.

Any such extension requires its own evidence and explicit approval where the
choice is substantive.

## Implementation decomposition after ratification

### LIB012-A — Version value + strict parser

Status: **READY**

Implement the canonical public `std:semver/SemVer` value construction/validation
and strict SemVer 2.0.0 parser, including core, prerelease and build metadata with
arbitrary-precision Integer semantics.

If implementation exposes a substantive choice about canonical envelope,
forged-value acceptance, public constructors, equality/hash integration, parser
error taxonomy, resource limits or another observable behavior not mechanically
fixed by A′, stop the affected slice and cross the explicit approval gate.

### LIB012-B — precedence + canonical formatting conformance

Status: **BLOCKED_BY_A**

Implement/retain full SemVer precedence conformance, build-metadata-insensitive
precedence equality, canonical formatting and comprehensive edge-case evidence.

### LIB012-C — TOOL001 reuse/migration audit

Status: **BLOCKED_BY_A_B**

Determine whether current Package Tool ReleaseVersion implementation should reuse
LIB012 mechanism while preserving Package Tool's no-build-metadata rule and all
existing dependency/selection behavior. This slice is an integration audit, not
permission to change package policy.

### Requirement/range layer

Status: **DEFERRED — independent design required**

Do not create it merely because npm/Cargo/Composer provide one. A future real
consumer must justify the exact public requirement algebra and syntax.

## Ratification summary

```text
LIB012_0_STATUS=RATIFIED
LIB012_0_SELECTED_CANDIDATE=A_PRIME
PUBLIC_MODULE=std:semver/SemVer
SEMVER_PROFILE=2.0.0_STRICT
VERSION_ONLY_INITIAL=YES
BUILD_METADATA=PRESERVED
FULL_VALUE_EQUALITY_INCLUDES_BUILD=YES
PRECEDENCE_IGNORES_BUILD=YES
NUMERIC_COMPONENTS=UNBOUNDED_INTEGER
COERCION=NO
V_PREFIX=NO
PARTIAL_VERSION=NO
RANGE_REQUIREMENT_INITIAL=DEFERRED
TOOL001_POLICY_CHANGED=NO
LIB012_A_STATUS=READY
LIB012_B_STATUS=BLOCKED_BY_A
LIB012_C_STATUS=BLOCKED_BY_A_B
SPECIFICATION_CHANGED=NO
IMPLEMENTATION_CHANGED=NO
IMPLEMENTATION_VERSION_CHANGED=NO
```
