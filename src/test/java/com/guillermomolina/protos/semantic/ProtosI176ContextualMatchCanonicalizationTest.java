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

package com.guillermomolina.protos.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMapConstruction;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.source.SourceSpan;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * I176 / D263-C: contextual {@code match(subject) { ... }} canonicalizes to
 * the ordinary {@code subject.caseOf(%{ ... })} send, with every implicit
 * action an ordinary parameterless Closure and no contextual provenance.
 */
class ProtosI176ContextualMatchCanonicalizationTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();

    @Test
    void lowersToOrdinaryCaseOfSendWithOneMapArgument() {
        String source = "match(value) {\n    0: 11\n    1: 23\n    Any: 53\n}";
        CanonicalSend send = assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));

        assertEquals("caseOf", send.message());
        assertFalse(send.contextual());
        assertEquals(new SourceSpan(0, source.length()), send.span());
        assertEquals(
                "value",
                assertInstanceOf(CanonicalLookup.class, send.receiver()).name());
        assertEquals(1, send.arguments().size());

        CanonicalMapConstruction map =
                assertInstanceOf(CanonicalMapConstruction.class, send.arguments().get(0));
        assertEquals(
                "Map",
                assertInstanceOf(CanonicalLookup.class, map.factory()).name());
        assertEquals(
                new SourceSpan(source.indexOf('{'), source.length()),
                map.span());
        assertEquals(
                List.of("0", "1"),
                map.entries().subList(0, 2).stream()
                        .map(entry -> assertInstanceOf(CanonicalLiteral.class, entry.key()).value())
                        .toList());
        assertEquals(
                "Any",
                assertInstanceOf(CanonicalLookup.class, map.entries().get(2).key()).name());

        for (CanonicalMapConstruction.Entry entry : map.entries()) {
            CanonicalClosure action = assertInstanceOf(CanonicalClosure.class, entry.value());
            assertTrue(action.parameters().isEmpty());
            assertEquals(1, action.body().expressions().size());
            assertEquals(action.span(), action.body().span());
            assertEquals(action.span(), action.body().expressions().get(0).span());
        }
    }

    @Test
    void matchesExplicitCaseOfExpansionStructurally() {
        assertEquivalent(
                "match(value) {\n    0: 11\n    1: 23\n    2: 37\n    Any: 53\n}",
                "value.caseOf(%{\n    0: () => 11\n    1: () => 23\n"
                        + "    2: () => 37\n    Any: () => 53\n})");
        assertEquivalent(
                "match(value) {\n    [1, Capture]: x => x\n"
                        + "    [Capture, Capture]: (x, y) => x + y\n    Any: null\n}",
                "value.caseOf(%{\n    [1, Capture]: x => x\n"
                        + "    [Capture, Capture]: (x, y) => x + y\n    Any: () => null\n})");
        assertEquivalent("match(value) {}", "value.caseOf(%{})");
        assertEquivalent(
                "match(next()) { 0: a; Any: b }",
                "next().caseOf(%{ 0: () => a; Any: () => b })");
    }

    @Test
    void preservesExplicitParametersDefaultsAndRest() {
        CanonicalClosure action = onlyAction(
                "match(value) { Any: (first, second: 2, ...rest) => rest }");
        List<CanonicalParameter> parameters = action.parameters();
        assertEquals(3, parameters.size());
        assertEquals("first", parameters.get(0).name());
        assertEquals(
                "2",
                assertInstanceOf(
                                CanonicalLiteral.class,
                                parameters.get(1).defaultValue().orElseThrow())
                        .value());
        assertTrue(parameters.get(2).rest());
    }

    @Test
    void groupingDoesNotChangeExplicitClosureClassification() {
        CanonicalClosure grouped = onlyAction("match(value) { [Capture]: (x => x) }");
        assertEquals(1, grouped.parameters().size());
        assertEquals(
                "x",
                assertInstanceOf(CanonicalLookup.class, grouped.body().expressions().get(0))
                        .name());
        assertEquivalent(
                "match(value) { [Capture]: ((x) => x) }",
                "value.caseOf(%{ [Capture]: ((x) => x) })");
    }

    @Test
    void wrapsClosureValuedLookupInsteadOfInvokingIt() {
        CanonicalClosure action = onlyAction("match(value) { Any: handler }");
        assertTrue(action.parameters().isEmpty());
        assertEquals(
                "handler",
                assertInstanceOf(CanonicalLookup.class, action.body().expressions().get(0))
                        .name());
    }

    @Test
    void wrapsClosureInvocationAsAnExpression() {
        CanonicalClosure action = onlyAction("match(value) { Any: handler(1) }");
        assertTrue(action.parameters().isEmpty());
        CanonicalCall call =
                assertInstanceOf(CanonicalCall.class, action.body().expressions().get(0));
        assertEquals(
                "handler",
                assertInstanceOf(CanonicalLookup.class, call.receiver()).name());
    }

    @Test
    void objectRightHandSideBecomesObjectConstructionInsideImplicitClosure() {
        CanonicalClosure action = onlyAction("match(value) { 0: { x: 10 } }");
        assertTrue(action.parameters().isEmpty());
        assertInstanceOf(CanonicalObject.class, action.body().expressions().get(0));
        assertEquivalent(
                "match(value) { 0: { x: 10 } }",
                "value.caseOf(%{ 0: () => ({ x: 10 }) })");
    }

    @Test
    void explicitOuterClosureMayReturnAnotherClosure() {
        CanonicalClosure action = onlyAction("match(value) { Any: () => () => 1 }");
        assertTrue(action.parameters().isEmpty());
        CanonicalClosure inner =
                assertInstanceOf(CanonicalClosure.class, action.body().expressions().get(0));
        assertTrue(inner.parameters().isEmpty());

        CanonicalClosure implicit = onlyAction("match(value) { Any: (() => 1)() }");
        assertInstanceOf(CanonicalCall.class, implicit.body().expressions().get(0));
    }

    @Test
    void nestedContextualMatchesLowerRecursively() {
        assertEquivalent(
                "match(a) { 0: match(b) { 1: 2 }; Any: 3 }",
                "a.caseOf(%{ 0: () => b.caseOf(%{ 1: () => 2 }); Any: () => 3 })");
    }

    @Test
    void groupedOrdinaryMatchCallStaysOrdinary() {
        CanonicalCall grouped = assertInstanceOf(
                CanonicalCall.class, canonicalizeOnly("(match)(value) { body }"));
        assertEquals(2, grouped.arguments().size());
        assertInstanceOf(CanonicalCall.class, canonicalizeOnly("match(value)"));
    }

    @Test
    void noMatchingCanonicalNodeExists() {
        assertFalse(
                Arrays.stream(CanonicalExpression.class.getPermittedSubclasses())
                        .map(Class::getSimpleName)
                        .anyMatch(name -> name.contains("Match") || name.contains("Case")),
                "contextual match must lower to ordinary canonical sends");
    }

    @Test
    void postfixSuffixesApplyToTheCompleteSelection() {
        assertEquivalent(
                "match(subject) { Any: handler }.value",
                "subject.caseOf(%{ Any: () => handler }).value");
        assertEquivalent(
                "match(subject) { Any: handler }(42)",
                "subject.caseOf(%{ Any: () => handler })(42)");
        assertEquivalent(
                "match(subject) { Any: [1, 2] }[0]",
                "subject.caseOf(%{ Any: () => [1, 2] })[0]");
    }

    @Test
    void subjectSlotCreationIsAnOrdinaryReceiverExpression() {
        assertEquivalent(
                "match(flag: true) { true: 11; Any: 23 }",
                "(flag: true).caseOf(%{ true: () => 11; Any: () => 23 })");
    }

    @Test
    void objectBodyActionKeepsItsSlotsInsideTheImplicitClosure() {
        assertEquivalent(
                "match(value) {\n"
                        + "    1: {\n"
                        + "        local: 10\n"
                        + "        copy: local\n"
                        + "    }\n"
                        + "    Any: null\n"
                        + "}",
                "value.caseOf(%{\n"
                        + "    1: () => ({\n"
                        + "        local: 10\n"
                        + "        copy: local\n"
                        + "    })\n"
                        + "    Any: () => null\n"
                        + "})");
    }

    @Test
    void nestedSelectionsInsideImplicitAndExplicitActions() {
        assertEquivalent(
                "match(a) { 0: match(b) { 1: 2 }; Any: () => match(c) { 3: 4 } }",
                "a.caseOf(%{ 0: () => b.caseOf(%{ 1: () => 2 }); "
                        + "Any: () => c.caseOf(%{ 3: () => 4 }) })");
        assertEquivalent(
                "match(match(a) { 0: b }) { match(c) { Any: 1 }: match(d) { Any: 2 } }",
                "a.caseOf(%{ 0: () => b }).caseOf(%{ "
                        + "c.caseOf(%{ Any: () => 1 }): () => d.caseOf(%{ Any: () => 2 }) })");
    }

    @Test
    void groupedExplicitClosuresStayExplicit() {
        assertEquivalent(
                "match(value) { [Capture, Capture]: ((x, y) => x + y); Any: (() => 0) }",
                "value.caseOf(%{ [Capture, Capture]: ((x, y) => x + y); Any: (() => 0) })");
    }

    @Test
    void implicitActionsKeepReturnsAndAssignmentsInsideTheClosure() {
        assertEquivalent(
                "match(value) { 1: ^2; Any: 3 }",
                "value.caseOf(%{ 1: () => ^2; Any: () => 3 })");
        assertEquivalent(
                "match(value) { 1: total = total + 1 }",
                "value.caseOf(%{ 1: () => total = total + 1 })");
    }

    @Test
    void everyContextualSelectionIsAnOrdinaryNonContextualCaseOfSend() {
        for (String source : List.of(
                "match(a) { 0: match(b) { 1: 2 } }",
                "match(match(a) { 0: b }) { match(c) { Any: 1 }: match(d) { Any: 2 } }",
                "match(subject) { Any: handler }(42)",
                "match(value) { 1: { local: 10 } }")) {
            int sends = countCaseOfSends(canonicalizeOnly(source));
            assertTrue(sends > 0, source);
            assertFalse(shape(canonicalizeOnly(source)).contains("contextual=true"), source);
        }
    }

    private static int countCaseOfSends(Object node) {
        int count = 0;
        if (node instanceof CanonicalSend send && send.message().equals("caseOf")) {
            assertFalse(send.contextual());
            assertEquals(1, send.arguments().size());
            assertInstanceOf(CanonicalMapConstruction.class, send.arguments().get(0));
            count++;
        }
        if (node instanceof List<?> list) {
            for (Object element : list) {
                count += countCaseOfSends(element);
            }
        } else if (node instanceof Optional<?> optional) {
            if (optional.isPresent()) {
                count += countCaseOfSends(optional.get());
            }
        } else if (node instanceof Record record) {
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                try {
                    count += countCaseOfSends(component.getAccessor().invoke(record));
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            }
        }
        return count;
    }

    private void assertEquivalent(String contextual, String explicit) {
        assertEquals(shape(canonicalizeOnly(explicit)), shape(canonicalizeOnly(contextual)));
    }

    private CanonicalClosure onlyAction(String source) {
        CanonicalSend send = assertInstanceOf(CanonicalSend.class, canonicalizeOnly(source));
        CanonicalMapConstruction map =
                assertInstanceOf(CanonicalMapConstruction.class, send.arguments().get(0));
        assertEquals(1, map.entries().size());
        return assertInstanceOf(CanonicalClosure.class, map.entries().get(0).value());
    }

    private CanonicalExpression canonicalizeOnly(String source) {
        SurfaceSequence program = new ProtosParser(source).parseProgram();
        assertEquals(1, program.expressions().size());
        return canonicalizer.canonicalize(program.expressions().get(0));
    }

    /** Span-free structural rendering of a canonical tree, local to this test. */
    private static String shape(Object node) {
        if (node instanceof List<?> list) {
            StringBuilder text = new StringBuilder("[");
            for (Object element : list) {
                text.append(shape(element)).append(',');
            }
            return text.append(']').toString();
        }
        if (node instanceof Optional<?> optional) {
            return optional.map(value -> "Some(" + shape(value) + ")").orElse("None");
        }
        if (node instanceof Record record) {
            StringBuilder text = new StringBuilder(record.getClass().getSimpleName()).append('(');
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                if (component.getType() == SourceSpan.class) {
                    continue;
                }
                try {
                    text.append(component.getName())
                            .append('=')
                            .append(shape(component.getAccessor().invoke(record)))
                            .append(';');
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            }
            return text.append(')').toString();
        }
        return String.valueOf(node);
    }
}
