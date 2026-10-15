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

import com.guillermomolina.protos.analysis.ProtosSourceLayoutView;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
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
 * Exact bundled TOOL010 bootstrap without project package-resolution authority.
 *
 * <p>This class owns only host execution mechanics: it selects the configured formatter
 * distribution root through {@link ProtosBundledToolModuleResolver}, creates one fresh tool
 * Process, and invokes fixed bundled-formatter operations. It owns no indentation, spacing,
 * wrapping, comment, line-width, or other formatter policy.
 *
 * <p>No project root, user module resolver, package graph, user Filesystem, or user source
 * execution authority is supplied to the bootstrap.
 */
public final class ProtosFormatterToolBootstrap {

    private static final String TOOL_NAME = "formatter";
    private static final String ENTRY_MODULE = "Main";
    private static final String EXPECTED_TOOL_ID = "TOOL010";
    private static final String SOURCE_LAYOUT_SLOT =
            "formatterSourceLayout";

    private static final String VERIFY_SOURCE =
            "formatter: import(\"self:Main\")\n"
                    + "formatter.toolId\n";

    private static final String ACCEPT_SOURCE_LAYOUT_SOURCE =
            "formatter: import(\"self:Main\")\n"
                    + "formatter.acceptSourceLayout(formatterSourceLayout)\n";

    private static final String FORMAT_STRUCTURAL_SOURCE =
            "formatter: import(\"self:Main\")\n"
                    + "formatter.formatStructural(formatterSourceLayout)\n";

    private ProtosFormatterToolBootstrap() {}

    public static void verifyAvailable(
            Path coreRoot,
            Path formatterToolRoot,
            ProtosModuleResolver standardLibraryResolver)
            throws IOException {
        try (ProtosPolyglotRuntimeHost runtimeHost =
                ProtosPolyglotRuntimeHost.open()) {
            verifyAvailable(
                    coreRoot,
                    formatterToolRoot,
                    standardLibraryResolver,
                    ignored -> {},
                    runtimeHost);
        }
    }

    static void verifyAvailable(
            Path coreRoot,
            Path formatterToolRoot,
            ProtosModuleResolver standardLibraryResolver,
            Consumer<ProtosProcessRuntime> processObserver,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        OperationResult result =
                execute(
                        coreRoot,
                        formatterToolRoot,
                        standardLibraryResolver,
                        VERIFY_SOURCE,
                        null,
                        processObserver,
                        runtimeHost);

        if (result.outcome().state()
                        != ProtosExecutionOutcome.State.COMPLETED
                || !(result.outcome().value()
                        instanceof ProtosStringValue toolId)
                || !EXPECTED_TOOL_ID.equals(toolId.value())) {
            throw new IOException(
                    "bundled TOOL010 formatter bootstrap identity mismatch");
        }
    }

    static void acceptSourceLayout(
            Path coreRoot,
            Path formatterToolRoot,
            ProtosModuleResolver standardLibraryResolver,
            ProtosSourceLayoutView sourceLayout,
            Consumer<ProtosProcessRuntime> processObserver,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        Objects.requireNonNull(sourceLayout, "sourceLayout");

        OperationResult result =
                execute(
                        coreRoot,
                        formatterToolRoot,
                        standardLibraryResolver,
                        ACCEPT_SOURCE_LAYOUT_SOURCE,
                        sourceLayout,
                        processObserver,
                        runtimeHost);

        if (result.outcome().state()
                != ProtosExecutionOutcome.State.COMPLETED) {
            throw new IOException(
                    "bundled TOOL010 source-layout operation did not complete"
                            + "; state="
                            + result.outcome().state()
                            + "; error="
                            + result.outcome().error()
                            + "; trace="
                            + result.outcome()
                                    .failureDiagnosticTrace()
                                    .orElse(null));
        }
    }

    static String formatStructural(
            Path coreRoot,
            Path formatterToolRoot,
            ProtosModuleResolver standardLibraryResolver,
            ProtosSourceLayoutView sourceLayout,
            Consumer<ProtosProcessRuntime> processObserver,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        Objects.requireNonNull(sourceLayout, "sourceLayout");

        OperationResult result =
                execute(
                        coreRoot,
                        formatterToolRoot,
                        standardLibraryResolver,
                        FORMAT_STRUCTURAL_SOURCE,
                        sourceLayout,
                        processObserver,
                        runtimeHost);

        if (result.outcome().state()
                        != ProtosExecutionOutcome.State.COMPLETED
                || !(result.outcome().value()
                        instanceof ProtosStringValue formatted)) {
            throw new IOException(
                    "bundled TOOL010 structural formatter did not return String"
                            + "; state="
                            + result.outcome().state()
                            + "; value="
                            + result.outcome().value()
                            + "; error="
                            + result.outcome().error()
                            + "; trace="
                            + result.outcome()
                                    .failureDiagnosticTrace()
                                    .orElse(null));
        }

        return formatted.value();
    }

    private static OperationResult execute(
            Path coreRoot,
            Path formatterToolRoot,
            ProtosModuleResolver standardLibraryResolver,
            String operationSource,
            ProtosSourceLayoutView sourceLayout,
            Consumer<ProtosProcessRuntime> processObserver,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        Objects.requireNonNull(coreRoot, "coreRoot");
        Objects.requireNonNull(
                formatterToolRoot,
                "formatterToolRoot");
        Objects.requireNonNull(
                standardLibraryResolver,
                "standardLibraryResolver");
        Objects.requireNonNull(
                operationSource,
                "operationSource");
        Objects.requireNonNull(
                processObserver,
                "processObserver");
        Objects.requireNonNull(
                runtimeHost,
                "runtimeHost");

        ProtosBundledToolModuleResolver toolResolver =
                new ProtosBundledToolModuleResolver(
                        TOOL_NAME,
                        formatterToolRoot,
                        formatterToolRoot.resolveSibling("shared"),
                        standardLibraryResolver);

        ProtosPrelude toolPrelude =
                new ProtosCoreBootstrap().bootstrap(
                        coreRoot,
                        toolResolver);

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
            ProtosPolyglotProcessContext processContext =
                    runtimeHost.hostProcess(
                            process,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());

            processObserver.accept(process);

            ProtosObjectValue bridgedSourceLayout = null;
            if (sourceLayout != null) {
                if (bootstrap.activation()
                        .context()
                        .hasLocalSlot(SOURCE_LAYOUT_SLOT)) {
                    throw new IOException(
                            "TOOL010 source-layout bootstrap slot already exists");
                }

                bridgedSourceLayout =
                        ProtosSourceLayoutToolBridge.project(
                                sourceLayout,
                                toolPrelude);

                bootstrap.activation()
                        .context()
                        .createLocalSlot(
                                SOURCE_LAYOUT_SLOT,
                                bridgedSourceLayout);
            }

            // Resolve the exact entry eagerly. This performs no project/package lookup.
            toolResolver.entryModule(ENTRY_MODULE);

            Source source =
                    Source.newBuilder(
                                    ProtosLanguage.ID,
                                    operationSource,
                                    "<formatter-tool-operation>")
                            .mimeType(ProtosLanguage.MIME_TYPE)
                            .build();

            ProtosExecutionOutcome outcome =
                    processContext.execute(
                            source,
                            bootstrap.activation());

            return new OperationResult(
                    outcome,
                    bridgedSourceLayout);
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException(
                    "bundled TOOL010 formatter operation failed",
                    failure);
        } finally {
            process.requestTerminationForRuntime();
        }
    }

    private static ProtosEnvironmentValue.NativeNameDomain
            emptyEnvironmentDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(
                    String left,
                    String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=")
                        && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(
                    String captured,
                    String query) {
                return captured.equals(query);
            }
        };
    }

    private record OperationResult(
            ProtosExecutionOutcome outcome,
            ProtosObjectValue bridgedSourceLayout) {
        private OperationResult {
            Objects.requireNonNull(outcome, "outcome");
        }
    }
}
