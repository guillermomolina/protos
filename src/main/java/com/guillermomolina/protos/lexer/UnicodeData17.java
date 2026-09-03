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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Compact, generated Unicode 17.0.0 data for lexical identifier semantics. */
final class UnicodeData17 {
    private static final String RESOURCE =
        "/com/guillermomolina/protos/lexer/unicode17.bin";
    private static final int MAGIC = 0x50543137;
    private static final Data DATA = load();

    private UnicodeData17() {}

    static boolean isXidStart(int codePoint) {
        return inRanges(DATA.xidStartRanges, codePoint, 2);
    }

    static boolean isXidContinue(int codePoint) {
        return inRanges(DATA.xidContinueRanges, codePoint, 2);
    }

    static int combiningClass(int codePoint) {
        int[] ranges = DATA.combiningClassRanges;
        int low = 0;
        int high = ranges.length / 3 - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int offset = mid * 3;
            int start = ranges[offset];
            int end = ranges[offset + 1];
            if (codePoint < start) {
                high = mid - 1;
            } else if (codePoint > end) {
                low = mid + 1;
            } else {
                return ranges[offset + 2];
            }
        }
        return 0;
    }

    static int decompositionIndex(int codePoint) {
        return Arrays.binarySearch(DATA.decompositionCodePoints, codePoint);
    }

    static int decompositionStart(int index) {
        return DATA.decompositionOffsets[index];
    }

    static int decompositionEnd(int index) {
        return DATA.decompositionOffsets[index + 1];
    }

    static int decompositionCodePoint(int index) {
        return DATA.decompositionData[index];
    }

    static int compose(int first, int second) {
        long key = ((long) first << 21) | second;
        int index = Arrays.binarySearch(DATA.compositionKeys, key);
        return index < 0 ? -1 : DATA.compositionValues[index];
    }

    private static boolean inRanges(int[] ranges, int codePoint, int width) {
        int low = 0;
        int high = ranges.length / width - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int offset = mid * width;
            int start = ranges[offset];
            int end = ranges[offset + 1];
            if (codePoint < start) {
                high = mid - 1;
            } else if (codePoint > end) {
                low = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    private static Data load() {
        try (InputStream raw = UnicodeData17.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("Missing Unicode 17 data resource " + RESOURCE);
            }
            try (DataInputStream input = new DataInputStream(raw)) {
                if (input.readInt() != MAGIC
                    || input.readInt() != 17
                    || input.readInt() != 0
                    || input.readInt() != 0) {
                    throw new IllegalStateException("Unexpected Unicode data resource version");
                }

                int[] xidStartRanges = readArray(input, 2);
                int[] xidContinueRanges = readArray(input, 2);
                int[] combiningClassRanges = readArray(input, 3);

                int decompositionCount = input.readInt();
                int decompositionDataLength = input.readInt();
                int[] decompositionCodePoints = new int[decompositionCount];
                int[] decompositionOffsets = new int[decompositionCount + 1];
                int[] decompositionData = new int[decompositionDataLength];
                int dataOffset = 0;
                for (int i = 0; i < decompositionCount; i++) {
                    decompositionCodePoints[i] = input.readInt();
                    int length = input.readInt();
                    decompositionOffsets[i] = dataOffset;
                    for (int j = 0; j < length; j++) {
                        decompositionData[dataOffset++] = input.readInt();
                    }
                }
                decompositionOffsets[decompositionCount] = dataOffset;
                if (dataOffset != decompositionDataLength) {
                    throw new IllegalStateException("Corrupt Unicode decomposition data");
                }

                int compositionCount = input.readInt();
                long[] compositionKeys = new long[compositionCount];
                int[] compositionValues = new int[compositionCount];
                for (int i = 0; i < compositionCount; i++) {
                    int first = input.readInt();
                    int second = input.readInt();
                    compositionKeys[i] = ((long) first << 21) | second;
                    compositionValues[i] = input.readInt();
                }

                return new Data(
                    xidStartRanges,
                    xidContinueRanges,
                    combiningClassRanges,
                    decompositionCodePoints,
                    decompositionOffsets,
                    decompositionData,
                    compositionKeys,
                    compositionValues
                );
            }
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static int[] readArray(DataInputStream input, int width) throws IOException {
        int count = input.readInt();
        int[] values = new int[Math.multiplyExact(count, width)];
        for (int i = 0; i < values.length; i++) {
            values[i] = input.readInt();
        }
        return values;
    }

    private record Data(
        int[] xidStartRanges,
        int[] xidContinueRanges,
        int[] combiningClassRanges,
        int[] decompositionCodePoints,
        int[] decompositionOffsets,
        int[] decompositionData,
        long[] compositionKeys,
        int[] compositionValues
    ) {}
}
