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
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProtosCoreBootstrap {
    private final ProtosSourceFileLoader sourceLoader;

    public ProtosCoreBootstrap() {
        this(new ProtosSourceFileLoader());
    }

    ProtosCoreBootstrap(ProtosSourceFileLoader sourceLoader) {
        this.sourceLoader =
                Objects.requireNonNull(sourceLoader, "sourceLoader");
    }

    public ProtosPrelude bootstrap(Path coreDirectory) throws IOException {
        return bootstrap(coreDirectory, ProtosModuleResolver.rejecting());
    }

    public ProtosPrelude bootstrap(Path coreDirectory, ProtosModuleResolver moduleResolver) throws IOException {
        Objects.requireNonNull(coreDirectory, "coreDirectory");
        Objects.requireNonNull(moduleResolver, "moduleResolver");

        publishStandardRoot(coreDirectory);

        ProtosObjectValue bootstrapContext =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation bootstrapActivation =
                new ProtosActivation(
                        bootstrapContext,
                        List.of(),
                        bootstrapContext);

        sourceLoader
                .load(coreDirectory.resolve("Context.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Number.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Integer.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Float.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("UInt8.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Int8.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("UInt16.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Int16.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("UInt32.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Int32.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("UInt64.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Int64.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Error.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("InvalidReturn.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("error_taxonomy.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Array.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("String.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Encoding.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Map.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("IdentityMap.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Path.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("IpAddress.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("IpEndpoint.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Network.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("TcpConnection.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("TcpListener.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Future.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Actor.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Process.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("TextReader.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("TextWriter.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("BufferedReader.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("BufferedWriter.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("import.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("Bytes.protos"))
                .call(bootstrapActivation);

        Object contextBinding =
                bootstrapContext
                        .readLocalSlot("Context")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core bootstrap did not define Context"));
        if (!(contextBinding instanceof ProtosObjectValue contextPrototype)) {
            throw new IllegalStateException(
                    "Core Context binding is not an ordinary object");
        }
        if (contextPrototype.parent().orElse(null)
                != ProtosObjectValue.rootObject()) {
            throw new IllegalStateException(
                    "Core Context prototype must delegate directly to Object");
        }

        ProtosObjectValue numberPrototype =
                requirePrototype(
                        bootstrapContext, "Number", ProtosObjectValue.rootObject());
        ProtosObjectValue integerPrototype =
                requirePrototype(bootstrapContext, "Integer", numberPrototype);
        ProtosObjectValue floatPrototype =
                requirePrototype(bootstrapContext, "Float", numberPrototype);
        ProtosObjectValue uInt8Prototype =
                requirePrototype(bootstrapContext, "UInt8", integerPrototype);
        ProtosObjectValue int8Prototype =
                requirePrototype(bootstrapContext, "Int8", integerPrototype);
        ProtosObjectValue uInt16Prototype =
                requirePrototype(bootstrapContext, "UInt16", integerPrototype);
        ProtosObjectValue int16Prototype =
                requirePrototype(bootstrapContext, "Int16", integerPrototype);
        ProtosObjectValue uInt32Prototype =
                requirePrototype(bootstrapContext, "UInt32", integerPrototype);
        ProtosObjectValue int32Prototype =
                requirePrototype(bootstrapContext, "Int32", integerPrototype);
        ProtosObjectValue uInt64Prototype =
                requirePrototype(bootstrapContext, "UInt64", integerPrototype);
        ProtosObjectValue int64Prototype =
                requirePrototype(bootstrapContext, "Int64", integerPrototype);
        ProtosStandardNumberEqualityProtocol.install(numberPrototype);
        ProtosStandardNumberOrderingProtocol.install(numberPrototype);
        ProtosStandardHashSupport.installNumberHash(numberPrototype);
        ProtosStandardIntegerProtocol.install(integerPrototype);
        ProtosStandardFloatProtocol.install(floatPrototype);
        ProtosStandardFixedIntegerProtocol.install(
                uInt8Prototype, ProtosFixedIntegerValue.Family.UINT8);
        ProtosStandardFixedIntegerProtocol.install(
                int8Prototype, ProtosFixedIntegerValue.Family.INT8);
        ProtosStandardFixedIntegerProtocol.install(
                uInt16Prototype, ProtosFixedIntegerValue.Family.UINT16);
        ProtosStandardFixedIntegerProtocol.install(
                int16Prototype, ProtosFixedIntegerValue.Family.INT16);
        ProtosStandardFixedIntegerProtocol.install(
                uInt32Prototype, ProtosFixedIntegerValue.Family.UINT32);
        ProtosStandardFixedIntegerProtocol.install(
                int32Prototype, ProtosFixedIntegerValue.Family.INT32);
        ProtosStandardFixedIntegerProtocol.install(
                uInt64Prototype, ProtosFixedIntegerValue.Family.UINT64);
        ProtosStandardFixedIntegerProtocol.install(
                int64Prototype, ProtosFixedIntegerValue.Family.INT64);
        ProtosStandardNumericConversionProtocol.install(integerPrototype, floatPrototype, uInt8Prototype, int8Prototype, uInt16Prototype, int16Prototype, uInt32Prototype, int32Prototype, uInt64Prototype, int64Prototype);

        Object errorBinding =
                bootstrapContext
                        .readLocalSlot("Error")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core bootstrap did not define Error"));
        if (!(errorBinding instanceof ProtosObjectValue errorPrototype)) {
            throw new IllegalStateException(
                    "Core Error binding is not an ordinary object");
        }
        if (errorPrototype.parent().orElse(null)
                != ProtosObjectValue.rootObject()) {
            throw new IllegalStateException(
                    "Core Error prototype must delegate directly to Object");
        }
        ProtosStandardErrorProtocol.install(errorPrototype);

        Object invalidReturnBinding =
                bootstrapContext
                        .readLocalSlot("InvalidReturn")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core bootstrap did not define InvalidReturn"));
        if (!(invalidReturnBinding
                instanceof ProtosObjectValue invalidReturnPrototype)) {
            throw new IllegalStateException(
                    "Core InvalidReturn binding is not an ordinary object");
        }
        if (invalidReturnPrototype.parent().orElse(null)
                != errorPrototype) {
            throw new IllegalStateException(
                    "Core InvalidReturn prototype must delegate directly to Error");
        }
        ProtosCoreErrorTaxonomy.validate(bootstrapContext, errorPrototype);

        Object arrayBinding =
                bootstrapContext
                        .readLocalSlot("Array")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core bootstrap did not define Array"));
        if (!(arrayBinding instanceof ProtosObjectValue arrayPrototype)) {
            throw new IllegalStateException(
                    "Core Array binding is not an ordinary object");
        }
        if (arrayPrototype.parent().orElse(null)
                != ProtosObjectValue.rootObject()) {
            throw new IllegalStateException(
                    "Core Array prototype must delegate directly to Object");
        }
        ProtosStandardArrayProtocol.install(arrayPrototype);
        ProtosParallelRuntime.installArrayParallel(arrayPrototype);

        ProtosObjectValue stringPrototype =
                requirePrototype(
                        bootstrapContext, "String", ProtosObjectValue.rootObject());
        ProtosStandardStringProtocol.install(stringPrototype);
        ProtosStandardHashSupport.installStringHash(stringPrototype);
        ProtosObjectValue mapPrototype = requirePrototype(bootstrapContext, "Map", ProtosObjectValue.rootObject());
        ProtosStandardMapProtocol.install(mapPrototype);
        ProtosObjectValue identityMapPrototype = requirePrototype(bootstrapContext, "IdentityMap", ProtosObjectValue.rootObject());
        ProtosStandardIdentityMapProtocol.install(identityMapPrototype);
        ProtosObjectValue pathPrototype = requirePrototype(bootstrapContext, "Path", ProtosObjectValue.rootObject());
        ProtosStandardPathProtocol.install(pathPrototype);
        ProtosObjectValue ipAddressPrototype =
                requirePrototype(
                        bootstrapContext, "IpAddress", ProtosObjectValue.rootObject());
        ProtosStandardIpAddressProtocol.install(ipAddressPrototype);
        ProtosObjectValue ipEndpointPrototype =
                requirePrototype(
                        bootstrapContext, "IpEndpoint", ProtosObjectValue.rootObject());
        ProtosStandardIpEndpointProtocol.install(ipEndpointPrototype, ipAddressPrototype);
        ProtosObjectValue networkPrototype =
                requirePrototype(
                        bootstrapContext, "Network", ProtosObjectValue.rootObject());
        ProtosStandardNetworkProtocol.install(networkPrototype);
        ProtosObjectValue tcpConnectionPrototype =
                requirePrototype(
                        bootstrapContext,
                        "_coreTcpConnectionPrototype",
                        ProtosObjectValue.rootObject());
        ProtosStandardTcpConnectionProtocol.install(tcpConnectionPrototype);
        bootstrapContext.removeLocalSlot("_coreTcpConnectionPrototype");
        ProtosObjectValue tcpListenerPrototype =
                requirePrototype(
                        bootstrapContext,
                        "_coreTcpListenerPrototype",
                        ProtosObjectValue.rootObject());
        ProtosStandardTcpListenerProtocol.install(tcpListenerPrototype);
        bootstrapContext.removeLocalSlot("_coreTcpListenerPrototype");
        ProtosObjectValue futurePrototype = requirePrototype(bootstrapContext, "Future", ProtosObjectValue.rootObject());
        ProtosStandardFutureProtocol.install(futurePrototype);
        ProtosObjectValue processObject =
                requirePrototype(
                        bootstrapContext, "Process", ProtosObjectValue.rootObject());
        ProtosStandardProcessProtocol.install(processObject);
        ProtosObjectValue actorObject =
                requirePrototype(
                        bootstrapContext, "Actor", ProtosObjectValue.rootObject());
        ProtosObjectValue actorRefPrototype =
                requirePrototype(
                        bootstrapContext,
                        "_coreActorRefPrototype",
                        ProtosObjectValue.rootObject());
        ProtosObjectValue groupRefPrototype =
                requirePrototype(
                        bootstrapContext,
                        "_coreGroupRefPrototype",
                        ProtosObjectValue.rootObject());
        ProtosObjectValue sendOperationPrototype =
                requirePrototype(
                        bootstrapContext,
                        "_coreSendOperationPrototype",
                        ProtosObjectValue.rootObject());
        bootstrapContext.removeLocalSlot("_coreActorRefPrototype");
        bootstrapContext.removeLocalSlot("_coreGroupRefPrototype");
        bootstrapContext.removeLocalSlot("_coreSendOperationPrototype");
        ProtosObjectValue textReaderFactory =
                requirePrototype(
                        bootstrapContext, "TextReader", ProtosObjectValue.rootObject());
        ProtosObjectValue textWriterFactory =
                requirePrototype(
                        bootstrapContext, "TextWriter", ProtosObjectValue.rootObject());
        ProtosObjectValue bufferedReaderFactory =
                requirePrototype(
                        bootstrapContext, "BufferedReader", ProtosObjectValue.rootObject());
        ProtosObjectValue bufferedWriterFactory =
                requirePrototype(
                        bootstrapContext, "BufferedWriter", ProtosObjectValue.rootObject());
        ProtosObjectValue importFacility =
                requirePrototype(
                        bootstrapContext, "import", ProtosObjectValue.rootObject());
        ProtosModuleRuntime moduleRuntime = new ProtosModuleRuntime(moduleResolver);
        ProtosStandardActorProtocol actorProtocol =
                new ProtosStandardActorProtocol(
                        moduleRuntime, actorRefPrototype, sendOperationPrototype);
        actorProtocol.installGroupRefPrototype(groupRefPrototype);
        actorProtocol.installActorObject(actorObject);
        ProtosStandardImportProtocol.installImportFacility(importFacility, moduleRuntime);

        // Bytes is standardized but intentionally not a required Core-prelude binding.
        ProtosObjectValue bufferedBytesPrototype =
                requirePrototype(
                        bootstrapContext, "Bytes", ProtosObjectValue.rootObject());
        bootstrapContext.removeLocalSlot("Bytes");
        ProtosStandardBytesProtocol.install(bufferedBytesPrototype);

        ProtosObjectValue encodingPrototype =
                requirePrototype(
                        bootstrapContext, "Encoding", ProtosObjectValue.rootObject());
        ProtosStandardEncodingProtocol.install(
                encodingPrototype, bufferedBytesPrototype);

        ProtosStandardTextReaderProtocol.installFactory(
                textReaderFactory, bootstrapActivation);
        ProtosStandardTextWriterProtocol.installFactory(
                textWriterFactory, bootstrapActivation);
        ProtosStandardBufferedByteIoProtocol.installReaderFactory(
                bufferedReaderFactory, bufferedBytesPrototype, bootstrapActivation);
        ProtosStandardBufferedByteIoProtocol.installWriterFactory(
                bufferedWriterFactory, bufferedBytesPrototype, bootstrapActivation);

        if (bootstrapContext.hasLocalSlot("_coreRootObject")) {
            throw new IllegalStateException(
                    "Core bootstrap root seed already exists");
        }
        bootstrapContext.createLocalSlot(
                "_coreRootObject",
                ProtosObjectValue.rootObject());
        try {
            sourceLoader
                    .load(coreDirectory.resolve("prelude.protos"))
                    .call(bootstrapActivation);
        } finally {
            bootstrapContext.removeLocalSlot("_coreRootObject");
        }
        ProtosObjectValue preludeBindings =
                requirePrototype(
                        bootstrapContext, "_corePreludeBindings", contextPrototype);
        bootstrapContext.removeLocalSlot("_corePreludeBindings");

        freezeSharedStandardGraph(
                bootstrapContext,
                preludeBindings,
                bufferedBytesPrototype,
                actorRefPrototype,
                groupRefPrototype,
                sendOperationPrototype,
                tcpConnectionPrototype,
                tcpListenerPrototype);
        validateFrozenStandardGraph(
                bootstrapContext,
                preludeBindings,
                bufferedBytesPrototype,
                actorRefPrototype,
                groupRefPrototype,
                sendOperationPrototype,
                tcpConnectionPrototype,
                tcpListenerPrototype);

        return new ProtosPrelude(
                preludeBindings,
                contextPrototype,
                bufferedBytesPrototype,
                actorRefPrototype,
                tcpConnectionPrototype,
                tcpListenerPrototype);
    }


    private void publishStandardRoot(Path coreDirectory) throws IOException {
        ProtosObjectValue object = ProtosObjectValue.rootObject();
        synchronized (object) {
            if (object.isFrozen()) {
                validatePublishedRoot(object);
                return;
            }
            if (!object.isOpen()) {
                throw new IllegalStateException(
                        "standard Object root must be open during unpublished bootstrap construction");
            }

            ProtosStandardObjectProtocol.install();
            installSourceBackedObjectBehavior(coreDirectory);
            ProtosStandardHashSupport.installObjectHash();
            ProtosStandardFutureProtocol.installObjectFuture();
            ProtosParallelRuntime.installObjectParallel();

            validateRootSurface(object);
            requireContextLocalRootExecutionProjection(object, "init");
            requireContextLocalRootExecutionProjection(object, "==");
            requireContextLocalRootExecutionProjection(object, "!=");
            freezeSharedStandardGraph(object);
            validatePublishedRoot(object);
        }
    }

    private static void validateRootSurface(ProtosObjectValue object) {
        Set<String> expected =
                Set.of(
                        "call",
                        "identityHash",
                        "hasSlot",
                        "slotValue",
                        "ifTrue",
                        "ifFalse",
                        "and",
                        "or",
                        "hash",
                        "future",
                        "parallel",
                        "parent",
                        "ensure",
                        "while",
                        "init",
                        "==",
                        "!=");
        if (!object.localSlotsSnapshot().keySet().containsAll(expected)) {
            throw new IllegalStateException(
                    "standard Object root publication surface is incomplete");
        }
        validateExistingSourceBackedClosure(object, "init");
        validateExistingSourceBackedClosure(object, "==");
        validateExistingSourceBackedClosure(object, "!=");
    }

    private static void requireContextLocalRootExecutionProjection(
            ProtosObjectValue object, String selector) {
        Object value =
                object.readLocalSlot(selector)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "standard Object." + selector + " is missing"));
        if (!(value instanceof ProtosClosureValue closure)
                || closure.definition() == null
                || closure.executionPlan().isEmpty()
                || closure.nativeBody().isPresent()) {
            throw new IllegalStateException(
                    "standard Object." + selector + " is not source-backed");
        }
        closure.requireContextLocalExecutionProjectionForRuntime();
    }

    private static void validateContextLocalRootExecutionProjection(
            ProtosObjectValue object, String selector) {
        Object value = object.readLocalSlot(selector).orElse(null);
        if (!(value instanceof ProtosClosureValue closure)
                || !closure.requiresContextLocalExecutionProjectionForRuntime()) {
            throw new IllegalStateException(
                    "standard Object."
                            + selector
                            + " is missing Context-local executable projection metadata");
        }
    }

    private static void validatePublishedRoot(ProtosObjectValue object) {
        validateRootSurface(object);
        if (!object.isFrozen()) {
            throw new IllegalStateException("standard Object root was not published frozen");
        }
        validateContextLocalRootExecutionProjection(object, "init");
        validateContextLocalRootExecutionProjection(object, "==");
        validateContextLocalRootExecutionProjection(object, "!=");
        for (Object value : object.localSlotsSnapshot().values()) {
            if (!(value instanceof ProtosClosureValue closure) || !closure.isFrozen()) {
                throw new IllegalStateException(
                        "published standard Object behavior must be a frozen Closure");
            }
        }
    }

    /**
     * Seals only the implementation-owned standard graph being published by Core bootstrap.
     *
     * <p>This is not the Protos {@code freeze()} operation made recursive. It is a bootstrap
     * publication walk over standard objects that are about to be physically shared. Closure
     * semantic captures are included because a frozen Closure must not retain mutable shared
     * Protos state behind its structural shell.
     */
    private static void freezeSharedStandardGraph(Object... roots) {
        Set<ProtosObjectValue> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ProtosObjectValue> pending = new ArrayDeque<>();
        enqueueStandardObjects(pending, roots);
        while (!pending.isEmpty()) {
            ProtosObjectValue object = pending.removeFirst();
            if (!visited.add(object)) {
                continue;
            }
            enqueueStandardObjects(
                    pending, object.localSlotsSnapshot().values().toArray());
            if (object instanceof ProtosClosureValue closure) {
                enqueueStandardObjects(
                        pending, closure.capturedLexicalContexts().toArray());
                enqueueStandardObjects(pending, closure.capturedReceiver());
                closure.methodHome().ifPresent(pending::addLast);
            }
            object.freeze();
        }
    }

    private static void validateFrozenStandardGraph(Object... roots) {
        Set<ProtosObjectValue> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ProtosObjectValue> pending = new ArrayDeque<>();
        enqueueStandardObjects(pending, roots);
        while (!pending.isEmpty()) {
            ProtosObjectValue object = pending.removeFirst();
            if (!visited.add(object)) {
                continue;
            }
            if (!object.isFrozen()) {
                throw new IllegalStateException(
                        "Core published mutable standard object: " + object.getClass().getName());
            }
            enqueueStandardObjects(
                    pending, object.localSlotsSnapshot().values().toArray());
            if (object instanceof ProtosClosureValue closure) {
                enqueueStandardObjects(
                        pending, closure.capturedLexicalContexts().toArray());
                enqueueStandardObjects(pending, closure.capturedReceiver());
                closure.methodHome().ifPresent(pending::addLast);
            }
        }
    }

    private static void enqueueStandardObjects(
            ArrayDeque<ProtosObjectValue> pending, Object... values) {
        for (Object value : values) {
            if (value instanceof ProtosObjectValue object) {
                pending.addLast(object);
            }
        }
    }


    private void installSourceBackedObjectBehavior(Path coreDirectory)
            throws IOException {
        ProtosObjectValue sourceContext =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation sourceActivation =
                new ProtosActivation(sourceContext, List.of(), sourceContext);

        sourceLoader
                .load(coreDirectory.resolve("Object.protos"))
                .call(sourceActivation);

        ProtosClosureValue init =
                requireSourceBackedClosure(sourceContext, "_coreObjectInit");
        ProtosClosureValue equals =
                requireSourceBackedClosure(sourceContext, "_coreObjectEquals");
        ProtosClosureValue notEquals =
                requireSourceBackedClosure(
                        sourceContext, "_coreObjectNotEquals");

        sourceContext.removeLocalSlot("_coreObjectInit");
        sourceContext.removeLocalSlot("_coreObjectEquals");
        sourceContext.removeLocalSlot("_coreObjectNotEquals");
        if (!sourceContext.localSlotsSnapshot().isEmpty()) {
            throw new IllegalStateException(
                    "Core object source left unexpected bootstrap bindings");
        }
        sourceContext.freeze();

        ProtosObjectValue object = ProtosObjectValue.rootObject();
        synchronized (object) {
            validateExistingSourceBackedClosure(object, "init");
            validateExistingSourceBackedClosure(object, "==");
            validateExistingSourceBackedClosure(object, "!=");
            if (!object.hasLocalSlot("init")) {
                object.createLocalSlot("init", init);
            }
            if (!object.hasLocalSlot("==")) {
                object.createLocalSlot("==", equals);
            }
            if (!object.hasLocalSlot("!=")) {
                object.createLocalSlot("!=", notEquals);
            }
        }
    }

    private static ProtosClosureValue requireSourceBackedClosure(
            ProtosObjectValue sourceContext, String name) {
        Object value =
                sourceContext
                        .readLocalSlot(name)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core object source did not define "
                                                        + name));
        if (!(value instanceof ProtosClosureValue closure)
                || closure.definition() == null
                || closure.executionPlan().isEmpty()
                || closure.nativeBody().isPresent()) {
            throw new IllegalStateException(
                    "Core " + name + " must be a source-backed Closure");
        }
        return closure;
    }

    private static void validateExistingSourceBackedClosure(
            ProtosObjectValue object, String selector) {
        Object existing = object.readLocalSlot(selector).orElse(null);
        if (existing == null) {
            return;
        }
        if (!(existing instanceof ProtosClosureValue closure)
                || closure.definition() == null
                || closure.executionPlan().isEmpty()
                || closure.nativeBody().isPresent()) {
            throw new IllegalStateException(
                    "Core Object."
                            + selector
                            + " is not installed from distributable Core source");
        }
    }

    private static ProtosObjectValue requirePrototype(
            ProtosObjectValue bootstrapContext,
            String name,
            Object expectedParent) {
        Object binding =
                bootstrapContext
                        .readLocalSlot(name)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core bootstrap did not define " + name));
        if (!(binding instanceof ProtosObjectValue prototype)) {
            throw new IllegalStateException(
                    "Core " + name + " binding is not an ordinary object");
        }
        if (prototype.parent().orElse(null) != expectedParent) {
            throw new IllegalStateException(
                    "Core " + name + " prototype has the wrong delegation parent");
        }
        return prototype;
    }
}
