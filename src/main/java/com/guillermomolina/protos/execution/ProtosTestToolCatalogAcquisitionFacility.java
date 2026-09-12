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
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * D101 Test-Tool-private one-shot resource-catalog acquisition capability.
 *
 * <p>This is deliberately not a Filesystem. The installed bootstrap-local Closure accepts exactly
 * one already-selected D099 PATH and can consume its acquisition authority only once. It resolves
 * that PATH against the captured Test Tool invocation working directory, follows ordinary host
 * symbolic links, requires one readable regular final target, reads its complete bytes once, and
 * returns a frozen Bytes snapshot in the caller Prelude.
 *
 * <p>The capability performs no CLI parsing, search, merge, environment lookup, UTF-8 decoding,
 * TOML/schema parsing, provider resolution or resource admission. It is installed only into the
 * initial bundled Test Tool module activation and is never a Core/prelude binding.
 */
public final class ProtosTestToolCatalogAcquisitionFacility {
    public static final String BOOTSTRAP_SLOT = "catalogAcquirer";

    private ProtosTestToolCatalogAcquisitionFacility() {}

    public static void install(
            ProtosActivation activation,
            Path invocationWorkingDirectory) {
        Objects.requireNonNull(activation, "activation");
        Path capturedWorkingDirectory =
                Objects.requireNonNull(invocationWorkingDirectory, "invocationWorkingDirectory")
                        .toAbsolutePath()
                        .normalize();

        if (activation.context().hasLocalSlot(BOOTSTRAP_SLOT)) {
            throw new IllegalStateException(
                    "catalog acquisition bootstrap slot already exists: " + BOOTSTRAP_SLOT);
        }

        AtomicBoolean consumed = new AtomicBoolean(false);
        activation.context().createLocalSlot(
                BOOTSTRAP_SLOT,
                ProtosClosureValue.nativeClosure(
                        (callActivation, supplied) ->
                                acquire(
                                        callActivation,
                                        supplied,
                                        capturedWorkingDirectory,
                                        consumed)));
    }

    private static Object acquire(
            ProtosActivation activation,
            List<?> supplied,
            Path invocationWorkingDirectory,
            AtomicBoolean consumed) {
        if (!consumed.compareAndSet(false, true)) {
            throw toolError(activation);
        }

        if (supplied.size() != 1
                || !(supplied.get(0) instanceof ProtosStringValue selectedPath)) {
            throw toolError(activation);
        }

        final Path path;
        try {
            Path parsed = Path.of(selectedPath.value());
            path =
                    parsed.isAbsolute()
                            ? parsed
                            : invocationWorkingDirectory.resolve(parsed);
        } catch (InvalidPathException failure) {
            throw toolError(activation);
        }

        final byte[] content;
        try {
            Path finalPath = path.toRealPath();
            BasicFileAttributes attributes =
                    Files.readAttributes(finalPath, BasicFileAttributes.class);
            if (!attributes.isRegularFile() || !Files.isReadable(finalPath)) {
                throw toolError(activation);
            }
            content = Files.readAllBytes(finalPath);
        } catch (IOException | SecurityException failure) {
            throw toolError(activation);
        }

        return frozenBytes(activation, content);
    }

    private static ProtosBytesValue frozenBytes(
            ProtosActivation activation,
            byte[] content) {
        ProtosPrelude prelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "catalog acquisition requires caller Core prelude"));
        ProtosBytesValue bytes =
                new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        for (byte octet : content) {
            bytes.indexedAdd(
                    new ProtosIntegerValue(
                            BigInteger.valueOf(octet & 0xff)));
        }
        bytes.freeze();
        return bytes;
    }

    private static ProtosSignalException toolError(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
