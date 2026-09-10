# DOC003 — Documentation branding and approved logo integration

GitHub coordination: Issue [#290](https://github.com/guillermomolina/protos/issues/290), labelled `family:DOC`.

Status: **CLOSED**

## Purpose

DOC003 integrates the already approved Protos logo into maintained repository
documentation without changing language semantics, documentation authority, or
the visual identity itself.

## Approved asset identity

DOC003 maintains two project-owner-approved transparent PNG assets:

- full logo:
  - path: `docs/assets/branding/protos-logo.png`;
  - SHA-256: `e58ec207447e600b7390d1aa6b8529a5cd89791b60ce87d87e8f0234524ac384`;
  - PNG dimensions: `1264 × 414`;
  - role: full Protos wordmark for documentation and other full-branding surfaces;
- symbol:
  - path: `docs/assets/branding/protos-symbol.png`;
  - SHA-256: `4f0de3936f77f0cb4132a8018eaf9c100bc1d85d696bddb3b59a4f57e96d7e74`;
  - PNG dimensions: `398 × 414`;
  - role: compact Protos identity for favicon, file-icon, application-icon, and
    similar consumers.

Both binaries are the exact transparent assets supplied and approved by the
project owner. DOC003 publishes them unchanged; it does not regenerate, trace,
recolor, rescale, crop, or otherwise derive replacement artwork.

The recorded hashes identify the canonical maintained binaries. A future
branding change may replace them explicitly, but agents must not silently
substitute another generated or approximate variant.

## Documentation placement

The logo is displayed at two high-value entry points:

- repository `README.md`;
- `docs/guide/README.md`.

The root README uses `docs/assets/branding/protos-logo.png`. The Programming
Guide uses the relative path `../assets/branding/protos-logo.png`.

No logo is added to the normative specification, internal project-navigation
pages, or every guide chapter merely for repetition.

## Authority boundary

`guillermomolina/protos` owns these maintained branding assets for repository
documentation and shared project identity. WEB001 may consume the exact assets
from a selected Protos revision for web-specific presentation, but website layout,
favicon wiring, and rendering remain WEB001 responsibilities.

This work adds no tagline and does not modify the owner-supplied transparent
binaries.

## Validation and closure

DOC003 closes after publication only if:

- `docs/assets/branding/protos-logo.png` is byte-identical to the approved
  transparent full logo with SHA-256
  `e58ec207447e600b7390d1aa6b8529a5cd89791b60ce87d87e8f0234524ac384` and
  dimensions `1264 × 414`;
- `docs/assets/branding/protos-symbol.png` is byte-identical to the approved
  transparent symbol with SHA-256
  `4f0de3936f77f0cb4132a8018eaf9c100bc1d85d696bddb3b59a4f57e96d7e74` and
  dimensions `398 × 414`;
- the repository README and Programming Guide continue to resolve the canonical
  full-logo path at their already-selected display widths;
- the durable DOC003 record remains under the ratified role-first work path;
- no specification, implementation/runtime, implementation version, public API,
  website layout, or license terms change.

Validation class: **GOVERNANCE_DOCUMENTATION_ONLY**.
