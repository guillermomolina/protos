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
        String source = "f: (first, second = first) => { second }";
        int firstDefaultReference = source.indexOf("first", source.indexOf("second"));

        ProtosStaticDefinitionResult result = definition(source, firstDefaultReference).orElseThrow();
        assertEquals(
                new SourceSpan(source.indexOf("first"), source.indexOf("first") + "first".length()),
                result.targets().get(0).span());

        String selfDefault = "f: (value = value) => { value }";
        int selfReference = selfDefault.indexOf("value", selfDefault.indexOf("=") + 1);
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
    void resolvesSingletonMatchBinderAndAliasOrigins() {
        String binderSource =
                "subject match {\n"
                        + "  case @value => value\n"
                        + "}";
        int binderReference = binderSource.lastIndexOf("value");
        ProtosStaticDefinitionResult binder = definition(binderSource, binderReference).orElseThrow();
        assertEquals("@value", slice(binderSource, binder.targets().get(0).span()));

        String aliasSource =
                "subject match {\n"
                        + "  case @whole: 1 => whole\n"
                        + "  case _ => 0\n"
                        + "}";
        int aliasReference = aliasSource.lastIndexOf("whole");
        ProtosStaticDefinitionResult alias = definition(aliasSource, aliasReference).orElseThrow();
        assertTrue(slice(aliasSource, alias.targets().get(0).span()).startsWith("@whole:"));
    }

    @Test
    void matchGuardInvocationInvalidatesBinderBeforeBody() {
        String source =
                "subject match {\n"
                        + "  case @value when predicate(value) => value\n"
                        + "  case _ => 0\n"
                        + "}";
        int guardReference = source.indexOf("value", source.indexOf("predicate"));
        int bodyReference = source.lastIndexOf("value");

        assertTrue(definition(source, guardReference).isPresent());
        assertTrue(definition(source, bodyReference).isEmpty());
    }

    @Test
    void failsClosedForMultiOriginOrAndCaptureInterfaceBindings() {
        String orSource =
                "subject match {\n"
                        + "  case (@item: 1 | @item: 2) => item\n"
                        + "  case _ => 0\n"
                        + "}";
        assertTrue(definition(orSource, orSource.lastIndexOf("item")).isEmpty());

        String captureSource =
                "subject match {\n"
                        + "  case opaque captures(item) => item\n"
                        + "  case _ => 0\n"
                        + "}";
        assertTrue(definition(captureSource, captureSource.lastIndexOf("item")).isEmpty());
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
