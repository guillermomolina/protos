# WEB001-B — Website repository, stack and deployment architecture audit

GitHub coordination: Issue
[#282](https://github.com/guillermomolina/protos/issues/282), child of
WEB001 / [#278](https://github.com/guillermomolina/protos/issues/278).

Status: **RATIFIED — CANDIDATE C SELECTED**

> **Hosting supersession (WEB001-F, 2026-09-10):** WEB001-B remains the
> authoritative ratification for repository topology, source authority,
> exact-SHA source acquisition, local execution, Astro + Starlight, static
> output, provenance, and future playground isolation. Its GitHub Pages hosting
> component was later superseded by the explicitly approved WEB001-F Candidate
> B: self-hosted immutable static image behind a private reverse-proxy
> environment. GitHub Pages is no longer an active or standby production path.

## Scope

WEB001-A ratified the visitor-journey information architecture and the
render/curate-from-canonical-sources contract. WEB001-B evaluates the durable
implementation architecture needed to realize that contract.

This audit covers:

- repository topology;
- canonical Protos source consumption;
- local developer execution and reproducibility;
- static-site framework;
- hosting and deployment;
- security and supply-chain boundaries; and
- growth toward richer reference material and a future playground.

It does not implement the site, configure DNS, publish production content, alter
Protos semantics, or redesign the already selected Protos logo.

## Current evidence

### Ratified WEB001-A contract

The public top level is:

`Home / Learn / Reference / Design / Community / GitHub`

The website must curate or render canonical maintained sources rather than fork
their semantic/documentation authority.

### Existing companion-repository precedent

`guillermomolina/protos-benchmarks` already establishes a useful project
precedent: executable infrastructure can live outside the canonical Protos
repository while consuming an explicitly identified Protos revision.

Its current Docker build accepts `PROTOS_REPOSITORY` and an exact
`PROTOS_REVISION`, performs a depth-one fetch of that revision, checks out the
detached commit, verifies the resulting HEAD exactly equals the requested SHA,
and only then builds.

The website does not need the whole Protos build, but the same ownership and
provenance principle is directly reusable.

### Concrete website repository

`guillermomolina/protos-website` now exists as a public empty repository.
Its existence makes the independent-repository option concrete but does not by
itself ratify the architecture.

## Decision dimensions

The selected architecture should:

1. keep Protos specification/documentation authority in `guillermomolina/protos`;
2. let a website contributor clone and run the website without cloning the full
   Protos repository manually;
3. make a production build reproducible by identifying its exact Protos input;
4. keep website dependencies and deployment permissions out of the language
   implementation repository where practical;
5. keep local development simple;
6. support the ratified mix of landing pages, learning material, normative
   reference, design rationale, and community routing;
7. provide good static search and code-documentation ergonomics;
8. avoid unnecessary service/runtime infrastructure for a static site;
9. preserve a clean security boundary for any future code-executing playground;
   and
10. avoid a migration trap if the public surface grows.

## Repository topology

### R1 — Website inside `guillermomolina/protos`

Example: `website/` in the language repository.

Strengths:

- canonical sources are immediately available;
- atomic commits can update implementation/docs/site presentation together;
- no cross-repository source acquisition step.

Weaknesses:

- a website-only contributor must clone the language/runtime repository;
- Node/frontend dependencies, Docker/site tooling, deployment workflows and
  web-specific automation share the implementation repository;
- website workflow permissions and supply-chain exposure live closer to the
  canonical language repository;
- website lifecycle and language/runtime lifecycle become unnecessarily coupled;
- independent website experimentation creates more concurrent churn on `protos`.

Assessment: **viable but not preferred**.

### R2 — Independent companion repository consuming canonical Protos sources

`guillermomolina/protos-website` owns only web-specific code, layout, assets,
build/deployment machinery and web-curated pages. Canonical language,
documentation and examples remain in `guillermomolina/protos`.

Strengths:

- independent clone/run/deploy lifecycle;
- clear responsibility boundary analogous to `protos-benchmarks`;
- website dependency and workflow blast radius is separated from the language
  implementation repository;
- the website can consume only the public source subset it needs;
- the website can identify the exact Protos revision from which it was built;
- future website contributors need not obtain the full language development
  environment.

Costs:

- requires an explicit source-acquisition/provenance contract;
- updates to canonical documentation are not magically deployments of the
  website; the consumed revision must advance deliberately or through later
  automation.

Assessment: **recommended**.

### R3 — Independent repository with copied documentation

Strengths:

- simplest website build once copied material exists;
- fully independent runtime.

Weaknesses:

- directly violates the ratified WEB001-A source-authority intent;
- invites drift between site and repository;
- makes corrections/versioning ambiguous;
- eventually requires synchronization machinery to repair a problem created by
  the architecture itself.

Assessment: **reject**.

## Canonical-source acquisition

An independent repository still needs a deterministic way to obtain the selected
Protos material.

### S1 — Git submodule

Pros:

- Git-native exact revision;
- visibly pinned in the website tree.

Cons:

- clone/update ergonomics are frequently surprising;
- introduces submodule-specific contributor commands and CI handling;
- exposes the entire repository relationship when the site needs only selected
  content.

Assessment: acceptable, but unnecessary.

### S2 — Build-time exact-SHA shallow fetch with a website-owned source lock

The website stores a tiny source lock/manifest containing at least:

- canonical repository coordinate;
- exact 40-character Protos commit SHA.

A source-preparation script performs an exact shallow fetch and checks that the
materialized HEAD equals the requested revision. It then stages only the
canonical paths needed by the web build into an ignored/generated workspace.

Suggested canonical inputs initially include:

- `README.md`;
- `docs/guide/`;
- selected `docs/design/`;
- `spec/`;
- `protos/tutorials/`; and
- `protos/examples/`.

Pros:

- same provenance principle already used by `protos-benchmarks`;
- reproducible;
- no submodule UX;
- the host checkout contains only `protos-website`;
- generated copies are build inputs, not separately maintained sources;
- source selection can later become sparse without changing authority.

Cons:

- requires one small synchronization script and lock manifest;
- updating site-visible canonical material requires advancing the lock.

Assessment: **recommended**.

### S3 — Build silently from current `protos/main`

Pros:

- always sees latest repository material when a build starts.

Cons:

- the same website revision can produce different output over time;
- a build cannot be reproduced from the website commit alone;
- canonical changes can enter a website build without an explicit website-side
  provenance update;
- failures are harder to attribute.

Assessment: useful only as an explicit update command, **not as the production
build contract**.

### Source update contract

The recommended model is:

1. `protos-website` records an exact Protos SHA;
2. an explicit `update-protos-source` operation may resolve the desired current
   upstream revision and update that lock;
3. every ordinary development/CI/production build consumes the locked SHA, not a
   moving branch;
4. generated/staged canonical files are ignored and never committed as new
   authority; and
5. the built site exposes or records both the website revision and Protos source
   revision for provenance.

This separates "advance the source input" from "build the already-selected
input".

## Local execution contract

### L1 — Native Node only

Example:

```sh
npm ci
npm run dev
```

This is the fastest contributor loop but makes the local Node/toolchain version a
host prerequisite.

### L2 — Docker/Compose only

Example:

```sh
docker compose up --build
```

This maximizes environment reproducibility but makes every ordinary edit loop pay
container startup/build cost and makes Node tooling less transparent.

### L3 — One canonical application contract with two entry paths

Provide both:

```sh
npm ci
npm run dev
```

and:

```sh
docker compose up --build
```

Both use the same locked Protos source contract and the same underlying website
scripts. Compose is a reproducible wrapper, not a second implementation.

The initial Compose topology should contain one website service only. A future
playground does not become a second service merely for symmetry; it is introduced
only when that independently secured execution system exists.

Assessment: **recommended**.

A person interested only in the website should therefore be able to:

```sh
git clone https://github.com/guillermomolina/protos-website.git
cd protos-website
docker compose up --build
```

without manually cloning `guillermomolina/protos`.

## Static-site framework comparison

Current official framework documentation was reviewed on 2026-09-10.

### F1 — VitePress

Official sources:

- https://vitepress.dev/
- https://vitepress.dev/guide/getting-started
- https://vitepress.dev/reference/default-theme-search
- https://vitepress.dev/guide/custom-theme
- https://vitepress.dev/guide/deploy

Relevant properties:

- static, content-centric generator;
- excellent Markdown-first documentation ergonomics;
- built-in browser-side local full-text search via MiniSearch;
- customizable/replaceable Vue theme;
- simple development server and static deployment.

Strengths for Protos:

- smallest conceptual/tooling footprint among the leading JS candidates;
- excellent match for documentation-heavy content;
- easy GitHub Pages deployment.

Trade-offs:

- the project is primarily documentation-shaped;
- a strongly differentiated product landing experience and future non-doc web
  surfaces naturally pull the project further into custom Vue/theme work;
- adopting Vue as the site customization model is a stronger UI-framework choice
  than Protos currently needs.

Assessment: **strong second choice**.

### F2 — Astro + Starlight

Official sources:

- https://starlight.astro.build/
- https://starlight.astro.build/guides/pages/
- https://starlight.astro.build/guides/site-search/
- https://starlight.astro.build/reference/configuration/
- https://docs.astro.build/guides/deploy/github/

Relevant properties:

- Starlight supplies navigation, search, internationalization, SEO, code
  presentation, dark mode and documentation UX;
- Pagefind full-text search is built in for static output without requiring an
  external search service;
- Markdown/MDX content can coexist with fully custom Astro pages;
- custom pages retain access to Astro's general page-generation model;
- deployment remains static and works with GitHub Pages;
- current Astro requires Node.js 22.12.0 or newer.

Strengths for Protos:

- documentation ergonomics comparable to a docs-specific generator;
- a first-class custom landing/product surface without replacing the docs stack;
- room for later download, benchmark, package or interactive integration without
  first converting a docs generator into a general site;
- no required client UI framework for the basic site;
- React/Vue/Svelte/etc. can be introduced only for islands that actually need
  them;
- Pagefind satisfies initial search without another hosted service.

Costs:

- somewhat more framework surface than VitePress;
- Astro + Starlight introduces two related layers rather than one;
- current Node baseline is newer.

Assessment: **recommended**.

### F3 — Docusaurus

Official sources:

- https://docusaurus.io/docs/
- https://docusaurus.io/docs/versioning
- https://docusaurus.io/docs/search
- https://docusaurus.io/docs/deployment

Strengths:

- mature documentation platform;
- established versioning;
- mature plugin ecosystem;
- React customization;
- first-class Algolia DocSearch integration.

Trade-offs for current Protos:

- documentation versioning is not currently required and Docusaurus itself warns
  that versioning increases contributor/build complexity when not needed;
- default search strength centers on Algolia while local search is community
  supplied;
- React is introduced as a broad site-level implementation choice despite no
  current requirement for a client application;
- more machinery than needed for the initial static website.

Assessment: **not recommended for the initial site**.

### Framework recommendation

**Recommend Astro + Starlight.**

VitePress would be the preferred fallback if minimizing framework surface were
the dominant criterion. Astro + Starlight wins because WEB001 is explicitly more
than a reference manual: it requires a differentiated Home plus Learn,
Reference, Design and Community, while keeping a static, low-operations
deployment. Starlight supplies the documentation half; Astro supplies the
general website half without forcing all pages into a client framework.

## Hosting comparison

### H1 — GitHub Pages via GitHub Actions

Official sources:

- https://docs.github.com/pages/
- https://docs.github.com/pages/configuring-a-custom-domain-for-your-github-pages-site/about-custom-domains-and-github-pages
- https://docs.astro.build/guides/deploy/github/

Properties:

- static hosting integrated with the repository;
- custom domains supported;
- GitHub recommends verifying custom domains to reduce takeover risk;
- Astro has an official GitHub Pages deployment path;
- Actions can use the dedicated Pages deployment flow;
- no additional hosting vendor account or deploy credential is required for the
  basic repository-to-Pages path.

Strengths:

- smallest operational surface for the current project;
- deployment authority stays within the same GitHub repository/account boundary;
- suitable for a fully static Astro/Starlight output;
- no application server to patch or expose.

Limitation:

- no first-class per-PR preview environment comparable to dedicated web hosts.

Assessment: **recommended initially**.

### H2 — Cloudflare Pages

Official sources:

- https://developers.cloudflare.com/pages/configuration/git-integration/
- https://developers.cloudflare.com/pages/configuration/preview-deployments/
- https://developers.cloudflare.com/pages/configuration/custom-domains/

Strengths:

- direct GitHub integration;
- automatic branch/PR preview deployments;
- custom-domain support;
- mature static edge delivery.

Trade-offs:

- adds another account/provider/integration to a site that does not yet need
  edge-specific behavior;
- Git integration grants a third-party system repository access within the
  configured installation scope;
- provider-specific project configuration becomes another operational surface.

Assessment: **excellent later option if preview/edge features become valuable,
but not necessary now**.

### H3 — Vercel / Netlify class of hosted build platforms

These can host Astro static output well and generally provide polished previews,
but introduce another hosting/control plane without a present requirement that
GitHub Pages cannot satisfy.

Assessment: defer.

### Hosting recommendation

**Recommend GitHub Pages via GitHub Actions for the initial site**, with
`protos.guillermomolina.com` as the custom domain.

Do not make Pages a permanent product dependency: the build artifact must remain
ordinary static output so a later move to Cloudflare Pages or another static
host is operational rather than architectural.

## Security model

The source of a public static website is not secret. Security comes from keeping
authority and secrets out of the browser/build where they are not needed.

Recommended boundary:

```text
guillermomolina/protos
    canonical language/docs/spec/examples
                |
                | public read-only, exact SHA
                v
guillermomolina/protos-website
    Astro/Starlight + web assets + source lock
                |
                | static build
                v
GitHub Pages
    protos.guillermomolina.com
```

Security consequences:

- the website needs **no write credential to `guillermomolina/protos`**;
- canonical Protos acquisition is public read-only;
- production output is static;
- no database, CMS, server session or application secret is required initially;
- workflow permissions should be explicit and least-privilege;
- third-party Actions should be pinned to immutable full commit SHAs where
  practical rather than mutable tags;
- generated canonical-source staging directories and build artifacts must not
  become independently maintained content;
- dependency updates remain website-repository changes and cannot silently alter
  the Protos implementation repository.

The custom domain should be verified through GitHub before/while binding it to
Pages, following GitHub's takeover-protection guidance.

## Future playground boundary

A future playground changes the threat model because it executes untrusted user
programs.

It must therefore **not** be treated as ordinary Astro/Starlight functionality
and must not inherit website deployment authority merely because its UI appears
under the same domain.

The preferred long-term shape is:

```text
static protos-website
        |
        | narrow API boundary, only if/when approved
        v
independent playground execution service
    sandboxing
    resource limits
    execution isolation
    separate deployment authority
```

Whether that execution service uses another repository is intentionally deferred
until the playground is designed. The durable requirement here is security and
deployment isolation, not repository-count symmetry.

## Combined alternatives

### Candidate A — monorepo + VitePress + GitHub Pages

Simple and viable, but couples web tooling to the language repository.

### Candidate B — companion repo + VitePress + GitHub Pages

Very good. Strong separation and low complexity. Less flexible than Astro for
the non-documentation half of WEB001.

### Candidate C — companion repo + Astro/Starlight + GitHub Pages

Preserves source authority, independent execution, reproducibility and
least-privilege deployment while giving both a first-class product landing layer
and first-class documentation layer.

**Recommended.**

### Candidate D — companion repo + Docusaurus + GitHub Pages

Mature but pays for React/versioning/search complexity before Protos needs it.

### Candidate E — companion repo + Astro/Starlight + Cloudflare Pages

Technically strong and gives excellent previews, but adds a second control plane
before preview/edge requirements justify it.

## Selected architecture

**Candidate C** is selected, with these components ratified together:

- repository: `guillermomolina/protos-website` as independent companion repo;
- authority: canonical Protos sources remain in `guillermomolina/protos`;
- source acquisition: exact-SHA shallow fetch from a website-owned source lock,
  with generated content never becoming independent authority;
- local execution: both native Node and `docker compose up --build`, backed by
  the same scripts/contracts;
- framework: Astro + Starlight;
- output: static site;
- hosting: GitHub Pages via GitHub Actions;
- domain: `protos.guillermomolina.com`;
- provenance: retain both website revision and exact consumed Protos revision;
- future playground: separate execution/security boundary.

### Why this is the most Protos-aligned option

It keeps responsibilities ordinary and explicit:

- the language repository owns language/documentation truth;
- the website repository owns website behavior;
- a small explicit revision lock connects them;
- the build pays for no server it does not use;
- the static site does not gain execution authority merely because a future
  playground may exist;
- complexity is added at the boundary where it is actually needed.

It also scales without requiring a different model later: richer public
reference and product surfaces remain static website concerns, while genuinely
different execution/security concerns cross an explicit service boundary.

## Choices intentionally deferred

Even if this architecture is approved, WEB001-B does not yet decide:

- exact Astro/Starlight dependency versions;
- exact Node container image digest;
- exact full SHAs for deployment Actions;
- internal component/file naming;
- CSS/design tokens derived from the selected logo;
- whether source-lock advancement is manual or later automated;
- package/download/benchmark integration details;
- playground protocol, runtime, sandbox or repository topology.

Those are implementation details or later substantive checkpoints as applicable.

## Ratification record

On 2026-09-10, the project owner explicitly approved WEB001-B Candidate C.

The selected website architecture is therefore:

- **repository topology:** independent public companion repository
  `guillermomolina/protos-website`;
- **canonical-source authority:** `guillermomolina/protos` remains authoritative
  for Protos language, specification, maintained documentation, tutorials and
  examples;
- **source consumption:** the website records an exact 40-character Protos Git
  revision and materializes canonical source input read-only from that exact
  revision; production builds do not silently consume a moving branch;
- **local execution:** the website supports both native Node execution and
  `docker compose up --build`, with both paths using the same underlying source
  and build contracts;
- **framework:** Astro + Starlight;
- **output:** ordinary static site output;
- **initial hosting:** GitHub Pages via GitHub Actions;
- **public domain:** `protos.guillermomolina.com`;
- **provenance:** retained website revision plus exact consumed Protos revision;
- **security boundary:** the static website requires no write authority over
  `guillermomolina/protos`; and
- **future playground:** any service that executes untrusted Protos code remains
  an independently secured execution/deployment boundary rather than ordinary
  website runtime functionality.

WEB001-B is **RATIFIED / CLOSED**.

This ratification selects the durable architecture above but does not bootstrap
`guillermomolina/protos-website`, select exact dependency versions or container
digests, configure GitHub Pages, bind DNS, publish the selected logo, or
implement a playground. Those remain downstream implementation or later
architecture checkpoints as applicable.
