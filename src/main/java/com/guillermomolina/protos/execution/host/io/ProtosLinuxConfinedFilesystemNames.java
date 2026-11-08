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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * I156 directory-entry names of the confined Filesystem grant: strict, reversible UTF-8 with no
 * normalization, so a Protos Path component and its native name always denote the same entry.
 */
final class ProtosLinuxConfinedFilesystemNames {
    /** Linux NAME_MAX: the longest single directory-entry name in bytes. */
    private static final int NAME_MAX = 255;

    private ProtosLinuxConfinedFilesystemNames() {}

    /**
     * Whether {@code name} is exactly one Linux directory-entry name that round-trips through
     * strict UTF-8: non-empty, not {@code .} or {@code ..}, without {@code /} or NUL, without
     * unpaired surrogates and at most NAME_MAX bytes.
     */
    static boolean isEntryName(String name) {
        if (name == null
                || name.isEmpty()
                || name.equals(".")
                || name.equals("..")
                || name.indexOf('/') >= 0
                || name.indexOf('\0') >= 0) {
            return false;
        }
        try {
            return strictUtf8(name).length <= NAME_MAX;
        } catch (CharacterCodingException unrepresentable) {
            return false;
        }
    }

    /** Joins already validated entry names into one relative path; empty selects {@code "."}. */
    static String relativePath(List<String> components) {
        return components.isEmpty() ? "." : String.join("/", components);
    }

    static byte[] strictUtf8(String text) throws CharacterCodingException {
        ByteBuffer encoded =
                StandardCharsets.UTF_8
                        .newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(CharBuffer.wrap(text));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
    }

    static String strictUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    /** A NUL-terminated strict UTF-8 copy whose lifetime is {@code arena}. */
    static MemorySegment cString(Arena arena, String text) throws ProtosLinuxConfinedFilesystemNative.NativeException {
        byte[] bytes;
        try {
            bytes = strictUtf8(text);
        } catch (CharacterCodingException unrepresentable) {
            throw new ProtosLinuxConfinedFilesystemNative.NativeException("path is not representable as strict UTF-8", ProtosLinuxConfinedFilesystemNative.EILSEQ);
        }
        for (byte octet : bytes) {
            if (octet == 0) {
                throw new ProtosLinuxConfinedFilesystemNative.NativeException("path contains NUL", ProtosLinuxConfinedFilesystemNative.EINVAL);
            }
        }
        MemorySegment segment = arena.allocate(bytes.length + 1L, 1);
        MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.length);
        segment.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
        return segment;
    }
}
