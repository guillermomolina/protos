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

package com.guillermomolina.protos.lexer;

import java.text.Normalizer;

/** Unicode XID_Start/XID_Continue recognition derived from the JDK Unicode database. */
final class UnicodeXid {
    private UnicodeXid() {}

    static boolean isStart(int codePoint) {
        if (!isIdStart(codePoint)) {
            return false;
        }
        String normalized = Normalizer.normalize(Character.toString(codePoint), Normalizer.Form.NFKC);
        if (normalized.isEmpty()) {
            return false;
        }

        int index = 0;
        int first = normalized.codePointAt(index);
        if (!isIdStart(first)) {
            return false;
        }
        index += Character.charCount(first);

        while (index < normalized.length()) {
            int current = normalized.codePointAt(index);
            if (!isIdContinue(current)) {
                return false;
            }
            index += Character.charCount(current);
        }
        return true;
    }

    static boolean isContinue(int codePoint) {
        if (!isIdContinue(codePoint)) {
            return false;
        }
        String normalized = Normalizer.normalize(Character.toString(codePoint), Normalizer.Form.NFKC);
        if (normalized.isEmpty()) {
            return false;
        }

        for (int index = 0; index < normalized.length();) {
            int current = normalized.codePointAt(index);
            if (!isIdContinue(current)) {
                return false;
            }
            index += Character.charCount(current);
        }
        return true;
    }

    private static boolean isIdStart(int codePoint) {
        int type = Character.getType(codePoint);
        return switch (type) {
            case Character.UPPERCASE_LETTER,
                 Character.LOWERCASE_LETTER,
                 Character.TITLECASE_LETTER,
                 Character.MODIFIER_LETTER,
                 Character.OTHER_LETTER,
                 Character.LETTER_NUMBER -> true;
            default -> isOtherIdStart(codePoint);
        };
    }

    private static boolean isIdContinue(int codePoint) {
        if (isIdStart(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        return switch (type) {
            case Character.NON_SPACING_MARK,
                 Character.COMBINING_SPACING_MARK,
                 Character.DECIMAL_DIGIT_NUMBER,
                 Character.CONNECTOR_PUNCTUATION -> true;
            default -> isOtherIdContinue(codePoint);
        };
    }

    private static boolean isOtherIdStart(int codePoint) {
        return codePoint == 0x1885
            || codePoint == 0x1886
            || codePoint == 0x2118
            || codePoint == 0x212E
            || codePoint == 0x309B
            || codePoint == 0x309C;
    }

    private static boolean isOtherIdContinue(int codePoint) {
        return codePoint == 0x00B7
            || codePoint == 0x0387
            || codePoint >= 0x1369 && codePoint <= 0x1371
            || codePoint == 0x19DA;
    }
}
