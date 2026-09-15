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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosStandaloneProcessBootstrap;
import com.guillermomolina.protos.execution.ProtosTestExecutionSupport;
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * DIST003-D1 regression for the Test Tool stdout contract.
 *
 * <p>The bundled Test Tool module must complete both stdout marker writes before it returns its
 * final outcome; otherwise the CLI terminates the session and the pending writes are lost. The
 * exact awaited statement shape is exercised below through the same host machinery the production
 * module uses without recursively executing the repository Protos corpus from the Java test lane.
 */
final class ProtosTestToolStdoutCompletionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final String BOOTSTRAP_MARKER = "Protos test tool bootstrap";
    private static final String EXPECTED_STDOUT = BOOTSTRAP_MARKER + "\ntest\n";

    /*
     * The exact final statements of protos/tools/test/Main.protos: construct the stdout TextWriter,
     * await both marker writes, then yield the module value. Keeping the awaited write shape here
     * lets the completion and failure mechanics be exercised without rerunning the full corpus.
     */
    private static final String MARKER_TAIL =
            """
            writer: TextWriter(process.stdout(), process.stdoutEncoding())
            writer.writeLine("Protos test tool bootstrap").value()
            writer.writeLine(process.args()[0]).value()
            null
            """;

    private static final ProtosEnvironmentValue.NativeNameDomain EXACT_ENVIRONMENT_DOMAIN =
            new ProtosEnvironmentValue.NativeNameDomain() {
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

    @Test
    void moduleCannotTerminateBeforeMarkerWritesComplete() throws Exception {
        GatedWriteBackend stdout = new GatedWriteBackend();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ProtosExecutionOutcome> pending =
                    executor.submit(() -> executeMarkerTail(stdout));

            assertTrue(
                    stdout.firstWriteStarted.await(30, TimeUnit.SECONDS),
                    "first marker write never reached the stdout backend");
            // Only the test holds the write completion: while it is pending, the module must
            // remain suspended in its awaited write and cannot return its final outcome.
            assertFalse(pending.isDone(), "module terminated before its marker writes completed");

            stdout.release();

            ProtosExecutionOutcome outcome = pending.get(30, TimeUnit.SECONDS);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "marker tail outcome=" + outcome.state() + ", error=" + outcome.error());
            assertEquals(EXPECTED_STDOUT, stdout.captured(StandardCharsets.UTF_8));
        } finally {
            stdout.release();
            executor.shutdownNow();
        }
    }

    @Test
    void markerWriteFailureIsNotSilentlyDiscarded() throws Exception {
        ProtosExecutionOutcome outcome = executeMarkerTail(new FailingWriteBackend());

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
        assertNotNull(outcome.error(), "failed marker write must carry the stored Error");
    }

    private static ProtosExecutionOutcome executeMarkerTail(
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend)
            throws IOException {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosEncodingValue utf8 =
                (ProtosEncodingValue)
                        prelude.encodingPrototype()
                                .readLocalSlot("UTF8")
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Core Encoding.UTF8 is missing"));
        ProtosStandaloneProcessBootstrap.Result process =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of("test"),
                        EXACT_ENVIRONMENT_DOMAIN,
                        List.of(),
                        null,
                        stdoutBackend,
                        null,
                        utf8,
                        utf8,
                        utf8,
                        null);
        return ProtosTestExecutionSupport.execute(MARKER_TAIL, process.activation());
    }

    private static final class GatedWriteBackend
            implements ProtosProcessStandardStreamBinding.WritableBackend {
        private final Object lock = new Object();
        private final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private final List<ProtosByteIoFlow.WriteCompletion> pending = new ArrayList<>();
        private boolean released;

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            captured.writeBytes(bytes);
            firstWriteStarted.countDown();
            synchronized (lock) {
                if (released) {
                    completion.succeeded();
                } else {
                    pending.add(completion);
                }
            }
            return () -> {};
        }

        void release() {
            synchronized (lock) {
                released = true;
                for (ProtosByteIoFlow.WriteCompletion completion : pending) {
                    completion.succeeded();
                }
                pending.clear();
            }
        }

        String captured(Charset charset) {
            return captured.toString(charset);
        }
    }

    private static final class FailingWriteBackend
            implements ProtosProcessStandardStreamBinding.WritableBackend {
        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            completion.failed(0);
            return () -> {};
        }
    }
}
