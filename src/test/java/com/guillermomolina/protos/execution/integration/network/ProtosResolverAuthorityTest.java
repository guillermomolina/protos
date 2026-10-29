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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosResolverCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.io.ProtosHostIpEndpoint;
import com.guillermomolina.protos.runtime.io.ProtosResolverBackend;
import com.guillermomolina.protos.runtime.io.ProtosResolverBackend.AddressDescriptor;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D223-A1 Resolver authority, source-backed admission and explicit composition, end to end through
 * fresh Processes with deterministic fake backends (no public DNS).
 */
final class ProtosResolverAuthorityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path LIBRARY = Path.of("protos", "lib");

    @Test
    void resolverFamilyIsSourceBackedAndPublishedOnlyThroughItsModule() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosObjectValue resolver = prelude.resolverPrototypeForRuntime();
        ProtosObjectValue resolutionError = prelude.resolutionErrorPrototypeForRuntime();
        assertTrue(resolver.isFrozen());
        assertTrue(resolutionError.isFrozen());
        assertSame(ProtosObjectValue.rootObject(), resolver.parent().orElseThrow());
        assertSame(prelude.errorPrototype(), resolutionError.parent().orElseThrow());
        assertEquals(
                java.util.Set.of("lookupAddresses", "close"),
                resolver.localSlotsSnapshot().keySet());
        for (String operation : List.of("lookupAddresses", "close")) {
            ProtosClosureValue closure =
                    assertInstanceOf(
                            ProtosClosureValue.class,
                            resolver.readLocalSlot(operation).orElseThrow());
            assertTrue(closure.nativeBody().isEmpty(), operation + " must be a Protos method");
        }
        assertFalse(prelude.bindings().hasLocalSlot("Resolver"));
        assertFalse(prelude.bindings().hasLocalSlot("ResolutionError"));
        assertFalse(prelude.bindings().hasLocalSlot(ProtosResolverHostFacility.BOOTSTRAP_SLOT));
        assertTrue(prelude.isStandardModuleMemberForRuntime(resolver));
        assertTrue(prelude.isStandardModuleMemberForRuntime(resolutionError));
    }

    @Test
    void resolverSourceLoadInvokesNothingBeforeTheOwningPreludeExists() throws Exception {
        // Core bootstrap without any std: module resolver: loading Resolver.protos may only
        // declare, so it neither imports DnsNames nor invokes a closure during the load.
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        assertTrue(prelude.resolverPrototypeForRuntime().isFrozen());
        assertTrue(prelude.resolutionErrorPrototypeForRuntime().isFrozen());
        assertFalse(prelude.bindings().hasLocalSlot(ProtosResolverHostFacility.BOOTSTRAP_SLOT));
    }

    @Test
    void grantsAreIndependentAndOnlyExplicitGrantsInstallBootstrapSlots() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosNetworkCapabilityValue network = new ProtosNetworkCapabilityValue(prelude, new Object());
        // A Resolver is owned by exactly one Process incarnation, so each one gets its own.
        assertSlots(prelude, null, null, false, false);
        assertSlots(prelude, network, null, true, false);
        assertSlots(prelude, null, new ProtosResolverCapabilityValue(prelude, new FakeBackend()), false, true);
        assertSlots(prelude, network, new ProtosResolverCapabilityValue(prelude, new FakeBackend()), true, true);

        ProtosPrelude other = prelude();
        ProtosResolverCapabilityValue foreign =
                new ProtosResolverCapabilityValue(other, new FakeBackend());
        assertThrows(IllegalStateException.class, () -> bootstrap(prelude, null, foreign));
    }

    @Test
    void processTerminationRetiresExactlyItsOwnGrantOnce() throws Exception {
        ProtosPrelude prelude = prelude();
        FakeBackend usedBackend = new FakeBackend();
        FakeBackend unusedBackend = new FakeBackend();
        FakeBackend separateBackend = new FakeBackend();
        ProtosResolverCapabilityValue used = new ProtosResolverCapabilityValue(prelude, usedBackend);
        ProtosResolverCapabilityValue unused =
                new ProtosResolverCapabilityValue(prelude, unusedBackend);
        ProtosResolverCapabilityValue separate =
                new ProtosResolverCapabilityValue(prelude, separateBackend);
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosExecutionOutcome first =
                    run(host, prelude, null, used,
                            "resolver.lookupAddresses(\"example.com\").value().size() == 1");
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, first.state(), String.valueOf(first.error()));
            assertSame(ProtosBooleanValue.TRUE, first.value());
            ProtosProcessRuntime owner = used.ownerForRuntime().orElseThrow();
            assertTrue(used.retiredForRuntime());
            assertEquals(1, usedBackend.closes);

            // Repeated termination and repeated retirement close nothing again.
            assertFalse(owner.requestTerminationForRuntime());
            owner.retireResolverForRuntime();
            used.retireForRuntime();
            assertEquals(1, usedBackend.closes);

            // A granted but never used Resolver is retired by its Process too.
            ProtosExecutionOutcome idle = run(host, prelude, null, unused, "context.hasSlot(\"resolver\")");
            assertSame(ProtosBooleanValue.TRUE, idle.value(), String.valueOf(idle.error()));
            assertTrue(unused.retiredForRuntime());
            assertEquals(1, unusedBackend.closes);
            assertEquals(List.of(), unusedBackend.names);

            // Neither termination touched the separately provisioned grant.
            assertFalse(separate.retiredForRuntime());
            assertTrue(separate.ownerForRuntime().isEmpty());
            assertEquals(0, separateBackend.closes);

            // The RuntimeHost is still alive: a second Process with its own grant resolves.
            ProtosExecutionOutcome second =
                    run(host, prelude, null, separate,
                            "resolver.lookupAddresses(\"example.org\").value().size() == 1");
            assertSame(ProtosBooleanValue.TRUE, second.value(), String.valueOf(second.error()));
            assertNotSame(owner, separate.ownerForRuntime().orElseThrow());
            assertEquals(List.of("example.org."), separateBackend.names);

            // A retired capability cannot be granted to another Process on the same host.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> run(host, prelude, null, used, "1"));
            assertSame(owner, used.ownerForRuntime().orElseThrow());
            assertEquals(List.of("example.com."), usedBackend.names);
            assertEquals(1, usedBackend.closes);
        }
    }

    @Test
    void anOwnedGrantIsRejectedForASecondProcessWithoutRetiringIt() throws Exception {
        ProtosPrelude prelude = prelude();
        FakeBackend backend = new FakeBackend();
        ProtosResolverCapabilityValue resolver = new ProtosResolverCapabilityValue(prelude, backend);
        ProtosStandaloneProcessBootstrap.Result first = bootstrap(prelude, null, resolver);
        assertSame(first.process(), resolver.ownerForRuntime().orElseThrow());

        assertThrows(IllegalArgumentException.class, () -> bootstrap(prelude, null, resolver));
        assertSame(first.process(), resolver.ownerForRuntime().orElseThrow());
        assertFalse(resolver.retiredForRuntime());
        assertEquals(0, backend.closes);

        assertTrue(first.process().requestTerminationForRuntime());
        assertTrue(resolver.retiredForRuntime());
        assertEquals(1, backend.closes);
    }

    @Test
    void aFailedBootstrapLeavesNoUnownedActiveResolver() throws Exception {
        ProtosPrelude prelude = prelude();
        FakeBackend backend = new FakeBackend();
        // A grant from another Prelude fails RootActor bootstrap after it was bound.
        ProtosResolverCapabilityValue foreign =
                new ProtosResolverCapabilityValue(prelude(), backend);
        assertThrows(IllegalStateException.class, () -> bootstrap(prelude, null, foreign));
        assertTrue(foreign.ownerForRuntime().isPresent());
        assertTrue(foreign.retiredForRuntime());
        assertEquals(1, backend.closes);
        assertThrows(IllegalArgumentException.class, () -> bootstrap(prelude, null, foreign));
        assertEquals(1, backend.closes);
        assertEquals(List.of(), backend.names);
    }

    @Test
    void closingTheRuntimeHostRetiresEveryProvisionedCapability() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open();
        ProtosResolverCapabilityValue unused = host.provisionHostResolver(prelude);
        ProtosResolverCapabilityValue granted = host.provisionHostResolver(prelude);
        ProtosExecutionOutcome outcome = run(host, prelude, null, granted, "1");
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), String.valueOf(outcome.error()));
        assertTrue(granted.retiredForRuntime());
        assertFalse(unused.retiredForRuntime());
        assertTrue(unused.ownerForRuntime().isEmpty());

        host.close();
        assertTrue(unused.retiredForRuntime());
        assertFalse(
                assertInstanceOf(ProtosHostResolverBackend.class, unused.backendForRuntime())
                        .executorInitializedForTesting());
        assertEquals(0, host.provisionedResolverCountForTesting());
        host.close();
        assertThrows(IllegalStateException.class, () -> host.provisionHostResolver(prelude));
        assertThrows(IllegalArgumentException.class, () -> bootstrap(prelude, null, unused));
    }

    @Test
    void resolvedAddressesStayOrdinaryDataAfterRetirement() throws Exception {
        ProtosPrelude prelude = prelude();
        FakeBackend backend = new FakeBackend();
        ProtosResolverCapabilityValue resolver = new ProtosResolverCapabilityValue(prelude, backend);
        ProtosExecutionOutcome outcome;
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            outcome =
                    run(host, prelude, null, resolver,
                            "resolver.lookupAddresses(\"example.com\").value()");
        }
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), String.valueOf(outcome.error()));
        assertTrue(resolver.retiredForRuntime());

        ProtosArrayValue addresses = assertInstanceOf(ProtosArrayValue.class, outcome.value());
        assertTrue(addresses.isFrozen());
        assertEquals(1, addresses.indexedSize());
        ProtosObjectValue address = assertInstanceOf(ProtosObjectValue.class, addresses.indexedAt(0));
        assertTrue(address.isFrozen());
        assertSame(prelude.ipAddressPrototypeForRuntime(), address.parent().orElseThrow());
        assertTrue(
                ProtosStandardIpAddressProtocol.recognizesValue(
                        address, prelude.ipAddressPrototypeForRuntime()));
        assertEquals(java.util.Set.of("version", "bits"), address.localSlotsSnapshot().keySet());
        // Transferable data: it carries no Resolver authority, before or after retirement.
        ProtosObjectValue copy =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosActorValueTransfer.snapshotValue(address, prelude.newModuleActivation()));
        assertSame(prelude.ipAddressPrototypeForRuntime(), copy.parent().orElseThrow());
        assertEquals(1, backend.closes);
    }

    @Test
    void importingTheModuleConfersNoAuthorityAndStartsNoBackend() throws Exception {
        ProtosPrelude prelude = prelude();
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosExecutionOutcome outcome =
                    run(host, prelude, null, null, """
                            Resolvers: import("std:network/Resolvers")
                            failureOf: (future) => {
                                caught: null
                                Error.handle(() => { future.value() }, (error) => {
                                    caught = error
                                    error
                                })
                                caught
                            }
                            child: Resolvers.Resolver {}
                            invalid: (future) => { failureOf(future).parent() === InvalidIOArgument }
                            invalid(Resolvers.Resolver.lookupAddresses("example.com")) && invalid(child.lookupAddresses("example.com")) && invalid(Resolvers.Resolver.close()) && !context.hasSlot("resolver") && !context.hasSlot("network")
                            """);
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), String.valueOf(outcome.error()));
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
            assertEquals(0, host.provisionedResolverCountForTesting());
            assertFalse(host.networkHostInitializedForTesting());
        }
    }

    @Test
    void strictSharedAdmissionRunsInProtosAndInvalidNamesNeverReachTheBackend() throws Exception {
        String label63 = "a".repeat(63);
        String name253 =
                "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        List<String> valid =
                List.of(
                        "example.com",
                        "EXAMPLE.Com.",
                        "localhost",
                        "a-b.c-d",
                        "xn--bcher-kva.example",
                        "XN--BCHER-KVA.EXAMPLE",
                        "xn--4dbc.example",
                        label63 + ".com",
                        name253,
                        name253 + ".");
        List<String> expectedSubmissions =
                List.of(
                        "example.com.",
                        "example.com.",
                        "localhost.",
                        "a-b.c-d.",
                        "xn--bcher-kva.example.",
                        "xn--bcher-kva.example.",
                        "xn--4dbc.example.",
                        label63 + ".com.",
                        name253 + ".",
                        name253 + ".");
        List<String> invalid =
                List.of(
                        "", ".", "a..b", ".a", "-a.com", "a-.com", "ab--cd.com", "xn--.com",
                        "xn--a.com", "xn--ab-j1t.com", "xn--ab-0ea.com", "xn--a-zhc.com",
                        "bücher.example", "ｅｘａｍｐｌｅ.com", "a".repeat(64) + ".com",
                        name253.substring(0, 252) + "dd", "127.0.0.1", "127.0.0.1.", "::1",
                        "2001:db8::1", "under_score.com", "a b.com");

        StringBuilder names = new StringBuilder();
        for (String name : valid) names.append("\"").append(name).append("\", ");
        for (String name : invalid) names.append("\"").append(name).append("\", ");
        String program = """
                failureOf: (future) => {
                    caught: null
                    Error.handle(() => { future.value() }, (error) => {
                        caught = error
                        error
                    })
                    caught
                }
                code: (future) => {
                    failure: failureOf(future)
                    answer: "?"
                    if(failure === null) {
                        answer = "1"
                    } else {
                        if(failure.parent() === InvalidIOArgument) {
                            answer = "0"
                        }
                    }
                    answer
                }
                outcome: ""
                [%s].each((name) => {
                    outcome = outcome + code(resolver.lookupAddresses(name))
                })
                outcome + code(resolver.lookupAddresses()) + code(resolver.lookupAddresses(42)) + code(resolver.lookupAddresses(null)) + code(resolver.lookupAddresses("a.com", "b.com"))
                """.formatted(names.substring(0, names.length() - 2));

        ProtosPrelude prelude = prelude();
        FakeBackend backend = new FakeBackend();
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosExecutionOutcome outcome =
                    run(host, prelude, null, new ProtosResolverCapabilityValue(prelude, backend), program);
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), String.valueOf(outcome.error()));
            String expected = "1".repeat(valid.size()) + "0".repeat(invalid.size()) + "0000";
            assertEquals(expected, assertInstanceOf(ProtosStringValue.class, outcome.value()).value());
        }
        assertEquals(expectedSubmissions, backend.names);
    }

    @Test
    void resolutionComposesExplicitlyWithNumericTcpAndKeepsTheOriginalIdentity() throws Exception {
        ProtosPrelude prelude = prelude();
        FakeBackend backend = new FakeBackend();
        List<ProtosHostIpEndpoint> connects = new ArrayList<>();
        ProtosNetworkCapabilityValue network =
                new ProtosNetworkCapabilityValue(
                        prelude,
                        (com.guillermomolina.protos.runtime.io.ProtosNetworkConnectFlow.Backend)
                                (endpoint, completion) -> {
                                    connects.add(endpoint);
                                    completion.failed();
                                    return () -> {};
                                });
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosExecutionOutcome outcome =
                    run(host, prelude, network, new ProtosResolverCapabilityValue(prelude, backend), """
                            IpEndpoint: import("std:network/IpEndpoints").IpEndpoint
                            IpAddress: import("std:network/IpAddresses").IpAddress
                            Identity: import("std:tls/internal/Identity")
                            asciiOnly: {
                                codePoint: (scalar) => { Error().signal() }
                                isNfc: (scalars) => { Error().signal() }
                            }
                            failureOf: (future) => {
                                caught: null
                                Error.handle(() => { future.value() }, (error) => {
                                    caught = error
                                    error
                                })
                                caught
                            }
                            originalHostname: "API.Example.com"
                            addresses: resolver.lookupAddresses(originalHostname).value()
                            endpoint: IpEndpoint(addresses[0], 443)
                            connectFailure: failureOf(network.connectTcp(endpoint))
                            byName: failureOf(network.connectTcp(originalHostname))
                            reference: Identity.reference(asciiOnly, originalHostname)
                            (addresses.size() == 1) && IpAddress.recognizes(addresses[0]) && (connectFailure.parent() === IOError) && (byName.parent() === InvalidIOArgument) && (originalHostname == "API.Example.com") && (reference.sni == "api.example.com") && (Identity.reference(asciiOnly, "localhost") === null)
                            """);
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), String.valueOf(outcome.error()));
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }
        assertEquals(List.of("api.example.com."), backend.names);
        assertEquals(1, connects.size());
        assertEquals(443, connects.get(0).port());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new byte[] {(byte) 192, 0, 2, 10}, connects.get(0).octets());
    }

    @Test
    void hostProvisioningIsExplicitLazyAndIndependentOfNetwork() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosHostResolverBackend backend;
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosResolverCapabilityValue resolver = host.provisionHostResolver(prelude);
            backend = assertInstanceOf(ProtosHostResolverBackend.class, resolver.backendForRuntime());
            assertFalse(backend.executorInitializedForTesting());
            assertFalse(host.networkHostInitializedForTesting());
            assertEquals(1, host.provisionedResolverCountForTesting());
        }
        List<ProtosResolverBackend.FailureKind> failures = new ArrayList<>();
        backend.lookup("example.com.", new ProtosResolverBackend.Completion() {
            @Override public void succeeded(List<AddressDescriptor> addresses) {}
            @Override public void failed(ProtosResolverBackend.FailureKind kind) { failures.add(kind); }
        });
        assertEquals(List.of(ProtosResolverBackend.FailureKind.UNAVAILABLE), failures);
        assertFalse(backend.executorInitializedForTesting());

        ProtosFreshProcessExecutor.Request carrying =
                request(prelude, source("1"), null, new ProtosResolverCapabilityValue(prelude, new FakeBackend()));
        assertThrows(IllegalArgumentException.class, () -> ProtosFreshProcessExecutor.execute(carrying));
    }

    // ------------------------------------------------------------ helpers

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(LIBRARY));
    }

    private static ProtosStandaloneProcessBootstrap.Result bootstrap(
            ProtosPrelude prelude,
            ProtosNetworkCapabilityValue network,
            ProtosResolverCapabilityValue resolver) {
        return ProtosStandaloneProcessBootstrap.create(
                prelude, List.of(), ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN,
                List.of(), null, null, null, null, null, null, null, network, resolver);
    }

    private static void assertSlots(
            ProtosPrelude prelude,
            ProtosNetworkCapabilityValue network,
            ProtosResolverCapabilityValue resolver,
            boolean expectNetwork,
            boolean expectResolver) {
        ProtosObjectValue context = bootstrap(prelude, network, resolver).activation().context();
        assertEquals(expectNetwork, context.hasLocalSlot("network"));
        assertEquals(expectResolver, context.hasLocalSlot("resolver"));
        if (expectNetwork) assertSame(network, context.readLocalSlot("network").orElseThrow());
        if (expectResolver) assertSame(resolver, context.readLocalSlot("resolver").orElseThrow());
    }

    private static ProtosExecutionOutcome run(
            ProtosPolyglotRuntimeHost host,
            ProtosPrelude prelude,
            ProtosNetworkCapabilityValue network,
            ProtosResolverCapabilityValue resolver,
            String program) {
        return ProtosFreshProcessExecutor.execute(
                request(prelude, source(program), network, resolver), host);
    }

    private static ProtosFreshProcessExecutor.Request request(
            ProtosPrelude prelude,
            Source entry,
            ProtosNetworkCapabilityValue network,
            ProtosResolverCapabilityValue resolver) {
        return new ProtosFreshProcessExecutor.Request(
                prelude, entry, List.of(), ProtosStandaloneHostedExecution.HOST_ENVIRONMENT_NAME_DOMAIN,
                List.of(), null, null, null, null, null, null, null, network, resolver);
    }

    private static Source source(String characters) {
        return Source.newBuilder(ProtosLanguage.ID, characters, "<resolver-authority-test>")
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    /**
     * Answers every admitted name synchronously with 192.0.2.10 and records the names and the
     * number of close calls.
     */
    private static final class FakeBackend implements ProtosResolverBackend {
        private final List<String> names = new ArrayList<>();
        private int closes;

        @Override
        public Cancellation lookup(String absoluteName, Completion completion) {
            names.add(absoluteName);
            completion.succeeded(List.of(new AddressDescriptor(new byte[] {(byte) 192, 0, 2, 10})));
            return () -> {};
        }

        @Override
        public void close() {
            closes++;
        }
    }
}
