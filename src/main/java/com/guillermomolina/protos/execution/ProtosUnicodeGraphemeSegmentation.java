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

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.VersionInfo;
import java.util.Locale;

final class ProtosUnicodeGraphemeSegmentation {
    private static final VersionInfo REQUIRED_UNICODE = VersionInfo.getInstance(17, 0, 0, 0);

    private ProtosUnicodeGraphemeSegmentation() {}

    static long count(String text) {
        requireUnicode17();

        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);

        long count = 0;
        for (int boundary = iterator.first(), next = iterator.next();
                next != BreakIterator.DONE;
                boundary = next, next = iterator.next()) {
            count++;
        }
        return count;
    }

    static String at(String text, int wanted) {
        requireUnicode17();

        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);

        int index = 0;
        int start = iterator.first();
        for (int end = iterator.next();
                end != BreakIterator.DONE;
                start = end, end = iterator.next()) {
            if (index == wanted) {
                return text.substring(start, end);
            }
            index++;
        }
        return null;
    }

    private static void requireUnicode17() {
        VersionInfo actual = UCharacter.getUnicodeVersion();
        if (actual.compareTo(REQUIRED_UNICODE) != 0) {
            throw new IllegalStateException(
                    "Unicode grapheme segmentation requires Unicode 17.0.0 data; ICU reports "
                            + actual);
        }
    }
}
