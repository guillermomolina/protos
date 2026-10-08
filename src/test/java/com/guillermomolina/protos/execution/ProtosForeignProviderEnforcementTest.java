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

import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * I082-F PLAT052 provider authority enforcement: profiles are enforcement states, rejection is
 * fail-closed before any provider hook, and admitted compartments open only through their
 * host-configured mechanism with zero provisioned authority.
 */
class ProtosForeignProviderEnforcementTest {
    private static final Path EXECUTION =
            Path.of("src/main/java/com/guillermomolina/protos/execution");

    @Test
    void admissionIsDecidedFromProfileAndMechanismKindWithoutRunningProviderCode() {
        Probe probe = new Probe("matrix");
        Optional<ProtosForeignProviderEnforcement> none = Optional.empty();
        Optional<ProtosForeignProviderEnforcement> restriction =
                Optional.of(exploding(true, false));
        Optional<ProtosForeignProviderEnforcement> isolation = Optional.of(exploding(false, true));
        Optional<ProtosForeignProviderEnforcement> both = Optional.of(exploding(true, true));
        Optional<ProtosForeignProviderEnforcement> neither = Optional.of(exploding(false, false));

        assertRejected(probe, ProtosForeignProviderExecutionProfile.UNAVAILABLE, none);
        assertRejected(probe, ProtosForeignProviderExecutionProfile.UNAVAILABLE, restriction);
        assertRejected(probe, ProtosForeignProviderExecutionProfile.UNAVAILABLE, isolation);

        assertRejected(probe, ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS, none);
        assertRejected(probe, ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS, neither);
        assertRejected(probe, ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS, both);
        assertRejected(
                probe, ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS, isolation);
        assertAdmitted(
                probe, ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS, restriction);

        // Strong isolation is never silently satisfied by an in-process restriction.
        assertRejected(probe, ProtosForeignProviderExecutionProfile.STRONGLY_ISOLATED, none);
        assertRejected(probe, ProtosForeignProviderExecutionProfile.STRONGLY_ISOLATED, restriction);
        assertRejected(probe, ProtosForeignProviderExecutionProfile.STRONGLY_ISOLATED, both);
        assertAdmitted(probe, ProtosForeignProviderExecutionProfile.STRONGLY_ISOLATED, isolation);

        assertAdmitted(probe, ProtosForeignProviderExecutionProfile.TRUSTED_IN_PROCESS, none);
        assertRejected(probe, ProtosForeignProviderExecutionProfile.TRUSTED_IN_PROCESS, restriction);

        assertTrue(probe.events.isEmpty(), probe.events.toString());
    }

    @Test
    void rejectedProvidersFailClosedBeforeEveryProviderHookAndRetryStaysClosed() throws Exception {
        List<Probe> rejected = new ArrayList<>();
        List<ProtosForeignProviderDescriptor> descriptors = new ArrayList<>();
        Probe unavailable = new Probe("unavailable");
        Probe unenforced = new Probe("unenforced");
        Probe misenforced = new Probe("misenforced");
        Probe unisolated = new Probe("unisolated");
        Probe inProcessIsolated = new Probe("inprocess");
        descriptors.add(
                unavailable.descriptor(
                        ProtosForeignProviderExecutionProfile.UNAVAILABLE,
                        Optional.of(new Isolation(unavailable.events))));
        descriptors.add(
                unenforced.descriptor(
                        ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS,
                        Optional.empty()));
        descriptors.add(
                misenforced.descriptor(
                        ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS,
                        Optional.of(new Isolation(misenforced.events))));
        descriptors.add(
                unisolated.descriptor(
                        ProtosForeignProviderExecutionProfile.STRONGLY_ISOLATED,
                        Optional.empty()));
        descriptors.add(
                inProcessIsolated.descriptor(
                        ProtosForeignProviderExecutionProfile.STRONGLY_ISOLATED,
                        ProtosForeignInertRestriction.zeroAuthority()));
        rejected.addAll(
                List.of(unavailable, unenforced, misenforced, unisolated, inProcessIsolated));

        try (ProtosForeignValueFixture fixture = new ProtosForeignValueFixture(descriptors)) {
            ProtosActor root = fixture.bootstrap.process().rootActorForRuntime();
            for (Probe probe : rejected) {
                for (int attempt = 0; attempt < 2; attempt++) {
                    assertEquals(
                            ProtosExecutionOutcome.State.FAILED,
                            fixture.run("import(\"" + probe.scheme + ":thing\")").state(),
                            probe.scheme);
                    assertThrows(
                            IllegalStateException.class,
                            () -> fixture.context.foreignSessionForRuntime(root, probe.id));
                }
                // No canonicalization, compartment, session, target, or value hook ever ran.
                assertTrue(probe.events.isEmpty(), probe.scheme + " " + probe.events);
            }
            assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
            assertEquals(0, fixture.provider.restriction.confinements.get());
        }
    }

    @Test
    void admittedRestrictedProviderOpensOnlyThroughItsZeroAuthorityMechanism() throws Exception {
        try (ProtosForeignValueFixture fixture = new ProtosForeignValueFixture()) {
            fixture.provider.modules.put(
                    "root", new ProtosForeignValueFixture.Fake(false).member("x", "value"));
            assertEquals(0, fixture.provider.restriction.confinements.get());

            fixture.eval("m: import(\"test:root\")\nm.x");
            fixture.eval("m: import(\"test:root\")\nm.x");

            assertEquals(1, fixture.provider.restriction.confinements.get());
            assertEquals(1, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    @Test
    void stronglyIsolatedProviderOpensThroughItsIsolationAndTrustedOnlyFromHostRegistry()
            throws Exception {
        Probe isolated = new Probe("isolated");
        Probe trusted = new Probe("trusted");
        Probe unenforced = new Probe("unenforced");
        ProtosForeignProviderDescriptor unenforcedDescriptor =
                unenforced.descriptor(
                        ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS,
                        Optional.empty());
        List<ProtosForeignProviderDescriptor> descriptors =
                List.of(
                        isolated.descriptor(
                                ProtosForeignProviderExecutionProfile.STRONGLY_ISOLATED,
                                Optional.of(new Isolation(isolated.events))),
                        trusted.descriptor(
                                ProtosForeignProviderExecutionProfile.TRUSTED_IN_PROCESS,
                                Optional.empty()),
                        unenforcedDescriptor);
        try (ProtosForeignValueFixture fixture = new ProtosForeignValueFixture(descriptors)) {
            assertInstanceOf(
                    ProtosForeignModuleFacadeValue.class, fixture.eval("import(\"isolated:a\")"));
            assertEquals(
                    List.of("canonical:a", "isolate", "compartment", "session", "acquire:a",
                            "classify"),
                    isolated.events);

            assertInstanceOf(
                    ProtosForeignModuleFacadeValue.class, fixture.eval("import(\"trusted:b\")"));
            assertEquals(
                    List.of("canonical:b", "compartment", "session", "acquire:b", "classify"),
                    trusted.events);

            // No specifier, target spelling, or prior trusted use upgrades another provider.
            for (String specifier :
                    List.of(
                            "unenforced:b",
                            "unenforced:trusted:b",
                            "unenforced:TRUSTED_IN_PROCESS",
                            "trusted:../unenforced:b")) {
                fixture.run("import(\"" + specifier + "\")");
            }
            assertTrue(unenforced.events.isEmpty(), unenforced.events.toString());
            ProtosForeignProviderRegistry registry =
                    fixture.host.foreignProvidersForRuntime();
            assertSame(unenforcedDescriptor, registry.lookup(unenforced.id).orElseThrow());
            assertTrue(ProtosForeignProviderAdmission.rejection(unenforcedDescriptor).isPresent());
            assertEquals(2, fixture.context.foreignProviderCompartmentCountForTesting());
        }
    }

    @Test
    void failedConfinementIsNeitherCachedNorAdmittedAndRetryReconfines() throws Exception {
        Probe probe = new Probe("flaky");
        AtomicInteger failures = new AtomicInteger(1);
        ProtosForeignProviderEnforcement.InProcessRestriction flaky =
                factory -> {
                    probe.events.add("confine");
                    if (failures.getAndDecrement() > 0) {
                        throw new IllegalStateException("confinement unavailable");
                    }
                    return factory.openCompartment();
                };
        try (ProtosForeignValueFixture fixture =
                new ProtosForeignValueFixture(
                        List.of(
                                probe.descriptor(
                                        ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS,
                                        Optional.of(flaky))))) {
            assertEquals(
                    ProtosExecutionOutcome.State.FAILED,
                    fixture.run("import(\"flaky:a\")").state());
            assertEquals(List.of("canonical:a", "confine"), probe.events);
            assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());

            ProtosForeignModuleFacadeValue retried =
                    assertInstanceOf(
                            ProtosForeignModuleFacadeValue.class,
                            fixture.eval("import(\"flaky:a\")"));
            assertTrue(retried.attachmentForRuntime().isPresent());
            assertEquals(
                    List.of("canonical:a", "confine", "canonical:a", "confine", "compartment",
                            "session", "acquire:a", "classify"),
                    probe.events);
        }
    }

    @Test
    void terminatedProcessCannotReacquireAuthorityAndActorsShareNoAdmissionState()
            throws Exception {
        try (ProtosForeignValueFixture fixture = new ProtosForeignValueFixture()) {
            ProtosActor root = fixture.bootstrap.process().rootActorForRuntime();
            ProtosActor other =
                    fixture.bootstrap
                            .process()
                            .createHostedActorForRuntime(
                                    new ProtosObjectValue(ProtosObjectValue.rootObject()));
            ProtosForeignProviderSessionBinding rootSession =
                    fixture.context.foreignSessionForRuntime(root, fixture.provider.id);
            ProtosForeignProviderSessionBinding otherSession =
                    fixture.context.foreignSessionForRuntime(other, fixture.provider.id);
            assertNotSame(rootSession, otherSession);
            assertNotSame(rootSession.sessionForRuntime(), otherSession.sessionForRuntime());
            // One Process compartment, confined once, regardless of how many Actors use it.
            assertEquals(1, fixture.provider.restriction.confinements.get());

            fixture.bootstrap.process().requestTerminationForRuntime();
            fixture.context.awaitTerminalDispositionForRuntime();

            assertFalse(rootSession.isOpenForRuntime());
            assertFalse(otherSession.isOpenForRuntime());
            assertThrows(
                    IllegalStateException.class,
                    () -> fixture.context.foreignSessionForRuntime(root, fixture.provider.id));
            assertEquals(1, fixture.provider.restriction.confinements.get());
            assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
        }
    }

    @Test
    void unusedProvidersStayInertAndAnEmptyRegistryAdmitsNothing() throws Exception {
        Probe isolated = new Probe("isolated");
        try (ProtosForeignValueFixture fixture =
                new ProtosForeignValueFixture(
                        List.of(
                                isolated.descriptor(
                                        ProtosForeignProviderExecutionProfile.STRONGLY_ISOLATED,
                                        Optional.of(new Isolation(isolated.events)))))) {
            fixture.eval("1 + 1");
            assertTrue(isolated.events.isEmpty());
            assertEquals(0, fixture.provider.restriction.confinements.get());
            assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
        }
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            assertSame(ProtosForeignProviderRegistry.EMPTY, host.foreignProvidersForRuntime());
        }
    }

    @Test
    void profileAndEnforcementAreImmutableHostMetadataWithoutGuestOrImportSwitch()
            throws Exception {
        assertTrue(ProtosForeignProviderDescriptor.class.isRecord());
        List<String> components =
                Stream.of(ProtosForeignProviderDescriptor.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList();
        assertTrue(components.containsAll(List.of("profile", "enforcement")));
        for (Method method : ProtosForeignProviderRegistry.class.getDeclaredMethods()) {
            if (!Modifier.isPrivate(method.getModifiers())) {
                assertTrue(
                        List.of("of", "lookup", "lookupImportScheme", "size", "isEmpty")
                                .contains(method.getName()),
                        method.getName());
            }
        }
        // The admission owner is stateless: no shared mutable authority object exists to escape.
        assertEquals(0, ProtosForeignProviderAdmission.class.getDeclaredFields().length);
        // Only the Process lifecycle creates session bindings, and only after admission.
        for (Path file : foreignSources()) {
            String source = Files.readString(file);
            boolean lifecycle =
                    file.getFileName().toString().equals("ProtosForeignProviderProcessLifecycle.java");
            assertEquals(
                    lifecycle,
                    source.contains("new ProtosForeignProviderSessionBinding("),
                    file.toString());
            boolean admission =
                    file.getFileName().toString().equals("ProtosForeignProviderAdmission.java");
            assertEquals(
                    admission, source.contains(".openCompartment()"), file.toString());
            assertEquals(admission, source.contains(".openConfinedCompartment("), file.toString());
        }
        // Import routing never consults or chooses a profile itself.
        String imports = Files.readString(EXECUTION.resolve("ProtosForeignModuleImports.java"));
        assertFalse(imports.contains("ExecutionProfile"));
        assertTrue(
                imports.indexOf("ProtosForeignProviderAdmission.require(descriptor)")
                        < imports.indexOf(".canonicalTarget("));
    }

    @Test
    void callbacksAndValueOperationsCarryNoProviderAuthorityTypes() {
        List<Class<?>> authority =
                List.of(
                        ProtosForeignProviderDescriptor.class,
                        ProtosForeignProviderRegistry.class,
                        ProtosForeignProviderFactory.class,
                        ProtosForeignProviderCompartment.class,
                        ProtosForeignProviderEnforcement.class,
                        ProtosForeignProviderExecutionProfile.class,
                        ProtosForeignProviderProcessLifecycle.class);
        for (Class<?> type :
                List.of(
                        ProtosForeignCallback.class,
                        ProtosForeignArgument.class,
                        ProtosForeignValueAdapter.class,
                        ProtosForeignHandle.class)) {
            for (Method method : type.getDeclaredMethods()) {
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertFalse(authority.contains(parameter), type + "." + method.getName());
                }
                assertFalse(
                        authority.contains(method.getReturnType()), type + "." + method.getName());
            }
        }
    }

    @Test
    void foreignSubstrateIntroducesNoAmbientDiscoveryOrHostAccess() throws Exception {
        List<Path> sources = foreignSources();
        assertTrue(sources.size() > 20);
        for (Path file : sources) {
            String source = Files.readString(file);
            for (String forbidden :
                    List.of(
                            "ServiceLoader",
                            "HostAccess",
                            "PolyglotAccess",
                            "IOAccess",
                            "allowAllAccess",
                            "allowHostAccess",
                            "allowHostClassLookup",
                            "allowHostClassLoading",
                            "allowCreateProcess",
                            "allowNativeAccess",
                            "allowEnvironmentAccess",
                            "allowIO",
                            "Context.newBuilder",
                            "ProcessBuilder",
                            "Runtime.getRuntime",
                            "System.getenv",
                            "System.getProperty",
                            "Class.forName",
                            "getClassLoader",
                            "URLClassLoader",
                            "setAccessible")) {
                assertFalse(source.contains(forbidden), file + " must not use " + forbidden);
            }
        }
    }

    /**
     * I085-A: external plugin discovery is explicit host configuration owned by exactly one
     * non-substrate class; no other execution source performs service discovery.
     */
    @Test
    void externalPluginDiscoveryIsConfinedToTheHostConfiguredLoader() throws Exception {
        List<Path> discovering;
        try (Stream<Path> files = Files.list(EXECUTION)) {
            discovering =
                    files.filter(file -> file.toString().endsWith(".java"))
                            .filter(
                                    file -> {
                                        try {
                                            return Files.readString(file)
                                                    .contains("ServiceLoader.load");
                                        } catch (java.io.IOException failure) {
                                            throw new java.io.UncheckedIOException(failure);
                                        }
                                    })
                            .toList();
        }
        assertEquals(
                List.of(EXECUTION.resolve("ProtosExternalProviderPluginLoader.java")),
                discovering);
    }

    private static List<Path> foreignSources() throws Exception {
        try (Stream<Path> files = Files.list(EXECUTION)) {
            return files.filter(
                            file -> file.getFileName().toString().startsWith("ProtosForeign"))
                    .sorted()
                    .toList();
        }
    }

    private static void assertRejected(
            Probe probe,
            ProtosForeignProviderExecutionProfile profile,
            Optional<ProtosForeignProviderEnforcement> enforcement) {
        ProtosForeignProviderDescriptor descriptor = probe.descriptor(profile, enforcement);
        assertTrue(ProtosForeignProviderAdmission.rejection(descriptor).isPresent(), profile + "");
        assertThrows(
                IllegalStateException.class,
                () -> ProtosForeignProviderAdmission.require(descriptor));
        assertThrows(
                IllegalStateException.class,
                () -> ProtosForeignProviderAdmission.openCompartment(descriptor));
    }

    private static void assertAdmitted(
            Probe probe,
            ProtosForeignProviderExecutionProfile profile,
            Optional<ProtosForeignProviderEnforcement> enforcement) {
        ProtosForeignProviderDescriptor descriptor = probe.descriptor(profile, enforcement);
        assertTrue(ProtosForeignProviderAdmission.rejection(descriptor).isEmpty(), profile + "");
        ProtosForeignProviderAdmission.require(descriptor);
    }

    /** A mechanism of the requested kinds that must never be invoked. */
    private static ProtosForeignProviderEnforcement exploding(
            boolean restriction, boolean isolation) {
        if (restriction && isolation) {
            return new Both();
        }
        if (restriction) {
            return (ProtosForeignProviderEnforcement.InProcessRestriction)
                    factory -> {
                        throw new AssertionError("enforcement must not run during admission");
                    };
        }
        if (isolation) {
            return (ProtosForeignProviderEnforcement.StrongIsolation)
                    factory -> {
                        throw new AssertionError("enforcement must not run during admission");
                    };
        }
        return factory -> {
            throw new AssertionError("enforcement must not run during admission");
        };
    }

    private static final class Both
            implements ProtosForeignProviderEnforcement.InProcessRestriction,
                    ProtosForeignProviderEnforcement.StrongIsolation {
        @Override
        public ProtosForeignProviderCompartment openConfinedCompartment(
                ProtosForeignProviderFactory factory) {
            throw new AssertionError("ambiguous enforcement must never run");
        }
    }

    /** Test stand-in for a physical isolation mechanism; records that the factory ran inside it. */
    private static final class Isolation implements ProtosForeignProviderEnforcement.StrongIsolation {
        private final List<String> events;

        Isolation(List<String> events) {
            this.events = events;
        }

        @Override
        public ProtosForeignProviderCompartment openConfinedCompartment(
                ProtosForeignProviderFactory factory) {
            events.add("isolate");
            return factory.openCompartment();
        }
    }

    /** Inert provider double recording every provider hook the runtime invokes. */
    private static final class Probe
            implements ProtosForeignModuleProvider, ProtosForeignProviderFactory {
        final ProtosForeignProviderId id;
        final String scheme;
        final List<String> events = new CopyOnWriteArrayList<>();

        Probe(String scheme) {
            this.id = new ProtosForeignProviderId("probe-" + scheme);
            this.scheme = scheme;
        }

        ProtosForeignProviderDescriptor descriptor(
                ProtosForeignProviderExecutionProfile profile,
                Optional<ProtosForeignProviderEnforcement> enforcement) {
            return new ProtosForeignProviderDescriptor(
                    id,
                    profile,
                    enforcement,
                    this,
                    Optional.of(new ProtosForeignImportRoute(scheme, this)),
                    new ProtosForeignValueAdapter() {
                        @Override
                        public String language() {
                            return "probe";
                        }

                        @Override
                        public ProtosForeignAdmissionDescriptor classify(
                                ProtosForeignProviderSession session, Object value) {
                            events.add("classify");
                            return ProtosForeignAdmissionDescriptor.opaque();
                        }
                    });
        }

        @Override
        public String canonicalTarget(String exactTarget) {
            events.add("canonical:" + exactTarget);
            return exactTarget;
        }

        @Override
        public Object acquireTarget(ProtosForeignProviderSession session, String canonicalTarget) {
            events.add("acquire:" + canonicalTarget);
            return "target:" + canonicalTarget;
        }

        @Override
        public ProtosForeignProviderCompartment openCompartment() {
            events.add("compartment");
            return new ProtosForeignProviderCompartment() {
                @Override
                public ProtosForeignProviderId providerId() {
                    return id;
                }

                @Override
                public ProtosForeignProviderSession openSession() {
                    events.add("session");
                    return new ProtosForeignProviderSession() {
                        @Override
                        public void close() {}
                    };
                }

                @Override
                public void close() {}
            };
        }
    }
}
