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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosStandardEnvironmentProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void getAndContainsRespectNativeIdentityRepresentabilityAndDecodeTiming() throws Exception {
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
                                new ProtosEnvironmentValue.NativeEntry("BROKEN", invalidUnicode)));

        Object path =
                ProtosInvocation.invokeMessage(
                        environment, "get", List.of(new ProtosStringValue("pAtH")), activation);
        assertEquals("ok", ((ProtosStringValue) path).value());

        Object missing =
                ProtosInvocation.invokeMessage(
                        environment, "get", List.of(new ProtosStringValue("MISSING")), activation);
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
    }

    @Test
    void eachPrevalidatesEveryPairBeforeCallbacksAndReturnsReceiver() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();
        String invalidUnicode = String.valueOf((char) 0xD800);

        ProtosEnvironmentValue invalid =
                environment(
                        prototype,
                        exactDomain(),
                        List.of(
                                new ProtosEnvironmentValue.NativeEntry("A", "ok"),
                                new ProtosEnvironmentValue.NativeEntry("B", invalidUnicode)));

        AtomicInteger calls = new AtomicInteger();
        ProtosClosureValue callback =
                ProtosClosureValue.nativeClosure(
                        (callActivation, supplied) -> {
                            calls.incrementAndGet();
                            return ProtosNullValue.INSTANCE;
                        });

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(invalid, "each", List.of(callback), activation));
        assertEquals(0, calls.get());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                invalid,
                                "each",
                                List.of(new ProtosIntegerValue(java.math.BigInteger.ONE)),
                                activation));
        assertEquals(0, calls.get());

        ProtosEnvironmentValue empty = environment(prototype, exactDomain(), List.of());
        Object result = ProtosInvocation.invokeMessage(empty, "each", List.of(callback), activation);
        assertSame(empty, result);
        assertEquals(0, calls.get());
    }

    @Test
    void eachUsesCanonicalUnicodeScalarNameOrder() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();

        String bmp = String.valueOf((char) 0xE000);
        String supplementary = new String(Character.toChars(0x10000));

        ProtosEnvironmentValue environment =
                environment(
                        prototype,
                        exactDomain(),
                        List.of(
                                new ProtosEnvironmentValue.NativeEntry(supplementary, "second"),
                                new ProtosEnvironmentValue.NativeEntry("A", "first"),
                                new ProtosEnvironmentValue.NativeEntry(bmp, "middle")));

        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        ProtosClosureValue callback =
                ProtosClosureValue.nativeClosure(
                        (callActivation, supplied) -> {
                            names.add(((ProtosStringValue) supplied.get(0)).value());
                            return ProtosNullValue.INSTANCE;
                        });

        Object result =
                ProtosInvocation.invokeMessage(environment, "each", List.of(callback), activation);

        assertSame(environment, result);
        assertEquals(List.of("A", bmp, supplementary), names);
    }

    @Test
    void environmentPrototypeIsConstructionOnlyAndNotMapLike() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue prototype = ProtosStandardEnvironmentProtocol.createPrototype();

        assertSame(ProtosObjectValue.rootObject(), prototype.parent().orElseThrow());
        assertEquals(java.util.Set.of("get", "contains", "each"), prototype.localSlotsSnapshot().keySet());
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
