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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFileFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemNamespaceMutationFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemOpenFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemOpenOptions;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import java.util.Objects;

/**
 * I016-D1 / I021-A host/resource bridge for one provisioned Filesystem authority capability.
 *
 * <p>The returned object is deliberately not installed in the Core prelude and there is no Protos
 * constructor for it. Process/bootstrap policy may provision zero or more capabilities later under
 * I017.
 *
 * <p>The backend is bound to one authority capability. For every accepted open it owns complete
 * race-safe path resolution and confinement, including parent traversal, symlink/reparse/alias/mount
 * indirection and concurrent namespace changes. If confinement inside that capability cannot be
 * established, the backend must fail instead of using uncertain authority. It also owns race-free
 * existing/create/createNew selection, empty-file creation, failure-atomic truncate-on-open, and
 * acquisition of a stable selected resource that later File operations do not re-resolve by Path.
 *
 * <p>Any create/truncate effect crosses the portable open commitment handshake immediately before
 * the effect becomes observable. I021 namespace replacement/removal instead uses the dedicated
 * per-operation atomic effect/commit cutover so cancellation cannot split a successful namespace
 * transition from its Future outcome. File capability descriptors must exactly match the captured
 * read/write/append authority; optional seek/size/truncate/sync surfaces may be advertised only when
 * the selected backend resource implements their complete standard contracts.
 */
public final class ProtosStandardFilesystemProtocol {
    private ProtosStandardFilesystemProtocol() {}

    public interface OpenCompletion {
        boolean commitPortableEffect();

        void succeeded(
                ProtosFileFlow.Resource resource,
                ProtosFileFlow.Capabilities capabilities,
                Runnable releaseIfUntransferred);

        void failed();
    }

    @FunctionalInterface
    public interface Backend {
        ProtosFilesystemOpenFlow.Cancellation open(
                ProtosPathValue path,
                ProtosFilesystemOpenOptions options,
                OpenCompletion completion);

        default ProtosFilesystemNamespaceMutationFlow.Cancellation replace(
                ProtosPathValue sourcePath,
                ProtosPathValue targetPath,
                ProtosFilesystemNamespaceMutationFlow.MutationCompletion completion) {
            completion.failed();
            return () -> {};
        }

        default ProtosFilesystemNamespaceMutationFlow.Cancellation remove(
                ProtosPathValue path,
                ProtosFilesystemNamespaceMutationFlow.MutationCompletion completion) {
            completion.failed();
            return () -> {};
        }
    }

    private enum Operation {
        OPEN,
        REPLACE,
        REMOVE
    }

    public static ProtosObjectValue createCapability(
            ProtosObjectValue bytesPrototype,
            ProtosActivation constructionActivation,
            Backend backend) {
        Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        Objects.requireNonNull(constructionActivation, "constructionActivation");
        Objects.requireNonNull(backend, "backend");

        ProtosFilesystemValue filesystem = new ProtosFilesystemValue();

        ProtosFilesystemOpenFlow flow =
                new ProtosFilesystemOpenFlow(
                        filesystem,
                        constructionActivation,
                        (path, options, completion) ->
                                backend.open(
                                        path,
                                        options,
                                        new OpenCompletion() {
                                            @Override
                                            public boolean commitPortableEffect() {
                                                return completion.commitPortableEffect();
                                            }

                                            @Override
                                            public void succeeded(
                                                    ProtosFileFlow.Resource resource,
                                                    ProtosFileFlow.Capabilities capabilities,
                                                    Runnable releaseIfUntransferred) {
                                                materialize(
                                                        bytesPrototype,
                                                        constructionActivation,
                                                        options,
                                                        resource,
                                                        capabilities,
                                                        releaseIfUntransferred,
                                                        completion);
                                            }

                                            @Override
                                            public void failed() {
                                                completion.failed();
                                            }
                                        }));

        ProtosFilesystemNamespaceMutationFlow namespaceMutationFlow =
                new ProtosFilesystemNamespaceMutationFlow(
                        filesystem,
                        constructionActivation,
                        backend::replace,
                        backend::remove);

        filesystem.createLocalSlot(
                "open", operationClosure(filesystem, flow, namespaceMutationFlow, Operation.OPEN));
        filesystem.createLocalSlot(
                "replace",
                operationClosure(filesystem, flow, namespaceMutationFlow, Operation.REPLACE));
        filesystem.createLocalSlot(
                "remove",
                operationClosure(filesystem, flow, namespaceMutationFlow, Operation.REMOVE));
        return filesystem;
    }

    private static ProtosClosureValue operationClosure(
            ProtosObjectValue filesystem,
            ProtosFilesystemOpenFlow openFlow,
            ProtosFilesystemNamespaceMutationFlow namespaceMutationFlow,
            Operation operation) {
        return ProtosClosureValue.nativeClosure(
                (activation, arguments) -> {
                    if (activation.receiver() != filesystem) {
                        return invalid(activation);
                    }
                    return switch (operation) {
                        case OPEN -> {
                            if (arguments.size() == 1) {
                                yield openFlow.open(activation, arguments.get(0));
                            }
                            if (arguments.size() == 2) {
                                yield openFlow.open(
                                        activation, arguments.get(0), arguments.get(1));
                            }
                            yield invalid(activation);
                        }
                        case REPLACE ->
                                arguments.size() == 2
                                        ? namespaceMutationFlow.replace(
                                                activation,
                                                arguments.get(0),
                                                arguments.get(1))
                                        : invalid(activation);
                        case REMOVE ->
                                arguments.size() == 1
                                        ? namespaceMutationFlow.remove(
                                                activation, arguments.get(0))
                                        : invalid(activation);
                    };
                });
    }

    private static void materialize(
            ProtosObjectValue bytesPrototype,
            ProtosActivation constructionActivation,
            ProtosFilesystemOpenOptions options,
            ProtosFileFlow.Resource resource,
            ProtosFileFlow.Capabilities capabilities,
            Runnable releaseIfUntransferred,
            ProtosFilesystemOpenFlow.OpenCompletion completion) {
        try {
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(capabilities, "capabilities");
            Objects.requireNonNull(releaseIfUntransferred, "releaseIfUntransferred");

            boolean append =
                    options.placement() == ProtosFilesystemOpenOptions.Placement.APPEND;
            if (capabilities.readable() != options.readAccess()
                    || capabilities.writable() != options.writeAccess()
                    || capabilities.append() != append) {
                throw new IllegalArgumentException(
                        "backend File capability shape does not match captured open authority");
            }

            ProtosObjectValue file =
                    append
                            ? ProtosStandardFileProtocol.createAppend(
                                    bytesPrototype,
                                    constructionActivation,
                                    resource,
                                    capabilities)
                            : ProtosStandardFileProtocol.createPositioned(
                                    bytesPrototype,
                                    constructionActivation,
                                    resource,
                                    capabilities);
            completion.succeeded(file, releaseIfUntransferred);
        } catch (RuntimeException invalidBackendDescriptor) {
            releaseSafely(releaseIfUntransferred);
            completion.failed();
        }
    }

    private static void releaseSafely(Runnable release) {
        if (release == null) {
            return;
        }
        try {
            release.run();
        } catch (RuntimeException ignored) {
            // Invalid backend descriptors are never exposed as standard Files.
        }
    }

    private static ProtosFutureValue invalid(ProtosActivation activation) {
        ProtosFutureValue future =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain());
        future.fail(
                ProtosCoreErrors.newOccurrence(
                        activation, ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT));
        return future;
    }
}
