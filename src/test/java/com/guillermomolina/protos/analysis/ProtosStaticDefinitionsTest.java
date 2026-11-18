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

        int parameter = source.indexOf("value");

        ProtosStaticDefinitionResult target = definition(source, assignmentTarget).orElseThrow();
        assertEquals(
                new SourceSpan(assignmentTarget, assignmentTarget + "value".length()),
                target.referenceSpan());
        assertEquals(
                new SourceSpan(parameter, parameter + "value".length()),
                target.targets().get(0).span());
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
    void contextualForBodyReadResolvesToTheHeaderBinderParameter() {
        String numeric = "for(i, 0, 3) { i }";
        int binder = numeric.indexOf("i");
        int bodyRead = numeric.lastIndexOf("i");
        ProtosStaticDefinitionResult numericResult =
                definition(numeric, bodyRead).orElseThrow();
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
                definition(generic, itemRead).orElseThrow().targets().get(0).span());
        assertEquals(
                new SourceSpan(generic.indexOf("xs"), generic.indexOf("xs") + 2),
                definition(generic, iterableRead).orElseThrow().targets().get(0).span());
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
                definition(source, groupedRead).orElseThrow().targets().get(0).span());
        assertTrue(definition(source, genericKeyword).isEmpty());
        assertTrue(definition(source, inSeparator).isEmpty());
        assertTrue(definition(source, numericKeyword).isEmpty());
        assertTrue(definition(source, numericBinder).isEmpty());
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
        assertTrue(definition(source, source.lastIndexOf("old")).isEmpty());
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
        assertTrue(definition(source, 0).isEmpty());
    }

    @Test
    void slotIsNotVisibleOutsideItsActivation() {
        String source = "f: () => {\n  inner: 1\n}\ninner";
        assertTrue(definition(source, source.lastIndexOf("inner")).isEmpty());
    }

    @Test
    void sameNameInUnprovenOuterActivationHasNoOrigin() {
        String source = "x: 1\nf: () => {\n  x\n}";
        assertTrue(definition(source, source.lastIndexOf("x")).isEmpty());
    }

    @Test
    void parameterOfAnotherClosureIsNotAnOrigin() {
        String source = "f: (p) => { p }\ng: () => { p }";
        assertTrue(definition(source, source.lastIndexOf("p")).isEmpty());
    }

    @Test
    void opaqueInvocationInvalidatesSlotOrigin() {
        String source = "v: 1\nsink()\nv";
        assertTrue(definition(source, source.lastIndexOf("v")).isEmpty());
    }

    @Test
    void opaqueInvocationInvalidatesObjectMembers() {
        String source = "obj: {\n  dato: 1\n}\nsink()\nobj.dato";
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
        String method = "obj: {\n  m: () => { 1 }\n  dato: 2\n}\nobj.m()\nobj.dato";
        assertTrue(definition(method, method.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void unknownMemberReceiverHasNoOrigin() {
        String source = "unknown.dato";
        assertTrue(definition(source, source.indexOf("dato")).isEmpty());
        String parameter = "f: (o) => { o.dato }";
        assertTrue(definition(parameter, parameter.indexOf("dato")).isEmpty());
    }

    @Test
    void absentMemberThatCouldBeInheritedHasNoOrigin() {
        String source = "obj: {\n  dato: 1\n}\nobj.toString";
        assertTrue(definition(source, source.indexOf("toString")).isEmpty());
    }

    @Test
    void objectSlotsAreNotLexicalSlotsOfTheOuterActivation() {
        String source = "obj: {\n  dato: 1\n}\ndato";
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void explicitParentObjectHasNoMemberProvenance() {
        String source = "base: {\n  dato: 1\n}\nobj: base {\n  propio: 2\n}\nobj.propio\nobj.dato";
        assertTrue(definition(source, source.lastIndexOf("propio")).isEmpty());
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void compositionObjectHasNoMemberProvenance() {
        String source = "mixin: {\n  dato: 1\n}\nobj: {\n  ...mixin\n  propio: 2\n}\nobj.propio";
        assertTrue(definition(source, source.lastIndexOf("propio")).isEmpty());
    }

    @Test
    void aliasedObjectLosesMemberProvenance() {
        String source = "obj: {\n  dato: 1\n}\nalias: obj\nalias.nuevo: 2\nobj.dato";
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void namedArgumentLabelIsNotAReference() {
        String source = "value: 1\nsink(value: value)";
        int label = source.indexOf("value", source.indexOf("sink"));
        assertTrue(definition(source, label).isEmpty());
        assertProven(source, source.lastIndexOf("value"), 0, "value");
    }

    @Test
    void creationTargetsAreNotReferences() {
        String source = "x: 1\nobj: {\n  dato: 1\n}";
        assertTrue(definition(source, 0).isEmpty());
        assertTrue(definition(source, source.indexOf("dato")).isEmpty());
        String grouped = "(a, b): [1, 2]";
        assertTrue(definition(grouped, grouped.indexOf("a")).isEmpty());
    }

    @Test
    void invalidSyntaxAndOutOfDocumentPositionsHaveNoOrigin() {
        String invalid = "x: 1\nx(";
        assertTrue(definition(invalid, invalid.lastIndexOf("x")).isEmpty());
        String source = "x: 1\nx";
        assertTrue(definition(source, -1).isEmpty());
        assertTrue(definition(source, source.length()).isEmpty());
    }

    @Test
    void contextMemberWriteReplacingSlotValueInvalidatesMemberProvenance() {
        String source = "obj: {\n  dato: 1\n}\ncontext.obj = {\n  otro: 2\n}\nobj.dato";
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void contextMemberWriteWithSameNamedMemberNeverResolvesToTheOldDeclaration() {
        String source = "obj: {\n  dato: 1\n}\ncontext.obj = {\n  dato: 2\n}\nobj.dato";
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
    }

    @Test
    void contextMemberWriteDoesNotKeepUnprovenSlotOrigin() {
        String source = "obj: {\n  dato: 1\n}\ncontext.obj = {\n  otro: 2\n}\nobj";
        assertTrue(definition(source, source.lastIndexOf("obj")).isEmpty());
        // The write receiver is the context, not a read of the slot.
        assertTrue(definition(source, source.indexOf("obj =")).isEmpty());
        String before = "obj: {\n  dato: 1\n}\nobj\ncontext.obj = 2";
        assertProven(before, before.indexOf("obj\ncontext"), 0, "obj");
    }

    @Test
    void contextMemberCreationInvalidatesSlotAndMemberFacts() {
        String source = "obj: {\n  dato: 1\n}\ncontext.otro: 2\nobj.dato\nobj";
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
        assertTrue(definition(source, source.lastIndexOf("obj")).isEmpty());
    }

    @Test
    void thisMemberWriteInvalidatesFacts() {
        String source = "obj: {\n  dato: 1\n}\nthis.obj = 2\nobj.dato";
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
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
        assertTrue(definition(source, source.lastIndexOf("dato")).isEmpty());
        String creation = "f: (o) => {\n  obj: {\n    dato: 1\n  }\n  o.campo: 3\n  obj.dato\n}";
        assertTrue(definition(creation, creation.lastIndexOf("dato")).isEmpty());
    }

    private ProtosStaticDefinitionResult assertProven(
            String source, int reference, int declaration, String name) {
        ProtosStaticDefinitionResult result = definition(source, reference).orElseThrow();
        assertEquals(new SourceSpan(reference, reference + name.length()), result.referenceSpan());
        assertEquals(1, result.targets().size());
        assertEquals(
                new SourceSpan(declaration, declaration + name.length()),
                result.targets().get(0).span());
        return result;
    }

    private Optional<ProtosStaticDefinitionResult> definition(String source, int sourceOffset) {
        return core.definition(new ProtosDocumentSnapshot("test.protos", 1L, source), sourceOffset);
    }

    private static String slice(String source, SourceSpan span) {
        return source.substring(span.startOffset(), span.endOffset());
    }
}
