# LIB006 Deterministic Hashing Design Record

Status: **DESIGN CLOSED — bounded SHA-256 implementation surface approved**
Work item: `LIB006`
Nature: Project design record; **non-normative**
Design closed: 2026-09-07

## Purpose

`LIB006` provides a reusable ordinary Standard Library owner for deterministic
non-keyed hashing needed by Package Tool resolution-input digests and other
future tooling/library use.

The initial bounded scope is deliberately one algorithm and one whole-buffer
operation:

```text
std:crypto/SHA256

digest(bytes) -> Bytes
```

This record does not add a Core value family, native cryptography primitive,
entropy source, MAC, password hashing API, signature API, cipher, TLS facility,
streaming hash state, filesystem authority, network authority, or Package
Tool-specific host shortcut.

The first implementation slice is `LIB006-B`. Closing that slice closes the
bounded initial LIB006 scope and satisfies the hashing prerequisite currently
blocking `TOOL001-F2B3`.

## Audit material

Materially reviewed for this decision:

- `AGENTS.md`
- `docs/design/STANDARD_LIBRARY_IDEAS.md`
- `docs/design/PROTOS_DESIGN_PHILOSOPHY.md`
- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/TOOL001_PACKAGE_TOOL.md`
- `docs/design/PACKAGE_LOCKFILE_FORMAT.md`
- `docs/design/PACKAGE_VERSION_RESOLUTION.md`
- `spec/semantics/VALUES_AND_COLLECTIONS.md`
- existing ordinary Standard Library module placement under `protos/lib/**`.

The relevant existing boundary is:

- Core already owns exact arbitrary-precision Integer arithmetic;
- Core v0.1 exposes exact integer `div` and `mod`;
- Core does not currently expose a public bitwise/wrapping API;
- the Standard Library ideas audit already places cryptographic hashes outside
  Core and warns against silently inheriting JVM/native crypto semantics.

## Selected module identity

The initial module is:

```text
std:crypto/SHA256
```

Physical source:

```text
protos/lib/crypto/SHA256.protos
```

This selects a reusable Standard Library owner rather than:

```text
self:Sha256
java.security.MessageDigest
Core.SHA256
```

The Package Tool may later import this normal `std:` module through its existing
bundled-tool resolver composition.

## Public API

The initial surface is exactly:

```text
digest(bytes) -> Bytes
```

### Input

`bytes` is a Core `Bytes` value representing the complete message octet
sequence.

The operation:

- reads the message value only;
- performs no I/O;
- consumes no ambient authority;
- does not mutate the caller's Bytes;
- does not retain the caller's Bytes after completion.

An invalid input domain fails through the ordinary library Error path. LIB006
does not introduce a new Core Error prototype merely for argument validation.

### Result

A successful call returns a **fresh** Core `Bytes` value containing exactly
32 octets: the SHA-256 digest in standard network/big-endian digest-byte order.

Repeated calls over equal input octet sequences return equal digest bytes but
fresh result objects. No singleton/cache identity is observable.

The initial module deliberately does **not** add `digestHex`. Hexadecimal
presentation remains a separate encoding concern; Package Tool F2B3 already owns
the lowercase-hex spelling required by the lock grammar.

## Algorithm contract

`digest` implements SHA-256 as defined by the SHA-2 256-bit algorithm in
FIPS 180-4:

- 512-bit message blocks;
- standard `0x80` + zero padding;
- 64-bit big-endian message-length field modulo the SHA-256 algorithm limit;
- the standard eight initial 32-bit words;
- the standard 64 round constants;
- the standard `Ch`, `Maj`, upper/lower sigma functions;
- addition modulo `2^32`;
- standard final eight-word big-endian digest serialization.

The observable contract is the SHA-256 octet mapping. Internal helper names,
word representation, schedule storage, loop decomposition and optimization are
not API.

## Pure-Protos initial implementation

`LIB006-B` must be implemented in ordinary Protos source without a new Java
native Closure, JVM crypto call, host library bridge or extra capability.

The lack of a public bitwise API does not block the initial implementation.
Internal SHA-256 word operations can be expressed over non-negative exact
Integers:

```text
word32(x)       = x mod 2^32
logicalShr(x,n) = x div 2^n
```

and private fixed-32-bit helpers can derive AND/XOR/NOT/rotate behavior from
exact quotient/remainder arithmetic.

Those private helpers do **not** become `std:math/Bits`, Core operators, or a
general wrapping-arithmetic contract. A future reusable Bits library remains an
independent design.

This arithmetic implementation is intentionally chosen as the bootstrap-safe,
portable correctness path. A later optimization may replace internal machinery
only if the public digest result and ordinary library semantics remain identical
and any native/host boundary is separately audited.

## Security boundary

The initial module is a deterministic **unkeyed hash**, not a general
cryptography toolkit.

It makes no constant-time guarantee and must not be presented as:

- a MAC;
- password hashing / password storage;
- a KDF;
- authenticated encryption;
- a signature primitive;
- a random oracle API with secret-key handling guarantees.

No secret key or entropy enters `digest`.

If future keyed/secret-bearing cryptographic APIs are added, they require a
separate security/API/native-boundary audit. LIB006-B does not pre-authorize
them merely because the physical namespace is `std:crypto`.

## Message-size boundary

The SHA-256 format carries a 64-bit bit-length field. The initial implementation
must reject an input whose byte length cannot be represented by the standard
SHA-256 64-bit bit-length construction rather than silently wrap a larger
ordinary Protos Integer length.

This is algorithm validation, not host-memory policy.

The implementation may naturally encounter memory/resource limits earlier, but
must not define host `int`/array size as the semantic SHA-256 maximum.

## Determinism and state

The module is stateless from the caller's perspective:

- no mutable module-global hash context;
- no shared digest instance;
- no hidden cache;
- no randomness;
- no time/locale/encoding dependence;
- no Actor-shared mutable hash state.

All per-call working state remains local to the invocation.

## Initial conformance

`LIB006-B` must make behavior expectations live primarily in Protos source.

At minimum the focal corpus should cover:

1. empty message SHA-256;
2. `"abc"` encoded as UTF-8 bytes;
3. the standard multi-block
   `"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"` vector;
4. at least one binary/non-text Bytes vector;
5. repeated equal input -> equal octets + fresh result identity;
6. caller input remains unchanged;
7. exact result length 32;
8. invalid non-Bytes input fails;
9. a boundary-focused padding case around 55/56/64 bytes.

A Java test is not the semantic owner merely because Java has a convenient
`MessageDigest`. Host-side code may be used only as a mechanical harness if
needed; expected digest values must be fixed independently in the Protos
fixtures.

## Explicit exclusions

Initial LIB006 does not include:

```text
SHA-1
SHA-512
SHA-3
BLAKE*
MD5
HMAC
PBKDF2 / scrypt / Argon2
incremental update/finalize state
stream/File/TextReader hashing
hash algorithm registry
algorithm-name dispatch
hardware/native acceleration contract
entropy/randomness
```

Additional algorithms or streaming state require focused follow-up work rather
than widening LIB006-B during implementation.

## TOOL001 dependency

`TOOL001-F2B3` already has a complete semantic resolution-root model after F2B2.
It remains dependency-blocked until LIB006-B publishes this reusable hashing
owner.

After LIB006-B closes:

```text
LIB006          -> CLOSED
LIB006-B        -> CLOSED
TOOL001-F2B3    -> READY
```

F2B3 may then:

1. serialize the already-frozen semantic resolution input;
2. call `std:crypto/SHA256.digest`;
3. lowercase-hex encode those 32 digest octets under the existing lock grammar;
4. compare the current digest with the canonical lock header.

LIB006 does not own any of those Package Tool projection/stale policies.
