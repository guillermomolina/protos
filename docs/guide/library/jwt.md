# Strict HS256 JWT and explicit authentication

`std:auth/jwt` is an ordinary importable Protos Standard Library MODULE. It
exports `issue`, `verify`, and the `JwtError` prototype. Both operations are
synchronous and require the caller to supply an authenticated policy, a real
standard Bytes key, and an explicit `std:datetime/instant` value. Importing
this module creates no filesystem, network, clock, entropy, Process, or other
authority. The normative contract is [JWT.md](../../../spec/auth/JWT.md).

## Interface and responsibility

```protos
JWT: import("std:auth/jwt")
JSON: import("std:json/json")
Instant: import("std:datetime/instant")
Base64url: import("std:text/base64url")
HMAC: import("std:crypto/hmac_sha256")

// Test fixture only: do not embed production secrets in source code.
fixtureKey: Encoding.UTF8.encode("")
for(i, 0, 32) { fixtureKey.add(i) }

policy: {
    issuer: "https://issuer.example"
    audience: "protos-realworld"
    type: "realworld-access+jwt"
    maxLifetimeSeconds: 3600
    clockSkewSeconds: 0
}

claims: JSON.object(
    "iss", JSON.string("https://issuer.example"),
    "aud", JSON.string("protos-realworld"),
    "sub", JSON.string("user-id-42"),
    "iat", JSON.number(1794096000, 0),
    "exp", JSON.number(1794099600, 0)
)
trustedNow: Instant(1794097000 * 1000000000)
token: JWT.issue(claims, fixtureKey, policy, trustedNow)
verified: JWT.verify(token, fixtureKey, policy, trustedNow)
// The caller still decides whether the subject may perform any action.
```

`issue(claims, signingKey, policy, now) -> String` requires an object JSON node;
it does not add missing registered claims. It serializes its own private JSON
snapshot and signs exactly the two resulting Base64url segments with HS256.
`verify(token, verificationKey, policy, now) -> JSON object node` returns a
newly parsed claims tree **only after** validating the protected header and
successfully authenticating the original two segments with the complete HMAC.
It does not reconstruct the signing input from reserialized JSON.

The key must be genuine standard `Bytes`, **32 through 4096 octets**. A String
or password is never interpreted or silently encoded as a key. The policy is
a trusted ordinary object whose own slots include nonempty String `issuer`,
`audience`, `type`, and positive Integer `maxLifetimeSeconds`. Its optional
nonnegative Integer `clockSkewSeconds` defaults to 0 and cannot exceed 300.
Inherited bindings do not supply policy fields. Callers must provide their
own trustworthy `Instant`; there is no `now()` or implicit time lookup.

## Accepted claims and timing

Required claims are matching String `iss`, matching String or nonempty
String-Array `aud`, nonempty String `sub`, and exact integral NumericDate
`iat` and `exp`. Optional `nbf` is also an exact integral NumericDate;
optional `jti` is a String. Other structured JSON claims are permitted.
`exp` must exceed `iat`; the difference cannot exceed
`maxLifetimeSeconds`. Time checks use exact nanoseconds without rounding
`now` through Float or truncating it to seconds. With zero skew the instant
at `exp` is already expired. Skew also applies to future `iat` and `nbf`.
JSON decimals of mathematical integer value (such as `1.0`) are valid; a
fractional mathematical value (such as `1.2`) is not.

## Security and operational limits

Compact tokens have at most **8192 ASCII octets**, decoded protected headers
at most **1024 octets**, decoded payloads at most **4096 octets**, JSON
container nesting at most **16**, and signatures exactly **32 octets**.
Only three-segment protected compact JWS with `alg: "HS256"` and the exact
configured `typ` is supported. `none`, `RS256`, `ES256`, key IDs, remote
key URLs, embedded keys, `crit`, unencoded payloads, JWE and JSON-form JWS
are not supported. Duplicate JSON member names are rejected, including
equivalent escaped spellings.

A malformed token or rejected claims signal a fresh `JwtError`; malformed
issuer configuration or signing arguments signal an ordinary `Error`.
Cryptographic-provider failure is not a failed user signature and must not be
silently converted into `false` or `null`. A failed verification never
produces authenticated claims.

JWT proves integrity and adherence to a configured claim profile. It **does
not** verify a password, provision or generate signing keys, read the current
time, implement sessions, check revocations, authorize users, or serve HTTP.
HTTP handlers must perform their own authorization after verification.
Production keys must come from a properly provisioned and protected trusted
source, not an example literal; rotate and safeguard them according to the
application's key-management policy. `std:crypto/hmac_sha256` authenticates
Bytes and `std:text/base64url` transports arbitrary Bytes as URL-safe text,
but neither replaces this JWT profile and policy validation.

See the [HTTP guide](http.md), [cryptography guide](crypto.md),
[text and Base64url guide](text.md), and the runnable
[`05-strict-jwt.protos` tutorial](../../../protos/tutorials/13-http/05-strict-jwt.protos).
