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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * D151 invocation-local host resolver for Test Tool exact file selection.
 *
 * <p>The installed bootstrap-local closure translates one user-supplied FILE
 * locator into zero or more authorized corpus/source associations. Physical
 * paths terminate at this boundary: the returned values contain only CorpusId
 * and the source path relative to that corpus authority.
 */
public final class ProtosTestToolFileSelectionFacility {
    public static final String BOOTSTRAP_SLOT = "fileSourceResolver";

    private ProtosTestToolFileSelectionFacility() {}

    public record CorpusSourceRoot(String corpusId, Path root) {
        public CorpusSourceRoot {
            Objects.requireNonNull(corpusId, "corpusId");
            Objects.requireNonNull(root, "root");
            if (corpusId.isEmpty()) {
                throw new IllegalArgumentException("corpusId must not be empty");
            }
            root = root.toAbsolutePath().normalize();
        }
    }

    public static void install(
            ProtosActivation activation,
            Path invocationWorkingDirectory,
            List<CorpusSourceRoot> sourceRoots) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(sourceRoots, "sourceRoots");

        Path capturedWorkingDirectory =
                Objects.requireNonNull(
                                invocationWorkingDirectory,
                                "invocationWorkingDirectory")
                        .toAbsolutePath()
                        .normalize();

        ArrayList<CorpusSourceRoot> capturedRoots =
                new ArrayList<>(sourceRoots.size());
        for (CorpusSourceRoot sourceRoot : sourceRoots) {
            capturedRoots.add(
                    Objects.requireNonNull(sourceRoot, "sourceRoot"));
        }
        List<CorpusSourceRoot> immutableRoots =
                List.copyOf(capturedRoots);

        if (activation.context().hasLocalSlot(BOOTSTRAP_SLOT)) {
            throw new IllegalStateException(
                    "file selection bootstrap slot already exists: "
                            + BOOTSTRAP_SLOT);
        }

        activation.context().createLocalSlot(
                BOOTSTRAP_SLOT,
                ProtosClosureValue.nativeClosure(
                        (callActivation, supplied) ->
                                resolve(
                                        callActivation,
                                        supplied,
                                        capturedWorkingDirectory,
                                        immutableRoots)));
    }

    private static Object resolve(
            ProtosActivation activation,
            List<?> supplied,
            Path invocationWorkingDirectory,
            List<CorpusSourceRoot> sourceRoots) {
        if (supplied.size() != 1
                || !(supplied.get(0)
                        instanceof ProtosStringValue selectedFile)) {
            throw toolError(activation);
        }

        final Path selectedPath;
        try {
            Path parsed = Path.of(selectedFile.value());
            selectedPath =
                    (parsed.isAbsolute()
                                    ? parsed
                                    : invocationWorkingDirectory.resolve(parsed))
                            .toAbsolutePath()
                            .normalize();
        } catch (InvalidPathException failure) {
            throw toolError(activation);
        }

        ProtosPrelude prelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "file selection requires caller Core prelude"));

        ArrayList<Object> associations = new ArrayList<>();

        for (CorpusSourceRoot sourceRoot : sourceRoots) {
            Path root = sourceRoot.root();

            if (!selectedPath.startsWith(root)
                    || selectedPath.equals(root)) {
                continue;
            }

            Path relative = root.relativize(selectedPath);
            if (!isExactRegularSource(root, relative, selectedPath)) {
                continue;
            }

            ProtosArrayValue association =
                    prelude.newFrozenArray(
                            List.of(
                                    new ProtosStringValue(
                                            sourceRoot.corpusId()),
                                    new ProtosStringValue(
                                            logicalRelativePath(relative))));
            associations.add(association);
        }

        return prelude.newFrozenArray(associations);
    }

    /**
     * Resolves one already-authorized logical corpus/source association back to
     * its exact physical source locator.
     *
     * <p>This is the inverse host-side operation of file selection. Physical
     * paths remain inside the host boundary; callers supply only the canonical
     * CorpusId and corpus-relative source path.
     */
    public static Path resolveAuthorizedSource(
            List<CorpusSourceRoot> sourceRoots,
            String corpusId,
            String sourcePath)
            throws IOException {
        Objects.requireNonNull(sourceRoots, "sourceRoots");
        Objects.requireNonNull(corpusId, "corpusId");
        Objects.requireNonNull(sourcePath, "sourcePath");

        if (corpusId.isEmpty() || sourcePath.isEmpty()) {
            throw new IOException(
                    "logical source association must not be empty");
        }

        Path resolved = null;

        for (CorpusSourceRoot sourceRoot : sourceRoots) {
            if (!sourceRoot.corpusId().equals(corpusId)) {
                continue;
            }

            if (resolved != null) {
                throw new IOException(
                        "duplicate Test Tool corpus source root: "
                                + corpusId);
            }

            final Path parsed;
            try {
                parsed = Path.of(sourcePath);
            } catch (InvalidPathException failure) {
                throw new IOException(
                        "invalid logical source path",
                        failure);
            }

            if (parsed.isAbsolute()) {
                throw new IOException(
                        "logical source path must be relative");
            }

            Path root = sourceRoot.root();
            Path candidate =
                    root.resolve(parsed)
                            .toAbsolutePath()
                            .normalize();

            if (!candidate.startsWith(root)
                    || candidate.equals(root)) {
                throw new IOException(
                        "logical source path escapes corpus root");
            }

            Path relative =
                    root.relativize(candidate);

            if (!logicalRelativePath(relative)
                    .equals(sourcePath)) {
                throw new IOException(
                        "logical source path is not canonical");
            }

            if (!isExactRegularSource(
                    root,
                    relative,
                    candidate)) {
                throw new IOException(
                        "logical source does not resolve to an exact regular source");
            }

            resolved = candidate;
        }

        if (resolved == null) {
            throw new IOException(
                    "unknown Test Tool corpus source association");
        }

        return resolved;
    }

    private static boolean isExactRegularSource(
            Path root,
            Path relative,
            Path selectedPath) {
        try {
            if (!Files.isRegularFile(
                            selectedPath,
                            LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(selectedPath)) {
                return false;
            }

            /*
             * D151 generation 1 does not accept symlink/reparse aliases.
             *
             * The corpus root itself is host authority and may have been
             * reached through an aliased installation path. What must remain
             * exact is the selected path below that root.
             */
            Path realRoot = root.toRealPath();
            Path expectedReal =
                    realRoot.resolve(relative).normalize();
            Path selectedReal = selectedPath.toRealPath();

            return selectedReal.equals(expectedReal);
        } catch (IOException | SecurityException failure) {
            return false;
        }
    }

    private static String logicalRelativePath(Path relative) {
        StringBuilder result = new StringBuilder();

        for (Path component : relative) {
            if (!result.isEmpty()) {
                result.append('/');
            }
            result.append(component);
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "relative source path must not be empty");
        }

        return result.toString();
    }

    private static ProtosSignalException toolError(
            ProtosActivation activation) {
        return new ProtosSignalException(
                ProtosCoreErrors.newError(activation));
    }
}
