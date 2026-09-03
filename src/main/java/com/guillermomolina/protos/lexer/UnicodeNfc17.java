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

import java.util.Arrays;

/** Unicode 17.0.0 NFC conformance independent of the host Unicode database. */
final class UnicodeNfc17 {
    private static final int S_BASE = 0xAC00;
    private static final int L_BASE = 0x1100;
    private static final int V_BASE = 0x1161;
    private static final int T_BASE = 0x11A7;
    private static final int L_COUNT = 19;
    private static final int V_COUNT = 21;
    private static final int T_COUNT = 28;
    private static final int N_COUNT = V_COUNT * T_COUNT;
    private static final int S_COUNT = L_COUNT * N_COUNT;

    private UnicodeNfc17() {}

    static boolean isNormalized(String text) {
        if (isAscii(text)) {
            return true;
        }
        int[] original = text.codePoints().toArray();
        return Arrays.equals(original, normalize(original));
    }

    private static boolean isAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x80) {
                return false;
            }
        }
        return true;
    }

    private static int[] normalize(int[] source) {
        IntBuffer decomposed = new IntBuffer(source.length + 4);
        for (int codePoint : source) {
            decompose(codePoint, decomposed);
        }
        canonicalOrder(decomposed);
        return compose(decomposed).toArray();
    }

    private static void decompose(int codePoint, IntBuffer output) {
        int syllableIndex = codePoint - S_BASE;
        if (syllableIndex >= 0 && syllableIndex < S_COUNT) {
            int leading = L_BASE + syllableIndex / N_COUNT;
            int vowel = V_BASE + (syllableIndex % N_COUNT) / T_COUNT;
            int trailing = syllableIndex % T_COUNT;
            output.add(leading);
            output.add(vowel);
            if (trailing != 0) {
                output.add(T_BASE + trailing);
            }
            return;
        }

        int index = UnicodeData17.decompositionIndex(codePoint);
        if (index < 0) {
            output.add(codePoint);
            return;
        }
        for (int i = UnicodeData17.decompositionStart(index);
             i < UnicodeData17.decompositionEnd(index);
             i++) {
            decompose(UnicodeData17.decompositionCodePoint(i), output);
        }
    }

    private static void canonicalOrder(IntBuffer codePoints) {
        for (int i = 1; i < codePoints.size(); i++) {
            int currentClass = UnicodeData17.combiningClass(codePoints.get(i));
            if (currentClass == 0) {
                continue;
            }
            int j = i;
            while (j > 0) {
                int previousClass = UnicodeData17.combiningClass(codePoints.get(j - 1));
                if (previousClass == 0 || previousClass <= currentClass) {
                    break;
                }
                codePoints.swap(j - 1, j);
                j--;
            }
        }
    }

    private static IntBuffer compose(IntBuffer decomposed) {
        if (decomposed.size() == 0) {
            return decomposed;
        }

        IntBuffer result = new IntBuffer(decomposed.size());
        int starter = decomposed.get(0);
        int starterIndex = 0;
        int lastClass = 0;
        result.add(starter);

        for (int i = 1; i < decomposed.size(); i++) {
            int current = decomposed.get(i);
            int currentClass = UnicodeData17.combiningClass(current);
            int composite = composePair(starter, current);

            if (composite >= 0 && (lastClass == 0 || lastClass < currentClass)) {
                result.set(starterIndex, composite);
                starter = composite;
            } else {
                if (currentClass == 0) {
                    starterIndex = result.size();
                    starter = current;
                }
                result.add(current);
                lastClass = currentClass;
            }
        }
        return result;
    }

    private static int composePair(int first, int second) {
        int leadingIndex = first - L_BASE;
        if (leadingIndex >= 0 && leadingIndex < L_COUNT) {
            int vowelIndex = second - V_BASE;
            if (vowelIndex >= 0 && vowelIndex < V_COUNT) {
                return S_BASE + (leadingIndex * V_COUNT + vowelIndex) * T_COUNT;
            }
        }

        int syllableIndex = first - S_BASE;
        if (syllableIndex >= 0 && syllableIndex < S_COUNT && syllableIndex % T_COUNT == 0) {
            int trailingIndex = second - T_BASE;
            if (trailingIndex > 0 && trailingIndex < T_COUNT) {
                return first + trailingIndex;
            }
        }

        return UnicodeData17.compose(first, second);
    }

    private static final class IntBuffer {
        private int[] values;
        private int size;

        IntBuffer(int initialCapacity) {
            values = new int[Math.max(initialCapacity, 8)];
        }

        int size() {
            return size;
        }

        int get(int index) {
            return values[index];
        }

        void set(int index, int value) {
            values[index] = value;
        }

        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        void swap(int first, int second) {
            int value = values[first];
            values[first] = values[second];
            values[second] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
