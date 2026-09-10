# Standard Library Naming

Status: project distribution policy; non-normative
Owner: official Protos distribution / CLI host resolution

## Purpose

This document records naming rules for importable modules shipped in the Protos
standard distribution. These rules are host/distribution policy under the Core
module-resolution boundary. They do not add syntax or redefine Core module
semantics.

## Canonical `std:` identity

Official Standard Library modules use:

```text
std:<logical-module-name>
```

The complete `std:` spelling is the canonical `ModuleKey` produced by the
official resolver. Case is significant and is preserved exactly.

For example:

```text
std:collections/Set
std:collections/IdentitySet
```

are canonical identities. Spellings such as `std:collections/set` are different
identifiers and do not alias the canonical modules.

## Logical-name portability

Each logical-name segment:

- begins with an ASCII letter;
- then contains only ASCII letters, ASCII digits, or `_`;
- uses `/` only between segments;
- is case-sensitive;
- must match the distributed directory or source-file spelling exactly,
  independently of host filesystem case sensitivity.

The official distribution never ships sibling path entries whose names differ
only by ASCII letter case. The resolver rejects an ambiguous case-fold-equivalent
sibling set if one is encountered.

The first logical segment `core`, in any ASCII letter case, is reserved and
cannot be resolved through `std:` because `protos/lib/core/` is the Core
bootstrap source area rather than importable Standard Library.

Segments equal case-insensitively to Windows reserved device names (`CON`, `PRN`,
`AUX`, `NUL`, `COM1` through `COM9`, or `LPT1` through `LPT9`) are not portable
Standard Library names and are rejected.

## Source naming

The physical mapping remains direct:

```text
std:<logical-name>
    -> protos/lib/<logical-name>.protos
```

No case conversion, underscore insertion/removal, extension spelling supplied by
the program, search path, or fallback is involved.

When one distributed Protos source has one canonical named public concept as its
clear owner, the source filename should preserve that exact public name,
including case. For example:

```text
Set          -> Set.protos
IdentitySet  -> IdentitySet.protos
```

This is a conceptual-correspondence rule, not a global PascalCase rule.
Aggregation, bootstrap, taxonomy, or responsibility-oriented sources that do
not correspond one-to-one with one public concept may keep descriptive names.

Directory/namespace names are chosen for their logical grouping role and need
not imitate the case convention of a public concept.

## Compatibility discipline

The Standard Library is still evolving. When a short-lived unpublished or
early-development spelling is replaced before wider API accumulation, the
official distribution should prefer one canonical spelling rather than retaining
aliases indefinitely. A compatibility alias requires an explicit independent
reason; it is not created automatically by filesystem case behavior.
