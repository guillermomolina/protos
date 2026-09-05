/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFileFlow;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.Objects;

/**
 * I016-C installer for one already-acquired positioned or append File capability.
 *
 * <p>Only capabilities that the immutable File capability descriptor promises are installed. A raw
 * File deliberately does not gain Flushable merely because byte-I/O adapters elsewhere expose it.
 */
public final class ProtosStandardFileProtocol {
    private ProtosStandardFileProtocol() {}

    public static ProtosObjectValue createPositioned(
            ProtosObjectValue bytesPrototype,
            ProtosActivation constructionActivation,
            ProtosFileFlow.Resource resource,
            ProtosFileFlow.Capabilities capabilities) {
        if (Objects.requireNonNull(capabilities, "capabilities").append()) {
            throw new IllegalArgumentException(
                    "createPositioned requires non-append File capabilities");
        }
        return create(bytesPrototype, constructionActivation, resource, capabilities);
    }

    public static ProtosObjectValue createAppend(
            ProtosObjectValue bytesPrototype,
            ProtosActivation constructionActivation,
            ProtosFileFlow.Resource resource,
            ProtosFileFlow.Capabilities capabilities) {
        if (!Objects.requireNonNull(capabilities, "capabilities").append()) {
            throw new IllegalArgumentException(
                    "createAppend requires append-mode File capabilities");
        }
        return create(bytesPrototype, constructionActivation, resource, capabilities);
    }

    private static ProtosObjectValue create(
            ProtosObjectValue bytesPrototype,
            ProtosActivation constructionActivation,
            ProtosFileFlow.Resource resource,
            ProtosFileFlow.Capabilities capabilities) {
        Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        Objects.requireNonNull(constructionActivation, "constructionActivation");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(capabilities, "capabilities");

        ProtosObjectValue file = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosFileFlow flow =
                new ProtosFileFlow(
                        file, bytesPrototype, constructionActivation, resource, capabilities);

        if (capabilities.readable()) {
            file.createLocalSlot(
                    "read",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.size() == 1 && activation.receiver() == file
                                            ? flow.read(activation, arguments.get(0))
                                            : invalid(activation)));
        }
        if (capabilities.writable()) {
            file.createLocalSlot(
                    "write",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.size() == 1 && activation.receiver() == file
                                            ? flow.write(activation, arguments.get(0))
                                            : invalid(activation)));
        }
        if (capabilities.seekable()) {
            file.createLocalSlot(
                    "position",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.isEmpty() && activation.receiver() == file
                                            ? flow.position(activation)
                                            : invalid(activation)));
            file.createLocalSlot(
                    "seek",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.size() == 1 && activation.receiver() == file
                                            ? flow.seek(activation, arguments.get(0))
                                            : invalid(activation)));
            file.createLocalSlot(
                    "seekBy",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.size() == 1 && activation.receiver() == file
                                            ? flow.seekBy(activation, arguments.get(0))
                                            : invalid(activation)));
            file.createLocalSlot(
                    "seekToEnd",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.isEmpty() && activation.receiver() == file
                                            ? flow.seekToEnd(activation)
                                            : invalid(activation)));
        }
        if (capabilities.sized()) {
            file.createLocalSlot(
                    "size",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.isEmpty() && activation.receiver() == file
                                            ? flow.size(activation)
                                            : invalid(activation)));
        }
        if (capabilities.truncatable()) {
            file.createLocalSlot(
                    "truncate",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.size() == 1 && activation.receiver() == file
                                            ? flow.truncate(activation, arguments.get(0))
                                            : invalid(activation)));
        }
        if (capabilities.syncable()) {
            file.createLocalSlot(
                    "sync",
                    ProtosClosureValue.nativeClosure(
                            (activation, arguments) ->
                                    arguments.isEmpty() && activation.receiver() == file
                                            ? flow.sync(activation)
                                            : invalid(activation)));
        }

        file.createLocalSlot(
                "close",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) ->
                                arguments.isEmpty() && activation.receiver() == file
                                        ? flow.close(activation)
                                        : invalid(activation)));
        return file;
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
