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

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosComplexNumericSupport;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.io.ProtosFileValue;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSealedValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
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
        bytes.indexedAdd(octet(65));
        bytes.indexedAdd(octet(255));

        assertEquals("Bytes[65, 255]", inspector.render(bytes));
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
                                ProtosObjectValue.rootObject(), List.of("component"))));
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

    @Test
    void canonicalFractionsRenderExactly() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertEquals("1/5", inspector.render(fraction(1, 5, prelude)));
        assertEquals("1/2", inspector.render(fraction(2, 4, prelude)));
        assertEquals("-2/3", inspector.render(fraction(-2, 3, prelude)));
        assertEquals("-2/3", inspector.render(fraction(2, -3, prelude)));
        assertEquals("3", inspector.render(fraction(6, 2, prelude)));

        String largeDigits = "123456789012345678901234567890123456789";
        Object large = bigInteger(largeDigits, prelude);
        Object largeFraction =
                ProtosNumericValueSupport.divideIntegersExactly(large, integer(2), prelude);
        assertTrue(ProtosNumericValueSupport.isFraction(largeFraction));
        assertEquals(largeDigits + "/2", inspector.render(largeFraction));
        Object largeDenominator =
                ProtosNumericValueSupport.divideIntegersExactly(integer(1), large, prelude);
        assertEquals("1/" + largeDigits, inspector.render(largeDenominator));
    }

    @Test
    void canonicalComplexComponentsRenderExactly() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertEquals("Complex(0, 1)", inspector.render(complex(integer(0), integer(1), prelude)));
        assertEquals(
                "Complex(1/2, -0.0)",
                inspector.render(complex(fraction(1, 2, prelude), floating(-0.0), prelude)));
        assertEquals("3", inspector.render(complex(integer(3), integer(0), prelude)));
        assertEquals(
                "Complex(3, 0.0)",
                inspector.render(complex(integer(3), floating(0.0), prelude)));
        assertEquals(
                "Complex(3, -0.0)",
                inspector.render(complex(integer(3), floating(-0.0), prelude)));
        assertEquals(
                "Complex(NaN, Infinity)",
                inspector.render(
                        complex(floating(Double.NaN), floating(Double.POSITIVE_INFINITY), prelude)));
        assertEquals(
                "Complex(-Infinity, 1)",
                inspector.render(
                        complex(floating(Double.NEGATIVE_INFINITY), integer(1), prelude)));

        String largeDigits = "98765432109876543210987654321";
        assertEquals(
                "Complex(" + largeDigits + ", 1)",
                inspector.render(complex(bigInteger(largeDigits, prelude), integer(1), prelude)));
    }

    @Test
    void numericFamiliesInsideStructuresKeepTheirRepresentations() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object ratio = fraction(1, 5, prelude);
        Object point = complex(integer(0), integer(1), prelude);

        String array =
                inspector.render(
                        new ProtosArrayValue(ProtosObjectValue.rootObject(), List.of(ratio, point)));
        assertEquals("[1/5, Complex(0, 1)]", array);
        assertFalse(array.contains("<value>"), array);

        ProtosObjectValue parent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        parent.createLocalSlot("inherited", ratio);
        ProtosObjectValue object = new ProtosObjectValue(parent);
        object.createLocalSlot("ratio", ratio);
        object.createLocalSlot("point", point);
        object.createLocalSlot("self", object);

        String rendered = inspector.render(object);
        assertEquals("Object {ratio: 1/5, point: Complex(0, 1), self: <cycle>}", rendered);
        assertFalse(rendered.contains("inherited"), rendered);

        // The container reached at depth MAX_DEPTH is elided; its content is never traversed.
        ProtosObjectValue beyond = new ProtosObjectValue(ProtosObjectValue.rootObject());
        beyond.createLocalSlot("ratio", ratio);
        beyond.createLocalSlot("point", point);
        String deep = inspector.render(nest(beyond, ProtosDiagnosticInspector.MAX_DEPTH));
        assertTrue(deep.contains("next: ..."), deep);
        assertFalse(deep.contains("ratio"), deep);
        assertFalse(deep.contains("1/5"), deep);
        assertFalse(deep.contains("Complex("), deep);

        // Scalars held by the deepest traversed container keep their exact representations.
        ProtosObjectValue within = new ProtosObjectValue(ProtosObjectValue.rootObject());
        within.createLocalSlot("ratio", ratio);
        within.createLocalSlot("point", point);
        String allowed = inspector.render(nest(within, ProtosDiagnosticInspector.MAX_DEPTH - 1));
        assertTrue(allowed.contains("ratio: 1/5"), allowed);
        assertTrue(allowed.contains("point: Complex(0, 1)"), allowed);
        assertFalse(allowed.contains("..."), allowed);
    }

    @Test
    void delegatingAndUnrecognizedSealedObjectsAreNotNumericValues() throws IOException {
        ProtosPrelude prelude = corePrelude();

        ProtosObjectValue fakeFraction = new ProtosObjectValue(prelude.fractionPrototype());
        fakeFraction.createLocalSlot("numerator", integer(1));
        fakeFraction.createLocalSlot("denominator", integer(5));
        fakeFraction.freeze();
        assertFalse(ProtosNumericValueSupport.isFraction(fakeFraction));
        assertEquals(
                "Object {numerator: 1, denominator: 5}", inspector.render(fakeFraction));

        ProtosObjectValue fakeComplex = new ProtosObjectValue(prelude.complexPrototype());
        fakeComplex.createLocalSlot("re", integer(0));
        fakeComplex.createLocalSlot("im", integer(1));
        fakeComplex.freeze();
        assertFalse(ProtosComplexNumericSupport.isComplex(fakeComplex));
        assertEquals("Object {re: 0, im: 1}", inspector.render(fakeComplex));

        ProtosObjectValue template = new ProtosObjectValue(ProtosObjectValue.rootObject());
        template.createLocalSlot("visible", new ProtosStringValue("slot"));
        ProtosSealedValue sealed =
                ProtosSealedValue.seal(new Object(), template, "private-payload-1/5");
        String rendered = inspector.render(sealed);
        assertEquals("<value>", rendered);
        assertFalse(rendered.contains("private-payload"), rendered);
    }

    @Test
    void coreScalarFamilyCoverageIsExplicit() throws IOException {
        ProtosPrelude prelude = corePrelude();
        String largeDigits = "18446744073709551616";

        assertEquals("null", inspector.render(ProtosNullValue.INSTANCE));
        assertEquals("true", inspector.render(ProtosBooleanValue.TRUE));
        assertEquals("false", inspector.render(ProtosBooleanValue.FALSE));
        assertEquals("42", inspector.render(integer(42)));
        assertEquals(largeDigits, inspector.render(bigInteger(largeDigits, prelude)));
        assertEquals("1.5", inspector.render(floating(1.5)));
        assertEquals("1/5", inspector.render(fraction(1, 5, prelude)));
        assertEquals(
                "Complex(0, 1)", inspector.render(complex(integer(0), integer(1), prelude)));
        assertEquals("\"text\"", inspector.render(new ProtosStringValue("text")));

        assertEquals("<filesystem>", inspector.render(new ProtosFilesystemValue()));
        assertEquals("<file>", inspector.render(new ProtosFileValue()));
    }

    private static ProtosObjectValue nest(ProtosObjectValue leaf, int wrappers) {
        ProtosObjectValue current = leaf;
        for (int i = 0; i < wrappers; i++) {
            ProtosObjectValue outer = new ProtosObjectValue(ProtosObjectValue.rootObject());
            outer.createLocalSlot("next", current);
            current = outer;
        }
        return current;
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static Object integer(long value) {
        return ProtosNumericValueSupport.integer(value);
    }

    private static Object bigInteger(String digits, ProtosPrelude prelude) {
        Object value = ProtosNumericValueSupport.integerFromHost(new BigInteger(digits), prelude);
        assertTrue(ProtosNumericValueSupport.isLargeInteger(value));
        return value;
    }

    private static Object floating(double value) {
        return new ProtosFloatValue(value);
    }

    private static Object fraction(long numerator, long denominator, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.divideIntegersExactly(
                integer(numerator), integer(denominator), prelude);
    }

    private static Object complex(Object real, Object imaginary, ProtosPrelude prelude) {
        return ProtosComplexNumericSupport.normalize(real, imaginary, prelude);
    }

    private static ProtosIntegerValue octet(int value) {
        return new ProtosIntegerValue(value);
    }

}
