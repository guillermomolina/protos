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
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import java.util.List;
import java.util.Objects;

/**
 * Host-neutral assembly of one standalone Process bootstrap before entry-source execution.
 *
 * <p>This class does not discover host facilities. The launcher supplies already-captured
 * application arguments and Environment entries/name-domain, independently optional standard byte
 * backends with their host-selected Encoding descriptors, and an optional already-provisioned
 * default Filesystem capability. The resulting non-importable entry activation receives the exact
 * E2 bootstrap-local authority model before its first source expression.
 */
public final class ProtosStandaloneProcessBootstrap {
    private ProtosStandaloneProcessBootstrap() {}

    public record Result(ProtosProcessRuntime process, ProtosActivation activation) {
        public Result {
            Objects.requireNonNull(process, "process");
            Objects.requireNonNull(activation, "activation");
        }
    }

    public static Result create(
            ProtosPrelude prelude,
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
        Objects.requireNonNull(prelude, "prelude");
        Objects.requireNonNull(applicationArguments, "applicationArguments");
        Objects.requireNonNull(environmentNameDomain, "environmentNameDomain");
        Objects.requireNonNull(environmentEntries, "environmentEntries");

        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        prelude.actorRefPrototypeForRuntime(),
                        defaultFilesystem);

        process.establishArgumentsForRuntime(
                ProtosStandardProcessArgumentsProtocol.createPrototype(),
                List.copyOf(applicationArguments));
        process.establishEnvironmentForRuntime(
                ProtosStandardEnvironmentProtocol.createPrototype(),
                environmentNameDomain,
                List.copyOf(environmentEntries));
        process.establishStandardStreamsForRuntime(
                ProtosStandardProcessStreamProtocol.createReadablePrototype(),
                ProtosStandardProcessStreamProtocol.createWritablePrototype(),
                prelude.bytesPrototypeForRuntime(),
                stdinBackend,
                stdoutBackend,
                stderrBackend);
        process.establishStandardStreamEncodingsForRuntime(
                stdinEncoding,
                stdoutEncoding,
                stderrEncoding);

        return new Result(
                process,
                ProtosActorBootstrap.newStandaloneInitialActivation(
                        process, prelude));
    }
}
