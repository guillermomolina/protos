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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.util.VersionInfo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** LIB014-1 evidence for the private Unicode 17.0.0 data facility of std:regex/Regex. */
final class ProtosRegexUnicodeFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void facilityIsFrozenStatelessMemberOfExactRegexKeyOnly() throws Exception {
        ProtosPrelude prelude = core();
        ProtosObjectValue facility = facility(prelude);

        assertTrue(facility.isFrozen());
        assertEquals(
                Set.of("propertyName", "ranges", "caseClosure"),
                facility.localSlotsSnapshot().keySet());
        assertTrue(prelude.isStandardModuleMemberForRuntime(facility));
        assertSame(facility, facility(prelude));
        assertEquals(VersionInfo.getInstance(17, 0, 0, 0), UCharacter.getUnicodeVersion());

        ProtosObjectValue otherContext = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(
                new ProtosModuleKey("std:regex/Other"), otherContext);
        assertFalse(otherContext.hasLocalSlot(ProtosRegexUnicodeFacility.BOOTSTRAP_SLOT));
    }

    @Test
    void propertyNamesResolveLooselyToCanonicalLongNames() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue facility = facility(prelude);

        assertEquals("General_Category", propertyName(facility, "gc", activation));
        assertEquals("General_Category", propertyName(facility, "general category", activation));
        assertEquals("Script_Extensions", propertyName(facility, "scx", activation));
        assertEquals("Alphabetic", propertyName(facility, "Alpha", activation));
        assertEquals("White_Space", propertyName(facility, "WSpace", activation));
        assertSame(
                ProtosNullValue.INSTANCE,
                invoke(facility, "propertyName", activation, new ProtosStringValue("Nope")));
    }

    @Test
    void rangesAnswerFrozenScalarSetsOrNull() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue facility = facility(prelude);

        List<int[]> upper = ranges(facility, "General_Category", "Lu", activation);
        assertTrue(contains(upper, 'A'));
        assertFalse(contains(upper, 'a'));
        assertTrue(contains(ranges(facility, "General_Category", "L", activation), 'a'));
        assertTrue(contains(ranges(facility, "Script", "Greek", activation), 0x03B1));
        assertTrue(contains(ranges(facility, "White_Space", "Yes", activation), 0x2028));

        List<int[]> sidetic = ranges(facility, "Script", "Sidetic", activation);
        assertFalse(sidetic.isEmpty(), "Unicode 17.0.0 assigns Sidetic letters");
        for (int[] range : sidetic) {
            assertTrue(range[0] >= 0x10940 && range[1] <= 0x1095F);
        }

        assertTrue(ranges(facility, "General_Category", "Cs", activation).isEmpty());
        assertFalse(contains(ranges(facility, "General_Category", "Cn", activation), 0xD800));

        assertSame(
                ProtosNullValue.INSTANCE,
                invoke(facility, "ranges", activation,
                        new ProtosStringValue("General_Category"), new ProtosStringValue("Nope")));
        assertSame(
                ProtosNullValue.INSTANCE,
                invoke(facility, "ranges", activation,
                        new ProtosStringValue("Basic_Emoji"), new ProtosStringValue("Yes")));
        assertThrows(
                ProtosSignalException.class,
                () -> invoke(facility, "ranges", activation,
                        new ProtosStringValue("Script"), new ProtosStringValue("")));
    }

    @Test
    void caseClosureFollowsSimpleCaseFolding() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue facility = facility(prelude);

        List<int[]> kelvin = caseClosure(facility, prelude, activation, 'k', 'k');
        assertTrue(contains(kelvin, 'K'));
        assertTrue(contains(kelvin, 'k'));
        assertTrue(contains(kelvin, 0x212A));
        assertFalse(contains(kelvin, 'j'));

        List<int[]> sharpS = caseClosure(facility, prelude, activation, 0x1E9E, 0x1E9E);
        assertTrue(contains(sharpS, 0x00DF));
        assertFalse(contains(sharpS, 's'), "simple folding never maps a scalar to a sequence");

        List<int[]> longS = caseClosure(facility, prelude, activation, 0x017F, 0x017F);
        assertTrue(contains(longS, 's'));
        assertTrue(contains(longS, 'S'));

        List<int[]> digits = caseClosure(facility, prelude, activation, '0', '9');
        assertEquals(1, digits.size());

        assertThrows(
                ProtosSignalException.class,
                () -> invoke(facility, "caseClosure", activation,
                        prelude.newFrozenArray(List.of(new ProtosIntegerValue(2)))));
        assertThrows(
                ProtosSignalException.class,
                () -> invoke(facility, "caseClosure", activation,
                        prelude.newFrozenArray(
                                List.of(new ProtosIntegerValue(9), new ProtosIntegerValue(1)))));
        assertThrows(
                ProtosSignalException.class,
                () -> invoke(facility, "caseClosure", activation,
                        prelude.newFrozenArray(
                                List.of(new ProtosIntegerValue(0),
                                        new ProtosIntegerValue(0x110000)))));
    }

    private static String propertyName(
            ProtosObjectValue facility, String alias, ProtosActivation activation)
            throws Exception {
        return assertInstanceOf(
                        ProtosStringValue.class,
                        invoke(facility, "propertyName", activation, new ProtosStringValue(alias)))
                .value();
    }

    private static List<int[]> ranges(
            ProtosObjectValue facility, String property, String value, ProtosActivation activation)
            throws Exception {
        return pairs(
                invoke(facility, "ranges", activation,
                        new ProtosStringValue(property), new ProtosStringValue(value)));
    }

    private static List<int[]> caseClosure(
            ProtosObjectValue facility,
            ProtosPrelude prelude,
            ProtosActivation activation,
            int low,
            int high)
            throws Exception {
        return pairs(
                invoke(facility, "caseClosure", activation,
                        prelude.newFrozenArray(
                                List.of(new ProtosIntegerValue(low), new ProtosIntegerValue(high)))));
    }

    private static List<int[]> pairs(Object result) {
        ProtosArrayValue array = assertInstanceOf(ProtosArrayValue.class, result);
        assertTrue(array.isFrozen());
        int size = array.indexedSizeForRuntime();
        assertEquals(0, size % 2);
        List<int[]> pairs = new ArrayList<>();
        int previousHigh = -2;
        for (int index = 0; index < size; index += 2) {
            int low = bound(array.indexedAtForRuntime(index));
            int high = bound(array.indexedAtForRuntime(index + 1));
            assertTrue(low <= high);
            assertTrue(low > previousHigh + 1, "ranges are ascending, disjoint, non-adjacent");
            assertFalse(low <= 0xDFFF && high >= 0xD800, "surrogates are never members");
            previousHigh = high;
            pairs.add(new int[] {low, high});
        }
        return pairs;
    }

    private static int bound(Object value) {
        return assertInstanceOf(ProtosIntegerValue.class, value).intValueExactForRuntime();
    }

    private static boolean contains(List<int[]> ranges, int scalar) {
        for (int[] range : ranges) {
            if (range[0] <= scalar && scalar <= range[1]) {
                return true;
            }
        }
        return false;
    }

    private static Object invoke(
            ProtosObjectValue facility,
            String selector,
            ProtosActivation activation,
            Object... arguments)
            throws Exception {
        return ProtosInvocation.invokeMessage(facility, selector, List.of(arguments), activation);
    }

    private static ProtosObjectValue facility(ProtosPrelude prelude) {
        return ProtosStandardModuleMemberTestSupport.member(
                prelude,
                ProtosRegexUnicodeFacility.MODULE_KEY.canonicalId(),
                ProtosRegexUnicodeFacility.BOOTSTRAP_SLOT);
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }
}
