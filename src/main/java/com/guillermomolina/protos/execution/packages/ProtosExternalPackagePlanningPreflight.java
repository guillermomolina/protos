/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Host-mechanical composition of borrowed verified external custody into Protos-owned V2 planning.
 *
 * <p>This boundary does not acquire, discover, re-capture, detach or resolve external packages. The
 * caller retains ownership of every supplied {@link ProtosCapturedFilesystemCustody}; this class
 * materializes temporary Package Tool-domain views over those exact captured backings, invokes the
 * bundled-Protos generation-2 planner, terminates the Package Tool Process and returns only the raw
 * inert plan value for the later F2E4 defensive-detach boundary.
 */
final class ProtosExternalPackagePlanningPreflight {
    /** D242: exact bundled Package Tool SOURCE entry {@code ExternalPlanning.protos}. */
    private static final String ENTRY = "ExternalPlanning";

    record VerifiedExternalPackage(
            String kind,
            String packageId,
            String exact,
            String contentMethod,
            String contentAlgorithm,
            String contentHex,
            ProtosCapturedFilesystemCustody custody) {
        VerifiedExternalPackage {
            kind = requireNonEmpty(kind, "kind");
            packageId = requireNonEmpty(packageId, "packageId");
            exact = requireNonEmpty(exact, "exact");
            contentMethod = requireNonEmpty(contentMethod, "contentMethod");
            contentAlgorithm = requireNonEmpty(contentAlgorithm, "contentAlgorithm");
            contentHex = requireNonEmpty(contentHex, "contentHex");
            Objects.requireNonNull(custody, "custody");
            if (!kind.equals("registry") && !kind.equals("git")) {
                throw new IllegalArgumentException("unsupported external package kind: " + kind);
            }
        }

        /** Exact external identity of this verified input; never consults the custody. */
        ProtosExactExternalPackageIdentity identity() {
            ProtosPackageContentIdentity content =
                    new ProtosPackageContentIdentity(
                            contentMethod, contentAlgorithm, contentHex);
            return kind.equals("registry")
                    ? new ProtosExactExternalPackageIdentity.Registry(packageId, exact, content)
                    : new ProtosExactExternalPackageIdentity.Git(packageId, exact, content);
        }

        /**
         * Pairs one complete exact identity with the custody F2E2 verified for exactly that
         * identity. Every field comes from {@code identity}; nothing is derived from a Path.
         */
        static VerifiedExternalPackage fromIdentity(
                ProtosExactExternalPackageIdentity identity,
                ProtosCapturedFilesystemCustody custody) {
            ProtosPackageContentIdentity content = identity.content();
            return switch (identity) {
                case ProtosExactExternalPackageIdentity.Registry exact ->
                        registry(
                                exact.packageId(),
                                exact.exactVersion(),
                                content.method(),
                                content.algorithm(),
                                content.hex(),
                                custody);
                case ProtosExactExternalPackageIdentity.Git exact ->
                        git(
                                exact.packageId(),
                                exact.revision(),
                                content.method(),
                                content.algorithm(),
                                content.hex(),
                                custody);
            };
        }

        static VerifiedExternalPackage registry(
                String packageId,
                String exactVersion,
                String contentMethod,
                String contentAlgorithm,
                String contentHex,
                ProtosCapturedFilesystemCustody custody) {
            return new VerifiedExternalPackage(
                    "registry",
                    packageId,
                    exactVersion,
                    contentMethod,
                    contentAlgorithm,
                    contentHex,
                    custody);
        }

        static VerifiedExternalPackage git(
                String packageId,
                String exactRevision,
                String contentMethod,
                String contentAlgorithm,
                String contentHex,
                ProtosCapturedFilesystemCustody custody) {
            return new VerifiedExternalPackage(
                    "git",
                    packageId,
                    exactRevision,
                    contentMethod,
                    contentAlgorithm,
                    contentHex,
                    custody);
        }

        private static String requireNonEmpty(String value, String label) {
            Objects.requireNonNull(value, label);
            if (value.isEmpty()) {
                throw new IllegalArgumentException(label + " must not be empty");
            }
            return value;
        }
    }

    private ProtosExternalPackagePlanningPreflight() {}

    static Object buildRaw(
            Path coreRoot,
            Path packageToolRoot,
            Path projectRoot,
            ProtosModuleResolver standardLibraryResolver,
            List<VerifiedExternalPackage> verifiedExternalPackages)
            throws IOException {
        return buildRaw(
                coreRoot,
                packageToolRoot,
                projectRoot,
                standardLibraryResolver,
                verifiedExternalPackages,
                ignored -> {});
    }

    static Object buildRaw(
            Path coreRoot,
            Path packageToolRoot,
            Path projectRoot,
            ProtosModuleResolver standardLibraryResolver,
            List<VerifiedExternalPackage> verifiedExternalPackages,
            Consumer<ProtosProcessRuntime> processObserver)
            throws IOException {
        Objects.requireNonNull(coreRoot, "coreRoot");
        Objects.requireNonNull(packageToolRoot, "packageToolRoot");
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(standardLibraryResolver, "standardLibraryResolver");
        Objects.requireNonNull(verifiedExternalPackages, "verifiedExternalPackages");
        Objects.requireNonNull(processObserver, "processObserver");

        List<VerifiedExternalPackage> borrowed =
                List.copyOf(verifiedExternalPackages);

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosNioReadOnlyTreeFilesystemBackend backend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(projectRoot)) {
            if (!backend.secureConfinementAvailable()) {
                throw new IOException(
                        "secure read-only external package preflight is unavailable on this host");
            }

            ProtosBundledToolModuleResolver toolResolver =
                    new ProtosBundledToolModuleResolver(
                            "package", packageToolRoot, (packageToolRoot).resolveSibling("shared"), standardLibraryResolver);
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

            try {
                // The hosted Process context executes the explicit SOURCE entry below.
                        runtimeHost.hostProcess(
                                process,
                                InputStream.nullInputStream(),
                                OutputStream.nullOutputStream(),
                                OutputStream.nullOutputStream());
                processObserver.accept(process);

                bindProjectFilesystem(toolPrelude, bootstrap, backend);
                bindBorrowedExternalInputs(toolPrelude, bootstrap, borrowed);

                ProtosExecutionOutcome outcome =
                        ProtosCanonicalInitialModuleExecution.execute(
                                toolPrelude,
                                toolResolver,
                                toolResolver.entryModule(ENTRY),
                                bootstrap.activation());
                if (outcome.state() != ProtosExecutionOutcome.State.COMPLETED) {
                    throw new IOException(
                            "external Package Tool planning preflight did not complete normally");
                }

                return outcome.value();
            } catch (IOException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new IOException("external Package Tool planning preflight failed", failure);
            } finally {
                process.requestTerminationForRuntime();
            }
        }
    }

    private static void bindProjectFilesystem(
            ProtosPrelude toolPrelude,
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            ProtosNioReadOnlyTreeFilesystemBackend backend)
            throws IOException {
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        toolPrelude.bytesPrototypeForRuntime(),
                        bootstrap.activation(),
                        backend);
        if (!(rawFilesystem instanceof ProtosFilesystemValue filesystem)) {
            throw new IOException(
                    "Package Tool project Filesystem has the wrong value family");
        }

        var context = bootstrap.activation().context();
        if (context.hasLocalSlot("projectTreeFilesystem")) {
            throw new IOException(
                    "Package Tool project Filesystem bootstrap slot already exists");
        }
        context.createLocalSlot("projectTreeFilesystem", filesystem);
    }

    private static void bindBorrowedExternalInputs(
            ProtosPrelude toolPrelude,
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            List<VerifiedExternalPackage> borrowed)
            throws IOException {
        ArrayList<Object> values = new ArrayList<>(borrowed.size());

        try {
            for (VerifiedExternalPackage input : borrowed) {
                ProtosObjectValue value =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                value.createLocalSlot("kind", new ProtosStringValue(input.kind()));
                value.createLocalSlot("packageId", new ProtosStringValue(input.packageId()));
                value.createLocalSlot("exact", new ProtosStringValue(input.exact()));
                value.createLocalSlot(
                        "contentMethod", new ProtosStringValue(input.contentMethod()));
                value.createLocalSlot(
                        "contentAlgorithm", new ProtosStringValue(input.contentAlgorithm()));
                value.createLocalSlot(
                        "contentHex", new ProtosStringValue(input.contentHex()));
                value.createLocalSlot(
                        "filesystem",
                        input.custody().materialize(bootstrap.activation()));
                value.freeze();
                values.add(value);
            }
        } catch (RuntimeException failure) {
            throw new IOException(
                    "cannot materialize verified external package custody for planning",
                    failure);
        }

        ProtosArrayValue array = toolPrelude.newFrozenArray(values);
        var context = bootstrap.activation().context();
        if (context.hasLocalSlot("verifiedExternalInputs")) {
            throw new IOException(
                    "Package Tool verified external input bootstrap slot already exists");
        }
        context.createLocalSlot("verifiedExternalInputs", array);
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
