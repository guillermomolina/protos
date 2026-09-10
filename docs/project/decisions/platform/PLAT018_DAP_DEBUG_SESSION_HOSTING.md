# PLAT018 — DAP debug-session hosting and endpoint ownership

Status: **RATIFIED**

Nature: durable non-normative GraalVM/Truffle debugger-hosting architecture decision

Approved by project owner: **2026-09-10**

GitHub Issue: **#306**

Primary consumers: `LM009-D` / GitHub #305, `LM009-E`, and later Protos debugger launchers/distributions

Normative effect: **none**. PLAT018 does not change Protos language semantics,
Process/Actor/Task identity, scheduling, failure, cancellation, visibility, or
source/debugger semantics. It selects how the current GraalVM/Truffle
implementation hosts the already-proven DAP tool around a debug invocation.

## Problem

I026-F proved that the real GraalVM Debug Adapter Protocol implementation can
debug Protos source, including verified source breakpoints, threads, stack
frames, activation-local scopes, representative scalar/indexed values, stepping,
continue and clean disconnect. That evidence deliberately used test-owned socket
and staging machinery and did not define a production/public endpoint,
readiness, launch, lifetime or distribution contract.

LM009-D needs a public debugger-launch path that later LM009-E can consume from
VS Code without any of the following becoming permanent architecture:

- a fixed global DAP port;
- editor-side port-allocation coordination;
- a second DAP implementation or protocol proxy;
- a long-lived shared debugger daemon;
- a debugger server owned by one privileged semantic Protos `Process`;
- direct exposure of GraalVM option names as the stable Protos product contract;
- source execution racing breakpoint configuration; or
- ordinary non-debug runs paying active debugger-server cost.

The decision checkpoint therefore concerns platform ownership and lifecycle:
which host object owns the GraalVM DAP instrument, how the endpoint is allocated,
how readiness is communicated, how launch waits for the client, and how this
boundary scales from one local debug session to many independent sessions and to
future runtimes.

## Existing authority and fixed constraints

PLAT018 preserves the following existing authorities:

- I026-F remains the behavioral evidence that the real GraalVM DAP works against
  Protos source.
- PLAT013 remains authoritative for debugger/interop value projection.
- PLAT015 remains authoritative for debugger scope projection.
- LM009-C remains authoritative for the editor's external-Protos-launcher and
  workspace-extension-host model, including VS Code Remote/Dev Container
  placement.
- Protos semantic execution state remains owned by the existing Process/Actor/
  Task/runtime model. A debugger host is implementation machinery, not a new
  semantic runtime entity.
- The exact public CLI spelling, exact readiness serialization, user-facing
  configuration surface, remote-debug product policy, and VS Code adapter
  integration remain owned by LM009-D/E or a later explicit tooling decision.
  PLAT018 constrains those consumers without selecting their syntax by accident.

## Truffle / GraalVM implementation audit

The decision was ratified after an extended comparison across maintained
Truffle-language tooling and the exact GraalVM 25.3.4.1 DAP implementation used
by Protos.

### Cross-language result

The strongest maintained precedents converge on a common separation of
responsibility:

- the language/runtime owns the execution being debugged;
- Truffle instrumentation/debug APIs provide the runtime mechanism;
- the editor consumes a standard protocol instead of becoming a language or
  execution authority; and
- debugger infrastructure is activated for a debug run rather than centralized
  globally for all executions.

Relevant examples included TruffleRuby, GraalJS/Node, GraalPy, Sulong/LLVM,
FastR, Espresso, SOMns, TruffleSqueak and Pkl. They differ in protocol/product
surface, but none provides a compelling reason for Protos to introduce a shared
DAP daemon, a second debugger implementation, or editor-owned semantic state.
Espresso additionally reinforces that Truffle is infrastructure rather than the
user-facing language contract: it can expose Java-ecosystem debugging while
retaining Truffle as the implementation substrate.

### Exact GraalVM 25.3.4.1 findings

The exact retained GraalVM implementation establishes four decisive facts:

1. `DAPInstrument` accepts endpoint port `0` and passes it directly to
   `ServerSocket`, allowing the operating system to allocate the actual ephemeral
   loopback port atomically.
2. `DAPAddressTest.testPort0()` explicitly verifies that this path binds to
   loopback and produces a real dynamically allocated port.
3. `dap.WaitAttached` gates execution until debugger configuration reaches
   `configurationDone`; Protos therefore does not need sleeps, polling or an
   editor-created staging barrier to prevent the guest from outrunning
   breakpoint setup.
4. GraalVM distinguishes DAP `launch` from `attach`. A launched session records
   ownership of the debuggee and its disconnect path cancels the associated
   contexts; attach does not claim the same ownership relationship.

The DAP server also owns system-thread cleanup that must be joined before Engine
close. This further argues for debugger lifecycle to remain with the Truffle
Engine/RuntimeHost lifetime rather than being attached to an arbitrary semantic
Protos Process.

## Rejected alternatives

### A — expose raw GraalVM DAP options as the Protos public contract

Rejected as the stable architecture. It minimizes current implementation work
but leaks `dap`, `dap.Suspend`, `dap.WaitAttached` and GraalVM endpoint details
into Protos compatibility. Every editor/client would then own readiness and
lifecycle policy, and a future runtime/backend migration would inherit accidental
Graal-specific public obligations.

### B — dedicated Protos debug launch with caller-selected port

Rejected as the normal path. It can be correct, but forces IDEs and other clients
to allocate ports and coordinate parallel sessions. The conventional
"find-free-port, close, reopen" sequence also creates a TOCTOU race that is
unnecessary because the retained GraalVM DAP already supports port `0`.

An explicit endpoint may remain a later advanced/manual override if a product
contract genuinely needs it. Such an override is not the automatic IDE baseline
selected here.

### C — runtime-owned session-local endpoint with explicit readiness

Superseded by the refined C-prime formulation below. The original candidate
correctly selected runtime ownership and per-session readiness but described the
debugger too narrowly as belonging to one Protos Process and left automatic
allocation abstract. The exact GraalVM Engine/instrument and port-0 evidence
allows a cleaner ownership and allocation boundary.

### D — stdio DAP proxy/shim

Rejected. A Protos adapter process that proxies DAP frames between VS Code and
GraalVM would duplicate framing, buffering, failure, cancellation and lifetime
machinery despite GraalVM already implementing the protocol. It would create a
permanent integration layer with no semantic benefit.

### E — long-lived shared debugger daemon

Rejected. A global daemon would introduce session routing, isolation, shared
mutable lifecycle, cleanup and security coordination between otherwise
independent executions. It scales by centralization rather than by ordinary
composition and makes unrelated debug sessions failure-coupled.

## Selected architecture — C-prime

**C-prime: RuntimeHost-owned one-shot GraalVM DAP session with an OS-ephemeral
loopback endpoint and a Protos-owned readiness boundary.**

Conceptually:

```text
one debug invocation
        |
        v
ProtosPolyglotRuntimeHost
        |
        +-- one Truffle Engine
        |       |
        |       +-- real GraalVM DAP instrument
        |               |
        |               +-- loopback : 0
        |               +-- OS selects real ephemeral port
        |               +-- WaitAttached = true
        |
        +-- zero or more Process-scoped Contexts belonging to that invocation
        |
        +-- Protos-owned readiness adapter
                |
                +-- stable control record -> debugger client
```

The debugger therefore belongs to the **debug invocation / RuntimeHost / Engine
lifetime**, not to one privileged semantic Process. This matches the current
runtime topology in which one `ProtosPolyglotRuntimeHost` owns one shareable
Engine and hosts Process-scoped Contexts beneath it. It also matches GraalVM's
Engine rule that Contexts sharing one Engine share instruments and their
configuration.

## Durable constraints

1. **One debug invocation owns one debugger host lifetime.** The implementation
   must not introduce a JVM-global DAP singleton or long-lived shared daemon.

2. **RuntimeHost/Engine ownership is authoritative.** The GraalVM DAP instrument
   belongs to the debug invocation's `ProtosPolyglotRuntimeHost` / Truffle
   `Engine` boundary. No semantic Protos `Process`, Actor, Task or object becomes
   the privileged owner merely to fit debugger implementation convenience.

3. **Use the real GraalVM DAP.** Protos and the editor must not implement a
   second DAP server or proxy the protocol in the baseline architecture.

4. **Automatic allocation is loopback port `0`.** The default debugger endpoint
   must ask the retained GraalVM/JDK socket layer to bind loopback port `0`, so
   the OS performs collision-free allocation atomically. The baseline must not
   scan for a free port, reserve then release a port, or use a fixed global port.

5. **No default arbitrary-interface exposure.** Baseline automatic debugging
   binds to loopback only. Remote-network listening is not implicitly enabled by
   editor convenience.

6. **Readiness is explicit and Protos-owned.** A launcher/runtime control record
   communicates the actual endpoint after the DAP socket is bound and before
   guest execution proceeds. This record is control metadata, not guest stdout
   and not guest semantics.

7. **GraalVM diagnostic text is not a public editor API.** If the current
   implementation must derive the actual port from the retained GraalVM
   readiness diagnostic because no suitable supported endpoint API exists, that
   parsing remains inside one version-bounded Protos adapter. VS Code and other
   consumers must see only the stable Protos-owned readiness contract.

8. **Version-bound internals may change.** A later GraalVM release may expose a
   supported endpoint API or change its diagnostic internals. Protos may replace
   the private readiness adapter without changing the public debugger-launch
   contract, provided the selected ownership/readiness invariants remain true.

9. **Guest execution waits for debugger configuration.** The launch path uses
   the retained GraalVM wait-attached/configuration gate so source cannot run
   ahead of breakpoint installation. It must not emulate this guarantee with
   sleeps or timing heuristics.

10. **Normal F5 does not imply stop-on-entry.** The baseline launch uses the
    equivalent of GraalVM `Suspend=false` while retaining wait-for-configuration.
    A future explicit stop-on-entry user option may map differently but is not
    selected by PLAT018.

11. **F5 consumes DAP launch semantics.** The editor-launched baseline is a DAP
    `launch` relationship, not a test-style/manual `attach`. Attach remains a
    distinct later/manual mode and must not silently acquire launch ownership.

12. **Termination claims require retained evidence.** GraalVM's current launched
    disconnect path cancels associated contexts, but LM009-D/E must prove the
    complete Protos process/context cleanup behavior before promising a specific
    user-visible Stop/`terminateDebuggee` contract. PLAT018 does not invent
    stronger termination semantics than the retained implementation proves.

13. **Normal completion releases debugger resources with the RuntimeHost/Engine
    lifecycle.** DAP system threads/socket/session state must not outlive the
    owning debug invocation through leaked global state.

14. **Parallel sessions scale independently.** N concurrently active debug
    invocations may own N independent loopback endpoints without a global port
    registry or daemon. Resource cost is proportional to active debug
    invocations rather than all Protos executions.

15. **Ordinary runs do not activate DAP.** Non-debug execution creates no DAP
    listener/session/thread state. Distribution bytes required to make debugging
    available are acceptable product footprint; active debugger cost remains
    pay-for-use.

16. **The public contract is backend-neutral.** User-facing Protos concepts are
    "launch/debug this Protos execution" plus a stable readiness/connection
    contract. They do not promise GraalVM option spellings. A future native,
    Windows or non-Graal backend may realize the same contract differently.

17. **Remote/Dev Container placement follows the execution host.** The launcher,
    DAP endpoint and workspace-side editor extension host should remain colocated
    under the LM009-C workspace-host model, avoiding cross-host path/port
    guessing in the default Remote/Dev Container workflow.

18. **Distributed debugging composes; it does not become a cluster-wide semantic
    singleton.** A future distributed tool may coordinate multiple independent
    runtime-host debug sessions, but PLAT018 does not introduce one global
    debugger universe into Protos semantics.

19. **No semantic observability is added.** PLAT013/015 and the retained Truffle
    instrumentation/source authorities remain the only debugger value/scope/
    source projections. Endpoint, readiness and server lifetime are host
    machinery.

20. **Semantic surprises stop implementation.** If LM009-D/E discovers that
    satisfying this host architecture requires changing observable Protos
    Process/Actor/Task/cancellation/failure semantics, the affected work stops
    and crosses the appropriate Dxxx/specification approval gate instead of
    extending PLAT018 silently.

## Scalability assessment

C-prime scales by composition rather than coordination:

- endpoint allocation is kernel-local and atomic;
- no process contends on a global debugger registry or fixed port;
- each inactive ordinary execution carries zero active debugger state;
- active debugger state is O(number of active debug invocations);
- one RuntimeHost/Engine naturally covers all Process Contexts that already
  belong to that debug invocation without manufacturing one DAP server per
  Process; and
- remote/container deployments preserve the same local ownership boundary.

This is preferable to both per-Process debugger proliferation and daemon-style
centralization. It also leaves room for later capacity policies if real evidence
shows large numbers of simultaneous debugger sessions matter; no such global
policy is imposed preemptively.

## Future evolution

The selected public architecture survives the most likely runtime changes:

- **GraalVM DAP API improvement:** replace the version-bounded readiness adapter,
  keep the Protos readiness contract.
- **GraalVM diagnostic change:** fail compatibility validation for the supported
  runtime instead of silently misdiscovering an endpoint; adapt the internal
  boundary in the matching runtime-support change.
- **Native Windows launcher:** expose the same Protos debug/readiness contract
  while using the platform's normal launcher/process mechanics.
- **Alternative Truffle transport/tool:** a later PLAT decision may replace the
  internal mechanism while preserving the public Protos contract if equivalent
  debugger behavior exists.
- **Non-Graal backend:** the public contract remains meaningful without carrying
  GraalVM option names into compatibility.
- **Many local/distributed executions:** compose independent debug sessions;
  orchestration, if needed, remains a higher-level tool rather than new language
  semantics.

## Distribution implication

At ratification time Protos' Maven project retains the GraalVM DAP dependency as
test-only I026-F evidence, while the portable runtime dependency closure contains
the optimizing Truffle runtime but does not yet intentionally package the DAP
tool as a supported production debugger component.

Therefore LM009-D and/or its distribution follow-up must make the compatible DAP
tool available to the shipped debugger launch path before clean-install debugger
acceptance can close. The selected direction is to package the matching DAP tool,
not a broad unrelated tooling universe, and to keep it inactive during ordinary
non-debug execution.

This distribution consequence does not authorize an implementation change in
this ratification publication.

## LM009-D boundary released by ratification

PLAT018 releases LM009-D to implement the platform portion of the public debugger
launcher using the selected RuntimeHost/Engine ownership, port-0 loopback,
wait-attached staging and readiness architecture.

It does **not** settle the remaining public tooling-policy choices that belong to
LM009-D itself, including:

- exact CLI command/argument spelling;
- exact stable readiness-record serialization and stream framing;
- any explicit endpoint override syntax;
- user-facing stop-on-entry configuration;
- manual attach UX;
- remote-network debugging policy; or
- the exact VS Code F5 configuration surface owned by LM009-E.

If any of those choices materially constrains the durable public tool contract,
LM009-D must present the appropriate tooling decision checkpoint before
implementation crosses it.

## Ratification result

**Selected:** C-prime — RuntimeHost-owned one-shot GraalVM DAP session with
OS-ephemeral loopback endpoint and Protos-owned readiness.

The selection is explicitly optimized for:

- correctness against the retained GraalVM lifecycle;
- independent parallel debug sessions;
- minimal shared mutable state;
- zero active debugger cost for ordinary runs;
- future GraalVM/native/Windows/backend evolution;
- Remote/Dev Container locality; and
- the Protos principles of mechanisms over institutions, no privileged semantic
  pets, pay only for what is used, independence over coordination, and scaling by
  composition rather than changing universes.
