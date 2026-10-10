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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.VersionInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Private Unicode 17.0.0 character data for {@code std:regex/Regex} (LIB014, D187).
 *
 * <p>Following the D101 pattern, regex syntax, the accepted property set, bare-name precedence,
 * class algebra and every other policy stay in Protos; this facility owns only the irreducible
 * Unicode data lookups. The frozen, stateless facility is provisioned as a standard initial member
 * of the exact {@code std:regex/Regex} module, which captures it lexically and removes the
 * bootstrap slot during initialization, so it never belongs to the published module surface.
 *
 * <p>Character sets cross this boundary as frozen Arrays of Integers {@code [lo0, hi0, lo1, hi1,
 * ...]} of ascending, disjoint, non-adjacent inclusive Unicode scalar ranges. Surrogate code
 * points are never members because the regex matching unit is the Unicode scalar.
 *
 * <p>Unicode data comes from ICU4J and is refused unless ICU reports exactly Unicode 17.0.0, the
 * same guard used by the default grapheme segmentation; JDK character tables are never consulted.
 */
public final class ProtosRegexUnicodeFacility {
    public static final ProtosModuleKey MODULE_KEY = new ProtosModuleKey("std:regex/Regex");
    public static final String BOOTSTRAP_SLOT = "_regexUnicodeFacility";

    private static final VersionInfo REQUIRED_UNICODE = VersionInfo.getInstance(17, 0, 0, 0);
    private static final int MAX_SCALAR = 0x10FFFF;
    private static final int FIRST_SURROGATE = 0xD800;
    private static final int LAST_SURROGATE = 0xDFFF;

    private ProtosRegexUnicodeFacility() {}

    /**
     * Creates the frozen, stateless facility.
     *
     * <ul>
     *   <li>{@code propertyName(alias)} answers the canonical long Unicode property name for a
     *       loosely matched property alias, or {@code null} when the alias names no property;
     *   <li>{@code ranges(property, value)} answers the code point set of {@code property=value}
     *       for a loosely matched value alias, or {@code null} when the value is invalid or the
     *       property is not a code point property;
     *   <li>{@code caseClosure(ranges)} answers the smallest superset closed under Unicode simple
     *       case folding: every scalar whose simple case folding equals that of a member.
     * </ul>
     */
    public static ProtosObjectValue createFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facility.createLocalSlot(
                "propertyName",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> propertyName(activation, supplied)));
        facility.createLocalSlot(
                "ranges",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ranges(activation, supplied)));
        facility.createLocalSlot(
                "caseClosure",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> caseClosure(activation, supplied)));
        return facility.freeze();
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object propertyName(ProtosActivation activation, List<?> supplied) {
        requireUnicode17();
        if (supplied.size() != 1 || !(supplied.get(0) instanceof ProtosStringValue alias)) {
            throw invalid(activation);
        }
        String name;
        try {
            name = UCharacter.getPropertyName(
                    UCharacter.getPropertyEnum(alias.value()), UProperty.NameChoice.LONG);
        } catch (IllegalArgumentException unknown) {
            return ProtosNullValue.INSTANCE;
        }
        return name == null ? ProtosNullValue.INSTANCE : new ProtosStringValue(name);
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object ranges(ProtosActivation activation, List<?> supplied) {
        requireUnicode17();
        if (supplied.size() != 2
                || !(supplied.get(0) instanceof ProtosStringValue property)
                || !(supplied.get(1) instanceof ProtosStringValue value)
                || value.value().isEmpty()) {
            throw invalid(activation);
        }
        UnicodeSet set = new UnicodeSet();
        try {
            set.applyPropertyAlias(property.value(), value.value());
        } catch (IllegalArgumentException unknown) {
            return ProtosNullValue.INSTANCE;
        }
        if (set.hasStrings()) {
            return ProtosNullValue.INSTANCE;
        }
        return toArray(activation, set);
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object caseClosure(ProtosActivation activation, List<?> supplied) {
        requireUnicode17();
        if (supplied.size() != 1 || !(supplied.get(0) instanceof ProtosArrayValue array)) {
            throw invalid(activation);
        }
        UnicodeSet set = fromArray(activation, array);
        UnicodeSet closed = new UnicodeSet(set);
        int[] members = CaseOrbits.MEMBERS;
        for (int index = 0; index < members.length; index++) {
            if (set.contains(members[index])) {
                for (int equivalent : CaseOrbits.ORBITS[index]) {
                    closed.add(equivalent);
                }
            }
        }
        return toArray(activation, closed);
    }

    private static UnicodeSet fromArray(ProtosActivation activation, ProtosArrayValue array) {
        List<Object> values = array.indexedSnapshot();
        if (values.size() % 2 != 0) {
            throw invalid(activation);
        }
        UnicodeSet set = new UnicodeSet();
        for (int index = 0; index < values.size(); index += 2) {
            int low = scalarBound(activation, values.get(index));
            int high = scalarBound(activation, values.get(index + 1));
            if (low > high) {
                throw invalid(activation);
            }
            set.add(low, high);
        }
        return set;
    }

    private static int scalarBound(ProtosActivation activation, Object value) {
        if (ProtosNumericValueSupport.isIntegerInIntRange(value)) {
            int bound = ProtosNumericValueSupport.exactInt(value);
            if (bound >= 0 && bound <= MAX_SCALAR) {
                return bound;
            }
        }
        throw invalid(activation);
    }

    private static ProtosArrayValue toArray(ProtosActivation activation, UnicodeSet set) {
        UnicodeSet scalars = new UnicodeSet(set).remove(FIRST_SURROGATE, LAST_SURROGATE);
        List<Object> bounds = new ArrayList<>(scalars.getRangeCount() * 2);
        for (int index = 0; index < scalars.getRangeCount(); index++) {
            bounds.add(ProtosNumericValueSupport.integer(scalars.getRangeStart(index)));
            bounds.add(ProtosNumericValueSupport.integer(scalars.getRangeEnd(index)));
        }
        return prelude(activation).newFrozenArray(bounds);
    }

    private static ProtosPrelude prelude(ProtosActivation activation) {
        return activation
                .prelude()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Regex Unicode data requires caller Core prelude"));
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static void requireUnicode17() {
        VersionInfo actual = UCharacter.getUnicodeVersion();
        if (actual.compareTo(REQUIRED_UNICODE) != 0) {
            throw new IllegalStateException(
                    "Regex Unicode data requires Unicode 17.0.0 data; ICU reports " + actual);
        }
    }

    /**
     * Simple-case-folding equivalence classes with more than one member, derived once from the
     * guarded Unicode 17.0.0 data and immutable afterwards.
     */
    private static final class CaseOrbits {
        /** Ascending scalars that share their simple case folding with another scalar. */
        static final int[] MEMBERS;
        /** {@code ORBITS[i]} lists every scalar whose simple case folding equals MEMBERS[i]'s. */
        static final int[][] ORBITS;

        static {
            requireUnicode17();
            TreeMap<Integer, TreeSet<Integer>> byFolding = new TreeMap<>();
            for (int scalar = 0; scalar <= MAX_SCALAR; scalar++) {
                if (scalar == FIRST_SURROGATE) {
                    scalar = LAST_SURROGATE;
                    continue;
                }
                int folded = UCharacter.foldCase(scalar, true);
                if (folded != scalar) {
                    TreeSet<Integer> orbit =
                            byFolding.computeIfAbsent(folded, ignored -> new TreeSet<>());
                    // Simple case folding is idempotent, so the folding target is a member.
                    orbit.add(folded);
                    orbit.add(scalar);
                }
            }
            TreeMap<Integer, int[]> orbitOf = new TreeMap<>();
            for (TreeSet<Integer> orbit : byFolding.values()) {
                int[] members = orbit.stream().mapToInt(Integer::intValue).toArray();
                for (int member : members) {
                    orbitOf.put(member, members);
                }
            }
            MEMBERS = orbitOf.keySet().stream().mapToInt(Integer::intValue).toArray();
            ORBITS = orbitOf.values().toArray(new int[0][]);
        }

        private CaseOrbits() {}
    }
}
