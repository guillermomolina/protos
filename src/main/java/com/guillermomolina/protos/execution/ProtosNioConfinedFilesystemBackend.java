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

import com.guillermomolina.protos.runtime.ProtosFilesystemNamespaceMutationFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemOpenFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemOpenOptions;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Confined direct-child NIO Filesystem backend with independent read and namespace authority.
 *
 * <p>Existing-file read opens delegate to {@link ProtosNioReadOnlyFilesystemBackend}; namespace
 * mutation is separately restricted to an explicit set of direct-child names. The two allowlists
 * therefore do not accidentally grant one another's authority.
 *
 * <p>Namespace operations retain a {@link SecureDirectoryStream} for the authority root and use
 * only relative entry operations on that pinned directory. They never pre-classify a mutable final
 * name before acting on it. {@code move} supplies the D042 atomic replacement attempt; a provider
 * that cannot replace the selected target atomically fails the operation instead. {@code deleteFile}
 * acts on the final entry itself, including a symbolic link rather than its referent, and never
 * performs recursive deletion.
 *
 * <p>This backend is intentionally general and contains no package-manifest, lockfile, staging-name,
 * or package-command policy. Hosts choose the readable and namespace-mutable direct-child sets when
 * provisioning a Filesystem capability.
 */
public final class ProtosNioConfinedFilesystemBackend
        implements ProtosStandardFilesystemProtocol.Backend, AutoCloseable {
    private final Path authorityRoot;
    private final Set<String> mutableDirectChildren;
    private final ProtosNioReadOnlyFilesystemBackend readBackend;
    private final SecureDirectoryStream<Path> secureRoot;
    private boolean closed;

    public ProtosNioConfinedFilesystemBackend(
            Path authorityRoot,
            Set<String> readableDirectChildren,
            Set<String> mutableDirectChildren)
            throws IOException {
        this.authorityRoot =
                Objects.requireNonNull(authorityRoot, "authorityRoot")
                        .toAbsolutePath()
                        .normalize();

        LinkedHashSet<String> mutableNames = new LinkedHashSet<>();
        for (String name : Objects.requireNonNull(mutableDirectChildren, "mutableDirectChildren")) {
            requireDirectChildName(name);
            mutableNames.add(name);
        }
        this.mutableDirectChildren = Set.copyOf(mutableNames);

        this.readBackend =
                new ProtosNioReadOnlyFilesystemBackend(
                        this.authorityRoot,
                        Objects.requireNonNull(readableDirectChildren, "readableDirectChildren"));

        SecureDirectoryStream<Path> secured = null;
        try {
            DirectoryStream<Path> opened = Files.newDirectoryStream(this.authorityRoot);
            secured = secureDirectoryStream(opened);
            if (secured == null) {
                opened.close();
            }
        } catch (IOException | RuntimeException failure) {
            try {
                readBackend.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        this.secureRoot = secured;
    }

    /** True only when namespace operations can stay relative to a pinned authority directory. */
    public boolean secureNamespaceConfinementAvailable() {
        return secureRoot != null;
    }

    @Override
    public ProtosFilesystemOpenFlow.Cancellation open(
            ProtosPathValue path,
            ProtosFilesystemOpenOptions options,
            ProtosStandardFilesystemProtocol.OpenCompletion completion) {
        return readBackend.open(path, options, completion);
    }

    @Override
    public ProtosFilesystemNamespaceMutationFlow.Cancellation replace(
            ProtosPathValue sourcePath,
            ProtosPathValue targetPath,
            ProtosFilesystemNamespaceMutationFlow.MutationCompletion completion) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(targetPath, "targetPath");
        Objects.requireNonNull(completion, "completion");

        String sourceChild = permittedMutableDirectChild(sourcePath);
        String targetChild = permittedMutableDirectChild(targetPath);
        if (closed || secureRoot == null || sourceChild == null || targetChild == null) {
            completion.failed();
            return () -> {};
        }

        completion.commitPortableEffect(
                () ->
                        secureRoot.move(
                                nativeChild(sourceChild), secureRoot, nativeChild(targetChild)));
        return () -> {};
    }

    @Override
    public ProtosFilesystemNamespaceMutationFlow.Cancellation remove(
            ProtosPathValue path,
            ProtosFilesystemNamespaceMutationFlow.MutationCompletion completion) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(completion, "completion");

        String child = permittedMutableDirectChild(path);
        if (closed || secureRoot == null || child == null) {
            completion.failed();
            return () -> {};
        }

        completion.commitPortableEffect(() -> secureRoot.deleteFile(nativeChild(child)));
        return () -> {};
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        IOException failure = null;
        if (secureRoot != null) {
            try {
                secureRoot.close();
            } catch (IOException closeFailure) {
                failure = closeFailure;
            }
        }
        try {
            readBackend.close();
        } catch (IOException closeFailure) {
            if (failure == null) {
                failure = closeFailure;
            } else {
                failure.addSuppressed(closeFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private String permittedMutableDirectChild(ProtosPathValue path) {
        if (path.rooted() || path.components().size() != 1) {
            return null;
        }
        ProtosPathValue.Component component = path.components().get(0);
        if (!(component instanceof ProtosPathValue.Normal normal)) {
            return null;
        }
        return mutableDirectChildren.contains(normal.name()) ? normal.name() : null;
    }

    private Path nativeChild(String name) {
        return authorityRoot.getFileSystem().getPath(name);
    }

    private void requireDirectChildName(String name) {
        Objects.requireNonNull(name, "mutable direct-child name");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("invalid mutable direct-child name");
        }
        Path candidate = authorityRoot.getFileSystem().getPath(name);
        if (candidate.isAbsolute()
                || candidate.getNameCount() != 1
                || candidate.getParent() != null
                || !candidate.toString().equals(name)) {
            throw new IllegalArgumentException("mutable name is not one native direct child");
        }
    }

    private static SecureDirectoryStream<Path> secureDirectoryStream(DirectoryStream<Path> stream) {
        if (!(stream instanceof SecureDirectoryStream<?> secure)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        SecureDirectoryStream<Path> result = (SecureDirectoryStream<Path>) secure;
        return result;
    }
}
