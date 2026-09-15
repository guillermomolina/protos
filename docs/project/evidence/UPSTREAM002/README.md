# UPSTREAM002 evidence

UPSTREAM002 owns retained Protos-side evidence for the GraalVM Community
container base migration from Oracle Linux 8 to Oracle Linux 10, evaluated
under GitHub Issue #525.

Coordination remains in GitHub Issue #525. The retained evidence is deliberately
separate from live issue state: this directory records what was actually
observed in the OL10 container, with exact environment baselines and explicit
limits on the conclusions.

## Records

- [`UPSTREAM002_OL10_ENVIRONMENT_SNAPSHOT.md`](UPSTREAM002_OL10_ENVIRONMENT_SNAPSHOT.md)
  — exact OS/runtime/Maven/Python/GitHub-CLI identity observed in the
  devcontainer built from the OL10 image.
- [`UPSTREAM002_OL10_VALIDATION_EVIDENCE.md`](UPSTREAM002_OL10_VALIDATION_EVIDENCE.md)
  — retained validation evidence: full Maven suite, toolchain verifier tests
  and binding state, and the portable-distribution validation handoff.

## Authority boundary

This evidence is non-normative. It does not authorize an image, toolchain, or
repository change by itself: the repository-side migration is owned by DIST004
(GitHub #526) and the exact DIST002 Maven coordinate remains project authority.
UPSTREAM002 is classified `ACTION_REQUIRED` with DIST004 as the owning work item.
