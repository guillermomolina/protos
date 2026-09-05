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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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

        ProtosStandardObjectProtocol.install();
        installSourceBackedObjectBehavior(coreDirectory);

        ProtosObjectValue bootstrapContext =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation bootstrapActivation =
                new ProtosActivation(
                        bootstrapContext,
                        List.of(),
                        bootstrapContext);

        sourceLoader
                .load(coreDirectory.resolve("context.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("number.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("integer.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("float.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("uint8.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("int8.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("uint16.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("int16.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("uint32.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("int32.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("uint64.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("int64.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("error.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("invalid_return.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("error_taxonomy.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("array.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("string.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("map.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("identity_map.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("path.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("future.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("actor.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("buffered_reader.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("buffered_writer.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("import.protos"))
                .call(bootstrapActivation);
        sourceLoader
                .load(coreDirectory.resolve("bytes.protos"))
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
        ProtosStandardHashSupport.installObjectHash();
        ProtosStandardHashSupport.installNumberHash(numberPrototype);
        ProtosStandardIntegerProtocol.install(integerPrototype);
        ProtosStandardFloatProtocol.install(floatPrototype);
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
        ProtosObjectValue futurePrototype = requirePrototype(bootstrapContext, "Future", ProtosObjectValue.rootObject());
        ProtosStandardFutureProtocol.install(futurePrototype);
        ProtosObjectValue actorObject =
                requirePrototype(
                        bootstrapContext, "Actor", ProtosObjectValue.rootObject());
        ProtosObjectValue bufferedReaderFactory =
                requirePrototype(
                        bootstrapContext, "BufferedReader", ProtosObjectValue.rootObject());
        ProtosObjectValue bufferedWriterFactory =
                requirePrototype(
                        bootstrapContext, "BufferedWriter", ProtosObjectValue.rootObject());
        ProtosObjectValue importFacility =
                requirePrototype(
                        bootstrapContext, "import", ProtosObjectValue.rootObject());
        ProtosParallelRuntime.installObjectParallel();
        ProtosModuleRuntime moduleRuntime = new ProtosModuleRuntime(moduleResolver);
        new ProtosStandardActorProtocol(moduleRuntime).installActorObject(actorObject);
        ProtosStandardImportProtocol.installImportFacility(importFacility, moduleRuntime);

        // Bytes is standardized but intentionally not a required Core-prelude binding.
        ProtosObjectValue bufferedBytesPrototype =
                requirePrototype(
                        bootstrapContext, "Bytes", ProtosObjectValue.rootObject());
        bootstrapContext.removeLocalSlot("Bytes");
        ProtosStandardBytesProtocol.install(bufferedBytesPrototype);
        ProtosStandardBufferedByteIoProtocol.installReaderFactory(
                bufferedReaderFactory, bufferedBytesPrototype, bootstrapActivation);
        ProtosStandardBufferedByteIoProtocol.installWriterFactory(
                bufferedWriterFactory, bufferedBytesPrototype, bootstrapActivation);

        sourceLoader
                .load(coreDirectory.resolve("prelude.protos"))
                .call(bootstrapActivation);
        ProtosObjectValue preludeBindings =
                requirePrototype(
                        bootstrapContext, "_corePreludeBindings", contextPrototype);
        bootstrapContext.removeLocalSlot("_corePreludeBindings");
        preludeBindings.freeze();

        return new ProtosPrelude(preludeBindings, contextPrototype);
    }


    private void installSourceBackedObjectBehavior(Path coreDirectory)
            throws IOException {
        ProtosObjectValue sourceContext =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation sourceActivation =
                new ProtosActivation(sourceContext, List.of(), sourceContext);

        sourceLoader
                .load(coreDirectory.resolve("object.protos"))
                .call(sourceActivation);

        ProtosClosureValue init =
                requireSourceBackedClosure(sourceContext, "_coreObjectInit");
        ProtosClosureValue notEquals =
                requireSourceBackedClosure(
                        sourceContext, "_coreObjectNotEquals");

        sourceContext.removeLocalSlot("_coreObjectInit");
        sourceContext.removeLocalSlot("_coreObjectNotEquals");
        if (!sourceContext.localSlotsSnapshot().isEmpty()) {
            throw new IllegalStateException(
                    "Core object source left unexpected bootstrap bindings");
        }
        sourceContext.freeze();

        ProtosObjectValue object = ProtosObjectValue.rootObject();
        synchronized (object) {
            validateExistingSourceBackedClosure(object, "init");
            validateExistingSourceBackedClosure(object, "!=");
            if (!object.hasLocalSlot("init")) {
                object.createLocalSlot("init", init);
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
