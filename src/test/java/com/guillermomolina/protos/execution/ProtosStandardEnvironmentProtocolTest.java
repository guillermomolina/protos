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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosStandardEnvironmentProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    /**
     * Deliberately Java-side: native name identity/representability and invalid
     * native Unicode cannot be manufactured by ordinary Protos source. The same
     * host-side test also preserves the whole-snapshot prevalidation cutover.
     */
    @Test
    void nativeDomainDecodeTimingAndEnumerationPrevalidationStayHostSide()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();
        AtomicInteger queryMatches = new AtomicInteger();
        ProtosEnvironmentValue.NativeNameDomain domain =
                new ProtosEnvironmentValue.NativeNameDomain() {
                    @Override
                    public boolean sameCapturedName(String left, String right) {
                        return left.equalsIgnoreCase(right);
                    }

                    @Override
                    public boolean isQueryRepresentable(String name) {
                        return !name.contains("=") && name.indexOf('\0') < 0;
                    }

                    @Override
                    public boolean matchesQuery(String captured, String query) {
                        queryMatches.incrementAndGet();
                        return captured.equalsIgnoreCase(query);
                    }
                };

        String invalidUnicode = String.valueOf((char) 0xD800);
        ProtosEnvironmentValue environment =
                environment(
                        prototype,
                        domain,
                        List.of(
                                new ProtosEnvironmentValue.NativeEntry("Path", "ok"),
                                new ProtosEnvironmentValue.NativeEntry(
                                        "BROKEN", invalidUnicode)));

        Object path =
                ProtosInvocation.invokeMessage(
                        environment,
                        "get",
                        List.of(new ProtosStringValue("pAtH")),
                        activation);
        assertEquals("ok", ((ProtosStringValue) path).value());

        Object missing =
                ProtosInvocation.invokeMessage(
                        environment,
                        "get",
                        List.of(new ProtosStringValue("MISSING")),
                        activation);
        assertSame(ProtosNullValue.INSTANCE, missing);

        Object brokenPresent =
                ProtosInvocation.invokeMessage(
                        environment,
                        "contains",
                        List.of(new ProtosStringValue("broken")),
                        activation);
        assertSame(ProtosBooleanValue.TRUE, brokenPresent);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                environment,
                                "get",
                                List.of(new ProtosStringValue("broken")),
                                activation));

        queryMatches.set(0);
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                environment,
                                "contains",
                                List.of(new ProtosStringValue("bad=name")),
                                activation));
        assertEquals(0, queryMatches.get());

        ProtosEnvironmentValue invalidEnumeration =
                environment(
                        prototype,
                        exactDomain(),
                        List.of(
                                new ProtosEnvironmentValue.NativeEntry("A", "ok"),
                                new ProtosEnvironmentValue.NativeEntry(
                                        "B", invalidUnicode)));
        AtomicInteger callbacks = new AtomicInteger();
        ProtosClosureValue callback =
                ProtosClosureValue.nativeClosure(
                        (callActivation, supplied) -> {
                            callbacks.incrementAndGet();
                            return ProtosNullValue.INSTANCE;
                        });

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                invalidEnumeration,
                                "each",
                                List.of(callback),
                                activation));
        assertEquals(0, callbacks.get());
    }

    @Test
    void environmentPrototypeIsConstructionOnlyAndNotMapLike() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();

        assertSame(ProtosObjectValue.rootObject(), prototype.parent().orElseThrow());
        assertEquals(
                java.util.Set.of("get", "contains", "each"),
                prototype.localSlotsSnapshot().keySet());
        assertTrue(prototype.isFrozen());
        assertTrue(prelude.bindings().readLocalSlot("Environment").isEmpty());
        assertNotSame(prelude.mapPrototype(), prototype);
    }

    private static ProtosEnvironmentValue environment(
            ProtosObjectValue prototype,
            ProtosEnvironmentValue.NativeNameDomain domain,
            List<ProtosEnvironmentValue.NativeEntry> entries) {
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
        assertEquals(
                ProtosProcessRuntime.EnvironmentSnapshotState.AVAILABLE,
                process.establishEnvironmentForRuntime(prototype, domain, entries));
        return process.environmentSnapshotForRuntime().orElseThrow();
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactDomain() {
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
