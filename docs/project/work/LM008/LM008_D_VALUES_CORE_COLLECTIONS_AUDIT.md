# LM008-D — Values and Core collections surface audit

Status: CLOSED

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

## D2 checkpoint

Checkpoint state: COMPLETE

Audit state: COMPLETE

Resolved implementation dependency: `I032 — Fixed-width numeric arithmetic publication`
(GitHub Issue `#265`), CLOSED by `03b865472ed7a026d84b7ecd125b1889a1da4a53`.

Original audit validation class: `TEST_IMPACT`

Reconciliation validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

Normative authority: `spec/semantics/VALUES_AND_COLLECTIONS.md`.

D2 audits the complete already-normative Core numeric surface. It introduces no
numeric semantics. Grammar/operator lowering was already reconciled by LM008-B;
D2 concerns the guest-visible value/protocol behavior selected by those forms.

### Evidence matrix

| Surface row | Normative requirement | Retained language-level evidence | Current implementation/mechanism evidence | Classification / owner |
|---|---|---|---|---|
| Numeric prototype topology | `Number` delegates to `Object`; `Integer` and `Float` delegate to `Number`; all eight fixed-width prototypes delegate to `Integer`; delegation does not itself confer numeric-family membership. | New `number/prototype-hierarchy.protos` proves the complete standard prototype chain. Existing `integer/numeric-parent-does-not-confer-membership.protos` and `numeric-value-parent-continues-family-lookup.protos` cover the membership/delegation distinction. | Core bootstrap validates the same topology. | `COVERED` |
| Integer construction/conversion | Exact `Integer` factory accepts Integer/fixed-width/integral finite Float, preserves mathematical value, rejects fractional/non-finite/non-number input and invalid arity/receiver. | Existing `numeric-conversion/integer-*`, `fixed-width/integer-from-uint8.protos`, and copied/inherited factory receiver-error cases. | `ProtosStandardNumericConversionProtocol`. | `COVERED` |
| Float construction/conversion | Exact `Float` factory accepts numeric values, preserves Float semantic value, correctly rounds exact integers to binary64 and preserves the required zero/infinity/NaN behavior. | Existing `numeric-conversion/float-*` cases include exact, rounded-large, overflow, signed-zero, infinity, NaN, arity and domain evidence. | `ProtosStandardNumericConversionProtocol` plus exact binary64 conversion helper. | `COVERED` |
| Fixed-width construction/conversion | Each of `UInt8`/`Int8`/`UInt16`/`Int16`/`UInt32`/`Int32`/`UInt64`/`Int64` is an exact factory; successful conversion preserves the target family and range; invalid arity/domain/range/non-integral Float signals Error. | Existing `fixed-width/*` corpus covers all eight family boundaries plus cross-family, Float, non-finite, object, arity and inherited-receiver cases. | `ProtosStandardNumericConversionProtocol` installs one family-parameterized exact factory per prototype. | `COVERED` |
| Integer `+` / `-` / `*` | Integer-only operands, arbitrary-precision exact Integer result; no implicit fixed/Float coercion. | Existing `integer/add-*`, `subtract-*`, `multiply-*`, big-value and mixed-family error cases plus receiver-domain regressions. | `ProtosStandardIntegerProtocol`. | `COVERED` |
| Integer `/` | Integer/Integer only; exact rational is rounded to binary64 round-to-nearest ties-to-even, including overflow, subnormal boundaries and signed zero; zero divisor signals Error. | Existing `integer/division-*` cases cover half/third, huge exact one, overflow, tie-even up/down, min-subnormal ties, negative zero and zero/mixed errors. | `ProtosStandardIntegerProtocol` + `ProtosBinary64Rounding`. | `COVERED` |
| Integer `div` / `mod` / `%` | Integer-only quotient truncates toward zero; remainder has dividend sign; `%` is the same standard remainder contract; zero divisor signals Error. | Existing `integer/div-*`, `mod-*`, `remainder-*`, big/negative/both-negative and zero/mixed cases. | `ProtosStandardIntegerProtocol`; source-backed `%` delegates to `mod`. | `COVERED` |
| Integer unary `negated` | Exact Integer negation, with strict semantic Integer receiver membership. | Existing `integer/negated.protos`, `double-negated.protos`, extracted/inherited method evidence and delegated non-Integer receiver error. | Source-backed `Integer.negated` plus standard Integer arithmetic. | `COVERED` |
| Float `+` / `-` / `*` / `/` | Same-family Float operands only; IEEE binary64 arithmetic with NaN, infinities, signed zero, overflow and underflow preserved; no implicit Integer/fixed coercion. | Existing `float/*` arithmetic corpus covers ordinary results, signed zero, infinities, NaN, overflow/underflow and mixed-Integer errors. | `ProtosStandardFloatProtocol`. | `COVERED` |
| Float unary `negated` | Same-family Float result with sign-bit semantics, including signed zero; strict Float receiver membership. | Existing `float/negated-zero.protos`, `double-negated-zero.protos`, `negated-normal.protos`, extracted-method and delegated-receiver error evidence. | Source-backed `Float.negated` plus standard Float arithmetic. | `COVERED` |
| Fixed-width `+` / `-` / `*` | Same fixed family only; result remains that family; overflow/underflow signals Error; no wrap, saturation, promotion or implicit widening. | I032-A retained successful operations, overflow/underflow, mixed-family and delegated-receiver rejection; I032-D adds all-eight-family success, every binary cross-family boundary, and the remaining UInt16/Int16/UInt32/Int32/UInt64 range edges. | `ProtosStandardFixedIntegerProtocol` installs family-specific `+`/`-`/`*` through one family-parameterized bridge, requires exact receiver/argument family membership, and range-checks every fixed result before rematerialization. | `COVERED` |
| Fixed-width unary `negated` | Preserve the exact fixed family; signal Error when mathematical negation is out of range, including unsigned nonzero and signed minimum. | I032-A publishes source-backed per-family negation with success/error regressions; I032-D adds all-eight-family success plus unsigned-positive and signed-minimum edge evidence through UInt64/Int64. | Each fixed-width Core prototype owns source-backed `negated`; bootstrap requires that source-backed closure before installing the family-specific primitive arithmetic bridge. | `COVERED` |
| Fixed-width `/` | Same fixed family only; result is Float using the same exact-rational binary64 rounding contract as Integer division; zero divisor signals Error. | I032-B retains all-eight-family success, exact tie-even rounding, zero-divisor, mixed-family, Integer/Float and delegated receiver/argument rejection; I032-D adds an independent cross-family `/` closure guard. | `ProtosStandardFixedIntegerProtocol` requires exact same-family operands and passes their exact mathematical values to `ProtosBinary64Rounding.divideExactIntegers`, returning semantic Float. | `COVERED` |
| Fixed-width `div` / `mod` / `%` | Same fixed family only; successful result stays in family; quotient/remainder sign rules are exact; zero divisor signals Error; mixed families fail. | I032-C retains all-eight-family quotient/remainder success, signed-divisor sign behavior, signed-minimum quotient overflow, zero, mixed-family/Integer/Float and delegated-domain rejection; `%` is exercised as source-backed Core behavior. I032-D adds independent cross-family `div`/`mod`/`%` and strict-arity guards. | `ProtosStandardFixedIntegerProtocol` publishes checked same-family `div`/`mod` through the family bridge and installs `%` from distributable Core source; fixed results are range-checked before same-family rematerialization. | `COVERED` |
| Numeric ordering | Standard `< <= > >=` accept semantic Number-family values, compare exact mathematical values across families, treat signed zeros as equal and NaN as unordered; invalid receiver/argument/arity fails as specified. | Existing `number/ordering-cross-family-exact.protos`, `ordering-integer-selectors.protos`, `ordering-signed-zero-infinity-nan.protos` and receiver/argument error cases. | `ProtosStandardNumberOrderingProtocol`. | `COVERED` |
| Numeric semantic equality | `==` compares exact mathematical numeric values across Integer/fixed/Float families; NaN equals nothing; signed zeros are equal; non-number argument returns false; invalid receiver/arity signals Error. | Existing `numeric-equality/*` and newer `equality/numeric-*` cases cover exact cross-family equality, float exactness, NaN/signed zero, infinity, non-number argument and receiver-domain behavior. | `ProtosStandardNumberEqualityProtocol`. | `COVERED` |
| Numeric semantic identity | Numeric values use value identity with family included: equal same-family values identify; cross-family equal values do not; Float identity distinguishes signed zero while all semantic NaNs share one Float identity. | Existing `equality/nonidentity-same-number.protos` / `nonidentity-cross-family.protos` plus new `equality/numeric-value-identity-specials.protos` for fixed same/cross-family identity, fixed-vs-Integer identity, Float signed zero and NaN identity. | `ProtosIdentity` implements Integer value identity, fixed family+value identity and Float raw-bit identity with canonical NaN identity. | `COVERED` |
| Standard numeric `hash()` equality coherence | Zero-argument Number-family `hash()` returns ordinary Integer; any numeric values equal under standard `==` must have equal hashes across families; signed zeros hash together; all NaNs use one standard hash class; receiver/domain/arity is strict. | New `number/hash-equality-coherence.protos` proves cross-family Integer/Float/fixed coherence, signed-zero coherence, NaN-class coherence and Integer result family. New receiver/arity error probes cover strict invocation boundaries. | `ProtosStandardHashSupport.installNumberHash`. | `COVERED` |
| Numeric `identityHash()` separation | Primitive identity hashing remains coherent with numeric `===`, but ordinary numeric `hash()` is equality-oriented and is not defined by identity hashing. | D1 retains ordinary inherited `identityHash()` evidence; D2's identity and equality/hash probes jointly distinguish cross-family identity from cross-family equality/hash. | `ProtosIdentity.identityHash` tags numeric identity families; Number `hash` uses equality classes instead. | `COVERED` |

### I032 implementation-gap reconciliation

The original D2 audit found a publication/implementation mismatch against already
settled fixed-width semantics, not a design question. I032 / GitHub #265 repaired
that mismatch without amending the specification.

The implementation-bearing publications were:

1. I032-A — checked same-family `+`, `-`, `*` plus source-backed `negated`;
2. I032-B — same-family `/` to Float through the existing exact-rational
   binary64 rounding helper;
3. I032-C — checked same-family `div`/`mod` plus source-backed `%`; and
4. I032-D — final all-family, cross-family, range and arity conformance
   reconciliation and top-level closure validation.

I032-D closed the implementation owner at
`03b865472ed7a026d84b7ecd125b1889a1da4a53`. The last implementation-bearing
I032 version is `0.2.327-SNAPSHOT`; D itself changed no production implementation,
specification, implementation version or native boundary.

The historical failing example `UInt8(1) + UInt8(2)` is therefore no longer a
current implementation gap: retained I032 conformance now requires the normative
same-family result and rejects the corresponding cross-family/domain/range
violations.

### D2 audit result

No D2 row requires a new Dxxx or PLATxxx decision.

The complete numeric surface is now classified `COVERED`. Three previously
indirect evidence areas were made explicit by the original D2 audit:

1. the complete standard numeric prototype hierarchy;
2. fixed/Float special-case numeric value identity; and
3. standard Number `hash()` equality coherence plus its receiver/arity boundary.

I032 A-D supplies the retained ordinary-Protos evidence that was missing for the
four fixed-width arithmetic rows. Reconciliation against the closed
family-parameterized implementation finds no residual numeric publication gap,
no weakened expectation and no new semantic or platform choice.

D2 is COMPLETE. `LM008-D` remains `IN_PROGRESS`; D3 is next and audits String,
while D4 remains the Array/Map/IdentityMap plus final-D reconciliation checkpoint.

## D3 checkpoint

Checkpoint state: COMPLETE

Validation class: `TEST_IMPACT`

Normative authority: `spec/semantics/VALUES_AND_COLLECTIONS.md`.

D3 audits String construction/value identity, equality/hash and the fundamental
Core String protocol. It introduces no String semantics and allocates no
implementation owner.

### Evidence matrix

| Surface row | Normative requirement | Retained language-level evidence | Current implementation/mechanism evidence | Classification |
|---|---|---|---|---|
| String value/prototype topology | `String` is an immutable value-identity family; the standard prelude `String` object delegates directly to `Object`, and every semantic String value has `String` as immediate parent. Delegation to `String` does not confer String-family membership. | New `string/prototype-topology.protos` proves both parent edges. Existing `string/delegated-size-receiver-error.protos` and `delegated-concat-receiver-error.protos` prove that an ordinary delegator does not become a semantic String. | Core bootstrap installs the String prototype under `Object`; represented String values use that prototype and standard String protocol receiver checks require `ProtosStringValue`. | `COVERED` |
| Exact String semantic value / identity / equality | String semantic value is the exact finite Unicode-scalar sequence. `===` uses value identity over that exact sequence; default standard `==` agrees for String values. No Unicode normalization, canonical-equivalence folding or locale policy is implicit. | Existing `string/value-identity-and-no-normalization.protos` proves value identity/equality for separately produced equal text and distinguishes precomposed `é` from `e\\u{301}`. | `ProtosIdentity` compares String represented values by exact stored text; default source-backed equality follows semantic identity for built-in String values. | `COVERED` |
| String `size()` | Zero-argument `size` returns an Integer count of Unicode extended grapheme clusters using the required Unicode data model; bytes/code units/code points are not the indexing unit. Receiver-domain and arity are strict. | Existing `string/grapheme-size.protos`, `size-wrong-arity-error.protos`, and `delegated-size-receiver-error.protos`. | `ProtosStandardStringProtocol` uses ICU grapheme boundaries and requires Unicode 17 data. | `COVERED` |
| String `at(index)` / bracket read | `at` accepts semantic exact-integer indexes, indexes Unicode grapheme clusters from zero, returns the exact grapheme as a String, and signals Error for negative/out-of-range/non-integer/invalid arity. Indexed syntax remains ordinary `at` dispatch. | Existing `string/grapheme-at-exact.protos`, `at-negative-error.protos`, `at-out-of-range-error.protos`, `at-float-error.protos`, `at-string-error.protos`, and `at-wrong-arity-error.protos`; LM008-B separately owns bracket lowering. | `ProtosStandardStringProtocol` accepts Integer/fixed-width exact-integer represented values, rejects invalid bounds/domain, and slices by ICU grapheme boundaries. | `COVERED` |
| String binary `+` | Standard `+` requires a semantic String receiver and String operand, concatenates exact scalar sequences without normalization/encoding/locale processing, mutates neither operand and returns String value semantics. | Existing `string/concat-exact.protos`, `concat-integer-error.protos`, `concat-null-error.protos`, `delegated-concat-receiver-error.protos`, plus `value-identity-and-no-normalization.protos`. | `ProtosStandardStringProtocol` validates exact represented String receiver/operand and constructs the concatenated String value. | `COVERED` |
| Standard String `hash()` | Zero-argument String `hash()` returns an ordinary Integer and is coherent with standard String equality: equal exact String values have equal hashes. A delegator is not accepted as a String-family receiver; arity is strict. Hash does not redefine identity or require unequal Strings to have unequal hashes. | New `string/hash-equality-coherence.protos`, `hash-delegated-receiver-error.protos`, and `hash-wrong-arity-error.protos`. | `ProtosStandardHashSupport.installStringHash` validates a represented `ProtosStringValue`, hashes its exact text and returns `ProtosIntegerValue`. | `COVERED` |
| String mutability / representation separation | String operations never mutate the receiver; encoded bytes are a distinct semantic domain, and Core String exposes no implicit encoding/decoding, normalization, collation or locale policy through these fundamental operations. | Concatenation/value-identity/grapheme evidence observes pure returned values; existing text/encoding conformance owns explicit Encoding-object conversion. | String represented values are immutable; the String provider exposes only `size`, `at`, and `+`, while hashing is installed separately as the standard value hash. | `COVERED` |

### D3 audit result

No D3 row requires a new Dxxx or PLATxxx decision.

The existing String implementation is guest-visible and consistent with the
already-normative value model. D3 found no implementation/publication defect.
The retained corpus already covered grapheme-aware size/indexing, concatenation,
strict receiver/domain behavior, exact value identity/equality and absence of
implicit normalization.

D3 adds only the previously indirect guest-visible evidence for:

1. the `String -> Object` and String-value -> `String` prototype topology; and
2. specialized String `hash()` equality coherence, semantic receiver-domain and
   exact-arity boundaries.

D3 is COMPLETE. `LM008-D` remains `IN_PROGRESS`; D4 is next and owns Array, Map,
IdentityMap and the final LM008-D reconciliation.

## D4 checkpoint

Checkpoint state: COMPLETE

Audit state: COMPLETE

Validation class: `TEST_IMPACT`

Normative authority: `spec/semantics/VALUES_AND_COLLECTIONS.md`.

Resolved dependencies:

- `I034 — Array semantic-Integer indexing publication` / GitHub #272 — CLOSED by
  `3139454d98abc5a4f1f918377fd660478db4d8e9`;
- `I035 — Map single-hash insertion publication` / GitHub #273 — CLOSED by
  `3eb7e94ca047580a76b3e3c5c49ccc264a994db5`;
- `I036 — Keyed Map iteration snapshot publication` / GitHub #275 — CLOSED by
  `f6db5abda5b2247f662bdd635dbbb46328016e5c`;
- `I031 — Standard Object reflection/mutation/state publication` / GitHub #242 —
  CLOSED by final reconciliation `5c6eee9f90b2e5dc8542bd616c02c8f1d728cd20`,
  with guest-visible structural `close()` / `freeze()` published by C/D.

D4 audits the fundamental standard Array, Map and IdentityMap value/protocol
surface. It introduces no collection semantics. The three implementation gaps
below are mismatches against already-settled contracts, not design questions.

### Evidence matrix

| Surface row | Normative requirement | Retained language-level evidence | Current implementation/mechanism evidence | Classification / owner |
|---|---|---|---|---|
| Array factory / topology / identity | `Array(...)` is a variadic shallow factory; every successful invocation creates a fresh open identity-bearing Array whose parent is the invocation receiver; inherited factory behavior composes with prototypes; ordinary default equality/hash remain identity-based. | Existing `collections/array-construction-and-indexing.protos`, `array-factory-fresh-single-and-shallow.protos`, `array-inherited-factory-parent-observable.protos`, copied-factory receiver rejection and `array-ordinary-identity-and-equality.protos`. | `ProtosStandardArrayProtocol.call` constructs `ProtosArrayValue(receiver, supplied)` and `ProtosArrayValue` owns a private copied element sequence while retaining element references. | `COVERED` |
| Array `at` / `atPut` semantic Integer domain | `at(index)` and `atPut(index,value)` accept any semantic Integer family according to exact mathematical value, reject non-Integer/negative/out-of-range indexes, preserve dense length, and `atPut` returns the exact supplied value. | I034 retained `collections/array-fixed-width-indexing.protos`, fixed-width negative/large-index errors, and the pre-existing ordinary-Integer/Float/bounds/bracket/exact-RHS corpus. | I034 `3139454d` centralizes exact ordinary/fixed Integer extraction while preserving dense bounds, receiver state and exact result semantics. | `COVERED` — I034/#272 CLOSED |
| Array receiver / size / basic `each` | Indexed methods require actual Array state; `size()` is ordinary unbounded Integer; `each` validates polymorphic callability, snapshots the indexed values in ascending order, invokes once per element and returns the receiver. | Existing delegated/copied receiver errors, size arity/domain, and `array-each-*` order/callability/shallow-snapshot cases. | `ProtosStandardArrayProtocol` requires `ProtosArrayValue`; Array snapshot is `List.copyOf(elements)`, which preserves the exact element references even when live indexed positions are later replaced. | `COVERED` |
| Array open / closed / frozen indexed mutation | Reads remain available in all states; open and closed Arrays may replace existing positions; frozen `atPut` fails before index validation/mutation. | Retained `reflection/close-array-indexed-replacement-remains-allowed.protos` and `reflection/freeze-array-indexed-replacement-rejected.protos` now exercise the formerly blocked guest-visible states, alongside the existing open-state Array corpus. | `ProtosArrayValue` / `ProtosStandardArrayProtocol` retain closed replacement and frozen-first rejection; I031 supplies ordinary structural state transitions. | `COVERED` — I031/#242 CLOSED |
| Map / IdentityMap factory, topology and default identity | `Map` and `IdentityMap` independently delegate directly to `Object`; zero-argument factories create fresh open identity-bearing keyed objects, inherited factory behavior preserves kind/receiver parent, and default equality/hash remain object-identity based. | New `collections/keyed-map-prototype-topology.protos`, `map-factory-fresh-open.protos`, `map-factory-wrong-arity-error.protos`, `keyed-map-default-identity-equality.protos`; existing IdentityMap factory cases. | Both standard providers construct their represented keyed value with the invocation receiver as parent; neither installs structural equality/hash. | `COVERED` |
| Map query hash / equality search contract | Each key search obtains the query key's standard `hash`, accepts any semantic Integer-family result by exact value, filters by recorded hash, then sends `queryKey == storedKey` in insertion order and requires canonical Boolean. There is no identity shortcut. | New `map-fixed-width-hash-result.protos`, `map-query-equality-direction-representative.protos`, `map-invalid-hash-result-error.protos`, `map-invalid-equality-result-error.protos`, and `map-recorded-hash-no-identity-shortcut.protos`, plus existing lookup/missing/equal-key cases. | `ProtosStandardMapProtocol.find/hash` implements fixed-width hash acceptance, recorded-hash filtering, query-to-stored dispatch, canonical-Boolean validation and no identity shortcut. | `COVERED` |
| Map absent insertion hash count / recorded hash | An absent `atPut` obtains query-key `hash` exactly once for that operation and stores that exact `queryHash` as the new entry's insertion-time recorded hash. | I035 retained `collections/map-single-hash-absent-insertion.protos`, which makes callback count and reuse of the one search hash observable. | I035 `3eb7e94c` computes one operation-local query hash, searches with it and reuses it for absent insertion. | `COVERED` — I035/#273 CLOSED |
| Map replacement / representative / order / remove | Equal-key update replaces only the value, retains representative key and insertion position; remove returns the old value; remove/reinsert moves the new association to the end; insertion-time recorded hash is not recomputed by key mutation. | Existing replacement/remove/reinsert cases plus new `map-query-equality-direction-representative.protos` and `map-recorded-hash-no-identity-shortcut.protos`. | Mutable keyed entries retain key and recorded hash while replacing only `value`; list order is insertion order. | `COVERED` |
| Map comparison reentrancy | While user `hash`/`==` is active for a Map key comparison, keyed mutation of that same Map fails instead of waiting or mutating through the comparison; unrelated Map effects remain ordinary. | New `map-reentrant-mutation-from-hash-error.protos` directly covers the synchronous guest-visible guard. Explicit-suspension continuity remains part of LM008-F's advanced LM005/LM006 cross-check rather than being duplicated by this fundamental D4 checkpoint. | `ProtosMapValue.comparisonDepth` is entered around both user hash and equality dispatch, and `mutationEntry` rejects same-Map keyed mutation while active. | `COVERED` for fundamental synchronous surface; suspension cross-check deferred to LM008-F |
| Map / IdentityMap basic `each` callability, order and result | `each(block)` validates receiver then polymorphic callability, calls with exactly `(key,value)` in insertion order and returns the exact receiver on normal completion. | New `map-each-order-return.protos`, `map-each-noninvokable-error.protos`, `map-each-wrong-arity-error.protos`, `identity-map-each-order-return.protos`, `identity-map-each-noninvokable-error.protos`, and `identity-map-each-wrong-arity-error.protos`. | Both providers validate callability before iteration and traverse keyed snapshot order. | `COVERED` for basic invocation/order/result surface |
| Map / IdentityMap association snapshot stability | The `each` snapshot captures each representative key and exact mapped value reference at snapshot establishment; later insert/remove/replacement/reinsert must not alter or duplicate pending visits; snapshot remains shallow. | I036 retained `collections/map-each-association-snapshot-mutation.protos`, `identity-map-each-association-snapshot-mutation.protos`, and `map-each-nested-independent-snapshot.protos`. | I036 `f6db5abd` uses iteration-only shallow immutable association snapshots while preserving live-entry search/update machinery. | `COVERED` — I036/#275 CLOSED |
| IdentityMap deterministic identity keys | IdentityMap search uses primitive non-overridable `identityHashOf` plus `===`; it sends no user `identityHash`, `hash`, `==` or other key callback and retains exact representative identity. | Existing distinct/equal String/path/numeric-family IdentityMap cases plus new `identity-map-no-key-callbacks.protos`. | `ProtosStandardIdentityMapProtocol.find` uses only `ProtosIdentity.identityHash` and `ProtosIdentity.identical`; identity-hash recomputation is observationally permitted because the primitive hash is stable and non-overridable. | `COVERED` |
| Map / IdentityMap open / closed / frozen mutation boundaries | Open maps insert/replace/remove; closed maps permit existing-key replacement but reject insertion/removal after the required state/search ordering; frozen mutation fails before key callbacks; read-only lookup remains available. | Retained I031 `reflection/freeze-map-keyed-replacement-rejected.protos` plus LM008-D closure probes `collections/map-closed-state-boundaries.protos`, `map-frozen-state-boundaries.protos`, and `identity-map-closed-frozen-state-boundaries.protos` prove closed replacement, missing-insert search, remove fail-fast, frozen fail-fast, preservation and read availability for both keyed Map families. | Current Map/IdentityMap providers apply the already-normative state gates; I031 supplies ordinary guest-visible structural `close()` / `freeze()`. | `COVERED` — I031/#242 CLOSED |

### D4 dependency and evidence reconciliation

The three collection-specific implementation owners are closed and retain the
regressions required by the original D4 checkpoint:

1. I034/#272 accepts every semantic Integer family for Array indexing by exact
   mathematical value (`3139454d`, implementation `0.2.331-SNAPSHOT`).
2. I035/#273 obtains one Map insertion query hash and reuses that exact value as
   recorded hash (`3eb7e94c`, implementation `0.2.332-SNAPSHOT`).
3. I036/#275 establishes shallow immutable key/value association snapshots for
   Map/IdentityMap iteration (`f6db5abd`, implementation `0.2.334-SNAPSHOT`).

I031/#242 is also CLOSED. Its structural `Object.close()` / `freeze()` publication
makes collection state reachable from ordinary Protos. I031 already retained the
Array closed/frozen probes and a Map frozen replacement probe. This D4 closure adds
only the missing keyed-state evidence: closed Map replacement and missing-insert
search behavior, closed Map remove fail-fast, frozen Map mutation fail-fast, and
combined closed/frozen IdentityMap behavior. No collection production code is
changed by this reconciliation.

### D4 final audit result

No D4 row requires a new Dxxx or PLATxxx decision. After the four dependencies
closed and the formerly impossible state probes became executable, every D4
surface row is `COVERED`. No `TRACKED_IMPLEMENTATION_GAP`, `RUNNABLE_UNCOVERED`,
`SPECIFIED_NOT_GUEST_VISIBLE`, or `NEEDS_DESIGN_DECISION` row remains in D4.

D1, D2, D3 and D4 are all complete. LM008-D is therefore CLOSED after the central
Test Tool corpus and focused collection/native-boundary validation pass on the
same publication candidate. Parent LM008 remains `IN_PROGRESS`; LM008-E remains
independently READY and LM008-F remains dependency-gated.

## LM008-D closure result

- D1 canonical values / Boolean / general equality-identity-hash: **COMPLETE**;
- D2 numeric surface: **COMPLETE**; I032 dependency CLOSED;
- D3 String surface: **COMPLETE**;
- D4 Array / Map / IdentityMap surface: **COMPLETE**;
- D4 repaired implementation owners: **I034/#272, I035/#273, I036/#275 — CLOSED**;
- D4 Object-state evidence dependency: **I031/#242 — CLOSED**;
- unresolved LM008-D surface rows: **0**;
- new semantic decisions: **0**;
- new platform decisions: **0**;
- specification change: **none**;
- production implementation change in this closure slice: **none**;
- implementation version change in this closure slice: **none**;
- native-boundary change in this closure slice: **none**;
- validation class: **TEST_IMPACT** (three retained ordinary-Protos state probes);
- parent LM008: **IN_PROGRESS**.

<!-- LM008-D final reconciliation -->
