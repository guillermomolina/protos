# D068 — Exact-SHA documentation extractor execution boundary for protos-website

Status: **RATIFIED — Candidate A-prime selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #330

Nature: durable implementation-independent documentation/tooling architecture

Triggered by: TOOL003 / #322 closure, D066 / #325, D067 / #326, and
WEB001-J7B under #301.

Normative language effect: **none**.

## Decision

WEB001-J7B SHALL obtain the Standard Library documentation model by executing
the Protos-owned documentation producer from the **same exact Protos revision**
selected by `protos-source.lock.json`.

The durable cross-repository contract is the existing **D064 JSON model**, not
the current Java class, Maven invocation, JDK version, or any other producer
implementation detail.

Conceptually:

```text
protos-source.lock.json exact SHA
        |
        v
exact Protos checkout
        |
        | Protos-owned producer from that same revision
        v
D064 JSON
        |
        v
protos-website renderer
```

At the time of ratification the producer is implemented by TOOL003 using
JDK 21 + Maven + `ProtosStandardLibraryDocumentationExtractor`. That is a
replaceable implementation detail.

The website MUST NOT maintain an independent Protos parser or documentation
extractor.

Generated D064 JSON is ephemeral derived input. It is not committed as a second
API authority.

An ephemeral cache keyed by the exact Protos revision MAY be used as an
optimization, provided:

- the cache is never authority;
- the artifact provenance is revalidated before consumption; and
- cache invalidation cannot change which source revision is authoritative.

Candidate C — producer-side immutable D064 artifact publication — remains the
explicit future scaling path if repeated compilation or multiple independent
consumers justify a durable publication channel.

## Existing authority retained

D068 composes with, and does not reopen:

- D061: Protos owns mechanical extraction; downstream renderers do not infer
  API semantics from implementation bodies.
- D062: `//!` / `///` remain tooling-only documentation conventions.
- D064: the implementation-neutral deterministic JSON model and stable
  symbol-identity contract remain the consumer boundary.
- D066: `protos` remains canonical authority; `protos-website` remains an
  independent renderer consuming an exact Protos revision.
- D067: every mechanically observable importable `std:` module/top-level slot
  remains represented, with authored documentation optional and missing
  documentation explicit.
- TOOL003: the minimum Standard Library extractor published at implementation
  version `0.2.350-SNAPSHOT` remains the current producer.

No visibility/export rule, stability vocabulary, package-publication rule,
runtime documentation state, deployment-runtime change, or new Protos semantic
category is selected here.

## Exhaustive implementation prior art

### Apple Swift / Symbol Graph / DocC

Swift separates compiler-owned API extraction from documentation rendering.
The compiler emits Symbol Graph information; DocC consumes compiler-known
symbols plus authored documentation and produces renderable documentation data.

This is the closest architectural precedent for Protos:

```text
Swift source -> compiler -> Symbol Graph -> DocC/rendering
Protos source -> Protos producer -> D064 JSON -> website
```

The lesson adopted is that the language/compiler side owns semantic extraction
while a neutral intermediate model isolates renderers from producer internals.

### Go / go doc / pkgsite

Go documentation tooling derives package and declaration information through Go
tooling rather than asking a website to maintain another Go parser. Local
generation and ecosystem-scale serving both retain source/package authority.

The lesson adopted is strong authority locality with very little additional
institutional machinery.

### Rust / Cargo / rustdoc / docs.rs

`cargo doc` invokes the language-owned documentation tool for the selected
package/toolchain. Rustdoc also supports machine-readable output. At ecosystem
scale, docs.rs moves generation producer-side.

The lesson adopted is evolutionary: same-source/toolchain generation is a sound
small-scale default; centralized immutable generation becomes attractive when
real ecosystem scale appears.

### Java / Javadoc / Doclets

Javadoc is part of the JDK and operates over the Java language model. Doclets
consume that model to produce output.

The lesson adopted is that a documentation renderer need not — and should not —
own an independent parser merely to avoid a build-toolchain dependency.

### .NET / Roslyn / DocFX

DocFX separates semantic metadata extraction from rendering through an
intermediate documentation model.

The lesson adopted is that a stable interchange artifact can outlive the
current producer implementation and can later be published producer-side
without forcing renderer redesign.

### Haskell / Haddock

Haddock derives documentation from compiler-aware module/interface information
and then feeds separate backends.

The lesson adopted is a small explicit producer/model/backend split.

### Dart / dart doc

Dart documentation generation is owned by Dart package/analyzer tooling rather
than a website-specific parser.

The lesson adopted is source-toolchain ownership of extraction.

### Kotlin / Dokka and the emerging Kotlin Documentation Model

Dokka demonstrates the long-term cost of a very broad documentation engine
coupled to compiler evolution and many hypothetical consumers. Kotlin's newer
documentation-model direction moves toward a stable build artifact consumable
by multiple tools.

The lesson for Protos is especially strong: do not recreate the abandoned
pre-D066 generic TOOL003-B platform. Keep the producer bounded and make D064 the
stable boundary.

### TypeScript / TypeDoc

TypeDoc derives a reflection model from TypeScript compiler symbols and can emit
JSON. Its compiler-version compatibility surface illustrates the cost of
versioning the extractor independently of the source/compiler.

The lesson adopted is to keep source revision and producer revision naturally
identical for now.

### Elixir / ExDoc

ExDoc consumes compiler-produced documentation information from compiled
artifacts.

The useful lesson is compiler/toolchain ownership of extraction. Protos does
not adopt runtime or bytecode documentation state.

### Doxygen

Doxygen is a highly scalable standalone documentation system with its own
language parsers and optional compiler-assisted modes.

Its scalability is respected, but its independent-parser model is deliberately
not adopted: duplicating Protos language understanding outside Protos would
create a second semantic authority and maintenance burden.

### Python / Sphinx autodoc

Sphinx autodoc commonly imports modules to discover documentation and can
therefore execute import-time behavior and require runtime dependencies.

Protos deliberately rejects this pattern. Standard Library documentation
extraction remains static and never requires executing arbitrary modules.

## Candidate families

### A-prime — same exact checkout produces D064 JSON

**Selected.**

The exact revision owns both source and producer. D064 JSON is the only durable
cross-repository documentation-model contract.

Current producer implementation:

```text
exact checkout
    |
    | JDK21 + Maven
    v
TOOL003 extractor
    |
    v
D064 JSON
```

A future Protos-owned launcher, native executable, self-hosted Protos tool, or
producer-side artifact source may replace this implementation without changing
the website's semantic model.

### B — separately published extractor

Rejected for now. It creates a second compatibility/version axis and a new
distribution lifecycle before a real second consumer requires it.

### C — immutable producer-side D064 artifact publication

Deferred as the explicit scaling path.

It is likely preferable when many consumers or high-frequency builds make
repeated source-side generation wasteful, but it requires a durable immutable
publication/discovery/retention mechanism not justified by the current single
consumer.

### D — commit generated D064 JSON into protos

Rejected because exact repository-revision provenance becomes
self-referential.

### E — reimplement extraction in Node in protos-website

Rejected because it duplicates Protos parsing/semantic authority and violates
D061/D066.

### F — Dockerized extractor as mandatory ABI

Rejected for now because it makes Docker mandatory for the native website path
or introduces nested-container orchestration while still requiring an image
identity/publication policy.

## Focused future/scalability/Protos scorecard

Scores: 1–10.

| Candidate | Future resilience | Scalability | Protos philosophy | Total |
| --- | ---: | ---: | ---: | ---: |
| **A-prime — exact checkout -> D064** | **9** | **8** | **10** | **27/30** |
| C — producer-side immutable D064 registry | 10 | 10 | 7 | 27/30 |
| B — separately published extractor | 8 | 9 | 7 | 24/30 |
| F — Dockerized extractor ABI | 8 | 7 | 6 | 21/30 |
| E — website Node parser | 5 | 8 | 2 | 15/30 |
| D — committed self-referential JSON | 4 | 8 | 3 | 15/30 |

A-prime and C tie numerically because they optimize different scales.

A-prime is selected for the current scale because it preserves exact
source/producer identity and earned generality with one consumer. C remains the
deliberate future evolution when concrete scale justifies it.

## Why A-prime is the most Protos choice now

- **One authority:** the exact Protos revision owns source interpretation and
  extraction.
- **One interchange mechanism:** D064 JSON is the sole cross-repository semantic
  documentation boundary.
- **No speculative institution:** no artifact registry, lookup service,
  independent extractor release line or website parser.
- **Pay only for use:** current Java/Maven cost is build-time producer cost only
  and does not enter the static production runtime.
- **Generality is earned:** centralized immutable D064 publication remains a
  clean future path.
- **Implementation freedom:** the website does not care whether future Protos
  extraction is implemented in Java, native code, or Protos itself.

## Failure and future stress

### Locked revision predates TOOL003

J7B preparation must fail explicitly. J7B must select a revision containing the
ratified extractor.

### Current producer dependencies are unavailable

A clean uncached build may fail while acquiring Maven/JDK build dependencies.
This is an operational producer failure, not a reason to transfer parsing
authority to the website. Caching may improve it without changing the decision.

### Protos grows substantially

If compiling the producer becomes materially expensive, that is evidence for
Candidate C rather than for another parser.

### Many consumers appear

If each consumer independently recompiles the same exact source, Candidate C
becomes preferable: generate immutable D064 once producer-side and distribute
it by source identity.

### Producer implementation changes

The website integration boundary remains D064 JSON. Replacing Java/Maven does
not require changing documentation identity or page semantics.

## Future-regret question

**What plausible future requirement would make A-prime regrettable?**

Many independent consumers or very high-frequency builds could make repeated
producer compilation unnecessarily expensive.

**Escape path:** move generation producer-side and publish immutable D064
artifacts keyed by exact source revision/content identity. The website remains a
D064 consumer.

## Strongest argument against A-prime

The independent website was intentionally lightweight and Node-oriented.
Adding JDK21 + Maven to clean build/development requirements is a real increase
in build weight.

That cost is accepted because it is explicit, build-time-only, cacheable and
replaceable. Creating a durable publication/discovery institution for a single
consumer would introduce more permanent project machinery.

## Authorized WEB001-J7B consequence

After this ratification is published, WEB001-J7B may:

1. advance `protos-source.lock.json` to the exact selected revision containing
   TOOL003;
2. fetch the additional exact Protos build/source paths required by the current
   producer implementation;
3. compile/run the documentation producer from that same exact checkout;
4. consume only D064 JSON across the repository boundary;
5. use exact-SHA ephemeral caching as a non-authoritative optimization;
6. render all D067 observable modules/slots, visibly distinguishing missing
   authored documentation;
7. retain J7A source-browser links and exact revision provenance;
8. add JDK/Maven only to build/development tooling, never the production NGINX
   runtime image;
9. validate source-lock identity, artifact provenance, deterministic pages and
   full Astro build; and
10. preserve migration to Candidate C without changing D064 or page semantics.

## Closure

```text
D068                     RATIFIED — A-prime
TOOL003                  CLOSED / producer available
D064_JSON                DURABLE_CROSS_REPO_CONTRACT
WEB001_J7B               READY
SPECIFICATION_CHANGED    NO
PROTOS_SEMANTICS_CHANGED NO
```
