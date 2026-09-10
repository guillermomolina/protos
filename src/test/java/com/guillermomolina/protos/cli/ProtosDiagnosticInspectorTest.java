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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
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

        assertTrue(rendered.startsWith("[null, null"), rendered);
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
}
