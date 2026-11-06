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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProtosAPlusExecutionProjectionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void sharedRootSourceClosureKeepsStagedDirectFallbackUntilA4B3() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosClosureValue init = rootClosure("init");
        ProtosClosureExecutionPlan template = init.executionPlan().orElseThrow();
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());

        assertFalse(ProtosPolyglotExecutionContext.hasEnteredContextForRuntime());
        assertTrue(init.requiresContextLocalExecutionProjectionForRuntime());
        assertTrue(rootClosure("==").requiresContextLocalExecutionProjectionForRuntime());
        assertTrue(rootClosure("match").requiresContextLocalExecutionProjectionForRuntime());
        ProtosClosureValue boundInit =
                (ProtosClosureValue)
                        ProtosValueLookup.readMember(receiver, "init", prelude).orElseThrow();
        assertTrue(boundInit.requiresContextLocalExecutionProjectionForRuntime());
        assertSame(template, boundInit.executionPlan().orElseThrow());
        assertSame(
                receiver,
                ProtosInvocation.invokeMessage(
                        receiver,
                        "init",
                        List.of(),
                        prelude.newModuleActivation()));
        assertSame(template, init.executionPlan().orElseThrow());
    }

    @Test
    void unenteredComposedCallOfContextLocalClosureRejectsProjection() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosClosureValue init = rootClosure("init");

        assertFalse(ProtosPolyglotExecutionContext.hasEnteredContextForRuntime());
        assertTrue(init.requiresContextLocalExecutionProjectionForRuntime());
        UnsupportedOperationException failure =
                assertThrows(
                        UnsupportedOperationException.class,
                        () ->
                                ProtosBytecodeRootNode.PrepareClosureCall.perform(
                                        init, prelude.newModuleActivation()));
        assertSame(UnsupportedOperationException.class, failure.getClass());
        assertEquals(
                "Context-local Closure projection requires an entered Protos Context",
                failure.getMessage());
    }

    @Test
    void enteredContextPreparedInvocationExecutesSharedRootClosureThroughBytecode()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosClosureValue match = rootClosure("match");

        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot(
                "==",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ProtosBooleanValue.TRUE));

        ProtosObjectValue argument =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation caller = prelude.newModuleActivation();

        try (ProtosPolyglotExecutionContext context =
                ProtosPolyglotExecutionContext.open(
                        InputStream.nullInputStream(),
                        OutputStream.nullOutputStream(),
                        OutputStream.nullOutputStream())) {
            Object result =
                    context.callEntered(
                            () -> {
                                ProtosPreparedCalls.PreparedClosureCall prepared =
                                        ProtosBytecodeRootNode.PrepareSendArguments.performForActivation(
                                                receiver,
                                                "match",
                                                caller,
                                                new Object[] {argument});

                                assertFalse(prepared.isNative());

                                Object entered =
                                        ProtosBytecodeRootNode.EnterClosureCall.ordinaryIndirect(
                                                assertInstanceOf(
                                                        ProtosPreparedCalls.OrdinarySourceCall
                                                                .class,
                                                        prepared),
                                                IndirectCallNode.create());

                                return prepared.finish(entered);
                            });

            assertSame(ProtosBooleanValue.TRUE, result);
        }

        assertSame(match, rootClosure("match"));
    }

    @Test
    void twoProcessContextsProjectOneSharedRootClosureThroughBytecodeAndOverlap()
            throws Exception {
        ProtosPrelude firstPrelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosPrelude secondPrelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosClosureValue match = rootClosure("match");
        ProtosClosureExecutionPlan template = match.executionPlan().orElseThrow();
        ProtosProcessRuntime firstProcess =
                new ProtosProcessRuntime(firstPrelude.actorRefPrototypeForRuntime());
        ProtosProcessRuntime secondProcess =
                new ProtosProcessRuntime(secondPrelude.actorRefPrototypeForRuntime());
        CountDownLatch bothInEquality = new CountDownLatch(2);
        CountDownLatch releaseEquality = new CountDownLatch(1);
        ExecutorService carriers = Executors.newFixedThreadPool(2);

        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            try {
                ProtosPolyglotProcessContext firstContext = host(host, firstProcess);
            ProtosPolyglotProcessContext secondContext = host(host, secondProcess);
            ProtosLanguageContext firstLanguageContext =
                    firstContext.currentLanguageContextForTesting();
            ProtosLanguageContext secondLanguageContext =
                    secondContext.currentLanguageContextForTesting();

            assertNotSame(firstLanguageContext, secondLanguageContext);
            assertNotSame(
                    firstLanguageContext.languageForTesting(),
                    secondLanguageContext.languageForTesting());

            ProtosObjectValue firstReceiver = blockingEqualityReceiver(bothInEquality, releaseEquality);
            ProtosObjectValue secondReceiver = blockingEqualityReceiver(bothInEquality, releaseEquality);

            Future<Object> first =
                    carriers.submit(
                            () ->
                                    firstContext.callForRuntime(
                                            () -> invokeMatch(firstPrelude, firstReceiver)));
            Future<Object> second =
                    carriers.submit(
                            () ->
                                    secondContext.callForRuntime(
                                            () -> invokeMatch(secondPrelude, secondReceiver)));

            assertTrue(
                    bothInEquality.await(10, TimeUnit.SECONDS),
                    "both Process Contexts must overlap inside the shared source-backed behavior");
            releaseEquality.countDown();
            assertSame(ProtosBooleanValue.TRUE, first.get(10, TimeUnit.SECONDS));
            assertSame(ProtosBooleanValue.TRUE, second.get(10, TimeUnit.SECONDS));

            assertEquals(
                    1,
                    firstLanguageContext
                            .projectedBytecodeExecutionPlanCountForTesting());
            assertEquals(
                    1,
                    secondLanguageContext
                            .projectedBytecodeExecutionPlanCountForTesting());
            assertSame(template, match.executionPlan().orElseThrow());
            } finally {
                releaseEquality.countDown();
                carriers.shutdownNow();
                terminate(firstProcess);
                terminate(secondProcess);
            }
        }
    }

    // D241-B / I148: the private source Filesystem open method uses the same per-Context
    // projection as the shared root Closures; its per-capability binding keeps that requirement.
    @Test
    void privateFilesystemOpenMethodProjectsPerProcessContext() throws Exception {
        ProtosPrelude firstPrelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosPrelude secondPrelude = new ProtosCoreBootstrap().bootstrap(CORE);
        for (ProtosPrelude prelude : List.of(firstPrelude, secondPrelude)) {
            ProtosClosureValue method = prelude.filesystemOpenMethodForRuntime();
            assertTrue(method.definition() != null);
            assertTrue(method.executionPlan().isPresent());
            assertTrue(method.nativeBody().isEmpty());
            assertTrue(method.requiresContextLocalExecutionProjectionForRuntime());
            ProtosObjectValue filesystem = new ProtosObjectValue(ProtosObjectValue.rootObject());
            assertTrue(
                    method.bindMethod(filesystem, filesystem)
                            .requiresContextLocalExecutionProjectionForRuntime());
        }
        ProtosClosureExecutionPlan firstTemplate =
                firstPrelude.filesystemOpenMethodForRuntime().executionPlan().orElseThrow();
        ProtosClosureExecutionPlan secondTemplate =
                secondPrelude.filesystemOpenMethodForRuntime().executionPlan().orElseThrow();
        ProtosProcessRuntime firstProcess =
                new ProtosProcessRuntime(firstPrelude.actorRefPrototypeForRuntime());
        ProtosProcessRuntime secondProcess =
                new ProtosProcessRuntime(secondPrelude.actorRefPrototypeForRuntime());

        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            try {
                ProtosPolyglotProcessContext firstContext = host(host, firstProcess);
                ProtosPolyglotProcessContext secondContext = host(host, secondProcess);
                ProtosLanguageContext firstLanguageContext =
                        firstContext.currentLanguageContextForTesting();
                ProtosLanguageContext secondLanguageContext =
                        secondContext.currentLanguageContextForTesting();
                assertNotSame(firstLanguageContext, secondLanguageContext);

                assertInvalidOpen(
                        firstPrelude, firstContext.callForRuntime(() -> invokeOpen(firstPrelude)));
                assertInvalidOpen(
                        secondPrelude,
                        secondContext.callForRuntime(() -> invokeOpen(secondPrelude)));

                // Each Context already owns the projection of its own open template: looking it up
                // again is a hit that adds no projection, and the two projections are distinct
                // Context-owned plans, neither being the shared template.
                ProtosClosureExecutionPlan firstProjection =
                        projectedPlan(firstLanguageContext, firstPrelude, firstTemplate);
                ProtosClosureExecutionPlan secondProjection =
                        projectedPlan(secondLanguageContext, secondPrelude, secondTemplate);
                assertNotSame(firstProjection, secondProjection);
                assertNotSame(firstTemplate, firstProjection);
                assertNotSame(secondTemplate, secondProjection);
                assertSame(
                        firstTemplate,
                        firstPrelude.filesystemOpenMethodForRuntime().executionPlan().orElseThrow());
                assertSame(
                        secondTemplate,
                        secondPrelude.filesystemOpenMethodForRuntime().executionPlan().orElseThrow());
            } finally {
                terminate(firstProcess);
                terminate(secondProcess);
            }
        }
    }

    private static ProtosClosureExecutionPlan projectedPlan(
            ProtosLanguageContext languageContext,
            ProtosPrelude prelude,
            ProtosClosureExecutionPlan template) {
        int before = languageContext.projectedBytecodeExecutionPlanCountForTesting();
        ProtosClosureExecutionPlan projection =
                languageContext.bytecodeExecutionPlanForDefinition(
                        prelude.filesystemOpenMethodForRuntime().definition(), template);
        assertEquals(
                before,
                languageContext.projectedBytecodeExecutionPlanCountForTesting(),
                "the open template was projected by the invocation itself");
        return projection;
    }

    /* An open admitted by the signature that fails on a non-Path value before any backend use. */
    private static Object invokeOpen(ProtosPrelude prelude) {
        assertTrue(ProtosPolyglotExecutionContext.hasEnteredContextForRuntime());
        ProtosActivation caller = prelude.newModuleActivation();
        ProtosObjectValue filesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(),
                        caller,
                        (path, options, completion) -> {
                            throw new AssertionError("backend reached for an invalid Path");
                        });
        return ProtosInvocation.invokeMessage(
                filesystem,
                "open",
                List.of(new ProtosObjectValue(ProtosObjectValue.rootObject())),
                caller);
    }

    private static void assertInvalidOpen(ProtosPrelude prelude, Object result) {
        com.guillermomolina.protos.runtime.ProtosFutureValue future =
                assertInstanceOf(com.guillermomolina.protos.runtime.ProtosFutureValue.class, result);
        assertEquals(com.guillermomolina.protos.runtime.ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                prelude.bindings().readLocalSlot("InvalidIOArgument").orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private static ProtosObjectValue blockingEqualityReceiver(
            CountDownLatch bothInEquality, CountDownLatch releaseEquality) {
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot(
                "==",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            bothInEquality.countDown();
                            try {
                                if (!releaseEquality.await(10, TimeUnit.SECONDS)) {
                                    throw new AssertionError("peer Process Context did not reach equality");
                                }
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(interrupted);
                            }
                            return ProtosBooleanValue.TRUE;
                        }));
        return receiver;
    }

    private static Object invokeMatch(ProtosPrelude prelude, ProtosObjectValue receiver) {
        assertTrue(ProtosPolyglotExecutionContext.hasEnteredContextForRuntime());
        ProtosActivation caller = prelude.newModuleActivation();
        return ProtosInvocation.invokeMessage(
                receiver,
                "match",
                List.of(new ProtosObjectValue(ProtosObjectValue.rootObject())),
                caller);
    }

    private static ProtosClosureValue rootClosure(String selector) {
        Object value =
                ProtosObjectValue.rootObject()
                        .readLocalSlot(selector)
                        .orElseThrow();
        if (!(value instanceof ProtosClosureValue closure)) {
            throw new AssertionError("Object." + selector + " is not a Closure");
        }
        return closure;
    }

    private static ProtosPolyglotProcessContext host(
            ProtosPolyglotRuntimeHost host, ProtosProcessRuntime process) {
        return host.hostProcess(
                process,
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream());
    }

    private static void terminate(ProtosProcessRuntime process) {
        if (process.lifecycleState() == ProtosProcessRuntime.LifecycleState.RUNNING) {
            process.requestTerminationForRuntime();
        }
    }
}
