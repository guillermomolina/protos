# LM008-D — Values and Core collections surface audit

Status: IN_PROGRESS

Parent: `LM008 — Core Language Surface Completeness`

Durable coordination: GitHub Issue `#102`

Nature: non-normative audit/evidence record

## Scope decomposition

`LM008-D` remains one durable work item. For bounded execution it is audited in
four mechanical checkpoints that do not allocate new formal project identifiers:

- `D1` — canonical `null`/Boolean values, Boolean control protocols, general
  equality/identity and baseline Object hash protocols;
- `D2` — numeric families, construction/conversion, arithmetic, ordering,
  numeric equality/identity and numeric hashing;
- `D3` — String construction/value identity, equality/hash and fundamental
  String protocol surface;
- `D4` — Array, Map and IdentityMap fundamental Core surface plus final D
  reconciliation.

This decomposition selects no semantics. A newly exposed substantive choice still
stops at the normal Dxxx/PLATxxx approval gate.

## D1 checkpoint

Checkpoint state: COMPLETE

Validation class: `TEST_IMPACT`

Normative authority: `spec/semantics/VALUES_AND_COLLECTIONS.md`, with grammar
lowering already reconciled by closed LM008-B and informative runtime material
used only as supplementary implementation evidence.

D1 changes no normative specification, production implementation, public API,
native boundary or implementation version. It adds only two focused ordinary
Protos conformance programs where the existing evidence was indirect or
historically Java-only.

### Evidence matrix

| Surface row | Normative requirement | Retained language-level evidence | Supplementary implementation/mechanism evidence | Classification |
|---|---|---|---|---|
| Canonical `null` | Exactly one semantic `null` represents absence; failed lookup is not `null`; canonical `null` has value identity. | New `equality/canonical-null-booleans-identity.protos` directly proves repeated `null` identity and separation from both Booleans; existing Boolean unselected-path cases return the manifest's canonical `null` expectation. | Canonical literal/bootstrap representation and existing parser/evaluator tests. | `COVERED` |
| Canonical Boolean values | Core has exactly canonical singleton `true` and `false`; delegation or ordinary objects cannot become semantic Booleans. | New canonical-value identity probe proves stable self-identity and `true !== false`; retained `boolean/*` cases require canonical Boolean manifest results and `boolean/nonboolean-receiver-error.protos` rejects a non-Boolean receiver at the standard protocol boundary. | `ProtosStandardBooleanProtocol` validates the original receiver against the canonical Boolean values. | `COVERED` |
| Boolean `not` / one-way / two-way selection | `not`, `ifTrue`, `ifFalse`, and `ifTrueIfFalse` use ordinary dispatch, exact arities, selected-only invocation and exact selected callback results. | Retained `boolean/not-*`, `iftrue-*`, `iffalse-*` and `iftrueiffalse-*` central-corpus families cover canonical results, selection, eager callback-expression evaluation, exactly-once invocation and invalid selected callbacks. | `ProtosStandardBooleanProtocol`. | `COVERED` |
| Boolean `and` / `or` | Standard receivers short-circuit by selected-only callback invocation and require the invoked callback result to be exactly canonical Boolean, with no truthiness or implicit awaiting. | Retained `boolean/and-*` and `boolean/or-*` cases cover short-circuit, selected results and invalid-result Error behavior; LM008-B separately owns `&&`/`||` lowering evidence. | `ProtosStandardBooleanProtocol`. | `COVERED` |
| Default Object equality / inequality | `==` is customizable semantic equality; default Object equality is semantic identity; default `!=` complements dynamically selected equality and rejects a non-Boolean equality result at the standard Boolean boundary. | Existing `equality/default-same-object.protos` / `default-distinct-objects.protos` cover default equality. New `equality/invalid-custom-equality-default-inequality-error.protos` uses ordinary `Object.alias` to publish a custom `==` returning an Integer and proves inherited default `!=` signals Error instead of applying truthiness. | Source-backed `Object.protos` owns default `==`/`!=`; historical `ProtosDefaultEqualityAndNonIdentityTest` remains supplementary. | `COVERED` |
| Primitive identity / non-identity | `===` is non-overridable semantic identity and `!==` is its complement; ordinary objects retain individual identity while the closed value-identity families follow their normative value rules. | Existing `equality/nonidentity-*` plus default equality cases cover ordinary-object identity; the new canonical-value probe covers `null`/Boolean value identity. Numeric and String value identity remain explicitly assigned to D2/D3. | Canonical identity lowering/runtime semantic identity support. | `COVERED` |
| Standard `Object.identityHash()` | The ordinary overridable message exposes the primitive identity-hash result when the standard implementation is selected; it does not redefine `===`/primitive `identityHashOf`, and hash collisions do not define identity. | Existing central `bytes/identity-equality-hash.protos` calls inherited `identityHash()` repeatedly while also checking `===`/`!==`; Bytes does not replace the Object selector. | `ProtosStandardObjectProtocol` installs the inherited standard message and returns a semantic Integer from primitive identity hashing. | `COVERED` |
| Default `Object.hash()` | Default Object equality/hash are coherent for identity-bearing ordinary behavior; hash equality is never proof of identity and no persistent/global uniqueness promise exists. | Existing central `bytes/identity-equality-hash.protos` calls inherited `hash()` repeatedly alongside equality/identity observations. Numeric and String specialized equality/hash contracts remain D2/D3. | `ProtosStandardHashSupport.installObjectHash` installs the inherited default Object hash. | `COVERED` |

The standard prelude's deliberate absence of a binding named `Boolean` is not
silently inferred here. LM008-E owns required/forbidden Core prelude bindings and
will classify that absence at the binding surface. D1 only establishes the
semantic Boolean family and the behavior reached when standard Boolean protocol
implementation is selected.

Likewise D1 does not pre-classify numeric or String value identity merely because
the general identity section names those closed value families. Their family
construction, conversion, equality, identity and specialized hash contracts are
owned by D2 and D3 respectively.

### D1 audit result

No D1 row requires a new semantic or architectural decision.

No already-normative D1 promise was found absent from the current guest-visible
implementation. Two evidence gaps are closed by ordinary Protos conformance:

1. canonical `null`/Boolean identity is now asserted directly instead of inferred
   from unrelated protocol results; and
2. the historical Java-only invalid-dynamic-equality/default-inequality check now
   has a guest-visible source-level probe using the already-published
   `Object.alias` structural surface.

No production implementation owner is allocated by D1. `LM008-D` remains
`IN_PROGRESS`; D2 is next and audits the complete normative numeric surface
without treating D1's general identity/hash rows as a substitute for
numeric-family-specific rules.
