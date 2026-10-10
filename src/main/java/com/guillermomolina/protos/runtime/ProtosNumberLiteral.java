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

package com.guillermomolina.protos.runtime;

import java.math.BigInteger;
import java.util.Objects;

public final class ProtosNumberLiteral {
    private ProtosNumberLiteral() {}

    /**
     * The exact host descriptor of a number literal spelling: a {@link Double} for a Float
     * literal, a {@link Long} for an Integer literal within the signed-64 range, and a {@link
     * BigInteger} only for an Integer literal beyond that range. A descriptor is never a guest
     * value: lowering decides from it whether the literal can stay a primitive carrier, and the
     * executable code mints the guest Integer or Float (a large Integer with the executing
     * Prelude, I091 / PLAT056 Candidate C).
     *
     * <p>The spelling must match a {@code number-literal} production of the grammar exactly,
     * including {@code _} separators only between two digits; anything else, such as a
     * non-ASCII digit, is rejected rather than reinterpreted.
     *
     * @throws NumberFormatException when {@code spelling} is not a grammar number literal
     */
    public static Object parse(String spelling) {
        Objects.requireNonNull(spelling, "spelling");
        int radix = radixOf(spelling);
        if (radix != 10) {
            return parseInteger(digitsOf(spelling, 2, spelling.length(), radix), radix);
        }

        int fraction = spelling.indexOf('.');
        int exponent = Math.max(spelling.indexOf('e'), spelling.indexOf('E'));
        int integralEnd = fraction >= 0 ? fraction : exponent >= 0 ? exponent : spelling.length();
        String integral = digitsOf(spelling, 0, integralEnd, 10);
        if (fraction < 0 && exponent < 0) {
            return parseInteger(integral, 10);
        }

        StringBuilder normalized = new StringBuilder(spelling.length()).append(integral);
        if (fraction >= 0) {
            int fractionEnd = exponent >= 0 ? exponent : spelling.length();
            normalized.append('.').append(digitsOf(spelling, fraction + 1, fractionEnd, 10));
        }
        if (exponent >= 0) {
            int digits = exponent + 1;
            normalized.append('e');
            if (digits < spelling.length()
                    && (spelling.charAt(digits) == '+' || spelling.charAt(digits) == '-')) {
                normalized.append(spelling.charAt(digits));
                digits++;
            }
            normalized.append(digitsOf(spelling, digits, spelling.length(), 10));
        }
        return Double.parseDouble(normalized.toString());
    }

    private static int radixOf(String spelling) {
        if (spelling.length() < 2 || spelling.charAt(0) != '0') {
            return 10;
        }
        return switch (spelling.charAt(1)) {
            case 'x', 'X' -> 16;
            case 'o', 'O' -> 8;
            case 'b', 'B' -> 2;
            default -> 10;
        };
    }

    /**
     * The digits of {@code spelling[from, to)} without separators, after checking that the run
     * is a grammar digit sequence: one or more ASCII digits of {@code radix}, where each {@code
     * _} lies between two digits.
     */
    private static String digitsOf(String spelling, int from, int to, int radix) {
        StringBuilder digits = new StringBuilder(to - from);
        boolean previousIsDigit = false;
        for (int index = from; index < to; index++) {
            char character = spelling.charAt(index);
            if (character == '_') {
                if (!previousIsDigit || index + 1 >= to) {
                    throw malformed(spelling);
                }
                previousIsDigit = false;
                continue;
            }
            if (asciiDigit(character, radix) < 0) {
                throw malformed(spelling);
            }
            digits.append(character);
            previousIsDigit = true;
        }
        if (digits.isEmpty()) {
            throw malformed(spelling);
        }
        return digits.toString();
    }

    /**
     * The exact value of a validated digit run: a Long within the signed-64 range, accumulated
     * without arbitrary precision, otherwise a BigInteger.
     */
    private static Object parseInteger(String digits, int radix) {
        long value = 0L;
        for (int index = 0; index < digits.length(); index++) {
            int digit = asciiDigit(digits.charAt(index), radix);
            if (value > (Long.MAX_VALUE - digit) / radix) {
                return new BigInteger(digits, radix);
            }
            value = value * radix + digit;
        }
        return value;
    }

    /* The value of an ASCII digit of radix 2, 8, 10 or 16, otherwise -1. */
    private static int asciiDigit(char character, int radix) {
        int digit;
        if (character >= '0' && character <= '9') {
            digit = character - '0';
        } else if (character >= 'a' && character <= 'f') {
            digit = character - 'a' + 10;
        } else if (character >= 'A' && character <= 'F') {
            digit = character - 'A' + 10;
        } else {
            return -1;
        }
        return digit < radix ? digit : -1;
    }

    private static NumberFormatException malformed(String spelling) {
        return new NumberFormatException("not a Protos number literal: " + spelling);
    }
}
