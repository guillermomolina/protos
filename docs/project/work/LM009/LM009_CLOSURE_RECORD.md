# LM009 — Closure Record

Status: **CLOSED**

Closure date: **2026-09-14**

Nature: non-normative language-maturity / editor-tooling evidence

## Closure decision

LM009 is closed as the first production-grade VS Code editor and IDE maturity
baseline for Protos.

The closure contract is satisfied by the ratified and proven slices A–I:

```text
LM009-A  CLOSED
LM009-B  CLOSED
LM009-C  CLOSED
LM009-D  CLOSED
LM009-E  CLOSED
LM009-F  CLOSED
LM009-G  CLOSED
LM009-H  CLOSED
LM009-I  CLOSED
```

LM009-I is closed through:

```text
D127  RATIFIED / Candidate B′
D128  RATIFIED / Candidate C″

I1  CLOSED
I2  CLOSED
I3  CLOSED
I4  RATIFIED
```

## End-to-end product evidence

The released editor baseline has evidence for:

```text
S1  language association / editor baseline
S2  run current file
S3  real debugger integration
S4  static diagnostics / symbols / definitions baseline
S5  references / integrated static-intelligence baseline
```

For the packaging and distribution tranche specifically:

```text
I1-C
  VSIX_CONTENT_REPRODUCIBLE=YES
  VSIX_ARTIFACT_REPRODUCIBLE=YES
  INDEPENDENT_CLEAN_WORKTREE_PROOF=PASS

I2
  CI_VSIX_ARTIFACT=YES
  CI_VSIX_CANONICAL=YES
  CI_VSIX_CONTENT_VALIDATION=PASS
  CI_VSIX_REPRODUCIBILITY_VALIDATION=PASS

I3-A
  CLEAN_INSTALL_VSIX=REAL_CANONICAL_ARTIFACT
  EXTENSION_DEVELOPMENT_PATH_USED=NO

I3-B
  REAL_PROTOS_RUNTIME=YES
  RUN_CURRENT_FILE=PASS_ON_CI
  EXTENSION_DEVELOPMENT_PATH_USED=NO

I3-C
  REAL_PROTOS_DEBUGGER=YES
  SOURCE_BREAKPOINT=CI_PROOF
  STOP_LOCATION=CI_PROOF
  STACK_FRAMES=CI_PROOF
  ACTIVATION_LOCAL_SCOPE=CI_PROOF
  REPRESENTATIVE_SCALAR_VALUE=CI_PROOF
  STEP_NEXT=CI_PROOF
  CONTINUE=CI_PROOF
  CLEAN_TERMINATION=CI_PROOF
  EXTENSION_DEVELOPMENT_PATH_USED=NO
```

## Distribution boundary

LM009-I4 ratifies:

```text
PUBLIC_RELEASE_CANONICAL_PATH=GITHUB_RELEASE
EXTENSION_VERSION_POLICY=INDEPENDENT_SEMVER
RUNTIME_VERSION_POLICY=SEPARATE
MARKETPLACE_REQUIRED_FOR_LM009=NO
OPEN_VSX_REQUIRED_FOR_LM009=NO
PUBLIC_REGISTRY_AUTO_PUBLISH=NO
CANONICAL_VSIX_ATTACHED_TO_RELEASE=YES
GENERATED_VSIX_COMMITTED=NO
```

The VS Code extension remains a thin client. The Protos runtime/language server
remains an external authority selected through the existing launcher boundary.

## Explicit deferrals

The following are intentionally outside LM009 closure:

```text
hover
completion
signature help
```

These remain explicitly deferred to **LM010**. Their deferral does not reopen or
block LM009 because they were classified as non-closure static-intelligence
follow-up work.

Future formatter, rename/refactoring, debugger mutation/evaluation and other
non-baseline editor capabilities likewise remain later work.

## Closure invariants

LM009 closure does not:

- change Protos language semantics;
- create a second semantic authority in VS Code;
- bundle the Protos runtime into the VSIX;
- require Marketplace or Open VSX publication;
- require automatic publication on ordinary `main` pushes; or
- redefine the external runtime launcher contract.

The closure record is governance-only and is evidence reconciliation for the
already-published implementation and CI slices.
