# Decision records

`decisions/` contains durable non-normative records of already-tracked design or
platform decisions. A decision record documents rationale and outcome; its path
does not create semantic authority.

- [`language/`](language/README.md) contains `Dxxx` records. Observable language
  authority remains in the applicable ratified specification under `spec/`.
- [`platform/`](platform/README.md) contains `PLATxxx` runtime/platform
  architecture records. They remain non-normative and semantically invisible
  except where separate Protos semantics explicitly require otherwise.

Existing flat `Dxxx` and `PLATxxx` files are migrated only by later bounded
DOC002 slices after durable-reference and compatibility review.
