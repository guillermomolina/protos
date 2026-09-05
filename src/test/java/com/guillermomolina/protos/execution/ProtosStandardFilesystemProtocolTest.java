/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosStandardFilesystemProtocolTest {
    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue bytesPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(bytesPrototype);
        RecordingBackend backend = new RecordingBackend();
        ProtosObjectValue filesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        bytesPrototype, activation, backend);
        return new Fixture(prelude, activation, backend, filesystem);
    }

    @Test
    void capabilityIsHostProvisionedOpenOnlyAndAbsentFromCorePrelude() throws Exception {
        Fixture x = fixture();
        assertTrue(x.prelude.bindings().readLocalSlot("Filesystem").isEmpty());
        assertTrue(x.filesystem instanceof ProtosFilesystemValue);
        assertEquals(1, x.filesystem.localSlotsSnapshot().size());
        assertTrue(x.filesystem.hasLocalSlot("open"));
        assertSame(ProtosObjectValue.rootObject(), x.filesystem.parent().orElseThrow());
    }

    @Test
    void defaultOpenMaterializesExactReadFileCapability() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future =
                (ProtosFutureValue)
                        ProtosInvocation.invokeMessage(
                                x.filesystem,
                                "open",
                                List.of(path(x.prelude, "data.bin")),
                                x.activation);

        Invocation invocation = x.backend.invocations.remove();
        assertTrue(invocation.options.readAccess());
        assertFalse(invocation.options.writeAccess());
        assertEquals(
                ProtosFilesystemOpenOptions.Creation.EXISTING,
                invocation.options.creation());
        assertEquals(
                ProtosFilesystemOpenOptions.Placement.POSITIONED,
                invocation.options.placement());

        AtomicInteger releases = new AtomicInteger();
        invocation.completion.succeeded(
                new ReadResource(),
                new ProtosFileFlow.Capabilities(
                        true, false, false, false, false, false),
                releases::incrementAndGet);

        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        ProtosObjectValue file =
                (ProtosObjectValue) future.resolvedValue().orElseThrow();
        assertTrue(file instanceof ProtosFileValue);
        assertTrue(file.hasLocalSlot("read"));
        assertTrue(file.hasLocalSlot("close"));
        assertFalse(file.hasLocalSlot("write"));
        assertFalse(file.hasLocalSlot("flush"));
        assertEquals(0, releases.get());
    }

    @Test
    void appendOpenMaterializesAppendFileWithZeroInitialLogicalPosition() throws Exception {
        Fixture x = fixture();
        ProtosObjectValue options = ordinaryOptions();
        options.createLocalSlot("read", ProtosBooleanValue.FALSE);
        options.createLocalSlot("write", ProtosBooleanValue.TRUE);
        options.createLocalSlot("append", ProtosBooleanValue.TRUE);

        ProtosFutureValue future =
                (ProtosFutureValue)
                        ProtosInvocation.invokeMessage(
                                x.filesystem,
                                "open",
                                List.of(path(x.prelude, "log.bin"), options),
                                x.activation);

        Invocation invocation = x.backend.invocations.remove();
        assertEquals(
                ProtosFilesystemOpenOptions.Placement.APPEND,
                invocation.options.placement());

        invocation.completion.succeeded(
                new AppendSeekResource(),
                new ProtosFileFlow.Capabilities(
                        false, true, true, false, false, false, true),
                () -> {});

        ProtosObjectValue file =
                (ProtosObjectValue) future.resolvedValue().orElseThrow();
        ProtosFutureValue position =
                (ProtosFutureValue)
                        ProtosInvocation.invokeMessage(
                                file, "position", List.of(), x.activation);
        assertEquals(
                BigInteger.ZERO,
                ((ProtosIntegerValue) position.resolvedValue().orElseThrow()).value());
    }

    @Test
    void invalidAndSpecializedOptionsFailBeforeBackendAuthority() throws Exception {
        Fixture x = fixture();

        ProtosObjectValue noAccess = ordinaryOptions();
        noAccess.createLocalSlot("read", ProtosBooleanValue.FALSE);
        ProtosFutureValue invalid =
                (ProtosFutureValue)
                        ProtosInvocation.invokeMessage(
                                x.filesystem,
                                "open",
                                List.of(path(x.prelude, "a"), noAccess),
                                x.activation);
        assertInvalid(x, invalid);

        ProtosBytesValue specialized =
                new ProtosBytesValue(new ProtosObjectValue(ProtosObjectValue.rootObject()));
        ProtosFutureValue nonOrdinary =
                (ProtosFutureValue)
                        ProtosInvocation.invokeMessage(
                                x.filesystem,
                                "open",
                                List.of(path(x.prelude, "b"), specialized),
                                x.activation);
        assertInvalid(x, nonOrdinary);

        assertTrue(x.backend.invocations.isEmpty());
    }

    @Test
    void mismatchedBackendAuthorityIsReleasedAndNeverExposedAsFile() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future =
                (ProtosFutureValue)
                        ProtosInvocation.invokeMessage(
                                x.filesystem,
                                "open",
                                List.of(path(x.prelude, "data.bin")),
                                x.activation);
        Invocation invocation = x.backend.invocations.remove();
        AtomicInteger releases = new AtomicInteger();

        invocation.completion.succeeded(
                new PositionedWriteResource(),
                new ProtosFileFlow.Capabilities(
                        false, true, false, false, false, false),
                releases::incrementAndGet);

        assertEquals(1, releases.get());
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.bindings().readLocalSlot("IOError").orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private static void assertInvalid(Fixture x, ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.bindings().readLocalSlot("InvalidIOArgument").orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private static ProtosObjectValue ordinaryOptions() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static ProtosPathValue path(ProtosPrelude prelude, String name) {
        return new ProtosPathValue(
                prelude.pathPrototype(),
                false,
                List.of(new ProtosPathValue.Normal(name)));
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            RecordingBackend backend,
            ProtosObjectValue filesystem) {}

    private static final class RecordingBackend
            implements ProtosStandardFilesystemProtocol.Backend {
        private final ArrayDeque<Invocation> invocations = new ArrayDeque<>();

        @Override
        public ProtosFilesystemOpenFlow.Cancellation open(
                ProtosPathValue path,
                ProtosFilesystemOpenOptions options,
                ProtosStandardFilesystemProtocol.OpenCompletion completion) {
            Invocation invocation = new Invocation(path, options, completion);
            invocations.add(invocation);
            return invocation.cancellations::incrementAndGet;
        }
    }

    private static final class Invocation {
        private final ProtosPathValue path;
        private final ProtosFilesystemOpenOptions options;
        private final ProtosStandardFilesystemProtocol.OpenCompletion completion;
        private final AtomicInteger cancellations = new AtomicInteger();

        private Invocation(
                ProtosPathValue path,
                ProtosFilesystemOpenOptions options,
                ProtosStandardFilesystemProtocol.OpenCompletion completion) {
            this.path = path;
            this.options = options;
            this.completion = completion;
        }
    }

    private static final class ReadResource implements ProtosFileFlow.ReadableResource {
        @Override
        public ProtosFileFlow.Cancellation readAt(
                BigInteger position, int maxBytes, ProtosFileFlow.ReadCompletion completion) {
            completion.eof();
            return () -> {};
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            completion.succeeded();
        }
    }

    private static final class PositionedWriteResource
            implements ProtosFileFlow.WritableResource {
        @Override
        public ProtosFileFlow.Cancellation writeAt(
                BigInteger position, byte[] bytes, ProtosFileFlow.WriteCompletion completion) {
            return () -> {};
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            completion.succeeded();
        }
    }

    private static final class AppendSeekResource
            implements ProtosFileFlow.AppendWritableResource, ProtosFileFlow.SeekableResource {
        @Override
        public ProtosFileFlow.Cancellation append(
                byte[] bytes, ProtosFileFlow.AppendCompletion completion) {
            return () -> {};
        }

        @Override
        public ProtosFileFlow.Cancellation endPosition(
                ProtosFileFlow.IntegerCompletion completion) {
            completion.succeeded(BigInteger.valueOf(17));
            return () -> {};
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            completion.succeeded();
        }
    }
}
