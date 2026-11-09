# Strict HS256 JWT Standard Library contract (D246-C)

Status: Normative. Language version: 0.1. Decision: D246-C. Implementation: I161.

## Surface and authority

`std:auth/jwt` is an ordinary importable Standard Library MODULE, not Core or Prelude. Its public module object exports exactly `issue(claims, signingKey, policy, now)`, `verify(token, verificationKey, policy, now)`, and `JwtError`. `JwtError` is an ordinary Standard Library Error prototype delegating directly to `Error`; each JWT rejection produces a **fresh occurrence**, never a cached singleton or the prototype itself. The two functions are synchronous and produce neither Task nor Future.

All security authority is supplied by the caller. The module never manufactures, caches, locates, or chooses keys; reads ambient clock time; obtains entropy; accesses the environment, network, filesystem, process services, or remote key endpoints; or installs an authorization context. Import confers no authority. No shared mutable cryptographic, session, user, or token state exists across Actors, Processes, or Contexts.

The only accepted signing or verification key is a genuine standard `Bytes` value of length **32 through 4096 octets**, captured byte-exactly before use. Strings, passwords, foreign arrays, fake Bytes, and silent character encoding are rejected. Key provision, key rotation, credential verification, and key custody belong to the application/host, never to this module.

## Required trusted policy and time

Both operations require an ordinary caller-supplied policy object and an explicit `now` value recognized as a genuine `std:datetime/instant` Instant. The following **own slots** are required; inherited bindings are never consulted:

| Own policy slot | Domain |
|---|---|
| `issuer` | nonempty String |
| `audience` | nonempty String |
| `type` | nonempty String, required protected `typ` |
| `maxLifetimeSeconds` | positive Integer |
| `clockSkewSeconds` | optional Integer in `0..300`; defaults to `0` |

Policy is snapshotted before use. Values with the wrong domains, including Boolean coercions or floating-point approximations, are not accepted. `now` is measured as exact Integer nanoseconds; the operation must not round it to whole seconds or convert through Float.

## Compact JWS wire profile

Only compact **protected JWS**, `base64url(header) + "." + base64url(payload) + "." + base64url(tag)`, is supported. Exactly three nonempty segments separated by exactly two ASCII periods are required. All segment encodings must be canonical, unpadded URL-safe RFC 4648 Base64url; the original encoded first and second segments constitute the signing input and **must not** be re-encoded or normalized during verification.

Protected header is a JSON object containing **exactly** the String members `alg: "HS256"` and `typ: policy.type`. Reject a missing, duplicate, differently typed, or mismatched member; any other field; `alg:none`; RS256, ES256 or any alternative algorithm; `kid`, `jwk`, `jku`, `x5u`, `crit`; unencoded payloads; JWE; and flattened/general JWS JSON serialization. Reject duplicate JSON names, including escaped-equivalent spellings, rather than treating one as authoritative.

The MAC is **HMAC-SHA256**, computed with `std:crypto/hmac_sha256` over the original ASCII signing input. The signature must decode to exactly **32 bytes**. Verification delegates to `HMAC.verify` for full-tag comparison: no independent short-circuiting equality, truncated tag acceptance, or alternative cryptographic backend is permitted. Cryptographic provider failures must propagate as provider/programming errors, never masquerade as an invalid signature or become `false`/`null`.

## Claims

Input `claims` for `issue` is a structured JSON **object node** in the `std:json/json` model. Upon successful `verify`, the result is a **freshly parsed object JSON node**, exposed only after all authentication, parsing and policy checks succeed. The library never reflects arbitrary request objects into claims.

| JSON claim | Required validation |
|---|---|
| `iss` | String, exactly `policy.issuer` |
| `aud` | String equal to `policy.audience`, or nonempty Array of Strings containing it |
| `sub` | nonempty String |
| `iat` | exact integral NumericDate seconds |
| `exp` | exact integral NumericDate seconds |
| `nbf` | optional exact integral NumericDate seconds; enforce when present |
| `jti` | optional String; never implicitly generated |

Additional structured JSON claims are allowed. Required claims are never silently added or repaired. JSON numbers are exact decimal coefficient/exponent pairs; `1.0` may be mathematically integral, but `1.2` is not. Integer conversion must use coefficient and exponent exactly, without Float, rounding, String coercion, or Boolean coercion. Structured JSON must have valid kinds and no container nesting deeper than **16** levels.

Require `exp > iat` and `exp - iat <= policy.maxLifetimeSeconds` using exact Integers. At trusted time `now` and skew `s` seconds, require `iat * 10^9 <= now.nanoseconds + s * 10^9` and, when present, `nbf * 10^9 <= now.nanoseconds + s * 10^9`. Expiration is exclusive: `now.nanoseconds < exp * 10^9 + s * 10^9`. At zero skew `now == exp` is expired. All comparisons retain full nanosecond precision.

## Resource bounds

| Resource | Maximum |
|---|---:|
| Entire compact ASCII token | 8192 octets |
| Decoded protected header | 1024 octets |
| Decoded JSON payload | 4096 octets |
| JSON container depth | 16 |
| HMAC-SHA256 signature | exactly 32 octets |
| Signing/verification key | 32–4096 octets |

Reject oversized compact tokens before Base64url decoding. Reject oversized decoded header before parsing header JSON, and oversized decoded payload before parsing claims JSON. Apply equivalent header, payload, signing-input, depth and token bounds during issuance. Do not permit excessive or attacker-selected arithmetic work through gigantic exponents or recursively cyclic caller-owned nodes. JWT limits do not restrict the independent general-purpose Base64url codec.

## Issuance ordering

1. Validate and snapshot the genuine Bytes key, trusted policy and explicit Instant.
2. Require structured JSON object claims. Encode via `JSON.encode`, capture bounded UTF-8 bytes, then parse the snapshot into new JSON nodes, rejecting malformed kinds, duplicate keys, excessive depth and all invalid required or temporal claims.
3. Encode a protected JSON object containing only `alg` and `typ`; enforce its decoded 1024-byte limit.
4. Base64url encode header and payload; form the original encoded ASCII `header.payload` input.
5. Call `HMAC.digest` on private Bytes of that input. Encode the complete 32-byte digest; assemble three segments and enforce the final 8192-octet token limit before returning.

## Verification ordering and failure taxonomy

1. Validate trusted caller policy, genuine Bytes key and explicit Instant. Invalid configuration or issuing arguments signal a fresh **ordinary Error**, not a JWT authentication error.
2. Validate the token String's ASCII compact shape, three nonempty segments, 8192-octet limit and strict canonical Base64url spelling.
3. Decode the bounded header, parse strict JSON, require exactly supported header members, `alg == "HS256"` and exact `typ` match; decode the signature and require exactly 32 bytes.
4. Construct MAC input from the **original** encoded first and second segments. Call `HMAC.verify(key, input, tag)`. If it answers false, signal a fresh `JwtError` and do not decode or return claims.
5. Only after successful MAC authentication, decode the bounded payload and parse strict JSON; validate object kind, depth, issuer, audience, subject, integral NumericDates, lifetime and time comparisons. Then return the fresh parsed claims node.

Malformed or unauthenticated untrusted tokens and invalid authenticated claims signal fresh `JwtError` occurrences. Wrap malformed compact/Base64url/JSON failures without swallowing unrelated cryptographic provider failures. There is no verified-claims output on any unsuccessful path. A successfully verified token proves possession of the configured signing key and agreement with the trusted claim policy; it does **not** authorize a user, verify a password, establish an HTTP session or grant account permissions.

## Actor and Process isolation

Calls run wholly within their caller's execution/Actor context and retain no key, claims or derived state across calls. Importing the MODULE neither opens I/O nor provides a new capability. Existing Actor/Process/Context isolation and transfer rules apply to caller-provided values; importing or verifying JWT grants no additional cross-boundary authority.

## Scope

This contract does not define OAuth, OpenID Connect, JWT Bearer HTTP middleware, sessions, key discovery, `kid` routing, asymmetric signatures, token encryption, refresh-token logic, entropy provisioning, or wall-clock access. The caller composes those concerns only through separately approved facilities and authorization decisions.
