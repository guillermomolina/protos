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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * I060-B/D167 placement guards for the buffered byte wrapper factories.
 *
 * <p>{@code BufferedReader} and {@code BufferedWriter} are FROZEN runtime-owned standard objects
 * published only as initial members of {@code std:io/BufferedReader} and
 * {@code std:io/BufferedWriter}. Module instances stay Actor-local while the factory identity is
 * shared; every construction creates a fresh wrapper. Isolation transfer keeps the factories as
 * exact anchors and never imports a module to recover them; the counting resolver makes any such
 * import deterministically observable.
 */
final class ProtosStandardBufferedByteIoPlacementTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final ProtosModuleKey READER_MODULE =
            new ProtosModuleKey("std:io/BufferedReader");
    private static final ProtosModuleKey WRITER_MODULE =
            new ProtosModuleKey("std:io/BufferedWriter");
    private static final String SOURCE_MODULES =
            """
            Array(import("std:io/BufferedReader"), import("std:io/BufferedWriter"))
            """;
    private static final String SOURCE_FACTORIES =
            """
            BufferedReader: import("std:io/BufferedReader").BufferedReader
            BufferedWriter: import("std:io/BufferedWriter").BufferedWriter
            Array(BufferedReader, BufferedWriter)
            """;

    private static final String SOURCE_PARALLEL_FACTORIES =
            """
            BufferedReader: import("std:io/BufferedReader").BufferedReader
            BufferedWriter: import("std:io/BufferedWriter").BufferedWriter
            factories: Array(BufferedReader, BufferedWriter)
            factories
            """;

    /** Delegating resolver that counts every resolve and loadSource request. */
    private static final class CountingResolver implements ProtosModuleResolver {
        private final ProtosModuleResolver delegate =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        final AtomicInteger resolveCount = new AtomicInteger();
        final AtomicInteger loadSourceCount = new AtomicInteger();

        @Override
        public ProtosModuleKey resolve(
                String exactSpecifier, Optional<ProtosModuleKey> importingModule)
                throws Exception {
            resolveCount.incrementAndGet();
            return delegate.resolve(exactSpecifier, importingModule);
        }

        @Override
        public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
            loadSourceCount.incrementAndGet();
            return delegate.loadSource(key);
        }
    }

    @Test
    void preludeNoLongerPublishesBufferedFactories() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

        assertTrue(prelude.bindings().readLocalSlot("BufferedReader").isEmpty());
        assertTrue(prelude.bindings().readLocalSlot("BufferedWriter").isEmpty());
        assertFalse(Files.exists(CORE.resolve("BufferedReader.protos")));
        assertFalse(Files.exists(CORE.resolve("BufferedWriter.protos")));
        System.out.println("PRELUDE_BUFFERED_READER_ABSENT=PASS");
        System.out.println("PRELUDE_BUFFERED_WRITER_ABSENT=PASS");
    }

    @Test
    void standardModulesPublishFrozenFactoriesWithoutRedeclaringThem() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        for (ProtosObjectValue factory :
                List.of(
                        ProtosStandardModuleMemberTestSupport.bufferedReaderFactory(prelude),
                        ProtosStandardModuleMemberTestSupport.bufferedWriterFactory(prelude))) {
            assertTrue(factory.isFrozen());
            assertSame(ProtosObjectValue.rootObject(), factory.parent().orElseThrow());
            assertEquals(Set.of("call", "owning"), factory.localSlotsSnapshot().keySet());
            assertTrue(prelude.isStandardModuleMemberForRuntime(factory));
        }
        for (String name : new String[] {"BufferedReader", "BufferedWriter"}) {
            String source =
                    Files.readString(STANDARD_LIBRARY.resolve("io").resolve(name + ".protos"));
            assertFalse(source.contains(name + ":"), name);
        }
        System.out.println("STDLIB_BUFFERED_READER_PRESENT=PASS");
        System.out.println("STDLIB_BUFFERED_WRITER_PRESENT=PASS");
        System.out.println("READER_FACTORY_FROZEN=PASS");
        System.out.println("WRITER_FACTORY_FROZEN=PASS");
    }

    @Test
    void genericModuleAndTransferPathsHaveNoBufferedKnowledge() throws Exception {
        Path main = Path.of("src", "main", "java", "com", "guillermomolina", "protos");
        for (Path file :
                List.of(
                        main.resolve("execution").resolve("ProtosModuleRuntime.java"),
                        main.resolve("execution")
                                .resolve("ProtosCanonicalInitialModuleExecution.java"),
                        main.resolve("execution").resolve("ProtosParallelRuntime.java"),
                        main.resolve("execution").resolve("ProtosDetachedExecutionValue.java"),
                        main.resolve("runtime").resolve("ProtosActorValueTransfer.java"),
                        main.resolve("runtime").resolve("ProtosPrelude.java"))) {
            String source = Files.readString(file);
            assertFalse(source.contains("BufferedReader"), file.toString());
            assertFalse(source.contains("BufferedWriter"), file.toString());
        }
    }

    @Test
    void moduleInstancesAreActorLocalWhileFactoryIdentityIsShared() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, new CountingResolver());
        ProtosActivation actorA = prelude.newModuleActivation();
        ProtosActivation actorB = prelude.newModuleActivation();
        assertNotSame(actorA.actorModuleState(), actorB.actorModuleState());

        List<?> modulesA = arrayElements(ProtosTestExecutionSupport.evaluate(SOURCE_MODULES, actorA));
        List<?> modulesB = arrayElements(ProtosTestExecutionSupport.evaluate(SOURCE_MODULES, actorB));
        List<?> factoriesA =
                arrayElements(ProtosTestExecutionSupport.evaluate(SOURCE_FACTORIES, actorA));
        List<?> factoriesB =
                arrayElements(ProtosTestExecutionSupport.evaluate(SOURCE_FACTORIES, actorB));

        for (int index = 0; index < 2; index++) {
            assertNotSame(modulesA.get(index), modulesB.get(index));
            assertSame(factoriesA.get(index), factoriesB.get(index));
        }
        assertSame(
                ProtosStandardModuleMemberTestSupport.bufferedReaderFactory(prelude),
                factoriesA.get(0));
        assertSame(
                ProtosStandardModuleMemberTestSupport.bufferedWriterFactory(prelude),
                factoriesA.get(1));
        System.out.println("MODULE_INSTANCES_ACTOR_LOCAL=PASS");
        System.out.println("FACTORY_IDENTITY_SHARED=PASS");
    }

    @Test
    void everyConstructionCreatesAFreshWrapper() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue lower = lowerCapability();
        ProtosObjectValue reader =
                ProtosStandardModuleMemberTestSupport.bufferedReaderFactory(prelude);
        ProtosObjectValue writer =
                ProtosStandardModuleMemberTestSupport.bufferedWriterFactory(prelude);

        for (ProtosObjectValue factory : List.of(reader, writer)) {
            for (String selector : new String[] {"call", "owning"}) {
                Object first =
                        ProtosInvocation.invokeMessage(
                                factory, selector, List.of(lower), activation);
                Object second =
                        ProtosInvocation.invokeMessage(
                                factory, selector, List.of(lower), activation);
                assertInstanceOf(ProtosObjectValue.class, first);
                assertNotSame(first, second);
                assertNotSame(factory, first);
            }
        }
        System.out.println("FRESH_READER_WRAPPER_PER_CONSTRUCTION=PASS");
        System.out.println("FRESH_WRITER_WRAPPER_PER_CONSTRUCTION=PASS");
    }

    @Test
    void actorAndDetachedTransferKeepExactFactoriesWithoutImporting() throws Exception {
        CountingResolver resolver = new CountingResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        List<?> factories =
                arrayElements(ProtosTestExecutionSupport.evaluate(SOURCE_FACTORIES, activation));
        ProtosActorModuleState modules = activation.actorModuleState();
        ProtosActorModuleState.ModuleRecord readerModule =
                modules.lookup(READER_MODULE).orElseThrow();
        ProtosActorModuleState.ModuleRecord writerModule =
                modules.lookup(WRITER_MODULE).orElseThrow();
        int resolves = resolver.resolveCount.get();
        int loads = resolver.loadSourceCount.get();

        ProtosActivation detachedDestination = prelude.newModuleActivation();
        for (Object factory : factories) {
            assertSame(factory, ProtosActorValueTransfer.snapshotValue(factory, activation));
            assertSame(
                    factory,
                    ProtosDetachedExecutionValue.snapshot(factory, detachedDestination));
        }

        assertEquals(resolves, resolver.resolveCount.get());
        assertEquals(loads, resolver.loadSourceCount.get());
        assertSame(readerModule, modules.lookup(READER_MODULE).orElseThrow());
        assertSame(writerModule, modules.lookup(WRITER_MODULE).orElseThrow());
        assertTrue(detachedDestination.actorModuleState().lookup(READER_MODULE).isEmpty());
        assertTrue(detachedDestination.actorModuleState().lookup(WRITER_MODULE).isEmpty());
        System.out.println("ACTOR_TRANSFER_EXACT_FACTORY_IDENTITY=PASS");
        System.out.println("DETACHED_TRANSFER_EXACT_FACTORY_IDENTITY=PASS");
        System.out.println("TRANSFER_CAUSES_NO_MODULE_IMPORT=PASS");
        System.out.println("TRANSFER_CAUSES_NO_MODULE_SOURCE_EXECUTION=PASS");
    }

    @Test
    void parallelTransferKeepsExactFactoriesWithoutImporting() throws Exception {
        CountingResolver resolver = new CountingResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        try (var hosted = ProtosHostedExecutionTestFixture.open(prelude)) {
            List<?> factories =
                    arrayElements(
                            hosted.evaluatePersistent(
                                    "<i060-p-source>", SOURCE_PARALLEL_FACTORIES));
            int resolves = resolver.resolveCount.get();
            int loads = resolver.loadSourceCount.get();

            var domain = hosted.activation().executionDomain();
            ProtosFutureValue future =
                    assertInstanceOf(
                            ProtosFutureValue.class,
                            hosted.evaluatePersistent(
                                    "<i060-p-transfer>",
                                    "((value) => { value }).parallel(factories)"));
            while (future.isPending()) {
                hosted.callEntered(
                        () -> {
                            domain.dispatchUntilIdle();
                            return null;
                        });
                Thread.onSpinWait();
            }
            assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
            List<?> transferred = arrayElements(future.resolvedValue().orElseThrow());

            // Both the P worker and the result copy-back ran without any module request.
            assertEquals(resolves, resolver.resolveCount.get());
            assertEquals(loads, resolver.loadSourceCount.get());
            assertSame(factories.get(0), transferred.get(0));
            assertSame(factories.get(1), transferred.get(1));
        }
        System.out.println("P_TRANSFER_EXACT_FACTORY_IDENTITY=PASS");
    }

    private static List<?> arrayElements(Object value) {
        return assertInstanceOf(ProtosArrayValue.class, value).indexedSnapshot();
    }

    /** Minimal explicit lower capability satisfying ByteReadable, ByteWritable and Closable. */
    private static ProtosObjectValue lowerCapability() {
        ProtosObjectValue lower = new ProtosObjectValue(ProtosObjectValue.rootObject());
        for (String selector : new String[] {"read", "write", "flush", "close"}) {
            lower.createLocalSlot(
                    selector,
                    ProtosClosureValue.nativeClosure(
                            (activation, args) -> ProtosNullValue.INSTANCE));
        }
        return lower;
    }
}
