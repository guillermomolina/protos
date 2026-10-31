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

import com.guillermomolina.protos.runtime.ProtosNullValue;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

final class ProtosReplLexicalObservationTest {
    private static ProtosHostedExecutionTestFixture open()
            throws Exception {
        return ProtosHostedExecutionTestFixture.open(
                new ProtosCoreBootstrap().bootstrap(
                        Path.of("protos", "lib", "core")));
    }

    private static ProtosPolyglotProcessContext context(
            ProtosHostedExecutionTestFixture hosted) {
        return (ProtosPolyglotProcessContext)
                hosted.process()
                        .executionHostForRuntime()
                        .orElseThrow();
    }

    @Test
    void reflectsCreationRemovalAndRecreation() throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);
            var activation = hosted.activation();

            assertFalse(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("sessionVariable"));

            hosted.evaluatePersistent(
                    "<d227-create>", "sessionVariable: 1");

            assertTrue(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("sessionVariable"));

            hosted.callEntered(() -> {
                activation.context().removeLocalSlot("sessionVariable");
                return null;
            });

            assertFalse(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("sessionVariable"));

            hosted.callEntered(() -> {
                activation.context().createLocalSlot(
                        "sessionVariable", ProtosNullValue.INSTANCE);
                return null;
            });

            assertTrue(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("sessionVariable"));
        }
    }

    @Test
    void contentionFailsClosedWithoutWaiting() throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);

            Field field = ProtosPolyglotProcessContext.class
                    .getDeclaredField("persistentRootActorGate");
            field.setAccessible(true);
            ReentrantLock gate =
                    (ReentrantLock) field.get(ctx);

            var executor = Executors.newSingleThreadExecutor();

            gate.lock();
            try {
                var result = executor.submit(() ->
                        ctx.tryObservePersistentLexicalNamesForRuntime(
                                hosted.activation()));

                assertEquals(
                        List.of(),
                        result.get(5, TimeUnit.SECONDS));
            } finally {
                gate.unlock();
                executor.shutdownNow();
            }

            assertFalse(ctx
                    .tryObservePersistentLexicalNamesForRuntime(
                            hosted.activation())
                    .isEmpty());
        }
    }

    @Test
    void closedProcessReturnsNoNames() throws Exception {
        var hosted = open();
        var ctx = context(hosted);
        var activation = hosted.activation();

        try {
            hosted.evaluatePersistent(
                    "<d227-before-close>", "liveBeforeClose: 1");

            assertTrue(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("liveBeforeClose"));
        } finally {
            hosted.close();
        }

        assertEquals(
                List.of(),
                ctx.tryObservePersistentLexicalNamesForRuntime(activation));
    }

    @Test
    void foreignActivationIsRejected() throws Exception {
        try (var first = open(); var second = open()) {
            assertEquals(
                    List.of(),
                    context(first)
                            .tryObservePersistentLexicalNamesForRuntime(
                                    second.activation()));
        }
    }
}
