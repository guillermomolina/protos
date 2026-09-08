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
package com.guillermomolina.protos.conformance;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosNioConfinedFilesystemBackend;
import com.guillermomolina.protos.execution.ProtosSourceFileLoader;
import com.guillermomolina.protos.execution.ProtosStandardFilesystemProtocol;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosFilesystemTreeSurfaceConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path CASE_ROOT = Path.of("protos", "tests", "filesystem");

    @TempDir Path authorityRoot;

    @Test
    void entriesSelectorIsProtosVisibleAndUnsupportedBackendFailsAsIOError() throws Exception {
        try (Fixture fixture = fixture()) {
            assertIoError(
                    fixture,
                    () -> {
                        execute("entries-unsupported-io-error.protos", fixture.activation());
                    });
        }
    }

    @Test
    void captureTreeSelectorIsProtosVisibleAndUnsupportedBackendFailsAsIOError()
            throws Exception {
        try (Fixture fixture = fixture()) {
            assertIoError(
                    fixture,
                    () -> {
                        execute(
                                "capture-tree-unsupported-io-error.protos",
                                fixture.activation());
                    });
        }
    }

    private Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosNioConfinedFilesystemBackend backend =
                new ProtosNioConfinedFilesystemBackend(authorityRoot, Set.of(), Set.of());
        if (!backend.secureNamespaceConfinementAvailable()) {
            backend.close();
            assumeTrue(false, "host provider has no SecureDirectoryStream");
        }

        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation, backend);
    }

    private static void assertIoError(Fixture fixture, ThrowingAction action) {
        ProtosSignalException signal = assertThrows(ProtosSignalException.class, action::run);
        assertSame(
                ProtosCoreErrors.prototype(
                        fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                signal.error().parent().orElseThrow());
    }

    private static Object execute(String file, ProtosActivation activation) throws IOException {
        return new ProtosSourceFileLoader().load(CASE_ROOT.resolve(file)).call(activation);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private record Fixture(
            ProtosActivation activation, ProtosNioConfinedFilesystemBackend backend)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            backend.close();
        }
    }
}
