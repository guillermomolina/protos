# Protos External OS Execution v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-11-21

This document is the primary normative owner of the D229-B1 explicit external
operating-system execution capability and the lifecycle of the OS children it
creates. `PROCESS_IO.md` owns its RootActor bootstrap placement and the Protos
Process custody boundary; `IO_CORE.md` owns cross-cutting Future-returning I/O
operations, commitment, cancellation, error categories and `Closable`
lifecycle; `BYTE_IO.md` owns the byte protocols of child endpoints;
`FUTURES_AND_TASKS.md`, `ACTORS.md` and `PARALLEL_EXECUTION.md` own Future,
Actor and P boundaries. This document does not create a separate Future model,
I/O scheduler, shell, pipe facility or Protos Process creation facility.

An external OS child is **not** a Protos Process (`PROCESS_IO.md` §28). Creating
one creates no Protos execution domain, RootActor, Actor, Node or Cluster
member.

## 1. Capability and authority

External execution is a separately host-provisioned, live resource capability.
The host may grant one concrete execution capability to the initial RootActor
module of a Process, exposing it under the local name `execution`. The grant is
optional and explicit: without it, the slot is **absent**, not present with
`null` as its value. No default grant exists in the standalone CLI or in
standard embedding (see Host grant selection below).

Possession of the grant authorizes precisely the launch operation of that grant
under its host policy. It authorizes no other host capability. A grant may be
narrower than the operating-system credentials of the host.

The standard protocol prototypes of the execution capability, of child
processes and of exit statuses are authority-free. Constructing an ordinary
child of them, obtaining their methods or imitating their structure is not
equivalent to possessing an authentic host-granted capability or a
capability-backed child. No public constructor, importable execution facility,
grant registry, ambient `process.exec` or ambient `execution` binding exists.

`execution` is installed before the first expression of the initial RootActor
SOURCE or MODULE, including the message-service entry. It is local to that
initial module's private execution context, not a prelude binding, module
export, imported module's local slot or automatically inherited Actor state.
The `Process` capability does not expose or recover execution authority.

External execution is independent of Process, Filesystem, Network, Resolver,
Clock and Entropy authority, socket, file and thread authorization,
source/package loading and imports, inspection, foreign interop and providers,
`Actor.spawn`, and the choice between direct and RootActor message-service
bootstrap modes. No combination of those permissions implies an execution
grant, and an execution grant implies none of them. Each Process or embedding
Context requires its own explicit host grant.

**Confinement of a Protos Filesystem is not confinement of an external OS
child.** A child operates under the native authority the host grants it, not
under the Protos `Filesystem`, `Path` or `Network` namespaces. A host that
claims OS-child isolation must establish that isolation separately, or must not
grant execution.

Protos execution authority and the host's effective native permissions are
distinct. A host-runtime permission that merely allows the host to create
native processes (for example, a language-embedding framework's
process-creation option) is neither a Protos grant nor sufficient to provision
one; it may only be a host precondition for a grant the host separately and
explicitly provisions.

**Host grant selection.** The standard host-side selections of the grant are:

- standalone CLI: `--allow-execution`;
- polyglot embedding: the Context option `protos.AllowExecution`.

Both selections are disabled by default. No other existing option, flag or
permission grants execution implicitly: in particular `allowCreateProcess`,
`allowAllAccess`, `allowIO`, native access, FFM, and Filesystem, Network or any
other Protos or host permission do not by themselves provision the Protos
grant. Conversely, selecting the Protos grant never widens the native
permissions the host actually confers; where the host does not permit native
process creation, the selection cannot create that permission. A host that
cannot sustain the custody and cleanup obligations of §8 for a Process or
Context must not provision the grant, even when it is selected.

## 2. Public operations

The public operations of the grant and of its children are exactly:

```text
execution.start(executable, arguments, environment, workingDirectory)
    -> Future<ChildProcess>

child.stdin()       -> ByteWritable + Closable
child.stdout()      -> ByteReadable + Closable
child.stderr()      -> ByteReadable + Closable
child.wait()        -> Future<ExitStatus>
child.terminate()   -> Future
```

These are the definitive public selectors. `start` has exactly four
arguments; there is no overload, optional argument, default argument or other
arity variant of `start`, and omitting an argument is an ordinary arity
failure. No other selector of the grant or of a child is standardized; the
`ExitStatus` selectors are defined in §5.

Every successfully dispatched Future-returning invocation returns a fresh
ordinary Future. Each operation authenticates the receiver's actual grant or
child capability, not merely its shape or delegation ancestry. Ordinary
callable lookup, arity and argument association rules apply before operation
semantics and remain ordinary language failures; there is no new named-argument
or dispatch mechanism.

No shell, shell-command string, interpolation, quoting, expansion, globbing,
command splitting, pipeline, process group, session, PTY, PID inspection,
detach, raw signal, force-kill or Protos Process creation operation is
standardized.

## 3. Launch request

### 3.1 Executable

`executable` is one String, losslessly representable in the host's native
executable-name domain. It names either:

- an absolute native path admitted by the grant's execution policy; or
- a simple executable name, containing no path separator, resolved according
  to a **stable grant-specific host search policy**.

A relative spelling containing a path separator is not portable and is rejected.
The grant's native path and search domain is not the Core `Path`/`Filesystem`
namespace and is not influenced by a Protos `Filesystem` base.

### 3.2 Arguments

The public types of the `start` arguments are:

| Argument | Public type |
|---|---|
| `executable` | `String` |
| `arguments` | `Array` of `String` |
| `environment` | `Map` with `String` keys and `String` values |
| `workingDirectory` | `String` or `null` |

No other type is admitted in these positions; in particular a Protos `Path`
value is not implicitly accepted as a native directory or executable.

`arguments` is an `Array` containing only Strings, each losslessly
representable as a native argument. The child receives exactly those arguments,
in order, by direct executable invocation. Whether and how the host supplies a
native program-name argument in addition is a host-adapter detail and is not
supplied or inferred from `arguments`.

### 3.3 Environment

`environment` is a `Map` that is the **complete, explicit per-child
environment**: a finite mapping of String name/value pairs, each losslessly representable in the
native environment, with names unique under the host's native
environment-name equivalence. An empty `Map` selects an empty environment.
The child inherits nothing implicitly: no entry of the Protos Process
bootstrap environment, of the host's mutable environment or of host secrets is
added. Applications may deliberately construct a complete child environment
from data they are authorized to hold, including entries read from
`process.environment()`.

### 3.4 Working directory

`workingDirectory` is either a String naming a native directory authorized by
the grant, or `null`. `null` selects the stable default directory established
by the grant. The argument is never omitted (§2). There is no
mutable Process-global Protos working directory. Launching a child never changes
`process.environment()`, the Core Filesystem base or any host-global working
directory or environment.

### 3.5 Snapshot

The complete invocation values (executable, every argument, every environment
entry and the working directory) are captured when `start` is invoked, before
any asynchronous backend activity can observe them. Later mutation of a
caller-supplied `Array` or `Map` does not affect the launch.

### 3.6 Validation

Ordinary semantic invalidity is detected before any attributable launch effect:
an argument whose type differs from §3.2 (including a non-String `Array`
element, a non-String `Map` key or value, or a non-String non-`null` working
directory); a String containing NUL or otherwise not
losslessly representable natively; a relative executable spelling containing a
separator; duplicate environment names under native equivalence; or an invalid
native option value. A successfully dispatched `start` then returns a fresh
failed Future carrying a fresh `InvalidIOArgument` occurrence, creates no child
and acquires no endpoint.

Denial by the grant's policy, an unresolvable executable, and every other
backend start failure fail the start Future with a fresh `IOError`, unless a
narrower category is already normatively required by `IO_CORE.md`.

A failed start produces no exit status and publishes no child. A successful
start followed by a nonzero exit is not an I/O failure.

## 4. Start commitment and cancellation

`start` returns its Future without waiting for native creation. A potentially
indefinite synchronous native launch wait never executes inside the invoking
Actor turn. The start Future is an ordinary I/O-operation Future, not an
implicit Protos Task.

The start operation's **commitment frontier** is the earliest instant at which
the implementation can no longer guarantee that cancelling the launch produces
zero OS children. It may precede return from the native process-creation
primitive and always precedes guest-visible publication. Commitment is an
internal operation frontier (`IO_CORE.md` §4), not an additional Future state.
Cancellation and commitment have a single consistent precedence:

- **Cancellation wins.** No child has been or will be created by that
  invocation. The start Future becomes `cancelled`, with no later child,
  endpoint or launch effect.
- **Commitment wins.** Cancellation cannot turn the start Future into a
  no-effect `cancelled` result. When a child exists, the Future resolves to
  exactly one corresponding child capability, published into the live
  originating Actor; the child is registered under Process custody (§8) before
  its guest publication. When it is definitively established that no child exists, the
  Future fails with `IOError`.
- **Uncertain native creation.** The implementation must not publish a
  no-effect cancellation or a definitive failed launch while a possible OS
  child is unmanaged. It retains custody of the attempted launch, establishes
  whether creation occurred, and safely terminates and reaps any possible child
  before classifying the outcome as a failure.

Cancellation and commitment are mutually exclusive precedence outcomes.
Uncertain native creation is an intermediate custody condition, not a
third terminal outcome. While creation remains uncertain, the runtime
retains custody of the attempted launch and must not publish a
cancellation or failure that falsely implies zero unmanaged children.
The uncertainty must be resolved under the custody and cleanup rules
above before the operation is classified as successful or failed.

**After the commitment frontier, a possible child is never lost.** Every
endpoint partially acquired by a failed or uncertain creation remains in
cleanup custody until released.

If the originating Actor terminates after commitment, the residual launch and
its resources remain under Process custody (§8): a result lost because its Actor died does not
discharge the custody obligation. No guest code or callback runs inside the
terminated Actor. A Process-termination cutover may prevent guest
publication but does not erase the host effect or the custody obligation.

## 5. Child identity, wait and exit status

A successful start creates a distinct capability-backed child identity, even
when executable and arguments are identical to another launch or when a native
process identifier is reused. A native process identifier is neither the
child's semantic identity nor an authorization token, and is not exposed.

`ExitStatus` is an immutable, terminal ordinary value with exactly these
public selectors:

```text
status.exited()    -> Boolean
status.exitCode()  -> Integer | null
status.signaled()  -> Boolean
```

A successfully resolved wait yields exactly one of two variants:

- **Verified normal exit:** `exited()` returns `true`, `signaled()` returns
  `false`, and `exitCode()` returns the native exit code as a Protos `Integer`.
- **Verified signal termination:** `exited()` returns `false`, `signaled()`
  returns `true`, and `exitCode()` returns `null`.

There is no third public variant and no public selector exposing a signal
number.

**Conservative classification.** The variant must be supported by reliable
backend information distinguishing normal exit from signal termination. A
numeric code alone never proves signal termination, and numeric conventions
such as `128 + signal` are never used to infer a cause; in particular a POSIX
signal is not inferred from a Windows exit code. Equally, a normal exit is not
asserted when the backend cannot distinguish it from signal termination. When
the terminal category cannot be reliably established, the wait fails with a
fresh `IOError`; the backend never fabricates an `ExitStatus` or invents a
signal. For a correctly observed outcome, a nonzero exit code is an ordinary
**resolved** wait result, never a failed wait.

The **logical terminal outcome** of a child is distinct from the **physical
identity** of any guest object representing it. Every distinct `wait()`
invocation returns a **fresh** Future. All successful waits for the same child
observe the same stable terminal content, including waits issued after the
outcome was first observed. It is not required that every such Future deliver
the same physical `ExitStatus` instance: the identity of objects materialized by
distinct observations is not portably guaranteed beyond the general Protos rules
for ordinary values. An implementation may retain one internal outcome and
materialize an appropriate guest representation of it without changing its
content. The permanence of that outcome belongs to the child and its runtime
custody, not to any particular Future or observer. Cancelling one
pending wait cancels only that observation; it never terminates the child and
never affects another waiter. Exit monitoring and native reaping remain under
runtime custody; repeated waits cannot consume the native exit status twice.
When supervision cannot establish the outcome or its terminal category, the
observation fails through the ordinary failed-Future mechanism with a fresh
`IOError`; no exit status is fabricated and no additional Future state exists.

Observed exit of the child does not imply EOF of its stdout or stderr
endpoints (§6), and `wait()` completion never depends on EOF.

## 6. Standard endpoints

Each launched child has three separate standard byte endpoints:

- stdin: `ByteWritable` and `Closable`;
- stdout: `ByteReadable` and `Closable`;
- stderr: `ByteReadable` and `Closable`, independent of stdout.

Repeated acquisition of the same endpoint of one child denotes the same logical
endpoint. The endpoints follow the `IO_CORE.md` and `BYTE_IO.md` Future, error,
commitment, closure, ordering, Bytes, capacity and cancellation contracts;
`BYTE_IO.md` (External child endpoints) states the endpoint-specific
specializations.

Endpoint transport carries Bytes only. There is no implicit text transcoding,
stdout/stderr merging, whole-output buffering, automatic drain on `wait()`,
unbounded host queue, or loss of bytes produced before a read. Host buffering
for each endpoint is finite and applies backpressure: an undrained endpoint
can block the child, and a child blocked writing to an undrained endpoint can
legitimately prevent `wait()` from completing.

Closing stdin prevents later writes and requests EOF to the child after
already committed output. A write after the child has closed its reading side
may fail as broken pipe (`IOError`). Closing a stdout or stderr endpoint
releases that logical reader; it is not a child termination operation and
does not itself submit a request equivalent to `terminate()` (§7). Endpoint EOF follows
closure of every native writer of that endpoint, not merely exit of the direct
child; descendants retaining native writers may delay EOF.

Closing the reading side may nevertheless have indirect native consequences
for the child, such as a broken pipe, a write error or a signal delivered to the
child program by the host system, and the child may terminate as a result.
Closing an endpoint is therefore not guaranteed to be free of indirect effects
on the child's lifetime, but it is never an explicit termination request.
There is no portable child close operation that terminates the process.

## 7. Termination request

`terminate()` submits an explicit best-effort host termination request and
returns a fresh Future that resolves when the request has been submitted.
Successful submission does not promise exit, immediate force-kill, a particular
signal, shutdown of descendants, or output drain. `wait()` remains the
definitive exit observation. Failure to submit fails the Future with `IOError`.
Repeated requests are permitted and never restart the child. A request after the
child's exit has been observed is a harmless successful no-op. Native signal,
escalation and force semantics are host-adapter details.

## 8. Custody, Actor death and Process termination

**Custodian.** The Protos Process owns each native child, its endpoints and its
supervision until completion and release. Actor-local guest objects hold
operation authority, not sole native cleanup responsibility. A child never
becomes unmanaged because its last guest reference is lost; custody does not
depend on reachability or garbage collection.

**Actor death.** Termination of an Actor requests cancellation of its
uncommitted pending I/O operations under the existing Future rules. It does not
terminate an existing child, does not undo a committed start, and does not
invalidate a separately delegated execution grant held by a live Actor. A
committed start whose result can no longer be published remains under Process
custody; no callback enters the terminated Actor.

Revocation of guest authority and continued host custody are distinct: the
former removes every guest operation; the latter retains only host-side
cleanup and reap responsibility and grants no guest access.

**Process termination cutover.** Termination of the Process (`PROCESS_IO.md`
§28) immediately revokes all guest execution, child and endpoint access from
that Process and prohibits new launches. Children and endpoints still owned
exclusively on its behalf transfer to **host-only cleanup custody**. No guest
close, flush, wait, EOF or Task completion is synthesized, and no guest
callback runs after the terminal boundary.

**Cleanup obligation.** For each child in host-only cleanup custody, the host
initiates a supported best-effort termination when it is still live, closes or
revokes exclusively owned endpoint handles, and retains tracking and native
wait/reap (or equivalent safe final release) responsibility until each known
child is reaped or equivalently released. Cleanup does not rely on guest
reachability, garbage collection, terminated Actor Tasks or Process re-entry.
Host aftermath may be asynchronous, and native errors are reported, if at all,
outside the terminated guest domain.

**Limits.** This is a custody, revocation and attempt-to-reclaim guarantee. It
does not guarantee termination time, a successful kill, a successful flush,
descendant cleanup, or a synchronous wait during Process termination.
Independently provisioned host or other-Process principals are not destroyed
because one Process loses its rights. No public detach or custody-transfer
operation exists.

A backend unable to retain reliable per-child cleanup and reap custody must not
grant execution.

## 9. Isolation and transfer

**Actors.** An execution grant may be **explicitly delegated** to another Actor
of the same Process, through Actor messages or `Actor.spawn` initialization.
The destination receives a non-amplifying Actor-local proxy sharing the same
logical authorization; it is not a mutable alias of the source object, and the
Process capability's delegation contract is a distinct authorization that this
does not merge with. `Actor.spawn` never inherits the grant implicitly, and
imported modules never receive it. Child capabilities (`ChildProcess`) and their live endpoints are not
transferable between Actors; an Actor transfer whose graph reaches them fails
with `NonTransferableValue`. No endpoint delegation, general proxy mechanism or
other delegation or transfer path is defined.

**Isolated parallel execution.** No execution grant, child capability or
endpoint crosses into P. Capturing, supplying or returning them fails with
`NonParallelValue`. Completed authority-free values, such as an `ExitStatus`,
Bytes read from an endpoint and Integers, follow the ordinary value rules.

**Contexts and Processes.** Separate Protos Processes and embedding Contexts
receive independently authorized grants. A child created through one Process
or Context confers no control to another, and closing or terminating one never
terminates children owned by another. A multi-Context host must not terminate
unrelated Contexts or global host services as a substitute for isolating one
terminated Process.

## 10. Platform, cost and deferrals

Linux and other POSIX systems, macOS/BSD and Windows may implement creation,
observation and termination differently. No POSIX signal, process-group,
session, PTY or job-control semantics, and no Windows job-object semantics, are
portable Core. The native mechanism used by an implementation is not Protos
semantics and is not evidence of sandboxing.

The ability to distinguish normal exit from signal termination is
backend-specific. A backend reports a signal termination only where the
platform supplies verifiable evidence of it, and reports a normal exit only
where it can exclude signal termination; otherwise `wait()` fails with
`IOError` (§5). Portability is an obligation to conform or to reject safely,
never permission to fabricate a result.

Host I/O completion machinery never runs ordinary guest Protos code; results are
published in the legitimate owning Actor. A Process without a granted and used
execution capability incurs no child supervision, native pipe or
synchronization cost.

This revision does not define: shell grammar or quoting, a command-string run
operation, globbing, a pipeline or general pipe facility, raw signals, PID
enumeration, force-kill, process trees or groups, PTY/TTY, interactive jobs,
daemon detach, cross-Process child authority transfer, distributed execution,
a public signal number, or an OS sandbox promise.
