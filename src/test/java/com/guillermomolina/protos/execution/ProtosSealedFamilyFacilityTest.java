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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSealedValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * LIB020-A evidence for the private sealing facilities of std:text/Style and std:text/StyledText
 * (shared with std:text/ANSI by LIB020-B),
 * for the logical run content that StyledText keeps out of Protos-visible state, and for the
 * non-portability of both families across Actor and isolated-parallel transfer.
 */
final class ProtosSealedFamilyFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final String IMPORTS =
            "Style: import(\"std:text/Style\")\n"
                    + "StyledText: import(\"std:text/StyledText\")\n";

    @Test
    void facilitiesAreDistinctFrozenMembersOfTheirExactModulesOnly() throws Exception {
        ProtosPrelude prelude = core();
        ProtosObjectValue style = styleFacility(prelude);
        ProtosObjectValue styledText = styledTextFacility(prelude);

        assertTrue(style.isFrozen());
        assertTrue(styledText.isFrozen());
        assertNotSame(style, styledText);
        assertEquals(Set.of("seal", "recognizes", "state"), style.localSlotsSnapshot().keySet());
        assertEquals(
                Set.of("seal", "recognizes", "state"), styledText.localSlotsSnapshot().keySet());

        ProtosObjectValue otherContext = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(
                new ProtosModuleKey("std:text/UTF8"), otherContext);
        assertFalse(otherContext.hasLocalSlot(ProtosSealedFamilyFacility.STYLE_BOOTSTRAP_SLOT));
        assertFalse(
                otherContext.hasLocalSlot(ProtosSealedFamilyFacility.STYLED_TEXT_BOOTSTRAP_SLOT));
    }

    @Test
    void modulesPublishExactlyTheirRatifiedSurface() throws Exception {
        ProtosActivation activation = core().newModuleActivation();
        ProtosObjectValue style =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:text/Style\")", activation));
        ProtosObjectValue styledText =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:text/StyledText\")", activation));

        assertEquals(Set.of("call", "recognizes"), style.localSlotsSnapshot().keySet());
        assertEquals(
                Set.of("call", "concat", "recognizes"), styledText.localSlotsSnapshot().keySet());
    }

    @Test
    void ansiReceivesTheSameFacilitiesAndPublishesOnlyRender() throws Exception {
        ProtosPrelude prelude = core();
        ProtosObjectValue ansiContext = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(
                ProtosSealedFamilyFacility.ANSI_MODULE_KEY, ansiContext);

        assertEquals(
                Set.of(
                        ProtosSealedFamilyFacility.STYLE_BOOTSTRAP_SLOT,
                        ProtosSealedFamilyFacility.STYLED_TEXT_BOOTSTRAP_SLOT),
                ansiContext.localSlotsSnapshot().keySet());
        assertSame(
                styleFacility(prelude),
                ansiContext
                        .readLocalSlot(ProtosSealedFamilyFacility.STYLE_BOOTSTRAP_SLOT)
                        .orElseThrow());
        assertSame(
                styledTextFacility(prelude),
                ansiContext
                        .readLocalSlot(ProtosSealedFamilyFacility.STYLED_TEXT_BOOTSTRAP_SLOT)
                        .orElseThrow());

        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue ansi =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:text/ANSI\")", activation));
        assertEquals(Set.of("render"), ansi.localSlotsSnapshot().keySet());
        assertEquals(
                "\u001b[31mx\u001b[0m",
                assertInstanceOf(
                                ProtosStringValue.class,
                                ProtosTestExecutionSupport.evaluate(
                                        IMPORTS
                                                + "import(\"std:text/ANSI\").render("
                                                + "StyledText(\"x\", Style(\"red\")), true)",
                                        activation))
                        .value());
    }

    @Test
    void styleKeepsItsForegroundAsPrivateFamilyState() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosSealedValue style =
                assertInstanceOf(
                        ProtosSealedValue.class,
                        ProtosTestExecutionSupport.evaluate(IMPORTS + "Style(\"red\")", activation));

        assertTrue(style.isFrozen());
        assertEquals(Set.of("equals", "==", "hash"), style.localSlotsSnapshot().keySet());
        assertEquals("red", foreground(prelude, activation, style));
        assertFalse(style.belongsTo(styledTextFacility(prelude)));
    }

    @Test
    void emptyTextKeepsNoRunsOrStyle() throws Exception {
        for (String expression :
                List.of(
                        "StyledText(\"\")",
                        "StyledText(\"\", Style(\"red\"))",
                        "StyledText.concat()",
                        "StyledText.concat(\"\")",
                        "StyledText.concat(\"\", StyledText(\"\", Style(\"blue\")), \"\")")) {
            assertEquals(List.of(), runs(expression), expression);
        }
    }

    @Test
    void constructionKeepsTextAndStyle() throws Exception {
        assertEquals(List.of("plain@default"), runs("StyledText(\"plain\")"));
        assertEquals(List.of("x@red"), runs("StyledText(\"x\", Style(\"red\"))"));
        assertEquals(List.of("x@default"), runs("StyledText(\"x\", Style(\"default\"))"));
    }

    @Test
    void concatPreservesOrderTextAndStyles() throws Exception {
        assertEquals(List.of("plain@default"), runs("StyledText.concat(\"plain\")"));
        assertEquals(
                List.of("[phase] @default", "FAIL@red", " path/to/test@default"),
                runs(
                        "StyledText.concat(\"[phase] \", StyledText(\"FAIL\", Style(\"red\")),"
                                + " \" path/to/test\")"));
        assertEquals(
                List.of("a@red", "b@green", "c@blue", "d@default"),
                runs(
                        "StyledText.concat(StyledText(\"a\", Style(\"red\")),"
                                + " StyledText(\"b\", Style(\"green\")),"
                                + " StyledText.concat(StyledText(\"c\", Style(\"blue\")), \"d\"))"));
    }

    @Test
    void concatDropsEmptyRunsAndCoalescesEqualAdjacentStyles() throws Exception {
        assertEquals(List.of("xy@default"), runs("StyledText.concat(\"x\", \"\", \"y\")"));
        assertEquals(
                List.of("xy@default"),
                runs("StyledText.concat(StyledText(\"x\", Style(\"default\")), \"y\")"));
        assertEquals(
                List.of("a@default", "bc@red", "d@default"),
                runs(
                        "StyledText.concat(\"a\", StyledText(\"b\", Style(\"red\")),"
                                + " StyledText(\"\", Style(\"blue\")),"
                                + " StyledText(\"c\", Style(\"red\")), \"d\")"));
    }

    @Test
    void concatOfExistingStyledTextAnswersAFreshValueWithTheSameRuns() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue pair =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        ProtosTestExecutionSupport.evaluate(
                                IMPORTS
                                        + "original: StyledText(\"ab\", Style(\"cyan\"))\n"
                                        + "[original, StyledText.concat(original)]",
                                activation));
        Object original = pair.indexedAtForRuntime(0);
        Object copy = pair.indexedAtForRuntime(1);
        assertNotSame(original, copy);
        assertEquals(List.of("ab@cyan"), describe(prelude, activation, original));
        assertEquals(List.of("ab@cyan"), describe(prelude, activation, copy));
    }

    @Test
    void styleAndStyledTextAreNotTransferableAcrossActorsOrIntoParallelExecution()
            throws Exception {
        ProtosPrelude prelude = core();
        for (String expression :
                List.of(
                        "Style(\"red\")",
                        "Style(\"default\")",
                        "StyledText(\"plain\")",
                        "StyledText(\"x\", Style(\"red\"))",
                        "StyledText.concat()",
                        "StyledText.concat(\"a\", StyledText(\"b\", Style(\"blue\")))")) {
            Object value = ProtosTestExecutionSupport.evaluate(
                    IMPORTS + expression, prelude.newModuleActivation());
            assertInstanceOf(ProtosSealedValue.class, value, expression);
            assertNonTransferable(value, prelude);
            assertNonParallel(value, prelude);
        }
    }

    @Test
    void sealedValuesNestedInOrdinaryGraphsFailTheWholeTransfer() throws Exception {
        ProtosPrelude prelude = core();
        for (String expression :
                List.of(
                        "graph: [\"label\", Style(\"green\")]\ngraph.freeze()",
                        "graph: [\"label\", StyledText(\"x\", Style(\"green\"))]\n"
                                + "graph.freeze()")) {
            Object graph = ProtosTestExecutionSupport.evaluate(
                    IMPORTS + expression, prelude.newModuleActivation());
            assertInstanceOf(ProtosArrayValue.class, graph, expression);
            assertNonTransferable(graph, prelude);
            assertNonParallel(graph, prelude);
        }
    }

    @Test
    void theSealItselfBlocksActorTransferIndependentlyOfClosureSlots() throws Exception {
        // A Style carries Closure slots, which are already non-transferable across Actors; a
        // slotless value of the Style family proves that the family alone blocks the copy.
        ProtosPrelude prelude = core();
        ProtosSealedValue slotless =
                ProtosSealedValue.seal(
                        styleFacility(prelude),
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        new ProtosStringValue("red"));
        assertTrue(slotless.localSlotsSnapshot().isEmpty());
        assertNonTransferable(slotless, prelude);
        assertNonParallel(slotless, prelude);
    }

    @Test
    void ordinaryFrozenObjectsStillCrossBothBoundariesAsPlainCopies() throws Exception {
        ProtosPrelude prelude = core();
        ProtosObjectValue control = new ProtosObjectValue(ProtosObjectValue.rootObject());
        control.createLocalSlot("text", new ProtosStringValue("red"));
        control.freeze();

        Object actorCopy =
                ProtosActorValueTransfer.snapshotValue(control, prelude.newModuleActivation());
        Object parallelCopy =
                ProtosParallelRuntime.captureValuesForTesting(
                                List.of(control), prelude.newModuleActivation(), new boolean[1])
                        .get(0);
        for (Object copied : List.of(actorCopy, parallelCopy)) {
            ProtosObjectValue copy = assertInstanceOf(ProtosObjectValue.class, copied);
            assertNotSame(control, copy);
            assertEquals(ProtosObjectValue.class, copy.getClass());
            assertEquals(
                    "red",
                    assertInstanceOf(
                                    ProtosStringValue.class,
                                    copy.readLocalSlot("text").orElseThrow())
                            .value());
        }
    }

    private static List<String> runs(String expression) throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        Object value = ProtosTestExecutionSupport.evaluate(IMPORTS + expression, activation);
        return describe(prelude, activation, value);
    }

    /** Reads StyledText runs through its family facility as {@code text@foreground}. */
    private static List<String> describe(
            ProtosPrelude prelude, ProtosActivation activation, Object value) {
        ProtosSealedValue styledText = assertInstanceOf(ProtosSealedValue.class, value);
        assertTrue(styledText.isFrozen());
        assertTrue(styledText.localSlotsSnapshot().isEmpty());
        ProtosArrayValue runs =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        ProtosInvocation.invokeMessage(
                                styledTextFacility(prelude), "state", List.of(value), activation));
        assertTrue(runs.isFrozen());
        List<String> described = new ArrayList<>();
        for (int index = 0; index < runs.indexedSizeForRuntime(); index++) {
            ProtosArrayValue run =
                    assertInstanceOf(ProtosArrayValue.class, runs.indexedAtForRuntime(index));
            assertTrue(run.isFrozen());
            assertEquals(2, run.indexedSizeForRuntime());
            String text =
                    assertInstanceOf(ProtosStringValue.class, run.indexedAtForRuntime(0)).value();
            assertFalse(text.isEmpty());
            described.add(text + "@" + foreground(prelude, activation, run.indexedAtForRuntime(1)));
        }
        return described;
    }

    private static String foreground(
            ProtosPrelude prelude, ProtosActivation activation, Object style) {
        return assertInstanceOf(
                        ProtosStringValue.class,
                        ProtosInvocation.invokeMessage(
                                styleFacility(prelude), "state", List.of(style), activation))
                .value();
    }

    private static void assertNonTransferable(Object value, ProtosPrelude owner) {
        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosActorValueTransfer.snapshotValue(
                                        value, owner.newModuleActivation()));
        assertSame(
                owner.bindings().readLocalSlot("NonTransferableValue").orElseThrow(),
                failure.error().parent().orElseThrow());
    }

    private static void assertNonParallel(Object value, ProtosPrelude owner) {
        RuntimeException failure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                ProtosParallelRuntime.captureValuesForTesting(
                                        List.of(value),
                                        owner.newModuleActivation(),
                                        new boolean[1]));
        assertEquals("NonParallel", failure.getClass().getSimpleName());
    }

    private static ProtosObjectValue styleFacility(ProtosPrelude prelude) {
        return ProtosStandardModuleMemberTestSupport.member(
                prelude,
                ProtosSealedFamilyFacility.STYLE_MODULE_KEY.canonicalId(),
                ProtosSealedFamilyFacility.STYLE_BOOTSTRAP_SLOT);
    }

    private static ProtosObjectValue styledTextFacility(ProtosPrelude prelude) {
        return ProtosStandardModuleMemberTestSupport.member(
                prelude,
                ProtosSealedFamilyFacility.STYLED_TEXT_MODULE_KEY.canonicalId(),
                ProtosSealedFamilyFacility.STYLED_TEXT_BOOTSTRAP_SLOT);
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }
}
