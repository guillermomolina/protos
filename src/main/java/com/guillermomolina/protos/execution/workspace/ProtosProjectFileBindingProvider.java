/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS.
 * "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY OF THE
 * LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE
 * CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS FILE, A
 * COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */

package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.analysis.ProtosProjectBinding;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProjection;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProvider;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** D089 generation-1 {@code protos.project} provider for exact local candidate roots. */
public final class ProtosProjectFileBindingProvider implements ProtosProjectBindingProvider {
    private static final String PROJECT_FILE = "protos.project";
    private static final String ROOT_MANIFEST = "protos.toml";
    private static final String LOCK_FILE = "protos.lock";
    private static final String RESOLUTION_METHOD = "protos-resolution-input-v1";
    private static final String METADATA_METHOD = "protos-project-metadata-v1";
    private static final byte[] METADATA_PREFIX =
            "protos-project-metadata-v1\n".getBytes(StandardCharsets.US_ASCII);

    @Override
    public Optional<ProtosProjectBinding> acquire(Path candidateRoot) {
        Objects.requireNonNull(candidateRoot, "candidateRoot");
        try {
            return Optional.of(acquireValidated(candidateRoot));
        } catch (IOException | IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }

    private static ProtosProjectBinding acquireValidated(Path candidateRoot) throws IOException {
        Path selected = candidateRoot.toAbsolutePath().normalize();
        Path realRoot = selected.toRealPath();
        if (!Files.isDirectory(realRoot)) {
            throw new IOException("ProjectBinding candidate root is not a directory");
        }

        StableFile projectFile = stableExactFile(realRoot, PROJECT_FILE);
        ProjectDocument document = ProjectDocument.parse(projectFile.bytes());

        ArrayList<StableFile> metadataFiles = new ArrayList<>();
        MessageDigest metadataDigest = sha256();
        metadataDigest.update(METADATA_PREFIX);

        StableFile rootManifest = stableExactFile(realRoot, ROOT_MANIFEST);
        metadataFiles.add(rootManifest);
        appendMetadataRecord(metadataDigest, ROOT_MANIFEST, rootManifest.bytes());

        ArrayList<ProtosProjectBindingProjection.PackageRef> packageRefs = new ArrayList<>();
        packageRefs.add(new ProtosProjectBindingProjection.PackageRef(document.rootPackageId(), ""));

        for (ProjectDocument.Member member : document.members()) {
            Path memberRoot =
                    ProtosWorkspaceMemberLocationTraversal.requireConfinedMemberDirectory(
                            realRoot, member.location());
            StableFile memberManifest = stableExactFile(memberRoot, ROOT_MANIFEST);
            metadataFiles.add(memberManifest);
            appendMetadataRecord(
                    metadataDigest,
                    member.location() + "/" + ROOT_MANIFEST,
                    memberManifest.bytes());
            packageRefs.add(
                    new ProtosProjectBindingProjection.PackageRef(
                            member.packageId(), member.location()));
        }

        StableFile lockFile = stableExactFile(realRoot, LOCK_FILE);
        metadataFiles.add(lockFile);
        appendMetadataRecord(metadataDigest, LOCK_FILE, lockFile.bytes());

        String currentMetadata = HexFormat.of().formatHex(metadataDigest.digest());
        if (!currentMetadata.equals(document.metadataHex())) {
            throw new IOException("ProjectBinding project metadata witness is stale");
        }

        String freshnessWitness =
                "resolution-input " + RESOLUTION_METHOD + " sha256:" + document.resolutionHex()
                        + "\nmetadata-content " + METADATA_METHOD + " sha256:"
                        + document.metadataHex();

        ProtosProjectBindingProjection projection =
                new ProtosProjectBindingProjection(
                        ProtosProjectBindingProjection.CURRENT_GENERATION,
                        realRoot,
                        document.rootPackageId(),
                        packageRefs,
                        freshnessWitness);

        ProtosPackageExecutionPlan physicalBindingPlan = physicalBindingPlan(projection);
        ProtosWorkspacePackageProjectIndex projectIndex =
                ProtosWorkspacePackageProjectIndex.bind(realRoot, physicalBindingPlan);
        ProtosWorkspacePackageDirectoryIndex directoryIndex =
                ProtosWorkspacePackageDirectoryIndex.bind(projectIndex);

        ArrayList<ProtosProjectBinding.PackageRoot> roots = new ArrayList<>();
        for (ProtosProjectBindingProjection.PackageRef packageRef : projection.packages()) {
            Path directory = directoryIndex.requireLocation(packageRef.location()).directory();
            roots.add(
                    new ProtosProjectBinding.PackageRoot(
                            packageRef.packageId(), packageRef.location(), directory));
        }

        List<ProtosWorkspacePackageSourceInventory.Source> inventory =
                ProtosWorkspacePackageSourceInventory.bind(directoryIndex).snapshot();
        ArrayList<ProtosProjectBinding.Source> sources = new ArrayList<>();
        for (ProtosWorkspacePackageSourceInventory.Source source : inventory) {
            sources.add(
                    new ProtosProjectBinding.Source(
                            source.packageId(), source.logicalModule(), source.source()));
        }

        // The authority files must still contain the exact bytes admitted by this attempt after
        // bounded source enumeration. Ordinary .protos source churn is intentionally not checked.
        projectFile.revalidate();
        for (StableFile metadataFile : metadataFiles) {
            metadataFile.revalidate();
        }

        return new ProtosProjectBinding(projection, roots, sources);
    }

    /**
     * Internal adapter only: the existing confinement/source-inventory machinery consumes the
     * workspace package subset of a detached execution-plan carrier. D089 projection identity is
     * the authority here; exports/dependencies are deliberately absent and are never reconstructed.
     */
    private static ProtosPackageExecutionPlan physicalBindingPlan(
            ProtosProjectBindingProjection projection) {
        ProtosPackageExecutionPlan.WorkspaceRef root =
                new ProtosPackageExecutionPlan.WorkspaceRef(projection.rootPackageId());
        ArrayList<ProtosPackageExecutionPlan.PackageNode> packages = new ArrayList<>();
        for (ProtosProjectBindingProjection.PackageRef packageRef : projection.packages()) {
            packages.add(
                    new ProtosPackageExecutionPlan.PackageNode(
                            new ProtosPackageExecutionPlan.WorkspaceRef(packageRef.packageId()),
                            packageRef.location(),
                            Map.of()));
        }
        return new ProtosPackageExecutionPlan(1, root, packages, List.of());
    }

    private static StableFile stableExactFile(Path directory, String expectedName)
            throws IOException {
        Path exact = requireExactRegularFile(directory, expectedName);
        BasicFileAttributes before =
                Files.readAttributes(
                        exact,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) {
            throw new IOException("ProjectBinding authority input is not a regular file");
        }
        byte[] bytes = Files.readAllBytes(exact);
        BasicFileAttributes after =
                Files.readAttributes(
                        exact,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile()
                || before.size() != after.size()
                || after.size() != bytes.length
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("ProjectBinding authority input changed while reading");
        }
        return new StableFile(exact, bytes);
    }

    private static Path requireExactRegularFile(Path directory, String expectedName)
            throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("ProjectBinding authority directory is unavailable");
        }
        Path exact = null;
        int caseFoldMatches = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                Path fileName = entry.getFileName();
                if (fileName == null) {
                    continue;
                }
                String actualName = fileName.toString();
                if (equalsAsciiIgnoreCase(actualName, expectedName)) {
                    caseFoldMatches++;
                    if (actualName.equals(expectedName)) {
                        exact = entry;
                    }
                }
            }
        }
        if (caseFoldMatches > 1 || exact == null) {
            throw new IOException("ProjectBinding authority file spelling is absent or ambiguous");
        }
        BasicFileAttributes attributes =
                Files.readAttributes(
                        exact,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("ProjectBinding authority input is not a regular file");
        }
        return exact;
    }

    private static void appendMetadataRecord(
            MessageDigest digest, String relativePath, byte[] content) {
        byte[] pathBytes = relativePath.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(pathBytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(pathBytes);
        digest.update((byte) '\n');
        digest.update(Integer.toString(content.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(content);
        digest.update((byte) '\n');
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static boolean equalsAsciiIgnoreCase(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        for (int index = 0; index < left.length(); index++) {
            if (asciiLower(left.charAt(index)) != asciiLower(right.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static char asciiLower(char value) {
        return value >= 'A' && value <= 'Z'
                ? (char) (value + ('a' - 'A'))
                : value;
    }

    private record StableFile(Path path, byte[] bytes) {
        StableFile {
            path = Objects.requireNonNull(path, "path");
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        void revalidate() throws IOException {
            BasicFileAttributes attributes =
                    Files.readAttributes(
                            path,
                            BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || !Arrays.equals(bytes, Files.readAllBytes(path))) {
                throw new IOException("ProjectBinding authority input changed during validation");
            }
        }
    }

    private record ProjectDocument(
            String resolutionHex,
            String metadataHex,
            String rootPackageId,
            List<Member> members) {
        private static final Comparator<Member> MEMBER_ORDER =
                (left, right) -> {
                    int location = compareUnsignedUtf8(
                            renderQstring(left.location()), renderQstring(right.location()));
                    return location != 0
                            ? location
                            : compareUnsignedUtf8(
                                    renderQstring(left.packageId()),
                                    renderQstring(right.packageId()));
                };

        ProjectDocument {
            resolutionHex = requireDigest(resolutionHex);
            metadataHex = requireDigest(metadataHex);
            if (rootPackageId == null || rootPackageId.isEmpty()) {
                throw new IllegalArgumentException("root PackageId must not be empty");
            }
            members = List.copyOf(Objects.requireNonNull(members, "members"));
        }

        static ProjectDocument parse(byte[] bytes) throws IOException {
            String text = decodeCanonicalUtf8(bytes);
            if (text.indexOf('\r') >= 0 || !text.endsWith("\n")) {
                throw new IOException("noncanonical protos.project line endings");
            }
            String[] lines = text.split("\n", -1);
            if (lines.length < 6 || !lines[lines.length - 1].isEmpty()) {
                throw new IOException("incomplete protos.project");
            }
            if (!lines[0].equals("project-format 1")) {
                throw new IOException("unsupported protos.project generation");
            }
            String resolution =
                    parseDigestLine(
                            lines[1], "resolution-input ", RESOLUTION_METHOD);
            String metadata =
                    parseDigestLine(
                            lines[2], "metadata-content ", METADATA_METHOD);
            if (!lines[3].isEmpty()) {
                throw new IOException("protos.project header separator is not canonical");
            }

            Cursor root = new Cursor(lines[4]);
            root.expect("root workspace ");
            String rootPackage = requireNonEmpty(root.qstring(), "root PackageId");
            root.requireEnd();

            ArrayList<Member> members = new ArrayList<>();
            Set<String> packageIds = new HashSet<>();
            Set<String> locations = new HashSet<>();
            packageIds.add(rootPackage);
            Member previous = null;
            for (int index = 5; index < lines.length - 1; index++) {
                if (lines[index].isEmpty()) {
                    throw new IOException("unexpected blank protos.project body record");
                }
                Cursor cursor = new Cursor(lines[index]);
                cursor.expect("workspace-member ");
                String location = requireNonEmpty(cursor.qstring(), "workspace member location");
                cursor.expect(" workspace ");
                String packageId = requireNonEmpty(cursor.qstring(), "workspace member PackageId");
                cursor.requireEnd();
                Member member = new Member(location, packageId);
                if (!locations.add(location) || !packageIds.add(packageId)) {
                    throw new IOException("duplicate protos.project workspace identity");
                }
                if (previous != null && MEMBER_ORDER.compare(previous, member) >= 0) {
                    throw new IOException("noncanonical protos.project workspace-member order");
                }
                members.add(member);
                previous = member;
            }

            ProjectDocument document =
                    new ProjectDocument(resolution, metadata, rootPackage, members);
            if (!document.render().equals(text)) {
                throw new IOException("protos.project is not canonical generation-1 text");
            }
            return document;
        }

        private String render() {
            StringBuilder result = new StringBuilder();
            result.append("project-format 1\n");
            result.append("resolution-input ")
                    .append(RESOLUTION_METHOD)
                    .append(" sha256:")
                    .append(resolutionHex)
                    .append('\n');
            result.append("metadata-content ")
                    .append(METADATA_METHOD)
                    .append(" sha256:")
                    .append(metadataHex)
                    .append("\n\n");
            result.append("root workspace ")
                    .append(renderQstring(rootPackageId))
                    .append('\n');
            for (Member member : members) {
                result.append("workspace-member ")
                        .append(renderQstring(member.location()))
                        .append(" workspace ")
                        .append(renderQstring(member.packageId()))
                        .append('\n');
            }
            return result.toString();
        }

        private static String parseDigestLine(
                String line, String prefix, String expectedMethod) throws IOException {
            String start = prefix + expectedMethod + " sha256:";
            if (!line.startsWith(start)) {
                throw new IOException("unsupported protos.project digest method");
            }
            String hex = line.substring(start.length());
            return requireDigest(hex);
        }

        private static String requireDigest(String hex) {
            Objects.requireNonNull(hex, "hex");
            if (hex.length() != 64) {
                throw new IllegalArgumentException("ProjectBinding digest must be 64 lowercase hex digits");
            }
            for (int index = 0; index < hex.length(); index++) {
                char value = hex.charAt(index);
                boolean digit = value >= '0' && value <= '9';
                boolean lowerHex = value >= 'a' && value <= 'f';
                if (!digit && !lowerHex) {
                    throw new IllegalArgumentException("ProjectBinding digest must be lowercase hex");
                }
            }
            return hex;
        }

        private static String requireNonEmpty(String value, String label) throws IOException {
            if (value.isEmpty()) {
                throw new IOException(label + " must not be empty");
            }
            return value;
        }

        private static String decodeCanonicalUtf8(byte[] bytes) throws IOException {
            if (bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xef
                    && (bytes[1] & 0xff) == 0xbb
                    && (bytes[2] & 0xff) == 0xbf) {
                throw new IOException("protos.project UTF-8 BOM is forbidden");
            }
            try {
                return StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException invalidUtf8) {
                throw new IOException("protos.project is not canonical UTF-8", invalidUtf8);
            }
        }

        private static String renderQstring(String value) {
            StringBuilder result = new StringBuilder("\"");
            value.codePoints()
                    .forEach(
                            codePoint -> {
                                if (codePoint == '"') {
                                    result.append("\\\"");
                                } else if (codePoint == '\\') {
                                    result.append("\\\\");
                                } else if (codePoint <= 0x1f || codePoint == 0x7f) {
                                    result.append(String.format("\\u%04x", codePoint));
                                } else {
                                    result.appendCodePoint(codePoint);
                                }
                            });
            return result.append('"').toString();
        }

        private static int compareUnsignedUtf8(String left, String right) {
            byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
            byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(leftBytes.length, rightBytes.length);
            for (int index = 0; index < length; index++) {
                int compared = Integer.compare(leftBytes[index] & 0xff, rightBytes[index] & 0xff);
                if (compared != 0) {
                    return compared;
                }
            }
            return Integer.compare(leftBytes.length, rightBytes.length);
        }

        record Member(String location, String packageId) {
            Member {
                Objects.requireNonNull(location, "location");
                Objects.requireNonNull(packageId, "packageId");
            }
        }

        private static final class Cursor {
            private final String text;
            private int offset;

            Cursor(String text) {
                this.text = Objects.requireNonNull(text, "text");
            }

            void expect(String expected) throws IOException {
                if (!text.startsWith(expected, offset)) {
                    throw new IOException("noncanonical protos.project record syntax");
                }
                offset += expected.length();
            }

            String qstring() throws IOException {
                if (offset >= text.length() || text.charAt(offset) != '"') {
                    throw new IOException("expected canonical protos.project qstring");
                }
                offset++;
                StringBuilder value = new StringBuilder();
                while (offset < text.length()) {
                    int codePoint = text.codePointAt(offset);
                    if (codePoint == '"') {
                        offset++;
                        return value.toString();
                    }
                    if (codePoint == '\\') {
                        offset++;
                        if (offset >= text.length()) {
                            throw new IOException("truncated protos.project qstring escape");
                        }
                        char escaped = text.charAt(offset++);
                        if (escaped == '"' || escaped == '\\') {
                            value.append(escaped);
                            continue;
                        }
                        if (escaped != 'u' || offset + 4 > text.length()) {
                            throw new IOException("noncanonical protos.project qstring escape");
                        }
                        int escapedCodePoint = 0;
                        for (int digit = 0; digit < 4; digit++) {
                            char hex = text.charAt(offset++);
                            int nibble = lowerHexNibble(hex);
                            if (nibble < 0) {
                                throw new IOException("noncanonical protos.project qstring hex");
                            }
                            escapedCodePoint = (escapedCodePoint << 4) | nibble;
                        }
                        if (!((escapedCodePoint <= 0x1f) || escapedCodePoint == 0x7f)) {
                            throw new IOException("unnecessary protos.project unicode escape");
                        }
                        value.appendCodePoint(escapedCodePoint);
                        continue;
                    }
                    if (codePoint <= 0x1f || codePoint == 0x7f) {
                        throw new IOException("literal control in protos.project qstring");
                    }
                    value.appendCodePoint(codePoint);
                    offset += Character.charCount(codePoint);
                }
                throw new IOException("unterminated protos.project qstring");
            }

            void requireEnd() throws IOException {
                if (offset != text.length()) {
                    throw new IOException("trailing protos.project record material");
                }
            }

            private static int lowerHexNibble(char value) {
                if (value >= '0' && value <= '9') {
                    return value - '0';
                }
                if (value >= 'a' && value <= 'f') {
                    return value - 'a' + 10;
                }
                return -1;
            }
        }
    }
}
