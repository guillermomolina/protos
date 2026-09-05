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

        ProtosObjectValue preludeBindings =
                new ProtosObjectValue(contextPrototype);
        preludeBindings.createLocalSlot("Context", contextPrototype);
        preludeBindings.createLocalSlot("Number", numberPrototype);
        preludeBindings.createLocalSlot("Integer", integerPrototype);
        preludeBindings.createLocalSlot("Float", floatPrototype);
        preludeBindings.createLocalSlot("UInt8", uInt8Prototype);
        preludeBindings.createLocalSlot("Int8", int8Prototype);
        preludeBindings.createLocalSlot("UInt16", uInt16Prototype);
        preludeBindings.createLocalSlot("Int16", int16Prototype);
        preludeBindings.createLocalSlot("UInt32", uInt32Prototype);
        preludeBindings.createLocalSlot("Int32", int32Prototype);
        preludeBindings.createLocalSlot("UInt64", uInt64Prototype);
        preludeBindings.createLocalSlot("Int64", int64Prototype);
        preludeBindings.createLocalSlot("Error", errorPrototype);
        preludeBindings.createLocalSlot(
                "InvalidReturn", invalidReturnPrototype);
        ProtosCoreErrorTaxonomy.exportBindings(bootstrapContext, preludeBindings);
        preludeBindings.createLocalSlot("Array", arrayPrototype);
        preludeBindings.createLocalSlot("String", stringPrototype);
        preludeBindings.createLocalSlot("Map", mapPrototype);
        preludeBindings.createLocalSlot("IdentityMap", identityMapPrototype);
        preludeBindings.createLocalSlot("Path", pathPrototype);
        preludeBindings.createLocalSlot(
                "import",
                ProtosStandardImportProtocol.createImportFacility(
                        new ProtosModuleRuntime(moduleResolver)));
        preludeBindings.freeze();

        return new ProtosPrelude(preludeBindings, contextPrototype);
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
