# D048 — IpAddress / IpEndpoint construction and recognition

Status: **RATIFIED**
Specification revision: **`0.1.391`**
Explicit project-owner approval: **2026-09-09**
Nature: bounded normative networking-data construction/recognition decision
Dependency: D047 / specification revision `0.1.388`
Primary implementation consumer: `I028-A`

## Decision

D048 closes the source-visible construction/recognition checkpoint deliberately
left open by D047. The selected Core surface is:

```text
IpAddress(version, bits)
IpEndpoint(address, port)
IpAddress.recognizes(value)
IpEndpoint.recognizes(value)
```

`IpAddress` and `IpEndpoint` are canonical frozen prelude factory/prototypes and
ordinary invocation remains the construction mechanism. Each success produces a
fresh ordinary frozen identity with exact immediate canonical parent and exact
transparent own data slots: `version`/`bits` or `address`/`port`.

Recognition is transparent, not branded. A manually constructed ordinary object
is a standard recognized value when and only when it has the exact frozen,
immediate-parent, exact-own-slot and canonical numeric/nested-state invariant.
Factory provenance is irrelevant. Extra own slots, transitive-only ancestry,
mutable state or coincidental shape under another parent are rejected.

`recognizes(value)` returns canonical Boolean for arbitrary candidates without
calling candidate behavior or performing host/network work. Equality/hash remain
D047 structural laws and `===` remains ordinary identity.

## Why this option

The review compared hidden native branding, shape-only duck typing, transitive
prototype recognition, split IPv4/IPv6 prototypes, named-only factories and
String/text construction. It also revisited Self, Io, Smalltalk, Erlang/OTP,
Java, Rust, Go and WASI.

The selected immediate-parent + exact frozen state model keeps the data ordinary
while preventing accidental shape collisions and mutable/custom intermediate
prototypes from changing canonical inherited equality/hash behavior. Public data
slots let later LIB005 parse/format helpers remain ordinary Protos composition.
D049 independently guarantees that physically shared standard factory/prototypes
are published frozen, reinforcing rather than changing this model.

## Future / scale result

- DNS, text parsing, IPv6 scope, UDP and later transports can layer without
  changing canonical numeric construction.
- Validation is bounded O(1) and creates no authority, DNS cache or network
  resource.
- Actor/distribution transfer remains transparent ordinary frozen data with no
  fd/socket/interface identity or hidden host brand.
- No `ProtosIpAddressValue`/`ProtosIpEndpointValue` semantic representation is
  required. A conforming implementation may use a bounded direct inspection
  bridge for non-overridable recognition if its ordinary source surface cannot
  observe the invariant safely; that bridge is implementation machinery, not a
  new language value family.

## Explicit exclusions

D048 does not introduce text/String-to-address conversion, implicit DNS,
`Ipv4Address`/`Ipv6Address` prototypes, wildcard/port-zero endpoint semantics,
Network authority, TCP backend behavior, UDP, TLS/QUIC/HTTP, interface discovery,
network policy, generic socket options or deadline APIs.

## Implementation handoff

`I028` moves from OPEN to READY. `I028-A` owns the approved address/endpoint
ordinary-object foundation, including construction, recognition, validation,
freeze, structural equality/hash and transfer conformance. This ratification
contains no production implementation.
