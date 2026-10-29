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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.Normalizer2;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.List;

/**
 * Shared host-only Unicode facts read by the Protos IDNA2008 rules of {@code
 * std:network/internal/DnsNames} (I130-C3, D223-A1).
 *
 * <p>The two operations are installed on private facilities ({@code std:tls/Client}'s TLS
 * backend facility and the Core Resolver bridge). They supply raw ICU properties of one scalar and
 * the NFC test only: they validate no DNS name, perform no IDNA conversion or mapping, and decide
 * no admission. Every decision stays in Protos.
 */
public final class ProtosIdnaUnicodeFactsFacility {
    private ProtosIdnaUnicodeFactsFacility() {}

    /**
     * {@code codePoint(scalar)}: a frozen object of the ICU Unicode properties of one Unicode
     * scalar value that the RFC 5892/5893 rules in {@code Identity.protos} read: {@code
     * generalCategory}, {@code hangulSyllableType}, {@code joiningType} and {@code bidiClass} (short
     * property value aliases), {@code script} (long alias), {@code combiningClass} (Integer), the
     * Booleans {@code noncharacter}, {@code defaultIgnorable}, {@code whiteSpace} and {@code
     * joinControl}, and {@code stable}, whether {@code NFKC(casefold(NFKC(cp)))} is the scalar
     * itself. A non-scalar argument signals.
     *
     * <p>Every call reads every property, so one successful call proves that all ICU data it needs
     * is present: {@code uprops.icu} (category, script, binary properties), {@code ubidi.icu}
     * (Bidi_Class, Joining_Type), {@code ucase.icu} (case folding), {@code pnames.icu} (value
     * aliases), {@code nfc.nrm} (combining class, NFC) and {@code nfkc.nrm}. Native Image includes
     * them through the {@code icudata/*.icu}, {@code nfc.nrm} and {@code nfkc.nrm} resource globs.
     * Missing data fails the operation; there is no fallback.
     */
    @TruffleBoundary
    public static Object codePoint(ProtosActivation activation, List<?> supplied) {
        int scalar = scalar(activation, supplied.get(0));
        String text = new StringBuilder().appendCodePoint(scalar).toString();
        Normalizer2 nfkc = Normalizer2.getNFKCInstance();
        String folded = nfkc.normalize(UCharacter.foldCase(nfkc.normalize(text), true));
        ProtosObjectValue facts = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facts.createLocalSlot(
                "generalCategory",
                propertyValueName(
                        UProperty.GENERAL_CATEGORY,
                        UCharacter.getType(scalar),
                        UProperty.NameChoice.SHORT));
        facts.createLocalSlot(
                "hangulSyllableType",
                intPropertyName(scalar, UProperty.HANGUL_SYLLABLE_TYPE, UProperty.NameChoice.SHORT));
        facts.createLocalSlot(
                "joiningType",
                intPropertyName(scalar, UProperty.JOINING_TYPE, UProperty.NameChoice.SHORT));
        facts.createLocalSlot(
                "bidiClass",
                intPropertyName(scalar, UProperty.BIDI_CLASS, UProperty.NameChoice.SHORT));
        facts.createLocalSlot(
                "script", intPropertyName(scalar, UProperty.SCRIPT, UProperty.NameChoice.LONG));
        facts.createLocalSlot(
                "combiningClass",
                ProtosNumericValueSupport.integer(UCharacter.getCombiningClass(scalar)));
        facts.createLocalSlot(
                "noncharacter", binary(scalar, UProperty.NONCHARACTER_CODE_POINT));
        facts.createLocalSlot(
                "defaultIgnorable", binary(scalar, UProperty.DEFAULT_IGNORABLE_CODE_POINT));
        facts.createLocalSlot("whiteSpace", binary(scalar, UProperty.WHITE_SPACE));
        facts.createLocalSlot("joinControl", binary(scalar, UProperty.JOIN_CONTROL));
        facts.createLocalSlot("stable", bool(folded.equals(text)));
        return facts.freeze();
    }

    /** {@code isNfc(scalars)}: whether the Array of Unicode scalar values is in NFC. */
    @TruffleBoundary
    public static Object isNfc(ProtosActivation activation, List<?> supplied) {
        if (!(supplied.get(0) instanceof ProtosArrayValue array)) {
            throw invalid(activation);
        }
        StringBuilder text = new StringBuilder();
        for (Object element : array.indexedSnapshot()) {
            text.appendCodePoint(scalar(activation, element));
        }
        return bool(Normalizer2.getNFCInstance().isNormalized(text));
    }

    private static int scalar(ProtosActivation activation, Object value) {
        if (!ProtosNumericValueSupport.isIntegerInIntRange(value)) {
            throw invalid(activation);
        }
        int scalar = ProtosNumericValueSupport.exactInt(value);
        if (scalar < 0
                || scalar > Character.MAX_CODE_POINT
                || (scalar >= Character.MIN_SURROGATE && scalar <= Character.MAX_SURROGATE)) {
            throw invalid(activation);
        }
        return scalar;
    }

    private static ProtosStringValue intPropertyName(int scalar, int property, int nameChoice) {
        return propertyValueName(
                property, UCharacter.getIntPropertyValue(scalar, property), nameChoice);
    }

    private static ProtosStringValue propertyValueName(int property, int value, int nameChoice) {
        return new ProtosStringValue(UCharacter.getPropertyValueName(property, value, nameChoice));
    }

    private static ProtosBooleanValue binary(int scalar, int property) {
        return bool(UCharacter.hasBinaryProperty(scalar, property));
    }

    private static ProtosBooleanValue bool(boolean value) {
        return ProtosBooleanValue.of(value);
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
