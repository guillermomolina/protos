# D066 — Documentation authority, project Wiki, and public website topology

Status: **RATIFIED — Candidate D selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #325

Nature: durable implementation-independent documentation/tooling architecture decision

Triggered by: WEB001 / #278, WEB001-B, WEB001-J / #301, and the TOOL003 scope re-audit

Normative language effect: **none**. D066 selects repository/documentation authority boundaries and
presentation ownership. It does not change Protos syntax, semantics, Standard Library behavior,
package identity, runtime behavior, or compatibility guarantees.

## Decision

Protos uses three deliberately different documentation/project surfaces:

```text
guillermomolina/protos
    canonical, version-sensitive authority

Protos GitHub Wiki
    non-authoritative project/contributor knowledge

guillermomolina/protos-website
    independent public user-facing presentation and deployment
```

The three surfaces are not peers and do not form three competing authorities.

`guillermomolina/protos` remains authoritative for material whose correctness or meaning must be
recoverable from an exact source revision: implementation, `spec/`, Standard Library source and API
documentation, ratified durable decisions, tests, release-coupled guides/reference inputs,
executable examples, reusable editor/source assets, and other version-sensitive facts.

The GitHub Wiki is a contributor/project knowledge surface only. It may explain how to build and
work on Protos, project orientation, repository architecture at an explanatory level, issue and
governance conventions, FAQ, troubleshooting, glossary, roadmap orientation, and navigation to
canonical records. It MUST NOT become an alternate specification, API authority, durable decision
registry, release-versioned reference source, or maintained copy of canonical language docs.

`guillermomolina/protos-website` remains an independent companion repository. It owns public web
presentation, navigation, styling, materialization/adaptation, build/deployment mechanics, and the
public product/language-facing experience. It consumes an exact Protos revision and MUST NOT become
semantic or API authority.

## Why this decision was reopened

WEB001-B correctly selected an independent website repository with exact-SHA read-only acquisition
of canonical Protos sources. As WEB001-J expanded, the website accumulated separate materializers
for guide, tutorials, examples, specification, Standard Library source and editor grammar. During
TOOL003, that cross-repository boundary encouraged an implementation interpretation stronger than
the actual requirement: a generalized documentation-model/extractor platform for website, CLI,
IDE/LSP, search and package documentation before those additional consumers existed.

The project-owner review identified that repository separation and content transformation are two
different problems. Even in a monorepo, Markdown and source documentation still require adaptation
for Astro/Starlight. Conversely, an independent website does not require every transformation to
become a reusable ecosystem platform.

D066 therefore re-audits topology while preserving the good WEB001-B boundaries and restoring the
Protos rule that generality must be earned by concrete consumers.

## Current Protos constraints

The decision must preserve all of the following already-ratified boundaries:

1. `spec/` remains normative for observable language semantics.
2. Canonical maintained documentation and source assets remain in `guillermomolina/protos` when
   their meaning is tied to a source revision.
3. WEB001-B keeps `protos-website` as an independent companion repository using read-only exact-SHA
   canonical-source acquisition and explicit provenance.
4. WEB001-F keeps production hosting/deployment independent from language/runtime implementation.
5. D061 keeps mechanically observable documentation facts Protos-owned rather than website-owned.
6. D062 keeps `//!` / `///` as tooling-only documentation authoring conventions over ordinary
   comments and preserves canonical supplemental Markdown in `protos`.
7. D064 keeps documentation identity/provenance contracts independent of renderer URLs or source
   coordinates.
8. TOOL003-A is already published implementation and cannot be silently erased by a topology
   decision.
9. The normal Protos devcontainer already carries a Node toolchain sufficient for native website
   development, so lack of Docker-inside-Docker is not a monorepo disqualifier. Docker image
   publication can remain a CI concern.

## Prior-art and systems survey

The survey is deliberately about authority/topology rather than copying any one project's exact
layout.

### Rust

Rust keeps language/compiler/library sources and substantial reference/book/rustdoc material under
the canonical `rust-lang/rust` project tree while `www.rust-lang.org` is maintained as a separate
website repository. This demonstrates that source-coupled documentation and an independently
operated public website are compatible rather than mutually exclusive.

Relevant evidence:

- https://github.com/rust-lang/rust
- https://github.com/rust-lang/www.rust-lang.org

Lesson for Protos: independent public presentation need not own canonical language documentation.

### Go

Go separates the canonical language repository from the website implementation. The Go repository
remains the language/source authority while `golang/website` owns website content and serving
programs. Website issues still coordinate through the broader Go project rather than redefining
language authority.

Relevant evidence:

- https://github.com/golang/go
- https://github.com/golang/website

Lesson for Protos: a companion website repository is a mature pattern when provenance and
ownership remain explicit.

### CPython

CPython keeps the Python language and library documentation sources directly in `python/cpython`
under `Doc/`, including the language reference, while `python/pythondotorg` separately owns the
python.org web application. Python also uses Wiki surfaces for community/beginner material without
making that Wiki the language-reference authority.

Relevant evidence:

- https://github.com/python/cpython/tree/main/Doc
- https://github.com/python/pythondotorg
- https://wiki.python.org/

Lesson for Protos: canonical docs, public website, and community/project Wiki can have distinct
roles without becoming competing sources of truth.

### Swift

Swift uses multiple documentation locations according to ownership. Library-specific documentation
is expected to remain with the library, while the separate `swiftlang/docs` repository hosts
cross-cutting Swift documentation content published through swift.org. This is evidence against a
single universal storage rule and in favor of ownership following the thing whose lifecycle the
content describes.

Relevant evidence:

- https://github.com/swiftlang/swift
- https://github.com/swiftlang/docs

Lesson for Protos: version-sensitive documentation should remain close to its owning artifact;
cross-project presentation can remain separate.

### Zig

Zig's canonical repository contains language-reference and Standard Library documentation build
inputs, while its public website has historically been maintained separately. The build can derive
language/reference output from the canonical source tree rather than requiring the website to own
those facts.

Relevant evidence:

- https://github.com/ziglang/zig
- https://github.com/ziglang/www.ziglang.org

Lesson for Protos: generated reference material naturally belongs to the language/source authority
even when final presentation is external.

### GitHub Wiki mechanics

GitHub documents that a repository Wiki is cloneable as its own Git repository using the
`<repository>.wiki.git` form. Therefore a Wiki does not share commits/tags atomically with the main
repository.

Relevant evidence:

- https://docs.github.com/en/communities/documenting-your-project-with-wikis/adding-or-editing-wiki-pages
- https://docs.github.com/en/repositories/archiving-a-github-repository/backing-up-a-repository

Lesson for Protos: Wiki is suitable for human-maintained project knowledge but is a poor authority
for material that must correspond exactly to language releases or source revisions.

## Candidate set

### Candidate A — retain the current two-repository model only

Keep `protos` authoritative and `protos-website` independent, but do not establish a separate Wiki
role. Contributor/project explanatory material remains mixed between repository docs, README,
Issues and website navigation.

This preserves current architecture but leaves pressure for the website or durable repository docs
to absorb project-help material that does not need release coupling.

### Candidate B — monorepo website under `protos/`

Move the website implementation to a `website/` subtree of `guillermomolina/protos`.

Benefits:

- source and presentation can change atomically;
- no cross-repository source lock/fetch is required;
- canonical sources are directly available to build scripts;
- ordinary website development can run through Node inside the existing devcontainer;
- Docker image publication can remain in GitHub Actions.

Costs:

- Node/frontend dependencies, website workflow churn and deployment permissions live in the
  language repository;
- website-only lifecycle and experimentation become repository-main churn;
- independent deployment/security ownership is weakened without a concrete need to do so.

### Candidate C — canonical documentation in Wiki + independent website

Move substantial maintained documentation to the Protos Wiki and keep the website separate.

This appears simple from the GitHub UI but fails the important atomicity property because the Wiki
is a different Git repository. Release-sensitive documentation would need explicit revision mapping
back to `protos`, recreating the same distributed synchronization problem in a less suitable place.

Candidate C is rejected.

### Candidate D — canonical repository + contributor Wiki + independent website

Keep canonical/version-sensitive material in `protos`, reserve the Wiki for non-authoritative
contributor/project knowledge, and keep the public website as the independent presentation layer.

This preserves atomic language/documentation history and deployment isolation while giving
project-help content an intentionally lightweight home.

### Candidate E — monorepo website + contributor Wiki

Combine Candidate B with the non-authoritative Wiki role from Candidate D.

This is coherent and keeps a useful Wiki boundary, but still pays the monorepo lifecycle and
supply-chain coupling cost without a demonstrated requirement for atomic website implementation
commits.

## Comparative matrix

Scores use the GITHUB010 common 1–5 scale. `H` = high confidence, `M` = medium confidence.
Totals are comparison aids only.

| Candidate | Correctness | Protos fit | Future | Scale | Simplicity | Portability | Resource cost | Operability | Reversibility | Evidence | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A — two repos only | 5/H | 4/H | 4/H | 4/H | 3/H | 5/H | 3/H | 4/H | 4/H | 5/H | 41 |
| B — monorepo | 5/H | 4/M | 5/H | 5/H | 4/H | 4/H | 4/H | 4/H | 3/M | 5/H | 43 |
| C — Wiki canonical | 3/H | 2/H | 3/H | 3/H | 4/M | 4/H | 4/H | 3/H | 3/M | 3/H | 32 |
| **D — repo + Wiki + website** | **5/H** | **5/H** | **5/H** | **5/H** | **5/M** | **5/H** | **4/H** | **5/H** | **5/H** | **5/H** | **49** |
| E — monorepo + Wiki | 5/H | 4/M | 5/H | 5/H | 4/M | 4/H | 4/H | 4/H | 3/M | 5/H | 43 |

### Score justification — Candidate A

Correctness and portability are strong because the current exact-SHA model already preserves
source authority. Simplicity/resource scores are lower because every project-facing content need
must choose between canonical repo docs and website materialization, encouraging additional
cross-repository adapters. Reversibility remains good because no authority has moved.

### Score justification — Candidate B

Atomicity and scale are excellent, and local Docker availability is not required for ordinary web
development. The weaker Protos/reversibility scores come from coupling independent web lifecycle,
Node supply chain and deployment workflow to the canonical language repository without an
immediate semantic need.

### Score justification — Candidate C

Wiki is simple for humans but fails release atomicity and weakens authority discipline. Repairing
that later requires either moving content back or inventing revision links between two Git
histories, so it scores poorly on correctness and Protos alignment despite low initial UI friction.

### Score justification — Candidate D

Each surface has one narrow role. Version-sensitive truth stays in ordinary Git history with the
code it describes; project-help knowledge can evolve cheaply; website deployment remains isolated.
The only meaningful cost is navigation discipline across three surfaces, which is operationally
small if each surface states its authority role clearly.

### Score justification — Candidate E

It shares Candidate D's useful Wiki role and Candidate B's atomic source access, but the web
implementation itself still becomes canonical-repo churn. Its evidence base is mature and it scales
well, but migration back to a separate deployable web repo would be more expensive than preserving
the already-working boundary now.

## Failure modes and counterexamples

### Wiki drift into authority

The main failure mode of Candidate D is social rather than technical: contributors may copy a
specification/API explanation into the Wiki and later treat it as authoritative. D066 therefore
requires explicit Wiki banners/navigation pointing back to canonical repository/spec/reference
sources and forbids release-sensitive authority from moving there.

### Website presentation lag

Because the website consumes an exact Protos revision, a canonical documentation change does not
become public presentation automatically. This is intentional reproducibility, not inconsistency.
The project may later automate source-lock update proposals, but ordinary builds must remain pinned.

### Three-surface discoverability

Users may not know whether to use repo, Wiki or website. The escape is not to collapse authority;
it is to make roles explicit:

```text
website -> learn/use Protos
Wiki    -> work on/understand the project
repo    -> authoritative versioned source and decisions
```

### Generalized tooling reappearing prematurely

A neutral documentation contract can tempt implementation to build support for hypothetical
consumers. D066 explicitly separates reusable *contract* from reusable *platform implementation*.
Only a real CLI/IDE/package consumer earns the corresponding implementation generalization.

## Future-scenario stress test

### Many releases and long-lived versioned docs

Candidate D keeps release-sensitive source/doc history together. A tag/commit can still reconstruct
its implementation, specification and owned documentation. Wiki material remains intentionally
current/project-oriented rather than pretending to be release-versioned.

### Large Standard Library and package ecosystem

Canonical API facts remain package/source-owned. D061/D062/D064 may support later reusable tooling,
but implementation can grow incrementally from actual consumers rather than requiring the website
pipeline to become the universal package-doc system now.

### Many contributors

Wiki lowers friction for project-help material without granting it semantic authority. Website
contributors can continue working in the companion repo without cloning/building the runtime.
Language contributors keep canonical source and tests together.

### Independent website redesign or hosting change

The separate `protos-website` lifecycle allows Astro/Starlight, styling, container delivery or host
changes without touching language implementation history. Canonical inputs remain read-only.

### Future interactive playground

WEB001's existing separate security boundary remains intact. D066 does not make Wiki or canonical
documentation architecture depend on a code-executing web service.

### Future CLI/IDE documentation consumer

If a real second consumer requires the D064 neutral artifact, the already-ratified model can be
reused and TOOL003 can grow at that point. D066 does not forbid generalization; it forbids paying
for it before the requirement exists.

## Future-regret question

**What plausible future requirement would make us regret Candidate D?**

A future in which website presentation must change atomically with every language/source commit and
where web build/deployment ownership becomes inseparable from compiler releases could make the
separate repository feel unnecessarily indirect.

**Escape path:** move `protos-website` into a `website/` subtree later. The site is static, canonical
source authority already remains in `protos`, and no website-owned semantic data must be migrated.
The current choice therefore preserves a straightforward monorepo migration path.

A second possible regret is that contributor Wiki content becomes important enough to require exact
release versioning.

**Escape path:** move that specific content back into canonical `docs/` and leave the Wiki as a
navigation/current-project layer. Because D066 forbids Wiki semantic authority, no compatibility
promise prevents that move.

## Strongest argument against Candidate D

The strongest objection is that three visible surfaces create cognitive overhead and duplicate
navigation. A monorepo can be easier to explain: one checkout, one commit graph, one documentation
source, one website subtree. Candidate D accepts that criticism but judges it smaller than the
lifecycle/supply-chain coupling introduced by moving a currently independent working deployment
into the language repository. The mitigation is strict role separation and cross-linking rather
than duplicated content.

## Consequences for WEB001-B

WEB001-B is **refined, not replaced**.

Still ratified:

- independent `guillermomolina/protos-website` companion repository;
- `guillermomolina/protos` canonical authority;
- exact-SHA read-only source acquisition;
- Astro + Starlight and static output;
- explicit source/build provenance;
- independent deployment/security boundary;
- no website write authority to canonical Protos source; and
- independently secured future playground execution.

D066 adds the non-authoritative contributor/project Wiki role and explicitly rejects the inference
that an independent website requires a broadly generalized cross-consumer documentation platform.

## Consequences for D061 / D062 / D064 and TOOL003

D061, D062 and D064 remain RATIFIED. Their authority, authoring and identity/model contracts are not
reopened.

TOOL003 implementation scope is corrected:

- TOOL003-A remains the only published TOOL003 implementation slice at D066 ratification time;
- the previously authored but unpublished TOOL003-B candidate is abandoned and MUST NOT be applied;
- the original A/B/C/D decomposition in GitHub #322 is no longer an approved implementation plan;
- WEB001-J7B requires the smallest Protos-owned deterministic Standard Library extraction mechanism
  sufficient for the exact canonical revision and D061/D062/D064 invariants;
- CLI/IDE/search/package-documentation implementation must not be built until a real consumer earns
  that generalization; and
- removing or materially simplifying already-published TOOL003-A is a separate bounded action and is
  not implied by D066.

## Wiki authority boundary

Suitable Wiki content:

- contributor onboarding;
- build/development-environment instructions;
- repository/project architecture overview at explanatory level;
- GitHub issue/family/governance workflow explanations;
- FAQ;
- troubleshooting;
- glossary;
- roadmap/project orientation; and
- navigation links to canonical records.

Must remain in canonical `protos` when applicable:

- normative language/Standard Library semantics;
- source/version-sensitive API reference facts;
- Standard Library source documentation;
- ratified Dxxx/PLATxxx and other durable decisions;
- release-coupled guides/reference inputs;
- tests and executable examples tied to a source revision;
- package/source identity rules; and
- canonical reusable grammar/editor assets.

## Intentionally deferred

D066 does not select:

- the initial Wiki page inventory or sidebar structure;
- whether Wiki content is later maintained manually or through helper automation;
- automated proposals for advancing `protos-source.lock.json`;
- the exact simplification of the current website materializer set;
- removal/refactoring of TOOL003-A;
- the minimal implementation shape for WEB001-J7B extraction;
- API publication/coverage policy deferred by D064;
- documentation version-selection UX on the website; or
- any future package documentation registry/service.

Each item may be implemented locally if mechanically implied by existing authority or routed through
its own decision gate if it creates a new durable architecture/semantic constraint.

## Ratification closure

The project owner explicitly approved Candidate D on 2026-09-11 after comparing the existing
two-repository design, monorepo, Wiki-canonical, hybrid repo/Wiki/website, and monorepo+Wiki
architectures, including concrete Protos Docker/devcontainer constraints and mature ecosystem prior
art.

Result:

```text
D066                         RATIFIED — Candidate D
CANONICAL_VERSIONED_AUTHORITY guillermomolina/protos
PROJECT_CONTRIBUTOR_WIKI      NON_AUTHORITATIVE
PUBLIC_WEBSITE_REPOSITORY     guillermomolina/protos-website
WEBSITE_SOURCE_INPUT          EXACT_PROTOS_REVISION
PROTOS_SOURCE_LOCK            RETAINED
WEB001_B                      REFINED_NOT_REPLACED
D061_D062_D064                RETAINED
TOOL003_A                     RETAINED_AS_PUBLISHED
TOOL003_B_UNPUBLISHED         ABANDONED
CROSS_CONSUMER_GENERALIZATION EARNED_ONLY_BY_REAL_CONSUMERS
SPECIFICATION_CHANGED         NO
IMPLEMENTATION_CHANGED        NO
IMPLEMENTATION_VERSION_CHANGED NO
```
