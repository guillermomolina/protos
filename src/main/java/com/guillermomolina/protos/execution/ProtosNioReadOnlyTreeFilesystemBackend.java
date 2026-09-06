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

import com.guillermomolina.protos.runtime.ProtosFileFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemOpenFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemOpenOptions;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only NIO Filesystem backend confined to one complete directory tree. */
public final class ProtosNioReadOnlyTreeFilesystemBackend
        implements ProtosStandardFilesystemProtocol.Backend, AutoCloseable {
    private static final int MAX_READ_CHUNK = 64 * 1024;

    private final Path authorityRoot;
    private final SecureDirectoryStream<Path> secureRoot;
    private boolean closed;

    public ProtosNioReadOnlyTreeFilesystemBackend(Path authorityRoot) throws IOException {
        this.authorityRoot =
                Objects.requireNonNull(authorityRoot, "authorityRoot")
                        .toAbsolutePath()
                        .normalize();
        DirectoryStream<Path> opened = Files.newDirectoryStream(this.authorityRoot);
        SecureDirectoryStream<Path> secure = secureDirectoryStream(opened);
        if (secure == null) {
            opened.close();
        }
        this.secureRoot = secure;
    }

    public boolean secureConfinementAvailable() {
        return secureRoot != null;
    }

    @Override
    public ProtosFilesystemOpenFlow.Cancellation open(
            ProtosPathValue path,
            ProtosFilesystemOpenOptions options,
            ProtosStandardFilesystemProtocol.OpenCompletion completion) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(completion, "completion");

        List<String> components = permittedRelativeComponents(path);
        if (closed
                || secureRoot == null
                || components == null
                || !readOnlyExisting(options)) {
            completion.failed();
            return () -> {};
        }

        SeekableByteChannel channel = null;
        ArrayList<SecureDirectoryStream<Path>> openedDirectories = new ArrayList<>();
        try {
            SecureDirectoryStream<Path> current = secureRoot;
            for (int index = 0; index < components.size() - 1; index++) {
                SecureDirectoryStream<Path> next =
                        current.newDirectoryStream(
                                nativeComponent(components.get(index)),
                                LinkOption.NOFOLLOW_LINKS);
                openedDirectories.add(next);
                current = next;
            }

            channel =
                    current.newByteChannel(
                            nativeComponent(components.get(components.size() - 1)),
                            Set.<OpenOption>of(
                                    StandardOpenOption.READ,
                                    LinkOption.NOFOLLOW_LINKS));

            ReadOnlyResource resource = new ReadOnlyResource(channel);
            channel = null;
            completion.succeeded(
                    resource,
                    new ProtosFileFlow.Capabilities(
                            true, false, false, false, false, false, false),
                    resource::closeSilently);
        } catch (IOException | RuntimeException failure) {
            closeSilently(channel);
            completion.failed();
        } finally {
            closeDirectories(openedDirectories);
        }
        return () -> {};
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (secureRoot != null) {
            secureRoot.close();
        }
    }

    private List<String> permittedRelativeComponents(ProtosPathValue path) {
        if (path.rooted() || path.components().isEmpty()) {
            return null;
        }
        ArrayList<String> result = new ArrayList<>(path.components().size());
        for (ProtosPathValue.Component component : path.components()) {
            if (!(component instanceof ProtosPathValue.Normal normal)) {
                return null;
            }
            String name = normal.name();
            if (!portableNativeComponent(name)) {
                return null;
            }
            result.add(name);
        }
        return List.copyOf(result);
    }

    private boolean portableNativeComponent(String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return false;
        }
        Path candidate = authorityRoot.getFileSystem().getPath(name);
        return !candidate.isAbsolute()
                && candidate.getNameCount() == 1
                && candidate.getParent() == null
                && candidate.toString().equals(name);
    }

    private Path nativeComponent(String name) {
        return authorityRoot.getFileSystem().getPath(name);
    }

    private static boolean readOnlyExisting(ProtosFilesystemOpenOptions options) {
        return options.readAccess()
                && !options.writeAccess()
                && options.creation() == ProtosFilesystemOpenOptions.Creation.EXISTING
                && !options.truncateInitialContent()
                && options.placement() == ProtosFilesystemOpenOptions.Placement.POSITIONED;
    }

    private static SecureDirectoryStream<Path> secureDirectoryStream(
            DirectoryStream<Path> stream) {
        if (!(stream instanceof SecureDirectoryStream<?> secure)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        SecureDirectoryStream<Path> result = (SecureDirectoryStream<Path>) secure;
        return result;
    }

    private static void closeDirectories(List<SecureDirectoryStream<Path>> directories) {
        for (int index = directories.size() - 1; index >= 0; index--) {
            try {
                directories.get(index).close();
            } catch (IOException ignored) {
                // The selected File channel already owns its stable resource binding.
            }
        }
    }

    private static void closeSilently(SeekableByteChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // Failed acquisition retains no Protos-visible resource custody.
        }
    }

    private static final class ReadOnlyResource implements ProtosFileFlow.ReadableResource {
        private final SeekableByteChannel channel;
        private boolean closed;

        ReadOnlyResource(SeekableByteChannel channel) {
            this.channel = Objects.requireNonNull(channel, "channel");
        }

        @Override
        public synchronized ProtosFileFlow.Cancellation readAt(
                BigInteger position,
                int maxBytes,
                ProtosFileFlow.ReadCompletion completion) {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(completion, "completion");
            if (closed
                    || position.signum() < 0
                    || position.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                    || maxBytes <= 0) {
                completion.failed();
                return () -> {};
            }

            int requested = Math.min(maxBytes, MAX_READ_CHUNK);
            ByteBuffer buffer = ByteBuffer.allocate(requested);
            try {
                channel.position(position.longValueExact());
                int read = channel.read(buffer);
                if (read < 0) {
                    completion.eof();
                } else if (read == 0) {
                    completion.failed();
                } else {
                    byte[] bytes = new byte[read];
                    buffer.flip();
                    buffer.get(bytes);
                    completion.data(bytes);
                }
            } catch (IOException | RuntimeException failure) {
                completion.failed();
            }
            return () -> {};
        }

        @Override
        public synchronized void close(ProtosFileFlow.CloseCompletion completion) {
            Objects.requireNonNull(completion, "completion");
            if (closed) {
                completion.succeeded();
                return;
            }
            closed = true;
            try {
                channel.close();
                completion.succeeded();
            } catch (IOException failure) {
                completion.failed();
            }
        }

        void closeSilently() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                channel.close();
            } catch (IOException ignored) {
                // Runtime cleanup cannot create a new portable File result.
            }
        }
    }
}
