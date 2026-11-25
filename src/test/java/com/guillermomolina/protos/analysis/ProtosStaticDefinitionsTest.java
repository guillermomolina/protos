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

package com.guillermomolina.protos.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtosStaticDefinitionsTest {
    private final ProtosStaticAnalysisCore core = new ProtosStaticAnalysisCore();

    @Test
    void resolvesSameActivationClosureParameterBeforeOpaqueEffects() {
        String source = "f: (parameter) => {\n  parameter\n}";
        int declaration = source.indexOf("parameter");
        int reference = source.lastIndexOf("parameter");

        ProtosStaticDefinitionResult result = exactOrigin(source, reference).orElseThrow();

        assertEquals(new SourceSpan(reference, reference + "parameter".length()), result.referenceSpan());
        assertEquals(1, result.targets().size());
        assertEquals(
                new SourceSpan(declaration, declaration + "parameter".length()),
                result.targets().get(0).span());
    }

    @Test
    void appliesLeftToRightParameterInstallationInsideDefaults() {
        String source = "f: (first, second: first) => { second }";
        int firstDefaultReference = source.indexOf("first", source.indexOf("second"));

        ProtosStaticDefinitionResult result = exactOrigin(source, firstDefaultReference).orElseThrow();
        assertEquals(
                new SourceSpan(source.indexOf("first"), source.indexOf("first") + "first".length()),
                result.targets().get(0).span());

        String selfDefault = "f: (value: value) => { value }";
        int selfReference = selfDefault.indexOf("value", selfDefault.indexOf("value:") + "value:".length());
        assertTrue(exactOrigin(selfDefault, selfReference).isEmpty());
    }

    @Test
    void invalidatesExactFactsAfterGuestInvocationButNotBeforeIt() {
        String source = "f: (value) => {\n  sink(value)\n  value\n}";
        int callArgument = source.indexOf("value", source.indexOf("sink"));
        int afterCall = source.lastIndexOf("value");

        assertTrue(exactOrigin(source, callArgument).isPresent());
        assertTrue(exactOrigin(source, afterCall).isEmpty());
    }

    @Test
    void mapConstructionFactoryInvocationInvalidatesFactsBeforeFirstEntry() {
        String source =
                "f: (value) => {\n"
                        + "  %{\n"
                        + "    value: value\n"
                        + "  }\n"
                        + "}";
        int keyReference = source.indexOf("value", source.indexOf("%{"));
        int valueReference = source.indexOf("value", keyReference + 1);

        assertTrue(exactOrigin(source, keyReference).isEmpty());
        assertTrue(exactOrigin(source, valueReference).isEmpty());
    }

    @Test
    void assignmentTargetIsDeferredButAssignmentDoesNotChangeExistingOrigin() {
        String source = "f: (value) => {\n  value = 2\n  value\n}";
        int assignmentTarget = source.indexOf("value", source.indexOf("=>"));
        int laterRead = source.lastIndexOf("value");

        int parameter = source.indexOf("value");

        ProtosStaticDefinitionResult target = exactOrigin(source, assignmentTarget).orElseThrow();
        assertEquals(
                new SourceSpan(assignmentTarget, assignmentTarget + "value".length()),
                target.referenceSpan());
        assertEquals(
                new SourceSpan(parameter, parameter + "value".length()),
                target.targets().get(0).span());
        assertTrue(exactOrigin(source, laterRead).isPresent());
    }


    @Test
    void doesNotTreatLazyBooleanRightOperandAsSameActivation() {
        String source = "f: (condition, value) => { condition && value }";
        int conditionReference = source.indexOf("condition", source.indexOf("=>"));
        int lazyCapture = source.lastIndexOf("value");

        assertTrue(exactOrigin(source, conditionReference).isPresent());
        assertTrue(exactOrigin(source, lazyCapture).isEmpty());
    }

    @Test
    void doesNotPromoteOuterCaptureToGenerationOneDefinition() {
        String source =
                "f: (outer) => {\n"
                        + "  nested: (inner) => {\n"
                        + "    outer\n"
                        + "    inner\n"
                        + "  }\n"
                        + "}";
        int outerCapture = source.lastIndexOf("outer");
        int innerReference = source.lastIndexOf("inner");

        assertTrue(exactOrigin(source, outerCapture).isEmpty());
        assertTrue(exactOrigin(source, innerReference).isPresent());
    }

    @Test
    void contextualWhileHeaderIsNotALexicalReadOfWhileBinding() {
        String source =
                "f: (while, n) => {\n"
                        + "  (while)(n) { n }\n"
                        + "  while(n > 1) { n = n - 1 }\n"
                        + "}";
        int declaration = source.indexOf("while");
        int groupedRead = source.indexOf("while", source.indexOf("(while)"));
        int contextualHeader = source.lastIndexOf("while");

        ProtosStaticDefinitionResult grouped = exactOrigin(source, groupedRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + "while".length()),
                grouped.targets().get(0).span());
        assertTrue(exactOrigin(source, contextualHeader).isEmpty());
    }

    @Test
    void contextualWhileConditionAndBodyAreDeferredClosureActivations() {
        String source =
                "f: (n) => {\n"
                        + "  while(n > 1) { n = n - 1 }\n"
                        + "  n\n"
                        + "}";
        int conditionRead = source.indexOf("n", source.indexOf("while("));
        int bodyRead = source.lastIndexOf("n - 1");
        int afterLoop = source.lastIndexOf("n");

        assertTrue(exactOrigin(source, conditionRead).isEmpty());
        assertTrue(exactOrigin(source, bodyRead).isEmpty());
        assertTrue(exactOrigin(source, afterLoop).isEmpty());

        String inner = "while(c) { ((k) => k)(1) }";
        int innerReference = inner.indexOf("k", inner.indexOf("=>"));
        assertTrue(exactOrigin(inner, innerReference).isPresent());
    }

    @Test
    void contextualForBodyReadResolvesToTheHeaderBinderParameter() {
        String numeric = "for(i, 0, 3) { i }";
        int binder = numeric.indexOf("i");
        int bodyRead = numeric.lastIndexOf("i");
        ProtosStaticDefinitionResult numericResult =
                exactOrigin(numeric, bodyRead).orElseThrow();
        assertEquals(new SourceSpan(bodyRead, bodyRead + 1), numericResult.referenceSpan());
        assertEquals(
                new SourceSpan(binder, binder + 1),
                numericResult.targets().get(0).span());

        String generic = "f: (xs) => {\n  for(item in xs) { item }\n}";
        int itemDeclaration = generic.indexOf("item");
        int itemRead = generic.lastIndexOf("item");
        int iterableRead = generic.indexOf("xs", generic.indexOf(" in "));
        assertEquals(
                new SourceSpan(itemDeclaration, itemDeclaration + "item".length()),
                exactOrigin(generic, itemRead).orElseThrow().targets().get(0).span());
        assertEquals(
                new SourceSpan(generic.indexOf("xs"), generic.indexOf("xs") + 2),
                exactOrigin(generic, iterableRead).orElseThrow().targets().get(0).span());
    }

    @Test
    void contextualForHeaderTokensAreNotLexicalReads() {
        String source =
                "f: (for, in, n) => {\n"
                        + "  (for)(n)\n"
                        + "  for(x in n) { x }\n"
                        + "  for(i, 0, 1) { i }\n"
                        + "}";
        int forDeclaration = source.indexOf("for");
        int groupedRead = source.indexOf("for", source.indexOf("(for)"));
        int genericKeyword = source.indexOf("for(x");
        int inSeparator = source.indexOf(" in ", genericKeyword) + 1;
        int numericKeyword = source.lastIndexOf("for(");
        int numericBinder = source.indexOf("i", numericKeyword + 3);

        assertEquals(
                new SourceSpan(forDeclaration, forDeclaration + "for".length()),
                exactOrigin(source, groupedRead).orElseThrow().targets().get(0).span());
        assertTrue(exactOrigin(source, genericKeyword).isEmpty());
        assertTrue(exactOrigin(source, inSeparator).isEmpty());
        assertTrue(exactOrigin(source, numericKeyword).isEmpty());
        assertTrue(exactOrigin(source, numericBinder).isEmpty());
    }

    @Test
    void contextualUntilKeywordIsNotALexicalReadOfUntilBinding() {
        String source =
                "f: (until, n) => {\n"
                        + "  (until)(n)\n"
                        + "  { n } until(n)\n"
                        + "}";
        int declaration = source.indexOf("until");
        int groupedRead = source.indexOf("until", source.indexOf("(until)"));
        int contextualKeyword = source.lastIndexOf("until");

        ProtosStaticDefinitionResult grouped = exactOrigin(source, groupedRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + "until".length()),
                grouped.targets().get(0).span());
        assertTrue(exactOrigin(source, contextualKeyword).isEmpty());
    }

    @Test
    void contextualUntilBodyAndConditionAreIndependentDeferredClosures() {
        String source =
                "f: (n) => {\n"
                        + "  { n = n - 1 } until(n == 0)\n"
                        + "  n\n"
                        + "}";
        int bodyRead = source.indexOf("n - 1");
        int conditionRead = source.indexOf("n == 0");
        int afterLoop = source.lastIndexOf("n");

        assertTrue(exactOrigin(source, bodyRead).isEmpty());
        assertTrue(exactOrigin(source, conditionRead).isEmpty());
        assertTrue(exactOrigin(source, afterLoop).isEmpty());

        String bodyLocal = "{ local: 1 } until(local)";
        assertTrue(exactOrigin(bodyLocal, bodyLocal.lastIndexOf("local")).isEmpty());

        String innerBody = "{ ((k) => k)(1) } until(c)";
        assertTrue(exactOrigin(
                innerBody, innerBody.indexOf("k", innerBody.indexOf("=>"))).isPresent());
        String innerCondition = "{ c } until(((k) => k)(1))";
        assertTrue(exactOrigin(
                innerCondition,
                innerCondition.indexOf("k", innerCondition.indexOf("=>"))).isPresent());
    }

    @Test
    void contextualIfHeaderIsNotALexicalReadOfIfBinding() {
        String source =
                "f: (if, n) => {\n"
                        + "  (if)(n) { n }\n"
                        + "  if(n) { n } else { n }\n"
                        + "}";
        int declaration = source.indexOf("if");
        int groupedRead = source.indexOf("if", source.indexOf("(if)"));
        int contextualHeader = source.lastIndexOf("if(");

        ProtosStaticDefinitionResult grouped = exactOrigin(source, groupedRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + "if".length()),
                grouped.targets().get(0).span());
        assertTrue(exactOrigin(source, contextualHeader).isEmpty());
        assertTrue(exactOrigin(source, source.indexOf("else")).isEmpty());
    }

    @Test
    void contextualIfConditionIsEagerButBranchesAreClosureActivations() {
        String source =
                "f: (n) => {\n"
                        + "  if(n > 1) { n } else { n }\n"
                        + "  n\n"
                        + "}";
        int declaration = source.indexOf("n");
        int conditionRead = source.indexOf("n", source.indexOf("if("));
        int trueRead = source.indexOf("n", source.indexOf("{ n }"));
        int falseRead = source.indexOf("n", source.indexOf("else"));
        int afterConditional = source.lastIndexOf("n");

        ProtosStaticDefinitionResult condition =
                exactOrigin(source, conditionRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + 1),
                condition.targets().get(0).span());
        assertTrue(exactOrigin(source, trueRead).isEmpty());
        assertTrue(exactOrigin(source, falseRead).isEmpty());
        assertTrue(exactOrigin(source, afterConditional).isEmpty());

        String inner = "if(c) { ((k) => k)(1) } else { ((j) => j)(2) }";
        assertTrue(exactOrigin(inner, inner.indexOf("k", inner.indexOf("=>"))).isPresent());
        assertTrue(exactOrigin(inner, inner.indexOf("j", inner.indexOf("j) =>") + 1)).isPresent());
    }

    @Test
    void i176ContextualMatchHeaderIsNotALexicalReadOfMatchBinding() {
        String source =
                "f: (match, n) => {\n"
                        + "  (match)(n) { n }\n"
                        + "  match(n) { 0: n }\n"
                        + "}";
        int declaration = source.indexOf("match");
        int groupedRead = source.indexOf("match", source.indexOf("(match)"));
        int contextualHeader = source.lastIndexOf("match(");

        ProtosStaticDefinitionResult grouped = exactOrigin(source, groupedRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + "match".length()),
                grouped.targets().get(0).span());
        assertTrue(exactOrigin(source, contextualHeader).isEmpty());
        assertTrue(navigation(source, contextualHeader).isEmpty());
    }

    @Test
    void i176ContextualMatchSubjectIsEagerButActionsAreDeferred() {
        String source =
                "f: (n) => {\n"
                        + "  match(n) {\n"
                        + "    0: n\n"
                        + "    Any: (m) => n\n"
                        + "  }\n"
                        + "  n\n"
                        + "}";
        int declaration = source.indexOf("n");
        int subjectRead = source.indexOf("n", source.indexOf("match("));
        int implicitRead = source.indexOf("n", source.indexOf("0:"));
        int explicitRead = source.indexOf("n", source.indexOf("(m) =>"));
        int afterMatch = source.lastIndexOf("n");

        ProtosStaticDefinitionResult subject =
                exactOrigin(source, subjectRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + 1),
                subject.targets().get(0).span());
        assertTrue(exactOrigin(source, implicitRead).isEmpty());
        assertTrue(exactOrigin(source, explicitRead).isEmpty());
        assertTrue(exactOrigin(source, afterMatch).isEmpty());

        String inner = "match(c) { 0: ((k) => k)(1) }";
        assertTrue(exactOrigin(inner, inner.indexOf("k", inner.indexOf("=>"))).isPresent());
    }

    @Test
    void i176ImplicitActionSlotIsNotAnEnclosingDeclaration() {
        String source = "match(v) {\n  0: local: 1\n}\nlocal";
        assertTrue(exactOrigin(source, source.lastIndexOf("local")).isEmpty());

        String explicit = "v.caseOf(%{\n  0: () => local: 1\n})\nlocal";
        assertEquals(
                exactOrigin(explicit, explicit.lastIndexOf("local")).isPresent(),
                exactOrigin(source, source.lastIndexOf("local")).isPresent());
    }

    @Test
    void i176NavigationThroughContextualMatchUsesOrdinaryLexicalScope() {
        String source =
                "valor: 1\n"
                        + "clave: 2\n"
                        + "match(valor) {\n"
                        + "  clave: valor\n"
                        + "  [Capture]: (x) => x + valor\n"
                        + "}\n";
        int clave = source.indexOf("clave");
        assertNavigates(source, source.indexOf("valor", source.indexOf("match(")), "valor", 0);
        assertNavigates(source, source.indexOf("clave", clave + 1), "clave", clave);
        assertNavigates(source, source.indexOf("valor", source.indexOf("clave:", clave + 1)),
                "valor", 0);
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
        assertNavigates(source, source.indexOf("x +"), "x", source.indexOf("(x)") + 1);
    }

    private static final String I176_OBJECT_CONTEXTUAL =
            "f: (outer) => {\n"
                    + "    match(1) {\n"
                    + "        1: {\n"
                    + "            inner: 10\n"
                    + "            copy: inner\n"
                    + "            captured: outer\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";

    private static final String I176_OBJECT_EXPLICIT =
            "f: (outer) => {\n"
                    + "    1.caseOf(%{\n"
                    + "        1: () => ({\n"
                    + "            inner: 10\n"
                    + "            copy: inner\n"
                    + "            captured: outer\n"
                    + "        })\n"
                    + "    })\n"
                    + "}";

    @Test
    void i176ObjectValuedImplicitActionResolvesLikeExplicitClosure() {
        assertSameResolution(I176_OBJECT_CONTEXTUAL, I176_OBJECT_EXPLICIT, "inner", 1);
        assertSameResolution(I176_OBJECT_CONTEXTUAL, I176_OBJECT_EXPLICIT, "outer", 1);
        assertTrue(resolutionLabels(I176_OBJECT_CONTEXTUAL,
                        nth(I176_OBJECT_CONTEXTUAL, "outer", 1))
                .contains("navigation outer#0"));
    }

    @Test
    void i176GroupedObjectValuedImplicitActionResolvesLikeUngroupedAction() {
        String grouped = I176_OBJECT_CONTEXTUAL
                .replace("        1: {\n", "        1: ({\n")
                .replace("        }\n    }\n}", "        })\n    }\n}");
        assertTrue(grouped.contains("1: ({"));
        assertTrue(grouped.contains("})\n    }\n}"));
        assertSameResolution(grouped, I176_OBJECT_EXPLICIT, "inner", 1);
        assertSameResolution(grouped, I176_OBJECT_EXPLICIT, "outer", 1);
        assertSameResolution(grouped, I176_OBJECT_CONTEXTUAL, "inner", 1);
        assertSameResolution(grouped, I176_OBJECT_CONTEXTUAL, "outer", 1);
    }

    @Test
    void i176NestedDeferredScopesResolveLikeExplicitExpansion() {
        String contextual =
                "f: (outer) => {\n"
                        + "    match(1) {\n"
                        + "        1: {\n"
                        + "            run: (param) => match(param) {\n"
                        + "                Any: param + outer\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}";
        String explicit =
                "f: (outer) => {\n"
                        + "    1.caseOf(%{\n"
                        + "        1: () => ({\n"
                        + "            run: (param) => param.caseOf(%{\n"
                        + "                Any: () => param + outer\n"
                        + "            })\n"
                        + "        })\n"
                        + "    })\n"
                        + "}";
        assertSameResolution(contextual, explicit, "param", 1);
        assertSameResolution(contextual, explicit, "param", 2);
        assertSameResolution(contextual, explicit, "outer", 1);
        for (String source : List.of(contextual, explicit)) {
            assertTrue(resolutionLabels(source, nth(source, "param", 2))
                    .contains("navigation param#0"), source);
            assertTrue(resolutionLabels(source, nth(source, "outer", 1))
                    .contains("navigation outer#0"), source);
        }
    }

    @Test
    void i176SiblingImplicitActionsKeepIndependentActivations() {
        String contextual =
                "f: (value) => {\n"
                        + "    match(value) {\n"
                        + "        0: (local: 1) + local\n"
                        + "        Any: (local: 2) + local\n"
                        + "    }\n"
                        + "    local\n"
                        + "}";
        String explicit =
                "f: (value) => {\n"
                        + "    value.caseOf(%{\n"
                        + "        0: () => (local: 1) + local\n"
                        + "        Any: () => (local: 2) + local\n"
                        + "    })\n"
                        + "    local\n"
                        + "}";
        for (int occurrence = 1; occurrence <= 4; occurrence++) {
            assertSameResolution(contextual, explicit, "local", occurrence);
        }
        for (String source : List.of(contextual, explicit)) {
            List<String> first = resolutionLabels(source, nth(source, "local", 1));
            List<String> second = resolutionLabels(source, nth(source, "local", 3));
            List<String> after = resolutionLabels(source, nth(source, "local", 4));
            assertTrue(first.contains("navigation local#0"), source);
            assertFalse(first.contains("navigation local#2"), source);
            assertTrue(second.contains("navigation local#2"), source);
            assertFalse(second.contains("navigation local#0"), source);
            assertFalse(after.contains("navigation local#0"), source);
            assertFalse(after.contains("navigation local#2"), source);
            assertFalse(after.contains("exact local#0"), source);
            assertFalse(after.contains("exact local#2"), source);
        }
    }

    @Test
    void i176EagerSubjectAndKeySlotsFollowOrdinaryEvaluation() {
        String contextual =
                "f: () => {\n"
                        + "    match(subj: 1) {\n"
                        + "        (keyed: 1): subj + keyed\n"
                        + "    }\n"
                        + "    subj + keyed\n"
                        + "}";
        String explicit =
                "f: () => {\n"
                        + "    (subj: 1).caseOf(%{\n"
                        + "        (keyed: 1): () => subj + keyed\n"
                        + "    })\n"
                        + "    subj + keyed\n"
                        + "}";
        for (String name : List.of("subj", "keyed")) {
            assertSameResolution(contextual, explicit, name, 1);
            assertSameResolution(contextual, explicit, name, 2);
            for (String source : List.of(contextual, explicit)) {
                assertTrue(resolutionLabels(source, nth(source, name, 2))
                        .contains("navigation " + name + "#0"), source);
            }
        }
    }

    private void assertSameResolution(
            String contextual, String explicit, String name, int occurrence) {
        assertEquals(
                resolutionLabels(explicit, nth(explicit, name, occurrence)),
                resolutionLabels(contextual, nth(contextual, name, occurrence)),
                name + "#" + occurrence);
    }

    /**
     * Navigation and exact-origin targets named by identifier and occurrence
     * index, so equivalent sources of different lengths compare directly.
     */
    private List<String> resolutionLabels(String source, int reference) {
        List<String> labels = new ArrayList<>();
        navigation(source, reference).ifPresent(result -> result.targets().forEach(
                target -> labels.add("navigation " + occurrenceLabel(source, target.span()))));
        exactOrigin(source, reference).ifPresent(result -> result.targets().forEach(
                target -> labels.add("exact " + occurrenceLabel(source, target.span()))));
        return labels;
    }

    private static String occurrenceLabel(String source, SourceSpan span) {
        String name = slice(source, span);
        int count = 0;
        int index = source.indexOf(name);
        while (index >= 0 && index < span.startOffset()) {
            count++;
            index = source.indexOf(name, index + 1);
        }
        return name + "#" + count;
    }

    private static int nth(String source, String name, int occurrence) {
        int index = source.indexOf(name);
        for (int count = 0; count < occurrence; count++) {
            index = source.indexOf(name, index + 1);
        }
        assertTrue(index >= 0, name + "#" + occurrence);
        return index;
    }

    @Test
    void sessionResultRetainsExactSnapshotFreshness() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("workspace");
        String source = "f: (value) => value";
        ProtosDocumentSnapshot snapshot = new ProtosDocumentSnapshot("doc", 1L, source);
        session.putDocument("workspace", snapshot);

        ProtosStaticDefinitionResult result = session.definitionCurrent(
                        "workspace", "doc", source.lastIndexOf("value"))
                .orElseThrow();
        assertTrue(session.isCurrent("workspace", result));

        session.putDocument("workspace", new ProtosDocumentSnapshot("doc", 2L, source + "\n"));
        assertFalse(session.isCurrent("workspace", result));
    }

    @Test
    void localSlotCreationIsTheOriginOfALaterRead() {
        String source = "mensaje: \"Hola\"\nmensaje";
        assertProven(source, source.lastIndexOf("mensaje"), 0, "mensaje");
    }

    @Test
    void closureStoredInSlotIsTheOriginOfItsCallReceiver() {
        String source = "saludar: (nombre) => {\n  print(nombre)\n}\n\nsaludar(\"Hola\")";
        assertProven(source, source.lastIndexOf("saludar"), 0, "saludar");
        assertProven(
                source, source.indexOf("nombre", source.indexOf("print")),
                source.indexOf("nombre"), "nombre");
    }

    @Test
    void localCreationInsideClosureIsTheOriginInThatActivation() {
        String source = "f: () => {\n  local: 1\n  local\n}";
        assertProven(source, source.lastIndexOf("local"), source.indexOf("local"), "local");
    }

    @Test
    void assignmentKeepsTheOriginalCreationForLaterReads() {
        String source = "count: 1\ncount = 2\ncount";
        assertProven(source, source.lastIndexOf("count"), 0, "count");
    }

    @Test
    void assignmentTargetResolvesToTheOriginalCreation() {
        String source = "count: 1\ncount = 2\ncount";
        int target = source.indexOf("count =");
        ProtosStaticDefinitionResult result = assertProven(source, target, 0, "count");
        assertEquals("count", slice(source, result.referenceSpan()));
        assertEquals("count", slice(source, result.targets().get(0).span()));
    }

    @Test
    void groupedCreationProvesEveryTargetAfterTheValue() {
        String source = "(a, b): [1, 2]\na\nb";
        assertProven(source, source.lastIndexOf("a"), source.indexOf("a"), "a");
        assertProven(source, source.lastIndexOf("b"), source.indexOf("b"), "b");
    }

    @Test
    void slotCreatedAfterOpaqueInvocationIsProvenWithoutOlderFacts() {
        String source = "old: 1\nsink()\nfresh: 2\nfresh\nold";
        assertProven(source, source.lastIndexOf("fresh"), source.indexOf("fresh"), "fresh");
        assertTrue(exactOrigin(source, source.lastIndexOf("old")).isEmpty());
    }

    @Test
    void ownMemberOfBareObjectLiteralResolvesToItsDeclaration() {
        String source = "obj: {\n  dato: 1\n}\nobj.dato";
        int selector = source.lastIndexOf("dato");
        ProtosStaticDefinitionResult result =
                assertProven(source, selector, source.indexOf("dato"), "dato");
        assertEquals(new SourceSpan(selector, selector + "dato".length()), result.referenceSpan());
        assertProven(source, source.lastIndexOf("obj"), 0, "obj");
    }

    @Test
    void closureStoredInOwnMemberResolvesAtItsCall() {
        String source = "obj: {\n  saludar: () => { 42 }\n}\nobj.saludar()";
        assertProven(
                source, source.lastIndexOf("saludar"), source.indexOf("saludar"), "saludar");
    }

    @Test
    void sameNamedMembersOfDifferentObjectsResolveToTheirOwnDeclarations() {
        String source = "a: {\n  dato: 1\n}\nb: {\n  dato: 2\n}\na.dato\nb.dato";
        int firstDeclaration = source.indexOf("dato");
        int secondDeclaration = source.indexOf("dato", firstDeclaration + 1);
        assertProven(source, source.indexOf("a.dato") + 2, firstDeclaration, "dato");
        assertProven(source, source.indexOf("b.dato") + 2, secondDeclaration, "dato");
    }

    @Test
    void readBeforeCreationHasNoOrigin() {
        String source = "later\nlater: 1";
        assertTrue(exactOrigin(source, 0).isEmpty());
    }

    @Test
    void slotIsNotVisibleOutsideItsActivation() {
        String source = "f: () => {\n  inner: 1\n}\ninner";
        assertTrue(exactOrigin(source, source.lastIndexOf("inner")).isEmpty());
    }

    @Test
    void sameNameInUnprovenOuterActivationHasNoOrigin() {
        String source = "x: 1\nf: () => {\n  x\n}";
        assertTrue(exactOrigin(source, source.lastIndexOf("x")).isEmpty());
    }

    @Test
    void parameterOfAnotherClosureIsNotAnOrigin() {
        String source = "f: (p) => { p }\ng: () => { p }";
        assertTrue(exactOrigin(source, source.lastIndexOf("p")).isEmpty());
    }

    @Test
    void opaqueInvocationInvalidatesSlotOrigin() {
        String source = "v: 1\nsink()\nv";
        assertTrue(exactOrigin(source, source.lastIndexOf("v")).isEmpty());
    }

    @Test
    void opaqueInvocationInvalidatesObjectMembers() {
        String source = "obj: {\n  dato: 1\n}\nsink()\nobj.dato";
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
        String method = "obj: {\n  m: () => { 1 }\n  dato: 2\n}\nobj.m()\nobj.dato";
        assertTrue(exactOrigin(method, method.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void unknownMemberReceiverHasNoOrigin() {
        String source = "unknown.dato";
        assertTrue(exactOrigin(source, source.indexOf("dato")).isEmpty());
        String parameter = "f: (o) => { o.dato }";
        assertTrue(exactOrigin(parameter, parameter.indexOf("dato")).isEmpty());
    }

    @Test
    void absentMemberThatCouldBeInheritedHasNoOrigin() {
        String source = "obj: {\n  dato: 1\n}\nobj.toString";
        assertTrue(exactOrigin(source, source.indexOf("toString")).isEmpty());
    }

    @Test
    void objectSlotsAreNotLexicalSlotsOfTheOuterActivation() {
        String source = "obj: {\n  dato: 1\n}\ndato";
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void explicitParentObjectHasNoMemberProvenance() {
        String source = "base: {\n  dato: 1\n}\nobj: base {\n  propio: 2\n}\nobj.propio\nobj.dato";
        assertTrue(exactOrigin(source, source.lastIndexOf("propio")).isEmpty());
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void compositionObjectHasNoMemberProvenance() {
        String source = "mixin: {\n  dato: 1\n}\nobj: {\n  ...mixin\n  propio: 2\n}\nobj.propio";
        assertTrue(exactOrigin(source, source.lastIndexOf("propio")).isEmpty());
    }

    @Test
    void aliasedObjectLosesMemberProvenance() {
        String source = "obj: {\n  dato: 1\n}\nalias: obj\nalias.nuevo: 2\nobj.dato";
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void namedArgumentLabelIsNotAReference() {
        String source = "value: 1\nsink(value: value)";
        int label = source.indexOf("value", source.indexOf("sink"));
        assertTrue(exactOrigin(source, label).isEmpty());
        assertProven(source, source.lastIndexOf("value"), 0, "value");
    }

    @Test
    void creationTargetsAreNotReferences() {
        String source = "x: 1\nobj: {\n  dato: 1\n}";
        assertTrue(exactOrigin(source, 0).isEmpty());
        assertTrue(exactOrigin(source, source.indexOf("dato")).isEmpty());
        String grouped = "(a, b): [1, 2]";
        assertTrue(exactOrigin(grouped, grouped.indexOf("a")).isEmpty());
    }

    @Test
    void invalidSyntaxAndOutOfDocumentPositionsHaveNoOrigin() {
        String invalid = "x: 1\nx(";
        assertTrue(exactOrigin(invalid, invalid.lastIndexOf("x")).isEmpty());
        String source = "x: 1\nx";
        assertTrue(exactOrigin(source, -1).isEmpty());
        assertTrue(exactOrigin(source, source.length()).isEmpty());
    }

    @Test
    void contextMemberWriteReplacingSlotValueInvalidatesMemberProvenance() {
        String source = "obj: {\n  dato: 1\n}\ncontext.obj = {\n  otro: 2\n}\nobj.dato";
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void contextMemberWriteWithSameNamedMemberNeverResolvesToTheOldDeclaration() {
        String source = "obj: {\n  dato: 1\n}\ncontext.obj = {\n  dato: 2\n}\nobj.dato";
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void contextMemberWriteDoesNotKeepUnprovenSlotOrigin() {
        String source = "obj: {\n  dato: 1\n}\ncontext.obj = {\n  otro: 2\n}\nobj";
        assertTrue(exactOrigin(source, source.lastIndexOf("obj")).isEmpty());
        // The write receiver is the context, not a read of the slot.
        assertTrue(exactOrigin(source, source.indexOf("obj =")).isEmpty());
        String before = "obj: {\n  dato: 1\n}\nobj\ncontext.obj = 2";
        assertProven(before, before.indexOf("obj\ncontext"), 0, "obj");
    }

    @Test
    void contextMemberCreationInvalidatesSlotAndMemberFacts() {
        String source = "obj: {\n  dato: 1\n}\ncontext.otro: 2\nobj.dato\nobj";
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
        assertTrue(exactOrigin(source, source.lastIndexOf("obj")).isEmpty());
    }

    @Test
    void thisMemberWriteInvalidatesFacts() {
        String source = "obj: {\n  dato: 1\n}\nthis.obj = 2\nobj.dato";
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void writeToKnownOwnMemberKeepsItsDeclaration() {
        String source = "obj: {\n  dato: 1\n}\nobj.dato = 2\nobj.dato";
        int declaration = source.indexOf("dato");
        assertProven(source, source.lastIndexOf("dato"), declaration, "dato");
        assertProven(source, source.lastIndexOf("obj"), 0, "obj");
    }

    @Test
    void memberWriteOnUnprovenReceiverInvalidatesObjectFacts() {
        String source = "f: (o) => {\n  obj: {\n    dato: 1\n  }\n  obj.dato\n"
                + "  o.campo = 3\n  obj.dato\n}";
        int declaration = source.indexOf("dato");
        assertProven(source, source.indexOf("dato", declaration + 1), declaration, "dato");
        assertTrue(exactOrigin(source, source.lastIndexOf("dato")).isEmpty());
        String creation = "f: (o) => {\n  obj: {\n    dato: 1\n  }\n  o.campo: 3\n  obj.dato\n}";
        assertTrue(exactOrigin(creation, creation.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void directResolutionKeepsHalfOpenReferenceSpans() {
        String source = "count: 1\ncount\n";
        int reference = source.lastIndexOf("count");
        int end = reference + "count".length();
        ProtosStaticParseResult.Parsed parsed = (ProtosStaticParseResult.Parsed) core.parse(
                new ProtosDocumentSnapshot("test.protos", 1L, source));

        ProtosStaticDefinitionResult atStart =
                ProtosStaticDefinitions.resolve(parsed, reference).orElseThrow();
        assertEquals(new SourceSpan(reference, end), atStart.referenceSpan());
        assertEquals(new SourceSpan(0, "count".length()), atStart.targets().get(0).span());

        ProtosStaticDefinitionResult inside =
                ProtosStaticDefinitions.resolve(parsed, reference + 2).orElseThrow();
        assertEquals(new SourceSpan(reference, end), inside.referenceSpan());

        // The end offset lies outside the half-open span. Accepting a cursor
        // just after a reference is an editor-protocol concern, not a change
        // to the AST reference range.
        assertTrue(ProtosStaticDefinitions.resolve(parsed, end).isEmpty());
    }

    @Test
    void captureOfARemovedAndRecreatedSlotHasNoOrigin() {
        // The Closure reads x from its captured context when it is invoked,
        // which happens after the first slot was removed and a new one was
        // created. Neither creation is the proven origin of the captured read.
        String source =
                "x: 1\n"
                        + "\n"
                        + "capturada: () => x\n"
                        + "\n"
                        + "context.removeSlot(\"x\")\n"
                        + "x: 2\n"
                        + "\n"
                        + "capturada()\n";
        int capture = source.indexOf("x", source.indexOf("=>"));

        assertTrue(exactOrigin(source, capture).isEmpty());
        assertTrue(exactOrigin(source, capture + 1).isEmpty());
        // The removal is an opaque invocation, so the later call receiver
        // keeps no proof either.
        assertTrue(exactOrigin(source, source.lastIndexOf("capturada")).isEmpty());
    }

    @Test
    void closuresSharingACapturedContextDoNotProveTheirCapturedReads() {
        // Inside reiniciar, context would name that Closure's own activation,
        // so the outer context is kept in contextoCompartido. Both Closures
        // capture the outer context by reference; between the two invocations
        // of leer, reiniciar removes the outer contador slot and creates a new
        // one on that same object, so the read inside leer observes
        // different slots.
        String source =
                "contador: 0\n"
                        + "contextoCompartido: context\n"
                        + "\n"
                        + "leer: () => contador\n"
                        + "\n"
                        + "reiniciar: () => {\n"
                        + "  contextoCompartido.removeSlot(\"contador\")\n"
                        + "  contextoCompartido.contador: 10\n"
                        + "}\n"
                        + "\n"
                        + "leer()\n"
                        + "reiniciar()\n"
                        + "leer()\n";
        int capture = source.indexOf("contador", source.indexOf("=>"));
        int firstCall = source.indexOf("leer()");
        int reset = source.indexOf("reiniciar()");
        int secondCall = source.lastIndexOf("leer()");

        assertTrue(exactOrigin(source, capture).isEmpty());
        assertProven(source, firstCall, source.indexOf("leer"), "leer");
        // Every guest invocation is an opaque barrier for later reads.
        assertTrue(exactOrigin(source, reset).isEmpty());
        assertTrue(exactOrigin(source, secondCall).isEmpty());
    }

    @Test
    void immediateInvocationProvesSourceSlotCapture() {
        // The literal is constructed as the call receiver and activated at
        // once by the frozen Object.call; its fresh activation has no x, so
        // lexical lookup continues to the SOURCE context proven at the call.
        String source = "x: 1\n(() => x)()\n";
        assertProven(source, source.indexOf("x", source.indexOf("=>")), 0, "x");
    }

    @Test
    void immediateInvocationProvesOuterClosureParameterCapture() {
        String source = "f: (exterior) => {\n  (() => exterior)()\n}";
        assertProven(
                source, source.lastIndexOf("exterior"), source.indexOf("exterior"), "exterior");
    }

    @Test
    void nestedImmediateInvocationsFollowEveryProvenLexicalLink() {
        String source = "x: 1\n(() => {\n  (() => x)()\n})()\n";
        assertProven(source, source.lastIndexOf("x"), 0, "x");
    }

    @Test
    void localCreationInImmediateActivationShadowsCapturedSlot() {
        String source = "x: 1\n(() => {\n  x: 2\n  x\n})()\n";
        assertProven(source, source.lastIndexOf("x"), source.indexOf("x: 2"), "x");
    }

    @Test
    void readsBeforeAndAfterLocalCreationSelectCapturedThenLocalOrigin() {
        String source = "x: 1\n(() => {\n  x\n  x: 2\n  x\n})()\n";
        assertProven(source, source.indexOf("x", source.indexOf("=>")), 0, "x");
        assertProven(source, source.lastIndexOf("x"), source.indexOf("x: 2"), "x");
    }

    @Test
    void assignmentToCapturedSlotKeepsItsOrigin() {
        // Assigning a literal selects the captured slot and changes only its
        // value; no guest code runs, so the slot's presence stays proven.
        String source = "x: 1\n(() => {\n  x = 2\n  x\n})()\n";
        int target = source.indexOf("x", source.indexOf("=>"));
        assertProven(source, target, 0, "x");
        assertProven(source, source.lastIndexOf("x"), 0, "x");
    }

    @Test
    void literalArgumentsAndLiteralDefaultsBindParametersExactly() {
        String source = "x: 1\n((a, b: 3) => {\n  a\n  b\n  x\n})(\"uno\")\n";
        assertProven(source, source.indexOf("  a\n") + 2, source.indexOf("a,"), "a");
        assertProven(source, source.indexOf("  b\n") + 2, source.indexOf("b:"), "b");
        assertProven(source, source.indexOf("  x\n") + 2, 0, "x");

        String grouped = "x: 1\n(((a) => {\n  x\n  a\n}))(1)\n";
        assertProven(grouped, grouped.indexOf("  x\n") + 2, 0, "x");
        assertProven(grouped, grouped.indexOf("  a\n") + 2, grouped.indexOf("a)"), "a");
    }

    @Test
    void creationAfterAnOpaqueCallRestoresTheProofOfTheCapturedSlot() {
        String source = "desconocido()\nx: 1\n(() => x)()\n";
        assertProven(source, source.lastIndexOf("x"), source.indexOf("x: 1"), "x");
    }

    @Test
    void unknownCallInsideImmediateActivationDropsCapturedFacts() {
        String source = "x: 1\n(() => {\n  desconocido()\n  x\n})()\n";
        assertTrue(exactOrigin(source, source.lastIndexOf("x")).isEmpty());
    }

    @Test
    void explicitRemovalFromTheCapturedContextDropsCapturedFacts() {
        String source =
                "x: 1\nexterior: context\n(() => {\n  exterior.removeSlot(\"x\")\n  x\n})()\n";
        assertTrue(exactOrigin(source, source.lastIndexOf("  x\n") + 2).isEmpty());
    }

    @Test
    void removalAndRecreationThroughTheCapturedContextHaveNoOrigin() {
        String source =
                "x: 1\nexterior: context\n(() => {\n"
                        + "  exterior.removeSlot(\"x\")\n"
                        + "  exterior.x: 2\n"
                        + "  x\n"
                        + "})()\n";
        assertTrue(exactOrigin(source, source.lastIndexOf("  x\n") + 2).isEmpty());
    }

    @Test
    void mutableAliasOfTheCapturedContextDropsCapturedFacts() {
        String source = "x: 1\nalias: context\n(() => {\n  alias.x: 5\n  x\n})()\n";
        assertTrue(exactOrigin(source, source.lastIndexOf("  x\n") + 2).isEmpty());
    }

    @Test
    void deferredCaptureRunAfterAnUnknownCallHasNoOrigin() {
        String source = "x: 1\nf: () => x\ndesconocido()\nf()\n";
        assertTrue(exactOrigin(source, source.indexOf("x", source.indexOf("=>"))).isEmpty());
    }

    @Test
    void captureWhoseLexicalParentRunsAtAnUnprovenTimeHasNoOrigin() {
        // g is not invoked here; when it eventually runs, the SOURCE context
        // may have been mutated or its x replaced.
        String source = "x: 1\ng: () => {\n  (() => x)()\n}\n";
        assertTrue(exactOrigin(source, source.lastIndexOf("x")).isEmpty());

        String replaced = "x: 1\nf: () => x\ncontext.removeSlot(\"x\")\nx: 2\nf()\n";
        assertTrue(exactOrigin(replaced, replaced.indexOf("x", replaced.indexOf("=>"))).isEmpty());
    }

    @Test
    void dynamicThisMemberInImmediateActivationHasNoOrigin() {
        String source = "x: 1\n(() => this.x)()\n";
        assertTrue(exactOrigin(source, source.lastIndexOf("x")).isEmpty());
    }

    @Test
    void callWhoseTargetIsNotTheLiteralClosureHasNoCapturedOrigin() {
        String named = "x: 1\nf: () => x\nf()\n";
        assertTrue(exactOrigin(named, named.indexOf("x", named.indexOf("=>"))).isEmpty());

        String member = "x: 1\n(() => x).call()\n";
        assertTrue(exactOrigin(member, member.indexOf("x", member.indexOf("=>"))).isEmpty());

        String method = "x: 1\nobj: {\n  method: () => x\n}\nobj.method()\n";
        assertTrue(exactOrigin(method, method.indexOf("x", method.indexOf("=>"))).isEmpty());
    }

    @Test
    void defaultsWithUnknownEffectsKeepTheCaptureUnproven() {
        String source = "x: 1\n((a: desconocido()) => x)()\n";
        assertTrue(exactOrigin(source, source.lastIndexOf("x")).isEmpty());

        String supplied = "x: 1\n((a: desconocido()) => x)(1)\n";
        assertTrue(exactOrigin(supplied, supplied.lastIndexOf("x")).isEmpty());
    }

    @Test
    void spreadNamedOrEffectfulArgumentsKeepTheCaptureUnproven() {
        String spread = "x: 1\nlista: [1]\n((a) => x)(...lista)\n";
        assertTrue(exactOrigin(spread, spread.lastIndexOf("x")).isEmpty());

        String effect = "x: 1\n((a) => x)(desconocido())\n";
        assertTrue(exactOrigin(effect, effect.indexOf("x", effect.indexOf("=>"))).isEmpty());

        String named = "x: 1\n((a) => x)(a: 1)\n";
        assertTrue(exactOrigin(named, named.indexOf("x", named.indexOf("=>"))).isEmpty());

        String missing = "x: 1\n((a) => x)()\n";
        assertTrue(exactOrigin(missing, missing.lastIndexOf("x")).isEmpty());
    }

    @Test
    void sameIdentifierInDifferentScopesKeepsDistinctOrigins() {
        String source = "f: (x) => x\nx: 1\n(() => x)()\n";
        assertProven(source, source.indexOf("x", source.indexOf("=>")), source.indexOf("x"), "x");
        assertProven(source, source.lastIndexOf("x"), source.indexOf("x: 1"), "x");

        String absent = "g: (x) => {\n  (() => x)()\n}\n(() => x)()\n";
        assertTrue(exactOrigin(absent, absent.lastIndexOf("x")).isEmpty());
    }

    @Test
    void objectConstructionBodyIsNotACapturedLexicalContext() {
        String source = "x: 1\nobj: {\n  y: (() => x)()\n}\n";
        assertTrue(exactOrigin(source, source.lastIndexOf("x")).isEmpty());
    }

    @Test
    void restParameterOriginIsTheIdentifierWithoutItsPrefix() {
        String source = "f: (...elementos) => elementos";
        assertProven(
                source,
                source.lastIndexOf("elementos"),
                source.indexOf("elementos"),
                "elementos");
    }

    @Test
    void defaultedParameterOfADeferredClosureOriginIsTheIdentifierOnly() {
        String source = "f: (a, b: 3) => {\n  b\n}";
        assertProven(source, source.lastIndexOf("b"), source.indexOf("b:"), "b");
    }

    @Test
    void navigationReachesOuterSlotFromEachCallback() {
        String source = "valor: 42\nnumeros: [1]\nnumeros.each((item) => {\n  print(valor)\n})\n";
        int read = source.lastIndexOf("valor");
        assertNavigates(source, read, "valor", 0);
        assertExactUnproven(source, read);
    }

    @Test
    void navigationReachesOuterSlotFromCallbackOfAnyMethodName() {
        String source = "valor: 42\nejecutar((item) => {\n  print(valor)\n})\n";
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);

        String other = "valor: 42\nlista.recorrerConIndice(1, (a, b) => valor)\n";
        assertNavigates(other, other.lastIndexOf("valor"), "valor", 0);
    }

    @Test
    void navigationReachesOuterSlotFromClosureStoredAndCalledLater() {
        String source =
                "valor: 42\ncallback: (item) => {\n  print(valor)\n}\nejecutar(callback)\ncallback(1)\n";
        int read = source.indexOf("valor", source.indexOf("print"));
        assertNavigates(source, read, "valor", 0);
        assertExactUnproven(source, read);
    }

    @Test
    void navigationReachesOuterSlotFromClosureReceivedAsArgument() {
        String source =
                "valor: 42\naplicar: (fn) => {\n  fn(1)\n}\naplicar((item) => valor)\n";
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
        // The parameter receiving the Closure is itself a lexical declaration.
        assertNavigates(source, source.indexOf("fn(1)"), "fn", source.indexOf("fn)"));
    }

    @Test
    void navigationCrossesTwoNestedClosures() {
        String source = "valor: 42\nexterna: () => {\n  interna: () => valor\n}\n";
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
    }

    @Test
    void navigationCrossesThreeNestedClosures() {
        String source =
                "valor: 42\na: () => {\n  b: () => {\n    c: () => {\n      valor\n    }\n  }\n}\n";
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
    }

    @Test
    void navigationReachesCapturedOuterParameter() {
        String source = "f: (exterior) => {\n  lista.each((item) => exterior)\n}";
        int read = source.lastIndexOf("exterior");
        assertNavigates(source, read, "exterior", source.indexOf("exterior"));
        assertExactUnproven(source, read);
    }

    @Test
    void innerParameterShadowsOuterDeclarationOfTheSameName() {
        String source = "valor: 1\nf: (valor) => {\n  ejecutar(() => valor)\n}\n";
        assertNavigates(
                source, source.lastIndexOf("valor"), "valor", source.indexOf("(valor)") + 1);
    }

    @Test
    void innerSlotShadowsOuterSlot() {
        String source =
                "valor: 1\nf: () => {\n  valor: 2\n  ejecutar(() => valor)\n}\n";
        assertNavigates(
                source, source.lastIndexOf("valor"), "valor", source.indexOf("valor: 2"));
    }

    @Test
    void readBeforeLocalCreationNavigatesToTheEnclosingDeclaration() {
        String source = "valor: 1\nf: () => {\n  print(valor)\n  valor: 2\n}\n";
        int read = source.indexOf("valor", source.indexOf("print"));
        assertNavigates(source, read, "valor", 0);
        assertExactUnproven(source, read);
    }

    @Test
    void readAfterLocalCreationNavigatesToTheLocalDeclarationAfterAnOpaqueCall() {
        String source = "valor: 1\nf: () => {\n  valor: 2\n  ejecutar()\n  valor\n}\n";
        int read = source.lastIndexOf("valor");
        assertNavigates(source, read, "valor", source.indexOf("valor: 2"));
        // The opaque call may have removed the local slot.
        assertExactUnproven(source, read);
    }

    @Test
    void navigationReachesOuterSlotFromContextualIfBranches() {
        String source = "valor: 1\nif(c) {\n  valor\n} else {\n  ejecutar(() => valor)\n}\n";
        int trueRead = source.indexOf("valor", source.indexOf("{"));
        assertNavigates(source, trueRead, "valor", 0);
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
        assertExactUnproven(source, trueRead);
    }

    @Test
    void navigationReachesOuterSlotFromContextualWhileConditionAndBody() {
        String source = "valor: 1\nwhile(valor > 0) {\n  ejecutar(() => valor)\n}\n";
        int condition = source.indexOf("valor", source.indexOf("while"));
        assertNavigates(source, condition, "valor", 0);
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
        assertExactUnproven(source, condition);
    }

    @Test
    void navigationReachesOuterSlotFromContextualUntilBodyAndCondition() {
        String source = "valor: 1\n{\n  ejecutar(() => valor)\n} until(valor)\n";
        assertNavigates(source, source.indexOf("valor", source.indexOf("=>")), "valor", 0);
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
    }

    @Test
    void navigationReachesOuterSlotAndBinderInsideContextualFor() {
        String source = "valor: 1\nfor(i, 0, 3) {\n  ejecutar(() => valor + i)\n}\n";
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
        assertNavigates(source, source.lastIndexOf("i)"), "i", source.indexOf("i,"));
    }

    @Test
    void navigationReachesOuterSlotFromLazyBooleanOperands() {
        String source = "valor: 1\nc && valor\nc || valor\n";
        int and = source.indexOf("valor", source.indexOf("&&"));
        assertNavigates(source, and, "valor", 0);
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
        assertExactUnproven(source, and);
    }

    @Test
    void closureInObjectBodyNavigatesToTheGenuineEnclosingContext() {
        String source = "valor: 1\nobj: {\n  metodo: () => valor\n}\n";
        assertNavigates(source, source.lastIndexOf("valor"), "valor", 0);
    }

    @Test
    void directObjectBodyReadNavigatesToAnEarlierOwnSlot() {
        String source = "obj: {\n  dato: 1\n  copia: dato\n}\n";
        int read = source.lastIndexOf("dato");
        assertNavigates(source, read, "dato", source.indexOf("dato: 1"));
        assertExactUnproven(source, read);
    }

    @Test
    void laterObjectSlotDoesNotSatisfyAnEarlierBodyRead() {
        String source = "dato: 9\n\nobj: {\n  copia: dato\n  dato: 1\n}\n";
        int read = source.indexOf("dato", source.indexOf("copia:"));
        assertNavigates(source, read, "dato", 0);
        assertExactUnproven(source, read);
    }

    @Test
    void earlierObjectSlotShadowsTheEnclosingDeclaration() {
        String source = "dato: 9\n\nobj: {\n  dato: 1\n  copia: dato\n}\n";
        int read = source.lastIndexOf("dato");
        assertNavigates(source, read, "dato", source.indexOf("dato: 1"));
        assertExactUnproven(source, read);
    }

    @Test
    void nestedObjectDoesNotCaptureTheOuterObject() {
        String source =
                "dato: 9\n\nobj: {\n  dato: 1\n\n  interior: {\n    copia: dato\n  }\n}\n";
        int read = source.lastIndexOf("dato");
        assertNavigates(source, read, "dato", 0);
        assertExactUnproven(source, read);
    }

    @Test
    void nestedObjectReadSelectsItsOwnEarlierSlot() {
        String source =
                "dato: 9\n\nobj: {\n  dato: 1\n\n  interior: {\n    dato: 2\n    copia: dato\n  }\n}\n";
        int read = source.lastIndexOf("dato");
        assertNavigates(source, read, "dato", source.indexOf("dato: 2"));
        assertExactUnproven(source, read);
    }

    @Test
    void nestedObjectWithoutGenuineOuterDeclarationHasNoNavigation() {
        String source = "obj: {\n  dato: 1\n\n  interior: {\n    copia: dato\n  }\n}\n";
        int read = source.lastIndexOf("dato");
        assertNoNavigation(source, read);
        assertExactUnproven(source, read);
    }

    @Test
    void closureInObjectBodySkipsTheSameNamedObjectSlot() {
        String source = "dato: 9\n\nobj: {\n  dato: 1\n  ejecutar: () => dato\n}\n";
        int read = source.lastIndexOf("dato");
        assertNavigates(source, read, "dato", 0);
        assertExactUnproven(source, read);
    }

    @Test
    void closureInObjectBodyWithoutOuterDeclarationHasNoNavigation() {
        String source = "obj: {\n  dato: 1\n  ejecutar: () => dato\n}\n";
        int read = source.lastIndexOf("dato");
        assertNoNavigation(source, read);
        assertExactUnproven(source, read);
    }

    @Test
    void closureInNestedObjectNavigatesToTheSourceDeclaration() {
        String source =
                "dato: 9\n\nobj: {\n  dato: 1\n\n  interior: {\n    dato: 2\n    ejecutar: () => dato\n  }\n}\n";
        int read = source.lastIndexOf("dato");
        assertNavigates(source, read, "dato", 0);
        assertExactUnproven(source, read);
    }

    @Test
    void unknownCallBeforeCapturedReadKeepsNavigationButDropsExactProof() {
        String immediate = "valor: 1\n(() => {\n  desconocido()\n  valor\n})()\n";
        int read = immediate.lastIndexOf("valor");
        assertExactUnproven(immediate, read);
        assertNavigates(immediate, read, "valor", 0);

        String deferred = "valor: 1\nejecutar(() => {\n  desconocido()\n  valor\n})\n";
        assertExactUnproven(deferred, deferred.lastIndexOf("valor"));
        assertNavigates(deferred, deferred.lastIndexOf("valor"), "valor", 0);
    }

    @Test
    void siblingClosuresKeepTheirOwnSameNamedLocals() {
        String source =
                "a: () => {\n  local: 1\n  ejecutar(() => local)\n}\n"
                        + "b: () => {\n  local: 2\n  ejecutar(() => local)\n}\n";
        int first = source.indexOf("local", source.indexOf("=> local") + 1);
        assertNavigates(source, first, "local", source.indexOf("local: 1"));
        assertNavigates(source, source.lastIndexOf("local"), "local", source.indexOf("local: 2"));
    }

    @Test
    void nestedSameNamedParametersSelectTheNearest() {
        String source = "f: (x) => {\n  g: (x) => {\n    ejecutar(() => x)\n  }\n  ejecutar(() => x)\n}";
        int outerParameter = source.indexOf("x");
        int innerParameter = source.indexOf("x", source.indexOf("g:"));
        int innerRead = source.indexOf("x", source.indexOf("=> x") + 1);
        assertNavigates(source, innerRead, "x", innerParameter);
        assertNavigates(source, source.lastIndexOf("x"), "x", outerParameter);
    }

    @Test
    void severalDeclarationsInOneContextAreAllCandidates() {
        String source = "x: 1\nx: 2\nf: () => x\n";
        int read = source.lastIndexOf("x");
        assertNavigates(source, read, "x", 0, source.indexOf("x: 2"));
        assertExactUnproven(source, read);
    }

    @Test
    void removedAndRecreatedSlotNavigatesToBothCreationsWithoutExactProof() {
        String source =
                "x: 1\ncapturada: () => x\ncontext.removeSlot(\"x\")\nx: 2\ncapturada()\n";
        int read = source.indexOf("x", source.indexOf("=>"));
        assertNavigates(source, read, "x", 0, source.indexOf("x: 2"));
        assertExactUnproven(source, read);
    }

    @Test
    void contextAliasMutationKeepsTheLexicalDeclarationButNotTheExactOrigin() {
        String source = "x: 1\nalias: context\n(() => {\n  alias.x: 5\n  x\n})()\n";
        int read = source.lastIndexOf("  x\n") + 2;
        assertExactUnproven(source, read);
        // A member creation through the alias is not a lexical declaration.
        assertNavigates(source, read, "x", 0);
    }

    @Test
    void readWhoseOnlyOriginIsTheDynamicReceiverHasNoNavigation() {
        String source = "obj: {\n  metodo: () => nombre\n}\n";
        assertNoNavigation(source, source.lastIndexOf("nombre"));
    }

    @Test
    void ordinaryMemberIsNeverALexicalDeclaration() {
        // Lexical bindings have priority over the receiver, so the method's
        // read selects the enclosing slot, never the same-named member.
        String method =
                "nombre: 1\nobj: {\n  nombre: 2\n  metodo: () => nombre\n}\n";
        assertNavigates(method, method.lastIndexOf("nombre"), "nombre", 0);

        // A direct object-body read is a read in the object under
        // construction: its earlier own slot is the nearest declaration.
        String body = "nombre: 1\nobj: {\n  nombre: 2\n  otro: nombre\n}\n";
        assertNavigates(body, body.lastIndexOf("nombre"), "nombre", body.indexOf("nombre: 2"));

        String outside = "obj: {\n  solo: 1\n}\nejecutar(() => solo)\n";
        assertNoNavigation(outside, outside.lastIndexOf("solo"));
    }

    @Test
    void readBeforeEveryKnownDeclarationHasNoNavigation() {
        String source = "valor\nvalor: 1\n";
        assertNoNavigation(source, 0);
    }

    @Test
    void declarationInAnActivationOutsideTheReadChainIsNotACandidate() {
        String source = "f: () => {\n  oculto: 1\n}\ng: () => oculto\n";
        assertNoNavigation(source, source.lastIndexOf("oculto"));
    }

    @Test
    void assignmentIsNotADeclaration() {
        String source = "f: () => {\n  valor = 1\n}\ng: () => valor\n";
        assertNoNavigation(source, source.lastIndexOf("valor"));
    }

    private ProtosStaticDefinitionResult assertProven(
            String source, int reference, int declaration, String name) {
        ProtosStaticDefinitionResult result = exactOrigin(source, reference).orElseThrow();
        assertEquals(new SourceSpan(reference, reference + name.length()), result.referenceSpan());
        assertEquals(1, result.targets().size());
        assertEquals(
                new SourceSpan(declaration, declaration + name.length()),
                result.targets().get(0).span());
        return result;
    }

    /** The strict exact-origin proof shared by References and Hover. */
    private Optional<ProtosStaticDefinitionResult> exactOrigin(String source, int sourceOffset) {
        ProtosStaticParseResult parsed =
                core.parse(new ProtosDocumentSnapshot("test.protos", 1L, source));
        if (!(parsed instanceof ProtosStaticParseResult.Parsed successful)) {
            return Optional.empty();
        }
        return ProtosStaticDefinitions.resolve(successful, sourceOffset);
    }

    /** Declaration navigation as answered by the public definition query. */
    private Optional<ProtosStaticDefinitionResult> navigation(String source, int sourceOffset) {
        return core.definition(new ProtosDocumentSnapshot("test.protos", 1L, source), sourceOffset);
    }

    /**
     * Asserts that navigation from the name at {@code reference} answers exactly
     * the identifiers starting at {@code declarations}, in that order.
     */
    private void assertNavigates(
            String source, int reference, String name, int... declarations) {
        ProtosStaticDefinitionResult result = navigation(source, reference).orElseThrow();
        assertEquals(new SourceSpan(reference, reference + name.length()), result.referenceSpan());
        assertEquals(declarations.length, result.targets().size());
        for (int index = 0; index < declarations.length; index++) {
            assertEquals(
                    new SourceSpan(declarations[index], declarations[index] + name.length()),
                    result.targets().get(index).span());
        }
    }

    private void assertNoNavigation(String source, int reference) {
        assertTrue(navigation(source, reference).isEmpty());
    }

    private void assertExactUnproven(String source, int reference) {
        assertTrue(exactOrigin(source, reference).isEmpty());
    }

    private static String slice(String source, SourceSpan span) {
        return source.substring(span.startOffset(), span.endOffset());
    }
}
