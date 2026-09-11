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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosByteRegionValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosFileValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosDiagnosticInspectorTest {
    private final ProtosDiagnosticInspector inspector = new ProtosDiagnosticInspector();

    @Test
    void stringsAreQuotedEscapedAndSourceLikeForDiagnostics() {
        assertEquals(
                "\"line\\n\\\"quoted\\\"\\\\tail\"",
                inspector.render(new ProtosStringValue("line\n\"quoted\"\\tail")));
    }

    @Test
    void ordinaryObjectsExposeOnlyLocalSlotsAndDetectCycles() {
        ProtosObjectValue parent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        parent.createLocalSlot("inherited", new ProtosStringValue("hidden"));

        ProtosObjectValue child = new ProtosObjectValue(parent);
        child.createLocalSlot("name", new ProtosStringValue("Alice"));
        child.createLocalSlot("self", child);

        String rendered = inspector.render(child);
        assertEquals("Object {name: \"Alice\", self: <cycle>}", rendered);
        assertFalse(rendered.contains("inherited"));
    }

    @Test
    void collectionItemAndOutputGrowthAreBounded() {
        List<Object> elements = new ArrayList<>();
        for (int i = 0; i < ProtosDiagnosticInspector.MAX_ITEMS + 10; i++) {
            elements.add(ProtosNullValue.INSTANCE);
        }

        String rendered =
                inspector.render(
                        new ProtosArrayValue(ProtosObjectValue.rootObject(), elements));

        assertTrue(rendered.startsWith("["), rendered);
        assertTrue(rendered.contains("\n"), rendered);
        assertTrue(rendered.contains("..."), rendered);
        assertTrue(rendered.length() <= ProtosDiagnosticInspector.MAX_OUTPUT_CHARS, rendered);
    }

    @Test
    void nestedObjectDepthIsBounded() {
        ProtosObjectValue current = new ProtosObjectValue(ProtosObjectValue.rootObject());
        for (int i = 0; i < ProtosDiagnosticInspector.MAX_DEPTH + 3; i++) {
            ProtosObjectValue outer = new ProtosObjectValue(ProtosObjectValue.rootObject());
            outer.createLocalSlot("next", current);
            current = outer;
        }

        String rendered = inspector.render(current);
        assertTrue(rendered.contains("..."), rendered);
        assertTrue(rendered.length() <= ProtosDiagnosticInspector.MAX_OUTPUT_CHARS, rendered);
    }

    @Test
    void closuresUseAStableDiagnosticFamilyLabelWithoutHostDetails() {
        ProtosClosureValue closure =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ProtosNullValue.INSTANCE);

        assertEquals("<closure>", inspector.render(closure));
    }

    @Test
    void byteSequencesExposeOnlyBoundedSemanticContent() {
        ProtosBytesValue bytes = new ProtosBytesValue(ProtosObjectValue.rootObject());
        bytes.indexedAdd(uint8(65));
        bytes.indexedAdd(uint8(255));
        ProtosByteRegionValue region =
                new ProtosByteRegionValue(List.of(uint8(1), uint8(2)));

        assertEquals("Bytes[65, 255]", inspector.render(bytes));
        assertEquals("ByteRegion[1, 2]", inspector.render(region));
    }

    @Test
    void authorityAndDisplayDeferredFamiliesRemainOpaque() {
        assertEquals("<filesystem>", inspector.render(new ProtosFilesystemValue()));
        assertEquals("<file>", inspector.render(new ProtosFileValue()));
        assertEquals(
                "<encoding>",
                inspector.render(
                        ProtosEncodingValue.portableForRuntime(
                                ProtosObjectValue.rootObject(),
                                ProtosEncodingValue.PortableKind.UTF8)));
        assertEquals(
                "<path>",
                inspector.render(
                        new ProtosPathValue(
                                ProtosObjectValue.rootObject(),
                                false,
                                List.of(new ProtosPathValue.Normal("component")))));
    }

    @Test
    void wideStructuredValueSwitchesToDeterministicMultilineLayout() {
        String first = "a".repeat(40);
        String second = "b".repeat(40);
        String third = "c".repeat(40);

        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        object.createLocalSlot("first", new ProtosStringValue(first));
        object.createLocalSlot("second", new ProtosStringValue(second));
        object.createLocalSlot("third", new ProtosStringValue(third));

        assertEquals(
                "Object {\n"
                        + "  first: \"" + first + "\",\n"
                        + "  second: \"" + second + "\",\n"
                        + "  third: \"" + third + "\"\n"
                        + "}",
                inspector.render(object));
    }

    @Test
    void multilineLayoutIndentsNestedStructuredValues() {
        String longText = "x".repeat(44);
        ProtosArrayValue items =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(
                                new ProtosStringValue(longText),
                                new ProtosStringValue(longText)));

        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        object.createLocalSlot("items", items);
        object.createLocalSlot("note", new ProtosStringValue(longText));

        assertEquals(
                "Object {\n"
                        + "  items: [\n"
                        + "    \"" + longText + "\",\n"
                        + "    \"" + longText + "\"\n"
                        + "  ],\n"
                        + "  note: \"" + longText + "\"\n"
                        + "}",
                inspector.render(object));
    }

    @Test
    void wideScalarStaysSingleLineBecausePrettyLayoutIsStructural() {
        String value = "x".repeat(ProtosDiagnosticInspector.MAX_COMPACT_CHARS + 8);
        String rendered = inspector.render(new ProtosStringValue(value));

        assertEquals("\"" + value + "\"", rendered);
        assertFalse(rendered.contains("\n"), rendered);
    }

    private static ProtosFixedIntegerValue uint8(int value) {
        return new ProtosFixedIntegerValue(
                ProtosFixedIntegerValue.Family.UINT8, BigInteger.valueOf(value));
    }

}
