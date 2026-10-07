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

import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosRawForeignValue;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import java.util.Objects;
import java.util.Optional;

/**
 * Private D188 state shared by a raw foreign reference and an attached foreign module facade: the
 * exact session binding (the session generation), the provider value contract, the underlying
 * target, and its raw classification.
 *
 * <p>The binding is never replaced: once it closes, every operation through this handle fails
 * before entry and nothing rebinds to a later session of the same Actor and provider.
 * Capabilities are those classified at admission; the generic projection never re-derives them
 * from physical shape.
 */
final class ProtosForeignHandle implements ProtosRawForeignValue.Handle {
    private final ProtosForeignProviderSessionBinding session;
    private final ProtosForeignValueAdapter adapter;
    private final Object target;
    private final ProtosForeignAdmissionDescriptor descriptor;

    ProtosForeignHandle(
            ProtosForeignProviderSessionBinding session,
            ProtosForeignValueAdapter adapter,
            Object target,
            ProtosForeignAdmissionDescriptor descriptor) {
        this.session = Objects.requireNonNull(session, "session");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.target = Objects.requireNonNull(target, "target");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.kind() != ProtosForeignAdmissionDescriptor.Kind.RAW) {
            throw new IllegalArgumentException("a foreign handle carries a raw classification");
        }
    }

    /** The handle of a raw reference or attached facade receiver, or null for any other value. */
    static ProtosForeignHandle of(Object receiver) {
        if (receiver instanceof ProtosRawForeignValue raw
                && raw.handleForRuntime() instanceof ProtosForeignHandle handle) {
            return handle;
        }
        if (receiver instanceof ProtosForeignModuleFacadeValue facade) {
            return facade.attachmentForRuntime()
                    .map(ProtosForeignModuleFacadeValue.Attachment::handle)
                    .orElse(null);
        }
        return null;
    }

    ProtosForeignProviderSessionBinding session() {
        return session;
    }

    ProtosForeignValueAdapter adapter() {
        return adapter;
    }

    Object target() {
        return target;
    }

    /** Executable and not also instantiable: the generic layer never chooses between them. */
    boolean projectsCall() {
        return descriptor.has(ProtosForeignAdmissionDescriptor.Capability.EXECUTABLE)
                && !descriptor.has(ProtosForeignAdmissionDescriptor.Capability.INSTANTIABLE);
    }

    /** Array-element access, unless hash-entry access makes the meaning of {@code at} ambiguous. */
    boolean projectsAt() {
        return descriptor.has(ProtosForeignAdmissionDescriptor.Capability.INDEXED_READ)
                && !descriptor.has(ProtosForeignAdmissionDescriptor.Capability.HASH_ENTRIES);
    }

    boolean projectsAtPut() {
        return descriptor.has(ProtosForeignAdmissionDescriptor.Capability.INDEXED_WRITE)
                && !descriptor.has(ProtosForeignAdmissionDescriptor.Capability.HASH_ENTRIES);
    }

    /** Pull iteration only where the provider declared it faithful; shape never implies it. */
    boolean projectsEach() {
        return descriptor.has(ProtosForeignAdmissionDescriptor.Capability.ITERABLE);
    }

    @Override
    public Object sessionGenerationForRuntime() {
        return session;
    }

    @Override
    public Object stableIdentityKeyForRuntime() {
        return descriptor.stableIdentityKey();
    }

    @Override
    public Optional<ProtosSlotLookupResult> lookupForRuntime(
            ProtosRawForeignValue receiver, String name, ProtosPrelude prelude) {
        return ProtosForeignProjectedOperations.lookupRaw(this, name, prelude);
    }
}
