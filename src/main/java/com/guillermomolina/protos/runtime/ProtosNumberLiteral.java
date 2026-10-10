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
     * The value of a number literal: a {@link ProtosFloatValue}, a {@link ProtosIntegerValue}
     * within the signed-64 range, or, for an Integer literal outside that range, its exact value
     * as a host {@link BigInteger}. The latter is a lowering-time descriptor, not a guest value:
     * the large Integer is an ordinary guest object owned by the executing Prelude, so the
     * executable code mints it when the literal is evaluated (I091 / PLAT056 Candidate C).
     */
    public static Object materialize(String spelling) {
        Objects.requireNonNull(spelling, "spelling");
        String normalized = spelling.replace("_", "");

        if (isFloat(normalized)) {
            return new ProtosFloatValue(Double.parseDouble(normalized));
        }

        int radix = 10;
        int digitsStart = 0;
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            radix = 16;
            digitsStart = 2;
        } else if (normalized.startsWith("0b") || normalized.startsWith("0B")) {
            radix = 2;
            digitsStart = 2;
        } else if (normalized.startsWith("0o") || normalized.startsWith("0O")) {
            radix = 8;
            digitsStart = 2;
        }

        String digits = normalized.substring(digitsStart);
        long exact = parseNonNegativeLong(digits, radix);
        if (exact >= 0L) {
            return new ProtosIntegerValue(exact);
        }
        return new BigInteger(digits, radix);
    }

    /**
     * The exact value of a non-empty digit run when it fits the signed-long range, otherwise
     * -1. Digits are recognized exactly as {@link Long#parseLong(String, int)} recognizes them;
     * runs that overflow or are not plain digits are left to the arbitrary-precision parser,
     * which also reports malformed spellings.
     */
    private static long parseNonNegativeLong(String digits, int radix) {
        if (digits.isEmpty()) {
            return -1L;
        }
        long value = 0L;
        for (int index = 0; index < digits.length(); index++) {
            int digit = Character.digit(digits.charAt(index), radix);
            if (digit < 0 || value > (Long.MAX_VALUE - digit) / radix) {
                return -1L;
            }
            value = value * radix + digit;
        }
        return value;
    }

    private static boolean isFloat(String spelling) {
        if (spelling.startsWith("0x")
                || spelling.startsWith("0X")
                || spelling.startsWith("0b")
                || spelling.startsWith("0B")
                || spelling.startsWith("0o")
                || spelling.startsWith("0O")) {
            return false;
        }
        return spelling.indexOf('.') >= 0
                || spelling.indexOf('e') >= 0
                || spelling.indexOf('E') >= 0;
    }
}
