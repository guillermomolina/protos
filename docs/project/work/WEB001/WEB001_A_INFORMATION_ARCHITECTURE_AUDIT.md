# WEB001-A — Public information architecture and source-authority audit

GitHub coordination: Issue
[#280](https://github.com/guillermomolina/protos/issues/280), child of
WEB001 / [#278](https://github.com/guillermomolina/protos/issues/278).

Status: **RATIFIED — OPTION B SELECTED**

## Scope

This slice audits how the official Protos website should organize public
information and how that presentation should relate to authoritative and
maintained repository sources.

It deliberately does **not** select a website framework, hosting provider,
deployment pipeline, DNS arrangement, visual design system, or playground
architecture. Those choices remain downstream of this content/visitor contract.

No statement in this document defines Protos language semantics. `spec/` remains
the normative authority.

## Current Protos public-information surfaces

The repository already contains strong material, but it is organized by
repository responsibility rather than by a new visitor's journey.

### Project identity and first explanation

`README.md` already provides the concise project identity, the "Why Protos?"
argument, first-look code, design goals, and links into deeper material. It is
the strongest current source for the facts and language concepts that a website
home page should summarize, but the web landing page should be a curated
presentation rather than a second independently maintained copy of the README.

### Learning

`docs/guide/README.md` defines the Programming Guide as non-normative learning
material and explicitly distinguishes three complementary learning surfaces:

- conceptual guide chapters under `docs/guide/`;
- progressive executable programs under `protos/tutorials/`; and
- task-oriented examples under `protos/examples/`.

That separation should be preserved on the website rather than flattened into
one undifferentiated "Docs" bucket.

### Design rationale

`docs/design/PROTOS_DESIGN_PHILOSOPHY.md` explains why Protos aims for a small
semantic universe that scales by composition. It is suitable as the primary
source for a public Design section, provided the website keeps its non-normative
status visible.

### Normative reference

`spec/` is the authoritative source for observable syntax and semantics. The
website may render, index, navigate, or link that material, but must not fork or
silently rewrite it into a second semantic authority.

### Project/community coordination

GitHub Issues, Discussions, the repository, and the optional Protos Development
Project already own project coordination and community work. The website should
route users to those surfaces rather than mirror changing issue state,
priorities, assignees, roadmaps, or execution logs.

## Visitor journeys

The initial website should optimize for five distinct visitor questions.

1. **What is Protos and why should I care?**
   The home page should answer this in one short visit with identity, a small
   code sample, the core design thesis, project maturity, and clear next actions.
2. **How do I learn Protos?**
   `Learn` should provide a progressive path through getting started, the
   Programming Guide, tutorials, and examples.
3. **What exactly does the language guarantee?**
   `Reference` should lead to normative language/specification material and,
   when ready, Standard Library reference material.
4. **Why was Protos designed this way?**
   `Design` should expose the non-normative philosophy and selected explanatory
   design material without presenting proposals as guarantees.
5. **How do I follow or contribute to the project?**
   `Community` should route to contribution guidance, Discussions, Issues,
   releases and other canonical GitHub/project surfaces.

These are visitor roles, not repository directory names.

## External prior art

Representative official programming-language sites were reviewed on
2026-09-10. The purpose is not to copy their visual identity, but to test whether
the proposed Protos structure follows patterns that scale for real language
projects.

### Rust

Sources:

- https://rust-lang.org/
- https://rust-lang.org/learn/

Rust separates a proposition-led landing page from a dedicated learning route.
The home page explains why the language matters; the Learn surface then offers
different learning modes and core documentation. The useful lesson for Protos is
that "why" and "learn" should be adjacent but not collapsed.

### Zig

Sources:

- https://ziglang.org/
- https://ziglang.org/learn/
- https://ziglang.org/learn/getting-started/

Zig keeps a very direct home page, then separates Learn, versioned language
reference / Standard Library documentation, downloads, and getting started.
This is especially relevant to Protos because a young language can expose an
honest development state without making the repository itself the user
experience.

### Gleam

Source:

- https://gleam.run/documentation/

Gleam's documentation hub explicitly separates learning material, references,
guides, cheatsheets, deployment, and community resources. That validates a
journey-based structure and, particularly, the distinction between learning
material and reference material.

### Elixir

Sources:

- https://elixir-lang.org/learning/
- https://elixir-lang.org/docs/

Elixir separates Learning from versioned API/reference documentation. The
important lesson for Protos is that tutorial/educational material and exact
reference serve different visitor intents and should remain independently
navigable.

### Julia

Sources:

- https://julialang.org/
- https://docs.julialang.org/

Julia likewise separates project positioning from its documentation/manual
surface and makes community and installation paths prominent. It reinforces the
value of a small project-facing front door with deeper reference behind it.

## Information-architecture alternatives

### Option A — repository-shaped navigation

Example:

`README / Guide / Tutorials / Examples / Design / Spec / GitHub`

Advantages:

- almost no mapping layer;
- obvious provenance for maintainers;
- straightforward to implement.

Problems:

- exposes repository organization to people who do not yet know the project;
- makes `Guide`, `Tutorials`, and `Spec` peer categories without explaining
  their different purposes;
- scales poorly when releases, Standard Library reference, benchmarks, packages,
  tools, or a playground become public;
- encourages the website to become a file browser.

Assessment: **not recommended**.

### Option B — visitor-journey navigation

Top level:

`Home / Learn / Reference / Design / Community / GitHub`

Repository sources remain underneath those public roles.

Advantages:

- each destination answers a clear visitor question;
- learning and normative reference remain distinct;
- the website can grow without exposing internal work-family structure;
- the same navigation still works when downloads, packages, benchmarks, or a
  playground mature;
- source authority can remain explicit within every section.

Costs:

- requires a deliberate mapping from repository sources to public routes;
- some content needs short web-specific introductions/navigation pages.

Assessment: **recommended**.

### Option C — single documentation portal plus minimal landing page

Example:

`Home / Docs / GitHub`, with most material below `Docs`.

Advantages:

- small top-level navigation;
- common static-documentation-site model;
- easy search surface.

Problems:

- "Docs" becomes an overloaded bucket containing learning, normative reference,
  rationale, community instructions, and eventually tool/package material;
- makes important authority distinctions less visible;
- becomes harder to keep approachable as Protos grows.

Assessment: viable for a very small site, but **less future-proof than Option B**.

## Ratified public route contract

The selected top-level contract is:

```text
/
├── learn/
│   ├── getting-started/
│   ├── guide/
│   ├── tutorials/
│   └── examples/
├── reference/
│   ├── language/
│   └── standard-library/     # when sufficiently mature
├── design/
├── community/
└── [GitHub]                  # canonical external project/development surface
```

Future additions such as Downloads/Releases, Packages, Benchmarks, or Playground
should be promoted to first-class navigation only when the underlying product
surface is mature enough to justify it. They must not be pre-created as empty
marketing sections.

## Source-authority contract

The recommended contract is **render/curate from canonical sources, do not fork
them**.

| Public surface | Primary source/owner | Website behavior |
|---|---|---|
| Home | WEB001 curated presentation grounded in current public project facts | Web-specific concise composition; link deeper |
| Learn / Guide | `docs/guide/` / DOC001 | Render or integrate from canonical files; do not maintain a copied guide |
| Learn / Tutorials | `protos/tutorials/` | Present executable tutorial sources with minimal web framing |
| Learn / Examples | `protos/examples/` | Present task-oriented examples with minimal web framing |
| Design | `docs/design/` | Render selected maintained non-normative material with visible authority status |
| Reference / Language | `spec/` | Render/index canonical normative sources or link them; never rewrite semantics independently |
| Reference / Standard Library | applicable normative/reference sources | Add when source structure is mature enough; retain explicit authority |
| Community | repository community files + GitHub surfaces | Curated links/navigation; no mirrored live coordination state |
| Project status | GitHub Issues/Project + durable repository evidence | Link only; do not create a second status database |

Every rendered source-backed page should be able to expose a source link
("View source" / "Edit on GitHub" where appropriate) so provenance stays
auditable.

## Initial-launch boundary

The recommended first useful website does not need every future section fully
materialized.

It should launch with:

- a strong Home page;
- `Learn`, integrating the already-maintained guide/tutorial/example structure;
- `Design`, beginning with the current design philosophy;
- `Community`, routing to canonical GitHub/community surfaces; and
- a visible `Reference` entry that leads to canonical specification material,
  even if richer specification rendering/search arrives in a later slice.

This produces a coherent public site without waiting for package tooling,
complete Standard Library reference generation, downloads automation, benchmark
integration, or a playground.

## Ratified information architecture

**Option B — visitor-journey navigation** is selected, together with the
source-authority contract above.

The important design property is not the exact label spelling; it is the stable
separation of:

- project proposition (`Home`);
- learning (`Learn`);
- exact/normative lookup (`Reference`);
- rationale (`Design`);
- participation (`Community`); and
- canonical development (`GitHub`).

This structure is scalable, keeps authority boundaries visible, and matches the
way mature language projects separate user intents without forcing Protos to
copy any one project's information architecture.

### Ratification record

On 2026-09-10, the project owner explicitly approved Option B and its
source-authority contract. WEB001-A is therefore **RATIFIED / CLOSED**.

The ratified website information architecture separates project proposition
(`Home`), learning (`Learn`), exact/normative lookup (`Reference`), rationale
(`Design`), participation (`Community`), and canonical development (`GitHub`).
Public source-backed material is rendered or curated from its canonical owner
rather than copied into an independently drifting content fork.

This ratification does **not** select repository topology, website framework,
hosting provider, deployment pipeline, DNS implementation, visual design system,
or playground architecture. Those remain downstream WEB001 architecture
questions. The next checkpoint is the repository/technology/deployment comparison.
