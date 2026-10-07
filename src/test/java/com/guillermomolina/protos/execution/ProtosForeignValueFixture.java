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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Hosted Process with one inert D188 test provider routed by the {@code test:} scheme.
 *
 * <p>The provider is a plain-Java double: no guest language, Polyglot Context, or host authority.
 * Its values carry explicit source classifications so tests can tell semantic category from shape.
 */
final class ProtosForeignValueFixture implements AutoCloseable {
    static final String LANGUAGE = "test-lang";

    final TestProvider provider = new TestProvider();
    final ProtosPrelude prelude;
    final ProtosStandaloneProcessBootstrap.Result bootstrap;
    final ProtosPolyglotRuntimeHost host;
    final ProtosPolyglotProcessContext context;
    final ProtosActivation root;
    private ProtosActivation current;

    ProtosForeignValueFixture() throws Exception {
        prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                Path.of("protos", "lib", "core"),
                                new ProtosModuleRuntimeTest.MemoryResolver());
        bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        prelude, List.of(), exactEnvironmentDomain(), List.of(),
                        null, null, null, null, null, null, null);
        host =
                ProtosPolyglotRuntimeHost.openWithForeignProvidersForTesting(
                        ProtosForeignProviderRegistry.of(List.of(provider.descriptor())));
        context =
                host.hostProcess(
                        bootstrap.process(),
                        InputStream.nullInputStream(),
                        OutputStream.nullOutputStream(),
                        OutputStream.nullOutputStream());
        root = bootstrap.activation();
    }

    /** Runs {@code source} as a fresh root-Actor task; the module cache is shared across runs. */
    ProtosExecutionOutcome run(String source) {
        current =
                prelude.newModuleActivation(
                        root.actorModuleState(),
                        new ProtosModuleKey("entry"),
                        prelude.newExecutionContext(),
                        root.executionDomain());
        return context.executeModuleSource(
                ProtosModuleSource.fromCharacters(new ProtosModuleKey("entry"), source), current);
    }

    Object eval(String source) {
        ProtosExecutionOutcome outcome = run(source);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), source);
        return outcome.value();
    }

    ProtosObjectValue failure(String source) {
        ProtosExecutionOutcome outcome = run(source);
        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state(), source);
        return outcome.error();
    }

    /** The activation of the most recent {@link #run}, for direct runtime calls. */
    ProtosActivation activation() {
        return current;
    }

    ProtosForeignModuleFacadeValue facade(String target) {
        return assertInstanceOf(
                ProtosForeignModuleFacadeValue.class, eval("import(\"test:" + target + "\")"));
    }

    ProtosObjectValue standardError(String name) {
        return (ProtosObjectValue) prelude.bindings().readLocalSlot(name).orElseThrow();
    }

    @Override
    public void close() {
        try {
            bootstrap.process().requestTerminationForRuntime();
        } finally {
            host.close();
        }
    }

    /** Explicitly source-classified foreign integral value. */
    record Integral(BigInteger value) {}

    /** Explicitly source-classified foreign binary floating value. */
    record Binary64(double value) {}

    /** Foreign decimal: never collapsed, even when the number fits. */
    record Decimal(BigDecimal value) {}

    /** A number whose source family is unknown: never collapsed. */
    record Ambiguous(long value) {}

    /** True foreign null and an ambiguous null-like value (JavaScript undefined). */
    enum Marker {
        NULL,
        UNDEFINED
    }

    /**
     * A foreign object. Java equality is deliberately total so that any leak of host equality into
     * Protos {@code ==}, {@code hash}, Map, or IdentityMap is observable.
     */
    static final class Fake {
        final Object identity = new Object();
        final boolean stableIdentity;
        final Set<ProtosForeignAdmissionDescriptor.Capability> capabilities;
        final Map<String, Object> faithful = new LinkedHashMap<>();
        final Map<String, Object> unfaithful = new LinkedHashMap<>();
        final List<Object> elements = new ArrayList<>();
        Function<List<ProtosForeignArgument>, Object> body = arguments -> Marker.NULL;
        /** Pull-iteration failure injected at "iterator", "hasNext", or "next". */
        String iteratorFailureStage;
        TestForeignFailure iteratorFailure;
        /** Runs inside the provider right after each successful pull. */
        Runnable afterNext = () -> {};

        Fake(boolean stableIdentity, ProtosForeignAdmissionDescriptor.Capability... capabilities) {
            this.stableIdentity = stableIdentity;
            this.capabilities =
                    capabilities.length == 0
                            ? EnumSet.noneOf(ProtosForeignAdmissionDescriptor.Capability.class)
                            : EnumSet.of(capabilities[0], capabilities);
        }

        Fake member(String name, Object value) {
            faithful.put(name, value);
            return this;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Fake || other instanceof Alias;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /** A distinct physical wrapper of the same foreign object. */
    static final class Alias {
        final Fake fake;

        Alias(Fake fake) {
            this.fake = fake;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Fake || other instanceof Alias;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /** A host failure whose message is unsafe; only its explicit safe fields may be projected. */
    static final class TestForeignFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String safeMessage;
        Throwable foreignCause;

        TestForeignFailure(String safeMessage) {
            super("SECRET host detail");
            this.safeMessage = safeMessage;
        }
    }

    /** Provider double: module routing, lifecycle, and the D188 value contract. */
    static final class TestProvider
            implements ProtosForeignModuleProvider,
                    ProtosForeignProviderFactory,
                    ProtosForeignValueAdapter {
        final ProtosForeignProviderId id = new ProtosForeignProviderId("provider-test");
        final Map<String, Object> modules = new LinkedHashMap<>();
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<List<ProtosForeignArgument>> executions = new CopyOnWriteArrayList<>();
        final List<ProtosForeignProviderSession> sessions = new CopyOnWriteArrayList<>();
        /** Protos runtime objects that reached an iterator operation; must stay empty. */
        final List<Object> leaks = new CopyOnWriteArrayList<>();

        ProtosForeignProviderDescriptor descriptor() {
            return new ProtosForeignProviderDescriptor(
                    id,
                    ProtosForeignProviderExecutionProfile.RESTRICTED_IN_PROCESS,
                    this,
                    Optional.of(new ProtosForeignImportRoute("test", this)),
                    this);
        }

        long count(String event) {
            return events.stream().filter(event::equals).count();
        }

        private static Fake fake(Object target) {
            return target instanceof Alias alias ? alias.fake : (Fake) target;
        }

        @Override
        public String canonicalTarget(String exactTarget) {
            return exactTarget;
        }

        @Override
        public Object acquireTarget(ProtosForeignProviderSession session, String canonicalTarget) {
            Object target = modules.get(canonicalTarget);
            if (target instanceof TestForeignFailure failure) {
                throw failure;
            }
            return target;
        }

        @Override
        public ProtosForeignProviderCompartment openCompartment() {
            return new ProtosForeignProviderCompartment() {
                @Override
                public ProtosForeignProviderId providerId() {
                    return id;
                }

                @Override
                public ProtosForeignProviderSession openSession() {
                    ProtosForeignProviderSession session =
                            new ProtosForeignProviderSession() {
                                @Override
                                public void close() {}
                            };
                    sessions.add(session);
                    return session;
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public String language() {
            return LANGUAGE;
        }

        @Override
        public ProtosForeignAdmissionDescriptor classify(
                ProtosForeignProviderSession session, Object value) {
            if (value instanceof Boolean bool) {
                return ProtosForeignAdmissionDescriptor.booleanValue(bool);
            }
            if (value == Marker.NULL) {
                return ProtosForeignAdmissionDescriptor.absent();
            }
            if (value instanceof String text) {
                return ProtosForeignAdmissionDescriptor.text(text);
            }
            if (value instanceof Integral integral) {
                return ProtosForeignAdmissionDescriptor.integral(integral.value());
            }
            if (value instanceof Binary64 binary) {
                return ProtosForeignAdmissionDescriptor.binary64(binary.value());
            }
            if (value instanceof Fake || value instanceof Alias) {
                Fake fake = fake(value);
                return ProtosForeignAdmissionDescriptor.raw(
                        fake.stableIdentity ? fake.identity : null, fake.capabilities);
            }
            // Marker.UNDEFINED, Decimal, Ambiguous, Lists, Maps: shape never decides.
            return ProtosForeignAdmissionDescriptor.opaque();
        }

        @Override
        public boolean canFaithfullyReadMember(
                ProtosForeignProviderSession session, Object target, String name) {
            return target instanceof Fake || target instanceof Alias
                    ? fake(target).faithful.containsKey(name)
                    : false;
        }

        @Override
        public Object readMember(ProtosForeignProviderSession session, Object target, String name) {
            events.add("read:" + name);
            Object value = fake(target).faithful.get(name);
            if (value instanceof TestForeignFailure failure) {
                throw failure;
            }
            return value instanceof Supplier<?> supplier ? supplier.get() : value;
        }

        @Override
        public Object execute(
                ProtosForeignProviderSession session,
                Object target,
                List<ProtosForeignArgument> arguments) {
            executions.add(arguments);
            return fake(target).body.apply(arguments);
        }

        @Override
        public Object readElement(
                ProtosForeignProviderSession session, Object target, ProtosForeignArgument index) {
            events.add("at:" + index.value());
            return fake(target).elements.get(((BigInteger) index.value()).intValueExact());
        }

        @Override
        public void writeElement(
                ProtosForeignProviderSession session,
                Object target,
                ProtosForeignArgument index,
                ProtosForeignArgument value) {
            events.add("atPut:" + index.value());
            fake(target).elements.set(((BigInteger) index.value()).intValueExact(), value.value());
        }

        /** Provider-private pull iterator: a live position over the target's element list. */
        static final class Cursor {
            final Fake fake;
            int index;

            Cursor(Fake fake) {
                this.fake = fake;
            }
        }

        private void guardNoProtosObject(Object... received) {
            for (Object value : received) {
                if (value instanceof ProtosClosureValue
                        || value instanceof ProtosObjectValue
                        || value instanceof ProtosActivation
                        || value instanceof ProtosTask) {
                    leaks.add(value);
                }
            }
        }

        private static void failAt(Fake fake, String stage) {
            if (stage.equals(fake.iteratorFailureStage)) {
                throw fake.iteratorFailure;
            }
        }

        @Override
        public Object openIterator(ProtosForeignProviderSession session, Object target) {
            guardNoProtosObject(session, target);
            events.add("iterator");
            failAt(fake(target), "iterator");
            return new Cursor(fake(target));
        }

        @Override
        public boolean iteratorHasNext(ProtosForeignProviderSession session, Object iterator) {
            guardNoProtosObject(session, iterator);
            Cursor cursor = (Cursor) iterator;
            events.add("hasNext");
            failAt(cursor.fake, "hasNext");
            return cursor.index < cursor.fake.elements.size();
        }

        @Override
        public Object iteratorNext(ProtosForeignProviderSession session, Object iterator) {
            guardNoProtosObject(session, iterator);
            Cursor cursor = (Cursor) iterator;
            events.add("next:" + cursor.index);
            failAt(cursor.fake, "next");
            Object element = cursor.fake.elements.get(cursor.index++);
            cursor.fake.afterNext.run();
            return element;
        }

        @Override
        public ProtosForeignFailureDescription describeFailure(Throwable failure) {
            if (failure instanceof TestForeignFailure test) {
                return new ProtosForeignFailureDescription(
                        "test-category", "TestForeignFailure", test.safeMessage, test.foreignCause);
            }
            return ProtosForeignFailureDescription.EMPTY;
        }
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactEnvironmentDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }
}
