# D052 — TCP live-resource object topology

Status: **RATIFIED**
Specification revision: **`0.1.393`**
Explicit project-owner approval: **2026-09-09**
Nature: normative Core v0.1 TCP live-resource object/delegation decision
Primary normative owner: `spec/io/NETWORK.md`
Implementation consumers: `I028-C` / `I028-D`

## Decision boundary

D047 already defines Network authority, TCP acquisition, TcpConnection/TcpListener capability roles,
Byte I/O/lifecycle composition and Actor/P non-transferability. D052 fixes only the previously
unspecified Protos-visible object topology of acquired live TCP resources. It does not select a JVM,
OS, event-loop, selector, channel, fd, thread, Actor, broker or other implementation representation.

## Selected semantics

A successfully acquired `TcpConnection` and `TcpListener` is an ordinary Protos identity-bearing
object. At acquisition its ordinary structural state is `OPEN`.

Each family has one canonical standard frozen behavior prototype for the active Core standard-object
domain:

```text
TcpConnection capability -> canonical TCP-connection protocol prototype -> Object
TcpListener capability   -> canonical TCP-listener protocol prototype   -> Object
```

The immediate parent edge is portable and observable through ordinary `parent()`, lookup and
reflection. The family protocol prototypes themselves are authority-free ordinary standard objects,
delegate directly to `Object`, and are frozen before observation. Core v0.1 requires no public Prelude
binding, constructor or global identifier named `TcpConnection` or `TcpListener`; possession of a live
resource may expose its standard protocol prototype through ordinary `parent()`.

The standard TcpConnection selectors are local behavior of its family protocol prototype:
`read`, `write`, `close`, `shutdownRead`, `shutdownWrite`, `localEndpoint`, and `remoteEndpoint`.
The standard TcpListener selectors are local behavior of its family protocol prototype:
`accept`, `localPort`, and `close`. A freshly acquired concrete resource has no mandatory local Protos
slots merely to materialize those standard selectors or backend state. While its structural state
permits ordinary mutation, application code may create local slots and may shadow inherited selectors
under the normal object/lookup rules.

Delegation never manufactures TCP authority or semantic-family membership. An ordinary object whose
parent is a TCP family prototype, or whose parent is a concrete TCP capability, is still an ordinary
non-TCP object. If lookup reaches standard TCP family behavior with such an incompatible original
receiver, invocation fails under the ordinary standard receiver-domain rule before exercising TCP
resource state. Only a resource delivered by a standardized successful TCP acquisition belongs to the
corresponding live-resource family.

The nearer TCP-family `close` selector is the `Closable` resource lifecycle operation specified by
Core I/O. It is not the structural `Object.close()` transition merely because both messages use the
same spelling. Resource close does not by itself change the Protos object's OPEN/CLOSED/FROZEN
structural state. Conversely, ordinary structural state does not redefine the already-specified TCP
resource lifecycle. Ordinary lookup/shadowing and `super` semantics resolve this selector relationship;
D052 adds no second dispatch mechanism.

D047's initial transfer rule remains unchanged: concrete `Network`, `TcpConnection`, and
`TcpListener` capabilities, and ordinary graphs that reach such live authority, are not Actor/P
transferable. The authority-free standard TCP protocol prototypes do not themselves convey resource
authority.

## Endpoint observation boundary

D052 does **not** strengthen D047's endpoint identity contract. The remote logical endpoint of a
successful outgoing connection remains structurally equal to the supplied recognized `IpEndpoint`,
and local/remote endpoint accessors return recognized endpoint data as already required by D047/D048.
This revision intentionally does not specify whether repeated endpoint observations return the same
object identity, fresh structurally equal objects, or whether an outgoing remote endpoint is `===` to
the acquisition argument. Any future stronger identity promise requires its own explicit semantic
approval; no such decision is allocated here.

## Why this option

The review compared per-instance ordinary File-style protocol installation, public Prelude TCP type
objects, represented-value TCP pseudo-objects, a generic HostResource hierarchy, and ordinary live
objects delegating to shared non-authoritative protocol prototypes. The selected model preserves
Protos's ordinary object/delegation universe while avoiding per-connection duplication of protocol
behavior and unnecessary global names. It matches the existing receiver-domain distinction that
ordinary delegation grants lookup but not semantic-family membership.

The design was stress-tested against high-cardinality servers, full-duplex proxies, cancellation,
half-close, TLS layering, future QUIC as a separate transport, brokered/multiprocess runtimes and
backend replacement. None requires a new Protos-visible socket/event-loop/channel universe.

## Scale and future invariants

- Protocol behavior scales with the number of standard family prototypes, not the number of live
  connections/listeners.
- A live TCP resource does not imply one thread, Actor, event loop or selector registration as
  semantic identity.
- Local application metadata/custom behavior remains ordinary local Protos state rather than a
  wrapper-specific extension API.
- A future backend or physical representation may change without changing this topology.
- A future public convenience binding for a TCP family, if ever desired, is not approved here and
  must justify the additional global surface separately.

## Explicitly deferred

D052 does not select endpoint `===` identity, TCP backend machinery, DNS, UDP, TLS/QUIC/HTTP,
transferable resource proxies, timeout/deadline APIs, generic socket options, or a generic host-resource
hierarchy. PLAT003 records the separately approved durable JVM implementation architecture that must
remain observationally conformant with these semantics.
