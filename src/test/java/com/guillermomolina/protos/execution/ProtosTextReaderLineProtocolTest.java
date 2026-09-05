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
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosTextReaderLineProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void framesLfCrLfCrAndEofFinalLineInOneOrderedDomain() throws Exception {
        Fixture f = fixture("UTF8");
        f.source.bytes(
                'a', '\n',
                'b', '\r', '\n',
                'c', '\r',
                'd');
        f.source.eof();

        assertEquals("a", stringResult(readLine(f.reader, f.activation)));
        assertEquals("b", stringResult(readLine(f.reader, f.activation)));
        assertEquals("c", stringResult(readLine(f.reader, f.activation)));
        assertEquals("d", stringResult(readLine(f.reader, f.activation)));
        assertNullResult(readLine(f.reader, f.activation));
        assertEquals(2, f.source.reads);
    }

    @Test
    void crCompletesImmediatelyAndFollowingLfIsFoldedByNextOperation() throws Exception {
        Fixture f = fixture("UTF8");
        f.source.bytes('a', '\r');
        Pending pending = f.source.pending(true);

        ProtosFutureValue first = readLine(f.reader, f.activation);
        assertEquals("a", stringResult(first));
        assertEquals(
                1,
                f.source.reads,
                "a terminating CR must not wait for another backend read");

        ProtosFutureValue second = readLine(f.reader, f.activation);
        assertEquals(ProtosFutureValue.State.PENDING, second.state());
        assertEquals(2, f.source.reads);

        pending.resolve(bytes(f.prelude, '\n', 'B', '\n'), f.activation);
        assertEquals("B", stringResult(second));
    }

    @Test
    void maxBytesCountsEncodedContentAndExcludesInitialBomAndTerminator() throws Exception {
        Fixture utf8Exact = fixture("UTF8");
        utf8Exact.source.bytes(0xef, 0xbb, 0xbf, 0xc3, 0xa9, '\n');
        assertEquals(
                "é",
                stringResult(
                        readLine(
                                utf8Exact.reader,
                                utf8Exact.activation,
                                new ProtosIntegerValue(BigInteger.valueOf(2)))));

        Fixture utf8Short = fixture("UTF8");
        utf8Short.source.bytes(0xef, 0xbb, 0xbf, 0xc3, 0xa9, '\n');
        ProtosFutureValue tooLong =
                readLine(
                        utf8Short.reader,
                        utf8Short.activation,
                        new ProtosIntegerValue(BigInteger.ONE));
        assertEquals(ProtosFutureValue.State.FAILED, tooLong.state());
        assertErrorParent(
                utf8Short.prelude,
                tooLong.failedError().orElseThrow(),
                "LineTooLong");

        Fixture utf16 = fixture("UTF16LE");
        utf16.source.bytes(0xff, 0xfe, 0x41, 0x00, 0x0d, 0x00, 0x0a, 0x00);
        assertEquals(
                "A",
                stringResult(
                        readLine(
                                utf16.reader,
                                utf16.activation,
                                new ProtosIntegerValue(BigInteger.valueOf(2)))));
    }

    @Test
    void lineTooLongWinsBeforeLaterMalformedInputAndPermanentlyFailsReader()
            throws Exception {
        Fixture f = fixture("UTF8");
        f.source.bytes('a', 'b', 0xc0, 0xaf);

        ProtosFutureValue first =
                readLine(
                        f.reader,
                        f.activation,
                        new ProtosIntegerValue(BigInteger.ONE));
        assertEquals(ProtosFutureValue.State.FAILED, first.state());
        ProtosObjectValue lineError = first.failedError().orElseThrow();
        assertErrorParent(f.prelude, lineError, "LineTooLong");

        ProtosFutureValue repeated = readLine(f.reader, f.activation);
        assertEquals(ProtosFutureValue.State.FAILED, repeated.state());
        assertSame(lineError, repeated.failedError().orElseThrow());
        assertEquals(1, f.source.reads);

        Fixture malformed = fixture("UTF8");
        malformed.source.bytes('a', 'b', 0xc0, 0xaf);
        ProtosFutureValue decoding =
                readLine(
                        malformed.reader,
                        malformed.activation,
                        new ProtosIntegerValue(BigInteger.valueOf(2)));
        assertEquals(ProtosFutureValue.State.FAILED, decoding.state());
        assertErrorParent(
                malformed.prelude,
                decoding.failedError().orElseThrow(),
                "EncodingError");
    }

    @Test
    void partialLineIsNotReturnedWhenUnderlyingFailurePrecedesTerminator()
            throws Exception {
        Fixture f = fixture("UTF8");
        ProtosObjectValue ioError =
                ProtosCoreErrors.newOccurrence(
                        f.activation, ProtosCoreErrors.StandardError.I_O_ERROR);
        f.source.bytes('a');
        f.source.failure(ioError);

        ProtosFutureValue line = readLine(f.reader, f.activation);
        assertEquals(ProtosFutureValue.State.FAILED, line.state());
        assertSame(ioError, line.failedError().orElseThrow());

        ProtosFutureValue repeated = readLine(f.reader, f.activation);
        assertEquals(ProtosFutureValue.State.FAILED, repeated.state());
        assertSame(ioError, repeated.failedError().orElseThrow());
        assertEquals(2, f.source.reads);
    }

    @Test
    void successfulCancellationPreservesWholeLineForNextOperation() throws Exception {
        Fixture f = fixture("UTF8");
        Pending pending = f.source.pending(true);

        ProtosFutureValue cancelled = readLine(f.reader, f.activation);
        assertEquals(ProtosFutureValue.State.PENDING, cancelled.state());
        assertTrue(cancelled.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, cancelled.state());

        pending.resolve(bytes(f.prelude, 'A', '\n'), f.activation);

        assertEquals("A", stringResult(readLine(f.reader, f.activation)));
        assertEquals(
                1,
                f.source.reads,
                "late read-ahead from cancelled line read must be retained");
    }

    @Test
    void readTextAndReadLineShareTheSameInvocationOrderedQueue() throws Exception {
        Fixture f = fixture("UTF8");
        Pending pending = f.source.pending(true);

        ProtosFutureValue line = readLine(f.reader, f.activation);
        ProtosFutureValue text = readText(f.reader, f.activation);
        assertEquals(ProtosFutureValue.State.PENDING, line.state());
        assertEquals(ProtosFutureValue.State.PENDING, text.state());
        assertEquals(1, f.source.reads);

        pending.resolve(bytes(f.prelude, 'A', '\n', 'B'), f.activation);

        assertEquals("A", stringResult(line));
        assertEquals("B", stringResult(text));
        assertEquals(1, f.source.reads);
    }

    @Test
    void invalidBoundFailsAsFutureBeforeUnderlyingIo() throws Exception {
        Fixture f = fixture("UTF8");

        ProtosFutureValue zero =
                readLine(
                        f.reader,
                        f.activation,
                        new ProtosIntegerValue(BigInteger.ZERO));
        assertInvalid(f, zero);

        ProtosFutureValue negative =
                readLine(
                        f.reader,
                        f.activation,
                        new ProtosIntegerValue(BigInteger.valueOf(-1)));
        assertInvalid(f, negative);

        ProtosFutureValue wrongType =
                readLine(f.reader, f.activation, new ProtosStringValue("1"));
        assertInvalid(f, wrongType);

        ProtosFutureValue wrongArity =
                assertInstanceOf(
                        ProtosFutureValue.class,
                        ProtosInvocation.invokeMessage(
                                f.reader,
                                "readLine",
                                List.of(
                                        new ProtosIntegerValue(BigInteger.ONE),
                                        new ProtosIntegerValue(BigInteger.ONE)),
                                f.activation));
        assertInvalid(f, wrongArity);

        assertEquals(0, f.source.reads);
    }

    private static void assertInvalid(Fixture f, ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertErrorParent(
                f.prelude,
                future.failedError().orElseThrow(),
                "InvalidIOArgument");
    }

    private static Fixture fixture(String encodingName) throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ScriptedSource source = new ScriptedSource(activation);
        ProtosObjectValue factory =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        prelude.bindings().readLocalSlot("TextReader").orElseThrow());
        ProtosEncodingValue encoding =
                assertInstanceOf(
                        ProtosEncodingValue.class,
                        prelude.encodingPrototype()
                                .readLocalSlot(encodingName)
                                .orElseThrow());
        ProtosObjectValue reader =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosInvocation.invokeMessage(
                                factory,
                                "call",
                                List.of(source.source, encoding),
                                activation));
        return new Fixture(prelude, activation, source, reader);
    }

    private static ProtosFutureValue readLine(
            ProtosObjectValue reader, ProtosActivation activation, Object... arguments) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(
                        reader,
                        "readLine",
                        List.of(arguments),
                        activation));
    }

    private static ProtosFutureValue readText(
            ProtosObjectValue reader, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invokeMessage(
                        reader, "readText", List.of(), activation));
    }

    private static String stringResult(ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        return assertInstanceOf(
                        ProtosStringValue.class,
                        future.resolvedValue().orElseThrow())
                .value();
    }

    private static void assertNullResult(ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertSame(ProtosNullValue.INSTANCE, future.resolvedValue().orElseThrow());
    }

    private static void assertErrorParent(
            ProtosPrelude prelude, ProtosObjectValue error, String parentName) {
        assertSame(
                prelude.bindings().readLocalSlot(parentName).orElseThrow(),
                error.parent().orElseThrow());
    }

    private static ProtosBytesValue bytes(ProtosPrelude prelude, int... values) {
        ProtosBytesValue bytes =
                new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        for (int value : values) {
            bytes.indexedAdd(
                    new ProtosIntegerValue(
                            BigInteger.valueOf(value & 0xff)));
        }
        return bytes;
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ScriptedSource source,
            ProtosObjectValue reader) {}

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

        ScriptedSource(ProtosActivation activation) {
            constructionActivation = activation;
            source.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (callActivation, supplied) -> {
                                reads++;
                                if (supplied.size() != 1) {
                                    throw new AssertionError(
                                            "unexpected source read arity");
                                }
                                Step step = steps.pollFirst();
                                if (step == null) {
                                    throw new AssertionError(
                                            "no scripted read step");
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
                                if (step instanceof BytesStep bytes) {
                                    future.resolve(
                                            ProtosTextReaderLineProtocolTest.bytes(
                                                    callActivation
                                                            .prelude()
                                                            .orElseThrow(),
                                                    bytes.values()),
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
