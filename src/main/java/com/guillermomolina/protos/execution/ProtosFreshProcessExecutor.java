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

import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * Local host mechanism for one exact Source in one fresh semantic Protos Process.
 *
 * <p>The request is already past module/discovery policy: the caller supplies the exact Source
 * and a Prelude whose import facility was created with the already-selected module resolver. The
 * Source is parsed only after the fresh Process is bound to its own Polyglot Context, so executable
 * AST ownership cannot leak from the caller's Context/sharing layer.
 *
 * <p>Every invocation creates and terminates a new {@code ProtosProcessRuntime}. A caller may
 * supply an explicit {@link ProtosPolyglotRuntimeHost} so many fresh Processes reuse one Engine
 * while retaining one distinct Context per Process. The no-host overload owns one temporary host.
 * The returned outcome intentionally contains no Process/Actor/task handle.
 */
public final class ProtosFreshProcessExecutor {
    private ProtosFreshProcessExecutor() {}

    public record Request(
            ProtosPrelude prelude,
            Source entry,
            List<String> applicationArguments,
            ProtosEnvironmentValue.NativeNameDomain environmentNameDomain,
            List<ProtosEnvironmentValue.NativeEntry> environmentEntries,
            ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
            ProtosProcessStandardStreamBinding.WritableBackend stderrBackend,
            ProtosEncodingValue stdinEncoding,
            ProtosEncodingValue stdoutEncoding,
            ProtosEncodingValue stderrEncoding,
            ProtosFilesystemValue defaultFilesystem) {

        public Request {
            Objects.requireNonNull(prelude, "prelude");
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(applicationArguments, "applicationArguments");
            Objects.requireNonNull(environmentNameDomain, "environmentNameDomain");
            Objects.requireNonNull(environmentEntries, "environmentEntries");
            applicationArguments = List.copyOf(applicationArguments);
            environmentEntries = List.copyOf(environmentEntries);
        }
    }

    public static ProtosExecutionOutcome execute(Request request) {
        Objects.requireNonNull(request, "request");
        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open()) {
            return execute(request, runtimeHost);
        }
    }

    public static ProtosExecutionOutcome execute(
            Request request, ProtosPolyglotRuntimeHost runtimeHost) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(runtimeHost, "runtimeHost");

        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        request.prelude(),
                        request.applicationArguments(),
                        request.environmentNameDomain(),
                        request.environmentEntries(),
                        request.stdinBackend(),
                        request.stdoutBackend(),
                        request.stderrBackend(),
                        request.stdinEncoding(),
                        request.stdoutEncoding(),
                        request.stderrEncoding(),
                        request.defaultFilesystem());
        ProtosProcessRuntime process = bootstrap.process();

        try {
            ProtosPolyglotProcessContext processContext =
                    runtimeHost.hostProcess(
                            process,
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
            return processContext.execute(request.entry(), bootstrap.activation());
        } finally {
            process.requestTerminationForRuntime();
        }
    }
}
