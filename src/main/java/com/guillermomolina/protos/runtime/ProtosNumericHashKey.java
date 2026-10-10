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

import java.util.Arrays;
import java.util.Objects;

/**
 * Internal exact numeric hash key, not a guest Number or an identity.
 * Small hashes remain signed longs; large hashes use canonical two's
 * complement bytes. No numeric narrowing or collision-based equality.
 */
public final class ProtosNumericHashKey {
    private final long small;
    private final byte[] large;

    private ProtosNumericHashKey(long small, byte[] large) {
        this.small = small;
        this.large = large;
    }

    public static ProtosNumericHashKey ofLong(long value) {
        return new ProtosNumericHashKey(value, null);
    }

    public static ProtosNumericHashKey fromSemanticInteger(
            ProtosIntegerValue value) {
        Objects.requireNonNull(value, "value");
        if (value.isSmallForRuntime()) {
            return ofLong(value.smallValueForRuntime());
        }
        return fromCanonicalTwosComplement(value.value().toByteArray());
    }

    public static ProtosNumericHashKey fromIdentity(Object value) {
        return ofLong(ProtosIdentity.identityHash(value));
    }

    public static ProtosNumericHashKey fromCanonicalTwosComplement(
            byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0) {
            throw new IllegalArgumentException("empty signed integer");
        }
        int offset = 0;
        while (offset + 1 < encoded.length) {
            int first = encoded[offset] & 255;
            int next = encoded[offset + 1] & 255;
            boolean redundantPositive = first == 0 && next < 128;
            boolean redundantNegative = first == 255 && next >= 128;
            if (!redundantPositive && !redundantNegative) {
                break;
            }
            offset++;
        }
        int length = encoded.length - offset;
        if (length <= Long.BYTES) {
            long value = encoded[offset] < 0 ? -1L : 0L;
            for (int i = offset; i < encoded.length; i++) {
                value = (value << 8) | (encoded[i] & 255L);
            }
            return ofLong(value);
        }
        return new ProtosNumericHashKey(
                0L, Arrays.copyOfRange(encoded, offset, encoded.length));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProtosNumericHashKey key)) {
            return false;
        }
        if (large == null || key.large == null) {
            return large == null && key.large == null
                    && small == key.small;
        }
        return Arrays.equals(large, key.large);
    }

    @Override
    public int hashCode() {
        return large == null
                ? Long.hashCode(small)
                : Arrays.hashCode(large);
    }
}
