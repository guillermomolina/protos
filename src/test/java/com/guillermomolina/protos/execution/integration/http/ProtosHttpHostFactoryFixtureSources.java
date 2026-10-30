/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

/**
 * Protos sources of {@link ProtosHttpHostFactoryIntegrationTest}: the application service Actor
 * module {@code app:service}, the factory module {@code app:factory} and the program whose
 * top-level bindings are the fixture entry points, plus the D225-B1 RootActor service entry and
 * its Worker factory.
 */
final class ProtosHttpHostFactoryFixtureSources {
    private ProtosHttpHostFactoryFixtureSources() {}

    static final String SERVICE =
            """
            // A Future pending until opened; opening twice is a no-op.
            latch: () => {
                blocker: (() => {
                    Actor.current().termination().value()
                }).future()
                opened: (() => {
                    Error.handle(
                        () => {
                            blocker.value()
                        },
                        (error) => {
                            error
                        }
                    )
                    null
                }).future()
                gate: {
                    future: opened
                    open: () => {
                        blocker.cancel()
                    }
                }
                gate
            }

            // An application service Actor. Its lifetime belongs to the program.
            start: () => {
                served: 0
                waiting: 0
                released: latch()
                // A Worker that registered itself from its factory.
                registered: null
                behavior: {
                    publicProjects: () => {
                        served = served + 1
                        "protos,website"
                    }
                    isSelf: (reference) => {
                        reference === Actor.current()
                    }
                    // Replies only once released: a late reply.
                    slow: () => {
                        waiting = waiting + 1
                        released.future.value()
                        "late"
                    }
                    waitingCount: () => {
                        waiting
                    }
                    release: () => {
                        released.open()
                        null
                    }
                    servedCount: () => {
                        served
                    }
                    register: (worker) => {
                        registered = worker
                        null
                    }
                    // Answers what an ordinary request transferred.
                    echo: (payload) => {
                        payload.state.label
                    }
                    accept: (value) => {
                        true
                    }
                    // Sends the registered Worker a second "serve" and
                    // answers its reply.
                    reserve: (payload) => {
                        registered.request("serve", payload).value()
                    }
                }
                behavior
            }
            """;

    /**
     * D225-B1: an entry module started through the RootActor service bootstrap. The RootActor
     * itself is the application service; it hands a single-member GroupRef to itself to HTTP
     * Workers as their factory initialization, naming the factory by its exact {@code app:}
     * identity because each Worker imports it inside its own Actor. Host startup awaits, so it runs in an ordinary
     * message turn after READY; every outcome is reported on the Process standard output.
     */
    static final String ROOT_SERVICE =
            """
            http: import("std:http/Host")
            IpAddress: import("std:network/IpAddresses").IpAddress
            IpEndpoints: import("std:network/IpEndpoints")
            IpEndpoint: IpEndpoints.IpEndpoint
            ProcessStreams: import("std:io/ProcessStreams")
            out: ProcessStreams.stdoutWriter(process)
            loopback: IpAddress(4, 2130706433)
            hits: 0
            host: null
            // Names an Error by its prototype, with its kind when present.
            describeError: (error) => {
                names: ["HostError", "NonTransferableValue", "InvalidIOArgument", "IOLifecycleError", "SlotNotFound", "Error"]
                prototypes: [http.HostError, NonTransferableValue, InvalidIOArgument, IOLifecycleError, SlotNotFound, Error]
                found: "unknown"
                index: 0
                while((found == "unknown") && (index < names.size())) {
                    prototype: prototypes[index]
                    name: names[index]
                    Error.handle(
                        () => {
                            prototype.handle(
                                () => {
                                    error.signal()
                                },
                                (matched) => {
                                    found = name
                                    matched
                                }
                            )
                        },
                        (unmatched) => {
                            unmatched
                        }
                    )
                    index = index + 1
                }
                if(error.hasSlot("kind")) {
                    found = found + " kind=" + error.kind
                }
                found
            }
            service: () => {
                self: Actor.group(Actor.current())
                Actor.current().send("start", self)
                behavior: {
                    // A startup Error is reported, not fatal, so a failing test
                    // names it instead of only timing out.
                    start: (group) => {
                        phase: "listen"
                        Error.handle(
                            () => {
                                request: {
                                    ipVersion: 4
                                    address: loopback
                                    port: null
                                }
                                listener: network.listenTcp(request).value()
                                options: {
                                    workers: 2
                                    maxConnections: 4
                                }
                                dependencies: {
                                    service: group
                                }
                                phase = "construction"
                                host = http.Host.owningWithFactory(listener, "app:root-service-factory", "make", dependencies, options)
                                phase = "host.ready()"
                                host.ready().value()
                                phase = "report"
                                out.writeLine("listening " + IpEndpoints.format(IpEndpoint(loopback, listener.localPort()))).value()
                            },
                            (error) => {
                                out.writeLine("start-failed " + describeError(error) + " in phase " + phase).value()
                                error
                            }
                        )
                        null
                    }
                    hit: () => {
                        hits = hits + 1
                        hits
                    }
                    shutdown: (mode) => {
                        host.shutdown(mode).value()
                        out.writeLine("host " + mode).value()
                        null
                    }
                    // Reports its own dispatch before failing fatally.
                    fail: () => {
                        out.writeLine("failing").value()
                        Error().signal()
                    }
                }
                behavior
            }
            """;

    /**
     * The Worker-side factory of {@link #ROOT_SERVICE}, supplied as {@code
     * app:root-service-factory} through the service session's application module catalog. Each
     * Worker imports its own instance, so {@code factoryRuns} counts that Worker's run only.
     */
    static final String ROOT_SERVICE_FACTORY =
            """
            factoryRuns: 0
            ambient: context.hasSlot("process") || context.hasSlot("filesystem") || context.hasSlot("network") || context.hasSlot("resolver")
            flag: (condition) => {
                text: "0"
                if(condition) {
                    text = "1"
                }
                text
            }
            make: (dependencies) => {
                factoryRuns = factoryRuns + 1
                service: dependencies.service
                (request) => {
                    target: Encoding.Latin1.decode(request.target)
                    body: Encoding.Latin1.encode("")
                    if(target == "/hit") {
                        body.add(48 + service.request("hit").value())
                    }
                    if(target == "/isolation") {
                        noStop: Error.handle(
                            () => {
                                service.stop()
                                false
                            },
                            (error) => { true }
                        )
                        body = Encoding.Latin1.encode(flag(factoryRuns == 1) + flag(!ambient) + flag(noStop))
                    }
                    if(target == "/drain") {
                        service.send("shutdown", "drain")
                    }
                    if(target == "/abort") {
                        service.send("shutdown", "abort")
                    }
                    if(target == "/fail") {
                        service.send("fail")
                    }
                    response: {
                        status: 200
                        headers: []
                        body: body
                    }
                    response
                }
            }
            """;

    static final String FACTORY =
            """
            // Module-instance state: the constructing Actor's instance and the
            // instance of each Worker are distinct.
            factoryRuns: 0
            creatorInstance: false
            active: 0
            peak: 0
            // An imported module receives no bootstrap-local authority.
            ambient: context.hasSlot("process") || context.hasSlot("filesystem") || context.hasSlot("network") || context.hasSlot("resolver")

            markCreator: () => {
                creatorInstance = true
            }
            flag: (condition) => {
                text: "0"
                if(condition) {
                    text = "1"
                }
                text
            }
            latin1: (text) => {
                Encoding.Latin1.encode(text)
            }
            digit: (count) => {
                bytes: latin1("")
                bytes.add(48 + count)
                bytes
            }
            respond: (body) => {
                response: {
                    status: 200
                    headers: []
                    body: body
                }
                response
            }

            make: (dependencies) => {
                factoryRuns = factoryRuns + 1
                builder: Actor.current()
                projects: dependencies.projects
                creator: dependencies.creator
                state: dependencies.state
                (request) => {
                    target: Encoding.Latin1.decode(request.target)
                    active = active + 1
                    if(active > peak) {
                        peak = active
                    }
                    if(request.method == "POST") {
                        request.body.read(1).value()
                    }
                    active = active - 1
                    body: digit(peak)
                    if(target == "/factory") {
                        body = latin1(flag(factoryRuns == 1) + flag(Actor.current() === builder) + flag(builder !== creator) + flag(!creatorInstance) + flag(!ambient))
                    }
                    if(target == "/payload") {
                        state.hits = state.hits + 1
                        intact: (state.label == "original") && (state.first === state.second) && (state.first.tag == "shared")
                        intact = intact && (state.items[1] == "b") && (state.numbers[1] == 5)
                        intact = intact && (state.nested.deeper.enabled == true) && (state.nested.deeper.nothing === null)
                        body = latin1(flag(intact))
                        body.add(48 + state.hits)
                    }
                    if(target == "/projects") {
                        body = latin1(projects.request("publicProjects").value())
                    }
                    if(target == "/self") {
                        body = latin1(flag(projects.request("isSelf", projects).value()))
                    }
                    if(target == "/slow") {
                        body = latin1(projects.request("slow").value())
                    }
                    respond(body)
                }
            }
            // Suspends until the service has registered this Worker.
            registering: (dependencies) => {
                dependencies.projects.request("register", Actor.current()).value()
                make(dependencies)
            }
            // Suspends on a service reply that comes only once released.
            suspended: (dependencies) => {
                dependencies.projects.request("slow").value()
                make(dependencies)
            }
            failing: (dependencies) => {
                Error().signal()
            }
            failingAfterSuspension: (dependencies) => {
                dependencies.projects.request("publicProjects").value()
                Error().signal()
            }
            notCallableResult: (dependencies) => {
                result: {
                    call: 42
                }
                result
            }
            // The Future itself is the result: it is never awaited for a handler.
            futureResult: (dependencies) => {
                (() => {
                    (request) => {
                        respond(latin1("adopted"))
                    }
                }).future()
            }
            notCallable: {
                call: 42
            }
            """;

    static final String PROGRAM =
            """
            http: import("std:http/Host")
            IpAddress: import("std:network/IpAddresses").IpAddress
            site: import("app:factory")
            loopback: IpAddress(4, 2130706433)
            service: Actor.spawn("app:service", "start")
            listener: null
            host: null
            state: null
            otherListener: null
            other: null
            // The phase reached by the last entry point, reported with any
            // escaped guest Error.
            phase: "idle"

            // Names an escaped Error by its prototype, with its kind when present.
            describeError: (error) => {
                names: ["HostError", "NonTransferableValue", "RequestOutcomeUncertain", "InvalidIOArgument", "IOLifecycleError", "SlotNotFound", "Cancelled", "Error"]
                prototypes: [http.HostError, NonTransferableValue, RequestOutcomeUncertain, InvalidIOArgument, IOLifecycleError, SlotNotFound, Cancelled, Error]
                found: "unknown"
                index: 0
                while((found == "unknown") && (index < names.size())) {
                    prototype: prototypes[index]
                    name: names[index]
                    // A non-matching prototype lets the Error reach the outer
                    // handler, which only discards it.
                    Error.handle(
                        () => {
                            prototype.handle(
                                () => {
                                    error.signal()
                                },
                                (matched) => {
                                    found = name
                                    matched
                                }
                            )
                        },
                        (unmatched) => {
                            unmatched
                        }
                    )
                    index = index + 1
                }
                if(error.hasSlot("kind")) {
                    found = found + " kind=" + error.kind
                }
                found
            }
            // This program's module context, whose top-level bindings are the
            // fixture entry points.
            program: context
            // Runs the entry point named name; an escaped Error is answered as
            // a "guest-error:" String naming it and the phase reached, which
            // the fixture turns into a descriptive test failure. The embedding
            // accepts only Protos values, integers and Strings as arguments, so
            // the entry point is named rather than passed.
            diagnose: (name, ...supplied) => {
                result: null
                Error.handle(
                    () => {
                        entry: program.slotValue(name)
                        result = entry(...supplied)
                    },
                    (error) => {
                        result = "guest-error: " + describeError(error) + " in phase " + phase
                        error
                    }
                )
                result
            }
            // Names the first value of a fresh initialization, or of a few
            // smaller shapes, that an ordinary request cannot transfer.
            firstNonTransferable: () => {
                payload: initialization()
                nullHolder: {
                    nothing: null
                }
                booleanHolder: {
                    enabled: true
                }
                candidates: [
                    ["projects", payload.projects],
                    ["creator", payload.creator],
                    ["state.label", state.label],
                    ["state.hits", state.hits],
                    ["state.items", state.items],
                    ["state.numbers", state.numbers],
                    ["state.first", state.first],
                    ["object with a Boolean slot", booleanHolder],
                    ["object with a null slot", nullHolder],
                    ["state.nested.deeper", state.nested.deeper],
                    ["state.nested", state.nested],
                    ["state", state],
                    ["initialization", payload]
                ]
                found: "none"
                candidates.each((candidate) => {
                    if(found == "none") {
                        Error.handle(
                            () => {
                                service.request("accept", candidate[1]).value()
                            },
                            (error) => {
                                found = candidate[0] + " (" + describeError(error) + ")"
                                error
                            }
                        )
                    }
                })
                found
            }

            listen: () => {
                request: {
                    ipVersion: 4
                    address: loopback
                    port: null
                }
                network.listenTcp(request).value()
            }
            openListener: () => {
                listener = listen()
                listener.localPort()
            }
            openOther: () => {
                otherListener = listen()
                otherListener.localPort()
            }
            // A fresh initialization: nested ordinary data with one alias, the
            // creator's ActorRef and the application service's ActorRef. It
            // holds no standard Bytes: an ordinary Actor request currently
            // rejects those with NonTransferableValue whatever the receiver,
            // which is outside HTTP.md §15.13.
            initialization: () => {
                shared: {
                    tag: "shared"
                }
                deeper: {
                    enabled: true
                    nothing: null
                }
                nested: {
                    deeper: deeper
                }
                state = {
                    label: "original"
                    hits: 0
                    items: ["a", "b"]
                    numbers: [3, 5]
                    first: shared
                    second: shared
                    nested: nested
                }
                dependencies: {
                    projects: service
                    creator: Actor.current()
                    state: state
                }
                dependencies
            }
            start: (binding, workerCount, connectionLimit) => {
                phase = "construction"
                site.markCreator()
                options: {
                    workers: workerCount
                    maxConnections: connectionLimit
                }
                host = http.Host.owningWithFactory(listener, "app:factory", binding, initialization(), options)
                // Mutations after construction never reach a Worker's copy.
                state.label = "mutated"
                state.hits = 7
                state.first.tag = "mutated"
                phase = "host.ready()"
                ready: host.ready().value() === host
                phase = "running"
                ready
            }
            startOther: () => {
                phase = "other construction"
                other = http.Host.owningWithFactory(otherListener, "app:factory", "make", initialization(), null)
                phase = "other host.ready()"
                ready: other.ready().value() === other
                phase = "running"
                ready
            }
            drain: () => {
                phase = "shutdown(drain)"
                host.shutdown("drain").value() === host
            }
            abort: () => {
                phase = "shutdown(abort)"
                host.shutdown("abort").value() === host
            }
            drainOther: () => {
                phase = "other shutdown(drain)"
                other.shutdown("drain").value() === other
            }
            pendingDrain: null
            beginDrain: () => {
                pendingDrain = host.shutdown("drain")
                pendingDrain !== null
            }
            finishDrain: () => {
                pendingDrain.value() === host
            }
            terminated: () => {
                host.termination().value() === host
            }
            closed: (candidate) => {
                IOLifecycleError.handle(
                    () => {
                        candidate.accept().value()
                        false
                    },
                    (error) => { true }
                )
            }
            listenerClosed: () => {
                closed(listener)
            }
            // Every observer fails with a fresh HostError "startup".
            hostFailureKinds: () => {
                kinds: []
                errors: []
                record: (future) => {
                    http.HostError.handle(
                        () => {
                            future.value()
                        },
                        (error) => {
                            kinds = Array(...kinds, error.kind)
                            errors = Array(...errors, error)
                            error
                        }
                    )
                }
                record(host.ready())
                if(errors.size() == 0) {
                    host.shutdown("abort")
                }
                record(host.termination())
                record(host.shutdown("drain"))
                fresh: (errors.size() == 3) && (errors[0] !== errors[1]) && (errors[1] !== errors[2])
                fresh && (kinds[0] == "startup") && (kinds[1] == "startup") && (kinds[2] == "startup")
            }
            startFailing: (moduleSpecifier, binding) => {
                listener = listen()
                options: {
                    workers: 3
                    maxConnections: 3
                }
                host = http.Host.owningWithFactory(listener, moduleSpecifier, binding, initialization(), options)
                hostFailureKinds() && closed(listener)
            }
            // A valid construction whose initialization cannot cross an
            // ordinary Actor boundary: startup fails, and the host still
            // cleans up and closes its listener.
            rejectedInitialization: (kind) => {
                listener = listen()
                extra: null
                hostile: null
                if(kind == "closure") {
                    holder: {
                        run: () => { 1 }
                    }
                    hostile = {
                        deep: [holder]
                    }
                }
                if(kind == "future") {
                    hostile = {
                        pending: (() => { 1 }).future()
                    }
                }
                if(kind == "network") {
                    hostile = {
                        authority: network
                    }
                }
                if(kind == "listener") {
                    extra = listen()
                    hostile = {
                        resource: extra
                    }
                }
                if(kind == "acceptor") {
                    extra = listen()
                    hostile = {
                        resource: extra.acceptCapability()
                    }
                }
                options: {
                    workers: 2
                    maxConnections: 2
                }
                host = http.Host.owningWithFactory(listener, "app:factory", "make", hostile, options)
                rejected: hostFailureKinds() && closed(listener)
                if(extra !== null) {
                    // The caller keeps custody of its own other listener and
                    // closes it itself.
                    extra.close().value()
                }
                rejected
            }
            // A second "serve" reaches the Worker through the service.
            reserve: () => {
                service.request("reserve", initialization()).value() === null
            }
            // Constructs without waiting for readiness.
            startSuspended: (binding) => {
                host = http.Host.owningWithFactory(listener, "app:factory", binding, initialization(), null)
                host !== null
            }
            // Abort while the factory is suspended: the shutdown succeeds and
            // readiness fails with "startup" (HTTP.md §§15.7, 15.13).
            abortWhileConstructing: () => {
                aborted: host.shutdown("abort").value() === host
                notReady: http.HostError.handle(
                    () => {
                        host.ready().value()
                        false
                    },
                    (error) => { error.kind == "startup" }
                )
                aborted && notReady
            }
            servedCount: () => {
                service.request("servedCount").value()
            }
            // HTTP.md §15.13 without HostLifecycle: the initialization crosses
            // an ordinary request, and a factory Worker spawned exactly as the
            // host spawns it builds its handler, answers "ready" with true, and
            // answers "drained" to its first "serve" after "abort". Answers
            // "ok", or the stage that failed and the Error observed there.
            probeFactoryWorker: (binding) => {
                stage: "ordinary request transfer"
                outcome: "ok"
                probeListener: listen()
                worker: null
                // The first finding is kept: a later stage that fails only
                // appends to it.
                note: (finding) => {
                    if(outcome == "ok") {
                        outcome = finding
                    } else {
                        outcome = outcome + "; then " + finding
                    }
                }
                failing: firstNonTransferable()
                if(failing != "none") {
                    note(stage + " of " + failing)
                } else {
                    Error.handle(
                        () => {
                            if(service.request("echo", initialization()).value() != "original") {
                                note(stage + ": unexpected echo")
                            }
                            stage = "spawn"
                            worker = Actor.spawn("std:http/internal/HostWorker", "startWithFactory", probeListener.acceptCapability(), "app:factory", binding, 1, 1, null)
                            stage = "serve request"
                            served: worker.request("serve", initialization())
                            stage = "ready"
                            if(worker.request("ready").value() !== true) {
                                note(stage + ": answered false")
                            }
                            stage = "abort"
                            worker.send("abort")
                            stage = "serve reply"
                            if(served.value() != "drained") {
                                note(stage + ": not drained")
                            }
                        },
                        (error) => {
                            note(stage + ": " + describeError(error))
                            error
                        }
                    )
                }
                if(worker !== null) {
                    worker.stop()
                    worker.termination().value()
                }
                probeListener.close().value()
                outcome
            }
            slowWaiting: () => {
                service.request("waitingCount").value() == 1
            }
            release: () => {
                service.request("release").value()
                service.request("servedCount").value() >= 0
            }
            stopService: () => {
                service.stop()
                service.termination().value()
                true
            }
            0
            """;
}
