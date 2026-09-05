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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosStandardTextReaderProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void coreFactoryIsFrozenFreshAndAcceptsRepresentedByteReadableCapability()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue factory = factory(prelude);
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");

        assertSame(ProtosObjectValue.rootObject(), factory.parent().orElseThrow());
        assertTrue(factory.isFrozen());
        assertEquals(Set.of("call", "owning"), factory.localSlotsSnapshot().keySet());

        ScriptedSource ordinary = new ScriptedSource(activation, true);
        ProtosObjectValue first =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosInvocation.invokeMessage(
                                factory,
                                "call",
                                List.of(ordinary.source, utf8),
                                activation));
        ProtosObjectValue second =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosInvocation.invokeMessage(
                                factory,
                                "call",
                                List.of(ordinary.source, utf8),
                                activation));
        assertNotSame(first, second);
        assertEquals(Set.of("readText", "close"), first.localSlotsSnapshot().keySet());

        ProtosProcessRuntime process =
                new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        process.establishStandardStreamsForRuntime(
                ProtosStandardProcessStreamProtocol.createReadablePrototype(),
                ProtosStandardProcessStreamProtocol.createWritablePrototype(),
                prelude.bytesPrototypeForRuntime(),
                (maximum, completion) -> () -> {},
                null,
                null);
        ProtosProcessStandardStreamValue representedStdin =
                process.stdinForRuntime().orElseThrow();

        ProtosObjectValue representedWrapper =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosInvocation.invokeMessage(
                                factory,
                                "call",
                                List.of(representedStdin, utf8),
                                activation));
        assertEquals(
                Set.of("readText", "close"),
                representedWrapper.localSlotsSnapshot().keySet());

        ProtosSignalException owningWithoutClose =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        factory,
                                        "owning",
                                        List.of(representedStdin, utf8),
                                        activation));
        assertErrorParent(
                prelude, owningWithoutClose.error(), "InvalidIOArgument");

        ProtosObjectValue fakeEncoding =
                new ProtosObjectValue(prelude.encodingPrototype());
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                factory,
                                "call",
                                List.of(ordinary.source, fakeEncoding),
                                activation));
    }

    @Test
    void utf8ReadTextConsumesInitialBomAndReturnsProgressBeforeIncompleteSuffix()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedSource source = new ScriptedSource(activation, false);
        source.bytes(0xef, 0xbb);
        source.bytes(0xbf, 0x41, 0xf0, 0x9f);
        source.bytes(0x98, 0x80);
        source.eof();

        ProtosObjectValue reader =
                reader(prelude, activation, source, "UTF8", false);

        ProtosFutureValue first = readText(reader, activation);
        assertEquals("A", stringResult(first));
        assertEquals(
                2,
                source.reads,
                "reader must not wait for a larger chunk once A is returnable");

        ProtosFutureValue second = readText(reader, activation);
        assertEquals("😀", stringResult(second));
        assertEquals(3, source.reads);

        ProtosFutureValue third = readText(reader, activation);
        assertSame(ProtosNullValue.INSTANCE, third.resolvedValue().orElseThrow());
        assertEquals(4, source.reads);
    }

    @Test
    void portableUtf16AndLatin1KeepIndependentStrictPerFlowState()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();

        ScriptedSource leSource = new ScriptedSource(activation, false);
        leSource.bytes(0xff);
        leSource.bytes(0xfe, 0x41, 0x00, 0x3d, 0xd8);
        leSource.bytes(0x00, 0xde);
        leSource.eof();
        ProtosObjectValue le =
                reader(prelude, activation, leSource, "UTF16LE", false);
        assertEquals("A", stringResult(readText(le, activation)));
        assertEquals("😀", stringResult(readText(le, activation)));
        assertSame(
                ProtosNullValue.INSTANCE,
                readText(le, activation).resolvedValue().orElseThrow());

        ScriptedSource beSource = new ScriptedSource(activation, false);
        beSource.bytes(0xfe, 0xff, 0x00, 0x41);
        beSource.eof();
        ProtosObjectValue be =
                reader(prelude, activation, beSource, "UTF16BE", false);
        assertEquals("A", stringResult(readText(be, activation)));

        ScriptedSource latinSource = new ScriptedSource(activation, false);
        latinSource.bytes(0x00, 0x80, 0xff);
        latinSource.eof();
        ProtosObjectValue latin =
                reader(prelude, activation, latinSource, "Latin1", false);
        assertEquals(
                "\u0000\u0080\u00ff",
                stringResult(readText(latin, activation)));
    }

    @Test
    void malformedDataAfterValidPrefixIsDeferredThenPermanentlyPoisonsReader()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedSource source = new ScriptedSource(activation, false);
        source.bytes(0x41, 0xc0, 0xaf);
        source.bytes(0x42);

        ProtosObjectValue reader =
                reader(prelude, activation, source, "UTF8", false);

        ProtosFutureValue prefix = readText(reader, activation);
        assertEquals("A", stringResult(prefix));
        assertEquals(1, source.reads);

        ProtosFutureValue failure = readText(reader, activation);
        assertEquals(ProtosFutureValue.State.FAILED, failure.state());
        ProtosObjectValue error = failure.failedError().orElseThrow();
        assertErrorParent(prelude, error, "EncodingError");
        assertEquals(1, source.reads);

        ProtosFutureValue repeated = readText(reader, activation);
        assertEquals(ProtosFutureValue.State.FAILED, repeated.state());
        assertSame(error, repeated.failedError().orElseThrow());
        assertEquals(
                1,
                source.reads,
                "permanently failed TextReader must not consume more source input");
    }

    @Test
    void incompleteFinalSequenceFailsStrictlyAtEof()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedSource source = new ScriptedSource(activation, false);
        source.bytes(0xf0, 0x9f);
        source.eof();

        ProtosObjectValue reader =
                reader(prelude, activation, source, "UTF8", false);
        ProtosFutureValue failure = readText(reader, activation);

        assertEquals(ProtosFutureValue.State.FAILED, failure.state());
        assertErrorParent(
                prelude, failure.failedError().orElseThrow(), "EncodingError");
        assertEquals(2, source.reads);
    }

    @Test
    void underlyingFailureBeforeTextPoisonsReaderWithoutAdditionalReads()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue ioError =
                ProtosCoreErrors.newOccurrence(
                        activation, ProtosCoreErrors.StandardError.I_O_ERROR);
        ScriptedSource source = new ScriptedSource(activation, false);
        source.failure(ioError);
        source.bytes(0x41);

        ProtosObjectValue reader =
                reader(prelude, activation, source, "UTF8", false);

        ProtosFutureValue first = readText(reader, activation);
        assertEquals(ProtosFutureValue.State.FAILED, first.state());
        assertSame(ioError, first.failedError().orElseThrow());

        ProtosFutureValue second = readText(reader, activation);
        assertEquals(ProtosFutureValue.State.FAILED, second.state());
        assertSame(ioError, second.failedError().orElseThrow());
        assertEquals(1, source.reads);
    }

    @Test
    void successfulCancellationPreservesLateReadAheadForNextOrderedRead()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedSource source = new ScriptedSource(activation, false);
        Pending pending = source.pending(true);

        ProtosObjectValue reader =
                reader(prelude, activation, source, "UTF8", false);
        ProtosFutureValue cancelled = readText(reader, activation);
        assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());
        assertEquals(1, source.reads);

        assertTrue(cancelled.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());
        assertEquals(1, pending.cancelRequests);

        pending.resolve(bytes(prelude, 0x5a), activation);

        ProtosFutureValue next = readText(reader, activation);
        assertEquals("Z", stringResult(next));
        assertEquals(
                1,
                source.reads,
                "late bytes from the cancelled operation must become next logical input");
    }

    @Test
    void borrowingAndOwningCloseRespectCutoverAndReleaseObligations()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();

        ScriptedSource borrowedSource = new ScriptedSource(activation, true);
        ProtosObjectValue borrowed =
                reader(prelude, activation, borrowedSource, "UTF8", false);
        ProtosFutureValue borrowedClose = close(borrowed, activation);
        assertEquals(ProtosFutureValue.State.RESOLVED, borrowedClose.state());
        assertSame(borrowed, borrowedClose.resolvedValue().orElseThrow());
        assertEquals(0, borrowedSource.closes);

        ScriptedSource ownedSource = new ScriptedSource(activation, true);
        ProtosObjectValue owned =
                reader(prelude, activation, ownedSource, "UTF8", true);
        ProtosFutureValue firstClose = close(owned, activation);
        ProtosFutureValue secondClose = close(owned, activation);
        assertNotSame(firstClose, secondClose);
        assertEquals(ProtosFutureValue.State.RESOLVED, firstClose.state());
        assertEquals(ProtosFutureValue.State.RESOLVED, secondClose.state());
        assertEquals(1, ownedSource.closes);

        ProtosFutureValue rejected = readText(owned, activation);
        assertEquals(ProtosFutureValue.State.FAILED, rejected.state());
        assertErrorParent(
                prelude,
                rejected.failedError().orElseThrow(),
                "IOLifecycleError");
    }

    @Test
    void readerFailureDoesNotAutoCloseOwnedSourceButLaterCloseStillReleasesIt()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedSource source = new ScriptedSource(activation, true);
        source.bytes(0xc0, 0xaf);

        ProtosObjectValue reader =
                reader(prelude, activation, source, "UTF8", true);
        ProtosFutureValue failure = readText(reader, activation);
        assertEquals(ProtosFutureValue.State.FAILED, failure.state());
        assertEquals(0, source.closes);

        ProtosFutureValue close = close(reader, activation);
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertEquals(1, source.closes);
    }

    @Test
    void closeCutoverTerminatesUncommittedReadAndRequestsLowerCancellation()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedSource source = new ScriptedSource(activation, false);
        Pending pending = source.pending(true);

        ProtosObjectValue reader =
                reader(prelude, activation, source, "UTF8", false);
        ProtosFutureValue read = readText(reader, activation);
        assertEquals(ProtosFutureValue.State.PENDING, read.state());

        ProtosFutureValue close = close(reader, activation);
        assertEquals(ProtosFutureValue.State.FAILED, read.state());
        assertErrorParent(
                prelude, read.failedError().orElseThrow(), "IOLifecycleError");
        assertEquals(1, pending.cancelRequests);
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertFalse(
                close.cancelRequest(),
                "already-resolved committed close cannot be rewritten to cancelled");
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());

        pending.resolve(bytes(prelude, 0x41), activation);
        assertEquals(
                ProtosFutureValue.State.FAILED,
                readText(reader, activation).state());
    }

    @Test
    void operationArityValidationUsesFailedFutureAfterDispatch()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedSource source = new ScriptedSource(activation, false);
        ProtosObjectValue reader =
                reader(prelude, activation, source, "UTF8", false);

        ProtosFutureValue badRead =
                assertInstanceOf(
                        ProtosFutureValue.class,
                        ProtosInvocation.invokeMessage(
                                reader,
                                "readText",
                                List.of(ProtosNullValue.INSTANCE),
                                activation));
        assertEquals(ProtosFutureValue.State.FAILED, badRead.state());
        assertErrorParent(
                prelude,
                badRead.failedError().orElseThrow(),
                "InvalidIOArgument");

        assertEquals(0, source.reads);
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static ProtosObjectValue factory(ProtosPrelude prelude) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                prelude.bindings().readLocalSlot("TextReader").orElseThrow());
    }

    private static ProtosObjectValue reader(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ScriptedSource source,
            String encodingName,
            boolean owning) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                ProtosInvocation.invokeMessage(
                        factory(prelude),
                        owning ? "owning" : "call",
                        List.of(source.source, encoding(prelude, encodingName)),
                        activation));
    }

    private static ProtosEncodingValue encoding(
            ProtosPrelude prelude, String name) {
        return assertInstanceOf(
                ProtosEncodingValue.class,
                prelude.encodingPrototype().readLocalSlot(name).orElseThrow());
    }

    private static ProtosFutureValue readText(
            ProtosObjectValue reader, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(
                        reader, "readText", List.of(), activation));
    }

    private static ProtosFutureValue close(
            ProtosObjectValue reader, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(
                        reader, "close", List.of(), activation));
    }

    private static String stringResult(ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        return assertInstanceOf(
                        ProtosStringValue.class,
                        future.resolvedValue().orElseThrow())
                .value();
    }

    private static void assertErrorParent(
            ProtosPrelude prelude, ProtosObjectValue error, String parentName) {
        assertSame(
                prelude.bindings().readLocalSlot(parentName).orElseThrow(),
                error.parent().orElseThrow());
    }

    private static ProtosBytesValue bytes(
            ProtosPrelude prelude, int... values) {
        ProtosBytesValue bytes =
                new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        for (int value : values) {
            bytes.indexedAdd(
                    new ProtosIntegerValue(BigInteger.valueOf(value)));
        }
        return bytes;
    }

    private static final class Pending {
        final ProtosFutureValue future;
        int cancelRequests;

        Pending(ProtosFutureValue future, boolean ignoreCancellation) {
            this.future = future;
            future.attachCancellationProducer(
                    () -> {
                        cancelRequests++;
                        if (!ignoreCancellation) {
                            future.cancelTerminal();
                        }
                    });
        }

        void resolve(Object value, ProtosActivation activation) {
            future.resolve(value, activation);
        }
    }

    private static final class ScriptedSource {
        private sealed interface Step
                permits BytesStep, EofStep, FailureStep, PendingStep {}

        private record BytesStep(int[] values) implements Step {}
        private record EofStep() implements Step {}
        private record FailureStep(ProtosObjectValue error) implements Step {}
        private record PendingStep(Pending pending) implements Step {}

        final ProtosObjectValue source =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        final ProtosActivation constructionActivation;
        final ArrayDeque<Step> steps = new ArrayDeque<>();
        int reads;
        int closes;

        ScriptedSource(ProtosActivation activation, boolean closable) {
            constructionActivation = activation;
            source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (callActivation, supplied) -> {
                                reads++;
                                if (supplied.size() != 1) {
                                    throw new AssertionError("unexpected source read arity");
                                }
                                Step step = steps.pollFirst();
                                if (step == null) {
                                    throw new AssertionError("no scripted read step");
                                }
                                if (step instanceof PendingStep pending) {
                                    return pending.pending().future;
                                }

                                ProtosFutureValue future =
                                        new ProtosFutureValue(
                                                callActivation
                                                        .prelude()
                                                        .orElseThrow()
                                                        .futurePrototype(),
                                                callActivation.executionDomain());
                                if (step instanceof BytesStep byteStep) {
                                    future.resolve(
                                            ProtosStandardTextReaderProtocolTest.bytes(
                                                    callActivation
                                                            .prelude()
                                                            .orElseThrow(),
                                                    byteStep.values()),
                                            callActivation);
                                } else if (step instanceof EofStep) {
                                    future.resolve(
                                            ProtosNullValue.INSTANCE,
                                            callActivation);
                                } else if (step instanceof FailureStep failure) {
                                    future.fail(failure.error());
                                }
                                return future;
                            }));
            if (closable) {
                source.createLocalSlot(
                        "close",
                        ProtosClosureValue.nativeClosure(
                                (callActivation, supplied) -> {
                                    closes++;
                                    ProtosFutureValue future =
                                            new ProtosFutureValue(
                                                    callActivation
                                                            .prelude()
                                                            .orElseThrow()
                                                            .futurePrototype(),
                                                    callActivation.executionDomain());
                                    future.resolve(source, callActivation);
                                    return future;
                                }));
            }
        }

        void bytes(int... values) {
            steps.addLast(new BytesStep(values.clone()));
        }

        void eof() {
            steps.addLast(new EofStep());
        }

        void failure(ProtosObjectValue error) {
            steps.addLast(new FailureStep(error));
        }

        Pending pending(boolean ignoreCancellation) {
            Pending pending =
                    new Pending(
                            new ProtosFutureValue(
                                    constructionActivation
                                            .prelude()
                                            .orElseThrow()
                                            .futurePrototype(),
                                    constructionActivation.executionDomain()),
                            ignoreCancellation);
            steps.addLast(new PendingStep(pending));
            return pending;
        }
    }
}
