/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Host composition gate from one already-selected package root to verified immutable custody.
 *
 * <p>Package ContentIdentity policy remains owned by bundled Protos {@code self:ContentIdentity}.
 * This class performs no tree walking, hashing, package discovery, store scanning or source reopen.
 * It captures the selected root once through {@link ProtosCapturedFilesystemCustody}, materializes
 * one Package Tool-domain view of that capture, invokes the Protos verifier, and returns the same
 * custody only when the verifier returns exactly the supplied Filesystem object.
 *
 * <p>The Package Tool Process receives no ambient/root Filesystem for the store. The source Path is
 * consumed only by the capture step; the verifier receives the captured Filesystem plus inert
 * expected-identity Strings. The tool Process is terminated before successful custody is returned.
 */
final class ProtosPackageContentVerification {
    private static final String VERIFY_SOURCE =
            "ContentIdentity: import(\"self:ContentIdentity\")\n"
                    + "expected: {\n"
                    + "    method: expectedContentIdentityMethod\n"
                    + "    algorithm: expectedContentIdentityAlgorithm\n"
                    + "    hex: expectedContentIdentityHex\n"
                    + "}\n"
                    + "ContentIdentity.verify(capturedFilesystem, expected)\n";

    private ProtosPackageContentVerification() {}

    static ProtosCapturedFilesystemCustody captureAndVerify(
            Path coreRoot,
            Path packageToolRoot,
            Path selectedRoot,
            ProtosModuleResolver standardLibraryResolver,
            String expectedMethod,
            String expectedAlgorithm,
            String expectedHex)
            throws IOException {
        return captureAndVerify(
                coreRoot,
                packageToolRoot,
                selectedRoot,
                standardLibraryResolver,
                expectedMethod,
                expectedAlgorithm,
                expectedHex,
                ignored -> {});
    }

    static ProtosCapturedFilesystemCustody captureAndVerify(
            Path coreRoot,
            Path packageToolRoot,
            Path selectedRoot,
            ProtosModuleResolver standardLibraryResolver,
            String expectedMethod,
            String expectedAlgorithm,
            String expectedHex,
            Consumer<ProtosProcessRuntime> processObserver)
            throws IOException {
        Objects.requireNonNull(coreRoot, "coreRoot");
        Objects.requireNonNull(packageToolRoot, "packageToolRoot");
        Objects.requireNonNull(selectedRoot, "selectedRoot");
        Objects.requireNonNull(standardLibraryResolver, "standardLibraryResolver");
        Objects.requireNonNull(expectedMethod, "expectedMethod");
        Objects.requireNonNull(expectedAlgorithm, "expectedAlgorithm");
        Objects.requireNonNull(expectedHex, "expectedHex");
        Objects.requireNonNull(processObserver, "processObserver");

        ProtosCapturedFilesystemCustody custody =
                ProtosCapturedFilesystemCustody.captureSelectedRoot(selectedRoot);
        boolean transferred = false;
        try {
            ProtosBundledToolModuleResolver toolResolver =
                    new ProtosBundledToolModuleResolver(
                            "package", packageToolRoot, standardLibraryResolver);
            ProtosPrelude toolPrelude =
                    new ProtosCoreBootstrap().bootstrap(coreRoot, toolResolver);
            ProtosStandaloneProcessBootstrap.Result bootstrap =
                    ProtosStandaloneProcessBootstrap.create(
                            toolPrelude,
                            List.of(),
                            emptyEnvironmentDomain(),
                            List.of(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
            ProtosProcessRuntime process = bootstrap.process();

            try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
                try {
                    ProtosPolyglotProcessContext processContext =
                            runtimeHost.hostProcess(
                                    process,
                                    InputStream.nullInputStream(),
                                    OutputStream.nullOutputStream(),
                                    OutputStream.nullOutputStream());
                    processObserver.accept(process);
                    ProtosFilesystemValue capturedFilesystem =
                            custody.materialize(bootstrap.activation());
                    bindVerificationInputs(
                            bootstrap,
                            capturedFilesystem,
                            expectedMethod,
                            expectedAlgorithm,
                            expectedHex);

                    Source verifySource =
                            Source.newBuilder(
                                            ProtosLanguage.ID,
                                            VERIFY_SOURCE,
                                            "<package-content-verification>")
                                    .mimeType(ProtosLanguage.MIME_TYPE)
                                    .build();
                    ProtosExecutionOutcome outcome =
                            processContext.execute(verifySource, bootstrap.activation());
                    if (outcome.state() != ProtosExecutionOutcome.State.COMPLETED
                            || outcome.value() != capturedFilesystem) {
                        throw new IOException(
                                "captured package ContentIdentity verification failed");
                    }
                    transferred = true;
                    return custody;
                } catch (IOException failure) {
                    throw failure;
                } catch (RuntimeException failure) {
                    throw new IOException(
                            "captured package ContentIdentity verification failed", failure);
                } finally {
                    process.requestTerminationForRuntime();
                }
            }
        } finally {
            if (!transferred) {
                custody.close();
            }
        }
    }

    private static void bindVerificationInputs(
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            ProtosFilesystemValue capturedFilesystem,
            String expectedMethod,
            String expectedAlgorithm,
            String expectedHex)
            throws IOException {
        var context = bootstrap.activation().context();
        for (String slot :
                List.of(
                        "capturedFilesystem",
                        "expectedContentIdentityMethod",
                        "expectedContentIdentityAlgorithm",
                        "expectedContentIdentityHex")) {
            if (context.hasLocalSlot(slot)) {
                throw new IOException("Package Tool verification bootstrap slot already exists: " + slot);
            }
        }
        context.createLocalSlot("capturedFilesystem", capturedFilesystem);
        context.createLocalSlot(
                "expectedContentIdentityMethod", new ProtosStringValue(expectedMethod));
        context.createLocalSlot(
                "expectedContentIdentityAlgorithm", new ProtosStringValue(expectedAlgorithm));
        context.createLocalSlot("expectedContentIdentityHex", new ProtosStringValue(expectedHex));
    }

    private static ProtosEnvironmentValue.NativeNameDomain emptyEnvironmentDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }
}
