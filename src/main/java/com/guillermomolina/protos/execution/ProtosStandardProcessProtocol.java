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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;
import java.util.Optional;

/**
 * Public standard Process capability selector bridge.
 *
 * <p>The Process prototype identity is source-backed and carries no authority. Java retains one
 * audited Closure-construction helper for the eight runtime/capability accessors because each
 * operation must validate an actual represented Process capability and synchronously observe the
 * already-established Process bootstrap state. No accessor performs host discovery or waiting.
 */
public final class ProtosStandardProcessProtocol {
    private enum Accessor {
        ARGS("args"),
        ENVIRONMENT("environment"),
        STDIN("stdin"),
        STDIN_ENCODING("stdinEncoding"),
        STDOUT("stdout"),
        STDOUT_ENCODING("stdoutEncoding"),
        STDERR("stderr"),
        STDERR_ENCODING("stderrEncoding");

        private final String selector;

        Accessor(String selector) {
            this.selector = selector;
        }
    }

    private ProtosStandardProcessProtocol() {}

    public static void install(ProtosObjectValue processPrototype) {
        Objects.requireNonNull(processPrototype, "processPrototype");
        for (Accessor accessor : Accessor.values()) {
            if (processPrototype.hasLocalSlot(accessor.selector)) {
                throw new IllegalStateException(
                        "standard Process already defines local " + accessor.selector);
            }
            processPrototype.createLocalSlot(
                    accessor.selector, accessorClosure(accessor));
        }
        processPrototype.freeze();
    }

    private static ProtosClosureValue accessorClosure(Accessor accessor) {
        return ProtosClosureValue.nativeClosure(
                (activation, supplied) -> {
                    if (!supplied.isEmpty()) {
                        throw error(activation);
                    }
                    ProtosProcessRuntime process = requireProcess(activation);
                    /*
                     * Linearize the synchronous accessor against Process termination. Every
                     * underlying bootstrap accessor is synchronized on the same runtime object,
                     * so the reentrant monitor makes the complete public observation happen
                     * wholly before or wholly after the RUNNING -> TERMINATING cutover.
                     */
                    synchronized (process) {
                        if (process.lifecycleState()
                                != ProtosProcessRuntime.LifecycleState.RUNNING) {
                            throw error(activation);
                        }
                        try {
                            return switch (accessor) {
                                case ARGS ->
                                        requireAvailable(
                                                process.argumentsSnapshotForRuntime(),
                                                activation);
                                case ENVIRONMENT ->
                                        requireAvailable(
                                                process.environmentSnapshotForRuntime(),
                                                activation);
                                case STDIN ->
                                        requireAvailable(
                                                process.stdinForRuntime(),
                                                activation);
                                case STDIN_ENCODING ->
                                        requireAvailableEncoding(
                                                process.stdinEncodingStateForRuntime(),
                                                process.stdinEncodingForRuntime(),
                                                activation);
                                case STDOUT ->
                                        requireAvailable(
                                                process.stdoutForRuntime(),
                                                activation);
                                case STDOUT_ENCODING ->
                                        requireAvailableEncoding(
                                                process.stdoutEncodingStateForRuntime(),
                                                process.stdoutEncodingForRuntime(),
                                                activation);
                                case STDERR ->
                                        requireAvailable(
                                                process.stderrForRuntime(),
                                                activation);
                                case STDERR_ENCODING ->
                                        requireAvailableEncoding(
                                                process.stderrEncodingStateForRuntime(),
                                                process.stderrEncodingForRuntime(),
                                                activation);
                            };
                        } catch (IllegalStateException invalidBootstrapState) {
                            throw error(activation);
                        }
                    }
                });
    }

    private static ProtosProcessRuntime requireProcess(
            ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosProcessCapabilityValue capability)) {
            throw error(activation);
        }
        return capability.processForRuntime();
    }

    private static Object requireAvailable(
            Optional<?> value, ProtosActivation activation) {
        return value.orElseThrow(() -> error(activation));
    }

    private static Object requireAvailableEncoding(
            ProtosProcessRuntime.StandardStreamEncodingState state,
            Optional<?> value,
            ProtosActivation activation) {
        if (state != ProtosProcessRuntime.StandardStreamEncodingState.AVAILABLE) {
            throw error(activation);
        }
        return requireAvailable(value, activation);
    }

    private static ProtosSignalException error(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
