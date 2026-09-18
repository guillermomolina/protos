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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D137 B-prime resolver for one official direct-file source tree.
 *
 * <p>Local specifiers are exact {@code ./}/{@code ../} names resolved relative to the importing
 * local module. The resolver keeps one pinned secure handle for the selected entry directory,
 * performs exact-component, no-follow traversal beneath that authority, and delegates non-local
 * specifiers to the supplied resolver (currently the Standard Library resolver).
 *
 * <p>The selected command-line entry is already-authorized source. It receives a canonical local
 * ModuleKey and its already-read characters are retained so the canonical initial-module lifecycle
 * can cache that exact module before execution without rereading the entry through local traversal.
 */
public final class ProtosDirectFileModuleResolver
        implements ProtosModuleResolver, AutoCloseable {
    private static final String LOCAL_KEY_PREFIX = "direct-file:";

    private final Path sourceRoot;
    private final ProtosModuleResolver fallbackResolver;
    private final ProtosModuleKey entryModule;
    private final ProtosModuleSource entrySource;
    private final Map<ProtosModuleKey, List<String>> localPaths =
            new ConcurrentHashMap<>();
    private final SecureDirectoryStream<Path> secureRoot;
    private boolean closed;

    public ProtosDirectFileModuleResolver(
            Path selectedEntry,
            CharSequence entryCharacters,
            ProtosModuleResolver fallbackResolver) {
        Path entry =
                Objects.requireNonNull(selectedEntry, "selectedEntry")
                        .toAbsolutePath()
                        .normalize();
        Path parent = entry.getParent();
        Path fileName = entry.getFileName();
        if (parent == null || fileName == null) {
            throw new IllegalArgumentException(
                    "direct-file entry must have a containing directory and file name");
        }

        this.sourceRoot = parent;
        this.fallbackResolver =
                Objects.requireNonNull(fallbackResolver, "fallbackResolver");

        List<String> entryPath = List.of(fileName.toString());
        this.entryModule = localKey(entryPath);
        this.entrySource =
                new ProtosModuleSource(
                        entryModule,
                        Objects.requireNonNull(entryCharacters, "entryCharacters").toString(),
                        Optional.of(entry));
        localPaths.put(entryModule, entryPath);

        SecureDirectoryStream<Path> secured = null;
        try {
            DirectoryStream<Path> opened = Files.newDirectoryStream(sourceRoot);
            secured = secureDirectoryStream(opened);
            if (secured == null) {
                opened.close();
            }
        } catch (IOException | RuntimeException unavailable) {
            secured = null;
        }
        this.secureRoot = secured;
    }

    public ProtosModuleKey entryModule() {
        return entryModule;
    }

    public boolean secureConfinementAvailable() {
        return secureRoot != null;
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws Exception {
        Objects.requireNonNull(exactSpecifier, "exactSpecifier");
        Objects.requireNonNull(importingModule, "importingModule");
        requireOpen();

        if (localSpecifier(exactSpecifier)) {
            return resolveLocal(exactSpecifier, importingModule);
        }
        return fallbackResolver.resolve(exactSpecifier, importingModule);
    }

    @Override
    public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
        Objects.requireNonNull(key, "key");
        requireOpen();

        if (key.equals(entryModule)) {
            return entrySource;
        }

        List<String> localPath = localPaths.get(key);
        if (localPath != null) {
            return loadLocalSource(key, localPath);
        }

        return fallbackResolver.loadSource(key);
    }

    private ProtosModuleKey resolveLocal(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws IOException {
        ProtosModuleKey importer =
                importingModule.orElseThrow(
                        () ->
                                new IOException(
                                        "direct-file local import requires a local importing module"));
        List<String> importerPath = localPaths.get(importer);
        if (importerPath == null) {
            throw new IOException(
                    "direct-file local import requires a local importing module");
        }

        List<String> target =
                normalizeLocalSpecifier(
                        importerPath.subList(0, importerPath.size() - 1),
                        exactSpecifier);
        ProtosModuleKey key = localKey(target);
        List<String> previous = localPaths.putIfAbsent(key, target);
        if (previous != null && !previous.equals(target)) {
            throw new IOException("direct-file local ModuleKey collision");
        }
        return key;
    }

    private List<String> normalizeLocalSpecifier(
            List<String> importingDirectory, String exactSpecifier)
            throws IOException {
        Deque<String> normalized = new ArrayDeque<>(importingDirectory);
        String[] pieces = exactSpecifier.split("/", -1);

        for (String piece : pieces) {
            if (piece.isEmpty()) {
                throw new IOException("invalid direct-file local module specifier");
            }
            if (piece.equals(".")) {
                continue;
            }
            if (piece.equals("..")) {
                if (normalized.isEmpty()) {
                    throw new IOException(
                            "direct-file local module escaped its source root");
                }
                normalized.removeLast();
                continue;
            }

            requirePortableNativeComponent(piece);
            normalized.addLast(piece);
        }

        if (normalized.isEmpty()) {
            throw new IOException("direct-file local module must name a source file");
        }
        return List.copyOf(normalized);
    }

    private synchronized ProtosModuleSource loadLocalSource(
            ProtosModuleKey key, List<String> components)
            throws IOException {
        requireOpen();
        if (secureRoot == null) {
            throw new IOException(
                    "secure direct-file local-source confinement is unavailable");
        }

        SecureDirectoryStream<Path> current = null;
        try {
            current =
                    secureRoot.newDirectoryStream(
                            nativeComponent("."), LinkOption.NOFOLLOW_LINKS);

            for (int index = 0; index < components.size(); index++) {
                String component = components.get(index);
                requireExactChild(current, component);
                boolean last = index == components.size() - 1;

                if (!last) {
                    SecureDirectoryStream<Path> next =
                            current.newDirectoryStream(
                                    nativeComponent(component),
                                    LinkOption.NOFOLLOW_LINKS);
                    SecureDirectoryStream<Path> previous = current;
                    current = next;
                    previous.close();
                    continue;
                }

                BasicFileAttributeView attributeView =
                        current.getFileAttributeView(
                                nativeComponent(component),
                                BasicFileAttributeView.class,
                                LinkOption.NOFOLLOW_LINKS);
                if (attributeView == null) {
                    throw new IOException(
                            "direct-file local module attributes unavailable");
                }
                BasicFileAttributes attributes = attributeView.readAttributes();
                if (!attributes.isRegularFile()) {
                    throw new IOException("direct-file local module is not a regular source file");
                }

                String characters;
                try (SeekableByteChannel channel =
                                current.newByteChannel(
                                        nativeComponent(component),
                                        Set.<OpenOption>of(
                                                StandardOpenOption.READ,
                                                LinkOption.NOFOLLOW_LINKS));
                        Reader reader =
                                Channels.newReader(
                                        channel,
                                        StandardCharsets.UTF_8
                                                .newDecoder()
                                                .onMalformedInput(CodingErrorAction.REPORT)
                                                .onUnmappableCharacter(CodingErrorAction.REPORT),
                                        -1)) {
                    characters = readAll(reader);
                }

                return new ProtosModuleSource(
                        key,
                        characters,
                        Optional.of(physicalPath(components)));
            }
        } catch (DirectoryIteratorException failure) {
            throw failure.getCause();
        } finally {
            closeSilently(current);
        }

        throw new IOException("direct-file local module not found");
    }

    private void requireExactChild(
            SecureDirectoryStream<Path> directory, String expectedName)
            throws IOException {
        try {
            for (Path observed : directory) {
                Path fileName = observed.getFileName();
                if (fileName != null && fileName.toString().equals(expectedName)) {
                    return;
                }
            }
        } catch (DirectoryIteratorException failure) {
            throw failure.getCause();
        }
        throw new IOException("direct-file local module not found");
    }

    private void requirePortableNativeComponent(String name) throws IOException {
        try {
            Path candidate = sourceRoot.getFileSystem().getPath(name);
            if (candidate.isAbsolute()
                    || candidate.getNameCount() != 1
                    || candidate.getParent() != null
                    || !candidate.toString().equals(name)) {
                throw new IOException(
                        "invalid direct-file local module path component");
            }
        } catch (InvalidPathException failure) {
            throw new IOException(
                    "invalid direct-file local module path component",
                    failure);
        }
    }

    private Path physicalPath(List<String> components) {
        Path result = sourceRoot;
        for (String component : components) {
            result = result.resolve(component);
        }
        return result.toAbsolutePath().normalize();
    }

    private Path nativeComponent(String name) {
        return sourceRoot.getFileSystem().getPath(name);
    }

    private static boolean localSpecifier(String specifier) {
        return specifier.startsWith("./") || specifier.startsWith("../");
    }

    private static ProtosModuleKey localKey(List<String> components) {
        return new ProtosModuleKey(
                LOCAL_KEY_PREFIX + String.join("/", components));
    }

    private static String readAll(Reader reader) throws IOException {
        char[] buffer = new char[8192];
        StringBuilder result = new StringBuilder();
        for (;;) {
            int read = reader.read(buffer);
            if (read < 0) {
                return result.toString();
            }
            if (read == 0) {
                continue;
            }
            result.append(buffer, 0, read);
        }
    }

    private synchronized void requireOpen() throws IOException {
        if (closed) {
            throw new IOException("direct-file module resolver is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeSilently(secureRoot);
    }

    private static SecureDirectoryStream<Path> secureDirectoryStream(
            DirectoryStream<Path> stream) {
        if (!(stream instanceof SecureDirectoryStream<?> secure)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        SecureDirectoryStream<Path> result =
                (SecureDirectoryStream<Path>) secure;
        return result;
    }

    private static void closeSilently(DirectoryStream<Path> stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // Resolver-owned source traversal handles have no guest-visible close result.
        }
    }
}
