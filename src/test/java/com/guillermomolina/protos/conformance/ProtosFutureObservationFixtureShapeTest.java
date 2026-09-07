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
package com.guillermomolina.protos.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.execution.ProtosClosureInvoker;
import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosSourceFileLoader;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosFutureObservationFixtureShapeTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "conformance",
                    "future",
                    "failed-value-resignals-recorded-error.protos");

    @Test
    void retainedStoredFixtureSignalsSameLocalErrorTwiceAfterTerminalProgress()
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(
                                        STANDARD_LIBRARY));
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue fixture =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        new ProtosSourceFileLoader().load(FIXTURE).call(activation));
        ProtosFutureValue future =
                assertInstanceOf(
                        ProtosFutureValue.class,
                        fixture.readLocalSlot("future").orElseThrow());
        ProtosObjectValue error =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        fixture.readLocalSlot("error").orElseThrow());
        ProtosClosureValue observe =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        fixture.readLocalSlot("observe").orElseThrow());

        awaitTerminal(future, activation);
        assertEquals(ProtosFutureValue.State.FAILED, future.state());

        ProtosSignalException first =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosClosureInvoker.invoke(observe, List.of(), activation));
        ProtosSignalException second =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosClosureInvoker.invoke(observe, List.of(), activation));

        assertSame(error, first.error());
        assertSame(error, second.error());
        assertSame(first.error(), second.error());
    }

    @Test
    void retainedStoredFixtureSignalsItsLocalErrorOnceAfterTerminalProgress()
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(
                                        STANDARD_LIBRARY));
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue fixture =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        new ProtosSourceFileLoader().load(FIXTURE).call(activation));
        ProtosFutureValue future =
                assertInstanceOf(
                        ProtosFutureValue.class,
                        fixture.readLocalSlot("future").orElseThrow());
        ProtosObjectValue error =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        fixture.readLocalSlot("error").orElseThrow());
        ProtosClosureValue observe =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        fixture.readLocalSlot("observe").orElseThrow());

        awaitTerminal(future, activation);
        assertEquals(ProtosFutureValue.State.FAILED, future.state());

        ProtosSignalException observed =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosClosureInvoker.invoke(observe, List.of(), activation));

        assertSame(error, observed.error());
    }

    @Test
    void retainedStoredFixtureHasExactLocalShapeWithoutObservation() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(
                                        STANDARD_LIBRARY));
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue fixture =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        new ProtosSourceFileLoader().load(FIXTURE).call(activation));

        assertEquals(
                Set.of("future", "error", "observe"),
                fixture.localSlotsSnapshot().keySet());

        assertInstanceOf(
                ProtosFutureValue.class,
                fixture.readLocalSlot("future").orElseThrow());
        assertInstanceOf(
                ProtosObjectValue.class,
                fixture.readLocalSlot("error").orElseThrow());

        ProtosClosureValue observe =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        fixture.readLocalSlot("observe").orElseThrow());
        assertNotNull(observe.definition());
        assertEquals(0, observe.definition().parameters().size());
    }

    private static void awaitTerminal(
            ProtosFutureValue future, ProtosActivation activation) {
        int dispatches = 0;
        while (future.state() == ProtosFutureValue.State.PENDING) {
            if (!activation.executionDomain().dispatchOne()) {
                throw new AssertionError(
                        "retained Future is pending with no runnable work");
            }
            dispatches++;
            if (dispatches > 100000) {
                throw new AssertionError(
                        "retained Future exceeded bounded terminal progress");
            }
        }
    }
}
