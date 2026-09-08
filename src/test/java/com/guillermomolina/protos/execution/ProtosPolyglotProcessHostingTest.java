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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

class ProtosPolyglotProcessHostingTest {
    @Test
    void twoProcessesUseDistinctContextsOnOneExplicitSharedEngine() {
        ProtosProcessRuntime first = process();
        ProtosProcessRuntime second = process();

        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPolyglotProcessContext firstContext = host(host, first);
            ProtosPolyglotProcessContext secondContext = host(host, second);

            assertSame(host.engineForTesting(), firstContext.engineForTesting());
            assertSame(host.engineForTesting(), secondContext.engineForTesting());
            assertNotSame(
                    firstContext.currentLanguageContextForTesting(),
                    secondContext.currentLanguageContextForTesting());
            assertEquals(2, host.activeProcessContextCountForTesting());

            first.requestTerminationForRuntime();
            second.requestTerminationForRuntime();
            assertEquals(0, host.activeProcessContextCountForTesting());
        }
    }

    @Test
    void terminatingOneProcessClosesOnlyItsOwnContext() {
        ProtosProcessRuntime first = process();
        ProtosProcessRuntime second = process();

        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPolyglotProcessContext firstContext = host(host, first);
            ProtosPolyglotProcessContext secondContext = host(host, second);

            first.requestTerminationForRuntime();

            assertTrue(firstContext.isClosedForTesting());
            assertFalse(secondContext.isClosedForTesting());
            secondContext.currentLanguageContextForTesting();
            assertEquals(1, host.activeProcessContextCountForTesting());
            assertThrows(IllegalStateException.class, host::close);

            second.requestTerminationForRuntime();
            assertTrue(secondContext.isClosedForTesting());
        }
    }

    @Test
    void terminationInsideEnteredProcessContextDefersPhysicalCloseUntilCarrierLeaves() {
        ProtosProcessRuntime process = process();

        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPolyglotProcessContext context = host(host, process);

            context.callForRuntime(
                    () -> {
                        assertFalse(context.isClosedForTesting());
                        process.requestTerminationForRuntime();
                        assertFalse(context.isClosedForTesting());
                        assertThrows(
                                IllegalStateException.class,
                                context::currentLanguageContextForTesting);
                        return null;
                    });

            assertTrue(context.isClosedForTesting());
            assertEquals(0, host.activeProcessContextCountForTesting());
        }
    }

    @Test
    void processHostBindingIsFixedAndFailedRebindingReleasesSecondContext() {
        ProtosProcessRuntime process = process();

        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPolyglotProcessContext first = host(host, process);
            assertEquals(1, host.activeProcessContextCountForTesting());

            assertThrows(IllegalStateException.class, () -> host(host, process));
            assertEquals(1, host.activeProcessContextCountForTesting());
            first.currentLanguageContextForTesting();

            process.requestTerminationForRuntime();
            assertEquals(0, host.activeProcessContextCountForTesting());
        }
    }

    @Test
    void runtimeHostRefusesToCloseLiveProcessContextsInsteadOfTerminatingProcesses() {
        ProtosProcessRuntime process = process();
        ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open();
        host(host, process);

        assertThrows(IllegalStateException.class, host::close);
        assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, process.lifecycleState());

        process.requestTerminationForRuntime();
        host.close();
    }

    private static ProtosProcessRuntime process() {
        return new ProtosProcessRuntime(new ProtosObjectValue(ProtosObjectValue.rootObject()));
    }

    private static ProtosPolyglotProcessContext host(
            ProtosPolyglotRuntimeHost host, ProtosProcessRuntime process) {
        return host.hostProcess(
                process,
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream());
    }
}
