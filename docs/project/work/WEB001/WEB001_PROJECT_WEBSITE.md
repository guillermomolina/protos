# WEB001 — Protos project website

GitHub coordination: Issue [#278](https://github.com/guillermomolina/protos/issues/278), labelled `family:WEB`.

## Purpose

`WEB001` establishes the official public-facing Protos project website at
`protos.guillermolina.com` as the entry point for people who want to understand,
learn, use, follow, or contribute to Protos.

The website and GitHub have deliberately different jobs:

- the website presents the language and project to users and newcomers;
- GitHub Issues and Discussions remain the live development, coordination, and
  community-work surfaces; and
- repository sources remain authoritative for the material they own.

This opening record allocates the first `WEBxxx` work item and defines the family
boundary. It does **not** ratify a website framework, hosting provider, build
pipeline, visual design system, or documentation-generation architecture.

## `WEBxxx` family boundary

`WEBxxx` tracks independently meaningful work whose primary deliverable is the
official Protos web presence or website-specific delivery machinery.

Appropriate `WEBxxx` scope includes:

- website information architecture and navigation;
- landing/product presentation and web-specific user experience;
- website-specific integration of maintained documentation;
- web build and publication machinery;
- hosting and custom-domain delivery for the official project website; and
- web-only adoption/community surfaces that connect users to the canonical
  repository, documentation, Issues, Discussions, releases, or other project
  resources.

The family does not absorb unrelated ownership merely because content is visible
on the web:

- `spec/` remains normative for observable Protos language and Standard Library
  semantics;
- substantial documentation initiatives whose primary deliverable is maintained
  documentation remain `DOCxxx` unless the work is specifically about the web
  presentation/delivery layer;
- bundled developer tools remain `TOOLxxx`;
- distribution/release work retains its existing family ownership; and
- live scheduling, assignment, execution logs, and work status remain in GitHub
  rather than being mirrored into the website as a second coordination system.

If website implementation exposes a substantive language, ecosystem, security,
identity, compatibility, or durable architecture decision, the affected slice
must stop at the normal explicit-approval gate instead of settling that decision
inside `WEBxxx` implementation work.

## WEB001 opening charter

The intended public outcome is a website that:

1. explains what Protos is and why it is different within a short first visit;
2. gives a clear route into learning material and language/reference material;
3. presents design philosophy without competing with normative specification
   authority;
4. directs contributors and interested users to the appropriate GitHub community
   and coordination surfaces;
5. can publish automatically to `protos.guillermolina.com`; and
6. remains simple enough to maintain without creating an unnecessary second
   software product around the language project.

The structure should be able to grow later to expose specification, Standard
Library reference, releases/downloads, benchmark material, and an interactive
playground when those surfaces are mature enough, without requiring those
features in the initial launch.

## Architecture checkpoint

No implementation stack is selected by this record. VitePress + GitHub Pages is
a candidate previously identified for evaluation, not an approved decision.
Alternatives must be compared against at least maintainability, repository-source
reuse, static hosting/deployment simplicity, custom-domain support, search and
code-documentation ergonomics, contributor workflow, future playground needs,
and avoidable platform lock-in before a durable stack choice is published.

Likewise, this record does not pre-allocate child identifiers. Information
architecture/content contract, technology/deployment selection, landing
experience, documentation integration, community/adoption surfaces, domain
publication, and launch validation are candidate decomposition boundaries only.
Create durable child tracking only when a slice has independently meaningful
project identity under the repository governance rules.

## Authority and lifecycle

- GitHub Issue #278 is the canonical live coordination surface for `WEB001`.
- This file is the durable non-normative project record for the family opening and
  WEB001 charter.
- `spec/` remains the normative language authority.
- `docs/project/history/OPEN_TASKS.md` remains historical and is not updated by
  WEB001.
- `docs/project/registries/IMPLEMENTATION_STATUS.md` is not a live status mirror and is not
  updated merely to open WEB001.
- Opening WEB001 changes no Protos implementation version, runtime behavior,
  public language API, or specification semantics.

<!-- WEB001-A-PUBLIC-INFORMATION-ARCHITECTURE-AUDIT -->
## WEB001-A — public information architecture and source-authority audit

GitHub Issue [#280](https://github.com/guillermomolina/protos/issues/280) owns
the independently reviewable WEB001-A audit.

`WEB001_A_INFORMATION_ARCHITECTURE_AUDIT.md` compares repository-shaped,
visitor-journey, and single-docs-portal information architectures; maps the
current README, Programming Guide/tutorials/examples, design philosophy,
normative specification, and GitHub community surfaces to public website roles;
and records representative prior art from Rust, Zig, Gleam, Elixir, and Julia.

The project owner explicitly approved the WEB001-A recommendation on
2026-09-10. The visitor-journey top level
`Home / Learn / Reference / Design / Community / GitHub` and the
render/curate-from-canonical-sources contract are **RATIFIED**; WEB001-A is
**CLOSED**. Repository topology, website framework, hosting, deployment, DNS,
visual design, and playground architecture remain unselected downstream
questions.

<!-- WEB001-B-WEBSITE-ARCHITECTURE-AUDIT -->
## WEB001-B — website repository, stack and deployment architecture audit

GitHub Issue [#282](https://github.com/guillermomolina/protos/issues/282) owns
the independently reviewable WEB001-B architecture audit.

`WEB001_B_WEBSITE_ARCHITECTURE_AUDIT.md` evaluates repository topology,
canonical-source acquisition, local execution, static-site frameworks, hosting,
deployment/security boundaries, and future playground isolation.

The project owner explicitly approved WEB001-B Candidate C on 2026-09-10.
The website architecture is **RATIFIED** and WEB001-B is **CLOSED**.

The selected architecture uses independent companion repository
`guillermomolina/protos-website`; read-only exact-SHA acquisition of canonical
Protos sources from `guillermomolina/protos`; equivalent native-Node and
Docker-Compose local execution; Astro + Starlight static generation; explicit
website/Protos revision provenance; and an independently secured future
playground execution boundary.

WEB001-B originally selected GitHub Pages for the initial
`protos.guillermolina.com` deployment. That **hosting component only** is
superseded by the later WEB001-F production-hosting ratification below. The
repository topology, source-authority, exact-SHA acquisition, local execution,
framework, static-output, provenance and playground-boundary decisions remain
ratified.

No website bootstrap, dependency pin, production-host implementation, DNS
change, logo publication, or playground implementation is included in the
WEB001-B ratification.

<!-- WEB001-F-PRODUCTION-HOSTING-ARCHITECTURE -->
## WEB001-F — production hosting architecture

GitHub Issue [#294](https://github.com/guillermomolina/protos/issues/294) owns
the production-hosting architecture checkpoint.

After the initial website was bootstrapped and reviewed, the project owner
explicitly approved WEB001-F Candidate B on 2026-09-10: the official static site
will be self-hosted through a private container/reverse-proxy environment rather
than GitHub Pages.

The ratified production boundary is:

```text
guillermomolina/protos
    canonical source authority
            |
            | public read-only exact SHA
            v
guillermomolina/protos-website
    portable Astro/Starlight source + generic production image contract
            |
            | immutable static build/image
            v
private deployment environment
            |
            | environment-specific routing
            v
reverse proxy
    TLS termination
            |
            v
protos.guillermolina.com
```

The public website repository owns only portable website/build behavior. All
environment-specific orchestration, routing, network names, host paths,
credentials and operational deployment configuration remain private
infrastructure concerns.

Production must not bind-mount the website source tree into the running
container. A deployment builds an immutable image from an identified website
revision, copies only the generated static site into a minimal HTTP-serving
runtime, and lets the private reverse proxy terminate TLS externally.

GitHub Pages is no longer an active or standby production path for WEB001. The
existing dormant/manual Pages workflow is to be retired by the bounded website
implementation slice so there is one production authority rather than two.

This hosting change does **not** alter the WEB001-B decisions for companion-repo
topology, exact-SHA canonical-source consumption, native/Docker local execution,
Astro + Starlight, static output, provenance, or the independent security
boundary required by any future code-executing playground.

WEB001-F is **RATIFIED / CLOSED** at the design level. Its implementation is
downstream: first provide the generic public production Docker target, then add
the environment-specific private production stack, and finally perform
DNS/production activation as an explicit operational step.
