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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtosStaticDefinitionsTest {
    private final ProtosStaticAnalysisCore core = new ProtosStaticAnalysisCore();

    @Test
    void resolvesSameActivationClosureParameterBeforeOpaqueEffects() {
        String source = "f: (parameter) => {\n  parameter\n}";
        int declaration = source.indexOf("parameter");
        int reference = source.lastIndexOf("parameter");

        ProtosStaticDefinitionResult result = definition(source, reference).orElseThrow();

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

        ProtosStaticDefinitionResult result = definition(source, firstDefaultReference).orElseThrow();
        assertEquals(
                new SourceSpan(source.indexOf("first"), source.indexOf("first") + "first".length()),
                result.targets().get(0).span());

        String selfDefault = "f: (value: value) => { value }";
        int selfReference = selfDefault.indexOf("value", selfDefault.indexOf("value:") + "value:".length());
        assertTrue(definition(selfDefault, selfReference).isEmpty());
    }

    @Test
    void invalidatesExactFactsAfterGuestInvocationButNotBeforeIt() {
        String source = "f: (value) => {\n  sink(value)\n  value\n}";
        int callArgument = source.indexOf("value", source.indexOf("sink"));
        int afterCall = source.lastIndexOf("value");

        assertTrue(definition(source, callArgument).isPresent());
        assertTrue(definition(source, afterCall).isEmpty());
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

        assertTrue(definition(source, keyReference).isEmpty());
        assertTrue(definition(source, valueReference).isEmpty());
    }

    @Test
    void assignmentTargetIsDeferredButAssignmentDoesNotChangeExistingOrigin() {
        String source = "f: (value) => {\n  value = 2\n  value\n}";
        int assignmentTarget = source.indexOf("value", source.indexOf("=>"));
        int laterRead = source.lastIndexOf("value");

        assertTrue(definition(source, assignmentTarget).isEmpty());
        assertTrue(definition(source, laterRead).isPresent());
    }


    @Test
    void doesNotTreatLazyBooleanRightOperandAsSameActivation() {
        String source = "f: (condition, value) => { condition && value }";
        int conditionReference = source.indexOf("condition", source.indexOf("=>"));
        int lazyCapture = source.lastIndexOf("value");

        assertTrue(definition(source, conditionReference).isPresent());
        assertTrue(definition(source, lazyCapture).isEmpty());
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

        assertTrue(definition(source, outerCapture).isEmpty());
        assertTrue(definition(source, innerReference).isPresent());
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

        ProtosStaticDefinitionResult grouped = definition(source, groupedRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + "while".length()),
                grouped.targets().get(0).span());
        assertTrue(definition(source, contextualHeader).isEmpty());
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

        assertTrue(definition(source, conditionRead).isEmpty());
        assertTrue(definition(source, bodyRead).isEmpty());
        assertTrue(definition(source, afterLoop).isEmpty());

        String inner = "while(c) { ((k) => k)(1) }";
        int innerReference = inner.indexOf("k", inner.indexOf("=>"));
        assertTrue(definition(inner, innerReference).isPresent());
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

        ProtosStaticDefinitionResult grouped = definition(source, groupedRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + "until".length()),
                grouped.targets().get(0).span());
        assertTrue(definition(source, contextualKeyword).isEmpty());
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

        assertTrue(definition(source, bodyRead).isEmpty());
        assertTrue(definition(source, conditionRead).isEmpty());
        assertTrue(definition(source, afterLoop).isEmpty());

        String bodyLocal = "{ local: 1 } until(local)";
        assertTrue(definition(bodyLocal, bodyLocal.lastIndexOf("local")).isEmpty());

        String innerBody = "{ ((k) => k)(1) } until(c)";
        assertTrue(definition(
                innerBody, innerBody.indexOf("k", innerBody.indexOf("=>"))).isPresent());
        String innerCondition = "{ c } until(((k) => k)(1))";
        assertTrue(definition(
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

        ProtosStaticDefinitionResult grouped = definition(source, groupedRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + "if".length()),
                grouped.targets().get(0).span());
        assertTrue(definition(source, contextualHeader).isEmpty());
        assertTrue(definition(source, source.indexOf("else")).isEmpty());
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
                definition(source, conditionRead).orElseThrow();
        assertEquals(
                new SourceSpan(declaration, declaration + 1),
                condition.targets().get(0).span());
        assertTrue(definition(source, trueRead).isEmpty());
        assertTrue(definition(source, falseRead).isEmpty());
        assertTrue(definition(source, afterConditional).isEmpty());

        String inner = "if(c) { ((k) => k)(1) } else { ((j) => j)(2) }";
        assertTrue(definition(inner, inner.indexOf("k", inner.indexOf("=>"))).isPresent());
        assertTrue(definition(inner, inner.indexOf("j", inner.indexOf("j) =>") + 1)).isPresent());
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

    private Optional<ProtosStaticDefinitionResult> definition(String source, int sourceOffset) {
        return core.definition(new ProtosDocumentSnapshot("test.protos", 1L, source), sourceOffset);
    }

    private static String slice(String source, SourceSpan span) {
        return source.substring(span.startOffset(), span.endOffset());
    }
}
