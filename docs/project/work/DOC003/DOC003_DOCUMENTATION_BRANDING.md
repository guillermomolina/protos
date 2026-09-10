# DOC003 — Documentation branding and approved logo integration

GitHub coordination: Issue [#290](https://github.com/guillermomolina/protos/issues/290), labelled `family:DOC`.

Status: **CLOSED**

## Purpose

DOC003 integrates the already approved Protos logo into maintained repository
documentation without changing language semantics, documentation authority, or
the visual identity itself.

## Approved asset identity

The maintained documentation asset is:

- path: `docs/assets/branding/protos-logo.png`;
- SHA-256: `7c70f12c95daec4d68fd0b64dd453e925eef4b2d58c962bca8091f289496e3a4`;
- PNG dimensions: `1448 × 1086`;
- identity: the exact project-owner-approved Protos logo asset.

The hash records which already-approved binary was published. It is not a claim
that Protos branding can never change; replacing this asset requires an explicit
future branding change rather than silently substituting another generated
variant.

## Documentation placement

The logo is displayed at two high-value entry points:

- repository `README.md`;
- `docs/guide/README.md`.

The root README uses `docs/assets/branding/protos-logo.png`. The Programming
Guide uses the relative path `../assets/branding/protos-logo.png`.

No logo is added to the normative specification, internal project-navigation
pages, or every guide chapter merely for repetition.

## Authority boundary

`guillermomolina/protos` owns this maintained branding asset for repository
documentation. WEB001 may consume the exact asset from a selected Protos
revision for web-specific presentation, but website layout and rendering remain
WEB001 responsibilities.

This work adds no tagline and does not redesign, regenerate, crop, recolor, trace,
or reinterpret the approved logo.

## Validation and closure

DOC003 closes after publication only if:

- the committed PNG is byte-identical to the approved asset;
- the SHA-256 equals `7c70f12c95daec4d68fd0b64dd453e925eef4b2d58c962bca8091f289496e3a4`;
- PNG dimensions remain `1448 × 1086`;
- README and Programming Guide references resolve to the committed asset;
- the durable DOC003 record is under the ratified role-first work path;
- no specification, implementation/runtime, implementation version, public API,
  or license terms change.

Validation class: **GOVERNANCE_DOCUMENTATION_ONLY**.
