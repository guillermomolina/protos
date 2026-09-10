# D060 — Public debugger launcher and readiness contract

Status: **RATIFIED**

Allocated: **2026-09-10**

Explicit project-owner approval: **2026-09-10**

Nature: durable implementation-independent public tooling contract

Triggered by: `LM009-D` / GitHub #305

Decision issue: GitHub #308

Platform dependency: `PLAT018` — RATIFIED

Primary consumers: `LM009-D`, `LM009-E`, later debugger-capable editor/tool integrations

Normative language effect: **none**. D060 defines the public debugger-launch tooling
boundary. It does not change Protos language semantics, Process/Actor/Task
semantics, debugger value/scope/source semantics, or the PLAT018 GraalVM/Truffle
hosting architecture.

## Decision boundary

PLAT018 already selects the implementation architecture beneath this contract:

- one debug invocation owns one `ProtosPolyglotRuntimeHost` / Truffle Engine;
- the real GraalVM DAP instrument is used rather than a Protos DAP proxy;
- the default endpoint is loopback port `0`, allocated atomically by the OS;
- readiness is translated through a stable Protos-owned boundary;
- guest execution waits for debugger configuration;
- normal F5 uses DAP `launch` semantics; and
- ordinary non-debug runs activate no debugger server.

D060 owns the implementation-independent launcher/readiness surface consumed by
humans, VS Code and other debugger clients. It must remain meaningful if the
internal GraalVM machinery is replaced later.

## Ecosystem audit

The selected contract was approved only after an extended comparison of mature
debugger launch/discovery patterns, including the project owner's explicit
request to examine the readiness-file alternative in depth.

Four recurring families were identified.

### 1. Dedicated adapter process over stdio

Dart's debug adapter, GDB DAP mode, `lldb-dap`, NetCoreDbg and similar integrations
can be launched directly by an IDE and speak DAP over stdin/stdout. This is a very
small contract when the debug adapter is an independent process.

It is not the Protos baseline because PLAT018 deliberately retains GraalVM's DAP
instrument inside the owning RuntimeHost/Engine and rejects adding a second
stdio-DAP proxy solely to adapt transports.

### 2. Runtime-owned ephemeral endpoint + startup-stream readiness

Go/Delve, Java/JDWP, Node Inspector and Ruby `rdbg` all demonstrate variants of
the same general model: the runtime/debugger owns an endpoint, may select it
dynamically, reports where it is listening, and a client connects afterward.

Go/Delve is especially close to the selected Protos model: its DAP server may
listen on loopback port `0`, and the VS Code integration observes a startup
readiness message before connecting. This validates the use of a one-shot child
process stream as an ordinary rendezvous when the IDE launched the debugger
process itself.

### 3. Existing structured control plane

Java/JDT LS and Scala/Metals demonstrate an attractive later architecture in
which an already-existing language/build service starts the debug session and
returns the adapter endpoint structurally.

Protos does not create such a daemon/service merely for debugger endpoint
discovery. If a later language-service host already exists for independently
justified LM009-F/H responsibilities, it may internally consume or adapt this
D060 contract without changing the baseline launcher semantics.

### 4. Out-of-band rendezvous file

The strongest mainstream precedent for the rejected-baseline Candidate D is VS
Code Java's no-config/manual-terminal debugging. A Java program may be started by
a shell or build tool that is not a child process directly owned by the editor;
a communication file then provides an out-of-band endpoint discovery mechanism.

This pattern is legitimate when there is no parent/child stream relationship. It
also has real costs: unique-path allocation, permissions, atomic replacement,
stale-file/session identity, crash cleanup, file watchers, Remote/Container path
placement and Windows temporary-path semantics.

D060 therefore does **not** reject readiness files categorically. It rejects
making them mandatory for the ordinary F5 path where the editor already owns the
launcher process and its pipes. A future `debug terminal` / no-config/manual
launch capability may deliberately add a rendezvous-file mode after its own
design checkpoint.

## Ratified decision — B-prime

D060 selects:

```text
protos debug <file> [application-args...]
```

plus one versioned Protos-owned readiness record on **stdout**.

Conceptually:

```text
debugger client / editor
        |
        | starts
        v
protos debug source.protos [...]
        |
        +-- PLAT018 debug invocation / RuntimeHost / Engine
        |       |
        |       +-- real DAP @ loopback : 0
        |
        +-- stdout:
              PROTOS_DEBUG_READY {...}
                        |
                        v
                client connects DAP
```

No second debugger executable is introduced.

## Public launcher contract

1. **One existing launcher.** `debug` is a first-class subcommand of the ordinary
   `protos` launcher. Repository-internal Java class names, JAR paths and GraalVM
   option names are not public debugger invocation syntax.

2. **One explicit source file in the baseline.** The baseline form debugs one
   ordinary source path. Workspace/package logical-entry debugging is not
   silently inferred. A later public form may be added deliberately.

3. **Application arguments remain ordinary.** Arguments following `<file>` become
   ordinary application arguments under the same convention as direct file
   execution. Neither the `debug` subcommand nor the source identity becomes a
   guest `process.args()` value.

4. **Automatic endpoint ownership remains PLAT018.** The normal path does not ask
   the user or editor to select a port. It uses the RuntimeHost-owned loopback
   port-0 allocation selected by PLAT018.

5. **Exactly one readiness success record.** After the debugger endpoint is
   genuinely bound and before guest source can execute, the launcher emits
   exactly one UTF-8 newline-terminated readiness record beginning with the exact
   ASCII marker `PROTOS_DEBUG_READY`, followed by exactly one U+0020 SPACE and
   then the JSON payload.

6. **Version-1 readiness payload.** The prefix is followed by one JSON object.
   Version 1 requires:

   - `version`: integer `1`;
   - `protocol`: string `"dap"`;
   - `transport`: string `"tcp"`;
   - `host`: the actual numeric loopback address selected for the endpoint; and
   - `port`: the actual allocated TCP port as an integer in the valid TCP port
     range.

   A typical producer record is:

   ```text
   PROTOS_DEBUG_READY {"version":1,"protocol":"dap","transport":"tcp","host":"127.0.0.1","port":54321}
   ```

7. **JSON field order is not semantic.** Producers should use compact deterministic
   JSON for stable diagnostics/tests, but consumers must parse JSON rather than
   depend on property ordering or incidental whitespace inside the object.

8. **Numeric loopback host, not DNS policy.** Version 1 reports the actual numeric
   loopback address (`127.0.0.1`, `::1`, or another valid loopback representation
   if a future backend requires it). Host and port remain separate fields so IPv6
   syntax is never inferred by splitting a `host:port` string.

9. **Stdout owns successful startup control.** Before readiness, stdout is reserved
   for this one success/control record. No human prose, ANSI styling or localized
   text may be mixed into that machine record.

10. **Stderr owns launch diagnostics/failure.** Pre-readiness diagnostics and launch
    failures belong on stderr. The readiness record itself is not written to
    stderr.

11. **Guest output remains debugger output.** Under the PLAT018 baseline, guest
    execution is held until debugger configuration and GraalVM DAP publishes guest
    stdout/stderr as DAP output events. D060 does not redefine program I/O
    semantics; it reserves only the pre-execution launcher readiness boundary.

12. **Consumers stop parsing after readiness.** A client recognizes only the first
    valid record with the exact prefix while awaiting readiness. After accepting
    it, the client must stop treating later process output as D060 control
    framing.

13. **Failure is explicit, not guessed.** Process termination before a valid
    readiness record is debugger-launch failure. Clients must not scan ports,
    connect speculatively, or infer readiness from timing.

14. **Forward-compatible payload.** Consumers of readiness version 1 must ignore
    unknown JSON members while validating all required v1 fields. Missing,
    duplicate-invalid, wrongly typed or semantically invalid required fields are
    readiness failures; consumers must not guess replacements.

15. **Incompatible framing requires an explicit version evolution.** Additive v1
    members are permitted. An incompatible change to required meaning/framing
    needs an explicitly designed version transition rather than reinterpretation
    of existing records.

16. **Local-only baseline.** D060 adds no arbitrary-interface network exposure.
    Remote-network debugger listening is not silently enabled.

17. **No baseline endpoint override.** `--listen`, explicit host/port selection and
    caller-owned endpoint allocation are not part of the initial contract. They
    may be added later only for a demonstrated manual/remote use case.

18. **No baseline manual attach UX.** A separate attach command/session-discovery
    UX is deferred. D060 does not conflate attach ownership with F5 launch
    ownership.

19. **No baseline stop-on-entry option.** Normal F5 retains PLAT018's selected
    wait-for-configuration without implicit stop-on-entry. A later public option
    may be designed independently.

20. **No stronger termination promise.** D060 does not claim unproven
    `terminateDebuggee` behavior. DAP launch/disconnect and complete Protos
    Process/Context cleanup remain governed by PLAT018 plus focused LM009-D/E
    evidence.

21. **Backend-neutral public meaning.** A future native Windows launcher,
    alternative Truffle tool or non-Graal backend may realize the same
    `protos debug` + readiness contract with different internal machinery.

## Why readiness uses stdout

The initial D060 proposal placed the success record on stderr. The ecosystem
audit caused that detail to be revised before approval.

The selected split is:

```text
stdout  -> successful machine-readable launcher readiness
stderr  -> launcher/runtime diagnostics and launch failure
DAP     -> guest stdout/stderr once the debug session runs
```

For the selected PLAT018 path this is cleaner than stderr because guest execution
cannot race the readiness phase and guest streams are already projected through
DAP after attachment/configuration. It also matches the ordinary CLI distinction
between successful result/control data and diagnostics.

This stdout choice is specific to the one-shot pre-execution debugger readiness
contract. It does not establish stdout as a generic control protocol for other
Protos tools.

## Why the readiness-file baseline is deferred

A file rendezvous solves a different problem well: endpoint discovery between
independently launched processes where the debugger client is not the process
parent.

For normal LM009-E F5, requiring a file would create additional state for no
corresponding capability:

```text
per active session:
    launcher process
    DAP socket
    + rendezvous pathname
    + file lifecycle
    + file watcher
    + stale/crash identity validation
```

B-prime instead composes resources the launcher already has:

```text
per active session:
    launcher process
    existing process pipe
    DAP socket
```

Both are O(active debug sessions), but B-prime has less mutable/persistent
coordination state and no global filesystem namespace to reconcile.

If future no-config/manual-terminal debugging creates the independent-process
discovery requirement, Candidate D becomes a valid new consumer pattern rather
than technical debt carried by every F5 invocation.

## Scalability and concurrency

Each debug invocation remains independent:

- one launcher process/invocation;
- one PLAT018 RuntimeHost/Engine;
- one real DAP server;
- one OS-selected loopback endpoint; and
- one one-shot readiness record on the invocation's own stdout.

N simultaneous debug invocations therefore have O(N) active debugger state with
no shared port registry, readiness directory, daemon or serialized allocator.
The kernel performs endpoint allocation and each parent/child relationship owns
its own readiness stream.

Ordinary execution pays none of this active debugger cost.

## Remote, containers and Windows

LM009-C already selects a workspace-extension-host model in which executable
editor integration runs where the workspace and Protos launcher live. B-prime
fits this directly: in a Dev Container or Remote-SSH workspace, the workspace
extension host launches `protos debug`, reads that remote child process stdout,
and connects to the remote host's loopback DAP endpoint.

No UI-host port guessing or cross-host path transport is required for the
baseline.

A future Windows/native launcher can expose the same process stdout readiness
contract without POSIX-only inherited file descriptors or Unix temporary-file
semantics.

## Protos alignment

B-prime was selected because it preserves the project's global design
properties:

- **mechanisms over institutions:** one subcommand and one one-shot record, not a
  daemon/discovery service;
- **pay only for what you use:** ordinary runs create no debugger resources;
- **minimize shared mutable state:** readiness is invocation-local and transient;
- **prefer independence over coordination:** parallel sessions do not share a
  port allocator, registry or rendezvous directory;
- **scale by composition:** future clients can consume the same launch/readiness
  boundary without creating a new debugger universe;
- **generality must be earned:** a readiness file or control-plane service is
  added only when a real independent-process/service requirement exists; and
- **keep platform at the boundary:** the public command and readiness schema do
  not expose GraalVM options or JVM implementation classes.

## Rejected baseline alternatives

### A — raw GraalVM debugger flags

Rejected because it leaks backend vocabulary into the public Protos tooling
contract and weakens future backend replacement.

### B — original stderr variant

Superseded before approval by B-prime after ecosystem review. The command/schema
shape is retained; successful readiness moves to stdout while stderr remains the
diagnostic/failure channel.

### C — human prose readiness

Rejected because punctuation, localization and IPv6 parsing would become an
accidental machine API.

### D — mandatory readiness file

Rejected only as the **baseline F5 contract**. Retained as a valid future
no-config/manual-launch design candidate when parent/child pipe ownership is not
available.

### E — inherited control FD/pipe as public API

Rejected for the baseline because arbitrary inherited FD/handle conventions add
cross-platform process machinery to a contract that ordinary stdout already
satisfies.

### F — caller-selected endpoint

Rejected as the normal path by PLAT018. A future advanced manual/remote override
remains possible after a separate demonstrated requirement.

### G — create/use a long-lived language-service control plane now

Rejected for LM009-D because no already-needed service currently owns that
lifecycle. If LM009-F later establishes such a service independently, it may
become an internal consumer/orchestrator of D060 rather than replacing the
public debugger contract by accident.

## Intentionally deferred

D060 does not decide:

- `protos debug run <logical-entry>` or workspace/package debug syntax;
- endpoint override / `--listen` syntax;
- public remote-network debugging policy;
- manual attach/discovery UX;
- no-config/debug-terminal rendezvous-file syntax;
- stop-on-entry user configuration;
- debugger expression evaluation/mutation;
- authentication/encryption for network-exposed debugging;
- VS Code `launch.json` property names;
- editor UI presentation;
- a language-service-owned debugger control plane;
- debugger distribution packaging mechanics; or
- stronger Stop/terminate behavior than LM009-D/E can prove.

Each substantive future extension crosses the normal decision gate when it
becomes necessary.

## Ratification closure

The project owner explicitly approved **D060 Candidate B-prime** on 2026-09-10
after the extended cross-language debugger audit and the focused comparison of
Candidate D/readiness-file rendezvous against direct child-process readiness.

Result:

```text
D060       RATIFIED — B-prime
PLAT018    RATIFIED — C-prime
LM009-D    PLATFORM + PUBLIC TOOLING DECISIONS RELEASED
LM009-D    IMPLEMENTATION PENDING
LM009-E    BLOCKED_BY_DEPENDENCY: LM009-D
```

This ratification changes no Protos specification, executable runtime,
implementation version, native boundary, public debugger implementation,
Marketplace artifact or license term.
