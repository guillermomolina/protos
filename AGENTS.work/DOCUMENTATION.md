# Protos documentation work

These instructions apply to `DOCxxx` and to documentation editing in any
family.

Investigation contract: the documentation surface plus the governing
specification sections for the behavior being documented — enough to avoid
documenting planned behavior as existing. No exhaustive implementation audit
is required.

## Documentation work

Substantial documentation initiatives with an independently meaningful project
lifecycle use the `DOCxxx` family.

`DOCxxx` is for documentation whose primary deliverable is public or project
explanation, learning material, navigation, conceptual guidance, or another
maintained documentation surface that is useful to track independently from an
implementation item.

Do not use `DOCxxx` merely because a change edits Markdown. Documentation that is
part of closing an `Ixxx`, `LIBxxx`, `TOOLxxx`, `CLIxxx`, `LMxxx`, `PERFxxx`, or
other already-owned work item remains part of that owning family.

Keep these boundaries explicit:

- `spec/` remains the normative authority for Protos language semantics;
  `DOCxxx` MUST NOT create, complete, or reinterpret missing normative behavior;
- implementation status remains owned by the applicable implementation family;
- executable conformance/tutorial dogfooding whose primary purpose is maturity
  coverage remains `LMxxx`;
- a future bundled documentation generator/tool is `TOOLxxx`, not `DOCxxx`;
  `DOCxxx` owns documentation content/lifecycle, not toolchain orchestration.

When documentation discovers a genuine normative ambiguity, record the applicable
normative blocker and mark only the affected `DOCxxx` slice blocked. Do not block
independent documentation work that can proceed from already-defined semantics.

New independently tracked documentation work uses the next unused `DOCxxx`
identifier, creates/uses its GitHub Issue for live coordination, and records it
in an owning durable project record when the first repository publication
materially establishes the work. Operational `DOCxxx` identifiers MUST NOT exist
only in prompts or chat history.

The first tracked documentation initiative is `DOC001 — Protos Programming
Documentation`, owned by
`guillermomolina/protos-project-docs:docs/project/work/DOC001/DOC001_PROGRAMMING_DOCUMENTATION.md`.

## Documentation quality

Developer documentation must describe the current implementation and specification accurately.

Do not document planned behavior as though it already exists.

When implementation differs intentionally from the specification because work is incomplete, state that explicitly.

Examples in documentation must use valid Protos syntax according to the current grammar.

The README is an introduction to the project, not an alternative language specification. Detailed semantic rules belong in the canonical specification documents.

## Documentation editing

When editing existing documentation:

- Make minimal, localized edits. Do not reflow, rewrap, or reconstruct surrounding paragraphs unless explicitly required.
- Preserve existing line breaks, paragraph boundaries, Markdown formatting, and intentional whitespace outside the exact text being changed.
- Do not concatenate words, punctuation, inline code, or Markdown elements across edit boundaries.
- After editing, inspect the actual resulting file content around every changed region rather than relying only on the generated patch.
- Before reporting completion, run `git diff --check` and review `git diff` for accidental whitespace, missing spaces, line-break changes, or unrelated formatting changes.
- Treat existing Markdown hard line breaks (two trailing spaces) as intentional unless the task explicitly requests formatting cleanup.
- Do not perform unrelated formatting cleanup in a semantic specification revision.

## Public project news and social announcements

Agents preparing a significant public project announcement MUST provide:

- an English canonical announcement for GitHub Discussions — Announcements
  (`https://github.com/guillermomolina/protos/discussions/categories/announcements`);
- an English Reddit post for `r/Protos` (`https://www.reddit.com/r/Protos/`); and
- a concise English X post for `@ProtosLang` (`https://x.com/ProtosLang`).

Content rules:

- Reddit receives a substantive, self-contained explanation of the milestone, its significance, relevant technical details, and links to canonical evidence.
- Use the `Announcement` flair for project announcements, `Release` for releases, and an appropriate existing technical flair when preparing other community posts.
- X receives a short, understandable summary and a canonical source link.
- Use hashtags sparingly and only when relevant.
- Do not publish internal implementation checkpoints automatically.
- Significant completed features, releases, verified performance milestones, major tooling improvements, and project announcements are suitable news.
- Published technical claims must match the actual published implementation and evidence.
- GitHub Discussions — Announcements remains the canonical news authority.
- Do not maintain duplicate versioned Markdown news archives.
- The website consumes canonical announcements dynamically.
- Keep social publication human-executed unless explicitly authorized otherwise.
- Record actual publication URLs when available and identify pending publications.

For release announcements, follow `AGENTS.work/RELEASE.md` (section "Public release announcements") instead of duplicating its release-specific contract here.
