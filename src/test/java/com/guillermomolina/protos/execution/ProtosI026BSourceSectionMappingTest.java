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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ProtosI026BSourceSectionMappingTest {
    @Test
    void rootSectionUsesBodySpanAndExactOwningSource() {
        Source source = source("0123456789");
        ProtosConstantNode body = new ProtosConstantNode(new SourceSpan(2, 7), "value");
        ProtosRootNode root = new ProtosRootNode(null, source, body);

        SourceSection section = root.getSourceSection();

        assertSame(source, section.getSource());
        assertEquals(2, section.getCharIndex());
        assertEquals(5, section.getCharLength());
        assertEquals("23456", section.getCharacters().toString());
    }

    @Test
    void adoptedExecutionNodeDerivesSectionFromRootOwnedSource() {
        Source source = source("abcdefghij");
        ProtosConstantNode child = new ProtosConstantNode(new SourceSpan(3, 6), "value");
        ProtosSequenceNode body =
                new ProtosSequenceNode(
                        new SourceSpan(1, 8),
                        new ProtosExpressionNode[] {child});

        assertNull(child.getSourceSection());

        ProtosRootNode root = new ProtosRootNode(null, source, body);
        root.getCallTarget();
        SourceSection section = child.getSourceSection();

        assertSame(source, section.getSource());
        assertEquals(3, section.getCharIndex());
        assertEquals(3, section.getCharLength());
        assertEquals("def", section.getCharacters().toString());
    }

    @Test
    void sourceLessRootAndUnadoptedNodeDoNotFabricateLocations() {
        ProtosConstantNode unadopted =
                new ProtosConstantNode(new SourceSpan(0, 1), "value");
        assertNull(unadopted.getSourceSection());

        ProtosConstantNode body = new ProtosConstantNode(new SourceSpan(0, 1), "value");
        ProtosRootNode sourceLessRoot = new ProtosRootNode(body);
        sourceLessRoot.getCallTarget();

        assertNull(sourceLessRoot.getSourceSection());
        assertNull(body.getSourceSection());
    }

    @Test
    void invalidRetainedSpanFailsClosedInsteadOfBeingClipped() {
        Source source = source("abc");
        ProtosConstantNode invalidChild =
                new ProtosConstantNode(new SourceSpan(1, 4), "value");
        ProtosSequenceNode body =
                new ProtosSequenceNode(
                        new SourceSpan(0, 3),
                        new ProtosExpressionNode[] {invalidChild});
        ProtosRootNode root = new ProtosRootNode(null, source, body);
        root.getCallTarget();

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, invalidChild::getSourceSection);
        assertTrue(failure.getMessage().contains("exceeds owning Truffle Source length 3"));
    }

    @Test
    void expressionBaseRetainsOnlySpanAndCompactInstrumentationTagState() {
        Field[] instanceFields =
                java.util.Arrays.stream(ProtosExpressionNode.class.getDeclaredFields())
                        .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                        .toArray(Field[]::new);

        assertEquals(2, instanceFields.length);
        assertTrue(java.util.Arrays.stream(instanceFields)
                .anyMatch(field -> field.getName().equals("span") && field.getType() == SourceSpan.class));
        assertTrue(java.util.Arrays.stream(instanceFields)
                .anyMatch(field -> field.getName().equals("instrumentationTags") && field.getType() == byte.class));
        assertTrue(java.util.Arrays.stream(instanceFields)
                .noneMatch(field -> field.getType() == Source.class || field.getType() == SourceSection.class));
    }

    private static Source source(String characters) {
        return Source.newBuilder(ProtosLanguage.ID, characters, "i026-b.protos")
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }
}
