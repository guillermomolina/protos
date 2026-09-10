# WEB001 — Protos project website

GitHub coordination: Issue [#278](https://github.com/guillermomolina/protos/issues/278), labelled `family:WEB`.

## Purpose

`WEB001` establishes the official public-facing Protos project website at
`protos.guillermomolina.com` as the entry point for people who want to understand,
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
5. can publish automatically to `protos.guillermomolina.com`; and
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
- `docs/project/IMPLEMENTATION_STATUS.md` is not a live status mirror and is not
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
