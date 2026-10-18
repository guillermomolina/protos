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
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInspectFacility;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * LIB022-A: deferred standard module members are created only while a module context of their
 * exact key is provisioned, once per module context, and never become Prelude-shared anchors.
 */
final class ProtosLazyStandardModuleMembersTest {
    private static final ProtosModuleKey LAZY_KEY = new ProtosModuleKey("std:lazyFixture");
    private static final ProtosModuleKey EAGER_KEY = new ProtosModuleKey("std:eagerFixture");
    private static final ProtosModuleKey UNRELATED_KEY = new ProtosModuleKey("std:unrelated");
    private static final String SLOT = "_lazyFacility";

    @Test
    void suppliersRunOnlyDuringProvisioningOfTheirExactModule() {
        AtomicInteger invocations = new AtomicInteger();
        ProtosPrelude prelude =
                prelude(
                        Map.of(),
                        Map.of(
                                LAZY_KEY,
                                Map.of(
                                        SLOT,
                                        () -> {
                                            invocations.incrementAndGet();
                                            return frozenObject();
                                        })));
        assertEquals(0, invocations.get());

        prelude.newExecutionContext();
        assertEquals(0, invocations.get());

        ProtosObjectValue unrelated = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(UNRELATED_KEY, unrelated);
        prelude.installStandardModuleMembersForRuntime(null, unrelated);
        assertEquals(0, invocations.get());
        assertFalse(unrelated.hasLocalSlot(SLOT));

        ProtosObjectValue first = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(LAZY_KEY, first);
        assertEquals(1, invocations.get());
        ProtosObjectValue firstFacility =
                assertInstanceOf(ProtosObjectValue.class, first.readLocalSlot(SLOT).orElseThrow());
        assertTrue(firstFacility.isFrozen());

        ProtosObjectValue second = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(LAZY_KEY, second);
        assertEquals(2, invocations.get());
        ProtosObjectValue secondFacility =
                assertInstanceOf(ProtosObjectValue.class, second.readLocalSlot(SLOT).orElseThrow());
        assertNotSame(firstFacility, secondFacility);

        // A deferred private object is never a Prelude-shared transfer anchor.
        assertFalse(prelude.isStandardModuleMemberForRuntime(firstFacility));
        assertFalse(prelude.isStandardModuleMemberForRuntime(secondFacility));
    }

    @Test
    void coreBootstrapDefersTheInspectFacilityToItsModuleProvisioning() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        String slot = ProtosInspectFacility.BOOTSTRAP_SLOT;
        ProtosModuleKey key = ProtosInspectFacility.MODULE_KEY;
        assertTrue(prelude.bindings().readLocalSlot(slot).isEmpty());

        ProtosObjectValue unrelated = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(
                new ProtosModuleKey("std:runtime"), unrelated);
        assertFalse(unrelated.hasLocalSlot(slot));

        ProtosObjectValue first = prelude.newExecutionContext();
        ProtosObjectValue second = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(key, first);
        prelude.installStandardModuleMembersForRuntime(key, second);
        ProtosObjectValue firstFacility =
                assertInstanceOf(ProtosObjectValue.class, first.readLocalSlot(slot).orElseThrow());
        ProtosObjectValue secondFacility =
                assertInstanceOf(ProtosObjectValue.class, second.readLocalSlot(slot).orElseThrow());
        assertTrue(firstFacility.isFrozen());
        assertNotSame(firstFacility, secondFacility);
        assertFalse(prelude.isStandardModuleMemberForRuntime(firstFacility));
    }

    @Test
    void eagerMembersKeepTheirSharedIdentityBesideDeferredMembers() {
        ProtosObjectValue shared = frozenObject();
        ProtosPrelude prelude =
                prelude(
                        Map.of(EAGER_KEY, Map.of("Shared", shared)),
                        Map.of(LAZY_KEY, Map.of(SLOT, ProtosLazyStandardModuleMembersTest::lazy)));

        ProtosObjectValue first = prelude.newExecutionContext();
        ProtosObjectValue second = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(EAGER_KEY, first);
        prelude.installStandardModuleMembersForRuntime(EAGER_KEY, second);
        assertSame(shared, first.readLocalSlot("Shared").orElseThrow());
        assertSame(shared, second.readLocalSlot("Shared").orElseThrow());
        assertFalse(first.hasLocalSlot(SLOT));
        assertTrue(prelude.isStandardModuleMemberForRuntime(shared));
    }

    @Test
    void eagerAndDeferredMembersOfOneModuleAreBothInstalled() {
        ProtosObjectValue shared = frozenObject();
        ProtosPrelude prelude =
                prelude(
                        Map.of(LAZY_KEY, Map.of("Shared", shared)),
                        Map.of(LAZY_KEY, Map.of(SLOT, ProtosLazyStandardModuleMembersTest::lazy)));
        ProtosObjectValue module = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(LAZY_KEY, module);
        assertSame(shared, module.readLocalSlot("Shared").orElseThrow());
        assertTrue(module.hasLocalSlot(SLOT));
    }

    @Test
    void provisioningRejectsNullAndUnfrozenSuppliedObjects() {
        ProtosPrelude nullPrelude = prelude(Map.of(), Map.of(LAZY_KEY, Map.of(SLOT, () -> null)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        nullPrelude.installStandardModuleMembersForRuntime(
                                LAZY_KEY, nullPrelude.newExecutionContext()));

        ProtosPrelude unfrozenPrelude =
                prelude(
                        Map.of(),
                        Map.of(
                                LAZY_KEY,
                                Map.of(
                                        SLOT,
                                        () -> new ProtosObjectValue(
                                                ProtosObjectValue.rootObject()))));
        assertThrows(
                IllegalStateException.class,
                () ->
                        unfrozenPrelude.installStandardModuleMembersForRuntime(
                                LAZY_KEY, unfrozenPrelude.newExecutionContext()));
    }

    @Test
    void constructionRejectsDuplicateAndNullRegistrations() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        prelude(
                                Map.of(LAZY_KEY, Map.of(SLOT, frozenObject())),
                                Map.of(
                                        LAZY_KEY,
                                        Map.of(SLOT, ProtosLazyStandardModuleMembersTest::lazy))));
        assertThrows(NullPointerException.class, () -> prelude(Map.of(), null));
        HashMap<String, Supplier<ProtosObjectValue>> nullSupplier =
                new HashMap<>();
        nullSupplier.put(SLOT, null);
        assertThrows(
                NullPointerException.class,
                () -> prelude(Map.of(), Map.of(LAZY_KEY, nullSupplier)));
        HashMap<String, Supplier<ProtosObjectValue>> nullName = new HashMap<>();
        nullName.put(null, ProtosLazyStandardModuleMembersTest::lazy);
        assertThrows(
                NullPointerException.class, () -> prelude(Map.of(), Map.of(LAZY_KEY, nullName)));
        HashMap<ProtosModuleKey, Map<String, Supplier<ProtosObjectValue>>> nullKey =
                new HashMap<>();
        nullKey.put(null, Map.of(SLOT, ProtosLazyStandardModuleMembersTest::lazy));
        assertThrows(NullPointerException.class, () -> prelude(Map.of(), nullKey));
    }

    private static ProtosObjectValue lazy() {
        return frozenObject();
    }

    private static ProtosObjectValue frozenObject() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    private static ProtosPrelude prelude(
            Map<ProtosModuleKey, Map<String, ProtosObjectValue>> eager,
            Map<ProtosModuleKey, Map<String, Supplier<ProtosObjectValue>>> lazy) {
        ProtosObjectValue contextPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.createLocalSlot("Array", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();
        return new ProtosPrelude(
                bindings,
                contextPrototype,
                null,
                null,
                null,
                null,
                null,
                null,
                eager,
                List.of(),
                lazy);
    }
}
