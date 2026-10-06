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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AUD006-A4 evidence for the private linear CommandLine Array construction facility. */
final class ProtosCommandLineArrayConstructionFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final int VALUE_COUNT = 64;

    @Test
    void factoryIsFrozenStatelessMemberOfExactCommandLineKeyOnly() throws Exception {
        ProtosPrelude prelude = core();
        ProtosObjectValue factory = factory(prelude);

        assertTrue(factory.isFrozen());
        assertEquals(Set.of("call"), factory.localSlotsSnapshot().keySet());
        assertTrue(prelude.isStandardModuleMemberForRuntime(factory));
        assertSame(factory, factory(prelude));

        ProtosObjectValue otherContext = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(
                new ProtosModuleKey("std:cli/Other"), otherContext);
        assertFalse(
                otherContext.hasLocalSlot(
                        ProtosCommandLineArrayConstructionFacility.BOOTSTRAP_SLOT));
    }

    @Test
    void eachFactoryCallCreatesAnIndependentBuilder() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue factory = factory(prelude);

        Object first = ProtosInvocation.invokeMessage(factory, "call", List.of(), activation);
        Object second = ProtosInvocation.invokeMessage(factory, "call", List.of(), activation);
        assertNotSame(first, second);

        ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosInvocation.invokeMessage(first, "append", List.of(value), activation);

        ProtosArrayValue secondResult =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        ProtosInvocation.invokeMessage(second, "finish", List.of(), activation));
        assertEquals(0, secondResult.indexedSizeForRuntime());

        ProtosArrayValue firstResult =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        ProtosInvocation.invokeMessage(first, "finish", List.of(), activation));
        assertEquals(1, firstResult.indexedSizeForRuntime());
        assertSame(value, firstResult.indexedAtForRuntime(0));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        factory, "call", List.of(value), activation));
    }

    @Test
    void finishMaterializesOneOrdinaryArrayPreservingOrderAndIdentity() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        Object builder =
                ProtosInvocation.invokeMessage(factory(prelude), "call", List.of(), activation);

        List<ProtosObjectValue> values = distinctValues();
        for (ProtosObjectValue value : values) {
            ProtosInvocation.invokeMessage(builder, "append", List.of(value), activation);
        }

        ProtosArrayValue result =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        ProtosInvocation.invokeMessage(builder, "finish", List.of(), activation));
        assertSame(prelude.arrayPrototype(), result.parent().orElseThrow());
        assertFalse(result.isFrozen());
        assertEquals(VALUE_COUNT, result.indexedSizeForRuntime());
        for (int index = 0; index < VALUE_COUNT; index++) {
            assertSame(values.get(index), result.indexedAtForRuntime(index));
        }

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(builder, "finish", List.of(), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        builder, "append", List.of(values.get(0)), activation));
    }

    @Test
    void builderOperationsRequireExactReceiverAndArity() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue builder =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosInvocation.invokeMessage(
                                factory(prelude), "call", List.of(), activation));
        ProtosObjectValue child = new ProtosObjectValue(builder);
        ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(child, "append", List.of(value), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(child, "finish", List.of(), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(builder, "append", List.of(), activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        builder, "finish", List.of(value), activation));
    }

    /**
     * Appends need no Prelude and therefore cannot materialize any Array; the accumulated values
     * are materialized exactly once, by the single successful finish.
     */
    @Test
    void builderStateAppendsLinearlyAndMaterializesExactlyOnce() throws Exception {
        ProtosPrelude prelude = core();
        ProtosCommandLineArrayConstructionFacility.BuilderState state =
                new ProtosCommandLineArrayConstructionFacility.BuilderState();

        List<ProtosObjectValue> values = distinctValues();
        for (ProtosObjectValue value : values) {
            assertTrue(state.append(value));
        }
        assertFalse(state.isConsumed());
        assertEquals(0, state.finalMaterializations());

        ProtosArrayValue result = state.finish(prelude);
        assertTrue(state.isConsumed());
        assertEquals(1, state.finalMaterializations());
        assertEquals(VALUE_COUNT, result.indexedSizeForRuntime());

        assertNull(state.finish(prelude));
        assertFalse(state.append(values.get(0)));
        assertEquals(1, state.finalMaterializations());
        assertEquals(VALUE_COUNT, result.indexedSizeForRuntime());
    }

    @Test
    void importedCommandLineRemovesBootstrapSlotAndParseStillUsesFacility() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue module =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:cli/CommandLine\")", activation));
        assertFalse(
                module.hasLocalSlot(ProtosCommandLineArrayConstructionFacility.BOOTSTRAP_SLOT));
        assertEquals(
                Set.of("option", "positional", "command", "parse", "renderHelp"),
                module.localSlotsSnapshot().keySet());

        Object positionals =
                ProtosTestExecutionSupport.evaluate(
                        """
                        CommandLine: import("std:cli/CommandLine")
                        spec: CommandLine.command({
                            name: "tool"
                            help: null
                            options: Array()
                            positionals: Array(CommandLine.positional({
                                key: "rest"
                                minOccurrences: 0
                                maxOccurrences: null
                                valueName: "ARG"
                                help: null
                            }))
                            subcommands: Array()
                        })
                        CommandLine.parse(spec, Array("a", "b", "c")).command.positionals
                        """,
                        activation);
        ProtosArrayValue result = assertInstanceOf(ProtosArrayValue.class, positionals);
        assertTrue(result.isFrozen());
        assertEquals(3, result.indexedSizeForRuntime());
    }

    private static List<ProtosObjectValue> distinctValues() {
        ArrayList<ProtosObjectValue> values = new ArrayList<>(VALUE_COUNT);
        for (int index = 0; index < VALUE_COUNT; index++) {
            values.add(new ProtosObjectValue(ProtosObjectValue.rootObject()));
        }
        return values;
    }

    private static ProtosObjectValue factory(ProtosPrelude prelude) {
        return ProtosStandardModuleMemberTestSupport.member(
                prelude,
                ProtosCommandLineArrayConstructionFacility.MODULE_KEY.canonicalId(),
                ProtosCommandLineArrayConstructionFacility.BOOTSTRAP_SLOT);
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }
}
