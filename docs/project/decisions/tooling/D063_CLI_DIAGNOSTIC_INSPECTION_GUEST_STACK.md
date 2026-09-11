# D063 — CLI diagnostic inspection and guest stack presentation contract

Status: **RATIFIED**

Allocated: **2026-09-10**

Explicit project-owner approval: **2026-09-10**

Nature: durable implementation-independent CLI/tooling presentation decision

Triggered by: `CLI008-A` / GitHub #312

Decision issue: GitHub #314

Primary consumers: CLI008-B value inspection/pretty rendering, CLI008-C uncaught
guest stack diagnostics, future CLI/REPL diagnostic surfaces

Normative language effect: **none**. D063 defines diagnostic presentation and the
separation between diagnostic inspection, ordinary program output and explicit
serialization. It does not add a nominal type system, a guest `inspect` protocol,
Error slots, serialization semantics, runtime-visible stack objects, or new Protos
value semantics. Observable Protos semantics remain owned by the applicable
material under `spec/`.

## Problem

The current CLI/REPL needs richer information than ordinary program output.

Today `ProtosValueRenderer` serves several different purposes. Scalars and core
collections already have useful source-like rendering, while ordinary objects and
several semantic value families collapse to generic placeholders. The same renderer
also participates in ordinary `print(...)` output for non-String values.

Enriching that shared renderer directly would therefore make one representation try
to satisfy three different contracts:

1. human-oriented program output;
2. interactive diagnostic inspection; and
3. machine-oriented interchange/serialization.

Those contracts have different stability, ambiguity, safety and round-trip
requirements. D063 separates them before CLI008 changes implementation.

The same audit also found that a failed Protos Error reaches the CLI without enough
information to reconstruct its original guest stack. A durable public presentation
contract is still selectable now, but the physical capture mechanism must not be
bound to the execution shape being replaced by PERF006 C-prime continuations.

## Prior art and design evidence

The selected direction follows the recurring architectural separation present in
mature dynamic-language and tooling ecosystems without copying any one surface as
Protos semantics.

### Python

Python distinguishes information-rich diagnostic representation from ordinary
human-facing string/print presentation. Exception values and tracebacks are separate
objects/concepts, and traceback summaries can avoid retaining full live frame graphs.

Relevant lesson for Protos: diagnostic representation and program output need not be
the same contract, and execution-trace information need not mutate the exception
value.

### Ruby

Ruby separates `inspect`-style debugging representation from normal output and keeps
backtrace locations as structured diagnostic information.

Relevant lesson for Protos: richer inspection can remain tooling-oriented rather than
becoming the ordinary printed form of every value.

### Smalltalk / Pharo

Smalltalk-family systems distinguish compact display/printing from richer Inspector
exploration. Object-oriented introspection does not require every ordinary output
operation to recursively dump object structure.

Relevant lesson for Protos: a prototype/object-centric REPL benefits from a dedicated
inspection surface.

### Node.js

Node's diagnostic inspector is explicitly debugging output rather than a stable
machine format. It bounds depth/items/strings, handles cycles and permits compact or
multiline presentation. Stack presentation is likewise bounded.

Relevant lesson for Protos: diagnostic richness must be bounded and must not become
an accidental serialization protocol.

### Truffle

Truffle provides guest-oriented source/frame facilities such as source sections,
debug frames and guest stack support. Those are possible implementation mechanisms,
not public Protos CLI authority.

Relevant lesson for Protos: the CLI contract should describe guest facts while hiding
JVM/Truffle/scheduler implementation frames and while allowing the physical backend
to evolve.

## Alternatives evaluated

### Candidate A — enrich the existing shared renderer and show `type`

Rejected.

This would silently change ordinary program stdout, introduce a misleading nominal
"type" vocabulary into a prototype language, and couple interactive diagnostics to a
representation that must also serve non-interactive output.

### Candidate B — separate non-evaluating diagnostic inspection from program output

**Selected.**

The REPL/CLI obtains a dedicated bounded diagnostic representation. Ordinary
`print(...)` remains a separate program-output facility.

This permits the inspector to be informative without making diagnostic punctuation,
cycle markers, depth truncation or object detail into the contract of ordinary
stdout.

### Candidate C — add a guest-visible `inspect` / `repr` protocol now

Rejected for the CLI008 baseline.

Allowing guest code to customize inspection would cross from tooling into language or
Standard Library semantics and would let inspection perform effects, fail, suspend or
require authority. A future guest-visible customization protocol requires its own
decision if real use cases justify it.

### Candidate D — make Truffle debugger/interop display the public authority

Rejected.

Host tooling may provide reusable implementation projection, but it must not define
the portable public CLI contract or leak Java/Truffle identities.

## Ratified decision — Candidate B

### 1. `inspect != print != serialize`

D063 explicitly separates three responsibilities.

```text
                         Protos value
                              |
             +----------------+----------------+
             |                |                |
             v                v                v
       program output      diagnostics      interchange
          print(...)        REPL/CLI        serializer
             |             inspect/pretty       |
             |                |                 |
      human-facing text   understand value   stable data format
```

The three surfaces may share low-level neutral projection machinery internally, but
their public contracts are independent.

#### Diagnostic inspection

Diagnostic inspection exists to help a person understand the value currently being
observed.

Where a value has a natural literal/source form, diagnostics should prefer a
source-like representation when doing so is safe and informative:

```text
42
true
"hello"
[1, 2, 3]
```

Strings remain quoted and escaped in diagnostic inspection so that the REPL visibly
distinguishes the value from ordinary terminal text.

Source-like rendering is a preference, **not a universal round-trip guarantee**.
Objects, Closures, Futures, Actors, Processes, resources, cyclic graphs and
identity-sensitive structures may not have a faithful executable literal form.
Diagnostic output must not distort the language merely to make every rendered value
re-evaluable.

#### Program output

`print(...)` remains ordinary program output.

For example, printing a String emits its text rather than its quoted diagnostic form.
Improving REPL inspection must not silently change this behavior.

D063 does not require every non-String value to acquire a new stable textual contract.
Any later change to ordinary program output that is externally observable must be
evaluated under its own semantic/tooling authority.

#### Serialization / machine interchange

Neither the REPL inspector nor `print(...)` is a machine interchange protocol.

When Protos needs reliable program-to-program data, it must use an explicit
serialization/data mechanism with its own escaping, schema/format, compatibility,
error and round-trip rules, such as an applicable JSON or future serialization API.

A consumer must not be required to parse pretty diagnostic text in order to recover a
value.

### 2. No nominal `type` concept

The CLI must not introduce a new `type` property or parallel nominal classification
system.

Protos values are governed by prototypes/delegation and existing semantic value
families. Diagnostics may use a CLI-only `kind`/family label when needed to
disambiguate otherwise opaque values, for example conceptually:

```text
Object { ... }
<closure>
<future>
<actor>
<process>
```

Such labels:

- are diagnostic metadata/presentation only;
- do not participate in lookup, delegation, equality or other guest semantics;
- are not exposed as a new guest slot or protocol;
- do not expose Java class names; and
- must not fabricate a more specific semantic category than the runtime actually
  knows.

### 3. Ordinary-object inspection is local-slot first

The default object view shows own/local guest-visible slots, not a recursively
flattened delegation universe.

Conceptually:

```text
Object {
    name: "Alice",
    age: 42,
    address: Object {
        city: "Tarragona"
    }
}
```

The exact punctuation, indentation and width policy remain CLI presentation details.

Delegation/prototype exploration belongs to an explicit deeper inspection view rather
than every ordinary evaluation result.

This keeps common objects readable and prevents a small value from implicitly dumping
large portions of Core or unrelated prototype graphs.

### 4. Inspection is non-evaluating

Baseline diagnostic inspection must not invoke arbitrary guest methods or a
guest-defined `inspect`/`toString`-like protocol.

Looking at a value must not, merely because of diagnostic rendering:

- perform I/O;
- mutate program state;
- suspend;
- acquire additional authority;
- signal a guest Error;
- execute user callbacks; or
- trigger resource activity.

Inspection reads only already-available guest-visible state and approved
implementation-neutral projections.

### 5. Inspection is bounded and deterministic

Recursive diagnostic rendering must have deterministic finite limits for at least:

- nesting depth;
- collection/object item count;
- String/text length;
- total output size; and
- cycle/reference expansion.

Cycles and truncation must be represented explicitly rather than recursing without
bound.

Exact default numeric limits and punctuation are intentionally deferred to CLI008-B
unless selecting them would create a new substantive compatibility promise.

### 6. Capability/resource values remain safe

Capability-backed values must not expose host-private details merely for a richer
display.

In particular, diagnostics must not reveal otherwise unavailable JVM identities,
native handles, private host paths, implementation ports, scheduler objects or other
authority-bearing implementation details.

Semantically public information may be shown when it is already legitimately visible
to the guest/tool contract.

## Guest Error and stack presentation

### Alternatives

#### S1 — Java/JVM stack

Rejected.

Normal Protos diagnostics must not display Java, Truffle, scheduler or implementation
frames as though they were guest call frames.

#### S2 — final source location only

Rejected as the baseline target.

A single location is insufficient for nested calls and real debugging.

#### S3 — structured guest trace owned by the transfer/failure occurrence

**Selected.**

A top-level uncaught Error should be able to present a bounded guest-only execution
trace, conceptually:

```text
SlotNotFound
  at <closure> (src/orders.protos:42:17)
  at processOrder (src/orders.protos:18:9)
  at <module> (src/main.protos:7:1)
```

The exact wording/punctuation may evolve compatibly. The semantic requirements are
the guest-only frame facts and the absence of fabricated host detail.

### Trace ownership

The diagnostic trace belongs to the **Error transfer/failure occurrence**, not to a
slot stored on the Error object.

The same Error identity may be signalled more than once:

```text
Error E
   |
   +-- signal occurrence #1 -> Trace A
   |
   +-- signal occurrence #2 -> Trace B
```

Mutating `E` to carry one stack would make the trace ambiguous and would change Error
state merely because the Error was transferred.

Therefore:

- re-signalling the same Error may produce a different trace;
- the Error object's identity and guest-visible slots remain unchanged;
- a Future/Task failure may retain diagnostic occurrence metadata separately from the
  Error value; and
- diagnostic metadata does not become a new guest-visible Error taxonomy.

### Guest frame contents

A frame may include only facts genuinely known to Protos/tooling, such as:

- known executable/Closure/module label;
- canonical source/module identity;
- source line; and
- source column.

When the Closure/function name is not genuinely known, the CLI uses an anonymous
guest label such as `<closure>` rather than inventing a Java/Truffle-derived name.

Normal guest diagnostics must exclude:

- JVM frames;
- Truffle internal frames;
- scheduler/carrier frames;
- Java implementation class names;
- synthetic host helper names; and
- unrelated runtime plumbing.

Internal runtime failures remain distinguishable from ordinary Protos Error signals;
D063 does not require them to masquerade as guest Errors.

## Futures, Tasks, Actors and Processes

### Same Task: preserve the logical stack

An ordinary `Future.value()` suspension/resume inside one Task must not conceptually
erase the logical guest call stack once the C-prime continuation backend exposes the
required logical-location authority.

Conceptually:

```text
main
  -> a
     -> b
        -> Future.value()
           [suspend]
           [resume]
        -> c
           ERROR
```

The resulting guest trace may still represent the real logical call chain through
`c`, `b`, `a`, `main`.

This is a presentation/diagnostic requirement, not a mandate for a particular
physical JVM stack representation.

### Async origins

D063 does not invent one continuous call stack across asynchronous boundaries.

Optional `Async origin:` or equivalent segments are valid only when the runtime has
retained trustworthy provenance for that relationship.

If provenance is unavailable, the CLI prints the real local guest stack and nothing
fabricated.

### Actor / Process boundaries

Actor and Process boundaries are semantic boundaries.

Diagnostics must not pretend that independently scheduled or isolated execution
contexts form a single synchronous stack unless an independently authoritative
causal relationship is actually retained.

Any future cross-boundary provenance model that materially changes lifetime, memory
cost, authority or causal semantics must cross the applicable decision gate.

## Performance and scalability requirements

The diagnostic architecture must preserve the project rule to pay only for what is
used.

Required properties:

- no inspection graph walk when no value is being diagnostically rendered;
- no always-on snapshot of every activation merely because stack diagnostics exist;
- no global registry of live diagnostic frame graphs;
- no strong retention of complete live activation graphs solely for later formatting;
- bounded trace depth/output;
- bounded retained async provenance if such provenance is later enabled;
- occurrence/task/relationship-local diagnostic state rather than global mutable
  causality state; and
- ordinary successful execution must not pay a large diagnostic allocation or
  synchronization tax.

Handled Errors also must not incur an avoidable heavyweight stack-retention policy
merely to support top-level uncaught diagnostics.

## PERF006 / C-prime interaction

D063 ratifies the **public diagnostic contract**, not the host-specific capture
mechanism.

PERF006 is replacing replay-era execution with C-prime Bytecode DSL continuations.
Its B4 work owns Error/unwind migration and B5 owns source/instrumentation/debugger
compatibility.

CLI008-C must therefore not bind the durable stack contract to the current physical
AST/JVM stack shape.

Expected dependency:

```text
D063 public presentation contract
            |
            +--> CLI008-B diagnostic inspector implementation
            |
            +--> CLI008-C stack presentation
                     |
                     +--> consume PERF006 B4/B5 logical guest-location authority
                          OR
                          open a PLATxxx decision if a new durable host-specific
                          stack-capture architecture is still required
```

A later PLAT decision is necessary only if implementation work exposes a genuinely
new durable Truffle/JVM architecture choice. D063 itself does not select
`TruffleStackTrace`, `AbstractTruffleException`, continuation metadata or another
physical transport.

## Compatibility contract

The following distinctions are durable:

- diagnostic inspection is not ordinary `print(...)`;
- neither diagnostic inspection nor `print(...)` is serialization;
- diagnostics do not introduce nominal `type`;
- source-like rendering is preferred for natural literal values but general
  inspection is not promised to round-trip;
- object inspection is local-slot-first by default;
- baseline inspection does not execute arbitrary guest code;
- recursive output is bounded and deterministic;
- guest Error stacks contain guest frames only;
- transfer/failure trace metadata does not mutate the Error object;
- async origins are never fabricated; and
- host/runtime implementation detail is not public CLI diagnostic authority.

The following remain evolvable CLI presentation policy unless separately promoted to
a stronger contract:

- exact braces/punctuation;
- line width;
- indentation;
- exact truncation marker spelling;
- exact default numeric depth/item/string limits;
- colors;
- source snippets/carets; and
- explicit deep-inspection command spelling.

## Intentionally deferred decisions

D063 does **not** select:

- an exact `:inspect`/CLI command syntax;
- exact pretty-print line width;
- exact numeric depth/item/string/output limits;
- a guest-visible customizable `inspect`/`repr` protocol;
- source snippets or caret diagnostics;
- color policy;
- Error message/payload conventions;
- a general serialization protocol or format;
- async-origin retention mechanics;
- cross-Actor/Process causal-provenance semantics;
- Truffle/JVM stack-capture representation; or
- implementation-specific continuation/debug metadata layout.

Each deferred item must cross the appropriate future decision gate if implementation
cannot remain mechanical under the ratified contract.

## CLI008 consequence

D063 resolves the substantive public-presentation question discovered by CLI008-A.

Result:

```text
D063       RATIFIED — Candidate B + S3
CLI008-A   CLOSED
CLI008-B   READY
CLI008-C   BLOCKED_BY_DEPENDENCIES — wait for/reuse PERF006 B4/B5 logical
                                     Error/unwind + source/debugger authority;
                                     open PLATxxx only if still required
CLI008-D   NOT_STARTED
```

No CLI008-B or CLI008-C implementation is included in this ratification.

## Ratification closure

The project owner explicitly approved **Candidate B + S3** on 2026-09-10 and further
clarified that the durable contract must separate:

```text
inspect != print != serialize
```

with source-like diagnostics for naturally literal values but no universal
re-evaluable/round-trip requirement.

That approval resolves D063. This record preserves the decision without changing
Protos language semantics or selecting the physical Truffle/JVM stack mechanism.
