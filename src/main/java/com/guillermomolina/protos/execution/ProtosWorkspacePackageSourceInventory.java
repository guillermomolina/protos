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

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Canonical current-source inventory over workspace package roots that have already been bound by
 * the package authority.
 *
 * <p>This class does not discover projects or packages. It enumerates only roots present in the
 * supplied directory index and admits a source only after an exact round trip through the existing
 * logical-module to source lookup.
 */
final class ProtosWorkspacePackageSourceInventory {
    record Source(String packageId, String logicalModule, Path source) {
        Source {
            Objects.requireNonNull(packageId, "packageId");
            Objects.requireNonNull(logicalModule, "logicalModule");
            Objects.requireNonNull(source, "source");
        }
    }

    private static final String SOURCE_SUFFIX = ".protos";

    private final ProtosWorkspacePackageDirectoryIndex directoryIndex;
    private final ProtosWorkspacePackageSourceLookup sourceLookup;

    private ProtosWorkspacePackageSourceInventory(
            ProtosWorkspacePackageDirectoryIndex directoryIndex) {
        this.directoryIndex = Objects.requireNonNull(directoryIndex, "directoryIndex");
        this.sourceLookup = ProtosWorkspacePackageSourceLookup.bind(directoryIndex);
    }

    static ProtosWorkspacePackageSourceInventory bind(
            ProtosWorkspacePackageDirectoryIndex directoryIndex) {
        return new ProtosWorkspacePackageSourceInventory(directoryIndex);
    }

    ProtosWorkspacePackageDirectoryIndex directoryIndex() {
        return directoryIndex;
    }

    List<Source> snapshot() throws IOException {
        ArrayList<Source> sources = new ArrayList<>();
        for (ProtosPackageExecutionPlan.PackageNode packageNode
                : directoryIndex.projectIndex().plan().packages()) {
            inventoryPackage(packageNode.ref().packageId(), sources);
        }
        sources.sort(
                Comparator.comparing(Source::packageId)
                        .thenComparing(Source::logicalModule));
        return List.copyOf(sources);
    }

    private void inventoryPackage(String packageId, List<Source> sources) throws IOException {
        ProtosWorkspacePackageDirectoryIndex.PackageDirectory packageDirectory =
                directoryIndex.requirePackage(packageId);
        Path packageRoot = packageDirectory.directory().toRealPath();
        if (!Files.isDirectory(packageRoot)) {
            throw new IOException("workspace package source root is not a directory");
        }

        Files.walkFileTree(
                packageRoot,
                EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, BasicFileAttributes attributes) throws IOException {
                        Path realDirectory = directory.toRealPath();
                        if (!Files.isDirectory(realDirectory)
                                || !realDirectory.startsWith(packageRoot)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        if (!directoryIndex.isOwnedCanonicalPath(packageId, realDirectory)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        if (!attributes.isRegularFile()) {
                            return FileVisitResult.CONTINUE;
                        }

                        Path realSource = file.toRealPath();
                        if (!realSource.startsWith(packageRoot)) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (!directoryIndex.isOwnedCanonicalPath(packageId, realSource)) {
                            return FileVisitResult.CONTINUE;
                        }

                        String logicalModule = logicalModuleFor(packageRoot, file);
                        if (logicalModule == null) {
                            return FileVisitResult.CONTINUE;
                        }

                        Path canonicalSource =
                                sourceLookup.requireSource(packageId, logicalModule);
                        if (!canonicalSource.equals(realSource)) {
                            throw new IOException(
                                    "workspace package source inventory round-trip mismatch");
                        }
                        sources.add(new Source(packageId, logicalModule, canonicalSource));
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private static String logicalModuleFor(Path packageRoot, Path source) {
        Path relative = packageRoot.relativize(source.toAbsolutePath().normalize());
        if (relative.getNameCount() == 0) {
            return null;
        }

        StringBuilder logicalModule = new StringBuilder();
        for (int index = 0; index < relative.getNameCount(); index++) {
            String segment = relative.getName(index).toString();
            if (index == relative.getNameCount() - 1) {
                if (!segment.endsWith(SOURCE_SUFFIX)
                        || segment.length() == SOURCE_SUFFIX.length()) {
                    return null;
                }
                segment = segment.substring(0, segment.length() - SOURCE_SUFFIX.length());
            }
            if (index > 0) {
                logicalModule.append('/');
            }
            logicalModule.append(segment);
        }

        String candidate = logicalModule.toString();
        try {
            return ProtosPackageRuntimeNames.requireLogicalName(candidate);
        } catch (IOException invalidLogicalName) {
            return null;
        }
    }
}
