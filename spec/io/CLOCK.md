# Core Clock capability (D253-C1)

## 1. Protocol and authority

`Clock` is an authority-free, frozen standard Core protocol whose immediate
delegation parent is `Object`. Its only own method is `epochNanoseconds`.
It is retained privately by Core, not published as a Prelude binding or a
Standard Library module. Possessing its prototype, copying ordinary slots or
delegating to a grant cannot create authority. Only an authentic host-provisioned
grant authorizes observation; apparent shape or delegation-parent equality alone
is insufficient.

Clock authority is independent of Process, Filesystem, Network, Resolver,
Entropy, inspection and foreign access. None implies or is implied by Clock.
`PROCESS_IO.md` owns initial RootActor bootstrap placement and opt-in policy.
Without explicit authorization the bootstrap-local `clock` slot is absent,
never a `null` placeholder. Importing code does not acquire Clock.

## 2. Synchronous observation

```protos
clock.epochNanoseconds()  // -> Integer
```

The operation accepts exactly zero arguments and synchronously returns one
exact semantic `Integer`: wall-clock nanoseconds since `1970-01-01T00:00:00Z`
on the uniform Unix-like timeline used by `std:datetime/instant`. Negative
values represent times before the epoch. Given one observation's integral epoch
seconds `s` and normalized nanosecond fraction `n` (`0 <= n < 1000000000`),
the result is the mathematical integer `s * 1000000000 + n`, without overflow,
floating-point conversion, clamping or a signed-64 nanosecond limit.

Nanoseconds specify the integer unit, not physical resolution or UTC accuracy.
Samples may repeat, move backward or jump forward. There is no monotonicity,
rollback resistance, trusted timestamp signature, time attestation or cross-node
ordering guarantee. Implementations must not fabricate sub-resolution digits.

Observation creates no Future or Task, suspension, background work, implicit
provider discovery, guest callback or Standard Library import. Loading the
source-backed protocol performs no clock observation.

## 3. Errors and lifetime

Invalid receiver or argument admission signals a fresh ordinary
`InvalidIOArgument` before any provider observation. A retired grant signals
fresh `IOLifecycleError`; a failed or malformed provider observation signals
fresh `IOError`. These are ordinary synchronous signals under `IO_CORE.md`
and `../semantics/ERRORS.md`, never failed Futures or fallback success values.
An already selected Protos signal is preserved. Host implementation details
must not escape through provider-failure diagnostics.

A grant belongs to its provisioning host and at most one Process incarnation.
It cannot be reused in another Context, rebound or revived. Host revocation,
Process termination and Context shutdown retire its authority idempotently.
There is no guest `close()`, Closable implementation, lifecycle Future or
asynchronous commitment protocol. Service READY is not termination.

## 4. Isolation

Authentic grants are neither Actor-transferable nor delegable in C1. Messages,
requests, replies and spawn initialization reject complete graphs reaching the
grant, including nested containers, cycles and delegation-parent edges,
atomically with `NonTransferableValue` (`../concurrency/ACTORS.md`).
Isolated P execution cannot capture, receive, return or copy Clock authority;
such attempts signal `NonParallelValue` under
`../concurrency/PARALLEL_EXECUTION.md`. Read-only observation grants no exception.

Completed Integer samples are ordinary transferable numeric data. A RootActor
may broker samples through normal Actor messaging; recipients obtain data,
without observation authority or a freshness guarantee.

## 5. Hosts and portability

Trusted hosts may inject deterministic test sources. Guest code cannot create a
grant or select a provider. JVM embedding and standalone JVM/Native CLI preserve
the same observable contract; no particular syscall, FFM, JNI, operating system
or physical resolution is mandated. Samples must represent runtime observations,
not timestamps cached during compilation or Native Image generation.

## 6. Explicit composition and exclusions

```protos
Instant: import("std:datetime/instant")
now: Instant(clock.epochNanoseconds())
```

The existing authority-free Instant constructor receives the exact Integer.
JWT continues to require caller-supplied genuine Instants in
`JWT.issue(claims, key, policy, now)` and `JWT.verify(token, key, policy, now)`.
An Instant's type does not attest to its source's trustworthiness. Logging may
receive the explicitly supplied zero-argument callback
`() => { Instant(clock.epochNanoseconds()) }`; logging acquires no authority.

C1 excludes monotonic clocks, timers, deadlines, sleeps, timeouts, Future reads,
remote clocks, direct grant delegation, ambient `now()`, `Instant.now()`,
`Clock.now()` and a `std:datetime/clock` facade. Civil-clock observation does not
provide HTTP deadline or slow-client bounds.
