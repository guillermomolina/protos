/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;

final class ProtosStandardImportProtocol {
    private ProtosStandardImportProtocol() {}

    static ProtosObjectValue createImportFacility(ProtosModuleRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facility.createLocalSlot("call", ProtosClosureValue.nativeClosure((activation, supplied) -> {
            if (supplied.size() != 1) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
            }
            return runtime.importModule(supplied.get(0), activation);
        }));
        facility.freeze();
        return facility;
    }
}
