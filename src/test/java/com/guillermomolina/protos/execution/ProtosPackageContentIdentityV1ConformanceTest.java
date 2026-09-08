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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosPackageContentIdentityV1ConformanceTest {
    private static final byte[] MAGIC =
            "protos-package-tree-v1\0".getBytes(StandardCharsets.US_ASCII);

    private static final Set<String> WINDOWS_RESERVED =
            Set.of(
                    "con", "prn", "aux", "nul",
                    "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
                    "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    @Test
    void fixedPositiveStreamsAndDigestsMatchExternalConstants() throws Exception {
        assertVector(
                List.of(file("protos.toml", bytes(""))),
                "70726f746f732d7061636b6167652d747265652d763100"
                        + "010b70726f746f732e746f6d6c00"
                        + "00",
                "beb503347f64442d909b4848e69333a72f4d049d7f5b37c32f453f409641ef04");

        assertVector(
                List.of(
                        file("z.protos", bytes("Z")),
                        file("protos.toml", bytes("[package]\n")),
                        file("a.protos", bytes(""))),
                "70726f746f732d7061636b6167652d747265652d763100"
                        + "0108612e70726f746f7300"
                        + "010b70726f746f732e746f6d6c0a5b7061636b6167655d0a"
                        + "01087a2e70726f746f73015a"
                        + "00",
                "a4e71028b12d648a10729e5dedf947d8bfbff1e6c8e1b95bb04ac170f5682e67");

        assertVector(
                List.of(
                        file("protos.toml", bytes("id = \"pkg\"\n")),
                        file(
                                "assets/data.bin",
                                new byte[] {0, 1, 2, (byte) 0xff, 0x7f, (byte) 0x80}),
                        file("Main.protos", bytes("value: 42\n"))),
                "70726f746f732d7061636b6167652d747265652d763100"
                        + "010b4d61696e2e70726f746f730a76616c75653a2034320a"
                        + "010f6173736574732f646174612e62696e06000102ff7f80"
                        + "010b70726f746f732e746f6d6c0b6964203d2022706b67220a"
                        + "00",
                "2de3f9f78fb1355348861e08cb549919d10e4b50f8965f3fc933eac72fff8870");

        byte[] threeHundredZ = new byte[300];
        Arrays.fill(threeHundredZ, (byte) 'Z');
        assertDigest(
                List.of(
                        file("protos.toml", bytes("")),
                        file("a".repeat(128), threeHundredZ)),
                "4ae0bad7f7915a5e6db1ad4bb29ee71351fcf812b562b41da34f99b8fd4b8f02");
    }

    @Test
    void varuintBoundaryVectorsRemainCanonicalAndNotSixtyFourBitBound() {
        assertEquals("00", hex(varuint(BigInteger.ZERO)));
        assertEquals("01", hex(varuint(BigInteger.ONE)));
        assertEquals("7f", hex(varuint(BigInteger.valueOf(127))));
        assertEquals("8001", hex(varuint(BigInteger.valueOf(128))));
        assertEquals("ff01", hex(varuint(BigInteger.valueOf(255))));
        assertEquals("ac02", hex(varuint(BigInteger.valueOf(300))));
        assertEquals("808001", hex(varuint(BigInteger.valueOf(16384))));
        assertEquals(
                "8080808080808080808001",
                hex(varuint(BigInteger.ONE.shiftLeft(70))));
    }

    @Test
    void framingAndExactCaseKeepDistinctTreesDistinct() throws Exception {
        String framingA =
                digest(List.of(file("protos.toml", bytes("")), file("a", bytes("bc"))));
        String framingB =
                digest(List.of(file("protos.toml", bytes("")), file("ab", bytes("c"))));

        assertEquals(
                "43c4dd19a8fa1aea875f3930a80070d6192a9532bddd466787254c0f710ef60e",
                framingA);
        assertEquals(
                "552f16ad2b83e32bd9ba72037b717384257f5ae5087130fbc790a958c2fdc33f",
                framingB);
        assertNotEquals(framingA, framingB);

        String upper =
                digest(
                        List.of(
                                file("protos.toml", bytes("")),
                                file("Main.protos", bytes("x"))));
        String lower =
                digest(
                        List.of(
                                file("protos.toml", bytes("")),
                                file("main.protos", bytes("x"))));

        assertEquals(
                "9afaaf7e2a250fd11b83d12e63cf7e18527c95820d03bff6895e57a51600e450",
                upper);
        assertEquals(
                "ff22975888d65d4dd9563bd09ca67190c02473bdead0c536faf101cd66f72ec2",
                lower);
        assertNotEquals(upper, lower);
    }

    @Test
    void oneByteManifestMutationChangesIdentity() throws Exception {
        String x = digest(List.of(file("protos.toml", bytes("x"))));
        String y = digest(List.of(file("protos.toml", bytes("y"))));

        assertEquals(
                "802294744fe3b655b2e6aebfaf0392ca3c62b4240b634b2f74eab11816a3df78",
                x);
        assertEquals(
                "c52cac30290c488fe48f8323c1b55229cfeede2ca92bb7da4ada7dd6de586d71",
                y);
        assertNotEquals(x, y);
    }

    @Test
    void invalidPathCollisionAndEntryKindVectorsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> logicalTree(List.of(file("Main.protos", bytes("x")))));

        for (String invalid :
                List.of(
                        "a//b",
                        "a/../b",
                        "name.",
                        "bad name",
                        "café.protos",
                        "a\\b",
                        "/leading",
                        "trailing/",
                        "CON.txt")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            logicalTree(
                                    List.of(
                                            file("protos.toml", bytes("")),
                                            file(invalid, bytes("x")))),
                    invalid);
        }

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        logicalTree(
                                List.of(
                                        file("protos.toml", bytes("")),
                                        file("Parser.protos", bytes("x")),
                                        file("parser.protos", bytes("y")))));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        logicalTree(
                                List.of(
                                        file("protos.toml", bytes("")),
                                        file("Data/a", bytes("x")),
                                        file("data/b", bytes("y")))));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        logicalTree(
                                List.of(
                                        file("protos.toml", bytes("")),
                                        entry(
                                                "link",
                                                EntryKind.SYMLINK,
                                                bytes("../outside"),
                                                "mode-a",
                                                "/store/a"))));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        logicalTree(
                                List.of(
                                        file("protos.toml", bytes("")),
                                        entry(
                                                "pipe",
                                                EntryKind.SPECIAL,
                                                bytes(""),
                                                "fifo",
                                                "/store/a"))));
    }

    @Test
    void emptyDirectoriesMetadataAndStorageLayoutAreNonSemantic() throws Exception {
        List<ObservedEntry> first =
                List.of(
                        entry(
                                "protos.toml",
                                EntryKind.FILE,
                                bytes("id = \"x\"\n"),
                                "0644@t1",
                                "/store/A"),
                        entry(
                                "src/Main.protos",
                                EntryKind.FILE,
                                bytes("42\n"),
                                "0755@t2",
                                "/store/A"));

        List<ObservedEntry> second =
                List.of(
                        entry(
                                "empty",
                                EntryKind.DIRECTORY,
                                bytes(""),
                                "0700@later",
                                "/elsewhere/B"),
                        entry(
                                "src",
                                EntryKind.DIRECTORY,
                                bytes(""),
                                "0777@later",
                                "/elsewhere/B"),
                        entry(
                                "src/Main.protos",
                                EntryKind.FILE,
                                bytes("42\n"),
                                "0600@later",
                                "/elsewhere/B"),
                        entry(
                                "protos.toml",
                                EntryKind.FILE,
                                bytes("id = \"x\"\n"),
                                "0444@later",
                                "/elsewhere/B"));

        assertEquals(digest(first), digest(second));
        assertArrayEquals(
                canonicalStream(logicalTree(first)),
                canonicalStream(logicalTree(second)));
    }

    private static void assertVector(
            List<ObservedEntry> entries, String expectedStreamHex, String expectedDigest)
            throws Exception {
        byte[] stream = canonicalStream(logicalTree(entries));
        assertArrayEquals(HexFormat.of().parseHex(expectedStreamHex), stream);
        assertEquals(expectedDigest, sha256Hex(stream));
    }

    private static void assertDigest(List<ObservedEntry> entries, String expectedDigest)
            throws Exception {
        assertEquals(expectedDigest, digest(entries));
    }

    private static String digest(List<ObservedEntry> entries) throws Exception {
        return sha256Hex(canonicalStream(logicalTree(entries)));
    }

    private static Map<String, byte[]> logicalTree(List<ObservedEntry> entries) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, Map<String, String>> childSpellings = new HashMap<>();

        for (ObservedEntry entry : entries) {
            validatePath(entry.path(), childSpellings);
            switch (entry.kind()) {
                case FILE -> {
                    if (files.putIfAbsent(entry.path(), entry.bytes()) != null) {
                        throw new IllegalArgumentException(
                                "duplicate file path: " + entry.path());
                    }
                }
                case DIRECTORY -> {
                    // Structural only under E1A.
                }
                case SYMLINK, SPECIAL ->
                        throw new IllegalArgumentException(
                                "invalid protos-package-tree-v1 entry kind: " + entry.kind());
            }
        }

        if (!files.containsKey("protos.toml")) {
            throw new IllegalArgumentException("root protos.toml regular file is required");
        }
        return files;
    }

    private static void validatePath(
            String path, Map<String, Map<String, String>> childSpellings) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("empty package path");
        }

        String[] segments = path.split("/", -1);
        String parent = "";
        for (String segment : segments) {
            validateSegment(segment);

            String folded = asciiLower(segment);
            Map<String, String> siblings =
                    childSpellings.computeIfAbsent(parent, ignored -> new HashMap<>());
            String prior = siblings.putIfAbsent(folded, segment);
            if (prior != null && !prior.equals(segment)) {
                throw new IllegalArgumentException(
                        "ASCII-case-fold collision: " + prior + " / " + segment);
            }

            parent = parent.isEmpty() ? segment : parent + "/" + segment;
        }
    }

    private static void validateSegment(String segment) {
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
            throw new IllegalArgumentException("invalid path segment: " + segment);
        }
        if (segment.endsWith(".")) {
            throw new IllegalArgumentException("path segment ends in dot: " + segment);
        }

        for (int i = 0; i < segment.length(); i++) {
            char ch = segment.charAt(i);
            boolean allowed =
                    (ch >= 'A' && ch <= 'Z')
                            || (ch >= 'a' && ch <= 'z')
                            || (ch >= '0' && ch <= '9')
                            || ch == '.'
                            || ch == '_'
                            || ch == '-';
            if (!allowed) {
                throw new IllegalArgumentException("invalid v1 path character");
            }
        }

        int dot = segment.indexOf('.');
        String basename = dot >= 0 ? segment.substring(0, dot) : segment;
        if (WINDOWS_RESERVED.contains(asciiLower(basename))) {
            throw new IllegalArgumentException(
                    "Windows-reserved path segment: " + segment);
        }
    }

    private static String asciiLower(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            out.append(ch >= 'A' && ch <= 'Z' ? (char) (ch + ('a' - 'A')) : ch);
        }
        return out.toString();
    }

    private static byte[] canonicalStream(Map<String, byte[]> files) {
        List<Map.Entry<String, byte[]>> ordered = new ArrayList<>(files.entrySet());
        ordered.sort(
                (left, right) ->
                        compareUnsignedAscii(left.getKey(), right.getKey()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(MAGIC);

        for (Map.Entry<String, byte[]> entry : ordered) {
            byte[] path = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            byte[] content = entry.getValue();

            out.write(0x01);
            out.writeBytes(varuint(BigInteger.valueOf(path.length)));
            out.writeBytes(path);
            out.writeBytes(varuint(BigInteger.valueOf(content.length)));
            out.writeBytes(content);
        }

        out.write(0x00);
        return out.toByteArray();
    }

    private static int compareUnsignedAscii(String left, String right) {
        int common = Math.min(left.length(), right.length());
        for (int i = 0; i < common; i++) {
            int a = left.charAt(i);
            int b = right.charAt(i);
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return Integer.compare(left.length(), right.length());
    }

    private static byte[] varuint(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("negative varuint");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BigInteger remaining = value;
        do {
            int low = remaining.and(BigInteger.valueOf(0x7f)).intValue();
            remaining = remaining.shiftRight(7);
            out.write(remaining.signum() == 0 ? low : low | 0x80);
        } while (remaining.signum() != 0);
        return out.toByteArray();
    }

    private static String sha256Hex(byte[] stream) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(stream));
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static ObservedEntry file(String path, byte[] bytes) {
        return entry(path, EntryKind.FILE, bytes, "metadata-a", "/store/a");
    }

    private static ObservedEntry entry(
            String path,
            EntryKind kind,
            byte[] bytes,
            String ignoredMetadata,
            String ignoredStorageLocator) {
        return new ObservedEntry(
                path, kind, bytes, ignoredMetadata, ignoredStorageLocator);
    }

    private enum EntryKind {
        FILE,
        DIRECTORY,
        SYMLINK,
        SPECIAL
    }

    private record ObservedEntry(
            String path,
            EntryKind kind,
            byte[] bytes,
            String ignoredMetadata,
            String ignoredStorageLocator) {}
}
