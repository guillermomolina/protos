# GITHUB002 PR-first generated launcher mechanics

Status: adopted by `GITHUB002-D` when its Pull Request is merged.

This is project tooling/governance, not Protos language semantics.

## Artifact-owned identity

Every generated ZIP has a stable `ARTIFACT_ID`. The same ZIP keeps the same ID
on reruns. Its PR head branch is:

```text
protos-patch/<lowercase-slice>/<artifact-id>
```

This slice uses `protos-patch/github002-d/g002d-7c3f54a91e2b`.

The candidate commit carries:

```text
Protos-Patch-Artifact: <artifact-id>
Protos-Patch-Slice: <slice>
```

The PR body carries:

```html
<!-- protos-generated-patch artifact-id=<artifact-id> slice=<slice> -->
```

A launcher may reuse or recover remote state only when these identities match.

## Rerun state machine

- absent branch/PR -> build from current `PR_BASE`, validate, non-force push,
  open the PR;
- owned open PR -> `PR_ALREADY_OPEN`;
- owned branch with no PR -> `PR_RECOVERED`, without rewriting that branch;
- owned merged PR observed on `main` -> `PUBLISHED`;
- owned PR closed without merge -> stop;
- ambiguous or mismatched ownership -> stop.

A rerun never force-pushes an old candidate onto a newer base. PR freshness is a
GitHub/CI responsibility after the PR exists.

## Failed PR creation

If this invocation created the remote branch but PR creation fails, the launcher
first rechecks whether GitHub created the PR despite the local failure. If no
owned PR exists, it may delete the remote branch only if its tip is still exactly
the SHA pushed by this invocation.

## Issue linkage

Closing keywords are reserved for the leaf Issue actually completed by merge:

```text
Closes #<leaf>
Refs #<parent>
```

## Reference helper

`tools/generated_patch_pr.py` is the stdlib-only reference implementation.
Generated ZIPs should embed the acceptance-tested helper rather than depending on
whatever helper version a later `PR_BASE` contains.

`PR_OPENED`, `PR_RECOVERED`, and `PR_ALREADY_OPEN` are not publication.
`PUBLISHED` is valid only after the owned PR is merged and its merge commit is
observed on `main`.
