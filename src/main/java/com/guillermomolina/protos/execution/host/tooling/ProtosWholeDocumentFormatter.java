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

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.analysis.ProtosSourceLayoutView;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisCore;
import com.guillermomolina.protos.analysis.ProtosStaticParseResult;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Editor-neutral whole-document formatting authority for TOOL010.
 *
 * <p>Invalid or incomplete source fails closed before formatter bootstrap:
 * the exact original snapshot text is returned unchanged with an inert
 * failure reason. Successfully parsed source is formatted by the exact
 * bundled TOOL010 policy through {@link ProtosFormatterToolBootstrap}.</p>
 */
public final class ProtosWholeDocumentFormatter {

    public enum Status {
        SUCCESS,
        FAILURE
    }

    public record Result(
            Status status,
            String source,
            Optional<String> reason) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(reason, "reason");

            if (status == Status.SUCCESS && reason.isPresent()) {
                throw new IllegalArgumentException(
                        "successful formatting cannot carry a failure reason");
            }

            if (status == Status.FAILURE && reason.isEmpty()) {
                throw new IllegalArgumentException(
                        "failed formatting requires an inert reason");
            }
        }

        static Result success(String source) {
            return new Result(
                    Status.SUCCESS,
                    source,
                    Optional.empty());
        }

        static Result failure(
                String originalSource,
                String reason) {
            return new Result(
                    Status.FAILURE,
                    originalSource,
                    Optional.of(reason));
        }
    }

    private ProtosWholeDocumentFormatter() {}

    public static Result format(
            Path coreRoot,
            Path formatterToolRoot,
            ProtosModuleResolver standardLibraryResolver,
            ProtosDocumentSnapshot snapshot,
            Consumer<ProtosProcessRuntime> processObserver,
            ProtosPolyglotRuntimeHost runtimeHost)
            throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");

        ProtosStaticAnalysisCore analysis =
                new ProtosStaticAnalysisCore();

        ProtosStaticParseResult parsed =
                analysis.parse(snapshot);

        if (parsed instanceof ProtosStaticParseResult.Failed failed) {
            return Result.failure(
                    snapshot.characters(),
                    failed.message());
        }

        ProtosSourceLayoutView sourceLayout =
                analysis.sourceLayout(snapshot);

        return Result.success(
                ProtosFormatterToolBootstrap.formatStructural(
                        coreRoot,
                        formatterToolRoot,
                        standardLibraryResolver,
                        sourceLayout,
                        processObserver,
                        runtimeHost));
    }
}
