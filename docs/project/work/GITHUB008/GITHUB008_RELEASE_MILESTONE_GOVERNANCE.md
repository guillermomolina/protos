# GITHUB008 — Release milestone governance

Status: **CLOSED / ACTIVE**

Owning live Issue: GitHub #320.

This record governs GitHub Milestone usage for Protos. It does not define Protos
language semantics, specification authority, implementation behavior, semantic
versioning policy, release compatibility guarantees, or the contents/date of any
future release.

## Current evidence

At GITHUB008 authoring time:

- GitHub Issue search returned no open or closed Issue assigned to any milestone;
- the repository had one public GitHub Release, `v0.2.236`, published as a
  prerelease;
- `main` used Maven implementation version `0.2.347-SNAPSHOT`; and
- GITHUB004–GITHUB007 already owned live Status/Priority projection, native Issue
  hierarchy, and Issue intake convergence.

This means Milestones are currently unused for active Issue planning and can be
given a narrow meaning without migrating existing milestone-assigned work.

## Selected responsibility split

| GitHub mechanism | Protos responsibility |
| --- | --- |
| Project Status / Priority | live scheduling and lifecycle |
| Native Parent/Sub-issue | formal work hierarchy |
| `family:*` | formal work-family classification |
| Milestone | selected release target / release gate |
| Git tag | immutable source identity |
| GitHub Release | published downloadable iteration |

Milestones deliberately do not duplicate Project Roadmap, Priority, Status,
families, or hierarchy.

## Release milestone contract

A release milestone:

1. exists only after the project owner selects a concrete intended release;
2. is titled with the release version without the tag prefix, for example
   `0.3.0`;
3. may have a due date only when a real commitment exists;
4. contains only Issues/PRs whose completion genuinely gates or belongs to the
   selected release;
5. does not inherit automatically through Issue hierarchy;
6. avoids duplicate progress accounting through container parents plus every
   child unless both independently gate the release; and
7. closes only when the release outcome is published or explicitly abandoned
   and reconciled.

Milestone membership is not inferred from family, status, priority, Project
Roadmap, title, parent relationship, implementation-version movement, or general
activity.

## Relationship to tags and Releases

For a selected release version `X.Y.Z`:

```text
Milestone: X.Y.Z
Tag:       vX.Y.Z
Release:   Protos X.Y.Z
```

These are related but distinct artifacts. A milestone plans/gates the release;
the tag identifies the exact source point; the GitHub Release publishes the
deliverable and notes/assets.

The existing `v0.2.236` prerelease remains historical release evidence and is not
given a retrospective milestone merely for symmetry.

## Deferred release target

GITHUB008 intentionally does **not** create `0.3.0` or any other future
milestone. Choosing the next public Protos release target, scope, and optional
date remains an explicit project-owner release/scheduling decision.

## Automation boundary

Future release automation may check a selected milestone for open gate items or
use milestone membership to generate candidate release notes. It may not:

- invent the next version;
- create a milestone from Maven version movement;
- auto-assign all Issues of a family or parent tree;
- treat 100% milestone completion as publication approval; or
- rewrite Project Status/Priority based on milestone state.

## Closure

GITHUB008 is closed with publication of this policy because there is no existing
milestone-assigned Issue set to migrate and no live automation/milestone mutation
is part of the change. The policy remains active for future release planning.
