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
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.source.Source;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TOOL009 local bridge for one already-discovered logical Case.
 *
 * <p>The request contains only inert discovery data. Each attempt creates a fresh semantic Process,
 * re-materializes the source declaration before publishing any Case authority, validates the exact
 * discovery signature, resolves one local selector in Protos, and only then invokes the selected
 * Test value. Live Test/body values never cross a Process boundary.
 *
 * <p>TOOL009 Package Non-TOML Publication 2: when a request also carries a project-tree
 * CaseAuthority root and fixture identity, this bridge additionally provisions a fresh read-only
 * physical project-tree authority for the exact same Process, before the source declaration
 * executes, and installs it under the {@code projectTreeFilesystem} slot the declaration's module
 * scope closes over. Discovery, selection and the selected Test's {@code call()} then run in that
 * one Process, so the CaseAuthority provisioning and the suite-native selection authority are never
 * split across two Processes. {@link #resolveAuthorityRoot} and {@link #fixtureIdentity} own the
 * D133/D134 trusted-root confinement mechanics this bridge and {@link
 * ProtosTestLogicalCaseExecutionFacility} share; there is exactly one CaseAuthority model.
 *
 * <p>This bridge owns no CaseId encoding, scheduling, retry, fixtures, tags, result policy or
 * resource policy.
 */
final class ProtosTestLogicalCaseAttemptBridge {

    enum Phase {
        REMATERIALIZATION_ERROR,
        CASE_EXECUTION
    }

    record Request(
            Path sourcePath,
            String source,
            List<String> expectedSignature,
            String selector,
            Path projectTreeCasesRoot,
            String projectTreeFixtureIdentity) {

        Request {
            sourcePath =
                    Objects.requireNonNull(sourcePath, "sourcePath")
                            .toAbsolutePath()
                            .normalize();
            source = Objects.requireNonNull(source, "source");
            expectedSignature =
                    List.copyOf(
                            Objects.requireNonNull(
                                    expectedSignature,
                                    "expectedSignature"));
            selector = Objects.requireNonNull(selector, "selector");

            if (selector.isEmpty()) {
                throw new IllegalArgumentException(
                        "logical Case selector must not be empty");
            }

            for (String name : expectedSignature) {
                if (name == null || name.isEmpty()) {
                    throw new IllegalArgumentException(
                            "declaration signature contains an invalid selector");
                }
            }

            if ((projectTreeCasesRoot == null) != (projectTreeFixtureIdentity == null)) {
                throw new IllegalArgumentException(
                        "project-tree CaseAuthority root and fixture identity must be supplied"
                                + " together");
            }

            if (projectTreeCasesRoot != null) {
                projectTreeCasesRoot = projectTreeCasesRoot.toAbsolutePath().normalize();

                if (projectTreeFixtureIdentity.isEmpty()) {
                    throw new IllegalArgumentException(
                            "project-tree CaseAuthority fixture identity must not be empty");
                }
            }
        }

        Request(
                Path sourcePath,
                String source,
                List<String> expectedSignature,
                String selector) {
            this(sourcePath, source, expectedSignature, selector, null, null);
        }
    }

    record Result(
            Phase phase,
            ProtosCapturedProcessExecution.Result captured,
            ProtosPrelude sourcePrelude) {

        Result {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(captured, "captured");
            Objects.requireNonNull(sourcePrelude, "sourcePrelude");
        }

        ProtosExecutionOutcome outcome() {
            return captured.outcome();
        }

        byte[] stdout() {
            return captured.stdout();
        }

        byte[] stderr() {
            return captured.stderr();
        }
    }

    private final Path core;
    private final ProtosModuleResolver fallbackResolver;
    private final ProtosPolyglotRuntimeHost runtimeHost;

    ProtosTestLogicalCaseAttemptBridge(
            Path core,
            ProtosModuleResolver fallbackResolver,
            ProtosPolyglotRuntimeHost runtimeHost) {
        this.core =
                Objects.requireNonNull(core, "core")
                        .toAbsolutePath()
                        .normalize();
        this.fallbackResolver =
                Objects.requireNonNull(
                        fallbackResolver,
                        "fallbackResolver");
        this.runtimeHost =
                Objects.requireNonNull(
                        runtimeHost,
                        "runtimeHost");
    }

    Result execute(Request request) throws Exception {
        Objects.requireNonNull(request, "request");

        try (ProtosDirectFileModuleResolver resolver =
                new ProtosDirectFileModuleResolver(
                        request.sourcePath(),
                        request.source(),
                        fallbackResolver)) {

            ProtosPrelude prelude =
                    new ProtosCoreBootstrap().bootstrap(core, resolver);

            ProtosProcessRuntime process =
                    new ProtosProcessRuntime(
                            prelude.actorRefPrototypeForRuntime());

            var rootActor = process.rootActorForRuntime();

            ProtosActivation declarationActivation =
                    prelude.newModuleActivation(
                            rootActor.moduleState(),
                            null,
                            prelude.newExecutionContext(),
                            rootActor.executionDomain());

            ProtosNioReadOnlyTreeFilesystemBackend projectTreeBackend =
                    request.projectTreeCasesRoot() == null
                            ? null
                            : new ProtosNioReadOnlyTreeFilesystemBackend(
                                    resolveAuthorityRoot(
                                            request.projectTreeCasesRoot(),
                                            request.projectTreeFixtureIdentity()));

            try {
                if (projectTreeBackend != null
                        && !projectTreeBackend.secureConfinementAvailable()) {
                    throw new IOException(
                            "secure project-tree CaseAuthority confinement is unavailable");
                }

                return executeWithinProcess(
                        request,
                        prelude,
                        resolver,
                        process,
                        rootActor,
                        declarationActivation,
                        projectTreeBackend);
            } finally {
                if (projectTreeBackend != null) {
                    projectTreeBackend.close();
                }
            }
        }
    }

    private Result executeWithinProcess(
            Request request,
            ProtosPrelude prelude,
            ProtosDirectFileModuleResolver resolver,
            ProtosProcessRuntime process,
            com.guillermomolina.protos.runtime.ProtosActor rootActor,
            ProtosActivation declarationActivation,
            ProtosNioReadOnlyTreeFilesystemBackend projectTreeBackend)
            throws Exception {
        ByteArrayOutputStream stdout =
                new ByteArrayOutputStream();
        ByteArrayOutputStream stderr =
                new ByteArrayOutputStream();

        ProtosPolyglotProcessContext processContext =
                runtimeHost.hostProcess(
                        process,
                        InputStream.nullInputStream(),
                        stdout,
                        stderr);

        try {
            ProtosExecutionOutcome declaration =
                    ProtosCanonicalInitialModuleExecution.execute(
                            prelude,
                            resolver,
                            resolver.entryModule(),
                            declarationActivation);

            if (declaration.state()
                    != ProtosExecutionOutcome.State.COMPLETED) {
                return result(
                        Phase.REMATERIALIZATION_ERROR,
                        declaration,
                        stdout,
                        stderr,
                        prelude);
            }

            ProtosObjectValue module =
                    rootActor.moduleState()
                            .lookup(resolver.entryModule())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "rematerialized suite module is not cached"))
                            .instance();

            if (projectTreeBackend != null) {
                installProjectTreeFilesystem(
                        module,
                        prelude,
                        declarationActivation,
                        projectTreeBackend);
            }

            ProtosActivation selectionActivation =
                    selectionActivation(
                            prelude,
                            rootActor,
                            module,
                            request);

            try {
                processContext.evaluatePersistent(
                        selectionSource(),
                        selectionActivation);
            } catch (ProtosSignalException mismatch) {
                return result(
                        Phase.REMATERIALIZATION_ERROR,
                        ProtosExecutionOutcome.failed(
                                mismatch.error()),
                        stdout,
                        stderr,
                        prelude);
            }

            ProtosExecutionOutcome body =
                    processContext.execute(
                            invocationSource(),
                            selectionActivation);

            return result(
                    Phase.CASE_EXECUTION,
                    body,
                    stdout,
                    stderr,
                    prelude);
        } finally {
            process.requestTerminationForRuntime();
            process.awaitTerminationForRuntime();
            processContext.awaitTerminalDispositionForRuntime();
        }
    }

    /**
     * Installs the project-tree CaseAuthority Filesystem onto the already-declared module
     * instance, after {@link ProtosCanonicalInitialModuleExecution} has cached and executed it.
     *
     * <p>{@code ProtosCanonicalInitialModuleExecution.execute} caches the declaration
     * activation's own context as the module's instance before running the module's top-level
     * declarations ("the normative cache-before-execute point"); a pre-existing slot on that
     * object at that point is host contamination of the module's own declaration surface, not an
     * ambient capability. Installing the capability afterward, directly on the resulting module
     * instance, keeps declaration pristine while remaining visible to any Test Closure declared
     * during that same declaration: a Closure captures a live reference to its enclosing module
     * instance, not a snapshot, so a slot added immediately after declaration is still visible
     * when the selected Test later calls {@code call()}.
     */
    private static void installProjectTreeFilesystem(
            ProtosObjectValue module,
            ProtosPrelude prelude,
            ProtosActivation declarationActivation,
            ProtosNioReadOnlyTreeFilesystemBackend projectTreeBackend) {
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(),
                        declarationActivation,
                        projectTreeBackend);

        if (!(rawFilesystem instanceof ProtosFilesystemValue filesystem)) {
            throw new IllegalStateException(
                    "project-tree CaseAuthority Filesystem has the wrong value family");
        }

        if (module.hasLocalSlot("projectTreeFilesystem")) {
            throw new IllegalStateException(
                    "project-tree CaseAuthority slot already exists");
        }

        module.createLocalSlot("projectTreeFilesystem", filesystem);
    }

    private static Result result(
            Phase phase,
            ProtosExecutionOutcome outcome,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream stderr,
            ProtosPrelude sourcePrelude) {
        return new Result(
                phase,
                new ProtosCapturedProcessExecution.Result(
                        outcome,
                        stdout.toByteArray(),
                        stderr.toByteArray()),
                sourcePrelude);
    }

    private static ProtosActivation selectionActivation(
            ProtosPrelude prelude,
            com.guillermomolina.protos.runtime.ProtosActor rootActor,
            ProtosObjectValue module,
            Request request) {

        ProtosObjectValue context =
                prelude.newExecutionContext();

        ArrayList<Object> selectors =
                new ArrayList<>(
                        request.expectedSignature().size());

        for (String selector : request.expectedSignature()) {
            selectors.add(new ProtosStringValue(selector));
        }

        context.createLocalSlot(
                "discoverySubject",
                module);
        context.createLocalSlot(
                "expectedSignature",
                prelude.newFrozenArray(selectors));
        context.createLocalSlot(
                "selectedSelector",
                new ProtosStringValue(request.selector()));

        return prelude.newModuleActivation(
                rootActor.moduleState(),
                null,
                context,
                rootActor.executionDomain());
    }

    /**
     * D133 project-tree CaseAuthority descriptor validation, shared with {@link
     * ProtosTestLogicalCaseExecutionFacility} so the async facility decodes a
     * sourceAssociation's optional third element with the exact same descriptor
     * shape this bridge provisions against.
     */
    static String fixtureIdentity(
            ProtosArrayValue descriptor,
            ProtosActivation caller) {
        if (!descriptor.indexedSize().equals(BigInteger.valueOf(4))) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        Object kind = descriptor.indexedAt(BigInteger.ZERO);
        Object fixture = descriptor.indexedAt(BigInteger.ONE);
        Object isolation = descriptor.indexedAt(BigInteger.valueOf(2));
        Object lifecycle = descriptor.indexedAt(BigInteger.valueOf(3));

        if (!(kind instanceof ProtosStringValue kindValue)
                || !(fixture instanceof ProtosStringValue fixtureValue)
                || !(isolation instanceof ProtosStringValue isolationValue)
                || !(lifecycle instanceof ProtosStringValue lifecycleValue)
                || !kindValue.value().equals("project-tree")
                || !isolationValue.value().equals("case")
                || !lifecycleValue.value().equals("case")
                || fixtureValue.value().isEmpty()) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        return fixtureValue.value();
    }

    /**
     * D133/D134 trusted-root confinement: resolves one fixture's physical authority
     * root under {@code casesRoot}, rejecting an absolute or escaping fixture
     * identity before any Filesystem backend is opened against it.
     */
    static Path resolveAuthorityRoot(
            Path casesRoot,
            String fixtureIdentity) {
        Path logical = Path.of(fixtureIdentity);

        if (logical.isAbsolute()) {
            throw new IllegalArgumentException(
                    "CaseAuthority fixture identity must be relative");
        }

        Path resolved =
                casesRoot.toAbsolutePath().normalize().resolve(logical).normalize();

        if (!resolved.startsWith(casesRoot.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    "CaseAuthority fixture identity escapes cases root");
        }

        return resolved;
    }

    private static Source selectionSource() {
        return source(
                "<tool009-logical-case-selection>",
                """
                Discovery: import("self:Discovery")

                selected:
                    Discovery.resolveSelectedTest(
                        discoverySubject,
                        expectedSignature,
                        selectedSelector
                    )

                selected
                """);
    }

    private static Source invocationSource() {
        return source(
                "<tool009-logical-case-body>",
                """
                selected()
                """);
    }

    private static Source source(
            String name,
            String characters) {
        return Source.newBuilder(
                        ProtosLanguage.ID,
                        characters,
                        name)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }
}
