# Protos news and milestones

This directory records major public-facing Protos milestones.

It is intentionally **not** a work-tracking system. Bugs, implementation work,
design decisions and follow-up tasks remain in GitHub Issues and the Protos
Development Project. Entries here are durable summaries of milestones that are
worth surfacing to users and contributors.

News entries are **not formal Issue families**: they do not receive
`family:*`, `status:*` or `priority:*` labels, do not become Project work items,
and do not participate in Issue intake, hierarchy, dependency or work-queue
automation.

GitHub Discussions **Announcements** are the conversational/community-facing
surface for important news. `docs/news/` is the durable, versioned archive.
Major milestones may appear in both; routine Announcements do not need a
permanent news entry.

## 2026

- **2026-09-13 — [PERF006 complete: Protos reaches the optimizing Truffle runtime](2026-09-13-perf006-complete.md)**
  C′ becomes the production execution generation, the historical replay
  bottleneck disappears, and controlled exact-source measurements show
  1.06x–1.73x steady-state optimizer gains across all five retained workloads.

- **2026-09-11 — [Protos receives its first community contribution](2026-09-11-first-community-contribution.md)**
  PR #315 by `jabrailkhalil` adds an executable isolated-parallel cookbook
  program and becomes the first external community pull request merged into
  Protos.
